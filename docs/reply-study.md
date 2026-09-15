# Shared-state narrow reply audit

`replyStudy` diagnoses the retained K2/R1 experiment without changing strategy, shared rules or
submission code. It is not another promotion bench. Its initial protocol was fixed in DEVLOG
before capture: seeds 20000..20005, both sides, v05 and the K2/R1 candidate vs pressure-v1,
24 serial games at nominal CG 486/36-ms search budgets. No outcome-based selection.

```powershell
.\gradlew.bat replyStudy "-Pmode=capture"
.\gradlew.bat replyStudy "-Pmode=analyze"
```

Defaults: `-Ppath=build/analysis/reply-study-v1/capture` is the capture directory;
`-Pout=build/analysis/reply-study-v1/report.json` is the analysis report. Capture refuses an
existing directory; analysis refuses an existing report. To reanalyze, use the SAME capture
directory and a new report path, not fresh games. Capture records a manifest and each completed
game immediately; an interrupted/incomplete manifest is not accepted as the planned full sample.
These audit files contain the replay inside a `replay` field, not top-level visualizer JSON.

All game files are hashed in the manifest. Before analysis, commands are replayed through core
and scores, gained income, ownership and region instability/ink are checked at every turn.
Pre-turn positions at t10/20/30/40/50 are selected, at most 120. Games ending before a selected
turn remain in the corpus; unavailable positions are counted, not replaced. Both source policies
are reported separately because their trajectories may cover different states.

## What is compared

- **Prediction**: the actual private first-build/reranking methods of the pinned engine, via
  tools-only reflection. A zero-budget play reuses real input preparation; then a large ceiling
  allows the first decision to complete. No copy of its pathing or value formula is maintained.
  The probe's selected prefix must match the start of ample-time full play.
- **Known-state**: both retained affordable prefixes evaluated against an opponent WAIT.
- **Predicted reply**: those SAME prefixes evaluated against the model's placements and sabotage.
- **Recorded-action oracle**: those SAME prefixes evaluated against the pressure bot's recorded
  simultaneous action. This future action is used only after prediction is fixed. It never enters
  candidate generation or either full-play call. Internal optimized simulation is checked against
  `Rules.resolve` for each combination, including collisions and same-turn ink.
- **Immediate score increment**: core's current-turn score-difference improvement over our WAIT
  against the same opponent action. Comparing its ranking with the oracle heuristic reveals a
  short-term/long-term tradeoff, not automatically an evaluator error. Future route completion,
  costs and vulnerability still enter the existing heuristic.
- **Budget sensitivity**: fresh full play at 36 ms and at a 60-second ceiling (not a forced wait),
  alternating call order by position. Record all emitted actions, accepted cells, sabotage and
  elapsed time. A prefix/action difference is stronger evidence than elapsed time alone, but does
  not prove the cause of a historical loss or timeout.

The counterfactual reranking compares only the FIRST build prefix, as the existing model does.
It does not include our later sabotage or leftover-paint construction; full-play actions include
both. Thus oracle improvements are improvements under a limited heuristic, not recoverable match
points or proof that an oracle-enabled bot would win. Do not sum them as final score gains.

## Important controls and limits

Capture uses the played candidate's complete pinned configuration, including NOISE=0.1. Probes
explicitly use NOISE=0 and fresh instances to isolate deterministic choices; other flags stay off.
They are not reconstructions of a live candidate's hidden RNG/history. Complete decision scans
are checked to have finished before any phase cutoff. Full-play timing includes preparation;
the standalone first-build probe excludes its priming and includes reflection overhead.

JIT/GC/host effects and earlier diagnostic calls can affect subsequent timing. These are local
in-process measurements, not CG CPU time or an external-process readiness test. The captured
games are new trajectories on previously inspected development maps, not the old 900-game screen
recreated exactly. No extra variants, confirmation-pool games or promotions follow automatically.

