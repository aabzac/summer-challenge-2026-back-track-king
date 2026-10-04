"""Synthetic implementation checks for the fixed longer build64 diagnostic.

The artificial high-step boundary is a persistence fixture, not 1,999 trained
updates. No real rows, production entry points, or old-module mutations occur.
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


def function_tree(path, name):
    tree = ast.parse(Path(path).read_text(encoding='utf-8-sig'))
    return ast.dump(next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == name),
                    include_attributes=False)


def reject(callback, fragment=''):
    try:
        callback()
    except (ValueError, FileNotFoundError, FileExistsError) as error:
        assert fragment.lower() in str(error).lower(), str(error)
        return {'errorType': type(error).__name__, 'reason': str(error)}
    raise AssertionError('Expected synthetic rejection: ' + fragment)


def synthetic_items(count=64):
    result = []
    for index in range(count):
        width, height = (2, 2) if index % 2 == 0 else (3, 2)
        size = width * height
        features = np.random.default_rng(614021 + index).normal(0, .2, (2, size, 64)).astype(np.float32)
        legal = np.ones(size, bool); legal[2] = False
        target = (0, 1) if index % 3 else ()
        state = q.State(features, legal, np.asarray([1, 1], bool),
                        (target, ()), (index % 2, -1), (0., 0.), False)
        episode = q.Episode('BUILD64-LONG-SYNTHETIC-' + str(index), width, height, 2,
                            np.asarray([i % 2 for i in range(size)], np.int32),
                            np.asarray([1, 2, 1, 3, 1, 2][:size], np.int32), [state])
        result.append(q.Transition(episode, 0, 0, 0, 0., 0.))
    return result


def arrays_identical(first, second):
    assert set(first) == set(second)
    for name, left in first.items():
        right = second[name]
        assert left.shape == right.shape and left.dtype == right.dtype and left.tobytes() == right.tobytes(), name


def models_identical(first, second):
    assert first.updates == second.updates
    for group in ('p', 'm', 'v'):
        arrays_identical(getattr(first, group), getattr(second, group))


def scalar_bits(value):
    return np.float64(value).tobytes()


def inherited_proof():
    path = run.ROOT / 'build/analysis/indrill-build64-v1/setup/checks.json'
    proof = run.read(path)
    assert proof['schema'] == 'indrill-build64-checks-v1' and proof['passed'] is True and proof['syntheticOnly'] is True
    assert proof['realRowsRead'] == proof['realCandidateFitRuns'] == 0
    assert proof['environment'] == run.environment()
    for source, digest in proof['sourcePins'].items():
        run.check_pin({'path': source, 'sha256': digest})
    for item in proof['runtimePins'] + proof['priorProofs']:
        run.check_pin(item)
    assert proof['objective']['finiteDifferenceCoordinates'] == 22
    assert proof['objective']['frozenHeadParametersAndMomentsPreserved'] is True
    assert len(proof['metricGuards']) == 7
    return run.pin(path)


def objective_checks():
    old = obj.original
    identical_functions = {}
    for name in ('output_objective', 'network_gradients'):
        expected = function_tree(old.__file__, name)
        assert function_tree(obj.__file__, name) == expected
        identical_functions[name] = hashlib.sha256(expected.encode()).hexdigest()
    expected = function_tree(old.single.__file__, 'assert_network_invariants')
    assert function_tree(obj.__file__, 'assert_network_invariants') == expected
    assert obj.UPDATES == 8000 and old.UPDATES == old.single.UPDATES == 2000
    assert obj.FROZEN_HEADS == old.FROZEN_HEADS and obj.MODEL_SEED == old.MODEL_SEED == 2026100401
    assert obj.LEARNING_RATE == old.LEARNING_RATE == .0003 and obj.CLIP_NORM == old.CLIP_NORM == 10.
    assert obj.subset_loss is old.subset_loss and obj.q is old.q and obj.v2 is old.v2
    candidate, original = obj.network_initializer(), old.network_initializer()
    models_identical(candidate, original)
    items = synthetic_items(); rng = np.random.default_rng(2026100411)
    order, position = np.empty(0, np.int64), 0
    for step in range(1, 6):
        chosen, order, position = run.sampler(rng, order, position)
        batch = q.Batch([items[int(index)] for index in chosen])
        new_loss, new_gradient = obj.network_gradients(candidate, batch)
        old_loss, old_gradient = old.network_gradients(original, batch)
        assert scalar_bits(new_loss) == scalar_bits(old_loss)
        arrays_identical(new_gradient, old_gradient)
        new_norm = candidate.adam(new_gradient, .0003, 10.)
        old_norm = original.adam(old_gradient, .0003, 10.)
        assert scalar_bits(new_norm) == scalar_bits(old_norm)
        models_identical(candidate, original)
        assert candidate.updates == step
        obj.assert_network_invariants(candidate); old.assert_network_invariants(original)
    # Only this artificial fixture's counter is assigned. Production code never
    # masks, resets or temporarily rewrites an update count to evade an invariant.
    boundary = obj.network_initializer(); boundary.updates = 8000
    obj.assert_network_invariants(boundary)
    boundary.updates = 8001
    rejected = reject(lambda: obj.assert_network_invariants(boundary), 'update count')
    return {'mathematicalFunctionAstSha256': identical_functions, 'invariantAstMatchesOldWithNewUpdateConstant': True,
            'finiteDifferenceProofReusedThroughAstAndSourceIdentity': True,
            'initialParametersAndMomentsBitExact': True, 'fiveUpdateLossGradientNormParametersAdamBitExact': True,
            'syntheticUpdatesPerComparisonModel': 5, 'newCap8000Accepted': True, 'aboveCapRejected': rejected,
            'oldModuleConstantsUnchanged': True}


def sampling_check():
    rng, old_rng = np.random.default_rng(2026100411), np.random.default_rng(2026100411)
    order, old_order = np.empty(0, np.int64), np.empty(0, np.int64)
    position = old_position = 0; visits = np.zeros(64, np.int64)
    for _ in range(8000):
        chosen, order, position = run.sampler(rng, order, position)
        if old_position == len(old_order):
            old_order, old_position = old_rng.permutation(64), 0
        expected = old_order[old_position:old_position + 16]; old_position += 16
        assert chosen.dtype == order.dtype == np.int64
        assert chosen.tobytes() == expected.tobytes() and order.tobytes() == old_order.tobytes()
        assert position == old_position and rng.bit_generator.state == old_rng.bit_generator.state
        visits[chosen] += 1
    assert visits.tolist() == [2000] * 64
    return {'samplerOnlyUpdates': 8000, 'sampleVisits': int(visits.sum()),
            'visitsPerSyntheticObservation': 2000, 'originalPermutationArithmeticExact': True, 'modelUpdates': 0}


def synthetic_update(model, rng, order, position, items):
    chosen, order, position = run.sampler(rng, order, position)
    loss, gradient = obj.network_gradients(model, q.Batch([items[int(index)] for index in chosen]))
    norm = model.adam(gradient, .0003, 10.)
    obj.assert_network_invariants(model)
    return order, position, loss, norm


def boundary_checks(root):
    root.mkdir()
    binding = {'schema': 'indrill-build64-long-SYNTHETIC-boundary-v1',
               'syntheticOnly': True, 'artificialStartingCounter': 1999,
               'priorTrainedUpdatesRepresented': 0, 'purpose': 'Persistence across the old invariant boundary'}
    model, rng = run.initialize(), np.random.default_rng(2026100411)
    model.updates = 1999  # Explicit artificial state, never accepted as a production chain.
    order, position = np.empty(0, np.int64), 0
    for _ in range(1999):
        _, order, position = run.sampler(rng, order, position)
    items = synthetic_items()
    start = root / 'artificial-step-1999.npz'
    run.checkpoint(start, model, rng, order, position, binding)
    order, position, _, _ = synthetic_update(model, rng, order, position, items)
    assert model.updates == 2000 and position == 64
    middle = root / 'artificial-step-2000.npz'
    run.checkpoint(middle, model, rng, order, position, binding)
    resumed, resumed_rng = run.initialize(), np.random.default_rng(0)
    resumed_order, resumed_position = run.restore(middle, resumed, resumed_rng, binding, 64)
    models_identical(model, resumed)
    assert rng.bit_generator.state == resumed_rng.bit_generator.state
    assert order.tobytes() == resumed_order.tobytes() and position == resumed_position
    order, position, loss, norm = synthetic_update(model, rng, order, position, items)
    resumed_order, resumed_position, resumed_loss, resumed_norm = synthetic_update(
        resumed, resumed_rng, resumed_order, resumed_position, items)
    assert scalar_bits(loss) == scalar_bits(resumed_loss) and scalar_bits(norm) == scalar_bits(resumed_norm)
    models_identical(model, resumed)
    assert model.updates == 2001 and position == resumed_position == 16
    assert order.tobytes() == resumed_order.tobytes() and rng.bit_generator.state == resumed_rng.bit_generator.state
    end = root / 'artificial-step-2001.npz'
    run.checkpoint(end, model, rng, order, position, binding)
    restored, restored_rng = run.initialize(), np.random.default_rng(0)
    restored_order, restored_position = run.restore(end, restored, restored_rng, binding, 64)
    models_identical(model, restored)
    assert order.tobytes() == restored_order.tobytes() and position == restored_position
    assert rng.bit_generator.state == restored_rng.bit_generator.state
    old_rejected = reject(lambda: obj.original.assert_network_invariants(restored), 'update count')
    obj.assert_network_invariants(restored)
    source = run.pin(middle)
    write_once = reject(lambda: run.checkpoint(middle, restored, rng, order, position, binding), 'File exists')
    assert run.pin(middle) == source
    stale = reject(lambda: run.restore(end, run.initialize(), np.random.default_rng(0), dict(binding, schema='wrong'), 64), 'binding')
    return {'artificialStartingCounter': 1999, 'priorTrainedUpdatesRepresented': 0,
            'updatesOnUninterruptedPath': 2, 'updatesAfterResume': 1,
            'lossNormParametersAdamRngOrderPositionBitExact': True,
            'restoreBeyondOld2000Cap': True,
            'oldInvariantStillRejects2001': old_rejected, 'writeOnceRejected': write_once,
            'staleBindingRejected': stale,
            'scope': 'Artificial high-counter snapshots only; not a simulated or trained 0..1999 prefix.'}


class NoScoringModel:
    updates = 0

    def forward(self, *_args, **_kwargs):
        raise AssertionError('Metric validation must not call learned forward')


class NoScoringData:
    rows = [{}] * 64

    def batch(self, *_args, **_kwargs):
        raise AssertionError('Metric validation must not construct a scoring batch')


def metric_and_source_guards(root):
    root.mkdir()
    old_runner = run.ROOT / 'tools/indrill/build64_v1/run_fit.py'
    for name in ('evaluate', 'validate_metrics', 'record_metrics'):
        function = getattr(run, name)
        assert Path(function.__code__.co_filename).resolve() == old_runner.resolve()
    binding = {'syntheticOnly': True, 'settings': {'observations': 64}}
    rejected = []
    for mask in range(7):
        folder = root / f'metric-triplet-{mask}'; folder.mkdir()
        for index, suffix in enumerate(('.json', '-intent.json', '-receipt.json')):
            if mask & (1 << index):
                run.write_once(folder / ('metrics-0000' + suffix), {'syntheticIncompleteTriplet': True})
        before = {path.name: run.digest(path) for path in folder.iterdir()}
        validation = reject(lambda: run.validate_metrics(folder, 0, binding), 'Interrupted metrics')
        recording = None
        if mask:
            recording = reject(lambda: run.record_metrics(folder, NoScoringModel(), NoScoringData(), binding), 'Interrupted metrics')
        assert before == {path.name: run.digest(path) for path in folder.iterdir()}
        rejected.append({'existingTripletMask': mask, 'validationRejected': validation,
                         'partialRecordingRejected': recording, 'writes': 0, 'modelCalls': 0})
    fixture = root / 'synthetic-source-pin-fixture.py'
    with fixture.open('x', encoding='utf-8') as stream:
        stream.write('# Synthetic source guard fixture\n')
    original_pin = run.pin(fixture)
    with fixture.open('a', encoding='utf-8') as stream:
        stream.write('# Deliberately changed synthetic bytes\n')
    changed = reject(lambda: run.check_pin(original_pin), 'Pinned bytes changed')
    return {'metricFunctionsReuseFrozenImplementation': True, 'incompleteMetricTriplets': rejected,
            'changedSyntheticSourceBytesRejected': changed, 'oldModulesMutated': False}


def checkpoint_variant(source, target, kind):
    with np.load(source, allow_pickle=False) as saved:
        values = {name: saved[name].copy() for name in saved.files}
    if kind == 'parameter':
        values['W0'].flat[0] = np.nextafter(values['W0'].flat[0], np.float32(np.inf))
    elif kind == 'moment':
        values['adam_m_WB'].flat[0] += np.float32(.25)
    elif kind == 'sampleOrder':
        values['sampleOrder'][[0, 1]] = values['sampleOrder'][[1, 0]]
    elif kind in ('rng', 'step'):
        metadata = json.loads(str(values['metadata']))
        if kind == 'rng':
            metadata['rngState']['state']['state'] += 1
        else:
            metadata['updates'] += 1
        values['metadata'] = np.asarray(json.dumps(metadata))
    else:
        raise ValueError(kind)
    with target.open('xb') as stream:
        np.savez_compressed(stream, **values)


def prefix_guard_checks(root):
    root.mkdir()
    model, rng = run.initialize(), np.random.default_rng(2026100411)
    order, position, loss, norm = synthetic_update(model, rng, np.empty(0, np.int64), 0, synthetic_items())
    first, second = root / 'new-prefix.npz', root / 'old-prefix.npz'
    run.checkpoint(first, model, rng, order, position, {'syntheticOnly': True, 'experiment': 'new'})
    run.checkpoint(second, model, rng, order, position, {'syntheticOnly': True, 'experiment': 'old'})
    new_receipt = {'loss': float(loss), 'unclippedGradientNorm': float(norm), 'sampledIndices': order[:16].tolist()}
    old_receipt = dict(new_receipt)
    run.compare_prefix_checkpoint(first, second, 1, new_receipt, old_receipt)
    failures = {}
    for kind in ('parameter', 'moment', 'sampleOrder', 'rng', 'step'):
        target = root / f'changed-{kind}.npz'
        checkpoint_variant(first, target, kind)
        failures[kind] = reject(lambda path=target: run.compare_prefix_checkpoint(path, second, 1, new_receipt, old_receipt))
    for key in ('loss', 'unclippedGradientNorm'):
        changed = dict(new_receipt)
        changed[key] = float(np.nextafter(np.float64(changed[key]), np.float64(np.inf)))
        failures[key] = reject(lambda value=changed: run.compare_prefix_checkpoint(first, second, 1, value, old_receipt))
    changed = dict(new_receipt, sampledIndices=new_receipt['sampledIndices'][::-1])
    failures['sampledIndices'] = reject(lambda: run.compare_prefix_checkpoint(first, second, 1, changed, old_receipt))
    metric = {'updates': 0, 'checkpoint': {'path': 'synthetic-new'},
              'rows': [{'observationId': 'SYNTHETIC-ONLY', 'buildNll': 1., 'predictedCells': [0]}]}
    old_metric = dict(metric, checkpoint={'path': 'synthetic-old'})
    run.compare_prefix_metrics(metric, old_metric)
    changed_metric = dict(metric, rows=[dict(metric['rows'][0], buildNll=1.0000000001)])
    failures['metric'] = reject(lambda: run.compare_prefix_metrics(changed_metric, old_metric))
    return {'sameSavedPrefixAcceptedAcrossExperimentBindings': True, 'changedSavedFieldsRejected': failures,
            'modelUpdates': 1, 'realRowsRead': 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = run.absolute(args.out)
    setup = (run.OUTPUT / 'setup').resolve(); proof_path = setup / 'checks.json'
    run.require(out.is_relative_to(setup) and out != setup and not out.exists() and not proof_path.exists(),
                'Choose a new long-build64 fixture directory; preserve completed proof')
    original_settings = json.dumps(run.SETTINGS, sort_keys=True)
    paths = set()
    for name in ('build64_long_v1', 'build64_v1', 'fit_one_v1', 'capacity_v1'):
        paths.update((run.ROOT / 'tools/indrill' / name).glob('*.py'))
    paths.update((Path(q.__file__), Path(obj.v2.__file__)))
    sources = {run.pin(path)['path']: run.pin(path)['sha256'] for path in sorted(paths)}
    runtime = [run.pin(path) for path in sorted(run.runtime_files())]
    report = {'schema': 'indrill-build64-long-checks-v1', 'syntheticOnly': True,
              'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'sourcePins': sources,
              'runtimePins': runtime, 'environment': run.environment(), 'priorProofs': [inherited_proof()],
              'realRowsRead': 0, 'realCandidateFitRuns': 0,
              'limitations': ['High-step snapshots are explicitly artificial; they do not represent prior fitting.',
                              'Five exact synthetic updates and unchanged AST reuse earlier derivative checks; no universal equivalence claim.',
                              'No production preparation, prefix audit, real-data fitting or learned evaluation occurs.']}
    out.mkdir(parents=True)
    run.write_once(out / 'intent.json', report)
    try:
        report['objective'] = objective_checks()
        report['sampling'] = sampling_check()
        report['boundary'] = boundary_checks(out / 'boundary')
        report['guards'] = metric_and_source_guards(out / 'guards')
        report['prefix'] = prefix_guard_checks(out / 'prefix')
        assert json.dumps(run.SETTINGS, sort_keys=True) == original_settings
        assert run.SETTINGS['updates'] == 8000 and run.SETTINGS['batch'] == 16
        assert obj.original.UPDATES == obj.original.single.UPDATES == 2000
        for path, digest in sources.items():
            run.check_pin({'path': path, 'sha256': digest})
        for item in runtime + report['priorProofs']:
            run.check_pin(item)
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
