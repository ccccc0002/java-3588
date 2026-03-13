#!/usr/bin/env python3
"""Live smoke test for the built-in web UI and its backing endpoints."""

from __future__ import annotations

import argparse
import json
import time
from dataclasses import dataclass
from http.cookiejar import CookieJar
from typing import Any, Dict, List, Optional, Tuple
from urllib import error, parse, request
from urllib.parse import urlparse


@dataclass(frozen=True)
class SmokeTarget:
    method: str
    path: str
    data: Optional[Dict[str, Any]] = None
    expect_json: bool = False
    name: str = ''
    timeout_sec: Optional[float] = None


class HttpSession:
    def __init__(self, base_url: str, timeout_sec: float = 15.0):
        self.base_url = base_url.rstrip('/')
        self.timeout_sec = timeout_sec
        self.cookie_jar = CookieJar()
        self.opener = request.build_opener(request.HTTPCookieProcessor(self.cookie_jar))

    def open(
        self,
        method: str,
        path: str,
        data: Optional[Dict[str, Any]] = None,
        headers: Optional[Dict[str, str]] = None,
        timeout_sec: Optional[float] = None,
    ) -> Tuple[int, bytes, Dict[str, str]]:
        url = self.base_url + path
        body = None
        final_headers = {'Accept': 'application/json, text/html;q=0.9, */*;q=0.8'}
        if headers:
            final_headers.update(headers)
        if data is not None:
            body = parse.urlencode({k: '' if v is None else str(v) for k, v in data.items()}).encode('utf-8')
            final_headers.setdefault('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8')
        req = request.Request(url, data=body, headers=final_headers, method=method.upper())
        effective_timeout = self.timeout_sec if timeout_sec is None else max(1.0, float(timeout_sec))
        try:
            with self.opener.open(req, timeout=effective_timeout) as resp:
                return resp.getcode(), resp.read(), dict(resp.headers.items())
        except error.HTTPError as exc:
            return exc.code, exc.read(), dict(exc.headers.items())
        except Exception as exc:
            message = str(getattr(exc, 'reason', exc)) or exc.__class__.__name__
            payload = json.dumps({'code': -1, 'msg': message}, ensure_ascii=False).encode('utf-8')
            return 0, payload, {
                'Content-Type': 'application/json; charset=UTF-8',
                'X-Smoke-Error': exc.__class__.__name__,
            }


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def decode_body(body: bytes) -> str:
    return body.decode('utf-8', 'replace')


def parse_json(body: bytes) -> Any:
    return json.loads(decode_body(body))


def smoke_login(session: HttpSession, username: str, password: str) -> Dict[str, Any]:
    status, body, _ = session.open('POST', '/login', {'account': username, 'password': password})
    require(status == 200, f'login failed with http {status}')
    payload = parse_json(body)
    require(payload.get('code') == 0, f'login failed: {payload}')
    return payload


def collect_camera_context(session: HttpSession) -> Dict[str, Any]:
    fallback = {
        'camera_id': 1,
        'rtsp_url': '',
    }
    status, body, _ = session.open('POST', '/camera/listData')
    if status != 200:
        return fallback

    try:
        payload = parse_json(body)
    except Exception:
        return fallback

    if not isinstance(payload, dict) or payload.get('code') != 0:
        return fallback

    data = payload.get('data') or []
    if not data:
        return fallback

    preferred = next((item for item in data if item.get('rtspUrl')), data[0])
    return {
        'camera_id': preferred.get('id', 1),
        'rtsp_url': preferred.get('rtspUrl', ''),
    }


