import importlib.util
import pathlib
import sys
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parent / 'run_phase10_acceptance.py'
SPEC = importlib.util.spec_from_file_location('run_phase10_acceptance', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
run_phase10_acceptance = importlib.util.module_from_spec(SPEC)
sys.modules['run_phase10_acceptance'] = run_phase10_acceptance
SPEC.loader.exec_module(run_phase10_acceptance)


class RunPhase10AcceptanceTests(unittest.TestCase):
    def test_parse_args_defaults_base_url_to_18082(self):
        args = run_phase10_acceptance.parse_args([])
        self.assertEqual(args.base_url, 'http://127.0.0.1:18082')

    def test_parse_args_accepts_expected_flags(self):
        args = run_phase10_acceptance.parse_args([
            '--base-url', 'http://127.0.0.1:8080',
            '--runtime-api-url', 'http://127.0.0.1:18081',
            '--bridge-url', 'http://127.0.0.1:19080',
            '--bootstrap-token', 'edge-demo-bootstrap',
            '--plugin-id', 'yolov8n',
            '--camera-id', '2',
            '--model-id', '3',
            '--algorithm-id', '4',
            '--source', 'rtsp://demo',
            '--timeout-sec', '25',
            '--runtime-stack-budget', '12.5',
            '--expect-runtime-api-backend', 'java',
            '--expect-snapshot-telemetry-status', 'ok',
            '--expect-plan-telemetry-status', 'degraded',
            '--expect-bridge-decode-runtime-status', 'ok',
            '--expect-bridge-decode-mode', 'mpp-rga',
            '--max-plan-concurrency-pressure', '1.8',
            '--max-plan-suggested-min-dispatch-ms', '5000',
            '--min-snapshot-ready-stream-count', '1',
            '--min-plan-ready-stream-count', '1',
            '--stage-retry-attempts', '3',
            '--runtime-stack-retry-attempts', '2',
            '--cookie', 'satoken=demo',
            '--auth-header-name', 'access-token',
            '--auth-header-value', 'demo-token',
            '--output-dir', 'tmp/out',
            '--manage-bridge',
            '--bridge-bootstrap-token', 'bridge-bootstrap',
            '--bridge-wait-seconds', '15',
            '--bridge-poll-interval', '2',
            '--include-soak',
            '--soak-duration-sec', '90',
            '--soak-interval-sec', '6',
            '--soak-max-iterations', '2',
            '--soak-max-failed-steps', '1',
            '--fail-fast',
            '--dry-run',
        ])
        self.assertEqual(args.base_url, 'http://127.0.0.1:8080')
        self.assertEqual(args.runtime_api_url, 'http://127.0.0.1:18081')
        self.assertEqual(args.bridge_url, 'http://127.0.0.1:19080')
        self.assertEqual(args.bootstrap_token, 'edge-demo-bootstrap')
        self.assertEqual(args.plugin_id, 'yolov8n')
        self.assertEqual(args.camera_id, 2)
        self.assertEqual(args.model_id, 3)
        self.assertEqual(args.algorithm_id, 4)
        self.assertEqual(args.source, 'rtsp://demo')
        self.assertEqual(args.timeout_sec, 25)
        self.assertEqual(args.runtime_stack_budget, 12.5)
        self.assertEqual(args.expect_runtime_api_backend, 'java')
        self.assertEqual(args.expect_snapshot_telemetry_status, 'ok')
        self.assertEqual(args.expect_plan_telemetry_status, 'degraded')
        self.assertEqual(args.expect_bridge_decode_runtime_status, 'ok')
        self.assertEqual(args.expect_bridge_decode_mode, 'mpp-rga')
        self.assertEqual(args.max_plan_concurrency_pressure, 1.8)
        self.assertEqual(args.max_plan_suggested_min_dispatch_ms, 5000)
        self.assertEqual(args.min_snapshot_ready_stream_count, 1)
        self.assertEqual(args.min_plan_ready_stream_count, 1)
        self.assertEqual(args.stage_retry_attempts, 3)
        self.assertEqual(args.runtime_stack_retry_attempts, 2)
        self.assertEqual(args.cookie, 'satoken=demo')
        self.assertEqual(args.auth_header_name, 'access-token')
        self.assertEqual(args.auth_header_value, 'demo-token')
        self.assertEqual(args.output_dir, 'tmp/out')
        self.assertTrue(args.manage_bridge)
        self.assertEqual(args.bridge_bootstrap_token, 'bridge-bootstrap')
        self.assertEqual(args.bridge_wait_seconds, 15)
        self.assertEqual(args.bridge_poll_interval, 2)
        self.assertTrue(args.include_soak)
        self.assertEqual(args.soak_duration_sec, 90)
        self.assertEqual(args.soak_interval_sec, 6)
        self.assertEqual(args.soak_max_iterations, 2)
        self.assertEqual(args.soak_max_failed_steps, 1)
        self.assertTrue(args.fail_fast)
        self.assertTrue(args.dry_run)

    def test_build_linux_gate_argv_contains_runtime_stack_smoke_flags(self):
        args = run_phase10_acceptance.parse_args([
            '--base-url', 'http://127.0.0.1:8080',
            '--runtime-api-url', 'http://127.0.0.1:18081',
            '--bridge-url', 'http://127.0.0.1:19080',
            '--bootstrap-token', 'edge-demo-bootstrap',
            '--plugin-id', 'yolov8n',
            '--max-plan-concurrency-pressure', '1.7',
            '--max-plan-suggested-min-dispatch-ms', '4800',
            '--min-snapshot-ready-stream-count', '1',
            '--min-plan-ready-stream-count', '1',
            '--dry-run',
        ])
        argv = run_phase10_acceptance.build_linux_gate_argv(args)
        self.assertIn('--include-runtime-stack-smoke', argv)
        self.assertIn('--runtime-api-url', argv)
        self.assertIn('http://127.0.0.1:18081', argv)
        self.assertIn('--bridge-url', argv)
        self.assertIn('http://127.0.0.1:19080', argv)
        self.assertIn('--max-plan-concurrency-pressure', argv)
        self.assertIn('1.7', argv)
        self.assertIn('--max-plan-suggested-min-dispatch-ms', argv)
        self.assertIn('4800', argv)
        self.assertIn('--stage-retry-attempts', argv)
        self.assertIn('0', argv)
        self.assertIn('--runtime-stack-retry-attempts', argv)
        self.assertIn('1', argv)
        self.assertIn('--expect-bridge-decode-runtime-status', argv)
        self.assertIn('any', argv)
        self.assertIn('--expect-bridge-decode-mode', argv)
        self.assertIn('--dry-run', argv)

    def test_main_forwards_args_to_run_linux_gates_main(self):
        with mock.patch.object(run_phase10_acceptance.run_linux_gates, 'main', return_value=0) as linux_main:
            exit_code = run_phase10_acceptance.main([
                '--base-url', 'http://127.0.0.1:8080',
                '--runtime-api-url', 'http://127.0.0.1:18081',
                '--bridge-url', 'http://127.0.0.1:19080',
                '--bootstrap-token', 'edge-demo-bootstrap',
                '--plugin-id', 'yolov8n',
                '--dry-run',
            ])
        self.assertEqual(exit_code, 0)
        linux_main.assert_called_once()
        forwarded = linux_main.call_args[0][0]
        self.assertIn('--include-runtime-stack-smoke', forwarded)
        self.assertIn('--runtime-api-url', forwarded)
        self.assertIn('http://127.0.0.1:18081', forwarded)


if __name__ == '__main__':
    unittest.main()
