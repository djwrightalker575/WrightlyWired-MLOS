def recover_task_status(task: dict) -> str:
    return "queued" if task.get("idempotency_hint") == "safe_retry" else "waiting_approval"
