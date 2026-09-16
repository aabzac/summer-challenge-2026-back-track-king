# Quick-income follow-ups, registered before play

The user requested trying all three proposed improvements in order. Each is a separate
default-off variant of the original quick-income candidate, not a cumulative tuning chain.
Selected control B remains frozen v06 and common opponent C remains frozen v05. No promotion
or upload is authorized. Old quick-income results retain their original verdicts.

1. **early:** USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_QUICK_EARLY=1. Keep the ordinary
   first root/fallback, then compare the retained quick route before remaining roots. Preserve
   the portfolio and continuation order, width, depth, responses and complete-comparison rule.
2. **value:** USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_QUICK_VALUE=1. Change only quick
   selection from least paint to estimated early net earnings per actual construction turn.
   Simulate affordable placement packing on the known board, without opponent actions, for
   the at-most-six-paint route. Use the existing route horizon; keep ordinary root ordering.
3. **safe:** USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_QUICK_SAFE=1. Keep least-paint
   selection, but require positive net income gain after the already predicted threat region
   is inked. Reuse the existing core threat evaluation. No new threat scans or route searches.
   This deliberately conservative filter may reject profitable short-lived routes; test that
   tradeoff rather than assuming that survival guarantees better play.

All other experiment flags remain off. Implement, validate and evaluate each in order. Do not
retune a variant after viewing its results, rerun a failed screen or combine failed variants.
Shared-state diagnostics use every zero-own-income state in turns 0..19 of all 14 corrected
v06 boss games, including wins and losses. Fresh NOISE=0 bots get nominal 486/36 ms and an
ample-time comparison. These are mechanism diagnostics, not strength or original CG timing.
Recorded simultaneous opponent actions are only one-turn counterfactuals after policy choice.

## Fixed strength protocol

Use practical-300-1000-v2, draws half, authentic generator, SEED=1, nominal CG 486/36 ms,
two workers, sequential runs without other heavy jobs. Each variant uses its own fresh pools:

- early: screen seeds 100000..100149; direct 101000..101499; style screen 102000..102149;
  style full 103000..103499.
- value: screen 110000..110149; direct 111000..111499; style screen 112000..112149;
  style full 113000..113499.
- safe: screen 120000..120149; direct 121000..121499; style screen 122000..122149;
  style full 123000..123499.

After exactly 300 direct games: <=52% stops; >52% and <60% receives independent fixed 1000
at >=55%; >=60% skips direct 1000. Direct failure stops before common-opponent play.
After direct success, A vs C and B vs C each play 300 on the same new 150 maps: >=+3 percentage
points passes. Otherwise each plays 1000 on the same separate 500 new maps; nonnegative
candidate-minus-control passes. No pooling, tuning, retries or early outcome stopping.
Every stage excludes all known previous benchmark map hashes, including earlier variants.
The matched control intentionally shares the candidate's common-opponent maps and exclusions.
Unused historical reserve pools remain untouched. Record full parameters, code/class hashes,
map identities, per-side outcomes and every error/timing outlier; verify identities per stage.
CI is descriptive, with no overall 95% guarantee or multiple-candidate selection guarantee.

Engine, legality and default-v06 equivalence checks precede play. Passing strength requires
additional exact-artifact process checks before a readiness claim. Keep the selected Player
unchanged during rejected strength experiments. Results and any deviations are appended below.

## Host timing deviation observed during early

Windows Kernel-Power events confirm repeated Modern Standby during the early screen and its
continuation. Screen 300 has extreme search maxima A/B 93125/266542 ms, including paired pauses
around seeds 100056/100057 and 100143/100144. All games/outliers remain included; no retry or
replacement pool. A one-off JVM thread snapshot confirmed active workers, not a deadlock.
At 2026-09-16 19:21:15 UTC, during the first direct continuation, a temporary
task-scoped ES_CONTINUOUS | ES_SYSTEM_REQUIRED request was started to inhibit automatic sleep.
It does not change global power settings or override intentional user sleep. Release it at task
completion (four-hour upper bound). Windows API reference:
https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-setthreadexecutionstate
Initial event evidence: build/analysis/quick-followups/power-events-initial.json; exact helper
start/release timestamps: build/quick-followups/awake-start.json and awake-end.json.
These interrupted timed games have an environment limitation; a numeric gate result must not
be described as clean timing or as proof that search-order changes caused the observed score.

The system-only request did not prevent later Modern Standby on battery. At 19:39:08 UTC,
after the early strength series, it was replaced with a task-scoped system AND display request.
The old request was released. The replacement also expires after four hours and must be released
at completion. No system power-plan settings were changed.

## 1. Early comparison - NOT PASSED

- Default-off flag moves the quick root just after root zero; no portfolio/continuation rewrite,
  extra route queries or wider/deeper rollouts. Earlier visitation also affects exact-value ties.
