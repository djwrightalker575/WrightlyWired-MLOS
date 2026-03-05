import json
import sqlite3
from pathlib import Path
from typing import Any

class Repo:
    def __init__(self, db_path: Path):
        self.db_path = db_path

    def conn(self):
        c = sqlite3.connect(self.db_path)
        c.row_factory = sqlite3.Row
        return c

    def create_run(self, run: dict[str, Any]) -> None:
        with self.conn() as c:
            c.execute("INSERT INTO runs(id,created_at,status,root_intent_text,taskgraph_json,summary_json) VALUES(?,?,?,?,?,?)",
                      (run["id"], run["created_at"], run["status"], run["root_intent_text"], json.dumps(run["taskgraph_json"]), json.dumps(run.get("summary_json"))))

    def update_run_status(self, run_id: str, status: str, summary: dict[str, Any] | None = None):
        with self.conn() as c:
            c.execute("UPDATE runs SET status=?, summary_json=? WHERE id=?", (status, json.dumps(summary), run_id))

    def list_runs(self):
        with self.conn() as c:
            return [dict(r) for r in c.execute("SELECT * FROM runs ORDER BY created_at DESC").fetchall()]

    def get_run(self, run_id: str):
        with self.conn() as c:
            row = c.execute("SELECT * FROM runs WHERE id=?", (run_id,)).fetchone()
            return dict(row) if row else None

    def insert_task(self, t: dict[str, Any]) -> None:
        with self.conn() as c:
            c.execute("""INSERT INTO tasks(id,run_id,parent_task_id,depends_on_json,plugin,action,input_json,risk,approval_required,idempotency_hint,status,attempts,max_attempts,created_at)
                      VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                      (t["id"], t["run_id"], t.get("parent_task_id"), json.dumps(t.get("depends_on", [])), t["plugin"], t["action"], json.dumps(t.get("input", {})), t["risk"], int(t["approval_required"]), t.get("idempotency_hint", "safe_retry"), t["status"], t.get("attempts", 0), t.get("max_attempts", 1), t["created_at"]))

    def list_tasks(self, run_id: str):
        with self.conn() as c:
            return [dict(r) for r in c.execute("SELECT * FROM tasks WHERE run_id=? ORDER BY created_at", (run_id,)).fetchall()]

    def get_task(self, task_id: str):
        with self.conn() as c:
            row = c.execute("SELECT * FROM tasks WHERE id=?", (task_id,)).fetchone()
            return dict(row) if row else None

    def update_task(self, task_id: str, **fields):
        if not fields:
            return
        keys = []
        vals = []
        for k, v in fields.items():
            if isinstance(v, (dict, list)):
                v = json.dumps(v)
            keys.append(f"{k}=?")
            vals.append(v)
        vals.append(task_id)
        with self.conn() as c:
            c.execute(f"UPDATE tasks SET {', '.join(keys)} WHERE id=?", vals)

    def log_event(self, ev: dict[str, Any]):
        with self.conn() as c:
            c.execute("INSERT INTO events(run_id,task_id,ts,level,event_type,message,data_json) VALUES(?,?,?,?,?,?,?)",
                      (ev["run_id"], ev.get("task_id"), ev["ts"], ev["level"], ev["event_type"], ev["message"], json.dumps(ev.get("data", {}))))

    def list_events(self, run_id: str, since_id: int | None = None):
        q = "SELECT * FROM events WHERE run_id=?"
        args = [run_id]
        if since_id:
            q += " AND id>?"
            args.append(since_id)
        q += " ORDER BY id"
        with self.conn() as c:
            return [dict(r) for r in c.execute(q, args).fetchall()]

    def create_approval(self, a: dict[str, Any]):
        with self.conn() as c:
            c.execute("INSERT INTO approvals(id,run_id,task_id,requested_at,status,policy_key,request_json,decision_json) VALUES(?,?,?,?,?,?,?,?)",
                      (a["id"], a["run_id"], a["task_id"], a["requested_at"], a["status"], a["policy_key"], json.dumps(a["request_json"]), json.dumps(a.get("decision_json"))))

    def list_approvals(self, status: str | None = None):
        with self.conn() as c:
            if status:
                rows = c.execute("SELECT * FROM approvals WHERE status=? ORDER BY requested_at", (status,)).fetchall()
            else:
                rows = c.execute("SELECT * FROM approvals ORDER BY requested_at DESC").fetchall()
            return [dict(r) for r in rows]

    def get_approval(self, approval_id: str):
        with self.conn() as c:
            row = c.execute("SELECT * FROM approvals WHERE id=?", (approval_id,)).fetchone()
            return dict(row) if row else None

    def decide_approval(self, approval_id: str, status: str, decision_json: dict[str, Any]):
        with self.conn() as c:
            c.execute("UPDATE approvals SET status=?, decision_json=? WHERE id=?", (status, json.dumps(decision_json), approval_id))

    def add_artifact(self, a: dict[str, Any]):
        with self.conn() as c:
            c.execute("INSERT INTO artifacts(id,run_id,task_id,kind,path,sha256,bytes,created_at) VALUES(?,?,?,?,?,?,?,?)",
                      (a["id"], a["run_id"], a.get("task_id"), a["kind"], a["path"], a.get("sha256"), a.get("bytes"), a["created_at"]))

    def list_artifacts(self, run_id: str):
        with self.conn() as c:
            return [dict(r) for r in c.execute("SELECT * FROM artifacts WHERE run_id=? ORDER BY created_at", (run_id,)).fetchall()]

    def get_artifact(self, artifact_id: str):
        with self.conn() as c:
            row = c.execute("SELECT * FROM artifacts WHERE id=?", (artifact_id,)).fetchone()
            return dict(row) if row else None

    def find_runnable_tasks(self, run_id: str):
        tasks = self.list_tasks(run_id)
        by_id = {t["id"]: t for t in tasks}
        runnable = []
        for t in tasks:
            if t["status"] != "queued":
                continue
            deps = json.loads(t["depends_on_json"])
            if all(by_id[d]["status"] == "succeeded" for d in deps):
                runnable.append(t)
        return runnable

    def recover_running_tasks(self):
        with self.conn() as c:
            rows = c.execute("SELECT * FROM tasks WHERE status='running'").fetchall()
            return [dict(r) for r in rows]
