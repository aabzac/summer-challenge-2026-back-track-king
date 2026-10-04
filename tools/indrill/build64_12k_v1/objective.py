"""Unchanged build64 likelihood/backprop with an explicit 12,000-update guard.

The mathematical function bodies are identical to build64-long-v1. Restoring a
parent checkpoint preserves its real optimizer counter; old modules are untouched.
"""
from pathlib import Path
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from tools.indrill.build64_long_v1 import objective as original

np, q, v2, capacity = original.np, original.q, original.v2, original.capacity
FROZEN_HEADS = original.FROZEN_HEADS
MODEL_SEED, UPDATES = original.MODEL_SEED, 12000
LEARNING_RATE, CLIP_NORM = original.LEARNING_RATE, original.CLIP_NORM
subset_loss = original.subset_loss
require = original.require


def network_initializer():
    model = original.network_initializer()
    assert_network_invariants(model)
    return model


def assert_network_invariants(model):
    require(type(model.updates) is int and 0 <= model.updates <= UPDATES, 'Invalid network update count')
    require(hasattr(model, '_fit_one_frozen_heads'), 'Restore into network_initializer, preserving initial head snapshots')
    require(set(model.p) == set(v2.PARAM_SHAPES) == set(model.m) == set(model.v), 'Unexpected network tensors')
    for group in (model.p, model.m, model.v):
        for name, shape in v2.PARAM_SHAPES.items():
            require(group[name].shape == shape and group[name].dtype == np.float32
                    and np.isfinite(group[name]).all(), 'Nonfinite or invalid network tensor: ' + name)
    for name in FROZEN_HEADS:
        require(np.array_equal(model.p[name], model._fit_one_frozen_heads[name]), 'Frozen head parameter changed: ' + name)
        require(not np.any(model.m[name]) and not np.any(model.v[name]), 'Frozen head optimizer moment changed: ' + name)
    require(not np.any(model.p['WV']) and not np.any(model.p['BV']), 'Value head must remain zero')


def output_objective(build, batch):
    count = len(batch.transitions)
    require(count > 0 and build.shape == batch.costs.shape == batch.legal_build.shape
            == batch.mask.shape and build.shape[0] == count, 'Build batch shape mismatch')
    require(not np.any(batch.legal_build & ~batch.mask), 'A padded cell cannot be legal')
    losses = np.empty(count, np.float64)
    db = np.zeros_like(build, dtype=np.float32)
    for row in range(count):
        losses[row], derivative, _, _ = subset_loss(
            build[row], batch.costs[row], batch.legal_build[row], batch.builds[row])
        db[row] = derivative / count
    return float(losses.mean()), losses, db


def network_gradients(model, batch):
    """Mean build NLL and gradients, with no ink contribution to the trunk."""
    assert_network_invariants(model)
    build, ink, value, cache = model.forward(batch, cache=True)
    require(all(np.isfinite(a).all() for a in (build, ink, value)), 'Nonfinite network output')
    h0, local, global_h, h1, _region_h, _mean_h1 = cache
    loss, _per_row, db = output_objective(build, batch)
    p = model.p
    grad = {name: np.zeros_like(tensor) for name, tensor in p.items()}
    grad['WB'] = np.einsum('bn,bnh->h', db, h1, optimize=True)
    grad['BB'][0] = db.sum()
    # Frozen ink parameters alone would not remove the ink derivative upstream.
    dh1 = (db[..., None] * p['WB']) * batch.mask[..., None]
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
    require(np.isfinite(loss) and all(np.isfinite(a).all() for a in grad.values()), 'Nonfinite build loss/gradient')
    require(all(not np.any(grad[name]) for name in FROZEN_HEADS), 'Frozen-head derivative is nonzero')
    return loss, grad
