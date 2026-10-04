"""Synthetic checks for original joint-objective continuation; no real rows.

Artificial counters and saved-metric fixtures are never production evidence.
No production preparation/training entry point is called by this harness.
"""
import argparse
import ast
from datetime import datetime, timezone
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
        episode = q.Episode('JOINT12K-SYNTHETIC-' + str(index), width, height, 2,
                            np.asarray([i % 2 for i in range(size)], np.int32),
                            np.asarray([1, 2, 1, 3, 1, 2][:size], np.int32), [state])
        result.append(q.Transition(episode, 0, 0, 0, 0., 0.))
    return result


def sampler_at(step):
    rng = np.random.default_rng(2026100411)
    order, position = np.empty(0, np.int64), 0
    for _ in range(step):
        _, order, position = run.sampler(rng, order, position)
    return rng, order, position


def update(model, rng, order, position, data):
    chosen, order, position = run.sampler(rng, order, position)
    loss, gradient = obj.network_gradients(model, q.Batch([data[int(i)] for i in chosen]))
    norm = model.adam(gradient, .0003, 10.)
    obj.assert_network_invariants(model)
    return chosen, order, position, float(loss), float(norm)


def parent_snapshot(folder, model, rng, order, position):
    folder.mkdir()
    binding = {'schema': 'JOINT12K-SYNTHETIC-PARENT', 'artificialCounter': True, 'realRowsRead': 0}
    path = folder / f'parent-{model.updates:05d}.npz'
    run.checkpoint(path, model, rng, order, position, binding)
    receipt = path.with_suffix('.json')
    run.write_once(receipt, {'binding': binding, 'checkpoint': run.pin(path), 'previousReceipt': None})
    return {'checkpoint': run.pin(path), 'receipt': run.pin(receipt), 'binding': binding}


def anchor_chain(folder, parent, config):
    folder.mkdir(); (folder / 'checkpoints').mkdir()
    binding = {'schema': 'JOINT12K-SYNTHETIC-CONTINUATION', 'settings': config, 'parent': parent}
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
    receipt_path = run.checkpoint_path(split_dir, 10001).with_suffix('.json')
    altered = run.read(receipt_path)
    altered['sampledIndices'] = list(reversed(altered['sampledIndices']))
    receipt_path.write_text(json.dumps(altered), encoding='utf-8')  # Deliberate synthetic tamper.
    wrong_sample = reject(lambda: run.verify_chain(split_dir, run.initialize(), np.random.default_rng(0), binding, config), 'Update sampling')
    for key in ('checkpoint', 'receipt'):
        run.check_pin(parent[key])
    return {'artificialStartingCounter': 9999, 'historicalTrainingClaim': False,
            'verifiedNumericSequence': [9999, 10000, 10001], 'resumeAt10000BitExact': True,
            'parametersAdamSamplerAllVerified': True, 'pendingIntentRejected': pending,
            'missingReceiptRejected': missing, 'failedRunRejected': failed,
            'changedSampleReceiptRejected': wrong_sample, 'parentPinsUnchanged': True}


class NoScoringModel:
    def __init__(self, step):
        self.updates = step

    def forward(self, *_args, **_kwargs):
        raise AssertionError('Saved metric operation attempted learned scoring')


class NoScoringData:
    rows = [{}] * 64

    def batch(self, *_args, **_kwargs):
        raise AssertionError('Saved metric operation attempted to construct a model batch')


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


def prior_proofs():
    objective_path = run.ROOT / 'build/analysis/indrill-capacity-v1/setup/synthetic-checks-first.json'
    objective = run.read(objective_path)
    assert objective['checks']['status'] == 'PASS' and objective['checks']['syntheticOnly'] is True
    assert objective['checks']['weightCoordinates'] == 75 and objective['checks']['valueHeadAndMomentsZero'] is True
    for entry in objective['pins']:
        run.check_pin(entry)
    continuation_path = run.ROOT / 'build/analysis/indrill-build64-12k-v1/setup/checks.json'
    continuation = run.read(continuation_path)
    assert continuation['passed'] is True and continuation['syntheticOnly'] is True
    assert continuation['environment'] == run.environment()
    for source, digest in continuation['sourcePins'].items():
        run.check_pin({'path': source, 'sha256': digest})
    for entry in continuation['runtimePins'] + continuation['priorProofs']:
        run.check_pin(entry)
    return [run.pin(objective_path), run.pin(continuation_path)]


