"""User-authorized local strength despite the retained ink40 process timeouts.

Load the unchanged prior driver privately: reuse its map preparation, exact
artifact checks, report checks and practical-v4 schedule. Only the explicit
timeout prerequisite and local scope metadata change. No timing games repeat.
"""
from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location('ink40_local_base', Path(__file__).with_name('run_v09_ink40_strength.py'))
base = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(base)
base.DOC = ROOT / 'docs/v09-ink40-strength-v1.md'
base.HARNESS = ROOT / 'build/analysis/v09-ink40-local-strength-harness-v1'
TEST_COUNT = 35
AUTHORIZATION = {'timeoutGateWaived': True, 'localStrengthOnly': True, 'promotion': False,
    'userRequest': 'Давай уберём эту проверку с таймаутами. И хапустим бенч для40мс',
    'scope': 'Run the registered local inkON/OFF comparison at40 despite retained process timeouts. Keep all other validation, budgets and practical-v4 stopping rules.'}
_old_diagnostic = base.diagnostic_evidence
_old_verify = base.verify


def readiness_guards(ready):
    base.need(ready['complete'] is True and ready['status'] == 'NOT_READY' and ready['strengthReady'] is False,
              'Complete retained failed readiness required')
    base.need(ready['engineExact'] == 292, 'Exact engine qualification required')
    base.need(ready['parity']['positions'] == 524 and ready['parity']['pass'] is True
              and ready['parity'].get('reused') is True, 'Inherited exact parity required')
    p = ready['process']
    base.need(p['games'] == 16 and p['pass'] is False and p['missing'] == 0
              and all(type(p[k]) is int and p[k] >= 0 for k in ('errors', 'timeouts', 'overruns', 'missing'))
              and p['errors'] == p['timeouts'] == p['overruns'] > 0,
              'Only complete timeout-only process failure is waived')
    base.need({'artifact', 'artifact-off'}.issubset(ready['artifacts']), 'Both artifact identities required')
    base.need(all(type(row['charactersUtf16']) is int and 0 < row['charactersUtf16'] <= 100000
                  for row in ready['artifacts'].values()), 'Source cap exceeded')


def waiver_guards(ready, audit):
    readiness_guards(ready)
    base.need(audit['complete'] is True and audit['status'] == 'PASS' and audit['checks'] > 0
              and audit['promotion'] is False and audit['newBossGames'] == audit['newGameOrPolicyCalls'] == 0,
              'Complete independent reconciliation required')
    base.need(audit['readiness'] == {'status': 'NOT_READY', 'strengthReady': False}
              and audit['strengthReady'] is False, 'Retain original readiness verdict')
    p = audit['process']
    base.need(all(p[k] == ready['process'][k] for k in ('games', 'pass', 'errors', 'timeouts', 'overruns', 'missing')),
              'Process audit/readiness mismatch')
    rows = p['rows']
    base.need(len(rows) == 16 and len({(r['map'], r['seat']) for r in rows}) == 16,
              'Complete distinct audited process rows required')
    errors = timeouts = overruns = statuses = 0
    for row in rows:
        base.need(row['seat'] in (0, 1) and row['seed'] == 7000 + row['seat']
                  and row['responses'] == row['turns'] and row['missing'] == 0,
                  'Incomplete audited process responses')
        base.need(len(row['errors']) == 2, 'Malformed audited errors')
        for seat, error in enumerate(row['errors']):
            if error is not None:
                base.need(seat == row['seat'] and isinstance(error, str)
                          and re.fullmatch(r'exception: java\.lang\.IllegalStateException: TIMEOUT turn \d+ \(deadline 50 ms\)', error),
                          'Only recorded candidate TIMEOUT errors are waived')
                errors += 1
        count = sum(e is not None for e in row['errors'])
        base.need(row['timeouts'] == row['overruns'] == row['badStatuses'] == count,
                  'Audited row has unexplained process failure')
        timeouts += row['timeouts']; overruns += row['overruns']; statuses += row['badStatuses']
    base.need(errors == p['errors'] == p['botErrors'] and timeouts == p['timeouts']
              and overruns == p['overruns'] and statuses == p['badStatuses'], 'Raw timeout counts mismatch')
    base.need(p['first']['count'] == 16 and p['first']['maxMs'] <= 1000
              and p['later']['count'] == sum(r['responses'] - 1 for r in rows)
              and p['later']['above50Ms'] == timeouts, 'Full process response coverage required')


