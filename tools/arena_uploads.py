"""File, measure and compare the CodinGame arena games of every upload of a version.

Every Submit in the CG IDE is a new agent that plays its placement games (~120) in the puzzle arena. `fetch` files
the live entry's games under replays/arena/<label>/upload-N/ (gitignored):

  upload.json      identity (agent, first-turn marker: display name, build, config) and rating snapshots
  battles.json     one row per finished game: result, opponent (user, agent, arena score), seat, map size
  games/<id>.json  the CG replay, same format as replays/cg (gradlew cgAnalyze -Ppath=<dir>/games -Pagent=<id>)

`compare` prints every upload and every version: CG score and rank at the end of placement, win rate, an
opponent-adjusted performance rating, and stratified head-to-head deltas on the opponents two uploads or two
versions share — within one league, since CG scores are not comparable across leagues. The promotion rule that
reads these numbers is docs/arena-uploads.md.

  python tools/arena_uploads.py watch [--version LABEL] [--interval 15]   # fetch every 15 min until placement ends
  python tools/arena_uploads.py fetch [--version LABEL] [--agent ID] [--no-replays]
  python tools/arena_uploads.py import --version v12 --agent 6814073 [--score 29.40 --rank 316 --complete]
  python tools/arena_uploads.py compare [LABEL ...] [--out docs/evidence/arena-uploads-<date>.json]
  python tools/arena_uploads.py relabel <candidate> vNN      # at a promotion (same configuration)

CG lists the games of the live agent only, and a league promotion at the end of placement replaces the agent (the
promoted one replays its placement in the new league): `watch` keeps the games of a placement that ends in a promotion.
API cost of a fetch: one leaderboard request, one last-battles request (results of every game of the upload) and
one request per replay not already on disk (replays/cg is searched first). `import` is offline.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import re
import statistics
import sys
import time
import urllib.error
import urllib.request

BASE = 'https://www.codingame.com/services/'
PUZZLE = 'summer-challenge-2026-back-track-king'
PLAYER = 'aabzac'
MARKER = re.compile(r'BTK_BUILD_V1 strategy=([a-z][a-z0-9_-]{0,39}) build=([0-9a-f]{64}) config=([0-9a-f]{64})')
DEFAULT_SCALE = 2.0  # logistic scale (CG score points) when the joint fit is not identifiable
LEAGUES = {0: 'Wood2', 1: 'Wood1', 2: 'Bronze', 3: 'Silver', 4: 'Gold', 5: 'Legend'}  # leaderboard divisionIndex


class BudgetExhausted(Exception):
    pass


class Api:
    """CodinGame's public services; every call counts against --max-requests."""

    def __init__(self, max_requests, pause=0.5, opener=urllib.request.urlopen):
        self.left, self.used, self.pause, self.opener = max_requests, 0, pause, opener

    def post(self, service, body):
        if self.left <= 0:
            raise BudgetExhausted(service)
        self.left -= 1
        self.used += 1
        request = urllib.request.Request(BASE + service, data=json.dumps(body).encode('utf-8'), method='POST',
                                         headers={'Content-Type': 'application/json',
                                                  'User-Agent': 'Mozilla/5.0 (btk arena_uploads)'})
        with self.opener(request, timeout=60) as response:
            value = json.loads(response.read().decode('utf-8'))
        if self.pause:
            time.sleep(self.pause)
        return value

    def leaderboard(self):
        return self.post('Leaderboards/getFilteredPuzzleLeaderboard',
                         [PUZZLE, None, 'global', {'active': False, 'column': '', 'filter': ''}])

    def battles(self, agent_id):
        return self.post('gamesPlayersRanking/findLastBattlesByAgentId', [agent_id, None])

    def game(self, game_id):
        return self.post('gameResult/findByGameId', [game_id, None])


def now():
    return datetime.now(timezone.utc).isoformat(timespec='seconds')


def read_json(path, default=None):
    path = Path(path)
    return json.loads(path.read_text(encoding='utf-8')) if path.exists() else default


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + '.tmp')
    tmp.write_text(json.dumps(value, indent=1, ensure_ascii=False) + '\n', encoding='utf-8')
    tmp.replace(path)


# ---- replays ----------------------------------------------------------------------------------------------------

