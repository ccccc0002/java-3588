import argparse
import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest
from unittest import mock


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'tmux_parallel_ctl.py'
SPEC = importlib.util.spec_from_file_location('tmux_parallel_ctl', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
tmux_parallel_ctl = importlib.util.module_from_spec(SPEC)
sys.modules['tmux_parallel_ctl'] = tmux_parallel_ctl
SPEC.loader.exec_module(tmux_parallel_ctl)


class TmuxParallelCtlTests(unittest.TestCase):
    def test_parse_lane(self):
        lane_name, lane_cmd = tmux_parallel_ctl.parse_lane('ai=python3 scripts/testing/run_inference_quality_diagnostics.py')
        self.assertEqual('ai', lane_name)
        self.assertEqual('python3 scripts/testing/run_inference_quality_diagnostics.py', lane_cmd)

    def test_parse_lane_rejects_invalid(self):
        with self.assertRaises(ValueError):
            tmux_parallel_ctl.parse_lane('invalid_lane_spec')

    def test_build_lane_command_contains_workdir_and_command(self):
        command = tmux_parallel_ctl.build_lane_command(pathlib.Path('/tmp/demo'), 'echo ok')
        self.assertIn('echo ok', command)
        self.assertIn('[lane-exit:$code]', command)
        self.assertIn('cd', command)

    def test_build_default_lanes_uses_arguments(self):
        args = argparse.Namespace(
            output_root='runtime/test-out/parallel',
            base_url='http://127.0.0.1:18082',
            bridge_url='http://127.0.0.1:19080',
            camera_id=1,
            model_id=1,
            algorithm_id=1,
            invalid_camera_id=999999,
            timeout_sec=45,
            source='test://frame',
            plugin_id='yolov8n',
            quality_iterations=20,
            quality_interval_ms=250,
            web_username='admin',
            web_password='admin123',
        )
        lanes = tmux_parallel_ctl.build_default_lanes(args)
        self.assertEqual(4, len(lanes))
        self.assertEqual('media', lanes[0][0])
        self.assertIn('--base-url http://127.0.0.1:18082', lanes[0][1])
        self.assertEqual('ai', lanes[1][0])
        self.assertIn('--plugin-id yolov8n', lanes[1][1])

    def test_start_session_returns_tmux_missing_when_not_installed(self):
        args = tmux_parallel_ctl.parse_args(['start', '--session', 'phase2-parallel'])
        with mock.patch.object(tmux_parallel_ctl, 'tmux_available', return_value=False):
            result = tmux_parallel_ctl.start_session(args)
        self.assertEqual('tmux_missing', result['status'])

    def test_status_session_not_running(self):
        with mock.patch.object(tmux_parallel_ctl, 'tmux_available', return_value=True), \
             mock.patch.object(tmux_parallel_ctl, 'session_exists', return_value=False):
            result = tmux_parallel_ctl.status_session('phase2-parallel')
        self.assertEqual('not_running', result['status'])

    def test_load_lanes_from_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            lane_file = pathlib.Path(temp_dir) / 'lanes.json'
            lane_file.write_text(
                json.dumps(
                    [
                        {'name': 'phase7', 'command': 'mvn -Dtest=ConfigControllerTest test'},
                        {'name': 'phase9', 'command': 'mvn -Dtest=ActiveCameraInferenceSchedulerServiceTest test'},
                    ]
                ),
                encoding='utf-8',
            )
            lanes = tmux_parallel_ctl.load_lanes_from_file(lane_file)

        self.assertEqual(2, len(lanes))
        self.assertEqual(('phase7', 'mvn -Dtest=ConfigControllerTest test'), lanes[0])


if __name__ == '__main__':
    unittest.main()
