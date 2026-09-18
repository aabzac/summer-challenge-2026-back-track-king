"""Build a small Russian gallery for a checked, matched boss batch; never run games/rendering.

Reads the existing batch verifier and report, then links already rendered games.
No raw replay, source, session template, or credentials are embedded in the HTML.
"""
from __future__ import annotations

import argparse
from contextlib import redirect_stdout
from html import escape
import io
from pathlib import Path
import sys
from types import SimpleNamespace

TOOLS = Path(__file__).resolve().parents[1]
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))
import boss_batch

ROOT = TOOLS.parent
DEFAULT_OUT = ROOT / 'replays/rendered/cg/chords-boss-v1.html'
FILTERS = [('all', 'Все карты'), ('candidate', 'Победил только кандидат'),
           ('control', 'Победил только контроль'), ('both', 'Победили оба'),
           ('neither', 'Никто не победил'), ('pending', 'Незавершённые пары')]
RESULTS = {'W': ('win', 'Победа'), 'L': ('loss', 'Поражение'), 'D': ('draw', 'Ничья')}


def checked_snapshot(plan_path):
    """Use one stable count while a batch may still be appending immutable results."""
    directory, plan = boss_batch.load_plan(plan_path)
    if plan['design'] != 'matched' or set(plan['builds']) != {'candidate', 'control'}:
        raise ValueError('Gallery requires a matched candidate/control plan')
    for _ in range(3):
        records = boss_batch.result_records(directory, plan)
        with redirect_stdout(io.StringIO()):
            summary = boss_batch.report(SimpleNamespace(plan=plan_path))
        if summary['completedGames'] == len(records):
            return plan, records, summary
    raise ValueError('Batch changed while reading; rerun the gallery builder')


def grouped_pairs(plan, records):
    by_slot = {record['slot']['slot']: record for record in records}
    if len(by_slot) != len(records):
        raise ValueError('Duplicate saved slot')
    groups = {}
    for slot in plan['slots']:
        pair = groups.setdefault(slot['pair'], {})
        if slot['arm'] in pair:
            raise ValueError('Duplicate arm in a planned pair')
        pair[slot['arm']] = {'slot': slot, 'record': by_slot.get(slot['slot'])}
    for pair in groups.values():
        if set(pair) != {'candidate', 'control'}:
            raise ValueError('Every planned pair must contain candidate and control')
        c, b = pair['candidate']['slot'], pair['control']['slot']
        if (c['seed'], c['seat']) != (b['seed'], b['seat']):
            raise ValueError('Matched pair has different planned seeds or seats')
    return sorted(groups.items())


def pair_category(pair):
    candidate, control = pair['candidate']['record'], pair['control']['record']
    if candidate is None or control is None:
        return 'pending'
    wins = candidate['outcome']['result'] == 'W', control['outcome']['result'] == 'W'
    return 'both' if all(wins) else 'candidate' if wins[0] else 'control' if wins[1] else 'neither'


def game_cell(item):
    record = item['record']
    if record is None:
        return '<td class="pending"><span>Ожидается</span><small>Ещё не сохранено</small></td>'
    game = record['outcome']
    style, label = RESULTS[game['result']]
    player = game['seat']
    ours, boss = game['scores'][player], game['scores'][1 - player]
    score = f'{ours:g} : {boss:g}'
    game_id = game['gameId']
    url = f'/visualizer/index.html?file=/replays/rendered/cg/boss/{game_id}.json'
    return (f'<td><a class="game" href="{url}" target="_blank" rel="noopener">'
            f'<span class="result {style}">{label}</span><strong>{escape(score)}</strong>'
            f'<small>Игра {game_id} · открыть реплей ↗</small></a></td>')


