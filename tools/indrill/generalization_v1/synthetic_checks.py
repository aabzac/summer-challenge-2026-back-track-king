"""Tiny synthetic-only persistence/boundary rehearsal; no real corpus is read.

Only this new runner's dependencies/configuration are replaced locally. Frozen
capacity/train_q modules and their globals remain unchanged. CLI takes a NEW
output directory, never the production run directory.
"""
from contextlib import contextmanager, redirect_stdout
import argparse
import copy
import io
from pathlib import Path
import struct
from types import SimpleNamespace
from unittest.mock import patch

import run_experiment as run
from trainer_synthetic_checks import SyntheticDataset, assert_models_equal

np = run.np


class SyntheticInterrupt(RuntimeError):
    pass


def rejected(callback, fragment):
    try:
        callback()
    except (ValueError, FileExistsError, FileNotFoundError) as error:
        assert fragment in str(error), (fragment, str(error))
        return str(error)
    raise AssertionError('Expected rejection: ' + fragment)


def binary(path, model):
    blob = run.v2.WEIGHT_MAGIC + struct.pack('>ii', run.q.INPUTS, run.q.HIDDEN)
    for name in run.v2.PARAM_SHAPES:
        blob += np.asarray(model.p[name], '>f4').tobytes()
    with path.open('xb') as stream:
        stream.write(blob)


class Fixture:
    def __init__(self, directory):
        self.directory = directory
        directory.mkdir()
        self.data = SyntheticDataset()
        self.config = dict(run.SETTINGS, updates=4, observations=64, metricsAt=[0, 2, 4])
        self.freeze_path = directory / 'synthetic-freeze.json'
        self.comparator_path = directory / 'synthetic-comparator.bin'
        binary(self.comparator_path, run.initialize())
        self.freeze = {'settings': self.config, 'pins': [], 'comparator': run.pin(self.comparator_path)}
        run.write_once(self.freeze_path, self.freeze)
        self.calls = []
        self.original = run.previous

    @contextmanager
    def active(self):
        def evaluate(model, data, ids):
            self.calls.append((model.updates, tuple(ids)))
            return self.original.evaluate(model, data, ids)
        proxy = SimpleNamespace(**vars(self.original))
        proxy.evaluate = evaluate
        with patch.object(run, 'OUTPUT', self.directory), patch.object(run, 'SETTINGS', self.config), \
             patch.object(run, 'verify_freeze', lambda _: (self.freeze, self.data)), patch.object(run, 'previous', proxy):
            yield

    def train(self, resume=False):
        with self.active(), redirect_stdout(io.StringIO()):
            run.run(self.freeze_path, resume)

    def binding(self):
        return run.make_binding(self.freeze_path, self.freeze)


def check_sampler():
    rng = np.random.default_rng(run.SETTINGS['samplingSeed'])
    order, position = np.empty(0, np.int64), 0
    exposure = np.zeros(2048, np.int64)
    for step in range(12000):
        chosen, order, position = run.sampler(rng, order, position)
        assert len(set(chosen.tolist())) == 32
        exposure[chosen] += 1
        if (step + 1) % 64 == 0:
            assert position == 2048
    assert exposure.sum() == 384000 and set(exposure.tolist()) == {187, 188}
    assert int((exposure == 187).sum()) == int((exposure == 188).sum()) == 1024
    return {'updates': 12000, 'sampleVisits': 384000, 'visits187': 1024, 'visits188': 1024}


def check_run(directory):
    full, resumed = Fixture(directory / 'full'), Fixture(directory / 'resumed')
    full.train()
    original_write = run.write_once
    def interrupt(path, payload):
        original_write(path, payload)
        if Path(path).name == 'step-00002.json':
            raise SyntheticInterrupt('Synthetic interruption after complete update2')
    with patch.object(run, 'write_once', interrupt):
        try:
            resumed.train()
        except SyntheticInterrupt:
            pass
        else:
            raise AssertionError('Interruption did not occur')
    resumed.train(resume=True)
    models, samplers = [], []
    for fixture in (full, resumed):
        model, rng = run.initialize(), np.random.default_rng(2)
        order, position = run.verify_chain(fixture.directory / 'run', model, rng, fixture.binding(), fixture.config)
        models.append(model); samplers.append((order, position, rng.bit_generator.state))
        assert fixture.calls == [(0, tuple(range(64))), (2, tuple(range(64))), (4, tuple(range(64))),
                                 (4, tuple(range(64, 68))), (0, tuple(range(64, 68)))]
        for step in fixture.config['metricsAt']:
            run.validate_metrics(fixture.directory / 'run', step, fixture.binding(), fixture.data.rows[:64])
        before = list(fixture.calls)
        with fixture.active():
            run.finish_holdout(fixture.directory / 'run', model, fixture.data, fixture.binding(), fixture.freeze['comparator'], fixture.config)
        assert fixture.calls == before  # Saved complete evaluations are read, never repeated.
        assert run.read(fixture.directory / 'run/result.json')['sampleVisits'] == 128
        with fixture.active():
            rejected(lambda: run.run(fixture.freeze_path, True), 'Only an incomplete')
    assert_models_equal(*models)
    np.testing.assert_array_equal(samplers[0][0], samplers[1][0])
    assert samplers[0][1:] == samplers[1][1:]
    expected = run.obj.fresh_model(run.SETTINGS['modelSeed'])
    for name in ('WV', 'BV'):
        assert all(not np.any(group[name]) for group in (models[0].p, models[0].m, models[0].v))
    assert any(not np.array_equal(expected.p[k], models[0].p[k]) for k in ('WI', 'BI'))
    assert any(not np.array_equal(expected.p[k], models[0].p[k]) for k in ('WB', 'BB'))
    assert any(not np.array_equal(expected.p[k], models[0].p[k]) for k in ('W0', 'WS', 'WN', 'WG'))
    return full, {'fullOptimizerRngOrderPositionBitExact': True, 'freshValueHeadFrozen': True,
                  'trunkAndBothActionHeadsUpdated': True, 'scheduledTrainingEvaluations': [0, 2, 4],
                  'terminalHoldoutCandidateAndComparatorCalls': 1, 'committedHoldoutNotRescored': True}


