"""Synthetic-only sufficient-invariance and frozen-decoder implementation checks.

These checks do not prove universal floating-point capacity bounds, access a
teacher corpus, or train a model. Results are create-new and pin checked sources.
"""
from __future__ import annotations

import copy
import hashlib
import itertools
import json
from pathlib import Path
import sys

import numpy as np

import representation_certificates as cert
import teacher_dataset as data

q = data.q


def fixture(width=5, height=3, target=(8,), ink=8):
    n = width * height
    features = np.zeros((n, 64), dtype=np.float32)
    features[:, 0] = .25
    regions = np.arange(n, dtype=np.int32)
    costs = np.ones(n, dtype=np.int32)
    episode = q.Episode("synthetic", width, height, n, regions, costs, [])
    row = dict(schema=data.OBS_SCHEMA, observationId="10:0:0", gameId=10,
               teacherSeat=0, turn=0, teacherFrameIndex=1, teacherAgentId=123,
               mapHash="synthetic-map", replaySha256="synthetic", split="train",
               panelTags=["A", "B"], cohortTags=[], width=width, height=height,
               size=n, regionCount=n, featureCount=64, paintBudget=3,
               regions=regions.tolist(), costs=costs.tolist(),
               legalBuild=[True] * n, buildCost=costs.tolist(), legalInk=[True] * n,
               neighbors=np.where(episode.neighbor_mask, episode.neighbors, -1).tolist(),
               regionSizes=[1] * n, features=features.tolist(), targetCells=list(target),
               targetInk=ink, acceptedPaint=len(target), hadSkippedActions=False,
               skippedActions=[])
    row["inputSha256"] = data.modelinput_sha256(row)
    return row


def rehash(row):
    row["inputSha256"] = data.modelinput_sha256(row)
    return row


def has_group(groups, members):
    return any(set(members).issubset(group) for group in groups)


