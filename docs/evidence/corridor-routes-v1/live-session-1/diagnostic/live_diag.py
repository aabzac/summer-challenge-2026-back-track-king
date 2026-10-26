import csv, json, collections, statistics as st, os, sys
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"
E = os.path.join(root, "docs/evidence/corridor-routes-v1/live-session-1")
plan = json.load(open(os.path.join(E, "plan.json"), encoding="utf-8"))
pairs = {}
for s in plan["slots"]:
    r = json.load(open(os.path.join(E, "results", "%04d.json" % s["slot"]), encoding="utf-8"))["outcome"]
    pairs.setdefault(s["pair"], {})[s["arm"]] = dict(game=str(r["gameId"]), res=r["result"], scores=r["scores"], seat=r["seat"], map=r["mapHash"][:8])
game_arm = {}; game_seat = {}; game_res = {}; game_pair = {}
for p, d in pairs.items():
    for arm, g in d.items():
        game_arm[g["game"]] = arm; game_seat[g["game"]] = g["seat"]; game_res[g["game"]] = g["res"]; game_pair[g["game"]] = p
I = lambda r, k: int(r[k])
cells = {}
for arm, d in (("K1", "live-k1"), ("v12", "live-v12")):
    cells[arm] = list(csv.DictReader(open(os.path.join(root, "build/analysis", d, "cells.csv"), encoding="utf-8")))
turns = {}
for arm, f in (("K1", "k1-turns.csv"), ("v12", "v12-turns.csv")):
    turns[arm] = list(csv.DictReader(open(os.path.join(root, "build/analysis/live-turns", f), encoding="utf-8")))

def cls(r):
    return ("completion" if r["onCompletionAny"] in ("1", "true") else "steal" if r["onSteal"] in ("1", "true")
            else "bypass" if r["onBypass"] in ("1", "true") else "extend" if r["onExtend"] in ("1", "true") else "none")

def side_report(arm, mine):
    rows = [r for r in cells[arm] if (r["player"] == r["leader"]) == mine]
    games = {r["game"] for r in rows}; n = len(games)
    earn = [r for r in rows if I(r, "firstEarnTurn") >= 0]; idle = [r for r in rows if I(r, "firstEarnTurn") < 0]
    lost = [r for r in idle if I(r, "lostTurn") >= 0]; neut = [r for r in lost if r["neutralised"] in ("1", "true")]
    kept = [r for r in idle if I(r, "lostTurn") < 0]
    pts = sum(I(r, "totalPoints") for r in earn)
    span = 0
    for r in earn:
        end = I(r, "lostTurn") if I(r, "lostTurn") >= 0 else I(r, "gameTurns"); span += max(1, end - I(r, "firstEarnTurn"))
    earned_lost = sum(1 for r in earn if I(r, "lostTurn") >= 0)
    paint = sum(I(r, "cost") for r in rows)
    label = f"{arm} {'ours' if mine else 'boss'}"
    print(f"{label:9} games {n:2} cells/game {len(rows)/n:6.1f} paint/game {paint/n:6.1f} | earning {len(earn)/n:5.1f} ({100*len(earn)/len(rows):4.1f} %) "
          f"pts/earning cell {pts/len(earn):5.1f} span {span/len(earn):5.1f} mult {pts/span:4.2f} delay {st.mean(I(r,'delay') for r in earn):4.2f} earning later lost {100*earned_lost/len(earn):4.1f} % | "
          f"idle {100*len(idle)/len(rows):4.1f} %: inked {len(lost)-len(neut)} ({(len(lost)-len(neut))/n:4.1f}/g) neutralised {len(neut)} kept {len(kept)} ({len(kept)/n:4.1f}/g)")
    return rows, earn, idle, lost, neut, kept

print("=== per-cell, both arms, both sides (40 games each, same maps/seats) ===")
R = {}
for arm in ("K1", "v12"):
    for mine in (True, False):
        R[(arm, mine)] = side_report(arm, mine)

print("\n=== our cells: route class at placement (all cells) and of the kept-idle ones ===")
for arm in ("K1", "v12"):
    rows, earn, idle, lost, neut, kept = R[(arm, True)]
    c_all = collections.Counter(cls(r) for r in rows); c_kept = collections.Counter(cls(r) for r in kept)
    print(f"  {arm}: all {dict(c_all)} | kept idle {dict(c_kept)}")
    far = [r for r in kept if I(r, "gameTurns") - I(r, "turn") >= 10]
    print(f"      kept idle with >=10 turns left: {len(far)} ({len(far)/40:.1f}/game), class {dict(collections.Counter(cls(r) for r in far))}")

