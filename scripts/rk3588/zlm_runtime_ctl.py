#!/usr/bin/env python3
"""Manage an isolated ZLMediaKit runtime for the RK3588 workspace."""

from __future__ import annotations

import argparse
import json
import os
import signal
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Optional


@dataclass(frozen=True)
class ZlmRuntimeConfig:
    repo_root: Path
    runtime_dir: Path
    pid_path: Path
    log_path: Path
    media_server_bin: Path
    template_config_path: Path
    generated_config_path: Path
    http_port: int = 1987
    rtmp_port: int = 19350
    rtsp_port: int = 1554
    rtp_proxy_port: int = 10050
    srt_port: int = 19000
    rtc_udp_port: int = 18000
    rtc_tcp_port: int = 18001
    rtc_ice_udp_port: int = 13478
    rtc_ice_tcp_port: int = 13479
    onvif_port: int = 13702
    rtc_signal_port: int = 13000
    rtc_signal_ssl_port: int = 0
    api_secret: str = 'java-rk3588-zlm'
    media_server_id: str = 'java-rk3588-zlm'
    poll_interval: float = 0.2
    stop_wait_seconds: float = 5.0


def default_config() -> ZlmRuntimeConfig:
    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent.parent
    runtime_dir = repo_root / 'runtime'
    return ZlmRuntimeConfig(
        repo_root=repo_root,
        runtime_dir=runtime_dir,
        pid_path=runtime_dir / 'zlm-runtime.pid',
        log_path=runtime_dir / 'zlm-runtime.log',
        media_server_bin=Path('/home/zql/ks/3rd_party/media/MediaServer'),
        template_config_path=Path('/home/zql/ks/3rd_party/media/config.ini'),
        generated_config_path=runtime_dir / 'zlm' / 'config.ini',
    )


def media_root_path(config: ZlmRuntimeConfig) -> Path:
    return config.generated_config_path.parent / 'www'


def render_runtime_config(
    template_text: str,
    http_port: int,
    rtmp_port: int,
    rtsp_port: int,
    rtp_proxy_port: int,
    srt_port: int,
    rtc_udp_port: int,
    rtc_tcp_port: int,
    rtc_ice_udp_port: int,
    rtc_ice_tcp_port: int,
    onvif_port: int,
    rtc_signal_port: int,
    rtc_signal_ssl_port: int,
    api_secret: str,
    media_server_id: str,
    media_root: Path,
) -> str:
    media_root = media_root.resolve()
    snap_root = media_root / 'snap'
    default_snap = media_root / 'logo.png'
    lines = []
    section = ''
    for raw_line in str(template_text).splitlines():
        line = raw_line
        stripped = raw_line.strip()
        if stripped.startswith('[') and stripped.endswith(']'):
            section = stripped.lower()
        elif section == '[api]' and stripped.startswith('secret='):
            line = f'secret={api_secret}'
        elif section == '[api]' and stripped.startswith('snapRoot='):
            line = f'snapRoot={snap_root.as_posix()}/'
        elif section == '[api]' and stripped.startswith('defaultSnap='):
            line = f'defaultSnap={default_snap.as_posix()}'
        elif section == '[api]' and stripped.startswith('downloadRoot='):
            line = f'downloadRoot={media_root.as_posix()}'
        elif section == '[http]' and stripped.startswith('port='):
            line = f'port={int(http_port)}'
        elif section == '[http]' and stripped.startswith('rootPath='):
            line = f'rootPath={media_root.as_posix()}'
        elif section == '[http]' and stripped.startswith('sslport='):
            line = 'sslport=0'
        elif section == '[protocol]' and stripped.startswith('mp4_save_path='):
            line = f'mp4_save_path={media_root.as_posix()}'
        elif section == '[protocol]' and stripped.startswith('hls_save_path='):
            line = f'hls_save_path={media_root.as_posix()}'
        elif section == '[rtmp]' and stripped.startswith('port='):
            line = f'port={int(rtmp_port)}'
        elif section == '[rtmp]' and stripped.startswith('sslport='):
            line = 'sslport=0'
        elif section == '[rtsp]' and stripped.startswith('port='):
            line = f'port={int(rtsp_port)}'
        elif section == '[rtsp]' and stripped.startswith('sslport='):
            line = 'sslport=0'
        elif section == '[rtp_proxy]' and stripped.startswith('port='):
            line = f'port={int(rtp_proxy_port)}'
        elif section == '[srt]' and stripped.startswith('port='):
            line = f'port={int(srt_port)}'
        elif section == '[rtc]' and stripped.startswith('port='):
            line = f'port={int(rtc_udp_port)}'
        elif section == '[rtc]' and stripped.startswith('tcpPort='):
            line = f'tcpPort={int(rtc_tcp_port)}'
        elif section == '[rtc]' and stripped.startswith('icePort='):
            line = f'icePort={int(rtc_ice_udp_port)}'
        elif section == '[rtc]' and stripped.startswith('iceTcpPort='):
            line = f'iceTcpPort={int(rtc_ice_tcp_port)}'
        elif section == '[rtc]' and stripped.startswith('enableTurn='):
            line = 'enableTurn=0'
        elif section == '[onvif]' and stripped.startswith('port='):
            line = f'port={int(onvif_port)}'
        elif section == '[rtc]' and stripped.startswith('signalingPort='):
            line = f'signalingPort={int(rtc_signal_port)}'
        elif section == '[rtc]' and stripped.startswith('signalingSslPort='):
            line = f'signalingSslPort={int(rtc_signal_ssl_port)}'
        elif section == '[hook]' and stripped.startswith('enable='):
            line = 'enable=0'
        elif section == '[hook]' and stripped.startswith('on_'):
            key = stripped.split('=', 1)[0]
            line = f'{key}='
        elif section == '[general]' and stripped.startswith('mediaServerId='):
            line = f'mediaServerId={media_server_id}'
        lines.append(line)
    return '\n'.join(lines) + '\n'


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


