"""Math/process checks for outcome-v4; run python -m unittest discover -s tools/neural -p test_outcome_v4.py."""
import copy
from contextlib import contextmanager
import json
from pathlib import Path
import shutil
import unittest
import uuid
from types import SimpleNamespace

import numpy as np

import train_outcome_v4 as train


@contextmanager
def temporary_directory():
    # Ordinary mkdir avoids Windows Python 3.13's owner-only mode-0700 temp ACL.
    parent = (train.ROOT / "build/tmp/neural-v4-tests").resolve()
    directory = parent / str(uuid.uuid4())
    directory.mkdir(parents=True)
    try:
        yield directory
    finally:
        if directory.resolve().parent != parent:
            raise ValueError("Test cleanup escaped its workspace")
        shutil.rmtree(directory)


class ObjectiveTest(unittest.TestCase):
    def setUp(self):
        self.scores = np.array([[.1, -.4, .8], [.3, -.7, 0.], [-.2, .2, .4]], dtype=np.float64)
        self.mask = np.array([[1, 1, 1], [1, 1, 0], [1, 1, 1]], dtype=bool)
        self.labels = np.array([2, 0, 1])
        self.old = train.log_softmax(self.scores + .05, self.mask)[np.arange(3), self.labels]
        self.teacher = np.array([[.2, 0., -.3], [-.1, .4, 500.], [.1, .2, -.4]])
        self.advantages = np.array([1., -1., .5])
        self.weights = np.ones(3)

    def check_finite_difference(self, advantages, teacher=None, old=None, kl=.02, temperature=1.):
        teacher = self.teacher if teacher is None else teacher
        old = self.old if old is None else old
        args = (self.mask, self.labels, old, teacher, advantages, self.weights)
        _, analytic, _ = train.objective(self.scores, *args, kl_coefficient=kl, temperature=temperature)
        epsilon = 1e-5
        numeric = np.zeros_like(self.scores)
        for index in np.ndindex(self.scores.shape):
            plus, minus = self.scores.copy(), self.scores.copy()
            plus[index] += epsilon
            minus[index] -= epsilon
            numeric[index] = (train.objective(plus, *args, kl_coefficient=kl, temperature=temperature)[0]
                              - train.objective(minus, *args, kl_coefficient=kl, temperature=temperature)[0]) / (2 * epsilon)
        np.testing.assert_allclose(analytic, numeric, rtol=1e-6, atol=1e-8)
        np.testing.assert_array_equal(analytic[~self.mask], 0.)
        return analytic

    def test_forward_kl_matches_finite_difference(self):
        self.check_finite_difference(np.zeros(3))

    def test_unclipped_positive_and_negative_match_finite_difference(self):
        gradient = self.check_finite_difference(self.advantages, kl=0.)
        self.assertLess(gradient[0, self.labels[0]], 0.)
        self.assertGreater(gradient[1, self.labels[1]], 0.)

    def test_clipped_positive_and_negative_stop_gradient(self):
        # Ratios 1.5 for positive advantage and .5 for negative advantage both clip.
        old = self.old - np.log([1.5, .5, 1.5])
        gradient = self.check_finite_difference(self.advantages, old=old, kl=0.)
        np.testing.assert_array_equal(gradient, 0.)

    def test_other_sides_of_clip_remain_active(self):
        old = self.old - np.log([.5, 1.5, .5])
        gradient = self.check_finite_difference(self.advantages, old=old, kl=0.)
        self.assertLess(gradient[0, self.labels[0]], 0.)
        self.assertGreater(gradient[1, self.labels[1]], 0.)

    def test_zero_advantage_at_prior_is_exact_noop(self):
        loss, gradient, _ = train.objective(self.scores, self.mask, self.labels, self.old,
                                           self.scores, np.zeros(3), self.weights)
        self.assertEqual(loss, 0.)
        np.testing.assert_array_equal(gradient, 0.)

    def test_masked_logits_do_not_affect_loss(self):
        args = (self.mask, self.labels, self.old, self.teacher, self.advantages, self.weights)
        before = train.objective(self.scores, *args)
        changed = self.scores.copy()
        changed[~self.mask] = 1e200
        after = train.objective(changed, *args)
        self.assertEqual(before[0], after[0])
        np.testing.assert_array_equal(before[1], after[1])

    def test_stable_softmax_extreme_logits(self):
        scores = np.array([[10000., -10000., -10.]])
        mask = np.array([[True, True, False]])
        value = train.log_softmax(scores, mask)
        self.assertEqual(value[0, 0], 0.)
        self.assertEqual(value[0, 1], -20000.)
        self.assertEqual(value[0, 2], -np.inf)

    def test_build_temperature_actor_and_untempered_kl_finite_difference(self):
        old = train.log_softmax(self.scores / .5, self.mask)[np.arange(3), self.labels]
        for ratios in ([1., 1., 1.], [1.5, .5, 1.5], [.5, 1.5, .5]):
            self.check_finite_difference(self.advantages, old=old - np.log(ratios), temperature=.5)

    def test_build_temperature_scales_actor_gradient_by_inverse_temperature(self):
        old = train.log_softmax(self.scores / .5, self.mask)[np.arange(3), self.labels]
        args = (self.mask, self.labels, old, self.teacher, self.advantages, self.weights)
        actual = train.objective(self.scores, *args, temperature=.5, kl_coefficient=0.)
        scaled_logits = train.objective(self.scores / .5, *args, temperature=1., kl_coefficient=0.)
        self.assertEqual(actual[0], scaled_logits[0])
        np.testing.assert_array_equal(actual[1], 2 * scaled_logits[1])

    def test_teacher_kl_is_independent_of_actor_temperature(self):
        args = (self.mask, self.labels, self.old, self.teacher, np.zeros(3), self.weights)
        baseline = train.objective(self.scores, *args, temperature=1.)
        colder = train.objective(self.scores, *args, temperature=.5)
        self.assertEqual(baseline[0], colder[0])
        np.testing.assert_array_equal(baseline[1], colder[1])
        self.assertEqual(baseline[2]["teacherKl"], colder[2]["teacherKl"])

    def test_actor_temperature_rejects_nonpositive_and_nonfinite(self):
        args = (self.mask, self.labels, self.old, self.teacher, self.advantages, self.weights)
        for temperature in (0., -.5, float("nan"), float("inf")):
            with self.assertRaisesRegex(ValueError, "temperature"):
                train.objective(self.scores, *args, temperature=temperature)


