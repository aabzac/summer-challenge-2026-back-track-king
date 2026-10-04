"""Fixed matched build-only fit; imports never select rows or train a model."""
from __future__ import annotations

import argparse
from collections import defaultdict
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


# Frozen objective initializes single-worker BLAS before its dataset imports.
fitone = module('indrill_build64_prior_runner', HERE.parent / 'fit_one_v1/run_fit.py')
obj = module('indrill_build64_objective', HERE / 'objective.py')
teacher, previous = fitone.teacher, fitone.previous
np, q = obj.np, obj.q
require, read, write_once = fitone.require, fitone.read, fitone.write_once
absolute, digest, pin, check_pin = fitone.absolute, fitone.digest, fitone.pin, fitone.check_pin
environment, runtime_files = fitone.environment, fitone.runtime_files
OUTPUT = ROOT / 'build/analysis/indrill-build64-v1'
PROTOCOL = ROOT / 'docs/indrill-build64-v1.md'
SETTINGS = {'modelSeed': 2026100401, 'samplingSeed': 2026100411, 'updates': 2000,
            'batch': 16, 'observations': 64, 'metricsAt': [0, 500, 1000, 1500, 2000]}
LEARNING_RATE, CLIP_NORM = .0003, 10.
METRICS = ('buildExact', 'cellPrecision', 'cellRecall', 'predictedPaint', 'teacherPaint', 'buildNll')


def initialize():
    return obj.network_initializer()


def sampler(rng, order, position, count=64, batch_size=16):
    require(count > 0 and count % batch_size == 0, 'Epoch/batch divisibility differs')
    if position == len(order):
        order, position = rng.permutation(count), 0
    chosen = order[position:position + batch_size]
    require(len(chosen) == batch_size, 'Partial sampled batch')
    return chosen, order, position + batch_size


def checkpoint(path, model, rng, order, position, binding):
    obj.assert_network_invariants(model)
    previous.checkpoint(Path(path), model, rng, order, position, binding)


def restore(path, model, rng, binding, count):
    order, position = previous.restore(Path(path), model, rng, binding, count)
    obj.assert_network_invariants(model)
    return order, position


def schedule_entry(step, chosen, order, position, rng):
    return {'step': step, 'sampledIndices': chosen.tolist(), 'sampleOrder': order.tolist(),
            'samplePosition': position, 'rngState': rng.bit_generator.state}


def hash_entry(hasher, entry):
    hasher.update(json.dumps(entry, sort_keys=True, separators=(',', ':')).encode('utf-8') + b'\n')


