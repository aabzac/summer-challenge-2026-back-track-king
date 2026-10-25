# Back Track King — CodinGame Summer Challenge 2026 bot

Java 17 / Gradle. One rules engine (`btk.core`) shared by the bot, the local referee and the CG
replay validator. See `AGENTS.md` for the working agreement and `docs/` for rules, version history
and dev log.

## Quick start

```bash
gradlew test                                   # rules unit tests
gradlew run -Pbot0=current -Pbot1=v01 -Pseed=3  # one game -> replays/local/*.json
gradlew bench -Pbot1=current -Pbot2=v01 -Pgames=300 -Pmaps=replays/cg
gradlew benchSuite -Pbot=current               # matched local opponent panel on development-v1
gradlew botCatalog                            # names, categories and experiment status
gradlew botCatalog -Pbot=exp-income-guard      # pinned recipe, implementation identity and parameters
gradlew submit                                 # -> build/submit/Player.java (compile-checked)
gradlew submit -PsubmitVersion=indrill-q-v1 -PsubmitOut=build/submit-indrill # separate Q-learning candidate
gradlew submit -PsubmitVersion=indrill-q-v2 -PsubmitOut=build/submit-indrill-v2 # learned position/WAIT candidate
gradlew submit -PsubmitVersion=neural-v2 -PsubmitOut=build/submit-neural-v2 # outcome-tuned imitation candidate
gradlew submit -PsubmitVersion=neural-v3 -PsubmitOut=build/submit-neural-v3 # same trained weights, deadline-aware execution
gradlew submit -PsubmitVersion=neural-v4 -PsubmitOut=build/submit-neural-v4 # build-temperature training experiment
gradlew submit -PsubmitVersion=neural-v5 -PsubmitOut=build/submit-neural-v5 # exact completion-income runtime experiment
gradlew submit -PsubmitVersion=neural-v6 -PsubmitOut=build/submit-neural-v6 # exact resumable feature-path experiment
gradlew submit -PsubmitVersion=neural-v7 -PsubmitOut=build/submit-neural-v7 # bounded deadline-only construction fallback
gradlew submit -PsubmitVersion=v09              # selected frozen v09 source + complete snapshot
gradlew submit -PsubmitVersion=v08              # historical v08 reference build
gradlew submit -PsubmitVersion=v07              # historical v07 reference build
gradlew submit -PsubmitVersion=v06              # historical v06 reference build
gradlew submit -PsubmitVersion=v05              # optional historical v05 reference build
gradlew procCheck -Pbot=v01 -Pgames=4          # Player.java as external process, CG protocol + timing
gradlew cgFetch -Ptop=20 -Plimit=10            # download top players' replays -> replays/cg/
gradlew cgFetch -Pgame=903337834               # download one known arena/IDE game id
gradlew cgValidate                             # validate our engine against the arena replay corpus
gradlew cgRender -Ppath=replays/cg/903337834.json # validated replay -> rendered/cg/arena
gradlew cgMapValidate                          # regenerate CG maps from refereeInput seeds, compare exactly
gradlew cgAnalyze -Pagent=6735801               # validated games of one arena entry -> build/analysis/
gradlew cgForecast -Pagent=6736850              # offline adaptive sabotage prediction, no strategy changes
gradlew cgTiming                               # phase timing on two captured large-map replay trajectories
gradlew cgDisrupt                              # full sabotage valuation vs timed search on captured losses
gradlew cgBossLossStudy -Pbefore=16 -Ptotal=30   # user-attributed boss cohorts; refuses changed count/existing report
gradlew cgRolloutBossProbe                      # known-state v06 route/model diagnosis from that cohort report
gradlew replyStudy -Pmode=capture               # fixed 24-game local diagnostic capture (once)
gradlew replyStudy -Pmode=analyze               # shared-state reply/ranking/time audit, no strategy change
gradlew jointReplyStudy                        # joint first-build/own-sabotage audit on the SAME saved sample
gradlew jointActionCheck                       # serial cost check of opt-in joint search on saved states
gradlew v09ContinuationStudy -Pout=build/analysis/v09-continuation-v1/new-run # fixed 40-game continuation/closure diagnostic
gradlew v09FullActionStudy -Pout=build/analysis/v09-full-action-v1/new-run # complete construction and repeated-decision experiment
```

On PowerShell quote properties containing dots or `?`: `gradlew cgValidate "-Ppath=replays/cg/903184412.json"`,
`gradlew bench "-Pbot1=current?NOISE=0.2,ROLLOUT_WIDTH=4" -Pbot2=v02`. Use `,` (not `&`) between params —
cmd.exe swallows `&`. Bench two deterministic clones with different `SEED`/`NOISE`, otherwise they
mirror each other and every game is a draw.

The completed [v09 continuation diagnostic](docs/v09-continuation-v1.md) covers
all 2,936 states from 40 saved games: no qualifying full-WAIT correction and no
recovered closing win. The selected follow-up checks omitted sabotage while
preserving complete construction. That [follow-up](docs/v09-full-action-v1.md)
is complete: some promised attacks slip, but earlier attacks have mixed effects
and can prevent a timely winning closure. Selected v09 remains unchanged.

## Pieces

| piece | what it does |
|---|---|
| `btk.core` | `GameState` (primitive arrays), `Rules` (validation, simultaneous resolution, connections, scoring), `Pathing` (BFS shortest path with N/E/S/W tie-break, Dijkstra by paint cost), `Protocol` (fast CG IO), `Action` |
| `btk.bot` | `Bot` interface, `Params` (all tunables, overridable as `current?KEY=v&K2=v`), `Timer`, `MainBot` (what ships), `BotFactory`, `current/` working strategy, `vNN/` frozen versions |
| `btk.submit` | `CGRunner` (CG entry point: fast parsing, JIT warm-up on turn 1, connection cross-check), `FileMerger` (single `Player.java`) |
| `btk.referee` | `Referee` (in-process games), `MapGenerator`/`GridMaker` (authentic CG maps by seed), `Replay` (visualizer JSON) |
| `btk.runner` | `LocalRunner`, `BenchmarkRunner` (paired maps, confidence grouped by map, JSON reports), `Maps` (authentic generation or replay maps), `ProcessBot`/`ProcessCheck` |
| `btk.tools` | `CgFetch` (leaderboard -> battles -> gameResult), `CgReplay` (parse + rebuild map), `CgValidate` (engine vs CG events), `CgRender` (validated visualizer export), `CgMapValidate` (generated maps vs CG viewer data) |
| `visualizer/index.html` | drag & drop a replay JSON; paths, ownership per connection, hover info |

## Reproducible benchmarks

