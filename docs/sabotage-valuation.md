# Sabotage valuation diagnostic — 2026-09-15

## Question and method

Follow-up to `arena-analysis-2026-09-15.md`, committed as `674aaa4`: does the bot miss a profitable
ink because time runs out, because its evaluation prefers another target, or because the analysis
knows the opponent's simultaneous action?

`gradlew cgDisrupt` examines the four preselected positions: Meruem 903970829 P0 turns 43/46,
OldJohn 903956941 P1 turns 19/28. All turn numbers are zero-based. `cgValidate` first reproduces
264/264 corpus games exactly. No policy, core or frozen-version source changes.

The tools-only adapter invokes EvalBot's real private selector and value function, avoiding
copied policy/rules and any diagnostic code in the submission. Two separate inspections:

- **Known-state full scan:** assume our recorded accepted placements succeed, update connections
  with the core, then evaluate every target with noise off. No opponent selected-turn action is
  provided. Only accepted placed cells are marked as our plan; intended but unbuilt route cells
  are unknown. Thus this is not an exact reconstruction of all hidden bot scratch data.
- **Timed local replay-prefix run:** initialized current evaluator with v05 parameters, old
  generic 500-ms warmup, 486/36-ms decisions while resetting the board to the recorded trajectory
  after each turn. Capture scan completion, emitted action and elapsed time; then fully evaluate
  that call's build state, preserving its actual plan marks. Full diagnostic calls consume no
  RNG, but can affect subsequent JIT. No equivalence to CG hardware or deployed source hash.

Full output: `build/analysis/disrupt-diagnostic.json` (local/gitignored). Reproduce default four:

```powershell
.\gradlew.bat cgValidate cgDisrupt
.\gradlew.bat test
```

Custom selection uses `-Ptargets=path@player@turn+turn,...`; output accepts `-Pout=...`.
Checked-in `src/test/resources/btk/bot/disrupt-valuation.json` contains only pre-turn action prefixes
plus our selected-turn action, not the opponent's selected-turn reply. Unit tests do not require
downloaded replays or timing assertions. All 112 tests pass.

## What the fixed sample showed

**Meruem, turn 43:** recorded region 7; full known-state selector also chooses 7. Ready region 5
would reduce our income by 295 and the opponent's by 472: immediate difference benefit +177.
Its v05 value is 6608.1. Region 7 needs two disruptions but, if inked on this board, would increase
our income by 153 while reducing theirs by 153; its value is 9256.9. The evaluator favors the
larger delayed effect, with no need for time truncation to explain that preference.

**Meruem, turn 46:** recorded/full target 6 needs four hits, value 3272.6. Ready alternatives:
region 3 at 3106.2; region 5 at 3063.5. Region 5's known-state difference benefit is only +51
(our income +34, opponent income -17), versus +189 in the earlier oracle counterfactual.
The opponent's actual simultaneous ink changes routing materially. Simply copying the oracle
target is not an implementable decision rule; nor does a missed immediate gain prove a bad plan.

**OldJohn, turn 19:** recorded no sabotage, but full known-state choice is ready region 27,
value 4896.9. The post-ink income loss is 59 for us and 132 for the opponent, so a positive
target exists without knowing the opponent's reply.

**OldJohn, turn 28:** recorded WAIT; full known-state choice is ready region 12, value 4260.1,
income losses 20/84 (benefit +64 against WAIT, versus +179 with the recorded opponent reply).
The formula does not reject this useful target. The original missing output could still involve
timing, hidden plan terms, deployed parameters or a different build; stdout cannot settle it.

In the single local timed run, targets were 7, 6, 27, 12 respectively and all scans completed.
Elapsed times were 33.45, 29.81, 17.52 and 18.30 ms. Both OldJohn omissions were **not reproduced**.
This is diagnostic evidence, not a formal process deadline check or a claim about the CG CPU.

## Next experiment, not yet implemented

Test one parameter discounting a sabotage effect that needs additional future hits. Existing
valuation projects a hypothetical ink's income effect across nearly the entire remaining game,
then divides by hits needed. That can prefer starting a four-hit target to collecting a useful
one-hit effect while the opponent keeps changing the network.

Use a default-neutral/default-off change and preserve the existing terminal win/loss safeguard.
Do not turn this into unconditional ready-target priority: useful investments and simultaneous
interactions remain real. Do not combine it with income veto, opponent modeling or timing changes.
First demonstrate the intended mechanism on captured states, then predeclare a fresh paired-map
screen against frozen v05 and a different/older opponent. Keep the normal promotion gate; selected
arena positions and these four successful scans alone are not evidence of increased strength.

The prepared v05 Player.java is unchanged (SHA-256
`748b564a77a0d3293a925913f396a60d4598783501bc4a74a9a6624f6fadbbc5`). No upload, no new version,
and no strength benchmark for this tools-only step.
