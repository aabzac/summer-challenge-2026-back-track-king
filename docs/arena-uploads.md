# Arena uploads: measure every upload, average across uploads, roll back when clearly worse (2026-10-27)

Status: **adopted 2026-10-27**. This was the user's proposal, agreed in the project chat after v13's upload. It amends
step 3 of policy v5 (`docs/benchmarking-v5.md`, AGENTS.md rule 3). The tool is `tools/arena_uploads.py`
(`tools/test_arena_uploads.py`).

## Why

- The arena is the objective (AGENTS.md: climb the arena). Every Submit creates a new agent that plays about **120
  placement games** against the real field. These games cost no boss-API quota; a live boss session costs 80 of the
  ~80–160 API games available per day.
- The live boss step is thin. A 40-game session has ±7.5 pp per arm, and every promotion since 2026-10-23 (v10–v13)
  was made after one session, skipping the second one that v5 requires. One upload gives three times that sample,
  against the population we are ranked in.
- A single upload is still noisy. The CG score at the end of placement depends on the order and strength of the
  opponents the matchmaker serves. So the same artifact is uploaded twice, and decisions are made on the mean of
  the uploads. The spread between two uploads of one artifact is the noise margin.

## Procedure (replaces v5 step 3's 80/120-game live requirement)

1. Steps 1, 1a and 2 of policy v5 are unchanged (proxy screen, replication, non-inferiority).
2. **Live boss session 1 is a cheap filter.** It uses 40 games on the cached control's maps, with the same-session
   control, as today. A candidate continues only if its paired delta is **≥ 0** (candidate wins ≥ control wins).
   Session 2 is no longer required. History: the passes were D +22.5, B +2.5, E1 +5.0 and B2 +7.5 pp; the stops were
   K1 −15, Q −5 and C10 −10 pp.
3. **Before every upload, fetch the live entry:** `python tools/arena_uploads.py fetch`. CodinGame lists the games
   of the **live agent only**. The v10–v12 agents return an empty list since they were replaced, so an upload that is
   not fetched before the next Submit keeps only the replays already downloaded.
4. **Upload the candidate.** The user submits `build/submit-<candidate>/Player.java` after its strict `procCheck`.
   Its first-turn marker reads `vNN (modified) | BTK_BUILD_V1 …`. After placement, run
   `python tools/arena_uploads.py fetch --version <candidate-id>`. Placement is over when the leaderboard reports
   `percentage` 100; the fetch records it. The first fetch after placement is the upload's **placement score**.
5. **Re-upload the same artifact** and fetch it after its placement (upload-2 of the same label).
6. **Decide on the means** with `python tools/arena_uploads.py compare <candidate-id> <selected>`:
   - **Roll back if the candidate is clearly worse.** That means its mean placement score is below the selected
     version's mean by more than the margin **M**, *and* its pooled performance rating is below the selected
     version's by more than two standard errors of the difference. Rolling back means re-uploading
     `build/submit-<selected>/Player.java` and fetching it as the selected version's next upload; each rollback adds
     to the noise sample.
   - **Early rollback:** if upload-1 alone is below the selected version's mean by more than 2 M, roll back without
     the second upload.
   - **Otherwise promote** as before: default flag, frozen copy, pins, `docs/vNN-promotion.md`. Then move the
     candidate's uploads to the new label with `python tools/arena_uploads.py relabel <candidate-id> vNN`; the
     configuration hash must match. Then upload the promoted default artifact. Its marker reads `vNN`, so it is a
     further upload of the same configuration.
   - Win rate, opponent median and the shared-opponent delta are reported as context. They depend on the field the
     matchmaker serves at that hour; the rating and the performance rating already account for it.
7. **M = 2 × the standard deviation of the placement score between uploads of one configuration** (`compare` prints
   it for every version with two or more placement scores). It is **provisionally 1.0 CG point** until v13's two
   uploads give the first measurement, and it is re-estimated as rollbacks and promotions add uploads.

The selected version's baseline is its own uploads. A version needs at least two placement scores before a
candidate is judged against it (v13: upload-1 done, upload-2 pending).

## The tool

