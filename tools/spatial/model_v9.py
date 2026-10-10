"""V9: frozen spatial architecture plus unlearned distance-weighted town context.

The state dictionary and 3,439-float export order are inherited unchanged. Java
still supplies the observations and legality; this module contains no game rules.
"""
from __future__ import annotations

from train_spatial import torch
from model import H, S, TOWNS, W, SpatialNet


def town_kernel(town_cells, valid):
    """Nx12x600 float32 coefficients, with absent towns/padded cells excluded."""
    present = town_cells >= 0
    count = present.sum(1).clamp_min(1).to(torch.float32)
    cells = torch.arange(S, device=town_cells.device)
    distance = ((cells[None, None, :] % W - town_cells[:, :, None] % W).abs()
                + (cells[None, None, :] // W - town_cells[:, :, None] // W).abs())
    denominator = count[:, None, None] * (1 + distance).to(torch.float32)
    return (1. / denominator) * present[:, :, None] * valid.reshape(-1, 1, S)


def ordered_town_context(embeddings, kernel):
    """Accumulate float32 in town-index order, then add once to the trunk."""
    context = torch.zeros((embeddings.shape[0], embeddings.shape[1], S),
                          dtype=embeddings.dtype, device=embeddings.device)
    for town in range(TOWNS):
        context = context + embeddings[:, :, town:town + 1] * kernel[:, town:town + 1, :]
    return context


class SpatialV9Net(SpatialNet):
    def __init__(self, town_context=True):
        super().__init__()
        self.town_context = bool(town_context)

    def forward(self, obs):
        if not self.town_context:
            return super().forward(obs)
        # Preserve every existing operation/order around the one added context.
        n = obs["planes"].shape[0]
        valid = (obs["planes"][:, 0:1] > 0).to(torch.float32)
        denom = valid.sum((2, 3)).clamp_min(1)
        x = self.stem(obs["planes"]).relu().reshape(n, 8, S)
        town_valid = (obs["townCells"] >= 0).unsqueeze(1)
        town_features = self.town(obs["towns"]).transpose(1, 2) * town_valid
        town_indices = obs["townCells"].clamp_min(0).unsqueeze(1).expand(-1, 8, -1)
        x = x.scatter_add(2, town_indices, town_features)
        x = x + ordered_town_context(town_features, town_kernel(obs["townCells"], valid))
        x = x.reshape(n, 8, H, W)
        x = (x + self.res2(self.res1(x).relu())).relu()
        global_features = (x * valid).sum((2, 3)) / denom
        x = (x + self.global_layer(global_features)[:, :, None, None]).relu()
        z = torch.cat(((x * valid).sum((2, 3)) / denom, obs["scalars"]), dim=1)
        cells = x.flatten(2).transpose(1, 2)
        build = torch.cat((self.build(cells).squeeze(-1), self.stop(z)), dim=1)
        cell_ink = self.ink(cells).squeeze(-1)
        region_valid = (obs["regionIds"] >= 0).to(torch.float32) * valid.flatten(1)
        region_indices = obs["regionIds"].clamp_min(0)
        sums = torch.zeros_like(cell_ink).scatter_add(1, region_indices, cell_ink * region_valid)
        counts = torch.zeros_like(cell_ink).scatter_add(1, region_indices, region_valid)
        ink = torch.cat((sums / counts.clamp_min(1), self.noink(z)), dim=1)
        return build, ink, self.value(z).squeeze(-1)
