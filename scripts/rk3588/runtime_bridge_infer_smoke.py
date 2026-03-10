#!/usr/bin/env python3
"""Run a smoke inference call against the RK3588 runtime bridge."""

from __future__ import annotations

import argparse
import json
import time
from typing import Any, Dict, Optional
from urllib import error, request


def build_request_payload(
    source: str,
    trace_id: str,
    camera_id: int,
    model_id: int,
    plugin_id: str = '',
    timestamp_ms: Optional[int] = None,
) -> Dict[str, Any]:
    payload: Dict[str, Any] = {
        'trace_id': str(trace_id).strip() or f'trace-{int(time.time() * 1000)}',
        'camera_id': int(camera_id),
        'model_id': int(model_id),
        'frame': {
            'source': str(source).strip(),
            'timestamp_ms': int(timestamp_ms if timestamp_ms is not None else time.time() * 1000),
        },
    }
    plugin_text = str(plugin_id or '').strip()
    if plugin_text:
        payload['plugin_route'] = {'requested': True, 'plugin': {'plugin_id': plugin_text}}
    return payload


def post_infer(bridge_url: str, payload: Dict[str, Any], timeout_sec: float = 60.0, http_open=None) -> Dict[str, Any]:
    http_open = http_open or request.urlopen
    url = bridge_url.rstrip('/') + '/v1/infer'
    body = json.dumps(payload, ensure_ascii=True).encode('utf-8')
    req = request.Request(url=url, data=body, headers={'Content-Type': 'application/json', 'Accept': 'application/json'}, method='POST')
    try:
        with http_open(req, timeout=max(1.0, float(timeout_sec))) as resp:
            status_code = int(resp.status)
            response_payload = json.loads(resp.read().decode('utf-8'))
    except error.HTTPError as exc:
        status_code = int(exc.code)
        response_payload = json.loads(exc.read().decode('utf-8'))
    if status_code >= 400:
        raise RuntimeError(f'infer request failed with status {status_code}: {response_payload}')
    if not isinstance(response_payload, dict):
        raise RuntimeError('infer response is not a json object')
    return response_payload


def validate_response(payload: Dict[str, Any], expected_plugin_id: str = '') -> Dict[str, Any]:
    backend_type = str(payload.get('backend_type', '')).strip()
    if backend_type != 'rk3588_rknn':
        raise RuntimeError(f'unexpected backend_type: {backend_type}')
    detections = payload.get('detections') if isinstance(payload.get('detections'), list) else []
    plugin = payload.get('plugin') if isinstance(payload.get('plugin'), dict) else {}
    plugin_id = str(plugin.get('plugin_id', '')).strip()
    if expected_plugin_id and plugin_id != expected_plugin_id:
        raise RuntimeError(f'plugin_id mismatch: expected {expected_plugin_id}, got {plugin_id or "<empty>"}')
    if expected_plugin_id and not plugin_id:
        raise RuntimeError('plugin_id is missing from infer response')
    return {
        'backend_type': backend_type,
        'plugin_id': plugin_id,
        'detection_count': len(detections),
        'alert_count': len(payload.get('alerts') or []),
        'latency_ms': payload.get('latency_ms'),
        'labels': [item.get('label') for item in detections if isinstance(item, dict)],
    }


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Smoke test RK3588 runtime bridge inference endpoint')
    parser.add_argument('--bridge-url', default='http://127.0.0.1:19080')
    parser.add_argument('--source', default='test://frame')
    parser.add_argument('--trace-id', default='')
    parser.add_argument('--camera-id', type=int, default=1)
    parser.add_argument('--model-id', type=int, default=1)
    parser.add_argument('--plugin-id', default='yolov8n')
    parser.add_argument('--timeout-sec', type=float, default=60.0)
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    payload = build_request_payload(
        source=args.source,
        trace_id=args.trace_id,
        camera_id=args.camera_id,
        model_id=args.model_id,
        plugin_id=args.plugin_id,
    )
    response_payload = post_infer(args.bridge_url, payload, timeout_sec=args.timeout_sec)
    summary = validate_response(response_payload, expected_plugin_id=args.plugin_id)
    print(json.dumps({'request': payload, 'summary': summary, 'response': response_payload}, ensure_ascii=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
