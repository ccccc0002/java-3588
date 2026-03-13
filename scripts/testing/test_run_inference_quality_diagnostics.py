import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent / "run_inference_quality_diagnostics.py"
SPEC = importlib.util.spec_from_file_location("run_inference_quality_diagnostics", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load module from {MODULE_PATH}")
run_inference_quality_diagnostics = importlib.util.module_from_spec(SPEC)
sys.modules["run_inference_quality_diagnostics"] = run_inference_quality_diagnostics
SPEC.loader.exec_module(run_inference_quality_diagnostics)


class InferenceQualityDiagnosticsTests(unittest.TestCase):
    def test_analyze_detection_marks_invalid_bbox_and_score(self):
        detection = {"label": "person", "bbox": [10, 10, 5, 30], "score": 1.5}
        stats = run_inference_quality_diagnostics.analyze_detection(detection)
        self.assertTrue(stats["invalid_bbox"])
        self.assertTrue(stats["invalid_score"])
        self.assertEqual(stats["label"], "person")

    def test_analyze_response_summarizes_metrics(self):
        response = {
            "trace_id": "trace-1",
            "latency_ms": 55,
            "detections": [
                {"label": "person", "bbox": [10, 20, 30, 40], "score": 0.91},
                {"label": "", "bbox": [0, 0, 0, 0], "score": -0.2},
            ],
            "alerts": [{"label": "person"}],
        }
        metrics = run_inference_quality_diagnostics.analyze_response(response)
        self.assertEqual(metrics["detection_count"], 2)
        self.assertEqual(metrics["alert_count"], 1)
        self.assertEqual(metrics["invalid_bbox_count"], 1)
        self.assertEqual(metrics["invalid_score_count"], 1)
        self.assertEqual(metrics["empty_label_count"], 1)
        self.assertEqual(metrics["latency_ms"], 55.0)

    def test_summarize_results_aggregates_histogram_and_latency(self):
        iteration_results = [
            {
                "status": "ok",
                "metrics": {
                    "latency_ms": 20.0,
                    "detection_count": 1,
                    "alert_count": 1,
                    "invalid_bbox_count": 0,
                    "invalid_score_count": 0,
                    "empty_label_count": 0,
                    "labels": ["person"],
                },
            },
            {
                "status": "ok",
                "metrics": {
                    "latency_ms": 40.0,
                    "detection_count": 2,
                    "alert_count": 1,
                    "invalid_bbox_count": 1,
                    "invalid_score_count": 1,
                    "empty_label_count": 1,
                    "labels": ["person", "bus"],
                },
            },
            {"status": "failed", "error": "timeout"},
        ]
        summary = run_inference_quality_diagnostics.summarize_results(iteration_results, expected_iterations=3)
        self.assertEqual(summary["expected_iterations"], 3)
        self.assertEqual(summary["completed_iterations"], 3)
        self.assertEqual(summary["successful_iterations"], 2)
        self.assertEqual(summary["failed_iterations"], 1)
        self.assertEqual(summary["total_detection_count"], 3)
        self.assertEqual(summary["total_alert_count"], 2)
        self.assertEqual(summary["invalid_bbox_count"], 1)
        self.assertEqual(summary["invalid_score_count"], 1)
        self.assertEqual(summary["empty_label_count"], 1)
        self.assertEqual(summary["label_histogram"]["person"], 2)
        self.assertEqual(summary["latency_ms"]["p50"], 30.0)
        self.assertEqual(summary["latency_ms"]["max"], 40.0)

    def test_summarize_results_degrades_when_invalid_bbox_exceeds_threshold(self):
        iteration_results = [
            {
                "status": "ok",
                "metrics": {
                    "latency_ms": 20.0,
                    "detection_count": 1,
                    "alert_count": 0,
                    "invalid_bbox_count": 1,
                    "invalid_score_count": 0,
                    "empty_label_count": 0,
                    "labels": ["person"],
                },
            },
        ]
        summary = run_inference_quality_diagnostics.summarize_results(
            iteration_results,
            expected_iterations=1,
            max_invalid_bbox_count=0,
        )
        self.assertEqual("degraded", summary["status"])

    def test_summarize_results_allows_limited_failed_iterations(self):
        iteration_results = [
            {"status": "failed", "error": "timeout"},
            {
                "status": "ok",
                "metrics": {
                    "latency_ms": 12.0,
                    "detection_count": 1,
                    "alert_count": 0,
                    "invalid_bbox_count": 0,
                    "invalid_score_count": 0,
                    "empty_label_count": 0,
                    "labels": ["person"],
                },
            },
        ]
        summary = run_inference_quality_diagnostics.summarize_results(
            iteration_results,
            expected_iterations=2,
            max_failed_iterations=1,
        )
        self.assertEqual("passed", summary["status"])

    def test_normalize_optional_threshold(self):
        self.assertIsNone(run_inference_quality_diagnostics.normalize_optional_threshold(-1))
        self.assertIsNone(run_inference_quality_diagnostics.normalize_optional_threshold("x"))
        self.assertEqual(0, run_inference_quality_diagnostics.normalize_optional_threshold(0))
        self.assertEqual(2, run_inference_quality_diagnostics.normalize_optional_threshold("2"))

    def test_main_writes_summary_with_fake_runner(self):
        responses = [
            {"trace_id": "t1", "latency_ms": 11, "detections": [{"label": "person", "bbox": [1, 2, 3, 4], "score": 0.5}], "alerts": []},
            {"trace_id": "t2", "latency_ms": 12, "detections": [], "alerts": []},
        ]
        index = {"value": 0}

        def fake_post(*args, **kwargs):
            i = index["value"]
            index["value"] += 1
            return responses[i]

        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = run_inference_quality_diagnostics.main(
                [
                    "--bridge-url",
                    "http://127.0.0.1:19080",
                    "--iterations",
                    "2",
                    "--output-dir",
                    temp_dir,
                ],
                infer_runner=fake_post,
                sleep_fn=lambda *_: None,
            )
            self.assertEqual(exit_code, 0)
            summary_path = pathlib.Path(temp_dir) / "summary.json"
            self.assertTrue(summary_path.exists())
            summary = json.loads(summary_path.read_text(encoding="utf-8"))
            self.assertEqual(summary["successful_iterations"], 2)
            self.assertEqual(summary["failed_iterations"], 0)
            self.assertEqual("passed", summary["status"])

    def test_main_returns_nonzero_when_quality_gate_is_violated(self):
        responses = [
            {
                "trace_id": "t1",
                "latency_ms": 11,
                "detections": [{"label": "person", "bbox": [3, 3, 3, 9], "score": 0.5}],
                "alerts": [],
            },
        ]

        def fake_post(*args, **kwargs):
            return responses[0]

        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = run_inference_quality_diagnostics.main(
                [
                    "--bridge-url",
                    "http://127.0.0.1:19080",
                    "--iterations",
                    "1",
                    "--max-invalid-bbox-count",
                    "0",
                    "--output-dir",
                    temp_dir,
                ],
                infer_runner=fake_post,
                sleep_fn=lambda *_: None,
            )
            self.assertEqual(exit_code, 1)
            summary_path = pathlib.Path(temp_dir) / "summary.json"
            summary = json.loads(summary_path.read_text(encoding="utf-8"))
            self.assertEqual("degraded", summary["status"])

    def test_main_retries_transient_failure_and_recovers(self):
        calls = {"count": 0}

        def flaky_post(*args, **kwargs):
            calls["count"] += 1
            if calls["count"] == 1:
                raise RuntimeError("transient timeout")
            return {
                "trace_id": "t1",
                "latency_ms": 10,
                "detections": [{"label": "person", "bbox": [1, 1, 3, 3], "score": 0.9}],
                "alerts": [],
            }

        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = run_inference_quality_diagnostics.main(
                [
                    "--bridge-url",
                    "http://127.0.0.1:19080",
                    "--iterations",
                    "1",
                    "--retry-attempts",
                    "2",
                    "--retry-interval-ms",
                    "0",
                    "--output-dir",
                    temp_dir,
                ],
                infer_runner=flaky_post,
                sleep_fn=lambda *_: None,
            )
            self.assertEqual(exit_code, 0)
            summary = json.loads((pathlib.Path(temp_dir) / "summary.json").read_text(encoding="utf-8"))
            self.assertEqual("passed", summary["status"])
            self.assertEqual(1, summary["successful_iterations"])


if __name__ == "__main__":
    unittest.main()
