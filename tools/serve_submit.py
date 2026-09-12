"""Serve build/submit/Player.java on http://localhost:7788/Player.java with CORS.

Used to push the merged submission into the CodinGame IDE from browser automation:
the IDE page fetches the file and pastes it into the Monaco editor (see tools/cg_paste.js).
Run from the project root:  python tools/serve_submit.py
"""
import http.server
import pathlib
import sys

PORT = 7788
FILE = pathlib.Path("build/submit/Player.java")


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if not self.path.startswith("/Player.java"):
            self.send_response(404)
            self.end_headers()
            return
        data = FILE.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self._cors()
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def _cors(self):
        # Chromium Private Network Access: a public https page fetching localhost needs this preflight header.
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Private-Network", "true")

    def log_message(self, fmt, *args):
        sys.stderr.write("[serve_submit] " + fmt % args + "\n")


if __name__ == "__main__":
    if not FILE.exists():
        sys.exit(f"{FILE} not found; run gradlew submit first")
    print(f"serving {FILE} ({FILE.stat().st_size} bytes) at http://localhost:{PORT}/Player.java")
    http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