def parse_message(stdout):
    """The first-turn MESSAGE marker: '[<display> | ]BTK_BUILD_V1 strategy=.. build=.. config=..'."""
    for command in re.split(r'[;\n]', stdout or ''):
        command = command.strip()
        if not command.startswith('MESSAGE '):
            continue
        text = command[len('MESSAGE '):]
        start = text.find('BTK_BUILD_V1 ')
        match = MARKER.match(text, start) if start >= 0 else None
        if not match:
            continue
        prefix = text[:start]
        display = prefix[:-3].strip() if prefix.endswith(' | ') else None
        return {'display': display, 'version': display.split()[0] if display else None,
                'modified': bool(display) and display.endswith('(modified)'),
                'strategy': match.group(1), 'build': match.group(2), 'config': match.group(3)}
    return None


def replay_seat(game, agent_id):
    """(our in-game index, the opponent's agents[] entry) or (None, None)."""
    agents = game.get('agents') or []
    ours = [a for a in agents if a.get('agentId') == agent_id]
    if len(ours) != 1:
        return None, None
    seat = ours[0].get('index')
    return seat, next((a for a in agents if a.get('index') != seat), None)


def replay_marker(game, agent_id):
    seat, _ = replay_seat(game, agent_id)
    for frame in game.get('frames') or []:
        if frame.get('agentId') == seat and 'BTK_BUILD_V1' in (frame.get('stdout') or ''):
            marker = parse_message(frame['stdout'])
            if marker:
                return marker
    return None


def map_size(game):
    try:
        view = game['frames'][0]['view']
        width, height = json.loads(view[view.index('{'):])['global']['graphics'].split('|')[:2]
        return int(width), int(height)
    except (KeyError, IndexError, ValueError, TypeError, AttributeError):
        return None


def replay_result(game, agent_id):
    """win/loss/draw from gameResult ranks (indexed by in-game index, 0 = first)."""
    seat, opponent = replay_seat(game, agent_id)
    ranks = game.get('ranks')
    if seat is None or opponent is None or not isinstance(ranks, list) or len(ranks) < 2:
        return None
    mine, theirs = ranks[seat], ranks[opponent['index']]
    return 'win' if mine < theirs else 'loss' if mine > theirs else 'draw'


def nicknames(game, battle):
    """{in-game index: pseudo}, as CgFetch stores it in btkNicknames."""
    names = {}
    players = (battle or {}).get('players') or []
    for agent in game.get('agents') or []:
        codingamer = agent.get('codingamer') or {}
        if 'index' not in agent:
            continue
        if codingamer.get('pseudo'):
            names[str(agent['index'])] = codingamer['pseudo']
            continue
        for player in players:
            if player.get('userId') is not None and player.get('userId') == codingamer.get('userId') and player.get('nickname'):
                names[str(agent['index'])] = player['nickname']
    return names


# ---- rows -------------------------------------------------------------------------------------------------------

def battle_row(battle, agent_id):
    """A finished last-battles entry -> row; positions are the finishing order (equal = draw)."""
    players = battle.get('players') or []
    ours = [p for p in players if p.get('playerAgentId') == agent_id]
    others = [p for p in players if p.get('playerAgentId') != agent_id]
    if not battle.get('done') or len(ours) != 1 or len(others) != 1:
        return None
    mine, theirs = ours[0].get('position'), others[0].get('position')
    if mine is None or theirs is None:
        return None
    opponent = others[0]
    return {'gameId': battle['gameId'], 'result': 'win' if mine < theirs else 'loss' if mine > theirs else 'draw',
            'opponent': {'userId': opponent.get('userId'), 'pseudo': opponent.get('nickname'),
                         'agentId': opponent.get('playerAgentId')}}


def enrich(row, game, agent_id):
    """Seat, map size and (when no leaderboard score was recorded) the opponent's score from the replay."""
    seat, opponent = replay_seat(game, agent_id)
    if seat is None:
        return row
    row['seat'] = seat
    size = map_size(game)
    if size:
        row['width'], row['height'] = size
    if opponent is not None:
        info = row.setdefault('opponent', {})
        codingamer = opponent.get('codingamer') or {}
        info.setdefault('userId', codingamer.get('userId'))
        info.setdefault('pseudo', codingamer.get('pseudo'))
        info.setdefault('agentId', opponent.get('agentId'))
        if info.get('score') is None and isinstance(opponent.get('score'), (int, float)):
            info['score'], info['scoreSource'] = round(opponent['score'], 2), 'replay'
    if 'result' not in row:
        row['result'] = replay_result(game, agent_id)
    return row


def leaderboard_scores(board):
    return {u['agentId']: (u.get('score'), u.get('rank')) for u in board.get('users') or [] if 'agentId' in u}


# ---- upload directories -----------------------------------------------------------------------------------------

def uploads(root):
    """Every filed upload: (directory, upload.json), ordered by label then upload number."""
    found = []
    for meta_path in Path(root).glob('*/upload-*/upload.json'):
        found.append((meta_path.parent, read_json(meta_path)))
    return sorted(found, key=lambda item: (item[1].get('label', ''), item[1].get('upload', 0)))


