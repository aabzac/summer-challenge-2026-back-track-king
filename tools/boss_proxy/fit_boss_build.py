"""Calibrate the proxy's project choice against the boss's announced "build a>b" pair.

For every build turn, candidate pairs with a positive M6 route (own-only, unstable-avoiding;
shared fallback for inactive pairs when M6 is unreachable) are valued by
    gain * earning / (delay + 1) / cost ** a * (1 + stick * samePairAsPrev)
and the top-1 agreement with the boss's pair is reported over a grid of (a, stick, horizon).
Usage: python fit_boss_build.py <pairs.csv>
"""
import csv, sys, math, collections
rows = collections.defaultdict(list)
for r in csv.DictReader(open(sys.argv[1])):
    rows[(r['game'], int(r['turn']))].append({k: (int(v) if k not in ('game',) else v) for k, v in r.items()})
print("build turns", len(rows))
def value(c, cost, gain, same, a, stick, H, turnsLeft, paint=3):
    delay = (max(0, cost - paint) + 2) // 3
    earning = min(H, turnsLeft) - delay
    if earning <= 0 or gain <= 0 or cost <= 0: return None
    return gain * earning / (delay + 1) / (cost ** a) * (1 + stick * same)
best = None
for H in (16, 24, 40, 100):
    for a in (0.0, 0.5, 1.0, 1.5, 2.0):
        for stick in (0.0, 0.3, 0.6, 1.0, 2.0):
            hits = 0; n = 0; chosen_absent = 0
            for key, cands in rows.items():
                scored = []
                for c in cands:
                    cost, gain = c['costM6'], c['gainM6']
                    if (cost < 0 or gain <= 0) and not c['active'] and c['costAny'] > 0:
                        cost, gain = c['costAny'], c['gainAny']
                    v = value(c, cost, gain, c['samePairAsPrev'], a, stick, H, c['turnsLeft'])
                    if v is not None: scored.append((v, c['pair'], c['chosen']))
                if not scored: continue
                n += 1
                if not any(ch for _, _, ch in scored): chosen_absent += 1; continue
                scored.sort(reverse=True)
                hits += scored[0][2]
            acc = hits / n
            if best is None or acc > best[0]: best = (acc, H, a, stick, n, chosen_absent)
            if stick == 0.3: print(f"H={H:3d} a={a:.1f} stick={stick:.1f}: top-1 {acc:.3f} (n={n}, boss pair not valued positive in {chosen_absent})")
print("best", best)
