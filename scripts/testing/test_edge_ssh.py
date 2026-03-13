import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent.parent / 'rk3588' / 'edge_ssh.py'
SPEC = importlib.util.spec_from_file_location('edge_ssh', MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f'Unable to load module from {MODULE_PATH}')
edge_ssh = importlib.util.module_from_spec(SPEC)
sys.modules['edge_ssh'] = edge_ssh
SPEC.loader.exec_module(edge_ssh)


class EdgeSshTests(unittest.TestCase):
    def test_parse_transfer_spec_accepts_valid_pair(self):
        local_path, remote_path = edge_ssh.parse_transfer_spec('a.txt:/tmp/a.txt')
        self.assertEqual('a.txt', local_path)
        self.assertEqual('/tmp/a.txt', remote_path)

    def test_parse_transfer_spec_rejects_invalid_pair(self):
        with self.assertRaises(ValueError):
            edge_ssh.parse_transfer_spec('missing_delimiter')

    def test_compose_remote_command_wraps_with_workdir(self):
        command = edge_ssh.compose_remote_command('python3 --version', '/home/zql/ks/java-rk3588')
        self.assertIn("cd '/home/zql/ks/java-rk3588' && python3 --version", command)

    def test_load_profile_reads_required_fields(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            profile_path = pathlib.Path(temp_dir) / 'rk3588-edge.local.json'
            profile_path.write_text(
                json.dumps(
                    {
                        'host': '192.168.1.104',
                        'username': 'zql',
                        'password': 'secret',
                        'workdir': '/home/zql/ks/java-rk3588',
                    }
                ),
                encoding='utf-8',
            )
            profile = edge_ssh.load_profile(profile_path)

        self.assertEqual('192.168.1.104', profile.host)
        self.assertEqual('zql', profile.username)
        self.assertEqual('secret', profile.password)
        self.assertEqual('/home/zql/ks/java-rk3588', profile.workdir)

    def test_resolve_remote_path_uses_workdir_for_relative_target(self):
        resolved = edge_ssh.resolve_remote_path('scripts/rk3588/tmux_parallel_ctl.py', '/home/zql/ks/java-rk3588')
        self.assertEqual('/home/zql/ks/java-rk3588/scripts/rk3588/tmux_parallel_ctl.py', resolved)


if __name__ == '__main__':
    unittest.main()
