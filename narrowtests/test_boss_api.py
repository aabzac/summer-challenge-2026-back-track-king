"""Offline contract/security tests. No browser, network, CodinGame account or matches."""

import copy
import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
import urllib.error


sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import boss_api as api


FAKE_COOKIE = "rememberMe=synthetic-test-only; session=not-a-real-credential"
FAKE_HANDLE = "synthetic-test-session-handle"
SOURCE = "// source backup: Unicode \u03b1\nclass Player { public static void main(String[] args) {} }\n"


def template():
    return [FAKE_HANDLE, {
        "code": SOURCE, "programmingLanguageId": "Java",
        "multi": {"agentsIds": [-1, 6740207], "gameOptions": None,
                  "isSoloLeague": False, "playType": ["IDE_CODE", "BOSS"]},
        "observed_extra_field": {"keep": [1, "two", None]},
    }]


def request_entry(arguments=None, *, headers=True, url=None):
    request = {
        "method": "POST", "url": url or api.ORIGIN + "/services/TestSession/play",
        "headers": [{"name": "Cookie", "value": FAKE_COOKIE},
                    {"name": "X-CSRF-Token", "value": "synthetic-csrf-token"},
                    {"name": "Host", "value": "ignored-captured-host.example"}] if headers else [],
        "cookies": [], "postData": {"mimeType": "application/json", "text": json.dumps(arguments or template())},
    }
    return {"request": request}


class Response(io.BytesIO):
    def __init__(self, value, url):
        super().__init__(value)
        self.url = url

    def geturl(self):
        return self.url


