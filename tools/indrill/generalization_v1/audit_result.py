"""Saved-evidence audit only: no learned forward, gradients, training or games.

Run audit and archive only after the fixed 12,000-update generalization run.
Saved arithmetic and bootstrap checks never call learned forward or gradients.
"""
import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import sys
import zipfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from tools.indrill.generalization_v1 import run_experiment as run

np = run.np
OUT = ROOT / 'build/analysis/indrill-generalization-v1'
FINAL = ROOT / 'docs/evidence/indrill-generalization-v1.json'
ARCHIVE = FINAL.with_name('indrill-generalization-v1-artifacts.zip')
INDEX = ARCHIVE.with_suffix('.json')
CHAIN = OUT / 'chain-manifest.json'
INTENT = OUT / 'completed-audit-intent.json'
FAILURE = OUT / 'completed-audit-failure.json'
METRIC_STEPS = [0, 4000, 8000, 12000]
METRIC_FIELDS = ('buildExact', 'inkExact', 'jointExact', 'cellPrecision', 'cellRecall', 'predictedPaint', 'teacherPaint', 'buildNll', 'inkNll', 'jointNll')
PINS = {}
need = run.require


def remember(path):
    value = run.pin(path)
    previous = PINS.setdefault(value['path'], value['sha256'])
    need(previous == value['sha256'], 'Evidence changed during audit: ' + value['path'])
    return value


def checked(item):
    value = remember(item['path'])
    need(value['sha256'] == item['sha256'], 'Pinned evidence differs: ' + item['path'])
    return value


def saved(path):
    remember(path)
    return run.read(path)


def finite(value):
    return type(value) in (int, float) and math.isfinite(value)


def same(observed, expected, label):
    """Compare saved aggregation arithmetic; this never evaluates the model."""
    if isinstance(expected, dict):
        need(isinstance(observed, dict) and set(observed) == set(expected), label + ': keys differ')
        for key, value in expected.items():
            same(observed[key], value, label + '/' + key)
    elif isinstance(expected, list):
        need(isinstance(observed, list) and len(observed) == len(expected), label + ': list differs')
        for i, value in enumerate(expected):
            same(observed[i], value, label + '/' + str(i))
    elif isinstance(expected, float):
        need(finite(observed) and math.isclose(observed, expected, rel_tol=1e-12, abs_tol=1e-12), label + ': number differs')
    else:
        need(observed == expected, label + ': value differs')


def aggregate(rows):
    maps = defaultdict(list)
    for row in rows:
        maps[row['mapHash']].append(row)
    means = {key: {metric: float(np.mean([row[metric] for row in group])) for metric in METRIC_FIELDS}
             for key, group in sorted(maps.items())}
    return {'observations': len(rows), 'maps': len(means), 'mapMeans': means,
            'meanOfMapMeans': {metric: float(np.mean([m[metric] for m in means.values()])) if means else None
                               for metric in METRIC_FIELDS}}


def summaries(rows):
    report = {'all': aggregate(rows)}
    for field in ('teacherCardinality', 'costPattern', 'turnBand', 'teacherSeat', 'hadSkippedActions'):
        report[field] = {value: aggregate([r for r in rows if str(r[field]) == value])
                         for value in sorted({str(r[field]) for r in rows})}
    report['cohorts'] = {cohort: aggregate([r for r in rows if cohort in r['cohortTags']]) for cohort in ('old40', 'later11', 'holdoutCompanion')}
    return report


