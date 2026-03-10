from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional


@dataclass(frozen=True)
class FfmpegRockchipBuildConfig:
    workspace_root: Path
    install_prefix: Path
    mpp_repo_url: str = 'https://github.com/HermanChen/mpp.git'
    mpp_branch: str = 'develop'
    librga_repo_url: str = 'https://github.com/airockchip/librga.git'
    librga_branch: str = 'main'
    ffmpeg_repo_url: str = 'https://github.com/nyanmisaka/ffmpeg-rockchip.git'
    ffmpeg_branch: str = 'master'
    rtsp_probe_url: str = ''

    @property
    def source_root(self) -> Path:
        return self.workspace_root / 'src'

    @property
    def build_root(self) -> Path:
        return self.workspace_root / 'build'

    @property
    def mpp_source_dir(self) -> Path:
        return self.source_root / 'mpp'

    @property
    def librga_source_dir(self) -> Path:
        return self.source_root / 'librga'

    @property
    def ffmpeg_source_dir(self) -> Path:
        return self.source_root / 'ffmpeg-rockchip'


def default_config(repo_root: Optional[Path] = None, rtsp_probe_url: str = '') -> FfmpegRockchipBuildConfig:
    root = Path(repo_root or Path(__file__).resolve().parent.parent.parent)
    workspace_root = root / 'runtime' / 'toolchains' / 'ffmpeg-rockchip-build'
    install_prefix = root / 'runtime' / 'toolchains' / 'ffmpeg-rockchip'
    return FfmpegRockchipBuildConfig(
        workspace_root=workspace_root,
        install_prefix=install_prefix,
        rtsp_probe_url=rtsp_probe_url.strip(),
    )


def repo_sync_command(repo_url: str, target_dir: Path, branch: str = 'master') -> str:
    return (
        f'if [ ! -d "{target_dir}/.git" ]; then '
        f'git clone --depth 1 --branch "{branch}" "{repo_url}" "{target_dir}"; '
        f'else git -C "{target_dir}" fetch --depth 1 origin "{branch}" && '
        f'git -C "{target_dir}" checkout -f "{branch}" && '
        f'git -C "{target_dir}" reset --hard "origin/{branch}"; fi'
    )


def build_commands(config: FfmpegRockchipBuildConfig) -> List[str]:
    env = (
        f'export PREFIX="{config.install_prefix}"; '
        f'export PKG_CONFIG_PATH="$PREFIX/lib/pkgconfig:$PREFIX/lib64/pkgconfig:${{PKG_CONFIG_PATH:-}}"; '
        f'export LD_LIBRARY_PATH="$PREFIX/lib:$PREFIX/lib64:${{LD_LIBRARY_PATH:-}}"'
    )
    return [
        'set -euo pipefail',
        'sudo apt-get update',
        'sudo apt-get install -y build-essential git cmake meson ninja-build pkg-config autoconf automake libtool yasm nasm libdrm-dev libudev-dev zlib1g-dev libssl-dev',
        f'mkdir -p "{config.source_root}" "{config.build_root}" "{config.install_prefix}"',
        repo_sync_command(config.mpp_repo_url, config.mpp_source_dir, branch=config.mpp_branch),
        repo_sync_command(config.librga_repo_url, config.librga_source_dir, branch=config.librga_branch),
        repo_sync_command(config.ffmpeg_repo_url, config.ffmpeg_source_dir, branch=config.ffmpeg_branch),
        env,
        f'rm -rf "{config.build_root}/mpp" && mkdir -p "{config.build_root}/mpp" && cd "{config.build_root}/mpp" && cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" -DRKPLATFORM=ON -DHAVE_DRM=ON -DARCH=arm64 "{config.mpp_source_dir}" && make -j"$(nproc)" && make install',
        f'rm -rf "{config.build_root}/librga" && mkdir -p "{config.build_root}/librga" && cd "{config.build_root}/librga" && cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX="$PREFIX" "{config.librga_source_dir}" && make -j"$(nproc)" && make install',
        f'cd "{config.ffmpeg_source_dir}" && ./configure --prefix="$PREFIX" --enable-version3 --enable-gpl --enable-libdrm --enable-rkmpp --enable-rkrga --disable-debug --disable-static --enable-shared',
        f'cd "{config.ffmpeg_source_dir}" && make -j"$(nproc)"',
        f'cd "{config.ffmpeg_source_dir}" && make install',
        "bash -lc \"printf '%s\\n%s\\n' '$PREFIX/lib' '$PREFIX/lib64' > /tmp/ffmpeg-rockchip.conf\"",
        'sudo cp /tmp/ffmpeg-rockchip.conf /etc/ld.so.conf.d/ffmpeg-rockchip.conf',
        'sudo ldconfig',
    ]


