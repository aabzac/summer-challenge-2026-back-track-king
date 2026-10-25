# stage4-threat-model-v1 — construction search consistent with the boss chooser (2026-10-25)

Two default-off flags on `current` (= v10 "Boss Model"), each in its own merger block, screened together
with `current` as the same-map control. Artifacts with both flags off are byte-identical to the pinned
default (`V10SubmissionTest`: 88,671 chars, `e5da0dcb…`, config `f0e507ac…`); the configuration hash
carries a flag only while it is on (`BossThreatRootTest`).

| flag | block | what changes |
|---|---|---|
| `USE_BOSS_THREAT_MODEL` (A) | `// BOSS_THREAT` | `threats[me]` — the opponent target that the route proposals bypass (variant 2 blocks that region) and protect against (`protectedGain`, weight `ROLLOUT_THREAT_WEIGHT` 0.5) — comes from `chooseBossLite(s, 1 − me, liteOppHeld)`: the two-stage boss chooser seeded with the opponent's observed held target, exactly what the rollout's simulated opponent plays on its first turn. Before: `chooseTarget(s, 1 − me)`, the symmetric myopic chooser. Requires `USE_BOSS_OPPONENT_MODEL`. One chooser call replaces one of equal cost (≤ 4 ink simulations, timer-checked). |
| `USE_BOSS_ROOT_TARGET` (B) | `// BOSS_ROOT` | every root's first simulated own turn disrupts `liteHeld`, the region the real turn actually disrupts (`actions()` overrides `selectedTarget` with it since boss-sabotage-v2); the no-disrupt alternative (`attack == 1`) is not searched because the real turn never wastes the point. Before: `rootTargets[i] = chooseTarget(targetBoard)` per root (self-like, ≤ 4 ink simulations each, up to 7 ordinary + 2 network roots) and two rollout scenarios per root whose winner only decided the construction, since the target was overridden anyway. Requires `USE_BOSS_LITE`. Removes work (≈ 28 ink simulations and half the comparisons per real turn). |

## Hypothesis (Stage 3–4 of the 2026-10-20 plan, first step after `prune-v1`)

Since `opponent-model-v1` (B of Stage 1) the rollout's opponent disrupts with the two-stage boss chooser and
our own simulated later turns do too; the real DISRUPT is the lite chooser's held target. Two places in the
construction search still use the old symmetric `chooseTarget`:

1. **The threat our routes are planned around.** The bypass generator (variant 2) and the protected-gain
   term exist, but they bypass and discount the region *we* would hit in the opponent's seat, not the region
   the boss-like opponent is holding. Against a 99 %-disrupt opponent that holds one region for four turns
   this is the Stage 4 "exposure" defect in its cheapest form: the bypass is built around the wrong region.
2. **Our own first simulated turn.** The rollout compares "hit the `chooseTarget` region" with "hit nothing"
   for every root, and the better scenario chooses the construction — but the real turn then hits `liteHeld`.
   The construction is thus optimised for a sabotage line we never play; the no-disrupt scenario is the
   "wasted disrupt point" of Stage 3 inside the rollout. Making the first simulated turn play the real target
   is the same consistency step that turned A into B in Stage 1 (own simulated disrupts = real chooser).

Expected effect: both are small, but in the direction the proxy rewards (routes that survive the boss's
held target; constructions valued under the real sabotage line). B also frees compute; the dividend line
showed more search alone does not convert, so any B gain is read as consistency, not compute.

## Predeclared screen (`tools/boss_proxy/run_stage4_screen.sh`)

Fresh pool seed **1002000** (first use of the range; 1001000..1001149 was `prune-v1`), 150 maps × 2 seats =
300 paired games per cell, CG 486/36 ms, two workers, the user's machine (Java 17, 14 cores). Cells:

- v5 step (a): `A-vs-boss2`, `B-vs-boss2`, `AB-vs-boss2`, `cur-vs-boss2` — each arm against the boss-like
  screening proxy on the same maps/seeds/seats as the `current` control, run in the same session. Reading:
  the paired per-map delta vs the control (`summarize_screen.py --control cur`); **continue only at
  ≥ control + 3 pp**.
