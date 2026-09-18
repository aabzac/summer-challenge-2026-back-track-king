"""Offline batch integrity/resume tests; no network, JVM games, or production captures."""
import copy
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import shutil
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import uuid

import boss_batch as batch
from boss_api import ApiError, Session
from replay_capture import EMPTY_DECLARATION, IDENTITY_SCHEME, config_sha256


class BossBatchTest(unittest.TestCase):
    def setUp(self):
        test_root = Path(__file__).resolve().parents[1] / 'build/test-boss-batch'
        test_root.mkdir(parents=True, exist_ok=True)
        self.project = test_root / ('case-' + uuid.uuid4().hex)
        self.project.mkdir()

        def cleanup():
            if self.project.resolve().parent != test_root.resolve():
                raise AssertionError('Test cleanup escaped its workspace directory')
            shutil.rmtree(self.project)
        self.addCleanup(cleanup)
        self.session = Session(['offline-session', {'programmingLanguageId': 'Java', 'code': '',
                               'multi': {'agentsIds': [-1, 6740207], 'playType': ['IDE_CODE', 'BOSS'],
                                         'isSoloLeague': False, 'gameOptions': None}}], 'offline=test')
        for name, value in [('ROOT', self.project), ('load_session', None), ('call_service_text', None)]:
            mock = patch.object(batch, name, value) if name == 'ROOT' else patch.object(batch, name)
            active = mock.start()
            self.addCleanup(mock.stop)
            if name == 'load_session':
                active.return_value = self.session
            elif name == 'call_service_text':
                self.transport = active
                active.side_effect = AssertionError('Unexpected network operation in offline test')
        self.output = io.StringIO()
        stdout = redirect_stdout(self.output)
        stdout.__enter__()
        self.addCleanup(stdout.__exit__, None, None, None)

    def build(self, strategy='v07'):
        params = {'NOISE': '0.1', 'USE_ROLLOUT_NETWORK_CHORDS': 'false'}
        normalized = f'// {strategy}\nclass Player {{\n{EMPTY_DECLARATION}\n}}\n'
        identity = batch.digest(normalized)
        marker = f'BTK_BUILD_V1 strategy={strategy} build={identity} config={config_sha256(params)}'
        source = normalized.replace(EMPTY_DECLARATION, f'public static final String BUILD_MARKER = "{marker}";')
        manifest = {'schemaVersion': 2, 'identityScheme': IDENTITY_SCHEME, 'strategy': strategy,
                    'strategyId': strategy, 'buildId': identity, 'params': params, 'marker': marker,
                    'configSha256': config_sha256(params), 'sha256': batch.digest(source),
                    'bytesUtf8': len(source.encode()), 'charactersUtf16': len(source.encode('utf-16-le')) // 2}
        artifact = self.project / 'artifacts' / strategy
        registry = self.project / 'build/submission-registry' / identity
        for directory in (artifact, registry):
            directory.mkdir(parents=True)
            (directory / 'Player.java').write_text(source, encoding='utf-8', newline='')
            (directory / 'manifest.json').write_text(json.dumps(manifest), encoding='utf-8')
        (artifact / 'classes').mkdir()
        # Only exact-artifact hashing is exercised; this fixture is never executed.
        (artifact / 'classes/Player.class').write_bytes(b'offline-class-fixture')
        check = artifact / 'process-check.json'
        check.write_text(json.dumps({'schemaVersion': 1, 'sourceSha256': manifest['sha256'],
                                     'classesSha256': batch.classes_hash(artifact),
                                     'strict': True, 'graceMs': 0, 'problems': 0,
                                     'games': [{'player': 0, 'timeouts': 0, 'nominalOverruns': 0,
                                                'result': {'error': [None, None], 'turns': 1, 'winner': 0, 'score': [10, 5]},
                                                'timings': [{'turn': 0, 'budgetMs': 1000, 'graceMs': 0, 'sendMs': 1.0,
                                                             'responseMs': 12.0, 'consumerMs': 13.0, 'status': 'OK', 'io': None}]}]}), encoding='utf-8')
        return artifact, check, marker

    def plan(self, games=1, comparison=False, design='independent', purpose='smoke'):
        candidate, candidate_check, marker = self.build()
        control, control_check = None, None
        if comparison:
            control, control_check, _ = self.build('v06')
        out = self.project / 'batch'
        batch.make_plan(SimpleNamespace(session=self.project / 'offline-session.json', candidate=candidate,
                                      candidate_check=candidate_check, control=control, control_check=control_check,
                                      games=games, design=design, purpose=purpose, seed=8123, out=out))
        args = SimpleNamespace(plan=out / 'plan.json', session=self.project / 'offline-session.json', timeout=5, delay=0)
        _, plan = batch.load_plan(args.plan)
        return args, plan, marker

    @staticmethod
    def replay(slot=None, marker=None, game_id=1234, graphics='offline map', ranks=None):
        slot = slot or {'seat': 0, 'seed': '123'}
        player, boss = slot['seat'], 1 - slot['seat']
        ranks = ranks if ranks is not None else ([0, 1] if player == 0 else [1, 0])
        scores = [10 if rank == min(ranks) else 5 for rank in ranks]
        return {'gameId': game_id, 'refereeInput': f"seed={slot['seed']}\n", 'ranks': ranks, 'scores': scores,
                'agents': [{'index': boss, 'agentId': 6740207, 'arenaboss': {'nickname': 'Boss 4'}},
                           {'index': player, 'agentId': -1, 'codingamer': {'pseudo': 'offline'}}],
                'frames': [{'view': ' 0\n' + json.dumps({'global': {'graphics': graphics}})},
                           {'agentId': player, 'stdout': 'WAIT' + (';MESSAGE ' + marker if marker else '')},
                           {'agentId': boss, 'stdout': 'WAIT', 'keyframe': True,
                            'view': ' 1\n' + json.dumps({'endScreen': [scores, 'logo', []]})}]}

    def finish(self, args, plan, marker, game_id=1234):
        raw = self.replay(plan['slots'][0], marker, game_id)
        self.transport.side_effect = [json.dumps({'gameId': game_id}), json.dumps(raw)]
        batch.run(args)
        return raw

    def fail_original_422(self, args):
        self.transport.side_effect = ApiError('HTTP 422; details redacted', status=422, uncertain=True)
        with self.assertRaises(ValueError):
            batch.run(args)
        self.transport.reset_mock()
        return args.plan.parent / '0001'

    def retry_args(self, args):
        amendment = self.project / 'amendment.md'
        amendment.write_bytes(b'Explicit authorization for one separate attempt.\r\nExploratory only.\r\n')
        return SimpleNamespace(plan=args.plan, slot=1, amendment=amendment)

    def test_retry_authorization_is_local_hash_bound_and_preserves_originals(self):
        args, plan, _ = self.plan()
        folder = self.fail_original_422(args)
        paths = [args.plan, folder / 'intent.json', folder / 'api-error.json']
        originals = [path.read_bytes() for path in paths]
        retry = self.retry_args(args)
        batch.authorize_retry(retry)
        self.transport.assert_not_called()
        authorization = batch.validate_retry_authorization(folder, plan, plan['slots'][0])
        self.assertEqual(retry.amendment.read_bytes(), (folder / 'retry-amendment.txt').read_bytes())
        self.assertEqual(batch.digest(retry.amendment.read_bytes()), authorization['amendmentSha256'])
        self.assertEqual(originals, [path.read_bytes() for path in paths])
        with self.assertRaises(ValueError):
            batch.authorize_retry(retry)
        self.assertFalse((folder / 'retry/intent.json').exists())

    def test_retry_authorization_refuses_any_known_original_outcome_or_previous_retry(self):
        args, _, _ = self.plan()
        folder = self.fail_original_422(args)
        retry = self.retry_args(args)
        for name in ('play-response.json', 'recovered-game.json', 'canonical.json',
                     'canonical-rejected-1.txt', 'result.json', 'retry-authorization.json', 'retry-amendment.txt'):
            path = folder / name
            path.write_text('{"gameId":1234}', encoding='utf-8')
            with self.subTest(name=name), self.assertRaises(ValueError):
                batch.authorize_retry(retry)
            path.unlink()
        (folder / 'retry').mkdir()
        with self.assertRaises(ValueError):
            batch.authorize_retry(retry)
        self.transport.assert_not_called()

    def test_retry_authorization_refuses_wrong_error_status_or_known_error_game_id(self):
        args, _, _ = self.plan()
        folder = self.fail_original_422(args)
        retry = self.retry_args(args)
        path = folder / 'api-error.json'
        original = batch.read_json(path)
        for altered in ({**original, 'status': 503}, {**original, 'uncertain': False},
                        {**original, 'details': {'gameId': 1234}}):
            path.write_text(json.dumps(altered), encoding='utf-8')
            with self.subTest(altered=altered), self.assertRaises(ValueError):
                batch.authorize_retry(retry)
        self.transport.assert_not_called()

    def test_retry_success_binds_second_response_and_report_is_exploratory(self):
        args, plan, _ = self.plan(comparison=True, design='matched', purpose='boss-screen')
        folder = self.fail_original_422(args)
        original_intent, original_error = (folder / 'intent.json').read_bytes(), (folder / 'api-error.json').read_bytes()
        batch.authorize_retry(self.retry_args(args))
        pending = batch.report(args)
        self.assertEqual('amended exploratory', pending['protocolStatus'])
        self.assertEqual(1, pending['attemptedRequests'])
        self.assertEqual(1, pending['authorizedRetries'])
        self.assertEqual(0, pending['completedRetryGames'])
        responses = []
        for index, slot in enumerate(plan['slots']):
            marker = batch.read_json(Path(plan['builds'][slot['arm']]['directory']) / 'manifest.json')['marker']
            responses.extend([json.dumps({'gameId': 1234 + index}),
                              json.dumps(self.replay(slot, marker, 1234 + index, graphics='same paired map'))])
        self.transport.side_effect = responses
        batch.run(args)
        result = batch.report(args)
        self.assertTrue(result['complete'])
        self.assertEqual(3, result['attemptedRequests'])
        self.assertEqual(2, result['completedGames'])
        self.assertEqual(1, result['completedRetryGames'])
        self.assertEqual(1, len(result['excludedAttempts']))
        self.assertNotIn('oneSidedP', result)
        self.assertEqual(1.0, result['exploratoryOneSidedP'])
        self.assertEqual('exploratory only; protocol amended', result['bossScreen'])
        self.assertEqual(2, batch.read_json(folder / 'result.json')['requestAttempt'])
        self.assertEqual(1234, batch.bound_game_id(folder, 2))
        self.assertFalse((folder / 'play-response.json').exists())
        self.assertEqual(original_intent, (folder / 'intent.json').read_bytes())
        self.assertEqual(original_error, (folder / 'api-error.json').read_bytes())
        self.transport.reset_mock()
        batch.run(args)
        self.transport.assert_not_called()

    def test_uncertain_second_attempt_never_repeats_and_private_body_is_separate(self):
        args, plan, _ = self.plan()
        folder = self.fail_original_422(args)
        retry = self.retry_args(args)
        batch.authorize_retry(retry)
        original_error = (folder / 'api-error.json').read_bytes()
        self.transport.side_effect = ApiError('HTTP 422; redacted', status=422, uncertain=True,
                                             response_bytes=b'private-second-attempt')
        with self.assertRaises(ValueError):
            batch.run(args)
        self.assertEqual(1, self.transport.call_count)
        self.assertEqual(original_error, (folder / 'api-error.json').read_bytes())
        private = self.project / 'build/cg-boss/private/api-errors' / plan['planId'] / '0001-retry.bin'
        self.assertEqual(b'private-second-attempt', private.read_bytes())
        self.assertEqual(2, batch.report(args)['attemptedRequests'])
        self.transport.reset_mock()
        with self.assertRaises(ValueError):
            batch.run(args)
        with self.assertRaises(ValueError):
            batch.authorize_retry(retry)
        self.transport.assert_not_called()

    def test_second_attempt_failed_canonical_lookup_resumes_lookup_only(self):
        args, plan, marker = self.plan()
        folder = self.fail_original_422(args)
        batch.authorize_retry(self.retry_args(args))
        self.transport.side_effect = [json.dumps({'gameId': 1234}), 'null']
        with self.assertRaises(ValueError):
            batch.run(args)
        self.assertEqual(1, len(list((folder / 'retry').glob('canonical-rejected-*'))))
        self.transport.reset_mock()
        self.transport.side_effect = [json.dumps(self.replay(plan['slots'][0], marker))]
        batch.run(args)
        self.assertEqual(['gameResult/findByGameId'], [c.args[1] for c in self.transport.call_args_list])
        self.assertTrue(batch.report(args)['complete'])

    def test_retry_recovery_binds_only_the_second_intent(self):
        args, plan, marker = self.plan()
        folder = self.fail_original_422(args)
        batch.authorize_retry(self.retry_args(args))
        self.transport.side_effect = ApiError('Offline unknown second response', uncertain=True)
        with self.assertRaises(ValueError):
            batch.run(args)
        self.transport.reset_mock()
        batch.recover(SimpleNamespace(plan=args.plan, slot=1, game_id=1234))
        self.transport.assert_not_called()
        self.assertFalse((folder / 'recovered-game.json').exists())
        self.assertEqual(2, batch.read_json(folder / 'retry/recovered-game.json')['requestAttempt'])
        self.transport.side_effect = [json.dumps(self.replay(plan['slots'][0], marker))]
        batch.run(args)
        self.assertEqual(['gameResult/findByGameId'], [c.args[1] for c in self.transport.call_args_list])

    def test_retry_tampering_blocks_before_network(self):
        args, _, _ = self.plan()
        folder = self.fail_original_422(args)
        batch.authorize_retry(self.retry_args(args))
        for name in ('intent.json', 'api-error.json', 'retry-amendment.txt', 'retry-authorization.json'):
            path = folder / name
            original = path.read_bytes()
            path.write_bytes(original + b' ')
            with self.subTest(name=name), self.assertRaises(ValueError):
                batch.run(args)
            path.write_bytes(original)
        self.transport.assert_not_called()

    def test_matched_schedule_is_reproducible_balanced_and_complete(self):
        slots = batch.make_schedule(40, 'matched', 42)
        self.assertEqual(slots, batch.make_schedule(40, 'matched', 42))
        self.assertEqual(list(range(1, 81)), [slot['slot'] for slot in slots])
        pair_seeds, first_arms, seats = [], [], []
        for first, second in zip(slots[::2], slots[1::2]):
            self.assertEqual({'candidate', 'control'}, {first['arm'], second['arm']})
            self.assertEqual((first['pair'], first['seat'], first['seed']),
                             (second['pair'], second['seat'], second['seed']))
            pair_seeds.append(first['seed']); first_arms.append(first['arm']); seats.append(first['seat'])
        self.assertEqual(40, len(set(pair_seeds)))
        self.assertEqual(20, first_arms.count('candidate'))
        self.assertEqual(20, seats.count(0))

    def test_http_error_body_stays_in_private_diagnostics(self):
        args, plan, _ = self.plan()
        secret = b'{"id":402,"message":"private diagnostic token example"}'
        self.transport.side_effect = ApiError('HTTP 422; details redacted', status=422,
                                             uncertain=True, response_bytes=secret, retry_after_seconds=600)
        with self.assertRaises(ValueError):
            batch.run(args)
        private = self.project / 'build/cg-boss/private/api-errors' / plan['planId'] / '0001.bin'
        self.assertEqual(secret, private.read_bytes())
        public = json.loads((args.plan.parent / '0001/api-error.json').read_bytes())
        self.assertEqual(402, public['apiCode'])
        self.assertEqual(600, public['retryAfterSeconds'])
        self.assertNotIn('private diagnostic token example', json.dumps(public))
        self.assertNotIn('private diagnostic token example', self.output.getvalue())

    def test_independent_and_single_build_schedules_never_reuse_seeds(self):
        for comparison in (True, False):
            slots = batch.make_schedule(21, 'independent', 5, comparison)
            self.assertEqual(42 if comparison else 21, len(slots))
            self.assertEqual(len(slots), len({slot['seed'] for slot in slots}))
            self.assertEqual({'candidate', 'control'} if comparison else {'candidate'}, {s['arm'] for s in slots})
        for size in (0, -1, 3001):
            with self.assertRaises(ValueError):
                batch.make_schedule(size, 'independent', 5)

    def test_out_of_order_agents_do_not_reorder_slot_ranks(self):
        for seat in (0, 1):
            slot = {'seat': seat, 'seed': '123'}
            for own_rank, other_rank, expected in ((0, 1, 'W'), (1, 0, 'L'), (0, 0, 'D'), (1, 2, 'W')):
                ranks = [own_rank, other_rank] if seat == 0 else [other_rank, own_rank]
                raw = self.replay(slot, ranks=ranks)
                for agents in (raw['agents'], list(reversed(raw['agents']))):
                    raw['agents'] = agents
                    result = batch.outcome(raw, 1234, slot, 6740207)
                    self.assertEqual(expected, result['result'])
                    self.assertEqual(seat, result['seat'])

    def test_malformed_agents_are_rejected(self):
        raw = self.replay()
        invalid = [[], [raw['agents'][0]], [raw['agents'][0]] * 2, [None, raw['agents'][1]]]
        for index in (True, '1', None, 2):
            agents = copy.deepcopy(raw['agents']); agents[0]['index'] = index; invalid.append(agents)
        for agents in invalid:
            with self.subTest(agents=agents), self.assertRaises(ValueError):
                batch.outcome({**raw, 'agents': agents}, 1234, {'seat': 0, 'seed': '123'}, 6740207)

    def test_malformed_ranks_and_unfinished_replay_are_rejected(self):
        for ranks in (None, [], [0], [0, 1, 2], [False, 1], ['0', 1], [0.0, 1], [-1, 0], [0, 3]):
            raw = self.replay(); raw['ranks'] = ranks
            with self.subTest(ranks=ranks), self.assertRaises(ValueError):
                batch.outcome(raw, 1234, {'seat': 0, 'seed': '123'}, 6740207)
        for ending in (None, [], [[]], [None], [[10]], [[10, 5, 0]]):
            raw = self.replay()
            raw['frames'][-1]['view'] = ' 1\n' + json.dumps({'endScreen': ending})
            with self.subTest(ending=ending), self.assertRaises(ValueError):
                batch.outcome(raw, 1234, {'seat': 0, 'seed': '123'}, 6740207)

    def test_wrong_game_seat_boss_and_seed_cannot_match_a_slot(self):
        for change in ('game', 'seat', 'boss', 'seed'):
            raw = self.replay()
            if change == 'game': raw['gameId'] = 999
            if change == 'seat':
                for agent in raw['agents']: agent['index'] = 1 - agent['index']
            if change == 'boss': raw['agents'][0]['agentId'] = 99
            if change == 'seed': raw['refereeInput'] = 'seed=124\n'
            with self.subTest(change=change), self.assertRaises(ValueError):
                batch.outcome(raw, 1234, {'seat': 0, 'seed': '123'}, 6740207)

    def test_map_identity_hashes_the_exact_graphics_string(self):
        raw = self.replay(graphics='3|1|unchanged map ')
        self.assertEqual(batch.digest('3|1|unchanged map '), batch.map_hash(raw))
        self.assertNotEqual(batch.map_hash(raw), batch.map_hash(self.replay(graphics='3|1|unchanged map')))
        changed = copy.deepcopy(raw); changed['frames'][-1]['stdout'] = 'MESSAGE outcome changed'
        self.assertEqual(batch.map_hash(raw), batch.map_hash(changed))

    @staticmethod
    def sample_record(game_id, arm, pair=1, seat=0, map_hash='map-a'):
        return {'slot': {'pair': pair, 'arm': arm},
                'outcome': {'gameId': game_id, 'seat': seat, 'mapHash': map_hash}}

    def test_matched_map_and_seat_are_required_but_same_pair_map_is_allowed(self):
        plan = {'design': 'matched', 'excludedMapHashes': []}
        records = [self.sample_record(1, 'candidate'), self.sample_record(2, 'control')]
        batch.check_sample(plan, records)
        for field, value in (('mapHash', 'map-b'), ('seat', 1)):
            invalid = copy.deepcopy(records); invalid[1]['outcome'][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                batch.check_sample(plan, invalid)

    def test_map_and_game_reuse_cannot_silently_count_as_fresh_samples(self):
        baseline = self.sample_record(1, 'candidate')
        for design, other in [('independent', self.sample_record(2, 'control')),
                              ('matched', self.sample_record(2, 'control', pair=2)),
                              ('matched', self.sample_record(2, 'candidate')),
                              ('matched', self.sample_record(1, 'control'))]:
            with self.subTest(design=design, other=other), self.assertRaises(ValueError):
                batch.check_sample({'design': design, 'excludedMapHashes': []}, [baseline, other])
        with self.assertRaises(ValueError):
            batch.check_sample({'design': 'matched', 'excludedMapHashes': ['map-a']}, [baseline])

    def test_plan_is_local_immutable_and_detects_schedule_tampering(self):
        args, plan, _ = self.plan()
        self.transport.assert_not_called()
        original = args.plan.read_bytes()
        batch.write_once(args.plan, plan)
        self.assertEqual(original, args.plan.read_bytes())
        altered = copy.deepcopy(plan); altered['slots'][0]['seed'] = '999'
        with self.assertRaises(ValueError): batch.write_once(args.plan, altered)
        args.plan.write_text(json.dumps(altered), encoding='utf-8')
        with self.assertRaises(ValueError): batch.load_plan(args.plan)

    def test_changed_executable_or_session_is_rejected_before_api(self):
        args, plan, _ = self.plan()
        executable = Path(plan['builds']['candidate']['directory']) / 'classes/Player.class'
        original = executable.read_bytes()
        executable.write_bytes(b'changed class')
        with self.assertRaises(ValueError): batch.run(args)
        self.transport.assert_not_called()
        executable.write_bytes(original)
        self.session.play_template[1]['multi']['agentsIds'][1] = 99
        with self.assertRaisesRegex(ValueError, 'Session template differs'): batch.run(args)
        self.transport.assert_not_called()

    def test_incomplete_process_check_cannot_authorize_batch_play(self):
        artifact, check, _ = self.build()
        original = batch.read_json(check)
        batch.artifact(artifact, check)
        invalid = []
        no_schema = copy.deepcopy(original); del no_schema['schemaVersion']; invalid.append(no_schema)
        no_games = copy.deepcopy(original); no_games['games'] = []; invalid.append(no_games)
        for field in ('result', 'timings', 'timeouts', 'nominalOverruns', 'player'):
            missing = copy.deepcopy(original); del missing['games'][0][field]; invalid.append(missing)
        no_error = copy.deepcopy(original); del no_error['games'][0]['result']['error']; invalid.append(no_error)
        failed = copy.deepcopy(original); failed['games'][0]['timings'][0]['status'] = 'TIMEOUT'; invalid.append(failed)
        for case in invalid:
            check.write_text(json.dumps(case), encoding='utf-8')
            with self.subTest(case=case), self.assertRaises(ValueError): batch.artifact(artifact, check)
        self.transport.assert_not_called()

    def test_orphan_response_blocks_play_when_original_intent_is_missing(self):
        args, _, _ = self.plan()
        folder = args.plan.parent / '0001'; folder.mkdir()
        (folder / 'play-response.json').write_text(json.dumps({'gameId': 1234}), encoding='utf-8')
        with self.assertRaises(ValueError): batch.run(args)
        self.transport.assert_not_called()
        self.assertFalse((folder / 'intent.json').exists())

    def test_uncertain_play_is_never_automatically_repeated(self):
        args, _, _ = self.plan()
        self.transport.side_effect = ApiError('Offline uncertain failure', uncertain=True)
        with self.assertRaises(ValueError): batch.run(args)
        self.assertEqual('TestSession/play', self.transport.call_args.args[1])
        self.assertTrue((args.plan.parent / '0001/intent.json').is_file())
        self.assertFalse((args.plan.parent / '0001/play-response.json').exists())
        self.transport.reset_mock()
        with self.assertRaises(ValueError): batch.run(args)
        self.transport.assert_not_called()

    def test_failed_canonical_lookup_resumes_lookup_without_another_play(self):
        args, plan, marker = self.plan()
        raw = self.replay(plan['slots'][0], marker)
        self.transport.side_effect = [json.dumps({'gameId': 1234}), ApiError('Offline lookup failed')]
        with self.assertRaises(ApiError): batch.run(args)
        self.assertEqual(['TestSession/play', 'gameResult/findByGameId'], [c.args[1] for c in self.transport.call_args_list])
        self.transport.reset_mock(); self.transport.side_effect = [json.dumps(raw)]
        batch.run(args)
        self.assertEqual(['gameResult/findByGameId'], [c.args[1] for c in self.transport.call_args_list])
        self.assertTrue(batch.report(args)['complete'])
        self.transport.reset_mock(); self.transport.side_effect = AssertionError('Completed run cannot play again')
        batch.run(args)
        self.transport.assert_not_called()

    def test_invalid_lookup_response_is_preserved_and_only_lookup_retried(self):
        args, plan, marker = self.plan()
        self.transport.side_effect = [json.dumps({'gameId': 1234}), 'null']
        with self.assertRaises(ValueError): batch.run(args)
        folder = args.plan.parent / '0001'
        self.assertFalse((folder / 'canonical.json').exists())
        rejected = list(folder.glob('canonical-rejected-*.txt'))
        self.assertEqual(1, len(rejected))
        self.assertEqual('null', rejected[0].read_text(encoding='utf-8'))
        self.transport.reset_mock(); self.transport.side_effect = [json.dumps(self.replay(plan['slots'][0], marker))]
        batch.run(args)
        self.assertEqual(['gameResult/findByGameId'], [c.args[1] for c in self.transport.call_args_list])

    def test_incomplete_canonical_never_locks_capture_and_only_lookup_is_retried(self):
        args, plan, marker = self.plan()
        complete = self.replay(plan['slots'][0], marker)
        no_end = copy.deepcopy(complete); del no_end['frames'][-1]['view']
        no_ranks = copy.deepcopy(complete); del no_ranks['ranks']
        no_map = copy.deepcopy(complete); no_map['frames'][0]['view'] = ' 0\n{}'
        no_seed = copy.deepcopy(complete); no_seed['refereeInput'] = None
        self.transport.side_effect = [json.dumps({'gameId': 1234}), json.dumps(no_end),
                                      json.dumps(no_ranks), json.dumps(no_map), json.dumps(no_seed), json.dumps(complete)]
        folder = args.plan.parent / '0001'
        for attempt in range(1, 5):
            with self.assertRaises(ValueError): batch.run(args)
            self.assertFalse((folder / 'canonical.json').exists())
            self.assertFalse((folder / 'result.json').exists())
            self.assertFalse((self.project / 'replays/cg/boss/_metadata/1234.json').exists())
            self.assertEqual([], list((self.project / 'replays/cg/boss').rglob('1234.json')))
            self.assertEqual(attempt, len(list(folder.glob('canonical-rejected-*.txt'))))
        batch.run(args)
        operations = [call.args[1] for call in self.transport.call_args_list]
        self.assertEqual(1, operations.count('TestSession/play'))
        self.assertEqual(5, operations.count('gameResult/findByGameId'))
        self.assertTrue((folder / 'result.json').is_file())

    def test_manual_recovery_only_looks_up_the_recorded_created_game(self):
        args, plan, marker = self.plan()
        self.transport.side_effect = ApiError('Offline uncertain failure', uncertain=True)
        with self.assertRaises(ValueError): batch.run(args)
        self.transport.reset_mock()
        batch.recover(SimpleNamespace(plan=args.plan, slot=1, game_id=1234))
        self.transport.assert_not_called()
        self.transport.side_effect = [json.dumps(self.replay(plan['slots'][0], marker))]
        batch.run(args)
        self.assertEqual(['gameResult/findByGameId'], [c.args[1] for c in self.transport.call_args_list])

    def test_recovery_cannot_replace_a_known_play_response_game(self):
        args, _, _ = self.plan()
        self.transport.side_effect = [json.dumps({'gameId': 1234}), ApiError('Offline lookup failure')]
        with self.assertRaises(ApiError): batch.run(args)
        self.transport.reset_mock()
        with self.assertRaises(ValueError):
            batch.recover(SimpleNamespace(plan=args.plan, slot=1, game_id=9999))
        self.transport.assert_not_called()
        self.assertFalse((args.plan.parent / '0001/recovered-game.json').exists())

    def test_matched_batch_reports_only_after_both_bound_captures_complete(self):
        args, plan, _ = self.plan(comparison=True, design='matched', purpose='boss-screen')
        before = batch.report(args)
        self.assertFalse(before['complete'])
        self.assertNotIn('oneSidedP', before)
        responses = []
        for index, slot in enumerate(plan['slots']):
            build = plan['builds'][slot['arm']]
            marker = batch.read_json(Path(build['directory']) / 'manifest.json')['marker']
            raw = self.replay(slot, marker, 1234 + index, graphics='identical paired map')
            responses.extend([json.dumps({'gameId': 1234 + index}), json.dumps(raw)])
        self.transport.side_effect = responses
        batch.run(args)
        result = batch.report(args)
        self.assertTrue(result['complete'])
        self.assertEqual({'candidateOnlyWin': 0, 'controlOnlyWin': 0}, result['discordances'])
        self.assertEqual(1.0, result['oneSidedP'])
        self.assertEqual(1, result['arms']['candidate']['games'])
        self.assertEqual(1, result['arms']['control']['games'])

    def test_unknown_marker_interrupts_resume_before_any_additional_play(self):
        args, plan, _ = self.plan(games=2)
        raw = self.replay(plan['slots'][0])
        self.transport.side_effect = [json.dumps({'gameId': 1234}), json.dumps(raw)]
        with self.assertRaises(ValueError): batch.run(args)
        self.assertTrue((args.plan.parent / '0001/result.json').is_file())
        self.transport.reset_mock()
        with self.assertRaises(ValueError): batch.run(args)
        self.transport.assert_not_called()
        self.assertFalse((args.plan.parent / '0002/intent.json').exists())

    def test_foreign_intent_cannot_resume_a_different_plan(self):
        args, _, _ = self.plan()
        self.transport.side_effect = ApiError('Offline uncertain failure', uncertain=True)
        with self.assertRaises(ValueError): batch.run(args)
        intent = args.plan.parent / '0001/intent.json'
        original = batch.read_json(intent)
        self.transport.reset_mock()
        for key, replacement in (('planId', 'foreign-plan'), ('slot', {**original['slot'], 'seed': '999'}),
                                 ('sourceSha256', 'foreign-source')):
            value = copy.deepcopy(original); value[key] = replacement
            intent.write_text(json.dumps(value), encoding='utf-8')
            with self.subTest(key=key), self.assertRaises(ValueError):
                batch.recover(SimpleNamespace(plan=args.plan, slot=1, game_id=1234))
        self.transport.assert_not_called()

    def test_result_outcome_and_canonical_bytes_are_revalidated(self):
        args, plan, marker = self.plan()
        self.finish(args, plan, marker)
        result_path = args.plan.parent / '0001/result.json'
        original = result_path.read_bytes()
        record = batch.read_json(result_path); record['outcome']['result'] = 'L'
        result_path.write_text(json.dumps(record), encoding='utf-8')
        with self.assertRaises(ValueError): batch.result_records(args.plan.parent, plan)
        result_path.write_bytes(original)
        canonical = self.project / record['capture']['path']
        canonical.write_bytes(canonical.read_bytes() + b'\n')
        with self.assertRaises(ValueError): batch.result_records(args.plan.parent, plan)

    def test_play_response_must_identify_one_game(self):
        self.assertEqual(1234, batch.find_game_id({'game': {'gameId': 1234}, 'again': [{'gameId': 1234}]}))
        for raw in ({}, {'gameId': True}, {'gameId': -1}, [{'gameId': 1234}, {'gameId': 5678}]):
            with self.subTest(raw=raw), self.assertRaises(ValueError): batch.find_game_id(raw)


if __name__ == '__main__':
    unittest.main()
