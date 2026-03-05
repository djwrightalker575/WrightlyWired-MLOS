import pytest
from mlos.intent.taskgraph import TaskGraph, TaskNode
from mlos.intent.validators import validate_taskgraph
from mlos.core.errors import ValidationError

def test_cycle_rejected():
    g=TaskGraph(nodes=[TaskNode(id='a',depends_on=['b'],plugin='filesystem',action='read_text'),TaskNode(id='b',depends_on=['a'],plugin='filesystem',action='read_text')])
    with pytest.raises(ValidationError): validate_taskgraph(g)

def test_missing_dep_rejected():
    g=TaskGraph(nodes=[TaskNode(id='a',depends_on=['x'],plugin='filesystem',action='read_text')])
    with pytest.raises(ValidationError): validate_taskgraph(g)
