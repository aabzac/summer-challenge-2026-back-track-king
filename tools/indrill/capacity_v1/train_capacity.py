"""Fixed supervised capacity diagnostic; requires separately reviewed setup freezes.

CLI: --stage A|B --freeze <frozen-run.json> [--resume]. No default launch.
Freeze schema (must be reviewed and created before any training):
 {schema:'indrill-capacity-training-freeze-v1',status:'FROZEN',
  outputDirectory:'build/analysis/indrill-capacity-v1',datasetDirectory:'...',
  pins:[{path,sha256},...],syntheticChecks:{path,sha256},trainerChecks:{path,sha256},
  certificateChecks:{python:{path,sha256},java:{path,sha256}},
  exportFreeze:{path,sha256},comparator:{path:<frozen weights-6.bin>,sha256},checksPassed:true}
Pins must include protocol, every installed .py, both unchanged trainer sources,
teacher.jsonl, teacher-preflight.json, dataset-manifest.json, synthetic result and
the frozen comparator .bin/.npz pair. No generated Java or original weight write.

Every completed update is stored in a distinct checkpoint including Adam/RNG and
sample position. Resume validates the entire contiguous committed chain. A partial
checkpoint or update intent without its completed checkpoint blocks automatic
resume; interrupted unrecorded computation is never silently replayed.
"""
import argparse
from collections import defaultdict
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import platform
import sys

sys.dont_write_bytecode = True
import subset_objective as obj
from subset_objective import np, train_q as q, train_q_v2 as v2, require
import teacher_dataset as teacher

ROOT = Path(__file__).resolve().parents[3]
PREP = Path(__file__).resolve().parent
OUTPUT = ROOT / 'build/analysis/indrill-capacity-v1'
SETTINGS = {
    'A': {'modelSeed': 2026100401, 'samplingSeed': 2026100411, 'updates': 2000, 'batch': 16,
          'observations': 64, 'metricsAt': [0, 500, 1000, 1500, 2000]},
    'B': {'modelSeed': 2026100402, 'samplingSeed': 2026100412, 'updates': 4000, 'batch': 32,
          'observations': 2048, 'metricsAt': [0, 1000, 2000, 3000, 4000]},
}
METRICS = ('buildExact', 'inkExact', 'jointExact', 'cellPrecision', 'cellRecall',
           'predictedPaint', 'teacherPaint', 'buildNll', 'inkNll', 'jointNll')


def utc():
    return datetime.now(timezone.utc).isoformat()


def absolute(path):
    path = Path(path)
    value = path.resolve() if path.is_absolute() else (ROOT / path).resolve()
    require(value.is_relative_to(ROOT), 'Input/output outside workspace')
    return value


def file_pin(path):
    path = absolute(path)
    return {'path': path.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def check_pin(value):
    require(file_pin(value['path']) == value, 'Frozen input changed: ' + str(value['path']))


def read(path):
    return json.loads(absolute(path).read_text(encoding='utf-8'))


def write_once(path, value):
    with absolute(path).open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2, allow_nan=False)
        stream.write('\n')


def certificate_check_proofs(proofs):
    require(set(proofs) == {'python', 'java'}, 'Both Python and frozen-Java certificate checks are required')
    for language, item in proofs.items():
        check_pin(item)
        proof = read(item['path'])
        require(proof['syntheticOnly'] is True and proof['passed'] is True, 'Certificate implementation checks failed')
        if language == 'python':
            require(proof['schema'] == 'indrill-capacity-certificate-checks-v1'
                    and proof['numpyUniversalEqualityClaimed'] is False, 'Wrong certificate-check scope')
        pins = proof['sourcePins' if language == 'python' else 'sourceAndClassPins']
        require(pins, 'Certificate checks must pin the tested implementation')
        for path, digest in pins.items():
            check_pin({'path': path, 'sha256': digest})


