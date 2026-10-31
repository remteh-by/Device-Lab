export const MAX_REPORT_BYTES=1048576;
const object=value=>value&&typeof value==='object'&&!Array.isArray(value);
const count=value=>Number.isSafeInteger(value)&&value>=0&&value<=1e12;
const number=value=>typeof value==='number'&&Number.isFinite(value)&&Math.abs(value)<=1e12;
export function parseReport(text){
 if(new TextEncoder().encode(text).length>MAX_REPORT_BYTES)throw new Error('Отчёт больше 1 МБ');
 const value=JSON.parse(text);
 if(!object(value)||value.schema!==1||!['controller','mouse','phone-intake'].includes(value.kind))throw new Error('Нужен отчёт Device Lab schema 1');
 const pending=[[value,0]];let nodes=0;
 while(pending.length){const [entry,depth]=pending.pop();if(++nodes>10000||depth>16)throw new Error('Слишком сложный отчёт');if(entry&&typeof entry==='object')for(const child of Object.values(entry))pending.push([child,depth+1]);else if(typeof entry==='number'&&!Number.isFinite(entry))throw new Error('Некорректное число');else if(typeof entry==='string'&&entry.length>4000)throw new Error('Слишком длинное поле');}
 for(const field of ['session_id','application','version','source','device'])if(typeof value[field]!=='string'||!value[field].trim()||value[field].length>400)throw new Error('Повреждён заголовок отчёта');
 if(value.duration_ms!==undefined&&(!number(value.duration_ms)||value.duration_ms<0))throw new Error('Некорректная продолжительность');
 for(const field of ['comment','limitations'])if(value[field]!==undefined&&typeof value[field]!=='string')throw new Error('Некорректный текст отчёта');
 if(value.kind==='mouse'){
  if(!object(value.summary)||!object(value.conditions))throw new Error('Нет результатов мыши');
  for(const key of ['totalClicks','suspectedBounce','scrollReversals','pointerEvents'])if(!count(value.summary[key]))throw new Error('Некорректные счётчики мыши');
  for(const key of ['eventRateHz','peakEventRateHz','meanHoldMs'])if(value.summary[key]!==undefined&&value.summary[key]!==null&&(!number(value.summary[key])||value.summary[key]<0))throw new Error('Некорректная частота или длительность');
  if(value.observations!==undefined&&(!Array.isArray(value.observations)||value.observations.length>512))throw new Error('Превышен журнал мыши');
 }
 if(value.kind==='controller'){
  if(!object(value.axes)||!object(value.resting_axes)||!number(value.display_dead_zone)||value.display_dead_zone<0||value.display_dead_zone>=1)throw new Error('Некорректные результаты контроллера');
  for(const collection of [value.axes,value.resting_axes])for(const row of Object.values(collection)){
   if(!object(row)||!count(row.samples))throw new Error('Некорректная выборка оси');
   for(const key of ['min','max','mean','standard_deviation','last'])if(row.samples>0?(!number(row[key])||(key==='standard_deviation'&&row[key]<0)):row[key]!==null)throw new Error('Некорректные измерения оси');
   if(row.samples&&row.min>row.max)throw new Error('Нарушен диапазон оси');
  }
  for(const name of ['left_x','left_y','right_x','right_y'])if(!value.resting_axes[name])throw new Error('Нет оси покоя');
  if(value.recent_samples!==undefined&&(!Array.isArray(value.recent_samples)||value.recent_samples.length>256))throw new Error('Превышен журнал контроллера');
 }
 if(value.kind==='phone-intake'){
  if(!object(value.checks)||!Object.keys(value.checks).length||Object.keys(value.checks).length>32)throw new Error('Нет проверок телефона');
  for(const [key,row] of Object.entries(value.checks))if(key.length>80||!object(row)||!['not-measured','observed','suspicion','unavailable'].includes(row.status)||row.detail!==undefined&&typeof row.detail!=='string')throw new Error('Некорректный статус проверки');
 }
 return value;
}
function canonical(value){if(Array.isArray(value))return value.map(canonical);if(object(value))return Object.keys(value).sort().map(key=>[key,canonical(value[key])]);return value;}
export const stable=value=>JSON.stringify(canonical(value));
export function comparison(before,after){
 if(!before||!after)return {compatible:false,reasons:['Откройте два отчёта']};
 const reasons=[];
 for(const [key,label] of [['kind','тип'],['source','источник'],['device','устройство'],['application','приложение'],['version','версия'],['os','ОС']])if((before[key]??null)!==(after[key]??null))reasons.push(label);
 if(before.display_dead_zone!==after.display_dead_zone)reasons.push('фильтр отображения');
 if(stable(before.conditions??before.capabilities??null)!==stable(after.conditions??after.capabilities??null))reasons.push('условия');
 return {compatible:!reasons.length,reasons};
}
export const kindName=kind=>({mouse:'Мышь',controller:'Геймпад','phone-intake':'Телефон'}[kind]||kind);
export const statusName=status=>({'not-measured':'Не измерено',observed:'Наблюдалось в тесте',suspicion:'Есть замечание',unavailable:'Недоступно'}[status]||'Не измерено');
export const checkName=key=>({screen:'Экран',touch:'Мультитач',speaker:'Динамик',microphone:'Микрофон',vibration:'Вибрация',sensors:'Датчики',camera:'Камера',battery:'Батарея'}[key]||key);
export function measurements(report){
 if(report.kind==='mouse')return [['Нажатия',report.summary.totalClicks],['Подозрения на повтор',report.summary.suspectedBounce],['Обратные шаги колеса',report.summary.scrollReversals],['Частота событий браузера, Гц',report.summary.eventRateHz],['Событий движения',report.summary.pointerEvents]];
 if(report.kind==='controller')return Object.entries(report.resting_axes).map(([name,row])=>[name+' · покой',row.samples>0?'N='+row.samples+'; среднее '+row.mean+'; σ '+row.standard_deviation:null]);
 return Object.entries(report.checks).map(([name,row])=>[checkName(name),statusName(row.status)+(row.detail?' · '+row.detail:'')]);
}
