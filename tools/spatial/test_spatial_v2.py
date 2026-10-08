"""New v2 wiring checks; no training run and no changes to the frozen v1 tests."""
from __future__ import annotations

import base64
import copy
from pathlib import Path
import re
import tempfile
import unittest

from train_spatial import Collector, SpatialNet, tensor_observations, torch
from v2_support import GameAudit, INITIAL_SHA256, TimedSpatialNet, export_java_class, weight_hash
from compare_v2_panel import compare_panel
from run_v2_training import CONFIG, first_panel


def empty_observation():
    mask = [False] * 600 + [True]
    return {"planes": [0.] * (24 * 600), "towns": [0.] * (12 * 49),
            "townCells": [-1] * 12, "regionIds": [-1] * 600, "scalars": [0.] * 6,
            "buildMask": mask.copy(), "disruptMask": mask.copy()}


def state(env_id=0, seed=10, turn=0, done=False):
    return {"id": env_id, "seed": seed, "width": 1, "height": 1, "mapSha256": f"map-{seed}",
            "turn": turn, "done": done, "score": [5, 0] if turn else [0, 0],
            "rewardGap": [5, -5] if turn else [0, 0],
            "terminalSign": [1, -1] if done else [0, 0],
            "obs": [empty_observation(), empty_observation()]}


class FakeTerminalBridge:
    """Scripted one-transition episodes; terminal facts are supplied as test data."""
    def __init__(self):
        self.audit = GameAudit(10, 1)
        self.seed = None

    def request(self, **message):
        op = message["op"]
        if op == "reset":
            self.seed = message["seeds"][0]
            reply = {"envs": [state(seed=self.seed)]}
        elif op == "step":
            reply = {"envs": [state(seed=self.seed, turn=1, done=True)]}
        elif op == "observe":
            reply = {"obs": [empty_observation() for _ in message["envIds"]]}
        else:
            raise AssertionError(op)
        self.audit.observe_reply(message, reply)
        return reply


class ZeroNet(torch.nn.Module):
    def forward(self, obs):
        count = obs["planes"].shape[0]
        return torch.zeros(count, 601), torch.zeros(count, 601), torch.zeros(count)


