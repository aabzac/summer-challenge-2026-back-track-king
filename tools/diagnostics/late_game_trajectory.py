"""Score trajectory over the game for one agent's validated games (late-game-v1, 2026-10-27).

Reads a CgPlayerStudy report (``gradlew cgAnalyze`` / ``btk.tools.CgPlayerStudy``) and prints, for wins and
losses separately, the score difference and the share of the game's final points scored at fixed fractions of
the game length, how many games change their leader after two thirds of the game, and the paint each side
spends per turn in each third.

    python tools/diagnostics/late_game_trajectory.py build/analysis/agent-6814073.json --label v12
"""
import argparse
import json
import statistics as st
from pathlib import Path

FRACTIONS = (0.2, 1 / 3, 0.5, 2 / 3, 0.8, 1.0)


def at(game, f):
    turns = game['turns']
    t = min(turns - 1, int(f * turns))
    by_turn = {x['turn']: x for x in game['trace']}
    return by_turn.get(t)


def paint_by_third(game, side):
    turns = game['turns']
    out = [0.0, 0.0, 0.0]
    count = [0, 0, 0]
    for x in game['trace']:
        third = min(2, int(3 * x['turn'] / turns))
        count[third] += 1
        out[third] += x.get('paint', [0, 0])[side] if isinstance(x.get('paint'), list) else 0
    return [o / c if c else 0 for o, c in zip(out, count)]


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('report', type=Path)
    ap.add_argument('--label', default='agent')
    args = ap.parse_args()
    games = json.loads(args.report.read_text(encoding='utf-8'))['games']
    print(f'{args.label}: {len(games)} games')
    for outcome in ('win', 'loss'):
        gs = [g for g in games if g['outcome'] == outcome]
        if not gs:
            continue
        print(f'  {outcome}: {len(gs)} games, median length {st.median(g["turns"] for g in gs)}')
        for f in FRACTIONS:
            diffs, shares, own_shares = [], [], []
            for g in gs:
                x = at(g, f)
                if x is None:
                    continue
                me = g['me']
                sc = x['score']
                diffs.append(sc[me] - sc[1 - me])
                total = g['scores'][me] + g['scores'][1 - me]
                shares.append((sc[me] + sc[1 - me]) / max(1, total))
            print('    at %.2f L: median diff %6.0f, ahead %3d/%d, share of final points %.2f'
                  % (f, st.median(diffs), sum(1 for d in diffs if d > 0), len(diffs), st.median(shares)))
        flips = 0
        for g in gs:
            x = at(g, 2 / 3)
            if x is None:
                continue
            me = g['me']
            ahead = x['score'][me] - x['score'][1 - me] > 0
            if ahead != (outcome == 'win'):
                flips += 1
        print(f'    leader changes after 2/3 L: {flips}/{len(gs)}')


if __name__ == '__main__':
    main()