def ensure_runtime_layout(config: ZlmRuntimeConfig) -> None:
    config.runtime_dir.mkdir(parents=True, exist_ok=True)
    config.generated_config_path.parent.mkdir(parents=True, exist_ok=True)
    (config.generated_config_path.parent / 'logs').mkdir(parents=True, exist_ok=True)
    media_root = media_root_path(config)
    media_root.mkdir(parents=True, exist_ok=True)
    (media_root / 'snap').mkdir(parents=True, exist_ok=True)


def ensure_runtime_config(config: ZlmRuntimeConfig) -> None:
    ensure_runtime_layout(config)
    template = config.template_config_path.read_text(encoding='utf-8', errors='replace')
    rendered = render_runtime_config(
        template_text=template,
        http_port=config.http_port,
        rtmp_port=config.rtmp_port,
        rtsp_port=config.rtsp_port,
        rtp_proxy_port=config.rtp_proxy_port,
        srt_port=config.srt_port,
        rtc_udp_port=config.rtc_udp_port,
        rtc_tcp_port=config.rtc_tcp_port,
        rtc_ice_udp_port=config.rtc_ice_udp_port,
        rtc_ice_tcp_port=config.rtc_ice_tcp_port,
        onvif_port=config.onvif_port,
        rtc_signal_port=config.rtc_signal_port,
        rtc_signal_ssl_port=config.rtc_signal_ssl_port,
        api_secret=config.api_secret,
        media_server_id=config.media_server_id,
        media_root=media_root_path(config),
    )
    with config.generated_config_path.open('w', encoding='utf-8', newline='\n') as handle:
        handle.write(rendered)


