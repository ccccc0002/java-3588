import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent / 'validate_trace_governance_flow.py'
SPEC = importlib.util.spec_from_file_location('validate_trace_governance_flow', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
validate_trace_governance_flow = importlib.util.module_from_spec(SPEC)
sys.modules['validate_trace_governance_flow'] = validate_trace_governance_flow
SPEC.loader.exec_module(validate_trace_governance_flow)


class ValidateTraceGovernanceFlowTests(unittest.TestCase):
    def test_parse_args_accepts_expected_flags(self):
        args = validate_trace_governance_flow.parse_args([
            '--base-url', 'http://127.0.0.1:8080',
            '--camera-id', '9',
            '--model-id', '4',
            '--algorithm-id', '5',
            '--video-port', '18080',
            '--source', 'test://frame',
            '--output-dir', 'tmp/out',
            '--timeout-sec', '20',
            '--fail-fast',
            '--dry-run',
        ])

        self.assertEqual(args.base_url, 'http://127.0.0.1:8080')
        self.assertEqual(args.camera_id, 9)
        self.assertEqual(args.model_id, 4)
        self.assertEqual(args.algorithm_id, 5)
        self.assertEqual(args.video_port, 18080)
        self.assertEqual(args.source, 'test://frame')
        self.assertEqual(args.output_dir, 'tmp/out')
        self.assertEqual(args.timeout_sec, 20)
        self.assertTrue(args.fail_fast)
        self.assertTrue(args.dry_run)

    def test_dry_run_writes_passing_summary(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = validate_trace_governance_flow.main([
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
            self.assertEqual(len(events), 5)
            self.assertEqual(events[0]['api'], '/stream/start(trace-flow)')
            self.assertEqual(events[-1]['api'], '/stream/stop(trace-flow)')
            self.assertTrue(all(event['passed'] for event in events))

            summary = json.loads(summary_path.read_text(encoding='utf-8'))
            self.assertEqual(summary['status'], 'passed')
            self.assertEqual(summary['failed_checks'], 0)
            self.assertEqual(summary['total_checks'], 5)
            self.assertTrue(summary['dry_run'])

    def test_fail_fast_returns_non_zero_and_records_failure(self):
        class FailingClient:
            def post_form(self, path, payload):
                if path == '/stream/start':
                    return {
                        'code': 0,
                        'data': {
                            'trace_id': 'start-trace',
                            'playUrl': 'http://example/live.flv',
                        },
                        '_http_status': 200,
                    }
                raise RuntimeError(f'post_form failure for {path}')

            def post_json(self, path, payload):
                raise RuntimeError(f'post_json failure for {path}')

            def get(self, path):
                raise RuntimeError(f'get failure for {path}')

        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = validate_trace_governance_flow.main([
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
