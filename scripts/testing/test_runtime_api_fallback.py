import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'runtime_api_fallback.py'
SPEC = importlib.util.spec_from_file_location('runtime_api_fallback', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
runtime_api_fallback = importlib.util.module_from_spec(SPEC)
sys.modules['runtime_api_fallback'] = runtime_api_fallback
SPEC.loader.exec_module(runtime_api_fallback)


class RuntimeApiFallbackTests(unittest.TestCase):
    def test_issue_token_and_authorize_roundtrip(self):
        state = runtime_api_fallback.RuntimeApiFallbackState(bootstrap_token='edge-demo-bootstrap')

        issued = runtime_api_fallback.issue_token(state, 'edge-user', 'admin', now_ms=1000)

        token = issued['token']
        self.assertTrue(runtime_api_fallback.is_authorized(state, f'Bearer {token}', now_ms=1500))
        self.assertFalse(runtime_api_fallback.is_authorized(state, 'Bearer wrong-token', now_ms=1500))

    def test_extract_snapshot_prefers_bridge_runtime_snapshot(self):
        payload = {
            'status': 'ok',
            'runtime_snapshot': {
                'streams': [{'camera_id': '1', 'play_url': 'http://127.0.0.1:1987/live/1.flv', 'push_url': 'rtmp://127.0.0.1:19350/live/1', 'ready': True}],
                'media': {'server_type': 'zlm'},
                'stream_count': 1,
                'ready_stream_count': 1,
            },
        }

        snapshot = runtime_api_fallback.extract_runtime_snapshot(payload)
        plan = runtime_api_fallback.build_inference_plan(snapshot, budget=12.5)

        self.assertEqual(1, snapshot['stream_count'])
        self.assertEqual('http://127.0.0.1:1987/live/1.flv', snapshot['streams'][0]['play_url'])
        self.assertEqual(12.5, plan['budget'])
        self.assertEqual(1, plan['ready_stream_count'])
        self.assertTrue(plan['items'][0]['ready'])

    def test_fetch_bridge_snapshot_returns_empty_structure_on_failure(self):
        snapshot = runtime_api_fallback.fetch_bridge_snapshot(
            'http://127.0.0.1:19080/health',
            http_open=lambda req, timeout=0: (_ for _ in ()).throw(RuntimeError('down')),
        )

        self.assertEqual([], snapshot['streams'])
        self.assertEqual(0, snapshot['stream_count'])
        self.assertEqual({}, snapshot['media'])


if __name__ == '__main__':
    unittest.main()
