import importlib.util
import pathlib
import sys
import tempfile
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'runtime_bridge_ctl.py'
SPEC = importlib.util.spec_from_file_location('runtime_bridge_ctl', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
runtime_bridge_ctl = importlib.util.module_from_spec(SPEC)
sys.modules['runtime_bridge_ctl'] = runtime_bridge_ctl
SPEC.loader.exec_module(runtime_bridge_ctl)


class RuntimeBridgeControllerTests(unittest.TestCase):
    def test_start_launches_detached_process_and_writes_pid(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            runtime_dir = repo_root / 'runtime'
            config = runtime_bridge_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=runtime_dir,
                pid_path=runtime_dir / 'runtime-bridge.pid',
                log_path=runtime_dir / 'runtime-bridge.log',
                health_url='http://127.0.0.1:19080/health',
                wait_seconds=1.0,
                poll_interval=0.01,
            )
            process = mock.Mock(pid=4321)

            with mock.patch.object(runtime_bridge_ctl, 'read_pid', return_value=None), \
                 mock.patch.object(runtime_bridge_ctl, 'is_process_running', return_value=False), \
                 mock.patch.object(runtime_bridge_ctl, 'wait_for_health', return_value={'http_status': 200, 'payload': {'status': 'ok'}}) as wait_mock, \
                 mock.patch.object(runtime_bridge_ctl.subprocess, 'Popen', return_value=process) as popen_mock:
                result = runtime_bridge_ctl.start_bridge(config, extra_env={'RUNTIME_BOOTSTRAP_TOKEN': 'demo'})

            self.assertEqual('started', result['status'])
            self.assertEqual(4321, result['pid'])
            self.assertEqual('ok', result['health']['payload']['status'])
            self.assertEqual('4321', config.pid_path.read_text(encoding='utf-8').strip())
            self.assertTrue(config.log_path.exists())
            wait_mock.assert_called_once()
            popen_args, popen_kwargs = popen_mock.call_args
            self.assertEqual(['bash', str(config.run_script)], popen_args[0])
            self.assertEqual(str(repo_root), popen_kwargs['cwd'])
            self.assertTrue(popen_kwargs['start_new_session'])
            self.assertEqual('demo', popen_kwargs['env']['RUNTIME_BOOTSTRAP_TOKEN'])

    def test_start_persists_extra_env_for_future_restarts(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            runtime_dir = repo_root / 'runtime'
            config = runtime_bridge_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=runtime_dir,
                pid_path=runtime_dir / 'runtime-bridge.pid',
                log_path=runtime_dir / 'runtime-bridge.log',
                health_url='http://127.0.0.1:19080/health',
                wait_seconds=1.0,
                poll_interval=0.01,
            )
            process = mock.Mock(pid=4321)

            with mock.patch.object(runtime_bridge_ctl, 'read_pid', return_value=None), \
                 mock.patch.object(runtime_bridge_ctl, 'is_process_running', return_value=False), \
                 mock.patch.object(runtime_bridge_ctl, 'wait_for_health', return_value={'http_status': 200, 'payload': {'status': 'ok'}}), \
                 mock.patch.object(runtime_bridge_ctl.subprocess, 'Popen', return_value=process):
                runtime_bridge_ctl.start_bridge(config, extra_env={'RUNTIME_BOOTSTRAP_TOKEN': 'demo', 'DEFAULT_PLUGIN_ID': 'yolov8n'})

            persisted = (runtime_dir / 'runtime-bridge.env').read_text(encoding='utf-8')
            self.assertIn('RUNTIME_BOOTSTRAP_TOKEN=demo', persisted)
            self.assertIn('DEFAULT_PLUGIN_ID=yolov8n', persisted)

    def test_start_uses_persisted_env_when_extra_env_missing(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            runtime_dir = repo_root / 'runtime'
            runtime_dir.mkdir(parents=True, exist_ok=True)
            (runtime_dir / 'runtime-bridge.env').write_text('RUNTIME_BOOTSTRAP_TOKEN=demo\nDEFAULT_PLUGIN_ID=yolov8n\n', encoding='utf-8')
            config = runtime_bridge_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=runtime_dir,
                pid_path=runtime_dir / 'runtime-bridge.pid',
                log_path=runtime_dir / 'runtime-bridge.log',
                health_url='http://127.0.0.1:19080/health',
                wait_seconds=1.0,
                poll_interval=0.01,
            )
            process = mock.Mock(pid=4321)

            with mock.patch.object(runtime_bridge_ctl, 'read_pid', return_value=None), \
                 mock.patch.object(runtime_bridge_ctl, 'is_process_running', return_value=False), \
                 mock.patch.object(runtime_bridge_ctl, 'wait_for_health', return_value={'http_status': 200, 'payload': {'status': 'ok'}}), \
                 mock.patch.object(runtime_bridge_ctl.subprocess, 'Popen', return_value=process) as popen_mock:
                runtime_bridge_ctl.start_bridge(config)

            self.assertEqual('demo', popen_mock.call_args.kwargs['env']['RUNTIME_BOOTSTRAP_TOKEN'])
            self.assertEqual('yolov8n', popen_mock.call_args.kwargs['env']['DEFAULT_PLUGIN_ID'])

    def test_start_returns_already_running_when_pid_is_active(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            runtime_dir = repo_root / 'runtime'
            runtime_dir.mkdir(parents=True, exist_ok=True)
            pid_path = runtime_dir / 'runtime-bridge.pid'
            pid_path.write_text('9876\n', encoding='utf-8')
            config = runtime_bridge_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=runtime_dir,
                pid_path=pid_path,
                log_path=runtime_dir / 'runtime-bridge.log',
                health_url='http://127.0.0.1:19080/health',
            )

            with mock.patch.object(runtime_bridge_ctl, 'is_process_running', return_value=True), \
                 mock.patch.object(runtime_bridge_ctl, 'fetch_health', return_value={'http_status': 200, 'payload': {'status': 'ok'}}), \
                 mock.patch.object(runtime_bridge_ctl.subprocess, 'Popen') as popen_mock:
                result = runtime_bridge_ctl.start_bridge(config)

            self.assertEqual('already_running', result['status'])
            self.assertEqual(9876, result['pid'])
            popen_mock.assert_not_called()

    def test_stop_terminates_running_process_group_and_cleans_pid_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            runtime_dir = repo_root / 'runtime'
            runtime_dir.mkdir(parents=True, exist_ok=True)
            pid_path = runtime_dir / 'runtime-bridge.pid'
            pid_path.write_text('2468\n', encoding='utf-8')
            config = runtime_bridge_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=runtime_dir,
                pid_path=pid_path,
                log_path=runtime_dir / 'runtime-bridge.log',
                health_url='http://127.0.0.1:19080/health',
                stop_wait_seconds=0.01,
            )

            running_states = iter([True, False])
            with mock.patch.object(runtime_bridge_ctl, 'is_process_running', side_effect=lambda _pid: next(running_states)), \
                 mock.patch.object(runtime_bridge_ctl, 'terminate_process_group') as terminate_mock:
                result = runtime_bridge_ctl.stop_bridge(config)

            self.assertEqual('stopped', result['status'])
            self.assertEqual(2468, result['pid'])
            self.assertFalse(pid_path.exists())
            terminate_mock.assert_called_once_with(2468, runtime_bridge_ctl.signal.SIGTERM)

    def test_status_reports_pid_and_health(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            runtime_dir = repo_root / 'runtime'
            runtime_dir.mkdir(parents=True, exist_ok=True)
            pid_path = runtime_dir / 'runtime-bridge.pid'
            pid_path.write_text('1357\n', encoding='utf-8')
            config = runtime_bridge_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=runtime_dir,
                pid_path=pid_path,
                log_path=runtime_dir / 'runtime-bridge.log',
                health_url='http://127.0.0.1:19080/health',
            )

            with mock.patch.object(runtime_bridge_ctl, 'is_process_running', return_value=True), \
                 mock.patch.object(runtime_bridge_ctl, 'fetch_health', return_value={'http_status': 200, 'payload': {'status': 'ok', 'runtime': 'rknn'}}):
                result = runtime_bridge_ctl.status_bridge(config)

            self.assertEqual('running', result['status'])
            self.assertEqual(1357, result['pid'])
            self.assertEqual('ok', result['health']['payload']['status'])


if __name__ == '__main__':
    unittest.main()