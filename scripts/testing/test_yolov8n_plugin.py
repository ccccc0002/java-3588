import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest
from typing import Tuple

ROOT = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'plugins' / 'yolov8n'
INFERENCE_MODULE_PATH = ROOT / 'inference.py'
POSTPROCESS_MODULE_PATH = ROOT / 'postprocess.py'


def load_module(module_name: str, path: pathlib.Path):
    spec = importlib.util.spec_from_file_location(module_name, path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f'failed to load module from {path}')
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


yolov8n_inference = load_module('rk3588_yolov8n_inference', INFERENCE_MODULE_PATH)
yolov8n_postprocess = load_module('rk3588_yolov8n_postprocess', POSTPROCESS_MODULE_PATH)


class FakeContext:
    def __init__(self, root_dir: pathlib.Path, config: dict, model_path: pathlib.Path):
        self.root_dir = root_dir
        self.config = config
        self.model_path = model_path


class FakeCapture:
    def __init__(self, source: str, frames=None):
        self.source = source
        self.frames = list(frames or [1])
        self.read_index = 0
        self.set_calls = []
        self.release_count = 0

    def isOpened(self):
        return True

    def read(self):
        if self.read_index >= len(self.frames):
            return False, None
        value = self.frames[self.read_index]
        self.read_index += 1
        frame = yolov8n_inference.np.full((2, 3, 3), value, dtype=yolov8n_inference.np.uint8)
        return True, frame

    def release(self):
        self.release_count += 1

    def set(self, prop, value):
        self.set_calls.append((prop, value))
        if prop == yolov8n_inference.decode_impl.cv2.CAP_PROP_POS_FRAMES and value == 0:
            self.read_index = 0
            return True
        return True


def write_png(path: pathlib.Path, width: int, height: int, rgb: Tuple[int, int, int]) -> None:
    import cv2
    import numpy as np
    image = np.zeros((height, width, 3), dtype=np.uint8)
    image[:, :] = (rgb[2], rgb[1], rgb[0])
    ok, encoded = cv2.imencode('.png', image)
    if not ok:
        raise RuntimeError('failed to encode png test image')
    path.write_bytes(encoded.tobytes())


