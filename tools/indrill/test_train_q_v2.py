"""Gradient, WAIT, negative-bootstrap and checkpoint contracts for v2."""
from pathlib import Path
import copy
import contextlib
import io
import json
import struct
import unittest
from unittest.mock import patch
import uuid

import numpy as np
import train_q as v1
import train_q_v2 as v2
import check_parity_v2 as parity
from test_train_q import episode


def scratch_directory():
    directory = Path(__file__).resolve().parents[2] / "build" / "indrill-tests" / "v2-contract"
    directory.mkdir(parents=True, exist_ok=True)
    return directory


class V2TrainerTests(unittest.TestCase):
    def test_finite_differences_include_value_head_and_shared_layers(self):
        rng = np.random.default_rng(202)
        model = v2.Model(rng)
        batch = v1.Batch([v1.nstep_transitions([episode()], 2)[0],
                          v1.nstep_transitions([episode(2, 1)], 2)[1]])
        build, ink, value = model.forward(batch)
        targets = value + v1.action_values(build, ink, batch.builds, batch.inks) + np.array([.3, -.4])
        importance = np.array([.6, 1], dtype=np.float32)
        _, _, analytic = model.loss_gradients(batch, targets, importance)
        for name, p in model.p.items():
            for index in rng.choice(p.size, size=min(8, p.size), replace=False):
                original = float(p.flat[index])
                p.flat[index] = original + .001
                high = model.loss_gradients(batch, targets, importance)[0]
                p.flat[index] = original - .001
                low = model.loss_gradients(batch, targets, importance)[0]
                p.flat[index] = original
                self.assertAlmostEqual(float(analytic[name].flat[index]), (high - low) / .002,
                                       delta=3e-4, msg=f"{name}[{index}]")

    def test_full_wait_updates_value_and_representation_with_zero_action_gradients(self):
        rng = np.random.default_rng(17)
        model = v2.Model(rng)
        ep = episode()
        ep.states[0].builds = ((), ())
        ep.states[0].inks = (-1, -1)
        batch = v1.Batch(v1.nstep_transitions([ep], 1)[:1])
        _, _, value = model.forward(batch)
        target = value + 1
        before, _, grad = model.loss_gradients(batch, target, np.ones(1, np.float32))
        self.assertAlmostEqual(float(grad["BV"][0]), -1, places=5)
        self.assertGreater(float(np.linalg.norm(grad["WV"])), 0)
        self.assertGreater(float(np.linalg.norm(grad["W0"])), 0)
        for name in ("WB", "WI", "BB", "BI"):
            np.testing.assert_array_equal(grad[name], 0)
        model.adam(grad, 1e-4)
        after, _, _ = model.loss_gradients(batch, target, np.ones(1, np.float32))
        self.assertLess(after, before)

    def test_value_added_once_and_greedy_bootstrap_can_be_negative(self):
        model = v2.Model(np.random.default_rng(21))
        for name in ("WB", "WI", "WV"):
            model.p[name].fill(0)
        model.p["BB"][0], model.p["BI"][0], model.p["BV"][0] = 1, 2, 7
        batch = v1.Batch(v1.nstep_transitions([episode()], 1)[:1])
        b, i, value = model.forward(batch)
        builds, inks = v1.choose_actions(b, i, batch)
        self.assertEqual(len(builds[0]), 3)
        self.assertEqual(float(value[0] + v1.action_values(b, i, builds, inks)[0]), 12)
        self.assertEqual(float(value[0] + v1.action_values(b, i, [()], [-1])[0]), 7)
        model.p["BV"][0] = -10
        b, i, value = model.forward(batch)
        builds, inks = v1.choose_actions(b, i, batch)
        self.assertEqual(float(value[0] + v1.action_values(b, i, builds, inks)[0]), -5)

    def test_binary_checkpoint_target_and_priority_resume_are_exact(self):
        rng = np.random.default_rng(29)
        model, target = v2.Model(rng), v2.Model(rng)
        target.copy_from(model)
        target.p["BV"][0] = -3
        model.updates = 57
        model.m["BV"][0], model.v["BV"][0] = .12, .3
        model.replay_keys = np.array([11, 22], dtype=np.uint64)
        model.replay_priorities = np.array([.5, 3], dtype=np.float64)
        directory = scratch_directory()
        binary = directory / "weights.bin"
        v2.write_weights(model, target, binary, directory / "IndrillV2Weights.java")
        self.assertEqual(binary.read_bytes()[:8], b"BTKW0002")
        self.assertEqual(len(binary.read_bytes()), 16 + 3387 * 4)
        loaded = v2.Model(rng)
        loaded.load(binary)
        for name in model.p:
            np.testing.assert_array_equal(model.p[name], loaded.p[name])
            np.testing.assert_array_equal(model.m[name], loaded.m[name])
            np.testing.assert_array_equal(model.v[name], loaded.v[name])
        self.assertEqual(loaded.updates, 57)
        following = v2.load_target(loaded, binary, rng)
        self.assertEqual(float(following.p["BV"][0]), -3)
        priorities, restored, new_priority = v2.restore_priorities(loaded, np.array([22, 11, 33], np.uint64))
        np.testing.assert_array_equal(priorities, [3, .5, 3])
        self.assertEqual(restored, 2)
        self.assertEqual(new_priority, 3)
        java = (directory / "IndrillV2Weights.java").read_text()
        self.assertIn("package btk.bot.current;", java)
        self.assertIn("public static final float[] WV", java)
        self.assertIn("public static final float[] BV", java)
        # A mismatched Java-readable artifact must not silently use its sidecar.
        data = bytearray(binary.read_bytes())
        data[-4:] = struct.pack(">f", 8)
        binary.write_bytes(data)
        with self.assertRaisesRegex(ValueError, "disagree in BV"):
            loaded.load(binary)

    def test_optional_v1_warm_start_copies_only_inference_tensors(self):
        rng = np.random.default_rng(31)
        old, target = v1.Model(rng), v1.Model(rng)
        target.copy_from(old)
        old.updates = 99
        path = scratch_directory() / "v1.bin"
        v1.write_weights(old, target, path)
        new = v2.Model(rng)
        new.warm_start_v1(path)
        for name in v1.PARAM_SHAPES:
            np.testing.assert_array_equal(new.p[name], old.p[name])
        np.testing.assert_array_equal(new.p["WV"], 0)
        np.testing.assert_array_equal(new.p["BV"], 0)
        self.assertEqual(new.updates, 0)
        self.assertTrue(all(not p.any() for p in new.m.values()))
        with self.assertRaisesRegex(ValueError, "BTKW0002"):
            v2.Model(rng).load(path)

    def test_parity_accepts_declared_float_tolerance_and_rejects_wrong_actions(self):
        model = v2.Model(np.random.default_rng(13))
        model.p["BV"][0] = 10000
        ep = episode()
        batch = v1.Batch(v1.nstep_transitions([ep], 1)[:1])
        b, i, value = model.forward(batch)
        builds, inks = v1.choose_actions(b, i, batch)
        row = dict(schemaVersion=2, seed=1, turn=0, player=0, width=ep.width,
                   height=ep.height, size=ep.size, regionCount=ep.region_count,
                   regions=ep.regions.tolist(), costs=ep.costs.tolist(),
                   buildCost=ep.costs.tolist(), legalBuild=ep.states[0].legal_build.tolist(),
                   legalInk=ep.states[0].legal_ink.tolist(),
                   neighbors=np.where(ep.neighbor_mask, ep.neighbors, -1).tolist(),
                   regionSizes=ep.region_sizes.tolist(), features=batch.x[0].tolist(),
                   buildQ=b[0].tolist(), inkQ=i[0].tolist(), stateValue=float(value[0]),
                   selectedCells=list(builds[0]), selectedInk=inks[0])
        self.assertTrue(parity.compare(model, [row])["passed"])
        row["stateValue"] += .005  # Within 1e-4 + 1e-6*10000.
        result = parity.compare(model, [row])
        self.assertTrue(result["passed"])
        json.dumps(result)  # Evidence must contain serializable native scalars.
        row["stateValue"] += .02
        self.assertFalse(parity.compare(model, [row])["passed"])
        row["stateValue"] = float(value[0])
        row["selectedCells"] = [] if builds[0] else [0]
        self.assertFalse(parity.compare(model, [row])["passed"])

    def test_parity_requires_complete_fixed_panel_and_frozen_weight_fingerprints(self):
        weight_hash, probe_hash = "a" * 64, "b" * 64
        exclusions = {"exclusionSha256": "c" * 64, "probeExclusionSha256": "d" * 64}
        manifest = dict(schemaVersion=1, complete=True, rows=18, probeSeeds=parity.PROBE_SEEDS,
                        probeTurns=parity.PROBE_TURNS, weightsSha256=weight_hash,
                        probeSha256=probe_hash, **exclusions)
        rows = [dict(seed=seed, turn=turn, player=player, weightsSha256=weight_hash)
                for seed in parity.PROBE_SEEDS for turn in parity.PROBE_TURNS for player in (0, 1)]
        self.assertTrue(parity.validate_probe_integrity(manifest, rows, weight_hash, probe_hash, exclusions)["complete"])
        for invalid_rows in (rows[:1], rows[:-1], rows + [rows[0]], rows[:-1] + [rows[0]]):
            with self.assertRaises(ValueError):
                parity.validate_probe_integrity(manifest, invalid_rows, weight_hash, probe_hash, exclusions)
        wrong_rows = copy.deepcopy(rows)
        wrong_rows[0]["weightsSha256"] = "e" * 64
        with self.assertRaisesRegex(ValueError, "weight fingerprint"):
            parity.validate_probe_integrity(manifest, wrong_rows, weight_hash, probe_hash, exclusions)
        for field, wrong in (("complete", False), ("rows", 1), ("probeSeeds", [1, 2, 3]),
                             ("weightsSha256", "e" * 64), ("probeSha256", "e" * 64),
                             ("probeExclusionSha256", "e" * 64)):
            invalid = dict(manifest, **{field: wrong})
            with self.assertRaises(ValueError, msg=field):
                parity.validate_probe_integrity(invalid, rows, weight_hash, probe_hash, exclusions)

    def test_parity_cli_resolves_canonical_exclusions_and_writes_complete_evidence(self):
        # Use a scratch project root so the CLI must resolve its canonical paths
        # through __file__; production exclusions, weights and evidence stay untouched.
        root = scratch_directory() / "parity-cli" / uuid.uuid4().hex
        tools_dir, bench_dir, probe_dir = root / "tools/indrill", root / "build/bench", root / "probe"
        for directory in (tools_dir, bench_dir, probe_dir):
            directory.mkdir(parents=True, exist_ok=True)
        canonical_files = {
            "exclusionSha256": bench_dir / "indrill-q-v2-training-excluded.json",
            "probeExclusionSha256": bench_dir / "indrill-q-v2-probe-excluded.json",
        }
        for index, path in enumerate(canonical_files.values()):
            path.write_text(json.dumps({"schemaVersion": 1, "fixture": index}) + "\n", encoding="utf-8")
        exclusions = {key: v1.file_sha256(path) for key, path in canonical_files.items()}
        rng = np.random.default_rng(87)
        model, target = v2.Model(rng), v2.Model(rng)
        target.copy_from(model)
        binary = root / "weights.bin"
        v2.write_weights(model, target, binary)  # No Java output at all.
        weight_hash = v1.file_sha256(binary)
        ep = episode()
        batch = v1.Batch(v1.nstep_transitions([ep], 1)[:1])
        b, i, value = model.forward(batch)
        builds, inks = v1.choose_actions(b, i, batch)
        rows = []
        for seed in parity.PROBE_SEEDS:
            for turn in parity.PROBE_TURNS:
                for player in (0, 1):
                    rows.append(dict(schemaVersion=1, weightsSha256=weight_hash,
                                     seed=seed, turn=turn, player=player, width=ep.width,
                                     height=ep.height, size=ep.size, regionCount=ep.region_count,
                                     regions=ep.regions.tolist(), costs=ep.costs.tolist(),
                                     buildCost=ep.costs.tolist(), legalBuild=ep.states[0].legal_build.tolist(),
                                     legalInk=ep.states[0].legal_ink.tolist(),
                                     neighbors=np.where(ep.neighbor_mask, ep.neighbors, -1).tolist(),
                                     regionSizes=ep.region_sizes.tolist(), features=batch.x[0].tolist(),
                                     buildQ=b[0].tolist(), inkQ=i[0].tolist(), stateValue=float(value[0]),
                                     selectedCells=list(builds[0]), selectedInk=inks[0]))
        probe = probe_dir / "probe.jsonl"
        probe.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
        manifest = dict(schemaVersion=1, complete=True, rows=18, probeSeeds=parity.PROBE_SEEDS,
                        probeTurns=parity.PROBE_TURNS, weightsSha256=weight_hash,
                        probeSha256=v1.file_sha256(probe), **exclusions)
        manifest_path = probe_dir / "manifest.json"
        manifest_path.write_text(json.dumps(manifest) + "\n", encoding="utf-8")
        out = probe_dir / "evidence.json"
        arguments = ["--weights", str(binary), "--probe", str(probe), "--out", str(out)]
        with patch.object(parity, "__file__", str(tools_dir / "check_parity_v2.py")):
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(parity.main(arguments), 0)
            result = json.loads(out.read_text())
            self.assertTrue(result["passed"])
            self.assertEqual(result["observations"], 18)
            self.assertEqual(result["qValues"], 18 * (ep.size + ep.region_count + 1))
            self.assertEqual(result["buildActionAgreements"], 18)
            self.assertEqual(result["inkActionAgreements"], 18)
            self.assertEqual(result["weights"]["sha256"], weight_hash)
            self.assertEqual(result["manifest"]["sha256"], v1.file_sha256(manifest_path))
            self.assertTrue(result["manifest"]["allRowWeightsMatchBinary"])
            self.assertEqual(result["manifest"]["exclusionHashes"], exclusions)
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                parity.main(arguments)  # The CLI must preserve write-once evidence.
            # Prove these scratch canonical files, rather than real project files,
            # were used: changing one fingerprint must prevent any new evidence.
            canonical_files["exclusionSha256"].write_text("{}\n", encoding="utf-8")
            rejected_out = probe_dir / "rejected.json"
            invalid_arguments = arguments[:-1] + [str(rejected_out)]
            with self.assertRaisesRegex(ValueError, "exclusion fingerprint"):
                parity.main(invalid_arguments)
            self.assertFalse(rejected_out.exists())


if __name__ == "__main__":
    unittest.main()
