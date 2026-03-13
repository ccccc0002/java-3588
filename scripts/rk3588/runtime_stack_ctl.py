#!/usr/bin/env python3
"""Control the RK3588 runtime stack as a single unit."""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Optional, Tuple

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import media_worker_ctl
import java_app_ctl
import runtime_api_ctl
import runtime_bridge_ctl
import zlm_runtime_ctl


@dataclass(frozen=True)
class StackControllerConfig:
    repo_root: Path
    runtime_dir: Optional[Path] = None

    def __post_init__(self) -> None:
        if self.runtime_dir is None:
            object.__setattr__(self, 'runtime_dir', self.repo_root / 'runtime')


def default_config() -> StackControllerConfig:
    return StackControllerConfig(repo_root=SCRIPT_DIR.parent.parent)


def build_zlm_config(config: StackControllerConfig) -> zlm_runtime_ctl.ZlmRuntimeConfig:
    defaults = zlm_runtime_ctl.default_config()
    return zlm_runtime_ctl.ZlmRuntimeConfig(
        repo_root=config.repo_root,
        runtime_dir=config.runtime_dir,
        pid_path=config.runtime_dir / 'zlm-runtime.pid',
        log_path=config.runtime_dir / 'zlm-runtime.log',
        media_server_bin=defaults.media_server_bin,
        template_config_path=defaults.template_config_path,
        generated_config_path=config.runtime_dir / 'zlm' / 'config.ini',
        http_port=defaults.http_port,
        rtmp_port=defaults.rtmp_port,
        rtsp_port=defaults.rtsp_port,
        rtp_proxy_port=defaults.rtp_proxy_port,
        srt_port=defaults.srt_port,
        rtc_udp_port=defaults.rtc_udp_port,
        rtc_tcp_port=defaults.rtc_tcp_port,
        rtc_ice_udp_port=defaults.rtc_ice_udp_port,
        rtc_ice_tcp_port=defaults.rtc_ice_tcp_port,
        onvif_port=defaults.onvif_port,
        rtc_signal_port=defaults.rtc_signal_port,
        rtc_signal_ssl_port=defaults.rtc_signal_ssl_port,
        api_secret=defaults.api_secret,
        media_server_id=defaults.media_server_id,
        poll_interval=defaults.poll_interval,
        stop_wait_seconds=defaults.stop_wait_seconds,
    )


def build_runtime_api_config(config: StackControllerConfig) -> runtime_api_ctl.RuntimeApiControllerConfig:
    defaults = runtime_api_ctl.default_config()
    return runtime_api_ctl.RuntimeApiControllerConfig(
        repo_root=config.repo_root,
        runtime_dir=config.runtime_dir,
        start_script=config.repo_root / 'scripts' / 'rk3588' / 'Run-Runtime-Api.sh',
        health_url=defaults.health_url,
        wait_seconds=defaults.wait_seconds,
        poll_interval=defaults.poll_interval,
        stop_wait_seconds=defaults.stop_wait_seconds,
        stop_pattern=defaults.stop_pattern,
    )


def build_java_app_config(config: StackControllerConfig) -> java_app_ctl.AppControllerConfig:
    defaults = java_app_ctl.default_config()
    return java_app_ctl.AppControllerConfig(
        repo_root=config.repo_root,
        runtime_dir=config.runtime_dir,
        start_script=config.repo_root / 'scripts' / 'rk3588' / 'Run-Java-App.sh',
        health_url=defaults.health_url,
        wait_seconds=defaults.wait_seconds,
        poll_interval=defaults.poll_interval,
        stop_wait_seconds=defaults.stop_wait_seconds,
        stop_pattern=defaults.stop_pattern,
    )


def build_bridge_config(config: StackControllerConfig) -> runtime_bridge_ctl.ControllerConfig:
    defaults = runtime_bridge_ctl.default_config()
    return runtime_bridge_ctl.ControllerConfig(
        repo_root=config.repo_root,
        runtime_dir=config.runtime_dir,
        pid_path=config.runtime_dir / 'runtime-bridge.pid',
        log_path=config.runtime_dir / 'runtime-bridge.log',
        health_url=defaults.health_url,
        wait_seconds=defaults.wait_seconds,
        poll_interval=defaults.poll_interval,
        stop_wait_seconds=defaults.stop_wait_seconds,
        run_script=config.repo_root / 'scripts' / 'rk3588' / 'Run-Runtime-Bridge.sh',
        env_path=config.runtime_dir / 'runtime-bridge.env',
    )


