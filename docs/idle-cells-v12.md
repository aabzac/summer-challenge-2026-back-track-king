# Idle cells — why 35–45 % of our accepted cells never earn (v12's first 50 arena games, 2026-10-25)

> **Erratum (2026-10-26, `docs/arena-losses-v12.md`).** The "v12 vs opponents" split below used the per-cell table's
> `leader` column, which is the index of our *seat* in each game, not a side flag; the two columns are therefore
> "games as P1" vs "games as P0", both sides mixed. The correct split: **v12 leaves 44.9 % of its cells idle
> (3,439 of 7,657), the opponents 26.9 % (1,614 of 6,005)**; the excess is 1,906 cells kept to the end on no active
> connection against the opponents' 365. The conclusions "we are not worse than the field" and "the controllable
> waste is ~10 cells per game" are withdrawn; what stands is that inked idle cells are placed into stable regions on
> both sides and that collisions are symmetric. Verdicts of the screens that followed (`fill-off-v1`,
> `collision-avoidance-v1`, `end-forecast-v1`) are unaffected: they were measured against the proxy and `current`.

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

## Next step taken — `fill-off-v1` (2026-10-25 night, `docs/evidence/fill-off-v1/`)

`current?USE_ROLLOUT_FILL=0` vs boss-v2 with `current` (= v12) as the same-map control and head-to-head, fresh
pool 1005000, 300 paired games per cell (`tools/boss_proxy/run_fill_screen.sh`):

| cell | rate | W-L-D | maps h < 19 | h ≥ 19 |
|---|---|---|---|---|
| `current` (v12) vs boss-v2 (control) | **78.5 %** | 235-64-1 | 73.4 % | 91.7 % |
| fill off vs boss-v2 | 74.7 % | 224-76-0 | 72.2 % | 81.0 % |
| fill off vs `current` | **43.0 %** | 129-171-0 | 37.0 % | 58.3 % |

Paired vs the control against the proxy: **−3.8 ± 6.9 pp** (34/41/75 maps). Zero errors; the move-time maxima
(74–97 ms, 1–3 moves per 300 games on both sides) are machine hiccups during this run.

**Verdict: rejected, the fill stays.** Switching the leftover-paint fill off loses against the proxy
(tall maps 81 % vs 92 %) and loses head-to-head (43 %, small maps 37 %). So the fill cells are not the
purposeless bucket: cells that lie on no route hypothesis *at placement* earn later or protect rails, and the
"≥ 10 turns idle, no route" group is dominated by something else (most likely route segments of pairs that
were later connected along another path, and network roots). The idle-cell line therefore leaves two
candidates for later, both needing code: **collision avoidance** in the real turn (6.9 % of our paint is
neutralised; the rollout already predicts the opponent's first-turn cells from its portfolio and could
penalise them in our own root choice) and a **game-end forecast** for the last third (76 % idle on both
sides). Placement-time instability rules and the fill are closed as levers.
