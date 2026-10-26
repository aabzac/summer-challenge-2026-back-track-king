"""boss-v4 (2026-10-26): what predicts the boss's attacks on the opponent's under-construction concentrations?

Conditional logit of the boss's DISRUPT target on CgLeaderStudy's full feature table (sabotage.csv; the boss is the row's
player, "opp" is the boss's opponent = our bot), fitted on the commitment turns (target switches) of the canonical corpus
with the 80 games of the corridor-routes-v1 live session held out by game (K1 arm = "candidate", v12 arm = "control").
Excluded columns: oppChosen (the opponent's simultaneous choice: a leak) and hitPrevByMe (zero for the chosen region on a
switch turn by construction: a selection artifact); regionSize duplicates cells.

Metrics on the holdout, per arm: top-1 on commitment turns, top-1 on every disrupt turn (argmax, no hold rule), and top-1
on the under-construction attacks (turns whose chosen region holds >= 3 of the opponent's tracks and none of them on an
active path). The shipped boss-v1 weights are scored on the same rows.

Usage: python fit_boss_target_v4.py <sabotage.csv> <turns.csv> <holdout.txt> <out.json> [iters]
"""
import csv, json, sys, os, time
import numpy as np

sab_path, turns_path, hold_path, out_path = sys.argv[1:5]
ITERS = int(sys.argv[5]) if len(sys.argv) > 5 else 3000
L2 = 1e-3
t0 = time.time()

COLS = ['instab', 'cells', 'oppCells', 'ownCells', 'neutralCells', 'oppActive', 'ownActive', 'dOpp', 'dOwn',
        'oppPlacedPrev', 'oppPlacedPrev2', 'ownPlacedPrev', 'routesAny', 'routeMinCostAny', 'routesOppOwn',
        'routeMinCostOppOwn', 'routesMeOwn', 'oppRouteTraffic', 'freeCells', 'closes', 'adjTownRegion', 'hitPrevByOpp']

holdout = {}
for line in open(hold_path, encoding='utf-8'):
    line = line.strip()
    if line:
        g, arm = line.split(','); holdout[g] = arm

cache = out_path + '.npz'
if os.path.exists(cache):
    z = np.load(cache, allow_pickle=True)
    X, chosen, gkey, tkey, same = z['X'], z['chosen'], z['gkey'], z['tkey'], z['same']
    print(f"loaded cache: {len(X)} rows")
else:
    turns = {}
    with open(turns_path, encoding='utf-8') as fh:
        rd = csv.reader(fh); hdr = next(rd)
        ig, it, itg, isame = hdr.index('game'), hdr.index('turn'), hdr.index('target'), hdr.index('sameTarget')
        for r in rd:
            if int(r[itg]) >= 0: turns[(r[ig], int(r[it]))] = r[isame] == '1'
    Xl, chl, gl, tl, sl = [], [], [], [], []
    with open(sab_path, encoding='utf-8') as fh:
        rd = csv.reader(fh); hdr = next(rd); col = {n: i for i, n in enumerate(hdr)}
        ci = [col[n] for n in COLS]
        ig, it, il, ip, ich = col['game'], col['turn'], col['leader'], col['player'], col['chosen']
        for r in rd:
            if r[ip] != r[il]: continue
            key = (r[ig], int(r[it])); s = turns.get(key)
            if s is None: continue
            Xl.append([float(r[i]) for i in ci]); chl.append(int(r[ich])); gl.append(r[ig]); tl.append(int(r[it])); sl.append(s)
    X = np.array(Xl); chosen = np.array(chl, dtype=np.int8); gkey = np.array(gl); tkey = np.array(tl); same = np.array(sl)
    np.savez_compressed(cache, X=X, chosen=chosen, gkey=gkey, tkey=tkey, same=same)
    print(f"parsed {len(X)} rows in {time.time()-t0:.0f} s")

c = {n: X[:, i] for i, n in enumerate(COLS)}
def inv_cost(v): return np.where(v > 0, 1.0 / np.maximum(v, 1), 0.0)
F = {
    # the 13 shipped features (boss-v1 order)
    'dOpp': c['dOpp'], 'dOwn': c['dOwn'], 'oppCells': c['oppCells'], 'oppActive': c['oppActive'], 'ownCells': c['ownCells'],
    'oppPlacedPrev2': c['oppPlacedPrev2'], 'instab': c['instab'], 'oppRouteTraffic': c['oppRouteTraffic'],
    'routesAny': c['routesAny'], 'adjTownRegion': c['adjTownRegion'], 'closes': c['closes'],
    'neutralCells': c['neutralCells'], 'logFree': np.log1p(c['freeCells']),
    # the unused columns
    'ownActive': c['ownActive'], 'oppPlacedPrev': c['oppPlacedPrev'], 'ownPlacedPrev': c['ownPlacedPrev'],
    'routesOppOwn': c['routesOppOwn'], 'invCostOppOwn': inv_cost(c['routeMinCostOppOwn']),
    'nearCompleteOpp': ((c['routeMinCostOppOwn'] >= 1) & (c['routeMinCostOppOwn'] <= 3)).astype(float),
    'routesMeOwn': c['routesMeOwn'], 'invCostAny': inv_cost(c['routeMinCostAny']), 'hitPrevByOpp': c['hitPrevByOpp'],
    'logCells': np.log1p(c['cells']),
    # derived shapes
    'nonEarning': c['oppCells'] - c['oppActive'], 'instab2': (c['instab'] == 2).astype(float), 'instab3': (c['instab'] == 3).astype(float),
}
F['nonEarnXnear'] = F['nonEarning'] * F['nearCompleteOpp']
F['nonEarnXroutes'] = F['nonEarning'] * np.minimum(c['routesOppOwn'], 5)
F['underConstruction'] = ((c['oppCells'] >= 3) & (c['oppActive'] == 0)).astype(float)
BASE = ['dOpp', 'dOwn', 'oppCells', 'oppActive', 'ownCells', 'oppPlacedPrev2', 'instab', 'oppRouteTraffic', 'routesAny',
        'adjTownRegion', 'closes', 'neutralCells', 'logFree']
