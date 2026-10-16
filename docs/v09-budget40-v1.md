# v09 budget40 v1: prospective timing and strength experiment

Declared2026-10-16 before measurements, on the user's explicit request after
budget38 stopped at49.6667%/300. This is a new experiment at40ms, not a retry
or extension of the38ms sample. No old results are pooled into the new gate.

## Comparison

- Candidate: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40`.
- Control: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=36`.

Same native rollout and full parameters except the later-turn budget. Batching
is ON in both experimental arms, missed-deadline correction OFF; selected v09
and default36/batching OFF remain unchanged. Keep allocation/strategy/guards,
first-turn1000ms, MARGIN_MS14 and warmup500ms. Candidate's effective later
reserve becomes10ms. Parsing/preparation still consume its40ms allowance.
The first nominal in-process search stays486ms for both arms.

Extend the existing opt-in parameter to40, preserving36/38 support and default,
frozen-v09, batched36 and budget38 generated source identities. Unsupported
non-native/legacy combinations still fail before play. Native strategy and
core sources do not change. Only current-native budget wiring/validation and
prospective test/gate tooling may change. No uploads, live games or promotion.

## Preflight and exact artifacts

Use pinned Java17/runtime. Relevant parameter, preparation charging, first/later,
seat-swap, external50ms, source-identity/size and Python gate tests must pass.
Run cgValidate; preserve prior full-suite limitations rather than silently
claiming a clean full suite. Known prior unrelated failures are documented in
the38ms report; do not modify their old pins or unrelated untracked work.

Export36 and40 with gradlew submit to separate experiment directories, each
within100000 characters. Control source must equal
`2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a`.
Freeze final source/classes/configuration, runtime, protocol, inputs and map
hashes after preflight. Preserve previous artifacts/archives and selected
submission. Pre-measurement repairs are recorded; measurement retries/tuning
are not allowed. First complete the process stage; strength is conditional.

## Fixed process stage

Exactly16 games: each of the following eight maps once in each seat, in order:
903510176,904146009,904148181,904147739,903184594,903182662,903507919,903584040.
This is the same predefined panel as38ms, not chosen from40ms outcomes.
Each two-game call uses seeds7000/7001 and the fixed36ms control opponent.

Run eight sequential gradlew procCheck calls against the exact40 artifact,
games2, grace0, processDiagnostics=false. External limits1000ms first/50ms
later stay strict. No profiling or concurrent agent-launched heavy work.
Complete all16 once even after a reported failure; retain all raw responses,
missing replies, errors and outliers. Incomplete infrastructure evidence is
INCOMPLETE, never PASS. Do not replace maps or relax the deadline.

PASS requires complete16-game evidence with zero bot errors, timeouts and
nominal overruns. Summarize reader-stamped full response times, first/later
separately: median, p95/p99/p99.9, max, counts>45/>48/>50 and missing replies.
WARN or consumer wake-up time cannot replace response time. Finite/complete
raw timing coverage and exact artifact identities are checked independently.
This is local observed readiness, not a universal host timing guarantee.

**Any failed or incomplete process stage skips the entire strength stage.**

## Conditional strength stage

Only after process PASS, compare40 against36 with exactly two workers and
fresh bots/maps, paired seats. Budgets follow bot identity when seats swap;
report firstA/B486ms and laterA40/B36ms explicitly.

- Block1:300 games/150 maps, seeds261316000..261316149.
- At pooled score>=50%, block2 adds200 games/100 maps,
  seeds261416000..261416099.
- At pooled score>=50% at500, block3 adds500 games/250 maps,
  seeds261516000..261516249.

Practical-v4: stop strictly below50% after300 or500; no early pass. Survivors
finish1000, with>=55% required for the local numerical pass. Wins1/draws0.5/
losses0; confidence intervals are descriptive. No extensions, tuning, retries,
outcome-dependent stopping within blocks or map substitution.

Verify all500 prospective shared-core hashes before play: mutually distinct,
disjoint from all prior known maps and all500 reserved38ms maps (including its
350 unplayed maps). Check registered seed intervals too. Expected exclusion
union24124; investigate any additional sources before measurements, never
silently reroll collisions. Keep actual fresh-map counts and source pins.

The in-process strength result excludes protocol IO/warmup and cannot replace
the exact-artifact process gate. This repeated budget investigation is a local
selection exercise; a pass is not independent arena confirmation, proof of an
optimal budget, selected-v09 promotion or authorization for a boss batch.

## Completion

