"""Evidence guards for the fixed schedule; no model training or game simulation."""
import json
from pathlib import Path
import tempfile
import unittest

import run_outcome_v2 as driver


class CollectionEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.weights = self.root / "weights.json"
        self.weights.write_text("{}")
        self.data = self.root / "round-1"
        self.data.mkdir()
        self.games = [{"id": i, "seed": 2200000 + i // 2, "studentSeat": i % 2,
                       "opponent": ("v09", "neural-v1", "v07")[(i // 2) % 3],
                       "outcome": 1, "scoreStudent": 2, "scoreOpponent": 1,
                       "mapHash": f"{i // 2:064x}"} for i in range(192)]
        self.meta = {"schema": "BTKNEURALRL1", "games": 192, "seed": 2200000,
                     "temperature": 1, "weightsSha256": driver.sha(self.weights),
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


if __name__ == "__main__":
    unittest.main()
