"""Pool-collection invariants using scripted transitions, never native games."""
from __future__ import annotations

import base64
from pathlib import Path
import re
import tempfile
import unittest

from train_spatial import ROOT, SpatialNet, np, torch
from model import WEIGHT_ORDER
from pool_collector import PoolCollector
from run_v3_training import CONFIG, fresh_learner
from run_v2_training import sha256
from v2_support import weight_hash
from v3_support import INITIAL_OPPONENT_SHA256, POOL, WARM_START_SHA256, PoolAudit, export_v3_weights, frozen_history_integrity


def observation(marker, cell=600):
    build = [False] * 601
    build[cell] = True
    return {"planes": [0.] * 14400, "towns": [0.] * 588, "townCells": [-1] * 12,
            "regionIds": [-1] * 600, "scalars": [float(marker), 0., 0., 0., 0., 0.],
            "buildMask": build, "disruptMask": [False] * 600 + [True]}


def pool_state(env_id, seed, turn=0, done=False):
    seat = env_id % 2
    obs = [observation(-999, 91), observation(-999, 92)]
    obs[seat] = observation(env_id + 1, 3 + env_id * 4)
    return {"id": env_id, "seed": seed, "turn": turn, "done": done,
            "width": 1, "height": 1, "mapSha256": f"map-{seed}", "score": [10, 4],
            "rewardGap": [2, -2] if turn else [0, 0], "terminalSign": [1, -1] if done else [0, 0],
            "obs": obs, "learnerSeat": seat, "opponentSeat": 1 - seat, "opponentSpec": POOL[env_id],
            "opponentParams": {"fixture": POOL[env_id]},
            "opponentWeightsSha256": INITIAL_OPPONENT_SHA256 if env_id < 2 else WARM_START_SHA256 if env_id < 4 else None}


def diagnostic(env_id, turn=0):
    return {"envId": env_id, "turn": turn, "opponentSpec": POOL[env_id], "learnerSeat": env_id % 2,
            "opponentSeat": 1 - env_id % 2, "budgetMs": 60000, "elapsedMs": 1., "complete": True,
            "legal": True, "cached": False}


class PoisonOpponentPlans:
    def __iter__(self):
        raise AssertionError("Learner collector must never inspect opponent plans")

    def __getitem__(self, _):
        raise AssertionError("Learner collector must never inspect opponent plans")


class ScriptedPoolBridge:
    def __init__(self):
        self.states = {}
        self.committed = {}
        self.events = []
        self.plans = []

    def request(self, **message):
        op = message["op"]
        if op == "reset":
            ids = message.get("envIds", list(range(len(message["seeds"]))))
            result = []
            for env_id, seed in zip(ids, message["seeds"], strict=True):
                self.states[env_id] = pool_state(env_id, seed)
                result.append(self.states[env_id])
            return {"envs": result}
        if op == "opponentPlans":
            self.events.append("opponentPlans")
            for env_id in message["envIds"]:
                state = self.states[env_id]
                self.committed[env_id] = (state["seed"], state["turn"])
            return {"plans": PoisonOpponentPlans(),
                    "diagnostics": [diagnostic(env_id, self.states[env_id]["turn"]) for env_id in message["envIds"]]}
        if op == "observe":
            assert message["seats"] == [env_id % 2 for env_id in message["envIds"]]
            return {"obs": [observation(env_id + 1) for env_id in message["envIds"]]}
        if op == "step":
            assert "plans" not in message
            assert len(message["learnerPlans"]) == len(message["envIds"])
            self.plans.extend(message["learnerPlans"])
            result = []
            for env_id in message["envIds"]:
                old = self.states[env_id]
                assert self.committed[env_id] == (old["seed"], old["turn"])
                turn = old["turn"] + 1
                done = env_id == 0 or turn == 2
                self.states[env_id] = pool_state(env_id, old["seed"], turn, done)
                result.append(self.states[env_id])
            return {"envs": result}
        raise AssertionError(op)


class SpyZeroNet(torch.nn.Module):
    def __init__(self, bridge):
        super().__init__()
        self.bridge = bridge
        self.markers = []

    def forward(self, obs):
        self.bridge.events.append("policy")
        self.markers.extend(obs["scalars"][:, 0].tolist())
        size = len(obs["scalars"])
        return torch.zeros(size, 601), torch.zeros(size, 601), torch.zeros(size)


def load_frozen_v2_model():
    model = SpatialNet()
    source = (ROOT / "src/main/java/btk/bot/neural/SpatialV2Weights.java").read_text(encoding="utf-8")
    payload = "".join(re.findall(r'"([A-Za-z0-9+/=]+)"', source))
    weights = np.frombuffer(base64.b64decode(payload), dtype="<f4")
    state, at = model.state_dict(), 0
    with torch.no_grad():
        for name in WEIGHT_ORDER:
            count = state[name].numel()
            state[name].copy_(torch.from_numpy(weights[at:at + count].copy()).reshape_as(state[name]))
            at += count
    assert at == 3439
    return model


