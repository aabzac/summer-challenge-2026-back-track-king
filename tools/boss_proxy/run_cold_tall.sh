#!/bin/bash
# cold-timing-30x20-v1 (2026-10-22): is D's arena weakness on 30x20 a cold-JVM timing effect?
#  A. strict process check of D's exact artifact (build/submit-stage2-d) on 12 CG 30x20 maps, one
#     fresh JVM per game: share of later turns at the 36-38 ms ceiling, maxima, timeouts.
#  B. cold paired games, one JVM per game (btk.tools.ColdTurnTiming, CG 486/36 ms): D vs boss-v2 on
#     the 43 generated 30x20 maps of the 980000/990000 pools, both seats -> compare per map/seat
#     with the HOT bench results of the same maps (D-vs-boss2.json of both pools).
#  C. same cold games for M32 = D + ROLLOUT_MAX_ROUTES=32 (route enumeration capped at 64 queries
#     instead of 128, leaving more of a budget-bound turn to the root x reply search), interleaved
#     with B so both arms see the same machine conditions; then M32's HOT rows vs boss-v2 on both
#     full pools (300 games each) so the hot/cold x D/M32 square is complete.
# Windows: CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_cold_tall.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/cold-30x20}; mkdir -p "$OUT"
D="current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true,USE_BOSS_LITE_ROUTES=true,USE_INCREMENTAL_INK_REFRESH=true,USE_FAST_CHEAPEST_PATH=true,USE_BATCHED_REFRESH=true"
M32="$D,ROLLOUT_MAX_ROUTES=32"
BOSS2="current?USE_BOSS_SABOTAGE=1"
SEEDS=$(cat build/analysis/cold-30x20-seeds.txt)
echo "== A: strict procCheck of the exact D artifact on replays/cg-30x20 $(date -u +%FT%TZ)"
java -Xmx1500m -cp "$CP" btk.runner.ProcessCheck v09 12 replays/cg-30x20 0 "$OUT/procheck-D-30x20.json" false build/submit-stage2-d 2>&1 | tail -5 || true
echo "== B/C: cold paired games on $(echo $SEEDS | wc -w) 30x20 seeds, both seats, one JVM per game $(date -u +%FT%TZ)"
: > "$OUT/cold-D.log"; : > "$OUT/cold-M32.log"
for s in $SEEDS; do
  java -Xmx1500m -cp "$CP" btk.tools.ColdTurnTiming "$D" "$BOSS2" $s >> "$OUT/cold-D.log" 2>&1 || echo "FAIL D P0 seed $s" >> "$OUT/cold-D.log"
  java -Xmx1500m -cp "$CP" btk.tools.ColdTurnTiming "$M32" "$BOSS2" $s >> "$OUT/cold-M32.log" 2>&1 || echo "FAIL M32 P0 seed $s" >> "$OUT/cold-M32.log"
  java -Xmx1500m -cp "$CP" btk.tools.ColdTurnTiming "$BOSS2" "$D" $s >> "$OUT/cold-D.log" 2>&1 || echo "FAIL D P1 seed $s" >> "$OUT/cold-D.log"
  java -Xmx1500m -cp "$CP" btk.tools.ColdTurnTiming "$BOSS2" "$M32" $s >> "$OUT/cold-M32.log" 2>&1 || echo "FAIL M32 P1 seed $s" >> "$OUT/cold-M32.log"
done
echo "== hot M32 rows vs boss-v2 on both pools $(date -u +%FT%TZ)"
for seed in 980000 990000; do
  echo "-- M32-vs-boss2 seed $seed"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$M32" "$BOSS2" 300 "" 2 $seed "$OUT/hot-M32-vs-boss2-$seed.json" cg "" "" 2>&1 | grep -E "win rate|time/move|error" || true
done
echo "COLD TALL DONE $(date -u +%FT%TZ)"
