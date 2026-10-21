#!/bin/bash
# Second pool (seed 960000) for the route term's neural-v6 gain, plus a hold-margin sweep on C.
set -e
cd /home/claude/btk
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes6:lib/gson-2.13.1.jar}
OUT=build/analysis/boss-routes; mkdir -p $OUT
B="current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true"
C="$B,USE_BOSS_LITE_ROUTES=true"
run() { echo "-- $1: $2 vs $3 (300 games, seed $4)"
  java -Xmx1500m -cp $CP btk.runner.BenchmarkRunner "$2" "$3" 300 "" 2 $4 $OUT/$1.json cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
run pool2-C-vs-neural-v6 "$C" neural-v6 960000
run pool2-B-vs-neural-v6 "$B" neural-v6 960000
run hold05-vs-C "$C,BOSS_HOLD_MARGIN=0.5" "$C" 970000
run hold20-vs-C "$C,BOSS_HOLD_MARGIN=2.0" "$C" 970000
echo "ROUTES CONFIRM DONE"
