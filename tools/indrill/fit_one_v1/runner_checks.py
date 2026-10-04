"""Two-update SYNTHETIC persistence rehearsal; no real row, corpus or production run.

Production constants and run_arm are never patched or called. Checkpoint APIs are
exercised on one artificial 2x2 position; all corruption fixtures are newly copied
files inside the requested create-new setup directory. No file is deleted.
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import sys
from unittest.mock import patch

sys.dont_write_bytecode = True
import run_fit as run

np, q, obj = run.np, run.q, run.obj


def tiny_batch():
    rng = np.random.default_rng(614021)
    features = rng.normal(0, .2, (2, 4, 64)).astype(np.float32)
    state = q.State(features, np.asarray([1, 1, 1, 0], bool), np.asarray([1, 1], bool),
                    ((0, 2), ()), (0, -1), (0., 0.), False)
    episode = q.Episode('FIT-ONE-SYNTHETIC-ONLY', 2, 2, 2, np.asarray([0, 0, 1, 1], np.int32),
                        np.asarray([1, 2, 1, 3], np.int32), [state])
    return q.Batch([q.Transition(episode, 0, 0, 0, 0., 0.)])


def arrays(arm, state):
    if arm == 'free':
        return {'logits': state.logits, 'm': state.m, 'v': state.v, 'legal': state.legal}
    return {group + ':' + name: value for group in ('p', 'm', 'v') for name, value in getattr(state, group).items()}


def same_state(arm, first, second):
    run.require(first.updates == second.updates, 'Synthetic update counters differ')
    one, two = arrays(arm, first), arrays(arm, second)
    run.require(set(one) == set(two), 'Synthetic tensor sets differ')
    for key in one: np.testing.assert_array_equal(one[key], two[key], err_msg=key)


def reject(callback, fragment):
    try:
        callback()
    except (ValueError, FileNotFoundError, FileExistsError) as error:
        run.require(fragment in str(error), 'Unexpected synthetic rejection: ' + str(error))
        return {'errorType': type(error).__name__, 'reason': str(error)}
    raise AssertionError('Expected rejection: ' + fragment)


def initial_chain(folder, arm, batch, binding):
    folder.mkdir(); (folder / 'checkpoints').mkdir()
    state = run.initialize(arm, batch)
    path = folder / 'checkpoints/step-0000.npz'
    run.checkpoint(path, arm, state, binding)
    run.write_once(path.with_suffix('.json'), {'binding': binding, 'step': 0, 'checkpoint': run.pin(path),
                    'previousReceipt': None, 'updateIntent': None})
    return state


def update_once(folder, arm, state, batch, binding):
    step = state.updates + 1
    previous = folder / 'checkpoints' / f'step-{step - 1:04d}.npz'
    path = folder / 'checkpoints' / f'step-{step:04d}.npz'
    intent_path = path.parent / f'update-{step:04d}-intent.json'
    previous_receipt = run.pin(previous.with_suffix('.json'))
    run.write_once(intent_path, {'binding': binding, 'step': step, 'fromCheckpoint': run.pin(previous),
                                 'previousReceipt': previous_receipt})
    if arm == 'free':
        loss, gradients = obj.free_gradients(state.logits, batch)
        norm = obj.free_adam(state, gradients)
    else:
        loss, gradients = obj.network_gradients(state, batch)
        norm = state.adam(gradients, run.SETTINGS['learningRate'], run.SETTINGS['clipNorm'])
    run.require(np.isfinite(loss) and np.isfinite(norm) and state.updates == step, 'Invalid synthetic update')
    run.checkpoint(path, arm, state, binding)
    run.write_once(path.with_suffix('.json'), {'binding': binding, 'step': step, 'checkpoint': run.pin(path),
        'previousReceipt': previous_receipt, 'updateIntent': run.pin(intent_path),
        'lossBeforeUpdate': float(loss), 'unclippedGradientNorm': float(norm)})


def corrupt_snapshot(source, target, key, kind):
    with np.load(source, allow_pickle=False) as archive:
        payload = {name: archive[name].copy() for name in archive.files}
    if kind == 'nonfinite': payload[key].flat[0] = np.nan
    elif kind == 'frozen': payload[key].flat[0] += .25
    elif kind == 'illegal': payload[key].flat[3] = 1.
    else: raise ValueError('Unknown synthetic corruption')
    with target.open('xb') as stream: np.savez_compressed(stream, **payload)


def check_arm(root, arm, batch):
    root.mkdir()
    binding = {'schema': 'indrill-fit-one-SYNTHETIC-checkpoint', 'arm': arm,
               'syntheticOnly': True, 'fixedFixtureUpdates': 2}
    full_dir, boundary_dir = root / 'uninterrupted', root / 'boundary'
    full = initial_chain(full_dir, arm, batch, binding)
    for _ in range(2): update_once(full_dir, arm, full, batch, binding)
    original = initial_chain(boundary_dir, arm, batch, binding)
    update_once(boundary_dir, arm, original, batch, binding)
    restored = run.initialize(arm, batch)
    run.require(run.verify_chain(boundary_dir, arm, restored, binding, 2) == 1, 'Wrong safe resume boundary')
    same_state(arm, original, restored)
    update_once(boundary_dir, arm, restored, batch, binding)
    same_state(arm, full, restored)
    checked = run.initialize(arm, batch)
    run.require(run.verify_chain(boundary_dir, arm, checked, binding, 2) == 2, 'Completed synthetic chain differs')
    same_state(arm, full, checked)

    first = boundary_dir / 'checkpoints/step-0000.npz'
    original_hash = run.pin(first)
    write_once = reject(lambda: run.checkpoint(first, arm, checked, binding), 'File exists')
    run.require(run.pin(first) == original_hash, 'Write-once rejection changed retained bytes')
    stale = reject(lambda: run.restore(first, arm, run.initialize(arm, batch), {'wrong': True}, 0), 'metadata differs')
    wrong_step = reject(lambda: run.restore(first, arm, run.initialize(arm, batch), binding, 1), 'metadata differs')

    partial = root / 'uncommitted-fixture'
    partial_state = initial_chain(partial, arm, batch, binding)
    update_once(partial, arm, partial_state, batch, binding)
    previous = partial / 'checkpoints/step-0001.npz'
    run.write_once(previous.parent / 'update-0002-intent.json', {'binding': binding, 'step': 2,
        'fromCheckpoint': run.pin(previous), 'previousReceipt': run.pin(previous.with_suffix('.json'))})
    pending = reject(lambda: run.verify_chain(partial, arm, run.initialize(arm, batch), binding, 2), 'Uncommitted update')

    damaged = root / 'wrong-hash-fixture'; damaged_state = initial_chain(damaged, arm, batch, binding)
    damaged_path = damaged / 'checkpoints/step-0000.npz'
    with damaged_path.open('ab') as stream: stream.write(b'SYNTHETIC-CORRUPTION')
    hash_rejection = reject(lambda: run.verify_chain(damaged, arm, run.initialize(arm, batch), binding, 2), 'receipt chain differs')

    key = 'logits' if arm == 'free' else 'W0'
    nonfinite = root / 'nonfinite.npz'; corrupt_snapshot(first, nonfinite, key, 'nonfinite')
    nonfinite_rejection = reject(lambda: run.restore(nonfinite, arm, run.initialize(arm, batch), binding, 0), 'Nonfinite')
    frozen = root / 'forbidden-state.npz'
    corrupt_snapshot(first, frozen, 'logits' if arm == 'free' else 'WI', 'illegal' if arm == 'free' else 'frozen')
    frozen_rejection = reject(lambda: run.restore(frozen, arm, run.initialize(arm, batch), binding, 0),
                              'Illegal free-logit' if arm == 'free' else 'Frozen head parameter changed')
    # Both absolute historical paths and normalized workspace-relative pins bind
    # the same file bytes. Neither spelling may bypass the expected digest.
    run.check_pin(original_hash)
    run.check_pin({'path': str(first.resolve()), 'sha256': original_hash['sha256']})
    wrong_digest = reject(lambda: run.check_pin({'path': str(first.resolve()), 'sha256': '0' * 64}), 'Pinned bytes changed')

    missing_metrics = []
    for mask in range(7):  # Every incomplete subset, including all three absent.
        missing_dir = root / ('missing-metrics-' + str(mask))
        initial_chain(missing_dir, arm, batch, binding)
        paths = [missing_dir / ('metrics-0000' + suffix) for suffix in ('.json', '-intent.json', '-receipt.json')]
        for index, path in enumerate(paths):
            if mask & (1 << index): run.write_once(path, {'syntheticIncompleteTriplet': True})
        before = {p.relative_to(missing_dir).as_posix(): run.digest(p) for p in missing_dir.rglob('*') if p.is_file()}
        with patch.object(run, 'metrics', side_effect=AssertionError('Missing evidence must not trigger evaluation')) as guarded:
            refusal = reject(lambda: run.validate_metrics(missing_dir, 0, binding), 'Interrupted metrics')
            run.require(guarded.call_count == 0, 'Validation evaluated metrics')
        after = {p.relative_to(missing_dir).as_posix(): run.digest(p) for p in missing_dir.rglob('*') if p.is_file()}
        run.require(before == after, 'Read-only metric validation wrote evidence')
        missing_metrics.append({'existingTripletMask': mask, 'rejected': refusal, 'evaluations': 0, 'writes': 0})
    return {'updatesPerCompletedPath': 2, 'interruptionAfter': 1, 'checkpointAndAdamBitExactAfterResume': True,
        'completedChainVerified': True, 'writeOnceRejected': write_once, 'staleBindingRejected': stale,
        'wrongStepRejected': wrong_step, 'ambiguousUpdateRejected': pending, 'changedCheckpointRejected': hash_rejection,
        'nonfiniteStateRejected': nonfinite_rejection, 'forbiddenStateRejected': frozen_rejection,
        'absoluteAndRelativePinsAccepted': True, 'wrongDigestRejected': wrong_digest,
        'allIncompleteMetricTripletsRejectedWithoutEvaluationOrWrite': missing_metrics}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = run.absolute(args.out)
    setup = (run.OUTPUT / 'setup').resolve()
    proof_path = setup / 'runner-checks.json'
    run.require(out.is_relative_to(setup) and out != setup and not out.exists() and not proof_path.exists(), 'Choose a new directory under fit-one setup; preserve existing proof')
    original_settings = json.dumps(run.SETTINGS, sort_keys=True)
    paths = (Path(__file__), run.HERE / 'run_fit.py', run.HERE / 'objective.py',
             run.ROOT / 'tools/indrill/capacity_v1/subset_objective.py',
             run.ROOT / 'tools/indrill/train_q.py', run.ROOT / 'tools/indrill/train_q_v2.py')
    sources = {run.pin(p)['path']: run.pin(p)['sha256'] for p in paths}
    out.mkdir(parents=True)
    run.write_once(out / 'intent.json', {'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'syntheticOnly': True,
        'sourcePins': sources, 'realCorpusRowsRead': 0, 'realCandidateFitRuns': 0})
    batch = tiny_batch()
    arms = {arm: check_arm(out / arm, arm, batch) for arm in ('free', 'network')}
    run.require(json.dumps(run.SETTINGS, sort_keys=True) == original_settings and run.SETTINGS['updates'] == 2000, 'Production settings changed')
    for path, digest in sources.items(): run.check_pin({'path': path, 'sha256': digest})
    result = {'schema': 'indrill-fit-one-runner-checks-v1', 'passed': True, 'syntheticOnly': True,
        'sourcePins': sources, 'arms': arms, 'realCorpusRowsRead': 0, 'realCandidateFitRuns': 0,
        'productionSettingsUnchanged': True,
        'scope': 'Two-update checkpoint/persistence API rehearsal only; does not run real-row selection, freeze preparation or either production fit.'}
    run.write_once(out / 'result.json', result)
    run.write_once(proof_path, result)
    print(json.dumps({'passed': True, 'syntheticOnly': True, 'result': run.pin(proof_path), 'fixtures': out.relative_to(run.ROOT).as_posix()}))


if __name__ == '__main__':
    main()
