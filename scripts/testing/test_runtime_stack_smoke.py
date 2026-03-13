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
        with mock.patch.object(runtime_stack_smoke, 'get_runtime_health', return_value={'data': {'status': 'ok', 'backend': 'python_fallback'}}) as runtime_health_mock, \
             mock.patch.object(runtime_stack_smoke, 'issue_runtime_token', return_value={'data': {'token': 'token-1'}}) as token_mock, \
             mock.patch.object(runtime_stack_smoke, 'get_runtime_snapshot', return_value={'data': {
                 'streams': [{'play_url': 'http://127.0.0.1:1987/live/1.live.flv'}],
                 'stream_count': 1,
                 'ready_stream_count': 1,
                 'telemetry_status': 'degraded',
                 'telemetry_error': 'scheduler_summary_failed',
                 'throttle_hint': {
                     'recommended_frame_stride': 2,
                     'suggested_min_dispatch_ms': 2400,
                     'concurrency_pressure': 1.6,
                     'concurrency_level': 3,
                     'strategy_source': 'scheduler_feedback',
                 },
             }}) as snapshot_mock, \
             mock.patch.object(runtime_stack_smoke, 'get_inference_plan', return_value={'data': {
                 'ready_stream_count': 1,
                 'stream_count': 1,
                 'telemetry_status': 'ok',
                 'telemetry_error': '',
                 'throttle_hint': {
                     'recommended_frame_stride': 3,
                     'suggested_min_dispatch_ms': 5200,
                     'concurrency_pressure': 1.8,
                     'concurrency_level': 4,
                     'strategy_source': 'scheduler_feedback',
                 },
             }}) as plan_mock, \
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
                expected_runtime_api_backend='python_fallback',
            )

        self.assertEqual('python_fallback', result['runtime_api']['health']['backend'])
        self.assertEqual('token-1', result['runtime_api']['token']['token'])
        self.assertEqual(1, result['runtime_api']['snapshot']['stream_count'])
        self.assertEqual(1, result['runtime_api']['plan']['ready_stream_count'])
        self.assertEqual('degraded', result['runtime_api']['snapshot']['telemetry']['status'])
        self.assertEqual('scheduler_summary_failed', result['runtime_api']['snapshot']['telemetry']['error'])
        self.assertEqual(2, result['runtime_api']['snapshot']['throttle_hint']['recommended_frame_stride'])
        self.assertEqual(2400, result['runtime_api']['snapshot']['throttle_hint']['suggested_min_dispatch_ms'])
        self.assertEqual(1.6, result['runtime_api']['snapshot']['throttle_hint']['concurrency_pressure'])
        self.assertEqual(3, result['runtime_api']['snapshot']['throttle_hint']['concurrency_level'])
        self.assertEqual('ok', result['runtime_api']['plan']['telemetry']['status'])
        self.assertEqual('', result['runtime_api']['plan']['telemetry']['error'])
        self.assertEqual(3, result['runtime_api']['plan']['throttle_hint']['recommended_frame_stride'])
        self.assertEqual(5200, result['runtime_api']['plan']['throttle_hint']['suggested_min_dispatch_ms'])
        self.assertEqual(1.8, result['runtime_api']['plan']['throttle_hint']['concurrency_pressure'])
        self.assertEqual(4, result['runtime_api']['plan']['throttle_hint']['concurrency_level'])
        self.assertEqual('any', result['acceptance_gates']['expected_snapshot_telemetry_status'])
        self.assertEqual('any', result['acceptance_gates']['expected_plan_telemetry_status'])
        self.assertEqual(1, result['acceptance_gates']['actual']['snapshot_ready_stream_count'])
        self.assertEqual(1, result['acceptance_gates']['actual']['plan_ready_stream_count'])
        self.assertEqual(1.8, result['acceptance_gates']['actual']['plan_concurrency_pressure'])
        self.assertEqual(5200, result['acceptance_gates']['actual']['plan_suggested_min_dispatch_ms'])
        self.assertEqual('ok', result['bridge']['health']['status'])
        self.assertEqual(1, result['bridge']['infer']['detection_count'])
        self.assertTrue(result['zlm']['play_check']['readable'])
        runtime_health_mock.assert_called_once()
        token_mock.assert_called_once()
        snapshot_mock.assert_called_once()
        plan_mock.assert_called_once()
        bridge_health_mock.assert_called_once()
        infer_mock.assert_called_once()
        validate_mock.assert_called_once()
        play_mock.assert_called_once_with('http://127.0.0.1:1987/live/1.live.flv')

    def test_run_stack_smoke_uses_telemetry_defaults_when_missing(self):
        with mock.patch.object(runtime_stack_smoke, 'get_runtime_health', return_value={'data': {'status': 'ok', 'backend': 'java'}}), \
             mock.patch.object(runtime_stack_smoke, 'issue_runtime_token', return_value={'data': {'token': 'token-1'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_runtime_snapshot', return_value={'data': {'streams': [{'play_url': 'http://127.0.0.1:1987/live/1.live.flv'}], 'stream_count': 1}}), \
             mock.patch.object(runtime_stack_smoke, 'get_inference_plan', return_value={'data': {'ready_stream_count': 1}}), \
             mock.patch.object(runtime_stack_smoke, 'get_bridge_health', return_value={'status': 'ok'}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'post_infer', return_value={'backend_type': 'rk3588_rknn', 'plugin': {'plugin_id': 'yolov8n'}, 'detections': [], 'alerts': []}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'validate_response', return_value={'backend_type': 'rk3588_rknn', 'plugin_id': 'yolov8n', 'detection_count': 0, 'alert_count': 0, 'labels': []}), \
             mock.patch.object(runtime_stack_smoke, 'verify_play_url', return_value={'http_status': 200, 'bytes_read': 8, 'readable': True}):
            result = runtime_stack_smoke.run_stack_smoke(
                runtime_api_url='http://127.0.0.1:18081',
                bridge_url='http://127.0.0.1:19080',
                bootstrap_token='edge-demo-bootstrap',
                plugin_id='yolov8n',
                source='test://frame',
            )

        self.assertEqual('ok', result['runtime_api']['snapshot']['telemetry']['status'])
        self.assertEqual('', result['runtime_api']['snapshot']['telemetry']['error'])
        self.assertEqual(1, result['runtime_api']['snapshot']['throttle_hint']['recommended_frame_stride'])
        self.assertEqual(1000, result['runtime_api']['snapshot']['throttle_hint']['suggested_min_dispatch_ms'])
        self.assertEqual(1.0, result['runtime_api']['snapshot']['throttle_hint']['concurrency_pressure'])
        self.assertEqual(0, result['runtime_api']['snapshot']['throttle_hint']['concurrency_level'])
        self.assertEqual('scheduler_feedback', result['runtime_api']['snapshot']['throttle_hint']['strategy_source'])
        self.assertEqual(0.0, result['acceptance_gates']['max_plan_concurrency_pressure'])
        self.assertEqual(0, result['acceptance_gates']['max_plan_suggested_min_dispatch_ms'])


    def test_run_stack_smoke_rejects_unexpected_runtime_backend(self):
        with mock.patch.object(runtime_stack_smoke, 'get_runtime_health', return_value={'data': {'status': 'ok', 'backend': 'java'}}):
            with self.assertRaisesRegex(RuntimeError, 'runtime_api backend mismatch'):
                runtime_stack_smoke.run_stack_smoke(
                    runtime_api_url='http://127.0.0.1:18081',
                    bridge_url='http://127.0.0.1:19080',
                    bootstrap_token='edge-demo-bootstrap',
                    expected_runtime_api_backend='python_fallback',
                )

    def test_run_stack_smoke_rejects_when_snapshot_telemetry_status_mismatch(self):
        with mock.patch.object(runtime_stack_smoke, 'get_runtime_health', return_value={'data': {'status': 'ok', 'backend': 'java'}}), \
             mock.patch.object(runtime_stack_smoke, 'issue_runtime_token', return_value={'data': {'token': 'token-1'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_runtime_snapshot', return_value={'data': {
                 'streams': [{'play_url': 'http://127.0.0.1:1987/live/1.live.flv'}],
                 'stream_count': 1,
                 'telemetry_status': 'degraded',
             }}), \
             mock.patch.object(runtime_stack_smoke, 'get_inference_plan', return_value={'data': {'ready_stream_count': 1, 'telemetry_status': 'ok'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_bridge_health', return_value={'status': 'ok'}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'post_infer', return_value={'backend_type': 'rk3588_rknn', 'plugin': {'plugin_id': 'yolov8n'}, 'detections': [], 'alerts': []}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'validate_response', return_value={'backend_type': 'rk3588_rknn', 'plugin_id': 'yolov8n', 'detection_count': 0, 'alert_count': 0, 'labels': []}), \
             mock.patch.object(runtime_stack_smoke, 'verify_play_url', return_value={'http_status': 200, 'bytes_read': 8, 'readable': True}):
            with self.assertRaisesRegex(RuntimeError, 'snapshot telemetry status mismatch'):
                runtime_stack_smoke.run_stack_smoke(
                    runtime_api_url='http://127.0.0.1:18081',
                    bridge_url='http://127.0.0.1:19080',
                    bootstrap_token='edge-demo-bootstrap',
                    plugin_id='yolov8n',
                    source='test://frame',
                    expected_snapshot_telemetry_status='ok',
                )

    def test_run_stack_smoke_rejects_when_plan_telemetry_status_mismatch(self):
        with mock.patch.object(runtime_stack_smoke, 'get_runtime_health', return_value={'data': {'status': 'ok', 'backend': 'java'}}), \
             mock.patch.object(runtime_stack_smoke, 'issue_runtime_token', return_value={'data': {'token': 'token-1'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_runtime_snapshot', return_value={'data': {
                 'streams': [{'play_url': 'http://127.0.0.1:1987/live/1.live.flv'}],
                 'stream_count': 1,
                 'telemetry_status': 'ok',
             }}), \
             mock.patch.object(runtime_stack_smoke, 'get_inference_plan', return_value={'data': {'ready_stream_count': 1, 'telemetry_status': 'degraded'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_bridge_health', return_value={'status': 'ok'}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'post_infer', return_value={'backend_type': 'rk3588_rknn', 'plugin': {'plugin_id': 'yolov8n'}, 'detections': [], 'alerts': []}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'validate_response', return_value={'backend_type': 'rk3588_rknn', 'plugin_id': 'yolov8n', 'detection_count': 0, 'alert_count': 0, 'labels': []}), \
             mock.patch.object(runtime_stack_smoke, 'verify_play_url', return_value={'http_status': 200, 'bytes_read': 8, 'readable': True}):
            with self.assertRaisesRegex(RuntimeError, 'plan telemetry status mismatch'):
                runtime_stack_smoke.run_stack_smoke(
                    runtime_api_url='http://127.0.0.1:18081',
                    bridge_url='http://127.0.0.1:19080',
                    bootstrap_token='edge-demo-bootstrap',
                    plugin_id='yolov8n',
                    source='test://frame',
                    expected_plan_telemetry_status='ok',
                )

    def test_run_stack_smoke_rejects_when_plan_pressure_exceeds_limit(self):
        with mock.patch.object(runtime_stack_smoke, 'get_runtime_health', return_value={'data': {'status': 'ok', 'backend': 'java'}}), \
             mock.patch.object(runtime_stack_smoke, 'issue_runtime_token', return_value={'data': {'token': 'token-1'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_runtime_snapshot', return_value={'data': {
                 'streams': [{'play_url': 'http://127.0.0.1:1987/live/1.live.flv'}],
                 'stream_count': 1,
                 'telemetry_status': 'ok',
             }}), \
             mock.patch.object(runtime_stack_smoke, 'get_inference_plan', return_value={'data': {
                 'ready_stream_count': 1,
                 'telemetry_status': 'ok',
                 'throttle_hint': {'concurrency_pressure': 2.2},
             }}), \
             mock.patch.object(runtime_stack_smoke, 'get_bridge_health', return_value={'status': 'ok'}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'post_infer', return_value={'backend_type': 'rk3588_rknn', 'plugin': {'plugin_id': 'yolov8n'}, 'detections': [], 'alerts': []}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'validate_response', return_value={'backend_type': 'rk3588_rknn', 'plugin_id': 'yolov8n', 'detection_count': 0, 'alert_count': 0, 'labels': []}), \
             mock.patch.object(runtime_stack_smoke, 'verify_play_url', return_value={'http_status': 200, 'bytes_read': 8, 'readable': True}):
            with self.assertRaisesRegex(RuntimeError, 'plan concurrency pressure exceeds threshold'):
                runtime_stack_smoke.run_stack_smoke(
                    runtime_api_url='http://127.0.0.1:18081',
                    bridge_url='http://127.0.0.1:19080',
                    bootstrap_token='edge-demo-bootstrap',
                    plugin_id='yolov8n',
                    source='test://frame',
                    max_plan_concurrency_pressure=1.5,
                )

    def test_run_stack_smoke_rejects_when_plan_dispatch_exceeds_limit(self):
        with mock.patch.object(runtime_stack_smoke, 'get_runtime_health', return_value={'data': {'status': 'ok', 'backend': 'java'}}), \
             mock.patch.object(runtime_stack_smoke, 'issue_runtime_token', return_value={'data': {'token': 'token-1'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_runtime_snapshot', return_value={'data': {
                 'streams': [{'play_url': 'http://127.0.0.1:1987/live/1.live.flv'}],
                 'stream_count': 1,
                 'telemetry_status': 'ok',
             }}), \
             mock.patch.object(runtime_stack_smoke, 'get_inference_plan', return_value={'data': {
                 'ready_stream_count': 1,
                 'telemetry_status': 'ok',
                 'throttle_hint': {'suggested_min_dispatch_ms': 6400},
             }}), \
             mock.patch.object(runtime_stack_smoke, 'get_bridge_health', return_value={'status': 'ok'}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'post_infer', return_value={'backend_type': 'rk3588_rknn', 'plugin': {'plugin_id': 'yolov8n'}, 'detections': [], 'alerts': []}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'validate_response', return_value={'backend_type': 'rk3588_rknn', 'plugin_id': 'yolov8n', 'detection_count': 0, 'alert_count': 0, 'labels': []}), \
             mock.patch.object(runtime_stack_smoke, 'verify_play_url', return_value={'http_status': 200, 'bytes_read': 8, 'readable': True}):
            with self.assertRaisesRegex(RuntimeError, 'plan min dispatch exceeds threshold'):
                runtime_stack_smoke.run_stack_smoke(
                    runtime_api_url='http://127.0.0.1:18081',
                    bridge_url='http://127.0.0.1:19080',
                    bootstrap_token='edge-demo-bootstrap',
                    plugin_id='yolov8n',
                    source='test://frame',
                    max_plan_suggested_min_dispatch_ms=5000,
                )

    def test_run_stack_smoke_rejects_when_snapshot_ready_stream_below_minimum(self):
        with mock.patch.object(runtime_stack_smoke, 'get_runtime_health', return_value={'data': {'status': 'ok', 'backend': 'java'}}), \
             mock.patch.object(runtime_stack_smoke, 'issue_runtime_token', return_value={'data': {'token': 'token-1'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_runtime_snapshot', return_value={'data': {
                 'streams': [{'play_url': 'http://127.0.0.1:1987/live/1.live.flv'}],
                 'stream_count': 3,
                 'ready_stream_count': 1,
                 'telemetry_status': 'ok',
             }}), \
             mock.patch.object(runtime_stack_smoke, 'get_inference_plan', return_value={'data': {'ready_stream_count': 2, 'telemetry_status': 'ok'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_bridge_health', return_value={'status': 'ok'}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'post_infer', return_value={'backend_type': 'rk3588_rknn', 'plugin': {'plugin_id': 'yolov8n'}, 'detections': [], 'alerts': []}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'validate_response', return_value={'backend_type': 'rk3588_rknn', 'plugin_id': 'yolov8n', 'detection_count': 0, 'alert_count': 0, 'labels': []}), \
             mock.patch.object(runtime_stack_smoke, 'verify_play_url', return_value={'http_status': 200, 'bytes_read': 8, 'readable': True}):
            with self.assertRaisesRegex(RuntimeError, 'snapshot ready stream count below minimum'):
                runtime_stack_smoke.run_stack_smoke(
                    runtime_api_url='http://127.0.0.1:18081',
                    bridge_url='http://127.0.0.1:19080',
                    bootstrap_token='edge-demo-bootstrap',
                    plugin_id='yolov8n',
                    source='test://frame',
                    min_snapshot_ready_stream_count=2,
                )

    def test_run_stack_smoke_rejects_when_plan_ready_stream_below_minimum(self):
        with mock.patch.object(runtime_stack_smoke, 'get_runtime_health', return_value={'data': {'status': 'ok', 'backend': 'java'}}), \
             mock.patch.object(runtime_stack_smoke, 'issue_runtime_token', return_value={'data': {'token': 'token-1'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_runtime_snapshot', return_value={'data': {
                 'streams': [{'play_url': 'http://127.0.0.1:1987/live/1.live.flv'}],
                 'stream_count': 3,
                 'ready_stream_count': 2,
                 'telemetry_status': 'ok',
             }}), \
             mock.patch.object(runtime_stack_smoke, 'get_inference_plan', return_value={'data': {'ready_stream_count': 1, 'telemetry_status': 'ok'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_bridge_health', return_value={'status': 'ok'}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'post_infer', return_value={'backend_type': 'rk3588_rknn', 'plugin': {'plugin_id': 'yolov8n'}, 'detections': [], 'alerts': []}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'validate_response', return_value={'backend_type': 'rk3588_rknn', 'plugin_id': 'yolov8n', 'detection_count': 0, 'alert_count': 0, 'labels': []}), \
             mock.patch.object(runtime_stack_smoke, 'verify_play_url', return_value={'http_status': 200, 'bytes_read': 8, 'readable': True}):
            with self.assertRaisesRegex(RuntimeError, 'plan ready stream count below minimum'):
                runtime_stack_smoke.run_stack_smoke(
                    runtime_api_url='http://127.0.0.1:18081',
                    bridge_url='http://127.0.0.1:19080',
                    bootstrap_token='edge-demo-bootstrap',
                    plugin_id='yolov8n',
                    source='test://frame',
                    min_plan_ready_stream_count=2,
                )

    def test_run_stack_smoke_handles_invalid_ready_stream_values(self):
        with mock.patch.object(runtime_stack_smoke, 'get_runtime_health', return_value={'data': {'status': 'ok', 'backend': 'java'}}), \
             mock.patch.object(runtime_stack_smoke, 'issue_runtime_token', return_value={'data': {'token': 'token-1'}}), \
             mock.patch.object(runtime_stack_smoke, 'get_runtime_snapshot', return_value={'data': {
                 'streams': [{'play_url': 'http://127.0.0.1:1987/live/1.live.flv'}],
                 'stream_count': 'invalid',
                 'ready_stream_count': 'bad',
                 'telemetry_status': 'ok',
             }}), \
             mock.patch.object(runtime_stack_smoke, 'get_inference_plan', return_value={'data': {
                 'ready_stream_count': 'NaN',
                 'stream_count': 'none',
                 'telemetry_status': 'ok',
             }}), \
             mock.patch.object(runtime_stack_smoke, 'get_bridge_health', return_value={'status': 'ok'}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'post_infer', return_value={'backend_type': 'rk3588_rknn', 'plugin': {'plugin_id': 'yolov8n'}, 'detections': [], 'alerts': []}), \
             mock.patch.object(runtime_stack_smoke.runtime_bridge_infer_smoke, 'validate_response', return_value={'backend_type': 'rk3588_rknn', 'plugin_id': 'yolov8n', 'detection_count': 0, 'alert_count': 0, 'labels': []}), \
             mock.patch.object(runtime_stack_smoke, 'verify_play_url', return_value={'http_status': 200, 'bytes_read': 8, 'readable': True}):
            with self.assertRaisesRegex(RuntimeError, 'snapshot ready stream count below minimum'):
                runtime_stack_smoke.run_stack_smoke(
                    runtime_api_url='http://127.0.0.1:18081',
                    bridge_url='http://127.0.0.1:19080',
                    bootstrap_token='edge-demo-bootstrap',
                    plugin_id='yolov8n',
                    source='test://frame',
                    min_snapshot_ready_stream_count=1,
                )


if __name__ == '__main__':
    unittest.main()