def verify_freeze(path):
    freeze = read(path)
    require(freeze['schema'] == 'indrill-capacity-training-freeze-v1' and freeze['status'] == 'FROZEN'
            and freeze['checksPassed'] is True, 'Reviewed frozen setup required')
    require(absolute(freeze['outputDirectory']) == OUTPUT, 'Diagnostic output path differs')
    check_pin(freeze['exportFreeze'])
    from export_launcher import verify_execution
    execution = verify_execution(absolute(freeze['exportFreeze']['path']))
    dataset = absolute(freeze['datasetDirectory'])
    require(dataset.is_relative_to(OUTPUT), 'Dataset must be separately retained under diagnostic output')
    required = set(PREP.glob('*.py')) | {ROOT / 'docs/indrill-capacity-v1.md', ROOT / 'tools/indrill/train_q.py', ROOT / 'tools/indrill/train_q_v2.py'}
    required |= {dataset / n for n in ('teacher.jsonl', 'teacher-preflight.json', 'dataset-manifest.json')}
    required |= {absolute(freeze['syntheticChecks']['path']), absolute(freeze['trainerChecks']['path']),
                 absolute(freeze['comparator']['path']), absolute(freeze['comparator']['path']).with_suffix('.npz')}
    required |= {absolute(item['path']) for item in freeze['certificateChecks'].values()}
    required.add(absolute(freeze['exportFreeze']['path']))
    required |= {Path(item['path']).resolve() for item in execution['pins']
                 if Path(item['path']).resolve().is_relative_to(ROOT)}
    observed = [absolute(item['path']) for item in freeze['pins']]
    require(len(observed) == len(set(observed)) and required.issubset(set(observed)), 'Missing/repeated implementation, input or comparator pins')
    for item in freeze['pins']:
        check_pin(item)
    check_pin(freeze['syntheticChecks']); check_pin(freeze['comparator'])
    checks = read(freeze['syntheticChecks']['path'])
    require(checks['checks']['status'] == 'PASS' and checks['checks']['syntheticOnly'] is True, 'Synthetic implementation checks must pass')
    for item in checks['pins']:
        require(hashlib.sha256(absolute(item['path']).read_bytes()).hexdigest() == item['sha256'], 'Synthetic proof covers different implementation')
    check_pin(freeze['trainerChecks'])
    rehearsal = read(freeze['trainerChecks']['path'])
    require(rehearsal['status'] == 'PASS' and rehearsal['syntheticOnly'] is True
            and rehearsal['realCorpusRowsRead'] == rehearsal['realCandidateFitRuns'] == 0,
            'A passing synthetic persistence/boundary rehearsal is required')
    for item in rehearsal['pins']:
        check_pin(item)
    certificate_check_proofs(freeze['certificateChecks'])
    require(absolute(freeze['comparator']['path']) == ROOT / 'build/analysis/indrill-q-v2/weights-6.bin', 'Unexpected frozen comparator')
    return freeze, dataset


def certificate_precheck(data):
    from representation_certificates import SCHEMA, input_label_binding
    report = data.manifest['representationCertificates']
    require(report['schema'] == SCHEMA and report['complete'] is True and report['observations'] == len(data.rows)
            and report['inputLabelBindingSha256'] == input_label_binding(data.rows),
            'Representation certificate report is incomplete or targets different input/labels')
    # A descriptive runtime tie certificate does not add a new capacity ceiling.
    require(report['capacityCeilingApplied'] is False, 'Unexpected capacity gate in descriptive certificate report')
    return report


def dataset_precheck(data):
    certificate_precheck(data)
    for stage in ('A', 'B'):
        require(len(data.panel_indices(stage)) == SETTINGS[stage]['observations'], 'Wrong fixed panel cardinality ' + stage)
    require(len(data.panel_indices('holdout')) <= 1020, 'Unbounded holdout panel')
    a_maps = {data.rows[i]['mapHash'] for i in data.panel_indices('A')}
    b_maps = {data.rows[i]['mapHash'] for i in data.panel_indices('B')}
    require(len(a_maps) == 16 and len(b_maps) == 128 and a_maps.issubset(b_maps), 'Fixed map panels differ')
    return teacher.collision_summary(data.rows, data.panel_indices('A'))


