# v09 batched refresh: refreshed boss comparison

Prospective continuation, 2026-10-15. The user requests the remaining 40 candidate
games, verification of the v09 control cache, and a decision on enabling batching.
The completed local experiment is [v09-batched-refresh-v1](v09-batched-refresh-v1.md):
535W/402L/63D over 1000 games, 56.65%, practical-v4 local pass. Its candidate,
parameters, process evidence and outcomes remain frozen; no new local strength run.

## Cache decision before play

The October 4 v09 cache is technically intact: 40 unique maps and game IDs,
20 games in each seat, 15W/25L, exact selected v09 source and parameters.
Age alone does not invalidate it. However, all 40 games were inspected in the
continuation diagnostic, and the subsequent optimization was selected and checked
using that development material. The cache policy requires refresh after this
outcome-guided development cycle. Preserve the old cache and its verdicts.

Therefore collect a fresh control cache of exactly 40 games, then play exactly
40 new candidate games on those same seeds, maps and seats. This is 80 IDE test
games in total, with no arena submission. The requested cache verification and
completion authorize the necessary refresh; it is not an extension of a failure.
The frozen candidate is unchanged before and after seeing control outcomes.

## Frozen comparison and decision

- Candidate: `current?NOISE=0.1,USE_BATCHED_REFRESH=true`, exact source SHA-256:
  `2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a`.
  Directory `build/analysis/v09-batched-refresh-v1/artifact`; strict process
  reports `process-1.json` through `process-4.json`, all eight games passed.
- Control: `v09?NOISE=0.1`, directory
  `build/analysis/v09-batched-refresh-v1/artifact-v09`, source SHA-256
  `aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac`.
  Reuse the exact-source/class strict control process panel from
  `build/analysis/neural-v7/control-process/`; no changed control executable.
- Both full manifest parameter maps must equal their respective local block
  parameter maps. The missed-deadline correction stays OFF.
- Fresh control schedule seed: `2026101509`, 40 distinct maps, 20 P0 and 20 P1.
  Freeze the schedule, map exclusions and shared-core map identities before play.
  Reject prior/duplicate maps; no reroll or replacement.
- Use existing `boss_batch.py`, `boss_cached.py` and authenticated-owner replay
  lookup. Preserve current IDE source privately before any play. Read the live
  boss and league; expected historical boss is 6767608, league 6/3/0. Changed
  metadata is reviewed before any game, never guessed from the sentinel -2.
- Run sequentially with 20-second spacing. Every request has a durable intent.
  No tuning, outcome-based early stop, retries of uncertain creation, replacement
  losses, extra candidate games or automatic protocol amendment.
- Verify all canonical replays, maps, seeds, seats, build markers, boss/league,
  terminal results and technical failures. Run the actual replays through the
  shared engine. A known game ID permits read-only retrieval, never another play.
- Boss pass: candidate points >= refreshed control points, wins=1, draws=0.5,
  losses=0; equality passes. Errors or incomplete evidence block a clean pass.
  Apply unchanged practical-v4 to the retained three local blocks and this stage.

## Limits and outcome handling

This is the project's practical selection filter, not a statistical guarantee
of boss noninferiority or arena rank. A fixed boss ID cannot prove its hidden
implementation or execution conditions stayed unchanged. Record collection dates.
The old local native-call outlier of 75 ms remains unresolved: the aggregate
counter includes first turns and has no turn number. The strict process sample
passed; neither result guarantees every future response fits 50 ms.

Record the full result and recommendation, preserve all attempts and evidence,
update BOTS/PLAN/DEVLOG and commit locally. Do not silently change defaults or
publish to the arena: freezing and checking the final promoted packaging is a
separate implementation step after the decision requested here.