class PolicyStateTest(unittest.TestCase):
    @staticmethod
    def tiny_model():
        model = train.Mlp([3, 4, 1], np.random.default_rng(55))
        model.mean = np.array([.3, -.4, .2], dtype=np.float32)
        model.inv_std = np.array([.7, .25, 1.4], dtype=np.float32)
        model.step = 1
        return train.decode(train.export_head(model))

    def test_bootstrap_export_is_byte_exact(self):
        shipped = train.read_shipped_weights()
        for name, item in shipped.items():
            self.assertEqual(train.export_head(train.decode(item)), item, name)

    def test_normalization_bytes_survive_updated_export(self):
        for item in train.read_shipped_weights().values():
            model = train.decode(item)
            model.adam([np.ones_like(p) * .01 for p in model.params], 3e-5, weight_decay=0.)
            blob = train.base64.b64decode(train.export_head(model)["base64"])
            self.assertEqual(blob[:8 * model.sizes[0]], model.initial_blob[:8 * model.sizes[0]])

    def test_zero_advantage_at_prior_preserves_model(self):
        model = self.tiny_model()
        x = np.random.default_rng(9).normal(size=(2, 3, 3)).astype(np.float32)
        teacher = model.forward((x - model.mean) * model.inv_std)[-1][..., 0]
        mask, labels = np.ones((2, 3), dtype=bool), np.array([0, 1])
        old = train.log_softmax(teacher, mask)[np.arange(2), labels]
        batch = (x, mask, labels, old, teacher, np.zeros(2), np.ones(2))
        before = [p.copy() for p in model.params]
        _, grads, _ = train.loss_and_grads(model, batch)
        model.adam(grads, 3e-5, weight_decay=0.)
        for a, b in zip(before, model.params):
            np.testing.assert_array_equal(a, b)

    def test_mlp_backward_matches_finite_difference(self):
        model = self.tiny_model()
        x = np.random.default_rng(9).normal(size=(2, 3, 3)).astype(np.float32)
        teacher = model.forward((x - model.mean) * model.inv_std)[-1][..., 0]
        mask, labels = np.ones((2, 3), dtype=bool), np.array([0, 1])
        old = train.log_softmax(teacher, mask)[np.arange(2), labels]
        batch = (x, mask, labels, old, teacher, np.array([1., -1.]), np.ones(2))
        _, gradients, _ = train.loss_and_grads(model, batch)
        epsilon = .001
        for parameter, analytic in zip(model.params, gradients):
            for index in np.ndindex(parameter.shape):
                original = parameter[index].copy()
                parameter[index] = original + epsilon
                plus = train.loss_and_grads(model, batch)[0]
                parameter[index] = original - epsilon
                minus = train.loss_and_grads(model, batch)[0]
                parameter[index] = original
                self.assertAlmostEqual(float(analytic[index]), (plus - minus) / (2 * epsilon), delta=3e-4)

    def test_quantized_java_order_scalar_reference(self):
        model = self.tiny_model()
        raw = np.random.default_rng(11).normal(size=(7, 3)).astype(np.float32)
        actual = train.java_scores(model, raw)
        expected = []
        for row in raw:
            activation = (row - model.mean) * model.inv_std
            for layer, (weights, biases) in enumerate(zip(model.w, model.b)):
                following = []
                for output, bias in enumerate(biases):
                    total = bias
                    for feature, value in enumerate(activation):
                        total = np.float32(total + np.float32(weights[feature, output] * value))
                    following.append(total if layer == len(model.w) - 1 else max(total, np.float32(0.)))
                activation = np.asarray(following, dtype=np.float32)
            expected.append(activation[0])
        np.testing.assert_array_equal(actual, expected)

    def test_rebase_retains_optimizer(self):
        model = self.tiny_model()
        model.adam([np.ones_like(p) * .015 for p in model.params], 3e-5, weight_decay=0.)
        item = train.export_head(model)
        state = ([m.copy() for m in model.m], [v.copy() for v in model.v], model.step)
        result = train.rebase_to_export(model, item)
        self.assertTrue(result["parametersExactlyEqualDecodedBehavior"])
        for actual, expected in zip(model.params, train.decode(item).params):
            np.testing.assert_array_equal(actual, expected)
        for actual, expected in zip(model.m + model.v, state[0] + state[1]):
            np.testing.assert_array_equal(actual, expected)
        self.assertEqual(model.step, state[2])

    def test_build_behavior_parity_uses_cold_quantized_policy_and_keeps_logp(self):
        model = self.tiny_model()
        x = np.random.default_rng(12).normal(size=(2, 3, 3)).astype(np.float32)
        logits = train.java_scores(model, x.reshape(-1, 3)).reshape(2, 3)
        mask, labels = np.ones((2, 3), dtype=bool), np.array([0, 1])
        old = train.log_softmax(logits / .5, mask)[np.arange(2), labels].astype(np.float32)
        original = old.copy()
        head = SimpleNamespace(name="build", idx=np.zeros((2, 6), dtype=np.int32),
            gather=lambda selected: (x[selected], mask[selected], labels[selected], old[selected],
                                     logits[selected], np.ones(len(selected)), np.ones(len(selected))))
        result = train.check_data_policy(head, model, model)
        self.assertEqual(result["decisionsChecked"], 2)
        np.testing.assert_array_equal(old, original)
        old[:] = train.log_softmax(logits, mask)[np.arange(2), labels]
        with self.assertRaisesRegex(ValueError, "policy mismatch"):
            train.check_data_policy(head, model, model)

    def test_checkpoint_roundtrip_includes_optimizer_rng_and_prior(self):
        prior = train.read_shipped_weights()
        models = {name: train.decode(item) for name, item in prior.items()}
        for model in models.values():
            model.adam([np.ones_like(p) * .01 for p in model.params], 3e-5, weight_decay=0.)
        rng = np.random.default_rng(123)
        rng.random(3)
        rounds = [{"games": 192, "dataSha256": {"meta.json": "test"}}]
        with temporary_directory() as folder:
            file = Path(folder) / "test.npz"
            train.save_checkpoint(file, models, prior, rng, rounds)
            loaded, saved_prior, restored_rng, saved_rounds = train.load_checkpoint(file)
        self.assertEqual(saved_prior, prior)
        self.assertEqual(saved_rounds, rounds)
        np.testing.assert_array_equal(rng.random(10), restored_rng.random(10))
        for name in models:
            expected, actual = models[name], loaded[name]
            self.assertEqual(expected.step, actual.step)
            self.assertEqual(train.export_head(expected), train.export_head(actual))
            for a, b in zip(expected.params + expected.m + expected.v,
                            actual.params + actual.m + actual.v):
                np.testing.assert_array_equal(a, b)


