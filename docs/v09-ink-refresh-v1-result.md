# v09 incremental ink refresh: result

2026-10-18. **COMPLETE_DIAGNOSTIC**, no promotion decision.

Selective refresh after hypothetical sabotage reduces the cost of the same
search by **15.64%** on the fixed saved-state panel. At a 36 ms internal
allowance, completed comparisons increase **14.98%**, from 2,523 to 2,901.
All 524 ample-search decisions and semantic diagnostics match exactly.
The deferred attack on turn 40 and all six winning-closure controls survive.
The exact main submission passes the fixed eight-game strict process sample.

This supports a separate strength experiment. It does not establish a win-rate
gain or promote the implementation. Default 36 ms, batching OFF, incremental
refresh OFF, missed-deadline correction OFF and selected v09 remain unchanged.
Earlier batching, budget and boss verdicts retain their historical meanings.

## Profile and selected change

Follow the [prospective protocol](v09-ink-refresh-v1.md). Profile the retained
exact batched36 submission on every nonterminal state, both seats, of games
907134258, 907134581, 907134651 and 907134720: 524 inputs with persistent policy
instances and a 5,000 ms allowance. No new profile games or selected fast rows.

Among 2,398 JFR samples inside generated policy calls, inclusive attribution is:

- Connection refresh: 1,168 samples, **48.71%**.
- Sabotage target selection: 1,094 samples, **45.62%**.
- Cheapest-path construction: 627 samples, **26.15%**.

These are overlapping call stacks, not additive time shares. Leaf samples
attribute 23.98% to path reconstruction, 17.35% to cheapest-path construction,
15.97% to BFS and 8.01% directly to target selection. Sampling, inlining and
stack truncation limit precision; instrumented time is not a readiness result.

The single implementation change is `USE_INCREMENTAL_INK_REFRESH`, default OFF.
When evaluating hypothetical sabotage, keep already canonical connections that
remain passable and recompute only broken ones. Ink only removes connectors:
an unaffected canonical shortest path remains canonical, and a missing path
cannot become available. Broken paths use the existing canonical algorithms;
destination batching is retained when enabled. No new per-node allocation or
buffers are introduced.

Rules and the refresh helper stay in `btk.core`. Target order, candidates,
evaluation, RNG, depth, widths, timer checks and all safeguards are unchanged.
In particular, terminal reachability is still checked: ink can block future
construction through free cells even when no existing connection is broken.

## Correctness and equal-work comparison

Focused tests: **248 passed out of 249**, including all seven new core tests
and three new submission tests. The retained failure is the existing historical
catalog pin in `FileMergerTest.characterLimitIsCheckedBeforePublishing`: the
old `exp-income-guard` entry rejects changed `Pathing.class` with
`IllegalStateException`, before the expected unsupported-export rejection
(`IllegalArgumentException`). The preceding size assertions pass. Historical
pins were not changed or bypassed. This is
not a claim of a fully passing repository suite. The initial Gradle invocation
could not access its external cache lock; its failed log is also retained.

`cgValidate`: **292/292 downloaded games reproduce exactly**. Disabled current,
frozen v09 and old batched exports remain byte-identical. Plain/compacted
generated classes agree in both batching modes; the 50 ms limit and margins
are unchanged. The main enabled artifact is **91,202 / 100,000 characters**,
source SHA-256 `6217083e6319f69a29619f83c7de43d99876153b1dc9d043ca3376f5bccf8124`.

The main comparison uses batching ON, NOISE 0.1, missed-deadline correction OFF
in both arms; only incremental refresh differs. Both generated policies see
the same input chronology with their own persistent memory. Arm order rotates.
All 524 ample calls finish and match ordered actions, accepted cells and target,
raw selected-value bits, and every semantic diagnostic key and value.

Each arm performs exactly 60,118 route queries, 5,850 comparisons and 87,630
simulated turns. Total native wall time falls from **24,379.9402 ms** to
**20,568.0980 ms**, a **15.64% reduction** for identical work. Ample calls are
deliberately allowed beyond 50 ms and do not certify response deadlines.

