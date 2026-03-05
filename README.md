# WrightlyWired MLOS

## Setup
```bash
python -m venv .venv && source .venv/bin/activate && pip install -e .
```

## Run
```bash
./scripts/dev_run.sh
```
- API: http://127.0.0.1:8000
- UI: http://127.0.0.1:8000/ui

## CLI
```bash
./scripts/mlos run "write a file hello world"
./scripts/mlos runs
./scripts/mlos run-status <RUN_ID>
```

## Tests
```bash
pytest -q
```
