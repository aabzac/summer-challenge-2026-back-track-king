# v09 deferred sabotage correction: local strength v1

Declared 2026-10-11 before strength games, baseline `d3f74ca`.
The user requested moving from position/runtime diagnostics to a benchmark
to test whether the existing improvement helps in complete games.

## Fixed candidate and question

Candidate: `current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true`.
Control: selected frozen `v09`, with its saved full defaults including NOISE=0.1.
Test the exact candidate already used in the position and runtime studies:
targeted overdue sabotage correction plus the implemented zero-build forecast
reuse. Do not implement destination-batched refresh or modify strategy now.

The question is whether this complete candidate beats selected v09 under the
local nominal time budgets. This is not a causal ablation of the deadline flag:
the candidate also retains its tested NOISE=0 setting, whereas selected v09 has
NOISE=0.1. No parameter is tuned after observations. An eventual positive result
would apply to the full candidate recipe, not the flag alone.

The exact enabled export is 98,572 characters, SHA256
`3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c`.
Its previous ample equivalence and required native r33 attack at turn40 are
retained evidence. Its strict process result is **FAILED** (56.0151ms), with two
additional nominal spikes99.5029/64.5907ms and unresolved exact causes.
This local strength benchmark does not replace, rerun or overturn those failures.
Even a local pass cannot establish readiness, authorize boss games or promotion.

## Registered local sequence

Use unchanged practical-v4 local rules and `tools/run_practical_v4.py`:

- Block1: 300 games /150 paired maps, seeds241011000..241011149.
- If pooled score is at least50%, block2 adds200 /100 maps at241111000..241111099.
- If pooled score is still at least50%, block3 adds500 /250 maps at241211000..241211249.
- Stop strictly below50% at cumulative300 or500. Survivors complete1000 and
  pass the local strength threshold only at >=55%; there is no early pass.

Two workers, nominal CG486/36ms search allowances, fresh bot instances and both
seats on every map; draws count as half. Register all three pools before play.
Exclude previous benchmark maps and saved CG/development maps using the same
Protocol.initBlock hash convention. Confirm disjoint map identities between
blocks. Do not replace seeds, retry timed blocks, add opponents, tune parameters
or extend a failed sample. Stop on engine or bot errors.

The in-process bench has no hard50ms protocol timeout and no per-game disposable
500ms submission warmup. Retain its slow-call metrics separately; they cannot
certify the exact exported process's deadline or arena-host performance.

## Preflight, provenance and completion

No production code changes. Revalidate the engine and freshly export the exact
candidate into this experiment's separate artifact directory; require equality
to the old source hash. Preserve the selected submission. Reuse completed
correctness evidence because source and policy have not changed; do not run a
new process check on unchanged code merely to seek a passing result.

Before the first game, freeze full effective parameters, source and executable
identity, prospective protocol, pools, exclusions, Java/runtime and the previous
semantic/process evidence. Verify the freeze before/after each complete block
and reconcile actual report parameters, maps, seats, game counts and errors.
No CPU-heavy jobs run alongside timed strength work beyond its two workers.

Retain every result, including a futility stop or execution failure. Record the
local verdict separately from **PROCESS_FAILED / NO_PROMOTION**. This request
authorizes local strength testing only: no boss batch, upload, push, promotion
or automatic optimization after results. Update PLAN/BOTS/DEVLOG, archive the
evidence and commit the completed experiment locally, including rejection.

## Completed result — 2026-10-11

**STOP_FUTILITY_300 / LOCAL_NOT_PASSED_AND_PROCESS_FAILED.** Candidate:
134 wins,142 losses,24 draws, or146/300 points (**48.6667%**). The fixed first
block is complete:150 distinct fresh maps, each played in both seats, seeds
241011000..241011149. There are no bot errors or missing games. It took464.761s
with two workers and the declared486/36ms search budgets.

The pooled score is strictly below50% at the first registered look. Blocks2/3
were registered before play and were not run. There were no timed retries,
parameter edits, extra arms or extensions. This result does not establish a
strength improvement for the complete candidate. It also does not establish
that the targeted deadline correction itself is harmful: NOISE differs between
the two recipes, and the benchmark is a whole-candidate comparison. The broad
map-grouped diagnostic interval is37.58%..59.76%, not the stopping rule or proof
of inferiority.

The candidate won74 of its150 P0 games and60 of its150 P1 games (wins only;
draws remain in the overall score). Average game length92.08 turns and average
candidate score difference-3.50. Its accepted disruption rate is71.10% of turns,
versus70.04% for the control; these aggregate counts do not identify the effect
of the targeted correction. No per-turn intervention logger was added to timed
play, and no mechanism frequency or causal attribution is inferred.

The in-process maximum calls were66ms candidate and50ms control; the runner
recorded2/0 slow calls. These are native search observations, not external
protocol timeout results. The previously measured strict56.0151ms process
failure remains unchanged. No new process trial or boss stage was run.

## Verification and decision

- Engine292/292 exact. Fresh candidate source98,572/100,000 characters and all
  generated class bytes match the previous enabled artifact. Full artifact
  parameters match the benchmark candidate, including NOISE=0.
- All150 maps are disjoint from22,894 prior benchmark/saved-CG/development map
  identities. Seed order, two seats per map, full parameters, pool hashes and
  exclusion identity are checked against the prospective declaration.
- All2,589 frozen source/class/input files and seven runtime files remain exact
  before and after the completed block. The experiment uses the unchanged v4
  runner and decision checker; completed reports and decisions are write-once.
  The raw runner report still exposes legacy v3/STOP_SCREEN_300 labels; this
  experiment's controlling result is the separate v4 STOP_FUTILITY_300 decision.
- Preserve selected v09 and leave the correction OFF. Local evaluation ends
  at the futility stop. The previously selected path-batching idea remains
  unimplemented and is not automatically pursued after this strength result.

[Result](evidence/v09-deferred-strength-v1.json),
[archive index](evidence/v09-deferred-strength-v1-artifacts.json),
[retained evidence](evidence/v09-deferred-strength-v1-artifacts.zip).
