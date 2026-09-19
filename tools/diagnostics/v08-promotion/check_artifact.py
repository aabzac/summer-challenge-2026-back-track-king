"""Fixed eight-game check of the promoted default artifact; never retries or uploads."""
import hashlib
import importlib.util
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
WORK = ROOT / 'build/analysis/v08-promotion'
DURABLE = ROOT / 'docs/evidence/v08-promotion.json'
PROTOCOL = ROOT / 'docs/evidence/v08-promotion-protocol.json'
HELPER = ROOT / 'tools/diagnostics/network-after-base-v1/check_artifact.py'
spec = importlib.util.spec_from_file_location('prior_artifact_helpers', HELPER)
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)
h.WORK = WORK
sys.path.insert(0, str(ROOT / 'tools'))
from boss_batch import classes_hash


def execute(args, name):
    log = WORK / (name + '.log')
    started = h.now()
    with log.open('x', encoding='utf-8') as out:
        process = subprocess.Popen(args, cwd=ROOT, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                                   errors='replace', creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            for line in process.stdout:
                out.write(line)
                out.flush()
                print(line, end='', flush=True)
            code = process.wait()
        except BaseException:
            if process.poll() is None:
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                               creationflags=subprocess.CREATE_NO_WINDOW, check=False)
            raise
    return {'command': args, 'exitCode': code, 'startedAt': started,
            'finishedAt': h.now(), 'logSha256': h.sha(log)}


def normalize_marker(source):
    result, count = re.subn(rb'public static final String BUILD_MARKER = "[^"\r\n]*";',
                            b'public static final String BUILD_MARKER = "";', source)
    h.require(count == 1, 'Exactly one build marker required')
    return result


