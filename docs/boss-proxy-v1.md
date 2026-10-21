# boss-proxy-v1 — a local imitation of the Silver boss as a screening opponent (2026-10-20)

Stage 0 of the 2026-10-20 plan (claude/strategy-analysis-2026-10-20.md in the Claude project):
make the local bench measure the arena meta before trying new strategy mechanisms. Nothing in this
document changes the selected version or a submission. One default-off flag was added to
`current` (`USE_BOSS_SABOTAGE`, see §5); with the flag off `current` is unchanged.

## 1. Why

The v4 local gate is a v09 self-play mirror (~90-turn games, 64–67 % accepted disruption). The
boss plays 65–72-turn games, disrupts on 88–92 % of turns, holds a target until it inks, never
builds in a region at instability ≥ 2, and inks our construction before it earns. Mechanisms
motivated by boss data cannot show up in the mirror, and 40-game boss stages (±7.5 pp) cannot
confirm them. A validated boss-like local opponent closes that gap for screening; live games
remain the confirmation (`benchmarking-v5.md`).

## 2. Corpus and tools

All canonical boss replays under `replays/cg/boss/**` except `_metadata/`, `raw/`, `unknown/`:
**855 games, 60,440 boss turns** (50 flat v05–v06 era, 44 v07, 481 `current/*` v08–v09 era,
120 `v09/*`, 160 `neural-*`). Boss seat = `agents[].arenaboss.nickname == "Boss 4"`. Every game
replays exactly through `btk.core`.

Tools (local, `btk.tools`, not shipped): `BossIntentStudy` (per boss turn: announced intent
`build a>b` / `steal` / `block` / `fortify`, target, hold/switch, pair ranks, membership of placed
cells in optimal routes under eight cost models, per-pair candidate values; per cell: instability,
region class), `CgLeaderStudy -Pplayer="Boss 4"` (candidate-region features per turn for the
target logit), `BossAgreementStudy` (a bot replayed in the boss seat over the corpus),
`ProxyBehaviorStudy` (local games with the proxy in the boss seat, same tables). Fits:
`tools/boss_proxy/fit_boss_target.py`, `fit_boss_hold.py`, `fit_boss_build.py`; pipeline
`tools/boss_proxy/run_full_study.sh`. Outputs under `build/analysis/boss-full/` (2.5 M candidate rows).

## 3. What the boss does (855 games)

**Announced intents** (its `MESSAGE`): build 51.8 %, steal 11.7 %, fortify 0.9 %, block 0.2 %
(turn 0 only), none 35.4 %. Idle turns are 0.1 % before turn 20, 16 % in 20–45, 67 % in 45–70,
99 % after 70; 86 % of idle turns occur while ahead. On build turns it spends 2.99/3 paint.

**Sabotage.** DISRUPT on 87.7 % of turns (92 % vs v09-class opponents; the rest are late idle
turns). Same target as the previous turn 62.9 %; 64.6 % of switches follow the ink of the previous
target, so the boss holds a region until it inks about 83 % of the time it could. 20,088
commitment decisions: target at instability 0/1/2/3 = 69/12/11/8 %; region holds opponent tracks
95 % (opponent construction 43.5 %, opponent active path 51.3 %, empty/own-only 5.2 %), opponent
placed there within the last two turns 33 %, own tracks present 36 %.

**Construction.** Build turns name a pair; 67 % of them an inactive pair (28 % of those complete
it that turn), 33 % an already active pair (own-only take-over/insurance routes, immediate gain
+1.4 / opponent −2.5). Among inactive pairs the named pair is rank 1 by completion gain/cost in
59 % of turns, by remaining cost in 52 %, by gain in 34 %; the same pair as the previous build in
52 %. Placed cells lie on some paint-optimal route of the named pair under: shared paint cost
39 %, own-only 57 %, **own-only avoiding regions at instability ≥ 2: 61 %** (71 % on inactive
pairs); nothing else explains more. Cells by region instability: 0 → 95.3 %, 1 → 4.7 %, ≥ 2 →
**0.0 %**; town regions 16.6 %, regions shared with the opponent 35.5 %, adjacent to own rails 51 %.
Steals (12 % of turns): +4.4 own / −5.6 opponent income immediately.

## 4. Fits

**Target choice** — conditional logit over 13 candidate-region features at the 20,088 commitment
turns (80/20 split by turn, L2 1e-3): held-out **top-1 51.0 %** (uniform 4.5 %; the Indrill fit
in `indrill-study-v1.md` reached 38 %). Raw per-unit weights:

