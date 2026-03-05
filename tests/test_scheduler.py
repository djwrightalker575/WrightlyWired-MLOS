from mlos.orchestrator.scheduler import runnable

def test_scheduler_deps():
    tasks=[
      {'id':'a','status':'succeeded','depends_on_json':'[]'},
      {'id':'b','status':'queued','depends_on_json':'["a"]'},
      {'id':'c','status':'queued','depends_on_json':'["b"]'}
    ]
    run=runnable(tasks)
    assert [t['id'] for t in run]==['b']
