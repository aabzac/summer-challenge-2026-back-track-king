# v07 promotion - Network After Base

User selected the verified network-after-base-v1 candidate as the main strategy on 2026-09-18.
This is a packaging/default-selection step, not another strength experiment or retuning.
Measured strategy revision: 794daf3 (with candidate flags explicit in the frozen launch).

## Selected policy and evidence

Exactly the 98 candidate parameters from build/bench/network-after-base-v1/launch.json:
USE_ROLLOUT_QUICK_ROUTE=true, USE_ROLLOUT_NETWORK_AFTER_BASE=true,
USE_ROLLOUT_NETWORK=false; every other field unchanged. Copy RolloutBot to v07, changing
only package/class/name. Preserve all existing v00-v06 source and parameter snapshots.
Full strength and prior exact-artifact evidence: [network-after-base-v1.md](network-after-base-v1.md).

- Direct vs v06: 187-112-1 / 300, 62.50%, PASS_300.
- Matched common-v05 screen: both 172-128 / 300, 57.3333%; required continuation.
- Separate matched common-v05 confirmation: candidate 649-351 / 1000, 64.90%;
  v06 569-431 / 1000, 56.90%; +8.00 percentage points, PASS_1000.
- Independent QuickIncome ablation: 179-121 / 300, 59.6667%, diagnostic only.
- Prior exact candidate process check: 0/8 problem games, max later 44.6592 ms.

## Fixed promotion verification, declared before process games

Archive all 25 existing build/submit files under build/analysis/v07-promotion/previous-submit
before replacing the main artifact. Before/after identities are retained in before.json.

Run the full unit suite and cgValidate. Verify complete parameter equality, normalized frozen
source equality and ample-budget action parity between measured policy, current/main and v07.
Generate the default build/submit/Player.java with gradlew submit; compare it with the measured
candidate source and explain every packaging difference. Freeze exact source/class hashes.

Then run four separate gradlew procCheck invocations against frozen v05, two games each for
both player sides, on these maps in this order:

1. replays/cg/903510176.json
2. replays/cg/bot/904146009.json
3. replays/cg/bot/904148181.json
4. replays/cg/bot/904147739.json

Strict 1000/50-ms deadlines, zero grace, no optional instrumentation, no retries or discarded
outliers. Complete all eight games if reports remain valid, even after a process failure.
No other CPU-heavy checks during timing. Preserve every report and any errors. Hold a temporary
Windows execution-state request during the panel and release it in finally.
These maps form a packaging/timing regression sample, not fresh arena-strength evidence.
A failure leaves timing readiness open; never report a successful candidate check as clearing
a failure of the newly packaged file. No arena upload is included in this promotion.

## Arena checkpoint before v07 upload

User report on 2026-09-18: CG currently runs QuickIncome, rank **111 / 627**, before the
intended v07 upload. Exact deployed hash and placement status are unverified. No v07 upload
has been performed by the agent or reported by the user at this checkpoint.

## Result

**PASS. v07 is selected locally and the main build/submit/Player.java is ready.**

- 273/273 tests pass, zero failures/errors/skips. Core validation: 264/264 exact CG replays.
- Current rollout source is byte-identical to the measured experiment. The frozen v07 source
  differs only in package, class/constructor name and name() label. All 98 parameters match
  the measured candidate for current/main/v07; active-network and evolving-board tests pass.
- All prior frozen sources, snapshots and experiment evidence are unchanged. Historical
  regression fixtures and diagnostic defaults now explicitly disable the newly selected flags.
- Default generated Player differs byte-for-byte from the previously verified candidate ONLY
  in the two boolean default declarations (false to true). Effective parameters already matched.
  Size: **75,288 / 100,000 characters**. SHA-256:
  `3c94f8ed5cd0cc16933e45b7fab7e8d48faafb9a1f52c952f601cd3fbcfc42c7`.
- Strict process panel: **0/8 problem games**, zero timeouts and nominal overruns;
  maximum later response **44.7744 ms**, first response **608.3554 ms**. No later response over
  45 ms. Fixed four maps, both sides, all reports retained, no retries. This is a bounded local
  timing sample, not a CG-host guarantee or a new strength benchmark.
- Source, classes, full parameters and map identities remained fixed throughout the panel;
  the temporary Windows execution-state request was released successfully.
- Previous v06 Player and all 25 old submission files are preserved in
  `build/analysis/v07-promotion/previous-submit/`; standalone PlayerQuickIncome files remain unchanged.

No retuning, repeat strength games, push or arena upload. The CG checkpoint remains the
user-reported QuickIncome **111 / 627** before v07 upload.

Durable [process evidence](evidence/v07-promotion.json) and
[verification / prospective protocol](evidence/v07-promotion-verification.json).
The exact source and compiled classes tested here remain at `build/submit/`.
