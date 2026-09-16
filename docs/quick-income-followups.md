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
