"""Compare how now-Gold players beat Boss 4 with how our bots play it, from canonical CG replays.

Two corpora, both against the same boss agent (6740207):
  winners: replays/cg-boss-winners/  -- the boss's recent battles against players who are now in Gold
  ours:    replays/cg/boss/**        -- our own IDE/boss games (canonical files only)

Everything is computed from viewer events (types 1/2/3/5/6/7) plus tracked occupancy; no game
rules are re-implemented. Per-turn income reconstructed from active paths is checked against the
recorded scoring events and the run fails on any mismatch.

  python tools/diagnostics/boss_winners_study.py fetch [--boss 6740207] [--league-index 4]
  python tools/diagnostics/boss_winners_study.py study [--out docs/evidence/boss-winners-study-v1.json]

`fetch` saves a leaderboard snapshot, the boss's last battles, and every canonical game against
opponents whose current league index equals --league-index (Gold = 4 while Legend is open).
"""
import argparse
import collections
import glob
import json
import os
import re
import statistics
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CHALLENGE = 'summer-challenge-2026-back-track-king'
PHASES = [(1, 10), (11, 20), (21, 30), (31, 45), (46, 200)]
LIVE_TURNS = 2


# ---------------------------------------------------------------- CG access

def post(service, body):
    req = urllib.request.Request('https://www.codingame.com/services/' + service,
                                 data=json.dumps(body).encode(), headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode())


def fetch(args):
    stamp = time.strftime('%Y-%m-%d')
    snap = os.path.join(ROOT, 'build', 'cg-snapshots')
    os.makedirs(snap, exist_ok=True)
    lb = post('Leaderboards/getFilteredChallengeLeaderboard',
              [CHALLENGE, None, 'global', {'active': False, 'column': '', 'filter': ''}])
    json.dump(lb, open(os.path.join(snap, f'leaderboard-{stamp}.json'), 'w', encoding='utf-8'))
    battles = post('gamesPlayersRanking/findLastBattlesByAgentId', [args.boss, None])
    json.dump(battles, open(os.path.join(snap, f'boss-{args.boss}-battles-{stamp}.json'), 'w', encoding='utf-8'))
    league_of = {u['pseudo']: u.get('league', {}).get('divisionIndex') for u in lb['users'] if 'pseudo' in u}
    out = os.path.join(ROOT, 'replays', 'cg-boss-winners')
    os.makedirs(out, exist_ok=True)
    saved = present = 0
    for b in battles:
        if not b.get('done'):
            continue
        opp = [p for p in b['players'] if p.get('playerAgentId') != args.boss]
        if not opp or league_of.get(opp[0].get('nickname')) != args.league_index:
            continue
        path = os.path.join(out, f"{b['gameId']}.json")
        if os.path.exists(path):
            present += 1
            continue
        game = post('gameResult/findByGameId', [b['gameId'], None])
        names = {}
        for a in game.get('agents', []):
            cg = a.get('codingamer') or {}
            if 'pseudo' in cg:
                names[str(a['index'])] = cg['pseudo']
            elif a.get('arenaboss'):
                names[str(a['index'])] = a['arenaboss'].get('nickname', 'Boss')
        game['btkNicknames'] = names
        game['btkSource'] = {'bossAgent': args.boss, 'opponent': opp[0].get('nickname'),
                             'opponentWon': opp[0]['position'] == 0, 'battle': b, 'fetchedAt': stamp}
        json.dump(game, open(path, 'w', encoding='utf-8'))
        saved += 1
        time.sleep(0.4)
    print(f'fetch: {saved} saved, {present} already present -> {out}')


# ---------------------------------------------------------------- replay parsing

def parse_map(view):
    g = json.loads(view.split('\n', 1)[1])['global']['graphics'].split('|')
    w, h = int(g[0]), int(g[1])
    cells = g[3:3 + w * h]
    region = [int(c.split()[0]) for c in cells]
    terrain = [int(c.split()[1]) for c in cells]
    tc = int(g[3 + w * h])
    towns = [tuple(int(v) for v in t.split()[:3]) for t in g[4 + w * h:4 + w * h + tc]]
    town_regions = {region[y * w + x] for _, x, y in towns}
    town_cells = {y * w + x for _, x, y in towns}
    return w, h, region, terrain, town_regions, town_cells