def objective_checks():
    assert obj.loss_gradients is obj.capacity.loss_gradients
    assert obj.output_objective is obj.capacity.output_objective
    assert obj.fresh_model is obj.capacity.fresh_model
    assert Path(obj.loss_gradients.__code__.co_filename).resolve() == (run.ROOT / 'tools/indrill/capacity_v1/subset_objective.py').resolve()
    assert obj.FROZEN_HEADS == ('WV', 'BV') and obj.UPDATES == 12000
    candidate, original = run.initialize(), obj.capacity.fresh_model(2026100401)
    same_models(candidate, original)
    ink_initial = {name: candidate.p[name].copy() for name in ('WI', 'BI')}
    data = items(); rng, order, position = sampler_at(0)
    for step in range(1, 6):
        chosen, order, position = run.sampler(rng, order, position)
        batch = q.Batch([data[int(i)] for i in chosen])
        loss, gradient = obj.network_gradients(candidate, batch)
        original_loss, per_head, original_gradient = obj.capacity.loss_gradients(original, batch)
        assert bits(loss) == bits(original_loss) and per_head.shape == (16, 2)
        same_arrays(gradient, original_gradient)
        assert all(not np.any(gradient[name]) for name in ('WV', 'BV'))
        assert np.any(gradient['WI']) and np.any(gradient['BI'])
        assert bits(candidate.adam(gradient, .0003, 10.)) == bits(original.adam(original_gradient, .0003, 10.))
        same_models(candidate, original)
        obj.assert_network_invariants(candidate)
        assert candidate.updates == step
    for name in ('WI', 'BI'):
        assert candidate.p[name].tobytes() != ink_initial[name].tobytes()
        assert np.any(candidate.m[name]) and np.any(candidate.v[name])
    for name in ('WV', 'BV'):
        assert all(not np.any(group[name]) for group in (candidate.p, candidate.m, candidate.v))
    cap = run.initialize(); cap.updates = 12000  # Artificial invariant fixture only.
    obj.assert_network_invariants(cap); cap.updates = 12001
    over_cap = reject(lambda: obj.assert_network_invariants(cap), 'update count')
    forbidden = run.initialize(); forbidden.m['WV'][0] = .25
    value_guard = reject(lambda: obj.assert_network_invariants(forbidden), 'value-head')
    return {'immutableJointFunctionsReusedByIdentity': True, 'priorJointDerivativeProofReused': True,
            'initializerAndFirstFiveLossGradientNormParameterMomentUpdatesBitExact': True,
            'syntheticUpdatesPerComparisonModel': 5, 'inkParametersAndMomentsTrain': True,
            'valueParametersMomentsAndGradientsStayZero': True, 'completed12000CounterAccepted': True,
            'above12000Rejected': over_cap, 'changedValueMomentRejected': value_guard}


def sampler_checks():
    rng, order, position = sampler_at(0)
    old_rng, old_order, old_position = sampler_at(0)
    resumed_rng, resumed_order, resumed_position = sampler_at(2000)
    total, extra = np.zeros(64, np.int64), np.zeros(64, np.int64)
    for step in range(1, 12001):
        chosen, order, position = run.sampler(rng, order, position)
        if old_position == len(old_order):
            old_order, old_position = old_rng.permutation(64), 0
        expected = old_order[old_position:old_position + 16]; old_position += 16
        assert chosen.tobytes() == expected.tobytes()
        same_sampler(rng, old_rng, order, old_order, position, old_position)
        total[chosen] += 1
        if step > 2000:
            other, resumed_order, resumed_position = run.sampler(resumed_rng, resumed_order, resumed_position)
            assert chosen.tobytes() == other.tobytes()
            same_sampler(rng, resumed_rng, order, resumed_order, position, resumed_position)
            extra[chosen] += 1
    assert total.tolist() == [3000] * 64 and extra.tolist() == [2500] * 64
    return {'samplerOnlyTotalUpdates': 12000, 'totalVisits': int(total.sum()), 'visitsPerObservation': 3000,
            'continuedUpdates': 10000, 'continuedVisits': int(extra.sum()), 'continuedVisitsPerObservation': 2500,
            'fullAndRestoredSamplerBitExact': True, 'modelUpdates': 0}


