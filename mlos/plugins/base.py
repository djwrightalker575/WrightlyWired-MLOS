from dataclasses import dataclass
from typing import Any, Callable
from pydantic import BaseModel

@dataclass
class IdempotencyHint:
    safe_retry: bool = True

@dataclass
class ActionSpec:
    name: str
    input_model: type[BaseModel]
    output_model: type[BaseModel]
    risk: str
    idempotency: IdempotencyHint
    artifact_kinds: list[str]
    handler: Callable[..., dict[str, Any]]

class Plugin:
    name: str
    actions: dict[str, ActionSpec]

    def get_action(self, action: str) -> ActionSpec:
        return self.actions[action]
