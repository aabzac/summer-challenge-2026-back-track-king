"""Separate fresh B2048 joint fit; immutable old math, one final holdout evaluation.

Only explicit prepare/run CLI commands perform work. Import never reads a corpus,
restores a learned model, scores one, or starts training. The original capacity
Stage B remains closed; none of its globals or prerequisite verdicts are changed.
"""
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
CAPACITY = HERE.parent / 'capacity_v1'
sys.path.insert(0, str(CAPACITY))
# This import establishes the original single-worker NumPy environment first.
import subset_objective as obj
import teacher_dataset as teacher
import train_capacity as previous

np, q, v2, require = obj.np, obj.train_q, obj.train_q_v2, obj.require
OUTPUT = ROOT / 'build/analysis/indrill-generalization-v1'
PROTOCOL = ROOT / 'docs/indrill-generalization-v1.md'
OLD_FREEZE = ROOT / 'build/analysis/indrill-capacity-v1/training-freeze.json'
PREREQUISITE = ROOT / 'docs/evidence/indrill-joint64-12k-v1.json'
PREREQUISITE_ARCHIVE = ROOT / 'docs/evidence/indrill-joint64-12k-v1-artifacts.json'
SETTINGS = {'modelSeed': 2026100402, 'samplingSeed': 2026100412, 'updates': 12000,
            'batch': 32, 'observations': 2048, 'metricsAt': [0, 4000, 8000, 12000]}
LEARNING_RATE, CLIP_NORM = .0003, 10.


def absolute(path):
    value = Path(path)
    return value.resolve() if value.is_absolute() else (ROOT / value).resolve()


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
    require(set(item) == {'path', 'sha256'} and pin(item['path']) == item, 'Pinned bytes/path differ: ' + str(item.get('path')))


def read(path):
    return json.loads(absolute(path).read_text(encoding='utf-8'))


def write_once(path, value):
    with absolute(path).open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2, allow_nan=False)
        stream.write('\n')


def utc():
    return datetime.now(timezone.utc).isoformat()


def environment():
    return {'python': sys.version, 'numpy': np.__version__, 'platform': platform.platform(),
            'executable': str(Path(sys.executable).resolve()),
            'threadEnvironment': {k: os.environ.get(k) for k in
                                  ('OPENBLAS_NUM_THREADS', 'MKL_NUM_THREADS', 'OMP_NUM_THREADS')}}


def runtime_files():
    import numpy._core._multiarray_umath as backend
    import numpy.linalg._umath_linalg as linalg
    paths = {Path(p).resolve() for p in (sys.executable, np.__file__, backend.__file__, linalg.__file__)}
    libs = Path(np.__file__).resolve().parent.parent / 'numpy.libs'
    if libs.exists():
        paths.update(p.resolve() for p in libs.iterdir() if p.is_file())
    return paths


def source_files():
    # Audit tooling is intentionally separate: it cannot change a frozen learner.
    return {HERE / 'run_experiment.py', HERE / 'synthetic_checks.py',
            HERE.parent / 'train_q.py', HERE.parent / 'train_q_v2.py'} | set(CAPACITY.glob('*.py'))


def referenced_pins(value):
    if isinstance(value, dict):
        if set(value) == {'path', 'sha256'}:
            yield value
        else:
            for child in value.values():
                yield from referenced_pins(child)
    elif isinstance(value, list):
        for child in value:
            yield from referenced_pins(child)


