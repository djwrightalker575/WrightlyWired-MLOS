from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles

from mlos.core.config import load_config
from mlos.core.ids import new_id
from mlos.core.timebase import now_iso
from mlos.db.migrate import migrate
from mlos.db.repo import Repo
from mlos.db.models import RunCreate, ApprovalDecision
from mlos.intent.parser import parse_intent
from mlos.intent.validators import validate_taskgraph
from mlos.plugins.registry import PluginRegistry
from mlos.orchestrator.executor import Executor
from mlos.orchestrator.worker import Worker

cfg = load_config()
migrate(cfg.db_path, Path(__file__).resolve().parents[1] / "db" / "schema.sql")
repo = Repo(cfg.db_path)
registry = PluginRegistry({"config": cfg, "repo": repo})
executor = Executor(repo, registry, cfg)
worker = Worker(repo, executor)

@asynccontextmanager
async def lifespan(app: FastAPI):
    await worker.start()
    yield
    await worker.stop()

app = FastAPI(lifespan=lifespan)
app.mount("/static", StaticFiles(directory=str(Path(__file__).parent / "static")), name="static")

@app.get('/ui')
def ui():
    return FileResponse(Path(__file__).parent / 'static' / 'index.html')

@app.post('/api/runs')
async def create_run(req: RunCreate):
    graph = parse_intent(req.intent)
    validate_taskgraph(graph)
    run_id = new_id('run')
    repo.create_run({"id":run_id,"created_at":now_iso(),"status":"queued","root_intent_text":req.intent,"taskgraph_json":graph.model_dump()})
    for n in graph.nodes:
        repo.insert_task({"id":n.id+"_"+run_id[-4:],"run_id":run_id,"depends_on":[d+"_"+run_id[-4:] for d in n.depends_on],"plugin":n.plugin,"action":n.action,"input":n.input,"risk":n.risk,"approval_required":n.approval_required,"idempotency_hint":n.idempotency_hint,"status":"queued","max_attempts":n.max_attempts,"created_at":now_iso()})
    repo.update_run_status(run_id,'running')
    await worker.enqueue(run_id)
    return {"run_id":run_id}

@app.get('/api/runs')
def runs(): return repo.list_runs()

@app.get('/api/runs/{run_id}')
def run(run_id:str):
    r=repo.get_run(run_id)
    if not r: raise HTTPException(404)
    return r

@app.get('/api/runs/{run_id}/tasks')
def tasks(run_id:str): return repo.list_tasks(run_id)

@app.get('/api/runs/{run_id}/events')
def events(run_id:str, since_id:int|None=None): return repo.list_events(run_id, since_id)

@app.get('/api/approvals')
def approvals(status:str|None=None): return repo.list_approvals(status)

@app.post('/api/approvals/{approval_id}/decision')
async def approval_decision(approval_id:str, body:ApprovalDecision):
    ap=repo.get_approval(approval_id)
    if not ap: raise HTTPException(404)
    st='approved' if body.decision=='approve' else 'denied'
    repo.decide_approval(approval_id, st, body.model_dump())
    task=repo.get_task(ap['task_id'])
    if st=='approved':
        repo.update_task(task['id'], approval_required=0, status='queued')
        await worker.enqueue(task['run_id'])
    else:
        repo.update_task(task['id'], status='failed', error_json={"error":"approval denied"})
    return {"ok":True}

@app.get('/api/artifacts/{artifact_id}/download')
def download(artifact_id:str):
    a=repo.get_artifact(artifact_id)
    if not a: raise HTTPException(404)
    return FileResponse(a['path'])


def run():
    import uvicorn
    uvicorn.run("mlos.api.main:app", host=cfg.host, port=cfg.port, reload=False)
