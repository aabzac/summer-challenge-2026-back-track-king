"""Hermetic tests of arena_uploads: marker parsing, results, filing rules, fetch/import and the statistics."""
import argparse
import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest

import arena_uploads as arena

BUILD, CONFIG, OTHER = 'a' * 64, 'b' * 64, 'c' * 64
ME = 1172955


def temp(case):
    folder = tempfile.TemporaryDirectory()
    case.addCleanup(folder.cleanup)
    return Path(folder.name)


def marker(display='v13', config=CONFIG):
    text = f'BTK_BUILD_V1 strategy=current build={BUILD} config={config}'
    return f'{display} | {text}' if display else text


def replay(game_id, agent, opponent, seat=0, result='win', opponent_score=27.5, message=None, size=(27, 18)):
    view = ' 0\n' + json.dumps({'global': {'graphics': f'{size[0]}|{size[1]}|3|0 0'}, 'frame': {'graphics': '0||'}}) + '\n'
    ranks = [0, 0]
    if result != 'draw':
        ranks[seat], ranks[1 - seat] = (0, 1) if result == 'win' else (1, 0)
    agents = [None, None]
    agents[seat] = {'index': seat, 'agentId': agent, 'codingamer': {'userId': ME, 'pseudo': 'aabzac'}, 'score': 29.0}
    agents[1 - seat] = {'index': 1 - seat, 'agentId': opponent, 'codingamer': {'userId': opponent + 1, 'pseudo': f'p{opponent}'},
                        'score': opponent_score}
    frames = [{'view': view, 'keyframe': True, 'agentId': -1}, {'agentId': 1 - seat, 'stdout': 'WAIT\n'},
              {'agentId': seat, 'stdout': f'PLACE_TRACKS 1 1;MESSAGE {message or marker()}\n'}]
    return {'gameId': game_id, 'agents': agents, 'ranks': ranks, 'frames': frames}


def battle(game_id, agent, opponent, result='win', done=True):
    mine, theirs = {'win': (0, 1), 'loss': (1, 0), 'draw': (0, 0)}[result]
    return {'gameId': game_id, 'done': done,
            'players': [{'playerAgentId': agent, 'position': mine, 'userId': ME, 'nickname': 'aabzac'},
                        {'playerAgentId': opponent, 'position': theirs, 'userId': opponent + 1, 'nickname': f'p{opponent}'}]}


class FakeApi(arena.Api):
    def __init__(self, board, battles, games, max_requests=100):
        super().__init__(max_requests, pause=0)
        self.board, self.battle_lists, self.games, self.calls = board, battles, games, []

    def post(self, service, body):
        if self.left <= 0:
            raise arena.BudgetExhausted(service)
        self.left -= 1
        self.used += 1
        self.calls.append((service, body))
        if service.startswith('Leaderboards/'):
            return self.board
        if service.startswith('gamesPlayersRanking/'):
            return self.battle_lists.get(body[0], [])
        return json.loads(json.dumps(self.games[body[0]]))


def board(agent, score=29.23, rank=316, percentage=100, others=(), division=3):
    users = [{'pseudo': 'aabzac', 'agentId': agent, 'rank': rank, 'score': score, 'percentage': percentage,
              'inProgress': percentage != 100, 'league': {'divisionIndex': division}, 'creationTime': 1793110979048,
              'codingamer': {'userId': ME}}]
    users += [{'pseudo': f'p{a}', 'agentId': a, 'rank': 300 + i, 'score': s} for i, (a, s) in enumerate(others)]
    return {'users': users, 'count': 1634}


def fetch_args(root, **extra):
    values = dict(root=str(root), cache=[], player='aabzac', agent=None, version=None, no_replays=False,
                  max_requests=100, pause=0, force_label=False)
    values.update(extra)
    return argparse.Namespace(**values)


