"""Minimal authenticated CodinGame transport and one-time HAR session import.

No browser dependency, redirects, automatic retries, source upload CLI, or gameplay
loop. Credentials are imported only into ignored build/cg-boss/private/session.json. Inspect
commands are local and print metadata only. A caller explicitly supplies each service
operation; an uncertain TestSession/play failure must not be retried automatically.
"""

from __future__ import annotations

import argparse
import base64
import copy
from dataclasses import dataclass, field
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
import hashlib
import http.client
import json
import math
import os
from pathlib import Path
import re
import tempfile
from typing import Any
import urllib.error
import urllib.parse
import urllib.request


ORIGIN = "https://www.codingame.com"
PROJECT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SESSION_PATH = PROJECT_ROOT / "build" / "cg-boss" / "private" / "session.json"
PLAY_SERVICE = "TestSession/play"
MAX_HAR_BYTES = 64 * 1024 * 1024
MAX_SESSION_BYTES = 2 * 1024 * 1024
MAX_REQUEST_BYTES = 4 * 1024 * 1024
MAX_RESPONSE_BYTES = 32 * 1024 * 1024
MAX_ERROR_BYTES = 64 * 1024
SERVICE_PATTERN = re.compile(r"[A-Za-z][A-Za-z0-9]*/[A-Za-z][A-Za-z0-9]*\Z")
COOKIE_NAME = re.compile(r"[!#$%&'*+.^_`|~0-9A-Za-z-]+\Z")
SAFE_CAPTURE_HEADERS = frozenset({
    "user-agent", "accept", "accept-language", "x-requested-with",
    "x-csrf-token", "x-xsrf-token",
})


class SessionError(ValueError):
    """A constant, safe-to-display message; never includes source or credentials."""


class ApiError(RuntimeError):
    def __init__(self, message: str, *, status: int | None = None, uncertain: bool = False,
                 response_bytes: bytes = b"", body_truncated: bool = False,
                 retry_after_seconds: int | None = None):
        super().__init__(message)
        self.status = status
        self.uncertain = uncertain
        # This body is intentionally absent from exception args, str and repr.
        # Callers may persist it only to their ignored private diagnostics directory.
        self._response_bytes = response_bytes[:MAX_ERROR_BYTES]
        self.body_truncated = body_truncated or len(response_bytes) > MAX_ERROR_BYTES
        self.error_code = _safe_error_code(self._response_bytes)
        self.retry_after_seconds = (retry_after_seconds if type(retry_after_seconds) is int
                                    and retry_after_seconds >= 0 else None)

    @property
    def response_bytes(self) -> bytes:
        return self._response_bytes


def _safe_error_code(raw: bytes) -> int | str | None:
    try:
        parsed = json.loads(raw.decode("utf-8"))
    except (ValueError, UnicodeError, RecursionError):
        return None
    if not isinstance(parsed, dict):
        return None
    value = parsed.get("id")
    if type(value) is int:
        return value
    if isinstance(value, str) and re.fullmatch(r"[A-Z][A-Z0-9_]{0,63}", value):
        return value
    return None


def _parse_retry_after(value: Any, *, received_at: datetime | None = None) -> int | None:
    """Return only a delay, never the raw header; this does not schedule any retry."""
    if not isinstance(value, str) or len(value) > 128 or any(ord(c) < 32 or ord(c) > 126 for c in value):
        return None
    value = value.strip()
    if re.fullmatch(r"[0-9]{1,10}", value):
        return int(value)
    try:
        deadline = parsedate_to_datetime(value)
        if deadline.tzinfo is None:
            # Obsolete HTTP asctime dates also mean UTC.
            deadline = deadline.replace(tzinfo=timezone.utc)
        received_at = received_at or datetime.now(timezone.utc)
        return max(0, math.ceil((deadline - received_at).total_seconds()))
    except (TypeError, ValueError, OverflowError):
        return None


@dataclass(repr=False)
class Session:
    play_template: list = field(repr=False)
    cookie: str = field(repr=False)
    headers: dict[str, str] = field(default_factory=dict, repr=False)
    source_backup: Path | None = None
    source_sha256: str = ""
    session_path: Path | None = None
    origin: str = ORIGIN

    def __repr__(self):
        return "Session(origin='https://www.codingame.com', credentials=<redacted>)"

    def public_metadata(self) -> dict:
        config = self.play_template[1]
        multi = config["multi"]
        return {
            "origin": ORIGIN,
            "session_loaded": True,
            "source_backup_present": self.source_backup is not None and self.source_backup.is_file(),
            "source_sha256": self.source_sha256,
            "language": config["programmingLanguageId"],
            "agent_ids": list(multi["agentsIds"]),
            "play_type": list(multi["playType"]),
            "solo_league": multi["isSoloLeague"],
            "game_options_present": multi.get("gameOptions") is not None,
        }


