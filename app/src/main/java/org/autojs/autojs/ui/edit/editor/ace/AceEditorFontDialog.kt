package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context
import android.graphics.Rect
import android.text.format.Formatter
import android.view.TouchDelegate
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.ui.edit.editor.ace.font.AceFontIds
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadEvent
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadSubscription
import org.autojs.autojs.ui.edit.editor.ace.font.FontFileValidationException
import org.autojs.autojs.ui.edit.editor.ace.font.FontInstallationState
import org.autojs.autojs.ui.edit.editor.ace.font.FontInstallationStatus
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

object AceEditorFontDialog {

    fun build(
        context: Context,
        currentFontId: String,
        onConfirmed: (String) -> Unit,
    ): MaterialDialog {
        val manager = AceEditorFontManager.get(context)
        val adapter = Adapter(
            context = context,
            manager = manager,
            fonts = manager.options(context),
            currentFontId = currentFontId,
            onPersistedFontDeleted = onConfirmed,
        )
        var refreshSubscription: AceFontCatalogRefreshSubscription? = null
        val dialog = MaterialDialog.Builder(context)
            .title(R.string.text_ace_editor_font)
            .options(
                listOf(
                    MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_download_all)) {
                        adapter.confirmDownloadAll()
                    },
                ),
            )
            .adapter(adapter, null)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ -> onConfirmed(adapter.selectedFontId) }
            .build()
        dialog.optionsView?.contentDescription = context.getString(R.string.text_more)
        dialog.optionsView?.post {
            val options = dialog.optionsView ?: return@post
            val parent = options.parent as? View ?: return@post
            val targetSize = (48 * context.resources.displayMetrics.density).toInt()
            val bounds = Rect().also(options::getHitRect)
            val horizontalExtra = ((targetSize - bounds.width()).coerceAtLeast(0) + 1) / 2
            val verticalExtra = ((targetSize - bounds.height()).coerceAtLeast(0) + 1) / 2
            bounds.inset(-horizontalExtra, -verticalExtra)
            parent.touchDelegate = TouchDelegate(bounds, options)
        }
        dialog.setOnShowListener {
            refreshSubscription = manager.refreshAsync { result ->
                if (!adapter.isDisposed) {
                    result.onSuccess { adapter.submit(manager.options(context)) }
                }
            }
        }
        dialog.setOnDismissListener {
            refreshSubscription?.cancel()
            adapter.dispose()
        }
        return dialog
    }

    private class Adapter(
        private val context: Context,
        private val manager: AceEditorFontManager,
        fonts: List<AceEditorFontOption>,
        currentFontId: String,
        private val onPersistedFontDeleted: (String) -> Unit,
    ) : RecyclerView.Adapter<Adapter.ViewHolder>() {

        private val choiceTint = ThemeColorHelper.getThemeColorStateList(context)
        private val downloads = linkedMapOf<String, DownloadUi>()
        private var fonts: List<AceEditorFontOption> = fonts
        private var selectedId: String = currentFontId
        private var disposed = false
        private var bulkDownload: BulkDownloadUi? = null

        val selectedFontId: String
            get() = selectedId

        val isDisposed: Boolean
            get() = disposed

        init {
            if (fonts.none { it.id == selectedId }) selectedId = AceFontIds.BUNDLED_IOSEVKA
            fonts.filter { font ->
                font.remoteFont?.artifact?.sha256 == manager.activeDownloadSha256(font.id)
            }.forEach {
                startDownload(it, selectOnSuccess = false, notifyOnSuccess = false)
            }
        }

        fun submit(newFonts: List<AceEditorFontOption>) {
            if (disposed) return
            fonts = newFonts
            if (fonts.none { it.id == selectedId }) {
                selectedId = AceFontIds.BUNDLED_IOSEVKA
            }
            notifyDataSetChanged()
        }

        fun dispose() {
            if (disposed) return
            disposed = true
            downloads.values.mapNotNull { it.subscription }.forEach { it.cancel() }
            downloads.clear()
            bulkDownload = null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.dialog_ace_editor_font_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val font = fonts[position]
            val download = downloads[font.id]
            val state = manager.installationState(font)
            val selectable = isSelectable(font, state?.status)
            holder.title.text = font.displayName
            holder.subtitle.text = font.author
            holder.subtitle.visibility = if (font.author.isBlank()) View.GONE else View.VISIBLE
            holder.status.text = statusText(font, state, download)
            holder.radioButton.buttonTintList = choiceTint
            holder.radioButton.isEnabled = selectable
            holder.radioButton.isChecked = font.id == selectedId
            holder.progress.visibility = if (download == null) View.GONE else View.VISIBLE
            holder.progress.progress = download?.percent ?: 0
            bindAction(holder.action, holder.downloadButton, font, state, download)
            val itemClick = View.OnClickListener {
                when {
                    selectable -> select(font.id)
                    download == null && font.remoteFont != null &&
                        state?.status == FontInstallationStatus.NOT_INSTALLED -> {
                        confirmDownload(font)
                    }
                }
            }
            holder.itemView.setOnClickListener(
                itemClick.takeIf {
                    selectable || download == null && font.remoteFont != null &&
                        state?.status == FontInstallationStatus.NOT_INSTALLED
                },
            )
            holder.itemView.setOnLongClickListener {
                showDetails(font)
                true
            }
        }

        override fun getItemCount(): Int = fonts.size

        private fun bindAction(
            action: TextView,
            downloadButton: ImageButton,
            font: AceEditorFontOption,
            state: FontInstallationState?,
            download: DownloadUi?,
        ) {
            val status = state?.status
            val showDownloadButton = download == null &&
                font.delivery == AceEditorFontOption.Delivery.DOWNLOADABLE &&
                status == FontInstallationStatus.NOT_INSTALLED
            val textRes = when {
                download != null -> R.string.text_cancel
                font.delivery != AceEditorFontOption.Delivery.DOWNLOADABLE -> null
                status == FontInstallationStatus.UPDATE_AVAILABLE -> R.string.text_update
                status == FontInstallationStatus.INSTALLED || status == FontInstallationStatus.REVOKED -> {
                    if (state.installed != null) R.string.text_delete else null
                }
                else -> null
            }
            downloadButton.visibility = if (showDownloadButton) View.VISIBLE else View.GONE
            ImageViewCompat.setImageTintList(downloadButton, choiceTint)
            downloadButton.setOnClickListener(if (showDownloadButton) {
                View.OnClickListener { confirmDownload(font) }
            } else {
                null
            })
            action.visibility = if (textRes == null) View.GONE else View.VISIBLE
            if (textRes == null) {
                action.setOnClickListener(null)
                return
            }
            action.setText(textRes)
            action.contentDescription = context.getString(textRes)
            action.setTextColor(choiceTint.defaultColor)
            action.setOnClickListener {
                when {
                    download != null -> {
                        manager.cancelDownload(font.id)
                    }
                    status == FontInstallationStatus.NOT_INSTALLED ||
                        status == FontInstallationStatus.UPDATE_AVAILABLE -> confirmDownload(font)
                    else -> confirmDelete(font)
                }
            }
        }

        fun confirmDownloadAll() {
            if (disposed) return
            if (bulkDownload != null) {
                ViewUtils.showToast(context, R.string.text_ace_editor_font_download_all_running)
                return
            }
            val candidates = fonts.filter { font ->
                if (font.remoteFont == null || downloads.containsKey(font.id)) return@filter false
                manager.installationState(font)?.status in setOf(
                    FontInstallationStatus.NOT_INSTALLED,
                    FontInstallationStatus.UPDATE_AVAILABLE,
                )
            }
            if (candidates.isEmpty()) {
                ViewUtils.showToast(context, R.string.text_ace_editor_font_download_all_empty)
                return
            }
            val totalBytes = candidates.sumOf { it.remoteFont?.artifact?.sizeBytes ?: 0L }
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.text_download_all)
                    .content(
                        context.getString(
                            R.string.text_ace_editor_font_download_all_confirmation,
                            candidates.size,
                            Formatter.formatShortFileSize(context, totalBytes),
                        ),
                    )
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.text_download_all)
                    .positiveColorRes(R.color.dialog_button_attraction)
                    .onPositive { _, _ -> startDownloadAll(candidates) }
                    .build()
            }
        }

        private fun startDownloadAll(candidates: List<AceEditorFontOption>) {
            if (disposed || bulkDownload != null) return
            val bulk = BulkDownloadUi(candidates.mapTo(linkedSetOf()) { it.id })
            bulkDownload = bulk
            candidates.forEach { font ->
                startDownload(
                    font,
                    selectOnSuccess = false,
                    notifyOnSuccess = false,
                    bulk = bulk,
                )
            }
        }

        private fun statusText(
            font: AceEditorFontOption,
            state: FontInstallationState?,
            download: DownloadUi?,
        ): String {
            if (font.delivery != AceEditorFontOption.Delivery.DOWNLOADABLE) {
                return context.getString(R.string.text_ace_editor_font_built_in)
            }
            val size = font.remoteFont?.artifact?.sizeBytes
                ?: state?.installed?.sizeBytes
            if (download != null) {
                return context.getString(R.string.text_downloading) + " ${download.percent}%"
            }
            val stateText = when (state?.status) {
                FontInstallationStatus.INSTALLED -> context.getString(R.string.text_installed)
                FontInstallationStatus.UPDATE_AVAILABLE -> context.getString(R.string.text_update)
                FontInstallationStatus.REVOKED -> context.getString(R.string.text_unavailable)
                else -> context.getString(R.string.text_not_installed)
            }
            return size?.let {
                context.getString(
                    R.string.text_ace_editor_font_status,
                    stateText,
                    Formatter.formatShortFileSize(context, it),
                )
            } ?: stateText
        }

        private fun select(fontId: String) {
            if (fontId == selectedId) return
            val previous = fonts.indexOfFirst { it.id == selectedId }
            selectedId = fontId
            if (previous >= 0) notifyItemChanged(previous)
            fonts.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }?.let(::notifyItemChanged)
        }

        private fun isSelectable(font: AceEditorFontOption, status: FontInstallationStatus?): Boolean {
            if (font.delivery != AceEditorFontOption.Delivery.DOWNLOADABLE) return true
            return status in setOf(
                FontInstallationStatus.INSTALLED,
                FontInstallationStatus.UPDATE_AVAILABLE,
            )
        }

        private fun confirmDownload(font: AceEditorFontOption) {
            val remote = font.remoteFont ?: return
            if (downloads.containsKey(font.id)) return
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(font.displayName)
                    .content(
                        context.getString(
                            R.string.text_ace_editor_font_download_confirmation,
                            font.displayName,
                            Formatter.formatShortFileSize(context, remote.artifact.sizeBytes),
                        ),
                    )
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.text_download)
                    .positiveColorRes(R.color.dialog_button_attraction)
                    .onPositive { _, _ -> startDownload(font) }
                    .build()
            }
        }

        private fun startDownload(
            font: AceEditorFontOption,
            selectOnSuccess: Boolean = true,
            notifyOnSuccess: Boolean = true,
            bulk: BulkDownloadUi? = null,
        ) {
            val remote = font.remoteFont ?: run {
                finishBulkDownload(bulk, font.id, successful = false)
                return
            }
            if (disposed || downloads.containsKey(font.id)) {
                finishBulkDownload(bulk, font.id, successful = false)
                return
            }
            val ui = DownloadUi(percent = 0)
            downloads[font.id] = ui
            notifyFontChanged(font.id)
            ui.subscription = try {
                manager.download(font.id, remote.artifact.sha256) { event ->
                    if (disposed) return@download
                    when (event) {
                        is FontDownloadEvent.Queued -> Unit
                        is FontDownloadEvent.Progress -> {
                            ui.percent = if (event.totalBytes <= 0L) 0 else {
                                ((event.bytesDownloaded * 100L) / event.totalBytes).toInt().coerceIn(0, 100)
                            }
                            notifyFontChanged(font.id)
                        }
                        is FontDownloadEvent.Success -> {
                            downloads.remove(font.id)
                            if (selectOnSuccess) selectedId = font.id
                            submit(manager.options(context))
                            if (notifyOnSuccess) ViewUtils.showToast(context, R.string.text_installed)
                            finishBulkDownload(bulk, font.id, successful = true)
                        }
                        is FontDownloadEvent.Cancelled -> {
                            downloads.remove(font.id)
                            notifyFontChanged(font.id)
                            finishBulkDownload(bulk, font.id, successful = false)
                        }
                        is FontDownloadEvent.Failure -> {
                            downloads.remove(font.id)
                            notifyFontChanged(font.id)
                            val message = if (containsIntegrityFailure(event.error)) {
                                context.getString(R.string.text_ace_editor_font_integrity_failed)
                            } else {
                                context.getString(
                                    R.string.text_ace_editor_font_download_failed,
                                    event.error.message ?: event.error.javaClass.simpleName,
                                )
                            }
                            if (bulk == null) ViewUtils.showToast(context, message, true)
                            finishBulkDownload(
                                bulk,
                                font.id,
                                successful = false,
                                failureMessage = message,
                            )
                        }
                    }
                }
            } catch (error: Throwable) {
                downloads.remove(font.id)
                notifyFontChanged(font.id)
                if (bulk == null) {
                    ViewUtils.showToast(
                        context,
                        context.getString(
                            R.string.text_ace_editor_font_download_failed,
                            error.message ?: error.javaClass.simpleName,
                        ),
                        true,
                    )
                }
                finishBulkDownload(
                    bulk,
                    font.id,
                    successful = false,
                    failureMessage = context.getString(
                        R.string.text_ace_editor_font_download_failed,
                        error.message ?: error.javaClass.simpleName,
                    ),
                )
                null
            }
        }

        private fun finishBulkDownload(
            bulk: BulkDownloadUi?,
            fontId: String,
            successful: Boolean,
            failureMessage: String? = null,
        ) {
            if (bulk == null || bulkDownload !== bulk || !bulk.pendingIds.remove(fontId)) return
            if (successful) bulk.succeeded++ else bulk.failed++
            if (bulk.firstFailure == null) bulk.firstFailure = failureMessage
            if (bulk.pendingIds.isNotEmpty()) return
            bulkDownload = null
            if (disposed) return
            if (bulk.failed == 0) {
                ViewUtils.showToast(
                    context,
                    context.getString(R.string.text_ace_editor_font_download_all_completed, bulk.succeeded),
                )
            } else {
                val summary = context.getString(
                        R.string.text_ace_editor_font_download_all_result,
                        bulk.succeeded,
                        bulk.failed,
                    )
                ViewUtils.showToast(
                    context,
                    bulk.firstFailure?.let { "$summary\n$it" } ?: summary,
                    true,
                )
            }
        }

        private fun confirmDelete(font: AceEditorFontOption) {
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(font.displayName)
                    .content(context.getString(R.string.text_ace_editor_font_delete_confirmation, font.displayName))
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.text_delete)
                    .positiveColorRes(R.color.dialog_button_caution)
                    .onPositive { _, _ -> delete(font) }
                    .build()
            }
        }

        private fun delete(font: AceEditorFontOption) {
            if (!manager.delete(font.id)) return
            if (selectedId == font.id) selectedId = AceFontIds.BUNDLED_IOSEVKA
            if (AceEditorFontPreferences.getId() == font.id) {
                AceEditorFontPreferences.setId(AceFontIds.BUNDLED_IOSEVKA)
                onPersistedFontDeleted(AceFontIds.BUNDLED_IOSEVKA)
            }
            submit(manager.options(context))
        }

        private fun showDetails(font: AceEditorFontOption) {
            val content = buildList {
                font.author.takeIf { it.isNotBlank() }?.let(::add)
                font.licenseName.takeIf { it.isNotBlank() }?.let(::add)
                font.remoteFont?.source?.repository?.let(::add)
            }.joinToString("\n\n")
            if (content.isBlank()) return
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(font.displayName)
                    .content(content)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_attraction)
                    .apply {
                        font.remoteFont?.licenseUrl?.let { licenseUrl ->
                            neutralText(R.string.text_ace_editor_font_license)
                            neutralColorRes(R.color.dialog_button_default)
                            onNeutral { _, _ -> IntentUtils.browse(context, licenseUrl) }
                        }
                    }
                    .build()
            }
        }

        private fun notifyFontChanged(fontId: String) {
            fonts.indexOfFirst { it.id == fontId }.takeIf { it >= 0 }?.let(::notifyItemChanged)
        }

        private fun containsIntegrityFailure(error: Throwable): Boolean {
            val seen = hashSetOf<Throwable>()
            val pending = ArrayDeque<Throwable>().apply { add(error) }
            while (pending.isNotEmpty()) {
                val current = pending.removeFirst()
                if (!seen.add(current)) continue
                if (current is FontFileValidationException) return true
                current.cause?.let(pending::add)
                current.suppressed.forEach(pending::add)
            }
            return false
        }

        private data class DownloadUi(
            var percent: Int,
            var subscription: FontDownloadSubscription? = null,
        )

        private data class BulkDownloadUi(
            val pendingIds: MutableSet<String>,
            var succeeded: Int = 0,
            var failed: Int = 0,
            var firstFailure: String? = null,
        )

        private class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val radioButton: AppCompatRadioButton = itemView.findViewById(R.id.radio_button)
            val title: TextView = itemView.findViewById(R.id.title)
            val subtitle: TextView = itemView.findViewById(R.id.subtitle)
            val status: TextView = itemView.findViewById(R.id.status)
            val progress: ProgressBar = itemView.findViewById(R.id.progress)
            val action: TextView = itemView.findViewById(R.id.action)
            val downloadButton: ImageButton = itemView.findViewById(R.id.download_button)
        }
    }
}
