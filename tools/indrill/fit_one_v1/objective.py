"""Fixed build-only objectives for indrill-fit-one-v1; no data I/O or runner."""
from dataclasses import dataclass
from pathlib import Path
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
CAPACITY = ROOT / 'tools/indrill/capacity_v1'
sys.path.insert(0, str(CAPACITY))
import subset_objective as capacity
from subset_objective import np, train_q as q, train_q_v2 as v2

UPDATES, BATCH_SIZE, MODEL_SEED = 2000, 1, 2026100401
LEARNING_RATE, CLIP_NORM = .0003, 10.
BETA1, BETA2, EPSILON = .9, .999, 1e-8
FROZEN_HEADS = ('WI', 'BI', 'WV', 'BV')
subset_loss = capacity.subset_loss


def require(ok, message):
    if not ok:
        raise ValueError(message)


def _batch(batch):
    require(len(batch.transitions) == BATCH_SIZE and batch.x.shape[0] == 1, 'Fixed batch one required')
    require(batch.legal_build.shape == batch.costs.shape == batch.mask.shape,
            'Construction mask/cost shape mismatch')
    require(not np.any(batch.legal_build & ~batch.mask), 'A padded cell cannot be legal')


def network_initializer():
    model = capacity.fresh_model(MODEL_SEED)
    model._fit_one_frozen_heads = {name: model.p[name].copy() for name in FROZEN_HEADS}
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


def network_gradients(model, batch):
    """Return mean build NLL and full parameter gradients; no ink derivative.

    WI/BI remain at initialization, but ink outputs can change as the trunk learns.
    This is deliberately separate from the old joint-loss backward adapter.
    """
    _batch(batch)
    assert_network_invariants(model)
    build, ink, value, cache = model.forward(batch, cache=True)
    require(all(np.isfinite(a).all() for a in (build, ink, value)), 'Nonfinite network output')
    h0, local, global_h, h1, _region_h, _mean_h1 = cache
    loss, derivative, _, _ = subset_loss(build[0], batch.costs[0], batch.legal_build[0], batch.builds[0])
    db = derivative.astype(np.float32)[None, :]
    p = model.p
    grad = {name: np.zeros_like(tensor) for name, tensor in p.items()}
    grad['WB'] = np.einsum('bn,bnh->h', db, h1, optimize=True)
    grad['BB'][0] = db.sum()
    # dInk is identically zero BEFORE shared-trunk backpropagation.
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


@dataclass
class FreeState:
    logits: np.ndarray
    m: np.ndarray
    v: np.ndarray
    legal: np.ndarray
    updates: int = 0


def free_initializer(batch):
    _batch(batch)
    legal = batch.legal_build[0].copy()
    state = FreeState(np.zeros(legal.shape, np.float32), np.zeros(legal.shape, np.float32),
                      np.zeros(legal.shape, np.float32), legal)
    assert_free_invariants(state)
    return state


def assert_free_invariants(state):
    require(type(state.updates) is int and 0 <= state.updates <= UPDATES, 'Invalid free-logit update count')
    require(state.legal.ndim == 1 and state.legal.dtype == bool, 'Invalid free-logit legal mask')
    for name in ('logits', 'm', 'v'):
        value = getattr(state, name)
        require(value.shape == state.legal.shape and value.dtype == np.float32 and np.isfinite(value).all(),
                'Nonfinite or invalid free-logit tensor: ' + name)
        require(not np.any(value[~state.legal]), 'Illegal free-logit parameter/moment changed: ' + name)


def free_gradients(logits, batch):
    _batch(batch)
    require(logits.shape == batch.legal_build[0].shape and logits.dtype == np.float32
            and np.isfinite(logits).all(), 'Invalid free logits')
    require(not np.any(logits[~batch.legal_build[0]]), 'Illegal free logits must remain zero')
    loss, derivative, _, _ = subset_loss(logits, batch.costs[0], batch.legal_build[0], batch.builds[0])
    gradient = derivative.astype(np.float32)
    require(np.isfinite(gradient).all() and not np.any(gradient[~batch.legal_build[0]]), 'Invalid free derivative')
    return loss, gradient


def free_adam(state, gradient):
    """Identical float32 moment/update convention to the unchanged network Adam."""
    assert_free_invariants(state)
    require(state.updates < UPDATES, 'Fixed update budget exhausted')
    require(gradient.shape == state.logits.shape and gradient.dtype == np.float32
            and np.isfinite(gradient).all(), 'Invalid free gradient')
    require(not np.any(gradient[~state.legal]), 'Illegal-cell derivative must be zero')
    norm = float(np.sqrt(float(np.sum(gradient * gradient))))
    require(np.isfinite(norm), 'Nonfinite free gradient norm')
    factor = min(1., CLIP_NORM / max(norm, 1e-12))
    g = gradient * factor
    state.updates += 1
    state.m[:] = BETA1 * state.m + (1. - BETA1) * g
    state.v[:] = BETA2 * state.v + (1. - BETA2) * g * g
    mhat = state.m / (1. - BETA1 ** state.updates)
    vhat = state.v / (1. - BETA2 ** state.updates)
    state.logits -= LEARNING_RATE * mhat / (np.sqrt(vhat) + EPSILON)
    assert_free_invariants(state)
    return norm
