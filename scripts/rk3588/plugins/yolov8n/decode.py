from __future__ import annotations

import subprocess
import sys
import time
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse

import cv2
import numpy as np

PLUGIN_DIR = Path(__file__).resolve().parent
RK3588_SCRIPT_DIR = PLUGIN_DIR.parent.parent
if str(RK3588_SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(RK3588_SCRIPT_DIR))

from hw_support import cached_runtime_capabilities, ensure_ffmpeg_mpp_rga_decode_support, resolve_ffmpeg_binary


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
    decode_options = resolve_decode_options(request_payload, package_context)
    direct_path = first_non_blank(frame.get('image_path'), frame.get('path'), frame.get('local_path'))
    if direct_path:
        resolved = resolve_source_path(package_context, direct_path)
        return read_image_bgr(resolved), build_decode_meta('image_path', str(resolved), 'image', decode_options)

    ffmpeg_bin = decode_options['ffmpeg_bin']
    source = str(frame.get('source', '')).strip()
    if source == 'test://frame':
        fallback = resolve_optional_path(package_context, package_context.config.get('default_test_image_path'))
        if fallback is None or not fallback.exists():
            raise RuntimeError('default_test_image_path is not configured or missing')
        return read_image_bgr(fallback), build_decode_meta('default_test_image', str(fallback.resolve()), 'image', decode_options)
    if not source:
        raise RuntimeError('frame.source is required')
    if source.startswith('file://'):
        resolved = Path(source[7:]).expanduser().resolve()
        if is_video_file_path(resolved):
            image, backend = read_stream_frame(
                str(resolved),
                decode_options['backend'],
                stream_manager=get_stream_manager(runtime_state),
                codec_hint=decode_options['codec'],
                ffmpeg_bin=ffmpeg_bin,
                retry_count=decode_options['retry_count'],
                retry_backoff_ms=decode_options['retry_backoff_ms'],
                timeout_sec=decode_options['timeout_sec'],
            )
            return image, build_decode_meta('video_file', str(resolved), backend, decode_options)
        return read_image_bgr(resolved), build_decode_meta('file_url', str(resolved), 'image', decode_options)
    if source.startswith('http://') or source.startswith('https://'):
        return read_image_url(source), build_decode_meta('http_url', source, 'http', decode_options)
    if is_stream_source(source):
        image, backend = read_stream_frame(
            source,
            decode_options['backend'],
            stream_manager=get_stream_manager(runtime_state),
            codec_hint=decode_options['codec'],
            ffmpeg_bin=ffmpeg_bin,
            retry_count=decode_options['retry_count'],
            retry_backoff_ms=decode_options['retry_backoff_ms'],
            timeout_sec=decode_options['timeout_sec'],
        )
        return image, build_decode_meta('stream', source, backend, decode_options)
    resolved = resolve_source_path(package_context, source)
    if is_video_file_path(resolved):
        image, backend = read_stream_frame(
            str(resolved),
            decode_options['backend'],
            stream_manager=get_stream_manager(runtime_state),
            codec_hint=decode_options['codec'],
            ffmpeg_bin=ffmpeg_bin,
            retry_count=decode_options['retry_count'],
            retry_backoff_ms=decode_options['retry_backoff_ms'],
            timeout_sec=decode_options['timeout_sec'],
        )
        return image, build_decode_meta('video_file', str(resolved), backend, decode_options)
    return read_image_bgr(resolved), build_decode_meta('filesystem', str(resolved), 'image', decode_options)


def build_decode_meta(source_kind, resolved_source, backend, decode_options):
    return {
        'source_kind': source_kind,
        'resolved_source': resolved_source,
        'decoder_backend': backend,
        'decoder_backend_requested': decode_options['backend'],
        'decoder_hwaccel': decode_options['hwaccel'],
        'decoder_codec': decode_options['codec'],
        'decoder_ffmpeg_bin': decode_options['ffmpeg_bin'],
        'decoder_retry_count': decode_options['retry_count'],
        'decoder_retry_backoff_ms': decode_options['retry_backoff_ms'],
        'decoder_timeout_sec': decode_options['timeout_sec'],
    }


