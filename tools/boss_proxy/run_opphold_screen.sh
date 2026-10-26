#!/bin/bash
# opp-hold-v1 (2026-10-26): v5 steps (a)+(b) for the hold margin of the bot's boss model on current (= v12). boss-v3 measured
# the lite chooser's margin 0 (re-evaluate the target every turn) agreeing 58.8 / 62.6 % with the live boss on the K1-session
# games against 54.1 / 58.1 % at the shipped 1.0. Fresh pool seed 1012000 (first use), 300 paired games per cell, 2 workers,
# CG timing (486/36 ms).
#   O0 = current?BOSS_OPP_HOLD_MARGIN=0   margin 0 only for the simulated opponent inside the rollout; our own disrupt keeps 1.0
#   H0 = current?BOSS_HOLD_MARGIN=0       margin 0 everywhere (our real disrupt and both models)
#   X-vs-boss2     each arm and the control against the policy's screening proxy (margin 1.0) on the same maps/seeds/seats;
#                  the reading is the paired per-map delta vs cur-vs-boss2 (v5 step a: continue only at >= control + 3 pp)
#   X-vs-cur       head-to-head with the selected version (v5 step b: continue only at >= 48 %)
#   X-vs-boss2m0   each arm and the control against the better-agreeing proxy (BOSS_HOLD_MARGIN=0); paired delta vs cur-vs-boss2m0.
#                  An opponent model tuned to the margin-0 boss is expected to look worse against the margin-1 proxy than it is.
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_opphold_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/opphold-v1}; mkdir -p "$OUT"
CUR="current"
O0="current?BOSS_OPP_HOLD_MARGIN=0"
H0="current?BOSS_HOLD_MARGIN=0"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0"
BOSS2M0="$BOSS2,BOSS_HOLD_MARGIN=0"
GAMES=${GAMES:-300}; SEED=${SEED:-1012000}
CELLS=${CELLS:-"cur-vs-boss2 O0-vs-boss2 H0-vs-boss2 O0-vs-cur H0-vs-cur cur-vs-boss2m0 O0-vs-boss2m0 H0-vs-boss2m0"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    cur-vs-boss2)   run $cell "$CUR" "$BOSS2" ;;
    O0-vs-boss2)    run $cell "$O0" "$BOSS2" ;;
    H0-vs-boss2)    run $cell "$H0" "$BOSS2" ;;
    O0-vs-cur)      run $cell "$O0" "$CUR" ;;
    H0-vs-cur)      run $cell "$H0" "$CUR" ;;
    cur-vs-boss2m0) run $cell "$CUR" "$BOSS2M0" ;;
    O0-vs-boss2m0)  run $cell "$O0" "$BOSS2M0" ;;
    H0-vs-boss2m0)  run $cell "$H0" "$BOSS2M0" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "OPPHOLD SCREEN DONE $(date -u +%FT%TZ)"