class MarkerTest(unittest.TestCase):
    def test_display_modified_and_bare_markers(self):
        clean = arena.parse_message(f'PLACE_TRACKS 1 2;MESSAGE {marker()}\n')
        self.assertEqual(('v13', 'v13', False, BUILD, CONFIG),
                         (clean['display'], clean['version'], clean['modified'], clean['build'], clean['config']))
        modified = arena.parse_message(f'MESSAGE {marker("v13 (modified)")}')
        self.assertEqual(('v13', True), (modified['version'], modified['modified']))
        named = arena.parse_message(f'MESSAGE {marker("v11 Root Target")}')
        self.assertEqual(('v11', False), (named['version'], named['modified']))
        bare = arena.parse_message(f'MESSAGE {marker(None)}')
        self.assertEqual((None, None, False), (bare['display'], bare['version'], bare['modified']))
        self.assertIsNone(arena.parse_message('MESSAGE hello;PLACE_TRACKS 1 1'))
        self.assertIsNone(arena.parse_message('MESSAGE BTK_BUILD_V1 strategy=current build=xyz config=abc'))

    def test_marker_is_read_from_our_seat_only(self):
        game = replay(1, 10, 20, seat=1)
        game['frames'][1]['stdout'] = f'MESSAGE {marker("v99", OTHER)}'
        self.assertEqual('v13', arena.replay_marker(game, 10)['version'])
        self.assertEqual((27, 18), arena.map_size(game))


class ResultTest(unittest.TestCase):
    def test_battle_positions_give_the_result(self):
        for result in ('win', 'loss', 'draw'):
            row = arena.battle_row(battle(5, 10, 20, result), 10)
            self.assertEqual((result, 20, 21, 'p20'), (row['result'], row['opponent']['agentId'],
                                                       row['opponent']['userId'], row['opponent']['pseudo']))
        self.assertIsNone(arena.battle_row(battle(5, 10, 20, done=False), 10))
        self.assertIsNone(arena.battle_row(battle(5, 11, 20), 10))

    def test_replay_ranks_are_indexed_by_seat(self):
        for seat in (0, 1):
            for result in ('win', 'loss', 'draw'):
                self.assertEqual(result, arena.replay_result(replay(1, 10, 20, seat=seat, result=result), 10))

    def test_enrich_keeps_a_leaderboard_score(self):
        row = {'gameId': 1, 'result': 'win', 'opponent': {'agentId': 20, 'score': 30.0, 'scoreSource': 'leaderboard'}}
        arena.enrich(row, replay(1, 10, 20, seat=1, size=(30, 20)), 10)
        self.assertEqual((1, 30, 20, 30.0), (row['seat'], row['width'], row['height'], row['opponent']['score']))
        fresh = arena.enrich({'gameId': 1}, replay(1, 10, 20, result='loss', opponent_score=26.123), 10)
        self.assertEqual(('loss', 26.12, 'replay'), (fresh['result'], fresh['opponent']['score'], fresh['opponent']['scoreSource']))


class FilingTest(unittest.TestCase):
    def setUp(self):
        self.root = temp(self)

    def test_labels(self):
        clean, modified = arena.parse_message(f'MESSAGE {marker()}'), arena.parse_message(f'MESSAGE {marker("v13 (modified)")}')
        self.assertEqual('v13', arena.resolve_label(None, clean))
        self.assertEqual('v13', arena.resolve_label('v13', clean))
        with self.assertRaises(SystemExit):
            arena.resolve_label('v14', clean)
        self.assertEqual('v14', arena.resolve_label('v14', clean, force=True))
        with self.assertRaises(SystemExit):
            arena.resolve_label(None, modified)
        self.assertEqual('bypass-b3', arena.resolve_label('bypass-b3', modified))
        with self.assertRaises(SystemExit):
            arena.resolve_label(None, None)

    def test_uploads_are_numbered_per_label_and_configuration_checked(self):
        mark = arena.parse_message(f'MESSAGE {marker()}')
        first, meta = arena.upload_dir(self.root, 'v13', 100, mark)
        arena.write_json(first / 'upload.json', meta)
        again, same = arena.upload_dir(self.root, 'v13', 100, mark)
        self.assertEqual((first, 1), (again, same['upload']))
        second, meta2 = arena.upload_dir(self.root, 'v13', 101, mark)
        self.assertEqual(('upload-2', 2), (second.name, meta2['upload']))
        with self.assertRaises(SystemExit):
            arena.upload_dir(self.root, 'v14', 100, mark)
        with self.assertRaises(SystemExit):
            arena.upload_dir(self.root, 'v13', 102, arena.parse_message(f'MESSAGE {marker(config=OTHER)}'))