def resolve_label(requested, marker, force=False):
    """The version label: a clean marker names it; an experiment ('(modified)') or a marker-less build needs --version."""
    if marker and marker.get('version') and not marker.get('modified'):
        if requested and requested != marker['version'] and not force:
            raise SystemExit(f"the replays' first-turn marker says {marker['display']!r}; refusing to file them as "
                             f"{requested!r} (--force-label overrides)")
        return requested or marker['version']
    if not requested:
        shown = marker.get('display') if marker else None
        raise SystemExit(f'the first-turn marker carries no clean version id ({shown!r}); name the upload with --version')
    return requested


def upload_dir(root, label, agent_id, marker, force=False):
    """The directory of this agent's upload: the existing one, or upload-(N+1) of its label."""
    for directory, meta in uploads(root):
        if meta.get('agentId') == agent_id:
            if meta.get('label') != label:
                raise SystemExit(f"agent {agent_id} is already filed as {meta.get('label')}/{directory.name}")
            return directory, meta
    siblings = [meta for _, meta in uploads(root) if meta.get('label') == label]
    configs = {(meta.get('marker') or {}).get('config') for meta in siblings} - {None}
    config = (marker or {}).get('config')
    if config and configs and config not in configs and not force:
        raise SystemExit(f'{label} already holds configuration {sorted(configs)[0][:8]}..., this agent runs '
                         f'{config[:8]}...: a different strategy needs its own label (--force-label overrides)')
    number = 1 + max((meta.get('upload', 0) for meta in siblings), default=0)
    meta = {'schemaVersion': 1, 'label': label, 'upload': number, 'agentId': agent_id, 'marker': marker,
            'snapshots': []}
    return Path(root) / label / f'upload-{number}', meta


def load_rows(directory):
    return {int(k): v for k, v in (read_json(Path(directory) / 'battles.json', {}).get('games') or {}).items()}


def save_rows(directory, rows):
    write_json(Path(directory) / 'battles.json',
               {'schemaVersion': 1, 'games': {str(k): rows[k] for k in sorted(rows)}})


def cached_game(game_id, caches):
    for cache in caches:
        path = Path(cache) / f'{game_id}.json'
        if path.exists():
            return path
    return None


# ---- fetch / import ---------------------------------------------------------------------------------------------

