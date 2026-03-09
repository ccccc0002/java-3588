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
    def test_start_stack_starts_app_then_bridge(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            app_script = repo_root / 'runtime' / 'start-app-18082.sh'
            app_script.parent.mkdir(parents=True, exist_ok=True)
            app_script.write_text('#!/usr/bin/env bash\n', encoding='utf-8')
            config = runtime_stack_ctl.StackControllerConfig(
                repo_root=repo_root,
                app_start_script=app_script,
                app_health_url='http://127.0.0.1:18082/api/inference/health',
                app_wait_seconds=5,
                app_poll_interval=0.01,
            )
            app_process = mock.Mock(pid=2345)

            with mock.patch.object(runtime_stack_ctl.subprocess, 'Popen', return_value=app_process) as popen_mock, \
                 mock.patch.object(runtime_stack_ctl, 'wait_for_app_health', return_value={'http_status': 200, 'payload': {'code': 0}}), \
                 mock.patch.object(runtime_stack_ctl.runtime_bridge_ctl, 'start_bridge', return_value={'status': 'started', 'pid': 3456}):
                result = runtime_stack_ctl.start_stack(config, bridge_env={'RUNTIME_BOOTSTRAP_TOKEN': 'demo'})

            self.assertEqual('started', result['status'])
            self.assertEqual(2345, result['app']['pid'])
            self.assertEqual('started', result['bridge']['status'])
            popen_args, popen_kwargs = popen_mock.call_args
            self.assertEqual(['bash', str(app_script)], popen_args[0])
            self.assertEqual(str(repo_root), popen_kwargs['cwd'])
            self.assertTrue(popen_kwargs['start_new_session'])

    def test_status_stack_reports_both_layers(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = runtime_stack_ctl.StackControllerConfig(
                repo_root=repo_root,
                app_start_script=repo_root / 'runtime' / 'start-app-18082.sh',
                app_health_url='http://127.0.0.1:18082/api/inference/health',
            )
            with mock.patch.object(runtime_stack_ctl, 'fetch_health', return_value={'http_status': 200, 'payload': {'code': 0}}), \
                 mock.patch.object(runtime_stack_ctl.runtime_bridge_ctl, 'status_bridge', return_value={'status': 'running', 'pid': 3456}):
                result = runtime_stack_ctl.status_stack(config)

            self.assertEqual('running', result['status'])
            self.assertEqual('running', result['app']['status'])
            self.assertEqual('running', result['bridge']['status'])

    def test_stop_stack_stops_bridge_then_app(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = runtime_stack_ctl.StackControllerConfig(
                repo_root=repo_root,
                app_start_script=repo_root / 'runtime' / 'start-app-18082.sh',
                app_health_url='http://127.0.0.1:18082/api/inference/health',
                app_stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar.*18082',
            )
            with mock.patch.object(runtime_stack_ctl.runtime_bridge_ctl, 'stop_bridge', return_value={'status': 'stopped', 'pid': 3456}) as stop_bridge_mock, \
                 mock.patch.object(runtime_stack_ctl.subprocess, 'run', return_value=mock.Mock(returncode=0, stdout='', stderr='')) as run_mock:
                result = runtime_stack_ctl.stop_stack(config)

            self.assertEqual('stopped', result['status'])
            self.assertEqual('stopped', result['bridge']['status'])
            self.assertEqual('stopped', result['app']['status'])
            stop_bridge_mock.assert_called_once()
            run_mock.assert_called_once()

    def test_start_stack_fails_when_app_health_never_recovers(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            app_script = repo_root / 'runtime' / 'start-app-18082.sh'
            app_script.parent.mkdir(parents=True, exist_ok=True)
            app_script.write_text('#!/usr/bin/env bash\n', encoding='utf-8')
            config = runtime_stack_ctl.StackControllerConfig(
                repo_root=repo_root,
                app_start_script=app_script,
                app_health_url='http://127.0.0.1:18082/api/inference/health',
            )

            with mock.patch.object(runtime_stack_ctl.subprocess, 'Popen', return_value=mock.Mock(pid=2345)), \
                 mock.patch.object(runtime_stack_ctl, 'wait_for_app_health', return_value={'http_status': 0, 'payload': {'message': 'connection refused'}}), \
                 mock.patch.object(runtime_stack_ctl.runtime_bridge_ctl, 'start_bridge') as start_bridge_mock:
                result = runtime_stack_ctl.start_stack(config)

            self.assertEqual('app_unhealthy', result['status'])
            start_bridge_mock.assert_not_called()


if __name__ == '__main__':
    unittest.main()