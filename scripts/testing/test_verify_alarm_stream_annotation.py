import importlib.util
import json
import pathlib
import sys
import unittest
from io import BytesIO


MODULE_PATH = pathlib.Path(__file__).resolve().parent / "verify_alarm_stream_annotation.py"
SPEC = importlib.util.spec_from_file_location("verify_alarm_stream_annotation", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load module from {MODULE_PATH}")
verify_alarm_stream_annotation = importlib.util.module_from_spec(SPEC)
sys.modules["verify_alarm_stream_annotation"] = verify_alarm_stream_annotation
SPEC.loader.exec_module(verify_alarm_stream_annotation)


def make_jpeg(width: int = 320, height: int = 240, with_red_box: bool = True) -> bytes:
    import cv2
    import numpy as np

    image = np.zeros((height, width, 3), dtype=np.uint8)
    if with_red_box:
        cv2.rectangle(image, (40, 50), (220, 180), (0, 0, 255), 3)
    ok, encoded = cv2.imencode(".jpg", image)
    if not ok:
        raise RuntimeError("failed to encode test image")
    return encoded.tobytes()


class FakeHttpResponse:
    def __init__(self, status: int, body: bytes):
        self.status = status
        self._body = body

    def read(self) -> bytes:
        return self._body

    def getcode(self) -> int:
        return self.status

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False


class VerifyAlarmStreamAnnotationTests(unittest.TestCase):
    def test_build_dispatch_payload_includes_plugin_route(self):
        payload = verify_alarm_stream_annotation.build_dispatch_payload(
            camera_id=1,
            model_id=2,
            algorithm_id=3,
            source="test://frame",
            plugin_id="yolov8n",
            timestamp_ms=123456789,
        )
        self.assertEqual(payload["camera_id"], 1)
        self.assertEqual(payload["model_id"], 2)
        self.assertEqual(payload["algorithm_id"], 3)
        self.assertEqual(payload["persist_report"], 1)
        self.assertEqual(payload["frame"]["timestamp_ms"], 123456789)
        self.assertEqual(payload["plugin_route"]["plugin"]["plugin_id"], "yolov8n")

    def test_evaluate_red_overlay_detects_drawn_rectangle(self):
        image_bytes = make_jpeg(with_red_box=True)
        metrics = verify_alarm_stream_annotation.evaluate_red_overlay(
            image_bytes=image_bytes,
            bboxes=[[40, 50, 220, 180]],
        )
        self.assertTrue(metrics["decoded"])
        self.assertTrue(metrics["overlay_hit"])
        self.assertGreater(metrics["total_red_count"], 0)

    def test_evaluate_red_overlay_fails_without_red_rectangle(self):
        image_bytes = make_jpeg(with_red_box=False)
        metrics = verify_alarm_stream_annotation.evaluate_red_overlay(
            image_bytes=image_bytes,
            bboxes=[[40, 50, 220, 180]],
        )
        self.assertTrue(metrics["decoded"])
        self.assertFalse(metrics["overlay_hit"])

    def test_verify_alarm_stream_annotation_happy_path(self):
        dispatch_payload = {
            "code": 0,
            "data": {
                "trace_id": "trace-verify-1",
                "result": {
                    "alerts": [
                        {
                            "label": "person",
                            "bbox": [40, 50, 220, 180],
                        }
                    ]
                },
                "report": {
                    "report_id": 778899,
                    "status": "ok",
                },
            },
        }
        image_bytes = make_jpeg(with_red_box=True)

        def fake_open(req, timeout=30):
            url = req.full_url
            if url.endswith("/api/inference/dispatch"):
                return FakeHttpResponse(200, json.dumps(dispatch_payload).encode("utf-8"))
            if "/report/stream?" in url:
                return FakeHttpResponse(200, image_bytes)
            raise AssertionError(f"unexpected url: {url}")

        summary = verify_alarm_stream_annotation.verify_alarm_stream_annotation(
            base_url="http://127.0.0.1:18082",
            camera_id=1,
            model_id=1,
            algorithm_id=1,
            source="test://frame",
            plugin_id="yolov8n",
            timeout_sec=30.0,
            http_open=fake_open,
        )

        self.assertEqual(summary["status"], "passed")
        self.assertEqual(summary["report_id"], 778899)
        self.assertEqual(summary["alert_count"], 1)
        self.assertEqual(summary["bbox_count"], 1)
        self.assertTrue(summary["overlay_metrics"]["overlay_hit"])


if __name__ == "__main__":
    unittest.main()
