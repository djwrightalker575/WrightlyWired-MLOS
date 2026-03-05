from mlos.intent.taskgraph import TaskGraph, TaskNode


def parse_intent(intent: str) -> TaskGraph:
    text = intent.lower().strip()
    if text.startswith("run shell command"):
        cmd = intent.split("run shell command", 1)[1].strip() or "echo hi"
        node = TaskNode(id="task_001", plugin="shell", action="run", input={"command": ["bash", "-lc", cmd]}, risk="high", approval_required=True, max_attempts=1, idempotency_hint="unsafe_retry")
    else:
        node = TaskNode(id="task_001", plugin="filesystem", action="write_text", input={"path": "hello.txt", "text": "hello world"}, risk="medium", approval_required=False, max_attempts=1)
    return TaskGraph(meta={"intent": intent}, nodes=[node])
