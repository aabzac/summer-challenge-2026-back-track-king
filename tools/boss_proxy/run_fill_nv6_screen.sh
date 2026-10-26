#!/bin/bash
# arena-losses-v12 cross-check (2026-10-26): is the leftover-paint fill worth keeping against an arena-like builder?
# The idle-cell diagnostic (corrected split) shows v12 leaves 44.9 % of its cells idle vs the opponents' 26.9 %, most of
# the excess being cells kept to the end on no active connection; fill-off-v1 lost vs the boss-v2 proxy (-3.8 pp), a
# v09-style builder. neural-v6 is the arena-like reference opponent of policy v5's degenerate clause (100 % disrupt profile,
# best live boss sample of the neural line). Fresh pool seed 1008000, 300 paired games per cell, 2 workers, CG timing.
#   F0-vs-nv6    current?USE_ROLLOUT_FILL=0 vs neural-v6
#   cur-vs-nv6   current (v12) vs neural-v6 on the same maps/seeds/seats — the paired delta is the reading
# Windows (docs/evidence/prune-v1/summary.txt has the classpath):
#   CP="build/classes/java/main;build/resources/main;<gson jar>" bash tools/boss_proxy/run_fill_nv6_screen.sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
CP=${CP:-out/classes7:lib/gson-2.13.1.jar}
OUT=${OUT:-build/analysis/fill-nv6-v1}; mkdir -p "$OUT"
CUR="current"
F0="current?USE_ROLLOUT_FILL=0"
NV6="neural-v6"
GAMES=${GAMES:-300}; SEED=${SEED:-1008000}
CELLS=${CELLS:-"F0-vs-nv6 cur-vs-nv6"}
run() { echo "-- $1: $2 vs $3 ($GAMES games, seed $SEED) $(date -u +%FT%TZ)"
  java -Xmx1500m -cp "$CP" btk.runner.BenchmarkRunner "$2" "$3" $GAMES "" 2 $SEED "$OUT/$1.json" cg "" "" 2>&1 | grep -E "win rate|avg score|avg turns|accepted|time/move|error" || true; }
for cell in $CELLS; do
  case $cell in
    F0-vs-nv6)  run $cell "$F0" "$NV6" ;;
    cur-vs-nv6) run $cell "$CUR" "$NV6" ;;
    *) echo "unknown cell $cell"; exit 1 ;;
  esac
done
echo "FILL NV6 SCREEN DONE $(date -u +%FT%TZ)"
