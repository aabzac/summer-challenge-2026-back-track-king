# repo-cleanup-v1 — smaller commits, a test suite of live code (2026-10-27)

**Ask (user, 2026-10-27):** too many files get committed; are they all needed? Are the failing tests needed, and should the
suite keep only what checks the current or still-relevant strategies? The audit and plan were approved as "1, 3 да, 2 нет,
4 позже": archives out of the index, tests tiers 1 and 2; no history rewrite; closed research lines later.

## What weighed what (before)

| | files | in a fresh clone | in the working tree |
|---|---|---|---|
| `docs/evidence` archives (59 zip + 1 tar.gz, added 2026-09-19..10-18) | 60 | ≈ 410 MB of 445 MB | 481 MB |
| other evidence (json, txt, csv, 8 csv.gz) | ≈ 1 200 | ≈ 31 MB | ≈ 185 MB |
| code, tests, tools, docs with all their history | ≈ 1 000 | ≈ 4 MB | ≈ 9 MB |

Clone sizes are `git pack-objects` repacks of all history with and without the archives. Every commit also rewrote
1.1 MB of journals (DEVLOG 711 KB, BOTS 199 KB, PLAN 137 KB, STATUS 37 KB).

## What changed

1. **Archives and binary exports leave the index** (`540b9a1`). `docs/evidence/**/*.{zip,tar.gz,tgz,csv.gz}` and
   `.claude/settings.local.json` are gitignored; 68 files (512.7 MB) were untracked, not deleted: they stay on the author's
   disk and in history. `docs/evidence/untracked-evidence-2026-10-27.json` lists path, size, sha256, git blob and the commit
   that added each (`git show <addedIn>:<path> > <path>` restores one). The two tests that read an archive now read the
   extracted `Player.java` under `src/test/resources/historical/` (sha256 pinned, as recorded in the evidence manifests).
   History is not rewritten: the SHAs are cited throughout the evidence.
2. **Journals keep their live part** (`9833706`). DEVLOG entries before 2026-10-20, PLAN sections before Stage 0 and the
   BOTS per-experiment sections up to 2026-10-06 moved verbatim to `docs/archive/` (relative links rebased); live files
   125 / 27 / 74 KB. Lossless: archive + live parts concatenate to the original non-blank lines.
3. **Tier 1 — the 25 red tests** (`9538023`). The six `exp-*` recipes (rejected on v05) ran on an archived EvalBot engine
   (`RetainedEngine`, `experiment-engine-v1*.json`) whose manifest pins `btk/core/Pathing.class`; fast cheapest path
   changed that class on 2026-10-18, so the recipes could not run and 19 tests stayed red. Removed with the catalog entries,
   the BotCatalog/BotFactory experiment paths, the reply-study capture/analysis and `JointReplyStudy`/`JointActionCheck`
   (`ReplyStudy`/`ReplyProbe` keep the helpers other tools use). `pduhard-beam-v1` was never registered (6 red tests):
   bot and test removed, its doc and evidence stay. 863 tests / 25 red → 831 / 0.
4. **Tier 2 — prune-v2** (`7c66eda`). The experiments of v12's time that were rejected or parked leave `current`:
   `USE_COLLISION_AVOIDANCE` (collision-avoidance-v1), `USE_CORRIDOR_ROUTES` (K1), `USE_QUICK_ALWAYS` / `DELAY_POWER` (Q),
   `BOSS_OPP_HOLD_MARGIN` (opp-hold-v1), `USE_BOSS_NO_CLOSE` (boss-v4), `USE_ROUTE_COMMIT` (C10), `USE_BOSS_THREAT_MODEL`
   (stage4-threat-model-v1). Their merger blocks are cut from `current/RolloutBot.java` (−173 lines) and
   `current/BossSabotage.java` (−10); `BotFactory` and the RolloutBot constructor refuse them on current; the frozen copies
   still carry the code and run them (`v13?FLAG=1`, the threat model also on v11/v12). `Params` fields and the
   `FileMerger` blocks stay for those copies. Their 12 test classes and the 12 `btk.bot.v10` classes that tested the
   prune-v1 flags in the frozen v10 code are removed; `PruneV2Test` covers the refusals and a v13 run.
   **Check:** the default artifact is byte-identical to `build/submit-v13/Player.java` (94,816 chars, sha `3d3e861d…`,
   pinned by `V13SubmissionTest`), so the shipped bot, its strict procCheck and the arena upload are unchanged.
5. **Rules** — AGENTS.md rule 8 says what an experiment commits (evidence JSON/TXT yes, archives and binary exports never,
   files over 1 MB need a reason, closed periods move to `docs/archive/`); rule 9: tests follow the code they test.

## Not done (user decision "4 позже")

Tier 3: the closed research lines (neural v1–v5/v7, spatial-ppo v1–v10, indrill, leader, pressure, the v05-era EvalBot flags,
v09-era probe tools) with their code and `tools/{neural,spatial,indrill}` — about 330 more tests; neural-v6 stays as a
screening opponent. Entangled (shared `CgDisruptStudy`, FileMerger special cases, packaging tests that use neural-v1 and
spatial-ppo-v10 as controls), so a separate PR.
