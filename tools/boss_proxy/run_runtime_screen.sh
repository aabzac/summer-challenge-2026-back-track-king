#!/bin/bash
# Stage 2 (compute): D = C + the three validated runtime flags (incremental ink refresh now also inside
# chooseBossLite, fast cheapest path, batched refresh) on the 970000 pool, same cells as before.
set -e
cd /home/claude/btk
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=build/analysis/boss-runtime; mkdir -p $OUT
C="current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true,USE_BOSS_LITE_ROUTES=true"
D="$C,USE_INCREMENTAL_INK_REFRESH=true,USE_FAST_CHEAPEST_PATH=true,USE_BATCHED_REFRESH=true"
GAMES=${GAMES:-300}; SEED=${SEED:-970000}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED)"
  java -Xmx1500m -cp $CP btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED $OUT/$1.json cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
run D-vs-C "$D" "$C"
run D-vs-neural-v6 "$D" neural-v6
run D-vs-v09 "$D" v09
run D-vs-boss2 "$D" "current?USE_BOSS_SABOTAGE=1"
echo "RUNTIME SCREEN DONE"
