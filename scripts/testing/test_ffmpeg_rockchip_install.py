import importlib.util
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'ffmpeg_rockchip_install.py'
SPEC = importlib.util.spec_from_file_location('ffmpeg_rockchip_install', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
ffmpeg_rockchip_install = importlib.util.module_from_spec(SPEC)
sys.modules['ffmpeg_rockchip_install'] = ffmpeg_rockchip_install
SPEC.loader.exec_module(ffmpeg_rockchip_install)


class FfmpegRockchipInstallTests(unittest.TestCase):
    def test_build_commands_include_rkmpp_rkrga_and_dependency_builds(self):
        config = ffmpeg_rockchip_install.FfmpegRockchipBuildConfig(
            workspace_root=pathlib.Path('/opt/workspace'),
            install_prefix=pathlib.Path('/opt/ffmpeg-rockchip'),
            rtsp_probe_url='rtsp://demo/stream',
        )

        commands = ffmpeg_rockchip_install.build_commands(config)
        script = '\n'.join(commands)

        self.assertIn('HermanChen/mpp', script)
        self.assertIn('HermanChen/mpp.git"', script)
        self.assertIn('--branch "develop"', script)
        self.assertIn('airockchip/librga.git', script)
        self.assertIn('ffmpeg-rockchip.git', script)
        self.assertIn('airockchip/librga', script)
        self.assertIn('nyanmisaka/ffmpeg-rockchip', script)
        self.assertIn('--enable-rkmpp', script)
        self.assertIn('--enable-rkrga', script)
        self.assertIn('--enable-libdrm', script)
        self.assertIn('cmake', script)
        self.assertIn('make -j"$(nproc)"', script)
        self.assertIn('ld.so.conf.d/ffmpeg-rockchip.conf', script)
        self.assertIn('ldconfig', script)

    def test_verify_commands_use_rtsp_probe_and_required_features(self):
        config = ffmpeg_rockchip_install.FfmpegRockchipBuildConfig(
            workspace_root=pathlib.Path('/opt/workspace'),
            install_prefix=pathlib.Path('/opt/ffmpeg-rockchip'),
            rtsp_probe_url='rtsp://admin:pwd@192.168.1.245:554/h264/ch1/main/av_stream',
        )

        commands = ffmpeg_rockchip_install.verify_commands(config)
        script = '\n'.join(commands)

        self.assertIn('h264_rkmpp', script)
        self.assertIn('hevc_rkmpp', script)
        self.assertIn('scale_rkrga', script)
        self.assertIn('rtsp://admin:pwd@192.168.1.245:554/h264/ch1/main/av_stream', script)
        self.assertIn('-hwaccel rkmpp', script)
        self.assertIn('-vf scale_rkrga', script)

    def test_parse_args_supports_script_only_for_shell_wrapper(self):
        args = ffmpeg_rockchip_install.parse_args(['build', '--script-only'])

        self.assertTrue(args.script_only)
        self.assertEqual('build', args.mode)


if __name__ == '__main__':
    unittest.main()
