"""Fixed late multi-cell network chord experiment: finite practical stages, no retries.

Compile, test and validate the final sources before --preflight. This runner deliberately
skips compilation/resource processing and refuses any source, class, resource, protocol,
pool, orchestration or submission change after the manifest has been written. --run is
single-use even if interrupted; it never retries, promotes, exports or uploads a bot.
During --run only, a Windows thread-scoped system AND display request reduces the risk
of the documented Modern Standby interruption. It changes no global power settings;
success does not guarantee that suspension or external scheduling stalls cannot occur.
"""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import threading

from practical_gate import POLICY, assess, compact, read_report, require
from run_quick_followup import ROOT, identities, save, sha

NAME = 'network-chords-v1'
PROTOCOL = ROOT / f'docs/{NAME}.md'
FOLDER = ROOT / 'build/bench' / NAME
CANDIDATE = 'current?USE_ROLLOUT_NETWORK_CHORDS=1'
POOLS = {'screen': (200000, 150, 'development'), 'direct': (201000, 500, 'confirmation'),
         'style': (202000, 150, 'confirmation'), 'full': (203000, 500, 'confirmation')}
HEX = re.compile(r'[0-9a-f]{64}')


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def set_execution_state(flags):
    import ctypes
    function = ctypes.WinDLL('kernel32', use_last_error=True).SetThreadExecutionState
    function.argtypes = [ctypes.c_uint]
    function.restype = ctypes.c_uint
    ctypes.set_last_error(0)
    result = int(function(flags))
    return result, ctypes.get_last_error() if result == 0 else 0


@contextmanager
def power_request(platform_name=None):
    """Acquire and release on this main thread; record the result even on a run error."""
    path = FOLDER / 'power-request.json'
    require(not path.exists(), 'Existing power record must not be overwritten or retried')
    require(threading.current_thread() is threading.main_thread(),
            'Power request and release must use the main Python thread')
    platform_name = os.name if platform_name is None else platform_name
    record = {'platform': platform_name, 'startedAt': timestamp(),
              'threadId': threading.get_ident(), 'globalPowerSettingsChanged': False,
              'reason': 'Prior Modern Standby interruption; task-scoped system and display request',
              'requestFlags': '0x80000003', 'releaseFlags': '0x80000000',
              'requestAt': None, 'requestReturnCode': None, 'requestLastError': None,
              'releaseAt': None, 'releaseReturnCode': None, 'releaseLastError': None,
              'requestSucceeded': False, 'releaseAttempted': False,
              'status': 'unsupported' if platform_name != 'nt' else 'not_requested'}
    try:
        if platform_name == 'nt':
            record['requestAt'] = timestamp()
            result, error = set_execution_state(0x80000003)
            record.update(requestReturnCode=result, requestLastError=error,
                          requestSucceeded=result != 0,
                          status='active' if result != 0 else 'request_failed')
            require(result != 0, 'Windows execution-state request failed; no strength games started')
        yield record
        record['runOutcome'] = 'completed'
    except BaseException as error:
        record['runOutcome'] = 'error'
        record['error'] = repr(error)
        raise
    finally:
        try:
            if record['requestSucceeded']:
                require(threading.get_ident() == record['threadId'], 'Power release changed threads')
                record['releaseAt'] = timestamp()
                record['releaseAttempted'] = True
                result, error = set_execution_state(0x80000000)  # ES_CONTINUOUS resets this thread.
                record.update(releaseReturnCode=result, releaseLastError=error,
                              status='released' if result != 0 else 'release_failed')
        except BaseException as error:
            record.update(status='release_error', releaseError=repr(error))
            raise
        finally:
            record['endedAt'] = timestamp()
            save(path, record)
            print('POWER REQUEST', record['status'], flush=True)


def submissions():
    return {p.relative_to(ROOT).as_posix(): sha(p)
            for p in sorted((ROOT / 'build/submit').rglob('*')) if p.is_file()}


def code_identity():
    result = identities()  # Includes every main source/class, especially btk.core.
    for p in sorted((ROOT / 'build/resources/main').rglob('*')):
        if p.is_file():
            result[p.relative_to(ROOT).as_posix()] = sha(p)
    return result


