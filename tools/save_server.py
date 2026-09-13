"""
Tiny HTTP server that receives POST requests from the Tampermonkey scripts.
Stderr logs go to logs/, captured boss replays to replays/cg/bot/, and raw
TestSession/play fallbacks to replays/cg/bot/raw/.

Usage
-----
    python tools/save_server.py

Then in a userscript set:
    const SAVE_SERVER_URL = 'http://localhost:7777';

The request may set kind="bot" or kind="bot_raw" to select the boss replay directory.

Notes
-----
* Run this from the project root (next to the logs/ folder).
* The server binds to localhost only — not reachable from the network.
* Stop with Ctrl-C.
"""

import json
import os
import socket
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


PROJECT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SAVE_DIRS = {
    'stderr': os.path.join(PROJECT_DIR, 'logs'),
    'bot': os.path.join(PROJECT_DIR, 'replays', 'cg', 'bot'),
    'bot_raw': os.path.join(PROJECT_DIR, 'replays', 'cg', 'bot', 'raw'),
}
PORT     = 7777


class ExclusiveHTTPServer(ThreadingHTTPServer):
    """Handle concurrent requests, but never share the port with an older server."""

    allow_reuse_address = False
    daemon_threads = True

    def server_bind(self):
        if hasattr(socket, 'SO_EXCLUSIVEADDRUSE'):
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        super().server_bind()


class Handler(BaseHTTPRequestHandler):

    def do_OPTIONS(self):
        """Handle CORS pre-flight (browser sends this before the real POST)."""
        self._send_cors(200)

    def do_POST(self):
        try:
            length = int(self.headers.get('Content-Length', 0))
            body   = self.rfile.read(length)
            data   = json.loads(body)

            content  = data.get('content', '')
            kind     = data.get('kind', 'stderr')
            if kind not in SAVE_DIRS:
                raise ValueError(f"unsupported save kind: {kind}")
            filename = data.get('filename') or f"stderr_{datetime.now():%Y-%m-%d_%H-%M-%S}.txt"
            # Sanitise filename
            filename = os.path.basename(filename)

            save_dir = SAVE_DIRS[kind]
            os.makedirs(save_dir, exist_ok=True)
            path = os.path.join(save_dir, filename)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)

            self._send_cors(200)
            lines = content.count('\n')
            print(f"[save_server] Saved {lines} lines -> {path}")

        except Exception as exc:
            print(f"[save_server] ERROR: {exc}")
            self._send_cors(500)

    def _send_cors(self, code: int):
        self.send_response(code)
        self.send_header('Access-Control-Allow-Origin',  '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def log_message(self, fmt, *args):
        pass   # suppress default HTTP log noise


def main():
    for save_dir in SAVE_DIRS.values():
        os.makedirs(save_dir, exist_ok=True)
    try:
        server = ExclusiveHTTPServer(('localhost', PORT), Handler)
    except OSError as exc:
        raise SystemExit(
            f"[save_server] Cannot bind port {PORT}; another save_server is probably running"
        ) from exc

    print(f"[save_server] Listening on http://localhost:{PORT}")
    print(f"[save_server] Stderr logs: {SAVE_DIRS['stderr']}")
    print(f"[save_server] Boss replays: {SAVE_DIRS['bot']}")
    print(f"[save_server] Boss raw fallbacks: {SAVE_DIRS['bot_raw']}")
    print("[save_server] Press Ctrl-C to stop.\n")
    server.serve_forever()


if __name__ == '__main__':
    main()
