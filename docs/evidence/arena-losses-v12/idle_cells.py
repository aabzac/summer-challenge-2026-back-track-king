import csv, collections, sys
path = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king\build\analysis\idle-v12\cells.csv"
rows = list(csv.DictReader(open(path, encoding="utf-8")))
def I(r, k): return int(r[k])
games = {r["game"] for r in rows}
print("games", len(games), "cells", len(rows))

def report(label, sel):
    n = len(sel); paint = sum(I(r, "cost") for r in sel)
    idle = [r for r in sel if I(r, "firstEarnTurn") < 0]
    idle_paint = sum(I(r, "cost") for r in idle)
    print(f"\n=== {label}: {n} cells, {paint} paint; never earned {len(idle)} cells = {100*len(idle)/n:.1f} % ({100*idle_paint/paint:.1f} % of paint)")
    lost = [r for r in idle if I(r, "lostTurn") >= 0]
    kept = [r for r in idle if I(r, "lostTurn") < 0]
    neut = [r for r in lost if r["neutralised"] in ("1", "true")]
    print(f"  lost before earning: {len(lost)} ({100*len(lost)/max(1,len(idle)):.1f} % of idle; neutralised {len(neut)}, inked {len(lost)-len(neut)})")
    print(f"  kept to the end, never on an active connection: {len(kept)} ({100*len(kept)/max(1,len(idle)):.1f} % of idle)")
    # inked-before-earning: instability at placement and turns to loss
    inked = [r for r in lost if r["neutralised"] not in ("1", "true")]
    byinst = collections.Counter(I(r, "instab") for r in inked)
    print("  inked: instability at placement ->", dict(sorted(byinst.items())))
    ttl = collections.Counter(min(I(r, "lostTurn") - I(r, "turn"), 12) for r in inked)
    print("  inked: turns placement->ink (capped 12) ->", dict(sorted(ttl.items())))
    opp_in = sum(1 for r in inked if I(r, "oppInRegion") > 0); print(f"  inked: opponent already had tracks in the region at placement: {opp_in}/{len(inked)}")
    town = sum(1 for r in inked if r["townRegion"] in ("1", "true")); print(f"  inked: town regions (should be 0): {town}")
    # kept-but-idle: time to end, route class at placement
    tte = collections.Counter(("<=2" if I(r, "gameTurns") - I(r, "turn") <= 2 else "3-9" if I(r, "gameTurns") - I(r, "turn") <= 9 else ">=10") for r in kept)
    print("  kept idle: turns to game end ->", dict(tte))
    cls = collections.Counter(("completion" if r["onCompletionAny"] in ("1", "true") else "steal" if r["onSteal"] in ("1", "true") else "bypass" if r["onBypass"] in ("1", "true") else "extend" if r["onExtend"] in ("1", "true") else "none") for r in kept)
    print("  kept idle: route class at placement ->", dict(cls))
    far = [r for r in kept if I(r, "gameTurns") - I(r, "turn") >= 10]
    cls_far = collections.Counter(("completion" if r["onCompletionAny"] in ("1", "true") else "steal" if r["onSteal"] in ("1", "true") else "bypass" if r["onBypass"] in ("1", "true") else "extend" if r["onExtend"] in ("1", "true") else "none") for r in far)
    print("  kept idle with >=10 turns left: route class ->", dict(cls_far), "; instab at placement ->", dict(sorted(collections.Counter(I(r, "instab") for r in far).items())))
    # all placements by instability (how often do we build into instab>=2 at all)
    allinst = collections.Counter(I(r, "instab") for r in sel if r["townRegion"] not in ("1", "true"))
    print("  all non-town placements by instability ->", dict(sorted(allinst.items())))
    earned = [r for r in sel if I(r, "firstEarnTurn") >= 0]
    if earned:
        dl = sorted(I(r, "delay") for r in earned); print(f"  earned cells: median delay {dl[len(dl)//2]} turns, mean points {sum(I(r,'totalPoints') for r in earned)/len(earned):.1f}")
    # by game phase: placement turn relative to game length (thirds)
    phase = collections.Counter()
    for r in idle:
        f = I(r, "turn") / max(1, I(r, "gameTurns")); phase["early(<1/3)" if f < 1/3 else "mid" if f < 2/3 else "late(>=2/3)"] += 1
    tot = collections.Counter()
    for r in sel:
        f = I(r, "turn") / max(1, I(r, "gameTurns")); tot["early(<1/3)" if f < 1/3 else "mid" if f < 2/3 else "late(>=2/3)"] += 1
    print("  idle share by game phase ->", {k: f"{phase[k]}/{tot[k]} = {100*phase[k]/max(1,tot[k]):.0f} %" for k in ("early(<1/3)", "mid", "late(>=2/3)")})

mine = [r for r in rows if r["player"] == r["leader"]]
theirs = [r for r in rows if r["player"] != r["leader"]]
report("v12 (aabzac)", mine)
report("opponents", theirs)
# by map size for us
print("\n=== v12 idle share by map (height) ===")
heights = {}
import json, os
for g in games:
    try:
        raw = json.load(open(os.path.join(r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king\build\analysis\idle-v12\replays", g + ".json"), encoding="utf-8"))
        import re
        m = re.search(r'"graphics":"(\d+)\|(\d+)\|', raw["frames"][0].get("view", "")); heights[g] = f"{m.group(1)}x{m.group(2)}" if m else "?"
    except Exception as e: heights[g] = "?"
bysize = collections.defaultdict(lambda: [0, 0])
for r in mine:
    k = heights.get(r["game"], "?"); bysize[k][1] += 1; bysize[k][0] += 1 if I(r, "firstEarnTurn") < 0 else 0
for k in sorted(bysize): print(f"  {k}: idle {bysize[k][0]}/{bysize[k][1]} = {100*bysize[k][0]/bysize[k][1]:.0f} %")
# win vs loss games
outcome = {}
ev = json.load(open(r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king\docs\evidence\arena-2026-10-25-v12-agent-6813957.json", encoding="utf-8"))
for g in ev["games"]: outcome[str(g["gameId"])] = g["outcome"]
for oc in ("win", "loss"):
    s = [r for r in mine if outcome.get(r["game"]) == oc]
    if s: print(f"  {oc} games: idle {sum(1 for r in s if I(r,'firstEarnTurn')<0)}/{len(s)} = {100*sum(1 for r in s if I(r,'firstEarnTurn')<0)/len(s):.0f} %")
