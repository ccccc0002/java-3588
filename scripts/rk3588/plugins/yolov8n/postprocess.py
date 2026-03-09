from __future__ import annotations

import numpy as np


def postprocess(raw_outputs, request_payload, runtime_plan, package_context, runtime_state):
    boxes, classes, scores = decode_outputs(
        raw_outputs.get('outputs') or [],
        float(raw_outputs.get('obj_threshold', 0.25)),
        float(raw_outputs.get('nms_threshold', 0.45)),
        tuple(runtime_state.get('input_size', (640, 640))),
    )
    prep_meta = raw_outputs.get('prep_meta') or {}
    source_meta = raw_outputs.get('source_meta') or {}
    labels = runtime_state.get('labels') or raw_outputs.get('labels') or []
    label_aliases_zh = normalize_label_aliases(runtime_state.get('label_aliases_zh'))
    enabled_class_ids = normalize_int_set(runtime_state.get('enabled_class_ids'))
    enabled_labels = normalize_text_set(runtime_state.get('enabled_labels'))
    alert_labels = normalize_text_set(runtime_state.get('alert_labels'))

    detections = []
    alerts = []
    restored = restore_boxes(boxes, prep_meta)
    for index in range(len(restored)):
        class_id = int(classes[index])
        label = resolve_label(labels, class_id)
        label_zh = resolve_label_zh(label_aliases_zh, class_id, label)
        if not is_detection_enabled(class_id, label, label_zh, enabled_class_ids, enabled_labels):
            continue
        alert = is_alert_detection(class_id, label, label_zh, alert_labels)
        bbox = [float(v) for v in restored[index].tolist()]
        detection = {
            'label': label,
            'label_zh': label_zh,
            'score': float(scores[index]),
            'bbox': bbox,
            'class_id': class_id,
            'alert': alert,
        }
        detections.append(detection)
        if alert:
            alerts.append(detection)
    return {
        'detections': detections,
        'alerts': alerts,
        'plugin_meta': {
            'model_path': raw_outputs.get('model_path'),
            'source_kind': source_meta.get('source_kind'),
            'resolved_source': source_meta.get('resolved_source'),
            'plan_ready_stream_count': raw_outputs.get('plan_ready_stream_count', 0),
            'active_stream_session_count': raw_outputs.get('active_stream_session_count', 0),
            'obj_threshold': float(raw_outputs.get('obj_threshold', 0.25)),
            'nms_threshold': float(raw_outputs.get('nms_threshold', 0.45)),
            'alert_label_count': len(alert_labels),
        },
        'attributes': {
            'raw_output_count': len(raw_outputs.get('outputs') or []),
            'detection_count': len(detections),
            'alert_detection_count': len(alerts),
        },
    }


def resolve_label(labels, class_id):
    if 0 <= class_id < len(labels):
        text = str(labels[class_id]).strip()
        if text:
            return text
    return f'class-{class_id}'


def resolve_label_zh(label_aliases_zh, class_id, label):
    class_key = str(class_id)
    return label_aliases_zh.get(label) or label_aliases_zh.get(class_key) or label


def is_detection_enabled(class_id, label, label_zh, enabled_class_ids, enabled_labels):
    if not enabled_class_ids and not enabled_labels:
        return True
    if class_id in enabled_class_ids:
        return True
    return bool(match_text_filter(class_id, label, label_zh, enabled_labels))


def is_alert_detection(class_id, label, label_zh, alert_labels):
    if not alert_labels:
        return True
    return bool(match_text_filter(class_id, label, label_zh, alert_labels))


def match_text_filter(class_id, label, label_zh, filters):
    candidates = {str(class_id), str(label).strip(), str(label_zh).strip()}
    return candidates & set(filters or set())


def normalize_label_aliases(value):
    if not isinstance(value, dict):
        return {}
    return {str(key).strip(): str(item).strip() for key, item in value.items() if str(key).strip() and str(item).strip()}


def normalize_text_set(value):
    if value is None:
        return set()
    if isinstance(value, str):
        items = value.split(',')
    elif isinstance(value, (list, tuple, set)):
        items = list(value)
    else:
        return set()
    return {str(item).strip() for item in items if str(item).strip()}


def normalize_int_set(value):
    normalized = set()
    for item in normalize_text_set(value):
        try:
            normalized.add(int(item))
        except ValueError:
            continue
    return normalized


