#!/bin/bash
# earning-delay-v1 (2026-10-26): v5 steps (a)+(b) plus the neural-v6 cross-check for two earning-delay arms on current
# (= v12). The arena's top half earns 2 turns after placement, we 4 (docs/arena-losses-v12.md); both arms live inside the
# proxy's competent zone (regions the boss values by income). Fresh pool seed 1011000 (first use), 300 paired games per
# cell, 2 workers, CG timing (486/36 ms).
#   Q  = current?USE_QUICK_ALWAYS=true   the cheapest positive completion (<= 6 paint) is kept among the roots every turn
#   D2 = current?DELAY_POWER=2           a proposal's value divides by turns-to-complete squared instead of once
#   X-vs-boss2   each arm and the control against the boss-like screening proxy on the same maps/seeds/seats;
#                the reading is the paired per-map delta vs cur-vs-boss2 (v5 step a: continue only at >= control + 3 pp)
#   X-vs-cur     head-to-head with the selected version (v5 step b: continue only at >= 48 %)
#   X-vs-nv6     each arm and the control against neural-v6, the arena-like builder; paired delta vs cur-vs-nv6
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_delay_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/delay-v1}; mkdir -p "$OUT"
CUR="current"
Q="current?USE_QUICK_ALWAYS=true"
D2="current?DELAY_POWER=2"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0"
NV6="neural-v6"
GAMES=${GAMES:-300}; SEED=${SEED:-1011000}
CELLS=${CELLS:-"cur-vs-boss2 Q-vs-boss2 D2-vs-boss2 Q-vs-cur D2-vs-cur cur-vs-nv6 Q-vs-nv6 D2-vs-nv6"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    cur-vs-boss2) run $cell "$CUR" "$BOSS2" ;;
    Q-vs-boss2)   run $cell "$Q" "$BOSS2" ;;
    D2-vs-boss2)  run $cell "$D2" "$BOSS2" ;;
    Q-vs-cur)     run $cell "$Q" "$CUR" ;;
    D2-vs-cur)    run $cell "$D2" "$CUR" ;;
    cur-vs-nv6)   run $cell "$CUR" "$NV6" ;;
    Q-vs-nv6)     run $cell "$Q" "$NV6" ;;
    D2-vs-nv6)    run $cell "$D2" "$NV6" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "DELAY SCREEN DONE $(date -u +%FT%TZ)"
