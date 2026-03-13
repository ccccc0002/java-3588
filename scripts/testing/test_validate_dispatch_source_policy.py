import importlib.util
import json
import pathlib
import sys
import unittest


MODULE_PATH = pathlib.Path(__file__).resolve().parent / "validate_dispatch_source_policy.py"
SPEC = importlib.util.spec_from_file_location("validate_dispatch_source_policy", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Unable to load module from {MODULE_PATH}")
validate_dispatch_source_policy = importlib.util.module_from_spec(SPEC)
sys.modules["validate_dispatch_source_policy"] = validate_dispatch_source_policy
SPEC.loader.exec_module(validate_dispatch_source_policy)


class FakeHttpResponse:
    def __init__(self, status: int, body: bytes):
        self.status = status
        self._body = body

    def read(self):
        return self._body

    def getcode(self):
        return self.status

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False


class ValidateDispatchSourcePolicyTests(unittest.TestCase):
    def test_build_dispatch_payload_omits_frame_source_by_default(self):
        payload = validate_dispatch_source_policy.build_dispatch_payload(
            camera_id=1,
            model_id=2,
            algorithm_id=3,
            persist_report=0,
            trace_id="trace-1",
        )
        self.assertEqual("trace-1", payload["trace_id"])
        self.assertEqual(1, payload["camera_id"])
        self.assertEqual(2, payload["model_id"])
        self.assertEqual(3, payload["algorithm_id"])
        self.assertEqual(0, payload["persist_report"])
        self.assertIn("timestamp_ms", payload["frame"])
        self.assertNotIn("source", payload["frame"])

    def test_verify_dispatch_source_policy_happy_path(self):
        def fake_open(req, timeout=30):
            payload = json.loads(req.data.decode("utf-8"))
            camera_id = int(payload.get("camera_id", 0))
            if camera_id == 1:
                body = {
                    "code": 0,
                    "data": {
                        "request": {
                            "frame": {
                                "source": "rtsp://demo/camera-1",
                            }
                        }
                    },
                }
                return FakeHttpResponse(200, json.dumps(body).encode("utf-8"))
            body = {
                "code": 500,
                "data": {
                    "dead_letter": {
                        "request_payload": {
                            "frame": {}
                        }
                    }
                },
            }
            return FakeHttpResponse(500, json.dumps(body).encode("utf-8"))

        summary = validate_dispatch_source_policy.verify_dispatch_source_policy(
            base_url="http://127.0.0.1:18082",
            camera_id=1,
            model_id=1,
            algorithm_id=1,
            invalid_camera_id=999999,
            timeout_sec=30,
            http_open=fake_open,
        )

        self.assertEqual("passed", summary["status"])
        self.assertEqual("rtsp://demo/camera-1", summary["valid_case"]["request_frame_source"])
        self.assertEqual("", summary["invalid_case"]["dead_letter_frame_source"])

    def test_verify_dispatch_source_policy_fails_when_valid_path_uses_test_frame(self):
        def fake_open(req, timeout=30):
            payload = json.loads(req.data.decode("utf-8"))
            camera_id = int(payload.get("camera_id", 0))
            if camera_id == 1:
                body = {
                    "code": 0,
                    "data": {
                        "request": {
                            "frame": {
                                "source": "test://frame",
                            }
                        }
                    },
                }
                return FakeHttpResponse(200, json.dumps(body).encode("utf-8"))
            body = {"code": 500, "data": {}}
            return FakeHttpResponse(500, json.dumps(body).encode("utf-8"))

        with self.assertRaises(RuntimeError):
            validate_dispatch_source_policy.verify_dispatch_source_policy(
                base_url="http://127.0.0.1:18082",
                camera_id=1,
                model_id=1,
                algorithm_id=1,
                invalid_camera_id=999999,
                timeout_sec=30,
                http_open=fake_open,
            )


if __name__ == "__main__":
    unittest.main()
