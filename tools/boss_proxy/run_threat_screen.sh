#!/bin/bash
# threat-weight-v1 (2026-10-26): v5 step (a) + h2h for ROLLOUT_THREAT_WEIGHT (default 0.5, never screened): a route proposal's
# value blends its plain gain and its gain if the predicted threat region inks, (1 - w) * gain + w * protectedGain. Since v12 the
# exposure-income discount prices part of the same risk, so the blend may be off its optimum. Fresh pool seed 1016000 (first use),
# 300 paired games per cell, 2 workers, CG timing (486/36 ms); an arm that passes step (a) goes to step 1a (run_replication.sh)
# on pool 1017000.
#   T25 = current?ROLLOUT_THREAT_WEIGHT=0.25     T75 = current?ROLLOUT_THREAT_WEIGHT=0.75
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_threat_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/threat-v1}; mkdir -p "$OUT"
CUR="current"
T25="current?ROLLOUT_THREAT_WEIGHT=0.25"
T75="current?ROLLOUT_THREAT_WEIGHT=0.75"
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0"
GAMES=${GAMES:-300}; SEED=${SEED:-1016000}
CELLS=${CELLS:-"cur-vs-boss2 T25-vs-boss2 T75-vs-boss2 T25-vs-cur T75-vs-cur"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    cur-vs-boss2) run $cell "$CUR" "$BOSS2" ;;
    T25-vs-boss2) run $cell "$T25" "$BOSS2" ;;
    T75-vs-boss2) run $cell "$T75" "$BOSS2" ;;
    T25-vs-cur)   run $cell "$T25" "$CUR" ;;
    T75-vs-cur)   run $cell "$T75" "$CUR" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "THREAT SCREEN DONE $(date -u +%FT%TZ)"
