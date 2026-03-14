import importlib.util
import pathlib
import sys
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parent / "run_phase12_h265_closeout.py"
SPEC = importlib.util.spec_from_file_location("run_phase12_h265_closeout", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load module from {MODULE_PATH}")
run_phase12_h265_closeout = importlib.util.module_from_spec(SPEC)
sys.modules["run_phase12_h265_closeout"] = run_phase12_h265_closeout
SPEC.loader.exec_module(run_phase12_h265_closeout)


class RunPhase12H265CloseoutTests(unittest.TestCase):
    def test_parse_args_defaults_match_h265_closeout_profile(self):
        args = run_phase12_h265_closeout.parse_args([])
        self.assertEqual(args.base_url, "http://127.0.0.1:18082")
        self.assertEqual(args.runtime_api_url, "http://127.0.0.1:18081")
        self.assertEqual(args.bridge_url, "http://127.0.0.1:19080")
        self.assertEqual(args.source, run_phase12_h265_closeout.DEFAULT_H265_SOURCE)
        self.assertEqual(args.dry_run_source, "test://frame")
        self.assertEqual(args.expect_snapshot_telemetry_status, "ok")
        self.assertEqual(args.expect_plan_telemetry_status, "ok")
        self.assertEqual(args.expect_bridge_decode_runtime_status, "ok")
        self.assertEqual(args.expect_bridge_decode_mode, "mpp-rga")
        self.assertEqual(args.soak_duration_sec, 900)
        self.assertEqual(args.soak_max_iterations, 0)
        self.assertEqual(args.quality_iterations, 30)
        self.assertFalse(args.skip_quality_diagnostics)

    def test_build_phase11_argv_contains_h265_and_quality_flags(self):
        args = run_phase12_h265_closeout.parse_args(
            [
                "--source",
                "rtsp://demo/h265/stream",
                "--soak-max-iterations",
                "0",
                "--quality-iterations",
                "18",
                "--quality-interval-ms",
                "180",
                "--quality-timeout-sec",
                "50",
                "--max-memory-used-delta-mb",
                "256",
                "--max-loadavg-1m",
                "6",
                "--dry-run",
            ]
        )
        argv = run_phase12_h265_closeout.build_phase11_argv(args)
        self.assertIn("--source", argv)
        self.assertIn("test://frame", argv)
        self.assertIn("--verify-quality-diagnostics", argv)
        self.assertIn("--quality-iterations", argv)
        self.assertIn("18", argv)
        self.assertIn("--soak-max-iterations", argv)
        self.assertIn("1", argv)
        self.assertIn("--max-memory-used-delta-mb", argv)
        self.assertIn("256.0", argv)
        self.assertIn("--max-loadavg-1m", argv)
        self.assertIn("6.0", argv)
        self.assertIn("--dry-run", argv)

    def test_build_phase11_argv_uses_unbounded_soak_when_not_dry_run(self):
        args = run_phase12_h265_closeout.parse_args(["--source", "rtsp://demo/h265/stream", "--soak-max-iterations", "0"])
        argv = run_phase12_h265_closeout.build_phase11_argv(args)
        self.assertIn("--soak-max-iterations", argv)
        self.assertIn("999999", argv)
        self.assertIn("rtsp://demo/h265/stream", argv)

    def test_build_phase11_argv_skips_quality_when_requested(self):
        args = run_phase12_h265_closeout.parse_args(["--skip-quality-diagnostics"])
        argv = run_phase12_h265_closeout.build_phase11_argv(args)
        self.assertNotIn("--verify-quality-diagnostics", argv)
        self.assertNotIn("--quality-iterations", argv)
        self.assertNotIn("--quality-interval-ms", argv)
        self.assertNotIn("--quality-timeout-sec", argv)

    def test_main_forwards_to_phase11_runner(self):
        with mock.patch.object(run_phase12_h265_closeout.run_phase11_handoff, "main", return_value=0) as phase11_main:
            exit_code = run_phase12_h265_closeout.main(
                [
                    "--source",
                    "rtsp://demo/h265/stream",
                    "--dry-run",
                ]
            )
        self.assertEqual(exit_code, 0)
        phase11_main.assert_called_once()
        forwarded = phase11_main.call_args[0][0]
        self.assertIn("--source", forwarded)
        self.assertIn("test://frame", forwarded)
        self.assertIn("--verify-quality-diagnostics", forwarded)


if __name__ == "__main__":
    unittest.main()
