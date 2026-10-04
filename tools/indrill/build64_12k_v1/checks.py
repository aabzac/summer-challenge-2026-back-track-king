"""Synthetic continuation/persistence checks; no real rows or production run.

High-counter snapshots are explicitly artificial fixtures. They are not evidence
of an 8,000-update training prefix and never alter the retained parent experiment.
"""
import argparse
import ast
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
import run_fit as run

obj, np, q = run.obj, run.np, run.q


def reject(callback, fragment=''):
    try:
        callback()
    except (ValueError, FileNotFoundError, FileExistsError) as error:
        assert fragment.lower() in str(error).lower(), str(error)
        return {'errorType': type(error).__name__, 'reason': str(error)}
    raise AssertionError('Expected synthetic rejection: ' + fragment)


def function_tree(path, name):
    tree = ast.parse(Path(path).read_text(encoding='utf-8-sig'))
    return ast.dump(next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == name),
                    include_attributes=False)


def same_arrays(first, second):
    assert set(first) == set(second)
    for name, left in first.items():
        right = second[name]
        assert left.dtype == right.dtype and left.shape == right.shape and left.tobytes() == right.tobytes(), name


def same_models(first, second):
    assert first.updates == second.updates
    for name in ('p', 'm', 'v'):
        same_arrays(getattr(first, name), getattr(second, name))


def same_sampler(first_rng, second_rng, first_order, second_order, first_position, second_position):
    assert first_rng.bit_generator.state == second_rng.bit_generator.state
    assert first_position == second_position and first_order.dtype == second_order.dtype
    assert first_order.tobytes() == second_order.tobytes()


def bits(value):
    return np.float64(value).tobytes()


def items():
    result = []
    for index in range(64):
        width, height = (2, 2) if index % 2 == 0 else (3, 2)
        size = width * height
        x = np.random.default_rng(614021 + index).normal(0, .2, (2, size, 64)).astype(np.float32)
        legal = np.ones(size, bool); legal[2] = False
        state = q.State(x, legal, np.asarray([1, 1], bool),
                        (((0, 1) if index % 3 else ()), ()), (index % 2, -1), (0., 0.), False)
        episode = q.Episode('12K-SYNTHETIC-' + str(index), width, height, 2,
                            np.asarray([i % 2 for i in range(size)], np.int32),
                            np.asarray([1, 2, 1, 3, 1, 2][:size], np.int32), [state])
        result.append(q.Transition(episode, 0, 0, 0, 0., 0.))
    return result


def prior_proof():
    path = run.ROOT / 'build/analysis/indrill-build64-long-v1/setup/checks.json'
    proof = run.read(path)
    assert proof['schema'] == 'indrill-build64-long-checks-v1' and proof['passed'] is True and proof['syntheticOnly'] is True
    assert proof['realRowsRead'] == proof['realCandidateFitRuns'] == 0 and proof['environment'] == run.environment()
    for path, digest in proof['sourcePins'].items():
        run.check_pin({'path': path, 'sha256': digest})
    for entry in proof['runtimePins'] + proof['priorProofs']:
        run.check_pin(entry)
    assert proof['objective']['finiteDifferenceProofReusedThroughAstAndSourceIdentity'] is True
    assert proof['objective']['fiveUpdateLossGradientNormParametersAdamBitExact'] is True
    return run.pin(run.ROOT / 'build/analysis/indrill-build64-long-v1/setup/checks.json')