def prerequisite():
    evidence = read(PREREQUISITE)
    require(evidence['experiment'] == 'indrill-joint64-12k-v1' and evidence['complete'] is True
            and evidence['auditStatus'] == 'PASS' and evidence['decision'] == 'JOINT_FIT_ESTABLISHED',
            'Completed passing joint64 audit required')
    require(evidence['settings']['updates'] == 12000 and evidence['observations'] == 64
            and evidence['integrity']['adamStepsBitExact'] is True, 'Prerequisite trajectory audit differs')
    curve = evidence['curve'][-1]
    require(curve['step'] == 12000 and curve['observations'] == 64 and curve['jointExactCount'] >= 63,
            'Prerequisite final joint fit was not established')
    for item in referenced_pins(evidence):
        check_pin(item)
    result_path = absolute(evidence['runnerResult']['path']).parent / 'run/result.json'
    result = read(result_path)
    require(result['decision'] == evidence['decision'] and result['updates'] == 12000
            and result['finalJointExact'] == curve['jointExactCount']
            and result['finalMetrics'] == curve['metrics'] and result['freeze'] == evidence['freeze'],
            'Prerequisite run/audit binding differs')
    for item in referenced_pins(result):
        check_pin(item)
    metric = read(result['finalMetrics']['path'])
    require(metric['checkpoint'] == result['checkpoint'] and metric['jointExactCount'] == result['finalJointExact']
            and metric['observations'] == 64, 'Prerequisite saved metrics differ')
    archive = read(PREREQUISITE_ARCHIVE)
    require(archive['complete'] is True and archive['experiment'] == evidence['experiment']
            and archive['finalEvidence'] == pin(PREREQUISITE) and archive['checkpointChain'] == evidence['checkpointChain'],
            'Prerequisite archive binding differs')
    archive_pin = {'path': archive['archive'], 'sha256': archive['sha256']}
    check_pin(archive_pin)
    return {'evidence': pin(PREREQUISITE), 'archiveIndex': pin(PREREQUISITE_ARCHIVE), 'archive': archive_pin,
            'freeze': evidence['freeze'], 'chain': evidence['checkpointChain'], 'runnerResult': evidence['runnerResult'],
            'runResult': pin(result_path), 'checkpoint': result['checkpoint'], 'finalMetrics': result['finalMetrics'],
            'mapExclusionRegistry': evidence['mapExclusionRegistry'],
            'jointExactCount': result['finalJointExact'], 'weightsInherited': False}


def data_summary(data):
    previous.dataset_precheck(data)
    train, held = data.panel_indices('B'), data.panel_indices('holdout')
    require(len(data.rows) == 3068 and len(train) == 2048 and len(held) == 1020, 'Fixed dataset cardinality differs')
    train_maps = {data.rows[i]['mapHash'] for i in train}
    held_maps = {data.rows[i]['mapHash'] for i in held}
    train_inputs = {data.rows[i]['inputSha256'] for i in train}
    held_inputs = {data.rows[i]['inputSha256'] for i in held}
    require(len(train_maps) == 128 and len(held_maps) == 51 and not train_maps & held_maps
            and not train_inputs & held_inputs, 'Held-out map/input overlap or cardinality differs')
    return {'trainingRows': len(train), 'holdoutRows': len(held), 'trainingMaps': sorted(train_maps),
            'holdoutMaps': sorted(held_maps), 'mapOverlap': 0, 'inputOverlap': 0,
            'trainingObservationIds': [data.rows[i]['observationId'] for i in train],
            'holdoutObservationIds': [data.rows[i]['observationId'] for i in held],
            'trainingCollisionSummary': teacher.collision_summary(data.rows, train)}


def check_proof(path):
    proof = read(path)
    require(proof['schema'] == 'indrill-generalization-checks-v1' and proof['passed'] is True
            and proof['syntheticOnly'] is True and proof['realRowsRead'] == proof['realCandidateFitRuns'] == 0
            and proof['productionSettingsUnchanged'] is True and proof['environment'] == environment(),
            'Passing synthetic proof for this runtime required')
    require(set(proof['sourcePins']) == {pin(p)['path'] for p in source_files()}, 'Synthetic source inventory differs')
    for name, sha in proof['sourcePins'].items():
        check_pin({'path': name, 'sha256': sha})
    require({absolute(p['path']) for p in proof['runtimePins']} == runtime_files(), 'Synthetic runtime inventory differs')
    for item in proof['runtimePins']:
        check_pin(item)
    return proof


