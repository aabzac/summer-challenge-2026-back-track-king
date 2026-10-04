"""Synthetic checks for the batched build ablation and its persistence boundary.

No real dataset is loaded and no production entry point is called. Existing
subset/one-row proofs are checked by pin, rather than rerunning their test suites.
"""
import argparse
import copy
from datetime import datetime, timezone
import json
from pathlib import Path
import sys
from types import SimpleNamespace
from unittest.mock import patch

sys.dont_write_bytecode = True
import run_fit as run

obj, np, q = run.obj, run.np, run.q


def synthetic_items(count=64):
    result = []
    for index in range(count):
        width, height = (2, 2) if index % 2 == 0 else (3, 2)
        size = width * height
        rng = np.random.default_rng(614021 + index)
        features = rng.normal(0, .2, (2, size, 64)).astype(np.float32)
        legal = np.ones(size, bool); legal[2] = False
        target = (0, 1) if index % 3 else ()
        state = q.State(features, legal, np.asarray([1, 1], bool),
                        (target, ()), (index % 2, -1), (0., 0.), False)
        episode = q.Episode('BUILD64-SYNTHETIC-' + str(index), width, height, 2,
                            np.asarray([i % 2 for i in range(size)], np.int32),
                            np.asarray([1, 2, 1, 3, 1, 2][:size], np.int32), [state])
        result.append(q.Transition(episode, 0, 0, 0, 0., 0.))
    return result


def padded(batch):
    result = copy.deepcopy(batch)
    result.x = np.pad(batch.x, ((0, 0), (0, 2), (0, 0)), constant_values=123.)
    for name in ('mask', 'legal_build', 'regions'):
        setattr(result, name, np.pad(getattr(batch, name), ((0, 0), (0, 2))))
    for name in ('costs', 'degrees'):
        setattr(result, name, np.pad(getattr(batch, name), ((0, 0), (0, 2)), constant_values=1))
    for name in ('neighbors', 'neighbor_mask'):
        setattr(result, name, np.pad(getattr(batch, name), ((0, 0), (0, 2), (0, 0))))
    result.region_sizes = np.pad(batch.region_sizes, ((0, 0), (0, 1)), constant_values=1)
    result.legal_ink = np.pad(batch.legal_ink, ((0, 0), (0, 1)))
    return result


def gradients_equal(first, second, *, exact=False):
    assert set(first) == set(second)
    for name in first:
        if exact:
            np.testing.assert_array_equal(first[name], second[name], err_msg=name)
        else:
            np.testing.assert_allclose(first[name], second[name], atol=2e-7, rtol=2e-5, err_msg=name)


