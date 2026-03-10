from __future__ import annotations

import argparse
import json
import signal
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from hw_support import ensure_ffmpeg_mpp_rga_transcode_support
from rk3588_runtime_bridge import RuntimeApiClient, RuntimeBridgeConfig, TokenProvider


@dataclass(frozen=True)
class MediaTarget:
    camera_id: int
    camera_name: str
    source_url: str
    push_url: str
    play_url: str = ''
    video_port: int = 0
    ffmpeg_bin: str = 'ffmpeg'
    rtsp_transport: str = 'tcp'
    decode_backend: str = 'mpp'
    decode_hwaccel: str = 'rga'
    input_codec: str = 'auto'
    osd_enabled: bool = False
    osd_text: str = ''
    video_codec: str = 'h264_rkmpp'


@dataclass
class ManagedSession:
    target: MediaTarget
    process: Any
    command: List[str]
    started_at: float


class MediaWorker:
    def __init__(self, popen_factory=None, now_fn=None, capability_validator=None):
        self._popen_factory = popen_factory or subprocess.Popen
        self._now_fn = now_fn or time.monotonic
        self._capability_validator = capability_validator or validate_target_capability
        self._sessions: Dict[int, ManagedSession] = {}

    def sync(self, runtime_snapshot: Dict[str, Any]) -> Dict[str, Any]:
        desired = {target.camera_id: target for target in build_targets(runtime_snapshot)}
        stopped_camera_ids = self._reap_exited_sessions()
        started_camera_ids = []

        for camera_id in list(self._sessions.keys()):
            session = self._sessions[camera_id]
            target = desired.get(camera_id)
            if target is None or session.command != build_ffmpeg_command(target):
                self._stop_session(camera_id)
                stopped_camera_ids.append(camera_id)

        for camera_id, target in desired.items():
            session = self._sessions.get(camera_id)
            if session is not None and session.process.poll() is None:
                continue
            self._start_session(target)
            started_camera_ids.append(camera_id)

        status = self.status()
        return {
            'desired_count': len(desired),
            'running_count': status['running_count'],
            'started_camera_ids': sorted(started_camera_ids),
            'stopped_camera_ids': sorted(set(stopped_camera_ids)),
            'sessions': status['sessions'],
        }

    def status(self) -> Dict[str, Any]:
        sessions = []
        running_count = 0
        for camera_id, session in sorted(self._sessions.items()):
            is_running = session.process.poll() is None
            if is_running:
                running_count += 1
            sessions.append({
                'camera_id': camera_id,
                'camera_name': session.target.camera_name,
                'source_url': session.target.source_url,
                'push_url': session.target.push_url,
                'play_url': session.target.play_url,
                'pid': getattr(session.process, 'pid', 0),
                'running': is_running,
                'input_codec': session.target.input_codec,
                'decode_backend': session.target.decode_backend,
            })
        return {
            'running_count': running_count,
            'sessions': sessions,
        }

    def _reap_exited_sessions(self) -> List[int]:
        exited_camera_ids = []
        for camera_id in list(self._sessions.keys()):
            session = self._sessions.get(camera_id)
            if session is None or session.process.poll() is None:
                continue
            self._sessions.pop(camera_id, None)
            exited_camera_ids.append(camera_id)
        return sorted(exited_camera_ids)

    def close(self) -> None:
        for camera_id in list(self._sessions.keys()):
            self._stop_session(camera_id)

    def _start_session(self, target: MediaTarget) -> None:
        self._capability_validator(target)
        command = build_ffmpeg_command(target)
        process = self._popen_factory(
            command,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            stdin=subprocess.DEVNULL,
            start_new_session=True,
        )
        self._sessions[target.camera_id] = ManagedSession(
            target=target,
            process=process,
            command=command,
            started_at=float(self._now_fn()),
        )

    def _stop_session(self, camera_id: int) -> None:
        session = self._sessions.pop(camera_id, None)
        if session is None:
            return
        process = session.process
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=3)
            except Exception:
                process.kill()


def validate_target_capability(target: MediaTarget) -> None:
    if normalize_backend(target.decode_backend) != 'mpp':
        return
    ensure_ffmpeg_mpp_rga_transcode_support(ffmpeg_bin=target.ffmpeg_bin)


