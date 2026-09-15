# Bot versions

Gate: A vs previous >= 55% on >= 200 games (two sides on each map), map-grouped 95% CI excluding
50%, no bot errors. Process timing and an older opponent remain separate promotion checks.
`current` = **v05**, `btk.bot.current.EvalBot`; frozen copies in `btk.bot.vNN`.

Readable labels and six runnable retained experiments are listed in [bot_catalog.md](bot_catalog.md)
and `gradlew botCatalog`. Version ids remain stable; `EvalBotVNN` class names remain frozen.
Named experiments are local, not promoted, and verify a pinned engine/configuration snapshot.
Their historical screen results are not new evidence for the shared catalog snapshot.

Latest user-reported arena snapshot (2026-09-15): **Gold opened; still Silver, 235/622**.
User confirms the arena still runs the previous build, without the new isolated warmup;
the exact deployed source hash and placement status remain unverified. League changes
alter the ranking population, so this is not directly comparable to the old rank snapshots.
The warmup fix has process-timing evidence, not a new strength-benchmark result: the ordinary
in-process benchmark does not invoke MainBot's submission warmup.

Fresh diagnostic: arena entry **6739024**, 50 newly downloaded games, **25 wins / 25 losses**,
zero own failures, one win by opponent timeout; **264/264** corpus replays exact.
This is a separate entry/sample from 6736850, not a controlled strength comparison.
See `arena-analysis-2026-09-15.md` for scope, loss evidence and the next hypothesis.

Sabotage follow-up (`cgDisrupt`): known-state full scans reproduce Meruem's recorded target
preferences; local timed scans finish at all four selected positions. OldJohn's missed sabotage
is not reproduced, leaving its original cause open. Diagnostics only: v05 and Player.java unchanged,
no new strength bench or promotion. See `sabotage-valuation.md`.

Delayed-sabotage experiment (2026-09-15): `DISRUPT_DELAY_FACTOR=0.65` scores **49.3%** vs v05,
146-150-4 over 300 games / 150 new paired maps, map-grouped CI **38.2-60.4%**. Gate not passed.
On 200 matched games each vs aggressive v02, candidate **73.3%** (146-53-1), v05 **76.5%**
(153-47-0); no common-opponent support for promotion. Keep **factor 1.0**, no v06.
116 tests / 264 exact replays; candidate and restored frozen-v05 process samples each 0/4 failures.
Full protocol, source/artifact hashes and reports are in DEVLOG and `sabotage-valuation.md`.

## Local reference panel (not submission versions)

`pressure-v1` is a separate sabotage-first, sticky-target, short-horizon policy. Its fixed consumed
parameters live in `btk/bot/versions/pressure-v1.properties`; the policy stays outside the merged
submission. Do not retune this reference id after recording its baseline. Use `benchSuite` to
compare each candidate against the matched v05 / pressure-v1 / v03 / aggressive-v02 panel.

On development-v1 (150 maps, seeds 20000..20149), frozen v05 vs pressure-v1 is **207-93 (69.0%)**,
CI **57.9-80.1%**. Pressure uses accepted sabotage on **99.9%** of turns but builds on only
**56.7%**; games average **61.3 turns**, **267/300** close early. This supplies different tactical
pressure, not evidence that it reproduces the whole arena meta or should replace v05.

Height breakdown illustrates why match-ups must stay separate: v05 wins **24/44** height-20
games vs pressure-v1 but **36/44** vs v03 on the same maps. These small slices have wide intervals
and are exploratory. Whole-run v05 vs v03 is **209-91 (69.7%)**, CI **58.6-80.8%**.
New schema-3 reports and reserved confirmation pool are described in `benchmarking.md`.
No current-policy promotion is made by this infrastructure/reference work.

Third matched baseline, v05 vs aggressive v02: **217-83 (72.3%)**, CI **61.2-83.4%**,
300 games; 81.8 turns average. All three reports use the same 150 distinct maps and have zero
bot errors. In-process timing spikes in this last series (max 260/243 ms, 5/7 slow moves) remain
diagnostic, not a process-deadline pass. Reports: `build/bench/panel-v1-v05-development/`.