def check_guards(directory, fixture):
    results = {}
    folder = fixture.directory / 'run'
    def chain(path, binding=None):
        return run.verify_chain(path, run.initialize(), np.random.default_rng(0), binding or fixture.binding(), fixture.config)
    bad_binding = dict(fixture.binding(), freshInitialization=False)
    results['wrongBinding'] = rejected(lambda: chain(folder, bad_binding), 'commit/sampler')
    for name, mutate, fragment in (
        ('pendingUpdate', lambda p: run.write_once(run.update_intent_path(p, 5), {'synthetic': True}), 'Uncommitted/extra update'),
        ('extraCheckpoint', lambda p: (p / 'checkpoints/step-00005.npz').write_bytes(b'SYNTHETIC'), 'Incomplete checkpoint sequence'),
        ('failure', lambda p: run.write_once(p / 'failure.json', {'synthetic': True}), 'failed run'),
    ):
        # Preserve pinned paths by mutating/undoing only this synthetic fixture.
        if name == 'pendingUpdate':
            file = run.update_intent_path(folder, 5)
        elif name == 'extraCheckpoint':
            file = folder / 'checkpoints/step-00005.npz'
        else:
            file = folder / 'failure.json'
        mutate(folder)
        try:
            results[name] = rejected(lambda: chain(folder), fragment)
        finally:
            file.unlink()
    receipt_path = run.checkpoint_path(folder, 3).with_suffix('.json')
    original = receipt_path.read_bytes()
    receipt = run.read(receipt_path); receipt['previousReceipt']['sha256'] = '0' * 64
    receipt_path.write_text(__import__('json').dumps(receipt), encoding='utf-8')
    try:
        results['linkedReceipt'] = rejected(lambda: chain(folder), 'receipt chain')
    finally:
        receipt_path.write_bytes(original)
    model = run.initialize()
    results['earlyHoldout'] = rejected(lambda: run.finish_holdout(folder, model, fixture.data, fixture.binding(), fixture.freeze['comparator'], fixture.config), 'final fixed update')
    pending = directory / 'pending-evaluation'; pending.mkdir()
    paths = run.metric_paths(pending, 0)
    run.write_once(paths[1], {'binding': {}, 'target': {}})
    callbacks = []
    results['pendingEvaluation'] = rejected(lambda: run.evaluation_once(paths, {}, {}, lambda: callbacks.append(1)), 'Uncommitted evaluation')
    assert not callbacks
    complete = directory / 'complete-evaluation'; complete.mkdir()
    paths = run.metric_paths(complete, 0)
    run.evaluation_once(paths, {'a': 1}, {'b': 2}, lambda: {'synthetic': True})
    results['evaluationBinding'] = rejected(lambda: run.evaluation_once(paths, {'a': 9}, {'b': 2}, lambda: callbacks.append(1)), 'intent differs')
    assert not callbacks
    pinfile = directory / 'pinned-synthetic.txt'; pinfile.write_text('SYNTHETIC1')
    item = run.pin(pinfile); pinfile.write_text('SYNTHETIC2')
    results['changedPin'] = rejected(lambda: run.check_pin(item), 'Pinned bytes')
    metric = run.read(run.metric_paths(folder, 4)[0]); metric['rows'][0]['targetInk'] = 999
    results['metricLabel'] = rejected(lambda: run.validate_metric_payload(metric, 4, run.pin(run.checkpoint_path(folder, 4)), fixture.data.rows[:64]), 'identity/target')
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    directory = run.absolute(args.out)
    run.require(directory.is_relative_to(run.ROOT / 'build') and directory != run.OUTPUT / 'run', 'Synthetic output must be a separate build directory')
    directory.mkdir(parents=True, exist_ok=False)
    original_settings = copy.deepcopy(run.SETTINGS)
    sampler = check_sampler()
    fixture, persistence = check_run(directory)
    guards = check_guards(directory, fixture)
    assert run.SETTINGS == original_settings
    assert run.obj.loss_gradients is __import__('subset_objective').loss_gradients
    proof = {'schema': 'indrill-generalization-checks-v1', 'passed': True, 'syntheticOnly': True,
             'realRowsRead': 0, 'realCandidateFitRuns': 0, 'productionSettingsUnchanged': True,
             'environment': run.environment(), 'sourcePins': {run.pin(p)['path']: run.digest(p) for p in sorted(run.source_files())},
             'runtimePins': [run.pin(p) for p in sorted(run.runtime_files())],
             'originalJointObjectiveReusedWithoutModification': True, 'sampler': sampler,
             'persistence': persistence, 'rejections': guards}
    run.write_once(directory / 'result.json', proof)
    print(__import__('json').dumps({'passed': True, 'syntheticOnly': True, 'proof': run.pin(directory / 'result.json')}))


if __name__ == '__main__':
    main()