At first/later allowances 486/36 ms, after isolated disposable warmup:

- Completed comparisons: **2,523 to 2,901**, +14.98%.
- Simulated turns: **36,457 to 41,578**, +14.05%.
- Fully completed search: **254 to 295** of 524 inputs.
- Ordinary phase completed: 234 to 271; network phase: 230 to 271.
- Native maximum: 41.9276 ms baseline, **43.9605 ms candidate**; neither exceeds 50 ms.

There are 175 differing timed actions. The timed paths can diverge because additional completed work changes cutoff
decisions and later RNG history. These are saved-state coverage measurements,
not complete-game strength results or a universal latency guarantee.

## Attack and closing safeguards

Check the persistent turn 38 to 40 sequence from 907134651/P1, plus winning
closure starts 46, 47, 49, 50, 51 and 52 from 907134720/P1. Each has an existing
native reply condition and a passive WAIT condition: 14 paired continuations
per phase, eight turns or until terminal. Ample continuations match exactly.
At both ample and 36 ms, each arm retains six wins, with **zero lost baseline
wins**. All six native closing controls remain wins.

At 36 ms, the candidate attacks **region 33 on turn 40** in both reply regimes,
with all waiting/attack/delayed comparisons complete and unchanged construction.
Candidate native times are **29.0653 ms** against the native reply and
**29.6121 ms** against WAIT. The committed deadline is turn 39; intervention
still occurs only after it, with the original safeguards satisfied.

Packaging limitation: batching ON + deadline correction ON + incremental
refresh produces 100,447 characters and is rejected before emission. Before
any comparisons or guard games, the supplementary guard recipe was amended
to the correction's historical batching OFF, NOISE 0 configuration in both
arms. Its reference matches the historical source exactly; the enabled guard
artifact has 99,636 characters. The main 91,202-character candidate was never
changed. This panel does **not** validate the oversized three-flag combination.
See the retained [packaging amendment](v09-ink-refresh-v1-packaging-amendment.md).

## Full-response process check and evidence

The fixed eight-game sample uses the exact main artifact, two seats on each of
903510176, 904146009, 904148181 and 904147739, opponent v09, seeds 7000/7001.
All calls use the shared frozen `ProcessCheck`, equivalent to `gradlew procCheck`,
without rebuilding. Strict first/later limits are 1,000/50 ms, grace zero,
IO diagnostics OFF. All eight complete once with **zero errors, timeouts,
overruns or missing replies**. Across 792 later responses, median is 27.3429 ms,
p95 38.3455 ms, p99 40.5695 ms and maximum **46.5054 ms**; two exceed 45 ms,
none exceed 48 or 50 ms. Percentiles use the nearest-rank convention.
First-response maximum is **681.2976 ms**. Full response includes IO and is
distinct from the native search timings above. No game was retried or replaced.

The active premeasurement freeze contains 1,503 workspace files and six runtime
files; 105 selected/prior artifact files remain unchanged. The earlier incomplete
freeze, created after the rejected combined guard export, remains a historical
preflight record and was not used for any measurement. Raw profile, decisions,
continuations, process timings, failed and successful commands, source, classes,
tests and preservation checks are retained in the evidence archive.

Independent audit v2 passes **76,130 checks**, including frozen inputs, all raw
decision/guard rows, response timestamps and source identities. The first audit
FAIL is retained: its offline `jfr print` decoder defaulted to five stack frames,
hiding policy ancestors. Decoding the same recording with `--stack-depth 128`
reproduces all 2,398 scoped samples and every leaf, inclusive and per-play count.
The original audit/decoder output, correction explanation and separate v2 audit
are archived. No policy calls, measurements or games were repeated to repair
this audit-only error.

[Evidence summary](evidence/v09-ink-refresh-v1.json),
[archive index](evidence/v09-ink-refresh-v1-artifacts.json).

Next eligible step is a separately declared strength comparison under the v4
fresh-map schedule and valid boss-cache policy. No boss requests, arena upload,
strategy promotion or push were part of this diagnostic experiment.
