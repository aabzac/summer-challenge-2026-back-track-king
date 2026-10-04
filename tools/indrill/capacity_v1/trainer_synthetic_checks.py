"""Short SYNTHETIC control-flow/checkpoint rehearsal; no corpus or model fit.

Production SETTINGS remain unchanged on disk. Runtime patches use A=3 and B=4
updates and 64 tiny synthetic observations. A pass/fail counts are explicit
fixtures, not claimed learning results. Frozen setup/export validation is bypassed
only inside this harness and remains untested here. All output paths are new and
inside a separate diagnostic setup subdirectory; production runs reject its freeze.
"""
from contextlib import contextmanager, redirect_stdout
import argparse
import copy
import io
import json
from pathlib import Path
import struct
from unittest.mock import patch

import train_capacity as trainer
from subset_objective import np, train_q as q, train_q_v2 as v2
import subset_objective as obj
import representation_certificates as certificates


class SyntheticInterrupt(RuntimeError):
    pass


class SyntheticDataset:
    def __init__(self):
        self.rows, self.items = [], []
        rng = np.random.default_rng(73121)
        for i in range(68):
            x = rng.normal(0, .2, (2, 4, 64)).astype(np.float32)
            cells, ink = ((0, 2), 0) if i % 2 else ((), -1)
            state = q.State(x, np.asarray([1, 1, 1, 0], bool), np.asarray([1, 1], bool),
                            (cells, ()), (ink, -1), (0., 0.), False)
            episode = q.Episode('SYNTHETIC:' + str(i), 2, 2, 2, np.asarray([0, 0, 1, 1], np.int32),
                                np.asarray([1, 2, 1, 3], np.int32), [state])
            self.items.append(q.Transition(episode, 0, 0, 0, 0., 0.))
            self.rows.append({'observationId': 'SYNTHETIC:' + str(i), 'mapHash': f'SYNTHETIC-map-{i // 4}',
                'gameId': -1 - i, 'teacherSeat': i % 2, 'turn': i,
                'cohortTags': ['old40'] if i >= 64 else [], 'inputSha256': f'SYNTHETIC-input-{i}',
                'hadSkippedActions': False, 'targetCells': cells, 'targetInk': ink})
        self.panels = {'A': list(range(64)), 'B': list(range(64)), 'holdout': list(range(64, 68))}

    def panel_indices(self, name):
        return self.panels[name].copy()

    def batch(self, indices):
        return q.Batch([self.items[int(i)] for i in indices])


def assert_models_equal(first, second):
    for group in ('p', 'm', 'v'):
        for name, tensor in getattr(first, group).items():
            np.testing.assert_array_equal(tensor, getattr(second, group)[name], err_msg=group + ':' + name)
    assert first.updates == second.updates


def point_values(path):
    with np.load(path, allow_pickle=False) as saved:
        return {key: saved[key].copy() for key in saved.files}


def assert_points_equal(first, second):
    one, two = point_values(first), point_values(second)
    assert set(one) == set(two)
    for key in one.keys() - {'metadata'}:
        np.testing.assert_array_equal(one[key], two[key], err_msg=key)
    meta1, meta2 = json.loads(str(one['metadata'])), json.loads(str(two['metadata']))
    for key in ('updates', 'rngState', 'samplePosition', 'settings', 'learningRate', 'clipNorm'):
        assert meta1[key] == meta2[key], key


def expect_rejection(callback, fragment):
    try:
        callback()
    except (ValueError, FileNotFoundError, FileExistsError) as error:
        assert fragment in str(error), (fragment, str(error))
        return {'type': type(error).__name__, 'reason': str(error)}
    raise AssertionError('Expected rejection did not occur: ' + fragment)