def run_target(session: HttpSession, target: SmokeTarget, base_url: str = '') -> Dict[str, Any]:
    status, body, headers = session.open(target.method, target.path, target.data, timeout_sec=target.timeout_sec)
    result: Dict[str, Any] = {
        'name': target.name or target.path,
        'method': target.method,
        'path': target.path,
        'http_status': status,
        'content_type': headers.get('Content-Type', ''),
    }
    if 'X-Smoke-Error' in headers:
        result['transport_error'] = headers['X-Smoke-Error']

    text = decode_body(body)
    if target.expect_json:
        try:
            payload = parse_json(body)
        except Exception as exc:
            result['ok'] = False
            result['error'] = f'Invalid JSON: {exc}'
            result['snippet'] = text[:240]
            return result

        result['payload'] = payload
        if isinstance(payload, dict):
            result['ok'] = status == 200 and payload.get('code', 0) in {0, 200}
            if not result['ok']:
                result['error'] = payload.get('msg', f'Unexpected response code: {payload.get("code")}')
            return result

        result['ok'] = status == 200
        if not result['ok']:
            result['error'] = 'Unexpected non-object JSON payload'
        return validate_stream_urls(base_url, result)

    result['snippet'] = text[:240]
    result['ok'] = status == 200 and '<html' in text.lower()
    if not result['ok']:
        result['error'] = f'Expected html page, got status={status}'
    return result


def should_retry_target(result: Dict[str, Any]) -> bool:
    if result.get('ok'):
        return False
    status = int(result.get('http_status') or 0)
    if status in {0, 408, 429, 502, 503, 504}:
        return True
    if result.get('transport_error'):
        return True
    error_text = str(result.get('error') or '').lower()
    return any(keyword in error_text for keyword in ('timed out', 'timeout', 'temporarily unavailable'))


def run_target_with_retries(
    session: HttpSession,
    target: SmokeTarget,
    base_url: str,
    max_attempts: int,
    retry_interval_ms: int,
    retry_on_any_failure: bool = False,
) -> Dict[str, Any]:
    attempts = max(int(max_attempts), 1)
    interval_sec = max(int(retry_interval_ms), 0) / 1000.0
    history: List[Dict[str, Any]] = []
    current = run_target(session, target, base_url)
    history.append(dict(current))
    for _ in range(1, attempts):
        can_retry = should_retry_target(current) or (retry_on_any_failure and not current.get('ok'))
        if not can_retry:
            break
        if interval_sec > 0:
            time.sleep(interval_sec)
        current = run_target(session, target, base_url)
        history.append(dict(current))

    merged = dict(current)
    merged['attempts'] = len(history)
    if len(history) > 1:
        merged['retry_history'] = history[:-1]
    return merged


def is_loopback_host(value: str) -> bool:
    host = str(value or '').strip().lower()
    return host in {'127.0.0.1', 'localhost', '0.0.0.0', '::1', '[::1]'}


def validate_stream_urls(base_url: str, result: Dict[str, Any]) -> Dict[str, Any]:
    payload = result.get('payload')
    if not isinstance(payload, dict):
        return result
    rows = payload.get('data') if isinstance(payload.get('data'), list) else []
    if not rows:
        return result
    base_host = urlparse(base_url).hostname or ''
    if is_loopback_host(base_host):
        return result
    for row in rows:
        if not isinstance(row, dict):
            continue
        play_url = str(row.get('playUrl') or row.get('play_url') or '').strip()
        if not play_url:
            continue
        play_host = urlparse(play_url).hostname or ''
        if is_loopback_host(play_host):
            result['ok'] = False
            result['error'] = f'playUrl points to loopback host: {play_url}'
            return result
    return result


