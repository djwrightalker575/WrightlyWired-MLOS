import json, hashlib
from pathlib import Path
from mlos.core.timebase import now_iso
from mlos.core.ids import new_id
from mlos.core.logging import mirror_log
from mlos.orchestrator.approvals import request_approval

class Executor:
    def __init__(self, repo, registry, config):
        self.repo=repo; self.registry=registry; self.config=config

    def recover(self):
        for t in self.repo.recover_running_tasks():
            status = "queued" if t["idempotency_hint"] == "safe_retry" else "waiting_approval"
            self.repo.update_task(t["id"], status=status, error_json={"error":"interrupted"})

    def run_once(self, run_id:str)->bool:
        tasks=self.repo.find_runnable_tasks(run_id)
        if not tasks:
            return False
        t=tasks[0]
        self.repo.update_task(t['id'], status='running', started_at=now_iso(), attempts=t['attempts']+1)
        self.repo.log_event({"run_id":run_id,"task_id":t['id'],"ts":now_iso(),"level":"info","event_type":"task_running","message":"task started"})
        if t['approval_required']:
            existing=[a for a in self.repo.list_approvals('pending') if a['task_id']==t['id']]
            if existing:
                self.repo.update_task(t['id'], status='waiting_approval')
                return True
            request_approval(self.repo, run_id, t['id'], f"{t['plugin']}.{t['action']}", {"input": t['input_json']})
            self.repo.update_task(t['id'], status='waiting_approval')
            self.repo.log_event({"run_id":run_id,"task_id":t['id'],"ts":now_iso(),"level":"warn","event_type":"approval_needed","message":"approval requested"})
            return True
        try:
            result=self.registry.dispatch(t['plugin'], t['action'], json.loads(t['input_json']))
            self.repo.update_task(t['id'], status='succeeded', finished_at=now_iso(), result_json=result)
            self.repo.log_event({"run_id":run_id,"task_id":t['id'],"ts":now_iso(),"level":"info","event_type":"task_succeeded","message":"task succeeded","data":result})
            lp=Path(self.config.logs_dir)/f"{run_id}.log"; mirror_log(lp,f"{t['id']} succeeded")
            if result.get('path'):
                p=Path(result['path'])
                if p.exists():
                    self.repo.add_artifact({"id":new_id('art'),"run_id":run_id,"task_id":t['id'],"kind":"file","path":str(p),"sha256":hashlib.sha256(p.read_bytes()).hexdigest(),"bytes":p.stat().st_size,"created_at":now_iso()})
        except Exception as e:
            attempts=t['attempts']+1
            if attempts < t['max_attempts']:
                self.repo.update_task(t['id'], status='queued', error_json={"error":str(e)})
            else:
                self.repo.update_task(t['id'], status='failed', finished_at=now_iso(), error_json={"error":str(e)})
            self.repo.log_event({"run_id":run_id,"task_id":t['id'],"ts":now_iso(),"level":"error","event_type":"task_failed","message":str(e)})
        return True