Verification: **127 tests, 264/264 exact replays**. Rebuilt frozen-v05 Player.java is 65,201
characters, but the fixed strict process sample **failed 1/4 games**: P1 turn 41 on 903182662,
66.6288 ms spent preparing/sending input, no timestamped response. This does not establish a
66-ms bot search or identify the cause. Preserve the failed report; investigate before claiming
submission timing readiness. No retry-to-green or upload; strategy source is unchanged.

## Frozen submission versions

`BotFactory` loads fixed defaults for v01–v05 from `src/main/resources/btk/bot/versions/`, then
applies explicit overrides. Early snapshots preserve the effective defaults at `8364ad5`,
while later versions record their promoted configuration; they do not retrospectively
reconstruct the original defaults used by every historical bench.
When freezing a new version, add its parameter snapshot as well as its source and factory entry.
See `benchmarking.md` for the complete procedure.

The rows below are historical: their original benchmarks alternated sides across different maps
and used per-game Wilson intervals. They have not been relabelled as results of the new paired
benchmark. New reports use map-grouped Hoeffding bounds for the statistical promotion gate.

| version | date | change | bench vs prev | CG result |
|---|---|---|---|---|
| v00 Autoplace | 2026-09-13 | `AUTOPLACE` towards the first inactive desired pair. Wood 2 exit. | — | not submitted |
| v01 Greedy Routes | 2026-09-13 | Dijkstra cheapest path per inactive pair; rank by `(free + own - opp) * turnsEarning / (turnsToFinish+1)`; spend 3 paint along the best path, leftover to next pair. Idles (`WAIT`) once every pair is connected. No DISRUPT. | vs v00: 66% [56-75], n=100, CG maps (Wood rules) | not submitted separately |
| v02 Route Evaluation | 2026-09-13 | Simulation-evaluated candidates: per pair, cheapest route (any tracks) + own route avoiding opponent tracks (steal); value = income delta after completion (mine − opp) × turns earning × instability risk / (turnsToFinish+1). Free DISRUPT chosen by simulating ink of each region (opp income lost − 1.2 × own loss, + not-yet-earning tracks) / disrupts needed. Routing surcharge for unstable regions. `NOISE` param to break self-play mirrors. | vs v01: 100% (300-0), +29k pts. Ablations (both sides NOISE=0.2, n=300): no DISRUPT 97.3%, no STEAL 87.5%, no instability avoidance 54.8% [49-60]. | not submitted separately |

| v03 Risk Aware | 2026-09-13 | v02 + state value = income diff − W_VULN × worst single ink the opponent could do (my loss − their loss, / disrupts needed, only regions on active paths with my tracks). Third candidate type: backup route for an active pair avoiding the most threatened region. Closing rule: an ink that leaves no connectable pair is taken only when ahead, never when behind. `HORIZON` param (default 100). `NOISE=0.1` default (deterministic mirrors gave 0-0 games). Opponent model (`USE_OPP_MODEL`, collision surcharge) implemented but off: 47-51% in ablations. | vs v02: 58.5% [51.6-65.1], n=200. Ablations (n=300): no backup 60.2% [54.5-65.5] sig.; W_VULN=0 & no backup 52.7%; USE_CLOSE=0 48% (self-play games rarely close); HORIZON 20/30/50 → 52/53/43%. Process check: first turn ≤ 660 ms, later ≤ 38 ms. | **Silver, around 350/645**; Gold not yet open; usually wins boss games |
| v04 Network Extension | 2026-09-14 | v03 + idle extension: when no complete route improves evaluation, spend remaining paint next to own rails/towns; prefer uninkable town regions and avoid non-town regions at instability ≥2. | vs v03: **64.7%**, W-L-D 194-106-0, n=300/150 paired maps, map-grouped 95% CI **[53.6%, 75.8%]**, avg score diff +405. Control vs v02: 54%, identical to v03 on the same 50 maps. Process check: 0/8 problem games, first turn ≤745 ms, later ≤43 ms. | Boss **7/7**; awaiting Silver arena result |
| v05 Disrupt Reserve | 2026-09-14 | v04 + 6-ms reserve for sabotage: cut route/idle search short on the original turn clock; evaluation formulas and CGRunner margin unchanged. | Confirmation vs v04: **57.7%**, W-L-D **575-421-4**, n=1000/500 new paired maps, map-grouped 95% CI **[51.6%, 63.8%]**, avg diff **+879.1**. Common opponents: v03 **69.3% vs v04's 61.3%**; aggressive v02 **70.0% vs v04's 67.7%**, each 300 matched games. Process check: **0/8** problem games, first <=676 ms, later <=34 ms. | Uploaded as agent **6736850**. User: rank 321 at 43%; API snapshot rank 333. First 56 fetched games: **38-18**, no own timeouts; placement ongoing |

