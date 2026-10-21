"""Behavioural fingerprint of the boss seat from BossIntentStudy tables.

Usage: python fingerprint.py <label> <turns.csv> <cells.csv> [gameIdFilterFile]
If a filter file is given (one game id per line), only those games are used.
"""
import csv, sys, statistics as st, collections, json

label, turns_path, cells_path = sys.argv[1:4]
filt = None
if len(sys.argv) > 4:
    filt = set(l.strip() for l in open(sys.argv[4]) if l.strip())

rows = []
for r in csv.DictReader(open(turns_path)):
    if filt is not None and r['game'] not in filt:
        continue
    for k, v in r.items():
        if k not in ('game', 'oppName', 'intent'):
            r[k] = int(v)
    rows.append(r)
cells = [c for c in csv.DictReader(open(cells_path)) if filt is None or c['game'] in filt]

games = collections.defaultdict(list)
for r in rows: games[r['game']].append(r)
fp = {}
fp['games'] = len(games)
last = [g[-1] for g in games.values()]
# winner from final pre-turn scores + last turn income is approximate; use score at last state (pre-turn) as proxy
fp['boss_win_rate(last pre-turn score)'] = round(sum(1 for g in last if g['scoreBoss'] > g['scoreOpp']) / len(last), 3)
lens = [max(x['turn'] for x in g) + 1 for g in games.values()]
fp['game_length_mean'] = round(st.mean(lens), 1); fp['game_length_median'] = st.median(lens)
fp['closed_before_100'] = round(sum(1 for l in lens if l < 100) / len(lens), 3)
dis = [r for r in rows if r['target'] >= 0]
fp['disrupt_rate'] = round(len(dis) / len(rows), 3)
early = [r for r in rows if r['turn'] < 45]
fp['disrupt_rate_turns<45'] = round(sum(1 for r in early if r['target'] >= 0) / max(1, len(early)), 3)
holdable = [r for r in dis if r['turn'] > 0]
fp['same_target_rate'] = round(sum(r['sameTarget'] for r in holdable) / max(1, len(holdable)), 3)
sw = [r for r in holdable if not r['sameTarget']]
fp['switch_after_ink_share'] = round(sum(r['prevTargetInked'] for r in sw) / max(1, len(sw)), 3)
commit = [r for r in dis if r['turn'] == 0 or not r['sameTarget']]
def cls(r):
    if r['targetOppCells'] == 0: return 'empty/own'
    return 'opp-active' if r['targetOppActive'] > 0 else 'opp-construction'
cc = collections.Counter(cls(r) for r in commit)
fp['commit_classes'] = {k: round(v / len(commit), 3) for k, v in cc.items()}
fp['commit_instab'] = {k: round(v / len(commit), 3) for k, v in sorted(collections.Counter(r['targetInstab'] for r in commit).items())}
fp['commit_live2_share'] = round(sum(1 for r in commit if r['targetLive2'] > 0) / len(commit), 3)
fp['placement_rate'] = round(sum(1 for r in rows if r['placed'] > 0) / len(rows), 3)
fp['paint_per_turn'] = round(st.mean(r['paint'] for r in rows), 2)
for lo, hi in ((0, 3), (3, 11), (11, 21), (21, 31), (31, 46), (46, 101)):
    seg = [r for r in rows if lo <= r['turn'] < hi]
    if seg: fp[f'paint_turns_{lo}-{hi-1}'] = round(st.mean(r['paint'] for r in seg), 2)
fp['cells_instab'] = {k: round(v / len(cells), 3) for k, v in sorted(collections.Counter(c['regionInstab'] for c in cells).items())}
fp['cells_town_region'] = round(sum(1 for c in cells if c['townRegion'] == '1') / len(cells), 3)
fp['cells_shared_region'] = round(sum(1 for c in cells if c['sharedRegion'] == '1') / len(cells), 3)
fp['cells_adj_own'] = round(sum(1 for c in cells if c['adjOwn'] == '1') / len(cells), 3)
fp['income_boss_t20'] = round(st.mean(r['incomeBoss'] for r in rows if r['turn'] == 20), 1) if any(r['turn'] == 20 for r in rows) else None
fp['income_opp_t20'] = round(st.mean(r['incomeOpp'] for r in rows if r['turn'] == 20), 1) if any(r['turn'] == 20 for r in rows) else None
fp['income_boss_t30'] = round(st.mean(r['incomeBoss'] for r in rows if r['turn'] == 30), 1) if any(r['turn'] == 30 for r in rows) else None
fp['income_opp_t30'] = round(st.mean(r['incomeOpp'] for r in rows if r['turn'] == 30), 1) if any(r['turn'] == 30 for r in rows) else None
print(json.dumps({label: fp}, indent=1))
