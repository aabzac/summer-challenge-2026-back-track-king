# Ink45 versus ink40: local strength result

Completed 2026-10-18: **490W / 468L / 42D in 1,000 games, 51.1%** for
45 ms against the identical candidate at 40 ms. The practical-v4 local
criterion requires at least 55%; result **NOT_PASSED**. The additional
5 ms did not establish a useful strength improvement in this experiment.
Keep 45 ms experimental; no default or strategy promotion follows.

## Comparison and prospective controls

The user requested an ordinary local benchmark after the 45 ms boss series.
[Protocol](v09-ink45-strength-v1.md) was fixed before games at base commit
341741f. The earlier explicit timing-gate waiver remained in force.

- A: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=45,USE_INCREMENTAL_INK_REFRESH=true`.
- B: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true`.

Both arms use incremental ink and batching, with fast path and missed-deadline
correction OFF. Full resolved parameters differ only in TURN_BUDGET_MS.
First-search allowances are 486 ms each; later allowances are 45/40 ms,
including after seat swaps. This is a direct budget comparison, distinct from
the prior ink ON/OFF experiment at 40 ms, which scored 54.0% on other maps.

The retained pre-fast-path native runtime supplies unchanged core, strategy,
Params and runner classes. The two wrapper classes MainBot and BotFactory
received only the three documented budget-validation edits to accept 45.
Before those edits, their baseline classes were reproduced byte for byte.
Zero-game diagnostics verified wrapper acceptance, artifact parameters and
actual budgets in both seats. The first helper incorrectly assumed that the
factory returned MainBot; its failed log is retained, and only that diagnostic
assumption was corrected. No strategy call or game occurred in preparation.

Exact submitted artifacts remain 91,748 characters for A (source SHA-256
`1118fc8ad63d8b58b7d1f2a5f0e50c4605314e63d370518a93d19ae1cb7953a4`)
and 91,714 for B
(`aff2dc9bf48d2f9a141012c3d47fc079bb9af9354cdfa846ef40832804db75a5`).
Engine 292/292 and ample semantic parity on 524 positions are inherited
evidence for unchanged classes, not newly run tests.

The scoped driver admits only this exact 45/40 pair in its private gate reader;
shared and historical gate tools remain unchanged. All 29 synthetic checks
pass. The initial run's five Windows Temp fixture-access errors are retained;
the correction uses workspace fixtures without changing assertions or games.

All 500 prospective paired maps are distinct and absent from 40,174 previous
played/reserved map identities. This includes all 143 registered pools and
saved CG maps. Seeds are 401018000/401118000/401218000 for 150/100/250 maps.
The freeze pins 3,368 workspace files and seven external runtime inputs.
Two workers play each map in both seats with fresh bots, once per block.

## Fixed-stage results

- Block 1: 146W / 144L / 10D in 300 games, **50.3333%**. Continue.
- Block 2: 99W / 95L / 6D in 200 games, **51.0%**; pooled 500 **50.6%**.
  Continue; neither interim can pass a candidate early.
- Block 3: 245W / 229L / 26D in 500 games, **51.6%**.
- Pooled: **490W / 468L / 42D, 51.1%, NOT_PASSED**.
- Candidate as P0: 250W / 229L / 21D, **52.1%**; as P1:
  240W / 239L / 21D, **50.1%**.

Zero game errors in either arm; all 500 maps played in both seats. No games
were omitted, replaced, retried, added after the planned sample or pooled
with historical results. The modest observed edge is below the practical
criterion and does not prove that the budgets are exactly equivalent.

Independent audit **PASS: 42,028 checks and 3,824 input pins**. It recounts
raw scores, seat assignments, errors, all stopping decisions, actual budgets,
map freshness and unchanged frozen inputs. This validates the evidence and
failed local criterion, not timing readiness or promotion.

Native mean call durations were 16.612/15.519 ms for A/B, with slow-move
counters 9/3 and reported maxima 82/80 ms. Maxima include opening calls and
integer-ms measurement; these diagnostics are not a strict 50 ms response test.

## Decision and retained qualifications

Keep selected v09, default 36 ms and experimental flags OFF. This result does
not support switching the candidate from 40 to 45 ms for strength. There are
no new boss games, process games, production edits, arena uploads or pushes.

The previously failed strict timing checks remain failed: the 45 ms two-game
panel had two timeouts (51.6577/51.0781 ms), and the full 40 ms 16-game panel
had two timeouts. The timing prerequisite was waived for this local bench;
native call durations do not measure full protocol response time. The prior
45 ms boss observation remains 14/40 with one startup failure and changed
public boss metadata, and cannot override this failed local criterion.

[Evidence summary](evidence/v09-ink45-strength-v1.json),
[archive index](evidence/v09-ink45-strength-v1-artifacts.json),
[raw evidence archive](evidence/v09-ink45-strength-v1-artifacts.zip).
