import importlib.util
import pathlib
import sys
import tempfile
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'hw_support.py'
SPEC = importlib.util.spec_from_file_location('hw_support', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
hw_support = importlib.util.module_from_spec(SPEC)
sys.modules['hw_support'] = hw_support
SPEC.loader.exec_module(hw_support)


class HwSupportTests(unittest.TestCase):
    def test_resolve_ffmpeg_binary_prefers_repo_runtime_toolchain(self):
        with tempfile.TemporaryDirectory() as tmp:
            repo_root = pathlib.Path(tmp)
            ffmpeg_bin = repo_root / 'runtime' / 'toolchains' / 'ffmpeg-rockchip' / 'bin' / 'ffmpeg'
            ffmpeg_bin.parent.mkdir(parents=True, exist_ok=True)
            ffmpeg_bin.write_text('#!/bin/sh\n', encoding='utf-8')

            resolved = hw_support.resolve_ffmpeg_binary(
                ffmpeg_bin='ffmpeg',
                repo_root=repo_root,
                which_fn=lambda _binary: '/usr/bin/ffmpeg',
            )

            self.assertEqual(str(ffmpeg_bin.resolve()), resolved)

    def test_probe_runtime_capabilities_detects_ffmpeg_and_gstreamer_features(self):
        def fake_runner(command, timeout_sec):
            key = tuple(command)
            if key == ('/opt/ffmpeg-rockchip/bin/ffmpeg', '-hide_banner', '-decoders'):
                return hw_support.ToolProbe(True, stdout=' V..... h264_rkmpp\n V..... hevc_rkmpp\n')
            if key == ('/opt/ffmpeg-rockchip/bin/ffmpeg', '-hide_banner', '-encoders'):
                return hw_support.ToolProbe(True, stdout=' V..... h264_rkmpp\n V..... hevc_rkmpp\n')
            if key == ('/opt/ffmpeg-rockchip/bin/ffmpeg', '-hide_banner', '-filters'):
                return hw_support.ToolProbe(True, stdout=' ... scale_rkrga ...\n')
            if key in {
                ('gst-inspect-1.0', 'mppvideodec'),
                ('gst-inspect-1.0', 'mpph264enc'),
                ('gst-inspect-1.0', 'mpph265enc'),
                ('gst-inspect-1.0', 'rgaconvert'),
            }:
                return hw_support.ToolProbe(True, stdout='ok')
            raise AssertionError(f'unexpected command: {command}')

        original_binary_exists = hw_support._binary_exists
        original_resolve = hw_support.resolve_ffmpeg_binary
        try:
            hw_support._binary_exists = lambda binary: True
            hw_support.resolve_ffmpeg_binary = lambda ffmpeg_bin='ffmpeg', repo_root=None, env=None, which_fn=None: '/opt/ffmpeg-rockchip/bin/ffmpeg'
            caps = hw_support.probe_runtime_capabilities(runner=fake_runner)
        finally:
            hw_support._binary_exists = original_binary_exists
            hw_support.resolve_ffmpeg_binary = original_resolve

        self.assertTrue(caps.has_ffmpeg_mpp_rga_decode)
        self.assertTrue(caps.has_ffmpeg_mpp_rga_transcode)
        self.assertTrue(caps.has_gstreamer_mpp_rga)
        self.assertEqual('/opt/ffmpeg-rockchip/bin/ffmpeg', caps.ffmpeg_path)

    def test_ensure_ffmpeg_mpp_rga_decode_support_raises_clear_error(self):
        original_cached = hw_support.cached_runtime_capabilities
        try:
            hw_support.cached_runtime_capabilities = lambda ffmpeg_bin='ffmpeg': hw_support.RuntimeCapabilities(
                ffmpeg_path='ffmpeg',
                ffmpeg_available=True,
                ffmpeg_h264_rkmpp_decoder=False,
                ffmpeg_hevc_rkmpp_decoder=False,
                ffmpeg_h264_rkmpp_encoder=False,
                ffmpeg_hevc_rkmpp_encoder=False,
                ffmpeg_scale_rkrga=False,
                gst_inspect_available=True,
                gst_launch_available=True,
                gst_mppvideodec=True,
                gst_mpph264enc=True,
                gst_mpph265enc=True,
                gst_rgaconvert=False,
            )
            with self.assertRaises(RuntimeError) as ctx:
                hw_support.ensure_ffmpeg_mpp_rga_decode_support()
        finally:
            hw_support.cached_runtime_capabilities = original_cached

        message = str(ctx.exception)
        self.assertIn('h264_rkmpp', message)
        self.assertIn('hevc_rkmpp', message)
        self.assertIn('scale_rkrga', message)
        self.assertIn('rgaconvert', message)


if __name__ == '__main__':
    unittest.main()