def build_targets(runtime_snapshot: Dict[str, Any]) -> List[MediaTarget]:
    media = runtime_snapshot.get('media') if isinstance(runtime_snapshot.get('media'), dict) else {}
    streams = runtime_snapshot.get('streams') if isinstance(runtime_snapshot.get('streams'), list) else []
    targets: List[MediaTarget] = []
    for item in streams:
        if not isinstance(item, dict):
            continue
        if not to_bool(item.get('enabled'), True):
            continue
        source_url = str(item.get('rtsp_url', '')).strip()
        push_url = str(item.get('push_url', '')).strip()
        if not source_url or not push_url:
            continue
        camera_id = to_int(item.get('camera_id'), 0)
        if camera_id <= 0:
            continue
        camera_name = str(item.get('camera_name', '')).strip()
        osd_text = str(item.get('osd_text', '')).strip() or camera_name or str(camera_id)
        input_codec = normalize_codec(first_non_blank(item.get('codec'), media.get('input_codec'), infer_input_codec(source_url), 'auto'))
        targets.append(MediaTarget(
            camera_id=camera_id,
            camera_name=camera_name,
            source_url=source_url,
            push_url=push_url,
            play_url=str(item.get('play_url', '')).strip(),
            video_port=to_int(item.get('video_port'), 0),
            ffmpeg_bin=str(media.get('ffmpeg_bin', 'ffmpeg') or 'ffmpeg').strip() or 'ffmpeg',
            rtsp_transport=str(media.get('rtsp_transport', 'tcp') or 'tcp').strip() or 'tcp',
            decode_backend=str(media.get('decode_backend', 'mpp') or 'mpp').strip().lower() or 'mpp',
            decode_hwaccel=str(media.get('decode_hwaccel', 'rga') or 'rga').strip().lower() or 'rga',
            input_codec=input_codec,
            osd_enabled=to_bool(media.get('osd_enabled'), False),
            osd_text=osd_text,
            video_codec=normalize_encoder(str(media.get('video_codec', 'h264_rkmpp') or 'h264_rkmpp').strip() or 'h264_rkmpp'),
        ))
    return targets


def build_ffmpeg_command(target: MediaTarget) -> List[str]:
    if normalize_backend(target.decode_backend) == 'mpp':
        return build_mpp_rga_command(target)
    return build_legacy_ffmpeg_command(target)


def build_mpp_rga_command(target: MediaTarget) -> List[str]:
    decoder = resolve_decoder(target.input_codec)
    encoder = resolve_encoder(target.video_codec, force_reencode=True)
    command = [
        target.ffmpeg_bin,
        '-nostdin',
        '-hide_banner',
        '-loglevel', 'error',
    ]
    if str(target.source_url).startswith('rtsp://'):
        command.extend(['-rtsp_transport', target.rtsp_transport])
    command.extend([
        '-hwaccel', 'rkmpp',
        '-hwaccel_output_format', 'drm_prime',
        '-afbc', 'rga',
        '-c:v', decoder,
        '-i', target.source_url,
        '-map', '0:v:0',
        '-vf', build_filter_chain(target),
        '-c:v', encoder,
    ])
    if encoder == 'h264_rkmpp':
        command.extend(['-g', '50', '-bf', '0'])
    command.extend(['-an', '-f', 'flv', target.push_url])
    return command


def build_legacy_ffmpeg_command(target: MediaTarget) -> List[str]:
    command = [
        target.ffmpeg_bin,
        '-nostdin',
        '-hide_banner',
        '-loglevel', 'error',
    ]
    if str(target.source_url).startswith('rtsp://'):
        command.extend(['-rtsp_transport', target.rtsp_transport])
    command.extend(['-i', target.source_url, '-map', '0:v:0'])
    if target.osd_enabled:
        command.extend(['-vf', build_drawtext_filter(target.osd_text), '-c:v', 'libx264', '-preset', 'veryfast', '-tune', 'zerolatency'])
    else:
        command.extend(['-c:v', target.video_codec if target.video_codec != 'copy' else 'copy'])
    command.extend(['-an', '-f', 'flv', target.push_url])
    return command


def build_filter_chain(target: MediaTarget) -> str:
    filters: List[str] = []
    if target.osd_enabled:
        filters.extend([
            'hwdownload',
            'format=nv12',
            build_drawtext_filter(target.osd_text),
            'hwupload',
        ])
    filters.append('scale_rkrga=w=iw:h=ih:format=nv12')
    return ','.join(filters)


def build_drawtext_filter(text: str) -> str:
    escaped = text.replace('\\', '\\\\').replace(':', '\\:').replace("'", "\\'")
    return f"drawtext=text='{escaped}':x=16:y=16:fontsize=18:fontcolor=white:box=1:boxcolor=black@0.45"


def normalize_backend(value: str) -> str:
    text = str(value or '').strip().lower()
    if text in ('mpp', 'rk3588', 'rk3588_mpp', 'rkmpp'):
        return 'mpp'
    if text in ('ffmpeg', 'opencv'):
        return text
    return 'mpp'


