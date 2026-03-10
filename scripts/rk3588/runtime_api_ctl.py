#!/usr/bin/env python3
"""Control the Runtime API process on port 18081."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional, Tuple
from urllib import error, request


PYTHON_FALLBACK_BACKEND = 'python_fallback'
PYTHON_FALLBACK_PATTERN = 'runtime_api_fallback.py'


@dataclass(frozen=True)
class RuntimeApiControllerConfig:
    repo_root: Path
    runtime_dir: Path
    start_script: Path
    health_url: str
    wait_seconds: float = 40.0
    poll_interval: float = 1.0
    stop_wait_seconds: float = 10.0
    stop_pattern: str = 'java-rk3588-0.0.1-SNAPSHOT.jar'
    fallback_stop_pattern: str = PYTHON_FALLBACK_PATTERN
    env_path: Optional[Path] = None
    snapshot_seed_path: Optional[Path] = None

    def __post_init__(self) -> None:
        if self.env_path is None:
            object.__setattr__(self, 'env_path', self.runtime_dir / 'runtime-api.env')
        if self.snapshot_seed_path is None:
            object.__setattr__(self, 'snapshot_seed_path', self.runtime_dir / 'runtime-api-fallback-snapshot.json')


def default_config() -> RuntimeApiControllerConfig:
    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent.parent
    runtime_dir = repo_root / 'runtime'
    return RuntimeApiControllerConfig(
        repo_root=repo_root,
        runtime_dir=runtime_dir,
        start_script=repo_root / 'scripts' / 'rk3588' / 'Run-Runtime-Api.sh',
        health_url='http://127.0.0.1:18081/api/v1/runtime/health',
    )


def load_persisted_env(env_path: Path) -> Dict[str, str]:
    try:
        lines = env_path.read_text(encoding='utf-8').splitlines()
    except FileNotFoundError:
        return {}
    env: Dict[str, str] = {}
    for raw_line in lines:
        line = raw_line.strip()
        if not line or line.startswith('#') or '=' not in line:
            continue
        key, value = line.split('=', 1)
        key = key.strip()
        if key:
            env[key] = value
    return env


def write_persisted_env(env_path: Path, env_values: Dict[str, str]) -> None:
    env_path.parent.mkdir(parents=True, exist_ok=True)
    lines = [f'{key}={env_values[key]}' for key in sorted(env_values.keys()) if str(key).strip()]
    env_path.write_text('\n'.join(lines) + ('\n' if lines else ''), encoding='utf-8')


def normalize_env_list(values: Optional[list[str]]) -> Dict[str, str]:
    env: Dict[str, str] = {}
    for raw in values or []:
        item = str(raw or '').strip()
        if not item or '=' not in item:
            continue
        key, value = item.split('=', 1)
        key = key.strip()
        if key:
            env[key] = value
    return env


def fetch_health(health_url: str, timeout: float = 2.0) -> Dict[str, Any]:
    req = request.Request(health_url, headers={'Accept': 'application/json'}, method='GET')
    try:
        with request.urlopen(req, timeout=timeout) as resp:
            payload = json.loads(resp.read().decode('utf-8'))
            return {'http_status': int(resp.status), 'payload': payload}
    except error.HTTPError as exc:
        try:
            payload = json.loads(exc.read().decode('utf-8'))
        except Exception:
            payload = {'message': str(exc)}
        return {'http_status': int(exc.code), 'payload': payload}
    except Exception as exc:
        return {'http_status': 0, 'payload': {'message': str(exc)}}


def extract_backend_from_health(health: Dict[str, Any]) -> str:
    payload = health.get('payload') if isinstance(health, dict) else {}
    if not isinstance(payload, dict):
        return ''
    data = payload.get('data') if isinstance(payload.get('data'), dict) else {}
    backend = str(data.get('backend', '')).strip()
    if backend:
        return backend
    return str(payload.get('backend', '')).strip()


def enrich_runtime_result(result: Dict[str, Any], default_backend: str = '') -> Dict[str, Any]:
    enriched = dict(result)
    health = enriched.get('health') if isinstance(enriched.get('health'), dict) else {}
    backend = extract_backend_from_health(health) or str(default_backend or '').strip()
    if backend:
        enriched['backend'] = backend
    return enriched


def wait_for_health(config: RuntimeApiControllerConfig) -> Dict[str, Any]:
    deadline = time.time() + max(0.1, config.wait_seconds)
    last = {'http_status': 0, 'payload': {'message': 'health check not started'}}
    while time.time() < deadline:
        last = fetch_health(config.health_url)
        if int(last.get('http_status', 0)) == 200:
            return last
        time.sleep(max(0.01, config.poll_interval))
    return last


def wait_for_down(config: RuntimeApiControllerConfig) -> Dict[str, Any]:
    deadline = time.time() + max(0.1, config.stop_wait_seconds)
    last = {'http_status': 200, 'payload': {'message': 'shutdown check not started'}}
    while time.time() < deadline:
        last = fetch_health(config.health_url)
        if int(last.get('http_status', 0)) != 200:
            return last
        time.sleep(max(0.01, config.poll_interval))
    return last


def derive_runtime_api_base_url(health_url: str) -> str:
    marker = '/api/v1/runtime/health'
    if health_url.endswith(marker):
        return health_url[:-len(marker)]
    return health_url.rsplit('/api/', 1)[0] if '/api/' in health_url else health_url.rstrip('/')


def request_json(url: str, method: str = 'GET', headers: Optional[Dict[str, str]] = None, payload: Optional[Dict[str, Any]] = None, timeout: float = 5.0) -> Dict[str, Any]:
    body = None if payload is None else json.dumps(payload, ensure_ascii=True).encode('utf-8')
    request_headers = {'Accept': 'application/json'}
    if body is not None:
        request_headers['Content-Type'] = 'application/json'
    if headers:
        request_headers.update(headers)
    req = request.Request(url, data=body, headers=request_headers, method=method)
    with request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode('utf-8'))


def resolve_bootstrap_token(env_values: Dict[str, str]) -> str:
    return str(env_values.get('RUNTIME_BOOTSTRAP_TOKEN', '')).strip()


def export_runtime_snapshot_seed(config: RuntimeApiControllerConfig, env_values: Dict[str, str]) -> Dict[str, Any]:
    bootstrap_token = resolve_bootstrap_token(env_values)
    if not bootstrap_token:
        return {'status': 'skipped', 'reason': 'missing_bootstrap_token'}
    base_url = derive_runtime_api_base_url(config.health_url)
    token_payload = request_json(
        base_url + '/api/v1/auth/token',
        method='POST',
        headers={'X-Bootstrap-Token': bootstrap_token},
        payload={'user_id': 'runtime-api-ctl', 'role': 'admin'},
        timeout=max(2.0, config.poll_interval * 10),
    )
    token = str(((token_payload.get('data') or {}) if isinstance(token_payload, dict) else {}).get('token', '')).strip()
    if not token:
        return {'status': 'skipped', 'reason': 'missing_token'}
    snapshot_payload = request_json(
        base_url + '/api/v1/runtime/snapshot',
        method='GET',
        headers={'Authorization': f'Bearer {token}'},
        timeout=max(2.0, config.poll_interval * 10),
    )
    snapshot_data = snapshot_payload.get('data') if isinstance(snapshot_payload.get('data'), dict) else {}
    config.snapshot_seed_path.parent.mkdir(parents=True, exist_ok=True)
    config.snapshot_seed_path.write_text(json.dumps(snapshot_data, ensure_ascii=True), encoding='utf-8')
    return {
        'status': 'exported',
        'snapshot_seed_path': str(config.snapshot_seed_path),
        'stream_count': len(snapshot_data.get('streams', [])) if isinstance(snapshot_data.get('streams'), list) else 0,
    }


def launch_runtime_api(config: RuntimeApiControllerConfig, child_env: Dict[str, str]) -> subprocess.Popen[Any]:
    return subprocess.Popen(
        ['bash', str(config.start_script)],
        cwd=str(config.repo_root),
        env=child_env,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )


def resolve_requested_backend(persisted_env: Dict[str, str]) -> str:
    return str(persisted_env.get('RUNTIME_API_BACKEND', '')).strip().lower()


def start_runtime_api(config: RuntimeApiControllerConfig, extra_env: Optional[Dict[str, str]] = None) -> Dict[str, Any]:
    current = fetch_health(config.health_url)
    if int(current.get('http_status', 0)) == 200:
        return enrich_runtime_result({'status': 'already_running', 'health': current, 'script': str(config.start_script)}, default_backend='java')
    if not config.start_script.exists():
        return {'status': 'missing_start_script', 'script': str(config.start_script)}

    persisted_env = load_persisted_env(config.env_path)
    if extra_env:
        persisted_env.update({str(key): str(value) for key, value in extra_env.items() if str(key).strip()})
        write_persisted_env(config.env_path, persisted_env)

    requested_backend = resolve_requested_backend(persisted_env)
    base_env = os.environ.copy()
    base_env.update(persisted_env)

    def attempt(backend: str) -> Dict[str, Any]:
        child_env = dict(base_env)
        if backend == PYTHON_FALLBACK_BACKEND:
            child_env['RUNTIME_API_BACKEND'] = PYTHON_FALLBACK_BACKEND
        else:
            child_env.pop('RUNTIME_API_BACKEND', None)
        process = launch_runtime_api(config, child_env)
        health = wait_for_health(config)
        if int(health.get('http_status', 0)) == 200:
            return enrich_runtime_result({
                'status': 'started',
                'pid': process.pid,
                'backend': backend or 'java',
                'health': health,
                'script': str(config.start_script),
                'env_path': str(config.env_path),
            }, default_backend=backend or 'java')
        return enrich_runtime_result({
            'status': 'runtime_unhealthy',
            'pid': process.pid,
            'backend': backend or 'java',
            'health': health,
            'script': str(config.start_script),
            'env_path': str(config.env_path),
        }, default_backend=backend or 'java')

    if requested_backend == PYTHON_FALLBACK_BACKEND:
        return attempt(PYTHON_FALLBACK_BACKEND)

    java_result = attempt('java')
    if java_result.get('status') == 'started':
        return java_result
    fallback_result = attempt(PYTHON_FALLBACK_BACKEND)
    if fallback_result.get('status') == 'started':
        return fallback_result
    return {
        'status': 'runtime_unhealthy',
        'backend': PYTHON_FALLBACK_BACKEND,
        'java_attempt': java_result,
        'fallback_attempt': fallback_result,
        'script': str(config.start_script),
        'env_path': str(config.env_path),
        'health': fallback_result.get('health', java_result.get('health', {})),
    }


def stop_runtime_api(config: RuntimeApiControllerConfig) -> Dict[str, Any]:
    patterns = [config.stop_pattern, config.fallback_stop_pattern]
    stop_results = []
    for pattern in patterns:
        completed = subprocess.run(['pkill', '-f', pattern], capture_output=True, text=True)
        stop_results.append({'pattern': pattern, 'exit_code': completed.returncode, 'stderr': completed.stderr})
        if completed.returncode not in (0, 1):
            return {
                'status': 'stop_failed',
                'pattern': pattern,
                'exit_code': completed.returncode,
                'stderr': completed.stderr,
                'results': stop_results,
            }
    health = wait_for_down(config)
    if int(health.get('http_status', 0)) != 200:
        return {'status': 'stopped', 'patterns': patterns, 'results': stop_results, 'health': health}
    return {'status': 'stop_pending_exit', 'patterns': patterns, 'results': stop_results, 'health': health}


def status_runtime_api(config: RuntimeApiControllerConfig) -> Dict[str, Any]:
    health = fetch_health(config.health_url)
    if int(health.get('http_status', 0)) == 200:
        return enrich_runtime_result({'status': 'running', 'health': health, 'script': str(config.start_script), 'env_path': str(config.env_path)}, default_backend='java')
    return {'status': 'down', 'health': health, 'script': str(config.start_script), 'env_path': str(config.env_path)}


def restart_runtime_api(config: RuntimeApiControllerConfig, extra_env: Optional[Dict[str, str]] = None) -> Dict[str, Any]:
    persisted_env = load_persisted_env(config.env_path)
    if extra_env:
        persisted_env.update({str(key): str(value) for key, value in extra_env.items() if str(key).strip()})
    seed_export = None
    if resolve_requested_backend(persisted_env) == PYTHON_FALLBACK_BACKEND:
        current = fetch_health(config.health_url)
        if int(current.get('http_status', 0)) == 200 and extract_backend_from_health(current) != PYTHON_FALLBACK_BACKEND:
            try:
                seed_export = export_runtime_snapshot_seed(config, persisted_env)
            except Exception as exc:
                seed_export = {'status': 'failed', 'message': str(exc)}
    stopped = stop_runtime_api(config)
    if stopped.get('status') != 'stopped':
        result = {'status': 'restart_blocked', 'stop': stopped}
        if seed_export is not None:
            result['seed_export'] = seed_export
        return result
    started = start_runtime_api(config, extra_env=extra_env)
    result = {'status': started.get('status', 'unknown'), 'stop': stopped, 'start': started}
    if seed_export is not None:
        result['seed_export'] = seed_export
    return result


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Control Runtime API application')
    parser.add_argument('command', choices=['start', 'stop', 'status', 'restart'])
    parser.add_argument('--repo-root', default='')
    parser.add_argument('--runtime-dir', default='')
    parser.add_argument('--start-script', default='')
    parser.add_argument('--health-url', default='')
    parser.add_argument('--wait-seconds', type=float, default=40.0)
    parser.add_argument('--poll-interval', type=float, default=1.0)
    parser.add_argument('--stop-wait-seconds', type=float, default=10.0)
    parser.add_argument('--stop-pattern', default='java-rk3588-0.0.1-SNAPSHOT.jar')
    parser.add_argument('--env', action='append', default=[])
    return parser.parse_args(argv)


def build_config(args: argparse.Namespace) -> Tuple[RuntimeApiControllerConfig, Dict[str, str]]:
    config = default_config()
    repo_root = Path(args.repo_root).resolve() if args.repo_root else config.repo_root
    runtime_dir = Path(args.runtime_dir).resolve() if args.runtime_dir else config.runtime_dir
    start_script = Path(args.start_script).resolve() if args.start_script else config.start_script
    health_url = args.health_url or config.health_url
    controller_config = RuntimeApiControllerConfig(
        repo_root=repo_root,
        runtime_dir=runtime_dir,
        start_script=start_script,
        health_url=health_url,
        wait_seconds=args.wait_seconds,
        poll_interval=args.poll_interval,
        stop_wait_seconds=args.stop_wait_seconds,
        stop_pattern=args.stop_pattern,
    )
    return controller_config, normalize_env_list(args.env)


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    config, extra_env = build_config(args)
    if args.command == 'start':
        result = start_runtime_api(config, extra_env=extra_env)
    elif args.command == 'stop':
        result = stop_runtime_api(config)
    elif args.command == 'restart':
        result = restart_runtime_api(config, extra_env=extra_env)
    else:
        result = status_runtime_api(config)
    print(json.dumps(result, ensure_ascii=True))
    return 0 if result.get('status') in {'started', 'already_running', 'running', 'stopped'} else 1


if __name__ == '__main__':
    raise SystemExit(main())