def pool_path(stage):
    return ROOT / f'bench/pools/{NAME}-{stage}.json'


def infrastructure_identity():
    paths = [Path(__file__).resolve(), ROOT / 'tools/practical_gate.py',
             ROOT / 'tools/run_quick_followup.py', PROTOCOL, ROOT / 'build.gradle.kts',
             ROOT / 'settings.gradle.kts', ROOT / 'gradlew.bat',
             ROOT / 'gradle/wrapper/gradle-wrapper.properties',
             ROOT / 'gradle/wrapper/gradle-wrapper.jar']
    paths.extend(pool_path(stage) for stage in POOLS)
    return {p.relative_to(ROOT).as_posix(): sha(p) for p in paths}


def history():
    """Include durable compact evidence when old full reports are no longer in build/."""
    maps, seeds, files = set(), set(), {}

    def visit(value):
        if isinstance(value, list):
            for item in value:
                visit(item)
        elif isinstance(value, dict):
            for field in ('gamesPerMap', 'maps'):
                entries = value.get(field)
                if isinstance(entries, dict):
                    for key, item in entries.items():
                        if HEX.fullmatch(key):
                            maps.add(key)
                            if isinstance(item, dict) and isinstance(item.get('seed'), int):
                                seeds.add(item['seed'])
            for key in value.get('priorMapHashes', []):
                if isinstance(key, str) and HEX.fullmatch(key):
                    maps.add(key)
            key = value.get('mapHash')
            if isinstance(key, str) and HEX.fullmatch(key):
                maps.add(key)
                if isinstance(value.get('seed'), int):
                    seeds.add(value['seed'])
            for child in value.values():
                if isinstance(child, (list, dict)):
                    visit(child)

    for folder in ('build/bench', 'docs/evidence'):
        for path in sorted((ROOT / folder).rglob('*.json')):
            if FOLDER in path.parents or path.name.startswith(NAME):
                continue
            raw = json.loads(path.read_bytes())
            visit(raw)
            files[path.relative_to(ROOT).as_posix()] = sha(path)
    return maps, seeds, files


def require_compiled():
    classes = ROOT / 'build/classes/java/main'
    require(classes.is_dir(), 'Compile final main sources before preflight')
    for source in (ROOT / 'src/main/java').rglob('*.java'):
        target = classes / source.relative_to(ROOT / 'src/main/java').with_suffix('.class')
        require(target.is_file(), f'Missing compiled class: {target}')
        require(target.stat().st_mtime_ns >= source.stat().st_mtime_ns,
                f'Source newer than its compiled class: {source}')
    for source in (ROOT / 'src/main/resources').rglob('*'):
        if source.is_file():
            target = ROOT / 'build/resources/main' / source.relative_to(ROOT / 'src/main/resources')
            require(target.is_file() and sha(source) == sha(target),
                    f'Missing or stale copied resource: {source}')


def command(task, *arguments):
    return [str(ROOT / 'gradlew.bat'), task, '--console=plain', '-x', 'compileJava',
            '-x', 'processResources', *arguments]


def execute(args, log_path):
    with log_path.open('x', encoding='utf-8') as log:
        process = subprocess.Popen(args, cwd=ROOT, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                                   errors='replace')
        try:
            for line in process.stdout:
                log.write(line)
                log.flush()
                print(line, end='', flush=True)
            require(process.wait() == 0, f'Command failed; preserve {log_path}')
        except BaseException:
            # Never leave an interrupted benchmark silently running in the background.
            if process.poll() is None:
                if os.name == 'nt':
                    subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
                else:
                    process.terminate()
            raise
    return log_path.read_text(encoding='utf-8')


def assert_unchanged(manifest):
    require(code_identity() == manifest['code'], 'Main source/class/resource identities changed')
    require(infrastructure_identity() == manifest['infrastructure'], 'Protocol/tooling/pools changed')
    require(submissions() == manifest['submissionFiles'], 'Existing submission files changed')
    for relative, expected in manifest['priorReports'].items():
        require(sha(ROOT / relative) == expected, f'Prior evidence changed: {relative}')