def direct_roundtrip(directory):
    directory.mkdir()
    data = SyntheticDataset()
    binding = {'schema': 'SYNTHETIC-CHECKPOINT-ONLY', 'stage': 'tiny'}
    def advance(model, rng, order, position, count):
        sampled = []
        for _ in range(count):
            if position == len(order):
                order, position = rng.permutation(4), 0
            ids = order[position:position + 2].tolist()
            sampled.append(ids); position += 2
            _, _, gradient = obj.loss_gradients(model, data.batch(ids))
            model.adam(gradient, .0003, 10.)
        return order, position, sampled
    full, resumed = obj.fresh_model(2026100401), obj.fresh_model(2026100401)
    full_rng, resumed_rng = np.random.default_rng(2026100411), np.random.default_rng(2026100411)
    full_order, full_position, full_sequence = advance(full, full_rng, np.empty(0, np.int64), 0, 6)
    order, position, first_sequence = advance(resumed, resumed_rng, np.empty(0, np.int64), 0, 3)
    path = directory / 'synthetic-state.npz'
    trainer.checkpoint(path, resumed, resumed_rng, order, position, binding)
    recovered, recovered_rng = obj.fresh_model(9), np.random.default_rng(8)
    order, position = trainer.restore(path, recovered, recovered_rng, binding, 4)
    assert_models_equal(resumed, recovered)
    assert resumed_rng.bit_generator.state == recovered_rng.bit_generator.state
    order, position, second_sequence = advance(recovered, recovered_rng, order, position, 3)
    assert_models_equal(full, recovered)
    np.testing.assert_array_equal(full_order, order)
    assert full_position == position and full_rng.bit_generator.state == recovered_rng.bit_generator.state
    assert full_sequence == first_sequence + second_sequence
    stale = expect_rejection(lambda: trainer.restore(path, obj.fresh_model(9), np.random.default_rng(8),
                                                     {'schema': 'SYNTHETIC-WRONG'}, 4), 'binding differs')
    return {'updates': 6, 'interruptionAfter': 3, 'sampleSequence': full_sequence,
            'parametersAdamRngPositionBitExact': True, 'staleBindingRejected': stale}


def certificate_binding_checks(directory):
    data = SyntheticDataset()
    # This fixture checks the trainer's report binding, not the certificate math.
    data.manifest = {'representationCertificates': {'schema': certificates.SCHEMA, 'complete': True,
        'observations': len(data.rows), 'inputLabelBindingSha256': certificates.input_label_binding(data.rows),
        'capacityCeilingApplied': False}}
    trainer.certificate_precheck(data)
    data.manifest['representationCertificates']['inputLabelBindingSha256'] = 'SYNTHETIC-WRONG'
    wrong = expect_rejection(lambda: trainer.certificate_precheck(data), 'different input/labels')
    data.manifest['representationCertificates']['inputLabelBindingSha256'] = certificates.input_label_binding(data.rows)
    data.manifest['representationCertificates']['complete'] = False
    incomplete = expect_rejection(lambda: trainer.certificate_precheck(data), 'incomplete')
    proofs = {language: trainer.file_pin(trainer.ROOT / 'build/analysis/indrill-capacity-v1/setup' / name)
              for language, name in (('python', 'certificate-synthetic-first.json'), ('java', 'certificate-java-first.json'))}
    trainer.certificate_check_proofs(proofs)
    invalid = directory / 'synthetic-failed-certificate.json'
    trainer.write_once(invalid, {'syntheticOnly': True, 'passed': False})
    rejected = expect_rejection(lambda: trainer.certificate_check_proofs(dict(proofs, python=trainer.file_pin(invalid))), 'checks failed')
    return {'correctBindingAccepted': True, 'wrongBindingRejected': wrong, 'incompleteRejected': incomplete,
            'sourcePinnedCertificateProofsAccepted': proofs, 'failedCertificateProofRejected': rejected,
            'certificateMathNotTestedHere': True}


