import hashlib, json, shutil
from pathlib import Path
from pydantic import BaseModel
from mlos.plugins.base import Plugin, ActionSpec, IdempotencyHint

class WriteTextIn(BaseModel): path:str; text:str
class ReadTextIn(BaseModel): path:str
class FSOut(BaseModel): ok:bool; path:str|None=None; text:str|None=None; sha256:str|None=None

class FilesystemPlugin(Plugin):
    name="filesystem"
    def __init__(self, ctx):
        self.root = Path(ctx["config"].workspace_root).resolve()
        self.actions={
          "write_text": ActionSpec("write_text", WriteTextIn, FSOut, "medium", IdempotencyHint(True), ["file"], self.write_text),
          "read_text": ActionSpec("read_text", ReadTextIn, FSOut, "low", IdempotencyHint(True), [], self.read_text),
        }
    def _safe(self,p:str)->Path:
        rp=(self.root/p).resolve()
        if not str(rp).startswith(str(self.root)):
            raise ValueError("path traversal blocked")
        return rp
    def write_text(self, d:WriteTextIn):
        p=self._safe(d.path); p.parent.mkdir(parents=True, exist_ok=True)
        tmp=p.with_suffix(p.suffix+".tmp")
        tmp.write_text(d.text, encoding="utf-8"); tmp.replace(p)
        h=hashlib.sha256(p.read_bytes()).hexdigest()
        return {"ok":True,"path":str(p),"sha256":h}
    def read_text(self,d:ReadTextIn):
        p=self._safe(d.path)
        return {"ok":True,"path":str(p),"text":p.read_text(encoding='utf-8')}
