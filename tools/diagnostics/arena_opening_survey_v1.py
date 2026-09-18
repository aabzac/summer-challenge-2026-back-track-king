#!/usr/bin/env python3
"""Reproduce a fixed descriptive opening survey from recorded CG viewer events.

This does not simulate rules, calculate paths, or evaluate hypothetical scores.
Recorded type-3 point events are summed; placement/ink events track only observed
occupancy for the empty-region disruption diagnostic. All game rules remain in core.
"""

import argparse
import collections
import hashlib
import json
import pathlib
import re
import statistics


TOP_IDS = (
    904811950, 904811503, 904811442, 904811378, 904811095,
    904812234, 904812076, 904811609, 904811514, 904811477,
    904812209, 904812115, 904812077, 904811625, 904811413,
    904812214, 904812171, 904812124, 904811992, 904811982,
    904811901, 904811810, 904811567,
)
OWN_IDS = (904812240, 904811869, 904810618, 904809888, 904809774)
BOSS_IDS = (
    904553513, 904553687, 904553769, 904553911, 904553970,
    904554072, 904554369, 904554429, 904554475, 904554568,
    904554907, 904554986, 904555027, 904555096, 904555166,
    904555229, 904555291, 904555363, 904555424, 904555504,
)
PLACE = re.compile(r"PLACE_TRACKS\s+(\d+)\s+(\d+)")


def view(frame):
    text = frame.get("view", "").partition("\n")[2].strip()
    return json.loads(text) if text else {}


def events(frame):
    fields = view(frame).get("graphics", "0").split("|")
    return [
        (int(fields[1 + 4 * i]), list(map(int, fields[4 + 4 * i].split())))
        for i in range(int(fields[0]))
    ]


def mean(values):
    values = [value for value in values if value is not None]
    return round(statistics.mean(values), 6) if values else None