def verify_chain(folder, model, rng, binding, config):
    """Read all committed boundaries, reject ambiguity, restore only final state."""
    folder = Path(folder)
    require(not (folder / 'failure.json').exists(), 'A failed run cannot resume')
    points = sorted((folder / 'checkpoints').glob('step-*.npz'))
    steps = [int(p.stem.split('-')[1]) for p in points]
    require(steps and steps == list(range(steps[-1] + 1)) and steps[-1] <= config['updates'],
            'Incomplete checkpoint sequence')
    expected_files, expected_intents = set(), set()
    expected_rng = np.random.default_rng(config['samplingSeed'])
    initial = initialize()
    order, position, previous_receipt = np.empty(0, np.int64), 0, None
    for step, path in enumerate(points):
        chosen = np.empty(0, np.int64)
        if step:
            chosen, order, position = sampler(expected_rng, order, position, config['observations'], config['batch'])
        receipt_path = path.with_suffix('.json')
        receipt = read(receipt_path)
        expected_files |= {path.name, receipt_path.name}
        require(receipt['binding'] == binding and receipt['checkpoint'] == pin(path)
                and receipt['previousReceipt'] == previous_receipt, 'Checkpoint receipt chain differs')
        if step:
            intent_path = folder / f'update-{step:04d}-intent.json'
            expected_intents.add(intent_path.name)
            require(receipt['updateIntent'] == pin(intent_path) and receipt['sampledIndices'] == chosen.tolist(),
                    'Update receipt sampling differs')
            require(read(intent_path) == {'binding': binding, 'fromCheckpoint': pin(points[step - 1]),
                    'previousReceipt': previous_receipt, 'sampledIndices': chosen.tolist()}, 'Update intent differs')
            require(np.isfinite(receipt['loss']) and np.isfinite(receipt['unclippedGradientNorm'])
                    and receipt['unclippedGradientNorm'] >= 0, 'Nonfinite update receipt')
        observed_order, observed_position = restore(path, model, rng, binding, config['observations'])
        with np.load(path, allow_pickle=False) as saved:
            metadata = json.loads(str(saved['metadata']))
            require(set(saved.files) == {'metadata', 'sampleOrder'} |
                    {prefix + name for name in model.p for prefix in ('', 'adam_m_', 'adam_v_')},
                    'Checkpoint tensor keys differ')
            require(saved['sampleOrder'].dtype == np.int64 and metadata == dict(binding, updates=step,
                    rngState=expected_rng.bit_generator.state, samplePosition=position), 'Checkpoint metadata differs')
            if step == 0:
                for name in initial.p:
                    for prefix, group in (('', initial.p), ('adam_m_', initial.m), ('adam_v_', initial.v)):
                        require(saved[prefix + name].tobytes() == group[name].tobytes(), 'Initial checkpoint differs')
        require(model.updates == step and observed_position == position and np.array_equal(observed_order, order)
                and rng.bit_generator.state == expected_rng.bit_generator.state, 'Checkpoint sampler differs')
        previous_receipt = pin(receipt_path)
    require({p.name for p in (folder / 'checkpoints').iterdir()} == expected_files
            and {p.name for p in folder.glob('update-*-intent.json')} == expected_intents,
            'Uncommitted update/partial checkpoint: no automatic replay')
    return order, position


def check_proof(path):
    proof = fitone.check_proof(path, 'indrill-build64-checks-v1')
    require(proof['realRowsRead'] == proof['realCandidateFitRuns'] == 0
            and proof['productionSettingsUnchanged'] is True and proof['environment'] == environment(),
            'Synthetic proof scope/runtime differs')
    required = {pin(p)['path'] for p in HERE.glob('*.py')}
    require(required.issubset(proof['sourcePins']), 'Synthetic proof omits active implementation')
    for item in proof['runtimePins'] + proof['priorProofs']:
        check_pin(item)
    require({absolute(p['path']) for p in proof['runtimePins']} == runtime_files(), 'Runtime proof inventory differs')
    return proof


