#!/usr/bin/env python3
"""Fallback Runtime API for RK3588 when the Java control plane is unavailable."""

from __future__ import annotations

import argparse
import json
import secrets
import threading
import time
from dataclasses import dataclass, field
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Callable, Dict, Optional
from urllib import error, request


DEFAULT_BRIDGE_HEALTH_URL = 'http://127.0.0.1:19080/health'


@dataclass
class RuntimeApiFallbackState:
    bootstrap_token: str
    issued_tokens: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    token_ttl_ms: int = 3600 * 1000
    lock: threading.Lock = field(default_factory=threading.Lock)


@dataclass(frozen=True)
class RuntimeApiFallbackConfig:
    host: str = '127.0.0.1'
    port: int = 18081
    bootstrap_token: str = ''
    bridge_health_url: str = DEFAULT_BRIDGE_HEALTH_URL
    bridge_timeout_sec: float = 5.0
    token_ttl_sec: int = 3600


class RuntimeApiFallbackHandler(BaseHTTPRequestHandler):
    server_version = 'RuntimeApiFallback/0.1'

    def do_GET(self) -> None:
        if self.path == '/api/v1/runtime/health':
            self._write_json(200, success_payload(self.server.service.build_health_payload()))
            return
        if self.path == '/api/v1/runtime/snapshot':
            if not self.server.service.is_authorized(self.headers.get('Authorization')):
                self._write_json(401, unauthorized_payload())
                return
            self._write_json(200, success_payload(self.server.service.build_runtime_snapshot()))
            return
        self._write_json(404, error_payload(404, 'not found'))

    def do_POST(self) -> None:
        length = int(self.headers.get('Content-Length', '0') or '0')
        raw_body = self.rfile.read(length) if length > 0 else b''
        try:
            payload = json.loads(raw_body.decode('utf-8')) if raw_body else {}
        except json.JSONDecodeError:
            self._write_json(400, error_payload(400, 'request body must be valid json'))
            return
        if self.path == '/api/v1/auth/token':
            bootstrap_token = self.headers.get('X-Bootstrap-Token', '')
            if not self.server.service.is_bootstrap_authorized(bootstrap_token):
                self._write_json(401, unauthorized_payload('invalid bootstrap token', 'access_denied'))
                return
            user_id = str(payload.get('user_id', '')).strip() or 'edge-user'
            role = str(payload.get('role', '')).strip() or 'admin'
            self._write_json(200, success_payload(self.server.service.issue_token(user_id, role)))
            return
        if self.path == '/api/v1/inference/plan':
            if not self.server.service.is_authorized(self.headers.get('Authorization')):
                self._write_json(401, unauthorized_payload())
                return
            budget = as_float(payload.get('budget'), 10.0)
            self._write_json(200, success_payload(self.server.service.build_inference_plan(budget)))
            return
        self._write_json(404, error_payload(404, 'not found'))

    def log_message(self, format: str, *args: Any) -> None:
        return

    def _write_json(self, status_code: int, payload: Dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode('utf-8')
        self.send_response(status_code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class RuntimeApiFallbackServer(ThreadingHTTPServer):
    def __init__(self, server_address: tuple[str, int], service: 'RuntimeApiFallbackService'):
        super().__init__(server_address, RuntimeApiFallbackHandler)
        self.service = service


class RuntimeApiFallbackService:
    def __init__(
        self,
        config: RuntimeApiFallbackConfig,
        state: Optional[RuntimeApiFallbackState] = None,
        http_open: Optional[Callable[..., Any]] = None,
        now_ms: Optional[Callable[[], int]] = None,
    ):
        self.config = config
        self.state = state or RuntimeApiFallbackState(
            bootstrap_token=config.bootstrap_token,
            token_ttl_ms=max(1, int(config.token_ttl_sec)) * 1000,
        )
        self.http_open = http_open or request.urlopen
        self.now_ms = now_ms or (lambda: int(time.time() * 1000))

    def is_bootstrap_authorized(self, token: str) -> bool:
        expected = str(self.state.bootstrap_token or '').strip()
        return bool(expected) and secrets.compare_digest(str(token or '').strip(), expected)

    def issue_token(self, user_id: str, role: str) -> Dict[str, Any]:
        return issue_token(self.state, user_id, role, now_ms=self.now_ms())

    def is_authorized(self, authorization_header: Optional[str]) -> bool:
        return is_authorized(self.state, authorization_header or '', now_ms=self.now_ms())

    def build_health_payload(self) -> Dict[str, Any]:
        snapshot = self.build_runtime_snapshot()
        return {
            'status': 'ok',
            'backend': 'python_fallback',
            'runtime': 'fallback',
            'stream_count': snapshot.get('stream_count', 0),
            'ready_stream_count': snapshot.get('ready_stream_count', 0),
            'bridge_health_url': self.config.bridge_health_url,
        }

    def build_runtime_snapshot(self) -> Dict[str, Any]:
        return fetch_bridge_snapshot(
            self.config.bridge_health_url,
            timeout_sec=self.config.bridge_timeout_sec,
            http_open=self.http_open,
        )

    def build_inference_plan(self, budget: float) -> Dict[str, Any]:
        snapshot = self.build_runtime_snapshot()
        return build_inference_plan(snapshot, budget=budget)


def success_payload(data: Dict[str, Any]) -> Dict[str, Any]:
    return {'code': 0, 'msg': 'OK', 'data': data}


def error_payload(code: int, message: str) -> Dict[str, Any]:
    return {'code': code, 'msg': message, 'success': False, 'error': {'code': str(code), 'message': message}}


def unauthorized_payload(message: str = 'unauthorized', error_code: str = 'unauthorized') -> Dict[str, Any]:
    return {'code': 401, 'msg': message, 'success': False, 'error': {'code': error_code, 'message': message}}


def as_float(value: Any, default: float) -> float:
    try:
        result = float(value)
    except (TypeError, ValueError):
        return default
    return result if result > 0 else default


def normalize_stream(stream: Dict[str, Any]) -> Dict[str, Any]:
    item = dict(stream or {})
    item['camera_id'] = item.get('camera_id', '')
    item['camera_name'] = str(item.get('camera_name', ''))
    item['rtsp_url'] = str(item.get('rtsp_url', ''))
    item['play_url'] = str(item.get('play_url', ''))
    item['push_url'] = str(item.get('push_url', ''))
    item['ready'] = bool(item.get('ready', item.get('play_url') or item.get('push_url')))
    return item


def empty_snapshot(message: str = '') -> Dict[str, Any]:
    snapshot = {
        'device_count': 0,
        'session_count': 0,
        'algorithm_count': 0,
        'dead_letter_size': 0,
        'push_queue_size': 0,
        'stream_count': 0,
        'ready_stream_count': 0,
        'media': {},
        'streams': [],
        'sessions': [],
        'backend': 'python_fallback',
    }
    if message:
        snapshot['fallback'] = {'mode': 'python_fallback', 'message': message}
    return snapshot


def extract_runtime_snapshot(payload: Dict[str, Any]) -> Dict[str, Any]:
    data = payload.get('runtime_snapshot') if isinstance(payload.get('runtime_snapshot'), dict) else payload
    if not isinstance(data, dict):
        return empty_snapshot('invalid runtime snapshot payload')
    streams = data.get('streams') if isinstance(data.get('streams'), list) else []
    normalized_streams = [normalize_stream(item) for item in streams if isinstance(item, dict)]
    ready_count = sum(1 for item in normalized_streams if item.get('ready'))
    snapshot = empty_snapshot()
    snapshot.update({key: value for key, value in data.items() if key not in {'streams', 'media', 'sessions'}})
    snapshot['streams'] = normalized_streams
    snapshot['media'] = dict(data.get('media')) if isinstance(data.get('media'), dict) else {}
    snapshot['sessions'] = list(data.get('sessions')) if isinstance(data.get('sessions'), list) else []
    snapshot['stream_count'] = int(data.get('stream_count', len(normalized_streams)) or len(normalized_streams))
    snapshot['ready_stream_count'] = int(data.get('ready_stream_count', ready_count) or ready_count)
    snapshot['device_count'] = int(data.get('device_count', snapshot['stream_count']) or snapshot['stream_count'])
    snapshot['session_count'] = int(data.get('session_count', len(snapshot['sessions'])) or len(snapshot['sessions']))
    snapshot['algorithm_count'] = int(data.get('algorithm_count', 0) or 0)
    snapshot['dead_letter_size'] = int(data.get('dead_letter_size', 0) or 0)
    snapshot['push_queue_size'] = int(data.get('push_queue_size', 0) or 0)
    snapshot['backend'] = 'python_fallback'
    return snapshot


def fetch_bridge_snapshot(
    bridge_health_url: str,
    timeout_sec: float = 5.0,
    http_open: Optional[Callable[..., Any]] = None,
) -> Dict[str, Any]:
    opener = http_open or request.urlopen
    req = request.Request(bridge_health_url, headers={'Accept': 'application/json'}, method='GET')
    try:
        with opener(req, timeout=timeout_sec) as response:
            payload = json.loads(response.read().decode('utf-8'))
        if not isinstance(payload, dict):
            return empty_snapshot('bridge health payload is invalid')
        return extract_runtime_snapshot(payload)
    except error.HTTPError as exc:
        return empty_snapshot(f'bridge health http {exc.code}')
    except Exception as exc:
        return empty_snapshot(str(exc))


def build_inference_plan(snapshot: Dict[str, Any], budget: float = 10.0) -> Dict[str, Any]:
    normalized_budget = as_float(budget, 10.0)
    streams = snapshot.get('streams') if isinstance(snapshot.get('streams'), list) else []
    items = []
    for stream in streams:
        if not isinstance(stream, dict):
            continue
        normalized = normalize_stream(stream)
        items.append(
            {
                'camera_id': normalized.get('camera_id', ''),
                'camera_name': normalized.get('camera_name', ''),
                'rtsp_url': normalized.get('rtsp_url', ''),
                'play_url': normalized.get('play_url', ''),
                'push_url': normalized.get('push_url', ''),
                'ready': bool(normalized.get('ready')),
            }
        )
    return {
        'budget': normalized_budget,
        'stream_count': int(snapshot.get('stream_count', len(items)) or len(items)),
        'ready_stream_count': int(snapshot.get('ready_stream_count', sum(1 for item in items if item.get('ready'))) or 0),
        'items': items,
        'backend': 'python_fallback',
    }


def issue_token(state: RuntimeApiFallbackState, user_id: str, role: str, now_ms: Optional[int] = None) -> Dict[str, Any]:
    issued_at = int(now_ms if now_ms is not None else time.time() * 1000)
    expires_at = issued_at + max(1, int(state.token_ttl_ms))
    token = secrets.token_urlsafe(24)
    record = {
        'token': token,
        'token_type': 'Bearer',
        'user_id': str(user_id or 'edge-user'),
        'role': str(role or 'admin'),
        'issued_at': issued_at,
        'expires_at': expires_at,
    }
    with state.lock:
        state.issued_tokens[token] = record
        prune_expired_tokens(state, issued_at)
    return dict(record)


def prune_expired_tokens(state: RuntimeApiFallbackState, now_ms: int) -> None:
    expired = [token for token, record in state.issued_tokens.items() if int(record.get('expires_at', 0)) <= now_ms]
    for token in expired:
        state.issued_tokens.pop(token, None)


def is_authorized(state: RuntimeApiFallbackState, authorization_header: str, now_ms: Optional[int] = None) -> bool:
    header = str(authorization_header or '').strip()
    if not header.lower().startswith('bearer '):
        return False
    token = header[7:].strip()
    now_value = int(now_ms if now_ms is not None else time.time() * 1000)
    with state.lock:
        prune_expired_tokens(state, now_value)
        record = state.issued_tokens.get(token)
        return bool(record and int(record.get('expires_at', 0)) > now_value)


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Fallback runtime API server for RK3588')
    parser.add_argument('--host', default='127.0.0.1')
    parser.add_argument('--port', type=int, default=18081)
    parser.add_argument('--bootstrap-token', default='')
    parser.add_argument('--bridge-health-url', default=DEFAULT_BRIDGE_HEALTH_URL)
    parser.add_argument('--bridge-timeout-sec', type=float, default=5.0)
    parser.add_argument('--token-ttl-sec', type=int, default=3600)
    return parser.parse_args(argv)


def build_config(args: argparse.Namespace) -> RuntimeApiFallbackConfig:
    return RuntimeApiFallbackConfig(
        host=args.host,
        port=args.port,
        bootstrap_token=str(args.bootstrap_token or ''),
        bridge_health_url=str(args.bridge_health_url or DEFAULT_BRIDGE_HEALTH_URL),
        bridge_timeout_sec=max(0.1, float(args.bridge_timeout_sec)),
        token_ttl_sec=max(1, int(args.token_ttl_sec)),
    )


def run_server(config: RuntimeApiFallbackConfig) -> int:
    service = RuntimeApiFallbackService(config)
    server = RuntimeApiFallbackServer((config.host, config.port), service)
    print(
        json.dumps(
            {
                'status': 'starting',
                'backend': 'python_fallback',
                'listen': f'{config.host}:{config.port}',
                'bridge_health_url': config.bridge_health_url,
            },
            ensure_ascii=True,
        ),
        flush=True,
    )
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    config = build_config(args)
    return run_server(config)


if __name__ == '__main__':
    raise SystemExit(main())
