# Bot versions

Active strength policy (2026-09-16): **practical-300-1000-v1**. Direct 300 games at >=60%,
otherwise separate fresh 1000 at >=55%; then matched candidate/control vs a common opponent,
300 each at >=+3 percentage points, otherwise fresh 1000 each at a nonnegative difference.
CI is diagnostic only; no overall 95% guarantee. Both stages, zero bot errors, engine fidelity
and exact-artifact process checks are required. See [benchmarking.md](benchmarking.md).
Historical entries retain the policy and verdict in force when those experiments ran.
`current` = **v06**, `btk.bot.current.RolloutBot`; frozen copies in `btk.bot.vNN`.

v06 was selected on 2026-09-16 at the user's request after fixed 2x1000 confirmation:
**1230-770 (61.5%) vs v05**, map-grouped 95% CI **57.21-65.79%**. Both individual gates pass;
older-opponent checks are **68.5% vs v03** and **74.5% vs pressure-v1**, 200 games each.
The frozen policy and all parameters match that evidence. Earlier sections below retain their
historical decisions; the current selection supersedes their prior default-v05 status.

Packaging checks at v06 promotion: **217 tests pass**, **264/264 arena replays exact**. The promotion-time default Player is
56,007 characters, source `7f4d94e40e5252d09b86df6e1b8c208e92c86d98a521d858eaf182a9178c2c9c`.
Its fixed strict process check **FAILED 1/42 games**: map 903510176, P0 turn 73, response
**50.462 ms** with a 50-ms deadline (send 0.164 ms). This supersedes a readiness claim based
only on the earlier candidate's 0/42 pass. v06 is selected locally; timing readiness is open,
no arena upload by the agent. Report and executable archive: `build/analysis/v06-promotion/`.

Readable labels and six runnable retained experiments are listed in [bot_catalog.md](bot_catalog.md)
and `gradlew botCatalog`. Version ids remain stable; `EvalBotVNN` class names remain frozen.
Named experiments are local, not promoted, and verify a pinned engine/configuration snapshot.
Their historical screen results are not new evidence for the shared catalog snapshot.

Latest user-reported arena snapshot (2026-09-16): **v06 uploaded, 153/645**, after **rank 270
for v05** before upload. Exact deployed source hash is not independently verified. The 30 saved
Boss 4 games split by the user's corrected first-16/last-14 assignment give **v05 2-14**, **v06 5-9**,
with the first v06 game **904146009**,
on 30 distinct unpaired maps; all validate exactly, with no failures or skipped commands.
See [the loss diagnosis](boss-v06-loss-analysis.md) for route-portfolio evidence and the next
single-change proposal. No strategy change or new strength confirmation in this analysis.

Previous user-reported arena snapshot (2026-09-15): **Gold opened; still Silver, 235/622**.
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

## Independent simultaneous rollout v1 (2026-09-16, initial screen; later promoted as v06)

`current?USE_ROLLOUT_POLICY=1` selects a separate RolloutBot. It searches eight affordable turns
of both players' construction and sabotage, comparing earned score across two opponent route
priorities. Core SearchState provides reusable exact simulation; historical policies and existing
core implementations are unchanged. [Design and limitations](rollout-policy.md); evidence in DEVLOG.

Fixed development results, CG 486/36-ms search, two workers, paired generated maps:

- vs **v05: 170-130-0, 56.67%**, 300 games / 150 maps, Hoeffding95 **45.58-67.76%**.
  Mean score difference +277.17. The confidence gate is **not passed**.
- vs **v03: 137-63-0, 68.5%**, 200 games / 100 maps, CI **54.92-82.08%**.
- vs **pressure-v1: 149-51-0, 74.5%**, 200 games / 100 maps, CI **60.92-88.08%**.

No bot errors; candidate parameters unchanged throughout all 700 games. Reference results do not
replace the v05 gate. Keep USE_ROLLOUT_POLICY=false and current=v05; retain the candidate without
a new frozen version at the end of that screen. Development seeds 30000..30149 inadvertently
overlapped reserved confirmation-v1: preserve the screen as development evidence and retire that
reserve. The subsequent user-requested fixed confirmation is recorded below; see
[pool status](../bench/pools/README.md).

