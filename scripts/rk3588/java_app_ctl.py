#!/usr/bin/env python3
"""Control the Java RK3588 application process."""

from __future__ import annotations

import argparse
import json
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional
from urllib import error, request
from urllib.parse import urlparse


@dataclass(frozen=True)
class AppControllerConfig:
    repo_root: Path
    runtime_dir: Path
    start_script: Path
    health_url: str
    wait_seconds: float = 20.0
    poll_interval: float = 1.0
    stop_wait_seconds: float = 10.0
    stop_pattern: str = 'java-rk3588-0.0.1-SNAPSHOT.jar.*18082'
    app_port: int = 18082


def default_config() -> AppControllerConfig:
    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent.parent
    runtime_dir = repo_root / 'runtime'
    return AppControllerConfig(
        repo_root=repo_root,
        runtime_dir=runtime_dir,
        start_script=repo_root / 'scripts' / 'rk3588' / 'Run-Java-App.sh',
        health_url='http://127.0.0.1:18082/api/inference/health',
    )


def resolve_start_script(config: AppControllerConfig) -> Path:
    managed_env = config.runtime_dir / 'config' / 'java-app.env'
    fallback_script = config.runtime_dir / 'start-app-18082.sh'
    if config.start_script.name == 'Run-Java-App.sh' and not managed_env.exists() and fallback_script.exists():
        return fallback_script
    return config.start_script


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


def parse_health_port(health_url: str, default_port: int = 18082) -> int:
    try:
        parsed = urlparse(str(health_url or ''))
        if parsed.port:
            return int(parsed.port)
    except Exception:
        return default_port
    return default_port


def _parse_pids(stdout: str) -> List[int]:
    pids: List[int] = []
    for token in str(stdout or '').replace(',', ' ').split():
        if token.isdigit():
            value = int(token)
            if value > 1:
                pids.append(value)
    # keep order while de-duplicating
    seen = set()
    ordered: List[int] = []
    for pid in pids:
        if pid in seen:
            continue
        seen.add(pid)
        ordered.append(pid)
    return ordered


def find_listening_pids_by_port(port: int) -> List[int]:
    # Try lsof/fuser/ss in one probe to avoid hard dependencies on a single tool.
    script = (
        'if command -v lsof >/dev/null 2>&1; then '
        f'lsof -tiTCP:{port} -sTCP:LISTEN 2>/dev/null; '
        'elif command -v fuser >/dev/null 2>&1; then '
        f'fuser -n tcp {port} 2>/dev/null; '
        'elif command -v ss >/dev/null 2>&1; then '
        f'ss -ltnp "( sport = :{port} )" 2>/dev/null | sed -n \'s/.*pid=\\([0-9]\\+\\).*/\\1/p\'; '
        'fi'
    )
    completed = subprocess.run(['bash', '-lc', script], capture_output=True, text=True)
    if completed.returncode not in (0, 1):
        return []
    return _parse_pids(completed.stdout)


def terminate_pids(pids: List[int], force: bool = False) -> None:
    signal = '-KILL' if force else '-TERM'
    for pid in pids:
        subprocess.run(['kill', signal, str(pid)], capture_output=True, text=True)


def wait_for_health(config: AppControllerConfig) -> Dict[str, Any]:
    deadline = time.time() + max(0.1, config.wait_seconds)
    last = {'http_status': 0, 'payload': {'message': 'health check not started'}}
    while time.time() < deadline:
        last = fetch_health(config.health_url)
        if int(last.get('http_status', 0)) == 200:
            return last
        time.sleep(max(0.01, config.poll_interval))
    return last


def wait_for_down(config: AppControllerConfig) -> Dict[str, Any]:
    deadline = time.time() + max(0.1, config.stop_wait_seconds)
    last = {'http_status': 200, 'payload': {'message': 'shutdown check not started'}}
    while time.time() < deadline:
        last = fetch_health(config.health_url)
        if int(last.get('http_status', 0)) != 200:
            return last
        time.sleep(max(0.01, config.poll_interval))
    return last


def start_app(config: AppControllerConfig) -> Dict[str, Any]:
    script_path = resolve_start_script(config)
    current = fetch_health(config.health_url)
    if int(current.get('http_status', 0)) == 200:
        return {'status': 'already_running', 'health': current, 'script': str(script_path)}
    if not script_path.exists():
        return {'status': 'missing_start_script', 'script': str(script_path)}
    process = subprocess.Popen(
        ['bash', str(script_path)],
        cwd=str(config.repo_root),
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )
    health = wait_for_health(config)
    if int(health.get('http_status', 0)) == 200:
        return {'status': 'started', 'pid': process.pid, 'health': health, 'script': str(script_path)}
    return {'status': 'app_unhealthy', 'pid': process.pid, 'health': health, 'script': str(script_path)}


