"""Assess a single pooled 1000-game direct comparison with rejection-only futility looks.

Policy **practical-1000-futility-boss-cache-v4** (adopted 2026-09-20). It replaces the v3
screen/conditional-direct shape with one fixed 1000-game sample versus the selected control,
played on three registered blocks of disjoint fresh maps and pooled into a single decision:

  block 1: 300 games / 150 maps   -> cumulative 300
  block 2: 200 games / 100 maps   -> cumulative 500
  block 3: 500 games / 250 maps   -> cumulative 1000

Two pre-specified interim looks are **futility only**: they can stop the run (reject the
candidate) but never promote it early, so they cannot inflate the false-promotion rate. Stop
when the pooled score is strictly below 50% at cumulative 300 or 500 games (a candidate whose
true rate is 55% falls under 50% at 500 games about 1% of the time, so a genuine passer is
almost never aborted). A candidate that survives both looks always plays the full 1000, and
promotion is decided once, at >=55% over all 1000 games.

The 40-game cached-boss check is unchanged from v3 (`boss_cached`/`boss_practical_gate`): the
boss batch may run concurrently once the 500-game look clears, and a local failure can never be
rescued by boss results. The v2 and v3 checkers and their historical verdicts stay untouched.
"""
import argparse
import json
from pathlib import Path
import re

from boss_practical_gate import BOSS_GAMES, checked_boss
from practical_gate import compact, fresh, read_report as _legacy_read_report, require, same_bot, same_conditions

POLICY = 'practical-1000-futility-boss-cache-v4'
BLOCK_GAMES = (300, 200, 500)
CUMULATIVE = (300, 500, 1000)
PASS_GAMES = 1000
PASS_PERCENT = 55
FUTILITY_PERCENT = 50
ARM_BUDGET_FIELDS = ('firstSearchBudgetMsA', 'firstSearchBudgetMsB',
                     'turnSearchBudgetMsA', 'turnSearchBudgetMsB')


def _turn_budget(data, side):
    """Resolve only the prospective timing option; other recipe checks remain unchanged."""
    params, spec = data['params' + side], data[side.lower()]
    require(isinstance(params, dict) and isinstance(spec, str), f'Missing arm {side} recipe')
    value = params.get('TURN_BUDGET_MS', '36')
    require(isinstance(value, str) and value in ('36', '38', '40'),
            f'Unsupported TURN_BUDGET_MS for arm {side}')
    version, separator, query = spec.partition('?')
    requested = None
    if separator:
        for item in re.split('[&,]', query):
            key, equals, setting = item.partition('=')
            if key.strip() == 'TURN_BUDGET_MS':
                require(bool(equals) and setting.strip() in ('36', '38', '40'),
                        f'Invalid TURN_BUDGET_MS specification for arm {side}')
                requested = setting.strip()
    require((requested if requested is not None else '36') == value,
            f'TURN_BUDGET_MS specification/parameters differ for arm {side}')
    if value != '36':
        require(version == 'current' and params.get('USE_ROLLOUT_POLICY') == 'true'
                and params.get('USE_HEURISTIC_POLICY', 'false') == 'false'
                and params.get('USE_NEURAL_TARGET_PROPOSAL', 'false') == 'false',
                f'{value} ms requires the current native rollout policy for arm {side}')
    return int(value)


def effective_budgets(report):
    """Validate actual arm budgets without modifying the legacy report or its raw hash.

    Shared 486/36 fields identify the CG baseline. New reports additionally state the
    effective budgets by bot identity. Historical reports can infer the baseline only
    when neither arm requests an override.
    """
    data = report['data']
    require(data['timingProfile'] == 'CG' and data['firstSearchBudgetMs'] == 486
            and data['turnSearchBudgetMs'] == 36, 'Expected CG baseline budgets 486/36')
    expected = dict(zip(ARM_BUDGET_FIELDS, (486, 486, _turn_budget(data, 'A'), _turn_budget(data, 'B'))))
    present = [field in data for field in ARM_BUDGET_FIELDS]
    require(not any(present) or all(present), 'Partial per-arm search budget evidence')
    if not any(present):
        require(expected['turnSearchBudgetMsA'] == expected['turnSearchBudgetMsB'] == 36,
                'Nondefault budget requires explicit per-arm search budget evidence')
        return expected
    for field, value in expected.items():
        require(type(data[field]) is int and data[field] == value,
                f'Effective search budget differs from recipe: {field}')
    return expected


def read_report(path, games):
    report = _legacy_read_report(path, games)
    effective_budgets(report)
    return report