Prediction agreement excludes states with no build candidate, where this model is never called.
Exact empty-vs-empty placements are shown separately through the actual-build denominator; cell
precision/recall can be obtained from intersection/predicted/actual counts. Disruption agreement
uses recorded non-WAIT disruption states. `oracleImprovesChoice` requires a STRICT improvement
under the same heuristic, not just a different tie-break. Null heuristic means rejected route,
not zero value. Full-action differences include all positions; prefix differences only those
with a retained build. Results and the selected follow-up are recorded after the fixed run below.

## Results

Completed once on 2026-09-15: all 24 games captured, 110 eligible positions and 10 unavailable
after game end. The 110 observations contain **108 distinct state/perspective identities**;
do not treat either repeats or turns on the same six maps as independent trials. Source-v05
trajectories contribute 53 positions, source-K2/R1 57. No bot errors or replay/core mismatches.

- The first-build model ran on **81 observations (79 distinct states)**, with two candidates
  on 77; 29 positions used no such candidate. All 81 ample probes matched full-play prefixes.
- Recorded pressure builds were predicted exactly in **12/55** nonempty-build observations
  (21.8%). Including six correct empty cases, placement agreement is **18/81**. Cell overlap
  is 48 of 192 predicted and 144 actual cells: micro precision **25.0%**, recall **33.3%**.
  The model predicted a build on **20/26** observations where pressure did not build.
- Disruption target agreement is **61/81 (75.3%)**; pressure disrupted on every eligible
  model observation, and the model never predicted no sabotage in those observations.
- Substituting the actual pressure response strictly improves the selected heuristic value in
  **4 observations / 3 unique states**, all among the two-choice cases. Two occurrences come
  from each source policy. Another argmax index changes only because of a tie; it is NOT counted.
  Thus poor action prediction does not necessarily translate into poor ranking of these two
  retained candidates under this heuristic. This does not bound the benefit of other candidate
  sets, better evaluation or a richer model, and is not proof prediction never matters.
- With the actual opponent action, the heuristic's preferred route has a smaller immediate
  score increment than an alternative in **11 observations**. Example seed 20000/P0/t30:
  projected values **1382.67 vs 963.33**, immediate increments **0 vs +51**. This is evidence of
  investment preference, not proof that the short-term option wins the match. One conflict
  involves a rejected future route with a small positive current gain; null is not scored as zero.
- **0/81 first prefixes differ** between nominal and ample full play. Full actions differ in
  **5/110 observations**, all in later/leftover construction with the same first prefix and
  sabotage target. Nominal mean/max **12.04/31.25 ms**; ample **16.65/112.52 ms**. These are
  alternating-order fresh-instance diagnostics, not an original-loss timing reconstruction or
  a hard 50-ms process pass. More time did not change the first decision in this sample.

Three compact checked-in fixtures (`src/test/resources/btk/bot/reply-study.json`) preserve initial
protocol, pre-turn action history, separately labelled opponent oracle and expected choices:

- seed 20002/P0/t10: wrong placement/collision forecast; actual sabotage target was predicted
  correctly, but oracle projected ranking changes from candidate 1 to 0 (1222 vs 1364).
- seed 20003/P1/t10: differing construction and sabotage target; projected ranking changes from
  candidate 0 to 1 (1512.43 vs 1955). This example does not isolate which difference is causal.
- seed 20000/P0/t30: immediate-versus-projected tradeoff above; oracle does not change selection.

Report: `build/analysis/reply-study-v1/report.json`, SHA-256
`6c407ef3dc420b0cb85257b5be6340b65a302f1a45ca10fd0d313fc3792be7bd`.
Capture manifest SHA-256 `652b2c28ca30e62ea974d58d740fb9210f8f76d213ed905364102d059f196d0b`.
Post-analysis fixture extraction/deduplication used the retained data without new capture or
measurement reruns. Later input-validation hardening changes neither probe nor report semantics.

**Decision:** keep the experiment disabled, no v06 or new submission. Neither increasing the
beam nor improving prediction alone is justified by these observations. The next narrow
hypothesis is to inspect build-and-OWN-sabotage jointly: the current first-build ranking omits
our later sabotage and leftover paint. Test its effect on shared states before implementing a
policy change; omitted interactions are a candidate explanation, not a proven cause of losses.
