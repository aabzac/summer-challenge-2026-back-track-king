# Fixed small boss screen: network chords versus v07

Date: 2026-09-18. This protocol proposes a separate boss-specific screen; no games
were run for this document. The active [practical-300-1000-v2 promotion
gate](benchmarking.md) is unchanged. A boss result does not replace its direct and
matched common-opponent stages, engine fidelity, errors or exact-artifact checks.

## Recommendation

Use a fresh control from the same boss and league. **40 candidate games plus 40 v07
games** is a useful fixed default if the question is whether there is a large
improvement, approximately 30% to 60% outright wins. Under independent fresh-map
sampling, the exact one-sided comparison has about **79.8% power** for that hypothetical
effect. It has only **46.8% power** for 30% to 50%; failing to reject would not show
that the candidate has no useful advantage.

For a cheaper exploratory screen, **20+20** is reasonable, with an explicit expectation
of substantial uncertainty: power is **46.3%** for 30% to 60%, and **24.5%** for 30% to
50%. **30+30** is a middle option, with **66.5%** and **36.0%** power respectively.
Choose exactly one size before the first game. These are alternative fixed designs,
not a plan to start with 20, inspect the result, then extend to 30 or 40.

If the API can reproduce exactly the same map and player seat for each build, prefer
matched map pairs and use the paired test described below. Forty pairs still means
40 games per build, 80 executions. Its power depends on how correlated the two builds'
outcomes are; the independent-game figures above must not be presented as matched
design power.

## The old 6/20 is an estimate, not a known 30% baseline

v07 won six of the last 20 boss games. The two-sided exact 95% Clopper-Pearson interval
for its win probability is **11.89% to 54.28%**, under independent, comparable games.
The sample does not establish a fixed true rate of 30%.

For example, a candidate winning **10/20** has a one-sided binomial p-value of
**0.04796** against a hypothetical *known* 30% rate. Compared to the actual historical
**6/20** with a one-sided Fisher exact test, its p-value is **0.16661**. Calling the
first result evidence that it beats v07 would ignore uncertainty in the old control.

Historical comparison also needs the same boss implementation, league rules, map/seat
distribution and comparable execution conditions. The candidate was developed partly
from these historical replays. They are useful for mechanism analysis, but a fresh
contemporaneous control gives a cleaner evaluation of the resulting policy.

Confidence intervals describe sampling uncertainty under their assumptions. They do
not give a guarantee of passing a later stage, and do not repair changed bosses,
biased map selection, repeated maps or simulator/opponent mismatch.

## Exact thresholds at one-sided alpha = 0.05

The primary outcome in this document is an **outright win against the boss**. A draw
is a non-win for this binary test; report W/L/D and the half-win score separately.
This differs deliberately from the active local strength gate, where draws count half.
The comparison direction is fixed in advance: candidate better than control.

If 30% really were a known population rate, the smallest passing candidate counts are:

- **20 games: 10 wins** (50.0%); exact upper-tail p = 0.047962.
- **30 games: 14 wins** (46.7%); p = 0.040053.
- **40 games: 18 wins** (45.0%); p = 0.031951.
- **60 games: 25 wins** (41.7%); p = 0.036238.

These test only the restricted hypothesis `p_candidate <= 0.30`; they are not valid
thresholds for declaring improvement over an uncertain v07 rate.

Using the historical **six wins out of 20** as the control in one-sided Fisher:

- **20 candidate games: 13 wins** (65.0%); p = 0.028080. Twelve gives 0.055485.
- **30 candidate games: 18 wins** (60.0%); p = 0.035891. Seventeen gives 0.058168.
- **40 candidate games: 23 wins** (57.5%); p = 0.040520. Twenty-two gives 0.059057.
- **60 candidate games: 33 wins** (55.0%); p = 0.045688. Thirty-two gives 0.059447.

For a **fresh independent control with the same n**, the threshold depends on its
observed count. Examples show why a single candidate percentage cannot be fixed without
also specifying the control outcome:

- **20+20:** control 4, 6 or 8 wins requires candidate **10, 13 or 15** wins.
- **30+30:** control 6, 9 or 12 wins requires candidate **13, 17 or 20** wins.
- **40+40:** control 8, 12 or 16 wins requires candidate **16, 21 or 25** wins.
- **60+60:** control 12, 18 or 24 wins requires candidate **22, 28 or 34** wins.

All thresholds are calculated by enumerating the exact distributions, without a
normal approximation. The accompanying evidence includes the p-value at each boundary
and at one fewer candidate win.

## Power and uncertainty

The following are hypothetical design calculations, not estimates of achieved power.
Within each line, candidate true rates are **40%, 50%, 60%, 70%**, in that order.
The fresh-control calculations assume its true rate is 30%; that assumption is used
to explore a design, not asserted as a fact about the boss.

For the restricted known-30% binomial test, rejection probabilities are:

- n=20: **24.5%, 58.8%, 87.2%, 98.3%**.
- n=30: **28.5%, 70.8%, 95.2%, 99.8%**.
- n=40: **31.1%, 78.5%, 98.1%, 100.0%** rounded.
- n=60: **44.4%, 92.2%, 99.9%, 100.0%** rounded.

For comparison with the **already observed historical 6/20**, conditional on those
six old wins remaining fixed, rejection probabilities over future candidate outcomes
are:

- n=20: **2.1%, 13.2%, 41.6%, 77.2%**.
- n=30: **2.1%, 18.1%, 57.8%, 91.6%**.
- n=40: **1.9%, 21.5%, 68.9%, 96.8%**.
- n=60: **1.3%, 25.9%, 82.2%, 99.5%**.

