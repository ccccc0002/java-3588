from __future__ import annotations

import subprocess
import time
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse

import cv2
import numpy as np


@dataclass
class StreamSession:
    source: str
    capture: object
    created_at: float
    last_used_at: float
    loop_on_eof: bool


class StreamSessionManager:
    def __init__(self, now_fn=None, ttl_sec=30.0, max_sessions=4):
        self._now_fn = now_fn or time.monotonic
        self.ttl_sec = max(0.0, float(ttl_sec or 0.0))
        self.max_sessions = max(1, int(max_sessions or 1))
        self._sessions = {}

    def read_frame(self, source):
        self.close_expired()
        session = self._sessions.get(source)
        reused = session is not None
        if session is None:
            session = self._open_session(source)
            self._sessions[source] = session
        try:
            frame = self._read_frame(session)
        except Exception:
            self._close_session(source)
            session = self._open_session(source)
            self._sessions[source] = session
            reused = False
            frame = self._read_frame(session)
        session.last_used_at = self._now()
        self._enforce_capacity()
        return frame, reused

    def close_expired(self):
        if self.ttl_sec <= 0:
            return
        now = self._now()
        expired_sources = [
            source
            for source, session in self._sessions.items()
            if now - session.last_used_at > self.ttl_sec
        ]
        for source in expired_sources:
            self._close_session(source)

    def close(self):
        for source in list(self._sessions.keys()):
            self._close_session(source)

    def session_count(self):
        return len(self._sessions)

    def _enforce_capacity(self):
        if len(self._sessions) <= self.max_sessions:
            return
        overflow = len(self._sessions) - self.max_sessions
        ranked = sorted(self._sessions.items(), key=lambda item: item[1].last_used_at)
        for source, _session in ranked[:overflow]:
            self._close_session(source)

    def _open_session(self, source):
        capture = cv2.VideoCapture(source)
        if not capture.isOpened():
            capture.release()
            raise RuntimeError(f'failed to open stream source: {source}')
        now = self._now()
        return StreamSession(
            source=source,
            capture=capture,
            created_at=now,
            last_used_at=now,
            loop_on_eof=is_video_path_like(source),
        )

    def _read_frame(self, session):
        ok, frame = session.capture.read()
        if ok and frame is not None:
            return frame
        if session.loop_on_eof and session.capture.set(cv2.CAP_PROP_POS_FRAMES, 0):
            ok, frame = session.capture.read()
            if ok and frame is not None:
                return frame
        raise RuntimeError(f'failed to read frame from stream source: {session.source}')

    def _close_session(self, source):
        session = self._sessions.pop(source, None)
        if session is not None:
            session.capture.release()

    def _now(self):
        return float(self._now_fn())


def create_stream_manager(package_context):
    ttl_sec = to_float(package_context.config.get('stream_session_ttl_sec'), 30.0)
    max_sessions = to_int(package_context.config.get('stream_max_sessions'), 4)
    if ttl_sec <= 0 or max_sessions <= 0:
        return None
    return StreamSessionManager(ttl_sec=ttl_sec, max_sessions=max_sessions)


def load_frame_bgr(request_payload, package_context, runtime_state=None):
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
        if is_video_file_path(resolved):
            image, backend = read_stream_frame(
                str(resolved),
                str(package_context.config.get('stream_decode_backend', 'auto')),
                stream_manager=get_stream_manager(runtime_state),
            )
            return image, {
                'source_kind': 'video_file',
                'resolved_source': str(resolved),
                'decoder_backend': backend,
            }
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
        image, backend = read_stream_frame(
            source,
            str(package_context.config.get('stream_decode_backend', 'auto')),
            stream_manager=get_stream_manager(runtime_state),
        )
        return image, {
            'source_kind': 'stream',
            'resolved_source': source,
            'decoder_backend': backend,
        }
    resolved = resolve_source_path(package_context, source)
    if is_video_file_path(resolved):
        image, backend = read_stream_frame(
            str(resolved),
            str(package_context.config.get('stream_decode_backend', 'auto')),
            stream_manager=get_stream_manager(runtime_state),
        )
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


def get_stream_manager(runtime_state):
    if isinstance(runtime_state, dict):
        return runtime_state.get('stream_manager')
    return None


def read_image_url(source):
    with urllib.request.urlopen(source, timeout=10) as response:
        payload = response.read()
    array = np.frombuffer(payload, dtype=np.uint8)
    image = cv2.imdecode(array, cv2.IMREAD_COLOR)
    if image is None:
        raise RuntimeError(f'failed to decode image url: {source}')
    return image


def read_stream_frame(source, preferred_backend='auto', stream_manager=None):
    backend = str(preferred_backend or 'auto').strip().lower()
    if backend == 'opencv':
        return read_stream_frame_opencv(source, stream_manager=stream_manager), 'opencv'
    if backend == 'ffmpeg':
        return read_stream_frame_ffmpeg(source), 'ffmpeg'

    last_error = None
    for name, fn in (
        ('opencv', lambda current_source: read_stream_frame_opencv(current_source, stream_manager=stream_manager)),
        ('ffmpeg', read_stream_frame_ffmpeg),
    ):
        try:
            return fn(source), name
        except Exception as exc:
            last_error = exc
    raise RuntimeError(f'failed to decode stream source: {source}; last_error={last_error}')


def read_stream_frame_opencv(source, stream_manager=None):
    if stream_manager is not None:
        frame, _reused = stream_manager.read_frame(source)
        return frame
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


def is_video_path_like(source):
    text = str(source or '').strip()
    if not text:
        return False
    if is_stream_source(text):
        return False
    if text.startswith('file://'):
        text = text[7:]
    return is_video_file_path(text)


def first_non_blank(*values):
    for value in values:
        text = str(value or '').strip()
        if text:
            return text
    return ''


def to_float(value, default):
    try:
        return float(value)
    except (TypeError, ValueError):
        return float(default)


def to_int(value, default):
    try:
        return int(value)
    except (TypeError, ValueError):
        return int(default)
