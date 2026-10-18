# Geometry-only cheapest-path optimization

Declared 2026-10-18 before implementation and measurements, starting at baf59a1.
The user requests cheaper path construction without changing strategy or guards,
and a check for improvement at 36 ms.

Select one change from the retained ink-refresh JFR profile: cheapestPath has
627/2398 inclusive samples (26.15%), 416 leaf samples (17.35%). The compacted
source prevents reliable attribution within that method. Cache only immutable
grid geometry: each cell's N/E/S/W neighbors, in the existing order. No dynamic
board, path result, price, blocked-mask or search-frontier reuse. Preserve heap
order, strict relaxation, rail flood order, all early exits, costs, exposed
scratch distances, reconstruction and the existing unusual blocking semantics.

Add default-OFF Params.USE_FAST_CHEAPEST_PATH, current native rollout only.
Keep all strategy, safety conditions, RNG, search limits and timer checks.
Allocate optional buffers during initialization; no per-node allocation.
Disabled generated submissions must remain byte-identical to the saved current,
v09 and batched artifacts. Do not edit frozen bot packages or selected exports.

Main candidate: current?NOISE=0.1,USE_BATCHED_REFRESH=true,USE_FAST_CHEAPEST_PATH=true.
Control: current?NOISE=0.1,USE_BATCHED_REFRESH=true. Incremental ink and deadline
correction stay OFF in both. This continues the same controlled performance
base; it does not promote the previously rejected batching optimization.

Before timing, run focused core, rollout and submission tests, record failures,
and gradlew cgValidate (expected 292 exact replay checks). Differential core
tests compare complete ordered paths, every lastCost cell and lastDist contents
across ties, blocking, ink, mutable costs, repeated queries and geometry changes.
Build exact main ON/OFF, default, v09, and supplementary guard ON/OFF with
gradlew submit into new separate directories. Every tested artifact must meet
100,000 characters; do not overwrite previous evidence. Preserve the known
historical catalog-pin test failure rather than altering its pinned reference.

Reuse the fixed 524-position panel from 907134258/907134581/907134651/907134720,
both seats, persistent own policy memory, alternating arm order. First compare
5,000 ms ample decisions, raw selected values and all semantic diagnostics;
require exact equality before interpreting timings. Then compare the same
inputs at first/later 486/36 ms, with the existing disposable 500 ms warmup.
Report equal-work time, completed comparisons/searches, simulated turns and
timing outliers. No profiling overhead in these measurements.

Supplementary guards use the established historical NOISE0, batchingOFF,
deadlineON recipe in both arms, only the new path flag differs. Check turn38
through40 of 907134651/P1 plus six winning starts46/47/49/50/51/52 of
907134720/P1, native reply and WAIT, eight turns or terminal, ample then36 ms.
Require ample equivalence, the turn40 target33 attack and all six winning
closures at36 ms. This separate recipe does not certify a combined-flags bot.

If correctness and packaging pass, complete exactly eight strict process games,
two seats on each of 903510176/904146009/904148181/904147739 against v09,
seeds7000/7001, first/later full response limits1000/50 ms, zero grace,
diagnosticsOFF. Use the frozen shared ProcessCheck on the exact submission.
Retain all failures and outliers; no repeated or substituted measurements.

Only if parity, guards, source size, process checks, equal-work speed and
completed comparisons at36 ms all improve/pass, proceed to one separately
frozen fresh-map local strength schedule. Predeclare all500 map identities and
inputs before play: two workers, both seats, same486/36 ms, fixed300/+200/+500,
strict below50% futility at cumulative300/500, final threshold55% at1,000,
no early pass, retry, tuning or extension. Local failure ends evaluation.
A local pass alone cannot enable the flag: fresh matched boss confirmation
and a production-control comparison remain separate requirements. No live
requests, uploads, promotion, default changes or push belong to this work.

Freeze inputs, runtime and exact artifacts before each measurement domain;
check identities and independently audit raw results. Retain failed attempts,
archive evidence, update BOTS/PLAN/DEVLOG and commit the completed experiment.
