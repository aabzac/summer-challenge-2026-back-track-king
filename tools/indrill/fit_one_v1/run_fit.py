"""Write-once, fixed-budget two-arm diagnostic. No work occurs on import."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import sys

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE))
import objective as obj
# objective -> train_q establishes the frozen one-worker BLAS environment before
# NumPy is imported. The dataset adapter must be imported afterward.
sys.path.insert(0, str(ROOT / 'tools/indrill/capacity_v1'))
import teacher_dataset as teacher
import subset_objective as subset
import train_capacity as previous

np, q = obj.np, teacher.q
OUTPUT = ROOT / 'build/analysis/indrill-fit-one-v1'
OLD = ROOT / 'build/analysis/indrill-capacity-v1'
PROTOCOL = ROOT / 'docs/indrill-fit-one-v1.md'
OLD_EVIDENCE = ROOT / 'docs/evidence/indrill-capacity-v1.json'
SETTINGS = {'updates': 2000, 'batch': 1, 'learningRate': .0003, 'clipNorm': 10.,
            'betas': [.9, .999], 'epsilon': 1e-8, 'networkSeed': 2026100401,
            'metricsAt': [0, 500, 1000, 1500, 2000]}
EXPECTED_ID = '905008481:0:0'
EXPECTED_MAP = 'cffa8f3e2bf34d3b9e279ad5ef8ff9185a9f909a1288c2e7e351578d9295d952'


def require(ok, message):
    if not ok:
        raise ValueError(message)


def absolute(path):
    path = Path(path)
    return path.resolve() if path.is_absolute() else (ROOT / path).resolve()


def digest(path):
    value = hashlib.sha256()
    with absolute(path).open('rb') as stream:
        while chunk := stream.read(1 << 20):
            value.update(chunk)
    return value.hexdigest()


def pin(path):
    path = absolute(path)
    name = path.relative_to(ROOT).as_posix() if path.is_relative_to(ROOT) else str(path)
    return {'path': name, 'sha256': digest(path)}


def check_pin(item):
    require(digest(item['path']) == item['sha256'], 'Pinned bytes changed: ' + str(item['path']))


def read(path):
    return json.loads(absolute(path).read_text(encoding='utf-8'))


def write_once(path, value):
    with absolute(path).open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2, allow_nan=False)
        stream.write('\n')


def environment():
    return {'python': sys.version, 'numpy': np.__version__, 'platform': platform.platform(),
            'executable': str(Path(sys.executable).resolve()),
            'threadEnvironment': {k: os.environ.get(k) for k in
                                  ('OPENBLAS_NUM_THREADS', 'MKL_NUM_THREADS', 'OMP_NUM_THREADS')}}


def runtime_files():
    paths = {Path(sys.executable).resolve(), Path(np.__file__).resolve()}
    import numpy._core._multiarray_umath as backend
    import numpy.linalg._umath_linalg as linalg
    paths |= {Path(backend.__file__).resolve(), Path(linalg.__file__).resolve()}
    libs = Path(np.__file__).resolve().parent.parent / 'numpy.libs'
    if libs.exists():
        paths.update(p.resolve() for p in libs.iterdir() if p.is_file())
    return paths


def prior_bindings():
    """Verify completed evidence and original freezes without loading the dataset."""
    freeze_path = OLD / 'training-freeze.json'
    old, _ = previous.verify_freeze(freeze_path)
    evidence = read(OLD_EVIDENCE)
    require(old['schema'] == 'indrill-capacity-training-freeze-v1' and old['status'] == 'FROZEN'
            and old['checksPassed'] is True, 'Original training freeze is incomplete')
    require(evidence['experiment'] == 'indrill-capacity-v1' and evidence['complete'] is True
            and evidence['auditStatus'] == 'PASS' and evidence['decision'] == 'FIT_NOT_ESTABLISHED',
            'Original completed audit differs')
    require(evidence['freeze'] == pin(freeze_path), 'Old evidence/freeze mismatch')
    pins = list(old['pins']) + [pin(freeze_path), pin(OLD_EVIDENCE), old['comparator']]
    export_freeze = read(old['exportFreeze']['path'])
    require(export_freeze['complete'] is True, 'Old export runtime is incomplete')
    pins += [old['exportFreeze']] + export_freeze['pins']
    for item in evidence['dataset'].values():
        if isinstance(item, dict) and set(item) == {'path', 'sha256'}:
            pins.append(item)
    a = evidence['stageA']
    pins += [a['result'], a['intent'], a['finalCheckpoint']]
    for entry in a['metrics']:
        pins += [entry['metrics'], entry['receipt']]
    result = read(a['result']['path'])
    require(result['freeze'] == evidence['freeze'] and result['updates'] == 2000
            and result['decision'] == 'FIT_NOT_ESTABLISHED' and result['finalJointExact'] == 6,
            'Original final result differs')
    require(result['checkpoint'] == a['finalCheckpoint'], 'Original final checkpoint differs')
    pins += [result['checkpoint'], result['finalMetrics']]
    # The previous audit pins the whole chain; retain that binding, without any
    # previous model forward/backward calls or optimizer restoration.
    for field in ('checkpointChain', 'mapExclusionRegistry'):
        value = evidence[field]
        if isinstance(value, dict) and 'path' in value and 'sha256' in value:
            pins.append({'path': value['path'], 'sha256': value['sha256']})
    unique = {}
    for item in pins:
        check_pin(item)
        normalized = pin(item['path'])
        require(normalized['path'] not in unique or unique[normalized['path']] == normalized,
                'Conflicting old pin')
        unique[normalized['path']] = normalized
    return old, evidence, list(unique.values())


def check_proof(path, schema):
    proof = read(path)
    require(proof['schema'] == schema and proof['passed'] is True
            and proof['syntheticOnly'] is True, 'Synthetic checks must pass')
    require(proof['sourcePins'], 'Synthetic checks have no source binding')
    for name, sha in proof['sourcePins'].items():
        check_pin({'path': name, 'sha256': sha})
    return proof


def prepare(checks_path, runner_checks_path):
    require(not (OUTPUT / 'freeze.json').exists() and not (OUTPUT / 'selected-row.jsonl').exists()
            and not (OUTPUT / 'prepare-intent.json').exists(), 'Refusing existing preparation')
    require(not any((OUTPUT / ('arm-' + arm)).exists() for arm in ('free', 'network')), 'Arm already exists')
    old, evidence, old_pins = prior_bindings()
    objective_proof = check_proof(checks_path, 'indrill-fit-one-checks-v1')
    runner_proof = check_proof(runner_checks_path, 'indrill-fit-one-runner-checks-v1')
    require(pin(HERE / 'objective.py')['path'] in objective_proof['sourcePins']
            and pin(Path(__file__))['path'] in runner_proof['sourcePins'], 'Checks omit active implementation')
    dataset = absolute(old['datasetDirectory'])
    preflight, manifest = read(dataset / 'teacher-preflight.json'), read(dataset / 'dataset-manifest.json')
    require(preflight['complete'] is True and manifest['complete'] is True
            and not preflight['contractFailures'] and not manifest['contractFailures'], 'Old dataset incomplete')
    require(manifest['preflightSha256'] == digest(dataset / 'teacher-preflight.json')
            and manifest['teacherJsonlSha256'] == digest(dataset / 'teacher.jsonl'), 'Old dataset binding differs')
    panel = [r for r in preflight['selection'] if 'A' in r['panelTags']]
    require(len(panel) == 64, 'Old A panel cardinality differs')
    order = [(r['gameId'], r['teacherSeat'], r['turn']) for r in panel]
    require(order == sorted(order), 'Previous manifest A order unexpectedly differs')
    selected = panel[0]  # Actual manifest order defines the choice, never scores.
    require(selected['observationId'] == EXPECTED_ID and selected['mapHash'] == EXPECTED_MAP, 'Fixed first A tuple differs')
    exclusion = read(evidence['mapExclusionRegistry']['path'])
    require(exclusion['hashScheme'] == 'sha256-Protocol.initBlock(state,0)-utf8'
            and exclusion['distinctMaps'] == 293 and selected['mapHash'] in exclusion['gamesPerMap'],
            'Selected map is not covered by the retained exclusion registry')
    raw_line, row = None, None
    with (dataset / 'teacher.jsonl').open('rb') as stream:
        for line in stream:
            candidate = json.loads(line)
            if 'A' in candidate['panelTags']:
                raw_line, row = line, candidate
                break
    require(row is not None and all(row[k] == v for k, v in selected.items()), 'First exported A row differs')
    data = teacher.dataset_from_rows([row])
    require(len(data.rows) == 1 and data.rows[0]['split'] == 'train', 'Wrong selected-row split')
    game = next(g for g in preflight['games'] if g['gameId'] == row['gameId'])
    require(game['mapHash'] == row['mapHash'] and game['replaySha256'] == row['replaySha256'], 'Row/raw replay binding differs')
    check_pin({'path': game['replayPath'], 'sha256': game['replaySha256']})
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write_once(OUTPUT / 'prepare-intent.json', {'createdAtUtc': datetime.now(timezone.utc).isoformat(),
               'selectionRule': 'First A tuple in retained manifest and export order', 'oldFreeze': pin(OLD / 'training-freeze.json'),
               'oldEvidence': pin(OLD_EVIDENCE), 'settings': SETTINGS, 'newGames': 0, 'gradientsRun': 0})
    with (OUTPUT / 'selected-row.jsonl').open('xb') as stream:
        stream.write(raw_line)
    paths = set(HERE.glob('*.py')) | runtime_files() | {PROTOCOL, absolute(checks_path), absolute(runner_checks_path),
            OUTPUT / 'selected-row.jsonl', OUTPUT / 'prepare-intent.json', ROOT / game['replayPath']}
    new_pins = {p['path']: p for p in old_pins}
    for path in paths:
        item = pin(path); new_pins[item['path']] = item
    freeze = {'schema': 'indrill-fit-one-freeze-v1', 'status': 'FROZEN', 'checksPassed': True,
              'outputDirectory': OUTPUT.relative_to(ROOT).as_posix(), 'settings': SETTINGS, 'environment': environment(),
              'selectedRow': pin(OUTPUT / 'selected-row.jsonl'), 'selection': selected,
              'selectedInputSha256': row['inputSha256'], 'selectedMapHash': row['mapHash'],
              'mapExclusionBinding': evidence['mapExclusionRegistry'],
              'priorFreeze': pin(OLD / 'training-freeze.json'), 'priorEvidence': pin(OLD_EVIDENCE),
              'comparator': old['comparator'], 'checks': pin(checks_path), 'runnerChecks': pin(runner_checks_path),
              'pins': [new_pins[k] for k in sorted(new_pins)], 'newGames': 0, 'gradientsRun': 0}
    write_once(OUTPUT / 'freeze.json', freeze)
    print(json.dumps({'freeze': pin(OUTPUT / 'freeze.json'), 'selectedObservation': row['observationId']}))


def verify_freeze(path):
    freeze = read(path)
    require(freeze['schema'] == 'indrill-fit-one-freeze-v1' and freeze['status'] == 'FROZEN'
            and freeze['checksPassed'] is True and freeze['settings'] == SETTINGS, 'Wrong fixed protocol')
    require(absolute(freeze['outputDirectory']) == OUTPUT and freeze['environment'] == environment(), 'Output/runtime differs')
    for item in freeze['pins']:
        check_pin(item)
    required = set(HERE.glob('*.py')) | runtime_files() | {PROTOCOL, absolute(freeze['selectedRow']['path']),
               absolute(freeze['checks']['path']), absolute(freeze['runnerChecks']['path'])}
    observed = [absolute(p['path']) for p in freeze['pins']]
    require(len(observed) == len(set(observed)) and required.issubset(observed), 'Missing/repeated freeze pins')
    check_proof(freeze['checks']['path'], 'indrill-fit-one-checks-v1')
    check_proof(freeze['runnerChecks']['path'], 'indrill-fit-one-runner-checks-v1')
    check_pin(freeze['selectedRow'])
    row = read(freeze['selectedRow']['path'])
    require(row['observationId'] == EXPECTED_ID and row['mapHash'] == EXPECTED_MAP
            and row['inputSha256'] == freeze['selectedInputSha256'], 'Selected row differs')
    data = teacher.dataset_from_rows([row])
    return freeze, data


def initialize(arm, batch):
    return obj.free_initializer(batch) if arm == 'free' else obj.network_initializer()


def invariant(arm, state):
    (obj.assert_free_invariants if arm == 'free' else obj.assert_network_invariants)(state)


def checkpoint(path, arm, state, binding):
    invariant(arm, state)
    payload = {'metadata': np.asarray(json.dumps({'binding': binding, 'updates': int(state.updates),
                                                'sampleVisits': int(state.updates)}))}
    if arm == 'free':
        payload.update(logits=state.logits, adam_m=state.m, adam_v=state.v, legal=state.legal)
    else:
        for name in state.p:
            payload[name], payload['adam_m_' + name], payload['adam_v_' + name] = state.p[name], state.m[name], state.v[name]
    with Path(path).open('xb') as stream:
        np.savez_compressed(stream, **payload)


def restore(path, arm, state, binding, step):
    with np.load(path, allow_pickle=False) as saved:
        metadata = json.loads(str(saved['metadata']))
        require(metadata == {'binding': binding, 'updates': step, 'sampleVisits': step}, 'Checkpoint metadata differs')
        arrays = [('logits', state.logits), ('adam_m', state.m), ('adam_v', state.v)] if arm == 'free' else [
            (prefix + name, group[name]) for name in state.p
            for prefix, group in (('', state.p), ('adam_m_', state.m), ('adam_v_', state.v))]
        require(set(saved.files) == {'metadata'} | {k for k, _ in arrays} | ({'legal'} if arm == 'free' else set()), 'Checkpoint tensor keys differ')
        for key, target in arrays:
            require(saved[key].shape == target.shape and saved[key].dtype == np.float32, 'Checkpoint tensor type/shape differs')
            target[:] = saved[key]
        if arm == 'free':
            require(saved['legal'].dtype == np.bool_ and np.array_equal(saved['legal'], state.legal), 'Free legal mask differs')
        state.updates = step
    invariant(arm, state)


def verify_chain(folder, arm, state, binding, max_updates):
    points = sorted((folder / 'checkpoints').glob('step-*.npz'))
    steps = [int(p.stem.split('-')[1]) for p in points]
    require(steps and steps == list(range(steps[-1] + 1)) and steps[-1] <= max_updates, 'Incomplete checkpoint sequence')
    expected_files = set()
    previous_receipt = None
    for step, path in enumerate(points):
        receipt_path = path.with_suffix('.json'); receipt = read(receipt_path)
        expected_files |= {path.name, receipt_path.name}
        require(receipt['binding'] == binding and receipt['step'] == step and receipt['checkpoint'] == pin(path)
                and receipt['previousReceipt'] == previous_receipt, 'Checkpoint receipt chain differs')
        if step:
            intent_path = path.parent / f'update-{step:04d}-intent.json'
            expected_files.add(intent_path.name)
            intent = read(intent_path)
            require(receipt['updateIntent'] == pin(intent_path) and intent == {'binding': binding, 'step': step,
                    'fromCheckpoint': pin(points[step - 1]), 'previousReceipt': previous_receipt}, 'Update intent chain differs')
        else:
            require(receipt['updateIntent'] is None, 'Initial checkpoint cannot have an update')
        restore(path, arm, state, binding, step)
        previous_receipt = pin(receipt_path)
    require({p.name for p in (folder / 'checkpoints').iterdir()} == expected_files,
            'Uncommitted update/partial checkpoint: no automatic replay')
    return steps[-1]


def metrics(arm, state, batch):
    values = state.logits if arm == 'free' else state.forward(batch)[0][0]
    require(np.isfinite(values).all(), 'Nonfinite logits')
    loss = subset.subset_loss(values, batch.costs[0], batch.legal_build[0], batch.builds[0])[0]
    decoded = q.choose_build(values, batch.costs[0], batch.legal_build[0])
    target, predicted = set(batch.builds[0]), set(decoded)
    overlap, both_empty = len(target & predicted), not target and not predicted
    return {'exactSet': int(target == predicted), 'targetCells': sorted(target), 'predictedCells': list(decoded),
            'cellPrecision': 1. if both_empty else overlap / len(predicted) if predicted else 0.,
            'cellRecall': 1. if both_empty else overlap / len(target) if target else 0.,
            'predictedPaint': sum(int(batch.costs[0, c]) for c in predicted),
            'teacherPaint': sum(int(batch.costs[0, c]) for c in target), 'buildNll': float(loss),
            'teacherSetLogitScore': sum(float(values[c]) for c in sorted(target)),
            'decodedSetLogitScore': sum(float(values[c]) for c in sorted(predicted)),
            'scoreScope': 'Descriptive float64 sum of cell logits; frozen decoder retains its own float32 pattern comparisons.'}


def record_metrics(folder, arm, state, batch, binding):
    step = state.updates; name = f'metrics-{step:04d}'
    path, intent_path, receipt_path = [folder / (name + suffix) for suffix in ('.json', '-intent.json', '-receipt.json')]
    check = pin(folder / 'checkpoints' / f'step-{step:04d}.npz')
    if not any(p.exists() for p in (path, intent_path, receipt_path)):
        write_once(intent_path, {'binding': binding, 'checkpoint': check, 'step': step})
        write_once(path, {'arm': arm, 'updates': step, 'checkpoint': check, **metrics(arm, state, batch)})
        write_once(receipt_path, {'binding': binding, 'intent': pin(intent_path), 'metrics': pin(path)})
    validate_metrics(folder, step, binding)


def validate_metrics(folder, step, binding):
    name = f'metrics-{step:04d}'
    path, intent_path, receipt_path = [folder / (name + suffix) for suffix in ('.json', '-intent.json', '-receipt.json')]
    check = pin(folder / 'checkpoints' / f'step-{step:04d}.npz')
    require(all(p.exists() for p in (path, intent_path, receipt_path)), 'Interrupted metrics: no automatic reevaluation')
    require(read(intent_path) == {'binding': binding, 'checkpoint': check, 'step': step}, 'Metric intent differs')
    require(read(receipt_path) == {'binding': binding, 'intent': pin(intent_path), 'metrics': pin(path)}, 'Metric receipt differs')
    require(read(path)['checkpoint'] == check and read(path)['updates'] == step
            and read(path)['arm'] == binding['arm'], 'Metric checkpoint/arm differs')


def run_arm(arm, freeze_path, resume=False):
    freeze, data = verify_freeze(freeze_path)
    batch = data.batch([0]); state = initialize(arm, batch)
    folder = OUTPUT / ('arm-' + arm)
    other = OUTPUT / ('arm-network' if arm == 'free' else 'arm-free')
    require(not other.exists() or (other / 'result.json').exists() or (other / 'failure.json').exists(),
            'The other arm is incomplete; execute the arms serially')
    binding = {'schema': 'indrill-fit-one-checkpoint-v1', 'arm': arm, 'freeze': pin(freeze_path),
               'selectedRow': freeze['selectedRow'], 'settings': SETTINGS}
    if resume:
        require(folder.is_dir() and not (folder / 'result.json').exists() and not (folder / 'failure.json').exists(),
                'Only an incomplete nonfailed original arm may resume')
        require(read(folder / 'intent.json')['binding'] == binding and read(folder / 'intent.json')['environment'] == environment(), 'Arm intent/runtime differs')
        verify_chain(folder, arm, state, binding, SETTINGS['updates'])
        for step in SETTINGS['metricsAt']:
            if step < state.updates:
                # Earlier metrics must already be complete; never replay them.
                validate_metrics(folder, step, binding)
    else:
        folder.mkdir()
        write_once(folder / 'intent.json', {'binding': binding, 'environment': environment(), 'createdAtUtc': datetime.now(timezone.utc).isoformat()})
        (folder / 'checkpoints').mkdir()
        path = folder / 'checkpoints/step-0000.npz'; checkpoint(path, arm, state, binding)
        write_once(path.with_suffix('.json'), {'binding': binding, 'step': 0, 'checkpoint': pin(path),
                   'previousReceipt': None, 'updateIntent': None})
    try:
        while True:
            step = state.updates
            if step in SETTINGS['metricsAt']:
                record_metrics(folder, arm, state, batch, binding)
            if step == SETTINGS['updates']:
                break
            previous = folder / 'checkpoints' / f'step-{step:04d}.npz'
            next_path = folder / 'checkpoints' / f'step-{step + 1:04d}.npz'
            intent_path = folder / 'checkpoints' / f'update-{step + 1:04d}-intent.json'
            previous_receipt = pin(previous.with_suffix('.json'))
            write_once(intent_path, {'binding': binding, 'step': step + 1, 'fromCheckpoint': pin(previous), 'previousReceipt': previous_receipt})
            if arm == 'free':
                loss, gradients = obj.free_gradients(state.logits, batch)
                norm = obj.free_adam(state, gradients)
            else:
                loss, gradients = obj.network_gradients(state, batch)
                norm = state.adam(gradients, SETTINGS['learningRate'], SETTINGS['clipNorm'])
            require(np.isfinite(loss) and np.isfinite(norm), 'Nonfinite loss/gradient norm')
            require(state.updates == step + 1, 'Optimizer update count differs')
            invariant(arm, state); checkpoint(next_path, arm, state, binding)
            write_once(next_path.with_suffix('.json'), {'binding': binding, 'step': step + 1, 'checkpoint': pin(next_path),
                       'previousReceipt': previous_receipt, 'updateIntent': pin(intent_path),
                       'lossBeforeUpdate': float(loss), 'unclippedGradientNorm': float(norm)})
    except Exception as error:
        if not (folder / 'failure.json').exists():
            write_once(folder / 'failure.json', {'decision': 'TECHNICAL_FAILURE', 'freeze': pin(freeze_path),
                       'updatesInMemory': int(state.updates), 'error': str(error), 'automaticReplayAllowed': False})
        raise
    verify_freeze(freeze_path)
    final_path = folder / f'metrics-{SETTINGS["updates"]:04d}.json'; final = read(final_path)
    result = {'arm': arm, 'freeze': pin(freeze_path), 'updates': state.updates, 'sampleVisits': state.updates,
              'decision': 'FINAL_SET_FIT' if final['exactSet'] else 'FIT_NOT_ESTABLISHED',
              'finalExactSet': final['exactSet'], 'checkpoint': pin(folder / 'checkpoints' / f'step-{state.updates:04d}.npz'),
              'finalMetrics': pin(final_path), 'newGames': 0, 'promotion': False}
    write_once(folder / 'result.json', result)
    print(json.dumps(result))


def finalize(freeze_path):
    require(not (OUTPUT / 'result.json').exists(), 'Refusing existing final report')
    freeze, data = verify_freeze(freeze_path); batch = data.batch([0]); arms = {}
    for arm in ('free', 'network'):
        folder = OUTPUT / ('arm-' + arm); result = read(folder / 'result.json')
        require(result['arm'] == arm and result['freeze'] == pin(freeze_path)
                and result['updates'] == result['sampleVisits'] == SETTINGS['updates'], 'Arm did not complete fixed budget')
        require(not (folder / 'failure.json').exists(), 'Arm has technical failure')
        binding = read(folder / 'intent.json')['binding']; state = initialize(arm, batch)
        expected_binding = {'schema': 'indrill-fit-one-checkpoint-v1', 'arm': arm, 'freeze': pin(freeze_path),
                            'selectedRow': freeze['selectedRow'], 'settings': SETTINGS}
        require(binding == expected_binding and read(folder / 'intent.json')['environment'] == environment(), 'Arm binding/runtime differs')
        require(verify_chain(folder, arm, state, binding, SETTINGS['updates']) == SETTINGS['updates'], 'Incomplete arm chain')
        for step in SETTINGS['metricsAt']:
            validate_metrics(folder, step, binding)
        check_pin(result['checkpoint']); check_pin(result['finalMetrics'])
        require(result['checkpoint'] == pin(folder / 'checkpoints' / f'step-{SETTINGS["updates"]:04d}.npz')
                and result['finalMetrics'] == pin(folder / f'metrics-{SETTINGS["updates"]:04d}.json'), 'Result does not bind final artifacts')
        final = read(result['finalMetrics']['path'])
        require(final['exactSet'] == result['finalExactSet'] and result['decision'] ==
                ('FINAL_SET_FIT' if final['exactSet'] else 'FIT_NOT_ESTABLISHED'), 'Final decision differs')
        arms[arm] = {'result': pin(folder / 'result.json'), 'decision': result['decision'],
                     'metrics': final, 'checkpoints': SETTINGS['updates'] + 1, 'updateIntents': SETTINGS['updates']}
    report = {'schema': 'indrill-fit-one-final-v1', 'complete': True, 'auditStatus': 'PASS', 'freeze': pin(freeze_path),
              'observationId': EXPECTED_ID, 'mapHash': EXPECTED_MAP, 'arms': arms, 'newGames': 0, 'promotion': False,
              'scope': 'One development position, build-only fixed-budget fitting. No generalization, convergence, universal capacity or strength claim.'}
    write_once(OUTPUT / 'result.json', report)
    print(json.dumps({'result': pin(OUTPUT / 'result.json'), 'decisions': {k: v['decision'] for k, v in arms.items()}}))


def main():
    parser = argparse.ArgumentParser(description=__doc__); commands = parser.add_subparsers(dest='command', required=True)
    setup = commands.add_parser('prepare'); setup.add_argument('--checks', required=True); setup.add_argument('--runner-checks', required=True)
    run = commands.add_parser('run'); run.add_argument('--arm', choices=('free', 'network'), required=True); run.add_argument('--resume', action='store_true')
    final = commands.add_parser('finalize')
    for command in (run, final): command.add_argument('--freeze', default=str(OUTPUT / 'freeze.json'))
    args = parser.parse_args()
    if args.command == 'prepare': prepare(args.checks, args.runner_checks)
    elif args.command == 'run': run_arm(args.arm, args.freeze, args.resume)
    else: finalize(args.freeze)


if __name__ == '__main__':
    main()
