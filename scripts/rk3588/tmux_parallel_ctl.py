#!/usr/bin/env python3
"""Launch and control parallel task lanes inside tmux."""

from __future__ import annotations

import argparse
import datetime
import json
import shlex
import shutil
import subprocess
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple


def run_tmux(args: Sequence[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ['tmux', *args],
        check=False,
        capture_output=True,
        text=True,
    )


def tmux_available() -> bool:
    return shutil.which('tmux') is not None


def session_exists(session: str) -> bool:
    result = run_tmux(['has-session', '-t', session])
    return result.returncode == 0


def parse_lane(value: str) -> Tuple[str, str]:
    text = str(value or '').strip()
    if '=' not in text:
        raise ValueError(f'invalid lane spec: {value}')
    name, command = text.split('=', 1)
    lane_name = name.strip()
    lane_cmd = command.strip()
    if not lane_name or not lane_cmd:
        raise ValueError(f'invalid lane spec: {value}')
    return lane_name, lane_cmd


def build_lane_command(workdir: Path, command: str) -> str:
    wd = shlex.quote(str(workdir))
    cmd = str(command).strip()
    return (
        "bash -lc "
        + shlex.quote(f'cd {wd} && {cmd}; code=$?; echo "[lane-exit:$code]"; exec bash')
    )


def list_windows(session: str) -> List[Dict[str, object]]:
    result = run_tmux(['list-windows', '-t', session, '-F', '#{window_name}|#{pane_current_command}|#{pane_dead}'])
    if result.returncode != 0:
        return []
    windows: List[Dict[str, object]] = []
    for line in (result.stdout or '').splitlines():
        parts = line.split('|')
        if len(parts) != 3:
            continue
        windows.append(
            {
                'name': parts[0],
                'current_command': parts[1],
                'pane_dead': parts[2] == '1',
            }
        )
    return windows


def build_default_lanes(args: argparse.Namespace) -> List[Tuple[str, str]]:
    output_root = str(args.output_root).strip()
    stamp = datetime.datetime.utcnow().strftime('%Y%m%d-%H%M%S')
    base = f'{output_root}/{stamp}'
    return [
        (
            'media',
            (
                f"python3 scripts/testing/validate_dispatch_source_policy.py "
                f"--base-url {shlex.quote(args.base_url)} "
                f"--camera-id {args.camera_id} --model-id {args.model_id} --algorithm-id {args.algorithm_id} "
                f"--invalid-camera-id {args.invalid_camera_id} --timeout-sec {args.timeout_sec} "
                f"--output-dir {shlex.quote(base + '-source-policy')}"
            ),
        ),
        (
            'ai',
            (
                f"python3 scripts/testing/run_inference_quality_diagnostics.py "
                f"--bridge-url {shlex.quote(args.bridge_url)} "
                f"--camera-id {args.camera_id} --model-id {args.model_id} "
                f"--source {shlex.quote(args.source)} --plugin-id {shlex.quote(args.plugin_id)} "
                f"--iterations {args.quality_iterations} --interval-ms {args.quality_interval_ms} "
                f"--timeout-sec {args.timeout_sec} "
                f"--output-dir {shlex.quote(base + '-quality')}"
            ),
        ),
        (
            'qa',
            (
                "python3 -m unittest "
                "scripts/testing/test_validate_dispatch_source_policy.py "
                "scripts/testing/test_run_inference_quality_diagnostics.py "
                "scripts/testing/test_web_ui_live_smoke.py"
            ),
        ),
        (
            'integration',
            (
                f"python3 scripts/testing/web_ui_live_smoke.py "
                f"--base-url {shlex.quote(args.base_url)} "
                f"--username {shlex.quote(args.web_username)} "
                f"--password {shlex.quote(args.web_password)} "
                f"--timeout-sec {args.timeout_sec}"
            ),
        ),
    ]


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Control tmux-based parallel lanes')
    parser.add_argument('command', choices=['start', 'status', 'stop', 'list'])
    parser.add_argument('--session', default='phase2-parallel')
    parser.add_argument('--workdir', default='.')
    parser.add_argument('--force', action='store_true', default=False)
    parser.add_argument('--lane', action='append', default=[])
    parser.add_argument('--with-default-lanes', action='store_true', default=False)

    parser.add_argument('--base-url', default='http://127.0.0.1:18082')
    parser.add_argument('--bridge-url', default='http://127.0.0.1:19080')
    parser.add_argument('--camera-id', type=int, default=1)
    parser.add_argument('--model-id', type=int, default=1)
    parser.add_argument('--algorithm-id', type=int, default=1)
    parser.add_argument('--invalid-camera-id', type=int, default=999999)
    parser.add_argument('--plugin-id', default='yolov8n')
    parser.add_argument('--source', default='test://frame')
    parser.add_argument('--quality-iterations', type=int, default=20)
    parser.add_argument('--quality-interval-ms', type=int, default=250)
    parser.add_argument('--timeout-sec', type=int, default=45)
    parser.add_argument('--web-username', default='admin')
    parser.add_argument('--web-password', default='admin123')
    parser.add_argument('--output-root', default='runtime/test-out/parallel')
    return parser.parse_args(argv)


def start_session(args: argparse.Namespace) -> Dict[str, object]:
    if not tmux_available():
        return {'status': 'tmux_missing', 'session': args.session}

    if session_exists(args.session):
        if args.force:
            run_tmux(['kill-session', '-t', args.session])
        else:
            return {'status': 'already_running', 'session': args.session, 'windows': list_windows(args.session)}

    lanes: List[Tuple[str, str]] = []
    if args.with_default_lanes:
        lanes.extend(build_default_lanes(args))
    for lane in args.lane:
        lanes.append(parse_lane(lane))
    if not lanes:
        lanes = [('main', 'bash')]

    workdir = Path(args.workdir).resolve()
    first_name, first_cmd = lanes[0]
    first = run_tmux(['new-session', '-d', '-s', args.session, '-n', first_name, build_lane_command(workdir, first_cmd)])
    if first.returncode != 0:
        return {
            'status': 'start_failed',
            'session': args.session,
            'error': (first.stderr or first.stdout or '').strip(),
        }

    for lane_name, lane_cmd in lanes[1:]:
        run_tmux(['new-window', '-t', args.session, '-n', lane_name, build_lane_command(workdir, lane_cmd)])

    return {
        'status': 'started',
        'session': args.session,
        'lane_count': len(lanes),
        'lanes': [name for name, _ in lanes],
        'windows': list_windows(args.session),
    }


def status_session(session: str) -> Dict[str, object]:
    if not tmux_available():
        return {'status': 'tmux_missing', 'session': session}
    if not session_exists(session):
        return {'status': 'not_running', 'session': session}
    return {'status': 'running', 'session': session, 'windows': list_windows(session)}


def stop_session(session: str) -> Dict[str, object]:
    if not tmux_available():
        return {'status': 'tmux_missing', 'session': session}
    if not session_exists(session):
        return {'status': 'not_running', 'session': session}
    result = run_tmux(['kill-session', '-t', session])
    return {'status': 'stopped' if result.returncode == 0 else 'stop_failed', 'session': session}


def list_sessions() -> Dict[str, object]:
    if not tmux_available():
        return {'status': 'tmux_missing', 'sessions': []}
    result = run_tmux(['list-sessions', '-F', '#S'])
    sessions = [line.strip() for line in (result.stdout or '').splitlines() if line.strip()]
    return {'status': 'listed', 'sessions': sessions}


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    if args.command == 'start':
        result = start_session(args)
    elif args.command == 'status':
        result = status_session(args.session)
    elif args.command == 'stop':
        result = stop_session(args.session)
    else:
        result = list_sessions()
    print(json.dumps(result, ensure_ascii=True))
    return 0 if result.get('status') in {'started', 'already_running', 'running', 'stopped', 'not_running', 'listed'} else 1


if __name__ == '__main__':
    raise SystemExit(main())