def _safe_header(value: Any, name: str) -> str:
    if not isinstance(value, str) or not value or len(value) > 65536:
        raise SessionError(f"Invalid {name} value")
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise SessionError(f"Invalid {name} characters")
    # urllib sends HTTP header values as Latin-1. Fail before a request is attempted.
    try:
        value.encode("latin-1")
    except UnicodeError:
        raise SessionError(f"Invalid {name} encoding") from None
    return value


def _cookie(value: Any) -> str:
    value = _safe_header(value, "cookie")
    for part in value.split(";"):
        name, separator, content = part.strip().partition("=")
        if not separator or not COOKIE_NAME.fullmatch(name):
            raise SessionError("Invalid cookie structure")
        if content.strip().lower() in {"<redacted>", "[redacted]", "redacted", "***", "<removed>"}:
            raise SessionError("HAR/session credentials are redacted")
    return value


def _template(value: Any, *, need_code: bool) -> list:
    if not isinstance(value, list) or len(value) != 2:
        raise SessionError("Invalid TestSession/play argument list")
    handle, config = value
    if not isinstance(handle, str) or not handle.strip() or len(handle) > 4096:
        raise SessionError("Invalid test session handle")
    if not isinstance(config, dict) or config.get("programmingLanguageId") != "Java":
        raise SessionError("Captured play request must use Java")
    code = config.get("code")
    if not isinstance(code, str) or need_code and not code.strip():
        raise SessionError("Play source code is missing")
    if not need_code and code:
        raise SessionError("Session JSON must not contain the captured source code")
    multi = config.get("multi")
    if not isinstance(multi, dict):
        raise SessionError("Invalid multiplayer play configuration")
    agents = multi.get("agentsIds")
    if (not isinstance(agents, list) or len(agents) != 2
            or any(type(agent) is not int for agent in agents)
            or agents.count(-1) != 1 or sum(agent == -2 or agent > 0 for agent in agents) != 1):
        raise SessionError("Captured request must contain IDE code and one boss agent")
    expected_types = ["IDE_CODE" if agent == -1 else "BOSS" for agent in agents]
    if multi.get("playType") != expected_types or multi.get("isSoloLeague") is not False:
        raise SessionError("Captured request is not a multiplayer IDE-versus-boss request")
    if "gameOptions" not in multi or multi["gameOptions"] is not None and not isinstance(multi["gameOptions"], str):
        raise SessionError("Invalid captured game options")
    return copy.deepcopy(value)


def _json_file(path: Path, limit: int, label: str) -> Any:
    try:
        with Path(path).open("rb") as stream:
            raw = stream.read(limit + 1)
        if len(raw) > limit:
            raise SessionError(f"{label} exceeds the size limit")
        return json.loads(raw.decode("utf-8-sig"))
    except SessionError:
        raise
    except (OSError, ValueError, UnicodeError):
        raise SessionError(f"Cannot read valid {label} JSON") from None


def _captured_headers(request: dict) -> tuple[str, dict[str, str]]:
    raw_headers = request.get("headers", [])
    if not isinstance(raw_headers, list):
        raise SessionError("Invalid HAR request headers")
    headers = {}
    cookies = []
    for item in raw_headers:
        if not isinstance(item, dict) or not isinstance(item.get("name"), str):
            raise SessionError("Invalid HAR header entry")
        name = item["name"].lower()
        if name == "cookie":
            cookies.append(_cookie(item.get("value")))
        elif name in SAFE_CAPTURE_HEADERS:
            headers[name] = _safe_header(item.get("value"), "request header")
    if len(cookies) > 1:
        raise SessionError("Multiple Cookie headers require an unambiguous HAR capture")
    if cookies:
        return cookies[0], headers
    raw_cookies = request.get("cookies", [])
    if not isinstance(raw_cookies, list) or not raw_cookies:
        raise SessionError("HAR has no Cookie header or request cookies; export with credentials")
    parts = []
    for item in raw_cookies:
        if not isinstance(item, dict) or not isinstance(item.get("name"), str) or not isinstance(item.get("value"), str):
            raise SessionError("Invalid HAR cookie entry")
        if ";" in item["value"]:
            raise SessionError("Invalid HAR cookie value")
        parts.append(item["name"] + "=" + item["value"])
    return _cookie("; ".join(parts)), headers


def _private_write(path: Path, payload: bytes) -> None:
    """Atomic write in the already validated ignored directory, mode 0600 on POSIX."""
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        descriptor, temporary = tempfile.mkstemp(prefix=".session-", suffix=".tmp", dir=path.parent)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
        temporary = None
    finally:
        if temporary is not None:
            try:
                os.unlink(temporary)
            except OSError:
                pass