**214 tests**, **264 arena + 27 boss exact replays**. All 110 saved-state work/legality checks
completed at least one full comparison. Candidate submission 56,008 chars; strict process sample
**0/4 failures**, first max 579.57 ms, later 40.59 ms. In-process outliers include 196/296-ms
candidate/opponent maxima against pressure; do not infer arena timing safety.

Restored v05 submission **52,219 chars**, source `1b7ba04b299e7e31af66c8d2de45654cec2a78b80233dac673402aae825214a3`.
Its strict process sample **FAILED 4/4**, two later turns and two first turns. First input send
took 267..1178 ms, dominated by flush; one send exhausted the deadline. Cause remains open;
both reports retained, no retries or upload. Final Player is v05 **without a fresh process pass**.
Reports/artifacts: `build/bench/rollout-v1/`, `build/analysis/rollout-v1/`.

### Fixed confirmation: two independent 1000-game series (2026-09-16)

Unchanged `13aee8e` strategy/parameters vs v05, CG 486/36-ms budgets, two workers, sequential
runs on disjoint fresh maps; all prior benchmark map identities excluded before play:

- confirmation-v2, seeds 90000..90499: **605-395-0, 60.5%**, map-grouped Hoeffding95
  **54.43-66.57%**, gate **PASS**. P0 308/500, P1 297/500; score difference +338.303.
- confirmation-v3, seeds 91000..91499: **625-375-0, 62.5%**, CI **56.43-68.57%**, gate
  **PASS**. P0 319/500, P1 306/500; score difference +377.823.
- Predeclared pooled result: **1230-770-0, 61.5%**, **2000 games / 1000 distinct maps**,
  CI **57.21-65.79%**, gate **PASS**. P0 627/1000, P1 603/1000; score difference +358.063.

Zero bot errors. Source/class hashes, effective parameters and unchanged v05 Player verified.
Run 1 search max A/B 61/39 ms, slow 2/0; run 2 max **515/461 ms**, slow **307/180**, affecting
36 games for either side. Keep all games: no filtering or retries. In-process timing outliers
are not process-deadline measurements, and their cause remains unknown. Statistical passes
support the measured head-to-head advantage, not an arena timing-readiness claim.

**Not promoted**: this was a measurement request; current=v05 and USE_ROLLOUT_POLICY=false.
No packaging changes or new process sample. Consumed v2/v3 are no longer reserves; untouched
v4 is registered for future confirmation. Reports: `build/bench/rollout-confirmation-2x1000/`;
protocol, full outcome, hashes and timing limitations in DEVLOG.

### Fixed strict process check (2026-09-16)

Both unchanged submit-generated artifacts passed **42 games each**, 21 real maps with both
sides, vs frozen v04, serial games, **1000/50-ms deadlines, zero grace**, no IO instrumentation.
Candidate and control source/executable hashes match the earlier artifacts.

- **RolloutBot: 0/42 problems**, 3190 responses, no timeouts or nominal overruns. Startup max
  **662.59 ms**; ordinary-turn p95 **38.17 ms**, p99 **39.05 ms**, max **47.857 ms**.
  Two turns exceed the separate 45-ms headroom target; worst remaining margin **2.143 ms**.
- **v05: 0/42 problems**, 3115 responses. Startup max **668.31 ms**; ordinary-turn p95
  **34.18 ms**, p99 **37.63 ms**, max **40.09 ms**. One timely answer (32.57 ms) was consumed
  by the host at 66.58 ms; it was correctly accepted using the reader timestamp.

Conditional diagnostics were not triggered. Earlier large delays did not recur; their cause
remains unknown, not fixed. This is a local timing pass, not a guarantee about CodinGame or
a new strength comparison. Current/default/final Player remains v05, now freshly process-tested.
Exact candidate: `build/analysis/rollout-process-v1/Player-candidate.java` (56,008 characters),
source hash `d7f259ca8ecf70757458e69282f89c2597151e5e895c8d3753490707e1c92c8f`.
Reports and manifests in the same directory; full protocol, hashes and outcome in DEVLOG.

## Short own-build planning (2026-09-16, not promoted)

`current?USE_OWN_BUILD_PLAN=1`: up to three old-scored root routes, three actual affordable build
turns, at most 39 nodes / 27 leaves. Uses core scoring, earned points and existing terminal
vulnerability value. No opponent moves or future sabotage; no new future routes. Incomplete
comparisons fall back, and only the first planned placement set is emitted before normal sabotage.

