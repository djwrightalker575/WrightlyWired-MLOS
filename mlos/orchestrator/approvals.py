from mlos.core.ids import new_id
from mlos.core.timebase import now_iso

def request_approval(repo, run_id:str, task_id:str, policy_key:str, request_json:dict):
    aid=new_id('appr')
    repo.create_approval({"id":aid,"run_id":run_id,"task_id":task_id,"requested_at":now_iso(),"status":"pending","policy_key":policy_key,"request_json":request_json})
    return aid