class Harness:
    def __init__(self, directory, pass_a=False):
        self.directory, self.pass_a = directory, pass_a
        directory.mkdir()
        self.data = SyntheticDataset()
        self.calls, self.adam_calls, self.captured_models = [], [], []
        self.active_stage = None
        self.settings = copy.deepcopy(trainer.SETTINGS)
        self.settings['A'].update(updates=3, metricsAt=[0, 1, 3])
        self.settings['B'].update(updates=4, observations=64, metricsAt=[0, 2, 4])
        self.freeze_path = directory / 'synthetic-freeze.json'
        self.comparator = directory / 'synthetic-comparator.bin'
        synthetic_model = v2.Model(np.random.default_rng(8801))
        blob = v2.WEIGHT_MAGIC + struct.pack('>ii', v2.INPUTS, v2.HIDDEN)
        for name in v2.PARAM_SHAPES:
            blob += synthetic_model.p[name].astype('>f4').tobytes()
        with self.comparator.open('xb') as stream:
            stream.write(blob)
        self.freeze = {'schema': 'SYNTHETIC-TEST-ONLY', 'status': 'NOT_A_PRODUCTION_FREEZE',
                       'comparator': trainer.file_pin(self.comparator)}
        trainer.write_once(self.freeze_path, self.freeze)

    @contextmanager
    def context(self):
        real_evaluate, real_adam = trainer.evaluate, v2.Model.adam
        def evaluate(model, data, indices):
            panel = 'holdout' if indices == data.panel_indices('holdout') else self.active_stage
            self.calls.append({'stage': self.active_stage, 'panel': panel, 'updates': model.updates, 'modelId': id(model)})
            self.captured_models.append(model)
            result = real_evaluate(model, data, indices)
            if self.active_stage == 'A':
                result['jointExactCount'] = 63 if self.pass_a else 62
                result['syntheticGateFixture'] = True
                result['notAFittingResult'] = True
            return result
        def adam(model, gradient, learning_rate, clip_norm=10.):
            self.adam_calls.append({'modelId': id(model), 'beforeUpdate': model.updates})
            return real_adam(model, gradient, learning_rate, clip_norm)
        def synthetic_freeze(path):
            assert Path(path) == self.freeze_path
            assert trainer.read(path) == self.freeze
            trainer.check_pin(self.freeze['comparator'])
            return self.freeze, self.directory
        with patch.object(trainer, 'OUTPUT', self.directory), patch.object(trainer, 'SETTINGS', self.settings), \
             patch.object(trainer, 'verify_freeze', synthetic_freeze), \
             patch.object(trainer.teacher, 'load_dataset', lambda _: self.data), \
             patch.object(trainer, 'dataset_precheck', lambda _: {'exactJointCeilingCount': 64, 'syntheticFixture': True}), \
             patch.object(trainer, 'evaluate', evaluate), patch.object(v2.Model, 'adam', adam):
            yield

    def run(self, stage, resume=False):
        self.active_stage = stage
        with redirect_stdout(io.StringIO()):
            trainer.run_stage(stage, self.freeze_path, resume)

    def interrupted_after_first_commit(self):
        real_write = trainer.write_once
        def interrupt(path, value):
            real_write(path, value)
            if Path(path).name == 'step-0001.json':
                raise SyntheticInterrupt('Synthetic host interruption after committed update1')
        with patch.object(trainer, 'write_once', interrupt):
            try:
                self.run('A')
            except SyntheticInterrupt:
                return
        raise AssertionError('Interruption was not reached')


