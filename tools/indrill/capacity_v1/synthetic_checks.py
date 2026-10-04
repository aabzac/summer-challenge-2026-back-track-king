"""Small implementation checks only. Never reads replay corpora or trains a model."""
import argparse
from datetime import datetime, timezone
import hashlib
import itertools
import json
from pathlib import Path

import subset_objective as objective
from subset_objective import np, train_q as q


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def enumerate_subsets(logits, costs, legal):
    ids = np.flatnonzero(legal)
    subsets = [cells for n in range(len(ids) + 1) for cells in itertools.combinations(ids, n)
               if sum(costs[i] for i in cells) <= 3]
    scores = np.asarray([sum(logits[i] for i in cells) for cells in subsets], dtype=np.float64)
    log_z = float(np.logaddexp.reduce(scores))
    probabilities = np.exp(scores - log_z)
    marginal = np.zeros(len(logits), dtype=np.float64)
    for cells, probability in zip(subsets, probabilities):
        marginal[list(cells)] += probability
    return subsets, log_z, marginal


def tiny_batch():
    rng = np.random.default_rng(901)
    items = []
    for k, (width, height, regions, costs, legal, ink, cells, target_ink) in enumerate([
        (3, 2, [0, 0, 1, 0, 1, 1], [1, 2, 1, 3, 1, 2], [1, 1, 0, 1, 1, 1], [1, 1], (0, 1), 1),
        (2, 1, [0, 0], [1, 3], [1, 0], [0], (), -1),
    ]):
        size = width * height
        x = rng.normal(0, .2, (2, size, 64)).astype(np.float32)
        state = q.State(x, np.asarray(legal, bool), np.asarray(ink, bool), (cells, ()), (target_ink, -1), (0., 0.), False)
        episode = q.Episode(str(k), width, height, max(regions) + 1, np.asarray(regions, np.int32), np.asarray(costs, np.int32), [state])
        items.append(q.Transition(episode, 0, 0, 0, 0., 0.))
    return q.Batch(items)


