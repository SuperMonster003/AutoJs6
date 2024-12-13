package org.autojs.autojs.core.console

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.CountDownTimer
import android.util.Log
import android.view.Gravity
import android.view.Gravity.BOTTOM
import android.view.Gravity.CENTER_HORIZONTAL
import android.view.Gravity.CENTER_VERTICAL
import android.view.Gravity.END
import android.view.Gravity.LEFT
import android.view.Gravity.RIGHT
import android.view.Gravity.START
import android.view.Gravity.TOP
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractConsole
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.gesture.DragGesture
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.DrawableUtils
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
import kotlin.math.roundToInt
import android.graphics.Point as AndroidPoint

/**
 * Created by Stardust on May 2, 2017.
 * Modified by SuperMonster003 as of Apr 12, 2023.
 * Transformed by SuperMonster003 on Apr 12, 2023.
 */
open class ConsoleImpl(val uiHandler: UiHandler) : AbstractConsole() {

    private val lock = Object()

    var configurator = Configurator()

    val logEntries = ArrayList<LogEntry>()

    @Volatile
    var isShowing = false
        private set

    private val mDefaultSafeDelay: Long = 360
    private val mSafeSizeToSend: Int = 2.0.pow(16).toInt()
    private val mSafeSizeToCopy: Int = 2.0.pow(19).toInt()

    private var mLogListeners = ArrayList<WeakReference<LogListener?>>()
    private var mConsoleView: WeakReference<ConsoleView?>? = null

    @get:Synchronized
    private var mCountDownTimer: CountDownTimer? = null

    private var mFloatyWindow: ResizableExpandableFloatyWindow? = null

    private val context: Context
        get() = mConsoleView?.get()?.context ?: uiHandler.applicationContext

    private val mLockWindowShow = Object()
    private val mLockWindowCreated = Object()
    private val mLockConsoleView = Object()
    private val mIdCounter = AtomicInteger(0)
    private val mConsoleFloaty: ConsoleFloaty
    private val mInput: BlockingQueue<String> = ArrayBlockingQueue(1)
    private val mDisplayOverOtherAppsPerm = DisplayOverOtherAppsPermission(uiHandler.applicationContext)

    private val logEntriesJoint
        get() = synchronized(logEntries) {
            logEntries.joinToString("\n") { it.content }
        }

    init {
        synchronized(mLockWindowCreated) {
            mConsoleFloaty = ConsoleFloaty(this)
            mFloatyWindow = ResizableExpandableFloatyWindow(mConsoleFloaty)
            mLockWindowCreated.notify()
        }
    }

    override fun setTitle(title: CharSequence?) {
        val niceTitle = title ?: ""
        configurator.setTitle(niceTitle)
        mConsoleFloaty.setTitle(niceTitle)
    }

    fun getTitle() = mConsoleFloaty.getTitle()

    // TODO by SuperMonster003 on Oct 24, 2024.
    //  ! Add an option in preferences,
    //  ! such as "Show detailed error stack trace in console".
    //  ! zh-CN:
    //  ! 增加设置选项, 如 "控制台打印详细的错误堆栈信息".
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

    @ScriptInterface
    override fun rawInput(): String? {
        if (getFloatingConsoleView() == null) {
            if (!isShowing) show()
            waitForConsoleView()
        }
        getFloatingConsoleView()?.showEditText()
        return try {
            mInput.take()
        } catch (_: InterruptedException) {
            throw ScriptInterruptedException()
        }
    }

    @ScriptInterface
    fun rawInput(data: Any?, vararg param: Any?) = rawInput().also { log(data, *param) }

    fun setConsoleView(consoleView: ConsoleView?) {
        consoleView.let {
            mConsoleView = WeakReference(it)
            addLogListener(it)
        }
        synchronized(mLockConsoleView) { mLockConsoleView.notify() }
    }

