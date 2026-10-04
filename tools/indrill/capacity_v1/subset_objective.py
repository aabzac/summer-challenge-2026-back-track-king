"""Diagnostic supervised objective; no corpus I/O, training CLI, or rule reconstruction.

Keep the existing V2 network/decoder unchanged. Costs and legality come from core.
"""
from pathlib import Path
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools' / 'indrill'))
import train_q
import train_q_v2
import numpy as np


def require(ok, message):
    if not ok:
        raise ValueError(message)


def subset_loss(logits, costs, legal, target, budget=3):
    """Exact unordered subset NLL, derivative, marginals, log partition.

    O(cells * budget) space and O(cells * budget**2) work. Empty set appears once.
    Illegal logits have zero derivative and are never included in the partition.
    """
    require(budget == 3, 'This frozen diagnostic uses paint budget three')
    q, costs, legal = np.asarray(logits, dtype=np.float64), np.asarray(costs), np.asarray(legal, dtype=bool)
    require(q.ndim == 1 and q.shape == costs.shape == legal.shape, 'Build shape mismatch')
    ids = np.flatnonzero(legal)
    require(np.isfinite(q[ids]).all(), 'Nonfinite legal build logits')
    require(np.isin(costs[ids], (1, 2, 3)).all(), 'Invalid exported legal-cell price')
    chosen = tuple(int(i) for i in target)
    require(len(chosen) == len(set(chosen)) and all(0 <= i < len(q) and legal[i] for i in chosen), 'Invalid target subset')
    require(sum(int(costs[i]) for i in chosen) <= budget, 'Target exceeds paint budget')
    m = len(ids)
    forward = np.full((m + 1, budget + 1), -np.inf, dtype=np.float64)
    suffix = np.full_like(forward, -np.inf)
    forward[0, 0] = suffix[m, 0] = 0.0
    for j, cell in enumerate(ids):
        price = int(costs[cell])
        forward[j + 1] = forward[j]
        forward[j + 1, price:] = np.logaddexp(forward[j, price:], q[cell] + forward[j, :-price])
    for j in range(m - 1, -1, -1):
        cell, price = ids[j], int(costs[ids[j]])
        suffix[j] = suffix[j + 1]
        suffix[j, price:] = np.logaddexp(suffix[j + 1, price:], q[cell] + suffix[j + 1, :-price])
    log_z = float(np.logaddexp.reduce(forward[m]))
    marginal = np.zeros_like(q)
    for j, cell in enumerate(ids):
        remaining = budget - int(costs[cell])
        terms = [forward[j, a] + suffix[j + 1, b]
                 for a in range(remaining + 1) for b in range(remaining - a + 1)]
        marginal[cell] = np.exp(q[cell] + np.logaddexp.reduce(terms) - log_z)
    derivative = marginal.copy()
    if chosen:
        derivative[list(chosen)] -= 1.0
    loss = log_z - float(q[list(chosen)].sum()) if chosen else log_z
    require(np.isfinite(loss) and np.isfinite(derivative).all(), 'Nonfinite subset objective')
    return loss, derivative, marginal, log_z


def ink_loss(logits, legal, target):
    """Softmax over legal regions plus fixed NONE logit zero."""
    q, legal = np.asarray(logits, dtype=np.float64), np.asarray(legal, dtype=bool)
    require(q.ndim == 1 and q.shape == legal.shape, 'Ink shape mismatch')
    require(np.isfinite(q[legal]).all(), 'Nonfinite legal ink logits')
    require(target == -1 or (0 <= target < len(q) and legal[target]), 'Invalid ink target')
    log_z = float(np.logaddexp.reduce(np.concatenate(([0.0], q[legal]))))
    derivative = np.zeros_like(q)
    derivative[legal] = np.exp(q[legal] - log_z)
    loss = log_z
    if target >= 0:
        derivative[target] -= 1.0
        loss -= float(q[target])
    return loss, derivative, log_z


def output_objective(build, ink, batch):
    n = len(batch.transitions)
    require(n > 0, 'Empty supervised batch')
    db, di = np.zeros_like(build, dtype=np.float32), np.zeros_like(ink, dtype=np.float32)
    losses = np.zeros((n, 2), dtype=np.float64)
    for row in range(n):
        losses[row, 0], derivative, _, _ = subset_loss(build[row], batch.costs[row], batch.legal_build[row], batch.builds[row])
        db[row] = derivative / n
        losses[row, 1], derivative, _ = ink_loss(ink[row], batch.legal_ink[row], batch.inks[row])
        di[row] = derivative / n
    return float(losses.sum(axis=1).mean()), losses, db, di


def loss_gradients(model, batch):
    build, ink, _value, cache = model.forward(batch, cache=True)
    h0, local, global_h, h1, region_h, _mean_h1 = cache
    loss, per_head, db, di = output_objective(build, ink, batch)
    p = model.p
    grad = {name: np.zeros_like(value) for name, value in p.items()}
    grad['WB'] = np.einsum('bn,bnh->h', db, h1, optimize=True)
    grad['BB'][0] = db.sum()
    grad['WI'] = np.einsum('br,brh->h', di, region_h, optimize=True)
    grad['BI'][0] = di.sum()
    row = np.arange(len(batch.transitions))[:, None]
    ink_cell = di[row, batch.regions] / batch.region_sizes[row, batch.regions]
    dh1 = (db[..., None] * p['WB'] + ink_cell[..., None] * p['WI']) * batch.mask[..., None]
    dz1 = dh1 * (h1 > 0)
    grad['WS'] = np.einsum('bni,bnj->ij', h0, dz1, optimize=True)
    grad['WN'] = np.einsum('bni,bnj->ij', local, dz1, optimize=True)
    grad['WG'] = global_h.T @ dz1.sum(axis=1)
    grad['B1'] = dz1.sum(axis=(0, 1))
    dh0 = dz1 @ p['WS'].T
    neighbor_gradient = (dz1 @ p['WN'].T) / batch.degrees[..., None]
    for direction in range(4):
        bi, cells = np.nonzero(batch.neighbor_mask[:, :, direction])
        np.add.at(dh0, (bi, batch.neighbors[bi, cells, direction]), neighbor_gradient[bi, cells])
    dh0 += ((dz1.sum(axis=1) @ p['WG'].T) / batch.sizes[:, None])[:, None]
    dz0 = dh0 * (h0 > 0)
    grad['W0'] = np.einsum('bni,bnj->ij', batch.x, dz0, optimize=True)
    grad['B0'] = dz0.sum(axis=(0, 1))
    # WV/BV never enter the likelihood: gradients and optimizer state remain zero.
    require(all(np.isfinite(value).all() for value in grad.values()), 'Nonfinite supervised gradient')
    return loss, per_head, grad


def fresh_model(seed):
    model = train_q_v2.Model(np.random.default_rng(seed))
    for name in ('WV', 'BV'):
        model.p[name].fill(0)
    for name in model.p:
        model.m[name].fill(0)
        model.v[name].fill(0)
    model.updates = 0
    return model


def assert_finite_frozen_value(model):
    require(all(np.isfinite(array).all() for group in (model.p, model.m, model.v) for array in group.values()), 'NUMERICAL_FAILURE')
    require(all(not np.any(group[name]) for group in (model.p, model.m, model.v) for name in ('WV', 'BV')), 'Value head or its optimizer state changed')
