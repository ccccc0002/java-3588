#!/usr/bin/env python3
"""Launch and control parallel task lanes inside tmux."""

from __future__ import annotations

import argparse
import datetime
import json
import re
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


def parse_lane_exit_code(pane_text: str) -> Optional[int]:
    text = str(pane_text or '')
    matches = re.findall(r'\[lane-exit:(-?\d+)\]', text)
    if not matches:
        return None
    try:
        return int(matches[-1])
    except ValueError:
        return None


def capture_window_report(session: str, window_name: str, tail_lines: int) -> Dict[str, object]:
    result = run_tmux(['capture-pane', '-pt', f'{session}:{window_name}', '-S', '-400'])
    pane_text = result.stdout or ''
    lane_exit = parse_lane_exit_code(pane_text)
    tail_count = max(int(tail_lines), 0)
    tail = pane_text.splitlines()[-tail_count:] if tail_count > 0 else []

    lane_status = 'running'
    if lane_exit is not None:
        lane_status = 'passed' if lane_exit == 0 else 'failed'

    return {
        'name': window_name,
        'lane_exit': lane_exit,
        'lane_status': lane_status,
        'tail': tail,
    }


def build_default_lanes(args: argparse.Namespace) -> List[Tuple[str, str]]:
    output_root = str(args.output_root).strip()
    stamp = datetime.datetime.utcnow().strftime('%Y%m%d-%H%M%S')
    base = f'{output_root}/{stamp}'
    quality_args = [
        f"--iterations {args.quality_iterations}",
        f"--interval-ms {args.quality_interval_ms}",
        f"--retry-attempts {args.quality_retry_attempts}",
        f"--retry-interval-ms {args.quality_retry_interval_ms}",
        f"--timeout-sec {args.timeout_sec}",
    ]
    if int(args.quality_max_invalid_bbox_count) >= 0:
        quality_args.append(f"--max-invalid-bbox-count {args.quality_max_invalid_bbox_count}")
    if int(args.quality_max_invalid_score_count) >= 0:
        quality_args.append(f"--max-invalid-score-count {args.quality_max_invalid_score_count}")
    if int(args.quality_max_empty_label_count) >= 0:
        quality_args.append(f"--max-empty-label-count {args.quality_max_empty_label_count}")
    if int(args.quality_max_failed_iterations) >= 0:
        quality_args.append(f"--max-failed-iterations {args.quality_max_failed_iterations}")

    return [
        (
            'media',
            (
                f"python3 scripts/testing/validate_dispatch_source_policy.py "
                f"--base-url {shlex.quote(args.base_url)} "
                f"--camera-id {args.camera_id} --model-id {args.model_id} --algorithm-id {args.algorithm_id} "
                f"--invalid-camera-id {args.invalid_camera_id} --timeout-sec {args.timeout_sec} "
                f"--retry-attempts {args.source_policy_retry_attempts} "
                f"--retry-interval-ms {args.source_policy_retry_interval_ms} "
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
                f"{' '.join(quality_args)} "
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
                f"--timeout-sec {args.timeout_sec} "
                f"--skip-capture-endpoints"
            ),
        ),
    ]


def load_lanes_from_file(path: Path) -> List[Tuple[str, str]]:
    payload = json.loads(path.read_text(encoding='utf-8-sig'))
    if not isinstance(payload, list):
        raise ValueError('lane file must be a JSON array')
    lanes: List[Tuple[str, str]] = []
    for item in payload:
        if not isinstance(item, dict):
            raise ValueError('lane entry must be an object')
        name = str(item.get('name', '')).strip()
        command = str(item.get('command', '')).strip()
        if not name or not command:
            raise ValueError('lane entry requires name and command')
        lanes.append((name, command))
    return lanes


