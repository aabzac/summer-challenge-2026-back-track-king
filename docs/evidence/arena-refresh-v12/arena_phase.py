import json, os, statistics as st
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king"
rep = json.load(open(os.path.join(root, "build/analysis/agent-6814073.json"), encoding="utf-8"))

def phase(g, a, b):
    me, opp = g["me"], 1 - g["me"]; tr = g["trace"]
    if len(tr) <= b: return None
    seg = tr[a:b]
    return dict(
        inc_gain_me=tr[b]["incomeBefore"][me] - tr[a]["incomeBefore"][me], inc_gain_opp=tr[b]["incomeBefore"][opp] - tr[a]["incomeBefore"][opp],
        cells_me=sum(t["cells"][me] for t in seg), cells_opp=sum(t["cells"][opp] for t in seg),
        inked_me=sum(t["inkedOwnTracks"][me] for t in seg), inked_opp=sum(t["inkedOwnTracks"][opp] for t in seg),
        bti_me=sum(t["builtThenInked"][me] for t in seg), bti_opp=sum(t["builtThenInked"][opp] for t in seg),
        wmy=sum(t.get("withoutMyDisruptDelta", 0) for t in seg if t.get("counterfactualsAvailable")),
        wopp=sum(t.get("withoutOpponentDisruptDelta", 0) for t in seg if t.get("counterfactualsAvailable")),
        wplace=sum(t.get("withoutMyPlacementDelta", 0) for t in seg if t.get("counterfactualsAvailable")),
        place_turns_me=sum(1 for t in seg if t["cells"][me] > 0), place_turns_opp=sum(1 for t in seg if t["cells"][opp] > 0))

def summarize(label, games, a, b):
    ph = [p for p in (phase(g, a, b) for g in games) if p]
    if not ph: return
    m = lambda k: st.mean(p[k] for p in ph)
    print(f"  {label:26} n={len(ph):3} t{a}-{b}: income gain me {m('inc_gain_me'):+6.1f} / opp {m('inc_gain_opp'):+6.1f}; cells {m('cells_me'):4.1f}/{m('cells_opp'):4.1f}; "
          f"placing turns {m('place_turns_me'):4.1f}/{m('place_turns_opp'):4.1f}; own tracks inked {m('inked_me'):4.1f}/{m('inked_opp'):4.1f}; "
          f"built-then-inked {m('bti_me'):4.1f}/{m('bti_opp'):4.1f}; value of my disrupts {-m('wmy'):+6.0f}, of theirs {-m('wopp'):+6.0f} (score swing they caused)")

wins = [g for g in rep["games"] if g["outcome"] == "win"]; losses = [g for g in rep["games"] if g["outcome"] == "loss"]
ando = [g for g in rep["games"] if g["names"][1 - g["me"]] == "Ando_Riccardo"]
print("=== v12 re-upload (6814073), phase breakdown ===")
for a, b in ((0, 10), (10, 20), (20, 30), (30, 40)):
    summarize("losses", losses, a, b); summarize("wins", wins, a, b)
    summarize("vs Ando_Riccardo, lost", [g for g in ando if g["outcome"] == "loss"], a, b)
    summarize("vs Ando_Riccardo, won", [g for g in ando if g["outcome"] == "win"], a, b)
    print()
