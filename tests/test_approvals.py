import asyncio
from pathlib import Path
from mlos.core.config import Config
from mlos.db.migrate import migrate
from mlos.db.repo import Repo
from mlos.plugins.registry import PluginRegistry
from mlos.orchestrator.executor import Executor
from mlos.orchestrator.worker import Worker
from mlos.core.timebase import now_iso


def test_approval_gate(tmp_path):
    cfg=Config(runtime_dir=tmp_path/'runtime',db_path=tmp_path/'runtime/state/mlos.sqlite',logs_dir=tmp_path/'runtime/logs',artifacts_dir=tmp_path/'runtime/artifacts',workspace_root=tmp_path/'runtime/workspace')
    cfg.ensure_dirs(); migrate(cfg.db_path, Path('mlos/db/schema.sql'))
    repo=Repo(cfg.db_path); reg=PluginRegistry({'config':cfg,'repo':repo}); ex=Executor(repo,reg,cfg)
    repo.create_run({'id':'run1','created_at':now_iso(),'status':'running','root_intent_text':'x','taskgraph_json':{'nodes':[]}})
    repo.insert_task({'id':'t1','run_id':'run1','depends_on':[],'plugin':'shell','action':'run','input':{'command':['bash','-lc','echo hi']},'risk':'high','approval_required':True,'idempotency_hint':'unsafe_retry','status':'queued','max_attempts':1,'created_at':now_iso()})
    assert ex.run_once('run1')
    t=repo.get_task('t1')
    assert t['status']=='waiting_approval'
