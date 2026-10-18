# Ink40 local strength after an explicit timeout-gate waiver

Declared 2026-10-18 before any strength games, starting at 8764233. The user
explicitly requests: "Давай уберём эту проверку с таймаутами. И хапустим бенч
для 40мс". This supersedes the timing prerequisite for this local strength
experiment. It does not change the recorded timing result or remove the
arena's response limit. Keep the completed ink40 protocol, evidence and
NOT_READY verdict immutable, including its two timeouts50.2949/52.1667 ms.

Candidate A: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true`.
Control B: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40`.
Same pre-fast-path native runtime and full effective parameters as the prior
ink40 preparation; only incremental ink refresh differs. Both first/later
nominal search allowances486/40 ms, fast path/deadline correction OFF.
No production change, new build or new process test is needed.

## Explicit local-only exception

Allow the complete independently reconciled failed timing sample to proceed.
Verify its raw errors are the two recorded timeouts, with no missing replies,
and preserve every report/hash. Keep engine292 exact, ample524 semantic parity,
artifact size/configuration/native-runtime checks and all immutable pins.
The prior audit PASS certifies evidence integrity, not timing readiness.
New synthetic driver checks must accept the explicit waiver while rejecting
incomplete/corrupt inputs and unexplained non-timeout failures.

Record `timeoutGateWaived=true`, `localStrengthOnly=true`, promotion=false.
Native timing is diagnostic and does not gate continuation. Actual game errors
still block a clean strength conclusion. Do not reinterpret or replay the
failed process games. No new live/boss requests or arena upload.

## Fixed comparison

Register all500 paired maps before play. Check shared-core map hashes against
all known played/reserved identities, including all500 fast-path maps, and
against registered seed intervals. The previously proposed ink40 ranges were
not registered or played before this waiver; use them once:

- Block1:300 games /150 maps, seeds301018000..301018149.
- Block2:+200 /100, seeds301118000..301118099.
- Block3:+500 /250, seeds301218000..301218249.

Run unchanged BenchmarkRunner with two workers and fresh bots, both seats on
every map. Record actual per-arm allowances486/40. Use unchanged practical-v4
local arithmetic: win1/draw0.5; stop strictly below50% at pooled300 or500;
no early pass. Survivors finish1,000 and meet the local numerical criterion
only at>=55%. No extensions, tuning, replacement games or retries. Complete
each started block. Do not pool prior36ms,40ms budget or process outcomes.

Freeze runtime, params, map identities, new waiver protocol and driver before
the first game. Preserve earlier published ink40 evidence and all predecessor
artifacts. Independently recount raw scores, seats, maps, budgets, timing,
errors, hashes and stopping. Keep confidence diagnostics separate from the rule.

A numerical local pass would establish only this comparison under native40ms;
known strict-response failures and the separate promotion requirements remain.
No default change or promotion is authorized. Keep selectedv09/default36 and
all experimental flags OFF. Record final result in a new report and archive,
update BOTS/PLAN/DEVLOG, and commit locally without rewriting earlier verdicts.
