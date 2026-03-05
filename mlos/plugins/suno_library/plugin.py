from pydantic import BaseModel
from mlos.plugins.base import Plugin, ActionSpec, IdempotencyHint
class In(BaseModel): records:list[dict]
class Out(BaseModel): ok:bool; songs:int
class SunoLibraryPlugin(Plugin):
    name="suno_library"
    def __init__(self, ctx): self.actions={"extract_songs":ActionSpec("extract_songs",In,Out,"medium",IdempotencyHint(True),["csv"],self.extract)}
    def extract(self,d:In): return {"ok":True,"songs":len(d.records)}
