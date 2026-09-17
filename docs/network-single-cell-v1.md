# Network single-cell experiment v1

Registered before strength games on 2026-09-17. User authorized the proposed experiment
after the [network-control analysis](network-control-strategy.md). Research base is original
QuickIncome; selected control and submission remain v06. This is one action-generator change,
not an independent long-term network planner or automatic promotion.

## Frozen implementation

- Default-OFF `USE_ROLLOUT_NETWORK`; all later QuickIncome/rollout/SPREAD flags stay OFF.
- Core `NetworkCells.collectSingleCellConnections` enumerates legal single-cell builds with
  at least two adjacent passable connectors, including cells within the same component.
  This necessary geometric filter includes equal-length path switches. It does not promise
  positive income. New helpers live in a separate core class; existing Pathing methods and
  bytecode identity remain intact for archived reference opponents.
- Prioritize candidates by the summed active directed-path usage of their adjacent connectors.
  This is a cheap strategic ranking, not a new scoring rule. Ties prefer less paint then cell
  index. Exactly evaluate at most `ROLLOUT_NETWORK_MAX_CELLS=24` cells using scratch SearchState
  and canonical whole-network income. Keep only positive own-minus-enemy income gains.
- Keep at most `ROLLOUT_NETWORK_ROOTS=2` best distinct singleton first actions, sorted by exact
  gain, then paint and cell index. Reject duplicates of existing singleton roots. A new root
  spends no remaining paint on unrelated placements; the evaluated action is exactly emitted.
- Existing route generation and ordinary root target selection happen first. Network exact
  evaluations reserve `min(ROLLOUT_NETWORK_RESERVE_MS=12, budgetMs/3)` for subsequent work.
  This is 12 ms at nominal 36 ms. Target completion uses the remaining full turn deadline.
  Bounds/checks are cooperative, not a hard timing guarantee.
- Search order: ordinary first root, network roots, remaining ordinary roots including build
  WAIT. Each new root compares the usual own sabotage and no sabotage against the unchanged
  opponent scenarios. Only complete reply coverage may replace the current answer.
- On later simulated turns, own construction resumes ordinary retained route zero, exactly
  like the existing build-WAIT root. No new cell generator is run inside rollouts and the
  opponent model is unchanged. The next real observation replans normally.
- Maximum default work: 24 extra exact cell evaluations, two target choices, four complete
  comparisons / 64 extra resolved turns; at most 288 simulated turns total. New arrays are
  allocated at init only when enabled; no per-node allocations. Ordinary fallback retained.

Known limitations: capped preselection can miss a useful cell; current income can undervalue
investment or overvalue fragile rails; withholding leftover paint can cost development; future
rollout uses the old fixed portfolio. Added cost can reduce ordinary search coverage. This
experiment does not test multi-cell bundles, different sabotage or a network-aware continuation.

## Pre-strength verification

Tests cover mixed-network income capture on both sides, harmful own-rail shortening, actual
terrain paint, no leftover placement, legality/input isolation, cap/deduplication, ordinary
continuation, simultaneous neutral rails and ink, search ordering and expired-state reset.
Core filter tests cover geometry/boundaries and all 625 small board configurations for both
players. Existing tests cover default/frozen v06 equivalence. Run the full suite and cgValidate.

`networkCellStudy` uses ALL the same 133 known public states from all 14 user-labelled v06
boss games, wins and losses. Revalidate every replay and state hash; fresh NOISE=0 baseline
and candidate, alternating order, nominal 486/36 ms and ample 60000 ms. Compare archived
useful-cell availability/evaluation/root retention/full comparisons/selection, ordinary path
queries and completed comparisons. Check old baseline full effective parameter values and
ample actions. New fields are fixed as above and differ only in NETWORK between policies.
This is mechanism/cost evidence, not live strength or original arena timing/RNG reconstruction.

No outcome-based tuning or strength retry. Implementation bugs found before strength may be
fixed and verification repeated with preserved diagnostic outputs; freeze final code/configuration
before any strength games. If no new roots can be completed in the nominal sample, or legality,
input isolation or engine fidelity fail, do not start strength until diagnosed. Clock outliers
are preserved and do not become a process-readiness claim.

