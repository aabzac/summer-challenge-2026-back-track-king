"""Read-only streaming audit of one frozen v2 replay block; no model training."""
from pathlib import Path
import argparse
import gzip
import hashlib
import json
import struct
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--out", type=Path, required=True, help="New output JSON path; existing evidence is never replaced")
args = parser.parse_args()
if args.out.exists():
    parser.error(f"Output already exists: {args.out}")
SOURCE = ROOT / "build/analysis/indrill-q-v2/round-6.bin.gz"
meta = json.loads(Path(str(SOURCE) + ".json").read_text())
digest = hashlib.sha256()
with SOURCE.open("rb") as source:
    for chunk in iter(lambda: source.read(1024 * 1024), b""):
        digest.update(chunk)
source_sha256 = digest.hexdigest()
assert source_sha256 == meta["datasetSha256"]
episodes = []
rows = []

with gzip.open(SOURCE, "rb") as f:
    def integer():
        return struct.unpack(">i", f.read(4))[0]

    assert f.read(8) == b"BTKQ0001"
    features, games = integer(), integer()
    assert features == 64 and games == len(meta["results"])
    for game in range(games):
        episode, width, height, regions = [integer() for _ in range(4)]
        size = width * height
        f.read(size * 5)
        states = integer()
        rewards, scores, actions = [], [], []
        for step in range(states):
            x = np.frombuffer(f.read(2 * size * features * 4), dtype=">f4").reshape(2, size, features)
            scores.append(round(float(x[0, 0, 19]) * 1000) - round(float(x[0, 0, 20]) * 1000))
            f.read(size + regions)
            actions.append(struct.unpack(">10i", f.read(40)))
            rewards.append(struct.unpack(">ff", f.read(8)))
            f.read(1)
        outcome = meta["results"][game]
        assert abs(scores[-1] - (outcome["score0"] - outcome["score1"])) < .1
        terminal = int(np.sign(outcome["score0"] - outcome["score1"]))
        r0 = np.array([r[0] for r in rewards[:-1]])
        shaping = r0.copy()
        shaping[-1] -= terminal
        projection_extra = shaping - np.diff(scores) / 100
        q_seats = (0, 1) if outcome["opponent"] == "self" else (1 - outcome["controlSeat"],)
        episodes.append(dict(game=game, seed=outcome["seed"], opponent=outcome["opponent"],
            controlSeat=outcome["controlSeat"], turns=states - 1,
            scoreDifference=outcome["score0"] - outcome["score1"],
            undiscountedReward0=float(r0.sum()),
            discountedReturn0=float(np.dot(r0, .99 ** np.arange(len(r0)))),
            terminalShapedReward0=float(shaping[-1]), terminalOutcome0=terminal,
            changedProjectionTurns=int((abs(projection_extra) > .01).sum())))
        for step in range(states - 1):
            a = actions[step]
            for player in (0, 1):
                sign = 1 if player == 0 else -1
                offset = player * 5
                ink, count = a[offset], a[offset + 1]
                rows.append(dict(game=game, turn=step, player=player,
                    qControlled=player in q_seats, losing=terminal * sign < 0,
                    reward=rewards[step][player], shaped=float(shaping[step] * sign),
                    terminal=step == states - 2, projectionExtra=float(projection_extra[step] * sign),
                    buildCount=count, ink=ink >= 0, fullWait=count == 0 and ink < 0))
    assert not f.read(1)

def describe(selected):
    def ratio(condition):
        return sum(condition(r) for r in selected) / max(1, len(selected))
    reward = np.array([abs(r["reward"]) for r in selected])
    shaped = np.array([abs(r["shaped"]) for r in selected])
    return dict(count=len(selected), absRewardQuantiles={str(q):float(np.quantile(reward, q))
        for q in (.5, .9, .99, 1)} if len(reward) else {},
        meanAbsReward=float(reward.mean()) if len(reward) else 0,
        meanAbsShaped=float(shaped.mean()) if len(shaped) else 0,
        absShapedAbove1Fraction=ratio(lambda r: abs(r["shaped"]) > 1),
        positiveShapedFraction=ratio(lambda r:r["shaped"] > 1e-6),
        changedProjectionFraction=ratio(lambda r:abs(r["projectionExtra"]) > .01),
        fullWaitFraction=ratio(lambda r:r["fullWait"]),
        placementFraction=ratio(lambda r:r["buildCount"] > 0),
        inkFraction=ratio(lambda r:r["ink"]))

returns = np.array([abs(e["undiscountedReward0"]) for e in episodes])
result = dict(source=str(SOURCE.relative_to(ROOT)), sourceSha256=source_sha256,
    sourceMetadataSha256Verified=True,
    schema="indrill-reward-diagnostic-v1", games=games, statesBothPerspectives=len(rows),
    notes=["Single fixed final training block; no evaluation games or training performed.",
           "Scoring features store integer scores/1000 in float32; recovered scores are rounded to integers. Projection delta compares at tolerance 0.01 reward units.",
           "Both perspectives duplicate magnitudes but reverse signs. Q-only metrics exclude frozen-control actions.",
           "Positive rewards in games eventually lost are not inherently incorrect credit assignments."],
    absEpisodeReturnQuantiles={str(q):float(np.quantile(returns,q)) for q in (.5,.9,.99,1)},
    gamesWithProjectionDeltaChangingActualReward=sum(e["changedProjectionTurns"] > 0 for e in episodes),
    all=describe(rows), qControlled=describe([r for r in rows if r["qControlled"]]),
    terminal=describe([r for r in rows if r["terminal"]]),
    qLosingLate=describe([r for r in rows if r["qControlled"] and r["losing"] and r["turn"] >= 75]),
    phases={f"{lo}-{hi}":describe([r for r in rows if lo <= r["turn"] <= hi])
            for lo,hi in ((0,24),(25,49),(50,74),(75,99))}, episodes=episodes)
args.out.parent.mkdir(parents=True, exist_ok=True)
with args.out.open("x", encoding="utf-8") as output:
    output.write(json.dumps(result, indent=2) + "\n")
print(json.dumps({k:v for k,v in result.items() if k != "episodes"}, indent=2))