- v5 step (b): `A-vs-cur`, `B-vs-cur`, `AB-vs-cur` — head-to-head with the selected version; **≥ 48 %**.
- Zero bot errors; max move time of every arm in the 36–38 ms band of `current` (no new unbounded step:
  A replaces a chooser call, B removes calls; v5 rule 6 cold timing not triggered).

Decision rule: keep a flag only if its arm passes both steps; if several pass, the next arena candidate is
the best-passing single flag or AB if AB ≥ both singles vs the proxy (one change at a time still holds for
the live step: AB is two flags, so it goes live only after its singles are read). Any failing arm is
recorded and parked. A passing arm gets `submit` + strict `procCheck` here; the 80 live boss games are the
user's launch.

## Results (2026-10-25, `docs/evidence/stage4-threat-model-v1/`, screen 13:53–14:23 UTC)

| cell (300 paired games, pool 1002000) | rate | W-L-D | ms/move cand. | max ms | maps h < 19 | h ≥ 19 |
|---|---|---|---|---|---|---|
| `current` vs boss-v2 (control) | **64.7 %** | 194-106-0 | 11.7 | 36 | 66.0 % | 62.3 % |
| A vs boss-v2 | 66.2 % | 198-101-1 | 11.5 | 36 | 64.2 % | 69.8 % |
| **B vs boss-v2** | **73.5 %** | 220-79-1 | 7.9 | 39 | 76.5 % | 67.9 % |
| AB vs boss-v2 | 70.7 % | 212-88-0 | 8.7 | 36 | 72.7 % | 67.0 % |
| A vs `current` | 51.0 % | 153-147-0 | 11.3 | 36 | 48.5 % | 55.7 % |
| **B vs `current`** | **57.0 %** | 171-129-0 | 7.6 | 37 | 57.7 % | 55.7 % |
| AB vs `current` | 57.0 % | 171-129-0 | 7.9 | 36 | 62.4 % | 47.2 % |

Paired per-map deltas vs the control on identical maps/seeds/seats (`summarize_screen.py --control cur`):

| arm | delta vs `current` against boss-v2 | maps better / worse / equal | step (a) ≥ +3 pp | step (b) ≥ 48 % |
|---|---|---|---|---|
| A `USE_BOSS_THREAT_MODEL` | **+1.5 ± 7.1 pp** | 43 / 45 / 62 | **fails** | 51.0 % passes |
| B `USE_BOSS_ROOT_TARGET` | **+8.8 ± 7.1 pp** | 54 / 32 / 64 | **PASS** | **57.0 % PASS** |
| AB | +6.0 ± 7.2 pp | 47 / 35 / 68 | PASS | 57.0 % PASS |

