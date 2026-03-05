from pathlib import Path
from pydantic import BaseModel
from mlos.plugins.base import Plugin, ActionSpec, IdempotencyHint
class In(BaseModel): root:str
class Out(BaseModel): ok:bool; files:int
class ArchiveIndexerPlugin(Plugin):
    name="archive_indexer"
    def __init__(self, ctx): self.actions={"scan_tree":ActionSpec("scan_tree",In,Out,"medium",IdempotencyHint(True),["manifest"],self.scan)}
    def scan(self,d:In):
        files=sum(1 for p in Path(d.root).rglob('*') if p.is_file())
        return {"ok":True,"files":files}
