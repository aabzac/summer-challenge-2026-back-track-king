"""Run one practical-1000-futility-boss-cache-v4 local comparison: candidate vs a control.

Plays three registered blocks of disjoint fresh maps against the selected control and pools them
into a single 1000-game decision, stopping early only on a futility look (pooled score strictly
below 50% at cumulative 300 or 500 games). All prior benchmark map identities are excluded before
any game; reports and decisions are write-once. No HTTP, upload, promotion, tuning or retries.

  python tools/run_practical_v4.py --spec "current?USE_ROLLOUT_TRACK_VALUE=1" --name track-value-v4 --seed 540000

The 40-game cached-boss check is separate (`tools/boss_cached.py`); by policy it may be launched
concurrently once the 500-game look clears, and it can never rescue a local failure. Combine both
domains with `python tools/practical_gate_v4.py --block1 ... --block2 ... --block3 ... --boss-plan ...`.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import practical_gate_v4 as gate  # noqa: E402
from practical_gate import read_report  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
BENCH = ROOT / 'build/bench'
# name, games, pairs, seed offset, cumulative games after this block
BLOCKS = [('block1', 300, 150, 0), ('block2', 200, 100, 100_000), ('block3', 500, 250, 200_000)]


def pool_path(name, block):
    return ROOT / 'bench/pools' / f'{name}-{block}.json'


def ensure_pool(name, block, pairs, seed_start):
    path = pool_path(name, block)
    manifest = {'schemaVersion': 1, 'id': f'{name}-{block}', 'purpose': 'confirmation',
                'generator': 'cg-sha1prng', 'seedStart': seed_start, 'pairs': pairs}
    text = json.dumps(manifest, indent=2) + '\n'
    if path.exists():
        if path.read_text(encoding='utf-8') != text:
            raise SystemExit(f'pool manifest differs from the registered one: {path}')
    else:
        path.write_text(text, encoding='utf-8')
    return path


def build_exclusion_index(folder, block):
    """Presence-only map union over prior reports/indices and this run's earlier blocks."""
    merged, sources = {}, {}
    reports = []
    for path in sorted(BENCH.rglob('*.json')):
        if folder in path.parents:
            continue  # this experiment's own folder is added explicitly below
        try:
            data = json.loads(path.read_bytes())
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
        if isinstance(data, dict) and isinstance(data.get('gamesPerMap'), dict) and data['gamesPerMap']:
            reports.append(path)
    reports += sorted(p for p in folder.glob('*.json')
                      if not p.name.endswith(('-excluded.json', '-decision.json')))
    for path in reports:
        data = json.loads(path.read_bytes())
        maps = data.get('gamesPerMap')
        if not isinstance(maps, dict) or not maps:
            continue
        for key, value in maps.items():
            if type(value) is not int or value <= 0:
                raise SystemExit(f'expected positive integer map count in {path}: {key}={value!r}')
            # Prior indices overlap: exclusion needs membership, not recursive game totals.
            merged[key] = 1
        sources[path.relative_to(ROOT).as_posix()] = len(maps)
    out = folder / f'{block}-excluded.json'
    if out.exists():
        raise SystemExit(f'exclusion index already exists: {out}')
    out.write_text(json.dumps({'gamesPerMap': merged, 'sourceFiles': sources}), encoding='utf-8')
    print(f'exclusion index: {len(merged)} prior maps from {len(sources)} files -> {out.relative_to(ROOT)}', flush=True)
    return out


def run_block(name, spec, control, block, games, pairs, seed_start):
    folder = BENCH / name
    folder.mkdir(parents=True, exist_ok=True)
    out = folder / f'{block}.json'
    if out.exists():
        raise SystemExit(f'report already exists, never replaced: {out}')
    pool = ensure_pool(name, block, pairs, seed_start)
    excluded = build_exclusion_index(folder, block)
    args = [str(ROOT / 'gradlew.bat'), 'bench', '--console=plain',
            '-Pbot1=' + spec, '-Pbot2=' + control, '-Pthreads=2', '-Ptiming=cg',
            '-Ppool=' + pool.relative_to(ROOT).as_posix(), '-Pout=' + out.relative_to(ROOT).as_posix(),
            '-PexcludeReports=' + excluded.relative_to(ROOT).as_posix()]
    print('RUN', ' '.join(args[1:]), flush=True)
    with (folder / f'{block}.log').open('x', encoding='utf-8') as log:
        proc = subprocess.Popen(args, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, encoding='utf-8', errors='replace')
        for line in proc.stdout:
            log.write(line)
            if any(tag in line for tag in ('/300', '/200', '/500', 'A win rate', 'report:', 'FAIL', 'Exception')):
                print(line.rstrip(), flush=True)
        proc.wait()
    if proc.returncode != 0:
        raise SystemExit(f'bench failed with exit code {proc.returncode}; see {block}.log')
    return read_report(out, games)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--spec', required=True, help='candidate bot spec, e.g. current?USE_ROLLOUT_TRACK_VALUE=1')
    parser.add_argument('--control', default='v09')
    parser.add_argument('--name', required=True, help='experiment id; used for the report folder and pools')
    parser.add_argument('--seed', type=int, required=True, help='base seed for block 1; blocks are 100k apart')
    args = parser.parse_args()
    folder = BENCH / args.name
    reports = []
    for block, games, pairs, offset in BLOCKS:
        reports.append(run_block(args.name, args.spec, args.control, block, games, pairs, args.seed + offset))
        result = gate.assess(*reports)
        decision = folder / f'{block}-decision.json'
        decision.write_text(json.dumps(result, indent=1), encoding='utf-8')
        summary = {k: v for k, v in result.items() if k not in ('reports', 'boss')}
        print(json.dumps(summary, indent=1), flush=True)
        stage = result['directStage']
        if stage.startswith('STOP_FUTILITY') or stage == 'NOT_PASSED':
            print(f'STOPPED at {block}: {stage}', flush=True)
            return
        if stage == 'PASS_1000':
            print('LOCAL PASS_1000: run the 40-game cached-boss check, then combine with '
                  'practical_gate_v4.py --boss-plan', flush=True)
            return
        if block == 'block2':
            print('500-game look cleared: the cached-boss batch may now run concurrently with block 3.', flush=True)
    print('DONE', flush=True)


if __name__ == '__main__':
    main()
