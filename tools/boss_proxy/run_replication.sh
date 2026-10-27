#!/bin/bash
# Policy v5 step 1a (adopted 2026-10-26, docs/benchmarking-v5.md): replicate a step-(a) pass on a fresh pool against the same
# screening proxy, with current as the same-map control, before step (b) and the live step.
#   CAND="current?FLAG=1" NAME=X SEED=<fresh pool> [OUT=build/analysis/replication-X] bash tools/boss_proxy/run_replication.sh
# then:  python tools/boss_proxy/pooled_delta.py <selection dir>:X-vs-boss2 <OUT>:X-vs-boss2 --control cur
# Extra arms: EXTRA="Y=current?FLAG2=1 Z=current?FLAG3=1" adds Y-vs-boss2 and Z-vs-boss2 on the same pool.
# 300 paired games per cell, 2 workers, CG timing (486/36 ms).
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" CAND=... NAME=... SEED=... bash tools/boss_proxy/run_replication.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
: "${SEED:?SEED (a fresh pool) is required}"
NAME=${NAME:-cand}
OUT=${OUT:-build/analysis/replication-$NAME}; mkdir -p "$OUT"
# since the v13 promotion (2026-10-27) the proxy must switch the bypass flag off too (a sabotage spec with it on is refused)
BOSS2="current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0,USE_BYPASS_ROUTES=0"
GAMES=${GAMES:-300}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
run cur-vs-boss2 "current" "$BOSS2"
if [ -n "${CAND:-}" ]; then run "$NAME-vs-boss2" "$CAND" "$BOSS2"; fi
for arm in ${EXTRA:-}; do run "${arm%%=*}-vs-boss2" "${arm#*=}" "$BOSS2"; done
echo "REPLICATION DONE $(date -u +%FT%TZ)"