class BossApiTest(unittest.TestCase):
    def setUp(self):
        scratch = Path(__file__).resolve().parents[1] / "build" / "test-boss-api"
        scratch.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=scratch)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.out = self.root / "build/cg-boss/private/session.json"
        self.har = self.root / "capture.har"

    def save_har(self, entries=None):
        self.har.write_text(json.dumps({"log": {"entries": entries or [request_entry()]}}), encoding="utf-8")

    def session(self):
        self.save_har()
        return api.import_har(self.har, self.out, project_root=self.root)

    def play_args(self, session):
        arguments = copy.deepcopy(session.play_template)
        arguments[1]["code"] = "class Player {}"
        return arguments

    def test_import_preserves_exact_template_except_separate_source_backup(self):
        session = self.session()
        expected = template()
        expected[1]["code"] = ""
        self.assertEqual(expected, session.play_template)
        self.assertEqual(SOURCE.encode("utf-8"), session.source_backup.read_bytes())
        self.assertEqual(hashlib.sha256(SOURCE.encode("utf-8")).hexdigest(), session.source_sha256)
        saved = json.loads(self.out.read_text(encoding="utf-8"))
        self.assertEqual("", saved["play_template"][1]["code"])
        self.assertNotIn("host", session.headers)
        self.assertEqual(FAKE_COOKIE, session.cookie)
        self.assertEqual("synthetic-csrf-token", session.headers["x-csrf-token"])
        metadata = json.dumps(session.public_metadata()) + repr(session)
        self.assertNotIn(FAKE_COOKIE, metadata)
        self.assertNotIn(FAKE_HANDLE, metadata)
        self.assertNotIn("synthetic-csrf-token", metadata)
        self.assertNotIn(SOURCE, metadata)

    def test_source_is_preserved_before_credentials_are_written(self):
        self.save_har()
        actual_write = api._private_write

        def checking_write(path, content):
            self.assertEqual(SOURCE, (path.parent / "ide-source-backup.java").read_text(encoding="utf-8"))
            actual_write(path, content)

        with mock.patch.object(api, "_private_write", side_effect=checking_write):
            api.import_har(self.har, self.out, project_root=self.root)

    def test_cookie_array_fallback_and_swapped_seats_are_supported(self):
        arguments = template()
        arguments[1]["multi"].update(agentsIds=[6740207, -1], playType=["BOSS", "IDE_CODE"], gameOptions="seed=123")
        entry = request_entry(arguments, headers=False)
        entry["request"]["cookies"] = [{"name": "session", "value": "fake-value"}]
        self.save_har([entry])
        session = api.import_har(self.har, self.out, project_root=self.root)
        self.assertEqual("session=fake-value", session.cookie)
        self.assertEqual([6740207, -1], session.play_template[1]["multi"]["agentsIds"])
        self.assertEqual("seed=123", session.play_template[1]["multi"]["gameOptions"])

    def test_ambiguous_har_requires_explicit_original_entry_index(self):
        self.save_har([request_entry(), request_entry()])
        with self.assertRaises(api.SessionError):
            api.import_har(self.har, self.out, project_root=self.root)
        self.assertFalse(self.out.exists())
        session = api.import_har(self.har, self.out, entry_index=1, project_root=self.root)
        self.assertEqual(FAKE_HANDLE, session.play_template[0])

    def test_observed_minus2_boss_sentinel_is_preserved_in_both_seats(self):
        for agents, types in (([-1, -2], ["IDE_CODE", "BOSS"]), ([-2, -1], ["BOSS", "IDE_CODE"])):
            arguments = template()
            arguments[1]["multi"].update(agentsIds=agents, playType=types)
            self.save_har([request_entry(arguments)])
            session = api.import_har(self.har, self.out, project_root=self.root)
            self.assertEqual(agents, session.play_template[1]["multi"]["agentsIds"])

    def test_sanitized_har_without_credentials_does_not_create_session(self):
        self.save_har([request_entry(headers=False)])
        with self.assertRaises(api.SessionError) as raised:
            api.import_har(self.har, self.out, project_root=self.root)
        self.assertIn("export with credentials", str(raised.exception))
        self.assertFalse(self.out.exists())
        self.assertEqual(SOURCE.encode("utf-8"), (self.out.parent / "ide-source-backup.java").read_bytes())

    def test_foreign_origin_or_query_suffix_cannot_supply_credentials(self):
        for url in ("https://example.invalid/services/TestSession/play",
                    api.ORIGIN + "/services/TestSession/play?redirect=example.invalid"):
            with self.subTest(url=url):
                self.save_har([request_entry(url=url)])
                with self.assertRaises(api.SessionError):
                    api.import_har(self.har, self.out, entry_index=0, project_root=self.root)
        self.assertFalse(self.out.exists())

    def test_invalid_secret_and_misaligned_agent_types_are_rejected_without_echo(self):
        cases = []
        redacted = request_entry()
        redacted["request"]["headers"][0]["value"] = "session=<redacted>"
        cases.append(redacted)
        injected = request_entry()
        injected["request"]["headers"][0]["value"] = "session=fake\r\nX-Leak: synthetic-secret"
        cases.append(injected)
        bad_order = template()
        bad_order[1]["multi"]["playType"] = ["BOSS", "IDE_CODE"]
        cases.append(request_entry(bad_order))
        for entry in cases:
            self.save_har([entry])
            with self.assertRaises(api.SessionError) as raised:
                api.import_har(self.har, self.out, project_root=self.root)
            self.assertNotIn("synthetic-secret", str(raised.exception))
            self.assertNotIn(FAKE_COOKIE, str(raised.exception))
        self.assertFalse(self.out.exists())

    def test_secret_output_cannot_escape_ignored_private_directory(self):
        self.save_har()
        with self.assertRaises(api.SessionError):
            api.import_har(self.har, self.root / "session.json", project_root=self.root)
        with self.assertRaises(api.SessionError):
            api.import_har(self.har, self.out, backup_path=self.root / "outside.java", project_root=self.root)
        self.assertFalse(self.out.exists())

    def test_existing_different_backup_and_tampered_backup_are_rejected(self):
        session = self.session()
        old_secret = self.out.read_bytes()
        different = template()
        different[1]["code"] += "// different code\n"
        self.save_har([request_entry(different)])
        with self.assertRaises(api.SessionError):
            api.import_har(self.har, self.out, project_root=self.root)
        self.assertEqual(old_secret, self.out.read_bytes())
        self.assertEqual(SOURCE, session.source_backup.read_text(encoding="utf-8"))
        session.source_backup.write_text("tampered", encoding="utf-8")
        with self.assertRaises(api.SessionError):
            api.load_session(self.out)

    def test_transport_preserves_response_text_and_uses_only_fixed_origin(self):
        session = self.session()
        body = '{ "gameId": 123, "value": "\\u03b1" }\n'
        opener = mock.Mock()
        opener.open.return_value = Response(body.encode(), api.ORIGIN + "/services/TestSession/play")
        with mock.patch.object(api.urllib.request, "build_opener", return_value=opener) as factory:
            result = api.call_service_text(session, api.PLAY_SERVICE, self.play_args(session), 12)
        self.assertEqual(body, result)
        self.assertIsInstance(factory.call_args.args[0], api._NoRedirect)
        self.assertEqual(1, opener.open.call_count)
        request = opener.open.call_args.args[0]
        self.assertEqual(api.ORIGIN + "/services/TestSession/play", request.full_url)
        self.assertEqual(FAKE_COOKIE, request.get_header("Cookie"))
        self.assertEqual(12, opener.open.call_args.kwargs["timeout"])
        self.assertEqual("class Player {}", json.loads(request.data)[1]["code"])

    def test_json_wrapper_and_read_only_service_have_same_transport(self):
        session = self.session()
        opener = mock.Mock()
        opener.open.return_value = Response(b'{"testSessionHandle":"refreshed"}', api.ORIGIN + "/services/TestSession/startTestSession")
        with mock.patch.object(api.urllib.request, "build_opener", return_value=opener):
            result = api.call_service(session, "TestSession/startTestSession", [FAKE_HANDLE])
        self.assertEqual({"testSessionHandle": "refreshed"}, result)

    def test_http_errors_and_redirects_are_redacted_and_never_retried(self):
        session = self.session()
        for status in (302, 401, 503):
            opener = mock.Mock()
            opener.open.side_effect = urllib.error.HTTPError(
                "https://sensitive.invalid/" + FAKE_HANDLE, status, "sensitive-error-body",
                {"Location": "https://foreign.invalid/" + FAKE_COOKIE}, io.BytesIO(FAKE_COOKIE.encode()))
            with mock.patch.object(api.urllib.request, "build_opener", return_value=opener):
                with self.assertRaises(api.ApiError) as raised:
                    api.call_service(session, api.PLAY_SERVICE, self.play_args(session))
            self.assertEqual(status, raised.exception.status)
            self.assertTrue(raised.exception.uncertain)
            self.assertNotIn(FAKE_COOKIE, str(raised.exception))
            self.assertNotIn(FAKE_HANDLE, str(raised.exception))
            self.assertNotIn("sensitive-error-body", str(raised.exception))
            self.assertEqual(1, opener.open.call_count)
        self.assertIsNone(api._NoRedirect().redirect_request(None, None, 302, "", {}, "https://foreign.invalid"))

    def test_network_failure_is_uncertain_and_redacted(self):
        session = self.session()
        opener = mock.Mock()
        opener.open.side_effect = urllib.error.URLError("private diagnostic " + FAKE_COOKIE)
        with mock.patch.object(api.urllib.request, "build_opener", return_value=opener):
            with self.assertRaises(api.ApiError) as raised:
                api.call_service(session, api.PLAY_SERVICE, self.play_args(session))
        self.assertTrue(raised.exception.uncertain)
        self.assertNotIn(FAKE_COOKIE, str(raised.exception))
        self.assertEqual(1, opener.open.call_count)

    def test_private_http_error_body_retained_but_never_in_exception_str_or_repr(self):
        session = self.session()
        for code, expected in ((402, 402), ("RATE_LIMITED", "RATE_LIMITED"),
                               ("synthetic-secret-token", None), (True, None)):
            body = json.dumps({"id": code, "message": FAKE_COOKIE, "details": FAKE_HANDLE}).encode()
            opener = mock.Mock()
            opener.open.side_effect = urllib.error.HTTPError(
                api.ORIGIN + "/services/TestSession/play", 422, "", {}, io.BytesIO(body))
            with mock.patch.object(api.urllib.request, "build_opener", return_value=opener):
                with self.assertRaises(api.ApiError) as raised:
                    api.call_service_text(session, api.PLAY_SERVICE, self.play_args(session))
            error = raised.exception
            self.assertEqual(body, error.response_bytes)
            self.assertEqual(expected, error.error_code)
            self.assertFalse(error.body_truncated)
            self.assertNotIn(FAKE_COOKIE, str(error) + repr(error))
            self.assertNotIn(FAKE_HANDLE, str(error) + repr(error))
            self.assertNotIn("synthetic-secret-token", str(error) + repr(error))
            self.assertEqual(1, opener.open.call_count)

    def test_private_http_error_body_is_bounded(self):
        session = self.session()
        opener = mock.Mock()
        opener.open.side_effect = urllib.error.HTTPError(
            api.ORIGIN + "/services/TestSession/play", 503, "", {},
            io.BytesIO(b"x" * (api.MAX_ERROR_BYTES + 100)))
        with mock.patch.object(api.urllib.request, "build_opener", return_value=opener):
            with self.assertRaises(api.ApiError) as raised:
                api.call_service_text(session, api.PLAY_SERVICE, self.play_args(session))
        self.assertEqual(api.MAX_ERROR_BYTES, len(raised.exception.response_bytes))
        self.assertTrue(raised.exception.body_truncated)
        self.assertIsNone(raised.exception.error_code)

    def test_malformed_json_failure_is_uncertain_and_redacted(self):
        session = self.session()
        opener = mock.Mock()
        opener.open.return_value = Response(FAKE_COOKIE.encode(), api.ORIGIN + "/services/TestSession/play")
        with mock.patch.object(api.urllib.request, "build_opener", return_value=opener):
            with self.assertRaises(api.ApiError) as raised:
                api.call_service(session, api.PLAY_SERVICE, self.play_args(session))
        self.assertTrue(raised.exception.uncertain)
        self.assertNotIn(FAKE_COOKIE, str(raised.exception))
        self.assertEqual(1, opener.open.call_count)

    def test_service_injection_empty_code_and_bad_timeout_fail_before_network(self):
        session = self.session()
        with mock.patch.object(api.urllib.request, "build_opener") as opener:
            for service in ("https://foreign.invalid", "TestSession/../play", "TestSession/play?x=1", "TestSession/play\n"):
                with self.assertRaises(api.ApiError):
                    api.call_service(session, service, [])
            with self.assertRaises(api.ApiError):
                api.call_service(session, api.PLAY_SERVICE, session.play_template)
            with self.assertRaises(api.ApiError):
                api.call_service(session, "TestSession/startTestSession", [], float("nan"))
            opener.assert_not_called()


if __name__ == "__main__":
    unittest.main()