def game_record(root, cohort, game_id):
    relative = pathlib.Path("replays/cg") / ("bot" if cohort == "boss" else "") / f"{game_id}.json"
    raw = (root / relative).read_bytes()
    doc = json.loads(raw)
    assert doc["gameId"] == game_id
    global_fields = view(doc["frames"][0])["global"]["graphics"].split("|")
    width, height = map(int, global_fields[:2])
    regions = [int(cell.split()[0]) for cell in global_fields[3:3 + width * height]]
    town_count = int(global_fields[3 + width * height])
    towns = [tuple(map(int, town.split()[:3])) for town in global_fields[4 + width * height:]]
    assert len(towns) == town_count
    town_regions = {regions[y * width + x] for _, x, y in towns}

    def placement_metrics(cells):
        touched = {town for x, y in cells for town, tx, ty in towns if abs(x - tx) + abs(y - ty) == 1}
        return {
            "cells": len(cells),
            "unique_cells": len(set(cells)),
            "protected_region_cells": sum(regions[y * width + x] in town_regions for x, y in cells),
            "town_adjacent_cells": sum(any(abs(x - tx) + abs(y - ty) == 1 for _, tx, ty in towns) for x, y in cells),
            "distinct_adjacent_towns": len(touched),
        }

    names, agents, bosses = {}, {}, {}
    for agent in doc["agents"]:
        player = agent["index"]
        agents[player] = agent.get("agentId")
        bosses[player] = "arenaboss" in agent or agents[player] == -2
        names[player] = agent.get("codingamer", {}).get("pseudo") or agent.get("arenaboss", {}).get("nickname") or ("Boss" if bosses[player] else "aabzac")
    assert set(names) == {0, 1}
    groups = {
        player: ("boss" if bosses[player] else "v07-boss") if cohort == "boss"
        else "top-arena" if cohort == "top"
        else "own-arena" if names[player] == "aabzac" else "own-opponents"
        for player in (0, 1)
    }
    stdout, point_events, accepted_first3 = [[], []], [[], []], [[], []]
    disruption_counts = [collections.Counter(), collections.Counter()]
    owner = {}
    neutral_first3 = 0
    turn = 0
    pending = ["", ""]
    failures = []
    final_scores = None
    for frame in doc["frames"][1:]:
        player = frame.get("agentId")
        if player in (0, 1) and frame.get("stdout") is not None:
            pending[player] = frame["stdout"].strip()
        if not frame.get("keyframe"):
            continue
        turn += 1
        for player in (0, 1):
            stdout[player].append(pending[player])
        pending = ["", ""]
        if frame.get("summary"):
            failures.append({"turn": turn, "summary": frame["summary"]})
        gained = [0, 0]
        for kind, data in events(frame):
            if kind == 1:
                player, x, y = data[:3]
                owner[y * width + x] = player
                if turn <= 3:
                    if player in (0, 1):
                        accepted_first3[player].append((x, y))
                    else:
                        assert player == 2
                        neutral_first3 += 1
            elif kind == 2:
                player, region = data
                observed_owners = [owned_by for cell, owned_by in owner.items() if regions[cell] == region]
                count = disruption_counts[player]
                for suffix in ("all", "first1", "first3", "first10"):
                    if suffix != "all" and turn > int(suffix[5:]):
                        continue
                    count["disruptions_" + suffix] += 1
                    count["empty_region_" + suffix] += not observed_owners
                    count["no_enemy_track_" + suffix] += 1 - player not in observed_owners
            elif kind == 3:
                gained[data[0]] += data[1]
            elif kind == 5:
                # Reflect the recorded ink event; no instability/ink rules are simulated.
                for cell in list(owner):
                    if regions[cell] == data[0]:
                        del owner[cell]
        for player in (0, 1):
            point_events[player].append(gained[player])
        end_screen = view(frame).get("endScreen")
        if end_screen:
            final_scores = end_screen[0]
    assert final_scores is not None
    assert not any(pending), "Unexpected incomplete terminal turn"
    sides = []
    for player in (0, 1):
        commands = stdout[player][:3]
        assert not any("AUTOPLACE" in command for command in commands), "Explicit placement metric requires no opening AUTOPLACE"
        attempted1 = [tuple(map(int, cell)) for cell in PLACE.findall(commands[0])]
        attempted3 = [tuple(map(int, cell)) for command in commands for cell in PLACE.findall(command)]
        points = point_events[player]
        # A consistency check against recorded data, not a recalculation of game scoring.
        if final_scores[player] >= 0:
            assert sum(points) == final_scores[player]
        sides.append({
            "player": player, "name": names[player], "agent_id": agents[player],
            "group": groups[player], "won": doc["ranks"][player] == 0,
            "final_score": final_scores[player], "opening_commands_first3": commands,
            "attempted_first1": placement_metrics(attempted1),
            "attempted_first3": placement_metrics(attempted3),
            "accepted_owned_first3": placement_metrics(accepted_first3[player]),
            "first_scoring_turn": next((i + 1 for i, points in enumerate(points) if points), None),
            "recorded_points_first8": sum(points[:8]),
            "recorded_points_first15": sum(points[:15]),
            "recorded_points_first20": sum(points[:20]),
            "recorded_income_turn15": points[14] if len(points) >= 15 else None,
            "recorded_income_turn20": points[19] if len(points) >= 20 else None,
            "disruption": {f"{metric}_{period}": disruption_counts[player][f"{metric}_{period}"]
                for period in ("all", "first1", "first3", "first10")
                for metric in ("disruptions", "empty_region", "no_enemy_track")},
        })
    return {
        "game_id": game_id, "cohort": cohort, "path": relative.as_posix(),
        "sha256": hashlib.sha256(raw).hexdigest(), "bytes": len(raw),
        "width": width, "height": height, "town_count": town_count,
        "turns": turn, "neutral_placements_first3": neutral_first3,
        "server_summaries": failures, "sides": sides,
    }


def aggregate(records, predicate):
    rows = [(record, side) for record in records for side in record["sides"] if predicate(side)]
    result = {
        "game_sides": len(rows), "distinct_games": len({game["game_id"] for game, _ in rows}),
        "player_names": sorted({side["name"] for _, side in rows}),
        "wins": sum(side["won"] for _, side in rows),
        "mean_turns": mean([game["turns"] for game, _ in rows]),
        "total_turns": sum(game["turns"] for game, _ in rows),
    }
    for key in ("attempted_first1", "attempted_first3", "accepted_owned_first3"):
        result[key] = {
            metric: sum(side[key][metric] for _, side in rows)
            for metric in ("cells", "unique_cells", "protected_region_cells", "town_adjacent_cells")
        }
        result[key].update({
            "mean_distinct_adjacent_towns": mean([side[key]["distinct_adjacent_towns"] for _, side in rows]),
            "sides_touching_at_least2_towns": sum(side[key]["distinct_adjacent_towns"] >= 2 for _, side in rows),
            "sides_touching_at_least3_towns": sum(side[key]["distinct_adjacent_towns"] >= 3 for _, side in rows),
        })
    result["mean_recorded_points"] = {
        key: mean([side[key] for _, side in rows]) for key in (
            "first_scoring_turn", "recorded_points_first8", "recorded_points_first15",
            "recorded_points_first20", "recorded_income_turn15", "recorded_income_turn20",
        )
    }
    result["disruption"] = {
        key: sum(side["disruption"][key] for _, side in rows)
        for key in rows[0][1]["disruption"]
    }
    return result