## Paired benchmark checkpoint (2026-09-13)

Infrastructure verification at strategy commit `8364ad5`, using the fixed parameter snapshots
and two worker threads. No new version or strategy promotion:

- `current` vs `v03`: 200 games on 100 generated maps, seeds 1000..1099; W-L-D **86-82-32**,
  A **51.0%**, map-grouped 95% CI **[37.4%, 64.6%]**. No bot errors; statistical gate not passed.
  Report: `build/bench/paired-current-v03.json` (local, gitignored).
- `current` vs `v02`: 40 games on 20 maps from `replays/cg`; W-L-D **22-14-4**, A **60.0%**,
  map-grouped 95% CI **[29.6%, 90.4%]**. No bot errors; smoke check only, gate not passed.
  Report: `build/bench/paired-current-v02-replays.json` (local, gitignored).

Full commands, timing limitations and validation results are recorded in `DEVLOG.md`.

## What top Silver players do (`gradlew cgStudy -Pbot=current`, 107 replays, winner's moves)

- 97/107 games close early (no connectable pair), average ~50 turns. Disrupt on 92% of turns,
  first disrupt at turn 0-1 in 86% of games, targets mostly regions with opponent-only tracks.
- Place on 88% of turns, 2.15 cells/turn (baseline current/v03 study: 73%, 1.76; v04 idle
  extension: 100%, 2.44 — it now overbuilds rather than idles).
  29% of their cells go to regions shared with the opponent (hostage effect), 20% to town regions
  (uninkable), 51% adjacent to own tracks.
- v04 agrees on 15.8% of winner placement cells; its 53% adjacency to own rails closely matches
  the winners' 51%. The reported 17% versus 31% income-raising rate is **among turns with
  placements**, not all turns. The hypothetical placement-only check ignores simultaneous
  sabotage and does not measure how the bot's own games develop.

These historical policy-study measurements are diagnostics, not arena outcomes. Counts such as
1.76 and 2.44 are **cells/turn**, not paint/turn. Legacy study counts only explicit PLACE_TRACKS
commands (not accepted AUTOPLACE expansion) and is not the player-specific loss-analysis tool.

## v04 arena diagnostic checkpoint (2026-09-14)

- Public API snapshot: `aabzac`, agent **6735801**, rank **477**, score **11.78**; placement
  completion was not independently established. Latest 50 completed games: **23 wins / 27 losses**,
  no server timeouts. This matchmaking-selected sample is not an independent strength benchmark.
- Height 14-17: **18/20 wins**; height 18: **4/10**; height 19-20: **1/20**. Opponents differ, so
  these are exploratory strata rather than a causal estimate of map size.
- Captured large-map states reproduce loss of the sabotage phase under tight search budgets;
  it runs after route search on the same timer. This also happens with idle extension disabled.
