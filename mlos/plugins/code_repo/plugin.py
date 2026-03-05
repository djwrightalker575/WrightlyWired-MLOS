import subprocess
from pathlib import Path
from pydantic import BaseModel
from mlos.plugins.base import Plugin, ActionSpec, IdempotencyHint
class In(BaseModel): repo:str='.'
class Out(BaseModel): ok:bool; output:str
class CodeRepoPlugin(Plugin):
    name="code_repo"
    def __init__(self, ctx): self.actions={"git_status":ActionSpec("git_status",In,Out,"low",IdempotencyHint(True),[],self.status)}
    def status(self,d:In):
        p=subprocess.run(["git","-C",d.repo,"status","--short"],capture_output=True,text=True)
        return {"ok":p.returncode==0,"output":p.stdout.strip()}