def fetch(args, api=None):
    api = api or Api(args.max_requests, pause=args.pause)
    root, caches = Path(args.root), [Path(c) for c in args.cache]
    board = api.leaderboard()
    entry = next((u for u in board.get('users') or [] if (u.get('pseudo') or '').lower() == args.player.lower()), None)
    agent_id = args.agent or (entry or {}).get('agentId')
    if not agent_id:
        raise SystemExit(f'{args.player} is not on the puzzle leaderboard; pass --agent')
    live = entry is not None and entry.get('agentId') == agent_id
    battles = [b for b in api.battles(agent_id) if b.get('done')]
    print(f"agent {agent_id}{' (live entry)' if live else ''}: {len(battles)} finished games in the last-battles window")
    existing = next(((d, m) for d, m in uploads(root) if m.get('agentId') == agent_id), None)
    if not battles and existing is None:
        raise SystemExit('  nothing filed: no finished games' + ('' if live else ' (CG lists the games of the live entry '
                                                                               'only: fetch an upload before it is replaced)'))
    games, marker = {}, (existing[1].get('marker') if existing else None)
    if existing is None:
        for battle in battles:
            game = load_game(battle, caches, api, None)
            games[battle['gameId']] = game
            marker = replay_marker(game, agent_id)
            if marker:
                break
    label = existing[1]['label'] if existing else resolve_label(args.version, marker, args.force_label)
    directory, meta = existing or upload_dir(root, label, agent_id, marker, args.force_label)
    if args.version and args.version != label:
        raise SystemExit(f'agent {agent_id} is filed as {label}, not {args.version}')
    meta.setdefault('player', args.player)
    if live and existing is None:
        promoted = promoted_from(root, label, (entry.get('league') or {}).get('divisionIndex'))
        if promoted:
            meta['promotedFrom'] = promoted
            print(f'  league promotion: this agent replays the placement of {label} agent {promoted} in '
                  f"{LEAGUES.get((entry.get('league') or {}).get('divisionIndex'), '?')}")
    if live:
        meta['userId'] = (entry.get('codingamer') or {}).get('userId')
        if entry.get('creationTime'):
            meta['uploadedAt'] = datetime.fromtimestamp(entry['creationTime'] / 1000, timezone.utc).isoformat(timespec='seconds')
    meta['source'] = meta.get('source') or 'fetch'

    rows = load_rows(directory)
    scores = leaderboard_scores(board)
    for battle in battles:
        row = battle_row(battle, agent_id)
        if row is None:
            continue
        previous = rows.get(row['gameId'], {})
        row = {**previous, **row, 'opponent': {**previous.get('opponent', {}), **row['opponent']}}
        info = row['opponent']
        if info.get('score') is None and info.get('agentId') in scores:
            info['score'], info['rankAtFetch'] = scores[info['agentId']]
            info['scoreSource'] = 'leaderboard'
        rows[row['gameId']] = row

    missing = 0
    if not args.no_replays:
        for position, battle in enumerate(battles):
            game_id = battle['gameId']
            try:
                game = games.get(game_id) or load_game(battle, caches, api, directory)
            except (BudgetExhausted, urllib.error.URLError, OSError, ValueError) as error:
                # stop at the first refusal (a rate limit must not be hammered); a rerun resumes from disk
                print(f'  replay {game_id} not fetched ({type(error).__name__}: {error}); stopping here')
                missing = len(battles) - position
                break
            store_game(directory, game_id, game)
            if game_id in rows:
                enrich(rows[game_id], game, agent_id)
    elif games:
        for game_id, game in games.items():
            store_game(directory, game_id, game)
            if game_id in rows:
                enrich(rows[game_id], game, agent_id)

    if live:
        print(f"  leaderboard: score {entry.get('score')}, rank {entry.get('rank')} of {board.get('count')}, "
              f"placement {entry.get('percentage')} %" + ('' if entry.get('percentage') == 100 else
                                                           ' (still placing: fetch again when it reaches 100 %)'))
        meta['snapshots'].append({'at': now(), 'rank': entry.get('rank'), 'score': entry.get('score'),
                                  'percentage': entry.get('percentage'), 'inProgress': entry.get('inProgress'),
                                  'division': (entry.get('league') or {}).get('divisionIndex'),
                                  'players': board.get('count'), 'games': len(rows)})
    else:
        print('  not the live entry: no rating snapshot (the leaderboard only shows the live agent)')
    write_json(directory / 'upload.json', meta)
    save_rows(directory, rows)
    print(f'{label}/{directory.name}: {len(rows)} games filed, {api.used} API requests'
          + (f', {missing} replays still missing (rerun to continue)' if missing else ''))
    return directory


def promoted_from(root, label, division):
    """The agent of `label` last seen in a lower league, when the new live agent is its promotion (not a Submit)."""
    latest = None
    for _, meta in uploads(root):
        snapshots = meta.get('snapshots') or []
        if meta.get('label') == label and snapshots and snapshots[-1].get('at'):
            if latest is None or snapshots[-1]['at'] > latest[1]['at']:
                latest = (meta, snapshots[-1])
    if latest and division is not None and latest[1].get('division') is not None and latest[1]['division'] < division:
        return latest[0].get('agentId')
    return None


def watch(args, api_factory=None, sleep=time.sleep):
    """Fetch the live entry every --interval minutes until its placement ends (a promotion replaces the agent)."""
    deadline = time.time() + args.hours * 3600
    while True:
        try:
            directory = fetch(args, api_factory() if api_factory else None)
        except (urllib.error.URLError, OSError, ValueError) as error:
            print(f'  fetch failed ({type(error).__name__}: {error}); retrying next round')
            directory = None
        last = {}
        if directory is not None:
            last = (read_json(directory / 'upload.json').get('snapshots') or [{}])[-1]
            if last.get('percentage') == 100 and not last.get('inProgress'):
                print(f'placement finished: {directory}')
                return directory
        if time.time() > deadline:
            print('deadline reached; placement not finished')
            return directory
        # near the end of placement poll every few minutes: a promotion replaces the agent right after its last game
        sleep(60 * (min(args.interval, 3) if (last.get('percentage') or 0) >= 85 else args.interval))


def load_game(battle, caches, api, directory):
    """A replay from the upload directory, a cache (replays/cg), or the API (with btkNicknames like CgFetch)."""
    game_id = battle['gameId']
    own = Path(directory) / 'games' / f'{game_id}.json' if directory else None
    path = own if own and own.exists() else cached_game(game_id, caches)
    if path:
        return json.loads(path.read_text(encoding='utf-8'))
    game = api.game(game_id)
    names = nicknames(game, battle)
    if names:
        game['btkNicknames'] = names
    return game


