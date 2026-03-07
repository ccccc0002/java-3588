#!/usr/bin/env python3
"""Linux-native trace and governance flow validator."""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence

REPLAY_NOT_FOUND_ERROR = "INFER_DL_REPLAY_NOT_FOUND"
STRICT_REQUIRED_ERROR = "INFER_DL_REPLAY_BATCH_STRICT_RESUME_EXPECTED_TOTAL_REQUIRED"
DEFAULT_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_SOURCE = "test://frame"
DEFAULT_OUTPUT_DIR = "scripts/testing/out/trace-governance"


@dataclass(frozen=True)
class CheckResult:
    api: str
    passed: bool
    detail: str
    trace_id: Optional[str] = None
    http_status: Optional[int] = None
    response_code: Optional[int] = None


class ApiClient:
    def __init__(self, base_url: str, timeout_sec: int = 10):
        self.base_url = base_url.rstrip("/")
        self.timeout_sec = timeout_sec

    def get(self, path: str) -> Dict[str, Any]:
        request = urllib.request.Request(self.base_url + path, headers={"Accept": "application/json"}, method="GET")
        return self._send(request)

    def post_json(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        request = urllib.request.Request(
            self.base_url + path,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json", "Accept": "application/json"},
            method="POST",
        )
        return self._send(request)

    def post_form(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        request = urllib.request.Request(
            self.base_url + path,
            data=urllib.parse.urlencode([(k, str(v)) for k, v in payload.items() if v is not None]).encode("utf-8"),
            headers={"Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json"},
            method="POST",
        )
        return self._send(request)

    def _send(self, request: urllib.request.Request) -> Dict[str, Any]:
        try:
            with urllib.request.urlopen(request, timeout=self.timeout_sec) as response:
                body = response.read().decode("utf-8")
                payload = json.loads(body) if body else {}
                payload.setdefault("_http_status", response.getcode())
                return payload
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            try:
                payload = json.loads(body) if body else {}
            except json.JSONDecodeError:
                payload = {"message": body}
            payload.setdefault("_http_status", exc.code)
            return payload


class DryRunClient:
    def __init__(self, source: str):
        self.source = source
        self.counter = 0

    def _trace(self, prefix: str) -> str:
        self.counter += 1
        return f"{prefix}-{self.counter}"

    def get(self, path: str) -> Dict[str, Any]:
        if path == "/api/inference/dead-letter/replay?dead_letter_id=-1":
            return {
                "code": 1,
                "data": {"trace_id": self._trace("replay-not-found"), "error_code": REPLAY_NOT_FOUND_ERROR},
                "_http_status": 200,
            }
        raise RuntimeError(f"unsupported dry-run GET path: {path}")

    def post_json(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        if path == "/api/inference/dispatch":
            trace_id = str(payload.get("trace_id"))
            return {
                "code": 0,
                "data": {
                    "trace_id": trace_id,
                    "result": {"trace_id": trace_id},
                    "report": {"trace_id": trace_id, "status": "accepted"},
                    "idempotent": {"trace_id": trace_id, "status": "accepted"},
                    "request": {"frame": {"source": self.source}},
                },
                "_http_status": 200,
            }
        if path == "/api/inference/dead-letter/replay/batch?limit=2":
            return {
                "code": 1,
                "data": {
                    "trace_id": self._trace("strict-required"),
                    "strict_resume": True,
                    "actual_total_selected_count": 2,
                    "error_code": STRICT_REQUIRED_ERROR,
                },
                "_http_status": 200,
            }
        raise RuntimeError(f"unsupported dry-run POST path: {path}")

    def post_form(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        if path == "/stream/start":
            return {
                "code": 0,
                "data": {"trace_id": self._trace("stream-start"), "playUrl": "http://127.0.0.1/live/test.flv"},
                "_http_status": 200,
            }
        if path == "/stream/stop":
            return {
                "code": 0,
                "data": {"trace_id": self._trace("stream-stop")},
                "_http_status": 200,
            }
        raise RuntimeError(f"unsupported dry-run form path: {path}")


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
    parser = argparse.ArgumentParser(description="Validate trace and governance flow without PowerShell.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--model-id", type=int, default=1)
    parser.add_argument("--algorithm-id", type=int, default=1)
    parser.add_argument("--video-port", type=int, default=0)
    parser.add_argument("--source", default=DEFAULT_SOURCE)
    parser.add_argument("--timeout-sec", type=int, default=10)
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
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


def as_bool(value: Any) -> Optional[bool]:
    return value if isinstance(value, bool) else None


def as_int(value: Any) -> Optional[int]:
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, int):
        return value
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def build_result(api: str, passed: bool, detail: str, payload: Dict[str, Any]) -> CheckResult:
    return CheckResult(
        api=api,
        passed=passed,
        detail=detail,
        trace_id=get_nested(payload, "data", "trace_id"),
        http_status=payload.get("_http_status") if isinstance(payload, dict) else None,
        response_code=as_int(payload.get("code")) if isinstance(payload, dict) else None,
    )


def check_stream_start(client: Any, args: argparse.Namespace) -> CheckResult:
    form = {"cameraId": args.camera_id}
    if args.video_port > 0:
        form["videoPort"] = args.video_port
    payload = client.post_form("/stream/start", form)
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and bool(get_nested(payload, "data", "playUrl"))
    return build_result("/stream/start(trace-flow)", passed, f"code={payload.get('code')}; trace_id={get_nested(payload, 'data', 'trace_id')}; playUrl={get_nested(payload, 'data', 'playUrl')}", payload)


def check_dispatch(client: Any, args: argparse.Namespace) -> CheckResult:
    trace_seed = f"trace-flow-{uuid.uuid4().hex}"
    payload = client.post_json(
        "/api/inference/dispatch",
        {
            "trace_id": trace_seed,
            "camera_id": args.camera_id,
            "model_id": args.model_id,
            "algorithm_id": args.algorithm_id,
            "persist_report": 0,
            "frame": {"source": args.source, "timestamp_ms": int(time.time() * 1000)},
            "roi": [],
        },
    )
    consistent = (
        get_nested(payload, "data", "trace_id") == trace_seed
        and get_nested(payload, "data", "result", "trace_id") == trace_seed
        and get_nested(payload, "data", "report", "trace_id") == trace_seed
        and get_nested(payload, "data", "idempotent", "trace_id") == trace_seed
    )
    passed = (
        as_int(payload.get("code")) == 0
        and bool(get_nested(payload, "data", "trace_id"))
        and bool(get_nested(payload, "data", "report", "status"))
        and bool(get_nested(payload, "data", "idempotent", "status"))
        and get_nested(payload, "data", "request", "frame", "source") == args.source
        and consistent
    )
    return build_result(
        "/api/inference/dispatch(trace-flow)",
        passed,
        f"code={payload.get('code')}; trace_id={get_nested(payload, 'data', 'trace_id')}; result_trace_id={get_nested(payload, 'data', 'result', 'trace_id')}; report_trace_id={get_nested(payload, 'data', 'report', 'trace_id')}; idempotent_trace_id={get_nested(payload, 'data', 'idempotent', 'trace_id')}; report_status={get_nested(payload, 'data', 'report', 'status')}; idempotent_status={get_nested(payload, 'data', 'idempotent', 'status')}; request_source={get_nested(payload, 'data', 'request', 'frame', 'source')}; consistent={consistent}",
        payload,
    )


def check_replay_not_found(client: Any) -> CheckResult:
    payload = client.get("/api/inference/dead-letter/replay?dead_letter_id=-1")
    passed = as_int(payload.get("code")) not in (None, 0) and bool(get_nested(payload, "data", "trace_id")) and get_nested(payload, "data", "error_code") == REPLAY_NOT_FOUND_ERROR
    return build_result("/api/inference/dead-letter/replay(trace-governance)", passed, f"code={payload.get('code')}; trace_id={get_nested(payload, 'data', 'trace_id')}; error_code={get_nested(payload, 'data', 'error_code')}", payload)


def check_strict_resume(client: Any) -> CheckResult:
    payload = client.post_json("/api/inference/dead-letter/replay/batch?limit=2", {"strict_resume": 1, "dead_letter_ids": [901, 902], "dry_run": 1})
    passed = (
        as_int(payload.get("code")) not in (None, 0)
        and bool(get_nested(payload, "data", "trace_id"))
        and as_bool(get_nested(payload, "data", "strict_resume")) is True
        and as_int(get_nested(payload, "data", "actual_total_selected_count")) == 2
        and get_nested(payload, "data", "error_code") == STRICT_REQUIRED_ERROR
    )
    return build_result("/api/inference/dead-letter/replay/batch(trace-governance)", passed, f"code={payload.get('code')}; trace_id={get_nested(payload, 'data', 'trace_id')}; strict_resume={get_nested(payload, 'data', 'strict_resume')}; actual_total={get_nested(payload, 'data', 'actual_total_selected_count')}; error_code={get_nested(payload, 'data', 'error_code')}", payload)


def check_stream_stop(client: Any, args: argparse.Namespace) -> CheckResult:
    payload = client.post_form("/stream/stop", {"cameraId": args.camera_id})
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id"))
    return build_result("/stream/stop(trace-flow)", passed, f"code={payload.get('code')}; trace_id={get_nested(payload, 'data', 'trace_id')}", payload)


def build_check_queue(client: Any, args: argparse.Namespace):
    return [
        lambda: check_stream_start(client, args),
        lambda: check_dispatch(client, args),
        lambda: check_replay_not_found(client),
        lambda: check_strict_resume(client),
        lambda: check_stream_stop(client, args),
    ]


def event_from_result(result: CheckResult) -> Dict[str, Any]:
    event = {"timestamp": utc_now(), "api": result.api, "passed": result.passed, "detail": result.detail}
    if result.trace_id:
        event["trace_id"] = result.trace_id
    if result.http_status is not None:
        event["http_status"] = result.http_status
    if result.response_code is not None:
        event["response_code"] = result.response_code
    return event


def summary_from_results(args: argparse.Namespace, started_at: str, finished_at: str, results: List[CheckResult], status: str, last_error: Optional[str]) -> Dict[str, Any]:
    passed_checks = len([item for item in results if item.passed])
    failed_checks = len(results) - passed_checks
    return {
        "started_at": started_at,
        "finished_at": finished_at,
        "base_url": args.base_url,
        "camera_id": args.camera_id,
        "model_id": args.model_id,
        "algorithm_id": args.algorithm_id,
        "video_port": args.video_port,
        "dry_run": args.dry_run,
        "fail_fast": args.fail_fast,
        "total_checks": len(results),
        "passed_checks": passed_checks,
        "failed_checks": failed_checks,
        "status": status,
        "last_error": last_error,
    }


def main(argv: Optional[Sequence[str]] = None, client: Optional[Any] = None) -> int:
    args = parse_args(argv)
    recorder = EventRecorder(Path(args.output_dir))
    started_at = utc_now()
    client_impl = client if client is not None else (DryRunClient(args.source) if args.dry_run else ApiClient(args.base_url, args.timeout_sec))
    results: List[CheckResult] = []
    last_error: Optional[str] = None
    status = "passed"

    for check_factory in build_check_queue(client_impl, args):
        try:
            checker = check_factory()
        except Exception as exc:
            checker = CheckResult(api="unexpected_error", passed=False, detail=f"{type(exc).__name__}: {exc}")
        results.append(checker)
        recorder.append_event(event_from_result(checker))
        if not checker.passed:
            status = "failed"
            last_error = checker.detail
            if args.fail_fast:
                break

    finished_at = utc_now()
    summary = summary_from_results(args, started_at, finished_at, results, status, last_error)
    recorder.write_summary(summary)

    for result in results:
        label = "PASS" if result.passed else "FAIL"
        print(f"{label}: {result.api} :: {result.detail}")
    print(json.dumps(summary, ensure_ascii=True, indent=2))
    return 0 if summary["failed_checks"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
