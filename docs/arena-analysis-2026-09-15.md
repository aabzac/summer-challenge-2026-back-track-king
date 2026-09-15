# Later arena sample: entry 6739024

## Scope and reproduction

User reports Gold opened on 2026-09-15; our bot remains in Silver at 235/622 and still uses
the previous build, without the new isolated warmup. Exact deployed source hash and placement
completion are unknown. The public global leaderboard query returned rank 363 / score 18.5501
for aabzac, agent 6739024. Global and league-local ranks are different scopes.

Fetched the latest 50 completed games available for that entry on 2026-09-15. All were new to
the local corpus (214 -> 264 arena files). Fetch date does not establish that each battle took
place after Gold opened; battle timestamps/league-at-play are not established by this report.
Do not pool this sample with earlier entries 6735801 or 6736850, or attribute it to new warmup.

Commands (PowerShell, repo root):

```powershell
.\gradlew.bat cgFetch "-Pplayer=aabzac" "-Plimit=50"
.\gradlew.bat cgValidate cgAnalyze "-Pagent=6739024" "-Pout=build/analysis/agent-6739024-2026-09-15.json"
```

Engine validation: **264/264 exact**. Agent-filtered analysis contains exactly the 50 new IDs
listed below. Full trace is local/gitignored under `build/analysis/`; raw replays in `replays/cg/`.
Analysis uses `CgPlayerStudy` at commit `1a63fd7`; no engine or strategy code changed.

## Findings

- 25 wins, 25 losses, no draws, against 38 distinct opponent agent IDs. Zero own terminal
  failures. One win, 903920870 vs Adler3D, is an opponent timeout; excluding it gives 24-25.
  This is a selected recent arena sample, not a paired benchmark or promotion test.
- Losses average 77.0 turns; 12/25 reach 100 turns. Wins average 69.8; 6/25 reach 100.
- In losses, we disrupt on 84.7% of turns vs the opponent's 88.6%. We spend 2.46 paint/turn
  vs 1.96, but 2000/3894 of our accepted cells never earn (51.4%) vs the opponent's 28.3%.
  In wins our never-earning fraction is 37.8%. These cells may still block or provide options;
  the statistic alone does not mean every such placement should be removed.
- Wins/losses by map height: 14: 3/2; 15: 2/4; 16: 2/5; 17: 6/7; 18: 7/3; 19: 3/3;
  20: 2/1. Heights 19-20 are 5/4 including the timeout win. No basis to extrapolate the old
  large-map collapse unchanged; these strata are tiny and opponents differ.
- First income comes later than the opponent's in 33/50 games, including 18 wins and 15 losses.
  A slow opening alone does not separate wins from losses in this sample.

## Captured loss episodes (turns are zero-based)

These are one-turn counterfactuals: keep the opponent's actual same-turn action, change ours,
then return to the recorded trajectory next turn. Deltas are improvements in that turn's
score difference, NOT final-score gains, match wins, independent observations or sums to recover.

- **903953019 vs Mazoise, P1, turn 20:** our two accepted placements produce income 144 vs
  opponent 160; omitting those placements improves the immediate difference by 42. No region
  inks this turn. Useful partial-build regression, but the previously rejected blanket income
  veto must not be re-enabled on this example alone.
- **903970829 vs Meruem, P0, turn 46:** recorded `DISRUPT 6`; replacing it with region 5
  improves the immediate difference by 189, with our placements and opponent reply held fixed.
  At turn 43 the same region gives +185. Actual and alternative branches remain nonterminal.
  This is target selection, not simply failure to output a sabotage command.
- **903956941 vs OldJohn, P1, turn 28:** recorded WAIT; sabotage of region 12 gives +179
  in the oracle branch, without ending the game. Unlike the Meruem episode, absent output could
  reflect timer exhaustion, evaluation rejection, or both; stdout alone cannot decide.
- **903948756 vs 4bungi, P1, turn 33:** omitting our sabotage improves the immediate difference
  by 60 while the opponent inks another region. More sabotage is not automatically better.

In losses there are 39 turns with positive omit-build delta and 404 with a better immediate
sabotage alternative. The latter spans 23 losses but is highly repeated within the same games:
OldJohn 59 turns, DoubleA_W 51, Sawa974 46, gman.76 46. This is not 404 independent mistakes.

## Decision and next bounded diagnostic

Keep v05 and the prepared submission unchanged. Prior rejected income veto, early sabotage
fallback, reply reranking and interruptible evaluation flags remain off.

Investigate **sabotage target valuation** before adding another strategy flag. Current
`disruptValue` projects post-ink income change across `turnsLeft - needed`, divides by the
number of required disruptions, and includes track/route terms. `chooseDisruptImpl` can also
stop before checking every region. Both are plausible explanations, not established causes.

On the Meruem and OldJohn prefixes, first compare the recorded target with a complete evaluation
of candidate targets from information available before the simultaneous opponent turn. Record
target value components and whether the timed scan completes. This distinguishes a search-budget
problem from a valuation issue and from reliance on an unknowable opponent reply. Only then
choose one default-off change (for example a bounded sabotage income horizon), not several.

Any strength screen must use fresh paired maps, frozen v05, map-aware uncertainty, and an older
or different opponent; retain the >=55% / CI above 50% gate and exact-artifact process checks.
These 50 observed maps are development evidence, not held-out confirmation.

## Exact fetched game IDs

```text
903887347 903889244 903898122 903901333 903903473 903905424 903911030 903915933
903919749 903920870 903929642 903935052 903936178 903936435 903937359 903937931
903938176 903939635 903940522 903940621 903940977 903941146 903941833 903942020
903948045 903948756 903949395 903949425 903950373 903951000 903951672 903952126
903953019 903953095 903954097 903956941 903957079 903958788 903959677 903964881
903965819 903969232 903969650 903970829 903972831 903973710 903974325 903974545
903976293 903977005
```
