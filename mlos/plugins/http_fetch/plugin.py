import urllib.request
from pydantic import BaseModel
from mlos.plugins.base import Plugin, ActionSpec, IdempotencyHint
class In(BaseModel): url:str
class Out(BaseModel): ok:bool; status_code:int
class HttpFetchPlugin(Plugin):
    name="http_fetch"
    def __init__(self, ctx): self.actions={"head":ActionSpec("head",In,Out,"medium",IdempotencyHint(True),[],self.head)}
    def head(self,d:In):
        req=urllib.request.Request(d.url,method='HEAD')
        with urllib.request.urlopen(req,timeout=5) as r:
            code=r.status
        return {"ok":200<=code<400,"status_code":code}
