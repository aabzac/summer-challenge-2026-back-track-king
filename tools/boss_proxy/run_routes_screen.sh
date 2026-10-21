#!/bin/bash
# Stage 3 (cheap) screen: C = opponent-model-v1 + USE_BOSS_LITE_ROUTES on the 970000 pool, same cells as run_model_screen.sh.
set -e
cd /home/claude/btk
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes6:lib/gson-2.13.1.jar}
OUT=build/analysis/boss-routes; mkdir -p $OUT
B="current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true"
C="$B,USE_BOSS_LITE_ROUTES=true"
GAMES=${GAMES:-300}; SEED=${SEED:-970000}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED)"
  java -Xmx1500m -cp $CP btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED $OUT/$1.json cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
run C-vs-B "$C" "$B"
run C-vs-neural-v6 "$C" neural-v6
run C-vs-v09 "$C" v09
run C-vs-boss2 "$C" "current?USE_BOSS_SABOTAGE=1"
echo "ROUTES SCREEN DONE"
