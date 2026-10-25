# prune-v1 — the rejected rollout experiments leave `current` (2026-10-25)

**Goal.** Make room in the 100,000-character arena artifact for the Stage 3–4 valuation work without
changing a single decision of the selected version. v10 "Boss Model" shipped at **99,676 / 100,000**
characters; nothing fits on top of it (`docs/v10-promotion.md`, PLAN "Prune (`prune-v1`) — now the
first task").

**What leaves `current`.** Seventeen rollout experiments that were screened and rejected or parked
between v06 and v09 and have been `false` in every selected version since: `USE_ROLLOUT_ALL_CELL_RISK`
(tactical bypass routes, `TacticalPathing`), `USE_ROLLOUT_ROOT_FILL`, `USE_ROLLOUT_QUICK_EARLY`,
`USE_ROLLOUT_QUICK_VALUE`, `USE_ROLLOUT_QUICK_SAFE`, `USE_ROLLOUT_DIVERSE_REPLIES`, `USE_ROLLOUT_REPLAN`,
`USE_ROLLOUT_SECOND_TARGET`, `USE_ROLLOUT_SPREAD`, `USE_ROLLOUT_NETWORK` (the pre-base network roots;
`USE_ROLLOUT_NETWORK_AFTER_BASE` stays selected), `USE_ROLLOUT_ROUTE_DEPENDENCIES`,
`USE_ROLLOUT_NETWORK_CHORDS` (`NetworkChords`), `USE_ROLLOUT_RHEA` (code already gone since v09),
`USE_ROLLOUT_LIVE_TARGET`, `ROLLOUT_ALWAYS_DISRUPT`, `USE_ROLLOUT_TRACK_VALUE`,
`ROLLOUT_TRACK_VALUE_SELF_ONLY`. `RolloutBot.java` goes from 1,601 to 1,076 lines; every removed
statement was guarded by one of these default-false flags, or was a helper reachable only from such a
statement (`addSpreadRoots`/`fillSpread`/`independentRegions`/`remainingPaint`, `addChordRoots`/
`chordCells`/`chordCheaper`, `selectReplyRoutes`/`sameProject`/`sameCorridor`/`replyIndex`,
`reconsider`, `considerQuick`'s value branch and `quickBuildTurns`, `captureDependencies`/
`copyDependencies`/`missingDependency`, `searchRoot`, `trackValue`, `observeLive`). The
`MISSED_DEADLINE` and `BOSS_SABOTAGE` blocks stay in the source: the merger already strips them from
the default artifact and they are still live experiments / the screening proxy.

**What leaves the artifact only.** `Params` fields that the shipped rollout never reads — the pruned
flags and their companions, the EvalBot/heuristic/pressure/forecast weights, `MESSAGE`/`DEBUG` — are
wrapped in `// LOCAL_ONLY_BEGIN/END` blocks (86 fields). The fields stay in `Params.java` because the
frozen packages (v05–v10), EvalBot, HeuristicBot and the tools read them; `FileMerger` drops the blocks,
their values in the embedded `new Params("…")` configuration, and the `NetworkChords`/`TacticalPathing`
classes from **current rollout artifacts only**. Frozen exports (`-PsubmitVersion=v10` included) and
the EvalBot/heuristic/neural/spatial artifacts keep every field and class, byte for byte.

**Guards.** `BotFactory.PRUNED_ROLLOUT_FLAGS`: a true value of any pruned flag on `current`/`main` is
refused (`parameters()` and the `RolloutBot` constructor), never silently ignored; `v10?FLAG=1` still
runs the mechanism in the frozen copy. `BotFactory.V10_FLAGS_OFF` (the seven selected flags off = the
v09 configuration) is the shared constant for tools and tests. The historical mechanism tests moved
from `btk.bot.current` to `btk.bot.v10` with `RolloutBot → RolloutBotV10` and `current? → v10?`
(twelve files; `RolloutQuickRouteTest` keeps its three live quick-route tests on current). Tools that
exercise a pruned mechanism (`RolloutRiskStudy`, `RolloutRiskPolicyCheck`, `RolloutFillPolicyCheck`)
run it on v10.

## Equivalence check (predeclared)

1. **Decisions, ample time** — `btk.bot.PruneV1Test`: on one generated map per board size
   (seeds 1…, the 30×20 board included) and both seats, `current` and `v10` play the same seat in
   lockstep against a `v10` opponent with a 60 s budget per call (every search completes, the seeded
   noise stream is shared), up to 60 turns per game. Every turn the two action lists and the search
   counters (`routeQueries`, `completedComparisons`, `simulatedTurns`, `selectedValue`, `selectedRoute`,
   `selectedTarget`, `networkRoots`, `fillCells`) must be identical. This is the equivalence proof.
2. **Artifact** — `btk.submit.PruneV1SubmissionTest`: the default artifact declares none of the
   local-only fields, embeds only declared fields, carries neither `NetworkChords` nor
   `TacticalPathing`, compiles, and resolves to the same 130-value configuration (same `config=` hash
   as the arena artifact, since the configuration itself did not change); `FileMergerTest`'s compiled
   decision parity (`checkFrozenDecisions("current")`) covers the compacted artifact; the frozen `v10`
   export keeps its pins (99,598 chars, `9c3b4008…`).
3. **Timed path** — `tools/boss_proxy/run_prune_screen.sh`, fresh pool seed **1001000** (first use),
   300 paired games per cell, 2 workers, CG timing: `current` vs `v10` head-to-head (expected 50 %,
   v5 step (b) reading ≥ 48 %), and both vs the boss-like proxy on the same maps/seeds/seats with the
   paired per-map delta as the reading. A regression guard only; it cannot improve the verdict.
4. **Process** — `gradlew submit` of the default artifact, `verifySubmission`, strict `procCheck`
   (zero grace, 4 games vs v09): 0 problem games.

No arena upload: the resolved configuration is unchanged, so the pruned artifact plays exactly like
the one on the arena; the first upload of pruned code comes with the first Stage 3–4 candidate.

## Results (2026-10-25)

| step | result |
|---|---|
| Decisions, ample time (`PruneV1Test`) | **PASS** — one map per board size (21×14, 23×15, 24×16, 26×17, 27×18, 29×19, 30×20), both seats, up to 60 turns each: identical action lists and identical search counters on every turn; `prunedExperimentsAreRefusedOnCurrentAndStillRunOnFrozenV10` PASS. |
| Artifact (`PruneV1SubmissionTest`, `V10SubmissionTest`, `FileMergerTest`) | default `gradlew submit` → **70,397 / 100,000 chars** (was 99,676), sha `5047c83a…`, build `f7caee04…`, **config `f0e507ac…` = the arena artifact's**; 22 classes (no `NetworkChords`, no `TacticalPathing`), none of the 86 local-only fields declared or embedded; compiled decisions match (`checkFrozenDecisions("current")`); frozen `v10` export unchanged (99,598 chars, `9c3b4008…`), `v09` unchanged (`aca0be45…`). Manifest in `evidence/prune-v1/default-artifact-manifest.json`. |
| Timed path (`run_prune_screen.sh`, pool 1001000, 300 paired games per cell, 2 workers, CG timing) | `current` vs `v10` head-to-head **50.7 %** (151-147-2; ≥ 48 %); vs boss-v2 on the same maps/seeds/seats **68.3 %** (205-95) against `v10`'s **70.3 %** (211-89), paired **−2.0 ± 6.5 pp** (maps better/worse/equal 32/35/83); zero errors; mean move 13.9–14.4 ms, max 37 ms for the pruned bot in both cells (the `v10` control cell saw host contention: 6 moves over 50 ms, max 155 ms, none failed). `evidence/prune-v1/summary.txt`, the three reports beside it. |
| Process (`procCheck`, zero grace, 4 games vs v09) | **0 problem games**, 0 timeouts, first response ≤ 668 ms, later responses ≤ 38.6 ms, 2/4 wins (`evidence/prune-v1/default-artifact-process-check.json`). |
| Tests | 822 tests, 25 failures, all pre-existing (19 via the retained-engine pin incl. `FileMergerTest.characterLimitIsCheckedBeforePublishing` and `NeuralTargetSizeProbeTest`, 6 `PduhardBeamBotTest`). Pins of every current-rollout artifact moved (recorded in the DEVLOG entry). |

**Verdict: equivalence established, 29,279 characters of room for Stage 3–4.** No upload: the arena entry
(agent 6811139) runs the same configuration; the first upload of pruned code comes with the first
Stage 3–4 candidate that passes policy v5.