def preflight():
    require(PROTOCOL.is_file(), f'Write the fixed protocol first: {PROTOCOL}')
    require_compiled()
    require(not FOLDER.exists(), 'Existing experiment folder: no repeated preflight or retry')
    for stage, (seed, pairs, purpose) in POOLS.items():
        data = {'schemaVersion': 1, 'id': NAME + '-' + stage, 'purpose': purpose,
                'generator': 'cg-sha1prng', 'seedStart': seed, 'pairs': pairs}
        path = pool_path(stage)
        if path.exists():
            require(json.loads(path.read_bytes()) == data, f'Unexpected predeclared pool: {path}')
        else:
            save(path, data)
    maps, used_seeds, reports = history()
    reserved = {}
    for path in sorted((ROOT / 'bench/pools').glob('*.json')):
        if path.name.startswith(NAME + '-'):
            continue
        pool = json.loads(path.read_bytes())
        if isinstance(pool, dict) and 'seedStart' in pool and 'pairs' in pool:
            reserved[path.relative_to(ROOT).as_posix()] = sha(path)
            start, count = pool['seedStart'], pool['pairs']
            for stage, (seed, pairs, _) in POOLS.items():
                require(max(start, seed) >= min(start + count, seed + pairs),
                        f'{stage}: overlaps existing reserved pool {path}')
    for stage, (seed, pairs, _) in POOLS.items():
        require(not used_seeds.intersection(range(seed, seed + pairs)),
                f'{stage}: seed range has already appeared in recorded games')
    require(maps, 'Prior map identities missing; cannot establish fresh stages')
    manifest = {'name': NAME, 'createdAt': timestamp(), 'policy': POLICY,
                'candidate': CANDIDATE, 'control': 'v07', 'commonOpponent': 'v05',
                'threads': 2, 'timing': 'CG', 'code': code_identity(),
                'powerPolicy': 'Windows main-thread 0x80000003 during run, finally release 0x80000000; no global settings',
                'infrastructure': infrastructure_identity(), 'submissionFiles': submissions(),
                'protocol': PROTOCOL.read_text(encoding='utf-8'),
                'priorMapHashes': sorted(maps), 'priorReports': {**reports, **reserved},
                'poolHashes': {stage: sha(pool_path(stage)) for stage in POOLS},
                'pools': {stage: {'seedStart': seed, 'pairs': pairs, 'purpose': purpose}
                          for stage, (seed, pairs, purpose) in POOLS.items()}}
    require(manifest['submissionFiles'], 'Expected existing submission files to preserve')
    FOLDER.mkdir(parents=True, exist_ok=False)
    save(FOLDER / 'preflight-inputs.json', manifest)
    params = {}
    for label, spec in [('candidate', CANDIDATE), ('control', 'v07'), ('commonOpponent', 'v05')]:
        assert_unchanged(manifest)
        output = execute(command('botCatalog', '-Pbot=' + spec), FOLDER / (label + '-params.log'))
        params[spec] = dict(re.findall(r'^  ([A-Z][A-Z0-9_]*)=(\S+)\s*$', output, re.MULTILINE))
        require(len(params[spec]) >= 30, 'Incomplete resolved configuration from BotFactory')
        require(params[spec].get('SEED') == '1', 'Unexpected bot SEED')
        require(params[spec].get('NOISE') == '0.1', 'Unexpected bot NOISE')
    changed = {key for key in params[CANDIDATE]
               if params[CANDIDATE][key] != params['v07'][key]}
    require(changed == {'USE_ROLLOUT_NETWORK_CHORDS'}, 'Candidate must differ from v07 in exactly the chord flag')
    require(params[CANDIDATE].get('USE_ROLLOUT_NETWORK_CHORDS') == 'true', 'Expected network chords enabled')
    assert_unchanged(manifest)
    manifest['resolvedParameters'] = params
    save(FOLDER / 'launch.json', manifest)
    print('PREFLIGHT COMPLETE: no games played. Run this same script with --run.', flush=True)


def run():
    manifest = json.loads((FOLDER / 'launch.json').read_bytes())
    require(manifest['name'] == NAME and manifest['candidate'] == CANDIDATE,
            'Wrong experiment manifest')
    assert_unchanged(manifest)
    require(not (FOLDER / 'started.json').exists(), 'Existing run: no retry')
    with power_request():
        run_stages(manifest)