def prepare(checks_path):
    require(not any((OUTPUT / n).exists() for n in ('freeze.json', 'prepare-intent.json', 'run')), 'Refusing existing preparation/run')
    check_proof(checks_path)
    original, directory = previous.verify_freeze(OLD_FREEZE)
    prior = prerequisite()
    data = teacher.load_dataset(directory)
    summary = data_summary(data)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write_once(OUTPUT / 'prepare-intent.json', {'createdAtUtc': utc(), 'settings': SETTINGS,
               'originalFreeze': pin(OLD_FREEZE), 'prerequisite': prior, 'gradientsRun': 0, 'learnedPredictions': 0})
    paths = source_files() | runtime_files() | {PROTOCOL, absolute(checks_path), OLD_FREEZE, OUTPUT / 'prepare-intent.json'}
    inputs = list(original['pins']) + list(referenced_pins(prior))
    inputs += read(original['exportFreeze']['path'])['pins']
    inputs += [original['exportFreeze'], original['comparator'], pin(absolute(original['comparator']['path']).with_suffix('.npz'))]
    pins = {pin(p)['path']: pin(p) for p in paths}
    for item in inputs:
        # Historical export pins can contain absolute workspace paths. Normalize
        # the new binding without rewriting any historical proof.
        require(digest(item['path']) == item['sha256'], 'Historical input changed')
        normalized = pin(item['path']); pins[normalized['path']] = normalized
    freeze = {'schema': 'indrill-generalization-freeze-v1', 'status': 'FROZEN', 'checksPassed': True,
              'settings': SETTINGS, 'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM,
              'betas': [.9, .999], 'epsilon': 1e-8, 'outputDirectory': OUTPUT.relative_to(ROOT).as_posix(),
              'datasetDirectory': directory.relative_to(ROOT).as_posix(), 'environment': environment(),
              'originalFreeze': pin(OLD_FREEZE), 'prerequisite': prior, 'datasetSummary': summary,
              'comparator': original['comparator'], 'checks': pin(checks_path),
              'pins': [pins[k] for k in sorted(pins)], 'gradientsRun': 0, 'learnedPredictions': 0,
              'freshInitialization': True, 'oldStageBReopened': False}
    write_once(OUTPUT / 'freeze.json', freeze)
    print(json.dumps({'freeze': pin(OUTPUT / 'freeze.json'), 'trainingRows': 2048, 'holdoutRows': 1020, 'gradientsRun': 0}))


def verify_freeze(path):
    freeze = read(path)
    require(freeze['schema'] == 'indrill-generalization-freeze-v1' and freeze['status'] == 'FROZEN'
            and freeze['checksPassed'] is True and freeze['settings'] == SETTINGS
            and freeze['learningRate'] == LEARNING_RATE and freeze['clipNorm'] == CLIP_NORM
            and freeze['betas'] == [.9, .999] and freeze['epsilon'] == 1e-8
            and freeze['freshInitialization'] is True and freeze['oldStageBReopened'] is False, 'Fixed protocol differs')
    require(absolute(freeze['outputDirectory']) == OUTPUT and freeze['environment'] == environment(), 'Output/runtime differs')
    required = source_files() | runtime_files() | {PROTOCOL, absolute(freeze['checks']['path']), OLD_FREEZE, OUTPUT / 'prepare-intent.json'}
    required |= {absolute(p['path']) for p in referenced_pins(freeze['prerequisite'])}
    required |= {absolute(freeze['datasetDirectory']) / n for n in ('teacher.jsonl', 'teacher-preflight.json', 'dataset-manifest.json')}
    observed = [absolute(p['path']) for p in freeze['pins']]
    require(len(observed) == len(set(observed)) and required.issubset(observed), 'Missing/repeated frozen inputs')
    for item in freeze['pins']:
        check_pin(item)
    check_proof(freeze['checks']['path'])
    original, directory = previous.verify_freeze(OLD_FREEZE)
    require(freeze['originalFreeze'] == pin(OLD_FREEZE) and freeze['comparator'] == original['comparator']
            and directory == absolute(freeze['datasetDirectory']) and prerequisite() == freeze['prerequisite'], 'Historical input binding differs')
    data = teacher.load_dataset(directory)
    require(data_summary(data) == freeze['datasetSummary'], 'Fixed dataset selection differs')
    return freeze, data


