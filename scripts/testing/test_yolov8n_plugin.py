import importlib.util
import pathlib
import sys
import tempfile
import unittest
from typing import Tuple

MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'plugins' / 'yolov8n' / 'inference.py'
SPEC = importlib.util.spec_from_file_location('rk3588_yolov8n_inference', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'failed to load module from {MODULE_PATH}')
yolov8n_inference = importlib.util.module_from_spec(SPEC)
sys.modules['rk3588_yolov8n_inference'] = yolov8n_inference
SPEC.loader.exec_module(yolov8n_inference)


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
            original_read_stream_ffmpeg = yolov8n_inference.decode_impl.read_stream_frame_ffmpeg
            try:
                def fail_opencv(source):
                    raise RuntimeError('opencv stream open failed')

                def ok_ffmpeg(source):
                    return sample

                yolov8n_inference.decode_impl.read_stream_frame_opencv = fail_opencv
                yolov8n_inference.decode_impl.read_stream_frame_ffmpeg = ok_ffmpeg
                image_bgr, meta = yolov8n_inference.load_frame_bgr({'frame': {'source': 'rtsp://demo/stream'}}, context)
            finally:
                yolov8n_inference.decode_impl.read_stream_frame_opencv = original_read_stream_opencv
                yolov8n_inference.decode_impl.read_stream_frame_ffmpeg = original_read_stream_ffmpeg

            self.assertEqual(tuple(image_bgr.shape[:2]), (6, 10))
            self.assertEqual(meta['source_kind'], 'stream')
            self.assertEqual(meta['decoder_backend'], 'ffmpeg')

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
                def fake_read_stream_frame(source, preferred_backend='auto', stream_manager=None):
                    return sample, 'opencv'

                yolov8n_inference.decode_impl.read_stream_frame = fake_read_stream_frame
                image_bgr, meta = yolov8n_inference.load_frame_bgr({'frame': {'source': str(video_path)}}, context)
            finally:
                yolov8n_inference.decode_impl.read_stream_frame = original_read_stream_frame

            self.assertEqual(tuple(image_bgr.shape[:2]), (4, 5))
            self.assertEqual(meta['source_kind'], 'video_file')
            self.assertEqual(meta['decoder_backend'], 'opencv')

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
