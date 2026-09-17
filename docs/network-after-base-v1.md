# Network after ordinary search experiment v1

Registered before strength games on 2026-09-17. User authorized the first proposed follow-up:
complete the ordinary QuickIncome search before any single-cell network work. The previous
interleaved network experiment scored 48.3333% against v06 and 50.6667% in its separate
QuickIncome ablation. Those completed samples are historical evidence, not reusable stages.

## Hypothesis and fixed change

The earlier 133-state diagnostic lost 14.2% of ordinary completed comparisons at nominal time.
Protecting that work may allow beneficial network actions without displacing ordinary search.
This protects search coverage, not true action quality: the rollout can still prefer a bad move.

- New default-OFF USE_ROLLOUT_NETWORK_AFTER_BASE enables the existing single-cell generator
  independently. It is mutually exclusive with old USE_ROLLOUT_NETWORK and incompatible with
  other later QuickIncome/rollout/SPREAD experiments. Both modes remain OFF by default.
- Original QuickIncome route generation, ordinary action/target preparation, comparison order,
  opponent replies and turn deadline are retained. Complete all comparisons of retained
  ordinary roots before attempting network work; an incomplete ordinary reply aborts that phase
  and prevents the new phase. Route enumeration retains its original bounds/reserve.
- Save ordinary action, value and coverage. Only after ordinary completion and while time
  remains, reset the network scratch arrays, generate network roots and compare them using the
  same routine. Never restart or extend the clock. No new network enumeration/evaluation or
  target computation occurs before this boundary.
- A new action can replace the saved answer only when all opponent replies for its own
  build/sabotage choice have completed and its rollout value is strictly larger. Partial
  comparisons cannot replace the answer; ties keep the earlier ordinary action.
- The generator, active-path preselection, exact core income, positive-gain filter, singleton
  construction and ordinary route-zero continuation are unchanged. Fixed bounds: 24 exact cell
  evaluations, two extra roots, min(12 ms, original budget/3) reserved after exact evaluations.
  This is a phase cutoff on the original clock, not a fresh 12-ms time allowance. New cells may
  receive no evaluation if ordinary work leaves too little time; no threshold tuning follows.
- Same allocation-free per-node work, search budgets 486/36 ms, existing CGRunner margin.
  Ordinary snapshots use fixed arrays. Common comparison logic is extracted without changing
  the original interleaved mode's ordering; default/frozen equivalence is checked with ample time.

The main limitation is reduced opportunity coverage: a good cell on a difficult board may never
be examined. Unused paint, old continuation and opponent-model limitations remain. This is one
scheduling change, not a test of full-paint bundles, different sabotage or network replanning.

## Pre-strength verification

Run the full Java tests and cgValidate. Deterministic regression checks must cover both sides,
ordinary action/value/comparison equivalence with the flag OFF, useful late singleton output,
input isolation and legality, root order, mutually exclusive flags, reset and incomplete/expired
phase behavior. Preserve all old frozen core/policy classes and parameter snapshots.

Use all 133 existing saved public states from all 14 user-labelled v06 boss games, including
wins and losses. Validate replay/state hashes; fresh NOISE=0 baseline/candidate, alternate
measurement order, nominal 486/36 ms and ample 60000 ms. Archive full effective parameters.
Verify all prior baseline ample actions and candidate's saved ordinary ample actions, values
and comparisons; report nominal differences descriptively because clocks/JIT can differ.
Record ordinary completion, late phase starts, eligible/evaluated cells, retained roots,
complete new comparisons and selections. Never read the current or future recorded opponent
action into the policy. Sample is mechanism/cost evidence, not match strength.

If correctness/engine checks fail, diagnose before strength. If zero nominal network comparisons
complete, stop as an unexercised mechanism rather than running strength or changing thresholds.
Pre-strength implementation repairs are allowed with all outputs preserved; freeze the final
implementation and diagnostic before any strength game. No outcome-based tuning afterward.

## Fixed strength and independent ablation

Candidate A: original QuickIncome, old NETWORK=0, AFTER_BASE=1, bounds 24/2/12; every later
experiment OFF. B: frozen v06. Common opponent C: frozen v05. Ablation control: identical
QuickIncome code/configuration with AFTER_BASE=0 (both network flags OFF). Resolve and archive
full configs, SEED=1, NOISE=0.1, two workers, CG generator and nominal 486/36-ms search budgets.

