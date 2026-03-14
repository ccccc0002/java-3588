#!/usr/bin/env python3
"""Phase11 orchestration: run acceptance + soak and capture resource evidence."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Sequence

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import run_phase10_acceptance


DEFAULT_OUTPUT_DIR = "scripts/testing/out/phase11-handoff"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Phase11 handoff validation and resource evidence collection.")
    parser.add_argument("--base-url", default="http://127.0.0.1:18082")
    parser.add_argument("--runtime-api-url", default="http://127.0.0.1:18081")
    parser.add_argument("--bridge-url", default="http://127.0.0.1:19080")
    parser.add_argument("--bootstrap-token", default="edge-demo-bootstrap")
    parser.add_argument("--plugin-id", default="yolov8n")
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--model-id", type=int, default=1)
    parser.add_argument("--algorithm-id", type=int, default=1)
    parser.add_argument("--source", default="test://frame")
    parser.add_argument("--timeout-sec", type=int, default=30)
    parser.add_argument("--runtime-stack-budget", type=float, default=10.0)
    parser.add_argument("--expect-runtime-api-backend", default="")
    parser.add_argument("--expect-snapshot-telemetry-status", default="any", choices=["any", "ok", "degraded"])
    parser.add_argument("--expect-plan-telemetry-status", default="any", choices=["any", "ok", "degraded"])
    parser.add_argument("--expect-bridge-decode-runtime-status", default="ok", choices=["any", "ok", "degraded"])
    parser.add_argument("--expect-bridge-decode-mode", default="mpp-rga")
    parser.add_argument("--max-plan-concurrency-pressure", type=float, default=0.0)
    parser.add_argument("--max-plan-suggested-min-dispatch-ms", type=int, default=0)
    parser.add_argument("--min-snapshot-ready-stream-count", type=int, default=0)
    parser.add_argument("--min-plan-ready-stream-count", type=int, default=0)
    parser.add_argument("--cookie", default="")
    parser.add_argument("--auth-header-name", default="")
    parser.add_argument("--auth-header-value", default="")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--manage-bridge", action="store_true")
    parser.add_argument("--bridge-bootstrap-token", default="")
    parser.add_argument("--bridge-wait-seconds", type=int, default=20)
    parser.add_argument("--bridge-poll-interval", type=int, default=1)
    parser.add_argument("--soak-duration-sec", type=int, default=120)
    parser.add_argument("--soak-interval-sec", type=int, default=5)
    parser.add_argument("--soak-max-iterations", type=int, default=1)
    parser.add_argument("--soak-max-failed-steps", type=int, default=0)
    parser.add_argument("--max-memory-used-delta-mb", type=float, default=0.0)
    parser.add_argument("--max-loadavg-1m", type=float, default=0.0)
    parser.add_argument("--verify-alarm-preview", action="store_true")
    parser.add_argument("--alarm-preview-timeout-sec", type=float, default=45.0)
    parser.add_argument("--verify-quality-diagnostics", action="store_true")
    parser.add_argument("--quality-iterations", type=int, default=20)
    parser.add_argument("--quality-interval-ms", type=int, default=200)
    parser.add_argument("--quality-timeout-sec", type=float, default=30.0)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


def parse_meminfo(meminfo_path: str = "/proc/meminfo") -> Dict[str, float]:
    values: Dict[str, float] = {}
    if not os.path.exists(meminfo_path):
        return values
    with open(meminfo_path, "r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or ":" not in line:
                continue
            key, rest = line.split(":", 1)
            token = rest.strip().split(" ", 1)[0]
            try:
                values[key] = float(token)
            except ValueError:
                continue
    total_kb = values.get("MemTotal")
    avail_kb = values.get("MemAvailable")
    if total_kb is None or avail_kb is None:
        return {}
    used_kb = max(total_kb - avail_kb, 0.0)
    return {
        "total_mb": round(total_kb / 1024.0, 2),
        "available_mb": round(avail_kb / 1024.0, 2),
        "used_mb": round(used_kb / 1024.0, 2),
    }


def parse_loadavg(loadavg_path: str = "/proc/loadavg") -> Dict[str, float]:
    if not os.path.exists(loadavg_path):
        return {}
    with open(loadavg_path, "r", encoding="utf-8") as handle:
        raw = handle.read().strip()
    parts = raw.split()
    if len(parts) < 3:
        return {}
    result: Dict[str, float] = {}
    labels = ["loadavg_1m", "loadavg_5m", "loadavg_15m"]
    for index, label in enumerate(labels):
        try:
            result[label] = float(parts[index])
        except ValueError:
            continue
    return result


def top_processes(limit: int = 5) -> List[Dict[str, Any]]:
    cmd = ["ps", "-eo", "pid,pcpu,pmem,rss,comm", "--sort=-pcpu"]
    try:
        completed = subprocess.run(cmd, capture_output=True, text=True, check=False)
    except FileNotFoundError:
        return []
    if completed.returncode != 0:
        return []
    lines = [line.strip() for line in (completed.stdout or "").splitlines() if line.strip()]
    if len(lines) <= 1:
        return []
    result: List[Dict[str, Any]] = []
    for line in lines[1 : limit + 1]:
        cols = line.split(None, 4)
        if len(cols) < 5:
            continue
        pid_raw, cpu_raw, mem_raw, rss_raw, cmd_raw = cols
        try:
            pid = int(pid_raw)
            cpu = float(cpu_raw)
            mem = float(mem_raw)
            rss_kb = int(rss_raw)
        except ValueError:
            continue
        result.append(
            {
                "pid": pid,
                "cpu_percent": cpu,
                "mem_percent": mem,
                "rss_mb": round(rss_kb / 1024.0, 2),
                "command": cmd_raw,
            }
        )
    return result


def collect_resource_snapshot(label: str) -> Dict[str, Any]:
    return {
        "label": label,
        "timestamp": utc_now(),
        "cpu": parse_loadavg(),
        "memory": parse_meminfo(),
        "top_processes": top_processes(),
    }


def build_phase10_argv(args: argparse.Namespace, phase10_output_dir: str) -> List[str]:
    argv: List[str] = [
        "--base-url",
        args.base_url,
        "--runtime-api-url",
        args.runtime_api_url,
        "--bridge-url",
        args.bridge_url,
        "--bootstrap-token",
        args.bootstrap_token,
        "--plugin-id",
        args.plugin_id,
        "--camera-id",
        str(args.camera_id),
        "--model-id",
        str(args.model_id),
        "--algorithm-id",
        str(args.algorithm_id),
        "--source",
        args.source,
        "--timeout-sec",
        str(args.timeout_sec),
        "--runtime-stack-budget",
        str(float(args.runtime_stack_budget)),
        "--expect-runtime-api-backend",
        args.expect_runtime_api_backend,
        "--expect-snapshot-telemetry-status",
        args.expect_snapshot_telemetry_status,
        "--expect-plan-telemetry-status",
        args.expect_plan_telemetry_status,
        "--expect-bridge-decode-runtime-status",
        args.expect_bridge_decode_runtime_status,
        "--expect-bridge-decode-mode",
        args.expect_bridge_decode_mode,
        "--max-plan-concurrency-pressure",
        str(float(args.max_plan_concurrency_pressure)),
        "--max-plan-suggested-min-dispatch-ms",
        str(int(args.max_plan_suggested_min_dispatch_ms)),
        "--min-snapshot-ready-stream-count",
        str(int(args.min_snapshot_ready_stream_count)),
        "--min-plan-ready-stream-count",
        str(int(args.min_plan_ready_stream_count)),
        "--output-dir",
        phase10_output_dir,
        "--include-soak",
        "--soak-duration-sec",
        str(int(args.soak_duration_sec)),
        "--soak-interval-sec",
        str(int(args.soak_interval_sec)),
        "--soak-max-iterations",
        str(int(args.soak_max_iterations)),
        "--soak-max-failed-steps",
        str(int(args.soak_max_failed_steps)),
    ]
    if args.cookie:
        argv.extend(["--cookie", args.cookie])
    if args.auth_header_name:
        argv.extend(["--auth-header-name", args.auth_header_name])
    if args.auth_header_value:
        argv.extend(["--auth-header-value", args.auth_header_value])
    if args.manage_bridge:
        argv.append("--manage-bridge")
    if args.bridge_bootstrap_token:
        argv.extend(["--bridge-bootstrap-token", args.bridge_bootstrap_token])
    argv.extend(["--bridge-wait-seconds", str(int(args.bridge_wait_seconds))])
    argv.extend(["--bridge-poll-interval", str(int(args.bridge_poll_interval))])
    if args.dry_run:
        argv.append("--dry-run")
    return argv


def build_alarm_preview_argv(args: argparse.Namespace, alarm_preview_output_dir: str) -> List[str]:
    return [
        "--base-url",
        args.base_url,
        "--camera-id",
        str(args.camera_id),
        "--model-id",
        str(args.model_id),
        "--algorithm-id",
        str(args.algorithm_id),
        "--source",
        args.source,
        "--plugin-id",
        args.plugin_id,
        "--timeout-sec",
        str(float(args.alarm_preview_timeout_sec)),
        "--output-dir",
        alarm_preview_output_dir,
    ]


def default_alarm_preview_runner(argv: List[str]) -> Dict[str, Any]:
    script_path = SCRIPT_DIR / "verify_alarm_stream_annotation.py"
    cmd = [sys.executable, str(script_path), *argv]
    completed = subprocess.run(cmd, capture_output=True, text=True, check=False)
    return {
        "exit_code": int(completed.returncode),
        "stdout": completed.stdout or "",
        "stderr": completed.stderr or "",
    }


def build_quality_diagnostics_argv(args: argparse.Namespace, quality_output_dir: str) -> List[str]:
    return [
        "--bridge-url",
        args.bridge_url,
        "--camera-id",
        str(args.camera_id),
        "--model-id",
        str(args.model_id),
        "--source",
        args.source,
        "--plugin-id",
        args.plugin_id,
        "--iterations",
        str(int(args.quality_iterations)),
        "--interval-ms",
        str(int(args.quality_interval_ms)),
        "--timeout-sec",
        str(float(args.quality_timeout_sec)),
        "--output-dir",
        quality_output_dir,
    ]


def default_quality_diagnostics_runner(argv: List[str]) -> Dict[str, Any]:
    script_path = SCRIPT_DIR / "run_inference_quality_diagnostics.py"
    cmd = [sys.executable, str(script_path), *argv]
    completed = subprocess.run(cmd, capture_output=True, text=True, check=False)
    return {
        "exit_code": int(completed.returncode),
        "stdout": completed.stdout or "",
        "stderr": completed.stderr or "",
    }


def compact_text(value: str, max_chars: int = 2000) -> str:
    text = str(value or "").strip()
    if len(text) <= max_chars:
        return text
    return text[-max_chars:]


def build_summary(
    args: argparse.Namespace,
    started_at: str,
    finished_at: str,
    phase10_exit_code: int,
    phase10_output_dir: str,
    alarm_preview_output_dir: str,
    alarm_preview_exit_code: Optional[int],
    alarm_preview_run: Optional[Dict[str, Any]],
    quality_output_dir: str,
    quality_exit_code: Optional[int],
    quality_run: Optional[Dict[str, Any]],
    before: Dict[str, Any],
    after: Dict[str, Any],
) -> Dict[str, Any]:
    alarm_preview_enabled = bool(args.verify_alarm_preview)
    alarm_preview_passed = (not alarm_preview_enabled) or (alarm_preview_exit_code == 0)
    quality_enabled = bool(args.verify_quality_diagnostics)
    quality_passed = (not quality_enabled) or (quality_exit_code == 0)
    status = "passed" if phase10_exit_code == 0 and alarm_preview_passed and quality_passed else "failed"
    memory_delta_mb: Optional[float] = None
    before_used = ((before.get("memory") or {}) if isinstance(before, dict) else {}).get("used_mb")
    after_used = ((after.get("memory") or {}) if isinstance(after, dict) else {}).get("used_mb")
    if isinstance(before_used, (int, float)) and isinstance(after_used, (int, float)):
        memory_delta_mb = round(float(after_used) - float(before_used), 2)
    max_memory_used_delta_mb = float(getattr(args, "max_memory_used_delta_mb", 0.0) or 0.0)
    max_loadavg_1m = float(getattr(args, "max_loadavg_1m", 0.0) or 0.0)
    resource_gate_enabled = max_memory_used_delta_mb > 0.0 or max_loadavg_1m > 0.0
    resource_gate_violations: List[str] = []
    if max_memory_used_delta_mb > 0.0 and isinstance(memory_delta_mb, (int, float)) and memory_delta_mb > max_memory_used_delta_mb:
        resource_gate_violations.append(
            f"memory_used_delta_mb {memory_delta_mb} exceeds max_memory_used_delta_mb {max_memory_used_delta_mb}"
        )
    after_loadavg_1m = ((after.get("cpu") or {}) if isinstance(after, dict) else {}).get("loadavg_1m")
    if max_loadavg_1m > 0.0 and isinstance(after_loadavg_1m, (int, float)) and float(after_loadavg_1m) > max_loadavg_1m:
        resource_gate_violations.append(
            f"after.loadavg_1m {after_loadavg_1m} exceeds max_loadavg_1m {max_loadavg_1m}"
        )
    resource_gate_status = "skipped"
    if resource_gate_enabled:
        resource_gate_status = "passed" if not resource_gate_violations else "failed"
    if status == "passed" and resource_gate_status == "failed":
        status = "failed"
    alarm_preview_status = "skipped"
    if alarm_preview_enabled:
        alarm_preview_status = "passed" if alarm_preview_exit_code == 0 else "failed"
    quality_status = "skipped"
    if quality_enabled:
        quality_status = "passed" if quality_exit_code == 0 else "failed"
    return {
        "started_at": started_at,
        "finished_at": finished_at,
        "status": status,
        "phase10_exit_code": int(phase10_exit_code),
        "phase10_output_dir": phase10_output_dir,
        "alarm_preview": {
            "enabled": alarm_preview_enabled,
            "status": alarm_preview_status,
            "exit_code": alarm_preview_exit_code,
            "output_dir": alarm_preview_output_dir,
            "stdout": compact_text((alarm_preview_run or {}).get("stdout", "")),
            "stderr": compact_text((alarm_preview_run or {}).get("stderr", "")),
        },
        "quality_diagnostics": {
            "enabled": quality_enabled,
            "status": quality_status,
            "exit_code": quality_exit_code,
            "output_dir": quality_output_dir,
            "stdout": compact_text((quality_run or {}).get("stdout", "")),
            "stderr": compact_text((quality_run or {}).get("stderr", "")),
        },
        "dry_run": bool(args.dry_run),
        "resource": {
            "before": before,
            "after": after,
            "memory_used_delta_mb": memory_delta_mb,
        },
        "resource_gate": {
            "enabled": resource_gate_enabled,
            "status": resource_gate_status,
            "max_memory_used_delta_mb": max_memory_used_delta_mb,
            "max_loadavg_1m": max_loadavg_1m,
            "violations": resource_gate_violations,
        },
    }


def main(
    argv: Optional[Sequence[str]] = None,
    phase10_runner: Optional[Callable[[Optional[Sequence[str]]], int]] = None,
    resource_collector: Optional[Callable[[str], Dict[str, Any]]] = None,
    alarm_preview_runner: Optional[Callable[[List[str]], Dict[str, Any]]] = None,
    quality_diagnostics_runner: Optional[Callable[[List[str]], Dict[str, Any]]] = None,
) -> int:
    args = parse_args(argv)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    summary_path = output_dir / "summary.json"

    started_at = utc_now()
    collector = resource_collector or collect_resource_snapshot
    before = collector("before")

    phase10_output_dir = str(output_dir / "phase10-acceptance")
    phase10_argv = build_phase10_argv(args, phase10_output_dir)
    runner = phase10_runner or run_phase10_acceptance.main
    phase10_exit_code = int(runner(phase10_argv))

    alarm_preview_output_dir = str(output_dir / "alarm-preview")
    alarm_preview_exit_code: Optional[int] = None
    alarm_preview_run: Optional[Dict[str, Any]] = None
    if phase10_exit_code == 0 and args.verify_alarm_preview:
        preview_argv = build_alarm_preview_argv(args, alarm_preview_output_dir)
        preview_runner = alarm_preview_runner or default_alarm_preview_runner
        alarm_preview_run = preview_runner(preview_argv)
        try:
            alarm_preview_exit_code = int((alarm_preview_run or {}).get("exit_code", 1))
        except Exception:
            alarm_preview_exit_code = 1

    quality_output_dir = str(output_dir / "quality-diagnostics")
    quality_exit_code: Optional[int] = None
    quality_run: Optional[Dict[str, Any]] = None
    if phase10_exit_code == 0 and args.verify_quality_diagnostics:
        quality_argv = build_quality_diagnostics_argv(args, quality_output_dir)
        quality_runner = quality_diagnostics_runner or default_quality_diagnostics_runner
        quality_run = quality_runner(quality_argv)
        try:
            quality_exit_code = int((quality_run or {}).get("exit_code", 1))
        except Exception:
            quality_exit_code = 1

    after = collector("after")
    finished_at = utc_now()

    summary = build_summary(
        args=args,
        started_at=started_at,
        finished_at=finished_at,
        phase10_exit_code=phase10_exit_code,
        phase10_output_dir=phase10_output_dir,
        alarm_preview_output_dir=alarm_preview_output_dir,
        alarm_preview_exit_code=alarm_preview_exit_code,
        alarm_preview_run=alarm_preview_run,
        quality_output_dir=quality_output_dir,
        quality_exit_code=quality_exit_code,
        quality_run=quality_run,
        before=before,
        after=after,
    )
    summary_path.write_text(json.dumps(summary, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=True, indent=2))
    return 0 if summary.get("status") == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
