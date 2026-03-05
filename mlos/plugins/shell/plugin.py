import subprocess
from pathlib import Path
from pydantic import BaseModel, Field
from mlos.plugins.base import Plugin, ActionSpec, IdempotencyHint

FORBIDDEN = ["rm -rf", "sudo", "mkfs", "dd "]

class RunIn(BaseModel):
    command:list[str]=Field(default_factory=list)
    cwd:str|None=None
    timeout_s:int=30
class RunOut(BaseModel): ok:bool; exit_code:int; stdout:str; stderr:str

class ShellPlugin(Plugin):
    name="shell"
    def __init__(self, ctx):
        self.root=Path(ctx["config"].workspace_root).resolve()
        self.actions={"run": ActionSpec("run", RunIn, RunOut, "high", IdempotencyHint(False), ["log"], self.run)}
    def run(self,d:RunIn):
        cmd=" ".join(d.command)
        if any(x in cmd for x in FORBIDDEN):
            raise ValueError("forbidden command")
        cwd=Path(d.cwd).resolve() if d.cwd else self.root
        if not str(cwd).startswith(str(self.root)):
            raise ValueError("cwd outside workspace")
        p=subprocess.run(d.command,cwd=str(cwd),capture_output=True,text=True,timeout=d.timeout_s)
        return {"ok":p.returncode==0,"exit_code":p.returncode,"stdout":p.stdout,"stderr":p.stderr}
