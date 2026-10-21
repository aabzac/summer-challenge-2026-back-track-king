#!/bin/bash
# Proxy validation: fingerprint study + ordering benches, all vs boss-v1 on the same fresh map pool.
set -e
cd /home/claude/btk
export JAVA_TOOL_OPTIONS=
CP=out/classes:lib/gson-2.13.1.jar
OUT=build/analysis/proxy-validation
mkdir -p $OUT
GAMES=${GAMES:-200}
SEED=${SEED:-910000}
echo "== fingerprint: boss-v1 vs v09, $GAMES games, seed $SEED"
java -Xmx1500m -cp $CP btk.tools.ProxyBehaviorStudy boss-v1 v09 $GAMES $SEED $OUT/fingerprint 2 2>&1 | tail -2
python3 -I tools/boss_proxy/fingerprint.py proxy_vs_v09 $OUT/fingerprint/turns.csv $OUT/fingerprint/cells.csv > $OUT/fingerprint.json
python3 -I tools/boss_proxy/fingerprint.py real_boss_vs_v09 build/analysis/boss-full/intent/turns.csv build/analysis/boss-full/intent/cells.csv build/analysis/v09_boss_games.txt > $OUT/fingerprint-real.json
python3 -I tools/boss_proxy/fingerprint.py real_boss_all build/analysis/boss-full/intent/turns.csv build/analysis/boss-full/intent/cells.csv > $OUT/fingerprint-real-all.json
echo "== ordering benches (A vs boss-v1), $GAMES games each, seed $SEED"
for spec in "v09" "v08" "v07" "neural-v6" "neural-v1" "current?USE_ROLLOUT_ALL_CELL_RISK=1" "current?USE_ROLLOUT_ROOT_FILL=1" "v09?USE_ROLLOUT_LIVE_TARGET=1" "current?USE_BATCHED_REFRESH=1"; do
  name=$(echo "$spec" | tr '?=,' '___')
  echo "-- $spec"
  java -Xmx1500m -cp $CP btk.runner.BenchmarkRunner "$spec" boss-v1 $GAMES "" 2 $SEED $OUT/bench-$name.json cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|early|time/move" || true
done
echo "VALIDATION DONE"