def decode_outputs(outputs, obj_threshold, nms_threshold, input_size):
    tensors = [normalize_output(np.array(item)) for item in outputs]
    if len(tensors) < 6 or len(tensors) % 3 != 0:
        raise RuntimeError(f'unexpected yolov8 output tensor count: {len(tensors)}')
    branches = 3
    pair_per_branch = len(tensors) // branches
    boxes, classes_conf, box_conf = [], [], []
    for index in range(branches):
        boxes.append(box_process(tensors[pair_per_branch * index], input_size))
        classes_conf.append(tensors[pair_per_branch * index + 1])
        box_conf.append(np.ones_like(tensors[pair_per_branch * index + 1][:, :1, :, :], dtype=np.float32))

    def flatten(tensor):
        channels = tensor.shape[1]
        return tensor.transpose(0, 2, 3, 1).reshape(-1, channels)

    flat_boxes = [flatten(item) for item in boxes]
    flat_classes = [flatten(item) for item in classes_conf]
    flat_conf = [flatten(item) for item in box_conf]

    boxes_all, classes_all, scores_all = [], [], []
    for branch_idx in range(branches):
        branch_boxes = flat_boxes[branch_idx]
        branch_classes = flat_classes[branch_idx]
        branch_conf = flat_conf[branch_idx].reshape(-1)
        class_ids = np.argmax(branch_classes, axis=1)
        class_scores = np.max(branch_classes, axis=1)
        scores = class_scores * branch_conf
        keep = np.where(scores >= obj_threshold)[0]
        if keep.size == 0:
            continue
        boxes_all.append(branch_boxes[keep])
        classes_all.append(class_ids[keep])
        scores_all.append(scores[keep])
    if not boxes_all:
        return np.empty((0, 4), dtype=np.float32), np.empty((0,), dtype=np.int32), np.empty((0,), dtype=np.float32)

    boxes_cat = np.concatenate(boxes_all, axis=0)
    classes_cat = np.concatenate(classes_all, axis=0)
    scores_cat = np.concatenate(scores_all, axis=0)

    final_boxes, final_classes, final_scores = [], [], []
    for class_id in np.unique(classes_cat):
        idxs = np.where(classes_cat == class_id)[0]
        keep = nms_boxes(boxes_cat[idxs], scores_cat[idxs], nms_threshold)
        if keep.size == 0:
            continue
        final_boxes.append(boxes_cat[idxs][keep])
        final_classes.append(classes_cat[idxs][keep])
        final_scores.append(scores_cat[idxs][keep])
    if not final_boxes:
        return np.empty((0, 4), dtype=np.float32), np.empty((0,), dtype=np.int32), np.empty((0,), dtype=np.float32)
    return np.concatenate(final_boxes, axis=0), np.concatenate(final_classes, axis=0), np.concatenate(final_scores, axis=0)


def normalize_output(tensor):
    if tensor.ndim == 3:
        tensor = tensor[np.newaxis, ...]
    if tensor.ndim != 4:
        raise RuntimeError(f'unexpected tensor rank: {tensor.ndim}')
    if tensor.shape[1] in (1, 64, 80):
        return tensor.astype(np.float32)
    if tensor.shape[-1] in (1, 64, 80):
        return np.transpose(tensor, (0, 3, 1, 2)).astype(np.float32)
    return tensor.astype(np.float32)


def dfl(position):
    n, c, h, w = position.shape
    parts = 4
    bins = c // parts
    reshaped = position.reshape(n, parts, bins, h, w)
    reshaped = softmax(reshaped, axis=2)
    weights = np.arange(bins, dtype=np.float32).reshape(1, 1, bins, 1, 1)
    return (reshaped * weights).sum(axis=2)


def softmax(value, axis):
    shifted = value - np.max(value, axis=axis, keepdims=True)
    exp = np.exp(shifted)
    return exp / np.sum(exp, axis=axis, keepdims=True)


def box_process(position, input_size):
    grid_h, grid_w = position.shape[2:4]
    col, row = np.meshgrid(np.arange(grid_w), np.arange(grid_h))
    col = col.reshape(1, 1, grid_h, grid_w)
    row = row.reshape(1, 1, grid_h, grid_w)
    grid = np.concatenate((col, row), axis=1)
    stride = np.array([input_size[1] // grid_h, input_size[0] // grid_w], dtype=np.float32).reshape(1, 2, 1, 1)
    position = dfl(position)
    box_xy = grid + 0.5 - position[:, 0:2, :, :]
    box_xy2 = grid + 0.5 + position[:, 2:4, :, :]
    return np.concatenate((box_xy * stride, box_xy2 * stride), axis=1)


def nms_boxes(boxes, scores, threshold):
    x1 = boxes[:, 0]
    y1 = boxes[:, 1]
    x2 = boxes[:, 2]
    y2 = boxes[:, 3]
    areas = (x2 - x1) * (y2 - y1)
    order = scores.argsort()[::-1]
    keep = []
    while order.size > 0:
        i = order[0]
        keep.append(i)
        xx1 = np.maximum(x1[i], x1[order[1:]])
        yy1 = np.maximum(y1[i], y1[order[1:]])
        xx2 = np.minimum(x2[i], x2[order[1:]])
        yy2 = np.minimum(y2[i], y2[order[1:]])
        w1 = np.maximum(0.0, xx2 - xx1)
        h1 = np.maximum(0.0, yy2 - yy1)
        inter = w1 * h1
        union = areas[i] + areas[order[1:]] - inter
        iou = np.divide(inter, union, out=np.zeros_like(inter), where=union > 0)
        inds = np.where(iou <= threshold)[0]
        order = order[inds + 1]
    return np.array(keep, dtype=np.int32)


def restore_boxes(boxes, prep_meta):
    if boxes.size == 0:
        return boxes.reshape(0, 4)
    pad_left = float(prep_meta.get('pad_left', 0.0))
    pad_top = float(prep_meta.get('pad_top', 0.0))
    scale = float(prep_meta.get('scale', 1.0)) or 1.0
    original_width = float(prep_meta.get('original_width', 0.0))
    original_height = float(prep_meta.get('original_height', 0.0))
    restored = boxes.astype(np.float32).copy()
    restored[:, [0, 2]] = (restored[:, [0, 2]] - pad_left) / scale
    restored[:, [1, 3]] = (restored[:, [1, 3]] - pad_top) / scale
    restored[:, [0, 2]] = np.clip(restored[:, [0, 2]], 0.0, original_width)
    restored[:, [1, 3]] = np.clip(restored[:, [1, 3]], 0.0, original_height)
    return restored
