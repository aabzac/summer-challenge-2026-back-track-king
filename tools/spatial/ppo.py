"""PPO utilities with time indexed by actual simultaneous game turns."""
from __future__ import annotations

import torch
from model import evaluate_plan, take_observations


def gae(rewards, values, dones, bootstrap, gamma=0.99, lam=0.95):
    """[time, seat] tensors; terminal masks prevent crossing reset boundaries.

    A nonterminal rollout cutoff bootstraps from its next-state value, while an
    actual terminal uses zero. There is one discount per complete real turn.
    """
    advantages = torch.zeros_like(rewards)
    carry = torch.zeros_like(bootstrap)
    for t in range(rewards.shape[0] - 1, -1, -1):
        next_value = bootstrap if t == rewards.shape[0] - 1 else values[t + 1]
        alive = (~dones[t]).to(rewards.dtype)
        delta = rewards[t] + gamma * alive * next_value - values[t]
        carry = delta + gamma * lam * alive * carry
        advantages[t] = carry
    return advantages, advantages + values


def update(model, optimizer, rollout, *, epochs, minibatch, clip=0.2,
           value_coefficient=0.5, entropy_coefficient=0.01):
    n = rollout["actions"].shape[0]
    advantage = rollout["advantages"].flatten()
    advantage = (advantage - advantage.mean()) / advantage.std(unbiased=False).clamp_min(1e-8)
    old_logprob = rollout["logprobs"].flatten()
    returns = rollout["returns"].flatten()
    rows = torch.arange(n * 4).reshape(n, 4)
    totals = {"policy_loss": 0., "value_mse": 0., "entropy": 0., "approx_kl": 0.,
              "clip_fraction": 0., "gradient_norm": 0.}
    updates = 0
    for _ in range(epochs):
        for selection in torch.randperm(n).split(minibatch):
            obs = take_observations(rollout["observations"], rows[selection].reshape(-1))
            new_lp, entropy, value = evaluate_plan(model, obs, rollout["actions"][selection])
            logratio = new_lp - old_logprob[selection]
            ratio = logratio.exp()
            obj = ratio * advantage[selection]
            clipped_obj = ratio.clamp(1 - clip, 1 + clip) * advantage[selection]
            policy_loss = -torch.minimum(obj, clipped_obj).mean()
            value_mse = (value - returns[selection]).square().mean()
            loss = policy_loss + value_coefficient * value_mse - entropy_coefficient * entropy.mean()
            if not torch.isfinite(loss):
                raise FloatingPointError("Non-finite PPO loss")
            optimizer.zero_grad(set_to_none=True)
            loss.backward()
            gradient_norm = torch.nn.utils.clip_grad_norm_(model.parameters(), 0.5)
            if not torch.isfinite(gradient_norm):
                raise FloatingPointError("Non-finite PPO gradient")
            optimizer.step()
            for key, val in {"policy_loss": policy_loss, "value_mse": value_mse,
                             "entropy": entropy.mean(), "approx_kl": ((ratio - 1) - logratio).mean(),
                             "clip_fraction": ((ratio - 1).abs() > clip).float().mean(),
                             "gradient_norm": gradient_norm}.items():
                totals[key] += float(val.detach())
            updates += 1
    if not all(torch.isfinite(p).all() for p in model.parameters()):
        raise FloatingPointError("Non-finite updated model")
    return {key: val / updates for key, val in totals.items()}