def parse_events(view):
    if '\n' not in view:
        return []
    body = view.split('\n', 1)[1].strip()
    gfx = json.loads(body).get('graphics', '') if body else ''
    if not gfx:
        return []
    tok = gfx.split('|')
    ev, i = [], 1
    for _ in range(int(tok[0])):
        if i + 3 >= len(tok):
            break
        ev.append((int(tok[i]), tok[i + 3].split()))
        i += 4
    return ev


def phase(t):
    for i, (a, b) in enumerate(PHASES):
        if a <= t <= b:
            return i
    return len(PHASES) - 1


def corpus_files(name):
    if name == 'winners':
        return sorted(glob.glob(os.path.join(ROOT, 'replays', 'cg-boss-winners', '*.json')))
    base = os.path.join(ROOT, 'replays', 'cg', 'boss')
    out = set()
    for p in glob.glob(os.path.join(base, '**', '*.json'), recursive=True):
        rel = os.path.relpath(p, base).replace('\\', '/')
        if rel.startswith(('raw/', '_metadata/')) or not re.fullmatch(r'\d+\.json', os.path.basename(p)):
            continue
        out.add(p)
    return sorted(out)


class Side:
    """Per-player, per-phase counters plus whole-game counters."""

    def __init__(self):
        self.phase = collections.defaultdict(collections.Counter)
        self.game = collections.Counter()