def run_stages(manifest):
    save(FOLDER / 'started.json', {'startedAt': timestamp(), 'launchSha256': sha(FOLDER / 'launch.json')})
    used = set(manifest['priorMapHashes'])

    def exclusions(label):
        # Include any additional evidence produced since preflight, never remove earlier exclusions.
        used.update(history()[0])
        path = FOLDER / (label + '-excluded.json')
        save(path, {'gamesPerMap': dict.fromkeys(sorted(used), 2)})
        return path

    def bench(stage, label, bot_a, bot_b, excluded):
        assert_unchanged(manifest)
        out = FOLDER / (label + '.json')
        require(not out.exists(), 'Existing report must not be replaced')
        print('START', label, flush=True)
        execute(command('bench', '-Pbot1=' + bot_a, '-Pbot2=' + bot_b,
                        '-Pthreads=2', '-Ptiming=cg',
                        '-Ppool=' + pool_path(stage).relative_to(ROOT).as_posix(),
                        '-Pout=' + out.relative_to(ROOT).as_posix(),
                        '-PexcludeReports=' + excluded.relative_to(ROOT).as_posix()),
                FOLDER / (label + '.log'))
        assert_unchanged(manifest)
        seed, pairs, _ = POOLS[stage]
        report = read_report(out, pairs * 2)
        data = report['data']
        require(data['a'] == bot_a and data['b'] == bot_b, 'Wrong bots in report')
        require(data['threads'] == 2, 'Wrong thread count')
        require(data['paramsA'] == manifest['resolvedParameters'][bot_a]
                and data['paramsB'] == manifest['resolvedParameters'][bot_b], 'Resolved parameters changed')
        require(data['poolId'] == NAME + '-' + stage
                and data['poolSha256'] == manifest['poolHashes'][stage], 'Pool identity changed')
        require([p['seed'] for p in data['pairs']] == list(range(seed, seed + pairs)),
                'Report does not cover the complete predeclared seed interval')
        require(not set(report['maps']).intersection(json.loads(excluded.read_bytes())['gamesPerMap']),
                'Previously evaluated map reused')
        used.update(report['maps'])
        return report

    try:
        screen = bench('screen', 'screen', CANDIDATE, 'v07', exclusions('screen'))
        direct = ca = co = fa = fo = None
        decision = assess(screen)
        if decision['strengthStatus'] == 'NEEDS_DIRECT_1000':
            direct = bench('direct', 'direct', CANDIDATE, 'v07', exclusions('direct'))
            decision = assess(screen, direct)
        if decision['strengthStatus'] == 'NEEDS_MATCHED_300_EACH':
            excluded = exclusions('style')
            ca = bench('style', 'style-candidate', CANDIDATE, 'v05', excluded)
            co = bench('style', 'style-control', 'v07', 'v05', excluded)
            decision = assess(screen, direct, ca, co)
        if decision['strengthStatus'] == 'NEEDS_MATCHED_1000_EACH':
            excluded = exclusions('full')
            fa = bench('full', 'full-candidate', CANDIDATE, 'v05', excluded)
            fo = bench('full', 'full-control', 'v07', 'v05', excluded)
            decision = assess(screen, direct, ca, co, fa, fo)
        require(decision['strengthStatus'] in ('PASS', 'NOT_PASSED'), 'Unfinished strength protocol')
        save(FOLDER / 'decision.json', decision)
        save(ROOT / f'docs/evidence/{NAME}.json', {'launch': manifest, 'decision': decision})
        assert_unchanged(manifest)
        save(FOLDER / 'complete.json', {'completedAt': timestamp(),
                                      'strengthStatus': decision['strengthStatus'],
                                      'submissionFilesUnchanged': submissions()})
        print('FINAL', decision['strengthStatus'], flush=True)
    except BaseException as error:
        save(FOLDER / 'failed.json', {'failedAt': timestamp(), 'error': repr(error),
                                    'policy': 'Preserve all evidence; no automatic retry or continuation'})
        raise


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--preflight', action='store_true', help='Freeze completed build and inputs; no games')
    mode.add_argument('--run', action='store_true', help='Execute the already frozen experiment exactly once')
    args = parser.parse_args()
    preflight() if args.preflight else run()
