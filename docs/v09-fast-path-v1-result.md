# Geometry-only cheapest path: result

2026-10-18. Complete: **STOP_FUTILITY_300**, optimization stays OFF.

The same complete search takes **7.22% less time** on the fixed saved-state
panel. At 36 ms, completed comparisons increase **4.41%, from 2,995 to 3,127**.
All 524 ample decisions and semantic diagnostics match exactly. The turn-40
attack, six winning closures and eight strict process games pass. However,
the separately frozen strength comparison scores **134W/146L/20D in 300 games,
48.0%**. The predeclared below-50% stopping rule ends evaluation at the first
look. Faster computation did not demonstrate a strength improvement here.

## One implementation change

Follow the [prospective diagnostic protocol](v09-fast-path-v1.md). The preceding
JFR profile attributes 627/2,398 samples (26.15%) inclusively to cheapestPath,
416 (17.35%) at its leaf. Its compacted source does not support reliable
line-level attribution. The selected hypothesis is repeated geometry work.

`Params.USE_FAST_CHEAPEST_PATH`, default OFF, enables a primitive N/E/S/W
neighbor table in the current rollout bot. Allocate it during initialization;
reuse the same array and refill only when grid dimensions change. It caches
no board contents, blocked masks, dynamic costs, frontier or resulting path.
Ordinary expansion, rail-block flood and incoming-direction lookup use the
table. Their existing direction order, queue/heap changes, strict relaxation,
early exits, reconstruction and unusual rail-fringe/blocking semantics remain
unchanged. No new per-node allocation, strategy, timer or guard condition.

Main A is `current?NOISE=0.1,USE_BATCHED_REFRESH=true,USE_FAST_CHEAPEST_PATH=true`.
Main B is `current?NOISE=0.1,USE_BATCHED_REFRESH=true`. Incremental ink and
missed-deadline correction stay OFF in both; every other effective parameter
matches. This isolates the next implementation change on the same batched36
base. It does not overturn batching's earlier verdict against selected v09.

Disabled current, frozen v09, old batched and historical guard source bytes
remain unchanged. Candidate main source is **91,481 / 100,000 characters**,
SHA-256 `51dd8cb73786e9c996eacaaef4512bb3b8e93b8207f9af4aa3e4d4ccad894321`.
Its build identity is `e70f470939bff5ff320bfb6b8fe077491f4253799c0c1f5b387a4faf58460e24`.

## Correctness, packaging and equal work

The final focused test run passes **260/261**, including all **12 new tests**.
Core tests compare the full ordered output and untouched tail, every cell's
lastCost and all lastDist values, mutable ownership/ink/masks/surcharges,
equal-cost ties, blocked connectors, rail-fringe goals, unreachable/same-cell
queries, 1D/1-cell shapes, dimension changes, shared scratch and epoch wrap.
Submission tests verify flag restrictions, historical source hashes, initialization,
source limits and identical executable classes after compaction.

The one retained failure is the pre-existing historical exp-income-guard
catalog pin in FileMergerTest.characterLimitIsCheckedBeforePublishing. Its
Pathing.class rejection occurs before the expected unsupported-export error.
No historical pin was changed; this is not a full-suite-green claim.

The first run passed 259/261: the same historical failure plus the new guard
source-size check at **100,008 characters**. Before any measurements, rename
only the new private identifier cheapestNeighbors to pathNeighbors in four
files, including exporter and test references. Exact replacement checks and
unchanged other source hashes establish the repair's scope. Preserve original
XML, command/log/exit and source snapshots, then repeat the unchanged focused
suite once. No measurement/game retry or assertion relaxation. See the
[packaging amendment](v09-fast-path-v1-packaging-amendment.md).

`gradlew cgValidate`: **292/292 replays exactly reproduced**. Separate
`gradlew submit` exports compile, and 260 prior/selected artifact files stay
unchanged. The diagnostic freeze protects 1,918 workspace files, seven runtime
files and executable/source inventories.

The fixed panel has 524 nonterminal positions, both seats, from games
907134258/907134581/907134651/907134720. Each policy keeps its own persistent
memory; arm order rotates. All 524 ample calls complete and match complete
actions, selected-value bits and every semantic diagnostic field.

Both arms perform 60,118 route queries, 5,850 comparisons and 87,630 simulated
turns. Total equal-work wall time drops from **25,634.9065 to 23,784.0140 ms**,
or **7.2202%**. The 5,000 ms ample allowance deliberately exceeds the ordinary
turn budget and does not certify response deadlines.

