"""Fixed practical-v3 local screens for the leader-calibrated track valuation (track-value-v1).

Two independent default-off variants on the selected v08 policy, control frozen v08:
  both:  current?USE_ROLLOUT_TRACK_VALUE=1                              (both simulated choosers)
  self:  current?USE_ROLLOUT_TRACK_VALUE=1,ROLLOUT_TRACK_VALUE_SELF_ONLY=1 (our chooser only)

  python tools/run_track_value_experiment.py screen both
  python tools/run_track_value_experiment.py screen self
  python tools/run_track_value_experiment.py direct both|self      # only after NEEDS_DIRECT_1000

Pools are fresh authentic seed ranges registered under bench/pools; every prior benchmark map
identity (live-target-v1 direct exclusion index plus every report played since) is passed to
-PexcludeReports so an overlapping map aborts the run before any game. Reports are write-once.
No HTTP, upload, promotion, retries or pooling. See docs/indrill-study-v1.md.
"""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from practical_gate import assess, read_report  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
NAME = 'track-value-v1'
FOLDER = ROOT / 'build/bench' / NAME
CONTROL = 'v08'
VARIANTS = {
    'both': {'spec': 'current?USE_ROLLOUT_TRACK_VALUE=1', 'screen': 530000, 'direct': 532000},
    'self': {'spec': 'current?USE_ROLLOUT_TRACK_VALUE=1,ROLLOUT_TRACK_VALUE_SELF_ONLY=1', 'screen': 531000, 'direct': 533000},
}
STAGES = {'screen': (150, 'development', 300), 'direct': (500, 'confirmation', 1000)}
# Complete exclusion index at the start of this experiment: everything before the live-target
# direct stage plus the reports played after that index was written.
PRIOR = ['build/bench/live-target-v1/direct-excluded.json', 'build/bench/live-target-v1/direct.json']


def pool_path(variant, stage):
    return ROOT / 'bench/pools' / f'{NAME}-{variant}-{stage}.json'


def ensure_pool(variant, stage):
    path = pool_path(variant, stage)
    pairs, purpose, _ = STAGES[stage]
    manifest = {'schemaVersion': 1, 'id': f'{NAME}-{variant}-{stage}', 'purpose': purpose,
                'generator': 'cg-sha1prng', 'seedStart': VARIANTS[variant][stage], 'pairs': pairs}
    text = json.dumps(manifest, indent=2) + '\n'
    if path.exists():
        if path.read_text(encoding='utf-8') != text:
            raise SystemExit(f'pool manifest differs from the registered one: {path}')
    else:
        path.write_text(text, encoding='utf-8')
    return path


def exclusion_index(label):
    """Merge gamesPerMap of every prior report, including this experiment's earlier stages."""
    merged, sources = {}, {}
    files = [ROOT / p for p in PRIOR]
    files += sorted(p for p in FOLDER.glob('*.json')
                    if not p.name.endswith(('-excluded.json', '-decision.json')))
    for path in files:
        if not path.exists():
            raise SystemExit(f'missing prior report: {path}')
        data = json.loads(path.read_bytes())
        for key, value in data['gamesPerMap'].items():
            merged[key] = merged.get(key, 0) + int(value)
        sources[path.relative_to(ROOT).as_posix()] = len(data['gamesPerMap'])
    out = FOLDER / f'{label}-excluded.json'
    if out.exists():
        raise SystemExit(f'exclusion index already exists: {out}')
    out.write_text(json.dumps({'gamesPerMap': merged, 'sourceFiles': sources}), encoding='utf-8')
    print(f'exclusion index: {len(merged)} prior maps from {len(sources)} files -> {out.relative_to(ROOT)}', flush=True)
    return out


def run(stage, variant):
    FOLDER.mkdir(parents=True, exist_ok=True)
    label = f'{variant}-{stage}'
    out = FOLDER / f'{label}.json'
    if out.exists():
        raise SystemExit(f'report already exists, never replaced: {out}')
    pool = ensure_pool(variant, stage)
    excluded = exclusion_index(label)
    args = [str(ROOT / 'gradlew.bat'), 'bench', '--console=plain',
            '-Pbot1=' + VARIANTS[variant]['spec'], '-Pbot2=' + CONTROL, '-Pthreads=2', '-Ptiming=cg',
            '-Ppool=' + pool.relative_to(ROOT).as_posix(), '-Pout=' + out.relative_to(ROOT).as_posix(),
            '-PexcludeReports=' + excluded.relative_to(ROOT).as_posix()]
    print('RUN', ' '.join(args[1:]), flush=True)
    with (FOLDER / f'{label}.log').open('x', encoding='utf-8') as log:
        proc = subprocess.Popen(args, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, encoding='utf-8', errors='replace')
        for line in proc.stdout:
            log.write(line)
            if '/300' in line or '/1000' in line or 'A ' in line[:12] or 'report' in line or 'FAIL' in line:
                print(line.rstrip(), flush=True)
        proc.wait()
    if proc.returncode != 0:
        raise SystemExit(f'bench failed with exit code {proc.returncode}; see {label}.log')
    games = STAGES[stage][2]
    screen = read_report(FOLDER / f'{variant}-screen.json', 300)
    direct = read_report(out, 1000) if stage == 'direct' else None
    result = assess(screen, direct)
    decision = FOLDER / f'{label}-decision.json'
    decision.write_text(json.dumps(result, indent=1), encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k not in ('reports',)}, indent=1), flush=True)
    print('DONE', label, flush=True)


if __name__ == '__main__':
    if len(sys.argv) != 3 or sys.argv[1] not in STAGES or sys.argv[2] not in VARIANTS:
        raise SystemExit(__doc__)
    run(sys.argv[1], sys.argv[2])
