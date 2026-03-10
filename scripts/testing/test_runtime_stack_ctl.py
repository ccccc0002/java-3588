import importlib.util
import pathlib
import sys
import tempfile
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'runtime_stack_ctl.py'
SPEC = importlib.util.spec_from_file_location('runtime_stack_ctl', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
runtime_stack_ctl = importlib.util.module_from_spec(SPEC)
sys.modules['runtime_stack_ctl'] = runtime_stack_ctl
SPEC.loader.exec_module(runtime_stack_ctl)


class RuntimeStackControllerTests(unittest.TestCase):
    def test_start_stack_starts_zlm_runtime_api_bridge_then_worker(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = runtime_stack_ctl.StackControllerConfig(repo_root=repo_root)
            call_order = []

            with mock.patch.object(
                runtime_stack_ctl.zlm_runtime_ctl,
                'start_runtime',
                side_effect=lambda cfg: call_order.append(('zlm', cfg)) or {'status': 'started', 'pid': 1111},
            ) as start_zlm_mock, mock.patch.object(
                runtime_stack_ctl.runtime_api_ctl,
                'start_runtime_api',
                side_effect=lambda cfg, extra_env=None: call_order.append(('runtime_api', cfg, extra_env)) or {'status': 'started', 'pid': 2222},
            ) as start_runtime_api_mock, mock.patch.object(
                runtime_stack_ctl.runtime_bridge_ctl,
                'start_bridge',
                side_effect=lambda cfg, extra_env=None: call_order.append(('bridge', cfg, extra_env)) or {'status': 'started', 'pid': 3333},
            ) as start_bridge_mock, mock.patch.object(
                runtime_stack_ctl.media_worker_ctl,
                'start_worker',
                side_effect=lambda cfg, extra_env=None: call_order.append(('worker', cfg, extra_env)) or {'status': 'started', 'pid': 4444},
            ) as start_worker_mock:
                result = runtime_stack_ctl.start_stack(
                    config,
                    runtime_api_env={'RUNTIME_BOOTSTRAP_TOKEN': 'edge-demo-bootstrap'},
                    bridge_env={'DEFAULT_PLUGIN_ID': 'yolov8n'},
                    worker_env={'RUNTIME_BOOTSTRAP_TOKEN': 'edge-demo-bootstrap'},
                )

            self.assertEqual('started', result['status'])
            self.assertEqual(['zlm', 'runtime_api', 'bridge', 'worker'], [item[0] for item in call_order])
            self.assertEqual(repo_root, call_order[0][1].repo_root)
            self.assertEqual('edge-demo-bootstrap', call_order[1][2]['RUNTIME_BOOTSTRAP_TOKEN'])
            self.assertEqual('yolov8n', call_order[2][2]['DEFAULT_PLUGIN_ID'])
            self.assertEqual('edge-demo-bootstrap', call_order[3][2]['RUNTIME_BOOTSTRAP_TOKEN'])
            start_zlm_mock.assert_called_once()
            start_runtime_api_mock.assert_called_once()
            start_bridge_mock.assert_called_once()
            start_worker_mock.assert_called_once()

    def test_start_stack_short_circuits_when_runtime_api_is_unhealthy(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = runtime_stack_ctl.StackControllerConfig(repo_root=repo_root)

            with mock.patch.object(
                runtime_stack_ctl.zlm_runtime_ctl,
                'start_runtime',
                return_value={'status': 'started', 'pid': 1111},
            ) as start_zlm_mock, mock.patch.object(
                runtime_stack_ctl.runtime_api_ctl,
                'start_runtime_api',
                return_value={'status': 'runtime_unhealthy', 'pid': 2222, 'health': {'http_status': 0}},
            ) as start_runtime_api_mock, mock.patch.object(
                runtime_stack_ctl.runtime_bridge_ctl,
                'start_bridge',
            ) as start_bridge_mock, mock.patch.object(
                runtime_stack_ctl.media_worker_ctl,
                'start_worker',
            ) as start_worker_mock:
                result = runtime_stack_ctl.start_stack(config)

            self.assertEqual('runtime_api_unhealthy', result['status'])
            self.assertEqual('skipped', result['bridge']['status'])
            self.assertEqual('skipped', result['worker']['status'])
            start_zlm_mock.assert_called_once()
            start_runtime_api_mock.assert_called_once()
            start_bridge_mock.assert_not_called()
            start_worker_mock.assert_not_called()

    def test_status_stack_reports_running_when_all_layers_are_running(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = runtime_stack_ctl.StackControllerConfig(repo_root=repo_root)
            with mock.patch.object(runtime_stack_ctl.zlm_runtime_ctl, 'status_runtime', return_value={'status': 'running', 'pid': 1111}) as status_zlm_mock, \
                 mock.patch.object(runtime_stack_ctl.runtime_api_ctl, 'status_runtime_api', return_value={'status': 'running'}) as status_runtime_api_mock, \
                 mock.patch.object(runtime_stack_ctl.runtime_bridge_ctl, 'status_bridge', return_value={'status': 'running', 'pid': 3333}) as status_bridge_mock, \
                 mock.patch.object(runtime_stack_ctl.media_worker_ctl, 'status_worker', return_value={'status': 'running', 'pid': 4444}) as status_worker_mock:
                result = runtime_stack_ctl.status_stack(config)

            self.assertEqual('running', result['status'])
            self.assertEqual('running', result['zlm']['status'])
            self.assertEqual('running', result['runtime_api']['status'])
            self.assertEqual('running', result['bridge']['status'])
            self.assertEqual('running', result['worker']['status'])
            status_zlm_mock.assert_called_once()
            status_runtime_api_mock.assert_called_once()
            status_bridge_mock.assert_called_once()
            status_worker_mock.assert_called_once()

    def test_stop_stack_stops_worker_bridge_runtime_api_then_zlm(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = runtime_stack_ctl.StackControllerConfig(repo_root=repo_root)
            call_order = []

            with mock.patch.object(
                runtime_stack_ctl.media_worker_ctl,
                'stop_worker',
                side_effect=lambda cfg: call_order.append('worker') or {'status': 'stopped', 'pid': 4444},
            ) as stop_worker_mock, mock.patch.object(
                runtime_stack_ctl.runtime_bridge_ctl,
                'stop_bridge',
                side_effect=lambda cfg: call_order.append('bridge') or {'status': 'stopped', 'pid': 3333},
            ) as stop_bridge_mock, mock.patch.object(
                runtime_stack_ctl.runtime_api_ctl,
                'stop_runtime_api',
                side_effect=lambda cfg: call_order.append('runtime_api') or {'status': 'stopped'},
            ) as stop_runtime_api_mock, mock.patch.object(
                runtime_stack_ctl.zlm_runtime_ctl,
                'stop_runtime',
                side_effect=lambda cfg: call_order.append('zlm') or {'status': 'stopped', 'pid': 1111},
            ) as stop_zlm_mock:
                result = runtime_stack_ctl.stop_stack(config)

            self.assertEqual('stopped', result['status'])
            self.assertEqual(['worker', 'bridge', 'runtime_api', 'zlm'], call_order)
            stop_worker_mock.assert_called_once()
            stop_bridge_mock.assert_called_once()
            stop_runtime_api_mock.assert_called_once()
            stop_zlm_mock.assert_called_once()

    def test_restart_stack_blocks_when_stop_is_partial(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = runtime_stack_ctl.StackControllerConfig(repo_root=repo_root)
            partial_stop = {
                'status': 'partial',
                'worker': {'status': 'stop_pending_exit'},
                'bridge': {'status': 'stopped'},
                'runtime_api': {'status': 'stopped'},
                'zlm': {'status': 'stopped'},
            }
            with mock.patch.object(runtime_stack_ctl, 'stop_stack', return_value=partial_stop) as stop_stack_mock, \
                 mock.patch.object(runtime_stack_ctl, 'start_stack') as start_stack_mock:
                result = runtime_stack_ctl.restart_stack(
                    config,
                    runtime_api_env={'RUNTIME_BOOTSTRAP_TOKEN': 'demo'},
                    bridge_env={'DEFAULT_PLUGIN_ID': 'yolov8n'},
                    worker_env={'RUNTIME_BOOTSTRAP_TOKEN': 'demo'},
                )

            self.assertEqual('restart_blocked', result['status'])
            self.assertEqual('stop_pending_exit', result['worker']['status'])
            stop_stack_mock.assert_called_once_with(config)
            start_stack_mock.assert_not_called()

    def test_parse_args_supports_all_env_groups(self):
        args = runtime_stack_ctl.parse_args([
            'start',
            '--runtime-api-env', 'RUNTIME_BOOTSTRAP_TOKEN=edge-demo-bootstrap',
            '--bridge-env', 'DEFAULT_PLUGIN_ID=yolov8n',
            '--worker-env', 'RUNTIME_BOOTSTRAP_TOKEN=edge-demo-bootstrap',
        ])
        config, runtime_api_env, bridge_env, worker_env = runtime_stack_ctl.build_config(args)

        self.assertTrue(config.repo_root.exists())
        self.assertEqual('edge-demo-bootstrap', runtime_api_env['RUNTIME_BOOTSTRAP_TOKEN'])
        self.assertEqual('yolov8n', bridge_env['DEFAULT_PLUGIN_ID'])
        self.assertEqual('edge-demo-bootstrap', worker_env['RUNTIME_BOOTSTRAP_TOKEN'])


if __name__ == '__main__':
    unittest.main()
