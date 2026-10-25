#!/bin/bash
# collision-avoidance-v1 (2026-10-25 night): v5 steps (a)+(b) for the explicit collision aversion on current (= v12),
# fresh pool seed 1006000 (first use of the 1006000 range), 300 paired games per cell, 2 workers, CG timing (486/36 ms).
#   C5  = current?USE_COLLISION_AVOIDANCE=true                        5 points per root cell shared with the opponent's predicted cells
#   C15 = current?USE_COLLISION_AVOIDANCE=true,COLLISION_PENALTY=15   15 points
#   X-vs-boss2   each arm and the control (current) against the boss-like screening proxy on the same maps/seeds/seats;
#                the reading is the paired per-map delta vs cur-vs-boss2 (v5 step a: continue only at >= control + 3 pp)
#   X-vs-cur     head-to-head with the selected version (v5 step b: continue only at >= 48 %)
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_collision_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/collision-v1}; mkdir -p "$OUT"
CUR="current"
C5="current?USE_COLLISION_AVOIDANCE=true"
C15="current?USE_COLLISION_AVOIDANCE=true,COLLISION_PENALTY=15"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0"
GAMES=${GAMES:-300}; SEED=${SEED:-1006000}
CELLS=${CELLS:-"C5-vs-boss2 C15-vs-boss2 cur-vs-boss2 C5-vs-cur C15-vs-cur"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    C5-vs-boss2)  run $cell "$C5" "$BOSS2" ;;
    C15-vs-boss2) run $cell "$C15" "$BOSS2" ;;
    cur-vs-boss2) run $cell "$CUR" "$BOSS2" ;;
    C5-vs-cur)    run $cell "$C5" "$CUR" ;;
    C15-vs-cur)   run $cell "$C15" "$CUR" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "COLLISION SCREEN DONE $(date -u +%FT%TZ)"
