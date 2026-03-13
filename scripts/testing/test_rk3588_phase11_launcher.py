import pathlib
import unittest


class Rk3588Phase11LauncherTests(unittest.TestCase):
    def test_phase11_launcher_exists_and_points_to_handoff_runner(self):
        root = pathlib.Path(__file__).resolve().parents[2]
        launcher = root / "scripts" / "rk3588" / "Run-Phase11-Handoff.sh"
        self.assertTrue(launcher.exists(), f"launcher missing: {launcher}")
        content = launcher.read_text(encoding="utf-8")
        self.assertIn("run_phase11_handoff.py", content)
        self.assertIn("python3", content)


if __name__ == "__main__":
    unittest.main()
