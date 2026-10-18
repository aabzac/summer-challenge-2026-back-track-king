# Incremental ink strength: retained boss-cache assessment

Read-only assessment, 2026-10-18. The current experiment is the separately
declared local 36 ms comparison; this note creates no games and makes no claim
about the current live boss, account, session or league.

The correct control is `current?NOISE=0.1,USE_BATCHED_REFRESH=true`, with
incremental refresh OFF. Its retained source SHA-256 is
`2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a`,
build `c6b5ed795e426be03fd0bc976a6ca13302dfa17341b2c6275a59133d23ae5f03`.
The completed v2 batched36 arm has 10 wins and 30 losses, as independently
recorded in the [budget40 follow-up](v09-boss-budget40-followup-v1-result.md).
The cached v09 control's 11/40 is a different bot and cannot supply this
comparison's control threshold.

The old batched pool is unsuitable for a new confirmation. Its maps were
exposed to [outcome-guided loss diagnosis](v09-batched-loss-diagnosis-v1.md),
whose recorded rule requires a refreshed boss control pool for a subsequent
candidate. The follow-up also labels reuse as regression evidence, rather
than fresh confirmation. In addition, the ordinary frozen `boss_cached.py`
reader rejects that completed v2 plan because slot 29 used a separately
authorized second attempt: `Cached-control mode does not authorize replacement
play attempts`. This was checked locally without network calls. Keep the
amended historical outcomes and the stricter original reader unchanged.

If the local comparison passes and a subsequent boss experiment is requested,
freeze a new 40-map schedule and both exact artifacts before any new outcome.
Collect 40 batched36 control games, verify all individual canonical results,
then collect exactly 40 incremental-refresh candidate games on the same maps,
seeds and balanced seats. Candidate points must be at least the new control
points; wins count one and draws one half. Check freshness through the shared
core against prior local and boss maps. Preserve technical failures, refusals
and uncertain creations without automatic replacement or retries.

Retain the user's pacing: 20 seconds between ordinary games and at least
300 seconds from completion to the next request at global games 21, 41 and
61. Five-minute pauses did not prevent an earlier play-limit refusal and
do not establish the service's quota or reset time. Use one sequential session.
Before any future play, verify the live owner, puzzle, boss and league and
privately preserve the current IDE source. The last recorded observation was
boss 6767608, league divisions 6/3/0, on October 16; it is historical metadata.

The existing new-control-then-cache mechanism and cooldown guard can be used
through a new experiment-specific adapter. Do not edit historical tools or
reuse the old amended plan as an ordinary unamended cache. A pass relative to
batched36 would establish the incremental effect on that base; it would not
overturn batching's prior result against selected v09. No live boss call,
arena upload, default change, promotion or push belongs to the current local
experiment.
