from pathlib import Path
from pydantic import BaseModel
from mlos.plugins.base import Plugin, ActionSpec, IdempotencyHint
class In(BaseModel): path:str
class Out(BaseModel): ok:bool; exists:bool
class MediaToolsPlugin(Plugin):
    name="media_tools"
    def __init__(self, ctx): self.actions={"audio_probe":ActionSpec("audio_probe",In,Out,"medium",IdempotencyHint(True),[],self.probe)}
    def probe(self,d:In): return {"ok":True,"exists":Path(d.path).exists()}
