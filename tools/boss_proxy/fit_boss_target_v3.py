"""boss-v3 (2026-10-26): refit the boss's DISRUPT-target conditional logit on the whole canonical corpus with a
game-level holdout (the 80 games of the corridor-routes-v1 live session, K1 and v12 arms), and score the current
shipped weights on the same rows, so that old and new weights are compared apples to apples.

Inputs: CgLeaderStudy sabotage.csv (candidate regions per turn, both players), BossIntentStudy turns.csv (per boss
turn: target, sameTarget), a holdout file "gameId,arm" per line, out.json.
Commitment turns (target differs from the previous turn's) are the fit set, as in fit_boss_target.py; the hold rule
is fitted separately. Variants: the 13 shipped features ("base"), plus "ink" (indicators instab == 2 and instab == 3,
the time-to-ink shape) and "mix" (ink + oppCells x instab + the opponent's non-earning tracks).
The parsed rows are cached next to the output as <out>.npz.

Usage: python fit_boss_target_v3.py <sabotage.csv> <turns.csv> <holdout.txt> <out.json> [l2] [iters]
"""
import csv, json, sys, math, os, time
import numpy as np

sab_path, turns_path, hold_path, out_path = sys.argv[1:5]
L2 = float(sys.argv[5]) if len(sys.argv) > 5 else 1e-3
ITERS = int(sys.argv[6]) if len(sys.argv) > 6 else 3000

SHIPPED = [+0.137316, -0.111814, +0.775046, -0.003927, -0.282833, +0.291303, +0.884541,
           +0.371747, -0.001751, +0.368378, -3.437223, +0.155573, +0.148325]
BASE = ['dOpp', 'dOwn', 'oppCells', 'oppActive', 'ownCells', 'oppPlacedPrev2', 'instab',
        'oppRouteTraffic', 'routesAny', 'adjTownRegion', 'closes', 'neutralCells', 'logFree']
EXTRA = ['instab2', 'instab3', 'oppCellsXinstab', 'nonEarning']
ALL = BASE + EXTRA
VARIANTS = {'base': BASE, 'ink': BASE + ['instab2', 'instab3'], 'mix': ALL}

holdout = {}
for line in open(hold_path, encoding='utf-8'):
    line = line.strip()
    if line:
        g, arm = line.split(','); holdout[g] = arm

t0 = time.time()
cache = out_path + '.npz'
if os.path.exists(cache):
    z = np.load(cache, allow_pickle=True)
    X, chosen, gkey, tkey, same = z['X'], z['chosen'], z['gkey'], z['tkey'], z['same']
    print(f"loaded cache {cache}: {len(X)} rows")
else:
    turns = {}
    with open(turns_path, encoding='utf-8') as fh:
        rd = csv.reader(fh); hdr = next(rd); ig, it, itg, isame = hdr.index('game'), hdr.index('turn'), hdr.index('target'), hdr.index('sameTarget')
        for r in rd:
            if int(r[itg]) >= 0: turns[(r[ig], int(r[it]))] = r[isame] == '1'
    Xl, chl, gl, tl, sl = [], [], [], [], []
    with open(sab_path, encoding='utf-8') as fh:
        rd = csv.reader(fh); hdr = next(rd); col = {n: i for i, n in enumerate(hdr)}
        ci = [col[n] for n in ('dOpp', 'dOwn', 'oppCells', 'oppActive', 'ownCells', 'oppPlacedPrev2', 'instab',
                               'oppRouteTraffic', 'routesAny', 'adjTownRegion', 'closes', 'neutralCells', 'freeCells')]
        ig, it, il, ip, ich = col['game'], col['turn'], col['leader'], col['player'], col['chosen']
        for r in rd:
            if r[ip] != r[il]: continue
            key = (r[ig], int(r[it])); s = turns.get(key)
            if s is None: continue
            v = [float(r[i]) for i in ci]
            Xl.append(v); chl.append(int(r[ich])); gl.append(r[ig]); tl.append(int(r[it])); sl.append(s)
    X = np.array(Xl, dtype=np.float64); chosen = np.array(chl, dtype=np.int8); gkey = np.array(gl); tkey = np.array(tl); same = np.array(sl)
    np.savez_compressed(cache, X=X, chosen=chosen, gkey=gkey, tkey=tkey, same=same)
    print(f"parsed {len(X)} boss candidate rows in {time.time()-t0:.0f} s, cached")

# derived features: logFree replaces freeCells (index 12); extras appended
F = np.empty((len(X), len(ALL)))
F[:, :12] = X[:, :12]; F[:, 12] = np.log1p(X[:, 12])
F[:, 13] = (X[:, 6] == 2); F[:, 14] = (X[:, 6] == 3); F[:, 15] = X[:, 2] * X[:, 6]; F[:, 16] = X[:, 2] - X[:, 3]
# choice sets = (game, turn); ids in row order
set_id = np.zeros(len(X), dtype=np.int64); n_sets = 0; last = None
for i in range(len(X)):
    k = (gkey[i], tkey[i])
    if k != last: last = k; n_sets += 1
    set_id[i] = n_sets - 1
