import importlib.util
import pathlib
import sys
import tempfile
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'runtime_api_ctl.py'
SPEC = importlib.util.spec_from_file_location('runtime_api_ctl', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
runtime_api_ctl = importlib.util.module_from_spec(SPEC)
sys.modules['runtime_api_ctl'] = runtime_api_ctl
SPEC.loader.exec_module(runtime_api_ctl)


class RuntimeApiControllerTests(unittest.TestCase):
    def test_start_runtime_api_uses_repo_script_health_and_env(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            runtime_dir = repo_root / 'runtime'
            script_path = repo_root / 'scripts' / 'rk3588' / 'Run-Runtime-Api.sh'
            script_path.parent.mkdir(parents=True, exist_ok=True)
            script_path.write_text('#!/usr/bin/env bash\n', encoding='utf-8')
            config = runtime_api_ctl.RuntimeApiControllerConfig(
                repo_root=repo_root,
                runtime_dir=runtime_dir,
                start_script=script_path,
                health_url='http://127.0.0.1:18081/api/v1/runtime/health',
                wait_seconds=5,
                poll_interval=0.01,
                stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar',
            )
            process = mock.Mock(pid=3456)

            with mock.patch.object(runtime_api_ctl, 'fetch_health', side_effect=[{'http_status': 0, 'payload': {}}, {'http_status': 200, 'payload': {'success': True}}]), \
                 mock.patch.object(runtime_api_ctl.subprocess, 'Popen', return_value=process) as popen_mock:
                result = runtime_api_ctl.start_runtime_api(config, extra_env={'RUNTIME_BOOTSTRAP_TOKEN': 'edge-demo-bootstrap'})

            self.assertEqual('started', result['status'])
            self.assertEqual(3456, result['pid'])
            self.assertEqual('edge-demo-bootstrap', popen_mock.call_args.kwargs['env']['RUNTIME_BOOTSTRAP_TOKEN'])
            self.assertEqual(str(repo_root), popen_mock.call_args.kwargs['cwd'])
            persisted = (runtime_dir / 'runtime-api.env').read_text(encoding='utf-8')
            self.assertIn('RUNTIME_BOOTSTRAP_TOKEN=edge-demo-bootstrap', persisted)

    def test_start_runtime_api_loads_persisted_env(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            runtime_dir = repo_root / 'runtime'
            runtime_dir.mkdir(parents=True, exist_ok=True)
            (runtime_dir / 'runtime-api.env').write_text('RUNTIME_BOOTSTRAP_TOKEN=edge-demo-bootstrap\nJAVA_BIN=/usr/bin/java\n', encoding='utf-8')
            script_path = repo_root / 'scripts' / 'rk3588' / 'Run-Runtime-Api.sh'
            script_path.parent.mkdir(parents=True, exist_ok=True)
            script_path.write_text('#!/usr/bin/env bash\n', encoding='utf-8')
            config = runtime_api_ctl.RuntimeApiControllerConfig(
                repo_root=repo_root,
                runtime_dir=runtime_dir,
                start_script=script_path,
                health_url='http://127.0.0.1:18081/api/v1/runtime/health',
                wait_seconds=5,
                poll_interval=0.01,
                stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar',
            )
            process = mock.Mock(pid=3456)

            with mock.patch.object(runtime_api_ctl, 'fetch_health', side_effect=[{'http_status': 0, 'payload': {}}, {'http_status': 200, 'payload': {'success': True}}]), \
                 mock.patch.object(runtime_api_ctl.subprocess, 'Popen', return_value=process) as popen_mock:
                runtime_api_ctl.start_runtime_api(config)

            self.assertEqual('edge-demo-bootstrap', popen_mock.call_args.kwargs['env']['RUNTIME_BOOTSTRAP_TOKEN'])
            self.assertEqual('/usr/bin/java', popen_mock.call_args.kwargs['env']['JAVA_BIN'])

    def test_status_runtime_api_reports_down_when_health_fails(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = runtime_api_ctl.RuntimeApiControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                start_script=repo_root / 'scripts' / 'rk3588' / 'Run-Runtime-Api.sh',
                health_url='http://127.0.0.1:18081/api/v1/runtime/health',
                stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar',
            )
            with mock.patch.object(runtime_api_ctl, 'fetch_health', return_value={'http_status': 0, 'payload': {'message': 'connection refused'}}):
                result = runtime_api_ctl.status_runtime_api(config)

            self.assertEqual('down', result['status'])

    def test_stop_runtime_api_waits_for_health_to_drop(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            config = runtime_api_ctl.RuntimeApiControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                start_script=repo_root / 'scripts' / 'rk3588' / 'Run-Runtime-Api.sh',
                health_url='http://127.0.0.1:18081/api/v1/runtime/health',
                poll_interval=0.01,
                stop_wait_seconds=1,
                stop_pattern='java-rk3588-0.0.1-SNAPSHOT.jar',
            )
            with mock.patch.object(runtime_api_ctl.subprocess, 'run', return_value=mock.Mock(returncode=0, stdout='', stderr='')) as run_mock, \
                 mock.patch.object(runtime_api_ctl, 'fetch_health', side_effect=[{'http_status': 200, 'payload': {'success': True}}, {'http_status': 0, 'payload': {'message': 'connection refused'}}]):
                result = runtime_api_ctl.stop_runtime_api(config)

            self.assertEqual('stopped', result['status'])
            run_mock.assert_called_once_with(['pkill', '-f', 'java-rk3588-0.0.1-SNAPSHOT.jar'], capture_output=True, text=True)

    def test_build_config_parses_env_arguments(self):
        args = runtime_api_ctl.parse_args(['start', '--env', 'RUNTIME_BOOTSTRAP_TOKEN=edge-demo-bootstrap', '--env', 'JAVA_BIN=/usr/bin/java'])
        config, extra_env = runtime_api_ctl.build_config(args)

        self.assertTrue(config.env_path.name.endswith('runtime-api.env'))
        self.assertEqual('edge-demo-bootstrap', extra_env['RUNTIME_BOOTSTRAP_TOKEN'])
        self.assertEqual('/usr/bin/java', extra_env['JAVA_BIN'])

    def test_default_config_uses_runtime_health_and_longer_wait(self):
        config = runtime_api_ctl.default_config()

        self.assertTrue(config.health_url.endswith('/api/v1/runtime/health'))
        self.assertEqual(40.0, config.wait_seconds)
        self.assertEqual('java-rk3588-0.0.1-SNAPSHOT.jar', config.stop_pattern)


if __name__ == '__main__':
    unittest.main()