def checks():
    rng = np.random.default_rng(2026100400)
    max_partition = max_marginal = max_output = max_weight = 0.
    tested = 0
    for size in range(0, 8):
        for case in range(12):
            logits = rng.normal(0, 3, size)
            if case == 1:
                logits = np.full(size, 1000.)
            if case == 2:
                logits = np.full(size, -1000.)
            costs = rng.integers(1, 4, size)
            legal = np.zeros(size, bool) if case == 0 else rng.random(size) > .2
            subsets, expected_z, expected_marginal = enumerate_subsets(logits, costs, legal)
            target = subsets[len(subsets) // 2]
            loss, derivative, marginal, log_z = objective.subset_loss(logits, costs, legal, target)
            max_partition = max(max_partition, abs(log_z - expected_z))
            max_marginal = max(max_marginal, float(np.max(np.abs(marginal - expected_marginal), initial=0)))
            np.testing.assert_allclose(log_z, expected_z, atol=1e-10, rtol=1e-12)
            np.testing.assert_allclose(marginal, expected_marginal, atol=1e-10, rtol=1e-10)
            reverse = objective.subset_loss(logits, costs, legal, tuple(reversed(target)))
            np.testing.assert_array_equal(derivative, reverse[1])
            np.testing.assert_allclose(loss, reverse[0], atol=1e-10)
            for cell in range(size):
                plus, minus = logits.copy(), logits.copy()
                plus[cell] += 1e-5; minus[cell] -= 1e-5
                numerical = (objective.subset_loss(plus, costs, legal, target)[0] - objective.subset_loss(minus, costs, legal, target)[0]) / 2e-5
                max_output = max(max_output, abs(numerical - derivative[cell]))
                np.testing.assert_allclose(numerical, derivative[cell], atol=5e-7, rtol=5e-6)
            tested += 1
    ink_checks = 0
    for legal in (np.asarray([0, 0, 0], bool), np.asarray([1, 0, 1], bool)):
        for target in (-1, *np.flatnonzero(legal)):
            logits = np.asarray([1.3, 18., -.9], dtype=np.float64)
            _, derivative, log_z = objective.ink_loss(logits, legal, int(target))
            np.testing.assert_allclose(np.exp(-log_z) + np.exp(logits[legal] - log_z).sum(), 1.)
            for region in range(3):
                plus, minus = logits.copy(), logits.copy()
                plus[region] += 1e-5; minus[region] -= 1e-5
                numerical = (objective.ink_loss(plus, legal, int(target))[0] - objective.ink_loss(minus, legal, int(target))[0]) / 2e-5
                max_output = max(max_output, abs(numerical - derivative[region]))
                np.testing.assert_allclose(numerical, derivative[region], atol=1e-9, rtol=1e-7)
            ink_checks += 1
    batch = tiny_batch()
    model = objective.fresh_model(777)
    # Positive/negative margins keep finite differences away from ReLU kinks.
    for name, array in model.p.items():
        if name.startswith('W') and name != 'WV':
            array *= .08
    model.p['B0'].fill(1.0); model.p['B0'][:3] = -1.
    model.p['B1'].fill(1.0); model.p['B1'][:3] = -1.
    loss, _, gradients = objective.loss_gradients(model, batch)
    weight_checks = 0
    for name, tensor in model.p.items():
        indices = np.unique(np.linspace(0, tensor.size - 1, min(8, tensor.size), dtype=int))
        for flat in indices:
            index = np.unravel_index(flat, tensor.shape)
            original = tensor[index].copy()
            tensor[index] = original + np.float32(.01)
            plus = objective.output_objective(*model.forward(batch)[:2], batch)[0]
            upper = float(tensor[index])
            tensor[index] = original - np.float32(.01)
            minus = objective.output_objective(*model.forward(batch)[:2], batch)[0]
            lower = float(tensor[index])
            tensor[index] = original
            numerical = (plus - minus) / (upper - lower)
            analytic = float(gradients[name][index])
            max_weight = max(max_weight, abs(numerical - analytic))
            np.testing.assert_allclose(numerical, analytic, atol=2e-5, rtol=.015, err_msg=f'{name}{index}')
            weight_checks += 1
    objective.assert_finite_frozen_value(model)
    build, ink, _ = model.forward(batch)
    _, _, db, di = objective.output_objective(build, ink, batch)
    assert not np.any(db[~batch.legal_build]) and not np.any(di[~batch.legal_ink])
    assert not np.any(gradients['WV']) and not np.any(gradients['BV'])
    # One optimizer application checks only frozen-head invariants, not fitting.
    model.adam(gradients, .0003, 10.)
    objective.assert_finite_frozen_value(model)
    return {'status': 'PASS', 'subsetCases': tested, 'inkCases': ink_checks, 'weightCoordinates': weight_checks,
            'maxPartitionAbsError': max_partition, 'maxMarginalAbsError': max_marginal,
            'maxOutputGradientAbsError': max_output, 'maxWeightGradientAbsError': max_weight,
            'maskedPaddingAndMultipleRegions': True, 'targetOrderInvariant': True,
            'valueHeadAndMomentsZero': True, 'syntheticOnly': True, 'corpusRowsRead': 0,
            'limitations': ['Finite differences sample tensor coordinates; they are not a proof for every input.',
                           'No export, Java feature parity, real-board coverage, fitting, or transfer result is established.']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    path = Path(args.out).resolve()
    objective.require(path.parent == objective.ROOT / 'build/analysis/indrill-capacity-v1/setup' and not path.exists(), 'Write a new result only inside the diagnostic setup directory')
    result = {'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'checks': None,
              'pins': [{'path': str(p), 'sha256': sha(p)} for p in
                       (Path(__file__), Path(objective.__file__), Path(q.__file__), Path(objective.train_q_v2.__file__))]}
    try:
        result['checks'] = checks()
    except Exception as error:
        result['checks'] = {'status': 'FAIL', 'type': type(error).__name__, 'message': str(error), 'syntheticOnly': True}
        with path.open('x', encoding='utf-8') as stream:
            json.dump(result, stream, indent=2); stream.write('\n')
        raise
    with path.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, indent=2); stream.write('\n')
    print(json.dumps(result['checks']))


if __name__ == '__main__':
    main()
