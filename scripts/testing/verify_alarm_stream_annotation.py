#!/usr/bin/env python3
"""Verify persisted alarm preview image is retrievable and annotated."""

from __future__ import annotations

import argparse
import json
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Sequence, Tuple
from urllib import error, parse, request


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def build_dispatch_payload(
    camera_id: int,
    model_id: int,
    algorithm_id: int,
    source: str,
    trace_id: str = "",
    plugin_id: str = "",
    timestamp_ms: Optional[int] = None,
) -> Dict[str, Any]:
    payload: Dict[str, Any] = {
        "trace_id": str(trace_id).strip() or f"alarm-verify-{uuid.uuid4().hex}",
        "camera_id": int(camera_id),
        "model_id": int(model_id),
        "algorithm_id": int(algorithm_id),
        "persist_report": 1,
        "frame": {
            "source": str(source).strip(),
            "timestamp_ms": int(timestamp_ms if timestamp_ms is not None else time.time() * 1000),
        },
        "roi": [],
    }
    plugin_text = str(plugin_id or "").strip()
    if plugin_text:
        payload["plugin_route"] = {
            "requested": True,
            "plugin": {
                "plugin_id": plugin_text,
            },
        }
    return payload


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


def get_bytes(
    url: str,
    timeout_sec: float,
    http_open: Optional[Callable[..., Any]] = None,
) -> Tuple[int, bytes]:
    opener = http_open or request.urlopen
    req = request.Request(url=url, headers={"Accept": "*/*"}, method="GET")
    try:
        with opener(req, timeout=max(1.0, float(timeout_sec))) as resp:
            status_code = int(getattr(resp, "status", resp.getcode()))
            payload = resp.read()
    except error.HTTPError as exc:
        status_code = int(exc.code)
        payload = exc.read()
    return status_code, payload


def unwrap_dispatch_payload(payload: Dict[str, Any]) -> Dict[str, Any]:
    code = payload.get("code")
    data = payload.get("data")
    if code != 0 or not isinstance(data, dict):
        raise RuntimeError(f"dispatch failed: code={code}, payload={payload}")
    return data


def extract_alert_items(dispatch_data: Dict[str, Any]) -> List[Dict[str, Any]]:
    result = dispatch_data.get("result")
    if not isinstance(result, dict):
        return []
    alerts = result.get("alerts")
    detections = result.get("detections")
    source = alerts if isinstance(alerts, list) and alerts else detections
    if not isinstance(source, list):
        return []
    return [item for item in source if isinstance(item, dict)]


def to_bbox_ints(items: List[Dict[str, Any]]) -> List[List[int]]:
    bboxes: List[List[int]] = []
    for item in items:
        raw = item.get("bbox")
        if not isinstance(raw, list) or len(raw) < 4:
            continue
        try:
            x1, y1, x2, y2 = [int(round(float(raw[i]))) for i in range(4)]
        except Exception:
            continue
        bboxes.append([x1, y1, x2, y2])
    return bboxes


def is_empty_alert_report(report_data: Dict[str, Any]) -> bool:
    if not isinstance(report_data, dict):
        return False
    status = str(report_data.get("status", "")).strip().lower()
    reason = str(report_data.get("reason", "")).strip().lower()
    if status != "skipped":
        return False
    return "empty alert" in reason or reason in {"no alerts", "no alert", "alerts filtered"}


