# Spatial v4 teacher corpus format

The fixed collection and learning recipe is in `docs/spatial-ppo-v4.md`. This document specifies the new exporter/reader interface; existing core, observation and runtime implementations remain the source of game rules and features.

Invoke `btk.tools.spatial.SpatialTeacherExport smoke|corpus <fresh-output-directory> <exclusions.json>` through the prepared Java runtime. The output directory must not exist. The exclusion JSON must contain a nonempty `gamesPerMap` object keyed by canonical map hash. Before creating or invoking any policy, the exporter generates every scheduled map and rejects any hash already excluded or repeated within this schedule. A rejection preserves a failed manifest; no replacement map is generated.

`corpus` uses 24 training maps (seeds 12000000..12000023) and six validation maps (12100000..12100005), with opponent `[v01, spatial-ppo-v2, v09][mapIndex % 3]`. Each map supplies two complete games, one per teacher seat. `smoke` uses three maps (11900000..11900002), one game each, teacher seat `mapIndex % 2`, and at most two actual turns. Games stop at the core's actual terminal if earlier. Smoke data is never training or validation data.

## Binary layout

Each game has its own gzip file. Decompression yields a 16-byte header followed by fixed 63638-byte records, with no alignment padding. All integers and floats are little-endian. Floats preserve the exact Java float32 bits; gzip is lossless.

Header: eight ASCII bytes `BTKSD001`, one int32 with value 63638, one reserved int32 with value zero.

Record fields, in order (offsets are relative to the beginning of the record):

- Offset 0: `planes`, 14400 float32 values, existing observation plane-major 24 x 20 x 30.
- Offset 57600: `towns`, 588 float32 values, existing 12 x 49 town features.
- Offset 59952: `scalars`, six float32 values.
- Offset 59976: `townCells`, twelve int32 padded cell indices; missing towns use -1.
- Offset 60024: `regionIds`, 600 int32 values; off-board padding uses -1.
- Offset 62424: `buildMask`, 601 uint8 values, each exactly 0 or 1.
- Offset 63025: `disruptMask`, 601 uint8 values, each exactly 0 or 1.
- Offset 63626: `label`, one int32.
- Offset 63630: `stage`, one int32, 0..3.
- Offset 63634: `turn`, one int32, the actual pre-transition turn number.

Records are turn-major, four records per turn with stages 0, 1, 2, 3. Stages 0..2 label the teacher's ordered core-expanded placements using padded cell `y*30+x`. The first unused build slot and remaining build slots use STOP=600. Stage 3 labels the core-expanded disruption region, or STOP=600 when absent. Every label must be in the corresponding stage's Java mask. A STOP only forces subsequent build decisions; disruption remains independently available.

Every row uses the unchanged `SpatialObservation.prepare` on the pre-turn state followed by `prefix` containing only the teacher's earlier build choices. Current/future labels, the opponent's chosen actions and post-turn outcomes never enter observations. No Python pathing, action reconstruction or feature reimplementation is required. The teacher's final returned actions are expanded through `Rules.expand`; the ordered placements include any final filler actions. Reconstructed labels must expand to the same ordered cells, cost and disruption.

Both policies act on separate copies of the same pre-turn state, with serial 60000 ms calls and no warmup. One `Rules.resolve` applies their original plans simultaneously. Acceptance is checked against its expanded moves, not surviving tracks: a collision followed by immediate inking is a valid accepted action sequence. Incomplete searches, over-budget calls, core errors/skips, illegal labels or nonfinite observations fail the collection; partial output remains evidence and must never be loaded for training.

## Manifest and audit metadata

`intent.json` is the write-once initial configuration. `manifest.progress.json` is atomically replaced after preflight and each game; its status may remain `collecting`. Only the write-once final `manifest.json` with `status: complete` is usable. Final failure metadata is retained with `status: failed` and a failure string. Completed game binary/metadata files are never overwritten.

The manifest includes `schemaVersion: 1`, `format: BTKSD001`, `experiment: spatial-ppo-v4`, `mode: smoke|corpus`, status/timestamps, `headerBytes`, `rowBytes`, `teacherSpec`, `teacherParams`, `opponentParams`, `opponentV2WeightsSha256`, `budgetMs`, `warmup`, `exclusionsSha256`, `excludedMapCount`, `javaVersion`, `mapPreflightComplete`, `maps`, `games`, `rows`, `totalTurns` and `callStats`.

Each map has `split: train|validation|smoke`, `mapIndex`, `seed`, `mapHash`, `opponentSpec`, `initBlockP0`, `width`, `height` and `townCount`. `mapHash` is SHA-256 of UTF-8 `Protocol.initBlock(initialState, 0)`. The exclusion hash covers the exact supplied exclusion file bytes.

Each game has the map identity fields, `teacherSeat`, unique `id` (for example `train-00-p0`), relative `binary` and `metadata` filenames, `status`, integer `rowCount` and `turns`, `terminal`, `sha256` (compressed file bytes), `rawSha256` (decompressed header plus records), `metadataSha256`, compressed `bytes`, `uncompressedBytes` and nullable `failure`. A complete game's uncompressed length is `16 + rowCount*63638`.

The per-game JSON carries the same game identity, `teacherSpec`, `status`, `binary`, `rowCount`, integer `turnCount`, `terminal`, final `score`, nullable `failure`, `stopReason` and a `turns` array. Each turn entry contains:

- `turn`, `rowStart` and `preStateSha256` (initial protocol block, actual turn and max-turn count, then turn protocol block).
- `calls`: each policy's `me`, `spec`, `role: teacher|opponent`, `budgetMs`, `elapsedMs`, `complete`, `legal`, `selectedOpening`, original formatted `actions`, and failure diagnostics if any. V09 additionally records `ordinarySearchComplete`; the spatial policy records `interrupted`; the finite v01 policy records `completionEvidence`. V09 completion means ordinary search completed or its deliberate opening was selected.
- Original `teacherActions` and `opponentActions` strings. `teacherExpanded` and `opponentExpanded` each contain ordered padded `cells`, `disrupt` (region or -1), and core `cost`.
- Four `labels`, four ordered `prefixes`, four `paintBefore` values and four `stoppedBefore` booleans.
- `acceptedTeacher` and `acceptedOpponent` using the same expanded-plan schema; `gained`, `scoreAfter`, `neutralised` (native cell indices), `inkedRegions` (region IDs) and `terminalAfter` from the exact core transition.

`callStats` is keyed by `role:spec` and contains `calls`, `completed`, `failed`, `elapsedMsTotal` and `maxElapsedMs`. A failed call remains in the per-turn record and totals. Full action/outcome metadata is audit material, not model input or a learning target.

## Reader constraints

Reject incomplete manifests/games, wrong hashes/header/record lengths, nonfinite float features, malformed masks, illegal targets, missing/reordered stages, identity/split inconsistencies, or data-map overlap with the supplied exclusion index. Corpus mode has exactly 30 unique maps and 60 paired-seat games, split by whole map. No partial game is accepted. The reader may decompress each file once into a structured read-only memmap with `align=False` and offset 16; selected minibatches are copied to tensors. At the 100-turn ceiling, 60 games contain at most 24000 rows (about 1.527 GB uncompressed including headers); the binary features for 64 decision rows occupy about 4.1 MB. Cache files are derived and excluded from the evidence archive; gzip files, manifests and action audits are retained.
