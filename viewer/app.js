'use strict';
const selected={before:null,after:null};
function parse(text){
 if(new TextEncoder().encode(text).length>1048576)throw new Error('Отчёт больше 1 МБ');
 const value=JSON.parse(text);if(!value||value.schema!==1||!['controller','mouse','phone-intake'].includes(value.kind))throw new Error('Нужен отчёт Device Lab schema 1');
 const pending=[[value,0]];let nodes=0;while(pending.length){const [entry,depth]=pending.pop();if(++nodes>10000||depth>16)throw new Error('Слишком сложный отчёт');if(entry&&typeof entry==='object'){for(const child of Object.values(entry))pending.push([child,depth+1]);}else if(typeof entry==='number'&&!Number.isFinite(entry))throw new Error('Некорректное число');else if(typeof entry==='string'&&entry.length>4000)throw new Error('Слишком длинное поле');}
 for(const field of ['session_id','application','version','source','device'])if(typeof value[field]!=='string'||value[field].length>400)throw new Error('Повреждён заголовок отчёта');
 if(value.kind==='mouse'&&(!value.summary||!value.conditions))throw new Error('Нет результатов мыши');
 if(value.kind==='controller'&&(!value.axes||!value.resting_axes))throw new Error('Нет результатов контроллера');
 if(value.kind==='phone-intake'&&!value.checks)throw new Error('Нет проверок телефона');
 const count=v=>Number.isSafeInteger(v)&&v>=0&&v<=1e12;
 const number=v=>typeof v==='number'&&Number.isFinite(v)&&Math.abs(v)<=1e12;
 if(value.kind==='mouse'){
  for(const key of ['totalClicks','suspectedBounce','scrollReversals','pointerEvents'])if(!count(value.summary[key]))throw new Error('Некорректные счётчики');
  if(value.summary.eventRateHz!==null&&(!number(value.summary.eventRateHz)||value.summary.eventRateHz<0))throw new Error('Некорректная частота');
 }
 if(value.kind==='controller'&&(!number(value.display_dead_zone)||value.display_dead_zone<0||value.display_dead_zone>=1))throw new Error('Некорректная мёртвая зона');
 if(value.kind==='controller')for(const name of ['left_x','left_y','right_x','right_y']){
  const row=value.resting_axes[name];if(!row||!count(row.samples))throw new Error('Некорректная выборка оси');
  if(row.samples>0&&(!number(row.mean)||!number(row.standard_deviation)||row.standard_deviation<0))throw new Error('Некорректные измерения оси');
 }
 if(value.kind==='phone-intake')for(const row of Object.values(value.checks))if(!row||!['not-measured','observed','suspicion','unavailable'].includes(row.status))throw new Error('Некорректный статус проверки');
 return value;
}
function element(tag,text){const node=document.createElement(tag);node.textContent=String(text);return node;}
function table(parent,rows){const result=document.createElement('table');for(const [label,value]of rows){const row=document.createElement('tr');row.append(element('th',label),element('td',value===null?'Не измерено':value));result.append(row);}parent.append(result);}
function render(target,report,title){target.replaceChildren();target.append(element('h2',title));if(!report){target.append(element('p','Отчёт не выбран'));return;}target.append(element('p',report.device),element('p',report.application+' '+report.version+' · '+report.kind),element('p','Источник: '+report.source),element('p','Сессия: '+report.session_id));
 if(report.kind==='mouse'){table(target,[['Нажатия',report.summary.totalClicks],['Подозрения на повтор',report.summary.suspectedBounce],['Обратные шаги колеса',report.summary.scrollReversals],['Частота событий браузера, Гц',report.summary.eventRateHz],['Событий движения',report.summary.pointerEvents]]);target.append(element('p','Условия: '+JSON.stringify(report.conditions)));}
 if(report.kind==='controller'){table(target,Object.entries(report.resting_axes).map(([name,row])=>[name+' · покой',row.samples>0?'N='+row.samples+'; среднее '+row.mean+'; σ '+row.standard_deviation:'Не измерено']));target.append(element('p','Фильтр отображения: '+report.display_dead_zone+'; исходные значения API сохраняются отдельно.'))}
 if(report.kind==='phone-intake')table(target,Object.entries(report.checks).map(([name,row])=>[name,row.status+' · '+(row.detail||'')]));
 target.append(element('h3','Комментарий'),element('p',report.comment||'Не указан'),element('h3','Ограничения'),element('p',report.limitations||'Наблюдения API. Автоматического диагноза нет.'));
 const details=document.createElement('details');details.append(element('summary','Исходный JSON'),element('pre',JSON.stringify(report,null,2)));target.append(details);
}
function refresh(){render(document.getElementById('left'),selected.before,'До');render(document.getElementById('right'),selected.after,'После');if(selected.before&&selected.after){const a=selected.before,b=selected.after;const compatible=a.kind===b.kind&&a.source===b.source&&a.device===b.device&&a.display_dead_zone===b.display_dead_zone&&JSON.stringify(a.conditions??a.capabilities??null)===JSON.stringify(b.conditions??b.capabilities??null);document.getElementById('status').textContent=compatible?'Тип, источник и сохранённые условия совпадают. Сравните выборку и результаты; исправность не выводится автоматически.':'Тип, источник, устройство или условия отличаются. Прямое числовое сравнение ограничено.';}}
for(const key of ['before','after'])document.getElementById(key).addEventListener('change',async event=>{const file=event.target.files?.[0];event.target.value='';if(!file)return;try{if(file.size>1048576)throw new Error('Файл больше 1 МБ');const report=parse(await file.text());selected[key]=report;document.getElementById('status').textContent='Отчёт открыт локально.';refresh();}catch(error){document.getElementById('status').textContent=error.message+'; открытые отчёты сохранены.';}});
document.getElementById('print').addEventListener('click',()=>window.print());document.getElementById('clear').addEventListener('click',()=>{selected.before=selected.after=null;document.getElementById('status').textContent='Отчёты закрыты.';refresh();});refresh();
