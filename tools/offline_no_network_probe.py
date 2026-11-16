import sys,tempfile,os,time,json,threading
sys.path.insert(0,'/tmp/cabang/android/app/src/main/python')
import cabang_core as c
d=tempfile.mkdtemp()
rpc=c.open_rpc(d,'/tmp/v/bin/deltachat-rpc-server')
c.start_configured_io(rpc)
print(c.snapshot_json(rpc)[:200],flush=True)
time.sleep(3)
print("accounts",rpc.get_all_account_ids(),flush=True)
os._exit(0)