def objective_checks():
    items = synthetic_items(16); batch = q.Batch(items)
    model = obj.network_initializer()
    old = obj.capacity.fresh_model(obj.MODEL_SEED)
    for group in ('p', 'm', 'v'):
        gradients_equal(getattr(model, group), getattr(old, group), exact=True)
        for name in model.p:
            assert getattr(model, group)[name].tobytes() == getattr(old, group)[name].tobytes()
    assert model.updates == old.updates == 0
    for name, tensor in model.p.items():
        if name.startswith('W') and name not in obj.FROZEN_HEADS:
            tensor *= .08
    model.p['B0'].fill(1.); model.p['B0'][:3] = -1.
    model.p['B1'].fill(1.); model.p['B1'][:3] = -1.
    build, ink, _ = model.forward(batch)
    loss, per_row, db = obj.output_objective(build, batch)
    _, old_per_head, old_db, _ = obj.capacity.output_objective(build, ink, batch)
    np.testing.assert_array_equal(per_row, old_per_head[:, 0])
    np.testing.assert_array_equal(db, old_db)
    assert loss == float(old_per_head[:, 0].mean()) and not np.any(db[~batch.legal_build])
    with patch.object(model, 'forward', wraps=model.forward) as counted:
        actual_loss, gradient = obj.network_gradients(model, batch)
        assert counted.call_count == 1 and counted.call_args.args[0] is batch
    assert actual_loss == loss
    # Use the frozen joint adapter with its already-validated ink derivative set
    # to zero before backprop. This checks the original batch numeric convention.
    original_output = obj.capacity.output_objective
    def build_component(build_values, ink_values, same_batch):
        _, heads, build_derivative, ink_derivative = original_output(build_values, ink_values, same_batch)
        heads[:, 1] = 0.; ink_derivative.fill(0.)
        return float(heads[:, 0].mean()), heads, build_derivative, ink_derivative
    with patch.object(obj.capacity, 'output_objective', side_effect=build_component):
        old_loss, _, old_gradient = obj.capacity.loss_gradients(model, batch)
    assert actual_loss == old_loss
    gradients_equal(gradient, old_gradient, exact=True)
    one = q.Batch(items[:1])
    one_loss, one_gradient = obj.network_gradients(model, one)
    single_loss, single_gradient = obj.single.network_gradients(model, one)
    assert one_loss == single_loss
    gradients_equal(one_gradient, single_gradient, exact=True)
    twice_loss, twice_gradient = obj.network_gradients(model, q.Batch(items + items))
    np.testing.assert_allclose(twice_loss, loss, atol=1e-8, rtol=1e-8)
    gradients_equal(twice_gradient, gradient)
    extra = padded(batch)
    extra_loss, extra_gradient = obj.network_gradients(model, extra)
    np.testing.assert_allclose(extra_loss, loss, atol=1e-7, rtol=1e-7)
    gradients_equal(extra_gradient, gradient)
    altered = copy.deepcopy(batch)
    altered.inks[:] = [-1] * len(items); altered.legal_ink.fill(False)
    altered_loss, altered_gradient = obj.network_gradients(model, altered)
    assert altered_loss == loss
    gradients_equal(altered_gradient, gradient, exact=True)
    coordinates, max_error = 0, 0.
    for name, tensor in model.p.items():
        if name in obj.FROZEN_HEADS:
            assert not np.any(gradient[name]); continue
        for flat in np.unique(np.linspace(0, tensor.size - 1, min(3, tensor.size), dtype=int)):
            index = np.unravel_index(flat, tensor.shape); original = tensor[index].copy()
            tensor[index] = original + np.float32(.01); upper = float(tensor[index])
            plus = obj.output_objective(model.forward(extra)[0], extra)[0]
            tensor[index] = original - np.float32(.01); lower = float(tensor[index])
            minus = obj.output_objective(model.forward(extra)[0], extra)[0]
            tensor[index] = original
            numerical, analytical = (plus - minus) / (upper - lower), float(extra_gradient[name][index])
            max_error = max(max_error, abs(numerical - analytical)); coordinates += 1
            np.testing.assert_allclose(numerical, analytical, atol=2e-5, rtol=.015, err_msg=f'{name}{index}')
    heads = {name: model.p[name].copy() for name in obj.FROZEN_HEADS}
    model.adam(gradient, obj.LEARNING_RATE, obj.CLIP_NORM)
    obj.assert_network_invariants(model)
    for name in obj.FROZEN_HEADS:
        np.testing.assert_array_equal(model.p[name], heads[name])
        assert not np.any(model.m[name]) and not np.any(model.v[name])
    assert all(np.isfinite(a).all() for a in model.forward(extra))
    return {'batchSize': 16, 'initializerMatchesOriginalCapacity': True,
            'outputDerivativeMatchesOriginalBuildComponentArrayExact': True,
            'backpropMatchesOriginalWithZeroInkDerivativeArrayExact': True,
            'singleBatchedForward': True, 'oneRowMatchesFrozenFitOne': True,
            'duplicatingBatchPreservesMeanGradient': True, 'paddingAndInkLabelInvariance': True,
            'finiteDifferenceCoordinates': coordinates, 'maxWeightGradientAbsError': max_error,
            'frozenHeadParametersAndMomentsPreserved': True, 'finiteOutputs': True}


def reject(callback, fragment):
    try:
        callback()
    except (ValueError, FileNotFoundError, FileExistsError) as error:
        assert fragment.lower() in str(error).lower(), str(error)
        return {'errorType': type(error).__name__, 'reason': str(error)}
    raise AssertionError('Expected synthetic rejection: ' + fragment)


def same_state(first, second, first_rng, second_rng, first_order, second_order, first_position, second_position):
    assert first.updates == second.updates and first_position == second_position
    assert first_rng.bit_generator.state == second_rng.bit_generator.state
    assert first_order.dtype == second_order.dtype and first_order.tobytes() == second_order.tobytes()
    for group in ('p', 'm', 'v'):
        assert set(getattr(first, group)) == set(getattr(second, group))
        for name in first.p:
            a, b = getattr(first, group)[name], getattr(second, group)[name]
            assert a.dtype == b.dtype and a.shape == b.shape and a.tobytes() == b.tobytes(), group + ':' + name


