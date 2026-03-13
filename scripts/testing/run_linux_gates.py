#!/usr/bin/env python3
"""Unified Linux gate runner for RK3588 validation assets."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Sequence

DEFAULT_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_OUTPUT_DIR = "scripts/testing/out/linux-gates"
SCRIPT_DIR = Path(__file__).resolve().parent
BRIDGE_CTL_PATH = SCRIPT_DIR.parent / 'rk3588' / 'runtime_bridge_ctl.py'
RUNTIME_STACK_SMOKE_PATH = SCRIPT_DIR.parent / 'rk3588' / 'runtime_stack_smoke.py'


@dataclass(frozen=True)
class StageResult:
    stage: str
    passed: bool
    detail: str
    exit_code: int
    summary: Dict[str, Any]


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run Linux-native RK3588 validation gates.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--model-id", type=int, default=1)
    parser.add_argument("--algorithm-id", type=int, default=1)
    parser.add_argument("--primary-camera-id", type=int, default=1)
    parser.add_argument("--secondary-camera-id", type=int, default=2)
    parser.add_argument("--expected-backend-type", default="")
    parser.add_argument("--expected-override-source", default="")
    parser.add_argument("--source", default="test://frame")
    parser.add_argument("--cookie", default="")
    parser.add_argument("--auth-header-name", default="")
    parser.add_argument("--auth-header-value", default="")
    parser.add_argument("--timeout-sec", type=int, default=10)
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--include-runtime-stack-smoke", action="store_true")
    parser.add_argument("--runtime-api-url", default="http://127.0.0.1:18081")
    parser.add_argument("--bridge-url", default="http://127.0.0.1:19080")
    parser.add_argument("--bootstrap-token", default="edge-demo-bootstrap")
    parser.add_argument("--plugin-id", default="yolov8n")
    parser.add_argument("--runtime-stack-budget", type=float, default=10.0)
    parser.add_argument("--expect-runtime-api-backend", default="")
    parser.add_argument("--expect-snapshot-telemetry-status", default="any", choices=["any", "ok", "degraded"])
    parser.add_argument("--expect-plan-telemetry-status", default="any", choices=["any", "ok", "degraded"])
    parser.add_argument("--max-plan-concurrency-pressure", type=float, default=0.0)
    parser.add_argument("--max-plan-suggested-min-dispatch-ms", type=int, default=0)
    parser.add_argument("--min-snapshot-ready-stream-count", type=int, default=0)
    parser.add_argument("--min-plan-ready-stream-count", type=int, default=0)
    parser.add_argument("--include-soak", action="store_true")
    parser.add_argument("--soak-duration-sec", type=int, default=60)
    parser.add_argument("--soak-interval-sec", type=int, default=5)
    parser.add_argument("--soak-max-iterations", type=int, default=1)
    parser.add_argument("--manage-bridge", action="store_true")
    parser.add_argument("--bridge-bootstrap-token", default="")
    parser.add_argument("--bridge-wait-seconds", type=int, default=20)
    parser.add_argument("--bridge-poll-interval", type=int, default=1)
    parser.add_argument("--fail-fast", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def default_command_runner(stage_name: str, command: List[str], summary_path: str) -> Dict[str, Any]:
    completed = subprocess.run(command, capture_output=True, text=True)
    return {
        "exit_code": completed.returncode,
        "stdout": completed.stdout,
        "stderr": completed.stderr,
        "summary_path": summary_path,
        "stage_name": stage_name,
    }


def parse_last_json_object(stdout_text: str) -> Optional[Dict[str, Any]]:
    lines = [line.strip() for line in str(stdout_text or "").splitlines() if line.strip()]
    for line in reversed(lines):
        try:
            payload = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(payload, dict):
            return payload
    return None


def default_bridge_runner(command: str, bridge_args: List[str]) -> Dict[str, Any]:
    completed = subprocess.run([sys.executable, str(BRIDGE_CTL_PATH), command, *bridge_args], capture_output=True, text=True)
    payload: Dict[str, Any] = {}
    stdout = completed.stdout or ""
    lines = [line.strip() for line in stdout.splitlines() if line.strip()]
    if lines:
        try:
            payload = json.loads(lines[-1])
        except json.JSONDecodeError:
            payload = {"status": "invalid_output", "raw": lines[-1]}
    return {
        "exit_code": completed.returncode,
        "stdout": stdout,
        "stderr": completed.stderr,
        "payload": payload,
    }


def build_bridge_args(args: argparse.Namespace) -> List[str]:
    bridge_args = [
        '--wait-seconds', str(args.bridge_wait_seconds),
        '--poll-interval', str(args.bridge_poll_interval),
    ]
    if args.bridge_bootstrap_token:
        bridge_args.extend(['--env', f'RUNTIME_BOOTSTRAP_TOKEN={args.bridge_bootstrap_token}'])
    return bridge_args


def build_stage_definitions(args: argparse.Namespace, root_output: Path) -> List[Dict[str, Any]]:
    python_bin = sys.executable or "python3"
    stages = [
        {
            "name": "inference_contracts",
            "script": SCRIPT_DIR / "validate_inference_contracts.py",
            "output_dir": root_output / "inference-contracts",
            "args": [
                "--base-url", args.base_url,
                "--camera-id", str(args.camera_id),
                "--model-id", str(args.model_id),
                "--algorithm-id", str(args.algorithm_id),
                "--source", args.source,
                "--expected-backend-type", args.expected_backend_type,
                "--expected-override-source", args.expected_override_source,
                "--timeout-sec", str(args.timeout_sec),
                "--cookie", args.cookie,
                "--auth-header-name", args.auth_header_name,
                "--auth-header-value", args.auth_header_value,
            ],
        },
        {
            "name": "trace_governance",
            "script": SCRIPT_DIR / "validate_trace_governance_flow.py",
            "output_dir": root_output / "trace-governance",
            "args": [
                "--base-url", args.base_url,
                "--camera-id", str(args.camera_id),
                "--model-id", str(args.model_id),
                "--algorithm-id", str(args.algorithm_id),
                "--source", args.source,
                "--timeout-sec", str(args.timeout_sec),
                "--cookie", args.cookie,
                "--auth-header-name", args.auth_header_name,
                "--auth-header-value", args.auth_header_value,
            ],
        },
        {
            "name": "rollout_readiness",
            "script": SCRIPT_DIR / "validate_rollout_readiness.py",
            "output_dir": root_output / "rollout-readiness",
            "args": [
                "--base-url", args.base_url,
                "--primary-camera-id", str(args.primary_camera_id),
                "--secondary-camera-id", str(args.secondary_camera_id),
                "--expected-backend-type", args.expected_backend_type,
                "--expected-override-source", args.expected_override_source,
                "--timeout-sec", str(args.timeout_sec),
                "--cookie", args.cookie,
                "--auth-header-name", args.auth_header_name,
                "--auth-header-value", args.auth_header_value,
            ],
        },
    ]
    if args.include_soak:
        stages.append(
            {
                "name": "stability_soak",
                "script": SCRIPT_DIR / "run_stability_soak.py",
                "output_dir": root_output / "stability-soak",
                "args": [
                    "--base-url", args.base_url,
                    "--camera-id", str(args.camera_id),
                    "--model-id", str(args.model_id),
                    "--algorithm-id", str(args.algorithm_id),
                    "--source", args.source,
                    "--timeout-sec", str(args.timeout_sec),
                    "--duration-sec", str(args.soak_duration_sec),
                    "--interval-sec", str(args.soak_interval_sec),
                    "--max-iterations", str(args.soak_max_iterations),
                    "--cookie", args.cookie,
                    "--auth-header-name", args.auth_header_name,
                    "--auth-header-value", args.auth_header_value,
                ],
            }
        )
    if args.include_runtime_stack_smoke:
        stages.append(
            {
                "name": "runtime_stack_smoke",
                "script": RUNTIME_STACK_SMOKE_PATH,
                "output_dir": root_output / "runtime-stack-smoke",
                "append_output_dir": False,
                "append_control_flags": False,
                "args": [
                    "--runtime-api-url", args.runtime_api_url,
                    "--bridge-url", args.bridge_url,
                    "--bootstrap-token", args.bootstrap_token,
                    "--plugin-id", args.plugin_id,
                    "--source", args.source,
                    "--camera-id", str(args.camera_id),
                    "--model-id", str(args.model_id),
                    "--budget", str(float(args.runtime_stack_budget)),
                    "--timeout-sec", str(args.timeout_sec),
                    "--expect-runtime-api-backend", args.expect_runtime_api_backend,
                    "--expect-snapshot-telemetry-status", args.expect_snapshot_telemetry_status,
                    "--expect-plan-telemetry-status", args.expect_plan_telemetry_status,
                    "--max-plan-concurrency-pressure", str(args.max_plan_concurrency_pressure),
                    "--max-plan-suggested-min-dispatch-ms", str(args.max_plan_suggested_min_dispatch_ms),
                    "--min-snapshot-ready-stream-count", str(args.min_snapshot_ready_stream_count),
                    "--min-plan-ready-stream-count", str(args.min_plan_ready_stream_count),
                ],
            }
        )
    for stage in stages:
        command = [python_bin, str(stage["script"]), *stage["args"]]
        if stage.get("append_output_dir", True):
            command.extend(["--output-dir", str(stage["output_dir"])])
        if stage.get("append_control_flags", True):
            if args.dry_run:
                command.append("--dry-run")
            if args.fail_fast:
                command.append("--fail-fast")
        stage["command"] = command
    return stages


def read_stage_summary(
    summary_path: Path,
    exit_code: int,
    stage_name: str = "",
    stdout_text: str = "",
) -> Dict[str, Any]:
    if summary_path.exists():
        result = json.loads(summary_path.read_text(encoding="utf-8"))
        if isinstance(result, dict):
            return result
    if stage_name == "runtime_stack_smoke":
        parsed = parse_last_json_object(stdout_text)
        if parsed is not None:
            return {
                "status": "failed" if exit_code != 0 else "passed",
                "source": "stdout_json",
                "payload": parsed,
            }
    return {"status": "failed" if exit_code != 0 else "passed", "missing_summary": True}


def event_from_stage(result: StageResult) -> Dict[str, Any]:
    return {
        "timestamp": utc_now(),
        "stage": result.stage,
        "passed": result.passed,
        "detail": result.detail,
        "exit_code": result.exit_code,
        "status": result.summary.get("status"),
    }


def bridge_payload_summary(run_result: Optional[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    if not isinstance(run_result, dict):
        return None
    payload = run_result.get('payload') if isinstance(run_result.get('payload'), dict) else {}
    if not payload:
        return None
    return dict(payload)


def summary_from_results(
    args: argparse.Namespace,
    started_at: str,
    finished_at: str,
    results: List[StageResult],
    bridge_start: Optional[Dict[str, Any]] = None,
    bridge_stop: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    passed_stages = len([item for item in results if item.passed])
    failed_stages = len(results) - passed_stages
    summary = {
        "started_at": started_at,
        "finished_at": finished_at,
        "base_url": args.base_url,
        "camera_id": args.camera_id,
        "model_id": args.model_id,
        "algorithm_id": args.algorithm_id,
        "primary_camera_id": args.primary_camera_id,
        "secondary_camera_id": args.secondary_camera_id,
        "expected_backend_type": args.expected_backend_type,
        "expected_override_source": args.expected_override_source,
        "cookie_present": bool(args.cookie),
        "auth_header_name": args.auth_header_name,
        "auth_header_present": bool(args.auth_header_value),
        "include_soak": args.include_soak,
        "include_runtime_stack_smoke": args.include_runtime_stack_smoke,
        "runtime_api_url": args.runtime_api_url,
        "bridge_url": args.bridge_url,
        "manage_bridge": args.manage_bridge,
        "dry_run": args.dry_run,
        "fail_fast": args.fail_fast,
        "executed_stages": len(results),
        "passed_stages": passed_stages,
        "failed_stages": failed_stages,
        "status": "passed" if failed_stages == 0 else "failed",
    }
    bridge_start_payload = bridge_payload_summary(bridge_start)
    if bridge_start_payload is not None:
        summary['bridge_start'] = bridge_start_payload
    bridge_stop_payload = bridge_payload_summary(bridge_stop)
    if bridge_stop_payload is not None:
        summary['bridge_stop'] = bridge_stop_payload
    return summary


def main(
    argv: Optional[Sequence[str]] = None,
    command_runner: Optional[Callable[[str, List[str], str], Dict[str, Any]]] = None,
    bridge_runner: Optional[Callable[[str, List[str]], Dict[str, Any]]] = None,
) -> int:
    args = parse_args(argv)
    root_output = Path(args.output_dir)
    root_output.mkdir(parents=True, exist_ok=True)
    events_path = root_output / "events.ndjson"
    summary_path = root_output / "summary.json"
    started_at = utc_now()
    runner = command_runner or default_command_runner
    bridge_exec = bridge_runner or default_bridge_runner
    results: List[StageResult] = []
    bridge_start_result: Optional[Dict[str, Any]] = None
    bridge_stop_result: Optional[Dict[str, Any]] = None
    should_stop_bridge = False

    try:
        if args.manage_bridge:
            bridge_start_result = bridge_exec('start', build_bridge_args(args))
            bridge_payload = bridge_payload_summary(bridge_start_result) or {}
            bridge_status = str(bridge_payload.get('status', '')).strip()
            should_stop_bridge = bridge_status == 'started'
            if int(bridge_start_result.get('exit_code', 1)) != 0 or bridge_status not in {'started', 'already_running', 'running'}:
                finished_at = utc_now()
                final_summary = summary_from_results(args, started_at, finished_at, results, bridge_start_result, None)
                final_summary['status'] = 'failed'
                summary_path.write_text(json.dumps(final_summary, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
                print(json.dumps(final_summary, ensure_ascii=True, indent=2))
                return 1

        for stage in build_stage_definitions(args, root_output):
            stage_output_dir = Path(stage["output_dir"])
            stage_output_dir.mkdir(parents=True, exist_ok=True)
            child_summary_path = stage_output_dir / "summary.json"
            run_result = runner(stage["name"], stage["command"], str(child_summary_path))
            child_summary = read_stage_summary(
                child_summary_path,
                int(run_result.get("exit_code", 1)),
                stage_name=stage["name"],
                stdout_text=str(run_result.get("stdout", "")),
            )
            passed = int(run_result.get("exit_code", 1)) == 0 and str(child_summary.get("status")) == "passed"
            detail = f"status={child_summary.get('status')}; exit_code={run_result.get('exit_code', 1)}; summary_path={child_summary_path}"
            stage_result = StageResult(stage=stage["name"], passed=passed, detail=detail, exit_code=int(run_result.get("exit_code", 1)), summary=child_summary)
            results.append(stage_result)
            with events_path.open("a", encoding="utf-8") as handle:
                handle.write(json.dumps(event_from_stage(stage_result), ensure_ascii=True) + "\n")
            print((run_result.get("stdout") or "").rstrip())
            stderr = (run_result.get("stderr") or "").rstrip()
            if stderr:
                print(stderr, file=sys.stderr)
            if not passed and args.fail_fast:
                break
    finally:
        if args.manage_bridge and should_stop_bridge:
            bridge_stop_result = bridge_exec('stop', build_bridge_args(args))

    finished_at = utc_now()
    final_summary = summary_from_results(args, started_at, finished_at, results, bridge_start_result, bridge_stop_result)
    summary_path.write_text(json.dumps(final_summary, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(final_summary, ensure_ascii=True, indent=2))
    return 0 if final_summary["failed_stages"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
