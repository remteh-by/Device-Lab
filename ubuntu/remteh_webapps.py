#!/usr/bin/env python3
"""User-scoped RemTeh apps. Public GitHub releases, verified archives, atomic updates."""
import argparse,fcntl,hashlib,http.server,io,json,os,re,shutil,stat,subprocess,sys,tempfile,time,urllib.parse,urllib.request,zipfile,uuid
from html.parser import HTMLParser
from pathlib import Path,PurePosixPath
ROOT=Path.home()/'.local/share/remteh-webapps';PORT=18730;MANAGED='remteh-apps-v2';RESERVE=64*1024*1024
APPS={
 'mouse':{'repo':'Mouse-Tester','prefix':'Mouse-Tester','file':'Mouse-Tester-{version}-site.zip','limit':10*1024*1024,'unpacked':32*1024*1024,'web':True},
 'lab':{'repo':'Device-Lab','prefix':'Device-Lab','file':'Device-Lab-{version}-site.zip','limit':10*1024*1024,'unpacked':32*1024*1024,'web':True},
 'xplay':{'repo':'x-portal','prefix':'X-Play-{version}-linux-arm64','file':'X-Play-{version}-linux-arm64.zip','limit':250*1024*1024,'unpacked':900*1024*1024,'web':False},
}
def numbers(value):
 if not isinstance(value,str) or not re.fullmatch(r'\d+\.\d+\.\d+',value):raise ValueError('Invalid version')
 return tuple(map(int,value.split('.')))
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write_json(path,value):
 data=(json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode();fd,name=tempfile.mkstemp(prefix=path.name+'.pending-',dir=path.parent);temp=Path(name)
 try:
  with os.fdopen(fd,'wb') as f:f.write(data);f.flush();os.fsync(f.fileno())
  temp.chmod(0o600);os.replace(temp,path)
 finally:temp.unlink(missing_ok=True)
class Redirects(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,req,fp,code,msg,headers,newurl):
  url=urllib.parse.urlsplit(newurl)
  if url.scheme!='https' or url.port not in (None,443) or url.username or url.password or url.hostname not in ('github.com','api.github.com') and not (url.hostname or '').endswith('.githubusercontent.com'):raise ValueError('Download left GitHub')
  return super().redirect_request(req,fp,code,msg,headers,newurl)
def fetch(url,limit):
 request=urllib.request.Request(url,headers={'User-Agent':'RemTeh-Ubuntu-Updater/2.0','Accept':'application/vnd.github+json' if url.startswith('https://api.github.com/') else 'application/octet-stream'})
 with urllib.request.build_opener(Redirects()).open(request,timeout=60) as response:data=response.read(limit+1)
 if len(data)>limit:raise ValueError('Download exceeds limit')
 return data
def candidate(rows,spec):
 if not isinstance(rows,list):raise ValueError('Invalid release list')
 found=[]
 for release in rows:
  tag=release.get('tag_name','')
  if release.get('draft') or not re.fullmatch(r'v\d+\.\d+\.\d+',tag):continue
  version=tag[1:];name=spec['file'].format(version=version);asset=next((a for a in release.get('assets',[]) if a.get('name')==name),None)
  if not asset:continue
  expected='https://github.com/remteh-by/'+spec['repo']+'/releases/download/'+tag+'/'+name
  if asset.get('browser_download_url')!=expected or not re.fullmatch(r'sha256:[0-9a-f]{64}',asset.get('digest','')) or type(asset.get('size')) is not int or not 0<asset['size']<=spec['limit']:raise ValueError('Invalid asset metadata')
  found.append((numbers(version),version,asset))
 return max(found,key=lambda item:item[0],default=None)
class Policy(HTMLParser):
 def __init__(self):super().__init__();self.policies=[]
 def handle_starttag(self,tag,attrs):
  attrs=dict(attrs)
  if tag=='meta' and attrs.get('http-equiv','').lower()=='content-security-policy':self.policies.append(attrs.get('content',''))
def validate_site(directory,spec):
 document=(directory/'index.html').read_text();parser=Policy();parser.feed(document)
 good=False
 for text in parser.policies:
  policy={items[0]:items[1:] for group in text.split(';') if (items:=group.split())}
  if policy.get('connect-src') in (["'none'"],["'self'"]) and policy.get('script-src')==["'self'"] and policy.get('object-src')==["'none'"]:good=True
 if not good:raise ValueError('Static site policy is not compatible')
 if (directory/'sw.js').exists()!=(directory/'manifest.webmanifest').exists():raise ValueError('Incomplete offline build')
 if (directory/'manifest.webmanifest').exists():
  manifest=json.loads((directory/'manifest.webmanifest').read_text());allowed={'./','/'+spec['prefix']+'/'}
  if manifest.get('scope') not in allowed or manifest.get('start_url') not in allowed:raise ValueError('Unexpected offline app scope')
def unpack(raw,destination,key,version):
 spec=APPS[key];web=spec['web'];prefix=spec['prefix'].format(version=version);files={};items=[];seen=set()
 with zipfile.ZipFile(io.BytesIO(raw)) as archive:
  entries=archive.infolist();size=sum(e.file_size for e in entries)
  if len(entries)>4096 or size>spec['unpacked']:raise ValueError('Archive exceeds limits')
  if shutil.disk_usage(destination.parent).free<size+RESERVE:raise OSError('Insufficient free space; installed app preserved')
  for entry in entries:
   path=PurePosixPath(entry.filename);mode=entry.external_attr>>16
   if path.is_absolute() or '\\' in entry.filename or '\x00' in entry.orig_filename or not path.parts or any(p in ('.','..') or p.startswith('.') or len(p)>255 for p in path.parts) or path in seen:raise ValueError('Unsafe archive path')
   seen.add(path)
   if stat.S_IFMT(mode) not in (0,stat.S_IFREG,stat.S_IFDIR):raise ValueError('Special archive file')
   if web:
    allowed={'index.html','favicon.svg','assets','devices','icons','sw.js','manifest.webmanifest','style.css','app.js','reports.js'}
    if path.parts[0] not in allowed or path.parts[0] not in {'assets','devices','icons'} and len(path.parts)>1:raise ValueError('Unexpected site file')
    relative=path
   else:
    if path.parts[0]!=prefix:raise ValueError('Unexpected native archive root')
    relative=PurePosixPath(*path.parts[1:])
    if str(relative)=='.':continue
   target=destination/str(relative);items.append((entry,target,mode))
  # All paths and file types are validated before any archive member is written.
  for entry,target,mode in items:
   if entry.is_dir():target.mkdir(parents=True,exist_ok=True);continue
   target.parent.mkdir(parents=True,exist_ok=True)
   with archive.open(entry) as source,target.open('xb') as out:shutil.copyfileobj(source,out,1024*1024)
   target.chmod(0o755 if mode&0o111 else 0o644);files[str(target.relative_to(destination))]=sha(target)
 if web:validate_site(destination,spec)
 else:
  binary=destination/'x-play'
  with binary.open('rb') as stream:header=stream.read(20)
  if header[:6]!=b'\x7fELF\x02\x01' or header[18:20]!=b'\xb7\x00' or not (destination/'resources/app.asar').is_file():raise ValueError('Expected Linux ARM64 X Play')
  binary.chmod(0o755)
 return files
def receipt(root,version,verify=False):
 numbers(version);path=root/'versions'/version
 if path.is_symlink():raise ValueError('Version directory is a symlink')
 data=json.loads((path/'receipt.json').read_text())
 if data.get('managed_by') not in (MANAGED,'remteh-webapps-v1') or data.get('version')!=version:raise ValueError('Unmanaged installed version')
 if data.get('content') not in (None,'Mouse-Tester','Device-Lab','app'):raise ValueError('Invalid content directory')
 if verify and data.get('files'):
  if any(PurePosixPath(name).is_absolute() or '..' in PurePosixPath(name).parts for name in data['files']):raise ValueError('Unsafe receipt path')
  directory=path/data['content'];paths=[p for p in directory.rglob('*') if p.is_file()]
  if any(p.is_symlink() for p in paths):raise ValueError('Managed file became a symlink')
  actual={str(p.relative_to(directory)) for p in paths}
  if actual!=set(data['files']) or any(sha(directory/name)!=digest for name,digest in data['files'].items()):raise ValueError('Managed version bytes changed')
 return data
def installed(root):
 state_file=root/'state.json';state=json.loads(state_file.read_text()) if state_file.exists() else {}
 link=root/'current'
 if link.is_symlink():
  target=link.resolve(strict=True);target.relative_to((root/'versions').resolve());
  if target.parent!=(root/'versions').resolve():raise ValueError('Unexpected version link')
  version=target.name;data=receipt(root,version)
  if state.get('version')!=version:state={'version':version,'previous':data.get('previous'),'sha256':data['sha256'],'recovered_after_interruption':True};write_json(state_file,state)
 elif link.exists():raise ValueError('Current path is not a managed link')
 elif state.get('version'):raise ValueError('Installed version link missing')
 return state

def switch_version(root,version,new_state):
 old=os.readlink(root/'current') if (root/'current').is_symlink() else None;link=root/('current.pending-'+uuid.uuid4().hex)
 if link.exists() or link.is_symlink():raise ValueError('Unexpected pending current link')
 try:
  link.symlink_to('versions/'+version);os.replace(link,root/'current')
  try:write_json(root/'state.json',new_state)
  except BaseException:
   if (root/'current').is_symlink() and os.readlink(root/'current')=='versions/'+version:
    if old is None:(root/'current').unlink()
    else:link.symlink_to(old);os.replace(link,root/'current')
   raise
 finally:link.unlink(missing_ok=True)
def update_one(key):
 spec=APPS[key];root=ROOT/key;root.mkdir(parents=True,exist_ok=True)
 with (root/'update.lock').open('a') as lock:
  fcntl.flock(lock,fcntl.LOCK_EX);state=installed(root)
  found=candidate(json.loads(fetch('https://api.github.com/repos/remteh-by/'+spec['repo']+'/releases?per_page=30',2*1024*1024)),spec)
  if found is None:raise ValueError('No compatible published release for '+key)
  numeric,version,asset=found;current=state.get('version','0.0.0')
  if numeric<=numbers(current) or version==state.get('blocked_version'):
   state['checked_at']=int(time.time());write_json(root/'state.json',state);return {'app':key,'state':'current' if numeric<=numbers(current) else 'held-after-rollback','version':current}
  if shutil.disk_usage(root).free<asset['size']*4+RESERVE:raise OSError('Insufficient space; installed app preserved')
  raw=fetch(asset['browser_download_url'],asset['size']);digest=hashlib.sha256(raw).hexdigest()
  if len(raw)!=asset['size'] or 'sha256:'+digest!=asset['digest']:raise ValueError('Release checksum mismatch')
  versions=root/'versions';versions.mkdir(exist_ok=True);destination=versions/version;stage=None
  if destination.exists():
   prior=receipt(root,version,verify=True)
   if prior.get('sha256')!=digest or prior.get('bytes')!=len(raw):raise ValueError('Existing version does not match release')
  else:
   stage=Path(tempfile.mkdtemp(prefix='.staging-',dir=versions))
   try:
    content=spec['prefix'] if spec['web'] else 'app';directory=stage/content;directory.mkdir();files=unpack(raw,directory,key,version)
    write_json(stage/'receipt.json',{'managed_by':MANAGED,'app':key,'version':version,'previous':state.get('version'),'sha256':digest,'bytes':len(raw),'content':content,'files':files})
    stage.rename(destination)
   finally:
    if stage.exists():shutil.rmtree(stage)
  switch_version(root,version,{'version':version,'previous':state.get('version'),'checked_at':int(time.time()),'sha256':digest,'channel':'published GitHub stable and preview releases'})
  keep={version,state.get('version')}
  for old in versions.iterdir():
   if old.name not in keep and re.fullmatch(r'\d+\.\d+\.\d+',old.name) and not old.is_symlink():
    try:receipt(root,old.name)
    except (ValueError,FileNotFoundError,json.JSONDecodeError):continue
    with (old/'running.lock').open('a') as running:
     try:fcntl.flock(running,fcntl.LOCK_EX|fcntl.LOCK_NB)
     except BlockingIOError:continue
     # Preserve a manually launched process as well as official launcher locks.
     active=False
     if not spec['web'] and Path('/proc').exists():
      for process in Path('/proc').iterdir():
       if process.name.isdigit():
        try:active|=str((process/'exe').readlink()).startswith(str(old)+'/')
        except (OSError,PermissionError):pass
     if not active:shutil.rmtree(old)
  return {'app':key,'state':'updated','version':version,'sha256':digest}
def rollback(key):
 root=ROOT/key
 with (root/'update.lock').open('a') as lock:
  fcntl.flock(lock,fcntl.LOCK_EX);state=installed(root);previous=state.get('previous')
  if not previous:raise ValueError('No retained previous version')
  old=receipt(root,previous,verify=True);switch_version(root,previous,{'version':previous,'previous':state['version'],'blocked_version':state['version'],'sha256':old['sha256'],'checked_at':int(time.time())});return {'app':key,'state':'rolled-back','version':previous,'held':state['version']}
class Handler(http.server.SimpleHTTPRequestHandler):
 extensions_map={**http.server.SimpleHTTPRequestHandler.extensions_map,'.webmanifest':'application/manifest+json','.js':'text/javascript'}
 def translate_path(self,path):
  decoded=urllib.parse.unquote(urllib.parse.urlsplit(path).path)
  for key,spec in APPS.items():
   if spec['web'] and decoded.startswith('/'+spec['prefix']+'/'):
    relative=decoded[len(spec['prefix'])+2:]
    if '\\' in relative or '\x00' in relative or '..' in PurePosixPath(relative).parts:raise ValueError('Invalid path')
    current=(ROOT/key/'current').resolve();base=(current/spec['prefix']).resolve();target=(base/relative).resolve();target.relative_to(base)
    # A page fetched just before a version switch can still request its hashed bundles.
    if not target.exists() and relative.startswith('assets/'):
     try:
      previous=json.loads((current/'receipt.json').read_text()).get('previous');numbers(previous);old=(ROOT/key/'versions'/previous/spec['prefix']).resolve();fallback=(old/relative).resolve();fallback.relative_to(old)
      if fallback.is_file():return str(fallback)
     except (OSError,ValueError,TypeError):pass
    return str(target)
  raise ValueError('Unknown app path')
 def do_GET(self):
  try:self.translate_path(self.path)
  except (ValueError,OSError):return self.send_error(404)
  return super().do_GET()
 def do_HEAD(self):
  try:self.translate_path(self.path)
  except (ValueError,OSError):return self.send_error(404)
  return super().do_HEAD()
 def list_directory(self,path):self.send_error(403)
 def end_headers(self):self.send_header('Cache-Control','no-cache');self.send_header('X-Content-Type-Options','nosniff');super().end_headers()
 def log_message(self,*args):pass
def launch(key):
 if key=='xplay':
  state=installed(ROOT/key);version=ROOT/key/'versions'/state['version'];data=receipt(ROOT/key,state['version']);binary=version/data['content']/'x-play'
  with (version/'running.lock').open('a') as running:
   fcntl.flock(running,fcntl.LOCK_SH)
   log=ROOT/key/'last-launch.log';previous=ROOT/key/'previous-launch.log'
   if log.exists():os.replace(log,previous)
   with log.open('wb') as output:
    result=subprocess.run([str(binary)],stdout=output,stderr=subprocess.STDOUT)
   if result.returncode and shutil.which('notify-send'):subprocess.run(['notify-send','X Play','Не удалось открыть приложение. Сохранённая предыдущая версия доступна через команду отката.'],capture_output=True)
   return result.returncode
 else:
  subprocess.run(['systemctl','--user','start','remteh-mouse.service'],check=True,capture_output=True)
  url='https://remteh.by/remont-gamepadov/diagnostika-gejmpada' if key=='gamepad' else 'http://127.0.0.1:'+str(PORT)+'/'+APPS[key]['prefix']+'/'
  profile=Path.home()/'.config/remteh-webapps'/key;os.execv('/usr/bin/chromium-browser',['chromium-browser','--app='+url,'--disable-gpu','--no-first-run','--no-default-browser-check','--user-data-dir='+str(profile),'--class=RemTeh'+key.title()])
def install():
 if not sys.platform.startswith('linux') or os.getuid()==0:raise ValueError('Install as the Ubuntu desktop user')
 ROOT.mkdir(parents=True,exist_ok=True);target=ROOT/'remteh_webapps.py'
 if Path(__file__).resolve()!=target.resolve():
  source=Path(__file__).read_bytes()
  if target.exists() and target.read_bytes()!=source:shutil.copyfile(target,ROOT/'remteh_webapps.previous.py');(ROOT/'remteh_webapps.previous.py').chmod(0o600)
  fd,name=tempfile.mkstemp(prefix='updater.pending-',dir=ROOT)
  with os.fdopen(fd,'wb') as file:file.write(source);file.flush();os.fsync(file.fileno())
  Path(name).chmod(0o700);os.replace(name,target)
 logo=ROOT/'remteh-logo-white.png'
 if not logo.exists():shutil.copyfile(Path(__file__).with_name('remteh-logo-white.png'),logo);logo.chmod(0o644)
 results=[update_one(key) for key in APPS];units=Path.home()/'.config/systemd/user';units.mkdir(parents=True,exist_ok=True)
 units.joinpath('remteh-mouse.service').write_text('[Unit]\nDescription=RemTeh local web apps\n\n[Service]\nExecStart=/usr/bin/python3 %h/.local/share/remteh-webapps/remteh_webapps.py serve\nRestart=on-failure\nNoNewPrivileges=true\nPrivateTmp=true\nProtectSystem=strict\nProtectHome=read-only\nRestrictAddressFamilies=AF_UNIX AF_INET\n\n[Install]\nWantedBy=default.target\n')
 units.joinpath('remteh-apps-update.service').write_text('[Unit]\nDescription=Verified RemTeh GitHub release updates\n\n[Service]\nType=oneshot\nExecStart=/usr/bin/python3 %h/.local/share/remteh-webapps/remteh_webapps.py update all\nNoNewPrivileges=true\nPrivateTmp=true\nProtectSystem=strict\nProtectHome=read-only\nReadWritePaths=%h/.local/share/remteh-webapps\nRestrictAddressFamilies=AF_UNIX AF_INET AF_INET6\n')
 units.joinpath('remteh-apps-update.timer').write_text('[Unit]\nDescription=Check RemTeh releases hourly\n\n[Timer]\nOnStartupSec=2min\nOnCalendar=hourly\nRandomizedDelaySec=5min\nPersistent=true\n\n[Install]\nWantedBy=timers.target\n')
 for directory in [Path.home()/'.local/share/applications',Path.home()/'Desktop']:
  directory.mkdir(parents=True,exist_ok=True)
  for key,title in [('mouse','Mouse Tester — RemTeh'),('lab','Device Lab — RemTeh'),('gamepad','Gamepad Tester — RemTeh'),('xplay','X Play')]:
   file=directory/('remteh-'+key+'.desktop');file.write_text('[Desktop Entry]\nType=Application\nName='+title+'\nExec=/usr/bin/python3 '+str(target)+' launch '+key+'\nIcon='+str(ROOT/'remteh-logo-white.png')+'\nTerminal=false\nCategories=Game;Utility;\nStartupWMClass=RemTeh'+key.title()+'\n');file.chmod(0o755)
 subprocess.run(['systemctl','--user','daemon-reload'],check=True);subprocess.run(['systemctl','--user','enable','--now','remteh-mouse.service','remteh-apps-update.timer'],check=True,capture_output=True);subprocess.run(['systemctl','--user','restart','remteh-mouse.service'],check=True,capture_output=True)
 subprocess.run(['systemctl','--user','disable','--now','remteh-mouse-update.timer'],capture_output=True)
 return results
if __name__=='__main__':
 os.umask(0o077);parser=argparse.ArgumentParser();parser.add_argument('command',choices=['install','update','rollback','serve','launch','status']);parser.add_argument('app',nargs='?',default='all',choices=['all','mouse','lab','xplay','gamepad']);args=parser.parse_args()
 if args.command=='serve':http.server.ThreadingHTTPServer(('127.0.0.1',PORT),Handler).serve_forever()
 elif args.command=='install':print(json.dumps(install()))
 elif args.command=='launch':
  if args.app=='all':parser.error('Select mouse, lab, gamepad or xplay')
  raise SystemExit(launch(args.app) or 0)
 elif args.command=='rollback':
  if args.app not in APPS:parser.error('Select mouse, lab or xplay')
  print(json.dumps(rollback(args.app)))
 elif args.command=='status':print(json.dumps({key:installed(ROOT/key) for key in APPS if (ROOT/key).exists()}))
 elif args.app=='gamepad':print(json.dumps({'app':'gamepad','state':'web','updates':'latest public site on launch'}))
 else:
  rows=[];failed=False
  for key in APPS if args.app=='all' else [args.app]:
   try:rows.append(update_one(key))
   except Exception as error:failed=True;rows.append({'app':key,'state':'preserved','error':str(error)})
  print(json.dumps(rows));raise SystemExit(1 if failed else 0)