def analyze(path, boss_agent):
    g = json.load(open(path, encoding='utf-8'))
    fr = g['frames']
    w, h, region, terrain, town_regions, town_cells = parse_map(fr[0]['view'])
    boss = next((a['index'] for a in g['agents'] if a.get('agentId') == boss_agent or a.get('arenaboss')), None)
    m = re.search(r'"endScreen":\[\[(-?\d+),(-?\d+)\]', fr[-1]['view'])
    if boss is None or not m:
        return None
    final = (int(m.group(1)), int(m.group(2)))
    me = 1 - boss
    nreg = max(region) + 1
    occ, paths, placed_turn = {}, {}, {}
    last_place = {0: [-99] * nreg, 1: [-99] * nreg}
    sides = {0: Side(), 1: Side()}
    stdout = {0: '', 1: ''}
    boss_tag = collections.Counter()
    projects, current_project = [], None
    turn = 0
    for f in fr[1:]:
        aid = f.get('agentId', -1)
        if aid in (0, 1):
            stdout[aid] = f.get('stdout', '') or ''
        if not f.get('keyframe'):
            continue
        ev = parse_events(f['view'])
        turn += 1
        ph = phase(turn)
        active_cells = set()
        for cells in paths.values():
            active_cells.update(cells)
        # boss project from its MESSAGE tag
        pair = None
        for s in stdout[boss].strip().split(';'):
            s = s.strip()
            if s.startswith('MESSAGE'):
                text = s[8:].strip()
                boss_tag[text.split()[0] if text else ''] += 1
                mm = re.search(r'^build\s+(\d+)\s*>\s*(\d+)', text)
                if mm:
                    pair = (int(mm.group(1)), int(mm.group(2)))
        if pair is not None and (current_project is None or current_project['pair'] != pair):
            current_project = {'pair': pair, 'start': turn, 'end': turn, 'lost': 0}
            projects.append(current_project)
        elif pair is None:
            current_project = None
        if current_project is not None:
            current_project['end'] = turn
        for p in (0, 1):
            sides[p].phase[ph]['turns'] += 1
            if any(c.strip().startswith(('PLACE_TRACKS', 'AUTOPLACE')) for c in stdout[p].split(';')):
                sides[p].phase[ph]['build_turns'] += 1
        for t, d in ev:
            if t == 1:
                p, x, y = int(d[0]), int(d[1]), int(d[2])
                c = y * w + x
                occ[c] = 2 if c in occ and occ[c] != p else p
                placed_turn[c] = turn
                if p in (0, 1):
                    last_place[p][region[c]] = turn
                    sides[p].phase[ph]['paint'] += terrain[c] + 1
                    sides[p].phase[ph]['cells'] += 1
                    sides[p].game['cells'] += 1
                    if region[c] in town_regions:
                        sides[p].phase[ph]['protected_cells'] += 1
        inked_now = {int(d[0]) for t, d in ev if t == 5}
        for t, d in ev:
            if t == 2:
                p, r = int(d[0]), int(d[1])
                s = sides[p]
                s.phase[ph]['disrupts'] += 1
                opp = [c for c, o in occ.items() if region[c] == r and o == 1 - p]
                cls = 'none' if not opp else ('active' if any(c in active_cells for c in opp) else 'construction')
                s.game['hits'] += 1
                s.game['hit_' + cls] += 1
                if last_place[1 - p][r] >= turn - LIVE_TURNS:
                    s.game['hit_live'] += 1
                    if cls == 'construction':
                        s.game['hit_construction_live'] += 1
                if turn == 1:
                    s.game['first_turn_disrupt'] += 1
        for r in inked_now:
            for c in [c for c in occ if region[c] == r]:
                o = occ[c]
                if o in (0, 1):
                    sides[o].game['tracks_lost'] += 1
                    if o == boss and current_project is not None:
                        current_project['lost'] += 1
                del occ[c]
        changed = set()
        for t, d in ev:
            if t == 6:
                changed.add((int(d[0]), int(d[1])))
        for t, d in ev:
            if t == 7:
                a, b = int(d[0]), int(d[1])
                paths[(a, b)] = [int(d[i + 1]) * w + int(d[i]) for i in range(2, len(d), 2)]
                changed.discard((a, b))
        for key in changed:
            paths.pop(key, None)
        if current_project is not None and (pair in paths or (pair[1], pair[0]) in paths):
            current_project['completed'] = True
        # income split from active paths, validated against scoring events
        scored = [0, 0]
        for t, d in ev:
            if t == 3:
                scored[int(d[0])] += int(d[1])
        calc = [0, 0]
        contested = 0
        for cells in paths.values():
            own = [0, 0]
            for c in cells:
                if c in town_cells:
                    continue
                o = occ.get(c, -1)
                if o in (0, 1):
                    own[o] += 1
            if own[0] and own[1]:
                contested += 1
            for p in (0, 1):
                calc[p] += own[p]
                if own[p] == 0:
                    continue
                if own[p] > own[1 - p]:
                    sides[p].phase[ph]['own_conns'] += 1
                    sides[p].phase[ph]['income_own'] += own[p]
                elif own[p] < own[1 - p]:
                    sides[p].phase[ph]['income_stolen'] += own[p]
                else:
                    sides[p].phase[ph]['income_tie'] += own[p]
                if own[1 - p] == 0:
                    sides[p].phase[ph]['exclusive_conns'] += 1
        if calc != scored:
            raise ValueError(f'{os.path.basename(path)} turn {turn}: reconstructed income {calc} != scored {scored}')
        for p in (0, 1):
            sides[p].phase[ph]['income'] += scored[p]
            sides[p].phase[ph]['contested_conns'] += contested
    for pr in projects:
        pr.setdefault('completed', False)
    boss_side = sides[boss]
    boss_side.game['projects'] = len(projects)
    boss_side.game['projects_completed'] = sum(1 for p in projects if p['completed'])
    boss_side.game['projects_abandoned_after_ink'] = sum(1 for p in projects if not p['completed'] and p['lost'] > 0)
    return {'game': os.path.basename(path), 'turns': turn, 'playerWon': final[me] > final[boss],
            'final': final, 'player': sides[me], 'boss': sides[boss], 'bossTags': boss_tag}


# ---------------------------------------------------------------- aggregation