def publication_pins():
    files = [ROOT / 'docs/evidence' / ('v09-ink40-v1' + suffix)
             for suffix in ('.json', '-artifacts.json', '-artifacts.zip')]
    summary, index = base.read(files[0]), base.read(files[1])
    base.need(summary['complete'] is True and summary['diagnosticStatus'] == 'NOT_READY'
              and summary['strength']['played'] is False and summary['promotion'] is False,
              'Prior published failed timing result required')
    base.need(index['complete'] is True and index['summary']['sha256'] == base.sha(files[0])
              and index['archive']['sha256'] == base.sha(files[2]), 'Prior publication linkage changed')
    return [base.pin(p) for p in files]


def diagnostic_evidence():
    frozen, ready, audit = _old_diagnostic()
    waiver_guards(ready, audit)
    auditor = audit['auditor']
    base.need(base.sha(ROOT / auditor['path']) == auditor['sha256'], 'Readiness auditor changed')
    publication_pins()
    return frozen, ready, audit


def prepare():
    base.prepare()
    base.save(base.RUN / 'local-authorization.json', dict(AUTHORIZATION, protocol=base.pin(base.DOC),
               priorPublishedEvidence=publication_pins(), retainedReadiness=base.pin(base.DIAG / 'readiness.json')))


def freeze():
    base.need(not base.BENCH.exists(), 'Freeze must precede games')
    diagnostic, ready, audit = diagnostic_evidence()
    plan, preview = base.read(base.RUN / 'input-plan.json'), base.read(base.RUN / 'pool-preflight.json')
    authorization = base.read(base.RUN / 'local-authorization.json')
    base.need(all(authorization[k] == v for k, v in AUTHORIZATION.items())
              and authorization['protocol'] == plan['protocol'] == base.pin(base.DOC)
              and authorization['priorPublishedEvidence'] == publication_pins(), 'Authorization/protocol drift')
    base.need(preview['passed'] and preview['complete'] and preview['distinctProspectiveMaps'] == 500,
              'Complete500 fresh-map preflight required')
    artifacts = {side: ready['artifacts'][name] for side, name in [('A', 'artifact'), ('B', 'artifact-off')]}
    pa, pb = dict(artifacts['A']['params']), dict(artifacts['B']['params'])
    base.need(pa.pop(base.FLAG) == 'true' and base.FLAG not in pb and pa == pb, 'Recipe differs beyond ink flag')
    base.need(pa['TURN_BUDGET_MS'] == '40' and pa['NOISE'] == '0.1' and pa['USE_BATCHED_REFRESH'] == 'true'
              and pa.get('USE_FAST_CHEAPEST_PATH', 'false') == pa.get('USE_ROLLOUT_MISSED_DEADLINE', 'false') == 'false',
              'Wrong shared policy')
    base.need(preview['candidate']['params'] == artifacts['A']['params']
              and preview['control']['params'] == artifacts['B']['params']
              and [preview[k] for k in base.gate.ARM_BUDGET_FIELDS] == [486, 486, 40, 40], 'Native params/budgets differ')
    files = dict(diagnostic['files'])
    files.update(base.read(base.RUN / 'excluded-maps.json')['sourcePins'])
    additions = [base.DOC, Path(__file__), Path(__file__).with_name('test_v09_ink40_local_strength.py'),
                 Path(__file__).with_name('run_v09_ink40_strength.py'), Path(__file__).with_name('test_v09_ink40_strength.py'),
                 Path(__file__).with_name('V09Ink40StrengthPreflight.java'), Path(__file__).with_name('run_v09_fast_path_strength.py'),
                 base.DIAG / 'measurement-freeze.json', base.DIAG / 'readiness.json',
                 base.DIAG / 'independent-readiness-audit.json', base.DIAG / 'baseline.json', ROOT / auditor_path(audit)]
    additions += [ROOT / row['path'] for row in plan['pools'] + publication_pins()]
    additions += list((ROOT / 'tools').glob('practical_gate*.py')) + list((ROOT / 'tools').glob('boss*.py'))
    additions += [ROOT / 'tools/run_practical_v4.py']
    additions += [p for folder in (base.RUN, base.HARNESS) for p in folder.rglob('*')
                  if p.is_file() and '__pycache__' not in p.parts]
    files.update({base.rel(p): base.sha(p) for p in additions})
    for evidence in (ready, audit):
        files.update({base.rel(ROOT / p): row['sha256'] for p, row in evidence['inputPins'].items()
                      if (ROOT / p).resolve().is_relative_to(ROOT)})
    files.update(base.read(base.DIAG / 'baseline.json')['artifactFiles'])
    runtime = dict(diagnostic['runtimeFiles']); runtime[str(Path(sys.executable))] = base.sha(sys.executable)
    inventory = base.classpath_inventory(base.classpath())
    base.need(inventory and all(p in files for p in inventory), 'Unpinned classpath additions')
    checked = base.read(base.HARNESS / 'driver-tests-exit.json')
    base.need(checked['exitCode'] == 0 and checked['tests'] == TEST_COUNT and checked['noGamesOrBotCalls'] is True,
              'Successful local-waiver synthetic checks required')
    base.need(base.sha(base.HARNESS / 'driver-tests.log') == checked['logSha256'], 'Synthetic log changed')
    for name, digest in base.read(base.HARNESS / 'source-pins.json').items():
        base.need(base.sha(ROOT / name) == digest, 'Driver source changed after synthetic checks')
    base.save(base.RUN / 'freeze.json', {'experiment': base.NAME, 'at': base.now(), 'sourceCommit': plan['sourceCommit'],
        'files': files, 'runtimeFiles': runtime, 'classpath': base.classpath(), 'java': str(base.JAVA),
        'classpathInventory': inventory, 'managedDirectories': diagnostic['managedDirectories'],
        'managedInventory': diagnostic['managedInventory'], 'candidate': base.A, 'control': base.B,
        'artifacts': artifacts, 'baseSeed': base.BASE, 'threads': 2, 'strengthOnly': True, **AUTHORIZATION,
        'diagnosticReadinessReused': base.pin(base.DIAG / 'readiness.json'),
        'independentDiagnosticAudit': base.pin(base.DIAG / 'independent-readiness-audit.json'),
        'userAuthorization': base.pin(base.RUN / 'local-authorization.json'), 'priorPublishedEvidence': publication_pins(),
        'retainedProcessFailure': ready['process'], 'noNewProcessGames': True})
    print('Frozen', len(files), 'files; prior process NOT_READY retained; local-only timeout waiver', flush=True)