def start_runtime(config: ZlmRuntimeConfig) -> Dict[str, object]:
    existing_pid = read_pid(config.pid_path)
    if existing_pid and is_process_running(existing_pid):
        return {'status': 'already_running', 'pid': existing_pid, 'pid_path': str(config.pid_path), 'log_path': str(config.log_path), 'config_path': str(config.generated_config_path)}
    config.pid_path.unlink(missing_ok=True)
    ensure_runtime_config(config)
    with config.log_path.open('a', encoding='utf-8') as log_handle:
        process = subprocess.Popen(
            [str(config.media_server_bin), '-c', str(config.generated_config_path), '--log-dir', str(config.generated_config_path.parent / 'logs')],
            cwd=str(config.media_server_bin.parent),
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    write_pid(config.pid_path, process.pid)
    time.sleep(max(0.05, config.poll_interval))
    if process.poll() is not None:
        config.pid_path.unlink(missing_ok=True)
        return {'status': 'exited_early', 'pid': process.pid, 'pid_path': str(config.pid_path), 'log_path': str(config.log_path)}
    return {'status': 'started', 'pid': process.pid, 'pid_path': str(config.pid_path), 'log_path': str(config.log_path), 'config_path': str(config.generated_config_path)}


def stop_runtime(config: ZlmRuntimeConfig) -> Dict[str, object]:
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


def status_runtime(config: ZlmRuntimeConfig) -> Dict[str, object]:
    pid = read_pid(config.pid_path)
    if not pid:
        return {'status': 'not_running', 'pid': None, 'pid_path': str(config.pid_path), 'log_path': str(config.log_path)}
    if not is_process_running(pid):
        config.pid_path.unlink(missing_ok=True)
        return {'status': 'not_running', 'pid': pid, 'pid_path': str(config.pid_path), 'log_path': str(config.log_path)}
    return {'status': 'running', 'pid': pid, 'pid_path': str(config.pid_path), 'log_path': str(config.log_path), 'config_path': str(config.generated_config_path)}


def restart_runtime(config: ZlmRuntimeConfig) -> Dict[str, object]:
    stop_runtime(config)
    return start_runtime(config)


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description='Control dedicated ZLMediaKit runtime')
    parser.add_argument('command', choices=['start', 'stop', 'status', 'restart'])
    parser.add_argument('--repo-root', default='')
    parser.add_argument('--runtime-dir', default='')
    parser.add_argument('--pid-path', default='')
    parser.add_argument('--log-path', default='')
    parser.add_argument('--media-server-bin', default='')
    parser.add_argument('--template-config-path', default='')
    parser.add_argument('--generated-config-path', default='')
    parser.add_argument('--http-port', type=int, default=1987)
    parser.add_argument('--rtmp-port', type=int, default=19350)
    parser.add_argument('--rtsp-port', type=int, default=1554)
    parser.add_argument('--rtp-proxy-port', type=int, default=10050)
    parser.add_argument('--srt-port', type=int, default=19000)
    parser.add_argument('--rtc-udp-port', type=int, default=18000)
    parser.add_argument('--rtc-tcp-port', type=int, default=18001)
    parser.add_argument('--rtc-ice-udp-port', type=int, default=13478)
    parser.add_argument('--rtc-ice-tcp-port', type=int, default=13479)
    parser.add_argument('--onvif-port', type=int, default=13702)
    parser.add_argument('--rtc-signal-port', type=int, default=13000)
    parser.add_argument('--rtc-signal-ssl-port', type=int, default=0)
    parser.add_argument('--api-secret', default='java-rk3588-zlm')
    parser.add_argument('--media-server-id', default='java-rk3588-zlm')
    return parser.parse_args(argv)


def build_config(args) -> ZlmRuntimeConfig:
    config = default_config()
    repo_root = Path(args.repo_root).resolve() if args.repo_root else config.repo_root
    runtime_dir = Path(args.runtime_dir).resolve() if args.runtime_dir else config.runtime_dir
    return ZlmRuntimeConfig(
        repo_root=repo_root,
        runtime_dir=runtime_dir,
        pid_path=Path(args.pid_path).resolve() if args.pid_path else runtime_dir / 'zlm-runtime.pid',
        log_path=Path(args.log_path).resolve() if args.log_path else runtime_dir / 'zlm-runtime.log',
        media_server_bin=Path(args.media_server_bin).resolve() if args.media_server_bin else config.media_server_bin,
        template_config_path=Path(args.template_config_path).resolve() if args.template_config_path else config.template_config_path,
        generated_config_path=Path(args.generated_config_path).resolve() if args.generated_config_path else runtime_dir / 'zlm' / 'config.ini',
        http_port=args.http_port,
        rtmp_port=args.rtmp_port,
        rtsp_port=args.rtsp_port,
        rtp_proxy_port=args.rtp_proxy_port,
        srt_port=args.srt_port,
        rtc_udp_port=args.rtc_udp_port,
        rtc_tcp_port=args.rtc_tcp_port,
        rtc_ice_udp_port=args.rtc_ice_udp_port,
        rtc_ice_tcp_port=args.rtc_ice_tcp_port,
        onvif_port=args.onvif_port,
        rtc_signal_port=args.rtc_signal_port,
        rtc_signal_ssl_port=args.rtc_signal_ssl_port,
        api_secret=args.api_secret,
        media_server_id=args.media_server_id,
    )


def main(argv=None) -> int:
    args = parse_args(argv)
    config = build_config(args)
    if args.command == 'start':
        result = start_runtime(config)
    elif args.command == 'stop':
        result = stop_runtime(config)
    elif args.command == 'restart':
        result = restart_runtime(config)
    else:
        result = status_runtime(config)
    print(json.dumps(result, ensure_ascii=True))
    return 0 if result['status'] in {'started', 'already_running', 'running', 'stopped', 'not_running', 'killed'} else 1


if __name__ == '__main__':
    raise SystemExit(main())
