# pduhard-beam-v1 — beam from the 3rd-place post-mortem

Source: [Pduhard, 3rd, Summer Challenge 2026](https://github.com/Pduhard/back-track-king-postmortem).
Requested 6 October 2026: build a strategy from that description, then bench it.
The published bot that finished 3rd is a self-play PPO network plus a short value search.
The weights, the 50,000 training iterations and the distilled network are not in the post.
This candidate is the beam described before that network, scored with the reward the post
does specify. It is not a claim to reproduce the submitted bot.

## What the post specifies, and what this bot does

The early search:

- Dijkstra cheapest paths mark the cells worth placing on.
- One pass keeps the N best `DISRUPT` regions and the M best `PLACE_TRACK` cells.
- Each disrupt is crossed with the best cell, then the next cells that still fit in 3 paint.
- Each combination is scored by the evaluation of the state it leads to.
- A beam keeps the K best combinations. The opponent answers with the same greedy pack.
- One more turn is searched while time remains.

The reward the post states, used here as that evaluation:

- income gap, my income minus the opponent's, with nothing extra for the act of placing,
  connecting or inking;
- over the turns still left, plus the points already banked;
- at a terminal state, +/-30 for a win or a loss.

Two heuristics stand in for the "few heuristics" the post does not list. They are frozen:

- a cell on an unfinished Dijkstra path scores `0.35 * (our future cells - opponent cells)
  * turns after the path would be finished / empty cells`, reduced to a quarter in a
  non-town region that is already instability 2 or more;
- a region is shortlisted by the path income it would swing once inked, plus
  `0.2 * (opponent tracks - 1.5 * our tracks)` capped at eight turns. Only the path-income
  part enters the state value, so an ink is taken when it changes points and not kept as a
  threat. The track term is a 0.25 tie-break, below one point of real income.

The opponent's greedy plan is resolved on the same state as ours, because placements are
simultaneous. N=4, M=8, K=4, at most 18 plans. The second turn uses at most eight nearby
unfinished pairs. `v09` / `current` are not modified. The bot id is `pduhard-beam-v1`.
It is local-only and is not on the default submission.

## Predeclared comparison

Practical-v4 against frozen v09, one run, no tuning:

```powershell
python tools/run_practical_v4.py --spec pduhard-beam-v1 --control v09 --name pduhard-beam-v1 --seed 9400000
```

300 / +200 / +500 paired games, two workers, CG 486/36 ms budgets, futility stop below
50% at 300 or 500, pass only at 55% of 1000. A local failure does not start a boss batch.

## Result (6 October 2026)

**Rejected locally: 2 wins, 298 losses, 0 draws (0.67%).** The fixed v4 runner stopped
at `STOP_FUTILITY_300`. Blocks 2 and 3 and the boss stage did not run. v09 stays selected.

150 fresh paired maps, seeds 9400000–9400149, both seats, 20,003 prior map hashes excluded.
Zero game errors. Candidate slow moves 0, later-turn maximum 47 ms; v09 recorded 3 slow
moves, maximum 99 ms. Mean score difference −3766. Seat split 1/150 and 1/150.
150 of 300 games closed early.

The candidate placed on 30.7% of turns and spent 0.78 paint per turn; v09 placed on
98.7% and spent 2.93. The candidate disrupted on 93.3% of turns, v09 on 43.6%.
A one-to-two-turn income evaluation spends the disrupt, then leaves the paint unused
once a route is no longer an immediate point swing. The post itself says this beam
was going to stall, which is why the submitted bot moved on to self-play PPO.
No retune, no further local games, no arena upload.
