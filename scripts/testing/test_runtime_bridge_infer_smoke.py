import importlib.util
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'runtime_bridge_infer_smoke.py'
SPEC = importlib.util.spec_from_file_location('runtime_bridge_infer_smoke', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
runtime_bridge_infer_smoke = importlib.util.module_from_spec(SPEC)
sys.modules['runtime_bridge_infer_smoke'] = runtime_bridge_infer_smoke
SPEC.loader.exec_module(runtime_bridge_infer_smoke)


class RuntimeBridgeInferSmokeTests(unittest.TestCase):
    def test_build_request_payload_uses_plugin_route_when_plugin_id_present(self):
        payload = runtime_bridge_infer_smoke.build_request_payload(
            source='rtsp://demo/stream',
            trace_id='trace-1',
            camera_id=7,
            model_id=9,
            plugin_id='yolov8n',
            timestamp_ms=12345,
        )

        self.assertEqual('trace-1', payload['trace_id'])
        self.assertEqual('rtsp://demo/stream', payload['frame']['source'])
        self.assertEqual(12345, payload['frame']['timestamp_ms'])
        self.assertTrue(payload['plugin_route']['requested'])
        self.assertEqual('yolov8n', payload['plugin_route']['plugin']['plugin_id'])

    def test_validate_response_requires_expected_plugin_and_backend(self):
        payload = {
            'backend_type': 'rk3588_rknn',
            'detections': [{'label': 'bus'}],
            'plugin': {'plugin_id': 'yolov8n', 'load_count': 1},
        }

        summary = runtime_bridge_infer_smoke.validate_response(payload, expected_plugin_id='yolov8n')

        self.assertEqual('rk3588_rknn', summary['backend_type'])
        self.assertEqual(1, summary['detection_count'])
        self.assertEqual('yolov8n', summary['plugin_id'])

    def test_validate_response_rejects_missing_plugin_id(self):
        payload = {
            'backend_type': 'rk3588_rknn',
            'detections': [],
            'plugin': {},
        }

        with self.assertRaises(RuntimeError) as ctx:
            runtime_bridge_infer_smoke.validate_response(payload, expected_plugin_id='yolov8n')

        self.assertIn('plugin_id', str(ctx.exception))


if __name__ == '__main__':
    unittest.main()
