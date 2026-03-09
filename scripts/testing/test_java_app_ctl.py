import importlib.util
import pathlib
import sys
import tempfile
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'java_app_ctl.py'
SPEC = importlib.util.spec_from_file_location('java_app_ctl', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
java_app_ctl = importlib.util.module_from_spec(SPEC)
sys.modules['java_app_ctl'] = java_app_ctl
SPEC.loader.exec_module(java_app_ctl)


class JavaAppControllerTests(unittest.TestCase):
    def test_start_app_uses_repo_start_script_and_returns_started(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            script_path = repo_root / 'scripts' / 'rk3588' / 'Run-Java-App.sh'
            script_path.parent.mkdir(parents=True, exist_ok=True)
            script_path.write_text('#!/usr/bin/env bash\n', encoding='utf-8')
            config = java_app_ctl.AppControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                start_script=script_path,
                health_url='http://127.0.0.1:18082/api/inference/health',
                wait_seconds=5,
                poll_interval=0.01,
                stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar.*18082',
            )
            process = mock.Mock(pid=4567)

            with mock.patch.object(
                java_app_ctl,
                'fetch_health',
                side_effect=[{'http_status': 0, 'payload': {}}, {'http_status': 200, 'payload': {'code': 0}}],
            ), mock.patch.object(java_app_ctl.subprocess, 'Popen', return_value=process) as popen_mock:
                result = java_app_ctl.start_app(config)

            self.assertEqual('started', result['status'])
            self.assertEqual(4567, result['pid'])
            self.assertEqual(200, result['health']['http_status'])
            self.assertEqual(str(script_path), result['script'])
            popen_args, popen_kwargs = popen_mock.call_args
            self.assertEqual(['bash', str(script_path)], popen_args[0])
            self.assertEqual(str(repo_root), popen_kwargs['cwd'])
            self.assertTrue(popen_kwargs['start_new_session'])

    def test_start_app_uses_runtime_fallback_when_managed_env_missing(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            start_script = repo_root / 'scripts' / 'rk3588' / 'Run-Java-App.sh'
            start_script.parent.mkdir(parents=True, exist_ok=True)
            start_script.write_text('#!/usr/bin/env bash\n', encoding='utf-8')
            fallback_script = repo_root / 'runtime' / 'start-app-18082.sh'
            fallback_script.parent.mkdir(parents=True, exist_ok=True)
            fallback_script.write_text('#!/usr/bin/env bash\n', encoding='utf-8')
            config = java_app_ctl.AppControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                start_script=start_script,
                health_url='http://127.0.0.1:18082/api/inference/health',
                wait_seconds=5,
                poll_interval=0.01,
                stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar.*18082',
            )
            process = mock.Mock(pid=5678)

            with mock.patch.object(
                java_app_ctl,
                'fetch_health',
                side_effect=[{'http_status': 0, 'payload': {}}, {'http_status': 200, 'payload': {'code': 0}}],
            ), mock.patch.object(java_app_ctl.subprocess, 'Popen', return_value=process) as popen_mock:
                result = java_app_ctl.start_app(config)

            self.assertEqual('started', result['status'])
            self.assertEqual(str(fallback_script), result['script'])
            popen_args, _ = popen_mock.call_args
            self.assertEqual(['bash', str(fallback_script)], popen_args[0])

    def test_start_app_returns_already_running_when_health_is_up(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = java_app_ctl.AppControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                start_script=repo_root / 'scripts' / 'rk3588' / 'Run-Java-App.sh',
                health_url='http://127.0.0.1:18082/api/inference/health',
                stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar.*18082',
            )
            with mock.patch.object(
                java_app_ctl,
                'fetch_health',
                return_value={'http_status': 200, 'payload': {'code': 0}},
            ), mock.patch.object(java_app_ctl.subprocess, 'Popen') as popen_mock:
                result = java_app_ctl.start_app(config)

            self.assertEqual('already_running', result['status'])
            popen_mock.assert_not_called()

    def test_stop_app_waits_for_health_to_drop_after_pkill(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = java_app_ctl.AppControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                start_script=repo_root / 'scripts' / 'rk3588' / 'Run-Java-App.sh',
                health_url='http://127.0.0.1:18082/api/inference/health',
                poll_interval=0.01,
                stop_wait_seconds=1,
                stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar.*18082',
            )
            with mock.patch.object(
                java_app_ctl.subprocess,
                'run',
                return_value=mock.Mock(returncode=0, stdout='', stderr=''),
            ) as run_mock, mock.patch.object(
                java_app_ctl,
                'fetch_health',
                side_effect=[
                    {'http_status': 200, 'payload': {'code': 0}},
                    {'http_status': 0, 'payload': {'message': 'connection refused'}},
                ],
            ):
                result = java_app_ctl.stop_app(config)

            self.assertEqual('stopped', result['status'])
            self.assertEqual(0, result['health']['http_status'])
            run_mock.assert_called_once_with(
                ['pkill', '-f', 'java-rk3588-0.0.1-SNAPSHOT.jar.*18082'],
                capture_output=True,
                text=True,
            )

    def test_stop_app_returns_stop_pending_exit_when_health_stays_up(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = java_app_ctl.AppControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                start_script=repo_root / 'scripts' / 'rk3588' / 'Run-Java-App.sh',
                health_url='http://127.0.0.1:18082/api/inference/health',
                poll_interval=0.01,
                stop_wait_seconds=0.02,
                stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar.*18082',
            )
            with mock.patch.object(
                java_app_ctl.subprocess,
                'run',
                return_value=mock.Mock(returncode=0, stdout='', stderr=''),
            ), mock.patch.object(
                java_app_ctl,
                'fetch_health',
                return_value={'http_status': 200, 'payload': {'code': 0}},
            ):
                result = java_app_ctl.stop_app(config)

            self.assertEqual('stop_pending_exit', result['status'])
            self.assertEqual(200, result['health']['http_status'])

    def test_status_app_reports_down_when_health_fails(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = java_app_ctl.AppControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                start_script=repo_root / 'scripts' / 'rk3588' / 'Run-Java-App.sh',
                health_url='http://127.0.0.1:18082/api/inference/health',
                stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar.*18082',
            )
            with mock.patch.object(
                java_app_ctl,
                'fetch_health',
                return_value={'http_status': 0, 'payload': {'message': 'connection refused'}},
            ):
                result = java_app_ctl.status_app(config)

            self.assertEqual('down', result['status'])
            self.assertEqual('connection refused', result['health']['payload']['message'])

    def test_restart_app_blocks_when_shutdown_does_not_complete(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = java_app_ctl.AppControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                start_script=repo_root / 'scripts' / 'rk3588' / 'Run-Java-App.sh',
                health_url='http://127.0.0.1:18082/api/inference/health',
            )
            with mock.patch.object(
                java_app_ctl,
                'stop_app',
                return_value={'status': 'stop_pending_exit'},
            ) as stop_app_mock, mock.patch.object(java_app_ctl, 'start_app') as start_app_mock:
                result = java_app_ctl.restart_app(config)

            self.assertEqual('restart_blocked', result['status'])
            stop_app_mock.assert_called_once_with(config)
            start_app_mock.assert_not_called()


if __name__ == '__main__':
    unittest.main()