def prior_proofs():
    paths = [run.ROOT / 'build/analysis/indrill-fit-one-v1/setup/checks.json',
             run.ROOT / 'build/analysis/indrill-fit-one-v1/setup/runner-checks.json']
    pins = []
    for path in paths:
        result = run.read(path)
        assert result['passed'] is True and result['syntheticOnly'] is True
        for source, digest in result['sourcePins'].items():
            run.check_pin({'path': source, 'sha256': digest})
        pins.append(run.pin(path))
    path = run.ROOT / 'build/analysis/indrill-capacity-v1/setup/synthetic-checks-first.json'
    result = run.read(path)
    assert result['checks']['status'] == 'PASS' and result['checks']['syntheticOnly'] is True
    for entry in result['pins']:
        run.check_pin(entry)
    pins.append(run.pin(path))
    return pins


def sampling_check():
    actual_rng = np.random.default_rng(2026100411)
    old_rng = np.random.default_rng(2026100411)
    order = np.empty(0, np.int64); old_order = np.empty(0, np.int64)
    position = old_position = 0; visits = np.zeros(64, np.int64)
    for _ in range(2000):
        chosen, order, position = run.sampler(actual_rng, order, position, 64, 16)
        # Literal original capacity epoch/permutation schedule, with synthetic
        # observation indices only; no corpus or model is consulted.
        if old_position == len(old_order):
            old_order, old_position = old_rng.permutation(64), 0
        expected = old_order[old_position:old_position + 16]
        old_position += 16
        np.testing.assert_array_equal(chosen, expected)
        np.testing.assert_array_equal(order, old_order)
        assert position == old_position and actual_rng.bit_generator.state == old_rng.bit_generator.state
        visits[chosen] += 1
    assert visits.tolist() == [500] * 64
    return {'samplerOnlyUpdates': 2000, 'originalPermutationSequenceArrayExact': True,
            'rngStateAndPositionMatchAtEveryStep': True, 'visitsPerSyntheticObservation': 500,
            'sampleVisits': int(visits.sum()), 'modelUpdatesInThisCheck': 0}


def initial_chain(folder, binding, config, *, wrong_rng=False):
    folder.mkdir(); (folder / 'checkpoints').mkdir()
    model, rng = run.initialize(), np.random.default_rng(config['samplingSeed'])
    order, position = np.empty(0, np.int64), 0
    if wrong_rng:
        rng.random()
    path = folder / 'checkpoints/step-0000.npz'
    run.checkpoint(path, model, rng, order, position, binding)
    run.write_once(path.with_suffix('.json'), {'binding': binding, 'checkpoint': run.pin(path), 'previousReceipt': None})
    return model, rng, order, position


def update_once(folder, model, rng, order, position, items, binding, config, *, wrong_order=False):
    chosen, order, position = run.sampler(rng, order, position, config['observations'], config['batch'])
    step = model.updates + 1
    previous = folder / 'checkpoints' / f'step-{step - 1:04d}.npz'
    path = folder / 'checkpoints' / f'step-{step:04d}.npz'
    previous_receipt = run.pin(previous.with_suffix('.json'))
    intent = folder / f'update-{step:04d}-intent.json'
    run.write_once(intent, {'binding': binding, 'fromCheckpoint': run.pin(previous),
                           'previousReceipt': previous_receipt, 'sampledIndices': chosen.tolist()})
    loss, gradient = obj.network_gradients(model, q.Batch([items[int(i)] for i in chosen]))
    norm = model.adam(gradient, obj.LEARNING_RATE, obj.CLIP_NORM)
    assert np.isfinite(loss) and np.isfinite(norm) and model.updates == step
    obj.assert_network_invariants(model)
    if wrong_order:
        order = order[::-1].copy()
    run.checkpoint(path, model, rng, order, position, binding)
    run.write_once(path.with_suffix('.json'), {'binding': binding, 'checkpoint': run.pin(path),
        'previousReceipt': previous_receipt, 'updateIntent': run.pin(intent),
        'sampledIndices': chosen.tolist(), 'loss': float(loss), 'unclippedGradientNorm': float(norm)})
    return order, position


def corrupt_snapshot(source, target, key, value):
    with np.load(source, allow_pickle=False) as saved:
        payload = {name: saved[name].copy() for name in saved.files}
    payload[key].flat[0] = value
    with target.open('xb') as stream:
        np.savez_compressed(stream, **payload)


