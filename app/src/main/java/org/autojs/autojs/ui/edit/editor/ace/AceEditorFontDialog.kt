package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.ui.edit.editor.ace.font.AceFontIds
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadEvent
import org.autojs.autojs.ui.edit.editor.ace.font.FontDownloadSubscription
import org.autojs.autojs.ui.edit.editor.ace.font.FontFileValidationException
import org.autojs.autojs.ui.edit.editor.ace.font.FontInstallationState
import org.autojs.autojs.ui.edit.editor.ace.font.FontInstallationStatus
import org.autojs.autojs.ui.edit.editor.ace.font.RemoteFont
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DialogUtils.choiceWidgetThemeColor
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import kotlin.math.roundToInt

object AceEditorFontDialog {

    fun build(
        context: Context,
        currentFontId: String,
        onSelected: (String) -> Unit,
    ): MaterialDialog = build(
        context = context,
        currentFontId = currentFontId,
        onSelected = onSelected,
        onPreviewRequested = null,
    )

    fun build(
        context: Context,
        currentFontId: String,
        onSelected: (String) -> Unit,
        onPreviewRequested: ((String) -> Unit)?,
    ): MaterialDialog {
        val manager = AceEditorFontManager.get(context)
        val adapter = Adapter(
            context = context,
            manager = manager,
            fonts = manager.options(context, currentFontId),
            currentFontId = currentFontId,
            onPersistedFontDeleted = onSelected,
        )
        var refreshSubscription: AceFontCatalogRefreshSubscription? = null
        var swipeActionsAttached = false
        val builder = MaterialDialog.Builder(context)
            .title(R.string.text_ace_editor_font)
            .options(
                listOf(
                    MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_use_default)) {
                        adapter.useDefault()
                    },
                    MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_sort)) {
                        adapter.showSortDialog()
                    },
                    MaterialDialog.OptionMenuItemSpec(context.getString(R.string.text_download_all)) {
                        adapter.confirmDownloadAll()
                    },
                    MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_details)) {
                        showFontHelp(context)
                    },
                ),
            )
            .adapter(adapter, null)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ -> onSelected(adapter.selectedFontId) }
        if (onPreviewRequested != null) {
            builder
                .neutralText(R.string.dialog_button_preview)
                .neutralColorRes(R.color.dialog_button_hint)
                .onNeutral { dialog, _ ->
                    val fontId = adapter.selectedFontId
                    dialog.dismiss()
                    onPreviewRequested(fontId)
                }
        }
        val dialog = builder.build()
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
            if (!swipeActionsAttached) {
                dialog.recyclerView?.let { recyclerView ->
                    attachTouchHelper(context, recyclerView, adapter)
                    swipeActionsAttached = true
                }
            }
            refreshSubscription = manager.refreshAsync { result ->
                if (!adapter.isDisposed) {
                    result.onSuccess {
                        adapter.submit(manager.options(context, adapter.selectedFontId))
                    }
                }
            }
        }
        dialog.setOnDismissListener {
            refreshSubscription?.cancel()
            adapter.dispose()
        }
        return dialog
    }

    private fun showFontHelp(context: Context) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_ace_editor_font)
                .content(R.string.description_ace_editor_font_dialog)
                .positiveText(R.string.dialog_button_dismiss)
                .positiveColorRes(R.color.dialog_button_default)
                .build()
        }
    }

    private fun attachTouchHelper(
        context: Context,
        recyclerView: RecyclerView,
        adapter: Adapter,
    ) {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        ) {
            private val swipePainter = SwipePainter(context)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean = false

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int = adapter.swipeDirections(viewHolder.bindingAdapterPosition)

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                adapter.notifyItemChanged(position)
                adapter.performSwipeAction(position, direction)
            }

            override fun onChildDraw(
                canvas: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean,
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    swipePainter.draw(canvas, viewHolder, dX)
                }
                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        helper.attachToRecyclerView(recyclerView)
    }

    private class SwipePainter(
        private val context: Context,
    ) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = DisplayUtils.dpToPx(14f)
        }
        private val iconBounds = Rect()

        fun draw(canvas: Canvas, viewHolder: RecyclerView.ViewHolder, dX: Float) {
            val itemView = viewHolder.itemView
            when {
                dX > 0f -> drawAction(
                    canvas = canvas,
                    itemView = itemView,
                    left = itemView.left,
                    right = itemView.left + dX.toInt(),
                    iconRes = R.drawable.ic_download_thicker,
                    label = context.getString(R.string.text_download),
                    color = Color.rgb(46, 125, 50),
                    alignStart = true,
                )
                dX < 0f -> drawAction(
                    canvas = canvas,
                    itemView = itemView,
                    left = itemView.right + dX.toInt(),
                    right = itemView.right,
                    iconRes = R.drawable.ic_key_store_delete,
                    label = context.getString(R.string.text_delete),
                    color = Color.rgb(198, 40, 40),
                    alignStart = false,
                )
            }
        }

        private fun drawAction(
            canvas: Canvas,
            itemView: View,
            left: Int,
            right: Int,
            iconRes: Int,
            label: String,
            color: Int,
            alignStart: Boolean,
        ) {
            backgroundPaint.color = color
            canvas.drawRect(
                left.toFloat(),
                itemView.top.toFloat(),
                right.toFloat(),
                itemView.bottom.toFloat(),
                backgroundPaint,
            )

            val iconSize = DisplayUtils.dpToPx(22f).roundToInt()
            val iconTop = itemView.top + (itemView.height - iconSize) / 2
            val iconLeft = if (alignStart) {
                itemView.left + DisplayUtils.dpToPx(20f).roundToInt()
            } else {
                itemView.right - DisplayUtils.dpToPx(20f).roundToInt() - iconSize
            }
            iconBounds.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            context.getDrawable(iconRes)?.mutate()?.let { drawable ->
                DrawableCompat.setTint(drawable, Color.WHITE)
                drawable.bounds = iconBounds
                drawable.draw(canvas)
            }

            val textY = itemView.top + itemView.height / 2f -
                (textPaint.descent() + textPaint.ascent()) / 2f
            val textX = if (alignStart) {
                iconBounds.right + DisplayUtils.dpToPx(12f)
            } else {
                iconBounds.left - DisplayUtils.dpToPx(12f) - textPaint.measureText(label)
            }
            canvas.drawText(label, textX, textY, textPaint)
        }
    }

    private class Adapter(
        private val context: Context,
        private val manager: AceEditorFontManager,
        fonts: List<AceEditorFontOption>,
        currentFontId: String,
        private val onPersistedFontDeleted: (String) -> Unit,
    ) : RecyclerView.Adapter<Adapter.ViewHolder>() {

        private val choiceTint = ThemeColorHelper.getThemeColorStateList(context)
        private val progressTint = ColorUtils.adjustColorForContrast(
            context.getColor(R.color.dialog_progress_gray_background_tint),
            ThemeColorManager.colorPrimary,
            2.3,
        ).let(ColorStateList::valueOf)
        private val progressTrackTint = ColorStateList.valueOf(
            ColorUtils.applyAlpha(progressTint.defaultColor, 0.2),
        )
        private val downloads = linkedMapOf<String, DownloadUi>()
        private var fonts: List<AceEditorFontOption> = AceEditorFontUiPreferences.sortedOptions(context, fonts)
        private var selectedId: String = currentFontId
        private var disposed = false
        private var bulkDownload: BulkDownloadUi? = null

        val selectedFontId: String
            get() = selectedId

        val isDisposed: Boolean
            get() = disposed

        init {
            if (fonts.none { it.id == selectedId }) selectedId = AceFontIds.BUNDLED_IOSEVKA
            fonts.mapNotNull { font ->
                font.variants.firstOrNull { variant ->
                    variant.artifact.sha256 == manager.activeDownloadSha256(variant.id)
                }?.let { variant -> font.withVariant(variant) }
            }.forEach { active ->
                startDownload(active, selectOnSuccess = false, notifyOnSuccess = false)
            }
        }

        fun submit(newFonts: List<AceEditorFontOption>) {
            if (disposed) return
            fonts = AceEditorFontUiPreferences.sortedOptions(context, newFonts)
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
            val download = activeDownload(font)
            val state = manager.installationState(font)
            val selectable = isSelectable(font, state?.status)
            holder.title.text = font.displayName
            val subtitle = buildList {
                if (font.variants.size > 1) font.remoteFont?.variantName?.let(::add)
                font.author.takeIf { it.isNotBlank() }?.let(::add)
            }.joinToString(" · ")
            holder.subtitle.text = subtitle
            holder.subtitle.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
            holder.status.text = statusText(font, state, download)
            holder.radioButton.buttonTintList = choiceTint
            holder.radioButton.isEnabled = selectable
            holder.radioButton.isChecked = font.id == selectedId
            holder.progress.visibility = if (download == null) View.GONE else View.VISIBLE
            holder.progress.progress = download?.percent ?: 0
            holder.progress.progressTintList = progressTint
            holder.progress.progressBackgroundTintList = progressTrackTint
            holder.itemView.alpha = if (selectable) 1f else UNAVAILABLE_ALPHA
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

        fun useDefault() {
            select(AceFontIds.BUNDLED_IOSEVKA)
        }

        fun showSortDialog() {
            if (disposed) return
            val modes = AceEditorFontSortMode.entries
            var selectedMode = AceEditorFontUiPreferences.getSortMode(context)
            val typeLockedByField = AceEditorFontSortField.entries.associateWithTo(mutableMapOf()) {
                AceEditorFontUiPreferences.getTypeLocked(context, it)
            }
            var syncingTypeLock = false

            fun syncTypeLock(dialog: MaterialDialog) {
                syncingTypeLock = true
                dialog.setPromptCheckBoxChecked(typeLockedByField.getValue(selectedMode.field))
                syncingTypeLock = false
            }

            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.text_sort)
                    .items(modes.map { context.getString(it.labelRes) })
                    .checkBoxPromptRes(
                        R.string.text_ace_editor_font_sort_type_locked,
                        typeLockedByField.getValue(selectedMode.field),
                    ) { _, isChecked ->
                        if (!syncingTypeLock) typeLockedByField[selectedMode.field] = isChecked
                    }
                    .options(
                        listOf(
                            MaterialDialog.OptionMenuItemSpec(
                                context.getString(R.string.dialog_button_use_default),
                            ) { dialog ->
                                AceEditorFontSortField.entries.forEach { field ->
                                    typeLockedByField[field] = field.typeLockedByDefault
                                }
                                selectedMode = AceEditorFontSortMode.DEFAULT
                                dialog.selectedIndex = modes.indexOf(AceEditorFontSortMode.DEFAULT)
                                syncTypeLock(dialog)
                            },
                        ),
                    )
                    .itemsCallbackSingleChoice(modes.indexOf(selectedMode)) { dialog, _, which, _ ->
                        selectedMode = modes[which]
                        syncTypeLock(dialog)
                        true
                    }
                    .widgetThemeColor()
                    .alwaysCallSingleChoiceCallback()
                    .onPositive { _, _ ->
                        AceEditorFontUiPreferences.setSortMode(context, selectedMode)
                        typeLockedByField.forEach { (field, typeLocked) ->
                            AceEditorFontUiPreferences.setTypeLocked(context, field, typeLocked)
                        }
                        submit(manager.options(context, selectedId))
                    }
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_attraction)
                    .build()
            }
        }

        fun swipeDirections(position: Int): Int {
            val font = fonts.getOrNull(position) ?: return 0
            if (isGroupDownloading(font)) return 0
            if (font.delivery != AceEditorFontOption.Delivery.DOWNLOADABLE) return 0
            val state = manager.installationState(font) ?: return 0
            var directions = 0
            if (font.remoteFont != null && state.status in setOf(
                    FontInstallationStatus.NOT_INSTALLED,
                    FontInstallationStatus.UPDATE_AVAILABLE,
                )
            ) {
                directions = directions or ItemTouchHelper.RIGHT
            }
            if (state.installed != null) {
                directions = directions or ItemTouchHelper.LEFT
            }
            return directions
        }

        fun performSwipeAction(position: Int, direction: Int) {
            val font = fonts.getOrNull(position) ?: return
            when (direction) {
                ItemTouchHelper.RIGHT -> confirmDownload(font)
                ItemTouchHelper.LEFT -> confirmDelete(font)
            }
        }

        fun confirmDownloadAll() {
            if (disposed) return
            if (bulkDownload != null) {
                ViewUtils.showToast(context, R.string.text_ace_editor_font_download_all_running)
                return
            }
            val candidates = manager.downloadOptions(context).filter { font ->
                if (font.remoteFont == null || isGroupDownloading(font)) return@filter false
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
                            PFiles.formatSizeWithUnit(totalBytes),
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
            val size = font.remoteFont?.artifact?.sizeBytes
                ?: state?.installed?.sizeBytes
                ?: font.sizeBytes
            if (font.delivery != AceEditorFontOption.Delivery.DOWNLOADABLE) {
                val stateText = context.getString(R.string.text_ace_editor_font_built_in)
                return if (font.delivery == AceEditorFontOption.Delivery.BUNDLED && size != null) {
                    context.getString(
                        R.string.text_ace_editor_font_status,
                        stateText,
                        PFiles.formatSizeWithUnit(size),
                    )
                } else {
                    stateText
                }
            }
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
                    PFiles.formatSizeWithUnit(it),
                )
            } ?: stateText
        }

        private fun select(fontId: String) {
            if (fontId == selectedId) return
            val previous = fonts.indexOfFirst { it.id == selectedId }
            selectedId = fontId
            if (previous >= 0) notifyItemChanged(previous)
            fonts.indexOfFirst { it.id == selectedId }
                .takeIf { it >= 0 }
                ?.let(::notifyItemChanged)
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
            if (isGroupDownloading(font)) return
            val displayName = font.variantDisplayName()
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(displayName)
                    .content(
                        context.getString(
                            R.string.text_ace_editor_font_download_confirmation,
                            displayName,
                            PFiles.formatSizeWithUnit(remote.artifact.sizeBytes),
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
            if (disposed || downloads.containsKey(font.id) || isSiblingDownloading(font)) {
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
                            submit(manager.options(context, selectedId))
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
            submit(manager.options(context, selectedId))
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
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .apply {
                        if (font.variants.size > 1) {
                            negativeText(R.string.text_ace_editor_font_filter_feature_title)
                            negativeColorRes(R.color.dialog_button_advanced_settings)
                            onNegative { _, _ -> showVariants(font) }
                        }
                        font.remoteFont?.licenseUrl?.let { licenseUrl ->
                            neutralText(R.string.text_ace_editor_font_license)
                            neutralColorRes(R.color.dialog_button_hint)
                            onNeutral { _, _ -> IntentUtils.browse(context, licenseUrl) }
                        }
                    }
                    .build()
            }
        }

        private fun showVariants(font: AceEditorFontOption) {
            val variants = font.variants.sortedWith(
                compareBy<RemoteFont> { it.variantOrder }
                    .thenBy { it.id },
            )
            if (variants.size <= 1) return
            val preferredId = AceEditorFontUiPreferences.getSelectedVariantId(context, font.groupId)
            var selected = variants.indexOfFirst { it.id == preferredId }
                .takeIf { it >= 0 }
                ?: variants.indexOfFirst { it.id == font.id }.coerceAtLeast(0)
            val items = variants.map { variant ->
                val stateText = context.getString(
                    when (manager.installationState(variant).status) {
                        FontInstallationStatus.INSTALLED -> R.string.text_installed
                        FontInstallationStatus.UPDATE_AVAILABLE -> R.string.text_update
                        FontInstallationStatus.REVOKED -> R.string.text_unavailable
                        FontInstallationStatus.NOT_INSTALLED -> R.string.text_not_installed
                    },
                )
                listOfNotNull(
                    variant.variantName,
                    stateText,
                    PFiles.formatSizeWithUnit(variant.artifact.sizeBytes),
                ).joinToString(" · ")
            }
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(font.displayName)
                    .items(items)
                    .itemsCallbackSingleChoice(selected) { _, _, which, _ ->
                        selected = which
                        true
                    }
                    .choiceWidgetThemeColor()
                    .alwaysCallSingleChoiceCallback()
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_attraction)
                    .onPositive { _, _ -> selectVariant(font, variants[selected]) }
                    .build()
            }
        }

        private fun selectVariant(
            previous: AceEditorFontOption,
            variant: RemoteFont,
        ) {
            if (!manager.selectVariant(previous.groupId, variant.id)) return
            val option = manager.options(context, variant.id)
                .firstOrNull { it.groupId == previous.groupId && it.id == variant.id }
                ?: return
            val state = manager.installationState(option)
            if (isSelectable(option, state?.status)) {
                select(variant.id)
                submit(manager.options(context, selectedId))
            } else if (state?.status == FontInstallationStatus.NOT_INSTALLED) {
                confirmDownload(option)
            }
        }

        private fun AceEditorFontOption.variantDisplayName(): String {
            val variant = remoteFont?.variantName?.takeIf { variants.size > 1 }
            return variant?.let { "$displayName · $it" } ?: displayName
        }

        private fun AceEditorFontOption.withVariant(variant: RemoteFont): AceEditorFontOption = copy(
            id = variant.id,
            family = variant.family,
            author = variant.author,
            licenseName = variant.licenseName,
            type = if (manager.installationState(variant).installed == null) {
                AceEditorFontType.NOT_INSTALLED
            } else {
                AceEditorFontType.INSTALLED
            },
            remoteFont = variant,
            features = variant.features,
            sizeBytes = variant.artifact.sizeBytes,
        )

        private fun activeDownload(font: AceEditorFontOption): DownloadUi? =
            downloads[font.id] ?: font.variants.firstNotNullOfOrNull { downloads[it.id] }

        private fun isGroupDownloading(font: AceEditorFontOption): Boolean =
            font.variants.any { downloads.containsKey(it.id) || manager.isDownloading(it.id) }

        private fun isSiblingDownloading(font: AceEditorFontOption): Boolean =
            font.variants.any {
                it.id != font.id && (downloads.containsKey(it.id) || manager.isDownloading(it.id))
            }

        private fun notifyFontChanged(fontId: String) {
            fonts.indexOfFirst { it.containsFontId(fontId) }
                .takeIf { it >= 0 }
                ?.let(::notifyItemChanged)
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
        }

        private companion object {
            const val UNAVAILABLE_ALPHA = 0.58f
        }
    }
}