def import_har(har_path: Path, out_path: Path = DEFAULT_SESSION_PATH, *,
               entry_index: int | None = None, backup_path: Path | None = None,
               project_root: Path = PROJECT_ROOT) -> Session:
    """Import one captured boss request; backup actual source before writing credentials.

    entry_index refers to its zero-based index in log.entries. A HAR containing more
    than one matching play request is rejected unless this explicit index is supplied.
    No captured requests are replayed here.
    """
    secret_root = (Path(project_root) / "build" / "cg-boss" / "private").resolve()
    out_path = Path(out_path).resolve()
    if out_path != secret_root / "session.json":
        raise SessionError("Credentials must be saved as ignored build/cg-boss/private/session.json")
    backup_path = Path(backup_path).resolve() if backup_path is not None else secret_root / "ide-source-backup.java"
    if not backup_path.is_relative_to(secret_root) or backup_path.suffix.lower() != ".java":
        raise SessionError("Source backup must be a Java file inside build/cg-boss/private")
    doc = _json_file(Path(har_path), MAX_HAR_BYTES, "HAR")
    entries = doc.get("log", {}).get("entries") if isinstance(doc, dict) and isinstance(doc.get("log"), dict) else None
    if not isinstance(entries, list):
        raise SessionError("HAR has no request entries")
    matching = []
    for index, entry in enumerate(entries):
        request = entry.get("request") if isinstance(entry, dict) else None
        if not isinstance(request, dict):
            continue
        if request.get("method") == "POST" and request.get("url") == ORIGIN + "/services/" + PLAY_SERVICE:
            matching.append(index)
    if entry_index is None:
        if len(matching) != 1:
            raise SessionError("HAR must contain exactly one boss play request, or specify --entry-index")
        entry_index = matching[0]
    if entry_index not in matching:
        raise SessionError("Selected HAR entry is not a same-origin TestSession/play request")
    request = entries[entry_index]["request"]
    post = request.get("postData")
    if not isinstance(post, dict) or not isinstance(post.get("text"), str):
        raise SessionError("Captured play request has no JSON body")
    try:
        text = post["text"]
        if post.get("encoding") == "base64":
            text = base64.b64decode(text, validate=True).decode("utf-8")
        template = _template(json.loads(text), need_code=True)
        source = template[1]["code"].encode("utf-8")
    except (ValueError, UnicodeError):
        raise SessionError("Invalid captured Java boss request body") from None
    if len(source) > MAX_REQUEST_BYTES:
        raise SessionError("Captured Java source exceeds the size limit")
    # Preserve the source before any session-secret mutation, and never replace a
    # different existing backup. A caller may explicitly choose a new backup name.
    try:
        if backup_path.exists():
            if backup_path.read_bytes() != source:
                raise SessionError("A different source backup already exists; choose another --backup path")
        else:
            backup_path.parent.mkdir(parents=True, exist_ok=True)
            with backup_path.open("xb") as stream:
                stream.write(source)
                stream.flush()
                os.fsync(stream.fileno())
        cookie, headers = _captured_headers(request)
        template[1]["code"] = ""
        record = {
            "schema": 1, "origin": ORIGIN, "cookie": cookie, "headers": headers,
            "play_template": template,
            "source_backup": str(backup_path.relative_to(secret_root)),
            "source_sha256": hashlib.sha256(source).hexdigest(),
        }
        _private_write(out_path, (json.dumps(record, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))
    except SessionError:
        raise
    except OSError:
        raise SessionError("Could not preserve source and save the local session") from None
    return load_session(out_path)


def load_session(path: Path = DEFAULT_SESSION_PATH) -> Session:
    path = Path(path).resolve()
    record = _json_file(path, MAX_SESSION_BYTES, "session")
    if not isinstance(record, dict) or record.get("schema") != 1 or record.get("origin") != ORIGIN:
        raise SessionError("Unsupported local session schema or origin")
    template = _template(record.get("play_template"), need_code=False)
    cookie = _cookie(record.get("cookie"))
    headers = record.get("headers", {})
    if not isinstance(headers, dict) or any(key not in SAFE_CAPTURE_HEADERS for key in headers):
        raise SessionError("Unsupported session header")
    headers = {key: _safe_header(value, "request header") for key, value in headers.items()}
    backup_name = record.get("source_backup")
    digest = record.get("source_sha256")
    if not isinstance(backup_name, str) or not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
        raise SessionError("Invalid source backup metadata")
    backup = (path.parent / backup_name).resolve()
    if not backup.is_relative_to(path.parent) or backup.suffix.lower() != ".java":
        raise SessionError("Source backup escapes the session directory")
    try:
        if hashlib.sha256(backup.read_bytes()).hexdigest() != digest:
            raise SessionError("Source backup hash does not match the imported source")
    except OSError:
        raise SessionError("Source backup is unavailable") from None
    return Session(template, cookie, headers, backup, digest, path)


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file_pointer, code, message, headers, new_url):
        return None


