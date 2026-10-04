"""Synthetic-only implementation checks; no replay, real row, or fitting run."""
import copy
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
import objective as obj
from objective import np, q

ROOT = Path(__file__).resolve().parents[3]
OUTPUT = ROOT / 'build/analysis/indrill-fit-one-v1/setup/checks.json'


def synthetic_batch(target=(0, 1), ink=1):
    rng = np.random.default_rng(910)
    x = rng.normal(0, .2, (2, 6, 64)).astype(np.float32)
    state = q.State(x, np.asarray([1, 1, 0, 1, 1, 1], bool), np.asarray([1, 1], bool),
                    (target, ()), (ink, -1), (0., 0.), False)
    episode = q.Episode('SYNTHETIC-ONLY', 3, 2, 2, np.asarray([0, 0, 1, 0, 1, 1], np.int32),
                        np.asarray([1, 2, 1, 3, 1, 2], np.int32), [state])
    return q.Batch([q.Transition(episode, 0, 0, 0, 0., 0.)])


def padded(batch):
    result = copy.deepcopy(batch)
    result.x = np.pad(batch.x, ((0, 0), (0, 2), (0, 0)), constant_values=123.)
    for name in ('mask', 'legal_build', 'regions'):
        setattr(result, name, np.pad(getattr(batch, name), ((0, 0), (0, 2))))
    for name in ('costs', 'degrees'):
        setattr(result, name, np.pad(getattr(batch, name), ((0, 0), (0, 2)), constant_values=1))
    for name in ('neighbors', 'neighbor_mask'):
        setattr(result, name, np.pad(getattr(batch, name), ((0, 0), (0, 2), (0, 0))))
    # Include an unused padded region as well as padded cells.
    result.region_sizes = np.pad(batch.region_sizes, ((0, 0), (0, 1)), constant_values=1)
    result.legal_ink = np.pad(batch.legal_ink, ((0, 0), (0, 1)))
    return result


def loss_only(model, batch):
    build = model.forward(batch)[0]
    return obj.subset_loss(build[0], batch.costs[0], batch.legal_build[0], batch.builds[0])[0]


def expect_rejection(callback, word):
    try:
        callback()
    except ValueError as error:
        assert word.lower() in str(error).lower(), str(error)
        return
    raise AssertionError('Expected rejected invariant: ' + word)


def network_checks():
    batch = synthetic_batch()
    model = obj.network_initializer()
    initialized = obj.capacity.fresh_model(obj.MODEL_SEED)
    for name in model.p:
        np.testing.assert_array_equal(model.p[name], initialized.p[name])
    # Keep active and inactive units safely away from the ReLU kink.
    for name, tensor in model.p.items():
        if name.startswith('W') and name not in obj.FROZEN_HEADS:
            tensor *= .08
    model.p['B0'].fill(1.); model.p['B0'][:3] = -1.
    model.p['B1'].fill(1.); model.p['B1'][:3] = -1.
    loss, gradient = obj.network_gradients(model, batch)
    assert loss == loss_only(model, batch)
    altered = copy.deepcopy(batch)
    altered.inks[0] = -1
    altered.legal_ink.fill(False)
    no_ink_loss, no_ink_gradient = obj.network_gradients(model, altered)
    assert no_ink_loss == loss
    for name in gradient:
        np.testing.assert_array_equal(gradient[name], no_ink_gradient[name], err_msg='Ink leaked into build trunk ' + name)
    extra = padded(batch)
    padded_loss, padded_gradient = obj.network_gradients(model, extra)
    np.testing.assert_allclose(padded_loss, loss, atol=1e-7, rtol=1e-7)
    for name in gradient:
        np.testing.assert_allclose(padded_gradient[name], gradient[name], atol=2e-7, rtol=1e-5, err_msg='Padding leak ' + name)
    coordinates, max_error = 0, 0.
    for name, tensor in model.p.items():
        if name in obj.FROZEN_HEADS:
            assert not np.any(gradient[name])
            continue
        for flat in np.unique(np.linspace(0, tensor.size - 1, min(8, tensor.size), dtype=int)):
            index = np.unravel_index(flat, tensor.shape)
            original = tensor[index].copy()
            tensor[index] = original + np.float32(.01)
            plus, upper = loss_only(model, extra), float(tensor[index])
            tensor[index] = original - np.float32(.01)
            minus, lower = loss_only(model, extra), float(tensor[index])
            tensor[index] = original
            numerical, analytical = (plus - minus) / (upper - lower), float(padded_gradient[name][index])
            max_error = max(max_error, abs(numerical - analytical))
            np.testing.assert_allclose(numerical, analytical, atol=2e-5, rtol=.015, err_msg=f'{name}{index}')
            coordinates += 1
    heads = {name: model.p[name].copy() for name in obj.FROZEN_HEADS}
    model.adam(gradient, obj.LEARNING_RATE, obj.CLIP_NORM)
    obj.assert_network_invariants(model)
    for name in obj.FROZEN_HEADS:
        np.testing.assert_array_equal(model.p[name], heads[name])
        assert not np.any(model.m[name]) and not np.any(model.v[name])
    assert all(np.isfinite(a).all() for a in model.forward(extra))
    model.m['WI'][0] = .1
    expect_rejection(lambda: obj.assert_network_invariants(model), 'moment changed')
    model.m['WI'][0] = 0.
    obj.assert_network_invariants(model)
    return {'finiteDifferenceCoordinates': coordinates, 'maxWeightGradientAbsError': max_error,
            'initializerMatchesFrozenCapacityInitializer': True, 'inkTargetsHaveNoBuildLossOrGradientEffect': True,
            'maskedCellAndRegionPadding': True, 'frozenHeadParametersAndMomentsPreserved': True,
            'nonzeroFrozenMomentRejected': True, 'finiteOutputs': True}


