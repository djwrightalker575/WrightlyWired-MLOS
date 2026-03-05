from pydantic import BaseModel, Field

class TaskNode(BaseModel):
    id: str
    depends_on: list[str] = Field(default_factory=list)
    plugin: str
    action: str
    input: dict = Field(default_factory=dict)
    risk: str = "low"
    approval_required: bool = False
    max_attempts: int = 1
    idempotency_hint: str = "safe_retry"

class TaskGraph(BaseModel):
    meta: dict = Field(default_factory=dict)
    nodes: list[TaskNode]