def evaluate(model, data, indices):
    """Descriptive immutable rows; no optimizer call or sampled model selection."""
    rows = []
    for offset in range(0, len(indices), 32):
        ids = indices[offset:offset + 32]
        batch = data.batch(ids)
        build, ink, _ = model.forward(batch)
        require(np.isfinite(build).all() and np.isfinite(ink).all(), 'NUMERICAL_FAILURE')
        _, losses, _, _ = obj.output_objective(build, ink, batch)
        predicted_build, predicted_ink = q.choose_actions(build, ink, batch)
        for j, index in enumerate(ids):
            source = data.rows[index]
            target, predicted = set(batch.builds[j]), set(predicted_build[j])
            overlap = len(target & predicted)
            both_empty = not target and not predicted
            b_ok, i_ok = predicted == target, predicted_ink[j] == batch.inks[j]
            turn = source['turn']
            rows.append({'observationId': source['observationId'], 'mapHash': source['mapHash'],
                'gameId': source['gameId'], 'teacherSeat': source['teacherSeat'], 'turn': turn,
                'cohortTags': source.get('cohortTags', []), 'inputSha256': source['inputSha256'],
                'hadSkippedActions': source['hadSkippedActions'], 'teacherCardinality': len(target),
                'costPattern': '+'.join(str(batch.costs[j, c]) for c in sorted(target, key=lambda c: (batch.costs[j, c], c))) or 'EMPTY',
                'turnBand': '0..19' if turn < 20 else '20..49' if turn < 50 else '50+',
                'targetCells': sorted(target), 'predictedCells': sorted(predicted),
                'targetInk': int(batch.inks[j]), 'predictedInk': int(predicted_ink[j]),
                'buildExact': int(b_ok), 'inkExact': int(i_ok), 'jointExact': int(b_ok and i_ok),
                'cellPrecision': 1. if both_empty else overlap / len(predicted) if predicted else 0.,
                'cellRecall': 1. if both_empty else overlap / len(target) if target else 0.,
                'predictedPaint': sum(int(batch.costs[j, c]) for c in predicted),
                'teacherPaint': sum(int(batch.costs[j, c]) for c in target),
                'buildNll': float(losses[j, 0]), 'inkNll': float(losses[j, 1]), 'jointNll': float(losses[j].sum())})
    return {'observations': len(rows), 'jointExactCount': sum(r['jointExact'] for r in rows),
            'summary': summarize(rows), 'rows': rows,
            'precisionRecallConvention': 'Both empty: precision=recall=1. Exactly one empty: both=0.',
            'collisionSummary': teacher.collision_summary(data.rows, indices)}


def aggregate(rows):
    maps = defaultdict(list)
    for row in rows:
        maps[row['mapHash']].append(row)
    means = {key: {metric: float(np.mean([row[metric] for row in group])) for metric in METRICS}
             for key, group in sorted(maps.items())}
    return {'observations': len(rows), 'maps': len(means), 'mapMeans': means,
            'meanOfMapMeans': {metric: float(np.mean([m[metric] for m in means.values()])) if means else None for metric in METRICS}}


def summarize(rows):
    report = {'all': aggregate(rows)}
    for field in ('teacherCardinality', 'costPattern', 'turnBand', 'teacherSeat', 'hadSkippedActions'):
        values = sorted({str(r[field]) for r in rows})
        report[field] = {value: aggregate([r for r in rows if str(r[field]) == value]) for value in values}
    report['cohorts'] = {cohort: aggregate([r for r in rows if cohort in r['cohortTags']])
                         for cohort in ('old40', 'later11', 'holdoutCompanion')}
    return report


