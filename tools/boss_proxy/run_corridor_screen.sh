#!/bin/bash
# corridor-routes-v1 (2026-10-26): v5 steps (a)+(b) for the shared-corridor route proposals on current (= v12), plus the
# arena-like cross-check against neural-v6 (arena-losses-v12: the field's top half beats v12 on multiplicity, which the
# boss-v2 proxy cannot see). Fresh pool seed 1009000 (first use), 300 paired games per cell, 2 workers, CG timing (486/36 ms).
#   K1 = current?USE_CORRIDOR_ROUTES=true                     a free cell's paint / (1 + 1 x other pairs using it)
#   K2 = current?USE_CORRIDOR_ROUTES=true,CORRIDOR_WEIGHT=2   paint / (1 + 2 x other pairs using it)
#   X-vs-boss2   each arm and the control (current) against the boss-like screening proxy on the same maps/seeds/seats;
#                the reading is the paired per-map delta vs cur-vs-boss2 (v5 step a: continue only at >= control + 3 pp)
#   X-vs-cur     head-to-head with the selected version (v5 step b: continue only at >= 48 %)
#   X-vs-nv6     each arm and the control against neural-v6, the arena-like builder; paired delta vs cur-vs-nv6
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_corridor_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/corridor-v1}; mkdir -p "$OUT"
CUR="current"
K1="current?USE_CORRIDOR_ROUTES=true"
K2="current?USE_CORRIDOR_ROUTES=true,CORRIDOR_WEIGHT=2"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0"
NV6="neural-v6"
GAMES=${GAMES:-300}; SEED=${SEED:-1009000}
CELLS=${CELLS:-"cur-vs-boss2 K1-vs-boss2 K2-vs-boss2 K1-vs-cur K2-vs-cur cur-vs-nv6 K1-vs-nv6 K2-vs-nv6"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    cur-vs-boss2) run $cell "$CUR" "$BOSS2" ;;
    K1-vs-boss2)  run $cell "$K1" "$BOSS2" ;;
    K2-vs-boss2)  run $cell "$K2" "$BOSS2" ;;
    K1-vs-cur)    run $cell "$K1" "$CUR" ;;
    K2-vs-cur)    run $cell "$K2" "$CUR" ;;
    cur-vs-nv6)   run $cell "$CUR" "$NV6" ;;
    K1-vs-nv6)    run $cell "$K1" "$NV6" ;;
    K2-vs-nv6)    run $cell "$K2" "$NV6" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "CORRIDOR SCREEN DONE $(date -u +%FT%TZ)"
