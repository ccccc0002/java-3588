#!/usr/bin/env python3
"""Non-interactive SSH helper for RK3588 board operations."""

from __future__ import annotations

import argparse
import json
import posixpath
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Sequence, Tuple

import paramiko


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DEFAULT_PROFILE = REPO_ROOT / 'state' / 'local' / 'rk3588-edge.local.json'


@dataclass(frozen=True)
class EdgeProfile:
    host: str
    username: str
    password: str
    workdir: str


def safe_stream_write(stream, text: str) -> None:
    content = str(text or '')
    if not content:
        return
    try:
        stream.write(content)
        stream.flush()
        return
    except UnicodeEncodeError:
        pass

    # Fallback for Windows consoles using non-UTF code pages.
    if hasattr(stream, 'buffer'):
        encoded = content.encode('utf-8', errors='replace')
        stream.buffer.write(encoded)
        stream.flush()
        return

    sanitized = content.encode('ascii', errors='replace').decode('ascii')
    stream.write(sanitized)
    stream.flush()


def parse_transfer_spec(spec: str) -> Tuple[str, str]:
    value = str(spec or '').strip()
    if ':' not in value:
        raise ValueError(f'invalid transfer spec: {spec}')
    left, right = value.split(':', 1)
    left = left.strip()
    right = right.strip()
    if not left or not right:
        raise ValueError(f'invalid transfer spec: {spec}')
    return left, right


def load_profile(path: Path) -> EdgeProfile:
    content = json.loads(path.read_text(encoding='utf-8-sig'))
    host = str(content.get('host', '')).strip()
    username = str(content.get('username', '')).strip()
    password = str(content.get('password', '')).strip()
    workdir = str(content.get('workdir', '')).strip()
    if not host or not username or not password:
        raise ValueError(f'invalid edge profile: {path}')
    return EdgeProfile(
        host=host,
        username=username,
        password=password,
        workdir=workdir,
    )


def compose_remote_command(command: str, workdir: str) -> str:
    cmd = str(command or '').strip()
    if not cmd:
        return ''
    wd = str(workdir or '').strip()
    if not wd:
        return cmd
    escaped = wd.replace("'", "'\"'\"'")
    return f"cd '{escaped}' && {cmd}"


def sftp_mkdirs(sftp: paramiko.SFTPClient, remote_dir: str) -> None:
    target = str(remote_dir or '').strip()
    if not target:
        return
    normalized = posixpath.normpath(target)
    current = '/'
    for part in normalized.split('/'):
        if not part:
            continue
        current = posixpath.join(current, part)
        try:
            sftp.stat(current)
        except FileNotFoundError:
            sftp.mkdir(current)


def sftp_upload(sftp: paramiko.SFTPClient, local_path: Path, remote_path: str) -> None:
    remote = str(remote_path).strip()
    remote_parent = posixpath.dirname(remote)
    sftp_mkdirs(sftp, remote_parent)
    sftp.put(str(local_path), remote)


def sftp_download(sftp: paramiko.SFTPClient, remote_path: str, local_path: Path) -> None:
    local_path.parent.mkdir(parents=True, exist_ok=True)
    sftp.get(remote_path, str(local_path))


def resolve_remote_path(path: str, workdir: str) -> str:
    target = str(path or '').strip()
    if not target:
        return target
    if target.startswith('/'):
        return target
    base = str(workdir or '').strip()
    if not base:
        return target
    return posixpath.normpath(posixpath.join(base, target))