def build_worker_config(config: StackControllerConfig) -> media_worker_ctl.ControllerConfig:
    defaults = media_worker_ctl.default_config()
    return media_worker_ctl.ControllerConfig(
        repo_root=config.repo_root,
        runtime_dir=config.runtime_dir,
        pid_path=config.runtime_dir / 'media-worker.pid',
        log_path=config.runtime_dir / 'media-worker.log',
        wait_seconds=defaults.wait_seconds,
        poll_interval=defaults.poll_interval,
        stop_wait_seconds=defaults.stop_wait_seconds,
        run_script=config.repo_root / 'scripts' / 'rk3588' / 'Run-Media-Worker.sh',
        env_path=config.runtime_dir / 'media-worker.env',
        state_path=config.runtime_dir / 'media-worker.state.json',
    )


def is_start_ok(status: str) -> bool:
    return status in {'started', 'already_running', 'running'}


def is_stop_ok(status: str) -> bool:
    return status in {'stopped', 'not_running', 'killed'}


def start_stack(
    config: StackControllerConfig,
    runtime_api_env: Optional[Dict[str, str]] = None,
    bridge_env: Optional[Dict[str, str]] = None,
    worker_env: Optional[Dict[str, str]] = None,
    with_java_app: bool = False,
) -> Dict[str, object]:
    zlm = zlm_runtime_ctl.start_runtime(build_zlm_config(config))
    if not is_start_ok(str(zlm.get('status', ''))):
        return {
            'status': 'zlm_unhealthy',
            'zlm': zlm,
            'java_app': {'status': 'skipped'},
            'runtime_api': {'status': 'skipped'},
            'bridge': {'status': 'skipped'},
            'worker': {'status': 'skipped'},
        }

    java_app: Dict[str, object] = {'status': 'skipped'}
    if with_java_app:
        java_app = java_app_ctl.start_app(build_java_app_config(config))
        if not is_start_ok(str(java_app.get('status', ''))):
            return {
                'status': 'java_app_unhealthy',
                'zlm': zlm,
                'java_app': java_app,
                'runtime_api': {'status': 'skipped'},
                'bridge': {'status': 'skipped'},
                'worker': {'status': 'skipped'},
            }

    runtime_api = runtime_api_ctl.start_runtime_api(build_runtime_api_config(config), extra_env=runtime_api_env)
    if not is_start_ok(str(runtime_api.get('status', ''))):
        return {
            'status': 'runtime_api_unhealthy',
            'zlm': zlm,
            'java_app': java_app,
            'runtime_api': runtime_api,
            'bridge': {'status': 'skipped'},
            'worker': {'status': 'skipped'},
        }

    bridge = runtime_bridge_ctl.start_bridge(build_bridge_config(config), extra_env=bridge_env)
    if not is_start_ok(str(bridge.get('status', ''))):
        return {
            'status': 'bridge_unhealthy',
            'zlm': zlm,
            'java_app': java_app,
            'runtime_api': runtime_api,
            'bridge': bridge,
            'worker': {'status': 'skipped'},
        }

    worker = media_worker_ctl.start_worker(build_worker_config(config), extra_env=worker_env)
    status = 'started' if is_start_ok(str(worker.get('status', ''))) else 'worker_unhealthy'
    return {
        'status': status,
        'zlm': zlm,
        'java_app': java_app,
        'runtime_api': runtime_api,
        'bridge': bridge,
        'worker': worker,
    }


def stop_stack(config: StackControllerConfig, with_java_app: bool = False) -> Dict[str, object]:
    worker = media_worker_ctl.stop_worker(build_worker_config(config))
    bridge = runtime_bridge_ctl.stop_bridge(build_bridge_config(config))
    runtime_api = runtime_api_ctl.stop_runtime_api(build_runtime_api_config(config))
    zlm = zlm_runtime_ctl.stop_runtime(build_zlm_config(config))
    java_app: Dict[str, object] = {'status': 'skipped'}
    if with_java_app:
        java_app = java_app_ctl.stop_app(build_java_app_config(config))
    status = 'stopped' if all(
        is_stop_ok(str(component.get('status', '')))
        for component in (worker, bridge, runtime_api, zlm)
    ) else 'partial'
    if with_java_app and not is_stop_ok(str(java_app.get('status', ''))):
        status = 'partial'
    return {
        'status': status,
        'worker': worker,
        'bridge': bridge,
        'runtime_api': runtime_api,
        'zlm': zlm,
        'java_app': java_app,
    }


