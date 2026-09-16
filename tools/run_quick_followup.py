"""Run one predeclared quick-income variant through practical_gate, once, sequentially.

This is experiment orchestration, not a strategy optimizer. Never promotes or uploads.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from datetime import datetime, timezone
from practical_gate import assess, read_report

ROOT = Path(__file__).resolve().parents[1]
FLAGS = {'early': 'USE_ROLLOUT_QUICK_EARLY', 'value': 'USE_ROLLOUT_QUICK_VALUE',
         'safe': 'USE_ROLLOUT_QUICK_SAFE'}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8') as f:
        json.dump(data, f, indent=2)


def identities():
    files = [p for folder in ('src/main/java', 'src/main/resources', 'build/classes/java/main')
             for p in (ROOT / folder).rglob('*') if p.is_file()]
    return {p.relative_to(ROOT).as_posix(): sha(p) for p in sorted(files)}


def known_maps():
    result = set()
    for path in (ROOT / 'build/bench').rglob('*.json'):
        data = json.loads(path.read_bytes())
        if isinstance(data, dict):
            result.update(data.get('gamesPerMap', {}))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('variant', choices=FLAGS)
    args = parser.parse_args()
    name = 'quick-followup-' + args.variant + '-v1'
    folder = ROOT / 'build/bench' / name
    folder.mkdir(parents=True, exist_ok=False)  # Refuse reruns, including interrupted/failed ones.
    candidate = 'current?USE_ROLLOUT_QUICK_ROUTE=1,' + FLAGS[args.variant] + '=1'
    frozen = identities()
    old_maps = known_maps()
    manifest = {'name': name, 'createdAt': datetime.now(timezone.utc).isoformat(),
                'candidate': candidate, 'control': 'v06', 'commonOpponent': 'v05',
                'code': frozen, 'priorMapHashes': sorted(old_maps),
                'protocol': (ROOT / 'docs/quick-income-followups.md').read_text(encoding='utf-8'),
                'policy': 'practical-300-1000-v2', 'threads': 2, 'timing': 'CG',
                'poolHashes': {stage: sha(ROOT / f'bench/pools/{name}-{stage}.json')
                               for stage in ('screen', 'direct', 'style', 'full')}}
    save(folder / 'launch.json', manifest)
    reports = {}

    def bench(stage, label, bot_a, bot_b, exclusions):
        if identities() != frozen:
            raise RuntimeError('Strategy/runtime files changed between stages')
        pool = ROOT / f'bench/pools/{name}-{stage}.json'
        if sha(pool) != manifest['poolHashes'][stage]:
            raise RuntimeError('Predeclared pool changed')
        out = folder / (label + '.json')
        if out.exists():
            raise RuntimeError('Existing result must not be replaced')
        command = [str(ROOT / 'gradlew.bat'), 'bench', '--console=plain',
                   '-Pbot1=' + bot_a, '-Pbot2=' + bot_b, '-Pthreads=2', '-Ptiming=cg',
                   '-Ppool=' + pool.relative_to(ROOT).as_posix(),
                   '-Pout=' + out.relative_to(ROOT).as_posix(),
                   '-PexcludeReports=' + exclusions.relative_to(ROOT).as_posix()]
        print('START', label, flush=True)
        with (folder / (label + '.log')).open('x', encoding='utf-8') as log:
            process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
            for line in process.stdout:
                log.write(line); log.flush(); print(line, end='', flush=True)
            code = process.wait()
        if code or identities() != frozen:
            raise RuntimeError(f'{label}: run failed or strategy identity changed; preserve all evidence')
        report = read_report(out, 300 if stage in ('screen', 'style') else 1000)
        if set(report['maps']) & set(json.loads(exclusions.read_bytes())['gamesPerMap']):
            raise RuntimeError('Previously evaluated map reused')
        reports[label] = report
        return report

    def exclusions(label):
        path = folder / (label + '-excluded.json')
        save(path, {'gamesPerMap': dict.fromkeys(sorted(known_maps()), 2)})
        return path

    screen = bench('screen', 'screen', candidate, 'v06', exclusions('screen'))
    direct = ca = co = fa = fo = None
    decision = assess(screen)
    if decision['strengthStatus'] == 'NEEDS_DIRECT_1000':
        direct = bench('direct', 'direct', candidate, 'v06', exclusions('direct'))
        decision = assess(screen, direct)
    if decision['strengthStatus'] == 'NEEDS_MATCHED_300_EACH':
        excluded = exclusions('style')
        ca = bench('style', 'style-candidate', candidate, 'v05', excluded)
        co = bench('style', 'style-control', 'v06', 'v05', excluded)
        decision = assess(screen, direct, ca, co)
    if decision['strengthStatus'] == 'NEEDS_MATCHED_1000_EACH':
        excluded = exclusions('full')
        fa = bench('full', 'full-candidate', candidate, 'v05', excluded)
        fo = bench('full', 'full-control', 'v06', 'v05', excluded)
        decision = assess(screen, direct, ca, co, fa, fo)
    if decision['strengthStatus'] not in ('PASS', 'NOT_PASSED'):
        raise RuntimeError('Unfinished protocol')
    save(folder / 'decision.json', decision)
    save(ROOT / f'docs/evidence/{name}.json', {'launch': manifest, 'decision': decision})
    print('FINAL', json.dumps({k: v for k, v in decision.items()
                              if k not in ('reports', 'matchedScreenDeltas', 'matchedFullDeltas')}), flush=True)


if __name__ == '__main__':
    main()
