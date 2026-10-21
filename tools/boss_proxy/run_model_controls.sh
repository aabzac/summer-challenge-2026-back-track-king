#!/bin/bash
# Same-map controls for run_model_screen.sh: boss-sabotage-v2 (lite) vs neural-v6 and vs v09 on seed 970000.
set -e
cd /home/claude/btk
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes5:lib/gson-2.13.1.jar}
OUT=build/analysis/boss-model; mkdir -p $OUT
LITE="current?NOISE=0.1,USE_BOSS_LITE=true"
GAMES=${GAMES:-300}; SEED=${SEED:-970000}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED)"
  java -Xmx1500m -cp $CP btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED $OUT/$1.json cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
run ctrl-lite-vs-neural-v6 "$LITE" neural-v6
run ctrl-lite-vs-v09 "$LITE" v09
echo "MODEL CONTROLS DONE"