def build_report(root):
    records = [game_record(root, cohort, game_id)
        for cohort, ids in (("top", TOP_IDS), ("own", OWN_IDS), ("boss", BOSS_IDS))
        for game_id in ids]
    groups = ("top-arena", "own-arena", "own-opponents", "v07-boss", "boss")
    names = sorted({side["name"] for record in records if record["cohort"] == "top" for side in record["sides"]})
    return {
        "schema": 1, "study": "arena-opening-survey-v1", "date": "2026-09-18",
        "method": {
            "turn_numbers": "One-based: first1 is the first simultaneous action/scoring turn.",
            "attempted": "Each explicit PLACE_TRACKS occurrence in recorded stdout, including neutral collisions; repeated coordinates would count repeatedly.",
            "accepted_owned": "Recorded type-1 placements with owner equal to player 0/1; owner-2 neutral placements excluded. This is an event count, not surviving end-of-window track stock.",
            "protected": "The initial map region contains a town. Core GameState.regionHasTown and Rules.expand prohibit disruption there.",
            "town_adjacent": "Manhattan distance exactly one to a recorded town; distinct towns are the union across this side's opening cells.",
            "empty_disruption": "Recorded type-2 event targets a region with no tracks in the observed event occupancy after prior placements and ink events, including this turn's prior placement events. Neutral tracks count as tracks.",
            "points": "Sums of recorded type-3 point events, not a local scoring implementation or counterfactual.",
            "selection": "Fixed 23 newly fetched leader games plus 5 own arena games from build/cg-fetch-v07-study.log; exactly 20 highest-ID canonical boss games available at selection. Fixed IDs are embedded in the reproducer.",
            "version_attribution": "Boss cohort attributed to v07 by the user. Own latest arena agent 6747874 is identified by metadata; its uploaded source hash is not verified here.",
        },
        "limitations": [
            "Descriptive observational samples; no strength test or causal attribution.",
            "Top cohort contains both sides of 23 games, with repeated players and pairings (RamzaBeoulve 17 sides); 46 sides are not 46 independent games.",
            "Own, boss and top cohorts have different maps, opponents and game durations; early income cannot rank their strengths.",
            "Attempted cells may collide and become neutral. Accepted-owned event counts separately expose that difference.",
            "Protected track ownership does not guarantee the region remains connected or that its tracks earn points.",
            "Empty-region sabotage does not prove territorial-denial intent; no counterfactual benefit was measured.",
            "This parser does not run engine fidelity validation; use the parent study's cgValidate/cgRender results separately.",
        ],
        "groups": {group: aggregate(records, lambda side, group=group: side["group"] == group) for group in groups},
        "top_players": {name: aggregate(records, lambda side, name=name: side["group"] == "top-arena" and side["name"] == name) for name in names},
        "games": records,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path(__file__).resolve().parents[2])
    parser.add_argument("--out", type=pathlib.Path, help="Write evidence; omit to print aggregate summaries only")
    parser.add_argument("--check", type=pathlib.Path, help="Require exact JSON equality with previously recorded evidence")
    args = parser.parse_args()
    result = build_report(args.root)
    if args.check:
        assert result == json.loads(args.check.read_text(encoding="utf-8")), "Survey or raw inputs changed"
        print("Exact evidence reproduction passed: 48 games, raw hashes, all individual sides and aggregates.")
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
        print(f"Wrote {args.out}")
    if not args.out and not args.check:
        print(json.dumps(result["groups"], indent=2))


if __name__ == "__main__":
    main()