# A frozen version id used as the local control is the same bot as the measured `current`
# artifact whose 40 boss games are cached (docs/vNN-promotion.md pins both); the cached record
# carries the build-time spec and parameter list, the local report the frozen id and every later
# default-off field. The alias is accepted only for this exact spec/build pair, and every field
# missing from the cached snapshot must be a later default-off field at its default value.
FROZEN_CONTROL_ALIASES = {
    'v08': {'spec': 'current?USE_PROTECTED_OPENING=1',
            'buildId': 'c6b4deae785d05d8fca5822393e8f805353541bc16345f9a0e4b89bf0871fb6b'},
    'v09': {'spec': 'current?USE_ROLLOUT_FILL=1',
            'buildId': '4cda8b1b50de4f94009b658d6aa56d13f3ff9577168ae4395e85718668e55407'},
}
LATER_DEFAULT_OFF_FIELDS = {
    'USE_ROLLOUT_LIVE_TARGET': 'false', 'ROLLOUT_LIVE_WEIGHT': '12.0', 'ROLLOUT_LIVE_TURNS': '2',
    'ROLLOUT_ALWAYS_DISRUPT': 'false', 'USE_ROLLOUT_TRACK_VALUE': 'false',
    'ROLLOUT_OPP_TRACK_VALUE': '5.0', 'ROLLOUT_OWN_TRACK_VALUE': '6.0',
    'ROLLOUT_TRACK_VALUE_SELF_ONLY': 'false', 'USE_ROLLOUT_FILL': 'false',
    'ROLLOUT_FILL_MAX_INSTABILITY': '0',
    'INDRILL_INK_PENALTY': '2',
}


def aliased_control(boss, block1):
    """Return the cached boss record with its control arm relabelled to the local frozen control
    when the documented alias applies; otherwise the record unchanged (checked_boss decides)."""
    local_spec = block1['data']['b']
    alias = FROZEN_CONTROL_ALIASES.get(local_spec)
    control = boss.get('control') if isinstance(boss, dict) else None
    if alias is None or not isinstance(control, dict) or control.get('spec') != alias['spec'] \
            or control.get('buildId') != alias['buildId']:
        return boss, None
    cached, local = control.get('params'), block1['data']['paramsB']
    require(isinstance(cached, dict) and cached and isinstance(local, dict), 'Missing control parameters for the alias')
    for key, value in cached.items():
        require(local.get(key) == value, f'Aliased control parameter differs: {key}')
    for key in local.keys() - cached.keys():
        require(LATER_DEFAULT_OFF_FIELDS.get(key) == local[key], f'Aliased control has a non-default later field: {key}')
    relabelled = dict(boss, control=dict(control, spec=local_spec, params=dict(local)))
    return relabelled, {'localControl': local_spec, 'cachedSpec': alias['spec'], 'buildId': alias['buildId'],
                        'laterDefaultFields': sorted(local.keys() - cached.keys())}


def _pool(reports):
    half = sum(report['halfWins'] for report in reports)
    games = sum(report['data']['games'] for report in reports)
    return half, games, (half / (2 * games) if games else 0.0)


def _below(half, games, percent):
    # Integer comparison, no rounded percentages: score < percent% <=> half*100 < 2*games*percent.
    return half * 100 < 2 * games * percent


def _link(previous, report, used):
    """A later block must share settings and both bot arms and use only fresh maps."""
    same_conditions(previous, report)
    require(effective_budgets(previous) == effective_budgets(report),
            'Effective per-arm search budgets changed between blocks')
    same_bot(previous, 'A', report, 'A')
    same_bot(previous, 'B', report, 'B')
    fresh(report, used)
    used.update(report['maps'])


