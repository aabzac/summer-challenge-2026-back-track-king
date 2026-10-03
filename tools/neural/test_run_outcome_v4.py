"""Evidence guards for the fixed schedule; no model training or game simulation."""
import json
from pathlib import Path
import unittest
from test_outcome_v4 import temporary_directory

import run_outcome_v4 as driver


class CollectionEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = temporary_directory()
        self.root = self.tmp.__enter__()
        self.addCleanup(lambda: self.tmp.__exit__(None, None, None))
        self.weights = self.root / "weights.json"
        self.weights.write_text("{}")
        self.data = self.root / "round-1"
        self.data.mkdir()
        self.games = [{"id": i, "seed": 3100000 + i // 2, "studentSeat": i % 2,
                       "opponent": ("v09", "neural-v1", "v07")[(i // 2) % 3],
                       "outcome": 1, "scoreStudent": 2, "scoreOpponent": 1,
                       "mapHash": f"{i // 2:064x}"} for i in range(192)]
        self.meta = {"schema": "BTKNEURALRL4", "games": 192, "seed": 3100000,
                     "sabotageTemperature": 1, "buildTemperature": .5, "teacherTemperature": 1,
                     "weightsSha256": driver.sha(self.weights),
                     "opponentFirstBudgetMs": 486, "opponentTurnBudgetMs": 36}
        self.save()

    def save(self):
        (self.data / "games.json").write_text(json.dumps(self.games))
        self.meta["checksums"] = {"games.json": driver.sha(self.data / "games.json")}
        (self.data / "meta.json").write_text(json.dumps(self.meta))

    def test_actual_records_must_match_paired_schedule(self):
        self.assertTrue(driver.verify_collection(self.data, self.weights, 1))
        self.games[1]["studentSeat"] = 0
        self.save()
        with self.assertRaisesRegex(ValueError, "schedule"):
            driver.verify_collection(self.data, self.weights, 1)

    def test_content_hash_checked_not_only_manifest_count(self):
        (self.data / "games.json").write_text(json.dumps(self.games[:-1] + [dict(self.games[-1], turns=999)]))
        with self.assertRaisesRegex(ValueError, "Changed dataset"):
            driver.verify_collection(self.data, self.weights, 1)

    def test_opponent_result_and_map_identity_are_checked(self):
        self.games[3]["mapHash"] = "f" * 64
        self.save()
        with self.assertRaisesRegex(ValueError, "different maps"):
            driver.verify_collection(self.data, self.weights, 1)
        self.games[3]["mapHash"] = self.games[2]["mapHash"]
        self.games[3]["outcome"] = -1
        self.save()
        with self.assertRaisesRegex(ValueError, "outcome mismatch"):
            driver.verify_collection(self.data, self.weights, 1)

    def test_partial_stage_is_not_implicitly_replayed(self):
        (self.data / "meta.json").unlink()
        with self.assertRaisesRegex(ValueError, "Incomplete collection"):
            driver.verify_collection(self.data, self.weights, 1)
        self.assertFalse(driver.verify_collection(self.root / "absent", self.weights, 1))

    def test_temperatures_and_legacy_ambiguity_are_rejected(self):
        for field, changed in (("sabotageTemperature", .5), ("buildTemperature", 1), ("teacherTemperature", .5)):
            original = self.meta[field]
            self.meta[field] = changed
            self.save()
            with self.assertRaisesRegex(ValueError, "plan"):
                driver.verify_collection(self.data, self.weights, 1)
            self.meta[field] = original
        self.meta["temperature"] = 1
        self.save()
        with self.assertRaisesRegex(ValueError, "plan"):
            driver.verify_collection(self.data, self.weights, 1)


class ResumeGuardsTest(unittest.TestCase):
    def test_completed_update_resume_verifies_lineage_steps_rng_and_full_data_pins(self):
        import train_outcome_v4 as trainer
        with temporary_directory() as directory:
            data = directory / "round-1"
            data.mkdir()
            previous_weights, previous_checkpoint = directory / "w0.json", directory / "c0.npz"
            weights, checkpoint, evidence = directory / "w1.json", directory / "c1.npz", directory / "e1.json"
            prior = trainer.read_shipped_weights()
            models = {name: trainer.decode(item) for name, item in prior.items()}
            rng = trainer.np.random.default_rng(driver.SEED)
            trainer.write_json(previous_weights, prior)
            trainer.save_checkpoint(previous_checkpoint, models, prior, rng, [])
            trainer.write_json(data / "games.json", [{"id": i} for i in range(driver.GAMES)])
            frozen = driver.read(trainer.ROOT / "docs/evidence/neural-v1-training.json")["dataMeta"]
            meta = {"schema": "BTKNEURALRL4", "sabotageTemperature": 1., "buildTemperature": .5,
                    "teacherTemperature": 1., "games": driver.GAMES,
                    "weightsSha256": driver.sha(previous_weights), "studentWeightsSha256": driver.sha(previous_weights),
                    "idxFields": ["game", "turn", "k", "rowStart", "rowCount", "labelOffset"]}
            heads = {}
            for name, model in models.items():
                meta[name + "Features"] = frozen[name + "Features"]
                meta["teacher" + name.title() + "BlobSha256"] = trainer.hashlib.sha256(prior[name]["base64"].encode()).hexdigest()
                trainer.np.zeros((4, model.sizes[0]), dtype="<f4").tofile(data / (name + ".bin"))
                trainer.np.array([[0, 0, 0, 0, 2, 0], [1, 0, 0, 2, 2, 1]], dtype="<i4").tofile(data / (name + ".idx"))
                trainer.np.full(2, -.7, dtype="<f4").tofile(data / (name + ".logp"))
                trainer.np.zeros(4, dtype="<f4").tofile(data / (name + ".teacher"))
                for _ in range(4):
                    model.adam([trainer.np.zeros_like(p) for p in model.params], 3e-5, weight_decay=0.)
                    rng.permutation(2)
                heads[name] = {"decisions": 2, "rebase": {"parametersExactlyEqualDecodedBehavior": True,
                                                       "optimizerStepRetained": 0},
                               "history": [{"epoch": epoch, "optimizerStep": epoch} for epoch in range(1, 5)]}
            meta["checksums"] = {p.name: driver.sha(p) for p in data.iterdir()}
            trainer.write_json(data / "meta.json", meta)
            hashes = {**meta["checksums"], "meta.json": driver.sha(data / "meta.json")}
            rounds = [{"dataSha256": hashes, "games": driver.GAMES}]
            trainer.write_json(weights, {name: trainer.export_head(model) for name, model in models.items()})
            trainer.save_checkpoint(checkpoint, models, prior, rng, rounds)
            result = {"round": 1, "games": driver.GAMES, "config": trainer.CONFIG, "heads": heads,
                      "inputWeightsSha256": driver.sha(previous_weights), "inputCheckpointSha256": driver.sha(previous_checkpoint),
                      "outputWeightsSha256": driver.sha(weights), "outputCheckpointSha256": driver.sha(checkpoint),
                      "inputDataSha256": hashes}
            trainer.write_json(evidence, result)
            args = (1, previous_weights, previous_checkpoint, weights, checkpoint, evidence, data)
            self.assertTrue(driver.verify_update(*args))
            original = checkpoint.read_bytes()
            for mutation, message in (("lineage", "rounds"), ("rng", "RNG continuation"), ("steps", "optimizer step")):
                loaded, saved_prior, saved_rng, saved_rounds = trainer.load_checkpoint(checkpoint)
                if mutation == "lineage":
                    saved_rounds[0]["games"] -= 1
                elif mutation == "rng":
                    saved_rng.random()
                else:
                    loaded["build"].step += 1
                checkpoint.unlink()
                trainer.save_checkpoint(checkpoint, loaded, saved_prior, saved_rng, saved_rounds)
                result["outputCheckpointSha256"] = driver.sha(checkpoint)
                evidence.write_text(json.dumps(result), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, message):
                    driver.verify_update(*args)
                checkpoint.write_bytes(original)
            result["outputCheckpointSha256"] = driver.sha(checkpoint)
            result["inputDataSha256"] = {"meta.json": hashes["meta.json"]}
            evidence.write_text(json.dumps(result), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "complete collection"):
                driver.verify_update(*args)

    def test_plan_requires_explicit_resume_and_identical_source_pins(self):
        with temporary_directory() as directory:
            path = directory / "plan.json"
            plan = {"sourceSha256": {"source.java": "abc"}, "rounds": 8}
            driver.verify_plan(path, plan, False)
            driver.verify_plan(path, plan, True)
            for altered, resume in ((plan, False), ({**plan, "rounds": 9}, True),
                                    ({**plan, "sourceSha256": {"source.java": "changed"}}, True),
                                    ({**plan, "sourceSha256": {"source.java": "abc", "new.java": "def"}}, True)):
                with self.assertRaisesRegex(ValueError, "identical recorded plan"):
                    driver.verify_plan(path, altered, resume)
            self.assertEqual(driver.read(path), plan)
            with self.assertRaisesRegex(ValueError, "No plan"):
                driver.verify_plan(directory / "absent.json", plan, True)

    def test_bootstrap_resume_checks_exact_rng_model_and_optimizer(self):
        import train_outcome_v4 as trainer
        with temporary_directory() as directory:
            weights, checkpoint = directory / "weights.json", directory / "checkpoint.npz"
            prior = trainer.read_shipped_weights()
            trainer.write_json(weights, prior)
            with self.assertRaisesRegex(ValueError, "Incomplete bootstrap"):
                driver.verify_bootstrap(weights, checkpoint, trainer)
            models = {name: trainer.decode(item) for name, item in prior.items()}
            rng = trainer.np.random.default_rng(driver.SEED)
            trainer.save_checkpoint(checkpoint, models, prior, rng, [])
            driver.verify_bootstrap(weights, checkpoint, trainer)
            checkpoint.unlink()
            rng.random()
            trainer.save_checkpoint(checkpoint, models, prior, rng, [])
            with self.assertRaisesRegex(ValueError, "RNG"):
                driver.verify_bootstrap(weights, checkpoint, trainer)
            checkpoint.unlink()
            rng = trainer.np.random.default_rng(driver.SEED)
            models["build"].params[0][0, 0] += 1
            trainer.save_checkpoint(checkpoint, models, prior, rng, [])
            with self.assertRaisesRegex(ValueError, "model/optimizer"):
                driver.verify_bootstrap(weights, checkpoint, trainer)
            checkpoint.unlink()
            models = {name: trainer.decode(item) for name, item in prior.items()}
            models["build"].m[0][0, 0] = 1
            trainer.save_checkpoint(checkpoint, models, prior, rng, [])
            with self.assertRaisesRegex(ValueError, "model/optimizer"):
                driver.verify_bootstrap(weights, checkpoint, trainer)


if __name__ == "__main__":
    unittest.main()