class V2Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_exact_initializer_and_instrumentation_rng_neutral(self):
        torch.manual_seed(20261008)
        original = SpatialNet()
        expected_rng = torch.get_rng_state().clone()
        torch.manual_seed(20261008)
        instrumented = TimedSpatialNet()
        self.assertEqual(weight_hash(original), INITIAL_SHA256)
        self.assertEqual(weight_hash(instrumented), INITIAL_SHA256)
        self.assertTrue(torch.equal(torch.get_rng_state(), expected_rng))
        obs = tensor_observations([empty_observation()])
        with torch.no_grad():
            for before, after in zip(original(obs), instrumented(obs), strict=True):
                torch.testing.assert_close(before, after, rtol=0, atol=0)
        self.assertTrue(torch.equal(torch.get_rng_state(), expected_rng))
        self.assertGreater(instrumented.forward_seconds, 0)

    def test_frozen_collector_real_terminal_masks_and_resets(self):
        bridge = FakeTerminalBridge()
        collector = Collector(bridge, {**CONFIG, "environments": 1, "map_seed_start": 10, "rollout_turns": 2})
        rollout = collector.collect(ZeroNet())
        self.assertTrue(rollout["dones"].all())
        torch.testing.assert_close(rollout["returns"], torch.tensor([[0.35, -0.35], [0.35, -0.35]]))
        self.assertTrue((rollout["logprobs"] == 0).all())
        audit = bridge.audit.summary()
        self.assertEqual(audit["completed_games"], 2)
        self.assertEqual(audit["replacement_resets"], 2)
        self.assertEqual(audit["completed_environment_turns"] + audit["unfinished_environment_turns"], 2)
        self.assertEqual([row["seed"] for row in audit["completed"]], [10, 11])
        self.assertEqual(audit["unfinished"][0]["seed"], 12)
        self.assertEqual(audit["unfinished"][0]["length"], 0)
        self.assertEqual(audit["unfinished"][0]["score"], [0, 0])
        self.assertFalse(audit["unfinished"][0]["done"])

    def test_map_exclusion_and_duplicate_rejected_without_replacement(self):
        audit = GameAudit(10, 1, excluded_hashes={"map-10"})
        with self.assertRaisesRegex(AssertionError, "overlap"):
            audit.observe_reply({"op": "reset", "seeds": [10]}, {"envs": [state()]})
        audit = GameAudit(10, 1)
        audit.observe_reply({"op": "reset", "seeds": [10]}, {"envs": [state()]})
        audit.observe_reply({"op": "step"}, {"envs": [state(turn=1, done=True)]})
        duplicate = state(seed=11)
        duplicate["mapSha256"] = "map-10"
        with self.assertRaisesRegex(AssertionError, "overlap"):
            audit.observe_reply({"op": "reset", "seeds": [11]}, {"envs": [duplicate]})

    def test_nonterminal_reset_and_unreset_terminal_step_rejected(self):
        audit = GameAudit(10, 1)
        audit.observe_reply({"op": "reset", "seeds": [10]}, {"envs": [state()]})
        with self.assertRaisesRegex(AssertionError, "nonterminal"):
            audit.observe_reply({"op": "reset", "seeds": [11]}, {"envs": [state(seed=11)]})
        audit = GameAudit(10, 1)
        audit.observe_reply({"op": "reset", "seeds": [10]}, {"envs": [state()]})
        audit.observe_reply({"op": "step"}, {"envs": [state(turn=1, done=True)]})
        with self.assertRaisesRegex(AssertionError, "without resetting"):
            audit.observe_reply({"op": "step"}, {"envs": [state(turn=2, done=True)]})

    def test_late_panel_slots_are_fixed_and_missing_stay_missing(self):
        audit = GameAudit(10, 8)
        entry = {"seed": 10, "turn": 63, "mapSha256": "map-10"}
        obs = empty_observation()
        audit.capture(entry, 0, 0, obs)
        audit.capture({**entry, "turn": 62}, 0, 0, obs)
        audit.capture({**entry, "seed": 12}, 0, 0, obs)
        audit.capture(entry, 0, 0, obs)
        self.assertEqual(len(audit.late_rows), 1)
        self.assertEqual(len(audit.summary()["late_panel_missing_slots"]), 31)

    def test_initial_panel_selection_is_fixed(self):
        class Panel:
            parity_observations = list(range(128))
        panel = Panel()
        selected = first_panel(panel)
        self.assertEqual(len(selected), 64)
        self.assertEqual(selected[:8], list(range(8)))
        self.assertEqual(selected[-8:], list(range(112, 120)))
        self.assertEqual(panel.parity_observations, [])

    def test_generated_classes_retain_exact_payload_and_reject_v1_name(self):
        torch.manual_seed(20261008)
        model = SpatialNet()
        with tempfile.TemporaryDirectory() as temporary:
            for name in ("SpatialInitialWeights", "SpatialV2Weights"):
                path = Path(temporary) / f"{name}.java"
                export_java_class(model, path, name)
                source = path.read_text()
                self.assertIn(f"class {name}", source)
                payload = "".join(re.findall(r'"([A-Za-z0-9+/=]+)"', source))
                self.assertEqual(payload, model.encoded_weights())
                self.assertEqual(len(base64.b64decode(payload)), 3439 * 4)
            with self.assertRaises(ValueError):
                export_java_class(model, Path(temporary) / "SpatialWeights.java", "SpatialWeights")

    def test_late_panel_comparator_checks_payload_logits_and_selected_action(self):
        model = SpatialNet()
        observation = empty_observation()
        with torch.no_grad():
            build, ink, value = model(tensor_observations([observation]))
        row = {"seed": 1, "turn": 75, "me": 0, "stage": 3, "observation": observation,
               "java": {"buildLogits": build[0].tolist(), "disruptLogits": ink[0].tolist(),
                        "value": float(value[0]), "selected": 600}}
        panel = {"label": "test", "weightsSha256": weight_hash(model), "rows": [row]}
        self.assertTrue(compare_panel(model, panel)["passed"])
        wrong = copy.deepcopy(panel)
        wrong["rows"][0]["java"]["selected"] = 0
        self.assertFalse(compare_panel(model, wrong)["passed"])
        wrong = copy.deepcopy(panel)
        wrong["rows"][0]["java"]["value"] += 0.01
        self.assertFalse(compare_panel(model, wrong)["passed"])
        with self.assertRaises(AssertionError):
            compare_panel(model, {**panel, "weightsSha256": "wrong"})
        wrong = copy.deepcopy(panel)
        wrong["rows"][0]["java"]["value"] = float("nan")
        with self.assertRaises(AssertionError):
            compare_panel(model, wrong)


if __name__ == "__main__":
    unittest.main()