def resolve_decode_options(request_payload, package_context):
    config = package_context.config if hasattr(package_context, 'config') and isinstance(package_context.config, dict) else {}
    payload = request_payload if isinstance(request_payload, dict) else {}
    decode_hints = payload.get('decode_hints') if isinstance(payload.get('decode_hints'), dict) else {}
    decode_block = payload.get('decode') if isinstance(payload.get('decode'), dict) else {}
    frame = payload.get('frame') if isinstance(payload.get('frame'), dict) else {}
    source = str(frame.get('source', '')).strip()
    backend = first_non_blank(
        decode_hints.get('backend'),
        decode_block.get('backend'),
        config.get('stream_decode_backend'),
        'auto',
    ).lower()
    if backend in {'rk3588', 'rk3588_mpp', 'rkmpp', 'mpp'}:
        backend = 'mpp'
    hwaccel = first_non_blank(
        decode_hints.get('hwaccel'),
        decode_block.get('hwaccel'),
        config.get('stream_decode_hwaccel'),
        'rga' if backend == 'mpp' else '',
    ).lower()
    codec = normalize_codec_hint(
        first_non_blank(
            decode_hints.get('codec'),
            decode_block.get('codec'),
            config.get('stream_decode_codec'),
            default_codec_hint_for_source(source),
            'auto',
        )
    )
    ffmpeg_bin = resolve_decode_ffmpeg_bin(
        package_context,
        first_non_blank(
            decode_hints.get('ffmpeg_bin'),
            decode_block.get('ffmpeg_bin'),
            config.get('stream_decode_ffmpeg_bin'),
            config.get('ffmpeg_bin'),
            'ffmpeg',
        ),
    )
    retry_count = resolve_decode_retry_count(
        first_non_blank(
            decode_hints.get('retry_count'),
            decode_block.get('retry_count'),
            config.get('stream_decode_retry_count'),
            '1',
        )
    )
    retry_backoff_ms = resolve_decode_retry_backoff_ms(
        first_non_blank(
            decode_hints.get('retry_backoff_ms'),
            decode_block.get('retry_backoff_ms'),
            config.get('stream_decode_retry_backoff_ms'),
            '200',
        )
    )
    timeout_sec = resolve_decode_timeout_sec(
        first_non_blank(
            decode_hints.get('timeout_sec'),
            decode_block.get('timeout_sec'),
            config.get('stream_decode_timeout_sec'),
            '20',
        )
    )
    return {
        'backend': backend,
        'hwaccel': hwaccel,
        'codec': codec,
        'ffmpeg_bin': ffmpeg_bin,
        'retry_count': retry_count,
        'retry_backoff_ms': retry_backoff_ms,
        'timeout_sec': timeout_sec,
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


def resolve_decode_ffmpeg_bin(package_context, value='ffmpeg'):
    text = str(value or 'ffmpeg').strip() or 'ffmpeg'
    if is_path_like(text):
        resolved = resolve_optional_path(package_context, text)
        if resolved is not None:
            return str(resolved)
    return resolve_ffmpeg_binary(text)


def ensure_mpp_decode_support(ffmpeg_bin='ffmpeg'):
    return ensure_ffmpeg_mpp_rga_decode_support(ffmpeg_bin=ffmpeg_bin)


def can_use_mpp_decode(ffmpeg_bin='ffmpeg'):
    return cached_runtime_capabilities(ffmpeg_bin=ffmpeg_bin).has_ffmpeg_mpp_rga_decode


def read_stream_frame(
    source,
    preferred_backend='auto',
    stream_manager=None,
    codec_hint='auto',
    ffmpeg_bin='ffmpeg',
    retry_count=0,
    retry_backoff_ms=0,
    timeout_sec=20.0,
):
    backend = str(preferred_backend or 'auto').strip().lower()
    ffmpeg_bin = resolve_ffmpeg_binary(ffmpeg_bin)
    codec_hint = normalize_codec_hint(codec_hint)
    if backend == 'opencv':
        return read_stream_frame_opencv(source, stream_manager=stream_manager), 'opencv'
    if backend == 'mpp':
        ensure_mpp_decode_support(ffmpeg_bin=ffmpeg_bin)
        return read_stream_frame_mpp(
            source,
            codec_hint=codec_hint,
            ffmpeg_bin=ffmpeg_bin,
            retry_count=retry_count,
            retry_backoff_ms=retry_backoff_ms,
            timeout_sec=timeout_sec,
        ), 'mpp'
    if backend == 'ffmpeg':
        return read_stream_frame_ffmpeg(source, ffmpeg_bin=ffmpeg_bin, timeout_sec=timeout_sec), 'ffmpeg'

    last_error = None
    for name, fn in (
        (
            'mpp',
            lambda current_source: read_stream_frame_mpp(
                current_source,
                codec_hint=codec_hint,
                ffmpeg_bin=ffmpeg_bin,
                retry_count=retry_count,
                retry_backoff_ms=retry_backoff_ms,
                timeout_sec=timeout_sec,
            ),
        ),
        ('opencv', lambda current_source: read_stream_frame_opencv(current_source, stream_manager=stream_manager)),
        ('ffmpeg', lambda current_source: read_stream_frame_ffmpeg(current_source, ffmpeg_bin=ffmpeg_bin, timeout_sec=timeout_sec)),
    ):
        if name == 'mpp' and not can_use_mpp_decode(ffmpeg_bin=ffmpeg_bin):
            last_error = RuntimeError('MPP+RGA decode backend is unavailable')
            continue
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


def build_mpp_ffmpeg_command(source, codec_hint='auto', ffmpeg_bin='ffmpeg'):
    decoder = resolve_mpp_decoder(codec_hint, source)
    command = [
        ffmpeg_bin,
        '-loglevel', 'error',
    ]
    if str(source).startswith('rtsp://'):
        command.extend(['-rtsp_transport', 'tcp'])
    if decoder:
        command.extend(['-c:v', decoder])
    command.extend([
        '-hwaccel', 'rkmpp',
        '-hwaccel_output_format', 'drm_prime',
        '-afbc', 'rga',
        '-i', source,
        '-vf', 'scale_rkrga=w=iw:h=ih:format=nv12,hwdownload,format=nv12',
        '-frames:v', '1',
        '-f', 'image2pipe',
        '-vcodec', 'mjpeg',
        '-',
    ])
    return command

def read_stream_frame_mpp(source, codec_hint='auto', ffmpeg_bin='ffmpeg', retry_count=0, retry_backoff_ms=0, timeout_sec=20.0):
    command = build_mpp_ffmpeg_command(source, codec_hint=codec_hint, ffmpeg_bin=ffmpeg_bin)
    retries = max(0, int(retry_count or 0))
    retry_delay_sec = max(0.0, float(retry_backoff_ms or 0.0) / 1000.0)
    timeout_value = resolve_decode_timeout_sec(timeout_sec)
    last_error = None

    for attempt in range(retries + 1):
        completed = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout_value,
            check=False,
        )
        stderr = completed.stderr.decode('utf-8', errors='replace').strip()

        if completed.returncode == 0 and completed.stdout:
            payload = np.frombuffer(completed.stdout, dtype=np.uint8)
            image = cv2.imdecode(payload, cv2.IMREAD_COLOR)
            if image is not None:
                return image
            last_error = RuntimeError(f'mpp ffmpeg returned undecodable frame for stream source: {source}')
            retryable = should_retry_mpp_failure(source, completed.returncode, stderr, undecodable_frame=True)
        else:
            last_error = RuntimeError(f'mpp ffmpeg failed for stream source: {source}; code={completed.returncode}; stderr={stderr}')
            retryable = should_retry_mpp_failure(source, completed.returncode, stderr, undecodable_frame=False)

        if not retryable or attempt >= retries:
            raise last_error
        if retry_delay_sec > 0:
            time.sleep(retry_delay_sec)

    raise RuntimeError(f'mpp ffmpeg failed for stream source: {source}')


