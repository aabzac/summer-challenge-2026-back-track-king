"""What each side builds in each third of the game, and what that paint earns (late-game-v1, 2026-10-27).

Reads a ``CgCellStudy`` cells.csv (one row per placed track, both players) and the matching ``CgPlayerStudy``
report (for each game's outcome). A cell's third is ``min(2, 3 * turn // gameTurns)``. Classes at placement,
first match wins:

* ``bypass``  - on the shortest unstable-region-avoiding path of an own active pair that crosses an unstable region;
* ``steal``   - on an own-only path not longer than an active pair's current connection (take-over);
* ``complete``- on (or within +1 paint of) a cheapest completion of an inactive pair;
* ``extend``  - adjacent to an own track on an active path;
* ``filler``  - none of these.

    python tools/diagnostics/late_game_cells.py build/analysis/late-game/indrill/cells.csv \
        build/analysis/late-game/indrill-agent.json --label Indrill
"""
import argparse
import json
from pathlib import Path

import pandas as pd

CLASSES = ('bypass', 'steal', 'complete', 'extend', 'filler')


def classify(df):
    cls = pd.Series('filler', index=df.index)
    cls[df.onExtend == 1] = 'extend'
    complete = (df.onCompletionAny == 1) | (df.onCompletionOwn == 1) | (df.onShortestCells == 1) | df.slackAny.between(0, 1)
    cls[complete] = 'complete'
    cls[df.onSteal == 1] = 'steal'
    cls[df.onBypass == 1] = 'bypass'
    return cls


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('cells', type=Path)
    ap.add_argument('report', type=Path)
    ap.add_argument('--label', default='agent')
    ap.add_argument('--out', type=Path)
    args = ap.parse_args()
    games = json.loads(args.report.read_text(encoding='utf-8'))['games']
    outcome = {int(g['gameId']): g['outcome'] for g in games}
    df = pd.read_csv(args.cells)
    df = df[df.game.isin(outcome)].copy()
    df['outcome'] = df.game.map(outcome)
    df['side'] = (df.player == df.leader).map({True: args.label, False: 'opponent'})
    df['third'] = (3 * df.turn // df.gameTurns).clip(upper=2)
    df['cls'] = classify(df)
    df['earned'] = df.firstEarnTurn >= 0
    df['lostUnearned'] = (df.lostTurn >= 0) & ~df.earned
    # turns per third per game, for paint per turn
    meta = df.groupby('game').agg(gameTurns=('gameTurns', 'first'), outcome=('outcome', 'first'))
    third_turns = {}
    for oc, g in meta.groupby('outcome'):
        tt = [0, 0, 0]
        for n in g.gameTurns:
            for t in range(n):
                tt[min(2, 3 * t // n)] += 1
        third_turns[oc] = tt
    result = {'label': args.label, 'games': {oc: int((meta.outcome == oc).sum()) for oc in third_turns}, 'cells': {}}
    lines = []
    for oc in ('win', 'loss'):
        if oc not in third_turns:
            continue
        lines.append(f'=== {args.label} {oc}s: {result["games"][oc]} games ===')
        for third in range(3):
            lines.append(f'  third {third + 1} ({third_turns[oc][third]} game-turns)')
            for side in (args.label, 'opponent'):
                d = df[(df.outcome == oc) & (df.third == third) & (df.side == side)]
                paint = d.cost.sum()
                ppt = paint / max(1, third_turns[oc][third])
                pts = d.totalPoints.sum()
                row = {'paintPerTurn': round(ppt, 2), 'cellsPerTurn': round(len(d) / max(1, third_turns[oc][third]), 2),
                       'earns': round(d.earned.mean(), 3) if len(d) else None,
                       'lostUnearned': round(d.lostUnearned.mean(), 3) if len(d) else None,
                       'pointsPerPaint': round(pts / max(1, paint), 1), 'byClass': {}}
                parts = []
                for c in CLASSES:
                    dc = d[d.cls == c]
                    share = dc.cost.sum() / max(1, paint)
                    e = dc.earned.mean() if len(dc) else float('nan')
                    ppp = dc.totalPoints.sum() / max(1, dc.cost.sum())
                    row['byClass'][c] = {'paintShare': round(share, 3), 'earns': None if len(dc) == 0 else round(e, 3),
                                         'pointsPerPaint': round(ppp, 1)}
                    parts.append(f'{c} {100 * share:4.1f}% (earns {100 * e:3.0f}%, {ppp:4.1f} pts/paint)')
                earning = d[d.earned]
                firsts = earning[['pairsInactiveAtT', 'pairsStolen', 'pairsBypassed', 'pairsOther']].sum()
                tot = max(1, firsts.sum())
                row['firstEarnPairs'] = {k: round(v / tot, 3) for k, v in firsts.items()}
                result['cells'].setdefault(oc, {}).setdefault(str(third + 1), {})[side] = row
                lines.append(f'    {side:9s} paint/turn {ppt:4.2f}, earns {100 * row["earns"]:3.0f}%, lost unearned '
                             f'{100 * row["lostUnearned"]:3.0f}%, {row["pointsPerPaint"]:4.1f} pts/paint')
                lines.append('      ' + ' | '.join(parts))
                lines.append('      pairs at first income: new %.0f%%, taken over %.0f%%, bypassed %.0f%%, other %.0f%%'
                             % tuple(100 * firsts[k] / tot for k in ('pairsInactiveAtT', 'pairsStolen', 'pairsBypassed', 'pairsOther')))
    print('\n'.join(lines))
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(result, indent=1), encoding='utf-8')


if __name__ == '__main__':
    main()
