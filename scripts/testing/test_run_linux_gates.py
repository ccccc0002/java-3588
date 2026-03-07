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
            '--output-dir', 'tmp/out',
            '--timeout-sec', '20',
            '--include-soak',
            '--soak-duration-sec', '60',
            '--soak-interval-sec', '5',
            '--soak-max-iterations', '2',
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
        self.assertEqual(args.output_dir, 'tmp/out')
        self.assertEqual(args.timeout_sec, 20)
        self.assertTrue(args.include_soak)
        self.assertEqual(args.soak_duration_sec, 60)
        self.assertEqual(args.soak_interval_sec, 5)
        self.assertEqual(args.soak_max_iterations, 2)
        self.assertTrue(args.fail_fast)
        self.assertTrue(args.dry_run)

    def test_dry_run_writes_combined_passing_summary(self):
        def fake_runner(stage_name, command, summary_path):
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

    def test_fail_fast_stops_after_first_failed_stage(self):
        def fake_runner(stage_name, command, summary_path):
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
                '--fail-fast',
                '--dry-run',
            ], command_runner=fake_runner)

            self.assertEqual(exit_code, 1)
            summary = json.loads((pathlib.Path(temp_dir) / 'summary.json').read_text(encoding='utf-8'))
            self.assertEqual(summary['status'], 'failed')
            self.assertEqual(summary['failed_stages'], 1)
            self.assertEqual(summary['executed_stages'], 2)


if __name__ == '__main__':
    unittest.main()