def old_sampling_audit(evidence):
    """Verify every old sampler boundary and initial tensors; never score old model."""
    chain_pin = evidence['checkpointChain']; check_pin(chain_pin)
    chain = read(chain_pin['path'])
    require(chain['complete'] is True and chain['auditStatus'] == 'PASS', 'Old chain is incomplete')
    entries = chain['chains']['A']
    require([r['step'] for r in entries] == list(range(SETTINGS['updates'] + 1)), 'Old chain budget differs')
    intent = read(evidence['stageA']['intent']['path'])
    binding = intent['binding']
    require(binding['settings'] == SETTINGS and binding['learningRate'] == LEARNING_RATE
            and binding['clipNorm'] == CLIP_NORM and binding['freeze'] == evidence['freeze'], 'Old recipe differs')
    rng, model = np.random.default_rng(SETTINGS['samplingSeed']), initialize()
    order, position = np.empty(0, np.int64), 0
    exposure, schedule = np.zeros(64, np.int64), hashlib.sha256()
    for step, entry in enumerate(entries):
        for key in ('checkpoint', 'receipt', 'updateIntent'):
            if entry[key] is not None:
                check_pin(entry[key])
        chosen = np.empty(0, np.int64)
        if step:
            chosen, order, position = sampler(rng, order, position)
            exposure[chosen] += 1
            require(read(entry['updateIntent']['path']) == {'binding': binding,
                    'fromCheckpoint': entries[step - 1]['checkpoint']}, 'Old update intent differs')
        receipt = read(entry['receipt']['path'])
        require(receipt['binding'] == binding and receipt['checkpoint'] == entry['checkpoint'], 'Old receipt differs')
        with np.load(absolute(entry['checkpoint']['path']), allow_pickle=False) as saved:
            metadata = json.loads(str(saved['metadata']))
            require(metadata == dict(binding, updates=step, rngState=rng.bit_generator.state, samplePosition=position)
                    and saved['sampleOrder'].dtype == np.int64 and np.array_equal(saved['sampleOrder'], order),
                    'Original sample sequence differs')
            if step == 0:
                for name in model.p:
                    for prefix, group in (('', model.p), ('adam_m_', model.m), ('adam_v_', model.v)):
                        value = saved[prefix + name]
                        require(value.dtype == np.float32 and value.shape == group[name].shape
                                and value.tobytes() == group[name].tobytes(), 'Initial tensors differ from old A')
        hash_entry(schedule, schedule_entry(step, chosen, order, position, rng))
    require(np.array_equal(exposure, np.full(64, 500)), 'Original exposure differs')
    return {'schema': 'indrill-build64-old-sampling-v1', 'complete': True, 'checksPassed': True,
            'oldChain': chain_pin, 'oldIntent': evidence['stageA']['intent'], 'initialCheckpoint': entries[0]['checkpoint'],
            'settings': SETTINGS, 'checkpointsVerified': len(entries), 'sampleVisits': int(exposure.sum()),
            'visitsPerObservation': exposure.tolist(), 'scheduleSha256': schedule.hexdigest(),
            'scheduleEncoding': 'UTF-8 sorted compact JSON schedule_entry plus LF, steps 0..2000',
            'initialParameterAndMomentArraysBitIdentical': True, 'learnedPredictions': 0, 'gradients': 0}


