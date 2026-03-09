#!/usr/bin/env python3
"""Manage the RK3588 runtime bridge as a detached background process."""

from __future__ import annotations

import argparse
import json
import os
import signal
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional
from urllib import error, request


@dataclass(frozen=True)
class ControllerConfig:
    repo_root: Path
    runtime_dir: Path
    pid_path: Path
    log_path: Path
    health_url: str
    wait_seconds: float = 10.0
    poll_interval: float = 0.5
    stop_wait_seconds: float = 5.0
    run_script: Optional[Path] = None

    def __post_init__(self) -> None:
        if self.run_script is None:
            object.__setattr__(self, 'run_script', self.repo_root / 'scripts' / 'rk3588' / 'Run-Runtime-Bridge.sh')


def default_config() -> ControllerConfig:
    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent.parent
    runtime_dir = repo_root / 'runtime'
    return ControllerConfig(
        repo_root=repo_root,
        runtime_dir=runtime_dir,
        pid_path=runtime_dir / 'runtime-bridge.pid',
        log_path=runtime_dir / 'runtime-bridge.log',
        health_url=os.environ.get('BRIDGE_HEALTH_URL', 'http://127.0.0.1:19080/health'),
    )


def read_pid(pid_path: Path) -> Optional[int]:
    try:
        raw = pid_path.read_text(encoding='utf-8').strip()
    except FileNotFoundError:
        return None
    if not raw:
        return None
    try:
        return int(raw)
    except ValueError:
        pid_path.unlink(missing_ok=True)
        return None


def write_pid(pid_path: Path, pid: int) -> None:
    pid_path.parent.mkdir(parents=True, exist_ok=True)
    pid_path.write_text(f'{pid}\n', encoding='utf-8')


def is_process_running(pid: int) -> bool:
    if pid <= 0:
        return False
    try:
        os.kill(pid, 0)
    except OSError:
        return False
    return True


def terminate_process_group(pid: int, sig: int) -> None:
    if hasattr(os, 'killpg') and hasattr(os, 'getpgid'):
        os.killpg(os.getpgid(pid), sig)
        return
    os.kill(pid, sig)


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


def wait_for_health(config: ControllerConfig) -> Dict[str, Any]:
    deadline = time.time() + max(0.1, config.wait_seconds)
    last = {'http_status': 0, 'payload': {'message': 'health check not started'}}
    while time.time() < deadline:
        last = fetch_health(config.health_url)
        if int(last.get('http_status', 0)) == 200:
            return last
        time.sleep(max(0.01, config.poll_interval))
    return last


def start_bridge(config: ControllerConfig, extra_env: Optional[Dict[str, str]] = None) -> Dict[str, Any]:
    config.runtime_dir.mkdir(parents=True, exist_ok=True)
    existing_pid = read_pid(config.pid_path)
    if existing_pid and is_process_running(existing_pid):
        return {
            'status': 'already_running',
            'pid': existing_pid,
            'health': fetch_health(config.health_url),
            'pid_path': str(config.pid_path),
            'log_path': str(config.log_path),
        }
    if existing_pid and not is_process_running(existing_pid):
        config.pid_path.unlink(missing_ok=True)

    env = os.environ.copy()
    if extra_env:
        env.update({k: str(v) for k, v in extra_env.items() if v is not None})

    with config.log_path.open('ab') as log_file:
        process = subprocess.Popen(
            ['bash', str(config.run_script)],
            cwd=str(config.repo_root),
            env=env,
            stdin=subprocess.DEVNULL,
            stdout=log_file,
            stderr=log_file,
            start_new_session=True,
        )
    write_pid(config.pid_path, process.pid)
    health = wait_for_health(config)
    result = {
        'status': 'started' if int(health.get('http_status', 0)) == 200 else 'started_unhealthy',
        'pid': process.pid,
        'health': health,
        'pid_path': str(config.pid_path),
        'log_path': str(config.log_path),
    }
    if result['status'] == 'started_unhealthy' and not is_process_running(process.pid):
        config.pid_path.unlink(missing_ok=True)
    return result


