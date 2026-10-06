# indrill-forum-v1 — hand readout of the rank-1 post

Source: [Indrill, ranked 1st, 21 September 2026](https://forum.codingame.com/t/summer-challenge-2026-feedback-strategies/208574/5).
Requested 6 October 2026: write the described strategy from a clean sheet, then bench it.
This is not his unpublished KataGo/BTR network, weights, or training pipeline. Earlier
learned prototypes `indrill-q-v1` and `indrill-q-v2` stay rejected at 0/300.

## What the post specifies, and what this bot does

The post's inference rule is greedy: no search, no randomness, one inked zone and one
construction triplet. The observation is a product of

- entry costs: the normal map, and the same map plus `2 * instability` on non-town cells;
- cell roles on each oriented town link: best (`AB + BC - AC = 0`), good
  (`AB + BC - AC <= cost(B)`), and one priority path (N/E/S/W DFS over best cells);
- aggregates: count, yield (`me - foe`), stability (`4 - ink`, min), prospect
  (stability times yield, sum), urgency (all-in turns to finish, min).

`IndrillFeatures` already computes that product. This bot does not add channels and does
not train. It reads those channels with weights frozen before any strength game:

- cell value = `4 * path prospect + 1.5 * good prospect + 0.25 * best prospect`,
  times `1.2 - 0.45 * urgency`;
- a cell touching an existing connector is multiplied by 3, a cell touching such a
  frontier by 1.7, any other suggested cell by 0.35;
- the build is the exact paint-3 knapsack (three plains, a river plus a plain, or one
  mountain), and non-positive cells are left unbuilt;
- a region's ink value is the mean, over every cell of that region, of opponent tracks
  (`+1`), own tracks (`-0.5`), opponent-favoring route yield, and `0.35` times positive
  good/path prospect, then multiplied by `1 + instability`. The maximum positive legal
  region is inked. Town regions and inked regions are illegal.

If the chosen disrupt would ink the region this turn, cells in that region are removed
from the knapsack. That is the only joint check. `v09` / `current` are not modified.
The bot id is `indrill-forum-v1`. It is local-only and is not on the default submission.

## Predeclared comparison

Practical-v4 against frozen v09, one run, no tuning:

```powershell
python tools/run_practical_v4.py --spec indrill-forum-v1 --control v09 --name indrill-forum-v1 --seed 9300000
```

300 / +200 / +500 paired games, two workers, CG 486/36 ms budgets, futility stop below
50% at 300 or 500, pass only at 55% of 1000. A local failure does not start a boss batch.

## Result (6 October 2026)

**Rejected locally: 14 wins, 286 losses, 0 draws (4.7%).** The fixed v4 runner stopped
at `STOP_FUTILITY_300`. Blocks 2 and 3 and the boss stage did not run. v09 stays selected.

150 fresh paired maps, seeds 9300000–9300149, both seats, 19,851 prior map hashes excluded.
Zero game errors, zero slow moves. Later-turn maxima 32 ms (candidate) and 45 ms (v09).
Mean score difference −2791. The candidate placed on 50.2% of turns and spent 1.49 paint
per turn; v09 placed on 95.9% and spent 2.83. The candidate disrupted on 99.8% of turns,
v09 on 71.0%. 220 of 300 games closed early. Seat split 7/150 and 7/150.

The published features are computable, and the greedy ink/build skeleton matches the post.
A fixed linear readout of those channels does not replace the unpublished network: most
suggested cells stay non-positive, so the knapsack leaves paint unused. No retune, no
further local games, no arena upload.