def prepare(checks_path):
    require(not any((OUTPUT / n).exists() for n in ('freeze.json', 'prepare-intent.json', 'selected-rows.jsonl', 'run')),
            'Refusing existing preparation/run')
    check_proof(checks_path)
    old, evidence, old_pins = fitone.prior_bindings()
    one_freeze_path = fitone.OUTPUT / 'freeze.json'
    one_freeze, _ = fitone.verify_freeze(one_freeze_path)
    one_evidence_path = ROOT / 'docs/evidence/indrill-fit-one-v1.json'
    one_evidence = read(one_evidence_path)
    require(one_evidence['complete'] is True and one_evidence['auditStatus'] == 'PASS'
            and one_evidence['freeze'] == pin(one_freeze_path), 'Fit-one completed evidence differs')
    extra_pins = one_freeze['pins'] + [pin(one_freeze_path), pin(one_evidence_path), one_evidence['runnerResult'],
                                      one_evidence['checkpointChain']]
    for item in extra_pins:
        check_pin(item)
    require(SETTINGS == previous.SETTINGS['A'], 'Original A settings changed')
    dataset = absolute(old['datasetDirectory'])
    preflight, manifest = read(dataset / 'teacher-preflight.json'), read(dataset / 'dataset-manifest.json')
    require(preflight['complete'] is True and manifest['complete'] is True and not preflight['contractFailures']
            and not manifest['contractFailures'] and manifest['preflightSha256'] == digest(dataset / 'teacher-preflight.json')
            and manifest['teacherJsonlSha256'] == digest(dataset / 'teacher.jsonl'), 'Dataset binding differs')
    selected = [r for r in preflight['selection'] if 'A' in r['panelTags']]
    require(len(selected) == 64 and len({r['mapHash'] for r in selected}) == 16, 'Original A panel differs')
    raw_lines, rows = [], []
    with (dataset / 'teacher.jsonl').open('rb') as stream:
        for line in stream:
            row = json.loads(line)
            if 'A' in row['panelTags']:
                require(len(rows) < 64 and all(row[k] == v for k, v in selected[len(rows)].items()),
                        'Exported A panel order/identity differs')
                raw_lines.append(line); rows.append(row)
    require(len(rows) == 64, 'Missing original A rows')
    data = teacher.dataset_from_rows(rows)
    previous.certificate_precheck(data)
    require(data.panel_indices('A') == list(range(64)) and all(r['split'] == 'train' for r in rows), 'Selected panel differs')
    old_final_pin = evidence['stageA']['metrics'][-1]['metrics']
    old_final = read(old_final_pin['path'])
    require(old_final['updates'] == 2000 and len(old_final['rows']) == 64
            and sum(r['buildExact'] for r in old_final['rows']) == 15, 'Old final comparison differs')
    for source, retained in zip(rows, old_final['rows']):
        for key in ('observationId', 'mapHash', 'gameId', 'teacherSeat', 'turn', 'inputSha256', 'targetCells', 'targetInk', 'hadSkippedActions'):
            require(source[key] == retained[key], 'Old saved metric identity/label differs: ' + key)
    exclusion = read(evidence['mapExclusionRegistry']['path'])
    require(exclusion['hashScheme'] == 'sha256-Protocol.initBlock(state,0)-utf8' and exclusion['distinctMaps'] == 293
            and all(r['mapHash'] in exclusion['gamesPerMap'] for r in rows), 'Map exclusion provenance differs')
    games = {g['gameId']: g for g in preflight['games']}
    replay_pins = {}
    for row in rows:
        game = games[row['gameId']]
        require(game['mapHash'] == row['mapHash'] and game['replaySha256'] == row['replaySha256'], 'Raw replay identity differs')
        replay_pins[game['replayPath']] = {'path': game['replayPath'], 'sha256': game['replaySha256']}
    for item in replay_pins.values():
        check_pin(item)
    audit = old_sampling_audit(evidence)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write_once(OUTPUT / 'prepare-intent.json', {'createdAtUtc': datetime.now(timezone.utc).isoformat(),
               'settings': SETTINGS, 'oldFreeze': evidence['freeze'], 'oldEvidence': pin(fitone.OLD_EVIDENCE),
               'selectionRule': 'Every original A row in original export order', 'newGames': 0, 'gradientsRun': 0})
    with (OUTPUT / 'selected-rows.jsonl').open('xb') as stream:
        for line in raw_lines:
            stream.write(line)
    write_once(OUTPUT / 'old-sampling-audit.json', audit)
    paths = set(HERE.glob('*.py')) | runtime_files() | {PROTOCOL, absolute(checks_path), OUTPUT / 'selected-rows.jsonl',
            OUTPUT / 'prepare-intent.json', OUTPUT / 'old-sampling-audit.json'}
    pins = {pin(p['path'])['path']: pin(p['path']) for p in old_pins + extra_pins + list(replay_pins.values())}
    for path in paths:
        item = pin(path); pins[item['path']] = item
    freeze = {'schema': 'indrill-build64-freeze-v1', 'status': 'FROZEN', 'checksPassed': True, 'settings': SETTINGS,
              'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM, 'betas': [.9, .999], 'epsilon': 1e-8,
              'outputDirectory': OUTPUT.relative_to(ROOT).as_posix(), 'environment': environment(),
              'selectedRows': pin(OUTPUT / 'selected-rows.jsonl'), 'selection': selected,
              'oldSamplingAudit': pin(OUTPUT / 'old-sampling-audit.json'), 'oldFinalMetrics': old_final_pin,
              'priorFreeze': evidence['freeze'], 'priorEvidence': pin(fitone.OLD_EVIDENCE),
              'fitOneFreeze': pin(one_freeze_path), 'fitOneEvidence': pin(one_evidence_path),
              'mapExclusionBinding': evidence['mapExclusionRegistry'], 'comparator': old['comparator'],
              'checks': pin(checks_path), 'pins': [pins[k] for k in sorted(pins)], 'newGames': 0, 'gradientsRun': 0}
    write_once(OUTPUT / 'freeze.json', freeze)
    print(json.dumps({'freeze': pin(OUTPUT / 'freeze.json'), 'observations': len(data.rows), 'oldSamplingAudit': freeze['oldSamplingAudit']}))


