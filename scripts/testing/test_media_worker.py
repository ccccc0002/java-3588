import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'media_worker.py'
SPEC = importlib.util.spec_from_file_location('media_worker', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
media_worker = importlib.util.module_from_spec(SPEC)
sys.modules['media_worker'] = media_worker
SPEC.loader.exec_module(media_worker)


class MediaWorkerTests(unittest.TestCase):
    def test_build_targets_from_runtime_snapshot(self):
        snapshot = {
            'media': {
                'server_type': 'zlm',
                'ffmpeg_bin': 'ffmpeg',
                'decode_backend': 'mpp',
                'decode_hwaccel': 'rga',
                'rtsp_transport': 'tcp',
                'osd_enabled': False,
                'video_codec': 'h264_rkmpp',
            },
            'streams': [
                {
                    'camera_id': 1,
                    'camera_name': 'North Gate',
                    'rtsp_url': 'rtsp://camera/1/h264/main',
                    'play_url': 'http://zlm/live/1.flv',
                    'push_url': 'rtmp://127.0.0.1:1935/live/1',
                    'video_port': 18082,
                    'enabled': True,
                }
            ],
        }

        targets = media_worker.build_targets(snapshot)

        self.assertEqual(1, len(targets))
        target = targets[0]
        self.assertEqual(1, target.camera_id)
        self.assertEqual('North Gate', target.camera_name)
        self.assertEqual('rtsp://camera/1/h264/main', target.source_url)
        self.assertEqual('rtmp://127.0.0.1:1935/live/1', target.push_url)
        self.assertEqual('mpp', target.decode_backend)
        self.assertEqual('rga', target.decode_hwaccel)
        self.assertEqual('h264', target.input_codec)
        self.assertFalse(target.osd_enabled)
        self.assertEqual('h264_rkmpp', target.video_codec)

    def test_build_ffmpeg_command_uses_mpp_rga_pipeline_for_h264(self):
        target = media_worker.MediaTarget(
            camera_id=1,
            camera_name='North Gate',
            source_url='rtsp://camera/1/h264/main',
            push_url='rtmp://127.0.0.1:1935/live/1',
            play_url='http://zlm/live/1.flv',
            video_port=18082,
            ffmpeg_bin='ffmpeg',
            rtsp_transport='tcp',
            decode_backend='mpp',
            decode_hwaccel='rga',
            input_codec='h264',
            osd_enabled=False,
            video_codec='h264_rkmpp',
        )

        command = media_worker.build_ffmpeg_command(target)

        self.assertEqual('ffmpeg', command[0])
        self.assertIn('-rtsp_transport', command)
        self.assertIn('tcp', command)
        self.assertIn('-hwaccel', command)
        self.assertIn('rkmpp', command)
        self.assertIn('-hwaccel_output_format', command)
        self.assertIn('drm_prime', command)
        self.assertIn('-afbc', command)
        self.assertIn('rga', command)
        self.assertIn('h264_rkmpp', command)
        self.assertIn('-vf', command)
        filter_index = command.index('-vf') + 1
        self.assertIn('scale_rkrga', command[filter_index])
        self.assertEqual('rtmp://127.0.0.1:1935/live/1', command[-1])

    def test_build_ffmpeg_command_uses_hevc_decoder_for_h265_sources(self):
        target = media_worker.MediaTarget(
            camera_id=2,
            camera_name='Warehouse West',
            source_url='rtsp://camera/2/h265/main',
            push_url='rtmp://127.0.0.1:1935/live/2',
            play_url='http://zlm/live/2.flv',
            video_port=18083,
            ffmpeg_bin='ffmpeg',
            rtsp_transport='tcp',
            decode_backend='mpp',
            decode_hwaccel='rga',
            input_codec='h265',
            osd_enabled=False,
            video_codec='h264_rkmpp',
        )

        command = media_worker.build_ffmpeg_command(target)

        decoder_index = command.index('-c:v') + 1
        self.assertEqual('hevc_rkmpp', command[decoder_index])
        self.assertIn('scale_rkrga', command[command.index('-vf') + 1])
        self.assertEqual('rtmp://127.0.0.1:1935/live/2', command[-1])

    def test_build_ffmpeg_command_forces_reencode_when_rga_is_enabled(self):
        target = media_worker.MediaTarget(
            camera_id=3,
            camera_name='Warehouse South',
            source_url='rtsp://camera/3/h264/main',
            push_url='rtmp://127.0.0.1:1935/live/3',
            ffmpeg_bin='ffmpeg',
            rtsp_transport='tcp',
            decode_backend='mpp',
            decode_hwaccel='rga',
            input_codec='h264',
            osd_enabled=False,
            video_codec='copy',
        )

        command = media_worker.build_ffmpeg_command(target)

        encoder_index = len(command) - 1 - command[::-1].index('-c:v') + 1
        self.assertEqual('h264_rkmpp', command[encoder_index])
        self.assertIn('scale_rkrga', command[command.index('-vf') + 1])

    def test_sync_starts_new_sessions_and_stops_removed_ones(self):
        popen_calls = []
        terminated = []

        class FakeProcess:
            def __init__(self, command):
                self.command = command
                self.pid = len(popen_calls) + 1000
                self._poll = None

            def poll(self):
                return self._poll

            def terminate(self):
                terminated.append(('terminate', self.command[-1]))
                self._poll = 0

            def wait(self, timeout=None):
                self._poll = 0
                return 0

            def kill(self):
                terminated.append(('kill', self.command[-1]))
                self._poll = -9

        def fake_popen(command, **_kwargs):
            popen_calls.append(command)
            return FakeProcess(command)

        worker = media_worker.MediaWorker(popen_factory=fake_popen, capability_validator=lambda target: None)
        first_snapshot = {
            'media': {'ffmpeg_bin': 'ffmpeg', 'decode_backend': 'mpp', 'decode_hwaccel': 'rga', 'video_codec': 'h264_rkmpp'},
            'streams': [
                {'camera_id': 1, 'camera_name': 'A', 'rtsp_url': 'rtsp://camera/1/h264/main', 'push_url': 'rtmp://127.0.0.1/live/1', 'enabled': True},
                {'camera_id': 2, 'camera_name': 'B', 'rtsp_url': 'rtsp://camera/2/h265/main', 'push_url': 'rtmp://127.0.0.1/live/2', 'enabled': True},
            ],
        }
        second_snapshot = {
            'media': {'ffmpeg_bin': 'ffmpeg', 'decode_backend': 'mpp', 'decode_hwaccel': 'rga', 'video_codec': 'h264_rkmpp'},
            'streams': [
                {'camera_id': 2, 'camera_name': 'B', 'rtsp_url': 'rtsp://camera/2/h265/main', 'push_url': 'rtmp://127.0.0.1/live/2', 'enabled': True},
            ],
        }

        first_status = worker.sync(first_snapshot)
        second_status = worker.sync(second_snapshot)

        self.assertEqual(2, first_status['running_count'])
        self.assertEqual(2, len(popen_calls))
        self.assertEqual(1, second_status['running_count'])
        self.assertEqual([1], second_status['stopped_camera_ids'])
        self.assertEqual(('terminate', 'rtmp://127.0.0.1/live/1'), terminated[0])

    def test_status_counts_only_running_sessions(self):
        class FakeProcess:
            def __init__(self, pid, return_code):
                self.pid = pid
                self._return_code = return_code

            def poll(self):
                return self._return_code

        worker = media_worker.MediaWorker(popen_factory=lambda *args, **kwargs: None, capability_validator=lambda target: None)
        target = media_worker.MediaTarget(
            camera_id=1,
            camera_name='A',
            source_url='rtsp://camera/1/h264/main',
            push_url='rtmp://127.0.0.1/live/1',
        )
        worker._sessions = {
            1: media_worker.ManagedSession(target=target, process=FakeProcess(1001, None), command=['ffmpeg'], started_at=1.0),
            2: media_worker.ManagedSession(target=target, process=FakeProcess(1002, 1), command=['ffmpeg'], started_at=2.0),
        }

        status = worker.status()

        self.assertEqual(1, status['running_count'])
        self.assertEqual([True, False], [item['running'] for item in status['sessions']])

    def test_sync_restarts_exited_session_and_reports_recycled_camera(self):
        popen_calls = []

        class FakeProcess:
            def __init__(self, command, pid, return_code=None):
                self.command = command
                self.pid = pid
                self._poll = return_code

            def poll(self):
                return self._poll

            def terminate(self):
                self._poll = 0

            def wait(self, timeout=None):
                self._poll = 0
                return 0

            def kill(self):
                self._poll = -9

        next_pid = {'value': 2000}

        def fake_popen(command, **_kwargs):
            next_pid['value'] += 1
            popen_calls.append(command)
            return FakeProcess(command, next_pid['value'])

        worker = media_worker.MediaWorker(popen_factory=fake_popen, capability_validator=lambda target: None)
        snapshot = {
            'media': {'ffmpeg_bin': 'ffmpeg', 'decode_backend': 'mpp', 'decode_hwaccel': 'rga', 'video_codec': 'h264_rkmpp'},
            'streams': [
                {'camera_id': 2, 'camera_name': 'B', 'rtsp_url': 'rtsp://camera/2/h265/main', 'push_url': 'rtmp://127.0.0.1/live/2', 'enabled': True},
            ],
        }

        first_status = worker.sync(snapshot)
        worker._sessions[2].process._poll = 1
        second_status = worker.sync(snapshot)

        self.assertEqual([2], first_status['started_camera_ids'])
        self.assertEqual([2], second_status['stopped_camera_ids'])
        self.assertEqual([2], second_status['started_camera_ids'])
        self.assertEqual(2, len(popen_calls))
        self.assertEqual(1, second_status['running_count'])

    def test_sync_fails_fast_when_mpp_rga_backend_is_missing(self):
        worker = media_worker.MediaWorker(
            popen_factory=lambda *args, **kwargs: self.fail('popen should not be called'),
            capability_validator=lambda target: (_ for _ in ()).throw(RuntimeError('MPP+RGA media pipeline is unavailable')),
        )
        snapshot = {
            'media': {'ffmpeg_bin': 'ffmpeg', 'decode_backend': 'mpp', 'decode_hwaccel': 'rga', 'video_codec': 'h264_rkmpp'},
            'streams': [
                {'camera_id': 9, 'camera_name': 'A', 'rtsp_url': 'rtsp://camera/9/h264/main', 'push_url': 'rtmp://127.0.0.1/live/9', 'enabled': True},
            ],
        }

        with self.assertRaises(RuntimeError) as ctx:
            worker.sync(snapshot)

        self.assertIn('MPP+RGA', str(ctx.exception))

    def test_run_worker_loop_writes_latest_state_file(self):
        events = []

        class FakeFetcher:
            def fetch(self):
                return {'media': {}, 'streams': []}

        class FakeWorker:
            def sync(self, snapshot):
                return {'desired_count': len(snapshot.get('streams', [])), 'running_count': 0, 'started_camera_ids': [], 'stopped_camera_ids': [], 'sessions': []}

            def close(self):
                return None

        with tempfile.TemporaryDirectory() as temp_dir:
            state_path = pathlib.Path(temp_dir) / 'media-worker.state.json'
            result = media_worker.run_worker_loop(
                fetcher=FakeFetcher(),
                worker=FakeWorker(),
                interval_sec=0.01,
                once=True,
                sleep_fn=lambda _seconds: None,
                emit_fn=events.append,
                stop_flag={'value': False},
                state_path=state_path,
            )

            payload = json.loads(state_path.read_text(encoding='utf-8'))

        self.assertEqual(0, result['error_count'])
        self.assertEqual('running', payload['status'])
        self.assertEqual(0, payload['sync']['running_count'])
        self.assertEqual('running', events[0]['status'])

    def test_run_worker_loop_continues_after_fetch_timeout(self):
        events = []

        class FakeFetcher:
            def __init__(self):
                self.calls = 0

            def fetch(self):
                self.calls += 1
                if self.calls == 1:
                    raise TimeoutError('snapshot timed out')
                return {'media': {}, 'streams': []}

        class FakeWorker:
            def __init__(self):
                self.sync_calls = 0
                self.closed = False

            def sync(self, snapshot):
                self.sync_calls += 1
                return {'desired_count': len(snapshot.get('streams', [])), 'running_count': 0, 'started_camera_ids': [], 'stopped_camera_ids': [], 'sessions': []}

            def close(self):
                self.closed = True

        fetcher = FakeFetcher()
        worker = FakeWorker()
        result = media_worker.run_worker_loop(
            fetcher=fetcher,
            worker=worker,
            interval_sec=0.01,
            once=False,
            sleep_fn=lambda _seconds: None,
            emit_fn=events.append,
            stop_flag={'value': False},
            iteration_limit=2,
        )

        self.assertEqual(1, result['error_count'])
        self.assertEqual(1, worker.sync_calls)
        self.assertEqual('error', events[0]['status'])
        self.assertEqual('running', events[1]['status'])


if __name__ == '__main__':
    unittest.main()