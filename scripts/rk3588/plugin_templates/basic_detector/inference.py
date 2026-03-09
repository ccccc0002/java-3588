from __future__ import annotations

import sys
from pathlib import Path

TEMPLATE_DIR = Path(__file__).resolve().parent
RK3588_SCRIPT_DIR = TEMPLATE_DIR.parent.parent
if str(RK3588_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(RK3588_SCRIPT_DIR))

import plugin_sdk
from rknn_plugin_support import RKNNLiteSession


def load(package_context):
    execution_mode = str(package_context.config.get('execution_mode', 'mock')).strip().lower()
    detection_config = plugin_sdk.load_detection_config(package_context)
    state = {
        'execution_mode': execution_mode,
        'default_label': str(package_context.config.get('default_label', 'object')),
        'default_score': float(package_context.config.get('default_score', 0.75)),
        'session': None,
        'labels': detection_config['labels'],
        'label_aliases_zh': detection_config['label_aliases_zh'],
        'enabled_class_ids': detection_config['enabled_class_ids'],
        'enabled_labels': detection_config['enabled_labels'],
        'alert_labels': detection_config['alert_labels'],
        'event_type': detection_config['event_type'],
    }
    if execution_mode == 'rknn_lite':
        state['session'] = RKNNLiteSession.open(
            model_path=package_context.model_path,
            core_mask=str(package_context.config.get('core_mask', 'auto')),
            target=str(package_context.config.get('target_platform', 'rk3588')),
            device_id=package_context.config.get('device_id'),
        )
    return state


def infer(request_payload, runtime_plan, package_context, runtime_state):
    frame = request_payload.get('frame') if isinstance(request_payload.get('frame'), dict) else {}
    if runtime_state.get('execution_mode') == 'rknn_lite':
        tensor = frame.get('tensor')
        if not isinstance(tensor, list):
            raise RuntimeError('frame.tensor is required when execution_mode=rknn_lite')
        outputs = runtime_state['session'].infer(inputs=[tensor])
        return {
            'mode': 'rknn_lite',
            'outputs': outputs,
            'source_meta': {'source_kind': 'tensor', 'resolved_source': 'frame.tensor'},
            'plan_ready_stream_count': runtime_plan.get('ready_stream_count', 0),
        }

    candidates = []
    for index, item in enumerate(request_payload.get('roi') or []):
        if not isinstance(item, dict):
            continue
        candidates.append({
            'label': str(item.get('label', f'template-{index}')),
            'score': float(item.get('score', runtime_state.get('default_score', 0.75))),
            'bbox': list(item.get('bbox', [0, 0, 0, 0])),
            'class_id': item.get('class_id'),
        })
    if not candidates:
        candidates.append({
            'label': runtime_state.get('default_label', 'object'),
            'score': runtime_state.get('default_score', 0.75),
            'bbox': [0, 0, 0, 0],
        })
    return {
        'mode': 'mock',
        'candidates': candidates,
        'source_meta': {
            'source_kind': 'template',
            'resolved_source': str(frame.get('source', 'template://frame') or 'template://frame'),
        },
        'plan_ready_stream_count': runtime_plan.get('ready_stream_count', 0),
    }


def cleanup(runtime_state, package_context):
    session = runtime_state.get('session') if isinstance(runtime_state, dict) else None
    if session is not None:
        session.release()
