package org.autojs.autojs.ui.explorer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginIndexSource
import org.autojs.autojs.core.plugin.center.PluginTrustManager
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.IntentUtils.ToastExceptionHolder
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs6.R
import java.util.concurrent.atomic.AtomicBoolean

internal object ExplorerDocumentPreviewerPluginUi {

    private val dialogShowing = AtomicBoolean(false)

    fun showUnavailableDialog(context: Context, item: ExplorerItem) {
        val spec = specFor(item) ?: return
        if (!dialogShowing.compareAndSet(false, true)) return
        val applicationInfo = findApplicationInfo(context, spec.packageName)
        val displayName = applicationInfo
            ?.runCatching { loadLabel(context.packageManager).toString() }
            ?.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: context.getString(spec.nameRes)
        val availability = ExplorerDocumentPreviewerAvailabilityPolicy.resolve(
            installed = applicationInfo != null,
            enabled = applicationInfo?.enabled == true &&
                PluginEnableStore.isEnabled(
                    context.applicationContext,
                    spec.packageName,
                    true,
                ),
            authorized = applicationInfo != null &&
                PluginTrustManager.isAuthorized(context.applicationContext, spec.packageName),
        )
        val content = when (availability) {
            ExplorerDocumentPreviewerAvailability.MISSING -> context.getString(
                R.string.error_plugin_not_found_in_installed_apps,
                displayName,
            )
            ExplorerDocumentPreviewerAvailability.DISABLED -> context.getString(
                R.string.error_plugin_is_not_enabled_in_plugin_center,
                displayName,
            )
            ExplorerDocumentPreviewerAvailability.UNAUTHORIZED -> context.getString(
                R.string.error_plugin_is_not_authorized_in_plugin_center,
                displayName,
            )
            ExplorerDocumentPreviewerAvailability.UNAVAILABLE -> context.getString(
                R.string.hint_plugin_upgrade_required_due_to_protocol_mismatch,
            )
        }
        try {
            MaterialDialog.Builder(context)
                .title(displayName)
                .content(content)
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .neutralText(R.string.text_open_by_other_apps)
                .neutralColorRes(R.color.dialog_button_hint)
                .positiveText(R.string.text_plugin_center)
                .positiveColorRes(R.color.dialog_button_attraction)
                .widgetThemeColor()
                .onNeutral { _, _ -> openWithOtherApps(context, item, spec.mimeType) }
                .onPositive { _, _ ->
                    PluginCenterActivity.launch(
                        context = context,
                        sourceScope = PluginIndexSource.OFFICIAL,
                        initialQuery = spec.packageName,
                    )
                }
                .dismissListener { dialogShowing.set(false) }
                .show()
        } catch (error: RuntimeException) {
            dialogShowing.set(false)
            throw error
        }
    }

    /** Per-type limit for opening a document straight out of an archive: EPUB books are large containers. */
    fun maximumArchiveDocumentBytes(previewerType: FileUtils.PreviewerType?): Long = when (previewerType) {
        FileUtils.PreviewerType.EPUB -> MAX_ARCHIVE_EPUB_BYTES
        FileUtils.PreviewerType.HTML, FileUtils.PreviewerType.MARKDOWN, null -> MAX_ARCHIVE_DOCUMENT_BYTES
    }

    private fun specFor(item: ExplorerItem): PreviewerSpec? = when (item.type.previewerType) {
        FileUtils.PreviewerType.HTML -> PreviewerSpec(
            packageName = HTML_PREVIEWER_PACKAGE,
            nameRes = R.string.plugin_html_previewer_name,
            mimeType = MIME_HTML,
        )
        FileUtils.PreviewerType.MARKDOWN -> PreviewerSpec(
            packageName = MARKDOWN_PREVIEWER_PACKAGE,
            nameRes = R.string.plugin_markdown_previewer_name,
            mimeType = MIME_MARKDOWN,
        )
        FileUtils.PreviewerType.EPUB -> PreviewerSpec(
            packageName = THREE_FOLIO_EPUB_PACKAGE,
            nameRes = R.string.plugin_three_folio_epub_name,
            mimeType = MIME_EPUB,
        )
        null -> null
    }

    private fun openWithOtherApps(context: Context, item: ExplorerItem, mimeType: String): Boolean {
        val uri = IntentUtils.getUriOfFile(context, item.path, AppFileProvider.AUTHORITY)
        val target = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = ClipData.newRawUri(item.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(target, context.getString(R.string.text_open_by_other_apps))
            .startSafely(context, true) {
                ToastExceptionHolder(
                    context,
                    R.string.error_no_applications_available_for_viewing_this_file,
                ).show()
            }
    }

    private fun findApplicationInfo(context: Context, packageName: String): ApplicationInfo? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(
                        PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.MATCH_DISABLED_COMPONENTS,
                )
            }
        }.getOrNull()

    private data class PreviewerSpec(
        val packageName: String,
        val nameRes: Int,
        val mimeType: String,
    )

    private const val HTML_PREVIEWER_PACKAGE =
        "io.github.supermonster003.autojs6.plugin.htmlpreviewer"
    private const val MARKDOWN_PREVIEWER_PACKAGE =
        "io.github.supermonster003.autojs6.plugin.markdownpreviewer"
    private const val THREE_FOLIO_EPUB_PACKAGE =
        "io.github.supermonster003.autojs6.plugin.three.folio.epub"
    private const val MIME_HTML = "text/html"
    private const val MIME_MARKDOWN = "text/markdown"
    private const val MIME_EPUB = "application/epub+zip"
    private const val MAX_ARCHIVE_DOCUMENT_BYTES = 8L * 1024L * 1024L
    private const val MAX_ARCHIVE_EPUB_BYTES = 256L * 1024L * 1024L
}

internal enum class ExplorerDocumentPreviewerAvailability {
    MISSING,
    DISABLED,
    UNAUTHORIZED,
    UNAVAILABLE,
}

internal object ExplorerDocumentPreviewerAvailabilityPolicy {

    fun resolve(
        installed: Boolean,
        enabled: Boolean,
        authorized: Boolean,
    ): ExplorerDocumentPreviewerAvailability = when {
        !installed -> ExplorerDocumentPreviewerAvailability.MISSING
        !enabled -> ExplorerDocumentPreviewerAvailability.DISABLED
        !authorized -> ExplorerDocumentPreviewerAvailability.UNAUTHORIZED
        else -> ExplorerDocumentPreviewerAvailability.UNAVAILABLE
    }
}
