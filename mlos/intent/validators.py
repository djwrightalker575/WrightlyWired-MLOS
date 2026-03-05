from mlos.core.errors import ValidationError
from mlos.intent.taskgraph import TaskGraph


def validate_taskgraph(graph: TaskGraph) -> None:
    ids = {n.id for n in graph.nodes}
    if len(ids) != len(graph.nodes):
        raise ValidationError("duplicate task ids")
    for n in graph.nodes:
        for d in n.depends_on:
            if d not in ids:
                raise ValidationError(f"missing dependency {d}")
    visiting, visited = set(), set()
    by_id = {n.id: n for n in graph.nodes}

    def dfs(node_id: str):
        if node_id in visiting:
            raise ValidationError("cycle detected")
        if node_id in visited:
            return
        visiting.add(node_id)
        for dep in by_id[node_id].depends_on:
            dfs(dep)
        visiting.remove(node_id)
        visited.add(node_id)

    for n in graph.nodes:
        dfs(n.id)