## Fixed strength and ablation protocol

Candidate A: original QuickIncome with NETWORK=1 and numeric bounds 24/2/12. All other new
experiment flags OFF. B: frozen v06. C: frozen v05. Resolve and archive every full configuration.
SEED=1, default NOISE=0.1; authentic CG generator, nominal 486/36-ms timing, two worker threads.
Every map played with both sides. Record source/class/resource identities including all core.

Practical-300-1000-v2, draws worth half:

1. A vs B, fixed 300 games: score >=60% passes direct stage; <=52% stops promotion evaluation.
2. Only 52% < score <60%: fresh fixed 1000 games, require >=55% in those alone; otherwise stop.
3. After direct success, A vs C AND B vs C, fixed 300 each on the SAME new 150 maps.
   Candidate advantage >=3 percentage points passes this stage.
4. Otherwise separate fixed 1000 each on the SAME new 500 maps; candidate >= control passes,
   including ties. Otherwise stop. No pooling, repeated stages, retuning or early score stopping.
5. Regardless of completed strength verdict, one independent fixed 300-game diagnostic ablation:
   A versus the identical QuickIncome code/configuration with ONLY NETWORK=0. It has no pass
   threshold, never rescues failed promotion and never triggers more matches or tuning.

Reserved fresh seeds: screen 170000..170149; direct 171000..171499; matched style
172000..172149; matched full 173000..173499; ablation 174000..174149. The runner checks prior
recorded/reserved seed ranges and excludes all known historical map hashes before each stage.
Matched candidate/control stage members intentionally share their declared new maps.

`tools/run_network_experiment.py --preflight` freezes completed build, full configurations,
protocol/tools/pools, prior evidence and all existing submission files. `--run` uses those
classes without compilation, enforces the conditional finite stages and saves durable per-map
evidence. It refuses changed inputs, existing outputs or a second launch after interruption.
No other CPU-heavy work during measurement. All errors/outliers retained.

During --run a task-scoped Windows system AND display sleep request is held and released in
finally on the same Python thread; record request/release timestamps and return codes. This
addresses previously observed Modern Standby interruptions without changing global power
settings. Failure to acquire the request stops before games. It is not a timing guarantee.

Both strength stages, zero errors, engine fidelity and exact-artifact checks are mandatory.
Confidence intervals remain diagnostics; this policy has no overall 95% guarantee.

## Artifact and completion rules

Preserve every pre-existing `build/submit` file. No default change, frozen-version edit, upload
or automatic promotion. If strength passes, use an isolated exact-source staging directory
with `gradlew submit` for the full candidate spec, then strict `gradlew procCheck` against v05:
maps 903510176, 904146009, 904148181 and 904147739, separately two games per map to guarantee
both sides, zero grace and no retry-to-green. Preserve the exact source/config/manifest/hash
and all per-turn failures. At strength failure skip this readiness stage and make no claim.

After the finite protocol, audit every decision, pairing, disjointness and single-flag ablation;
record results and limitations in BOTS/DEVLOG/PLAN and this document, then commit the completed
experiment under AGENTS #8. A retained default-off candidate is a valid negative outcome.

## Recorded pre-strength result

254 Java tests pass (seven new core-filter tests and six network-policy tests); 264/264 arena
replays reproduce exactly. Frozen-parameter tests explicitly include the new inactive fields;
immutable snapshots and all original core classes are unchanged. Seven practical-gate tests
and mocked orchestration branches have also passed.

All 133 fixed positions / 14 boss replays validate, with unchanged input hashes and exact
reproduction of all prior baseline ample-time actions. Ample time: network roots retained
and fully compared in 70 states, selected in 22; 244 extra complete comparisons and unchanged
13753 route queries. Nominal budgets: roots in 70 states, compared in 68, selected in 26;
34 changed full actions, baseline/candidate complete comparisons 937/1015 (211 network).
Nominal route queries 13577/13623 reflect clock effects, not added path queries.

