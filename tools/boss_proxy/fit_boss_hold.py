"""Calibrate the hold/abandon rule of the boss proxy.

For every boss turn after the first disrupt where the previous target was NOT inked, score all
candidate regions with the fitted logit and compare the held region's score with the best other
region's score. Reports, for several margins m, the predicted hold rate (hold iff best_other <
held + m) against the observed hold rate, and the agreement with the boss's actual decision.

Usage: python fit_boss_hold.py <sabotage.csv> <turns.csv> <fit.json>
"""
import csv, json, sys, math
import numpy as np

sab_path, turns_path, fit_path = sys.argv[1:4]
fit = json.load(open(fit_path))
F, W = fit['features'], np.array(fit['weights'])

turns = {}
for r in csv.DictReader(open(turns_path)):
    turns[(r['game'], int(r['turn']))] = r

sets = {}
for r in csv.DictReader(open(sab_path)):
    if r['player'] != r['leader']:
        continue
    key = (r['game'], int(r['turn']))
    t = turns.get(key)
    if t is None or int(t['turn']) == 0:
        continue
    f = {k: float(r[k]) for k in r if k not in ('game', 'leader', 'player')}
    f['logFree'] = math.log1p(f['freeCells'])
    sets.setdefault(key, []).append((int(r['region']), float(np.dot(W, [f[k] for k in F])), int(r['closes'])))

# previous target per (game, turn)
prev_target = {}
by_game = {}
for (g, t), r in turns.items():
    by_game.setdefault(g, {})[t] = r
for g, d in by_game.items():
    for t in sorted(d):
        if t - 1 in d:
            prev_target[(g, t)] = int(d[t - 1]['target'])

rows = []
for key, alts in sets.items():
    t = turns[key]
    pt = prev_target.get(key, -1)
    if pt < 0 or t['prevTargetInked'] == '1':
        continue
    held = [s for r, s, _ in alts if r == pt]
    if not held:
        continue  # previous target no longer a candidate (e.g. region now empty) -> forced switch
    best_other = max([s for r, s, _ in alts if r != pt], default=-1e9)
    cur = int(t['target'])
    rows.append((held[0], best_other, cur == pt, cur))
rows = np.array([(h, b, int(k), c) for h, b, k, c in rows])
held, best, kept = rows[:, 0], rows[:, 1], rows[:, 2].astype(bool)
print(f"turns with a holdable previous target: {len(rows)}; observed hold rate {kept.mean():.3f}")
print(f"forced switches (previous target not a candidate any more): see commit analysis")
for m in (0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0):
    pred = best < held + m
    agree = (pred == kept).mean()
    print(f"  margin {m:4.1f}: predicted hold {pred.mean():.3f}  agreement {agree:.3f}  "
          f"(hold when boss held {pred[kept].mean():.3f}, switch when boss switched {(~pred[~kept]).mean():.3f})")
