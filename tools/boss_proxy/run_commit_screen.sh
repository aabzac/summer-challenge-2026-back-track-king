#!/bin/bash
# route-commit-v1 (2026-10-27): v5 step (a) + h2h for route commitment on current (= v12). The order study of the arena games
# (docs/route-order-v1.md): v12 leaves routes half-built — 30 % of a completion's cells are placed >= 4 turns before its first
# income (opponents 13 %), half the paint 2.9 turns before income (1.5). A root that continues last turn's chosen route gets
# COMMIT_BONUS rollout-value points. Fresh pool seed 1017000 (first use), 300 paired games per cell, 2 workers, CG timing;
# an arm that passes step (a) goes to step 1a (run_replication.sh) on pool 1018000.
#   C10 = current?USE_ROUTE_COMMIT=true                    C25 = current?USE_ROUTE_COMMIT=true,COMMIT_BONUS=25
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_commit_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/commit-v1}; mkdir -p "$OUT"
CUR="current"
C10="current?USE_ROUTE_COMMIT=true"
C25="current?USE_ROUTE_COMMIT=true,COMMIT_BONUS=25"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0"
GAMES=${GAMES:-300}; SEED=${SEED:-1017000}
CELLS=${CELLS:-"cur-vs-boss2 C10-vs-boss2 C25-vs-boss2 C10-vs-cur C25-vs-cur"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    cur-vs-boss2) run $cell "$CUR" "$BOSS2" ;;
    C10-vs-boss2) run $cell "$C10" "$BOSS2" ;;
    C25-vs-boss2) run $cell "$C25" "$BOSS2" ;;
    C10-vs-cur)   run $cell "$C10" "$CUR" ;;
    C25-vs-cur)   run $cell "$C25" "$CUR" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "COMMIT SCREEN DONE $(date -u +%FT%TZ)"
