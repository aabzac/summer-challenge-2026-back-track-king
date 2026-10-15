"""Prospective batched-refresh experiment; no tuning, retries, boss or promotion.

Modes: prepare | freeze | process | strength | verify | summary.
Only process/strength play games. Shared practical-v4 execution stays unchanged.
Prior helper utilities are imported read-only; no previous experiment is modified.
"""
from __future__ import annotations
import hashlib, json, os, re, subprocess, sys, time
from pathlib import Path
import v09_deferred_strength as prior

ROOT = prior.ROOT
NAME = 'v09-batched-refresh-v1'
OUT = ROOT / 'build/analysis' / NAME
BENCH = ROOT / 'build/bench' / NAME
PRIOR = ROOT / 'build/bench' / (NAME + '-prior/maps.json')
DOC = ROOT / 'docs' / (NAME + '.md')
SUMMARY = ROOT / 'docs/evidence' / (NAME + '.json')
RUNTIME = prior.RUNTIME
SPEC = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true'
CONTROL = 'v09?NOISE=0.1'
OFF = 'current?NOISE=0.1'
SEED = 251015000
PROCESS_MAPS = ['replays/cg/903510176.json', 'replays/cg/boss/904146009.json',
                'replays/cg/boss/904148181.json', 'replays/cg/boss/904147739.json']
practical = prior.practical
now, require, sha, read, rel, pin, write, maps = (prior.now, prior.require, prior.sha,
    prior.read, prior.rel, prior.pin, prior.write, prior.maps)


def logged(label, argv, allowed=(0,)):
    write(OUT / (label + '-command.json'), {'startedAt': now(), 'argv': argv, 'cwd': str(ROOT)})
    env = dict(os.environ, JAVA_HOME=str(Path(read(RUNTIME)['java']).parent.parent))
    start = time.monotonic()
    with (OUT / (label + '.log')).open('xb') as log:
        code = subprocess.run(argv, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT).returncode
    write(OUT / (label + '-exit.json'), {'exitCode': code, 'finishedAt': now(),
          'elapsedSeconds': time.monotonic() - start, 'log': pin(OUT / (label + '.log'))})
    require(code in allowed, f'{label} failed ({code}); preserve evidence, no retry')
    return code


