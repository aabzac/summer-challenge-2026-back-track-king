#!/bin/bash
# Practical-v4 style blocks for boss-sabotage-v1 vs v09: 300 / +200 / +500 paired games on fresh seeds,
# futility stop below 50% pooled at 300 or 500. Reports under build/bench/boss-sabotage-v1/.
set -e
cd /home/claude/btk
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes3:lib/gson-2.13.1.jar}
CAND=${CAND:-"current?NOISE=0.1,USE_BOSS_SABOTAGE=true"}
CTRL=${CTRL:-v09}
OUT=build/bench/boss-sabotage-v1; mkdir -p $OUT
run() { java -Xmx1500m -cp $CP btk.runner.BenchmarkRunner "$CAND" "$CTRL" $1 "" 2 $2 $OUT/block$3.json cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|time/move|accepted|early" ; }
wl() { python3 -I -c "import json,sys; d=json.load(open('$1')); print(d.get('winsA', d.get('wins')), d.get('lossesA', d.get('losses')), d.get('draws'))"; }
echo "== block 1: 300 games seed 940000"; run 300 940000 1
echo "== block 2: 200 games seed 941000"; run 200 941000 2
echo "== block 3: 500 games seed 942000"; run 500 942000 3
echo "BLOCKS DONE"