def verify_freeze(path):
    freeze = read(path)
    require(freeze['schema'] == 'indrill-build64-freeze-v1' and freeze['status'] == 'FROZEN'
            and freeze['checksPassed'] is True and freeze['settings'] == SETTINGS and SETTINGS == previous.SETTINGS['A']
            and freeze['learningRate'] == LEARNING_RATE and freeze['clipNorm'] == CLIP_NORM
            and freeze['betas'] == [.9, .999] and freeze['epsilon'] == 1e-8, 'Wrong fixed protocol')
    require(absolute(freeze['outputDirectory']) == OUTPUT and freeze['environment'] == environment(), 'Output/runtime differs')
    observed = [absolute(p['path']) for p in freeze['pins']]
    required = set(HERE.glob('*.py')) | runtime_files() | {PROTOCOL, absolute(freeze['checks']['path']),
               absolute(freeze['selectedRows']['path']), absolute(freeze['oldSamplingAudit']['path'])}
    require(len(observed) == len(set(observed)) and required.issubset(observed), 'Missing/repeated freeze pins')
    for item in freeze['pins']:
        check_pin(item)
    for key in ('selectedRows', 'oldSamplingAudit', 'oldFinalMetrics', 'priorFreeze', 'priorEvidence',
                'fitOneFreeze', 'fitOneEvidence', 'mapExclusionBinding', 'checks', 'comparator'):
        check_pin(freeze[key])
        require(absolute(freeze[key]['path']) in observed, 'Referenced freeze input is not in inventory')
    check_proof(freeze['checks']['path'])
    with absolute(freeze['selectedRows']['path']).open('rb') as stream:
        rows = [json.loads(line) for line in stream]
    require(len(rows) == 64 and len(freeze['selection']) == 64 and all(all(row[k] == v for k, v in selected.items())
            for row, selected in zip(rows, freeze['selection'])), 'Selected rows differ')
    data = teacher.dataset_from_rows(rows)
    previous.certificate_precheck(data)
    require(data.panel_indices('A') == list(range(64)) and len({r['mapHash'] for r in rows}) == 16,
            'Selected panel differs')
    return freeze, data


def aggregate(rows):
    maps = defaultdict(list)
    for row in rows:
        maps[row['mapHash']].append(row)
    means = {key: {m: float(np.mean([r[m] for r in group])) for m in METRICS} for key, group in sorted(maps.items())}
    return {'observations': len(rows), 'maps': len(means), 'mapMeans': means,
            'meanOfMapMeans': {m: float(np.mean([v[m] for v in means.values()])) if means else None for m in METRICS}}


def summarize(rows):
    result = {'all': aggregate(rows)}
    for field in ('teacherCardinality', 'costPattern', 'turnBand', 'teacherSeat', 'hadSkippedActions'):
        result[field] = {key: aggregate([r for r in rows if str(r[field]) == key]) for key in sorted({str(r[field]) for r in rows})}
    return result


