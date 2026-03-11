from __future__ import annotations

import base64
import sys
from pathlib import Path

import cv2
import numpy as np

PLUGIN_DIR = Path(__file__).resolve().parent
RK3588_SCRIPT_DIR = PLUGIN_DIR.parent.parent
if str(RK3588_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(RK3588_SCRIPT_DIR))

import plugin_sdk


def postprocess(raw_outputs, request_payload, runtime_plan, package_context, runtime_state):
    boxes, classes, scores = decode_outputs(
        raw_outputs.get('outputs') or [],
        float(raw_outputs.get('obj_threshold', 0.25)),
        float(raw_outputs.get('nms_threshold', 0.45)),
        tuple(runtime_state.get('input_size', (640, 640))),
    )
    prep_meta = raw_outputs.get('prep_meta') or {}
    source_meta = raw_outputs.get('source_meta') or {}
    restored = restore_boxes(boxes, prep_meta)
    detections = []
    labels = runtime_state.get('labels') or raw_outputs.get('labels') or []
    for index in range(len(restored)):
        class_id = int(classes[index])
        label = labels[class_id] if 0 <= class_id < len(labels) else f'class-{class_id}'
        detections.append({
            'label': label,
            'score': float(scores[index]),
            'bbox': [float(v) for v in restored[index].tolist()],
            'class_id': class_id,
        })
    result = plugin_sdk.finalize_detections(
        detections=detections,
        request_payload=request_payload,
        package_context=package_context,
        runtime_state=runtime_state,
        source_meta=source_meta,
        plugin_meta={
            'model_path': raw_outputs.get('model_path'),
            'source_kind': source_meta.get('source_kind'),
            'resolved_source': source_meta.get('resolved_source'),
            'plan_ready_stream_count': raw_outputs.get('plan_ready_stream_count', 0),
            'active_stream_session_count': raw_outputs.get('active_stream_session_count', 0),
            'obj_threshold': float(raw_outputs.get('obj_threshold', 0.25)),
            'nms_threshold': float(raw_outputs.get('nms_threshold', 0.45)),
        },
        attributes={
            'source_kind': source_meta.get('source_kind'),
            'resolved_source': source_meta.get('resolved_source'),
        },
    )
    frame_payload = build_annotated_frame_payload(raw_outputs.get('image_bgr'), result.get('alerts') or result.get('detections') or [])
    if frame_payload:
        result['frame'] = frame_payload
    return result


def build_annotated_frame_payload(image_bgr, detections):
    if not isinstance(image_bgr, np.ndarray) or image_bgr.size == 0:
        return {}
    annotated = image_bgr.copy()
    line_width = max(2, int(round(max(annotated.shape[0], annotated.shape[1]) / 480.0)))
    font_scale = max(0.6, max(annotated.shape[0], annotated.shape[1]) / 1280.0)
    for detection in detections or []:
        bbox = detection.get('bbox') if isinstance(detection, dict) else None
        if not isinstance(bbox, (list, tuple)) or len(bbox) < 4:
            continue
        x1, y1, x2, y2 = [int(round(float(v))) for v in bbox[:4]]
        x1 = max(0, min(x1, annotated.shape[1] - 1))
        y1 = max(0, min(y1, annotated.shape[0] - 1))
        x2 = max(0, min(x2, annotated.shape[1] - 1))
        y2 = max(0, min(y2, annotated.shape[0] - 1))
        cv2.rectangle(annotated, (x1, y1), (x2, y2), (0, 0, 255), line_width)
        label = build_label_text(detection)
        if label:
            put_label(annotated, label, x1, y1, font_scale, line_width)
    ok, encoded = cv2.imencode('.jpg', annotated, [int(cv2.IMWRITE_JPEG_QUALITY), 90])
    if not ok:
        return {}
    return {
        'annotated_image_base64': base64.b64encode(encoded.tobytes()).decode('ascii'),
        'width': int(annotated.shape[1]),
        'height': int(annotated.shape[0]),
    }


def build_label_text(detection):
    if not isinstance(detection, dict):
        return ''
    # Use ASCII-safe class labels for OpenCV text rendering; Chinese aliases remain in payload data.
    label = str(detection.get('label') or detection.get('label_zh') or '').strip()
    score = detection.get('score')
    try:
        if score is not None:
            return f'{label} {float(score):.2f}'.strip()
    except (TypeError, ValueError):
        pass
    return label