def evaluate_red_overlay(
    image_bytes: bytes,
    bboxes: List[List[int]],
    border_thickness: int = 3,
    min_red_channel: int = 140,
    min_red_delta: int = 40,
) -> Dict[str, Any]:
    if not image_bytes:
        return {"decoded": False, "overlay_hit": False, "reason": "empty image bytes"}
    if not bboxes:
        return {"decoded": False, "overlay_hit": False, "reason": "empty bbox list"}

    import cv2  # local import to keep module import lightweight
    import numpy as np

    arr = np.frombuffer(image_bytes, dtype=np.uint8)
    image = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if image is None:
        return {"decoded": False, "overlay_hit": False, "reason": "image decode failed"}

    height, width = image.shape[:2]
    thickness = max(1, int(border_thickness))

    bbox_metrics: List[Dict[str, Any]] = []
    overlay_hit = False
    total_red = 0
    total_samples = 0

    for bbox in bboxes:
        x1, y1, x2, y2 = bbox
        x1 = max(0, min(x1, width - 1))
        x2 = max(0, min(x2, width - 1))
        y1 = max(0, min(y1, height - 1))
        y2 = max(0, min(y2, height - 1))
        if x2 <= x1 or y2 <= y1:
            continue

        left = image[y1 : y2 + 1, x1 : min(width, x1 + thickness)]
        right = image[y1 : y2 + 1, max(0, x2 - thickness + 1) : x2 + 1]
        top = image[y1 : min(height, y1 + thickness), x1 : x2 + 1]
        bottom = image[max(0, y2 - thickness + 1) : y2 + 1, x1 : x2 + 1]
        edges = [edge for edge in [left, right, top, bottom] if edge.size > 0]
        if not edges:
            continue
        samples = np.concatenate([edge.reshape(-1, 3) for edge in edges], axis=0)
        b = samples[:, 0].astype(np.int16)
        g = samples[:, 1].astype(np.int16)
        r = samples[:, 2].astype(np.int16)
        red_mask = (r >= int(min_red_channel)) & (r >= g + int(min_red_delta)) & (r >= b + int(min_red_delta))
        red_count = int(np.count_nonzero(red_mask))
        sample_count = int(samples.shape[0])
        ratio = float(red_count / sample_count) if sample_count > 0 else 0.0
        hit = ratio >= 0.01 or red_count >= 12
        overlay_hit = overlay_hit or hit
        total_red += red_count
        total_samples += sample_count
        bbox_metrics.append(
            {
                "bbox": [x1, y1, x2, y2],
                "sample_count": sample_count,
                "red_count": red_count,
                "red_ratio": round(ratio, 6),
                "hit": hit,
            }
        )

    overall_ratio = float(total_red / total_samples) if total_samples > 0 else 0.0
    return {
        "decoded": True,
        "overlay_hit": bool(overlay_hit),
        "image_width": int(width),
        "image_height": int(height),
        "bbox_metrics": bbox_metrics,
        "total_red_count": int(total_red),
        "total_sample_count": int(total_samples),
        "overall_red_ratio": round(overall_ratio, 6),
    }