def store_game(directory, game_id, game):
    path = Path(directory) / 'games' / f'{game_id}.json'
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(game, separators=(',', ':'), ensure_ascii=False), encoding='utf-8')


def import_upload(args):
    """File an earlier upload from replays already on disk (no API requests)."""
    root, needle = Path(args.root), re.compile(rf'"agentId":\s*{args.agent}\b')
    found = {}
    for cache in args.cache:
        for path in sorted(Path(cache).glob('*.json')):
            text = path.read_text(encoding='utf-8', errors='replace')
            if not needle.search(text):
                continue
            game = json.loads(text)
            if replay_seat(game, args.agent)[0] is not None:
                found.setdefault(game.get('gameId') or int(path.stem), game)
    if not found:
        raise SystemExit(f'no replay of agent {args.agent} under {args.cache}')
    marker = next((m for m in (replay_marker(g, args.agent) for g in found.values()) if m), None)
    label = resolve_label(args.version, marker, args.force_label)
    directory, meta = upload_dir(root, label, args.agent, marker, args.force_label)
    meta['source'] = 'import'
    if args.note:
        meta['note'] = args.note
    if args.division is not None:
        meta['division'] = args.division
    rows = load_rows(directory)
    for game_id, game in found.items():
        store_game(directory, game_id, game)
        rows[game_id] = enrich(rows.get(game_id, {'gameId': game_id}), game, args.agent)
    if args.score is not None:
        meta['snapshots'].append({'at': args.at or now(), 'rank': args.rank, 'score': args.score,
                                  'percentage': 100 if args.complete else None, 'games': args.games or len(rows),
                                  'division': args.division, 'source': 'manual'})
    write_json(directory / 'upload.json', meta)
    save_rows(directory, rows)
    print(f'{label}/{directory.name}: {len(rows)} games of agent {args.agent} imported, marker '
          f"{(marker or {}).get('display') or 'without display name'}")
    return directory


def relabel(root, old, new):
    """At a promotion the candidate's uploads join the new version's label (same configuration required)."""
    root = Path(root)
    moving = [(d, m) for d, m in uploads(root) if m.get('label') == old]
    if not moving:
        raise SystemExit(f'no uploads labelled {old}')
    targets = [m for _, m in uploads(root) if m.get('label') == new]
    configs = {(m.get('marker') or {}).get('config') for m in targets} - {None}
    for directory, meta in moving:
        if configs and (meta.get('marker') or {}).get('config') not in configs:
            raise SystemExit(f'{old}/{directory.name} runs another configuration than {new}')
    number = max((m.get('upload', 0) for m in targets), default=0)
    moved = []
    for directory, meta in moving:
        number += 1
        meta.setdefault('formerLabels', []).append(f"{old}/upload-{meta.get('upload')}")
        meta['label'], meta['upload'] = new, number
        destination = root / new / f'upload-{number}'
        destination.parent.mkdir(parents=True, exist_ok=True)
        directory.rename(destination)
        write_json(destination / 'upload.json', meta)
        moved.append(destination)
    if not any((root / old).iterdir()):
        (root / old).rmdir()
    print(f'{old} -> {new}: ' + ', '.join(d.name for d in moved))
    return moved


# ---- statistics -------------------------------------------------------------------------------------------------

def points(result):
    return {'win': 1.0, 'draw': 0.5, 'loss': 0.0}.get(result)


def rated(rows):
    return [(points(r['result']), r['opponent']['score']) for r in rows
            if points(r.get('result')) is not None and isinstance((r.get('opponent') or {}).get('score'), (int, float))]


def fit_rating(games, scale):
    """MLE of r in P(win) = 1 / (1 + exp(-(r - s) / scale)); returns (r, se, log-likelihood)."""
    if not games:
        return None, None, 0.0
    low, high = min(s for _, s in games) - 15, max(s for _, s in games) + 15
    r = statistics.median(s for _, s in games)
    for _ in range(100):
        p = [1 / (1 + math.exp(-(r - s) / scale)) for _, s in games]
        gradient = sum(y - q for (y, _), q in zip(games, p)) / scale
        curvature = sum(q * (1 - q) for q in p) / scale ** 2
        step = max(-3.0, min(3.0, gradient / curvature)) if curvature > 1e-12 else math.copysign(3.0, gradient or 1)
        r = max(low, min(high, r + step))
        if abs(step) < 1e-7:
            break
    p = [1 / (1 + math.exp(-(r - s) / scale)) for _, s in games]
    info = sum(q * (1 - q) for q in p) / scale ** 2
    loglik = sum(y * math.log(max(q, 1e-12)) + (1 - y) * math.log(max(1 - q, 1e-12)) for (y, _), q in zip(games, p))
    return r, (1 / math.sqrt(info) if info > 1e-12 else None), loglik


