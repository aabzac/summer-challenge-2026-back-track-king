"""Assess local direct stages plus a pinned 40-game boss cache; never play or promote.

The v2 checker and its historical decisions remain unchanged. This v3 checker
reuses its local-report validation, then consumes boss_cached.assess_cached.
Local and CG map hashes have different serializations: cross-domain map exclusion
and local executable provenance remain external experiment-manifest checks.
"""
import argparse
import json
from pathlib import Path
import re

from practical_gate import compact, fresh, read_report, require, same_bot, same_conditions

POLICY = 'practical-300-1000-boss-cache-v3'
SCREEN_GAMES = 300
DIRECT_GAMES = 1000
BOSS_GAMES = 40
BOSS_MAP_HASH_SCHEME = 'cg-global-graphics-utf8-sha256-v1'


def _hash(value):
    return isinstance(value, str) and re.fullmatch(r'[0-9a-f]{64}', value) is not None


def _integer(value, lower=0, upper=None):
    return type(value) is int and value >= lower and (upper is None or value <= upper)


def checked_boss(boss, screen):
    """Check the assessor contract and configuration; no CG scoring is recomputed."""
    require(isinstance(boss, dict) and boss.get('policy') == POLICY, 'Unexpected cached boss policy')
    require(type(boss.get('complete')) is bool, 'Missing cached boss completion status')
    require(isinstance(boss.get('issues'), list), 'Missing cached boss issues')
    require(not boss['issues'], 'Cached boss evidence has issues')
    require(_hash(boss.get('planId')), 'Invalid cached boss plan identity')
    require(boss.get('mapHashScheme', BOSS_MAP_HASH_SCHEME) == BOSS_MAP_HASH_SCHEME,
            'Unexpected cached boss map hash scheme')
    for arm, side in (('candidate', 'A'), ('control', 'B')):
        value = boss.get(arm)
        require(isinstance(value, dict), 'Missing cached boss build')
        require(value.get('spec') == screen['data'][side.lower()], 'Cached boss bot specification changed')
        require(isinstance(value.get('params'), dict) and bool(value['params'])
                and value['params'] == screen['data']['params' + side], 'Cached boss effective parameters changed')
        require(_hash(value.get('buildId')) and _hash(value.get('sourceSha256')), 'Missing cached boss artifact identity')
        for field in ('games', 'wins', 'losses', 'draws'):
            require(_integer(value.get(field), 0, BOSS_GAMES), 'Invalid cached boss outcome count')
        require(value['wins'] + value['losses'] + value['draws'] == value['games'],
                'Cached boss outcome totals disagree')
        require(_integer(value.get('halfWins'), 0, BOSS_GAMES * 2)
                and value['halfWins'] == 2 * value['wins'] + value['draws'], 'Cached boss half-win score disagrees')
    require(boss['control']['games'] == BOSS_GAMES, 'Pinned boss control must contain exactly 40 games')
    pairs = boss.get('pairs')
    require(isinstance(pairs, list) and len(pairs) == boss['candidate']['games'], 'Incomplete cached boss pairs')
    maps, ids = set(), set()
    for pair in pairs:
        require(isinstance(pair, dict) and _hash(pair.get('mapHash')), 'Invalid cached boss map identity')
        require(pair['mapHash'] not in maps, 'Cached boss stage repeats a map')
        maps.add(pair['mapHash'])
        require(_integer(pair.get('seat'), 0, 1), 'Invalid cached boss player side')
        seed = pair.get('seed')
        require((type(seed) is int or isinstance(seed, str))
                and re.fullmatch(r'-?(?:0|[1-9][0-9]*)', str(seed)) is not None, 'Invalid cached boss map seed')
        for key in ('candidateGameId', 'controlGameId'):
            game_id = pair.get(key)
            require(_integer(game_id, 1) and game_id not in ids, 'Repeated or invalid cached boss game ID')
            ids.add(game_id)
    complete = boss['candidate']['games'] == BOSS_GAMES
    require(boss['complete'] == complete, 'Cached boss completion disagrees with fixed sample size')
    expected = ('PASS' if boss['candidate']['halfWins'] >= boss['control']['halfWins'] else 'NOT_PASSED') if complete else 'INCOMPLETE'
    require(boss.get('bossStage') == expected, 'Cached boss verdict disagrees with the scores')
    return boss


