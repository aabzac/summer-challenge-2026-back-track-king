import csv, json, collections, statistics as st, os
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"
rep = json.load(open(os.path.join(root, "build/analysis/agent-6813957.json"), encoding="utf-8"))
outcome = {str(g["gameId"]): g["outcome"] for g in rep["games"]}
cells = list(csv.DictReader(open(os.path.join(root, "build/analysis/idle-v12/cells.csv"), encoding="utf-8")))
turns = list(csv.DictReader(open(os.path.join(root, "build/analysis/endgame/v12-turns.csv"), encoding="utf-8")))
I = lambda r, k: int(r[k])
def side_rows(oc, mine):
    return [r for r in cells if outcome.get(r["game"]) == oc and ((r["player"] == r["leader"]) == mine)]
print("earning-cell decomposition per game (mean over games of that outcome):")
for oc in ("loss", "win"):
    n = sum(1 for g in rep["games"] if g["outcome"] == oc)
    for mine, label in ((True, "v12"), (False, "opp")):
        sel = side_rows(oc, mine); earn = [r for r in sel if I(r, "firstEarnTurn") >= 0]
        pts = sum(I(r, "totalPoints") for r in earn)
        mult = []; span_all = 0
        for r in earn:
            end = I(r, "lostTurn") if I(r, "lostTurn") >= 0 else I(r, "gameTurns")
            span = max(1, end - I(r, "firstEarnTurn")); span_all += span; mult.append(I(r, "totalPoints") / span)
        lost_after = sum(1 for r in earn if I(r, "lostTurn") >= 0)
        delay = st.mean(I(r, "delay") for r in earn)
        print(f"  {oc:4} {label}: cells/game {len(sel)/n:5.1f}, earning {len(earn)/n:5.1f} ({100*len(earn)/len(sel):.0f} %), points/earning cell {pts/len(earn):5.1f}, "
              f"active span {span_all/len(earn):5.1f} turns, connections per earning cell-turn {pts/span_all:.2f}, mean delay {delay:.1f}, earning cells later inked {100*lost_after/len(earn):.0f} %")
# multiplicity distribution: share of earning cell-turns at multiplicity >=2 (approx by per-cell average)
print("\nper-cell average multiplicity buckets (earning cells):")
for oc in ("loss", "win"):
    for mine, label in ((True, "v12"), (False, "opp")):
        sel = side_rows(oc, mine); earn = [r for r in sel if I(r, "firstEarnTurn") >= 0]
        b = collections.Counter()
        for r in earn:
            end = I(r, "lostTurn") if I(r, "lostTurn") >= 0 else I(r, "gameTurns"); m = I(r, "totalPoints") / max(1, end - I(r, "firstEarnTurn"))
            b["<1.2" if m < 1.2 else "1.2-1.8" if m < 1.8 else "1.8-2.5" if m < 2.5 else ">=2.5"] += 1
        tot = sum(b.values()); print(f"  {oc:4} {label}: " + ", ".join(f"{k} {100*b[k]/tot:.0f} %" for k in ("<1.2", "1.2-1.8", "1.8-2.5", ">=2.5")))
# global active pairs over time (EndgameStudy), losses vs wins
print("\nactive pairs (global) at turns 10/20/30/40 and pairs on the map:")
byg = collections.defaultdict(dict)
for r in turns: byg[r["game"]][I(r, "turn")] = r
for oc in ("loss", "win"):
    games = [g for g in byg if outcome.get(g) == oc]
    for T in (5, 10, 20, 30, 40):
        v = [I(byg[g][T], "activePairs") for g in games if T in byg[g]]; p = [I(byg[g][T], "pairs") for g in games if T in byg[g]]
        print(f"  {oc:4} T={T:2}: active {st.mean(v):4.1f} of {st.mean(p):4.1f} pairs (n={len(v)})")
# per game: income per turn / active pairs — both sides' income from the trace at T, divided by global active pairs
print("\nincome per active pair at T (v12 / opp):")
for oc in ("loss", "win"):
    for T in (10, 20, 30):
        a = []; b = []
        for g in rep["games"]:
            if g["outcome"] != oc or len(g["trace"]) <= T: continue
            ap = I(byg[str(g["gameId"])][T], "activePairs") if T in byg[str(g["gameId"])] else 0
            if ap <= 0: continue
            a.append(g["trace"][T]["incomeBefore"][g["me"]] / ap); b.append(g["trace"][T]["incomeBefore"][1 - g["me"]] / ap)
        print(f"  {oc:4} T={T}: {st.mean(a):5.2f} / {st.mean(b):5.2f}  (n={len(a)})")
