import csv, json, os, collections, statistics as st, math
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"
D = os.path.join(root, "build/analysis/arena-v12b")
rep = json.load(open(os.path.join(root, "build/analysis/agent-6814073.json"), encoding="utf-8"))
outcome = {str(g["gameId"]): g["outcome"] for g in rep["games"]}
cells = list(csv.DictReader(open(os.path.join(D, "cells.csv"), encoding="utf-8")))
I = lambda r, k: int(r[k]); B = lambda r, k: r[k] in ("1", "true")
A, Z = 10, 25

def cls(r):
    if B(r, "onCompletionOwn"): return "completion(own)"
    if B(r, "onCompletionAny"): return "completion(shared)"
    if B(r, "onSteal"): return "steal"
    if B(r, "onBypass"): return "bypass"
    if B(r, "onExtend"): return "extend"
    return "none"

for oc in ("loss", "win"):
    games = {g for g in outcome if outcome[g] == oc}; ng = len(games)
    print(f"\n=== {oc}: {ng} games, completion events with first income in turns {A}-{Z} ===")
    for mine, who in ((True, "v12"), (False, "opp")):
        ev = collections.defaultdict(list)
        for r in cells:
            if r["game"] not in games or ((r["player"] == r["leader"]) != mine): continue
            fe = I(r, "firstEarnTurn")
            if A <= fe <= Z: ev[(r["game"], fe)].append(r)
        half_span, last_span, early_share, early_cls, early_cls_n, seeds = [], [], [], collections.Counter(), 0, 0
        ex_core = []
        for (g, fe), rs in ev.items():
            rs = sorted(rs, key=lambda r: I(r, "turn"))
            paint = sum(I(r, "cost") for r in rs); acc = 0; half_t = None
            for r in rs:
                acc += I(r, "cost")
                if half_t is None and acc * 2 >= paint: half_t = I(r, "turn")
            half_span.append(fe - half_t)
            early = [r for r in rs if fe - I(r, "turn") >= 4]
            early_share.append(len(early) / len(rs))
            for r in early: early_cls[cls(r)] += 1; early_cls_n += 1
            if len(rs) >= 2 and I(rs[1], "turn") - I(rs[0], "turn") >= 3: seeds += 1
            # core excess: span of the cells placed within the last ceil(paint/3)+2 turns only
            core = [r for r in rs if fe - I(r, "turn") <= math.ceil(paint / 3) + 2]
            if core:
                cp = sum(I(r, "cost") for r in core)
                ex_core.append((fe - min(I(r, "turn") for r in core)) - max(1, math.ceil(cp / 3)))
        n = len(ev)
        print(f"  {who}: events {n/ng:4.1f}/game; turns from half the paint to income {st.mean(half_span):4.2f} (median {st.median(half_span)}); "
              f"cells placed >= 4 turns before income {100*st.mean(early_share):4.1f} %; events with an isolated seed (first cell >= 3 turns before the second) {100*seeds/max(1,n):4.1f} %; "
              f"core excess (late cells only) {st.mean(ex_core):+4.2f}")
        if early_cls_n:
            print("        early cells (>= 4 turns before income), class at placement: " + ", ".join(f"{k} {100*v/early_cls_n:4.1f} %" for k, v in early_cls.most_common()))