def verify_rows(metric, source_rows, collision_summary):
    need(metric['observations'] == len(source_rows) == len(metric['rows']), 'Metric panel cardinality differs')
    need([r['observationId'] for r in metric['rows']] == [r['observationId'] for r in source_rows],
         'Saved metric row order differs')
    need(metric['precisionRecallConvention'] == 'Both empty: precision=recall=1. Exactly one empty: both=0.',
         'Metric overlap convention differs')
    need(metric['collisionSummary'] == collision_summary, 'Fixed-panel collision summary differs')
    for row, source in zip(metric['rows'], source_rows):
        for key in ('observationId', 'mapHash', 'gameId', 'teacherSeat', 'turn', 'cohortTags',
                    'inputSha256', 'hadSkippedActions', 'targetCells', 'targetInk'):
            need(row[key] == source[key], 'Metric source identity differs: ' + key)
        target, predicted, ink = row['targetCells'], row['predictedCells'], row['predictedInk']
        need(predicted == sorted(set(predicted)) and all(type(c) is int and 0 <= c < source['size']
             and source['legalBuild'][c] for c in predicted), 'Saved prediction is not a legal cell set')
        need(type(ink) is int and (ink == -1 or 0 <= ink < source['regionCount'] and source['legalInk'][ink]),
             'Saved ink/NONE prediction is invalid')
        paint, teacher_paint = sum(source['costs'][c] for c in predicted), sum(source['costs'][c] for c in target)
        need(paint <= 3 and teacher_paint <= 3, 'Saved set exceeds paint budget')
        overlap, both_empty = len(set(target) & set(predicted)), not target and not predicted
        expected = {'buildExact': int(target == predicted), 'inkExact': int(ink == source['targetInk']),
                    'jointExact': int(target == predicted and ink == source['targetInk']), 'teacherCardinality': len(target),
                    'cellPrecision': 1. if both_empty else overlap / len(predicted) if predicted else 0.,
                    'cellRecall': 1. if both_empty else overlap / len(target) if target else 0.,
                    'predictedPaint': paint, 'teacherPaint': teacher_paint,
                    'turnBand': '0..19' if source['turn'] < 20 else '20..49' if source['turn'] < 50 else '50+',
                    'costPattern': '+'.join(str(source['costs'][c]) for c in sorted(target, key=lambda c: (source['costs'][c], c))) or 'EMPTY'}
        for key, value in expected.items():
            same(row[key], value, 'metric/' + key)
        need(all(finite(row[key]) and row[key] >= -1e-10 for key in ('buildNll', 'inkNll', 'jointNll')), 'Saved NLL is invalid')
        same(row['jointNll'], row['buildNll'] + row['inkNll'], 'metric/jointNll')
    counts = {field + 'Count': sum(row[field] for row in metric['rows']) for field in ('buildExact', 'inkExact', 'jointExact')}
    need(metric['jointExactCount'] == counts['jointExactCount'], 'Saved joint exact count differs')
    same(metric['summary'], summaries(metric['rows']), 'metric/summary')
    return {'observations': len(source_rows), 'maps': metric['summary']['all']['maps'], **counts,
            'meanOfMapMeans': metric['summary']['all']['meanOfMapMeans'],
            'meanBuildNll': float(np.mean([row['buildNll'] for row in metric['rows']])),
            'meanInkNll': float(np.mean([row['inkNll'] for row in metric['rows']])),
            'meanJointNll': float(np.mean([row['jointNll'] for row in metric['rows']])),
            'meanCellPrecision': float(np.mean([row['cellPrecision'] for row in metric['rows']])),
            'meanCellRecall': float(np.mean([row['cellRecall'] for row in metric['rows']])),
            'totalPredictedPaint': sum(row['predictedPaint'] for row in metric['rows']),
            'totalTeacherPaint': sum(row['teacherPaint'] for row in metric['rows'])}


def reproduce_transfer(candidate, comparator):
    """Bootstrap saved paired map means; no learned predictions or derivatives."""
    need([r['observationId'] for r in candidate['rows']] == [r['observationId'] for r in comparator['rows']],
         'Holdout observations are unpaired')
    first, second = candidate['summary']['all']['mapMeans'], comparator['summary']['all']['mapMeans']
    need(set(first) == set(second), 'Holdout map sets differ')
    keys = sorted(first)
    differences = np.asarray([[first[k][m] - second[k][m] for m in ('buildExact', 'inkExact')] for k in keys], np.float64)
    rng, intervals = np.random.default_rng(2026100421), {}
    if keys:
        boot = np.empty((10000, 2), np.float64)
        for start in range(0, 10000, 100):
            sampled = rng.integers(0, len(keys), (100, len(keys)))
            boot[start:start + 100] = differences[sampled].mean(axis=1)
        for i, metric in enumerate(('buildExact', 'inkExact')):
            intervals[metric] = {'difference': float(differences[:, i].mean()),
                                 'percentile95': np.percentile(boot[:, i], [2.5, 97.5]).tolist()}
    passed = len(keys) >= 20 and all(intervals[m]['percentile95'][0] > 0 for m in ('buildExact', 'inkExact'))
    return {'decision': 'TRANSFER_EVIDENCE' if passed else 'TRANSFER_NOT_ESTABLISHED', 'maps': len(keys),
            'pairedMapBootstrap': 10000, 'seed': 2026100421, 'intervals': intervals,
            'interpretation': 'Development-holdout diagnostic; no arena strength or pristine-confirmation claim.'}