def parse_session_sort_key(name: str) -> Tuple[str, int, str]:
    text = str(name or '').strip()
    match = re.search(r'-r(\d+)$', text)
    if not match:
        return text, -1, text
    try:
        return text[: match.start()], int(match.group(1)), text
    except ValueError:
        return text, -1, text


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Control tmux-based parallel lanes')
    parser.add_argument('command', choices=['start', 'status', 'stop', 'list', 'report', 'prune'])
    parser.add_argument('--session', default='phase2-parallel')
    parser.add_argument('--workdir', default='.')
    parser.add_argument('--force', action='store_true', default=False)
    parser.add_argument('--lane', action='append', default=[])
    parser.add_argument('--lane-file', default='')
    parser.add_argument('--with-default-lanes', action='store_true', default=False)
    parser.add_argument('--tail-lines', type=int, default=10)
    parser.add_argument('--session-prefix', default='phase2-')
    parser.add_argument('--keep-latest', type=int, default=8)

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
    parser.add_argument('--quality-retry-attempts', type=int, default=1)
    parser.add_argument('--quality-retry-interval-ms', type=int, default=200)
    parser.add_argument('--quality-max-invalid-bbox-count', type=int, default=-1)
    parser.add_argument('--quality-max-invalid-score-count', type=int, default=-1)
    parser.add_argument('--quality-max-empty-label-count', type=int, default=-1)
    parser.add_argument('--quality-max-failed-iterations', type=int, default=0)
    parser.add_argument('--source-policy-retry-attempts', type=int, default=3)
    parser.add_argument('--source-policy-retry-interval-ms', type=int, default=300)
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
    if args.lane_file:
        lanes.extend(load_lanes_from_file(Path(args.lane_file).resolve()))
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


def report_session(session: str, tail_lines: int) -> Dict[str, object]:
    if not tmux_available():
        return {'status': 'tmux_missing', 'session': session}
    if not session_exists(session):
        return {'status': 'not_running', 'session': session, 'lane_count': 0, 'lanes': []}

    windows = list_windows(session)
    lanes = [capture_window_report(session, str(item.get('name', '')), tail_lines) for item in windows]

    passed_count = sum(1 for lane in lanes if lane.get('lane_status') == 'passed')
    failed_count = sum(1 for lane in lanes if lane.get('lane_status') == 'failed')
    running_count = sum(1 for lane in lanes if lane.get('lane_status') == 'running')

    if running_count > 0:
        status = 'running'
    elif failed_count > 0:
        status = 'failed'
    else:
        status = 'passed'

    return {
        'status': status,
        'session': session,
        'lane_count': len(lanes),
        'passed_count': passed_count,
        'failed_count': failed_count,
        'running_count': running_count,
        'lanes': lanes,
    }


def list_sessions() -> Dict[str, object]:
    if not tmux_available():
        return {'status': 'tmux_missing', 'sessions': []}
    result = run_tmux(['list-sessions', '-F', '#S'])
    sessions = [line.strip() for line in (result.stdout or '').splitlines() if line.strip()]
    return {'status': 'listed', 'sessions': sessions}


def prune_sessions(prefix: str, keep_latest: int) -> Dict[str, object]:
    if not tmux_available():
        return {'status': 'tmux_missing', 'sessions': []}
    session_result = list_sessions()
    all_sessions = [str(item).strip() for item in session_result.get('sessions', []) if str(item).strip()]
    prefix_text = str(prefix or '').strip()
    matched = [name for name in all_sessions if name.startswith(prefix_text)] if prefix_text else list(all_sessions)
    if not matched:
        return {
            'status': 'pruned',
            'prefix': prefix_text,
            'keep_latest': max(int(keep_latest), 0),
            'matched_count': 0,
            'killed': [],
            'kept': [],
        }

    ordered = sorted(matched, key=parse_session_sort_key)
    keep_count = max(int(keep_latest), 0)
    kept = ordered[-keep_count:] if keep_count > 0 else []
    keep_set = set(kept)
    killed: List[str] = []
    failed: List[str] = []
    for name in ordered:
        if name in keep_set:
            continue
        result = run_tmux(['kill-session', '-t', name])
        if result.returncode == 0:
            killed.append(name)
        else:
            failed.append(name)

    status = 'pruned' if not failed else 'prune_partial_failed'
    return {
        'status': status,
        'prefix': prefix_text,
        'keep_latest': keep_count,
        'matched_count': len(matched),
        'killed': killed,
        'kept': kept,
        'failed': failed,
    }


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    if args.command == 'start':
        result = start_session(args)
    elif args.command == 'status':
        result = status_session(args.session)
    elif args.command == 'stop':
        result = stop_session(args.session)
    elif args.command == 'report':
        result = report_session(args.session, args.tail_lines)
    elif args.command == 'prune':
        result = prune_sessions(args.session_prefix, args.keep_latest)
    else:
        result = list_sessions()
    print(json.dumps(result, ensure_ascii=True))
    return 0 if result.get('status') in {
        'started',
        'already_running',
        'running',
        'stopped',
        'not_running',
        'listed',
        'passed',
        'failed',
        'pruned',
    } else 1


if __name__ == '__main__':
    raise SystemExit(main())
