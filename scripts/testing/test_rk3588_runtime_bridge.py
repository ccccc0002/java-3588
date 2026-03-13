import importlib.util
import pathlib
import sys
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'rk3588_runtime_bridge.py'
SPEC = importlib.util.spec_from_file_location('rk3588_runtime_bridge', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
rk3588_runtime_bridge = importlib.util.module_from_spec(SPEC)
sys.modules['rk3588_runtime_bridge'] = rk3588_runtime_bridge
SPEC.loader.exec_module(rk3588_runtime_bridge)


class FakeRuntimeClient:
    def __init__(self):
        self.snapshot_calls = 0
        self.plan_calls = 0
        self.token_calls = 0
        self.snapshot_payload = {'device_count': 2, 'ready_stream_count': 1, 'algorithm_count': 3}
        self.plan_payload = {
            'stream_count': 2,
            'ready_stream_count': 1,
            'telemetry_status': 'ok',
            'telemetry_error': '',
            'throttle_hint': {
                'recommended_frame_stride': 3,
                'suggested_min_dispatch_ms': 5200,
                'concurrency_pressure': 1.8,
                'concurrency_level': 4,
                'strategy_source': 'scheduler_feedback',
            },
            'items': [{'stream_id': 'cam-1'}],
        }

    def issue_token(self):
        self.token_calls += 1
        return 'token-123'

    def get_runtime_snapshot(self):
        self.snapshot_calls += 1
        return dict(self.snapshot_payload)

    def get_inference_plan(self, budget):
        self.plan_calls += 1
        return {'budget': budget, **self.plan_payload}


class FakePluginManager:
    def has_plugins(self):
        return True

    def inventory(self):
        return {'plugins': [{'plugin_id': 'yolov8n'}], 'errors': {}}

    def resolve(self, request_payload):
        return 'yolov8n'

    def execute(self, package, request_payload, plan):
        return {
            'detections': [{'label': 'person', 'label_zh': '人员', 'alert': False}],
            'alerts': [{'label': 'bus', 'label_zh': '公交车', 'alert': True}],
            'events': [{'event_type': 'vision.alert', 'label': 'bus', 'label_zh': '公交车'}],
            'plugin_meta': {'plugin_id': 'yolov8n', 'alert_label_count': 1},
            'attributes': {'detection_count': 1, 'alert_detection_count': 1},
            'latency_ms': 23,
        }



class FakeFailingRuntimeClient:
    def get_runtime_snapshot(self):
        raise rk3588_runtime_bridge.RuntimeBridgeError('runtime unavailable', status_code=502)

    def get_inference_plan(self, budget):
        raise rk3588_runtime_bridge.RuntimeBridgeError('runtime unavailable', status_code=502)



class FakeResponse:
    def __init__(self, status, payload):
        self.status = status
        self._payload = payload

    def read(self):
        import json
        return json.dumps(self._payload).encode('utf-8')

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False


class RuntimeApiClientTests(unittest.TestCase):
    def test_runtime_snapshot_retries_when_cached_token_is_invalid(self):
        calls = []
        token_counter = {'count': 0}

        def http_open(req, timeout=None):
            calls.append((req.full_url, req.get_method(), req.headers.get('Authorization', '')))
            if req.full_url.endswith('/api/v1/runtime/snapshot'):
                authorization = req.headers.get('Authorization', '')
                if authorization == 'Bearer stale-token':
                    return FakeResponse(200, {
                        'success': False,
                        'data': None,
                        'error': {'code': 'invalid_token', 'message': 'token invalid or expired'},
                        'meta': {},
                    })
                if authorization == 'Bearer refreshed-token':
                    return FakeResponse(200, {
                        'success': True,
                        'data': {'device_count': 1, 'ready_stream_count': 1},
                        'error': None,
                        'meta': {},
                    })
            if req.full_url.endswith('/api/v1/auth/token'):
                token_counter['count'] += 1
                return FakeResponse(200, {
                    'success': True,
                    'data': {'token': 'refreshed-token'},
                    'error': None,
                    'meta': {},
                })
            raise AssertionError(f'unexpected request: {req.full_url}')

        client = rk3588_runtime_bridge.RuntimeApiClient(
            rk3588_runtime_bridge.RuntimeBridgeConfig(runtime_url='http://runtime.example'),
            http_open=http_open,
        )
        provider = rk3588_runtime_bridge.TokenProvider(client)
        provider._cached_token = 'stale-token'
        client.set_token_provider(provider)

        payload = client.get_runtime_snapshot()

        self.assertEqual({'device_count': 1, 'ready_stream_count': 1}, payload)
        self.assertEqual(1, token_counter['count'])
        self.assertEqual(
            [
                ('http://runtime.example/api/v1/runtime/snapshot', 'GET', 'Bearer stale-token'),
                ('http://runtime.example/api/v1/auth/token', 'POST', ''),
                ('http://runtime.example/api/v1/runtime/snapshot', 'GET', 'Bearer refreshed-token'),
            ],
            calls,
        )

    def test_runtime_snapshot_raises_when_auth_error_cannot_be_recovered(self):
        def http_open(req, timeout=None):
            if req.full_url.endswith('/api/v1/runtime/snapshot'):
                return FakeResponse(200, {
                    'success': False,
                    'data': None,
                    'error': {'code': 'invalid_token', 'message': 'token invalid or expired'},
                    'meta': {},
                })
            raise AssertionError(f'unexpected request: {req.full_url}')

        client = rk3588_runtime_bridge.RuntimeApiClient(
            rk3588_runtime_bridge.RuntimeBridgeConfig(
                runtime_url='http://runtime.example',
                runtime_token='fixed-token',
            ),
            http_open=http_open,
        )

        with self.assertRaises(rk3588_runtime_bridge.RuntimeBridgeError) as context:
            client.get_runtime_snapshot()

        self.assertEqual(401, context.exception.status_code)
        self.assertIn('token invalid or expired', str(context.exception))

class RuntimeBridgeServiceTests(unittest.TestCase):
    def test_token_provider_caches_token(self):
        client = FakeRuntimeClient()
        provider = rk3588_runtime_bridge.TokenProvider(client)

        first = provider.get_token()
        second = provider.get_token()

        self.assertEqual(first, 'token-123')
        self.assertEqual(second, 'token-123')
        self.assertEqual(client.token_calls, 1)

    def test_health_returns_contract_payload_from_runtime_snapshot(self):
        service = rk3588_runtime_bridge.RuntimeBridgeService(
            runtime_client=FakeRuntimeClient(),
            token_provider=None,
            bridge_version='0.2.0',
            decode_mode='stub',
        )

        status_code, payload = service.handle_health()

        self.assertEqual(status_code, 200)
        self.assertEqual(payload['status'], 'ok')
        self.assertEqual(payload['runtime'], 'rknn')
        self.assertEqual(payload['decode'], 'bridge:stub')
        self.assertEqual(payload['version'], '0.2.0')
        self.assertEqual(payload['runtime_snapshot']['device_count'], 2)

    def test_infer_returns_contract_payload_and_plan_summary(self):
        service = rk3588_runtime_bridge.RuntimeBridgeService(
            runtime_client=FakeRuntimeClient(),
            token_provider=None,
            bridge_version='0.2.0',
            decode_mode='stub',
            default_plan_budget=12.5,
        )
        request = {
            'trace_id': 'trace-1',
            'camera_id': 101,
            'model_id': 5,
            'frame': {'source': 'test://frame', 'timestamp_ms': 1234567890},
            'roi': [],
        }

        status_code, payload = service.handle_infer(request)

        self.assertEqual(status_code, 200)
        self.assertEqual(payload['trace_id'], 'trace-1')
        self.assertEqual(payload['camera_id'], 101)
        self.assertEqual(payload['backend_type'], 'rk3588_rknn')
        self.assertEqual(payload['detections'], [])
        self.assertEqual(payload['plan_summary']['ready_stream_count'], 1)
        self.assertEqual(payload['plan_summary']['budget'], 12.5)
        self.assertEqual(payload['plan_summary']['telemetry_status'], 'ok')
        self.assertEqual(payload['plan_summary']['telemetry_error'], '')
        self.assertEqual(payload['plan_summary']['recommended_frame_stride'], 3)
        self.assertEqual(payload['plan_summary']['suggested_min_dispatch_ms'], 5200)
        self.assertEqual(payload['plan_summary']['strategy_source'], 'scheduler_feedback')
        self.assertEqual(payload['plan_summary']['concurrency_level'], 4)
        self.assertEqual(payload['plan_summary']['concurrency_pressure'], 1.8)

    def test_infer_propagates_plugin_alerts(self):
        service = rk3588_runtime_bridge.RuntimeBridgeService(
            runtime_client=FakeRuntimeClient(),
            token_provider=None,
            plugin_manager=FakePluginManager(),
        )
        request = {
            'trace_id': 'trace-plugin',
            'camera_id': 1,
            'model_id': 1,
            'frame': {'source': 'test://frame'},
        }

        status_code, payload = service.handle_infer(request)

        self.assertEqual(status_code, 200)
        self.assertEqual(payload['detections'][0]['label_zh'], '人员')
        self.assertEqual(payload['alerts'][0]['label'], 'bus')
        self.assertEqual(payload['events'][0]['event_type'], 'vision.alert')
        self.assertEqual(payload['plugin']['alert_label_count'], 1)
        self.assertEqual(payload['attributes']['alert_detection_count'], 1)

    def test_infer_can_echo_roi_as_detection_placeholders(self):
        service = rk3588_runtime_bridge.RuntimeBridgeService(
            runtime_client=FakeRuntimeClient(),
            token_provider=None,
            decode_mode='echo-roi',
        )
        request = {
            'trace_id': 'trace-roi',
            'camera_id': 102,
            'model_id': 7,
            'frame': {'source': 'test://frame'},
            'roi': [{'label': 'person', 'score': 0.8, 'bbox': [1, 2, 3, 4]}],
        }

        status_code, payload = service.handle_infer(request)

        self.assertEqual(status_code, 200)
        self.assertEqual(len(payload['detections']), 1)
        self.assertEqual(payload['detections'][0]['label'], 'person')
        self.assertEqual(payload['detections'][0]['bbox'], [1, 2, 3, 4])

    def test_infer_rejects_missing_frame_source(self):
        service = rk3588_runtime_bridge.RuntimeBridgeService(
            runtime_client=FakeRuntimeClient(),
            token_provider=None,
        )

        status_code, payload = service.handle_infer({'trace_id': 'trace-bad', 'camera_id': 9, 'model_id': 2, 'frame': {}})

        self.assertEqual(status_code, 400)
        self.assertEqual(payload['error_code'], 'I4001')
        self.assertIn('frame.source', payload['message'])

    def test_health_falls_back_to_offline_snapshot_when_runtime_is_unavailable(self):
        service = rk3588_runtime_bridge.RuntimeBridgeService(
            runtime_client=FakeFailingRuntimeClient(),
            token_provider=None,
            plugin_manager=FakePluginManager(),
        )

        status_code, payload = service.handle_health()

        self.assertEqual(status_code, 200)
        self.assertEqual(payload['status'], 'ok')
        self.assertEqual(payload['runtime_fallback']['mode'], 'offline')
        self.assertEqual(payload['runtime_snapshot']['device_count'], 0)

    def test_infer_uses_offline_plan_when_runtime_is_unavailable(self):
        service = rk3588_runtime_bridge.RuntimeBridgeService(
            runtime_client=FakeFailingRuntimeClient(),
            token_provider=None,
            plugin_manager=FakePluginManager(),
        )
        request = {
            'trace_id': 'trace-offline',
            'camera_id': 1,
            'model_id': 1,
            'frame': {'source': 'test://frame'},
        }

        status_code, payload = service.handle_infer(request)

        self.assertEqual(status_code, 200)
        self.assertEqual(payload['runtime_fallback']['mode'], 'offline')
        self.assertEqual(payload['plan_summary']['ready_stream_count'], 0)
        self.assertEqual(payload['plan_summary']['fallback']['mode'], 'offline')
        self.assertEqual(payload['plan_summary']['telemetry_status'], 'degraded')
        self.assertEqual(payload['plan_summary']['telemetry_error'], 'runtime_offline')
        self.assertEqual(payload['plan_summary']['recommended_frame_stride'], 1)
        self.assertEqual(payload['plan_summary']['suggested_min_dispatch_ms'], 1000)
        self.assertEqual(payload['plan_summary']['strategy_source'], 'offline_fallback')
        self.assertEqual(payload['plan_summary']['concurrency_level'], 0)
        self.assertEqual(payload['plan_summary']['concurrency_pressure'], 1.0)
        self.assertEqual(payload['plugin']['plugin_id'], 'yolov8n')


if __name__ == '__main__':
    unittest.main()
