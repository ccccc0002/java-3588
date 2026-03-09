from __future__ import annotations

import json
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
        'label_aliases_zh': load_label_aliases_zh(package_context),
        'enabled_class_ids': normalize_int_set(package_context.config.get('enabled_class_ids')),
        'enabled_labels': normalize_text_set(package_context.config.get('enabled_labels')),
        'alert_labels': normalize_text_set(package_context.config.get('alert_labels')),
        'input_size': input_size,
        'obj_threshold': float(package_context.config.get('obj_threshold', 0.25)),
        'nms_threshold': float(package_context.config.get('nms_threshold', 0.45)),
        'model_path': str(model_path),
        'default_test_image_path': resolve_optional_path(package_context, package_context.config.get('default_test_image_path')),
        'stream_manager': decode_impl.create_stream_manager(package_context),
    }


def infer(request_payload, runtime_plan, package_context, runtime_state):
    image_bgr, source_meta = load_frame_bgr(request_payload, package_context, runtime_state)
    input_image, prep_meta = prepare_image_input(image_bgr, runtime_state['input_size'])
    outputs = runtime_state['session'].infer(inputs=[input_image])
    stream_manager = runtime_state.get('stream_manager') if isinstance(runtime_state, dict) else None
    return {
        'outputs': outputs,
        'source_meta': source_meta,
        'prep_meta': prep_meta,
        'labels': runtime_state['labels'],
        'obj_threshold': runtime_state['obj_threshold'],
        'nms_threshold': runtime_state['nms_threshold'],
        'model_path': runtime_state['model_path'],
        'plan_ready_stream_count': runtime_plan.get('ready_stream_count', 0),
        'active_stream_session_count': stream_manager.session_count() if stream_manager is not None else 0,
    }


def cleanup(runtime_state, package_context):
    stream_manager = runtime_state.get('stream_manager') if isinstance(runtime_state, dict) else None
    if stream_manager is not None:
        stream_manager.close()
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
    inline_labels = package_context.config.get('class_names')
    normalized_inline = normalize_text_list(inline_labels)
    if normalized_inline:
        return normalized_inline
    labels_path = resolve_optional_path(package_context, package_context.config.get('labels_path'))
    if labels_path is None or not labels_path.exists():
        return []
    return [line.strip() for line in labels_path.read_text(encoding='utf-8-sig').splitlines() if line.strip()]


def load_label_aliases_zh(package_context):
    aliases = {}
    aliases_path = resolve_optional_path(package_context, package_context.config.get('label_aliases_zh_path'))
    if aliases_path is not None and aliases_path.exists():
        payload = json.loads(aliases_path.read_text(encoding='utf-8-sig'))
        if isinstance(payload, dict):
            aliases.update({str(key).strip(): str(value).strip() for key, value in payload.items() if str(key).strip() and str(value).strip()})
    inline_aliases = package_context.config.get('label_aliases_zh')
    if isinstance(inline_aliases, dict):
        aliases.update({str(key).strip(): str(value).strip() for key, value in inline_aliases.items() if str(key).strip() and str(value).strip()})
    return aliases


def normalize_text_list(value):
    if value is None:
        return []
    if isinstance(value, str):
        candidates = value.split(',')
    elif isinstance(value, (list, tuple, set)):
        candidates = list(value)
    else:
        return []
    return [str(item).strip() for item in candidates if str(item).strip()]


def normalize_text_set(value):
    return set(normalize_text_list(value))


def normalize_int_set(value):
    normalized = set()
    for item in normalize_text_list(value):
        try:
            normalized.add(int(item))
        except ValueError:
            continue
    return normalized


def load_frame_bgr(request_payload, package_context, runtime_state=None):
    return decode_impl.load_frame_bgr(request_payload, package_context, runtime_state)
