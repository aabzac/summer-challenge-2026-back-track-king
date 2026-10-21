# boss-sabotage-v2 — two-stage boss chooser at the real turn (`USE_BOSS_LITE`, 2026-10-21)

Candidate id `boss-sabotage-v2` = `current?NOISE=0.1,USE_BOSS_LITE=true`. Everything else is v09.
Supersedes `boss-sabotage-v1` (`USE_BOSS_SABOTAGE`), which failed on the arena (DEVLOG 2026-10-21).

## Hypothesis

v1's real-turn DISRUPT chooser was right in *what* it chose (boss-fitted logit, held target) and
wrong in *how*: two path searches per inactive pair and one full ink simulation per candidate
region, inside the 36 ms window without timer checks — 16–26 ms per turn on 30×20 maps in a cold
JVM, up to 53 ms per turn locally (`docs/evidence/stage1-opponent-model-v1/cold-turn-timing.log`).
The route features it paid for add nothing (held-out top-1 41.4 % with vs 41.5 % without, once
the income deltas are absent), and the income deltas are only needed for a shortlist.

v2 keeps the fitted weights and the hold rule and computes them in two stages
(`RolloutBot.chooseBossLite`, `// BOSS_LITE` merger block):

1. cheap features for every region in O(cells) — opponent cells, opponent cells on active paths,
   own cells, opponent cells placed in the last two turns, instability, adjacency to a town
   region (static), neutral cells, log(free cells) — ranked by the logit's cheap part;
2. the top `ROLLOUT_TARGETS` = 4 regions (plus the held region) get one `inkBoard` simulation
   each for dOpp/dOwn and the closure check, exactly the four simulations `chooseTarget` pays,
   then the full score. Winning one-hit closures are taken, losing ones refused; the held region
   is kept unless beaten by `BOSS_HOLD_MARGIN` (1.0); a best region with no opponent cells and
   no instability means WAIT. The loop checks the turn timer.

Offline, on the 20,088 boss commitment sets, this two-stage rule with K = 4 and no route terms
reproduces the full chooser: top-1 **51.1 %** (full chooser 51.5 %, shortlist recall 80.6 %;
`docs/evidence/stage1-opponent-model-v1/shortlist-recall-noroutes.log`).

## Packaging

Artifact **93,695 characters** (compacted), sha256 `a8c739fd00f74b1b2174daf197524c07e70623159404631bb9988da9ee40e3eb`,
compiles with `--release 17`; it contains `chooseBossLite` and not `BossSabotage`. (The screen
below was played by the first build, 93,544 chars `6ab05bf5…`; the only later change inside the
`BOSS_LITE` block is the `litePlaced` indirection used by the Stage 1 model, which is the identity
when the model is off.) With the flags off every pinned artifact is byte-identical (`current`
`ab0954a4…`, `v09` `aca0be45…`, deadline candidate `3bd070b3…`); the v1 artifact is unchanged
(`dfea43fa…`). `BOSS_HOLD_MARGIN` moved into its own `// BOSS_HOLD` block shared by both
choosers; the Stage 1 flags (`USE_BOSS_OPPONENT_MODEL`, `BOSS_OPPONENT_REPLIES`,
`USE_BOSS_OWN_MODEL`) live in a `// BOSS_MODEL` block that the v2 artifact does not contain
(with them on: 95,421 chars, compiles). Tests: `BossLiteTest` (3), `BossSabotageSubmissionTest`
(3); `Params` now has 148 public fields (`V07/V08/V09PromotionTest` +32/+30/+31).

## Timing (cold JVM, one game per process, CG 486/36 ms, 30×20 maps, 2-core cloud host)

| seed | v2 mean / max | v09 mean / max | v1 mean / max |
|---|---|---|---|
| 777000 | 22.0 / **39** | 22.4 / 36 | 29.9 / 44 |
| 778009 | 29.6 / **38** | 29.6 / 40 | 31.7 / **53** (1 slow) |
| 778013 | 31.8 / **38** | 31.0 / 37 | 30.1 / 45 |
| 778022 | 22.0 / **40** | 21.8 / 36 | 22.4 / 39 |

v2's profile is v09's; v1 overran the window by 8–17 ms and crossed 50 ms once in four games.
Smoke (8 games vs v09, hot JIT): time/move A 21.5 ms max 46, B 21.2 ms max 46. The strict
exact-artifact `procCheck` on the user's machine remains the gate.

## Predeclared comparison (policy v5, clause 5 as amended 2026-10-21)

`tools/boss_proxy/run_lite_screen.sh`, two workers, CG 486/36 ms, fresh seeds:

1. vs `neural-v6`, 300 paired games, seed 960000, with the v09 control on the same maps in the
   same run; continue only at candidate ≥ control + 3 pp.
2. v4 pooled 1000 games vs v09: seeds 961000 (300) / 962000 (+200) / 963000 (+500), futility
   below 50 % at 300 or 500, pass at ≥ 55 % over 1000.
3. Live: 80 paired boss games against a same-session v09 control, ≥ control + 5 pp.

## Results

**Step 1 — PASS.** vs neural-v6 (seed 960000, 300 paired games): candidate **50.3 %**
(151-149-0, +32.8 mean score, 59.9 turns, disrupt 98.0 %), control v09 **38.8 %** (116-183-1,
−352.7, 73.9 turns, disrupt 74.0 %) → **+11.5 pp** over the control (threshold +3 pp). For
comparison v1 scored 42.0 % against neural-v6 in the §6.2 ordering run (neural-v6 58.0 % against
it). Reports `docs/evidence/boss-sabotage-v2/step1-*.json`.
*Second pool (seed 970000, same-map controls of the Stage 1 screen, 2026-10-21):* candidate
**40.0 %** (120-180), v09 **30.7 %** (92-208) → **+9.3 pp**. The absolute level moves 10 pp
between map pools (neural-v6 is much stronger on this pool against everyone), the delta does
not: pooled over 600 games, 45.2 % vs 34.75 %, **+10.4 pp**. Never read a candidate's score
against a control measured on a different pool.

**Step 2 — PASS_1000.** vs v09, blocks 300/+200/+500 (seeds 961000/962000/963000): **188-99-13
(64.8 %)**, **130-60-10 (67.5 %)**, **304-191-5 (61.3 %)**; pooled **622W/350L/28D = 63.6 %**
(draws half; wins only 62.2 %), zero errors, mean score difference +432/+552/+473, 84–85-turn
games, candidate disrupts on 86–92 % of turns (v1: 99 %; v09: 73–77 %). Threshold 55 %. The
candidate is 3.9 pp below v1's 67.45 % in the mirror and 8.3 pp above it against neural-v6: the
route-traffic "block the empty corridor" hits v2 no longer makes were worth something against
v09 and nothing against an arena-class opponent. Reports `docs/evidence/boss-sabotage-v2/step2-*.json`,
log `screen.log`.

**Step 3 — live, pending (user's machine):** `gradlew test`; `gradlew submit
"-PsubmitVersion=current?NOISE=0.1,USE_BOSS_LITE=true" -PsubmitOut=build/submit-boss-lite`;
strict `procCheck` on the exact artifact (expected sha256 `a8c739fd…`, 93,695 chars); then 80
paired boss games with a same-session v09 control (`tools/boss_cached.py`), pass at ≥ control
+ 5 pp. Not uploaded to the arena before that.