Fixed direct screen vs v05: **147-153-0, 49.0%**, map-grouped 95% CI **37.91-60.09%**, 300 games /
150 paired generated maps, seeds 29000..29149. Gate not passed. Matched aggressive-v02 control,
200 games each on seeds 29000..29099: candidate **133-67 (66.5%)**, frozen v05 **150-50 (75.0%)**;
34 added / 51 lost wins, 115 unchanged. No promotion, tuning, confirmation or new frozen version.

All runs use CG 486/36-ms search and two workers, no bot errors. Candidate direct-screen mean
8.04 ms vs 6.99 ms, one 69-ms in-process outlier; not a process timeout diagnosis. **200 tests**,
**264 arena + 26 boss replays exact**. Candidate/restored-v05 diagnostic-enabled zero-grace
process samples each **0/4 failures**; first maxima 645.44 / 639.34 ms, later 35.80 / 35.17 ms.
Old startup failures remain unexplained; small successful samples are not a general safety claim.

Flag stays **off**. Final Player restored from **v05**, 50,038 chars; experiment artifact 82,376
chars, both below 100,000. Protocol, limitations, exact hashes and retained reports are recorded
in DEVLOG under own-build-plan-v1. Frozen strategy files and reference archives were not edited.

## Observed-threat defense priority (2026-09-16, not promoted)

`current?USE_DEFENSE_PRIORITY=1` adds a bounded first-build priority for a <=6-paint bypass of
already damaged active income. It uses core pathing/scoring, checks actual per-turn packing and
non-losing prefixes, and replans each turn. It is separate from the independent heuristic policy;
other experiments remain off, default current/selected submission remains v05.

Fixed direct screen: **141-157-2, 47.33%** vs v05 on **300 games / 150 paired generated maps**,
seeds 27000..27149; map-grouped Hoeffding 95% CI **36.24-58.42%**, mean score difference
**-183.69**, zero errors. **No promotion**, no confirmation run or parameter tuning.

Matched older/style control against `v02?W_FUTURE_TRACK=1.0,W_FUTURE_OWN=0.2`, 200 games each
on seeds 27000..27099: candidate **127-73 (63.5%)**, CI **49.92-77.08%**; frozen v05
**147-53 (73.5%)**, CI **59.92-87.08%**. Same map hashes, sides, opponent config and timing;
candidate adds 19 wins but loses 39, with 142 unchanged outcomes. These are descriptive
common-opponent results, not another direct comparison or a statistical proof of general harm.

All three runs use CG 486/36-ms budgets, two workers, default SEED/NOISE, with zero bot errors
or >50-ms in-process slow moves. Seven new defense tests plus merged ON parity/warmup: **189
tests passed**, **264 arena + 26 boss replays exact**. Full controls, process checks and source
hashes: defense-priority-v1 entries in DEVLOG; local reports under `build/bench/defense-priority-v1`.
Candidate process check: 0/4 failures, first max 807.38 ms (above 800-ms headroom target), later
39.23 ms. Restored-v05 check **failed 4/4, all at turn 0**, with input sending taking up to
1139 ms and no recorded response. Source restored to v05, but **not freshly timing-approved**;
preserve the failure for separate input/startup diagnosis, no retry-to-green or upload.

## Simple heuristic candidate (2026-09-16, not promoted)

`current?USE_HEURISTIC_POLICY=1` selects an independent known-board policy: sticky sabotage,
local ownership/paint route priorities, avoiding its target region, fixed path-query cap and
emergency-clock protection. No general position evaluator or reply search. Details and debug
commands: [heuristic-policy.md](heuristic-policy.md). Default current and selected Player remain v05.

Fixed screen vs v05: **126-174-0, 42.0%**, 300 games / 150 paired generated maps, seeds
22000..22149; map-grouped CI **30.9-53.1%**, mean difference **-205.7**. No promotion.
Mean local move time **1.32 vs 4.78 ms**; this is timing on different match trajectories, not an
isolated speed benchmark. Independent style check vs pressure-v1: **104-95-1, 52.25%** on
200 games / 100 paired maps, CI **38.7-65.8%**; no reliable advantage or matched v05 comparison.
Both runs have zero bot errors. No parameter tuning or confirmation games after these results.