def stop_bridge(config: ControllerConfig) -> Dict[str, Any]:
    pid = read_pid(config.pid_path)
    if not pid:
        return {'status': 'not_running', 'pid': None, 'pid_path': str(config.pid_path)}
    if not is_process_running(pid):
        config.pid_path.unlink(missing_ok=True)
        return {'status': 'not_running', 'pid': pid, 'pid_path': str(config.pid_path)}

    terminate_process_group(pid, signal.SIGTERM)
    deadline = time.time() + max(0.1, config.stop_wait_seconds)
    while time.time() < deadline:
        if not is_process_running(pid):
            config.pid_path.unlink(missing_ok=True)
            return {'status': 'stopped', 'pid': pid, 'pid_path': str(config.pid_path)}
        time.sleep(0.05)

    terminate_process_group(pid, signal.SIGKILL)
    config.pid_path.unlink(missing_ok=True)
    return {'status': 'killed', 'pid': pid, 'pid_path': str(config.pid_path)}


def status_bridge(config: ControllerConfig) -> Dict[str, Any]:
    pid = read_pid(config.pid_path)
    if not pid:
        return {'status': 'not_running', 'pid': None, 'pid_path': str(config.pid_path), 'log_path': str(config.log_path)}
    if not is_process_running(pid):
        config.pid_path.unlink(missing_ok=True)
        return {'status': 'not_running', 'pid': pid, 'pid_path': str(config.pid_path), 'log_path': str(config.log_path)}
    return {
        'status': 'running',
        'pid': pid,
        'health': fetch_health(config.health_url),
        'pid_path': str(config.pid_path),
        'log_path': str(config.log_path),
    }


def restart_bridge(config: ControllerConfig, extra_env: Optional[Dict[str, str]] = None) -> Dict[str, Any]:
    stop_bridge(config)
    return start_bridge(config, extra_env=extra_env)


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Control RK3588 runtime bridge process')
    parser.add_argument('command', choices=['start', 'stop', 'status', 'restart'])
    parser.add_argument('--repo-root', default='')
    parser.add_argument('--runtime-dir', default='')
    parser.add_argument('--pid-path', default='')
    parser.add_argument('--log-path', default='')
    parser.add_argument('--health-url', default='')
    parser.add_argument('--wait-seconds', type=float, default=10.0)
    parser.add_argument('--poll-interval', type=float, default=0.5)
    parser.add_argument('--stop-wait-seconds', type=float, default=5.0)
    parser.add_argument('--env', action='append', default=[], help='Extra environment variables in KEY=VALUE form')
    return parser.parse_args(argv)


def build_config(args: argparse.Namespace) -> ControllerConfig:
    config = default_config()
    repo_root = Path(args.repo_root).resolve() if args.repo_root else config.repo_root
    runtime_dir = Path(args.runtime_dir).resolve() if args.runtime_dir else config.runtime_dir
    pid_path = Path(args.pid_path).resolve() if args.pid_path else runtime_dir / 'runtime-bridge.pid'
    log_path = Path(args.log_path).resolve() if args.log_path else runtime_dir / 'runtime-bridge.log'
    health_url = args.health_url or config.health_url
    return ControllerConfig(
        repo_root=repo_root,
        runtime_dir=runtime_dir,
        pid_path=pid_path,
        log_path=log_path,
        health_url=health_url,
        wait_seconds=args.wait_seconds,
        poll_interval=args.poll_interval,
        stop_wait_seconds=args.stop_wait_seconds,
    )


def parse_env_pairs(pairs: list[str]) -> Dict[str, str]:
    env = {}
    for pair in pairs:
        if '=' not in pair:
            raise ValueError(f'invalid --env value: {pair}')
        key, value = pair.split('=', 1)
        key = key.strip()
        if not key:
            raise ValueError(f'invalid --env key: {pair}')
        env[key] = value
    return env


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    try:
        config = build_config(args)
        extra_env = parse_env_pairs(args.env)
        if args.command == 'start':
            result = start_bridge(config, extra_env=extra_env)
        elif args.command == 'stop':
            result = stop_bridge(config)
        elif args.command == 'restart':
            result = restart_bridge(config, extra_env=extra_env)
        else:
            result = status_bridge(config)
        print(json.dumps(result, ensure_ascii=True))
        return 0 if result['status'] in {'started', 'already_running', 'running', 'stopped', 'not_running', 'killed'} else 1
    except Exception as exc:
        print(json.dumps({'status': 'error', 'message': str(exc)}, ensure_ascii=True))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())