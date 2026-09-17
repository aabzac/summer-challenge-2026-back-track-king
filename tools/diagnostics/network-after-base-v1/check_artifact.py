"""One exact-artifact export/check, only after the complete frozen strength protocol passes.

--audit-only reads and validates inputs without writing, compiling or playing.
--run creates an exclusive isolated workspace, runs submit once, then the four fixed
two-game procCheck stages once each. Completed process failures never trigger a retry.
No promotion, upload, root compilation or root submission write is performed.
"""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import threading

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
NAME = 'network-after-base-v1'
BENCH = ROOT / 'build/bench' / NAME
WORK = ROOT / 'build/analysis' / NAME / 'artifact-workspace'
DURABLE = ROOT / 'docs/evidence' / (NAME + '-process.json')
MAPS = (('903510176', 'replays/cg/903510176.json'),
        ('904146009', 'replays/cg/bot/904146009.json'),
        ('904148181', 'replays/cg/bot/904148181.json'),
        ('904147739', 'replays/cg/bot/904147739.json'))
BUILD_FILES = ('build.gradle.kts', 'settings.gradle.kts', 'gradlew.bat',
               'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def now():
    return datetime.now(timezone.utc).isoformat()


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_bytes())


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8', newline='\n') as out:
        json.dump(value, out, indent=2, ensure_ascii=False)
        out.write('\n')


def relative(path):
    return path.relative_to(ROOT).as_posix()


def tree(folder, base):
    return {p.relative_to(base).as_posix(): sha(p)
            for p in sorted(folder.rglob('*')) if p.is_file()}


def verify_entries(base, entries, label):
    for name, expected in entries.items():
        path = base / name
        require(path.is_file() and sha(path) == expected, label + ' changed: ' + name)


def root_unchanged(launch):
    actual = {}
    for folder in ('src/main/java', 'src/main/resources', 'build/classes/java/main', 'build/resources/main'):
        actual.update(tree(ROOT / folder, ROOT))
    require(actual == launch['code'], 'Frozen root source/class/resource tree changed')
    verify_entries(ROOT, launch['infrastructure'], 'Frozen infrastructure')
    require(tree(ROOT / 'build/submit', ROOT) == launch['submissionFiles'],
            'Pre-existing root submission files changed')


def audit():
    require(os.name == 'nt', 'This exact Gradle/process procedure targets the Windows host')
    launch = read(BENCH / 'launch.json')
    require(launch['name'] == NAME, 'Wrong launch manifest')
    require(not (BENCH / 'failed.json').exists(), 'Interrupted/failed benchmark cannot export')
    complete = read(BENCH / 'complete.json')
    decision = read(BENCH / 'decision.json')
    require(complete['strengthStatus'] == decision['strengthStatus'] == 'PASS',
            'Entire strength protocol must PASS before artifact work')
    require(read(BENCH / 'started.json')['launchSha256'] == sha(BENCH / 'launch.json'),
            'Benchmark launch identity changed')
    power = read(BENCH / 'power-request.json')
    require(power['status'] == 'released' and power.get('runOutcome') == 'completed',
            'Benchmark, including ablation and its power request, must finish first')
    root_unchanged(launch)
    require(len(launch['submissionFiles']) == 25, 'Expected all 25 prior submission files')
    require(complete['submissionFilesUnchanged'] == launch['submissionFiles'],
            'Benchmark completion did not preserve submissions')
    candidate = launch['candidate']
    require(re.fullmatch(r'current\?[A-Za-z0-9_?,=.+-]+', candidate) is not None,
            'Unexpected candidate specification characters')
    params = launch['resolvedParameters'][candidate]
    require(params['USE_ROLLOUT_NETWORK_AFTER_BASE'] == 'true'
            and params['USE_ROLLOUT_NETWORK'] == 'false', 'Wrong experimental policy')

    # Reuse the frozen gate, instead of restating or weakening its acceptance rules.
    sys.path.insert(0, str(ROOT / 'tools'))
    from practical_gate import assess, read_report
    labels = [('screen', 300), ('direct', 1000), ('style-candidate', 300),
              ('style-control', 300), ('full-candidate', 1000), ('full-control', 1000)]
    reports = [read_report(BENCH / (name + '.json'), games)
               if (BENCH / (name + '.json')).exists() else None for name, games in labels]
    require(reports[0] is not None, 'Missing screen report')
    audited = assess(*reports)
    require(audited == decision and audited['strengthStatus'] == 'PASS',
            'Saved decision does not match independently reassessed full reports')
    ablation = read_report(BENCH / 'ablation.json', 300)
    require(ablation['data']['a'] == candidate
            and ablation['data']['b'] == launch['ablationControl'], 'Wrong ablation bots')
    ablation_params = ablation['data']['paramsB']
    require(params.keys() == ablation_params.keys()
            and {key for key in params if params[key] != ablation_params[key]}
            == {'USE_ROLLOUT_NETWORK_AFTER_BASE'}, 'Ablation is not one-flag-only')
    used = set(launch['priorMapHashes'])
    grouped = {}
    for (label, _), report in zip(labels, reports):
        if report is not None:
            grouped.setdefault(label.split('-')[0], []).append(report)
    grouped['ablation'] = [ablation]
    report_hashes = {}
    for stage, members in grouped.items():
        stage_maps = set()
        for report in members:
            data = report['data']
            require(data['paramsA'] == launch['resolvedParameters'][data['a']]
                    and data['paramsB'] == launch['resolvedParameters'][data['b']],
                    'Benchmark effective parameters differ from launch')
            pool = launch['pools'][stage]
            require([p['seed'] for p in data['pairs']]
                    == list(range(pool['seedStart'], pool['seedStart'] + pool['pairs'])),
                    'Benchmark seed interval differs from declaration')
            require(data['poolSha256'] == launch['poolHashes'][stage], 'Pool identity differs')
            require(not used.intersection(report['maps']), 'Repeated prior or cross-stage map')
            stage_maps.update(report['maps'])
            report_hashes[relative(Path(report['path']))] = report['sha256']
        used.update(stage_maps)
    for _, path in MAPS:
        require((ROOT / path).is_file(), 'Missing declared process map: ' + path)
    require(not WORK.exists(), 'Artifact workspace already exists: no retry or overwrite')
    require(not DURABLE.exists(), 'Durable process report already exists: no overwrite')
    return launch, report_hashes