def run(output):
    output = Path(output)
    if output.exists():
        raise ValueError("Refusing existing synthetic result")
    row = fixture()
    report = cert.observation_certificates(row)
    assert has_group(report["mathematicalCellGroups"], range(15))
    assert has_group(report["runtimeCellClasses"], [6, 7, 8])
    assert not has_group(report["runtimeCellClasses"], [0, 6])
    assert has_group(report["runtimeRegionClasses"], [6, 7, 8])
    assert any(c["head"] == "build" and c["lowerOmitted"] == 6 and c["higherSelected"] == 8
               for c in report["conditionalDecoderConstraints"])
    assert any(c["head"] == "ink" and c["lowerLegal"] == 6 for c in report["conditionalDecoderConstraints"])

    # Equal proportions with different degrees certify a real mean, not a
    # float32 reduction schedule. Single-row boundaries have different masks.
    boundary = fixture(3, 1, target=(1,), ink=-1)
    boundary_report = cert.observation_certificates(boundary)
    assert has_group(boundary_report["mathematicalCellGroups"], [0, 1, 2])
    assert not boundary_report["runtimeCellClasses"]
    assert boundary_report["structuralTieConstraints"]
    assert not boundary_report["conditionalDecoderConstraints"]
    assert boundary_report["decoderIncompatibilityNotProven"]

    # Repeating a cell class twice and four times preserves a mathematical
    # region mean. Different divisors/ordered lengths are not runtime equality.
    proportions = fixture(6, 1, target=(), ink=1)
    proportions.update(regions=[0, 0, 1, 1, 1, 1], regionCount=2,
                       regionSizes=[2, 4], legalInk=[True, True])
    rehash(proportions)
    proportions_report = cert.observation_certificates(proportions)
    assert proportions_report["mathematicalRegionGroups"] == [[0, 1]]
    assert not proportions_report["runtimeRegionClasses"]

    # Raw coordinates remain inputs. A reflection is not assumed to preserve
    # the representation when it changes either coordinate feature.
    coordinates = copy.deepcopy(row)
    for c, features in enumerate(coordinates["features"]):
        features[12], features[13] = c % 5 / 4, c // 5 / 2
    rehash(coordinates)
    coordinate_report = cert.observation_certificates(coordinates)
    assert not coordinate_report["mathematicalCellGroups"]
    assert not coordinate_report["runtimeCellClasses"]
    assert not coordinate_report["mathematicalRegionGroups"]

    # A lower illegal cell/region cannot create an obstruction. Unequal costs
    # have different shortlist classes and do not justify a tie certificate.
    masked = copy.deepcopy(row)
    for c in [6, 7]:
        masked["legalBuild"][c] = masked["legalInk"][c] = False
        masked["buildCost"][c] = 0
    rehash(masked)
    assert not cert.observation_certificates(masked)["conditionalDecoderConstraints"]
    different_cost = copy.deepcopy(row)
    different_cost["costs"][8] = different_cost["buildCost"][8] = 2
    different_cost.update(acceptedPaint=2, targetInk=-1)
    rehash(different_cost)
    assert not cert.observation_certificates(different_cost)["conditionalDecoderConstraints"]

    # Sampled forward checks are implementation witnesses, not proof for every
    # possible matrix kernel/weight or all floating-point aggregation patterns.
    dataset = data.dataset_from_rows([row])
    batch = dataset.batch([0])
    sampled_models = 12
    numpy_counterexamples = []
    for seed in range(sampled_models):
        model = q.Model(np.random.default_rng(8000 + seed))
        build, ink = model.forward(batch)
        for group in report["runtimeCellClasses"]:
            bits = build[0, group].view(np.uint32)
            assert np.allclose(build[0, group], build[0, group[0]], rtol=1e-4, atol=1e-6)
            if not np.all(bits == bits[0]):
                numpy_counterexamples.append(dict(seed=8000 + seed, head="build", cells=group,
                                                  rawFloat32Bits=bits.tolist()))
        for group in report["runtimeRegionClasses"]:
            bits = ink[0, group].view(np.uint32)
            assert np.allclose(ink[0, group], ink[0, group[0]], rtol=1e-4, atol=1e-6)
            if not np.all(bits == bits[0]):
                numpy_counterexamples.append(dict(seed=8000 + seed, head="ink", regions=group,
                                                  rawFloat32Bits=bits.tolist()))
    assert report["numpyRuntimeEqualityCertified"] is False

    # The same-cost shortlist orders before aggregate pattern comparisons.
    # Test all small contexts, including large finite magnitudes, rather than
    # attempting to infer an all-weights bound from these finite witnesses.
    levels = (-1e30, -1, 0, 1, 1e30)
    decoder_cases = 0
    for cost in (1, 2, 3):
        costs = np.asarray([1, cost, 2, cost, 3], dtype=np.int32)
        for equal, first, middle, last in itertools.product(levels, repeat=4):
            values = np.asarray([first, equal, middle, equal, last], dtype=np.float32)
            chosen = q.choose_build(values, costs, np.ones(5, dtype=bool))
            assert not (3 in chosen and 1 not in chosen)
            decoder_cases += 1
    for equal in levels:
        # Regions 6 and 8 have exact equal logits; strict > keeps lower 6,
        # while nonpositive logits choose NONE. Other regions are illegal.
        batch.legal_ink[:] = False
        batch.legal_ink[0, [6, 8]] = True
        ink = np.zeros((1, 15), dtype=np.float32)
        ink[0, [6, 8]] = equal
        _, inks = q.choose_actions(np.zeros((1, 15), dtype=np.float32), ink, batch)
        assert inks == ([6] if equal > 0 else [-1])

    full = dataset.manifest["representationCertificates"]
    assert full["schema"] == cert.SCHEMA and full["complete"]
    assert full["observations"] == 1 and full["capacityCeilingApplied"] is False
    assert full["inputLabelBindingSha256"] == cert.input_label_binding(dataset.rows)
    changed = copy.deepcopy(dataset.rows[0]); changed["targetCells"] = []
    assert cert.input_label_binding([changed]) != full["inputLabelBindingSha256"]
    repeated = copy.deepcopy(row)
    repeated.update(observationId="11:0:0", gameId=11, targetCells=[], targetInk=-1, acceptedPaint=0)
    pair = data.dataset_from_rows([row, repeated])
    assert pair.manifest["collisionSummary"]["A"]["exactJointCeilingCount"] == 1
    assert pair.manifest["representationCertificates"]["capacityCeilingApplied"] is False
    assert cert.input_label_binding(pair.rows) == cert.input_label_binding(pair.rows, [1, 0])

    here = Path(__file__).resolve().parent
    pins = [Path(__file__), here / "representation_certificates.py", here / "teacher_dataset.py",
            data.ROOT / "tools/indrill/train_q.py"]
    result = {"syntheticOnly": True, "passed": True, "schema": "indrill-capacity-certificate-checks-v1",
              "certificateSchema": cert.SCHEMA, "sampledForwardModels": sampled_models,
              "sameCostDecoderWitnesses": decoder_cases, "inkDecoderWitnesses": len(levels),
              "checks": ["normalized_neighbor_means", "ordered_runtime_classes", "normalized_region_means",
                         "coordinates_break_reflection", "legality_and_cost_scope", "numpy_rounding_scope",
                         "same_cost_shortlist_ties", "ink_scan_ties", "loader_binding", "aliasing_separate"],
              "scope": "Synthetic implementation checks only. No universal floating-point capacity ceiling; no corpus or training.",
              "numpyUniversalEqualityClaimed": False, "numpyCounterexampleCount": len(numpy_counterexamples),
              "numpyCounterexamples": numpy_counterexamples[:12],
              "sourcePins": {str(p.relative_to(data.ROOT)).replace(chr(92), "/"): hashlib.sha256(p.read_bytes()).hexdigest()
                             for p in pins}}
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2); stream.write("\n")
    print(json.dumps(result))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: certificate_synthetic_checks.py NEW_RESULT_JSON")
    run(sys.argv[1])