def initialize():
    return obj.fresh_model(SETTINGS['modelSeed'])


def make_binding(freeze_path, freeze):
    return {'schema': 'indrill-generalization-checkpoint-v1', 'freeze': pin(freeze_path),
            'settings': freeze['settings'], 'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM,
            'freshInitialization': True}


def sampler(rng, order, position, count=2048, batch_size=32):
    require(count > 0 and batch_size > 0 and count % batch_size == 0, 'Epoch/batch divisibility differs')
    if position == len(order):
        order, position = rng.permutation(count), 0
    chosen = order[position:position + batch_size]
    require(len(chosen) == batch_size, 'Partial sampled batch')
    return chosen, order, position + batch_size


def checkpoint_path(folder, step):
    return Path(folder) / 'checkpoints' / f'step-{step:05d}.npz'


def update_intent_path(folder, step):
    return Path(folder) / f'update-{step:05d}-intent.json'


def checkpoint(path, model, rng, order, position, binding):
    obj.assert_finite_frozen_value(model)
    previous.checkpoint(Path(path), model, rng, order, position, binding)


def restore(path, model, rng, binding, count):
    return previous.restore(Path(path), model, rng, binding, count)


def schedule_entry(step, chosen, order, position, rng):
    return {'step': step, 'sampledIndices': chosen.tolist(), 'samplePosition': int(position),
            'sampleOrderSha256': hashlib.sha256(np.asarray(order, '<i8').tobytes()).hexdigest(),
            'rngState': rng.bit_generator.state}


def hash_entry(hasher, entry):
    hasher.update(json.dumps(entry, sort_keys=True, separators=(',', ':')).encode() + b'\n')


def commit_checkpoint(folder, model, rng, chosen, order, position, binding, update_intent=None, loss=None, losses=None, norm=None):
    step = model.updates
    path = checkpoint_path(folder, step)
    checkpoint(path, model, rng, order, position, binding)
    receipt = {'binding': binding, 'checkpoint': pin(path), 'updateIntent': update_intent,
               'previousReceipt': pin(checkpoint_path(folder, step - 1).with_suffix('.json')) if step else None,
               'schedule': schedule_entry(step, chosen, order, position, rng),
               'loss': loss, 'meanHeadNll': losses, 'unclippedGradientNorm': norm}
    write_once(path.with_suffix('.json'), receipt)
    return receipt


