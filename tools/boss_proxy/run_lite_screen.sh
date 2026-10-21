#!/bin/bash
# boss-sabotage-v2 (USE_BOSS_LITE) screen per benchmarking-v5 clause 5 (amended 2026-10-21):
# step 1 vs neural-v6 with the v09 control on the same maps; step 2 = v4 pooled 1000 vs v09.
set -e
cd /home/claude/btk
export JAVA_TOOL_OPTIONS=
CP=out/classes4:lib/gson-2.13.1.jar
OUT=build/analysis/boss-lite; mkdir -p $OUT
CAND="current?NOISE=0.1,USE_BOSS_LITE=true"
run() { # name specA specB games seed
  echo "-- $1: $2 vs $3 ($4 games, seed $5)"
  java -Xmx1500m -cp $CP btk.runner.BenchmarkRunner "$2" "$3" $4 "" 2 $5 $OUT/$1.json cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true
}
run step1-cand-vs-neural-v6 "$CAND" neural-v6 300 960000
run step1-ctrl-v09-vs-neural-v6 v09 neural-v6 300 960000
run step2-block1-vs-v09 "$CAND" v09 300 961000
run step2-block2-vs-v09 "$CAND" v09 200 962000
run step2-block3-vs-v09 "$CAND" v09 500 963000
echo "LITE SCREEN DONE"
