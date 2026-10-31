import importlib.util,io,json,hashlib,tempfile,unittest,zipfile,shutil,os,fcntl
from pathlib import Path
from unittest.mock import patch
from types import SimpleNamespace
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('updates',ROOT/'ubuntu/remteh_webapps.py');app=importlib.util.module_from_spec(spec);spec.loader.exec_module(app)
def bundle(key):
 if key=='mouse':return Path(os.environ.get('REMTEH_MOUSE_TEST_BUNDLE','__unavailable_test_bundle__')).read_bytes() if Path(os.environ.get('REMTEH_MOUSE_TEST_BUNDLE','__unavailable_test_bundle__')).exists() else synthetic_site()
 raw=io.BytesIO()
 with zipfile.ZipFile(raw,'w') as z:
  for file in (ROOT/'dist/site').rglob('*'):
   if file.is_file():z.write(file,str(file.relative_to(ROOT/'dist/site')))
 return raw.getvalue()
def synthetic_site():
 raw=io.BytesIO()
 with zipfile.ZipFile(raw,'w') as z:z.writestr('index.html','<meta http-equiv="Content-Security-Policy" content="script-src \'self\'; connect-src \'none\'; object-src \'none\'">')
 return raw.getvalue()
def release(key,version,raw):
 spec=app.APPS[key];name=spec['file'].format(version=version)
 return {'tag_name':'v'+version,'draft':False,'prerelease':True,'assets':[{'name':name,'browser_download_url':'https://github.com/remteh-by/'+spec['repo']+'/releases/download/v'+version+'/'+name,'size':len(raw),'digest':'sha256:'+hashlib.sha256(raw).hexdigest()}]}