def status_stack(config: StackControllerConfig, with_java_app: bool = False) -> Dict[str, object]:
    zlm = zlm_runtime_ctl.status_runtime(build_zlm_config(config))
    runtime_api = runtime_api_ctl.status_runtime_api(build_runtime_api_config(config))
    bridge = runtime_bridge_ctl.status_bridge(build_bridge_config(config))
    worker = media_worker_ctl.status_worker(build_worker_config(config))
    java_app: Dict[str, object] = {'status': 'skipped'}
    if with_java_app:
        java_app = java_app_ctl.status_app(build_java_app_config(config))
    components = [zlm, runtime_api, bridge, worker]
    if with_java_app:
        components.append(java_app)
    status = 'running' if all(
        str(component.get('status', '')) == 'running'
        for component in components
    ) else 'degraded'
    return {
        'status': status,
        'zlm': zlm,
        'runtime_api': runtime_api,
        'bridge': bridge,
        'worker': worker,
        'java_app': java_app,
    }


def restart_stack(
    config: StackControllerConfig,
    runtime_api_env: Optional[Dict[str, str]] = None,
    bridge_env: Optional[Dict[str, str]] = None,
    worker_env: Optional[Dict[str, str]] = None,
    with_java_app: bool = False,
) -> Dict[str, object]:
    stopped = stop_stack(config, with_java_app=with_java_app)
    if stopped.get('status') != 'stopped':
        return {
            'status': 'restart_blocked',
            'worker': stopped.get('worker', {}),
            'bridge': stopped.get('bridge', {}),
            'runtime_api': stopped.get('runtime_api', {}),
            'zlm': stopped.get('zlm', {}),
            'java_app': stopped.get('java_app', {}),
            'stop': stopped,
        }
    return start_stack(
        config,
        runtime_api_env=runtime_api_env,
        bridge_env=bridge_env,
        worker_env=worker_env,
        with_java_app=with_java_app,
    )


def parse_env_pairs(pairs: list[str]) -> Dict[str, str]:
    env: Dict[str, str] = {}
    for pair in pairs:
        item = str(pair or '').strip()
        if '=' not in item:
            raise ValueError(f'invalid env value: {pair}')
        key, value = item.split('=', 1)
        key = key.strip()
        if not key:
            raise ValueError(f'invalid env key: {pair}')
        env[key] = value
    return env


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Control RK3588 runtime stack')
    parser.add_argument('command', choices=['start', 'stop', 'status', 'restart'])
    parser.add_argument('--repo-root', default='')
    parser.add_argument('--runtime-api-env', action='append', default=[])
    parser.add_argument('--bridge-env', action='append', default=[])
    parser.add_argument('--worker-env', action='append', default=[])
    parser.add_argument('--with-java-app', action='store_true')
    return parser.parse_args(argv)


def build_config(args: argparse.Namespace) -> Tuple[StackControllerConfig, Dict[str, str], Dict[str, str], Dict[str, str]]:
    defaults = default_config()
    repo_root = Path(args.repo_root).resolve() if args.repo_root else defaults.repo_root
    config = StackControllerConfig(repo_root=repo_root)
    return (
        config,
        parse_env_pairs(args.runtime_api_env),
        parse_env_pairs(args.bridge_env),
        parse_env_pairs(args.worker_env),
    )


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    config, runtime_api_env, bridge_env, worker_env = build_config(args)
    if args.command == 'start':
        result = start_stack(config, runtime_api_env=runtime_api_env, bridge_env=bridge_env, worker_env=worker_env, with_java_app=args.with_java_app)
    elif args.command == 'stop':
        result = stop_stack(config, with_java_app=args.with_java_app)
    elif args.command == 'restart':
        result = restart_stack(config, runtime_api_env=runtime_api_env, bridge_env=bridge_env, worker_env=worker_env, with_java_app=args.with_java_app)
    else:
        result = status_stack(config, with_java_app=args.with_java_app)
    print(json.dumps(result, ensure_ascii=True))
    return 0 if result.get('status') in {'started', 'running', 'stopped', 'degraded'} else 1


if __name__ == '__main__':
    raise SystemExit(main())
