#!/bin/bash
# exposure-income-v1 (2026-10-25, Stage 4 second half): v5 steps (a)+(b) for the own-exposure discount on current (= v11),
# fresh pool seed 1003000 (first use of the 1003000 range), 300 paired games per cell, 2 workers, CG timing (486/36 ms).
#   E1  = current?USE_EXPOSURE_INCOME=true                              income weighted by region survivability, weight 1.0
#   E05 = current?USE_EXPOSURE_INCOME=true,EXPOSURE_INCOME_WEIGHT=0.5   half weight
#   X-vs-boss2   each arm and the control (current) against the boss-like screening proxy on the same maps/seeds/seats;
#                the reading is the paired per-map delta vs cur-vs-boss2 (v5 step a: continue only at >= control + 3 pp)
#   X-vs-cur     head-to-head with the selected version (v5 step b: continue only at >= 48 %)
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_stage4b_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/stage4-exposure-v1}; mkdir -p "$OUT"
CUR="current"
E1="current?USE_EXPOSURE_INCOME=true"
E05="current?USE_EXPOSURE_INCOME=true,EXPOSURE_INCOME_WEIGHT=0.5"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0"
GAMES=${GAMES:-300}; SEED=${SEED:-1003000}
CELLS=${CELLS:-"E1-vs-boss2 E05-vs-boss2 cur-vs-boss2 E1-vs-cur E05-vs-cur"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    E1-vs-boss2)  run $cell "$E1" "$BOSS2" ;;
    E05-vs-boss2) run $cell "$E05" "$BOSS2" ;;
    cur-vs-boss2) run $cell "$CUR" "$BOSS2" ;;
    E1-vs-cur)    run $cell "$E1" "$CUR" ;;
    E05-vs-cur)   run $cell "$E05" "$CUR" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "STAGE4B SCREEN DONE $(date -u +%FT%TZ)"
