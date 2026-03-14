#!/usr/bin/env python3
"""Run phase3 nextwave lanes in a continuous loop with pruning."""

from __future__ import annotations

import argparse
import datetime
import json
import re
import subprocess
import sys
import time
from pathlib import Path
from typing import Callable, Dict, List, Optional, Sequence

Runner = Callable[[List[str]], subprocess.CompletedProcess]
NowFn = Callable[[], float]
SleepFn = Callable[[float], None]


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Continuous phase3 nextwave parallel loop.")
    parser.add_argument("--python-bin", default=sys.executable)
    parser.add_argument("--tmux-ctl", default="scripts/rk3588/tmux_parallel_ctl.py")
    parser.add_argument("--lane-file", default="scripts/rk3588/lanes/phase3-nextwave.json")
    parser.add_argument("--workdir", default=".")
    parser.add_argument("--session-prefix", default="phase3-nextwave-r")
    parser.add_argument("--start-index", type=int, default=0)
    parser.add_argument("--iterations", type=int, default=1)
    parser.add_argument("--report-tail-lines", type=int, default=12)
    parser.add_argument("--poll-interval-sec", type=float, default=15.0)
    parser.add_argument("--session-timeout-sec", type=float, default=1800.0)
    parser.add_argument("--prune-prefix", default="phase3-nextwave-")
    parser.add_argument("--keep-latest", type=int, default=4)
    parser.add_argument("--stop-on-failure", action="store_true", default=True)
    parser.add_argument("--output-dir", default="runtime/test-out/parallel/nextwave-loop")
    return parser.parse_args(argv)


def _default_runner(command: List[str]) -> subprocess.CompletedProcess:
    return subprocess.run(command, check=False, capture_output=True, text=True)


def _extract_json_payload(text: str) -> Dict[str, object]:
    value = str(text or "").strip()
    if not value:
        raise ValueError("empty command output")
    lines = [item.strip() for item in value.splitlines() if item.strip()]
    for line in reversed(lines):
        if line.startswith("{") and line.endswith("}"):
            return json.loads(line)
    raise ValueError(f"unable to parse json payload from output: {value}")


def run_tmux_ctl(
    python_bin: str,
    tmux_ctl: Path,
    args: List[str],
    runner: Runner,
) -> Dict[str, object]:
    command = [str(python_bin), str(tmux_ctl), *args]
    result = runner(command)
    payload = _extract_json_payload(result.stdout)
    payload["command"] = args
    payload["exit_code"] = int(result.returncode)
    return payload


def resolve_start_index(session_prefix: str, requested_start_index: int, sessions: Sequence[str]) -> int:
    if int(requested_start_index) > 0:
        return int(requested_start_index)
    prefix = str(session_prefix or "")
    max_index = 0
    pattern = re.compile(rf"^{re.escape(prefix)}(\d+)$")
    for session in sessions:
        match = pattern.match(str(session or "").strip())
        if not match:
            continue
        index = int(match.group(1))
        if index > max_index:
            max_index = index
    return max_index + 1 if max_index > 0 else 1


def poll_until_terminal(
    python_bin: str,
    tmux_ctl: Path,
    session: str,
    tail_lines: int,
    timeout_sec: float,
    poll_interval_sec: float,
    runner: Runner,
    now_fn: NowFn,
    sleep_fn: SleepFn,
) -> Dict[str, object]:
    started_at = float(now_fn())
    while True:
        report = run_tmux_ctl(
            python_bin,
            tmux_ctl,
            ["report", "--session", session, "--tail-lines", str(int(tail_lines))],
            runner,
        )
        status = str(report.get("status", "")).strip().lower()
        if status in {"passed", "failed", "not_running"}:
            return report
        elapsed = float(now_fn()) - started_at
        if timeout_sec > 0 and elapsed >= float(timeout_sec):
            report["status"] = "timeout"
            report["elapsed_sec"] = round(elapsed, 3)
            return report
        sleep_fn(max(float(poll_interval_sec), 0.0))


def _write_summary(output_dir: Path, summary: Dict[str, object]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.datetime.utcnow().strftime("%Y%m%d-%H%M%S")
    summary_path = output_dir / f"summary-{stamp}.json"
    latest_path = output_dir / "latest.json"
    encoded = json.dumps(summary, ensure_ascii=True, indent=2)
    summary_path.write_text(encoded + "\n", encoding="utf-8")
    latest_path.write_text(encoded + "\n", encoding="utf-8")


def execute_loop(
    args: argparse.Namespace,
    runner: Runner = _default_runner,
    now_fn: NowFn = time.monotonic,
    sleep_fn: SleepFn = time.sleep,
) -> Dict[str, object]:
    tmux_ctl = Path(str(args.tmux_ctl)).resolve()
    list_result = run_tmux_ctl(str(args.python_bin), tmux_ctl, ["list"], runner)
    sessions = [str(item).strip() for item in list_result.get("sessions", []) if str(item).strip()]
    next_index = resolve_start_index(str(args.session_prefix), int(args.start_index), sessions)

    runs: List[Dict[str, object]] = []
    max_iterations = max(int(args.iterations), 0)
    for _ in range(max_iterations):
        session = f"{args.session_prefix}{next_index}"
        start = run_tmux_ctl(
            str(args.python_bin),
            tmux_ctl,
            [
                "start",
                "--session",
                session,
                "--force",
                "--lane-file",
                str(args.lane_file),
                "--workdir",
                str(args.workdir),
            ],
            runner,
        )

        report: Dict[str, object] = {"status": "start_failed"}
        if str(start.get("status", "")).strip() in {"started", "already_running"}:
            report = poll_until_terminal(
                str(args.python_bin),
                tmux_ctl,
                session,
                int(args.report_tail_lines),
                float(args.session_timeout_sec),
                float(args.poll_interval_sec),
                runner,
                now_fn,
                sleep_fn,
            )

        prune = run_tmux_ctl(
            str(args.python_bin),
            tmux_ctl,
            [
                "prune",
                "--session-prefix",
                str(args.prune_prefix),
                "--keep-latest",
                str(int(args.keep_latest)),
            ],
            runner,
        )

        run_status = str(report.get("status", "")).strip().lower()
        run_item = {
            "session": session,
            "start_status": str(start.get("status", "")),
            "report_status": run_status,
            "prune_status": str(prune.get("status", "")),
            "passed": run_status == "passed",
        }
        runs.append(run_item)

        if run_status != "passed" and bool(args.stop_on_failure):
            break
        next_index += 1

    overall_status = "passed" if runs and all(item.get("passed") for item in runs) else "failed"
    if not runs:
        overall_status = "failed"

    summary = {
        "status": overall_status,
        "session_prefix": str(args.session_prefix),
        "iterations_requested": int(args.iterations),
        "iterations_executed": len(runs),
        "runs": runs,
        "generated_at": datetime.datetime.utcnow().isoformat() + "Z",
    }
    _write_summary(Path(str(args.output_dir)).resolve(), summary)
    return summary


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    summary = execute_loop(args)
    print(json.dumps(summary, ensure_ascii=True))
    return 0 if str(summary.get("status", "")) == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