The previously identified best useful singleton is eligible and exactly evaluated in all
70 applicable states in both timing modes; retained in 66, compared in 64 nominal / 66 ample.
It is selected in 23 nominal / 19 ample. Opportunity evidence was not used as a policy input.
Coverage permits the declared strength experiment; it is not evidence of match superiority.
Full report: [network-single-cell-v1-diagnostic.json](evidence/network-single-cell-v1-diagnostic.json).

The first configuration-only preflight hit sandbox denial of the external Gradle cache lock
before BotFactory configuration resolution. No launch manifest or started marker and zero
games existed. Its inputs/log were preserved under
`build/analysis/network-single-cell-v1/preflight-cache-denied`; complete preflight is repeated
with normal cache access before any measurement. This is not a strength retry.

## Completed result (2026-09-17)

**NOT PASSED; retain NETWORK OFF.** Fixed screen against v06: **145-155-0 / 300 = 48.3333%**,
below the strict >52% continuation floor. Direct 1000 and common-opponent stages were skipped.
The independently predeclared ablation against identical QuickIncome with NETWORK OFF is
**152-148-0 / 300 = 50.6667%**. This small observed edge does not establish improvement and
cannot rescue the failed strength gate. The two series use different fresh map pools; their
percentages must not be subtracted or pooled. Map-grouped 95% descriptive intervals are
37.24-59.42% and 39.58-61.76%, respectively.

All **600 games / 300 fresh paired maps** completed with zero bot errors. Seed intervals and
both sides are complete; neither pool overlaps the other or the 7524 historical map hashes.
Full configurations match preflight; the ablation differs in exactly USE_ROLLOUT_NETWORK.
All 227 main source/class/resource identities, 14 infrastructure identities, 165 prior
evidence/pool identities and all 25 existing submission files passed the final audit.
Windows execution-state request and release succeeded; no global power settings changed.
No stage was retried, tuned or stopped based on an interim score.

Search outliers remain in the reports: screen A/B maximum 89/58 ms and slow counters 3/1;
ablation 111/90 ms and counters 3/2. These counters use >50 ms even on the first move and
are not strict submission-process checks. In-process zero errors is not arena readiness.
As declared, a strength failure skips new submit/procCheck; the existing timing limitations
of selected v06 and the separately exported QuickIncome artifact remain unresolved.

The fixed 133-state diagnostic identifies a concrete cost: nominal ordinary comparisons
fall from 937 to **804** (1015 total minus 211 network), a **14.2% reduction**. Ordinary
coverage falls in 45 states; eight of the 34 changed full actions do not select a network
root. With ample time, ordinary comparisons remain exactly 1187 and all 22 changed actions
select network. Four additional nominal network choices instead select ordinary building
with ample time. This demonstrates clock-sensitive search coverage, not the cause of losses.

Single-cell actions can also leave usable paint unspent, and their simulated continuation
still uses the old route-zero portfolio. These are separate hypotheses about opportunity
cost and future value. The known best useful cell was evaluated in all 70 applicable states,
so this diagnostic does not support blaming the top-24 filter for missing those cells.
The result concerns this bounded first-action generator, not every network-control strategy.
No additional CG data was needed to complete this experiment.

Decision: preserve tested code behind the default-off flag, retain original QuickIncome as
the development base and v06 as selected. No promotion, export, upload or further tuning.
If another experiment is requested, first isolate the cost of generating/comparing these
actions from their construction value; do not combine that with a new opponent model.

The frozen pre-game protocol text and hash are embedded in the launch evidence. This results
section was appended only AFTER the final identity audit; executable sources/classes were
not changed after the measured run. Durable evidence:

- [Fixed strength result and launch manifest](evidence/network-single-cell-v1.json).
- [Independent single-flag ablation](evidence/network-single-cell-v1-ablation.json).
- [133-state mechanism and timing diagnostic](evidence/network-single-cell-v1-diagnostic.json).
- [Final independent identity/pairing audit and power record](evidence/network-single-cell-v1-audit.json).