def fit_scale(groups):
    """The logistic scale shared by all uploads (profile likelihood, golden section on log scale)."""
    groups = [g for g in groups if len(g) >= 10]
    if sum(len(g) for g in groups) < 60:
        return DEFAULT_SCALE, False

    def loglik(log_scale):
        return sum(fit_rating(g, math.exp(log_scale))[2] for g in groups)

    a, b = math.log(0.3), math.log(20.0)
    ratio = (math.sqrt(5) - 1) / 2
    c, d = b - ratio * (b - a), a + ratio * (b - a)
    fc, fd = loglik(c), loglik(d)
    for _ in range(40):
        if fc > fd:
            b, d, fd = d, c, fc
            c = b - ratio * (b - a)
            fc = loglik(c)
        else:
            a, c, fc = c, d, fd
            d = a + ratio * (b - a)
            fd = loglik(d)
    best = math.exp((a + b) / 2)
    if best < 0.31 or best > 19.5:
        return DEFAULT_SCALE, False
    return best, True


def record(rows):
    wins = sum(1 for r in rows if r.get('result') == 'win')
    draws = sum(1 for r in rows if r.get('result') == 'draw')
    losses = sum(1 for r in rows if r.get('result') == 'loss')
    n = wins + draws + losses
    rate = (wins + draws / 2) / n if n else None
    return {'games': n, 'wins': wins, 'draws': draws, 'losses': losses, 'rate': rate,
            'se': math.sqrt(rate * (1 - rate) / n) if n else None}


def placement(meta):
    """The first snapshot taken after placement finished (percentage 100), else None."""
    return next((s for s in meta.get('snapshots') or [] if s.get('percentage') == 100 and not s.get('inProgress')), None)


def size_class(row):
    width, height = row.get('width'), row.get('height')
    if width is None:
        return None
    return '30x20' if (width, height) == (30, 20) else 'tall' if height >= 19 else 'small'


def summarize(rows, scale):
    games = rated(rows)
    rating, se, _ = fit_rating(games, scale)
    known = sorted(s for _, s in games)
    summary = {**record(rows), 'opponents': len({(r.get('opponent') or {}).get('userId') for r in rows}),
               'opponentScoreMedian': statistics.median(known) if known else None,
               'ratedGames': len(games), 'performance': rating, 'performanceSe': se}
    for name, select in (('seat0', lambda r: r.get('seat') == 0), ('seat1', lambda r: r.get('seat') == 1),
                         ('small', lambda r: size_class(r) == 'small'), ('tall', lambda r: size_class(r) == 'tall'),
                         ('30x20', lambda r: size_class(r) == '30x20')):
        part = record([r for r in rows if select(r)])
        summary[name] = f"{part['wins']}-{part['draws']}-{part['losses']}" if part['games'] else None
    return summary


def shared(rows_a, rows_b):
    """Head-to-head on shared opponents (by user), stratified by opponent with weights 1 / (1/n_a + 1/n_b)."""
    def by_user(rows):
        table = {}
        for r in rows:
            user, value = (r.get('opponent') or {}).get('userId'), points(r.get('result'))
            if user is not None and value is not None:
                table.setdefault(user, []).append(value)
        return table

    a, b = by_user(rows_a), by_user(rows_b)
    common = sorted(set(a) & set(b))
    if not common:
        return {'opponents': 0}
    weights = {u: 1 / (1 / len(a[u]) + 1 / len(b[u])) for u in common}
    total = sum(weights.values())
    delta = sum(weights[u] * (sum(a[u]) / len(a[u]) - sum(b[u]) / len(b[u])) for u in common) / total
    games_a, games_b = sum(len(a[u]) for u in common), sum(len(b[u]) for u in common)
    return {'opponents': len(common), 'gamesA': games_a, 'gamesB': games_b,
            'rateA': sum(sum(a[u]) for u in common) / games_a, 'rateB': sum(sum(b[u]) for u in common) / games_b,
            'delta': delta, 'deltaSe': 0.5 / math.sqrt(total)}


# ---- compare ----------------------------------------------------------------------------------------------------

def collect(root, labels=None):
    found = []
    for directory, meta in uploads(root):
        if labels and meta.get('label') not in labels:
            continue
        found.append({'label': meta['label'], 'upload': meta['upload'], 'dir': directory, 'meta': meta,
                      'rows': list(load_rows(directory).values())})
    return found


def league(meta):
    """The league an upload placed in: its placement snapshot's division, else the latest one's, else a recorded one."""
    for snapshot in [placement(meta)] + list(reversed(meta.get('snapshots') or [])):
        if snapshot and snapshot.get('division') is not None:
            return snapshot['division']
    return meta.get('division')