Record all completed stages and any skipped stage with its reason. Retain
exact artifacts/raw reports/input hashes and independent audits. Update
BOTS/PLAN/DEVLOG, then commit locally, including a rejected result. Keep36ms,
selected v09 and earlier verdicts until a separate explicit promotion decision.

## Preflight result

All 45 focused Java tests and 46 Python gate/driver tests passed; cgValidate
reproduced 292/292 archived games exactly. Tests pin the previous default,
frozen-v09, batched36 and budget38 exports. The full suite was not rerun;
the 25 unrelated failures retained in the budget38 report are not represented
as fixed. No core, frozen bot or unrelated untracked source was changed.

Exact40 export: 90,641/100,000 characters, source SHA-256
`9510ff174e76297736144193a4c519f3728eac067d09614bb65e2f0ecd1b88d9`,
build ID `eb97929888b7a3e1be1876db4f5cd5044f27f65f9a0aea7019e7610a513d5a59`.
The control export remains byte-identical to the previous batched36 artifact.
Before measurement, 2,981 inputs and eight runtime files were frozen. All 500
prospective maps are unique and disjoint from 24,124 prior identities, including
all 500 reserved budget38 maps; seed ranges avoid all 128 prior registrations.

## Strict process result: PASS

The fixed 16 games completed once, with all 16 first replies and 1,479 later
replies observed. Zero errors, timeouts, nominal overruns or missing replies.
Reader-stamped full response times include input delivery, preparation, search
and output; they are distinct from WARN and consumer wake-up times.

- Later replies: median 33.4462 ms, p95 41.9180 ms, p99 43.8367 ms,
  p99.9 47.4910 ms, maximum 47.8363 ms.
- Nine later replies exceeded 45 ms; none exceeded 48 ms or 50 ms.
- First replies: median 584.3973 ms, maximum 732.0729 ms, below 1,000 ms.

Percentiles use nearest rank except the ordinary median. This passes the fixed
local readiness panel; it does not guarantee server timing or justify spending
the observed 2.1637 ms remaining margin. Only the tested 40 ms value proceeds
to the conditional strength comparison.

## Strength result: NOT_PASSED

Completed all three fixed blocks with two workers, fresh paired maps and zero
bot errors. Actual first budgets were 486/486 ms, later budgets 40/36 ms;
the budget followed bot identity through both seats. Arms differed only in
TURN_BUDGET_MS. No block was repeated, extended or tuned.

- Block 1: 151 wins, 141 losses, 8 draws; 155/300 points = 51.6667%.
- Block 2: 104 wins, 90 losses, 6 draws; 107/200 points = 53.5%.
  Pooled 262/500 = 52.4%, so the final block was required.
- Block 3: 233 wins, 237 losses, 30 draws; 248/500 points = 49.6%.
- Final: **488 wins, 468 losses, 44 draws; 510/1,000 points = 51.0%**.
  The declared 55% requirement was not met: **NOT_PASSED**.

All 500 played maps match the prospectively pinned hashes and seeds. Recorded
block durations were 395.646, 251.725 and 665.914 seconds. The previous 38 ms
results were not pooled into this new experiment.

Native-call timing diagnostics across the strength run: candidate maximum
59 ms with one call above 50 ms; control maximum 42 ms with none above 50 ms.
The candidate outlier occurred in block 3, pair index 1, seed 261516001,
candidate as P0. These counters include first turns,
use floored milliseconds and retain no turn index; they are not reader-stamped
full responses or an external-process timeout test. The outlier is retained
without a retry or an unsupported causal explanation. Passing the 16-game
process panel is not a claim that 40 ms always stays within 50 ms.

## Decision

**Keep 36 ms.** The 40 ms candidate passed the predefined exact-artifact timing
sample, but its full 1,000-game score did not establish the required useful
strength gain. The observed 51% does not prove that 40 ms is intrinsically
worse or that 36 ms is an optimal budget.

Default batching stays OFF, selected v09 and all historical verdicts remain
unchanged. Keep the opt-in 40 ms parameter and fixed experiment driver for
reproduction. No live boss games, upload, promotion or push. The selected
submission remains unchanged; results and exact artifacts are retained.

Independent audits passed: 125 raw-process checks and 50 final evidence checks.
All 2,981 frozen files, eight runtime files and 28 selected-submission files
remain unchanged. All 500 paired results, exclusions and stage decisions were
recomputed from retained raw evidence. Audit PASS confirms the evidence;
the candidate's strength verdict remains NOT_PASSED.

[Summary evidence](evidence/v09-budget40-v1.json),
[artifact index](evidence/v09-budget40-v1-artifacts.json),
[retained archive](evidence/v09-budget40-v1-artifacts.zip).
