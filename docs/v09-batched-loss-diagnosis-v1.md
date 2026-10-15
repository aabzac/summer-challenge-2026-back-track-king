# v09 batched refresh: diagnosis of paired boss regressions

Date: 2026-10-16. Offline follow-up requested while the additional boss series
is blocked by the service play limit. No new games, strategy changes or promotion.

## Fixed cohort and interpretation

Use every completed matched pair from the original 40-game boss series and the
28 completed pairs of the additional series. Across these 68 pairs the candidate
wins 19 and v09 wins 17: 12 improvements, **10 regressions**, seven shared wins
and 39 shared losses. This diagnosis includes all ten cases where v09 won and
the candidate lost: seven from v1 and three from v2. The unfinished twelve
candidate slots have no assigned outcomes.

These are selected failures, useful for finding mechanisms but unsuitable for
estimating their prevalence or deciding whether batching is stronger. The
original formal NOT_PASSED and additional INCOMPLETE_TRANSPORT verdicts remain
unchanged. The batch flag remains OFF and v09 remains selected.

The exact captured builds are `current?NOISE=0.1,USE_BATCHED_REFRESH=true` and
`v09?NOISE=0.1`; the missed-deadline correction is OFF. No frozen implementation
was edited. The study fixes the cohort, states and probe rules before model calls.
All 20 canonical replays reproduce exactly through `btk.core`: 761 candidate
turns and 602 control turns. All following turn numbers are **zero-based**, as
in the saved diagnostics. Income and score pairs are always **our bot : boss**.

## Where the games first separate

In all ten pairs our accepted action changes on the same full state before the
boss's action changes. First differences are on turns 1–3; boss differences
start on turns 2–7, after the boards have already separated. Nine first
differences include construction. Only v1-029 initially changes sabotage alone:
identical three placements, no candidate attack versus v09's DISRUPT25.

Six first differences occur during the protected opening on turns 1 or 2.
That branch bypasses ordinary rollout root selection. Its traffic and collision
choices consume the route portfolio already collected under the time budget.
It is therefore incorrect to explain every changed opening by a deeper search.
The configured rollout depth is eight for both versions; batching can increase
completed work, not that horizon. Historical route coverage and RNG state are
not present in the replay logs.

Holding the common first state and the recorded boss action fixed, swapping
only our first differing action produces identical immediate score gains in
nine cases. In v2-010 the candidate gives up four immediate points: v09 first
earns on turn 3, the candidate on turn 6. These one-turn checks do not show that
the opening decision alone caused the eventual loss. Subsequent actions and
boss responses differ.

## Concrete midgame vulnerabilities

In **six of the ten games**, the largest one-turn deterioration in income
margin involves a boss attack on a region already at instability 3. One more
hit can destroy it. This is an observable vulnerability before the hit; it is
not a claim that any alternative construction on that turn would save the game.

- **v1-006, candidate 909153741 / v09 909150731:** first difference turn 1.
  On turn 21 income goes from 201:38 to 54:20, with boss DISRUPT37 and our
  DISRUPT3. Destroying region 37 alone on the pre-turn board gives 54:20.
  Final candidate/control score margins are -985/+1174.
- **v1-011, 909154041 / 909151076:** first difference turn 2. On turn 21,
  154:93 becomes 108:242 after boss DISRUPT43. Destroying that region alone
  gives exactly 108:242: the damage includes a large rerouting benefit to the
  boss, not just loss of our tracks. Final margins -1521/+1621.
- **v1-020, 909154529 / 909151906:** first difference turn 3. On turn 19,
  60:32 becomes 33:51 after boss DISRUPT22 plus the actual construction.
  The isolated cut gives 15:18; construction changes the final income too.
  Final margins -191/+50. The late closing incident is qualified below.
- **v1-021, 909154597 / 909151959:** first difference turn 1. On turn 20,
  271:94 becomes 53:55 after boss DISRUPT20; the isolated cut gives 51:57.
  A +260 score lead precedes this turn. Final margins -788/+921.
- **v2-010, 909168879 / 909157779:** first difference turn 3. On turn 11,
  52:13 becomes 3:15 after boss DISRUPT22 and construction. The score lead
  before the turn is +82; final margins are -24/+360. This is a useful close
  loss for investigating both early income and exposure to a known cut.
- **v2-017, 909169114 / 909158082:** first difference turn 1. On turn 25,
  215:187 becomes 115:246 after boss DISRUPT34 and construction. Destroying
  region 34 alone gives 10:90. Final margins -1374/+1166.

In those six turns the number of potentially connectable desired pairs does
not fall. The failure concerns active paths and their allocation of income,
not the map becoming disconnected. Five of the six end that turn with a
negative income margin.

The remaining four largest drops happen without any region being destroyed
on that turn. They must not be counted as the same sabotage mechanism:

- **v1-027, 909154892 / 909152338:** turn 36 income 17:50 to 67:288;
  our action only disrupts, leaving three paint unused. Final margins -2186/+662.
- **v1-029, 909154992 / 909152509:** turn 15 income 33:25 to 65:122;
  final margins -1006/+1314.
- **v1-036, 909155317 / 909153059:** turn 18 income 97:95 to 121:180;
  the boss builds a competing route. Final margins -464/+793.
- **v2-005, 909168672 / 909157507:** turn 29 income 73:17 to 59:90;
  final margins -692/+42.

## Closing opportunities and timing

Across all 761 candidate states, the bounded check finds **zero missed
guaranteed immediate winning one-hit closures**, and zero missed immediate
winning closures against the recorded boss reply, with the same construction.
This does not enumerate every alternative construction or longer winning plan.
Seven losses end by closing the topology and three reach the turn limit.

