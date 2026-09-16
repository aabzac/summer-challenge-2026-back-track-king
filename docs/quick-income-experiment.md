# Quick income portfolio slot â€” 2026-09-16

## Protocol recorded before matches

Hypothesis: when our income is zero, retaining a cheap income-producing completion inside the
six-route portfolio can recover choices that the completed-network heuristic discards.
The existing eight-turn rollout still selects the action; no route is forced.

Candidate: `current?USE_ROLLOUT_QUICK_ROUTE=1`, control: frozen `v06` at parent revision
`63e39dc`. Other defaults match v06. Flag defaults off; frozen source/resources are unchanged.
The candidate reuses existing bounded proposals (no extra path queries): positive own and net
income on the hypothetically completed known board, actual construction cost <=6 paint.
Keep minimum cost, break ties by net income, then first enumeration order/canonical direction.
Replace an existing equal first-prefix route, or the last of the six slots. Only our portfolio
changes; opponent construction, sabotage, width, depth and search budgets remain unchanged.
The last slot may receive less coverage under a deadline; process checks and the nominal-budget
matches are necessary in addition to ample-time fixture tests.

Fixed development screen: **300 games / 150 paired maps**, seeds **40000..40149**, candidate
vs v06. Older-opponent check: **200 games / 100 paired maps**, seeds **41000..41099**, vs v05.
Authentic generator, swapped sides, two workers, CG timing (486/36 ms), default bot RNG SEED=1.
An exclusion index of all existing benchmark map hashes is prepared before play; the runner
rejects overlap. Reserved confirmation-v4 is unused. No tuning or sample extension after results.
Gate: score >=55% AND lower map-grouped 95% confidence bound >50%; failures invalidate readiness.
The older check detects gross regressions, not a measured gain over v06 against that opponent.

Before matches: regression tests, exact replay validation, candidate packaging and fixed strict
process sample. Process sample: 8 games, four maps (903510176, 904146009, 904147739, 904148181),
both sides, vs v05, zero grace, I/O diagnostics; preserve all failures, no retries.
After matches: restore default-off current submission and run the same fixed process sample.
Keep unpromoted flags off, record results and commit; no arena upload.

## Results

Main screen completed: **160 wins / 134 losses / 6 draws**, **54.33%** with draws worth half,
**300 games / 150 distinct maps**; map-grouped 95% CI **43.24–65.42%**. Gate **not passed**:
both the 55% practical threshold and the confidence requirement fail. Zero bot errors.
P0: 75/150 wins; P1: 85/150 wins. Average score difference +153.6. This is inconclusive
strength evidence, not proof of a gain, equivalence or harm. Do not extend the sample or tune.

Older-opponent check: **124-76-0 (62.0%) vs v05**, **200 games / 100 distinct maps**,
95% CI **48.42–75.58%**, zero errors. It does not show a clear gross reversal against v05,
but uncertainty is wide; it is not a matched comparison of candidate versus v06 on v05 games.
The candidate had average/max 18.29/96 ms and two slow moves; v05 5.17/36 ms and none.
All 250 new maps are distinct and excluded 4074 previously benchmarked identities; the second
sample additionally excluded the first sample. The reserved confirmation pool is untouched.

**Decision: not promoted; `USE_ROLLOUT_QUICK_ROUTE=false`, v06 remains selected.** No tuning,
additional strength games, new frozen version or arena upload. The final default-off process
check completed **0/8 problem games**, **639 turns**, max first/later response
**718.680/45.982 ms**; 1 later turn exceeded the 45-ms headroom target.
Both process samples use the same four maps/sides, serially and without retries; timing is
observational, not a controlled speedup estimate. The previous promotion-time 1/42 timeout
remains an open historical result. **225 tests pass**, including candidate merged-file/warmup
parity and the positive-ID boss nickname regression. Core unchanged; 264 arena and 30 boss
replays reproduce exactly.

Final default-off source: **57,405 characters**, SHA-256
`5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266`.
Candidate source: **57,404 characters**, SHA-256
`8fb0831f44a7f0ca98ada59371fc2dd7537d1743ac711f78c21a8d7e2023dbfd`.
Both compiled artifacts are archived beside their process reports.
Candidate process sample completed **0/8 problem games**, **583 turns**: maximum first response
747.670 ms, later 47.118 ms; one turn exceeds the separate 45-ms headroom target. This is a
small local timing sample, not a claim to resolve the historical v06 timeout.
The main in-process screen records A/B average 16.71/16.72 ms, maxima 58/40 ms, slow moves 2/0;
all results retained. These clocks omit protocol and do not attribute occasional host/JIT delays.

The captured opening retains the 4-paint route on both player sides, with unchanged query count,
width <=6, <=14 full comparisons and <=224 simulated turns. Eligibility tests reject negative-net,
over-limit and inked completions, reset after income starts, and preserve the expired-turn fallback.
Disabled-flag evolving-board choices match frozen v06. The candidate changes only the new flag
in the complete effective parameter comparison.


## Evidence and follow-up

Full reports: `build/bench/quick-income-v1/candidate-v06.json` and `candidate-v05.json`.
The exclusion index records the hashes of all source reports. Process data, both artifacts and
class/source identities: `build/analysis/quick-income-v1/`. Compact durable results, complete
parameters, paired map identities/outcomes and artifact hashes: `docs/evidence/quick-income-v1.json`.
Policy SHA-256: `1571e7df6bc2ab600b88a92d6319b363f9148c020c4f5078ea255a5652a55e43`;
Params SHA-256: `380a14651f62f0d56fa57ccdded162acd761430df4ffc4837d33cecc4e5e8770`.
The protocol snapshot hash precedes results; the document has since been extended with results.

Next diagnosis should distinguish availability from selection and valuation: on fixed shared
pre-turn states, record whether the quick candidate receives complete reply coverage, is selected,
and how its predicted early income and damage compare with recorded-action counterfactuals.
Include wins as well as losses. This is a proposal for mechanism work, not another parameter
choice or a claim that a wider search will improve strength. Do not tune on confirmation maps.


### Process coverage correction, declared before supplemental games

Final audit found a protocol deviation: ProcessCheck uses map index `i` and player `i%2`.
With an even four-map directory, eight games repeat the same four map/side combinations.
Both original 0/8 reports remain valid observations but do **not** cover both sides of each map.
Before any supplemental games, register exactly four missing combinations per unchanged artifact:
904146009 P0, 903510176 P1, 904148181 P0, 904147739 P1. A directory with numbered filenames
selects these combinations deterministically; raw map contents remain unchanged. This completes
coverage, without rerunning a previously checked combination or changing any strategy parameter.
The original and supplemental reports must all be retained; 12 process games total per artifact.
This affects process coverage only; BenchmarkRunner already plays both sides of every map.
The exact supplemental protocol is `build/analysis/quick-income-v1/missing-sides-protocol.json`.

Supplemental results: **0/4 problems for each artifact**, matching both original source and
executable hashes. Candidate supplement: **330 turns**, max first/later
**728.057/42.282 ms**.
Restored-default supplement: **308 turns**, max first/later
**654.465/42.468 ms**.
Combined: **0/12 problems each**, all **eight distinct map/side combinations** covered;
candidate **913 turns**, restored default **947 turns**.
No previously failing observation was replaced. The scheduling limitation is recorded in PLAN.
Final Player remains the same verified default-off SHA above.
