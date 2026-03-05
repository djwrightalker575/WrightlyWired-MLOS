import json
from pathlib import Path
from pydantic import BaseModel
from mlos.plugins.base import Plugin, ActionSpec, IdempotencyHint
class In(BaseModel): path:str
class Out(BaseModel): ok:bool; entries:int
class BrowserCapturePlugin(Plugin):
    name="browser_capture"
    def __init__(self, ctx): self.actions={"import_json_capture":ActionSpec("import_json_capture",In,Out,"low",IdempotencyHint(True),["jsonl"],self.imp)}
    def imp(self,d:In):
        data=json.loads(Path(d.path).read_text(encoding='utf-8'))
        return {"ok":True,"entries":len(data) if isinstance(data,list) else 1}
