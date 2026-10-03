"""Training-map exclusions must cover the exact inventory consumed by practical-v4."""
import copy
import hashlib
import unittest

import audit_v2_training as audit


def declared_seeds():
    old = [base + game for base in (2000000, 2001000, 2002000, 2003000)
           for game in range(48)]
    new = [base + game for base in (2100000, 2101000, 2102000, 2103000, 2104000, 2105000)
           for game in range(128)]
    return old + new


def synthetic_manifest(seeds):
    # Synthetic identities exercise manifest validation without generating games.
    rows = [{"seed": seed, "mapHash": hashlib.sha256(str(seed).encode()).hexdigest()}
            for seed in seeds]
    return {"schemaVersion": 1, "trainingOnly": True,
            "hashScheme": "sha256-init-block-player-0", "maps": rows,
            "gamesPerMap": {row["mapHash"]: 1 for row in rows}}


class TrainingMapManifestTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.seeds = declared_seeds()
        cls.valid = synthetic_manifest(cls.seeds)

    def setUp(self):
        self.manifest = copy.deepcopy(self.valid)

    def reject(self, message):
        with self.assertRaisesRegex(audit.AuditError, message):
            audit.check_map_index(self.manifest, self.seeds)

    def test_accepts_exact_960_declared_maps_without_mutation(self):
        expected = {row["seed"]: row["mapHash"] for row in self.manifest["maps"]}
        self.assertEqual(len(self.seeds), 960)
        self.assertEqual(audit.check_map_index(self.manifest, self.seeds), expected)
        self.assertEqual(self.manifest, self.valid)

    def test_rejects_missing_gate_consumed_field(self):
        del self.manifest["gamesPerMap"]
        self.reject("missing gamesPerMap")

    def test_rejects_empty_gate_consumed_inventory(self):
        self.manifest["gamesPerMap"] = {}
        self.reject("gamesPerMap differs")

    def test_rejects_one_omitted_gate_consumed_hash(self):
        del self.manifest["gamesPerMap"][self.manifest["maps"][0]["mapHash"]]
        self.reject("gamesPerMap differs")

    def test_rejects_an_extra_gate_consumed_hash(self):
        self.manifest["gamesPerMap"]["0" * 64] = 1
        self.reject("gamesPerMap differs")

    def test_rejects_wrong_or_noninteger_gate_consumed_counts(self):
        identity = self.manifest["maps"][0]["mapHash"]
        for value in (0, 2, -1, 1.0, "1"):
            with self.subTest(count=value):
                self.manifest["gamesPerMap"][identity] = value
                self.reject("integer gamesPerMap count one")

    def test_rejects_boolean_count_even_though_true_equals_one(self):
        self.manifest["gamesPerMap"][self.manifest["maps"][0]["mapHash"]] = True
        self.reject("integer gamesPerMap count one")

    def test_rejects_wrong_schema_version_and_boolean_version(self):
        for value in (2, True, "1", None):
            with self.subTest(schemaVersion=value):
                self.manifest["schemaVersion"] = value
                self.reject("map-index schema")

    def test_requires_explicit_training_only_metadata(self):
        for value in (False, 1, "true", None):
            with self.subTest(trainingOnly=value):
                self.manifest["trainingOnly"] = value
                self.reject("canonical map-hash scheme")

    def test_rejects_noncanonical_or_missing_hash_scheme(self):
        for value in ("cg-global-graphics-utf8-sha256-v1", None):
            with self.subTest(hashScheme=value):
                self.manifest["hashScheme"] = value
                self.reject("canonical map-hash scheme")

    def test_rejects_missing_declared_seed_even_with_consistent_gate_field(self):
        removed = self.manifest["maps"].pop()
        del self.manifest["gamesPerMap"][removed["mapHash"]]
        self.reject("exactly the declared training seeds")

    def test_rejects_unexpected_seed_despite_960_consistent_entries(self):
        self.manifest["maps"][0]["seed"] = 9999999
        self.reject("exactly the declared training seeds")

    def test_rejects_extra_declared_map_and_matching_gate_hash(self):
        extra = {"seed": 9999999, "mapHash": "0" * 64}
        self.manifest["maps"].append(extra)
        self.manifest["gamesPerMap"][extra["mapHash"]] = 1
        self.reject("exactly the declared training seeds")

    def test_rejects_duplicate_seed(self):
        self.manifest["maps"][1]["seed"] = self.manifest["maps"][0]["seed"]
        self.reject("repeats a seed")

    def test_rejects_duplicate_fingerprints_with_consistent_gate_inventory(self):
        first, second = self.manifest["maps"][:2]
        del self.manifest["gamesPerMap"][second["mapHash"]]
        second["mapHash"] = first["mapHash"]
        self.reject("repeat a full-map fingerprint")

    def test_rejects_invalid_fingerprint(self):
        self.manifest["maps"][0]["mapHash"] = "invalid"
        self.reject("Invalid seed/map fingerprint")


if __name__ == "__main__":
    unittest.main()
