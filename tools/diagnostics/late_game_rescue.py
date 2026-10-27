"""Summarise ``btk.tools.InkRescueStudy`` rows by side, outcome and game third (late-game-v1, 2026-10-27).

risk rows: an earning connection crosses a region one disrupt from ink; "covered" = the connection would still pay the
player after the worst such ink (a bypass with the player's tracks already exists).
hit rows: a region of an earning connection was inked; "rescued" = the pair still pays the player after the turn;
"prepared" = the rescuing path holds tracks the player placed before that turn.

    python tools/diagnostics/late_game_rescue.py build/analysis/late-game/v12-rescue.csv build/analysis/agent-6814073.json --label v12
"""
import argparse
import json
from pathlib import Path

import pandas as pd


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('rows', type=Path)
    ap.add_argument('report', type=Path)
    ap.add_argument('--label', default='agent')
    args = ap.parse_args()
    games = json.loads(args.report.read_text(encoding='utf-8'))['games']
    outcome = {int(g['gameId']): g['outcome'] for g in games}
    turns = {int(g['gameId']): g['turns'] for g in games}
    df = pd.read_csv(args.rows)
    df = df[df.game.isin(outcome)].copy()
    df['outcome'] = df.game.map(outcome)
    df['third'] = (3 * df.turn // df.gameTurns).clip(upper=2) + 1
    df['side'] = df.ours.map({1: args.label, 0: 'opponent'})
    for oc in ('win', 'loss'):
        gids = [g for g, o in outcome.items() if o == oc]
        if not gids:
            continue
        tt = {1: 0, 2: 0, 3: 0}
        for g in gids:
            n = turns[g]
            for t in range(n):
                tt[min(2, 3 * t // n) + 1] += 1
        print(f'=== {args.label} {oc}s: {len(gids)} games ===')
        for third in (1, 2, 3, 'all'):
            sel = df[(df.outcome == oc)] if third == 'all' else df[(df.outcome == oc) & (df.third == third)]
            gt = sum(tt.values()) if third == 'all' else tt[third]
            print(f'  third {third} ({gt} game-turns)')
            for side in (args.label, 'opponent'):
                d = sel[sel.side == side]
                risk = d[d.kind == 'risk']
                hit = d[d.kind == 'hit']
                cov = (risk.ownAfter > 0).mean() if len(risk) else float('nan')
                cov_w = (risk.ownAfter.clip(upper=risk.ownPre)).sum() / max(1, risk.ownPre.sum())
                res = (hit.ownAfter > 0).mean() if len(hit) else float('nan')
                kept = hit.ownAfter.clip(upper=hit.ownPre).sum() / max(1, hit.ownPre.sum())
                prep = (hit.prepared > 0).sum() / max(1, (hit.ownAfter > 0).sum())
                print(f'    {side:9s} at-risk pair-turns/turn {len(risk) / max(1, gt):4.2f} (tracks at stake {risk.ownPre.sum() / max(1, gt):5.1f}/turn), '
                      f'covered {100 * cov:3.0f}% (tracks kept {100 * cov_w:3.0f}%) | hits/turn {len(hit) / max(1, gt):4.2f} '
                      f'(tracks {hit.ownPre.sum() / max(1, gt):5.1f}/turn), rescued {100 * res:3.0f}%, tracks kept {100 * kept:3.0f}%, '
                      f'prepared {100 * prep:3.0f}% of rescues')


if __name__ == '__main__':
    main()