These are *conditional* operating probabilities, not the power of a new two-arm trial.
The small old control remains a bottleneck even if many candidate games are added.
Discrete thresholds explain why these values need not increase monotonically for
every alternative.

For fresh **independent n+n** games with both outcome counts random, Fisher power is:

- **20+20:** **9.9%, 24.5%, 46.3%, 71.3%**.
- **30+30:** **13.2%, 36.0%, 66.5%, 89.6%**.
- **40+40:** **16.8%, 46.8%, 79.8%, 96.4%**.
- **60+60:** **25.2%, 68.5%, 94.8%, 99.8%**.

The test is conservative because counts are discrete. At equal true rates of 30%,
actual rejection probabilities for these four sizes are approximately **2.65%, 2.80%,
3.06%, 3.20%**, within the stated 5% test level under the sampling assumptions.

Small-sample estimates also remain broad after apparently good results. Exact
two-sided 95% intervals include:

- **12/20 (60%)**: **36.05% to 80.88%**.
- **18/30 (60%)**: **40.60% to 77.34%**.
- **24/40 (60%)**: **43.33% to 75.14%**.
- **36/60 (60%)**: **46.54% to 72.44%**.

Do not decide the difference between builds by whether their separate intervals
overlap. Use the predeclared comparison test; intervals here communicate uncertainty
in each rate.

## Same maps require a paired comparison

If both builds face the same boss on exactly the same initial map and in the same seat,
record one pair of binary outcomes per independent map. Let `b` be pairs where only
the candidate wins and `c` pairs where only v07 wins. Concordant pairs (both win or
both do not win) do not contribute to the exact McNemar/sign test:

`p = P[Binomial(b + c, 0.5) >= b]`.

At alpha 0.05, examples are **5/5**, **9/10**, **12/15**, **15/20**, **20/30** or
**26/40** candidate-favouring discordant pairs. These denominators count discordances,
not total map pairs. Marginal counts such as 21/40 versus 12/40 are insufficient to
compute this paired p-value without knowing which maps each build won.

Matched power depends on the joint result distribution. For candidate/control true
rates 60%/30%, let `q` be the probability both win the same map. At 40 pairs:

- q=0.00: power **60.2%**.
- q=0.18, the within-pair independent-outcome illustration: **80.8%**.
- q=0.30, every control win is also a candidate win: **99.7%**.

These are illustrations, not estimates of actual correlation. Reusing a seed only
creates a matched pair if the initial map and seat really agree. If each map is played
in both seats, the resulting outcomes form a larger map cluster; do not count the
two seats as independent matched maps for the simple formulas above. For this small
protocol, use one prebalanced seat per unique map pair.

## Execution and decision record

Before starting, freeze one candidate artifact and v07 control artifact with source
and executable identities, the boss/league identifiers, the sample size, the outcome
definition, the independent or matched design and the exact test. Keep the network
chord candidate's other experiment flags off. Interleave the builds in a fixed,
balanced AB/BA order so time drift does not align with one build; balance player seats
where the interface permits. Record limitations when it does not.

For independent Fisher sampling, use distinct fresh maps for all games and retain
their hashes. For matched sampling, preselect unique fresh map pairs, give both builds
the same map and seat, and record the two outcomes together. Do not reuse the 20
diagnosis maps as the new evaluation set. Same boss/league labels alone do not prove
unchanged boss behavior; record available agent/build identifiers and collection time.

Complete the fixed sample regardless of the running W/L/D. Do not tune, switch the
candidate, extend a borderline result, stop early for success, pool the old 6/20 into
the fresh control, or repeat a completed stage until it passes. Bot timeouts and other
completed game failures stay in the record as non-wins. Distinguish transport failures
that never created a game from actual game failures; retain attempt logs and account
for every scheduled slot. If the boss changes or the run cannot be completed, report
the stage as interrupted/incomparable instead of replacing inconvenient games.

At completion, report W/L/D, the effect estimate, the exact one-sided p-value and
descriptive intervals. A rejected null is evidence of boss-specific improvement under
this fixed design. Otherwise report **inconclusive for improvement**; a small sample
does not establish equality or inferiority. A practical positive but non-significant
score difference may motivate a separate future experiment, but it does not become a
pass by extending this one. Testing many candidates introduces another selection
issue; results from repeated candidate screens should be labelled exploratory unless
a multiplicity/confirmation plan was fixed before those screens.

Thousands of local games can reduce sampling uncertainty for the tested local matchup.
They cannot by themselves establish the current external boss matchup or remove an
opponent-model mismatch. Repeating a small fixed set of maps many times also does not
create thousands of independent map observations. The small external screen answers
a different, narrower question and supplements the existing local gate.

## Reproducible calculations

The standard-library [calculation script](../tools/diagnostics/boss_evaluation_protocol.py)
enumerates all outcomes; it runs no matches and calls no API. Its
[JSON output](evidence/boss-evaluation-protocol.json) retains thresholds, exact-tail
p-values, all power scenarios and intervals. Reproduce from the repository root:

```powershell
python tools/diagnostics/boss_evaluation_protocol.py --check docs/evidence/boss-evaluation-protocol.json
```

The one-sample test sums the binomial upper tail. For Fisher, with candidate wins `a`
of `nA`, control wins `b` of `nB` and total wins `t=a+b`, it sums
`choose(nA,x) * choose(nB,t-x) / choose(nA+nB,t)` for all feasible `x >= a`.
Fisher and known-30% rejection boundaries use exact integer arithmetic. Fresh-control
power sums the two independent binomial probabilities over every rejecting `(a,b)`
combination. Paired power sums over the number and direction of discordances. Exact
Clopper-Pearson endpoints are obtained by inverting binomial tails numerically.

No confidence interval is used as a promise of passing, and none of these calculations
changes the repository's strategy-promotion policy.
