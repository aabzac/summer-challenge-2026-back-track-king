# Distributed construction experiment v1

Registered before strength games, 2026-09-17. User hypothesis: develop several useful routes
in different places so one sabotage campaign cannot suppress all construction. Base is the
resumed QuickIncome working implementation; selected control remains frozen v06.

## Fixed implementation

`USE_ROLLOUT_SPREAD=1`, default OFF, adds at most `ROLLOUT_SPREAD_ROOTS=2` construction roots.
Use existing retained routes only. Find the first ranked pair connecting different unordered
town pairs with no common disruptible region along either entire route. Shared town regions
are allowed because core forbids their sabotage. Test two real-paint allocations, 2+1 then 1+2.
Only keep actions that place on both routes and differ from already retained first actions.
No new route queries or increased portfolio width. This intentionally tests the bounded
two-project mechanism, not arbitrary territory occupation or every possible project pair.

The first ordinary construction root keeps first search priority, then the added roots,
then the remaining ordinary roots including construction WAIT. Each compares its normal
sabotage target and no sabotage against the existing two replies. Complete reply coverage
is required. At most nine construction roots, 18 comparisons and 288 simulated turns.

The split persists inside each eight-turn forecast. A route costing at most three remaining
paint is finished immediately; an inked or completed project yields to the other. If terrain
prevents a split, use ordinary affordable construction. Spend leftovers on valid retained
projects. No change to enemy policy, sabotage valuation, core, frozen bots or selected Player.
Additional search may reduce coverage under the real 36-ms search budget.

Base recipe: `current?USE_ROLLOUT_QUICK_ROUTE=1,ROLLOUT_QUICK_MAX_PAINT=6,USE_ROLLOUT_QUICK_EARLY=0,USE_ROLLOUT_QUICK_VALUE=0,USE_ROLLOUT_QUICK_SAFE=0,USE_ROLLOUT_DIVERSE_REPLIES=0,USE_ROLLOUT_REPLAN=0,USE_ROLLOUT_SECOND_TARGET=0,USE_ROLLOUT_SPREAD=0`.
Candidate differs only in `USE_ROLLOUT_SPREAD=1`; all other resolved values frozen at launch.

## Prospective evaluation

- Meaningful regression tests: terrain packing, region independence, finishing and abandonment,
  actual multi-turn split and delayed income, input isolation, legality, work bounds and default
  v06 equivalence. Core unchanged; run cgValidate before trusting the games.
- Mechanism study uses the existing fixed 133 pre-turn positions from all 14 v06 boss games,
  both outcomes, baseline QuickIncome vs candidate, NOISE=0, nominal 486/36 ms and ample time.
  It records mixed roots available/evaluated/selected and continued split turns. This is
  descriptive diagnostic evidence, not live boss performance or a parameter-tuning sample.
- Strength follows practical-300-1000-v2: 300 against frozen v06, >=60% skips direct 1000,
  <=52% stops; strictly between requires a separate 1000 at >=55%. After direct success,
  candidate AND v06 vs frozen v05 on matched 300 each, +3 percentage points required;
  otherwise matched fresh 1000 each, candidate >= control. No pooling, tuning or retries.
- Regardless of the strength verdict, one additional fixed 300-game ablation vs the SAME
  QuickIncome implementation with SPREAD=0, on separate maps. Diagnostic only: it measures
  this change, does not replace the v06 gate, and never triggers further games or tuning.
- Authentic generator, SEED=1, two workers, nominal CG timing 486/36 ms, sides swapped on each
  map. Exclude all known prior benchmark map hashes and earlier stages. Preserve all games,
  timing outliers and errors; freeze source/class/parameter identities across stages.

Reserved map seeds: screen 160000..160149; direct 161000..161499; matched style
162000..162149; matched full 163000..163499; diagnostic ablation 164000..164149.
Runner refuses existing output. Pools and this protocol are hashed before strength play.

If the strength gate passes, also build the exact candidate in isolation and run strict
process checks on 903510176, 904146009, 904148181 and 904147739, two games per individual map
to cover both sides, zero grace, no retry. Keep both existing Player files unchanged. No
automatic promotion, arena upload or push. Record and commit the completed experiment.

