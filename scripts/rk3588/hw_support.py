from __future__ import annotations

import os
import subprocess
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from shutil import which
from typing import Callable, Mapping, Sequence

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent


@dataclass(frozen=True)
class ToolProbe:
    available: bool
    stdout: str = ''
    stderr: str = ''
    error: str = ''


@dataclass(frozen=True)
class RuntimeCapabilities:
    ffmpeg_path: str
    ffmpeg_available: bool
    ffmpeg_h264_rkmpp_decoder: bool
    ffmpeg_hevc_rkmpp_decoder: bool
    ffmpeg_h264_rkmpp_encoder: bool
    ffmpeg_hevc_rkmpp_encoder: bool
    ffmpeg_scale_rkrga: bool
    gst_inspect_available: bool
    gst_launch_available: bool
    gst_mppvideodec: bool
    gst_mpph264enc: bool
    gst_mpph265enc: bool
    gst_rgaconvert: bool

    @property
    def has_ffmpeg_mpp_rga_decode(self) -> bool:
        return (
            self.ffmpeg_available
            and self.ffmpeg_h264_rkmpp_decoder
            and self.ffmpeg_hevc_rkmpp_decoder
            and self.ffmpeg_scale_rkrga
        )

    @property
    def has_ffmpeg_mpp_rga_transcode(self) -> bool:
        return self.has_ffmpeg_mpp_rga_decode and self.ffmpeg_h264_rkmpp_encoder

    @property
    def has_gstreamer_mpp_rga(self) -> bool:
        return (
            self.gst_inspect_available
            and self.gst_launch_available
            and self.gst_mppvideodec
            and self.gst_rgaconvert
            and self.gst_mpph264enc
        )

    def summary(self) -> str:
        ffmpeg_part = (
            f"ffmpeg(dec_h264={self.ffmpeg_h264_rkmpp_decoder},"
            f"dec_h265={self.ffmpeg_hevc_rkmpp_decoder},"
            f"enc_h264={self.ffmpeg_h264_rkmpp_encoder},"
            f"scale_rkrga={self.ffmpeg_scale_rkrga})"
        )
        gst_part = (
            f"gstreamer(mppvideodec={self.gst_mppvideodec},"
            f"mpph264enc={self.gst_mpph264enc},"
            f"mpph265enc={self.gst_mpph265enc},"
            f"rgaconvert={self.gst_rgaconvert})"
        )
        return f"{ffmpeg_part}; {gst_part}"


def _run_command(command: Sequence[str], timeout_sec: float = 5.0) -> ToolProbe:
    try:
        completed = subprocess.run(
            list(command),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=max(0.1, float(timeout_sec)),
            check=False,
        )
    except FileNotFoundError as exc:
        return ToolProbe(False, error=str(exc))
    except Exception as exc:
        return ToolProbe(False, error=str(exc))
    return ToolProbe(
        available=completed.returncode == 0,
        stdout=completed.stdout.decode('utf-8', errors='replace'),
        stderr=completed.stderr.decode('utf-8', errors='replace'),
    )


def _binary_exists(binary: str) -> bool:
    text = str(binary or '').strip()
    if not text:
        return False
    candidate = Path(text).expanduser()
    if candidate.exists():
        return True
    return bool(which(text))


def _contains_token(text: str, token: str) -> bool:
    return token.lower() in str(text or '').lower()


def _looks_like_path(value: str) -> bool:
    text = str(value or '').strip()
    if not text:
        return False
    return text.startswith(('.', '~')) or '/' in text or '\\' in text


def _iter_ffmpeg_candidates(
    ffmpeg_bin: str = 'ffmpeg',
    repo_root: Path | None = None,
    env: Mapping[str, str] | None = None,
    which_fn: Callable[[str], str | None] | None = None,
) -> list[str]:
    base_value = str(ffmpeg_bin or 'ffmpeg').strip() or 'ffmpeg'
    current_env = env or os.environ
    resolve_which = which_fn or which
    search_root = Path(repo_root or REPO_ROOT)
    candidates: list[str] = []

    def add_candidate(value: str | os.PathLike[str] | None) -> None:
        text = str(value or '').strip()
        if not text:
            return
        candidate = Path(text).expanduser()
        if _looks_like_path(text) or candidate.is_absolute():
            resolved = str(candidate.resolve(strict=False))
        else:
            resolved = text
        if resolved not in candidates:
            candidates.append(resolved)

    if _looks_like_path(base_value):
        add_candidate(base_value)
    add_candidate(current_env.get('RK3588_FFMPEG_BIN'))
    add_candidate(current_env.get('FFMPEG_BIN'))
    add_candidate(search_root / 'runtime' / 'toolchains' / 'ffmpeg-rockchip' / 'bin' / 'ffmpeg')
    add_candidate(search_root / 'runtime' / 'toolchains' / 'ffmpeg' / 'bin' / 'ffmpeg')
    add_candidate(resolve_which(base_value))
    add_candidate(base_value)
    return candidates


