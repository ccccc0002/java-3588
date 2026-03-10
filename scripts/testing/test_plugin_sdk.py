import importlib.util
import json
import pathlib
import sys
import tempfile
import types
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'plugin_sdk.py'
SPEC = importlib.util.spec_from_file_location('plugin_sdk', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
plugin_sdk = importlib.util.module_from_spec(SPEC)
sys.modules['plugin_sdk'] = plugin_sdk
SPEC.loader.exec_module(plugin_sdk)


class PluginSdkTests(unittest.TestCase):
    def test_load_detection_config_merges_labels_aliases_and_filters(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root_dir = pathlib.Path(temp_dir)
            labels_path = root_dir / 'labels.txt'
            aliases_path = root_dir / 'aliases.json'
            labels_path.write_text('person\nbicycle\nbus\n', encoding='utf-8')
            aliases_path.write_text(json.dumps({'person': '人员', '2': '公交车'}, ensure_ascii=False), encoding='utf-8')
            package_context = types.SimpleNamespace(
                root_dir=root_dir,
                config={
                    'labels_path': 'labels.txt',
                    'label_aliases_zh_path': 'aliases.json',
                    'label_aliases_zh': {'bus': '大巴车'},
                    'enabled_class_ids': ['1', '2'],
                    'enabled_labels': ['人员'],
                    'alert_labels': ['公交车', '2'],
                    'alert_event_type': 'vision.custom.alert',
                },
            )

            result = plugin_sdk.load_detection_config(package_context)

        self.assertEqual(['person', 'bicycle', 'bus'], result['labels'])
        self.assertEqual('人员', result['label_aliases_zh']['person'])
        self.assertEqual('公交车', result['label_aliases_zh']['2'])
        self.assertEqual('大巴车', result['label_aliases_zh']['bus'])
        self.assertEqual({1, 2}, result['enabled_class_ids'])
        self.assertEqual({'人员'}, result['enabled_labels'])
        self.assertEqual({'公交车', '2'}, result['alert_labels'])
        self.assertEqual('vision.custom.alert', result['event_type'])

    def test_finalize_detections_filters_by_enabled_and_alert_labels(self):
        package_context = types.SimpleNamespace(root_dir=pathlib.Path('.'), plugin_id='yolov8n', version='1.0.0')
        runtime_state = {
            'labels': ['person', 'bicycle', 'bus'],
            'label_aliases_zh': {'person': '人员', 'bus': '公交车'},
            'enabled_class_ids': set(),
            'enabled_labels': {'人员', '公交车'},
            'alert_labels': {'公交车'},
            'event_type': 'vision.alert',
        }
        detections = [
            {'class_id': 0, 'score': 0.9, 'bbox': [0, 0, 10, 10]},
            {'class_id': 1, 'score': 0.8, 'bbox': [10, 10, 20, 20]},
            {'class_id': 2, 'score': 0.7, 'bbox': [20, 20, 30, 30]},
        ]
        request_payload = {'trace_id': 'trace-1', 'camera_id': 11, 'frame': {'timestamp_ms': 12345}}
        source_meta = {'source_kind': 'stream', 'resolved_source': 'rtsp://demo/1'}

        result = plugin_sdk.finalize_detections(
            detections=detections,
            request_payload=request_payload,
            package_context=package_context,
            runtime_state=runtime_state,
            source_meta=source_meta,
        )

        self.assertEqual(['person', 'bus'], [item['label'] for item in result['detections']])
        self.assertEqual(['人员', '公交车'], [item['label_zh'] for item in result['detections']])
        self.assertEqual([False, True], [item['alert'] for item in result['detections']])
        self.assertEqual(['bus'], [item['label'] for item in result['alerts']])
        self.assertEqual(2, result['attributes']['detection_count'])
        self.assertEqual(1, result['attributes']['alert_detection_count'])
        self.assertEqual('vision.alert', result['events'][0]['event_type'])
        self.assertEqual('公交车', result['events'][0]['label_zh'])


if __name__ == '__main__':
    unittest.main()