def assess(screen, direct=None, boss=None):
    """Consume validated local reports and a read-only assess_cached result."""
    require(screen['data']['games'] == SCREEN_GAMES, 'Expected exactly 300 screen games')
    inputs = [screen]
    result = {
        'policy': POLICY,
        'confidenceRole': 'descriptive only; no overall 95% guarantee',
        'processReadiness': 'not assessed; exact-artifact process and engine checks remain required',
        'sourceIdentity': 'local executable identity is not assessed here; the cached boss assessor verifies its retained artifacts',
        'priorLocalExperimentFreshness': 'not assessed here; prospective exclusions remain required',
        'crossDomainFreshness': 'not assessed: local Protocol.initBlock and CG global.graphics use different hash schemes; the pinned boss pool is intentionally reused and external experiment setup must exclude it from local stages',
        'promotion': False,
        'screenScore': screen['score'],
    }
    used = set(screen['maps'])
    # Integer comparisons include draw half-credit without rounded percentages.
    if screen['halfWins'] * 100 <= 2 * SCREEN_GAMES * 52:
        require(direct is None and boss is None, 'Continuation supplied after screen at or below 52%')
        result.update(directStage='STOP_SCREEN_300', strengthStatus='NOT_PASSED',
                      bossStage='SKIPPED_DIRECT_FAILURE', bossUsage='NOT_STARTED')
        result['reports'] = [compact(screen)]
        return result

    if screen['halfWins'] * 100 >= 2 * SCREEN_GAMES * 60:
        require(direct is None, 'Direct continuation supplied after fast pass')
        result['directStage'] = 'PASS_300'
    elif direct is None:
        result['directStage'] = 'NEEDS_DIRECT_1000'
    else:
        require(direct['data']['games'] == DIRECT_GAMES, 'Expected exactly 1000 direct games')
        same_conditions(screen, direct)
        same_bot(screen, 'A', direct, 'A')
        same_bot(screen, 'B', direct, 'B')
        fresh(direct, used)
        inputs.append(direct)
        result['directScore'] = direct['score']
        result['directStage'] = 'PASS_1000' if direct['halfWins'] * 100 >= 2 * DIRECT_GAMES * 55 else 'NOT_PASSED'

    # A boss batch may run alongside the required direct continuation. Keep its
    # evidence even if local strength later fails; it cannot override that failure.
    if boss is not None:
        result['boss'] = checked_boss(boss, screen)
        result['bossStage'] = boss['bossStage']
        if boss['complete']:
            result['bossScoreDifference'] = (boss['candidate']['halfWins'] - boss['control']['halfWins']) / (2 * BOSS_GAMES)
    else:
        result['bossStage'] = 'NOT_STARTED'

    if result['directStage'] == 'NEEDS_DIRECT_1000':
        result.update(strengthStatus='NEEDS_DIRECT_1000',
                      bossUsage='RETAINED_PENDING_DIRECT' if boss is not None else 'NOT_STARTED')
    elif result['directStage'] == 'NOT_PASSED':
        result.update(strengthStatus='NOT_PASSED',
                      bossUsage='RETAINED_NONPROMOTING_DIRECT_FAILURE' if boss is not None else 'SKIPPED_DIRECT_FAILURE')
    elif boss is None or not boss['complete']:
        result.update(strengthStatus='NEEDS_BOSS_CACHED_40',
                      bossUsage='INCOMPLETE' if boss is not None else 'NOT_STARTED')
    else:
        result.update(strengthStatus=boss['bossStage'], bossUsage='ASSESSED_AFTER_DIRECT_PASS')
    result['reports'] = [compact(report) for report in inputs]
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--screen', required=True, type=Path)
    parser.add_argument('--direct', type=Path)
    parser.add_argument('--boss-plan', type=Path)
    parser.add_argument('--out', type=Path)
    args = parser.parse_args(argv)
    try:
        boss = None
        if args.boss_plan:
            from boss_cached import assess_cached
            boss = assess_cached(args.boss_plan)
        result = assess(read_report(args.screen, SCREEN_GAMES),
                        read_report(args.direct, DIRECT_GAMES) if args.direct else None, boss)
        if args.out:
            with args.out.open('x', encoding='utf-8') as stream:
                json.dump(result, stream, ensure_ascii=False, indent=2)
                stream.write('\n')
        print(json.dumps({key: value for key, value in result.items() if key not in ('reports', 'boss')},
                         ensure_ascii=False, indent=2))
        return result
    except (ValueError, OSError, KeyError, TypeError, AttributeError, ImportError) as error:
        if isinstance(error, (KeyError, TypeError, AttributeError, ImportError)):
            parser.exit(1, 'Unsupported or unavailable stage evidence; no verdict saved.\n')
        parser.exit(1, f'{error}\n')


if __name__ == '__main__':
    main()
