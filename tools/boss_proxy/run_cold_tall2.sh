#!/bin/bash
# cold-timing-30x20-v1, round 2 (2026-10-22 late): cheaper comparisons for budget-bound 30x20 turns.
#   D6 = D + ROLLOUT_DEPTH=6, W4 = D + ROLLOUT_WIDTH=4; cold paired games vs boss-v2 on the same 43
#   generated 30x20 maps (both seats, one JVM per game), interleaved with a fresh cold D control;
#   then hot rows D6 / W4 vs boss-v2 on pool 980000 (300 games).
# Windows: CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_cold_tall2.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/cold-30x20-r2}; mkdir -p "$OUT"
D="current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true,USE_BOSS_LITE_ROUTES=true,USE_INCREMENTAL_INK_REFRESH=true,USE_FAST_CHEAPEST_PATH=true,USE_BATCHED_REFRESH=true"
D6="$D,ROLLOUT_DEPTH=6"; W4="$D,ROLLOUT_WIDTH=4"
BOSS2="current?USE_BOSS_SABOTAGE=1"
SEEDS=$(cat build/analysis/cold-30x20-seeds.txt)
echo "== cold round 2 on $(echo $SEEDS | wc -w) 30x20 seeds $(date -u +%FT%TZ)"
: > "$OUT/cold-D.log"; : > "$OUT/cold-D6.log"; : > "$OUT/cold-W4.log"
for s in $SEEDS; do
  for seat in 0 1; do
    for arm in D D6 W4; do
      spec=${!arm}
      if [ $seat = 0 ]; then a="$spec"; b="$BOSS2"; else a="$BOSS2"; b="$spec"; fi
      java -Xmx1500m -cp "$CP" btk.tools.ColdTurnTiming "$a" "$b" $s >> "$OUT/cold-$arm.log" 2>&1 || echo "FAIL $arm P$seat seed $s" >> "$OUT/cold-$arm.log"
    done
  done
done
echo "== hot rows vs boss-v2, pool 980000 $(date -u +%FT%TZ)"
for arm in D6 W4; do
  spec=${!arm}; echo "-- $arm-vs-boss2 seed 980000"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$spec" "$BOSS2" 300 "" 2 980000 "$OUT/hot-$arm-vs-boss2-980000.json" cg "" "" 2>&1 | grep -E "win rate|time/move|error" || true
done
echo "COLD ROUND 2 DONE $(date -u +%FT%TZ)"
