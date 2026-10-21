#!/bin/bash
# Stage 1 opponent model (docs/stage1-opponent-model-v1.md): candidates A (opponent model) and
# B (A + own model) against neural-v6, boss-v2, v09 and boss-sabotage-v2 (lite) on the same maps.
set -e
cd /home/claude/btk
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes5:lib/gson-2.13.1.jar}
OUT=build/analysis/boss-model; mkdir -p $OUT
LITE="current?NOISE=0.1,USE_BOSS_LITE=true"
A="$LITE,USE_BOSS_OPPONENT_MODEL=true"
B="$LITE,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true"
GAMES=${GAMES:-300}; SEED=${SEED:-970000}
run() { # name specA specB
  echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED)"
  java -Xmx1500m -cp $CP btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED $OUT/$1.json cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true
}
run A-vs-lite "$A" "$LITE"
run A-vs-neural-v6 "$A" neural-v6
run A-vs-v09 "$A" v09
run B-vs-lite "$B" "$LITE"
run B-vs-neural-v6 "$B" neural-v6
run B-vs-v09 "$B" v09
run A-vs-boss2 "$A" "current?USE_BOSS_SABOTAGE=1"
echo "MODEL SCREEN DONE"
