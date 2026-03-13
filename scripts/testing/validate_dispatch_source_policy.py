#!/usr/bin/env python3
"""Validate dispatch source policy to avoid accidental synthetic test-frame fallback."""

from __future__ import annotations

import argparse
import json
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, Optional, Sequence, Tuple
from urllib import error, request

DEFAULT_BASE_URL = "http://127.0.0.1:18082"
DEFAULT_TIMEOUT_SEC = 30.0
DEFAULT_INVALID_CAMERA_ID = 999999
DEFAULT_OUTPUT_DIR = "runtime/test-out"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def build_dispatch_payload(
    camera_id: int,
    model_id: int,
    algorithm_id: int,
    persist_report: int = 0,
    trace_id: str = "",
) -> Dict[str, Any]:
    return {
        "trace_id": str(trace_id).strip() or f"dispatch-source-policy-{uuid.uuid4().hex}",
        "camera_id": int(camera_id),
        "model_id": int(model_id),
        "algorithm_id": int(algorithm_id),
        "persist_report": int(persist_report),
        "frame": {
            "timestamp_ms": int(time.time() * 1000),
        },
        "roi": [],
    }


def post_json(
    url: str,
    payload: Dict[str, Any],
    timeout_sec: float,
    http_open: Optional[Callable[..., Any]] = None,
) -> Tuple[int, Dict[str, Any]]:
    opener = http_open or request.urlopen
    body = json.dumps(payload, ensure_ascii=True).encode("utf-8")
    req = request.Request(
        url=url,
        data=body,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    try:
        with opener(req, timeout=max(1.0, float(timeout_sec))) as resp:
            status_code = int(getattr(resp, "status", resp.getcode()))
            raw = resp.read().decode("utf-8")
    except error.HTTPError as exc:
        status_code = int(exc.code)
        raw = exc.read().decode("utf-8", errors="replace")

    parsed: Dict[str, Any]
    try:
        loaded = json.loads(raw) if raw else {}
        parsed = loaded if isinstance(loaded, dict) else {"payload": loaded}
    except json.JSONDecodeError:
        parsed = {"message": raw}
    return status_code, parsed


def first_non_blank(*values: Any) -> Optional[str]:
    for value in values:
        text = str(value or "").strip()
        if text:
            return text
    return None


def verify_dispatch_source_policy(
    base_url: str,
    camera_id: int,
    model_id: int,
    algorithm_id: int,
    invalid_camera_id: int,
    timeout_sec: float,
    http_open: Optional[Callable[..., Any]] = None,
) -> Dict[str, Any]:
    dispatch_url = base_url.rstrip("/") + "/api/inference/dispatch"

    valid_payload = build_dispatch_payload(
        camera_id=camera_id,
        model_id=model_id,
        algorithm_id=algorithm_id,
        persist_report=0,
        trace_id=f"dispatch-source-valid-{uuid.uuid4().hex}",
    )
    valid_http_status, valid_response = post_json(dispatch_url, valid_payload, timeout_sec, http_open=http_open)
    valid_code = int(valid_response.get("code", -1))
    if valid_code != 0:
        raise RuntimeError(f"valid dispatch failed: http_status={valid_http_status}, payload={valid_response}")
    valid_source = first_non_blank(
        ((valid_response.get("data") or {}).get("request") or {}).get("frame", {}).get("source"),
    )
    if valid_source is None:
        raise RuntimeError(f"valid dispatch source is missing: payload={valid_response}")
    if valid_source == "test://frame":
        raise RuntimeError(f"valid dispatch source unexpectedly fell back to synthetic test frame: payload={valid_response}")

    invalid_payload = build_dispatch_payload(
        camera_id=invalid_camera_id,
        model_id=model_id,
        algorithm_id=algorithm_id,
        persist_report=0,
        trace_id=f"dispatch-source-invalid-{uuid.uuid4().hex}",
    )
    invalid_http_status, invalid_response = post_json(dispatch_url, invalid_payload, timeout_sec, http_open=http_open)
    invalid_code = int(invalid_response.get("code", -1))
    if invalid_code == 0:
        raise RuntimeError(f"invalid camera dispatch unexpectedly succeeded: payload={invalid_response}")
    dead_letter_source = first_non_blank(
        (((invalid_response.get("data") or {}).get("dead_letter") or {}).get("request_payload") or {}).get("frame", {}).get("source"),
    )
    if dead_letter_source == "test://frame":
        raise RuntimeError(f"dead-letter request_payload.frame.source should not be synthetic fallback: payload={invalid_response}")

    return {
        "status": "passed",
        "timestamp": utc_now(),
        "dispatch_url": dispatch_url,
        "valid_case": {
            "camera_id": int(camera_id),
            "http_status": int(valid_http_status),
            "response_code": int(valid_code),
            "request_frame_source": valid_source,
        },
        "invalid_case": {
            "camera_id": int(invalid_camera_id),
            "http_status": int(invalid_http_status),
            "response_code": int(invalid_code),
            "dead_letter_frame_source": dead_letter_source or "",
        },
    }


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate /api/inference/dispatch source resolution policy.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--model-id", type=int, default=1)
    parser.add_argument("--algorithm-id", type=int, default=1)
    parser.add_argument("--invalid-camera-id", type=int, default=DEFAULT_INVALID_CAMERA_ID)
    parser.add_argument("--timeout-sec", type=float, default=DEFAULT_TIMEOUT_SEC)
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR)
    return parser.parse_args(argv)


def write_summary(output_dir: Path, summary: Dict[str, Any]) -> Path:
    run_dir = output_dir / f"dispatch-source-policy-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
    run_dir.mkdir(parents=True, exist_ok=True)
    summary_path = run_dir / "summary.json"
    with summary_path.open("w", encoding="utf-8") as handle:
        json.dump(summary, handle, ensure_ascii=True, indent=2)
        handle.write("\n")
    return summary_path


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    try:
        summary = verify_dispatch_source_policy(
            base_url=args.base_url,
            camera_id=args.camera_id,
            model_id=args.model_id,
            algorithm_id=args.algorithm_id,
            invalid_camera_id=args.invalid_camera_id,
            timeout_sec=args.timeout_sec,
        )
        summary_path = write_summary(Path(args.output_dir), summary)
        print(json.dumps({"status": "passed", "summary_path": str(summary_path), "summary": summary}, ensure_ascii=True))
        return 0
    except Exception as exc:
        print(json.dumps({"status": "failed", "error": str(exc)}, ensure_ascii=True))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
