#!/usr/bin/env python3
"""Run repeated bridge inference and aggregate quality diagnostics."""

from __future__ import annotations

import argparse
import json
import math
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Sequence
from urllib import error, request


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def default_output_dir() -> str:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return str(Path("runtime") / "test-out" / f"inference-quality-{stamp}")


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Inference quality diagnostics against runtime bridge.")
    parser.add_argument("--bridge-url", default="http://127.0.0.1:19080")
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--model-id", type=int, default=1)
    parser.add_argument("--source", default="test://frame")
    parser.add_argument("--plugin-id", default="yolov8n")
    parser.add_argument("--iterations", type=int, default=20)
    parser.add_argument("--interval-ms", type=int, default=200)
    parser.add_argument("--timeout-sec", type=float, default=30.0)
    parser.add_argument("--output-dir", default="")
    parser.add_argument("--max-invalid-bbox-count", type=int, default=-1)
    parser.add_argument("--max-invalid-score-count", type=int, default=-1)
    parser.add_argument("--max-empty-label-count", type=int, default=-1)
    return parser.parse_args(argv)


def build_request_payload(
    camera_id: int,
    model_id: int,
    source: str,
    plugin_id: str,
    trace_id: str = "",
) -> Dict[str, Any]:
    payload: Dict[str, Any] = {
        "trace_id": str(trace_id).strip() or f"quality-{uuid.uuid4().hex}",
        "camera_id": int(camera_id),
        "model_id": int(model_id),
        "frame": {
            "source": str(source).strip(),
            "timestamp_ms": int(time.time() * 1000),
        },
    }
    plugin_text = str(plugin_id or "").strip()
    if plugin_text:
        payload["plugin_route"] = {"requested": True, "plugin": {"plugin_id": plugin_text}}
    return payload


