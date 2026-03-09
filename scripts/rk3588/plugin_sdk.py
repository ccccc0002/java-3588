from __future__ import annotations

import json
from pathlib import Path
from typing import Any


DEFAULT_EVENT_TYPE = 'vision.alert'


def resolve_optional_path(package_context, value):
    text = str(value or '').strip()
    if not text:
        return None
    candidate = Path(text).expanduser()
    if candidate.is_absolute():
        return candidate.resolve()
    return (package_context.root_dir / candidate).resolve()


def normalize_text_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        candidates = value.split(',')
    elif isinstance(value, (list, tuple, set)):
        candidates = list(value)
    else:
        return []
    result = []
    for item in candidates:
        text = str(item).strip()
        if text:
            result.append(text)
    return result


def normalize_text_set(value: Any) -> set[str]:
    return set(normalize_text_list(value))


def normalize_int_set(value: Any) -> set[int]:
    normalized = set()
    for item in normalize_text_list(value):
        try:
            normalized.add(int(item))
        except ValueError:
            continue
    return normalized


def load_labels(package_context) -> list[str]:
    inline_labels = normalize_text_list(package_context.config.get('class_names'))
    if inline_labels:
        return inline_labels
    labels_path = resolve_optional_path(package_context, package_context.config.get('labels_path'))
    if labels_path is None or not labels_path.exists():
        return []
    return [line.strip() for line in labels_path.read_text(encoding='utf-8-sig').splitlines() if line.strip()]


def load_label_aliases_zh(package_context) -> dict[str, str]:
    aliases: dict[str, str] = {}
    aliases_path = resolve_optional_path(package_context, package_context.config.get('label_aliases_zh_path'))
    if aliases_path is not None and aliases_path.exists():
        payload = json.loads(aliases_path.read_text(encoding='utf-8-sig'))
        if isinstance(payload, dict):
            aliases.update(_normalize_alias_map(payload))
    inline_aliases = package_context.config.get('label_aliases_zh')
    if isinstance(inline_aliases, dict):
        aliases.update(_normalize_alias_map(inline_aliases))
    return aliases


def load_detection_config(package_context) -> dict[str, Any]:
    return {
        'labels': load_labels(package_context),
        'label_aliases_zh': load_label_aliases_zh(package_context),
        'enabled_class_ids': normalize_int_set(package_context.config.get('enabled_class_ids')),
        'enabled_labels': normalize_text_set(package_context.config.get('enabled_labels')),
        'alert_labels': normalize_text_set(package_context.config.get('alert_labels')),
        'event_type': str(package_context.config.get('alert_event_type', DEFAULT_EVENT_TYPE)).strip() or DEFAULT_EVENT_TYPE,
    }


def normalize_postprocess_contract(value: Any) -> dict[str, Any]:
    if isinstance(value, dict):
        result = dict(value)
    elif isinstance(value, list):
        result = {'detections': value}
    else:
        result = {'detections': []}
    detections = result.get('detections')
    alerts = result.get('alerts')
    events = result.get('events')
    result['detections'] = detections if isinstance(detections, list) else []
    result['alerts'] = alerts if isinstance(alerts, list) else []
    result['events'] = events if isinstance(events, list) else []
    plugin_meta = result.get('plugin_meta')
    attributes = result.get('attributes')
    result['plugin_meta'] = plugin_meta if isinstance(plugin_meta, dict) else {}
    result['attributes'] = attributes if isinstance(attributes, dict) else {}
    return result


def finalize_detections(
    detections: list[dict[str, Any]],
    request_payload: dict[str, Any],
    package_context,
    runtime_state: dict[str, Any],
    source_meta: dict[str, Any] | None = None,
    plugin_meta: dict[str, Any] | None = None,
    attributes: dict[str, Any] | None = None,
) -> dict[str, Any]:
    labels = runtime_state.get('labels') or []
    label_aliases_zh = normalize_alias_map(runtime_state.get('label_aliases_zh'))
    enabled_class_ids = normalize_int_set(runtime_state.get('enabled_class_ids'))
    enabled_labels = normalize_text_set(runtime_state.get('enabled_labels'))
    alert_labels = normalize_text_set(runtime_state.get('alert_labels'))
    event_type = str(runtime_state.get('event_type') or DEFAULT_EVENT_TYPE).strip() or DEFAULT_EVENT_TYPE
    source_meta = source_meta if isinstance(source_meta, dict) else {}
    normalized_detections = []
    alerts = []
    for raw_detection in detections or []:
        detection = normalize_detection(raw_detection, labels, label_aliases_zh)
        if detection is None:
            continue
        if not is_detection_enabled(detection, enabled_class_ids, enabled_labels):
            continue
        detection['alert'] = is_alert_detection(detection, alert_labels)
        normalized_detections.append(detection)
        if detection['alert']:
            alerts.append(dict(detection))
    events = build_alert_events(alerts, request_payload, package_context, source_meta, event_type)
    merged_plugin_meta = dict(plugin_meta or {})
    merged_plugin_meta.setdefault('alert_label_count', len(alert_labels))
    merged_attributes = dict(attributes or {})
    merged_attributes.setdefault('detection_count', len(normalized_detections))
    merged_attributes.setdefault('alert_detection_count', len(alerts))
    return {
        'detections': normalized_detections,
        'alerts': alerts,
        'events': events,
        'plugin_meta': merged_plugin_meta,
        'attributes': merged_attributes,
    }


