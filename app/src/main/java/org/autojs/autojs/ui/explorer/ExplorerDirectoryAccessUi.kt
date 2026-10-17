package org.autojs.autojs.ui.explorer

import android.content.pm.PackageManager
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.model.explorer.ExplorerDirectoryAccessException
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.PrivilegedAccessMode
import org.autojs.autojs.permission.AllFilesAccessPermission
import org.autojs.autojs.permission.ShizukuPermission
import org.autojs.autojs.pio.PFile
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import rikka.shizuku.Shizuku

/** Permission requests stay explicit; a failed list operation never silently starts su. */
internal class ExplorerDirectoryAccessUi(private val explorer: ExplorerView) {
    private val context get() = explorer.context
    private var awaitingAllFilesAt: String? = null
    private var awaitingShizukuAt: String? = null
    private var permissionListener: Shizuku.OnRequestPermissionResultListener? = null
    private var dialog: MaterialDialog? = null

    fun showOptions() {
        dialog?.dismiss()
        val target = explorer.currentPageState.page?.path ?: return
        dialog = MaterialDialog.Builder(context)
            .title(R.string.text_explorer_access_options)
            .content(target + "\n\n" + context.getString(R.string.text_explorer_access_hint))
            .items(
                context.getString(R.string.text_explorer_access_normal),
                context.getString(R.string.text_all_files_access),
                context.getString(R.string.text_explorer_access_shizuku),
                context.getString(R.string.text_explorer_access_root),
            )
            .itemsCallback { _, _, which, _ ->
                awaitingAllFilesAt = null
                awaitingShizukuAt = null
                removePermissionListener()
                when (which) {
                    0 -> explorer.setDirectoryAccessMode(null)
                    1 -> {
                        awaitingAllFilesAt = target
                        if (!AllFilesAccessPermission(context).request()) awaitingAllFilesAt = null
                    }
                    2 -> requestShizuku(target)
                    3 -> explorer.setDirectoryAccessMode(PrivilegedAccessMode.ROOT)
                }
            }
            .negativeText(R.string.dialog_button_cancel)
            .showAdaptive()
    }

    private fun requestShizuku(target: String) {
        if (ShizukuPermission(context).has()) {
            explorer.setDirectoryAccessMode(PrivilegedAccessMode.SHIZUKU)
            return
        }
        awaitingShizukuAt = target
        if (!WrappedShizuku.isRunning() || Shizuku.isPreV11()) {
            ShizukuPermission(context).request()
            return
        }
        removePermissionListener()
        permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_CODE) return@OnRequestPermissionResultListener
            explorer.post {
                removePermissionListener()
                val pending = awaitingShizukuAt
                awaitingShizukuAt = null
                if (grantResult == PackageManager.PERMISSION_GRANTED && pending == explorer.currentPageState.page?.path && explorer.isAttachedToWindow) {
                    explorer.setDirectoryAccessMode(PrivilegedAccessMode.SHIZUKU)
                }
            }
        }.also(Shizuku::addRequestPermissionResultListener)
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }.onFailure {
            removePermissionListener()
            ShizukuPermission(context).request()
        }
    }

    fun onWindowFocusGained() {
        val target = explorer.currentPageState.page?.path
        if (awaitingAllFilesAt != null && AllFilesAccessPermission(context).has()) {
            val pending = awaitingAllFilesAt
            awaitingAllFilesAt = null
            if (pending == target) explorer.setDirectoryAccessMode(null)
        }
        if (awaitingShizukuAt != null && ShizukuPermission(context).has()) {
            val pending = awaitingShizukuAt
            awaitingShizukuAt = null
            removePermissionListener()
            if (pending == target) explorer.setDirectoryAccessMode(PrivilegedAccessMode.SHIZUKU)
        }
    }

    fun detach() {
        removePermissionListener()
        dialog?.dismiss()
        dialog = null
        awaitingAllFilesAt = null
        awaitingShizukuAt = null
    }

    private fun removePermissionListener() {
        permissionListener?.let(Shizuku::removeRequestPermissionResultListener)
        permissionListener = null
    }

    companion object {
        private const val REQUEST_CODE = 0x6458

        fun errorMessage(explorer: ExplorerView, error: Throwable): String {
            val context = explorer.context
            val path = explorer.currentPageState.page?.path.orEmpty()
            val missing = (error as? ExplorerDirectoryAccessException)?.reason == ExplorerDirectoryAccessException.Reason.NOT_FOUND
            return context.getString(if (missing) R.string.text_explorer_access_missing else R.string.text_explorer_access_failed, path) +
                if (!missing && ExplorerStorageCatalog.isAndroidRestrictedPath(path)) {
                    "\n\n" + context.getString(R.string.text_explorer_access_restricted)
                } else if (error !is ExplorerDirectoryAccessException) {
                    error.message?.let { "\n\n$it" }.orEmpty()
                } else ""
        }

        fun showFileInfo(explorer: ExplorerView, item: ExplorerItem) {
            val context = explorer.context
            MaterialDialog.Builder(context)
                .title(item.name)
                .content(listOf(
                    item.path,
                    PFiles.formatSizeWithUnit(item.size),
                    PFile.getFullDateString(item.lastModified()),
                    context.getString(R.string.text_explorer_privileged_file_hint),
                ).joinToString("\n\n"))
                .neutralText(R.string.text_copy_path)
                .onNeutral { _, _ ->
                    ClipboardUtils.setClip(context, item.path)
                    ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
                }
                .positiveText(R.string.dialog_button_dismiss)
                .showAdaptive()
        }
    }
}