def audit_chain(folder, binding):
    """Reproduce initialization, sampler and Adam arithmetic from saved moments."""
    points = sorted((folder / 'checkpoints').glob('step-*.npz'), key=lambda p: int(p.stem.split('-')[1]))
    expected_names = {f'step-{step:05d}{suffix}' for step in range(12001) for suffix in ('.npz', '.json')}
    need([int(p.stem.split('-')[1]) for p in points] == list(range(12001))
         and {p.name for p in (folder / 'checkpoints').iterdir()} == expected_names,
         'Checkpoint inventory is not exactly numeric 0..12000')
    expected_intents = {f'update-{step:05d}-intent.json' for step in range(1, 12001)}
    need({p.name for p in folder.glob('update-*-intent.json')} == expected_intents, 'Update intent inventory differs')
    initial = run.initialize()  # Seeded initializer only; never forward or optimizer.
    shapes = {name: value.shape for name, value in initial.p.items()}
    tensor_keys = {'metadata', 'sampleOrder'} | {prefix + name for name in shapes for prefix in ('', 'adam_m_', 'adam_v_')}
    rng, order, position = np.random.default_rng(2026100412), np.empty(0, np.int64), 0
    exposure, schedule = np.zeros(2048, np.int64), hashlib.sha256()
    previous_parameters, previous_checkpoint, previous_receipt = None, None, None
    entries, clipped, max_difference = [], 0, 0.
    for step, path in enumerate(points):
        need(path == folder / f'checkpoints/step-{step:05d}.npz', 'Noncanonical checkpoint name')
        chosen = np.empty(0, np.int64)
        if step:
            if position == len(order):
                order, position = rng.permutation(2048), 0
            chosen = order[position:position + 32]
            need(len(chosen) == 32, 'Partial sampler batch')
            position += 32
            exposure[chosen] += 1
        entry = {'step': step, 'sampledIndices': chosen.tolist(),
                 'sampleOrderSha256': hashlib.sha256(np.asarray(order, '<i8').tobytes()).hexdigest(),
                 'samplePosition': position, 'rngState': rng.bit_generator.state}
        schedule.update(json.dumps(entry, sort_keys=True, separators=(',', ':')).encode() + b'\n')
        checkpoint, receipt_path = remember(path), path.with_suffix('.json')
        receipt = saved(receipt_path)
        expected_receipt = {'binding': binding, 'checkpoint': checkpoint, 'previousReceipt': previous_receipt,
                            'schedule': entry, 'updateIntent': None, 'loss': None,
                            'meanHeadNll': None, 'unclippedGradientNorm': None}
        update_pin = None
        if step:
            update_path = folder / f'update-{step:05d}-intent.json'
            need(saved(update_path) == {'binding': binding, 'step': step, 'fromCheckpoint': previous_checkpoint,
                 'previousReceipt': previous_receipt, 'sampledIndices': chosen.tolist()}, 'Update intent sampling/binding differs')
            update_pin = remember(update_path)
            need(finite(receipt['loss']) and finite(receipt['unclippedGradientNorm'])
                 and receipt['unclippedGradientNorm'] >= 0, 'Nonfinite saved loss/norm')
            need(isinstance(receipt['meanHeadNll'], list) and len(receipt['meanHeadNll']) == 2
                 and all(finite(v) and v >= -1e-10 for v in receipt['meanHeadNll']), 'Invalid saved head losses')
            same(receipt['loss'], float(sum(receipt['meanHeadNll'])), 'update/head-loss-sum')
            clipped += int(receipt['unclippedGradientNorm'] > 10)
            expected_receipt.update(updateIntent=update_pin, meanHeadNll=receipt['meanHeadNll'],
                                    loss=receipt['loss'], unclippedGradientNorm=receipt['unclippedGradientNorm'])
        need(receipt == expected_receipt, 'Checkpoint receipt chain differs')
        with np.load(path, allow_pickle=False) as state:
            need(set(state.files) == tensor_keys and state['metadata'].shape == () and state['metadata'].dtype.kind == 'U',
                 'Checkpoint tensor/metadata inventory differs')
            need(json.loads(str(state['metadata'])) == dict(binding, updates=step,
                 rngState=rng.bit_generator.state, samplePosition=position), 'Metadata/sampler state differs')
            need(state['sampleOrder'].dtype == np.int64 and np.array_equal(state['sampleOrder'], order),
                 'Exact sample order differs')
            parameters = {}
            for name, shape in shapes.items():
                parameter, moment, variance = [state[prefix + name] for prefix in ('', 'adam_m_', 'adam_v_')]
                need(all(a.shape == shape and a.dtype == np.float32 and np.isfinite(a).all()
                         for a in (parameter, moment, variance)), 'Wrong/nonfinite tensor: ' + name)
                need((variance >= 0).all(), 'Negative Adam variance')
                if name in ('WV', 'BV'):
                    need(all(not np.any(a) for a in (parameter, moment, variance)), 'Frozen value state changed')
                if step == 0:
                    need(parameter.tobytes() == initial.p[name].tobytes() and not np.any(moment) and not np.any(variance),
                         'Not the fixed fresh initializer/zero optimizer')
                else:
                    predicted = previous_parameters[name].copy()
                    predicted -= .0003 * (moment / (1. - .9 ** step)) / (np.sqrt(variance / (1. - .999 ** step)) + 1e-8)
                    max_difference = max(max_difference, float(np.max(np.abs(predicted.astype(np.float64) - parameter.astype(np.float64)))))
                    need(predicted.tobytes() == parameter.tobytes(), 'Saved Adam parameter step differs: ' + name)
                parameters[name] = parameter.copy()
        entries.append({'step': step, 'checkpoint': checkpoint, 'receipt': remember(receipt_path),
                        'updateIntent': update_pin, 'sampledIndices': chosen.tolist(), 'samplePosition': position})
        previous_parameters, previous_checkpoint, previous_receipt = parameters, checkpoint, remember(receipt_path)
    need(int(exposure.sum()) == 384000 and Counter(exposure.tolist()) == {187: 1024, 188: 1024}
         and position == 1024, 'Fixed final sampling exposure differs')
    return entries, {'checkpointsVerified': 12001, 'updates': 12000, 'sampleVisits': 384000,
        'visitsPerObservation': exposure.tolist(), 'exposureCounts': {'187': 1024, '188': 1024},
        'scheduleSha256': schedule.hexdigest(), 'fixedFreshInitializerBitExact': True,
        'allSamplerRngOrderPositionExact': True, 'allTensorsFinite': True, 'valueHeadAndMomentsZero': True,
        'adamStepsBitExact': True, 'adamMaxAbsDifference': max_difference, 'clippedUpdates': clipped}