There is one self-contributed premature loss, v1-020 turn 83. The score is
1422:1613, income 4:5; both players attack region 18 at instability 2 and jointly
end the game at -191. Keeping the same construction and withholding our hit
leaves the game alive at -192 against that recorded reply. However, region 19
is already a one-hit closing cut: the boss can force the loss with DISRUPT19.
Avoiding the joint hit alone is therefore **not an identified recovered win**.
This is a lower-priority symptom than preventing the earlier loss of income.

There are no recorded server failures in the ten losses. Two contain a later
self-reported wall warning above 50 ms: v1-020 turn 32 at 51 ms, and v1-036
turn 12 at 58 ms. Such warnings are censored internal telemetry, not proof of
server timeouts or their causal effect. The logs contain neither search depth
nor completed comparison counters. The prior 75-ms native-call limitation is
unchanged; this offline analysis does not certify the time budget.

## Exact-artifact model check

Run the three previously generated artifacts (frozen v09, current batching OFF,
current batching ON) on every candidate input, with actual NOISE=0.1, persistent
instances per trajectory and a 5000-ms allowance. All **2,283 calls complete**.
All 761 states match in emitted action, shared search work and selected-value
bits across the three artifacts. The once-only run has no warmup, nominal-time
measurement or replay replacement. This finds no batching semantic defect on
the selected states.

The replayed inputs advance with recorded actions, not the newly chosen ones.
The all-ample policy history has a different sequence of RNG consumption from
the original time-bounded games. It matches the recorded candidate action on
369/761 states and differs on 392. On the ten first differing states, its action
matches v09's recorded choice five times, the candidate's twice, and neither
three times. This is not a reconstruction of the original searches and does
not identify historical timer or noise causality.

Before any calls, choose three states per pair: the first accepted-action
difference, the earliest largest income-margin drop, and the terminal pre-turn.
The 30 states give 90 identical cross-artifact inspections: 24 states have a
verified selected native root, and six explicitly take the protected opening.
For each applicable root, retain its raw construction, completed emitted
construction, and both native opponent replies and eight-turn forecasts.

Crucially, **both native replies already predict the actual dangerous sabotage
target in all six one-hit collapse states**. The simple hypothesis that the
model omits these attacks is not supported. The entire first boss action,
including construction, matches a retained reply in only two of the six.
Construction and subsequent recovery predictions are the useful next diagnostic
targets. Positive values on an all-ample alternative must not be compared with
the eventual recorded loss as if the same actions had been played.

Six of the 24 selected native roots add construction after the action was
evaluated. This is the known root/fill mismatch, not a newly discovered batching
bug. The earlier [root-fill experiment](rollout-fill-root-v1.md) already tested
that broad change and missed its local gate at 54.1%. The earlier
[all-cell risk experiment](rollout-risk-v1.md) also failed, at 54.2% locally and
10/40 against the boss versus v09's 15/40. Neither flag is justified by this
failure-only diagnosis. Historical diverse-reply and own-route-replan experiments
were rejected too; their verdicts are retained in `BOTS.md`.

## Most useful next investigation: execution of forecast recovery

A fixed additional core-only check crosses three own actions (recorded, ample
raw, ample full) with three boss actions (recorded, native reply 0, native reply
1) on each of the six one-hit-collapse states: 54 one-turn transitions. Six
terminal combinations verify v1-020's qualification. These 60 checks create no
games and make no new policy calls. Holding the recorded own action fixed,
at least one native reply reproduces the actual immediate score, income and
terminal status in five of the six cut states, despite only two exact boss
action matches. Across the six eight-turn comparisons, both native score margins
are higher than the recorded continuation in three cases, lower in two, and
mixed in one. There is no evidence here that every continuation is optimistic.

**v2-010 turn 11 is the strongest concrete starting point.** Native reply 1 is
exactly the recorded boss action. Against the ample raw/full own action it
reproduces income **3:15** and score margin **+70** after the first turn. By the
end of its eight-turn forecast, the model has recovered to **62:13** and **+180**.
The recorded continuation at the same elapsed turn instead has **14:28** and
**+24**, eventually losing by 24 points. Thus the immediate destruction is
already understood; the assumed subsequent recovery deserves investigation.

This is a **hypothesis about continuation optimism**, not an error estimate for
an identical policy trace: own construction differs at the starting turn,
later actions and boss responses differ, and the model has an all-ample RNG
history. The study has not found a replacement move that demonstrably wins.

The next bounded diagnostic should compare the promised construction sequence
with repeated actual move selection after the cut, retaining the complete first
action, actual noise setting and time budget. Start with v2-010 and use the
other five cut cases plus saved wins as controls. Distinguish abandoned repairs,
inaccurate boss construction, and recovery outside the useful scoring horizon
before selecting one narrow change. Do not simply add a sabotage reply that is
already present or reopen the rejected broad risk/root-fill flags. This report
implements no such strategy change and starts no new strength or live series.

## Evidence and limits

The [compact evidence](evidence/v09-batched-loss-diagnosis-v1.json) pins the
cohort, all trajectories, the prospective scope and exact generated artifacts.
The [archive index](evidence/v09-batched-loss-diagnosis-v1-artifacts.json) and
[archive](evidence/v09-batched-loss-diagnosis-v1-artifacts.zip) retain the
diagnostic helpers, all raw outputs and the twenty selected canonical replays.
Previously frozen inputs and historical verdicts remain immutable.

These ten pairs are now exposed to outcome-guided development. A subsequent
strategy candidate must use a refreshed boss control pool for confirmation,
in addition to its own prospective local and exact-artifact checks. The existing
unfinished series concerns the unchanged optimization and remains exploratory.
