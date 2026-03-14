import importlib.util
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / "rk3588" / "parallel_nextwave_loop.py"
SPEC = importlib.util.spec_from_file_location("parallel_nextwave_loop", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load module from {MODULE_PATH}")
parallel_nextwave_loop = importlib.util.module_from_spec(SPEC)
sys.modules["parallel_nextwave_loop"] = parallel_nextwave_loop
SPEC.loader.exec_module(parallel_nextwave_loop)


class _FakeRunner:
    def __init__(self, steps):
        self.steps = list(steps)
        self.calls = []

    def __call__(self, command):
        self.calls.append(list(command))
        if not self.steps:
            raise AssertionError(f"unexpected command: {command}")
        expected_args, payload = self.steps.pop(0)
        actual_args = tuple(command[2:])
        if tuple(expected_args) != actual_args:
            raise AssertionError(f"unexpected command args: {actual_args}, expected: {expected_args}")
        return subprocess.CompletedProcess(
            command,
            0,
            stdout=json.dumps(payload, ensure_ascii=True),
            stderr="",
        )


class ParallelNextwaveLoopTests(unittest.TestCase):
    def test_parse_args_can_disable_stop_on_failure(self):
        args = parallel_nextwave_loop.parse_args(["--continue-on-failure"])
        self.assertFalse(args.stop_on_failure)

    def test_resolve_start_index_uses_latest_existing_session(self):
        index = parallel_nextwave_loop.resolve_start_index(
            session_prefix="phase3-nextwave-r",
            requested_start_index=0,
            sessions=["phase3-nextwave-r1", "phase3-nextwave-r2", "phase3-nextwave-r10", "other"],
        )
        self.assertEqual(11, index)

    def test_execute_loop_runs_once_and_prunes_on_success(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            out_dir = pathlib.Path(temp_dir)
            args = parallel_nextwave_loop.parse_args(
                [
                    "--lane-file",
                    "scripts/rk3588/lanes/phase3-nextwave.json",
                    "--iterations",
                    "1",
                    "--report-tail-lines",
                    "5",
                    "--keep-latest",
                    "2",
                    "--prune-prefix",
                    "phase3-nextwave-",
                    "--output-dir",
                    str(out_dir),
                    "--poll-interval-sec",
                    "0",
                ]
            )

            runner = _FakeRunner(
                [
                    (("list",), {"status": "listed", "sessions": ["phase3-nextwave-r1", "phase3-nextwave-r2"]}),
                    (
                        (
                            "start",
                            "--session",
                            "phase3-nextwave-r3",
                            "--force",
                            "--lane-file",
                            "scripts/rk3588/lanes/phase3-nextwave.json",
                            "--workdir",
                            ".",
                        ),
                        {"status": "started", "session": "phase3-nextwave-r3"},
                    ),
                    (
                        ("report", "--session", "phase3-nextwave-r3", "--tail-lines", "5"),
                        {"status": "passed", "session": "phase3-nextwave-r3"},
                    ),
                    (
                        ("prune", "--session-prefix", "phase3-nextwave-", "--keep-latest", "2"),
                        {"status": "pruned", "killed": [], "kept": ["phase3-nextwave-r2", "phase3-nextwave-r3"]},
                    ),
                ]
            )

            summary = parallel_nextwave_loop.execute_loop(
                args,
                runner=runner,
                now_fn=lambda: 0.0,
                sleep_fn=lambda _seconds: None,
            )

            self.assertEqual("passed", summary["status"])
            self.assertEqual(1, len(summary["runs"]))
            self.assertEqual("phase3-nextwave-r3", summary["runs"][0]["session"])
            self.assertEqual("passed", summary["runs"][0]["report_status"])
            self.assertEqual("pruned", summary["runs"][0]["prune_status"])
            self.assertTrue((out_dir / "latest.json").exists())

    def test_execute_loop_stops_on_failure(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            args = parallel_nextwave_loop.parse_args(
                [
                    "--lane-file",
                    "scripts/rk3588/lanes/phase3-nextwave.json",
                    "--iterations",
                    "2",
                    "--output-dir",
                    temp_dir,
                    "--poll-interval-sec",
                    "0",
                ]
            )

            runner = _FakeRunner(
                [
                    (("list",), {"status": "listed", "sessions": []}),
                    (
                        (
                            "start",
                            "--session",
                            "phase3-nextwave-r1",
                            "--force",
                            "--lane-file",
                            "scripts/rk3588/lanes/phase3-nextwave.json",
                            "--workdir",
                            ".",
                        ),
                        {"status": "started", "session": "phase3-nextwave-r1"},
                    ),
                    (
                        ("report", "--session", "phase3-nextwave-r1", "--tail-lines", "12"),
                        {"status": "failed", "session": "phase3-nextwave-r1"},
                    ),
                    (
                        ("prune", "--session-prefix", "phase3-nextwave-", "--keep-latest", "4"),
                        {"status": "pruned", "killed": [], "kept": ["phase3-nextwave-r1"]},
                    ),
                ]
            )

            summary = parallel_nextwave_loop.execute_loop(
                args,
                runner=runner,
                now_fn=lambda: 0.0,
                sleep_fn=lambda _seconds: None,
            )

            self.assertEqual("failed", summary["status"])
            self.assertEqual(1, len(summary["runs"]))
            self.assertEqual("failed", summary["runs"][0]["report_status"])
            self.assertEqual(4, len(runner.calls))

    def test_execute_loop_can_continue_after_failure(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            args = parallel_nextwave_loop.parse_args(
                [
                    "--lane-file",
                    "scripts/rk3588/lanes/phase3-nextwave.json",
                    "--iterations",
                    "2",
                    "--output-dir",
                    temp_dir,
                    "--poll-interval-sec",
                    "0",
                    "--continue-on-failure",
                ]
            )

            runner = _FakeRunner(
                [
                    (("list",), {"status": "listed", "sessions": []}),
                    (
                        (
                            "start",
                            "--session",
                            "phase3-nextwave-r1",
                            "--force",
                            "--lane-file",
                            "scripts/rk3588/lanes/phase3-nextwave.json",
                            "--workdir",
                            ".",
                        ),
                        {"status": "started", "session": "phase3-nextwave-r1"},
                    ),
                    (
                        ("report", "--session", "phase3-nextwave-r1", "--tail-lines", "12"),
                        {"status": "failed", "session": "phase3-nextwave-r1"},
                    ),
                    (
                        ("prune", "--session-prefix", "phase3-nextwave-", "--keep-latest", "4"),
                        {"status": "pruned", "killed": [], "kept": ["phase3-nextwave-r1"]},
                    ),
                    (
                        (
                            "start",
                            "--session",
                            "phase3-nextwave-r2",
                            "--force",
                            "--lane-file",
                            "scripts/rk3588/lanes/phase3-nextwave.json",
                            "--workdir",
                            ".",
                        ),
                        {"status": "started", "session": "phase3-nextwave-r2"},
                    ),
                    (
                        ("report", "--session", "phase3-nextwave-r2", "--tail-lines", "12"),
                        {"status": "passed", "session": "phase3-nextwave-r2"},
                    ),
                    (
                        ("prune", "--session-prefix", "phase3-nextwave-", "--keep-latest", "4"),
                        {"status": "pruned", "killed": [], "kept": ["phase3-nextwave-r1", "phase3-nextwave-r2"]},
                    ),
                ]
            )

            summary = parallel_nextwave_loop.execute_loop(
                args,
                runner=runner,
                now_fn=lambda: 0.0,
                sleep_fn=lambda _seconds: None,
            )

            self.assertEqual("failed", summary["status"])
            self.assertEqual(2, len(summary["runs"]))
            self.assertEqual("failed", summary["runs"][0]["report_status"])
            self.assertEqual("passed", summary["runs"][1]["report_status"])
            self.assertEqual(7, len(runner.calls))


if __name__ == "__main__":
    unittest.main()
