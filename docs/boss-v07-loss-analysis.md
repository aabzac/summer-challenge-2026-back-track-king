# Latest 20 Boss 4 games attributed to v07

The fixed newest sample contains **6 wins and 14 losses**. All 20 canonical replays reproduce
exactly through `btk.core` and now have visualizer exports. The most concrete new tactical idea
is a **two/three-cell network shortcut**: sometimes every incomplete subset earns nothing,
but completing the small group redirects several active connections at once. The current
singleton network generator cannot discover that interaction by evaluating one cell at a time.
Ordinary town-pair route proposals may still contain a particular shortcut; this study does
not establish their coverage for every example.

Open the local collection while the repository replay server runs:
[20-game viewer](http://127.0.0.1:7799/replays/rendered/cg/boss-v07-20.html).
It has win/loss filters, all 20 renders, and four example buttons that jump to the relevant turn.
All diagnostic turn numbers below are **zero-based**; the visualizer displays the board after
the selected turn, so the button for t26 sets its slider to 27.

## Completed experiments

Two independent default-off candidates were tested against selected v07 on fresh paired maps:

- **Route dependencies:** 133 wins, 160 losses, seven draws / 300 = **45.50%**. Stop at screen.
- **Network chords:** 163-137 / 300 = **54.33%**, followed by a separate 544-456 / 1000 =
  **54.40%**. The required fresh-confirmation threshold is 55%; the strength gate is not passed.

No stages were pooled, retuned or retried. Direct failures skip common-opponent and process
checks. All 1600 games / 800 distinct fresh paired maps are retained, with zero bot errors.
Both flags stay OFF and v07 remains selected. The 26 original submission files are unchanged.
286 Java tests pass; 292 arena and 50 boss replays reproduce exactly. The small observed chord
edge is not a promotion decision or a new process-readiness claim.

[Dependency protocol](route-dependencies-v1.md), [chord protocol](network-chords-v1.md),
[final identity and outcome audit](evidence/v07-followups-final-audit.json), and
[a separate protected-opening concept from fresh arena games](arena-opening-survey-v1.md).

## Sample and attribution

The local boss directory contained exactly 50 canonical JSON files. The first 30 are the
previous v05/v06 sample; numeric game-id order selects the newest 20, from **904553513** to
**904555504**, captured locally on 18 September 2026 around 00:29–00:38. The user identifies
these as v07. The raw replays identify aabzac as P0 and Boss 4 as P1, but do not contain a
submission source hash. This is therefore a user-attributed cohort, not independent verification
of the deployed artifact. Raw input SHA-256 values and the exact membership are retained in
[the compact summary](evidence/boss-v07-summary.json).

There are no server failure turns, invalid outputs, or skipped actions in this sample.
Six wins in these different, unpaired maps is descriptive only: it is neither a promotion
gate nor a valid comparison with the older v05/v06 boss cohorts.

## Existing routes already contribute to a new route's value

The proposed 1-to-2 plus 2-to-3 interaction is already included. `RolloutBot.propose` paints
the candidate on a private board, refreshes every desired directed pair, and calls the core's
whole-network `Rules.incomePerTurn`. Its gain is the change in our income minus opponent income,
not merely the income of the town pair used to generate the proposal. Simulated turns also
refresh the complete network. Existing rails can belong to either player; each player receives
the income of their own cells on each active scoring path.

Thus 1-to-3 contributes when it is a desired connection and its canonical shortest existing
rail path uses those cells. Arbitrary town combinations are not automatically scoring pairs,
and shortest means among existing connected rails, not the hypothetical shortest route across
empty terrain. The remaining limitation is which construction proposals enter the bounded
search. Correctly scoring a combined network does not ensure that its best two-/three-cell
change is ever proposed.

## What the defeats show

Across the 14 losses, our accepted construction used **1402 paint versus 1726** for the boss.
Our **247 of 1143 accepted cells (21.6%)** never appeared in an active scoring connection;
the boss's corresponding count is **144 of 1336 (10.8%)**. Across the six wins, these counts
are much closer: 87/545 (16.0%) versus 85/552 (15.4%). This supports examining project
completion, route persistence after destruction, and efficient use of remaining paint.
It does not by itself identify why an individual cell failed to earn.

The first 20 turns show a smaller paint gap: 797 versus 835 across the losses. Much of the
full-game gap emerges later, as our bot stops construction or has fewer usable projects.
There is no evidence here of an output-format or rejected-placement bug.

The clearest fragmented-construction example is **904554907**, lost **904:4151**. Our 50/113
accepted cells never score; only 8/142 boss cells do not. At t21 the actual income is **0:134**.
Several of our early actions start geographically different projects, while the boss completes
a connected network. This replay is useful for testing the separate omitted-bridge dependency
bug found by the strategy audit, but this study does not claim that bug alone caused its result.

## A genuinely different move: complete a small network shortcut

Boss messages include 146 `steal`, 13 `fortify`, and one `block` turn. These are the opponent's
labels rather than proof of its internal objective. Applying only the boss's accepted placement
cells to the observed board, with neither player's disruption nor our simultaneous construction,
gives a positive immediate net-income change on 88 of the 146 `steal` turns, zero on 54, and a
negative change on four. The 54 zero turns can be preparatory investments; they are not failures.

Two exact subset probes demonstrate an interaction that singleton evaluation misses:

- **904554568, t26**, a loss of 3440:4760. The boss places **(22,6), (21,6), (20,6)** for three
  paint. Every single cell and every two-cell subset leave income at **132:126**. The complete
  three-cell group changes it to **64:186**, a **+128** change in the boss's income difference
  per turn. Three already active directed connections change canonical path; no new desired
  pair activates. This is network rerouting, rather than merely finishing a missing town pair.
- **904555424, t28**, one of our wins (2934:2299). The boss places **(18,10), (18,9)**, costing
  two plus one paint. Individually both have zero income effect; together the board changes
  from **169:44 to 125:78**, a **+78** change for the boss. Four active directed paths change.
  The fact we still won is a useful reminder that a strong local move does not establish a
  stronger full-game policy.

The natural bounded follow-up is to propose connected affordable two/three-cell shortcuts,
use the core to score their combined effect, and compare them only after the ordinary search
has completed. This extends v07's successful late network search without treating an unfinished
shortcut as a worthless set of independent cells. It requires its own fixed strength test;
the observed +128/+78 values are immediate diagnostic values, not predicted match improvement.

Complete subset results are in [boss-v07-steal-subsets.json](evidence/boss-v07-steal-subsets.json).
The 904554568 t26 position is also exported as a compact core-derived test fixture in
`src/test/resources/btk/bot/network-chord-boss-position.json`.

The implemented default-off chord candidate was checked on 146 fixed public states with
ample time. It changes 15 decisions across 11 games, at turns 5--40. In all 15, v07 already
chooses construction: ten singleton network actions and five ordinary three-cell actions.
Nine new choices extend the old singleton. This mechanism primarily changes construction
quality, rather than merely filling idle turns. All actions are legal and preserve the full
input state; disabled current agrees with frozen v07 in all 146 states. These are mechanism
checks, not strength results; [the fixed experiment](network-chords-v1.md) evaluates strength.

At the exact three-cell boss example above, the bounded candidate still chooses v07's old
singleton action: it evaluates 24 of 96 eligible chords and keeps two roots for rollout.
At the two-cell example, it selects a different three-cell group, (16,9), (17,9), (18,9).
The implementation therefore does not reproduce both observed boss moves automatically.
Full diagnostics are in [the public-state probe](evidence/v07-candidates-public-state-probe.json).

## A separate problem: a useful disruption is repeatedly postponed

In **904554072**, lost **1928:9013**, income settles at **18:107** after t36 and both players
then keep waiting. This is **not** a network wholly protected by town regions. A core probe
removing every legally disruptible region leaves 0:0 income in every game in the sample;
none of the 20 initial maps has a desired pair connectable through town regions alone.
This falsifies the initial protected-network explanation, not the usefulness of claiming
individual protected cells near towns.

At the exact public state before t37, scores are 794:2272. Region **29** needs three more
disruptions. Removing it changes income from **18:107 to 1:1**, and the game **does not end**.
The frozen v07 policy, run with ample time, nevertheless reproduces **WAIT** after all ordinary
comparisons complete. Its target selector does nominate region29. The explicit eight-turn
trace for the **first opponent reply scenario** scores attacking now at **-2044**, against
**-2012** for waiting first; the saved aggregate selected-WAIT value is also -2012. The
attack aggregate across both replies was not separately saved. Its modeled continuations
prefer postponement. This is not a missing target, insufficient search time, or a
terminal-loss veto at the observed state.

A simpler no-construction continuation would favor attacking immediately, but it omits the
construction modeled by the actual search and cannot replace it as ground truth. The diagnosis
is specifically a mismatch worth tracing in the rollout continuation, not proof that a blanket
"never wait when behind" rule is correct. We were already 1478 points behind: preventing further
loss of income would not by itself turn this game into a win.

The opponent model exposes a more specific next hypothesis. Both reply scenarios call
`fillBuild`; neither explicitly represents the opponent choosing no construction. In this
position our own route portfolio is empty, while both opponent replies run their full eight
turns. Both recorded bots wait from t37 through t97 (61 turns). We finally hit region29
on t98 and t99, only two of the required three hits; the boss waits through t98 and builds
on t99. Thus the difference from the saved no-build
control comes from modeled opponent construction and the resulting sabotage responses,
rather than our own unfinished investment. The no-build control ends with score gaps -1656
(attack now) and -1745 (wait first), favoring immediate disruption by 89; the modeled first
reply reverses the preference by 32. This is an opponent-model mismatch consistent with
repeated postponement, not proof that adding an opponent-WAIT scenario improves strength.
The own-WAIT branch also assumes greedy sabotage from the next simulated turn; the next real
decision can choose WAIT again. Future work should distinguish this uncommitted continuation
from the missing opponent no-build scenario rather than changing both in one experiment.

The separate `USE_ROLLOUT_ROUTE_DEPENDENCIES=1` candidate was checked on this same position and
still produces WAIT and the same two rollout values. That particular anomaly is therefore not
resolved by the omitted-bridge fix. Frozen-v07 and candidate evidence are retained separately:
[v07 trace](evidence/boss-v07-stalled-904554072-t37-rollout.json),
[dependency candidate trace](evidence/boss-v07-stalled-904554072-t37-dependencies.json).

One isolated next experiment is **WAIT without promised future sabotage**. Restrict the
first prototype to an empty own route portfolio and a first action with no construction.
For that own no-sabotage branch alone, continue without own sabotage throughout the existing
eight-turn horizon; leave the attacking branch, opponent replies, horizon and aggregation
unchanged. This changes the comparison of alternatives, not the legality of waiting or an
obligation to attack in the real game. It has not been implemented or benchmarked here.

Before strength games, save both full reply values and target traces at t37, then inspect
eight successive real decisions against a fixed WAIT opponent: changing only the first
decision would not establish that the three-hit destruction actually completes. Include
cases with construction, beneficial waiting and losing terminal closure. The principal
risk is excluding legitimate delayed attacks from that branch. It is not a general fix for
limited-horizon planning.

Simply replacing the second construction reply with a passive reply is not yet a demonstrated
fix. Combining the saved first active scenario with the saved no-build eight-turn control
under the existing `0.5 * mean + 0.5 * worst` would give attack **-1947** and WAIT **-1945.25**:
WAIT still wins by 1.75. This is arithmetic on saved diagnostic values, not a candidate run
or a statement about the previously unsaved second active reply.

## Defense is not the strongest new evidence

The 13 `fortify` labels usually make no immediate income change. The existing active-route
threat probe finds one clear fortify turn improving a threatened bypass: **904554907 t45**
reduces the paint cost of avoiding region37 from six to four on five exposed town pairs.
Most other fortify examples are outside that narrowly defined probe or show no improvement.
The old defense experiment already covered related ideas, so these observations do not justify
presenting defensive bypass construction as a newly discovered dominant tactic.

## Reproduction and retained evidence

All scoring, route recomputation, action expansion, and hypothetical ink changes use `btk.core`.
No game rules were reimplemented by the diagnostic code. The standalone sources live under
`tools/diagnostics/boss-v07`; they compile against the repository's existing classes and Gson,
without changing the strategy, core, or normal Gradle tasks.

- `CgLatestBossStudy`: exact expected-count/latest-range sample selection, validation,
  existing player/defense diagnostics, build-only branches, and the 20 visualizer exports.
- `CgTownRegionProbe`: all non-town regions inked through the core; checks the rejected
  fully protected-route hypothesis.
- `CgStalledSabotageProbe`: exact public state, exhaustive one/two-region ink branches,
  ample-time policy choice, private diagnostic rollout values, and explicit no-build controls.
  Optional fourth argument selects a bot spec; legacy `v07AmpleAction`/`v07Debug` JSON keys are
  retained, while `botSpec` identifies the candidate in newer reports.
- `CgBossBuildSubsets`: all nonempty subsets of accepted boss construction for the selected
  examples, excluding both simultaneous disruptions and opponent placements.
- `CgStateExport`: validated core-derived public-state fixtures.
- `build_report.py`: compact machine-readable summary and the browsable 20-game collection.

The full raw-turn derived report is [boss-v07-latest20.json](evidence/boss-v07-latest20.json);
the rejected hypothesis is preserved in [boss-v07-town-regions.json](evidence/boss-v07-town-regions.json).
Reports refuse to overwrite existing evidence. The gallery itself is regenerable and belongs
under the ignored replay-output directory. The probes are retrospective: they do not predict
unknown simultaneous actions, their observations are correlated, and their single-turn deltas
must not be added up as recoverable final score or converted into a claimed win rate.
