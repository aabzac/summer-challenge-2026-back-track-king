#!/usr/bin/env python3
"""Summarize BenchmarkRunner JSON reports of one same-map screen.

usage: python tools/boss_proxy/summarize_screen.py <dir> [--control D] [--tall 19]

Prints one row per report (A win rate, W-L-D, map-grouped 95 % CI, errors, time/move, tall-map
split) and, for every candidate row "X-vs-OPP" that has a control row "<control>-vs-OPP" in the
same directory, the paired per-map delta (candidate minus control on identical maps/seeds/seats).
"""
import json, math, os, sys


def load(d):
    out = {}
    for f in sorted(os.listdir(d)):
        if f.endswith('.json'):
            with open(os.path.join(d, f), encoding='utf-8') as h:
                out[f[:-5]] = json.load(h)
    return out


def rate(r):
    g = r['games']
    return (r['winsA'] + 0.5 * r['draws']) / g if g else float('nan')


def per_map(r):
    """mapHash -> candidate points on that map over both seats (0..2)."""
    m = {}
    for p in r['pairs']:
        pts = 0.0
        for seat, g in (('aAsP0', 0), ('aAsP1', 1)):
            w = p[seat]['winner']
            pts += 1.0 if w == g else (0.5 if w is None or w < 0 else 0.0)
        m[p['mapHash']] = pts
    return m


def tall_split(r, tall):
    gt = wt = gs = ws = 0
    for h, b in r['byHeight'].items():
        if int(h) >= tall:
            gt += b['games']; wt += b['winsA'] + 0.5 * b['draws']
        else:
            gs += b['games']; ws += b['winsA'] + 0.5 * b['draws']
    return (ws / gs if gs else float('nan'), gs, wt / gt if gt else float('nan'), gt)


def main():
    args = sys.argv[1:]
    d = args[0]
    control = args[args.index('--control') + 1] if '--control' in args else 'D'
    tall = int(args[args.index('--tall') + 1]) if '--tall' in args else 19
    reps = load(d)
    lo, hi = f'<{tall}', f'>={tall}'
    print(f"{'cell':22} {'rate':>6} {'W-L-D':>11} {'95% CI':>13} {'err':>5} {'msA':>5} {'msB':>5} "
          f"{'maxA':>5} {'slowA':>5} {lo:>6} {hi:>6}")
    for name, r in reps.items():
        ci = r.get('confidence95') or [float('nan')] * 2
        rs, gs, rt, gt = tall_split(r, tall)
        turns = max(1, r['turns'])
        print(f"{name:22} {100*rate(r):5.1f}% {r['winsA']:>4}-{r['winsB']:<4}{r['draws']:<3}"
              f"{100*ci[0]:5.1f}-{100*ci[1]:<5.1f} {r['errorsA']+r['errorsB']:>5} "
              f"{r['sumMsA']/turns:5.1f} {r['sumMsB']/turns:5.1f} {r['maxMsA']:>5} {r['slowA']:>5} "
              f"{100*rs:5.1f}% {100*rt:5.1f}%")
    print()
    print('paired per-map deltas (candidate - control, same maps/seeds/seats):')
    for name, r in reps.items():
        cand, _, opp = name.partition('-vs-')
        ctrl = f'{control}-vs-{opp}'
        if cand == control or ctrl not in reps:
            continue
        a, b = per_map(r), per_map(reps[ctrl])
        common = [h for h in a if h in b]
        if not common:
            continue
        deltas = [a[h] - b[h] for h in common]
        n = len(deltas)
        mean = sum(deltas) / n
        sd = math.sqrt(sum((x - mean) ** 2 for x in deltas) / (n - 1)) if n > 1 else float('nan')
        se = sd / math.sqrt(n)
        better = sum(1 for x in deltas if x > 0); worse = sum(1 for x in deltas if x < 0)
        print(f"  {name:22} vs {ctrl:18} maps {n:3}  delta {100*mean/2:+5.1f} pp  "
              f"+-{100*1.96*se/2:4.1f} (95%)  maps better/worse/equal {better}/{worse}/{n-better-worse}")


if __name__ == '__main__':
    main()
