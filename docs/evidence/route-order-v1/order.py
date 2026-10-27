import csv, json, os, collections, statistics as st, math, sys
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"
D = os.path.join(root, "build/analysis/arena-v12b")
rep = json.load(open(os.path.join(root, "build/analysis/agent-6814073.json"), encoding="utf-8"))
outcome = {str(g["gameId"]): g["outcome"] for g in rep["games"]}
opp_name = {str(g["gameId"]): g["names"][1 - g["me"]] for g in rep["games"]}
cells = list(csv.DictReader(open(os.path.join(D, "cells.csv"), encoding="utf-8")))
I = lambda r, k: int(r[k])
A, Z = 10, 25   # completion events (first income) in turns [A, Z]

def analyse(label, games):
    ng = len(games)
    if not ng: return
    print(f"\n=== {label}: {ng} games ===")
    for mine, who in ((True, "v12"), (False, "opp")):
        rows = [r for r in cells if r["game"] in games and ((r["player"] == r["leader"]) == mine)]
        # completion events: cells grouped by (game, first-earn turn); the event's cells and their placement turns
        ev = collections.defaultdict(list)
        for r in rows:
            fe = I(r, "firstEarnTurn")
            if fe >= 0: ev[(r["game"], fe)].append(r)
        spans, excess, sizes, paints, parallel_at_start = [], [], [], [], []
        for (g, fe), rs in ev.items():
            if not (A <= fe <= Z): continue
            first = min(I(r, "turn") for r in rs)
            paint = sum(I(r, "cost") for r in rs)
            minturns = max(1, math.ceil(paint / 3))
            span = fe - first          # turns from the first cell to the first income (cells placed on turn fe-1 earn at fe)
            spans.append(span); excess.append(span - minturns); sizes.append(len(rs)); paints.append(paint)
        # interleaving: per placement turn in [A-3, Z], how many distinct future completion events the turn's earning cells feed
        per_turn = collections.defaultdict(set); per_turn_n = collections.Counter(); per_turn_idle = collections.Counter()
        for r in rows:
            t = I(r, "turn")
            if not (A - 3 <= t <= Z): continue
            per_turn_n[(r["game"], t)] += 1
            fe = I(r, "firstEarnTurn")
            if fe >= 0: per_turn[(r["game"], t)].add(fe)
            else: per_turn_idle[(r["game"], t)] += 1
        turns_keys = list(per_turn_n)
        multi = sum(1 for k in turns_keys if len(per_turn[k]) > 1)
        dist_events = [len(per_turn[k]) for k in turns_keys]
        idle_share = sum(per_turn_idle[k] for k in turns_keys) / max(1, sum(per_turn_n[k] for k in turns_keys))
        if not spans: continue
        print(f"  {who}: completion events (first income in turns {A}-{Z}) {len(spans)/ng:4.1f}/game; cells per event {st.mean(sizes):4.1f}, paint per event {st.mean(paints):4.1f}; "
              f"turns from first cell to income {st.mean(spans):4.2f} (median {st.median(spans)}), minimum possible {st.mean([max(1, math.ceil(p/3)) for p in paints]):4.2f}, "
              f"**excess {st.mean(excess):+4.2f}** (events with excess >= 2: {100*sum(1 for e in excess if e >= 2)/len(excess):4.1f} %)")
        print(f"        placement turns {A-3}-{Z}: turns whose earning cells feed >1 future completion {100*multi/max(1,len(turns_keys)):4.1f} %, "
              f"mean distinct completions fed per turn {st.mean(dist_events):4.2f}; cells placed in these turns that never earn {100*idle_share:4.1f} %")

allg = set(outcome)
analyse("losses", {g for g in allg if outcome[g] == "loss"})
analyse("wins", {g for g in allg if outcome[g] == "win"})
analyse("vs Ando_Riccardo, lost", {g for g in allg if opp_name[g] == "Ando_Riccardo" and outcome[g] == "loss"})
analyse("vs Ando_Riccardo, won", {g for g in allg if opp_name[g] == "Ando_Riccardo" and outcome[g] == "win"})
