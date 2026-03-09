#!/usr/bin/env python3
"""Control the RK3588 Java app and runtime bridge as a single stack."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Optional

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import java_app_ctl
import runtime_bridge_ctl


@dataclass(frozen=True)
class StackControllerConfig:
    repo_root: Path
    app_start_script: Path
    app_health_url: str
    app_wait_seconds: float = 20.0
    app_poll_interval: float = 1.0
    app_stop_pattern: str = 'java-rk3588-0.0.1-SNAPSHOT.jar.*18082'


def default_config() -> StackControllerConfig:
    repo_root = SCRIPT_DIR.parent.parent
    return StackControllerConfig(
        repo_root=repo_root,
        app_start_script=repo_root / 'scripts' / 'rk3588' / 'Run-Java-App.sh',
        app_health_url='http://127.0.0.1:18082/api/inference/health',
    )


def build_app_config(config: StackControllerConfig) -> java_app_ctl.AppControllerConfig:
    return java_app_ctl.AppControllerConfig(
        repo_root=config.repo_root,
        runtime_dir=config.repo_root / 'runtime',
        start_script=config.app_start_script,
        health_url=config.app_health_url,
        wait_seconds=config.app_wait_seconds,
        poll_interval=config.app_poll_interval,
        stop_pattern=config.app_stop_pattern,
    )


def start_stack(config: StackControllerConfig, bridge_env: Optional[Dict[str, str]] = None) -> Dict[str, object]:
    app = java_app_ctl.start_app(build_app_config(config))
    if app.get('status') not in {'started', 'already_running'}:
        return {'status': 'app_unhealthy', 'app': app, 'bridge': {'status': 'skipped'}}
    bridge = runtime_bridge_ctl.start_bridge(runtime_bridge_ctl.default_config(), extra_env=bridge_env)
    status = 'started' if bridge.get('status') in {'started', 'already_running', 'running'} else 'bridge_unhealthy'
    return {'status': status, 'app': app, 'bridge': bridge}


def stop_stack(config: StackControllerConfig) -> Dict[str, object]:
    bridge = runtime_bridge_ctl.stop_bridge(runtime_bridge_ctl.default_config())
    app = java_app_ctl.stop_app(build_app_config(config))
    status = 'stopped' if app.get('status') == 'stopped' and bridge.get('status') in {'stopped', 'not_running', 'killed'} else 'partial'
    return {'status': status, 'app': app, 'bridge': bridge}


def status_stack(config: StackControllerConfig) -> Dict[str, object]:
    app = java_app_ctl.status_app(build_app_config(config))
    bridge = runtime_bridge_ctl.status_bridge(runtime_bridge_ctl.default_config())
    status = 'running' if app.get('status') == 'running' and bridge.get('status') == 'running' else 'degraded'
    return {'status': status, 'app': app, 'bridge': bridge}


def restart_stack(config: StackControllerConfig, bridge_env: Optional[Dict[str, str]] = None) -> Dict[str, object]:
    stop_stack(config)
    return start_stack(config, bridge_env=bridge_env)


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Control RK3588 Java app + bridge stack')
    parser.add_argument('command', choices=['start', 'stop', 'status', 'restart'])
    parser.add_argument('--repo-root', default='')
    parser.add_argument('--app-start-script', default='')
    parser.add_argument('--app-health-url', default='')
    parser.add_argument('--app-wait-seconds', type=float, default=20.0)
    parser.add_argument('--app-poll-interval', type=float, default=1.0)
    parser.add_argument('--app-stop-pattern', default='java-rk3588-0.0.1-SNAPSHOT.jar.*18082')
    parser.add_argument('--bridge-env', action='append', default=[])
    return parser.parse_args(argv)


def build_config(args: argparse.Namespace) -> StackControllerConfig:
    config = default_config()
    repo_root = Path(args.repo_root).resolve() if args.repo_root else config.repo_root
    app_start_script = Path(args.app_start_script).resolve() if args.app_start_script else config.app_start_script
    app_health_url = args.app_health_url or config.app_health_url
    return StackControllerConfig(
        repo_root=repo_root,
        app_start_script=app_start_script,
        app_health_url=app_health_url,
        app_wait_seconds=args.app_wait_seconds,
        app_poll_interval=args.app_poll_interval,
        app_stop_pattern=args.app_stop_pattern,
    )


def parse_env_pairs(pairs: list[str]) -> Dict[str, str]:
    env = {}
    for pair in pairs:
        if '=' not in pair:
            raise ValueError(f'invalid --bridge-env value: {pair}')
        key, value = pair.split('=', 1)
        key = key.strip()
        if not key:
            raise ValueError(f'invalid --bridge-env key: {pair}')
        env[key] = value
    return env


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    config = build_config(args)
    bridge_env = parse_env_pairs(args.bridge_env)
    if args.command == 'start':
        result = start_stack(config, bridge_env=bridge_env)
    elif args.command == 'stop':
        result = stop_stack(config)
    elif args.command == 'restart':
        result = restart_stack(config, bridge_env=bridge_env)
    else:
        result = status_stack(config)
    print(json.dumps(result, ensure_ascii=True))
    return 0 if result.get('status') in {'started', 'running', 'stopped', 'degraded'} else 1


if __name__ == '__main__':
    raise SystemExit(main())