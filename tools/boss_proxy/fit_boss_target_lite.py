"""Refit the boss target logit on a chosen feature subset (cheap in-rollout features).

Usage: python fit_boss_target_lite.py <sabotage.csv> <turns.csv> <out.json> feat1,feat2,...
Same protocol as fit_boss_target.py (boss commitment turns, 80/20 split, seed 1).
'logFree' = log1p(freeCells); other names are sabotage.csv columns.
"""
import csv, json, sys, math
import numpy as np

sab_path, turns_path, out_path, feats = sys.argv[1:5]
FEATURES = feats.split(',')
turns = {}
for r in csv.DictReader(open(turns_path)):
    turns[(r['game'], int(r['turn']))] = r
sets = {}
for r in csv.DictReader(open(sab_path)):
    if r['player'] != r['leader']: continue
    key = (r['game'], int(r['turn']))
    t = turns.get(key)
    if t is None or int(t['target']) < 0: continue
    f = {k: float(r[k]) for k in r if k not in ('game', 'leader', 'player')}
    f['logFree'] = math.log1p(f['freeCells'])
    sets.setdefault(key, []).append(([f[k] for k in FEATURES], int(r['chosen'])))

def build(keys):
    X, Y, G = [], [], []
    gi = 0
    for k in keys:
        alts = sets[k]
        if sum(c for _, c in alts) != 1: continue
        for vec, c in alts: X.append(vec); Y.append(c); G.append(gi)
        gi += 1
    return np.array(X), np.array(Y), np.array(G)

commit_keys = [k for k in sets if turns[k]['sameTarget'] == '0']
rng = np.random.default_rng(1); keys = list(commit_keys); rng.shuffle(keys)
split = int(len(keys) * 0.8)
Xtr, Ytr, Gtr = build(keys[:split]); Xte, Yte, Gte = build(keys[split:])
mu, sd = Xtr.mean(0), Xtr.std(0) + 1e-9
Ztr, Zte = (Xtr - mu) / sd, (Xte - mu) / sd

def loglik_grad(w, Z, Y, G, l2=1e-3):
    u = Z @ w; n = G.max() + 1
    umax = np.full(n, -1e30); np.maximum.at(umax, G, u)
    e = np.exp(u - umax[G]); Zs = np.zeros(n); np.add.at(Zs, G, e); p = e / Zs[G]
    ll = np.sum(np.log(p[Y == 1] + 1e-300)) - l2 * np.sum(w * w)
    return ll, Z[Y == 1].sum(0) - (Z * p[:, None]).sum(0) - 2 * l2 * w

w = np.zeros(len(FEATURES))
for it in range(4000):
    ll, g = loglik_grad(w, Ztr, Ytr, Gtr); w += 0.05 * g / (Gtr.max() + 1)

def top1(w, Z, Y, G):
    u = Z @ w; n = G.max() + 1
    best = np.full(n, -1e30); arg = np.full(n, -1)
    for i in range(len(u)):
        if u[i] > best[G[i]]: best[G[i]] = u[i]; arg[G[i]] = i
    return float(np.mean(Y[arg] == 1))

tr, te = top1(w, Ztr, Ytr, Gtr), top1(w, Zte, Yte, Gte)
print("features:", ",".join(FEATURES))
print("sets train %d test %d; train top-1 %.3f  test top-1 %.3f  (uniform %.3f)" % (
    Gtr.max() + 1, Gte.max() + 1, tr, te, (Gte.max() + 1) / len(Yte)))
w_raw = w / sd
for k, wi in zip(FEATURES, w_raw): print(f"  {k:16s} {wi:+.6f}")
json.dump({'features': FEATURES, 'weights': [float(x) for x in w_raw], 'train_top1': tr, 'test_top1': te,
           'train_sets': int(Gtr.max() + 1), 'test_sets': int(Gte.max() + 1)}, open(out_path, 'w'), indent=1)
