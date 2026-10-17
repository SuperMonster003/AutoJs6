package org.autojs.autojs.ui.explorer

import android.content.Context
import android.text.TextUtils
import android.text.format.Formatter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.autojs.autojs.model.explorer.Explorers
import org.autojs.autojs.model.explorer.PrivilegedAccessMode
import org.autojs.autojs.model.explorer.PrivilegedBackupManager
import org.autojs.autojs.model.explorer.PrivilegedBackupManager.BackupEntry
import org.autojs.autojs.model.explorer.PrivilegedBackupManager.RetentionPolicy
import org.autojs.autojs.permission.ShizukuPermission
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.util.DialogUtils.makeTextCopyable
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs6.R
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** A compact backup list shared by the path panel, file rows, and editor save details. */
class PrivilegedBackupUi private constructor(
    private val host: View,
    private val sourcePath: String?,
    private val directory: String?,
    private var discoveryMode: PrivilegedAccessMode?,
) {
    private val context: Context get() = host.context
    private val applicationContext = context.applicationContext
    private var dialog: MaterialDialog? = null
    private var detail: MaterialDialog? = null
    private var choice: MaterialDialog? = null
    private var progress: MaterialDialog? = null
    private var readRequest: Disposable? = null
    private var busy = false
    private var closed = false
    private var policy = RetentionPolicy()
    private val rows = BackupAdapter()
    private lateinit var status: TextView
    private lateinit var refresh: TextView
    private lateinit var list: RecyclerView
    private val foregroundColor get() = ColorUtils.setAlphaComponent(themeColor(android.R.attr.textColorPrimary, R.color.text_color_primary), 255)
    private val secondary get() = ColorUtils.setAlphaComponent(foregroundColor, 230)
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = close()
    }

    private fun show() {
        if (!host.isAttachedToWindow) return
        host.addOnAttachStateChangeListener(attachment)
        val body = column().apply { setPadding(dp(12), 0, dp(12), dp(2)) }
        body.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            status = label(context.getString(R.string.text_loading_with_dots), 12f).apply {
                setTextColor(secondary)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.MIDDLE
                setPadding(0, dp(4), dp(8), dp(4))
            }
            addView(status, LinearLayout.LayoutParams(0, -2, 1f))
            refresh = label(context.getString(R.string.text_refresh), 12f).apply {
                gravity = Gravity.CENTER
                minWidth = dp(48)
                minHeight = dp(40)
                clickableRow(this)
                setOnClickListener { reload() }
            }
            addView(refresh, LinearLayout.LayoutParams(-2, -2))
        })
        list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = rows
            isNestedScrollingEnabled = false
            contentDescription = context.getString(R.string.text_privileged_backups)
        }
        body.addView(list, LinearLayout.LayoutParams(-1, dp(72)))
        dialog = MaterialDialog.Builder(context)
            .title(R.string.text_privileged_backups)
            .customView(body, false)
            .negativeText(R.string.dialog_button_close)
            .onNegative { _, _ -> close() }
            .neutralText(R.string.text_privileged_backup_policy)
            .onNeutral { _, _ -> showPolicy() }
            .positiveText(R.string.text_privileged_backup_cleanup)
            .onPositive { _, _ -> confirmCleanup() }
            .autoDismiss(false)
            .dismissListener { close() }
            .showAdaptive()
        reload()
    }

    private fun reload() {
        if (closed || busy) return
        readRequest?.dispose()
        setBusy(true)
        status.text = context.getString(R.string.text_loading_with_dots)
        readRequest = Single.fromCallable {
            val scanDirectory = directory
            val scanMode = discoveryMode
            val discoveryError = if (scanDirectory != null && scanMode != null) runCatching {
                PrivilegedBackupManager.discoverDirectory(applicationContext, scanMode, scanDirectory)
            }.exceptionOrNull() else null
            LoadedBackups(
                PrivilegedBackupManager.list(applicationContext, sourcePath),
                PrivilegedBackupManager.policy(applicationContext),
                discoveryError,
                PrivilegedBackupManager.cleanupWarning(applicationContext),
            )
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe({ result ->
            if (closed) return@subscribe
            setBusy(false)
            val entries = result.entries
            policy = result.policy
            rows.replace(entries)
            list.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
            list.layoutParams = (list.layoutParams as LinearLayout.LayoutParams).apply {
                height = (entries.size * dp(64) * context.resources.configuration.fontScale.coerceAtLeast(1f)).roundToInt().coerceAtLeast(dp(64))
                    .coerceAtMost((context.resources.displayMetrics.heightPixels * 0.55f).roundToInt())
            }
            val summary = if (entries.isEmpty()) context.getString(R.string.text_privileged_backup_empty)
                else context.getString(R.string.text_privileged_backup_total, entries.size,
                    Formatter.formatFileSize(context, entries.sumOf { it.size.coerceAtLeast(0L) }))
            // Prioritize a cleanup warning over the repeated filter path, which is also in each row.
            status.text = summary + (result.cleanupWarning?.takeIf(String::isNotBlank) ?: sourcePath)
                ?.let { "\n$it" }.orEmpty()
            status.visibility = View.VISIBLE
            result.discoveryError?.let { showFailure(it) }
        }, { error ->
            if (closed) return@subscribe
            setBusy(false)
            status.visibility = View.VISIBLE
            status.text = error.message ?: context.getString(R.string.text_failed)
            showFailure(error)
        })
    }

    private fun showEntry(entry: BackupEntry) {
        if (busy || closed) return
        detail?.dismiss()
        val disabled = listOf(entry.canPreview, entry.canRestore, entry.canDelete)
            .mapIndexedNotNull { index, available -> index.takeUnless { available } }.toTypedArray()
        detail = MaterialDialog.Builder(context)
            .title(File(entry.source).name)
            .content(entryDetails(entry))
            .items(context.getString(R.string.text_view), context.getString(R.string.text_restore), context.getString(R.string.text_delete))
            .itemsDisabledIndices(*disabled)
            .itemsCallback { _, _, index, _ ->
                when (index) {
                    0 -> preview(entry)
                    1 -> prepareRestore(entry)
                    2 -> confirmDelete(entry)
                }
            }
            .neutralText(R.string.text_explorer_access_mode)
            .onNeutral { _, _ -> showAccessOptions(entry) }
            .positiveText(R.string.dialog_button_close)
            .showAdaptive()
            .apply { makeTextCopyable(contentView) }
    }

    private fun preview(entry: BackupEntry) = run(R.string.text_view, entry, {
        PrivilegedBackupManager.view(applicationContext, entry)
    }) { file ->
        if (FileUtils.TYPE.determineBy(file).isTextEditable()) {
            EditActivity.viewPath(context, File(entry.source).nameWithoutExtension, file.path, false)
        } else {
            IntentUtils.viewFile(context, file.path, exceptionHolder = IntentUtils.ToastExceptionHolder(context))
        }
    }

    private fun prepareRestore(entry: BackupEntry) = run(R.string.text_restore, entry, {
        PrivilegedBackupManager.prepareRestore(applicationContext, entry)
    }) { plan ->
        detail = MaterialDialog.Builder(context)
            .title(R.string.text_restore)
            .content(context.getString(
                if (plan.targetMissing) R.string.text_privileged_backup_restore_missing_confirm else R.string.text_privileged_backup_restore_confirm,
                entryDetails(plan.entry),
            ))
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.text_restore)
            .positiveColorRes(R.color.dialog_button_warn)
            .onPositive { _, _ ->
                run(R.string.text_restore, entry, { PrivilegedBackupManager.restore(applicationContext, plan) }) { result ->
                    // Existing editors retain their text and source baseline. Only file lists refresh.
                    Explorers.external().invalidateCache()
                    Explorers.workspace().refreshAll()
                    showSummary(result.message)
                    reload()
                }
            }
            .showAdaptive()
            .apply { makeTextCopyable(contentView) }
    }

    private fun confirmDelete(entry: BackupEntry) = run(R.string.text_delete, entry, {
        PrivilegedBackupManager.prepareDelete(applicationContext, entry)
    }) { current ->
        detail = MaterialDialog.Builder(context)
            .title(R.string.text_delete)
            .content(context.getString(R.string.text_privileged_backup_delete_confirm, entryDetails(current)))
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.text_delete)
            .positiveColorRes(R.color.dialog_button_caution)
            .onPositive { _, _ ->
                run(R.string.text_delete, current, { PrivilegedBackupManager.delete(applicationContext, current) }) { result ->
                    showSummary(result.message)
                    reload()
                }
            }
            .showAdaptive()
            .apply { makeTextCopyable(contentView) }
    }

    private fun confirmCleanup() {
        if (busy || closed) return
        detail = MaterialDialog.Builder(context)
            .title(R.string.text_privileged_backup_cleanup)
            .content(R.string.text_privileged_backup_cleanup_confirm)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_warn)
            .onPositive { _, _ ->
                run(R.string.text_privileged_backup_cleanup, null, { PrivilegedBackupManager.cleanup(applicationContext, sourcePath) }) { result ->
                    showSummary(result.message)
                    reload()
                }
            }
            .showAdaptive()
    }

    private fun showPolicy() {
        if (busy || closed) return
        var selected = policy
        val body = column().apply { setPadding(dp(12), 0, dp(12), dp(4)) }
        body.addView(label(context.getString(R.string.text_privileged_backup_policy_hint), 12f).apply {
            setTextColor(secondary)
            setPadding(0, 0, 0, dp(8))
        })
        val versionRow = policyRow(R.string.text_privileged_backup_max_versions, selected.maxVersions.toString())
        val bytesRow = policyRow(R.string.text_privileged_backup_max_bytes, byteLimitLabel(selected.maxBytes))
        body.addView(versionRow)
        body.addView(divider())
        body.addView(bytesRow)
        versionRow.setOnClickListener {
            val versions = listOf(1, 3, 5, 10)
            choice = MaterialDialog.Builder(context)
                .title(R.string.text_privileged_backup_max_versions)
                .items(versions.map(Int::toString))
                .itemsCallback { _, _, index, _ ->
                    selected = selected.copy(maxVersions = versions[index])
                    (versionRow.getChildAt(1) as TextView).text = selected.maxVersions.toString()
                }
                .negativeText(R.string.dialog_button_cancel)
                .showAdaptive()
        }
        bytesRow.setOnClickListener {
            val limits = listOf(16L, 64L, 256L).map { it * 1024L * 1024L }
            choice = MaterialDialog.Builder(context)
                .title(R.string.text_privileged_backup_max_bytes)
                .items(limits.map(::byteLimitLabel))
                .itemsCallback { _, _, index, _ ->
                    selected = selected.copy(maxBytes = limits[index])
                    (bytesRow.getChildAt(1) as TextView).text = byteLimitLabel(selected.maxBytes)
                }
                .negativeText(R.string.dialog_button_cancel)
                .showAdaptive()
        }
        detail = MaterialDialog.Builder(context)
            .title(R.string.text_privileged_backup_policy)
            .customView(body, true)
            .negativeText(R.string.dialog_button_cancel)
            .positiveText(R.string.dialog_button_confirm)
            .onPositive { _, _ ->
                run(R.string.text_privileged_backup_policy, null, {
                    PrivilegedBackupManager.setPolicy(applicationContext, selected)
                    PrivilegedBackupManager.cleanup(applicationContext)
                }) { result ->
                    policy = selected
                    showSummary(result.message)
                    reload()
                }
            }
            .showAdaptive()
    }

    private fun showAccessOptions(entry: BackupEntry) {
        detail = MaterialDialog.Builder(context)
            .title(R.string.text_explorer_access_mode)
            .content(R.string.text_privileged_backup_access_hint)
            .items(context.getString(R.string.text_explorer_access_shizuku), context.getString(R.string.text_explorer_access_root))
            .itemsCallback { _, _, index, _ ->
                val mode = if (index == 0) PrivilegedAccessMode.SHIZUKU else PrivilegedAccessMode.ROOT
                run(R.string.text_explorer_access_mode, entry, {
                    PrivilegedBackupManager.changeAccessMode(applicationContext, entry, mode)
                }) {
                    if (directory != null) discoveryMode = mode
                    val permission = ShizukuPermission(context)
                    if (mode == PrivilegedAccessMode.SHIZUKU && !permission.has()) {
                        permission.request()
                    } else {
                        reload()
                    }
                }
            }
            .negativeText(R.string.dialog_button_cancel)
            .showAdaptive()
    }

    private fun showDiscoveryAccessOptions() {
        detail = MaterialDialog.Builder(context)
            .title(R.string.text_explorer_access_mode)
            .content(R.string.text_privileged_backup_access_hint)
            .items(context.getString(R.string.text_explorer_access_shizuku), context.getString(R.string.text_explorer_access_root))
            .itemsCallback { _, _, index, _ ->
                discoveryMode = if (index == 0) PrivilegedAccessMode.SHIZUKU else PrivilegedAccessMode.ROOT
                val permission = ShizukuPermission(context)
                if (discoveryMode == PrivilegedAccessMode.SHIZUKU && !permission.has()) permission.request() else reload()
            }
            .negativeText(R.string.dialog_button_cancel)
            .showAdaptive()
    }

    private fun <T : Any> run(title: Int, entry: BackupEntry?, work: () -> T, success: (T) -> Unit) {
        if (closed || busy) return
        setBusy(true)
        progress = MaterialDialog.Builder(context).title(title).content(R.string.text_preparing)
            .progress(true, 0).cancelable(false).showAdaptive()
        // An authorized mutation must finish even if its originating window is destroyed.
        Single.fromCallable(work).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                progress?.dismiss()
                progress = null
                if (closed || !host.isAttachedToWindow) return@subscribe
                setBusy(false)
                success(result)
            }, { error ->
                progress?.dismiss()
                progress = null
                if (closed || !host.isAttachedToWindow) return@subscribe
                setBusy(false)
                showFailure(error, entry)
            })
    }

    private fun showFailure(error: Throwable, entry: BackupEntry? = null) {
        detail = MaterialDialog.Builder(context)
            .title(R.string.text_failed)
            .content(error.message ?: error.toString())
            .negativeText(R.string.dialog_button_close)
            .positiveText(R.string.text_refresh)
            .onPositive { _, _ -> reload() }
            .apply {
                if (entry != null) {
                    neutralText(R.string.text_explorer_access_mode)
                    onNeutral { _, _ -> showAccessOptions(entry) }
                } else if (directory != null && discoveryMode != null) {
                    neutralText(R.string.text_explorer_access_mode)
                    onNeutral { _, _ -> showDiscoveryAccessOptions() }
                }
            }
            .showAdaptive()
            .apply { makeTextCopyable(contentView) }
    }

    private fun showSummary(message: String) {
        detail = MaterialDialog.Builder(context)
            .title(R.string.text_done)
            .content(message)
            .positiveText(R.string.dialog_button_close)
            .showAdaptive()
            .apply { makeTextCopyable(contentView) }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        refresh.isEnabled = !value
        dialog?.getActionButton(DialogAction.NEUTRAL)?.isEnabled = !value
        dialog?.getActionButton(DialogAction.POSITIVE)?.isEnabled = !value
    }

    private fun close() {
        if (closed) return
        closed = true
        readRequest?.dispose()
        readRequest = null
        host.removeOnAttachStateChangeListener(attachment)
        progress?.dismiss()
        choice?.dismiss()
        detail?.dismiss()
        dialog?.dismiss()
        progress = null
        choice = null
        detail = null
        dialog = null
    }

    private fun entryDetails(entry: BackupEntry) = buildList {
        add(context.getString(R.string.text_privileged_backup_source) + "\n" + entry.source)
        add(context.getString(R.string.text_privileged_backup_path) + "\n" + entry.backupPath)
        add(dateAndSize(entry))
        if (entry.message.isNotBlank()) add(entry.message)
    }.joinToString("\n\n")

    private fun dateAndSize(entry: BackupEntry): String =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(entry.createdAt)) +
            " / " + Formatter.formatFileSize(context, entry.size)

    private fun byteLimitLabel(bytes: Long) = "${bytes / (1024 * 1024)} MiB"

    private fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private fun policyRow(title: Int, value: String) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        clickableRow(this)
        addView(label(context.getString(title), 14f), LinearLayout.LayoutParams(0, -2, 1f))
        addView(label(value, 13f).apply { setTextColor(secondary); setPadding(dp(12), dp(4), 0, dp(4)) })
    }

    private fun label(value: String, size: Float) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(foregroundColor)
    }

    private fun clickableRow(view: View) {
        view.isFocusable = true
        view.isClickable = true
        val value = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true) && value.resourceId != 0) {
            view.setBackgroundResource(value.resourceId)
        }
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(ColorUtils.setAlphaComponent(foregroundColor, 32))
        layoutParams = LinearLayout.LayoutParams(-1, 1)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun themeColor(attribute: Int, fallback: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
        } else context.getColor(fallback)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    private inner class BackupAdapter : RecyclerView.Adapter<BackupRow>() {
        private var entries = emptyList<BackupEntry>()
        fun replace(value: List<BackupEntry>) {
            entries = value
            notifyDataSetChanged()
        }
        override fun getItemCount() = entries.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BackupRow = BackupRow(column().apply {
            minimumHeight = dp(64)
            setPadding(0, dp(6), 0, dp(6))
            layoutParams = RecyclerView.LayoutParams(-1, -2)
            clickableRow(this)
        })
        override fun onBindViewHolder(holder: BackupRow, position: Int) = holder.bind(entries[position])
    }

    private inner class BackupRow(private val body: LinearLayout) : RecyclerView.ViewHolder(body) {
        private val title = label("", 14f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        private val metadata = label("", 11f).apply { setTextColor(secondary) }
        private val path = label("", 11f).apply { setTextColor(secondary); maxLines = 1; ellipsize = TextUtils.TruncateAt.MIDDLE }
        private val message = label("", 11f).apply { setTextColor(secondary) }
        init {
            body.addView(title)
            body.addView(metadata)
            body.addView(path)
            body.addView(message)
            body.addView(divider())
        }
        fun bind(entry: BackupEntry) {
            title.text = File(entry.source).name
            metadata.text = dateAndSize(entry)
            path.text = entry.source
            message.text = entry.message
            message.visibility = if (entry.status == "available" || entry.message.isBlank()) View.GONE else View.VISIBLE
            body.tag = entry.id
            body.contentDescription = entryDetails(entry)
            body.setOnClickListener { showEntry(entry) }
        }
    }

    private data class LoadedBackups(
        val entries: List<BackupEntry>,
        val policy: RetentionPolicy,
        val discoveryError: Throwable?,
        val cleanupWarning: String?,
    )

    companion object {
        @JvmStatic
        @JvmOverloads
        fun show(host: View, sourcePath: String? = null, directory: String? = null, mode: PrivilegedAccessMode? = null) =
            PrivilegedBackupUi(host, sourcePath, directory, mode).show()
    }
}