def main():
    h.require(not DURABLE.exists() and not (WORK / 'process-started.json').exists()
              and not PROTOCOL.exists(), 'Preserve completed/interrupted attempts; no retry')
    before = h.read(WORK / 'before.json')
    approved = h.read(ROOT / 'docs/evidence/strategy-search-opening-v1.json')
    h.require(approved['decision']['strengthStatus'] == 'PASS', 'Approved strength gate must pass')
    params = approved['launch']['resolvedParameters']['current?USE_PROTECTED_OPENING=1']
    manifest = h.read(ROOT / 'build/submit/manifest.json')
    h.require(manifest['strategy'] == 'current' and manifest['params'] == params,
              'Main submission must use every measured candidate parameter')
    player = ROOT / 'build/submit/Player.java'
    h.require(h.sha(player) == manifest['sha256'], 'Manifest/source mismatch')
    candidate = ROOT / 'build/cg-boss/artifacts/strategy-search-opening-v1/Player.java'
    h.require(h.sha(candidate) == approved['launch']['artifacts']['opening']['sourceSha256'],
              'Measured candidate source changed')
    old = candidate.read_bytes()
    declaration = b'public boolean USE_PROTECTED_OPENING = false;'
    h.require(old.count(declaration) == 1, 'Unexpected measured default declaration')
    expected = old.replace(declaration, b'public boolean USE_PROTECTED_OPENING = true;')
    h.require(normalize_marker(player.read_bytes()) == normalize_marker(expected),
              'Promoted source differs beyond opening default and recalculated marker')
    h.require(hashlib.sha256(normalize_marker(player.read_bytes())).hexdigest() == manifest['buildId'],
              'Build ID does not match exact promoted source')
    config_hash = hashlib.sha256(''.join(key+'='+params[key]+'\n' for key in sorted(params)).encode('utf-8')).hexdigest()
    marker = 'BTK_BUILD_V1 strategy=current build='+manifest['buildId']+' config='+config_hash
    actual_marker = re.search(rb'public static final String BUILD_MARKER = "([^"\r\n]*)";', player.read_bytes())
    h.require(manifest['configSha256'] == config_hash and manifest['marker'] == marker
              and actual_marker.group(1).decode('utf-8') == marker,
              'Recomputed marker/configuration identity differs from the actual source')
    for name in ('RolloutBot.java', 'ProtectedOpening.java'):
        path = 'src/main/java/btk/bot/current/' + name
        h.require(h.sha(ROOT/path) == approved['launch']['code'][path], 'Measured strategy changed')
    for group in ('oldFrozenFiles', 'priorEvidence'):
        h.verify_entries(ROOT, before[group], group)
    archived = {Path(p).relative_to('build/submit').as_posix(): value
                for p, value in before['previousSubmission'].items()}
    h.verify_entries(WORK/'previous-submit', archived, 'Archived selected submission')
    h.require(h.tree(WORK/'previous-submit', WORK/'previous-submit') == archived,
              'Archived submission contains missing or extra files')
    artifact = h.tree(ROOT/'build/submit', ROOT)
    executable = classes_hash(ROOT/'build/submit')
    inputs = {}
    for folder in ('src/main/java', 'src/main/resources', 'build/classes/java/main', 'build/resources/main'):
        inputs.update(h.tree(ROOT/folder, ROOT))
    for path in (Path(__file__), HELPER, ROOT/'build.gradle.kts', ROOT/'tools/boss_batch.py'):
        inputs[path.relative_to(ROOT).as_posix()] = h.sha(path)
    protocol = {'before': before, 'protocolText': (ROOT/'docs/v08-promotion.md').read_text(encoding='utf-8'),
                'protocolTextSha256': h.sha(ROOT/'docs/v08-promotion.md'),
                'runnerSha256': h.sha(Path(__file__)), 'helperSha256': h.sha(HELPER)}
    h.save(PROTOCOL, protocol)
    result = {'schemaVersion': 1, 'version': 'v08', 'startedAt': h.now(),
              'beforeSha256': h.sha(WORK/'before.json'), 'protocolSha256': h.sha(PROTOCOL),
              'sourceRevision': before['sourceCommit'], 'manifest': manifest,
              'sourceOnlyDifferences': ['USE_PROTECTED_OPENING default false to true', 'recomputed build marker'],
              'measuredCandidateSha256': h.sha(candidate), 'policySourceUnchanged': True,
              'artifactFileHashes': artifact, 'classesSha256': executable, 'inputIdentities': inputs,
              'priorFrozenIdentities': before['oldFrozenFiles'], 'priorEvidenceIdentities': before['priorEvidence'],
              'previousSubmissionIdentities': before['previousSubmission'],
              'maps': [], 'commands': [], 'errors': [], 'processReadiness': 'incomplete'}
    h.save(WORK/'process-started.json', result)
    failure = None
    try:
        with h.power_request():
            for item in before['fixedProcessPanel']:
                h.verify_entries(ROOT, inputs, 'Source/compiled inputs')
                h.require(h.tree(ROOT/'build/submit', ROOT) == artifact, 'Exact artifact changed')
                map_path = ROOT/item['path']
                h.require(h.sha(map_path) == item['sha256'], 'Fixed map changed')
                report_path = WORK/('process-' + map_path.stem + '.json')
                h.require(not report_path.exists(), 'Never overwrite a process report')
                command = execute([str(ROOT/'gradlew.bat'), 'procCheck', '--console=plain',
                    '-x', 'compileJava', '-x', 'processResources', '-Pbot=v07', '-Pgames=2',
                    '-Pmap='+str(map_path), '-PgraceMs=0', '-PprocessDiagnostics=false',
                    '-PsubmitOut=build/submit', '-Pout='+str(report_path)], 'process-'+map_path.stem)
                result['commands'].append(command)
                report = h.read(report_path)
                h.require(h.sha(map_path) == item['sha256'], 'Fixed map changed during process run')
                h.require(report['sourceSha256'] == manifest['sha256']
                          and report['classesSha256'] == executable, 'Executed artifact identity changed')
                h.require(report['opponent'] == 'v07'
                          and report['opponentParams'] == approved['launch']['resolvedParameters']['v07'],
                          'Frozen process opponent changed')
                h.require(report['strict'] and report['graceMs'] == 0 and not report['ioDiagnostics'],
                          'Strict process settings changed')
                games = report['games']
                h.require(len(games) == 2 and [g['player'] for g in games] == [0, 1], 'Incomplete sides')
                h.require(all(Path(g['map']).name == map_path.name for g in games), 'Wrong process map')
                problems = sum(g['timeouts'] > 0 or any(e is not None for e in g['result']['error']) for g in games)
                h.require(report['problems'] == problems and (command['exitCode'] == 0) == (problems == 0),
                          'Process exit/report status mismatch')
                result['maps'].append({'map': item['path'], 'mapSha256': h.sha(map_path),
                    'reportSha256': h.sha(report_path), 'problems': problems,
                    'games': [h.summary_game(g) for g in games]})
            result['gamesChecked'] = sum(len(m['games']) for m in result['maps'])
            result['problemGames'] = sum(m['problems'] for m in result['maps'])
            h.require(result['gamesChecked'] == 8, 'Incomplete fixed panel')
            result['processReadiness'] = 'PASS' if result['problemGames'] == 0 else 'NOT_PASSED'
    except BaseException as error:
        result['processReadiness'] = 'ERROR'
        result['errors'].append(repr(error))
        failure = error
    finally:
        try:
            h.verify_entries(ROOT, inputs, 'Source/compiled inputs')
            h.require(h.tree(ROOT/'build/submit', ROOT) == artifact, 'Exact artifact changed')
            h.verify_entries(ROOT, before['oldFrozenFiles'], 'Prior frozen versions')
            h.verify_entries(ROOT, before['priorEvidence'], 'Historical evidence')
            h.verify_entries(WORK/'previous-submit', archived, 'Archived selected submission')
            result['allFrozenInputsUnchanged'] = True
        except BaseException as error:
            result['errors'].append(repr(error))
            result['allFrozenInputsUnchanged'] = False
            result['processReadiness'] = 'ERROR'
            failure = failure or error
        if (WORK/'artifact-power-request.json').exists():
            result['powerRequest'] = h.read(WORK/'artifact-power-request.json')
        result['finishedAt'] = h.now()
        h.save(DURABLE, result)
    print('PROMOTION PROCESS', result['processReadiness'], 'problem games', result.get('problemGames'), flush=True)
    if failure:
        raise failure
    return 0 if result['processReadiness'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())
