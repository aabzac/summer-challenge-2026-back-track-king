"""Technical invariants; synthetic tensors do not reproduce game rules."""
from __future__ import annotations

import base64
from pathlib import Path
import re
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
if (ROOT / "build" / "spatial-deps").is_dir():
    sys.path.insert(0, str(ROOT / "build" / "spatial-deps"))

import numpy as np
import torch
from model import SpatialNet, WEIGHT_COUNT, evaluate_plan, masked_distribution
from ppo import gae, update


def observations(n):
    planes = torch.randn(n, 24, 20, 30) * 0.1
    planes[:, 0] = 1
    mask = torch.zeros(n, 601, dtype=torch.bool)
    mask[:, [0, 1, 600]] = True
    return {"planes": planes, "towns": torch.zeros(n, 12, 49),
            "townCells": torch.full((n, 12), -1, dtype=torch.int64),
            "regionIds": torch.zeros(n, 600, dtype=torch.int64),
            "scalars": torch.zeros(n, 6), "buildMask": mask.clone(), "disruptMask": mask.clone()}


class SpatialTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def setUp(self):
        torch.manual_seed(9)

    def test_gae_terminal_does_not_bootstrap_or_cross_reset(self):
        rewards = torch.tensor([[1.], [2.], [3.]])
        values = torch.tensor([[0.5], [10.], [20.]])
        dones = torch.tensor([[True], [False], [True]])
        adv, ret = gae(rewards, values, dones, torch.tensor([500.]), gamma=0.9, lam=1)
        torch.testing.assert_close(ret[:, 0], torch.tensor([1., 4.7, 3.]))
        torch.testing.assert_close(adv, ret - values)

    def test_gae_nonterminal_cutoff_bootstraps_once_per_turn(self):
        adv, ret = gae(torch.tensor([[2.]]), torch.tensor([[1.]]), torch.tensor([[False]]),
                       torch.tensor([3.]), gamma=0.9, lam=0.95)
        self.assertAlmostEqual(float(ret), 4.7, places=5)
        self.assertAlmostEqual(float(adv), 3.7, places=5)

    def test_masked_actions_and_forced_stop_zero_gradient(self):
        logits = torch.randn(3, 601, requires_grad=True)
        mask = torch.zeros_like(logits, dtype=torch.bool)
        mask[:, 600] = True
        dist = masked_distribution(logits, mask)
        action = dist.sample()
        self.assertTrue((action == 600).all())
        lp = dist.log_prob(action)
        self.assertTrue((lp == 0).all())
        self.assertTrue((dist.entropy() == 0).all())
        (-lp.sum()).backward()
        self.assertTrue((logits.grad == 0).all())

    def test_empty_mask_rejected(self):
        with self.assertRaises(ValueError):
            masked_distribution(torch.zeros(1, 601), torch.zeros(1, 601, dtype=torch.bool))

    def test_full_plan_logprob_sum_and_joint_ratio(self):
        model = SpatialNet()
        obs = observations(8)
        actions = torch.tensor([[0, 1, 600, 0], [1, 600, 600, 600]])
        # Decisions after STOP are conditional singleton masks.
        obs["buildMask"][6] = False
        obs["buildMask"][6, 600] = True
        joint, _, values = evaluate_plan(model, obs, actions)
        build, ink, all_values = model(obs)
        pieces = []
        for row in range(8):
            logits, mask = (ink[row], obs["disruptMask"][row]) if row % 4 == 3 else (build[row], obs["buildMask"][row])
            pieces.append(masked_distribution(logits, mask).log_prob(actions.flatten()[row]))
        conditional = torch.stack(pieces).reshape(2, 4)
        torch.testing.assert_close(joint, conditional.sum(1))
        torch.testing.assert_close(values, all_values[[0, 4]])
        shifted = conditional - torch.tensor([[0.1, 0.2, 0.3, 0.4], [0.5, 0.6, 0.0, 0.8]])
        torch.testing.assert_close((joint - shifted.sum(1)).exp(), (conditional - shifted).exp().prod(1))

    def test_finite_optimizer_changes_weights(self):
        model = SpatialNet()
        optimizer = torch.optim.Adam(model.parameters(), lr=3e-4)
        obs = observations(16)
        actions = torch.tensor([[0, 1, 600, 0]] * 4)
        with torch.no_grad():
            lp, _, values = evaluate_plan(model, obs, actions)
        rollout = {"observations": obs, "actions": actions, "logprobs": lp,
                   "advantages": torch.tensor([-1., -0.2, 0.2, 1.]),
                   "returns": values + torch.tensor([0.5, -0.5, 1., -1.])}
        before = model.stem.weight.detach().clone()
        metrics = update(model, optimizer, rollout, epochs=2, minibatch=2)
        self.assertFalse(torch.equal(before, model.stem.weight))
        self.assertTrue(all(np.isfinite(v) for v in metrics.values()))
        self.assertTrue(all(torch.isfinite(p).all() for p in model.parameters()))

    def test_export_count_and_float32_roundtrip(self):
        model = SpatialNet()
        encoded = model.encoded_weights()
        raw = base64.b64decode(encoded)
        self.assertEqual(len(raw), WEIGHT_COUNT * 4)
        weights = np.frombuffer(raw, dtype="<f4")
        self.assertTrue(np.isfinite(weights).all())
        np.testing.assert_array_equal(weights[:1728], model.stem.weight.detach().numpy().flatten())
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "SpatialWeights.java"
            model.export_java(target)
            self.assertEqual("".join(re.findall(r'"([A-Za-z0-9+/=]+)"', target.read_text())), encoded)


if __name__ == "__main__":
    unittest.main()
