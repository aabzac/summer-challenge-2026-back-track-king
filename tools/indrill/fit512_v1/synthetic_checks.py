"""Synthetic-only selection, fixed-budget and persistence checks; no monkeypatch.

The explicit execute seam receives tiny synthetic data/config/callbacks. Old
module globals, production settings and real data/model artifacts never change.
"""
from contextlib import redirect_stdout
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path

import run_experiment as run
from trainer_synthetic_checks import SyntheticDataset, assert_models_equal

np = run.np


class SyntheticInterrupt(BaseException):
    """Simulate process stop at an already committed boundary, not run failure."""


def rejected(callback, fragment):
    try:
        callback()
    except (ValueError, FileExistsError, FileNotFoundError) as error:
        assert fragment in str(error), (fragment, str(error))
        return str(error)
    raise AssertionError('Expected rejection: ' + fragment)


def selection_checks():
    maps = [hashlib.sha256(('synthetic-map-' + str(i)).encode()).hexdigest() for i in range(128)]
    ordered = sorted(maps, key=lambda value: hashlib.sha256(('indrill-capacity-v1/train/' + value).encode()).hexdigest())
    rows = [{'mapHash': 'synthetic-held-map', 'observationId': 'synthetic-held'}]
    rows += [{'mapHash': value, 'observationId': f'SYNTHETIC:{i}:{turn}'} for i, value in enumerate(maps) for turn in range(16)]
    b = list(range(1, 2049))
    a = [i for i in b if rows[i]['mapHash'] in set(ordered[:16]) and (i - 1) % 16 in (0, 5, 10, 15)]
    expected = [i for i in b if rows[i]['mapHash'] in set(ordered[:32])]
    expected_hash = hashlib.sha256(('\n'.join(rows[i]['observationId'] for i in expected) + '\n').encode()).hexdigest()
    result = run.select_panel(rows, b, a, expected_digest=expected_hash)
    assert result['fullDatasetIndices'] == expected and result['rankedMapHashes'] == ordered[:32]
    assert result['originalBPositions'] == [i - 1 for i in expected]
    assert len(a) == 64 and len(expected) == 512 and set(expected) != set(b[:512])
    assert result['observationIdsSha256'] != hashlib.sha256('\n'.join(result['observationIds']).encode()).hexdigest()
    rejected(lambda: run.select_panel(rows, b, a, expected_digest='0' * 64), 'digest differs')
    rejected(lambda: run.select_panel(rows, b[::-1], a, expected_digest=expected_hash), 'export order')
    rejected(lambda: run.select_panel(rows, b[:-1], a, expected_digest=expected_hash), 'cardinalities')
    outside = next(i for i in b if i not in expected)
    rejected(lambda: run.select_panel(rows, b, a + [outside], expected_digest=expected_hash), 'A inclusion')
    return {'observations': 512, 'maps': 32, 'rowsPerMap': 16, 'allAIncluded': True,
            'selectedExportOrderAndGlobalIndicesExact': True, 'first512RowsAreDifferent': True,
            'rankAndTrailingLfDigestExact': True, 'wrongDigestOrderCountsAndARejected': True}


def sampling_checks():
    rng = np.random.default_rng(run.SETTINGS['samplingSeed'])
    order, position, exposure = np.empty(0, np.int64), 0, np.zeros(512, np.int64)
    points = {}
    for step in range(1, 48001):
        chosen, order, position = run.sampler(rng, order, position, 512, 32)
        assert len(set(chosen.tolist())) == 32
        exposure[chosen] += 1
        if step in run.SETTINGS['metricsAt']:
            points[step] = dict(zip(*np.unique(exposure, return_counts=True)))
    assert np.array_equal(exposure, np.full(512, 3000)) and position == 512
    assert points[3000] == {187: 256, 188: 256}
    assert points[12000] == {750: 512} and points[24000] == {1500: 512} and points[48000] == {3000: 512}
    rejected(lambda: run.fit_decision(47999, 512, run.SETTINGS, 359), 'final fixed budget')
    assert run.fit_decision(48000, 358, run.SETTINGS, 359) == 'FIT70_NOT_ESTABLISHED'
    assert run.fit_decision(48000, 359, run.SETTINGS, 359) == 'FIT70_ESTABLISHED'
    return {'updates': 48000, 'sampleVisits': int(exposure.sum()), 'visitsPerRow': 3000,
            'interimExposureExact': True, 'threshold358Fails359Passes': True, 'earlyDecisionRejected': True}