def immutable_parent_checks(root):
    root.mkdir(); data = items()
    direct = run.initialize(); direct.updates = 1999  # Artificial state, not a learned 1,999-update prefix.
    rng, order, position = sampler_at(1999)
    _, order, position, _, _ = update(direct, rng, order, position, data)
    assert direct.updates == 2000 and position == 64
    assert np.any(direct.m['WI']) and np.any(direct.m['BI'])
    parent = parent_snapshot(root / 'parent', direct, rng, order, position)
    resumed, resumed_rng = run.initialize(), np.random.default_rng(0)
    resumed_order, resumed_position = run.restore_parent(resumed, resumed_rng, parent, 64, 2000)
    same_models(direct, resumed)
    same_sampler(rng, resumed_rng, order, resumed_order, position, resumed_position)
    for _ in range(3):
        chosen, order, position, loss, norm = update(direct, rng, order, position, data)
        other, resumed_order, resumed_position, other_loss, other_norm = update(resumed, resumed_rng, resumed_order, resumed_position, data)
        assert chosen.tobytes() == other.tobytes() and bits(loss) == bits(other_loss) and bits(norm) == bits(other_norm)
        same_models(direct, resumed)
        same_sampler(rng, resumed_rng, order, resumed_order, position, resumed_position)
    assert resumed.updates == 2003
    for name in ('WI', 'BI'):
        assert np.any(resumed.m[name]) and np.any(resumed.v[name])
    for entry in (parent['checkpoint'], parent['receipt']):
        run.check_pin(entry)
    refused = reject(lambda: run.checkpoint(run.absolute(parent['checkpoint']['path']), resumed,
                                           resumed_rng, resumed_order, resumed_position, parent['binding']), 'File exists')
    run.check_pin(parent['checkpoint'])
    return {'artificialStartingCounter': 1999, 'historical1999TrainingClaim': False,
            'actualSyntheticUpdateToParent': 1, 'continuationUpdatesPerPath': 3,
            'fullParametersInkAndValueAdamRngOrderPositionRestoredBitExact': True,
            'continuousAndResumed2001Through2003BitExact': True, 'parentPinsUnchanged': True,
            'crossesEpochBoundaryFromPosition64': True, 'parentOverwriteRejected': refused}


def metric_and_source_guards(root):
    root.mkdir()
    model = run.initialize(); model.updates = 2000  # Artificial untrained metric-binding fixture.
    rng, order, position = sampler_at(2000)
    parent = parent_snapshot(root / 'parent', model, rng, order, position)
    baseline_source = root / 'parent-metric.json'
    run.write_once(baseline_source, synthetic_metric(parent['checkpoint']))
    parent_metric_pin = run.pin(baseline_source)
    source_rows = [dict(row, size=2, costs=[1, 1], legalBuild=[True, True], legalInk=[True], acceptedPaint=0)
                   for row in run.read(baseline_source)['rows']]
    folder = root / 'inherited'
    _, _, _, _, binding = anchor_chain(folder, parent, dict(run.SETTINGS))
    inherited = run.inherit_metrics(folder, 2000, binding, parent_metric_pin, source_rows)
    before_repeat = {p.relative_to(folder).as_posix(): run.digest(p) for p in folder.rglob('*') if p.is_file()}
    assert {k: v for k, v in inherited.items() if k != 'checkpoint'} == {
        k: v for k, v in run.read(baseline_source).items() if k != 'checkpoint'}
    assert inherited['checkpoint'] == run.pin(run.checkpoint_path(folder, 2000))
    assert run.inherit_metrics(folder, 2000, binding, parent_metric_pin, source_rows) == inherited
    assert before_repeat == {p.relative_to(folder).as_posix(): run.digest(p) for p in folder.rglob('*') if p.is_file()}
    parent_metadata = root / 'synthetic-parent-metadata.json'
    run.write_once(parent_metadata, {'syntheticOnly': True, 'role': 'Start-proof field-binding fixture'})
    metadata_pin = run.pin(parent_metadata)
    fixture_freeze = {'parent': dict(parent, freeze=metadata_pin, evidence=metadata_pin, chain=metadata_pin,
                                    auditedPinsCount=2, metrics=parent_metric_pin)}
    missing_start = reject(lambda: run.validate_start(folder, fixture_freeze, binding, None), 'Missing/different starting proof')
    run.write_once(folder / 'start-proof.json', run.start_proof_value(folder, fixture_freeze, binding))
    run.validate_start(folder, fixture_freeze, binding, source_rows)
    proof_pin = run.pin(folder / 'start-proof.json')
    wrong_start = reject(lambda: run.validate_start(folder, fixture_freeze, dict(binding, schema='wrong'), None), 'Missing/different starting proof')
    run.check_pin(proof_pin)
    starting_score = reject(lambda: run.record_metrics(folder, NoScoringModel(2000), NoScoringData(), binding), 'must be inherited')
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
    run.write_once(run.metric_paths(partial_inherit, 2000)[1], {'syntheticInterruptedInheritance': True})
    inherited_guard = reject(lambda: run.inherit_metrics(partial_inherit, 2000, binding, parent_metric_pin), 'Interrupted metrics')
    changed_ink = json.loads(json.dumps(inherited))
    changed_ink['rows'][0]['inkExact'] = changed_ink['rows'][0]['jointExact'] = 0
    changed_ink['jointExactCount'] = 63
    changed_ink['summary'] = run.summarize(changed_ink['rows'])
    ink_guard = reject(lambda: run.validate_metric_payload(changed_ink, 2000, inherited['checkpoint'], 64, source_rows),
                       'Metric action/loss summary')
    changed_loss = json.loads(json.dumps(inherited))
    changed_loss['rows'][0]['jointNll'] += 1.
    changed_loss['summary'] = run.summarize(changed_loss['rows'])
    loss_guard = reject(lambda: run.validate_metric_payload(changed_loss, 2000, inherited['checkpoint'], 64, source_rows),
                        'Metric action/loss summary')
    changed_sources = json.loads(json.dumps(source_rows)); changed_sources[0]['targetInk'] = 0
    label_guard = reject(lambda: run.validate_metric_payload(inherited, 2000, inherited['checkpoint'], 64, changed_sources),
                         'Metric source/label differs')
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
            'changedInkExactRejectedDespiteConsistentAggregates': ink_guard,
            'changedJointNllRejectedDespiteConsistentAggregates': loss_guard,
            'changedExpectedLabelRejected': label_guard,
            'changedSyntheticSourceRejected': changed_source, 'parentCheckpointReceiptAndMetricUnchanged': True}


