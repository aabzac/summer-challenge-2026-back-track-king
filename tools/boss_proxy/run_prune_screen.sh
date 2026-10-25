#!/bin/bash
# prune-v1 (2026-10-25): regression guard for the pruned current (v10 behaviour with the rejected rollout
# experiments and their Params fields removed) against the frozen v10 copy, fresh pool seed 1001000
# (first use of the 1001000 range), 300 paired games per cell, 2 workers, CG timing (486/36 ms).
#   P-vs-F      head-to-head of the pruned current (P) and frozen v10 (F): the same algorithm, so the
#               expected rate is 50 %; the v5 step (b) reading applies (continue only at P >= 48 %).
#   P-vs-boss2  both arms against the boss-like screening proxy on the same maps/seeds/seats, F last
#   F-vs-boss2  in the same session as the control; the paired per-map delta is the reading.
# Equivalence itself is established by btk.bot.PruneV1Test (ample-time decisions identical to v10);
# this screen only guards the timed path. Windows:
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_prune_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/prune-v1}; mkdir -p "$OUT"
P="current"
F="v10"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0"
GAMES=${GAMES:-300}; SEED=${SEED:-1001000}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
run P-vs-F "$P" "$F"
run P-vs-boss2 "$P" "$BOSS2"
run F-vs-boss2 "$F" "$BOSS2"
echo "PRUNE SCREEN DONE $(date -u +%FT%TZ)"
