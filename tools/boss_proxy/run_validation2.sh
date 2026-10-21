#!/bin/bash
set -e
cd /home/claude/btk
export JAVA_TOOL_OPTIONS=
CP=out/classes3:lib/gson-2.13.1.jar
OUT=build/analysis/proxy-validation
GAMES=${GAMES:-200}; SEED=${SEED:-910000}
PROXY="current?USE_BOSS_SABOTAGE=1"
for spec in "v09" "v08" "v07" "neural-v6" "neural-v1" "current?USE_ROLLOUT_ALL_CELL_RISK=1" "current?USE_ROLLOUT_ROOT_FILL=1" "v09?USE_ROLLOUT_LIVE_TARGET=1" "current?USE_BATCHED_REFRESH=1"; do
  name=$(echo "$spec" | tr '?=,' '___')
  echo "-- $spec vs boss2"
  java -Xmx1500m -cp $CP btk.runner.BenchmarkRunner "$spec" "$PROXY" $GAMES "" 2 $SEED $OUT/bench2-$name.json cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|early" || true
done
echo "VALIDATION2 DONE"
