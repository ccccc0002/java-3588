import importlib.util
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'zlm_runtime_ctl.py'
SPEC = importlib.util.spec_from_file_location('zlm_runtime_ctl', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
zlm_runtime_ctl = importlib.util.module_from_spec(SPEC)
sys.modules['zlm_runtime_ctl'] = zlm_runtime_ctl
SPEC.loader.exec_module(zlm_runtime_ctl)


class ZlmRuntimeControllerTests(unittest.TestCase):
    def test_render_runtime_config_overrides_ports_paths_and_hooks(self):
        template = """[api]
secret=old
snapRoot=./www/snap/
defaultSnap=./www/logo.png
downloadRoot=./www
[http]
port=1985
rootPath=./www
sslport=1986
[protocol]
mp4_save_path=./www
hls_save_path=./www
[rtmp]
port=1935
sslport=0
[rtsp]
port=554
sslport=0
[rtp_proxy]
port=10000
[srt]
port=9000
[rtc]
port=8000
tcpPort=8000
icePort=3478
iceTcpPort=3478
enableTurn=1
signalingPort=3000
signalingSslPort=3001
[onvif]
port=3702
[hook]
enable=1
on_play=http://127.0.0.1:11001/index/hook/on_play
on_publish=http://127.0.0.1:11001/index/hook/on_publish
[general]
mediaServerId=old-id
"""
        media_root = pathlib.Path('/workspace/runtime/zlm/www').resolve()
        rendered = zlm_runtime_ctl.render_runtime_config(
            template_text=template,
            http_port=1987,
            rtmp_port=19350,
            rtsp_port=1554,
            rtp_proxy_port=10050,
            srt_port=19000,
            rtc_udp_port=18000,
            rtc_tcp_port=18001,
            rtc_ice_udp_port=13478,
            rtc_ice_tcp_port=13479,
            onvif_port=13702,
            rtc_signal_port=13000,
            rtc_signal_ssl_port=0,
            api_secret='demo-secret',
            media_server_id='java-rk3588-zlm',
            media_root=media_root,
        )

        self.assertIn('secret=demo-secret', rendered)
        self.assertIn('port=1987', rendered)
        self.assertIn('port=19350', rendered)
        self.assertIn('port=1554', rendered)
        self.assertIn('port=10050', rendered)
        self.assertIn('port=19000', rendered)
        self.assertIn('port=18000', rendered)
        self.assertIn('tcpPort=18001', rendered)
        self.assertIn('icePort=13478', rendered)
        self.assertIn('iceTcpPort=13479', rendered)
        self.assertIn('port=13702', rendered)
        self.assertIn('signalingPort=13000', rendered)
        self.assertIn('signalingSslPort=0', rendered)
        self.assertEqual(3, rendered.count('sslport=0'))
        self.assertIn(f'rootPath={media_root.as_posix()}', rendered)
        self.assertIn(f'downloadRoot={media_root.as_posix()}', rendered)
        self.assertIn(f'mp4_save_path={media_root.as_posix()}', rendered)
        self.assertIn(f'hls_save_path={media_root.as_posix()}', rendered)
        self.assertIn(f'snapRoot={(media_root / "snap").as_posix()}/', rendered)
        self.assertIn(f'defaultSnap={(media_root / "logo.png").as_posix()}', rendered)
        self.assertIn('enableTurn=0', rendered)
        self.assertIn('enable=0', rendered)
        self.assertIn('on_publish=', rendered)
        self.assertIn('on_play=', rendered)
        self.assertIn('mediaServerId=java-rk3588-zlm', rendered)

    def test_start_runtime_writes_config_and_creates_media_root(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = pathlib.Path(temp_dir)
            runtime_dir = repo_root / 'runtime'
            template_path = runtime_dir / 'zlm-template.ini'
            template_path.parent.mkdir(parents=True, exist_ok=True)
            template_path.write_text(
                '[api]\nsecret=old\nsnapRoot=./www/snap/\ndefaultSnap=./www/logo.png\ndownloadRoot=./www\n'
                '[http]\nport=1985\nrootPath=./www\n[protocol]\nmp4_save_path=./www\nhls_save_path=./www\n'
                '[rtmp]\nport=1935\n[rtsp]\nport=554\n[rtp_proxy]\nport=10000\n[srt]\nport=9000\n'
                '[rtc]\nport=8000\ntcpPort=8000\nicePort=3478\niceTcpPort=3478\nenableTurn=1\nsignalingPort=3000\nsignalingSslPort=3001\n'
                '[onvif]\nport=3702\n[hook]\nenable=1\n',
                encoding='utf-8',
            )
            config = zlm_runtime_ctl.ZlmRuntimeConfig(
                repo_root=repo_root,
                runtime_dir=runtime_dir,
                pid_path=runtime_dir / 'zlm-runtime.pid',
                log_path=runtime_dir / 'zlm-runtime.log',
                media_server_bin=repo_root / 'bin' / 'MediaServer',
                template_config_path=template_path,
                generated_config_path=runtime_dir / 'zlm' / 'config.ini',
                http_port=1987,
                rtmp_port=19350,
                rtsp_port=1554,
                rtp_proxy_port=10050,
                srt_port=19000,
                rtc_udp_port=18000,
                rtc_tcp_port=18001,
                rtc_ice_udp_port=13478,
                rtc_ice_tcp_port=13479,
                onvif_port=13702,
                rtc_signal_port=13000,
                rtc_signal_ssl_port=0,
                api_secret='demo-secret',
            )
            config.media_server_bin.parent.mkdir(parents=True, exist_ok=True)
            config.media_server_bin.write_text('', encoding='utf-8')
            process = mock.Mock(pid=2468)
            process.poll.return_value = None

            with mock.patch.object(zlm_runtime_ctl.subprocess, 'Popen', return_value=process) as popen_mock:
                result = zlm_runtime_ctl.start_runtime(config)

            self.assertEqual('started', result['status'])
            rendered = config.generated_config_path.read_text(encoding='utf-8')
            media_root = zlm_runtime_ctl.media_root_path(config)
            self.assertTrue(media_root.is_dir())
            self.assertTrue((media_root / 'snap').is_dir())
            self.assertIn(f'rootPath={media_root.as_posix()}', rendered)
            self.assertIn('enableTurn=0', rendered)
            self.assertIn('signalingPort=13000', rendered)
            self.assertIn('signalingSslPort=0', rendered)
            popen_args, popen_kwargs = popen_mock.call_args
            self.assertEqual(
                [str(config.media_server_bin), '-c', str(config.generated_config_path), '--log-dir', str(runtime_dir / 'zlm' / 'logs')],
                popen_args[0],
            )
            self.assertEqual(str(config.media_server_bin.parent), popen_kwargs['cwd'])


if __name__ == '__main__':
    unittest.main()
