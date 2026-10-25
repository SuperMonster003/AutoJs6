package org.autojs.autojs.core.console

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CountDownTimer
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import androidx.annotation.ColorInt
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.annotation.OmniColor
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.pref.keys.DialogStateKeys
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractConsole
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.api.modules.colors.Colors
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloatyWindow
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.joda.time.format.DateTimeFormat
import org.opencv.core.Point
import org.opencv.core.Size
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A console: a bounded list of [LogEntry] items plus an optional floating window that displays
 * them. Every script runtime owns one console (its entries only, no time prefix by default) which
 * forwards each line to its [parent], the process-wide [GlobalConsole] shown by the log page.
 *
 * Every window mutation (`setPosition`, `setSize`, colors, ...) only records the desired state in
 * [configurator]; the floating window applies the whole configuration when it attaches and every
 * setter re-applies its part on the UI thread when the window is already attached. Call order
 * between `show()` and the setters is therefore irrelevant.
 *
 * zh-CN: 控制台: 有界的 [LogEntry] 列表, 以及可选的用于显示它们的浮动窗口. 每个脚本运行时拥有一个控制台 (只含自身条目, 默认不带时间前缀),
 * 并把每一行转发给其 [parent], 即日志页显示的进程级 [GlobalConsole].
 * 所有窗口变更 (`setPosition`, `setSize`, 颜色等) 仅把期望状态记录到 [configurator]; 浮动窗口附加时应用整套配置, 窗口已附加时每个 setter 在 UI 线程重新应用自己的部分.
 * 因此 `show()` 与各 setter 之间的调用顺序无关紧要.
 *
 * Created by Stardust on May 2, 2017.
 * Modified by SuperMonster003 as of Apr 12, 2023.
 * Transformed by SuperMonster003 on Apr 12, 2023.
 * Rewritten by SuperMonster003 on Sep 8, 2026.
 */
