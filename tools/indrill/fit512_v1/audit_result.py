"""Saved-only fit512 audit and verified solid-compression archive.

Only explicit audit/archive CLI commands perform work. No learned forward,
optimizer, gradient reconstruction, holdout scoring or games occur here.
"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import gzip
import io
import json
from pathlib import Path
import sys
import tarfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from tools.indrill.fit512_v1 import run_experiment as run
from tools.indrill.generalization_v1 import audit_result as prior_audit

np, need = run.np, run.require
# These routines only inspect their arguments; no old globals are reassigned.
finite, same = prior_audit.finite, prior_audit.same
verify_rows, summaries = prior_audit.verify_rows, prior_audit.summaries
METRIC_FIELDS = prior_audit.METRIC_FIELDS
OUT = ROOT / 'build/analysis/indrill-fit512-v1'
FINAL = ROOT / 'docs/evidence/indrill-fit512-v1.json'
CHAIN = OUT / 'chain-manifest.json'
INTENT = OUT / 'completed-audit-intent.json'
FAILURE = OUT / 'completed-audit-failure.json'
ARCHIVE = FINAL.with_name('indrill-fit512-v1-artifacts.tar.gz')
INDEX = FINAL.with_name('indrill-fit512-v1-artifacts.json')
METRIC_STEPS = [0, 3000, 12000, 24000, 48000]
OBSERVATION_IDS_SHA256 = '6cc2f87084eb20b577e41f09bc73baea3dd18df6a5263cf7122c113f9f6b8d36'
PINS = {}


def remember(path):
    value = run.pin(path)
    previous = PINS.setdefault(value['path'], value['sha256'])
    need(previous == value['sha256'], 'Evidence changed during audit: ' + value['path'])
    return value


def checked(item):
    value = remember(item['path'])
    need(value['sha256'] == item['sha256'], 'Pinned bytes differ: ' + item['path'])
    return value


def saved(path):
    remember(path)
    return run.read(path)


def audit_chain(folder, binding):
    """Reproduce initialization, sampler and Adam arithmetic from saved moments."""
    points = sorted((folder / 'checkpoints').glob('step-*.npz'), key=lambda p: int(p.stem.split('-')[1]))
    expected_names = {f'step-{step:05d}{suffix}' for step in range(48001) for suffix in ('.npz', '.json')}
    need([int(p.stem.split('-')[1]) for p in points] == list(range(48001))
         and {p.name for p in (folder / 'checkpoints').iterdir()} == expected_names,
         'Checkpoint inventory is not exactly numeric 0..48000')
    expected_intents = {f'update-{step:05d}-intent.json' for step in range(1, 48001)}
    need({p.name for p in folder.glob('update-*-intent.json')} == expected_intents, 'Update intent inventory differs')
    initial = run.initialize()  # Seeded initializer only; never forward or optimizer.
    shapes = {name: value.shape for name, value in initial.p.items()}
    tensor_keys = {'metadata', 'sampleOrder'} | {prefix + name for name in shapes for prefix in ('', 'adam_m_', 'adam_v_')}
    rng, order, position = np.random.default_rng(2026100412), np.empty(0, np.int64), 0
    exposure, schedule = np.zeros(512, np.int64), hashlib.sha256()
    previous_parameters, previous_checkpoint, previous_receipt = None, None, None
    entries, clipped, max_difference = [], 0, 0.
    for step, path in enumerate(points):
        need(path == folder / f'checkpoints/step-{step:05d}.npz', 'Noncanonical checkpoint name')
        chosen = np.empty(0, np.int64)
        if step:
            if position == len(order):
                order, position = rng.permutation(512), 0
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
    need(int(exposure.sum()) == 1536000 and np.array_equal(exposure, np.full(512, 3000))
         and position == 512, 'Fixed final sampling exposure differs')
    return entries, {'checkpointsVerified': 48001, 'updates': 48000, 'sampleVisits': 1536000,
        'visitsPerObservation': exposure.tolist(), 'exposureCounts': {'3000': 512},
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


def construction_subgroups(metric):
    """Expose multi-cell behaviour without letting empty/single cells hide it."""
    result = {}
    for label, predicate in (('multiCell', lambda count: count >= 2), ('threeCell', lambda count: count == 3)):
        rows = [row for row in metric['rows'] if predicate(row['teacherCardinality'])]
        counts = {key: sum(row[key] for row in rows) for key in ('buildExact', 'inkExact', 'jointExact')}
        result[label] = {**prior_audit.aggregate(rows), 'exactCounts': counts,
                         'rowExactRates': {key: value / len(rows) if rows else None for key, value in counts.items()}}
    return result


def verify_selection(data, selection):
    b_indices, a_indices = data.panel_indices('B'), data.panel_indices('A')
    groups = Counter(data.rows[i]['mapHash'] for i in b_indices)
    need(len(b_indices) == 2048 and len(groups) == 128 and set(groups.values()) == {16}, 'Original B groups differ')
    ranked = sorted(groups, key=lambda value: hashlib.sha256(('indrill-capacity-v1/train/' + value).encode('utf-8')).hexdigest())[:32]
    positions = [position for position, index in enumerate(b_indices) if data.rows[index]['mapHash'] in set(ranked)]
    indices = [b_indices[position] for position in positions]
    ids = [data.rows[i]['observationId'] for i in indices]
    ids_hash = hashlib.sha256(('\n'.join(ids) + '\n').encode('utf-8')).hexdigest()
    expected = {'schema': 'indrill-fit512-selection-v1', 'sourcePanel': 'B', 'mapRankPrefix': 'indrill-capacity-v1/train/',
        'rankedMapHashes': ranked, 'fullDatasetIndices': indices, 'originalBPositions': positions,
        'observationIds': ids, 'observationIdsSha256': ids_hash, 'observations': 512, 'maps': 32,
        'rowsPerMap': 16, 'allOriginalAIncluded': True, 'order': 'original-export'}
    need(selection == expected and len(indices) == len(set(ids)) == 512 and set(a_indices).issubset(indices)
         and ids_hash == OBSERVATION_IDS_SHA256, 'Frozen intermediate selection differs')
    held = data.panel_indices('holdout')
    need(len(held) == 1020 and len({data.rows[i]['mapHash'] for i in held}) == 51
         and not set(ranked) & {data.rows[i]['mapHash'] for i in held}
         and not {data.rows[i]['inputSha256'] for i in indices} & {data.rows[i]['inputSha256'] for i in held}, 'Holdout split differs')
    return indices, held


def paired_description(candidate_rows, reference_rows):
    """Descriptive saved-row comparisons only, with no confidence test or gate."""
    need(len(candidate_rows) == len(reference_rows), 'Paired descriptive size differs')
    for first, second in zip(candidate_rows, reference_rows):
        need(all(first[key] == second[key] for key in ('observationId', 'inputSha256', 'mapHash', 'targetCells', 'targetInk')),
             'Saved descriptive row identity differs')
    first_mean, second_mean = prior_audit.aggregate(candidate_rows), prior_audit.aggregate(reference_rows)
    counts = {}
    for field in ('buildExact', 'inkExact', 'jointExact'):
        counts[field] = {name: sum((bool(first[field]), bool(second[field])) == flags
                                   for first, second in zip(candidate_rows, reference_rows))
                         for name, flags in (('gained', (True, False)), ('lost', (False, True)),
                                             ('retainedExact', (True, True)), ('bothInexact', (False, False)))}
    return {'observations': len(candidate_rows), 'maps': first_mean['maps'], 'pairedCounts': counts,
            'candidateExactCounts': {key: sum(row[key] for row in candidate_rows) for key in ('buildExact', 'inkExact', 'jointExact')},
            'referenceExactCounts': {key: sum(row[key] for row in reference_rows) for key in ('buildExact', 'inkExact', 'jointExact')},
            'candidateMeanOfMapMeans': first_mean['meanOfMapMeans'], 'referenceMeanOfMapMeans': second_mean['meanOfMapMeans'],
            'candidateMinusReferenceMapMean': {key: first_mean['meanOfMapMeans'][key] - second_mean['meanOfMapMeans'][key]
                                              for key in METRIC_FIELDS}, 'descriptiveOnly': True}


def audit():
    need(not any(path.exists() for path in (INTENT, FAILURE, CHAIN, FINAL)), 'Preserve previous audit attempts/outputs')
    folder = OUT / 'run'
    result = saved(folder / 'result.json')
    need(result['complete'] is True and result['updates'] == 48000 and result['sampleVisits'] == 1536000,
         'Complete fixed run and final candidate holdout required')
    freeze, data = run.verify_freeze(OUT / 'freeze.json')
    freeze_pin = remember(OUT / 'freeze.json')
    need(run.SETTINGS == {'modelSeed': 2026100402, 'samplingSeed': 2026100412, 'updates': 48000,
                         'batch': 32, 'observations': 512, 'metricsAt': METRIC_STEPS}
         and run.FIT_THRESHOLD == freeze['fitThreshold'] == 359, 'Fixed budget/threshold differs')
    run.write_once(INTENT, {'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'helper': run.pin(__file__),
        'pureMetricHelper': run.pin(prior_audit.__file__), 'freeze': freeze_pin, 'completedResult': run.pin(folder / 'result.json'),
        'newLearnedPredictions': 0, 'newGradients': 0, 'newGames': 0})
    try:
        for item in freeze['pins']:
            checked(item)
        for path in (__file__, prior_audit.__file__, INTENT):
            remember(path)
        selection = saved(freeze['selection']['path'])
        indices, held = verify_selection(data, selection)
        train_rows, held_rows = [data.rows[i] for i in indices], [data.rows[i] for i in held]
        train_collision = run.teacher.collision_summary(data.rows, indices)
        held_collision = run.teacher.collision_summary(data.rows, held)
        need(train_collision == freeze['datasetSummary']['trainingCollisionSummary']
             and train_collision['exactJointCeilingCount'] >= 359, 'Frozen collision ceiling/binding differs')
        prepare = saved(OUT / 'prepare-intent.json')
        need(prepare['settings'] == run.SETTINGS and prepare['prerequisite'] == freeze['prerequisite']
             and prepare['fitThreshold'] == 359 and prepare['gradientsRun'] == prepare['learnedPredictions'] == 0,
             'Preparation binding differs')
        datetime.fromisoformat(prepare['createdAtUtc'])
        need(not (OUT / 'preparation-result.json').exists(), 'Preparation stopped before training')
        binding, startup = run.make_binding(OUT / 'freeze.json', freeze), saved(folder / 'intent.json')
        need(set(startup) == {'createdAtUtc', 'binding', 'environment', 'freshInitialization',
                             'prerequisiteWeightsInherited', 'candidateOnlyFinalHoldout'}
             and startup['binding'] == binding and startup['environment'] == run.environment()
             and startup['freshInitialization'] is True and startup['prerequisiteWeightsInherited'] is False
             and startup['candidateOnlyFinalHoldout'] is True, 'Startup recipe/runtime differs')
        datetime.fromisoformat(startup['createdAtUtc'])
        expected = {'intent.json', 'result.json', 'checkpoints'}
        expected |= {f'update-{step:05d}-intent.json' for step in range(1, 48001)}
        expected |= {f'train-metrics-{step:05d}{suffix}' for step in METRIC_STEPS for suffix in ('.json', '-intent.json', '-receipt.json')}
        expected |= {'holdout-candidate' + suffix for suffix in ('.json', '-intent.json', '-receipt.json')}
        need({path.name for path in folder.iterdir()} == expected, 'Unexpected/missing production file, Q scoring, or failure')
        entries, integrity = audit_chain(folder, binding)
        curve = []
        for step in METRIC_STEPS:
            target = {'kind': 'training', 'step': step, 'checkpoint': entries[step]['checkpoint']}
            metric, pins = evaluation_triplet(run.metric_paths(folder, step), binding, target)
            need(metric['stage'] == 'B' and metric['updates'] == step and metric['checkpoint'] == target['checkpoint'],
                 'Training metric boundary differs')
            curve.append({'step': step, **pins, **verify_rows(metric, train_rows, train_collision),
                          'constructionSubgroups': construction_subgroups(metric)})
        final_metric = saved(curve[-1]['metrics']['path'])
        target = {'kind': 'holdout-candidate', 'step': 48000, 'checkpoint': entries[-1]['checkpoint']}
        candidate, candidate_pins = evaluation_triplet(run.holdout_paths(folder, 'candidate'), binding, target)
        need(candidate['stage'] == 'B' and candidate['updates'] == 48000 and candidate['checkpoint'] == target['checkpoint'],
             'Final candidate holdout boundary differs')
        held_summary = verify_rows(candidate, held_rows, held_collision)
        decision = 'FIT70_ESTABLISHED' if curve[-1]['jointExactCount'] >= 359 else 'FIT70_NOT_ESTABLISHED'
        expected_result = {'schema': 'indrill-fit512-run-result-v1', 'complete': True, 'freeze': freeze_pin,
            'selection': freeze['selection'], 'updates': 48000, 'sampleVisits': 1536000, 'fitThreshold': 359,
            'checkpoint': entries[-1]['checkpoint'], 'finalMetrics': curve[-1]['metrics'],
            'finalTrainingJointExact': curve[-1]['jointExactCount'], 'holdoutCandidate': candidate_pins['metrics'],
            'decision': decision, 'freshInitialization': True, 'holdoutAffectsFitDecision': False,
            'candidateOnlyFinalHoldout': True, 'newGames': 0, 'promotion': False}
        same(result, expected_result, 'run/result')
        prior = freeze['prerequisite']
        parent = saved(prior['evidence']['path'])
        need(parent['experiment'] == 'indrill-generalization-v1' and parent['complete'] is True and parent['auditStatus'] == 'PASS'
             and parent['curve'][-1]['metrics'] == prior['finalTrainingMetrics']
             and parent['holdoutCandidate']['metrics'] == prior['finalHoldoutMetrics']
             and prior['weightsInherited'] is False, 'Saved predecessor comparison differs')
        old_train, old_held = saved(prior['finalTrainingMetrics']['path']), saved(prior['finalHoldoutMetrics']['path'])
        b_indices = data.panel_indices('B')
        verify_rows(old_train, [data.rows[i] for i in b_indices], run.teacher.collision_summary(data.rows, b_indices))
        verify_rows(old_held, held_rows, held_collision)
        old_selected = [old_train['rows'][position] for position in selection['originalBPositions']]
        comparisons = {'trainingSubsetAgainstB2048': paired_description(final_metric['rows'], old_selected),
                       'holdoutAgainstB2048': paired_description(candidate['rows'], old_held['rows']),
                       'referenceTrainingMetrics': prior['finalTrainingMetrics'], 'referenceHoldoutMetrics': prior['finalHoldoutMetrics'],
                       'referenceTrainingSubsetConstruction': construction_subgroups({'rows': old_selected})}
        gap = {key: held_summary['meanOfMapMeans'][key] - curve[-1]['meanOfMapMeans'][key] for key in METRIC_FIELDS}
        registry = saved(prior['mapExclusionRegistry']['path'])
        need(registry['distinctMaps'] == 293 and ({row['mapHash'] for row in train_rows} | {row['mapHash'] for row in held_rows})
             <= set(registry['gamesPerMap']), 'Used map missing from retained exclusions')
        production_metadata = [remember(path) for path in sorted(folder.rglob('*.json'))]
        archive_paths = {Path(__file__).resolve(), Path(prior_audit.__file__).resolve(), OUT / 'freeze.json',
                         OUT / 'prepare-intent.json', OUT / 'selection.json', INTENT, run.PROTOCOL,
                         run.absolute(freeze['checks']['path']), run.absolute(prior['mapExclusionRegistry']['path'])}
        archive_paths.update(run.absolute(item['path']) for item in production_metadata)
        archive_paths.update(folder / f'checkpoints/step-{step:05d}.npz' for step in METRIC_STEPS)
        archive_paths.update(run.absolute(item['path']) for item in freeze['pins']
                             if run.absolute(item['path']).is_relative_to(ROOT) and run.absolute(item['path']).suffix in ('.py', '.md'))
        readme = Path(__file__).with_name('README.md')
        if readme.exists():
            archive_paths.add(readme)
        archive_inputs = [remember(path) for path in sorted(archive_paths)]
        need(run.environment() == freeze['environment'], 'Runtime changed during audit')
        for path, digest in list(PINS.items()):
            need(run.digest(path) == digest, 'Audited bytes changed before seal: ' + path)
        run.write_once(CHAIN, {'schema': 'indrill-fit512-completed-chain-v1', 'complete': True, 'auditStatus': 'PASS',
            'freeze': freeze_pin, 'selection': freeze['selection'], 'helper': run.pin(__file__),
            'pureMetricHelper': run.pin(prior_audit.__file__), 'chain': entries, 'integrity': integrity,
            'productionMetadataPins': production_metadata, 'archiveInputs': archive_inputs,
            'auditedPins': [{'path': path, 'sha256': digest} for path, digest in sorted(PINS.items())],
            'method': 'Independent ranked panel selection, fresh initialization, all saved states, exact sampler and Adam parameter arithmetic from saved moments, saved row/metric arithmetic; no learned forward, gradients or bootstrap.'})
        report = {'schemaVersion': 1, 'experiment': 'indrill-fit512-v1', 'complete': True, 'auditStatus': 'PASS',
            'decision': decision, 'fitThreshold': 359, 'freeze': freeze_pin, 'selection': freeze['selection'],
            'helper': run.pin(__file__), 'pureMetricHelper': run.pin(prior_audit.__file__), 'intent': run.pin(INTENT),
            'checkpointChain': run.pin(CHAIN), 'runnerResult': run.pin(folder / 'result.json'), 'settings': run.SETTINGS,
            'integrity': integrity, 'trainingObservations': 512, 'trainingMaps': 32, 'holdoutObservations': 1020, 'holdoutMaps': 51,
            'curve': curve, 'holdoutCandidate': {**candidate_pins, **held_summary, 'constructionSubgroups': construction_subgroups(candidate)},
            'holdoutMinusTrainingMapMean': gap, 'savedPredecessorComparisons': comparisons,
            'prerequisite': prior, 'syntheticChecks': freeze['checks'], 'freshInitialization': True,
            'mapExclusionRegistry': prior['mapExclusionRegistry'], 'retainedExactCoreGames': 293,
            'newGames': 0, 'auditNewLearnedPredictions': 0, 'auditNewGradients': 0, 'qV2Scored': False, 'bootstrapRun': False,
            'holdoutAffectsFitDecision': False, 'promotion': False, 'arenaUploaded': False,
            'limitations': ['Outcome-informed fixed-panel fitting experiment; 359/512 is an operational 70% fitting target, not a win-rate or statistical confidence claim.',
                'Per-example exposure matches joint64, while seed, batch, optimizer updates and data differ; sample size is not isolated as a causal variable.',
                'The unconditional final holdout is an already inspected research holdout. Its descriptive result cannot rescue the fitting decision or establish arena strength.',
                'Saved B2048 comparisons use its recorded predictions on the matching512 training subset and full holdout; training budgets and panels differ.',
                'Saved prediction/label bindings, NLL arithmetic and aggregates are checked without rederiving logits or learned outputs.',
                'Adam parameter arithmetic is checked from stored moments without reconstructing gradients; their origin is not independently established.',
                'A finite-budget failure cannot establish an architectural capacity ceiling. No further training or games follow automatically.']}
        run.write_once(FINAL, report)
        print(json.dumps({'evidence': run.pin(FINAL), 'decision': decision, 'finalTrainJoint': curve[-1]['jointExactCount'],
                          'finalHoldoutJoint': held_summary['jointExactCount']}))
    except Exception as error:
        run.write_once(FAILURE, {'auditStatus': 'FAILED', 'intent': run.pin(INTENT), 'helper': run.pin(__file__),
            'errorType': type(error).__name__, 'error': str(error), 'automaticRetry': False,
            'newLearnedPredictions': 0, 'newGradients': 0, 'newGames': 0})
        raise


def archive():
    """Package only completed audited inputs using solid gzip compression."""
    need(not ARCHIVE.exists() and not INDEX.exists(), 'Preserve existing archive/index')
    final, chain = run.read(FINAL), run.read(CHAIN)
    need(final['experiment'] == 'indrill-fit512-v1' and final['complete'] is True and final['auditStatus'] == 'PASS'
         and chain['complete'] is True and chain['auditStatus'] == 'PASS' and final['checkpointChain'] == run.pin(CHAIN)
         and final['freeze'] == chain['freeze'] and chain['helper'] == run.pin(__file__), 'Completed audit/source binding differs')
    verified = {}
    for item in chain['auditedPins']:
        path = run.absolute(item['path'])
        need(path not in verified or verified[path] == item['sha256'], 'Conflicting audited pin')
        need(run.digest(path) == item['sha256'], 'Audited input changed: ' + item['path'])
        verified[path] = item['sha256']
    metadata = {path.resolve() for path in (OUT / 'run').rglob('*.json')}
    need(metadata == {run.absolute(item['path']) for item in chain['productionMetadataPins']}, 'Production metadata inventory changed')
    rows, seen = [], set()
    for item in chain['archiveInputs'] + [run.pin(CHAIN), run.pin(FINAL)]:
        path = run.absolute(item['path'])
        need(path.is_relative_to(ROOT) and path not in seen, 'External/duplicate archive input')
        seen.add(path)
        relative = path.relative_to(ROOT).as_posix()
        need(path.is_file() and not path.is_symlink() and not relative.startswith('replays/')
             and not any(part.lower() in ('private', 'sessions', 'session') for part in path.parts), 'Missing/private/raw archive input')
        need(path.suffix in ('.py', '.md', '.json', '.npz'), 'Raw corpus/runtime binary or unexpected archive type')
        if path not in (CHAIN, FINAL):
            need(verified.get(path) == item['sha256'], 'Archive input has no completed byte audit')
        need(run.digest(path) == item['sha256'], 'Archive snapshot pin changed')
        rows.append({**item, 'bytes': path.stat().st_size})
    need({item['path'] for item in rows if item['path'].endswith('.npz')} ==
         {f'build/analysis/indrill-fit512-v1/run/checkpoints/step-{step:05d}.npz' for step in METRIC_STEPS},
         'Archive must retain exactly five scheduled full states')
    priors = []
    for name in ('indrill-capacity-v1', 'indrill-fit-one-v1', 'indrill-build64-v1', 'indrill-build64-long-v1',
                 'indrill-build64-12k-v1', 'indrill-joint64-12k-v1', 'indrill-generalization-v1'):
        index_path = ROOT / f'docs/evidence/{name}-artifacts.json'
        previous = run.read(index_path)
        path = run.absolute(previous['archive'])
        need(previous['complete'] is True and previous['experiment'] == name and path.is_relative_to(ROOT)
             and path.stat().st_size == previous['bytes'] and run.digest(path) == previous['sha256'], 'Prior archive/index differs')
        priors.append({'archive': run.pin(path), 'index': run.pin(index_path)})
    rows.sort(key=lambda item: item['path'])
    with ARCHIVE.open('xb') as raw:
        with gzip.GzipFile(filename='', mode='wb', fileobj=raw, compresslevel=6, mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode='w|', format=tarfile.PAX_FORMAT) as packed:
                for item in rows:
                    content = run.absolute(item['path']).read_bytes()
                    need(len(content) == item['bytes'] and hashlib.sha256(content).hexdigest() == item['sha256'],
                         'Snapshot bytes differ from completed audit')
                    info = tarfile.TarInfo(item['path'])
                    info.size, info.mtime, info.mode = len(content), 0, 0o644
                    packed.addfile(info, io.BytesIO(content))
    expected, observed = {item['path']: item for item in rows}, set()
    with tarfile.open(ARCHIVE, 'r|gz') as packed:
        for member in packed:
            need(member.isfile() and member.name in expected and member.name not in observed, 'Unexpected/repeated/non-file tar member')
            item = expected[member.name]
            stream = packed.extractfile(member)
            need(stream is not None and member.size == item['bytes'], 'Archived member size differs')
            with stream:
                content = stream.read()
            need(len(content) == item['bytes'] and hashlib.sha256(content).hexdigest() == item['sha256'], 'Archived member hash differs')
            observed.add(member.name)
    need(observed == set(expected), 'Archive inventory is incomplete')
    # Read gzip to its trailer independently so CRC/length is also verified even
    # if tar parsing stopped at end-of-archive padding before the gzip trailer.
    with gzip.open(ARCHIVE, 'rb') as compressed:
        while compressed.read(1 << 20):
            pass
    index = {'schemaVersion': 1, 'experiment': 'indrill-fit512-v1', 'complete': True,
        'format': 'tar.gz', 'finalEvidence': run.pin(FINAL), 'checkpointChain': run.pin(CHAIN),
        'archive': ARCHIVE.relative_to(ROOT).as_posix(), 'sha256': run.digest(ARCHIVE), 'bytes': ARCHIVE.stat().st_size,
        'files': rows, 'priorArchives': priors, 'auditedInputPinsVerified': len(verified),
        'productionMetadataFilesVerified': len(metadata), 'allTarEntryBytesVerified': True,
        'scope': 'Frozen sources/protocol/proof/selection, all production metadata and saved metrics, full checkpoint hash chain and five scheduled full NPZ states.',
        'retainedOutsideArchive': ['Other 47996 full NPZ states remain locally retained and individually hash-pinned.',
            'Original corpus, raw replays and runtime binaries remain locally retained and pinned through the input freezes.'],
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