def assess(block1, block2=None, block3=None, boss=None):
    # Some retained callers parse through the old reader before invoking assess directly.
    # Validate here too so they cannot bypass prospective per-arm timing checks.
    for report in (block1, block2, block3):
        if report is not None:
            effective_budgets(report)
    require(block1['data']['games'] == BLOCK_GAMES[0], 'Expected exactly 300 games in block 1')
    result = {
        'policy': POLICY,
        'confidenceRole': 'descriptive only; no overall 95% guarantee',
        'interimLooks': 'futility only: an interim can reject, never promote early',
        'processReadiness': 'not assessed; exact-artifact process and engine checks remain required',
        'sourceIdentity': 'local executable identity is not assessed here',
        'priorLocalExperimentFreshness': 'not assessed here; prospective exclusions remain required',
        'crossDomainFreshness': 'not assessed: local Protocol.initBlock and CG global.graphics use different hash schemes; the pinned boss pool is intentionally reused and external setup must exclude it from local blocks',
        'promotion': False,
        'effectiveSearchBudgetsMs': effective_budgets(block1),
        'searchBudgetEvidence': 'explicit per arm' if ARM_BUDGET_FIELDS[0] in block1['data']
                               else 'legacy CG baseline inferred; no override',
    }
    inputs = [block1]
    used = set(block1['maps'])
    half, games, score = _pool(inputs)
    result['blockScores'] = [block1['score']]
    result['pooledGames'] = games
    result['pooledScore'] = score

    if _below(half, games, FUTILITY_PERCENT):
        require(block2 is None and block3 is None and boss is None,
                'Continuation supplied after the 300-game futility stop')
        result.update(directStage='STOP_FUTILITY_300', strengthStatus='NOT_PASSED',
                      bossStage='SKIPPED_LOCAL_FAILURE', bossUsage='NOT_STARTED',
                      reports=[compact(block1)])
        return result

    if block2 is None:
        require(block3 is None and boss is None,
                'Block 3 or a boss batch supplied before the required block 2')
        result.update(directStage='NEEDS_BLOCK2_500', strengthStatus='NEEDS_LOCAL_1000',
                      bossStage='NOT_STARTED', bossUsage='NOT_STARTED', reports=[compact(block1)])
        return result

    require(block2['data']['games'] == BLOCK_GAMES[1], 'Expected exactly 200 games in block 2')
    _link(block1, block2, used)
    inputs.append(block2)
    half, games, score = _pool(inputs)
    result['blockScores'].append(block2['score'])
    result['pooledGames'] = games
    result['pooledScore'] = score

    if _below(half, games, FUTILITY_PERCENT):
        require(block3 is None and boss is None,
                'Continuation supplied after the 500-game futility stop')
        result.update(directStage='STOP_FUTILITY_500', strengthStatus='NOT_PASSED',
                      bossStage='SKIPPED_LOCAL_FAILURE', bossUsage='NOT_STARTED',
                      reports=[compact(report) for report in inputs])
        return result

    if block3 is None:
        result.update(directStage='NEEDS_BLOCK3_1000', strengthStatus='NEEDS_LOCAL_1000')
        _attach_boss(result, boss, block1, pending=True)
        result['reports'] = [compact(report) for report in inputs]
        return result

    require(block3['data']['games'] == BLOCK_GAMES[2], 'Expected exactly 500 games in block 3')
    _link(block1, block3, used)
    inputs.append(block3)
    half, games, score = _pool(inputs)
    result['blockScores'].append(block3['score'])
    result['pooledGames'] = games
    result['pooledScore'] = score
    require(games == PASS_GAMES, 'Pooled sample must total exactly 1000 games')
    local_pass = not _below(half, games, PASS_PERCENT)
    result['directStage'] = 'PASS_1000' if local_pass else 'NOT_PASSED'

    _attach_boss(result, boss, block1, pending=False)
    if not local_pass:
        result['strengthStatus'] = 'NOT_PASSED'
        result['bossUsage'] = 'RETAINED_NONPROMOTING_LOCAL_FAILURE' if boss is not None else 'SKIPPED_LOCAL_FAILURE'
    elif boss is None or not boss['complete']:
        result['strengthStatus'] = 'NEEDS_BOSS_CACHED_40'
        result['bossUsage'] = 'INCOMPLETE' if boss is not None else 'NOT_STARTED'
    else:
        result['strengthStatus'] = boss['bossStage']
        result['bossUsage'] = 'ASSESSED_AFTER_LOCAL_PASS'
        result['promotion'] = boss['bossStage'] == 'PASS'
    result['reports'] = [compact(report) for report in inputs]
    return result


def _attach_boss(result, boss, block1, pending):
    if boss is None:
        result['bossStage'] = 'NOT_STARTED'
        if pending:
            result['bossUsage'] = 'NOT_STARTED'
        return
    boss, alias = aliased_control(boss, block1)
    result['boss'] = checked_boss(boss, block1)
    if alias is not None:
        result['bossControlAlias'] = alias
    result['bossStage'] = boss['bossStage']
    if boss['complete']:
        result['bossScoreDifference'] = (boss['candidate']['halfWins'] - boss['control']['halfWins']) / (2 * BOSS_GAMES)
    if pending:
        result['bossUsage'] = 'RETAINED_PENDING_LOCAL_1000'


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--block1', required=True, type=Path)
    parser.add_argument('--block2', type=Path)
    parser.add_argument('--block3', type=Path)
    parser.add_argument('--boss-plan', type=Path)
    parser.add_argument('--out', type=Path)
    args = parser.parse_args(argv)
    try:
        boss = None
        if args.boss_plan:
            from boss_cached import assess_cached
            boss = assess_cached(args.boss_plan)
        result = assess(read_report(args.block1, BLOCK_GAMES[0]),
                        read_report(args.block2, BLOCK_GAMES[1]) if args.block2 else None,
                        read_report(args.block3, BLOCK_GAMES[2]) if args.block3 else None, boss)
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
