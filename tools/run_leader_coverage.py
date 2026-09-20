"""Behavioral coverage check of the retained sabotage flags against the leader-v1 reference.

Not a promotion gate. The v08 self-play mirror never produced the meta these flags target
(92-turn games, ~56% placement, ~64% accepted disruption), which is the shared structural reason
live-target-v1, always-disrupt-v1 and track-value-v1 all failed the practical bench. leader-v1
(btk.bot.reference.LeaderBot, calibrated on the arena leader) disrupts on 100% of turns and
spends full paint, so this run asks one descriptive question per flag: against an opponent that
behaves like the arena leader, does the flag change our result and our behavior, and in which
direction? A positive answer is a reason to design a new experiment, not a promotion.

Four arms, all against leader-v1 on the SAME fresh 150 paired maps (matched panel, so per-map
deltas against the control are meaningful), CG timing, two workers, 300 games each:

  control          v08
  track-value-self current?USE_ROLLOUT_TRACK_VALUE=1,ROLLOUT_TRACK_VALUE_SELF_ONLY=1
  live-target      current?USE_ROLLOUT_LIVE_TARGET=1
  always-disrupt   current?ROLLOUT_ALWAYS_DISRUPT=1

  python tools/run_leader_coverage.py            # runs every missing arm in order, then summary
  python tools/run_leader_coverage.py control    # one arm

Pool bench/pools/leader-coverage-v1.json (seeds 540000..540149) is registered on first use. Every
map identity from prior reports under build/bench, build/analysis and docs/evidence is passed to
-PexcludeReports once, before the first arm; the four arms deliberately share the pool. Reports,
logs, the exclusion index and the summary are write-once; an existing report is reused, never
replaced. No HTTP, upload, promotion, retries or pooling. See docs/indrill-study-v1.md.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from practical_gate import read_report  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
NAME = 'leader-coverage-v1'
FOLDER = ROOT / 'build/bench' / NAME
POOL = ROOT / 'bench/pools' / f'{NAME}.json'
OPPONENT = 'leader-v1'
GAMES = 300
ARMS = {
    'control': 'v08',
    'track-value-self': 'current?USE_ROLLOUT_TRACK_VALUE=1,ROLLOUT_TRACK_VALUE_SELF_ONLY=1',
    'live-target': 'current?USE_ROLLOUT_LIVE_TARGET=1',
    'always-disrupt': 'current?ROLLOUT_ALWAYS_DISRUPT=1',
}
POOL_MANIFEST = {'schemaVersion': 1, 'id': NAME, 'purpose': 'development',
                 'generator': 'cg-sha1prng', 'seedStart': 540000, 'pairs': GAMES // 2}
PRIOR_ROOTS = ('build/bench', 'build/analysis', 'docs/evidence')


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def ensure_pool() -> Path:
    text = json.dumps(POOL_MANIFEST, indent=2) + '\n'
    if POOL.exists():
        if POOL.read_text(encoding='utf-8') != text:
            raise SystemExit(f'pool manifest differs from the registered one: {POOL}')
    else:
        POOL.write_text(text, encoding='utf-8')
    return POOL


def collect_maps(node, into: dict) -> None:
    """Collect every gamesPerMap mapping found anywhere in a JSON document."""
    if isinstance(node, dict):
        maps = node.get('gamesPerMap')
        if isinstance(maps, dict):
            for key, value in maps.items():
                if isinstance(key, str) and len(key) == 64:
                    into[key] = into.get(key, 0) + int(value)
        for value in node.values():
            collect_maps(value, into)
    elif isinstance(node, list):
        for value in node:
            collect_maps(value, into)


def exclusion_index() -> Path:
    out = FOLDER / 'excluded.json'
    if out.exists():
        return out
    merged, sources = {}, {}
    for root in PRIOR_ROOTS:
        for path in sorted((ROOT / root).rglob('*.json')):
            if path.parent == FOLDER:
                continue
            try:
                data = json.loads(path.read_bytes())
            except (ValueError, OSError):
                continue
            found = {}
            collect_maps(data, found)
            if found:
                sources[path.relative_to(ROOT).as_posix()] = len(found)
                for key, value in found.items():
                    merged[key] = merged.get(key, 0) + value
    out.write_text(json.dumps({'gamesPerMap': merged, 'sourceFiles': sources}), encoding='utf-8')
    print(f'exclusion index: {len(merged)} prior maps from {len(sources)} files -> {out.relative_to(ROOT)}', flush=True)
    return out


def run_arm(arm: str, pool: Path, excluded: Path) -> Path:
    out = FOLDER / f'{arm}.json'
    if out.exists():
        print(f'REUSE {arm}: {out.relative_to(ROOT)} (write-once, never replaced)', flush=True)
        return out
    args = [str(ROOT / 'gradlew.bat'), 'bench', '--console=plain',
            '-Pbot1=' + ARMS[arm], '-Pbot2=' + OPPONENT, '-Pthreads=2', '-Ptiming=cg',
            '-Ppool=' + pool.relative_to(ROOT).as_posix(), '-Pout=' + out.relative_to(ROOT).as_posix(),
            '-PexcludeReports=' + excluded.relative_to(ROOT).as_posix()]
    print('RUN', arm, ' '.join(args[1:]), flush=True)
    with (FOLDER / f'{arm}.log').open('x', encoding='utf-8') as log:
        proc = subprocess.Popen(args, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, encoding='utf-8', errors='replace')
        for line in proc.stdout:
            log.write(line)
            if f'/{GAMES}' in line or 'report' in line or 'FAIL' in line or 'Exception' in line:
                print(line.rstrip(), flush=True)
        proc.wait()
    if proc.returncode != 0:
        raise SystemExit(f'bench {arm} failed with exit code {proc.returncode}; see {arm}.log')
    return out


def describe(arm: str, report: dict) -> dict:
    data = report['data']
    behavior = data['behavior']
    return {
        'arm': arm, 'spec': ARMS[arm], 'opponent': OPPONENT,
        'path': report['path'], 'sha256': report['sha256'],
        'games': data['games'], 'wins': data['winsA'], 'losses': data['winsB'], 'draws': data['draws'],
        'score': report['score'], 'confidence95': data['confidence95'],
        'winsAsP0': data['winsAasP0'], 'winsAsP1': data['winsAasP1'],
        'avgDiff': behavior['avgDiff'], 'avgTurns': behavior['avgTurns'],
        'closedEarly': behavior['closedEarly'],
        'placementRate': behavior['placementRate'], 'disruptRate': behavior['disruptRate'],
        'cellsPerTurn': [c / data['turns'] for c in behavior['placedCells']],
        'paintPerTurn': [p / data['turns'] for p in behavior['paintSpent']],
        'maxMs': [data['maxMsA'], data['maxMsB']], 'slow': [data['slowA'], data['slowB']],
        'errors': [data['errorsA'], data['errorsB']], 'elapsedMs': data['elapsedMs'],
    }


def matched_deltas(candidate: dict, control: dict) -> dict:
    if candidate['maps'].keys() != control['maps'].keys():
        raise SystemExit('arms were not played on the same maps')
    deltas, cand_only, ctrl_only, both = [], 0, 0, 0
    for key, ca in candidate['maps'].items():
        co = control['maps'][key]
        if ca['seed'] != co['seed'] or ca['dimensions'] != co['dimensions']:
            raise SystemExit('map metadata differs between arms')
        deltas.append((sum(ca['halfWins']) - sum(co['halfWins'])) / 4)
        for side in range(2):
            c, k = ca['halfWins'][side] == 2, co['halfWins'][side] == 2
            cand_only += c and not k
            ctrl_only += k and not c
            both += c and k
    n = len(deltas)
    mean = sum(deltas) / n
    var = sum((d - mean) ** 2 for d in deltas) / (n - 1)
    se = (var / n) ** 0.5
    return {'maps': n, 'meanMapScoreDelta': mean, 'standardError': se,
            'approx95': [mean - 1.96 * se, mean + 1.96 * se],
            'halfWinUnits': candidate['halfWins'] - control['halfWins'],
            'gamesCandidateOnlyWon': cand_only, 'gamesControlOnlyWon': ctrl_only, 'gamesBothWon': both,
            'mapsBetter': sum(d > 0 for d in deltas), 'mapsWorse': sum(d < 0 for d in deltas),
            'mapsEqual': sum(d == 0 for d in deltas)}


def summarize(reports: dict[str, dict]) -> dict:
    arms = {arm: describe(arm, rep) for arm, rep in reports.items()}
    control = reports['control']
    deltas = {arm: matched_deltas(rep, control) for arm, rep in reports.items() if arm != 'control'}
    return {'name': NAME, 'role': 'behavioral coverage check against a local reference; not a promotion gate',
            'opponent': OPPONENT, 'pool': POOL.relative_to(ROOT).as_posix(), 'poolSha256': sha(POOL),
            'poolManifest': POOL_MANIFEST, 'excludedIndex': (FOLDER / 'excluded.json').relative_to(ROOT).as_posix(),
            'writtenAt': datetime.now(timezone.utc).isoformat(), 'runnerSha256': sha(Path(__file__)),
            'conditions': {k: control['data'][k] for k in
                           ('timingProfile', 'firstSearchBudgetMs', 'turnSearchBudgetMs', 'threads', 'javaVersion')},
            'arms': arms, 'matchedVsControl': deltas}


def print_table(summary: dict) -> None:
    print(f"\n{'arm':18}{'W-L-D':>12}{'score':>8}{'diff':>8}{'turns':>7}{'closed':>7}"
          f"{'place%':>8}{'disr%':>7}{'oppDisr%':>9}{'dMap':>8}", flush=True)
    for arm, a in summary['arms'].items():
        d = summary['matchedVsControl'].get(arm)
        print(f"{arm:18}{f'{a['wins']}-{a['losses']}-{a['draws']}':>12}{a['score']:8.2%}{a['avgDiff']:8.0f}"
              f"{a['avgTurns']:7.1f}{a['closedEarly']:7d}{a['placementRate'][0]:8.1%}{a['disruptRate'][0]:7.1%}"
              f"{a['disruptRate'][1]:9.1%}{(d['meanMapScoreDelta'] if d else 0):+8.3f}", flush=True)


def main(selected: list[str]) -> None:
    FOLDER.mkdir(parents=True, exist_ok=True)
    pool = ensure_pool()
    excluded = exclusion_index()
    for arm in selected:
        run_arm(arm, pool, excluded)
    reports = {}
    for arm in ARMS:
        path = FOLDER / f'{arm}.json'
        if path.exists():
            reports[arm] = read_report(path, GAMES)
    if reports.keys() != ARMS.keys():
        print('arms still missing:', ', '.join(a for a in ARMS if a not in reports), flush=True)
        return
    prior = set(json.loads(excluded.read_bytes())['gamesPerMap'])
    for arm, rep in reports.items():
        if prior & set(rep['maps']):
            raise SystemExit(f'{arm}: previously evaluated map reused')
    summary = summarize(reports)
    out = FOLDER / 'summary.json'
    if out.exists():
        print(f'summary already exists, not replaced: {out.relative_to(ROOT)}', flush=True)
    else:
        out.write_text(json.dumps(summary, indent=1), encoding='utf-8')
        print('summary ->', out.relative_to(ROOT), flush=True)
    print_table(summary)


if __name__ == '__main__':
    chosen = sys.argv[1:] or list(ARMS)
    if any(a not in ARMS for a in chosen):
        raise SystemExit(__doc__)
    main(chosen)