def verify_chain(folder, model, rng, binding, config=None):
    """Restore/check all saved boundaries and sampler, never forward or backprop."""
    config = SETTINGS if config is None else config
    folder = Path(folder)
    require(not (folder / 'failure.json').exists(), 'A failed run cannot resume')
    points = sorted((folder / 'checkpoints').glob('step-*.npz'), key=lambda p: int(p.stem.split('-')[1]))
    steps = [int(p.stem.split('-')[1]) for p in points]
    require(steps and steps == list(range(steps[-1] + 1)) and steps[-1] <= config['updates'], 'Incomplete checkpoint sequence')
    expected_rng = np.random.default_rng(config['samplingSeed'])
    order, position = np.empty(0, np.int64), 0
    expected_files, expected_intents = set(), set()
    fresh = obj.fresh_model(config['modelSeed'])
    for step, path in zip(steps, points):
        require(path == checkpoint_path(folder, step), 'Noncanonical numeric checkpoint name')
        chosen = np.empty(0, np.int64)
        if step:
            chosen, order, position = sampler(expected_rng, order, position, config['observations'], config['batch'])
        receipt_path = path.with_suffix('.json'); receipt = read(receipt_path)
        require(receipt['binding'] == binding and receipt['checkpoint'] == pin(path)
                and receipt['schedule'] == schedule_entry(step, chosen, order, position, expected_rng), 'Checkpoint commit/sampler differs')
        previous_receipt = pin(checkpoint_path(folder, step - 1).with_suffix('.json')) if step else None
        require(receipt['previousReceipt'] == previous_receipt, 'Checkpoint receipt chain differs')
        if step:
            intent_path = update_intent_path(folder, step); intent = read(intent_path)
            require(receipt['updateIntent'] == pin(intent_path)
                    and intent == {'binding': binding, 'step': step,
                        'fromCheckpoint': pin(checkpoint_path(folder, step - 1)), 'previousReceipt': previous_receipt,
                        'sampledIndices': chosen.tolist()}, 'Update intent/source/sample differs')
            require(np.isfinite(receipt['loss']) and np.isfinite(receipt['unclippedGradientNorm'])
                    and len(receipt['meanHeadNll']) == 2 and np.isfinite(receipt['meanHeadNll']).all(), 'Nonfinite update receipt')
            expected_intents.add(intent_path)
        else:
            require(receipt['updateIntent'] is None and receipt['loss'] is None
                    and receipt['meanHeadNll'] is None and receipt['unclippedGradientNorm'] is None, 'Fresh checkpoint has update evidence')
        saved_order, saved_position = restore(path, model, rng, binding, config['observations'])
        require(model.updates == step and np.array_equal(saved_order, order) and saved_position == position
                and rng.bit_generator.state == expected_rng.bit_generator.state, 'Saved optimizer clock/RNG/order differs')
        if step == 0:
            require(all(np.array_equal(getattr(model, group)[name], tensor)
                        for group in ('p', 'm', 'v') for name, tensor in getattr(fresh, group).items()), 'Initial weights/Adam differ from fresh seed')
        expected_files.update((path, receipt_path))
    require(set((folder / 'checkpoints').iterdir()) == expected_files, 'Uncommitted/extra checkpoint files')
    require(set(folder.glob('update-*-intent.json')) == expected_intents, 'Uncommitted/extra update intent; no automatic replay')
    return order, position


def metric_paths(folder, step):
    base = Path(folder) / f'train-metrics-{step:05d}'
    return tuple(Path(str(base) + suffix) for suffix in ('.json', '-intent.json', '-receipt.json'))


def evaluation_once(paths, binding, target, evaluate):
    """An intent without both committed files is ambiguous and never rescored."""
    payload_path, intent_path, receipt_path = paths
    present = [p.exists() for p in paths]
    if any(present):
        require(all(present), 'Uncommitted evaluation; no automatic replay')
    else:
        write_once(intent_path, {'binding': binding, 'target': target})
        payload = evaluate()
        write_once(payload_path, payload)
        write_once(receipt_path, {'binding': binding, 'target': target,
                   'intent': pin(intent_path), 'payload': pin(payload_path)})
    require(read(intent_path) == {'binding': binding, 'target': target}, 'Evaluation intent differs')
    require(read(receipt_path) == {'binding': binding, 'target': target,
                'intent': pin(intent_path), 'payload': pin(payload_path)}, 'Evaluation receipt differs')
    return read(payload_path)


def validate_metric_payload(payload, step, checkpoint_pin, rows):
    require(payload['stage'] == 'B' and payload['updates'] == step and payload['checkpoint'] == checkpoint_pin
            and payload['observations'] == len(rows) == len(payload['rows']), 'Metric boundary/count differs')
    for actual, source in zip(payload['rows'], rows):
        require(all(actual[k] == source[k] for k in ('observationId', 'mapHash', 'inputSha256', 'teacherSeat', 'turn', 'gameId'))
                and actual['targetCells'] == list(source['targetCells']) and actual['targetInk'] == source['targetInk'], 'Metric identity/target differs')
        require(actual['buildExact'] == int(actual['predictedCells'] == actual['targetCells'])
                and actual['inkExact'] == int(actual['predictedInk'] == actual['targetInk'])
                and actual['jointExact'] == actual['buildExact'] * actual['inkExact'], 'Metric exact counts differ')
    require(payload['jointExactCount'] == sum(r['jointExact'] for r in payload['rows'])
            and payload['summary'] == previous.summarize(payload['rows']), 'Metric aggregate differs')
    return payload


