CREATE TABLE IF NOT EXISTS runs (
  id TEXT PRIMARY KEY,
  created_at TEXT NOT NULL,
  status TEXT NOT NULL,
  root_intent_text TEXT NOT NULL,
  taskgraph_json TEXT NOT NULL,
  summary_json TEXT
);

CREATE TABLE IF NOT EXISTS tasks (
  id TEXT PRIMARY KEY,
  run_id TEXT NOT NULL,
  parent_task_id TEXT,
  depends_on_json TEXT NOT NULL,
  plugin TEXT NOT NULL,
  action TEXT NOT NULL,
  input_json TEXT NOT NULL,
  risk TEXT NOT NULL,
  approval_required INTEGER NOT NULL,
  idempotency_hint TEXT NOT NULL,
  status TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  max_attempts INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  started_at TEXT,
  finished_at TEXT,
  result_json TEXT,
  error_json TEXT
);

CREATE TABLE IF NOT EXISTS events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  run_id TEXT NOT NULL,
  task_id TEXT,
  ts TEXT NOT NULL,
  level TEXT NOT NULL,
  event_type TEXT NOT NULL,
  message TEXT NOT NULL,
  data_json TEXT
);

CREATE TABLE IF NOT EXISTS artifacts (
  id TEXT PRIMARY KEY,
  run_id TEXT NOT NULL,
  task_id TEXT,
  kind TEXT NOT NULL,
  path TEXT NOT NULL,
  sha256 TEXT,
  bytes INTEGER,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS approvals (
  id TEXT PRIMARY KEY,
  run_id TEXT NOT NULL,
  task_id TEXT NOT NULL,
  requested_at TEXT NOT NULL,
  status TEXT NOT NULL,
  policy_key TEXT NOT NULL,
  request_json TEXT NOT NULL,
  decision_json TEXT
);

CREATE VIRTUAL TABLE IF NOT EXISTS semantic_notes USING fts5(content);