# keep only sets with exactly one chosen row
cnt = np.bincount(set_id, weights=chosen, minlength=n_sets); ok_set = cnt == 1
first = np.zeros(n_sets, dtype=np.int64); np.minimum.at(first, set_id, np.arange(len(X))) if False else None
first_idx = np.unique(set_id, return_index=True)[1]
set_game = gkey[first_idx]; set_turn = tkey[first_idx]; set_same = same[first_idx]
arm_of = np.array([holdout.get(g, '') for g in set_game])
commit = ok_set & (~set_same) & (set_turn > 0)
train_sets = commit & (arm_of == '')
hold_commit = {a: commit & (arm_of == a) for a in ('candidate', 'control')}
hold_all = {a: ok_set & (set_turn > 0) & (arm_of == a) for a in ('candidate', 'control')}
print(f"choice sets {n_sets} (usable {ok_set.sum()}); commitment {commit.sum()}: train {train_sets.sum()}, holdout K1 {hold_commit['candidate'].sum()}, v12 {hold_commit['control'].sum()}; "
      f"all disrupt turns in the holdout: K1 {hold_all['candidate'].sum()}, v12 {hold_all['control'].sum()}")

def rows_of(set_mask):
    return set_mask[set_id]

def top1(w, feats_idx, set_mask):
    m = rows_of(set_mask)
    if m.sum() == 0: return float('nan')
    u = F[m][:, feats_idx] @ w; sid = set_id[m]; ch = chosen[m]
    # remap set ids to 0..k-1
    uniq, inv = np.unique(sid, return_inverse=True)
    best = np.full(len(uniq), -1e300); np.maximum.at(best, inv, u)
    is_best = u >= best[inv] - 1e-12
    # a set counts as a hit if the chosen row is (one of) the argmax; ties broken against the model
    hit = np.zeros(len(uniq), dtype=bool); multi = np.bincount(inv, weights=is_best, minlength=len(uniq)) > 1
    np.logical_or.at(hit, inv[(ch == 1) & is_best], True)
    return float(np.mean(hit & ~multi))

def fit(feats_idx, set_mask, l2, iters):
    m = rows_of(set_mask)
    Z = F[m][:, feats_idx]; ch = chosen[m].astype(np.float64); sid = set_id[m]
    uniq, inv = np.unique(sid, return_inverse=True); k = len(uniq)
    mu, sd = Z.mean(0), Z.std(0) + 1e-9; Z = (Z - mu) / sd
    w = np.zeros(Z.shape[1]); lr = 0.05
    for it in range(iters):
        u = Z @ w
        umax = np.full(k, -1e30); np.maximum.at(umax, inv, u)
        e = np.exp(u - umax[inv]); zs = np.bincount(inv, weights=e, minlength=k)
        p = e / zs[inv]
        grad = Z[ch == 1].sum(0) - (Z * p[:, None]).sum(0) - 2 * l2 * w
        w += lr * grad / k
    return w / sd

results = {}
rng = np.random.default_rng(1)
train_ids = np.where(train_sets)[0]
for name, feats in VARIANTS.items():
    fi = [ALL.index(n) for n in feats]
    perm = rng.permutation(train_ids); split = int(len(perm) * 0.8)
    inner_train = np.zeros(n_sets, dtype=bool); inner_train[perm[:split]] = True
    inner_test = np.zeros(n_sets, dtype=bool); inner_test[perm[split:]] = True
    t1 = time.time()
    w_in = fit(fi, inner_train, L2, ITERS)
    inner = top1(w_in, fi, inner_test)
    w = fit(fi, train_sets, L2, ITERS)
    res = {'features': feats, 'weights': [float(x) for x in w], 'inner_test_top1': inner, 'train_top1': top1(w, fi, train_sets)}
    for arm, label in (('candidate', 'K1'), ('control', 'v12')):
        res[f'holdout_{label}_commit_top1'] = top1(w, fi, hold_commit[arm]); res[f'holdout_{label}_all_top1'] = top1(w, fi, hold_all[arm])
    results[name] = res
    print(f"{name:4} ({time.time()-t1:.0f} s): inner test top-1 {inner:.3f}, train {res['train_top1']:.3f}; holdout commitment top-1 K1 {res['holdout_K1_commit_top1']:.3f} v12 {res['holdout_v12_commit_top1']:.3f}; "
          f"all disrupt turns K1 {res['holdout_K1_all_top1']:.3f} v12 {res['holdout_v12_all_top1']:.3f}")
    for n, wi in zip(feats, w): print(f"      {n:16s} {wi:+.4f}")

fi = [ALL.index(n) for n in BASE]; W = np.array(SHIPPED)
ship = {'features': BASE, 'weights': SHIPPED, 'train_top1': top1(W, fi, train_sets)}
for arm, label in (('candidate', 'K1'), ('control', 'v12')):
    ship[f'holdout_{label}_commit_top1'] = top1(W, fi, hold_commit[arm]); ship[f'holdout_{label}_all_top1'] = top1(W, fi, hold_all[arm])
print(f"shipped W: train-set top-1 {ship['train_top1']:.3f}; holdout commitment top-1 K1 {ship['holdout_K1_commit_top1']:.3f} v12 {ship['holdout_v12_commit_top1']:.3f}; "
      f"all disrupt turns K1 {ship['holdout_K1_all_top1']:.3f} v12 {ship['holdout_v12_all_top1']:.3f}")
results['shipped'] = ship
results['_meta'] = {'l2': L2, 'iters': ITERS, 'commit_sets': int(commit.sum()), 'train_sets': int(train_sets.sum()), 'holdout_games': len(holdout), 'seconds': time.time() - t0}
json.dump(results, open(out_path, 'w', encoding='utf-8'), indent=1)
print("saved", out_path, f"({time.time()-t0:.0f} s)")
