"""Hermetic audit fixtures; no API, real plan/results, or executable bot invocation."""
from dataclasses import replace
import json
from pathlib import Path
import shutil
import unittest
import uuid

from diagnostics import boss_batch_audit as audit
from replay_capture import EMPTY_DECLARATION, IDENTITY_SCHEME, config_sha256


class BossBatchAuditTest(unittest.TestCase):
    def setUp(self):
        base = Path(__file__).resolve().parents[1] / 'build/test-boss-batch-audit'
        base.mkdir(parents=True, exist_ok=True)
        self.root = base / uuid.uuid4().hex
        self.root.mkdir()

        def cleanup():
            if self.root.resolve().parent != base.resolve():
                raise AssertionError('Test cleanup escaped workspace')
            shutil.rmtree(self.root)
        self.addCleanup(cleanup)
        self.write(audit.PROTOCOL, 'fixed test protocol\n')
        submission = {}
        for index in range(26):
            path = Path(f'build/submit/file-{index}.bin')
            self.write(path, f'original-{index}')
            submission[path.as_posix()] = audit.sha((self.root/path).read_bytes())
        self.write_json(audit.LAUNCH, {'launch': {'submissionFiles': submission}})
        self.markers, builds = {}, {}
        for arm in ('candidate', 'control'):
            builds[arm], self.markers[arm] = self.build(arm)
        slots = []
        for pair in range(1, 41):
            arms = ('candidate', 'control') if pair % 2 else ('control', 'candidate')
            for arm in arms:
                slots.append({'slot': len(slots)+1, 'pair': pair, 'arm': arm, 'seat': pair % 2, 'seed': str(1000+pair)})
        self.plan = {'schemaVersion': 1, 'design': 'matched', 'purpose': 'boss-screen', 'gamesPerBuild': 40,
                     'scheduleSeed': 2026091840, 'bossAgentId': 6740207, 'requestBossAgentId': -2,
                     'mapHashScheme': audit.MAP_SCHEME, 'alpha': 0.05, 'promotion': False,
                     'slots': slots, 'builds': builds, 'excludedMapHashes': [audit.sha('old-map')],
                     'sessionBindingSha256': audit.sha('private-binding-never-export')}
        self.path = self.root / 'build/cg-boss/audit-fixture/plan.json'
        self.pins = replace(audit.Pins(), protocol=audit.sha((self.root/audit.PROTOCOL).read_bytes()),
                            launch=audit.sha((self.root/audit.LAUNCH).read_bytes()),
                            candidate_build=builds['candidate']['buildId'], candidate_source=builds['candidate']['sourceSha256'],
                            control_build=builds['control']['buildId'], control_source=builds['control']['sourceSha256'])
        self.repin_plan()
        for slot in slots:
            self.game(slot)

    def write(self, path, value):
        path = self.root/path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value, encoding='utf-8', newline='')
        return path

    def write_json(self, path, value):
        return self.write(path, audit.encoded(value))

    def build(self, arm):
        strategy_id = 'current' if arm == 'candidate' else 'v07'
        strategy = audit.CANDIDATE if arm == 'candidate' else 'v07'
        params = {'USE_ROLLOUT_NETWORK_CHORDS': 'true' if arm == 'candidate' else 'false',
                  'USE_ROLLOUT_ROUTE_DEPENDENCIES': 'false', 'USE_ROLLOUT_NETWORK_AFTER_BASE': 'true'}
        normalized = f'// {strategy}\nclass Player {{\n{EMPTY_DECLARATION}\n}}\n'
        identity, config = audit.sha(normalized), config_sha256(params)
        marker = f'BTK_BUILD_V1 strategy={strategy_id} build={identity} config={config}'
        source = normalized.replace(EMPTY_DECLARATION, f'public static final String BUILD_MARKER = "{marker}";')
        manifest = {'schemaVersion': 2, 'identityScheme': IDENTITY_SCHEME, 'strategy': strategy,
                    'strategyId': strategy_id, 'buildId': identity, 'params': params, 'marker': marker,
                    'configSha256': config, 'sha256': audit.sha(source), 'bytesUtf8': len(source.encode()),
                    'charactersUtf16': len(source.encode('utf-16-le'))//2}
        directory = Path('build/cg-boss/artifacts')/arm
        for folder in (directory, Path('build/submission-registry')/identity):
            self.write(folder/'Player.java', source)
            self.write_json(folder/'manifest.json', manifest)
        self.write(directory/'classes/Player.class', 'non-executable test fixture')
        classes = audit.classes_hash(self.root/directory)
        process = {'schemaVersion': 1, 'sourceSha256': manifest['sha256'], 'classesSha256': classes,
                   'strict': True, 'graceMs': 0, 'problems': 0, 'games': []}
        for index in range(4):
            process['games'].append({'player': index % 2, 'timeouts': 0, 'nominalOverruns': 0,
                                     'result': {'error': [None, None], 'turns': 2, 'winner': 0, 'score': [10, 5]},
                                     'timings': [{'status': 'OK', 'turn': 0, 'graceMs': 0, 'responseMs': 4.0},
                                                 {'status': 'OK', 'turn': 1, 'graceMs': 0, 'responseMs': 3.0}]})
        process_path = self.write_json(directory/'process-check.json', process)
        return {'directory': str(self.root/directory), 'strategy': strategy, 'buildId': identity,
                'sourceSha256': manifest['sha256'], 'classesSha256': classes, 'configSha256': config,
                'processCheck': str(process_path), 'processCheckSha256': audit.sha(process_path.read_bytes())}, marker

    def repin_plan(self):
        self.plan.pop('planId', None)
        self.plan['planId'] = audit.sha(audit.encoded(self.plan))
        self.write_json(self.path, self.plan)
        self.pins = replace(self.pins, plan=audit.sha(self.path.read_bytes()), plan_id=self.plan['planId'])

    def game(self, slot):
        arm, pair, seat = slot['arm'], slot['pair'], slot['seat']
        own_win = pair <= (30 if arm == 'candidate' else 20)
        ranks = [0, 1] if own_win == (seat == 0) else [1, 0]
        scores = [100 if rank == 0 else 50 for rank in ranks]
        boss = {'agentId': 6740207, 'index': 1-seat, 'arenaboss': {'nickname': 'Boss 4'}}
        raw = {'gameId': 10000+slot['slot'], 'ranks': ranks, 'refereeInput': f"seed={slot['seed']}\n",
               'agents': [boss, {'agentId': -1, 'index': seat}],
               'frames': [{'view': ' 0\n'+json.dumps({'global': {'graphics': f'map-{pair}'}})},
                          {'agentId': seat, 'stdout': 'WAIT;MESSAGE '+self.markers[arm], 'stderr': 'DO-NOT-EXPORT-PRIVATE-TEXT'},
                          {'view': ' 1\n'+json.dumps({'endScreen': [scores, 'logo', []]})}]}
        folder = self.path.parent/f"{slot['slot']:04d}"
        build = self.plan['builds'][arm]
        self.write_json(folder/'intent.json', {'planId': self.plan['planId'], 'slot': slot, 'sourceSha256': build['sourceSha256']})
        self.write_json(folder/'play-response.json', {'gameId': raw['gameId'], 'privateHandle': 'DO-NOT-EXPORT-PRIVATE-HANDLE'})
        canonical = self.write_json(folder/'canonical.json', raw)
        archived = Path('replays/cg/boss')/arm/build['buildId']/f"{raw['gameId']}.json"
        self.write(archived, canonical.read_text(encoding='utf-8'))
        outcome = {'gameId': raw['gameId'], 'seat': seat, 'seed': slot['seed'], 'refereeInput': raw['refereeInput'],
                   'result': 'W' if own_win else 'L', 'scores': scores, 'ranks': ranks,
                   'mapHash': audit.sha(f'map-{pair}'), 'boss': boss, 'gameInformation': []}
        record = {'planId': self.plan['planId'], 'slot': slot, 'outcome': outcome,
                  'capture': {'path': archived.as_posix(), 'rawSha256': audit.sha(canonical.read_bytes()), 'expectedBuildMatches': True}}
        self.write_json(folder/'result.json', record)

    def test_complete_audit_exports_only_public_evidence_and_exact_paired_result(self):
        result = audit.export(self.path, self.root/audit.OUTPUT, self.root, self.pins)
        self.assertEqual(80, len(result['games']))
        self.assertEqual(40, len(result['pairs']))
        self.assertEqual(30, result['aggregate']['candidate']['wins'])
        self.assertEqual(20, result['aggregate']['control']['wins'])
        self.assertEqual({'candidateOnlyWin': 10, 'controlOnlyWin': 0}, result['discordances'])
        self.assertEqual(1/1024, result['exactTest']['p'])
        self.assertEqual(26, result['originalSubmission']['verifiedFiles'])
        exported = (self.root/audit.OUTPUT).read_text(encoding='utf-8')
        for forbidden in ('sessionBinding', 'DO-NOT-EXPORT', 'privateHandle', str(self.root), 'Player.java'):
            self.assertNotIn(forbidden, exported)
        with self.assertRaises(ValueError): audit.export(self.path, self.root/audit.OUTPUT, self.root, self.pins)

    def test_incomplete_sample_writes_no_final_evidence(self):
        missing = self.path.parent/'0080/result.json'
        missing.unlink()
        before = {p: audit.sha(p.read_bytes()) for p in self.path.parent.rglob('*') if p.is_file()}
        with self.assertRaises(audit.IncompleteError):
            audit.export(self.path, self.root/audit.OUTPUT, self.root, self.pins)
        self.assertFalse((self.root/audit.OUTPUT).exists())
        self.assertEqual(before, {p: audit.sha(p.read_bytes()) for p in self.path.parent.rglob('*') if p.is_file()})

    def test_protocol_and_original_submission_are_pinned(self):
        original = (self.root/audit.PROTOCOL).read_bytes()
        self.write(audit.PROTOCOL, 'edited after results')
        with self.assertRaisesRegex(ValueError, 'protocol'):
            audit.audit(self.path, self.root, self.pins)
        (self.root/audit.PROTOCOL).write_bytes(original)
        self.write('build/submit/file-0.bin', 'changed')
        with self.assertRaisesRegex(ValueError, 'build/submit'):
            audit.audit(self.path, self.root, self.pins)

    def test_saved_result_cannot_override_canonical_ranks(self):
        path = self.path.parent/'0001/result.json'
        result = audit.read(path); result['outcome']['result'] = 'L'
        self.write_json(path, result)
        with self.assertRaisesRegex(ValueError, 'canonical recomputation'):
            audit.audit(self.path, self.root, self.pins)

    def test_original_play_response_binds_each_game(self):
        self.write_json(self.path.parent/'0001/play-response.json', {'gameId': 123})
        with self.assertRaisesRegex(ValueError, 'original request'):
            audit.audit(self.path, self.root, self.pins)

    def test_map_mismatch_rejected_even_when_local_record_matches_canonical(self):
        folder = self.path.parent/'0002'
        raw = audit.read(folder/'canonical.json')
        raw['frames'][0]['view'] = ' 0\n'+json.dumps({'global': {'graphics': 'different paired map'}})
        path = self.write_json(folder/'canonical.json', raw)
        record = audit.read(folder/'result.json')
        self.write(record['capture']['path'], path.read_text(encoding='utf-8'))
        record['capture']['rawSha256'] = audit.sha(path.read_bytes())
        record['outcome']['mapHash'] = audit.sha('different paired map')
        self.write_json(folder/'result.json', record)
        with self.assertRaisesRegex(ValueError, 'matched pair differs'):
            audit.audit(self.path, self.root, self.pins)

    def test_known_map_exclusion_is_applied_to_the_original_plan(self):
        self.plan['excludedMapHashes'] = sorted([audit.sha('old-map'), audit.sha('map-1')])
        self.repin_plan()
        for slot in self.plan['slots']:
            self.game(slot)
        with self.assertRaisesRegex(ValueError, 'excluded historical map'):
            audit.audit(self.path, self.root, self.pins)

    def test_source_and_process_report_bytes_cannot_change(self):
        build = self.plan['builds']['candidate']
        source = Path(build['directory'])/'Player.java'
        original = source.read_bytes(); source.write_bytes(original+b'\n')
        with self.assertRaises(ValueError): audit.audit(self.path, self.root, self.pins)
        source.write_bytes(original)
        process = Path(build['processCheck'])
        process.write_bytes(process.read_bytes()+b'\n')
        with self.assertRaisesRegex(ValueError, 'Process report'):
            audit.audit(self.path, self.root, self.pins)

    def test_interrupted_audit_preserves_descriptive_results_without_final_test(self):
        for number in range(21, 81):
            folder = self.path.parent/f'{number:04d}'
            for file in folder.iterdir():
                if number != 21 or file.name != 'intent.json':
                    file.unlink()
        self.write_json(self.path.parent/'0021/api-error.json',
                        {'status': 422, 'uncertain': False, 'message': 'DO-NOT-EXPORT-PRIVATE-ERROR'})
        output = self.root/audit.INTERRUPTED_OUTPUT
        result = audit.export(self.path, output, self.root, self.pins, allow_interrupted=True)
        self.assertFalse(result['complete'])
        self.assertEqual('INTERRUPTED', result['status'])
        self.assertEqual(20, result['completedGames'])
        self.assertEqual(10, result['completedMapPairs'])
        self.assertEqual(59, result['notStartedSlots'])
        self.assertEqual(422, result['incompleteAttempts'][0]['httpStatus'])
        self.assertNotIn('exactTest', result)
        self.assertNotIn('discordances', result)
        self.assertNotIn('decision', result)
        self.assertFalse(result['promotion'])
        self.assertNotIn('DO-NOT-EXPORT', output.read_text(encoding='utf-8'))
        self.assertTrue((self.path.parent/'0021/intent.json').is_file())
        with self.assertRaisesRegex(ValueError, 'final evidence path'):
            audit.export(self.path, self.root/audit.OUTPUT, self.root, self.pins, allow_interrupted=True)
        self.assertFalse((self.root/audit.OUTPUT).exists())


if __name__ == '__main__':
    unittest.main()