class V3Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_only_learner_masks_and_actions_enter_ppo(self):
        bridge = ScriptedPoolBridge()
        collector = PoolCollector(bridge, {**CONFIG, "environments": 2, "rollout_turns": 2, "map_seed_start": 10})
        model = SpyZeroNet(bridge)
        rollout = collector.collect(model)
        self.assertEqual(bridge.events[0], "opponentPlans")
        self.assertTrue(all(marker in (1., 2.) for marker in model.markers))
        self.assertEqual(rollout["actions"].shape, (4, 4))
        self.assertEqual(rollout["observations"]["planes"].shape[0], 16)
        self.assertEqual(rollout["actions"].tolist(), [[3, 600, 600, 600], [7, 600, 600, 600]] * 2)
        self.assertEqual(rollout["logprob_recompute_max_error"], 0.)
        self.assertEqual(rollout["value_recompute_max_error"], 0.)
        self.assertTrue((rollout["logprobs"] == 0).all())
        self.assertEqual([plan["cells"] for plan in bridge.plans], [[3], [7], [3], [7]])

    def test_mixed_seat_rewards_terminal_gae_and_reset_boundaries(self):
        bridge = ScriptedPoolBridge()
        collector = PoolCollector(bridge, {**CONFIG, "environments": 2, "rollout_turns": 2, "map_seed_start": 10})
        rollout = collector.collect(SpyZeroNet(bridge))
        torch.testing.assert_close(rollout["rewards"], torch.tensor([[.32, -.02], [.32, -.32]]))
        self.assertEqual(rollout["dones"].tolist(), [[True, False], [True, True]])
        torch.testing.assert_close(rollout["returns"], torch.tensor([[.32, -.02 - .99 * .95 * .32], [.32, -.32]]))
        self.assertEqual(len(collector.finished), 3)
        self.assertEqual(collector.total_turns, 4)
        self.assertEqual(collector.legal_decisions, 16)
        self.assertEqual([state["turn"] for state in collector.states], [0, 0])

    def test_slot_metadata_opponent_accounting_and_outcomes(self):
        audit = PoolAudit(10, 8)
        starts = [pool_state(i, 10 + i) for i in range(8)]
        audit.observe_reply({"op": "reset", "seeds": list(range(10, 18))}, {"envs": starts})
        audit.observe_reply({"op": "opponentPlans", "envIds": list(range(8))},
                            {"diagnostics": [diagnostic(i) for i in range(8)]})
        audit.observe_reply({"op": "step", "envIds": list(range(8))},
                            {"envs": [pool_state(i, 10 + i, 1, True) for i in range(8)]})
        audit.observe_reply({"op": "reset", "seeds": list(range(18, 26))},
                            {"envs": [pool_state(i, 18 + i) for i in range(8)]})
        summary = audit.summary()
        self.assertEqual(summary["environment_turns"], 8)
        self.assertEqual(summary["completed_games"], 8)
        self.assertEqual(summary["replacement_resets"], 8)
        for stats in summary["per_pool_training_outcomes"].values():
            self.assertEqual(stats["learner_wins"], 1)
            self.assertEqual(stats["learner_losses"], 1)
            self.assertEqual(stats["environment_turns"], 2)
        self.assertEqual(summary["late_panel_requested_slots"], 16)

    def test_bad_slot_or_failed_opponent_aborts_without_replacement(self):
        bad = pool_state(0, 10)
        bad["learnerSeat"] = 1
        with self.assertRaisesRegex(AssertionError, "slot/seat"):
            PoolAudit(10, 8).observe_reply({"op": "reset", "seeds": [10]}, {"envs": [bad]})
        audit = PoolAudit(10, 8)
        audit.observe_reply({"op": "reset", "seeds": list(range(10, 18))},
                            {"envs": [pool_state(i, 10 + i) for i in range(8)]})
        diagnostics = [diagnostic(i) for i in range(8)]
        diagnostics[6].update(complete=False, failure="scripted deadline")
        with self.assertRaisesRegex(AssertionError, "no retry"):
            audit.observe_reply({"op": "opponentPlans", "envIds": list(range(8))}, {"diagnostics": diagnostics})
        self.assertEqual(audit.opponent_failures[0]["seed"], 16)
        self.assertEqual(audit.total_steps, 0)
        self.assertEqual(audit.next_seed, 18)

    def test_fresh_optimizer_and_rng_from_exact_warm_start(self):
        model = load_frozen_v2_model()
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / "warm.pt"
            torch.save({"iteration": 64, "config": {"schema": "spatial-ppo-v2"}, "model": model.state_dict(),
                        "optimizer": {"state": "must not be restored"}}, source)
            learner, optimizer = fresh_learner(source)
        self.assertEqual(weight_hash(learner), WARM_START_SHA256)
        self.assertFalse(optimizer.state)
        self.assertEqual(optimizer.param_groups[0]["lr"], .0003)
        expected = torch.Generator().manual_seed(20261009).get_state()
        self.assertTrue(torch.equal(torch.get_rng_state(), expected))

    def test_generated_v3_artifact_preserves_frozen_history(self):
        before = frozen_history_integrity()
        model = load_frozen_v2_model()
        v2_path = ROOT / "src/main/java/btk/bot/neural/SpatialV2Weights.java"
        v2_hash = sha256(v2_path)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "SpatialV3Weights.java"
            export_v3_weights(model, path)
            source = path.read_text(encoding="utf-8")
            payload = "".join(re.findall(r'"([A-Za-z0-9+/=]+)"', source))
            self.assertEqual(payload, model.encoded_weights())
            self.assertIn("public final class SpatialV3Weights", source)
            with self.assertRaises(ValueError):
                export_v3_weights(model, Path(temporary) / "SpatialV2Weights.java")
        self.assertEqual(sha256(v2_path), v2_hash)
        self.assertEqual(frozen_history_integrity(), before)


if __name__ == "__main__":
    unittest.main()
