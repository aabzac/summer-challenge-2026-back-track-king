import csv, collections, math, os, statistics as st
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"
W = [+0.137316, -0.111814, +0.775046, -0.003927, -0.282833, +0.291303, +0.884541, +0.371747, -0.001751, +0.368378, -3.437223, +0.155573, +0.148325]
hold = {}
for line in open(os.path.join(root, "build/analysis/boss-v3/holdout-games.txt"), encoding="utf-8"):
    g, a = line.strip().split(","); hold[g] = "K1" if a == "candidate" else "v12"

def load_probe(path):
    sets = collections.defaultdict(list)
    for r in csv.DictReader(open(path, encoding="utf-8")):
        if r["game"] not in hold: continue
        sets[(r["game"], int(r["turn"]))].append(r)
    return sets
probe = load_probe(os.path.join(root, "build/analysis/boss-v4/probe-k1.csv"))
probe.update(load_probe(os.path.join(root, "build/analysis/boss-v4/probe-v12.csv")))
# study rows for the holdout games only
study = collections.defaultdict(dict)
with open(os.path.join(root, "build/analysis/boss-leader-v3/sabotage.csv"), encoding="utf-8") as fh:
    rd = csv.DictReader(fh)
    for r in rd:
        if r["game"] not in hold or r["player"] != r["leader"]: continue
        study[(r["game"], int(r["turn"]))][int(r["region"])] = r
print("probe sets", len(probe), "study sets", len(study), "common", len(set(probe) & set(study)))

F = lambda r, k: float(r[k])
def uc(rows_by_region, chosen_region):
    r = rows_by_region.get(chosen_region)
    return r is not None and F(r, "oppCells") >= 3 and F(r, "oppActive") == 0

def score_probe(r, swap=None, srow=None):
    vals = {"dOpp": F(r, "dOpp"), "dOwn": F(r, "dOwn"), "oppCells": F(r, "oppCells"), "oppActive": F(r, "oppActive"), "ownCells": F(r, "ownCells"),
            "oppPrev2": F(r, "oppPrev2"), "instab": F(r, "instab"), "oppTraffic": F(r, "oppTraffic"), "routesAny": F(r, "routesAny"),
            "adjTown": F(r, "adjTown"), "neutralCells": F(r, "neutralCells"), "logFree": math.log1p(F(r, "freeCells"))}
    if swap and srow is not None:
        m = {"oppPrev2": "oppPlacedPrev2", "oppTraffic": "oppRouteTraffic", "adjTown": "adjTownRegion"}
        for k in swap:
            if k == "logFree": vals[k] = math.log1p(F(srow, "freeCells"))
            else: vals[k] = F(srow, m.get(k, k))
    return (W[0]*vals["dOpp"] + W[1]*vals["dOwn"] + W[2]*vals["oppCells"] + W[3]*vals["oppActive"] + W[4]*vals["ownCells"] + W[5]*vals["oppPrev2"]
            + W[6]*vals["instab"] + W[7]*vals["oppTraffic"] + W[8]*vals["routesAny"] + W[9]*vals["adjTown"] + W[11]*vals["neutralCells"] + W[12]*vals["logFree"])

def study_score(r):
    return (W[0]*F(r,"dOpp") + W[1]*F(r,"dOwn") + W[2]*F(r,"oppCells") + W[3]*F(r,"oppActive") + W[4]*F(r,"ownCells") + W[5]*F(r,"oppPlacedPrev2")
            + W[6]*F(r,"instab") + W[7]*F(r,"oppRouteTraffic") + W[8]*F(r,"routesAny") + W[9]*F(r,"adjTownRegion") + W[10]*F(r,"closes")
            + W[11]*F(r,"neutralCells") + W[12]*math.log1p(F(r,"freeCells")))

stats = collections.defaultdict(lambda: collections.Counter())
diffs = collections.defaultdict(collections.Counter)
pairs_feat = [("dOpp","dOpp"),("dOwn","dOwn"),("oppCells","oppCells"),("oppActive","oppActive"),("ownCells","ownCells"),("oppPrev2","oppPlacedPrev2"),
              ("instab","instab"),("oppTraffic","oppRouteTraffic"),("routesAny","routesAny"),("adjTown","adjTownRegion"),("neutralCells","neutralCells"),("freeCells","freeCells")]
SWAPS = [None, ["oppPrev2"], ["oppTraffic"], ["routesAny"], ["adjTown"], ["logFree"], ["oppPrev2","oppTraffic"], ["oppPrev2","oppTraffic","routesAny","adjTown","logFree","dOpp","dOwn"]]
for key in sorted(set(probe) & set(study)):
    game, turn = key
    if turn == 0: continue
    arm = hold[game]; prow = probe[key]; srows = study[key]
    boss = next((int(r["region"]) for r in prow if r["bossChosen"] == "1"), None)
    if boss is None: continue
    sets = ["all"] + (["uc"] if uc(srows, boss) else [])
    # study-frame argmax (all study candidates, closes included in the logit)
    sbest = max(srows.values(), key=study_score)
    # probe pieces
    m1 = next((int(r["region"]) for r in prow if r["choiceM1"] == "1"), -1)
    m0 = next((int(r["region"]) for r in prow if r["choiceM0"] == "1"), -1)
    elig = [r for r in prow if r["closeWin"] == "0" and r["closeLose"] == "0"]
    anywin = any(r["closeWin"] == "1" for r in prow)
    pbest = max(elig, key=score_probe) if elig else None
    for s in sets:
        c = stats[(arm, s)]; c["n"] += 1
        c["study_argmax"] += int(int(sbest["region"]) == boss)
        c["proxy_m1"] += int(m1 == boss); c["proxy_m0"] += int(m0 == boss)
        c["probe_argmax_no_close"] += int(pbest is not None and int(pbest["region"]) == boss)
        c["winning_close_available"] += int(anywin)
        c["proxy_m0_took_close"] += int(any(r["closeWin"] == "1" and r["choiceM0"] == "1" for r in prow))
        c["boss_in_probe_candidates"] += int(any(r["bossChosen"] == "1" and r["closeWin"] == "0" and r["closeLose"] == "0" for r in prow))
        for sw in SWAPS[1:]:
            best = max(elig, key=lambda r: score_probe(r, sw, srows.get(int(r["region"])))) if elig else None
            c["swap:" + "+".join(sw)] += int(best is not None and int(best["region"]) == boss)
    for r in prow:
        sr = srows.get(int(r["region"]))
        if sr is None: diffs[arm]["region_missing_in_study"] += 1; continue
        diffs[arm]["rows"] += 1
        for pk, sk in pairs_feat:
            if abs(F(r, pk) - F(sr, sk)) > 1e-3: diffs[arm][pk] += 1
for (arm, s), c in sorted(stats.items()):
    n = c["n"]
    print(f"\n{arm} {s}: n={n}")
    for k in ["study_argmax", "probe_argmax_no_close", "proxy_m0", "proxy_m1", "winning_close_available", "proxy_m0_took_close", "boss_in_probe_candidates"] + [k for k in c if k.startswith("swap:")]:
        print(f"   {k:48s} {100*c[k]/n:5.1f} %")
print("\nfeature mismatch, probe vs study, share of joined candidate rows:")
for arm, d in diffs.items():
    n = d["rows"]; print(f"  {arm}: rows {n}, regions missing in study {d['region_missing_in_study']}; " + ", ".join(f"{k} {100*d[k]/n:.1f} %" for k, _ in pairs_feat))
