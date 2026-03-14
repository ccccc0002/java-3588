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
            quality_retry_attempts=3,
            quality_retry_interval_ms=200,
            quality_max_invalid_bbox_count=0,
            quality_max_invalid_score_count=-1,
            quality_max_empty_label_count=-1,
            quality_max_failed_iterations=1,
            source_policy_retry_attempts=3,
            source_policy_retry_interval_ms=300,
            web_username='admin',
            web_password='admin123',
        )
        lanes = tmux_parallel_ctl.build_default_lanes(args)
        self.assertEqual(4, len(lanes))
        self.assertEqual('media', lanes[0][0])
        self.assertIn('--base-url http://127.0.0.1:18082', lanes[0][1])
        self.assertIn('--retry-attempts 3', lanes[0][1])
        self.assertIn('--retry-interval-ms 300', lanes[0][1])
        self.assertEqual('ai', lanes[1][0])
        self.assertIn('--plugin-id yolov8n', lanes[1][1])
        self.assertIn('--retry-attempts 3', lanes[1][1])
        self.assertIn('--retry-interval-ms 200', lanes[1][1])
        self.assertIn('--max-invalid-bbox-count 0', lanes[1][1])
        self.assertIn('--max-failed-iterations 1', lanes[1][1])

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

    def test_parse_lane_exit_code_uses_latest_marker(self):
        pane_text = '\n'.join(
            [
                '[INFO] lane running',
                '[lane-exit:1]',
                '[INFO] retry',
                '[lane-exit:0]',
            ]
        )
        self.assertEqual(0, tmux_parallel_ctl.parse_lane_exit_code(pane_text))
        self.assertIsNone(tmux_parallel_ctl.parse_lane_exit_code('no marker'))

    def test_report_session_aggregates_lane_statuses(self):
        windows = [
            {'name': 'phase7', 'current_command': 'bash', 'pane_dead': False},
            {'name': 'phase8', 'current_command': 'bash', 'pane_dead': False},
            {'name': 'phase9', 'current_command': 'bash', 'pane_dead': False},
        ]
        lane_reports = [
            {'name': 'phase7', 'lane_exit': 0, 'lane_status': 'passed', 'tail': ['[lane-exit:0]']},
            {'name': 'phase8', 'lane_exit': 1, 'lane_status': 'failed', 'tail': ['[lane-exit:1]']},
            {'name': 'phase9', 'lane_exit': None, 'lane_status': 'running', 'tail': []},
        ]

        with mock.patch.object(tmux_parallel_ctl, 'tmux_available', return_value=True), \
             mock.patch.object(tmux_parallel_ctl, 'session_exists', return_value=True), \
             mock.patch.object(tmux_parallel_ctl, 'list_windows', return_value=windows), \
             mock.patch.object(tmux_parallel_ctl, 'capture_window_report', side_effect=lane_reports):
            report = tmux_parallel_ctl.report_session('phase2-parallel', 10)

        self.assertEqual('running', report['status'])
        self.assertEqual(3, report['lane_count'])
        self.assertEqual(1, report['passed_count'])
        self.assertEqual(1, report['failed_count'])
        self.assertEqual(1, report['running_count'])
        self.assertEqual('phase8', report['lanes'][1]['name'])

    def test_parse_session_sort_key_prefers_retry_suffix(self):
        base, index, raw = tmux_parallel_ctl.parse_session_sort_key('phase2-nextwave-r14')
        self.assertEqual('phase2-nextwave', base)
        self.assertEqual(14, index)
        self.assertEqual('phase2-nextwave-r14', raw)

    def test_prune_sessions_keeps_latest_by_prefix(self):
        listed = {
            'status': 'listed',
            'sessions': ['phase2-nextwave-r1', 'phase2-nextwave-r2', 'phase2-nextwave-r3', 'other-r9'],
        }

        def fake_run_tmux(args):
            if args[:2] == ['kill-session', '-t']:
                return mock.Mock(returncode=0, stdout='', stderr='')
            return mock.Mock(returncode=1, stdout='', stderr='unsupported')

        with mock.patch.object(tmux_parallel_ctl, 'tmux_available', return_value=True), \
             mock.patch.object(tmux_parallel_ctl, 'list_sessions', return_value=listed), \
             mock.patch.object(tmux_parallel_ctl, 'run_tmux', side_effect=fake_run_tmux):
            result = tmux_parallel_ctl.prune_sessions('phase2-nextwave-', 1)

        self.assertEqual('pruned', result['status'])
        self.assertEqual(['phase2-nextwave-r3'], result['kept'])
        self.assertEqual(['phase2-nextwave-r1', 'phase2-nextwave-r2'], result['killed'])

    def test_prune_sessions_returns_partial_failed_when_kill_fails(self):
        listed = {
            'status': 'listed',
            'sessions': ['phase2-nextwave-r1', 'phase2-nextwave-r2'],
        }

        def fake_run_tmux(args):
            if args[:2] == ['kill-session', '-t']:
                target = args[2]
                code = 1 if target.endswith('r1') else 0
                return mock.Mock(returncode=code, stdout='', stderr='failed' if code else '')
            return mock.Mock(returncode=1, stdout='', stderr='unsupported')

        with mock.patch.object(tmux_parallel_ctl, 'tmux_available', return_value=True), \
             mock.patch.object(tmux_parallel_ctl, 'list_sessions', return_value=listed), \
             mock.patch.object(tmux_parallel_ctl, 'run_tmux', side_effect=fake_run_tmux):
            result = tmux_parallel_ctl.prune_sessions('phase2-nextwave-', 0)

        self.assertEqual('prune_partial_failed', result['status'])
        self.assertEqual(['phase2-nextwave-r2'], result['killed'])
        self.assertEqual(['phase2-nextwave-r1'], result['failed'])


if __name__ == '__main__':
    unittest.main()