def compare(root, labels=None):
    """Uploads, then versions and shared-opponent pairs within one league (CG scores do not compare across leagues)."""
    found = collect(root, labels)
    if not found:
        raise SystemExit(f'no uploads under {root}')
    everything = collect(root)
    scales = {lg: fit_scale([rated(u['rows']) for u in everything if league(u['meta']) == lg])
              for lg in {league(u['meta']) for u in everything}}
    report = {'createdAt': now(), 'root': str(root),
              'scales': {LEAGUES.get(lg, '?'): {'scale': s, 'fitted': f} for lg, (s, f) in scales.items()},
              'uploads': [], 'versions': [], 'pairs': []}
    for u in found:
        meta = u['meta']
        snapshots = meta.get('snapshots') or []
        report['uploads'].append({'label': u['label'], 'upload': u['upload'], 'agentId': meta.get('agentId'),
                                  'league': LEAGUES.get(league(meta)), 'promotedFrom': meta.get('promotedFrom'),
                                  'uploadedAt': meta.get('uploadedAt'), 'source': meta.get('source'),
                                  'build': (meta.get('marker') or {}).get('build'),
                                  'config': (meta.get('marker') or {}).get('config'),
                                  'placement': placement(meta), 'latest': snapshots[-1] if snapshots else None,
                                  **summarize(u['rows'], scales[league(meta)][0])})
    groups = list(dict.fromkeys((u['label'], league(u['meta'])) for u in found))
    for label, lg in groups:
        members = [u for u in found if u['label'] == label and league(u['meta']) == lg]
        scores = [p['score'] for p in (placement(u['meta']) for u in members) if p and p.get('score') is not None]
        report['versions'].append({'label': label, 'league': LEAGUES.get(lg), 'uploads': len(members),
                                   'placementScores': scores,
                                   'placementScoreMean': statistics.mean(scores) if scores else None,
                                   'placementScoreSd': statistics.stdev(scores) if len(scores) >= 2 else None,
                                   **summarize([r for u in members for r in u['rows']], scales[lg][0])})
        for i, a in enumerate(members):
            for b in members[i + 1:]:
                report['pairs'].append({'a': f"{a['label']}/upload-{a['upload']}",
                                        'b': f"{b['label']}/upload-{b['upload']}", **shared(a['rows'], b['rows'])})
    for i, (label_a, lg_a) in enumerate(groups):
        for label_b, lg_b in groups[i + 1:]:
            if lg_a != lg_b or label_a == label_b:
                continue
            rows_a = [r for u in found if u['label'] == label_a and league(u['meta']) == lg_a for r in u['rows']]
            rows_b = [r for u in found if u['label'] == label_b and league(u['meta']) == lg_b for r in u['rows']]
            name = f' ({LEAGUES.get(lg_a, "?")})'
            report['pairs'].append({'a': label_a + name, 'b': label_b + name, **shared(rows_a, rows_b)})
    return report


def pct(value, digits=1):
    return '—' if value is None else f'{100 * value:.{digits}f}'


def num(value, digits=2):
    return '—' if value is None else f'{value:.{digits}f}'


def snapshot_text(snapshot):
    if not snapshot:
        return '—'
    return f"{num(snapshot.get('score'))} #{snapshot.get('rank', '?')} @{snapshot.get('games', '?')}"


