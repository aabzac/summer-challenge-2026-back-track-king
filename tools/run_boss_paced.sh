#!/bin/bash
# Paced execution of a fixed boss_batch plan: chunks of CHUNK new games (default 20) with a GAP-second
# pause (default 300) between chunks, i.e. >= 300 s from the completion of global game 20/40/60 to the
# next request intent, plus the runner's ordinary --delay between games. Resumable: rerunning the
# same command continues with the first unplayed slot. Never retries an unknown play (the runner
# stops on API errors; see docs/boss-api.md).
# usage: bash tools/run_boss_paced.sh <plan.json> <session.json> [CHUNK] [GAP] [DELAY] [BOOTSTRAP BOOTSTRAP_SHA256 USER_ID]
#   with the three owner arguments the authenticated-owner lookup adapter (boss_owner_lookup.py batch)
#   is used instead of the plain runner.
set -u
cd "$(dirname "$0")/.."
PLAN=$1; SESSION=$2; CHUNK=${3:-20}; GAP=${4:-300}; DELAY=${5:-20}
BOOT=${6:-}; BOOTSHA=${7:-}; USERID=${8:-}
TOTAL=$(python -I -c "import json,sys; print(len(json.load(open(sys.argv[1],encoding='utf-8'))['slots']))" "$PLAN")
done_count() { python -I -c "import json,sys,os; p=sys.argv[1]; d=json.load(open(p,encoding='utf-8')); print(sum(1 for s in d['slots'] if os.path.exists(os.path.join(os.path.dirname(p), '%04d' % s['slot'], 'result.json'))))" "$PLAN"; }
runner() {
  if [ -n "$BOOT" ]; then
    python tools/boss_owner_lookup.py batch --plan "$PLAN" --session "$SESSION" --bootstrap "$BOOT" --bootstrap-sha256 "$BOOTSHA" --user-id "$USERID" --delay "$DELAY" --max-games "$CHUNK"
  else
    python tools/boss_batch.py run --plan "$PLAN" --session "$SESSION" --delay "$DELAY" --max-games "$CHUNK"
  fi
}
while true; do
  DONE=$(done_count)
  echo "== $(date -u +%FT%TZ) played $DONE/$TOTAL; starting a chunk of up to $CHUNK"
  if [ "$DONE" -ge "$TOTAL" ]; then break; fi
  runner; STATUS=$?
  DONE=$(done_count)
  if [ "$STATUS" -ne 0 ]; then echo "== $(date -u +%FT%TZ) runner stopped with status $STATUS after $DONE/$TOTAL games; not retrying (see the slot's api-error.json)"; exit $STATUS; fi
  if [ "$DONE" -ge "$TOTAL" ]; then break; fi
  echo "== $(date -u +%FT%TZ) gap: sleeping $GAP s before global game $((DONE+1))"
  sleep "$GAP"
done
echo "== $(date -u +%FT%TZ) plan complete: $TOTAL/$TOTAL"
python tools/boss_batch.py report --plan "$PLAN" --session "$SESSION" 2>/dev/null || python tools/boss_batch.py report --plan "$PLAN"
