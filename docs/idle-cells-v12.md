# Idle cells — why 35–45 % of our accepted cells never earn (v12's first 50 arena games, 2026-10-25)

Question from the user's "давай диагностику": what happens to the accepted cells that never earn a point.
Source: the 50 games of puzzle agent 6813957 (v12, `docs/evidence/arena-2026-10-25-v12-agent-6813957.json`),
replayed through `btk.core` by `gradlew cgCellStudy -Ppath=build/analysis/idle-v12/replays -Pplayer=aabzac`
(one row per placed track of either player: region instability at placement, route hypotheses the cell lay on,
first earning turn, loss turn, neutralisation) and summarised by a scratch script (`idle-summary.txt` in the
evidence directory). "Idle" = never on an active connection while owned. The share differs from `cgAnalyze`'s
"accepted cells never earning" (44.9 %) because that figure counts accepted actions, this one surviving tracks.

## What the 50 games say

| | v12 (6,656 cells, 7,910 paint) | opponents (7,006 cells) |
|---|---|---|
| idle cells | **2,369 = 35.6 %** (35.5 % of paint) | 2,684 = 38.3 % |
| …lost before earning | 1,276 (54 % of idle): **820 inked, 456 neutralised** (same-cell collisions) | 1,506: 1,072 inked, 434 neutralised |
| …kept to the end, never active | 1,093 (46 % of idle) | 1,178 |
| idle share by phase of the game | early 17 %, middle 34 %, **last third 76 %** | 17 % / 43 % / 79 % |
| idle share by map | 21×14 44 %, 23×15 34 %, 24×16 34 %, 26×17 28 %, 27×18 37 %, 29×19 39 %, 30×20 32 % | |
| idle share in won / lost games | 35 % / 36 % | |

The inked-before-earning cells (820): **742 were placed in regions at instability 0**, 43 at 1, 13 at 2, 22 at
3; the ink came a median of ~6 turns later (257 cells lasted ≥ 12 turns before the ink); in 300 of the 820
the opponent already had tracks in the region. The cells kept idle to the end (1,093): 734 had ≥ 10 turns of
game left when placed, and of those **508 lay on no route hypothesis at placement** (not completion, steal,
bypass or extension of a pair), 131 on an extension, 50 on a completion route; 723 of the 734 were placed at
instability 0. Overall we place 127 of 5,230 non-town cells into regions at instability ≥ 2 (2.4 %).

## Reading

1. **We are not worse than the field**: 35.6 % vs the opponents' 38.3 %; idle paint is structural in this game
   (late-game construction cannot complete, collisions neutralise cells, regions get inked).
2. **The exposure that inks our cells is created after placement.** 90 % of the inked idle cells were placed at
   instability 0; the region was hit *because* we built there (the boss-like chooser scores a region by the
   opponent's cell count, +0.78 per cell). Rules that look at instability at placement time
   (`UNSTABLE_COST`, "do not build at instability ≥ 2") cannot touch this — which is exactly what today's
   `unstable-cost-v1` screen found (+0.3 / +2.0 pp, parked). v12's value-side discount addresses the part that
   *is* visible (routes through already-unstable regions) and that was worth +3.7 pp.
3. **The one controllable waste is the ~500 cells per 50 games (≈ 10 per game, ≈ 3 turns of paint) placed with
   no route purpose and ≥ 10 turns left.** The candidates for that bucket are the leftover-paint fill
   (`USE_ROLLOUT_FILL`, selected since v09 on a mirror result: stable cells next to own rails) and the
   single-cell network roots; both add own cells to regions, i.e. ink magnets for a boss-like opponent, without
   a route behind them. The study cannot tell fill cells from route cells, so the direct test is a screen with
   the fill off.
4. **Collisions** (456 neutralised cells, 6.9 % of our paint; the opponents lose the same) are the second
   controllable bucket: the rollout predicts the opponent's construction from its reply portfolio but the real
   turn does not avoid the cells it predicts for this turn. A candidate for later (needs code).
5. The last third of a game is 76 % idle for both sides; a "cannot complete before the end" rule would need a
   game-end forecast the proposals do not have (they only cap the earning horizon). Low priority.

## Next step taken

`fill-off-v1`: `current?USE_ROLLOUT_FILL=0` vs boss-v2 with `current` (= v12) as the same-map control and
head-to-head, fresh pool 1005000 (`tools/boss_proxy/run_fill_screen.sh`); a parameter change, no code.
