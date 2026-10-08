"""Collect only learner trajectories against precommitted, fixed Java opponents.

Java owns opponent plans and every simultaneous core transition. No game rules
or opponent actions participate in Python policy inputs or PPO records.
"""
from __future__ import annotations

from train_spatial import Collector, evaluate_plan, gae, masked_distribution, tensor_observations, torch


def learner_observations(states):
    return [state["obs"][state["learnerSeat"]] for state in states]


class PoolCollector(Collector):
    def collect(self, model):
        nenv = len(self.states)
        tensor_steps, action_steps, logprob_steps, value_steps = [], [], [], []
        reward_steps, done_steps = [], []
        for _ in range(self.config["rollout_turns"]):
            env_ids = [state["id"] for state in self.states]
            seats = [state["learnerSeat"] for state in self.states]
            # The bridge caches each opponent's complete plan from this same
            # pre-turn state. Returned plans are intentionally never inspected.
            commitment = self.bridge.request(op="opponentPlans", envIds=env_ids)
            if ([item["envId"] for item in commitment["diagnostics"]] != env_ids
                    or [item["turn"] for item in commitment["diagnostics"]] != [state["turn"] for state in self.states]):
                raise AssertionError("Opponent plans were not committed for the current learner states")
            if any(not item["complete"] or not item["legal"] for item in commitment["diagnostics"]):
                raise AssertionError("An opponent could not complete a legal frozen plan")
            prefixes = [[] for _ in range(nenv)]
            stopped = [False] * nenv
            observations = learner_observations(self.states)
            stage_tensors, actions, logprobs = [], [], []
            for stage in range(4):
                if stage:
                    observations = self.bridge.request(op="observe", envIds=env_ids, seats=seats,
                                                       prefixes=prefixes, stages=[stage] * nenv,
                                                       stopped=stopped)["obs"]
                if self.total_turns in (0, nenv * (self.config["rollout_turns"] - 1)):
                    self.parity_observations.extend(observations)
                obs_tensor = tensor_observations(observations)
                with torch.no_grad():
                    build, ink, value = model(obs_tensor)
                    mask = obs_tensor["disruptMask" if stage == 3 else "buildMask"]
                    distribution = masked_distribution(ink if stage == 3 else build, mask)
                    action = distribution.sample()
                    logprob = distribution.log_prob(action)
                if not mask.gather(1, action[:, None]).all():
                    raise AssertionError("Sampled an illegal learner action")
                self.total_decisions += nenv
                self.legal_decisions += nenv
                if stage == 0:
                    value_steps.append(value)
                if stage < 3:
                    for i, cell in enumerate(action.tolist()):
                        if cell == 600:
                            stopped[i] = True
                        else:
                            prefixes[i].append(cell)
                stage_tensors.append(obs_tensor)
                actions.append(action)
                logprobs.append(logprob)
            tensor_steps.append({key: torch.stack([o[key] for o in stage_tensors], dim=1)
                                 .reshape(nenv * 4, *stage_tensors[0][key].shape[1:])
                                 for key in stage_tensors[0]})
            action_steps.append(torch.stack(actions, dim=1))
            logprob_steps.append(torch.stack(logprobs, dim=1).sum(1))
            learner_plans = [{"cells": prefixes[i], "disrupt": target}
                             for i, target in enumerate(actions[3].tolist())]
            new_states = self.bridge.request(op="step", envIds=env_ids, learnerPlans=learner_plans)["envs"]
            if ([state["id"] for state in new_states] != env_ids
                    or [state["learnerSeat"] for state in new_states] != seats):
                raise AssertionError("Bridge changed learner trajectory order")
            reward_steps.append(torch.tensor([
                (state["rewardGap"][seat] + self.config["terminal_bonus"] * state["terminalSign"][seat])
                / self.config["reward_scale"] for state, seat in zip(new_states, seats, strict=True)
            ], dtype=torch.float32))
            done_steps.append(torch.tensor([state["done"] for state in new_states]))
            ended = [state for state in new_states if state["done"]]
            if ended:
                self.finished.extend({"id": state["id"], "turn": state["turn"], "score": state["score"],
                                      "learnerSeat": state["learnerSeat"], "opponentSpec": state["opponentSpec"]}
                                     for state in ended)
                seeds = self.new_seeds(len(ended))
                replacements = self.bridge.request(op="reset", envIds=[s["id"] for s in ended], seeds=seeds)["envs"]
                self.maps.extend(self.map_metadata(state, seed) for state, seed in zip(replacements, seeds, strict=True))
                by_id = {state["id"]: state for state in replacements}
                new_states = [by_id.get(state["id"], state) for state in new_states]
            self.states = new_states
            self.total_turns += nenv
        with torch.no_grad():
            _, _, bootstrap = model(tensor_observations(learner_observations(self.states)))
        rewards, values, dones = map(torch.stack, (reward_steps, value_steps, done_steps))
        advantages, returns = gae(rewards, values, dones, bootstrap, self.config["gamma"], self.config["gae_lambda"])
        rollout = {"observations": {key: torch.cat([o[key] for o in tensor_steps]) for key in tensor_steps[0]},
                   "actions": torch.cat(action_steps), "logprobs": torch.stack(logprob_steps),
                   "values": values, "rewards": rewards, "dones": dones, "advantages": advantages,
                   "returns": returns, "learner_seats": [state["learnerSeat"] for state in self.states]}
        differences, value_differences = [], []
        with torch.no_grad():
            for start in range(0, len(rollout["actions"]), 32):
                observations = {key: data[start * 4:(start + 32) * 4] for key, data in rollout["observations"].items()}
                logprob, _, value = evaluate_plan(model, observations, rollout["actions"][start:start + 32])
                differences.append((logprob - rollout["logprobs"].flatten()[start:start + 32]).abs().max())
                value_differences.append((value - values.flatten()[start:start + 32]).abs().max())
        rollout["logprob_recompute_max_error"] = float(torch.stack(differences).max())
        rollout["value_recompute_max_error"] = float(torch.stack(value_differences).max())
        if rollout["logprob_recompute_max_error"] > 1e-4 or rollout["value_recompute_max_error"] > 1e-4:
            raise AssertionError("Learner joint log probability or pre-turn value changed before PPO")
        return rollout
