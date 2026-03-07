#!/usr/bin/env python3
"""Linux-native inference contract validator for RK3588 gate execution."""

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


STRICT_REQUIRED_ERROR = "INFER_DL_REPLAY_BATCH_STRICT_RESUME_EXPECTED_TOTAL_REQUIRED"
STRICT_COUNT_ERROR = "INFER_DL_REPLAY_BATCH_STRICT_RESUME_TOTAL_SELECTED_COUNT_MISMATCH"
STRICT_FINGERPRINT_ERROR = "INFER_DL_REPLAY_BATCH_STRICT_RESUME_WINDOW_FINGERPRINT_MISMATCH"
STRICT_RESUME_TOKEN_ERROR = "INFER_DL_REPLAY_BATCH_STRICT_RESUME_TOKEN_MISMATCH"
REPLAY_NOT_FOUND_ERROR = "INFER_DL_REPLAY_NOT_FOUND"
DEFAULT_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_SOURCE = "test://frame"
DEFAULT_OUTPUT_DIR = "scripts/testing/out/inference-contracts"
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


class ValidationError(RuntimeError):
    """Raised when a contract check fails unexpectedly."""


class ApiClient:
    def __init__(self, base_url: str, timeout_sec: int = 10):
        self.base_url = base_url.rstrip("/")
        self.timeout_sec = timeout_sec

    def get(self, path: str) -> Dict[str, Any]:
        request = urllib.request.Request(
            self.base_url + path,
            headers={"Accept": "application/json"},
            method="GET",
        )
        return self._send(request)

    def post_json(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        data = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            headers={"Content-Type": "application/json", "Accept": "application/json"},
            method="POST",
        )
        return self._send(request)

    def post_form(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        data = urllib.parse.urlencode([(key, str(value)) for key, value in payload.items() if value is not None]).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
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
    def __init__(self, camera_id: int, next_camera_id: int, model_id: int, algorithm_id: int):
        self.camera_id = camera_id
        self.next_camera_id = next_camera_id
        self.model_id = model_id
        self.algorithm_id = algorithm_id
        self.counter = 0

    def _trace(self, prefix: str) -> str:
        self.counter += 1
        return f"{prefix}-{self.counter}"

    def get(self, path: str) -> Dict[str, Any]:
        parsed = urllib.parse.urlparse(path)
        query = urllib.parse.parse_qs(parsed.query)
        normalized = parsed.path
        if normalized == "/api/inference/health":
            return {
                "code": 0,
                "data": {
                    "trace_id": self._trace("health"),
                    "backend_type": DEFAULT_BACKEND,
                    "upstream": {
                        "circuit_open": False,
                        "circuit_open_until_ms": None,
                    },
                },
                "_http_status": 200,
            }
        if normalized == "/api/inference/circuit/status":
            return {
                "code": 0,
                "data": {
                    "trace_id": self._trace("circuit-status"),
                    "backend_type": DEFAULT_BACKEND,
                    "circuit": {
                        "trace_id": self._trace("circuit-inner"),
                        "backend_type": DEFAULT_BACKEND,
                        "route_backend_type": DEFAULT_BACKEND,
                        "circuit_open": False,
                    },
                },
                "_http_status": 200,
            }
        if normalized == "/api/inference/circuit/reset":
            return {
                "code": 0,
                "data": {
                    "trace_id": self._trace("circuit-reset"),
                    "backend_type": DEFAULT_BACKEND,
                    "circuit": {
                        "trace_id": self._trace("circuit-reset-inner"),
                        "backend_type": DEFAULT_BACKEND,
                        "route_backend_type": DEFAULT_BACKEND,
                        "reset": True,
                    },
                },
                "_http_status": 200,
            }
        if normalized == "/api/inference/dead-letter/stats":
            return {
                "code": 0,
                "data": {
                    "trace_id": self._trace("dl-stats"),
                    "dead_letter": {
                        "queue_size": 1,
                        "max_size": 100,
                        "default_list_limit": 20,
                        "max_list_limit": 100,
                        "max_replay_attempts": 3,
                        "replayed_entry_count": 0,
                        "replay_success_entry_count": 0,
                        "replay_failed_entry_count": 0,
                        "pending_replay_entry_count": 1,
                        "exhausted_replay_entry_count": 0,
                        "retryable_entry_count": 1,
                        "non_retryable_entry_count": 0,
                        "replay_in_progress_entry_count": 0,
                    },
                },
                "_http_status": 200,
            }
        if normalized == "/api/inference/dead-letter/latest":
            items = [{
                "dead_letter_id": 841,
                "max_replay_attempts": 3,
                "remaining_replay_attempts": 2,
                "replay_exhausted": False,
            }]
            return {
                "code": 0,
                "data": {
                    "trace_id": self._trace("dl-latest"),
                    "dead_letter": items,
                },
                "_http_status": 200,
            }
        if normalized == "/api/inference/dead-letter/replay":
            dead_letter_id = int((query.get("dead_letter_id") or ["0"])[0])
            return {
                "code": 1,
                "data": {
                    "trace_id": self._trace("dl-replay"),
                    "dead_letter_id": dead_letter_id,
                    "error_code": REPLAY_NOT_FOUND_ERROR,
                },
                "_http_status": 200,
            }
        if normalized == "/api/inference/dead-letter/replay/batch":
            if query.get("only_exhausted") == ["1"]:
                route_summary = {
                    "trace_id": self._trace("dl-replay-batch"),
                    "selected_count": 0,
                    "requested_offset": 0,
                    "effective_offset": 0,
                    "next_offset": 0,
                    "has_more": False,
                    "total_selected_count": 0,
                    "strict_resume": False,
                    "processed_count": 0,
                    "success_count": 0,
                    "failed_count": 0,
                    "effective_limit": 2,
                    "max_limit": 50,
                    "truncated": False,
                    "dry_run": False,
                    "stop_on_error": False,
                    "stopped_on_error": False,
                    "dry_run_count": 0,
                    "success_dead_letter_ids": [],
                    "failed_dead_letter_ids": [],
                    "dry_run_dead_letter_ids": [],
                    "remaining_count": 0,
                    "failed_replay_in_progress_count": 0,
                    "failed_replay_exhausted_count": 0,
                    "failed_other_count": 0,
                    "backend_type_counts": {},
                    "failure_reason_counts": {},
                    "report_status_counts": {},
                }
                return {"code": 0, "data": route_summary, "_http_status": 200}
            if query.get("camera_ids") == [',,']:
                return {
                    "code": 0,
                    "data": self._route_batch_response([1], default_fallback_used=True, expanded_candidate_count=0, hit_sources=[]),
                    "_http_status": 200,
                }
            if query.get("cameras"):
                camera_ids = [int(item) for item in query["cameras"][0].split(',') if item]
                return {"code": 0, "data": self._route_batch_response(camera_ids), "_http_status": 200}
            if query.get("camera_range"):
                raw = query["camera_range"][0]
                left, right = [int(part) for part in raw.split('-')]
                return {"code": 0, "data": self._route_batch_response([left, right]), "_http_status": 200}
        if normalized == "/api/inference/route/batch":
            if query.get("camera_ids") == [',,']:
                return {
                    "code": 0,
                    "data": self._route_batch_response([1], default_fallback_used=True, expanded_candidate_count=0, hit_sources=[]),
                    "_http_status": 200,
                }
            if query.get("cameras"):
                camera_ids = [int(item) for item in query["cameras"][0].split(',') if item]
                return {"code": 0, "data": self._route_batch_response(camera_ids), "_http_status": 200}
            if query.get("camera_range"):
                raw = query["camera_range"][0]
                left, right = [int(part) for part in raw.split('-')]
                return {"code": 0, "data": self._route_batch_response([left, right]), "_http_status": 200}
        raise RuntimeError(f"unsupported dry-run GET path: {path}")

    def post_json(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        parsed = urllib.parse.urlparse(path)
        normalized = parsed.path
        if normalized == "/api/inference/dead-letter/replay/batch":
            if payload.get("strict_resume") == 1 and payload.get("expected_total_selected_count") is None:
                return {
                    "code": 1,
                    "data": {
                        "trace_id": self._trace("strict-required"),
                        "strict_resume": True,
                        "error_code": STRICT_REQUIRED_ERROR,
                    },
                    "_http_status": 200,
                }
            if payload.get("strict_resume") == 1 and payload.get("expected_window_fingerprint") == "deadbeef":
                return {
                    "code": 1,
                    "data": {
                        "trace_id": self._trace("strict-fingerprint"),
                        "strict_resume": True,
                        "expected_window_fingerprint": "deadbeef",
                        "actual_window_fingerprint": "a" * 64,
                        "error_code": STRICT_FINGERPRINT_ERROR,
                    },
                    "_http_status": 200,
                }
            if payload.get("strict_resume") == 1 and payload.get("expected_resume_token") == "resume-mismatch":
                return {
                    "code": 1,
                    "data": {
                        "trace_id": self._trace("strict-resume-token"),
                        "strict_resume": True,
                        "expected_resume_token": "resume-mismatch",
                        "actual_resume_token": "b" * 64,
                        "error_code": STRICT_RESUME_TOKEN_ERROR,
                    },
                    "_http_status": 200,
                }
            if payload.get("strict_resume") == 1:
                return {
                    "code": 1,
                    "data": {
                        "trace_id": self._trace("strict-count"),
                        "strict_resume": True,
                        "expected_total_selected_count": int(payload.get("expected_total_selected_count", 0)),
                        "actual_total_selected_count": 1,
                        "error_code": STRICT_COUNT_ERROR,
                    },
                    "_http_status": 200,
                }
        if normalized == "/api/inference/test":
            trace_id = self._trace("infer-test")
            return {
                "code": 0,
                "data": {
                    "trace_id": trace_id,
                    "backend_type": DEFAULT_BACKEND,
                    "result": {"trace_id": trace_id},
                },
                "_http_status": 200,
            }
        if normalized == "/api/inference/dispatch":
            trace_id = str(payload.get("trace_id") or self._trace("dispatch"))
            return {
                "code": 0,
                "data": {
                    "trace_id": trace_id,
                    "backend_type": DEFAULT_BACKEND,
                    "result": {"trace_id": trace_id},
                    "report": {"trace_id": trace_id, "status": "accepted"},
                    "idempotent": {"trace_id": trace_id, "status": "accepted"},
                },
                "_http_status": 200,
            }
        if normalized == "/api/inference/route":
            camera_id = int(payload.get("camera_id") or self.camera_id)
            return {
                "code": 0,
                "data": {
                    "trace_id": self._trace("route"),
                    "camera_id": camera_id,
                    "backend_type": DEFAULT_BACKEND,
                    "global_backend_type": DEFAULT_BACKEND,
                    "override_source": DEFAULT_OVERRIDE_SOURCE,
                },
                "_http_status": 200,
            }
        if normalized == "/api/inference/route/batch":
            camera_ids = self._resolve_camera_ids_from_payload(payload)
            return {"code": 0, "data": self._route_batch_response(camera_ids), "_http_status": 200}
        raise RuntimeError(f"unsupported dry-run POST path: {path}")

    def post_form(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        raise RuntimeError(f"unsupported dry-run form path: {path}")

    def _resolve_camera_ids_from_payload(self, payload: Dict[str, Any]) -> List[int]:
        value = payload.get("camera_ids")
        if isinstance(value, list):
            return [int(item) for item in value]
        if isinstance(value, str) and "-" in value:
            raw_ids: List[int] = []
            for token in value.split(','):
                token = token.strip()
                if not token:
                    continue
                if '-' in token:
                    left, right = [int(part) for part in token.split('-')]
                    raw_ids.extend([left, right])
                else:
                    raw_ids.append(int(token))
            deduped: List[int] = []
            for item in raw_ids:
                if item not in deduped:
                    deduped.append(item)
            return deduped
        cameras_alias = payload.get("cameras")
        if isinstance(cameras_alias, list):
            return [int(item) for item in cameras_alias]
        return [self.camera_id]

    def _route_batch_response(self, camera_ids: List[int], default_fallback_used: bool = False, expanded_candidate_count: Optional[int] = None, hit_sources: Optional[List[str]] = None) -> Dict[str, Any]:
        unique_camera_ids: List[int] = []
        for item in camera_ids:
            if item not in unique_camera_ids:
                unique_camera_ids.append(item)
        if not unique_camera_ids:
            unique_camera_ids = [1]
        if expanded_candidate_count is None:
            expanded_candidate_count = len(unique_camera_ids)
        if hit_sources is None:
            hit_sources = ["body_camera_ids"] if not default_fallback_used else []
        route_list = [{"camera_id": camera_id, "backend_type": DEFAULT_BACKEND, "override_source": DEFAULT_OVERRIDE_SOURCE} for camera_id in unique_camera_ids]
        source_stats = {} if default_fallback_used else {"body_camera_ids": {"unique_added_count": len(unique_camera_ids)}}
        return {
            "trace_id": self._trace("route-batch"),
            "global_backend_type": DEFAULT_BACKEND,
            "route_list": route_list,
            "truncated": False,
            "max_camera_ids": 500,
            "max_camera_ids_cap": 500,
            "default_fallback_used": default_fallback_used,
            "resolved_camera_count": len(route_list),
            "input_token_count": len(unique_camera_ids),
            "expanded_candidate_count": expanded_candidate_count,
            "invalid_token_count": 0,
            "duplicate_filtered_count": max(0, len(camera_ids) - len(unique_camera_ids)),
            "hit_sources": hit_sources,
            "truncated_source": None,
            "source_stats": source_stats,
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
    parser = argparse.ArgumentParser(description="Validate inference contracts without PowerShell.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--model-id", type=int, default=1)
    parser.add_argument("--algorithm-id", type=int, default=1)
    parser.add_argument("--source", default=DEFAULT_SOURCE)
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


def object_field_count(obj: Any) -> int:
    if obj is None:
        return -1
    if isinstance(obj, dict):
        return len(obj)
    return -1


def as_bool(value: Any) -> Optional[bool]:
    if isinstance(value, bool):
        return value
    return None


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
    if not isinstance(actual, str):
        return False
    return actual.strip().lower() == expected.strip().lower()


def is_expected_override_source(actual: Any, expected: str) -> bool:
    if not expected.strip():
        return True
    if not isinstance(actual, str):
        return False
    return actual.strip().lower() == expected.strip().lower()


def build_result(api: str, passed: bool, detail: str, payload: Dict[str, Any]) -> CheckResult:
    return CheckResult(
        api=api,
        passed=passed,
        detail=detail,
        trace_id=get_nested(payload, "data", "trace_id"),
        http_status=payload.get("_http_status") if isinstance(payload, dict) else None,
        response_code=as_int(payload.get("code")) if isinstance(payload, dict) else None,
    )


def check_health(client: Any, args: argparse.Namespace) -> CheckResult:
    payload = client.get("/api/inference/health")
    backend = get_nested(payload, "data", "backend_type")
    circuit_open = get_nested(payload, "data", "upstream", "circuit_open")
    circuit_open_until_ms = get_nested(payload, "data", "upstream", "circuit_open_until_ms")
    passed = (
        as_int(payload.get("code")) == 0
        and bool(get_nested(payload, "data", "trace_id"))
        and bool(backend)
        and isinstance(get_nested(payload, "data", "upstream"), dict)
        and as_bool(circuit_open) is not None
        and is_expected_backend(backend, args.expected_backend_type)
        and (backend != DEFAULT_BACKEND or circuit_open is False or as_int(circuit_open_until_ms) is not None)
    )
    return build_result("/api/inference/health", passed, f"code={payload.get('code')}; backend_type={backend}; circuit_open={circuit_open}; circuit_open_until_ms={circuit_open_until_ms}", payload)


def check_circuit_status(client: Any, args: argparse.Namespace) -> CheckResult:
    payload = client.get("/api/inference/circuit/status")
    circuit = get_nested(payload, "data", "circuit")
    passed = (
        as_int(payload.get("code")) == 0
        and bool(get_nested(payload, "data", "trace_id"))
        and is_expected_backend(get_nested(payload, "data", "backend_type"), args.expected_backend_type)
        and isinstance(circuit, dict)
        and bool(circuit.get("trace_id"))
        and bool(circuit.get("backend_type"))
        and bool(circuit.get("route_backend_type"))
        and as_bool(circuit.get("circuit_open")) is not None
    )
    return build_result("/api/inference/circuit/status", passed, f"code={payload.get('code')}; backend_type={get_nested(payload, 'data', 'backend_type')}; circuit_open={get_nested(payload, 'data', 'circuit', 'circuit_open')}", payload)


def check_circuit_reset(client: Any, args: argparse.Namespace) -> CheckResult:
    payload = client.get("/api/inference/circuit/reset")
    circuit = get_nested(payload, "data", "circuit")
    passed = (
        as_int(payload.get("code")) == 0
        and bool(get_nested(payload, "data", "trace_id"))
        and is_expected_backend(get_nested(payload, "data", "backend_type"), args.expected_backend_type)
        and isinstance(circuit, dict)
        and bool(circuit.get("trace_id"))
        and bool(circuit.get("backend_type"))
        and bool(circuit.get("route_backend_type"))
        and as_bool(circuit.get("reset")) is True
    )
    return build_result("/api/inference/circuit/reset", passed, f"code={payload.get('code')}; backend_type={get_nested(payload, 'data', 'backend_type')}; reset={get_nested(payload, 'data', 'circuit', 'reset')}", payload)


def check_dead_letter_stats(client: Any) -> CheckResult:
    payload = client.get("/api/inference/dead-letter/stats")
    stats = get_nested(payload, "data", "dead_letter")
    queue_size = as_int(get_nested(payload, "data", "dead_letter", "queue_size"))
    replay_success = as_int(get_nested(payload, "data", "dead_letter", "replay_success_entry_count"))
    replay_failed = as_int(get_nested(payload, "data", "dead_letter", "replay_failed_entry_count"))
    replayed = as_int(get_nested(payload, "data", "dead_letter", "replayed_entry_count"))
    retryable = as_int(get_nested(payload, "data", "dead_letter", "retryable_entry_count"))
    non_retryable = as_int(get_nested(payload, "data", "dead_letter", "non_retryable_entry_count"))
    passed = (
        as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and isinstance(stats, dict)
        and queue_size is not None and queue_size >= 0
        and as_int(stats.get("max_size")) is not None and as_int(stats.get("max_size")) >= 1
        and as_int(stats.get("default_list_limit")) is not None and as_int(stats.get("default_list_limit")) >= 1
        and as_int(stats.get("max_list_limit")) is not None and as_int(stats.get("max_list_limit")) >= as_int(stats.get("default_list_limit"))
        and as_int(stats.get("max_replay_attempts")) is not None and as_int(stats.get("max_replay_attempts")) >= 1
        and replay_success is not None and replay_failed is not None and replayed is not None and replayed == replay_success + replay_failed
        and retryable is not None and non_retryable is not None and retryable + non_retryable == queue_size
        and as_int(stats.get("replay_in_progress_entry_count")) is not None and as_int(stats.get("replay_in_progress_entry_count")) <= queue_size
    )
    return build_result("/api/inference/dead-letter/stats", passed, f"code={payload.get('code')}; queue_size={queue_size}; replayed={replayed}; replay_success={replay_success}; replay_failed={replay_failed}; retryable={retryable}; non_retryable={non_retryable}", payload)


def check_dead_letter_latest(client: Any) -> CheckResult:
    payload = client.get("/api/inference/dead-letter/latest?limit=5")
    items = get_nested(payload, "data", "dead_letter")
    budget_check = True
    if isinstance(items, list) and items:
        first = items[0]
        budget_check = (
            as_int(first.get("max_replay_attempts")) is not None and as_int(first.get("max_replay_attempts")) >= 1
            and as_int(first.get("remaining_replay_attempts")) is not None and as_int(first.get("remaining_replay_attempts")) >= 0
            and as_bool(first.get("replay_exhausted")) is not None
        )
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and isinstance(items, list) and len(items) <= 5 and budget_check
    return build_result("/api/inference/dead-letter/latest", passed, f"code={payload.get('code')}; list_size={(len(items) if isinstance(items, list) else -1)}; budget_check={budget_check}", payload)


def check_replay_not_found(client: Any) -> CheckResult:
    payload = client.get("/api/inference/dead-letter/replay?dead_letter_id=-1")
    passed = as_int(payload.get("code")) not in (None, 0) and bool(get_nested(payload, "data", "trace_id")) and as_int(get_nested(payload, "data", "dead_letter_id")) == -1 and get_nested(payload, "data", "error_code") == REPLAY_NOT_FOUND_ERROR
    return build_result("/api/inference/dead-letter/replay(not-found)", passed, f"code={payload.get('code')}; dead_letter_id={get_nested(payload, 'data', 'dead_letter_id')}; error_code={get_nested(payload, 'data', 'error_code')}", payload)


def check_replay_batch_summary(client: Any) -> CheckResult:
    payload = client.get("/api/inference/dead-letter/replay/batch?limit=2&only_exhausted=1")
    data = get_nested(payload, "data")
    passed = (
        as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and isinstance(data, dict)
        and as_int(data.get("selected_count")) is not None and as_int(data.get("processed_count")) is not None
        and as_int(data.get("success_count")) is not None and as_int(data.get("failed_count")) is not None
        and as_int(data.get("effective_limit")) is not None and as_int(data.get("max_limit")) is not None
        and as_bool(data.get("truncated")) is not None and as_bool(data.get("dry_run")) is not None
        and as_bool(data.get("stop_on_error")) is not None and as_bool(data.get("stopped_on_error")) is not None
        and isinstance(data.get("backend_type_counts"), dict) and isinstance(data.get("failure_reason_counts"), dict) and isinstance(data.get("report_status_counts"), dict)
    )
    return build_result("/api/inference/dead-letter/replay/batch(summary)", passed, f"code={payload.get('code')}; selected_count={get_nested(payload, 'data', 'selected_count')}; processed_count={get_nested(payload, 'data', 'processed_count')}; backend_type_counts={object_field_count(get_nested(payload, 'data', 'backend_type_counts'))}", payload)

def check_replay_batch_strict_required(client: Any) -> CheckResult:
    payload = client.post_json("/api/inference/dead-letter/replay/batch?limit=2", {"strict_resume": 1, "dead_letter_ids": [841, 842], "dry_run": 1})
    passed = as_int(payload.get("code")) not in (None, 0) and bool(get_nested(payload, "data", "trace_id")) and as_bool(get_nested(payload, "data", "strict_resume")) is True and get_nested(payload, "data", "error_code") == STRICT_REQUIRED_ERROR
    return build_result("/api/inference/dead-letter/replay/batch(strict-resume-required)", passed, f"code={payload.get('code')}; strict_resume={get_nested(payload, 'data', 'strict_resume')}; error_code={get_nested(payload, 'data', 'error_code')}", payload)


def check_replay_batch_strict_count(client: Any) -> CheckResult:
    payload = client.post_json("/api/inference/dead-letter/replay/batch?limit=2", {"strict_resume": 1, "expected_total_selected_count": 2, "dead_letter_ids": [841, 842], "dry_run": 1})
    passed = as_int(payload.get("code")) not in (None, 0) and bool(get_nested(payload, "data", "trace_id")) and as_bool(get_nested(payload, "data", "strict_resume")) is True and as_int(get_nested(payload, "data", "expected_total_selected_count")) == 2 and as_int(get_nested(payload, "data", "actual_total_selected_count")) is not None and get_nested(payload, "data", "error_code") == STRICT_COUNT_ERROR
    return build_result("/api/inference/dead-letter/replay/batch(strict-resume-count-mismatch)", passed, f"code={payload.get('code')}; expected_total={get_nested(payload, 'data', 'expected_total_selected_count')}; actual_total={get_nested(payload, 'data', 'actual_total_selected_count')}; error_code={get_nested(payload, 'data', 'error_code')}", payload)


def check_replay_batch_fingerprint(client: Any) -> CheckResult:
    payload = client.post_json("/api/inference/dead-letter/replay/batch?limit=2", {"strict_resume": 1, "expected_total_selected_count": 2, "expected_window_fingerprint": "deadbeef", "dead_letter_ids": [841, 842], "dry_run": 1})
    actual = get_nested(payload, "data", "actual_window_fingerprint")
    passed = as_int(payload.get("code")) not in (None, 0) and bool(get_nested(payload, "data", "trace_id")) and as_bool(get_nested(payload, "data", "strict_resume")) is True and get_nested(payload, "data", "expected_window_fingerprint") == "deadbeef" and isinstance(actual, str) and len(actual) == 64 and actual != "deadbeef" and get_nested(payload, "data", "error_code") == STRICT_FINGERPRINT_ERROR
    return build_result("/api/inference/dead-letter/replay/batch(strict-resume-fingerprint-mismatch)", passed, f"code={payload.get('code')}; expected_window={get_nested(payload, 'data', 'expected_window_fingerprint')}; actual_window={actual}; error_code={get_nested(payload, 'data', 'error_code')}", payload)


def check_replay_batch_resume_token(client: Any) -> CheckResult:
    payload = client.post_json("/api/inference/dead-letter/replay/batch?limit=2", {"strict_resume": 1, "expected_total_selected_count": 2, "expected_resume_token": "resume-mismatch", "dead_letter_ids": [841, 842], "dry_run": 1})
    actual = get_nested(payload, "data", "actual_resume_token")
    passed = as_int(payload.get("code")) not in (None, 0) and bool(get_nested(payload, "data", "trace_id")) and as_bool(get_nested(payload, "data", "strict_resume")) is True and get_nested(payload, "data", "expected_resume_token") == "resume-mismatch" and isinstance(actual, str) and len(actual) == 64 and actual != "resume-mismatch" and get_nested(payload, "data", "error_code") == STRICT_RESUME_TOKEN_ERROR
    return build_result("/api/inference/dead-letter/replay/batch(strict-resume-token-mismatch)", passed, f"code={payload.get('code')}; expected_resume={get_nested(payload, 'data', 'expected_resume_token')}; actual_resume={actual}; error_code={get_nested(payload, 'data', 'error_code')}", payload)


def build_test_body(args: argparse.Namespace) -> Dict[str, Any]:
    return {"camera_id": args.camera_id, "model_id": args.model_id, "frame": {"source": args.source, "timestamp_ms": int(time.time() * 1000)}, "roi": []}


def check_test(client: Any, args: argparse.Namespace) -> CheckResult:
    payload = client.post_json("/api/inference/test", build_test_body(args))
    backend = get_nested(payload, "data", "backend_type")
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and is_expected_backend(backend, args.expected_backend_type) and bool(get_nested(payload, "data", "result", "trace_id"))
    return build_result("/api/inference/test", passed, f"code={payload.get('code')}; trace_id={get_nested(payload, 'data', 'trace_id')}; backend_type={backend}; result_trace_id={get_nested(payload, 'data', 'result', 'trace_id')}", payload)


def check_dispatch(client: Any, args: argparse.Namespace) -> CheckResult:
    trace_id = f"contract-{uuid.uuid4().hex}"
    payload = client.post_json("/api/inference/dispatch", {"trace_id": trace_id, "camera_id": args.camera_id, "model_id": args.model_id, "algorithm_id": args.algorithm_id, "persist_report": 0, "frame": {"source": args.source, "timestamp_ms": int(time.time() * 1000)}, "roi": []})
    backend = get_nested(payload, "data", "backend_type")
    passed = as_int(payload.get("code")) == 0 and get_nested(payload, "data", "trace_id") == trace_id and is_expected_backend(backend, args.expected_backend_type) and get_nested(payload, "data", "result", "trace_id") == trace_id and get_nested(payload, "data", "report", "trace_id") == trace_id and bool(get_nested(payload, "data", "report", "status")) and get_nested(payload, "data", "idempotent", "trace_id") == trace_id and bool(get_nested(payload, "data", "idempotent", "status"))
    return build_result("/api/inference/dispatch", passed, f"code={payload.get('code')}; trace_id={get_nested(payload, 'data', 'trace_id')}; backend_type={backend}; report_status={get_nested(payload, 'data', 'report', 'status')}; idem_status={get_nested(payload, 'data', 'idempotent', 'status')}", payload)


def check_route(client: Any, args: argparse.Namespace) -> CheckResult:
    payload = client.post_json("/api/inference/route", {"camera_id": args.camera_id})
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and as_int(get_nested(payload, "data", "camera_id")) == args.camera_id and is_expected_backend(get_nested(payload, "data", "backend_type"), args.expected_backend_type) and bool(get_nested(payload, "data", "global_backend_type")) and is_expected_override_source(get_nested(payload, "data", "override_source"), args.expected_override_source)
    return build_result("/api/inference/route", passed, f"code={payload.get('code')}; camera_id={get_nested(payload, 'data', 'camera_id')}; backend_type={get_nested(payload, 'data', 'backend_type')}; global_backend_type={get_nested(payload, 'data', 'global_backend_type')}; override_source={get_nested(payload, 'data', 'override_source')}", payload)


def check_route_batch(client: Any, args: argparse.Namespace) -> CheckResult:
    payload = client.post_json("/api/inference/route/batch", {"camera_ids": [args.camera_id]})
    route_list = get_nested(payload, "data", "route_list")
    first = route_list[0] if isinstance(route_list, list) and route_list else None
    source_stats = get_nested(payload, "data", "source_stats")
    body_stats = source_stats.get("body_camera_ids") if isinstance(source_stats, dict) else None
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and bool(get_nested(payload, "data", "global_backend_type")) and isinstance(route_list, list) and len(route_list) >= 1 and as_int(first.get("camera_id")) == args.camera_id and is_expected_backend(first.get("backend_type"), args.expected_backend_type) and as_bool(get_nested(payload, "data", "truncated")) is not None and as_int(get_nested(payload, "data", "max_camera_ids")) is not None and as_int(get_nested(payload, "data", "max_camera_ids_cap")) == 500 and as_bool(get_nested(payload, "data", "default_fallback_used")) is False and as_int(get_nested(payload, "data", "resolved_camera_count")) == len(route_list) and isinstance(get_nested(payload, "data", "hit_sources"), list) and get_nested(payload, "data", "truncated_source") is None and isinstance(source_stats, dict) and isinstance(body_stats, dict) and as_int(body_stats.get("unique_added_count")) is not None
    return build_result("/api/inference/route/batch", passed, f"code={payload.get('code')}; list_size={(len(route_list) if isinstance(route_list, list) else -1)}; first_camera_id={(first.get('camera_id') if isinstance(first, dict) else None)}; default_fallback_used={get_nested(payload, 'data', 'default_fallback_used')}", payload)


def check_route_batch_range(client: Any, args: argparse.Namespace) -> CheckResult:
    next_camera_id = args.camera_id + 1
    payload = client.post_json("/api/inference/route/batch", {"camera_ids": f"{args.camera_id}-{next_camera_id},{args.camera_id}"})
    route_list = get_nested(payload, "data", "route_list")
    first = route_list[0] if isinstance(route_list, list) and len(route_list) > 0 else None
    second = route_list[1] if isinstance(route_list, list) and len(route_list) > 1 else None
    source_stats = get_nested(payload, "data", "source_stats")
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and isinstance(route_list, list) and len(route_list) >= 2 and as_int(first.get("camera_id")) == args.camera_id and as_int(second.get("camera_id")) == next_camera_id and as_bool(get_nested(payload, "data", "truncated")) is not None and as_int(get_nested(payload, "data", "resolved_camera_count")) == len(route_list) and isinstance(get_nested(payload, "data", "hit_sources"), list) and get_nested(payload, "data", "truncated_source") is None and isinstance(source_stats, dict) and isinstance(source_stats.get("body_camera_ids"), dict)
    return build_result("/api/inference/route/batch(range)", passed, f"code={payload.get('code')}; first_camera_id={(first.get('camera_id') if isinstance(first, dict) else None)}; second_camera_id={(second.get('camera_id') if isinstance(second, dict) else None)}; resolved_camera_count={get_nested(payload, 'data', 'resolved_camera_count')}", payload)

def check_route_batch_alias(client: Any, args: argparse.Namespace) -> CheckResult:
    next_camera_id = args.camera_id + 1
    payload = client.post_json("/api/inference/route/batch", {"cameras": [args.camera_id, next_camera_id]})
    route_list = get_nested(payload, "data", "route_list")
    first = route_list[0] if isinstance(route_list, list) and len(route_list) > 0 else None
    second = route_list[1] if isinstance(route_list, list) and len(route_list) > 1 else None
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and isinstance(route_list, list) and len(route_list) >= 2 and as_int(first.get("camera_id")) == args.camera_id and as_int(second.get("camera_id")) == next_camera_id
    return build_result("/api/inference/route/batch(cameras-alias)", passed, f"code={payload.get('code')}; first_camera_id={(first.get('camera_id') if isinstance(first, dict) else None)}; second_camera_id={(second.get('camera_id') if isinstance(second, dict) else None)}", payload)


def check_route_batch_query_alias(client: Any, args: argparse.Namespace) -> CheckResult:
    next_camera_id = args.camera_id + 1
    payload = client.get(f"/api/inference/route/batch?cameras={args.camera_id},{next_camera_id}")
    route_list = get_nested(payload, "data", "route_list")
    first = route_list[0] if isinstance(route_list, list) and len(route_list) > 0 else None
    second = route_list[1] if isinstance(route_list, list) and len(route_list) > 1 else None
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and isinstance(route_list, list) and len(route_list) >= 2 and as_int(first.get("camera_id")) == args.camera_id and as_int(second.get("camera_id")) == next_camera_id
    return build_result("/api/inference/route/batch(query-cameras-alias)", passed, f"code={payload.get('code')}; first_camera_id={(first.get('camera_id') if isinstance(first, dict) else None)}; second_camera_id={(second.get('camera_id') if isinstance(second, dict) else None)}", payload)


def check_route_batch_query_range(client: Any, args: argparse.Namespace) -> CheckResult:
    next_camera_id = args.camera_id + 1
    payload = client.get(f"/api/inference/route/batch?camera_range={next_camera_id}-{args.camera_id}")
    route_list = get_nested(payload, "data", "route_list")
    first = route_list[0] if isinstance(route_list, list) and len(route_list) > 0 else None
    second = route_list[1] if isinstance(route_list, list) and len(route_list) > 1 else None
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and isinstance(route_list, list) and len(route_list) >= 2 and as_int(first.get("camera_id")) == next_camera_id and as_int(second.get("camera_id")) == args.camera_id
    return build_result("/api/inference/route/batch(query-camera_range-alias)", passed, f"code={payload.get('code')}; first_camera_id={(first.get('camera_id') if isinstance(first, dict) else None)}; second_camera_id={(second.get('camera_id') if isinstance(second, dict) else None)}", payload)


def check_route_batch_fallback(client: Any) -> CheckResult:
    payload = client.get("/api/inference/route/batch?camera_ids=,,")
    route_list = get_nested(payload, "data", "route_list")
    first = route_list[0] if isinstance(route_list, list) and route_list else None
    source_stats = get_nested(payload, "data", "source_stats")
    passed = as_int(payload.get("code")) == 0 and bool(get_nested(payload, "data", "trace_id")) and isinstance(route_list, list) and len(route_list) >= 1 and as_int(first.get("camera_id")) == 1 and as_bool(get_nested(payload, "data", "default_fallback_used")) is True and as_int(get_nested(payload, "data", "expanded_candidate_count")) == 0 and isinstance(get_nested(payload, "data", "hit_sources"), list) and len(get_nested(payload, "data", "hit_sources")) == 0 and get_nested(payload, "data", "truncated_source") is None and isinstance(source_stats, dict) and len(source_stats) == 0
    return build_result("/api/inference/route/batch(default-fallback)", passed, f"code={payload.get('code')}; first_camera_id={(first.get('camera_id') if isinstance(first, dict) else None)}; default_fallback_used={get_nested(payload, 'data', 'default_fallback_used')}; expanded_candidate_count={get_nested(payload, 'data', 'expanded_candidate_count')}", payload)


def build_check_queue(client: Any, args: argparse.Namespace):
    return [
        lambda: check_health(client, args),
        lambda: check_circuit_status(client, args),
        lambda: check_circuit_reset(client, args),
        lambda: check_dead_letter_stats(client),
        lambda: check_dead_letter_latest(client),
        lambda: check_replay_not_found(client),
        lambda: check_replay_batch_summary(client),
        lambda: check_replay_batch_strict_required(client),
        lambda: check_replay_batch_strict_count(client),
        lambda: check_replay_batch_fingerprint(client),
        lambda: check_replay_batch_resume_token(client),
        lambda: check_test(client, args),
        lambda: check_dispatch(client, args),
        lambda: check_route(client, args),
        lambda: check_route_batch(client, args),
        lambda: check_route_batch_range(client, args),
        lambda: check_route_batch_alias(client, args),
        lambda: check_route_batch_query_alias(client, args),
        lambda: check_route_batch_query_range(client, args),
        lambda: check_route_batch_fallback(client),
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
    next_camera_id = args.camera_id + 1
    client_impl = client if client is not None else (DryRunClient(args.camera_id, next_camera_id, args.model_id, args.algorithm_id) if args.dry_run else ApiClient(args.base_url, args.timeout_sec))
    results: List[CheckResult] = []
    last_error: Optional[str] = None
    status = "passed"

    for check_factory in build_check_queue(client_impl, args):
        try:
            checker = check_factory()
        except Exception as exc:
            status = "failed"
            last_error = f"{type(exc).__name__}: {exc}"
            checker = CheckResult(api="unexpected_error", passed=False, detail=last_error)
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