Zero bot errors in every cell; accepted-disrupt rate 98.4–98.5 % for every arm (the proxy 100 %); game length
unchanged (avg 66–70 turns). B's mean move time falls from 11.7 to 7.9 ms: with one scenario per root and no
per-root `chooseTarget`, the search completes earlier — the strength gain is not bought with compute. The one
39 ms maximum (one move in 300 games; 37 in the head-to-head cell, the control's 36) is noise on a flag that
removes work; the strict process check below is the timing gate.

**Verdict.** B (`root-target-v1`) passes v5 steps (a) and (b) with the largest same-map proxy delta of the
chain so far (D over C was +2–3 pp vs the proxy and 53.7 % head-to-head; B is +8.8 pp and 57.0 %). A
(`threat-model-v1`) fails step (a) (+1.5 pp, inside the cell error) and is **parked**: the bypass built
around the boss's held target does not beat the bypass around the self-like threat — the rollout's opponent
model apparently already prices the held target into the route values, so the proposal-stage threat matters
little. AB is below B on the same maps (−2.8 pp): A adds nothing on top of B, so **B goes forward alone**
(one change at a time). Next for B: strict `procCheck` of its exact artifact (below), then v5 step (c) — 80
paired live boss games launched by the user (`tools/run_boss_paced.sh`), with `current` (= v10) as the
same-session control.

## B's artifact, strict process check and the live plan (2026-10-25)

- `gradlew submit "-PsubmitVersion=current?USE_BOSS_ROOT_TARGET=true" -PsubmitOut=build/submit-stage4-b` →
  **89,008 / 100,000 chars** (readable), sha `ea4bfe7a…`, build `87973a8a…`, **config `179dcc66…`** (the default's
  `f0e507ac…` plus the one flag), first-turn `MESSAGE v10 Boss Model (modified) | BTK_BUILD_V1 strategy=current …`.
- Strict `procCheck` (zero grace, 4 games vs v09, pool `replays/cg`, the artifact as a child process):
  **0 problem games, 0 timeouts**, first response ≤ 635 ms, later responses ≤ 37.4 ms, 4/4 wins
  (`evidence/stage4-threat-model-v1/B-artifact-process-check.json`, manifest alongside).
- Live plan for v5 step (c), frozen with no network calls: `build/cg-boss/stage4-b-current-boss-40/plan.json`, id
  `53e2fee9…`, matched design, **40 maps × 2 builds = 80 API games**, boss 6767608, schedule seed 2026102502,
  candidate = `build/submit-stage4-b`, control = `build/submit` (the default `current` artifact, sha `e5da0dcb…`,
  its strict check in `evidence/readable-export-v1/`), bound to the puzzle-IDE session of 2026-10-18 like every
  series since 2026-10-03. Pass rule as in `stage2-d-live-boss-v1.md`: B ≥ control + 5 pp over the 40 paired maps
  (the policy's 80 candidate games complete with a second session; one predeclared extension to 120). Launch, by
  the user on this machine (the runner reads the private session files):

  ```
  bash tools/run_boss_paced.sh build/cg-boss/stage4-b-current-boss-40/plan.json build/cg-boss/private/v09-ink45-boss-v1/session.json 20 300 20 build/cg-boss/private/v09-ink45-boss-v1/start-session.json 0722a2fe30a43359747729078cfa150d027fc9c6fc7a9e3fbb102210563accba 1172955
  ```
- Tooling fix on the way: `tools/replay_capture.validate_local_build` rejected every current artifact since
  `readable-export-v1` ("Registered source marker is malformed") because the declared `BUILD_MARKER` now leads with
  the display name; it reads the marker from `BTK_BUILD_V1` on, a prefix must be exactly `<name> | `
  (`test_replay_capture.py` 14/14, `test_boss_batch.py` 33/33, `test_boss_batch_audit.py` 9/9).

## Live boss, v5 step (c) session 1 (run by the user 2026-10-25 15:05–16:06 UTC, `evidence/stage4-threat-model-v1/live-session-1/`)

80 API games, 0 technical failures, 0 authorized retries, every replay carries the planned build marker,
**80/80 replays reproduced exactly** by the local engine (`cgValidate` on both capture directories). Runner report:
`complete: true`, `issues: []`, `protocolStatus: original fixed`.

| vs live boss 6767608, 40 matched maps, same session | wins | score | exact 95 % |
|---|---|---|---|
| **B** `current?USE_BOSS_ROOT_TARGET=true` (`87973a8a…`) | **21/40** | **52.5 %** | 36.1–68.5 |
| control `current` = v10 (`94f7cce9…`, the default artifact) | 20/40 | 50.0 % | 33.8–66.2 |

Paired over the 40 maps: candidate-only wins **10**, control-only wins **9**, equal 21 → **+2.5 pp**, one-sided
p = 0.50. Seats P0 10/20 vs 10/20, P1 11/20 vs 10/20. By size (B pts / control pts / maps): 21×14 3/5/7,
23×15 1/1/2, 24×16 5/2/7, 26×17 2/3/5, 27×18 2/3/6, 29×19 3/1/5, 30×20 5/5/8. The running paired delta moved
−10 → −5 → −6.7 → +2.5 over the four chunks of 10 pairs: the series is inside its own ±7.5 pp noise end to end.

**Reading (predeclared rule): the session pass threshold of +5 pp over the 40 paired maps is not reached; the
session is inconclusive**, neither the +8.8 pp the proxy promised nor a loss. v5 step (c) is decided on 80
candidate games: a second 40-map session on another day (fresh plan, same-session `current` control) completes
the sample, with one predeclared extension to 120 if the pooled 80 are within noise; the decision to spend it is
the user's. Until then **B stays a screened candidate, not a promotion; no arena upload.** Two facts for that
decision: the control itself scored 50 % against the live boss (D's session 1 on 2026-10-23 had 45 % vs v09's
22.5 %), so v10 has little headroom left to show on this boss in 40 games; and B's proxy gain came with a
compute dividend (7.9 vs 11.7 ms per move) that the live boss may simply not punish the way the proxy does.