## Pre-strength checks

241 Java tests pass, including five new split-construction regression tests and unchanged
sampled default/frozen/main decisions with ample time. Seven practical-gate tests pass (their
first sandbox invocation could not access temporary files; rerun with normal access passed).
Core unchanged and all 264/264 downloaded arena replays reproduced exactly.

On the fixed 133-state boss sample, ample-time split actions are available and compared in
46 states, selected in four. 158 extra complete comparisons, 585 continued split forecast
turns; exactly four changed actions, 13753 route queries for both policies. Total complete
comparisons baseline/candidate: 1187/1345. Under nominal budgets, available in 46 states,
compared in 36, selected in seven; 104 split comparisons and 396 continued split turns.
Actions differ in 21 states; complete comparisons 616/678 and route queries 12693/12344.
Timed proposal differences contribute to action differences. Fresh-instance nominal maxima
baseline/candidate 66.95/37.89 ms do not constitute process-readiness evidence.
Full diagnostic: `docs/evidence/spread-construction-v1-diagnostic.json`.

## Completed strength result: NOT PASSED

- Screen, 300 games / 150 fresh maps: **160 wins, 138 losses, two draws = 53.6667%**.
  Above the strict 52% continuation floor, so the unchanged candidate received fresh 1000.
  Mean score difference +100.2; search maxima candidate/v06 98/53 ms, slow moves 3/2;
  elapsed 501.6 seconds. No bot errors.
- Independent direct stage, 1000 games / 500 other maps: **502 wins, 490 losses, eight draws
  = 50.60%**, below the required 55%. Mean score difference -64.09; search maxima 99/51 ms,
  slow moves 5/2; elapsed 1620.7 seconds. No bot errors. Common-v05 stages skipped on direct
  failure, as prescribed. No pooling, rerun, retuning or outcome-based early stopping.
- Fixed diagnostic ablation, 300 games / 150 additional maps against the unchanged working
  QuickIncome base: **150 wins, 141 losses, nine draws = 51.50%**. Full parameters differ
  only in USE_ROLLOUT_SPREAD. Mean score difference +12.6; search maxima 141/63 ms, slow
  moves 2/1; elapsed 499.6 seconds. No bot errors. This is a small observed edge, not established
  superiority and not an alternative promotion gate. Its generic report-size threshold label
  is ignored; no continuation was prescribed for this diagnostic.

Total: **1600 games on 800 distinct fresh maps, zero bot errors**. All outcomes and timing
outliers retained. Strategy/source/class hashes stayed unchanged throughout the experiment.
All 25 pre-existing submission files, including Player.java and PlayerQuickIncome.java,
have identical hashes. Existing selected/default strategy stays v06; development base stays
QuickIncome with SPREAD disabled. The experimental source is retained behind its default-OFF flag.

No new candidate submission or process check was prescribed after strength failure. Search
outliers above nominal budgets remain in the reports; this experiment makes no process-timing
or arena-readiness claim. No upload, push or promotion.

## Interpretation and limits

The implementation really compares and sometimes chooses construction in different regions,
but the fixed strength test did not establish a qualifying improvement. The result applies to
this bounded variant: two projects from the existing six-route portfolio, strict absence of a
shared disruptible region, an eight-turn forecast and immediate completion when affordable.
It does not test arbitrary map-wide scattering, additional route generation or a longer forecast.
Additional comparisons and changed search order can also affect timed decisions. The diagnostic
four-of-133 selection frequency is from saved positions with ample time, not live match telemetry.
The original QuickIncome historical 53% on different maps is not a matched causal comparison;
the new 51.5% head-to-head is the designated ablation for the added mechanism.

Durable records: [strength](evidence/spread-construction-v1.json),
[ablation](evidence/spread-construction-v1-ablation.json),
[mechanism](evidence/spread-construction-v1-diagnostic.json),
[final audit](evidence/spread-construction-v1-audit.json).
