"""JSON-lines transport to the exact Java engine, without Python game logic."""
from __future__ import annotations

import json
import subprocess


class Bridge:
    def __init__(self, command):
        self.process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        text=True, encoding="utf-8", bufsize=1)
        self.calls = 0

    def request(self, **message):
        if self.process.poll() is not None:
            raise RuntimeError(f"Java bridge exited with {self.process.returncode}")
        self.process.stdin.write(json.dumps(message, separators=(",", ":")) + "\n")
        self.process.stdin.flush()
        line = self.process.stdout.readline()
        if not line:
            raise RuntimeError("Java bridge closed stdout")
        reply = json.loads(line)
        self.calls += 1
        if "error" in reply:
            raise RuntimeError(f"Java bridge: {reply['error']}")
        return reply

    def close(self):
        if self.process.poll() is None:
            self.process.stdin.close()
            try:
                self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.process.terminate()
                self.process.wait(timeout=10)

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
