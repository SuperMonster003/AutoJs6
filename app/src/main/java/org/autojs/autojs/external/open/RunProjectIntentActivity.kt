package org.autojs.autojs.external.open

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import org.autojs.autojs.AutoJs
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.project.ProjectLauncher
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.util.UUID

/**
 * Runs a project zip handed over by a plugin (3-Block Compose folder projects, the plugin's roadmap D64): the zip
 * (a readable content Uri as the data) is unpacked into the cache and run as a project there, with that folder as
 * its working directory; the folder is removed once no script runs in it any more. There is no intent filter: the
 * plugin starts it by component, and a host without it hides the destination.
 *
 * zh-CN: 运行插件交来的工程 zip: 解到缓存目录作为工程运行 (工作目录为该目录), 运行结束后清理.
 */
class RunProjectIntentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            run()
        } catch (e: Exception) {
            e.printStackTrace()
            ViewUtils.showToast(this, R.string.edit_and_run_handle_intent_error, true)
        }
        finish()
    }

    private fun run() {
        val uri = requireNotNull(intent.data) { "No project zip" }
        require(ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) { "Not a content Uri: $uri" }
        ScriptIntents.enforceExternalUriRequestShape(intent)
        val files = ProjectArchive.read(this, uri)
        val service = AutoJs.instance.scriptEngineService
        val runs = runsDirectory(this)
        ProjectRunCleaner.install(service, runs)
        ProjectRunCleaner.sweep(service, runs)
        val dir = File(runs, UUID.randomUUID().toString())
        try {
            ProjectArchive.unpack(files, dir)
            ProjectLauncher(dir.path).launchInteractive(service, this)
        } catch (e: RuntimeException) {
            dir.deleteRecursively()
            throw e
        }
    }

    companion object {
        private const val RUNS_DIRECTORY = "project-runs"

        /** Where project zips run, one folder per run. */
        @JvmStatic
        fun runsDirectory(context: Context) = File(context.cacheDir, RUNS_DIRECTORY)
    }
}
