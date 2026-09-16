# Bounded joint-action experiment

Follow-up to `d4670dd`, 2026-09-15. This is an opt-in strategy screen, not an arena promotion.
The predeclared protocol and full outcome are in DEVLOG; the hypothesis comes from
[joint-action diagnostics](joint-reply-study.md).

## Candidate and fallback

`current?USE_ROLLOUT_POLICY=0,USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1`

Since v06 promotion, historical EvalBot recipes below require `USE_ROLLOUT_POLICY=0`.
Their original default-v05 decisions remain historical records, not the current selection.

All other failed experiments remain off; defaults still implement v05. The new flag requires
K2/R1, sabotage enabled, and no conflicting income/direction/early-fallback/opponent-model or
interruptible-evaluation flags. The hot path uses primitive buffers allocated only when enabled.

Keep the same two distinct affordable prefixes and one predicted simultaneous response. Select
known-state targets using the existing complete sabotage chooser: before construction and after
each prefix, marking the full planned route. These scans are deterministic and restore the
original noise parameter; they do not consume extra RNG. No recorded actions enter gameplay.

Cross both prefixes with the deduplicated target set (at most four): no hit, pre-build target,
each prefix's target. Start with the original K2/R1 prefix plus its own known-state target.
All evaluated pairs use the same reply coverage and existing projected value; no separately
weighted sabotage score. Both hits precede ink and scoring through shared core operations.
The original no-own-action baseline, route completion/risk formula and its documented limits
are unchanged. There is no search over future targets or exhaustive target enumeration.

The entire search uses the original clock and 6-ms build-phase reserve. Incomplete target setup
or reference evaluation leaves normal play in place. An incomplete later pair is discarded;
the best previously completed strict improvement can still be emitted. Ties retain the original
pair. If no strict improvement was found, normal leftover construction/sabotage remains.
If a new pair is committed, emit exactly its prefix and target: suppress leftover construction
so execution matches the evaluated combination. This can spend less paint and is part of the
candidate, not a pure formula-only ablation. A phase boundary is not a hard atomic-call deadline.

The shared-state examples now select prefix 0 with targets **45 / WAIT / 29**. In particular,
the first example changes only sabotage, not the route; the second chooses no hit instead of
making its future route more vulnerable. This verifies the intended mechanism, not match strength.

## Evidence protocol

One serial cost check reuses all 110 saved positions (24 games / six maps), fresh NOISE=0 bots,
36-ms play budget, initialization outside the clock, alternating control/candidate order.
Control is the exact retained K2/R1 engine. It is not a replay of historical execution or a
CG process deadline test. `gradlew jointActionCheck` refuses to overwrite its report.

Cost result: control mean/max **12.81/31.84 ms**, candidate **13.50/31.30 ms**; neither exceeds
36 ms in this sample. **72** joint scans complete, **5** improved pairs are committed, **8** full
actions differ. Three other full-action differences can reflect budget-sensitive construction;
they are not all joint-choice changes. Report: `build/analysis/joint-action-v1/cost.json`.

Fixed strength screen: 150 new generated seeds **21000..21149**, each played as P0/P1, 300 games
per matchup, two workers, CG **486/36 ms**, NOISE=0.1. Candidate-v05 first; then fresh v05-pressure
and candidate-pressure controls on the same map identities. No adaptive sample extension,
parameter tuning or reserved confirmation games. The normal gate is >=55% and the map-grouped
95% lower bound >50%; a common-opponent result cannot replace it.

First result: candidate-v05 **130-169-1**, **43.5%**, map-grouped CI **32.4-54.6%**. Gate failed;
current defaults remain v05. This is a whole-candidate comparison, not a matched isolation of
joint search versus pure K2/R1 on these new maps. Do not attribute the entire difference to the
new flag by comparing a historical K2/R1 percentage from a different seed pool.

Per-game strength reports: `build/bench/joint-action-v1/`. Candidate and restored-v05 artifacts
each receive the predeclared strict four-game process sample against v04, with separate retained
reports. Passing this limited sample cannot resolve the previously unexplained process-input stall.

Completed common-opponent result: candidate-pressure **196-104, 65.3%**, CI **54.2-76.4%**;
v05-pressure **195-105, 65.0%**, CI **53.9-76.1%**. All 150 map hashes/seeds/sides and full
candidate/v05/pressure configurations match across reports. Joining by map and side gives
**57 added wins / 56 lost / 187 unchanged**, net +0.33 percentage points. This is descriptive,
not proof of equivalence or statistical improvement. All 900 games have zero bot errors.
The >50-ms local slow-move counters are 0/0 (candidate-v05), 2/0 (v05-pressure), 1/0
(candidate-pressure); they are not counts of every 36-ms search-budget overrun or process failures.

Final verification: **162 tests pass, cgValidate 264/264 exact**. Candidate submission is
**93,881/100,000 characters**, source SHA-256
`f7e635a5df3c9277df97e7c8c08a018b3eac4a56c8589d5581c65adefea4798e`.
Strict four-game sample has **0 failures**, reader first/later maxima **669.64/37.24 ms**.
Restored frozen-v05 is **65,269 characters**, source SHA-256
`5d0621acb2f4b819c0d9ce10a0589502832a5dea554c2e4d33549c66d2019484`;
same four input maps/sides/seeds, **0 failures**, maxima **650.75/38.88 ms**. Its source differs
from the previous artifact because Params declares the new unused default-off flag; frozen
strategy/core/runtime sources were not changed. No upload and no old-timeout-cause conclusion.

