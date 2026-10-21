"""Two-stage chooser check: rank candidates by the full-model score with the expensive
features (dOpp, dOwn, closes) zeroed; report how often the boss's chosen region is within the
top-K, and the top-1 accuracy of rescoring only the top-K with all features.

Usage: python shortlist_recall.py <sabotage.csv> <turns.csv> <boss-target-fit.json> [K,K,...] [drop1,drop2]
"""
import csv, json, sys, math
import numpy as np

sab_path, turns_path, fit_path = sys.argv[1:4]
Ks = [int(k) for k in (sys.argv[4] if len(sys.argv) > 4 else "2,3,4,6,8").split(',')]
DROP = set((sys.argv[5] if len(sys.argv) > 5 else "").split(',')) - {''}   # features removed from BOTH stages
fit = json.load(open(fit_path)); F = fit['features']; W = np.array(fit['weights'])
W = np.array([0.0 if f in DROP else w for f, w in zip(F, W)])
CHEAP = np.array([0.0 if f in ('dOpp', 'dOwn', 'closes') else 1.0 for f in F])
if DROP: print("dropped from both stages:", ",".join(sorted(DROP)))
turns = {}
for r in csv.DictReader(open(turns_path)): turns[(r['game'], int(r['turn']))] = r
sets = {}
for r in csv.DictReader(open(sab_path)):
    if r['player'] != r['leader']: continue
    key = (r['game'], int(r['turn']))
    t = turns.get(key)
    if t is None or int(t['target']) < 0 or t['sameTarget'] != '0': continue
    f = {k: float(r[k]) for k in r if k not in ('game', 'leader', 'player')}
    f['logFree'] = math.log1p(f['freeCells'])
    sets.setdefault(key, []).append((np.array([f[k] for k in F]), int(r['chosen'])))
n = 0; recall = {k: 0 for k in Ks}; top1 = {k: 0 for k in Ks}; sizes = []
for key, alts in sets.items():
    if sum(c for _, c in alts) != 1: continue
    X = np.array([v for v, _ in alts]); y = np.array([c for _, c in alts])
    cheap = X @ (W * CHEAP); full = X @ W
    order = np.argsort(-cheap); n += 1; sizes.append(len(alts))
    for k in Ks:
        top = order[:k]
        if y[top].any(): recall[k] += 1
        if y[top[np.argmax(full[top])]] == 1: top1[k] += 1
print(f"commit sets {n}, mean candidates {np.mean(sizes):.1f}")
for k in Ks: print(f"K={k}: shortlist recall {recall[k]/n:.3f}, two-stage top-1 {top1[k]/n:.3f}")
