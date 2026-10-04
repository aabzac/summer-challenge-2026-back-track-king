"""Fresh fixed 8,000-update build fit with an exact retained 2,000-update prefix."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
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


old = module('indrill_long_prior_runner', HERE.parent / 'build64_v1/run_fit.py')
obj = module('indrill_long_objective', HERE / 'objective.py')
np, q, previous = obj.np, obj.q, old.previous
require, read, write_once = old.require, old.read, old.write_once
absolute, digest, pin, check_pin = old.absolute, old.digest, old.pin, old.check_pin
environment, runtime_files = old.environment, old.runtime_files
sampler, schedule_entry, hash_entry = old.sampler, old.schedule_entry, old.hash_entry
evaluate, aggregate, summarize = old.evaluate, old.aggregate, old.summarize
record_metrics, validate_metrics = old.record_metrics, old.validate_metrics
OUTPUT = ROOT / 'build/analysis/indrill-build64-long-v1'
PROTOCOL = ROOT / 'docs/indrill-build64-long-v1.md'
EVIDENCE = ROOT / 'docs/evidence/indrill-build64-v1.json'
SETTINGS = {'modelSeed': 2026100401, 'samplingSeed': 2026100411, 'updates': 8000,
            'batch': 16, 'observations': 64, 'metricsAt': [0, 500, 1000, 1500, 2000, 4000, 6000, 8000]}
LEARNING_RATE, CLIP_NORM, PREFIX_UPDATES = .0003, 10., 2000


def initialize():
    return obj.network_initializer()


def checkpoint(path, model, rng, order, position, binding):
    obj.assert_network_invariants(model)
    previous.checkpoint(Path(path), model, rng, order, position, binding)


def restore(path, model, rng, binding, count):
    order, position = previous.restore(Path(path), model, rng, binding, count)
    obj.assert_network_invariants(model)
    return order, position


def verify_chain(folder, model, rng, binding, config):
    """Old write-once chain contract, with the new invariant's 8,000-update cap."""
    folder = Path(folder)
    require(not (folder / 'failure.json').exists(), 'A failed run cannot resume')
    points = sorted((folder / 'checkpoints').glob('step-*.npz'))
    steps = [int(p.stem.split('-')[1]) for p in points]
    require(steps and steps == list(range(steps[-1] + 1)) and steps[-1] <= config['updates'], 'Incomplete checkpoint sequence')
    expected_files, expected_intents = set(), set()
    expected_rng, initial = np.random.default_rng(config['samplingSeed']), initialize()
    order, position, previous_receipt = np.empty(0, np.int64), 0, None
    for step, path in enumerate(points):
        chosen = np.empty(0, np.int64)
        if step:
            chosen, order, position = sampler(expected_rng, order, position, config['observations'], config['batch'])
        receipt_path = path.with_suffix('.json'); receipt = read(receipt_path)
        expected_files |= {path.name, receipt_path.name}
        require(receipt['binding'] == binding and receipt['checkpoint'] == pin(path)
                and receipt['previousReceipt'] == previous_receipt, 'Checkpoint receipt chain differs')
        if step:
            intent_path = folder / f'update-{step:04d}-intent.json'; expected_intents.add(intent_path.name)
            require(receipt['updateIntent'] == pin(intent_path) and receipt['sampledIndices'] == chosen.tolist(), 'Update receipt sampling differs')
            require(read(intent_path) == {'binding': binding, 'fromCheckpoint': pin(points[step - 1]),
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


def compare_prefix_checkpoint(new_path, old_path, step, new_receipt=None, old_receipt=None):
    """Pure byte/state comparison; path/freeze bindings intentionally differ."""
    with np.load(absolute(new_path), allow_pickle=False) as candidate, np.load(absolute(old_path), allow_pickle=False) as reference:
        require(set(candidate.files) == set(reference.files), 'Prefix tensor inventory differs')
        for name in candidate.files:
            if name == 'metadata':
                first, second = json.loads(str(candidate[name])), json.loads(str(reference[name]))
                require(first['updates'] == second['updates'] == step and all(first[k] == second[k]
                        for k in ('rngState', 'samplePosition')), 'Prefix optimizer/sampler metadata differs')
            else:
                first, second = candidate[name], reference[name]
                require(first.shape == second.shape and first.dtype == second.dtype and first.tobytes() == second.tobytes(),
                        'Prefix array differs: ' + name)
    if step:
        require(new_receipt is not None and old_receipt is not None, 'Prefix receipts are required')
        require(new_receipt['sampledIndices'] == old_receipt['sampledIndices'], 'Prefix chosen batch differs')
        for key in ('loss', 'unclippedGradientNorm'):
            require(np.isfinite(new_receipt[key]) and np.isfinite(old_receipt[key]) and
                    struct.pack('>d', new_receipt[key]) == struct.pack('>d', old_receipt[key]), 'Prefix receipt differs: ' + key)


def compare_prefix_metrics(new_value, old_value):
    require({k: v for k, v in new_value.items() if k != 'checkpoint'} ==
            {k: v for k, v in old_value.items() if k != 'checkpoint'}, 'Prefix metric payload differs')


def check_proof(path):
    proof = old.fitone.check_proof(path, 'indrill-build64-long-checks-v1')
    require(proof['realRowsRead'] == proof['realCandidateFitRuns'] == 0 and proof['productionSettingsUnchanged'] is True
            and proof['environment'] == environment(), 'Synthetic proof scope/runtime differs')
    require({pin(p)['path'] for p in HERE.glob('*.py')}.issubset(proof['sourcePins']), 'Proof omits active implementation')
    for item in proof['runtimePins'] + proof['priorProofs']:
        check_pin(item)
    require({absolute(p['path']) for p in proof['runtimePins']} == runtime_files(), 'Runtime proof inventory differs')
    return proof


def referenced_pins(value):
    if isinstance(value, dict):
        if set(value) == {'path', 'sha256'}:
            yield value
        else:
            for item in value.values():
                yield from referenced_pins(item)
    elif isinstance(value, list):
        for item in value:
            yield from referenced_pins(item)


def reference_entries(freeze):
    check_pin(freeze['referenceChain'])
    chain = read(freeze['referenceChain']['path'])
    require(chain['complete'] is True and chain['auditStatus'] == 'PASS' and chain['freeze'] == freeze['priorFreeze'], 'Reference chain differs')
    entries = chain['chain']
    require([e['step'] for e in entries] == list(range(PREFIX_UPDATES + 1)), 'Reference prefix length differs')
    return entries


def prepare(checks_path):
    require(not any((OUTPUT / n).exists() for n in ('freeze.json', 'prepare-intent.json', 'run')), 'Refusing existing preparation/run')
    check_proof(checks_path)
    prior_path = old.OUTPUT / 'freeze.json'
    prior, data = old.verify_freeze(prior_path)
    evidence = read(EVIDENCE)
    require(evidence['experiment'] == 'indrill-build64-v1' and evidence['complete'] is True
            and evidence['auditStatus'] == 'PASS' and evidence['freeze'] == pin(prior_path)
            and evidence['decision'] == 'BUILD_FIT_NOT_ESTABLISHED' and evidence['settings'] == old.SETTINGS,
            'Reference completed audit differs')
    require({k: SETTINGS[k] for k in old.SETTINGS if k not in ('updates', 'metricsAt')} ==
            {k: v for k, v in old.SETTINGS.items() if k not in ('updates', 'metricsAt')}
            and prior['learningRate'] == LEARNING_RATE and prior['clipNorm'] == CLIP_NORM,
            'More than the declared budget/observation schedule differs')
    references = list(referenced_pins(evidence))
    for item in references:
        check_pin(item)
    final = read(evidence['runnerResult']['path'])
    require(final['complete'] is True and final['updates'] == PREFIX_UPDATES and final['comparison']['candidateBuildExact'] == 16
            and final['freeze'] == pin(prior_path), 'Reference result differs')
    require(prior['environment'] == environment(), 'Reference runtime differs; exact prefix cannot be promised')
    OUTPUT.mkdir(parents=True, exist_ok=True)
    write_once(OUTPUT / 'prepare-intent.json', {'createdAtUtc': datetime.now(timezone.utc).isoformat(),
               'settings': SETTINGS, 'priorFreeze': pin(prior_path), 'priorEvidence': pin(EVIDENCE), 'newGames': 0, 'gradientsRun': 0})
    paths = set(HERE.glob('*.py')) | runtime_files() | {PROTOCOL, absolute(checks_path), OUTPUT / 'prepare-intent.json',
            prior_path, EVIDENCE, absolute(prior['selectedRows']['path'])}
    pins = {pin(p['path'])['path']: pin(p['path']) for p in references}
    for path in paths:
        item = pin(path); pins[item['path']] = item
    freeze = {'schema': 'indrill-build64-long-freeze-v1', 'status': 'FROZEN', 'checksPassed': True, 'settings': SETTINGS,
              'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM, 'betas': [.9, .999], 'epsilon': 1e-8,
              'prefixUpdates': PREFIX_UPDATES, 'outputDirectory': OUTPUT.relative_to(ROOT).as_posix(),
              'environment': environment(), 'selectedRows': prior['selectedRows'], 'priorFreeze': pin(prior_path),
              'priorEvidence': pin(EVIDENCE), 'referenceChain': evidence['checkpointChain'],
              'referenceFinalMetrics': evidence['curve'][-1]['metrics'],
              'referenceMetrics': [{'step': e['step'], 'metrics': e['metrics'], 'intent': e['intent'], 'receipt': e['receipt']}
                                   for e in evidence['curve']],
              'mapExclusionBinding': prior['mapExclusionBinding'], 'checks': pin(checks_path),
              'pins': [pins[k] for k in sorted(pins)], 'newGames': 0, 'gradientsRun': 0}
    reference_entries(freeze)
    require([e['step'] for e in freeze['referenceMetrics']] == old.SETTINGS['metricsAt'], 'Reference metric schedule differs')
    write_once(OUTPUT / 'freeze.json', freeze)
    print(json.dumps({'freeze': pin(OUTPUT / 'freeze.json'), 'observations': len(data.rows), 'prefixUpdates': PREFIX_UPDATES}))


def verify_freeze(path):
    freeze = read(path)
    require(freeze['schema'] == 'indrill-build64-long-freeze-v1' and freeze['status'] == 'FROZEN' and freeze['checksPassed'] is True
            and freeze['settings'] == SETTINGS and freeze['prefixUpdates'] == PREFIX_UPDATES
            and freeze['learningRate'] == LEARNING_RATE and freeze['clipNorm'] == CLIP_NORM
            and freeze['betas'] == [.9, .999] and freeze['epsilon'] == 1e-8, 'Wrong fixed protocol')
    require(absolute(freeze['outputDirectory']) == OUTPUT and freeze['environment'] == environment(), 'Output/runtime differs')
    required = set(HERE.glob('*.py')) | runtime_files() | {PROTOCOL, absolute(freeze['checks']['path'])}
    observed = [absolute(p['path']) for p in freeze['pins']]
    require(len(observed) == len(set(observed)) and required.issubset(observed), 'Missing/repeated freeze pins')
    for item in freeze['pins']:
        check_pin(item)
    for key in ('selectedRows', 'priorFreeze', 'priorEvidence', 'referenceChain', 'referenceFinalMetrics', 'mapExclusionBinding', 'checks'):
        check_pin(freeze[key]); require(absolute(freeze[key]['path']) in observed, 'Missing referenced input pin')
    check_proof(freeze['checks']['path'])
    prior, data = old.verify_freeze(absolute(freeze['priorFreeze']['path']))
    require(prior['selectedRows'] == freeze['selectedRows'] and prior['mapExclusionBinding'] == freeze['mapExclusionBinding']
            and prior['environment'] == freeze['environment'], 'Prior input/runtime identity differs')
    evidence = read(freeze['priorEvidence']['path'])
    require(evidence['complete'] is True and evidence['auditStatus'] == 'PASS' and evidence['freeze'] == freeze['priorFreeze']
            and evidence['checkpointChain'] == freeze['referenceChain'] and evidence['curve'][-1]['metrics'] == freeze['referenceFinalMetrics'],
            'Reference audit binding differs')
    require(freeze['referenceMetrics'] == [{'step': e['step'], 'metrics': e['metrics'], 'intent': e['intent'], 'receipt': e['receipt']}
            for e in evidence['curve']], 'Reference metric evidence differs')
    reference_entries(freeze)
    return freeze, data


def compare_reference_step(folder, entries, step, receipt):
    entry = entries[step]
    for key in ('checkpoint', 'receipt', 'updateIntent'):
        if entry[key] is not None:
            check_pin(entry[key])
    path = Path(folder) / 'checkpoints' / f'step-{step:04d}.npz'
    retained = read(entry['receipt']['path'])
    require(retained['checkpoint'] == entry['checkpoint'], 'Reference receipt checkpoint differs')
    compare_prefix_checkpoint(path, entry['checkpoint']['path'], step, receipt, retained)
    return {'checkpoint': entry['checkpoint'], 'receipt': entry['receipt']}


def verify_prefix(folder, freeze, entries, through, expected_rows):
    """Read-only comparison of completed prefix; also used after interruption."""
    require(0 <= through <= PREFIX_UPDATES, 'Invalid prefix extent')
    for step in range(through + 1):
        path = Path(folder) / 'checkpoints' / f'step-{step:04d}.json'
        receipt = read(path)
        require(receipt['prefixReference'] == compare_reference_step(folder, entries, step, receipt), 'Prefix reference receipt differs')
    binding = read(Path(folder) / 'intent.json')['binding']
    for entry in freeze['referenceMetrics']:
        if entry['step'] <= through:
            for key in ('metrics', 'intent', 'receipt'):
                check_pin(entry[key])
            candidate = validate_metrics(folder, entry['step'], binding, expected_rows)
            compare_prefix_metrics(candidate, read(entry['metrics']['path']))


def prefix_gate_value(folder, freeze, binding):
    return {'schema': 'indrill-build64-long-prefix-v1', 'complete': True, 'checksPassed': True,
            'binding': binding, 'referenceChain': freeze['referenceChain'], 'checkpointsCompared': PREFIX_UPDATES + 1,
            'checkpoint': pin(Path(folder) / 'checkpoints/step-2000.npz'),
            'lastReceipt': pin(Path(folder) / 'checkpoints/step-2000.json'),
            'metrics': [{'step': e['step'], 'candidate': pin(Path(folder) / f'metrics-{e["step"]:04d}.json'),
                         'reference': e['metrics']} for e in freeze['referenceMetrics']],
            'arraysAndOptimizerSamplerBitExact': True, 'lossNormAndChosenBatchesBitExact': True,
            'metricPayloadsExact': True, 'learnedPredictionsDuringProof': 0, 'gradientsDuringProof': 0}


def ensure_prefix_gate(folder, freeze, entries, binding, rows):
    path = Path(folder) / 'prefix-proof.json'
    if not path.exists():
        verify_prefix(folder, freeze, entries, PREFIX_UPDATES, rows)
        write_once(path, prefix_gate_value(folder, freeze, binding))
    require(read(path) == prefix_gate_value(folder, freeze, binding), 'Prefix gate differs')


def make_binding(freeze_path, freeze):
    return {'schema': 'indrill-build64-long-checkpoint-v1', 'freeze': pin(freeze_path), 'selectedRows': freeze['selectedRows'],
            'settings': SETTINGS, 'learningRate': LEARNING_RATE, 'clipNorm': CLIP_NORM}


def run(freeze_path, resume=False):
    freeze, data = verify_freeze(freeze_path)
    entries = reference_entries(freeze)
    folder, binding = OUTPUT / 'run', make_binding(freeze_path, freeze)
    model, rng = initialize(), np.random.default_rng(SETTINGS['samplingSeed'])
    order, position = np.empty(0, np.int64), 0
    if resume:
        require(folder.is_dir() and not (folder / 'result.json').exists() and not (folder / 'failure.json').exists(), 'Only an incomplete nonfailed run may resume')
        intent = read(folder / 'intent.json')
        require(intent['binding'] == binding and intent['environment'] == environment(), 'Run intent/runtime differs')
        order, position = verify_chain(folder, model, rng, binding, SETTINGS)
        for step in SETTINGS['metricsAt']:
            if step < model.updates:
                validate_metrics(folder, step, binding, data.rows)
        # A current scheduled metric may legally not have started yet.
        through = min(model.updates, PREFIX_UPDATES)
        if through in old.SETTINGS['metricsAt'] and not (folder / f'metrics-{through:04d}.json').exists():
            through -= 1
        if through >= 0:
            verify_prefix(folder, freeze, entries, through, data.rows)
        if model.updates > PREFIX_UPDATES:
            require((folder / 'prefix-proof.json').exists(), 'Missing prefix proof after continuation')
            ensure_prefix_gate(folder, freeze, entries, binding, data.rows)
    else:
        folder.mkdir()
        write_once(folder / 'intent.json', {'binding': binding, 'environment': environment(), 'createdAtUtc': datetime.now(timezone.utc).isoformat()})
        (folder / 'checkpoints').mkdir()
    try:
        if not resume:
            path = folder / 'checkpoints/step-0000.npz'
            checkpoint(path, model, rng, order, position, binding)
            receipt = {'binding': binding, 'checkpoint': pin(path), 'previousReceipt': None}
            receipt['prefixReference'] = compare_reference_step(folder, entries, 0, receipt)
            write_once(path.with_suffix('.json'), receipt)
        while True:
            step = model.updates
            if step in SETTINGS['metricsAt']:
                record_metrics(folder, model, data, binding)
                if step <= PREFIX_UPDATES:
                    reference = next(e for e in freeze['referenceMetrics'] if e['step'] == step)
                    check_pin(reference['metrics'])
                    compare_prefix_metrics(read(folder / f'metrics-{step:04d}.json'), read(reference['metrics']['path']))
            if step == PREFIX_UPDATES:
                ensure_prefix_gate(folder, freeze, entries, binding, data.rows)
            if step == SETTINGS['updates']:
                break
            chosen, order, position = sampler(rng, order, position)
            before = folder / 'checkpoints' / f'step-{step:04d}.npz'
            path = folder / 'checkpoints' / f'step-{step + 1:04d}.npz'
            previous_receipt = pin(before.with_suffix('.json'))
            intent_path = folder / f'update-{step + 1:04d}-intent.json'
            write_once(intent_path, {'binding': binding, 'fromCheckpoint': pin(before), 'previousReceipt': previous_receipt, 'sampledIndices': chosen.tolist()})
            loss, gradients = obj.network_gradients(model, data.batch(chosen))
            require(np.isfinite(loss), 'Nonfinite build loss')
            norm = model.adam(gradients, LEARNING_RATE, CLIP_NORM)
            require(np.isfinite(norm) and model.updates == step + 1, 'Nonfinite norm/wrong update count')
            checkpoint(path, model, rng, order, position, binding)
            receipt = {'binding': binding, 'checkpoint': pin(path), 'previousReceipt': previous_receipt,
                       'updateIntent': pin(intent_path), 'sampledIndices': chosen.tolist(), 'loss': float(loss), 'unclippedGradientNorm': float(norm)}
            if step + 1 <= PREFIX_UPDATES:
                receipt['prefixReference'] = compare_reference_step(folder, entries, step + 1, receipt)
            write_once(path.with_suffix('.json'), receipt)
    except Exception as error:
        if not (folder / 'failure.json').exists():
            write_once(folder / 'failure.json', {'decision': 'TECHNICAL_FAILURE', 'freeze': pin(freeze_path),
                       'updatesInMemory': int(model.updates), 'error': str(error), 'automaticReplayAllowed': False})
        raise
    final = validate_metrics(folder, SETTINGS['updates'], binding, data.rows)
    result = {'schema': 'indrill-build64-long-run-result-v1', 'freeze': pin(freeze_path), 'updates': model.updates,
              'sampleVisits': model.updates * SETTINGS['batch'], 'finalBuildExact': final['buildExactCount'],
              'decision': 'BUILD_FIT_ESTABLISHED' if final['buildExactCount'] >= 63 else 'BUILD_FIT_NOT_ESTABLISHED',
              'checkpoint': pin(folder / 'checkpoints/step-8000.npz'), 'finalMetrics': pin(folder / 'metrics-8000.json'),
              'prefixProof': pin(folder / 'prefix-proof.json'), 'newGames': 0, 'promotion': False}
    write_once(folder / 'result.json', result)
    print(json.dumps(result))


def finalize(freeze_path):
    require(not (OUTPUT / 'result.json').exists(), 'Refusing existing final report')
    freeze, data = verify_freeze(freeze_path)
    folder, binding = OUTPUT / 'run', make_binding(freeze_path, freeze)
    result, intent = read(folder / 'result.json'), read(folder / 'intent.json')
    require(intent['binding'] == binding and intent['environment'] == environment(), 'Run intent/runtime differs')
    model, rng = initialize(), np.random.default_rng(SETTINGS['samplingSeed'])
    verify_chain(folder, model, rng, binding, SETTINGS)
    require(model.updates == result['updates'] == 8000 and result['sampleVisits'] == 128000
            and result['freeze'] == pin(freeze_path), 'Incomplete fixed budget')
    entries = reference_entries(freeze)
    verify_prefix(folder, freeze, entries, PREFIX_UPDATES, data.rows)
    require((folder / 'prefix-proof.json').exists() and read(folder / 'prefix-proof.json') == prefix_gate_value(folder, freeze, binding), 'Missing/different completed prefix proof')
    for step in SETTINGS['metricsAt']:
        validate_metrics(folder, step, binding, data.rows)
    require(result['checkpoint'] == pin(folder / 'checkpoints/step-8000.npz') and result['finalMetrics'] == pin(folder / 'metrics-8000.json')
            and result['prefixProof'] == pin(folder / 'prefix-proof.json'), 'Result final artifact binding differs')
    final, reference = read(result['finalMetrics']['path']), read(freeze['referenceFinalMetrics']['path'])
    require(result['finalBuildExact'] == final['buildExactCount'] and result['decision'] ==
            ('BUILD_FIT_ESTABLISHED' if final['buildExactCount'] >= 63 else 'BUILD_FIT_NOT_ESTABLISHED'), 'Final decision differs')
    pairs = list(zip(final['rows'], reference['rows']))
    require(len(pairs) == len(final['rows']) == len(reference['rows']) == 64 and reference['buildExactCount'] == 16, 'Reference comparison differs')
    for new, prior in pairs:
        require(all(new[k] == prior[k] for k in ('observationId', 'inputSha256', 'mapHash', 'targetCells')), 'Comparison row identity differs')
    groups = {name: [new['observationId'] for new, prior in pairs if (bool(new['buildExact']), bool(prior['buildExact'])) == flags]
              for name, flags in (('gained', (True, False)), ('lost', (False, True)), ('retainedExact', (True, True)), ('bothInexact', (False, False)))}
    count = final['buildExactCount']
    schedule, exposure = hashlib.sha256(), np.zeros(64, np.int64)
    sampler_rng, order, position = np.random.default_rng(SETTINGS['samplingSeed']), np.empty(0, np.int64), 0
    for step in range(8001):
        chosen = np.empty(0, np.int64)
        if step:
            chosen, order, position = sampler(sampler_rng, order, position); exposure[chosen] += 1
        hash_entry(schedule, schedule_entry(step, chosen, order, position, sampler_rng))
    require(np.array_equal(exposure, np.full(64, 2000)), 'Final exposure differs')
    report = {'schema': 'indrill-build64-long-final-v1', 'complete': True, 'freeze': pin(freeze_path), 'runResult': pin(folder / 'result.json'),
              'decision': result['decision'], 'updates': 8000, 'sampleVisits': 128000, 'visitsPerObservation': exposure.tolist(),
              'checkpointsVerified': 8001, 'scheduleSha256': schedule.hexdigest(), 'prefixProof': pin(folder / 'prefix-proof.json'),
              'comparison': {'referenceFinalMetrics': freeze['referenceFinalMetrics'], 'oldBuildExact': 16, 'candidateBuildExact': count,
                  'netExactDifference': count - 16, 'status': 'BETTER' if count > 16 else 'EQUAL' if count == 16 else 'WORSE',
                  'oldMeanBuildNll': reference['meanBuildNll'], 'candidateMeanBuildNll': final['meanBuildNll'],
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