**Decision:** do not promote or tune this screen; both flags remain off. Before attributing the
result specifically to joint choices, a separate fixed direct K2/R1 joint-on versus joint-off
ablation is the next diagnostic. The present whole-candidate v05 comparison cannot isolate that
effect. Captured-state improvements and small common-opponent differences do not bypass the gate.
The direct ablation was subsequently completed below; it did not establish a benefit either.

## Direct on/off follow-up (2026-09-16)

Committed the whole-candidate screen as `b4d842442e5feb96801c9d0d802dcc947b18639d`, then ran
the predeclared ablation without changing any executable source. Both sides use current code,
not the archived engine: K2/R1 with `USE_JOINT_ACTION=1` (A) versus `=0` (B). All 47 effective
parameters were checked: that flag is the only difference, and A equals the previous candidate.

Exactly **300 games / 150 paired maps**, seeds **21000..21149**, same initialization hashes as
the previous screen. These are reused development maps, not an independent confirmation pool.
Fresh bots for each game, both sides; NOISE=0.1, Params.SEED=1. The bot's initial RNG seed is
`SEED * 1_000_003 + playerId * 7919`, not the map seed. Java 17.0.12, two workers, CG 486/36-ms
search budget and 6-ms reserve, no competing timing-heavy job. One completed run, **194.8 s**.

```powershell
.\gradlew.bat bench "-Pbot1=current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1" "-Pbot2=current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=0" "-Pgames=300" "-Pthreads=2" "-Pseed=21000" "-Ptiming=cg" "-Pout=build/bench/joint-action-v1/joint-on-off.json"
```

Report path was checked unused before launch; do not overwrite it to repeat the experiment.
Result: **139-159-2, 46.7%**, map-grouped Hoeffding 95% CI **35.6-57.8%**. A as P0:
**67-82-1**, as P1: **72-77-1**; side percentages in console count wins only, not half-draws.
Mean score difference **-150.0**, **76.5** turns/game, **241/300** early closures, no bot errors.

Accepted sabotage A/B **80.72% / 82.32%**; construction **91.81% / 92.01%**;
cells/turn **2.188 / 2.205**, paint/turn **2.708 / 2.730**. These aggregate behavior differences
come from different trajectories; they do not identify which local choice caused losses or show
that maximizing paint/sabotage frequency would help. Height slices in the report are exploratory;
none is used to pick a variant, threshold or extra sample.

Search mean A/B **8.13 / 7.69 ms**, max **52 / 46 ms**, >50-ms slow counters **1 / 0**.
These are not external-process deadlines, and the earlier input-send stall is still unresolved.
Current/Params hashes remain those listed below; compiled classes respectively
`fbb5cf08c41d5246044fb7764c62686e201ec56cd55575114cb18ba7a6ef6f32` /
`64132b4e52857f2317ccf13b01d812b6eb5711b30e5c9047256329aa672889ac`.
Full report SHA-256: `f4c7a87d1716d980cd9789a82904c151d6eb21345748dcf695d6c41f594af704`.

**Decision: inconclusive, keep both experimental defaults off.** This fixed diagnostic found
no confirmed gain; its interval also cannot establish harm or equivalence. Do not extend the
sample, tune on a selected slice or bypass the failed v05 gate. The direct matchup measures the
whole enabled package, including extra work and suppression of leftover builds, not those
components individually. Non-transitive matchups prevent assigning the prior v05 result to this
flag alone. No extra opponent run, submission rebuild, promotion or upload.

Next bounded diagnostic: on the saved 110 shared states, compare the actually emitted full
actions (including no-hit and leftover-building differences), not just projected prefix/target
values. Reuse existing evidence, separate predicted and recorded opponent responses, and resolve
through core. A local repaired choice would still not establish match strength.

Verification before this run: existing **162 tests** remain up-to-date; freshly validated corpus
**264/264 exact**. No source edits or new tests in this follow-up. Player.java remains the same
frozen-v05 artifact, SHA-256 `5d0621acb2f4b819c0d9ce10a0589502832a5dea554c2e4d33549c66d2019484`.

## Retaining old comparisons

The old experiment ids still execute their original code, not today's changed EvalBot. One
archive preserves the exact two evolving classes (Params and EvalBot), while the original
manifest verifies the shared core/Bot/Timer dependencies. No rule copies or per-turn adapters.
Original ids, hashes and 46-field configurations remain unchanged; new flags are rejected on
old ids. See [bot_catalog.md](bot_catalog.md). Archive resources/loader never ship in Player.java.

Current source SHA-256 during measurements:
EvalBot `e69497ff356729fc9e3a477dee3140274d791f7c22234f7248c2c2533357b84f`;
Params `8df5e35ec46a826303e0ec48ee6a3e54c8ae8ebc4f4c0dcf24207ea2e3bf2416`.
The cost report also records compiled-class fingerprints and full configurations.
