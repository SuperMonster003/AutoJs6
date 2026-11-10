package org.autojs.autojs.core.virtualscreen

import android.view.Display
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScreenMetrics

/**
 * The screen scope of one script execution. Every screen-facing API of the runtime reads this object
 * instead of assuming the default display, so a task configured to run on a virtual screen needs no
 * changes to its JavaScript source (docs/virtual-screen/README.md).
 *
 * The display itself is created in the Shizuku user service; this object only holds its id and the
 * app-side [VirtualScreenSession] (which owns the ImageReader surface). A session is registered with
 * the runtime's closeable manager, so the screen disappears when the task ends.
 *
 * zh-CN: 单次脚本执行的屏幕作用域. 运行时所有面向屏幕的 API 都从这里取当前 display, 而不再假定默认显示屏,
 * 因此被配置为「在虚拟屏运行」的任务无需修改任何 JavaScript 源码. 显示屏由 Shizuku 用户服务创建, 本对象只保存
 * id 与 app 侧的 VirtualScreenSession (持有 ImageReader surface); 会话登记到 runtimes 的 closeable 管理器,
 * 任务结束即自动释放.
 */
class DisplayRoute(private val runtime: ScriptRuntime) {

    @Volatile
    var displayId: Int = Display.DEFAULT_DISPLAY
        private set

    @Volatile
    var session: VirtualScreenSession? = null
        private set

    /** True while this execution is bound to a live virtual display rather than the physical screen. */
    val isVirtual: Boolean
        get() = session?.let { !it.isReleased() } == true && displayId != Display.DEFAULT_DISPLAY

    val width: Int get() = session?.getWidth() ?: 0
    val height: Int get() = session?.getHeight() ?: 0
    val density: Int get() = session?.getDpi() ?: 0

    /**
     * Resolves the launch configuration into a concrete display. Called from
     * `ScriptRuntime.initPrologue()` (after the engine carries `ExecutionConfig` as a tag, before
     * modules are mounted), so globals such as `device` and `images` already see the right scope.
     */
    fun apply(config: ExecutionConfig?) {
        val enabled = isEnabled(config)
        if (!enabled) {
            displayId = Display.DEFAULT_DISPLAY
            session = null
            return
        }

        val reuseId = config?.virtualScreenDisplayId ?: 0
        val reused = if (reuseId > 0) VirtualScreenManager.get(reuseId) else null
        if (reuseId > 0 && reused == null) {
            throw IllegalStateException(
                "Virtual display $reuseId does not exist (it may have been released with its owning task). " +
                    "Set virtualScreenDisplayId=0 to create a new one.",
            )
        }

        val bound = reused ?: try {
            VirtualScreenManager.create(
                runtime = runtime,
                width = positiveOr(config?.virtualScreenWidth, ScreenMetrics.deviceScreenWidth),
                height = positiveOr(config?.virtualScreenHeight, ScreenMetrics.deviceScreenHeight),
                dpi = positiveOr(config?.virtualScreenDensity, ScreenMetrics.deviceScreenDensity),
                flags = 0,
            )
        } catch (t: Throwable) {
            throw IllegalStateException(
                "Cannot start this task on a virtual screen: ${t.message}. " +
                    "Shizuku must be running with AutoJs6 granted; Android 12+ is required.",
                t,
            )
        }

        displayId = bound.getDisplayId()
        session = bound
        runtime.putProperty(PROPERTY_DISPLAY_ID, displayId)
    }

    /** Called when a script explicitly releases its screen; avoids a dangling scope. */
    fun onSessionReleased(id: Int) {
        if (displayId == id) {
            session = null
            displayId = Display.DEFAULT_DISPLAY
        }
    }

    fun tap(x: Int, y: Int): Boolean = session?.tap(x, y) ?: false

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean =
        session?.swipe(x1, y1, x2, y2, durationMs) ?: false

    /** A long press is a zero-distance swipe on the shell side (down, hold, up). */
    fun longPress(x: Int, y: Int, durationMs: Int): Boolean = swipe(x, y, x, y, durationMs)

    fun key(keyCode: Int): Boolean = session?.key(keyCode) ?: false

    fun text(value: String): Boolean = session?.text(value) ?: false

    fun capture(): ImageWrapper? = runCatching { session?.capture() }.getOrNull()

    fun captureToFile(path: String): Boolean =
        runCatching { session?.captureToFile(path) == true }.getOrDefault(false)

    /** 0 means success, see VirtualScreenSession.launch. */
    fun launch(packageName: String): Boolean = session?.launch(packageName) == 0

    fun launchComponent(component: String): Boolean = session?.launchComponent(component) == 0

    /** ACTION_VIEW app links (example://, https://, ...) resolved on this display. */
    fun launchUri(uri: String): Boolean = session?.launchUri(uri) ?: false

    fun forceStop(packageName: String): Boolean = session?.forceStop(packageName) ?: false

    private fun isEnabled(config: ExecutionConfig?): Boolean = when (config?.virtualScreenMode) {
        ExecutionConfig.VIRTUAL_SCREEN_MODE_ON -> true
        ExecutionConfig.VIRTUAL_SCREEN_MODE_OFF -> false
        else -> config?.scriptConfig?.hasFeature(ExecutionConfig.FEATURE_VIRTUAL_SCREEN) == true ||
            runCatching { Pref.getBoolean(PREF_RUN_ON_VIRTUAL_SCREEN, false) }.getOrDefault(false)
    }

    private fun positiveOr(configured: Int?, fallback: Int): Int =
        if ((configured ?: 0) > 0) configured!! else fallback

    companion object {

        /** Global "run scripts on a virtual screen" switch, stored in the default preferences. */
        const val PREF_RUN_ON_VIRTUAL_SCREEN = "virtual_screen_run_mode"

        /** Intent extra: 1 = force on, 2 = force off, 0/absent = follow the setting. */
        const val EXTRA_VIRTUAL_SCREEN_MODE = "virtual_screen_mode"

        /** Intent extra: optional width / height / density / existing display id. */
        const val EXTRA_VIRTUAL_SCREEN_WIDTH = "virtual_screen_width"
        const val EXTRA_VIRTUAL_SCREEN_HEIGHT = "virtual_screen_height"
        const val EXTRA_VIRTUAL_SCREEN_DENSITY = "virtual_screen_density"
        const val EXTRA_VIRTUAL_SCREEN_DISPLAY_ID = "virtual_screen_display_id"

        /** Script-visible runtime property holding the current display id. */
        const val PROPERTY_DISPLAY_ID = "virtualScreen.displayId"

    }

}