def normalize_detection(raw_detection: Any, labels: list[str], label_aliases_zh: dict[str, str]) -> dict[str, Any] | None:
    if not isinstance(raw_detection, dict):
        return None
    class_id = to_int(raw_detection.get('class_id'))
    label = str(raw_detection.get('label') or '').strip()
    if not label and class_id is not None and 0 <= class_id < len(labels):
        label = str(labels[class_id]).strip()
    if not label:
        label = f'class-{class_id}' if class_id is not None else 'object'
    label_zh = resolve_label_zh(label_aliases_zh, class_id, label)
    bbox = raw_detection.get('bbox')
    if isinstance(bbox, (list, tuple)):
        bbox_list = [float(item) for item in list(bbox)]
    else:
        bbox_list = []
    normalized = {
        'label': label,
        'label_zh': label_zh,
        'score': to_float(raw_detection.get('score'), 0.0),
        'bbox': bbox_list,
        'alert': bool(raw_detection.get('alert', False)),
    }
    if class_id is not None:
        normalized['class_id'] = class_id
    for key in ('track_id', 'attributes', 'mask'):
        if key in raw_detection:
            normalized[key] = raw_detection[key]
    return normalized


def resolve_label_zh(label_aliases_zh: dict[str, str], class_id: int | None, label: str) -> str:
    class_key = '' if class_id is None else str(class_id)
    return label_aliases_zh.get(label) or (label_aliases_zh.get(class_key) if class_key else None) or label


def is_detection_enabled(detection: dict[str, Any], enabled_class_ids: set[int], enabled_labels: set[str]) -> bool:
    if not enabled_class_ids and not enabled_labels:
        return True
    class_id = detection.get('class_id')
    if isinstance(class_id, int) and class_id in enabled_class_ids:
        return True
    return bool(match_text_filter(detection, enabled_labels))


def is_alert_detection(detection: dict[str, Any], alert_labels: set[str]) -> bool:
    if not alert_labels:
        return True
    return bool(match_text_filter(detection, alert_labels))


def match_text_filter(detection: dict[str, Any], filters: set[str]) -> set[str]:
    candidates = {
        str(detection.get('label', '')).strip(),
        str(detection.get('label_zh', '')).strip(),
    }
    class_id = detection.get('class_id')
    if isinstance(class_id, int):
        candidates.add(str(class_id))
    return candidates & set(filters or set())


def build_alert_events(
    alerts: list[dict[str, Any]],
    request_payload: dict[str, Any],
    package_context,
    source_meta: dict[str, Any],
    event_type: str,
) -> list[dict[str, Any]]:
    frame = request_payload.get('frame') if isinstance(request_payload.get('frame'), dict) else {}
    events = []
    for index, detection in enumerate(alerts):
        event = {
            'event_type': event_type,
            'trace_id': str(request_payload.get('trace_id', '')).strip(),
            'camera_id': to_int(request_payload.get('camera_id')),
            'model_id': to_int(request_payload.get('model_id')),
            'algorithm_id': to_int(request_payload.get('algorithm_id')),
            'plugin_id': getattr(package_context, 'plugin_id', ''),
            'plugin_version': getattr(package_context, 'version', ''),
            'label': detection.get('label'),
            'label_zh': detection.get('label_zh'),
            'score': detection.get('score'),
            'bbox': list(detection.get('bbox') or []),
            'source_kind': source_meta.get('source_kind'),
            'resolved_source': source_meta.get('resolved_source'),
            'timestamp_ms': to_int(frame.get('timestamp_ms')),
            'event_index': index,
        }
        class_id = detection.get('class_id')
        if isinstance(class_id, int):
            event['class_id'] = class_id
        events.append(event)
    return events


def normalize_alias_map(value: Any) -> dict[str, str]:
    return _normalize_alias_map(value) if isinstance(value, dict) else {}


def _normalize_alias_map(value: dict[str, Any]) -> dict[str, str]:
    result = {}
    for key, item in value.items():
        text_key = str(key).strip()
        text_item = str(item).strip()
        if text_key and text_item:
            result[text_key] = text_item
    return result


def to_int(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def to_float(value: Any, default: float) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return float(default)
