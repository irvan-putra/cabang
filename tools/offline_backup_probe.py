# SPDX-License-Identifier: GPL-3.0-only
# Copyright (C) 2026 irvan-putra
# Offline backup/logging probe. Run: unshare -Urn timeout 60 python tools/offline_backup_probe.py  (disposable accounts only; seeds a pseudo-transport like upstream tests; adapt server path)
import os,sys,pathlib,tempfile,json,sqlite3,threading,time,re
sys.path.insert(0,'/tmp/cabang/android/app/src/main/python')
import cabang_core as core
from deltachat_rpc_client import Rpc,DeltaChat
server='/tmp/v/bin/deltachat-rpc-server'
M='SYNTHMARK-4d2e9b17-aa01'
root=pathlib.Path(tempfile.mkdtemp());out=pathlib.Path(tempfile.mkdtemp())
rpc=Rpc(accounts_dir=str(root),rpc_server_path=server);rpc.start();dc=DeltaChat(rpc)
aid=dc.add_account().id;rpc.close();rpc.process.wait(timeout=5)
for p in root.rglob('*.db'):
  with sqlite3.connect(p) as db:
    if 'transports' not in [r[0] for r in db.execute("select name from sqlite_master where type='table'")]:continue
    addr='fx@example.invalid'
    e={'addr':addr,'imap':{'server':'','port':0,'security':'Automatic','user':'','password':''},'smtp':{'server':'','port':0,'security':'Automatic','user':'','password':''},'certificate_checks':'Automatic','oauth2':False}
    c={'addr':addr,'imap':[],'imap_user':'','imap_password':'','smtp':[],'smtp_user':'','smtp_password':'','certificate_checks':'Automatic','oauth2':False}
    db.execute('delete from transports');db.execute('insert into transports(addr,entered_param,configured_param) values(?,?,?)',(addr,json.dumps(e),json.dumps(c)))
    db.execute("insert or replace into config(keyname,value) values('configured_addr',?)",(addr,));db.commit()
rpc=Rpc(accounts_dir=str(root),rpc_server_path=server);rpc.start()
events=[]
def drain(r):
  try:
    while True: events.append(json.dumps(r.get_next_event()))
  except BaseException: pass
threading.Thread(target=drain,args=(rpc,),daemon=True).start()
print('configured',rpc.is_configured(aid),flush=True)
path=core.export_backup(rpc,aid,str(out),M)
print('export via cabang_core ok',os.path.basename(path),os.path.getsize(path),flush=True)
# failure path with marker too
try: core.export_backup(rpc,aid,'/nonexistent/x',M)
except Exception as ex: print('fail path err',str(ex)[:100],'| marker in err:',M in str(ex),flush=True)
# import with wrong and right code into a fresh disposable account
rpc.close(); rpc.process.wait(timeout=5)
root2=pathlib.Path(tempfile.mkdtemp())
rpc2=Rpc(accounts_dir=str(root2),rpc_server_path=server);rpc2.start();threading.Thread(target=drain,args=(rpc2,),daemon=True).start()
b=rpc2.add_account()
try: rpc2.import_backup(b,path,'WRONG-CODE'); print('wrong code imported?! BAD')
except Exception as ex: print('wrong code rejected:',str(ex)[:80],flush=True)
b2=rpc2.add_account()
rpc2.import_backup(b2,path,M);print('right code import ok; configured',rpc2.is_configured(b2),flush=True)
time.sleep(1)
blob='\n'.join(events)
print('events',len(events),'marker in events',M in blob)
hits=[]
for base in (root,root2,out):
  for p in base.rglob('*'):
    if p.is_file() and M.encode() in p.read_bytes(): hits.append(str(p))
print('marker in files:',hits)
sys.stdout.flush();sys.stdout.flush();os._exit(0)