def put_label(image, text, x, y, font_scale, line_width):
    if not text:
        return
    thickness = max(1, line_width)
    (text_width, text_height), baseline = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, font_scale, thickness)
    top = max(0, y - text_height - baseline - 8)
    right = min(image.shape[1] - 1, x + text_width + 10)
    bottom = min(image.shape[0] - 1, top + text_height + baseline + 8)
    cv2.rectangle(image, (x, top), (right, bottom), (0, 0, 255), -1)
    cv2.putText(image, text, (x + 5, bottom - baseline - 4), cv2.FONT_HERSHEY_SIMPLEX, font_scale, (255, 255, 255), thickness, cv2.LINE_AA)


def decode_outputs(outputs, obj_threshold, nms_threshold, input_size):
    tensors = [np.asarray(output) for output in (outputs or []) if output is not None]
    if not tensors:
        return empty_detection_result()
    if looks_like_rknn_branch_outputs(tensors):
        return decode_rknn_branch_outputs(tensors, obj_threshold, nms_threshold, input_size)
    return decode_flat_prediction_output(tensors[0], obj_threshold, nms_threshold)


def empty_detection_result():
    return (
        np.empty((0, 4), dtype=np.float32),
        np.empty((0,), dtype=np.int32),
        np.empty((0,), dtype=np.float32),
    )


def looks_like_rknn_branch_outputs(tensors):
    if len(tensors) < 6 or len(tensors) % 3 != 0:
        return False
    return all(np.asarray(tensor).ndim == 4 for tensor in tensors)


def decode_rknn_branch_outputs(tensors, obj_threshold, nms_threshold, input_size):
    boxes = []
    class_probs = []
    branch_count = len(tensors) // 3
    for branch_index in range(branch_count):
        box_tensor = np.asarray(tensors[branch_index * 3], dtype=np.float32)
        class_tensor = np.asarray(tensors[branch_index * 3 + 1], dtype=np.float32)
        boxes.append(flatten_branch_output(box_process(box_tensor, input_size)))
        class_probs.append(flatten_branch_output(class_tensor))
    flat_boxes = np.concatenate(boxes, axis=0) if boxes else np.empty((0, 4), dtype=np.float32)
    flat_class_probs = np.concatenate(class_probs, axis=0) if class_probs else np.empty((0, 0), dtype=np.float32)
    return filter_and_nms(flat_boxes, flat_class_probs, obj_threshold, nms_threshold)


def decode_flat_prediction_output(predictions, obj_threshold, nms_threshold):
    predictions = np.asarray(predictions, dtype=np.float32)
    predictions = np.squeeze(predictions)
    if predictions.ndim != 2:
        predictions = predictions.reshape(predictions.shape[0], -1)
    if predictions.shape[0] < predictions.shape[1]:
        predictions = predictions.T
    boxes_xywh = predictions[:, :4]
    class_scores = predictions[:, 4:]
    boxes_xyxy = xywh_to_xyxy(boxes_xywh)
    return filter_and_nms(boxes_xyxy, class_scores, obj_threshold, nms_threshold)


def flatten_branch_output(tensor):
    tensor = np.asarray(tensor, dtype=np.float32)
    if tensor.ndim != 4:
        raise ValueError(f'expected 4D tensor for branch output, got shape={tensor.shape}')
    return tensor.transpose(0, 2, 3, 1).reshape(-1, tensor.shape[1])


def box_process(position, input_size):
    position = np.asarray(position, dtype=np.float32)
    if position.ndim != 4:
        raise ValueError(f'expected 4D tensor for box branch, got shape={position.shape}')
    grid_h, grid_w = position.shape[2:4]
    grid_x, grid_y = np.meshgrid(np.arange(grid_w, dtype=np.float32), np.arange(grid_h, dtype=np.float32))
    grid = np.stack((grid_x, grid_y), axis=0).reshape(1, 2, grid_h, grid_w)
    stride = np.array(
        [float(input_size[0]) / float(grid_w), float(input_size[1]) / float(grid_h)],
        dtype=np.float32,
    ).reshape(1, 2, 1, 1)
    distance = dfl(position)
    top_left = (grid + 0.5 - distance[:, 0:2, :, :]) * stride
    bottom_right = (grid + 0.5 + distance[:, 2:4, :, :]) * stride
    return np.concatenate((top_left, bottom_right), axis=1)


def dfl(position):
    position = np.asarray(position, dtype=np.float32)
    if position.ndim != 4 or position.shape[1] % 4 != 0:
        raise ValueError(f'invalid DFL tensor shape: {position.shape}')
    n, c, h, w = position.shape
    bins = c // 4
    reshaped = position.reshape(n, 4, bins, h, w)
    reshaped = reshaped - np.max(reshaped, axis=2, keepdims=True)
    exp = np.exp(reshaped)
    prob = exp / np.maximum(np.sum(exp, axis=2, keepdims=True), 1e-9)
    weights = np.arange(bins, dtype=np.float32).reshape(1, 1, bins, 1, 1)
    return np.sum(prob * weights, axis=2)