def stop_app(config: AppControllerConfig) -> Dict[str, Any]:
    completed = subprocess.run(['pkill', '-f', config.stop_pattern], capture_output=True, text=True)
    if completed.returncode in (0, 1):
        health = wait_for_down(config)
        if int(health.get('http_status', 0)) != 200:
            return {
                'status': 'stopped',
                'pattern': config.stop_pattern,
                'exit_code': completed.returncode,
                'health': health,
            }
        # Fallback path: occasionally older JVMs remain and still hold the listening port.
        fallback_pids = find_listening_pids_by_port(config.app_port)
        if fallback_pids:
            terminate_pids(fallback_pids, force=False)
            health = wait_for_down(config)
            if int(health.get('http_status', 0)) != 200:
                return {
                    'status': 'stopped',
                    'pattern': config.stop_pattern,
                    'exit_code': completed.returncode,
                    'health': health,
                    'fallback_killed_pids': fallback_pids,
                }
            terminate_pids(fallback_pids, force=True)
            health = wait_for_down(config)
            if int(health.get('http_status', 0)) != 200:
                return {
                    'status': 'stopped',
                    'pattern': config.stop_pattern,
                    'exit_code': completed.returncode,
                    'health': health,
                    'fallback_killed_pids': fallback_pids,
                    'fallback_force_kill': True,
                }
        return {
            'status': 'stop_pending_exit',
            'pattern': config.stop_pattern,
            'exit_code': completed.returncode,
            'health': health,
            'fallback_killed_pids': fallback_pids,
        }
    return {'status': 'stop_failed', 'pattern': config.stop_pattern, 'exit_code': completed.returncode, 'stderr': completed.stderr}


def status_app(config: AppControllerConfig) -> Dict[str, Any]:
    script_path = resolve_start_script(config)
    health = fetch_health(config.health_url)
    if int(health.get('http_status', 0)) == 200:
        return {'status': 'running', 'health': health, 'script': str(script_path)}
    return {'status': 'down', 'health': health, 'script': str(script_path)}


def restart_app(config: AppControllerConfig) -> Dict[str, Any]:
    stopped = stop_app(config)
    if stopped.get('status') != 'stopped':
        return {'status': 'restart_blocked', 'stop': stopped}
    started = start_app(config)
    return {'status': started.get('status', 'unknown'), 'stop': stopped, 'start': started}


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Control Java RK3588 application')
    parser.add_argument('command', choices=['start', 'stop', 'status', 'restart'])
    parser.add_argument('--repo-root', default='')
    parser.add_argument('--runtime-dir', default='')
    parser.add_argument('--start-script', default='')
    parser.add_argument('--health-url', default='')
    parser.add_argument('--wait-seconds', type=float, default=20.0)
    parser.add_argument('--poll-interval', type=float, default=1.0)
    parser.add_argument('--stop-wait-seconds', type=float, default=10.0)
    parser.add_argument('--stop-pattern', default='java-rk3588-0.0.1-SNAPSHOT.jar.*18082')
    parser.add_argument('--app-port', type=int, default=0)
    return parser.parse_args(argv)


def build_config(args: argparse.Namespace) -> AppControllerConfig:
    config = default_config()
    repo_root = Path(args.repo_root).resolve() if args.repo_root else config.repo_root
    runtime_dir = Path(args.runtime_dir).resolve() if args.runtime_dir else config.runtime_dir
    start_script = Path(args.start_script).resolve() if args.start_script else config.start_script
    health_url = args.health_url or config.health_url
    app_port = int(args.app_port) if int(args.app_port or 0) > 0 else parse_health_port(health_url, config.app_port)
    return AppControllerConfig(
        repo_root=repo_root,
        runtime_dir=runtime_dir,
        start_script=start_script,
        health_url=health_url,
        wait_seconds=args.wait_seconds,
        poll_interval=args.poll_interval,
        stop_wait_seconds=args.stop_wait_seconds,
        stop_pattern=args.stop_pattern,
        app_port=app_port,
    )


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    config = build_config(args)
    if args.command == 'start':
        result = start_app(config)
    elif args.command == 'stop':
        result = stop_app(config)
    elif args.command == 'restart':
        result = restart_app(config)
    else:
        result = status_app(config)
    print(json.dumps(result, ensure_ascii=True))
    return 0 if result.get('status') in {'started', 'already_running', 'running', 'stopped'} else 1


if __name__ == '__main__':
    raise SystemExit(main())
