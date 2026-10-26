"""Route-choice imitation (2026-10-27, construction line): conditional logit over the candidate routes exported by
btk.tools.RouteChoiceExport, fitted on a target population's labeled placement turns with a game-level holdout.

Reports, on the holdout: top-1 / top-3 of the fitted model, of our bot's proposal value (ourValue = gain difference x
earning turns / turns to complete, the core of RolloutBot.propose without the exposure discount), of the cheapest route,
and of a uniform pick; the fitted weights; and the mean features of the chosen routes per population.

Usage: python fit_route_choice.py <train csv>[,<csv>...] [--test <csv>[,...]] [--minscore 27 --scores <agent json>,...] [--turns 10,19]
"""
import csv, json, math, os, sys, collections
import numpy as np

FEATS = ['cost', 'turns', 'dIncMe', 'dIncOpp', 'gainDiff', 'ourValue', 'pairsActivated', 'pathLen', 'ownOnPath',
         'oppOnPath', 'ownShare', 'instabFree', 'maxInstabFree', 'unstableRegionsPath', 'adjOwn', 'touchOwn',
         'completesNow', 'active', 'variant']


def arg(name, default=None):
    a = sys.argv
    return a[a.index(name) + 1] if name in a else default


def load(paths, keep_game=None, turns=None):
    sets = collections.defaultdict(list)
    for p in paths.split(','):
        for r in csv.DictReader(open(p, encoding='utf-8')):
            if keep_game is not None and not keep_game(r): continue
            t = int(r['turn'])
            if turns and not (turns[0] <= t <= turns[1]): continue
            sets[(p, r['game'], r['set'])].append(r)
    out = []
    for k, rows in sets.items():
        if sum(int(r['chosen']) == 1 for r in rows) != 1: continue
        X = np.array([[float(r[f]) for f in FEATS] for r in rows]); y = np.array([int(r['chosen']) == 1 for r in rows])
        out.append((k[1], X, y, rows))
    return out


def transform(X):
    Z = X.copy()
    Z[:, FEATS.index('ownShare')] /= 1000.0
    for f in ('cost', 'pathLen', 'ownOnPath', 'oppOnPath'):
        i = FEATS.index(f); Z[:, i] = np.log1p(np.maximum(Z[:, i], 0))
    i = FEATS.index('ourValue'); Z[:, i] = np.sign(Z[:, i]) * np.log1p(np.abs(Z[:, i]))
    return Z


def fit(sets, l2=1e-3, iters=400):
    # Newton-Raphson on the conditional-logit log-likelihood (concave), vectorised over all candidates
    Xs = [transform(X) for _, X, _, _ in sets]
    allX = np.vstack(Xs); mu, sd = allX.mean(0), allX.std(0) + 1e-9
    Z = (allX - mu) / sd
    gid = np.concatenate([np.full(len(X), i) for i, X in enumerate(Xs)])
    y = np.concatenate([yy for _, _, yy, _ in sets]).astype(float)
    k = len(Xs); d = Z.shape[1]; w = np.zeros(d)
    prev = -np.inf
    for it in range(iters):
        u = Z @ w; umax = np.full(k, -np.inf); np.maximum.at(umax, gid, u)
        e = np.exp(u - umax[gid]); den = np.bincount(gid, weights=e, minlength=k); p = e / den[gid]
        ll = float(np.sum(np.log(p[y == 1] + 1e-300))) - l2 * k * float(w @ w)
        g = Z[y == 1].sum(0) - (Z * p[:, None]).sum(0) - 2 * l2 * k * w
        # Hessian: -sum_sets (E[zz'] - E[z]E[z]')
        Ez = np.zeros((k, d)); np.add.at(Ez, gid, Z * p[:, None])
        H = -(Z * p[:, None]).T @ Z + Ez.T @ Ez - 2 * l2 * k * np.eye(d)
        step = np.linalg.solve(H, -g)
        w = w + step
        if abs(ll - prev) < 1e-6 * max(1.0, abs(ll)): break
        prev = ll
    return w, mu, sd


def topk(score_fn, sets, k):
    hits = 0
    for _, X, y, rows in sets:
        s = score_fn(X, rows)
        order = np.argsort(-s, kind='stable')
        hits += int(y[order[:k]].any())
    return hits / max(1, len(sets))


def main():
    train_paths = sys.argv[1]
    turns = tuple(int(x) for x in arg('--turns', '10,19').split(','))
    minscore = float(arg('--minscore', '-1'))
    scores = {}
    if arg('--scores'):
        for js in arg('--scores').split(','):
            rep = json.load(open(js, encoding='utf-8'))
            for g in rep['games']:
                raw = json.load(open(g['file'].replace('\\', '/'), encoding='utf-8'))
                oag = [a for a in raw['agents'] if a['index'] == 1 - g['me']][0]
                scores[str(g['gameId'])] = oag.get('score') or 0
    keep = (lambda r: scores.get(r['game'], 0) >= minscore) if minscore >= 0 and scores else None
    sets = load(train_paths, keep, turns)
    games = sorted({g for g, _, _, _ in sets})
    rng = np.random.default_rng(1); rng.shuffle(games)
    hold = set(games[: max(1, len(games) // 5)])
    train = [s for s in sets if s[0] not in hold]; test = [s for s in sets if s[0] in hold]
    if arg('--test'):
        test = load(arg('--test'), None, turns)
    print(f"sets: train {len(train)} ({len(games) - len(hold)} games), test {len(test)}; candidates per set {np.mean([len(s[1]) for s in sets]):.1f}")
    w, mu, sd = fit(train)
    model = lambda X, rows: ((transform(X) - mu) / sd) @ w
    ourv = lambda X, rows: X[:, FEATS.index('ourValue')]
    cheap = lambda X, rows: -X[:, FEATS.index('cost')]
    uni = lambda X, rows: np.zeros(len(X))
    for name, fn in (('fitted model', model), ('our proposal value', ourv), ('cheapest route', cheap), ('uniform (first)', uni)):
        print(f"  {name:20} top-1 {topk(fn, test, 1):.3f}  top-3 {topk(fn, test, 3):.3f}   (train top-1 {topk(fn, train, 1):.3f})")
    print("  standardised weights (transformed features):")
    for f, wi in sorted(zip(FEATS, w), key=lambda t: -abs(t[1])):
        print(f"    {f:20} {wi:+.3f}")
    ch = np.array([X[y][0] for _, X, y, _ in sets])
    print("  chosen-route means: " + ", ".join(f"{f} {ch[:, FEATS.index(f)].mean():.2f}" for f in
          ('cost', 'completesNow', 'gainDiff', 'dIncMe', 'dIncOpp', 'ourValue', 'pairsActivated', 'ownShare', 'oppOnPath', 'unstableRegionsPath', 'adjOwn', 'active', 'variant')))
    json.dump({'features': FEATS, 'weights': [float(x) for x in w], 'mu': mu.tolist(), 'sd': sd.tolist(), 'turns': turns,
               'train_sets': len(train), 'test_sets': len(test)}, open(arg('--out', 'build/analysis/route-choice/fit.json'), 'w'), indent=1)


if __name__ == '__main__':
    main()
