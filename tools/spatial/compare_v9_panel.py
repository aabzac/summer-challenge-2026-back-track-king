"""Apply the frozen late-panel comparator to V9's explicit architecture."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from train_spatial import torch
from compare_v2_panel import compare_panel
from model_v9 import SpatialV9Net


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--panel", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    target = Path(args.out)
    if target.exists():
        raise FileExistsError("Refusing to overwrite an existing parity report")
    torch.set_num_threads(2)
    saved = torch.load(args.checkpoint, weights_only=False, map_location="cpu")
    if saved["config"]["schema"] != "spatial-ppo-v9-distillation" or saved["config"]["town_context"] is not True:
        raise ValueError("This comparator requires an explicit V9 context-enabled checkpoint")
    model = SpatialV9Net()
    model.load_state_dict(saved["model"])
    report = compare_panel(model, json.loads(Path(args.panel).read_text(encoding="utf-8-sig")))
    report["architecture"] = "spatial-ppo-v9 distance-weighted town context"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(json.dumps(report))
    if not report["passed"]:
        raise SystemExit("Python/Java V9 late-panel parity failed")