def resolve_ffmpeg_binary(
    ffmpeg_bin: str = 'ffmpeg',
    repo_root: Path | None = None,
    env: Mapping[str, str] | None = None,
    which_fn: Callable[[str], str | None] | None = None,
) -> str:
    fallback = str(ffmpeg_bin or 'ffmpeg').strip() or 'ffmpeg'
    for candidate in _iter_ffmpeg_candidates(ffmpeg_bin=ffmpeg_bin, repo_root=repo_root, env=env, which_fn=which_fn):
        if _binary_exists(candidate):
            return candidate
    return fallback


def probe_runtime_capabilities(
    ffmpeg_bin: str = 'ffmpeg',
    gst_inspect_bin: str = 'gst-inspect-1.0',
    gst_launch_bin: str = 'gst-launch-1.0',
    runner: Callable[[Sequence[str], float], ToolProbe] | None = None,
) -> RuntimeCapabilities:
    run = runner or _run_command
    ffmpeg_path = resolve_ffmpeg_binary(ffmpeg_bin=ffmpeg_bin)

    ffmpeg_available = _binary_exists(ffmpeg_path)
    decoders = run([ffmpeg_path, '-hide_banner', '-decoders'], 5.0) if ffmpeg_available else ToolProbe(False)
    encoders = run([ffmpeg_path, '-hide_banner', '-encoders'], 5.0) if ffmpeg_available else ToolProbe(False)
    filters = run([ffmpeg_path, '-hide_banner', '-filters'], 5.0) if ffmpeg_available else ToolProbe(False)

    gst_inspect_available = _binary_exists(gst_inspect_bin)
    gst_launch_available = _binary_exists(gst_launch_bin)
    gst_mppvideodec = run([gst_inspect_bin, 'mppvideodec'], 5.0).available if gst_inspect_available else False
    gst_mpph264enc = run([gst_inspect_bin, 'mpph264enc'], 5.0).available if gst_inspect_available else False
    gst_mpph265enc = run([gst_inspect_bin, 'mpph265enc'], 5.0).available if gst_inspect_available else False
    gst_rgaconvert = run([gst_inspect_bin, 'rgaconvert'], 5.0).available if gst_inspect_available else False

    return RuntimeCapabilities(
        ffmpeg_path=ffmpeg_path,
        ffmpeg_available=ffmpeg_available,
        ffmpeg_h264_rkmpp_decoder=_contains_token(decoders.stdout, 'h264_rkmpp'),
        ffmpeg_hevc_rkmpp_decoder=_contains_token(decoders.stdout, 'hevc_rkmpp'),
        ffmpeg_h264_rkmpp_encoder=_contains_token(encoders.stdout, 'h264_rkmpp'),
        ffmpeg_hevc_rkmpp_encoder=_contains_token(encoders.stdout, 'hevc_rkmpp'),
        ffmpeg_scale_rkrga=_contains_token(filters.stdout, 'scale_rkrga'),
        gst_inspect_available=gst_inspect_available,
        gst_launch_available=gst_launch_available,
        gst_mppvideodec=gst_mppvideodec,
        gst_mpph264enc=gst_mpph264enc,
        gst_mpph265enc=gst_mpph265enc,
        gst_rgaconvert=gst_rgaconvert,
    )


@lru_cache(maxsize=8)
def cached_runtime_capabilities(ffmpeg_bin: str = 'ffmpeg') -> RuntimeCapabilities:
    return probe_runtime_capabilities(ffmpeg_bin=ffmpeg_bin)


def ensure_ffmpeg_mpp_rga_decode_support(ffmpeg_bin: str = 'ffmpeg') -> RuntimeCapabilities:
    capabilities = cached_runtime_capabilities(ffmpeg_bin=ffmpeg_bin)
    if capabilities.has_ffmpeg_mpp_rga_decode:
        return capabilities
    raise RuntimeError(
        'MPP+RGA decode backend is unavailable. '
        f'Required ffmpeg-rockchip features: h264_rkmpp, hevc_rkmpp, scale_rkrga. Detected: {capabilities.summary()}. '
        'Current code can use gstreamer only after an executable rgaconvert pipeline is implemented and installed.'
    )


def ensure_ffmpeg_mpp_rga_transcode_support(ffmpeg_bin: str = 'ffmpeg') -> RuntimeCapabilities:
    capabilities = cached_runtime_capabilities(ffmpeg_bin=ffmpeg_bin)
    if capabilities.has_ffmpeg_mpp_rga_transcode:
        return capabilities
    raise RuntimeError(
        'MPP+RGA media pipeline is unavailable. '
        f'Required ffmpeg-rockchip features: h264_rkmpp decoder, hevc_rkmpp decoder, h264_rkmpp encoder, scale_rkrga. Detected: {capabilities.summary()}. '
        'If ffmpeg-rockchip is absent, install Rockchip ffmpeg or add a gstreamer pipeline with rgaconvert plus mpp encoders.'
    )
