import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent / 'validate_rollout_readiness.py'
SPEC = importlib.util.spec_from_file_location('validate_rollout_readiness', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
validate_rollout_readiness = importlib.util.module_from_spec(SPEC)
sys.modules['validate_rollout_readiness'] = validate_rollout_readiness
SPEC.loader.exec_module(validate_rollout_readiness)


class ValidateRolloutReadinessTests(unittest.TestCase):
    def test_parse_args_accepts_expected_flags(self):
        args = validate_rollout_readiness.parse_args([
            '--base-url', 'http://127.0.0.1:8080',
            '--primary-camera-id', '9',
            '--secondary-camera-id', '10',
            '--expected-backend-type', 'rk3588_rknn',
            '--expected-override-source', 'camera_override',
            '--output-dir', 'tmp/out',
            '--timeout-sec', '20',
            '--fail-fast',
            '--dry-run',
        ])

        self.assertEqual(args.base_url, 'http://127.0.0.1:8080')
        self.assertEqual(args.primary_camera_id, 9)
        self.assertEqual(args.secondary_camera_id, 10)
        self.assertEqual(args.expected_backend_type, 'rk3588_rknn')
        self.assertEqual(args.expected_override_source, 'camera_override')
        self.assertEqual(args.output_dir, 'tmp/out')
        self.assertEqual(args.timeout_sec, 20)
        self.assertTrue(args.fail_fast)
        self.assertTrue(args.dry_run)

    def test_dry_run_writes_passing_summary(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = validate_rollout_readiness.main([
                '--base-url', 'http://127.0.0.1:8080',
                '--output-dir', temp_dir,
                '--dry-run',
            ])

            self.assertEqual(exit_code, 0)
            output_dir = pathlib.Path(temp_dir)
            events_path = output_dir / 'events.ndjson'
            summary_path = output_dir / 'summary.json'
            self.assertTrue(events_path.exists())
            self.assertTrue(summary_path.exists())

            events = [json.loads(line) for line in events_path.read_text(encoding='utf-8').splitlines() if line.strip()]
            self.assertEqual(len(events), 3)
            self.assertEqual(events[0]['api'], '/api/inference/route(primary-gray-check)')
            self.assertEqual(events[-1]['api'], '/api/inference/route/batch(rollback-fallback)')
            self.assertTrue(all(event['passed'] for event in events))

            summary = json.loads(summary_path.read_text(encoding='utf-8'))
            self.assertEqual(summary['status'], 'passed')
            self.assertEqual(summary['failed_checks'], 0)
            self.assertEqual(summary['total_checks'], 3)
            self.assertTrue(summary['dry_run'])

    def test_fail_fast_returns_non_zero_and_records_failure(self):
        class FailingClient:
            def post_json(self, path, payload):
                if path == '/api/inference/route':
                    return {
                        'code': 0,
                        'data': {
                            'trace_id': 'route-trace',
                            'backend_type': 'rk3588_rknn',
                            'global_backend_type': 'rk3588_rknn',
                            'override_source': 'camera_override',
                        },
                        '_http_status': 200,
                    }
                raise RuntimeError(f'post_json failure for {path}')

            def get(self, path):
                raise RuntimeError(f'get failure for {path}')

            def post_form(self, path, payload):
                raise RuntimeError(f'post_form failure for {path}')

        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = validate_rollout_readiness.main([
                '--base-url', 'http://127.0.0.1:8080',
                '--output-dir', temp_dir,
                '--fail-fast',
            ], client=FailingClient())

            self.assertEqual(exit_code, 1)
            summary = json.loads((pathlib.Path(temp_dir) / 'summary.json').read_text(encoding='utf-8'))
            self.assertEqual(summary['status'], 'failed')
            self.assertEqual(summary['failed_checks'], 1)
            self.assertEqual(summary['total_checks'], 2)

            events = [json.loads(line) for line in (pathlib.Path(temp_dir) / 'events.ndjson').read_text(encoding='utf-8').splitlines() if line.strip()]
            self.assertFalse(events[-1]['passed'])


if __name__ == '__main__':
    unittest.main()
