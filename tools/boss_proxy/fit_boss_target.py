"""Fit a conditional-logit model of the boss's DISRUPT target choice at commitment turns.

Inputs: CgLeaderStudy sabotage.csv (candidate regions per turn, both players) and
BossIntentStudy turns.csv (per boss turn: target, sameTarget, prevTargetInked).
Only rows of the boss (player == leader) are used. A commitment turn is a boss turn whose
target differs from the previous turn's target (or the first disrupt of the game).

Usage: python fit_boss_target.py <sabotage.csv> <turns.csv> <out.json>
"""
import csv, json, sys, math
import numpy as np

sab_path, turns_path, out_path = sys.argv[1:4]

turns = {}
for r in csv.DictReader(open(turns_path)):
    turns[(r['game'], int(r['turn']))] = r

FEATURES = ['dOpp', 'dOwn', 'oppCells', 'oppActive', 'ownCells', 'oppPlacedPrev2', 'instab',
            'oppRouteTraffic', 'routesAny', 'adjTownRegion', 'closes', 'neutralCells', 'logFree']

sets = {}  # (game,turn) -> list of (features, chosen)
for r in csv.DictReader(open(sab_path)):
    if r['player'] != r['leader']:
        continue
    key = (r['game'], int(r['turn']))
    t = turns.get(key)
    if t is None or int(t['target']) < 0:
        continue
    f = {k: float(r[k]) for k in r if k not in ('game', 'leader', 'player')}
    f['logFree'] = math.log1p(f['freeCells'])
    vec = [f[k] for k in FEATURES]
    sets.setdefault(key, []).append((vec, int(r['chosen']), int(r['region'])))

def build(keys):
    X, Y, G = [], [], []
    for gi, k in enumerate(keys):
        alts = sets[k]
        if sum(c for _, c, _ in alts) != 1:
            continue
        for vec, c, _ in alts:
            X.append(vec); Y.append(c); G.append(gi)
    return np.array(X), np.array(Y), np.array(G)

commit_keys = [k for k in sets if turns[k]['sameTarget'] == '0']
stay_keys = [k for k in sets if turns[k]['sameTarget'] == '1']
print(f"choice sets: commit {len(commit_keys)}, stay {len(stay_keys)}")

rng = np.random.default_rng(1)
keys = list(commit_keys)
rng.shuffle(keys)
split = int(len(keys) * 0.8)
train_keys, test_keys = keys[:split], keys[split:]

Xtr, Ytr, Gtr = build(train_keys)
Xte, Yte, Gte = build(test_keys)
mu, sd = Xtr.mean(0), Xtr.std(0) + 1e-9
Ztr, Zte = (Xtr - mu) / sd, (Xte - mu) / sd

def loglik_grad(w, Z, Y, G, l2=1e-3):
    u = Z @ w
    ll = 0.0; grad = np.zeros_like(w)
    ngroups = G.max() + 1
    # group-wise softmax
    umax = np.full(ngroups, -1e30)
    np.maximum.at(umax, G, u)
    e = np.exp(u - umax[G])
    Zsum = np.zeros(ngroups); np.add.at(Zsum, G, e)
    p = e / Zsum[G]
    ll = np.sum(np.log(p[Y == 1] + 1e-300)) - l2 * np.sum(w * w)
    grad = Z[Y == 1].sum(0) - (Z * p[:, None]).sum(0) - 2 * l2 * w
    return ll, grad, p

w = np.zeros(len(FEATURES))
lr = 0.05
for it in range(4000):
    ll, g, _ = loglik_grad(w, Ztr, Ytr, Gtr)
    w += lr * g / (Gtr.max() + 1)
    if it % 1000 == 0:
        print(f"iter {it} ll/set {ll/(Gtr.max()+1):.4f}")

def top1(w, Z, Y, G):
    u = Z @ w
    hits = 0; n = G.max() + 1
    best = np.full(n, -1e30); arg = np.full(n, -1)
    for i, (gi, ui) in enumerate(zip(G, u)):
        if ui > best[gi]:
            best[gi] = ui; arg[gi] = i
    for gi in range(n):
        if Y[arg[gi]] == 1: hits += 1
    return hits / n

print("train top-1 %.3f  test top-1 %.3f  (uniform %.3f)" % (
    top1(w, Ztr, Ytr, Gtr), top1(w, Zte, Yte, Gte), 1.0 / (len(Yte) / (Gte.max() + 1))))
# unstandardised weights
w_raw = w / sd
bias = -float(np.sum(w * mu / sd))
rel = w_raw / abs(w_raw[0]) if abs(w_raw[0]) > 0 else w_raw
for k, wi, wr in zip(FEATURES, w_raw, rel):
    print(f"  {k:16s} {wi:+.4f}   rel. to dOpp {wr:+.3f}")
json.dump({'features': FEATURES, 'weights': [float(x) for x in w_raw], 'mu': [float(x) for x in mu],
           'sd': [float(x) for x in sd], 'commit_sets': len(commit_keys), 'stay_sets': len(stay_keys),
           'train_top1': top1(w, Ztr, Ytr, Gtr), 'test_top1': top1(w, Zte, Yte, Gte)}, open(out_path, 'w'), indent=1)
print("saved", out_path)