print("\n=== our cells: route length at placement, slack, instability, opponent presence ===")
for arm in ("K1", "v12"):
    rows = R[(arm, True)][0]
    comp = [r for r in rows if r["onCompletionAny"] in ("1", "true")]
    rl = [I(r, "completionRouteLen") for r in comp if I(r, "completionRouteLen") > 0]
    inst = collections.Counter(I(r, "instab") for r in rows if r["townRegion"] not in ("1", "true"))
    opp = sum(1 for r in rows if I(r, "oppInRegion") > 0); own = sum(1 for r in rows if I(r, "ownInRegion") > 0)
    adj = collections.Counter((r["adjOwn"] in ("1", "true"), r["adjTown"] in ("1", "true")) for r in rows)
    print(f"  {arm}: completion route len mean {st.mean(rl):5.2f} median {st.median(rl):4.1f} (n={len(rl)}); instab at placement {dict(sorted(inst.items()))}; "
          f"placed where boss already has tracks {100*opp/len(rows):4.1f} %, where own tracks {100*own/len(rows):4.1f} %; adjOwn {100*sum(v for (a,t),v in adj.items() if a)/len(rows):4.1f} %")

print("\n=== our inked cells (lost before earning): instability at placement, turns to ink, boss in region ===")
for arm in ("K1", "v12"):
    rows, earn, idle, lost, neut, kept = R[(arm, True)]
    inked = [r for r in lost if r["neutralised"] not in ("1", "true")]
    inked_all = [r for r in rows if I(r, "lostTurn") >= 0 and r["neutralised"] not in ("1", "true")]
    ttl = [I(r, "lostTurn") - I(r, "turn") for r in inked]
    print(f"  {arm}: inked before earning {len(inked)} ({len(inked)/40:.1f}/game), all inked (incl. after earning) {len(inked_all)} ({len(inked_all)/40:.1f}/game), "
          f"instab at placement {dict(sorted(collections.Counter(I(r,'instab') for r in inked).items()))}, turns to ink median {st.median(ttl) if ttl else '-'}, boss in region {sum(1 for r in inked if I(r,'oppInRegion')>0)}/{len(inked)}")
    # points lost: cells inked after earning — their points and lifetime
    ea = [r for r in earn if I(r, "lostTurn") >= 0]
    print(f"      earning cells later inked: {len(ea)} ({len(ea)/40:.1f}/game), mean points before loss {st.mean(I(r,'totalPoints') for r in ea) if ea else 0:.1f}")

print("\n=== multiplicity buckets of our earning cells ===")
for arm in ("K1", "v12"):
    earn = R[(arm, True)][1]; b = collections.Counter()
    for r in earn:
        end = I(r, "lostTurn") if I(r, "lostTurn") >= 0 else I(r, "gameTurns"); m = I(r, "totalPoints") / max(1, end - I(r, "firstEarnTurn"))
        b["<1.2" if m < 1.2 else "1.2-1.8" if m < 1.8 else "1.8-2.5" if m < 2.5 else ">=2.5"] += 1
    tot = sum(b.values()); print(f"  {arm}: " + ", ".join(f"{k} {100*b[k]/tot:.0f} %" for k in ("<1.2", "1.2-1.8", "1.8-2.5", ">=2.5")) + f"; maxPairs mean {st.mean(I(r,'maxPairs') for r in earn):.2f}")

print("\n=== per-turn (EndgameStudy): our score lead, active pairs, inked regions at turns 10/20/30/40 and at the end ===")
byg = {}
for arm in ("K1", "v12"):
    for r in turns[arm]: byg.setdefault((arm, r["game"]), {})[I(r, "turn")] = r
