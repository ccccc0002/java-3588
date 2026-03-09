import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent / 'run_stability_soak.py'
SPEC = importlib.util.spec_from_file_location('run_stability_soak', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
run_stability_soak = importlib.util.module_from_spec(SPEC)
sys.modules['run_stability_soak'] = run_stability_soak
SPEC.loader.exec_module(run_stability_soak)


class SoakRunnerTests(unittest.TestCase):
    def test_parse_args_accepts_expected_flags(self):
        args = run_stability_soak.parse_args([
            '--base-url', 'http://127.0.0.1:8080',
            '--camera-id', '9',
            '--model-id', '4',
            '--algorithm-id', '5',
            '--duration-sec', '30',
            '--interval-sec', '2',
            '--max-iterations', '3',
            '--output-dir', 'tmp/out',
            '--cookie', 'satoken=test-cookie',
            '--auth-header-name', 'access-token',
            '--auth-header-value', 'test-access-token',
            '--fail-fast',
            '--dry-run',
        ])

        self.assertEqual(args.base_url, 'http://127.0.0.1:8080')
        self.assertEqual(args.camera_id, 9)
        self.assertEqual(args.model_id, 4)
        self.assertEqual(args.algorithm_id, 5)
        self.assertEqual(args.duration_sec, 30)
        self.assertEqual(args.interval_sec, 2)
        self.assertEqual(args.max_iterations, 3)
        self.assertEqual(args.output_dir, 'tmp/out')
        self.assertEqual(args.cookie, 'satoken=test-cookie')
        self.assertEqual(args.auth_header_name, 'access-token')
        self.assertEqual(args.auth_header_value, 'test-access-token')
        self.assertTrue(args.fail_fast)
        self.assertTrue(args.dry_run)

    def test_dry_run_writes_summary_and_events(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = run_stability_soak.main([
                '--base-url', 'http://127.0.0.1:8080',
                '--duration-sec', '1',
                '--interval-sec', '0',
                '--max-iterations', '1',
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
            self.assertTrue(events)
            self.assertEqual(events[0]['step'], 'stream_start')
            self.assertEqual(events[-1]['step'], 'stream_stop')

            summary = json.loads(summary_path.read_text(encoding='utf-8'))
            self.assertEqual(summary['iterations_completed'], 1)
            self.assertEqual(summary['status'], 'passed')
            self.assertEqual(summary['failed_steps'], 0)
            self.assertEqual(summary['dry_run'], True)

    def test_api_client_post_form_includes_auth_header(self):
        client = run_stability_soak.ApiClient('http://127.0.0.1:8080', auth_header_name='access-token', auth_header_value='test-access-token')
        captured = {}

        def fake_send(request):
            captured['headers'] = {key.lower(): value for key, value in request.header_items()}
            return {'code': 0, '_http_status': 200}

        client._send = fake_send
        client.post_form('/stream/start', {'cameraId': 1})

        self.assertEqual(captured['headers'].get('access-token'), 'test-access-token')

    def test_fail_fast_returns_non_zero_and_records_failure(self):
        class FailingClient:
            def __init__(self):
                self.calls = []

            def post_json(self, path, payload):
                self.calls.append((path, payload))
                if path == '/api/inference/dispatch':
                    raise RuntimeError('dispatch failed')
                if path == '/stream/stop':
                    return {
                        'code': 0,
                        'data': {
                            'trace_id': 'stop-trace',
                        },
                        '_http_status': 200,
                    }
                return {'code': 0, 'data': {'trace_id': 'replay-trace'}, '_http_status': 200}

            def post_form(self, path, payload):
                self.calls.append((path, payload))
                return {
                    'code': 0,
                    'data': {
                        'trace_id': 'stream-trace',
                        'playUrl': 'http://example/live.flv',
                    },
                    '_http_status': 200,
                }

        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = run_stability_soak.main([
                '--base-url', 'http://127.0.0.1:8080',
                '--duration-sec', '10',
                '--interval-sec', '0',
                '--max-iterations', '1',
                '--output-dir', temp_dir,
                '--fail-fast',
            ], client=FailingClient())

            self.assertEqual(exit_code, 1)

            summary = json.loads((pathlib.Path(temp_dir) / 'summary.json').read_text(encoding='utf-8'))
            self.assertEqual(summary['status'], 'failed')
            self.assertEqual(summary['failed_steps'], 1)
            self.assertEqual(summary['iterations_completed'], 0)


if __name__ == '__main__':
    unittest.main()