def evaluation_triplet(paths, binding, target):
    payload_path, intent_path, receipt_path = paths
    need(all(p.exists() for p in paths), 'Missing/incomplete saved evaluation')
    need(saved(intent_path) == {'binding': binding, 'target': target}, 'Evaluation intent differs')
    need(saved(receipt_path) == {'binding': binding, 'target': target,
         'intent': remember(intent_path), 'payload': remember(payload_path)}, 'Evaluation receipt differs')
    return saved(payload_path), {'metrics': remember(payload_path), 'intent': remember(intent_path), 'receipt': remember(receipt_path)}


def preparation_history(freeze):
    """Preserve rejected pre-gradient metadata; never treat its freeze as active."""
    record_path = OUT / 'setup/preparation-repair.json'
    record = saved(record_path)
    need(record['schema'] == 'indrill-generalization-preparation-repair-v1'
         and record['realRunStarted'] is False and record['realGradients'] == record['realLearnedEvaluations'] == 0
         and record['nextChecks'] == freeze['checks'], 'Pre-gradient preparation repair record differs')
    expected = {OUT / 'setup/rejected-preparation-1' / name for name in ('freeze.json', 'prepare-intent.json')}
    retained = record['retained']
    need(len(retained) == 2 and {run.absolute(item['retained']['path']) for item in retained} == expected,
         'Rejected preparation inventory differs')
    pins = [remember(record_path)]
    for item in retained:
        need(item['original']['sha256'] == item['retained']['sha256'], 'Rejected preparation bytes were changed during preservation')
        pins.append(checked(item['retained']))
    rejected = saved(OUT / 'setup/rejected-preparation-1/freeze.json')
    # Retain the stale proof as bytes only. Its old source pins intentionally no
    # longer match the active learner; do not traverse or validate them as active.
    need(run.absolute(rejected['checks']['path']) == OUT / 'setup/checks-second/result.json', 'Unexpected rejected setup proof')
    pins.append(checked(rejected['checks']))
    return {'classification': 'REJECTED_PREGRADIENT_PREPARATION', 'record': remember(record_path),
            'retainedMetadata': pins[1:], 'activeProof': freeze['checks'],
            'historicalFreezeValidatedAsActive': False, 'trainingRetry': False}, pins