**169 tests**, arena **264/264** and boss **21/21** replays exact. Candidate and restored v05 each
passed one strict four-game process sample: later-response maxima **24.39 / 34.80 ms**, respectively.
These small checks do not resolve the earlier unexplained host input-send stall. This simple v1
is a runnable experimental baseline, not evidence that heuristic policies cannot climb Silver.

Detour follow-up: `current?USE_HEURISTIC_POLICY=1,USE_HEURISTIC_DETOUR=1` adds capped recovery
paint on existing connections (up to 16 extra path queries), with unchanged construction.
Matched v05 screen on seeds 25000..25149: **ON 135-165 (45.0%)**, CI **33.9-56.1%**;
**OFF 140-160 (46.7%)**, CI **35.6-57.8%**; 300 games / 150 paired maps each. The feature adds
31 wins and loses 36 on identical map/side match-ups, with no other config difference. No
promotion or established advantage; this is not a direct ON-vs-OFF match. Pressure style check:
**110-90 (55.0%)**, 200 games, CI **41.4-68.6%**; no matched OFF pressure control was run.

All runs have zero errors. Main-screen mean move time ON/OFF **1.39 / 1.22 ms**, on different
trajectories; not an isolated cost measurement. **177 tests**, **264 + 21** exact replays,
candidate/restored-v05 strict process samples **0/4 problems** each (later maxima **30.39 / 34.58 ms**).
Keep both new policy and detour selector default-off; final Player is frozen v05. Full protocol,
pre-benchmark mirror correction, artifact hashes and limits are in DEVLOG/heuristic-policy.md.

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
| v06 Simultaneous Rollout | 2026-09-16 | Independent eight-turn simultaneous construction/sabotage rollout; six route proposals, two opponent priorities; exact confirmed policy and full parameter snapshot. Selected by the user. | Fixed confirmation vs v05: **1230-770 (61.5%)**, 2x1000 games / 1000 distinct paired maps, map-grouped 95% CI **57.21-65.79%**; both individual gates pass. Older checks: v03 **68.5%**, pressure-v1 **74.5%**, 200 games each. Earlier process 0/42; final packaged source **FAILED 1/42**, later response 50.462 ms. | User-reported upload: 153/645, previous v05 rank 270; exact deployed hash unverified; local timing issue remains open |

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

## Retained narrow reply rerank: pressure follow-up (2026-09-15)

Fixed candidate `exp-reply-rerank?BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1`, pinned engine-v1 and
full snapshot, other experiments off. Three sequential 300-game match-ups on development-v1
(150 identical paired maps, seeds 20000..20149), two workers, nominal CG 486/36 ms:

- Candidate vs v05: **172-128-0, 57.3%**, map-grouped CI **46.2-68.4%**; primary gate not passed.
- Fresh v05 vs pressure-v1 control: **208-92-0, 69.3%**, CI **58.2-80.4%**.
- Candidate vs pressure-v1: **198-102-0, 66.0%**, CI **54.9-77.1%**. Beating the reference is
  not the candidate's promotion gate; its pressure result is below v05's control.

Matched map/side comparison against pressure: **46 added wins / 56 lost wins / 198 unchanged games**,
net **-3.33 pp**; map-pair totals improve/worsen/tie on **26/40/84** maps. Descriptive evidence,
not proof of statistical inferiority. Candidate mean search against pressure is **10.46 ms**
versus v05's **7.58 ms**, on different evolving trajectories. All 900 games have zero bot errors
and nominal-budget slow moves; no new process timing check or readiness claim.

**Keep disabled, no v06 or confirmation.** No parameter tuning, current/source/runtime changes
or Player.java rebuild. This already-inspected development pool is not independent confirmation.
Reports: `build/bench/narrow-reply-pressure-v1/`; full protocol, hashes and slices in DEVLOG.
Next diagnostic: test predicted responses and prefix ranking on shared states before widening
or retuning the beam. The results alone do not identify prediction error as the cause.

## Shared-state K2/R1 diagnosis (2026-09-15)

Tools-only follow-up: 24 newly captured games on six fixed development maps, both sides and
v05/K2-R1 source trajectories against pressure. 110 observations, 108 distinct state/perspectives;
NOISE=0 fresh-instance probes, no strategy changes. See [reply-study.md](reply-study.md).