FULL = BASE + ['ownActive', 'oppPlacedPrev', 'ownPlacedPrev', 'routesOppOwn', 'invCostOppOwn', 'nearCompleteOpp',
               'routesMeOwn', 'invCostAny', 'hitPrevByOpp', 'logCells', 'instab2', 'instab3']
INTER = FULL + ['nonEarning', 'nonEarnXnear', 'nonEarnXroutes', 'underConstruction']
VARIANTS = {'base': BASE, 'full': FULL, 'inter': INTER}
SHIPPED = [+0.137316, -0.111814, +0.775046, -0.003927, -0.282833, +0.291303, +0.884541,
           +0.371747, -0.001751, +0.368378, -3.437223, +0.155573, +0.148325]

# choice sets in row order
key = np.char.add(np.char.add(gkey.astype(str), ':'), tkey.astype(str))
change = np.r_[True, key[1:] != key[:-1]]
set_id = np.cumsum(change) - 1; n_sets = int(set_id[-1]) + 1
first = np.where(change)[0]
cnt = np.bincount(set_id, weights=chosen, minlength=n_sets); ok = cnt == 1
s_game, s_turn, s_same = gkey[first], tkey[first], same[first]
arm = np.array([holdout.get(g, '') for g in s_game])
# under-construction attack sets: the chosen row has >= 3 opponent tracks, none active
uc_row = (chosen == 1) & (c['oppCells'] >= 3) & (c['oppActive'] == 0)
uc_set = np.zeros(n_sets, dtype=bool); uc_set[set_id[uc_row]] = True
commit = ok & ~s_same & (s_turn > 0)
masks = {'train': commit & (arm == '')}
for a, lab in (('candidate', 'K1'), ('control', 'v12')):
    masks[f'{lab}_commit'] = commit & (arm == a)
    masks[f'{lab}_all'] = ok & (s_turn > 0) & (arm == a)
    masks[f'{lab}_uc'] = ok & (s_turn > 0) & (arm == a) & uc_set
print(f"sets {n_sets}; train {masks['train'].sum()}; holdout K1 commit {masks['K1_commit'].sum()} all {masks['K1_all'].sum()} uc {masks['K1_uc'].sum()}; "
      f"v12 commit {masks['v12_commit'].sum()} all {masks['v12_all'].sum()} uc {masks['v12_uc'].sum()}")

def mat(feats, set_mask):
    m = set_mask[set_id]
    return np.column_stack([F[n][m] for n in feats]), chosen[m], set_id[m]

def top1(w, feats, set_mask):
    Z, ch, sid = mat(feats, set_mask)
    if len(sid) == 0: return float('nan')
    u = Z @ w; uniq, inv = np.unique(sid, return_inverse=True)
    best = np.full(len(uniq), -1e300); np.maximum.at(best, inv, u)
    isb = u >= best[inv] - 1e-12
    multi = np.bincount(inv, weights=isb, minlength=len(uniq)) > 1
    hit = np.zeros(len(uniq), dtype=bool); np.logical_or.at(hit, inv[(ch == 1) & isb], True)
    return float(np.mean(hit & ~multi))

def fit(feats, set_mask):
    Z, ch, sid = mat(feats, set_mask)
    uniq, inv = np.unique(sid, return_inverse=True); k = len(uniq)
    mu, sd = Z.mean(0), Z.std(0) + 1e-9; Z = (Z - mu) / sd; ch = ch.astype(float)
    w = np.zeros(Z.shape[1])
    for _ in range(ITERS):
        u = Z @ w; umax = np.full(k, -1e30); np.maximum.at(umax, inv, u)
        e = np.exp(u - umax[inv]); zs = np.bincount(inv, weights=e, minlength=k); p = e / zs[inv]
        w += 0.05 * (Z[ch == 1].sum(0) - (Z * p[:, None]).sum(0) - 2 * L2 * w) / k
    return w / sd

def score(w, feats):
    return {m: top1(w, feats, masks[m]) for m in masks}

results = {}
results['shipped'] = {'features': BASE, 'weights': SHIPPED, **score(np.array(SHIPPED), BASE)}
def line(name, r):
    return (f"{name:8} train {r['train']:.3f} | K1 commit {r['K1_commit']:.3f} all {r['K1_all']:.3f} uc {r['K1_uc']:.3f} | "
            f"v12 commit {r['v12_commit']:.3f} all {r['v12_all']:.3f} uc {r['v12_uc']:.3f}")
print(line('shipped', results['shipped']))
for name, feats in VARIANTS.items():
    t1 = time.time(); w = fit(feats, masks['train'])
    results[name] = {'features': feats, 'weights': [float(x) for x in w], **score(w, feats)}
    print(line(name, results[name]) + f"  ({time.time()-t1:.0f} s)")
    for n, wi in zip(feats, w): print(f"      {n:18s} {wi:+.4f}")
results['_meta'] = {'iters': ITERS, 'l2': L2, 'sets': n_sets, **{k: int(v.sum()) for k, v in masks.items()}, 'seconds': time.time() - t0}
json.dump(results, open(out_path, 'w', encoding='utf-8'), indent=1)
print("saved", out_path, f"({time.time()-t0:.0f} s)")
