"""Static sufficient invariances, distinct from input aliasing and optimizer results.

No corpus I/O or learned-weight assumptions. Multiset signatures refer to real
arithmetic. Ordered refinements preserve Java17 scalar averaging operands; only
those refinements enter conditional decoder tie constraints. NumPy matrix kernels
can differ by row position even for identical operands. These static reports do
not establish a universal capacity ceiling for floating-point programs.
"""
from __future__ import annotations

from collections import Counter, defaultdict
import hashlib
import json
from math import gcd

import numpy as np

SCHEMA = "indrill-capacity-representation-certificates-v1"


def _normalized_multiset(ids):
    counts = Counter(ids)
    total = sum(counts.values())
    return tuple((key, value // gcd(value, total), total // gcd(value, total))
                 for key, value in sorted(counts.items()))


def _intern(values):
    known, ids = {}, []
    for value in values:
        if value not in known:
            known[value] = len(known)
        ids.append(known[value])
    return ids


def _groups(signatures):
    result = defaultdict(list)
    for index, signature in enumerate(signatures):
        result[signature].append(index)
    return [members for members in result.values() if len(members) > 1]


def input_label_binding(rows, indices=None):
    ids = range(len(rows)) if indices is None else indices
    bound = []
    for index in ids:
        row = rows[int(index)]
        bound.append([row["observationId"], row["inputSha256"],
                      [int(c) for c in row["targetCells"]], int(row["targetInk"])])
    bound.sort(key=lambda item: item[0])
    return hashlib.sha256(json.dumps(bound, separators=(",", ":"), ensure_ascii=True).encode("ascii")).hexdigest()


def observation_certificates(row):
    x = np.asarray(row["features"], dtype=np.float32)
    neighbors = np.asarray(row["neighbors"], dtype=np.int32)
    regions = np.asarray(row["regions"], dtype=np.int32)
    sizes = np.asarray(row["regionSizes"], dtype=np.int32)
    legal_build = np.asarray(row["legalBuild"], dtype=bool)
    costs = np.asarray(row["costs"], dtype=np.int32)
    legal_ink = np.asarray(row["legalInk"], dtype=bool)
    n, r = int(row["size"]), int(row["regionCount"])
    if x.shape != (n, 64) or neighbors.shape != (n, 4) or not np.isfinite(x).all():
        raise ValueError("Certificate requires validated finite observation features/topology")
    if not np.array_equal(np.bincount(regions, minlength=r), sizes):
        raise ValueError("Certificate region sizes disagree")
    # Equality uses exact raw byte strings, not just their cryptographic hashes.
    feature_ids = _intern([values.astype(">f4").tobytes() for values in x])
    mathematical, ordered = [], []
    for cell in range(n):
        ns = neighbors[cell]
        if ((ns < -1) | (ns >= n)).any():
            raise ValueError("Invalid certificate neighbor index")
        present = [feature_ids[int(other)] for other in ns if other >= 0]
        mathematical.append((feature_ids[cell], _normalized_multiset(present)))
        # Keep directions/missing slots and divisor, not just the neighbour set.
        # This is conservative: some other ordered operands also sum identically.
        ordered.append((feature_ids[cell], tuple(feature_ids[int(other)] if other >= 0 else -1 for other in ns), len(present)))
    math_cell_ids, runtime_cell_ids = _intern(mathematical), _intern(ordered)
    math_regions, ordered_regions = [], []
    for region in range(r):
        members = np.flatnonzero(regions == region)
        math_regions.append(_normalized_multiset([math_cell_ids[int(c)] for c in members]))
        ordered_regions.append((len(members), tuple(runtime_cell_ids[int(c)] for c in members)))
    math_cell_groups, runtime_cell_groups = _groups(mathematical), _groups(ordered)
    math_region_groups, runtime_region_groups = _groups(math_regions), _groups(ordered_regions)
    target = set(int(c) for c in row["targetCells"])
    target_ink = int(row["targetInk"])

    def constraints(cell_groups, region_groups):
        result = []
        for group in cell_groups:
            for cost in (1, 2, 3):
                eligible = [c for c in group if legal_build[c] and costs[c] == cost]
                for higher in eligible:
                    if higher not in target:
                        continue
                    omitted = [lower for lower in eligible if lower < higher and lower not in target]
                    if omitted:
                        result.append({"head": "build", "cost": cost, "lowerOmitted": omitted[0],
                                       "higherSelected": higher, "classMembers": eligible,
                                       "decoderRule": "same-cost shortlist retains lower indices before pattern aggregation"})
        if target_ink >= 0:
            for group in region_groups:
                if target_ink not in group:
                    continue
                lower = [region for region in group if legal_ink[region] and region < target_ink]
                if lower:
                    result.append({"head": "ink", "lowerLegal": lower[0], "higherSelected": target_ink,
                                   "classMembers": [region for region in group if legal_ink[region]],
                                   "decoderRule": "strict greater-than ink scan keeps lower region index on a positive tie"})
        return result

    structural_constraints = constraints(math_cell_groups, math_region_groups)
    conditional = constraints(runtime_cell_groups, runtime_region_groups)
    return {"observationId": row["observationId"], "inputSha256": row["inputSha256"],
            "globalContext": "Within this observation the global mean is shared by every cell.",
            "mathematicalCellGroups": math_cell_groups, "mathematicalRegionGroups": math_region_groups,
            "runtimeCellClasses": runtime_cell_groups, "runtimeRegionClasses": runtime_region_groups,
            "structuralTieConstraints": structural_constraints,
            "conditionalDecoderConstraints": conditional,
            "decoderIncompatibilityNotProven": bool(structural_constraints),
            "decoderProofScope": "Conditional on finite bitwise-equal logits: same-cost cell shortlists and the ink scan resolve ties before joint/pattern score aggregation. No claim about other tied patterns or unequal costs.",
            "runtimeImplementation": "Java17 scalar loops in src/main/java/btk/bot/current/IndrillV2Net.java, with finite intermediate results",
            "numpyRuntimeEqualityCertified": False,
            "runtimeEqualityScope": "Ordered signatures preserve Java scalar per-cell self/neighbor operations and divisor, then ordered equal-sized region pooling operations. These classes are not NumPy bitwise-equality certificates: row-dependent BLAS kernels can change rounding for identical operands. Mathematical multisets alone do not preserve reduction order; synthetic witnesses are implementation checks, not universal program-capacity bounds."}


def certificate_report(rows, indices=None):
    ids = list(range(len(rows))) if indices is None else [int(i) for i in indices]
    report = {"schema": SCHEMA, "complete": True, "observations": len(ids),
              "inputLabelBindingSha256": input_label_binding(rows, ids),
              "mathematicalCellGroups": 0, "mathematicalRegionGroups": 0,
              "runtimeCellClasses": 0, "runtimeRegionClasses": 0,
              "observationsWithConditionalTieObstruction": 0,
              "capacityCeilingApplied": False, "details": []}
    for i in ids:
        item = observation_certificates(rows[i])
        for key in ("mathematicalCellGroups", "mathematicalRegionGroups", "runtimeCellClasses", "runtimeRegionClasses"):
            report[key] += len(item[key])
        report["observationsWithConditionalTieObstruction"] += int(bool(item["conditionalDecoderConstraints"]))
        if any(item[key] for key in ("mathematicalCellGroups", "mathematicalRegionGroups", "runtimeCellClasses", "runtimeRegionClasses")):
            report["details"].append(item)
    report["interpretation"] = {
        "mathematicalInvariance": "Sufficient real-arithmetic equivalences, not arbitrary reflection/rotation symmetries. Coordinates and directional features are retained.",
        "conditionalDecoderConstraints": "Only Java scalar ordered refinements with explicit same-cost or ink-scan tie constraints, conditional on equal finite logits. NumPy runtime equality and a universal decoder capacity bound remain unproven. No automatic capacity ceiling or stop criterion.",
        "observationalAliasing": "Identical complete inputs with conflicting labels have a separate empirical ceiling reported by collision_summary; this is not the same as local pooling symmetry.",
        "optimization": "Static certificates do not establish fitting, convergence, a representation bottleneck, or a failure of reinforcement learning."}
    return report