- Full findings, one-turn placement counterexamples and limits: `arena-analysis-2026-09-14.md`.
- No strategy change / no v05. Timing-aligned v04-v03 control on new seeds 4000..4149:
  **63.7%**, W-L-D **191-109-0**, 300 games, map-grouped 95% CI **[52.6%, 74.8%]**.
  The previous-version advantage persists under the smaller allowance; it does not establish
  broader arena strength. Equal 54% estimates against v02 from the old small control did not
  establish equivalence or prove non-regression.

## Default-off experiment: sabotage time reserve (2026-09-14)

Historical initial screen, before the confirmation below. `current?DISRUPT_RESERVE_MS=6` cuts
route/idle search short on the same turn clock; `0` (then the default) retains v04 scheduling.
Frozen v01-v04 do not read this field. Evaluation formulas, total budget and margin are unchanged.

- Screen vs v04: **54.3%**, W-L-D **162-136-2**, 300 games on 150 distinct downloaded CG maps,
  sides swapped, 2 threads, CG profile; map-grouped 95% CI **[43.2%, 65.4%]**, mean score diff
  **+636.5**, no bot errors. **Gate not passed**. Report: `build/bench/reserve6-v04-cg.json`.
- On two captured large-map t40 states, all 6 repeat probes now emit sabotage at 36 ms; both
  v04 and reserve=0 omit it in all 6. This verifies the local failure mechanism, not arena wins.
- Post-hoc height 19-20 subset: **57/92 wins (62.0%)**; smaller maps are mostly unchanged.
  This selected small subset is diagnostic, not an independent promotion benchmark.
- Experimental submission: 0/8 process problem games, including both sides of the two failure
  maps; first response <=663 ms, later <=37 ms. Not a measurement of CG-host timing.
- Initial decision: leave the flag **off**, without a 1000-game confirmation. The subsequent
  matched v03 check below supports further testing, but does not pass the direct-v04 gate.
  Partial-build safety and diverse-opponent validation remain open.

## Common-opponent follow-up: reserve=6 versus v03 (2026-09-14)

Both campaigns used new authentic generated seeds **5000..5149**, 150 identical distinct maps
(hashes verified), both sides, 300 games each, 2 threads, CG 486/36-ms search profile. Runs were
sequential: control then candidate; no strategy/default/threshold changes.

- v04 vs v03: **61.3%**, W-L-D **184-116-0**, map-grouped 95% CI **[50.2%, 72.4%]**,
  mean score diff **+380.8**. Report: `build/bench/v04-v03-holdout5000.json`.
- `current?DISRUPT_RESERVE_MS=6` vs v03: **69.3%**, W-L-D **208-92-0**, same-method CI
  **[58.2%, 80.4%]**, mean score diff **+813.3**. No bot errors in either campaign.
  Report: `build/bench/reserve6-v03-holdout5000.json`.
- Matched games: **33 added wins, 9 lost wins, 258 unchanged**; net **+8 percentage points**.
  Map-pair totals improved on 24 maps, worsened on 3, tied on 123. A diagnostic paired-map
  percentile bootstrap (20,000 samples, seed 20260914) estimates the difference against v03
  at **[+4.3, +11.7] pp**. This approximate interval is not the acceptance gate; it does not
  account for host/JIT/order effects or different arena opponents. Comparison report:
  `build/bench/reserve6-v03-holdout5000-comparison.json`.
- Decision: retain as a promising, default-off candidate, not a disproven improvement. The old
  direct-v04 result remains inconclusive. v03 is a related reference, not diverse arena coverage;
  beating it more often is not transitive proof of beating v04. No v05 or threshold change.

## v05 confirmation and promotion (2026-09-14)

- Fixed candidate from **cb0f440**, `DISRUPT_RESERVE_MS=6`, against v04 on new authentic seeds
  **6000..6499**: 1000 games / 500 distinct paired maps, 2 threads, CG 486/36-ms profile.
  **57.7%**, W-L-D **575-421-4**, map-grouped CI **[51.6%, 63.8%]**, no bot errors.
  Existing gate passed without changing the criterion. Report: `build/bench/reserve6-v04-confirm6000.json`.
