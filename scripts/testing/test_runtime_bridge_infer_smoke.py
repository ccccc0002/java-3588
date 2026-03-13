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
    def test_validate_response_extracts_plan_summary_metrics(self):
        payload = {
            'backend_type': 'rk3588_rknn',
            'plugin': {'plugin_id': 'yolov8n'},
            'detections': [{'label': 'person'}],
            'alerts': [{'label': 'person'}],
            'latency_ms': 23,
            'plan_summary': {
                'telemetry_status': 'degraded',
                'telemetry_error': 'scheduler_summary_failed',
                'strategy_source': 'scheduler_feedback',
                'recommended_frame_stride': 3,
                'suggested_min_dispatch_ms': 5200,
                'concurrency_pressure': 1.8,
                'concurrency_level': 4,
            },
        }

        summary = runtime_bridge_infer_smoke.validate_response(payload, expected_plugin_id='yolov8n')

        self.assertEqual(summary['backend_type'], 'rk3588_rknn')
        self.assertEqual(summary['plugin_id'], 'yolov8n')
        self.assertEqual(summary['detection_count'], 1)
        self.assertEqual(summary['alert_count'], 1)
        self.assertEqual(summary['labels'], ['person'])
        self.assertEqual(summary['plan_summary']['telemetry_status'], 'degraded')
        self.assertEqual(summary['plan_summary']['telemetry_error'], 'scheduler_summary_failed')
        self.assertEqual(summary['plan_summary']['strategy_source'], 'scheduler_feedback')
        self.assertEqual(summary['plan_summary']['recommended_frame_stride'], 3)
        self.assertEqual(summary['plan_summary']['suggested_min_dispatch_ms'], 5200)
        self.assertEqual(summary['plan_summary']['concurrency_pressure'], 1.8)
        self.assertEqual(summary['plan_summary']['concurrency_level'], 4)

    def test_validate_response_uses_defaults_when_plan_summary_missing(self):
        payload = {
            'backend_type': 'rk3588_rknn',
            'plugin': {'plugin_id': 'yolov8n'},
            'detections': [],
            'alerts': [],
            'latency_ms': 10,
        }

        summary = runtime_bridge_infer_smoke.validate_response(payload, expected_plugin_id='yolov8n')

        self.assertEqual(summary['plan_summary']['telemetry_status'], 'ok')
        self.assertEqual(summary['plan_summary']['telemetry_error'], '')
        self.assertEqual(summary['plan_summary']['strategy_source'], 'scheduler_feedback')
        self.assertEqual(summary['plan_summary']['recommended_frame_stride'], 1)
        self.assertEqual(summary['plan_summary']['suggested_min_dispatch_ms'], 1000)
        self.assertEqual(summary['plan_summary']['concurrency_pressure'], 1.0)
        self.assertEqual(summary['plan_summary']['concurrency_level'], 0)

    def test_validate_response_raises_on_plugin_mismatch(self):
        payload = {
            'backend_type': 'rk3588_rknn',
            'plugin': {'plugin_id': 'roi-passthrough'},
            'detections': [],
            'alerts': [],
            'latency_ms': 12,
        }
        with self.assertRaises(RuntimeError):
            runtime_bridge_infer_smoke.validate_response(payload, expected_plugin_id='yolov8n')


if __name__ == '__main__':
    unittest.main()