    fun printAllStackTrace(t: Throwable) {
        ScriptRuntime.getStackTrace(t, true)?.let {
            println(Log.ERROR, it)
        }
    }

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
                handleExceptionWithDialog(e, R.string.text_failed_to_send_log_entries)
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
            type = Mime.TEXT_PLAIN
        }
        uiHandler.applicationContext.startActivity(Intent.createChooser(sendIntent, null).apply {
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
            .onPositive { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun show() = show(false)

    @ScriptInterface
    fun show(isReset: Boolean) {
        initCountDownTimer()
        setCloseButton(R.drawable.ic_close_white_24dp)
        if (isReset) clearStates()

        synchronized(mLockWindowShow) {
            if (!mDisplayOverOtherAppsPerm.has()) {
                mDisplayOverOtherAppsPerm.config()
                ViewUtils.showToast(context, R.string.error_no_display_over_other_apps_permission)
                return
            }
            if (isReset) reset()
            if (isShowing) return

            runWithWindow({ startFloatyService() }) {
                try {
                    FloatyService.addWindow(mFloatyWindow)
                    configurator.size?.let { FloatyService.setInitialMeasure(it) }
                    setTitleBarGestureListener()
                    // SecurityException: https://github.com/hyb1996-guest/AutoJsIssueReport/issues/4781
                } catch (e: Exception) {
                    e.printStackTrace()
                    ScriptRuntime.popException(e.message)
                    ViewUtils.showToast(context, R.string.error_no_display_over_other_apps_permission)
                }
            }
            synchronized(mLockWindowCreated) {
                isShowing = true
                configurator.title?.let { setTitle(it) }
                configurator.position?.run { setPosition(x, y, true) }
                setGravity(configurator.gravity)
                configurator.titleTextSize?.let { setTitleTextSize(it) }
                configurator.titleTextColor?.let { setTitleTextColor(it) }
                setTitleBackgroundColor(configurator.titleBackgroundColor)
                configurator.titleIconsTint?.let { setTitleIconsTint(it) }
                configurator.contentTextSize?.let { setContentTextSize(it) }
                configurator.contentTextColors?.let { setContentTextColors(it) }
                setContentBackgroundColor(configurator.contentBackgroundColor)
                setTouchable(configurator.isTouchable)
                mLockWindowCreated.wait(mDefaultSafeDelay)
            }
        }
    }

    private fun setTitleBarGestureListener() {
        mFloatyWindow?.windowBridge?.let { windowBridge ->
            mConsoleFloaty.titleBarView?.let { view ->
                DragGesture(windowBridge, view).apply { pressedAlpha = 1.0f }
            }
        }
    }

    private fun clearStates() {
        FloatyService.setInitialMeasure(null)
        mConsoleFloaty.clearStates()
        mFloatyWindow?.clearOnViewAttachedTask()
        configurator = Configurator()
    }

    fun reset() {
        if (isShowing) {
            hide(true)
            uiHandler.postDelayed({
                clearStates()
                show()
            }, mDefaultSafeDelay)
        } else {
            clearStates()
        }
    }

    private fun initCountDownTimer() {
        mCountDownTimer?.apply {
            cancel()
            mCountDownTimer = null
        }
    }

    private fun startFloatyService() {
        uiHandler.applicationContext.startService(Intent(uiHandler.applicationContext, FloatyService::class.java))
    }

    @ScriptInterface
    override fun hide() = hide(false)

    private fun hide(ignoreSavingStates: Boolean) = runWithWindow {
        synchronized(mLockWindowShow) {
            try {
                if (!ignoreSavingStates) saveStates()
                mFloatyWindow!!.close()
            } catch (_: IllegalArgumentException) {
                /* Ignored. */
            }
            isShowing = false
        }
    }

    private fun saveStates() = runWithWindow {
        // @Hint by SuperMonster003 on Feb 9, 2024.
        //  ! Position and size need to be saved as user may cause their changes.
        //  ! zh-CN: 需要保存位置和大小, 因为用户可能会改变它们.

        val bridge = mFloatyWindow!!.windowBridge ?: return@runWithWindow

        configurator.setPosition(bridge.x.toDouble(), bridge.y.toDouble())
        configurator.setGravity(Gravity.NO_GRAVITY)
        configurator.setSize(bridge.width.toDouble(), bridge.height.toDouble())
    }

    @ScriptInterface
    override fun setSize(w: Double, h: Double) = runWithWindow({ configurator.setSize(w, h) }) {
        trySetting {
            mConsoleFloaty.expandedView?.let {
                ViewUtils.setViewMeasure(it, w.toInt(), h.toInt())
            }
        }
    }

    @ScriptInterface
    fun getSize(): Size {
        return mConsoleFloaty.expandedView?.layoutParams?.let { params ->
            Size(params.width.toDouble(), params.height.toDouble())
        } ?: Size(mConsoleFloaty.defaultViewWidth, mConsoleFloaty.defaultViewHeight)
    }

    @ScriptInterface
    fun setTouchable() = setTouchable(DEFAULT_TOUCHABLE)

    @ScriptInterface
    override fun setTouchable(touchable: Boolean) = runWithWindow({ configurator.setTouchable(touchable) }) {
        trySetting {
            mFloatyWindow!!.setTouchable(touchable)
        }
    }

    @ScriptInterface
    fun setTouchThrough() = setTouchThrough(DEFAULT_TOUCH_THROUGH)

    @ScriptInterface
    fun setTouchThrough(touchThrough: Boolean) {
        setTouchable(!touchThrough)
    }

    @ScriptInterface
    fun isTouchable(): Boolean {
        // return mFloatyWindow.let { floatyWindow ->
        //     floatyWindow?.windowLayoutParams ?: return DEFAULT_TOUCHABLE
        //     floatyWindow.windowLayoutParams!!.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0
        // }
        return configurator.isTouchable
    }

    @ScriptInterface
    fun isTouchThrough() = !isTouchable()

    @ScriptInterface
    override fun setPosition(x: Double, y: Double) = setPosition(x, y, false)

    private fun setPosition(x: Double, y: Double, isSaveGravity: Boolean) = runWithWindow({
        configurator.setPosition(x, y)
        if (!isSaveGravity) {
            configurator.setGravity(Gravity.NO_GRAVITY)
        }
    }) {
        trySetting { mFloatyWindow!!.windowBridge?.updatePosition(x.toInt(), y.toInt()) }
    }

    @ScriptInterface
    fun getPosition(): Point {
        return mFloatyWindow.let { floatyWindow ->
            floatyWindow?.windowBridge ?: return Point()
            Point(floatyWindow.windowBridge!!.x.toDouble(), floatyWindow.windowBridge!!.y.toDouble())
        }
    }

    @SuppressLint("RtlHardcoded")
    @ScriptInterface
    fun setGravity(gravity: Int) = runWithWindow({ configurator.setGravity(gravity) }) {
        trySetting {
            if (gravity <= Gravity.NO_GRAVITY) return@trySetting
            val bridge = mFloatyWindow!!.windowBridge ?: return@trySetting

            val isLtr = mFloatyWindow!!.windowView!!.layoutDirection == View.LAYOUT_DIRECTION_LTR
            val w = if (bridge.width > 0) bridge.width else mConsoleFloaty.defaultViewWidth.roundToInt()
            val h = if (bridge.height > 0) bridge.height else mConsoleFloaty.defaultViewHeight.roundToInt()
            val pt = AndroidPoint(
                if (bridge.x > 0) bridge.x else mConsoleFloaty.initialX,
                if (bridge.y > 0) bridge.y else mConsoleFloaty.initialY,
            )
            var tmp = gravity

            fun setGravityLeft() = pt.apply { x = 0 }
            fun setGravityTop() = pt.apply { y = 0 }
            fun setGravityRight() = pt.apply { x = bridge.screenWidth - w }
            fun setGravityBottom() = pt.apply { y = bridge.screenHeight - h }
            fun setGravityStart() = if (isLtr) setGravityLeft() else setGravityRight()
            fun setGravityEnd() = if (isLtr) setGravityRight() else setGravityLeft()
            fun setGravityCenterHorizontal() = pt.apply { x = (bridge.screenWidth - w) / 2 }
            fun setGravityCenterVertical() = pt.apply { y = (bridge.screenHeight - h) / 2 }

            fun check(aim /* gravity */: Int, f: () -> Unit) {
                if (tmp >= aim && tmp and aim > 0) {
                    f().also { tmp = tmp and aim.inv() }
                }
            }

            check(END, ::setGravityEnd)
            check(START, ::setGravityStart)
            check(BOTTOM, ::setGravityBottom)
            check(TOP, ::setGravityTop)
            check(CENTER_VERTICAL, ::setGravityCenterVertical)
            check(RIGHT, ::setGravityRight)
            check(LEFT, ::setGravityLeft)
            check(CENTER_HORIZONTAL, ::setGravityCenterHorizontal)

            bridge.updatePosition(pt.x, pt.y)

            if (tmp > 0) Log.w(TAG, "Argument gravity remained: $tmp")
        }
    }

    @ScriptInterface
    fun setGravity(gravity: String) = setGravity(Gravities.parse(gravity))

    @ScriptInterface
    fun getGravity() = configurator.gravity

    @ScriptInterface
    fun setTitleTextSize(size: Float) = runWithWindow({ configurator.setTitleTextSize(size) }) {
        mConsoleFloaty.setTitleTextSize(size)
    }

    @ScriptInterface
    fun getTitleTextSize() = mConsoleFloaty.getTitleTextSize()

    @ScriptInterface
    fun setTitleTextColor(color: Int) = runWithWindow({ configurator.setTitleTextColor(color) }) {
        mConsoleFloaty.setTitleTextColor(color)
    }

    @ScriptInterface
    fun getTitleTextColor() = mConsoleFloaty.getTitleTextColor()

    @ScriptInterface
    fun setTitleBackgroundColor(@ColorInt color: Int) = runWithWindow({ configurator.setTitleBackgroundColor(color) }) {
        mConsoleFloaty.setTitleBackgroundColor(color)
    }

    @ScriptInterface
    fun getTitleBackgroundColor() = configurator.titleBackgroundColor

    @ScriptInterface
    fun setTitleBackgroundTint(@ColorInt tint: Int) = runWithWindow({ configurator.setTitleBackgroundTint(tint) }) {
        mConsoleFloaty.setTitleBackgroundTint(tint)
    }

    @ScriptInterface
    fun setTitleBackgroundAlpha(alpha: Int) = runWithWindow({ configurator.setTitleBackgroundAlpha(alpha) }) {
        mConsoleFloaty.setTitleBackgroundAlpha(alpha)
    }

    @ScriptInterface
    fun resetTitleBackgroundAlpha() = setTitleBackgroundAlpha(-1)

    @ScriptInterface
    fun setTitleIconsTint(color: Int) = runWithWindow({ configurator.setTitleIconsTint(color) }) {
        mConsoleFloaty.setTitleIconsTint(color)
    }

    @ScriptInterface
    fun setContentTextSize(size: Float) = runWithWindow({ configurator.setContentTextSize(size) }) {
        getFloatingConsoleView()?.textSize = size
    }

    @ScriptInterface
    fun getContentTextSize() = getFloatingConsoleView()?.textSize ?: 0f

    @ScriptInterface
    fun setContentTextColors(colors: Array<out Int>) = runWithWindow({ configurator.setContentTextColors(colors) }) {
        getFloatingConsoleView()?.setTextColors(colors)
    }

    @ScriptInterface
    fun getContentTextColors(): Map<Int, Int> {
        return getFloatingConsoleView()?.textColors ?: emptyMap()
    }

    @ScriptInterface
    fun setContentBackgroundColor(@ColorInt color: Int) = runWithWindow({ configurator.setContentBackgroundColor(color) }) {
        setContentViewBackgroundColor(getFloatingConsoleView(), color)
    }

    @ScriptInterface
    fun getContentBackgroundColor() = configurator.contentBackgroundColor

    @ScriptInterface
    fun setContentBackgroundTint(@ColorInt tint: Int) = runWithWindow({ configurator.setContentBackgroundTint(tint) }) {
        val alpha = configurator.contentBackgroundColor.alpha
        val niceAlpha = parseAlpha(alpha, R.color.floating_console_content_bg)
        val color = Color.argb(niceAlpha, tint.red, tint.green, tint.blue)
        setContentViewBackgroundColor(getFloatingConsoleView(), color)
    }

    @ScriptInterface
    fun setContentBackgroundAlpha(alpha: Int) = runWithWindow({ configurator.setContentBackgroundAlpha(alpha) }) {
        val bg = configurator.contentBackgroundColor
        val niceAlpha = parseAlpha(alpha, R.color.floating_console_content_bg)
        val color = Color.argb(niceAlpha, bg.red, bg.green, bg.blue)
        setContentViewBackgroundColor(getFloatingConsoleView(), color)
    }

    @ScriptInterface
    fun resetContentBackgroundAlpha() = setContentBackgroundAlpha(-1)

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
    fun setBackgroundTint(color: Int) {
        setTitleBackgroundTint(color)
        setContentBackgroundTint(color)
    }

    @ScriptInterface
    fun setBackgroundAlpha(alpha: Int) {
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

    fun submitInput(input: CharSequence) = mInput.offer(input.toString())

    fun expand() = uiHandler.post { mFloatyWindow?.expand() }

    fun collapse() = uiHandler.post { mFloatyWindow?.collapse() }

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

    private fun addLogListener(logListener: LogListener?) {
        mLogListeners.add(WeakReference(logListener))
    }

    private fun getStackTrace(t: Throwable) = ScriptRuntime.getStackTrace(t, false)

    private fun getFloatingConsoleView() = mConsoleView?.get() as? FloatingConsoleView

    private fun waitForConsoleView() {
        synchronized(mLockConsoleView) {
            try {
                mLockConsoleView.wait()
            } catch (_: InterruptedException) {
                throw ScriptInterruptedException()
            }
        }
    }

    private fun setCloseButton(resId: Int) = mConsoleFloaty.setCloseButton(resId)

    private fun runWithWindow(r: Runnable) {
        mFloatyWindow ?: return
        uiHandler.post(r)
    }

    private fun runWithWindow(initializer: () -> Unit, r: Runnable) {
        initializer()
        runWithWindow(r)
    }

    private fun trySetting(setter: () -> Unit) {
        val f = Runnable {
            synchronized(lock) {
                var retries = 5
                while (retries-- > 0) {
                    try {
                        setter()
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    runCatching { Thread.sleep(mDefaultSafeDelay) }
                }
            }
        }
        if (isShowing) f.run() else mFloatyWindow!!.addOnViewAttachedTask { f.run() }
    }

    private fun setContentViewBackgroundColor(view: FloatingConsoleView?, color: Int) {
        view?.apply { background = DrawableUtils.setDrawableColorFilterSrc(background, color) }
    }

    private fun parseAlpha(alpha: Int, defaultRes: Int) = when {
        alpha < 0 -> context.getColor(defaultRes).alpha
        else -> alpha
    }

    interface LogListener {
        fun onNewLog(logEntry: LogEntry?)
        fun onLogClear()
    }

    class LogEntry(var id: Int, @JvmField var level: Int, @JvmField var content: CharSequence) : Comparable<LogEntry> {
        override fun compareTo(other: LogEntry) = 0
    }

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
        var titleBackgroundColor: Int = context.getColor(R.color.floating_console_title_bar_bg)
            internal set

        @Volatile
        var titleIconsTint: Int? = null
            private set

        @Volatile
        var contentTextSize: Float? = null
            private set

        @Volatile
        var contentTextColors: Array<out Int>? = null
            private set

        @Volatile
        var contentBackgroundColor: Int = context.getColor(R.color.floating_console_content_bg)
            private set

        @Volatile
        var isTouchable: Boolean = true
            private set

        @Volatile
        var isExitOnClose: Boolean = false
            private set

        @Volatile
        var exitOnCloseTimeout: Long = DEFAULT_EXIT_ON_CLOSE_TIMEOUT
            private set

        fun setSize(w: Double, h: Double) = also {
            size = Size(w, h)
        }

        fun setPosition(x: Double, y: Double) = also {
            position = Point(x, y)
        }

        fun setGravity(gravity: Int) = also {
            this.gravity = gravity
        }

        fun setTitle(title: CharSequence?) = also {
            this.title = title ?: DEFAULT_TITLE
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

        fun setTitleBackgroundTint(tint: Int) = also {
            titleBackgroundColor = titleBackgroundColor.run { Color.argb(alpha, tint.red, tint.green, tint.blue) }
        }

        fun setTitleBackgroundAlpha(alpha: Int) = also {
            val niceAlpha = parseAlpha(alpha, R.color.floating_console_title_bar_bg)
            titleBackgroundColor = titleBackgroundColor.run { Color.argb(niceAlpha, red, green, blue) }
        }

        fun setTitleIconsTint(color: Int) = also {
            titleIconsTint = color
        }

        fun setContentTextSize(size: Float) = also {
            contentTextSize = size
        }

        fun setContentTextColors(colors: Array<out Int>) = also {
            contentTextColors = colors
        }

        fun setContentBackgroundColor(color: Int) = also {
            contentBackgroundColor = color
        }

        fun setContentBackgroundTint(tint: Int) {
            contentBackgroundColor = contentBackgroundColor.run { Color.argb(alpha, tint.red, tint.green, tint.blue) }
        }

        fun setContentBackgroundAlpha(alpha: Int) = also {
            val niceAlpha = parseAlpha(alpha, R.color.floating_console_content_bg)
            contentBackgroundColor = contentBackgroundColor.run { Color.argb(niceAlpha, red, green, blue) }
        }

        fun setTextSize(size: Float) = also {
            setTitleTextSize(size)
            setContentTextSize(size)
        }

        fun setTextColor(color: Int) = also {
            setTitleTextColor(color)
            setContentTextColors(Array(6) { color })
        }

        fun setBackgroundColor(color: Int) = also {
            setTitleBackgroundColor(color)
            setContentBackgroundColor(color)
        }

        fun setBackgroundTint(color: Int) = also {
            setTitleBackgroundTint(color)
            setContentBackgroundTint(color)
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
        fun setExitOnClose(exitOnClose: Boolean = DEFAULT_EXIT_ON_CLOSE) = also {
            isExitOnClose = exitOnClose
        }

        fun setTouchable(touchable: Boolean = DEFAULT_TOUCHABLE) = also {
            isTouchable = touchable
        }

        fun setTouchThrough(touchThrough: Boolean = DEFAULT_TOUCH_THROUGH) = also {
            isTouchable = !touchThrough
        }

        internal fun initExitOnClose() {
            setExitOnClose(false)
            exitOnCloseTimeout = DEFAULT_EXIT_ON_CLOSE_TIMEOUT
        }

        @JvmOverloads
        fun show(isReset: Boolean = false) = this@ConsoleImpl.show(isReset)

    }

    companion object {

        private val TAG = ConsoleImpl::class.java.simpleName

        const val DEFAULT_TITLE = ""
        const val DEFAULT_TOUCHABLE = true
        const val DEFAULT_TOUCH_THROUGH = true
        const val DEFAULT_GRAVITY = Gravity.NO_GRAVITY
        const val DEFAULT_EXIT_ON_CLOSE_TIMEOUT = 5000L
        const val DEFAULT_EXIT_ON_CLOSE = true

    }

}