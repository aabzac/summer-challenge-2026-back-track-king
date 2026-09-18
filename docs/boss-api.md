# Independent boss-game runner

`tools/boss_api.py` imports one browser session; `tools/boss_batch.py` runs a fixed
sequence directly against CodinGame. Python standard library only. After import,
the browser, Tampermonkey and `save_server.py` are not required for this runner.
The website's private API can change or expire the session; failures are explicit.

This uses IDE test games, not arena submissions. **TestSession/play saves the supplied
source in the website IDE.** The importer preserves the original source before any
API game; avoid editing that IDE in parallel with a batch. Selected local v07 and
the existing `build/submit` artifact are separate from these isolated exports.

Verified on 2026-09-18: **26 completed API games**, including six technical games
and 20 games from the fixed comparison. The comparison is **interrupted at 20/80**:
request 21 returned HTTP 422 without a recoverable game ID. The original error body
was not retained. Later read-only authorization checks and a separate technical
game succeeded; the cause of that earlier response remains unknown. The ten completed
map pairs contain candidate 3/10 wins and v07 2/10 wins, with no strength verdict.
See the [independent interruption audit](evidence/chords-boss-v1.interrupted.json).
The commands below document that frozen plan; rerunning it will stop at unresolved
slot 21 until its actual game ID can be recovered. Do not delete the intent or start
a replacement sample as if it were a continuation.

## One-time connection

