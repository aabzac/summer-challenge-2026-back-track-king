# v09 budget100 local sensitivity: completed result

Measured 2026-10-16; audited and recorded 2026-10-18 after the chat interruption.
The original measurement completed successfully before resumption. No games
were repeated or added on resumption.

The same batched-refresh policy with a 100 ms later-search allowance scores
**548 wins / 407 losses / 45 draws in 1,000 games against 36 ms: 57.05%**.
This supports useful sensitivity to extra local compute on this sample. It is
**COMPLETE_DIAGNOSTIC**, not an arena-ready candidate or a promotion result.

## Fixed comparison and validity

Both arms use `current?NOISE=0.1,USE_BATCHED_REFRESH=true`, with identical full
strategy parameters and missed-deadline correction OFF. Only the local caller's
later allowance differs: A=100 ms, B=36 ms. First search remains 486 ms for both;
the allowance follows each arm across seat swaps. Production `TURN_BUDGET_MS`
remains 36, and production guards still reject a 100 ms submission recipe.

The separately compiled diagnostic harness consumes the retained shared core,
referee and native strategy. No production source, existing runner, frozen bot,
submission artifact or search cap was changed. Width6, replies2, depth8,
maxRoutes64, networkCells24 and networkRoots2 remain fixed. There is no protocol
input/output measurement or submission warmup in this native comparison.

Two workers play both seats on each of 500 fresh maps. All prospective maps
are distinct and disjoint from 24,624 known or reserved map identities; seed
intervals were checked against 131 prior pool registrations. Each game gets
fresh bots. The prospective schedule and inputs were frozen before play.

Preflight passed: 292/292 exact shared-engine replays, isolated helper
compilation, fake-bot self-checks (four synthetic WAIT games, no real strategy
measurement), and 14 synthetic Python driver tests. All 2,523 frozen files,
eight external runtime hashes and 28 selected submission files were preserved.
Previously documented unrelated full-suite failures were not rerun or claimed
resolved by these focused checks.

## Strength result

- Block1, seeds261616000..261616149: 157W126L17D / 300, **55.1667%**.
- Block2, seeds261716000..261716099: 111W79L10D / 200, **58.0%**;
  cumulative **56.3% / 500**.
- Block3, seeds261816000..261816249: 280W202L18D / 500, **57.8%**.
- Pooled: **570.5 points / 1,000 = 57.05%**, zero technical game errors.

By seat, A scores **57.4% asP0** and **56.7% asP1** (500 games each).

Both interim scores cleared the strict-below-50% futility condition; no early
success was declared. All three fixed blocks completed once, in 468.867,
270.444 and 764.251 seconds. The pooled score exceeds the project's descriptive
55% reference, but this local-only experiment is explicitly ineligible for
promotion. No boss games, API calls or external deadline checks were requested.

The conservative map-grouped Hoeffding interval is **50.98% to 63.12%** at the
nominal 95% level. It is descriptive, assumes independent representative map
groups, and is not adjusted for selection through the interim futility checks.
It is not an overall confidence guarantee or a promotion gate.

## What the extra time changed

Each arm made 90,906 later native calls. Aggregating the existing counters over
those calls gives:

- Mean native play time: **19.179 ms at100 vs 13.689 ms at36**, +40.1%.
  The allowed budget increased 2.78-fold; actual mean compute increased 1.40-fold.
- Completed comparisons: **727,520 vs 569,995**, +27.64%; means **8.003 vs 6.270**
  per later call.
- Simulated rollout turns: **10,251,974 vs 7,942,022**, +29.09%; means
  **112.776 vs 87.365** per later call.
- Route queries: **9,250,987 vs 9,223,457**, only +0.30%.
- `ordinarySearchComplete`: **97.47% vs 84.05%** of later calls.
- `networkPhaseComplete`: **96.77% vs 83.73%** of later calls.

More time primarily coincides with more completed comparisons and simulated
continuations, rather than a large increase in route-query count. This makes
the cost of completing those comparisons a promising optimization target.
The experiment does not establish which particular extra comparison changed
which outcome: the two arms develop different states and may consume RNG
differently. These are trajectory aggregates, not same-state causal timings.
Completion flags cover retained comparisons; they do not prove exhaustive
route enumeration or game-tree search. Opening returns are included in the
later-call denominators and can leave completion flags false by design.

At100, **11,134 / 90,906 later calls (12.25%) exceeded50 ms**; 1,144 exceeded100
ms, and the maximum was **114.646 ms**. At36, three later calls exceeded50 ms,
maximum **55.823 ms**. These are native wall durations, not protocol response
times or server timeout reports. The observer adds symmetric measurement
overhead; referee timings additionally include state copying and use floored
milliseconds. Per-call samples were not retained, so medians/percentiles and
the exact count of early finishes cannot be recovered from these aggregates.
First native calls used the protected opening in all1,000 games per arm;
their mean durations were2.110/2.077 ms, despite the unchanged486 ms allowance.

## Decision

The time limit matters for this policy: relaxing it to100 ms produced a clear
positive result on this fixed local comparison. The bot also has finite search
limits, so extra allowance is not spent on every turn. A useful next experiment
would target cheaper completion of the existing comparisons within36 ms,
preserving their strategy and guards; this report does not implement that work.

The earlier40 ms experiment scored51.0% against36 on different maps. The two
experiments are not a direct100-vs40 comparison or a measured response curve.
Keep default36 ms, batching OFF and selectedv09; retain all historical verdicts.
No arena upload, promotion, push or new live games occurred.

The independent audit is **PASS**, issues=[]: it recounts raw scores/seats/counters, verifies the fixed
schedule and freshness, and checks source/runtime/artifact preservation. Its
tracked-source inventory explicitly excludes the six pre-existing untracked
pduhard/ppo sources, which were never part of the frozen runtime. The audit
helper correction changes no measured input or outcome.

[Prospective protocol](v09-budget100-local-v1.md),
[audited evidence](evidence/v09-budget100-local-v1.json),
[archive inventory](evidence/v09-budget100-local-v1-artifacts.json),
[exact raw results and retained runtime](evidence/v09-budget100-local-v1-artifacts.zip).
