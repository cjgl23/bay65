"""Local-only LTA adapter. Run with python3 server.py; no dependencies needed."""

import json
import re
import secrets
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

HOST, PORT = "127.0.0.1", 8765
TOKEN = secrets.token_urlsafe(32)
INDEX = Path(__file__).with_name("index.html")
LTA_URL = "https://datamall2.mytransport.sg/ltaodataservice/v3/BusArrival"
LOCK = threading.Lock()
STATE = {"key": None, "generation": 0, "cache": {}}


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


LTA_OPENER = build_opener(NoRedirect())


class ArrivalError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


def validate_stop(code):
    if not isinstance(code, str) or not re.fullmatch(r"\d{5}", code):
        raise ArrivalError(400, "Enter a five-digit bus stop code.")
    return code


def request_lta(key, code):
    url = LTA_URL + "?BusStopCode=" + code
    if sys.platform == "darwin":
        # Apple's curl uses the macOS trust store, including managed network CAs.
        # -q disables user curlrc files; the key goes over stdin, not process args.
        try:
            result = subprocess.run(
                ["/usr/bin/curl", "-q", "--silent", "--show-error", "--max-time", "12",
                 "--max-filesize", "1000000", "--proto", "=https", "--max-redirs", "0",
                 "--header", "@-", "--write-out", "\n%{http_code}", "--url", url],
                input=("AccountKey: " + key + "\nAccept: application/json\n").encode(),
                capture_output=True, timeout=15,
            )
        except subprocess.TimeoutExpired:
            raise URLError("LTA request timed out") from None
        if result.returncode:
            raise URLError("LTA HTTPS request failed")
        try:
            body, status_text = result.stdout.rsplit(b"\n", 1)
            status = int(status_text)
        except ValueError:
            raise URLError("Invalid HTTP response") from None
        if status != 200:
            raise HTTPError(url, status, "LTA request failed", {}, None)
        return body
    request = Request(url, headers={"AccountKey": key, "Accept": "application/json"})
    with LTA_OPENER.open(request, timeout=12) as response:
        return response.read(1_000_001)


def fetch_arrivals(key, code):
    """Only send the credential to LTA's fixed HTTPS endpoint; never log it."""
    validate_stop(code)
    try:
        raw = request_lta(key, code)
        if len(raw) > 1_000_000:
            raise ArrivalError(502, "LTA returned an unexpected response. Try again shortly.")
        data = json.loads(raw)
        if not isinstance(data, dict) or not isinstance(data.get("Services", []), list):
            raise ValueError("Unexpected arrival response")
        services = data.get("Services", [])
        if any(not isinstance(service, dict) for service in services):
            raise ValueError("Unexpected service response")
        if data.get("BusStopCode") not in (None, code):
            raise ValueError("Unexpected stop response")
    except HTTPError as error:
        if error.code in (401, 403):
            raise ArrivalError(401, "LTA rejected this key. Check your DataMall API Access Key and its activation status.") from None
        if error.code == 429:
            raise ArrivalError(429, "LTA's request limit was reached. Please wait a minute.") from None
        raise ArrivalError(502, "LTA is temporarily unavailable. We will retry shortly.") from None
    except (URLError, TimeoutError, OSError):
        raise ArrivalError(502, "Cannot reach LTA. Check your internet connection; we will retry shortly.") from None
    except (ValueError, TypeError):
        raise ArrivalError(502, "LTA returned an unexpected response. Try again shortly.") from None
    return {"stopCode": code, "services": services,
            "updatedAt": datetime.now(timezone.utc).isoformat()}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        # Avoid logging requests, bodies, or credentials.
        pass

    def send_payload(self, status, payload, content_type="application/json"):
        body = payload if isinstance(payload, bytes) else json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' blob: data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def check_access(self, api=False):
        expected = f"{HOST}:{self.server.server_port}"
        if self.headers.get("Host") != expected:
            raise ArrivalError(403, "Open the preview using its 127.0.0.1 address.")
        origin = self.headers.get("Origin")
        if origin and origin != "http://" + expected:
            raise ArrivalError(403, "Requests must come from the local preview.")
        if api and not secrets.compare_digest(self.headers.get("X-Launcher-Token", ""), TOKEN):
            raise ArrivalError(403, "Reload the local preview and try again.")

    def do_GET(self):
        try:
            self.check_access()
            route = urlsplit(self.path)
            if route.path in ("/", "/index.html"):
                page = INDEX.read_text().replace("<title>", f'<meta name="family-tv-local" content="{TOKEN}"><title>', 1)
                return self.send_payload(200, page.encode(), "text/html; charset=utf-8")
            if route.path == "/favicon.ico":
                return self.send_payload(204, b"")
            self.check_access(api=True)
            if route.path == "/api/status":
                with LOCK:
                    connected = STATE["key"] is not None
                return self.send_payload(200, {"connected": connected})
            if route.path == "/api/arrivals":
                code = validate_stop(parse_qs(route.query).get("stop", [""])[0])
                with LOCK:
                    key, generation = STATE["key"], STATE["generation"]
                    cached = STATE["cache"].get(code)
                if not key:
                    raise ArrivalError(409, "Connect your LTA key in Settings first.")
                if cached and time.monotonic() - cached[0] < 20:
                    return self.send_payload(200, cached[1])
                result = fetch_arrivals(key, code)
                with LOCK:
                    if generation != STATE["generation"]:
                        raise ArrivalError(409, "The LTA connection changed. Refresh and try again.")
                    STATE["cache"][code] = (time.monotonic(), result)
                return self.send_payload(200, result)
            raise ArrivalError(404, "Not found.")
        except ArrivalError as error:
            self.send_payload(error.status, {"error": error.message})

    def do_POST(self):
        try:
            self.check_access(api=True)
            if self.path == "/api/disconnect":
                with LOCK:
                    STATE.update(key=None, generation=STATE["generation"] + 1, cache={})
                return self.send_payload(200, {"connected": False})
            if self.path != "/api/connect":
                raise ArrivalError(404, "Not found.")
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 4096:
                    raise ValueError()
                body = json.loads(self.rfile.read(length))
                if not isinstance(body, dict):
                    raise ValueError()
                key, code = body.get("key", ""), body.get("stopCode", "")
                if not isinstance(key, str):
                    raise ValueError()
                key = key.strip()
                if not 8 <= len(key) <= 512 or any(not 33 <= ord(c) <= 126 for c in key):
                    raise ValueError()
            except (ValueError, TypeError, UnicodeError):
                raise ArrivalError(400, "Enter your DataMall API Access Key.") from None
            # Authenticate with one real request before replacing any working key.
            result = fetch_arrivals(key, validate_stop(code))
            with LOCK:
                STATE.update(key=key, generation=STATE["generation"] + 1,
                             cache={code: (time.monotonic(), result)})
            return self.send_payload(200, {"connected": True, "arrival": result})
        except ArrivalError as error:
            self.send_payload(error.status, {"error": error.message})


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"BAY65 live preview: http://{HOST}:{PORT}", flush=True)
    print("Enter the LTA key in Settings. It stays in server memory until disconnected or stopped.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        with LOCK:
            STATE.update(key=None, cache={})
        server.server_close()