In the logged-in Java IDE, open DevTools > Network, clear the request list and play
one game against the boss. Export the request log **with sensitive data** to
`build/cg-boss/private/session.har`. In Chrome/Edge, first enable Settings (F1) >
Preferences > Network > Allow to generate HAR with sensitive data. Then use the
export dropdown's **Export HAR (with sensitive data)**. Ordinary sanitized exports
remove the cookies and cannot authenticate the script. See the official
[Chrome Network reference](https://developer.chrome.com/docs/devtools/network/reference#save-all-as-har).

Keep the HAR and cookies local. `build/` is ignored by Git. Importing does not play:

```powershell
python tools/boss_api.py import-har --har build/cg-boss/private/session.har
python tools/boss_api.py inspect
```

The importer saves `private/ide-source-backup.java`, checks its SHA-256, then writes
`private/session.json` with credentials and the captured request template. It does
not print cookies, handles or source. If more than one play request was captured,
select its zero-based HAR entry using `--entry-index`. Imports preserve a different
existing source backup instead of overwriting it.

Only `https://www.codingame.com` receives these credentials. Redirects and automatic
HTTP retries are disabled. A refreshed HAR can renew an expired session; a running
plan still requires the same captured IDE/boss request template.

## Prepare exact builds

Both exports need their own successful strict process check; their source,
configuration, compiled classes and process report are frozen in the batch plan.
These checks establish packaging/protocol readiness, not strategy strength.

```powershell
.\gradlew.bat submit -PsubmitVersion=v07 -PsubmitOut=build/cg-boss/artifacts/v07
.\gradlew.bat procCheck -PsubmitOut=build/cg-boss/artifacts/v07 -Pbot=v06 -Pgames=4 -Pmap=replays/cg/boss -Pout=build/cg-boss/artifacts/v07/process-check.json
.\gradlew.bat submit "-PsubmitVersion=current?USE_ROLLOUT_NETWORK_CHORDS=1,USE_ROLLOUT_ROUTE_DEPENDENCIES=0" -PsubmitOut=build/cg-boss/artifacts/chords
.\gradlew.bat procCheck -PsubmitOut=build/cg-boss/artifacts/chords -Pbot=v07 -Pgames=4 -Pmap=replays/cg/boss -Pout=build/cg-boss/artifacts/chords/process-check.json
```

`submit` registers `Player.java` and its manifest in
`build/submission-registry/<buildId>/`. The first player output contains one
`MESSAGE BTK_BUILD_V1 strategy=... build=... config=...`. Canonical replays retain
stdout, whereas stderr is not reliably available there. The build ID hashes the
source with the marker declaration emptied; the manifest also hashes the final
exact source, so there is no self-hash cycle.

The marker is self-reported. Matching the retained source/configuration establishes
the mapping to a local artifact; it is not an independent attestation of remote
execution. The batch additionally records exactly which source it sent.

## Technical smoke game

The captured request can identify the default boss using `-2`. In that case, pin
its positive ID from `gameResult/findByGameId`, not from a guessed league number.
The observed Silver Boss 4 on 2026-09-18 is **6740207**; confirm again when changing
league or session. The runner preserves `-2` in requests but validates the positive
boss ID and league metadata in returned canonical replays.

```powershell
python tools/boss_batch.py plan --candidate build/cg-boss/artifacts/v07 --candidate-check build/cg-boss/artifacts/v07/process-check.json --games 1 --design independent --purpose smoke --boss-agent-id 6740207 --out build/cg-boss/smoke-v07
python tools/boss_batch.py run --plan build/cg-boss/smoke-v07/plan.json
```

Plan creation makes **no network calls**. The run creates exactly the scheduled
game, checks its seed, seat, boss, completed ranks, map and build marker, then saves
the exact canonical response. A technical game is excluded from the evaluation.

## Fixed candidate comparison

Recommended protocol: [40 games per build](boss-evaluation-protocol.md). If the
smoke checks confirm seed replay and both seats, use 40 matched map pairs. Each
distinct map is played once by each build, in the same prebalanced seat; AB/BA
execution order is balanced. This means **80 API games**, not 40 total games.

```powershell
python tools/boss_batch.py plan --candidate build/cg-boss/artifacts/chords --candidate-check build/cg-boss/artifacts/chords/process-check.json --control build/cg-boss/artifacts/v07 --control-check build/cg-boss/artifacts/v07/process-check.json --games 40 --design matched --purpose boss-screen --boss-agent-id 6740207 --seed 2026091840 --out build/cg-boss/chords-v07-boss-40
python tools/boss_batch.py run --plan build/cg-boss/chords-v07-boss-40/plan.json
python tools/boss_batch.py report --plan build/cg-boss/chords-v07-boss-40/plan.json
```

Use `--design independent` only if choosing that design **before** its first game;
each build then gets distinct seeds. Both modes detect duplicate game IDs, repeated
sampling maps, maps already present at plan creation, changed artifacts and mismatched
seeds/seats/bosses. The map fingerprint is SHA-256 of exact initial `global.graphics`
UTF-8 text (`cg-global-graphics-utf8-sha256-v1`), not the engine's separate map hash.

Do not extend a borderline sample, tune between games or replace losses. The report
computes a one-sided Fisher test for complete independent samples, or the exact
discordant-pair test for complete matched samples. Draws count as non-wins for this
binary test; W/L/D and half-win scores are also shown. Old 6/20 is historical context,
not a fixed known 30% probability. Neither this boss screen nor an overlapping
confidence interval changes the existing local promotion gate or its recorded
network-chords-v1 failure.

## Interruptions and recovery

Run the same `run --plan ...` command to resume. An OS lock prevents simultaneous
runners. Each slot's intent is flushed to disk **before** issuing play. Once an
intent exists, that slot never issues another automatic play, even after a timeout
or process crash. Received game IDs resume with read-only canonical retrieval.
Unexpected canonical responses are retained separately and can be fetched again.

If the request might have created a game but its response was lost, find the actual
game ID in the site's IDE/history/captured response and attach it:

```powershell
python tools/boss_batch.py recover --plan build/cg-boss/chords-v07-boss-40/plan.json --slot 7 --game-id ACTUAL_GAME_ID
python tools/boss_batch.py run --plan build/cg-boss/chords-v07-boss-40/plan.json
```

Do not invent an ID or delete intent files to force a retry. Recovery fetches and
checks the game against its scheduled map/seat/boss/build; it does not create a new
game. An unresolved creation failure leaves the sample interrupted. A completed
bot error/timeout is retained; missing expected provenance interrupts evaluation
instead of silently replacing the game. A mismatched build cannot be bypassed by
resuming. Saved outcomes are recomputed from canonical records during reporting.

HTTP error bodies are now retained (up to 64 KiB) only under ignored
`build/cg-boss/private/api-errors/<planId>/<slot>.bin`. Ordinary batch diagnostics
contain only the HTTP status, an optional restricted error code, and whether creation
is uncertain. Do not publish the private response body: it can contain session data.
An HTTP failure alone does not trigger another play, even when authorization is valid.

For the frozen chords comparison, export a full audit only after all 80 games exist.
An interruption has its own evidence file and never receives a significance verdict:

```powershell
python tools/diagnostics/boss_batch_audit.py --plan build/cg-boss/chords-v07-boss-40/plan.json --interrupted
python tools/diagnostics/boss_batch_gallery.py --plan build/cg-boss/chords-v07-boss-40/plan.json
```

Evidence exports are immutable; the interrupted export above already exists.
The gallery is `replays/rendered/cg/chords-boss-v1.html`, served by the usual local
visualizer server. Render canonical replays first using the commands below.

## Replay locations and manual captures

- Verified marker: `replays/cg/boss/<strategy>/<buildId>/<gameId>.json`.
- No matching marker: `replays/cg/boss/unknown/<gameId>.json`.
- Capture metadata: `replays/cg/boss/_metadata/<gameId>.json`.
- Raw noncanonical fallbacks: `replays/cg/boss/raw/unknown/`.
- Batch intent, raw API response and results: the chosen `build/cg-boss/<batch>/`.

All are local/ignored. Original replay bytes are immutable; conflicting captures
are rejected. Existing flat historical files stay at `replays/cg/boss/<gameId>.json`
with their original bytes and unknown automatic version attribution.

For browser/Tampermonkey captures, install the updated
`tools/codingame_replay_export.user.js` and restart `python tools/save_server.py`.
Its `/health` reports `protocolVersion: 2`. The API runner calls the same capture
helper directly, so it does not require that server. Java replay discovery recurses
through version/build directories, excludes raw/metadata and refuses duplicates.
Legacy missing `replays/cg/bot` paths resolve to `boss` for active Java tools;
historical standalone scripts and evidence are not rewritten.

```powershell
.\gradlew.bat cgValidate -Ppath=replays/cg/boss
.\gradlew.bat cgRender -Ppath=replays/cg/boss
```

The request shape was verified against the site's public
[IDE client](https://static.codingame.com/ide.f71b4ccb.js) and a real captured play
request. Authenticated online verification/results are recorded separately in DEVLOG.
