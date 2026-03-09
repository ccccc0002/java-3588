#!/usr/bin/env python3
"""Compatibility bridge that exposes /health and /v1/infer for the Java RK3588 client."""

from __future__ import annotations

import argparse
import json
import sys
import time
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Callable, Dict, Optional, Tuple
from urllib import error, request


BRIDGE_VERSION = '0.2.0'


class RuntimeBridgeError(RuntimeError):
    def __init__(self, message: str, status_code: int = 502):
        super().__init__(message)
        self.status_code = status_code


@dataclass(frozen=True)
class RuntimeBridgeConfig:
    runtime_url: str
    runtime_token: str = ''
    runtime_bootstrap_token: str = ''
    runtime_token_role: str = 'admin'
    runtime_user_id: str = 'java-rk3588-bridge'
    bootstrap_header_name: str = 'X-Bootstrap-Token'
    timeout_sec: float = 5.0
    default_plan_budget: float = 10.0
    decode_mode: str = 'stub'
    bridge_version: str = BRIDGE_VERSION


class RuntimeApiClient:
    def __init__(self, config: RuntimeBridgeConfig, http_open: Optional[Callable[..., Any]] = None):
        self.config = config
        self.http_open = http_open or request.urlopen
        self.token_provider = None

    def set_token_provider(self, token_provider: Optional['TokenProvider']) -> None:
        self.token_provider = token_provider

    def issue_token(self) -> str:
        if self.config.runtime_token:
            return self.config.runtime_token
        payload = {
            'user_id': self.config.runtime_user_id,
            'role': self.config.runtime_token_role,
        }
        headers = {'Content-Type': 'application/json'}
        if self.config.runtime_bootstrap_token:
            headers[self.config.bootstrap_header_name] = self.config.runtime_bootstrap_token
        status_code, payload = self._request_json('POST', '/api/v1/auth/token', payload=payload, headers=headers, authorize=False)
        data = unwrap_envelope(payload)
        token = str(data.get('token', '')).strip()
        if status_code >= 400 or not token:
            raise RuntimeBridgeError('failed to issue runtime token', status_code=status_code or 502)
        return token

    def get_runtime_snapshot(self) -> Dict[str, Any]:
        _, payload = self._request_json('GET', '/api/v1/runtime/snapshot', authorize=True)
        data = unwrap_envelope(payload)
        if not isinstance(data, dict):
            raise RuntimeBridgeError('runtime snapshot response is invalid')
        return data

    def get_inference_plan(self, budget: float) -> Dict[str, Any]:
        _, payload = self._request_json('POST', '/api/v1/inference/plan', payload={'budget': budget}, authorize=True)
        data = unwrap_envelope(payload)
        if not isinstance(data, dict):
            raise RuntimeBridgeError('inference plan response is invalid')
        return data

    def _request_json(
        self,
        method: str,
        path: str,
        payload: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
        authorize: bool = False,
    ) -> Tuple[int, Dict[str, Any]]:
        url = self.config.runtime_url.rstrip('/') + path
        body = None if payload is None else json.dumps(payload).encode('utf-8')
        req_headers = {'Accept': 'application/json'}
        if body is not None:
            req_headers['Content-Type'] = 'application/json'
        if headers:
            req_headers.update(headers)
        if authorize:
            token = self.token_provider.get_token() if self.token_provider is not None else self.issue_token()
            req_headers['Authorization'] = f'Bearer {token}'
        req = request.Request(url=url, data=body, headers=req_headers, method=method)
        try:
            with self.http_open(req, timeout=self.config.timeout_sec) as resp:
                return int(resp.status), self._decode_json(resp.read())
        except error.HTTPError as exc:
            return int(exc.code), self._decode_json(exc.read())
        except error.URLError as exc:
            raise RuntimeBridgeError(f'upstream request failed: {exc.reason}') from exc

    @staticmethod
    def _decode_json(raw: bytes) -> Dict[str, Any]:
        if not raw:
            return {}
        try:
            data = json.loads(raw.decode('utf-8'))
        except json.JSONDecodeError as exc:
            raise RuntimeBridgeError('upstream returned invalid json') from exc
        if isinstance(data, dict):
            return data
        raise RuntimeBridgeError('upstream json root is not an object')