def objective_checks():
    original = obj.original
    same_ast = {}
    for name in ('output_objective', 'network_gradients', 'assert_network_invariants'):
        expected = function_tree(original.__file__, name)
        assert function_tree(obj.__file__, name) == expected
        same_ast[name] = hashlib.sha256(expected.encode()).hexdigest()
    assert obj.UPDATES == 12000 and original.UPDATES == 8000 and original.original.UPDATES == 2000
    assert obj.MODEL_SEED == original.MODEL_SEED == 2026100401
    assert obj.subset_loss is original.subset_loss and obj.q is original.q and obj.v2 is original.v2
    candidate, reference = run.initialize(), original.network_initializer()
    same_models(candidate, reference)
    data = items(); rng = np.random.default_rng(2026100411)
    order, position = np.empty(0, np.int64), 0
    for _ in range(5):
        chosen, order, position = run.sampler(rng, order, position)
        batch = q.Batch([data[int(i)] for i in chosen])
        loss, gradient = obj.network_gradients(candidate, batch)
        old_loss, old_gradient = original.network_gradients(reference, batch)
        assert bits(loss) == bits(old_loss)
        same_arrays(gradient, old_gradient)
        assert bits(candidate.adam(gradient, .0003, 10.)) == bits(reference.adam(old_gradient, .0003, 10.))
        same_models(candidate, reference)
    cap = run.initialize(); cap.updates = 12000  # Artificial counter-boundary fixture only.
    obj.assert_network_invariants(cap)
    cap.updates = 12001
    over_cap = reject(lambda: obj.assert_network_invariants(cap), 'update count')
    return {'functionAstSha256': same_ast, 'priorFiniteDifferenceProofReused': True,
            'freshInitializerBitExact': True, 'firstFiveLossGradientNormParameterMomentUpdatesBitExact': True,
            'syntheticUpdatesPerComparisonModel': 5, 'completed12000CounterAccepted': True,
            'above12000Rejected': over_cap, 'oldModuleConstantsUnchanged': True}


def sampler_at(step):
    rng = np.random.default_rng(2026100411)
    order, position = np.empty(0, np.int64), 0
    for _ in range(step):
        _, order, position = run.sampler(rng, order, position)
    return rng, order, position


def sampler_checks():
    rng, order, position = sampler_at(0)
    old_rng, old_order, old_position = sampler_at(0)
    visits, extra_visits = np.zeros(64, np.int64), np.zeros(64, np.int64)
    continued_rng, continued_order, continued_position = sampler_at(8000)
    for step in range(1, 12001):
        chosen, order, position = run.sampler(rng, order, position)
        if old_position == len(old_order):
            old_order, old_position = old_rng.permutation(64), 0
        expected = old_order[old_position:old_position + 16]; old_position += 16
        assert chosen.tobytes() == expected.tobytes()
        same_sampler(rng, old_rng, order, old_order, position, old_position)
        visits[chosen] += 1
        if step > 8000:
            continued, continued_order, continued_position = run.sampler(continued_rng, continued_order, continued_position)
            assert chosen.tobytes() == continued.tobytes()
            same_sampler(rng, continued_rng, order, continued_order, position, continued_position)
            extra_visits[chosen] += 1
    assert visits.tolist() == [3000] * 64 and extra_visits.tolist() == [1000] * 64
    return {'samplerOnlyTotalUpdates': 12000, 'totalVisits': int(visits.sum()), 'visitsPerObservation': 3000,
            'continuedUpdates': 4000, 'continuedVisits': int(extra_visits.sum()), 'continuedVisitsPerObservation': 1000,
            'fullAndRestoredSamplerBitExact': True, 'modelUpdates': 0}


def update(model, rng, order, position, data):
    chosen, order, position = run.sampler(rng, order, position)
    loss, gradient = obj.network_gradients(model, q.Batch([data[int(i)] for i in chosen]))
    norm = model.adam(gradient, .0003, 10.)
    obj.assert_network_invariants(model)
    return chosen, order, position, float(loss), float(norm)


def parent_snapshot(folder, model, rng, order, position):
    folder.mkdir()
    binding = {'schema': '12K-SYNTHETIC-PARENT', 'artificialCounter': True, 'realRowsRead': 0}
    path = folder / f'parent-{model.updates:05d}.npz'
    run.checkpoint(path, model, rng, order, position, binding)
    receipt = path.with_suffix('.json')
    run.write_once(receipt, {'binding': binding, 'checkpoint': run.pin(path), 'previousReceipt': None})
    return {'checkpoint': run.pin(path), 'receipt': run.pin(receipt), 'binding': binding}


