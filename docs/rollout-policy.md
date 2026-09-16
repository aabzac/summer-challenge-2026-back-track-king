# Independent rollout policy v1

Experimental command: `current?USE_ROLLOUT_POLICY=1`. The default and selected submission remain
v05. This is a separate `RolloutBot`, not another branch in the existing EvalBot. See DEVLOG for
the predeclared experiment, artifacts and outcome.

Fixed screen: **56.67% vs v05** (170-130, 300 games), map-grouped 95% CI **45.58-67.76%**;
promotion gate not passed. Separate 200-game checks: **68.5% vs v03**, **74.5% vs pressure-v1**.
The candidate stays default-off. Its strict process sample passed four games, but in-process
outliers remain and the restored-v05 process sample failed all four games. DEVLOG preserves
timing evidence and the reserve-pool overlap correction. Final Player is v05 without a fresh
process-readiness pass.

## Decision process

1. Prepare a legal sabotage fallback. Estimate current exposed income and unfinished track
   ownership, shortlist four regions, then evaluate their destruction using exact core income.
   A one-hit map closure is accepted when its resulting score wins and rejected otherwise.
2. Interleave route proposals for both players. Use ordinary cheapest routes, routes avoiding
   enemy rails, and routes avoiding the predicted threatened region. Pathing and tie-breaking
   come from `btk.core.Pathing`. Consider building from either end. Retain up to six distinct
   first prefixes per player, ranked by complete-route income and one threatened-region scenario.
3. Form affordable first moves, spending remaining paint on the retained routes in priority order.
   Also consider building nothing this turn. Pair each move with its selected sabotage or WAIT.
   Deduplicate complete first placement sets. Route proposal ordering is only a shortlist heuristic.
4. For each first action, simulate eight simultaneous turns against up to two opponent route
   priorities. Both players pay actual terrain costs, can collide on neutral rails, and select
   future sabotage from their simulated observation plus their own intended construction.
   Opponent target selection does not inspect our simultaneous placements. All state transitions,
   paths, scoring and termination use the core. No recorded future actions enter the policy.
5. Compare earned score plus up to three turns of terminal income; actual terminal wins/losses
   have a larger utility. Combine scenario mean and worst value equally. Only a complete set of
   scenarios may replace the selected answer. Emit that first move and replan on the next observation.

This changes the policy's main objective from projected value of a completed route to points
earned during affordable multi-turn construction with both players' disruption. It is a small
portfolio search, not exhaustive minimax or a validated model of an arena opponent.

## Work bounds and limitations

- At most 128 route queries overall, six retained routes per player, seven construction choices
  (including WAIT), two sabotage choices and two opponent scenarios, eight turns each: at most
  224 resolved search turns. Earlier terminal positions reduce this count.
- Route generation reserves `min(ROLLOUT_RESERVE_MS, budgetMs / 2)` for subsequent work: with
  the nominal 36-ms budget and default reserve=20, that leaves 18 ms. Cooperative clock checks
  can stop enumeration or a rollout, so fixed maps do not imply identical timed choices.
- The root portfolios stay fixed inside a rollout. A destroyed route is abandoned and another
  retained route receives the paint; new paths are generated on the next real turn. The model
  cannot invent an unforeseen detour inside those eight simulated turns.
- Both opponent scenarios share the same sabotage chooser and differ only in construction
  priority. If only one opponent route survives proposal filtering, scenario coverage shrinks.
  The four-region sabotage shortlist can miss a valuable region with low estimated exposure.
- The terminal income tail assumes unchanged income. Route ranking approximates build turns
  by total paint, while the rollout uses actual per-turn packing. Neither is an unbiased forecast.
- Root-prefix deduplication keeps the better route-ranking continuation; the same actual first
  action can have other future continuations that are not separately evaluated.
- When time expires, preserve the best fully compared action or the independent initial fallback.
  If the clock expires before refreshing sabotage after construction, the fallback target was
  selected on the pre-build board. This remains legal but can have a different strategic value.

All policy weights and bounds are fields in Params: `ROLLOUT_*`, plus `SEED`, `NOISE`,
`USE_DISRUPT` and `UNSTABLE_COST`. Other construction experiments cannot be combined with this
selector. Historical bot packages and parameter snapshots are unchanged.

## Core and artifact verification

`btk.core.SearchState` retains state and path buffers across simulation nodes. It calls the
existing canonical `Pathing.shortestPath` for each directed connection and delegates placement,
disruption, scoring and termination to `Rules`. Search/evaluation nodes allocate no objects.
Existing Pathing, Rules and GameState implementations remain unchanged, preserving archived
experiment identities. Differential tests compare search transitions with `Rules.resolve`.

Build only the selected policy:

```powershell
.\gradlew.bat submit "-PsubmitVersion=current?USE_ROLLOUT_POLICY=1"
.\gradlew.bat procCheck "-Pbot=v04" "-Pgames=4" "-PprocessDiagnostics=true" "-Pout=build/analysis/rollout-new-process.json"
```

`rolloutCheck` audits all 110 saved public states in the existing reply-study capture for
legality, input isolation, work coverage and serial 36-ms cost. It refuses to overwrite its report.
The original audit and source/parameter/process evidence are under `build/analysis/rollout-v1/`.
This audit is not a strength benchmark, and the four-game process sample does not guarantee
timing on CodinGame's host. Restore the selected submission using `submit -PsubmitVersion=v05`.
