import pytest
from pathlib import Path
from mlos.plugins.shell.plugin import ShellPlugin, RunIn

class C: workspace_root=Path('runtime/workspace_test')

def test_forbidden_command_blocked(tmp_path):
    c=C(); c.workspace_root=tmp_path
    p=ShellPlugin({'config':c})
    with pytest.raises(ValueError): p.run(RunIn(command=['bash','-lc','rm -rf /']))