class FetchTest(unittest.TestCase):
    def setUp(self):
        self.root, self.cache = temp(self), temp(self)
        self.enterContext(contextlib.redirect_stdout(io.StringIO()))

    def test_fetch_files_games_snapshot_and_reuses_cache_and_disk(self):
        games = {g: replay(g, 100, 200 + g % 3, seat=g % 2, result='win' if g % 3 else 'loss') for g in range(1, 7)}
        (self.cache / '1.json').write_text(json.dumps(games[1]), encoding='utf-8')
        battles = {100: [battle(g, 100, 200 + g % 3, 'win' if g % 3 else 'loss') for g in range(1, 6)]}
        api = FakeApi(board(100, others=[(201, 28.5)]), battles, games)
        directory = arena.fetch(fetch_args(self.root, cache=[str(self.cache)]), api)
        self.assertEqual(self.root / 'v13' / 'upload-1', directory)
        self.assertEqual(2 + 4, api.used)  # leaderboard, battles, four replays (game 1 came from the cache)
        meta = arena.read_json(directory / 'upload.json')
        self.assertEqual(('v13', 1, 100, CONFIG, ME), (meta['label'], meta['upload'], meta['agentId'],
                                                       meta['marker']['config'], meta['userId']))
        self.assertEqual((29.23, 316, 100, 5), tuple(meta['snapshots'][0][k] for k in ('score', 'rank', 'percentage', 'games')))
        rows = arena.load_rows(directory)
        self.assertEqual(5, len(list((directory / 'games').glob('*.json'))))
        self.assertEqual(('leaderboard', 28.5), (rows[1]['opponent']['scoreSource'], rows[1]['opponent']['score']))
        self.assertEqual(('replay', 27.5), (rows[2]['opponent']['scoreSource'], rows[2]['opponent']['score']))
        self.assertEqual({1: 'win', 2: 'win', 3: 'loss', 4: 'win', 5: 'win'}, {k: r['result'] for k, r in rows.items()})

        battles[100].append(battle(6, 100, 200, 'loss'))
        api = FakeApi(board(100, score=29.5, rank=300, others=[(201, 28.5)]), battles, games)
        arena.fetch(fetch_args(self.root), api)
        self.assertEqual(3, api.used)  # only the new replay
        meta = arena.read_json(directory / 'upload.json')
        self.assertEqual([29.23, 29.5], [s['score'] for s in meta['snapshots']])
        self.assertEqual(29.23, arena.placement(meta)['score'])
        self.assertEqual(6, len(arena.load_rows(directory)))

    def test_budget_and_results_only(self):
        games = {g: replay(g, 100, 200, result='win') for g in range(1, 5)}
        battles = {100: [battle(g, 100, 200) for g in range(1, 5)]}
        api = FakeApi(board(100), battles, games, max_requests=3)
        directory = arena.fetch(fetch_args(self.root, max_requests=3), api)
        self.assertEqual(4, len(arena.load_rows(directory)))
        self.assertEqual(1, len(list((directory / 'games').glob('*.json'))))  # the marker replay only
        api = FakeApi(board(100), battles, games)
        arena.fetch(fetch_args(self.root, no_replays=True), api)
        self.assertEqual(2, api.used)

    def test_experiment_needs_a_label_and_a_new_agent_gets_the_next_upload(self):
        exp = {1: replay(1, 100, 200, message=marker('v13 (modified)', OTHER))}
        with self.assertRaises(SystemExit):
            arena.fetch(fetch_args(self.root), FakeApi(board(100), {100: [battle(1, 100, 200)]}, exp))
        arena.fetch(fetch_args(self.root, version='bypass-b3'), FakeApi(board(100), {100: [battle(1, 100, 200)]}, exp))
        clean = {2: replay(2, 101, 200)}
        arena.fetch(fetch_args(self.root), FakeApi(board(101), {101: [battle(2, 101, 200)]}, clean))
        clean2 = {3: replay(3, 102, 200)}
        arena.fetch(fetch_args(self.root), FakeApi(board(102), {102: [battle(3, 102, 200)]}, clean2))
        filed = [(m['label'], m['upload'], m['agentId']) for _, m in arena.uploads(self.root)]
        self.assertEqual([('bypass-b3', 1, 100), ('v13', 1, 101), ('v13', 2, 102)], filed)
        with self.assertRaises(SystemExit):  # another configuration than v13's
            arena.relabel(self.root, 'bypass-b3', 'v13')
        arena.relabel(self.root, 'bypass-b3', 'v14')
        filed = [(m['label'], m['upload'], m['agentId'], m.get('formerLabels')) for _, m in arena.uploads(self.root)]
        self.assertEqual(('v14', 1, 100, ['bypass-b3/upload-1']), filed[2])
        self.assertFalse((self.root / 'bypass-b3').exists())
        self.assertEqual(1, len(arena.load_rows(self.root / 'v14' / 'upload-1')))

    def test_import_from_downloaded_replays(self):
        for g in range(1, 4):
            (self.cache / f'{g}.json').write_text(json.dumps(replay(g, 100, 200, result='loss' if g == 2 else 'win')),
                                                  encoding='utf-8')
        (self.cache / '9.json').write_text(json.dumps(replay(9, 555, 200)), encoding='utf-8')
        args = argparse.Namespace(root=str(self.root), cache=[str(self.cache)], agent=100, version=None, note='x',
                                  score=26.0, rank=360, games=3, at='2026-10-25', complete=False, force_label=False,
                                  division=3)
        directory = arena.import_upload(args)
        rows = arena.load_rows(directory)
        self.assertEqual({1: 'win', 2: 'loss', 3: 'win'}, {k: r['result'] for k, r in rows.items()})
        meta = arena.read_json(directory / 'upload.json')
        self.assertEqual(('import', None, 3), (meta['source'], arena.placement(meta), arena.league(meta)))

    def test_an_agent_without_games_is_not_filed(self):
        with self.assertRaises(SystemExit):
            arena.fetch(fetch_args(self.root, agent=555, version='v13'), FakeApi(board(100), {}, {}))
        self.assertEqual([], arena.uploads(self.root))

    def test_promotion_is_recognised_and_watch_follows_it_to_the_end_of_placement(self):
        games = {g: replay(g, 100 if g < 10 else 101, 200) for g in range(1, 13)}
        rounds = [
            FakeApi(board(100, percentage=50), {100: [battle(1, 100, 200), battle(2, 100, 200)]}, games),
            FakeApi(board(100, percentage=92), {100: [battle(g, 100, 200) for g in (1, 2, 3)]}, games),
            FakeApi(board(101, score=10.5, rank=290, percentage=5, division=4), {101: [battle(10, 101, 200)]}, games),
            FakeApi(board(101, score=12.0, rank=280, percentage=100, division=4),
                    {101: [battle(g, 101, 200) for g in (10, 11, 12)]}, games),
        ]
        sleeps = []
        args = fetch_args(self.root, interval=15, hours=1)
        directory = arena.watch(args, api_factory=lambda: rounds.pop(0), sleep=sleeps.append)
        self.assertEqual([900, 180, 900], sleeps)  # every 3 minutes from 85 % placement on
        filed = {m['agentId']: (m['label'], m['upload'], m.get('promotedFrom'), arena.league(m))
                 for _, m in arena.uploads(self.root)}
        self.assertEqual({100: ('v13', 1, None, 3), 101: ('v13', 2, 100, 4)}, filed)
        self.assertEqual(self.root / 'v13' / 'upload-2', directory)
        self.assertEqual(3, len(arena.load_rows(directory)))
        self.assertIsNone(arena.placement(arena.read_json(self.root / 'v13' / 'upload-1' / 'upload.json')))
        report = arena.compare(self.root)
        self.assertEqual([('v13', 'Silver'), ('v13', 'Gold')], [(v['label'], v['league']) for v in report['versions']])
        self.assertEqual([], report['pairs'])  # no comparison across leagues