Bots have stable command ids and descriptive labels: `v03 - Risk Aware`, `v04 - Network Extension`,
`v05 - Disrupt Reserve`, `v06 - Simultaneous Rollout`, `v07 - Network After Base`,
`v08 - Protected Opening`, `v09 - Never Idle` (selected). Six retained, **not promoted** experiments can also be selected by name:
`exp-income-guard`, `exp-build-direction`, `exp-early-disrupt`, `exp-reply-rerank`,
`exp-interruptible-vuln`, `exp-disrupt-delay065`. For example:

```powershell
.\gradlew.bat bench "-Pbot1=exp-income-guard" "-Pbot2=v05" "-Ppool=bench/pools/development-v1.json"
```

Named experiments pin complete parameters and verify their compiled engine snapshot; they do not
silently follow future `current` changes. They are optional local opponents, not additions to the
default panel or allowed `submitVersion` values. See [the bot catalog](docs/bot_catalog.md) for
historical evidence, limitations and how to retain/revise an experiment.

`bench` requires an even number of games: 200 games means 100 map pairs, each played with A as
P0 and P1. Reference bots `v01`–`v09` load fixed parameter snapshots; explicit query overrides
are applied afterward. `current` uses the live `Params` defaults, currently the confirmed v09
never-idle policy (v08 plus leftover paint spent next to own rails). It scored **56.90% vs v08**
over 1000 games and **17/40 boss wins** versus the cached v08's **13/40** on the same maps and
sides. See [v09 promotion](docs/v09-promotion.md); v08's own record is in
[v08 promotion](docs/v08-promotion.md).
`current?USE_ROLLOUT_POLICY=0` selects the older working EvalBot; add that
selector when revisiting historical EvalBot or heuristic experiment recipes.

An independent simple-policy candidate is available as `current?USE_ROLLOUT_POLICY=0,USE_HEURISTIC_POLICY=1`.
It uses known-board sabotage and local route priorities, without completed-position evaluation
or reply search. It remains an unpromoted alternative. Its work cap, emergency-clock caveat and commands
are described in [heuristic-policy.md](docs/heuristic-policy.md).
Add `USE_HEURISTIC_DETOUR=1` to that spec for the optional bounded recovery-paint sabotage
experiment. It is off by default and does not change construction rules.

`spatial-ppo-v1` is a separate compact spatial policy/value prototype inspired by
Pduhard's final self-play architecture. Its first fixed four-update run exercises
the complete Java-engine/Python-PPO/Java-export pipeline, with no search. The
3,439-weight network has 24 board planes, a town table and residual/global layers.
It is a technical prototype; selected v09 remains unchanged. Exact settings,
checks, strength verdict and limitations: [spatial-ppo-v1](docs/spatial-ppo-v1.md).
Training/runtime instructions: [tools/spatial](tools/spatial/README.md).

```powershell
.\gradlew.bat spatialRuntime --offline --console=plain
.\gradlew.bat submit '-PsubmitVersion=spatial-ppo-v1' '-PsubmitOut=build/submit-spatial-v1'
.\gradlew.bat procCheck '-PsubmitOut=build/submit-spatial-v1' '-Pbot=v09' '-Pgames=8'
```

The generated `btk.bot.neural.SpatialWeights` is frozen by the completed experiment;
new training requires a new weight artifact and bench. Existing `Neural*` weights
are unchanged.

`spatial-ppo-v2` uses the same model and execution with a separate fixed
complete-game self-play run: 8,192 environment turns, fresh maps and three
checkpoints. `spatial-ppo-v2-init` preserves its exact untrained initializer for
the learning diagnostic. Both remain local candidates/references. Settings,
late-position checks and comparison results: [spatial-ppo-v2](docs/spatial-ppo-v2.md).
Completed: 92 training games; final beats its initializer 96/100, but wins 0/300
against v09 (STOP_FUTILITY_300). Candidate rejected for strength; v09 stays selected.

`spatial-ppo-v3` is the separate follow-up using v2 weights and a fixed pool of
four opponents, with equal learner turns on both sides. The learner-only collector
keeps the previous model and PPO objective. Recipe and results:
[spatial-ppo-v3](docs/spatial-ppo-v3.md).
Completed: 183 training games; final wins 6/100 against v2 and 0/300 against v09
(STOP_FUTILITY_300). Rejected for strength; selected v09 unchanged.

`spatial-ppo-v4` is a separate spatial version: supervised imitation of
frozen v09 decisions, starting from v2 weights. It uses the same production network
and runtime, with separate training/validation maps and no PPO or critic training.
Protocol and results: [spatial-ppo-v4](docs/spatial-ppo-v4.md).
Completed: 60 teacher games, 16 epochs; final vs v2 98W/2L/0D/100,
vs v09 0W/300L/0D/300 (STOP_FUTILITY_300). Rejected for strength; v09 stays selected.

`spatial-ppo-v5` keeps v4 construction and compares two disruptions using a
newly calibrated value head and exact one-turn simulation. One fixed fit changes
only 15 value coefficients. Saved-state nominal search completes 14/34 comparisons.
Results: vs v4 1W/1L/98D/100; vs v09 0W/300L/0D/300 (STOP_FUTILITY_300).
Strict process 8/8 clean; strength rejected, v09 stays selected.
[Protocol and retained evidence](docs/spatial-ppo-v5.md).

`spatial-ppo-v6` keeps the exact v4 weights and compares two last-cell construction
plans of equal paint cost over two exact simulated turns. It ranks actual score
changes without a learned value head. Nominal saved panel: 30/34
comparisons complete; vs v09 1W/299L/0D/300 (STOP_FUTILITY_300).
Strict process 8/8 clean; strength rejected, selected v09 unchanged.
[Protocol and retained evidence](docs/spatial-ppo-v6.md).

`spatial-ppo-v7` explores a different first build and recomputes the remaining
construction choices with their actual new prefix. Frozen v4 weights and the
two-turn score evaluator are unchanged. Nominal saved panel: 26/34
comparisons complete; vs v09 1W/299L/0D/300 (STOP_FUTILITY_300).
Strict process 8/8 clean; strength rejected, selected v09 unchanged.
[Protocol and evidence](docs/spatial-ppo-v7.md).

`spatial-ppo-v8` extends the same construction pair to score checkpoints at
depths2/4/6/8, using the deepest fully completed comparison when time expires.
Nominal panel: 19/34 usable comparisons; vs v09
2W/298L/0D/300 (STOP_FUTILITY_300). Strict process8/8 clean.
Strength rejected; selected v09 unchanged. [Evidence](docs/spatial-ppo-v8.md).