def evaluate(model, data):
    rows = []
    for offset in range(0, len(data.rows), 32):
        ids = list(range(offset, min(offset + 32, len(data.rows))))
        batch = data.batch(ids)
        build = model.forward(batch)[0]
        require(np.isfinite(build).all(), 'Nonfinite build logits')
        for j, index in enumerate(ids):
            source = data.rows[index]
            target = set(batch.builds[j])
            predicted = set(q.choose_build(build[j], batch.costs[j], batch.legal_build[j]))
            loss = obj.subset_loss(build[j], batch.costs[j], batch.legal_build[j], batch.builds[j])[0]
            require(np.isfinite(loss), 'Nonfinite build NLL')
            overlap, both_empty = len(target & predicted), not target and not predicted
            turn = source['turn']
            rows.append({'observationId': source['observationId'], 'mapHash': source['mapHash'],
                'gameId': source['gameId'], 'teacherSeat': source['teacherSeat'], 'turn': turn,
                'inputSha256': source['inputSha256'], 'hadSkippedActions': source['hadSkippedActions'],
                'teacherCardinality': len(target), 'costPattern': '+'.join(str(batch.costs[j, c])
                    for c in sorted(target, key=lambda c: (batch.costs[j, c], c))) or 'EMPTY',
                'turnBand': '0..19' if turn < 20 else '20..49' if turn < 50 else '50+',
                'targetCells': sorted(target), 'predictedCells': sorted(predicted), 'buildExact': int(predicted == target),
                'cellPrecision': 1. if both_empty else overlap / len(predicted) if predicted else 0.,
                'cellRecall': 1. if both_empty else overlap / len(target) if target else 0.,
                'predictedPaint': sum(int(batch.costs[j, c]) for c in predicted),
                'teacherPaint': sum(int(batch.costs[j, c]) for c in target), 'buildNll': float(loss)})
    return {'observations': len(rows), 'buildExactCount': sum(r['buildExact'] for r in rows),
            'meanBuildNll': float(np.mean([r['buildNll'] for r in rows])), 'summary': summarize(rows), 'rows': rows,
            'evaluationBatchSize': 32, 'precisionRecallConvention': 'Both empty: precision=recall=1. Exactly one empty: both=0.'}


def validate_metrics(folder, step, binding, expected_rows=None):
    name = f'metrics-{step:04d}'
    path, intent_path, receipt_path = [Path(folder) / (name + suffix) for suffix in ('.json', '-intent.json', '-receipt.json')]
    require(all(p.exists() for p in (path, intent_path, receipt_path)), 'Interrupted metrics: no automatic reevaluation')
    check = pin(Path(folder) / 'checkpoints' / f'step-{step:04d}.npz')
    require(read(intent_path) == {'binding': binding, 'checkpoint': check, 'step': step}, 'Metric intent differs')
    require(read(receipt_path) == {'binding': binding, 'intent': pin(intent_path), 'metrics': pin(path)}, 'Metric receipt differs')
    value = read(path)
    require(value['checkpoint'] == check and value['updates'] == step and value['evaluationBatchSize'] == 32,
            'Metric checkpoint/config differs')
    rows = value['rows']
    require(value['observations'] == len(rows) == binding['settings']['observations']
            and value['buildExactCount'] == sum(r['buildExact'] for r in rows)
            and value['meanBuildNll'] == float(np.mean([r['buildNll'] for r in rows]))
            and value['summary'] == summarize(rows), 'Metric aggregation differs')
    for i, row in enumerate(rows):
        require(all(np.isfinite(row[m]) for m in METRICS), 'Nonfinite retained metric')
        target, predicted = set(row['targetCells']), set(row['predictedCells'])
        overlap, both_empty = len(target & predicted), not target and not predicted
        require(row['targetCells'] == sorted(target) and row['predictedCells'] == sorted(predicted)
                and row['buildExact'] == int(target == predicted) and row['teacherCardinality'] == len(target)
                and row['cellPrecision'] == (1. if both_empty else overlap / len(predicted) if predicted else 0.)
                and row['cellRecall'] == (1. if both_empty else overlap / len(target) if target else 0.), 'Metric set summary differs')
        if expected_rows is not None:
            source = expected_rows[i]
            for key in ('observationId', 'mapHash', 'gameId', 'teacherSeat', 'turn', 'inputSha256', 'targetCells', 'hadSkippedActions'):
                require(row[key] == source[key], 'Metric source/label differs')
            require(all(0 <= c < source['size'] and source['legalBuild'][c] for c in predicted)
                    and row['predictedPaint'] == sum(int(source['costs'][c]) for c in predicted) <= 3
                    and row['teacherPaint'] == source['acceptedPaint'], 'Metric construction legality/paint differs')
    return value