- Aggressive reference is the fixed spec
  `v02?W_SELF_LOSS=0.5,W_FUTURE_TRACK=2.0,W_FUTURE_OWN=0.1`, not a new independent strategy.
  On the same 150 fresh maps (seeds **8000..8149**, both sides, 300 games each), v04 scores
  **67.7% (203-97)** and reserve=6 **70.0% (210-90)**. Candidate adds **14** wins and loses **7**;
  279 unchanged. This small positive difference does not establish equivalence/non-inferiority
  or arena strength. No errors. Reports: `build/bench/{v04,reserve6}-v02aggressive8000.json`.
- Together with the earlier matched v03 check, this supports promotion after the primary gate.
  Frozen as **v05** with the full confirmation-report parameter snapshot; current defaults now
  enable the 6-ms reserve. Evaluation formulas and v01-v04 source/snapshots are unchanged.
- `test`: **72 passed**; `cgValidate`: **158/158** exact; `submit`: compiled;
  process checks: **0/8** problem games, including both sides of the two large failure maps.
  First response max **676 ms**, later max **34 ms**, no local timeouts. CG-host timing differs.
- Ready file: `build/submit/Player.java`, SHA-256
  `5307e4f0f53b39ef8768d364e24fcf701d4f9ca3eafa444aa23a38d3a34a9c5a`.
  Manual boss/arena verification remains pending; no CG upload was performed.

## v05 arena checkpoint and build-income guard screen (2026-09-14)

- User reports rank **321** at **43%** placement (~54 games); API fetch snapshot shows rank
  **333**, score **18.11**, agent **6736850**. These are provisional observations, not matched
  comparisons with the remembered v03 rank (~335). The public API does not expose source hash.
- **56** fetched games: **38 wins / 18 losses**, no own server failures; one win includes an
  opponent timeout. All **214/214** corpus replays reproduce exactly. Height 19-20 record:
  **11/18 wins** (previous v04 sample 1/20); different opponents/placement stages prevent a
  causal version comparison. In losses, sabotage frequency is **75.8%**, versus v04's earlier
  **40.1%**. Report: `build/analysis/agent-6736850.json`.
- Harmful-build examples persist: **24** simultaneous-turn counterexamples in **14/18** losses.
  Known-board-only examples are checked in for Reh934 t27 (-26 income difference), fsicre
  t52 (-34), and older DiogoGiassi t46 (-38). RTJ t13's -71 requires the opponent's simultaneous
  sabotage; its construction alone improves income difference by +44, so it is a different
  prediction problem. These deltas are not additive match-score losses.
- Tested `current?GUARD_BUILD_INCOME=1`: veto candidates whose actual affordable prefix or
  idle placement reduces raw current income difference, using core simulation. It finds
  alternative builds on the three known-board regressions without using future opponent moves.
- Screen vs v05: **48.1%**, W-L-D **206-222-0**, **428 games / 214 distinct downloaded maps**,
  map-grouped CI **[38.8%, 57.4%]**, mean score diff **-58.1**, no errors. Gate not passed.
  Report: `build/bench/guard-income-v05-cg428.json`. No confirmation or promotion.
- Keep **v05** and `GUARD_BUILD_INCOME=false`. The hard veto is not supported as an overall
  improvement, despite fixing selected positions; the wide interval does not prove inferiority.
  Next hypothesis: change affordable placement order (e.g. build the other end first) to delay
  a harmful shortcut without discarding a valuable future route. No v06 or new submission.

## Unpromoted experiment: build direction (2026-09-14)

- `current?CHOOSE_BUILD_DIRECTION=1,GUARD_BUILD_INCOME=0` preserves the selected route but
  compares the affordable cells from either end using known-board income difference.
  Ties retain forward order; a temporarily losing route is not rejected.
- Versus **v05**, all **214 distinct CG arena maps**, both sides, **428 games**, 2 threads,
  CG timing: **52.3%**, **224-204-0**, map-grouped 95% CI **43.1-61.6%**, average score
  difference **+76.8**, no bot errors. Report: `build/bench/build-direction-v05-cg428.json`.
