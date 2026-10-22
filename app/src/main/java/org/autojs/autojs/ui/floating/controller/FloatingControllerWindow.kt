package org.autojs.autojs.ui.floating.controller

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.core.view.isVisible
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.pref.keys.FloatingKeys
import org.autojs.autojs.model.script.Scripts
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.ui.enhancedfloaty.FloatyService
import org.autojs.autojs.ui.enhancedfloaty.FloatyWindow
import org.autojs.autojs.ui.enhancedfloaty.WindowBridge
import org.autojs.autojs.ui.enhancedfloaty.gesture.DragGesture
import org.autojs.autojs.ui.floating.FloatyWindowManger
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.ContextUtils.withHostConfiguration
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.FloatingControllerBinding
import kotlin.math.roundToInt

/**
 * A translucent floating toolbar for one script: drag handle, script name, run/stop, edit, log
 * and close. Tapping the handle collapses the toolbar to the handle alone. The view follows the
 * host app's night mode and language, and the run/stop icon uses the theme color adjusted for
 * contrast against the toolbar background.
 *
 * zh-CN: 单个脚本的半透明悬浮工具栏: 拖动手柄, 脚本名, 运行/停止, 编辑, 日志与关闭. 轻触手柄可折叠为仅剩手柄.
 * 视图跟随宿主应用的夜间模式与语言, 运行/停止图标使用按工具栏背景做过对比度修正的主题色.
 *
 * Created by SuperMonster003 on Sep 14, 2026.
 */
class FloatingControllerWindow(
    val controller: ScriptExecutionController,
    private val onClosed: (FloatingControllerWindow) -> Unit,
) : FloatyWindow() {

    private var mContext: Context? = null
    private var mBinding: FloatingControllerBinding? = null
    private var mExpanded = true

    var isClosed = false
        private set

    override fun onCreateView(service: FloatyService): View {
        val context = ContextThemeWrapper(service.withHostConfiguration(), R.style.AppTheme).also { mContext = it }
        val binding = FloatingControllerBinding.inflate(LayoutInflater.from(context)).also { mBinding = it }
        binding.title.text = controller.displayName
        applyColors(context, binding)
        binding.runOrStop.setOnClickListener { controller.toggle(context) }
        binding.edit.setOnClickListener { Scripts.edit(context, controller.file) }
        binding.log.setOnClickListener { showLog(context) }
        binding.close.setOnClickListener { close() }
        controller.onStateChanged = { applyState(it) }
        applyState(controller.state)
        return binding.root
    }

    override fun onCreateWindowLayoutParams(): WindowManager.LayoutParams {
        val (x, y) = initialPosition()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            FloatyWindowManger.getWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    override fun onViewCreated(view: View) {
        val bridge = windowBridge ?: return
        val binding = mBinding ?: return
        DragGesture(bridge, binding.dragHandle).apply {
            pressedAlpha = 0.85f
            unpressedAlpha = 1f
            setPositionFilter { x, y -> clampToScreen(bridge, view, x, y) }
            setOnDraggedViewClickListener { toggle() }
            setOnDragEndListener { savePosition(bridge) }
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        controller.release()
        super.close()
        mBinding = null
        mContext = null
        onClosed(this)
    }

    // ========== Collapse and expand / 折叠与展开 ==========

    fun toggle() {
        if (mExpanded) collapse() else expand()
    }

    fun collapse() = animateExpansion(false)

    fun expand() = animateExpansion(true)

    private fun animateExpansion(expand: Boolean) {
        val binding = mBinding ?: return
        if (mExpanded == expand) return
        mExpanded = expand
        val transition = TransitionSet()
            .setOrdering(TransitionSet.ORDERING_TOGETHER)
            .addTransition(Fade(if (expand) Fade.IN else Fade.OUT))
            .addTransition(ChangeBounds())
            .setDuration(TRANSITION_DURATION_MS)
        TransitionManager.beginDelayedTransition(binding.container, transition)
        binding.buttonContainer.isVisible = expand
        binding.title.isVisible = expand
        binding.dragHandle.animate().rotation(if (expand) 0f else 180f).setDuration(TRANSITION_DURATION_MS).start()
    }

    // ========== Appearance / 外观 ==========

    private fun applyColors(context: Context, binding: FloatingControllerBinding) {
        val background = context.getColor(R.color.floating_controller_background)
        // The theme color is adjusted against the opaque background so light and dark
        // backgrounds both keep the run/stop icon readable.
        // zh-CN: 主题色按不透明背景做对比度修正, 使运行/停止图标在浅色与深色背景上都清晰可读.
        val accent = ColorUtils.adjustThemeColorForContrast(ColorUtils.applyAlpha(background, 1.0), MIN_ACCENT_CONTRAST)
        val neutral = context.getColor(R.color.day_night)
        binding.title.setTextColor(neutral)
        listOf(binding.dragHandle, binding.edit, binding.log, binding.close).forEach {
            it.imageTintList = ColorStateList.valueOf(neutral)
        }
        binding.runOrStop.imageTintList = ColorStateList.valueOf(accent)
    }

    private fun applyState(state: ScriptExecutionController.State) {
        val binding = mBinding ?: return
        val context = mContext ?: return
        val running = state == ScriptExecutionController.State.RUNNING
        binding.runOrStop.setImageResource(if (running) R.drawable.ic_stop_24dp else R.drawable.ic_play_arrow_24dp)
        binding.runOrStop.contentDescription = context.getString(if (running) R.string.text_stop else R.string.text_run)
    }

    private fun showLog(context: Context) {
        // The global console floats over other apps like this toolbar; the log activity is the
        // fallback when the console cannot be shown.
        // zh-CN: 全局控制台与本工具栏一样悬浮于其他应用之上; 控制台无法显示时回退到日志页面.
        val console = AutoJs.instance.globalConsole
        val shown = runCatching {
            if (console.isShowing) console.hide() else console.show()
            true
        }.getOrDefault(false)
        if (!shown) LogActivity.launch(context, controller.displayName, controller.file.path)
    }

    // ========== Position / 位置 ==========

    private fun clampToScreen(bridge: WindowBridge, view: View, x: Int, y: Int): IntArray {
        val maxX = maxOf(0, bridge.screenWidth - view.width)
        val maxY = maxOf(0, bridge.screenHeight - view.height)
        return intArrayOf(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    private fun savePosition(bridge: WindowBridge) {
        val screenWidth = bridge.screenWidth.takeIf { it > 0 } ?: return
        val screenHeight = bridge.screenHeight.takeIf { it > 0 } ?: return
        FloatingKeys.FLOATING_CONTROLLER_POSITION_X_PERCENT.set(bridge.x / screenWidth.toFloat(), true)
        FloatingKeys.FLOATING_CONTROLLER_POSITION_Y_PERCENT.set(bridge.y / screenHeight.toFloat(), true)
    }

    private fun initialPosition(): Pair<Int, Int> {
        val screenWidth = ScreenMetrics.deviceScreenWidth
        val screenHeight = ScreenMetrics.deviceScreenHeight
        val x = FloatingKeys.FLOATING_CONTROLLER_POSITION_X_PERCENT.get().takeIf { it >= 0f }?.let { (it * screenWidth).roundToInt() } ?: (screenWidth / 10)
        val y = FloatingKeys.FLOATING_CONTROLLER_POSITION_Y_PERCENT.get().takeIf { it >= 0f }?.let { (it * screenHeight).roundToInt() } ?: (screenHeight / 2)
        return x to y
    }

    companion object {

        private const val TRANSITION_DURATION_MS = 300L
        private const val MIN_ACCENT_CONTRAST = 4.5


    }

}
