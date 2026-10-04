"""Batched build-only diagnostic; frozen model, decoder and subset likelihood.

The output derivative is divided in float64 before assignment to float32, just
as in capacity-v1. One batched forward preserves its original numeric context.
"""
from pathlib import Path
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT))
from tools.indrill.fit_one_v1 import objective as single

np, q, v2, capacity = single.np, single.q, single.v2, single.capacity
FROZEN_HEADS = single.FROZEN_HEADS
MODEL_SEED, UPDATES = single.MODEL_SEED, single.UPDATES
LEARNING_RATE, CLIP_NORM = single.LEARNING_RATE, single.CLIP_NORM
subset_loss = single.subset_loss
network_initializer = single.network_initializer
assert_network_invariants = single.assert_network_invariants
require = single.require


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
