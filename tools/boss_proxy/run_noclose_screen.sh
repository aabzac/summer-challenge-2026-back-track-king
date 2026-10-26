#!/bin/bash
# boss-v4 validation (2026-10-26): does a proxy that declines one-hit closures like the live boss (USE_BOSS_NO_CLOSE) reproduce
# the live signs the policy proxy got wrong? Predeclared reading, before the first game: the no-close proxy is validated for
# construction changes if, on the same maps/seeds/seats with current (= v12) as the control,
#   K1 (corridor-routes, live -15 pp) delta <= 0,  Q (earning-delay, live -5 pp) delta <= 0,
#   and the v11 configuration (current?USE_EXPOSURE_INCOME=0; live: v12 beat it by +5 pp) delta <= 0.
# K1 against the policy proxy runs on the same pool for a same-map comparison of the two proxies' verdicts.
# Fresh pool seed 1014000 (first use), 300 paired games per cell, 2 workers, CG timing (486/36 ms).
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_noclose_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/noclose-v1}; mkdir -p "$OUT"
CUR="current"
K1="current?USE_CORRIDOR_ROUTES=true"
Q="current?USE_QUICK_ALWAYS=true"
V11="current?USE_EXPOSURE_INCOME=0"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0"
NC="$BOSS2,USE_BOSS_NO_CLOSE=true"
GAMES=${GAMES:-300}; SEED=${SEED:-1014000}
CELLS=${CELLS:-"cur-vs-nc K1-vs-nc Q-vs-nc V11-vs-nc cur-vs-boss2 K1-vs-boss2"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    cur-vs-nc)    run $cell "$CUR" "$NC" ;;
    K1-vs-nc)     run $cell "$K1" "$NC" ;;
    Q-vs-nc)      run $cell "$Q" "$NC" ;;
    V11-vs-nc)    run $cell "$V11" "$NC" ;;
    cur-vs-boss2) run $cell "$CUR" "$BOSS2" ;;
    K1-vs-boss2)  run $cell "$K1" "$BOSS2" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "NOCLOSE SCREEN DONE $(date -u +%FT%TZ)"
