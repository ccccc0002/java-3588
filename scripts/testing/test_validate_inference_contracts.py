import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent / 'validate_inference_contracts.py'
SPEC = importlib.util.spec_from_file_location('validate_inference_contracts', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
validate_inference_contracts = importlib.util.module_from_spec(SPEC)
sys.modules['validate_inference_contracts'] = validate_inference_contracts
SPEC.loader.exec_module(validate_inference_contracts)


class ValidateInferenceContractsTests(unittest.TestCase):
    def test_parse_args_accepts_expected_flags(self):
        args = validate_inference_contracts.parse_args([
            '--base-url', 'http://127.0.0.1:8080',
            '--camera-id', '9',
            '--model-id', '4',
            '--algorithm-id', '5',
            '--source', 'test://frame',
            '--expected-backend-type', 'rk3588_rknn',
            '--expected-override-source', 'camera_override',
            '--output-dir', 'tmp/out',
            '--cookie', 'satoken=test-cookie',
            '--auth-header-name', 'access-token',
            '--auth-header-value', 'test-access-token',
            '--timeout-sec', '20',
            '--fail-fast',
            '--dry-run',
        ])

        self.assertEqual(args.base_url, 'http://127.0.0.1:8080')
        self.assertEqual(args.camera_id, 9)
        self.assertEqual(args.model_id, 4)
        self.assertEqual(args.algorithm_id, 5)
        self.assertEqual(args.source, 'test://frame')
        self.assertEqual(args.expected_backend_type, 'rk3588_rknn')
        self.assertEqual(args.expected_override_source, 'camera_override')
        self.assertEqual(args.output_dir, 'tmp/out')
        self.assertEqual(args.cookie, 'satoken=test-cookie')
        self.assertEqual(args.auth_header_name, 'access-token')
        self.assertEqual(args.auth_header_value, 'test-access-token')
        self.assertEqual(args.timeout_sec, 20)
        self.assertTrue(args.fail_fast)
        self.assertTrue(args.dry_run)

    def test_api_client_get_includes_auth_header(self):
        client = validate_inference_contracts.ApiClient('http://127.0.0.1:8080', auth_header_name='access-token', auth_header_value='test-access-token')
        captured = {}

        def fake_send(request):
            captured['headers'] = {key.lower(): value for key, value in request.header_items()}
            return {'code': 0, '_http_status': 200}

        client._send = fake_send
        client.get('/api/inference/health')

        self.assertEqual(captured['headers'].get('access-token'), 'test-access-token')

    def test_dry_run_writes_passing_summary(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = validate_inference_contracts.main([
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
            self.assertGreaterEqual(len(events), 10)
            self.assertEqual(events[0]['api'], '/api/inference/health')
            self.assertTrue(all(event['passed'] for event in events))

            summary = json.loads(summary_path.read_text(encoding='utf-8'))
            self.assertEqual(summary['status'], 'passed')
            self.assertEqual(summary['failed_checks'], 0)
            self.assertEqual(summary['total_checks'], len(events))
            self.assertTrue(summary['dry_run'])

    def test_fail_fast_returns_non_zero_and_records_failure(self):
        class FailingClient:
            def get(self, path):
                if path == '/api/inference/health':
                    return {'code': 0, 'data': {'trace_id': 'trace-health', 'backend_type': 'rk3588_rknn', 'upstream': {'circuit_open': False}}, '_http_status': 200}
                raise RuntimeError(f'unexpected get failure for {path}')

            def post_json(self, path, payload):
                raise RuntimeError(f'post_json failure for {path}')

            def post_form(self, path, payload):
                raise RuntimeError(f'post_form failure for {path}')

        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = validate_inference_contracts.main([
                '--base-url', 'http://127.0.0.1:8080',
                '--output-dir', temp_dir,
                '--fail-fast',
            ], client=FailingClient())

            self.assertEqual(exit_code, 1)

            summary = json.loads((pathlib.Path(temp_dir) / 'summary.json').read_text(encoding='utf-8'))
            self.assertEqual(summary['status'], 'failed')
            self.assertEqual(summary['failed_checks'], 1)
            self.assertGreaterEqual(summary['total_checks'], 2)

            events = [json.loads(line) for line in (pathlib.Path(temp_dir) / 'events.ndjson').read_text(encoding='utf-8').splitlines() if line.strip()]
            self.assertFalse(events[-1]['passed'])


if __name__ == '__main__':
    unittest.main()