def synthetic_metric(parent_pin):
    # Handwritten saved metric fixture, never a learned prediction or fit claim.
    rows = [{'observationId': 'SYNTHETIC-' + str(i), 'mapHash': 'synthetic-map-' + str(i // 4),
             'gameId': 'SYNTHETIC-GAME-' + str(i // 4), 'turn': 0, 'inputSha256': f'{i:064x}',
             'cohortTags': ['old40'], 'teacherCardinality': 0, 'costPattern': 'EMPTY',
             'turnBand': '0..19', 'teacherSeat': i % 2, 'hadSkippedActions': False,
             'targetCells': [], 'predictedCells': [], 'targetInk': -1, 'predictedInk': -1,
             'buildExact': 1, 'inkExact': 1, 'jointExact': 1,
             'cellPrecision': 1., 'cellRecall': 1., 'predictedPaint': 0, 'teacherPaint': 0,
             'buildNll': 1., 'inkNll': 2., 'jointNll': 3.} for i in range(64)]
    return {'stage': 'A', 'updates': 2000, 'checkpoint': parent_pin, 'rows': rows,
            'observations': 64, 'jointExactCount': 64, 'summary': run.summarize(rows),
            'precisionRecallConvention': 'Both empty: precision=recall=1. Exactly one empty: both=0.',
            'collisionSummary': run.teacher.collision_summary(rows, list(range(64)))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = run.absolute(args.out)
    setup = (run.OUTPUT / 'setup').resolve(); proof_path = setup / 'checks.json'
    run.require(out.is_relative_to(setup) and out != setup and not out.exists() and not proof_path.exists(),
                'Choose a new joint fixture directory; preserve completed proof')
    original_settings = json.dumps(run.SETTINGS, sort_keys=True)
    paths = set()
    for name in ('joint64_12k_v1', 'build64_12k_v1', 'build64_long_v1', 'build64_v1', 'fit_one_v1', 'capacity_v1'):
        paths.update((run.ROOT / 'tools/indrill' / name).glob('*.py'))
    paths.update((Path(q.__file__), Path(obj.v2.__file__)))
    sources = {run.pin(path)['path']: run.pin(path)['sha256'] for path in sorted(paths)}
    runtime = [run.pin(path) for path in sorted(run.runtime_files())]
    report = {'schema': 'indrill-joint64-12k-checks-v1', 'syntheticOnly': True,
              'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'sourcePins': sources,
              'runtimePins': runtime, 'environment': run.environment(), 'priorProofs': prior_proofs(),
              'realRowsRead': 0, 'realCandidateFitRuns': 0,
              'limitations': ['High-counter states and saved metric payloads are artificial fixtures, not learned results.',
                              'The original joint derivative proof is reused through function identity and pinned sources.',
                              'No real parent restoration, preparation, training or learned evaluation is run.']}
    out.mkdir(parents=True)
    run.write_once(out / 'intent.json', report)
    try:
        assert report['environment']['threadEnvironment'] == dict.fromkeys(
            ('OPENBLAS_NUM_THREADS', 'MKL_NUM_THREADS', 'OMP_NUM_THREADS'), '1')
        report['objective'] = objective_checks()
        report['sampling'] = sampler_checks()
        report['parentContinuation'] = immutable_parent_checks(out / 'parent-continuation')
        report['numericCheckpointChain'] = numeric_chain_checks(out / 'numeric-chain')
        report['metricAndSourceGuards'] = metric_and_source_guards(out / 'guards')
        report['stopGuard'] = stop_order_guard()
        assert json.dumps(run.SETTINGS, sort_keys=True) == original_settings
        assert run.SETTINGS == {'modelSeed': 2026100401, 'samplingSeed': 2026100411,
            'startStep': 2000, 'updates': 12000, 'batch': 16, 'observations': 64,
            'metricsAt': [2000, 4000, 6000, 8000, 10000, 12000]}
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
