# Arena refresh of v12 — 147 more games, harder opponents, the gap is in turns 10–20 (2026-10-27)

The user's pick ("Давай арену") after `threat-weight-v1` closed the cheap screened levers. `gradlew cgFetch -Pplayer=aabzac
-Plimit=200` saved 150 new replays; **the live entry is now puzzle agent 6814073**, whose first-turn marker is
`v12 | BTK_BUILD_V1 strategy=current build=ce1d3a3b… config=d7803b42…` — the selected v12 artifact (`build/submit-v12`),
re-uploaded by the user after agent 6813957 (the first v12 upload, 32W/18L on its first 50). Leaderboard on 2026-10-27:
**rank 316, score 29.40**. `gradlew cgAnalyze -Pagent=6814073` validated 147 games, 0 failed turns
(`docs/evidence/arena-2026-10-27-v12-agent-6814073.json`, trace stripped; tables in `docs/evidence/arena-refresh-v12/`).

## Record

| | first upload, 6813957 (50) | re-upload, 6814073 (147) |
|---|---|---|
| record | 32-18 (64.0 %) | **89-58 (60.5 %)** |
| opponents' arena score, median (range) | 25.1 (22.4–26.6) | **27.2** (5.7–30.9) |
| vs opponents scored ≥ 28 | — (none) | **26-23 (53 %)**, n = 49 |
| vs 26–28 | 1-5 | **34-29 (54 %)**, n = 63 |
| vs 24–26 | 23-11 | 16-6 |
| vs < 24 | 8-2 | 13-0 |
| tall maps (h ≥ 19) / 30×20 / small | 14-6 / 6-1 / 18-12 | 22-18 / 14-9 / 67-40 |
| seat P0 / P1 | 17-9 / 15-9 | 43-32 / 46-26 |
| repeat opponents | hannahlenk 3-1, FREELOADER 2-2 | **Ando_Riccardo 14-16 (score 29.2)**, mattox 4-1, YahyaDS 4-0, luigi_gelato 3-1, TheMagicShop 3-2, AIE4Enjoyer / Online13 / DmitryYudakov 2-3, Murat_Eroglu 2-2 |

The rating rose, so the matchmaker now sends the field around score 27–29: v12 holds **53–54 %** against it and wins
everything below 24. 62 distinct opponents; Ando_Riccardo alone is 30 games.

## Where the losses happen (147 games)

| per game | losses (58): v12 / opponent | wins (89): v12 / opponent |
|---|---|---|
| final score | 2,568 / 3,250 | 2,766 / 1,945 |
| paint spent / cells placed | 200 / 153 · 162 / 126 | 168 / 135 · 136 / 109 |
| points per paint | **12.6 / 21.4** | 15.3 / 13.8 |
| first income turn | 3.4 / 2.7 | 3.5 / 2.2 |
| income per turn at turn 10 / 20 / 30 | **28 / 72 / 65 vs 25 / 96 / 90** | 29 / 71 / 76 vs 21 / 59 / 52 |
| game length | 78.5 turns | 59.3 turns |
| losses led past the midpoint / never led | 7 of 58 / 12 of 58 | |

Phase breakdown (`arena-refresh-v12-phase.txt`), turns 10–20, means per game:

| | income gained, v12 / opponent | cells placed | own tracks inked | value of our disrupts / theirs (score swing) |
|---|---|---|---|---|
| losses (58) | **+44 / +71** | 24.8 / 25.6 | 8.7 / 8.6 | +71 / −50 |
| wins (88) | +43 / +38 | 25.5 / 24.5 | 8.2 / 9.6 | +42 / −71 |
| vs Ando_Riccardo, lost (16) | +30 / +60 | 24.3 / 26.2 | 9.1 / 8.6 | +61 / −65 |
| vs Ando_Riccardo, won (14) | +36 / +60 | 25.4 / 25.8 | 9.3 / 10.8 | +48 / −72 |

1. **Turn 10 is level; turns 10–20 decide.** In the first 50 games the opponents' income lead was visible at turn 10; in
   this harder sample both sides reach turn 10 at the same income (28 vs 25 in the losses) and the losses open a 27-point
   income gap in the next ten turns. 51 of 58 losses were never led past the midpoint.
2. **The gap is construction productivity, not sabotage.** Over turns 10–20 the opponent places the same number of
   cells (25.6 vs 24.8) and loses the same number of tracks to ink (8.6 vs 8.7); its cells add 71 income per turn, ours
   44. Our disrupts are worth *more* in the losses (+71) than in the wins (+42). This is `arena-losses-v12`'s diagnosis
   (multiplicity, earning delay) on three times the sample, pinned to the middle game.
3. **Ando_Riccardo** builds +60 income in turns 10–20 in every game against us, won or lost; we beat him when our own
   middle game reaches his pace (+36 and a later collapse of his income), not by stopping his.

## Reading

v12 is a stable ~60 % entry that holds ~54 % against the field at score 27–29; its losses there are middle-game
construction races. The two construction candidates aimed at exactly this (corridor routes: multiplicity; quick
completions: earning delay) did not replicate on the proxy and lost their live boss sessions. The live boss is the
policy's arbiter, but the losses are to arena players who build better than the boss (the boss beats v12 ~45 % of the
time on the live sessions; the arena's 27–29 field beats it ~46 %). What a next construction candidate needs is a
sharper picture of *what* the field builds in turns 10–20 — which pairs, how many connections a completion activates,
whether they extend live networks or start new ones — measured on these 147 games and the 30 Ando games
(`cgCellStudy`, `EndgameStudy`), and then a mechanism that copies it, screened under step 1a.