def execution_state(flags):
    import ctypes
    function = ctypes.WinDLL('kernel32', use_last_error=True).SetThreadExecutionState
    function.argtypes = [ctypes.c_uint]
    function.restype = ctypes.c_uint
    ctypes.set_last_error(0)
    result = int(function(flags))
    return result, ctypes.get_last_error() if result == 0 else 0


@contextmanager
def power_request():
    require(threading.current_thread() is threading.main_thread(), 'Power request must use main thread')
    record = {'startedAt': now(), 'threadId': threading.get_ident(),
              'requestFlags': '0x80000003', 'releaseFlags': '0x80000000',
              'globalPowerSettingsChanged': False, 'requestSucceeded': False,
              'releaseAttempted': False, 'status': 'not_requested'}
    try:
        result, error = execution_state(0x80000003)
        record.update(requestAt=now(), requestReturnCode=result, requestLastError=error,
                      requestSucceeded=result != 0, status='active' if result else 'request_failed')
        require(result != 0, 'Artifact power request failed; no commands started')
        yield record
    finally:
        if record['requestSucceeded']:
            record['releaseAttempted'] = True
            record['releaseAt'] = now()
            try:
                require(threading.get_ident() == record['threadId'], 'Power release changed threads')
                result, error = execution_state(0x80000000)
                record.update(releaseReturnCode=result, releaseLastError=error,
                              status='released' if result else 'release_failed')
            except BaseException as error:
                record.update(status='release_error', releaseError=repr(error))
        record['endedAt'] = now()
        save(WORK / 'artifact-power-request.json', record)
        require(record['status'] == 'released', 'Artifact power request failed to release')


def command(task, *args):
    return [str(WORK / 'gradlew.bat'), task, '--console=plain', *args]


def execute(args, label):
    started = now()
    log_path = WORK / 'logs' / (label + '.log')
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open('x', encoding='utf-8') as log:
        process = subprocess.Popen(args, cwd=WORK, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                                   errors='replace', creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            for line in process.stdout:
                log.write(line)
                log.flush()
                print(line, end='', flush=True)
            code = process.wait()
        except BaseException:
            if process.poll() is None:
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                               creationflags=subprocess.CREATE_NO_WINDOW, check=False)
            raise
    return {'command': args, 'startedAt': started, 'finishedAt': now(), 'exitCode': code,
            'log': relative(log_path), 'logSha256': sha(log_path)}


