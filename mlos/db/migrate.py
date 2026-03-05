import sqlite3
from pathlib import Path


def migrate(db_path: Path, schema_path: Path) -> None:
    conn = sqlite3.connect(db_path)
    try:
        schema = schema_path.read_text(encoding="utf-8")
        conn.executescript(schema)
        conn.commit()
    finally:
        conn.close()
