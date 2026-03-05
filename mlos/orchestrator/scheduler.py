import json

def runnable(tasks: list[dict]) -> list[dict]:
    by = {t['id']: t for t in tasks}
    out=[]
    for t in tasks:
        if t['status']!='queued':
            continue
        deps=json.loads(t['depends_on_json'])
        if all(by[d]['status']=='succeeded' for d in deps):
            out.append(t)
    return out
