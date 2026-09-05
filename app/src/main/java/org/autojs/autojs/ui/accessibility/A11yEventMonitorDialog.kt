package org.autojs.autojs.ui.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.accessibility.A11yEventRecord
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.diagnostics.A11yServiceInfoFormat
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.A11yEventMonitorItemBinding
import org.autojs.autojs6.databinding.DialogA11yEventMonitorBinding
import kotlin.math.roundToInt

/**
 * Live list of the accessibility events delivered to the service, opened from the service manager.
 * Rows show the interval to the previous listed event, which is what makes repeated
 * `WINDOW_STATE_CHANGED` deliveries of a single window switch visible. Events are received through the
 * companion-level observer of [AccessibilityService], so the list survives a service restart.
 *
 * zh-CN: 服务收到的无障碍事件的实时列表, 由服务管理器打开. 每行显示与上一条所列事件的间隔, 用于观察一次窗口切换
 * 多次投递 `WINDOW_STATE_CHANGED` 的现象. 事件来自 [AccessibilityService] 伴生对象级的观察者, 因此列表可跨服务重启存活.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class A11yEventMonitorDialog private constructor(private val context: Context) {

    private val binding = DialogA11yEventMonitorBinding.inflate(LayoutInflater.from(context))
    private val handler = Handler(Looper.getMainLooper())

    // `all` keeps the newest MAX_RECORDS events; `shown` is the filtered subsequence bound to the adapter.
    // zh-CN: `all` 保留最新的 MAX_RECORDS 条事件; `shown` 是绑定到适配器的已过滤子序列.
    private val all = ArrayDeque<A11yEventRecord>()
    private val shown = ArrayList<A11yEventRecord>()
    private val pending = ArrayList<A11yEventRecord>()
    private val adapter = Adapter()

    private var isPaused = false
    private var droppedWhilePaused = 0
    private var windowStateChangesOnly = false
    private var isFlushScheduled = false

    // The options list is read each time the overflow menu opens, so the toggle's title can be swapped in place.
    // zh-CN: 每次打开溢出菜单都会重新读取 options 列表, 因此可以就地替换开关项的标题.
    private val options = ArrayList<MaterialDialog.OptionMenuItemSpec>()
    private val dialog: MaterialDialog

    private val flushTask = Runnable { flush() }

    init {
        binding.list.layoutManager = LinearLayoutManager(context)
        binding.list.adapter = adapter
        binding.listContainer.layoutParams.height = listHeight()

        options += MaterialDialog.OptionMenuItemSpec(context.getString(R.string.dialog_button_clear_items)) { clear() }
        options += windowStateChangesOnlyOption()

        dialog = MaterialDialog.Builder(context)
            .title(R.string.text_a11y_event_monitor)
            .options(options)
            .customView(binding.root, false)
            .autoDismiss(false)
            .negativeText(R.string.dialog_button_dismiss)
            .negativeColorRes(R.color.dialog_button_default)
            .onNegative { d, _ -> d.dismiss() }
            .neutralText(R.string.text_pause)
            .neutralColorRes(R.color.dialog_button_warn)
            .onNeutral { _, _ -> togglePaused() }
            .positiveText(R.string.dialog_button_copy)
            .positiveColorRes(R.color.dialog_button_hint)
            .onPositive { _, _ -> copyAll() }
            .showListener { AccessibilityService.addEventObserver(this, ::onEvent) }
            .dismissListener {
                AccessibilityService.removeEventObserver(this)
                handler.removeCallbacksAndMessages(null)
            }
            .build()

        updateEmptyHint()
        updateStatus()
    }

    // Observers are invoked on the service's main thread, which is the app main thread; posting covers
    // any other origin. Storms are coalesced into one adapter update per FLUSH_INTERVAL_MILLIS.
    // zh-CN: 观察者在服务主线程 (即应用主线程) 被调用; 其它来源一律 post 到主线程. 事件风暴按 FLUSH_INTERVAL_MILLIS 合并为一次适配器更新.
    private fun onEvent(record: A11yEventRecord) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { onEvent(record) }
            return
        }
        if (!dialog.isShowing) return
        if (isPaused) {
            droppedWhilePaused++
        } else {
            pending += record
        }
        if (!isFlushScheduled) {
            isFlushScheduled = true
            handler.postDelayed(flushTask, FLUSH_INTERVAL_MILLIS)
        }
    }

    private fun flush() {
        isFlushScheduled = false
        if (pending.isNotEmpty()) {
            val batch = ArrayList(pending)
            pending.clear()
            append(batch)
        }
        updateStatus()
    }

    private fun append(batch: List<A11yEventRecord>) {
        val wasAtBottom = !binding.list.canScrollVertically(1)

        val oldSize = all.size
        all.addAll(batch)
        val overflow = (all.size - MAX_RECORDS).coerceAtLeast(0)
        val dropped = ArrayList<A11yEventRecord>(overflow)
        repeat(overflow) { dropped += all.removeFirst() }

        // Dropped old records that passed the filter are exactly a prefix of `shown`.
        // zh-CN: 被丢弃的旧记录中通过过滤器的那些, 恰好是 `shown` 的一个前缀.
        val droppedOld = minOf(overflow, oldSize)
        val removedShown = dropped.subList(0, droppedOld).count(::accepts)
        if (removedShown > 0) {
            shown.subList(0, removedShown).clear()
            adapter.notifyItemRangeRemoved(0, removedShown)
            if (shown.isNotEmpty()) adapter.notifyItemChanged(0)
        }

        val appended = batch.drop(overflow - droppedOld).filter(::accepts)
        if (appended.isNotEmpty()) {
            val start = shown.size
            shown.addAll(appended)
            adapter.notifyItemRangeInserted(start, appended.size)
            if (wasAtBottom) binding.list.scrollToPosition(shown.size - 1)
        }
        updateEmptyHint()
    }

    private fun accepts(record: A11yEventRecord): Boolean {
        return !windowStateChangesOnly || record.type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun rebuildShown() {
        shown.clear()
        all.filterTo(shown, ::accepts)
        adapter.notifyDataSetChanged()
        if (shown.isNotEmpty()) binding.list.scrollToPosition(shown.size - 1)
        updateEmptyHint()
        updateStatus()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clear() {
        all.clear()
        pending.clear()
        shown.clear()
        adapter.notifyDataSetChanged()
        updateEmptyHint()
    }

    private fun togglePaused() {
        isPaused = !isPaused
        if (isPaused) droppedWhilePaused = 0
        dialog.setActionButton(DialogAction.NEUTRAL, if (isPaused) R.string.text_resume else R.string.text_pause)
        updateStatus()
    }

    private fun windowStateChangesOnlyOption(): MaterialDialog.OptionMenuItemSpec {
        val title = context.getString(R.string.text_window_state_changes_only)
        return MaterialDialog.OptionMenuItemSpec(if (windowStateChangesOnly) "$title  [ ${context.getString(R.string.text_enabled)} ]" else title) {
            windowStateChangesOnly = !windowStateChangesOnly
            options[1] = windowStateChangesOnlyOption()
            rebuildShown()
        }
    }

    private fun updateStatus() {
        val parts = mutableListOf<String>()
        if (windowStateChangesOnly) parts += context.getString(R.string.text_window_state_changes_only)
        if (isPaused) parts += context.getString(R.string.text_events_dropped_while_paused, droppedWhilePaused)
        binding.status.text = parts.joinToString(" | ")
        binding.status.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateEmptyHint() {
        binding.empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun copyAll() {
        if (shown.isEmpty()) {
            ViewUtils.showSnack(dialog.view, R.string.text_no_content)
            return
        }
        val text = shown.indices.joinToString("\n") { lineOf(it) }
        ClipboardUtils.setClip(context, text)
        ViewUtils.showSnack(dialog.view, R.string.text_already_copied_to_clip)
    }

    private fun intervalOf(position: Int): String {
        val delta = if (position == 0) 0L else shown[position].eventTime - shown[position - 1].eventTime
        return "+${A11yServiceInfoFormat.formatElapsed(delta)}"
    }

    private fun titleOf(position: Int) = "${intervalOf(position)}  ${shown[position].typeName}"

    private fun subtitleOf(position: Int): String {
        val record = shown[position]
        return buildString {
            append(record.packageName ?: "-").append(" / ").append(record.className ?: "-")
            append(" | window ").append(record.windowId)
            if (record.contentChangeTypes != 0) {
                append(" | changes 0x").append(Integer.toHexString(record.contentChangeTypes).uppercase())
            }
        }
    }

    private fun lineOf(position: Int) = "${titleOf(position)}  ${subtitleOf(position)}"

    private fun listHeight(): Int = (context.resources.displayMetrics.heightPixels * 0.52f).roundToInt()

    private inner class Adapter : RecyclerView.Adapter<Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(A11yEventMonitorItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.binding.title.text = titleOf(position)
            holder.binding.subtitle.text = subtitleOf(position)
        }

        override fun getItemCount() = shown.size

    }

    private inner class Holder(val binding: A11yEventMonitorItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition.takeIf { it in shown.indices } ?: return@setOnClickListener
                ClipboardUtils.setClip(context, lineOf(position))
                ViewUtils.showSnack(dialog.view, R.string.text_already_copied_to_clip)
            }
        }

    }

    companion object {

        private const val MAX_RECORDS = 500
        private const val FLUSH_INTERVAL_MILLIS = 100L

        fun show(context: Context): MaterialDialog = A11yEventMonitorDialog(context).dialog.showAdaptive()

    }

}
