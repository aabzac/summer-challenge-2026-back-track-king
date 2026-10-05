"""Fixed fresh FIT512 experiment; immutable earlier math and persistence helpers.

CLI performs work only with explicit prepare/run commands. Imports never read a
corpus, restore learned weights or score a model. No old module globals change.
Metric stage B records the original input panel; the new experiment is identified
by its own freeze/checkpoint/result schemas and exact 512-row selection binding.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(ROOT))
from tools.indrill.generalization_v1 import run_experiment as base

np, obj, teacher, previous = base.np, base.obj, base.teacher, base.previous
require, absolute, digest, pin, check_pin = base.require, base.absolute, base.digest, base.pin, base.check_pin
read, write_once, utc = base.read, base.write_once, base.utc
environment, runtime_files = base.environment, base.runtime_files
checkpoint_path, update_intent_path = base.checkpoint_path, base.update_intent_path
checkpoint, restore, commit_checkpoint = base.checkpoint, base.restore, base.commit_checkpoint
sampler, schedule_entry, hash_entry, verify_chain = base.sampler, base.schedule_entry, base.hash_entry, base.verify_chain
metric_paths, validate_metrics = base.metric_paths, base.validate_metrics
evaluation_once, validate_metric_payload, holdout_paths = base.evaluation_once, base.validate_metric_payload, base.holdout_paths
OUTPUT = ROOT / 'build/analysis/indrill-fit512-v1'
PROTOCOL = ROOT / 'docs/indrill-fit512-v1.md'
PARENT_FREEZE = base.OUTPUT / 'freeze.json'
PARENT_EVIDENCE = ROOT / 'docs/evidence/indrill-generalization-v1.json'
PARENT_ARCHIVE = ROOT / 'docs/evidence/indrill-generalization-v1-artifacts.json'
EXPECTED_IDS = '6cc2f87084eb20b577e41f09bc73baea3dd18df6a5263cf7122c113f9f6b8d36'
SETTINGS = {'modelSeed': 2026100402, 'samplingSeed': 2026100412, 'updates': 48000,
            'batch': 32, 'observations': 512, 'metricsAt': [0, 3000, 12000, 24000, 48000]}
LEARNING_RATE, CLIP_NORM, FIT_THRESHOLD = .0003, 10., 359


def source_files():
    return base.source_files() | {Path(base.__file__).resolve(), HERE / 'run_experiment.py', HERE / 'synthetic_checks.py'}


def initialize():
    return obj.fresh_model(SETTINGS['modelSeed'])


def select_panel(rows, b_indices, a_indices, *, map_count=32, rows_per_map=16,
                 total_maps=128, expected_digest=EXPECTED_IDS):
    """Rank maps first, retain selected B rows in the original export order."""
    require(len(set(b_indices)) == len(b_indices) and b_indices == sorted(b_indices), 'B indices repeated/out of export order')
    groups = Counter(rows[i]['mapHash'] for i in b_indices)
    require(len(groups) == total_maps and set(groups.values()) == {rows_per_map}, 'B map groups/cardinalities differ')
    rank = lambda value: hashlib.sha256(('indrill-capacity-v1/train/' + value).encode('utf-8')).hexdigest()
    ranked = sorted(groups, key=rank)
    require(0 < map_count <= len(ranked), 'Selected map count is invalid')
    chosen_maps = set(ranked[:map_count])
    positions = [i for i, index in enumerate(b_indices) if rows[index]['mapHash'] in chosen_maps]
    indices = [b_indices[i] for i in positions]
    ids = [rows[i]['observationId'] for i in indices]
    identity = hashlib.sha256(('\n'.join(ids) + '\n').encode('utf-8')).hexdigest()
    require(len(indices) == map_count * rows_per_map and len(set(ids)) == len(ids)
            and set(a_indices).issubset(indices), 'Selected panel cardinality/identity/A inclusion differs')
    require(identity == expected_digest, 'Selected observation ID digest differs')
    return {'schema': 'indrill-fit512-selection-v1', 'sourcePanel': 'B',
            'mapRankPrefix': 'indrill-capacity-v1/train/', 'rankedMapHashes': ranked[:map_count],
            'fullDatasetIndices': indices, 'originalBPositions': positions, 'observationIds': ids,
            'observationIdsSha256': identity, 'observations': len(indices), 'maps': map_count,
            'rowsPerMap': rows_per_map, 'allOriginalAIncluded': True, 'order': 'original-export'}


def inspect_parent():
    freeze, data = base.verify_freeze(PARENT_FREEZE)
    evidence, archive = read(PARENT_EVIDENCE), read(PARENT_ARCHIVE)
    require(evidence['experiment'] == 'indrill-generalization-v1' and evidence['complete'] is True
            and evidence['auditStatus'] == 'PASS' and evidence['freeze'] == pin(PARENT_FREEZE)
            and evidence['settings'] == base.SETTINGS and evidence['integrity']['adamStepsBitExact'] is True,
            'Completed audited predecessor required')
    for item in base.referenced_pins(evidence):
        check_pin(item)
    result = read(evidence['runnerResult']['path'])
    require(result['complete'] is True and result['updates'] == 12000 and result['sampleVisits'] == 384000
            and result['freeze'] == evidence['freeze'] and result['decision'] == evidence['decision'], 'Predecessor run differs')
    require(archive['complete'] is True and archive['experiment'] == evidence['experiment']
            and archive['finalEvidence'] == pin(PARENT_EVIDENCE) and archive['checkpointChain'] == evidence['checkpointChain'],
            'Predecessor archive differs')
    archive_pin = {'path': archive['archive'], 'sha256': archive['sha256']}
    check_pin(archive_pin)
    references = {'freeze': pin(PARENT_FREEZE), 'evidence': pin(PARENT_EVIDENCE), 'archiveIndex': pin(PARENT_ARCHIVE),
                  'archive': archive_pin, 'chain': evidence['checkpointChain'], 'runResult': evidence['runnerResult'],
                  'finalTrainingMetrics': result['finalMetrics'], 'finalCheckpoint': result['checkpoint'],
                  'finalHoldoutMetrics': evidence['holdoutCandidate']['metrics'],
                  'mapExclusionRegistry': evidence['mapExclusionRegistry'], 'weightsInherited': False}
    for item in base.referenced_pins(references):
        check_pin(item)
    return freeze, data, references


def selection_summary(data, selection):
    indices, held = selection['fullDatasetIndices'], data.panel_indices('holdout')
    training_maps = {data.rows[i]['mapHash'] for i in indices}
    held_maps = {data.rows[i]['mapHash'] for i in held}
    training_inputs = {data.rows[i]['inputSha256'] for i in indices}
    held_inputs = {data.rows[i]['inputSha256'] for i in held}
    require(len(indices) == 512 and len(training_maps) == 32 and len(held) == 1020 and len(held_maps) == 51
            and not training_maps & held_maps and not training_inputs & held_inputs, 'Panel/holdout count or disjointness differs')
    return {'trainingRows': 512, 'trainingMaps': 32, 'holdoutRows': 1020, 'holdoutMaps': 51,
            'mapOverlap': 0, 'inputOverlap': 0, 'trainingCollisionSummary': teacher.collision_summary(data.rows, indices)}


def check_proof(path):
    proof = read(path)
    require(proof['schema'] == 'indrill-fit512-checks-v1' and proof['passed'] is True and proof['syntheticOnly'] is True
            and proof['realRowsRead'] == proof['realCandidateFitRuns'] == 0 and proof['productionSettingsUnchanged'] is True
            and proof['oldModuleGlobalsUnchanged'] is True and proof['environment'] == environment(), 'Passing synthetic proof required')
    require(set(proof['sourcePins']) == {pin(p)['path'] for p in source_files()}, 'Synthetic source inventory differs')
    for path, sha in proof['sourcePins'].items():
        check_pin({'path': path, 'sha256': sha})
    require({absolute(p['path']) for p in proof['runtimePins']} == runtime_files(), 'Runtime proof inventory differs')
    for item in proof['runtimePins']:
        check_pin(item)
    return proof


def prepare(checks_path):
    require(not any((OUTPUT / n).exists() for n in ('prepare-intent.json', 'selection.json', 'freeze.json', 'run', 'preparation-result.json')),
            'Refusing existing preparation/run')
    check_proof(checks_path)
    parent, data, prior = inspect_parent()
    selection = select_panel(data.rows, data.panel_indices('B'), data.panel_indices('A'))
    summary = selection_summary(data, selection)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write_once(OUTPUT / 'prepare-intent.json', {'createdAtUtc': utc(), 'settings': SETTINGS, 'prerequisite': prior,
               'gradientsRun': 0, 'learnedPredictions': 0, 'fitThreshold': FIT_THRESHOLD})
    write_once(OUTPUT / 'selection.json', selection)
    if summary['trainingCollisionSummary']['exactJointCeilingCount'] < FIT_THRESHOLD:
        write_once(OUTPUT / 'preparation-result.json', {'complete': True, 'decision': 'INPUT_ALIASING_LIMIT',
                   'selection': pin(OUTPUT / 'selection.json'), 'datasetSummary': summary,
                   'fitThreshold': FIT_THRESHOLD, 'gradientsRun': 0, 'learnedPredictions': 0})
        print(json.dumps({'decision': 'INPUT_ALIASING_LIMIT', 'gradientsRun': 0}))
        return
    paths = source_files() | runtime_files() | {PROTOCOL, absolute(checks_path), OUTPUT / 'prepare-intent.json', OUTPUT / 'selection.json'}
    pins = {pin(p)['path']: pin(p) for p in paths}
    for item in list(parent['pins']) + list(base.referenced_pins(prior)):
        check_pin(item)
        pins[item['path']] = item
    freeze = {'schema': 'indrill-fit512-freeze-v1', 'status': 'FROZEN', 'checksPassed': True,
              'settings': SETTINGS, 'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM, 'betas': [.9, .999],
              'epsilon': 1e-8, 'fitThreshold': FIT_THRESHOLD, 'environment': environment(),
              'outputDirectory': OUTPUT.relative_to(ROOT).as_posix(), 'datasetDirectory': parent['datasetDirectory'],
              'selection': pin(OUTPUT / 'selection.json'), 'datasetSummary': summary, 'prerequisite': prior,
              'checks': pin(checks_path), 'pins': [pins[k] for k in sorted(pins)],
              'freshInitialization': True, 'candidateOnlyFinalHoldout': True, 'holdoutAffectsFitDecision': False,
              'gradientsRun': 0, 'learnedPredictions': 0, 'newGames': 0}
    # Catch source/proof drift during the data checks before committing a freeze.
    check_proof(checks_path)
    for item in freeze['pins']:
        check_pin(item)
    write_once(OUTPUT / 'freeze.json', freeze)
    print(json.dumps({'freeze': pin(OUTPUT / 'freeze.json'), 'selection': freeze['selection'], 'gradientsRun': 0}))


def verify_frozen_bytes(path, freeze, expected_pin=None):
    require(read(path) == freeze and (expected_pin is None or pin(path) == expected_pin), 'Freeze changed during execution')
    require(freeze['schema'] == 'indrill-fit512-freeze-v1' and freeze['status'] == 'FROZEN' and freeze['checksPassed'] is True
            and freeze['settings'] == SETTINGS and freeze['learningRate'] == LEARNING_RATE and freeze['clipNorm'] == CLIP_NORM
            and freeze['betas'] == [.9, .999] and freeze['epsilon'] == 1e-8 and freeze['fitThreshold'] == FIT_THRESHOLD
            and freeze['freshInitialization'] is True and freeze['candidateOnlyFinalHoldout'] is True
            and freeze['holdoutAffectsFitDecision'] is False, 'Fixed experiment recipe differs')
    require(absolute(freeze['outputDirectory']) == OUTPUT and freeze['environment'] == environment(), 'Output/runtime differs')
    required = source_files() | runtime_files() | {PROTOCOL, absolute(freeze['checks']['path']), OUTPUT / 'prepare-intent.json', OUTPUT / 'selection.json'}
    required |= {absolute(p['path']) for p in base.referenced_pins(freeze['prerequisite'])}
    required |= {absolute(freeze['datasetDirectory']) / n for n in ('teacher.jsonl', 'teacher-preflight.json', 'dataset-manifest.json')}
    # The whole prior freeze inventory remains directly pinned at both boundaries.
    parent = read(freeze['prerequisite']['freeze']['path'])
    required |= {absolute(p['path']) for p in parent['pins']}
    observed = [absolute(p['path']) for p in freeze['pins']]
    require(len(observed) == len(set(observed)) and required.issubset(observed), 'Missing/repeated source/data/runtime pins')
    for item in freeze['pins']:
        check_pin(item)
    check_pin(freeze['selection']); check_proof(freeze['checks']['path'])


def verify_freeze(path):
    freeze = read(path)
    verify_frozen_bytes(path, freeze)
    parent, data, prior = inspect_parent()
    require(prior == freeze['prerequisite'] and parent['datasetDirectory'] == freeze['datasetDirectory'], 'Predecessor binding differs')
    selection = select_panel(data.rows, data.panel_indices('B'), data.panel_indices('A'))
    require(read(freeze['selection']['path']) == selection and selection_summary(data, selection) == freeze['datasetSummary'], 'Frozen panel selection differs')
    require(freeze['datasetSummary']['trainingCollisionSummary']['exactJointCeilingCount'] >= FIT_THRESHOLD, 'INPUT_ALIASING_LIMIT')
    return freeze, data


def make_binding(freeze_path, freeze):
    return {'schema': 'indrill-fit512-checkpoint-v1', 'freeze': pin(freeze_path), 'selection': freeze['selection'],
            'settings': freeze['settings'], 'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM,
            'fitThreshold': freeze['fitThreshold'], 'freshInitialization': True}


def fit_decision(step, joint_count, config, threshold):
    require(step == config['updates'], 'Fit decision requires final fixed budget')
    require(type(joint_count) is int and 0 <= joint_count <= config['observations'], 'Invalid fitting count')
    return 'FIT70_ESTABLISHED' if joint_count >= threshold else 'FIT70_NOT_ESTABLISHED'


def final_holdout(folder, model, data, binding, config, evaluate=previous.evaluate):
    require(model.updates == config['updates'], 'Holdout requires final fixed update budget')
    held = data.panel_indices('holdout')
    point = pin(checkpoint_path(folder, model.updates))
    target = {'kind': 'holdout-candidate', 'step': model.updates, 'checkpoint': point}
    payload = evaluation_once(holdout_paths(folder, 'candidate'), binding, target,
        lambda: {'stage': 'B', 'updates': model.updates, 'checkpoint': point, **evaluate(model, data, held)})
    return validate_metric_payload(payload, model.updates, point, [data.rows[i] for i in held])


def execute(folder, freeze_path, freeze, data, indices, *, resume=False, evaluate=previous.evaluate,
            commit=commit_checkpoint, boundary_check=None):
    """Explicit orchestration seam; production calls only after verify_freeze.

    Synthetic checks pass tiny data/config and callbacks here, without patching
    any module. The production CLI has no recipe/evaluator/checkpoint overrides.
    """
    folder, config = Path(folder), freeze['settings']
    binding = make_binding(freeze_path, freeze)
    require(len(indices) == config['observations'] and len(set(indices)) == len(indices), 'Training selection differs')
    if resume:
        require(folder.is_dir() and not (folder / 'result.json').exists(), 'Only an incomplete run can resume')
        require(read(folder / 'intent.json')['binding'] == binding and read(folder / 'intent.json')['environment'] == environment(), 'Run intent/runtime differs')
    else:
        folder.mkdir()
        write_once(folder / 'intent.json', {'createdAtUtc': utc(), 'binding': binding, 'environment': environment(),
                   'freshInitialization': True, 'prerequisiteWeightsInherited': False, 'candidateOnlyFinalHoldout': True})
        (folder / 'checkpoints').mkdir()
    model, rng = obj.fresh_model(config['modelSeed']), np.random.default_rng(config['samplingSeed'])
    order, position, rows = np.empty(0, np.int64), 0, [data.rows[i] for i in indices]
    if resume:
        order, position = verify_chain(folder, model, rng, binding, config)
        for step in config['metricsAt']:
            if step < model.updates:
                validate_metrics(folder, step, binding, rows)
        require(model.updates == config['updates'] or not any(folder.glob('holdout-*')), 'Premature holdout artifacts')
    else:
        commit(folder, model, rng, np.empty(0, np.int64), order, position, binding)
    try:
        while True:
            step = model.updates
            if step in config['metricsAt']:
                point = pin(checkpoint_path(folder, step))
                evaluation_once(metric_paths(folder, step), binding, {'kind': 'training', 'step': step, 'checkpoint': point},
                    lambda: {'stage': 'B', 'updates': step, 'checkpoint': point, **evaluate(model, data, indices)})
                metric = validate_metrics(folder, step, binding, rows)
                print(json.dumps({'step': step, 'trainingJointExact': metric['jointExactCount'], 'trainingRows': len(indices)}), flush=True)
            if step == config['updates']:
                break
            chosen, order, position = sampler(rng, order, position, len(indices), config['batch'])
            intent = update_intent_path(folder, step + 1)
            write_once(intent, {'binding': binding, 'step': step + 1, 'fromCheckpoint': pin(checkpoint_path(folder, step)),
                       'previousReceipt': pin(checkpoint_path(folder, step).with_suffix('.json')), 'sampledIndices': chosen.tolist()})
            loss, losses, gradients = obj.loss_gradients(model, data.batch([indices[int(i)] for i in chosen]))
            require(np.isfinite(loss) and np.isfinite(losses).all(), 'NUMERICAL_FAILURE')
            norm = model.adam(gradients, LEARNING_RATE, CLIP_NORM)
            require(np.isfinite(norm) and model.updates == step + 1, 'NUMERICAL_FAILURE or optimizer clock differs')
            obj.assert_finite_frozen_value(model)
            commit(folder, model, rng, chosen, order, position, binding, pin(intent), float(loss), np.asarray(losses).mean(axis=0).tolist(), float(norm))
            if model.updates % 500 == 0:
                print(json.dumps({'step': model.updates, 'total': config['updates']}), flush=True)
        if boundary_check:
            boundary_check()
        # Unconditional, regardless of training agreement; never evaluates Q-v2.
        final_holdout(folder, model, data, binding, config, evaluate)
        if boundary_check:
            boundary_check()
    except Exception as error:
        if not (folder / 'failure.json').exists():
            write_once(folder / 'failure.json', {'freeze': binding['freeze'], 'updates': model.updates,
                       'errorType': type(error).__name__, 'error': str(error), 'automaticRetryAllowed': False})
        raise
    final = read(metric_paths(folder, model.updates)[0])
    result = {'schema': 'indrill-fit512-run-result-v1', 'complete': True, 'freeze': binding['freeze'], 'selection': freeze['selection'],
              'updates': model.updates, 'sampleVisits': model.updates * config['batch'], 'fitThreshold': freeze['fitThreshold'],
              'checkpoint': pin(checkpoint_path(folder, model.updates)), 'finalMetrics': pin(metric_paths(folder, model.updates)[0]),
              'finalTrainingJointExact': final['jointExactCount'], 'holdoutCandidate': pin(holdout_paths(folder, 'candidate')[0]),
              'decision': fit_decision(model.updates, final['jointExactCount'], config, freeze['fitThreshold']),
              'freshInitialization': True, 'holdoutAffectsFitDecision': False, 'candidateOnlyFinalHoldout': True,
              'newGames': 0, 'promotion': False}
    write_once(folder / 'result.json', result)
    print(json.dumps(result), flush=True)
    return result


def run(freeze_path, resume=False):
    freeze, data = verify_freeze(freeze_path)
    freeze_pin = pin(freeze_path)
    selection = read(freeze['selection']['path'])
    return execute(OUTPUT / 'run', freeze_path, freeze, data, selection['fullDatasetIndices'], resume=resume,
                   boundary_check=lambda: verify_frozen_bytes(freeze_path, freeze, freeze_pin))


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
