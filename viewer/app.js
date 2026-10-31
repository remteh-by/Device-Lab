import {parseReport,comparison,measurements,kindName,statusName,checkName,stable,MAX_REPORT_BYTES} from './reports.js';
const $=id=>document.getElementById(id),selected={before:null,after:null};
const LEGACY='remteh-device-lab-history-v1',HISTORY='remteh-device-lab-history-v2',WORKSPACE='remteh-device-lab-workspace-v1',MAX_HISTORY=3*1024*1024;
let history=[],storedRaw=null,storageBlocked=false;
function node(tag,text,className){const el=document.createElement(tag);if(text!==undefined)el.textContent=String(text);if(className)el.className=className;return el;}
function message(text){$('status').textContent=text;}
function format(value){return value===null||value===undefined?'Не измерено':typeof value==='number'?value.toLocaleString('ru-RU',{maximumFractionDigits:4}):String(value);}
function table(target,rows){const t=document.createElement('table');for(const [label,value]of rows){const row=document.createElement('tr'),th=node('th',label),td=node('td',format(value));th.scope='row';row.append(th,td);t.append(row);}target.append(t);}
function documentPanel(target,report){
 target.replaceChildren();
 if(!report){const empty=node('div',undefined,'empty');empty.append(node('strong','Пока без отчёта'),node('span','Откройте JSON из тестера или выберите сессию из истории.'));target.append(empty);return;}
 target.append(node('span',kindName(report.kind),'kind-badge'),node('h4',report.device),node('p',report.application+' '+report.version+' · '+(report.os||'ОС не указана'),'metadata'),node('p','Источник: '+report.source+' · сессия '+report.session_id,'metadata'));
 table(target,measurements(report));
 if(report.kind==='controller')target.append(node('p','Покой: число образцов N, среднее и стандартное отклонение значений API. Фильтр отображения: '+report.display_dead_zone+'.'));
 if(report.kind==='mouse')target.append(node('p','Условия: '+JSON.stringify(report.conditions)));
 target.append(node('h4','Комментарий'),node('p',report.comment||'Не указан'),node('h4','Ограничения'),node('p',report.limitations||'Наблюдения API. Автоматического диагноза нет.'));
 const details=document.createElement('details');details.append(node('summary','Исходный JSON'),node('pre',JSON.stringify(report,null,2)));target.append(details);
}
function comparePanel(){
 const target=$('comparison');target.replaceChildren();const a=selected.before,b=selected.after;if(!a||!b)return;
 const result=comparison(a,b);target.append(node('h3',result.compatible?'Условия совпадают':'Условия отличаются'));
 target.append(node('p',result.compatible?'Сопоставьте результаты и размер выборки. Изменение показаний само по себе не доказывает исправность.':'Различаются: '+result.reasons.join(', ')+'. Прямое числовое сравнение ограничено.'));
 if(a.kind!==b.kind)return;
 let rows;
 if(a.kind==='mouse')rows=measurements(a).map(([label,value],index)=>[label,value,measurements(b)[index][1]]);
 else if(a.kind==='controller')rows=Object.keys(a.resting_axes).map(key=>[key+' · среднее в покое',a.resting_axes[key].samples?a.resting_axes[key].mean:null,b.resting_axes[key]?.samples?b.resting_axes[key].mean:null]);
 else rows=[...new Set([...Object.keys(a.checks),...Object.keys(b.checks)])].map(key=>[checkName(key),statusName(a.checks[key]?.status),statusName(b.checks[key]?.status)]);
 const t=document.createElement('table'),head=document.createElement('tr');for(const name of ['Показатель','До','После','Изменение']){const th=node('th',name);th.scope='col';head.append(th);}t.append(head);
 for(const [label,left,right]of rows){const row=document.createElement('tr');row.append(node('th',label),node('td',format(left)),node('td',format(right)),node('td',result.compatible&&typeof left==='number'&&typeof right==='number'?format(right-left):'—'));t.append(row);}target.append(t);
}
function checkpoint(){
 try{sessionStorage.setItem(WORKSPACE,JSON.stringify(selected));return true;}catch{message('Документы открыты в этой вкладке. Не удалось сохранить черновик; экспортируйте JSON перед закрытием.');return false;}
}
function refresh(){
 documentPanel($('left'),selected.before);documentPanel($('right'),selected.after);comparePanel();$('loaded-count').textContent=Object.values(selected).filter(Boolean).length+' / 2';$('print').disabled=!selected.before&&!selected.after;
 for(const key of ['before','after'])for(const action of ['save','export'])document.querySelector(`[data-${action}="${key}"]`).disabled=!selected[key];
}
function open(key,report){selected[key]=report;refresh();message('Отчёт открыт локально. Для долговременного хранения сохраните его в историю или экспортируйте JSON.');checkpoint();}
function validateHistory(value){
 if(!value||value.format!=='remteh-device-lab-history'||value.schema!==1||!Array.isArray(value.entries)||value.entries.length>20)throw new Error('Нужна история Device Lab schema 1, до 20 отчётов');
 const ids=new Set();for(const entry of value.entries){if(!entry||typeof entry.id!=='string'||entry.id.length>80||ids.has(entry.id)||typeof entry.saved_at!=='string'||!Number.isFinite(Date.parse(entry.saved_at)))throw new Error('Повреждена запись истории');ids.add(entry.id);parseReport(JSON.stringify(entry.report));}
 if(new TextEncoder().encode(JSON.stringify(value)).length>MAX_HISTORY)throw new Error('История больше 3 МБ');return value.entries;
}
function historyValue(rows){return {format:'remteh-device-lab-history',schema:1,entries:rows};}
function latestHistory(){const current=localStorage.getItem(HISTORY);return current===null?localStorage.getItem(LEGACY):current;}
async function writeHistory(change){
 if(!navigator.locks)throw new Error('Надёжная история требует Web Locks в современном браузере по HTTPS или локальному HTTP. Экспорт JSON доступен');
 return navigator.locks.request(HISTORY,{mode:'exclusive'},()=>{
  const raw=latestHistory(),fresh=raw?validateHistory(JSON.parse(raw)):[];
  const rows=change(fresh),encoded=JSON.stringify(historyValue(rows));validateHistory(JSON.parse(encoded));
  localStorage.setItem(HISTORY,encoded);storedRaw=encoded;storageBlocked=false;history=rows;renderHistory();return rows;
 });
}
function download(value,name,raw=false){const blob=new Blob([raw?value:JSON.stringify(value,null,2)],{type:raw?'text/plain;charset=utf-8':'application/json'}),url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download=name;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function exportReport(report){download(report,'remteh-'+report.kind+'-'+report.session_id.replace(/[^a-z0-9_-]/gi,'_').slice(0,80)+'.json');}
function invite(){try{if(Date.now()<Number(localStorage.getItem('remteh-lab-support-hidden-until')||0))return;}catch{/* Core functionality is independent of invitation storage. */}$('support-invite').hidden=false;}
async function saveReport(key){
 try{const report=selected[key];let added=false;await writeHistory(current=>{if(current.some(row=>stable(row.report)===stable(report)))return current;if(current.length>=20)throw new Error('В истории уже 20 отчётов. Экспортируйте и удалите ненужную запись перед добавлением');added=true;return [{id:crypto.randomUUID(),saved_at:new Date().toISOString(),report},...current];});message(added?'Отчёт сохранён в локальную историю.':'Этот снимок отчёта уже есть в истории.');if(added)invite();}
 catch(error){message('Не удалось сохранить историю: '+error.message+'. Открытые отчёты сохранены в этой вкладке.');}
}
function renderHistory(){
 $('history-count').textContent=history.length;$('storage-status').textContent=storageBlocked?'История не прочитана. Исходные данные сохранены; экспортируйте резервную копию перед сбросом.':history.length?'История хранится только в этом браузере. Для переноса используйте экспорт.':'Сохранённых отчётов пока нет.';$('reset-history').hidden=!storageBlocked;try{$('legacy-history').hidden=!localStorage.getItem(LEGACY);}catch{$('legacy-history').hidden=true;}
 const target=$('history-list');target.replaceChildren();if(!history.length)return;const grid=node('div',undefined,'history-grid');
 for(const entry of history){const item=node('article',undefined,'history-item');item.append(node('h3',entry.report.device),node('p',kindName(entry.report.kind)+' · '+entry.report.application+' '+entry.report.version),node('p',new Date(entry.saved_at).toLocaleString('ru-RU')));const actions=node('div',undefined,'action-row');
 for(const [label,action]of [['Открыть до',()=>open('before',entry.report)],['Открыть после',()=>open('after',entry.report)],['JSON',()=>exportReport(entry.report)],['Удалить',async()=>{if(!confirm('Удалить этот отчёт из локальной истории? Экспортированный JSON останется у вас.'))return;try{await writeHistory(current=>current.filter(row=>row.id!==entry.id));message('Запись удалена из истории. Открытые документы сохранены.');}catch(error){message('История не изменена: '+error.message);}}]]){const button=node('button',label);button.addEventListener('click',action);actions.append(button);}item.append(actions);grid.append(item);
 }target.append(grid);
}
for(const key of ['before','after']){
 $(key).addEventListener('change',async event=>{const file=event.target.files?.[0];event.target.value='';if(!file)return;try{if(file.size>MAX_REPORT_BYTES)throw new Error('Файл больше 1 МБ');open(key,parseReport(await file.text()));}catch(error){message(error.message+'; открытые отчёты сохранены.');}});
 document.querySelector(`[data-save="${key}"]`).addEventListener('click',()=>saveReport(key));document.querySelector(`[data-export="${key}"]`).addEventListener('click',()=>exportReport(selected[key]));
}
$('print').addEventListener('click',()=>window.print());$('clear').addEventListener('click',()=>{selected.before=selected.after=null;refresh();checkpoint();message('Отчёты закрыты. История сохранена.');});
$('backup-history').addEventListener('click',()=>download(storageBlocked?storedRaw:historyValue(history),storageBlocked?'device-lab-history-original.txt':'device-lab-history.json',storageBlocked));
$('restore-history').addEventListener('click',()=>$('history-file').click());
$('history-file').addEventListener('change',async event=>{const file=event.target.files?.[0];event.target.value='';if(!file)return;try{if(file.size>MAX_HISTORY)throw new Error('Файл больше 3 МБ');const incoming=validateHistory(JSON.parse(await file.text()));let merged=[...history];for(const entry of incoming)if(!merged.some(row=>stable(row.report)===stable(entry.report)))merged.push({...entry,id:crypto.randomUUID()});validateHistory(historyValue(merged));if(!confirm('Добавить '+(merged.length-history.length)+' отчётов к локальной истории? Открытые документы не изменятся.'))return;await writeHistory(current=>{const latest=[...current];for(const entry of incoming)if(!latest.some(row=>stable(row.report)===stable(entry.report)))latest.push({...entry,id:crypto.randomUUID()});return latest;});message('История объединена. Открытые отчёты сохранены.');}catch(error){message('История не изменена: '+error.message);}});
$('reset-history').addEventListener('click',async()=>{const expected=storedRaw;if(!confirm('Начать пустую историю? Сначала экспортируйте исходную копию повреждённых данных.'))return;try{if(!navigator.locks)throw new Error('Web Locks недоступны');await navigator.locks.request(HISTORY,{mode:'exclusive'},()=>{if(latestHistory()!==expected)throw new Error('История изменилась в другом окне; обновите страницу');const empty=JSON.stringify(historyValue([]));localStorage.setItem(HISTORY,empty);history=[];storedRaw=empty;storageBlocked=false;renderHistory();});message('Новая история создана. История прежней версии и открытые документы сохранены.');}catch(error){message('История сохранена без изменений: '+error.message);}});
$('dismiss-support').addEventListener('click',()=>{$('support-invite').hidden=true;try{localStorage.setItem('remteh-lab-support-hidden-until',String(Date.now()+30*86400000));}catch{/* The invitation remains hidden in the current page. */}});
try{storedRaw=latestHistory();if(storedRaw)history=validateHistory(JSON.parse(storedRaw));}catch{storageBlocked=true;}
try{const raw=sessionStorage.getItem(WORKSPACE);if(raw){const value=JSON.parse(raw);for(const key of ['before','after'])if(value[key])selected[key]=parseReport(JSON.stringify(value[key]));}}catch{message('Черновик не прочитан. Импортируйте исходный JSON; история остаётся без изменений.');}
$('legacy-history').addEventListener('click',async()=>{try{const raw=localStorage.getItem(LEGACY);if(!raw)return;const rows=validateHistory(JSON.parse(raw));if(!confirm('Объединить историю предыдущей версии с текущей?'))return;await writeHistory(current=>{const merged=[...current];for(const entry of rows)if(!merged.some(row=>stable(row.report)===stable(entry.report)))merged.push({...entry,id:crypto.randomUUID()});return merged;});message('История предыдущей версии объединена. Открытые документы сохранены.');}catch(error){message('Предыдущая история сохранена без изменений: '+error.message);}});
window.addEventListener('storage',event=>{if(event.key!==HISTORY&&event.key!==LEGACY)return;try{storedRaw=latestHistory();history=storedRaw?validateHistory(JSON.parse(storedRaw)):[];storageBlocked=false;}catch{storageBlocked=true;}renderHistory();});
refresh();renderHistory();
// App updates never force a reload of active imported documents.
let waiting=null,changedElsewhere=false,requested=false,hadController=Boolean(navigator.serviceWorker?.controller);
function showUpdate(worker){waiting=worker;$('update').hidden=false;}
$('update').addEventListener('click',()=>{if(!checkpoint())return;if(changedElsewhere){location.reload();return;}requested=true;waiting?.postMessage({type:'ACTIVATE_UPDATE'});});
if('serviceWorker' in navigator&&location.protocol!=='file:'){
 navigator.serviceWorker.addEventListener('controllerchange',()=>{if(!hadController){hadController=true;return;}if(requested)location.reload();else{changedElsewhere=true;showUpdate(null);}});
 navigator.serviceWorker.register('./sw.js',{scope:'./',updateViaCache:'none'}).then(registration=>{const check=()=>{if(registration.waiting&&hadController)showUpdate(registration.waiting);};check();registration.addEventListener('updatefound',()=>registration.installing?.addEventListener('statechange',check));navigator.serviceWorker.ready.then(()=>$('offline-state').textContent=navigator.onLine?'Готово к запуску без сети':'Офлайн · приложение и история доступны');setInterval(()=>registration.update().catch(()=>{}),60000);}).catch(()=>$('offline-state').textContent='Автономная копия не подготовлена. Сохраните исходные JSON перед закрытием.');
 window.addEventListener('offline',()=>{$('offline-state').textContent='Офлайн · приложение и локальные данные';});window.addEventListener('online',()=>{$('offline-state').textContent='Сеть доступна · отчёты остаются локальными';});
}else $('offline-state').textContent='Для автономной установки откройте сайт по HTTPS или локальному HTTP.';
