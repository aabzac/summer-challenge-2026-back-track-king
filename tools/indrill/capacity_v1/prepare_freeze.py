"""Create the diagnostic's write-once training freeze after reviewed setup/export.

No gradients, game requests or uploads. Sources and all current implementation
checks are pinned from their installed paths; preparation-era proof is not reused.
"""
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
import export_launcher as exporter
import train_capacity as trainer
import teacher_dataset


def main():
    execution = exporter.verify_execution()
    run, setup, dataset = exporter.RUN, exporter.SETUP, exporter.DATASET
    output = run / 'training-freeze.json'
    trainer.require(not output.exists(), 'Training freeze already exists')
    for mode in ('preflight', 'export'):
        result = trainer.read(setup / (mode + '-result.json'))
        trainer.require(result['exitCode'] == 0 and result['executionFreeze'] == exporter.pin(exporter.EXECUTION), 'Missing successful bound export stage')
    data = teacher_dataset.load_dataset(dataset)
    trainer.dataset_precheck(data)
    python_proof = setup / 'certificate-synthetic-first.json'
    java_proof = setup / 'certificate-java-first.json'
    certificate_proofs = {'python': trainer.file_pin(python_proof), 'java': trainer.file_pin(java_proof)}
    trainer.certificate_check_proofs(certificate_proofs)
    for path in (setup / 'synthetic-java/synthetic-java-result.json', setup / 'synthetic-loader/result.json'):
        proof = trainer.read(path)
        trainer.require(proof['syntheticOnly'] is True and proof['passed'] is True, 'Synthetic export/loader check failed')
    comparator = ROOT / 'build/analysis/indrill-q-v2/weights-6.bin'
    with comparator.open('rb') as stream:
        tensors = trainer.v2.read_weight_tensors(stream)
    with trainer.np.load(comparator.with_suffix('.npz'), allow_pickle=False) as saved:
        trainer.require(all(trainer.np.array_equal(tensors[name], saved[name]) for name in tensors), 'Frozen comparator binary/checkpoint mismatch')
    paths = {p.resolve() for p in trainer.PREP.iterdir() if p.is_file()}
    paths |= {p.resolve() for p in setup.rglob('*') if p.is_file()}
    paths |= {p.resolve() for p in dataset.rglob('*') if p.is_file()}
    paths |= {ROOT / 'docs/indrill-capacity-v1.md', ROOT / 'tools/indrill/train_q.py', ROOT / 'tools/indrill/train_q_v2.py',
              comparator, comparator.with_suffix('.npz')}
    # Workspace pins are flattened. JDK/Gson pins outside the repo remain explicit
    # in exportFreeze and are rechecked by exporter.verify_execution every time.
    paths |= {Path(item['path']).resolve() for item in execution['pins'] if Path(item['path']).resolve().is_relative_to(ROOT)}
    value = {'schema': 'indrill-capacity-training-freeze-v1', 'status': 'FROZEN',
             'outputDirectory': run.relative_to(ROOT).as_posix(), 'datasetDirectory': dataset.relative_to(ROOT).as_posix(),
             'pins': [trainer.file_pin(p) for p in sorted(paths)],
             'syntheticChecks': trainer.file_pin(setup / 'synthetic-checks-first.json'),
             'trainerChecks': trainer.file_pin(setup / 'synthetic-trainer/result.json'),
             'certificateChecks': certificate_proofs, 'exportFreeze': trainer.file_pin(exporter.EXECUTION),
             'comparator': trainer.file_pin(comparator), 'checksPassed': True,
             'setupLimitations': 'Bounded synthetic implementation checks and reviewed real export; no fitting or transfer result.'}
    # Validate a create-new candidate manifest before sealing its canonical name.
    candidate = run / 'training-freeze-candidate.json'
    trainer.write_once(candidate, value)
    trainer.verify_freeze(candidate)
    exporter.verify_execution()
    trainer.write_once(output, value)
    trainer.verify_freeze(output)
    print(json.dumps({'trainingFreeze': trainer.file_pin(output), 'gradientsRun': 0}))


if __name__ == '__main__':
    main()
