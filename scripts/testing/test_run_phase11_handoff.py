import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parent / "run_phase11_handoff.py"
SPEC = importlib.util.spec_from_file_location("run_phase11_handoff", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load module from {MODULE_PATH}")
run_phase11_handoff = importlib.util.module_from_spec(SPEC)
sys.modules["run_phase11_handoff"] = run_phase11_handoff
SPEC.loader.exec_module(run_phase11_handoff)


class RunPhase11HandoffTests(unittest.TestCase):
    def test_parse_args_accepts_expected_flags(self):
        args = run_phase11_handoff.parse_args(
            [
                "--base-url",
                "http://127.0.0.1:18082",
                "--runtime-api-url",
                "http://127.0.0.1:18081",
                "--bridge-url",
                "http://127.0.0.1:19080",
                "--camera-id",
                "2",
                "--model-id",
                "3",
                "--algorithm-id",
                "4",
                "--soak-duration-sec",
                "120",
                "--soak-interval-sec",
                "6",
                "--soak-max-iterations",
                "2",
                "--verify-alarm-preview",
                "--alarm-preview-timeout-sec",
                "55",
                "--verify-quality-diagnostics",
                "--quality-iterations",
                "12",
                "--quality-interval-ms",
                "150",
                "--quality-timeout-sec",
                "40",
                "--output-dir",
                "tmp/out",
                "--dry-run",
            ]
        )
        self.assertEqual(args.base_url, "http://127.0.0.1:18082")
        self.assertEqual(args.runtime_api_url, "http://127.0.0.1:18081")
        self.assertEqual(args.bridge_url, "http://127.0.0.1:19080")
        self.assertEqual(args.camera_id, 2)
        self.assertEqual(args.model_id, 3)
        self.assertEqual(args.algorithm_id, 4)
        self.assertEqual(args.soak_duration_sec, 120)
        self.assertEqual(args.soak_interval_sec, 6)
        self.assertEqual(args.soak_max_iterations, 2)
        self.assertTrue(args.verify_alarm_preview)
        self.assertEqual(args.alarm_preview_timeout_sec, 55)
        self.assertTrue(args.verify_quality_diagnostics)
        self.assertEqual(args.quality_iterations, 12)
        self.assertEqual(args.quality_interval_ms, 150)
        self.assertEqual(args.quality_timeout_sec, 40.0)
        self.assertEqual(args.output_dir, "tmp/out")
        self.assertTrue(args.dry_run)

    def test_build_phase10_argv_includes_soak_and_gates(self):
        args = run_phase11_handoff.parse_args(
            [
                "--base-url",
                "http://127.0.0.1:18082",
                "--runtime-api-url",
                "http://127.0.0.1:18081",
                "--bridge-url",
                "http://127.0.0.1:19080",
                "--expect-snapshot-telemetry-status",
                "ok",
                "--expect-plan-telemetry-status",
                "ok",
                "--expect-bridge-decode-runtime-status",
                "degraded",
                "--expect-bridge-decode-mode",
                "ffmpeg",
                "--max-plan-concurrency-pressure",
                "1",
                "--max-plan-suggested-min-dispatch-ms",
                "1500",
                "--output-dir",
                "tmp/out",
            ]
        )
        argv = run_phase11_handoff.build_phase10_argv(args, "tmp/out/phase10-acceptance")
        self.assertIn("--include-soak", argv)
        self.assertIn("--expect-snapshot-telemetry-status", argv)
        self.assertIn("ok", argv)
        self.assertIn("--expect-bridge-decode-runtime-status", argv)
        self.assertIn("degraded", argv)
        self.assertIn("--expect-bridge-decode-mode", argv)
        self.assertIn("ffmpeg", argv)
        self.assertIn("--max-plan-concurrency-pressure", argv)
        self.assertIn("1.0", argv)
        self.assertIn("--output-dir", argv)
        self.assertIn("tmp/out/phase10-acceptance", argv)

    def test_parse_args_defaults_strict_decode_gates(self):
        args = run_phase11_handoff.parse_args([])
        self.assertEqual(args.expect_bridge_decode_runtime_status, "ok")
        self.assertEqual(args.expect_bridge_decode_mode, "mpp-rga")

    def test_build_alarm_preview_argv_includes_expected_flags(self):
        args = run_phase11_handoff.parse_args(
            [
                "--base-url",
                "http://127.0.0.1:18082",
                "--camera-id",
                "8",
                "--model-id",
                "9",
                "--algorithm-id",
                "10",
                "--source",
                "test://frame",
                "--plugin-id",
                "yolov8n",
                "--alarm-preview-timeout-sec",
                "66",
            ]
        )
        argv = run_phase11_handoff.build_alarm_preview_argv(args, "tmp/out/alarm-preview")
        self.assertIn("--base-url", argv)
        self.assertIn("http://127.0.0.1:18082", argv)
        self.assertIn("--camera-id", argv)
        self.assertIn("8", argv)
        self.assertIn("--model-id", argv)
        self.assertIn("9", argv)
        self.assertIn("--algorithm-id", argv)
        self.assertIn("10", argv)
        self.assertIn("--plugin-id", argv)
        self.assertIn("yolov8n", argv)
        self.assertIn("--timeout-sec", argv)
        self.assertIn("66.0", argv)
        self.assertIn("--output-dir", argv)
        self.assertIn("tmp/out/alarm-preview", argv)

    def test_build_quality_diagnostics_argv_includes_expected_flags(self):
        args = run_phase11_handoff.parse_args(
            [
                "--bridge-url",
                "http://127.0.0.1:19080",
                "--camera-id",
                "11",
                "--model-id",
                "12",
                "--source",
                "test://frame",
                "--plugin-id",
                "yolov8n",
                "--quality-iterations",
                "15",
                "--quality-interval-ms",
                "120",
                "--quality-timeout-sec",
                "50",
            ]
        )
        argv = run_phase11_handoff.build_quality_diagnostics_argv(args, "tmp/out/quality")
        self.assertIn("--bridge-url", argv)
        self.assertIn("http://127.0.0.1:19080", argv)
        self.assertIn("--camera-id", argv)
        self.assertIn("11", argv)
        self.assertIn("--model-id", argv)
        self.assertIn("12", argv)
        self.assertIn("--iterations", argv)
        self.assertIn("15", argv)
        self.assertIn("--interval-ms", argv)
        self.assertIn("120", argv)
        self.assertIn("--timeout-sec", argv)
        self.assertIn("50.0", argv)
        self.assertIn("--output-dir", argv)
        self.assertIn("tmp/out/quality", argv)

    def test_main_writes_summary_and_resource_snapshots(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            fake_snapshots = [
                {"label": "before", "cpu": {"loadavg_1m": 1.2}, "memory": {"used_mb": 100.0}},
                {"label": "after", "cpu": {"loadavg_1m": 1.8}, "memory": {"used_mb": 120.0}},
            ]

            def fake_collector(label):
                return dict(fake_snapshots[0] if label == "before" else fake_snapshots[1])

            with mock.patch.object(run_phase11_handoff.run_phase10_acceptance, "main", return_value=0) as phase10_main:
                exit_code = run_phase11_handoff.main(
                    [
                        "--base-url",
                        "http://127.0.0.1:18082",
                        "--output-dir",
                        temp_dir,
                        "--dry-run",
                    ],
                    resource_collector=fake_collector,
                )

            self.assertEqual(exit_code, 0)
            phase10_main.assert_called_once()

            summary_path = pathlib.Path(temp_dir) / "summary.json"
            self.assertTrue(summary_path.exists())
            summary = json.loads(summary_path.read_text(encoding="utf-8"))
            self.assertEqual(summary["status"], "passed")
            self.assertEqual(summary["phase10_exit_code"], 0)
            self.assertEqual(summary["alarm_preview"]["status"], "skipped")
            self.assertEqual(summary["quality_diagnostics"]["status"], "skipped")
            self.assertEqual(summary["resource"]["before"]["cpu"]["loadavg_1m"], 1.2)
            self.assertEqual(summary["resource"]["after"]["memory"]["used_mb"], 120.0)

    def test_main_runs_alarm_preview_stage_when_enabled(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            fake_snapshots = [
                {"label": "before", "cpu": {"loadavg_1m": 1.1}, "memory": {"used_mb": 200.0}},
                {"label": "after", "cpu": {"loadavg_1m": 1.3}, "memory": {"used_mb": 210.0}},
            ]

            def fake_collector(label):
                return dict(fake_snapshots[0] if label == "before" else fake_snapshots[1])

            preview_calls = []

            def fake_preview_runner(argv):
                preview_calls.append(list(argv))
                return {
                    "exit_code": 0,
                    "stdout": '{"status":"passed"}',
                    "stderr": "",
                }

            with mock.patch.object(run_phase11_handoff.run_phase10_acceptance, "main", return_value=0):
                exit_code = run_phase11_handoff.main(
                    [
                        "--base-url",
                        "http://127.0.0.1:18082",
                        "--output-dir",
                        temp_dir,
                        "--verify-alarm-preview",
                        "--dry-run",
                    ],
                    resource_collector=fake_collector,
                    alarm_preview_runner=fake_preview_runner,
                )

            self.assertEqual(exit_code, 0)
            self.assertEqual(len(preview_calls), 1)
            self.assertIn("--base-url", preview_calls[0])
            self.assertIn("http://127.0.0.1:18082", preview_calls[0])

            summary = json.loads((pathlib.Path(temp_dir) / "summary.json").read_text(encoding="utf-8"))
            self.assertEqual(summary["alarm_preview"]["status"], "passed")
            self.assertEqual(summary["alarm_preview"]["exit_code"], 0)

    def test_main_runs_quality_stage_when_enabled(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            fake_snapshots = [
                {"label": "before", "cpu": {"loadavg_1m": 1.1}, "memory": {"used_mb": 210.0}},
                {"label": "after", "cpu": {"loadavg_1m": 1.4}, "memory": {"used_mb": 220.0}},
            ]

            def fake_collector(label):
                return dict(fake_snapshots[0] if label == "before" else fake_snapshots[1])

            quality_calls = []

            def fake_quality_runner(argv):
                quality_calls.append(list(argv))
                return {
                    "exit_code": 0,
                    "stdout": '{"summary":{"successful_iterations":10}}',
                    "stderr": "",
                }

            with mock.patch.object(run_phase11_handoff.run_phase10_acceptance, "main", return_value=0):
                exit_code = run_phase11_handoff.main(
                    [
                        "--output-dir",
                        temp_dir,
                        "--verify-quality-diagnostics",
                        "--dry-run",
                    ],
                    resource_collector=fake_collector,
                    quality_diagnostics_runner=fake_quality_runner,
                )

            self.assertEqual(exit_code, 0)
            self.assertEqual(len(quality_calls), 1)
            self.assertIn("--iterations", quality_calls[0])
            self.assertIn("20", quality_calls[0])

            summary = json.loads((pathlib.Path(temp_dir) / "summary.json").read_text(encoding="utf-8"))
            self.assertEqual(summary["quality_diagnostics"]["status"], "passed")
            self.assertEqual(summary["quality_diagnostics"]["exit_code"], 0)


if __name__ == "__main__":
    unittest.main()
