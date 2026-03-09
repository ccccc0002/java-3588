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
        self.plan_payload = {'stream_count': 2, 'ready_stream_count': 1, 'items': [{'stream_id': 'cam-1'}]}

    def issue_token(self):
        self.token_calls += 1
        return 'token-123'

    def get_runtime_snapshot(self):
        self.snapshot_calls += 1
        return dict(self.snapshot_payload)

    def get_inference_plan(self, budget):
        self.plan_calls += 1
        return {'budget': budget, **self.plan_payload}


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


if __name__ == '__main__':
    unittest.main()
