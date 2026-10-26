#!/bin/bash
# end-forecast-v1 (2026-10-26): the proposal earning horizon as the game-end forecast — v5 steps (a)+(b) on current (= v12),
# fresh pool seed 1007000 (first use of the 1007000 range), 300 paired games per cell, 2 workers, CG timing (486/36 ms).
# The endgame study (docs/end-forecast-v1.md) found no board feature that forecasts the remaining turns better than the
# existing 16-turn cap within that cap; the one lever left is the cap itself: late proposals are credited ~4.5 earning turns
# too many on average.
#   H12 = current?ROLLOUT_ROUTE_HORIZON=12   route proposals earn for at most 12 turns (default 16)
#   H10 = current?ROLLOUT_ROUTE_HORIZON=10
#   X-vs-boss2   each arm and the control (current) against the boss-like screening proxy on the same maps/seeds/seats;
#                the reading is the paired per-map delta vs cur-vs-boss2 (v5 step a: continue only at >= control + 3 pp)
#   X-vs-cur     head-to-head with the selected version (v5 step b: continue only at >= 48 %)
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_horizon_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/horizon-v1}; mkdir -p "$OUT"
CUR="current"
H12="current?ROLLOUT_ROUTE_HORIZON=12"
H10="current?ROLLOUT_ROUTE_HORIZON=10"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0"
GAMES=${GAMES:-300}; SEED=${SEED:-1007000}
CELLS=${CELLS:-"H12-vs-boss2 H10-vs-boss2 cur-vs-boss2 H12-vs-cur H10-vs-cur"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    H12-vs-boss2) run $cell "$H12" "$BOSS2" ;;
    H10-vs-boss2) run $cell "$H10" "$BOSS2" ;;
    cur-vs-boss2) run $cell "$CUR" "$BOSS2" ;;
    H12-vs-cur)   run $cell "$H12" "$CUR" ;;
    H10-vs-cur)   run $cell "$H10" "$CUR" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "HORIZON SCREEN DONE $(date -u +%FT%TZ)"