def free_checks():
    batch = padded(synthetic_batch())
    state = obj.free_initializer(batch)
    assert not np.any(state.logits) and not np.any(state.m) and not np.any(state.v)
    loss, gradient = obj.free_gradients(state.logits, batch)
    expected_loss, expected_gradient, _, _ = obj.subset_loss(state.logits, batch.costs[0], batch.legal_build[0], batch.builds[0])
    assert loss == expected_loss
    np.testing.assert_array_equal(gradient, expected_gradient.astype(np.float32))
    max_error = 0.
    for cell in np.flatnonzero(state.legal):
        plus, minus = state.logits.copy(), state.logits.copy()
        plus[cell], minus[cell] = np.float32(.001), np.float32(-.001)
        numerical = (obj.free_gradients(plus, batch)[0] - obj.free_gradients(minus, batch)[0]) / float(plus[cell] - minus[cell])
        max_error = max(max_error, abs(numerical - float(gradient[cell])))
        np.testing.assert_allclose(numerical, gradient[cell], atol=2e-6, rtol=2e-5)
    assert not np.any(gradient[~state.legal])
    bad = gradient.copy(); bad[np.flatnonzero(~state.legal)[0]] = 1.
    expect_rejection(lambda: obj.free_adam(state, bad), 'Illegal-cell derivative')
    assert state.updates == 0 and not np.any(state.logits) and not np.any(state.m) and not np.any(state.v)
    # Independently use the unchanged existing optimizer, with one vector tensor.
    reference = q.Model(np.random.default_rng(1))
    reference.p = {'X': state.logits.copy()}
    reference.m = {'X': state.m.copy()}; reference.v = {'X': state.v.copy()}; reference.updates = 0
    reference_gradient = gradient.copy()
    first_norm = obj.free_adam(state, gradient)
    old_norm = reference.adam({'X': reference_gradient}, obj.LEARNING_RATE, obj.CLIP_NORM)
    assert first_norm == old_norm
    first_m = .1 * gradient.astype(np.float64)
    first_v = .001 * gradient.astype(np.float64) ** 2
    expected_first = -obj.LEARNING_RATE * (first_m / .1) / (np.sqrt(first_v / .001) + obj.EPSILON)
    np.testing.assert_allclose(state.logits, expected_first, atol=1e-10, rtol=2e-6)
    clipped = np.where(state.legal, 100., 0.).astype(np.float32)
    second_norm = obj.free_adam(state, clipped)
    reference.adam({'X': clipped}, obj.LEARNING_RATE, obj.CLIP_NORM)
    assert second_norm > obj.CLIP_NORM
    for name, key in (('logits', 'p'), ('m', 'm'), ('v', 'v')):
        np.testing.assert_array_equal(getattr(state, name), getattr(reference, key)['X'], err_msg='Existing Adam convention ' + name)
    assert state.updates == reference.updates == 2
    obj.assert_free_invariants(state)
    # Empty and all-illegal construction sets remain valid; no fabricated ink objective.
    empty = synthetic_batch(target=())
    empty.legal_build.fill(False)
    zeros = obj.free_initializer(empty)
    empty_loss, empty_gradient = obj.free_gradients(zeros.logits, empty)
    assert empty_loss == 0. and not np.any(empty_gradient)
    obj.free_adam(zeros, empty_gradient)
    obj.assert_free_invariants(zeros)
    bad_logits = state.logits.copy(); bad_logits[np.flatnonzero(state.legal)[0]] = np.nan
    expect_rejection(lambda: obj.free_gradients(bad_logits, batch), 'Invalid free logits')
    return {'maxOutputGradientAbsError': max_error, 'lossAndGradientMatchImportedSubsetLoss': True,
            'illegalAndPaddedGradientsAndMomentsZero': True, 'illegalGradientRejectedBeforeUpdate': True,
            'twoAdamStepsMatchUnchangedOptimizerBitwise': True, 'firstStepMatchesClosedForm': True,
            'gradientClippingBranchChecked': True, 'emptyAllIllegalCase': True, 'nonfiniteRejected': True}


def main():
    if len(sys.argv) != 1:
        raise SystemExit('No corpus or output arguments: writes only the fixed new synthetic checks.json')
    obj.require(not OUTPUT.exists(), 'Preserve existing fit-one checks; no implicit repeat')
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sources = (Path(__file__), Path(obj.__file__), Path(obj.capacity.__file__), Path(q.__file__), Path(obj.v2.__file__))
    report = {'schema': 'indrill-fit-one-checks-v1', 'syntheticOnly': True,
              'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'realRowsRead': 0, 'fitRuns': 0,
              'sourcePins': {p.resolve().relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
              'limitations': ['Finite differences sample coordinates on synthetic boards, not every input.',
                              'Optimizer smoke updates are implementation checks, not a fitting result.',
                              'Frozen ink parameters do not imply unchanged ink outputs while the trunk learns.']}
    try:
        report['network'] = network_checks()
        report['free'] = free_checks()
        report['passed'] = True
    except Exception as error:
        report['passed'] = False
        report['failure'] = {'type': type(error).__name__, 'message': str(error)}
        with OUTPUT.open('x', encoding='utf-8') as stream:
            json.dump(report, stream, indent=2); stream.write('\n')
        raise
    with OUTPUT.open('x', encoding='utf-8') as stream:
        json.dump(report, stream, indent=2); stream.write('\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