def immutable_parent_checks(root):
    root.mkdir(); data = items()
    direct = run.initialize(); direct.updates = 7999  # Artificial state; no historical prefix is asserted.
    rng, order, position = sampler_at(7999)
    _, order, position, _, _ = update(direct, rng, order, position, data)
    assert direct.updates == 8000 and position == 64 and any(np.any(v) for v in direct.m.values())
    parent = parent_snapshot(root / 'parent', direct, rng, order, position)
    restored, restored_rng = run.initialize(), np.random.default_rng(0)
    restored_order, restored_position = run.restore_parent(restored, restored_rng, parent, 64, 8000)
    same_models(direct, restored)
    same_sampler(rng, restored_rng, order, restored_order, position, restored_position)
    prior, prior_rng = obj.original.network_initializer(), np.random.default_rng(0)
    prior_order, prior_position = run.restore_parent(prior, prior_rng, parent, 64, 8000)
    for offset in range(3):
        chosen, order, position, loss, norm = update(direct, rng, order, position, data)
        other, restored_order, restored_position, other_loss, other_norm = update(
            restored, restored_rng, restored_order, restored_position, data)
        assert chosen.tobytes() == other.tobytes() and bits(loss) == bits(other_loss) and bits(norm) == bits(other_norm)
        same_models(direct, restored)
        same_sampler(rng, restored_rng, order, restored_order, position, restored_position)
        if offset == 0:
            # The unchanged old objective permits its completed 8000 state. Its
            # existing Adam can make this one synthetic 8001 step without any
            # old-module/counter mutation or later old-objective evaluation.
            prior_chosen, prior_order, prior_position = run.sampler(prior_rng, prior_order, prior_position)
            prior_loss, prior_gradient = obj.original.network_gradients(prior, q.Batch([data[int(i)] for i in prior_chosen]))
            prior_norm = prior.adam(prior_gradient, .0003, 10.)
            assert bits(prior_loss) == bits(loss) and bits(prior_norm) == bits(norm)
            assert position == prior_position == 16 and chosen.tobytes() == prior_chosen.tobytes()
            same_models(direct, prior)
            same_sampler(rng, prior_rng, order, prior_order, position, prior_position)
    assert direct.updates == 8003
    for key in ('checkpoint', 'receipt'):
        run.check_pin(parent[key])
    write_once = reject(lambda: run.checkpoint(run.absolute(parent['checkpoint']['path']), restored,
                                              restored_rng, restored_order, restored_position, parent['binding']), 'File exists')
    run.check_pin(parent['checkpoint'])
    return {'artificialStartingCounter': 7999, 'historical7999TrainingClaim': False,
            'actualSyntheticUpdateToParent': 1, 'continuationUpdatesPerPath': 3,
            'parametersAdamRngOrderAndPositionRestoreBitExact': True,
            'continuousAndRestored8001Through8003BitExact': True, 'parentPinsUnchanged': True,
            'oldObjectiveAndAdam8001StepBitExact': True, 'crossesEpochBoundaryFromPosition64': True,
            'parentOverwriteRejected': write_once}


def anchor_chain(folder, parent, config):
    folder.mkdir(); (folder / 'checkpoints').mkdir()
    binding = {'schema': '12K-SYNTHETIC-CONTINUATION', 'settings': config, 'parent': parent}
    model, rng = run.initialize(), np.random.default_rng(0)
    order, position = run.restore_parent(model, rng, parent, config['observations'], config['startStep'])
    path = run.checkpoint_path(folder, model.updates)
    run.checkpoint(path, model, rng, order, position, binding)
    run.write_once(path.with_suffix('.json'), {'binding': binding, 'checkpoint': run.pin(path),
        'previousReceipt': parent['receipt'], 'parentCheckpoint': parent['checkpoint']})
    return model, rng, order, position, binding


