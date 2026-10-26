import csv, collections, statistics as st, os
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king\build\analysis\boss-target"
I = lambda r, k: int(r[k])
def load(name):
    p = os.path.join(root, name + ".csv")
    return list(csv.DictReader(open(p, encoding="utf-8"))) if os.path.exists(p) else None

print("=== the boss's own targeting vs our construction (rows = boss turns with a DISRUPT; from the proxy run, the boss columns are model-independent) ===")
for arm in ("k1", "v12"):
    rows = load(f"{arm}-proxy")
    if not rows: continue
    rows = [r for r in rows if I(r, "turn") >= 1]  # turn 0: empty board, every region ties at 0
    n = len(rows)
    top1 = sum(1 for r in rows if I(r, "rankBoss") == 1); top3 = sum(1 for r in rows if I(r, "rankBoss") <= 3)
    zero = sum(1 for r in rows if I(r, "oppTracksBoss") == 0)
    earn = sum(1 for r in rows if I(r, "oppEarningBoss") > 0)
    print(f"  {arm:3}: boss disrupts {n:4}; target = region with the most of our tracks {100*top1/n:4.1f} % (top-3 {100*top3/n:4.1f} %); "
          f"our tracks in the target mean {st.mean(I(r,'oppTracksBoss') for r in rows):4.2f} (max on board {st.mean(I(r,'maxOppTracks') for r in rows):4.2f}); "
          f"target holds our earning tracks {100*earn/n:4.1f} %; target has none of our tracks {100*zero/n:4.1f} %; instability of target mean {st.mean(I(r,'instabBoss') for r in rows):4.2f}")
    # by phase
    for lo, hi, name in ((1, 10, "t1-10"), (11, 20, "t11-20"), (21, 45, "t21-45"), (46, 999, "t46+")):
        sel = [r for r in rows if lo <= I(r, "turn") <= hi]
        if not sel: continue
        print(f"       {name:6}: n={len(sel):4} top-1 {100*sum(1 for r in sel if I(r,'rankBoss')==1)/len(sel):4.1f} %, our tracks in target {st.mean(I(r,'oppTracksBoss') for r in sel):4.2f}, earning in target {100*sum(1 for r in sel if I(r,'oppEarningBoss')>0)/len(sel):4.1f} %")

print("\n=== agreement of each local boss model with the boss's actual region, and the model's own targeting ===")
for arm in ("k1", "v12"):
    for model in ("proxy", "nv6", "v12"):
        rows = load(f"{arm}-{model}")
        if not rows: continue
        rows = [r for r in rows if I(r, "turn") >= 1 and I(r, "botTarget") >= 0]
        n = len(rows); same = sum(1 for r in rows if I(r, "same") == 1)
        top1 = sum(1 for r in rows if I(r, "rankBot") == 1)
        by_conc = {}
        for lab, pred in (("max<=5", lambda r: I(r, "maxOppTracks") <= 5), ("max 6-9", lambda r: 6 <= I(r, "maxOppTracks") <= 9), ("max>=10", lambda r: I(r, "maxOppTracks") >= 10)):
            sel = [r for r in rows if pred(r)]
            by_conc[lab] = (len(sel), 100 * sum(1 for r in sel if I(r, "same") == 1) / max(1, len(sel)))
        # when the boss hits the top-1 region, does the model?
        bt = [r for r in rows if I(r, "rankBoss") == 1]; bt_same = sum(1 for r in bt if I(r, "same") == 1)
        # when they differ: our tracks in the boss's region vs the model's region
        diff = [r for r in rows if I(r, "same") == 0]
        print(f"  {arm:3} {model:5}: n={n:4} same region {100*same/n:4.1f} %; model picks our top-1 region {100*top1/n:4.1f} %; "
              f"agreement when the boss hits our top-1 region {100*bt_same/max(1,len(bt)):4.1f} % (n={len(bt)}); "
              f"when they differ: our tracks boss {st.mean(I(r,'oppTracksBoss') for r in diff):4.2f} vs model {st.mean(I(r,'oppTracksBot') for r in diff):4.2f}, "
              f"our earning boss {st.mean(I(r,'oppEarningBoss') for r in diff):4.2f} vs model {st.mean(I(r,'oppEarningBot') for r in diff):4.2f}, instab boss {st.mean(I(r,'instabBoss') for r in diff):4.2f} vs model {st.mean(I(r,'instabBot') for r in diff):4.2f}; "
              f"by concentration " + ", ".join(f"{k} {v[1]:.0f} % (n={v[0]})" for k, v in by_conc.items()))

print("\n=== K1 vs v12: how concentrated is our construction when the boss disrupts (same maps) ===")
for arm in ("k1", "v12"):
    rows = load(f"{arm}-proxy")
    if not rows: continue
    rows = [r for r in rows if I(r, "turn") >= 1]
    mx = [I(r, "maxOppTracks") for r in rows]; tot = [I(r, "oppTracksTotal") for r in rows]
    share = [I(r, "maxOppTracks") / max(1, I(r, "oppTracksTotal")) for r in rows if I(r, "oppTracksTotal") >= 20]
    print(f"  {arm:3}: max tracks in one disruptable region mean {st.mean(mx):4.2f} (median {st.median(mx)}), share of our tracks in that region (boards with >=20 tracks) {100*st.mean(share):4.1f} %, "
          f"our tracks in the boss's target {st.mean(I(r,'oppTracksBoss') for r in rows):4.2f}, earning tracks in the target {st.mean(I(r,'oppEarningBoss') for r in rows):4.2f}")
