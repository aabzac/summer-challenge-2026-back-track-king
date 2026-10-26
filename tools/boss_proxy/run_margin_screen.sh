#!/bin/bash
# boss-v3 (2026-10-26): does a proxy that agrees more with the live boss (hold margin 0 instead of 1.0: 58.8 % vs 54.1 %
# on K1's live games) flip corridor-routes-v1's sign? K1 = current?USE_CORRIDOR_ROUTES=true against the margin-0 proxy
# with current (= v12) as the same-map control; fresh pool seed 1010000, 300 paired games per cell, 2 workers, CG timing.
#   K1-vs-boss2m0   K1 vs the proxy with BOSS_HOLD_MARGIN=0
#   cur-vs-boss2m0  current vs the same proxy on the same maps/seeds/seats — the paired delta is the reading
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_margin_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/margin-v1}; mkdir -p "$OUT"
CUR="current"
K1="current?USE_CORRIDOR_ROUTES=true"
BOSS2M0="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0,BOSS_HOLD_MARGIN=0"
GAMES=${GAMES:-300}; SEED=${SEED:-1010000}
CELLS=${CELLS:-"cur-vs-boss2m0 K1-vs-boss2m0"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    cur-vs-boss2m0) run $cell "$CUR" "$BOSS2M0" ;;
    K1-vs-boss2m0)  run $cell "$K1" "$BOSS2M0" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "MARGIN SCREEN DONE $(date -u +%FT%TZ)"
