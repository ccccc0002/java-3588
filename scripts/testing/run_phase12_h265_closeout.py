#!/usr/bin/env python3
"""Phase12 closeout runner: enforce H.265 + long-soak handoff on RK3588."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import List, Optional, Sequence

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import run_phase11_handoff


DEFAULT_OUTPUT_DIR = "scripts/testing/out/phase12-h265-closeout"
DEFAULT_H265_SOURCE = "rtsp://admin:Admin123@192.168.1.245:554/h265/ch1/main/av_stream"


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Phase12 H.265 closeout (acceptance + soak + quality diagnostics).")
    parser.add_argument("--base-url", default="http://127.0.0.1:18082")
    parser.add_argument("--runtime-api-url", default="http://127.0.0.1:18081")
    parser.add_argument("--bridge-url", default="http://127.0.0.1:19080")
    parser.add_argument("--bootstrap-token", default="edge-demo-bootstrap")
    parser.add_argument("--plugin-id", default="yolov8n")
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--model-id", type=int, default=1)
    parser.add_argument("--algorithm-id", type=int, default=1)
    parser.add_argument("--source", default=DEFAULT_H265_SOURCE)
    parser.add_argument("--dry-run-source", default="test://frame")
    parser.add_argument("--timeout-sec", type=int, default=45)
    parser.add_argument("--runtime-stack-budget", type=float, default=12.0)
    parser.add_argument("--expect-runtime-api-backend", default="")
    parser.add_argument("--expect-snapshot-telemetry-status", default="ok", choices=["any", "ok", "degraded"])
    parser.add_argument("--expect-plan-telemetry-status", default="ok", choices=["any", "ok", "degraded"])
    parser.add_argument("--expect-bridge-decode-runtime-status", default="ok", choices=["any", "ok", "degraded"])
    parser.add_argument("--expect-bridge-decode-mode", default="mpp-rga")
    parser.add_argument("--max-plan-concurrency-pressure", type=float, default=2.0)
    parser.add_argument("--max-plan-suggested-min-dispatch-ms", type=int, default=3000)
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
    parser.add_argument("--soak-duration-sec", type=int, default=900)
    parser.add_argument("--soak-interval-sec", type=int, default=5)
    parser.add_argument("--soak-max-iterations", type=int, default=0)
    parser.add_argument("--max-memory-used-delta-mb", type=float, default=0.0)
    parser.add_argument("--max-loadavg-1m", type=float, default=0.0)
    parser.add_argument("--quality-iterations", type=int, default=30)
    parser.add_argument("--quality-interval-ms", type=int, default=250)
    parser.add_argument("--quality-timeout-sec", type=float, default=45.0)
    parser.add_argument("--skip-quality-diagnostics", action="store_true")
    parser.add_argument("--verify-alarm-preview", action="store_true")
    parser.add_argument("--alarm-preview-timeout-sec", type=float, default=45.0)
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


def _normalized_soak_iterations(value: int, dry_run: bool) -> int:
    numeric = int(value)
    if numeric > 0:
        return numeric
    if dry_run:
        return 1
    return 999999


def build_phase11_argv(args: argparse.Namespace) -> List[str]:
    effective_source = args.source
    if args.dry_run:
        effective_source = str(args.dry_run_source or "").strip() or "test://frame"
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
        str(int(args.camera_id)),
        "--model-id",
        str(int(args.model_id)),
        "--algorithm-id",
        str(int(args.algorithm_id)),
        "--source",
        effective_source,
        "--timeout-sec",
        str(int(args.timeout_sec)),
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
        args.output_dir,
        "--soak-duration-sec",
        str(int(args.soak_duration_sec)),
        "--soak-interval-sec",
        str(int(args.soak_interval_sec)),
        "--soak-max-iterations",
        str(_normalized_soak_iterations(int(args.soak_max_iterations), bool(args.dry_run))),
        "--max-memory-used-delta-mb",
        str(float(args.max_memory_used_delta_mb)),
        "--max-loadavg-1m",
        str(float(args.max_loadavg_1m)),
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

    if not args.skip_quality_diagnostics:
        argv.append("--verify-quality-diagnostics")
        argv.extend(["--quality-iterations", str(int(args.quality_iterations))])
        argv.extend(["--quality-interval-ms", str(int(args.quality_interval_ms))])
        argv.extend(["--quality-timeout-sec", str(float(args.quality_timeout_sec))])
    if args.verify_alarm_preview:
        argv.append("--verify-alarm-preview")
        argv.extend(["--alarm-preview-timeout-sec", str(float(args.alarm_preview_timeout_sec))])
    if args.dry_run:
        argv.append("--dry-run")
    return argv


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    return int(run_phase11_handoff.main(build_phase11_argv(args)))


if __name__ == "__main__":
    raise SystemExit(main())