def run_remote_command(
    client: paramiko.SSHClient,
    command: str,
    timeout_sec: int,
) -> Tuple[int, str, str, bool]:
    stdin, stdout, stderr = client.exec_command(command, get_pty=False)
    if stdin is not None:
        try:
            stdin.close()
        except Exception:
            pass

    channel = stdout.channel
    out_chunks: List[str] = []
    err_chunks: List[str] = []
    start = time.monotonic()
    timed_out = False

    while True:
        if channel.recv_ready():
            out_chunks.append(channel.recv(4096).decode('utf-8', errors='replace'))
        if channel.recv_stderr_ready():
            err_chunks.append(channel.recv_stderr(4096).decode('utf-8', errors='replace'))

        if channel.exit_status_ready():
            while channel.recv_ready():
                out_chunks.append(channel.recv(4096).decode('utf-8', errors='replace'))
            while channel.recv_stderr_ready():
                err_chunks.append(channel.recv_stderr(4096).decode('utf-8', errors='replace'))
            break

        if timeout_sec > 0 and (time.monotonic() - start) >= timeout_sec:
            timed_out = True
            channel.close()
            break
        time.sleep(0.05)

    exit_code = 124 if timed_out else int(channel.recv_exit_status())
    return exit_code, ''.join(out_chunks), ''.join(err_chunks), timed_out


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description='Run remote commands on RK3588 over SSH')
    parser.add_argument('--profile', default=str(DEFAULT_PROFILE))
    parser.add_argument('--host', default='')
    parser.add_argument('--username', default='')
    parser.add_argument('--password', default='')
    parser.add_argument('--workdir', default='')
    parser.add_argument('--command', default='')
    parser.add_argument('--upload', action='append', default=[])
    parser.add_argument('--download', action='append', default=[])
    parser.add_argument('--timeout-sec', type=int, default=120)
    parser.add_argument('--json-only', action='store_true', default=False)
    return parser.parse_args(argv)


def resolve_profile(args: argparse.Namespace) -> EdgeProfile:
    profile_path = Path(args.profile).resolve()
    profile = load_profile(profile_path)
    host = str(args.host or profile.host).strip()
    username = str(args.username or profile.username).strip()
    password = str(args.password or profile.password).strip()
    workdir = str(args.workdir or profile.workdir).strip()
    if not host or not username or not password:
        raise ValueError('missing ssh credentials')
    return EdgeProfile(host=host, username=username, password=password, workdir=workdir)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    profile = resolve_profile(args)
    command = compose_remote_command(args.command, profile.workdir)
    uploads = [parse_transfer_spec(item) for item in args.upload]
    downloads = [parse_transfer_spec(item) for item in args.download]
    resolved_uploads = [
        (local, resolve_remote_path(remote, profile.workdir))
        for local, remote in uploads
    ]
    resolved_downloads = [
        (resolve_remote_path(remote, profile.workdir), local)
        for remote, local in downloads
    ]

    result = {
        'status': 'ok',
        'host': profile.host,
        'username': profile.username,
        'workdir': profile.workdir,
        'uploads': [f'{src}:{dst}' for src, dst in resolved_uploads],
        'downloads': [f'{src}:{dst}' for src, dst in resolved_downloads],
        'command': command,
        'timeout_sec': int(args.timeout_sec),
        'exit_code': 0,
        'timed_out': False,
        'stdout': '',
        'stderr': '',
    }

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(
            hostname=profile.host,
            username=profile.username,
            password=profile.password,
            timeout=max(int(args.timeout_sec), 1),
            look_for_keys=False,
            allow_agent=False,
        )

        if resolved_uploads or resolved_downloads:
            with client.open_sftp() as sftp:
                for local, remote in resolved_uploads:
                    sftp_upload(sftp, Path(local).resolve(), remote)
                for remote, local in resolved_downloads:
                    sftp_download(sftp, remote, Path(local).resolve())

        if command:
            exit_code, stdout, stderr, timed_out = run_remote_command(
                client=client,
                command=command,
                timeout_sec=max(int(args.timeout_sec), 0),
            )
            result['exit_code'] = int(exit_code)
            result['timed_out'] = bool(timed_out)
            result['stdout'] = stdout
            result['stderr'] = stderr
            if exit_code != 0:
                result['status'] = 'failed'
    finally:
        client.close()

    if not args.json_only:
        if result['stdout']:
            safe_stream_write(sys.stdout, str(result['stdout']))
        if result['stderr']:
            safe_stream_write(sys.stderr, str(result['stderr']))
    safe_stream_write(sys.stdout, json.dumps(result, ensure_ascii=True) + '\n')
    return int(result['exit_code'])


if __name__ == '__main__':
    raise SystemExit(main())