Practical-300-1000-v2, draws worth half:

1. Fixed 300 games A vs B: >=60% passes direct; <=52% stops promotion evaluation.
2. Only >52% and <60%: separate fresh 1000 A vs B; require >=55% in that stage alone.
3. After direct success, A and B each play C on the SAME fresh 150 maps / 300 games each.
   Require candidate minus control >=3 percentage points.
4. Otherwise separate matched fresh 500 maps / 1000 games each; require candidate >= control.
5. After a valid completed strength verdict, fixed independent 300-game A vs QuickIncome
   ablation regardless of success/failure. It differs in AFTER_BASE only, has no pass threshold,
   never rescues failed promotion and triggers no continuation or tuning.

Pools: screen 180000..180149; direct 181000..181499; matched style 182000..182149; matched
full 183000..183499; ablation 184000..184149. Every map played on both sides. Before launch,
verify seed reservations and exclude every known historical map hash. Exclude earlier stages
from later pools; only paired common-opponent stage members intentionally share maps.

No interim-score stopping, pooling, discarded outliers, tuning/retries or extra confirmation.
Both strength stages, zero errors, engine fidelity and exact-artifact checks are mandatory.
Confidence intervals remain descriptive; no overall 95% guarantee is claimed.

## Execution and completion

tools/run_network_after_base_experiment.py --preflight freezes code/classes/resources,
infrastructure/protocol/pools, all prior evidence and every existing build/submit file.
Then --run is single-use, skips compilation, checks identities before/after every stage and
archives per-map evidence. No other CPU-heavy tasks during measurement. Failed/interrupted
runs are preserved, never automatically retried. On Windows a main-thread system/display
execution-state request is held for the run and released in finally; record both results and
timestamps without changing global power settings. Failure to acquire stops before games.

No default change, frozen-version edit, promotion or upload. If strength passes, create an
isolated staging copy of the exact source, run gradlew submit with the complete candidate spec,
then strict gradlew procCheck vs v05 on maps 903510176, 904146009, 904148181 and 904147739,
separately two games each for both sides, 1000/50-ms deadlines, zero grace, no retry-to-green.
Preserve source/config/manifest/hash and all failures. Existing submission files stay identical.
If strength fails, skip this artifact stage and make no process-readiness claim.

After all finite stages and power release, independently audit outcomes, pairing, disjointness,
configs, hashes and ablation identity BEFORE appending results to this protocol. Record results
in BOTS/DEVLOG/PLAN, retain the mode OFF if unpromoted, and commit under AGENTS #8. The original
pre-game protocol text/hash remains embedded in the launch evidence after results are appended.

## Recorded pre-strength result

261 Java tests pass, including seven new after-base tests; 264/264 arena replays reproduce
exactly. Mocked runner checks cover four finite-stage paths, repeat-launch rejection and power
request lifecycle. The first test run found one stale public diagnostic-array reset on an
expired turn; fixed before any strength game, preserved failure XML under build/analysis/
network-after-base-v1/pre-strength-failure-1.xml, then reran the full suite successfully.

All 133 states / 14 boss replays validate. All old baseline ample actions match. The new mode's
saved ordinary action, value, route, target, cells, comparison count and route-query count match
baseline in all 133 ample runs (1187 ordinary comparisons). The new phase adds 244 comparisons,
selects network in 22 states; final ample actions also match the prior interleaved network sample.

Nominal: baseline/candidate ordinary comparisons 888/887; completed ordinary search in 88 states
each. Late search starts in 88, network roots are compared in 22, selected in 12, with 69 complete
new comparisons. Candidate total comparisons 956. Of 70 known useful-cell states, late search
starts in 29; the known cell is evaluated in 22, retained/compared in 21 and selected in 10.
This is nonzero mechanism coverage, sufficient for the predeclared strength experiment.

Nominal ordinary actions differ in eight states, comparison counts in 40, and whole snapshots
in 42; candidate/base route queries 13523/13524. Timing/JIT variance remains, so the near-equal
aggregate ordinary counts are not an exact wall-clock preservation guarantee. Late work is
structurally after ordinary completion, but difficult states lose network coverage. No timing
outlier or mismatch is discarded. Full diagnostic: evidence/network-after-base-v1-diagnostic.json.

## Completed result (2026-09-17)

**Strength PASS; exact candidate process check PASS.** The selected default remains v06;
the new mode remains OFF until a separate selection/promotion decision.