class TokenProvider:
    def __init__(self, runtime_client: RuntimeApiClient):
        self.runtime_client = runtime_client
        self._cached_token = ''

    def get_token(self) -> str:
        if self._cached_token:
            return self._cached_token
        self._cached_token = self.runtime_client.issue_token()
        return self._cached_token

    def invalidate(self) -> None:
        self._cached_token = ''


class RuntimeBridgeService:
    def __init__(
        self,
        runtime_client: RuntimeApiClient,
        token_provider: Optional[TokenProvider],
        bridge_version: str = BRIDGE_VERSION,
        decode_mode: str = 'stub',
        default_plan_budget: float = 10.0,
        now_ms: Optional[Callable[[], int]] = None,
    ):
        self.runtime_client = runtime_client
        self.token_provider = token_provider
        self.bridge_version = bridge_version
        self.decode_mode = normalize_decode_mode(decode_mode)
        self.default_plan_budget = default_plan_budget
        self.now_ms = now_ms or (lambda: int(time.time() * 1000))

    def handle_health(self) -> Tuple[int, Dict[str, Any]]:
        try:
            snapshot = self.runtime_client.get_runtime_snapshot()
            return 200, {
                'status': 'ok',
                'runtime': 'rknn',
                'decode': f'bridge:{self.decode_mode}',
                'version': self.bridge_version,
                'runtime_snapshot': snapshot,
            }
        except RuntimeBridgeError as exc:
            return exc.status_code, {
                'status': 'down',
                'runtime': 'rknn',
                'decode': f'bridge:{self.decode_mode}',
                'version': self.bridge_version,
                'message': str(exc),
            }

    def handle_infer(self, payload: Dict[str, Any]) -> Tuple[int, Dict[str, Any]]:
        try:
            request_payload = payload if isinstance(payload, dict) else {}
            frame = request_payload.get('frame') if isinstance(request_payload.get('frame'), dict) else {}
            source = str(frame.get('source', '')).strip()
            if not source:
                return 400, build_error_payload('I4001', 'frame.source is required')

            trace_id = str(request_payload.get('trace_id', '')).strip() or f'bridge-{self.now_ms()}'
            camera_id = to_int(request_payload.get('camera_id'))
            model_id = to_int(request_payload.get('model_id'))
            if camera_id is None or model_id is None:
                return 400, build_error_payload('I4001', 'camera_id and model_id are required')

            plan_budget = to_float(request_payload.get('plan_budget'), self.default_plan_budget)
            plan = self.runtime_client.get_inference_plan(plan_budget)
            detections = self._build_detections(request_payload)
            result = {
                'trace_id': trace_id,
                'camera_id': camera_id,
                'latency_ms': max(1, int(self.now_ms() - self.now_ms() + 1)),
                'detections': detections,
                'backend_type': 'rk3588_rknn',
                'plan_summary': summarize_plan(plan, plan_budget),
                'frame': {
                    'source': source,
                    'timestamp_ms': to_int(frame.get('timestamp_ms')) or self.now_ms(),
                },
            }
            if model_id is not None:
                result['model_id'] = model_id
            return 200, result
        except RuntimeBridgeError as exc:
            return exc.status_code, build_error_payload('I5002', str(exc))

    def _build_detections(self, request_payload: Dict[str, Any]) -> list[Dict[str, Any]]:
        if self.decode_mode != 'echo-roi':
            return []
        roi = request_payload.get('roi')
        if not isinstance(roi, list):
            return []
        detections = []
        for index, item in enumerate(roi):
            if not isinstance(item, dict):
                continue
            bbox = normalize_bbox(item.get('bbox'))
            detections.append({
                'label': str(item.get('label', f'roi-{index}')),
                'score': to_float(item.get('score'), 1.0),
                'bbox': bbox,
            })
        return detections