def build_targets(camera_context: Dict[str, Any], include_capture_endpoints: bool = True) -> List[SmokeTarget]:
    camera_id = str(camera_context.get('camera_id') or '1')
    rtsp_url = str(camera_context.get('rtsp_url') or '')
    targets = [
        SmokeTarget('GET', '/login', name='login_page'),
        SmokeTarget('GET', '/', name='home_page'),
        SmokeTarget('GET', '/account', name='account_page'),
        SmokeTarget('GET', '/algorithm', name='algorithm_page'),
        SmokeTarget('GET', '/camera', name='camera_page'),
        SmokeTarget('GET', f'/camera/form?id={camera_id}', name='camera_form_page'),
        SmokeTarget('GET', '/config', name='config_page'),
        SmokeTarget('GET', '/location/form', name='location_form_page'),
        SmokeTarget('GET', '/model', name='model_page'),
        SmokeTarget('GET', '/report', name='report_page'),
        SmokeTarget('GET', '/statistic', name='statistic_page'),
        SmokeTarget('GET', '/stream', name='stream_page'),
        SmokeTarget('GET', '/stream/select_play', name='stream_select_play_page'),
        SmokeTarget('GET', '/warehouse', name='warehouse_page'),
        SmokeTarget('GET', '/testimage', name='testimage_page'),
        SmokeTarget('GET', '/testimage/ffmpeg', name='testimage_ffmpeg_page'),
        SmokeTarget('POST', '/account/listData', expect_json=True),
        SmokeTarget('POST', '/algorithm/listData', expect_json=True),
        SmokeTarget('POST', '/camera/listData', expect_json=True),
        SmokeTarget('POST', '/camera/listPage', {'page': 1, 'limit': 10}, True),
        SmokeTarget('POST', '/camera/algorithm/listData', {'cameraId': camera_id}, True),
        SmokeTarget('POST', '/camera/running', expect_json=True),
        SmokeTarget('POST', '/config/listData', expect_json=True),
        SmokeTarget('POST', '/model/listData', {'page': 1, 'limit': 10}, True),
        SmokeTarget('POST', '/report/listPage', {'page': 1, 'limit': 10}, True),
        SmokeTarget('POST', '/stream/play_list', expect_json=True),
        SmokeTarget('POST', '/stream/camera_list', {'page': 1, 'limit': 10}, True),
        SmokeTarget('POST', '/stream/statics/algorithms', expect_json=True),
        SmokeTarget('POST', '/stream/statics/counter', expect_json=True),
        SmokeTarget('POST', '/warehouse/listPage', {'page': 1, 'limit': 10}, True),
        SmokeTarget('POST', '/warehouse/listTree', expect_json=True),
        SmokeTarget('POST', '/location/listTree', expect_json=True),
        SmokeTarget('POST', '/testimage/ffmpeg', {'encoder': 'h264_rkmpp', 'decoder': 'hevc_rkmpp'}, True, timeout_sec=20.0),
    ]
    if include_capture_endpoints and rtsp_url:
        targets.extend([
            SmokeTarget('POST', '/camera/takePhoto', {'rtspUrl': rtsp_url}, True, timeout_sec=25.0),
            SmokeTarget('POST', '/testimage/get', {'indexCode': camera_id}, True, timeout_sec=25.0),
        ])
    return targets


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description='Live smoke test for web UI + API bindings')
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--username', required=True)
    parser.add_argument('--password', required=True)
    parser.add_argument('--timeout-sec', type=float, default=15.0)
    parser.add_argument('--retry-attempts', type=int, default=2)
    parser.add_argument('--retry-interval-ms', type=int, default=200)
    parser.add_argument('--retry-on-any-failure', action='store_true', default=False)
    parser.add_argument('--skip-capture-endpoints', action='store_true', default=False)
    args = parser.parse_args(argv)

    session = HttpSession(args.base_url, timeout_sec=max(1.0, float(args.timeout_sec)))
    smoke_login(session, args.username, args.password)
    camera_context = collect_camera_context(session)
    results = [
        run_target_with_retries(
            session=session,
            target=target,
            base_url=args.base_url,
            max_attempts=args.retry_attempts,
            retry_interval_ms=args.retry_interval_ms,
            retry_on_any_failure=args.retry_on_any_failure,
        )
        for target in build_targets(
            camera_context,
            include_capture_endpoints=not args.skip_capture_endpoints,
        )
    ]

    failed = [item for item in results if not item.get('ok')]
    summary = {
        'base_url': args.base_url,
        'camera_context': camera_context,
        'total': len(results),
        'passed': len(results) - len(failed),
        'failed': len(failed),
        'results': results,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0 if not failed else 1


if __name__ == '__main__':
    raise SystemExit(main())
