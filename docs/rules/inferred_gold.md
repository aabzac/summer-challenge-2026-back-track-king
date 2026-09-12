# Rules inferred from Gold replays (not from a statement)

Source: 107 Gold-league games of the top 15 (2026-09-13), replayed through `btk.core` by
`gradlew cgValidate`. Status: **107/107 games reproduced exactly** (every placement, ink event,
per-turn score, connection path, game-end turn and final score). Replace with the official text
of `docs/rules/bronze.md` etc. once available; keep this file as the record of what was verified.

## DISRUPT

- Syntax: `DISRUPT <regionId>` or `DISRUPT <x> <y>` (the cell's region). Both are used by top bots.
- Costs no paint; combines freely with 3 paint points of `PLACE_TRACKS` in the same turn.
- At most one per turn (nobody ever sends two; treated as invalid locally).
- Never targets a region containing a town (0 cases in ~11 600 disrupts; treated as invalid locally).
- Effect: region instability +1. Both players' disrupts add up (2+2, 3+1 observed).

## Ink

- When a region's instability reaches **4** it is inked at the end of that turn's disrupt phase
  (before connections/scoring). 43 cases reached 5 when both players hit 3 -> 5 in one turn.
- Inking removes **all tracks** in the region (any owner) — viewer event `5` lists exactly the
  cells that had tracks.
- Inked regions stay inked for the rest of the game (no region is inked twice; no track is ever
  placed on an inked cell afterwards). Inked cells are impassable for new paths.
- Protocol: per-cell `instability` = region instability, `inked` = 1 for cells of inked regions.

## Turn order (as reconstructed)

1. Both command lines expanded; `PLACE_TRACKS` applied simultaneously (same cell -> neutral owner 2).
2. Disrupts applied; regions with instability >= 4 inked (tracks removed).
3. Active connections recomputed (shortest path, N/E/S/W tie-break from the requesting town).
4. Scoring: +1 per owned track per active connection to each player.

## Game end

- Hard limit 100 turns (10/107 games).
- Otherwise the game ends at the end of the turn after which **no desired pair can be connected
  through non-inked cells any more** (flood fill ignoring tracks). Verified on 97/97 early-ending
  games. Typical length 31-75 turns; about half of all regions are inked by then.

## Not yet seen

- POIs (`totalPois` = 0 in metadata of these games), `AUTOBUILD`, effects of `instability` short of
  inking, whether a disrupt on a town region is refused or ignored, whether an over-budget command
  line is a loss or trimmed.