def audit():
    need(not any(p.exists() for p in (INTENT, FAILURE, CHAIN, FINAL)), 'Preserve previous audit attempts/outputs')
    folder = OUT / 'run'
    result = saved(folder / 'result.json')
    need(result['complete'] is True and result['updates'] == 12000 and result['sampleVisits'] == 384000,
         'Complete fixed run and both final holdout evaluations required')
    freeze, data = run.verify_freeze(OUT / 'freeze.json')
    freeze_pin = remember(OUT / 'freeze.json')
    need(run.SETTINGS == {'modelSeed': 2026100402, 'samplingSeed': 2026100412,
         'updates': 12000, 'batch': 32, 'observations': 2048, 'metricsAt': METRIC_STEPS}, 'Fixed recipe differs')
    run.write_once(INTENT, {'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'helper': run.pin(__file__),
        'freeze': freeze_pin, 'completedResult': run.pin(folder / 'result.json'),
        'newLearnedPredictions': 0, 'newGradients': 0, 'newGames': 0})
    try:
        for item in freeze['pins']:
            checked(item)
        for path in (__file__, INTENT):
            remember(path)
        setup_history, setup_pins = preparation_history(freeze)
        original = saved(freeze['originalFreeze']['path'])
        need(not (ROOT / 'build/analysis/indrill-capacity-v1/stage-B').exists(), 'Closed original Stage B was reopened')
        prepare = saved(OUT / 'prepare-intent.json')
        need(prepare['settings'] == run.SETTINGS and prepare['originalFreeze'] == freeze['originalFreeze']
             and prepare['prerequisite'] == freeze['prerequisite']
             and prepare['gradientsRun'] == prepare['learnedPredictions'] == 0, 'Preparation binding differs')
        datetime.fromisoformat(prepare['createdAtUtc'])
        prerequisite = saved(freeze['prerequisite']['evidence']['path'])
        need(prerequisite['complete'] is True and prerequisite['auditStatus'] == 'PASS'
             and prerequisite['decision'] == 'JOINT_FIT_ESTABLISHED'
             and prerequisite['curve'][-1]['jointExactCount'] == 63
             and freeze['prerequisite']['weightsInherited'] is False, 'Fitting prerequisite differs')
        binding, startup = run.make_binding(OUT / 'freeze.json', freeze), saved(folder / 'intent.json')
        need(set(startup) == {'createdAtUtc', 'binding', 'environment', 'freshInitialization', 'prerequisiteWeightsInherited'}
             and startup['binding'] == binding and startup['environment'] == run.environment()
             and startup['freshInitialization'] is True and startup['prerequisiteWeightsInherited'] is False,
             'Startup recipe/runtime differs')
        datetime.fromisoformat(startup['createdAtUtc'])
        expected = {'intent.json', 'result.json', 'checkpoints', 'holdout-final.json'}
        expected |= {f'update-{step:05d}-intent.json' for step in range(1, 12001)}
        expected |= {f'train-metrics-{step:05d}{suffix}' for step in METRIC_STEPS for suffix in ('.json', '-intent.json', '-receipt.json')}
        expected |= {f'holdout-{name}{suffix}' for name in ('candidate', 'frozenQv2') for suffix in ('.json', '-intent.json', '-receipt.json')}
        need({p.name for p in folder.iterdir()} == expected, 'Unexpected/missing production file or retained failure')
        train_indices, held_indices = data.panel_indices('B'), data.panel_indices('holdout')
        train_rows, held_rows = [data.rows[i] for i in train_indices], [data.rows[i] for i in held_indices]
        need(len(train_rows) == 2048 and len(held_rows) == 1020, 'Fixed panel size differs')
        train_maps, held_maps = {r['mapHash'] for r in train_rows}, {r['mapHash'] for r in held_rows}
        need(len(train_maps) == 128 and len(held_maps) == 51 and not train_maps & held_maps
             and not {r['inputSha256'] for r in train_rows} & {r['inputSha256'] for r in held_rows}, 'Map/input split differs')
        train_collision = run.teacher.collision_summary(data.rows, train_indices)
        held_collision = run.teacher.collision_summary(data.rows, held_indices)
        need(train_collision == freeze['datasetSummary']['trainingCollisionSummary'], 'Training panel collision binding differs')
        entries, integrity = audit_chain(folder, binding)
        curve = []
        for step in METRIC_STEPS:
            target = {'kind': 'training', 'step': step, 'checkpoint': entries[step]['checkpoint']}
            metric, pins = evaluation_triplet(run.metric_paths(folder, step), binding, target)
            need(metric['stage'] == 'B' and metric['updates'] == step and metric['checkpoint'] == target['checkpoint'],
                 'Training metric boundary differs')
            curve.append({'step': step, **pins, **verify_rows(metric, train_rows, train_collision)})
        final_checkpoint = entries[-1]['checkpoint']
        candidate_target = {'kind': 'holdout-candidate', 'step': 12000, 'checkpoint': final_checkpoint}
        candidate, candidate_pins = evaluation_triplet(run.holdout_paths(folder, 'candidate'), binding, candidate_target)
        need(candidate['stage'] == 'B' and candidate['updates'] == 12000 and candidate['checkpoint'] == final_checkpoint,
             'Holdout candidate checkpoint differs')
        comparator_target = {'kind': 'holdout-frozenQv2', 'comparator': freeze['comparator']}
        comparator, comparator_pins = evaluation_triplet(run.holdout_paths(folder, 'frozenQv2'), binding, comparator_target)
        need(comparator['comparator'] == freeze['comparator'] == original['comparator'], 'Frozen comparator differs')
        candidate_summary = verify_rows(candidate, held_rows, held_collision)
        comparator_summary = verify_rows(comparator, held_rows, held_collision)
        transfer = reproduce_transfer(candidate, comparator)
        held_report = saved(folder / 'holdout-final.json')
        same(held_report, {'binding': binding, 'checkpoint': final_checkpoint, 'comparator': freeze['comparator'],
             'candidate': candidate_pins['metrics'], 'frozenQv2': comparator_pins['metrics'], 'comparison': transfer}, 'holdout/final')
        expected_result = {'schema': 'indrill-generalization-run-result-v1', 'complete': True, 'freeze': freeze_pin,
            'updates': 12000, 'sampleVisits': 384000, 'checkpoint': final_checkpoint, 'finalMetrics': curve[-1]['metrics'],
            'finalTrainingJointExact': curve[-1]['jointExactCount'], 'holdoutFinal': remember(folder / 'holdout-final.json'),
            'decision': transfer['decision'], 'freshInitialization': True, 'newGames': 0, 'promotion': False, 'oldStageBReopened': False}
        same(result, expected_result, 'run/result')
        gap = {key: candidate_summary['meanOfMapMeans'][key] - curve[-1]['meanOfMapMeans'][key] for key in METRIC_FIELDS}
        registry_path = ROOT / 'build/bench/indrill-capacity-v1-diagnostic-maps.json'
        registry = saved(registry_path)
        need(registry['distinctMaps'] == 293 and (train_maps | held_maps) <= set(registry['gamesPerMap']), 'Diagnostic map exclusions differ')
        production_metadata = [remember(path) for path in sorted(folder.rglob('*.json'))]
        archive_paths = {Path(__file__).resolve(), OUT / 'freeze.json', OUT / 'prepare-intent.json', INTENT,
                         run.PROTOCOL, run.absolute(freeze['checks']['path']), run.absolute(freeze['originalFreeze']['path']), registry_path}
        readme = Path(__file__).with_name('README.md')
        if readme.exists():
            archive_paths.add(readme)  # Documentary snapshot, pinned here before archive.
        archive_paths.update(run.absolute(item['path']) for item in production_metadata)
        archive_paths.update(run.absolute(item['path']) for item in setup_pins)
        archive_paths.update(folder / f'checkpoints/step-{step:05d}.npz' for step in METRIC_STEPS)
        archive_paths.update(run.absolute(item['path']) for item in freeze['pins']
                             if run.absolute(item['path']).is_relative_to(ROOT) and run.absolute(item['path']).suffix in ('.py', '.md'))
        directory = run.absolute(freeze['datasetDirectory'])
        archive_paths.update(directory / name for name in ('dataset-manifest.json', 'teacher-preflight.json',
                                                          'loader-audit.json', 'independent-review.json'))
        archive_inputs = [remember(path) for path in sorted(archive_paths)]
        # Every active source/input and output pin is rehashed before sealing.
        # Avoid another full corpus load, learned call or parent tensor audit.
        need(run.environment() == freeze['environment'], 'Runtime changed during audit')
        for path, digest in list(PINS.items()):
            need(run.digest(path) == digest, 'Audited bytes changed before seal: ' + path)
        run.write_once(CHAIN, {'schema': 'indrill-generalization-completed-chain-v1', 'complete': True, 'auditStatus': 'PASS',
            'freeze': freeze_pin, 'helper': run.pin(__file__), 'chain': entries, 'integrity': integrity,
            'preparationHistory': setup_history,
            'productionMetadataPins': production_metadata, 'archiveInputs': archive_inputs,
            'auditedPins': [{'path': path, 'sha256': digest} for path, digest in sorted(PINS.items())],
            'method': 'Fresh initialization, all saved state boundaries, exact sampler and Adam parameter arithmetic from saved moments, saved metric arithmetic and paired-map bootstrap; no learned forward or gradients.'})
        report = {'schemaVersion': 1, 'experiment': 'indrill-generalization-v1', 'complete': True, 'auditStatus': 'PASS',
            'decision': transfer['decision'], 'freeze': freeze_pin, 'helper': run.pin(__file__), 'intent': run.pin(INTENT),
            'checkpointChain': run.pin(CHAIN), 'runnerResult': run.pin(folder / 'result.json'), 'settings': run.SETTINGS,
            'integrity': integrity, 'trainingObservations': 2048, 'trainingMaps': 128, 'holdoutObservations': 1020, 'holdoutMaps': 51,
            'curve': curve, 'holdoutCandidate': {**candidate_pins, **candidate_summary},
            'holdoutFrozenQv2': {**comparator_pins, **comparator_summary}, 'transfer': transfer,
            'holdoutReport': remember(folder / 'holdout-final.json'), 'holdoutMinusTrainingMapMean': gap,
            'originalFreeze': freeze['originalFreeze'], 'fittingPrerequisite': freeze['prerequisite'],
            'preparationHistory': setup_history,
            'syntheticChecks': freeze['checks'], 'comparator': freeze['comparator'], 'freshInitialization': True,
            'mapExclusionRegistry': remember(registry_path), 'retainedExactCoreGames': 293,
            'newGames': 0, 'auditNewLearnedPredictions': 0, 'auditNewGradients': 0,
            'oldStageBReopened': False, 'promotion': False, 'arenaUploaded': False,
            'limitations': ['Outcome-informed experiment on existing research holdouts, not pristine confirmation or arena-strength evidence.',
                'There is no binary training-fit threshold. Low training agreement cannot isolate generalization from limited optimization or representation.',
                'Transfer compares held-out action agreement with weak frozen Q-v2; it does not establish champion-quality play. Cross-model NLL is descriptive, not a gate.',
                'Intervals resample maps, not individual turns; the two-head decision is a diagnostic convention, not an overall 95% guarantee.',
                'Audit verifies saved prediction/label bindings and NLL sums/aggregations but does not rederive predictions or NLL from model logits.',
                'Adam parameter arithmetic is checked from saved moments without reconstructing gradients; the origin of those moments is not independently established.',
                'No new Java export, live game, RL update or automatic subsequent experiment is authorized by this verdict.']}
        run.write_once(FINAL, report)
        print(json.dumps({'evidence': run.pin(FINAL), 'decision': transfer['decision'],
              'trainingJointExact': curve[-1]['jointExactCount'], 'holdoutJointExact': candidate_summary['jointExactCount']}))
    except Exception as error:
        run.write_once(FAILURE, {'auditStatus': 'FAILED', 'intent': run.pin(INTENT), 'helper': run.pin(__file__),
            'errorType': type(error).__name__, 'error': str(error), 'automaticRetry': False,
            'newLearnedPredictions': 0, 'newGradients': 0, 'newGames': 0})
        raise


