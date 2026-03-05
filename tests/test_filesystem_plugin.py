import pytest
from pathlib import Path
from mlos.plugins.filesystem.plugin import FilesystemPlugin

class C: workspace_root=Path('runtime/workspace_test')

def test_traversal_blocked(tmp_path):
    c=C(); c.workspace_root=tmp_path
    p=FilesystemPlugin({'config':c})
    with pytest.raises(ValueError): p.write_text(type('x',(object,),{'path':'../bad.txt','text':'x'})())