open class ConsoleImpl @JvmOverloads constructor(
    val uiHandler: UiHandler,
    /** The console every line is forwarded to (the global console for runtime consoles). zh-CN: 每一行都会转发到的控制台 (运行时控制台的全局控制台). */
    val parent: ConsoleImpl? = null,
) : AbstractConsole() {

    private val applicationContext: Context = uiHandler.applicationContext

    private val mSafeSizeToSend: Int = 2.0.pow(16).toInt()
    private val mSafeSizeToCopy: Int = 2.0.pow(19).toInt()

    private val mLogListeners = CopyOnWriteArrayList<WeakReference<LogListener>>()
    private var mConsoleView: WeakReference<ConsoleView>? = null

    private val mIdCounter = AtomicInteger(0)
    private val mInputQueue = ConsoleInputQueue()
    private val mFloaty = ConsoleFloaty(this)
    private val mFloatyWindow = ResizableExpandableFloatyWindow(mFloaty)
    private val mDisplayOverOtherAppsPerm = DisplayOverOtherAppsPermission(applicationContext)

    @Volatile
    private var mCountDownTimer: CountDownTimer? = null

    /** Whether the window was handed to [FloatyService] and not closed since. zh-CN: 窗口是否已交给 [FloatyService] 且此后未被关闭. */
    @Volatile
    private var mWindowRequested = false

    val configurator = Configurator()

    val logEntries = ArrayList<LogEntry>()

    @Volatile
    var isShowing = false
        private set

    /** Set once the owning script runtime has exited. zh-CN: 所属脚本运行时退出后置位. */
    @Volatile
    var isRuntimeExited = false
        private set

    val isGlobal: Boolean
        get() = parent == null

    /** The context of the most recently bound view, or the application context. zh-CN: 最近绑定的视图的 context, 或应用 context. */
    val context: Context
        get() = mConsoleView?.get()?.context ?: mFloaty.expandedView?.context ?: applicationContext

    val floaty: ConsoleFloaty
        get() = mFloaty

    val floatyWindow: ResizableExpandableFloatyWindow
        get() = mFloatyWindow

    /** Entries as text, with the time prefix for the global console. zh-CN: 条目文本, 全局控制台带时间前缀. */
    private val logEntriesJoint: String
        get() = joinLogEntries(isGlobal)

    // ========== Printing / 打印 ==========

    override fun println(level: Int, charSequence: CharSequence): String? {
        appendEntry(level, charSequence)
        parent?.println(level, charSequence)
        return null
    }

    protected fun appendEntry(level: Int, content: CharSequence): LogEntry {
        val logEntry = LogEntry(mIdCounter.getAndIncrement(), level, content, System.currentTimeMillis())
        synchronized(logEntries) {
            logEntries.add(logEntry)
            // Soft cap (MCP roadmap P1.3): drop the oldest entries; consumers track entries by id.
            // zh-CN: 软上限 (MCP 路线图 P1.3): 丢弃最旧的条目; 消费方按 id 跟踪条目.
            val overflow = logEntries.size - MAX_LOG_ENTRIES
            if (overflow > 0) logEntries.subList(0, overflow).clear()
        }
        mLogListeners.forEach { it.get()?.onNewLog(logEntry) }
        return logEntry
    }

    /** The id of the newest entry ever printed, -1 before the first one. zh-CN: 最新打印条目的 id, 首条之前为 -1. */
    val latestLogId: Int
        get() = mIdCounter.get() - 1

    /** A copy of the retained entries in id order. zh-CN: 当前保留条目按 id 顺序的副本. */
    fun snapshotLogEntries(): List<LogEntry> = synchronized(logEntries) { ArrayList(logEntries) }

    public override fun write(level: Int, data: CharSequence?) {
        data?.let { println(level, it) }
    }

    override fun error(data: Any?, vararg formatArgs: Any?) {
        var niceData = data
        if (niceData is Throwable) {
            niceData = getStackTrace(niceData)
        }
        if (formatArgs.isNotEmpty()) {
            val sb = StringBuilder(niceData?.toString() ?: "")
            val newFormatArgs = mutableListOf<Any>()
            formatArgs.forEach { arg ->
                if (arg is Throwable) {
                    sb.append(getStackTrace(arg)).append(" ")
                } else {
                    arg?.let { newFormatArgs.add(it) }
                }
            }
            niceData = sb.toString()
            if (newFormatArgs.isEmpty()) {
                super.error(niceData, *newFormatArgs.toTypedArray())
            } else {
                super.error(niceData)
            }
        } else {
            super.error(niceData, *formatArgs)
        }
    }

    fun printAllStackTrace(t: Throwable) {
        ScriptRuntime.getStackTrace(t, true)?.let {
            println(Log.ERROR, it)
        }
    }

    @ScriptInterface
    override fun clear() {
        synchronized(logEntries) { logEntries.clear() }
        mLogListeners.forEach { it.get()?.onLogClear() }
    }

    /** Renders one entry the way this console exports it. zh-CN: 按本控制台的导出方式渲染一条条目. */
    fun formatEntry(logEntry: LogEntry, timeVisible: Boolean = isGlobal): String {
        return ConsoleLogFormat.formatLine(logEntry.timestamp, logEntry.level, logEntry.content, timeVisible, configurator.timeFormat)
    }

    fun joinLogEntries(timeVisible: Boolean = isGlobal): String = synchronized(logEntries) {
        logEntries.joinToString("\n") { formatEntry(it, timeVisible) }
    }

    // ========== Views and listeners / 视图与监听器 ==========

    fun setConsoleView(consoleView: ConsoleView?) {
        consoleView ?: return
        mConsoleView = WeakReference(consoleView)
        addLogListener(consoleView)
    }

    fun addLogListener(logListener: LogListener?) {
        logListener ?: return
        mLogListeners.removeAll { it.get().let { listener -> listener == null || listener === logListener } }
        mLogListeners.add(WeakReference(logListener))
    }

    fun removeLogListener(logListener: LogListener?) {
        mLogListeners.removeAll { reference -> reference.get().let { it == null || it === logListener } }
    }

    /**
     * Whether a view currently displays this console. Views listen from [ConsoleView.setConsole]
     * until they detach, so a view inflated a moment ago (a UI script right after `ui.layout`)
     * already counts even though its window attachment lags behind by a frame.
     *
     * zh-CN: 当前是否有视图显示此控制台. 视图自 [ConsoleView.setConsole] 起监听直至脱离窗口, 因此刚创建的视图 (UI 脚本 `ui.layout` 之后) 即便窗口附加还落后一帧也算在内.
     */
    private fun hasBoundViews(): Boolean = mLogListeners.any { it.get() is View }

    // ========== Input / 输入 ==========

    val pendingInputCount: Int
        get() = mInputQueue.pendingCount

    val hasPendingInput: Boolean
        get() = mInputQueue.hasPending

    /**
     * Blocks the calling (script) thread until a line is submitted to this console or to any
     * console it forwards to, showing the floating window when nothing displays this console yet.
     *
     * zh-CN: 阻塞调用 (脚本) 线程, 直到向本控制台或其转发目标提交一行输入; 若尚无视图显示本控制台则显示浮动窗口.
     */
    @ScriptInterface
    fun rawInput(): String {
        check(Looper.myLooper() != Looper.getMainLooper()) { "console.rawInput() must not be called on the UI thread" }
        if (isRuntimeExited) throw ScriptInterruptedException()
        val request = ConsoleInputQueue.newRequest(this)
        registerInputRequest(request)
        try {
            ensureInputSurface()
            return request.await()
        } catch (e: InterruptedException) {
            throw ScriptInterruptedException(e)
        } catch (e: CancellationException) {
            throw ScriptInterruptedException(e)
        } finally {
            unregisterInputRequest(request)
        }
    }

    @ScriptInterface
    fun rawInput(data: Any?, vararg formatArgs: Any?): String {
        log(data, *formatArgs)
        return rawInput()
    }

    /**
     * Hands [text] to the oldest script waiting on this console (or on a runtime console
     * forwarding to it) and echoes it into that console.
     *
     * zh-CN: 把 [text] 交给在本控制台 (或转发到本控制台的运行时控制台) 上等待最久的脚本, 并回显到该控制台.
     *
     * @return false when no script is waiting. zh-CN: 无脚本等待时返回 false.
     */
    fun submitInput(text: CharSequence): Boolean {
        val request = mInputQueue.submit(text.toString()) ?: return false
        (request.owner as? ConsoleImpl ?: this).println(Log.VERBOSE, "$INPUT_ECHO_PREFIX$text")
        return true
    }

    /** Nonblocking counterpart used by the external Node runtime's stdin. */
    internal fun requestInput(callback: (String) -> Unit): ConsoleInputRequest {
        val request = ConsoleInputQueue.newRequest(this)
        if (isRuntimeExited) return request.also { it.cancel() }
        registerInputRequest(request)
        request.whenComplete { text ->
            unregisterInputRequest(request)
            if (text != null) callback(text)
        }
        try {
            ensureInputSurface()
        } catch (error: Exception) {
            request.cancel()
            throw error
        }
        return request
    }

    private fun registerInputRequest(request: ConsoleInputRequest) {
        var console: ConsoleImpl? = this
        while (console != null) {
            console.mInputQueue.add(request)
            console.notifyInputRequestsChanged()
            console = console.parent
        }
    }

    private fun unregisterInputRequest(request: ConsoleInputRequest) {
        var console: ConsoleImpl? = this
        while (console != null) {
            console.mInputQueue.remove(request)
            console.notifyInputRequestsChanged()
            console = console.parent
        }
    }

    private fun notifyInputRequestsChanged() {
        val count = pendingInputCount
        mLogListeners.forEach { it.get()?.onInputRequestsChanged(count) }
    }

    private fun ensureInputSurface() {
        if (!isGlobal && !isShowing && !hasBoundViews()) {
            show()
        }
    }

    private fun cancelPendingInputs() {
        mInputQueue.cancelAll(this)
    }

    // ========== Clipboard, export and sharing / 剪贴板, 导出与分享 ==========

    fun copyAll() = copyAll(logEntriesJoint)

    private fun copyAll(text: String, cutOutEntriesSize: Int = -1) {
        if (text.isEmpty()) {
            ViewUtils.showToast(context, R.string.text_no_log_entries_to_copy)
            return
        }
        try {
            ClipboardUtils.setClip(context, text)
            if (cutOutEntriesSize >= 0) {
                ViewUtils.showToast(
                    context, context.getString(
                        R.string.text_already_copied_to_clip_but_only_latest_few_items,
                        cutOutEntriesSize
                    ), true
                )
            } else {
                ViewUtils.showToast(context, R.string.text_already_copied_to_clip)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (text.length < mSafeSizeToCopy || cutOutEntriesSize >= 0) {
                ViewUtils.showToast(context, R.string.error_failed_to_copy)
                return
            }
            try {
                val cutOutEntries = cutOutEntries(mSafeSizeToCopy)
                copyAll(cutOutEntries.joinToString("\n") { formatEntry(it) }, cutOutEntries.size)
            } catch (e: Exception) {
                e.printStackTrace()
                ViewUtils.showToast(context, R.string.error_failed_to_copy)
            }
        }
    }

    fun export() {
        mConsoleView?.get()?.export(createLogExportFileName())
    }

    fun export(uri: Uri?) {
        val message = logEntriesJoint
        if (message.isEmpty()) {
            ViewUtils.showToast(context, R.string.text_no_log_entries_to_export)
            return
        }
        try {
            context.contentResolver.openOutputStream(uri ?: return)?.use {
                it.write(message.toByteArray())
                it.flush()
            }
            ViewUtils.showToast(
                context,
                context.getString(R.string.text_some_items_exported, logEntries.size),
                true,
            )
        } catch (e: IOException) {
            handleExceptionWithDialog(e, R.string.text_failed_to_export_log_entries)
        }
    }

    fun send() {
        val message = logEntriesJoint
        if (message.isEmpty()) {
            ViewUtils.showToast(context, R.string.text_no_log_entries_to_send)
            return
        }
        try {
            send(message)
        } catch (e: Exception) {
            e.printStackTrace()
            if (message.length < mSafeSizeToSend) {
                throw Exception(e)
            }

            try {
                val cutOutEntries = cutOutEntries(mSafeSizeToSend)

                val msgReason = context.getString(R.string.text_num_of_log_entries_exceeds_limit_for_sending, logEntries.size)
                val msgAction = context.getString(R.string.text_only_latest_few_items_will_be_sent, cutOutEntries.size)

                NotAskAgainDialog.Builder(
                    context,
                    DialogStateKeys.DIALOG_NUM_OF_LOG_ENTRIES_EXCEEDS_LIMIT_FOR_SENDING,
                ).apply {
                    title(R.string.text_prompt)
                    content("$msgReason, $msgAction.")
                    widgetThemeColor()
                    cancelable(false)
                    negativeText(R.string.dialog_button_abandon)
                    negativeColorRes(R.color.dialog_button_default)
                    positiveText(R.string.dialog_button_continue)
                    positiveColorRes(R.color.dialog_button_attraction)
                    onPositive { _, _ -> send(cutOutEntries) }
                    if (show() == null) {
                        ViewUtils.showToast(context, msgAction.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Language.getPrefLanguage().locale)
                            else it.toString()
                        }, true)
                        send(cutOutEntries)
                    }
                }
            } catch (e: Exception) {
                handleExceptionWithDialog(e, R.string.text_failed_to_send_log_entries)
            }
        }
    }

    private fun send(entries: MutableList<LogEntry>) {
        send(entries.joinToString("\n") { formatEntry(it) })
    }

    private fun send(message: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = Mime.TEXT_PLAIN
        }
        Intent.createChooser(sendIntent, null).startSafely(applicationContext)
    }

    private fun cutOutEntries(maxLength: Int): MutableList<LogEntry> {
        var accLength = 0
        val chosenEntries = mutableListOf<LogEntry>()
        synchronized(logEntries) {
            for (entry in logEntries.reversed()) {
                if (accLength >= maxLength) {
                    break
                }
                accLength += entry.content.length
                chosenEntries.add(0, entry)
            }
        }
        return chosenEntries
    }

    private fun handleExceptionWithDialog(e: Exception, contentRes: Int) {
        e.printStackTrace()
        MaterialDialog.Builder(context)
            .title(R.string.text_prompt)
            .content(contentRes)
            .cancelable(false)
            .autoDismiss(false)
            .neutralText(R.string.text_details)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { dialog, _ ->
                dialog.setActionButton(DialogAction.NEUTRAL, null)
                dialog.setActionButton(DialogAction.POSITIVE, R.string.dialog_button_dismiss)
                dialog.contentView?.text = e.stackTraceToString()
                dialog.titleView?.text = context.getString(R.string.text_details)
            }
            .positiveText(R.string.dialog_button_dismiss)
            .positiveColorRes(R.color.dialog_button_default)
            .onPositive { dialog, _ -> dialog.dismiss() }
            .showAdaptive()
    }

    // ========== Floating window lifecycle / 浮动窗口生命周期 ==========

    override fun show() = show(false)

    @ScriptInterface
    fun show(isReset: Boolean) {
        cancelCountDownTimer()
        if (!mDisplayOverOtherAppsPerm.has()) {
            mDisplayOverOtherAppsPerm.config()
            ViewUtils.showToast(context, R.string.error_no_display_over_other_apps_permission)
            return
        }
        if (isReset) {
            uiHandler.post {
                hideNow()
                clearStates()
            }
        } else if (isShowing) {
            return
        }
        isShowing = true
        uiHandler.post { showOnUiThread() }
    }

    private fun showOnUiThread() {
        isShowing = true
        mFloaty.resetMoreButtonIcon()
        if (mWindowRequested) {
            // Already created (or queued in a starting service); make sure it is expanded.
            // zh-CN: 已创建 (或已在启动中的服务里排队); 确保处于展开状态.
            if (mFloatyWindow.isWindowAttached) mFloatyWindow.expand()
            return
        }
        replaceFinishedSiblings()
        applicationContext.startService(Intent(applicationContext, FloatyService::class.java))
        try {
            mWindowRequested = true
            sShowingConsoles.add(this)
            FloatyService.addWindow(mFloatyWindow)
        } catch (e: Exception) {
            e.printStackTrace()
            mWindowRequested = false
            isShowing = false
            sShowingConsoles.remove(this)
            ScriptRuntime.popException(e.message)
            ViewUtils.showToast(context, R.string.error_no_display_over_other_apps_permission)
        }
    }

    /**
     * Closes windows of the same script whose runtime already exited, so re-running a script
     * replaces its previous console instead of piling up windows.
     *
     * zh-CN: 关闭同一脚本且运行时已退出的窗口, 使重新运行脚本时替换其先前的控制台, 而不是堆叠窗口.
     */
    private fun replaceFinishedSiblings() {
        if (isGlobal) return
        val title = getTitle()
        sShowingConsoles
            .filter { it !== this && !it.isGlobal && it.isRuntimeExited && it.getTitle() == title }
            .forEach { it.hideNow() }
    }

    @ScriptInterface
    override fun hide() {
        uiHandler.post { hideNow() }
    }

    private fun hideNow() {
        mFloaty.exitResizeMode()
        runCatching { mFloatyWindow.close() }.onFailure { it.printStackTrace() }
        mWindowRequested = false
        isShowing = false
        sShowingConsoles.remove(this)
    }

    fun reset() {
        uiHandler.post {
            val wasShowing = isShowing
            hideNow()
            clearStates()
            if (wasShowing) show()
        }
    }

    private fun clearStates() {
        mFloaty.clearStates()
        mFloatyWindow.clearOnViewAttachedTask()
        configurator.clearStates()
    }

    fun expand() = uiHandler.post { if (mFloatyWindow.isWindowAttached) mFloatyWindow.expand() }

    fun collapse() = uiHandler.post { if (mFloatyWindow.isWindowAttached) mFloatyWindow.collapse() }

    /** Called by the floaty on the UI thread once its views are attached. zh-CN: 浮动窗口视图附加后由其在 UI 线程调用. */
    internal fun onFloatyAttached() {
        isShowing = true
        mWindowRequested = true
        if (hasPendingInput) mFloaty.onInputRequested()
    }

    /** Called by the floaty on the UI thread when its window was closed by any party. zh-CN: 浮动窗口被任一方关闭时由其在 UI 线程调用. */
    internal fun onFloatyDetached() {
        mWindowRequested = false
        isShowing = false
        sShowingConsoles.remove(this)
    }

    /** The user dragged the window; remember where it ended up. zh-CN: 用户拖动了窗口; 记录其最终位置. */
    internal fun onUserMoved(x: Int, y: Int) {
        configurator.setPosition(x.toDouble(), y.toDouble())
        configurator.setGravity(Gravity.NO_GRAVITY)
    }

    /** The user resized the window; remember the size and pin the current corner. zh-CN: 用户调整了窗口尺寸; 记录尺寸并固定当前位置. */
    internal fun onUserResized(width: Int, height: Int) {
        mFloaty.currentContentPosition?.let { configurator.setPosition(it.x.toDouble(), it.y.toDouble()) }
        configurator.setGravity(Gravity.NO_GRAVITY)
        configurator.setSize(width.toDouble(), height.toDouble())
    }

    /** Called by the owning runtime when it exits. zh-CN: 所属运行时退出时调用. */
    fun onRuntimeExit() {
        isRuntimeExited = true
        cancelPendingInputs()
    }

    private fun cancelCountDownTimer() {
        uiHandler.post {
            mCountDownTimer?.cancel()
            mCountDownTimer = null
        }
    }

    @JvmOverloads
    fun hideDelayed(exitOnCloseTimeout: Long = configurator.exitOnCloseTimeout) {
        configurator.resetExitOnClose()
        uiHandler.post {
            mCountDownTimer?.cancel()
            mCountDownTimer = object : CountDownTimer(exitOnCloseTimeout, 500) {
                override fun onTick(millisUntilFinished: Long) {
                    if (!isShowing) {
                        cancel()
                    } else when (/* remaining = */ ceil(millisUntilFinished / 1.0e3).toInt()) {
                        9 -> mFloaty.setMoreButtonIcon(R.drawable.ic_looks_9_white_48dp)
                        8 -> mFloaty.setMoreButtonIcon(R.drawable.ic_looks_8_white_48dp)
                        7 -> mFloaty.setMoreButtonIcon(R.drawable.ic_looks_7_white_48dp)
                        6 -> mFloaty.setMoreButtonIcon(R.drawable.ic_looks_6_white_48dp)
                        5 -> mFloaty.setMoreButtonIcon(R.drawable.ic_looks_5_white_48dp)
                        4 -> mFloaty.setMoreButtonIcon(R.drawable.ic_looks_4_white_48dp)
                        3 -> mFloaty.setMoreButtonIcon(R.drawable.ic_looks_3_white_48dp)
                        2 -> mFloaty.setMoreButtonIcon(R.drawable.ic_looks_2_white_48dp)
                        1 -> mFloaty.setMoreButtonIcon(R.drawable.ic_looks_1_white_48dp)
                    }
                }

                override fun onFinish() {
                    hide()
                    mCountDownTimer = null
                }
            }.start()
        }
    }

    // ========== Window configuration / 窗口配置 ==========

    override fun setTitle(title: CharSequence?) {
        configurator.setTitle(title)
        mFloaty.postApply { applyTitle() }
    }

    fun getTitle(): String = configurator.title?.toString() ?: DEFAULT_TITLE

    @ScriptInterface
    override fun setSize(w: Double, h: Double) {
        configurator.setSize(w, h)
        mFloaty.postApply {
            applySize()
            applyPlacement()
        }
    }

    @ScriptInterface
    fun getSize(): Size {
        mFloaty.currentSize?.let { return Size(it.width.toDouble(), it.height.toDouble()) }
        return configurator.size ?: Size(mFloaty.defaultViewWidth, mFloaty.defaultViewHeight)
    }

    @ScriptInterface
    override fun setPosition(x: Double, y: Double) {
        configurator.setPosition(x, y)
        configurator.setGravity(Gravity.NO_GRAVITY)
        mFloaty.postApply { applyPlacement() }
    }

    @ScriptInterface
    fun getPosition(): Point {
        mFloaty.currentContentPosition?.let { return Point(it.x.toDouble(), it.y.toDouble()) }
        return configurator.position ?: Point()
    }

    @ScriptInterface
    fun setGravity(gravity: Int) {
        configurator.setGravity(gravity)
        mFloaty.postApply { applyPlacement() }
    }

    @ScriptInterface
    fun setGravity(gravity: String) = setGravity(Gravities.parse(gravity))

    @ScriptInterface
    fun getGravity() = configurator.gravity

    @ScriptInterface
    fun setTouchable() = setTouchable(DEFAULT_TOUCHABLE)

    @ScriptInterface
    override fun setTouchable(touchable: Boolean) {
        configurator.setTouchable(touchable)
        mFloaty.postApply { applyTouchable() }
    }

    @ScriptInterface
    fun setTouchThrough() = setTouchThrough(DEFAULT_TOUCH_THROUGH)

    @ScriptInterface
    fun setTouchThrough(touchThrough: Boolean) = setTouchable(!touchThrough)

    @ScriptInterface
    fun isTouchable(): Boolean = configurator.isTouchable

    @ScriptInterface
    fun isTouchThrough() = !isTouchable()

    @ScriptInterface
    fun setAvoidStatusBar(avoid: Boolean) {
        configurator.setAvoidStatusBar(avoid)
        mFloaty.postApply { applyPlacement() }
    }

    @ScriptInterface
    fun isAvoidStatusBar() = configurator.isAvoidStatusBar

    @ScriptInterface
    fun setTimeVisible(visible: Boolean) {
        configurator.setTimeVisible(visible)
        mFloaty.postApply { applyDisplayOptions() }
    }

    @ScriptInterface
    fun isTimeVisible() = configurator.isTimeVisible

    @ScriptInterface
    fun setTimeFormat(pattern: String?) {
        configurator.setTimeFormat(pattern)
        mFloaty.postApply { applyDisplayOptions() }
    }

    @ScriptInterface
    fun getTimeFormat() = configurator.timeFormat

    @ScriptInterface
    fun setColorful(colorful: Boolean) {
        configurator.setColorful(colorful)
        mFloaty.postApply { applyDisplayOptions() }
    }

    @ScriptInterface
    fun isColorful() = configurator.isColorful

    @ScriptInterface
    fun setInputVisible(visible: Boolean) {
        configurator.setInputVisible(visible)
        mFloaty.postApply { applyInputVisibility() }
    }

    @ScriptInterface
    fun isInputVisible() = configurator.isInputVisible

    @ScriptInterface
    fun getTitleTextSize() = configurator.titleTextSize ?: mFloaty.consoleView?.titleTextSize ?: DEFAULT_TITLE_TEXT_SIZE

    @ScriptInterface
    fun setTitleTextSize(size: Float) {
        configurator.setTitleTextSize(size)
        mFloaty.postApply { applyTitleStyle() }
    }

    @ScriptInterface
    fun getTitleTextColor() = configurator.titleTextColor ?: mFloaty.consoleView?.titleTextColor ?: DEFAULT_TITLE_TEXT_COLOR

    @ScriptInterface
    fun setTitleTextColor(color: Int) {
        configurator.setTitleTextColor(color)
        mFloaty.postApply { applyTitleStyle() }
    }

    @ScriptInterface
    fun getTitleBackgroundColor() = configurator.titleBackgroundColor

    @ScriptInterface
    fun setTitleBackgroundColor(@ColorInt color: Int) {
        configurator.setTitleBackgroundColor(color)
        mFloaty.postApply { applyTitleStyle() }
    }

    @ScriptInterface
    fun getTitleBackgroundTint() = configurator.titleBackgroundTint

    @ScriptInterface
    fun setTitleBackgroundTint(@ColorInt tint: Int?) {
        configurator.setTitleBackgroundTint(tint)
        mFloaty.postApply { applyTitleStyle() }
    }

    @ScriptInterface
    fun getTitleBackgroundAlpha() = configurator.titleBackgroundAlpha

    @ScriptInterface
    fun setTitleBackgroundAlpha(alpha: Double?) {
        configurator.setTitleBackgroundAlpha(alpha)
        mFloaty.postApply { applyTitleStyle() }
    }

    @ScriptInterface
    fun resetTitleBackgroundAlpha() = setTitleBackgroundAlpha(DEFAULT_ALPHA)

    @ScriptInterface
    fun getTitleIconsTint() = configurator.titleIconsTint

    @ScriptInterface
    fun setTitleIconsTint(color: Int?) {
        configurator.setTitleIconsTint(color)
        mFloaty.postApply { applyTitleStyle() }
    }

    @ScriptInterface
    fun getContentTextSize() = configurator.contentTextSize ?: mFloaty.consoleView?.textSize ?: 0f

    @ScriptInterface
    fun setContentTextSize(size: Float) {
        configurator.setContentTextSize(size)
        mFloaty.postApply { applyContentStyle() }
    }

    @ScriptInterface
    fun getContentTextColors(): Map<Int, Int> {
        return mFloaty.consoleView?.textColors ?: emptyMap()
    }

    @ScriptInterface
    fun setContentTextColors(colors: Array<out Int?>) {
        configurator.setContentTextColors(colors)
        mFloaty.postApply { applyContentStyle() }
    }

    @ScriptInterface
    fun getContentBackgroundColor() = configurator.contentBackgroundColor

    @ScriptInterface
    fun setContentBackgroundColor(@ColorInt color: Int) {
        configurator.setContentBackgroundColor(color)
        mFloaty.postApply { applyContentStyle() }
    }

    @ScriptInterface
    fun getContentBackgroundTint() = configurator.contentBackgroundTint

    @ScriptInterface
    fun setContentBackgroundTint(@ColorInt tint: Int?) {
        configurator.setContentBackgroundTint(tint)
        mFloaty.postApply { applyContentStyle() }
    }

    @ScriptInterface
    fun getContentBackgroundAlpha() = configurator.contentBackgroundAlpha

    @ScriptInterface
    fun setContentBackgroundAlpha(alpha: Double?) {
        configurator.setContentBackgroundAlpha(alpha)
        mFloaty.postApply { applyContentStyle() }
    }

    @ScriptInterface
    fun resetContentBackgroundAlpha() = setContentBackgroundAlpha(DEFAULT_ALPHA)

    @ScriptInterface
    fun setTextSize(size: Float) {
        setTitleTextSize(size)
        setContentTextSize(size)
    }

    @ScriptInterface
    fun setTextColor(color: Int) {
        setTitleTextColor(color)
        setContentTextColors(Array(6) { color })
    }

    @ScriptInterface
    fun setBackgroundColor(color: Int) {
        setTitleBackgroundColor(color)
        setContentBackgroundColor(color)
    }

    @ScriptInterface
    fun setBackgroundTint(color: Int?) {
        setTitleBackgroundTint(color)
        setContentBackgroundTint(color)
    }

    @ScriptInterface
    fun setBackgroundAlpha(alpha: Double?) {
        setTitleBackgroundAlpha(alpha)
        setContentBackgroundAlpha(alpha)
    }

    @ScriptInterface
    fun resetBackgroundAlpha() {
        resetTitleBackgroundAlpha()
        resetContentBackgroundAlpha()
    }

    @ScriptInterface
    fun setExitOnClose(timeout: Long) {
        configurator.setExitOnClose(timeout)
    }

    @JvmOverloads
    @ScriptInterface
    fun setExitOnClose(exitOnClose: Boolean = true) {
        configurator.setExitOnClose(exitOnClose)
    }

    @ScriptInterface
    fun getExitOnClose() = when (configurator.isExitOnClose) {
        false -> false
        else -> configurator.exitOnCloseTimeout
    }

    private fun getStackTrace(t: Throwable) = ScriptRuntime.getStackTrace(t, false)

    private fun parseAlpha(alpha: Double?) = ColorUtils.toUint8(alpha ?: DEFAULT_ALPHA, true) / 255.0

    interface LogListener {

        fun onNewLog(logEntry: LogEntry)

        fun onLogClear()

        /** The number of scripts waiting for a line on this console changed. zh-CN: 在本控制台上等待输入的脚本数量发生变化. */
        fun onInputRequestsChanged(pendingCount: Int) {
            /* Empty body. */
        }

    }

    class LogEntry @JvmOverloads constructor(
        val id: Int,
        @JvmField val level: Int,
        @JvmField val content: CharSequence,
        /** Epoch milliseconds of the print call. zh-CN: 打印调用的时间戳 (epoch 毫秒). */
        @JvmField val timestamp: Long = System.currentTimeMillis(),
    ) : Comparable<LogEntry> {
        override fun compareTo(other: LogEntry) = id.compareTo(other.id)
    }

    /**
     * The desired state of the floating window. Setters return `this` so that `console.build()`
     * can map option keys to them by name.
     *
     * zh-CN: 浮动窗口的期望状态. setter 返回 `this`, 以便 `console.build()` 按名称把选项键映射到它们.
     */
    @Suppress("unused")
    inner class Configurator {

        @Volatile
        var size: Size? = null
            private set

        @Volatile
        var position: Point? = null
            private set

        @Volatile
        var gravity: Int = DEFAULT_GRAVITY
            private set

        @Volatile
        var title: CharSequence? = null
            private set

        @Volatile
        var titleTextSize: Float? = null
            private set

        @Volatile
        var titleTextColor: Int? = null
            private set

        @Volatile
        var titleBackgroundColor: Int = applicationContext.getColor(DEFAULT_TITLE_BAR_BG_COLOR_RES)

        @Volatile
        var titleBackgroundAlpha: Double = DEFAULT_ALPHA
            private set

        @Volatile
        var titleBackgroundTint: Int? = null

        @Volatile
        var titleIconsTint: Int? = null
            private set

        @Volatile
        var contentTextSize: Float? = null
            private set

        @Volatile
        var contentTextColors: Array<out Int?>? = null
            private set

        @Volatile
        var contentBackgroundColor: Int = applicationContext.getColor(DEFAULT_CONTENT_BG_COLOR_RES)

        @Volatile
        var contentBackgroundAlpha: Double = DEFAULT_ALPHA
            private set

        @Volatile
        var contentBackgroundTint: Int? = null

        @Volatile
        var isTouchable: Boolean = DEFAULT_TOUCHABLE
            private set

        @Volatile
        var isExitOnClose: Boolean = false
            private set

        @Volatile
        var exitOnCloseTimeout: Long = DEFAULT_EXIT_ON_CLOSE_TIMEOUT
            private set

        @Volatile
        var isTimeVisible: Boolean = ConsoleTimeSettings.FLOATING_WINDOW.isTimeVisible
            private set

        @Volatile
        var timeFormat: String = ConsoleTimeSettings.FLOATING_WINDOW.timeFormat
            private set

        @Volatile
        var isColorful: Boolean = DEFAULT_COLORFUL
            private set

        @Volatile
        var isAvoidStatusBar: Boolean = DEFAULT_AVOID_STATUS_BAR
            private set

        @Volatile
        var isInputVisible: Boolean = DEFAULT_INPUT_VISIBLE
            private set

        fun setSize(w: Double, h: Double) = also {
            size = Size(w, h)
        }

        fun setPosition(x: Double, y: Double) = also {
            position = Point(x, y)
        }

        fun setGravity(gravity: Int?) = also {
            this.gravity = gravity ?: DEFAULT_GRAVITY
        }

        fun setTitle(title: CharSequence?) = also {
            this.title = title ?: DEFAULT_TITLE
        }

        fun setTitleTextSize(size: Float) = also {
            titleTextSize = size
        }

        fun setTitleTextColor(@OmniColor color: Any?) = also {
            titleTextColor = color?.let { Colors.toIntRhino(it) }
        }

        fun setTitleBackgroundColor(@OmniColor color: Any?) = also {
            titleBackgroundColor = color?.let { Colors.toIntRhino(it) } ?: applicationContext.getColor(DEFAULT_TITLE_BAR_BG_COLOR_RES)
        }

        fun setTitleBackgroundTint(@OmniColor tint: Any?) = also {
            titleBackgroundTint = tint?.let { Colors.toIntRhino(it) }
        }

        fun setTitleBackgroundAlpha(alpha: Double?) = also {
            titleBackgroundAlpha = parseAlpha(alpha)
        }

        fun setTitleIconsTint(@OmniColor color: Any?) = also {
            titleIconsTint = color?.let { Colors.toIntRhino(it) }
        }

        fun setContentTextSize(size: Float) = also {
            contentTextSize = size
        }

        fun setContentTextColors(colors: Array<out @OmniColor Any?>) = also {
            contentTextColors = colors.map { it?.let { Colors.toIntRhino(it) } }.toTypedArray()
        }

        fun setContentBackgroundColor(@OmniColor color: Any?) = also {
            contentBackgroundColor = color?.let { Colors.toIntRhino(it) } ?: applicationContext.getColor(DEFAULT_CONTENT_BG_COLOR_RES)
        }

        fun setContentBackgroundTint(@OmniColor tint: Any?) = also {
            contentBackgroundTint = tint?.let { Colors.toIntRhino(it) }
        }

        fun setContentBackgroundAlpha(alpha: Double?) = also {
            contentBackgroundAlpha = parseAlpha(alpha)
        }

        fun setTextSize(size: Float) = also {
            setTitleTextSize(size)
            setContentTextSize(size)
        }

        fun setTextColor(@OmniColor color: Any?) = also {
            setTitleTextColor(color)
            setContentTextColors(Array(6) { color })
        }

        fun setBackgroundColor(@OmniColor color: Any?) = also {
            setTitleBackgroundColor(color)
            setContentBackgroundColor(color)
        }

        fun setBackgroundTint(@OmniColor color: Any?) = also {
            setTitleBackgroundTint(color)
            setContentBackgroundTint(color)
        }

        fun setBackgroundAlpha(alpha: Double?) = also {
            setTitleBackgroundAlpha(alpha)
            setContentBackgroundAlpha(alpha)
        }

        fun setExitOnClose(timeout: Long) = also {
            setExitOnClose(true)
            exitOnCloseTimeout = timeout
        }

        @JvmOverloads
        fun setExitOnClose(exitOnClose: Boolean = DEFAULT_EXIT_ON_CLOSE) = also {
            isExitOnClose = exitOnClose
        }

        fun setTouchable(touchable: Boolean = DEFAULT_TOUCHABLE) = also {
            isTouchable = touchable
        }

        fun setTouchThrough(touchThrough: Boolean = DEFAULT_TOUCH_THROUGH) = also {
            isTouchable = !touchThrough
        }

        fun setTimeVisible(visible: Boolean = DEFAULT_TIME_VISIBLE) = also {
            isTimeVisible = visible
        }

        fun setTimeFormat(pattern: String?) = also {
            timeFormat = ConsoleLogFormat.normalizeTimePattern(pattern)
        }

        fun setColorful(colorful: Boolean = DEFAULT_COLORFUL) = also {
            isColorful = colorful
        }

        fun setAvoidStatusBar(avoid: Boolean = DEFAULT_AVOID_STATUS_BAR) = also {
            isAvoidStatusBar = avoid
        }

        fun setInputVisible(visible: Boolean = DEFAULT_INPUT_VISIBLE) = also {
            isInputVisible = visible
        }

        fun clearStates() {
            size = null
            position = null
            gravity = DEFAULT_GRAVITY
            title = null
            titleTextSize = null
            titleTextColor = null
            titleBackgroundColor = applicationContext.getColor(DEFAULT_TITLE_BAR_BG_COLOR_RES)
            titleBackgroundAlpha = DEFAULT_ALPHA
            titleBackgroundTint = null
            contentBackgroundColor = applicationContext.getColor(DEFAULT_CONTENT_BG_COLOR_RES)
            contentBackgroundAlpha = DEFAULT_ALPHA
            contentBackgroundTint = null
            titleIconsTint = null
            contentTextSize = null
            contentTextColors = null
            isTouchable = DEFAULT_TOUCHABLE
            isExitOnClose = false
            exitOnCloseTimeout = DEFAULT_EXIT_ON_CLOSE_TIMEOUT
            isTimeVisible = ConsoleTimeSettings.FLOATING_WINDOW.isTimeVisible
            timeFormat = ConsoleTimeSettings.FLOATING_WINDOW.timeFormat
            isColorful = DEFAULT_COLORFUL
            isAvoidStatusBar = DEFAULT_AVOID_STATUS_BAR
            isInputVisible = DEFAULT_INPUT_VISIBLE
        }

        internal fun resetExitOnClose() {
            setExitOnClose(false)
            exitOnCloseTimeout = DEFAULT_EXIT_ON_CLOSE_TIMEOUT
        }

        @JvmOverloads
        fun show(isReset: Boolean = false) = this@ConsoleImpl.show(isReset)

    }

    companion object {

        private val TAG = ConsoleImpl::class.java.simpleName

        /** Runtime consoles whose window is requested or attached. zh-CN: 已请求或已附加窗口的控制台. */
        private val sShowingConsoles = CopyOnWriteArraySet<ConsoleImpl>()

        /** How many consoles show a window, for the memory gate (docs/memory/ROADMAP.md P4.2). zh-CN: 显示窗口的控制台数, 供内存门禁使用. */
        @JvmStatic
        fun showingConsoleCount() = sShowingConsoles.size

        /** Soft cap of retained log entries; older ones are dropped. zh-CN: 保留日志条目的软上限, 更早的条目被丢弃. */
        const val MAX_LOG_ENTRIES = 4096
        const val DEFAULT_TITLE = ""
        const val DEFAULT_ALPHA = 1.0
        const val DEFAULT_TOUCHABLE = true
        const val DEFAULT_TOUCH_THROUGH = true
        const val DEFAULT_GRAVITY = Gravity.NO_GRAVITY
        const val DEFAULT_EXIT_ON_CLOSE_TIMEOUT = 5000L
        const val DEFAULT_EXIT_ON_CLOSE = true
        const val DEFAULT_TIME_VISIBLE = false
        const val DEFAULT_COLORFUL = true
        const val DEFAULT_AVOID_STATUS_BAR = true
        const val DEFAULT_INPUT_VISIBLE = false
        const val DEFAULT_TITLE_TEXT_SIZE = 16f
        const val DEFAULT_TITLE_TEXT_COLOR = -0x1 /* white */
        const val INPUT_ECHO_PREFIX = "> "

        @JvmStatic
        @Suppress("SpellCheckingInspection")
        fun createLogExportFileName(): String =
            "autojs6-log-${DateTimeFormat.forPattern("yyyyMMdd-HHmmss").print(System.currentTimeMillis())}.txt"

        val DEFAULT_TITLE_BAR_BG_COLOR_RES = R.color.floating_console_title_bar_bg
        val DEFAULT_CONTENT_BG_COLOR_RES = R.color.floating_console_content_bg

    }

}