def aggregate(results):
    out = {}
    for outcome in ('playerWon', 'bossWon'):
        games = [r for r in results if r['playerWon'] == (outcome == 'playerWon')]
        if not games:
            continue
        block = {'games': len(games), 'lengthMean': round(statistics.mean(r['turns'] for r in games), 2),
                 'lengthMedian': statistics.median(r['turns'] for r in games)}
        for who in ('player', 'boss'):
            side = {}
            for i, (a, b) in enumerate(PHASES):
                agg = collections.Counter()
                for r in games:
                    agg.update(r[who].phase[i])
                t = agg['turns']
                if not t:
                    continue
                side[f'{a}-{b}'] = {
                    'turns': t,
                    'incomePerTurn': round(agg['income'] / t, 2),
                    'incomeOwnMajorityPerTurn': round(agg['income_own'] / t, 2),
                    'incomeStolenPerTurn': round(agg['income_stolen'] / t, 2),
                    'ownMajorityConnsPerTurn': round(agg['own_conns'] / t, 3),
                    'exclusiveConnsPerTurn': round(agg['exclusive_conns'] / t, 3),
                    'buildTurnShare': round(agg['build_turns'] / t, 4),
                    'paintPerTurn': round(agg['paint'] / t, 3),
                    'disruptTurnShare': round(agg['disrupts'] / t, 4),
                    'protectedCellShare': round(agg['protected_cells'] / max(1, agg['cells']), 4),
                }
            g = collections.Counter()
            for r in games:
                g.update(r[who].game)
            hits = max(1, g['hits'])
            side['game'] = {
                'hitsPerGame': round(g['hits'] / len(games), 2),
                'hitClassShare': {c: round(g['hit_' + c] / hits, 4) for c in ('none', 'construction', 'active')},
                'hitLiveShare': round(g['hit_live'] / hits, 4),
                'constructionHitLiveShare': round(g['hit_construction_live'] / max(1, g['hit_construction']), 4),
                'firstTurnDisruptShare': round(g['first_turn_disrupt'] / len(games), 4),
                'cellsPerGame': round(g['cells'] / len(games), 2),
                'tracksLostPerGame': round(g['tracks_lost'] / len(games), 2),
            }
            if who == 'boss':
                pr = max(1, g['projects'])
                side['game']['projectsPerGame'] = round(g['projects'] / len(games), 2)
                side['game']['projectCompletedShare'] = round(g['projects_completed'] / pr, 4)
                side['game']['projectAbandonedAfterInkShare'] = round(g['projects_abandoned_after_ink'] / pr, 4)
            block[who] = side
        out[outcome] = block
    return out


def print_summary(name, agg):
    print(f'=== {name}')
    for outcome, block in agg.items():
        print(f"  {outcome}: n={block['games']} length mean/median={block['lengthMean']}/{block['lengthMedian']}")
        for who in ('player', 'boss'):
            s = block[who]
            row = '    ' + who.ljust(7)
            for ph in ('11-20', '21-30', '31-45'):
                v = s.get(ph)
                if v:
                    row += (f" | {ph}: inc={v['incomePerTurn']:5.1f} own={v['incomeOwnMajorityPerTurn']:5.1f} "
                            f"conns={v['ownMajorityConnsPerTurn']:.2f} build={100*v['buildTurnShare']:.0f}% disr={100*v['disruptTurnShare']:.0f}%")
            print(row)
            gm = s['game']
            extra = (f" projects: completed={100*gm['projectCompletedShare']:.1f}% abandonedAfterInk={100*gm['projectAbandonedAfterInkShare']:.1f}%"
                     if who == 'boss' else '')
            print(f"      hits/game={gm['hitsPerGame']} live={100*gm['hitLiveShare']:.1f}% "
                  f"constructionLive={100*gm['constructionHitLiveShare']:.1f}% cells/game={gm['cellsPerGame']}{extra}")


def study(args):
    report = {'schemaVersion': 1, 'bossAgent': args.boss, 'liveTurns': LIVE_TURNS, 'phases': PHASES, 'corpora': {}}
    for name in ('winners', 'ours'):
        files = corpus_files(name)
        results = [r for r in (analyze(f, args.boss) for f in files) if r is not None]
        tags = collections.Counter()
        for r in results:
            tags.update(r['bossTags'])
        agg = aggregate(results)
        report['corpora'][name] = {'files': len(files), 'analyzed': len(results),
                                   'bossMessageTags': dict(tags.most_common(6)), 'byOutcome': agg}
        print_summary(f'{name}: {len(results)} games', agg)
    if args.out:
        path = os.path.join(ROOT, args.out)
        if os.path.exists(path) and not args.force:
            raise SystemExit(f'refusing to overwrite existing evidence {args.out}; use --force')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        json.dump(report, open(path, 'w', encoding='utf-8'), indent=1)
        print('wrote', args.out)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest='cmd', required=True)
    f = sub.add_parser('fetch')
    f.add_argument('--boss', type=int, default=6740207)
    f.add_argument('--league-index', type=int, default=4)
    s = sub.add_parser('study')
    s.add_argument('--boss', type=int, default=6740207)
    s.add_argument('--out', default='')
    s.add_argument('--force', action='store_true')
    args = ap.parse_args()
    (fetch if args.cmd == 'fetch' else study)(args)


if __name__ == '__main__':
    main()
