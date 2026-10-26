import csv, json, os, collections, statistics as st
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"
D = os.path.join(root, "build/analysis/arena-v12b")
rep = json.load(open(os.path.join(root, "build/analysis/agent-6814073.json"), encoding="utf-8"))
outcome = {str(g["gameId"]): g["outcome"] for g in rep["games"]}
opp_name = {str(g["gameId"]): g["names"][1 - g["me"]] for g in rep["games"]}
cells = list(csv.DictReader(open(os.path.join(D, "cells.csv"), encoding="utf-8")))
I = lambda r, k: int(r[k]); B = lambda r, k: r[k] in ("1", "true")
A, Z = 10, 20  # placement window [A, Z)

def cls(r):
    if B(r, "onCompletionOwn"): return "completion(own)"
    if B(r, "onCompletionAny"): return "completion(shared)"
    if B(r, "onSteal"): return "steal"
    if B(r, "onBypass"): return "bypass"
    if B(r, "onExtend"): return "extend"
    return "none"

def side_rows(games, mine):
    return [r for r in cells if r["game"] in games and ((r["player"] == r["leader"]) == mine) and A <= I(r, "turn") < Z]

def report(label, games):
    ng = len(games)
    if ng == 0: return
    print(f"\n=== {label}: {ng} games, cells placed in turns {A}-{Z-1} ===")
    for mine, who in ((True, "v12"), (False, "opp")):
        rows = side_rows(games, mine); n = len(rows)
        earn = [r for r in rows if I(r, "firstEarnTurn") >= 0]
        fast = [r for r in earn if I(r, "delay") <= 1]
        inked_before = [r for r in rows if I(r, "firstEarnTurn") < 0 and I(r, "lostTurn") >= 0 and not B(r, "neutralised")]
        pts = sum(I(r, "totalPoints") for r in earn)
        span = sum(max(1, (I(r, "lostTurn") if I(r, "lostTurn") >= 0 else I(r, "gameTurns")) - I(r, "firstEarnTurn")) for r in earn)
        print(f"  {who}: cells/game {n/ng:5.1f} | earn {100*len(earn)/max(1,n):4.1f} % | earn within 1 turn {100*len(fast)/max(1,n):4.1f} % | delay {st.mean(I(r,'delay') for r in earn) if earn else float('nan'):4.2f} | "
              f"pairs at first earn {st.mean(I(r,'pairsAtFirstEarn') for r in earn) if earn else float('nan'):4.2f} (of which inactive before {st.mean(I(r,'pairsInactiveAtT') for r in earn) if earn else float('nan'):4.2f}, stolen {st.mean(I(r,'pairsStolen') for r in earn) if earn else float('nan'):4.2f}, bypass {st.mean(I(r,'pairsBypassed') for r in earn) if earn else float('nan'):4.2f}, other {st.mean(I(r,'pairsOther') for r in earn) if earn else float('nan'):4.2f}) | "
              f"max pairs {st.mean(I(r,'maxPairs') for r in earn) if earn else float('nan'):4.2f} | conn/earning cell-turn {pts/max(1,span):4.2f} | points/cell placed {sum(I(r,'totalPoints') for r in rows)/max(1,n):5.1f} | inked before earning {100*len(inked_before)/max(1,n):4.1f} %")
        c = collections.Counter(cls(r) for r in rows)
        adj = sum(1 for r in rows if B(r, "adjOwn")); adjt = sum(1 for r in rows if B(r, "adjTown"))
        crl = [I(r, "completionRouteLen") for r in rows if I(r, "completionRouteLen") > 0]
        print(f"        class at placement: " + ", ".join(f"{k} {100*v/n:4.1f} %" for k, v in sorted(c.items(), key=lambda kv: -kv[1])) +
              f" | adjacent to own track {100*adj/n:4.1f} %, to a town {100*adjt/n:4.1f} % | completion route length {st.mean(crl) if crl else float('nan'):4.1f} | "
              f"instab>0 at placement {100*sum(1 for r in rows if I(r,'instab')>0)/n:4.1f} % | opp tracks in region {st.mean(I(r,'oppInRegion') for r in rows):4.2f}, own {st.mean(I(r,'ownInRegion') for r in rows):4.2f}")
        # completion events in the window: per (game, firstEarnTurn) the pairs newly activated
        ev = collections.defaultdict(int)
        for r in cells:
            if r["game"] not in games or ((r["player"] == r["leader"]) != mine): continue
            fe = I(r, "firstEarnTurn")
            if A <= fe < Z: ev[(r["game"], fe)] = max(ev[(r["game"], fe)], I(r, "pairsInactiveAtT"))
        events = [v for v in ev.values() if v > 0]
        print(f"        completion events with first income in turns {A}-{Z-1}: {len(events)/ng:4.2f} per game, pairs switched on per event {st.mean(events) if events else float('nan'):4.2f}, "
              f"pairs switched on per game {sum(events)/ng:4.2f}")

games_all = set(outcome)
report("losses", {g for g in games_all if outcome[g] == "loss"})
report("wins", {g for g in games_all if outcome[g] == "win"})
report("vs Ando_Riccardo, lost", {g for g in games_all if opp_name[g] == "Ando_Riccardo" and outcome[g] == "loss"})
report("vs Ando_Riccardo, won", {g for g in games_all if opp_name[g] == "Ando_Riccardo" and outcome[g] == "win"})
