import asyncio

class Worker:
    def __init__(self, repo, executor):
        self.repo=repo; self.executor=executor; self.queue=asyncio.Queue(); self._task=None; self.running=False

    async def start(self):
        self.running=True
        self.executor.recover()
        self._task=asyncio.create_task(self.loop())

    async def stop(self):
        self.running=False
        if self._task:
            self._task.cancel()

    async def enqueue(self, run_id:str):
        await self.queue.put(run_id)

    async def loop(self):
        while self.running:
            run_id=await self.queue.get()
            progressed=True
            while progressed:
                progressed=self.executor.run_once(run_id)
            tasks=self.repo.list_tasks(run_id)
            statuses={t['status'] for t in tasks}
            if statuses == {'succeeded'}:
                self.repo.update_run_status(run_id,'succeeded',{"tasks":len(tasks)})
            elif 'failed' in statuses:
                self.repo.update_run_status(run_id,'failed',{"tasks":len(tasks)})
            else:
                self.repo.update_run_status(run_id,'running',{"tasks":len(tasks)})
