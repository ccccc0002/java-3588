#!/usr/bin/env python3
"""Compatibility bridge that exposes /health and /v1/infer for the Java RK3588 client."""

from __future__ import annotations

import argparse
import json
import sys
import time
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Callable, Dict, Optional, Tuple
from urllib import error, request


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from plugin_runtime import EXPECTED_PLUGIN_RUNTIME, PluginPackageManager, PluginRuntimeError

BRIDGE_VERSION = '0.3.0'
AUTH_ERROR_CODES = {'invalid_token', 'token_invalid', 'token_expired', 'unauthorized', 'access_denied'}


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
    plugins_root: str = ''
    default_plugin_id: str = ''
    expected_plugin_runtime: str = EXPECTED_PLUGIN_RUNTIME


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
        data = unwrap_envelope(payload, 'runtime auth token')
        token = str(data.get('token', '')).strip()
        if status_code >= 400 or not token:
            raise RuntimeBridgeError('failed to issue runtime token', status_code=status_code or 502)
        return token

    def get_runtime_snapshot(self) -> Dict[str, Any]:
        _, payload = self._request_json('GET', '/api/v1/runtime/snapshot', authorize=True)
        data = unwrap_envelope(payload, 'runtime snapshot')
        if not isinstance(data, dict):
            raise RuntimeBridgeError('runtime snapshot response is invalid')
        return data

    def get_inference_plan(self, budget: float) -> Dict[str, Any]:
        _, payload = self._request_json('POST', '/api/v1/inference/plan', payload={'budget': budget}, authorize=True)
        data = unwrap_envelope(payload, 'inference plan')
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
        retryable_auth = authorize and self.token_provider is not None
        attempts = 2 if retryable_auth else 1
        for attempt in range(attempts):
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
                    status_code = int(resp.status)
                    response_payload = self._decode_json(resp.read())
            except error.HTTPError as exc:
                status_code = int(exc.code)
                response_payload = self._decode_json(exc.read())
            except error.URLError as exc:
                raise RuntimeBridgeError(f'upstream request failed: {exc.reason}') from exc

            if retryable_auth and is_auth_error_response(status_code, response_payload):
                self.token_provider.invalidate()
                if attempt + 1 < attempts:
                    continue
            return status_code, response_payload
        return 502, build_error_payload('I5002', 'unreachable auth retry state')

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
        plugin_manager: Optional[PluginPackageManager] = None,
    ):
        self.runtime_client = runtime_client
        self.token_provider = token_provider
        self.bridge_version = bridge_version
        self.decode_mode = normalize_decode_mode(decode_mode)
        self.default_plan_budget = default_plan_budget
        self.now_ms = now_ms or (lambda: int(time.time() * 1000))
        self.plugin_manager = plugin_manager

    def handle_health(self) -> Tuple[int, Dict[str, Any]]:
        plugin_inventory = self.plugin_manager.inventory() if self.plugin_manager is not None else None
        decode_label = 'plugin' if self.plugin_manager is not None and self.plugin_manager.has_plugins() else self.decode_mode
        try:
            snapshot = self.runtime_client.get_runtime_snapshot()
            payload = {
                'status': 'ok',
                'runtime': 'rknn',
                'decode': f'bridge:{decode_label}',
                'version': self.bridge_version,
                'runtime_snapshot': snapshot,
            }
            if plugin_inventory is not None:
                payload['plugins'] = plugin_inventory
                payload['decode_fallback'] = f'bridge:{self.decode_mode}'
            return 200, payload
        except RuntimeBridgeError as exc:
            payload = {
                'status': 'ok',
                'runtime': 'rknn',
                'decode': f'bridge:{decode_label}',
                'version': self.bridge_version,
                'runtime_snapshot': build_offline_snapshot(str(exc)),
                'runtime_fallback': {'mode': 'offline', 'message': str(exc)},
            }
            if plugin_inventory is not None:
                payload['plugins'] = plugin_inventory
                payload['decode_fallback'] = f'bridge:{self.decode_mode}'
            return 200, payload

    def handle_infer(self, payload: Dict[str, Any]) -> Tuple[int, Dict[str, Any]]:
        started_at = self.now_ms()
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
            runtime_fallback = None
            try:
                plan = self.runtime_client.get_inference_plan(plan_budget)
            except RuntimeBridgeError as exc:
                runtime_fallback = {'mode': 'offline', 'message': str(exc)}
                plan = build_offline_plan(plan_budget, str(exc))
            result = {
                'trace_id': trace_id,
                'camera_id': camera_id,
                'backend_type': EXPECTED_PLUGIN_RUNTIME,
                'plan_summary': summarize_plan(plan, plan_budget),
                'frame': {
                    'source': source,
                    'timestamp_ms': to_int(frame.get('timestamp_ms')) or self.now_ms(),
                },
            }
            if runtime_fallback is not None:
                result['runtime_fallback'] = runtime_fallback
            if model_id is not None:
                result['model_id'] = model_id

            package = self.plugin_manager.resolve(request_payload) if self.plugin_manager is not None else None
            if package is not None:
                plugin_output = self.plugin_manager.execute(package, request_payload, plan)
                result['detections'] = plugin_output.get('detections', [])
                if isinstance(plugin_output.get('alerts'), list):
                    result['alerts'] = plugin_output.get('alerts', [])
                if isinstance(plugin_output.get('events'), list):
                    result['events'] = plugin_output.get('events', [])
                result['latency_ms'] = max(1, to_int(plugin_output.get('latency_ms')) or (self.now_ms() - started_at))
                result['plugin'] = plugin_output.get('plugin_meta', {})
                if isinstance(plugin_output.get('frame'), dict):
                    result['frame'].update(plugin_output['frame'])
                if isinstance(plugin_output.get('attributes'), dict):
                    result['attributes'] = plugin_output['attributes']
                return 200, result

            result['detections'] = self._build_detections(request_payload)
            result['latency_ms'] = max(1, self.now_ms() - started_at)
            return 200, result
        except PluginRuntimeError as exc:
            return exc.status_code, build_error_payload('I5002', str(exc))
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