```
python tools/arena_uploads.py fetch [--version LABEL] [--agent ID] [--no-replays]
python tools/arena_uploads.py import --version v12 --agent 6814073 [--score 29.40 --rank 316 --games 147 --complete]
python tools/arena_uploads.py compare [LABEL ...] [--out docs/evidence/arena-uploads-<date>.json]
python tools/arena_uploads.py relabel <candidate-id> vNN
```

- **Layout** (`replays/` is gitignored): `replays/arena/<label>/upload-N/upload.json` holds the agent, the marker
  (display name, build, config), the upload time and the rating snapshots (score, rank, percentage, division, games).
  `battles.json` has one row per game: result, opponent user/agent/pseudo, the opponent's arena score and its source,
  seat and map size. `games/<gameId>.json` holds the CG replays in `replays/cg` format, so
  `gradlew cgAnalyze -Ppath=replays/arena/<label>/upload-N/games -Pagent=<id>` reads them.
- **Labels.** A clean marker (`v13 | …`) names the label itself, and a different `--version` is refused. An
  experiment (`(modified)`) or a marker without a display name (builds before 2026-10-25) needs `--version`. All
  uploads under one label must share one configuration hash. A new agent gets the next upload number.
- **API cost** of a fetch: 1 leaderboard + 1 last-battles request (the results of all the upload's games) + 1
  request per replay not already on disk. `replays/cg` is searched first. `--no-replays` keeps a fetch at 2–3
  requests, and `--max-requests` (default 200) caps a run. An interrupted fetch resumes. `import` and `compare`
  make no requests.
- **Opponent scores.** The leaderboard is read at fetch time when the opponent's agent is still live (the API lists
  the top 1,000); otherwise the score comes from the replay's `agents[].score`. Both are the opponent's score at
  download time, not at game time.
- **Performance rating** = the opponent arena score at which the upload would score 50 %, from a logistic fit of
  every result against the opponent's score. The scale is shared by all filed uploads (profile likelihood, 3.0–4.0
  CG points on the data so far), and the standard error comes from the Fisher information. It corrects the win rate
  for the field the matchmaker served.
- **Shared opponents** are matched by player. The delta is stratified by opponent, with weights
  1 / (1/n_a + 1/n_b), and its standard error is 0.5 / √Σw. It is printed for every pair of uploads of a version and
  for every pair of versions.

## First data (2026-10-27, `docs/evidence/arena-uploads-2026-10-27.json`)

v13 was fetched live (122 requests). The earlier uploads were imported from `replays/cg` without requests; for those
only the replays downloaded at the time exist, and their scores were noted in the DEVLOG during placement.

| upload | agent | placement score | games | W-L | win % | opp. median | perf. rating |
|---|---|---|---|---|---|---|---|
| v10/upload-1 (D, same config) | 6807052 | — | 50 | 30-20 | 60.0 | 27.64 | 29.18 ± 0.92 |
| v10/upload-2 | 6811139 | — (26.83 @50, placement) | 50 | 29-21 | 58.0 | 26.07 | 27.17 ± 0.90 |
| v11/upload-1 | 6813813 | — (25.36 @50, placement) | 52 | 36-16 | 69.2 | 23.53 | 25.85 ± 0.98 |
| v12/upload-1 | 6813957 | — (26.09 @50, placement) | 50 | 32-18 | 64.0 | 25.02 | 26.79 ± 0.93 |
| v12/upload-2 | 6814073 | **29.40** #316 @147 | 150 | 90-60 | 60.0 | 27.20 | 28.64 ± 0.55 |
| **v13/upload-1** | **6818214** | **29.23** #316 @120 | 120 | 75-45 | **62.5** | 28.20 | **28.99 ± 0.62** |

- v13's upload-1 placed at 29.23, rank 316. Its 120 games include 118 validated by `cgAnalyze`, all with 0 failed
  turns. Its placement score is 0.17 below v12's re-upload; its win rate is 2.5 pp higher against a field 1.0 point
  stronger, and its performance rating is 0.35 higher. Nothing is clearly worse, so v13 stays. The re-upload is
  now pending (upload-2).
- The 50-game imports are the first third of a placement, against weaker opponents (medians 23.5–27.6). Their
  performance ratings are comparable in principle, but their ±0.9 errors make them context only.
- Shared opponents so far: v12 vs v13 has 18 opponents, 46 / 65 games, and a delta of −6.6 ± 11.5 pp. The
  stratified head-to-head is too thin to decide anything at one upload each, and it is reported as context.
