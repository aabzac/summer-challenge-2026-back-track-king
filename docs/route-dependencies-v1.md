# Route dependencies v1 — fixed experiment

Registered 2026-09-18 before strength games. Selected control: **v07**.
Candidate: `current?USE_ROLLOUT_ROUTE_DEPENDENCIES=1`; common opponent: **v05**.
All other effective parameters retain selected v07 defaults, including QuickIncome and
network-after-base. The complete configuration and source/class hashes are frozen at preflight.

## Hypothesis and scope

The canonical paint-path generator compresses existing rail components. The rollout stores
that build recipe, but its invalidation check only sees cells explicitly in the recipe.
An omitted bridge can therefore be inked during a forecast without abandoning the project.
This changes predicted future construction, not core scoring: the real bot replans every turn.

The candidate records region dependencies of the already-computed completed target-pair
path, alongside the unchanged build recipe. Future simulated construction abandons a project
if a dependency region inks. No new path search, change to construction order, ranking,
scoring, core rules, frozen bots, or default policy. The canonical witness is conservative:
it can abandon a project even if another detour would still connect the pair.

Mechanism evidence must cover omitted bridges, both player sides, QuickIncome replacement,
retained route movement, input isolation and unchanged behavior with the flag disabled.
The replay audit uses observed ink only to classify a failure mode; recorded future actions
are never inputs to the candidate policy.

## Predeclared complete stages

Practical-300-1000-v2; nominal CG timing, two workers, paired sides, draws count half.
Every stage excludes all previously used map hashes and all earlier stages in this experiment.

- Direct screen: 300 games on seeds 190000–190149. At <=52% stop; >=60% direct pass.
- Only for >52% and <60%: separate 1000 direct games, seeds 191000–191499; require >=55%.
- After direct success: candidate and v07 each play v05 on the same 300 games,
  seeds 192000–192149; require candidate advantage >=3 percentage points.
- Otherwise: candidate and v07 each play v05 on the same separate 1000 games,
  seeds 193000–193499; require nonnegative candidate advantage.

No tuning, retries, pooled stages, extra strength ablation or continuation after direct failure.
All games, errors and timing outliers are retained. Confidence intervals are diagnostic.
A strength pass requires separate exact-artifact process checks before a readiness claim.
No promotion or arena upload is authorized by this experiment; selected v07 stays selected.

Only after a complete strength pass, build the exact candidate in an isolated staging copy
with `gradlew submit`, preserving the original `build/submit` hashes. Strict `procCheck` vs
v05 uses these four maps in order, two games per individual file to cover both sides:
903510176, bot/904146009, bot/904554568, bot/904555096 under `replays/cg`.
1000/50-ms deadlines, zero grace, eight games total, no retries or discarded failures.
Complete the fixed panel after any valid reported timing failure; do not run CPU-heavy work
concurrently. If strength fails, skip packaging/process readiness rather than claim a pass.

## Reproduction and evidence

After final compilation, tests and replay validation:

```powershell
python tools/run_route_dependencies_experiment.py --preflight
python tools/run_route_dependencies_experiment.py --run
```

The runner refuses existing preflight/run markers, freezes identities and existing submission
files, checks freshness and all complete stages with `practical_gate.py`, and writes durable
outcomes to `docs/evidence/route-dependencies-v1.json`. Results are recorded separately in
BOTS/DEVLOG after completion; this predeclared protocol remains unchanged during evaluation.
