import json, csv, collections, os, statistics as st
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"
rep = json.load(open(os.path.join(root, "build/analysis/agent-6813957.json"), encoding="utf-8"))
cells = list(csv.DictReader(open(os.path.join(root, "build/analysis/idle-v12/cells.csv"), encoding="utf-8")))
outcome = {str(g["gameId"]): g["outcome"] for g in rep["games"]}
# pooled idle share per outcome, both sides
for oc in ("win", "loss"):
    for mine, label in ((True, "me"), (False, "opp")):  # leader = our seat index; our cells have player == leader
        sel = [r for r in cells if outcome.get(r["game"]) == oc and ((r["player"] == r["leader"]) == mine)]
        idle = sum(1 for r in sel if int(r["firstEarnTurn"]) < 0)
        print(f"pooled idle share, {oc:4} games, {label}: {idle}/{len(sel)} = {100*idle/max(1,len(sel)):.1f} %")
# opponent-level table
by_opp = collections.defaultdict(lambda: [0, 0, 0.0])
for g in rep["games"]:
    opp = g["names"][1 - g["me"]]; raw = json.load(open(os.path.join(root, g["file"].replace("\\", "/")), encoding="utf-8"))
    sc = [a for a in raw["agents"] if a["index"] == 1 - g["me"]][0].get("score") or 0
    by_opp[opp][0 if g["outcome"] == "win" else 1] += 1; by_opp[opp][2] = sc
print("\nopponents (W-L, arena score):")
for k, v in sorted(by_opp.items(), key=lambda kv: -kv[1][2]):
    print(f"  {k:20} {v[0]}-{v[1]}  {v[2]:.1f}")
# early curves: cumulative income and cells per turn, me vs opp, losses vs wins (turns 0..24)
def curves(sel):
    inc = [[0.0, 0.0] for _ in range(25)]; cel = [[0.0, 0.0] for _ in range(25)]; n = len(sel)
    for g in sel:
        me = g["me"]; opp = 1 - me; cm = co = 0; km = ko = 0
        for t, row in enumerate(g["trace"][:25]):
            cm += row["gained"][me]; co += row["gained"][opp]; km += row["cells"][me]; ko += row["cells"][opp]
            inc[t][0] += cm / n; inc[t][1] += co / n; cel[t][0] += km / n; cel[t][1] += ko / n
    return inc, cel
for oc in ("loss", "win"):
    sel = [g for g in rep["games"] if g["outcome"] == oc]
    inc, cel = curves(sel)
    print(f"\n{oc} games (n={len(sel)}): cumulative income me/opp and cells me/opp at turns 5,10,15,20,24:")
    for t in (5, 10, 15, 20, 24):
        print(f"  t={t:2}: income {inc[t][0]:6.0f}/{inc[t][1]:6.0f}   cells {cel[t][0]:5.1f}/{cel[t][1]:5.1f}")
# first three income turns (turn index when cumulative income first exceeds thresholds) and active pairs
def first_turn(g, who, thr):
    c = 0
    for t, row in enumerate(g["trace"]):
        c += row["gained"][who]
        if c >= thr: return t
    return -1
for oc in ("loss", "win"):
    sel = [g for g in rep["games"] if g["outcome"] == oc]
    for thr in (1, 20, 50, 100):
        me = [first_turn(g, g["me"], thr) for g in sel]; op = [first_turn(g, 1 - g["me"], thr) for g in sel]
        me = [x for x in me if x >= 0]; op = [x for x in op if x >= 0]
        print(f"{oc:4}: turn when cumulative income reaches {thr:3}: me {st.mean(me):5.1f}  opp {st.mean(op):5.1f}")
# opponents' disrupt timing vs ours in the first 10 turns and the counterfactual value of our disrupts early
for oc in ("loss", "win"):
    sel = [g for g in rep["games"] if g["outcome"] == oc]
    early_my = st.mean(sum(t.get("withoutMyDisruptDelta", 0) for t in g["trace"][:20] if t.get("counterfactualsAvailable")) for g in sel)
    early_opp = st.mean(sum(t.get("withoutOpponentDisruptDelta", 0) for t in g["trace"][:20] if t.get("counterfactualsAvailable")) for g in sel)
    ink_me = st.mean(sum(t["inkedOwnTracks"][g["me"]] for t in g["trace"][:20]) for g in sel)
    ink_op = st.mean(sum(t["inkedOwnTracks"][1 - g["me"]] for t in g["trace"][:20]) for g in sel)
    print(f"{oc:4}: first 20 turns — value of my disrupts (score delta if omitted) {early_my:6.0f}, of the opponent's {early_opp:6.0f}; my tracks inked {ink_me:4.1f}, theirs {ink_op:4.1f}")