def post_infer(bridge_url: str, payload: Dict[str, Any], timeout_sec: float) -> Dict[str, Any]:
    url = bridge_url.rstrip("/") + "/v1/infer"
    body = json.dumps(payload, ensure_ascii=True).encode("utf-8")
    req = request.Request(
        url=url,
        data=body,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    try:
        with request.urlopen(req, timeout=max(1.0, float(timeout_sec))) as resp:
            status_code = int(getattr(resp, "status", resp.getcode()))
            raw = resp.read().decode("utf-8")
    except error.HTTPError as exc:
        status_code = int(exc.code)
        raw = exc.read().decode("utf-8", errors="replace")
    payload_obj = json.loads(raw) if raw else {}
    if status_code >= 400:
        raise RuntimeError(f"infer failed status={status_code}: {payload_obj}")
    if not isinstance(payload_obj, dict):
        raise RuntimeError("infer response is not json object")
    return payload_obj


def to_float(value: Any) -> Optional[float]:
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip()
    if not text:
        return None
    try:
        return float(text)
    except Exception:
        return None


def analyze_detection(detection: Dict[str, Any]) -> Dict[str, Any]:
    label = str(detection.get("label") or "").strip()
    bbox = detection.get("bbox") if isinstance(detection.get("bbox"), list) else []
    score = to_float(detection.get("score"))

    invalid_bbox = True
    if isinstance(bbox, list) and len(bbox) >= 4:
        x1 = to_float(bbox[0])
        y1 = to_float(bbox[1])
        x2 = to_float(bbox[2])
        y2 = to_float(bbox[3])
        if None not in (x1, y1, x2, y2):
            invalid_bbox = x2 <= x1 or y2 <= y1

    invalid_score = score is not None and (score < 0.0 or score > 1.0)
    return {
        "label": label,
        "empty_label": label == "",
        "invalid_bbox": bool(invalid_bbox),
        "invalid_score": bool(invalid_score),
    }


def analyze_response(response: Dict[str, Any]) -> Dict[str, Any]:
    detections = response.get("detections") if isinstance(response.get("detections"), list) else []
    alerts = response.get("alerts") if isinstance(response.get("alerts"), list) else []
    analyzed = [analyze_detection(item) for item in detections if isinstance(item, dict)]
    return {
        "trace_id": str(response.get("trace_id") or ""),
        "latency_ms": to_float(response.get("latency_ms")),
        "detection_count": len(detections),
        "alert_count": len(alerts),
        "invalid_bbox_count": sum(1 for item in analyzed if item["invalid_bbox"]),
        "invalid_score_count": sum(1 for item in analyzed if item["invalid_score"]),
        "empty_label_count": sum(1 for item in analyzed if item["empty_label"]),
        "labels": [item["label"] for item in analyzed if item["label"]],
    }


def percentile(values: List[float], p: float) -> Optional[float]:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return float(ordered[0])
    rank = (len(ordered) - 1) * p
    low = int(math.floor(rank))
    high = int(math.ceil(rank))
    if low == high:
        return float(ordered[low])
    weight = rank - low
    return float(ordered[low] + (ordered[high] - ordered[low]) * weight)


def summarize_results(
    iteration_results: List[Dict[str, Any]],
    expected_iterations: int,
    max_invalid_bbox_count: Optional[int] = None,
    max_invalid_score_count: Optional[int] = None,
    max_empty_label_count: Optional[int] = None,
) -> Dict[str, Any]:
    success = [item for item in iteration_results if item.get("status") == "ok"]
    failed = [item for item in iteration_results if item.get("status") != "ok"]
    latencies = [float(item["metrics"]["latency_ms"]) for item in success if item.get("metrics", {}).get("latency_ms") is not None]

    label_histogram: Dict[str, int] = {}
    total_detection_count = 0
    total_alert_count = 0
    invalid_bbox_count = 0
    invalid_score_count = 0
    empty_label_count = 0
    for item in success:
        metrics = item.get("metrics", {})
        total_detection_count += int(metrics.get("detection_count", 0))
        total_alert_count += int(metrics.get("alert_count", 0))
        invalid_bbox_count += int(metrics.get("invalid_bbox_count", 0))
        invalid_score_count += int(metrics.get("invalid_score_count", 0))
        empty_label_count += int(metrics.get("empty_label_count", 0))
        for label in metrics.get("labels", []):
            label_histogram[label] = int(label_histogram.get(label, 0)) + 1

    latency_summary = {
        "min": min(latencies) if latencies else None,
        "max": max(latencies) if latencies else None,
        "avg": round(sum(latencies) / len(latencies), 4) if latencies else None,
        "p50": round(percentile(latencies, 0.5), 4) if latencies else None,
        "p95": round(percentile(latencies, 0.95), 4) if latencies else None,
    }

    status = "passed" if len(failed) == 0 else "degraded"
    if status == "passed" and max_invalid_bbox_count is not None and invalid_bbox_count > max_invalid_bbox_count:
        status = "degraded"
    if status == "passed" and max_invalid_score_count is not None and invalid_score_count > max_invalid_score_count:
        status = "degraded"
    if status == "passed" and max_empty_label_count is not None and empty_label_count > max_empty_label_count:
        status = "degraded"

    return {
        "expected_iterations": int(expected_iterations),
        "completed_iterations": len(iteration_results),
        "successful_iterations": len(success),
        "failed_iterations": len(failed),
        "total_detection_count": int(total_detection_count),
        "total_alert_count": int(total_alert_count),
        "invalid_bbox_count": int(invalid_bbox_count),
        "invalid_score_count": int(invalid_score_count),
        "empty_label_count": int(empty_label_count),
        "label_histogram": label_histogram,
        "latency_ms": latency_summary,
        "status": status,
    }


def write_json(path: Path, payload: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


def normalize_optional_threshold(value: Any) -> Optional[int]:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return None
    return None if parsed < 0 else parsed


def main(
    argv: Optional[Sequence[str]] = None,
    infer_runner: Optional[Callable[[str, Dict[str, Any], float], Dict[str, Any]]] = None,
    sleep_fn: Optional[Callable[[float], None]] = None,
) -> int:
    args = parse_args(argv)
    iterations = max(1, int(args.iterations))
    interval_ms = max(0, int(args.interval_ms))
    timeout_sec = max(1.0, float(args.timeout_sec))
    output_dir = Path(args.output_dir or default_output_dir()).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    infer_call = infer_runner or post_infer
    sleeper = sleep_fn or time.sleep
    started_at = utc_now()
    events: List[Dict[str, Any]] = []

    for idx in range(iterations):
        payload = build_request_payload(
            camera_id=args.camera_id,
            model_id=args.model_id,
            source=args.source,
            plugin_id=args.plugin_id,
        )
        event: Dict[str, Any] = {
            "index": idx + 1,
            "timestamp": utc_now(),
            "request": payload,
            "status": "failed",
        }
        try:
            response = infer_call(args.bridge_url, payload, timeout_sec)
            metrics = analyze_response(response)
            event["status"] = "ok"
            event["response"] = response
            event["metrics"] = metrics
        except Exception as exc:
            event["error"] = f"{type(exc).__name__}: {exc}"
        events.append(event)
        if idx + 1 < iterations and interval_ms > 0:
            sleeper(interval_ms / 1000.0)

    finished_at = utc_now()
    max_invalid_bbox_count = normalize_optional_threshold(args.max_invalid_bbox_count)
    max_invalid_score_count = normalize_optional_threshold(args.max_invalid_score_count)
    max_empty_label_count = normalize_optional_threshold(args.max_empty_label_count)

    summary = summarize_results(
        events,
        expected_iterations=iterations,
        max_invalid_bbox_count=max_invalid_bbox_count,
        max_invalid_score_count=max_invalid_score_count,
        max_empty_label_count=max_empty_label_count,
    )
    summary_payload = {
        "started_at": started_at,
        "finished_at": finished_at,
        "bridge_url": args.bridge_url,
        "camera_id": int(args.camera_id),
        "model_id": int(args.model_id),
        "source": args.source,
        "plugin_id": args.plugin_id,
        "quality_gate": {
            "max_invalid_bbox_count": max_invalid_bbox_count,
            "max_invalid_score_count": max_invalid_score_count,
            "max_empty_label_count": max_empty_label_count,
        },
        **summary,
    }

    events_path = output_dir / "events.ndjson"
    with events_path.open("w", encoding="utf-8") as handle:
        for event in events:
            handle.write(json.dumps(event, ensure_ascii=True) + "\n")
    summary_path = output_dir / "summary.json"
    write_json(summary_path, summary_payload)

    print(json.dumps({"summary_path": str(summary_path), "summary": summary_payload}, ensure_ascii=True))
    return 0 if summary_payload.get("status") == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
