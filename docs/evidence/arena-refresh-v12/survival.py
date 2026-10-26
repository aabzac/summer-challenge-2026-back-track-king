import csv, json, os, collections, statistics as st
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"
D = os.path.join(root, "build/analysis/arena-v12b")
rep = json.load(open(os.path.join(root, "build/analysis/agent-6814073.json"), encoding="utf-8"))
outcome = {str(g["gameId"]): g["outcome"] for g in rep["games"]}
opp = {str(g["gameId"]): g["names"][1 - g["me"]] for g in rep["games"]}
rows = list(csv.DictReader(open(os.path.join(D, "connections.csv"), encoding="utf-8")))
I = lambda r, k: int(r[k])
CAUSES = ["inkOpp", "inkOwn", "inkBoth", "takeover", "reroute", "end"]

def table(label, games, a=10, z=20):
    ng = len(games)
    if not ng: return
    print(f"\n=== {label}: {ng} games, earning episodes that start in turns {a}-{z-1} ===")
    for ours, who in ((1, "v12"), (0, "opp")):
        ep = [r for r in rows if r["game"] in games and I(r, "ours") == ours and a <= I(r, "start") < z]
        if not ep: continue
        n = len(ep)
        dur = st.mean(I(r, "turns") for r in ep)
        cellturns = sum(I(r, "turns") * I(r, "maxCells") for r in ep) / ng
        c = collections.Counter(r["cause"] for r in ep)
        lost = collections.Counter()
        for r in ep: lost[r["cause"]] += I(r, "maxCells")
        tot_lost = sum(v for k, v in lost.items() if k != "end")
        print(f"  {who}: episodes/game {n/ng:5.1f}, mean length {dur:4.1f} turns (median {st.median(I(r,'turns') for r in ep)}), cells at start {st.mean(I(r,'cellsAtStart') for r in ep):4.1f}, "
              f"path length {st.mean(I(r,'pathLenAtStart') for r in ep):4.1f}, paid cell-turns/game {cellturns:6.0f}")
        print("        end cause (share of episodes): " + ", ".join(f"{k} {100*c[k]/n:4.1f} %" for k in CAUSES))
        print("        income removed per game by cause (tracks on the path at the end): " + ", ".join(f"{k} {lost[k]/ng:5.1f}" for k in CAUSES if k != "end") + f"  (total {tot_lost/ng:5.1f})")
        own_ink_short = [I(r, "turns") for r in ep if r["cause"] == "inkOwn"]
        opp_ink_short = [I(r, "turns") for r in ep if r["cause"] == "inkOpp"]
        print(f"        mean length when ended by the opponent's ink {st.mean(opp_ink_short) if opp_ink_short else float('nan'):4.1f}, by its own ink {st.mean(own_ink_short) if own_ink_short else float('nan'):4.1f}")

allg = set(outcome)
table("losses", {g for g in allg if outcome[g] == "loss"})
table("wins", {g for g in allg if outcome[g] == "win"})
table("vs Ando_Riccardo, lost", {g for g in allg if opp[g] == "Ando_Riccardo" and outcome[g] == "loss"})
table("vs Ando_Riccardo, won", {g for g in allg if opp[g] == "Ando_Riccardo" and outcome[g] == "win"})
table("losses, all start turns", {g for g in allg if outcome[g] == "loss"}, 0, 200)
table("wins, all start turns", {g for g in allg if outcome[g] == "win"}, 0, 200)