| feature | weight | | feature | weight |
|---|---|---|---|---|
| opp. income lost if inked (dOpp) | +0.137 | | opp. cells placed in last 2 turns | +0.291 |
| own income lost (dOwn) | −0.112 | | instability already reached (per level) | +0.885 |
| opponent tracks in region | **+0.775** | | opponent own-only route traffic | +0.372 |
| opponent active-path tracks | −0.004 | | adjacent to a town region | +0.368 |
| own tracks in region | −0.283 | | neutral tracks | +0.156 |
| one-hit closure | −3.44 | | log1p(free cells) | +0.148 |

An unfinished opponent track is worth 5.6 income points of the opponent's; the boss inks
construction, not income (`RolloutBot.chooseTarget` values it at 0.4/8 ≈ 0.05).

**Hold/abandon** — hold the committed region unless another region's logit exceeds it by a
margin: margin 1.0 reproduces the observed hold rate (84.5 % vs 83.5 %) with 82 % decision
agreement over 39,376 holdable turns.

**Project choice** — value = gain × earning / (delay + 1) / cost^a × (1 + stickiness·same pair),
gain = exact completed-route income-difference change, routes under the own-only
unstable-avoiding model: top-1 agreement with the announced pair 24 % without stickiness,
**50 % at stickiness 3.0** (a = 0, horizon 24); the boss finishes what it starts.

## 5. The proxies

**`boss-v1`** (`btk.bot.reference.BossBot`, catalog reference, local_only, snapshot
`versions/boss-v1.properties`, constants frozen in `BossWeights`): the fitted sabotage (logit +
hold margin 1.0 + take winning one-hit closures only), construction = the fitted project choice
over own-only unstable-avoiding routes (shared fallback for inactive pairs), full paint while a
profitable project exists, single-cell steals, idle otherwise. ~4 ms per turn.

**`current?USE_BOSS_SABOTAGE=1`** ("boss-v2" below; experiment id `boss-sabotage-v1`): v09's
own search and construction with the real-turn DISRUPT replaced by the same fitted chooser
(`btk.bot.current.BossSabotage`, hold margin `BOSS_HOLD_MARGIN=1.0`), computed on the pre-build
board before route enumeration and used as fallback and final target. The simulated players
inside the rollout still use the old chooser. Costs ~3–5 ms of the 36 ms search budget.
Restricted by `BotFactory` to `current` with the native rollout; hidden from `configuration()`
unless set; frozen snapshots default it off.

## 6. Validation (predeclared in §6 of the first draft; all games fresh generated maps,
CG 486/36 ms, two workers, both seats)

### 6.1 Fingerprint vs v09 (real: the 200 corpus games of the v09 builds `v09/*` + `current/4cda8b1b*`)

| statistic | real boss | boss-v1 (200 g.) | boss-v2 (300 g.) |
|---|---|---|---|
| boss win rate | **0.675** | 0.305 | **0.543** |
| game length mean / median | 72 / 68 | 85 / 100 | 77 / 79 |
| closed before turn 100 | 0.66 | 0.41 | 0.69 |
| disrupt rate (turns < 45) | 0.93 | 1.00 | 1.00 |
| same target as previous turn | 0.64 | 0.71 | 0.71 |
| switch after ink | 0.65 | 0.83 | 0.84 |
| commit: construction / active / empty | 0.45 / 0.50 / 0.04 | 0.48 / 0.47 / 0.05 | 0.50 / 0.43 / 0.07 |
| commit at instability 0 | 0.71 | 0.85 | 0.84 |
| commit on a live project | 0.34 | 0.47 | 0.47 |
| placement rate / paint per turn | 0.65 / 1.92 | 0.53 / 1.53 | 0.85 / 2.50 |
| boss cells at instability 0 / 1 / ≥2 | 0.95 / 0.05 / 0 | 0.98 / 0.02 / 0 | 0.98 / 0.01 / 0.01 |
| cells in town regions | 0.16 | 0.17 | 0.23 |
| income boss / v09 at turn 20 | 92 / 84 | 77 / 97 | 74 / 68 |

boss-v1 reproduces the sabotage meta but its construction is far weaker than the boss's (income
43 vs 26 at turn 10 with identical paint; `BossAgreementStudy`: target agreement 52 %, boss
cells also chosen 14 %, immediate construction value equal). It fails the win-rate criterion.
boss-v2 reproduces the sabotage meta and the game length/closure profile and is within 13 pp of
the boss's win rate; its construction is v09's (never idles), not the boss's.

### 6.2 Ordering (200 paired games each, seed 910000, same maps for every candidate)

