from pydantic import BaseModel

class RunCreate(BaseModel):
    intent: str

class ApprovalDecision(BaseModel):
    decision: str
    note: str | None = None
