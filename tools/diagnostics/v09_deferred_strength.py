"""Fixed local whole-candidate comparison. prepare|freeze|strength|verify|summary|archive.
No boss, process retry, strategy edit or promotion. Shared v4 runner stays unchanged.
"""
from __future__ import annotations
import hashlib,json,os,re,subprocess,sys,time,zipfile
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
NAME='v09-deferred-strength-v1';OUT=ROOT/'build/analysis'/NAME;BENCH=ROOT/'build/bench'/NAME
PRIOR=ROOT/'build/bench'/f'{NAME}-prior/maps.json';DOC=ROOT/f'docs/{NAME}.md'
SUMMARY=ROOT/f'docs/evidence/{NAME}.json';RUNTIME=ROOT/'build/spatial-runtime.json'
SPEC='current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true';CONTROL='v09';SEED=241011000
SOURCE_SHA='3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c'
OLD=ROOT/'build/analysis/v09-deferred-forecast-reuse-v1'
OLD_INDEX=ROOT/'docs/evidence/v09-deferred-forecast-reuse-v1-artifacts.json'
OLD_ZIP=OLD_INDEX.with_suffix('.zip')
NEURAL_PRIOR=ROOT/'build/bench/neural-target-strength-v1-prior/maps.json'
NEURAL_INDEX=ROOT/'docs/evidence/neural-target-strength-v1-artifacts.json'
sys.path.insert(0,str(ROOT/'tools'))
import run_practical_v4 as practical
def now():return datetime.now(timezone.utc).isoformat()
def require(ok,msg):
 if not ok:raise ValueError(msg)
