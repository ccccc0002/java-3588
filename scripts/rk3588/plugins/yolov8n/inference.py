from __future__ import annotations

import sys
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

import cv2
import numpy as np

PLUGIN_DIR = Path(__file__).resolve().parent
RK3588_SCRIPT_DIR = PLUGIN_DIR.parent.parent
if str(RK3588_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(RK3588_SCRIPT_DIR))

from rknn_plugin_support import RKNNLiteSession


def load(package_context):
    input_size = normalize_input_size(package_context.config.get('input_size'))
    labels = load_labels(package_context)
    model_path = resolve_model_path(package_context)
    session = RKNNLiteSession.open(
        model_path=model_path,
        core_mask=str(package_context.config.get('core_mask', 'auto')),
        target=str(package_context.config.get('target_platform', 'rk3588')),
        device_id=package_context.config.get('device_id'),
    )
    return {
        'session': session,
        'labels': labels,
        'input_size': input_size,
        'obj_threshold': float(package_context.config.get('obj_threshold', 0.25)),
        'nms_threshold': float(package_context.config.get('nms_threshold', 0.45)),
        'model_path': str(model_path),
        'default_test_image_path': resolve_optional_path(package_context, package_context.config.get('default_test_image_path')),
    }


def infer(request_payload, runtime_plan, package_context, runtime_state):
    image_bgr, source_meta = load_frame_bgr(request_payload, package_context)
    input_image, prep_meta = prepare_image_input(image_bgr, runtime_state['input_size'])
    outputs = runtime_state['session'].infer(inputs=[input_image])
    return {
        'outputs': outputs,
        'source_meta': source_meta,
        'prep_meta': prep_meta,
        'labels': runtime_state['labels'],
        'obj_threshold': runtime_state['obj_threshold'],
        'nms_threshold': runtime_state['nms_threshold'],
        'model_path': runtime_state['model_path'],
        'plan_ready_stream_count': runtime_plan.get('ready_stream_count', 0),
    }


def cleanup(runtime_state, package_context):
    session = runtime_state.get('session') if isinstance(runtime_state, dict) else None
    if session is not None:
        session.release()


def resolve_model_path(package_context):
    candidates = [
        package_context.config.get('rknn_model_path'),
        package_context.config.get('fallback_model_path'),
        str(package_context.model_path),
    ]
    for candidate in candidates:
        resolved = resolve_optional_path(package_context, candidate)
        if resolved is not None and resolved.exists() and resolved.is_file() and resolved.stat().st_size > 0:
            return resolved
    raise RuntimeError('no usable RKNN model file found for yolov8n plugin')


def load_labels(package_context):
    labels_path = resolve_optional_path(package_context, package_context.config.get('labels_path'))
    if labels_path is None or not labels_path.exists():
        return []
    return [line.strip() for line in labels_path.read_text(encoding='utf-8-sig').splitlines() if line.strip()]


def load_frame_bgr(request_payload, package_context):
    frame = request_payload.get('frame') if isinstance(request_payload.get('frame'), dict) else {}
    direct_path = first_non_blank(frame.get('image_path'), frame.get('path'), frame.get('local_path'))
    if direct_path:
        resolved = resolve_source_path(package_context, direct_path)
        return read_image_bgr(resolved), {'source_kind': 'image_path', 'resolved_source': str(resolved)}

    source = str(frame.get('source', '')).strip()
    if source == 'test://frame':
        fallback = resolve_optional_path(package_context, package_context.config.get('default_test_image_path'))
        if fallback is None or not fallback.exists():
            raise RuntimeError('default_test_image_path is not configured or missing')
        return read_image_bgr(fallback), {'source_kind': 'default_test_image', 'resolved_source': str(fallback.resolve())}
    if not source:
        raise RuntimeError('frame.source is required')
    if source.startswith('file://'):
        resolved = Path(source[7:]).expanduser().resolve()
        return read_image_bgr(resolved), {'source_kind': 'file_url', 'resolved_source': str(resolved)}
    if source.startswith('http://') or source.startswith('https://'):
        return read_image_url(source), {'source_kind': 'http_url', 'resolved_source': source}
    if is_stream_source(source):
        return read_stream_frame(source), {'source_kind': 'stream', 'resolved_source': source}
    resolved = resolve_source_path(package_context, source)
    return read_image_bgr(resolved), {'source_kind': 'filesystem', 'resolved_source': str(resolved)}


def read_image_url(source):
    with urllib.request.urlopen(source, timeout=10) as response:
        payload = response.read()
    array = np.frombuffer(payload, dtype=np.uint8)
    image = cv2.imdecode(array, cv2.IMREAD_COLOR)
    if image is None:
        raise RuntimeError(f'failed to decode image url: {source}')
    return image


def read_stream_frame(source):
    capture = cv2.VideoCapture(source)
    try:
        if not capture.isOpened():
            raise RuntimeError(f'failed to open stream source: {source}')
        ok, frame = capture.read()
        if not ok or frame is None:
            raise RuntimeError(f'failed to read frame from stream source: {source}')
        return frame
    finally:
        capture.release()


def read_image_bgr(path):
    resolved = Path(path).resolve()
    payload = np.fromfile(str(resolved), dtype=np.uint8)
    image = cv2.imdecode(payload, cv2.IMREAD_COLOR)
    if image is None:
        raise RuntimeError(f'failed to read image file: {path}')
    return image


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


def normalize_input_size(value):
    if isinstance(value, (list, tuple)) and len(value) == 2:
        width = int(value[0])
        height = int(value[1])
        if width > 0 and height > 0:
            return (width, height)
    return (640, 640)


def resolve_optional_path(package_context, value):
    text = str(value or '').strip()
    if not text:
        return None
    candidate = Path(text).expanduser()
    if candidate.is_absolute():
        return candidate.resolve()
    return (package_context.root_dir / candidate).resolve()


def resolve_source_path(package_context, value):
    resolved = resolve_optional_path(package_context, value)
    if resolved is None:
        raise RuntimeError('frame source path is blank')
    return resolved


def is_stream_source(source):
    parsed = urlparse(source)
    return parsed.scheme.lower() in {'rtsp', 'rtmp', 'udp'}


def first_non_blank(*values):
    for value in values:
        text = str(value or '').strip()
        if text:
            return text
    return ''