def call_service_text(session: Session, service: str, args: list, timeout: float = 30) -> str:
    """One explicit POST to the fixed origin. Never log or automatically retry.

    Returns exact UTF-8 response text, without JSON rewriting. ApiError.uncertain means a play request might have created
    a game; callers must preserve the attempt and must not silently issue it again.
    """
    if not isinstance(session, Session) or session.origin != ORIGIN:
        raise ApiError("Invalid session origin")
    if not isinstance(service, str) or not SERVICE_PATTERN.fullmatch(service):
        raise ApiError("Invalid service name")
    if not isinstance(args, list):
        raise ApiError("Service arguments must be a JSON array")
    if isinstance(timeout, bool) or not isinstance(timeout, (int, float)) or not math.isfinite(timeout) or timeout <= 0:
        raise ApiError("Request timeout must be a positive finite number")
    try:
        cookie = _cookie(session.cookie)
        if service == PLAY_SERVICE:
            _template(args, need_code=True)
        if any(key not in SAFE_CAPTURE_HEADERS for key in session.headers):
            raise SessionError("Unsupported session header")
        headers = {key: _safe_header(value, "request header") for key, value in session.headers.items()}
        payload = json.dumps(args, ensure_ascii=False, separators=(",", ":"), allow_nan=False).encode("utf-8")
    except (SessionError, ValueError, TypeError, UnicodeError):
        raise ApiError("Invalid local session or request arguments") from None
    if len(payload) > MAX_REQUEST_BYTES:
        raise ApiError("Request exceeds the size limit")
    headers.update({"Content-Type": "application/json; charset=UTF-8", "Accept": "application/json",
                    "Cookie": cookie, "Origin": ORIGIN, "Referer": ORIGIN + "/"})
    request = urllib.request.Request(ORIGIN + "/services/" + service, data=payload, headers=headers, method="POST")
    opener = urllib.request.build_opener(_NoRedirect())
    uncertain = service == PLAY_SERVICE
    try:
        with opener.open(request, timeout=timeout) as response:
            if response.geturl() != request.full_url:
                raise ApiError("Unexpected response URL; request not repeated", uncertain=uncertain)
            raw = response.read(MAX_RESPONSE_BYTES + 1)
            if len(raw) > MAX_RESPONSE_BYTES:
                raise ApiError("Response exceeds the size limit; request not repeated", uncertain=uncertain)
            return raw.decode("utf-8")
    except ApiError:
        raise
    except urllib.error.HTTPError as error:
        # Do not include the URL, headers, Location, body, reason, or request data.
        status = int(error.code)
        retry_after = _parse_retry_after(error.headers.get('Retry-After')) if error.headers is not None else None
        try:
            error_body = error.read(MAX_ERROR_BYTES + 1)
        except (OSError, http.client.HTTPException):
            error_body = b""
        finally:
            error.close()
        raise ApiError(f"HTTP {status}; response details redacted; request not repeated",
                       status=status, uncertain=uncertain, response_bytes=error_body,
                       retry_after_seconds=retry_after) from None
    except (urllib.error.URLError, OSError, http.client.HTTPException, ValueError, UnicodeError):
        raise ApiError("Network or JSON response failure; details redacted; request not repeated",
                       uncertain=uncertain) from None


def call_service(session: Session, service: str, args: list, timeout: float = 30) -> Any:
    """Decode one call_service_text response; invalid JSON is redacted, never retried."""
    text = call_service_text(session, service, args, timeout)
    try:
        return json.loads(text)
    except (ValueError, UnicodeError):
        raise ApiError("Invalid JSON response; details redacted; request not repeated",
                       uncertain=service == PLAY_SERVICE) from None


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    importer = commands.add_parser("import-har", help="Import credentials and back up the captured Java source; no HTTP calls")
    importer.add_argument("--har", required=True, type=Path)
    importer.add_argument("--out", type=Path, default=DEFAULT_SESSION_PATH)
    importer.add_argument("--entry-index", type=int)
    importer.add_argument("--backup", type=Path)
    inspector = commands.add_parser("inspect", aliases=["check"], help="Validate the local session and source backup, printing safe metadata only")
    inspector.add_argument("--session", type=Path, default=DEFAULT_SESSION_PATH)
    args = parser.parse_args(argv)
    try:
        if args.command == "import-har":
            session = import_har(args.har, args.out, entry_index=args.entry_index, backup_path=args.backup)
        else:
            session = load_session(args.session)
        print(json.dumps(session.public_metadata(), ensure_ascii=False, indent=2))
        return 0
    except (SessionError, ApiError) as error:
        print(str(error))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
