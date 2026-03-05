#!/usr/bin/env bash
set -euo pipefail
python -m uvicorn mlos.api.main:app --host 127.0.0.1 --port 8000
