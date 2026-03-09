from __future__ import annotations

import subprocess
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

import cv2
import numpy as np


def load_frame_bgr(request_payload, package_context):
    frame = request_payload.get('frame') if isinstance(request_payload.get('frame'), dict) else {}
    direct_path = first_non_blank(frame.get('image_path'), frame.get('path'), frame.get('local_path'))
    if direct_path:
        resolved = resolve_source_path(package_context, direct_path)
        return read_image_bgr(resolved), {
            'source_kind': 'image_path',
            'resolved_source': str(resolved),
            'decoder_backend': 'image',
        }

    source = str(frame.get('source', '')).strip()
    if source == 'test://frame':
        fallback = resolve_optional_path(package_context, package_context.config.get('default_test_image_path'))
        if fallback is None or not fallback.exists():
            raise RuntimeError('default_test_image_path is not configured or missing')
        return read_image_bgr(fallback), {
            'source_kind': 'default_test_image',
            'resolved_source': str(fallback.resolve()),
            'decoder_backend': 'image',
        }
    if not source:
        raise RuntimeError('frame.source is required')
    if source.startswith('file://'):
        resolved = Path(source[7:]).expanduser().resolve()
        return read_image_bgr(resolved), {
            'source_kind': 'file_url',
            'resolved_source': str(resolved),
            'decoder_backend': 'image',
        }
    if source.startswith('http://') or source.startswith('https://'):
        return read_image_url(source), {
            'source_kind': 'http_url',
            'resolved_source': source,
            'decoder_backend': 'http',
        }
    if is_stream_source(source):
        image, backend = read_stream_frame(source, str(package_context.config.get('stream_decode_backend', 'auto')))
        return image, {
            'source_kind': 'stream',
            'resolved_source': source,
            'decoder_backend': backend,
        }
    resolved = resolve_source_path(package_context, source)
    if is_video_file_path(resolved):
        image, backend = read_stream_frame(str(resolved), str(package_context.config.get('stream_decode_backend', 'auto')))
        return image, {
            'source_kind': 'video_file',
            'resolved_source': str(resolved),
            'decoder_backend': backend,
        }
    return read_image_bgr(resolved), {
        'source_kind': 'filesystem',
        'resolved_source': str(resolved),
        'decoder_backend': 'image',
    }


def read_image_url(source):
    with urllib.request.urlopen(source, timeout=10) as response:
        payload = response.read()
    array = np.frombuffer(payload, dtype=np.uint8)
    image = cv2.imdecode(array, cv2.IMREAD_COLOR)
    if image is None:
        raise RuntimeError(f'failed to decode image url: {source}')
    return image


def read_stream_frame(source, preferred_backend='auto'):
    backend = str(preferred_backend or 'auto').strip().lower()
    if backend == 'opencv':
        return read_stream_frame_opencv(source), 'opencv'
    if backend == 'ffmpeg':
        return read_stream_frame_ffmpeg(source), 'ffmpeg'

    last_error = None
    for name, fn in (('opencv', read_stream_frame_opencv), ('ffmpeg', read_stream_frame_ffmpeg)):
        try:
            return fn(source), name
        except Exception as exc:
            last_error = exc
    raise RuntimeError(f'failed to decode stream source: {source}; last_error={last_error}')


def read_stream_frame_opencv(source):
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


def read_stream_frame_ffmpeg(source):
    command = ['ffmpeg', '-loglevel', 'error']
    if str(source).startswith('rtsp://'):
        command.extend(['-rtsp_transport', 'tcp'])
    command.extend(['-i', source, '-frames:v', '1', '-f', 'image2pipe', '-vcodec', 'mjpeg', '-'])
    completed = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20, check=False)
    if completed.returncode != 0 or not completed.stdout:
        stderr = completed.stderr.decode('utf-8', errors='replace').strip()
        raise RuntimeError(f'ffmpeg failed for stream source: {source}; code={completed.returncode}; stderr={stderr}')
    payload = np.frombuffer(completed.stdout, dtype=np.uint8)
    image = cv2.imdecode(payload, cv2.IMREAD_COLOR)
    if image is None:
        raise RuntimeError(f'ffmpeg returned undecodable frame for stream source: {source}')
    return image


def read_image_bgr(path):
    resolved = Path(path).resolve()
    payload = np.fromfile(str(resolved), dtype=np.uint8)
    image = cv2.imdecode(payload, cv2.IMREAD_COLOR)
    if image is None:
        raise RuntimeError(f'failed to read image file: {path}')
    return image


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


def is_video_file_path(path):
    suffix = Path(path).suffix.lower()
    return suffix in {'.mp4', '.mov', '.mkv', '.avi', '.flv', '.ts', '.m4v'}


def first_non_blank(*values):
    for value in values:
        text = str(value or '').strip()
        if text:
            return text
    return ''