- **Not promoted**: the interval includes 50% and the point estimate is below 55%.
  Keep both experimental flags false; current and the existing submission remain v05.
  Fixes selected Reh934/fsicre prefixes but not the captured DiogoGiassi shortcut.
  No confirmation/older-opponent/process promotion checks after this failed screen.

## Unpromoted experiment: early ready-to-ink sabotage fallback (2026-09-14)

- `current?EARLY_DISRUPT_FALLBACK=1,GUARD_BUILD_INCOME=0,CHOOSE_BUILD_DIRECTION=0` saves a
  deterministic instability-3 target before route search. The normal post-build sabotage scan
  remains authoritative when complete; a target affected by placements is rescored before use.
- Versus **v05**, all **214 distinct CG arena maps**, both sides, **428 games**, 2 threads,
  CG timing: **49.8%**, **212-214-2**, map-grouped 95% CI **40.5-59.1%**, average score
  difference **-28.9**, no bot errors or nominal-budget slow moves. Report:
  `build/bench/early-disrupt-v05-cg428.json`.
- **Not promoted**: the point estimate is below 55% and the interval includes 50%.
  `EARLY_DISRUPT_FALLBACK` stays false; current and the uploaded submission remain v05.
  No confirmation, older-opponent or process promotion checks were run.

## Unpromoted experiment: top-4 routes vs top-2 likely replies (2026-09-14)

- `current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=4,OPP_REPLY_WIDTH=2` keeps four distinct
  affordable build prefixes, predicts two deterministic opponent build-and-sabotage replies,
  resolves each pair through the core, then selects the prefix with the best worst-case value.
  The original v05 choice remains the timeout fallback; all earlier failed flags are off.
- Versus **v05**, all **214 distinct CG arena maps**, both sides, **428 games**, 2 threads,
  CG timing: **46.5%**, **199-229-0**, map-grouped 95% CI **37.2-55.8%**, average score
  difference **-50.8**, no bot errors. Candidate search averaged **9.75 ms** versus **6.50 ms**
  and recorded 3 nominal-budget slow moves. Report:
  `build/bench/reply-rerank-v05-cg428.json`.
- **Not promoted**: the point estimate is below 55% and the interval includes 50%.
  `USE_REPLY_RERANK` stays false; current and the uploaded submission remain v05.
  No confirmation, older-opponent or process promotion checks were run after the failed screen.
- Predeclared fresh-map diagnostics on the same 150 generated maps, 300 games each:
  K=4/R=1 scored **49.3% (148-152-0)**, CI **38.2-60.4%**; K=2/R=1 scored
  **55.7% (166-132-2)**, CI **44.6-66.8%**. Reports:
  `build/bench/reply-rerank-{k4r1,k2r1}-v05-gen10000.json`.
- K=2/R=1 is the better point estimate and cheaper average search of the two diagnostics, but
  its lower bound does not exclude 50%. Both stay off; the sample was not adaptively extended
  and no independent confirmation was started.

## Offline diagnostic: adaptive sabotage forecast (2026-09-15)

- No strategy change or v06. Five fixed experts plus adaptive weights are evaluated by
  `cgForecast`, outside the submitted bot. 56 games of agent 6736850: 40 development games,
  16 holdout games, split by opponent agent. Existing inspected corpus, not fresh confirmation.
- Holdout top-1: adaptive **69.9%**, fixed mixture **57.7%**, persistence **68.2%**.
  Mean-per-opponent log-loss improvement versus fixed: **+0.0928**, 95% CI **[-0.0450, +0.2220]**;
  versus development-selected persistence: **+0.2292 [+0.0861, +0.3841]**.
- Diagnostic gate **not passed** against both controls. No gameplay bench or promotion;
  current stays v05. Protocol, limitations and command: `sabotage_forecast.md`.
