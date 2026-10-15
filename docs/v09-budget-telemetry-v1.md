# v09 budget and WARN telemetry audit

2026-10-16. User asks whether observed `WARN turn ... took ...ms` values can
justify increasing the current time budget, possibly using a median or allowing
rare overruns. This is a read-only analysis of current code and retained runs.
No strategy, budget, generated submission, live game or process run is changed.

## What the current budget means

`CGRunner` uses a 50-ms later-turn limit and a 14-ms margin: its internal
target is **36 ms for preparation and search together**. `Protocol.readTurn`,
region synchronization, connection calculation and validation consume that
allowance. Search receives `max(5, 36 - floor(preparation milliseconds))`.
The state copy passed to `play` happens after that subtraction. Formatting,
printing and flushing also occur after search, within the runner's measured
interval. The target is cooperative, not a hard interrupt at 36 ms.

The first turn is separate: 1000-ms limit, 986-ms internal allowance, about
500 ms for disposable-instance warmup and nominally 486 ms left before other
preparation costs. The actual warmup duration is subtracted, not assumed to be
exactly 500 ms. In-process benchmarks use nominal 486/36-ms search allowances
without protocol IO; they are not complete-response measurements.

Both retained exact candidate and control submissions have the same constants.
There is no total turn-budget parameter in `Params`; rollout reserves allocate
time between phases and do not change the 50-ms external limit. A future budget
experiment must be a separate exact artifact and leave frozen submissions intact.

## Interpreting `WARN turn 53 took 43ms`

The code prints a warning only when `floor(total milliseconds) > 36`, after
`println` and `flush`. Thus 43 means an internal measured interval of
**[43,44) ms**, already about seven milliseconds beyond the 36-ms target. It is
neither search time alone nor a measured seven milliseconds of server headroom.

The runner starts its clock after `TokenReader.hasNext()`. Initial token/pipe
handling and any delay before that clock are excluded. Conversely, a scheduling
pause after stdout flush but before the final timestamp can make the warning
larger even though the answer has already been emitted. Host receipt and the
server's clock are not observed by this log. Therefore a WARN above 50 ms is
not itself proof of a server timeout, and an observed sub-50 WARN is not a
complete-response deadline certificate.

Warnings are **left-censored observations**: turns with integer total <=36 have
no WARN. A median of warning values describes the warned tail, not the median
turn. An unlogged, retained warning-free turn gives only the bound <37 ms on
this internal interval. If an all-turn percentile falls in the warned portion,
its integer timing bucket can be recovered; otherwise only a bound is available.
This relies on the retained stderr being complete. The audit retains source
provenance and explicitly labels the complete-capture assumption; absence of a
warning cannot independently prove that the server retained every stderr byte.

## Retained measurements

Read all completed games from both boss series, not only the previously selected
losses: **68 candidate games and 80 control games**. The extra twelve control
games have no completed candidate match. They are useful for descriptive timing
but are excluded from the separate matched comparison. The exact prior stage
audits, builds, canonical results and 317 input-file hashes are pinned. No game
or policy is run for this audit. First turns are separated throughout.

- Candidate: **4,811 later turns**, **1,599 WARNs (33.24%)**. Among WARNs only:
  minimum 37, median 38, p90 43, p95 45, p99 49, maximum **98 ms**.
  There are 351 values >40, 77 >45 and **9 >50 ms**, the latter spread over
  **8 of 68 games**. No explicit live failure is recorded.
- Control: **5,717 later turns**, **2,096 WARNs (36.66%)**. Among WARNs only:
  minimum 37, median 38, p90 43, p95 45, p99 49, maximum **106 ms**.
  There are 473 values >40, 102 >45 and **13 >50 ms** in **12 of 80 games**.
  No explicit live failure is recorded.
- No first-turn WARN is retained. There are no duplicate warning copies.
  A warning's own printed turn number is used: 177 candidate warnings and 215
  control warnings occur in the capture one own-output frame later. Attaching
  them blindly to the containing frame would mislabel those turns.