- First-build model active on 81 observations: exact nonempty construction forecast **12/55**,
  disruption targets **61/81**. These are selected-state prediction metrics, not arena accuracy.
- Actual-response oracle improves the choice under the SAME heuristic in **4 observations /
  3 unique states**. Eleven observations prefer a projected route over a better immediate gain;
  that can be investment, not a proven evaluation bug.
- First prefix matches nominal/ample full play in **81/81** cases. Five of all 110 full actions
  differ only in subsequent construction, with unchanged first prefix and sabotage target.
- Keep disabled; no new strength bench, promotion, confirmation pool or Player rebuild.
  Next hypothesis: inspect joint build/own-sabotage valuation, not wider search from this evidence.

## Joint first-build / own-sabotage diagnosis (2026-09-15)

Same saved 110 observations, no new games or policy changes. The real sabotage chooser sees
known state and each affordable prefix/full planned route, never the recorded opponent action.
Targets differ across prefixes in **8/81** candidate observations. Including each prefix's target
changes the predicted joint choice in **3 unique states**; oracle projected value improves in 3,
worsens in 0. Immediate score effects are **+40 / 0 / -11**, not recoverable final-match points.

One of those examples values **the original prefix + another already known target** above either
prefix with its normal chooser's target. This supports testing a small set of joint action pairs,
not forcing each route to keep one sabotage target. Current/v05/Player stay unchanged and reply
reranking stays disabled. Method, regression examples and limitations: [joint-reply-study.md](joint-reply-study.md).

## Unpromoted experiment: bounded joint build/sabotage (2026-09-15)

`current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1` compares
two prefixes x at most four known-state targets, retaining the old pair on ties/incomplete setup.
Committed pairs emit exactly the evaluated prefix/target; otherwise normal leftover building
remains. Defaults are unchanged. See [joint-action-experiment.md](joint-action-experiment.md).

- Fixed 300 games / 150 paired generated maps, seeds 21000..21149, CG 486/36 ms, two workers:
  candidate vs v05 **130-169-1, 43.5%, CI 32.4-54.6%**. Gate failed; no v06 or confirmation.
- Same maps vs pressure-v1: candidate **196-104, 65.3%**, fresh v05 **195-105, 65.0%**.
  Matched outcomes: **57 added wins / 56 lost / 187 unchanged**, not a transfer improvement
  or proof of equivalence. No comparison with a historical K2/R1 percentage on other maps.
- Saved-state cost check: candidate/control mean **13.50/12.81 ms**, 5 improved pairs on 110
  observations; 72 complete scans, no call >36 ms. Mechanism/cost evidence, not strength.
- **162 tests, 264/264 exact CG replays**. Candidate 93,881 characters, restored-v05 65,269;
  both strict fixed process samples **0/4 failures**, first/later reader maxima respectively
  **669.64/37.24 ms** and **650.75/38.88 ms**. The prior process-input stall remains unexplained.
- Keep USE_JOINT_ACTION=false and USE_REPLY_RERANK=false. Player.java is restored frozen-v05
  with the current Params/runtime; source hash changed due to the new unused flag, not strategy.
  Original retained experiment ids/configurations/code hashes are preserved by one shared
  two-class archive, never shipped. Next diagnostic is a direct on/off joint-flag ablation
  within K2/R1 before inferring whether the regression belongs to this flag or the base branch.

## Diagnostic: direct joint-on/off K2/R1 ablation (2026-09-16)

Same current implementation at `b4d8424`, both K2/R1; full 47-field configurations differ ONLY
in `USE_JOINT_ACTION`. One fixed 300-game run / 150 paired generated maps, seeds 21000..21149,
reusing the joint-screen development pool. Two workers, CG 486/36 ms, NOISE=0.1; no new policy.

- Joint-on vs joint-off: **139-159-2, 46.7%**, map-grouped CI **35.6-57.8%**; mean score
  difference **-150.0**. P0 **67-82-1**, P1 **72-77-1**. Zero bot errors.
- Accepted sabotage **80.7% / 82.3%**, construction **91.8% / 92.0%**; paint/turn
  **2.708 / 2.730**. These are different game trajectories, not a same-state causal comparison
  or evidence that more actions would improve play. Mean game length **76.5** turns.
