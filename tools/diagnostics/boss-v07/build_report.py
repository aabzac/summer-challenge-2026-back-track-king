"""Build a compact summary and replay gallery from the fixed, core-validated boss report."""
from pathlib import Path
import json
from collections import Counter

ROOT = Path(__file__).resolve().parents[3]
report = json.loads((ROOT / 'docs/evidence/boss-v07-latest20.json').read_text())
entries = report['games']
summary = {'schemaVersion': 1, 'method': report['method'], 'games': [], 'cohorts': {}}
for outcome in ('win', 'loss', 'draw'):
    cohort = [e for e in entries if e['game']['outcome'] == outcome]
    if not cohort:
        continue
    data = {'count': len(cohort), 'turns': sum(e['game']['turns'] for e in cohort)}
    for key in ('scores', 'paint', 'cells', 'placementTurns', 'disruptTurns', 'acceptedCellsNeverEarned', 'skippedActions'):
        data[key] = [sum(e['game'][key][e['game']['me'] if p == 0 else 1-e['game']['me']] for e in cohort) for p in (0, 1)]
    data['first20Paint'] = [sum(sum(t['paint'][p] for t in e['game']['trace'][:20]) for e in cohort) for p in (0, 1)]
    data['bossMessages'] = dict(sum((Counter(e['defense']['messages']) for e in cohort), Counter()))
    summary['cohorts'][outcome] = data
for e in entries:
    g = e['game']; m = g['me']; b = 1-m
    summary['games'].append({
        'gameId': g['gameId'], 'rawSha256': e['sha256'], 'version': e['userReportedVersion'],
        'me': m, 'boss': b, 'bossName': e['boss']['name'], 'outcome': g['outcome'],
        'scoresMeBoss': [g['scores'][m], g['scores'][b]], 'turns': g['turns'],
        'width': g['width'], 'height': g['height'], 'towns': g['towns'],
        'firstIncomeMeBoss': [g['firstIncome'][m], g['firstIncome'][b]],
        'paintMeBoss': [g['paint'][m], g['paint'][b]],
        'unusedCellsMeBoss': [g['acceptedCellsNeverEarned'][m], g['acceptedCellsNeverEarned'][b]],
        'failures': g['failures'], 'rendered': f'/replays/rendered/cg/bot/{g["gameId"]}.json',
    })
summary['examples'] = [
    {'gameId': 904554568, 'turn': 26, 'label': 'Три клетки меняют три маршрута', 'detail': 'Каждая клетка и любая пара дают 0. Все три вместе: доход 132:126 → 64:186. +128 к перевесу босса за ход. Без одновременного хода и диверсий.'},
    {'gameId': 904555424, 'turn': 28, 'label': 'Две клетки работают только вместе', 'detail': 'По отдельности 0; вместе: доход 169:44 → 125:78, +78 к перевесу босса. Наша победа в этой игре: локальная находка не равна победной стратегии.'},
    {'gameId': 904554907, 'turn': 21, 'label': 'Незаконченные проекты', 'detail': 'За игру 50 из 113 наших принятых клеток ни разу не принесли очков; у босса 8 из 142. В t21 текущий доход 0:134.'},
    {'gameId': 904554072, 'turn': 37, 'label': 'Диверсия постоянно откладывается', 'detail': 'Доход 18:107; регион 29 можно уничтожить за 3 хода и получить 1:1 без завершения игры. v07 с полным поиском всё равно выбирает WAIT из-за моделируемого продолжения.'},
]
summary_path = ROOT / 'docs/evidence/boss-v07-summary.json'
if summary_path.exists():
    if json.loads(summary_path.read_text(encoding='utf-8')) != summary:
        raise SystemExit(f'Preserve changed existing summary: {summary_path}')
else:
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')

