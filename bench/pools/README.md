# Pool status

- `development-v1.json`: development maps; reusable for screens and diagnostics.
- `confirmation-v4.json`: active reserve, seeds 92000..92499, 1000 paired games. No strategy
  games have been run on it when registered (2026-09-16). Before use, pass all prior development/confirmation
  reports to `-PexcludeReports` to reject exact map-hash overlaps as documented in benchmarking.md.
- `confirmation-v2.json` and `confirmation-v3.json`: consumed by the fixed rollout-v1 vs v05
  2x1000 confirmation, seeds 90000..90499 and 91000..91499. Preserve the manifests and results;
  never tune on them and present a rerun as fresh confirmation. See DEVLOG for the protocol.
- `confirmation-v1.json`: **retired as a reserve**. Its original file is kept for historical
  identity. The rollout-v1 screen inadvertently selected seeds 30000..30149 from this range.
  Do not use it as independent confirmation or relabel that screen as confirmation. See DEVLOG.

- `quick-income-v1.json` and `quick-income-style-v1.json`: consumed development pools,
  seeds 40000..40149 (300 paired games vs v06) and 41000..41099 (200 vs v05), respectively.
  The quick-income screen did not pass promotion; these maps are not fresh confirmation.

- `track-value-v1-both-screen.json` (530000..530149), `track-value-v1-self-screen.json`
  (531000..531149) and `track-value-v1-self-direct.json` (533000..533499): consumed by the
  track-value-v1 stages against v08 on 2026-09-20 (49.50%, 54.17%, 50.85%; not promoted).
  These maps are not fresh confirmation. See docs/indrill-study-v1.md.

- `leader-coverage-v1.json` (540000..540149): consumed on 2026-09-20 by the four-arm matched
  panel against the `leader-v1` reference (v08 and the three retained sabotage flags, 300 games
  each on the same maps; no effect, not a gate). Not fresh confirmation. See DEVLOG.
