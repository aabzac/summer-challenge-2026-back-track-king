# Selection policy v5 — proxy screen, non-inferiority, larger live sample (adopted 2026-10-21; step 1a replication added 2026-10-26; step 3 = live filter + arena uploads since 2026-10-27)

Status: **adopted 2026-10-21** (user decision), rule 3 of `AGENTS.md` updated. The v4 policy, its
tools and all historical verdicts stay as recorded. Nothing in this document changes a strategy.

## Why v4 stopped working

1. The v4 local gate is a self-play mirror (candidate vs v09). Mirror games last ~90 turns with an
   accepted disruption on 64–67 % of turns; boss/arena games last 47–57 turns with 99 % disruption
   and the boss inks a region in four consecutive hits. Every mechanism motivated by arena data
   (live-target, track-value, always-disrupt, defense-priority, all-cell-risk) failed the mirror
   for a structural reason — the mirror never produces the situation the mechanism targets — and
   v4 forbids rescuing a local failure with boss results. Conversely, mirror PASS_1000 results
   (neural-v2/v3/v5/v6/v7, batched refresh 56.65 %) did not transfer to the boss.
2. The 40-game boss stage has a standard error of ±7.5 pp. The unchanged v09 control scored
   17, 11, 15 and 11 of 40 over four weeks. Decisions were made inside the noise.
3. "One run, no tuning" is correct for confirming a fixed candidate; applied to a new bot's zeroth
   iteration (indrill-forum 4.7 %, pduhard-beam 0.7 %, both leaving paint unspent) it measures
   nothing about the idea.

## v5 rule

Prerequisites, unchanged from v4: engine fidelity (`cgValidate`), zero bot errors, exact-artifact
strict process check, source-size check.

1. **Proxy screen** — 300 paired games / 150 fresh maps, candidate vs the current screening
   opponent (see "Proxy validity" below; `docs/boss-proxy-v1.md`), CG 486/36 ms, two workers. Continue only if the candidate's score
   is **at least the control's proxy score + 3 pp** (control = selected v09 on the same maps,
   seeds and seats, played in the same run). Draws = half.
1a. **Replication** (amended 2026-10-26, user decision after `docs/boss-v4.md` §5). An arm that passes step 1 is
   re-screened on a **fresh pool** — 300 paired games / 150 new maps against the same screening opponent, with
   `current` as the control on the same maps, seeds and seats in the same run (`tools/boss_proxy/run_replication.sh`).
   Continue only if the replication's paired delta is **positive** and the **pooled** delta over both pools is
   **≥ +3 pp** (`tools/boss_proxy/pooled_delta.py`). Why: a 300-game step 1 has a ±6–7 pp interval per arm, and taking
   the best of two arms through a +3 pp gate passes a null candidate about one screen in three; both construction
   candidates of 2026-10-26 passed step 1 at +6.5 / +6.3 pp, fell to −1.7 / −6.0 pp on a fresh pool (pooled +2.4 /
   +0.2) and lost their live sessions (−15 / −5 pp). The live step is the scarce resource (≈ 80 API games per
   candidate, a daily limit); the replication costs ≈ 10 minutes.
2. **Non-inferiority vs the selected version** — 300 paired games / 150 fresh maps, candidate
   vs v09. Continue only if the score is **≥ 48 %**. (The mirror is a regression guard, not the
   objective.)
3. **Live filter + arena (amended 2026-10-27, `docs/arena-uploads.md`).** One 40-game live boss session on the
   cached control's maps/seeds/seats with a same-session control is a cheap filter: continue at a paired delta ≥ 0.
   Then the candidate is uploaded to the arena twice (~120 placement games each), each placement watched with
   `tools/arena_uploads.py watch`; it is rolled back when its mean placement score is below the selected version's
   uploads in the same league by more than M (2 × the upload-to-upload SD of one artifact, ≈ 1.0 CG point from
   v13's Silver and Gold pairs, 2026-10-27/28) and its
   performance rating is lower by more than 2 SE, and promoted otherwise. The live-confirmation rule below is
   historical (it applied to the v10–v13 promotions, each made after its first session).
   *Historical:* **Live confirmation** — **80 paired games** against the live boss: 40 new candidate games on
   the cached control's 40 maps/seeds/seats **and** 40 more on a second fixed pool, with a fresh
   40-game v09 control on that second pool played in the same session (boss metadata may change).
   Pass when the candidate's pooled score over 80 games is **≥ control + 5 pp**; otherwise an
   optional extension to 120 games is allowed once, predeclared, and the decision is made on the
   full 120. No other extensions, retries or pooling with historical games.
4. Promotion = steps 1, 1a, 2 and 3 (the live filter and the arena uploads). A failure at any step stops the candidate;
   the evidence is retained.
5. **Degenerate proxy** (amended 2026-10-21). When the candidate is itself the screening opponent
   (as `boss-sabotage-v1` was), or when no proxy is currently valid, step 1 is played against
   **`neural-v6`** (the version with the best live boss sample and the arena-like 100 % disrupt
   profile) with the same rule — candidate ≥ control + 3 pp on the same maps — and step 2 is the
   v4 pooled 1000-game run against the selected version (blocks 300/+200/+500, futility stop
   below 50 % at 300 or 500, pass at ≥ 55 %); step 3 is unchanged. The original clause let
   `boss-sabotage-v1` reach the arena on a 67 % mirror result that neural-v6 (58 % against it)
   had already contradicted.
6. **Cold-JVM timing.** Any new per-turn step that is not bounded by the turn timer is measured
   in a fresh JVM per game (as CodinGame runs it) on 30×20 maps before step 1; the hot-JIT
   local bench under-reports such costs by 2–3× (`BossChooserTiming`: 5–10 ms warm vs 16–26 ms
   cold for the `BossSabotage` chooser). The strict exact-artifact `procCheck` remains the gate.

New heuristic bots (not flags on `current`) get an explicit, predeclared **tuning budget** —
e.g. up to five iterations of 100 games on development maps — before entering step 1; the
tuning games and the final configuration are recorded, and step 1 then runs on fresh maps.

## Proxy validity is a precondition

The proxy screen is only meaningful while the screening opponent reproduces the boss meta
against the selected version on fresh maps: game length, accepted-disruption rate, hit-class mix
at commitment, closure share, and a win rate within 15 pp of the live boss's. The current
screening opponent is `current?USE_BOSS_SABOTAGE=1` (`docs/boss-proxy-v1.md` §6: disrupt 99 %,
hold 71 %, commits 50/43/7, 77-turn games, 69 % closures, 54–62 % vs v09 against the boss's
67.5 %). `boss-v1` is a behavioural reference only. Re-run the fingerprint whenever the proxy
or the live boss changes; if validation fails, step 1 is suspended and the degenerate-proxy
clause applies.

## Reporting

Each candidate: one summary with the stage results (1, 1a with the pooled delta, 2, 3 — the live session and the
arena uploads' placement scores / performance ratings from `tools/arena_uploads.py compare`), the control's proxy score, map pools,
seeds, artifact hashes, and the proxy validation id in force. BOTS.md gets one row; DEVLOG one
entry; STATUS.md the current verdict.