def extract_error_details(payload: Dict[str, Any]) -> Tuple[str, str]:
    error_payload = payload.get('error') if isinstance(payload, dict) else None
    if isinstance(error_payload, dict):
        code = str(error_payload.get('code', '')).strip()
        message = str(error_payload.get('message', '')).strip()
        return code, message
    return '', ''


def is_auth_error_response(status_code: int, payload: Dict[str, Any]) -> bool:
    error_code, _ = extract_error_details(payload)
    if error_code in AUTH_ERROR_CODES:
        return True
    return status_code in (401, 403)


def unwrap_envelope(payload: Dict[str, Any], context: str = 'upstream request') -> Dict[str, Any]:
    if not isinstance(payload, dict):
        raise RuntimeBridgeError('upstream payload is invalid')
    if payload.get('success') is False:
        error_code, message = extract_error_details(payload)
        status_code = 401 if error_code in AUTH_ERROR_CODES else 502
        raise RuntimeBridgeError(message or f'{context} failed', status_code=status_code)
    if isinstance(payload.get('data'), dict):
        return dict(payload['data'])
    return payload


def build_error_payload(error_code: str, message: str) -> Dict[str, Any]:
    return {'error_code': error_code, 'message': message}


def build_offline_snapshot(message: str) -> Dict[str, Any]:
    return {
        'device_count': 0,
        'ready_stream_count': 0,
        'algorithm_count': 0,
        'fallback': {'mode': 'offline', 'message': message},
    }


def build_offline_plan(budget: float, message: str) -> Dict[str, Any]:
    return {
        'budget': budget,
        'stream_count': 0,
        'ready_stream_count': 0,
        'telemetry_status': 'degraded',
        'telemetry_error': 'runtime_offline',
        'throttle_hint': {
            'recommended_frame_stride': 1,
            'suggested_min_dispatch_ms': 1000,
            'strategy_source': 'offline_fallback',
        },
        'items': [],
        'fallback': {'mode': 'offline', 'message': message},
    }


def summarize_plan(plan: Dict[str, Any], budget: float) -> Dict[str, Any]:
    telemetry_status = str(plan.get('telemetry_status', 'ok')).strip().lower()
    if telemetry_status != 'degraded':
        telemetry_status = 'ok'
    telemetry_error = str(plan.get('telemetry_error', '')).strip()
    throttle_hint = plan.get('throttle_hint') if isinstance(plan.get('throttle_hint'), dict) else {}
    recommended_frame_stride = to_int(throttle_hint.get('recommended_frame_stride'))
    suggested_min_dispatch_ms = to_int(throttle_hint.get('suggested_min_dispatch_ms'))
    summary = {
        'budget': budget,
        'stream_count': to_int(plan.get('stream_count')) or 0,
        'ready_stream_count': to_int(plan.get('ready_stream_count')) or 0,
        'item_count': len(plan.get('items', [])) if isinstance(plan.get('items'), list) else 0,
        'telemetry_status': telemetry_status,
        'telemetry_error': telemetry_error,
        'recommended_frame_stride': recommended_frame_stride if recommended_frame_stride and recommended_frame_stride > 0 else 1,
        'suggested_min_dispatch_ms': suggested_min_dispatch_ms if suggested_min_dispatch_ms and suggested_min_dispatch_ms > 0 else 1000,
    }
    if isinstance(plan.get('fallback'), dict):
        summary['fallback'] = dict(plan['fallback'])
    return summary


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
    parser.add_argument('--plugins-root', default=str(SCRIPT_DIR / 'plugins'))
    parser.add_argument('--default-plugin-id', default='')
    parser.add_argument('--expected-plugin-runtime', default=EXPECTED_PLUGIN_RUNTIME)
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
        plugins_root=args.plugins_root,
        default_plugin_id=args.default_plugin_id,
        expected_plugin_runtime=args.expected_plugin_runtime,
    )
    client = RuntimeApiClient(config)
    token_provider = TokenProvider(client) if not config.runtime_token else None
    client.set_token_provider(token_provider)
    plugin_manager = PluginPackageManager(
        plugins_root=config.plugins_root,
        expected_runtime=config.expected_plugin_runtime,
        default_plugin_id=config.default_plugin_id,
    )
    return RuntimeBridgeService(
        runtime_client=client,
        token_provider=token_provider,
        bridge_version=config.bridge_version,
        decode_mode=config.decode_mode,
        default_plan_budget=config.default_plan_budget,
        plugin_manager=plugin_manager,
    )


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    BridgeRequestHandler.service = build_service(args)
    server = ThreadingHTTPServer((args.listen_host, args.listen_port), BridgeRequestHandler)
    print(json.dumps({'listen_host': args.listen_host, 'listen_port': args.listen_port, 'runtime_url': args.runtime_url, 'decode_mode': normalize_decode_mode(args.decode_mode), 'plugins_root': args.plugins_root, 'default_plugin_id': args.default_plugin_id}, ensure_ascii=True))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        return 0
    finally:
        server.server_close()
    return 0


if __name__ == '__main__':
    sys.exit(main())