def prepare():
    require(DOC.is_file() and not BENCH.exists() and not PRIOR.exists(), 'New protocol/output required')
    require(not (OUT / 'prepared.json').exists(), 'Already prepared')
    OUT.mkdir(parents=True, exist_ok=True)
    intervals = [(SEED + off, SEED + off + n - 1) for _, _, n, off in practical.BLOCKS]
    own = {practical.pool_path(NAME, b) for b, *_ in practical.BLOCKS}
    for p in (ROOT / 'bench/pools').glob('*.json'):
        if p in own:
            continue
        d = read(p)
        if d.get('generator') == 'cg-sha1prng' and type(d.get('seedStart')) is int and type(d.get('pairs')) is int:
            require(all(d['seedStart'] + d['pairs'] - 1 < a or d['seedStart'] > b for a, b in intervals),
                    'Previously registered seed interval overlaps: ' + str(p))
    pools = [practical.ensure_pool(NAME, b, n, SEED + off) for b, _, n, off in practical.BLOCKS]
    rt = read(RUNTIME)
    configs = {}
    for label, spec in [('candidate', SPEC), ('control', CONTROL), ('off', OFF), ('processControl', 'v09')]:
        argv = [rt['java'], '-cp', rt['classpath'], 'btk.tools.BotCatalogTool', spec]
        command = OUT / ('params-' + label + '-command.json')
        if command.exists():
            # A configuration-audit rejection may retain successful read-only captures.
            # Recover those exact observations; never rerun/replace their commands or logs.
            saved = read(OUT / ('params-' + label + '-exit.json'))
            require(read(command)['argv'] == argv and read(command)['cwd'] == str(ROOT)
                    and saved['exitCode'] == 0
                    and saved['log']['sha256'] == sha(OUT / ('params-' + label + '.log')),
                    'Cannot reuse incomplete or changed parameter capture')
        else:
            logged('params-' + label, argv)
        lines = (OUT / ('params-' + label + '.log')).read_text(encoding='utf-8').splitlines()
        params = dict(m.groups() for line in lines if (m := re.fullmatch(r'  ([A-Z][A-Z0-9_]*)=(.*)', line)))
        require(params.get('NOISE') == '0.1', 'NOISE must match in every arm')
        require(params.get('USE_ROLLOUT_MISSED_DEADLINE', 'false') == 'false', 'Missed deadline must be OFF')
        configs[label] = {'spec': spec, 'params': params}
    expected = dict(configs['off']['params'], USE_BATCHED_REFRESH='true')
    require(configs['candidate']['params'] == expected, 'Candidate differs beyond batched refresh')
    off = dict(configs['off']['params'])
    for field in ['USE_ROLLOUT_ALL_CELL_RISK', 'USE_ROLLOUT_ROOT_FILL']:
        require(off.pop(field, None) == 'false' and field not in configs['control']['params'],
                'Only known inactive current-only fields may be omitted by frozen v09')
    require(off == configs['control']['params'] == configs['processControl']['params'],
            'OFF/current/frozen/process control semantic parameters differ')
    # Validate the retained development-map index against its archived and frozen identities.
    old_entries = {x['path']: x for x in read(prior.NEURAL_INDEX)['files']}
    old_freeze = read(ROOT / 'build/analysis/neural-target-strength-v1/freeze.json')
    require(sha(prior.NEURAL_PRIOR) == old_entries[rel(prior.NEURAL_PRIOR)]['sha256']
            == old_freeze['files'][rel(prior.NEURAL_PRIOR)], 'Prior development index changed')
    prior_file = practical.build_exclusion_index(OUT, 'prior')
    prior_reports = read(prior_file)
    union = maps(prior_reports) | maps(read(prior.NEURAL_PRIOR))
    audit_file = ROOT / 'docs/evidence/neural-v7-control-boss.json'
    files = set()
    for tree in (ROOT / 'replays').glob('cg*'):
        if tree.is_dir():
            files.update(p for p in tree.rglob('*.json') if p.stem.isdigit()
                         and not {'raw', '_metadata', 'rendered'}.intersection(part.lower() for part in p.parts))
    for row in read(audit_file)['rows']:
        p = ROOT / row['canonical']['path']
        require(sha(p) == row['canonical']['sha256'], 'Canonical v09 source changed')
        require(re.fullmatch('[0-9a-f]{64}', row['coreMapHash']), 'Invalid canonical map identity')
        files.add(p)
        union[row['coreMapHash']] = 1
    require(files, 'No saved CG maps')
    # Bound each Windows command line; these calls read engine map states, never play games.
    chunks, chunk = [], []
    for p in sorted(files):
        if sum(len(str(x)) + 1 for x in chunk) + len(str(p)) > 20000:
            chunks.append(chunk); chunk = []
        chunk.append(p)
    if chunk:
        chunks.append(chunk)
    inputs = [prior.NEURAL_INDEX, prior.NEURAL_PRIOR, audit_file]
    for i, group in enumerate(chunks, 1):
        dest = OUT / f'saved-cg-maps-{i}.json'
        logged(f'index-cg-{i}', [rt['java'], '-cp', rt['classpath'], 'btk.tools.NeuralV2Verify',
              'maps', ','.join(str(p) for p in group), 'unused', str(dest)])
        union.update(maps(read(dest)))
        inputs.append(dest)
    write(PRIOR, {'purpose': 'All prior benchmark maps, retained development index and current saved CG sources',
                 'hashScheme': 'sha256-utf8-Protocol.initBlock(initialState,0)', 'gamesPerMap': union,
                 'inputs': [pin(prior_file), *map(pin, inputs)]})
    selected = [p for p in (ROOT / 'build/submit').rglob('*') if p.is_file()]
    write(OUT / 'prepared.json', {'recordedAt': now(), 'sourceCommit': subprocess.check_output(
        ['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(), 'configs': configs,
        'protocol': pin(DOC), 'pools': list(map(pin, pools)), 'priorUnion': pin(PRIOR),
        'priorReports': {name: sha(ROOT / name) for name in prior_reports['sourceFiles']},
        'priorInputs': list(map(pin, inputs)), 'replayPins': list(map(pin, sorted(files))),
        'selectedSubmission': list(map(pin, sorted(selected))), 'processMaps': [pin(ROOT / p) for p in PROCESS_MAPS],
        'excludedMaps': len(union), 'missedDeadline': False, 'bossGames': 0, 'promotion': False})
    print(f'Prepared three pools; {len(union)} excluded maps; four matched full configurations', flush=True)


def inventory():
    return prior.inventory() | {Path(__file__).resolve(), Path(prior.__file__).resolve()} | {
        p.resolve() for p in (ROOT / 'src/test/java').rglob('*.java') if not prior.unrelated(p)}


def classes_hash(folder):
    digest = hashlib.sha256()
    for p in sorted(folder.rglob('*.class')):
        digest.update(p.relative_to(folder).as_posix().encode()); digest.update(b'\0'); digest.update(p.read_bytes())
    return digest.hexdigest()


def freeze():
    prepared = read(OUT / 'prepared.json')
    require(not BENCH.exists() and not (OUT / 'process-1-command.json').exists(), 'Freeze must precede games')
    require(sha(DOC) == prepared['protocol']['sha256'], 'Protocol changed since preparation')
    log = (OUT / 'preflight.log').read_text(encoding='utf-8', errors='replace')
    checks = re.findall(r'(\d+)/(\d+) replays reproduced exactly', log)
    require(checks and all(a == b and int(a) > 0 for a, b in checks), 'Successful engine preflight required')
    study = read(OUT / 'study/report.json')
    require(study.get('complete') is True and study.get('amplePhaseComplete') is True, 'Complete passing study required')
    expected = study['expectedPositions']
    require(expected > 0 and study['retainedRows'] == len(study['rows']) == expected, 'Study row count mismatch')
    require(study['gameIds'] == [907134258, 907134581, 907134651, 907134720], 'Study cohort changed')
    for phase in ['ample', 'nominal']:
        for label in ['v09', 'off', 'on']:
            require(study['totals'][phase + '/' + label]['calls'] == expected, 'Study calls missing')
    require(all(not row['ampleFrozenOffDifferences'] and not row['ampleOffOnDifferences']
                and all(row['ample'][label]['completeSearch'] for label in ['v09', 'off', 'on'])
                for row in study['rows']), 'Ample semantic comparison failed')
    artifacts = {}
    for label, directory in [('candidate', 'artifact'), ('off', 'artifact-off'), ('control', 'artifact-v09')]:
        folder = OUT / directory; manifest = read(folder / 'manifest.json'); source = folder / 'Player.java'
        require(manifest['params'] == prepared['configs'][label]['params'], 'Artifact parameter mismatch: ' + label)
        require(manifest['strategyId'] == ('v09' if label == 'control' else 'current'), 'Wrong artifact strategy')
        require(manifest['sha256'] == sha(source) and manifest['charactersUtf16'] <= 100000, 'Invalid artifact identity/size')
        require((folder / 'classes/Player.class').is_file(), 'Compiled artifact missing')
        artifacts[label] = {'source': pin(source), 'manifest': pin(folder / 'manifest.json'),
                            'classesSha256': classes_hash(folder / 'classes')}
        measured = study['artifacts'][{'candidate': 'on', 'control': 'v09', 'off': 'off'}[label]]
        require(measured['source']['sha256'] == artifacts[label]['source']['sha256']
                and measured['manifest']['sha256'] == artifacts[label]['manifest']['sha256']
                and measured['classesSha256'] == artifacts[label]['classesSha256'], 'Study artifact changed')
    with (OUT / 'protocol-before-results.md').open('xb') as f:
        f.write(DOC.read_bytes())
    files = inventory() | {RUNTIME, PRIOR}
    files.update(ROOT / name for name in prepared['priorReports'])
    for key in ['pools', 'priorInputs', 'replayPins', 'selectedSubmission', 'processMaps']:
        for item in prepared[key]:
            p = ROOT / item['path']; require(sha(p) == item['sha256'], 'Prepared input changed: ' + str(p)); files.add(p)
    files.update(p.resolve() for p in OUT.rglob('*') if p.is_file() and '__pycache__' not in p.parts)
    rt = read(RUNTIME); java = Path(rt['java']); jdk = java.parent.parent
    external = {java, Path(sys.executable)} | {Path(p) for p in rt['classpath'].split(os.pathsep) if p.endswith('.jar')}
    external.update(p for p in [jdk / 'release', jdk / 'lib/modules', jdk / 'bin/server/jvm.dll'] if p.is_file())
    write(OUT / 'freeze.json', {'recordedAt': now(), 'files': {rel(p): sha(p) for p in sorted(files)},
          'inventory': sorted(rel(p) for p in inventory()), 'runtime': {str(p): sha(p) for p in sorted(external)},
          'configs': prepared['configs'], 'artifacts': artifacts, 'protocol': pin(OUT / 'protocol-before-results.md')})
    print(f'Frozen {len(files)} files, all three exports and {len(external)} runtimes', flush=True)


def verify(allow_results=False):
    frozen = read(OUT / 'freeze.json')
    require(sorted(rel(p) for p in inventory()) == frozen['inventory'], 'Source/class inventory drift')
    for name, digest in frozen['files'].items():
        require((ROOT / name).is_file() and sha(ROOT / name) == digest, 'Frozen input changed: ' + name)
    for name, digest in frozen['runtime'].items():
        require(sha(Path(name)) == digest, 'External runtime changed: ' + name)
    if allow_results:
        require(DOC.read_bytes().startswith((OUT / 'protocol-before-results.md').read_bytes()), 'Prospective text must remain verbatim')
    return {'verifiedAt': now(), 'unchangedFiles': len(frozen['files']), 'unchangedRuntimes': len(frozen['runtime'])}


def process_check():
    write(OUT / 'before-process-verification.json', verify())
    for i, map_path in enumerate(PROCESS_MAPS, 1):
        verify()
        require(not (OUT / f'process-{i}.json').exists(), 'Process report already exists; no retry')
        logged(f'process-{i}', [str(ROOT / 'gradlew.bat'), 'procCheck', '--console=plain', '-Pbot=v09', '-Pgames=2',
               '-Pmap=' + map_path, '-PgraceMs=0', '-PprocessDiagnostics=false',
               '-PsubmitOut=' + rel(OUT / 'artifact'), '-Pout=' + rel(OUT / f'process-{i}.json')], allowed=(0, 1))
        verify()
    write(OUT / 'process-summary.json', process_summary())


def process_summary():
    frozen = read(OUT / 'freeze.json'); rows = []; blocking = []
    for i, map_path in enumerate(PROCESS_MAPS, 1):
        report = read(OUT / f'process-{i}.json'); code = read(OUT / f'process-{i}-exit.json')['exitCode']
        require(code in (0, 1) and len(report['games']) == 2, 'Incomplete process sample')
        require(report['sourceSha256'] == frozen['artifacts']['candidate']['source']['sha256']
                and report['classesSha256'] == frozen['artifacts']['candidate']['classesSha256'], 'Wrong process artifact')
        require(report['opponent'] == 'v09' and report['opponentParams'] == frozen['configs']['control']['params'], 'Wrong process opponent')
        require(report['strict'] and report['graceMs'] == 0 and not report['ioDiagnostics'], 'Process profile drift')
        for g, game in enumerate(report['games']):
            require(game['player'] == g and game['seed'] == 7000 + g and game['map'] == Path(map_path).name, 'Process map/seat/seed drift')
            bad = game['timeouts'] > 0 or game['nominalOverruns'] > 0 or any(game['result']['error'])
            for seat, error in enumerate(game['result']['error']):
                if error is None:
                    continue
                timeout = (seat == g and game['timeouts'] > 0
                           and error.startswith('exception: java.lang.IllegalStateException: TIMEOUT ')
                           and any(t['status'] == 'TIMEOUT' for t in game['timings']))
                if not timeout:
                    blocking.append({'map': Path(map_path).stem, 'candidateSeat': g, 'errorSeat': seat, 'error': error})
            rows.append({'map': Path(map_path).stem, 'seat': g, 'seed': 7000 + g, 'passed': not bad,
                         'timeouts': game['timeouts'], 'nominalOverruns': game['nominalOverruns'],
                         'errors': game['result']['error'], 'report': pin(OUT / f'process-{i}.json')})
    return {'games': 8, 'passedGames': sum(x['passed'] for x in rows),
            'status': 'PASS' if all(x['passed'] for x in rows) else 'FAIL', 'rows': rows,
            'blockingNonTimingErrors': blocking, 'localStrengthPermitted': not blocking, 'verification': verify()}


def checked(report, block):
    # Reuse the previous fixed v4 report auditor with this experiment's immutable constants.
    prior.OUT, prior.BENCH, prior.PRIOR, prior.SPEC, prior.CONTROL, prior.SEED, prior.NAME = OUT, BENCH, PRIOR, SPEC, CONTROL, SEED, NAME
    return prior.checked(report, block)


def strength_child():
    require(not BENCH.exists(), 'No retry/resume of strength games')
    require((OUT / 'process-summary.json').is_file(), 'Complete the fixed process panel first')
    process = process_summary()
    require(read(OUT / 'process-summary.json')['status'] == process['status'], 'Process summary mismatch')
    require(process['localStrengthPermitted'], 'Non-timing bot/process errors block strength')
    write(OUT / 'before-strength-verification.json', verify())
    original = practical.run_block
    os.environ['JAVA_HOME'] = str(Path(read(RUNTIME)['java']).parent.parent)
    def guarded(*args):
        verify()
        try:
            report = original(*args)
        except BaseException as error:
            write(OUT / (args[3] + '-failure-verification.json'), {'error': repr(error), 'verification': verify()}); raise
        audit = checked(report, args[3]); audit['verification'] = verify()
        write(OUT / (args[3] + '-verification.json'), audit)
        return report
    practical.run_block = guarded
    sys.argv = [str(ROOT / 'tools/run_practical_v4.py'), '--name', NAME, '--spec', SPEC, '--control', CONTROL, '--seed', str(SEED)]
    practical.main()
    write(OUT / 'after-strength-verification.json', verify())
    print('Local sequence complete. Process readiness remains separate; no boss or promotion.', flush=True)


def summary():
    reports, blocks = [], []
    for block, games, *_ in practical.BLOCKS:
        if not (BENCH / (block + '.json')).exists(): break
        report = practical.read_report(BENCH / (block + '.json'), games)
        reports.append(report); blocks.append(checked(report, block))
        require(read(BENCH / (block + '-decision.json')) == practical.gate.assess(*reports), 'Decision drift')
    require(reports, 'No completed blocks')
    decision = practical.gate.assess(*reports)
    require(decision['directStage'] in {'STOP_FUTILITY_300', 'STOP_FUTILITY_500', 'NOT_PASSED', 'PASS_1000'}, 'Incomplete local sequence')
    require(not any((BENCH / (b + '.json')).exists() for b, *_ in practical.BLOCKS[len(reports):]), 'Noncontiguous blocks')
    require(read(OUT / 'strength-exit.json')['exitCode'] == 0 and (OUT / 'after-strength-verification.json').is_file(), 'Incomplete execution')
    process = process_summary(); data = [r['data'] for r in reports]
    write(SUMMARY, {'schema': NAME, 'recordedAt': now(), 'candidate': SPEC, 'control': CONTROL,
          'noiseBoth': 0.1, 'missedDeadline': False, 'selectedBot': 'v09', 'promotion': False, 'bossGames': 0,
          'scope': 'LOCAL_ONLY', 'processReadiness': process, 'localDecision': decision,
          'overallReadiness': 'NOT_READY_PROCESS_FAILED' if process['status'] == 'FAIL' else 'NOT_ASSESSED_BOSS_NOT_RUN',
          'games': sum(d['games'] for d in data), 'freshPairedMaps': sum(len(r['maps']) for r in reports),
          'wins': sum(d['winsA'] for d in data), 'losses': sum(d['winsB'] for d in data), 'draws': sum(d['draws'] for d in data),
          'errors': [sum(d['errorsA'] for d in data), sum(d['errorsB'] for d in data)],
          'nativeMaxMs': [max(d['maxMsA'] for d in data), max(d['maxMsB'] for d in data)],
          'nativeSlowCalls': [sum(d['slowA'] for d in data), sum(d['slowB'] for d in data)],
          'blocks': blocks, 'freeze': pin(OUT / 'freeze.json'), 'prepared': pin(OUT / 'prepared.json'),
          'study': pin(OUT / 'study/report.json'), 'verification': verify(allow_results=True),
          'caveats': ['Local nominal 486/36ms, two workers, no submission protocol/warmup; not a process deadline test.',
              'Strict eight-game sample is separate and cannot establish universal timing safety.',
              'Fixed sample, no retries/tuning/extensions; boss evidence and arena authorization absent.']})
    print(json.dumps(pin(SUMMARY)))


def main():
    modes = {'prepare': prepare, 'freeze': freeze, 'process': process_check, '_strength': strength_child,
             'strength': lambda: logged('strength', [sys.executable, str(Path(__file__).resolve()), '_strength']),
             'verify': lambda: print(json.dumps(verify())), 'summary': summary}
    require(len(sys.argv) == 2 and sys.argv[1] in modes, 'Choose prepare|freeze|process|strength|verify|summary')
    modes[sys.argv[1]]()


if __name__ == '__main__':
    main()
