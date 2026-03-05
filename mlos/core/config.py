from dataclasses import dataclass
from pathlib import Path


@dataclass
class Config:
    base_dir: Path = Path.cwd()
    runtime_dir: Path = Path("runtime")
    db_path: Path = Path("runtime/state/mlos.sqlite")
    logs_dir: Path = Path("runtime/logs")
    artifacts_dir: Path = Path("runtime/artifacts")
    workspace_root: Path = Path("runtime/workspace")
    host: str = "127.0.0.1"
    port: int = 8000

    def ensure_dirs(self) -> None:
        for d in [self.runtime_dir, self.db_path.parent, self.logs_dir, self.artifacts_dir, self.workspace_root]:
            d.mkdir(parents=True, exist_ok=True)


def load_config() -> Config:
    cfg = Config()
    cfg.ensure_dirs()
    return cfg
