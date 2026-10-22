#!/bin/bash
# Stage 2 "spend the dividend" (2026-10-22): two separate same-map screens against D, fresh pool
# seed 980000 (first use of the 980000 range), 300 paired games per cell, 2 workers, CG timing.
#   R3  = D + ROLLOUT_REPLIES=3   (opponent replies per root inside the rollout: 2 -> 3)
#   D10 = D + ROLLOUT_DEPTH=10    (rollout depth in simulated turns: 8 -> 10)
# Each candidate: head-to-head vs D, then vs boss-v2 / neural-v6 / v09 with D's own rows on the
# same maps as the control (run last, same seeds and seats). Read: keep a candidate only if
# >= 50 % vs D head-to-head and no row below D's control row by more than the cell error
# (+-5.7 pp at 300 games); v5 step (a) continuation needs candidate >= D control + 3 pp vs boss-v2.
# Windows: CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_dividend_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/boss-dividend}; mkdir -p "$OUT"
D="current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true,USE_BOSS_LITE_ROUTES=true,USE_INCREMENTAL_INK_REFRESH=true,USE_FAST_CHEAPEST_PATH=true,USE_BATCHED_REFRESH=true"
R3="$D,ROLLOUT_REPLIES=3"
D10="$D,ROLLOUT_DEPTH=10"
BOSS2="current?USE_BOSS_SABOTAGE=1"
GAMES=${GAMES:-300}; SEED=${SEED:-980000}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
run R3-vs-D "$R3" "$D"
run D10-vs-D "$D10" "$D"
run R3-vs-boss2 "$R3" "$BOSS2"
run D10-vs-boss2 "$D10" "$BOSS2"
run D-vs-boss2 "$D" "$BOSS2"
run R3-vs-neural-v6 "$R3" neural-v6
run D10-vs-neural-v6 "$D10" neural-v6
run D-vs-neural-v6 "$D" neural-v6
run R3-vs-v09 "$R3" v09
run D10-vs-v09 "$D10" v09
run D-vs-v09 "$D" v09
echo "DIVIDEND SCREEN DONE $(date -u +%FT%TZ)"