- Restored `Player.java` from frozen v05 strategy with current shared runtime: **62,821 / 100,000
  characters**. Generated manifest records parameters and SHA-256. 0/8 process problem games
  versus v04, including both sides of two captured large maps; a 180-ms measured later response
  without a registered timeout prevents claiming strict arena timing compliance.

## Unpromoted experiment: interrupt nested vulnerability evaluation (2026-09-15)

- `current?INTERRUPT_VULN_EVAL=1,PROFILE_SEARCH=0` checks the existing build cutoff between
  region simulations, discards incomplete candidate values, and retains the best completed
  candidate. No changed evaluation weights, sabotage reserve or warmup. Other experiments off.
- Fixed screen vs **v05**, **300 games / 150 generated paired maps**, seeds 12000..12149,
  CG timing, 2 workers: **51.3% (154-146-0)**, map-grouped 95% CI **40.2-62.4%**, average
  score difference **+38.9**, no bot errors. Candidate search averaged 7.06 ms (max 61, one
  nominal-budget slow move), reference 7.01 ms (max 36). Report:
  `build/bench/interrupt-eval-v05-gen12000.json`.
- **Not promoted**: neither the practical nor confidence gate passed. Flag remains false,
  current remains v05; no confirmation or older-opponent promotion bench. The separately
  predeclared process checks are timing diagnosis, not promotion evidence.
- Recorded-state profiling suggests tighter phase timing: later build p95 32.30 -> 30.54 ms,
  calls above 31 ms 90 -> 7 of 396. This is instrumented, sequential before/after evidence,
  not a controlled CPU speedup estimate or guarantee of CG deadlines. 214 incomplete evals
  were discarded. See DEVLOG for artifact checks and the unresolved warmup gap.

## Runtime fix: isolated route warmup (2026-09-15)

- **v05 strategy unchanged; no v06 or strength claim.** MainBot now warms a disposable instance
  of the selected strategy with 36-ms calls within the existing 500-ms allowance. Live RNG and
  game state are isolated. Generic Bot warmup remains unchanged for frozen strategy consumers.
- Fixed local process comparison against v04, 8 games each: old warmup first-response mean/max
  **626.94 / 696.84 ms**, isolated warmup **554.22 / 577.89 ms**; both 0/8 failures, later
  maximum **38.22 / 38.24 ms**. Sequential timing sample, not a statistical speedup claim.
- Runtime acceptance gate passed: semantic tests green, candidate no protocol/deadline failures,
  all first responses below 800 ms. `ISOLATED_ROUTE_WARMUP=true` is now the runtime default;
  failed strategy flags remain off. In-process strength benches do not exercise this hook.
- Final default v05 artifact independently rechecked: 0/8 failures, first max **620.88 ms**,
  later max **38.115 ms**, 109 tests and 214/214 exact replays. **64,775 / 100,000 characters**,
  source SHA-256 `748b564a77a0d3293a925913f396a60d4598783501bc4a74a9a6624f6fadbbc5`.
  Timed decisions can differ with JIT/hardware even though ample-time strategy semantics match.

## Known gaps (to become versions)

- Self-play does not reproduce the top Silver meta (v04 still averages 84 turns vs about 50):
  benches against frozen versions may not
  transfer. Needs a more aggressive reference opponent or evaluation against replay states.
- v04 overbuilds locally (100% of turns vs 88% for winners); refine which extensions are useful
  using replay-state evaluation rather than maximizing paint usage itself.
- Pre-emptive disrupts of empty regions on the opponent's likely corridors; disrupt every turn.
- No lookahead: a 2-3 turn plan search over (placements, disrupt) with the engine.
- Parallel benches show 100-300 ms outliers under 13 threads. The 2026-09-15 strict process
  check (zero grace) also caught one local v05 deadline miss in eight games: 50.7868 ms on
  903509641, P0 turn 53; consumer resumed at 56.2206 ms. The other seven games passed.
  This does not establish CG-host timeout frequency or prove a JIT/GC cause. Profile large-map
  work and first-turn headroom before the next strategy experiment; source/artifact unchanged.
