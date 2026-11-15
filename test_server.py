"""Local HTTP integration checks; all LTA responses are mocked, no real key needed."""

import io
import json
import threading
import unittest
from http.server import ThreadingHTTPServer
from unittest.mock import patch
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import server as app


class LocalApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer((app.HOST, 0), app.Handler)
        cls.base = f"http://{app.HOST}:{cls.server.server_port}"
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def setUp(self):
        with app.LOCK:
            app.STATE.update(key=None, generation=0, cache={})

    def request(self, path, body=None, headers=None):
        request_headers = {"X-Launcher-Token": app.TOKEN, "Content-Type": "application/json"}
        request_headers.update(headers or {})
        request = Request(self.base + path, headers=request_headers,
                          data=None if body is None else json.dumps(body).encode())
        try:
            response = urlopen(request, timeout=3)
        except HTTPError as error:
            response = error
        with response:
            return response.status, response.read()

    def test_page_has_session_token_but_no_key(self):
        with app.LOCK:
            app.STATE["key"] = "dummy-key-not-a-real-credential"
        status, body = self.request("/")
        self.assertEqual(status, 200)
        self.assertIn(app.TOKEN.encode(), body)
        self.assertNotIn(app.STATE["key"].encode(), body)
        _, status_body = self.request("/api/status")
        self.assertEqual(json.loads(status_body), {"connected": True})

    def test_protects_api_and_host(self):
        for headers in ({"X-Launcher-Token": "wrong"},
                        {"Origin": "https://unrelated.example"},
                        {"Host": "unrelated.example"}):
            status, _ = self.request("/api/status", headers=headers)
            self.assertEqual(status, 403)

    @patch.object(app, "fetch_arrivals")
    def test_connect_cache_and_disconnect(self, fetch):
        arrival = {"stopCode": "00001", "services": [], "updatedAt": "2026-11-15T12:00:00+00:00"}
        fetch.return_value = arrival
        key = "dummy-key-not-a-real-credential"
        status, body = self.request("/api/connect", {"key": key, "stopCode": "00001"})
        self.assertEqual(status, 200)
        self.assertTrue(json.loads(body)["connected"])
        self.assertNotIn(key.encode(), body)
        fetch.assert_called_once_with(key, "00001")
        status, body = self.request("/api/arrivals?stop=00001")
        self.assertEqual(status, 200)
        self.assertEqual(json.loads(body), arrival)
        self.assertEqual(fetch.call_count, 1)
        status, _ = self.request("/api/disconnect", {})
        self.assertEqual(status, 200)
        self.assertIsNone(app.STATE["key"])
        self.assertFalse(app.STATE["cache"])
        status, _ = self.request("/api/arrivals?stop=00001")
        self.assertEqual(status, 409)

    @patch.object(app, "fetch_arrivals", side_effect=app.ArrivalError(401, "Key rejected"))
    def test_failed_key_replacement_preserves_working_connection(self, _fetch):
        with app.LOCK:
            app.STATE["key"] = "previous-working-test-key"
        status, body = self.request("/api/connect", {"key": "new-invalid-test-key", "stopCode": "00001"})
        self.assertEqual(status, 401)
        self.assertEqual(json.loads(body)["error"], "Key rejected")
        self.assertEqual(app.STATE["key"], "previous-working-test-key")

    @patch.object(app, "fetch_arrivals")
    def test_rejects_invalid_key_or_stop_without_contacting_lta(self, fetch):
        status, _ = self.request("/api/connect", {"key": "key-with\nnewline"})
        self.assertEqual(status, 400)
        status, _ = self.request("/api/connect", {"key": "valid-test-key", "stopCode": "../other"})
        self.assertEqual(status, 400)
        fetch.assert_not_called()


class LtaAdapterTests(unittest.TestCase):
    @patch.object(app.sys, "platform", "linux")
    @patch.object(app.LTA_OPENER, "open")
    def test_fixed_https_request_and_no_estimates(self, open_request):
        open_request.return_value = io.BytesIO(b'{"BusStopCode":"00001","Services":[]}')
        result = app.fetch_arrivals("test-key-not-real", "00001")
        request = open_request.call_args.args[0]
        self.assertEqual(request.full_url, app.LTA_URL + "?BusStopCode=00001")
        self.assertNotIn("test-key", request.full_url)
        self.assertEqual(request.get_header("Accountkey"), "test-key-not-real")
        self.assertEqual(result["services"], [])
        self.assertIsNone(app.NoRedirect().redirect_request(None, None, None, None, None, None))

    @patch.object(app, "request_lta")
    def test_authentication_failure_is_sanitised(self, open_request):
        open_request.side_effect = HTTPError(app.LTA_URL, 403, "Sensitive upstream details", {}, None)
        with self.assertRaises(app.ArrivalError) as caught:
            app.fetch_arrivals("test-key-not-real", "00001")
        self.assertEqual(caught.exception.status, 401)
        self.assertNotIn("Sensitive", caught.exception.message)
        self.assertNotIn("test-key", caught.exception.message)

    @patch.object(app.sys, "platform", "darwin")
    @patch.object(app.subprocess, "run")
    def test_macos_uses_verified_https_without_exposing_key_in_arguments(self, run):
        run.return_value = app.subprocess.CompletedProcess([], 0, b'{"Services":[]}\n200', b'')
        result = app.fetch_arrivals("dummy-private-key", "00001")
        args = run.call_args.args[0]
        self.assertEqual(args[:2], ["/usr/bin/curl", "-q"])
        self.assertNotIn("dummy-private-key", " ".join(args))
        self.assertIn(b"AccountKey: dummy-private-key", run.call_args.kwargs["input"])
        self.assertNotIn("--insecure", args)
        self.assertNotIn("-k", args)
        self.assertNotIn("--location", args)
        self.assertEqual(result["services"], [])


if __name__ == "__main__":
    unittest.main()
