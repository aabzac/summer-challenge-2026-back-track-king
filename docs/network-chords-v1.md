# Network chords v1 — fixed experiment

Registered 2026-09-18 before strength games. Selected control: **v07**.
Candidate: `current?USE_ROLLOUT_NETWORK_CHORDS=1`; common opponent: **v05**.
The independent `USE_ROLLOUT_ROUTE_DEPENDENCIES` experiment stays OFF. All other parameters
retain selected v07 defaults. Complete effective parameters and source/class hashes are
frozen before play, including the generator bounds below.

## Hypothesis and scope

In boss game 904554568, zero-based turn 26, three plains cells (22,6), (21,6), (20,6)
change known-board income 132:126 to 64:186. Every proper subset has zero income effect.
No new desired connection activates: three existing shortest paths reroute. This measures
accepted boss construction alone on the public pre-turn state, not a projected match win.
The exact subset evidence is in `docs/evidence/boss-v07-steal-subsets.json`.

Selected v07 explicitly searches single-cell network changes after its ordinary portfolio.
It cannot generate a multi-cell chord through that phase when all individual cells have
zero immediate value (an ordinary route may still generate such a move incidentally).

The candidate adds one late phase: only after every ordinary AND existing singleton
comparison finishes, consider connected two-/three-cell chains affordable this turn.
Canonical core helpers enumerate unique pairs and triples with existing connector neighbors
at both ends; same-component chords remain eligible. A bounded 24-candidate prefix is ranked
by adjacent active-path traffic, then actual paint cost and stable cell identifiers.
Exact whole-network income retains at most two positive-gain roots. The ordinary simultaneous
rollout evaluates each complete first action, including construction and sabotage by both
players. Partial comparisons never replace the saved decision. The existing 12-ms network
reserve applies to candidate collection/evaluation. No new strategy for subsequent rollout
turns, future opponent information, or promotion is included.

Tests cover unique/cost-bounded core generation, zero-value subsets of a profitable triple,
both sides, input isolation, late-phase priority, interruptions and disabled baseline behavior.
Run full unit tests and exact CG validation after the core helper is added. Potential cost:
late work may rarely have time to run, and positive immediate income can still die to sabotage.

## Predeclared complete stages

Practical-300-1000-v2, nominal CG budgets, two workers, paired sides, half credit for draws.
Exclude all historical map hashes, prior route-dependency experiment maps, and earlier stages.

- Direct screen: 300 games, seeds 200000–200149; <=52% stops, >=60% passes directly.
- Only for >52% and <60%: separate 1000 direct games, seeds 201000–201499; require >=55%.
- After direct success: candidate and v07 each play v05 on the same 300 games,
  seeds 202000–202149; require candidate advantage >=3 percentage points.
- Otherwise: candidate and v07 each play v05 on the same fresh 1000 games,
  seeds 203000–203499; require nonnegative candidate advantage.

No tuning, retries, pooled stages or extra strength ablation. Complete every started stage
and preserve errors and timing outliers. CI is diagnostic. Exact-artifact process checks are
separate from strength; no automatic promotion or upload. Existing selected submission stays
unchanged throughout strength evaluation.

After a complete strength pass only, use an isolated staging copy for `gradlew submit`
with the exact candidate spec. Strict `procCheck` vs v05: four individual maps in order,
903510176, bot/904146009, bot/904554568, bot/904555096 under `replays/cg`, two games each
to cover both sides. Eight games, 1000/50-ms deadlines, zero grace, no retries or discarded
failures, no concurrent CPU-heavy work. Complete the fixed panel after a valid timing failure.
Retain exact source/class hashes and all reports; original submission files remain unchanged.
On strength failure, skip this stage and make no process-readiness claim.

## Reproduction

After final compilation, tests and validation, and after the independent dependency experiment:

```powershell
python tools/run_network_chords_experiment.py --preflight
python tools/run_network_chords_experiment.py --run
```

The single-use runner freezes identities, configuration, pools and existing submission files;
it writes durable decisions to `docs/evidence/network-chords-v1.json`. Final results are added
to BOTS/DEVLOG separately, preserving this predeclared protocol during evaluation.
