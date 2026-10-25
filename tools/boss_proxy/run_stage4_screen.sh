#!/bin/bash
# stage4-threat-model-v1 (2026-10-25): v5 steps (a)+(b) for the two Stage 3–4 consistency flags on current (= v10),
# fresh pool seed 1002000 (first use of the 1002000 range), 300 paired games per cell, 2 workers, CG timing (486/36 ms).
#   A  = current?USE_BOSS_THREAT_MODEL=true   the route proposals' predicted opponent target comes from the lite chooser
#   B  = current?USE_BOSS_ROOT_TARGET=true    our first simulated turn disrupts the real-turn lite target, no no-disrupt arm
#   AB = both
#   X-vs-boss2   each arm and the control (current) against the boss-like screening proxy on the same maps/seeds/seats;
#                the reading is the paired per-map delta vs cur-vs-boss2 (v5 step a: continue only at >= control + 3 pp)
#   X-vs-cur     head-to-head with the selected version (v5 step b: continue only at >= 48 %)
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_stage4_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/stage4-threat-v1}; mkdir -p "$OUT"
CUR="current"
A="current?USE_BOSS_THREAT_MODEL=true"
B="current?USE_BOSS_ROOT_TARGET=true"
AB="current?USE_BOSS_THREAT_MODEL=true,USE_BOSS_ROOT_TARGET=true"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0"
GAMES=${GAMES:-300}; SEED=${SEED:-1002000}
CELLS=${CELLS:-"A-vs-boss2 B-vs-boss2 AB-vs-boss2 cur-vs-boss2 A-vs-cur B-vs-cur AB-vs-cur"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    A-vs-boss2)   run $cell "$A" "$BOSS2" ;;
    B-vs-boss2)   run $cell "$B" "$BOSS2" ;;
    AB-vs-boss2)  run $cell "$AB" "$BOSS2" ;;
    cur-vs-boss2) run $cell "$CUR" "$BOSS2" ;;
    A-vs-cur)     run $cell "$A" "$CUR" ;;
    B-vs-cur)     run $cell "$B" "$CUR" ;;
    AB-vs-cur)    run $cell "$AB" "$CUR" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "STAGE4 SCREEN DONE $(date -u +%FT%TZ)"