for arm in ("K1", "v12"):
    games = [g for (a, g) in byg if a == arm]
    for T in (10, 20, 30, 40):
        lead = []; ap = []; ink = []; cm = []; cb = []
        for g in games:
            d = byg[(arm, g)]
            if T not in d: continue
            r = d[T]; seat = game_seat[g]; sd = I(r, "scoreDiff"); lead.append(sd if seat == 0 else -sd)
            ap.append(I(r, "activePairs")); ink.append(I(r, "inkedRegions"))
            cm.append(I(r, "cells0") if seat == 0 else I(r, "cells1")); cb.append(I(r, "cells1") if seat == 0 else I(r, "cells0"))
        print(f"  {arm} T={T}: our lead {st.mean(lead):7.1f} (n={len(lead)}), active pairs {st.mean(ap):4.1f}, inked regions {st.mean(ink):4.1f}, cells ours/boss {st.mean(cm):5.1f}/{st.mean(cb):5.1f}")
    ends = []; lens = []
    for g in games:
        d = byg[(arm, g)]; T = max(d); r = d[T]; seat = game_seat[g]; sd = I(r, "scoreDiff"); ends.append(sd if seat == 0 else -sd); lens.append(I(r, "gameTurns"))
    print(f"  {arm} end: our lead {st.mean(ends):7.1f}, game length {st.mean(lens):5.1f}, regions inked at the end {st.mean(I(byg[(arm,g)][max(byg[(arm,g)])],'inkedRegions')/I(byg[(arm,g)][max(byg[(arm,g)])],'regions') for g in games)*100:4.1f} %")

print("\n=== per pair (same map, same seat): outcome and our score, K1 vs v12 ===")
def ours(g): return g["scores"][g["seat"]]
def boss(g): return g["scores"][1 - g["seat"]]
dis = collections.Counter()
rows_out = []
for p in sorted(pairs):
    d = pairs[p]; k, v = d["candidate"], d["control"]
    key = f"{k['res']}{v['res']}"; dis[key] += 1
    rows_out.append((p, k["map"], k["seat"], k["res"], ours(k), boss(k), v["res"], ours(v), boss(v)))
print("  outcome combos (K1,v12):", dict(dis))
print("  pair map      seat K1  ours  boss | v12  ours  boss")
for r in rows_out: print(f"  {r[0]:4} {r[1]} {r[2]:4} {r[3]:3} {r[4]:5} {r[5]:5} | {r[6]:3} {r[7]:5} {r[8]:5}")
print(f"  mean ours K1 {st.mean(r[4] for r in rows_out):.0f} vs v12 {st.mean(r[7] for r in rows_out):.0f}; boss vs K1 {st.mean(r[5] for r in rows_out):.0f} vs boss vs v12 {st.mean(r[8] for r in rows_out):.0f}")

print("\n=== the 9 maps v12 won and K1 lost vs the 28 equal: our per-game cell stats by arm ===")
def game_stats(arm, game):
    rows = [r for r in cells[arm] if r["game"] == game and r["player"] == r["leader"]]
    if not rows: return None
    earn = [r for r in rows if I(r, "firstEarnTurn") >= 0]
    inked = [r for r in rows if I(r, "lostTurn") >= 0 and r["neutralised"] not in ("1", "true")]
    pts = sum(I(r, "totalPoints") for r in earn)
    return dict(cells=len(rows), earn=len(earn), inked=len(inked), pts=pts, delay=st.mean(I(r, "delay") for r in earn) if earn else 0)
for label, sel in (("v12 won, K1 lost", [p for p in pairs if pairs[p]["candidate"]["res"] == "L" and pairs[p]["control"]["res"] == "W"]),
                   ("K1 won, v12 lost", [p for p in pairs if pairs[p]["candidate"]["res"] == "W" and pairs[p]["control"]["res"] == "L"]),
                   ("both won", [p for p in pairs if pairs[p]["candidate"]["res"] == "W" and pairs[p]["control"]["res"] == "W"]),
                   ("both lost", [p for p in pairs if pairs[p]["candidate"]["res"] == "L" and pairs[p]["control"]["res"] == "L"])):
    for arm_key, arm in (("candidate", "K1"), ("control", "v12")):
        stats = [game_stats(arm, pairs[p][arm_key]["game"]) for p in sel]; stats = [s for s in stats if s]
        if not stats: continue
        print(f"  {label:18} {arm:3} n={len(stats):2}: cells {st.mean(s['cells'] for s in stats):5.1f} earning {st.mean(s['earn'] for s in stats):5.1f} inked {st.mean(s['inked'] for s in stats):5.1f} points {st.mean(s['pts'] for s in stats):6.0f} delay {st.mean(s['delay'] for s in stats):4.2f}")
