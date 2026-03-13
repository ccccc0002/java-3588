import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent / 'run_linux_gates.py'
SPEC = importlib.util.spec_from_file_location('run_linux_gates', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
run_linux_gates = importlib.util.module_from_spec(SPEC)
sys.modules['run_linux_gates'] = run_linux_gates
SPEC.loader.exec_module(run_linux_gates)


class RunLinuxGatesTests(unittest.TestCase):
    def test_parse_args_accepts_expected_flags(self):
        args = run_linux_gates.parse_args([
            '--base-url', 'http://127.0.0.1:8080',
            '--camera-id', '9',
            '--model-id', '4',
            '--algorithm-id', '5',
            '--primary-camera-id', '9',
            '--secondary-camera-id', '10',
            '--expected-backend-type', 'rk3588_rknn',
            '--expected-override-source', 'camera_override',
            '--source', 'test://frame',
            '--cookie', 'satoken=test-cookie',
            '--auth-header-name', 'access-token',
            '--auth-header-value', 'test-access-token',
            '--output-dir', 'tmp/out',
            '--timeout-sec', '20',
            '--include-runtime-stack-smoke',
            '--runtime-api-url', 'http://127.0.0.1:18081',
            '--bridge-url', 'http://127.0.0.1:19080',
            '--bootstrap-token', 'edge-demo-bootstrap',
            '--plugin-id', 'yolov8n',
            '--runtime-stack-budget', '12.5',
            '--expect-runtime-api-backend', 'java',
            '--expect-snapshot-telemetry-status', 'ok',
            '--expect-plan-telemetry-status', 'degraded',
            '--expect-bridge-decode-runtime-status', 'ok',
            '--expect-bridge-decode-mode', 'mpp-rga',
            '--max-plan-concurrency-pressure', '1.9',
            '--max-plan-suggested-min-dispatch-ms', '5200',
            '--min-snapshot-ready-stream-count', '1',
            '--min-plan-ready-stream-count', '1',
            '--include-soak',
            '--soak-duration-sec', '60',
            '--soak-interval-sec', '5',
            '--soak-max-iterations', '2',
            '--manage-bridge',
            '--bridge-bootstrap-token', 'edge-demo-bootstrap',
            '--bridge-wait-seconds', '15',
            '--bridge-poll-interval', '1',
            '--fail-fast',
            '--dry-run',
        ])

        self.assertEqual(args.base_url, 'http://127.0.0.1:8080')
        self.assertEqual(args.camera_id, 9)
        self.assertEqual(args.model_id, 4)
        self.assertEqual(args.algorithm_id, 5)
        self.assertEqual(args.primary_camera_id, 9)
        self.assertEqual(args.secondary_camera_id, 10)
        self.assertEqual(args.expected_backend_type, 'rk3588_rknn')
        self.assertEqual(args.expected_override_source, 'camera_override')
        self.assertEqual(args.source, 'test://frame')
        self.assertEqual(args.cookie, 'satoken=test-cookie')
        self.assertEqual(args.auth_header_name, 'access-token')
        self.assertEqual(args.auth_header_value, 'test-access-token')
        self.assertEqual(args.output_dir, 'tmp/out')
        self.assertEqual(args.timeout_sec, 20)
        self.assertTrue(args.include_runtime_stack_smoke)
        self.assertEqual(args.runtime_api_url, 'http://127.0.0.1:18081')
        self.assertEqual(args.bridge_url, 'http://127.0.0.1:19080')
        self.assertEqual(args.bootstrap_token, 'edge-demo-bootstrap')
        self.assertEqual(args.plugin_id, 'yolov8n')
        self.assertEqual(args.runtime_stack_budget, 12.5)
        self.assertEqual(args.expect_runtime_api_backend, 'java')
        self.assertEqual(args.expect_snapshot_telemetry_status, 'ok')
        self.assertEqual(args.expect_plan_telemetry_status, 'degraded')
        self.assertEqual(args.expect_bridge_decode_runtime_status, 'ok')
        self.assertEqual(args.expect_bridge_decode_mode, 'mpp-rga')
        self.assertEqual(args.max_plan_concurrency_pressure, 1.9)
        self.assertEqual(args.max_plan_suggested_min_dispatch_ms, 5200)
        self.assertEqual(args.min_snapshot_ready_stream_count, 1)
        self.assertEqual(args.min_plan_ready_stream_count, 1)
        self.assertTrue(args.include_soak)
        self.assertEqual(args.soak_duration_sec, 60)
        self.assertEqual(args.soak_interval_sec, 5)
        self.assertEqual(args.soak_max_iterations, 2)
        self.assertTrue(args.manage_bridge)
        self.assertEqual(args.bridge_bootstrap_token, 'edge-demo-bootstrap')
        self.assertEqual(args.bridge_wait_seconds, 15)
        self.assertEqual(args.bridge_poll_interval, 1)
        self.assertTrue(args.fail_fast)
        self.assertTrue(args.dry_run)

    def test_build_stage_definitions_includes_runtime_stack_stage_when_enabled(self):
        args = run_linux_gates.parse_args([
            '--base-url', 'http://127.0.0.1:8080',
            '--include-runtime-stack-smoke',
            '--runtime-api-url', 'http://127.0.0.1:18081',
            '--bridge-url', 'http://127.0.0.1:19080',
            '--bootstrap-token', 'edge-demo-bootstrap',
            '--plugin-id', 'yolov8n',
            '--runtime-stack-budget', '15.0',
            '--expect-runtime-api-backend', 'java',
            '--expect-snapshot-telemetry-status', 'ok',
            '--expect-plan-telemetry-status', 'ok',
            '--expect-bridge-decode-runtime-status', 'ok',
            '--expect-bridge-decode-mode', 'mpp-rga',
            '--max-plan-concurrency-pressure', '1.8',
            '--max-plan-suggested-min-dispatch-ms', '5000',
            '--min-snapshot-ready-stream-count', '1',
            '--min-plan-ready-stream-count', '1',
        ])
        stages = run_linux_gates.build_stage_definitions(args, pathlib.Path('tmp/out'))
        names = [stage['name'] for stage in stages]
        self.assertIn('runtime_stack_smoke', names)
        runtime_stage = [stage for stage in stages if stage['name'] == 'runtime_stack_smoke'][0]
        command = runtime_stage['command']
        self.assertIn('--runtime-api-url', command)
        self.assertIn('http://127.0.0.1:18081', command)
        self.assertIn('--bridge-url', command)
        self.assertIn('http://127.0.0.1:19080', command)
        self.assertIn('--expect-runtime-api-backend', command)
        self.assertIn('java', command)
        self.assertIn('--expect-bridge-decode-runtime-status', command)
        self.assertIn('ok', command)
        self.assertIn('--expect-bridge-decode-mode', command)
        self.assertIn('mpp-rga', command)
        self.assertIn('--max-plan-concurrency-pressure', command)
        self.assertIn('1.8', command)
        self.assertIn('--min-plan-ready-stream-count', command)
        self.assertIn('1', command)
        self.assertNotIn('--output-dir', command)

    def test_runtime_stack_stage_does_not_append_global_control_flags(self):
        args = run_linux_gates.parse_args([
            '--base-url', 'http://127.0.0.1:8080',
            '--include-runtime-stack-smoke',
            '--dry-run',
            '--fail-fast',
        ])
        stages = run_linux_gates.build_stage_definitions(args, pathlib.Path('tmp/out'))
        runtime_stage = [stage for stage in stages if stage['name'] == 'runtime_stack_smoke'][0]
        command = runtime_stage['command']
        self.assertNotIn('--dry-run', command)
        self.assertNotIn('--fail-fast', command)

    def test_build_stage_definitions_uses_bootstrap_header_by_default(self):
        args = run_linux_gates.parse_args([
            '--base-url', 'http://127.0.0.1:8080',
            '--bootstrap-token', 'edge-demo-bootstrap',
        ])
        stages = run_linux_gates.build_stage_definitions(args, pathlib.Path('tmp/out'))
        first_stage = stages[0]
        command = first_stage['command']
        header_name_index = command.index('--auth-header-name') + 1
        header_value_index = command.index('--auth-header-value') + 1
        self.assertEqual('X-Bootstrap-Token', command[header_name_index])
        self.assertEqual('edge-demo-bootstrap', command[header_value_index])

    def test_read_stage_summary_parses_runtime_stack_stdout_json(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            summary_path = pathlib.Path(temp_dir) / 'missing-summary.json'
            summary = run_linux_gates.read_stage_summary(
                summary_path=summary_path,
                exit_code=0,
                stage_name='runtime_stack_smoke',
                stdout_text='log line\n{"runtime_api": {"health": {"backend": "java"}}}\n',
            )
        self.assertEqual('passed', summary['status'])
        self.assertEqual('stdout_json', summary['source'])
        self.assertEqual('java', summary['payload']['runtime_api']['health']['backend'])

    def test_dry_run_writes_combined_passing_summary(self):
        def fake_runner(stage_name, command, summary_path):
            self.assertIn('--cookie', command)
            self.assertIn('satoken=gate-cookie', command)
            self.assertIn('--auth-header-name', command)
            self.assertIn('access-token', command)
            self.assertIn('--auth-header-value', command)
            self.assertIn('gate-access-token', command)
            summary = {
                'status': 'passed',
                'failed_checks': 0,
                'failed_steps': 0,
                'total_checks': 3,
            }
            pathlib.Path(summary_path).parent.mkdir(parents=True, exist_ok=True)
            pathlib.Path(summary_path).write_text(json.dumps(summary), encoding='utf-8')
            return {'exit_code': 0, 'stdout': f'PASS {stage_name}', 'stderr': ''}

        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = run_linux_gates.main([
                '--base-url', 'http://127.0.0.1:8080',
                '--output-dir', temp_dir,
                '--cookie', 'satoken=gate-cookie',
                '--auth-header-name', 'access-token',
                '--auth-header-value', 'gate-access-token',
                '--dry-run',
            ], command_runner=fake_runner)

            self.assertEqual(exit_code, 0)
            output_dir = pathlib.Path(temp_dir)
            events_path = output_dir / 'events.ndjson'
            summary_path = output_dir / 'summary.json'
            self.assertTrue(events_path.exists())
            self.assertTrue(summary_path.exists())

            events = [json.loads(line) for line in events_path.read_text(encoding='utf-8').splitlines() if line.strip()]
            self.assertEqual(len(events), 3)
            self.assertEqual(events[0]['stage'], 'inference_contracts')
            self.assertTrue(all(event['passed'] for event in events))

            summary = json.loads(summary_path.read_text(encoding='utf-8'))
            self.assertEqual(summary['status'], 'passed')
            self.assertEqual(summary['failed_stages'], 0)
            self.assertEqual(summary['executed_stages'], 3)
            self.assertTrue(summary['cookie_present'])
            self.assertEqual(summary['auth_header_name'], 'access-token')
            self.assertTrue(summary['auth_header_present'])
            self.assertFalse(summary['manage_bridge'])
            self.assertNotIn('cookie', summary)
            self.assertNotIn('auth_header_value', summary)

    def test_manage_bridge_wraps_stage_execution_and_cleanup(self):
        command_calls = []
        bridge_calls = []

        def fake_runner(stage_name, command, summary_path):
            command_calls.append((stage_name, command, summary_path))
            pathlib.Path(summary_path).parent.mkdir(parents=True, exist_ok=True)
            pathlib.Path(summary_path).write_text(json.dumps({'status': 'passed'}), encoding='utf-8')
            return {'exit_code': 0, 'stdout': f'PASS {stage_name}', 'stderr': ''}

        def fake_bridge_runner(command, bridge_args):
            bridge_calls.append((command, list(bridge_args)))
            if command == 'start':
                return {'exit_code': 0, 'stdout': '', 'stderr': '', 'payload': {'status': 'started', 'pid': 1234}}
            return {'exit_code': 0, 'stdout': '', 'stderr': '', 'payload': {'status': 'stopped', 'pid': 1234}}

        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = run_linux_gates.main([
                '--base-url', 'http://127.0.0.1:18082',
                '--output-dir', temp_dir,
                '--manage-bridge',
                '--bridge-bootstrap-token', 'edge-demo-bootstrap',
            ], command_runner=fake_runner, bridge_runner=fake_bridge_runner)

            self.assertEqual(exit_code, 0)
            self.assertEqual(3, len(command_calls))
            self.assertEqual(['start', 'stop'], [item[0] for item in bridge_calls])
            self.assertTrue(any(arg == 'RUNTIME_BOOTSTRAP_TOKEN=edge-demo-bootstrap' for arg in bridge_calls[0][1]))

            summary = json.loads((pathlib.Path(temp_dir) / 'summary.json').read_text(encoding='utf-8'))
            self.assertTrue(summary['manage_bridge'])
            self.assertEqual('started', summary['bridge_start']['status'])
            self.assertEqual('stopped', summary['bridge_stop']['status'])

    def test_fail_fast_stops_after_first_failed_stage(self):
        def fake_runner(stage_name, command, summary_path):
            self.assertIn('--cookie', command)
            self.assertIn('satoken=gate-cookie', command)
            self.assertIn('--auth-header-name', command)
            self.assertIn('access-token', command)
            self.assertIn('--auth-header-value', command)
            self.assertIn('gate-access-token', command)
            failed = stage_name == 'trace_governance'
            summary = {
                'status': 'failed' if failed else 'passed',
                'failed_checks': 1 if failed else 0,
                'failed_steps': 0,
                'total_checks': 3,
            }
            pathlib.Path(summary_path).parent.mkdir(parents=True, exist_ok=True)
            pathlib.Path(summary_path).write_text(json.dumps(summary), encoding='utf-8')
            return {'exit_code': 1 if failed else 0, 'stdout': stage_name, 'stderr': ''}

        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = run_linux_gates.main([
                '--base-url', 'http://127.0.0.1:8080',
                '--output-dir', temp_dir,
                '--cookie', 'satoken=gate-cookie',
                '--auth-header-name', 'access-token',
                '--auth-header-value', 'gate-access-token',
                '--fail-fast',
                '--dry-run',
            ], command_runner=fake_runner)

            self.assertEqual(exit_code, 1)
            summary = json.loads((pathlib.Path(temp_dir) / 'summary.json').read_text(encoding='utf-8'))
            self.assertEqual(summary['status'], 'failed')
            self.assertEqual(summary['failed_stages'], 1)
            self.assertEqual(summary['executed_stages'], 2)
            self.assertTrue(summary['cookie_present'])
            self.assertEqual(summary['auth_header_name'], 'access-token')
            self.assertTrue(summary['auth_header_present'])
            self.assertNotIn('cookie', summary)
            self.assertNotIn('auth_header_value', summary)


if __name__ == '__main__':
    unittest.main()
