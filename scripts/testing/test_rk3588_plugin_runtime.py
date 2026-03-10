import importlib.util
import json
import pathlib
import sys
import tempfile
import textwrap
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'rk3588_runtime_bridge.py'
SPEC = importlib.util.spec_from_file_location('rk3588_runtime_bridge', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'failed to load module from {MODULE_PATH}')
rk3588_runtime_bridge = importlib.util.module_from_spec(SPEC)
sys.modules['rk3588_runtime_bridge'] = rk3588_runtime_bridge
SPEC.loader.exec_module(rk3588_runtime_bridge)


class FakeRuntimeClient:
    def __init__(self):
        self.plan_calls = 0

    def get_runtime_snapshot(self):
        return {'device_count': 1, 'ready_stream_count': 1, 'algorithm_count': 1}

    def get_inference_plan(self, budget):
        self.plan_calls += 1
        return {'budget': budget, 'stream_count': 1, 'ready_stream_count': 1, 'items': [{'stream_id': 'cam-1'}]}


def write_plugin(root: pathlib.Path, plugin_id: str = 'demo-plugin', include_model: bool = True) -> None:
    plugin_dir = root / plugin_id
    (plugin_dir / 'config').mkdir(parents=True, exist_ok=True)
    (plugin_dir / 'model').mkdir(parents=True, exist_ok=True)
    manifest = {
        'schema_version': 'rknn.plugin.v1',
        'plugin_id': plugin_id,
        'version': '1.0.0',
        'runtime': 'rk3588_rknn',
        'capabilities': ['inference'],
        'entrypoints': {
            'load': 'inference.py:load',
            'infer': 'inference.py:infer',
            'postprocess': 'postprocess.py:postprocess',
        },
        'assets': {
            'config': 'config/plugin.json',
            'model': 'model/demo.rknn',
        },
    }
    (plugin_dir / 'manifest.json').write_text(json.dumps(manifest), encoding='utf-8')
    (plugin_dir / 'config' / 'plugin.json').write_text(json.dumps({'execution_mode': 'mock'}), encoding='utf-8')
    if include_model:
        (plugin_dir / 'model' / 'demo.rknn').write_text('placeholder-model', encoding='utf-8')
    (plugin_dir / 'inference.py').write_text(textwrap.dedent('''
        def load(package_context):
            return {'load_count': 1, 'execution_mode': package_context.config.get('execution_mode', 'mock')}

        def infer(request_payload, runtime_plan, package_context, runtime_state):
            return {
                'candidates': list(request_payload.get('roi') or []),
                'plan_ready_stream_count': runtime_plan.get('ready_stream_count', 0),
                'load_count': runtime_state.get('load_count', 0),
            }
    '''), encoding='utf-8')
    (plugin_dir / 'postprocess.py').write_text(textwrap.dedent('''
        def postprocess(raw_outputs, request_payload, runtime_plan, package_context, runtime_state):
            detections = []
            for index, item in enumerate(raw_outputs.get('candidates', [])):
                if not isinstance(item, dict):
                    continue
                detections.append({
                    'label': str(item.get('label', f'roi-{index}')),
                    'score': float(item.get('score', 1.0)),
                    'bbox': list(item.get('bbox', [0, 0, 0, 0])),
                })
            return {
                'detections': detections,
                'plugin_meta': {
                    'load_count': runtime_state.get('load_count', 0),
                    'plan_ready_stream_count': raw_outputs.get('plan_ready_stream_count', 0),
                },
            }
    '''), encoding='utf-8')


class PluginRuntimeTests(unittest.TestCase):
    def test_repo_plugin_inventory_includes_yolov8n_package(self):
        root = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'plugins'
        manager = rk3588_runtime_bridge.PluginPackageManager(root, default_plugin_id='yolov8n')

        inventory = manager.inventory()
        package = manager.resolve({})
        plugin_ids = {item['plugin_id'] for item in inventory['plugins']}

        self.assertIn('yolov8n', plugin_ids)
        self.assertEqual('yolov8n', inventory['default_plugin_id'])
        self.assertNotIn('yolov8n', inventory['errors'])
        self.assertEqual('yolov8n', package.context.plugin_id)
        self.assertEqual('config/plugin.json', package.context.manifest['assets']['config'])

    def test_plugin_manager_discovers_default_plugin_and_executes_once_loaded_state(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write_plugin(root)
            manager = rk3588_runtime_bridge.PluginPackageManager(root, default_plugin_id='demo-plugin')

            request_payload = {
                'trace_id': 'trace-plugin',
                'camera_id': 1,
                'model_id': 2,
                'frame': {'source': 'test://frame'},
                'roi': [{'label': 'person', 'score': 0.8, 'bbox': [1, 2, 3, 4]}],
            }
            package = manager.resolve(request_payload)
            first = manager.execute(package, request_payload, {'ready_stream_count': 1})
            second = manager.execute(package, request_payload, {'ready_stream_count': 2})

            self.assertEqual(package.context.plugin_id, 'demo-plugin')
            self.assertEqual(first['detections'][0]['label'], 'person')
            self.assertEqual(first['plugin_meta']['load_count'], 1)
            self.assertEqual(second['plugin_meta']['load_count'], 1)
            self.assertEqual(second['plugin_meta']['plan_ready_stream_count'], 2)
            self.assertEqual(first['alerts'], [])
            self.assertEqual(first['events'], [])

    def test_plugin_manager_records_invalid_plugin_errors(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write_plugin(root, plugin_id='broken-plugin', include_model=False)
            manager = rk3588_runtime_bridge.PluginPackageManager(root, default_plugin_id='broken-plugin')

            inventory = manager.inventory()

            self.assertIn('broken-plugin', inventory['errors'])
            with self.assertRaises(rk3588_runtime_bridge.PluginRuntimeError):
                manager.resolve({'plugin_id': 'broken-plugin'})

    def test_service_executes_requested_plugin_route(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write_plugin(root)
            manager = rk3588_runtime_bridge.PluginPackageManager(root)
            service = rk3588_runtime_bridge.RuntimeBridgeService(
                runtime_client=FakeRuntimeClient(),
                token_provider=None,
                plugin_manager=manager,
                decode_mode='stub',
            )

            status_code, payload = service.handle_infer({
                'trace_id': 'trace-routed',
                'camera_id': 5,
                'model_id': 9,
                'frame': {'source': 'test://frame'},
                'roi': [{'label': 'vehicle', 'score': 0.6, 'bbox': [10, 20, 30, 40]}],
                'plugin_route': {'requested': True, 'plugin': {'plugin_id': 'demo-plugin'}},
            })

            self.assertEqual(status_code, 200)
            self.assertEqual(payload['plugin']['plugin_id'], 'demo-plugin')
            self.assertEqual(payload['detections'][0]['label'], 'vehicle')
            self.assertEqual(payload['plugin']['load_count'], 1)

    def test_service_rejects_unknown_requested_plugin(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write_plugin(root)
            manager = rk3588_runtime_bridge.PluginPackageManager(root)
            service = rk3588_runtime_bridge.RuntimeBridgeService(
                runtime_client=FakeRuntimeClient(),
                token_provider=None,
                plugin_manager=manager,
            )

            status_code, payload = service.handle_infer({
                'trace_id': 'trace-missing',
                'camera_id': 7,
                'model_id': 3,
                'frame': {'source': 'test://frame'},
                'plugin_id': 'missing-plugin',
            })

            self.assertEqual(status_code, 503)
            self.assertEqual(payload['error_code'], 'I5002')
            self.assertIn('missing-plugin', payload['message'])


if __name__ == '__main__':
    unittest.main()
