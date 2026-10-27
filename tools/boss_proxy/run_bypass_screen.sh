#!/bin/bash
# bypass-routes-v1 (2026-10-27): v5 step (a) + h2h for bypass proposals on current (= v12). late-game-v1 (docs/late-game-v1.md):
# the last third is not a lever; in the middle third of v12's arena losses its paint earns 35 % vs the opponents' 62 %, and it puts
# 9 % of that paint on bypasses around unstable regions vs 20 % (v12 bypasses only its one predicted threat region). For each own
# earning connection crossing regions at instability >= BYPASS_MIN_INSTABILITY the bot proposes the cheapest route avoiding them.
# Fresh pool seed 1019000 (first use), 300 paired games per cell, 2 workers, CG timing; an arm that passes step (a) goes to
# step 1a (run_replication.sh) on pool 1020000.
#   B2 = current?USE_BYPASS_ROUTES=true                  B3 = current?USE_BYPASS_ROUTES=true,BYPASS_MIN_INSTABILITY=3
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_bypass_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/bypass-v1}; mkdir -p "$OUT"
CUR="current"
B2="current?USE_BYPASS_ROUTES=true"
B3="current?USE_BYPASS_ROUTES=true,BYPASS_MIN_INSTABILITY=3"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0"
GAMES=${GAMES:-300}; SEED=${SEED:-1019000}
CELLS=${CELLS:-"cur-vs-boss2 B2-vs-boss2 B3-vs-boss2 B2-vs-cur B3-vs-cur"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    cur-vs-boss2) run $cell "$CUR" "$BOSS2" ;;
    B2-vs-boss2)  run $cell "$B2" "$BOSS2" ;;
    B3-vs-boss2)  run $cell "$B3" "$BOSS2" ;;
    B2-vs-cur)    run $cell "$B2" "$CUR" ;;
    B3-vs-cur)    run $cell "$B3" "$CUR" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "BYPASS SCREEN DONE $(date -u +%FT%TZ)"
