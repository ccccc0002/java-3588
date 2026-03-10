import importlib.util
import pathlib
import sys
import tempfile
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'media_worker_ctl.py'
SPEC = importlib.util.spec_from_file_location('media_worker_ctl', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
media_worker_ctl = importlib.util.module_from_spec(SPEC)
sys.modules['media_worker_ctl'] = media_worker_ctl
SPEC.loader.exec_module(media_worker_ctl)


class MediaWorkerControllerTests(unittest.TestCase):
    def test_start_worker_uses_repo_script(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            script_path = repo_root / 'scripts' / 'rk3588' / 'Run-Media-Worker.sh'
            script_path.parent.mkdir(parents=True, exist_ok=True)
            script_path.write_text('#!/usr/bin/env bash\n', encoding='utf-8')
            config = media_worker_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                pid_path=repo_root / 'runtime' / 'media-worker.pid',
                log_path=repo_root / 'runtime' / 'media-worker.log',
                run_script=script_path,
                wait_seconds=0.01,
                poll_interval=0.01,
                stop_wait_seconds=1.0,
            )
            process = mock.Mock(pid=4567)
            process.poll.return_value = None

            with mock.patch.object(media_worker_ctl.subprocess, 'Popen', return_value=process) as popen_mock:
                result = media_worker_ctl.start_worker(config, extra_env={'RUNTIME_BOOTSTRAP_TOKEN': 'demo'})

            self.assertEqual('started', result['status'])
            self.assertEqual(4567, result['pid'])
            popen_args, popen_kwargs = popen_mock.call_args
            self.assertEqual(['bash', str(script_path)], popen_args[0])
            self.assertEqual(str(repo_root), popen_kwargs['cwd'])
            self.assertEqual('demo', popen_kwargs['env']['RUNTIME_BOOTSTRAP_TOKEN'])

    def test_start_worker_persists_extra_env_for_future_restarts(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            script_path = repo_root / 'scripts' / 'rk3588' / 'Run-Media-Worker.sh'
            script_path.parent.mkdir(parents=True, exist_ok=True)
            script_path.write_text('#!/usr/bin/env bash\n', encoding='utf-8')
            config = media_worker_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                pid_path=repo_root / 'runtime' / 'media-worker.pid',
                log_path=repo_root / 'runtime' / 'media-worker.log',
                run_script=script_path,
                wait_seconds=0.01,
                poll_interval=0.01,
                stop_wait_seconds=1.0,
            )
            process = mock.Mock(pid=4567)
            process.poll.return_value = None

            with mock.patch.object(media_worker_ctl.subprocess, 'Popen', return_value=process):
                media_worker_ctl.start_worker(config, extra_env={'RUNTIME_BOOTSTRAP_TOKEN': 'demo', 'TIMEOUT_SEC': '15'})

            persisted = (repo_root / 'runtime' / 'media-worker.env').read_text(encoding='utf-8')
            self.assertIn('RUNTIME_BOOTSTRAP_TOKEN=demo', persisted)
            self.assertIn('TIMEOUT_SEC=15', persisted)

    def test_start_worker_uses_persisted_env_when_extra_env_missing(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            script_path = repo_root / 'scripts' / 'rk3588' / 'Run-Media-Worker.sh'
            script_path.parent.mkdir(parents=True, exist_ok=True)
            script_path.write_text('#!/usr/bin/env bash\n', encoding='utf-8')
            runtime_dir = repo_root / 'runtime'
            runtime_dir.mkdir(parents=True, exist_ok=True)
            (runtime_dir / 'media-worker.env').write_text('RUNTIME_BOOTSTRAP_TOKEN=demo\nINTERVAL_SEC=3\n', encoding='utf-8')
            config = media_worker_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=runtime_dir,
                pid_path=runtime_dir / 'media-worker.pid',
                log_path=runtime_dir / 'media-worker.log',
                run_script=script_path,
                wait_seconds=0.01,
                poll_interval=0.01,
                stop_wait_seconds=1.0,
            )
            process = mock.Mock(pid=4567)
            process.poll.return_value = None

            with mock.patch.object(media_worker_ctl.subprocess, 'Popen', return_value=process) as popen_mock:
                media_worker_ctl.start_worker(config)

            self.assertEqual('demo', popen_mock.call_args.kwargs['env']['RUNTIME_BOOTSTRAP_TOKEN'])
            self.assertEqual('3', popen_mock.call_args.kwargs['env']['INTERVAL_SEC'])

    def test_status_worker_reports_running_when_pid_exists(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            pid_path = repo_root / 'runtime' / 'media-worker.pid'
            pid_path.parent.mkdir(parents=True, exist_ok=True)
            pid_path.write_text('4567\n', encoding='utf-8')
            config = media_worker_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                pid_path=pid_path,
                log_path=repo_root / 'runtime' / 'media-worker.log',
            )
            with mock.patch.object(media_worker_ctl, 'is_process_running', return_value=True):
                result = media_worker_ctl.status_worker(config)

            self.assertEqual('running', result['status'])
            self.assertEqual(4567, result['pid'])

    def test_stop_worker_terminates_process_group(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            pid_path = repo_root / 'runtime' / 'media-worker.pid'
            pid_path.parent.mkdir(parents=True, exist_ok=True)
            pid_path.write_text('4567\n', encoding='utf-8')
            config = media_worker_ctl.ControllerConfig(
                repo_root=repo_root,
                runtime_dir=repo_root / 'runtime',
                pid_path=pid_path,
                log_path=repo_root / 'runtime' / 'media-worker.log',
                stop_wait_seconds=0.05,
            )
            with mock.patch.object(media_worker_ctl, 'terminate_process_group') as terminate_mock, \
                 mock.patch.object(media_worker_ctl, 'is_process_running', side_effect=[True, False]):
                result = media_worker_ctl.stop_worker(config)

            self.assertEqual('stopped', result['status'])
            terminate_mock.assert_called_once()


if __name__ == '__main__':
    unittest.main()