def html_gallery(plan, records, summary, rendered_root):
    pairs = grouped_pairs(plan, records)
    missing = [str(r['outcome']['gameId']) for r in records
               if not (rendered_root / f"{r['outcome']['gameId']}.json").is_file()]
    if missing:
        raise ValueError(f'Render {len(missing)} saved games before building this gallery: ' + ', '.join(missing[:12]))
    counts = {key: 0 for key, _ in FILTERS}
    rows = []
    for index, pair in pairs:
        category = pair_category(pair)
        counts[category] += 1
        slot = pair['candidate']['slot']
        record = pair['candidate']['record'] or pair['control']['record']
        map_id = record['outcome']['mapHash'][:12] if record else 'ожидается'
        rows.append(f'<tr data-category="{category}"><th scope="row">Карта {index:02d}'
                    f'<small>seed {escape(str(slot["seed"]))}</small>'
                    f'<small>P{slot["seat"]} · {escape(map_id)}</small></th>'
                    + game_cell(pair['candidate']) + game_cell(pair['control']) + '</tr>')
    counts['all'] = len(pairs)
    finished_pairs = len(pairs) - counts['pending']
    complete = summary['complete'] is True
    amended = summary.get('protocolStatus', 'original fixed') == 'amended exploratory'
    excluded = summary.get('excludedAttempts', [])
    if (summary.get('protocolStatus', 'original fixed') not in ('original fixed', 'amended exploratory')
            or not isinstance(excluded, list) or bool(excluded) != amended):
        raise ValueError('Unsupported protocol amendment report')
    attempts = summary.get('attemptedRequests')
    if attempts is not None and (type(attempts) is not int or attempts < len(records) + len(excluded)):
        raise ValueError('Invalid attempted-request count')
    progress = f'{len(records)} / {len(plan["slots"])} учтённых игр · {finished_pairs} / {len(pairs)} пар'
    if attempts is not None:
        progress += f' · попыток: {attempts}'
    status = ('Выборка завершена по изменённому протоколу' if amended else 'Выборка завершена') if complete else 'Выборка не завершена'
    status_detail = ('Все запланированные результаты сохранены и проверены.' if complete
                     else 'Это промежуточный снимок. Итогового вывода до полной выборки нет; страница не обновляется автоматически.')
    amendment_notice = ''
    if amended:
        excluded_labels = []
        for attempt in excluded:
            if (not isinstance(attempt, dict) or type(attempt.get('slot')) is not int
                    or attempt.get('requestAttempt') != 1 or attempt.get('status') != 422):
                raise ValueError('Unsupported excluded-attempt evidence')
            excluded_labels.append(f'слот {attempt["slot"]}, HTTP {attempt["status"]}')
        retry_count = summary.get('completedRetryGames')
        authorized = summary.get('authorizedRetries')
        if (type(retry_count) is not int or type(authorized) is not int
                or not 0 <= retry_count <= authorized or authorized != len(excluded) or attempts is None):
            raise ValueError('Invalid authorized-retry counts')
        amendment_notice = (f'<aside class="amendment"><strong>Протокол изменён после начала серии</strong>'
                            f'<p>Исключены запросы с неизвестным результатом: '
                            f'{"; ".join(excluded_labels)}. Для повторного запуска сохранены та же карта, сторона и сборка. '
                            f'Исходные записи об ошибках сохранены.</p><p>Исключённых попыток: {len(excluded)}. '
                            f'Разрешено повторов: {authorized}; сохранено результатов повторов: {retry_count}. '
                            f'Статистика этой серии имеет исследовательский характер.</p></aside>')
    cards = []
    for arm, label in [('candidate', 'Кандидат'), ('control', 'Контроль')]:
        stats = summary['arms'][arm]
        rate = '—' if stats['winRate'] is None else f'{stats["winRate"]:.1%}'
        build = plan['builds'][arm]
        cards.append(f'<article><span class="eyebrow">{label}</span><h2>{escape(rate)} побед</h2>'
                     f'<p>{stats["wins"]} побед · {stats["draws"]} ничьих · {stats["losses"]} поражений</p>'
                     f'<small>{escape(build["strategy"])}<br>Сборка {escape(build["buildId"][:12])}</small></article>')
    statistical_note = ''
    if complete and amended and 'exploratoryOneSidedP' in summary:
        statistical_note = (f'<p>Исследовательский односторонний точный парный тест: '
                            f'p = {summary["exploratoryOneSidedP"]:.4g}. '
                            f'Это описательный результат серии с изменённым протоколом.</p>')
    elif complete and not amended and 'oneSidedP' in summary:
        meaning = ('Есть статистическое свидетельство улучшения.'
                   if summary['bossScreen'] == 'evidence of improvement'
                   else 'Статистического свидетельства улучшения недостаточно.')
        statistical_note = f'<p>Односторонний точный парный тест: p = {summary["oneSidedP"]:.4g}. {meaning}</p>'
    issues = ''.join(f'<li>{escape(str(issue))}</li>' for issue in summary['issues'])
    if issues:
        statistical_note += f'<ul class="issues">{issues}</ul>'
    filters = ''.join(f'<button type="button" data-filter="{key}" aria-pressed="{str(key == "all").lower()}">'
                      f'{label} <span>{counts[key]}</span></button>' for key, label in FILTERS)
    return '''<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Кандидат и контроль · игры с боссом</title>
<style>
:root{color-scheme:dark;font-family:system-ui,"Segoe UI",sans-serif;color:#e8edf5;background:#10151c;font-size:15px}
*{box-sizing:border-box}body{margin:0}main{max-width:1180px;margin:auto;padding:32px 24px 60px}
h1{font-size:clamp(26px,4vw,38px);letter-spacing:-.025em;margin:9px 0 12px}h2{font-size:29px;margin:10px 0}
p{line-height:1.6;color:#b2bfce;margin:7px 0}.eyebrow{font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:.09em;color:#9db2c9}
.status{display:inline-block;background:#263e41;color:#8fe3d2;padding:6px 10px;border-radius:6px;font-size:13px}
.incomplete{background:#453921;color:#f1cf86}.progress{margin-left:10px;color:#c4d2e1;font-size:13px}
.amendment{margin-top:20px;border:1px solid #665431;border-radius:9px;padding:16px 20px;background:#2b261c}.amendment strong{color:#f1cf86}.amendment p{font-size:13px;color:#d7c9b0}
.cards{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin:25px 0 20px}article{padding:22px;background:#19222d;border:1px solid #2e3d4e;border-radius:12px}
small{display:block;font-size:12px;line-height:1.55;color:#92a6bd;overflow-wrap:anywhere}article small{margin-top:14px}
.filters{display:flex;flex-wrap:wrap;gap:7px;margin:26px 0 12px}button{font:inherit;font-size:13px;background:#1a2532;color:#c5d3e3;border:1px solid #35475b;border-radius:7px;padding:9px 12px;cursor:pointer}
button[aria-pressed=true]{background:#294964;border-color:#7bbef0;color:#fff}button span{opacity:.65;margin-left:4px}button:focus-visible,a:focus-visible{outline:3px solid #91d9ff;outline-offset:3px}
.table-wrap{overflow-x:auto;border:1px solid #304053;border-radius:11px}table{border-collapse:collapse;width:100%;min-width:660px;text-align:left}
thead{background:#1a2634}thead th{font-size:13px;color:#aebfd3;padding:15px 18px}tbody th{width:26%;font-size:14px;font-weight:600;padding:18px}tbody th small{font-weight:400;margin-top:3px}
tbody td{width:37%;padding:8px 12px}tbody tr+tr{border-top:1px solid #293646}tbody tr:hover{background:#17212c}tr[hidden]{display:none}
.game{display:block;text-decoration:none;color:inherit;border-radius:7px;padding:10px}.game:hover{background:#223246}.game strong{font-size:19px;font-variant-numeric:tabular-nums;font-weight:600;margin-left:14px}.game small{margin-top:6px}
.result{display:inline-block;min-width:81px;font-size:13px}.win{color:#8ee1b5}.loss{color:#f3a897}.draw{color:#d8c293}.pending{color:#9ba8b8}.pending small{margin-top:5px}.issues{color:#f3bca4;font-size:13px}
.caption{font-size:13px;margin:10px 0 0}footer{border-top:1px solid #293646;margin-top:28px;padding-top:14px;font-size:12px;color:#91a4ba}
@media(max-width:640px){main{padding:22px 12px}.cards{gap:10px}article{padding:15px}.progress{display:block;margin:10px 0 0}h2{font-size:24px}}
</style></head><body><main>
''' + f'''<header><span class="eyebrow">Back Track King · парные игры с боссом</span>
<h1>Кандидат против контроля</h1><span class="status{'' if complete and not amended else ' incomplete'}">{status}</span>
<span class="progress">{progress}</span><p>{status_detail}</p></header>
{amendment_notice}
<section class="cards" aria-label="Результаты стратегий">{''.join(cards)}</section>
{statistical_note}
<p class="caption">В каждой строке одна карта и одинаковая сторона. Счёт указан как «наш бот : босс».
В фильтрах ничья считается отсутствием победы. Нажмите на результат, чтобы открыть визуализатор.</p>
<nav class="filters" aria-label="Фильтр результатов">{filters}</nav>
<p id="shown" class="caption" aria-live="polite">Показано карт: {len(pairs)}</p>
<div class="table-wrap"><table><thead><tr><th scope="col">Карта · сторона</th><th scope="col">Кандидат</th>
<th scope="col">Контроль</th></tr></thead><tbody>{''.join(rows)}</tbody></table></div>
<footer>Результаты и привязка к сборкам повторно проверены средствами batch runner.
Маркер реплея сопоставлен с локально сохранённым артефактом; это самоотчёт исполняемого бота.
Эта проверка с боссом не изменяет статус стратегии. План {escape(plan['planId'][:12])}.</footer>
''' + '''</main><script>
const rows=[...document.querySelectorAll('tbody tr')];
document.querySelectorAll('[data-filter]').forEach(button=>button.addEventListener('click',()=>{
  document.querySelectorAll('[data-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));
  let shown=0;rows.forEach(row=>{row.hidden=button.dataset.filter!=='all'&&row.dataset.category!==button.dataset.filter;if(!row.hidden)shown++;});
  document.getElementById('shown').textContent='Показано карт: '+shown;
}));
</script></body></html>
'''


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', required=True, type=Path)
    parser.add_argument('--out', type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)
    try:
        plan, records, summary = checked_snapshot(args.plan.resolve())
        text = html_gallery(plan, records, summary, ROOT / 'replays/rendered/cg/boss')
        out = args.out.resolve()
        if out.suffix.lower() != '.html':
            raise ValueError('Output must be an HTML file')
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(text.encode('utf-8'))
        print(f'Gallery saved: {out} ({len(records)}/{len(plan["slots"])} games; complete={summary["complete"]})')
    except (ValueError, OSError, KeyError, TypeError, AttributeError):
        parser.exit(1, 'Gallery not written: batch evidence is invalid, changed while reading, or a saved game has no render.\n')


if __name__ == '__main__':
    main()
