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

    def test_run_target_with_retries_recovers_after_transport_failure(self):
        session = FakeSession([
            (
                0,
                json.dumps({'code': -1, 'msg': 'timed out'}).encode('utf-8'),
                {'Content-Type': 'application/json', 'X-Smoke-Error': 'TimeoutError'},
            ),
            (200, b'<html><body>ok</body></html>', {'Content-Type': 'text/html'}),
        ])
        target = smoke.SmokeTarget('GET', '/stream')

        result = smoke.run_target_with_retries(
            session=session,
            target=target,
            base_url='http://127.0.0.1:18082',
            max_attempts=2,
            retry_interval_ms=0,
        )

        self.assertTrue(result['ok'])
        self.assertEqual(2, result['attempts'])
        self.assertIn('retry_history', result)
        self.assertEqual(0, result['retry_history'][0]['http_status'])

    def test_run_target_with_retries_does_not_retry_non_retryable_failure(self):
        session = FakeSession([
            (200, json.dumps({'code': 500, 'msg': 'invalid request'}).encode('utf-8'), {'Content-Type': 'application/json'}),
        ])
        target = smoke.SmokeTarget('POST', '/broken', expect_json=True)

        result = smoke.run_target_with_retries(
            session=session,
            target=target,
            base_url='http://127.0.0.1:18082',
            max_attempts=3,
            retry_interval_ms=0,
        )

        self.assertFalse(result['ok'])
        self.assertEqual(1, result['attempts'])
        self.assertEqual(1, len(session.calls))

    def test_run_target_with_retries_can_force_retry_on_any_failure(self):
        session = FakeSession([
            (200, json.dumps({'code': 500, 'msg': 'busy'}).encode('utf-8'), {'Content-Type': 'application/json'}),
            (200, json.dumps({'code': 0, 'msg': 'ok'}).encode('utf-8'), {'Content-Type': 'application/json'}),
        ])
        target = smoke.SmokeTarget('POST', '/broken', expect_json=True)

        result = smoke.run_target_with_retries(
            session=session,
            target=target,
            base_url='http://127.0.0.1:18082',
            max_attempts=2,
            retry_interval_ms=0,
            retry_on_any_failure=True,
        )

        self.assertTrue(result['ok'])
        self.assertEqual(2, result['attempts'])
        self.assertEqual(2, len(session.calls))

    def test_build_targets_can_skip_capture_endpoints(self):
        camera_context = {'camera_id': 1, 'rtsp_url': 'rtsp://example/live'}
        full_targets = smoke.build_targets(camera_context, include_capture_endpoints=True)
        light_targets = smoke.build_targets(camera_context, include_capture_endpoints=False)

        full_paths = {target.path for target in full_targets}
        light_paths = {target.path for target in light_targets}
        self.assertIn('/report/push-targets', full_paths)
        self.assertIn('/report/push-targets', light_paths)
        self.assertIn('/camera/takePhoto', full_paths)
        self.assertIn('/testimage/get', full_paths)
        self.assertNotIn('/camera/takePhoto', light_paths)
        self.assertNotIn('/testimage/get', light_paths)


if __name__ == '__main__':
    unittest.main()
