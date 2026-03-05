import time
from pydantic import BaseModel
from mlos.plugins.base import Plugin, ActionSpec, IdempotencyHint

class JobIn(BaseModel): duration_s:int=1
class JobOut(BaseModel): ok:bool; status:str

class JobRunnerPlugin(Plugin):
    name="job_runner"
    def __init__(self, ctx):
        self.actions={"start_job": ActionSpec("start_job", JobIn, JobOut, "medium", IdempotencyHint(True), ["log"], self.start_job)}
    def start_job(self,d:JobIn):
        time.sleep(min(d.duration_s,2))
        return {"ok":True,"status":"completed"}