def summary_game(game):
    timings = game['timings']
    first = [t for t in timings if t['turn'] == 0]
    later = sorted(t['responseMs'] for t in timings if t['turn'] > 0 and t['responseMs'] is not None)
    # Preserve every failed/unobserved/late turn in the compact durable report. Complete timings
    # remain in the immutable per-map report, including successful first and subsequent turns.
    unusual = [t for t in timings if t['responseMs'] is None
               or t['responseMs'] > (1000 if t['turn'] == 0 else 50)]
    return {'player': game['player'], 'map': game['map'], 'seed': game['seed'],
            'winner': game['result']['winner'], 'errors': game['result']['error'],
            'timeouts': game['timeouts'], 'nominalOverruns': game['nominalOverruns'],
            'turnTimingCount': len(timings), 'firstTurn': first[0] if first else None,
            'laterResponseMaxMs': max(later) if later else None,
            'laterResponseP99Ms': later[max(0, (99 * len(later) + 99) // 100 - 1)] if later else None,
            'laterOver45Ms': sum(t > 45 for t in later), 'unusualTurns': unusual}


def run(launch, strength_reports):
    WORK.mkdir(parents=False, exist_ok=False)
    result = {'schemaVersion': 1, 'experiment': NAME, 'startedAt': now(),
              'candidate': launch['candidate'], 'strengthStatus': 'PASS',
              'launchSha256': sha(BENCH / 'launch.json'), 'runnerSha256': sha(Path(__file__)),
              'strengthReportHashes': strength_reports, 'processReadiness': 'incomplete',
              'policy': 'Fixed four maps, both sides, zero grace; no retries/promotion/upload',
              'maps': [], 'commands': [], 'errors': []}
    failure = None
    try:
        source_entries = {name: value for name, value in launch['code'].items()
                          if name.startswith(('src/main/java/', 'src/main/resources/'))}
        copied = dict(source_entries)
        for name in BUILD_FILES:
            require(name in launch['infrastructure'], 'Build file absent from launch: ' + name)
            copied[name] = launch['infrastructure'][name]
        for name, expected in copied.items():
            source, target = ROOT / name, WORK / name
            require(sha(source) == expected, 'Source changed during staging: ' + name)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
        verify_entries(WORK, copied, 'Staged input')
        result['copiedInputHashes'] = copied
        save(WORK / 'launch.json', launch)
        save(WORK / 'artifact-started.json', {'startedAt': now(), 'launchSha256': result['launchSha256'],
                                            'runnerSha256': result['runnerSha256'], 'inputs': copied})
        with power_request():
            root_unchanged(launch)
            built = execute(command('submit', '-PsubmitVersion=' + launch['candidate']), 'submit')
            result['commands'].append(built)
            require(built['exitCode'] == 0, 'Isolated submit failed; preserve workspace, do not retry')
            verify_entries(WORK, copied, 'Staged input after compilation')
            compiled = {name: value for name, value in launch['code'].items()
                        if name.startswith(('build/classes/java/main/', 'build/resources/main/'))}
            actual_compiled = {}
            for folder in ('build/classes/java/main', 'build/resources/main'):
                actual_compiled.update(tree(WORK / folder, WORK))
            require(actual_compiled == compiled, 'Staged compiled main code/resources differ from launch')
            result['stagedBytecodeMatchesLaunch'] = True
            manifest_path = WORK / 'build/submit/manifest.json'
            manifest = read(manifest_path)
            source_path = WORK / 'build/submit/Player.java'
            require(manifest['strategy'] == launch['candidate'], 'Wrong generated strategy')
            require(manifest['params'] == launch['resolvedParameters'][launch['candidate']],
                    'Generated effective parameters differ from measured candidate')
            require(manifest['sha256'] == sha(source_path), 'Generated source and manifest differ')
            require(manifest['charactersUtf16'] <= manifest['sourceLimit'] == 100000,
                    'Submission source limit failed')
            artifact_files = tree(WORK / 'build/submit', WORK)
            require((WORK / 'build/submit/classes/Player.class').is_file(), 'Missing compiled Player')
            result.update(artifactSource=relative(source_path), manifest=manifest,
                          manifestSha256=sha(manifest_path), artifactFileHashes=artifact_files)
            executable_hash = None
            for game_id, original_map in MAPS:
                root_unchanged(launch)
                verify_entries(WORK, copied, 'Staged input')
                require(tree(WORK / 'build/submit', WORK) == artifact_files, 'Exact Player artifact changed')
                map_path = ROOT / original_map
                map_hash = sha(map_path)
                report_path = WORK / 'reports' / ('process-' + game_id + '.json')
                require(not report_path.exists(), 'Process report exists: no retry')
                report_path.parent.mkdir(parents=True, exist_ok=True)
                checked = execute(command('procCheck', '-x', 'compileJava', '-x', 'processResources',
                                          '-Pbot=v05', '-Pgames=2', '-Pmap=' + str(map_path),
                                          '-PgraceMs=0', '-PprocessDiagnostics=false',
                                          '-Pout=' + str(report_path)), 'process-' + game_id)
                result['commands'].append(checked)
                require(report_path.is_file(), 'Process command produced no report; preserve and stop')
                report = read(report_path)
                require(report['sourceSha256'] == manifest['sha256'], 'Executed source identity differs')
                if executable_hash is None:
                    executable_hash = report['classesSha256']
                require(report['classesSha256'] == executable_hash, 'Executed classes identity changed')
                require(report['opponent'] == 'v05'
                        and report['opponentParams'] == launch['resolvedParameters']['v05'],
                        'Wrong process opponent or effective parameters')
                require(report['strict'] and report['graceMs'] == 0 and not report['ioDiagnostics'],
                        'Process deadlines or instrumentation changed')
                games = report['games']
                require(len(games) == 2 and [g['player'] for g in games] == [0, 1],
                        'Missing exact two-game side coverage')
                require(all(Path(g['map']).name == game_id + '.json' for g in games), 'Wrong process map')
                require(sha(map_path) == map_hash, 'Process map changed during execution')
                counted = sum(g['timeouts'] > 0 or any(e is not None for e in g['result']['error']) for g in games)
                require(report['problems'] == counted, 'Process problem total disagrees with games')
                require((checked['exitCode'] == 0) == (counted == 0), 'Command/report status mismatch')
                result['maps'].append({'gameId': game_id, 'map': original_map, 'mapSha256': map_hash,
                                       'report': relative(report_path), 'reportSha256': sha(report_path),
                                       'sourceSha256': report['sourceSha256'],
                                       'classesSha256': report['classesSha256'], 'javaVersion': report['javaVersion'],
                                       'problems': counted, 'games': [summary_game(g) for g in games]})
                require(tree(WORK / 'build/submit', WORK) == artifact_files, 'Process check changed Player')
                root_unchanged(launch)
                # A valid completed failure is evidence: continue to the next distinct declared map,
                # never repeat or replace this map's two games.
            result['gamesChecked'] = sum(len(m['games']) for m in result['maps'])
            result['problemGames'] = sum(m['problems'] for m in result['maps'])
            require(result['gamesChecked'] == 8, 'Incomplete fixed process check')
            result['processReadiness'] = 'PASS' if result['problemGames'] == 0 else 'NOT_PASSED'
    except BaseException as error:
        failure = error
        result['errors'].append(repr(error))
        result['processReadiness'] = 'ERROR'
    finally:
        try:
            root_unchanged(launch)
            result['submissionFilesUnchanged'] = tree(ROOT / 'build/submit', ROOT)
            result['rootFrozenInputsUnchanged'] = True
        except BaseException as error:
            result['errors'].append(repr(error))
            result['rootFrozenInputsUnchanged'] = False
            result['processReadiness'] = 'ERROR'
            if failure is None:
                failure = error
        power_path = WORK / 'artifact-power-request.json'
        if power_path.exists():
            result['powerRequest'] = read(power_path)
        result['finishedAt'] = now()
        save(WORK / 'artifact-result.json', result)
        save(DURABLE, result)
    print('ARTIFACT RESULT', result['processReadiness'], 'problem games', result.get('problemGames'), flush=True)
    if failure is not None:
        raise failure
    return 0 if result['processReadiness'] == 'PASS' else 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument('--audit-only', action='store_true')
    modes.add_argument('--run', action='store_true')
    options = parser.parse_args()
    frozen, report_hashes = audit()
    if options.audit_only:
        print('AUDIT PASS; fixed benchmark and ablation complete, all frozen inputs unchanged; no files written.')
    else:
        sys.exit(run(frozen, report_hashes))
