import sqlite3, json
from pathlib import Path
from pydantic import BaseModel
from mlos.plugins.base import Plugin, ActionSpec, IdempotencyHint

class QueryIn(BaseModel): sql:str; params:list=[]
class QueryOut(BaseModel): ok:bool; rows:list

class SqliteStorePlugin(Plugin):
    name="sqlite_store"
    def __init__(self, ctx):
        self.db=Path(ctx["config"].db_path)
        self.actions={"query": ActionSpec("query", QueryIn, QueryOut, "low", IdempotencyHint(True), [], self.query)}
    def query(self,d:QueryIn):
        with sqlite3.connect(self.db) as c:
            cur=c.execute(d.sql,d.params)
            rows=[list(r) for r in cur.fetchall()]
        return {"ok":True,"rows":rows}