class BridgeRequestHandler(BaseHTTPRequestHandler):
    service: RuntimeBridgeService

    def log_message(self, fmt: str, *args: Any) -> None:
        return

    def do_GET(self) -> None:
        if self.path == '/health':
            status_code, payload = self.service.handle_health()
            self._write_json(status_code, payload)
            return
        self._write_json(404, {'message': 'not found'})

    def do_POST(self) -> None:
        if self.path != '/v1/infer':
            self._write_json(404, {'message': 'not found'})
            return
        raw_body = self.rfile.read(int(self.headers.get('Content-Length', '0') or '0'))
        try:
            payload = json.loads(raw_body.decode('utf-8')) if raw_body else {}
        except json.JSONDecodeError:
            self._write_json(400, build_error_payload('I4001', 'request body must be valid json'))
            return
        status_code, response_payload = self.service.handle_infer(payload)
        self._write_json(status_code, response_payload)

    def _write_json(self, status_code: int, payload: Dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=True).encode('utf-8')
        self.send_response(status_code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def unwrap_envelope(payload: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(payload, dict):
        raise RuntimeBridgeError('upstream payload is invalid')
    if isinstance(payload.get('data'), dict):
        return dict(payload['data'])
    return payload


def build_error_payload(error_code: str, message: str) -> Dict[str, Any]:
    return {'error_code': error_code, 'message': message}


def summarize_plan(plan: Dict[str, Any], budget: float) -> Dict[str, Any]:
    return {
        'budget': budget,
        'stream_count': to_int(plan.get('stream_count')) or 0,
        'ready_stream_count': to_int(plan.get('ready_stream_count')) or 0,
        'item_count': len(plan.get('items', [])) if isinstance(plan.get('items'), list) else 0,
    }


def normalize_decode_mode(value: str) -> str:
    mode = str(value or 'stub').strip().lower()
    return 'echo-roi' if mode == 'echo-roi' else 'stub'


def normalize_bbox(value: Any) -> list[float]:
    if isinstance(value, list) and len(value) == 4:
        return [float(item) for item in value]
    return [0.0, 0.0, 0.0, 0.0]


def to_int(value: Any) -> Optional[int]:
    try:
        if value is None or str(value).strip() == '':
            return None
        return int(value)
    except (TypeError, ValueError):
        return None


def to_float(value: Any, default: float) -> float:
    try:
        if value is None or str(value).strip() == '':
            return default
        return float(value)
    except (TypeError, ValueError):
        return default


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Run RK3588 runtime compatibility bridge.')
    parser.add_argument('--listen-host', default='0.0.0.0')
    parser.add_argument('--listen-port', type=int, default=19080)
    parser.add_argument('--runtime-url', required=True)
    parser.add_argument('--runtime-token', default='')
    parser.add_argument('--runtime-bootstrap-token', default='')
    parser.add_argument('--runtime-token-role', default='admin')
    parser.add_argument('--runtime-user-id', default='java-rk3588-bridge')
    parser.add_argument('--bootstrap-header-name', default='X-Bootstrap-Token')
    parser.add_argument('--timeout-sec', type=float, default=5.0)
    parser.add_argument('--plan-budget', type=float, default=10.0)
    parser.add_argument('--decode-mode', default='stub')
    parser.add_argument('--bridge-version', default=BRIDGE_VERSION)
    return parser.parse_args(argv)


def build_service(args: argparse.Namespace) -> RuntimeBridgeService:
    config = RuntimeBridgeConfig(
        runtime_url=args.runtime_url,
        runtime_token=args.runtime_token,
        runtime_bootstrap_token=args.runtime_bootstrap_token,
        runtime_token_role=args.runtime_token_role,
        runtime_user_id=args.runtime_user_id,
        bootstrap_header_name=args.bootstrap_header_name,
        timeout_sec=args.timeout_sec,
        default_plan_budget=args.plan_budget,
        decode_mode=args.decode_mode,
        bridge_version=args.bridge_version,
    )
    client = RuntimeApiClient(config)
    token_provider = TokenProvider(client) if not config.runtime_token else None
    client.set_token_provider(token_provider)
    return RuntimeBridgeService(
        runtime_client=client,
        token_provider=token_provider,
        bridge_version=config.bridge_version,
        decode_mode=config.decode_mode,
        default_plan_budget=config.default_plan_budget,
    )


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    BridgeRequestHandler.service = build_service(args)
    server = ThreadingHTTPServer((args.listen_host, args.listen_port), BridgeRequestHandler)
    print(json.dumps({'listen_host': args.listen_host, 'listen_port': args.listen_port, 'runtime_url': args.runtime_url, 'decode_mode': normalize_decode_mode(args.decode_mode)}, ensure_ascii=True))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        return 0
    finally:
        server.server_close()
    return 0


if __name__ == '__main__':
    sys.exit(main())