def validate_metrics(folder, step, binding, rows):
    paths = metric_paths(folder, step)
    target = {'kind': 'training', 'step': step, 'checkpoint': pin(checkpoint_path(folder, step))}
    def forbidden():
        raise ValueError('Missing saved metric; audit never evaluates')
    require(all(p.exists() for p in paths), 'Missing/uncommitted scheduled metric')
    payload = evaluation_once(paths, binding, target, forbidden)
    return validate_metric_payload(payload, step, target['checkpoint'], rows)


def load_comparator(item):
    check_pin(item)
    model = v2.Model(np.random.default_rng(0))
    with absolute(item['path']).open('rb') as stream:
        tensors = v2.read_weight_tensors(stream)
    require(set(tensors) == set(model.p), 'Comparator tensor inventory differs')
    for name in model.p:
        require(tensors[name].shape == model.p[name].shape and np.isfinite(tensors[name]).all(), 'Comparator tensor differs')
        model.p[name][:] = tensors[name]
    return model


def holdout_paths(folder, name):
    base = Path(folder) / ('holdout-' + name)
    return tuple(Path(str(base) + suffix) for suffix in ('.json', '-intent.json', '-receipt.json'))


def finish_holdout(folder, model, data, binding, comparator_pin, config=None):
    config = SETTINGS if config is None else config
    require(model.updates == config['updates'], 'Holdout requires final fixed update budget')
    final_pin = pin(checkpoint_path(folder, model.updates))
    held = data.panel_indices('holdout')
    rows = [data.rows[i] for i in held]
    target = {'kind': 'holdout-candidate', 'step': model.updates, 'checkpoint': final_pin}
    candidate = evaluation_once(holdout_paths(folder, 'candidate'), binding, target,
        lambda: {'stage': 'B', 'updates': model.updates, 'checkpoint': final_pin, **previous.evaluate(model, data, held)})
    validate_metric_payload(candidate, model.updates, final_pin, rows)
    target = {'kind': 'holdout-frozenQv2', 'comparator': comparator_pin}
    # Loader is inside the thunk: a committed comparison never restores/scores it again.
    comparator = evaluation_once(holdout_paths(folder, 'frozenQv2'), binding, target,
        lambda: {'comparator': comparator_pin, **previous.evaluate(load_comparator(comparator_pin), data, held)})
    require(comparator['comparator'] == comparator_pin and comparator['observations'] == len(held), 'Holdout comparator differs')
    report = {'binding': binding, 'checkpoint': final_pin, 'comparator': comparator_pin,
              'candidate': pin(holdout_paths(folder, 'candidate')[0]),
              'frozenQv2': pin(holdout_paths(folder, 'frozenQv2')[0]), 'comparison': previous.transfer(candidate, comparator)}
    report_path = Path(folder) / 'holdout-final.json'
    if report_path.exists():
        require(read(report_path) == report, 'Saved holdout comparison differs')
    else:
        write_once(report_path, report)
    return report