def verify_commands(config: FfmpegRockchipBuildConfig) -> List[str]:
    ffmpeg_bin = config.install_prefix / 'bin' / 'ffmpeg'
    commands = [
        'set -euo pipefail',
        f'"{ffmpeg_bin}" -hide_banner -decoders | grep -E "h264_rkmpp|hevc_rkmpp"',
        f'"{ffmpeg_bin}" -hide_banner -encoders | grep -E "h264_rkmpp|hevc_rkmpp"',
        f'"{ffmpeg_bin}" -hide_banner -filters | grep "scale_rkrga"',
    ]
    if config.rtsp_probe_url:
        commands.append(
            f'timeout 20 "{ffmpeg_bin}" -hide_banner -loglevel error -rtsp_transport tcp '
            f'-hwaccel rkmpp -hwaccel_output_format drm_prime -afbc rga -c:v h264_rkmpp '
            f'-i \'{config.rtsp_probe_url}\' -vf scale_rkrga=w=iw:h=ih:format=nv12,hwdownload,format=nv12 '
            f'-frames:v 1 -f null -'
        )
    return commands


def render_script(lines: List[str]) -> str:
    return '\n'.join(lines) + '\n'


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Generate ffmpeg-rockchip build or verify script for RK3588')
    parser.add_argument('mode', choices=['build', 'verify'])
    parser.add_argument('--repo-root', default='')
    parser.add_argument('--workspace-root', default='')
    parser.add_argument('--install-prefix', default='')
    parser.add_argument('--rtsp-probe-url', default='')
    parser.add_argument('--script-only', action='store_true')
    return parser.parse_args(argv)


def build_config(args: argparse.Namespace) -> FfmpegRockchipBuildConfig:
    config = default_config(repo_root=Path(args.repo_root).resolve() if args.repo_root else None, rtsp_probe_url=args.rtsp_probe_url)
    if args.workspace_root:
        config = FfmpegRockchipBuildConfig(
            workspace_root=Path(args.workspace_root).resolve(),
            install_prefix=config.install_prefix,
            mpp_repo_url=config.mpp_repo_url,
            mpp_branch=config.mpp_branch,
            librga_repo_url=config.librga_repo_url,
            librga_branch=config.librga_branch,
            ffmpeg_repo_url=config.ffmpeg_repo_url,
            ffmpeg_branch=config.ffmpeg_branch,
            rtsp_probe_url=config.rtsp_probe_url,
        )
    if args.install_prefix:
        config = FfmpegRockchipBuildConfig(
            workspace_root=config.workspace_root,
            install_prefix=Path(args.install_prefix).resolve(),
            mpp_repo_url=config.mpp_repo_url,
            mpp_branch=config.mpp_branch,
            librga_repo_url=config.librga_repo_url,
            librga_branch=config.librga_branch,
            ffmpeg_repo_url=config.ffmpeg_repo_url,
            ffmpeg_branch=config.ffmpeg_branch,
            rtsp_probe_url=config.rtsp_probe_url,
        )
    return config


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    config = build_config(args)
    lines = build_commands(config) if args.mode == 'build' else verify_commands(config)
    if not args.script_only:
        print(json.dumps({'mode': args.mode, 'workspace_root': str(config.workspace_root), 'install_prefix': str(config.install_prefix)}, ensure_ascii=True))
    print(render_script(lines))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