class YoloV8nInferenceTests(unittest.TestCase):
    def test_plugin_json_references_existing_default_assets(self):
        payload = json.loads((ROOT / 'config' / 'plugin.json').read_text(encoding='utf-8-sig'))

        self.assertEqual('mpp', payload['stream_decode_backend'])
        self.assertEqual('rga', payload['stream_decode_hwaccel'])
        self.assertIn('stream_decode_ffmpeg_bin', payload)
        self.assertTrue((ROOT / payload['default_test_image_path']).exists())
        self.assertTrue((ROOT / payload['labels_path']).exists())
        self.assertTrue((ROOT / payload['fallback_model_path']).exists())
        self.assertIn('label_aliases_zh', payload)
        self.assertEqual('人员', payload['label_aliases_zh']['person'])
        self.assertEqual('公交车', payload['label_aliases_zh']['bus'])
        self.assertIn('alert_labels', payload)

    def test_resolve_model_path_prefers_existing_override(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            override_model = root / 'external' / 'yolov8.rknn'
            override_model.parent.mkdir(parents=True, exist_ok=True)
            override_model.write_bytes(b'model')
            package_model = root / 'model' / 'placeholder.rknn'
            package_model.parent.mkdir(parents=True, exist_ok=True)
            package_model.write_bytes(b'placeholder')
            context = FakeContext(
                root_dir=root,
                config={
                    'rknn_model_path': str(override_model),
                    'fallback_model_path': 'model/placeholder.rknn',
                },
                model_path=package_model,
            )

            resolved = yolov8n_inference.resolve_model_path(context)

            self.assertEqual(resolved, override_model)

    def test_load_labels_prefers_class_names_from_config(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            context = FakeContext(
                root_dir=root,
                config={
                    'class_names': ['person', 'bus'],
                    'labels_path': 'config/missing.txt',
                },
                model_path=root / 'model' / 'placeholder.rknn',
            )

            labels = yolov8n_inference.load_labels(context)

            self.assertEqual(labels, ['person', 'bus'])

    def test_load_builds_runtime_state_from_detection_config(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            model_path = root / 'model' / 'demo.rknn'
            model_path.parent.mkdir(parents=True, exist_ok=True)
            model_path.write_bytes(b'model')
            labels_path = root / 'config' / 'labels.txt'
            labels_path.parent.mkdir(parents=True, exist_ok=True)
            labels_path.write_text('person\nbus\n', encoding='utf-8')
            context = FakeContext(
                root_dir=root,
                config={
                    'rknn_model_path': str(model_path),
                    'labels_path': 'config/labels.txt',
                    'label_aliases_zh': {'person': '人员', 'bus': '公交车'},
                    'enabled_class_ids': ['0'],
                    'enabled_labels': ['公交车'],
                    'alert_labels': ['公交车'],
                    'alert_event_type': 'vision.alert.custom',
                    'input_size': [640, 640],
                    'obj_threshold': 0.3,
                    'nms_threshold': 0.5,
                    'core_mask': 'auto',
                    'target_platform': 'rk3588',
                },
                model_path=model_path,
            )
            original_open = yolov8n_inference.RKNNLiteSession.open
            original_create_stream_manager = yolov8n_inference.decode_impl.create_stream_manager
            released = {'value': False}
            stream_closed = {'value': False}
            try:
                class FakeSession:
                    def release(self):
                        released['value'] = True

                class FakeStreamManager:
                    def close(self):
                        stream_closed['value'] = True

                yolov8n_inference.RKNNLiteSession.open = staticmethod(lambda model_path, core_mask, target, device_id=None: FakeSession())
                yolov8n_inference.decode_impl.create_stream_manager = lambda package_context: FakeStreamManager()

                runtime_state = yolov8n_inference.load(context)
                yolov8n_inference.cleanup(runtime_state, context)
            finally:
                yolov8n_inference.RKNNLiteSession.open = original_open
                yolov8n_inference.decode_impl.create_stream_manager = original_create_stream_manager

            self.assertEqual(['person', 'bus'], runtime_state['labels'])
            self.assertEqual({'person': '人员', 'bus': '公交车'}, runtime_state['label_aliases_zh'])
            self.assertEqual({0}, runtime_state['enabled_class_ids'])
            self.assertEqual({'公交车'}, runtime_state['enabled_labels'])
            self.assertEqual({'公交车'}, runtime_state['alert_labels'])
            self.assertEqual('vision.alert.custom', runtime_state['event_type'])
            self.assertEqual((640, 640), runtime_state['input_size'])
            self.assertEqual(0.3, runtime_state['obj_threshold'])
            self.assertEqual(0.5, runtime_state['nms_threshold'])
            self.assertTrue(hasattr(runtime_state['stream_manager'], 'close'))
            self.assertTrue(released['value'])
            self.assertTrue(stream_closed['value'])

    def test_load_frame_bgr_uses_default_test_image_for_test_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            image_path = root / 'model' / 'bus.png'
            image_path.parent.mkdir(parents=True, exist_ok=True)
            write_png(image_path, 12, 8, (10, 20, 30))
            context = FakeContext(
                root_dir=root,
                config={
                    'default_test_image_path': 'model/bus.png',
                },
                model_path=root / 'model' / 'placeholder.rknn',
            )

            image_bgr, meta = yolov8n_inference.load_frame_bgr({'frame': {'source': 'test://frame'}}, context)

            self.assertEqual(tuple(image_bgr.shape[:2]), (8, 12))
            self.assertEqual(meta['source_kind'], 'default_test_image')
            self.assertEqual(meta['resolved_source'], str(image_path.resolve()))


    def test_load_frame_bgr_prefers_request_decode_hint_backend(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            context = FakeContext(
                root_dir=root,
                config={
                    'stream_decode_backend': 'opencv',
                },
                model_path=root / 'model' / 'placeholder.rknn',
            )
            sample = yolov8n_inference.np.zeros((6, 10, 3), dtype=yolov8n_inference.np.uint8)
            original_read_stream_frame = yolov8n_inference.decode_impl.read_stream_frame
            try:
                captured = {}

                def fake_read_stream_frame(source, preferred_backend='auto', stream_manager=None, codec_hint='auto', ffmpeg_bin='ffmpeg'):
                    captured['preferred_backend'] = preferred_backend
                    return sample, preferred_backend

                yolov8n_inference.decode_impl.read_stream_frame = fake_read_stream_frame
                image_bgr, meta = yolov8n_inference.load_frame_bgr(
                    {
                        'frame': {'source': 'rtsp://demo/stream'},
                        'decode_hints': {'backend': 'mpp', 'hwaccel': 'rga', 'codec': 'h265'},
                    },
                    context,
                )
            finally:
                yolov8n_inference.decode_impl.read_stream_frame = original_read_stream_frame

            self.assertEqual(tuple(image_bgr.shape[:2]), (6, 10))
            self.assertEqual('mpp', captured['preferred_backend'])
            self.assertEqual('mpp', meta['decoder_backend'])
            self.assertEqual('mpp', meta['decoder_backend_requested'])
            self.assertEqual('rga', meta['decoder_hwaccel'])

    def test_load_frame_bgr_resolves_stream_decode_ffmpeg_bin_from_config(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            ffmpeg_bin = root / 'toolchains' / 'ffmpeg-rockchip' / 'bin' / 'ffmpeg'
            ffmpeg_bin.parent.mkdir(parents=True, exist_ok=True)
            ffmpeg_bin.write_text('#!/bin/sh\n', encoding='utf-8')
            context = FakeContext(
                root_dir=root,
                config={
                    'stream_decode_backend': 'mpp',
                    'stream_decode_ffmpeg_bin': 'toolchains/ffmpeg-rockchip/bin/ffmpeg',
                },
                model_path=root / 'model' / 'placeholder.rknn',
            )
            sample = yolov8n_inference.np.zeros((6, 10, 3), dtype=yolov8n_inference.np.uint8)
            original_read_stream_frame = yolov8n_inference.decode_impl.read_stream_frame
            try:
                captured = {}

                def fake_read_stream_frame(source, preferred_backend='auto', stream_manager=None, codec_hint='auto', ffmpeg_bin='ffmpeg'):
                    captured['preferred_backend'] = preferred_backend
                    captured['ffmpeg_bin'] = ffmpeg_bin
                    return sample, preferred_backend

                yolov8n_inference.decode_impl.read_stream_frame = fake_read_stream_frame
                image_bgr, meta = yolov8n_inference.load_frame_bgr(
                    {'frame': {'source': 'rtsp://demo/stream'}},
                    context,
                )
            finally:
                yolov8n_inference.decode_impl.read_stream_frame = original_read_stream_frame

            self.assertEqual(tuple(image_bgr.shape[:2]), (6, 10))
            self.assertEqual('mpp', captured['preferred_backend'])
            self.assertEqual(str(ffmpeg_bin.resolve()), captured['ffmpeg_bin'])
            self.assertEqual(str(ffmpeg_bin.resolve()), meta['decoder_ffmpeg_bin'])

    def test_load_frame_bgr_falls_back_to_ffmpeg_for_stream_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            context = FakeContext(
                root_dir=root,
                config={
                    'stream_decode_backend': 'auto',
                },
                model_path=root / 'model' / 'placeholder.rknn',
            )
            sample = yolov8n_inference.np.zeros((6, 10, 3), dtype=yolov8n_inference.np.uint8)
            original_read_stream_opencv = yolov8n_inference.decode_impl.read_stream_frame_opencv
            original_read_stream_mpp = yolov8n_inference.decode_impl.read_stream_frame_mpp
            original_read_stream_mpp = yolov8n_inference.decode_impl.read_stream_frame_mpp
            original_read_stream_ffmpeg = yolov8n_inference.decode_impl.read_stream_frame_ffmpeg
            try:
                def fail_opencv(source, stream_manager=None):
                    raise RuntimeError('opencv stream open failed')

                def ok_ffmpeg(source, ffmpeg_bin='ffmpeg'):
                    return sample

                yolov8n_inference.decode_impl.read_stream_frame_opencv = fail_opencv
                yolov8n_inference.decode_impl.read_stream_frame_mpp = lambda source, codec_hint='auto', ffmpeg_bin='ffmpeg': (_ for _ in ()).throw(RuntimeError('mpp failed'))
                yolov8n_inference.decode_impl.read_stream_frame_ffmpeg = ok_ffmpeg
                image_bgr, meta = yolov8n_inference.load_frame_bgr({'frame': {'source': 'rtsp://demo/stream'}}, context)
            finally:
                yolov8n_inference.decode_impl.read_stream_frame_opencv = original_read_stream_opencv
                yolov8n_inference.decode_impl.read_stream_frame_mpp = original_read_stream_mpp
                yolov8n_inference.decode_impl.read_stream_frame_ffmpeg = original_read_stream_ffmpeg

            self.assertEqual(tuple(image_bgr.shape[:2]), (6, 10))
            self.assertEqual(meta['source_kind'], 'stream')
            self.assertEqual(meta['decoder_backend'], 'ffmpeg')

    def test_load_frame_bgr_fails_fast_when_explicit_mpp_backend_is_unavailable(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            context = FakeContext(
                root_dir=root,
                config={'stream_decode_backend': 'mpp'},
                model_path=root / 'model' / 'placeholder.rknn',
            )
            original_ensure = yolov8n_inference.decode_impl.ensure_mpp_decode_support
            try:
                yolov8n_inference.decode_impl.ensure_mpp_decode_support = lambda ffmpeg_bin='ffmpeg': (_ for _ in ()).throw(RuntimeError('MPP+RGA decode backend is unavailable'))
                with self.assertRaises(RuntimeError) as ctx:
                    yolov8n_inference.load_frame_bgr(
                        {'frame': {'source': 'rtsp://demo/stream'}, 'decode_hints': {'backend': 'mpp', 'hwaccel': 'rga'}},
                        context,
                    )
            finally:
                yolov8n_inference.decode_impl.ensure_mpp_decode_support = original_ensure

            self.assertIn('MPP+RGA', str(ctx.exception))

    def test_load_frame_bgr_uses_stream_decoder_for_local_video_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            video_path = root / 'clips' / 'sample.mp4'
            video_path.parent.mkdir(parents=True, exist_ok=True)
            video_path.write_bytes(b'fake-video')
            context = FakeContext(
                root_dir=root,
                config={'stream_decode_backend': 'auto'},
                model_path=root / 'model' / 'placeholder.rknn',
            )
            sample = yolov8n_inference.np.zeros((4, 5, 3), dtype=yolov8n_inference.np.uint8)
            original_read_stream_frame = yolov8n_inference.decode_impl.read_stream_frame
            try:
                def fake_read_stream_frame(source, preferred_backend='auto', stream_manager=None, codec_hint='auto', ffmpeg_bin='ffmpeg'):
                    return sample, 'opencv'

                yolov8n_inference.decode_impl.read_stream_frame = fake_read_stream_frame
                image_bgr, meta = yolov8n_inference.load_frame_bgr({'frame': {'source': str(video_path)}}, context)
            finally:
                yolov8n_inference.decode_impl.read_stream_frame = original_read_stream_frame

            self.assertEqual(tuple(image_bgr.shape[:2]), (4, 5))
            self.assertEqual(meta['source_kind'], 'video_file')
            self.assertEqual(meta['decoder_backend'], 'opencv')

    def test_read_stream_frame_resolves_ffmpeg_binary_before_mpp_decode(self):
        sample = yolov8n_inference.np.zeros((6, 10, 3), dtype=yolov8n_inference.np.uint8)
        original_resolve = yolov8n_inference.decode_impl.resolve_ffmpeg_binary
        original_ensure = yolov8n_inference.decode_impl.ensure_mpp_decode_support
        original_read_stream_mpp = yolov8n_inference.decode_impl.read_stream_frame_mpp
        try:
            captured = {}
            yolov8n_inference.decode_impl.resolve_ffmpeg_binary = lambda ffmpeg_bin='ffmpeg', repo_root=None, env=None, which_fn=None: '/resolved/ffmpeg-rockchip'

            def fake_ensure(ffmpeg_bin='ffmpeg'):
                captured['ensure_ffmpeg_bin'] = ffmpeg_bin
                return None

            def fake_read_stream_mpp(source, codec_hint='auto', ffmpeg_bin='ffmpeg'):
                captured['mpp_ffmpeg_bin'] = ffmpeg_bin
                return sample

            yolov8n_inference.decode_impl.ensure_mpp_decode_support = fake_ensure
            yolov8n_inference.decode_impl.read_stream_frame_mpp = fake_read_stream_mpp

            frame, backend = yolov8n_inference.decode_impl.read_stream_frame(
                'rtsp://demo/stream',
                preferred_backend='mpp',
                codec_hint='h265',
            )
        finally:
            yolov8n_inference.decode_impl.resolve_ffmpeg_binary = original_resolve
            yolov8n_inference.decode_impl.ensure_mpp_decode_support = original_ensure
            yolov8n_inference.decode_impl.read_stream_frame_mpp = original_read_stream_mpp

        self.assertEqual('mpp', backend)
        self.assertEqual((6, 10, 3), tuple(frame.shape))
        self.assertEqual('/resolved/ffmpeg-rockchip', captured['ensure_ffmpeg_bin'])
        self.assertEqual('/resolved/ffmpeg-rockchip', captured['mpp_ffmpeg_bin'])

    def test_stream_session_manager_reuses_capture_for_same_source(self):
        created = []
        original_video_capture = yolov8n_inference.decode_impl.cv2.VideoCapture
        original_ffmpeg = yolov8n_inference.decode_impl.read_stream_frame_ffmpeg
        try:
            def fake_video_capture(source):
                capture = FakeCapture(source, frames=[1, 2, 3])
                created.append(capture)
                return capture

            yolov8n_inference.decode_impl.cv2.VideoCapture = fake_video_capture
            yolov8n_inference.decode_impl.read_stream_frame_ffmpeg = lambda source: self.fail('ffmpeg fallback should not be used')
            manager = yolov8n_inference.decode_impl.StreamSessionManager(now_fn=lambda: 100.0, ttl_sec=60.0)

            first_frame, first_backend = yolov8n_inference.decode_impl.read_stream_frame(
                'rtsp://demo/stream',
                preferred_backend='opencv',
                stream_manager=manager,
            )
            second_frame, second_backend = yolov8n_inference.decode_impl.read_stream_frame(
                'rtsp://demo/stream',
                preferred_backend='opencv',
                stream_manager=manager,
            )
        finally:
            yolov8n_inference.decode_impl.cv2.VideoCapture = original_video_capture
            yolov8n_inference.decode_impl.read_stream_frame_ffmpeg = original_ffmpeg
            if 'manager' in locals():
                manager.close()

        self.assertEqual(len(created), 1)
        self.assertEqual(first_backend, 'opencv')
        self.assertEqual(second_backend, 'opencv')
        self.assertEqual(int(first_frame[0, 0, 0]), 1)
        self.assertEqual(int(second_frame[0, 0, 0]), 2)

    def test_stream_session_manager_cleanup_releases_cached_capture(self):
        created = []
        original_video_capture = yolov8n_inference.decode_impl.cv2.VideoCapture
        try:
            def fake_video_capture(source):
                capture = FakeCapture(source, frames=[9])
                created.append(capture)
                return capture

            yolov8n_inference.decode_impl.cv2.VideoCapture = fake_video_capture
            runtime_state = {
                'stream_manager': yolov8n_inference.decode_impl.StreamSessionManager(now_fn=lambda: 100.0, ttl_sec=60.0),
            }
            yolov8n_inference.decode_impl.read_stream_frame(
                'rtsp://demo/stream',
                preferred_backend='opencv',
                stream_manager=runtime_state['stream_manager'],
            )

            yolov8n_inference.cleanup(runtime_state, None)
        finally:
            yolov8n_inference.decode_impl.cv2.VideoCapture = original_video_capture

        self.assertEqual(len(created), 1)
        self.assertEqual(created[0].release_count, 1)
        self.assertEqual(runtime_state['stream_manager'].session_count(), 0)

    def test_full_pipeline_applies_runtime_config_to_alert_events(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            model_path = root / 'model' / 'demo.rknn'
            model_path.parent.mkdir(parents=True, exist_ok=True)
            model_path.write_bytes(b'model')
            labels_path = root / 'config' / 'labels.txt'
            labels_path.parent.mkdir(parents=True, exist_ok=True)
            labels_path.write_text('person\nbus\n', encoding='utf-8')
            context = FakeContext(
                root_dir=root,
                config={
                    'rknn_model_path': str(model_path),
                    'labels_path': 'config/labels.txt',
                    'label_aliases_zh': {'person': '人员', 'bus': '公交车'},
                    'enabled_labels': ['人员', '公交车'],
                    'alert_labels': ['公交车'],
                    'alert_event_type': 'vision.alert.custom',
                    'input_size': [640, 640],
                },
                model_path=model_path,
            )
            context.plugin_id = 'yolov8n'
            context.version = '1.0.0'

            original_open = yolov8n_inference.RKNNLiteSession.open
            original_create_stream_manager = yolov8n_inference.decode_impl.create_stream_manager
            original_load_frame_bgr = yolov8n_inference.load_frame_bgr
            original_prepare_image_input = yolov8n_inference.prepare_image_input
            original_decode_outputs = yolov8n_postprocess.decode_outputs
            original_restore_boxes = yolov8n_postprocess.restore_boxes
            cleanup_flags = {'session': False, 'stream': False}
            infer_inputs = {}
            try:
                class FakeSession:
                    def infer(self, inputs):
                        infer_inputs['shape'] = tuple(inputs[0].shape)
                        return ['raw-output']

                    def release(self):
                        cleanup_flags['session'] = True

                class FakeStreamManager:
                    def session_count(self):
                        return 1

                    def close(self):
                        cleanup_flags['stream'] = True

                yolov8n_inference.RKNNLiteSession.open = staticmethod(lambda model_path, core_mask, target, device_id=None: FakeSession())
                yolov8n_inference.decode_impl.create_stream_manager = lambda package_context: FakeStreamManager()
                yolov8n_inference.load_frame_bgr = lambda request_payload, package_context, runtime_state=None: (
                    yolov8n_inference.np.zeros((12, 20, 3), dtype=yolov8n_inference.np.uint8),
                    {'source_kind': 'stream', 'resolved_source': 'rtsp://demo/stream'},
                )
                yolov8n_inference.prepare_image_input = lambda image_bgr, input_size: (
                    yolov8n_inference.np.zeros((1, input_size[1], input_size[0], 3), dtype=yolov8n_inference.np.uint8),
                    {'scale': 1.0, 'pad_left': 0, 'pad_top': 0, 'original_width': 20, 'original_height': 12},
                )
                yolov8n_postprocess.decode_outputs = lambda outputs, obj_threshold, nms_threshold, input_size: (
                    yolov8n_postprocess.np.array([[0.0, 0.0, 10.0, 10.0], [10.0, 1.0, 20.0, 12.0]], dtype=yolov8n_postprocess.np.float32),
                    yolov8n_postprocess.np.array([0, 1], dtype=yolov8n_postprocess.np.int32),
                    yolov8n_postprocess.np.array([0.9, 0.8], dtype=yolov8n_postprocess.np.float32),
                )
                yolov8n_postprocess.restore_boxes = lambda boxes, prep_meta: boxes

                runtime_state = yolov8n_inference.load(context)
                request_payload = {'trace_id': 'trace-e2e', 'camera_id': 7, 'frame': {'source': 'rtsp://demo/stream', 'timestamp_ms': 1000}}
                runtime_plan = {'ready_stream_count': 1}
                raw_outputs = yolov8n_inference.infer(request_payload, runtime_plan, context, runtime_state)
                result = yolov8n_postprocess.postprocess(raw_outputs, request_payload, runtime_plan, context, runtime_state)
                yolov8n_inference.cleanup(runtime_state, context)
            finally:
                yolov8n_inference.RKNNLiteSession.open = original_open
                yolov8n_inference.decode_impl.create_stream_manager = original_create_stream_manager
                yolov8n_inference.load_frame_bgr = original_load_frame_bgr
                yolov8n_inference.prepare_image_input = original_prepare_image_input
                yolov8n_postprocess.decode_outputs = original_decode_outputs
                yolov8n_postprocess.restore_boxes = original_restore_boxes

            self.assertEqual((1, 640, 640, 3), infer_inputs['shape'])
            self.assertEqual(['person', 'bus'], [item['label'] for item in result['detections']])
            self.assertEqual(['人员', '公交车'], [item['label_zh'] for item in result['detections']])
            self.assertEqual([False, True], [item['alert'] for item in result['detections']])
            self.assertEqual('vision.alert.custom', result['events'][0]['event_type'])
            self.assertEqual('公交车', result['events'][0]['label_zh'])
            self.assertEqual(1, result['plugin_meta']['active_stream_session_count'])
            self.assertTrue(cleanup_flags['session'])
            self.assertTrue(cleanup_flags['stream'])

    def test_postprocess_maps_zh_labels_filters_enabled_classes_and_alert_labels(self):
        original_decode_outputs = yolov8n_postprocess.decode_outputs
        original_restore_boxes = yolov8n_postprocess.restore_boxes
        try:
            yolov8n_postprocess.decode_outputs = lambda outputs, obj_threshold, nms_threshold, input_size: (
                yolov8n_postprocess.np.array([
                    [0.0, 0.0, 10.0, 10.0],
                    [20.0, 20.0, 30.0, 30.0],
                    [40.0, 40.0, 50.0, 50.0],
                ], dtype=yolov8n_postprocess.np.float32),
                yolov8n_postprocess.np.array([0, 5, 2], dtype=yolov8n_postprocess.np.int32),
                yolov8n_postprocess.np.array([0.9, 0.8, 0.7], dtype=yolov8n_postprocess.np.float32),
            )
            yolov8n_postprocess.restore_boxes = lambda boxes, prep_meta: boxes
            runtime_state = {
                'input_size': (640, 640),
                'labels': ['person', 'bicycle', 'car', 'motorcycle', 'airplane', 'bus'],
                'label_aliases_zh': {
                    'person': '\u4eba\u5458',
                    'bus': '\u516c\u4ea4\u8f66',
                },
                'enabled_class_ids': set(),
                'enabled_labels': {'person', '\u516c\u4ea4\u8f66'},
                'alert_labels': {'\u516c\u4ea4\u8f66'},
            }
            raw_outputs = {
                'outputs': [object(), object()],
                'obj_threshold': 0.25,
                'nms_threshold': 0.45,
                'prep_meta': {},
                'source_meta': {'source_kind': 'stream', 'resolved_source': 'rtsp://demo/stream'},
                'model_path': '/tmp/demo.rknn',
                'plan_ready_stream_count': 0,
            }

            result = yolov8n_postprocess.postprocess(raw_outputs, {}, {}, None, runtime_state)
        finally:
            yolov8n_postprocess.decode_outputs = original_decode_outputs
            yolov8n_postprocess.restore_boxes = original_restore_boxes

        self.assertEqual([item['label'] for item in result['detections']], ['person', 'bus'])
        self.assertEqual([item['label_zh'] for item in result['detections']], ['\u4eba\u5458', '\u516c\u4ea4\u8f66'])
        self.assertEqual([item['alert'] for item in result['detections']], [False, True])
        self.assertEqual([item['label'] for item in result['alerts']], ['bus'])
        self.assertEqual(result['attributes']['alert_detection_count'], 1)
        self.assertEqual(result['attributes']['detection_count'], 2)

    def test_postprocess_defaults_all_detections_to_alert_when_filter_is_empty(self):
        original_decode_outputs = yolov8n_postprocess.decode_outputs
        original_restore_boxes = yolov8n_postprocess.restore_boxes
        try:
            yolov8n_postprocess.decode_outputs = lambda outputs, obj_threshold, nms_threshold, input_size: (
                yolov8n_postprocess.np.array([[0.0, 0.0, 10.0, 10.0]], dtype=yolov8n_postprocess.np.float32),
                yolov8n_postprocess.np.array([0], dtype=yolov8n_postprocess.np.int32),
                yolov8n_postprocess.np.array([0.9], dtype=yolov8n_postprocess.np.float32),
            )
            yolov8n_postprocess.restore_boxes = lambda boxes, prep_meta: boxes
            runtime_state = {
                'input_size': (640, 640),
                'labels': ['person'],
                'label_aliases_zh': {'person': '\u4eba\u5458'},
                'enabled_class_ids': set(),
                'enabled_labels': set(),
                'alert_labels': set(),
            }
            raw_outputs = {
                'outputs': [object()],
                'obj_threshold': 0.25,
                'nms_threshold': 0.45,
                'prep_meta': {},
                'source_meta': {'source_kind': 'image', 'resolved_source': 'test://frame'},
                'model_path': '/tmp/demo.rknn',
                'plan_ready_stream_count': 0,
            }

            result = yolov8n_postprocess.postprocess(raw_outputs, {}, {}, None, runtime_state)
        finally:
            yolov8n_postprocess.decode_outputs = original_decode_outputs
            yolov8n_postprocess.restore_boxes = original_restore_boxes

        self.assertEqual(result['detections'][0]['label_zh'], '\u4eba\u5458')
        self.assertTrue(result['detections'][0]['alert'])
        self.assertEqual(len(result['alerts']), 1)

    def test_prepare_image_input_letterboxes_to_640_square(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            image_path = root / 'sample.png'
            write_png(image_path, 320, 160, (50, 60, 70))
            image_bgr = yolov8n_inference.read_image_bgr(image_path)

            input_image, prep = yolov8n_inference.prepare_image_input(image_bgr, (640, 640))

            self.assertEqual(tuple(input_image.shape), (1, 640, 640, 3))
            self.assertEqual(prep['scale'], 2.0)
            self.assertEqual(prep['pad_left'], 0)
            self.assertEqual(prep['pad_top'], 160)


if __name__ == '__main__':
    unittest.main()


