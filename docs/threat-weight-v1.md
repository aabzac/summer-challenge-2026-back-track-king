# threat-weight-v1 — the threat blend in the route proposals (2026-10-27)

`ROLLOUT_THREAT_WEIGHT` (default 0.5, never screened before) blends a route proposal's plain gain with its gain if the
predicted threat region inks: value ∝ (1 − w) · gain + w · protectedGain (`RolloutBot.propose`). Since v12 the
exposure-income discount prices part of the same risk, so the blend could have moved off its optimum. Parameter screen,
no code change; the first candidate under policy v5 step 1a (an arm passing step (a) would be replicated on pool 1017000).

## Screen (`tools/boss_proxy/run_threat_screen.sh`, fresh pool 1016000, 300 paired games per cell, CG timing)

| cell | rate | W-L-D | paired delta vs `current` (150 maps) | maps better / worse / equal |
|---|---|---|---|---|
| `cur-vs-boss2` | 79.0 % | 237-63-0 | — | — |
| `T25-vs-boss2` (w = 0.25) | 74.7 % | 224-76-0 | **−4.3 ± 6.6 pp** | 32 / 44 / 74 |
| `T75-vs-boss2` (w = 0.75) | 78.0 % | 234-66-0 | **−1.0 ± 6.6 pp** | 35 / 39 / 76 |
| `T25-vs-cur` | 49.3 % | 148-152-0 | — | — |
| `T75-vs-cur` | 54.0 % | 162-138-0 | — | — |

0 errors in 1,500 games. **Both arms fail step (a); the default 0.5 stays.** Evidence `docs/evidence/threat-weight-v1/`.
Pool 1016000..1016149 consumed, next ≥ 1017000.