class Fixture:
    def __init__(self, path):
        self.path = path; path.mkdir()
        self.data = SyntheticDataset()
        self.config = dict(run.SETTINGS, updates=4, observations=64, metricsAt=[0, 2, 4])
        self.selection = path / 'selection.json'
        run.write_once(self.selection, {'syntheticOnly': True, 'fullDatasetIndices': list(range(64))})
        self.freeze = {'settings': self.config, 'selection': run.pin(self.selection), 'fitThreshold': 45}
        self.freeze_path = path / 'synthetic-freeze.json'
        run.write_once(self.freeze_path, self.freeze)
        self.calls, self.boundaries = [], 0

    def evaluate(self, model, data, indices):
        self.calls.append((model.updates, tuple(indices)))
        return run.previous.evaluate(model, data, indices)

    def boundary(self):
        self.boundaries += 1

    def train(self, *, resume=False, commit=run.commit_checkpoint):
        with redirect_stdout(io.StringIO()):
            return run.execute(self.path / 'run', self.freeze_path, self.freeze, self.data, list(range(64)),
                               resume=resume, evaluate=self.evaluate, commit=commit, boundary_check=self.boundary)

    def binding(self):
        return run.make_binding(self.freeze_path, self.freeze)


def persistence_checks(directory):
    full, split = Fixture(directory / 'full'), Fixture(directory / 'resumed')
    full_result = full.train()
    def interrupt(*args, **kwargs):
        result = run.commit_checkpoint(*args, **kwargs)
        if args[1].updates == 2:
            raise SyntheticInterrupt()
        return result
    try:
        split.train(commit=interrupt)
    except SyntheticInterrupt:
        pass
    else:
        raise AssertionError('Synthetic stop was missed')
    split_result = split.train(resume=True)
    models, sampling = [], []
    for fixture, result in ((full, full_result), (split, split_result)):
        model, rng = run.obj.fresh_model(fixture.config['modelSeed']), np.random.default_rng(0)
        order, position = run.verify_chain(fixture.path / 'run', model, rng, fixture.binding(), fixture.config)
        models.append(model); sampling.append((order, position, rng.bit_generator.state))
        assert fixture.calls == [(0, tuple(range(64))), (2, tuple(range(64))), (4, tuple(range(64))), (4, tuple(range(64, 68)))]
        assert fixture.boundaries == 2 and result['decision'] == 'FIT70_NOT_ESTABLISHED'
        assert result['candidateOnlyFinalHoldout'] is True and result['holdoutAffectsFitDecision'] is False
        calls = list(fixture.calls)
        run.final_holdout(fixture.path / 'run', model, fixture.data, fixture.binding(), fixture.config, fixture.evaluate)
        assert fixture.calls == calls
        for step in fixture.config['metricsAt']:
            run.validate_metrics(fixture.path / 'run', step, fixture.binding(), fixture.data.rows[:64])
        rejected(lambda: fixture.train(resume=True), 'Only an incomplete')
    assert_models_equal(*models)
    np.testing.assert_array_equal(sampling[0][0], sampling[1][0]); assert sampling[0][1:] == sampling[1][1:]
    fresh = run.initialize()
    for names in (('W0', 'WS', 'WN', 'WG'), ('WB', 'BB'), ('WI', 'BI')):
        assert any(not np.array_equal(models[0].p[k], fresh.p[k]) for k in names)
    assert all(not np.any(group[k]) for group in (models[0].p, models[0].m, models[0].v) for k in ('WV', 'BV'))
    return full, {'fullOptimizerRngOrderPositionBitExact': True, 'freshSeedAndFrozenValueStateExact': True,
                  'trunkAndBothHeadsUpdated': True, 'trainingSteps': [0, 2, 4], 'onlyFinalCandidateHoldoutCalled': True,
                  'failedFitStillScoresHoldout': True, 'noComparatorOrBootstrapCalled': True,
                  'committedHoldoutNeverRescored': True}