class DataTest(unittest.TestCase):
    def test_leave_one_out_and_opponent_isolation(self):
        games = [{"id": 0, "opponent": "a", "outcome": 1},
                 {"id": 1, "opponent": "a", "outcome": -1},
                 {"id": 2, "opponent": "a", "outcome": 0},
                 {"id": 3, "opponent": "b", "outcome": 1},
                 {"id": 4, "opponent": "b", "outcome": 1}]
        advantage, _ = train.advantages_for_games(games)
        self.assertEqual(advantage, {0: 1.5, 1: -1.5, 2: 0., 3: 0., 4: 0.})
        changed = copy.deepcopy(games)
        changed[0]["outcome"] = -1
        updated, _ = train.advantages_for_games(changed)
        # A_g - outcome_g = -baseline does not depend on g's own outcome.
        self.assertEqual(advantage[0] - games[0]["outcome"], updated[0] - changed[0]["outcome"])
        self.assertEqual(advantage[3], updated[3])
        with self.assertRaises(ValueError):
            train.advantages_for_games(games[:1])

    def test_all_decisions_uniform_including_losses_and_longer_games(self):
        with temporary_directory() as folder:
            directory = Path(folder)
            np.arange(12, dtype="<f4").reshape(6, 2).tofile(directory / "build.bin")
            np.array([[0, 0, 0, 0, 2, 0], [1, 0, 0, 2, 2, 1], [1, 0, 1, 4, 2, 0]], dtype="<i4").tofile(directory / "build.idx")
            np.full(3, -.7, dtype="<f4").tofile(directory / "build.logp")
            np.arange(6, dtype="<f4").tofile(directory / "build.teacher")
            head = train.DataHead(directory, "build", {"buildFeatures": ["a", "b"]}, {0: 2., 1: -2.})
            np.testing.assert_array_equal(head.weights, [1., 1., 1.])
            np.testing.assert_array_equal(head.advantages, [2., -2., -2.])
            batch = head.gather(np.array([2, 0]))
            np.testing.assert_array_equal(batch[0][0], [[8., 9.], [10., 11.]])
            np.testing.assert_array_equal(batch[2], [0, 0])
            # Explicitly release Windows mappings before TemporaryDirectory cleanup.
            head.x._mmap.close()
            head.teacher._mmap.close()

    def test_output_overwrite_is_rejected_before_any_training(self):
        with temporary_directory() as directory:
            weights = directory / "weights.json"
            weights.write_text("preserve", encoding="utf-8")
            args = SimpleNamespace(command="train", out_weights=str(weights),
                                   out_checkpoint=str(directory / "state.npz"),
                                   evidence=str(directory / "evidence.json"), java_out=None)
            with self.assertRaisesRegex(ValueError, "overwrite"):
                train.require_new_outputs(args)
            self.assertEqual(weights.read_text(encoding="utf-8"), "preserve")

    def test_metadata_hash_and_feature_order_guards(self):
        with temporary_directory() as directory:
            prior = train.read_shipped_weights()
            weights = directory / "weights.json"
            train.write_json(weights, prior)
            games = [{"id": 0, "opponent": "v09", "outcome": 1},
                     {"id": 1, "opponent": "v09", "outcome": -1}]
            train.write_json(directory / "games.json", games)
            frozen = json.loads((train.ROOT / "docs/evidence/neural-v1-training.json").read_text(encoding="utf-8"))["dataMeta"]
            meta = {"schema": "BTKNEURALRL4", "sabotageTemperature": 1, "buildTemperature": .5,
                    "teacherTemperature": 1, "games": 2,
                    "weightsSha256": train.sha256(weights), "studentWeightsSha256": train.sha256(weights),
                    "idxFields": ["game", "turn", "k", "rowStart", "rowCount", "labelOffset"],
                    "checksums": {"games.json": train.sha256(directory / "games.json")}}
            for name in train.HEADS:
                meta[name + "Features"] = frozen[name + "Features"]
                meta["teacher" + name.title() + "BlobSha256"] = train.hashlib.sha256(prior[name]["base64"].encode("ascii")).hexdigest()
                for extension in ("bin", "idx", "logp", "teacher"):
                    path = directory / f"{name}.{extension}"
                    path.write_bytes(b"data")
                    meta["checksums"][path.name] = train.sha256(path)
            def store():
                (directory / "meta.json").write_text(json.dumps(meta), encoding="utf-8")
            store()
            self.assertEqual(train.validate_collection(directory, weights, prior)[1], games)
            meta["buildTemperature"] = 1
            store()
            with self.assertRaisesRegex(ValueError, "temperature"):
                train.validate_collection(directory, weights, prior)
            meta["buildTemperature"] = .5
            meta["weightsSha256"] = "incorrect"
            store()
            with self.assertRaisesRegex(ValueError, "weights hash"):
                train.validate_collection(directory, weights, prior)
            meta["weightsSha256"] = train.sha256(weights)
            meta["buildFeatures"] = list(reversed(meta["buildFeatures"]))
            store()
            with self.assertRaisesRegex(ValueError, "feature order"):
                train.validate_collection(directory, weights, prior)
            meta["buildFeatures"] = frozen["buildFeatures"]
            store()
            (directory / "build.bin").write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                train.validate_collection(directory, weights, prior)


if __name__ == "__main__":
    unittest.main()