Under complete retention, the all-later-turn p99 integer bucket is 46 ms for
the candidate and 47 ms for control; p99.9 is 53 and 56 ms respectively.
The all-turn median cannot be recovered:
it lies among unlogged values <=36 ms (true internal duration <37 ms). These
are internal-clock buckets, not full server-response percentiles. The number
of >50 WARNs is likewise not a timeout count.

The prior strict **full local response** measurements are more relevant to a
budget decision, though they come from a different host and game sample:

- Exact candidate: eight games, **678 later responses**, p50 **37.02365 ms**,
  p95 **38.1225 ms**, p99 **41.3965 ms**, maximum **46.7275 ms**.
  Nine responses exceed 40 ms, one exceeds 45 ms, none exceeds 50 ms.
- Exact saved v09 control: eight games, **657 later responses**, p50
  **37.468 ms**, p95 **38.692 ms**, p99 **40.4345 ms**, maximum **41.444 ms**.
  Nine responses exceed 40 ms, none exceeds 45 or 50 ms.

Both strict panels have zero recorded timeouts or nominal overruns. Percentiles
use nearest rank, except the conventional midpoint median. `responseMs` is the
stdout reader's arrival timestamp from the start of host input preparation;
the consumer thread's later wake-up is not substituted. These are retained
readiness samples, not new runs at a larger budget.
Each panel has fewer than 1000 later responses, so its nearest-rank p99.9 equals
its maximum; that percentile adds no independent tail evidence here.

## How to choose a larger budget

Do not set the increase to `50 - max(WARN)` or `50 - median(WARN)`. Even a
complete-response maximum of 45 ms would not justify adding five milliseconds
with zero remaining allowance for variation. Increasing search time changes
completed comparisons, actions, RNG consumption and subsequent game states;
the resulting response distribution is not a fixed translation of the old one.
For illustration, simply adding five to the candidate's old local maximum
already gives **51.7275 ms**. This arithmetic is not a prediction of a new run;
it shows why the available sample does not support a five-millisecond safety
claim. Likewise a high live WARN without a recorded server failure does not
establish a tolerance that a new artifact may rely on.

Use the full response interval from input dispatch through receipt of a valid
output line, separated into first/later turns. Report p50 for typical cost,
p95/p99/p99.9 for the tail, maximum, missing/late responses, and the number of
games containing a deadline failure. Keep every outlier. Also record completed
search work and, in a separate prospective strength comparison, score: more
thinking time is only useful if it buys useful decisions.

Rare deadline failures should be evaluated per game, not excused by a low
fraction of turns. As an illustration only, independent 0.1% per-turn failures
over 100 turns imply `1 - 0.999^100 = 9.52%` probability of at least one failure.
Real turns are dependent, and WARN events are not these failures; measure actual
per-game outcomes instead of using this illustration as an estimate.

A sensible first **experimental** target is 38 ms (+2 ms, about 5.6% more
nominal time, margin 12 ms), not an immediate switch to 41 ms (+5 ms). This is
a conservative test point, not a data-derived safe limit or an implemented
change. Freeze one candidate, retain the old 36-ms control, run a predeclared
strict process panel with the unchanged 50-ms response deadline, and keep its
result even if it fails. A pass on a small local sample does not establish the
server's tail behavior. Any new strategy/budget confirmation must follow the
project's prospective strength and boss-cache policy.

The saved rules specify <=50 ms on normal turns and <=1000 ms on the first.
These logs establish no permitted quota of rare server-limit overruns. An
explicit risk-taking experiment could measure the tradeoff, but median WARN
alone cannot select or justify that risk.

Decision: keep the current 36-ms internal target and 14-ms margin. The 38-ms
candidate is proposed, not implemented or tested. Original strength, process
and incomplete boss-series verdicts remain unchanged.

[Compact evidence](evidence/v09-budget-telemetry-v1.json) and the
[retained audit archive](evidence/v09-budget-telemetry-v1-artifacts.zip) contain
the aggregate statistics, per-game warning observations, helper and source pins.
