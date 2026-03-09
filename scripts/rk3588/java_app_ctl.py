#!/usr/bin/env python3
"""Control the Java RK3588 application process."""

from __future__ import annotations

import argparse
import json
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional
from urllib import error, request


@dataclass(frozen=True)
class AppControllerConfig:
    repo_root: Path
    runtime_dir: Path
    start_script: Path
    health_url: str
    wait_seconds: float = 20.0
    poll_interval: float = 1.0
    stop_pattern: str = 'java-rk3588-0.0.1-SNAPSHOT.jar.*18082'


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


def wait_for_health(config: AppControllerConfig) -> Dict[str, Any]:
    deadline = time.time() + max(0.1, config.wait_seconds)
    last = {'http_status': 0, 'payload': {'message': 'health check not started'}}
    while time.time() < deadline:
        last = fetch_health(config.health_url)
        if int(last.get('http_status', 0)) == 200:
            return last
        time.sleep(max(0.01, config.poll_interval))
    return last


def start_app(config: AppControllerConfig) -> Dict[str, Any]:
    current = fetch_health(config.health_url)
    if int(current.get('http_status', 0)) == 200:
        return {'status': 'already_running', 'health': current, 'script': str(config.start_script)}
    if not config.start_script.exists():
        return {'status': 'missing_start_script', 'script': str(config.start_script)}
    process = subprocess.Popen(
        ['bash', str(config.start_script)],
        cwd=str(config.repo_root),
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )
    health = wait_for_health(config)
    if int(health.get('http_status', 0)) == 200:
        return {'status': 'started', 'pid': process.pid, 'health': health, 'script': str(config.start_script)}
    return {'status': 'app_unhealthy', 'pid': process.pid, 'health': health, 'script': str(config.start_script)}


def stop_app(config: AppControllerConfig) -> Dict[str, Any]:
    completed = subprocess.run(['pkill', '-f', config.stop_pattern], capture_output=True, text=True)
    if completed.returncode in (0, 1):
        return {'status': 'stopped', 'pattern': config.stop_pattern, 'exit_code': completed.returncode}
    return {'status': 'stop_failed', 'pattern': config.stop_pattern, 'exit_code': completed.returncode, 'stderr': completed.stderr}


def status_app(config: AppControllerConfig) -> Dict[str, Any]:
    health = fetch_health(config.health_url)
    if int(health.get('http_status', 0)) == 200:
        return {'status': 'running', 'health': health, 'script': str(config.start_script)}
    return {'status': 'down', 'health': health, 'script': str(config.start_script)}


def restart_app(config: AppControllerConfig) -> Dict[str, Any]:
    stop_app(config)
    return start_app(config)


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Control Java RK3588 application')
    parser.add_argument('command', choices=['start', 'stop', 'status', 'restart'])
    parser.add_argument('--repo-root', default='')
    parser.add_argument('--runtime-dir', default='')
    parser.add_argument('--start-script', default='')
    parser.add_argument('--health-url', default='')
    parser.add_argument('--wait-seconds', type=float, default=20.0)
    parser.add_argument('--poll-interval', type=float, default=1.0)
    parser.add_argument('--stop-pattern', default='java-rk3588-0.0.1-SNAPSHOT.jar.*18082')
    return parser.parse_args(argv)


def build_config(args: argparse.Namespace) -> AppControllerConfig:
    config = default_config()
    repo_root = Path(args.repo_root).resolve() if args.repo_root else config.repo_root
    runtime_dir = Path(args.runtime_dir).resolve() if args.runtime_dir else config.runtime_dir
    start_script = Path(args.start_script).resolve() if args.start_script else config.start_script
    health_url = args.health_url or config.health_url
    return AppControllerConfig(
        repo_root=repo_root,
        runtime_dir=runtime_dir,
        start_script=start_script,
        health_url=health_url,
        wait_seconds=args.wait_seconds,
        poll_interval=args.poll_interval,
        stop_pattern=args.stop_pattern,
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