def transfer(candidate, comparator):
    require([r['observationId'] for r in candidate['rows']] == [r['observationId'] for r in comparator['rows']], 'Unpaired holdout observations')
    first, second = candidate['summary']['all']['mapMeans'], comparator['summary']['all']['mapMeans']
    require(set(first) == set(second), 'Unpaired holdout maps')
    keys = sorted(first)
    rng = np.random.default_rng(2026100421)
    differences = np.asarray([[first[k][m] - second[k][m] for m in ('buildExact', 'inkExact')] for k in keys], np.float64)
    intervals = {}
    if keys:
        boot = np.empty((10000, 2), np.float64)
        # Common paired map resamples for both metrics; bounded memory.
        for start in range(0, 10000, 100):
            sampled = rng.integers(0, len(keys), (100, len(keys)))
            boot[start:start + 100] = differences[sampled].mean(axis=1)
        for i, metric in enumerate(('buildExact', 'inkExact')):
            intervals[metric] = {'difference': float(differences[:, i].mean()), 'percentile95': np.percentile(boot[:, i], [2.5, 97.5]).tolist()}
    passed = len(keys) >= 20 and all(intervals[m]['percentile95'][0] > 0 for m in ('buildExact', 'inkExact'))
    return {'decision': 'TRANSFER_EVIDENCE' if passed else 'TRANSFER_NOT_ESTABLISHED', 'maps': len(keys),
            'pairedMapBootstrap': 10000, 'seed': 2026100421, 'intervals': intervals,
            'interpretation': 'Development-holdout diagnostic; no arena strength or pristine-confirmation claim.'}


def checkpoint(path, model, rng, order, position, metadata):
    value = dict(metadata, updates=int(model.updates), rngState=rng.bit_generator.state, samplePosition=int(position))
    payload = {'metadata': np.asarray(json.dumps(value)), 'sampleOrder': np.asarray(order, np.int64)}
    for name in model.p:
        payload[name] = model.p[name]
        payload['adam_m_' + name] = model.m[name]
        payload['adam_v_' + name] = model.v[name]
    with path.open('xb') as stream:
        np.savez_compressed(stream, **payload)


def restore(path, model, rng, binding, count):
    with np.load(path, allow_pickle=False) as saved:
        metadata = json.loads(str(saved['metadata']))
        require(all(metadata[k] == v for k, v in binding.items()), 'Checkpoint binding differs')
        for name, target in model.p.items():
            for prefix, group in (('', model.p), ('adam_m_', model.m), ('adam_v_', model.v)):
                require(saved[prefix + name].shape == target.shape and saved[prefix + name].dtype == np.float32, 'Checkpoint tensor shape/type differs')
                group[name][:] = saved[prefix + name]
        model.updates = metadata['updates']
        rng.bit_generator.state = metadata['rngState']
        order = saved['sampleOrder'].astype(np.int64)
        position = metadata['samplePosition']
        require((model.updates == 0 and len(order) == position == 0) or
                (sorted(order.tolist()) == list(range(count)) and 0 <= position <= count), 'Checkpoint sample position differs')
    obj.assert_finite_frozen_value(model)
    return order, position


