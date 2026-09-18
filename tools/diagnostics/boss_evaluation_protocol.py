#!/usr/bin/env python3
"""Exact small-sample boss comparison calculations; no bots, API calls or matches.

Python standard library only. Tests enumerate binomial/hypergeometric tails;
power enumerates all outcomes under explicitly specified hypothetical rates.
"""

import argparse
import functools
import json
import math
import pathlib


SIZES = (20, 30, 40, 60)
ALTERNATIVES = (0.4, 0.5, 0.6, 0.7)
ALPHA = 0.05


def binomial_pmf(k, n, probability):
    return math.comb(n, k) * probability**k * (1 - probability)**(n - k)


def binomial_upper(k, n, probability):
    return math.fsum(binomial_pmf(i, n, probability) for i in range(k, n + 1))


def known_30_percent_reject(k, n):
    numerator = sum(math.comb(n, i) * 3**i * 7**(n - i) for i in range(k, n + 1))
    return 20 * numerator <= 10**n


@functools.lru_cache(maxsize=None)
def fisher_fraction(candidate_wins, candidate_n, control_wins, control_n):
    """P(X >= observed candidate wins | total wins), alternative candidate > control."""
    total_wins = candidate_wins + control_wins
    numerator = sum(
        math.comb(candidate_n, x) * math.comb(control_n, total_wins - x)
        for x in range(candidate_wins, min(candidate_n, total_wins) + 1)
        if 0 <= total_wins - x <= control_n
    )
    return numerator, math.comb(candidate_n + control_n, total_wins)


def fisher_p(candidate_wins, candidate_n, control_wins, control_n):
    numerator, denominator = fisher_fraction(candidate_wins, candidate_n, control_wins, control_n)
    return numerator / denominator


def fisher_reject(candidate_wins, candidate_n, control_wins, control_n):
    numerator, denominator = fisher_fraction(candidate_wins, candidate_n, control_wins, control_n)
    return 20 * numerator <= denominator


def fisher_threshold(candidate_n, control_wins, control_n):
    return next((wins for wins in range(candidate_n + 1)
        if fisher_reject(wins, candidate_n, control_wins, control_n)), None)


def independent_power(n, candidate_probability, control_probability=0.3):
    return math.fsum(
        binomial_pmf(a, n, candidate_probability) * binomial_pmf(b, n, control_probability)
        for a in range(n + 1) for b in range(n + 1) if fisher_reject(a, n, b, n)
    )


def paired_threshold(discordant):
    # Under equal marginal win rates, candidate-only wins among discordant pairs
    # have conditional probability 1/2. Decisions use exact integer arithmetic.
    return next((k for k in range(discordant + 1)
        if 20 * sum(math.comb(discordant, i) for i in range(k, discordant + 1)) <= 2**discordant), None)


def paired_power(n, candidate_probability, control_probability, both_win_probability):
    low = max(0, candidate_probability + control_probability - 1)
    high = min(candidate_probability, control_probability)
    assert low <= both_win_probability <= high
    discordant_probability = candidate_probability + control_probability - 2 * both_win_probability
    if discordant_probability == 0:
        return 0.0
    candidate_only_given_discordance = (candidate_probability - both_win_probability) / discordant_probability
    terms = []
    for discordant in range(1, n + 1):
        threshold = paired_threshold(discordant)
        if threshold is not None:
            terms.append(binomial_pmf(discordant, n, discordant_probability)
                * binomial_upper(threshold, discordant, candidate_only_given_discordance))
    return math.fsum(terms)


def clopper_pearson(wins, games, confidence=0.95):
    tail = (1 - confidence) / 2

    def invert(function, target, increasing):
        low, high = 0.0, 1.0
        for _ in range(90):
            mid = (low + high) / 2
            if (function(mid) < target) == increasing:
                low = mid
            else:
                high = mid
        return (low + high) / 2

    lower = 0.0 if wins == 0 else invert(lambda p: binomial_upper(wins, games, p), tail, True)
    upper = 1.0 if wins == games else invert(
        lambda p: math.fsum(binomial_pmf(i, games, p) for i in range(wins + 1)), tail, False)
    return [lower, upper]


