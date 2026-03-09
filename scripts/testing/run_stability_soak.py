#!/usr/bin/env python3
"""RK3588-native stability soak runner for stream and inference governance checks."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence, Tuple


STRICT_RESUME_ERROR = "INFER_DL_REPLAY_BATCH_STRICT_RESUME_EXPECTED_TOTAL_REQUIRED"
DEFAULT_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_SOURCE = "test://frame"
BRIDGE_CTL_PATH = Path(__file__).resolve().parent.parent / "rk3588" / "runtime_bridge_ctl.py"


class ValidationError(RuntimeError):
    """Raised when an API response violates the expected contract."""


@dataclass(frozen=True)
class StepResult:
    step: str
    passed: bool
    detail: str
    trace_id: Optional[str] = None
    http_status: Optional[int] = None
    response_code: Optional[int] = None
    extra: Optional[Dict[str, Any]] = None


class ApiClient:
    def __init__(self, base_url: str, timeout_sec: int = 10, cookie: str = "", auth_header_name: str = "", auth_header_value: str = ""):
        self.base_url = base_url.rstrip("/")
        self.timeout_sec = timeout_sec
        self.cookie = cookie
        self.auth_header_name = auth_header_name
        self.auth_header_value = auth_header_value

    def post_json(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        data = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            headers=self._headers("application/json"),
            method="POST",
        )
        return self._send(request)

    def post_form(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        form_pairs = []
        for key, value in payload.items():
            if value is None:
                continue
            form_pairs.append((key, str(value)))
        data = urllib.parse.urlencode(form_pairs).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            headers=self._headers("application/x-www-form-urlencoded"),
            method="POST",
        )
        return self._send(request)

    def _headers(self, content_type: Optional[str] = None) -> Dict[str, str]:
        headers = {"Accept": "application/json"}
        if content_type:
            headers["Content-Type"] = content_type
        if self.cookie:
            headers["Cookie"] = self.cookie
        if self.auth_header_name and self.auth_header_value:
            headers[self.auth_header_name] = self.auth_header_value
        return headers

    def _send(self, request: urllib.request.Request) -> Dict[str, Any]:
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_sec) as response:
                body = response.read().decode("utf-8")
                payload = json.loads(body) if body else {}
                payload.setdefault("_http_status", response.getcode())
                return payload
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            payload: Dict[str, Any]
            try:
                payload = json.loads(body) if body else {}
            except json.JSONDecodeError:
                payload = {"message": body}
            payload.setdefault("_http_status", exc.code)
            return payload


class DryRunClient:
    def __init__(self) -> None:
        self.counter = 0

    def post_json(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        self.counter += 1
        request_trace_id = str(payload.get("trace_id") or f"dryrun-{self.counter}")
        if path == "/api/inference/dispatch":
            report = {
                "status": "accepted",
                "trace_id": request_trace_id,
            }
            idempotent = {
                "status": "accepted",
                "trace_id": request_trace_id,
            }
            result = {
                "trace_id": request_trace_id,
                "latency_ms": 1,
                "detections": [],
            }
            return {
                "code": 0,
                "data": {
                    "trace_id": request_trace_id,
                    "backend_type": "rk3588",
                    "result": result,
                    "report": report,
                    "idempotent": idempotent,
                },
                "_http_status": 200,
            }
        if path == "/api/inference/dead-letter/replay/batch":
            return {
                "code": 1,
                "message": "strict resume expected total selected count required",
                "data": {
                    "trace_id": f"replay-{self.counter}",
                    "error_code": STRICT_RESUME_ERROR,
                },
                "_http_status": 200,
            }
        if path == "/stream/stop":
            return {
                "code": 0,
                "data": {
                    "trace_id": f"stop-{self.counter}",
                },
                "_http_status": 200,
            }
        return {
            "code": 0,
            "data": {
                "trace_id": f"post-{self.counter}",
            },
            "_http_status": 200,
        }

    def post_form(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        self.counter += 1
        if path == "/stream/start":
            return {
                "code": 0,
                "data": {
                    "trace_id": f"start-{self.counter}",
                    "playUrl": "http://127.0.0.1/live/test.flv",
                },
                "_http_status": 200,
            }
        if path == "/stream/stop":
            return {
                "code": 0,
                "data": {
                    "trace_id": f"stop-{self.counter}",
                },
                "_http_status": 200,
            }
        return {
            "code": 0,
            "data": {
                "trace_id": f"form-{self.counter}",
            },
            "_http_status": 200,
        }


class EventRecorder:
    def __init__(self, output_dir: Path):
        self.output_dir = output_dir
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.events_path = self.output_dir / "events.ndjson"
        self.summary_path = self.output_dir / "summary.json"

    def append_event(self, event: Dict[str, Any]) -> None:
        with self.events_path.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(event, ensure_ascii=True) + "\n")

    def write_summary(self, summary: Dict[str, Any]) -> None:
        with self.summary_path.open("w", encoding="utf-8") as handle:
            json.dump(summary, handle, ensure_ascii=True, indent=2)
            handle.write("\n")


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run RK3588-ready stability soak checks.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--model-id", type=int, default=1)
    parser.add_argument("--algorithm-id", type=int, default=1)
    parser.add_argument("--source", default=DEFAULT_SOURCE)
    parser.add_argument("--duration-sec", type=int, default=300)
    parser.add_argument("--interval-sec", type=int, default=5)
    parser.add_argument("--max-iterations", type=int, default=None)
    parser.add_argument("--output-dir", default="scripts/testing/out/stability-soak")
    parser.add_argument("--cookie", default="")
    parser.add_argument("--auth-header-name", default="")
    parser.add_argument("--auth-header-value", default="")
    parser.add_argument("--timeout-sec", type=int, default=10)
    parser.add_argument("--manage-bridge", action="store_true")
    parser.add_argument("--bridge-bootstrap-token", default="")
    parser.add_argument("--bridge-wait-seconds", type=int, default=20)
    parser.add_argument("--bridge-poll-interval", type=int, default=1)
    parser.add_argument("--fail-fast", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    return parser.parse_args(argv)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def get_nested(obj: Any, *keys: str) -> Any:
    current = obj
    for key in keys:
        if not isinstance(current, dict):
            return None
        current = current.get(key)
    return current


def safe_code(payload: Dict[str, Any]) -> Optional[int]:
    value = payload.get("code")
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, int):
        return value
    return None


def build_event(iteration: int, result: StepResult) -> Dict[str, Any]:
    event = {
        "timestamp": utc_now(),
        "iteration": iteration,
        "step": result.step,
        "passed": result.passed,
        "detail": result.detail,
    }
    if result.trace_id:
        event["trace_id"] = result.trace_id
    if result.http_status is not None:
        event["http_status"] = result.http_status
    if result.response_code is not None:
        event["response_code"] = result.response_code
    if result.extra:
        event["extra"] = result.extra
    return event


def validate_stream_start(client: Any, camera_id: int) -> StepResult:
    payload = {"cameraId": camera_id}
    response = client.post_form("/stream/start", payload)
    data = response.get("data") if isinstance(response, dict) else None
    trace_id = get_nested(response, "data", "trace_id")
    play_url = get_nested(response, "data", "playUrl")
    http_status = response.get("_http_status") if isinstance(response, dict) else None
    response_code = safe_code(response) if isinstance(response, dict) else None
    if http_status != 200 or response_code != 0 or not trace_id or not play_url:
        raise ValidationError(
            "stream start validation failed"
            f"; http_status={http_status}; code={response_code}; trace_id={trace_id}; playUrl={play_url}"
        )
    return StepResult(
        step="stream_start",
        passed=True,
        detail=f"http_status={http_status}; code={response_code}; playUrl={play_url}",
        trace_id=str(trace_id),
        http_status=http_status,
        response_code=response_code,
        extra={"playUrl": play_url, "camera_id": camera_id, "data_present": data is not None},
    )


def validate_dispatch(client: Any, camera_id: int, model_id: int, algorithm_id: int, source: str) -> StepResult:
    trace_id = f"soak-{uuid.uuid4().hex}"
    timestamp_ms = int(time.time() * 1000)
    payload = {
        "trace_id": trace_id,
        "camera_id": camera_id,
        "model_id": model_id,
        "algorithm_id": algorithm_id,
        "persist_report": 0,
        "frame": {
            "source": source,
            "timestamp_ms": timestamp_ms,
        },
        "roi": [],
    }
    response = client.post_json("/api/inference/dispatch", payload)
    http_status = response.get("_http_status") if isinstance(response, dict) else None
    response_code = safe_code(response) if isinstance(response, dict) else None
    data_trace_id = get_nested(response, "data", "trace_id")
    backend_type = get_nested(response, "data", "backend_type")
    result_trace_id = get_nested(response, "data", "result", "trace_id")
    report_trace_id = get_nested(response, "data", "report", "trace_id")
    report_status = get_nested(response, "data", "report", "status")
    idempotent_trace_id = get_nested(response, "data", "idempotent", "trace_id")
    idempotent_status = get_nested(response, "data", "idempotent", "status")
    valid = (
        http_status == 200
        and response_code == 0
        and data_trace_id == trace_id
        and result_trace_id == trace_id
        and report_trace_id == trace_id
        and idempotent_trace_id == trace_id
        and bool(report_status)
        and bool(idempotent_status)
        and bool(backend_type)
    )
    if not valid:
        raise ValidationError(
            "dispatch validation failed"
            f"; http_status={http_status}; code={response_code}; data_trace_id={data_trace_id}"
            f"; result_trace_id={result_trace_id}; report_trace_id={report_trace_id}"
            f"; idempotent_trace_id={idempotent_trace_id}; report_status={report_status}"
            f"; idempotent_status={idempotent_status}; backend_type={backend_type}"
        )
    return StepResult(
        step="dispatch",
        passed=True,
        detail=(
            f"http_status={http_status}; code={response_code}; backend_type={backend_type}; "
            f"report_status={report_status}; idempotent_status={idempotent_status}"
        ),
        trace_id=trace_id,
        http_status=http_status,
        response_code=response_code,
        extra={
            "backend_type": backend_type,
            "report_status": report_status,
            "idempotent_status": idempotent_status,
            "camera_id": camera_id,
        },
    )


def validate_replay_governance(client: Any) -> StepResult:
    payload = {
        "dry_run": 1,
        "limit": 2,
        "strict_resume": 1,
    }
    response = client.post_json("/api/inference/dead-letter/replay/batch", payload)
    http_status = response.get("_http_status") if isinstance(response, dict) else None
    response_code = safe_code(response) if isinstance(response, dict) else None
    trace_id = get_nested(response, "data", "trace_id")
    error_code = get_nested(response, "data", "error_code")
    if http_status != 200 or response_code == 0 or error_code != STRICT_RESUME_ERROR:
        raise ValidationError(
            "replay governance validation failed"
            f"; http_status={http_status}; code={response_code}; trace_id={trace_id}; error_code={error_code}"
        )
    return StepResult(
        step="dead_letter_replay_batch",
        passed=True,
        detail=f"http_status={http_status}; code={response_code}; error_code={error_code}",
        trace_id=str(trace_id) if trace_id else None,
        http_status=http_status,
        response_code=response_code,
        extra={"error_code": error_code},
    )


def validate_stream_stop(client: Any, camera_id: int) -> StepResult:
    payload = {"cameraId": camera_id}
    response = client.post_form("/stream/stop", payload)
    http_status = response.get("_http_status") if isinstance(response, dict) else None
    response_code = safe_code(response) if isinstance(response, dict) else None
    trace_id = get_nested(response, "data", "trace_id")
    if http_status != 200 or response_code != 0 or not trace_id:
        raise ValidationError(
            "stream stop validation failed"
            f"; http_status={http_status}; code={response_code}; trace_id={trace_id}"
        )
    return StepResult(
        step="stream_stop",
        passed=True,
        detail=f"http_status={http_status}; code={response_code}",
        trace_id=str(trace_id),
        http_status=http_status,
        response_code=response_code,
        extra={"camera_id": camera_id},
    )


def run_cycle(
    client: Any,
    camera_id: int,
    model_id: int,
    algorithm_id: int,
    source: str,
) -> List[StepResult]:
    results = [validate_stream_start(client, camera_id)]
    results.append(validate_dispatch(client, camera_id, model_id, algorithm_id, source))
    results.append(validate_replay_governance(client))
    results.append(validate_stream_stop(client, camera_id))
    return results


def record_failure(
    recorder: EventRecorder,
    iteration: int,
    step: str,
    exc: BaseException,
) -> StepResult:
    detail = f"{type(exc).__name__}: {exc}"
    failed = StepResult(step=step, passed=False, detail=detail)
    recorder.append_event(build_event(iteration, failed))
    return failed



def default_bridge_runner(command: str, bridge_args: List[str]) -> Dict[str, Any]:
    completed = subprocess.run([sys.executable, str(BRIDGE_CTL_PATH), command, *bridge_args], capture_output=True, text=True)
    payload: Dict[str, Any] = {}
    stdout = completed.stdout or ''
    lines = [line.strip() for line in stdout.splitlines() if line.strip()]
    if lines:
        try:
            payload = json.loads(lines[-1])
        except json.JSONDecodeError:
            payload = {'status': 'invalid_output', 'raw': lines[-1]}
    return {
        'exit_code': completed.returncode,
        'stdout': stdout,
        'stderr': completed.stderr,
        'payload': payload,
    }


def build_bridge_args(args: argparse.Namespace) -> List[str]:
    bridge_args = [
        '--wait-seconds', str(args.bridge_wait_seconds),
        '--poll-interval', str(args.bridge_poll_interval),
    ]
    if args.bridge_bootstrap_token:
        bridge_args.extend(['--env', f'RUNTIME_BOOTSTRAP_TOKEN={args.bridge_bootstrap_token}'])
    return bridge_args


def bridge_payload_summary(run_result: Optional[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    if not isinstance(run_result, dict):
        return None
    payload = run_result.get('payload') if isinstance(run_result.get('payload'), dict) else {}
    if not payload:
        return None
    return dict(payload)


def build_summary(
    args: argparse.Namespace,
    started_at: str,
    finished_at: str,
    iterations_completed: int,
    total_events: int,
    failed_steps: int,
    status: str,
    last_error: Optional[str],
) -> Dict[str, Any]:
    summary = {
        "started_at": started_at,
        "finished_at": finished_at,
        "base_url": args.base_url,
        "camera_id": args.camera_id,
        "model_id": args.model_id,
        "algorithm_id": args.algorithm_id,
        "duration_sec": args.duration_sec,
        "interval_sec": args.interval_sec,
        "max_iterations": args.max_iterations,
        "dry_run": args.dry_run,
        "manage_bridge": args.manage_bridge,
        "fail_fast": args.fail_fast,
        "iterations_completed": iterations_completed,
        "total_events": total_events,
        "failed_steps": failed_steps,
        "status": status,
        "last_error": last_error,
    }
    return summary


def main(argv: Optional[Sequence[str]] = None, client: Optional[Any] = None, bridge_runner: Optional[Any] = None) -> int:
    args = parse_args(argv)
    recorder = EventRecorder(Path(args.output_dir))
    started_at = utc_now()
    deadline = time.time() + max(args.duration_sec, 0)
    client_impl = client if client is not None else (
        DryRunClient() if args.dry_run else ApiClient(args.base_url, args.timeout_sec, args.cookie, args.auth_header_name, args.auth_header_value)
    )
    bridge_exec = bridge_runner or default_bridge_runner
    bridge_start_result = None
    bridge_stop_result = None
    should_stop_bridge = False
    if args.manage_bridge:
        bridge_start_result = bridge_exec('start', build_bridge_args(args))
        bridge_payload = bridge_payload_summary(bridge_start_result) or {}
        bridge_status = str(bridge_payload.get('status', '')).strip()
        should_stop_bridge = bridge_status == 'started'
        if int(bridge_start_result.get('exit_code', 1)) != 0 or bridge_status not in {'started', 'already_running', 'running'}:
            finished_at = utc_now()
            summary = build_summary(
                args=args,
                started_at=started_at,
                finished_at=finished_at,
                iterations_completed=0,
                total_events=0,
                failed_steps=1,
                status='failed',
                last_error='bridge start failed',
            )
            bridge_payload = bridge_payload_summary(bridge_start_result)
            if bridge_payload is not None:
                summary['bridge_start'] = bridge_payload
            recorder.write_summary(summary)
            print(json.dumps(summary, ensure_ascii=True, indent=2))
            return 1

    iterations_completed = 0
    total_events = 0
    failed_steps = 0
    last_error: Optional[str] = None
    status = "passed"
    iteration = 0

    while True:
        if args.max_iterations is not None and iteration >= args.max_iterations:
            break
        if iteration > 0 and time.time() >= deadline:
            break
        iteration += 1

        try:
            results = run_cycle(client_impl, args.camera_id, args.model_id, args.algorithm_id, args.source)
            for result in results:
                recorder.append_event(build_event(iteration, result))
                total_events += 1
            iterations_completed += 1
        except Exception as exc:  # noqa: BLE001
            failed_steps += 1
            status = "failed"
            last_error = f"{type(exc).__name__}: {exc}"
            if isinstance(exc, KeyboardInterrupt):
                last_error = "KeyboardInterrupt"
            if isinstance(exc, ValidationError):
                failing_step = infer_step_name(str(exc))
            else:
                failing_step = "unexpected_error"
            record_failure(recorder, iteration, failing_step, exc)
            total_events += 1

            if args.fail_fast:
                break
        if args.interval_sec > 0 and time.time() < deadline:
            time.sleep(args.interval_sec)
        if time.time() >= deadline:
            break

    if args.manage_bridge and should_stop_bridge:
        bridge_stop_result = bridge_exec('stop', build_bridge_args(args))

    finished_at = utc_now()
    if iterations_completed == 0 and failed_steps > 0:
        status = "failed"
    elif failed_steps > 0:
        status = "completed_with_failures"
    summary = build_summary(
        args=args,
        started_at=started_at,
        finished_at=finished_at,
        iterations_completed=iterations_completed,
        total_events=total_events,
        failed_steps=failed_steps,
        status=status,
        last_error=last_error,
    )
    bridge_start_payload = bridge_payload_summary(bridge_start_result)
    if bridge_start_payload is not None:
        summary['bridge_start'] = bridge_start_payload
    bridge_stop_payload = bridge_payload_summary(bridge_stop_result)
    if bridge_stop_payload is not None:
        summary['bridge_stop'] = bridge_stop_payload
    recorder.write_summary(summary)

    print(json.dumps(summary, ensure_ascii=True, indent=2))
    return 0 if failed_steps == 0 else 1


def infer_step_name(detail: str) -> str:
    lowered = detail.lower()
    if "stream start" in lowered:
        return "stream_start"
    if "dispatch" in lowered:
        return "dispatch"
    if "replay governance" in lowered or "replay batch" in lowered:
        return "dead_letter_replay_batch"
    if "stream stop" in lowered:
        return "stream_stop"
    return "validation_error"


if __name__ == "__main__":
    sys.exit(main())
