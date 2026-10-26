#!/usr/bin/env python3
"""Pooled paired delta over several same-map screens (policy v5 step 1a, adopted 2026-10-26).

usage: python tools/boss_proxy/pooled_delta.py <dir>:<cell>[:<control>] [<dir>:<cell>[:<control>] ...] [--control cur]

Each argument names a candidate cell "X-vs-OPP" in a screen directory that also holds the control cell
"<control>-vs-OPP" played on the same maps/seeds/seats (a per-pool control name may follow the cell after a colon,
for pools whose cells were named differently). Prints each pool's paired per-map delta and the pooled delta
over all maps of all pools (candidate minus control, in pp, with the 95 % interval), and the step-1a verdict: continue
only if the replication (every pool after the first) is positive and the pooled delta is >= +3 pp.
"""
import importlib.util, math, os, sys

spec = importlib.util.spec_from_file_location("summarize_screen", os.path.join(os.path.dirname(__file__), "summarize_screen.py"))
ss = importlib.util.module_from_spec(spec); spec.loader.exec_module(ss)


def deltas(directory, cell, control):
    reps = ss.load(directory)
    _, _, opp = cell.partition('-vs-')
    ctrl = f'{control}-vs-{opp}'
    if cell not in reps or ctrl not in reps:
        raise SystemExit(f"missing {cell} or {ctrl} in {directory}")
    a, b = ss.per_map(reps[cell]), ss.per_map(reps[ctrl])
    return [a[h] - b[h] for h in a if h in b]


def summary(ds):
    n = len(ds); mean = sum(ds) / n
    sd = math.sqrt(sum((x - mean) ** 2 for x in ds) / (n - 1)) if n > 1 else float('nan')
    return 100 * mean / 2, 100 * 1.96 * sd / math.sqrt(n) / 2, n


def main():
    args = sys.argv[1:]
    control = 'cur'
    if '--control' in args:
        i = args.index('--control'); control = args[i + 1]; args = args[:i] + args[i + 2:]
    pooled, per_pool = [], []
    for item in args:
        parts = item.split(':')
        # Windows drive letters ("C:/...") stay with the directory
        if len(parts) >= 2 and len(parts[0]) == 1 and parts[1][:1] in ('/', chr(92)):
            parts = [parts[0] + ':' + parts[1]] + parts[2:]
        directory, cell = parts[0], parts[1]
        ctrl = parts[2] if len(parts) > 2 else control
        ds = deltas(directory, cell, ctrl); pooled += ds
        m, ci, n = summary(ds); per_pool.append(m)
        print(f"  {directory}:{cell:20} vs {ctrl}: maps {n:3}  delta {m:+5.1f} pp +-{ci:4.1f}")
    m, ci, n = summary(pooled)
    print(f"  pooled over {len(args)} pools: maps {n:3}  delta {m:+5.1f} pp +-{ci:4.1f}")
    replication_positive = all(x > 0 for x in per_pool[1:])
    verdict = "continue" if replication_positive and m >= 3.0 else "stop"
    print(f"  step 1a: replication {'positive' if replication_positive else 'NOT positive'}, pooled {m:+.1f} pp -> {verdict}")


if __name__ == '__main__':
    main()
