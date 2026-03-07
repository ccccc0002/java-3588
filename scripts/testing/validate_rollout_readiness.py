#!/usr/bin/env python3
"""Linux-native rollout readiness validator."""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional, Sequence

DEFAULT_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_OUTPUT_DIR = "scripts/testing/out/rollout-readiness"
DEFAULT_BACKEND = "rk3588_rknn"
DEFAULT_OVERRIDE_SOURCE = "camera_override"


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
    def __init__(self, primary_camera_id: int, secondary_camera_id: int):
        self.primary_camera_id = primary_camera_id
        self.secondary_camera_id = secondary_camera_id
        self.counter = 0

    def _trace(self, prefix: str) -> str:
        self.counter += 1
        return f"{prefix}-{self.counter}"

    def get(self, path: str) -> Dict[str, Any]:
        if path == "/api/inference/route/batch?camera_ids=,,":
            return {
                "code": 0,
                "data": {
                    "trace_id": self._trace("route-fallback"),
                    "default_fallback_used": True,
                    "expanded_candidate_count": 0,
                    "route_list": [{"camera_id": 1, "backend_type": DEFAULT_BACKEND, "override_source": DEFAULT_OVERRIDE_SOURCE}],
                },
                "_http_status": 200,
            }
        raise RuntimeError(f"unsupported dry-run GET path: {path}")

    def post_json(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        if path == "/api/inference/route":
            camera_id = int(payload.get("camera_id") or self.primary_camera_id)
            return {
                "code": 0,
                "data": {
                    "trace_id": self._trace("route-primary"),
                    "backend_type": DEFAULT_BACKEND,
                    "global_backend_type": DEFAULT_BACKEND,
                    "override_source": DEFAULT_OVERRIDE_SOURCE,
                    "camera_id": camera_id,
                },
                "_http_status": 200,
            }
        if path == "/api/inference/route/batch":
            return {
                "code": 0,
                "data": {
                    "trace_id": self._trace("route-batch"),
                    "default_fallback_used": False,
                    "resolved_camera_count": 2,
                    "route_list": [
                        {"camera_id": self.primary_camera_id, "backend_type": DEFAULT_BACKEND, "override_source": DEFAULT_OVERRIDE_SOURCE},
                        {"camera_id": self.secondary_camera_id, "backend_type": DEFAULT_BACKEND, "override_source": DEFAULT_OVERRIDE_SOURCE},
                    ],
                },
                "_http_status": 200,
            }
        raise RuntimeError(f"unsupported dry-run POST path: {path}")

    def post_form(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
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
    parser = argparse.ArgumentParser(description="Validate rollout readiness without PowerShell.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--primary-camera-id", type=int, default=1)
    parser.add_argument("--secondary-camera-id", type=int, default=2)
    parser.add_argument("--expected-backend-type", default="")
    parser.add_argument("--expected-override-source", default="")
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


def is_expected_backend(actual: Any, expected: str) -> bool:
    if not expected.strip():
        return bool(actual)
    return isinstance(actual, str) and actual.strip().lower() == expected.strip().lower()


def is_expected_override_source(actual: Any, expected: str) -> bool:
    if not expected.strip():
        return True
    return isinstance(actual, str) and actual.strip().lower() == expected.strip().lower()


def build_result(api: str, passed: bool, detail: str, payload: Dict[str, Any]) -> CheckResult:
    return CheckResult(
        api=api,
        passed=passed,
        detail=detail,
        trace_id=get_nested(payload, "data", "trace_id"),
        http_status=payload.get("_http_status") if isinstance(payload, dict) else None,
        response_code=as_int(payload.get("code")) if isinstance(payload, dict) else None,
    )


def check_primary_route(client: Any, args: argparse.Namespace) -> CheckResult:
    payload = client.post_json("/api/inference/route", {"camera_id": args.primary_camera_id})
    passed = (
        as_int(payload.get("code")) == 0
        and bool(get_nested(payload, "data", "trace_id"))
        and bool(get_nested(payload, "data", "backend_type"))
        and bool(get_nested(payload, "data", "global_backend_type"))
        and is_expected_backend(get_nested(payload, "data", "backend_type"), args.expected_backend_type)
        and is_expected_override_source(get_nested(payload, "data", "override_source"), args.expected_override_source)
    )
    return build_result("/api/inference/route(primary-gray-check)", passed, f"code={payload.get('code')}; trace_id={get_nested(payload, 'data', 'trace_id')}; camera_id={args.primary_camera_id}; backend_type={get_nested(payload, 'data', 'backend_type')}; global_backend_type={get_nested(payload, 'data', 'global_backend_type')}; override_source={get_nested(payload, 'data', 'override_source')}", payload)


def check_gray_consistency(client: Any, args: argparse.Namespace, primary_backend: Optional[str], primary_override_source: Optional[str]) -> CheckResult:
    payload = client.post_json("/api/inference/route/batch", {"camera_ids": [args.primary_camera_id, args.secondary_camera_id]})
    route_list = get_nested(payload, "data", "route_list")
    first = route_list[0] if isinstance(route_list, list) and len(route_list) >= 1 else None
    second = route_list[1] if isinstance(route_list, list) and len(route_list) >= 2 else None
    consistent = isinstance(first, dict) and primary_backend == first.get("backend_type") and primary_override_source == first.get("override_source")
    passed = (
        as_int(payload.get("code")) == 0
        and bool(get_nested(payload, "data", "trace_id"))
        and isinstance(route_list, list) and len(route_list) >= 2
        and as_int(get_nested(payload, "data", "resolved_camera_count")) == len(route_list)
        and as_bool(get_nested(payload, "data", "default_fallback_used")) is False
        and isinstance(first, dict) and as_int(first.get("camera_id")) == args.primary_camera_id
        and isinstance(second, dict) and as_int(second.get("camera_id")) == args.secondary_camera_id
        and bool(first.get("backend_type")) and bool(second.get("backend_type"))
        and consistent
        and is_expected_backend(first.get("backend_type"), args.expected_backend_type)
        and is_expected_override_source(first.get("override_source"), args.expected_override_source)
    )
    return build_result("/api/inference/route/batch(gray-consistency)", passed, f"code={payload.get('code')}; trace_id={get_nested(payload, 'data', 'trace_id')}; resolved_camera_count={get_nested(payload, 'data', 'resolved_camera_count')}; default_fallback_used={get_nested(payload, 'data', 'default_fallback_used')}; first_camera_id={(first.get('camera_id') if isinstance(first, dict) else None)}; second_camera_id={(second.get('camera_id') if isinstance(second, dict) else None)}; first_backend={(first.get('backend_type') if isinstance(first, dict) else None)}; second_backend={(second.get('backend_type') if isinstance(second, dict) else None)}; first_override_source={(first.get('override_source') if isinstance(first, dict) else None)}; second_override_source={(second.get('override_source') if isinstance(second, dict) else None)}; consistent_with_single={consistent}", payload)


def check_rollback_fallback(client: Any) -> CheckResult:
    payload = client.get("/api/inference/route/batch?camera_ids=,,")
    route_list = get_nested(payload, "data", "route_list")
    first = route_list[0] if isinstance(route_list, list) and route_list else None
    passed = (
        as_int(payload.get("code")) == 0
        and bool(get_nested(payload, "data", "trace_id"))
        and as_bool(get_nested(payload, "data", "default_fallback_used")) is True
        and isinstance(route_list, list) and len(route_list) >= 1
        and isinstance(first, dict) and as_int(first.get("camera_id")) == 1
        and as_int(get_nested(payload, "data", "expanded_candidate_count")) == 0
    )
    return build_result("/api/inference/route/batch(rollback-fallback)", passed, f"code={payload.get('code')}; trace_id={get_nested(payload, 'data', 'trace_id')}; default_fallback_used={get_nested(payload, 'data', 'default_fallback_used')}; first_camera_id={(first.get('camera_id') if isinstance(first, dict) else None)}; expanded_candidate_count={get_nested(payload, 'data', 'expanded_candidate_count')}; list_size={(len(route_list) if isinstance(route_list, list) else -1)}", payload)


def build_check_queue(client: Any, args: argparse.Namespace):
    state: Dict[str, Optional[str]] = {"primary_backend": None, "primary_override_source": None}

    def first() -> CheckResult:
        result = check_primary_route(client, args)
        payload = client.post_json("/api/inference/route", {"camera_id": args.primary_camera_id})
        state["primary_backend"] = get_nested(payload, "data", "backend_type")
        state["primary_override_source"] = get_nested(payload, "data", "override_source")
        return result

    def second(_previous: CheckResult) -> CheckResult:
        return check_gray_consistency(client, args, state["primary_backend"], state["primary_override_source"])

    return [first, second, lambda _previous: check_rollback_fallback(client)]


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
        "primary_camera_id": args.primary_camera_id,
        "secondary_camera_id": args.secondary_camera_id,
        "expected_backend_type": args.expected_backend_type,
        "expected_override_source": args.expected_override_source,
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
    client_impl = client if client is not None else (DryRunClient(args.primary_camera_id, args.secondary_camera_id) if args.dry_run else ApiClient(args.base_url, args.timeout_sec))
    results: List[CheckResult] = []
    last_error: Optional[str] = None
    status = "passed"
    first_result: Optional[CheckResult] = None

    for index, check_factory in enumerate(build_check_queue(client_impl, args)):
        try:
            checker = check_factory(first_result)
        except TypeError:
            checker = check_factory()
        except Exception as exc:
            checker = CheckResult(api="unexpected_error", passed=False, detail=f"{type(exc).__name__}: {exc}")
        results.append(checker)
        if index == 0:
            first_result = checker
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
