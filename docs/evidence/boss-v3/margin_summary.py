import csv, os, statistics as st
root = r"C:\Disk_D\Programming\cursor\summer-challenge-2026-back-track-king\build\analysis\boss-target"
I = lambda r, k: int(r[k])
def load(name):
    p = os.path.join(root, name + ".csv")
    return [r for r in csv.DictReader(open(p, encoding="utf-8")) if I(r, "turn") >= 1 and I(r, "botTarget") >= 0] if os.path.exists(p) else None
print("=== proxy agreement with the boss's region by hold margin (BOSS_HOLD_MARGIN), K1 games / v12 games ===")
print("margin   K1 same   K1 n   | v12 same  v12 n  | K1: by concentration <=5 / 6-9 / >=10 | under-construction attacks K1 / v12 | turns 11-45 K1 / v12")
for m, name in (("0", "m0"), ("0.5", "m0.5"), ("1.0", ""), ("2", "m2")):
    row = []
    out = f"{m:6} "
    stats = {}
    for arm in ("k1", "v12"):
        rows = load(f"{arm}-proxy" + (f"-{name}" if name else ""))
        if not rows: stats[arm] = None; continue
        n = len(rows); same = sum(1 for r in rows if I(r, "same") == 1)
        conc = []
        for lo, hi in ((0, 5), (6, 9), (10, 999)):
            sel = [r for r in rows if lo <= I(r, "maxOppTracks") <= hi]
            conc.append(100 * sum(1 for r in sel if I(r, "same") == 1) / max(1, len(sel)))
        uc = [r for r in rows if I(r, "oppEarningBoss") == 0 and I(r, "oppTracksBoss") >= 3]
        uc_same = 100 * sum(1 for r in uc if I(r, "same") == 1) / max(1, len(uc))
        mid = [r for r in rows if 11 <= I(r, "turn") <= 45]
        mid_same = 100 * sum(1 for r in mid if I(r, "same") == 1) / max(1, len(mid))
        stats[arm] = (100 * same / n, n, conc, uc_same, mid_same)
    k, v = stats["k1"], stats["v12"]
    if not k or not v: print(out + "(missing)"); continue
    print(f"{m:6}  {k[0]:5.1f} %  {k[1]:5}  |  {v[0]:5.1f} %  {v[1]:5}  |  {k[2][0]:4.0f} / {k[2][1]:4.0f} / {k[2][2]:4.0f} %            |  {k[3]:4.1f} % / {v[3]:4.1f} %                   |  {k[4]:4.1f} % / {v[4]:4.1f} %")
