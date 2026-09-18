# Network chords against the live Silver boss: fixed paired screen

Registered 2026-09-18, after API smoke verification and **before** this screen's
first game. This is a separate exploratory external-boss comparison requested by
the user. The network-chords-v1 local direct result remains NOT_PASSED (54.4% in
its fresh 1000-game stage); no pooling, local-gate reinterpretation or promotion.

## Frozen design

- 40 unique fresh maps, one prebalanced seat per map; each build plays once in
  exactly that seat. **80 executions, 40 independent map pairs.**
- Candidate: `current?USE_ROLLOUT_NETWORK_CHORDS=1,USE_ROLLOUT_ROUTE_DEPENDENCIES=0`.
  Build ID `faa7087da1c02859900cb43afb026be3e18abdbadd84ddb0c74a3830b2169783`;
  final Player SHA-256 `b14cc066978e82c4b7e364ca4b9126351f7e18fad9bd3e41b86dd23eb1c66353`.
- Control: frozen `v07`. Build ID
  `66c56c429c80a7861b199597f2fbeb5f1f1830b525977cacd8304f2d892ddb28`;
  final Player SHA-256 `f8cd5b81217537d1d4c98dcbea1be50d8f81ca9c9f93d4d742b0a19831021015`.
- Boss: canonical agent **6740207**, Boss 4, Silver. Captured API selector is `-2`.
  Reject a changed canonical boss/league identity; a stable label cannot prove that
  its hidden implementation never changed.
- Schedule randomization seed **2026091840**; 20 maps per seat and balanced AB/BA.
  The plan retains every requested seed, slot, exact artifact/process-report hash,
  and the known initial-map fingerprints excluded before starting.
- No map/side repeats within this sample beyond the intended two-build pair. The
  initial `global.graphics` SHA-256 must match within each pair, not just the seed.
- Five technical API games and the historical boss diagnosis corpus are excluded.
- Each exact tagged artifact passed a separate 4-game strict process check with
  no timeouts, errors or nominal overruns. This checks packaging, not the earlier
  failed strategy-strength gate. The original 26 `build/submit` files are unchanged.

## Outcome and decision

Primary outcome is outright win. A draw is a non-win in the paired binary test;
retain W/L/D, half-win score, canonical ranks, scores and terminal failures too.
Let b be maps won only by the candidate, c maps won only by control. The prespecified
one-sided exact test is `P[Binomial(b+c, 0.5) >= b]`, alpha 0.05. Both-win and
both-non-win pairs do not contribute to this conditional test. For example, b=9,
c=1 passes; b=8, c=2 does not. Forty pairs do not imply forty discordances.

Complete all 80 scheduled games regardless of intermediate outcomes. No tuning,
candidate changes, outcome-driven stops, replacements, extension or pooling old
6/20 with the fresh control. API requests execute sequentially with a pause; play
creation is never repeated after uncertainty. Resume only the same recorded request
through its game ID. Failed completed games stay recorded. Unrecoverable transport,
map, identity or provenance mismatch interrupts the stage instead of silently
replacing a game.

At completion, p <= 0.05 means evidence of improvement against this boss under this
design; otherwise the result is inconclusive for improvement. This exploratory
screen does not promote the candidate or override the repository strength gate.
Separate rate intervals are descriptive; their overlap is not the decision rule.

## Reproduction and evidence

Runner and setup: [boss-api.md](boss-api.md). Statistical rationale and alternate
fixed sizes: [boss-evaluation-protocol.md](boss-evaluation-protocol.md).
The immutable local plan is `build/cg-boss/chords-v07-boss-40/plan.json`. Each slot
retains intent, raw play response, canonical replay and independently checked result.

```powershell
python tools/boss_batch.py run --plan build/cg-boss/chords-v07-boss-40/plan.json
python tools/boss_batch.py report --plan build/cg-boss/chords-v07-boss-40/plan.json
```

Final outcome and replay IDs will be recorded separately in DEVLOG/evidence after
the fixed series. This prospective section must remain unchanged during play.
