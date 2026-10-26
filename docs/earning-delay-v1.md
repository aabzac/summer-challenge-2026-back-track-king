# earning-delay-v1 — routes that earn within a turn or two (2026-10-26)

Two fields in one `// DELAY` merger block, current-only rollout experiments, defaults off; the default artifact (v12,
91,349 chars, config `d7803b42…`) is byte-identical with both off and both leave the configuration at their defaults
(`EarningDelaySubmissionTest`, `EarningDelayTest`):

- `USE_QUICK_ALWAYS` (default false): the cheapest positive completion (≤ `ROLLOUT_QUICK_MAX_PAINT` = 6 paint, a
  positive own income after completion) is kept among the root routes **every turn**, not only before the first
  income as the v06 quick-income slot does (`considerQuick` / `retainQuickRoute`). A candidate-generation change: the
  rollout still decides.
- `DELAY_POWER` (default 1.0): a proposal's value `gain × earning / turns` divides by `turns^DELAY_POWER` instead
  (turns = ceil(cost / 3) to complete), so one-turn completions rank further ahead of long hypotheses in the
  `ROLLOUT_WIDTH` slots. An ordering change: the rollout still decides among the retained routes.

## Hypothesis (from the arena-loss diagnostic, `docs/arena-losses-v12.md`)

The field's top half has two construction traits we lack: multiplicity (`corridor-routes-v1` — passed the proxy,
lost the live step, see `docs/boss-v3.md` for why the proxy cannot see it) and **earning delay: their cells earn 2.2
turns after placement, ours 4.3**; in the K1 live games the same (2.1 vs 3.4–3.6). Our proposals are valued by
`gain × earning / turns`, which already prefers quick routes, but the quick completion is only *retained* while our
income is zero, and a long route with a large gain outranks a cheap completion with a small one. Cells on long
routes wait for the whole route (and 33 of them per game are inked before they earn, `docs/corridor-routes-v1.md`).
Both arms push the construction toward cells that earn at once — tracks the boss values by income, inside the
proxy's competent zone (`docs/proxy-agreement-v1.md`), unlike the corridor concentration.

## Predeclared screen (`tools/boss_proxy/run_delay_screen.sh`)

Fresh pool seed **1011000** (first use; 1010000..1010149 was `boss-v3`'s sign check), 150 maps × 2 seats = 300 paired
games per cell, CG 486/36 ms, two workers, the user's machine; proxy spec with `USE_EXPOSURE_INCOME=0`.

- v5 step (a): `Q-vs-boss2`, `D2-vs-boss2`, `cur-vs-boss2` — the boss-like screening proxy on the same maps/seeds/seats
  as the `current` (= v12) control; **continue only at ≥ control + 3 pp** (paired per-map delta,
  `summarize_screen.py --control cur`).
- v5 step (b): `Q-vs-cur`, `D2-vs-cur` — head-to-head vs the selected version; **≥ 48 %**.
- Arena-like cross-check: `Q-vs-nv6`, `D2-vs-nv6`, `cur-vs-nv6` — neural-v6; read as the paired delta vs `cur-vs-nv6`.

Then, for an arm that passes, v5 step (c): 80 paired live boss games (≥ control + 5 pp), launched under the allow
rule.

## Results

(filled in when the screen completes — `docs/evidence/earning-delay-v1/`)