def read_stream_frame_ffmpeg(source, ffmpeg_bin='ffmpeg', timeout_sec=20.0):
    command = [ffmpeg_bin, '-loglevel', 'error']
    if str(source).startswith('rtsp://'):
        command.extend(['-rtsp_transport', 'tcp'])
    command.extend(['-i', source, '-frames:v', '1', '-f', 'image2pipe', '-vcodec', 'mjpeg', '-'])
    completed = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=resolve_decode_timeout_sec(timeout_sec),
        check=False,
    )
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


def is_path_like(value):
    text = str(value or '').strip()
    if not text:
        return False
    return text.startswith(('.', '~')) or '/' in text or '\\' in text


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


def resolve_decode_retry_count(value, default=1):
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        parsed = int(default)
    return max(0, min(parsed, 10))


def resolve_decode_retry_backoff_ms(value, default=200):
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        parsed = int(default)
    return max(0, min(parsed, 5000))


def resolve_decode_timeout_sec(value, default=20.0):
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        parsed = float(default)
    return max(2.0, min(parsed, 120.0))

def normalize_codec_hint(value):
    text = str(value or '').strip().lower()
    if text in {'h265', 'hevc', '265'}:
        return 'h265'
    if text in {'h264', 'avc', '264'}:
        return 'h264'
    return 'auto'


def infer_stream_codec(source):
    text = str(source or '').strip().lower()
    if 'h265' in text or 'hevc' in text:
        return 'h265'
    if 'h264' in text or 'avc' in text:
        return 'h264'
    return 'auto'


def default_codec_hint_for_source(source):
    if is_stream_source(source):
        return 'auto'
    return infer_stream_codec(source)


def should_retry_mpp_failure(source, returncode, stderr, undecodable_frame=False):
    if not str(source or '').startswith('rtsp://'):
        return False
    if undecodable_frame:
        return True
    text = str(stderr or '').lower()
    retry_tokens = (
        'server returned 5xx',
        'setup failed: 500',
        'temporarily unavailable',
        'connection refused',
        'connection reset',
        'connection timed out',
        'timed out',
        'resource temporarily unavailable',
        'i/o error',
    )
    if any(token in text for token in retry_tokens):
        return True
    return int(returncode) in {8, 110, 111, 255}


def resolve_mpp_decoder(codec_hint, source=''):
    codec = normalize_codec_hint(codec_hint)
    if codec == 'auto':
        codec = normalize_codec_hint(default_codec_hint_for_source(source))
    if codec == 'h265':
        return 'hevc_rkmpp'
    if codec == 'h264':
        return 'h264_rkmpp'
    return ''