def verify_alarm_stream_annotation(
    base_url: str,
    camera_id: int,
    model_id: int,
    algorithm_id: int,
    source: str,
    plugin_id: str,
    timeout_sec: float,
    require_alert: bool = False,
    http_open: Optional[Callable[..., Any]] = None,
) -> Dict[str, Any]:
    dispatch_url = base_url.rstrip("/") + "/api/inference/dispatch"
    dispatch_payload = build_dispatch_payload(
        camera_id=camera_id,
        model_id=model_id,
        algorithm_id=algorithm_id,
        source=source,
        plugin_id=plugin_id,
    )
    dispatch_http_status, dispatch_response = post_json(dispatch_url, dispatch_payload, timeout_sec, http_open=http_open)
    dispatch_data = unwrap_dispatch_payload(dispatch_response)

    report_data = dispatch_data.get("report") if isinstance(dispatch_data.get("report"), dict) else {}
    alert_items = extract_alert_items(dispatch_data)
    bboxes = to_bbox_ints(alert_items)
    report_id = report_data.get("report_id")
    if report_id in (None, "", 0):
        if not require_alert and is_empty_alert_report(report_data):
            return {
                "status": "skipped_no_alert",
                "timestamp": utc_now(),
                "base_url": base_url,
                "dispatch_url": dispatch_url,
                "stream_url": "",
                "camera_id": int(camera_id),
                "model_id": int(model_id),
                "algorithm_id": int(algorithm_id),
                "plugin_id": str(plugin_id).strip(),
                "dispatch_http_status": int(dispatch_http_status),
                "stream_http_status": None,
                "trace_id": str(dispatch_data.get("trace_id", "")),
                "report_id": None,
                "alert_count": len(alert_items),
                "bbox_count": len(bboxes),
                "image_bytes": 0,
                "skip_reason": str(report_data.get("reason", "")).strip() or "empty alerts",
                "overlay_metrics": {"decoded": False, "overlay_hit": False, "reason": "skipped_no_alert"},
            }
        raise RuntimeError(f"dispatch did not persist report_id: report={report_data}")

    if not bboxes:
        if not require_alert and is_empty_alert_report(report_data):
            return {
                "status": "skipped_no_alert",
                "timestamp": utc_now(),
                "base_url": base_url,
                "dispatch_url": dispatch_url,
                "stream_url": "",
                "camera_id": int(camera_id),
                "model_id": int(model_id),
                "algorithm_id": int(algorithm_id),
                "plugin_id": str(plugin_id).strip(),
                "dispatch_http_status": int(dispatch_http_status),
                "stream_http_status": None,
                "trace_id": str(dispatch_data.get("trace_id", "")),
                "report_id": report_id,
                "alert_count": len(alert_items),
                "bbox_count": 0,
                "image_bytes": 0,
                "skip_reason": str(report_data.get("reason", "")).strip() or "empty alerts",
                "overlay_metrics": {"decoded": False, "overlay_hit": False, "reason": "skipped_no_alert"},
            }
        raise RuntimeError("dispatch response has no valid alert bbox data")

    stream_url = base_url.rstrip("/") + "/report/stream?" + parse.urlencode({"id": str(report_id)})
    stream_http_status, image_bytes = get_bytes(stream_url, timeout_sec, http_open=http_open)
    if stream_http_status >= 400:
        raise RuntimeError(f"report stream request failed: status={stream_http_status}")

    overlay_metrics = evaluate_red_overlay(image_bytes, bboxes)
    if not overlay_metrics.get("overlay_hit"):
        raise RuntimeError(f"overlay red-edge check failed: metrics={overlay_metrics}")

    return {
        "status": "passed",
        "timestamp": utc_now(),
        "base_url": base_url,
        "dispatch_url": dispatch_url,
        "stream_url": stream_url,
        "camera_id": int(camera_id),
        "model_id": int(model_id),
        "algorithm_id": int(algorithm_id),
        "plugin_id": str(plugin_id).strip(),
        "dispatch_http_status": int(dispatch_http_status),
        "stream_http_status": int(stream_http_status),
        "trace_id": str(dispatch_data.get("trace_id", "")),
        "report_id": report_id,
        "alert_count": len(alert_items),
        "bbox_count": len(bboxes),
        "image_bytes": len(image_bytes),
        "overlay_metrics": overlay_metrics,
    }


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify /report/stream returns annotated alarm image.")
    parser.add_argument("--base-url", default="http://127.0.0.1:18082")
    parser.add_argument("--camera-id", type=int, default=1)
    parser.add_argument("--model-id", type=int, default=1)
    parser.add_argument("--algorithm-id", type=int, default=1)
    parser.add_argument("--source", default="test://frame")
    parser.add_argument("--plugin-id", default="yolov8n")
    parser.add_argument("--timeout-sec", type=float, default=30.0)
    parser.add_argument("--require-alert", action="store_true")
    parser.add_argument("--output-dir", default="")
    return parser.parse_args(argv)


def write_summary(output_dir: str, summary: Dict[str, Any]) -> Path:
    root = Path(output_dir).resolve()
    root.mkdir(parents=True, exist_ok=True)
    summary_path = root / "summary.json"
    with summary_path.open("w", encoding="utf-8") as handle:
        json.dump(summary, handle, ensure_ascii=True, indent=2)
        handle.write("\n")
    return summary_path


def default_output_dir() -> str:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return str(Path("runtime") / "test-out" / f"alarm-stream-annotation-{stamp}")


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    out_dir = args.output_dir or default_output_dir()
    try:
        summary = verify_alarm_stream_annotation(
            base_url=args.base_url,
            camera_id=args.camera_id,
            model_id=args.model_id,
            algorithm_id=args.algorithm_id,
            source=args.source,
            plugin_id=args.plugin_id,
            timeout_sec=args.timeout_sec,
            require_alert=bool(args.require_alert),
        )
        summary_path = write_summary(out_dir, summary)
        print(json.dumps({"status": "passed", "summary_path": str(summary_path), "summary": summary}, ensure_ascii=True))
        return 0
    except Exception as exc:
        failure_summary = {
            "status": "failed",
            "timestamp": utc_now(),
            "base_url": args.base_url,
            "camera_id": args.camera_id,
            "model_id": args.model_id,
            "algorithm_id": args.algorithm_id,
            "source": args.source,
            "plugin_id": args.plugin_id,
            "error": f"{type(exc).__name__}: {exc}",
        }
        summary_path = write_summary(out_dir, failure_summary)
        print(json.dumps({"status": "failed", "summary_path": str(summary_path), "summary": failure_summary}, ensure_ascii=True))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