def run(freeze_path, resume=False):
    freeze, data = verify_freeze(freeze_path)
    folder, config = OUTPUT / 'run', freeze['settings']
    binding = make_binding(freeze_path, freeze)
    if resume:
        require(folder.is_dir() and not (folder / 'result.json').exists(), 'Only an incomplete run can resume')
        require(read(folder / 'intent.json')['binding'] == binding
                and read(folder / 'intent.json')['environment'] == environment(), 'Run intent/runtime differs')
    else:
        folder.mkdir()
        write_once(folder / 'intent.json', {'createdAtUtc': utc(), 'binding': binding, 'environment': environment(),
                   'freshInitialization': True, 'prerequisiteWeightsInherited': False})
        (folder / 'checkpoints').mkdir()
    indices = data.panel_indices('B'); rows = [data.rows[i] for i in indices]
    model, rng = initialize(), np.random.default_rng(config['samplingSeed'])
    order, position = np.empty(0, np.int64), 0
    if resume:
        order, position = verify_chain(folder, model, rng, binding, config)
        for step in config['metricsAt']:
            if step < model.updates:
                validate_metrics(folder, step, binding, rows)
        require(model.updates == config['updates'] or not any(folder.glob('holdout-*')), 'Premature holdout artifacts')
    else:
        commit_checkpoint(folder, model, rng, np.empty(0, np.int64), order, position, binding)
    try:
        while True:
            step = model.updates
            if step in config['metricsAt']:
                point = pin(checkpoint_path(folder, step))
                target = {'kind': 'training', 'step': step, 'checkpoint': point}
                evaluation_once(metric_paths(folder, step), binding, target,
                    lambda: {'stage': 'B', 'updates': step, 'checkpoint': point, **previous.evaluate(model, data, indices)})
                metric = validate_metrics(folder, step, binding, rows)
                print(json.dumps({'step': step, 'trainingJointExact': metric['jointExactCount'], 'trainingRows': len(indices)}), flush=True)
            if step == config['updates']:
                break
            chosen, order, position = sampler(rng, order, position, len(indices), config['batch'])
            intent_path = update_intent_path(folder, step + 1)
            write_once(intent_path, {'binding': binding, 'step': step + 1, 'fromCheckpoint': pin(checkpoint_path(folder, step)),
                       'previousReceipt': pin(checkpoint_path(folder, step).with_suffix('.json')), 'sampledIndices': chosen.tolist()})
            loss, losses, gradients = obj.loss_gradients(model, data.batch([indices[int(i)] for i in chosen]))
            require(np.isfinite(loss) and np.isfinite(losses).all(), 'NUMERICAL_FAILURE')
            norm = model.adam(gradients, LEARNING_RATE, CLIP_NORM)
            require(np.isfinite(norm) and model.updates == step + 1, 'NUMERICAL_FAILURE or optimizer clock differs')
            obj.assert_finite_frozen_value(model)
            commit_checkpoint(folder, model, rng, chosen, order, position, binding, pin(intent_path),
                              float(loss), np.asarray(losses).mean(axis=0).tolist(), float(norm))
            if model.updates % 500 == 0:
                print(json.dumps({'step': model.updates, 'total': config['updates']}), flush=True)
        # No parameter changes after this boundary. Holdout is terminal and has
        # independent write-once intents/receipts for each fixed model.
        for item in freeze['pins']:
            check_pin(item)
        holdout = finish_holdout(folder, model, data, binding, freeze['comparator'], config)
        for item in freeze['pins']:
            check_pin(item)
    except (ValueError, FloatingPointError) as error:
        if not (folder / 'failure.json').exists():
            write_once(folder / 'failure.json', {'freeze': pin(freeze_path), 'updates': model.updates,
                       'error': str(error), 'automaticRetryAllowed': False})
        raise
    final = read(metric_paths(folder, model.updates)[0])
    result = {'schema': 'indrill-generalization-run-result-v1', 'complete': True, 'freeze': pin(freeze_path),
              'updates': model.updates, 'sampleVisits': model.updates * config['batch'],
              'checkpoint': pin(checkpoint_path(folder, model.updates)), 'finalMetrics': pin(metric_paths(folder, model.updates)[0]),
              'finalTrainingJointExact': final['jointExactCount'], 'holdoutFinal': pin(folder / 'holdout-final.json'),
              'decision': holdout['comparison']['decision'], 'freshInitialization': True,
              'newGames': 0, 'promotion': False, 'oldStageBReopened': False}
    write_once(folder / 'result.json', result)
    print(json.dumps(result), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    prep = commands.add_parser('prepare'); prep.add_argument('--checks', required=True)
    train = commands.add_parser('run'); train.add_argument('--freeze', required=True); train.add_argument('--resume', action='store_true')
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare(args.checks)
    else:
        run(args.freeze, args.resume)


if __name__ == '__main__':
    main()
