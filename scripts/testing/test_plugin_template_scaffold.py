import json
import pathlib
import unittest


class PluginTemplateScaffoldTests(unittest.TestCase):
    def test_basic_detector_template_contains_required_files_and_contract_keys(self):
        root = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'plugin_templates' / 'basic_detector'
        manifest_path = root / 'manifest.json'
        config_path = root / 'config' / 'plugin.json'
        inference_path = root / 'inference.py'
        postprocess_path = root / 'postprocess.py'
        model_path = root / 'model' / 'placeholder.rknn'

        self.assertTrue(manifest_path.exists())
        self.assertTrue(config_path.exists())
        self.assertTrue(inference_path.exists())
        self.assertTrue(postprocess_path.exists())
        self.assertTrue(model_path.exists())

        manifest = json.loads(manifest_path.read_text(encoding='utf-8-sig'))
        config = json.loads(config_path.read_text(encoding='utf-8-sig'))

        self.assertEqual(manifest['schema_version'], 'rknn.plugin.v1')
        self.assertEqual(manifest['entrypoints']['load'], 'inference.py:load')
        self.assertEqual(manifest['entrypoints']['postprocess'], 'postprocess.py:postprocess')
        self.assertIn('template', manifest['capabilities'])

        self.assertIn('class_names', config)
        self.assertIn('label_aliases_zh', config)
        self.assertIn('enabled_labels', config)
        self.assertIn('alert_labels', config)
        self.assertEqual(config['alert_event_type'], 'vision.alert')


if __name__ == '__main__':
    unittest.main()
