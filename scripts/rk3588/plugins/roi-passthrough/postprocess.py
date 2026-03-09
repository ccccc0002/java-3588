from __future__ import annotations


def postprocess(raw_outputs, request_payload, runtime_plan, package_context, runtime_state):
    mode = raw_outputs.get('mode')
    if mode == 'rknn_lite':
        return {
            'detections': [],
            'attributes': {
                'raw_output_count': len(raw_outputs.get('outputs') or []),
            },
            'plugin_meta': {
                'execution_mode': mode,
                'plan_ready_stream_count': raw_outputs.get('plan_ready_stream_count', 0),
            },
        }
    return {
        'detections': list(raw_outputs.get('candidates') or []),
        'plugin_meta': {
            'execution_mode': mode or 'mock',
            'plan_ready_stream_count': raw_outputs.get('plan_ready_stream_count', 0),
        },
    }
