"""Offline checks of fixed-stage orchestration; no Gradle, games, API or power calls."""
import copy
from contextlib import nullcontext
import hashlib
import json
from pathlib import Path
import shutil
import unittest
from unittest.mock import patch
import uuid

import run_strategy_search_experiments as runner
from test_boss_practical_gate import cached_fixture, local_fixture
from diagnostics import strategy_control_compatibility as compatibility


def digest(text):
    return hashlib.sha256(text.encode()).hexdigest()


class StrategySearchExperimentsTest(unittest.TestCase):
    def setUp(self):
        self.test_root = Path(__file__).resolve().parents[1] / 'build/test-strategy-search-experiments'
        self.root = self.test_root / ('case-' + uuid.uuid4().hex)
        self.root.mkdir(parents=True)
        self.addCleanup(self.cleanup)
        self.folder = self.root / 'build/bench/strategy-search-v1'
        for name, value in (('ROOT', self.root), ('FOLDER', self.folder)):
            patcher = patch.object(runner, name, value)
            patcher.start()
            self.addCleanup(patcher.stop)

    def cleanup(self):
        if self.root.resolve().parent != self.test_root.resolve():
            raise AssertionError('Test cleanup escaped workspace directory')
        shutil.rmtree(self.root)

    def write(self, path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value), encoding='utf-8')
        return path

    def local_report(self, variant='rhea', stage='screen', units=330):
        data = local_fixture(runner.STAGES[stage][0] * 2, units,
                             start=runner.VARIANTS[variant][stage])
        data['a'] = runner.spec(variant)
        data['paramsA'] = {runner.VARIANTS[variant]['flag']: 'true'}
        data['paramsB'] = {runner.VARIANTS[variant]['flag']: 'false'}
        data['poolId'] = runner.experiment_name(variant) + '-' + stage
        data['poolSha256'] = digest(variant + stage)
        directory = self.folder / variant
        excluded = self.write(directory / (stage + '-excluded.json'), {'gamesPerMap': {digest('old'): 2}})
        data['excludedReports'] = {str(excluded.relative_to(self.root)).replace('/', '\\'): runner.sha(excluded)}
        path = self.write(directory / (stage + '.json'), data)
        report = runner.read_report(path, data['games'])
        manifest = {'threads': 2, 'resolvedParameters': {runner.spec(variant): data['paramsA'], 'v07': data['paramsB']},
                    'poolHashes': {variant: {stage: data['poolSha256']}}}
        return path, data, report, manifest

    def test_history_covers_nested_diagnostics_and_prior_strategy_experiments(self):
        first, second, third = (digest(value) for value in ('first', 'second', 'third'))
        self.write(self.root / 'build/bench/old/report.json', {'gamesPerMap': {first: 2}})
        self.write(self.root / 'build/analysis/audit.json', {'nested': [{'mapHash': second, 'seed': '77'}]})
        self.write(self.root / 'docs/evidence/strategy-search-prior-v0.json',
                   {'reports': [{'maps': {third: {'seed': 78}}}]})
        self.write(self.folder / 'rhea/screen.json', {'gamesPerMap': {digest('own'): 2}})
        self.write(self.root / 'docs/evidence/strategy-search-rhea-v1-screen.json',
                   {'gamesPerMap': {digest('own'): 2}})
        maps, seeds, files = runner.history()
        self.assertEqual({first, second, third}, maps)
        self.assertEqual({77, 78}, seeds)
        self.assertEqual(3, len(files))

    def test_history_refuses_malformed_or_unidentifiable_played_reports(self):
        path = self.write(self.root / 'build/analysis/report.json', {'games': 2, 'a': 'current', 'b': 'v07'})
        with self.assertRaisesRegex(ValueError, 'lacks map identities'):
            runner.history()
        path.write_text('{incomplete', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Unreadable historical JSON'):
            runner.history()

    def test_pools_refuse_previous_seeds_and_any_reserved_overlap(self):
        runner.validate_pools(set(), [('adjacent', 499999, 1)])
        with self.assertRaisesRegex(ValueError, 'already used'):
            runner.validate_pools({510149}, [])
        for pool in [('same', 500000, 150), ('straddles', 500149, 2), ('contains', 490000, 30000)]:
            with self.subTest(pool=pool), self.assertRaisesRegex(ValueError, 'overlaps'):
                runner.validate_pools(set(), [pool])
        self.assertEqual(1300, sum(runner.STAGES[stage][0] for _ in runner.VARIANTS for stage in runner.STAGES))

    def test_only_independent_single_flag_is_allowed(self):
        baseline = {'USE_ROLLOUT_RHEA': 'false', 'USE_PROTECTED_OPENING': 'false', 'OTHER': '7'}
        params = {'v07': baseline, 'current': baseline.copy()}
        for variant, settings in runner.VARIANTS.items():
            params[runner.spec(variant)] = dict(baseline, **{settings['flag']: 'true'})
        runner.validate_parameters(params)
        changed = copy.deepcopy(params)
        changed[runner.spec('rhea')]['USE_PROTECTED_OPENING'] = 'true'
        with self.assertRaisesRegex(ValueError, 'exactly its independent flag'):
            runner.validate_parameters(changed)
        changed = copy.deepcopy(params)
        changed['current']['OTHER'] = '8'
        with self.assertRaisesRegex(ValueError, 'Disabled current'):
            runner.validate_parameters(changed)

    def test_boss_map_hashes_require_full_canonical_provenance(self):
        games = [{'gameId': i + 1, 'seed': str(i + 1000), 'rawSha256': digest('raw' + str(i))}
                 for i in range(40)]
        cache = {'sourcePlan': {'sha256': digest('plan')}, 'games': games}
        value = {'hashScheme': runner.LOCAL_HASH_SCHEME, 'sourcePlanSha256': digest('plan'),
                 'maps': [dict(game, mapHash=digest('map' + str(game['gameId']))) for game in games]}
        path = self.write(self.root / 'boss-maps.json', value)
        self.assertEqual(value, runner.validate_boss_maps(path, cache))
        for field, replacement in [('hashScheme', 'cg-global-graphics-utf8-sha256-v1'),
                                   ('sourcePlanSha256', digest('other'))]:
            changed = dict(value, **{field: replacement})
            self.write(path, changed)
            with self.subTest(field=field), self.assertRaises(ValueError):
                runner.validate_boss_maps(path, cache)
        changed = copy.deepcopy(value)
        changed['maps'][0]['rawSha256'] = digest('tampered')
        self.write(path, changed)
        with self.assertRaisesRegex(ValueError, 'provenance'):
            runner.validate_boss_maps(path, cache)
        changed = copy.deepcopy(value)
        changed['maps'][0]['mapHash'] = changed['maps'][1]['mapHash']
        self.write(path, changed)
        with self.assertRaisesRegex(ValueError, 'not unique'):
            runner.validate_boss_maps(path, cache)

    def test_report_checks_complete_seed_interval_params_and_windows_exclusion_paths(self):
        path, data, report, manifest = self.local_report()
        self.assertEqual(report['sha256'], runner.verify_report(manifest, 'rhea', 'screen')['sha256'])
        for field, value in [('threads', 1), ('paramsA', {'USE_ROLLOUT_RHEA': 'false'}),
                             ('poolSha256', digest('other')), ('excludedReports', {})]:
            changed = copy.deepcopy(data)
            changed[field] = value
            self.write(path, changed)
            with self.subTest(field=field), self.assertRaises(ValueError):
                runner.verify_report(manifest, 'rhea', 'screen')
        changed = copy.deepcopy(data)
        changed['pairs'][0]['seed'] += 1
        self.write(path, changed)
        with self.assertRaisesRegex(ValueError, 'complete fixed seed'):
            runner.verify_report(manifest, 'rhea', 'screen')

    def test_direct_requires_completed_screen_strictly_between_52_and_60(self):
        for units, permitted in ((312, False), (313, True), (359, True), (360, False)):
            _, _, report, manifest = self.local_report(units=units)
            with patch.object(runner, 'complete_report', return_value=report):
                if permitted:
                    runner.permit_stage(manifest, 'rhea', 'direct')
                else:
                    with self.subTest(units=units), self.assertRaisesRegex(ValueError, 'strictly between'):
                        runner.permit_stage(manifest, 'rhea', 'direct')

    def test_interrupted_stage_is_write_once_and_cannot_retry(self):
        self.folder.mkdir(parents=True)
        self.write(self.folder / 'launch.json', {'name': runner.NAME})
        with patch.object(runner, 'load_manifest', return_value={}), \
                patch.object(runner, 'exclusion_maps', return_value={digest('old')}), \
                patch.object(runner, 'power_request', return_value=nullcontext()), \
                patch.object(runner, 'execute', side_effect=RuntimeError('interrupted')) as execute:
            with self.assertRaisesRegex(RuntimeError, 'interrupted'):
                runner.run_stage('rhea', 'screen')
            directory = self.folder / 'rhea'
            self.assertTrue((directory / 'screen-started.json').exists())
            self.assertTrue((directory / 'screen-failed.json').exists())
            self.assertFalse((directory / 'screen-complete.json').exists())
            with self.assertRaisesRegex(ValueError, 'already started'):
                runner.run_stage('rhea', 'screen')
            self.assertEqual(1, execute.call_count)

    def test_exclusions_include_sibling_and_interrupted_reports(self):
        self.write(self.folder / 'opening/screen.json', {'gamesPerMap': {digest('sibling'): 2}})
        with patch.object(runner, 'history', return_value=({digest('new-prior')}, set(), {})):
            actual = runner.exclusion_maps({'priorMapHashes': [digest('old')]})
        self.assertEqual({digest('old'), digest('new-prior'), digest('sibling')}, actual)

    def test_no_concurrent_local_stages_or_overwrite(self):
        self.write(self.folder / 'rhea/screen-started.json', {})
        with self.assertRaisesRegex(ValueError, 'running or interrupted'):
            runner.permit_stage({}, 'opening', 'screen')
        self.write(self.folder / 'rhea/screen-failed.json', {})
        runner.permit_stage({}, 'opening', 'screen')
        self.write(self.folder / 'opening/screen.json', {})
        with self.assertRaisesRegex(ValueError, 'not be overwritten'):
            runner.permit_stage({}, 'opening', 'screen')

    def test_os_lock_blocks_the_check_start_race_between_variants(self):
        from boss_batch import run_lock
        self.folder.mkdir(parents=True)
        with run_lock(self.folder), patch.object(runner, '_run_stage_locked') as run:
            with self.assertRaises(OSError):
                runner.run_stage('opening', 'screen')
            run.assert_not_called()
        with patch.object(runner, '_run_stage_locked') as run:
            runner.run_stage('opening', 'screen')
            run.assert_called_once_with('opening', 'screen')

    def certificate(self):
        cached_dir, current_dir = self.root / 'cached', self.root / 'current'
        cached_params = {'OLD': '7'}
        current_params = dict(cached_params, **{name: value for name, (_, value) in compatibility.FIELDS.items()})
        old = 'class Player {\npublic static final String BUILD_MARKER = "old";\n'
        old += 'public int OLD = 7;\nObject make() { return new Params("OLD=7"); }\n}\n'
        declarations = ''.join(f'public {kind} {name} = {value};\n'
                               for name, (kind, value) in compatibility.FIELDS.items())
        current = old.replace('public int OLD = 7;\n', 'public int OLD = 7;\n' + declarations)
        current = current.replace('"OLD=7"', '"' + ','.join(k + '=' + v for k, v in current_params.items()) + '"')
        current = current.replace('BUILD_MARKER = "old"', 'BUILD_MARKER = "new"')
        for folder, source, params in ((cached_dir, old, cached_params), (current_dir, current, current_params)):
            folder.mkdir()
            (folder / 'Player.java').write_bytes(source.encode())
            self.write(folder / 'manifest.json', {'strategy': 'v07', 'params': params,
                                                'sha256': digest(source), 'buildId': digest(folder.name)})
        cert = compatibility.certificate(current_dir, cached_dir)
        path = self.write(self.root / 'control-equivalence.json', cert)
        cache = {'build': {'buildId': cert['cachedBuildId'], 'sourceSha256': cert['cachedSourceSha256']},
                 'manifest': runner.pin(cached_dir / 'manifest.json')}
        return path, cert, cache

    def test_control_certificate_is_recomputed_and_tampering_refused(self):
        path, cert, cache = self.certificate()
        self.assertEqual(cert, runner.control_compatibility(path, cache, {'v07': cert['currentParams']}))
        altered = copy.deepcopy(cert)
        altered['addedUnusedFields']['OPENING_TURNS'] = '4'
        self.write(path, altered)
        with self.assertRaisesRegex(ValueError, 'certificate no longer matches'):
            runner.control_compatibility(path, cache)
        self.write(path, cert)
        source = Path(cert['currentDirectory']) / 'Player.java'
        source.write_bytes(source.read_bytes() + b'// changed after certification\n')
        with self.assertRaisesRegex(ValueError, 'source hash mismatch'):
            runner.control_compatibility(path, cache)

    def source_binding_inputs(self):
        artifacts = {v: {'directory': str(self.root / v), 'sourceSha256': digest(v)} for v in runner.VARIANTS}
        certificate = {'currentDirectory': str(self.root / 'v07'), 'currentSourceSha256': digest('v07')}
        return artifacts, certificate

    def test_source_binding_runs_read_only_verifier_for_both_candidates_and_control(self):
        artifacts, certificate = self.source_binding_inputs()

        def output(command, log):
            bot = next(value.split('=', 1)[1] for value in command if value.startswith('-PsubmitVersion='))
            source = digest('v07') if bot == 'v07' else artifacts['rhea' if 'RHEA' in bot else 'opening']['sourceSha256']
            text = 'Exact current-source artifact verified: ' + bot + ' ' + source + '\n'
            with log.open('x', encoding='utf-8') as stream:
                stream.write(text)
            return text

        with patch.object(runner, 'code_identity', return_value={'code': digest('code')}), \
                patch.object(runner, 'infrastructure_identity', return_value={'tool': digest('tool')}), \
                patch.object(runner, 'submissions', return_value={'Player': digest('selected')}), \
                patch.object(runner, 'execute', side_effect=output) as execute:
            result = runner.verify_artifact_sources(artifacts, certificate)
        self.assertEqual({'rhea', 'opening', 'control'}, set(result['sourceBindings']))
        self.assertEqual(3, execute.call_count)
        for call in execute.call_args_list:
            self.assertEqual('verifySubmission', call.args[0][1])
            self.assertIn('compileJava', call.args[0])
            self.assertNotIn('submit', call.args[0])
        for value in result['sourceBindings'].values():
            runner.verify_pin(value['log'])

    def test_source_binding_rejects_stale_export_or_code_change_during_check(self):
        artifacts, certificate = self.source_binding_inputs()
        with patch.object(runner, 'code_identity', return_value={}), \
                patch.object(runner, 'infrastructure_identity', return_value={}), \
                patch.object(runner, 'submissions', return_value={}), \
                patch.object(runner, 'execute', return_value='verified some older export'):
            with self.assertRaisesRegex(ValueError, 'Missing exact source'):
                runner.verify_artifact_sources(artifacts, certificate)
        output = 'Exact current-source artifact verified: ' + runner.spec('rhea') + ' ' + digest('rhea')
        with patch.object(runner, 'code_identity', side_effect=[{}, {}, {'changed': 'class'}]), \
                patch.object(runner, 'infrastructure_identity', return_value={}), \
                patch.object(runner, 'submissions', return_value={}), \
                patch.object(runner, 'execute', return_value=output):
            with self.assertRaisesRegex(ValueError, 'changed during artifact verification'):
                runner.verify_artifact_sources(artifacts, certificate)

    def test_compatibility_projects_only_control_checker_view_and_retains_originals(self):
        cert_path, cert, _ = self.certificate()
        _, data, report, _ = self.local_report(units=360)
        data['paramsB'] = cert['currentParams']
        report['data'] = data
        boss = cached_fixture()
        boss['candidate'].update(spec=runner.spec('rhea'), params=data['paramsA'])
        boss['control'].update(params=cert['cachedParams'], buildId=cert['cachedBuildId'],
                               sourceSha256=cert['cachedSourceSha256'])
        manifest = {'controlCompatibility': cert, 'controlEquivalence': runner.pin(cert_path)}
        original = copy.deepcopy(report)
        result = runner.assess_with_compatibility(manifest, report, boss=boss)
        self.assertEqual('PASS', result['strengthStatus'])
        self.assertEqual(original, report)
        self.assertEqual(cert['currentParams'], result['reports'][0]['paramsB'])
        self.assertFalse(result['controlMetadataCompatibility']['localArtifactClaimedIdenticalToCachedArtifact'])
        boss['candidate']['params'] = {'wrong': 'identity'}
        with self.assertRaisesRegex(ValueError, 'effective parameters changed'):
            runner.assess_with_compatibility(manifest, report, boss=boss)

    def test_final_screen_failure_needs_no_boss_and_cannot_be_overwritten(self):
        _, _, report, manifest = self.local_report(units=312)
        manifest['artifacts'] = {'rhea': {'buildId': digest('candidate')}}
        with patch.object(runner, 'load_manifest', return_value=manifest), \
                patch.object(runner, 'complete_report', return_value=report), \
                patch.object(runner, 'assert_unchanged'):
            runner.finalize('rhea')
            decision = json.loads((self.folder / 'rhea/decision.json').read_bytes())
            self.assertEqual('NOT_PASSED', decision['strengthStatus'])
            self.assertEqual('STOP_SCREEN_300', decision['directStage'])
            self.assertTrue((self.root / 'docs/evidence/strategy-search-rhea-v1.json').exists())
            with self.assertRaisesRegex(ValueError, 'write-once'):
                runner.finalize('rhea')

    def test_finalization_refuses_omitting_already_started_boss(self):
        _, _, report, manifest = self.local_report(units=330)
        manifest['artifacts'] = {'rhea': {'buildId': digest('candidate')}}
        directory = self.root / 'build/cg-boss/candidate'
        self.write(directory / 'plan.json', {'builds': {'candidate': {'buildId': digest('candidate')}}})
        self.write(directory / '0001/intent.json', {})
        with patch.object(runner, 'load_manifest', return_value=manifest), \
                patch.object(runner, 'complete_report', return_value=report):
            with self.assertRaisesRegex(ValueError, 'already started boss'):
                runner.finalize('rhea')


if __name__ == '__main__':
    unittest.main()
