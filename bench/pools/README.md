# Pool status

- `development-v1.json`: development maps; reusable for screens and diagnostics.
- `confirmation-v2.json`: active reserve, seeds 90000..90499, 1000 paired games. No strategy
  games have been run on it when registered (2026-09-16). Before use, pass all relevant development
  reports to `-PexcludeReports` to reject exact map-hash overlaps as documented in benchmarking.md.
- `confirmation-v1.json`: **retired as a reserve**. Its original file is kept for historical
  identity. The rollout-v1 screen inadvertently selected seeds 30000..30149 from this range.
  Do not use it as independent confirmation or relabel that screen as confirmation. See DEVLOG.