def sha(p):
 with Path(p).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def read(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
def rel(p):return Path(p).resolve().relative_to(ROOT).as_posix()
def pin(p):return {'path':rel(p),'sha256':sha(p),'bytes':Path(p).stat().st_size}
def write(p,obj):
 p.parent.mkdir(parents=True,exist_ok=True)
 with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(obj,f,indent=2,allow_nan=False);f.write('\n')
def unrelated(p):
 s=str(p).replace('\\','/').lower();return 'pduhard' in s or '/btk/tools/ppo/' in s or 'ppo-probe' in s
def maps(obj):
 values=obj.get('gamesPerMap');require(isinstance(values,dict) and values,'Empty map index')
 require(all(re.fullmatch('[0-9a-f]{64}',k) and type(v)is int and v>0 for k,v in values.items()),'Invalid map identity')
 return dict.fromkeys(values,1)
def logged(label,argv):
 write(OUT/f'{label}-command.json',{'startedAt':now(),'argv':argv,'cwd':str(ROOT)})
 start=time.monotonic();log=OUT/f'{label}.log'
 with log.open('xb') as f:
  try:code=subprocess.run(argv,cwd=ROOT,stdout=f,stderr=subprocess.STDOUT).returncode
  except BaseException as e:
   write(OUT/f'{label}-exit.json',{'exitCode':None,'error':repr(e),'finishedAt':now()});raise
 write(OUT/f'{label}-exit.json',{'exitCode':code,'elapsedSeconds':time.monotonic()-start,'finishedAt':now(),'log':pin(log)})
 require(code==0,f'{label} failed; retain {log}')
def prepare():
 require(DOC.is_file() and not BENCH.exists() and not PRIOR.exists(),'Protocol required; existing experiment cannot be restarted')
 require(not (OUT/'prepared.json').exists(),'Already prepared')
 runtime=read(RUNTIME);cfg={};OUT.mkdir(parents=True,exist_ok=True)
 intervals=[(SEED+off,SEED+off+n-1) for _,_,n,off in practical.BLOCKS]
 own={practical.pool_path(NAME,b) for b,*_ in practical.BLOCKS}
 for p in (ROOT/'bench/pools').glob('*.json'):
  if p in own:continue
  d=read(p)
  if d.get('generator')=='cg-sha1prng' and type(d.get('seedStart'))is int and type(d.get('pairs'))is int:
   lo=d['seedStart'];hi=lo+d['pairs']-1
   require(all(hi<a or lo>b for a,b in intervals),f'Prior seed overlap: {p}')
 pools=[practical.ensure_pool(NAME,b,n,SEED+off) for b,_,n,off in practical.BLOCKS]
 for label,spec in [('candidate',SPEC),('control',CONTROL)]:
  logged('params-'+label,[runtime['java'],'-cp',runtime['classpath'],'btk.tools.BotCatalogTool',spec])
  lines=(OUT/f'params-{label}.log').read_text(encoding='utf-8').splitlines()
  params=dict(m.groups() for line in lines if (m:=re.fullmatch(r'  ([A-Z][A-Z0-9_]*)=(.*)',line)))
  require(params and 'NOISE'in params,'Missing full configuration');cfg[label]={'spec':spec,'params':params}
 require(cfg['candidate']['params']['NOISE']=='0.0' and cfg['candidate']['params']['USE_ROLLOUT_MISSED_DEADLINE']=='true','Candidate mismatch')
 require(cfg['control']['params']['NOISE']=='0.1' and cfg['control']['params'].get('USE_ROLLOUT_MISSED_DEADLINE','false')=='false','Control mismatch')
 old_entries={x['path']:x for x in read(NEURAL_INDEX)['files']}
 inputs=[NEURAL_PRIOR,ROOT/'build/analysis/neural-target-strength-v1/saved-cg-maps.json']
 for p in inputs:
  require(old_entries[rel(p)]['sha256']==sha(p),'Prior index differs from retained archive')
  require(read(ROOT/'build/analysis/neural-target-strength-v1/freeze.json')['files'][rel(p)]==sha(p),'Prior index differs from freeze')
 prior_file=practical.build_exclusion_index(OUT,'prior');prior=read(prior_file)
 union=maps(prior)|maps(read(NEURAL_PRIOR))|maps(read(inputs[1]))
 audit_file=ROOT/'docs/evidence/neural-v7-control-boss.json';audit=read(audit_file);replaypins=[]
 for row in audit['rows']:
  p=ROOT/row['canonical']['path'];require(sha(p)==row['canonical']['sha256'],'Canonical source replay changed')
  require(re.fullmatch('[0-9a-f]{64}',row['coreMapHash']),'Invalid core map identity')
  union[row['coreMapHash']]=1;replaypins.append(pin(p))
 for item in read(inputs[1])['replays']:
  p=Path(item['path']);require(sha(p)==item['sha256'],'Saved CG replay changed');replaypins.append(pin(p))
 write(PRIOR,{'purpose':'Prior local benchmarks plus verified saved CG/development index and40 canonical v09 sources',
  'hashScheme':'sha256-utf8-Protocol.initBlock(initialState,0)','gamesPerMap':union,
  'inputs':[pin(prior_file),pin(NEURAL_INDEX),*[pin(p) for p in inputs],pin(audit_file)]})
 write(OUT/'prepared.json',{'recordedAt':now(),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
  'candidate':SPEC,'control':CONTROL,'configs':cfg,'pools':[pin(p) for p in pools],'protocol':pin(DOC),
  'priorUnion':pin(PRIOR),'priorReports':{name:sha(ROOT/name) for name in prior['sourceFiles']},
  'priorInputs':[pin(NEURAL_INDEX),*[pin(p) for p in inputs],pin(audit_file)],'replayPins':replaypins,
  'excludedMaps':len(union),'wholeCandidateNoiseConfounded':True,'inheritedProcessReadiness':'FAIL','bossGames':0,'promotion':False})
 print(f'Prepared3 pools; {len(union)} prior maps; exact existing enabled candidate versus v09',flush=True)
def inventory():
 patterns=['src/main/java/**/*.java','src/main/resources/**/*','build/classes/java/main/**/*.class',
  'build/resources/main/**/*','tools/practical_gate*.py','tools/boss*.py','tools/run_practical_v4.py',
  'build.gradle.kts','settings.gradle.kts','gradlew','gradlew.bat','gradle/wrapper/*']
 return {p.resolve() for pattern in patterns for p in ROOT.glob(pattern) if p.is_file() and not unrelated(p)}
def freeze():
 prepared=read(OUT/'prepared.json');require(not BENCH.exists(),'Freeze precedes games')
 require(sha(DOC)==prepared['protocol']['sha256'],'Prospective protocol changed after preparation')
 artifact=OUT/'artifact/Player.java';manifest=OUT/'artifact/manifest.json'
 require(sha(artifact)==SOURCE_SHA==sha(OLD/'artifact-on/Player.java'),'Exact candidate source changed')
 require(read(manifest)['params']==prepared['configs']['candidate']['params'],'Artifact/local params differ')
 require(read(manifest)['strategy']==SPEC,'Artifact spec differs')
 actual={p.name:sha(p) for p in (OUT/'artifact/classes').glob('*.class')}
 expected={p.name:sha(p) for p in (OLD/'artifact-on/classes').glob('*.class')}
 require(actual and actual==expected,'Fresh executable differs from exact prior artifact')
 require((OUT/'preflight.log').is_file() and '292/292 replays reproduced exactly'in (OUT/'preflight.log').read_text(encoding='utf-8',errors='replace'),'Root engine preflight required')
 saved=OUT/'protocol-before-results.md'
 with saved.open('xb') as f:f.write(DOC.read_bytes())
 files=inventory()|{Path(__file__).resolve(),RUNTIME,PRIOR,OLD_INDEX,OLD/'artifact-on/Player.java',OLD/'artifact-on/manifest.json'}
 files.update(ROOT/n for n in prepared['priorReports'])
 files.update(ROOT/item['path'] for key in ['pools','priorInputs','replayPins'] for item in prepared[key])
 files.update(p.resolve() for p in OUT.rglob('*') if p.is_file() and '__pycache__'not in p.parts)
 rt=read(RUNTIME);java=Path(rt['java']);jh=java.parent.parent
 external={java,Path(sys.executable)}|{Path(x) for x in rt['classpath'].split(os.pathsep) if x.endswith('.jar')}
 external.update(p for p in [jh/'release',jh/'lib/modules',jh/'bin/server/jvm.dll'] if p.is_file())
 write(OUT/'freeze.json',{'recordedAt':now(),'sourceCommit':prepared['sourceCommit'],'files':{rel(p):sha(p) for p in sorted(files)},
  'inventory':sorted(rel(p) for p in inventory()),'runtime':{str(p):sha(p) for p in sorted(external)},'configs':prepared['configs'],
  'artifactSourceSha256':SOURCE_SHA,'artifactClassPins':actual,'inheritedProcessReadiness':'FAIL','newProcessCheck':False})
 print(f'Frozen {len(files)} inputs and {len(external)} runtimes',flush=True)
def verify(allow_results=False):
 frozen=read(OUT/'freeze.json');require(sorted(rel(p) for p in inventory())==frozen['inventory'],'Source/class inventory drift')
 if allow_results:require(DOC.read_bytes().startswith((OUT/'protocol-before-results.md').read_bytes()),'Final report must retain prospective protocol verbatim')
 for name,digest in frozen['files'].items():
  p=ROOT/name
  require(p.is_file() and sha(p)==digest,f'Frozen input changed: {name}')
 for name,digest in frozen['runtime'].items():require(sha(Path(name))==digest,f'Runtime drift: {name}')
 return {'verifiedAt':now(),'unchangedFiles':len(frozen['files']),'unchangedRuntimes':len(frozen['runtime'])}
def checked(report,block):
 data=report['data'];cfg=read(OUT/'prepared.json')['configs'];i=[x[0] for x in practical.BLOCKS].index(block)
 _,games,n,off=practical.BLOCKS[i]
 require((data['a'],data['b'],data['threads'],data['baseSeed'])==(SPEC,CONTROL,2,SEED+off),'Block settings drift')
 require(data['paramsA']==cfg['candidate']['params'] and data['paramsB']==cfg['control']['params'],'Full config drift')
 require((data['timingProfile'],data['firstSearchBudgetMs'],data['turnSearchBudgetMs'])==('CG',486,36),'Timing profile drift')
 pool=practical.pool_path(NAME,block);require(data['poolSha256']==sha(pool) and data['poolId']==NAME+'-'+block,'Pool drift')
 require([x['seed'] for x in data['pairs']]==list(range(SEED+off,SEED+off+n)),'Unexpected seed order')
 ex=BENCH/f'{block}-excluded.json';actual={k.replace('\\','/'):v for k,v in data['excludedReports'].items()}
 require(actual=={rel(ex):sha(ex)},'Excluded report identity drift')
 used=set(maps(read(PRIOR)))
 for b,*_ in practical.BLOCKS[:i]:used.update(read(BENCH/f'{b}.json')['gamesPerMap'])
 require(used<=set(maps(read(ex))) and not used.intersection(report['maps']),'Map exposure/disjointness failure')
 return {'block':block,'games':games,'freshPairedMaps':n,'report':pin(BENCH/f'{block}.json'),'excluded':pin(ex),'fullParametersMatched':True,'freshnessMatched':True}
def strength_child():
 require(not BENCH.exists(),'No retry/resume: existing strength outputs retained')
 write(OUT/'before-strength-verification.json',verify());original=practical.run_block
 os.environ['JAVA_HOME']=str(Path(read(RUNTIME)['java']).parent.parent)
 def guarded(*args):
  verify()
  try:r=original(*args)
  except BaseException as e:
   write(OUT/f'{args[3]}-failure-verification.json',{'error':repr(e),'verification':verify()});raise
  audit=checked(r,args[3]);audit['verification']=verify();write(OUT/f'{args[3]}-verification.json',audit);return r
 practical.run_block=guarded
 sys.argv=[str(ROOT/'tools/run_practical_v4.py'),'--spec',SPEC,'--control',CONTROL,'--name',NAME,'--seed',str(SEED)]
 practical.main();write(OUT/'after-strength-verification.json',verify())
 print('Local stage complete. PROCESS_FAIL unchanged; no boss/promotion/upload.',flush=True)
def summary():
 verification=verify(allow_results=True);reports=[];blocks=[]
 for block,games,*_ in practical.BLOCKS:
  p=BENCH/f'{block}.json'
  if not p.exists():break
  r=practical.read_report(p,games);reports.append(r);blocks.append(checked(r,block))
  require(read(BENCH/f'{block}-decision.json')==practical.gate.assess(*reports),'Decision drift')
 require(reports and not any((BENCH/f'{b}.json').exists() for b,*_ in practical.BLOCKS[len(reports):]),'Noncontiguous blocks')
 decision=practical.gate.assess(*reports);stage=decision['directStage']
 require(stage in {'STOP_FUTILITY_300','STOP_FUTILITY_500','NOT_PASSED','PASS_1000'},'Local sequence incomplete')
 require(read(OUT/'strength-exit.json')['exitCode']==0 and (OUT/'after-strength-verification.json').is_file(),'Strength execution incomplete')
 data=[r['data'] for r in reports]
 write(SUMMARY,{'schema':NAME,'recordedAt':now(),'candidate':SPEC,'control':CONTROL,'selectedBot':'v09','promotion':False,
  'wholeCandidateComparison':True,'noiseCandidate':0.0,'noiseControl':0.1,'isolatesDeadlineFlagEffect':False,
  'localDecision':decision,'combinedReadiness':'LOCAL_PASS_BUT_PROCESS_FAILED' if stage=='PASS_1000' else 'LOCAL_NOT_PASSED_AND_PROCESS_FAILED',
  'games':sum(d['games'] for d in data),'freshPairedMaps':sum(len(r['maps']) for r in reports),
  'wins':sum(d['winsA'] for d in data),'losses':sum(d['winsB'] for d in data),'draws':sum(d['draws'] for d in data),
  'errors':[sum(d['errorsA'] for d in data),sum(d['errorsB'] for d in data)],
  'nativeMaxMs':[max(d['maxMsA'] for d in data),max(d['maxMsB'] for d in data)],
  'nativeSlowCalls':[sum(d['slowA'] for d in data),sum(d['slowB'] for d in data)],
  'blocks':blocks,'freeze':pin(OUT/'freeze.json'),'prepared':pin(OUT/'prepared.json'),'verification':verification,
  'sourceSha256':SOURCE_SHA,'inheritedProcessReadiness':'FAIL','strictFailure':{'map':904148181,'seat':0,'turn':33,'responseMs':56.0151},
  'newProcessCheck':False,'bossGames':0,'upload':False,
  'caveats':['Candidate includes NOISE=0 plus missed-deadline correction and existing canonical reuse; no new batching fix.',
   'Nominal in-process486/36ms with2workers, no protocol or500ms process warmup; not a50ms readiness check.',
   'Practical-v4 local result cannot clear inherited process failure or replace required boss evidence. No promotion.',
   'All blocks/maps/errors/outliers retained; no outcome-guided tuning, reruns or extensions.']})
 print(json.dumps(pin(SUMMARY)))
def archive():
 verify(allow_results=True);require(SUMMARY.is_file(),'Final summary required');old=read(OLD_INDEX)
 require(sha(OLD_ZIP)==old['archiveSha256'],'Prior baseline archive changed')
 files={p for tree in [OUT,BENCH,PRIOR.parent] for p in tree.rglob('*') if p.is_file() and '__pycache__'not in p.parts}
 files.update({DOC,SUMMARY,Path(__file__).resolve(),RUNTIME,OLD_INDEX,NEURAL_INDEX})
 files.update(practical.pool_path(NAME,b) for b,*_ in practical.BLOCKS)
 inherited={};prior_evidence={};external=read(OUT/'freeze.json')['runtime']
 for name,digest in read(OUT/'freeze.json')['files'].items():
  p=ROOT/name
  if p.suffix.lower()=='.zip':continue
  if name.startswith('build/bench/') and not p.is_relative_to(BENCH) and not p.is_relative_to(PRIOR.parent):
   prior_evidence[name]={'sha256':digest,'bytes':p.stat().st_size};continue
  if name in old['files'] and old['files'][name]['sha256']==digest:inherited[name]=old['files'][name]
  else:files.add(p)
 for name in ['docs/BOTS.md','docs/PLAN.md','docs/DEVLOG.md']:files.add(ROOT/name)
 require(all(not unrelated(p) for p in files),'Unrelated payload would be archived')
 archive=SUMMARY.with_name(NAME+'-artifacts.zip');entries={}
 with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for p in sorted(files):z.write(p,rel(p));entries[rel(p)]={'sha256':sha(p),'bytes':p.stat().st_size}
 with zipfile.ZipFile(archive) as z:
  require(z.testzip()is None and len(z.namelist())==len(set(z.namelist())),'ZIP integrity failure')
  for name,d in entries.items():data=z.read(name);require(len(data)==d['bytes'] and hashlib.sha256(data).hexdigest()==d['sha256'],'ZIP member mismatch')
 write(archive.with_suffix('.json'),{'archive':pin(archive),'archiveFiles':len(entries),'files':entries,
  'priorArchiveReferences':[pin(OLD_ZIP)],'priorEvidenceReferences':prior_evidence,'priorNeuralArchiveIndex':pin(NEURAL_INDEX),
  'inheritedBaselineEntries':inherited,'externalRuntimePins':external,
  'scope':'Fixed local strength outputs, fresh exact artifact, freeze/config/pool evidence and relevant new sources; unchanged baseline in pinned prior archive. No unrelated pduhard/ppo payload.'})
 print(json.dumps(pin(archive)))
def main():
 require(len(sys.argv)>=2,'Mode required');mode=sys.argv[1]
 if mode=='prepare':prepare()
 elif mode=='freeze':freeze()
 elif mode=='verify':print(json.dumps(verify()))
 elif mode=='strength':logged('strength',[sys.executable,str(Path(__file__).resolve()),'_strength'])
 elif mode=='_strength':strength_child()
 elif mode=='summary':summary()
 elif mode=='archive':archive()
 else:raise SystemExit('Unknown mode')
if __name__=='__main__':main()
