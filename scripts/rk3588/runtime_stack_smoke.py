#!/usr/bin/env python3
"""Run an end-to-end smoke check for the RK3588 runtime stack."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any, Dict, Optional
from urllib import error, request

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import runtime_bridge_infer_smoke


def _json_request(url: str, headers: Optional[Dict[str, str]] = None, body: Optional[Dict[str, Any]] = None, timeout_sec: float = 10.0, http_open=None) -> Dict[str, Any]:
    http_open = http_open or request.urlopen
    payload = None if body is None else json.dumps(body, ensure_ascii=True).encode('utf-8')
    req = request.Request(
        url=url,
        headers=headers or {'Accept': 'application/json'},
        data=payload,
        method='POST' if body is not None else 'GET',
    )
    try:
        with http_open(req, timeout=max(1.0, float(timeout_sec))) as resp:
            status_code = int(resp.status)
            response_payload = json.loads(resp.read().decode('utf-8'))
    except error.HTTPError as exc:
        status_code = int(exc.code)
        response_payload = json.loads(exc.read().decode('utf-8'))
    if status_code >= 400:
        raise RuntimeError(f'request failed with status {status_code}: {response_payload}')
    return response_payload if isinstance(response_payload, dict) else {'payload': response_payload}


def issue_runtime_token(runtime_api_url: str, bootstrap_token: str, user_id: str = 'edge-user', role: str = 'admin', timeout_sec: float = 10.0, http_open=None) -> Dict[str, Any]:
    return _json_request(
        url=runtime_api_url.rstrip('/') + '/api/v1/auth/token',
        headers={
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'X-Bootstrap-Token': str(bootstrap_token).strip(),
        },
        body={'user_id': user_id, 'role': role},
        timeout_sec=timeout_sec,
        http_open=http_open,
    )


def get_runtime_snapshot(runtime_api_url: str, token: str, timeout_sec: float = 10.0, http_open=None) -> Dict[str, Any]:
    return _json_request(
        url=runtime_api_url.rstrip('/') + '/api/v1/runtime/snapshot',
        headers={'Accept': 'application/json', 'Authorization': f'Bearer {token}'},
        timeout_sec=timeout_sec,
        http_open=http_open,
    )


def get_inference_plan(runtime_api_url: str, token: str, budget: float = 10.0, timeout_sec: float = 10.0, http_open=None) -> Dict[str, Any]:
    return _json_request(
        url=runtime_api_url.rstrip('/') + '/api/v1/inference/plan',
        headers={'Accept': 'application/json', 'Content-Type': 'application/json', 'Authorization': f'Bearer {token}'},
        body={'budget': float(budget)},
        timeout_sec=timeout_sec,
        http_open=http_open,
    )


def get_bridge_health(bridge_url: str, timeout_sec: float = 10.0, http_open=None) -> Dict[str, Any]:
    return _json_request(
        url=bridge_url.rstrip('/') + '/health',
        headers={'Accept': 'application/json'},
        timeout_sec=timeout_sec,
        http_open=http_open,
    )


def verify_play_url(play_url: str, timeout_sec: float = 10.0, http_open=None) -> Dict[str, Any]:
    http_open = http_open or request.urlopen
    req = request.Request(play_url, headers={'Accept': '*/*'}, method='GET')
    with http_open(req, timeout=max(1.0, float(timeout_sec))) as resp:
        chunk = resp.read(16)
        return {
            'http_status': int(resp.status),
            'bytes_read': len(chunk or b''),
            'readable': bool(chunk),
        }


def run_stack_smoke(
    runtime_api_url: str,
    bridge_url: str,
    bootstrap_token: str,
    plugin_id: str = 'yolov8n',
    source: str = 'test://frame',
    camera_id: int = 1,
    model_id: int = 1,
    budget: float = 10.0,
    timeout_sec: float = 30.0,
) -> Dict[str, Any]:
    token_payload = issue_runtime_token(runtime_api_url, bootstrap_token, timeout_sec=timeout_sec)
    token = str(((token_payload.get('data') or {}) if isinstance(token_payload, dict) else {}).get('token', '')).strip()
    if not token:
        raise RuntimeError(f'runtime token response is missing token: {token_payload}')

    snapshot_payload = get_runtime_snapshot(runtime_api_url, token, timeout_sec=timeout_sec)
    snapshot_data = snapshot_payload.get('data') if isinstance(snapshot_payload.get('data'), dict) else {}
    plan_payload = get_inference_plan(runtime_api_url, token, budget=budget, timeout_sec=timeout_sec)
    plan_data = plan_payload.get('data') if isinstance(plan_payload.get('data'), dict) else {}
    bridge_health = get_bridge_health(bridge_url, timeout_sec=timeout_sec)

    infer_request = runtime_bridge_infer_smoke.build_request_payload(
        source=source,
        trace_id='',
        camera_id=camera_id,
        model_id=model_id,
        plugin_id=plugin_id,
    )
    infer_payload = runtime_bridge_infer_smoke.post_infer(bridge_url, infer_request, timeout_sec=timeout_sec)
    infer_summary = runtime_bridge_infer_smoke.validate_response(infer_payload, expected_plugin_id=plugin_id)

    streams = snapshot_data.get('streams') if isinstance(snapshot_data.get('streams'), list) else []
    play_url = ''
    if streams and isinstance(streams[0], dict):
        play_url = str(streams[0].get('play_url', '')).strip()
    if not play_url:
        raise RuntimeError(f'play_url is missing from runtime snapshot: {snapshot_payload}')
    play_check = verify_play_url(play_url)

    return {
        'runtime_api': {
            'token': {'token': token},
            'snapshot': {
                'stream_count': int(snapshot_data.get('stream_count', len(streams) or 0)),
                'ready_stream_count': int(snapshot_data.get('ready_stream_count', 0)),
                'play_url': play_url,
            },
            'plan': {
                'budget': plan_data.get('budget'),
                'ready_stream_count': plan_data.get('ready_stream_count'),
                'stream_count': plan_data.get('stream_count'),
            },
        },
        'bridge': {
            'health': {
                'status': bridge_health.get('status'),
                'runtime': bridge_health.get('runtime'),
                'decode': bridge_health.get('decode'),
            },
            'infer': infer_summary,
        },
        'zlm': {
            'play_check': play_check,
        },
    }


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Smoke test the full RK3588 runtime stack')
    parser.add_argument('--runtime-api-url', default='http://127.0.0.1:18081')
    parser.add_argument('--bridge-url', default='http://127.0.0.1:19080')
    parser.add_argument('--bootstrap-token', default='edge-demo-bootstrap')
    parser.add_argument('--plugin-id', default='yolov8n')
    parser.add_argument('--source', default='test://frame')
    parser.add_argument('--camera-id', type=int, default=1)
    parser.add_argument('--model-id', type=int, default=1)
    parser.add_argument('--budget', type=float, default=10.0)
    parser.add_argument('--timeout-sec', type=float, default=30.0)
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    result = run_stack_smoke(
        runtime_api_url=args.runtime_api_url,
        bridge_url=args.bridge_url,
        bootstrap_token=args.bootstrap_token,
        plugin_id=args.plugin_id,
        source=args.source,
        camera_id=args.camera_id,
        model_id=args.model_id,
        budget=args.budget,
        timeout_sec=args.timeout_sec,
    )
    print(json.dumps(result, ensure_ascii=True))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
