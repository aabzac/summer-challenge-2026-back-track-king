"""Study how the leader Indrill (Python3, Legend #1 on 2026-09-20) plays, from canonical CG replays.

  python tools/diagnostics/indrill_study.py fetch [--agent 6752275] [--out replays/cg-indrill]
  python tools/diagnostics/indrill_study.py study [--corpus replays/cg-indrill] [--player Indrill]
                                                  [--out docs/evidence/indrill-study-v1.json] [--force]

`fetch` saves a dated leaderboard snapshot, the agent's last battles and every canonical
`gameResult` (both players' stdout) into the corpus directory. Files are never overwritten.

`study` computes everything from viewer events (types 1/2/3/5/6/7) plus tracked occupancy and the
recorded stdout; game rules are not re-implemented. Per-turn income reconstructed from active
paths is checked against the recorded scoring events and the run fails on any mismatch.
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
PHASES = [(1, 5), (6, 10), (11, 20), (21, 30), (31, 45), (46, 200)]
LIVE_TURNS = 2
DIRS = ((0, -1), (1, 0), (0, 1), (-1, 0))


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
    me = next((u for u in lb['users'] if u.get('agentId') == args.agent), None)
    if me:
        print(f"agent {args.agent}: {me.get('pseudo')} rank {me.get('rank')} score {me.get('score'):.2f} "
              f"{me.get('programmingLanguage')} league {me.get('league', {}).get('divisionIndex')}")
    battles = post('gamesPlayersRanking/findLastBattlesByAgentId', [args.agent, None])
    json.dump(battles, open(os.path.join(snap, f'agent-{args.agent}-battles-{stamp}.json'), 'w', encoding='utf-8'))
    out = os.path.join(ROOT, args.out)
    os.makedirs(out, exist_ok=True)
    saved = present = failed = 0
    for i, b in enumerate(battles):
        if not b.get('done'):
            continue
        path = os.path.join(out, f"{b['gameId']}.json")
        if os.path.exists(path):
            present += 1
            continue
        try:
            game = post('gameResult/findByGameId', [b['gameId'], None])
        except Exception as e:  # noqa: BLE001 - record and continue, never retry blindly
            failed += 1
            print(f"  failed {b['gameId']}: {e}", flush=True)
            time.sleep(3.0)
            continue
        names = {}
        for a in game.get('agents', []):
            cg = a.get('codingamer') or {}
            if 'pseudo' in cg:
                names[str(a['index'])] = cg['pseudo']
            elif a.get('arenaboss'):
                names[str(a['index'])] = a['arenaboss'].get('nickname', 'Boss')
        game['btkNicknames'] = names
        game['btkSource'] = {'agent': args.agent, 'battle': b, 'fetchedAt': stamp}
        json.dump(game, open(path, 'w', encoding='utf-8'))
        saved += 1
        if saved % 20 == 0:
            print(f'  {saved} saved ({i + 1}/{len(battles)} battles seen)', flush=True)
        time.sleep(0.4)
    print(f'fetch: {saved} saved, {present} already present, {failed} failed -> {out}')


# ---------------------------------------------------------------- replay parsing

def parse_map(view):
    g = json.loads(view.split('\n', 1)[1])['global']['graphics'].split('|')
    w, h = int(g[0]), int(g[1])
    cells = g[3:3 + w * h]
    region = [int(c.split()[0]) for c in cells]
    terrain = [int(c.split()[1]) for c in cells]
    tc = int(g[3 + w * h])
    towns = []
    for t in g[4 + w * h:4 + w * h + tc]:
        parts = t.split()
        tid, x, y = int(parts[0]), int(parts[1]), int(parts[2])
        desired = [] if len(parts) < 4 or parts[3] == 'x' else [int(v) for v in parts[3].split(',')]
        towns.append((tid, x, y, desired))
    return w, h, region, terrain, towns


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


def commands(stdout):
    return [c.strip() for c in (stdout or '').strip().split(';') if c.strip()]


class Side:
    def __init__(self):
        self.phase = collections.defaultdict(collections.Counter)
        self.game = collections.Counter()
        self.turns = []  # per-turn dicts for time series


def neighbours(c, w, h):
    x, y = c % w, c // w
    for dx, dy in DIRS:
        nx, ny = x + dx, y + dy
        if 0 <= nx < w and 0 <= ny < h:
            yield ny * w + nx


def analyze(path, player):
    g = json.load(open(path, encoding='utf-8'))
    fr = g['frames']
    w, h, region, terrain, towns = parse_map(fr[0]['view'])
    names = g.get('btkNicknames') or {}
    me = next((int(k) for k, v in names.items() if v == player), None)
    if me is None:
        for a in g.get('agents', []):
            if (a.get('codingamer') or {}).get('pseudo') == player:
                me = a['index']
    m = re.search(r'"endScreen":\[\[(-?\d+),(-?\d+)\]', fr[-1]['view'])
    if me is None or not m:
        return None
    final = (int(m.group(1)), int(m.group(2)))
    opp = 1 - me
    nreg = max(region) + 1
    town_regions = {region[y * w + x] for _, x, y, _ in towns}
    town_cells = {y * w + x: tid for tid, x, y, _ in towns}
    town_pos = {tid: y * w + x for tid, x, y, _ in towns}
    desired_pairs = {(a, b) for a, _, _, ds in towns for b in ds}
    occ, paths, placed_turn = {}, {}, {}
    instab = [0] * nreg
    inked = [False] * nreg
    last_place = {0: [-99] * nreg, 1: [-99] * nreg}
    sides = {0: Side(), 1: Side()}
    stdout = {0: '', 1: ''}
    summary_fail = None
    turn = 0
    score = [0, 0]
    for f in fr[1:]:
        aid = f.get('agentId', -1)
        if aid in (0, 1):
            stdout[aid] = f.get('stdout', '') or ''
        if not f.get('keyframe'):
            continue
        if f.get('summary'):
            summary_fail = f['summary']
        ev = parse_events(f['view'])
        turn += 1
        ph = phase(turn)
        active_cells_by_owner = collections.Counter()
        active_cells = set()
        for cells in paths.values():
            active_cells.update(cells)
        # commands issued this turn
        for p in (0, 1):
            cmds = commands(stdout[p])
            s = sides[p]
            s.phase[ph]['turns'] += 1
            s.game['turns'] += 1
            kinds = collections.Counter(c.split()[0] for c in cmds)
            n_place = kinds['PLACE_TRACKS']
            n_auto = kinds['AUTOPLACE']
            n_disr = kinds['DISRUPT']
            s.game['cmd_place'] += n_place
            s.game['cmd_autoplace'] += n_auto
            s.game['cmd_disrupt'] += n_disr
            s.game['cmd_wait'] += kinds['WAIT']
            s.game['cmd_message'] += kinds['MESSAGE']
            if n_place or n_auto:
                s.phase[ph]['build_turns'] += 1
            if n_disr:
                s.phase[ph]['disrupt_cmd_turns'] += 1
            if n_disr > 1:
                s.game['multi_disrupt_turns'] += 1
            # distinct regions targeted by explicit PLACE_TRACKS this turn
            regs = set()
            for c in cmds:
                t = c.split()
                if t[0] == 'PLACE_TRACKS' and len(t) >= 3:
                    try:
                        cc = int(t[2]) * w + int(t[1])
                        if 0 <= cc < w * h:
                            regs.add(region[cc])
                    except ValueError:
                        pass
            if n_place:
                s.game['place_turns'] += 1
                s.game['place_regions'] += len(regs)
                if len(regs) >= 2:
                    s.game['place_multi_region_turns'] += 1
        # placements (type 1)
        placed_now = {0: [], 1: []}
        for t, d in ev:
            if t == 1:
                p, x, y = int(d[0]), int(d[1]), int(d[2])
                c = y * w + x
                occ[c] = 2 if c in occ and occ[c] != p else p
                placed_turn[c] = turn
                if p in (0, 1):
                    placed_now[p].append(c)
                    last_place[p][region[c]] = turn
                    s = sides[p]
                    s.phase[ph]['paint'] += terrain[c] + 1
                    s.phase[ph]['cells'] += 1
                    s.game['cells'] += 1
                    s.game['paint'] += terrain[c] + 1
                    s.game['terrain_' + str(terrain[c])] += 1
                    if region[c] in town_regions:
                        s.phase[ph]['protected_cells'] += 1
                        s.game['protected_cells'] += 1
                    if instab[region[c]] >= 2:
                        s.game['cells_at_instab2plus'] += 1
                    if instab[region[c]] >= 1:
                        s.game['cells_at_instab1plus'] += 1
                    adj_town = any(n in town_cells for n in neighbours(c, w, h))
                    adj_own = any(occ.get(n) == p for n in neighbours(c, w, h) if n != c and placed_turn.get(n, 0) < turn)
                    adj_opp = any(occ.get(n) == 1 - p for n in neighbours(c, w, h))
                    if adj_town:
                        s.game['cells_adj_town'] += 1
                    if adj_own:
                        s.game['cells_adj_own'] += 1
                    if adj_opp:
                        s.game['cells_adj_opp'] += 1
                    if c in active_cells:
                        s.game['cells_on_active_path'] += 1
                    # opponent has tracks in this region already
                    if any(region[cc] == region[c] and o == 1 - p for cc, o in occ.items()):
                        s.game['cells_in_shared_region'] += 1
        for p in (0, 1):
            if placed_now[p]:
                regs = {region[c] for c in placed_now[p]}
                sides[p].game['accepted_place_turns'] += 1
                sides[p].game['accepted_regions'] += len(regs)
                if len(regs) >= 2:
                    sides[p].game['accepted_multi_region_turns'] += 1
        # disrupts (type 2), before inking
        inked_now = {int(d[0]) for t, d in ev if t == 5}
        for t, d in ev:
            if t == 2:
                p, r = int(d[0]), int(d[1])
                s = sides[p]
                s.phase[ph]['disrupts'] += 1
                s.game['hits'] += 1
                own_cells = [c for c, o in occ.items() if region[c] == r and o == p]
                opp_cells = [c for c, o in occ.items() if region[c] == r and o == 1 - p]
                neutral_cells = [c for c, o in occ.items() if region[c] == r and o == 2]
                if not opp_cells and not own_cells and not neutral_cells:
                    cls = 'empty'
                elif not opp_cells:
                    cls = 'own_only'
                elif any(c in active_cells for c in opp_cells):
                    cls = 'active'
                else:
                    cls = 'construction'
                s.game['hit_' + cls] += 1
                s.phase[ph]['hit_' + cls] += 1
                if own_cells:
                    s.game['hit_with_own_tracks'] += 1
                    s.game['hit_own_cells_at_risk'] += len(own_cells)
                if last_place[1 - p][r] >= turn - LIVE_TURNS:
                    s.game['hit_live'] += 1
                    if cls == 'construction':
                        s.game['hit_construction_live'] += 1
                if last_place[1 - p][r] == turn:
                    s.game['hit_same_turn_placement'] += 1
                s.game['hit_at_instab_' + str(min(instab[r], 3))] += 1
                if r in inked_now:
                    s.game['hit_finishing'] += 1
                if turn == 1:
                    s.game['first_turn_disrupt'] += 1
                # same target as previous turn?
                prev = s.game.get('_last_target')
                if prev == r:
                    s.game['hit_repeat_prev'] += 1
                s.game['_last_target'] = r
                # switch between targets while a partially disrupted region exists
                instab[r] += 1
        for r in inked_now:
            inked[r] = True
            for c in [c for c in occ if region[c] == r]:
                o = occ[c]
                if o in (0, 1):
                    sides[o].game['tracks_lost'] += 1
                    sides[o].phase[ph]['tracks_lost'] += 1
                    if c in active_cells:
                        sides[o].game['active_tracks_lost'] += 1
                    if placed_turn.get(c, 0) >= turn - LIVE_TURNS:
                        sides[o].game['fresh_tracks_lost'] += 1
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
        scored = [0, 0]
        for t, d in ev:
            if t == 3:
                scored[int(d[0])] += int(d[1])
        calc = [0, 0]
        conns = [0, 0]
        excl = [0, 0]
        stolen = [0, 0]
        for cells in paths.values():
            own = [0, 0]
            for c in cells:
                if c in town_cells:
                    continue
                o = occ.get(c, -1)
                if o in (0, 1):
                    own[o] += 1
            for p in (0, 1):
                calc[p] += own[p]
                if own[p] == 0:
                    continue
                if own[p] > own[1 - p]:
                    conns[p] += 1
                    sides[p].phase[ph]['income_own'] += own[p]
                elif own[p] < own[1 - p]:
                    stolen[p] += own[p]
                    sides[p].phase[ph]['income_stolen'] += own[p]
                if own[1 - p] == 0:
                    excl[p] += 1
        if calc != scored:
            raise ValueError(f'{os.path.basename(path)} turn {turn}: reconstructed income {calc} != scored {scored}')
        for p in (0, 1):
            score[p] += scored[p]
            sides[p].phase[ph]['income'] += scored[p]
            sides[p].phase[ph]['own_conns'] += conns[p]
            sides[p].phase[ph]['exclusive_conns'] += excl[p]
            active_own = sum(1 for c in active_cells if occ.get(c) == p)
            stock = sum(1 for c, o in occ.items() if o == p)
            sides[p].turns.append({'t': turn, 'income': scored[p], 'score': score[p], 'conns': conns[p],
                                   'stock': stock, 'active_stock': active_own,
                                   'placed': len(placed_now[p])})
            if turn == 1 and scored[p] > 0:
                sides[p].game['scores_turn1'] += 1
            if scored[p] > 0 and 'first_income_turn' not in sides[p].game:
                sides[p].game['first_income_turn'] = turn
    won = final[me] > final[opp]
    for p in (0, 1):
        s = sides[p]
        s.game.pop('_last_target', None)
        # dead cells: placed but never on any active path at the end (approximation: never in active cells at any time is expensive; use final stock)
        s.game['final_stock'] = sum(1 for c, o in occ.items() if o == p)
        s.game['final_active_stock'] = sum(1 for c in set().union(*paths.values()) if occ.get(c) == p) if paths else 0
    return {'game': os.path.basename(path), 'turns': turn, 'won': won, 'final': final, 'me': me,
            'opponent': names.get(str(opp), '?'), 'player': sides[me], 'opp': sides[opp],
            'failure': summary_fail, 'width': w, 'height': h, 'towns': len(towns),
            'desiredPairs': len(desired_pairs), 'townRegions': len(town_regions)}


# ---------------------------------------------------------------- aggregation

def ratio(a, b, nd=4):
    return round(a / b, nd) if b else None


def aggregate(results, who):
    games = results
    out = {'games': len(games)}
    if not games:
        return out
    out['lengthMean'] = round(statistics.mean(r['turns'] for r in games), 2)
    out['lengthMedian'] = statistics.median(r['turns'] for r in games)
    byph = {}
    for i, (a, b) in enumerate(PHASES):
        agg = collections.Counter()
        for r in games:
            agg.update(r[who].phase[i])
        t = agg['turns']
        if not t:
            continue
        byph[f'{a}-{b}'] = {
            'turns': t,
            'incomePerTurn': ratio(agg['income'], t, 2),
            'incomeOwnMajorityPerTurn': ratio(agg['income_own'], t, 2),
            'incomeStolenPerTurn': ratio(agg['income_stolen'], t, 2),
            'ownMajorityConnsPerTurn': ratio(agg['own_conns'], t, 3),
            'exclusiveConnsPerTurn': ratio(agg['exclusive_conns'], t, 3),
            'buildTurnShare': ratio(agg['build_turns'], t),
            'paintPerTurn': ratio(agg['paint'], t, 3),
            'cellsPerTurn': ratio(agg['cells'], t, 3),
            'disruptTurnShare': ratio(agg['disrupts'], t),
            'protectedCellShare': ratio(agg['protected_cells'], agg['cells']),
            'tracksLostPerTurn': ratio(agg['tracks_lost'], t, 3),
            'hitClass': {c: ratio(agg['hit_' + c], agg['disrupts']) for c in ('empty', 'own_only', 'construction', 'active')},
        }
    out['phases'] = byph
    g = collections.Counter()
    for r in games:
        g.update({k: v for k, v in r[who].game.items() if k != 'first_income_turn'})
    hits = g['hits']
    cells = g['cells']
    n = len(games)
    fits = [r[who].game['first_income_turn'] for r in games if 'first_income_turn' in r[who].game]
    out['game'] = {
        'cellsPerGame': ratio(cells, n, 2),
        'paintPerGame': ratio(g['paint'], n, 2),
        'paintPerCell': ratio(g['paint'], cells, 3),
        'terrainShare': {k: ratio(g['terrain_' + k], cells) for k in ('0', '1', '2')},
        'protectedCellShare': ratio(g['protected_cells'], cells),
        'cellsAdjTownShare': ratio(g['cells_adj_town'], cells),
        'cellsAdjOwnShare': ratio(g['cells_adj_own'], cells),
        'cellsAdjOppShare': ratio(g['cells_adj_opp'], cells),
        'cellsInSharedRegionShare': ratio(g['cells_in_shared_region'], cells),
        'cellsAtInstab1plusShare': ratio(g['cells_at_instab1plus'], cells),
        'cellsAtInstab2plusShare': ratio(g['cells_at_instab2plus'], cells),
        'tracksLostPerGame': ratio(g['tracks_lost'], n, 2),
        'tracksLostShareOfPlaced': ratio(g['tracks_lost'], cells),
        'activeTracksLostShare': ratio(g['active_tracks_lost'], g['tracks_lost']),
        'freshTracksLostShare': ratio(g['fresh_tracks_lost'], g['tracks_lost']),
        'finalStockPerGame': ratio(g['final_stock'], n, 2),
        'finalActiveStockShare': ratio(g['final_active_stock'], g['final_stock']),
        'firstIncomeTurnMean': round(statistics.mean(fits), 2) if fits else None,
        'scoresTurn1Share': ratio(g['scores_turn1'], n),
        'commands': {k: ratio(g['cmd_' + k], g['turns'], 3) for k in ('place', 'autoplace', 'disrupt', 'wait', 'message')},
        'multiDisruptTurnShare': ratio(g['multi_disrupt_turns'], g['turns']),
        'placeRegionsPerPlaceTurn': ratio(g['place_regions'], g['place_turns'], 3),
        'placeMultiRegionTurnShare': ratio(g['place_multi_region_turns'], g['place_turns']),
        'acceptedRegionsPerPlaceTurn': ratio(g['accepted_regions'], g['accepted_place_turns'], 3),
        'acceptedMultiRegionTurnShare': ratio(g['accepted_multi_region_turns'], g['accepted_place_turns']),
        'hitsPerGame': ratio(hits, n, 2),
        'hitsPerTurn': ratio(hits, g['turns']),
        'hitClassShare': {c: ratio(g['hit_' + c], hits) for c in ('empty', 'own_only', 'construction', 'active')},
        'hitLiveShare': ratio(g['hit_live'], hits),
        'hitSameTurnPlacementShare': ratio(g['hit_same_turn_placement'], hits),
        'constructionHitLiveShare': ratio(g['hit_construction_live'], g['hit_construction']),
        'hitWithOwnTracksShare': ratio(g['hit_with_own_tracks'], hits),
        'hitOwnCellsAtRiskPerHit': ratio(g['hit_own_cells_at_risk'], hits, 3),
        'hitAtInstab': {k: ratio(g['hit_at_instab_' + k], hits) for k in ('0', '1', '2', '3')},
        'hitFinishingShare': ratio(g['hit_finishing'], hits),
        'hitRepeatPrevShare': ratio(g['hit_repeat_prev'], hits),
        'firstTurnDisruptShare': ratio(g['first_turn_disrupt'], n),
    }
    return out


def series(results, who, key, upto=60):
    """Mean of a per-turn value across games, for turns 1..upto (only games that reached the turn)."""
    acc = collections.defaultdict(list)
    for r in results:
        for tr in r[who].turns:
            if tr['t'] <= upto:
                acc[tr['t']].append(tr[key])
    return [round(statistics.mean(acc[t]), 2) if acc.get(t) else None for t in range(1, upto + 1)]


def print_block(title, agg):
    print(f'=== {title}: n={agg.get("games")} length mean/median={agg.get("lengthMean")}/{agg.get("lengthMedian")}')
    if not agg.get('games'):
        return
    for ph, v in agg['phases'].items():
        print(f"  {ph:>6}: inc={v['incomePerTurn']:6.1f} own={v['incomeOwnMajorityPerTurn']:6.1f} conns={v['ownMajorityConnsPerTurn']:.2f} "
              f"excl={v['exclusiveConnsPerTurn']:.2f} build={100 * v['buildTurnShare']:.0f}% paint={v['paintPerTurn']:.2f} "
              f"disr={100 * v['disruptTurnShare']:.0f}% prot={100 * (v['protectedCellShare'] or 0):.0f}% "
              f"hits e/o/c/a={'/'.join(f'{100 * (v['hitClass'][c] or 0):.0f}' for c in ('empty', 'own_only', 'construction', 'active'))}")
    gm = agg['game']
    print(f"  cells/game={gm['cellsPerGame']} paint/cell={gm['paintPerCell']} terrain={gm['terrainShare']} protected={gm['protectedCellShare']}")
    print(f"  adjTown={gm['cellsAdjTownShare']} adjOwn={gm['cellsAdjOwnShare']} adjOpp={gm['cellsAdjOppShare']} shared={gm['cellsInSharedRegionShare']} "
          f"instab1+={gm['cellsAtInstab1plusShare']} instab2+={gm['cellsAtInstab2plusShare']}")
    print(f"  lost/game={gm['tracksLostPerGame']} lostShare={gm['tracksLostShareOfPlaced']} activeLost={gm['activeTracksLostShare']} "
          f"freshLost={gm['freshTracksLostShare']} finalStock={gm['finalStockPerGame']} finalActive={gm['finalActiveStockShare']}")
    print(f"  firstIncomeTurn={gm['firstIncomeTurnMean']} scoresT1={gm['scoresTurn1Share']} commands/turn={gm['commands']} multiDisrupt={gm['multiDisruptTurnShare']}")
    print(f"  placeRegions/turn={gm['placeRegionsPerPlaceTurn']} multiRegion={gm['placeMultiRegionTurnShare']} accepted={gm['acceptedRegionsPerPlaceTurn']}/{gm['acceptedMultiRegionTurnShare']}")
    print(f"  hits/game={gm['hitsPerGame']} hits/turn={gm['hitsPerTurn']} class={gm['hitClassShare']} live={gm['hitLiveShare']} sameTurn={gm['hitSameTurnPlacementShare']} "
          f"constrLive={gm['constructionHitLiveShare']}")
    print(f"  hitWithOwn={gm['hitWithOwnTracksShare']} ownAtRisk/hit={gm['hitOwnCellsAtRiskPerHit']} atInstab={gm['hitAtInstab']} finishing={gm['hitFinishingShare']} "
          f"repeatPrev={gm['hitRepeatPrevShare']} firstTurn={gm['firstTurnDisruptShare']}")


def study(args):
    files = sorted(glob.glob(os.path.join(ROOT, args.corpus, '*.json')))
    results = []
    skipped = 0
    for f in files:
        r = analyze(f, args.player)
        if r is None:
            skipped += 1
        else:
            results.append(r)
    report = {'schemaVersion': 1, 'player': args.player, 'corpus': args.corpus, 'files': len(files),
              'analyzed': len(results), 'skipped': skipped, 'liveTurns': LIVE_TURNS, 'phases': PHASES}
    wins = [r for r in results if r['won']]
    losses = [r for r in results if not r['won']]
    report['record'] = {'wins': len(wins), 'losses': len(losses),
                        'failures': sum(1 for r in results if r['failure']),
                        'asP0': sum(1 for r in results if r['me'] == 0),
                        'winsAsP0': sum(1 for r in wins if r['me'] == 0)}
    opp_rec = collections.defaultdict(lambda: [0, 0])
    for r in results:
        opp_rec[r['opponent']][0 if r['won'] else 1] += 1
    report['byOpponent'] = {k: {'wins': v[0], 'losses': v[1]} for k, v in sorted(opp_rec.items(), key=lambda kv: -sum(kv[1]))}
    report['maps'] = {'heightHist': dict(collections.Counter(r['height'] for r in results)),
                      'townsHist': dict(collections.Counter(r['towns'] for r in results))}
    report['player'] = {'all': aggregate(results, 'player'), 'wins': aggregate(wins, 'player'), 'losses': aggregate(losses, 'player')}
    report['opponents'] = {'all': aggregate(results, 'opp'), 'inPlayerWins': aggregate(wins, 'opp'), 'inPlayerLosses': aggregate(losses, 'opp')}
    report['series'] = {
        'playerIncome': series(results, 'player', 'income'),
        'oppIncome': series(results, 'opp', 'income'),
        'playerScore': series(results, 'player', 'score'),
        'oppScore': series(results, 'opp', 'score'),
        'playerConns': series(results, 'player', 'conns'),
        'oppConns': series(results, 'opp', 'conns'),
        'playerStock': series(results, 'player', 'stock'),
        'oppStock': series(results, 'opp', 'stock'),
        'playerActiveStock': series(results, 'player', 'active_stock'),
        'oppActiveStock': series(results, 'opp', 'active_stock'),
    }
    # per-opponent aggregates of the player (how does Indrill adapt?)
    report['playerByOpponent'] = {k: aggregate([r for r in results if r['opponent'] == k], 'player')
                                  for k, v in opp_rec.items() if sum(v) >= 10}
    print(f"{args.player}: {len(results)} games analyzed ({skipped} skipped), record {report['record']}")
    print('by opponent:', report['byOpponent'])
    print('maps:', report['maps'])
    print_block(f'{args.player} all', report['player']['all'])
    print_block(f'{args.player} losses', report['player']['losses'])
    print_block('opponents all', report['opponents']['all'])
    print('series (turn 1..30):')
    for k in ('playerIncome', 'oppIncome', 'playerConns', 'oppConns', 'playerStock', 'oppStock', 'playerActiveStock', 'oppActiveStock'):
        print(f'  {k:>18}: ' + ' '.join(f'{v:5.1f}' if v is not None else '    -' for v in report['series'][k][:30]))
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
    f.add_argument('--agent', type=int, default=6752275)
    f.add_argument('--out', default='replays/cg-indrill')
    s = sub.add_parser('study')
    s.add_argument('--corpus', default='replays/cg-indrill')
    s.add_argument('--player', default='Indrill')
    s.add_argument('--out', default='')
    s.add_argument('--force', action='store_true')
    args = ap.parse_args()
    (fetch if args.cmd == 'fetch' else study)(args)


if __name__ == '__main__':
    main()
