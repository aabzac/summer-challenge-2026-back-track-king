"""Guarded reuse of the immutable original joint construction/ink objective.

WI/BI train through the shared trunk; only the value head remains zero. The
original loss, derivatives, network forward and Adam arithmetic are unchanged.
"""
from pathlib import Path
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/indrill/capacity_v1'))
import subset_objective as capacity

np, q, v2 = capacity.np, capacity.train_q, capacity.train_q_v2
MODEL_SEED, UPDATES = 2026100401, 12000
LEARNING_RATE, CLIP_NORM = .0003, 10.
FROZEN_HEADS = ('WV', 'BV')
require = capacity.require
loss_gradients = capacity.loss_gradients
output_objective = capacity.output_objective
fresh_model = capacity.fresh_model
subset_loss, ink_loss = capacity.subset_loss, capacity.ink_loss


def assert_network_invariants(model):
    require(type(model.updates) is int and 0 <= model.updates <= UPDATES, 'Invalid network update count')
    require(set(model.p) == set(v2.PARAM_SHAPES) == set(model.m) == set(model.v), 'Unexpected network tensors')
    for group in (model.p, model.m, model.v):
        for name, shape in v2.PARAM_SHAPES.items():
            require(group[name].shape == shape and group[name].dtype == np.float32
                    and np.isfinite(group[name]).all(), 'Nonfinite or invalid network tensor: ' + name)
            if name in FROZEN_HEADS:
                require(not np.any(group[name]), 'Frozen value-head parameter/moment changed: ' + name)


def network_initializer():
    model = fresh_model(MODEL_SEED)
    assert_network_invariants(model)
    return model


def network_gradients(model, batch):
    """Return the original mean joint NLL and its unmodified full gradients."""
    assert_network_invariants(model)
    loss, _per_head, gradients = loss_gradients(model, batch)
    require(np.isfinite(loss), 'Nonfinite joint loss')
    require(all(not np.any(gradients[name]) for name in FROZEN_HEADS), 'Frozen value-head derivative is nonzero')
    return loss, gradients
