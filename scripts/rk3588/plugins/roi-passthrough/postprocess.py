from __future__ import annotations

import sys
from pathlib import Path

PLUGIN_DIR = Path(__file__).resolve().parent
RK3588_SCRIPT_DIR = PLUGIN_DIR.parent.parent
if str(RK3588_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(RK3588_SCRIPT_DIR))

import plugin_sdk


def postprocess(raw_outputs, request_payload, runtime_plan, package_context, runtime_state):
    mode = raw_outputs.get('mode')
    if mode == 'rknn_lite':
        return plugin_sdk.finalize_detections(
            detections=[],
            request_payload=request_payload,
            package_context=package_context,
            runtime_state=runtime_state,
            source_meta=raw_outputs.get('source_meta') or {},
            plugin_meta={
                'execution_mode': mode,
                'plan_ready_stream_count': raw_outputs.get('plan_ready_stream_count', 0),
            },
            attributes={
                'raw_output_count': len(raw_outputs.get('outputs') or []),
            },
        )
    return plugin_sdk.finalize_detections(
        detections=list(raw_outputs.get('candidates') or []),
        request_payload=request_payload,
        package_context=package_context,
        runtime_state=runtime_state,
        source_meta=raw_outputs.get('source_meta') or {},
        plugin_meta={
            'execution_mode': mode or 'mock',
            'plan_ready_stream_count': raw_outputs.get('plan_ready_stream_count', 0),
        },
    )