def commit_update(folder, model, rng, order, position, data, binding, *, save_receipt=True):
    before = run.checkpoint_path(folder, model.updates)
    prior_receipt = run.pin(before.with_suffix('.json'))
    chosen, next_order, next_position = run.sampler(rng, order, position)
    intent_path = run.update_intent_path(folder, model.updates + 1)
    run.write_once(intent_path, {'binding': binding, 'fromCheckpoint': run.pin(before),
        'previousReceipt': prior_receipt, 'sampledIndices': chosen.tolist()})
    loss, gradient = obj.network_gradients(model, q.Batch([data[int(i)] for i in chosen]))
    norm = model.adam(gradient, .0003, 10.)
    path = run.checkpoint_path(folder, model.updates)
    run.checkpoint(path, model, rng, next_order, next_position, binding)
    if save_receipt:
        run.write_once(path.with_suffix('.json'), {'binding': binding, 'checkpoint': run.pin(path),
            'previousReceipt': prior_receipt, 'updateIntent': run.pin(intent_path),
            'sampledIndices': chosen.tolist(), 'loss': float(loss), 'unclippedGradientNorm': float(norm)})
    return next_order, next_position


def numeric_chain_checks(root):
    root.mkdir(); data = items()
    config = {'startStep': 9999, 'updates': 10001, 'observations': 64, 'batch': 16, 'samplingSeed': 2026100411}
    seed_model = run.initialize(); seed_model.updates = 9999  # Artificial parent, not a trained prefix.
    seed_rng, seed_order, seed_position = sampler_at(9999)
    parent = parent_snapshot(root / 'parent', seed_model, seed_rng, seed_order, seed_position)
    full_dir, split_dir = root / 'continuous', root / 'resumed'
    full, rng, order, position, binding = anchor_chain(full_dir, parent, config)
    for _ in range(2):
        order, position = commit_update(full_dir, full, rng, order, position, data, binding)
    partial, partial_rng, partial_order, partial_position, _ = anchor_chain(split_dir, parent, config)
    partial_order, partial_position = commit_update(split_dir, partial, partial_rng, partial_order, partial_position, data, binding)
    resumed, resumed_rng = run.initialize(), np.random.default_rng(0)
    resumed_order, resumed_position = run.verify_chain(split_dir, resumed, resumed_rng, binding, config)
    assert resumed.updates == 10000
    same_models(partial, resumed)
    same_sampler(partial_rng, resumed_rng, partial_order, resumed_order, partial_position, resumed_position)
    resumed_order, resumed_position = commit_update(split_dir, resumed, resumed_rng, resumed_order, resumed_position, data, binding)
    same_models(full, resumed)
    same_sampler(rng, resumed_rng, order, resumed_order, position, resumed_position)
    checked, checked_rng = run.initialize(), np.random.default_rng(0)
    checked_order, checked_position = run.verify_chain(split_dir, checked, checked_rng, binding, config)
    same_models(full, checked)
    same_sampler(rng, checked_rng, order, checked_order, position, checked_position)
    assert [path.name for path in sorted((split_dir / 'checkpoints').glob('*.npz'))] == [
        'step-09999.npz', 'step-10000.npz', 'step-10001.npz']
    pending_dir = root / 'pending'; anchor_chain(pending_dir, parent, config)
    run.write_once(run.update_intent_path(pending_dir, 10000), {'syntheticPending': True})
    pending = reject(lambda: run.verify_chain(pending_dir, run.initialize(), np.random.default_rng(0), binding, config), 'Uncommitted')
    missing_dir = root / 'missing-receipt'
    m, r, o, p, _ = anchor_chain(missing_dir, parent, config)
    commit_update(missing_dir, m, r, o, p, data, binding, save_receipt=False)
    missing = reject(lambda: run.verify_chain(missing_dir, run.initialize(), np.random.default_rng(0), binding, config), 'step-10000.json')
    failed_dir = root / 'recorded-failure'; anchor_chain(failed_dir, parent, config)
    run.write_once(failed_dir / 'failure.json', {'syntheticOnly': True})
    failed = reject(lambda: run.verify_chain(failed_dir, run.initialize(), np.random.default_rng(0), binding, config), 'failed run')
    for key in ('checkpoint', 'receipt'):
        run.check_pin(parent[key])
    return {'artificialStartingCounter': 9999, 'historicalTrainingClaim': False,
            'verifiedNumericSequence': [9999, 10000, 10001], 'resumeAt10000BitExact': True,
            'parametersAdamSamplerAllVerified': True, 'pendingIntentRejected': pending,
            'missingReceiptRejected': missing, 'failedRunRejected': failed, 'parentPinsUnchanged': True}