def filter_and_nms(boxes_xyxy, class_scores, obj_threshold, nms_threshold):
    boxes_xyxy = np.asarray(boxes_xyxy, dtype=np.float32)
    class_scores = np.asarray(class_scores, dtype=np.float32)
    if boxes_xyxy.size == 0 or class_scores.size == 0:
        return empty_detection_result()
    class_ids = np.argmax(class_scores, axis=1)
    scores = class_scores[np.arange(class_scores.shape[0]), class_ids]
    keep = scores >= obj_threshold
    if not np.any(keep):
        return empty_detection_result()
    boxes_xyxy = boxes_xyxy[keep]
    class_ids = class_ids[keep].astype(np.int32)
    scores = scores[keep].astype(np.float32)
    kept_indices = nms(boxes_xyxy, scores, class_ids, nms_threshold)
    if kept_indices.size == 0:
        return empty_detection_result()
    return boxes_xyxy[kept_indices], class_ids[kept_indices], scores[kept_indices]


def xywh_to_xyxy(boxes_xywh):
    boxes_xywh = np.asarray(boxes_xywh, dtype=np.float32)
    boxes_xyxy = np.empty_like(boxes_xywh, dtype=np.float32)
    boxes_xyxy[:, 0] = boxes_xywh[:, 0] - boxes_xywh[:, 2] / 2.0
    boxes_xyxy[:, 1] = boxes_xywh[:, 1] - boxes_xywh[:, 3] / 2.0
    boxes_xyxy[:, 2] = boxes_xywh[:, 0] + boxes_xywh[:, 2] / 2.0
    boxes_xyxy[:, 3] = boxes_xywh[:, 1] + boxes_xywh[:, 3] / 2.0
    return boxes_xyxy


def restore_boxes(boxes_xyxy, prep_meta):
    boxes_xyxy = np.asarray(boxes_xyxy, dtype=np.float32)
    if boxes_xyxy.size == 0:
        return boxes_xyxy.reshape((-1, 4))
    scale = float(prep_meta.get('scale', 1.0) or 1.0)
    pad_left = float(prep_meta.get('pad_left', 0.0) or 0.0)
    pad_top = float(prep_meta.get('pad_top', 0.0) or 0.0)
    original_width = float(prep_meta.get('original_width', prep_meta.get('input_width', 1)) or 1)
    original_height = float(prep_meta.get('original_height', prep_meta.get('input_height', 1)) or 1)
    restored = boxes_xyxy.copy()
    restored[:, [0, 2]] = (restored[:, [0, 2]] - pad_left) / max(scale, 1e-6)
    restored[:, [1, 3]] = (restored[:, [1, 3]] - pad_top) / max(scale, 1e-6)
    restored[:, [0, 2]] = np.clip(restored[:, [0, 2]], 0.0, max(original_width - 1.0, 0.0))
    restored[:, [1, 3]] = np.clip(restored[:, [1, 3]], 0.0, max(original_height - 1.0, 0.0))
    return restored


def nms(boxes, scores, class_ids, threshold):
    if boxes.size == 0:
        return np.empty((0,), dtype=np.int32)
    order = scores.argsort()[::-1]
    keep = []
    while order.size > 0:
        index = order[0]
        keep.append(index)
        if order.size == 1:
            break
        remaining = order[1:]
        same_class = class_ids[remaining] == class_ids[index]
        overlaps = iou(boxes[index], boxes[remaining])
        suppress = same_class & (overlaps > threshold)
        order = remaining[~suppress]
    return np.asarray(keep, dtype=np.int32)


def iou(box, boxes):
    x1 = np.maximum(box[0], boxes[:, 0])
    y1 = np.maximum(box[1], boxes[:, 1])
    x2 = np.minimum(box[2], boxes[:, 2])
    y2 = np.minimum(box[3], boxes[:, 3])
    inter = np.maximum(0.0, x2 - x1) * np.maximum(0.0, y2 - y1)
    box_area = np.maximum(0.0, box[2] - box[0]) * np.maximum(0.0, box[3] - box[1])
    boxes_area = np.maximum(0.0, boxes[:, 2] - boxes[:, 0]) * np.maximum(0.0, boxes[:, 3] - boxes[:, 1])
    union = np.maximum(box_area + boxes_area - inter, 1e-6)
    return inter / union
