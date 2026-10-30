import {chromium} from 'playwright';import fs from 'node:fs';import path from 'node:path';import {fileURLToPath,pathToFileURL} from 'node:url';import assert from 'node:assert/strict';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),out=process.env.QA_DIR||path.join(root,'output');fs.mkdirSync(out,{recursive:true});
const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})}),results=[];
try{
 const page=await browser.newPage();const errors=[];page.on('pageerror',error=>errors.push(error.message));
 await page.goto(pathToFileURL(root+'/viewer/index.html').href);
 for(const kind of ['mouse','controller','phone-intake']){
  const report=JSON.parse(fs.readFileSync(root+'/examples/'+kind+'.json','utf8'));report.comment='<img src=x onerror="window.injected=true">';
  await page.locator('#before').setInputFiles({name:'report.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(report))});
  await page.locator('#left').getByText(report.comment,{exact:true}).waitFor();assert.equal(await page.evaluate(()=>window.injected),undefined);
  assert((await page.locator('#left table tr').count())>0);
  if(kind==='controller'){
   await page.getByText('N=2; среднее 0.05; σ 0.01',{exact:true}).waitFor();
   await page.locator('#after').setInputFiles({name:'after.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify({...report,display_dead_zone:0.2}))});
   await page.getByRole('status').filter({hasText:'условия отличаются'}).waitFor();
  }
  const invalid=structuredClone(report);
  if(kind==='controller')invalid.resting_axes.left_x.samples='2';else if(kind==='mouse')invalid.summary.totalClicks='2';else invalid.checks['Экран'].status='passed';
  const before=await page.locator('#left').textContent();
  await page.locator('#before').setInputFiles({name:'invalid.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(invalid))});
  await page.getByRole('status').filter({hasText:'открытые отчёты сохранены'}).waitFor();assert.equal(await page.locator('#left').textContent(),before);
  results.push({kind,validImport:true,invalidImportPreserves:true,scriptTextNotExecuted:true});
 }
 for(const width of [390,1920,2560,3840]){await page.setViewportSize({width,height:1000});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);}
 await page.screenshot({path:out+'/viewer.png',fullPage:true});assert.deepEqual(errors,[]);
}finally{await browser.close();fs.writeFileSync(out+'/viewer-results.json',JSON.stringify(results,null,2));}
console.log('Device Lab viewer passed all three report adapters, invalid data and wide viewports');