- Search mean **8.13 / 7.69 ms**, max **52 / 46 ms**, >50-ms slow counts **1 / 0**;
  nominal in-process measurements, not process timeouts. No new artifact or process check.
- **Inconclusive; keep off.** No confirmed benefit, but the interval also does not establish
  inferiority or equivalence. This comparison cannot apportion the prior loss against v05
  between K2/R1 and the joint flag; matchups are not transitive. No tuning or confirmation.
- Source/defaults/frozen references and v05 Player.java unchanged. Existing **162-test** results
  remain up-to-date; fresh **cgValidate 264/264 exact**. Report, hashes and next mechanism audit:
  [joint-action-experiment.md](joint-action-experiment.md#direct-onoff-follow-up-2026-09-16).

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

## Unpromoted experiment: quick income portfolio slot (2026-09-16)

- `current?USE_ROLLOUT_QUICK_ROUTE=1` retains one <=6-paint positive-own/net-income route
  inside the existing six slots while our income is zero. Reuses existing path proposals;
  unchanged depth, opponent model, sabotage, budgets and other parameters. Default-off.
- Fixed direct screen vs **v06**: **160-134-6**, **54.33%** with half credit for draws;
  **300 games / 150 distinct paired maps**, seeds 40000..40149, CG 486/36-ms budget, two workers.
  Map-grouped 95% CI **43.24�65.42%**, zero errors. **Gate not passed**.
- Separate older-opponent check vs **v05**: **124-76-0 (62.0%)**, **200 games / 100 paired maps**,
  seeds 41000..41099, CI **48.42�75.58%**, zero errors. No matched v06 control in this panel;
  the historical v06/v05 percentage is not a baseline for estimating a new improvement.
- No promotion, tuning or confirmation: selected **v06**, flag **false**, no v07 or upload.
  The mechanism works on the captured opening (4-paint candidate retained), but population
  improvement is unproven. The next question is selection/value/clock coverage of retained routes.
- **225 tests pass**; **264 arena + 30 boss replays exact**. Candidate strict process sample
  **0/8 problems**, max first/later response **747.670/47.118 ms**. Final default-off process
  **0/8 problems**, max first/later **718.680/45.982 ms**.
  Final coverage audit found repeated sides in the even-sized process map pool; original reports
  retained, four previously missing map/side combinations checked per exact artifact, **0/4**
  each. Combined **0/12 problems each**, all four maps on both sides. See
  [the experiment report](quick-income-experiment.md) for the protocol deviation and all results.
  Bench maxima A/B were **58/40 ms** vs v06 and **96/36 ms** vs v05; 2/0 slow moves each.
  These outliers are retained and do not establish a cause or explain the older v06 timeout.
- Full reports: `build/bench/quick-income-v1/`; durable parameters, hashes and paired outcomes:
  `docs/evidence/quick-income-v1.json`. Updated default Player: **57,405 characters**, SHA-256
  `5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266`.

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


## Quick-income reevaluation under practical-300-1000-v1 (2026-09-16)

- User explicitly adopted a new practical policy and applied it to the unchanged latest
  candidate, `current?USE_ROLLOUT_QUICK_ROUTE=1`. Historical verdicts above remain intact.
- Reused 300-game screen: **160-134-6, 54.33%**, below the new 60% fast-pass threshold.
- Separate fresh direct continuation vs **v06**: **525-465-10, 53.00%**, **1000 games / 500
  distinct paired maps**, seeds 42000..42499, CG 486/36-ms budgets, two workers, zero errors.
  New practical threshold **55% not met**; descriptive CI **46.93-59.07%** is not the gate.
- Common-opponent comparisons vs v05 **skipped after direct failure**, as predeclared. No
  reinterpretation of the old unmatched 62% as evidence of improvement over current.
- **Not promoted; v06 selected, quick-route flag false.** All 39 original class hashes and
  full parameters match; 4324 older map identities excluded. Strategy/Player unchanged.
  Search maxima A/B 85/97 ms, slow moves 3/5, retained; no new process-readiness claim.
- 226 Java tests, six Python checker tests, installed skill valid, 264/264 arena replays exact.
  Protocol, results and durable evidence: [quick-income-practical-v1.md](quick-income-practical-v1.md).