def persistence_checks(root):
    root.mkdir()
    config = {'observations': 64, 'batch': 16, 'samplingSeed': 2026100411, 'updates': 5}
    binding = {'schema': 'indrill-build64-SYNTHETIC-checkpoint-v1', 'syntheticOnly': True, 'settings': config}
    items = synthetic_items()
    full_dir, resume_dir = root / 'uninterrupted', root / 'resume-boundary'
    full, full_rng, full_order, full_position = initial_chain(full_dir, binding, config)
    for _ in range(5):
        full_order, full_position = update_once(full_dir, full, full_rng, full_order, full_position, items, binding, config)
    first, first_rng, first_order, first_position = initial_chain(resume_dir, binding, config)
    for _ in range(2):
        first_order, first_position = update_once(resume_dir, first, first_rng, first_order, first_position, items, binding, config)
    resumed, resumed_rng = run.initialize(), np.random.default_rng(config['samplingSeed'])
    resumed_order, resumed_position = run.verify_chain(resume_dir, resumed, resumed_rng, binding, config)
    same_state(first, resumed, first_rng, resumed_rng, first_order, resumed_order, first_position, resumed_position)
    for _ in range(3):
        resumed_order, resumed_position = update_once(resume_dir, resumed, resumed_rng, resumed_order, resumed_position, items, binding, config)
    same_state(full, resumed, full_rng, resumed_rng, full_order, resumed_order, full_position, resumed_position)
    checked, checked_rng = run.initialize(), np.random.default_rng(config['samplingSeed'])
    checked_order, checked_position = run.verify_chain(resume_dir, checked, checked_rng, binding, config)
    same_state(full, checked, full_rng, checked_rng, full_order, checked_order, full_position, checked_position)
    first_path = resume_dir / 'checkpoints/step-0000.npz'
    before = run.pin(first_path)
    write_once = reject(lambda: run.checkpoint(first_path, checked, checked_rng, checked_order, checked_position, binding), 'File exists')
    assert run.pin(first_path) == before
    stale = reject(lambda: run.restore(first_path, run.initialize(), np.random.default_rng(0), dict(binding, schema='wrong'), 64), 'binding')
    partial_dir = root / 'pending-intent'
    initial_chain(partial_dir, binding, config)
    run.write_once(partial_dir / 'update-0001-intent.json', {'syntheticInterruptedBeforeCheckpoint': True})
    partial = reject(lambda: run.verify_chain(partial_dir, run.initialize(), np.random.default_rng(0), binding, config), '')
    failed_dir = root / 'recorded-failure'
    initial_chain(failed_dir, binding, config)
    run.write_once(failed_dir / 'failure.json', {'syntheticOnly': True, 'decision': 'TECHNICAL_FAILURE'})
    failed = reject(lambda: run.verify_chain(failed_dir, run.initialize(), np.random.default_rng(0), binding, config), 'failed run')
    rng_dir = root / 'wrong-rng'
    initial_chain(rng_dir, binding, config, wrong_rng=True)
    rng_failure = reject(lambda: run.verify_chain(rng_dir, run.initialize(), np.random.default_rng(0), binding, config), '')
    order_dir = root / 'wrong-valid-permutation'
    bad, bad_rng, bad_order, bad_position = initial_chain(order_dir, binding, config)
    update_once(order_dir, bad, bad_rng, bad_order, bad_position, items, binding, config, wrong_order=True)
    order_failure = reject(lambda: run.verify_chain(order_dir, run.initialize(), np.random.default_rng(0), binding, config), '')
    nonfinite_path, frozen_path = root / 'nonfinite.npz', root / 'changed-frozen-moment.npz'
    corrupt_snapshot(first_path, nonfinite_path, 'W0', np.nan)
    corrupt_snapshot(first_path, frozen_path, 'adam_m_WI', .25)
    nonfinite = reject(lambda: run.restore(nonfinite_path, run.initialize(), np.random.default_rng(0), binding, 64), '')
    frozen = reject(lambda: run.restore(frozen_path, run.initialize(), np.random.default_rng(0), binding, 64), 'moment')
    return {'updatesPerCompletedPath': 5, 'resumeAfterUpdate': 2, 'crossesEpochBoundary': True,
            'parametersAdamRngOrderPositionBitExactAfterResume': True, 'completeSavedChainVerified': True,
            'writeOnceRejected': write_once, 'staleBindingRejected': stale, 'pendingIntentRejected': partial,
            'recordedFailureRejected': failed,
            'changedRngRejected': rng_failure, 'wrongValidPermutationRejected': order_failure,
            'nonfiniteStateRejected': nonfinite, 'frozenMomentChangeRejected': frozen}


