import json, os, sys, re
base = sys.argv[1] if len(sys.argv) > 1 else 'build/cg-boss/stage2-d-v09-boss-40b'
plan = json.load(open(f'{base}/plan.json', encoding='utf-8'))
pairs = {}
for s in plan['slots']:
    f = f"{base}/{s['slot']:04d}/result.json"
    if not os.path.exists(f): continue
    o = json.load(open(f, encoding='utf-8'))['outcome']
    pts = {'W': 1.0, 'D': 0.5, 'L': 0.0}.get(o['result'], None)
    size = '?'
    for cand in (f"{base}/{s['slot']:04d}/canonical.json", f"{base}/{s['slot']:04d}/attempt-1/canonical.json", f"{base}/{s['slot']:04d}/attempt-2/canonical.json"):
        if os.path.exists(cand):
            try:
                raw = json.load(open(cand, encoding='utf-8')); m = re.search(r'"graphics":"(\d+)\|(\d+)\|', raw['frames'][0].get('view', ''))
                if m: size = f"{m.group(1)}x{m.group(2)}"
            except Exception: pass
            break
    pairs.setdefault(s['pair'], {})[s['arm']] = dict(pts=pts, res=o['result'], scores=o['scores'], seat=o['seat'], game=o['gameId'], size=size)
done = [p for p in pairs.values() if 'candidate' in p and 'control' in p]
def tot(arm): v = [p[arm]['pts'] for p in pairs.values() if arm in p]; return sum(v), len(v)
cw, cn = tot('candidate'); kw, kn = tot('control')
print(f"played: candidate {cn} games {cw:.1f} pts ({100*cw/max(1,cn):.1f}%), control {kn} games {kw:.1f} pts ({100*kw/max(1,kn):.1f}%); complete pairs {len(done)}")
if done:
    dc = sum(1 for p in done if p['candidate']['pts'] > p['control']['pts']); dk = sum(1 for p in done if p['candidate']['pts'] < p['control']['pts'])
    diff = sum(p['candidate']['pts'] - p['control']['pts'] for p in done) / len(done)
    print(f"paired over {len(done)} maps: candidate-only wins {dc}, control-only wins {dk}, equal {len(done)-dc-dk}; mean diff {100*diff:+.1f} pp (pass needs >= +5.0 on 40 maps)")
    for seat in (0, 1):
        ps = [p for p in done if p['candidate']['seat'] == seat]
        if ps: print(f"  seat P{seat}: candidate {sum(p['candidate']['pts'] for p in ps):.0f}/{len(ps)}, control {sum(p['control']['pts'] for p in ps):.0f}/{len(ps)}")
    sizes = {}
    for p in done:
        k = p['candidate']['size']; a = sizes.setdefault(k, [0, 0, 0]); a[0] += p['candidate']['pts']; a[1] += p['control']['pts']; a[2] += 1
    print('  by map size (cand pts / ctrl pts / maps):', {k: (round(v[0], 1), round(v[1], 1), v[2]) for k, v in sorted(sizes.items())})
