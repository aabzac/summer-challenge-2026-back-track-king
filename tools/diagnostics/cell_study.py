"""Summarise a `gradlew cgCellStudy` export (one row per placed track) for the named player vs opponents.

  python tools/diagnostics/cell_study.py build/analysis/leader-study/cells.csv --label Indrill \
      --out docs/evidence/indrill-cell-study-v1.json

Descriptive only. Route classes come from the Java export (paint-optimal completion slack, own-only and
unstable-avoiding variants, take-over and bypass paths for active pairs); this script only aggregates.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import pandas as pd

PHASES = ([-1, 2, 10, 20, 30, 45, 200], ['0-2', '3-10', '11-20', '21-30', '31-45', '46+'])
FLAGS = ('townRegion', 'adjOwn', 'adjOpp', 'adjTown', 'adjAny', 'onCompletionAny', 'onCompletionOwn',
         'onShortestCells', 'onSteal', 'onBypass', 'onExtend', 'neutralised')


def load(path: Path) -> pd.DataFrame:
    df = pd.read_csv(path)
    for col in FLAGS:
        df[col] = df[col].astype(bool)
    df['named'] = df.player == df.leader
    df['earned'] = df.firstEarnTurn >= 0
    df['lostBefore'] = (~df.earned) & (df.lostTurn >= 0)
    df['phase'] = pd.cut(df.turn, PHASES[0], labels=PHASES[1])
    df['explained'] = (df.slackAny.between(0, 1) | df.slackOwn.between(0, 1) | df.slackSafe.between(0, 1)
                       | (df.stealSlack <= 0) | df.bypassSlack.between(-1, 1))
    return df


def r(x, digits=3):
    return None if pd.isna(x) else round(float(x), digits)


def side(g: pd.DataFrame, turns: int) -> dict:
    e = g[g.earned]
    classes = e[['pairsInactiveAtT', 'pairsStolen', 'pairsBypassed', 'pairsOther']].sum()
    by_phase = g.groupby('phase', observed=True)
    return {
        'cells': int(len(g)), 'cellsPerTurn': r(len(g) / turns), 'paintPerTurn': r(g.cost.sum() / turns),
        'placementRate': r(g.groupby(['game', 'turn']).size().shape[0] / turns),
        'everEarned': r(g.earned.mean()), 'lostBeforeEarning': r(g.lostBefore.mean()), 'neutralised': r(g.neutralised.mean()),
        'pointsPerPaint': r(g.totalPoints.sum() / g.cost.sum(), 2), 'pointsPerEarnedCell': r(e.totalPoints.mean(), 1),
        'meanMaxPairsEarned': r(e.maxPairs.mean(), 2), 'meanLifetimeEarned': r(e.lifetime.mean(), 1),
        'pointsShareFromCellsOn3PlusPairs': r(e[e.maxPairs >= 3].totalPoints.sum() / max(1, e.totalPoints.sum())),
        'townRegion': r(g.townRegion.mean()), 'adjOwn': r(g.adjOwn.mean()), 'meanCost': r(g.cost.mean(), 2),
        'oppInRegion': r((g.oppInRegion > 0).mean()), 'unstableRegion': r((g.instab > 0).mean()),
        'onSingleCheapestRoute': r((g.onCompletionAny | g.onCompletionOwn).mean()),
        'onSomeOptimalRoute': r((g.slackAny == 0).mean()), 'withinOnePaint': r(g.slackAny.between(0, 1).mean()),
        'noInactivePairReachable': r((g.slackAny < 0).mean()),
        'takeOverPath': r((g.stealSlack <= 0).mean()), 'bypassPath': r(g.bypassSlack.between(-1, 1).mean()),
        'explained': r(g.explained.mean()), 'explainedThrough45': r(g[g.turn <= 45].explained.mean()),
        'firstEarnPairClasses': {k: r(v / max(1, classes.sum())) for k, v in classes.items()},
        'earnedBySlackAny': {str(int(k)): r(v) for k, v in g.groupby(g.slackAny.clip(upper=6)).earned.mean().items()},
        'pointsPerPaintBySlackAny': {str(int(k)): r(v, 2) for k, v in
                                     (g.groupby(g.slackAny.clip(upper=6)).totalPoints.sum()
                                      / g.groupby(g.slackAny.clip(upper=6)).cost.sum()).items()},
        'byPhase': {str(k): {'cells': int(len(s)), 'earned': r(s.earned.mean()), 'paintPerTurn': None,
                             'onSomeOptimalRoute': r((s.slackAny == 0).mean()), 'explained': r(s.explained.mean()),
                             'townRegion': r(s.townRegion.mean())} for k, s in by_phase},
    }


def paint_by_phase(df: pd.DataFrame, games: pd.Series) -> dict:
    rows = []
    for game, length in games.items():
        sub = df[df.game == game]
        for t in range(int(length)):
            at = sub[sub.turn == t]
            rows.append((t, at[at.named].cost.sum(), at[~at.named].cost.sum()))
    tt = pd.DataFrame(rows, columns=['turn', 'named', 'opponents'])
    ph = pd.cut(tt.turn, PHASES[0], labels=PHASES[1])
    out = {}
    for k, s in tt.groupby(ph, observed=True):
        out[str(k)] = {'named': {'paintPerTurn': r(s.named.mean(), 2), 'placementRate': r((s.named > 0).mean()), 'fullPaint': r((s.named >= 3).mean())},
                       'opponents': {'paintPerTurn': r(s.opponents.mean(), 2), 'placementRate': r((s.opponents > 0).mean()), 'fullPaint': r((s.opponents >= 3).mean())}}
    out['overall'] = {'named': {'paintPerTurn': r(tt.named.mean(), 2), 'placementRate': r((tt.named > 0).mean())},
                      'opponents': {'paintPerTurn': r(tt.opponents.mean(), 2), 'placementRate': r((tt.opponents > 0).mean())}}
    return out


def summarise(path: Path, label: str) -> dict:
    df = load(path)
    games = df.groupby('game').gameTurns.first() if 'gameTurns' in df else df.groupby('game').turn.max() + 1
    turns = int(games.sum())
    return {'source': path.as_posix(), 'namedPlayer': label, 'games': int(df.game.nunique()), 'turns': turns,
            'meanGameLength': r(games.mean(), 1),
            'named': side(df[df.named], turns), 'opponents': side(df[~df.named], turns),
            'paintByPhase': paint_by_phase(df, games)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('cells', type=Path)
    parser.add_argument('--label', default='named')
    parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    result = summarise(args.cells, args.label)
    text = json.dumps(result, indent=1)
    if args.out:
        args.out.write_text(text, encoding='utf-8')
        print('->', args.out)
    else:
        print(text)


if __name__ == '__main__':
    main()
