# Fast-path premeasurement packaging repair

2026-10-18, before any saved-state timing, guard continuation or new game.
The first focused preflight completed 261 tests: 259 passed, including all
eight new core parity tests. Two failures are retained: the already known
historical exp-income-guard catalog pin and the new guard-artifact size test.
The guard export was 100,008 characters, eight over the 100,000-character cap.

Apply one packaging-only repair: rename the newly introduced private geometry
array identifier `cheapestNeighbors` to `pathNeighbors`. Apply the same token
rename in the exporter restoration expression and test reflection/absence
checks. No algorithm, costs, candidate order, heap/flood behavior, strategy,
guards, budgets, parameters, assertion or limit changes. Keep the main and
supplementary guard recipes exactly as declared.

Preserve the original command/log/exit, test-source pins, complete initial XML
and the four pre-repair source files. Verify the final files equal the originals
after only this replacement, and all other tested sources are unchanged.
Run the same focused suites once against the repaired source, in separately
named output records; do not overwrite the first attempt. The final test
source pins govern subsequent exports and measurement freeze. No measurements
or games have run or will be repeated to repair packaging.

This amendment repairs a source-size failure without relaxing the prospective
correctness, speed, guard, process or strength conditions in the
[original protocol](v09-fast-path-v1.md).
