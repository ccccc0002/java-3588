import pathlib
import unittest


class Rk3588Phase3NextwaveLoopLauncherTests(unittest.TestCase):
    def test_phase3_nextwave_loop_launcher_points_to_loop_script(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        launcher = root / "scripts" / "rk3588" / "Run-Phase3-Nextwave-Loop.sh"
        self.assertTrue(launcher.exists(), f"launcher missing: {launcher}")
        content = launcher.read_text(encoding="utf-8")
        self.assertIn("parallel_nextwave_loop.py", content)
        self.assertIn("python3", content)


if __name__ == "__main__":
    unittest.main()
