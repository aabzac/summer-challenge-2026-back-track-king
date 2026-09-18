"""Offline capture tests: no browser, credentials, network, or real replay writes."""
import json
from pathlib import Path
import shutil
import unittest
import uuid

from replay_capture import (EMPTY_DECLARATION, IDENTITY_SCHEME, config_sha256,
                            save_capture, sha256, validate_local_build)


class CaptureTest(unittest.TestCase):
    def setUp(self):
        test_root = Path(__file__).resolve().parents[1] / 'build/test-replay-capture'
        test_root.mkdir(parents=True, exist_ok=True)
        # Python 3.13's private 0700 temporary directories can deny access to
        # the restricted Windows token running this workspace.
        self.project = test_root / ('case-' + uuid.uuid4().hex)
        self.project.mkdir()
        def cleanup():
            if self.project.resolve().parent != test_root.resolve():
                raise AssertionError('Test cleanup escaped its workspace directory')
            shutil.rmtree(self.project)
        self.addCleanup(cleanup)

    def build(self, strategy='v07'):
        params = {'NOISE': '0.1', 'USE_ROLLOUT_NETWORK_CHORDS': 'false'}
        normalized = 'class Player {\n' + EMPTY_DECLARATION + '\n}\n'
        build = sha256(normalized)
        marker = f'BTK_BUILD_V1 strategy={strategy} build={build} config={config_sha256(params)}'
        source = normalized.replace(EMPTY_DECLARATION,
                                    f'public static final String BUILD_MARKER = "{marker}";')
        manifest = {'schemaVersion': 2, 'strategy': strategy, 'strategyId': strategy,
                    'buildId': build, 'configSha256': config_sha256(params), 'params': params,
                    'sha256': sha256(source), 'marker': marker, 'identityScheme': IDENTITY_SCHEME,
                    'bytesUtf8': len(source.encode()), 'charactersUtf16': len(source.encode('utf-16-le')) // 2}
        directory = self.project / 'build/submission-registry' / build
        directory.mkdir(parents=True)
        (directory / 'Player.java').write_bytes(source.encode('utf-8'))
        (directory / 'manifest.json').write_text(json.dumps(manifest), encoding='utf-8')
        return marker, manifest, directory

    def replay(self, marker=None, game=1234, player=0):
        first = 'PLACE_TRACKS 2 3' + (';MESSAGE ' + marker if marker else '') + '\n'
        raw = {'gameId': game, 'frames': [{'agentId': player, 'stdout': first},
                                        {'agentId': 1-player, 'stdout': 'WAIT\n'},
                                        {'agentId': player, 'stdout': 'WAIT\n'}],
               'agents': [{'index': player, 'agentId': -1, 'codingamer': {'pseudo': 'test'}},
                          {'index': 1-player, 'agentId': 6740207, 'arenaboss': {'nickname': 'Boss 4'}}]}
        return json.dumps(raw, separators=(',', ':'))

    def capture(self, content, **extra):
        return save_capture({'kind': 'boss', 'content': content, **extra}, self.project)

    def test_verified_marker_preserves_canonical_bytes_and_qualifies_execution_claim(self):
        marker, manifest, _ = self.build()
        content = self.replay(marker + ' note=timing 12ms')
        saved = self.capture(content, expectedBuildId=manifest['buildId'])
        self.assertEqual(f"replays/cg/boss/v07/{manifest['buildId']}/1234.json", saved['path'])
        self.assertEqual(content.encode(), (self.project / saved['path']).read_bytes())
        self.assertEqual(manifest['sha256'], saved['attribution']['artifactSha256'])
        self.assertTrue(saved['attribution']['retainedArtifactVerified'])
        self.assertFalse(saved['attribution']['executionIdentityIndependentlyVerified'])
        self.assertTrue(saved['expectedBuildMatches'])
        self.assertEqual('replays/cg/boss/_metadata/1234.json', saved['metadataPath'])

    def test_retries_preserve_both_raw_and_metadata_bytes(self):
        marker, _, _ = self.build()
        content = self.replay(marker)
        first = self.capture(content)
        files = {p: p.read_bytes() for p in self.project.rglob('*') if p.is_file()}
        second = self.capture(content)
        self.assertTrue(first['created'])
        self.assertFalse(second['created'])
        self.assertEqual(first['capturedAt'], second['capturedAt'])
        self.assertEqual(files, {p: p.read_bytes() for p in self.project.rglob('*') if p.is_file()})
        with self.assertRaises(ValueError):
            self.capture(content + '\n')

    def test_missing_unregistered_malformed_and_late_markers_are_unknown(self):
        marker, manifest, directory = self.build()
        self.assertEqual('unknown', self.capture(self.replay(), expectedBuildId=manifest['buildId'])['attribution']['status'])
        (directory / 'manifest.json').unlink()
        saved = self.capture(self.replay(marker, game=1235))
        self.assertEqual('unverified-marker', saved['attribution']['status'])
        self.assertIn('/unknown/', saved['path'])
        malformed = self.capture(self.replay('BTK_BUILD_V1 strategy=../escape', game=1236))
        self.assertEqual('unknown', malformed['attribution']['status'])
        raw = json.loads(self.replay(game=1237))
        raw['frames'][2]['stdout'] = 'WAIT;MESSAGE ' + marker
        self.assertEqual('unknown', self.capture(json.dumps(raw))['attribution']['status'])

    def test_duplicate_or_opponent_markers_cannot_assign_our_build(self):
        marker, _, _ = self.build()
        raw = json.loads(self.replay(marker))
        raw['frames'][2]['stdout'] = 'WAIT;MESSAGE ' + marker
        self.assertEqual('unknown', self.capture(json.dumps(raw))['attribution']['status'])
        raw = json.loads(self.replay(game=1235))
        raw['frames'][1]['stdout'] = 'WAIT;MESSAGE ' + marker
        self.assertEqual('unknown', self.capture(json.dumps(raw))['attribution']['status'])

    def test_tampered_source_or_config_does_not_receive_verified_directory(self):
        marker, manifest, directory = self.build()
        self.assertEqual(manifest, validate_local_build(self.project, manifest['buildId']))
        source = (directory / 'Player.java').read_text()
        (directory / 'Player.java').write_bytes((source + '\n').encode())
        saved = self.capture(self.replay(marker))
        self.assertEqual('unverified-marker', saved['attribution']['status'])
        (directory / 'Player.java').write_bytes(source.encode())
        manifest['params']['NOISE'] = '0.2'
        (directory / 'manifest.json').write_text(json.dumps(manifest))
        self.assertEqual('unverified-marker', self.capture(self.replay(marker, game=1235))['attribution']['status'])

    def test_slot_one_positive_boss_id_and_legacy_alias_are_supported(self):
        marker, _, _ = self.build()
        saved = save_capture({'kind': 'bot', 'content': self.replay(marker, player=1)}, self.project)
        self.assertEqual(1, saved['player'])
        self.assertEqual(0, saved['boss'])
        self.assertTrue(saved['attribution']['retainedArtifactVerified'])
        raw = save_capture({'kind': 'bot_raw', 'content': '{}', 'filename': '../../response.json'}, self.project)
        self.assertEqual('replays/cg/boss/raw/unknown/response.json', raw['path'])

    def test_legacy_flat_capture_keeps_original_path_and_bytes(self):
        content = self.replay()
        path = self.project / 'replays/cg/boss/1234.json'
        path.parent.mkdir(parents=True)
        path.write_bytes(content.encode())
        saved = self.capture(content)
        self.assertFalse(saved['created'])
        self.assertEqual('replays/cg/boss/1234.json', saved['path'])
        self.assertEqual('unknown', saved['attribution']['status'])
        replay_paths = [p for p in (self.project / 'replays/cg/boss').glob('**/1234.json')
                        if '_metadata' not in p.parts]
        self.assertEqual([path], replay_paths)

    def test_error_payload_and_arena_game_are_not_saved_as_boss_replays(self):
        with self.assertRaises(ValueError):
            self.capture('{"error":"no replay"}')
        raw = json.loads(self.replay())
        del raw['agents'][1]['arenaboss']
        with self.assertRaises(ValueError):
            self.capture(json.dumps(raw))
        self.assertFalse((self.project / 'replays').exists())

    def test_posted_labels_cannot_override_missing_marker(self):
        saved = self.capture(self.replay(), strategy='v07', buildId='a'*64, expectedBuildId='a'*64)
        self.assertEqual('unknown', saved['attribution']['status'])
        self.assertFalse(saved['expectedBuildMatches'])
        self.assertEqual('replays/cg/boss/unknown/1234.json', saved['path'])

    def test_retry_rechecks_local_artifact_without_rewriting_historical_metadata(self):
        marker, manifest, directory = self.build()
        content = self.replay(marker)
        first = self.capture(content, expectedBuildId=manifest['buildId'])
        metadata_path = self.project / first['metadataPath']
        preserved = metadata_path.read_bytes()
        (directory / 'manifest.json').write_text('[]', encoding='utf-8')
        retry = self.capture(content, expectedBuildId=manifest['buildId'])
        self.assertFalse(retry['expectedBuildMatches'])
        self.assertFalse(retry['attribution']['retainedArtifactVerified'])
        self.assertEqual('unverified-marker', retry['attribution']['status'])
        self.assertEqual(preserved, metadata_path.read_bytes())
        (directory / 'manifest.json').write_text(json.dumps(manifest), encoding='utf-8')
        self.assertTrue(self.capture(content, expectedBuildId=manifest['buildId'])['expectedBuildMatches'])

    def test_malformed_existing_metadata_fails_closed_and_preserves_replay(self):
        marker, _, _ = self.build()
        content = self.replay(marker)
        saved = self.capture(content)
        path = self.project / saved['metadataPath']
        original = json.loads(path.read_bytes())
        corruptions = [[], {**original, 'gameId': 999}, {**original, 'player': False},
                       {**original, 'path': '../escape/1234.json'},
                       {**original, 'attribution': {'retainedArtifactVerified': True}},
                       {**original, 'attribution': {**original['attribution'], 'buildId': 'a'*64}},
                       {**original, 'attribution': {**original['attribution'], 'artifactSha256': 'a'*64}}]
        for bad in corruptions:
            with self.subTest(value=bad):
                path.write_text(json.dumps(bad), encoding='utf-8')
                unchanged = path.read_bytes()
                with self.assertRaises(ValueError):
                    self.capture(content)
                self.assertEqual(unchanged, path.read_bytes())
                self.assertEqual(content.encode(), (self.project / saved['path']).read_bytes())

    def test_malformed_types_and_windows_device_filenames_are_rejected(self):
        for slots in ((False, True), (None, 1), ('0', 1)):
            raw = json.loads(self.replay())
            raw['agents'][0]['index'], raw['agents'][1]['index'] = slots
            with self.subTest(slots=slots), self.assertRaises(ValueError):
                self.capture(json.dumps(raw))
        for kind in ([], {}, False):
            with self.subTest(kind=kind), self.assertRaises(ValueError):
                save_capture({'kind': kind, 'content': '{}'}, self.project)
        for agent_id in (False, None, '6740207'):
            raw = json.loads(self.replay())
            raw['agents'][1]['agentId'] = agent_id
            with self.subTest(agent_id=agent_id), self.assertRaises(ValueError):
                self.capture(json.dumps(raw))
        for filename in ('NUL', 'nul.txt', 'COM1.json', 'capture.json.'):
            with self.subTest(filename=filename), self.assertRaises(ValueError):
                save_capture({'kind': 'boss_raw', 'content': '{}', 'filename': filename}, self.project)
        for build_id in (None, [], False):
            with self.subTest(build_id=build_id), self.assertRaises(ValueError):
                validate_local_build(self.project, build_id)


if __name__ == '__main__':
    unittest.main()