`spatial-learning-audit-v1` returns to the original roadmap's useful-learning
diagnosis. It compares four frozen policies on matched complete games and checks
future-value transfer and a delayed-route representation fixture, without new
training or bot changes. [Protocol and results](docs/spatial-learning-audit-v1.md).

`spatial-ppo-v9` tests one distance-weighted town-context representation change
with the same v4 teacher data and 16-epoch recipe. The synthetic alias is removed,
but vs v4 is 42W/58L; practical-v4 vs v09 is 0W/300L/0D / 300,
STOP_FUTILITY_300. Strict process 8/8 clean; v09 unchanged and original
stage 3 remains open. [Protocol and results](docs/spatial-ppo-v9.md).

`spatial-label-audit-v1` checks all 60 original teacher games without training.
Every observation stream is reproduced exactly; 21,702 teacher construction
permutations preserve the full next state. Ignoring order barely improves actual
full-plan agreement, and observed exact-input contradictions are rare. The main
same-example fitting gap remains unresolved; selected v09 is unchanged.
[Protocol, results and reproduction](docs/spatial-label-audit-v1.md).

`spatial-fit-probe-v1` tests whether unchanged V4 can learn a fixed small TRAIN
subset. One 3,072-step run improves 118/192 decisions to 192/192; actual Java
full plans and semantic successors improve from 8/48 to 48/48. Agreement on the
remaining training examples and previously exposed validation worsens. This
establishes subset trainability, without a new candidate or strength claim.
Selected v09 and original stage-3 status remain unchanged.
[Protocol, results and reproduction](docs/spatial-fit-probe-v1.md).

`spatial-ppo-v10` continues original V4 for exactly 64 more full-corpus epochs,
restoring Adam/RNG and keeping the model/loss/runtime unchanged. TRAIN unforced
agreement 6,251→7,337/14,428 (43.325%→50.853%); exposed validation 1,471→1,361/3,720 (39.543%→36.586%). Against original V4:
66W/34L/0D / 100; practical-v4 against v09: **0W/300L/0D / 300, STOP_FUTILITY_300**.
Strict process 8/8 clean. Strength rejected; selected v09 and original stage-3
status remain unchanged. [Protocol, results and reproduction](docs/spatial-ppo-v10.md).

`neural-proposal-audit-v1` returns to v09 through saved historical `neural-v3`
actions: 51 positions on eight games, 13 terminal omissions. Eight proposals
beat v09's best complete action under a shared rollout continuation; 12 tie and
31 score worse. Seven positives also change the disruption target, so the
construction contribution remains unresolved. No new model, strength bench or
production change. [Protocol and results](docs/neural-proposal-audit-v1.md).

`neural-component-audit-v1` separates those recorded actions on the same 51
positions: swapping only the target beats the best existing v09 action in seven
cases, swapping only construction in three. Of the original eight positives,
five need only the target replacement, two only construction, and one accepts
either. This prioritizes a bounded target-only adapter feasibility check, with
no new production candidate yet. [Protocol and results](docs/neural-component-audit-v1.md).

`neural-target-feasibility-v1` implements the isolated target module and verifies
all 49,735 feature values bitwise. Direct reuse stops at the source-size gate:
119,594/100,000 characters before integration. Module median 1.30 ms, p95 6.20 ms;
only 22/51 saved states leave enough time after v09 for the module alone. No hybrid
candidate or strength run; v09 remains selected. [Results](docs/neural-target-feasibility-v1.md).

The user-authorized follow-up `neural-target-strength-v1` deferred source packaging
and tested the integrated target-only hybrid locally at normal CG search budgets.
Against v09: **129W154L17D/300, 45.83%**, **STOP_FUTILITY_300** on 150 fresh paired
maps, zero bot errors. The module completed 7,146 E/D comparisons and changed 754
of 24,871 turns (3.03%). This candidate is rejected; v09 stays selected. No further
blocks, size work or boss games. [Protocol and results](docs/neural-target-strength-v1.md).


