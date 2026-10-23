# D against the live boss — v5 step (c), session 1 (`stage2-d-live-boss-v1`, 2026-10-23)

## Why now

D (`stage2-compute-v1`, the arena entry since 2026-10-21, agent 6807052; Silver #2 of 712 on the
morning of 2026-10-23) has passed the same-map screens of the chain v2 → B → C → D and the strict
process check; the "spend the dividend" and cold-JVM lines are closed without a change. The one
measurement the policy still requires before any promotion talk, and the one that addresses the
arena directly (the proxy cannot explain the 3-8 on 30×20), is the live boss series. The user chose
it ("босс").

## Design (predeclared before the first request; plan created with no network calls)

- Tool: `tools/boss_batch.py` (fixed, resumable plan; intent persisted before each request; no
  automatic retry of an unknown play), imported IDE session `build/cg-boss/private/session.json`
  (ignored, not read here), boss **6767608** (Silver Boss 4, validated from each canonical replay;
  request uses `-2`).
- **Matched design, 40 maps**: each map played once by each build in the same prebalanced seat,
  AB/BA order balanced — **80 API games**: candidate = D's exact arena artifact
  `build/submit-stage2-d` (source sha `0e4050c4…`, strict process check 8/8), control = the selected
  version **v09** (`build/cg-boss/artifacts/v09`, sha `df504a0d…`, its own process check), both in
  the **same session**. Plan `build/cg-boss/stage2-d-v09-boss-40b/plan.json`, id `7102e896…`,
  schedule seed 2026102301, bound to the puzzle-IDE session of 2026-10-18
  (`private/v09-ink45-boss-v1/session.json`, the one every live series since 2026-10-03 used) and
  run through the authenticated-owner lookup adapter (`boss_owner_lookup.py batch`, as all of them).
  A first plan on the default `private/session.json` (id `fe73e8cc…`) was refused at its first
  request with HTTP 422 / API 270 "It's too late to connect to the challenge" — that file is the
  September *contest* IDE session, closed since the contest ended; no game was created, the
  intent and error are retained in `build/cg-boss/stage2-d-v09-boss-40/0001/`.
- Why 40 maps and not v5's 80 candidate games: the cached v09 controls all predate the boss
  metadata change of 2026-10-18 (policy: historical only), so both halves of step (c) need
  same-session controls; 80 API games is the envelope that completed before (2026-10-15), while
  the 2026-10-16 run hit the service limit near its 70th request despite 300-second gaps. This
  session is therefore **step (c), part 1 of 2**: 40 paired maps now; a second 40-map session on
  another day completes the 80 candidate games the policy asks for, decided on all 80.
- Launch (the session's own machine, because the runner reads the private IDE session):

  ```
  bash tools/run_boss_paced.sh build/cg-boss/stage2-d-v09-boss-40b/plan.json build/cg-boss/private/v09-ink45-boss-v1/session.json 20 300 20 build/cg-boss/private/v09-ink45-boss-v1/start-session.json 0722a2fe30a43359747729078cfa150d027fc9c6fc7a9e3fbb102210563accba 1172955
  ```

  (bootstrap = the pinned `startTestSession` response of that session, user id = aabzac's public
  CodinGame id; rerunning the same command resumes at the first unplayed slot.)
- Pacing: `tools/run_boss_paced.sh` — chunks of 20 games, ≥ 300 s from the completion of global
  game 20/40/60 to the next request intent (the user's standing request), 20 s between games; an
  API error stops the run and is reported, never retried blindly (`docs/boss-api.md`).
- Reading, fixed now: draws count half; **pass for this session = D ≥ v09 + 5 pp over the 40
  paired maps**; the policy decision on step (c) is taken on the pooled 80 after session 2 (one
  predeclared extension to 120 allowed by v5, decided on all 120). The runner's `report` gives
  the paired discordance test as a diagnostic. Engine validation of every replay, build-marker
  checks, zero technical failures, and the exact schedule are preconditions, as in every live series.
- No arena upload, no promotion, no "selected version" change follows from this file alone.

## Results

_(filled when the session finishes)_
