# Bot names and retained experiments

One machine-readable catalog: `src/main/resources/btk/bot/catalog.json`. `BotCatalog` supplies
labels to the CLI, benchmark reports, suite index and new local replay metadata. Commands still
accept the original ids; the labels do not rename frozen Java classes or change strategy behavior.

```powershell
.\gradlew.bat botCatalog
.\gradlew.bat botCatalog "-Pbot=exp-reply-rerank"
.\gradlew.bat run "-Pbot0=v05" "-Pbot1=exp-income-guard" "-Pseed=40100"
.\gradlew.bat bench "-Pbot1=exp-income-guard" "-Pbot2=v05" "-Ppool=bench/pools/development-v1.json"
```

## Three roles, not one ranking

- **Versions**: v00 Autoplace, v01 Greedy Routes, v02 Route Evaluation, v03 Risk Aware,
  v04 Network Extension, v05 Disrupt Reserve, v06 Simultaneous Rollout,
  v07 Network After Base, **v08 Protected Opening (selected)**.
  `selected` identifies the chosen policy, not proof
  of the exact arena upload or process-timing readiness. Historical versions remain reference
  opponents; their numbers are a development history, not an ordering against every possible bot.
- **Local references**: pressure-v1 Sabotage First is an independent strategy intended to broaden
  behavioral coverage, not an arena promotion. leader-v1 Leader Style is a fast heuristic
  calibrated on the arena leader (Indrill; see [indrill-study-v1.md](indrill-study-v1.md)): full
  paint every turn, a sticky DISRUPT on the opponent's unfinished construction, and a protected
  opening. Its constants are frozen and never retuned. It reproduces the leader's relentless
  sabotage meta (100% accepted disruption vs v08's ~62%), not the leader's arena strength, so it
  is a screening opponent, not a strength benchmark or a claim to reproduce the player.
- **Experiments**: named, reproducible configurations of the shared experimental engine. A failed
  promotion gate does not make them useless as opponents. Their status is `not_promoted`.

`current - Working Copy` and `main - Submission Wrapper` are explicitly **mutable** working
entries. In particular, `main` is not a label for whichever Player.java happens to exist on disk.

## Retained experiment recipes

All six start from the complete neutral/v05-configuration snapshot at `218457a`, with unrelated
experiments off. Only the following recipe fields are changed:

- `exp-income-guard - Income Guard`: `GUARD_BUILD_INCOME=true`; rejects affordable builds that
  reduce known-state income difference. Historical screen: 48.1% vs v05 over 428 games.
- `exp-build-direction - Build Direction`: `CHOOSE_BUILD_DIRECTION=true`; chooses which end of
  a route to build first. Historical screen: 52.3% over 428 games, not conclusive.
- `exp-early-disrupt - Early Disrupt`: `EARLY_DISRUPT_FALLBACK=true`; prepares a ready-to-ink
  sabotage before route search. Historical screen: 49.8% over 428 games.
- `exp-reply-rerank - Reply Rerank`: `USE_REPLY_RERANK=true,BUILD_BEAM_WIDTH=4,OPP_REPLY_WIDTH=2`;
  ranks build prefixes against predicted simultaneous replies. Historical screen: 46.5% over
  428 games. This id is K4/R2, not the later K2/R1 or K4/R1 diagnostic variants.
- `exp-interruptible-vuln - Interruptible Evaluation`: `INTERRUPT_VULN_EVAL=true`; discards
  incomplete nested evaluations at the build cutoff. Historical screen: 51.3% over 300 games.
- `exp-disrupt-delay065 - Delayed Sabotage Discount`: `DISRUPT_DELAY_FACTOR=0.65`; discounts
  targets requiring additional hits. Historical screen: 49.3% over 300 games.

These percentages describe **historical screens**, not a new evaluation of this catalog snapshot.
They used different map sets and code revisions, and must not be ranked against one another.
Full W/L/D, intervals, controls and decisions remain in BOTS/DEVLOG; the catalog points to the
corresponding historical commits. No rejected flag is enabled in the default submission.

## Reproducibility without six copies of EvalBot

`experiment-engine-v1.json` records source revision `218457a9b6ce72a9e06c5b6a9102112a6c7e25e2`,
all 46 effective Params fields, and SHA-256 identities for the 17 compiled classes comprising
current EvalBot, Bot/Params/Timer and shared core (including nested classes). It describes one
common retained engine, not six historical executable artifacts.

Before creating a named experiment, the factory verifies the actual implementation bytes against
this manifest and overlays the recipe on a fresh complete parameter snapshot. Verification is
cached per JVM. A missing field, unknown id/parameter or implementation mismatch fails closed.
The guard never reads potentially stale workspace source in place of loaded class bytes.

Since the joint-action experiment, `experiment-engine-v1-classes.json` preserves the ORIGINAL
compiled Params/EvalBot bytes as base64 (Java 17.0.12), one shared archive for all six recipes.
`RetainedEngine` child-loads ONLY those two allowlisted classes. Bot, Timer and core still come
from the normal classpath and must pass the original dependency hashes; this is not an excuse
to run an old strategy against silently changed rules. Both archived and shared bytecode are
checked against the unchanged manifest; ids, fingerprints, preset hashes and original 46-field
configurations are preserved. New flags are rejected on old ids.

The archived EvalBot implements the shared Bot interface directly: no serialization/reflection
per move or copied rules engine. Reflection is used only to construct its private Params instance;
diagnostic tools inspect the actual class instance, not the new current EvalBot. The mutable
current parameter bag may expose newer fields, but old experiments neither accept nor report
them. The archive and loader never enter Player.java. Source remains recoverable from the
manifest's source revision; bytecode preservation avoids requiring the same compiler to rerun
old recipes. Hardware/JIT/run order are still not preserved.

The bytecode guard is intentionally conservative: even a compiler change, debug information or
harmless source-line movement can invalidate a fingerprint. It is not a semantic-equivalence test.
The source revision plus recorded configuration explains what to restore; a name alone is not
an archived JAR and cannot guarantee identical time-limited choices across hardware/JIT/GC.
Map generator, referee, timing profile and runtime identity remain separate benchmark provenance.

If the engine changes, **do not refresh an old manifest under the same experiment ids**. Either
restore the recorded implementation/compiler or create a new engine snapshot and new ids (for
example `exp-income-guard-r2`) with fresh evidence. If simultaneous comparisons against both
engine revisions become useful, archive one shared engine implementation, not one copy per flag.
The catalog does not automatically check out old commits or archive an entire execution
environment. The v1 archive preserves only the two evolving classes, not a standalone JAR.

Explicit query overrides remain supported, e.g. `exp-income-guard?NOISE=0.2`. Reports retain the
full spec, complete resolved parameters, baseline preset hash and an `explicitOverrides` marker;
an override is an ablation, not evidence about the unmodified recorded preset. Raw `current?…`
continues to mean a live exploratory configuration and deliberately has no immutable-engine claim.

## Reports, replay compatibility and panel selection

Benchmark schema 3 keeps `a`/`b` and `paramsA`/`paramsB` unchanged and adds `botA`/`botB` identity
objects. Suite indices retain original opponent ids and add identities. Historical JSON files are
not rewritten. Display names are metadata, never the matching key for statistical comparisons.

New LocalRunner replays retain exact input specs in `meta.bots` and record optional `meta.botDetails`
with labels, categories and experiment identity. The viewer prefers those recorded labels. For
older **local** replays it can read the same catalog over HTTP and label recognized ids; if the
catalog cannot load, it keeps original ids. Unknown names and all CG player nicknames are unchanged.
Presentation uses plain text, not HTML supplied by replay metadata.

The default opponent panel is unchanged. Experiments are available to `bench`, `run` and optional
custom panel manifests; adding every tiny variant to every benchmark would overweight one family.
Keep a small varied panel, then select diagnostic experiment opponents for the hypothesis under test.
Experiments are not accepted by `submit -PsubmitVersion=...`; arena promotion still requires the
existing version/confirmation/process-check workflow.

Checks: `gradlew test`, `node visualizer/bot-labels.test.js`; manual browser check with a local
version-vs-experiment replay. Catalog work does not fix the previously recorded process timeout.
