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
        self.assertIn("--max-plan-concurrency-pressure", argv)
        self.assertIn("1.0", argv)
        self.assertIn("--output-dir", argv)
        self.assertIn("tmp/out/phase10-acceptance", argv)

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
            self.assertEqual(summary["resource"]["before"]["cpu"]["loadavg_1m"], 1.2)
            self.assertEqual(summary["resource"]["after"]["memory"]["used_mb"], 120.0)


if __name__ == "__main__":
    unittest.main()
