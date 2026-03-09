from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

PLUGIN_DIR = Path(__file__).resolve().parent
RK3588_SCRIPT_DIR = PLUGIN_DIR.parent.parent
if str(RK3588_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(RK3588_SCRIPT_DIR))
if str(PLUGIN_DIR) not in sys.path:
    sys.path.insert(0, str(PLUGIN_DIR))

import decode as decode_impl
import preprocess as preprocess_impl
from rknn_plugin_support import RKNNLiteSession

read_image_bgr = decode_impl.read_image_bgr
read_stream_frame_opencv = decode_impl.read_stream_frame_opencv
read_stream_frame_ffmpeg = decode_impl.read_stream_frame_ffmpeg
resolve_optional_path = decode_impl.resolve_optional_path
normalize_input_size = preprocess_impl.normalize_input_size
prepare_image_input = preprocess_impl.prepare_image_input


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
    return decode_impl.load_frame_bgr(request_payload, package_context)
