import importlib.util
import json
import pathlib
import sys
import unittest

MODULE_PATH = pathlib.Path(__file__).with_name('web_ui_live_smoke.py')
spec = importlib.util.spec_from_file_location('web_ui_live_smoke', MODULE_PATH)
smoke = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = smoke
assert spec.loader is not None
spec.loader.exec_module(smoke)


class FakeSession:
    def __init__(self, responses):
        self.responses = list(responses)
        self.calls = []

    def open(self, method, path, data=None, headers=None, timeout_sec=None):
        self.calls.append({
            'method': method,
            'path': path,
            'data': data,
            'headers': headers,
            'timeout_sec': timeout_sec,
        })
        return self.responses.pop(0)


class RaisingOpener:
    def open(self, req, timeout):
        raise TimeoutError('timed out')


class WebUiLiveSmokeTest(unittest.TestCase):
    def test_http_session_open_returns_synthetic_error_for_timeout(self):
        session = smoke.HttpSession('http://example.com', timeout_sec=1)
        session.opener = RaisingOpener()

        status, body, headers = session.open('GET', '/slow')

        self.assertEqual(status, 0)
        self.assertIn('application/json', headers.get('Content-Type', ''))
        self.assertEqual(headers.get('X-Smoke-Error'), 'TimeoutError')
        payload = json.loads(body.decode('utf-8'))
        self.assertEqual(payload['code'], -1)
        self.assertIn('timed out', payload['msg'])

    def test_collect_camera_context_uses_camera_list_and_returns_first_rtsp_camera(self):
        payload = {
            'code': 0,
            'data': [
                {'id': 100, 'rtspUrl': ''},
                {'id': 200, 'rtspUrl': 'rtsp://example/stream'},
            ],
        }
        session = FakeSession([
            (200, json.dumps(payload).encode('utf-8'), {'Content-Type': 'application/json'})
        ])

        context = smoke.collect_camera_context(session)

        self.assertEqual(session.calls[0]['method'], 'POST')
        self.assertEqual(session.calls[0]['path'], '/camera/listData')
        self.assertEqual(context['camera_id'], 200)
        self.assertEqual(context['rtsp_url'], 'rtsp://example/stream')

    def test_run_target_accepts_list_json_payloads(self):
        session = FakeSession([
            (200, json.dumps([{'id': 1}]).encode('utf-8'), {'Content-Type': 'application/json'})
        ])
        target = smoke.SmokeTarget('POST', '/running', expect_json=True)

        result = smoke.run_target(session, target)

        self.assertTrue(result['ok'])
        self.assertEqual(result['payload'], [{'id': 1}])

    def test_run_target_marks_invalid_json_as_failure_without_raising(self):
        session = FakeSession([
            (200, b'not-json', {'Content-Type': 'text/plain'})
        ])
        target = smoke.SmokeTarget('POST', '/broken', expect_json=True)

        result = smoke.run_target(session, target)

        self.assertFalse(result['ok'])
        self.assertEqual(result['http_status'], 200)
        self.assertIn('error', result)
        self.assertIn('Invalid JSON', result['error'])


if __name__ == '__main__':
    unittest.main()
