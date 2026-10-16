package org.autojs.autojs.external.open

import android.content.ContentResolver
import android.os.Bundle
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.ui.BaseActivity
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.io.IOException

/**
 * Imports a project zip handed over by a plugin (3-Block Compose folder projects, the plugin's roadmap D64) into a
 * folder of the scripts directory named after the zip. When that folder exists the user picks: overwrite (the zip's
 * files replace the same paths, other files of the folder stay), save as a new folder (`name-2`, ...), or cancel.
 * There is no intent filter: the plugin starts it by component, and a host without it hides the destination.
 *
 * zh-CN: 把插件交来的工程 zip 导入脚本目录下的同名文件夹; 已存在时询问覆盖, 另存为新文件夹或取消.
 */
class ImportProjectIntentActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val files = try {
            val uri = requireNotNull(intent.data) { "No project zip" }
            require(ContentResolver.SCHEME_CONTENT.equals(uri.scheme, ignoreCase = true)) { "Not a content Uri: $uri" }
            ScriptIntents.enforceExternalUriRequestShape(intent)
            ProjectArchive.read(this, uri) to ProjectArchive.folderNameOf(this, uri)
        } catch (e: Exception) {
            failed(e)
            return
        }
        val (content, name) = files
        val parent = File(Pref.getScriptDirPath())
        val target = File(parent, name)
        if (!target.exists()) {
            importInto(content, target)
            return
        }
        MaterialDialog.Builder(this)
            .title(name)
            .content(R.string.confirm_overwrite_directory)
            .positiveText(R.string.text_overwrite)
            .neutralText(R.string.dialog_button_save_as)
            .negativeText(R.string.dialog_button_cancel)
            .onPositive { _, _ -> importInto(content, target) }
            .onNeutral { _, _ -> importInto(content, ProjectArchive.freeFolder(parent, name)) }
            .onNegative { _, _ -> finish() }
            .cancelListener { finish() }
            .show()
    }

    private fun importInto(files: Map<String, ByteArray>, dir: File) {
        try {
            if (dir.exists() && !dir.isDirectory) throw IOException("Not a folder: $dir")
            ProjectArchive.unpack(files, dir)
            dir.parentFile?.let { parent -> Explorers.workspace().notifyChildrenChanged(ExplorerDirPage(ScriptFile(parent.path), null)) }
            ViewUtils.showToast(this, R.string.text_import_succeed)
        } catch (e: Exception) {
            failed(e)
            return
        }
        finish()
    }

    private fun failed(e: Exception) {
        e.printStackTrace()
        ViewUtils.showToast(this, R.string.edit_and_run_handle_intent_error, true)
        finish()
    }
}
