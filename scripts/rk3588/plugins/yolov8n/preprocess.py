from __future__ import annotations

from typing import Tuple

import cv2
import numpy as np


def normalize_input_size(value) -> Tuple[int, int]:
    if isinstance(value, (list, tuple)) and len(value) == 2:
        width = int(value[0])
        height = int(value[1])
        if width > 0 and height > 0:
            return (width, height)
    return (640, 640)


def prepare_image_input(image_bgr, input_size):
    target_w, target_h = input_size
    src_h, src_w = image_bgr.shape[:2]
    scale = min(target_w / float(src_w), target_h / float(src_h))
    resized_w = max(1, int(round(src_w * scale)))
    resized_h = max(1, int(round(src_h * scale)))
    resized = cv2.resize(image_bgr, (resized_w, resized_h), interpolation=cv2.INTER_LINEAR)
    canvas = np.zeros((target_h, target_w, 3), dtype=np.uint8)
    pad_left = (target_w - resized_w) // 2
    pad_top = (target_h - resized_h) // 2
    canvas[pad_top:pad_top + resized_h, pad_left:pad_left + resized_w] = resized
    rgb = cv2.cvtColor(canvas, cv2.COLOR_BGR2RGB)
    batched = np.expand_dims(rgb, axis=0)
    return batched, {
        'original_width': src_w,
        'original_height': src_h,
        'scale': scale,
        'pad_left': pad_left,
        'pad_top': pad_top,
        'resized_width': resized_w,
        'resized_height': resized_h,
        'input_width': target_w,
        'input_height': target_h,
    }
