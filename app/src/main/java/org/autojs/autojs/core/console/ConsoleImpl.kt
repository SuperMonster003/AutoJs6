package org.autojs.autojs.core.console

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
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
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.annotation.OmniColor
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.permission.DisplayOverOtherAppsPermission
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractConsole
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.ResizableExpandableFloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.gesture.DragGesture
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.IntentUtils.startSafely
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.joda.time.format.DateTimeFormat
import org.opencv.core.Point
import org.opencv.core.Size
import java.io.IOException
import java.lang.ref.WeakReference
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

    private val applicationContext = uiHandler.applicationContext

    private val mSafeSizeToSend: Int = 2.0.pow(16).toInt()
    private val mSafeSizeToCopy: Int = 2.0.pow(19).toInt()

    private var mLogListeners = ArrayList<WeakReference<LogListener?>>()
    private var mConsoleView: WeakReference<ConsoleView?>? = null

    @get:Synchronized
    private var mCountDownTimer: CountDownTimer? = null

    private val mIdCounter = AtomicInteger(0)
    private val mConsoleFloaty = ConsoleFloaty(this)
    private val mFloatyWindow = ResizableExpandableFloatyWindow(mConsoleFloaty)
    private val mDisplayOverOtherAppsPerm = DisplayOverOtherAppsPermission(applicationContext)

    private var mStage = Stage.INIT
    private val mPendingMutations = ArrayDeque<Mutation>()
    private var mIsDraining = false

    private val context: Context
        get() = mConsoleView?.get()?.context ?: applicationContext

    private val logEntriesJoint
        get() = synchronized(logEntries) {
            logEntries.joinToString("\n") { it.content }
        }

    val configurator = Configurator()

    val logEntries = ArrayList<LogEntry>()

    @Volatile
    var isShowing = false
        private set

    override fun setTitle(title: CharSequence?) {
        val niceTitle = title ?: ""
        configurator.setTitle(niceTitle)
        mConsoleFloaty.setTitle(niceTitle)
    }

    fun getTitle() = mConsoleFloaty.getTitle()

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

    fun setConsoleView(consoleView: ConsoleView?) {
        consoleView.let {
            mConsoleView = WeakReference(it)
            addLogListener(it)
        }
        // Stage advancement: View available.
        // zh-CN: 阶段推进: 视图可用.
        advanceStage(Stage.VIEW_ATTACHED)
        // First frame layout completion advancement.
        // zh-CN: 首帧布局完成推进.
        (getFloatingConsoleView() ?: consoleView)?.doOnLayout {
            advanceStage(Stage.LAYOUT_DONE)
        }
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
                ViewUtils.showToast(context, R.string.error_failed_to_copy)
                return
            }
            try {
                val cutOutEntries = cutOutEntries(mSafeSizeToCopy)
                copyAll(cutOutEntries.joinToString("\n") { it.content }, cutOutEntries.size)
            } catch (e: Exception) {
                e.printStackTrace()
                ViewUtils.showToast(context, R.string.error_failed_to_copy)
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
        send(entries.joinToString("\n") { it.content })
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
            .show()
    }

    override fun show() = show(false)

    @ScriptInterface
    fun show(isReset: Boolean) {
        initCountDownTimer()
        setCloseButton(R.drawable.ic_close_white_24dp)
        if (isReset) clearStates()

        if (!mDisplayOverOtherAppsPerm.has()) {
            mDisplayOverOtherAppsPerm.config()
            ViewUtils.showToast(context, R.string.error_no_display_over_other_apps_permission)
            return
        }
        if (isReset) reset()
        if (isShowing) return

        // Reset stage.
        // zh-CN: 重置阶段.
        mStage = Stage.INIT

        startFloatyService()
        uiHandler.post {
            try {
                FloatyService.addWindow(mFloatyWindow)
                configurator.size?.let { FloatyService.setInitialMeasure(it) }
                setTitleBarGestureListener()

                // Window created.
                // zh-CN: 窗口已创建.
                advanceStage(Stage.WINDOW_CREATED)

                mFloatyWindow.addOnViewAttachedTask {
                    // View attached -> VIEW_ATTACHED .
                    // zh-CN: 视图 attach -> VIEW_ATTACHED.
                    advanceStage(Stage.VIEW_ATTACHED)
                    // First frame layout -> LAYOUT_DONE.
                    // zh-CN: 首帧布局 -> LAYOUT_DONE.
                    mConsoleFloaty.expandedView?.doOnLayout { advanceStage(Stage.LAYOUT_DONE) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ScriptRuntime.popException(e.message)
                ViewUtils.showToast(context, R.string.error_no_display_over_other_apps_permission)
            }
        }

        isShowing = true

        configurator.title?.let { setTitle(it) }
        configurator.position?.run { setPosition(x, y, true) }
        setGravity(getGravity())
        configurator.titleTextSize?.let { setTitleTextSize(it) }
        configurator.titleTextColor?.let { setTitleTextColor(it) }

        setTitleBackgroundAlpha(getTitleBackgroundAlpha())
        setTitleBackgroundTint(getTitleBackgroundTint())
        setTitleBackgroundColor(getTitleBackgroundColor())

        configurator.titleIconsTint?.let { setTitleIconsTint(it) }
        configurator.contentTextSize?.let { setContentTextSize(it) }
        configurator.contentTextColors?.let { setContentTextColors(it) }

        setContentBackgroundAlpha(getContentBackgroundAlpha())
        setContentBackgroundTint(getContentBackgroundTint())
        setContentBackgroundColor(getContentBackgroundColor())

        setTouchable(isTouchable())
    }

    private fun setTitleBarGestureListener() {
        mFloatyWindow.windowBridge?.let { windowBridge ->
            mConsoleFloaty.titleBarView?.let { view ->
                DragGesture(windowBridge, view).apply { pressedAlpha = 1.0f }
            }
        }
    }

    private fun clearStates() {
        FloatyService.setInitialMeasure(null)
        mConsoleFloaty.clearStates()
        mFloatyWindow.clearOnViewAttachedTask()
        configurator.clearStates()
    }

    fun reset() {
        if (isShowing) {
            hide(true)
            uiHandler.post {
                clearStates()
                show()
            }
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
        applicationContext.startService(Intent(applicationContext, FloatyService::class.java))
    }

    @ScriptInterface
    override fun hide() = hide(false)

    private fun hide(ignoreSavingStates: Boolean) {
        uiHandler.post {
            try {
                if (!ignoreSavingStates) saveStates()
                mFloatyWindow.close()
            } catch (_: IllegalArgumentException) {
                /* Ignored. */
            }
            isShowing = false
        }
    }

    private fun saveStates() = uiHandler.post {
        // @Hint by SuperMonster003 on Feb 9, 2024.
        //  ! Position and size need to be saved as user may cause their changes.
        //  ! zh-CN: 需要保存位置和大小, 因为用户可能会改变它们.

        val bridge = mFloatyWindow.windowBridge ?: return@post

        configurator.setPosition(bridge.x.toDouble(), bridge.y.toDouble())
        configurator.setGravity(Gravity.NO_GRAVITY)
        configurator.setSize(bridge.width.toDouble(), bridge.height.toDouble())
    }

    @ScriptInterface
    override fun setSize(w: Double, h: Double) {
        configurator.setSize(w, h)
        enqueueMutation(Stage.VIEW_ATTACHED) {
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
    override fun setTouchable(touchable: Boolean) {
        configurator.setTouchable(touchable)
        enqueueMutation(Stage.WINDOW_CREATED) {
            mFloatyWindow.setTouchable(touchable)
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

    private fun setPosition(x: Double, y: Double, shouldSaveGravity: Boolean) {
        configurator.setPosition(x, y)
        if (!shouldSaveGravity) {
            configurator.setGravity(Gravity.NO_GRAVITY)
        }
        // Wait for VIEW_ATTACHED before setting to avoid attach backfill override.
        // zh-CN: 等待 VIEW_ATTACHED 再设置, 避免 attach 回填覆盖.
        enqueueMutation(Stage.VIEW_ATTACHED) {
            mFloatyWindow.windowBridge?.updatePosition(x.toInt(), y.toInt())
        }
        // After first frame layout is complete, if gravity is not set and position is overwritten, set position again to avoid layout backfill override.
        // zh-CN: 首帧布局完成后, 若未设置重力且位置被改写, 再设置一次位置, 避免 layout 回填覆盖.
        enqueueMutation(Stage.LAYOUT_DONE) {
            val bridge = mFloatyWindow.windowBridge ?: return@enqueueMutation
            if (configurator.gravity == Gravity.NO_GRAVITY) {
                val needFix = bridge.x != x.toInt() || bridge.y != y.toInt()
                if (needFix) bridge.updatePosition(x.toInt(), y.toInt())
            }
        }
    }

    @ScriptInterface
    fun getPosition(): Point {
        return mFloatyWindow.let { floatyWindow ->
            floatyWindow.windowBridge ?: return Point()
            Point(floatyWindow.windowBridge!!.x.toDouble(), floatyWindow.windowBridge!!.y.toDouble())
        }
    }

    @SuppressLint("RtlHardcoded")
    @ScriptInterface
    fun setGravity(gravity: Int) {
        configurator.setGravity(gravity)
        enqueueMutation(Stage.LAYOUT_DONE) {
            if (gravity <= Gravity.NO_GRAVITY) return@enqueueMutation
            val bridge = mFloatyWindow.windowBridge ?: return@enqueueMutation

            val isLtr = mFloatyWindow.windowView!!.layoutDirection == View.LAYOUT_DIRECTION_LTR
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
    fun getGravity() = configurator.gravity

    @ScriptInterface
    fun setGravity(gravity: String) = setGravity(Gravities.parse(gravity))

    @ScriptInterface
    fun getTitleTextSize() = mConsoleFloaty.getTitleTextSize()

    @ScriptInterface
    fun setTitleTextSize(size: Float) {
        configurator.setTitleTextSize(size)
        uiHandler.post {
            mConsoleFloaty.setTitleTextSize(size)
        }
    }

    @ScriptInterface
    fun getTitleTextColor() = mConsoleFloaty.getTitleTextColor()

    @ScriptInterface
    fun setTitleTextColor(color: Int) {
        configurator.setTitleTextColor(color)
        uiHandler.post {
            mConsoleFloaty.setTitleTextColor(color)
        }
    }

    @ScriptInterface
    fun getTitleBackgroundColor() = configurator.titleBackgroundColor

    @ScriptInterface
    fun setTitleBackgroundColor(@ColorInt color: Int) {
        configurator.setTitleBackgroundColor(color)
        enqueueMutation(Stage.VIEW_ATTACHED) {
            mConsoleFloaty.titleBarView?.let { view ->
                ViewUtils.setBackgroundColor(view, configurator.titleBackgroundColor)
            }
        }
    }

    @ScriptInterface
    fun getTitleBackgroundTint() = configurator.titleBackgroundTint

    @ScriptInterface
    fun setTitleBackgroundTint(@ColorInt tint: Int?) {
        configurator.setTitleBackgroundTint(tint)
        enqueueMutation(Stage.VIEW_ATTACHED) {
            mConsoleFloaty.setTitleBackgroundTint(configurator.titleBackgroundTint)
        }
    }

    @ScriptInterface
    fun getTitleBackgroundAlpha() = configurator.titleBackgroundAlpha

    @ScriptInterface
    fun setTitleBackgroundAlpha(alpha: Double?) {
        configurator.setTitleBackgroundAlpha(alpha)
        enqueueMutation(Stage.VIEW_ATTACHED) {
            mConsoleFloaty.setTitleBackgroundAlpha(configurator.titleBackgroundAlpha.toFloat())
        }
    }

    @ScriptInterface
    fun resetTitleBackgroundAlpha() = setTitleBackgroundAlpha(DEFAULT_ALPHA)

    @ScriptInterface
    fun setTitleIconsTint(color: Int?) {
        configurator.setTitleIconsTint(color)
        enqueueMutation(Stage.VIEW_ATTACHED) {
            mConsoleFloaty.setTitleIconsTint(color)
        }
    }

    @ScriptInterface
    fun getContentTextSize() = getFloatingConsoleView()?.textSize ?: 0f

    @ScriptInterface
    fun setContentTextSize(size: Float) {
        configurator.setContentTextSize(size)
        uiHandler.post {
            getFloatingConsoleView()?.textSize = size
        }
    }

    @ScriptInterface
    fun getContentTextColors(): Map<Int, Int> {
        return getFloatingConsoleView()?.textColors ?: emptyMap()
    }

    @ScriptInterface
    fun setContentTextColors(colors: Array<out Int?>) {
        configurator.setContentTextColors(colors)
        uiHandler.post {
            getFloatingConsoleView()?.setTextColors(colors)
        }
    }

    @ScriptInterface
    fun getContentBackgroundColor() = configurator.contentBackgroundColor

    @ScriptInterface
    fun setContentBackgroundColor(@ColorInt color: Int) {
        configurator.setContentBackgroundColor(color)
        enqueueMutation(Stage.VIEW_ATTACHED) {
            ViewUtils.setBackgroundColor(getFloatingConsoleView(), configurator.contentBackgroundColor)
        }
    }

    @ScriptInterface
    fun getContentBackgroundTint() = configurator.contentBackgroundTint

    @ScriptInterface
    fun setContentBackgroundTint(@ColorInt tint: Int?) {
        configurator.setContentBackgroundTint(tint)
        enqueueMutation(Stage.VIEW_ATTACHED) {
            val view = getFloatingConsoleView() ?: return@enqueueMutation
            view.background?.mutate()?.clearColorFilter()
            val tint = configurator.contentBackgroundTint
            ViewCompat.setBackgroundTintList(view, tint?.let { ColorStateList.valueOf(it) })
        }
    }

    @ScriptInterface
    fun getContentBackgroundAlpha() = configurator.contentBackgroundAlpha

    @ScriptInterface
    fun setContentBackgroundAlpha(alpha: Double?) {
        configurator.setContentBackgroundAlpha(alpha)
        enqueueMutation(Stage.VIEW_ATTACHED) {
            getFloatingConsoleView()?.background?.mutate()?.alpha = ColorUtils.toUint8(configurator.contentBackgroundAlpha, true)
        }
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
    fun setBackgroundTint(color: Int) {
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

    fun expand() = uiHandler.post { mFloatyWindow.expand() }

    fun collapse() = uiHandler.post { mFloatyWindow.collapse() }

    @JvmOverloads
    fun hideDelayed(exitOnCloseTimeout: Long = configurator.exitOnCloseTimeout) {
        configurator.resetExitOnClose()
        uiHandler.post {
            mCountDownTimer = object : CountDownTimer(exitOnCloseTimeout, 500) {
                override fun onTick(millisUntilFinished: Long) {
                    if (!isShowing) {
                        cancel()
                    } else when (/* remaining = */ ceil(millisUntilFinished / 1.0e3).toInt()) {
                        9 -> setCloseButton(R.drawable.ic_looks_9_white_48dp)
                        8 -> setCloseButton(R.drawable.ic_looks_8_white_48dp)
                        7 -> setCloseButton(R.drawable.ic_looks_7_white_48dp)
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

    private fun setCloseButton(resId: Int) = mConsoleFloaty.setCloseButton(resId)

    private fun parseAlpha(alpha: Double?) = ColorUtils.toUint8(alpha ?: DEFAULT_ALPHA, true) / 255.0

    private fun advanceStage(newStage: Stage) {
        if (newStage.ordinal <= mStage.ordinal) return
        mStage = newStage
        drainMutations()
    }

    private fun enqueueMutation(required: Stage, action: () -> Unit) {
        uiHandler.post {
            if (mStage.ordinal >= required.ordinal) {
                runCatching { action() }.onFailure { it.printStackTrace() }
            } else {
                mPendingMutations.addLast(Mutation(required, action))
            }
        }
    }

    private fun drainMutations() {
        if (mIsDraining) return
        mIsDraining = true
        try {
            while (mPendingMutations.isNotEmpty()) {
                val m = mPendingMutations.first()
                if (mStage.ordinal >= m.required.ordinal) {
                    mPendingMutations.removeFirst()
                    runCatching { m.action() }.onFailure { it.printStackTrace() }
                } else break
            }
        } finally {
            mIsDraining = false
        }
    }

    /**
     * Lock-free phased mutation queue.
     * zh-CN: 无锁阶段化变更队列.
     */
    private enum class Stage { INIT, WINDOW_CREATED, VIEW_ATTACHED, LAYOUT_DONE }

    private data class Mutation(val required: Stage, val action: () -> Unit)

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
        var titleBackgroundColor: Int = context.getColor(DEFAULT_TITLE_BAR_BG_COLOR_RES)

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
        var contentBackgroundColor: Int = context.getColor(DEFAULT_CONTENT_BG_COLOR_RES)

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
            titleBackgroundColor = color?.let { Colors.toIntRhino(it) } ?: context.getColor(DEFAULT_TITLE_BAR_BG_COLOR_RES)
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
            contentBackgroundColor = color.let { Colors.toIntRhino(it) }
        }

        fun setContentBackgroundTint(@OmniColor tint: Any?) {
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

        fun clearStates() {
            size = null
            position = null
            gravity = DEFAULT_GRAVITY
            title = null
            titleTextSize = null
            titleTextColor = null
            titleBackgroundColor = context.getColor(DEFAULT_TITLE_BAR_BG_COLOR_RES)
            titleBackgroundAlpha = DEFAULT_ALPHA
            titleBackgroundTint = null
            contentBackgroundColor = context.getColor(DEFAULT_CONTENT_BG_COLOR_RES)
            contentBackgroundAlpha = DEFAULT_ALPHA
            contentBackgroundTint = null
            titleIconsTint = null
            contentTextSize = null
            contentTextColors = null
            isTouchable = DEFAULT_TOUCHABLE
            isExitOnClose = false
            exitOnCloseTimeout = DEFAULT_EXIT_ON_CLOSE_TIMEOUT
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

        const val DEFAULT_TITLE = ""
        const val DEFAULT_ALPHA = 1.0
        const val DEFAULT_TOUCHABLE = true
        const val DEFAULT_TOUCH_THROUGH = true
        const val DEFAULT_GRAVITY = Gravity.NO_GRAVITY
        const val DEFAULT_EXIT_ON_CLOSE_TIMEOUT = 5000L
        const val DEFAULT_EXIT_ON_CLOSE = true

        val DEFAULT_TITLE_BAR_BG_COLOR_RES = R.color.floating_console_title_bar_bg
        val DEFAULT_CONTENT_BG_COLOR_RES = R.color.floating_console_content_bg

    }

}
