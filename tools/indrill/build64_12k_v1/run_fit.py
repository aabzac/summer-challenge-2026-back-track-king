"""Separate total-12,000-update fit continuing the audited full step-8,000 state."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    sys.modules[name] = value
    spec.loader.exec_module(value)
    return value


old = module('indrill_12k_prior_runner', HERE.parent / 'build64_long_v1/run_fit.py')
obj = module('indrill_12k_objective', HERE / 'objective.py')
np, q, previous = obj.np, obj.q, old.previous
require, read, write_once = old.require, old.read, old.write_once
absolute, digest, pin, check_pin = old.absolute, old.digest, old.pin, old.check_pin
environment, runtime_files = old.environment, old.runtime_files
sampler, schedule_entry, hash_entry = old.sampler, old.schedule_entry, old.hash_entry
evaluate, aggregate, summarize = old.evaluate, old.aggregate, old.summarize
OUTPUT = ROOT / 'build/analysis/indrill-build64-12k-v1'
PROTOCOL = ROOT / 'docs/indrill-build64-12k-v1.md'
PARENT_EVIDENCE = ROOT / 'docs/evidence/indrill-build64-long-v1.json'
PARENT_ARCHIVE = ROOT / 'docs/evidence/indrill-build64-long-v1-artifacts.json'
SETTINGS = {'modelSeed': 2026100401, 'samplingSeed': 2026100411, 'startStep': 8000,
            'updates': 12000, 'batch': 16, 'observations': 64, 'metricsAt': [8000, 10000, 12000]}
LEARNING_RATE, CLIP_NORM = .0003, 10.


def initialize():
    return obj.network_initializer()


def checkpoint_path(folder, step):
    return Path(folder) / 'checkpoints' / f'step-{step:05d}.npz'


def update_intent_path(folder, step):
    return Path(folder) / f'update-{step:05d}-intent.json'


def checkpoint(path, model, rng, order, position, binding):
    obj.assert_network_invariants(model)
    previous.checkpoint(Path(path), model, rng, order, position, binding)


def restore(path, model, rng, binding, count):
    order, position = previous.restore(Path(path), model, rng, binding, count)
    obj.assert_network_invariants(model)
    return order, position


def restore_parent(model, rng, parent, count, start_step):
    check_pin(parent['checkpoint']); check_pin(parent['receipt'])
    receipt = read(parent['receipt']['path'])
    require(receipt['checkpoint'] == parent['checkpoint'] and receipt['binding'] == parent['binding'], 'Parent receipt/binding differs')
    order, position = restore(absolute(parent['checkpoint']['path']), model, rng, parent['binding'], count)
    require(model.updates == start_step, 'Parent optimizer clock differs')
    return order, position


def compare_start_checkpoint(new_path, parent_path, start_step):
    with np.load(absolute(new_path), allow_pickle=False) as current, np.load(absolute(parent_path), allow_pickle=False) as parent:
        require(set(current.files) == set(parent.files), 'Starting tensor inventory differs')
        for name in current.files:
            first, second = current[name], parent[name]
            if name == 'metadata':
                first, second = json.loads(str(first)), json.loads(str(second))
                require(first['updates'] == second['updates'] == start_step
                        and all(first[k] == second[k] for k in ('rngState', 'samplePosition')), 'Starting sampler/clock differs')
            else:
                require(first.dtype == second.dtype and first.shape == second.shape and first.tobytes() == second.tobytes(),
                        'Starting full-state array differs: ' + name)


def verify_chain(folder, model, rng, binding, config):
    """Verify new boundaries numerically, anchored to the immutable full parent."""
    folder = Path(folder)
    require(not (folder / 'failure.json').exists(), 'A failed run cannot resume')
    points = sorted((folder / 'checkpoints').glob('step-*.npz'), key=lambda p: int(p.stem.split('-')[1]))
    steps = [int(p.stem.split('-')[1]) for p in points]
    start = config['startStep']
    require(steps and steps == list(range(start, steps[-1] + 1)) and steps[-1] <= config['updates'], 'Incomplete checkpoint sequence')
    parent_order, parent_position = restore_parent(model, rng, binding['parent'], config['observations'], start)
    expected_rng = np.random.default_rng(config['samplingSeed'])
    order, position = np.empty(0, np.int64), 0
    for _ in range(start):
        _, order, position = sampler(expected_rng, order, position, config['observations'], config['batch'])
    require(np.array_equal(parent_order, order) and parent_position == position
            and rng.bit_generator.state == expected_rng.bit_generator.state, 'Parent sampler differs from original global sequence')
    expected_files, expected_intents = set(), set()
    previous_receipt = binding['parent']['receipt']
    for step, path in zip(steps, points):
        require(path == checkpoint_path(folder, step), 'Noncanonical numeric checkpoint name')
        chosen = np.empty(0, np.int64)
        if step > start:
            chosen, order, position = sampler(expected_rng, order, position, config['observations'], config['batch'])
        receipt_path = path.with_suffix('.json'); receipt = read(receipt_path)
        expected_files |= {path.name, receipt_path.name}
        require(receipt['binding'] == binding and receipt['checkpoint'] == pin(path)
                and receipt['previousReceipt'] == previous_receipt, 'Checkpoint receipt chain differs')
        if step == start:
            require(receipt == {'binding': binding, 'checkpoint': pin(path), 'previousReceipt': previous_receipt,
                    'parentCheckpoint': binding['parent']['checkpoint']}, 'Starting receipt differs')
            compare_start_checkpoint(path, binding['parent']['checkpoint']['path'], start)
        else:
            intent_path = update_intent_path(folder, step); expected_intents.add(intent_path.name)
            require(receipt['updateIntent'] == pin(intent_path) and receipt['sampledIndices'] == chosen.tolist(), 'Update sampling differs')
            require(read(intent_path) == {'binding': binding, 'fromCheckpoint': pin(checkpoint_path(folder, step - 1)),
                    'previousReceipt': previous_receipt, 'sampledIndices': chosen.tolist()}, 'Update intent differs')
            require(np.isfinite(receipt['loss']) and np.isfinite(receipt['unclippedGradientNorm'])
                    and receipt['unclippedGradientNorm'] >= 0, 'Nonfinite update receipt')
        observed_order, observed_position = restore(path, model, rng, binding, config['observations'])
        with np.load(path, allow_pickle=False) as saved:
            metadata = json.loads(str(saved['metadata']))
            require(set(saved.files) == {'metadata', 'sampleOrder'} |
                    {prefix + name for name in model.p for prefix in ('', 'adam_m_', 'adam_v_')}, 'Checkpoint tensor keys differ')
            require(saved['sampleOrder'].dtype == np.int64 and metadata == dict(binding, updates=step,
                    rngState=expected_rng.bit_generator.state, samplePosition=position), 'Checkpoint metadata differs')
        require(model.updates == step and np.array_equal(observed_order, order) and observed_position == position
                and rng.bit_generator.state == expected_rng.bit_generator.state, 'Checkpoint sampler differs')
        previous_receipt = pin(receipt_path)
    require({p.name for p in (folder / 'checkpoints').iterdir()} == expected_files
            and {p.name for p in folder.glob('update-*-intent.json')} == expected_intents,
            'Uncommitted update/partial checkpoint: no automatic replay')
    return order, position


def check_proof(path):
    proof = old.old.fitone.check_proof(path, 'indrill-build64-12k-checks-v1')
    require(proof['realRowsRead'] == proof['realCandidateFitRuns'] == 0 and proof['productionSettingsUnchanged'] is True
            and proof['environment'] == environment(), 'Synthetic proof scope/runtime differs')
    require({pin(p)['path'] for p in HERE.glob('*.py')}.issubset(proof['sourcePins']), 'Proof omits active implementation')
    for item in proof['runtimePins'] + proof['priorProofs']:
        check_pin(item)
    require({absolute(p['path']) for p in proof['runtimePins']} == runtime_files(), 'Runtime proof inventory differs')
    return proof


def inspect_parent(verify_audited=True):
    prior_path = old.OUTPUT / 'freeze.json'
    prior, data = old.verify_freeze(prior_path)
    evidence = read(PARENT_EVIDENCE)
    require(evidence['experiment'] == 'indrill-build64-long-v1' and evidence['complete'] is True
            and evidence['auditStatus'] == 'PASS' and evidence['freeze'] == pin(prior_path)
            and evidence['settings'] == old.SETTINGS and evidence['integrity']['checkpointsVerified'] == 8001
            and evidence['integrity']['adamStepsBitExact'] is True, 'Parent completed audit differs')
    check_pin(evidence['checkpointChain'])
    manifest = read(evidence['checkpointChain']['path'])
    require(manifest['complete'] is True and manifest['auditStatus'] == 'PASS' and manifest['freeze'] == evidence['freeze'], 'Parent manifest differs')
    require([e['step'] for e in manifest['chain']] == list(range(8001)), 'Parent chain extent differs')
    audited = manifest['auditedPins']
    audited_paths = [absolute(p['path']) for p in audited]
    require(audited and len(audited_paths) == len(set(audited_paths)), 'Parent audited inventory is empty/repeated')
    if verify_audited:
        for item in audited:
            check_pin(item)
    anchor = manifest['chain'][-1]
    run_intent_path, run_result_path = old.OUTPUT / 'run/intent.json', old.OUTPUT / 'run/result.json'
    intent, result = read(run_intent_path), read(run_result_path)
    require(intent['binding'] == old.make_binding(prior_path, prior) and intent['environment'] == environment()
            and prior['environment'] == environment(), 'Parent recipe/runtime differs')
    require(result['updates'] == 8000 and result['sampleVisits'] == 128000 and result['finalBuildExact'] == 59
            and result['decision'] == 'BUILD_FIT_NOT_ESTABLISHED' and result['freeze'] == evidence['freeze']
            and result['checkpoint'] == anchor['checkpoint'], 'Parent final state differs')
    metric = evidence['curve'][-1]
    require(metric['step'] == 8000 and result['finalMetrics'] == metric['metrics'], 'Parent final metric binding differs')
    require({absolute(anchor['checkpoint']['path']), absolute(anchor['receipt']['path']), run_intent_path,
             run_result_path, absolute(metric['metrics']['path']), absolute(metric['receipt']['path']),
             absolute(metric['intent']['path']), absolute(prior['selectedRows']['path'])}.issubset(audited_paths),
            'Parent audited inventory omits required starting inputs')
    baseline = old.validate_metrics(old.OUTPUT / 'run', 8000, intent['binding'], data.rows)
    require(baseline['buildExactCount'] == 59 and baseline['meanBuildNll'] == 0.5822936894583486, 'Parent baseline differs')
    archive = read(PARENT_ARCHIVE)
    require(archive['complete'] is True and archive['experiment'] == evidence['experiment']
            and archive['finalEvidence'] == pin(PARENT_EVIDENCE) and archive['checkpointChain'] == evidence['checkpointChain'], 'Parent archive binding differs')
    archive_pin = {'path': archive['archive'], 'sha256': archive['sha256']}; check_pin(archive_pin)
    parent = {'freeze': pin(prior_path), 'evidence': pin(PARENT_EVIDENCE), 'chain': evidence['checkpointChain'],
              'checkpoint': anchor['checkpoint'], 'receipt': anchor['receipt'], 'binding': intent['binding'],
              'metrics': metric['metrics'], 'metricsIntent': metric['intent'], 'metricsReceipt': metric['receipt'],
              'runIntent': pin(run_intent_path), 'runResult': pin(run_result_path), 'finalResult': evidence['runnerResult'],
              'archiveIndex': pin(PARENT_ARCHIVE), 'archiveZip': archive_pin, 'auditedPinsCount': len(audited)}
    for item in old.referenced_pins(parent):
        check_pin(item)
    require(all(SETTINGS[k] == old.SETTINGS[k] for k in ('modelSeed', 'samplingSeed', 'batch', 'observations'))
            and prior['learningRate'] == LEARNING_RATE and prior['clipNorm'] == CLIP_NORM, 'Continuation recipe differs')
    return parent, prior, data


def prepare(checks_path):
    require(not any((OUTPUT / n).exists() for n in ('freeze.json', 'prepare-intent.json', 'run')), 'Refusing existing preparation/run')
    check_proof(checks_path)
    parent, prior, data = inspect_parent(verify_audited=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write_once(OUTPUT / 'prepare-intent.json', {'createdAtUtc': datetime.now(timezone.utc).isoformat(),
               'settings': SETTINGS, 'parentCheckpoint': parent['checkpoint'], 'parentChain': parent['chain'],
               'parentAuditedPinsVerified': parent['auditedPinsCount'], 'newGames': 0, 'gradientsRun': 0})
    paths = set(HERE.glob('*.py')) | runtime_files() | {PROTOCOL, absolute(checks_path), OUTPUT / 'prepare-intent.json',
            absolute(prior['selectedRows']['path']), absolute(prior['mapExclusionBinding']['path'])}
    pins = {pin(p['path'])['path']: pin(p['path']) for p in old.referenced_pins(parent)}
    for path in paths:
        item = pin(path); pins[item['path']] = item
    freeze = {'schema': 'indrill-build64-12k-freeze-v1', 'status': 'FROZEN', 'checksPassed': True, 'settings': SETTINGS,
              'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM, 'betas': [.9, .999], 'epsilon': 1e-8,
              'outputDirectory': OUTPUT.relative_to(ROOT).as_posix(), 'environment': environment(), 'parent': parent,
              'selectedRows': prior['selectedRows'], 'mapExclusionBinding': prior['mapExclusionBinding'],
              'checks': pin(checks_path), 'pins': [pins[k] for k in sorted(pins)], 'newGames': 0, 'gradientsRun': 0}
    write_once(OUTPUT / 'freeze.json', freeze)
    print(json.dumps({'freeze': pin(OUTPUT / 'freeze.json'), 'observations': len(data.rows), 'parentAuditedPinsVerified': parent['auditedPinsCount']}))


def verify_freeze(path, verify_parent_pins=True):
    freeze = read(path)
    require(freeze['schema'] == 'indrill-build64-12k-freeze-v1' and freeze['status'] == 'FROZEN' and freeze['checksPassed'] is True
            and freeze['settings'] == SETTINGS and freeze['learningRate'] == LEARNING_RATE and freeze['clipNorm'] == CLIP_NORM
            and freeze['betas'] == [.9, .999] and freeze['epsilon'] == 1e-8, 'Wrong fixed protocol')
    require(absolute(freeze['outputDirectory']) == OUTPUT and freeze['environment'] == environment(), 'Output/runtime differs')
    required = set(HERE.glob('*.py')) | runtime_files() | {PROTOCOL, absolute(freeze['checks']['path']),
               absolute(freeze['selectedRows']['path']), absolute(freeze['mapExclusionBinding']['path'])}
    required |= {absolute(p['path']) for p in old.referenced_pins(freeze['parent'])}
    observed = [absolute(p['path']) for p in freeze['pins']]
    require(len(observed) == len(set(observed)) and required.issubset(observed), 'Missing/repeated freeze pins')
    for item in freeze['pins']:
        check_pin(item)
    check_pin(freeze['checks']); check_proof(freeze['checks']['path'])
    parent, prior, data = inspect_parent(verify_audited=verify_parent_pins)
    require(parent == freeze['parent'] and prior['selectedRows'] == freeze['selectedRows']
            and prior['mapExclusionBinding'] == freeze['mapExclusionBinding'], 'Parent/selected input binding differs')
    return freeze, data


def metric_paths(folder, step):
    return tuple(Path(folder) / (f'metrics-{step:04d}' + suffix)
                 for suffix in ('.json', '-intent.json', '-receipt.json'))


def validate_metrics(folder, step, binding, expected_rows=None):
    """Saved-only validation with canonical five-digit checkpoint references."""
    path, intent_path, receipt_path = metric_paths(folder, step)
    require(all(p.exists() for p in (path, intent_path, receipt_path)), 'Interrupted metrics: no automatic reevaluation')
    check = pin(checkpoint_path(folder, step))
    require(read(intent_path) == {'binding': binding, 'checkpoint': check, 'step': step}, 'Metric intent differs')
    require(read(receipt_path) == {'binding': binding, 'intent': pin(intent_path), 'metrics': pin(path)}, 'Metric receipt differs')
    value = read(path); rows = value['rows']
    require(value['checkpoint'] == check and value['updates'] == step and value['evaluationBatchSize'] == 32,
            'Metric checkpoint/config differs')
    require(value['observations'] == len(rows) == binding['settings']['observations']
            and value['buildExactCount'] == sum(r['buildExact'] for r in rows)
            and value['meanBuildNll'] == float(np.mean([r['buildNll'] for r in rows]))
            and value['summary'] == summarize(rows), 'Metric aggregation differs')
    require(expected_rows is None or len(expected_rows) == len(rows), 'Metric source cardinality differs')
    for index, row in enumerate(rows):
        require(all(np.isfinite(row[m]) for m in old.old.METRICS), 'Nonfinite retained metric')
        target, predicted = set(row['targetCells']), set(row['predictedCells'])
        overlap, both_empty = len(target & predicted), not target and not predicted
        require(row['targetCells'] == sorted(target) and row['predictedCells'] == sorted(predicted)
                and row['buildExact'] == int(target == predicted) and row['teacherCardinality'] == len(target)
                and row['cellPrecision'] == (1. if both_empty else overlap / len(predicted) if predicted else 0.)
                and row['cellRecall'] == (1. if both_empty else overlap / len(target) if target else 0.), 'Metric set summary differs')
        if expected_rows is not None:
            source = expected_rows[index]
            for key in ('observationId', 'mapHash', 'gameId', 'teacherSeat', 'turn', 'inputSha256', 'targetCells', 'hadSkippedActions'):
                require(row[key] == source[key], 'Metric source/label differs')
            require(all(0 <= c < source['size'] and source['legalBuild'][c] for c in predicted)
                    and row['predictedPaint'] == sum(int(source['costs'][c]) for c in predicted) <= 3
                    and row['teacherPaint'] == source['acceptedPaint'], 'Metric construction legality/paint differs')
    return value


def inherit_metrics(folder, step, binding, parent_metric_pin, expected_rows=None):
    """Copy only the already audited metric payload; no model or scoring path."""
    require(step == binding['settings']['startStep'], 'Only the starting metric is inherited')
    check_pin(parent_metric_pin)
    parent = read(parent_metric_pin['path'])
    require(parent['updates'] == step, 'Inherited metric clock differs')
    path, intent_path, receipt_path = metric_paths(folder, step)
    if not any(p.exists() for p in (path, intent_path, receipt_path)):
        check = pin(checkpoint_path(folder, step))
        write_once(intent_path, {'binding': binding, 'checkpoint': check, 'step': step})
        write_once(path, dict(parent, checkpoint=check))
        write_once(receipt_path, {'binding': binding, 'intent': pin(intent_path), 'metrics': pin(path)})
    value = validate_metrics(folder, step, binding, expected_rows)
    old.compare_prefix_metrics(value, parent)
    return value


def record_metrics(folder, model, data, binding):
    step = model.updates
    require(step in binding['settings']['metricsAt'] and step > binding['settings']['startStep'],
            'Starting metric must be inherited; new scoring only at declared later steps')
    path, intent_path, receipt_path = metric_paths(folder, step)
    if not any(p.exists() for p in (path, intent_path, receipt_path)):
        check = pin(checkpoint_path(folder, step))
        write_once(intent_path, {'binding': binding, 'checkpoint': check, 'step': step})
        write_once(path, {'updates': step, 'checkpoint': check, **evaluate(model, data)})
        write_once(receipt_path, {'binding': binding, 'intent': pin(intent_path), 'metrics': pin(path)})
    return validate_metrics(folder, step, binding, data.rows)


def make_binding(freeze_path, freeze):
    return {'schema': 'indrill-build64-12k-checkpoint-v1', 'freeze': pin(freeze_path), 'selectedRows': freeze['selectedRows'],
            'settings': SETTINGS, 'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM,
            'parent': {key: freeze['parent'][key] for key in ('checkpoint', 'receipt', 'binding')}}


def start_proof_value(folder, freeze, binding):
    step = binding['settings']['startStep']; path = checkpoint_path(folder, step)
    metric, metric_intent, metric_receipt = metric_paths(folder, step)
    parent = freeze['parent']
    return {'schema': 'indrill-build64-12k-start-v1', 'complete': True, 'checksPassed': True, 'binding': binding,
            'parentFreeze': parent['freeze'], 'parentEvidence': parent['evidence'], 'parentChain': parent['chain'],
            'parentAuditedPinsVerified': parent['auditedPinsCount'], 'parentCheckpoint': parent['checkpoint'],
            'parentReceipt': parent['receipt'], 'newInitialCheckpoint': pin(path), 'newInitialReceipt': pin(path.with_suffix('.json')),
            'parentMetrics': parent['metrics'], 'inheritedMetrics': pin(metric),
            'inheritedMetricIntent': pin(metric_intent), 'inheritedMetricReceipt': pin(metric_receipt),
            'arraysMomentsSamplerAndClockBitExact': True, 'metricPayloadExact': True,
            'parentTrajectoryReexecuted': False, 'learnedPredictions': 0, 'gradients': 0}


def validate_start(folder, freeze, binding, rows):
    path = Path(folder) / 'start-proof.json'
    require(path.exists() and read(path) == start_proof_value(folder, freeze, binding), 'Missing/different starting proof')
    step = binding['settings']['startStep']
    compare_start_checkpoint(checkpoint_path(folder, step), freeze['parent']['checkpoint']['path'], step)
    value = validate_metrics(folder, step, binding, rows)
    old.compare_prefix_metrics(value, read(freeze['parent']['metrics']['path']))


def run(freeze_path, resume=False):
    # Includes all audited parent pins before any new gradient, also on resume.
    freeze, data = verify_freeze(freeze_path, verify_parent_pins=True)
    folder, binding = OUTPUT / 'run', make_binding(freeze_path, freeze)
    model, rng = initialize(), np.random.default_rng(SETTINGS['samplingSeed'])
    if resume:
        require(folder.is_dir() and not (folder / 'result.json').exists() and not (folder / 'failure.json').exists(),
                'Only an incomplete nonfailed run may resume')
        intent = read(folder / 'intent.json')
        require(intent['binding'] == binding and intent['environment'] == environment(), 'Run intent/runtime differs')
        order, position = verify_chain(folder, model, rng, binding, SETTINGS)
        for step in SETTINGS['metricsAt']:
            if step < model.updates:
                validate_metrics(folder, step, binding, data.rows)
        if model.updates > SETTINGS['startStep']:
            validate_start(folder, freeze, binding, data.rows)
    else:
        order, position = restore_parent(model, rng, binding['parent'], SETTINGS['observations'], SETTINGS['startStep'])
        folder.mkdir()
        write_once(folder / 'intent.json', {'binding': binding, 'environment': environment(), 'createdAtUtc': datetime.now(timezone.utc).isoformat(),
                   'parentAuditedPinsVerified': freeze['parent']['auditedPinsCount'], 'parentTrajectoryReexecuted': False})
        (folder / 'checkpoints').mkdir()
    try:
        if not resume:
            path = checkpoint_path(folder, SETTINGS['startStep'])
            checkpoint(path, model, rng, order, position, binding)
            compare_start_checkpoint(path, binding['parent']['checkpoint']['path'], SETTINGS['startStep'])
            write_once(path.with_suffix('.json'), {'binding': binding, 'checkpoint': pin(path),
                       'previousReceipt': binding['parent']['receipt'], 'parentCheckpoint': binding['parent']['checkpoint']})
        if model.updates == SETTINGS['startStep']:
            inherit_metrics(folder, model.updates, binding, freeze['parent']['metrics'], data.rows)
            proof_path = folder / 'start-proof.json'
            if not proof_path.exists():
                write_once(proof_path, start_proof_value(folder, freeze, binding))
            validate_start(folder, freeze, binding, data.rows)
        while True:
            step = model.updates
            if step in SETTINGS['metricsAt'] and step > SETTINGS['startStep']:
                record_metrics(folder, model, data, binding)
            if step == SETTINGS['updates']:
                break
            chosen, order, position = sampler(rng, order, position)
            before, path = checkpoint_path(folder, step), checkpoint_path(folder, step + 1)
            previous_receipt = pin(before.with_suffix('.json'))
            intent_path = update_intent_path(folder, step + 1)
            write_once(intent_path, {'binding': binding, 'fromCheckpoint': pin(before), 'previousReceipt': previous_receipt,
                                    'sampledIndices': chosen.tolist()})
            loss, gradients = obj.network_gradients(model, data.batch(chosen))
            require(np.isfinite(loss), 'Nonfinite build loss')
            norm = model.adam(gradients, LEARNING_RATE, CLIP_NORM)
            require(np.isfinite(norm) and model.updates == step + 1, 'Nonfinite norm/wrong update count')
            checkpoint(path, model, rng, order, position, binding)
            write_once(path.with_suffix('.json'), {'binding': binding, 'checkpoint': pin(path), 'previousReceipt': previous_receipt,
                       'updateIntent': pin(intent_path), 'sampledIndices': chosen.tolist(), 'loss': float(loss), 'unclippedGradientNorm': float(norm)})
    except Exception as error:
        if not (folder / 'failure.json').exists():
            write_once(folder / 'failure.json', {'decision': 'TECHNICAL_FAILURE', 'freeze': pin(freeze_path),
                       'updatesInMemory': int(model.updates), 'error': str(error), 'automaticReplayAllowed': False})
        raise
    final = validate_metrics(folder, SETTINGS['updates'], binding, data.rows)
    result = {'schema': 'indrill-build64-12k-run-result-v1', 'freeze': pin(freeze_path), 'updates': model.updates,
              'newUpdates': model.updates - SETTINGS['startStep'], 'sampleVisits': model.updates * SETTINGS['batch'],
              'newSampleVisits': (model.updates - SETTINGS['startStep']) * SETTINGS['batch'], 'finalBuildExact': final['buildExactCount'],
              'decision': 'BUILD_FIT_ESTABLISHED' if final['buildExactCount'] >= 63 else 'BUILD_FIT_NOT_ESTABLISHED',
              'checkpoint': pin(checkpoint_path(folder, model.updates)), 'finalMetrics': pin(metric_paths(folder, model.updates)[0]),
              'startProof': pin(folder / 'start-proof.json'), 'parentTrajectoryReexecuted': False, 'newGames': 0, 'promotion': False}
    write_once(folder / 'result.json', result)
    print(json.dumps(result))


def finalize(freeze_path):
    require(not (OUTPUT / 'result.json').exists(), 'Refusing existing final report')
    freeze, data = verify_freeze(freeze_path, verify_parent_pins=False)
    folder, binding = OUTPUT / 'run', make_binding(freeze_path, freeze)
    result, intent = read(folder / 'result.json'), read(folder / 'intent.json')
    require(intent['binding'] == binding and intent['environment'] == environment()
            and intent['parentAuditedPinsVerified'] == freeze['parent']['auditedPinsCount'], 'Run intent/runtime differs')
    model, rng = initialize(), np.random.default_rng(SETTINGS['samplingSeed'])
    verify_chain(folder, model, rng, binding, SETTINGS)
    require(model.updates == result['updates'] == 12000 and result['newUpdates'] == 4000
            and result['sampleVisits'] == 192000 and result['newSampleVisits'] == 64000
            and result['freeze'] == pin(freeze_path), 'Incomplete fixed budget')
    validate_start(folder, freeze, binding, data.rows)
    for step in SETTINGS['metricsAt']:
        validate_metrics(folder, step, binding, data.rows)
    require(result['checkpoint'] == pin(checkpoint_path(folder, 12000)) and result['finalMetrics'] == pin(metric_paths(folder, 12000)[0])
            and result['startProof'] == pin(folder / 'start-proof.json'), 'Final artifact binding differs')
    final, parent = read(result['finalMetrics']['path']), read(freeze['parent']['metrics']['path'])
    require(result['finalBuildExact'] == final['buildExactCount'] and result['decision'] ==
            ('BUILD_FIT_ESTABLISHED' if final['buildExactCount'] >= 63 else 'BUILD_FIT_NOT_ESTABLISHED'), 'Final decision differs')
    pairs = list(zip(final['rows'], parent['rows']))
    require(len(pairs) == len(final['rows']) == len(parent['rows']) == 64 and parent['buildExactCount'] == 59, 'Parent comparison differs')
    for current, prior in pairs:
        require(all(current[k] == prior[k] for k in ('observationId', 'inputSha256', 'mapHash', 'targetCells')), 'Comparison row identity differs')
    groups = {name: [current['observationId'] for current, prior in pairs if (bool(current['buildExact']), bool(prior['buildExact'])) == flags]
              for name, flags in (('gained', (True, False)), ('lost', (False, True)), ('retainedExact', (True, True)), ('bothInexact', (False, False)))}
    schedule, exposure, new_exposure = hashlib.sha256(), np.zeros(64, np.int64), np.zeros(64, np.int64)
    sampler_rng, order, position = np.random.default_rng(SETTINGS['samplingSeed']), np.empty(0, np.int64), 0
    for step in range(12001):
        chosen = np.empty(0, np.int64)
        if step:
            chosen, order, position = sampler(sampler_rng, order, position); exposure[chosen] += 1
            if step > 8000:
                new_exposure[chosen] += 1
        hash_entry(schedule, schedule_entry(step, chosen, order, position, sampler_rng))
    require(np.array_equal(exposure, np.full(64, 3000)) and np.array_equal(new_exposure, np.full(64, 1000)), 'Final exposure differs')
    count = final['buildExactCount']
    report = {'schema': 'indrill-build64-12k-final-v1', 'complete': True, 'freeze': pin(freeze_path), 'runResult': pin(folder / 'result.json'),
              'decision': result['decision'], 'updates': 12000, 'newUpdates': 4000, 'sampleVisits': 192000, 'newSampleVisits': 64000,
              'visitsPerObservation': exposure.tolist(), 'newVisitsPerObservation': new_exposure.tolist(),
              'newCheckpointsVerified': 4001, 'inheritedParentCheckpoints': 8001, 'parentChain': freeze['parent']['chain'],
              'scheduleSha256': schedule.hexdigest(), 'startProof': pin(folder / 'start-proof.json'),
              'comparison': {'referenceFinalMetrics': freeze['parent']['metrics'], 'oldBuildExact': 59, 'candidateBuildExact': count,
                  'netExactDifference': count - 59, 'status': 'BETTER' if count > 59 else 'EQUAL' if count == 59 else 'WORSE',
                  'oldMeanBuildNll': parent['meanBuildNll'], 'candidateMeanBuildNll': final['meanBuildNll'],
                  'rowGroups': groups, 'groupCounts': {k: len(v) for k, v in groups.items()}},
              'metrics': [pin(metric_paths(folder, step)[0]) for step in SETTINGS['metricsAt']],
              'newLearnedEvaluationsAt': [10000, 12000], 'baselineMetricReused': True,
              'newLearnedPredictionsDuringFinalization': 0, 'newGradientsDuringFinalization': 0,
              'parentTrajectoryReexecuted': False, 'newGames': 0, 'promotion': False,
              'scope': 'Same development panel and inherited full trajectory; no independent confirmation, transfer or strength claim.'}
    write_once(OUTPUT / 'result.json', report)
    print(json.dumps(report))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    prep = sub.add_parser('prepare'); prep.add_argument('--checks', required=True)
    train = sub.add_parser('run'); train.add_argument('--freeze', required=True); train.add_argument('--resume', action='store_true')
    final = sub.add_parser('finalize'); final.add_argument('--freeze', required=True)
    args = parser.parse_args()
    if args.command == 'prepare': prepare(args.checks)
    elif args.command == 'run': run(args.freeze, args.resume)
    else: finalize(args.freeze)


if __name__ == '__main__':
    main()