An independent outcome-trained candidate, `indrill-q-v1`, implements a compact version of
[Indrill's forum approach](docs/indrill-q-v1.md): risk-aware Best/Good/Path observations, a
grid network with build/ink Q heads, and greedy whole-quota construction. It is separate from
the earlier imitation policy and does not change the selected v09. Weights are generated by
`tools/indrill/train_q.py`; each retraining requires a new experiment and strength bench.

The separate follow-up `indrill-q-v2` learns a board-value baseline that enters the complete
action once, including WAIT. Its fixed larger training schedule and evaluation are described
in [indrill-q-v2.md](docs/indrill-q-v2.md); training uses `tools/indrill/run_v2_training.py` and
generates only `IndrillV2Weights`. The v1 source and weights stay frozen.
It completed 960-game/14,000-update training but again won 0/300 games vs v09; selected v09 stays.

The completed `indrill-capacity-v1` diagnostic tests direct teacher imitation with
the existing Q representation. All 293 replays reproduce exactly; the export has
3,068 valid observations. Stage A uses 64 observations from 16 maps and completes
its fixed 2,000 updates / 32,000 sample visits. Final agreement is **6/64 joint,
15/64 build, 26/64 ink**, below the required 63/64 joint: **FIT_NOT_ESTABLISHED**.
Mean joint NLL falls from 17.1924 to 7.4922. Stage B and model scoring of the
1,020 held-out observations from 51 maps were not run. No input-label conflicts
or sufficient structural ties were found; this does not prove universal model
capacity, and decreasing loss does not establish convergence. The bounded
single-position follow-up is recorded below. This diagnostic creates no promoted
bot or strength result.
[Protocol](docs/indrill-capacity-v1.md), [evidence](docs/evidence/indrill-capacity-v1.json).

Both `indrill-fit-one-v1` arms completed exactly 2,000 updates on the first A
position, learning construction only: **FINAL_SET_FIT** for free cell logits and
the fresh unchanged network. The network's NLL falls **14.39639247 -> 0.002980866**;
its decoded set is exact at updates 500/1000/1500/2000. Free logits also decode
exactly, with final NLL **10.73031938**. The final audit and archive checks pass.
This one position received 2,000 visits versus about 500 per row in the old A run,
so the result does not isolate ink/build interference. The separate matched-panel
follow-up is recorded below. No generalization, strength, capacity-ceiling or
promotion claim follows. [Protocol](docs/indrill-fit-one-v1.md),
[final evidence](docs/evidence/indrill-fit-one-v1.json).

`indrill-build64-v1` completes the same 64-position recipe with construction-only
training: **16/64 exact sets versus the old 15/64**, with 3 gained, 2 lost and
13 retained exact rows. Original initialization, batch 16, sampling order and
2,000 updates / 500 visits per row are matched. Mean build NLL falls
**13.65655966 -> 5.16299332**, versus the old final **5.56911593**. This small
descriptive improvement does not meet the separate 63/64 memorization screen:
**BUILD_FIT_NOT_ESTABLISHED**. Final evidence audit and archive verification pass.
No holdout scoring, new games or promotion occurred. The separate longer-budget
follow-up is recorded below; this historical verdict is unchanged.
[Protocol](docs/indrill-build64-v1.md),
[evidence](docs/evidence/indrill-build64-v1.json).

`indrill-build64-long-v1` completes exactly **8,000 updates / 128,000 sample visits**,
with the same 64 observations, initialization, objective and batch 16. All first
2,001 states and five metric payloads match the prior run exactly. Final agreement
is **59/64 (92.1875%) versus 16/64**, with 43 gained and no lost exact sets. Mean
build NLL falls **5.16299332 -> 0.58229369** between updates 2,000 and 8,000. Extra training
substantially improves fitting on this panel, but the final-only 63/64 screen is
still not met: **BUILD_FIT_NOT_ESTABLISHED**. Five construction sets remain wrong.
Final audit and archive verification pass. No holdout scoring, new games, promotion or
generalization/arena-strength result follows. The separate continuation to 12,000
total updates is recorded below; this parent run and its verdict remain unchanged.
[Protocol](docs/indrill-build64-long-v1.md),
[evidence](docs/evidence/indrill-build64-long-v1.json).

`indrill-build64-12k-v1` reaches **64/64 exact construction sets**:
**BUILD_FIT_ESTABLISHED** against the final-only 63/64 screen. It continues the
audited full step-8,000 state for exactly **4,000 new updates / 12,000 total**,
preserving Adam moments, optimizer clock and sampler. All 59 parent successes
remain exact and the five errors are corrected. Build NLL falls
**0.58229369 -> 0.05434280**; the scheduled 10,000-update result was 63/64, but the
full fixed budget still ran. All 158/158 target cells match. Final audit and archive
verification pass. This establishes
construction fitting on these 64 development observations; ink heads stayed frozen.
It does not unlock the old joint Stage B or establish arena strength. The separate
matched joint diagnostic is recorded below; this construction-only result remains
unchanged.
[Protocol](docs/indrill-build64-12k-v1.md),
[evidence](docs/evidence/indrill-build64-12k-v1.json).

`indrill-joint64-12k-v1` completes **10,000 new updates / 12,000 total** from the
original joint model's full audited step-2,000 checkpoint. Final agreement is
**63/64 joint, 64/64 build and 63/64 ink**: **JOINT_FIT_ESTABLISHED** at the fixed
final-only 63/64 screen. All six original joint successes remain, with 57 gained
and one residual ink error. Mean joint NLL falls **7.49216108 -> 0.32454612**.
Construction is exact under both matched 12k recipes, with build NLL **0.13778353**
for joint training versus **0.05434280** for build-only. This establishes fitting
on these 64 development observations under the retained representation; the old
2,000-update failure did not establish an inability to learn the full labels.
All 158/158 construction cells match; final audit and archive verification pass.
No Stage B, holdout scoring, new games or promotion followed from that experiment.
The separately authorized larger-panel follow-up is recorded below; the original
capacity-v1 Stage B remains closed. [Protocol](docs/indrill-joint64-12k-v1.md),
[evidence](docs/evidence/indrill-joint64-12k-v1.json).

`indrill-fit512-v1` completes the user-authorized intermediate diagnostic: **512 positions
from 32 maps**, unchanged model/objective, fresh B initializer and **48,000 updates**
at batch 32. This gives **3,000 visits per position**, matching the successful
64-position run's exposure count. Final training agreement is **107/512 build
(20.90%), 299/512 ink (58.40%) and 59/512 joint (11.52%)**: the **359/512 (70%)**
target is not reached, **FIT70_NOT_ESTABLISHED**. Multi-cell builds match in only
**41/404** training rows. On the fixed 1,020-position / 51-map research holdout,
agreement is **85 build (8.33%), 396 ink (38.82%) and 25 joint (2.45%)**, below
the previous B2048 model's saved **111 / 424 / 42** on those same positions.
The smaller panel and longer training improve fitting on the selected rows, but
not held-out imitation; this does not isolate an architectural limit. The fixed
experiment is closed without extension, new games or promotion.
The saved-state audit passes for all **48,001 checkpoints** and exact sampler
exposure; saved Adam parameter arithmetic is bit-exact.
[Protocol](docs/indrill-fit512-v1.md), [results](docs/BOTS.md),
[audited evidence](docs/evidence/indrill-fit512-v1.json),
[verified archive index](docs/evidence/indrill-fit512-v1-artifacts.json).

`indrill-generalization-v1` completes a fresh **12,000-update / 384,000-visit**
joint imitation run on **2,048 positions from 128 maps**. Final training agreement
is **231/2,048 build (11.28%), 892/2,048 ink (43.55%) and 105/2,048 joint (5.13%)**.
On the fixed research holdout (1,020 positions / 51 other maps), the final model
scores **111 build (10.88%), 424 ink (41.57%) and 42 joint (4.12%)**, versus frozen
Q-v2's **29 / 8 / 1**. Both predeclared paired-map difference intervals are above
zero: **TRANSFER_EVIDENCE**, an imitation diagnostic rather than arena strength.
The small train/holdout gap accompanies low absolute training accuracy; this does
not isolate optimization budget from model/feature limitations. Multi-cell builds
are exact in only **3.02% of training and 3.34% of held-out rows**. Preserve the
fixed run and investigate training fit before a separately designed RL/strength
experiment. No Java export, new games or promotion. Final evidence verification
is recorded in [the experiment entry](docs/BOTS.md).
[Protocol](docs/indrill-generalization-v1.md),
[evidence](docs/evidence/indrill-generalization-v1.json).

`neural-v2` continues from the retained `neural-v1` imitation weights with terminal
win/draw/loss policy-gradient updates and a fixed teacher prior. The feature definitions,
candidate filters and sequential placement policy remain the same. Its one fixed
1,536-game training run, numerical checks and separate practical-v4 evaluation are documented
in [neural-policy-v2.md](docs/neural-policy-v2.md). Run the predeclared schedule with
`python tools/neural/run_outcome_v2.py`; existing stages are preserved, and `--resume` requires
the same source hashes and verified completed artifacts. Generated weights use a separate
class and submission directory; the original neural-v1 and selected v09 remain available.
Completed result: **64.5% vs v09 / 1000 games**, local gate passed; strict process
panel had **1/8 timeouts**, and the boss stage is pending. It is not ready/promoted.

`neural-v3` retains those exact trained weights, checks the remaining time budget
and discards incomplete feature/scoring passes. It passed **621/1000 (62.1%) vs v09**, all
**8/8 strict process games**, 406 tests and 292 archived replay validations. The
completed live boss check is **15/40 vs v09's 11/40** on the same maps/seats, with
**80/80 new replays reproduced exactly** and zero technical failures. Practical-v4
and readiness checks passed; the candidate is **not promoted**. Details and evidence:
[neural-policy-v3.md](docs/neural-policy-v3.md).

`neural-v4` repeats outcome training from the original v1 prior with construction
sampling temperature0.5 and sabotage temperature1. It keeps the v3 inference policy,
uses separate generated weights, and is evaluated against neural-v3 on fresh maps.
The completed experiment is **rejected**: 149W/150L/1D over 300 games (49.83%),
first futility stop; strict process check also failed 1/8. No boss run or promotion.
The retained schedule is `python tools/neural/run_outcome_v4.py`;
[predeclared protocol](docs/neural-policy-v4.md),
[final strength evidence](docs/evidence/neural-v4-v4.json),
[readiness evidence](docs/evidence/neural-v4-verification.json).

`neural-v7` adds a bounded construction fallback only when neural calculation stops
for time, retaining V6 features and V2 weights. All800 ample-time actions match V6;
the actual-prefix diagnostic fills96/96 unused paint, without proving a payoff gain.
All466 tests and strict process8/8 pass. Fresh practical-v4 versus v09 scores
**653/1000 (65.3%)**, zero errors. The fresh matched boss stage finishes **14W26L
versus v09 15W25L**: combined **NOT_PASSED**, all 80 replays exact. Early paint is
94.83% overall and 91.74% on 24 tall maps. This does not demonstrate that under-spending
is resolved, or establish a gain over V6 on different maps. IDE contains exact V7; default
remains v09, with no promotion or arena upload. This bounded experiment is closed;
the subsequent Indrill diagnostic is completed above. [Protocol](docs/neural-policy-v7.md),
[final evidence](docs/evidence/neural-v7-final.json),
[live archive](docs/evidence/neural-v7-live-artifacts.json).

`neural-v6` reuses exact feature path searches within preparation, with unchanged
V5/V3 policy and V2 weights.800 actions and11.8million feature values match exactly;
one local measurement reduced play time22.73% overall/30.82% on tall maps.452 tests,
strict process8/8, fresh practical-v4 vs v09 **643/1000 (64.3%)**, live boss **20/40
versus cached v09 11/40**: combined PASS. All40 new CG replays are exact. At completion,
IDE contained V6; default/local selection remained v09 and no arena upload was performed. CG still
spends only88.33% of nominal early paint on tall maps and emits some wall warnings;
this is not proof of complete timing safety or a paired strength gain over V3/V5.
[Protocol](docs/neural-policy-v6.md), [final evidence](docs/evidence/neural-v6-final.json).

`neural-v5` retains V3 weights and replaces hypothetical route-completion income
with an exact allocation-free core helper. All 800 saved-state actions and
11,789,067 feature values match. Allocations fall 88.30% overall, but the measured
1.85% time reduction does not establish a clear speedup. Fresh practical-v4 versus
v09 passes at **610W/389L/1D /1000 (61.05%)**. Strict process readiness still fails
1/8; no boss games or promotion. [Protocol](docs/neural-policy-v5.md),
[strength](docs/evidence/neural-v5-v4.json),
[readiness](docs/evidence/neural-v5-verification.json).

The selected protected opening claims town approaches during the first three turns using
potential route traffic, then returns to the existing rollout policy. Disable it with
`current?USE_PROTECTED_OPENING=0` for a v07-policy ablation. The independent rejected RHEA
experiment is available as `current?USE_ROLLOUT_RHEA=1,USE_PROTECTED_OPENING=0`; it evolves
construction priorities after ordinary and singleton search finishes. RHEA remains off and
cannot be combined with the opening. [Original fixed evaluation design](docs/strategy-search-v1.md).
Completed results: [RHEA](docs/evidence/strategy-search-rhea-v1.json) scored 44.83% over 300
games and failed; [protected opening](docs/evidence/strategy-search-opening-v1.json) scored
62% over 300 games and 13/40 against the boss versus the cached v07's 10/40, passing practical
v3. The user subsequently selected that exact opening candidate as v08, without retuning.

After its opening, `current`/v08 uses the eight-turn rollout strategy from frozen `v07`: both players
construct affordable routes and disrupt regions inside simultaneous rollouts. It compares earned
points across two opponent construction priorities and chooses the first build/sabotage action.
v07 adds the cheap-income route slot and evaluates network single-cell actions only after all
ordinary roots complete, using the remaining time. Old v06 remains available as a frozen control.
For a QuickIncome-only ablation use `current?USE_ROLLOUT_NETWORK_AFTER_BASE=0,USE_PROTECTED_OPENING=0`; to reproduce
the current implementation with v06 parameters also set `USE_ROLLOUT_QUICK_ROUTE=0`.
Historical `current?` experiment recipes must explicitly disable the newly selected mode.
See [rollout-policy.md](docs/rollout-policy.md) for the fixed route
portfolio, limits, verification and results; `gradlew rolloutCheck` audits saved public positions.

The latest [20-game v07 boss study](docs/boss-v07-loss-analysis.md) includes exact renders,
multi-cell network steals and a stalled-sabotage case. Open its
[filtered replay collection](http://127.0.0.1:7799/replays/rendered/cg/boss-v07-20.html)
with the local replay server running. A separate [fresh arena opening survey](docs/arena-opening-survey-v1.md)
describes protected ownership claims by leading players.

Two independent default-off follow-ups are available through parameters:
`current?USE_ROLLOUT_ROUTE_DEPENDENCIES=1,USE_PROTECTED_OPENING=0` retains omitted rail-bridge dependencies in forecasts;
`current?USE_ROLLOUT_NETWORK_CHORDS=1,USE_PROTECTED_OPENING=0` adds affordable two/three-cell network rerouting only
after ordinary and singleton comparisons finish. Their separate fixed protocols are
[route-dependencies-v1](docs/route-dependencies-v1.md) and [network-chords-v1](docs/network-chords-v1.md).
Neither flag is part of selected v08; benchmark verdicts are recorded in BOTS/DEVLOG.

`current?USE_ROLLOUT_TRACK_VALUE=1` is the leader-calibrated sabotage valuation from the
[Indrill study](docs/indrill-study-v1.md): every opponent track in a region is worth
`ROLLOUT_OPP_TRACK_VALUE` (5) and every own track costs `ROLLOUT_OWN_TRACK_VALUE` (6) in the
target valuation, instead of the single 0.4 track-balance weight, so unfinished opponent projects
are inked before they earn. Add `ROLLOUT_TRACK_VALUE_SELF_ONLY=1` to keep the v08 term for the
simulated opponent inside rollouts. Both are default-off; screen results are in BOTS/DEVLOG.
Fetch and study the leader's replays with `python tools/diagnostics/indrill_study.py fetch|study`
and export per-decision feature tables through the core with `gradlew cgLeaderStudy`.

`current?USE_ROLLOUT_POLICY=0,USE_DEFENSE_PRIORITY=1` tests a different feature in the regular EvalBot: prioritize a
cheap bypass of already damaged active income before ordinary construction. It checks at most
3 regions x 4 directed connections, caps the route at 6 paint, and uses core income plus actual
per-turn placement costs to reject harmful or late recovery. It replans each turn; no future
opponent actions are available. Default-off; see the defense-priority experiment in DEVLOG.

`current?USE_ROLLOUT_POLICY=0,USE_OWN_BUILD_PLAN=1` is a separate default-off construction experiment: up to three
ordinary root routes, three affordable build turns, at most 39 nodes / 27 continuations. It
compares earned points and terminal income using the core, spends leftovers on retained routes,
and emits only the selected first action before ordinary sabotage. No opponent moves or future
sabotage are simulated. `OWN_BUILD_WIDTH` (1..3) and `OWN_BUILD_DEPTH` (2..3) bound the search.
An interrupted comparison retains the ordinary route fallback; root generation is still timed,
so fixed rollout limits do not guarantee identical timed games. Do not combine with other
construction/opponent experiments. Protocol and results: own-build-plan-v1 in DEVLOG.

```bash
gradlew bench -Pbot1=current -Pbot2=v03 -Pgames=200 -Pthreads=2 -Pseed=1000 "-Pout=build/bench/current-v03.json"
```

Every run saves a JSON report (by default under `build/bench/`) containing full resolved
parameters, seeds, map hashes, both games in each pair and timing. `bench`, `run` and `cgStudy`
use nominal CG search budgets (486 ms first / 36 ms later); `-Ptiming=legacy` restores the old
1000/50-ms local allowance. The profile is saved in each bench report. Confidence is grouped by
distinct map; replaying the same map does not manufacture independent evidence. The old
per-game Wilson interval is retained as a diagnostic. The active practical policy v3 starts with
300 games: >=60% passes, <=52% stops, only >52% and <60% triggers fresh 1000 at >=55%.
It also requires 40 new candidate games against the live boss on the saved control's exact
40 maps/seeds/seats, scoring at least as much as that control (wins=1, draws=0.5; ties pass).
This replaces the mandatory older/style-opponent stages. Sequential boss games can run
alongside the required local 1000 after a qualifying screen. The cached boss pool is a
reusable regression check, not fresh confirmation. See [the v3 rule](docs/boss-cache-policy-v3.md)
for cache identities, refresh, commands and the initially accepted v07 control (10/40).
Both confidence intervals are now diagnostic only; the historical `promotionGatePassed` JSON
field is explicitly labelled legacy. See [docs/benchmarking.md](docs/benchmarking.md) for the
complete policy, stopping conditions and version-freezing procedure.

The active policy since 2026-09-20 is **practical-1000-futility-boss-cache-v4**: one pooled
1000-game local comparison (blocks 300 / +200 / +500) with **futility-only** interim looks that
stop when the pooled score is strictly below 50% at cumulative 300 or 500 games. Survivors play
the full 1000 and are gated once at >=55%; there is no early pass. The 40-game cached-boss check
is unchanged from v3 and may run concurrently once the 500-game look clears; a local failure is
never rescued by boss results. Run `python tools/run_practical_v4.py --spec <candidate> --name
<id> --seed <base>` (stops automatically on a futility look), then combine domains with
`python tools/practical_gate_v4.py --block1 <r1> --block2 <r2> --block3 <r3> --boss-plan <cached>`.
The superseded v3 tooling (`boss_practical_gate.py`) and its verdicts remain for reproduction.

Explicit empty or malformed replay pools fail instead of silently switching to generated maps.
`cgValidate` also exits with an error for divergences, malformed input or an empty replay corpus.

For the named development pool and a behaviorally different reference:

```powershell
.\gradlew.bat bench "-Pbot1=current" "-Pbot2=pressure-v1" "-Ppool=bench/pools/development-v1.json"
.\gradlew.bat benchSuite "-Pbot=v05" "-Pout=build/bench/my-v05-panel"
```

`pressure-v1` and `leader-v1` are local-only references, not promoted submissions. `leader-v1`
is a fast heuristic calibrated on the arena leader (Indrill): a sticky DISRUPT every turn on the
opponent's unfinished construction, greedy cheapest-route construction and a protected opening.
It reproduces the leader's relentless-sabotage meta (99.9% accepted disruption vs v08's ~56%),
not its never-idle construction (it places on ~42% of turns vs the leader's 97%) and not its
arena strength (v08 wins ~82%), so it is a behavioral screening opponent for sabotage
mechanisms, not a strength benchmark. Select it with `-Pbot2=leader-v1`; the matched four-arm
check is `python tools/run_leader_coverage.py`. See [docs/indrill-study-v1.md](docs/indrill-study-v1.md).
`leader-v2` keeps that sabotage and never idles (places on ~90% of the leader's states) but still
loses ~80% to v08. `gradlew cgCellStudy "-Ppath=<replays>" "-Pplayer=<nick>"` follows every
placed track of a named player through its life (route slack at placement, first earning turn,
pairs served, lifetime points); `python tools/diagnostics/cell_study.py <cells.csv>` aggregates
it. See [docs/indrill-construction-v1.md](docs/indrill-construction-v1.md).

`neural-v1` is an independent **imitation policy**: two small conditional-logit MLPs
(`btk.bot.neural`) pick the DISRUPT region and the placement cells from engine-grounded candidate
features, trained by behaviour cloning on the leader's 282 replays. Pipeline:
`gradlew cgNeuralExport` (candidate datasets through the core) -> `python tools/neural/train_neural.py`
(numpy, writes `NeuralWeights.java` + `docs/evidence/neural-v1-training.json`) -> `gradlew submit
-PsubmitVersion=neural-v1`. Only the closing-ink guard is a hard rule. See
[docs/neural-policy-v1.md](docs/neural-policy-v1.md).
The suite plays
the same paired maps against v05, pressure-v1, v03 and aggressive v02, skipping an exact self-spec.
Each opponent has its own report; no combined win rate is treated as a promotion decision.
Reports include height slices, accepted action frequencies and early closures; the default is
two worker threads. Choose a fresh suite output directory to preserve earlier evidence.

`bench/pools/confirmation-v4.json` reserves a fresh 1000-game pool. Use it only for a fixed
candidate, with `-PexcludeReports=<development report or suite index.json>`: overlapping map hashes
are rejected before any game starts. See [benchmarking.md](docs/benchmarking.md) for limitations
and confirmation commands. Do not tune on this reserved pool.
Confirmation-v2/v3 were consumed by the fixed rollout 2x1000 experiment; exclude those reports
as well as earlier development maps from future confirmation.
The older confirmation-v1 is retired: rollout-v1 inadvertently used part of its seed range for
a development screen. Its original file remains for history; see [pool status](bench/pools/README.md).

For arena diagnosis, fetch the player's latest games, then analyze the agent id printed by the
fetcher (keeps different arena entries separate):

```bash
gradlew cgFetch -Pplayer=aabzac -Plimit=50
gradlew cgAnalyze -Pagent=6735801
```

`cgAnalyze` validates each selected arena replay before reporting accepted placements/paint,
disrupts, first income and per-turn diagnostics. All per-player arrays in its JSON are indexed
by P0/P1; each game records `me`. Turns are zero-based. Counterfactuals change only the current
simultaneous turn and then reset to the recorded state; their score deltas are not projected
match outcomes and must not be summed as lost final points. Failed-server turns have no
counterfactual recommendations. Boss replays are excluded.

## Authentic maps by seed

`run` and `bench` use the ported CodinGame generator by default. To play on the exact map from
CG game 903182120, use its signed `refereeInput` seed without a `-Pmap` override:

```bash
gradlew run -Pbot0=current -Pbot1=v03 -Pseed=-176203007668178900
gradlew cgMapValidate "-Ppath=replays/cg/903182120.json"
```

`-Pmap` / `-Pmaps` still select maps directly from downloaded replays. Generator provenance,
seed constraints and regression fixtures are documented in [docs/map_generator.md](docs/map_generator.md).

## Visualizer

```bash
python -m http.server 7799
# http://127.0.0.1:7799/visualizer/index.html?file=/replays/local/<game>.json
```

Canonical CG files are validation inputs, not visualizer documents. Render one only after exact
engine validation:

```bash
gradlew cgRender "-Ppath=replays/cg/903337834.json"
# http://127.0.0.1:7799/visualizer/index.html?file=/replays/rendered/cg/arena/903337834.json
```

Boss games are detected from `arenaboss` metadata or legacy agent id `-2` and written under
`replays/rendered/cg/boss/` instead. Player slots follow `agents.index`, including positive boss IDs.
`cgValidate` only validates and never creates files.
The viewer groups each player's colored nickname, score and turn income, labels track ownership
in the connection table, and shows the current cell's sabotage state while stepping through turns.
CG seeds come from the original referee input and can be copied without 64-bit rounding.
Re-render older files whose seed was exported as zero; refresh an already open viewer after updates.
The rendered 30-game Boss 4 collection is available locally at
`http://127.0.0.1:7799/replays/rendered/cg/boss-30.html` while the replay server is running.
It uses the corrected 16 v05 / 14 v06 split; first v06 game: 904146009.

### Capturing an IDE/boss game

`tools/codingame_replay_export.user.js` intercepts the browser's `TestSession/play` response,
extracts its `gameId`, and downloads the canonical replay through `gameResult/findByGameId` without
reconstructing anything from stderr. Start `python tools/save_server.py` before pressing
**PLAY MY CODE**; a first-turn build marker matching a verified local artifact assigns the replay to
`replays/cg/boss/<strategy>/<buildId>/<gameId>.json`; missing/unverified markers go to
`replays/cg/boss/unknown/`. Original flat historical games stay under `boss/`.
This verifies the marker's local source/configuration mapping, not which source CG executed.
Neither a requested version nor a directory name assigns a build to an unmarked legacy game.
The original response is retained under `replays/cg/boss/raw/unknown/` only when
the canonical game-result lookup fails. Without the local server the userscript falls back to a
browser download. Run `gradlew cgValidate "-Ppath=replays/cg/boss"` to validate the recursive boss
corpus, or `gradlew cgRender "-Ppath=replays/cg/boss"` to produce validated visualizer documents
under `replays/rendered/cg/boss/`. Restart an old save server after updating; `/health` now reports
`protocolVersion: 2`. Old `bot` capture requests remain compatible.

For unattended sequential games, use the independent [boss API runner](docs/boss-api.md).
Import a browser session once into an ignored local file, freeze the exact submissions and a
full game schedule, then run it without a browser or save server. It preserves canonical
replays, verifies build/seed/seat/boss identity and resumes read-only retrieval after a lost
response without repeating uncertain game creation. New evaluations use the
[cached-control v3 rule](docs/boss-cache-policy-v3.md). See also the historical
[fresh-comparison protocol](docs/boss-evaluation-protocol.md)
and the separate [planned 40-pair chords comparison](docs/chords-boss-v1.md).
That comparison is **complete under explicit amendments: 80 games, 40 per strategy**.
Chords won 9/40 (22.5%); v07 won 10/40 (25%). The exploratory paired test provides no
evidence of improvement. Two failed HTTP requests were excluded and retried separately;
all 82 attempts remain recorded. See [amendment 1](docs/chords-boss-v1-amendment-1.md),
[the rate-limit amendment](docs/chords-boss-v1-amendment-2.md), the
[independent final audit](docs/evidence/chords-boss-v1.amended.json) and the
[80-game gallery](http://127.0.0.1:7799/replays/rendered/cg/chords-boss-v1.html).
Six technical games, including diagnostic `904843581`, remain outside the comparison.

`cgBossStudy` diagnoses defensive building on an explicit boss sample, correctly reading either
the `arenaboss` marker or legacy id `-2`. It validates every game first and records per-turn paint
costs for bypassing already threatened regions, using only accepted boss placements in the probe:

```powershell
.\gradlew.bat cgBossStudy "-Ppaths=replays/cg/boss/904047070.json,replays/cg/boss/904047111.json,replays/cg/boss/904047151.json,replays/cg/boss/904047204.json,replays/cg/boss/904047262.json"
```

The default report path is timestamped under `build/analysis/`; `-Pout` must name an unused file.
Turns are zero-based; cost reduction is evidence of an alternative connection, not proof of boss
intent or preserved income. Unfinished routes are excluded. Findings and limitations are in
the 2026-09-16 boss-defense entry in [DEVLOG](docs/DEVLOG.md).

## Submitting

The arena source limit is **100,000 characters** (user-confirmed). `submit` checks the UTF-16
length conservatively before writing, prints UTF-8 bytes separately, and compile-checks the
result. `build/submit/manifest.json` records the selected strategy, full resolved parameters,
character count and SHA-256 of the exact generated source. New builds also retain an immutable
source/configuration record in `build/submission-registry/<buildId>/` and emit its marker in the
first-turn `MESSAGE`; the marker identifies a local artifact but is not remote attestation.
The historical v06 packaging sample on 2026-09-16 failed **1/42** strict process games
(50.462 ms on a 50-ms turn); that failure remains recorded. Historical v07 subsequently passed
its exact-artifact check; see [v07 promotion](docs/v07-promotion.md), [BOTS](docs/BOTS.md)
and [DEVLOG](docs/DEVLOG.md) for the separate evidence. The newly selected artifact's checks
are recorded in [v08 promotion](docs/v08-promotion.md).
The v08 main artifact's fixed strict panel recorded **1/8 problem games**: map 903510176,
P1 turn 29, **67.5805 ms** against a 50-ms deadline; that record stays. The v09 main artifact's
identical eight-game panel passed **0/8** (first response <= 736 ms, later <= 47.2 ms), see
[v09 promotion](docs/v09-promotion.md).
The merged artifact removes leading/trailing line indentation while preserving token boundaries
and ordinary string literals. Java text blocks are rejected by the line-based merger; source
files themselves retain their formatting.

`submit` defaults to `current`, now v10 "Boss Model" as pruned by `prune-v1` (2026-10-25, 70,397 of
100,000 characters: the rejected rollout experiments and the `Params` fields the policy never reads are
left out of current rollout artifacts; see `docs/prune-v1.md`). To reproduce the selected frozen policy
with every historical mechanism, use `gradlew submit -PsubmitVersion=v10`; `v09`, `v08`, `v07`, `v06`
and `v05` also remain available. The merger
includes only the selected strategy as `EvalBot`, embedding its complete resolved parameters
in `MainBot` for both default and frozen builds. Frozen files and working source stay unchanged.
This reproduces the strategy, not a byte-for-byte historical artifact (shared core, runtime and
packaging are current). Other frozen versions are not supported by this option.
For an isolated experiment, `submit "-PsubmitVersion=current?ROLLOUT_WIDTH=4" -PsubmitOut=build/my-candidate`
embeds the resolved candidate parameters without touching `build/submit`. Pass the same
`-PsubmitOut=build/my-candidate` to `procCheck`; a missing explicit artifact is an error.
Always run `procCheck` after building; `-Pbot` selects its OPPONENT, not the submitted strategy.
Use `-Pmap=replays/cg/<id>.json -Pgames=2` to check both sides of a particular difficult map.

Submission `MainBot` now warms a disposable instance of the selected strategy with 36-ms calls
inside the existing 500-ms warmup allowance. This reaches route search without consuming the
live bot's RNG or mutating its state. `ISOLATED_ROUTE_WARMUP=true` is the runtime default;
`submit "-PsubmitVersion=v05?ISOLATED_ROUTE_WARMUP=0"` builds the old-warmup diagnostic control.
The v05 policy itself is unchanged. Core operations can still overrun a cooperative slice;
local timing checks do not guarantee identical behavior on the CG host.

`procCheck` now defaults to **zero grace**: 1,000 ms first response / 50 ms later. One clock
includes turn serialization and stdin write/flush; stdout lines are timestamped by the reader.
Late lines fail even when the waiting thread resumes with a nonempty queue. A timeout or EOF
ends the process session, so stale output cannot become the next turn's answer.
`-PgraceMs=30` explicitly restores extra allowance for diagnosis, not a strict timing pass;
nominal deadline overruns remain reported. `-Pout=build/analysis/process-check.json` selects
the per-turn JSON report (also written when games fail). It separates send time, reader arrival
and consumer wake-up, and records source/executable hashes. Reader scheduling and host load
still affect the measurement; it is not CG CPU time or a hard real-time watchdog for blocked IO.

`-PprocessDiagnostics=true` optionally splits input preparation/write/flush and records host CPU/GC
counter deltas plus failed input. It is off by default, requires an unused report path and does
not change deadlines or Player.java. These are host-side clues, not measured bot search time;
see [process diagnostics](docs/benchmarking.md#optional-process-input-diagnostics).

1. `gradlew test && gradlew bench ...` (gate in `AGENTS.md`)
2. `gradlew submit && gradlew procCheck`
3. Paste `build/submit/Player.java` into the CG IDE (language Java), PLAY MY CODE, then SUBMIT.
4. Record the version and CG rank in `docs/BOTS.md`.

## Opponent prediction study

`replyStudy` audits the retained narrow reply-reranking model against `pressure-v1`: first capture
the fixed local sample, then analyze saved shared states in a separate invocation. It separates
known-state, predicted-response and recorded-action oracle evaluations, plus nominal/ample-time
full-play choices. See [the protocol and limitations](docs/reply-study.md). It does not alter v05
or build a new submission.

`jointReplyStudy` reuses that capture and report, compares build prefixes with known-state own
sabotage targets, and checks every simultaneous combination through core. It does not replay
new games or rerun timing measurements. See [joint-action findings](docs/joint-reply-study.md).

The subsequent opt-in `USE_JOINT_ACTION` screen did not pass promotion; that feature remains
off. Historical recipes require `USE_ROLLOUT_POLICY=0` now that v06 is selected.
See [the experiment and checks](docs/joint-action-experiment.md).

`cgForecast` evaluates a lightweight adaptive sabotage model without changing our bot's actions.
It predicts from each pre-turn state and learns only after observing the next state; held-out
opponent entries never enter baseline selection. See [docs/sabotage_forecast.md](docs/sabotage_forecast.md)
for the fixed experiment, information boundaries, results and limitations.

`cgTiming` explicitly selects the older EvalBot and profiles its route search, nested vulnerability evaluation and sabotage
on recorded states. Optional `-Ppaths` is a comma-separated replay list; `-Pparams` selects an
explicit diagnostic variant and `-Pout` saves all per-turn observations. It enables
`PROFILE_SEARCH` only for that run. Evaluation time is INCLUDED in route time, not additional.
These instrumented EvalBot calls do not profile v06 or reproduce the local timeout match;
use them for attribution, and use uninstrumented `procCheck` for process deadline evidence.
`cgTiming` invokes the generic strategy warmup, not the isolated `MainBot` submission hook;
its warmup counters do not measure the new submission runtime.

Process-check coverage note: the current runner advances map and player side together.
For an even-sized map directory, simply doubling `-Pgames` repeats the same map/side combinations.
Inspect the report's map/player pairs or run each map separately with `-Pgames=2` when both sides
are required. The quick-income experiment preserves its original samples plus the missing-side
supplement; see `docs/quick-income-experiment.md`. The strength benchmark is correctly paired.