class Updates(unittest.TestCase):
 def setUp(self):self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name);self.override=patch.object(app,'ROOT',self.root);self.override.start();self.raw=bundle('mouse');self.version='1.3.0'
 def tearDown(self):self.override.stop();self.temp.cleanup()
 def download(self,url,limit):return json.dumps([release('mouse',self.version,self.raw)]).encode() if 'api.github.com' in url else self.raw
 def install(self):
  with patch.object(app,'fetch',self.download):return app.update_one('mouse')
 def test_actual_built_offline_sites(self):
  for key in ['mouse','lab']:
   dest=self.root/key;dest.mkdir();files=app.unpack(bundle(key),dest,key,'0.2.0');self.assertIn('index.html',files)
 def test_bad_archive_does_not_write_any_member(self):
  for name in ['../outside','/absolute','assets/../../outside','assets/.secret','server.py']:
   raw=io.BytesIO()
   with zipfile.ZipFile(raw,'w') as z:z.writestr('index.html','<p>valid path</p>');z.writestr(name,'bad')
   dest=self.root/'empty';dest.mkdir(exist_ok=True)
   with self.assertRaises(ValueError):app.unpack(raw.getvalue(),dest,'mouse','1.3.0')
   self.assertEqual(list(dest.iterdir()),[])
 def test_wrong_repository_and_corrupt_bytes_preserve_installed(self):
  self.install();current=(self.root/'mouse/current').resolve();state=(self.root/'mouse/state.json').read_bytes();self.version='1.3.1'
  with patch.object(app,'fetch',lambda url,limit:self.download(url,limit) if 'api.github.com' in url else b'bad'):
   with self.assertRaises(ValueError):app.update_one('mouse')
  self.assertEqual((self.root/'mouse/current').resolve(),current);self.assertEqual((self.root/'mouse/state.json').read_bytes(),state)
  row=release('mouse','1.3.2',self.raw);row['assets'][0]['browser_download_url']='https://example.com/replaced.zip'
  with self.assertRaises(ValueError):app.candidate([row],app.APPS['mouse'])
 def test_state_commit_failure_rolls_back_and_retry_reuses_verified_candidate(self):
  self.install();root=self.root/'mouse';old=(root/'current').resolve();before=(root/'state.json').read_bytes();self.version='1.3.1';writer=app.write_json
  def fail(path,value):
   if path==root/'state.json':raise OSError('ENOSPC simulation')
   return writer(path,value)
  with patch.object(app,'write_json',fail),patch.object(app,'fetch',self.download):
   with self.assertRaises(OSError):app.update_one('mouse')
  self.assertEqual((root/'current').resolve(),old);self.assertEqual((root/'state.json').read_bytes(),before);self.assertEqual(list(root.glob('current.pending-*')),[])
  self.assertEqual(self.install()['version'],'1.3.1');self.assertEqual(app.installed(root)['previous'],'1.3.0')
 def test_low_space_and_interruption_preserve_current_and_owner_files(self):
  self.install();root=self.root/'mouse';current=(root/'current').resolve();(self.root/'owner-file').write_text('keep');self.version='1.3.2'
  with patch.object(app.shutil,'disk_usage',return_value=SimpleNamespace(free=0)),patch.object(app,'fetch',self.download):
   with self.assertRaises(OSError):app.update_one('mouse')
  with patch.object(app,'unpack',side_effect=KeyboardInterrupt),patch.object(app,'fetch',self.download):
   with self.assertRaises(KeyboardInterrupt):app.update_one('mouse')
  self.assertEqual((root/'current').resolve(),current);self.assertEqual((self.root/'owner-file').read_text(),'keep');self.assertEqual(list((root/'versions').glob('.staging-*')),[])
 def test_rollback_holds_failed_version_and_leaves_unknown_folders(self):
  self.install();root=self.root/'mouse';unknown=root/'versions/9.0.0';unknown.mkdir();(unknown/'owner-file').write_text('keep');self.version='1.3.1';self.install();self.assertEqual(app.rollback('mouse')['version'],'1.3.0');self.assertEqual(self.install()['state'],'held-after-rollback');self.version='1.3.2';self.install();self.assertEqual((unknown/'owner-file').read_text(),'keep');self.assertEqual((root/'current').resolve().name,'1.3.2')
 def test_recovery_after_commit_interruption_uses_receipt(self):
  self.install();root=self.root/'mouse';self.version='1.3.1';self.install();(root/'state.json').unlink();self.assertEqual(app.installed(root)['version'],'1.3.1');self.assertTrue(app.installed(root)['recovered_after_interruption'])
 def test_active_old_version_is_retained_until_its_lock_is_released(self):
  self.install();root=self.root/'mouse';old=root/'versions/1.3.0'
  with (old/'running.lock').open('a') as running:
   fcntl.flock(running,fcntl.LOCK_SH);self.version='1.3.1';self.install();self.version='1.3.2';self.install();self.assertTrue(old.exists())
  self.version='1.3.3';self.install();self.assertFalse(old.exists())
 def test_native_requires_arm64_elf_and_resource_archive(self):
  for machine in [b'\xb7\x00',b'\x3e\x00']:
   raw=io.BytesIO();header=b'\x7fELF\x02\x01'+bytes(12)+machine
   with zipfile.ZipFile(raw,'w') as z:z.writestr('X-Play-1.3.0-linux-arm64/x-play',header);z.writestr('X-Play-1.3.0-linux-arm64/resources/app.asar','synthetic fixture')
   target=self.root/('native-'+machine.hex());target.mkdir()
   if machine==b'\xb7\x00':self.assertIn('x-play',app.unpack(raw.getvalue(),target,'xplay','1.3.0'))
   else:
    with self.assertRaises(ValueError):app.unpack(raw.getvalue(),target,'xplay','1.3.0')
 def test_old_hashed_bundle_remains_available_during_switch(self):
  raw=io.BytesIO()
  with zipfile.ZipFile(raw,'w') as z:
   with zipfile.ZipFile(io.BytesIO(synthetic_site())) as old:z.writestr('index.html',old.read('index.html'))
   z.writestr('assets/old-12345678.js','old verified bundle')
  self.raw=raw.getvalue();self.install();self.raw=synthetic_site();self.version='1.3.1';self.install();handler=object.__new__(app.Handler)
  target=Path(handler.translate_path('/Mouse-Tester/assets/old-12345678.js'));self.assertEqual(target.read_text(),'old verified bundle');self.assertIn('/1.3.0/',str(target))
  with self.assertRaises(ValueError):handler.translate_path('/Mouse-Tester/assets/../../private')
 def test_tampered_static_receipt_cannot_be_reused(self):
  self.install();root=self.root/'mouse';self.version='1.3.1';self.install();(root/'versions/1.3.1/Mouse-Tester/index.html').write_text('changed')
  with self.assertRaises(ValueError):app.receipt(root,'1.3.1',verify=True)
if __name__=='__main__':unittest.main()