- Direct screen vs v06: **187-112-1 / 300 = 62.50%**, PASS_300. The direct 1000 stage is
  correctly skipped. Map-grouped descriptive 95% interval: 51.41-73.59%.
- Matched common-v05 screen: candidate **172-128-0 / 300 = 57.3333%**, v06 **172-128-0 /
  300 = 57.3333%** on the same 150 maps. Difference zero, below the +3-point fast-pass rule.
- Separate matched common-v05 confirmation: candidate **649-351-0 / 1000 = 64.90%**,
  v06 **569-431-0 / 1000 = 56.90%** on the same NEW 500 maps. Difference **+8.00 percentage
  points**; PASS_1000 under the required nonnegative-difference rule. No pooling with the screen.
- Independent AFTER_BASE-only ablation vs original QuickIncome: **179-121-0 / 300 = 59.6667%**.
  Descriptive map-grouped 95% interval 48.58-70.76%, spanning 50%; observed support for the new
  mode, not a separate statistical guarantee. This diagnostic ends at its declared 300 games;
  its generic bench threshold label does not trigger another 1000-game stage.

Total **3200 games / 950 distinct fresh maps**, zero bot errors. Intentional matched-map reuse
occurs only between candidate/control common-opponent stage members. There is no overlap with
7824 historical maps or between independent stage pools. All full 98-field configurations
match launch; the ablation differs in exactly USE_ROLLOUT_NETWORK_AFTER_BASE.

Independent audit passed **113 checks**: integer gate calculation and pair-by-pair outcomes,
complete sides/seeds, disjointness, full configurations, all 227 main source/class/resource
identities, 14 infrastructure identities, previous evidence, and all 25 original submission
files. Both benchmark and artifact execution-state requests were successfully released.
No parameter changes, repeated stages, discarded games or intermediate-score stopping.

All in-process timing outliers remain recorded: maxima A/B were screen 48/42 ms, matched
screen candidate 37/34 and control 39/33, matched full candidate 42/34 and control 108/44,
ablation 70/38. Slow counters were zero except four for full-control v06 and one for ablation
candidate. These counters include first moves and do not substitute for strict process checks.

## Exact artifact verification

After the finite benchmark and independent audit, copied the exact frozen main sources,
resources and build inputs to build/analysis/network-after-base-v1/artifact-workspace.
gradlew submit compiled the full candidate spec; all staged main classes/resources match
the measured launch hashes. Generated source: **75,290 / 100,000 characters**, SHA-256
`9bb5d7b49dc3cb09baa2562deb64880634c43a48982a1c6a1c5c44579137230c`.

The four declared maps each ran once on BOTH sides against v05: **8/8 games, zero problem
games, timeouts or nominal deadline overruns**, zero grace, no retries. Maximum observed
later response **44.6592 ms**, first response **633.8189 ms**; no later response exceeded
the separate 45-ms headroom target. All per-turn records and exact executable files remain
in the isolated workspace; source/configuration and report hashes are durably archived.
This readiness result belongs to this exact candidate, not to the unchanged older artifacts.
It is a bounded local timing sample, not a CG-host or arena-ranking guarantee.

## Decision and evidence

The tested after-base variant passes the project's strength and exact-artifact requirements.
Preserve it as a verified default-off candidate; original QuickIncome remains the research
base and v06 remains selected. No frozen-version creation, default promotion or arena upload.

The mechanism evidence supports the intended scheduling change: ordinary ample decisions
remain identical, new actions compete only after ordinary completion, and the nominal sample
still exercises the new phase. Match results support further use of this candidate. They do
not assign all improvement to one causal factor, prove superiority on every map/opponent, or
allow subtracting the old interleaved experiment's win rate from this fresh experiment.

- [Strength stages and frozen launch](evidence/network-after-base-v1.json).
- [Independent single-flag ablation](evidence/network-after-base-v1-ablation.json).
- [133-state diagnostic](evidence/network-after-base-v1-diagnostic.json).
- [Independent final audit](evidence/network-after-base-v1-audit.json).
- [Exact artifact and process verification](evidence/network-after-base-v1-process.json).

The original pre-game protocol is preserved verbatim in launch evidence; this result section
was appended only after both audits and artifact checks. Source/class/resource/submission
identities remain unchanged. Exact audit/artifact orchestration scripts are archived byte-for-
byte under tools/diagnostics/network-after-base-v1/; they refuse to overwrite completed runs.