class StatisticsTest(unittest.TestCase):
    def test_rating_is_the_fifty_percent_point(self):
        games = [(1.0, 25.0), (0.0, 25.0), (1.0, 27.0), (0.0, 23.0)]
        rating, se, _ = arena.fit_rating(games, 2.0)
        self.assertAlmostEqual(25.0, rating, places=4)
        self.assertGreater(se, 0)
        stronger, _, _ = arena.fit_rating([(1.0, 25.0)] * 3 + [(0.0, 25.0)], 2.0)
        self.assertAlmostEqual(25.0 + 2.0 * __import__('math').log(3), stronger, places=4)

    def test_shared_opponents_are_stratified(self):
        def rows(spec):
            return [{'result': r, 'opponent': {'userId': u}} for u, results in spec.items() for r in results]
        a = rows({1: ['win', 'win'], 2: ['loss'], 3: ['win']})
        b = rows({1: ['win', 'loss'], 2: ['loss', 'loss'], 4: ['loss']})
        result = arena.shared(a, b)
        self.assertEqual((2, 3, 4), (result['opponents'], result['gamesA'], result['gamesB']))
        # opponent 1: 1.0 vs 0.5 with weight 1; opponent 2: 0 vs 0 with weight 2/3
        self.assertAlmostEqual(0.5 / (1 + 2 / 3), result['delta'])
        self.assertAlmostEqual(0.5 / (1 + 2 / 3) ** 0.5, result['deltaSe'])
        self.assertEqual({'opponents': 0}, arena.shared(a, rows({9: ['win']})))

    def test_compare_reports_uploads_versions_and_pairs(self):
        root = temp(self)
        for upload, agent, results in ((1, 100, 'wwlw'), (2, 101, 'wllw')):
            directory = root / 'v13' / f'upload-{upload}'
            arena.write_json(directory / 'upload.json', {'label': 'v13', 'upload': upload, 'agentId': agent,
                                                         'snapshots': [{'score': [29.1, 29.2][upload - 1], 'percentage': 100}]})
            arena.save_rows(directory, {i: {'gameId': i, 'result': {'w': 'win', 'l': 'loss'}[c],
                                            'opponent': {'userId': i % 2, 'score': 28.0}} for i, c in enumerate(results)})
        report = arena.compare(root)
        self.assertEqual([0.75, 0.5], [u['rate'] for u in report['uploads']])
        version = report['versions'][0]
        self.assertEqual((2, 8, [29.1, 29.2]), (version['uploads'], version['games'], version['placementScores']))
        self.assertEqual(('v13/upload-1', 'v13/upload-2', 2), (report['pairs'][0]['a'], report['pairs'][0]['b'],
                                                              report['pairs'][0]['opponents']))
        self.assertEqual({'?': {'scale': arena.DEFAULT_SCALE, 'fitted': False}}, report['scales'])


if __name__ == '__main__':
    unittest.main()
