import importlib.util
import json
import pathlib
import sys
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'runtime_stack_smoke.py'
SPEC = importlib.util.spec_from_file_location('runtime_stack_smoke', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
runtime_stack_smoke = importlib.util.module_from_spec(SPEC)
sys.modules['runtime_stack_smoke'] = runtime_stack_smoke
SPEC.loader.exec_module(runtime_stack_smoke)


class FakeResponse:
    def __init__(self, status, payload=b''):
        self.status = status
        self._payload = payload

    def read(self, amount=-1):
        if amount is None or amount < 0:
            return self._payload
        return self._payload[:amount]

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False


class RuntimeStackSmokeTests(unittest.TestCase):
    def test_issue_runtime_token_uses_bootstrap_header(self):
        requests = []

        def fake_open(req, timeout=0):
            requests.append(req)
            payload = json.dumps({'success': True, 'data': {'token': 'token-1'}}).encode('utf-8')
            return FakeResponse(200, payload)

        token_payload = runtime_stack_smoke.issue_runtime_token(
            runtime_api_url='http://127.0.0.1:18081',
            bootstrap_token='edge-demo-bootstrap',
            http_open=fake_open,
        )

        self.assertEqual('token-1', token_payload['data']['token'])
        self.assertEqual('edge-demo-bootstrap', requests[0].headers['X-bootstrap-token'])

    def test_verify_play_url_reads_stream_bytes(self):
        result = runtime_stack_smoke.verify_play_url(
            'http://127.0.0.1:1987/live/1.live.flv',
            http_open=lambda req, timeout=0: FakeResponse(200, b'FLV\x01stream-data'),
        )

        self.assertEqual(200, result['http_status'])
        self.assertGreaterEqual(result['bytes_read'], 4)
        self.assertTrue(result['readable'])

    def test_run_stack_smoke_combines_runtime_api_bridge_and_zlm_checks(self):
        with mock.patch.object(runtime_stack_smoke, 'issue_runtime_token', return_value={'data': {'token': 'token-1'}}) as token_mock, \
             mock.patch.object(runtime_stack_smoke, 'get_runtime_snapshot', return_value={'data': {'streams': [{'play_url': 'http://127.0.0.1:1987/live/1.live.flv'}], 'stream_count': 1}}) as snapshot_mock, \
             mock.patch.object(runtime_stack_smoke, 'get_inference_plan', return_value={'data': {'ready_stream_count': 1}}) as plan_mock, \
             mock.patch.object(runtime_stack_smoke, 'get_bridge_health', return_value={'status': 'ok'}) as bridge_health_mock, \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'post_infer', return_value={'backend_type': 'rk3588_rknn', 'plugin': {'plugin_id': 'yolov8n'}, 'detections': [{'label': 'person'}], 'alerts': []}) as infer_mock, \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'validate_response', return_value={'backend_type': 'rk3588_rknn', 'plugin_id': 'yolov8n', 'detection_count': 1, 'alert_count': 0, 'labels': ['person']}) as validate_mock, \
             mock.patch.object(runtime_stack_smoke, 'verify_play_url', return_value={'http_status': 200, 'bytes_read': 8, 'readable': True}) as play_mock:
            result = runtime_stack_smoke.run_stack_smoke(
                runtime_api_url='http://127.0.0.1:18081',
                bridge_url='http://127.0.0.1:19080',
                bootstrap_token='edge-demo-bootstrap',
                plugin_id='yolov8n',
                source='test://frame',
            )

        self.assertEqual('token-1', result['runtime_api']['token']['token'])
        self.assertEqual(1, result['runtime_api']['snapshot']['stream_count'])
        self.assertEqual(1, result['runtime_api']['plan']['ready_stream_count'])
        self.assertEqual('ok', result['bridge']['health']['status'])
        self.assertEqual(1, result['bridge']['infer']['detection_count'])
        self.assertTrue(result['zlm']['play_check']['readable'])
        token_mock.assert_called_once()
        snapshot_mock.assert_called_once()
        plan_mock.assert_called_once()
        bridge_health_mock.assert_called_once()
        infer_mock.assert_called_once()
        validate_mock.assert_called_once()
        play_mock.assert_called_once_with('http://127.0.0.1:1987/live/1.live.flv')


if __name__ == '__main__':
    unittest.main()
