import json, os, collections, statistics as st, sys
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"

def load(agent):
    rep = json.load(open(os.path.join(root, f"build/analysis/agent-{agent}.json"), encoding="utf-8"))
    rows = []
    for g in rep["games"]:
        me, opp = g["me"], 1 - g["me"]
        raw = json.load(open(os.path.join(root, g["file"].replace("\\", "/")), encoding="utf-8"))
        oag = [a for a in raw["agents"] if a["index"] == opp][0]
        oscore = oag.get("score") or 0
        tr = g["trace"]
        paint = [sum(t["paint"][p] for t in tr) for p in (0, 1)]
        final = [tr[-1]["score"][p] for p in (0, 1)]
        def cum(p, T): return sum(t["gained"][p] for t in tr[:T])
        def inc_at(p, T): return tr[T]["incomeBefore"][p] if len(tr) > T else None
        last_lead = max([i for i, t in enumerate(tr) if t["score"][me] - t["score"][opp] >= 0], default=-1)
        rows.append(dict(game=g["gameId"], outcome=g["outcome"], opp=g["names"][opp], oscore=oscore, w=g["width"], h=g["height"],
                         seat=me, turns=len(tr), final_me=final[me], final_opp=final[opp], paint_me=paint[me], paint_opp=paint[opp],
                         cells_me=g["cells"][me], cells_opp=g["cells"][opp], fi_me=g["firstIncome"][me], fi_opp=g["firstIncome"][opp],
                         inc10=(inc_at(me, 10), inc_at(opp, 10)), inc20=(inc_at(me, 20), inc_at(opp, 20)), inc30=(inc_at(me, 30), inc_at(opp, 30)),
                         never=g["acceptedCellsNeverEarned"], last_lead=last_lead,
                         wmy=sum(t.get("withoutMyDisruptDelta", 0) for t in tr if t.get("counterfactualsAvailable")),
                         wopp=sum(t.get("withoutOpponentDisruptDelta", 0) for t in tr if t.get("counterfactualsAvailable")),
                         inked_me=sum(t["inkedOwnTracks"][me] for t in tr), inked_opp=sum(t["inkedOwnTracks"][opp] for t in tr)))
    return rows

def pct(a, b): return f"{a}-{b} ({100*a/max(1,a+b):.1f} %)"
def rec(rows): w = sum(1 for r in rows if r["outcome"] == "win"); l = sum(1 for r in rows if r["outcome"] == "loss"); return w, l

for agent, label in ((6813957, "v12 first upload (6813957)"), (6814073, "v12 re-upload (6814073)")):
    rows = load(agent)
    print(f"\n===== {label}: {len(rows)} games, record {pct(*rec(rows))} =====")
    scores = sorted(r["oscore"] for r in rows); med = scores[len(scores)//2]
    print(f"  opponent arena score at game time: median {med:.2f}, range {scores[0]:.2f}..{scores[-1]:.2f}")
    for lab, sel in (("opp score >= 28", [r for r in rows if r["oscore"] >= 28]), ("26-28", [r for r in rows if 26 <= r["oscore"] < 28]),
                     ("24-26", [r for r in rows if 24 <= r["oscore"] < 26]), ("< 24", [r for r in rows if r["oscore"] < 24])):
        if sel: print(f"  {lab:16}: {pct(*rec(sel))}  n={len(sel)}")
    for lab, sel in (("tall h>=19", [r for r in rows if r["h"] >= 19]), ("30x20", [r for r in rows if r["w"] == 30]), ("small h<19", [r for r in rows if r["h"] < 19])):
        print(f"  {lab:16}: {pct(*rec(sel))}")
    for s in (0, 1): print(f"  seat P{s}          : {pct(*rec([r for r in rows if r['seat'] == s]))}")
    byopp = collections.defaultdict(list)
    for r in rows: byopp[r["opp"]].append(r)
    rep_opp = sorted(((k, v) for k, v in byopp.items() if len(v) >= 4), key=lambda kv: -len(kv[1]))
    print("  repeat opponents (>= 4 games): " + "; ".join(f"{k} {rec(v)[0]}-{rec(v)[1]} (score {v[0]['oscore']:.1f})" for k, v in rep_opp))
    for oc in ("loss", "win"):
        sel = [r for r in rows if r["outcome"] == oc]
        if not sel: continue
        ppp_me = st.mean(r["final_me"] / max(1, r["paint_me"]) for r in sel); ppp_opp = st.mean(r["final_opp"] / max(1, r["paint_opp"]) for r in sel)
        def m(key, i): v = [r[key][i] for r in sel if r[key][i] is not None]; return st.mean(v) if v else float("nan")
        print(f"  {oc:4} n={len(sel):3}: turns {st.mean(r['turns'] for r in sel):5.1f}; final {st.mean(r['final_me'] for r in sel):6.0f}/{st.mean(r['final_opp'] for r in sel):6.0f}; "
              f"paint {st.mean(r['paint_me'] for r in sel):5.0f}/{st.mean(r['paint_opp'] for r in sel):5.0f}; cells {st.mean(r['cells_me'] for r in sel):5.0f}/{st.mean(r['cells_opp'] for r in sel):5.0f}; "
              f"pts/paint {ppp_me:5.1f}/{ppp_opp:5.1f}; first income {st.mean(r['fi_me'] for r in sel):3.1f}/{st.mean(r['fi_opp'] for r in sel):3.1f}; "
              f"income@10 {m('inc10',0):5.1f}/{m('inc10',1):5.1f} @20 {m('inc20',0):5.1f}/{m('inc20',1):5.1f} @30 {m('inc30',0):5.1f}/{m('inc30',1):5.1f}; "
              f"tracks inked {st.mean(r['inked_me'] for r in sel):4.0f}/{st.mean(r['inked_opp'] for r in sel):4.0f}; my disrupts worth {st.mean(r['wmy'] for r in sel):5.0f}, theirs {st.mean(r['wopp'] for r in sel):5.0f}")
    losses = [r for r in rows if r["outcome"] == "loss"]
    if losses:
        led_past_half = sum(1 for r in losses if r["last_lead"] > r["turns"] / 2); never = sum(1 for r in losses if r["last_lead"] <= 1)
        print(f"  losses: led past the midpoint {led_past_half}/{len(losses)}, never led {never}/{len(losses)}; margin median {st.median(r['final_opp']-r['final_me'] for r in losses):.0f}")