def report():
    sizes = []
    for n in SIZES:
        known = next(k for k in range(n + 1) if known_30_percent_reject(k, n))
        historical = fisher_threshold(n, 6, 20)
        fresh_observed = []
        for control_wins in sorted({round(n * p) for p in (0.2, 0.3, 0.4)}):
            threshold = fisher_threshold(n, control_wins, n)
            fresh_observed.append({
                "control_wins": control_wins, "candidate_min_wins": threshold,
                "p_at_threshold": fisher_p(threshold, n, control_wins, n) if threshold is not None else None,
                "p_one_fewer": fisher_p(threshold - 1, n, control_wins, n) if threshold is not None and threshold > 0 else None,
            })
        sizes.append({
            "candidate_n": n,
            "known_p0_030": {
                "min_wins": known, "p_at_threshold": binomial_upper(known, n, 0.3),
                "p_one_fewer": binomial_upper(known - 1, n, 0.3),
                "power_by_candidate_rate": {str(p): binomial_upper(known, n, p) for p in ALTERNATIVES},
            },
            "historical_6_of_20": {
                "min_wins": historical, "p_at_threshold": fisher_p(historical, n, 6, 20),
                "p_one_fewer": fisher_p(historical - 1, n, 6, 20),
                "conditional_power_given_observed_historical_count": {
                    str(p): binomial_upper(historical, n, p) for p in ALTERNATIVES},
            },
            "fresh_independent_equal_n": {
                "control_n": n, "hypothetical_true_control_rate": 0.3,
                "type1_at_common_rate_030": independent_power(n, 0.3),
                "power_by_candidate_rate": {str(p): independent_power(n, p) for p in ALTERNATIVES},
                "thresholds_by_observed_control_count": fresh_observed,
            },
            "matched_pairs_example_060_vs_030": {
                "pairs": n,
                "power_by_both_win_probability": {str(q): paired_power(n, 0.6, 0.3, q) for q in (0.0, 0.18, 0.3)},
            },
        })
    return {
        "schema": 1, "name": "boss-evaluation-protocol", "date": "2026-09-18",
        "alpha": ALPHA, "alternative": "candidate outright-win probability exceeds control",
        "draws": "Non-wins for this binary boss-specific test; report draws and half-win score separately.",
        "historical_6_of_20_cp95": clopper_pearson(6, 20),
        "illustration_10_of_20_vs_historical_6_of_20_fisher_p": fisher_p(10, 20, 6, 20),
        "sizes": sizes,
        "paired_threshold_examples": {str(n): paired_threshold(n) for n in (5, 10, 15, 20, 30, 40, 60)},
        "cp95_examples": {f"{k}/{n}": clopper_pearson(k, n) for k, n in (
            (10, 20), (12, 20), (13, 20), (15, 30), (18, 30), (20, 40), (21, 40), (24, 40), (30, 60), (36, 60))},
        "limitations": [
            "All powers are hypothetical model probabilities, not estimated achieved power or guarantees.",
            "Known p0=.30 is a separate restricted hypothesis; the old 6/20 does not make .30 known.",
            "Historical conditional power fixes the already observed six wins; it is not fresh two-arm power.",
            "Independent Fisher calculations require independent maps/sampling units within and between arms.",
            "Same-map/seat comparisons require the paired test and a joint outcome model for power.",
            "No sequence of looks, selected runs, retuning, multiplicity correction or promotion is modeled.",
            "Existing practical-300-1000-v2 strategy promotion gate remains unchanged.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=pathlib.Path)
    parser.add_argument("--check", type=pathlib.Path)
    args = parser.parse_args()
    result = report()
    if args.check:
        assert result == json.loads(args.check.read_text(encoding="utf-8")), "Calculation evidence differs"
        print("All exact thresholds, power enumerations and interval inversions reproduced.")
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {args.out}")
    if not args.out and not args.check:
        print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