page = r'''<!doctype html>
<html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>v07 · последние 20 игр с Boss 4</title>
<style>
:root{color-scheme:dark;font-family:Inter,system-ui,sans-serif;background:#10151c;color:#e9eff6}*{box-sizing:border-box}body{margin:0}header{padding:24px 28px 16px;border-bottom:1px solid #2d3641}h1{font-size:24px;margin:0 0 8px}p{color:#a7b8cb;margin:7px 0;line-height:1.5}.count{color:#7dd7bd}main{display:grid;grid-template-columns:340px minmax(0,1fr);height:calc(100vh - 155px)}aside{padding:16px;overflow:auto;border-right:1px solid #2d3641}.filters{display:flex;gap:6px;margin-bottom:14px}button{font:inherit;cursor:pointer;background:#202b38;color:#e9eff6;border:1px solid #39475a;border-radius:7px;padding:8px 12px}.filters button.active{background:#2d5673;border-color:#68b8e5}.game{display:block;width:100%;text-align:left;margin:8px 0;padding:12px}.game.selected{border-color:#7dd7bd;background:#1c3835}.row{display:flex;justify-content:space-between;gap:8px}.loss{color:#faad99}.win{color:#7dd7bd}small{display:block;color:#9cb0c8;margin-top:5px}.examples{border-top:1px solid #2d3641;margin-top:16px;padding-top:10px}.example{font-size:13px;width:100%;text-align:left;margin:5px 0;color:#c0d7e8}section{display:flex;flex-direction:column;min-width:0}#caption{padding:14px 20px;background:#17222d;min-height:72px}#caption strong{display:block;font-size:16px}#caption p{font-size:13px}iframe{border:0;width:100%;flex:1;min-height:620px}a{color:#8fd1ff}@media(max-width:850px){main{display:block;height:auto}aside{max-height:350px;border-right:0}iframe{height:750px}header{padding:18px}}
</style>
<header><h1>v07 · последние 20 игр с Boss 4</h1><p><span class="count">6 побед</span> · 14 поражений · все 20 проверены движком и отрендерены.</p><p style="font-size:12px">904553513–904555504 · версия v07 указана пользователем; хеша исходника в CG-реплеях нет. В примерах t — номер хода с нуля.</p></header>
<main><aside><div class="filters"><button data-filter="loss" class="active">Поражения</button><button data-filter="win">Победы</button><button data-filter="all">Все 20</button></div><div id="games"></div><div class="examples"><strong>Моменты для разбора</strong><div id="examples"></div></div></aside><section><div id="caption"></div><iframe id="replay" title="Визуализатор реплея"></iframe></section></main>
<script>
const data=__DATA__;let selected=904554568,filter='loss';const frame=document.getElementById('replay');
function list(){document.getElementById('games').replaceChildren(...data.games.filter(g=>filter==='all'||g.outcome===filter).map(g=>{const b=document.createElement('button');b.className='game'+(g.gameId===selected?' selected':'');b.innerHTML=`<span class="row"><span>${g.gameId}</span><strong class="${g.outcome}">${g.scoresMeBoss.join(':')}</strong></span><small>${g.outcome==='win'?'Победа':'Поражение'} · ${g.turns} ходов · ${g.width}×${g.height} · ${g.towns} городов</small>`;b.onclick=()=>openGame(g.gameId);return b;}));}
function openGame(id,example){selected=id;const g=data.games.find(g=>g.gameId===id);list();const box=document.getElementById('caption');box.replaceChildren();const title=document.createElement('strong');title.textContent=example?example.label:`${id} · ${g.scoresMeBoss.join(':')} · ${g.outcome==='win'?'победа':'поражение'}`;box.append(title);const detail=document.createElement('p');detail.textContent=example?example.detail:'Клавиши ← / → меняют ход, пробел запускает просмотр. P0 — наш бот, P1 — Boss 4.';box.append(detail);const url=`/visualizer/index.html?file=${g.rendered}`;frame.onload=()=>{if(example){let tries=0;const advance=()=>{try{frame.contentWindow.setTurn(example.turn+1);if(frame.contentDocument.getElementById('slider').value===String(example.turn+1))return;}catch(e){}if(++tries<30)setTimeout(advance,100);};advance();}};frame.src=url;}
document.querySelectorAll('[data-filter]').forEach(b=>b.onclick=()=>{filter=b.dataset.filter;document.querySelectorAll('[data-filter]').forEach(x=>x.classList.toggle('active',x===b));list();});
data.examples.forEach(e=>{const b=document.createElement('button');b.className='example';b.textContent=`${e.gameId} · t${e.turn} · ${e.label}`;b.onclick=()=>openGame(e.gameId,e);document.getElementById('examples').append(b);});
openGame(selected,data.examples[0]);
</script></html>'''
# Historical evidence keeps its recorded locations. Only the rebuilt local gallery follows
# the directory rename, so regenerating this page cannot mutate the fixed summary.
gallery_games = [{**game, 'rendered': game['rendered'].replace('/replays/rendered/cg/bot/', '/replays/rendered/cg/boss/')}
                 for game in summary['games']]
page = page.replace('__DATA__', json.dumps({'games': gallery_games, 'examples': summary['examples']}, ensure_ascii=False))
gallery = ROOT / 'replays/rendered/cg/boss-v07-20.html'
gallery.parent.mkdir(parents=True, exist_ok=True)
gallery.write_text(page, encoding='utf-8')
print(summary_path)
print(gallery)
