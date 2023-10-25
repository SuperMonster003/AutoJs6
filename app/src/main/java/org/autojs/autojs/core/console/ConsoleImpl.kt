package org.autojs.autojs.core.console

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CountDownTimer
import android.os.Looper
import android.util.Log
import androidx.annotation.ColorInt
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.pref.Language
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractConsole
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.gesture.DragGesture
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.joda.time.format.DateTimeFormat
import org.opencv.core.Point
import org.opencv.core.Size
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.pow


/**
 * Created by Stardust on 2017/5/2.
 * Modified by SuperMonster003 as of Apr 12, 2023.
 * Transformed by SuperMonster003 on Apr 12, 2023.
 */
open class ConsoleImpl(val uiHandler: UiHandler) : AbstractConsole() {

    var configurator = Configurator()

    private val mDefaultSafeDelay: Long = 360
    private val mSafeSizeToSend: Int = 2.0.pow(16).toInt()
    private val mSafeSizeToCopy: Int = 2.0.pow(19).toInt()

    private var mLogListeners = ArrayList<WeakReference<LogListener?>>()
    private var mConsoleView: WeakReference<ConsoleView?>? = null

    @get:Synchronized
    private var mCountDownTimer: CountDownTimer? = null

    private val context: Context
        get() = mConsoleView?.get()?.context ?: uiHandler.context

    private val mLockWindowShow = Object()
    private val mLockWindowCreated = Object()
    private val mLockConsoleView = Object()
    private val mIdCounter = AtomicInteger(0)
    private val mFloatyWindow: ResizableExpandableFloatyWindow
    private val mConsoleFloaty: ConsoleFloaty
    private val mInput: BlockingQueue<String> = ArrayBlockingQueue(1)
    private val mDisplayOverOtherAppsPerm = DisplayOverOtherAppsPermission(uiHandler.context)

    val logEntries = ArrayList<LogEntry>()

    @Volatile
    var isShowing = false
        private set

    private val logEntriesJoint
        get() = synchronized(logEntries) {
            logEntries.joinToString("\n") { it.content }
        }

    // val size: Size
    //     get() = configurator.size ?: Size()
    //
    // val position: Point
    //     get() = configurator.position ?: Point()
    //
    // val background: Int
    //     get() = configurator.background ?: -1

    init {
        synchronized(mLockWindowCreated) {
            mConsoleFloaty = ConsoleFloaty(this)
            mFloatyWindow = ResizableExpandableFloatyWindow(mConsoleFloaty)
            mLockWindowCreated.notify()
        }
    }

    class LogEntry(var id: Int, @JvmField var level: Int, @JvmField var content: CharSequence) : Comparable<LogEntry> {
        override fun compareTo(other: LogEntry) = 0
    }

    interface LogListener {
        fun onNewLog(logEntry: LogEntry?)
        fun onLogClear()
    }

    fun setConsoleView(consoleView: ConsoleView?) {
        consoleView.let {
            mConsoleView = WeakReference(it)
            addLogListener(it)
        }
        synchronized(mLockConsoleView) { mLockConsoleView.notify() }
    }

    private fun addLogListener(logListener: LogListener?) {
        mLogListeners.add(WeakReference(logListener))
    }

    fun printAllStackTrace(t: Throwable?) {
        println(Log.ERROR, ScriptRuntime.getStackTrace(t, true))
    }

    private fun getStackTrace(t: Throwable?) = ScriptRuntime.getStackTrace(t, false)

    override fun println(level: Int, charSequence: CharSequence): String? {
        val logEntry = LogEntry(mIdCounter.getAndIncrement(), level, charSequence)
        synchronized(logEntries) { logEntries.add(logEntry) }
        mLogListeners.forEach { it.get()?.onNewLog(logEntry) }
        return null
    }

    public override fun write(level: Int, data: CharSequence?) {
        data?.let { println(level, it) }
    }

