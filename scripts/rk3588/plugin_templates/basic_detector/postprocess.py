from __future__ import annotations

import sys
from pathlib import Path

TEMPLATE_DIR = Path(__file__).resolve().parent
RK3588_SCRIPT_DIR = TEMPLATE_DIR.parent.parent
if str(RK3588_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(RK3588_SCRIPT_DIR))

import plugin_sdk


def postprocess(raw_outputs, request_payload, runtime_plan, package_context, runtime_state):
    return plugin_sdk.finalize_detections(
        detections=list(raw_outputs.get('candidates') or []),
        request_payload=request_payload,
        package_context=package_context,
        runtime_state=runtime_state,
        source_meta=raw_outputs.get('source_meta') or {},
        plugin_meta={
            'execution_mode': raw_outputs.get('mode') or 'mock',
            'plan_ready_stream_count': raw_outputs.get('plan_ready_stream_count', 0),
            'template': True,
        },
        attributes={
            'raw_output_count': len(raw_outputs.get('outputs') or []),
        },
    )
