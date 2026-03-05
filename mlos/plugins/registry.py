from mlos.plugins.filesystem.plugin import FilesystemPlugin
from mlos.plugins.shell.plugin import ShellPlugin
from mlos.plugins.http_fetch.plugin import HttpFetchPlugin
from mlos.plugins.browser_capture.plugin import BrowserCapturePlugin
from mlos.plugins.sqlite_store.plugin import SqliteStorePlugin
from mlos.plugins.archive_indexer.plugin import ArchiveIndexerPlugin
from mlos.plugins.suno_library.plugin import SunoLibraryPlugin
from mlos.plugins.media_tools.plugin import MediaToolsPlugin
from mlos.plugins.code_repo.plugin import CodeRepoPlugin
from mlos.plugins.job_runner.plugin import JobRunnerPlugin

class PluginRegistry:
    def __init__(self, context: dict):
        self.plugins = {}
        for cls in [FilesystemPlugin, ShellPlugin, HttpFetchPlugin, BrowserCapturePlugin, SqliteStorePlugin, ArchiveIndexerPlugin, SunoLibraryPlugin, MediaToolsPlugin, CodeRepoPlugin, JobRunnerPlugin]:
            p = cls(context)
            self.plugins[p.name] = p

    def action_spec(self, plugin: str, action: str):
        return self.plugins[plugin].get_action(action)

    def dispatch(self, plugin: str, action: str, payload: dict):
        spec = self.action_spec(plugin, action)
        data = spec.input_model(**payload)
        out = spec.handler(data)
        return spec.output_model(**out).model_dump()