| candidate | live boss delta vs control (40 g.) | mirror vs v09 | vs boss-v1 | vs boss-v2 |
|---|---|---|---|---|
| v07 | −3 (10 vs 13 v08) | — | 53.0 % | _pending_ |
| v08 | −4 (13 vs 17 v09) | 43 % | 62.0 % | _pending_ |
| v09 (control) | 0 | 50 % | **74.0 %** | _pending_ |
| neural-v6 | **+9** | 64 % | 78.5 % | _pending_ |
| neural-v1 | −5 | 56 % | 76.0 % | _pending_ |
| current?USE_ROLLOUT_ALL_CELL_RISK=1 | −5 | 54 % | 83.5 % | _pending_ |
| current?USE_ROLLOUT_ROOT_FILL=1 | +1 | 54 % | 76.5 % | _pending_ |
| v09?USE_ROLLOUT_LIVE_TARGET=1 | −5 (on v08) | 51 % | 72.0 % | _pending_ |
| current?USE_BATCHED_REFRESH=1 | −1 | 57 % | 73.5 % | _pending_ |

Against boss-v1 the sanity order holds with wide gaps (v07 < v08 < v09, neural-v6 > v09), but
the all-cell-risk flag looks +9.5 pp better than v09 where the live boss showed −12.5 pp: a
relentless but weak-building opponent rewards bypass construction that the real boss punishes.
boss-v1 is therefore **not accepted as a strength proxy**; it stays a behavioural reference.
The boss-v2 columns are filled in when the second ordering run completes.

### 6.3 Strength of the flag itself (boss-sabotage-v1, candidate id)

Screens: `current?USE_BOSS_SABOTAGE=1` vs v09 58-42 (100 games, seed 920000) and 163-137
(300 games, seed 930000) = 55.25 % over 400 games; on the validation pool (seed 910000) 124-76.

**Fixed v4-style run (2026-10-21, candidate `current?NOISE=0.1,USE_BOSS_SABOTAGE=true`, control
`v09`, fresh generated paired maps, CG 486/36 ms, two workers, 2-core cloud container):**

| block | seeds | W-L-D | score | pooled | look |
|---|---|---|---|---|---|
| 1 | 940000..940149 | 205-95-0 | 68.33 % | 68.33 % / 300 | clears futility |
| 2 | 941000..941099 | 140-60-0 | 70.00 % | 69.00 % / 500 | clears futility |
| 3 | 942000..942249 | 329-170-1 | 65.90 % | **67.45 % / 1000** | **PASS_1000 (≥ 55 %)** |

Zero bot errors; candidate as P0 67.0 %, as P1 67.8 %; mean score difference +454; games
75–78 turns; candidate accepted disruption 99.2–99.5 % vs v09 77.7–78.4 %; early closures
72 %. In-process timing maxima 127/63/65 ms (candidate) vs 68/77/57 ms (v09) on a shared
2-core host with no protocol/JIT isolation: not deadline evidence. Reports:
`docs/evidence/boss-proxy-v1/boss-sabotage-v1-block{1,2,3}.json`.

Under policy v5 (degenerate-proxy clause, `benchmarking-v5.md` §5) this completes steps 1–2.
Remaining: `submit` + strict `procCheck` on the exact artifact (94,845 characters), then step 3
— 80 paired live boss games (40 on the cached control's maps/seeds/seats + 40 on a second
fixed pool with a fresh same-session v09 control), pass at ≥ control + 5 pp.

## 7. Decision

- Screening opponent for policy v5 = **boss-v2** (`current?USE_BOSS_SABOTAGE=1`), re-validated
  whenever the live boss changes; boss-v1 stays in the panel as the behavioural reference.
- `boss-sabotage-v1` passed the local 1000-game stage (67.45 %); live confirmation pending.
- Selected v09 is unchanged; nothing uploaded.

## 8. Reproduction

```powershell
.\gradlew.bat bossIntentStudy                       # build/analysis/boss-intent/{turns,cells,pairs}.csv
.\gradlew.bat cgLeaderStudy "-Ppath=replays/cg/boss" "-Pplayer=Boss 4" "-Pout=build/analysis/boss-leader"
python tools/boss_proxy/fit_boss_target.py build/analysis/boss-leader/sabotage.csv build/analysis/boss-intent/turns.csv build/analysis/boss-fit.json
python tools/boss_proxy/fit_boss_hold.py   build/analysis/boss-leader/sabotage.csv build/analysis/boss-intent/turns.csv build/analysis/boss-fit.json
python tools/boss_proxy/fit_boss_build.py  build/analysis/boss-intent/pairs.csv
.\gradlew.bat bossAgreementStudy "-Pbot=boss-v1?NOISE=0"
.\gradlew.bat proxyBehaviorStudy "-Pboss=current?USE_BOSS_SABOTAGE=1" "-Popp=v09" -Pgames=300 -Pseed=930000
python tools/boss_proxy/fingerprint.py boss2 build/analysis/proxy-behavior/turns.csv build/analysis/proxy-behavior/cells.csv
.\gradlew.bat bench "-Pbot1=current?USE_BOSS_SABOTAGE=1" -Pbot2=v09 -Pgames=300 -Pseed=930000
```