def stage_checks(root):
    full = Harness(root / 'uninterrupted')
    with full.context():
        full.run('A')
        assert trainer.read(full.directory / 'stage-A/result.json')['decision'] == 'FIT_NOT_ESTABLISHED'
        calls_before = len(full.adam_calls)
        a_blocks_b = expect_rejection(lambda: full.run('B'), 'completed A pass')
        assert len(full.adam_calls) == calls_before and not (full.directory / 'stage-B/checkpoints').exists()
    resumed = Harness(root / 'resumed')
    with resumed.context():
        resumed.interrupted_after_first_commit()
        assert len(resumed.adam_calls) == 1
        resumed.run('A', resume=True)
        assert len(resumed.adam_calls) == 3  # Committed first update was not replayed.
    assert_points_equal(full.directory / 'stage-A/checkpoints/step-0003.npz', resumed.directory / 'stage-A/checkpoints/step-0003.npz')
    stale = Harness(root / 'stale-checkpoint')
    with stale.context():
        stale.interrupted_after_first_commit()
        path = stale.directory / 'stage-A/checkpoints/step-0001.npz'
        with path.open('ab') as stream:
            stream.write(b'SYNTHETIC-TAMPER')
        before = len(stale.adam_calls)
        stale_result = expect_rejection(lambda: stale.run('A', resume=True), 'Frozen input changed')
        assert len(stale.adam_calls) == before
    partial = Harness(root / 'partial-checkpoint')
    with partial.context():
        real_write = trainer.write_once
        def missing_commit(path, value):
            if Path(path).name == 'step-0001.json':
                raise SyntheticInterrupt('Synthetic interruption before checkpoint commit')
            real_write(path, value)
        with patch.object(trainer, 'write_once', missing_commit):
            try: partial.run('A')
            except SyntheticInterrupt: pass
        before = len(partial.adam_calls)
        partial_result = expect_rejection(lambda: partial.run('A', resume=True), 'step-0001.json')
        assert len(partial.adam_calls) == before
    pending = Harness(root / 'pending-update')
    with pending.context():
        def never_update(*_):
            raise SyntheticInterrupt('Synthetic interruption after update intent')
        with patch.object(obj, 'loss_gradients', never_update):
            try: pending.run('A')
            except SyntheticInterrupt: pass
        assert not pending.adam_calls
        pending_result = expect_rejection(lambda: pending.run('A', resume=True), 'Uncommitted update')
        assert not pending.adam_calls
    passing = Harness(root / 'A-pass-B-fresh', pass_a=True)
    with passing.context():
        passing.run('A')
        assert trainer.read(passing.directory / 'stage-A/result.json')['decision'] == 'A_PASS'
        assert all(row['panel'] != 'holdout' for row in passing.calls)
        passing.run('B')
        zero = point_values(passing.directory / 'stage-B/checkpoints/step-0000.npz')
        expected = obj.fresh_model(passing.settings['B']['modelSeed'])
        for name, tensor in expected.p.items():
            np.testing.assert_array_equal(zero[name], tensor)
            assert not np.any(zero['adam_m_' + name]) and not np.any(zero['adam_v_' + name])
        a_final = point_values(passing.directory / 'stage-A/checkpoints/step-0003.npz')
        assert any(not np.array_equal(zero[n], a_final[n]) for n in expected.p)
        holdout = [row for row in passing.calls if row['panel'] == 'holdout']
        assert len(holdout) == 2 and all(row['stage'] == 'B' for row in holdout)
        assert holdout[0]['updates'] == 4 and holdout[1]['updates'] == 0
        frozen_id = holdout[1]['modelId']
        assert all(call['modelId'] != frozen_id for call in passing.adam_calls)
        frozen_model = next(model for model in passing.captured_models if id(model) == frozen_id)
        with passing.comparator.open('rb') as stream:
            tensors = v2.read_weight_tensors(stream)
        for name in frozen_model.p:
            np.testing.assert_array_equal(frozen_model.p[name], tensors[name])
            assert not np.any(frozen_model.m[name]) and not np.any(frozen_model.v[name])
        assert frozen_model.updates == 0
        trainer.check_pin(passing.freeze['comparator'])
        duplicate = expect_rejection(lambda: passing.run('B', resume=True), 'incomplete original stage')
        assert len([row for row in passing.calls if row['panel'] == 'holdout']) == 2
    return {'checkpointChainContinuationBitExact': True, 'completedUpdatesNotReplayed': True,
        'staleCheckpointRejected': stale_result, 'partialCheckpointRejected': partial_result,
        'pendingUpdateRejected': pending_result, 'failedABlocksB': a_blocks_b,
        'passedAUsesFreshBSeedAndZeroOptimizer': True, 'holdoutCallsOnlyAfterFinalB': holdout,
        'frozenComparatorOptimizerCalls': 0, 'frozenComparatorWeightsUnchanged': True,
        'completedBRepeatRejected': duplicate,
        'syntheticGateSummariesInjected': {'A_FAIL': 62, 'A_PASS': 63, 'observations': 64},
        'stageTinyBoardAdamCalls': sum(len(x.adam_calls) for x in (full, resumed, stale, partial, pending, passing))}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out-dir', required=True)
    args = parser.parse_args()
    root = Path(args.out_dir).resolve()
    assert root.parent == trainer.ROOT / 'build/analysis/indrill-capacity-v1/setup' and not root.exists()
    production = copy.deepcopy(trainer.SETTINGS)
    assert production['A']['updates'] == 2000 and production['B']['updates'] == 4000
    root.mkdir()
    report = {'syntheticOnly': True, 'realCorpusRowsRead': 0, 'realCandidateFitRuns': 0,
              'productionSettings': production, 'pins': [trainer.file_pin(p) for p in
              (Path(__file__), Path(trainer.__file__), Path(obj.__file__), Path(q.__file__), Path(v2.__file__), Path(certificates.__file__))],
              'limitations': ['This bypasses production freeze/export/certificate preflight using visibly synthetic fixtures.',
                              'Injected A pass/fail counts exercise control flow; they are not empirical fit results.',
                              'Three/four-update schedules exercise persistence and boundaries, not full 2000/4000 performance.',
                              'No conclusion that real training is ready follows from this rehearsal.']}
    try:
        report['directRoundTrip'] = direct_roundtrip(root / 'roundtrip')
        report['stageChecks'] = stage_checks(root)
        report['certificateBindingChecks'] = certificate_binding_checks(root)
        assert trainer.SETTINGS == production
        report['status'] = 'PASS'
    except Exception as error:
        report['status'] = 'FAIL'
        report['failure'] = {'type': type(error).__name__, 'message': str(error)}
        trainer.write_once(root / 'result.json', report)
        raise
    trainer.write_once(root / 'result.json', report)
    print(json.dumps({'status': report['status'], 'result': trainer.file_pin(root / 'result.json'),
                      'syntheticOnly': True, 'productionUpdatesUnchanged': [2000, 4000]}))


if __name__ == '__main__':
    main()
