"""Separate joint fit from original Stage-A step 2,000 to total 12,000."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import zipfile

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]



def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    sys.modules[name] = value
    spec.loader.exec_module(value)
    return value


base = module('indrill_joint_base', HERE.parent / 'build64_12k_v1/run_fit.py')
old = base.old
obj = module('indrill_joint_objective', HERE / 'objective.py')
np, q, previous = obj.np, obj.q, base.previous
teacher = previous.teacher
require, read, write_once = base.require, base.read, base.write_once
absolute, digest, pin, check_pin = base.absolute, base.digest, base.pin, base.check_pin
environment, runtime_files = base.environment, base.runtime_files
sampler, schedule_entry, hash_entry = base.sampler, base.schedule_entry, base.hash_entry
aggregate, summarize = previous.aggregate, previous.summarize
OUTPUT = ROOT / 'build/analysis/indrill-joint64-12k-v1'
PROTOCOL = ROOT / 'docs/indrill-joint64-12k-v1.md'
PARENT_ROOT = ROOT / 'build/analysis/indrill-capacity-v1'
PARENT_EVIDENCE = ROOT / 'docs/evidence/indrill-capacity-v1.json'
PARENT_ARCHIVE = ROOT / 'docs/evidence/indrill-capacity-v1-artifacts.json'
BUILD_EVIDENCE = ROOT / 'docs/evidence/indrill-build64-12k-v1.json'
SETTINGS = {'modelSeed': 2026100401, 'samplingSeed': 2026100411, 'startStep': 2000,
            'updates': 12000, 'batch': 16, 'observations': 64, 'metricsAt': [2000, 4000, 6000, 8000, 10000, 12000]}
LEARNING_RATE, CLIP_NORM = .0003, 10.


def evaluate(model, data):
    return {'stage': 'A', **previous.evaluate(model, data, list(range(len(data.rows))))}



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


def metric_paths(folder, step):
    return tuple(Path(folder) / (f'metrics-{step:04d}' + suffix)
                 for suffix in ('.json', '-intent.json', '-receipt.json'))


def validate_metric_payload(value, step, checkpoint_pin, count, expected_rows=None):
    """Validate saved original joint metrics without a learned forward pass."""
    rows = value['rows']
    require(value['stage'] == 'A' and value['updates'] == step and value['checkpoint'] == checkpoint_pin,
            'Metric checkpoint/config differs')
    require(value['observations'] == len(rows) == count
            and value['jointExactCount'] == sum(r['jointExact'] for r in rows)
            and value['summary'] == summarize(rows), 'Metric aggregation differs')
    require(value['precisionRecallConvention'] == 'Both empty: precision=recall=1. Exactly one empty: both=0.',
            'Metric precision/recall convention differs')
    require(expected_rows is None or len(expected_rows) == len(rows), 'Metric source cardinality differs')
    for index, row in enumerate(rows):
        require(all(np.isfinite(row[m]) for m in previous.METRICS), 'Nonfinite retained metric')
        target, predicted = set(row['targetCells']), set(row['predictedCells'])
        overlap, both_empty = len(target & predicted), not target and not predicted
        require(row['targetCells'] == sorted(target) and row['predictedCells'] == sorted(predicted)
                and row['buildExact'] == int(target == predicted) and row['teacherCardinality'] == len(target)
                and row['inkExact'] == int(row['targetInk'] == row['predictedInk'])
                and row['jointExact'] == int(bool(row['buildExact']) and bool(row['inkExact']))
                and row['cellPrecision'] == (1. if both_empty else overlap / len(predicted) if predicted else 0.)
                and row['cellRecall'] == (1. if both_empty else overlap / len(target) if target else 0.)
                and row['jointNll'] == float(np.asarray([row['buildNll'], row['inkNll']], np.float64).sum()),
                'Metric action/loss summary differs')
        require(row['turnBand'] == ('0..19' if row['turn'] < 20 else '20..49' if row['turn'] < 50 else '50+'),
                'Metric turn stratum differs')
        if expected_rows is not None:
            source = expected_rows[index]
            for key in ('observationId', 'mapHash', 'gameId', 'teacherSeat', 'turn', 'inputSha256',
                        'targetCells', 'targetInk', 'hadSkippedActions'):
                require(row[key] == source[key], 'Metric source/label differs')
            ink = row['predictedInk']
            require(type(ink) is int and (ink == -1 or 0 <= ink < len(source['legalInk']) and source['legalInk'][ink]),
                    'Metric ink legality differs')
            require(all(type(c) is int and 0 <= c < source['size'] and source['legalBuild'][c] for c in predicted)
                    and row['predictedPaint'] == sum(int(source['costs'][c]) for c in predicted) <= 3
                    and row['teacherPaint'] == source['acceptedPaint'], 'Metric construction legality/paint differs')
            pattern = '+'.join(str(source['costs'][c]) for c in sorted(target, key=lambda c: (source['costs'][c], c))) or 'EMPTY'
            require(row['costPattern'] == pattern and row['cohortTags'] == source.get('cohortTags', []), 'Metric strata differ')
    if expected_rows is not None:
        require(value['collisionSummary'] == teacher.collision_summary(expected_rows, list(range(count))),
                'Metric collision summary differs')
    return value


def validate_metrics(folder, step, binding, expected_rows=None):
    path, intent_path, receipt_path = metric_paths(folder, step)
    require(all(p.exists() for p in (path, intent_path, receipt_path)), 'Interrupted metrics: no automatic reevaluation')
    check = pin(checkpoint_path(folder, step))
    require(read(intent_path) == {'binding': binding, 'checkpoint': check, 'step': step}, 'Metric intent differs')
    require(read(receipt_path) == {'binding': binding, 'intent': pin(intent_path), 'metrics': pin(path)}, 'Metric receipt differs')
    return validate_metric_payload(read(path), step, check, binding['settings']['observations'], expected_rows)


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
    return {'schema': 'indrill-joint64-12k-checkpoint-v1', 'freeze': pin(freeze_path), 'selectedRows': freeze['selectedRows'],
            'settings': SETTINGS, 'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM,
            'parent': {key: freeze['parent'][key] for key in ('checkpoint', 'receipt', 'binding')}}


def start_proof_value(folder, freeze, binding):
    step = binding['settings']['startStep']; path = checkpoint_path(folder, step)
    metric, metric_intent, metric_receipt = metric_paths(folder, step)
    parent = freeze['parent']
    return {'schema': 'indrill-joint64-12k-start-v1', 'complete': True, 'checksPassed': True, 'binding': binding,
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
            require(np.isfinite(loss), 'Nonfinite joint loss')
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
    result = {'schema': 'indrill-joint64-12k-run-result-v1', 'freeze': pin(freeze_path), 'updates': model.updates,
              'newUpdates': model.updates - SETTINGS['startStep'], 'sampleVisits': model.updates * SETTINGS['batch'],
              'newSampleVisits': (model.updates - SETTINGS['startStep']) * SETTINGS['batch'], 'finalJointExact': final['jointExactCount'],
              'decision': 'JOINT_FIT_ESTABLISHED' if final['jointExactCount'] >= 63 else 'JOINT_FIT_NOT_ESTABLISHED',
              'checkpoint': pin(checkpoint_path(folder, model.updates)), 'finalMetrics': pin(metric_paths(folder, model.updates)[0]),
              'startProof': pin(folder / 'start-proof.json'), 'parentTrajectoryReexecuted': False, 'newGames': 0, 'promotion': False}
    write_once(folder / 'result.json', result)
    print(json.dumps(result))


def check_proof(path):
    proof = old.old.fitone.check_proof(path, 'indrill-joint64-12k-checks-v1')
    require(proof['realRowsRead'] == proof['realCandidateFitRuns'] == 0 and proof['productionSettingsUnchanged'] is True
            and proof['environment'] == environment(), 'Synthetic proof scope/runtime differs')
    require({pin(p)['path'] for p in HERE.glob('*.py')}.issubset(proof['sourcePins']), 'Proof omits active implementation')
    for item in proof['runtimePins'] + proof['priorProofs']:
        check_pin(item)
    require({absolute(p['path']) for p in proof['runtimePins']} == runtime_files(), 'Runtime proof inventory differs')
    return proof


def inspect_parent(verify_audited=True):
    """Read original audited Stage A and its archive; never load the whole corpus."""
    prior_path = PARENT_ROOT / 'training-freeze.json'
    prior, dataset_path = previous.verify_freeze(prior_path)
    evidence = read(PARENT_EVIDENCE)
    require(evidence['experiment'] == 'indrill-capacity-v1' and evidence['complete'] is True
            and evidence['auditStatus'] == 'PASS' and evidence['freeze'] == pin(prior_path), 'Parent audit differs')
    stage = evidence['stageA']
    require(stage['settings'] == previous.SETTINGS['A'] and stage['updates'] == 2000 and stage['checkpoints'] == 2001
            and stage['samplingRngOrderPositionExact'] is True and stage['adamMaxAbsDifference'] == 0.
            and stage['adamBitDifferentTensorsWithinTolerance'] == 0, 'Parent trajectory audit differs')
    check_pin(evidence['checkpointChain']); manifest = read(evidence['checkpointChain']['path'])
    require(manifest['complete'] is True and manifest['auditStatus'] == 'PASS' and manifest['freeze'] == evidence['freeze'],
            'Parent manifest differs')
    chain = manifest['chains']['A']
    require([e['step'] for e in chain] == list(range(2001)), 'Parent chain extent differs')
    audited = manifest['auditedPins']; audited_paths = [absolute(p['path']) for p in audited]
    require(audited and len(audited_paths) == len(set(audited_paths)), 'Parent audited inventory is empty/repeated')
    if verify_audited:
        for item in audited:
            check_pin(item)
    anchor = chain[-1]
    intent, result = read(stage['intent']['path']), read(stage['result']['path'])
    binding = {'schema': 'indrill-capacity-checkpoint-v1', 'stage': 'A', 'freeze': pin(prior_path),
               'settings': previous.SETTINGS['A'], 'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM}
    require(intent['binding'] == binding and set(intent['environment']) == {'python', 'numpy', 'platform'}
            and all(environment()[k] == v for k, v in intent['environment'].items()), 'Parent recipe/runtime differs')
    require(result['updates'] == 2000 and result['finalJointExact'] == 6 and result['decision'] == 'FIT_NOT_ESTABLISHED'
            and result['freeze'] == evidence['freeze'] and result['checkpoint'] == anchor['checkpoint'] == stage['finalCheckpoint'],
            'Parent final state differs')
    metric = stage['metrics'][-1]
    require(metric['step'] == 2000 and result['finalMetrics'] == metric['metrics'], 'Parent final metric binding differs')
    require(read(metric['receipt']['path']) == {'binding': binding, 'metrics': metric['metrics']}, 'Parent metric commit differs')
    required_parent = [anchor['checkpoint'], anchor['receipt'], stage['intent'], stage['result'], metric['metrics'], metric['receipt']]
    require({absolute(p['path']) for p in required_parent}.issubset(audited_paths), 'Parent audit omits starting inputs')
    selection_path = ROOT / 'build/analysis/indrill-build64-v1/freeze.json'
    selection = read(selection_path); selected = selection['selectedRows']; check_pin(selected)
    require(selection['priorFreeze'] == evidence['freeze'] and selection['priorEvidence'] == pin(PARENT_EVIDENCE),
            'Selected row provenance differs')
    with absolute(selected['path']).open(encoding='utf-8') as stream:
        data = teacher.dataset_from_rows(json.loads(line) for line in stream if line.strip())
    choices = [r for r in read(dataset_path / 'teacher-preflight.json')['selection'] if 'A' in r['panelTags']]
    require(len(data.rows) == len(choices) == 64 and len({r['mapHash'] for r in data.rows}) == 16
            and data.panel_indices('A') == list(range(64)), 'Original panel differs')
    for row, choice in zip(data.rows, choices):
        require(all(row[k] == v for k, v in choice.items()), 'Selected row order/provenance differs')
    baseline = validate_metric_payload(read(metric['metrics']['path']), 2000, anchor['checkpoint'], 64, data.rows)
    require(baseline['jointExactCount'] == 6 and sum(r['buildExact'] for r in baseline['rows']) == 15
            and sum(r['inkExact'] for r in baseline['rows']) == 26
            and baseline['summary']['all']['meanOfMapMeans']['jointNll'] == 7.492161077696952, 'Parent baseline differs')
    archive = read(PARENT_ARCHIVE)
    require(archive['complete'] is True and archive['experiment'] == evidence['experiment']
            and archive['finalEvidence'] == pin(PARENT_EVIDENCE), 'Parent archive binding differs')
    archive_pin = {'path': archive['archive'], 'sha256': archive['sha256']}; check_pin(archive_pin)
    inventory = {r['path']: r for r in archive['files']}
    require(len(inventory) == len(archive['files']) and evidence['checkpointChain']['path'] in inventory
            and inventory[evidence['checkpointChain']['path']]['sha256'] == evidence['checkpointChain']['sha256'],
            'Parent archive chain inventory differs')
    if verify_audited:
        with zipfile.ZipFile(absolute(archive_pin['path'])) as saved:
            require(len(saved.namelist()) == len(inventory) and set(saved.namelist()) == set(inventory), 'Parent archive inventory differs')
            for name, item in inventory.items():
                payload = saved.read(name)
                require(len(payload) == item['bytes'] and hashlib.sha256(payload).hexdigest() == item['sha256'],
                        'Parent archived bytes differ: ' + name)
    parent = {'freeze': pin(prior_path), 'evidence': pin(PARENT_EVIDENCE), 'chain': evidence['checkpointChain'],
              'checkpoint': anchor['checkpoint'], 'receipt': anchor['receipt'], 'binding': binding,
              'metrics': metric['metrics'], 'metricsCommit': metric['receipt'], 'runIntent': stage['intent'],
              'runResult': stage['result'], 'archiveIndex': pin(PARENT_ARCHIVE), 'archiveZip': archive_pin,
              'auditedPinsCount': len(audited)}
    comparison_evidence = read(BUILD_EVIDENCE)
    require(comparison_evidence['experiment'] == 'indrill-build64-12k-v1' and comparison_evidence['complete'] is True
            and comparison_evidence['auditStatus'] == 'PASS' and comparison_evidence['decision'] == 'BUILD_FIT_ESTABLISHED',
            'Build-only comparison audit differs')
    build_freeze = read(comparison_evidence['freeze']['path'])
    require(build_freeze['selectedRows'] == selected, 'Build-only comparison panel differs')
    build_metric = comparison_evidence['curve'][-1]
    require(build_metric['step'] == 12000 and build_metric['buildExactCount'] == 64, 'Build-only final comparator differs')
    build_folder = absolute(build_metric['metrics']['path']).parent
    comparison = {'evidence': pin(BUILD_EVIDENCE), 'freeze': comparison_evidence['freeze'],
                  'chain': comparison_evidence['checkpointChain'], 'metrics': build_metric['metrics'],
                  'metricsIntent': build_metric['intent'], 'metricsReceipt': build_metric['receipt'],
                  'runIntent': pin(build_folder / 'intent.json')}
    for item in list(old.referenced_pins(parent)) + list(old.referenced_pins(comparison)):
        check_pin(item)
    build_binding = read(build_folder / 'intent.json')['binding']
    control = base.validate_metrics(build_folder, 12000, build_binding, data.rows)
    require(control['buildExactCount'] == 64 and control['meanBuildNll'] == 0.05434279870100711, 'Build-only saved result differs')
    require(all(SETTINGS[k] == previous.SETTINGS['A'][k] for k in ('modelSeed', 'samplingSeed', 'batch', 'observations')),
            'Continuation recipe differs')
    inputs = {'selectedRows': selected, 'selectionFreeze': pin(selection_path),
              'mapExclusionBinding': evidence['mapExclusionRegistry']}
    return parent, inputs, data, comparison


def prepare(checks_path):
    require(not any((OUTPUT / n).exists() for n in ('freeze.json', 'prepare-intent.json', 'run')), 'Refusing existing preparation/run')
    check_proof(checks_path)
    parent, inputs, data, comparison = inspect_parent(verify_audited=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write_once(OUTPUT / 'prepare-intent.json', {'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'settings': SETTINGS,
               'parentCheckpoint': parent['checkpoint'], 'parentChain': parent['chain'],
               'parentAuditedPinsVerified': parent['auditedPinsCount'], 'newGames': 0, 'gradientsRun': 0})
    paths = set(HERE.glob('*.py')) | runtime_files() | {PROTOCOL, absolute(checks_path), OUTPUT / 'prepare-intent.json'}
    references = list(old.referenced_pins(parent)) + list(old.referenced_pins(inputs)) + list(old.referenced_pins(comparison))
    pins = {pin(p['path'])['path']: pin(p['path']) for p in references}
    for path in paths:
        item = pin(path); pins[item['path']] = item
    freeze = {'schema': 'indrill-joint64-12k-freeze-v1', 'status': 'FROZEN', 'checksPassed': True, 'settings': SETTINGS,
              'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM, 'betas': [.9, .999], 'epsilon': 1e-8,
              'outputDirectory': OUTPUT.relative_to(ROOT).as_posix(), 'environment': environment(), 'parent': parent,
              **inputs, 'buildOnlyComparison': comparison, 'checks': pin(checks_path),
              'pins': [pins[k] for k in sorted(pins)], 'newGames': 0, 'gradientsRun': 0}
    write_once(OUTPUT / 'freeze.json', freeze)
    print(json.dumps({'freeze': pin(OUTPUT / 'freeze.json'), 'observations': len(data.rows), 'parentAuditedPinsVerified': parent['auditedPinsCount']}))


def verify_freeze(path, verify_parent_pins=True):
    freeze = read(path)
    require(freeze['schema'] == 'indrill-joint64-12k-freeze-v1' and freeze['status'] == 'FROZEN' and freeze['checksPassed'] is True
            and freeze['settings'] == SETTINGS and freeze['learningRate'] == LEARNING_RATE and freeze['clipNorm'] == CLIP_NORM
            and freeze['betas'] == [.9, .999] and freeze['epsilon'] == 1e-8, 'Wrong fixed protocol')
    require(absolute(freeze['outputDirectory']) == OUTPUT and freeze['environment'] == environment(), 'Output/runtime differs')
    required = set(HERE.glob('*.py')) | runtime_files() | {PROTOCOL, absolute(freeze['checks']['path']),
               absolute(freeze['selectedRows']['path']), absolute(freeze['mapExclusionBinding']['path']),
               absolute(freeze['selectionFreeze']['path']), OUTPUT / 'prepare-intent.json'}
    required |= {absolute(p['path']) for p in list(old.referenced_pins(freeze['parent'])) + list(old.referenced_pins(freeze['buildOnlyComparison']))}
    observed = [absolute(p['path']) for p in freeze['pins']]
    require(len(observed) == len(set(observed)) and required.issubset(observed), 'Missing/repeated freeze pins')
    for item in freeze['pins']:
        check_pin(item)
    check_pin(freeze['checks']); check_proof(freeze['checks']['path'])
    parent, inputs, data, comparison = inspect_parent(verify_audited=verify_parent_pins)
    require(parent == freeze['parent'] and comparison == freeze['buildOnlyComparison']
            and all(freeze[k] == v for k, v in inputs.items()), 'Parent/comparison/selected input binding differs')
    return freeze, data


def paired_comparison(current, reference, metrics):
    require(len(current['rows']) == len(reference['rows']) == 64, 'Comparison cardinality differs')
    pairs = list(zip(current['rows'], reference['rows']))
    for first, second in pairs:
        require(all(first[k] == second[k] for k in ('observationId', 'inputSha256', 'mapHash', 'targetCells')),
                'Comparison row identity differs')
        if 'targetInk' in second:
            require(first['targetInk'] == second['targetInk'], 'Comparison ink target differs')
    report = {}
    for metric in metrics:
        groups = {name: [first['observationId'] for first, second in pairs
                        if (bool(first[metric]), bool(second[metric])) == flags]
                  for name, flags in (('gained', (True, False)), ('lost', (False, True)),
                                      ('retainedExact', (True, True)), ('bothInexact', (False, False)))}
        candidate_count, old_count = sum(r[metric] for r in current['rows']), sum(r[metric] for r in reference['rows'])
        nll = metric.replace('Exact', 'Nll')
        # Keep the original mean-of-map-means convention for both saved controls.
        first_nll = current['summary']['all']['meanOfMapMeans'][nll]
        second_nll = reference['summary']['all']['meanOfMapMeans'][nll]
        report[metric] = {'oldExact': old_count, 'candidateExact': candidate_count,
                          'netExactDifference': candidate_count - old_count,
                          'status': 'BETTER' if candidate_count > old_count else 'EQUAL' if candidate_count == old_count else 'WORSE',
                          'oldMeanOfMapMeansNll': second_nll, 'candidateMeanOfMapMeansNll': first_nll,
                          'rowGroups': groups, 'groupCounts': {k: len(v) for k, v in groups.items()}}
    return report


def finalize(freeze_path):
    """Verify retained states and metrics; never score models or replay gradients."""
    require(not (OUTPUT / 'result.json').exists(), 'Refusing existing final report')
    freeze, data = verify_freeze(freeze_path, verify_parent_pins=False)
    folder, binding = OUTPUT / 'run', make_binding(freeze_path, freeze)
    result, intent = read(folder / 'result.json'), read(folder / 'intent.json')
    require(intent['binding'] == binding and intent['environment'] == environment()
            and intent['parentAuditedPinsVerified'] == freeze['parent']['auditedPinsCount'], 'Run intent/runtime differs')
    model, rng = initialize(), np.random.default_rng(SETTINGS['samplingSeed'])
    verify_chain(folder, model, rng, binding, SETTINGS)
    require(model.updates == result['updates'] == 12000 and result['newUpdates'] == 10000
            and result['sampleVisits'] == 192000 and result['newSampleVisits'] == 160000
            and result['freeze'] == pin(freeze_path), 'Incomplete fixed budget')
    validate_start(folder, freeze, binding, data.rows)
    for step in SETTINGS['metricsAt']:
        validate_metrics(folder, step, binding, data.rows)
    require(result['checkpoint'] == pin(checkpoint_path(folder, 12000)) and result['finalMetrics'] == pin(metric_paths(folder, 12000)[0])
            and result['startProof'] == pin(folder / 'start-proof.json'), 'Final artifact binding differs')
    final, parent = read(result['finalMetrics']['path']), read(freeze['parent']['metrics']['path'])
    control = read(freeze['buildOnlyComparison']['metrics']['path'])
    require(result['finalJointExact'] == final['jointExactCount'] and result['decision'] ==
            ('JOINT_FIT_ESTABLISHED' if final['jointExactCount'] >= 63 else 'JOINT_FIT_NOT_ESTABLISHED'), 'Final decision differs')
    schedule, exposure, new_exposure = hashlib.sha256(), np.zeros(64, np.int64), np.zeros(64, np.int64)
    sampler_rng, order, position = np.random.default_rng(SETTINGS['samplingSeed']), np.empty(0, np.int64), 0
    for step in range(12001):
        chosen = np.empty(0, np.int64)
        if step:
            chosen, order, position = sampler(sampler_rng, order, position); exposure[chosen] += 1
            if step > 2000:
                new_exposure[chosen] += 1
        hash_entry(schedule, schedule_entry(step, chosen, order, position, sampler_rng))
    require(np.array_equal(exposure, np.full(64, 3000)) and np.array_equal(new_exposure, np.full(64, 2500)), 'Final exposure differs')
    report = {'schema': 'indrill-joint64-12k-final-v1', 'complete': True, 'freeze': pin(freeze_path), 'runResult': pin(folder / 'result.json'),
              'decision': result['decision'], 'updates': 12000, 'newUpdates': 10000, 'sampleVisits': 192000, 'newSampleVisits': 160000,
              'visitsPerObservation': exposure.tolist(), 'newVisitsPerObservation': new_exposure.tolist(),
              'newCheckpointsVerified': 10001, 'inheritedParentCheckpoints': 2001, 'parentChain': freeze['parent']['chain'],
              'scheduleSha256': schedule.hexdigest(), 'startProof': pin(folder / 'start-proof.json'),
              'comparison': {'referenceFinalMetrics': freeze['parent']['metrics'],
                             **paired_comparison(final, parent, ('jointExact', 'buildExact', 'inkExact'))},
              'buildOnlyComparison': {'referenceFinalMetrics': freeze['buildOnlyComparison']['metrics'],
                                      **paired_comparison(final, control, ('buildExact',))},
              'metrics': [pin(metric_paths(folder, step)[0]) for step in SETTINGS['metricsAt']],
              'newLearnedEvaluationsAt': SETTINGS['metricsAt'][1:], 'baselineMetricReused': True,
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
