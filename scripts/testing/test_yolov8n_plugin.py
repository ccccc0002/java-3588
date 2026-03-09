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
