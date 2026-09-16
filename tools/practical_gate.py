"""Assess fixed practical stages from existing reports; never run games or promote a bot.

Source/class identity and prospective pool selection must also be recorded by the experiment.
Only complete, paired, distinct-map CG reports with consistent outcomes are accepted here.
"""
import argparse
import hashlib
import json
from pathlib import Path

POLICY = 'practical-300-1000-v2'
CONDITIONS = ('timingProfile', 'firstSearchBudgetMs', 'turnSearchBudgetMs', 'threads', 'javaVersion')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_report(path, games):
    path = Path(path)
    raw = path.read_bytes()
    data = json.loads(raw)
    require(data['games'] == games, f'{path}: expected exactly {games} games')
    require(data['timingProfile'] == 'CG' and data['firstSearchBudgetMs'] == 486
            and data['turnSearchBudgetMs'] == 36, f'{path}: wrong timing profile')
    require(data['errorsA'] == data['errorsB'] == 0, f'{path}: bot errors')
    require(data['gamesAasP0'] == data['gamesAasP1'] == games // 2, f'{path}: missing side coverage')
    pairs = data['pairs']
    require(len(pairs) == games // 2, f'{path}: incomplete pairs')
    require([p['index'] for p in pairs] == list(range(games // 2)), f'{path}: invalid pair indices')
    maps = {}
    wins = losses = draws = 0
    for p in pairs:
        key = p['mapHash']
        require(len(key) == 64 and all(c in '0123456789abcdef' for c in key), f'{path}: invalid map hash')
        require(key not in maps, f'{path}: repeated map')
        outcomes = []
        for side, field in enumerate(('aAsP0', 'aAsP1')):
            game = p[field]
            require(game['error'] == [None, None], f'{path}: hidden game error')
            winner = game['winner']
            require(winner in (-1, 0, 1), f'{path}: invalid winner')
            units = 1 if winner == -1 else 2 if winner == side else 0
            outcomes.append(units)
            wins += units == 2
            losses += units == 0
            draws += units == 1
        maps[key] = {'seed': p['seed'], 'halfWins': outcomes,
                     'aAsP0': p['aAsP0'], 'aAsP1': p['aAsP1'],
                     'dimensions': [p[k] for k in ('width', 'height', 'townCount', 'pairCount')]}
    require((wins, losses, draws) == (data['winsA'], data['winsB'], data['draws']),
            f'{path}: outcome totals disagree')
    require(data['gamesPerMap'] == dict.fromkeys(maps, 2), f'{path}: map counts disagree')
    return {'path': path.as_posix(), 'sha256': hashlib.sha256(raw).hexdigest(),
            'data': data, 'maps': maps, 'halfWins': 2 * wins + draws,
            'score': (wins + .5 * draws) / games}


def same_conditions(a, b):
    for key in CONDITIONS:
        require(a['data'][key] == b['data'][key], f'Conditions changed: {key}')


def same_bot(a, side_a, b, side_b):
    require(a['data'][side_a.lower()] == b['data'][side_b.lower()], 'Bot specification changed')
    require(a['data']['params' + side_a] == b['data']['params' + side_b], 'Effective parameters changed')


def fresh(report, used):
    require(not used.intersection(report['maps']), 'Stage reuses a previously evaluated map')


def matched(candidate, control, screen, used):
    same_conditions(candidate, control)
    same_conditions(candidate, screen)
    same_bot(screen, 'A', candidate, 'A')
    same_bot(screen, 'B', control, 'A')
    same_bot(candidate, 'B', control, 'B')
    require(candidate['maps'].keys() == control['maps'].keys(), 'Common-opponent maps differ')
    fresh(candidate, used)
    deltas = []
    for key, ca in candidate['maps'].items():
        co = control['maps'][key]
        require(ca['seed'] == co['seed'] and ca['dimensions'] == co['dimensions'],
                'Common-opponent map/run seed metadata differ')
        # Sum of two half-win scores / four is the mean map score in [0,1].
        deltas.append({'mapHash': key, 'candidateMinusControl':
                       (sum(ca['halfWins']) - sum(co['halfWins'])) / 4})
    return deltas


def assess(screen, direct=None, style_candidate=None, style_control=None,
           full_candidate=None, full_control=None):
    inputs = [screen]
    result = {'policy': POLICY, 'confidenceRole': 'descriptive only; no overall 95% guarantee',
              'processReadiness': 'not assessed', 'screenScore': screen['score']}
    used = set(screen['maps'])
    if screen['halfWins'] <= 312:
        require(all(x is None for x in (direct, style_candidate, style_control, full_candidate, full_control)),
                'Continuation supplied after screen at or below 52%')
        result['directStage'] = 'STOP_SCREEN_300'
        result['strengthStatus'] = 'NOT_PASSED'
        result['commonOpponentStage'] = 'SKIPPED_DIRECT_FAILURE'
        result['reports'] = [compact(screen)]
        return result
    if screen['halfWins'] >= 360:
        require(direct is None, 'Direct continuation supplied after fast pass')
        result['directStage'] = 'PASS_300'
    else:
        if direct is None:
            require(all(x is None for x in (style_candidate, style_control, full_candidate, full_control)),
                    'Common-opponent reports supplied before required direct continuation')
            result['strengthStatus'] = 'NEEDS_DIRECT_1000'
            result['reports'] = [compact(screen)]
            return result
        same_conditions(screen, direct)
        same_bot(screen, 'A', direct, 'A')
        same_bot(screen, 'B', direct, 'B')
        fresh(direct, used)
        used.update(direct['maps'])
        inputs.append(direct)
        result['directScore'] = direct['score']
        result['directStage'] = 'PASS_1000' if direct['halfWins'] >= 1100 else 'NOT_PASSED'
        if result['directStage'] == 'NOT_PASSED':
            require(all(x is None for x in (style_candidate, style_control, full_candidate, full_control)),
                    'Common-opponent stages supplied after direct failure')
            result['strengthStatus'] = 'NOT_PASSED'
            result['commonOpponentStage'] = 'SKIPPED_DIRECT_FAILURE'
            result['reports'] = [compact(r) for r in inputs]
            return result
    require((style_candidate is None) == (style_control is None), 'Need BOTH 300-game style reports')
    if style_candidate is None:
        require(full_candidate is None and full_control is None, 'Missing style screen')
        result['strengthStatus'] = 'NEEDS_MATCHED_300_EACH'
    else:
        result['matchedScreenDeltas'] = matched(style_candidate, style_control, screen, used)
        used.update(style_candidate['maps'])
        inputs.extend((style_candidate, style_control))
        delta_units = style_candidate['halfWins'] - style_control['halfWins']
        result['matchedScreenDifference'] = delta_units / 600
        if delta_units >= 18:
            require(full_candidate is None and full_control is None, 'Style continuation supplied after fast pass')
            result['strengthStatus'] = 'PASS'
            result['commonOpponentStage'] = 'PASS_300'
        else:
            require((full_candidate is None) == (full_control is None), 'Need BOTH 1000-game style reports')
            if full_candidate is None:
                result['strengthStatus'] = 'NEEDS_MATCHED_1000_EACH'
            else:
                same_bot(style_candidate, 'B', full_candidate, 'B')
                result['matchedFullDeltas'] = matched(full_candidate, full_control, screen, used)
                inputs.extend((full_candidate, full_control))
                delta_units = full_candidate['halfWins'] - full_control['halfWins']
                result['matchedFullDifference'] = delta_units / 2000
                result['strengthStatus'] = 'PASS' if delta_units >= 0 else 'NOT_PASSED'
                result['commonOpponentStage'] = 'PASS_1000' if delta_units >= 0 else 'NOT_PASSED'
    result['reports'] = [compact(r) for r in inputs]
    return result


def compact(report):
    data = report['data']
    fields = ('a', 'b', 'paramsA', 'paramsB', 'games', 'winsA', 'winsB', 'draws', 'errorsA',
              'errorsB', 'confidence95', 'baseSeed', 'elapsedMs', 'poolId', 'poolSha256',
              'excludedReports', 'maxMsA', 'maxMsB', 'slowA', 'slowB', *CONDITIONS)
    return {'path': report['path'], 'sha256': report['sha256'], 'score': report['score'],
            **{k: data[k] for k in fields if k in data}, 'maps': report['maps']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--screen', required=True)
    for name in ('direct', 'style-candidate', 'style-control', 'full-candidate', 'full-control', 'out'):
        parser.add_argument('--' + name)
    args = parser.parse_args()
    report = lambda name, n: read_report(getattr(args, name), n) if getattr(args, name) else None
    result = assess(read_report(args.screen, 300), report('direct', 1000),
                    report('style_candidate', 300), report('style_control', 300),
                    report('full_candidate', 1000), report('full_control', 1000))
    if args.out:
        with Path(args.out).open('x', encoding='utf-8') as file:
            json.dump(result, file, indent=2)
    print(json.dumps({k:v for k,v in result.items() if k not in
                      ('reports', 'matchedScreenDeltas', 'matchedFullDeltas')}, indent=2))


if __name__ == '__main__':
    main()
