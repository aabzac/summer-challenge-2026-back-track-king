"""Prospective, offline v3 local stages for two independent strategy experiments.

No HTTP calls, uploads, promotion, compilation or retries. Preflight freezes both
candidates; each fixed local stage is then invoked separately. The cached boss
batch may run independently alongside a required direct 1000. Evidence is
write-once, including interrupted stages. See docs/strategy-search-v1.md.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import threading

from boss_practical_gate import POLICY, assess
from practical_gate import compact, read_report, require
from run_quick_followup import ROOT, save, sha
from run_network_chords_experiment import (code_identity, command, execute,
                                           require_compiled, set_execution_state,
                                           submissions)

NAME = 'strategy-search-v1'
PROTOCOL = ROOT / 'docs' / (NAME + '.md')
FOLDER = ROOT / 'build/bench' / NAME
CONTROL_PLAN = ROOT / 'build/cg-boss/chords-v07-boss-40/plan.json'
LOCAL_HASH_SCHEME = 'sha256-init-block-player-0'
VARIANTS = {
    'rhea': {'flag': 'USE_ROLLOUT_RHEA', 'screen': 500000, 'direct': 501000},
    'opening': {'flag': 'USE_PROTECTED_OPENING', 'screen': 510000, 'direct': 511000},
}
STAGES = {'screen': (150, 'development'), 'direct': (500, 'confirmation')}
HEX = re.compile(r'[0-9a-f]{64}')


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def spec(variant):
    return 'current?' + VARIANTS[variant]['flag'] + '=1'


def experiment_name(variant):
    return 'strategy-search-' + variant + '-v1'


def pool_path(variant, stage):
    return ROOT / 'bench/pools' / (experiment_name(variant) + '-' + stage + '.json')


def pin(path):
    path = Path(path).resolve()
    require(path.is_relative_to(ROOT.resolve()), 'Evidence must remain inside the workspace')
    require('private' not in path.relative_to(ROOT.resolve()).parts, 'Do not pin private session files')
    return {'path': path.relative_to(ROOT.resolve()).as_posix(), 'sha256': sha(path)}


def verify_pin(value):
    path = ROOT / value['path']
    require(pin(path) == value, 'Pinned evidence changed: ' + value['path'])
    return path


def collect_history(value, maps, seeds):
    """Accept both full reports and durable compact outcomes; never infer wins."""
    if isinstance(value, list):
        for item in value:
            collect_history(item, maps, seeds)
    elif isinstance(value, dict):
        for field in ('gamesPerMap', 'maps'):
            entries = value.get(field)
            if isinstance(entries, dict):
                for key, item in entries.items():
                    if HEX.fullmatch(key):
                        maps.add(key)
                        if isinstance(item, dict) and type(item.get('seed')) is int:
                            seeds.add(item['seed'])
        for field in ('priorMapHashes', 'excludedMapHashes', 'mapHashes'):
            entries = value.get(field)
            if isinstance(entries, list):
                maps.update(key for key in entries if isinstance(key, str) and HEX.fullmatch(key))
        key = value.get('mapHash')
        if isinstance(key, str) and HEX.fullmatch(key):
            maps.add(key)
            seed = value.get('seed')
            if type(seed) is int or isinstance(seed, str) and re.fullmatch(r'-?\d+', seed):
                seeds.add(int(seed))
        for item in value.values():
            if isinstance(item, (list, dict)):
                collect_history(item, maps, seeds)


def history():
    maps, seeds, files = set(), set(), {}
    own_names = {experiment_name(v) + suffix + '.json' for v in VARIANTS
                 for suffix in ('', '-screen', '-direct')}
    # Include diagnostics as well as strength reports. Test fixture directories
    # are outside these roots and do not count as played maps.
    for folder in ('build/bench', 'build/analysis', 'docs/evidence'):
        for path in sorted((ROOT / folder).rglob('*.json')):
            if FOLDER in path.parents or path.parent == ROOT / 'docs/evidence' and path.name in own_names:
                continue
            try:
                value = json.loads(path.read_bytes())
            except (json.JSONDecodeError, UnicodeDecodeError) as error:
                raise ValueError('Unreadable historical JSON must be inspected, not skipped: '
                                 + path.relative_to(ROOT).as_posix()) from error
            file_maps, file_seeds = set(), set()
            collect_history(value, file_maps, file_seeds)
            if isinstance(value, dict) and type(value.get('games')) is int and value['games'] > 0 \
                    and any(key in value for key in ('a', 'b', 'winsA', 'winsB')):
                require(file_maps, 'Prior played-game report lacks map identities: ' + str(path))
            if file_maps or file_seeds:
                maps.update(file_maps)
                seeds.update(file_seeds)
                files[path.relative_to(ROOT).as_posix()] = sha(path)
    return maps, seeds, files


def validate_pools(used_seeds, reserved):
    ours = []
    for variant, settings in VARIANTS.items():
        for stage, (pairs, _) in STAGES.items():
            start = settings[stage]
            label = variant + '/' + stage
            require(not used_seeds.intersection(range(start, start + pairs)),
                    label + ': seed already used in recorded games')
            ours.append((label, start, pairs))
    for i, (label, start, pairs) in enumerate(ours):
        for other, other_start, other_count in [*ours[:i], *reserved]:
            require(max(start, other_start) >= min(start + pairs, other_start + other_count),
                    label + ': overlaps reserved pool ' + other)


def infrastructure_identity():
    names = ('tools/run_strategy_search_experiments.py', 'tools/run_network_chords_experiment.py',
             'tools/run_quick_followup.py', 'tools/practical_gate.py', 'tools/boss_practical_gate.py',
             'tools/boss_cached.py', 'tools/boss_batch.py', 'tools/boss_api.py',
             'tools/replay_capture.py', 'tools/diagnostics/boss_evaluation_protocol.py',
             'tools/diagnostics/strategy_control_compatibility.py',
             'docs/strategy-search-v1.md', 'docs/boss-cache-policy-v3.md',
             'build.gradle.kts', 'settings.gradle.kts', 'gradlew.bat',
             'gradle/wrapper/gradle-wrapper.properties', 'gradle/wrapper/gradle-wrapper.jar')
    paths = [ROOT / name for name in names]
    paths.extend(pool_path(variant, stage) for variant in VARIANTS for stage in STAGES)
    return {p.relative_to(ROOT).as_posix(): sha(p) for p in paths}


def validate_boss_maps(path, cache):
    value = json.loads(Path(path).read_bytes())
    require(value.get('hashScheme') == LOCAL_HASH_SCHEME, 'Boss maps must use the local core hash scheme')
    require(value.get('sourcePlanSha256') == cache['sourcePlan']['sha256'], 'Boss maps refer to a different cache')
    maps = value.get('maps')
    require(isinstance(maps, list) and len(maps) == 40, 'Need forty core-derived boss maps')
    expected = {game['gameId']: game for game in cache['games']}
    require(len(expected) == 40 and {item['gameId'] for item in maps} == expected.keys(),
            'Core-derived boss maps do not cover the pinned control games')
    require(len({item['mapHash'] for item in maps}) == 40, 'Boss local maps are not unique')
    for item in maps:
        game = expected[item['gameId']]
        require(isinstance(item['mapHash'], str) and HEX.fullmatch(item['mapHash']), 'Invalid local map hash')
        require(str(item['seed']) == str(game['seed']) and item['rawSha256'] == game['rawSha256'],
                'Boss core-map provenance changed')
    return value


def read_parameters(output):
    result = dict(re.findall(r'^  ([A-Z][A-Z0-9_]*)=(\S+)\s*$', output, re.MULTILINE))
    require(len(result) >= 98, 'Incomplete full resolved configuration from BotFactory')
    require(result.get('SEED') == '1' and result.get('NOISE') == '0.1', 'Unexpected seed or noise')
    return result


def validate_parameters(parameters):
    control = parameters['v07']
    require(parameters['current'] == control, 'Disabled current differs from frozen v07 parameters')
    for variant, settings in VARIANTS.items():
        candidate = parameters[spec(variant)]
        require(candidate.keys() == control.keys(), 'Candidate/control parameter schemas differ')
        changed = {key for key in candidate if candidate[key] != control[key]}
        require(changed == {settings['flag']} and candidate[settings['flag']] == 'true'
                and control[settings['flag']] == 'false',
                variant + ': exactly its independent flag must differ from v07')


def control_compatibility(path, cache, parameters=None):
    from diagnostics.strategy_control_compatibility import verify_certificate
    value = verify_certificate(Path(path))
    require(value['cachedBuildId'] == cache['build']['buildId']
            and value['cachedSourceSha256'] == cache['build']['sourceSha256'],
            'Control equivalence certificate refers to a different cached artifact')
    cached_manifest = json.loads(verify_pin(cache['manifest']).read_bytes())
    require(value['cachedParams'] == cached_manifest['params'], 'Cached full parameters differ')
    if parameters is not None:
        require(value['currentParams'] == parameters['v07'], 'Local v07 parameters differ from certified export')
    return value


def verify_artifact_sources(artifacts, compatibility):
    """Bind every exported source to this exact local source/class snapshot."""
    frozen_code = code_identity()
    frozen_infrastructure = infrastructure_identity()
    frozen_submissions = submissions()
    directory = ROOT / 'build/analysis' / NAME
    directory.mkdir(parents=True, exist_ok=True)
    inputs = [(variant, spec(variant), value['directory'], value['sourceSha256'])
              for variant, value in artifacts.items()]
    inputs.append(('control', 'v07', compatibility['currentDirectory'], compatibility['currentSourceSha256']))
    records = {}
    for label, bot, artifact_dir, expected_source in inputs:
        require(code_identity() == frozen_code and infrastructure_identity() == frozen_infrastructure,
                'Source/classes/tooling changed during artifact verification')
        log = directory / ('preflight-verify-' + label + '.log')
        output = execute(command('verifySubmission', '-PsubmitVersion=' + bot,
                                 '-PsubmitOut=' + str(artifact_dir)), log)
        expected_line = 'Exact current-source artifact verified: ' + bot + ' ' + expected_source
        require(expected_line in output.splitlines(), 'Missing exact source/configuration artifact verification')
        require(code_identity() == frozen_code and infrastructure_identity() == frozen_infrastructure,
                'Source/classes/tooling changed during artifact verification')
        require(submissions() == frozen_submissions, 'Selected submission changed during artifact verification')
        records[label] = {'spec': bot, 'directory': str(artifact_dir), 'sourceSha256': expected_source,
                          'log': pin(log), 'method': 'FileMerger.merge exact bytes and structural manifest equality'}
    return {'code': frozen_code, 'infrastructure': frozen_infrastructure,
            'submissionFiles': frozen_submissions, 'sourceBindings': records}


def assert_unchanged(manifest):
    require(code_identity() == manifest['code'], 'Main source/class/resource identity changed')
    require(infrastructure_identity() == manifest['infrastructure'], 'Protocol/tooling/pools changed')
    require(submissions() == manifest['submissionFiles'], 'Existing build/submit files changed')
    for name, expected in manifest['priorReports'].items():
        require(sha(ROOT / name) == expected, 'Prior evidence changed: ' + name)
    verify_pin(manifest['bossLocalMaps'])
    verify_pin(manifest['controlEquivalence'])
    for value in manifest['sourceBindings'].values():
        verify_pin(value['log'])
    from boss_cached import control_snapshot
    from boss_batch import artifact
    require(control_snapshot(CONTROL_PLAN, 'control') == manifest['cache'], 'Cached control identity changed')
    require(control_compatibility(ROOT / manifest['controlEquivalence']['path'], manifest['cache'],
                                  manifest.get('resolvedParameters')) == manifest['controlCompatibility'],
            'Frozen-control compatibility evidence changed')
    for value in manifest['artifacts'].values():
        require(artifact(Path(value['directory']), Path(value['processCheck'])) == value,
                'Exact candidate artifact/process evidence changed')


@contextmanager
def power_request(path):
    require(threading.current_thread() is threading.main_thread(), 'Power request must use the main thread')
    record = {'startedAt': timestamp(), 'globalPowerSettingsChanged': False, 'platform': os.name,
              'status': 'unsupported', 'requestSucceeded': False}
    try:
        if os.name == 'nt':
            result, error = set_execution_state(0x80000003)
            record.update(requestReturnCode=result, requestLastError=error,
                          requestSucceeded=result != 0, status='active' if result else 'request_failed')
            require(result != 0, 'Windows task-scoped power request failed')
        yield
    finally:
        if record['requestSucceeded']:
            result, error = set_execution_state(0x80000000)
            record.update(releaseReturnCode=result, releaseLastError=error,
                          status='released' if result else 'release_failed')
        record['endedAt'] = timestamp()
        save(path, record)


def preflight(args):
    from boss_cached import control_snapshot
    from boss_batch import artifact
    require(PROTOCOL.is_file(), 'Write the prospective protocol before preflight')
    require_compiled()
    require(not FOLDER.exists(), 'Existing preflight must not be overwritten or retried')
    maps, seeds, prior = history()
    require(maps, 'Prior map identities missing; cannot establish fresh stages')
    reserved = []
    own_pools = {pool_path(v, s) for v in VARIANTS for s in STAGES}
    for path in sorted((ROOT / 'bench/pools').glob('*.json')):
        if path in own_pools:
            continue
        value = json.loads(path.read_bytes())
        if isinstance(value, dict) and 'seedStart' in value and 'pairs' in value:
            reserved.append((path.name, value['seedStart'], value['pairs']))
            prior[path.relative_to(ROOT).as_posix()] = sha(path)
    validate_pools(seeds, reserved)
    cache = control_snapshot(CONTROL_PLAN, 'control')
    compatibility = control_compatibility(args.control_equivalence, cache)
    boss_maps = validate_boss_maps(args.boss_local_maps, cache)
    for item in boss_maps['maps']:
        maps.add(item['mapHash'])
        seeds.add(int(item['seed']))
    validate_pools(seeds, reserved)
    for variant, settings in VARIANTS.items():
        for stage, (pairs, purpose) in STAGES.items():
            value = {'schemaVersion': 1, 'id': experiment_name(variant) + '-' + stage,
                     'purpose': purpose, 'generator': 'cg-sha1prng',
                     'seedStart': settings[stage], 'pairs': pairs}
            path = pool_path(variant, stage)
            if path.exists():
                require(json.loads(path.read_bytes()) == value, 'Unexpected prospective pool: ' + str(path))
            else:
                save(path, value)
    artifacts = {}
    for variant in VARIANTS:
        directory = Path(getattr(args, variant + '_artifact')).resolve()
        pin(directory / 'manifest.json')  # Reject paths outside public workspace evidence.
        value = artifact(directory, directory / 'process-check.json')
        require(value['strategy'] == spec(variant), 'Wrong candidate artifact specification')
        artifacts[variant] = value
    source_bindings = verify_artifact_sources(artifacts, compatibility)
    manifest = {'name': NAME, 'createdAt': timestamp(), 'policy': POLICY, 'control': 'v07',
                'candidates': {variant: spec(variant) for variant in VARIANTS},
                'threads': 2, 'timingProfile': 'CG', 'firstSearchBudgetMs': 486, 'turnSearchBudgetMs': 36,
                **source_bindings, 'priorMapHashes': sorted(maps),
                'priorReports': prior, 'cache': cache, 'bossLocalMaps': pin(args.boss_local_maps),
                'controlEquivalence': pin(args.control_equivalence), 'controlCompatibility': compatibility,
                'artifacts': artifacts, 'protocol': PROTOCOL.read_text(encoding='utf-8'),
                'poolHashes': {variant: {stage: sha(pool_path(variant, stage)) for stage in STAGES}
                               for variant in VARIANTS},
                'pools': {variant: {stage: {'seedStart': settings[stage], 'pairs': pairs, 'purpose': purpose}
                                   for stage, (pairs, purpose) in STAGES.items()}
                          for variant, settings in VARIANTS.items()}}
    require(manifest['submissionFiles'], 'Expected selected submission files to preserve')
    FOLDER.mkdir(parents=True, exist_ok=False)
    save(FOLDER / 'preflight-inputs.json', manifest)
    parameters = {}
    for label, bot in [('control', 'v07'), ('disabled', 'current')]+[(v, spec(v)) for v in VARIANTS]:
        assert_unchanged(manifest)
        parameters[bot] = read_parameters(execute(command('botCatalog', '-Pbot=' + bot),
                                                FOLDER / (label + '-params.log')))
    validate_parameters(parameters)
    for variant, value in artifacts.items():
        exported = json.loads((Path(value['directory']) / 'manifest.json').read_bytes())
        require(exported['params'] == parameters[spec(variant)], 'Artifact and local candidate parameters differ')
    manifest['resolvedParameters'] = parameters
    assert_unchanged(manifest)
    save(FOLDER / 'launch.json', manifest)
    print('PREFLIGHT COMPLETE: both variants frozen; no games played.', flush=True)


def load_manifest():
    manifest = json.loads((FOLDER / 'launch.json').read_bytes())
    require(manifest['name'] == NAME and manifest['policy'] == POLICY
            and manifest['candidates'] == {v: spec(v) for v in VARIANTS}, 'Wrong launch manifest')
    assert_unchanged(manifest)
    return manifest


def verify_report(manifest, variant, stage):
    directory = FOLDER / variant
    report = read_report(directory / (stage + '.json'), STAGES[stage][0] * 2)
    data = report['data']
    require(data['a'] == spec(variant) and data['b'] == 'v07', 'Wrong bots in report')
    require(data['threads'] == manifest['threads'], 'Wrong thread count')
    require(data['paramsA'] == manifest['resolvedParameters'][spec(variant)]
            and data['paramsB'] == manifest['resolvedParameters']['v07'], 'Full effective parameters changed')
    require(data['poolId'] == experiment_name(variant) + '-' + stage
            and data['poolSha256'] == manifest['poolHashes'][variant][stage], 'Wrong pool identity')
    seed, pairs = VARIANTS[variant][stage], STAGES[stage][0]
    require([pair['seed'] for pair in data['pairs']] == list(range(seed, seed + pairs)),
            'Report does not cover the complete fixed seed interval')
    excluded_path = directory / (stage + '-excluded.json')
    excluded = json.loads(excluded_path.read_bytes())
    require(not set(report['maps']).intersection(excluded['gamesPerMap']), 'Previously evaluated map reused')
    normalized_exclusions = {name.replace('\\', '/'): digest
                             for name, digest in data.get('excludedReports', {}).items()}
    require(normalized_exclusions == {excluded_path.relative_to(ROOT).as_posix(): sha(excluded_path)},
            'Benchmark did not use the exact frozen exclusion report')
    return report


def complete_report(manifest, variant, stage):
    directory = FOLDER / variant
    record = json.loads((directory / (stage + '-complete.json')).read_bytes())
    report = verify_report(manifest, variant, stage)
    require(record['reportSha256'] == report['sha256'], 'Completed report changed')
    return report


def exclusion_maps(manifest):
    used = set(manifest['priorMapHashes']) | history()[0]
    for variant in VARIANTS:
        for stage in STAGES:
            path = FOLDER / variant / (stage + '.json')
            if path.exists():
                # Interrupted reports, if any, stay excluded too. Never delete evidence
                # or select replacement seeds to make a fixed stage fit the pool.
                collect_history(json.loads(path.read_bytes()), used, set())
    return used


def permit_stage(manifest, variant, stage):
    directory = FOLDER / variant
    require(not (directory / (stage + '-started.json')).exists(), 'Stage already started: no retries')
    require(not (directory / (stage + '.json')).exists(), 'Existing report must not be overwritten')
    if stage == 'direct':
        decision = assess(complete_report(manifest, variant, 'screen'))
        require(decision['strengthStatus'] == 'NEEDS_DIRECT_1000',
                'Direct 1000 is permitted only after a screen strictly between 52% and 60%')
    for other in VARIANTS:
        for part in STAGES:
            other_dir = FOLDER / other
            if (other_dir / (part + '-started.json')).exists():
                require((other_dir / (part + '-complete.json')).exists()
                        or (other_dir / (part + '-failed.json')).exists(),
                        'Another local stage is running or interrupted; no concurrent local bench')


def run_stage(variant, stage):
    from boss_batch import run_lock
    require(FOLDER.is_dir(), 'Complete shared preflight before starting a local stage')
    # One OS lock covers both variants and the check/start boundary. A crash
    # releases this lock, while the durable started record still forbids retries.
    with run_lock(FOLDER):
        _run_stage_locked(variant, stage)


def _run_stage_locked(variant, stage):
    manifest = load_manifest()
    permit_stage(manifest, variant, stage)
    directory = FOLDER / variant
    directory.mkdir(parents=True, exist_ok=True)
    excluded = directory / (stage + '-excluded.json')
    save(excluded, {'gamesPerMap': dict.fromkeys(sorted(exclusion_maps(manifest)), 2)})
    save(directory / (stage + '-started.json'),
         {'startedAt': timestamp(), 'launchSha256': sha(FOLDER / 'launch.json'), 'exclusions': pin(excluded)})
    try:
        with power_request(directory / (stage + '-power.json')):
            out = directory / (stage + '.json')
            execute(command('bench', '-Pbot1=' + spec(variant), '-Pbot2=v07', '-Pthreads=2', '-Ptiming=cg',
                            '-Ppool=' + pool_path(variant, stage).relative_to(ROOT).as_posix(),
                            '-Pout=' + out.relative_to(ROOT).as_posix(),
                            '-PexcludeReports=' + excluded.relative_to(ROOT).as_posix()),
                    directory / (stage + '.log'))
            assert_unchanged(manifest)
            report = verify_report(manifest, variant, stage)
            screen = report if stage == 'screen' else complete_report(manifest, variant, 'screen')
            decision = assess(screen, report if stage == 'direct' else None)
            save(directory / (stage + '-decision.json'), decision)
            save(ROOT / 'docs/evidence' / (experiment_name(variant) + '-' + stage + '.json'),
                 {'launch': manifest, 'stage': stage, 'decision': decision})
            save(directory / (stage + '-complete.json'),
                 {'completedAt': timestamp(), 'reportSha256': report['sha256'],
                  'strengthStatus': decision['strengthStatus'], 'submissionFilesUnchanged': submissions()})
            print(variant.upper(), stage.upper(), decision['strengthStatus'], flush=True)
    except BaseException as error:
        save(directory / (stage + '-failed.json'),
             {'failedAt': timestamp(), 'error': repr(error), 'policy': 'Preserve evidence; no retries'})
        raise


def assess_with_compatibility(manifest, screen, direct=None, boss=None):
    if boss is None:
        return assess(screen, direct)
    certificate = manifest['controlCompatibility']

    def project(report):
        if report is None:
            return None
        require(report['data']['paramsB'] == certificate['currentParams'],
                'Local full control parameters differ from the exact-source certificate')
        # Only the checker input view changes. Original full reports, hashes and
        # compacts are retained below; candidate metadata is never projected.
        return dict(report, data=dict(report['data'], paramsB=certificate['cachedParams']))

    require(boss['control']['params'] == certificate['cachedParams']
            and boss['control']['buildId'] == certificate['cachedBuildId']
            and boss['control']['sourceSha256'] == certificate['cachedSourceSha256'],
            'Boss control does not match the certified historical artifact')
    decision = assess(project(screen), project(direct), boss)
    decision['reports'] = [compact(report) for report in (screen, direct) if report is not None]
    decision['controlMetadataCompatibility'] = {
        'method': certificate['method'], 'certificate': manifest['controlEquivalence'],
        'removedFromCheckerControlViewOnly': certificate['addedUnusedFields'],
        'localFullParameters': certificate['currentParams'],
        'cachedFullParameters': certificate['cachedParams'],
        'originalReportsAndCandidateParametersUnchanged': True,
        'localArtifactClaimedIdenticalToCachedArtifact': False,
        'limitation': certificate['limitation'],
    }
    return decision


def started_boss_plans(build_id):
    found = []
    for path in sorted((ROOT / 'build/cg-boss').rglob('plan.json')):
        if 'private' in path.relative_to(ROOT).parts:
            continue
        value = json.loads(path.read_bytes())
        candidate = value.get('builds', {}).get('candidate', {})
        if candidate.get('buildId') == build_id and any(path.parent.rglob('intent.json')):
            found.append(path.resolve())
    return found


def finalize(variant, boss_plan=None):
    manifest = load_manifest()
    directory = FOLDER / variant
    require(not (directory / 'decision.json').exists(), 'Final decision is write-once')
    screen = complete_report(manifest, variant, 'screen')
    direct = complete_report(manifest, variant, 'direct') if (directory / 'direct-started.json').exists() else None
    boss = None
    started = started_boss_plans(manifest['artifacts'][variant]['buildId'])
    require(not started or boss_plan is not None, 'Supply the already started boss plan even after a local failure')
    if boss_plan:
        from boss_cached import assess_cached, load_cached
        _, plan = load_cached(Path(boss_plan))
        require(plan['cache'] == manifest['cache']
                and plan['builds']['candidate'] == manifest['artifacts'][variant],
                'Boss plan differs from the frozen cache/candidate artifact')
        require(not started or started == [Path(boss_plan).resolve()], 'Multiple started boss plans for one candidate')
        boss = assess_cached(Path(boss_plan))
    decision = assess_with_compatibility(manifest, screen, direct, boss)
    require(decision['strengthStatus'] in ('PASS', 'NOT_PASSED'), 'Required fixed stage is incomplete')
    if boss is not None:
        require(boss['complete'], 'Finish the full started forty-game boss batch even after local failure')
    assert_unchanged(manifest)
    save(directory / 'decision.json', decision)
    save(ROOT / 'docs/evidence' / (experiment_name(variant) + '.json'),
         {'launch': manifest, 'decision': decision, 'bossPlan': pin(boss_plan) if boss_plan else None})
    print(variant.upper(), 'FINAL', decision['strengthStatus'], flush=True)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument('--preflight', action='store_true')
    modes.add_argument('--stage', choices=STAGES)
    modes.add_argument('--finalize', action='store_true')
    parser.add_argument('--variant', choices=VARIANTS)
    parser.add_argument('--rhea-artifact', type=Path)
    parser.add_argument('--opening-artifact', type=Path)
    parser.add_argument('--boss-local-maps', type=Path)
    parser.add_argument('--control-equivalence', type=Path)
    parser.add_argument('--boss-plan', type=Path)
    args = parser.parse_args(argv)
    if args.preflight:
        if not all((args.rhea_artifact, args.opening_artifact, args.boss_local_maps, args.control_equivalence)):
            parser.error('--preflight requires both candidate artifacts, --boss-local-maps and --control-equivalence')
        preflight(args)
    else:
        if not args.variant:
            parser.error('--stage and --finalize require --variant')
        run_stage(args.variant, args.stage) if args.stage else finalize(args.variant, args.boss_plan)


if __name__ == '__main__':
    main()
