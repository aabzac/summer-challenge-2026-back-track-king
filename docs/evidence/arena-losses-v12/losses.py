import json, csv, collections, os, statistics as st
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"
rep = json.load(open(os.path.join(root, "build/analysis/agent-6813957.json"), encoding="utf-8"))
cells = list(csv.DictReader(open(os.path.join(root, "build/analysis/idle-v12/cells.csv"), encoding="utf-8")))
turns = list(csv.DictReader(open(os.path.join(root, "build/analysis/endgame/v12-turns.csv"), encoding="utf-8")))
idle = collections.defaultdict(lambda: [[0, 0], [0, 0]])  # game -> [[idle, total] me, [idle,total] opp]
for r in cells:
    side = 0 if r["player"] == r["leader"] else 1  # leader = our seat index; our cells have player == leader
    idle[r["game"]][side][1] += 1
    if int(r["firstEarnTurn"]) < 0: idle[r["game"]][side][0] += 1
inked_end = {}
for r in turns:
    inked_end[r["game"]] = int(r["inkedRegions"]) / int(r["regions"])
rows = []
for g in rep["games"]:
    gid = str(g["gameId"]); me = g["me"]; opp = 1 - me
    raw = json.load(open(os.path.join(root, g["file"].replace("\\", "/")), encoding="utf-8"))
    oscore = [a for a in raw["agents"] if a["index"] == opp][0].get("score")
    tr = g["trace"]
    diff = [t["score"][me] - t["score"][opp] for t in tr]
    gained_me = [t["gained"][me] for t in tr]; gained_opp = [t["gained"][opp] for t in tr]
    last_lead = max([i for i, d in enumerate(diff) if d >= 0], default=-1)
    max_lead = max(diff); min_lead = min(diff)
    n = len(tr)
    # income over thirds
    third = max(1, n // 3)
    inc = lambda arr, a, b: sum(arr[a:b])
    ink_me = sum(t["inkedOwnTracks"][me] for t in tr); ink_opp = sum(t["inkedOwnTracks"][opp] for t in tr)
    built_inked_me = sum(t["builtThenInked"][me] for t in tr); built_inked_opp = sum(t["builtThenInked"][opp] for t in tr)
    my_disrupts = sum(1 for t in tr if t["disrupt"][me] >= 0); opp_disrupts = sum(1 for t in tr if t["disrupt"][opp] >= 0)
    cf = [t for t in tr if t.get("counterfactualsAvailable")]
    w_my_dis = sum(t["withoutMyDisruptDelta"] for t in cf); w_opp_dis = sum(t["withoutOpponentDisruptDelta"] for t in cf)
    best_dis = sum(t["bestImmediateDisruptDelta"] for t in cf)
    first_dis_opp = next((i for i, t in enumerate(tr) if t["disrupt"][opp] >= 0), -1)
    rows.append(dict(game=gid, outcome=g["outcome"], opp=g["names"][opp], oscore=round(oscore or 0, 1), size=f'{g["width"]}x{g["height"]}',
                     seat=me, turns=n, final=diff[-1], maxLead=max_lead, minLead=min_lead, lastLead=last_lead,
                     firstIncMe=g["firstIncome"][me], firstIncOpp=g["firstIncome"][opp], firstDisOpp=first_dis_opp,
                     inc1=(inc(gained_me, 0, third), inc(gained_opp, 0, third)), inc2=(inc(gained_me, third, 2*third), inc(gained_opp, third, 2*third)),
                     inc3=(inc(gained_me, 2*third, n), inc(gained_opp, 2*third, n)),
                     inkMe=ink_me, inkOpp=ink_opp, builtInkedMe=built_inked_me, builtInkedOpp=built_inked_opp,
                     disMe=my_disrupts, disOpp=opp_disrupts, wMyDis=w_my_dis, wOppDis=w_opp_dis, bestDis=best_dis,
                     idleMe=round(100 * idle[gid][0][0] / max(1, idle[gid][0][1])), idleOpp=round(100 * idle[gid][1][0] / max(1, idle[gid][1][1])),
                     inkedEnd=round(100 * inked_end.get(gid, 0)), cellsMe=g["cells"][me], cellsOpp=g["cells"][opp]))
losses = [r for r in rows if r["outcome"] == "loss"]; wins = [r for r in rows if r["outcome"] == "win"]
print(f"games {len(rows)}: wins {len(wins)}, losses {len(losses)}\n")
print("LOSSES (sorted by opponent score):")
hdr = "game        opp                 oscore size   seat turns final maxLd minLd lastLd fIncMe fIncOp fDisOp inc1(me/op)  inc2       inc3       inkMe inkOp bInkMe bInkOp disMe disOp wMyDis wOpDis idleMe idleOp inkEnd cells"
print(hdr)
for r in sorted(losses, key=lambda r: -r["oscore"]):
    print(f'{r["game"]:11} {r["opp"][:18]:18} {r["oscore"]:6} {r["size"]:6} {r["seat"]:4} {r["turns"]:5} {r["final"]:5} {r["maxLead"]:5} {r["minLead"]:5} {r["lastLead"]:6} {r["firstIncMe"]:6} {r["firstIncOpp"]:6} {r["firstDisOpp"]:6} {str(r["inc1"]):12} {str(r["inc2"]):10} {str(r["inc3"]):10} {r["inkMe"]:5} {r["inkOpp"]:5} {r["builtInkedMe"]:6} {r["builtInkedOpp"]:6} {r["disMe"]:5} {r["disOpp"]:5} {r["wMyDis"]:6} {r["wOppDis"]:6} {r["idleMe"]:6} {r["idleOpp"]:6} {r["inkedEnd"]:6} {r["cellsMe"]}/{r["cellsOpp"]}')
def agg(sel, key, f=lambda x: x):
    v = [f(r[key]) for r in sel]; return round(st.mean(v), 1)
print("\nAGGREGATES (mean) losses vs wins:")
for key in ("oscore", "turns", "maxLead", "firstIncMe", "firstIncOpp", "firstDisOpp", "inkMe", "inkOpp", "builtInkedMe", "builtInkedOpp", "disMe", "disOpp", "wMyDis", "wOppDis", "idleMe", "idleOpp", "inkedEnd", "cellsMe", "cellsOpp"):
    print(f"  {key:14} losses {agg(losses, key):8}  wins {agg(wins, key):8}")
for i, key in enumerate(("inc1", "inc2", "inc3")):
    print(f"  {key} me/opp     losses {agg(losses, key, lambda x: x[0])}/{agg(losses, key, lambda x: x[1])}   wins {agg(wins, key, lambda x: x[0])}/{agg(wins, key, lambda x: x[1])}")
# when was the lead lost: lastLead relative to turns
print("\nlosses: lead lost at (lastLead/turns):", sorted(round(r["lastLead"] / r["turns"], 2) for r in losses))
print("losses: never led (lastLead<=1):", sum(1 for r in losses if r["lastLead"] <= 1), "; led past half:", sum(1 for r in losses if r["lastLead"] > r["turns"] / 2))
print("losses by opp score bucket:", collections.Counter(("<=20" if r["oscore"] <= 20 else "20-25" if r["oscore"] <= 25 else "25-30" if r["oscore"] <= 30 else ">30") for r in losses))
print("wins by opp score bucket:  ", collections.Counter(("<=20" if r["oscore"] <= 20 else "20-25" if r["oscore"] <= 25 else "25-30" if r["oscore"] <= 30 else ">30") for r in wins))
print("opponent disrupt rate (disOpp/turns): losses", round(st.mean(r["disOpp"] / r["turns"] for r in losses), 2), "wins", round(st.mean(r["disOpp"] / r["turns"] for r in wins), 2))
print("final margin in losses:", sorted(r["final"] for r in losses))
