"""Fixed distributed-construction gate plus one independent QuickIncome ablation; no retries."""
import json
import subprocess
from run_quick_followup import ROOT, identities, known_maps, run_variant, save, sha
from practical_gate import compact, read_report

NAME = 'spread-construction-v1'
BASE = ('current?USE_ROLLOUT_QUICK_ROUTE=1,ROLLOUT_QUICK_MAX_PAINT=6,'
        'USE_ROLLOUT_QUICK_EARLY=0,USE_ROLLOUT_QUICK_VALUE=0,USE_ROLLOUT_QUICK_SAFE=0,'
        'USE_ROLLOUT_DIVERSE_REPLIES=0,USE_ROLLOUT_REPLAN=0,USE_ROLLOUT_SECOND_TARGET=0,')
CANDIDATE = BASE + 'USE_ROLLOUT_SPREAD=1'
CONTROL = BASE + 'USE_ROLLOUT_SPREAD=0'


if __name__ == '__main__':
    # Freeze the extra comparison and submission preservation before the gate starts.
    folder = ROOT / 'build/analysis' / NAME
    folder.mkdir(parents=True, exist_ok=True)
    original = {p.relative_to(ROOT).as_posix(): sha(p)
                for p in (ROOT / 'build/submit').rglob('*') if p.is_file()}
    ablation_pool = ROOT / f'bench/pools/{NAME}-ablation.json'
    ablation_pool_hash = sha(ablation_pool)
    save(folder / 'pre-strength.json', {'candidate': CANDIDATE, 'ablationControl': CONTROL,
                                      'ablationPoolSha256': ablation_pool_hash,
                                      'submissionFiles': original})
    run_variant(NAME, CANDIDATE, f'docs/{NAME}.md')
    bench_folder = ROOT / 'build/bench' / NAME
    launch = json.loads((bench_folder / 'launch.json').read_bytes())
    if identities() != launch['code'] or sha(ablation_pool) != ablation_pool_hash:
        raise RuntimeError('Frozen inputs changed before ablation')
    excluded = bench_folder / 'ablation-excluded.json'
    prior_maps = known_maps()
    save(excluded, {'gamesPerMap': dict.fromkeys(sorted(prior_maps), 2)})
    out = bench_folder / 'ablation.json'
    if out.exists():
        raise RuntimeError('Preserve existing ablation')
    command = [str(ROOT / 'gradlew.bat'), 'bench', '--console=plain', '-Pbot1=' + CANDIDATE,
               '-Pbot2=' + CONTROL, '-Pthreads=2', '-Ptiming=cg',
               '-Ppool=' + ablation_pool.relative_to(ROOT).as_posix(),
               '-Pout=' + out.relative_to(ROOT).as_posix(),
               '-PexcludeReports=' + excluded.relative_to(ROOT).as_posix()]
    print('START fixed diagnostic ablation against QuickIncome', flush=True)
    with (bench_folder / 'ablation.log').open('x', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
        for line in process.stdout:
            log.write(line); log.flush(); print(line, end='', flush=True)
        code = process.wait()
    if code or identities() != launch['code']:
        raise RuntimeError('Ablation failed or strategy identity changed; preserve all evidence')
    result = read_report(out, 300)
    if set(result['maps']) & prior_maps:
        raise RuntimeError('Ablation reuses prior maps')
    after = {p.relative_to(ROOT).as_posix(): sha(p)
             for p in (ROOT / 'build/submit').rglob('*') if p.is_file()}
    if original != after:
        raise RuntimeError('Selected submission files changed')
    changed = {k for k, v in result['data']['paramsA'].items() if v != result['data']['paramsB'][k]}
    if changed != {'USE_ROLLOUT_SPREAD'}:
        raise RuntimeError('Ablation must change exactly one parameter')
    save(ROOT / f'docs/evidence/{NAME}-ablation.json',
         {'purpose': 'Fixed diagnostic ablation, not a promotion gate or adaptive continuation',
          'launch': launch, 'report': compact(result), 'submissionFilesUnchanged': after})
    print('ABLATION FINAL', result['data']['winsA'], result['data']['winsB'],
          result['data']['draws'], result['score'], flush=True)
