#!/bin/bash
# Stage 2 "spend the dividend", part 2 (2026-10-22): two separate same-map screens against D, fresh
# pool seed 990000 (first use of the 990000 range), 300 paired games per cell, 2 workers, CG timing.
#   M128 = D + ROLLOUT_MAX_ROUTES=128  (route queries per real turn 2*64 -> 2*128: on 30x20 maps the
#                                      enumeration of pairs x variants x players is no longer truncated)
#   W8   = D + ROLLOUT_WIDTH=8         (route portfolio per player 6 -> 8: more roots and more
#                                      candidate replies, replies/depth unchanged)
# Same design and reading as part 1 (run_dividend_screen.sh): head-to-head vs D, then vs boss-v2 /
# neural-v6 / v09 with D's own rows on the same maps as the control, run last in the same session.
# Windows: CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_dividend2_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/boss-dividend2}; mkdir -p "$OUT"
D="current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true,USE_BOSS_LITE_ROUTES=true,USE_INCREMENTAL_INK_REFRESH=true,USE_FAST_CHEAPEST_PATH=true,USE_BATCHED_REFRESH=true"
M128="$D,ROLLOUT_MAX_ROUTES=128"
W8="$D,ROLLOUT_WIDTH=8"
BOSS2="current?USE_BOSS_SABOTAGE=1"
GAMES=${GAMES:-300}; SEED=${SEED:-990000}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
run M128-vs-D "$M128" "$D"
run W8-vs-D "$W8" "$D"
run M128-vs-boss2 "$M128" "$BOSS2"
run W8-vs-boss2 "$W8" "$BOSS2"
run D-vs-boss2 "$D" "$BOSS2"
run M128-vs-neural-v6 "$M128" neural-v6
run W8-vs-neural-v6 "$W8" neural-v6
run D-vs-neural-v6 "$D" neural-v6
run M128-vs-v09 "$M128" v09
run W8-vs-v09 "$W8" v09
run D-vs-v09 "$D" v09
echo "DIVIDEND-2 SCREEN DONE $(date -u +%FT%TZ)"
