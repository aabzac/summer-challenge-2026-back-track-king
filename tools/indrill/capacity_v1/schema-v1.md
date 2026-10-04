# Capacity teacher schema v1

Diagnostic artifact schema. Run export only through the reviewed execution freeze.

`teacher-preflight.json` uses schema `indrill-capacity-preflight-v1`, carries
`complete`, source/runtime/input pins, exact old40/later11 IDs, per-game raw replay
hash/core full-map hash/teacher identity/valid and excluded turns/validation,
map groups, and a `selection` array of unique observations with `panelTags`.
Panel cardinalities are A=64, B=2048, holdout<=1020; A rows also occur in B.

`teacher.jsonl` contains one row per unique selected observation:

- `schema`: `indrill-capacity-observation-v1`; `observationId`:
  `<gameId>:<teacherSeat>:<turn>`.
- `mapHash`, `gameId`, `replaySha256`, `teacherSeat`, `teacherAgentId`, `turn`,
  `teacherFrameIndex`, `split` (train/holdout), `panelTags`, `cohortTags`.
  Cohorts: old40/later11/holdoutCompanion; training has an empty cohort list.
- `width`, `height`, `size`, `regionCount`, `featureCount`=64, `paintBudget`=3.
- `regions[N]`, `costs[N]`, `legalBuild[N]`, `buildCost[N]`, `legalInk[R]`,
  `neighbors[N][4]` (-1 off board, NESW), `regionSizes[R]`, `features[N][64]`.
  Costs are core terrain prices (1/2/3); buildCost is price on legal cells, else0.
  Features are unstandardized finite Java float32 values represented in JSON.
- `targetCells`: sorted unique accepted own cells; `targetInk`: accepted region
  or -1 for NONE; `acceptedPaint`, `hadSkippedActions`, `skippedActions`.
- `inputSha256` binds model inputs only using the byte format below.

The row is observed before either player's recorded actions. There are no
rewards, future scores, successor features or opponent action fields. Presence of
teacher stdout is independently traced to canonical frames: exactly one nonempty
teacher stdout for that turn, no server failure, and exact agreement with the
CgReplay text. Missing frames/null stdout never create an imitation WAIT label.

## Exact canonical model-input bytes

All integer values are signed32 big endian. Prefix ASCII magic `BTKCI001` (8bytes),
then six int32 values in order: width,height,size,regionCount,featureCount,paintBudget.
Next write each array in the order below, prefixed by its flattened int32 length:

1. regions: N int32.
2. costs: N int32.
3. legalBuild: N uint8, false0/true1.
4. buildCost: N int32.
5. legalInk: R uint8, false0/true1.
6. neighbors: N*4 int32, row major NESW.
7. regionSizes: R int32.
8. features: N*64 raw float32 bit patterns, big endian, row major.

Lengths include elements, not byte counts. Preserve signed zero. Nonfinite
features are prohibited. No string encoding, targets, labels, split, map identity
or source path occurs in these bytes. Map hash is separately the SHA256 of UTF8
`Protocol.initBlock(initial,0)`. It is not this observation hash or graphics hash.

## Completion and Python interface

`dataset-manifest.json` schema `indrill-capacity-dataset-v1` records `complete`,
`preflightSha256`, `teacherJsonlSha256`, `rowCount`, `panelCounts`, contract checks
and collision summaries. A failed/partial export must not set complete=true.
Use create-new writes; never replace existing outputs.

`teacher_dataset.load_dataset(directory, require_complete=True)` returns Dataset:
`rows`, `items` (train_q.Transition), `panels` (panel -> row indices), `manifest`;
`batch(indices)` returns train_q.Batch; `panel_indices(name)` returns indices.
Rows are validated; large numeric arrays may be converted to numpy arrays in
memory. Teacher features occupy perspective0; perspective1 is an unused view,
never another sample. Dummy transition fields have no outcome/next-state meaning.

`dataset_from_rows(rows, manifest=None)` is for synthetic/loader checks and does
not impose fixed panel cardinalities. `modelinput_sha256(row)` implements the
canonical binary hash. `collision_summary(rows, indices)` returns observation and
unique-input counts, conflict groups, exactJointCeilingCount/Rate and group label
histograms. Exact joint labels are `(sorted cells, targetInk)`.

## Sufficient representation certificates

After validation, the loader computes
`manifest['representationCertificates'] = representation_certificates.certificate_report(rows)`.
This is a derived report, not an alteration of the write-once exported manifest.
Its schema is `indrill-capacity-representation-certificates-v1`, with `complete`,
`observations`, `inputLabelBindingSha256`, mathematical cell/region group counts,
runtime cell/region class counts, `observationsWithConditionalTieObstruction`,
`capacityCeilingApplied=false`, `details` for observations with nonempty groups,
and explanatory `interpretation` fields. `input_label_binding(rows,indices=None)`
hashes ASCII compact JSON of `[observationId,inputSha256,targetCells,targetInk]`
tuples, sorted by observationId, so label changes also invalidate the binding.
The trainer must validate the report schema, completeness, row count and binding;
the report introduces no additional gate or numerical ceiling.

Mathematical cell signatures include exact raw float32 own-feature bytes and the
normalized multiset of neighbouring feature vectors. Mathematical region
signatures are normalized multisets of these cell signatures. These are
sufficient real-arithmetic invariances within one observation, whose global
context is shared. Coordinates/directional features are retained. A generic map
reflection or rotation is not an assumed symmetry.

`runtimeCellClasses` are conservative ordered refinements for **Java17 scalar
IndrillV2Net**: own features, ordered NESW feature vectors/missing slots and the
averaging divisor. Region refinements additionally preserve region size and the
ordered sequence of cell classes. Finite intermediates are an explicit scope
condition. They do not certify bitwise equality in NumPy: matrix kernels can
round identical operands differently at different row positions. Every detail
therefore includes `numpyRuntimeEqualityCertified=false`; the generic
`decoderIncompatibilityNotProven` remains true where a mathematical target tie
obstruction exists but no universal implementation claim is made.

Conditional decoder constraints cover only same-cost cell shortlists retaining
lower indices and the strict-greater ink scan retaining the lower legal region.
These decisions precede joint/pattern aggregation. No proof is claimed for
unequal-cost cells, ties between aggregate patterns or arbitrary runtime weights.
Exact-input label conflicts remain the separately reported observational ceiling;
an optimizer's failure to fit is another distinct result.

Synthetic implementation checks are retained in `certificate-synthetic-first.json`
(Python structure/decoder tests and NumPy rounding counterexamples) and
`certificate-java-first.json` (actual frozen Java class witnesses). Both bind the
tested sources; the Java result also binds compiled classes. They must be included
in any eventual reviewed training freeze. A changed checker/runtime requires new
write-once check results before `checksPassed=true` can be asserted.
