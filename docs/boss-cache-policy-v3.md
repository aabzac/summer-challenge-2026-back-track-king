# Practical selection with a cached boss control

Policy: **practical-300-1000-boss-cache-v3**, adopted by the user on 2026-09-19.
Applies to future experiments. The network-chords-v1 local result remains NOT_PASSED;
its amended boss series remains exploratory. No old verdict is recalculated as a pass.

## Required stages

Freeze candidate A and the selected control B, their full parameters, local map pools,
the exact boss cache and all branch rules before measuring the candidate.

1. **Direct screen:** A versus B, 300 games on 150 fresh maps, both sides per map.
   At least 60% passes the direct stage. At most 52% stops evaluation.
2. Only when **52% < score < 60%**, play a separate fixed **1000 games** on 500 fresh
   maps. At least 55% in these 1000 alone passes; otherwise the candidate fails.
   Do not pool the 300 and 1000, tune between them or repeat a completed failure.
3. **Boss check:** exactly **40 new candidate games**, one on each of the cached
   control's 40 unique maps, in the same seed and player seat. The cache has 20 P0
   and 20 P1 games. Do not create new control games for every candidate.
   Candidate total must be **at least the cached control total**; equality passes.
   In both local and boss stages, a win counts 1, a draw 0.5 and a loss 0.

The boss check replaces the mandatory matched older/style-opponent 300+300 and
conditional 1000+1000 stages. Such opponents remain useful optional diagnostics,
but cannot rescue a failed required stage. There is no automatic 1000-game boss stage.

This is a practical selection rule, not a significance or noninferiority test.
Passing does not establish that the candidate's true boss win rate is no worse.
Confidence intervals and discordant-map counts are descriptive; no overall 95%
guarantee or automatic significance verdict is attached to cache reuse.

## Parallel execution and completion

After the 300 screen qualifies, the sequential boss batch may run concurrently with
the required local 1000. If the screen already passed at 60%, only the boss stage
remains. At or below 52%, launch neither continuation nor boss evaluation.

Complete both started stages regardless of intermediate scores. If the local 1000
fails, retain any concurrently collected boss outcomes as diagnostics; they do not
permit promotion. If one stage is incomplete, the combined gate cannot pass.
Do not run multiple boss batches at once or alternate sessions to bypass rate limits.
Keep the current 20-second boss spacing; it is observed conservative pacing, not an
official quota. Respect refusals and retain uncertain requests without automatic replay.

Finish strict process/timing checks of the exact isolated submission before live
boss play and before heavy concurrent local work. A strength pass is not process
readiness and never authorizes strategy promotion, arena submission or a push.

## Cache identity and lifetime

Pin all 40 individual canonical control replays and result records, their hashes,
source plan, control source/build/configuration and process evidence. For each new
candidate game verify map content, seed, seat, boss identity and league, plus the
candidate's build marker and submitted artifact. A directory name or an aggregate
historical percentage does not establish a valid control. The cached build must be
the SAME selected control B as in the direct local stage.

Reject missing/tampered evidence, duplicate maps/game IDs, changed build or mismatched
map/side/boss/league. Preserve completed bot failures and scores; do not replace them
with another game. Errors block a clean pass even if the score threshold is met.
An uncertain API creation must be recovered by its actual game ID or recorded as
interrupted; this policy does not preauthorize blind retries or outcome replacement.

Check cache suitability before every new series. A changed boss identity or league
requires a fresh control. An unchanged boss ID does not prove its hidden implementation
or execution conditions remained unchanged; collection dates and that limitation
belong in the report. Changing selected control requires outcomes for that exact
new build. A newly selected candidate's retained games may supply its cache on the
same pool, subject to the same validity checks; do not silently relabel v07 outcomes.

Repeated use of these known maps is intentional regression testing. After a series
of changes chosen using this pool, schedule a separate fresh-map comparison and
refresh the control pool before the next development cycle. Record reuse and refresh
decisions in the experiment log; never extend or refresh a failing stage until it
passes. No universal cache-expiry count or duration has been adopted.

Local screen/continuation pools remain fresh, mutually disjoint and excluded from
earlier local experiments. Do not deliberately use the boss fixture maps for local
confirmation. Local benchmark hashes and boss graphics hashes use different schemes;
comparing their hash strings cannot establish cross-domain map disjointness. Use
the shared core map tooling when checking that property, not a second rules engine.

## Initially accepted control

The user explicitly accepts the completed v07 arm from
[the amended 40-pair comparison](evidence/chords-boss-v1.amended.json):

- Source plan: `build/cg-boss/chords-v07-boss-40/plan.json`, arm `control`.
- Plan ID: `3d951ec7e4ad1878e4e135d11b9f3a74b4611964074918c0d223cc06e90ee24b`.
- Control: `v07`, build `66c56c429c80a7861b199597f2fbeb5f1f1830b525977cacd8304f2d892ddb28`.
- Boss: canonical agent `6740207`, Boss 4, Silver; 40 unique maps, 20 seats each.
- Outcome: **10 wins, 30 losses, no draws**. Current practical threshold is therefore
  **10 win-equivalent points out of 40**, including half credit for draws.

Ten is derived from this cache, not a hard-coded threshold for future controls or a
claim that the control's true win probability is exactly 25%. Preserve the original
series' two excluded requests and amendments in its provenance. Accepting this cache
does not turn that historical exploratory series into an unamended confirmation.

## Tools

`tools/boss_cached.py` creates a new candidate-only plan referencing the immutable
control evidence; it never edits or appends to the original comparison plan.
The `plan` operation is local only. `run` is the operation that creates live games.
Use `--control-arm control` for the initial v07 panel. A complete, verified
candidate arm of a later plan can be referenced with `--control-arm candidate`;
cached-plan ancestry is checked recursively. This does not itself select that build
as the new control: the combined gate also requires it to match the selected local B.
Session import, isolated artifact preparation and replay rendering are described in
[boss-api.md](boss-api.md).

`tools/boss_practical_gate.py` checks the new combined rule. The original
`tools/practical_gate.py` remains the historical v2 checker for reproducibility.
No old experiment runner is silently switched to the new policy.

```powershell
python tools/boss_cached.py plan --control-plan build/cg-boss/chords-v07-boss-40/plan.json --control-arm control --candidate build/cg-boss/artifacts/NEW-CANDIDATE --candidate-check build/cg-boss/artifacts/NEW-CANDIDATE/process-check.json --out build/cg-boss/NEW-EXPERIMENT
python tools/boss_cached.py run --plan build/cg-boss/NEW-EXPERIMENT/plan.json
python tools/boss_cached.py report --plan build/cg-boss/NEW-EXPERIMENT/plan.json
python tools/boss_practical_gate.py --screen build/bench/NEW-EXPERIMENT/screen.json --direct build/bench/NEW-EXPERIMENT/direct.json --boss-plan build/cg-boss/NEW-EXPERIMENT/plan.json --out build/bench/NEW-EXPERIMENT/decision.json
```

Replace the uppercase placeholders with a prospectively frozen candidate/experiment;
omit `--direct` only when the 300 screen passed at 60%. Preserve source/executable
identity and prior-map exclusions in the experiment manifest in addition to these
checks. A standalone benchmark report cannot establish the full combined pass.