At first/later allowances 486/36 ms, after disposable 500 ms warmup:

- Completed comparisons: **2,995 to 3,127**, +4.4073%.
- Simulated turns: **43,876 to 45,892**.
- Route queries: **52,085 to 54,780**.
- Complete searches: **281 to 276**; ordinary completion 260 to 254 and network
  completion 257 to 252. Extra comparisons do not imply more complete searches.
- Native maximum: **37.3012 ms baseline, 40.3820 ms candidate**; neither exceeds
  50 ms in this saved-state panel. Native time excludes protocol IO.

Timed choices may differ as more work completes before cutoffs and subsequent
RNG histories diverge. These fixed-state counters alone do not establish strength.

## Safeguards and full-response timing

The supplementary guard recipe is the historical NOISE0, batchingOFF,
deadlineON configuration in both arms, differing only in the path flag. It
uses the turn38 start of 907134651/P1 and starts46/47/49/50/51/52 of
907134720/P1, each with native and WAIT replies, eight turns or terminal.
All 14 ample conditions match exactly. At both ample and 36 ms, candidate
retains all six winning closures with no lost baseline wins, and attacks
region33 on turn40 in both reply regimes with the existing guards satisfied.
The guard candidate is **99,924 characters**, source SHA-256
`8537ebf806c8d99c0a1cf13090f4f4e32e962acdeca46816b18a10e32415ba54`.
This separate guard recipe does not certify an untested combined-flags bot.

The exact main artifact completes eight strict process games: two seats on
each of 903510176/904146009/904148181/904147739, opponent v09, seeds7000/7001.
First/later full-response limits are 1000/50 ms, zero grace, IO diagnostics OFF.
There are **773 later responses**, with **zero errors, timeouts, nominal
overruns or missing replies**. Maximum later full response is **46.708 ms**
(rounded); this finite local
sample is not a universal guarantee on the arena host. No game was replaced.

Independent diagnostic audit **PASS, 73,368 checks** verifies raw decisions,
guards, process responses, both test attempts, exact lexical repair, freezes,
artifacts and readiness. No engine/game/policy call is repeated by the audit.

## Local strength and final decision

The [strength protocol](v09-fast-path-strength-v1.md) uses two workers and the
same 486/36 ms allowances. All 500 maps are prospectively distinct and absent
from 25,624 prior played/reserved identities, including the previous ink-strength
pool. Thirty synthetic driver tests pass; 4,244 files and seven runtime files
are frozen before play. Fixed blocks 300/+200/+500 stop only below 50% at the
first two looks, with the single final threshold 55% after 1,000.

Block 1 completes once: **134 wins, 146 losses, 20 draws / 300 = 48.0%**,
zero game errors. These are 150 fresh maps, both seats: **74W/66L/10D as P0**,
**60W/80L/10D as P1**. The decision is **STOP_FUTILITY_300**. Blocks 2 and 3
are not played; their 350 predeclared maps remain reserved. No boss stage is
started after the local failure.

Native timing across 27,626 calls per arm averages **15.98 ms candidate,
16.11 ms control**, maxima **43 / 41 ms**, zero recorded calls above 50 ms.
These integer-millisecond counters include first turns and exclude protocol
IO; full-response timing comes from the separate eight process games above.

The first independent strength audit stopped at check 4,300 because it compared
two independently summed performance ratios with exact floating-point equality:
0.07220203826372651 versus 0.07220203826372462. Preserve that audit and script.
A separate v2 auditor uses absolute tolerance 1e-12 only for those ratios while
retaining positive-gain checks and exact counts, artifacts and input hashes.
No game, measurement, build or policy call is repeated for this correction.
Corrected audit **PASS, 21,539 checks**, reconciling raw scores, maps, seats,
budgets, stopping and all frozen inputs. The original failure is retained.

Decision: **do not enable USE_FAST_CHEAPEST_PATH**. The timing improvement is
real in this fixed workload, but it does not meet the predeclared strength gate;
this sample does not establish zero true effect. Keep default 36 ms, batching,
incremental ink, deadline correction and fast path OFF, selected v09 and all
prior verdicts. No live boss requests, arena upload, promotion or push.
Evidence is retained in the
[summary](evidence/v09-fast-path-v1.json) and
[archive index](evidence/v09-fast-path-v1-artifacts.json).