def guard_checks(directory, fixture):
    folder, results = fixture.path / 'run', {}
    def verify(binding=None):
        return run.verify_chain(folder, run.initialize(), np.random.default_rng(0), binding or fixture.binding(), fixture.config)
    results['binding'] = rejected(lambda: verify(dict(fixture.binding(), fitThreshold=44)), 'commit/sampler')
    intent = run.update_intent_path(folder, 5)
    run.write_once(intent, {'syntheticPending': True})
    try:
        results['pendingUpdate'] = rejected(verify, 'Uncommitted/extra update')
    finally:
        intent.unlink()
    receipt_path = run.checkpoint_path(folder, 3).with_suffix('.json')
    original = receipt_path.read_bytes()
    receipt = run.read(receipt_path); receipt['previousReceipt']['sha256'] = '0' * 64
    receipt_path.write_text(json.dumps(receipt))
    try:
        results['linkedReceipt'] = rejected(verify, 'receipt chain')
    finally:
        receipt_path.write_bytes(original)
    failure = folder / 'failure.json'; run.write_once(failure, {'syntheticOnly': True})
    try:
        results['recordedFailure'] = rejected(verify, 'failed run')
    finally:
        failure.unlink()
    pending = directory / 'pending-evaluation'; pending.mkdir()
    paths = run.metric_paths(pending, 0); run.write_once(paths[1], {'syntheticOnly': True})
    calls = []
    results['pendingEvaluation'] = rejected(lambda: run.evaluation_once(paths, {}, {}, lambda: calls.append(1)), 'Uncommitted evaluation')
    assert not calls
    results['earlyHoldout'] = rejected(lambda: run.final_holdout(folder, run.initialize(), fixture.data, fixture.binding(), fixture.config, fixture.evaluate), 'final fixed update')
    bad = directory / 'pin.txt'; bad.write_text('synthetic-v1'); item = run.pin(bad); bad.write_text('synthetic-v2')
    results['sourcePin'] = rejected(lambda: run.check_pin(item), 'Pinned bytes')
    metric = run.read(run.metric_paths(folder, 4)[0]); metric['rows'][0]['targetInk'] = 999
    results['metricLabel'] = rejected(lambda: run.validate_metric_payload(metric, 4, run.pin(run.checkpoint_path(folder, 4)), fixture.data.rows[:64]), 'identity/target')
    # Exercise recorded persistence failure, with the intent retained and no retry.
    broken = Fixture(directory / 'write-failure')
    def fail_commit(*args, **kwargs):
        if args[1].updates == 1:
            raise OSError('SYNTHETIC checkpoint write failure')
        return run.commit_checkpoint(*args, **kwargs)
    try:
        broken.train(commit=fail_commit)
    except OSError:
        pass
    else:
        raise AssertionError('Synthetic persistence failure missed')
    assert run.read(broken.path / 'run/failure.json')['errorType'] == 'OSError'
    assert run.update_intent_path(broken.path / 'run', 1).exists()
    results['writeFailureNoRetry'] = rejected(lambda: broken.train(resume=True), 'failed run')
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', required=True)
    args = parser.parse_args(); output = run.absolute(args.out)
    run.require(output.is_relative_to(run.ROOT / 'build') and output != run.OUTPUT / 'run', 'Synthetic output must be separate build directory')
    output.mkdir(parents=True, exist_ok=False)
    source_before = {run.pin(p)['path']: run.digest(p) for p in run.source_files()}
    settings = copy.deepcopy(run.SETTINGS)
    old_settings, old_output = copy.deepcopy(run.base.SETTINGS), run.base.OUTPUT
    selection, sampling = selection_checks(), sampling_checks()
    fixture, persistence = persistence_checks(output)
    guards = guard_checks(output, fixture)
    assert settings == run.SETTINGS and old_settings == run.base.SETTINGS and old_output == run.base.OUTPUT
    assert source_before == {run.pin(p)['path']: run.digest(p) for p in run.source_files()}
    proof = {'schema': 'indrill-fit512-checks-v1', 'passed': True, 'syntheticOnly': True,
             'realRowsRead': 0, 'realCandidateFitRuns': 0, 'productionSettingsUnchanged': True,
             'oldModuleGlobalsUnchanged': True, 'environment': run.environment(), 'sourcePins': source_before,
             'runtimePins': [run.pin(p) for p in sorted(run.runtime_files())],
             'selection': selection, 'sampling': sampling, 'persistence': persistence, 'rejections': guards}
    run.write_once(output / 'result.json', proof)
    print(json.dumps({'passed': True, 'syntheticOnly': True, 'proof': run.pin(output / 'result.json')}))


if __name__ == '__main__':
    main()
