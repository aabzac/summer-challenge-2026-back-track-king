"""
Tiny HTTP server that receives POST requests from the Tampermonkey scripts.
Stderr logs go to logs/. Boss replays go to replays/cg/boss/<strategy>/<build>/
only when their first-turn MESSAGE matches a verified local artifact; otherwise
to boss/unknown/. Raw TestSession responses go to boss/raw/unknown/.

Usage
-----
    python tools/save_server.py

Then in a userscript set:
    const SAVE_SERVER_URL = 'http://localhost:7777';

Use kind="boss" or "boss_raw"; legacy "bot"/"bot_raw" requests remain supported.

Notes
-----
* Run this from the project root (next to the logs/ folder).
* The server binds to localhost only — not reachable from the network.
* Stop with Ctrl-C.
"""

import json
import os
import socket
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from replay_capture import save_capture


PROJECT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
PORT     = 7777
MAX_BODY_BYTES = 8 * 1024 * 1024


class ExclusiveHTTPServer(ThreadingHTTPServer):
    """Handle concurrent requests, but never share the port with an older server."""

    allow_reuse_address = False
    daemon_threads = True

    def server_bind(self):
        if hasattr(socket, 'SO_EXCLUSIVEADDRUSE'):
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        super().server_bind()


class Handler(BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path == '/health':
            self._send_json(200, {'ok': True, 'service': 'btk-save-server', 'protocolVersion': 2,
                                  'bossRoot': 'replays/cg/boss', 'buildMarkers': 'BTK_BUILD_V1'})
        else:
            self._send_json(404, {'ok': False, 'error': 'Unknown endpoint'})

    def do_OPTIONS(self):
        """Handle CORS pre-flight (browser sends this before the real POST)."""
        self._send_cors(200)

    def do_POST(self):
        try:
            length = int(self.headers.get('Content-Length', 0))
            if length <= 0 or length > MAX_BODY_BYTES:
                raise ValueError('Missing or oversized capture body')
            body   = self.rfile.read(length)
            data   = json.loads(body)
            result = save_capture(data, PROJECT_DIR)
            self._send_json(200, result)
            print(f"[save_server] {'Saved' if result['created'] else 'Already captured'} -> {result['path']}")
        except (ValueError, TypeError, KeyError):
            self._send_json(400, {'ok': False, 'error': 'Invalid or conflicting capture; existing files preserved'})
        except Exception as exc:
            print(f"[save_server] ERROR: {type(exc).__name__}")
            self._send_json(500, {'ok': False, 'error': 'Local capture failed; existing files preserved'})

    def _send_json(self, code, data):
        payload = json.dumps(data).encode('utf-8')
        self._headers(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _send_cors(self, code: int):
        self._headers(code)
        self.end_headers()

    def _headers(self, code):
        self.send_response(code)
        self.send_header('Access-Control-Allow-Origin',  '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')

    def log_message(self, fmt, *args):
        pass   # suppress default HTTP log noise


def main():
    try:
        server = ExclusiveHTTPServer(('localhost', PORT), Handler)
    except OSError as exc:
        raise SystemExit(
            f"[save_server] Cannot bind port {PORT}; another save_server is probably running"
        ) from exc

    print(f"[save_server] Listening on http://localhost:{PORT}")
    print(f"[save_server] Project: {PROJECT_DIR}")
    print("[save_server] Boss replays: replays/cg/boss/<strategy>/<build> or boss/unknown")
    print("[save_server] Raw fallbacks: replays/cg/boss/raw/unknown; stderr: logs/")
    print("[save_server] Press Ctrl-C to stop.\n")
    server.serve_forever()


if __name__ == '__main__':
    main()
