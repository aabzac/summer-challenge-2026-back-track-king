"""Synthetic local diagnostic evidence tests; never launches Java or bot games."""
import contextlib
import copy
import io
import json
from pathlib import Path
import shutil
import unittest
from unittest.mock import patch
import uuid

import run_budget100_local_v1 as study


class Budget100LocalTest(unittest.TestCase):
    def setUp(self):
        self.parent = Path(__file__).resolve().parents[1] / 'build/test-budget100-local-v1'
        self.root = self.parent / ('case-' + uuid.uuid4().hex)
        self.run, self.bench = self.root / 'analysis', self.root / 'bench'
        self.run.mkdir(parents=True)
        self.addCleanup(self.cleanup)
        self.addCleanup(patch.stopall)
        patch.object(study, 'ROOT', self.root).start()
        patch.object(study, 'RUN', self.run).start()
        patch.object(study, 'BENCH', self.bench).start()
        self.process = patch.object(study.subprocess, 'Popen', side_effect=AssertionError('No measured subprocess in tests')).start()
        java = self.root / 'java.exe'
        java.write_bytes(b'fake runtime, never executed')
        cls = self.root / 'classes/Only.class'
        cls.parent.mkdir()
        cls.write_bytes(b'pinned fake class')
        exclusion = self.root / 'excluded.json'
        study.save(exclusion, {'gamesPerMap': {'f' * 64: 1}})
        self.blocks, preflight = [], []
        number = 1
        for name, games, pairs, offset in study.BLOCKS:
            seed = study.BASE_SEED + offset
            path = self.root / 'pools' / (name + '.json')
            study.save(path, {'schemaVersion': 1, 'id': study.NAME + '-' + name, 'purpose': 'development',
                             'generator': 'cg-sha1prng', 'seedStart': seed, 'pairs': pairs})
            hashes = [f'{n:064x}' for n in range(number, number + pairs)]
            number += pairs
            seeds = list(range(seed, seed + pairs))
            self.blocks.append({'name': name, 'games': games, 'pairs': pairs, 'seedStart': seed,
                                'seeds': seeds, 'mapHashes': hashes, 'pool': study.pin(path)})
            preflight.append({'manifest': study.pin(path), 'maps': [
                {'seed': s, 'coreMapHash': h} for s, h in zip(seeds, hashes)]})
        map_path = self.root / 'map-audit.json'
        study.save(map_path, {'passed': True, 'complete': True, 'newGames': 0, 'botCalls': 0,
                              'distinctProspectiveMaps': 500, 'excludedMaps': 1,
                              'inputs': [study.pin(exclusion)], 'pools': preflight})
        params = {'NOISE': '0.1', 'USE_BATCHED_REFRESH': 'true', 'USE_ROLLOUT_POLICY': 'true'}
        self.frozen = {'experiment': study.NAME, 'preflightPassed': True, 'localOnly': True, 'promotion': False,
                       'candidate': {'spec': study.SPEC, 'params': dict(params)},
                       'control': {'spec': study.SPEC, 'params': dict(params)},
                       'budgets': dict(study.BUDGETS), 'threads': 2,
                       'files': {p.relative_to(self.root).as_posix(): study.sha(p) for p in self.root.rglob('*')
                                 if p.is_file() and p != java},
                       'runtimeFiles': {str(java): study.sha(java)},
                       'javaCommandPrefix': [str(java), '-cp', str(cls.parent)],
                       'strength': {'baseSeed': study.BASE_SEED, 'prospectiveMapCount': 500,
                                    'blocks': self.blocks, 'excludedMaps': study.pin(exclusion),
                                    'mapPreflight': study.pin(map_path)}}
        study.save(self.run / 'preflight-freeze.json', self.frozen)

    def cleanup(self):
        if self.root.resolve().parent != self.parent.resolve():
            raise AssertionError('Synthetic cleanup escaped intended workspace')
        shutil.rmtree(self.root)

    @staticmethod
    def counters(calls=1):
        keys = ('calls totalNs maxNs above36Ms above50Ms above100Ms aboveAllowance rolloutObservedCalls '
                'routeQueries completedComparisons simulatedTurns ordinarySearchComplete networkPhaseComplete '
                'selectedOpening selectedNetwork networkSearchStarted networkEligible networkEvaluated '
                'networkRoots networkComparisons').split()
        row = dict.fromkeys(keys, 0)
        row.update(calls=calls, totalNs=calls, maxNs=int(calls > 0), rolloutObservedCalls=calls)
        return row

    def report(self, index=0, wins=None, draws=0):
        block = self.blocks[index]
        games = block['games']
        wins = games // 2 if wins is None else wins
        pairs, observations = [], []
        for i, (seed, h) in enumerate(zip(block['seeds'], block['mapHashes'])):
            pair = {'index': i, 'seed': seed, 'mapHash': h}
            observed = {'index': i, 'seed': seed}
            for seat, key in enumerate(('aAsP0', 'aAsP1')):
                n = 2 * i + seat
                winner = seat if n < wins else -1 if n < wins + draws else 1 - seat
                scores = [0, 0] if winner == -1 else [int(winner == 0), int(winner == 1)]
                pair[key] = {'winner': winner, 'score': scores, 'error': [None, None], 'turns': 2}
                observed[key] = {side: {phase: self.counters() for phase in ('first', 'later')} for side in ('a', 'b')}
            pairs.append(pair)
            observations.append(observed)
        total = self.counters(games)
        total.update(meanNativePlayMs=0.000001, maxNativePlayMs=0.000001, meanRouteQueries=0.0,
                     meanCompletedComparisons=0.0, meanSimulatedTurns=0.0)
        return {'timingProfile': 'LOCAL_DIAGNOSTIC', 'experimentalTimingOnly': True, 'localOnly': True,
                'promotion': False, 'promotionEligible': False, 'complete': True,
                'promotionGatePassed': False, 'processReadinessAssessed': False, 'practicalDirectStage': 'NOT_APPLICABLE',
                'status': 'COMPLETE_LOCAL_DIAGNOSTIC', 'plannedGames': games,
                'a': study.SPEC, 'b': study.SPEC, 'paramsA': self.frozen['candidate']['params'],
                'paramsB': self.frozen['control']['params'], 'threads': 2,
                'firstSearchBudgetMsA': 486, 'firstSearchBudgetMsB': 486,
                'turnSearchBudgetMsA': 100, 'turnSearchBudgetMsB': 36,
                'games': games, 'baseSeed': block['seedStart'], 'errorsA': 0, 'errorsB': 0,
                'winsA': wins, 'winsB': games - wins - draws, 'draws': draws,
                'excludedReports': {str(self.root / 'excluded.json'): self.frozen['strength']['excludedMaps']['sha256']},
                'pairs': pairs, 'gamesPerMap': dict.fromkeys(block['mapHashes'], 2),
                'pairObservations': observations,
                'observations': {side: {phase: dict(total) for phase in ('first', 'later')} for side in ('a', 'b')}}

    def inspect(self, report, index=0):
        path = self.root / 'synthetic-report.json'
        path.write_text(json.dumps(report), encoding='utf-8')
        return study.inspect_report(path, self.frozen, index)

    def test_complete_freeze_with500_distinct_core_verified_maps_passes(self):
        self.assertIs(study.validate_freeze(self.frozen), self.frozen)
        self.assertEqual(study.load_freeze(), self.frozen)
        self.process.assert_not_called()

    def test_wrong_budget_policy_workers_or_strategy_difference_rejected(self):
        for key, value in [('budgets', {**study.BUDGETS, 'laterA': 36}), ('threads', 1),
                           ('localOnly', False), ('promotion', True), ('preflightPassed', False)]:
            changed = copy.deepcopy(self.frozen)
            changed[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                study.validate_freeze(changed)
        changed = copy.deepcopy(self.frozen)
        changed['candidate']['params']['NOISE'] = '0'
        with self.assertRaisesRegex(ValueError, 'recipes'):
            study.validate_freeze(changed)

    def test_incomplete_duplicate_overlapping_or_core_mismatched_maps_rejected(self):
        for change in ('short', 'duplicate', 'prior', 'core'):
            changed = copy.deepcopy(self.frozen)
            hashes = changed['strength']['blocks'][2]['mapHashes']
            if change == 'short': hashes.pop()
            if change == 'duplicate': hashes[0] = changed['strength']['blocks'][0]['mapHashes'][0]
            if change == 'prior': hashes[0] = 'f' * 64
            if change == 'core': hashes[0] = 'e' * 64
            with self.subTest(change=change), self.assertRaises(ValueError):
                study.validate_freeze(changed)

    def test_runtime_mutation_and_unpinned_extra_class_rejected(self):
        extra = self.root / 'classes/Unexpected.class'
        extra.write_bytes(b'new class')
        with self.assertRaisesRegex(ValueError, 'Unpinned classpath'):
            study.validate_freeze(self.frozen)
        extra.unlink()
        (self.root / 'java.exe').write_bytes(b'changed runtime')
        with self.assertRaisesRegex(ValueError, 'runtime changed'):
            study.validate_freeze(self.frozen)

    def test_raw_reports_validate_all_pairs_and_work_counters(self):
        report = self.inspect(self.report())
        self.assertEqual((report['wins'], report['losses'], report['draws']), (150, 150, 0))
        self.assertEqual(study.assess([report])['status'], 'CONTINUE')

    def test_hidden_errors_incomplete_status_wrong_budgets_and_totals_rejected(self):
        for key, value in [('complete', False), ('status', 'INCOMPLETE_OR_ERRORS'), ('plannedGames', 299),
                           ('failure', 'retained error'), ('turnSearchBudgetMsA', 36), ('turnSearchBudgetMsB', 100),
                           ('firstSearchBudgetMsA', 100), ('threads', 3), ('winsA', 151), ('errorsA', 1),
                           ('timingProfile', 'CG'), ('promotionEligible', True)]:
            report = self.report()
            report[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.inspect(report)
        report = self.report()
        report['pairs'][0]['aAsP0']['error'][1] = 'bad command'
        with self.assertRaisesRegex(ValueError, 'raw game error'):
            self.inspect(report)

    def test_raw_seed_winner_score_and_duplicate_map_rejected(self):
        for mode in ('seed', 'map', 'winner', 'score'):
            report = self.report()
            if mode == 'seed': report['pairs'][0]['seed'] += 1
            if mode == 'map': report['pairs'][0]['mapHash'] = report['pairs'][1]['mapHash']
            if mode == 'winner': report['pairs'][0]['aAsP0']['winner'] = 1
            if mode == 'score': report['pairs'][0]['aAsP0']['score'] = [True, 0]
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                self.inspect(report)

    def test_observation_coverage_and_aggregate_means_cannot_be_invented(self):
        for mode in ('missing', 'calls', 'aggregate', 'nan'):
            report = self.report()
            if mode == 'missing': report['pairObservations'].pop()
            if mode == 'calls': report['pairObservations'][0]['aAsP0']['a']['later']['calls'] = 2
            if mode == 'aggregate': report['observations']['a']['later']['routeQueries'] = 1
            if mode == 'nan': report['observations']['a']['later']['meanNativePlayMs'] = float('nan')
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                self.inspect(report)

    def test_exact_fifty_continues_but_one_half_win_below_stops(self):
        for index, cumulative in [(0, 300), (1, 500)]:
            reports = [self.inspect(self.report(i), i) for i in range(index + 1)]
            self.assertEqual(study.assess(reports)['status'], 'CONTINUE')
            reports[-1]['halfWins'] -= 1
            self.assertEqual(study.assess(reports)['status'], 'STOP_FUTILITY_' + str(cumulative))

    def test_full1000_is_diagnostic_at_any_score_and_no_continuation_after_futility(self):
        reports = [self.inspect(self.report(i), i) for i in range(3)]
        verdict = study.assess(reports)
        self.assertEqual(verdict['status'], 'COMPLETE_DIAGNOSTIC')
        self.assertEqual(verdict['games'], 1000)
        self.assertFalse(verdict['promotionEligible'])
        reports[0]['halfWins'] -= 1
        with self.assertRaisesRegex(ValueError, 'futility stop'):
            study.assess(reports)

    def test_existing_command_even_without_result_blocks_any_retry(self):
        path = self.bench / 'block1-command.json'
        study.save(path, {'unknownMeasurement': True})
        with self.assertRaisesRegex(ValueError, 'already attempted'):
            study.ensure_unstarted()
        with self.assertRaisesRegex(ValueError, 'already attempted'):
            study.run_block(self.frozen, 0)
        self.process.assert_not_called()

    def test_completed_report_and_summary_are_write_once(self):
        target = self.run / 'strength-summary.json'
        study.save(target, {'status': 'INCOMPLETE'})
        with self.assertRaises(FileExistsError):
            study.save(target, {'status': 'replacement'})
        with self.assertRaisesRegex(ValueError, 'Preserve'):
            study.ensure_unstarted()

    def test_strength_stops_after_first_futility_without_process_or_boss(self):
        report = self.inspect(self.report(wins=149))
        with patch.object(study, 'load_freeze', return_value=self.frozen), \
                patch.object(study, 'run_block', return_value=report) as run, \
                contextlib.redirect_stdout(io.StringIO()):
            result = study.strength_stage()
        run.assert_called_once_with(self.frozen, 0)
        self.assertEqual(result['status'], 'STOP_FUTILITY_300')
        self.assertEqual(result['processStage'], 'NOT_APPLICABLE_LOCAL_DIAGNOSTIC')
        self.assertEqual(result['bossStage'], 'NOT_REQUESTED')
        self.process.assert_not_called()

    def test_failed_measured_block_is_retained_incomplete_and_cannot_restart(self):
        def fail(*_args):
            study.save(self.bench / 'block1-command.json', {'attempted': True})
            raise ValueError('retained incomplete measurement')
        with patch.object(study, 'load_freeze', return_value=self.frozen), patch.object(study, 'run_block', side_effect=fail):
            with self.assertRaisesRegex(ValueError, 'incomplete measurement'):
                study.strength_stage()
        result = study.read(self.run / 'strength-summary.json')
        self.assertFalse(result['complete'])
        self.assertEqual(result['status'], 'INCOMPLETE')
        with self.assertRaises(ValueError):
            study.ensure_unstarted()


if __name__ == '__main__':
    unittest.main()
