import importlib.util
import pathlib
import sys
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'plugins' / 'roi-passthrough'
INFERENCE_MODULE_PATH = ROOT / 'inference.py'
POSTPROCESS_MODULE_PATH = ROOT / 'postprocess.py'


def load_module(module_name: str, path: pathlib.Path):
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f'failed to load module from {path}')
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


roi_inference = load_module('rk3588_roi_inference', INFERENCE_MODULE_PATH)
roi_postprocess = load_module('rk3588_roi_postprocess', POSTPROCESS_MODULE_PATH)


class FakeContext:
    def __init__(self, root_dir: pathlib.Path, config: dict, model_path: pathlib.Path):
        self.root_dir = root_dir
        self.config = config
        self.model_path = model_path


class RoiPassthroughPluginTests(unittest.TestCase):
    def test_load_exposes_label_settings_from_config(self):
        root = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'plugins' / 'roi-passthrough'
        context = FakeContext(
            root_dir=root,
            config={
                'execution_mode': 'mock',
                'default_label': 'person',
                'class_names': ['person', 'bus'],
                'label_aliases_zh': {'person': '人员', 'bus': '公交车'},
                'enabled_labels': ['person', '公交车'],
                'alert_labels': ['公交车'],
            },
            model_path=root / 'model' / 'placeholder.rknn',
        )

        state = roi_inference.load(context)

        self.assertEqual(state['labels'], ['person', 'bus'])
        self.assertEqual(state['label_aliases_zh']['bus'], '公交车')
        self.assertEqual(state['enabled_labels'], {'person', '公交车'})
        self.assertEqual(state['alert_labels'], {'公交车'})

    def test_postprocess_uses_shared_alert_contract(self):
        runtime_state = {
            'execution_mode': 'mock',
            'labels': ['person', 'bus'],
            'label_aliases_zh': {'person': '人员', 'bus': '公交车'},
            'enabled_class_ids': set(),
            'enabled_labels': {'person', '公交车'},
            'alert_labels': {'公交车'},
        }
        raw_outputs = {
            'mode': 'mock',
            'candidates': [
                {'label': 'person', 'score': 0.8, 'bbox': [1, 2, 3, 4], 'class_id': 0},
                {'label': 'bus', 'score': 0.9, 'bbox': [5, 6, 7, 8], 'class_id': 1},
                {'label': 'car', 'score': 0.7, 'bbox': [9, 10, 11, 12], 'class_id': 2},
            ],
            'source_meta': {'source_kind': 'mock', 'resolved_source': 'roi://source'},
            'plan_ready_stream_count': 1,
        }
        request_payload = {
            'trace_id': 'trace-roi-plugin',
            'camera_id': 11,
            'model_id': 22,
            'frame': {'source': 'test://frame', 'timestamp_ms': 12345},
        }
        context = FakeContext(
            root_dir=ROOT,
            config={},
            model_path=ROOT / 'model' / 'placeholder.rknn',
        )

        result = roi_postprocess.postprocess(raw_outputs, request_payload, {}, context, runtime_state)

        self.assertEqual([item['label'] for item in result['detections']], ['person', 'bus'])
        self.assertEqual([item['label_zh'] for item in result['detections']], ['人员', '公交车'])
        self.assertEqual([item['alert'] for item in result['detections']], [False, True])
        self.assertEqual([item['label'] for item in result['alerts']], ['bus'])
        self.assertEqual(result['events'][0]['event_type'], 'vision.alert')
        self.assertEqual(result['events'][0]['trace_id'], 'trace-roi-plugin')
        self.assertEqual(result['events'][0]['label_zh'], '公交车')


if __name__ == '__main__':
    unittest.main()