def archive():
    """Write one compact ZIP from the completed audit's exact curated inventory."""
    need(not ARCHIVE.exists() and not INDEX.exists(), 'Preserve previous archive/index')
    final, chain = run.read(FINAL), run.read(CHAIN)
    need(final['experiment'] == 'indrill-generalization-v1' and final['complete'] is True
         and final['auditStatus'] == 'PASS' and chain['complete'] is True and chain['auditStatus'] == 'PASS'
         and final['checkpointChain'] == run.pin(CHAIN) and final['freeze'] == chain['freeze']
         and chain['helper'] == run.pin(__file__), 'Completed audit/helper binding differs')
    run.check_pin(chain['freeze'])
    verified = {}
    for item in chain['auditedPins']:
        path = run.absolute(item['path'])
        need(path not in verified or verified[path] == item['sha256'], 'Conflicting audited hash')
        run.check_pin(item)
        verified[path] = item['sha256']
    metadata = {p.resolve() for p in (OUT / 'run').rglob('*.json')}
    need(metadata == {run.absolute(item['path']) for item in chain['productionMetadataPins']},
         'Production metadata inventory changed after audit')
    rows, seen = [], set()
    for item in chain['archiveInputs'] + [run.pin(CHAIN), run.pin(FINAL)]:
        path = run.absolute(item['path'])
        need(path.is_relative_to(ROOT) and path not in seen, 'External/duplicate archive input')
        seen.add(path)
        relative = path.relative_to(ROOT).as_posix()
        need(path.is_file() and not relative.startswith('replays/')
             and not any(part.lower() in ('private', 'sessions', 'session') for part in path.parts),
             'Missing/private/raw archive input')
        need(path.suffix in ('.py', '.md', '.json', '.npz'), 'Raw corpus/runtime binary or unexpected archive type')
        if path not in (CHAIN, FINAL):
            need(verified.get(path) == item['sha256'], 'Archive input lacks audited byte binding')
        run.check_pin(item)
        rows.append({**item, 'bytes': path.stat().st_size})
    priors = []
    for name in ('indrill-capacity-v1', 'indrill-fit-one-v1', 'indrill-build64-v1',
                 'indrill-build64-long-v1', 'indrill-build64-12k-v1', 'indrill-joint64-12k-v1'):
        index_path = ROOT / f'docs/evidence/{name}-artifacts.json'
        prior, zip_path = run.read(index_path), ROOT / f'docs/evidence/{name}-artifacts.zip'
        need(prior['complete'] is True and prior['experiment'] == name and run.absolute(prior['archive']) == zip_path
             and prior['bytes'] == zip_path.stat().st_size, 'Prior archive/index differs')
        need(run.digest(zip_path) == prior['sha256'], 'Prior archive hash differs')
        priors.append({'archive': run.pin(zip_path), 'index': run.pin(index_path)})
    rows.sort(key=lambda item: item['path'])
    with zipfile.ZipFile(ARCHIVE, 'x', zipfile.ZIP_DEFLATED, compresslevel=6) as zipped:
        for item in rows:
            content = run.absolute(item['path']).read_bytes()
            need(len(content) == item['bytes'] and hashlib.sha256(content).hexdigest() == item['sha256'],
                 'Snapshot differs from audited bytes')
            zipped.writestr(item['path'], content)
    with zipfile.ZipFile(ARCHIVE) as zipped:
        need(zipped.testzip() is None and len(zipped.namelist()) == len(rows)
             and set(zipped.namelist()) == {item['path'] for item in rows}, 'ZIP inventory/integrity differs')
        for item in rows:
            content = zipped.read(item['path'])
            need(len(content) == item['bytes'] and hashlib.sha256(content).hexdigest() == item['sha256'], 'Archived bytes differ')
    index = {'schemaVersion': 1, 'experiment': 'indrill-generalization-v1', 'complete': True,
        'finalEvidence': run.pin(FINAL), 'checkpointChain': run.pin(CHAIN),
        'archive': ARCHIVE.relative_to(ROOT).as_posix(), 'sha256': run.digest(ARCHIVE), 'bytes': ARCHIVE.stat().st_size,
        'files': rows, 'priorArchives': priors, 'auditedInputPinsVerified': len(verified),
        'productionMetadataFilesVerified': len(metadata),
        'scope': 'Frozen source/protocol/proofs, preparation/startup/update/evaluation metadata, full saved metrics and holdout reports, every checkpoint hash and four scheduled NPZ snapshots.',
        'retainedOutsideArchive': ['Other 11997 full NPZ states remain locally retained and individually hash-pinned.',
            'Original 467 MB dataset, raw replays and runtime binaries remain locally retained and pinned through the input freezes.'],
        'newGames': 0, 'newModelEvaluations': 0, 'newGradients': 0, 'promotion': False}
    run.write_once(INDEX, index)
    print(json.dumps({'archive': index['archive'], 'sha256': index['sha256'], 'files': len(rows), 'bytes': index['bytes']}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('audit', 'archive'))
    args = parser.parse_args()
    if args.command == 'audit':
        audit()
    else:
        archive()


if __name__ == '__main__':
    main()