class NoScoringModel:
    def __init__(self, step):
        self.updates = step

    def forward(self, *_args, **_kwargs):
        raise AssertionError('Saved metric operation attempted learned scoring')


class NoScoringData:
    rows = [{}] * 64

    def batch(self, *_args, **_kwargs):
        raise AssertionError('Saved metric operation attempted to construct a model batch')


def synthetic_metric(parent_pin):
    # Handwritten saved-metric fixture, not a model prediction or fitting result.
    rows = [{'observationId': 'SYNTHETIC-' + str(i), 'mapHash': 'synthetic-map-' + str(i // 4),
             'teacherCardinality': 0, 'costPattern': 'EMPTY', 'turnBand': '0..19', 'teacherSeat': i % 2,
             'hadSkippedActions': False, 'targetCells': [], 'predictedCells': [], 'buildExact': 1,
             'cellPrecision': 1., 'cellRecall': 1., 'predictedPaint': 0, 'teacherPaint': 0, 'buildNll': 1.}
            for i in range(64)]
    return {'updates': 8000, 'checkpoint': parent_pin, 'rows': rows, 'observations': 64,
            'evaluationBatchSize': 32, 'buildExactCount': 64, 'meanBuildNll': 1., 'summary': run.summarize(rows),
            'precisionRecallConvention': 'Both empty: precision=recall=1. Exactly one empty: both=0.'}


def metric_and_source_guards(root):
    root.mkdir()
    model = run.initialize(); model.updates = 8000  # Artificial untrained metric-binding fixture.
    rng, order, position = sampler_at(8000)
    parent = parent_snapshot(root / 'parent', model, rng, order, position)
    baseline_source = root / 'parent-metric.json'
    run.write_once(baseline_source, synthetic_metric(parent['checkpoint']))
    parent_metric_pin = run.pin(baseline_source)
    folder = root / 'inherited'
    _, _, _, _, binding = anchor_chain(folder, parent, dict(run.SETTINGS))
    inherited = run.inherit_metrics(folder, 8000, binding, parent_metric_pin)
    before_repeat = {p.relative_to(folder).as_posix(): run.digest(p) for p in folder.rglob('*') if p.is_file()}
    assert {k: v for k, v in inherited.items() if k != 'checkpoint'} == {
        k: v for k, v in run.read(baseline_source).items() if k != 'checkpoint'}
    assert inherited['checkpoint'] == run.pin(run.checkpoint_path(folder, 8000))
    assert run.inherit_metrics(folder, 8000, binding, parent_metric_pin) == inherited
    assert before_repeat == {p.relative_to(folder).as_posix(): run.digest(p) for p in folder.rglob('*') if p.is_file()}
    parent_metadata = root / 'synthetic-parent-metadata.json'
    run.write_once(parent_metadata, {'syntheticOnly': True, 'role': 'Start-proof field-binding fixture'})
    metadata_pin = run.pin(parent_metadata)
    fixture_freeze = {'parent': dict(parent, freeze=metadata_pin, evidence=metadata_pin, chain=metadata_pin,
                                    auditedPinsCount=2, metrics=parent_metric_pin)}
    missing_start = reject(lambda: run.validate_start(folder, fixture_freeze, binding, None), 'Missing/different starting proof')
    run.write_once(folder / 'start-proof.json', run.start_proof_value(folder, fixture_freeze, binding))
    run.validate_start(folder, fixture_freeze, binding, None)
    proof_pin = run.pin(folder / 'start-proof.json')
    wrong_start = reject(lambda: run.validate_start(folder, fixture_freeze, dict(binding, schema='wrong'), None), 'Missing/different starting proof')
    run.check_pin(proof_pin)
    starting_score = reject(lambda: run.record_metrics(folder, NoScoringModel(8000), NoScoringData(), binding), 'must be inherited')
    unscheduled_score = reject(lambda: run.record_metrics(folder, NoScoringModel(9000), NoScoringData(), binding), 'declared later steps')
    guards = []
    for mask in range(7):
        trial = root / f'metric-triplet-{mask}'; trial.mkdir()
        for index, path in enumerate(run.metric_paths(trial, 10000)):
            if mask & (1 << index):
                run.write_once(path, {'syntheticIncompleteTriplet': True})
        before = {p.name: run.digest(p) for p in trial.iterdir()}
        validation = reject(lambda: run.validate_metrics(trial, 10000, binding), 'Interrupted metrics')
        recording = None
        if mask:
            recording = reject(lambda: run.record_metrics(trial, NoScoringModel(10000), NoScoringData(), binding), 'Interrupted metrics')
        assert before == {p.name: run.digest(p) for p in trial.iterdir()}
        guards.append({'existingTripletMask': mask, 'validationRejected': validation,
                       'partialRecordingRejected': recording, 'modelCalls': 0, 'writes': 0})
    partial_inherit = root / 'partial-inheritance'; partial_inherit.mkdir()
    run.write_once(run.metric_paths(partial_inherit, 8000)[1], {'syntheticInterruptedInheritance': True})
    inherited_guard = reject(lambda: run.inherit_metrics(partial_inherit, 8000, binding, parent_metric_pin), 'Interrupted metrics')
    fixture = root / 'synthetic-source.py'
    with fixture.open('x', encoding='utf-8') as stream:
        stream.write('# Synthetic pin fixture\n')
    source_pin = run.pin(fixture)
    with fixture.open('a', encoding='utf-8') as stream:
        stream.write('# Deliberately changed synthetic bytes\n')
    changed_source = reject(lambda: run.check_pin(source_pin), 'Pinned bytes changed')
    for entry in (parent['checkpoint'], parent['receipt'], parent_metric_pin):
        run.check_pin(entry)
    return {'startingPayloadInheritedExactlyWithoutModelArgument': True, 'repeatedInheritanceDoesNotWrite': True,
            'savedStartProofValidatedWithoutScoring': True, 'missingStartProofRejected': missing_start,
            'changedStartBindingRejected': wrong_start,
            'startingMetricScoringRejected': starting_score, 'unscheduledScoringRejected': unscheduled_score,
            'incompleteMetricTriplets': guards, 'partialInheritanceRejected': inherited_guard,
            'changedSyntheticSourceRejected': changed_source, 'parentCheckpointReceiptAndMetricUnchanged': True}


def stop_order_guard():
    # Static guard complements the numeric invariant test; the real runner is
    # never entered by this synthetic harness.
    tree = ast.parse(Path(run.__file__).read_text(encoding='utf-8-sig'))
    function = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == 'run')
    loops = [node for node in ast.walk(function) if isinstance(node, ast.While)]
    assert len(loops) == 1
    expected = ast.dump(ast.parse("step == SETTINGS['updates']", mode='eval').body, include_attributes=False)
    stops = [index for index, node in enumerate(loops[0].body) if isinstance(node, ast.If)
             and ast.dump(node.test, include_attributes=False) == expected
             and len(node.body) == 1 and isinstance(node.body[0], ast.Break)]
    assert len(stops) == 1
    calls = {}
    for index, node in enumerate(loops[0].body):
        for child in ast.walk(node):
            if isinstance(child, ast.Call):
                name = child.func.id if isinstance(child.func, ast.Name) else child.func.attr if isinstance(child.func, ast.Attribute) else ''
                if name in ('sampler', 'network_gradients', 'adam'):
                    calls.setdefault(name, []).append(index)
    assert set(calls) == {'sampler', 'network_gradients', 'adam'}
    assert all(stops[0] < index for indices in calls.values() for index in indices)
    return {'staticCompletedStepBreakPrecedesSamplerGradientAndAdam': True,
            'productionEntryPointExecuted': False, 'stopStep': 12000}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = run.absolute(args.out)
    setup = (run.OUTPUT / 'setup').resolve(); proof_path = setup / 'checks.json'
    run.require(out.is_relative_to(setup) and out != setup and not out.exists() and not proof_path.exists(),
                'Choose a new 12k fixture directory; preserve completed proof')
    original_settings = json.dumps(run.SETTINGS, sort_keys=True)
    paths = set()
    for name in ('build64_12k_v1', 'build64_long_v1', 'build64_v1', 'fit_one_v1', 'capacity_v1'):
        paths.update((run.ROOT / 'tools/indrill' / name).glob('*.py'))
    paths.update((Path(q.__file__), Path(obj.v2.__file__)))
    sources = {run.pin(path)['path']: run.pin(path)['sha256'] for path in sorted(paths)}
    runtime = [run.pin(path) for path in sorted(run.runtime_files())]
    report = {'schema': 'indrill-build64-12k-checks-v1', 'syntheticOnly': True,
              'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'sourcePins': sources,
              'runtimePins': runtime, 'environment': run.environment(), 'priorProofs': [prior_proof()],
              'realRowsRead': 0, 'realCandidateFitRuns': 0,
              'limitations': ['High-counter states and metric payloads are explicitly artificial fixtures, not learned results.',
                              'Earlier derivative checks are reused through identical mathematical AST and pinned sources.',
                              'No real parent restoration, preparation, training or learned evaluation is run.']}
    out.mkdir(parents=True)
    run.write_once(out / 'intent.json', report)
    try:
        report['objective'] = objective_checks()
        report['sampling'] = sampler_checks()
        report['parentContinuation'] = immutable_parent_checks(out / 'parent-continuation')
        report['numericCheckpointChain'] = numeric_chain_checks(out / 'numeric-chain')
        report['metricAndSourceGuards'] = metric_and_source_guards(out / 'guards')
        report['stopGuard'] = stop_order_guard()
        assert json.dumps(run.SETTINGS, sort_keys=True) == original_settings
        assert run.SETTINGS['startStep'] == 8000 and run.SETTINGS['updates'] == 12000 and run.SETTINGS['batch'] == 16
        assert obj.original.UPDATES == 8000 and obj.original.original.UPDATES == 2000
        for path, digest in sources.items():
            run.check_pin({'path': path, 'sha256': digest})
        for entry in runtime + report['priorProofs']:
            run.check_pin(entry)
        assert report['environment'] == run.environment()
        report['productionSettingsUnchanged'] = True; report['passed'] = True
    except Exception as error:
        report['passed'] = False
        report['failure'] = {'type': type(error).__name__, 'message': str(error)}
        run.write_once(out / 'result.json', report)
        raise
    run.write_once(out / 'result.json', report)
    run.write_once(proof_path, report)
    print(json.dumps({'passed': True, 'syntheticOnly': True, 'result': run.pin(proof_path),
                      'fixtures': out.relative_to(run.ROOT).as_posix()}))


if __name__ == '__main__':
    main()
