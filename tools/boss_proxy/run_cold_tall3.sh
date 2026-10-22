#!/bin/bash
# cold-timing-30x20-v1, round 3 = the decisive cold series ("холод-300", 2026-10-22/23).
#   Fresh range 1000000..1000599 (600 generated maps; 159 of them tall: 86 30x20 + 73 29x19).
#   1. HOT reference: D vs boss-v2, all 600 maps x 2 seats in one JVM, 2 workers, CG timing
#      (the tall subset is read per map/seat; the full row is a free all-map D control for the range).
#   2. COLD arms, one JVM per game, both seats, on the 159 tall maps (318 games per arm), interleaved
#      per seed so all arms share machine conditions: D (control), D6 = D + ROLLOUT_DEPTH=6,
#      W4 = D + ROLLOUT_WIDTH=4.
# Reading (predeclared in docs/cold-timing-30x20-v1.md): primary = paired cold D - hot D on the
# 318 tall games; secondary = paired cold D6 - cold D and cold W4 - cold D (same session).
# Windows: CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_cold_tall3.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/cold-tall-v1}; mkdir -p "$OUT"
D="current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true,USE_BOSS_LITE_ROUTES=true,USE_INCREMENTAL_INK_REFRESH=true,USE_FAST_CHEAPEST_PATH=true,USE_BATCHED_REFRESH=true"
D6="$D,ROLLOUT_DEPTH=6"; W4="$D,ROLLOUT_WIDTH=4"
BOSS2="current?USE_BOSS_SABOTAGE=1"
SEEDS=$(cat build/analysis/cold-tall-v1-seeds.txt)
echo "== 1. hot D vs boss-v2, 600 maps (seed 1000000), 1200 games $(date -u +%FT%TZ)"
java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$D" "$BOSS2" 1200 "" 2 1000000 "$OUT/hot-D-vs-boss2-1000000.json" cg "" "" 2>&1 | grep -E "win rate|avg score|time/move|error" || true
echo "== 2. cold arms on $(echo $SEEDS | wc -w) tall seeds, both seats, one JVM per game $(date -u +%FT%TZ)"
: > "$OUT/cold-D.log"; : > "$OUT/cold-D6.log"; : > "$OUT/cold-W4.log"
i=0
for s in $SEEDS; do
  for seat in 0 1; do
    for arm in D D6 W4; do
      spec=${!arm}
      if [ $seat = 0 ]; then a="$spec"; b="$BOSS2"; else a="$BOSS2"; b="$spec"; fi
      java -Xmx1500m -cp "$CP" btk.tools.ColdTurnTiming "$a" "$b" $s >> "$OUT/cold-$arm.log" 2>&1 || echo "FAIL $arm P$seat seed $s" >> "$OUT/cold-$arm.log"
    done
  done
  i=$((i+1)); if [ $((i % 20)) = 0 ]; then echo "  $i seeds done $(date -u +%FT%TZ)"; fi
done
echo "COLD TALL V1 DONE $(date -u +%FT%TZ)"