- Captured study: 77 own-zero-income early positions, 50 from losses and 27 from wins. Nominal
  original/early: retained 67/68, fully compared 53/61, selected 14/21. Ample: retained 70/70,
  fully compared 70/70, selected 21/22. Nominal actions differ on 14 states, ample on one.
  One-turn recorded-opponent counterfactuals: nominal better/equal/worse 3/73/1; ample 1/76/0.
  Fresh instances and timing-dependent proposal pools limit causal interpretation.
- Screen 300, seeds 100000..100149: **164-130-6, 55.67%**, zero errors; continue.
- Independent 1000, seeds 101000..101499: **489-495-16, 49.70%**, zero errors; below 55%.
  Descriptive map-grouped CI 43.63..55.77%; mean score difference -42.4. Elapsed 1833.1 seconds.
  Direct search maxima A/B 140985/140994 ms include host standby pauses; all retained.
- Stop before common-opponent stages; no promotion, no retry, no new process-readiness claim.
  This failed, interrupted timed sample does not establish an arena benefit or prove harm.
- Validation: **227 Java tests pass**, default decisions agree with frozen v06 in existing
  evolving-board tests, **264/264 arena replays exact**. Initial two parameter-record tests
  required acknowledging the newly added inactive flag; immutable frozen snapshots unchanged.
- Durable evidence: `docs/evidence/quick-followup-early-v1.json` and
  `docs/evidence/quick-followup-early-diagnostic-v1.json`. Full reports/launch identity snapshot:
  `build/bench/quick-followup-early-v1/`. All source/class identities matched before/after play.

## 2. Income per actual construction turn - STOP_SCREEN_300

- Independent VALUE flag, EARLY off. Among otherwise eligible quick routes rank by
  `netGain * (min(routeHorizon, turnsLeft) - actualBuildTurns + 1) / actualBuildTurns`,
  with lower paint breaking ties. Core placements on an existing private scratch board and
  the actual packing function count construction turns; no opponent action or new path query.
- Two mechanism regressions, both player sides: prefer six-income over four-income routes
  taking two turns each; three river cells costing six paint require three turns, not two.
- Same 77 captured states: nominal retained/fully-compared/selected **70/64/10**;
  ample **70/70/12**. These diagnostics do not establish strength.
- Fixed 300, seeds 110000..110149: **150-138-12, 52.00%**, zero errors. Exactly 52% stops
  under the predeclared strict continuation floor. No 1000, matched v05, retry or promotion.
  Descriptive CI 40.91..63.09%; mean score difference +98.1; elapsed 461.8 seconds.
  Search max A/B 51/42 ms, slow moves 1/0; not an external-process deadline check.
- **229 Java tests pass**. Core unchanged since 264/264 exact validation; default equivalence
  and all earlier regressions pass. All source/class identities matched before/after play.
- Durable evidence: `docs/evidence/quick-followup-value-v1.json` and
  `docs/evidence/quick-followup-value-diagnostic-v1.json`; raw report/launch snapshot under
  `build/bench/quick-followup-value-v1/`. Retain default-off, selected v06/Player unchanged.

## 3. Predicted-threat survival filter - STOP_SCREEN_300

- Independent SAFE flag, EARLY and VALUE off. Require positive net-income gain relative to
  the threatened baseline after the already predicted region is inked; choose least paint
  among eligible survivors. No extra path queries, disruption scans or rollout scenarios.
- Regression on both sides: the cheapest three-paint completion is destroyed by the known
  predicted region; retain a six-paint alternative. With no predicted threat, keep the original
  cheap choice. Input state remains unchanged.
- Same 77 captured states: nominal retained/fully-compared/selected **64/52/14**;
  ample **67/66/19**. One ample quick root shares its complete first action with an earlier
  root and is deduplicated, not interrupted. Mechanism evidence only.
- Fixed 300, seeds 120000..120149: **153-142-5, 51.8333%**, zero errors. Below the >52%
  continuation floor: no 1000, matched v05 stages, retries, promotion or new process claim.
  Search max A/B 46/45 ms, slow moves 0/0; elapsed 543.3 seconds. In-process timing only.
- **230 Java tests pass**. Core and frozen versions unchanged, 264/264 exact validation remains
  applicable. All source/class identities matched before/after play. Durable evidence:
  `docs/evidence/quick-followup-safe-v1.json` and `quick-followup-safe-diagnostic-v1.json`.

## Combined decision

Completed all three predeclared independent variants, **1900 new games / 950 distinct maps**,
zero bot errors. Each failed the practical gate; none reached common-opponent testing.
Do not rank their percentages as a matched head-to-head comparison: each used fresh maps,
and the first variant also suffered recorded standby pauses. No combined variant or retuning
was attempted. Retain all flags OFF, selected v06 and the previous selected Player unchanged.
The known old v06 external-process timing issue remains open; these runs do not resolve it.
Final selected Player SHA-256 remains
`5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266`.
Temporary sleep/display requests are released after testing; no global power settings changed.