def metric_guard_checks(root):
    root.mkdir()
    binding = {'syntheticOnly': True, 'settings': {'observations': 64}}
    result = []
    for mask in range(7):
        folder = root / f'triplet-{mask}'; folder.mkdir()
        paths = [folder / ('metrics-0000' + suffix) for suffix in ('.json', '-intent.json', '-receipt.json')]
        for index, path in enumerate(paths):
            if mask & (1 << index):
                run.write_once(path, {'syntheticIncompleteTriplet': True})
        before = {p.name: run.digest(p) for p in folder.iterdir()}
        model = run.initialize()
        with patch.object(run, 'evaluate', side_effect=AssertionError('Missing metrics must not trigger scoring')) as evaluated, \
                patch.object(model, 'forward', side_effect=AssertionError('No learned forward during validation')) as forwarded, \
                patch.object(obj, 'network_gradients', side_effect=AssertionError('No gradient during validation')) as differentiated:
            validation = reject(lambda: run.validate_metrics(folder, 0, binding), 'Interrupted metrics')
            # All-absent is valid only for a never-started recording. Any partial
            # record must reject both validation and the normal recording path.
            recording = None
            if mask:
                recording = reject(lambda: run.record_metrics(folder, model, SimpleNamespace(rows=[]), binding), 'Interrupted metrics')
            assert evaluated.call_count == forwarded.call_count == differentiated.call_count == 0
        after = {p.name: run.digest(p) for p in folder.iterdir()}
        assert before == after
        result.append({'existingTripletMask': mask, 'validationRejected': validation,
                       'partialRecordingRejected': recording, 'learnedPredictions': 0, 'gradients': 0, 'writes': 0})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = run.absolute(args.out)
    setup = (run.OUTPUT / 'setup').resolve(); proof = setup / 'checks.json'
    run.require(out.is_relative_to(setup) and out != setup and not out.exists() and not proof.exists(),
                'Choose a new fixture directory under build64 setup; preserve completed proof')
    original_settings = json.dumps(run.SETTINGS, sort_keys=True)
    paths = set((run.ROOT / 'tools/indrill/build64_v1').glob('*.py'))
    paths.update((run.ROOT / 'tools/indrill/fit_one_v1').glob('*.py'))
    paths.update((run.ROOT / 'tools/indrill/capacity_v1').glob('*.py'))
    paths.update((Path(q.__file__), Path(obj.v2.__file__)))
    sources = {run.pin(path)['path']: run.pin(path)['sha256'] for path in sorted(paths)}
    runtime = [run.pin(path) for path in sorted(run.runtime_files())]
    report = {'schema': 'indrill-build64-checks-v1', 'syntheticOnly': True,
              'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'sourcePins': sources,
              'runtimePins': runtime, 'environment': run.environment(), 'priorProofs': prior_proofs(),
              'realRowsRead': 0, 'realCandidateFitRuns': 0,
              'limitations': ['Synthetic finite differences sample coordinates, not every input.',
                              'Short optimizer sequences check persistence, not fitting performance.',
                              'Frozen ink-head parameters do not freeze ink predictions as the trunk changes.']}
    out.mkdir(parents=True)
    run.write_once(out / 'intent.json', report)
    try:
        report['objective'] = objective_checks()
        report['sampling'] = sampling_check()
        report['persistence'] = persistence_checks(out / 'persistence')
        report['metricGuards'] = metric_guard_checks(out / 'metric-guards')
        assert json.dumps(run.SETTINGS, sort_keys=True) == original_settings
        assert run.SETTINGS['updates'] == 2000 and run.SETTINGS['batch'] == 16
        for path, digest in sources.items():
            run.check_pin({'path': path, 'sha256': digest})
        for entry in runtime + report['priorProofs']:
            run.check_pin(entry)
        assert run.environment() == report['environment']
        report['productionSettingsUnchanged'] = True
        report['passed'] = True
    except Exception as error:
        report['passed'] = False
        report['failure'] = {'type': type(error).__name__, 'message': str(error)}
        run.write_once(out / 'result.json', report)
        raise
    run.write_once(out / 'result.json', report)
    run.write_once(proof, report)
    print(json.dumps({'passed': True, 'syntheticOnly': True, 'result': run.pin(proof),
                      'fixtures': out.relative_to(run.ROOT).as_posix()}))


if __name__ == '__main__':
    main()