def record_metrics(folder, model, data, binding):
    step = model.updates; name = f'metrics-{step:04d}'
    path, intent_path, receipt_path = [folder / (name + suffix) for suffix in ('.json', '-intent.json', '-receipt.json')]
    if not any(p.exists() for p in (path, intent_path, receipt_path)):
        check = pin(folder / 'checkpoints' / f'step-{step:04d}.npz')
        write_once(intent_path, {'binding': binding, 'checkpoint': check, 'step': step})
        write_once(path, {'updates': step, 'checkpoint': check, **evaluate(model, data)})
        write_once(receipt_path, {'binding': binding, 'intent': pin(intent_path), 'metrics': pin(path)})
    validate_metrics(folder, step, binding, data.rows)


def make_binding(freeze_path, freeze):
    return {'schema': 'indrill-build64-checkpoint-v1', 'freeze': pin(freeze_path), 'selectedRows': freeze['selectedRows'],
            'settings': SETTINGS, 'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM}


def run(freeze_path, resume=False):
    freeze, data = verify_freeze(freeze_path)
    folder, binding = OUTPUT / 'run', make_binding(freeze_path, freeze)
    model, rng = initialize(), np.random.default_rng(SETTINGS['samplingSeed'])
    order, position = np.empty(0, np.int64), 0
    if resume:
        require(folder.is_dir() and not (folder / 'result.json').exists() and not (folder / 'failure.json').exists(),
                'Only an incomplete nonfailed original run may resume')
        intent = read(folder / 'intent.json')
        require(intent['binding'] == binding and intent['environment'] == environment(), 'Run intent/runtime differs')
        order, position = verify_chain(folder, model, rng, binding, SETTINGS)
        for step in SETTINGS['metricsAt']:
            if step < model.updates:
                validate_metrics(folder, step, binding, data.rows)
    else:
        folder.mkdir()
        write_once(folder / 'intent.json', {'binding': binding, 'environment': environment(), 'createdAtUtc': datetime.now(timezone.utc).isoformat()})
        (folder / 'checkpoints').mkdir()
        path = folder / 'checkpoints/step-0000.npz'
        checkpoint(path, model, rng, order, position, binding)
        write_once(path.with_suffix('.json'), {'binding': binding, 'checkpoint': pin(path), 'previousReceipt': None})
    try:
        while True:
            step = model.updates
            if step in SETTINGS['metricsAt']:
                record_metrics(folder, model, data, binding)
            if step == SETTINGS['updates']:
                break
            chosen, order, position = sampler(rng, order, position)
            before = folder / 'checkpoints' / f'step-{step:04d}.npz'
            path = folder / 'checkpoints' / f'step-{step + 1:04d}.npz'
            previous_receipt = pin(before.with_suffix('.json'))
            intent_path = folder / f'update-{step + 1:04d}-intent.json'
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
    final_path = folder / f'metrics-{SETTINGS["updates"]:04d}.json'
    final = validate_metrics(folder, SETTINGS['updates'], binding, data.rows)
    result = {'schema': 'indrill-build64-run-result-v1', 'freeze': pin(freeze_path), 'updates': model.updates,
              'sampleVisits': model.updates * SETTINGS['batch'], 'finalBuildExact': final['buildExactCount'],
              'decision': 'BUILD_FIT_ESTABLISHED' if final['buildExactCount'] >= 63 else 'BUILD_FIT_NOT_ESTABLISHED',
              'checkpoint': pin(folder / 'checkpoints' / f'step-{model.updates:04d}.npz'), 'finalMetrics': pin(final_path),
              'newGames': 0, 'promotion': False}
    write_once(folder / 'result.json', result)
    print(json.dumps(result))


def finalize(freeze_path):
    require(not (OUTPUT / 'result.json').exists(), 'Refusing existing final report')
    freeze, data = verify_freeze(freeze_path)
    folder, binding = OUTPUT / 'run', make_binding(freeze_path, freeze)
    intent, result = read(folder / 'intent.json'), read(folder / 'result.json')
    require(intent['binding'] == binding and intent['environment'] == environment(), 'Run intent/runtime differs')
    model, rng = initialize(), np.random.default_rng(SETTINGS['samplingSeed'])
    verify_chain(folder, model, rng, binding, SETTINGS)
    require(model.updates == SETTINGS['updates'] == result['updates'] and result['sampleVisits'] == 32000
            and result['freeze'] == pin(freeze_path), 'Incomplete fixed budget')
    for step in SETTINGS['metricsAt']:
        validate_metrics(folder, step, binding, data.rows)
    require(result['checkpoint'] == pin(folder / 'checkpoints/step-2000.npz')
            and result['finalMetrics'] == pin(folder / 'metrics-2000.json'), 'Result does not bind final artifacts')
    final, old_final = read(result['finalMetrics']['path']), read(freeze['oldFinalMetrics']['path'])
    require(result['finalBuildExact'] == final['buildExactCount'] and result['decision'] ==
            ('BUILD_FIT_ESTABLISHED' if final['buildExactCount'] >= 63 else 'BUILD_FIT_NOT_ESTABLISHED'), 'Final decision differs')
    schedule, exposure = hashlib.sha256(), np.zeros(64, np.int64)
    schedule_rng, order, position = np.random.default_rng(SETTINGS['samplingSeed']), np.empty(0, np.int64), 0
    for step in range(2001):
        chosen = np.empty(0, np.int64)
        if step:
            chosen, order, position = sampler(schedule_rng, order, position); exposure[chosen] += 1
        hash_entry(schedule, schedule_entry(step, chosen, order, position, schedule_rng))
    old_audit = read(freeze['oldSamplingAudit']['path'])
    require(old_audit['complete'] is True and old_audit['checksPassed'] is True and old_audit['settings'] == SETTINGS
            and old_audit['scheduleSha256'] == schedule.hexdigest() and np.array_equal(exposure, np.full(64, 500)),
            'Original/candidate sampling evidence differs')
    pairs = list(zip(final['rows'], old_final['rows']))
    require(len(pairs) == len(final['rows']) == len(old_final['rows']) == 64, 'Comparison cardinality differs')
    for new, old in pairs:
        require(all(new[k] == old[k] for k in ('observationId', 'inputSha256', 'mapHash', 'targetCells')), 'Comparison rows differ')
    old_count = sum(r['buildExact'] for r in old_final['rows']); new_count = final['buildExactCount']
    require(old_count == 15, 'Retained baseline count differs')
    groups = {name: [new['observationId'] for new, old in pairs if (bool(new['buildExact']), bool(old['buildExact'])) == flags]
              for name, flags in (('gained', (True, False)), ('lost', (False, True)), ('retainedExact', (True, True)), ('bothInexact', (False, False)))}
    report = {'schema': 'indrill-build64-final-v1', 'complete': True, 'freeze': pin(freeze_path), 'runResult': pin(folder / 'result.json'),
              'decision': result['decision'], 'updates': 2000, 'sampleVisits': 32000, 'visitsPerObservation': exposure.tolist(),
              'checkpointsVerified': 2001, 'matchedOldScheduleSha256': schedule.hexdigest(),
              'comparison': {'oldFinalMetrics': freeze['oldFinalMetrics'], 'oldBuildExact': old_count, 'candidateBuildExact': new_count,
                  'netExactDifference': new_count - old_count, 'status': 'BETTER' if new_count > old_count else 'EQUAL' if new_count == old_count else 'WORSE',
                  'oldMeanBuildNll': float(np.mean([r['buildNll'] for r in old_final['rows']])), 'candidateMeanBuildNll': final['meanBuildNll'],
                  'rowGroups': groups, 'groupCounts': {k: len(v) for k, v in groups.items()}},
              'metrics': [pin(folder / f'metrics-{step:04d}.json') for step in SETTINGS['metricsAt']],
              'newLearnedPredictionsDuringFinalization': 0, 'newGradientsDuringFinalization': 0, 'newGames': 0, 'promotion': False,
              'scope': 'Fixed-panel build fitting only; no strength, transfer or universal capacity claim.'}
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
