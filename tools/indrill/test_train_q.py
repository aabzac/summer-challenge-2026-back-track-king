"""Numerical/contract checks for the RL implementation, not strength tests."""
import gzip
import importlib.util
import itertools
from pathlib import Path
import struct
import sys
import unittest

import numpy as np

spec = importlib.util.spec_from_file_location("train_q", Path(__file__).with_name("train_q.py"))
q = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = q
spec.loader.exec_module(q)


def episode(width=3, height=2, terminal_reward=1.0):
    size = width * height
    rng = np.random.default_rng(199)
    region_ids = np.arange(size, dtype=np.int32) % 2
    legal_build, legal_ink = np.ones(size, dtype=bool), np.ones(2, dtype=bool)
    states = []
    for turn in range(4):
        features = rng.uniform(-.5, .5, (2, size, q.INPUTS)).astype(np.float32)
        terminal = turn == 3
        builds = ((), ()) if terminal else ((0, size - 1), (1,))
        inks = (-1, -1) if terminal else (0, 1)
        reward = terminal_reward if turn == 2 else 0.0
        states.append(q.State(features, legal_build, legal_ink, builds, inks,
                              (reward, -reward), turn >= 2))
    return q.Episode("fixture", width, height, 2, region_ids,
                     np.ones(size, dtype=np.uint8), states)


class TrainerTests(unittest.TestCase):
    def test_knapsack_matches_exhaustive_including_empty_and_costs(self):
        rng = np.random.default_rng(19)
        for _ in range(200):
            values = rng.normal(size=8).astype(np.float32)
            costs = rng.integers(1, 4, size=8)
            legal = rng.random(8) > .25
            chosen = q.choose_build(values, costs, legal)
            self.assertEqual(len(set(chosen)), len(chosen))
            self.assertLessEqual(sum(costs[list(chosen)]), 3)
            self.assertTrue(all(legal[list(chosen)]))
            brute = 0.0
            for count in (1, 2, 3):
                for cells in itertools.combinations(range(8), count):
                    if legal[list(cells)].all() and costs[list(cells)].sum() <= 3:
                        brute = max(brute, sum(float(values[c]) for c in cells))
            self.assertAlmostEqual(sum(float(values[c]) for c in chosen), brute, places=6)
        self.assertEqual(q.choose_build(np.zeros(4), np.ones(4, dtype=int), np.ones(4, bool)), ())
        self.assertEqual(q.choose_build(np.ones(4), np.ones(4, dtype=int), np.ones(4, bool)), (0, 1, 2))

    def test_nstep_terminal_does_not_bootstrap_or_cross_game(self):
        a, b = episode(terminal_reward=2), episode(terminal_reward=-3)
        items = q.nstep_transitions([a, b], nstep=3, gamma=.5)
        self.assertEqual(len(items), 12)
        self.assertEqual(items[0].reward, .5)
        self.assertEqual(items[0].discount, 0)
        self.assertEqual(items[0].next_step, 3)
        self.assertEqual(items[1].reward, -.5)
        self.assertEqual(items[6].reward, -.75)
        short = q.nstep_transitions([a], nstep=1, gamma=.5)
        self.assertEqual(short[0].discount, .5)
        self.assertEqual(short[0].next_step, 1)
        self.assertEqual(short[-2].discount, 0)
        self.assertEqual(short[-2].reward, 2)

    def test_gradients_through_both_heads_neighbors_and_global(self):
        rng = np.random.default_rng(37)
        model = q.Model(rng)
        transitions = [q.nstep_transitions([episode()], 2)[0],
                       q.nstep_transitions([episode(2, 1)], 2)[1]]
        batch = q.Batch(transitions)
        build, ink = model.forward(batch)
        targets = q.action_values(build, ink, batch.builds, batch.inks) + np.array([.3, -.4])
        importance = np.array([.6, 1], dtype=np.float32)
        _, _, analytic = model.loss_gradients(batch, targets, importance)
        # Central differences across every kind of parameter catch adjacency-transpose,
        # region-size and board-padding errors that a TD-loss smoke test would miss.
        for name, p in model.p.items():
            for index in rng.choice(p.size, size=min(8, p.size), replace=False):
                original = float(p.flat[index])
                p.flat[index] = original + .001
                high = model.loss_gradients(batch, targets, importance)[0]
                p.flat[index] = original - .001
                low = model.loss_gradients(batch, targets, importance)[0]
                p.flat[index] = original
                numeric = (high - low) / .002
                self.assertAlmostEqual(float(analytic[name].flat[index]), numeric,
                                       delta=3e-4, msg=f"{name}[{index}]")

    def test_binary_state_and_weights_contract(self):
        source = episode()
        scratch = Path(__file__).resolve().parents[2] / "build" / "indrill-tests"
        scratch.mkdir(parents=True, exist_ok=True)
        directory = scratch / "contract"
        directory.mkdir(parents=True, exist_ok=True)
        path = directory / "games.bin.gz"
        with gzip.open(path, "wb") as stream:
            stream.write(q.DATA_MAGIC + struct.pack(">ii", q.INPUTS, 1))
            stream.write(struct.pack(">iiii", 7, source.width, source.height, source.region_count))
            stream.write(source.regions.astype(">i4").tobytes())
            stream.write(source.costs.tobytes())
            stream.write(struct.pack(">i", len(source.states)))
            for state in source.states:
                stream.write(state.features.astype(">f4").tobytes())
                stream.write(state.legal_build.astype("u1").tobytes())
                stream.write(state.legal_ink.astype("u1").tobytes())
                for cells, ink in zip(state.builds, state.inks):
                    padded = list(cells) + [-1] * (3 - len(cells))
                    stream.write(struct.pack(">iiiii", ink, len(cells), *padded))
                stream.write(struct.pack(">ff?", *state.rewards, state.done))
        loaded = q.read_export(path, storage_dtype=np.float32)[0]
        np.testing.assert_array_equal(loaded.states[0].features, source.states[0].features)
        self.assertEqual(loaded.states[0].builds, source.states[0].builds)
        self.assertEqual(loaded.states[-2].rewards, source.states[-2].rewards)
        rng = np.random.default_rng(73)
        model, target = q.Model(rng), q.Model(rng)
        target.copy_from(model)
        weights = directory / "weights.bin"
        q.write_weights(model, target, weights, directory / "IndrillWeights.java")
        other = q.Model(rng)
        other.load(weights)
        for name in model.p:
            np.testing.assert_array_equal(other.p[name], model.p[name])
        weights.with_suffix(".npz").unlink()
        other.load(weights)
        for name in model.p:
            np.testing.assert_array_equal(other.p[name], model.p[name])

    def test_one_td_update_moves_both_heads_and_reduces_loss(self):
        rng = np.random.default_rng(47)
        model = q.Model(rng)
        batch = q.Batch(q.nstep_transitions([episode()], 3)[:2])
        build, ink = model.forward(batch)
        targets = q.action_values(build, ink, batch.builds, batch.inks) + 1
        importance = np.ones(2, dtype=np.float32)
        before, _, gradients = model.loss_gradients(batch, targets, importance)
        wb, wi = model.p["WB"].copy(), model.p["WI"].copy()
        model.adam(gradients, 1e-4)
        after, _, _ = model.loss_gradients(batch, targets, importance)
        self.assertLess(after, before)
        self.assertFalse(np.array_equal(wb, model.p["WB"]))
        self.assertFalse(np.array_equal(wi, model.p["WI"]))


if __name__ == "__main__":
    unittest.main()
