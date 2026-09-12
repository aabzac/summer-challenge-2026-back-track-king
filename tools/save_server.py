"""
Tiny HTTP server that receives a POST request from the Tampermonkey script
and saves the stderr log to the logs/ directory of the project.

Usage
-----
    python tools/save_server.py

Then in codingame_stderr_export.user.js set:
    const SAVE_SERVER_URL = 'http://localhost:7777';

The file is saved as:
    logs/stderr_YYYYMMDD_HHMMSS.txt

Notes
-----
* Run this from the project root (next to the logs/ folder).
* The server binds to localhost only — not reachable from the network.
* Stop with Ctrl-C.
"""

import json
import os
from datetime import datetime
from http.server import BaseHTTPRequestHandler, HTTPServer


SAVE_DIR = os.path.join(os.path.dirname(__file__), '..', 'logs')
PORT     = 7777


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
            filename = data.get('filename') or f"stderr_{datetime.now():%Y-%m-%d_%H-%M-%S}.txt"
            # Sanitise filename
            filename = os.path.basename(filename)

            os.makedirs(SAVE_DIR, exist_ok=True)
            path = os.path.join(SAVE_DIR, filename)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)

            lines = content.count('\n')
            print(f"[save_server] Saved {lines} lines → {path}")
            self._send_cors(200)

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
    os.makedirs(SAVE_DIR, exist_ok=True)
    print(f"[save_server] Listening on http://localhost:{PORT}")
    print(f"[save_server] Files will be saved to: {os.path.abspath(SAVE_DIR)}")
    print("[save_server] Press Ctrl-C to stop.\n")
    HTTPServer(('localhost', PORT), Handler).serve_forever()


if __name__ == '__main__':
    main()
