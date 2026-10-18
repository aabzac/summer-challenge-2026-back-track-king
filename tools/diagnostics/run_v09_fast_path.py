"""Write-once saved-state fast-path diagnostic; no live calls or strength games.

The root task prepares baseline.json before implementation. Build commands and
measurements are explicit modes, never triggered by importing this module.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / 'build/analysis/v09-fast-path-v1'
PROTOCOL = ROOT / 'docs/v09-fast-path-v1.md'
PACKAGING_AMENDMENT = ROOT / 'docs/v09-fast-path-v1-packaging-amendment.md'
FLAG = 'USE_FAST_CHEAPEST_PATH'
BASE = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true'
GUARD = 'current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true'
SPECS = {'artifact': BASE + ',' + FLAG + '=true', 'artifact-off': BASE,
         'artifact-v09': 'v09', 'default-artifact': 'current',
         'guard-off': GUARD, 'guard-on': GUARD + ',' + FLAG + '=true'}
UNCHANGED = {
    'artifact-off': '2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a',
    'artifact-v09': 'aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac',
    'default-artifact': 'ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5',
    'guard-off': '3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c'}
MAPS = ['replays/cg/903510176.json', 'replays/cg/boss/904146009.json',
        'replays/cg/boss/904148181.json', 'replays/cg/boss/904147739.json']
AUDIT = ROOT / 'docs/evidence/neural-v7-control-boss.json'
HELPER_SOURCES = [ROOT / 'tools/diagnostics/V09ForecastCostStudy.java',
                  ROOT / 'tools/diagnostics/V09FastPathGuardStudy.java']
TEST_FILTERS = ['btk.core.*', 'btk.bot.current.Rollout*', 'btk.bot.current.ProtectedOpeningTest',
                'btk.submit.FastCheapestPathSubmissionTest', 'btk.submit.IncrementalInkRefreshSubmissionTest',
                'btk.submit.BatchedRefreshSubmissionTest', 'btk.submit.DeferredCommitSubmissionTest',
                'btk.submit.DeferredCommitCompactionTest', 'btk.submit.FileMergerTest',
                'btk.submit.CGRunnerTurnBudgetTest', 'btk.submit.TurnBudgetSubmissionTest',
                'btk.tools.GeneratedPolicyTest', 'btk.tools.ProfileGeneratedPolicyTest',
                'btk.tools.V09BatchedRefreshStudyTest', 'btk.tools.V09ForecastParityTest']


def need(condition, reason):
    if not condition:
        raise ValueError(reason)


def read(path):
    return json.loads(Path(path).read_bytes())


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def rel(path):
    return Path(path).relative_to(ROOT).as_posix()


def pin(path):
    return {'path': rel(path), 'sha256': sha(path), 'bytes': Path(path).stat().st_size}


def save(path, value):
    with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def baseline():
    result = read(RUN / 'baseline.json')
    need(result['protocolSha256'] == sha(PROTOCOL), 'Prospective protocol changed')
    need(result['artifactFiles'], 'Missing prior artifact preservation pins')
    for name, digest in result['artifactFiles'].items():
        need(sha(ROOT / name) == digest, 'Prior artifact changed: ' + name)
    return result


def runtime():
    return baseline()['runtime']


def run(label, command, allowed=(0,)):
    need(re.fullmatch('[a-z0-9-]+', label), 'Invalid journal label')
    outputs = [RUN / (label + suffix) for suffix in ('-command.json', '.log', '-exit.json')]
    need(not any(p.exists() for p in outputs), 'Command already attempted: ' + label)
    command = list(map(str, command))
    save(outputs[0], {'command': command, 'cwd': str(ROOT), 'automaticRetry': False})
    env = dict(os.environ, JAVA_HOME=str(Path(runtime()['java']).parent.parent))
    start = time.monotonic()
    with outputs[1].open('xb') as log:
        process = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    save(outputs[2], {'exitCode': process.returncode, 'seconds': time.monotonic() - start,
                      'logSha256': sha(outputs[1])})
    print(outputs[1].read_text(encoding='utf-8', errors='replace')[-12000:], flush=True)
    need(process.returncode in allowed, f'{label} failed with exit {process.returncode}; retain all evidence')
    return process.returncode


def source_files():
    return {p for tree in ('src/main/java/btk', 'src/test/java/btk')
            for p in (ROOT / tree).rglob('*.java') if not {'pduhard', 'ppo'}.intersection(p.parts)
            and p.name != 'PduhardBeamBotTest.java'}


def tests():
    baseline()
    save(RUN / 'test-source-pins.json', {rel(p): sha(p) for p in sorted(source_files())})
    command = [ROOT / 'gradlew.bat', 'test', '--offline', '--console=plain']
    for name in TEST_FILTERS:
        command += ['--tests', name]
    # Exit1 is expected only for the documented catalog pin, checked from XML
    # by build-artifacts. Any other failure remains recorded and blocks export.
    run('preflight-tests', command, allowed=(0, 1))


def tests_packaging():
    baseline()
    need(PACKAGING_AMENDMENT.is_file(), 'Document the packaging amendment before its focused test run')
    need(not any((RUN / name).exists() for name in ('measurement-freeze.json', 'compare', 'guards',
         'artifact', 'guard-on', 'test-results', 'preflight-test-verdict.json')),
         'This amendment must precede exports and measurements')
    initial = RUN / 'test-results-initial'
    xmls = sorted(initial.glob('TEST-*.xml'))
    need(xmls and read(RUN / 'preflight-tests-exit.json')['exitCode'] == 1, 'Retain the initial failed attempt')
    suites = [ET.parse(p).getroot() for p in xmls]
    total = sum(int(s.attrib['tests']) for s in suites)
    need(total == 261 and not any(int(s.attrib.get(k, '0')) for s in suites for k in ('errors', 'skipped')),
         'Unexpected initial test inventory or errors')
    failures = [{'suite': s.attrib['name'], 'test': c.attrib['name'], 'text': ''.join(c.find('failure').itertext())}
                for s in suites for c in s.findall('testcase') if c.find('failure') is not None]
    need(len(failures) == 2, 'Only the two retained initial failures authorize this packaging repair')
    expected = {
        ('btk.submit.FileMergerTest', 'characterLimitIsCheckedBeforePublishing()'):
            'Experiment implementation changed: btk/core/Pathing.class',
        ('btk.submit.FastCheapestPathSubmissionTest', 'mainAndGuardArtifactsFitAndCompactionKeepsEveryExecutableClass()'):
            'Submission exceeds 100000 characters: 100008'}
    need({(f['suite'], f['test']) for f in failures} == set(expected)
         and all(expected[(f['suite'], f['test'])] in f['text'] for f in failures),
         'Initial failures differ from the documented catalog and source-size failures')
    original = read(RUN / 'test-source-pins.json')
    need(sorted(original) == sorted(rel(p) for p in source_files()), 'Initial source inventory changed')
    changed = ['src/main/java/btk/core/Pathing.java', 'src/main/java/btk/submit/FileMerger.java',
               'src/test/java/btk/core/FastCheapestPathTest.java',
               'src/test/java/btk/submit/FastCheapestPathSubmissionTest.java']
    snapshots = RUN / 'packaging-before-repair'
    need({p.name for p in snapshots.iterdir() if p.is_file()} == {Path(n).name for n in changed},
         'Exactly four before-repair source snapshots required')
    for name, digest in original.items():
        if name not in changed:
            need(sha(ROOT / name) == digest, 'Unrelated source change during packaging repair: ' + name)
            continue
        before = snapshots / Path(name).name
        need(sha(before) == digest, 'Before-repair snapshot differs from tested source: ' + name)
        content = before.read_bytes()
        need(b'cheapestNeighbors' in content and (ROOT / name).read_bytes()
             == content.replace(b'cheapestNeighbors', b'pathNeighbors'),
             'Only the private identifier replacement is authorized: ' + name)
    inputs = [RUN / 'test-source-pins.json', RUN / 'preflight-tests-command.json',
              RUN / 'preflight-tests-exit.json', RUN / 'preflight-tests.log', PACKAGING_AMENDMENT,
              *[p for p in initial.rglob('*') if p.is_file()], *[snapshots / Path(n).name for n in changed]]
    save(RUN / 'packaging-repair-validation.json', {'complete': True, 'passed': True,
         'initialTests': total, 'initialPassed': total - len(failures), 'initialFailures': failures,
         'changedFiles': changed, 'replacement': {'from': 'cheapestNeighbors', 'to': 'pathNeighbors'},
         'onlyIdentifierReplacement': True, 'noAssertionRelaxation': True, 'measurementsBeforeRepair': 0,
         'inputPins': {rel(p): {'sha256': sha(p), 'bytes': p.stat().st_size} for p in sorted(inputs)}})
    save(RUN / 'test-source-pins-packaging.json', {rel(p): sha(p) for p in sorted(source_files())})
    command = [ROOT / 'gradlew.bat', 'test', '--offline', '--console=plain']
    for name in TEST_FILTERS:
        command += ['--tests', name]
    run('preflight-tests-packaging', command, allowed=(0, 1))


def check_test_sources():
    amended = RUN / 'test-source-pins-packaging.json'
    if amended.exists():
        repair = read(RUN / 'packaging-repair-validation.json')
        need(repair['complete'] and repair['passed'] and repair['onlyIdentifierReplacement']
             and repair['noAssertionRelaxation'], 'Verified packaging-only amendment required')
        for name, info in repair['inputPins'].items():
            need(sha(ROOT / name) == info['sha256'], 'Retained packaging input changed: ' + name)
        need(read(RUN / 'preflight-tests-packaging-exit.json')['exitCode'] in (0, 1),
             'Completed amended focused-test attempt required')
    pins = read(amended if amended.exists() else RUN / 'test-source-pins.json')
    need(sorted(pins) == sorted(rel(p) for p in source_files()), 'Tested source inventory changed')
    for name, digest in pins.items():
        need(sha(ROOT / name) == digest, 'Source changed since focused tests: ' + name)


def retain_tests():
    check_test_sources()
    source = ROOT / 'build/test-results/test'
    target = RUN / 'test-results'
    need(source.is_dir() and not target.exists(), 'Fresh complete test results required')
    # Copy before checking: unexpected failures must also remain available.
    shutil.copytree(source, target)
    suites = [ET.parse(p).getroot() for p in sorted(target.glob('TEST-*.xml'))]
    need(suites, 'No JUnit reports')
    failures = []
    summary = {}
    for suite in suites:
        name = suite.attrib['name']
        summary[name] = {k: int(suite.attrib.get(k, '0')) for k in ('tests', 'failures', 'errors', 'skipped')}
        for case in suite.findall('testcase'):
            for kind in ('failure', 'error'):
                problem = case.find(kind)
                if problem is not None:
                    failures.append({'suite': name, 'test': case.attrib['name'], 'kind': kind,
                                     'text': ''.join(problem.itertext())})
    known = [f for f in failures if f['suite'] == 'btk.submit.FileMergerTest'
             and f['test'] == 'characterLimitIsCheckedBeforePublishing()' and f['kind'] == 'failure'
             and 'Experiment implementation changed: btk/core/Pathing.class' in f['text']]
    new_core = [n for n in summary if n.startswith('btk.core.') and 'Fast' in n and 'Path' in n]
    new_submit = [n for n in summary if n.startswith('btk.submit.') and 'Fast' in n and 'Path' in n]
    required = new_core + new_submit
    new_pass = bool(new_core and new_submit) and all(summary[n]['tests'] > 0
        and not any(summary[n][k] for k in ('failures', 'errors', 'skipped')) for n in required)
    core_pass = any(n.startswith('btk.core.') for n in summary) and all(
        not any(v[k] for k in ('failures', 'errors', 'skipped'))
        for n, v in summary.items() if n.startswith('btk.core.'))
    total = sum(s['tests'] for s in summary.values())
    skipped = sum(s['skipped'] for s in summary.values())
    accepted = new_pass and core_pass and len(failures) == len(known) <= 1 and skipped == 0
    verdict = {'tests': total, 'passed': total - len(failures) - skipped, 'skipped': skipped,
               'failures': failures, 'suites': summary, 'knownHistoricalCatalogRejection': bool(known),
               'allNewCoreAndSubmissionTestsPass': new_pass, 'allReportedCoreTestsPass': core_pass,
               'reportedSuitesPass': not failures and not skipped, 'fullSuitePass': False,
               'preflightAccepted': accepted,
               'focusedSuiteAttempts': 2 if (RUN / 'test-source-pins-packaging.json').exists() else 1,
               'noFailedTestRerun': not (RUN / 'test-source-pins-packaging.json').exists(),
               'packagingRepair': pin(RUN / 'packaging-repair-validation.json')
                    if (RUN / 'test-source-pins-packaging.json').exists() else None,
               'noMeasurementsRepeated': True,
               'scope': 'Reported focused suites, not the entire repository suite; any packaging repair retains its initial failed attempt.'}
    save(RUN / 'preflight-test-verdict.json', verdict)
    need(accepted, 'Unexpected/missing preflight tests; preserved reports, no build or measurements')


def build_artifacts():
    baseline()
    retain_tests()
    run('engine', [ROOT / 'gradlew.bat', 'cgValidate', '--offline', '--console=plain'])
    matches = re.findall(r'(\d+)/(\d+) replays reproduced exactly',
                         (RUN / 'engine.log').read_text(encoding='utf-8', errors='replace'))
    need(len(matches) == 1 and int(matches[0][0]) > 0 and matches[0][0] == matches[0][1],
         'Full exact engine validation required')
    save(RUN / 'engine-verdict.json', {'complete': True, 'exact': int(matches[0][0]),
                                      'total': int(matches[0][1]), 'log': pin(RUN / 'engine.log')})
    for label, spec in SPECS.items():
        need(not (RUN / label).exists(), 'Preserve existing artifact: ' + label)
        run('export-' + label, [ROOT / 'gradlew.bat', 'submit', '--offline', '--console=plain',
                               '-PsubmitVersion=' + spec, '-PsubmitOut=' + str(RUN / label)])
    artifact_checks()
    baseline()


def class_digest(directory):
    result = hashlib.sha256()
    files = sorted(Path(directory).rglob('*.class'))
    need(files and (Path(directory) / 'Player.class').is_file(), 'Generated executable missing')
    for path in files:
        result.update(path.relative_to(directory).as_posix().encode('utf-8'))
        result.update(b'\0')
        result.update(path.read_bytes())
    return result.hexdigest()


def artifact_checks():
    result = {}
    for label, spec in SPECS.items():
        folder = RUN / label
        manifest = read(folder / 'manifest.json')
        source = folder / 'Player.java'
        chars = len(source.read_text(encoding='utf-8').encode('utf-16-le')) // 2
        need(manifest['sha256'] == sha(source), 'Manifest/source mismatch: ' + label)
        need(chars == manifest['charactersUtf16'] and chars <= 100000, 'Source size: ' + label)
        need(manifest['strategy'] == spec, 'Unexpected export recipe: ' + label)
        if label in UNCHANGED:
            need(sha(source) == UNCHANGED[label], 'Disabled historical source changed: ' + label)
        params = manifest['params']
        need(params.get('TURN_BUDGET_MS', '36') == '36', 'Keep36 ms: ' + label)
        need(params.get('USE_INCREMENTAL_INK_REFRESH', 'false') == 'false', 'Ink optimization must stay OFF')
        result[label] = {'source': pin(source), 'manifest': pin(folder / 'manifest.json'),
                         'classesSha256': class_digest(folder / 'classes'),
                         'charactersUtf16': chars, 'params': params}
    for on, off in [('artifact', 'artifact-off'), ('guard-on', 'guard-off')]:
        a, b = dict(result[on]['params']), dict(result[off]['params'])
        need(a.pop(FLAG, 'false') == 'true' and b.pop(FLAG, 'false') == 'false' and a == b,
             'Optimization must be the only parameter difference: ' + on)
    main = result['artifact']['params']
    guard = result['guard-on']['params']
    need(main.get('NOISE') == '0.1' and main.get('USE_BATCHED_REFRESH') == 'true'
         and main.get('USE_ROLLOUT_MISSED_DEADLINE', 'false') == 'false', 'Wrong main recipe')
    need(float(guard['NOISE']) == 0 and guard.get('USE_BATCHED_REFRESH', 'false') == 'false'
         and guard.get('USE_ROLLOUT_MISSED_DEADLINE') == 'true', 'Wrong historical guard recipe')
    return result


def inventory(roots):
    return sorted(rel(p) for folder in roots for p in Path(folder).rglob('*') if p.is_file())


def freeze():
    base = baseline()
    check_test_sources()
    need(not (RUN / 'measurement-freeze.json').exists(), 'Preserve existing measurement freeze')
    need(not any((RUN / name).exists() for name in ('compare', 'guards', 'process-summary.json')),
         'Freeze must precede measurements')
    need(read(RUN / 'preflight-test-verdict.json')['preflightAccepted'], 'Tests must pass preflight')
    need(read(RUN / 'engine-verdict.json')['complete'], 'Engine validation required')
    artifacts = artifact_checks()
    classes, resources, helpers = [RUN / p for p in ('runtime/classes', 'runtime/resources', 'helper-classes')]
    need(not any(p.exists() for p in (classes, resources, helpers)), 'Fresh isolated runtime required')
    shutil.copytree(ROOT / 'build/classes/java/main', classes, ignore=shutil.ignore_patterns('pduhard', 'ppo'))
    shutil.copytree(ROOT / 'build/resources/main', resources)
    helpers.mkdir()
    rt = base['runtime']
    jars = [p for p in rt['classpath'].split(';') if p.endswith('.jar')]
    need(jars, 'Missing declared dependencies')
    cp = ';'.join(map(str, [helpers, classes, resources, *jars]))
    run('compile-helpers', [rt['javac'], '-encoding', 'UTF-8', '-cp', cp, '-d', helpers, *HELPER_SOURCES])
    files = {p for p in RUN.rglob('*') if p.is_file() and '__pycache__' not in p.parts}
    files.update(ROOT / p for p in base['artifactFiles'])
    files.update(source_files())
    files.update([Path(__file__).resolve(), PROTOCOL, AUDIT, *HELPER_SOURCES, ROOT / 'build.gradle.kts',
                  ROOT / 'build/analysis/v09-full-action-v1/selection.json', *[ROOT / p for p in MAPS]])
    if (RUN / 'test-source-pins-packaging.json').exists():
        files.add(PACKAGING_AMENDMENT)
    for row in read(AUDIT)['rows']:
        if row['gameId'] in (907134258, 907134581, 907134651, 907134720):
            files.add(ROOT / row['canonical']['path'])
    jdk = Path(rt['java']).parent.parent
    external = [Path(rt['java']), Path(rt['javac']), *map(Path, jars),
                jdk / 'bin/server/jvm.dll', jdk / 'lib/modules', Path(sys.executable)]
    managed = [helpers, classes, resources, *[RUN / name / 'classes' for name in SPECS]]
    source_inventory = sorted(rel(p) for p in source_files())
    save(RUN / 'measurement-freeze.json', {'experiment': 'v09-fast-path-v1', 'sourceCommit': base['commit'],
         'files': {rel(p): sha(p) for p in sorted(files)},
         'runtimeFiles': {str(p): sha(p) for p in external}, 'java': rt['java'], 'classpath': cp,
         'managedDirectories': [rel(p) for p in managed], 'managedInventory': inventory(managed),
         'sourceInventory': source_inventory, 'artifacts': artifacts,
         'newGamesBeforeProcess': 0, 'newBossGames': 0, 'promotion': False,
         'budgetsMs': {'first': 486, 'later': 36, 'ample': 5000, 'warmup': 500},
         'fullResponseLimitsMs': {'first': 1000, 'later': 50, 'grace': 0}})
    check_freeze()
    print('Frozen', len(files), 'workspace files and', len(external), 'runtime files', flush=True)


def check_freeze():
    frozen = read(RUN / 'measurement-freeze.json')
    for field in ('files', 'runtimeFiles'):
        for name, digest in frozen[field].items():
            need(sha(ROOT / name) == digest, 'Frozen input changed: ' + name)
    need(inventory([ROOT / p for p in frozen['managedDirectories']]) == frozen['managedInventory'],
         'Executable inventory changed')
    current_sources = sorted(rel(p) for p in source_files())
    need(current_sources == frozen['sourceInventory'], 'Source inventory changed')
    baseline()
    return frozen


def completed_panel(name):
    report = read(RUN / name / 'report.json')
    need(report['complete'] and report['amplePhaseComplete'] and report['nominalPhaseComplete'],
         'Incomplete prior panel: ' + name)
    return report


def check_guards():
    report = completed_panel('guards')
    for phase in ('ample', 'nominal'):
        rows = report[phase]
        need(rows['pairs'] == 14 and rows['baselineWins'] >= 6 and rows['candidateWins'] >= 6
             and rows['lostBaselineWins'] == 0, 'Winning guard failed: ' + phase)
        candidate = [r for r in rows['keyTurn40'] if r['arm'] == 'candidate']
        need(len(candidate) == 2, 'Both turn40 reply regimes required')
        for row in candidate:
            step = row['step']
            d = step['diagnostics']
            need(step['turn'] == 40 and step['ownAction']['target'] == 33
                 and d['deadlineChanged'] and d['deadlineInterventionTarget'] == 33
                 and d['deadlineStatus'] == 'intervened'
                 and d['deadlineWCompleted'] == d['deadlineACompleted'] == d['deadlineWr0Completed']
                 and d['deadlineACompleted'] > 0, 'Turn40 attack/guard conditions failed: ' + phase)
        branches = [json.loads(line) for line in (RUN / 'guards' / (phase + '.jsonl')).read_text(
                    encoding='utf-8').splitlines() if line.strip()]
        closures = [r for r in branches if r['gameId'] == 907134720 and r['regime'] == 'NATIVE_FIXED_PORTFOLIO']
        need(len(closures) == 6 and {r['startTurn'] for r in closures} == {46, 47, 49, 50, 51, 52},
             'Missing historical native winning starts')
        need(all(r[arm]['complete'] and r[arm]['terminalOutcome'] == 'WIN'
                 for r in closures for arm in ('baseline', 'candidate')), 'Native closing win lost')


def process_stage(frozen):
    completed_panel('compare')
    check_guards()
    outputs = [RUN / f'process-{i}.json' for i in range(1, 5)]
    need(not any(p.exists() for p in outputs) and not (RUN / 'process-summary.json').exists(),
         'The fixed process sample cannot be replaced or resumed by replaying games')
    prefix = [frozen['java'], '-cp', frozen['classpath']]
    results = []
    for i, (name, output) in enumerate(zip(MAPS, outputs), 1):
        check_freeze()
        code = run('process-' + str(i), prefix + ['btk.runner.ProcessCheck', 'v09', '2', name,
                   '0', str(output), 'false', str(RUN / 'artifact')], allowed=(0, 1))
        report = read(output)
        need(len(report['games']) == 2 and report['strict'] and report['graceMs'] == 0
             and report['ioDiagnostics'] is False and report['opponent'] == 'v09', 'Wrong strict process sample')
        need(report['sourceSha256'] == frozen['artifacts']['artifact']['source']['sha256']
             and report['classesSha256'] == frozen['artifacts']['artifact']['classesSha256'],
             'Process executed a different artifact')
        issues = []
        for j, game in enumerate(report['games']):
            need((game['game'], game['player'], game['seed'], game['map']) == (j, j, 7000 + j, Path(name).name),
                 'Wrong fixed process game identity')
            if game['timeouts'] or game['nominalOverruns'] or any(game['result']['error']):
                issues.append({'game': j, 'kind': 'game-errors-or-deadlines'})
            timings = game['timings']
            if len(timings) != game['result']['turns'] or [t['turn'] for t in timings] != list(range(len(timings))):
                issues.append({'game': j, 'kind': 'missing-or-unordered-replies'})
            for timing in timings:
                limit = 1000 if timing['turn'] == 0 else 50
                need(timing['budgetMs'] == limit and timing['graceMs'] == 0, 'Process budget changed')
                if timing['status'] != 'OK' or timing['responseMs'] is None or timing['responseMs'] > limit:
                    issues.append({'game': j, 'turn': timing['turn'], 'kind': 'response-failure'})
        results.append({'report': pin(output), 'exitCode': code, 'problems': report['problems'], 'issues': issues})
        # Deadline failures are retained; finish all eight once, without tuning or retries.
        check_freeze()
    passed = all(r['exitCode'] == 0 and r['problems'] == 0 and not r['issues'] for r in results)
    save(RUN / 'process-summary.json', {'complete': True, 'passed': passed, 'games': 8,
         'reports': results, 'strict': True, 'ioDiagnostics': False, 'newBossGames': 0,
         'promotion': False, 'noRetryOrReplacement': True})
    need(passed, 'Fixed process sample did not pass; retain failures, no strength authorization')


def measured(stage):
    frozen = check_freeze()
    prefix = [frozen['java'], '-cp', frozen['classpath']]
    if stage == 'compare':
        run(stage, prefix + ['btk.tools.V09ForecastCostStudy', 'compare', str(AUDIT),
            str(RUN / 'artifact-off'), str(RUN / 'artifact'), str(RUN / 'compare'), FLAG])
        completed_panel('compare')
    elif stage == 'guards':
        completed_panel('compare')
        run(stage, prefix + ['btk.tools.V09FastPathGuardStudy', str(RUN / 'guard-off'),
                            str(RUN / 'guard-on'), str(RUN / 'guards'), FLAG])
        check_guards()
    elif stage == 'process':
        process_stage(frozen)
    else:
        raise ValueError('Unknown measured stage')
    check_freeze()


def readiness():
    frozen = check_freeze()
    compare = completed_panel('compare')
    check_guards()
    process = read(RUN / 'process-summary.json')
    need(process['complete'] and process['games'] == 8, 'Complete strict sample required')
    ample = [json.loads(line) for line in (RUN / 'compare/ample.jsonl').read_text(
             encoding='utf-8').splitlines() if line.strip()]
    need(len(ample) == 524 and all(not r['differences'] and r['baseline']['completeSearch']
         and r['candidate']['completeSearch'] for r in ample), 'All524 ample decisions must match')
    totals = compare['totals']
    a, b = totals['ample/candidate'], totals['ample/baseline']
    for key in ('calls', 'routeQueries', 'completedComparisons', 'simulatedTurns'):
        need(a[key] == b[key], 'Equal-work semantic count mismatch: ' + key)
    need(a['calls'] == 524 and a['totalWallMs'] > 0 and b['totalWallMs'] > 0, 'Invalid ample totals')
    na, nb = totals['nominal/candidate'], totals['nominal/baseline']
    need(na['calls'] == nb['calls'] == 524 and nb['completedComparisons'] > 0, 'Invalid36 ms totals')
    speed = 1 - a['totalWallMs'] / b['totalWallMs']
    gain = na['completedComparisons'] / nb['completedComparisons'] - 1
    errors = timeouts = overruns = missing = games = 0
    for i in range(1, 5):
        for game in read(RUN / f'process-{i}.json')['games']:
            games += 1
            errors += sum(e is not None for e in game['result']['error'])
            timeouts += game['timeouts']
            overruns += game['nominalOverruns']
            missing += max(0, game['result']['turns'] - len(game['timings']))
            missing += sum(t['responseMs'] is None for t in game['timings'])
    engine = read(RUN / 'engine-verdict.json')
    ready = process['passed'] and not any((errors, timeouts, overruns, missing)) and speed > 0 and gain > 0
    inputs = [RUN / 'measurement-freeze.json', RUN / 'preflight-test-verdict.json', RUN / 'engine-verdict.json',
              RUN / 'process-summary.json', RUN / 'compare/report.json', RUN / 'compare/ample.jsonl',
              RUN / 'compare/nominal.jsonl', RUN / 'guards/report.json', RUN / 'guards/ample.jsonl',
              RUN / 'guards/nominal.jsonl', *[RUN / f'process-{i}.json' for i in range(1, 5)]]
    inputs += list(RUN.glob('*.log')) + list(RUN.glob('*-command.json')) + list(RUN.glob('*-exit.json'))
    save(RUN / 'readiness.json', {'complete': True, 'status': 'PASS' if ready else 'NOT_READY',
         'strengthReady': ready, 'engineExact': engine['exact'], 'parity': {'positions': 524, 'pass': True},
         'guards': {'ampleExact': True, 'turn40Target33': True, 'sixWinningClosures': True},
         'process': {'games': games, 'errors': errors, 'timeouts': timeouts, 'overruns': overruns,
                     'missing': missing, 'pass': process['passed']},
         'performance': {'equalWorkTimeReduction': speed, 'comparisonGain36': gain,
                         'ampleBaseline': b, 'ampleCandidate': a, 'nominalBaseline': nb, 'nominalCandidate': na},
         'artifacts': frozen['artifacts'], 'inputPins': {rel(p): {'sha256': sha(p), 'bytes': p.stat().st_size}
                                                       for p in sorted(set(inputs))},
         'independentAuditStillRequired': True, 'promotion': False, 'newBossGames': 0})
    check_freeze()
    print('Strength eligibility:', 'PASS; independent audit still required' if ready else 'NOT_READY', flush=True)


if __name__ == '__main__':
    need(len(sys.argv) >= 2, 'Mode tests|tests-packaging|build-artifacts|freeze|verify|compare|guards|process|readiness|command required')
    mode = sys.argv[1]
    if mode == 'command':
        need(len(sys.argv) >= 4, 'command LABEL EXECUTABLE [ARGS]')
        run(sys.argv[2], sys.argv[3:])
    else:
        need(len(sys.argv) == 2, 'Unexpected mode arguments')
        if mode == 'tests':
            tests()
        elif mode == 'tests-packaging':
            tests_packaging()
        elif mode == 'build-artifacts':
            build_artifacts()
        elif mode == 'freeze':
            freeze()
        elif mode == 'verify':
            check_freeze()
        elif mode == 'readiness':
            readiness()
        else:
            measured(mode)