    @ScriptInterface
    override fun clear() {
        synchronized(logEntries) { logEntries.clear() }
        mLogListeners.forEach { it.get()?.onLogClear() }
    }

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
                ViewUtils.showToast(context, R.string.text_failed_to_copy)
                return
            }
            try {
                val cutOutEntries = cutOutEntries(mSafeSizeToCopy)
                copyAll(cutOutEntries.joinToString("\n") { it.content }, cutOutEntries.size)
            } catch (e: Exception) {
                e.printStackTrace()
                ViewUtils.showToast(context, R.string.text_failed_to_copy)
            }
        }
    }

    fun export() {
        @Suppress("SpellCheckingInspection")
        mConsoleView?.get()?.export("autojs6-log-${DateTimeFormat.forPattern("yyyyMMdd-HHmmss").print(System.currentTimeMillis())}.txt")
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
            e.printStackTrace()
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(R.string.text_failed_to_export_log_entries)
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
                .onPositive { dialog, _ -> dialog.dismiss() }
                .show()
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
                    key(R.string.key_dialog_num_of_log_entries_exceeds_limit_for_sending),
                ).apply {
                    title(R.string.text_prompt)
                    content("$msgReason, $msgAction.")
                    cancelable(false)
                    negativeText(R.string.dialog_button_quit)
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
                e.printStackTrace()
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(R.string.text_failed_to_send_log_entries)
                    .cancelable(false)
                    .autoDismiss(false)
                    .neutralText(R.string.text_details)
                    .neutralColorRes(R.color.dialog_button_hint)
                    .onNeutral { dialog, _ ->
                        dialog.setActionButton(DialogAction.NEUTRAL, null)
                        dialog.setActionButton(DialogAction.POSITIVE, R.string.text_close)
                        dialog.contentView?.text = e.stackTraceToString()
                        dialog.titleView?.text = context.getString(R.string.text_details)
                    }
                    .positiveText(R.string.dialog_button_dismiss)
                    .onPositive { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }
    }

    private fun send(entries: MutableList<LogEntry>) {
        send(entries.joinToString("\n") { it.content })
    }

    private fun send(message: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
        }
        uiHandler.context.startActivity(Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
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

    override fun show() = show(false)

    @ScriptInterface
    fun show(isReset: Boolean) {
        initCountDownTimer()
        setCloseButton(R.drawable.ic_close_white_24dp)

        synchronized(mLockWindowShow) {
            if (!mDisplayOverOtherAppsPerm.has()) {
                mDisplayOverOtherAppsPerm.config()
                uiHandler.toast(R.string.error_no_display_over_other_apps_permission)
                return
            }
            if (isReset) reset()
            if (isShowing) return

            startFloatyService()
            runWithWindow {
                try {
                    FloatyService.addWindow(mFloatyWindow)
                    configurator.size?.let { FloatyService.addInitialMeasure(it) }
                    setTitleBarGestureListener()
                    // SecurityException: https://github.com/hyb1996-guest/AutoJsIssueReport/issues/4781
                } catch (e: Exception) {
                    e.printStackTrace()
                    ScriptRuntime.popException(e.message)
                    uiHandler.toast(R.string.error_no_display_over_other_apps_permission)
                }
            }
            synchronized(mLockWindowCreated) {
                isShowing = true
                configurator.title?.let { setTitle(it) }
                configurator.position?.run { setPosition(x, y) }
                configurator.titleTextSize?.let { setTitleTextSize(it) }
                configurator.titleTextColor?.let { setTitleTextColor(it) }
                configurator.titleBackgroundColor?.let { setTitleBackgroundColor(it) }
                configurator.titleBackgroundAlpha?.let { setTitleBackgroundAlpha(it) }
                configurator.titleIconsTint?.let { setTitleIconsTint(it) }
                configurator.contentTextSize?.let { setContentTextSize(it) }
                configurator.contentTextColors?.let { setContentTextColor(it) }
                configurator.contentBackgroundColor?.let { setContentBackgroundColor(it) }
                configurator.contentBackgroundAlpha?.let { setContentBackgroundAlpha(it) }
                mLockWindowCreated.wait(mDefaultSafeDelay)
            }
        }
    }

    private fun setTitleBarGestureListener() {
        mFloatyWindow.windowBridge?.let { windowBridge ->
            mConsoleFloaty.titleBarView?.let { view ->
                DragGesture(windowBridge, view).apply { pressedAlpha = 1.0f }
            }
        }
    }

    fun reset() {
        configurator = Configurator()
        mConsoleFloaty.clearStates()
        if (isShowing) {
            hide()
            uiHandler.postDelayed({ show() }, mDefaultSafeDelay)
        }
    }

    private fun initCountDownTimer() {
        mCountDownTimer?.apply {
            cancel()
            mCountDownTimer = null
        }
    }

    private fun startFloatyService() {
        uiHandler.context.startService(Intent(uiHandler.context, FloatyService::class.java))
    }

    @ScriptInterface
    override fun hide() = runWithWindow {
        synchronized(mLockWindowShow) {
            try {
                mFloatyWindow.close()
            } catch (ignored: IllegalArgumentException) {
                // Ignored.
            }
            isShowing = false
        }
    }

    @ScriptInterface
    override fun setSize(w: Double, h: Double) = runWithWindow {
        configurator.setSize(w, h)
        if (isShowing) {
            mConsoleFloaty.expandedView?.let {
                ViewUtils.setViewMeasure(it, w.toInt(), h.toInt())
            }
        }
    }

    @ScriptInterface
    override fun setPosition(x: Double, y: Double) = runWithWindow {
        configurator.setPosition(x, y)
        if (isShowing) {
            mFloatyWindow.windowBridge.updatePosition(x.toInt(), y.toInt())
        }
    }

    @ScriptInterface
    fun setTitleTextSize(size: Float) = runWithWindow {
        configurator.setTitleTextSize(size)
        mConsoleFloaty.setTitleTextSize(size)
    }

    @ScriptInterface
    fun setTitleTextColor(color: Int) = runWithWindow {
        configurator.setTitleTextColor(color)
        mConsoleFloaty.setTitleTextColor(color)
    }

    @ScriptInterface
    fun setTitleBackgroundColor(@ColorInt color: Int) = runWithWindow {
        configurator.setTitleBackgroundColor(color)
        mConsoleFloaty.setTitleBackgroundColor(color)
    }

    @ScriptInterface
    fun setTitleBackgroundAlpha(alpha: Int) = runWithWindow {
        configurator.setTitleBackgroundAlpha(alpha)
        mConsoleFloaty.setTitleBackgroundAlpha(alpha)
    }

    @ScriptInterface
    fun setTitleIconsTint(color: Int) = runWithWindow {
        configurator.setTitleIconsTint(color)
        mConsoleFloaty.setTitleIconsTint(color)
    }

    @ScriptInterface
    fun setContentTextSize(size: Float) = runWithWindow {
        configurator.setContentTextSize(size)
        getFloatingConsoleView()?.textSize = size
    }

    @ScriptInterface
    fun setContentTextColor(colors: Array<Int?>) = runWithWindow {
        configurator.setContentTextColor(colors)
        getFloatingConsoleView()?.setTextColors(colors)
    }

    @ScriptInterface
    fun setContentBackgroundColor(@ColorInt color: Int) = runWithWindow {
        configurator.setContentBackgroundColor(color)
        getFloatingConsoleView()?.setBackgroundColor(color)
    }

    @ScriptInterface
    fun setContentBackgroundAlpha(alpha: Int) = runWithWindow {
        configurator.setContentBackgroundAlpha(alpha)
        getFloatingConsoleView()?.background?.alpha = alpha
    }

    @ScriptInterface
    fun setTextSize(size: Float) {
        setTitleTextSize(size)
        setContentTextSize(size)
    }

    @ScriptInterface
    fun setTextColor(color: Int) {
        setTitleTextColor(color)
        setContentTextColor(arrayOf(color, color, color, color, color, color))
    }

    @ScriptInterface
    fun setBackgroundColor(color: Int) {
        setTitleBackgroundColor(color)
        setContentBackgroundColor(color)
    }

    @ScriptInterface
    fun setBackgroundAlpha(alpha: Int) {
        setTitleBackgroundAlpha(alpha)
        setContentBackgroundAlpha(alpha)
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
    override fun rawInput(): String? {
        if (getFloatingConsoleView() == null) {
            if (!isShowing) show()
            waitForConsoleView()
        }
        getFloatingConsoleView()?.showEditText()
        return try {
            mInput.take()
        } catch (e: InterruptedException) {
            throw ScriptInterruptedException()
        }
    }

    private fun getFloatingConsoleView(): FloatingConsoleView? {
        return mConsoleView?.get().takeIf { it is FloatingConsoleView } as? FloatingConsoleView
    }

    private fun waitForConsoleView() {
        synchronized(mLockConsoleView) {
            try {
                mLockConsoleView.wait()
            } catch (e: InterruptedException) {
                throw ScriptInterruptedException()
            }
        }
    }

    @ScriptInterface
    fun rawInput(data: Any?, vararg param: Any?) = rawInput().also { log(data, *param) }

    fun submitInput(input: CharSequence) = mInput.offer(input.toString())

    override fun setTitle(title: CharSequence?) {
        val niceTitle = title ?: ""
        configurator.setTitle(niceTitle)
        mConsoleFloaty.setTitle(niceTitle)
    }

    private fun setCloseButton(resId: Int) = mConsoleFloaty.setCloseButton(resId)

    override fun error(data: Any?, vararg options: Any?) {
        var niceData = data
        if (niceData is Throwable) {
            niceData = getStackTrace(niceData as Throwable?)
        }
        if (options.isNotEmpty()) {
            val sb = StringBuilder(niceData?.toString() ?: "")
            val newOptions = ArrayList<Any>()
            options.forEach { option ->
                if (option is Throwable) {
                    sb.append(getStackTrace(option)).append(" ")
                } else {
                    option?.let { newOptions.add(it) }
                }
            }
            niceData = sb.toString()
            if (newOptions.isEmpty()) {
                super.error(niceData, *newOptions.toTypedArray())
            } else {
                super.error(niceData)
            }
        } else {
            super.error(niceData, *options)
        }
    }

    private fun runWithWindow(r: Runnable) {
        if (isUiThread) r.run() else uiHandler.post(r)
    }

    private val isUiThread: Boolean
        get() = Looper.myLooper() == Looper.getMainLooper()

    fun expand() = uiHandler.post { mFloatyWindow.expand() }

    fun collapse() = uiHandler.post { mFloatyWindow.collapse() }

    @JvmOverloads
    fun hideDelayed(exitOnCloseTimeout: Long = configurator.exitOnCloseTimeout) {
        configurator.initExitOnClose()
        uiHandler.post {
            mCountDownTimer = object : CountDownTimer(exitOnCloseTimeout, 500) {
                override fun onTick(millisUntilFinished: Long) {
                    if (!isShowing) {
                        cancel()
                    } else when (/* remaining = */ ceil(millisUntilFinished / 1.0e3).toInt()) {
                        6 -> setCloseButton(R.drawable.ic_looks_6_white_48dp)
                        5 -> setCloseButton(R.drawable.ic_looks_5_white_48dp)
                        4 -> setCloseButton(R.drawable.ic_looks_4_white_48dp)
                        3 -> setCloseButton(R.drawable.ic_looks_3_white_48dp)
                        2 -> setCloseButton(R.drawable.ic_looks_2_white_48dp)
                        1 -> setCloseButton(R.drawable.ic_looks_1_white_48dp)
                    }
                }

                override fun onFinish() {
                    hide()
                    mCountDownTimer = null
                }
            }.start()
        }
    }

    inner class Configurator {

        var size: Size? = null
            private set
        var position: Point? = null
            private set
        var contentTextSize: Float? = null
            private set
        var contentTextColors: Array<Int?>? = null
            private set
        var contentBackgroundColor: Int? = null
            private set
        var contentBackgroundAlpha: Int? = null
            private set
        var titleTextSize: Float? = null
            private set
        var titleTextColor: Int? = null
            private set
        var titleBackgroundColor: Int? = null
            private set
        var titleBackgroundAlpha: Int? = null
            private set
        var titleIconsTint: Int? = null
            private set
        var isExitOnClose: Boolean = false
            private set
        var exitOnCloseTimeout: Long = DEFAULT_EXIT_ON_CLOSE_TIMEOUT
            private set
        var title: CharSequence? = null
            private set

        fun setSize(w: Double, h: Double) = also {
            size = Size(w, h)
        }

        fun setPosition(x: Double, y: Double) = also {
            position = Point(x, y)
        }

        fun setTitle(title: CharSequence?) = also {
            this.title = title ?: ""
        }

        fun setTitleTextSize(size: Float) = also {
            titleTextSize = size
        }

        fun setTitleTextColor(color: Int) = also {
            titleTextColor = color
        }

        fun setTitleBackgroundColor(color: Int) = also {
            titleBackgroundColor = color
        }

        fun setTitleBackgroundAlpha(alpha: Int) = also {
            titleBackgroundAlpha = alpha
        }

        fun setTitleIconsTint(color: Int) = also {
            titleIconsTint = color
        }

        fun setContentTextSize(size: Float) = also {
            contentTextSize = size
        }

        fun setContentTextColor(colors: Array<Int?>) = also {
            contentTextColors = colors
        }

        fun setContentBackgroundColor(color: Int) = also {
            contentBackgroundColor = color
        }

        fun setContentBackgroundAlpha(alpha: Int) = also {
            contentBackgroundAlpha = alpha
        }

        fun setTextSize(size: Float) = also {
            setTitleTextSize(size)
            setContentTextSize(size)
        }

        fun setTextColor(color: Int) = also {
            setTitleTextColor(color)
            setContentTextColor(arrayOf(color, color, color, color, color, color))
        }

        fun setBackgroundColor(color: Int) = also {
            setTitleBackgroundColor(color)
            setContentBackgroundColor(color)
        }

        fun setBackgroundAlpha(alpha: Int) = also {
            setTitleBackgroundAlpha(alpha)
            setContentBackgroundAlpha(alpha)
        }

        fun setExitOnClose(timeout: Long) = also {
            setExitOnClose(true)
            exitOnCloseTimeout = timeout
        }

        @JvmOverloads
        fun setExitOnClose(exitOnClose: Boolean = true) = also {
            isExitOnClose = exitOnClose
        }

        internal fun initExitOnClose() {
            setExitOnClose(false)
            exitOnCloseTimeout = DEFAULT_EXIT_ON_CLOSE_TIMEOUT
        }

        fun show() = this@ConsoleImpl.show()

    }

    companion object {

        private const val DEFAULT_EXIT_ON_CLOSE_TIMEOUT = 5000L

    }

}