def normalize_codec(value: str) -> str:
    text = str(value or '').strip().lower()
    if text in ('hevc', 'h265', '265'):
        return 'h265'
    if text in ('h264', '264', 'avc'):
        return 'h264'
    return 'auto'


def infer_input_codec(source_url: str) -> str:
    text = str(source_url or '').strip().lower()
    if 'h265' in text or 'hevc' in text:
        return 'h265'
    if 'h264' in text or 'avc' in text:
        return 'h264'
    return 'auto'


def resolve_decoder(input_codec: str) -> str:
    codec = normalize_codec(input_codec)
    if codec == 'h265':
        return 'hevc_rkmpp'
    return 'h264_rkmpp'


def normalize_encoder(value: str) -> str:
    text = str(value or '').strip().lower()
    if text in ('', 'copy', 'h264', 'h264_rkmpp'):
        return 'h264_rkmpp'
    if text in ('libx264', 'x264'):
        return 'libx264'
    if text in ('h265_rkmpp', 'hevc_rkmpp', 'h265', 'hevc'):
        return 'hevc_rkmpp'
    return text


def resolve_encoder(value: str, force_reencode: bool = False) -> str:
    encoder = normalize_encoder(value)
    if force_reencode and encoder == 'copy':
        return 'h264_rkmpp'
    return encoder


def first_non_blank(*values: Any) -> str:
    for value in values:
        text = str(value or '').strip()
        if text:
            return text
    return ''


def to_bool(value: Any, default: bool) -> bool:
    if isinstance(value, bool):
        return value
    if value is None:
        return default
    text = str(value).strip().lower()
    if text in ('1', 'true', 'yes', 'on', 'y'):
        return True
    if text in ('0', 'false', 'no', 'off', 'n'):
        return False
    return default


def to_int(value: Any, default: int) -> int:
    try:
        return int(value)
    except Exception:
        return default


class RuntimeSnapshotFetcher:
    def __init__(self, runtime_url: str, bootstrap_token: str = '', runtime_token: str = '', timeout_sec: float = 5.0):
        config = RuntimeBridgeConfig(
            runtime_url=runtime_url,
            runtime_token=runtime_token,
            runtime_bootstrap_token=bootstrap_token,
            timeout_sec=timeout_sec,
        )
        self.client = RuntimeApiClient(config)
        if not runtime_token:
            token_provider = TokenProvider(self.client)
            self.client.set_token_provider(token_provider)

    def fetch(self) -> Dict[str, Any]:
        return self.client.get_runtime_snapshot()


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Run RK3588 media worker against runtime snapshot stream targets')
    parser.add_argument('--runtime-url', default='http://127.0.0.1:18081')
    parser.add_argument('--bootstrap-token', default='')
    parser.add_argument('--runtime-token', default='')
    parser.add_argument('--timeout-sec', type=float, default=5.0)
    parser.add_argument('--interval-sec', type=float, default=2.0)
    parser.add_argument('--once', action='store_true')
    return parser.parse_args(argv)


def run_worker_loop(fetcher, worker, interval_sec: float, once: bool = False, sleep_fn=None, emit_fn=None, stop_flag=None, iteration_limit: Optional[int] = None) -> Dict[str, Any]:
    sleep = sleep_fn or time.sleep
    emit = emit_fn or (lambda payload: print(json.dumps(payload, ensure_ascii=True)))
    stop = stop_flag or {'value': False}
    error_count = 0
    iterations = 0
    try:
        while not stop['value']:
            try:
                snapshot = fetcher.fetch()
                result = worker.sync(snapshot)
                emit({'status': 'running', 'sync': result})
                if once:
                    break
            except Exception as exc:
                error_count += 1
                emit({'status': 'error', 'message': str(exc), 'error_count': error_count})
            iterations += 1
            if iteration_limit is not None and iterations >= max(1, int(iteration_limit)):
                break
            if not stop['value'] and not once:
                sleep(max(0.2, float(interval_sec)))
    finally:
        worker.close()
    return {'error_count': error_count, 'iterations': iterations}


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    fetcher = RuntimeSnapshotFetcher(
        runtime_url=args.runtime_url,
        bootstrap_token=args.bootstrap_token,
        runtime_token=args.runtime_token,
        timeout_sec=max(0.1, float(args.timeout_sec)),
    )
    worker = MediaWorker()
    stop_flag = {'value': False}

    def handle_signal(_signum, _frame):
        stop_flag['value'] = True

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    run_worker_loop(
        fetcher=fetcher,
        worker=worker,
        interval_sec=max(0.2, float(args.interval_sec)),
        once=args.once,
        stop_flag=stop_flag,
    )
    return 0


if __name__ == '__main__':
    raise SystemExit(main())