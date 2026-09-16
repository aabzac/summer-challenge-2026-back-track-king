# Simple heuristic policy v1

Independent `btk.bot.current.HeuristicBot`, selected by `current?USE_ROLLOUT_POLICY=0,USE_HEURISTIC_POLICY=1`.
Since v06 promotion, add `USE_ROLLOUT_POLICY=0` to every historical heuristic recipe below;
restore the selected submission with `submit -PsubmitVersion=v06`. This document records a
starting policy for our bot, not a reconstruction of the boss or a promoted submission.

## Decisions

1. Sabotage from the observed board: enemy tracks plus their active-path exposure, minus our
   exposure with a larger penalty, divided by remaining hits. Prefer retaining a useful target.
   Town/inked regions and targets that cannot be inked before the turn limit are excluded.
2. Build toward desired connections: core cheapest paths, with an optional opponent-avoiding
   alternative. Rank `(W_OWN * (new cells + our tracks) - W_OPP * enemy tracks) / (paint + 1)`.
   Prefer continuing the previous useful connection; spend affordable paint in town regions
   first, then in path order. Reconsider with remaining paint, at most three passes.
3. Never build in the selected sabotage region. Avoid unstable free cells outside town regions.
   Safe adjacent idle extension spends otherwise unused paint when enabled.

No completed-route simulation, whole-position evaluator, opponent reply prediction or random
noise. The sole hypothetical safety check is a ready-to-ink target: use core ink/connectivity
and income to reject a closure that immediately loses or draws. This does not predict the
opponent's simultaneous action. Active-path exposure is a cheap feature, not exact lost income;
detour cost and the value of shared-network shortcuts are **not** modelled in v1.

## Opt-in detour priority

`current?USE_HEURISTIC_POLICY=1,USE_HEURISTIC_DETOUR=1` adds recovery-paint cost to sabotage
priority. The flag defaults to false; construction, idle extension and closing safety are unchanged.

- Shortlist the four positive targets preferred by the old formula, and four unique undirected
  active town connections by absolute enemy-minus-own exposure. Reverse desired pairs do not
  cause duplicate recovery costs.
- For each shortlisted connection crossing a shortlisted region, ask core for the cheapest path
  with **all** cells of that region blocked. The current connection already exists, so its baseline
  paint cost is zero. Existing alternative rails remain free regardless of ownership.
- Cap recovery cost at 12 paint; use the same cap if recovery is impossible. Multiply it by the
  connection's signed ownership imbalance `(enemy - HEURISTIC_OWN_PENALTY * own)/(enemy + own)`.
  Add the strongest enemy burden minus strongest own burden, avoiding a sum of overlapping
  restoration projects. Apply `HEURISTIC_DETOUR_WEIGHT=1` before hits/stickiness.

At most `HEURISTIC_DETOUR_REGIONS * HEURISTIC_DETOUR_PAIRS = 16` added path queries per turn,
**separate from** the 64 construction queries. Interrupted calculations discard all partial
detour adjustments. `lastDetourQueries` / `lastDetourRegions` expose work; MESSAGE/DEBUG show
the query count. OFF does not allocate detour buffers or perform these queries.

This is a known-board connectivity/paint feature, not simulated future income or an opponent
policy model. It ignores unfinished connections, unshortlisted regions/pairs and future route
ownership. A free alternate connection can change which player's rails score, so zero recovery
paint does not imply zero score impact. An impossible route is assigned a bounded heuristic
value, not an estimate of an actual attainable repair. The original closing safety still applies.

Tests include free/expensive/impossible bypasses, own collateral, deduplication, query caps,
input isolation, fixed-work reproducibility and merged parity on active routes. A pre-edit v1
action-history digest guards OFF choices; its neutral-rail self-play has limited sabotage coverage.

## Work and reproducibility

At most `HEURISTIC_MAX_ROUTES=64` cheapest-path queries across the whole turn, in stable order.
This cap does not include core connection refreshes or closing safety checks, which have their
own bounded board/region loops. No per-candidate collections; primitive buffers are reused.
`SEED` controls stable tie-breaking, independent of the map seed; `NOISE` is not consumed.
Target and route memory depend on the preceding observations, not just the current board.

`HEURISTIC_EMERGENCY_STOP=true` retains the existing runner budget/margin as an emergency
cutoff. A cutoff can change the returned action, so arena play is not unconditionally
deterministic. For local debugging set it to false: the same initial state, observations,
player id, parameters and code then produce the same game actions regardless of clock budget.
Recorded-action replay remains separate from rerunning a bot's decisions.

`MESSAGE=1` displays path-query count, elapsed milliseconds and `EMERGENCY`; `DEBUG=1` prints
the same diagnostic to stderr. The bot exposes per-instance emergency counts to local callers.
Elapsed-time messages are naturally not deterministic. Ordinary benchmark JSON does not yet
aggregate emergency counts, and local move timing is not a process-deadline guarantee.

## Commands

```powershell
.\gradlew.bat run "-Pbot0=current?USE_HEURISTIC_POLICY=1" "-Pbot1=v05" "-Pseed=24010"
.\gradlew.bat run "-Pbot0=current?USE_HEURISTIC_POLICY=1,HEURISTIC_EMERGENCY_STOP=0,MESSAGE=1" "-Pbot1=v05" "-Pseed=24010"
.\gradlew.bat submit "-PsubmitVersion=current?USE_HEURISTIC_POLICY=1"
.\gradlew.bat procCheck "-Pbot=v04" "-Pgames=4"
.\gradlew.bat submit "-PsubmitVersion=v05"
```

The submission merger includes only the selected policy, and its disposable warmup uses that
same policy without touching live target memory. Do not disable the emergency guard for an
arena submission. Full resolved parameters are saved in benchmark and submission manifests;
only the fields actually referenced by this policy affect it. Existing eval/search weights do
not acquire a new meaning here; incompatible experimental search flags are rejected.

## Fixed experiment

One 300-game screen vs v05 (150 paired generated maps, seeds 22000..22149), followed by one
200-game style check vs pressure-v1 (100 paired maps, seeds 22000..22099). Two workers, nominal
CG 486/36-ms search budgets, default parameters except the policy selector. No tuning on the
results, sample extension or reserved confirmation games. Pressure results are not evidence of
improvement over v05 without a matched control. See BOTS and DEVLOG for the recorded outcome.

Detour follow-up used 300 OFF-vs-v05, then 300 ON-vs-v05 games on the same 150 paired maps,
seeds 25000..25149, plus 200 ON-vs-pressure games on the first 100 maps. Direct ON/OFF self-play
was replaced **before** strength measurement: neutral-rail mirrors in testing did not exercise
sabotage. All weights were fixed before the first run; no sample extension or confirmation games.

Outcome: **45.0% ON vs 46.7% OFF** against v05, with **31 added / 36 lost wins** in matched
map/side games. ON's map-grouped 95% CI **33.9-56.1%** fails the promotion gate. Against pressure
ON scored **55.0%**, CI **41.4-68.6%**, with no new matched OFF control. The feature stays off;
construction and default submission remain unchanged. These results do not establish a benefit
or prove that recovery-cost features are generally harmful. See DEVLOG for detailed evidence.
