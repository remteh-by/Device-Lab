import {spawnSync} from 'node:child_process';import fs from 'node:fs';import os from 'node:os';import path from 'node:path';
const target=fs.mkdtempSync(path.join(os.tmpdir(),'remteh-phone-parser-'));
try{for(const [command,args] of [['javac',['-d',target,'phonecheck/app/src/main/java/com/remteh/phonecheck/PhoneReportCodec.java','tests/java/PhoneReportsTest.java']],['java',['-ea','-cp',target,'PhoneReportsTest']]]){const r=spawnSync(command,args,{stdio:'inherit'});if(r.status)process.exitCode=r.status;if(r.status)break;}}finally{fs.rmSync(target,{recursive:true,force:true});}
