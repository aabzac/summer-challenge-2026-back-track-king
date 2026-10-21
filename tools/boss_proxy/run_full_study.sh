#!/bin/bash
# Full boss-corpus study: CgLeaderStudy (2 parallel halves) + BossIntentStudy, then the fits.
set -e
cd /home/claude/btk
export JAVA_TOOL_OPTIONS=
CP=out/classes:lib/gson-2.13.1.jar
SRC=replays/cg/boss
# split into two halves preserving the replays/cg/boss tree shape so listReplays walks recursively
rm -rf tmp_half1 tmp_half2
mkdir -p tmp_half1/replays/cg/boss tmp_half2/replays/cg/boss
i=0
for f in $(find $SRC -name '*.json' | sort); do
  rel=${f#$SRC/}
  if [ $((i % 2)) -eq 0 ]; then dst=tmp_half1/replays/cg/boss/$rel; else dst=tmp_half2/replays/cg/boss/$rel; fi
  mkdir -p "$(dirname "$dst")"; cp "$f" "$dst"; i=$((i+1))
done
echo "split $i files"
mkdir -p build/analysis/boss-full
( java -Xmx1500m -cp $CP btk.tools.CgLeaderStudy tmp_half1/replays/cg/boss "Boss 4" build/analysis/boss-full/leader1 > build/analysis/boss-full/leader1.log 2>&1 ) &
( java -Xmx1500m -cp $CP btk.tools.CgLeaderStudy tmp_half2/replays/cg/boss "Boss 4" build/analysis/boss-full/leader2 > build/analysis/boss-full/leader2.log 2>&1 ) &
wait
echo "leader studies done"
java -Xmx1500m -cp $CP btk.tools.BossIntentStudy $SRC "Boss 4" build/analysis/boss-full/intent > build/analysis/boss-full/intent.log 2>&1
echo "intent study done"
# merge sabotage.csv halves
head -1 build/analysis/boss-full/leader1/sabotage.csv > build/analysis/boss-full/sabotage.csv
tail -q -n +2 build/analysis/boss-full/leader1/sabotage.csv build/analysis/boss-full/leader2/sabotage.csv >> build/analysis/boss-full/sabotage.csv
head -1 build/analysis/boss-full/leader1/hits.csv > build/analysis/boss-full/hits.csv
tail -q -n +2 build/analysis/boss-full/leader1/hits.csv build/analysis/boss-full/leader2/hits.csv >> build/analysis/boss-full/hits.csv
head -1 build/analysis/boss-full/leader1/games.csv > build/analysis/boss-full/games.csv
tail -q -n +2 build/analysis/boss-full/leader1/games.csv build/analysis/boss-full/leader2/games.csv >> build/analysis/boss-full/games.csv
wc -l build/analysis/boss-full/*.csv build/analysis/boss-full/intent/*.csv
python3 -I tools/boss_proxy/fit_boss_target.py build/analysis/boss-full/sabotage.csv build/analysis/boss-full/intent/turns.csv build/analysis/boss-full/fit.json
python3 -I tools/boss_proxy/fit_boss_hold.py build/analysis/boss-full/sabotage.csv build/analysis/boss-full/intent/turns.csv build/analysis/boss-full/fit.json
echo "ALL DONE"
