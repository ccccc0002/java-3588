#!/usr/bin/env python3
"""Preset runner for Phase10 acceptance gates on Linux/RK3588."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import List, Optional, Sequence

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import run_linux_gates

DEFAULT_OUTPUT_DIR = "scripts/testing/out/phase10-acceptance"


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Phase10 acceptance gates with runtime stack smoke enabled.")
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
    parser.add_argument("--expect-bridge-decode-runtime-status", default="any", choices=["any", "ok", "degraded"])
    parser.add_argument("--expect-bridge-decode-mode", default="")
    parser.add_argument("--max-plan-concurrency-pressure", type=float, default=0.0)
    parser.add_argument("--max-plan-suggested-min-dispatch-ms", type=int, default=0)
    parser.add_argument("--min-snapshot-ready-stream-count", type=int, default=0)
    parser.add_argument("--min-plan-ready-stream-count", type=int, default=0)
    parser.add_argument("--runtime-stack-retry-attempts", type=int, default=1)
    parser.add_argument("--cookie", default="")
    parser.add_argument("--auth-header-name", default="")
    parser.add_argument("--auth-header-value", default="")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--manage-bridge", action="store_true")
    parser.add_argument("--bridge-bootstrap-token", default="")
    parser.add_argument("--bridge-wait-seconds", type=int, default=20)
    parser.add_argument("--bridge-poll-interval", type=int, default=1)
    parser.add_argument("--include-soak", action="store_true")
    parser.add_argument("--soak-duration-sec", type=int, default=60)
    parser.add_argument("--soak-interval-sec", type=int, default=5)
    parser.add_argument("--soak-max-iterations", type=int, default=1)
    parser.add_argument("--soak-max-failed-steps", type=int, default=0)
    parser.add_argument("--fail-fast", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


def build_linux_gate_argv(args: argparse.Namespace) -> List[str]:
    argv: List[str] = [
        "--base-url", args.base_url,
        "--camera-id", str(args.camera_id),
        "--model-id", str(args.model_id),
        "--algorithm-id", str(args.algorithm_id),
        "--source", args.source,
        "--timeout-sec", str(args.timeout_sec),
        "--output-dir", args.output_dir,
        "--include-runtime-stack-smoke",
        "--runtime-api-url", args.runtime_api_url,
        "--bridge-url", args.bridge_url,
        "--bootstrap-token", args.bootstrap_token,
        "--plugin-id", args.plugin_id,
        "--runtime-stack-budget", str(args.runtime_stack_budget),
        "--expect-runtime-api-backend", args.expect_runtime_api_backend,
        "--expect-snapshot-telemetry-status", args.expect_snapshot_telemetry_status,
        "--expect-plan-telemetry-status", args.expect_plan_telemetry_status,
        "--expect-bridge-decode-runtime-status", args.expect_bridge_decode_runtime_status,
        "--expect-bridge-decode-mode", args.expect_bridge_decode_mode,
        "--max-plan-concurrency-pressure", str(args.max_plan_concurrency_pressure),
        "--max-plan-suggested-min-dispatch-ms", str(args.max_plan_suggested_min_dispatch_ms),
        "--min-snapshot-ready-stream-count", str(args.min_snapshot_ready_stream_count),
        "--min-plan-ready-stream-count", str(args.min_plan_ready_stream_count),
        "--runtime-stack-retry-attempts", str(args.runtime_stack_retry_attempts),
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
    argv.extend(["--bridge-wait-seconds", str(args.bridge_wait_seconds)])
    argv.extend(["--bridge-poll-interval", str(args.bridge_poll_interval)])
    if args.include_soak:
        argv.append("--include-soak")
        argv.extend(["--soak-duration-sec", str(args.soak_duration_sec)])
        argv.extend(["--soak-interval-sec", str(args.soak_interval_sec)])
        argv.extend(["--soak-max-iterations", str(args.soak_max_iterations)])
        argv.extend(["--soak-max-failed-steps", str(args.soak_max_failed_steps)])
    if args.fail_fast:
        argv.append("--fail-fast")
    if args.dry_run:
        argv.append("--dry-run")
    return argv


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    return int(run_linux_gates.main(build_linux_gate_argv(args)))


if __name__ == "__main__":
    raise SystemExit(main())