def auditor_path(audit):
    return audit['auditor']['path']


def verify():
    frozen = _old_verify()
    base.need(all(frozen[k] == v for k, v in AUTHORIZATION.items()) and frozen['noNewProcessGames'] is True,
              'Local-only authorization changed')
    return frozen


def strength():
    frozen = base.verify()
    base.need(not base.BENCH.exists(), 'Existing strength attempt cannot be replaced')
    base.BENCH.mkdir(parents=True)
    base.save(base.RUN / 'strength-start.json', {'at': base.now(), 'freeze': base.pin(base.RUN / 'freeze.json'), **AUTHORIZATION})
    reports = []
    for index, (name, games, pairs, offset) in enumerate(base.schedule.BLOCKS):
        base.verify()
        report = base.BENCH / (name + '.json')
        command = [frozen['java'], '-cp', frozen['classpath'], 'btk.runner.BenchmarkRunner', base.A, base.B,
                   '', '', '2', '', base.rel(report), 'cg', base.rel(base.schedule.pool_path(base.NAME, name)),
                   base.rel(base.RUN / 'excluded-maps.json')]
        base.logged(name, command)
        parsed = base.gate.read_report(report, games)
        base.inspect(parsed, index, frozen)
        base.verify()
        reports.append(parsed)
        verdict = base.gate.assess(*reports)
        base.save(base.BENCH / (name + '-decision.json'), verdict)
        print(json.dumps({k: v for k, v in verdict.items() if k not in ('reports', 'boss')}, indent=2), flush=True)
        stage = verdict['directStage']
        if stage.startswith('STOP_FUTILITY') or stage in ('PASS_1000', 'NOT_PASSED'):
            base.save(base.RUN / 'strength-summary.json', {'experiment': base.NAME, 'complete': True, 'at': base.now(),
                'localDecision': stage, 'gate': verdict, 'reports': [base.pin(Path(r['path'])) for r in reports],
                'freeze': base.pin(base.RUN / 'freeze.json'), **AUTHORIZATION, 'newBossGames': 0, 'newProcessGames': 0,
                'scope': 'Local inkON/OFF at40 under explicit timeout-gate waiver; original NOT_READY retained. No promotion eligibility.'})
            return
    raise ValueError('Sequence ended without terminal local decision')


# Configure only the private in-memory module; historical files stay byte-identical.
base.readiness_guards = readiness_guards
base.diagnostic_evidence = diagnostic_evidence
base.verify = verify
base.strength = strength


if __name__ == '__main__':
    actions = {'prepare': prepare, 'freeze': freeze, 'verify': verify, 'strength': strength}
    base.need(len(sys.argv) == 2 and sys.argv[1] in actions, 'Mode prepare|freeze|verify|strength required')
    actions[sys.argv[1]]()