def print_report(report):
    scales = ', '.join(f"{name} {s['scale']:.2f} ({'fitted' if s['fitted'] else 'default'})"
                       for name, s in report['scales'].items())
    print('\nPerformance rating: the opponent arena score at which the upload would score 50 % (logistic scale per '
          f'league: {scales}). Scores compare within a league only.')
    print('\nupload            league  agent    placement score     latest score        games  W-D-L      win %   ± se  '
          'opp med  perf ± se       seats  small/tall/30x20')
    for u in report['uploads']:
        print(f"{u['label'] + '/upload-' + str(u['upload']):17} {u['league'] or '?':7} {u['agentId']!s:8} "
              f"{snapshot_text(u['placement']):19} "
              f"{snapshot_text(u['latest']):19} {u['games']:5}  {u['wins']}-{u['draws']}-{u['losses']:<6} "
              f"{pct(u['rate']):>5} {pct(u['se']):>5}  {num(u['opponentScoreMedian']):>7}  "
              f"{num(u['performance'])} ± {num(u['performanceSe'])}  {u['seat0'] or '—'}/{u['seat1'] or '—'}  "
              f"{u['small'] or '—'} / {u['tall'] or '—'} / {u['30x20'] or '—'}"
              + (f"  (promotion of {u['promotedFrom']})" if u.get('promotedFrom') else ''))
    print('\nversion  league  uploads  placement scores (mean ± sd)          games  win %   ± se  perf ± se')
    for v in report['versions']:
        scores = ', '.join(num(s) for s in v['placementScores']) or '—'
        spread = f"{num(v['placementScoreMean'])} ± {num(v['placementScoreSd'])}" if v['placementScores'] else '—'
        print(f"{v['label']:8} {v['league'] or '?':7} {v['uploads']:7}  {scores + ' (' + spread + ')':38} {v['games']:5}  "
              f"{pct(v['rate']):>5} {pct(v['se']):>5}  {num(v['performance'])} ± {num(v['performanceSe'])}")
    if report['pairs']:
        print('\nshared opponents (by player)      opps  games A/B  score A / B on them  stratified delta A-B')
        for p in report['pairs']:
            if not p.get('opponents'):
                print(f"{p['a'] + ' vs ' + p['b']:33} none")
                continue
            print(f"{p['a'] + ' vs ' + p['b']:33} {p['opponents']:4}  {p['gamesA']:4}/{p['gamesB']:<4}  "
                  f"{pct(p['rateA']):>5} / {pct(p['rateB']):<5}       {100 * p['delta']:+.1f} ± {100 * p['deltaSe']:.1f} pp")


# ---- command line -----------------------------------------------------------------------------------------------

def main(argv=None):
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--root', default='replays/arena', help='where uploads are filed (gitignored)')
    commands = parser.add_subparsers(dest='command', required=True)

    options = argparse.ArgumentParser(add_help=False)
    options.add_argument('--version', help='label; required for experiments ("(modified)" marker) and marker-less builds')
    options.add_argument('--player', default=PLAYER)
    options.add_argument('--no-replays', action='store_true', help='results only: 2-3 requests instead of one per game')
    options.add_argument('--max-requests', type=int, default=200, help='per fetch')
    options.add_argument('--pause', type=float, default=0.5, help='seconds between requests')
    options.add_argument('--cache', nargs='*', default=['replays/cg'], help='replay folders searched before the API')
    options.add_argument('--force-label', action='store_true')

    f = commands.add_parser('fetch', parents=[options],
                            help="file the live entry's (or --agent's) arena games and a rating snapshot")
    f.add_argument('--agent', type=int, help='an earlier upload instead of the live entry (no rating snapshot)')

    w = commands.add_parser('watch', parents=[options],
                            help='fetch the live entry every --interval minutes until its placement ends')
    w.add_argument('--interval', type=float, default=15, help='minutes between fetches (3 from 85 %% placement on)')
    w.add_argument('--hours', type=float, default=6, help='give up after this long')

    i = commands.add_parser('import', help='file an earlier upload from replays already downloaded (offline)')
    i.add_argument('--version', help='label (default: the marker\'s clean version id)')
    i.add_argument('--agent', type=int, required=True)
    i.add_argument('--cache', nargs='*', default=['replays/cg'])
    i.add_argument('--score', type=float, help='a recorded CG score for this upload')
    i.add_argument('--rank', type=int)
    i.add_argument('--games', type=int, help='games played when --score was read')
    i.add_argument('--at', help='when --score was read (ISO date)')
    i.add_argument('--complete', action='store_true', help='--score was read after placement finished')
    i.add_argument('--division', type=int, help='league (leaderboard divisionIndex: 3 Silver, 4 Gold)')
    i.add_argument('--note')
    i.add_argument('--force-label', action='store_true')

    c = commands.add_parser('compare', help='uploads, versions and shared-opponent deltas')
    c.add_argument('labels', nargs='*')
    c.add_argument('--out', help='also write the report as JSON (e.g. docs/evidence/arena-uploads-<date>.json)')

    r = commands.add_parser('relabel', help="at a promotion, move a candidate's uploads to the new version's label")
    r.add_argument('old')
    r.add_argument('new')

    args = parser.parse_args(argv)
    if args.command in ('fetch', 'watch'):
        if args.command == 'watch':
            args.agent = None
        directory = fetch(args) if args.command == 'fetch' else watch(args)
        print_report(compare(Path(args.root)))
        return directory
    if args.command == 'import':
        return import_upload(args)
    if args.command == 'relabel':
        return relabel(args.root, args.old, args.new)
    report = compare(Path(args.root), args.labels or None)
    print_report(report)
    if args.out:
        write_json(args.out, report)
        print(f'\nwritten {args.out}')
    return report


if __name__ == '__main__':
    main()