def run_stage(stage, freeze_path, resume=False):
    freeze, dataset_path = verify_freeze(freeze_path)
    freeze_pin, config = file_pin(freeze_path), SETTINGS[stage]
    folder = OUTPUT / ('stage-' + stage)
    if resume:
        require(folder.is_dir() and not (folder / 'result.json').exists(), 'Only an incomplete original stage may resume')
    else:
        folder.mkdir()  # Refuse any pre-existing stage, including failures.
    binding = {'schema': 'indrill-capacity-checkpoint-v1', 'stage': stage, 'freeze': freeze_pin,
               'settings': config, 'learningRate': .0003, 'clipNorm': 10.}
    environment = {'python': sys.version, 'numpy': np.__version__, 'platform': platform.platform()}
    if resume:
        intent = read(folder / 'intent.json')
        require(intent['binding'] == binding and intent['environment'] == environment, 'Resume configuration/runtime differs')
    else:
        write_once(folder / 'intent.json', {'createdAtUtc': utc(), 'binding': binding, 'environment': environment})
    if stage == 'B':
        passed = read(OUTPUT / 'stage-A/result.json')
        require(passed['decision'] == 'A_PASS' and passed['freeze'] == freeze_pin
                and passed['updates'] == SETTINGS['A']['updates'] and passed['finalJointExact'] >= 63, 'B requires the fixed completed A pass')
        check_pin(passed['checkpoint']); check_pin(passed['finalMetrics'])
        actual_a = read(passed['finalMetrics']['path'])
        require(actual_a['jointExactCount'] == passed['finalJointExact'] and actual_a['observations'] == SETTINGS['A']['observations']
                and actual_a['checkpoint'] == passed['checkpoint'], 'A result disagrees with retained final metrics')
    data = teacher.load_dataset(dataset_path)
    ceiling = dataset_precheck(data)
    if ceiling['exactJointCeilingCount'] < 63:
        require(stage == 'A' and not resume, 'Unexpected aliasing limit after stage began')
        write_once(folder / 'result.json', {'decision': 'INPUT_ALIASING_LIMIT', 'updates': 0, 'freeze': freeze_pin, 'ceiling': ceiling})
        return
    indices = data.panel_indices(stage)
    model, rng = obj.fresh_model(config['modelSeed']), np.random.default_rng(config['samplingSeed'])
    order, position = np.empty(0, np.int64), 0
    checkpoint_dir = folder / 'checkpoints'
    if resume:
        points = sorted(checkpoint_dir.glob('step-*.npz'))
        steps = [int(p.stem.split('-')[1]) for p in points]
        require(steps and steps == list(range(steps[-1] + 1)) and steps[-1] <= config['updates'], 'Incomplete checkpoint sequence')
        require(not (folder / 'numerical-failure.json').exists(), 'A numerical failure cannot resume inside this experiment')
        for step, path in enumerate(points):
            commit = read(checkpoint_dir / f'step-{step:04d}.json')
            check_pin(commit['checkpoint'])
            require(commit['checkpoint'] == file_pin(path) and commit['binding'] == binding, 'Checkpoint commit differs')
        # An update intent without a committed checkpoint is ambiguous, never rerun.
        require(not (checkpoint_dir / f'update-{steps[-1] + 1:04d}-intent.json').exists(), 'Uncommitted update requires offline recovery; no automatic replay')
        order, position = restore(points[-1], model, rng, binding, len(indices))
        require(model.updates == steps[-1], 'Checkpoint update count differs')
    else:
        checkpoint_dir.mkdir()
        path = checkpoint_dir / 'step-0000.npz'
        checkpoint(path, model, rng, order, position, binding)
        write_once(path.with_suffix('.json'), {'binding': binding, 'checkpoint': file_pin(path)})
    try:
        while True:
            step = model.updates
            if step in config['metricsAt']:
                metric_path = folder / f'train-metrics-{step:04d}.json'
                metric_commit = folder / f'train-metrics-{step:04d}-commit.json'
                if not metric_path.exists():
                    metrics = evaluate(model, data, indices)
                    write_once(metric_path, {'stage': stage, 'updates': step, 'checkpoint': file_pin(checkpoint_dir / f'step-{step:04d}.npz'), **metrics})
                    write_once(metric_commit, {'binding': binding, 'metrics': file_pin(metric_path)})
                committed = read(metric_commit)
                require(committed['binding'] == binding and committed['metrics'] == file_pin(metric_path), 'Descriptive metrics changed or interrupted before commit')
                require(read(metric_path)['checkpoint'] == file_pin(checkpoint_dir / f'step-{step:04d}.npz'), 'Metrics target a different checkpoint')
            if step == config['updates']:
                break
            next_step = step + 1
            write_once(checkpoint_dir / f'update-{next_step:04d}-intent.json', {'binding': binding, 'fromCheckpoint': file_pin(checkpoint_dir / f'step-{step:04d}.npz')})
            if position == len(order):
                order, position = rng.permutation(len(indices)), 0
            chosen = [indices[int(i)] for i in order[position:position + config['batch']]]
            require(len(chosen) == config['batch'], 'Epoch/batch divisibility violated')
            position += config['batch']
            loss, _, gradients = obj.loss_gradients(model, data.batch(chosen))
            require(np.isfinite(loss), 'NUMERICAL_FAILURE')
            norm = model.adam(gradients, .0003, 10.)
            require(np.isfinite(norm), 'NUMERICAL_FAILURE')
            obj.assert_finite_frozen_value(model)
            path = checkpoint_dir / f'step-{next_step:04d}.npz'
            checkpoint(path, model, rng, order, position, binding)
            write_once(path.with_suffix('.json'), {'binding': binding, 'checkpoint': file_pin(path), 'loss': loss, 'unclippedGradientNorm': norm})
    except (ValueError, FloatingPointError) as error:
        # Preserve state even when NaNs exist; no automatic optimizer/seed repair.
        failure_path = folder / 'numerical-failure.npz'
        checkpoint(failure_path, model, rng, order, position, binding)
        numerical = isinstance(error, FloatingPointError) or any(word in str(error).lower() for word in ('nonfinite', 'numerical_failure'))
        write_once(folder / 'numerical-failure.json', {'decision': 'NUMERICAL_FAILURE' if numerical else 'CONTRACT_FAILURE', 'error': str(error),
                    'updates': model.updates, 'checkpoint': file_pin(failure_path), 'freeze': freeze_pin})
        raise
    verify_freeze(freeze_path)
    final_metrics = folder / f'train-metrics-{config["updates"]:04d}.json'
    final = read(final_metrics)
    result = {'stage': stage, 'freeze': freeze_pin, 'updates': model.updates,
              'checkpoint': file_pin(checkpoint_dir / f'step-{model.updates:04d}.npz'),
              'finalMetrics': file_pin(final_metrics), 'finalJointExact': final['jointExactCount'],
              'promotion': False, 'newGames': 0}
    if stage == 'A':
        result['decision'] = 'A_PASS' if final['jointExactCount'] >= 63 else 'FIT_NOT_ESTABLISHED'
    else:
        # Presence of this write-once intent prevents re-evaluating holdout after an interruption.
        write_once(folder / 'holdout-evaluation-intent.json', {'freeze': freeze_pin, 'checkpoint': result['checkpoint'], 'comparator': freeze['comparator']})
        holdout = data.panel_indices('holdout')
        candidate = evaluate(model, data, holdout)
        frozen_model = v2.Model(np.random.default_rng(0))
        with absolute(freeze['comparator']['path']).open('rb') as stream:
            tensors = v2.read_weight_tensors(stream)
        for name in frozen_model.p:
            frozen_model.p[name][:] = tensors[name]  # Inference only; no optimizer restore or update.
        comparator = evaluate(frozen_model, data, holdout)
        report = {'candidate': candidate, 'frozenQv2': comparator, 'comparison': transfer(candidate, comparator)}
        write_once(folder / 'holdout-final.json', report)
        result['decision'] = report['comparison']['decision']
        result['holdoutFinal'] = file_pin(folder / 'holdout-final.json')
    verify_freeze(freeze_path)
    write_once(folder / 'result.json', result)
    print(json.dumps(result))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', choices=('A', 'B'), required=True)
    parser.add_argument('--freeze', required=True)
    parser.add_argument('--resume', action='store_true')
    args = parser.parse_args()
    run_stage(args.stage, args.freeze, args.resume)


if __name__ == '__main__':
    main()
