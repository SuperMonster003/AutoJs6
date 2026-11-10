package org.autojs.autojs.core.virtualscreen

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.AttributionSource
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.ConcurrentHashMap

/**
 * The shell-side half of the virtual screen capability. It lives inside the Shizuku user service
 * process (uid 2000), because a [VirtualDisplay] belongs to the process that created it: creating
 * one from a one-shot `execCommand` would destroy the display as soon as that command returns.
 *
 * Responsibilities (the minimum mechanism set of Mika, minus its self-implemented wireless ADB
 * pairing, which Shizuku already provides):
 * - create a public, trusted virtual display with independent display group / focus;
 * - force fullscreen windowing mode and a local IME policy on that display;
 * - launch third-party activities onto a given displayId;
 * - inject taps, strokes and keys onto that displayId;
 * - keep an own-display-group screen awake and release everything on destroy.
 *
 * Every hidden framework call is best effort and falls back to an equivalent shell command when
 * the ROM does not expose it.
 *
 * zh-CN: 虚拟屏能力的 shell 侧实现, 运行在 Shizuku 用户服务进程 (uid 2000) 内. VirtualDisplay 归属于创建它的进程,
 * 用一次性 execCommand 建屏会随命令结束立即销毁, 所以必须由常驻的用户服务持有. 职责: 创建公开可信虚拟屏 (独立显示组/焦点)、
 * 强制全屏窗口模式与本地输入法策略、在指定 displayId 启动第三方 Activity、向该 displayId 注入点击/滑动/按键、
 * 为独立显示组保活, 并在服务销毁时全部释放. 所有隐藏接口调用均为尽力而为, 失败时退回等效 shell 命令.
 *
 * Created for the virtual-screen capability on Nov 8, 2026.
 */
class ShellVirtualDisplay(context: Context?) {

    private data class Entry(
        val display: VirtualDisplay,
        val flags: Int,
        val width: Int,
        val height: Int,
        val dpi: Int,
    )

    /** Reports com.android.shell: system services cross-check the package name with the caller uid. */
    private open class ShellContext(base: Context) : ContextWrapper(base) {
        override fun getPackageName(): String = SHELL_PACKAGE
        override fun getOpPackageName(): String = SHELL_PACKAGE

        companion object {
            const val SHELL_PACKAGE = "com.android.shell"
            const val SHELL_UID = 2000
        }
    }

    /** Loaded only on Android 12+, where AttributionSource exists. */
    @RequiresApi(31)
    private class AttributedShellContext(base: Context) : ShellContext(base) {
        override fun getAttributionSource(): AttributionSource =
            AttributionSource.Builder(ShellContext.SHELL_UID)
                .setPackageName(ShellContext.SHELL_PACKAGE)
                .build()
    }

    private val shellContext: Context? = context?.let { base ->
        if (Build.VERSION.SDK_INT >= 31) AttributedShellContext(base) else ShellContext(base)
    }

    private val entries = ConcurrentHashMap<Int, Entry>()

    /** App links are interpolated into a shell command: allow only URI-safe characters. */
    private val safeUri = Regex("[A-Za-z0-9._~:/?&=%+#@,\\-]+")

    private var cachedDisplayManager: DisplayManager? = null

    /** Only displays created here may be released, launched onto or injected into. */
    private fun owns(displayId: Int) = entries.containsKey(displayId)

    private val awakeHandler: Handler? by lazy {
        (Looper.getMainLooper() ?: Looper.myLooper())?.let { Handler(it) }
    }
    private val awakeIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val awakeTask = object : Runnable {
        override fun run() {
            awakeIds.forEach(::pokeUserActivity)
            awakeHandler?.postDelayed(this, AWAKE_PERIOD_MILLIS)
        }
    }

    /**
     * Creates a display on [surface] (an ImageReader surface owned by the app process) and returns
     * its displayId, or [INVALID_DISPLAY]. A [requestedFlags] of 0 means automatic flags; any other
     * value is passed through unchanged for advanced callers.
     *
     * A [SecurityException] means the shell lacks `ADD_TRUSTED_DISPLAY`; the trusted-only bits are
     * then dropped and the display is created again (sharing the main screen's focus).
     *
     * zh-CN: 在 [surface] (app 进程持有的 ImageReader surface) 上建屏并返回 displayId, 失败返回 -1.
     * [requestedFlags] 为 0 时自动计算标志位, 非 0 时原样透传. 抛出 SecurityException 表示 shell 缺少
     * ADD_TRUSTED_DISPLAY, 此时去掉可信标志重建 (与主屏共享焦点).
     */
    fun create(surface: Surface?, width: Int, height: Int, dpi: Int, requestedFlags: Int): Int {
        val context = shellContext ?: return INVALID_DISPLAY
        if (surface == null || width <= 0 || height <= 0 || dpi <= 0) return INVALID_DISPLAY
        unlockHiddenApis()

        val sdk = Build.VERSION.SDK_INT
        val flags = if (requestedFlags != 0) requestedFlags else VirtualDisplayFlags.forSdk(sdk, true)

        fun attempt(attemptFlags: Int): Int {
            val vd = displayManager(context).createVirtualDisplay(
                DISPLAY_NAME, width, height, dpi, surface, attemptFlags,
            ) ?: return INVALID_DISPLAY
            val id = vd.display?.displayId ?: INVALID_DISPLAY
            if (id < 0) {
                runCatching { vd.release() }
                return INVALID_DISPLAY
            }
            entries[id] = Entry(vd, attemptFlags, width, height, dpi)
            afterCreate(id, attemptFlags)
            Log.i(TAG, "created display $id: ${width}x$height@$dpi flags=0x${Integer.toHexString(attemptFlags)}")
            return id
        }

        return try {
            attempt(flags)
        } catch (e: SecurityException) {
            Log.w(TAG, "trusted display rejected, retrying without trusted flags", e)
            if (requestedFlags == 0 && sdk >= VirtualDisplayFlags.TRUSTED_SDK) {
                try {
                    attempt(VirtualDisplayFlags.forSdk(sdk, false))
                } catch (t: Throwable) {
                    Log.e(TAG, "createVirtualDisplay failed without trusted flags", t)
                    INVALID_DISPLAY
                }
            } else {
                INVALID_DISPLAY
            }
        } catch (t: Throwable) {
            Log.e(TAG, "createVirtualDisplay failed", t)
            INVALID_DISPLAY
        }
    }

    /** Releases the display; DESTROY_CONTENT_ON_REMOVAL ends the activities left on it. */
    fun release(displayId: Int): Boolean {
        val entry = entries.remove(displayId) ?: return false
        stopAwake(displayId)
        return runCatching { entry.display.release() }.onFailure {
            Log.w(TAG, "release display $displayId failed", it)
        }.isSuccess
    }

    fun releaseAll() {
        entries.keys.toList().forEach(::release)
        awakeHandler?.removeCallbacks(awakeTask)
    }

    /** JSON array of the displays owned by this service. */
    fun list(): String {
        val array = JSONArray()
        entries.forEach { (id, entry) -> array.put(describe(id, entry)) }
        return array.toString()
    }

    /** JSON object for one display, or an empty string for an unknown displayId. */
    fun info(displayId: Int): String = entries[displayId]?.let { describe(displayId, it).toString() } ?: ""

    /**
     * Launches [packageName] (or an explicit `package/class` [component]) on [displayId].
     * Returns one of the LAUNCH_* codes.
     */
    fun launch(displayId: Int, packageName: String, component: String?, clearTask: Boolean): Int {
        if (!owns(displayId)) return LAUNCH_INVALID_DISPLAY
        val context = shellContext ?: return LAUNCH_ERROR
        if (!ShellCommandRunner.isSafeToken(packageName)) return LAUNCH_NO_ENTRY
        if (!component.isNullOrEmpty() && !ShellCommandRunner.isSafeToken(component)) return LAUNCH_NO_ENTRY

        val intent = when {
            !component.isNullOrEmpty() -> Intent().setComponent(ComponentName.unflattenFromString(component))
            packageName.isNotEmpty() -> runCatching {
                context.packageManager.getLaunchIntentForPackage(packageName)
            }.getOrNull()
            else -> null
        } ?: return LAUNCH_NO_ENTRY

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (clearTask) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        if (startOnDisplay(intent, displayId)) return LAUNCH_OK

        val target = intent.component?.flattenToShortString() ?: resolveLauncherComponent(packageName)
            ?: return LAUNCH_NO_ENTRY
        val clear = if (clearTask) "--activity-clear-task" else ""
        val result = ShellCommandRunner.run("am start --display $displayId $clear -n $target")
        return if (result.ok) LAUNCH_OK else LAUNCH_ERROR
    }

    /**
     * Starts an ACTION_VIEW app link (e.g. `example://...`) on [displayId]. `app.openUrl()` resolves
     * such links on the default display unless the runtime routes them here.
     * Returns one of the LAUNCH_* codes.
     *
     * zh-CN: 在 [displayId] 上打开 ACTION_VIEW applink. `app.openUrl()` 默认会落在主屏, 必须由运行时作用域路由到这里.
     */
    fun launchUri(displayId: Int, uri: String): Int {
        if (!owns(displayId)) return LAUNCH_INVALID_DISPLAY
        if (!safeUri.matches(uri)) return LAUNCH_NO_ENTRY
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (startOnDisplay(intent, displayId)) return LAUNCH_OK
        // Single quotes keep `?` and `&` intact; safeUri already excludes quotes and shell metacharacters.
        val quoted = "'" + uri.replace("'", "'\\''") + "'"
        val result = ShellCommandRunner.run(
            "am start --display $displayId -a android.intent.action.VIEW -d $quoted -f 0x10000000",
        )
        return if (result.ok) LAUNCH_OK else LAUNCH_ERROR
    }

    /** Shared hidden-API launch path for both launcher intents and app links. */
    private fun startOnDisplay(intent: Intent, displayId: Int): Boolean = runCatching {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        val taskManager = Class.forName("android.app.ActivityTaskManager")
            .getDeclaredMethod("getService").apply { isAccessible = true }.invoke(null)
        val method = taskManager.javaClass.methods.first {
            it.name == "startActivityAsUser" && it.parameterTypes.size == 12
        }
        // caller, callingPackage, callingFeatureId, intent, resolvedType, resultTo, resultWho,
        // requestCode, flags, profilerInfo, options, userId
        (method.invoke(
            taskManager, null, ShellContext.SHELL_PACKAGE, null, intent, null, null, null,
            0, 0, null, options, USER_CURRENT,
        ) as Int) >= 0
    }.onFailure {
        Log.w(TAG, "startActivityAsUser unavailable, falling back to am start", it)
    }.getOrDefault(false)

    /** Stops an app that was launched on this display. */
    fun forceStop(displayId: Int, packageName: String): Boolean {
        if (!owns(displayId) || !ShellCommandRunner.isSafeToken(packageName)) return false
        return ShellCommandRunner.run("am force-stop $packageName").ok
    }

    fun tap(displayId: Int, x: Int, y: Int): Boolean {
        if (!owns(displayId)) return false
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
        val up = MotionEvent.obtain(now, now + TAP_MILLIS, MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0)
        return try {
            down.source = InputDevice.SOURCE_TOUCHSCREEN
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            if (!inject(down, displayId, INJECT_ASYNC)) {
                return ShellCommandRunner.run("input -d $displayId tap $x $y").ok
            }
            // A failed UP must not trigger a second tap through the command fallback.
            inject(up, displayId, INJECT_ASYNC)
            true
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    fun swipe(displayId: Int, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean {
        if (!owns(displayId) || durationMs <= 0) return false
        val steps = SWIPE_STEPS
        val start = SystemClock.uptimeMillis()
        fun event(index: Int, action: Int): MotionEvent {
            val ratio = index.toFloat() / steps
            return MotionEvent.obtain(
                start, start + (durationMs * ratio).toLong(), action,
                x1 + (x2 - x1) * ratio, y1 + (y2 - y1) * ratio, 0,
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        }

        val down = event(0, MotionEvent.ACTION_DOWN)
        val downOk = try {
            inject(down, displayId, INJECT_ASYNC)
        } finally {
            down.recycle()
        }
        if (!downOk) {
            return ShellCommandRunner.run("input -d $displayId swipe $x1 $y1 $x2 $y2 $durationMs").ok
        }
        val stepDelay = (durationMs / steps).coerceAtLeast(1).toLong()
        for (index in 1..steps) {
            val action = if (index == steps) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE
            val e = event(index, action)
            try {
                inject(e, displayId, INJECT_ASYNC)
            } finally {
                e.recycle()
            }
            SystemClock.sleep(stepDelay)
        }
        return true
    }

    fun key(displayId: Int, keyCode: Int): Boolean {
        if (!owns(displayId)) return false
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(now, now + TAP_MILLIS, KeyEvent.ACTION_UP, keyCode, 0)
        // Only a failed DOWN falls back to the command; a failed UP must not repeat the key.
        if (!inject(down, displayId, INJECT_WAIT_FOR_RESULT)) {
            return ShellCommandRunner.run("input -d $displayId keyevent $keyCode").ok
        }
        inject(up, displayId, INJECT_WAIT_FOR_RESULT)
        return true
    }

    /**
     * ASCII text through `input text`. Non-ASCII input should use the accessibility service's
     * ACTION_SET_TEXT on a node of this display instead (see docs/virtual-screen/README.md).
     */
    fun text(displayId: Int, value: String): Boolean {
        if (!owns(displayId) || value.isEmpty()) return false
        // `input text` goes through sh -c; reject shell metacharacters instead of escaping them.
        if (value.any { it in "\$`|;&<>\n\r" }) {
            Log.w(TAG, "text contains shell metacharacters, rejected")
            return false
        }
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace(" ", "%s")
        return ShellCommandRunner.run("input -d $displayId text \"$escaped\"").ok
    }

    // region internals

    @SuppressLint("PrivateApi")
    private fun displayManager(context: Context): DisplayManager {
        cachedDisplayManager?.let { return it }
        // getSystemService() returns a DisplayManager bound to the system context, whose package
        // name does not match the shell uid; the hidden constructor takes our ShellContext.
        val manager = DisplayManager::class.java
            .getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }
            .newInstance(context) as DisplayManager
        cachedDisplayManager = manager
        return manager
    }

    private fun unlockHiddenApis() {
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/hardware/display/DisplayManager;",
                "Landroid/app/ActivityTaskManager;",
                "Landroid/app/IActivityTaskManager;",
                "Landroid/app/ActivityOptions;",
                "Landroid/hardware/input/InputManager;",
                "Landroid/hardware/input/InputManagerGlobal;",
                "Landroid/view/InputEvent;",
                "Landroid/view/WindowManagerGlobal;",
                "Landroid/os/ServiceManager;",
                "Landroid/os/IPowerManager;",
            )
        }
    }

    private fun afterCreate(displayId: Int, flags: Int) {
        setFullscreenWindowingMode(displayId)
        setLocalImePolicy(displayId)
        if (flags and VirtualDisplayFlags.OWN_DISPLAY_GROUP != 0) startAwake(displayId)
    }

    /** Some ROMs default a new display to freeform, which shrinks apps and skews a11y coordinates. */
    private fun setFullscreenWindowingMode(displayId: Int) {
        val ok = runCatching {
            val wm = windowManagerService()
            wm.javaClass.getMethod("setWindowingMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(wm, displayId, WINDOWING_MODE_FULLSCREEN)
            true
        }.getOrDefault(false)
        if (!ok) {
            ShellCommandRunner.run("wm set-display-windowing-mode -d $displayId $WINDOWING_MODE_FULLSCREEN")
        }
    }

    /** IME_POLICY_LOCAL: the keyboard appears on the virtual display, not on the main screen. */
    private fun setLocalImePolicy(displayId: Int) {
        runCatching {
            val wm = windowManagerService()
            wm.javaClass.getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(wm, displayId, DISPLAY_IME_POLICY_LOCAL)
        }.onFailure { Log.w(TAG, "setDisplayImePolicy unavailable on display $displayId", it) }
    }

    private fun windowManagerService(): Any = Class.forName("android.view.WindowManagerGlobal")
        .getDeclaredMethod("getWindowManagerService")
        .apply { isAccessible = true }
        .invoke(null)!!

    /** An own-display-group screen has its own sleep timer, and injected input is not user activity. */
    private fun startAwake(displayId: Int) {
        val handler = awakeHandler ?: return
        val wasEmpty = awakeIds.isEmpty()
        awakeIds.add(displayId)
        if (wasEmpty) {
            handler.removeCallbacks(awakeTask)
            handler.post(awakeTask)
        }
    }

    private fun stopAwake(displayId: Int) {
        awakeIds.remove(displayId)
        if (awakeIds.isEmpty()) awakeHandler?.removeCallbacks(awakeTask)
    }

    private fun pokeUserActivity(displayId: Int) {
        runCatching {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java).invoke(null, "power") as IBinder
            val power = Class.forName("android.os.IPowerManager\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            val method = power.javaClass.methods.firstOrNull {
                it.name == "userActivity" && it.parameterTypes.size == 4
            }
            method?.invoke(power, displayId, SystemClock.uptimeMillis(), USER_ACTIVITY_EVENT_TOUCH, 0)
        }
    }

    private fun inject(event: InputEvent, displayId: Int, mode: Int): Boolean = runCatching {
        event.javaClass.getMethod("setDisplayId", Int::class.javaPrimitiveType).invoke(event, displayId)
        val manager = inputManager()
        manager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            .invoke(manager, event, mode) as? Boolean == true
    }.onFailure {
        Log.w(TAG, "injectInputEvent failed on display $displayId", it)
    }.getOrDefault(false)

    @SuppressLint("PrivateApi")
    private fun inputManager(): Any = runCatching {
        // Android 14 moved the entry point to InputManagerGlobal.
        Class.forName("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null)!!
    }.getOrElse {
        Class.forName("android.hardware.input.InputManager").getMethod("getInstance").invoke(null)!!
    }

    private fun resolveLauncherComponent(packageName: String): String? {
        val result = ShellCommandRunner.run(
            "cmd package resolve-activity --brief -c android.intent.category.LAUNCHER $packageName",
        )
        return result.output.lines().map { it.trim() }.lastOrNull { it.contains('/') }
            ?.takeIf { ShellCommandRunner.isSafeToken(it) }
    }

    private fun describe(displayId: Int, entry: Entry): JSONObject = JSONObject()
        .put("displayId", displayId)
        .put("width", entry.width)
        .put("height", entry.height)
        .put("dpi", entry.dpi)
        .put("flags", entry.flags)
        .put("flagsHex", "0x" + Integer.toHexString(entry.flags))
        .put("flagNames", JSONArray(VirtualDisplayFlags.describe(entry.flags)))
        .put("trusted", VirtualDisplayFlags.isTrusted(entry.flags))
        .put("ownFocus", entry.flags and VirtualDisplayFlags.OWN_FOCUS != 0)
        .put("ownDisplayGroup", entry.flags and VirtualDisplayFlags.OWN_DISPLAY_GROUP != 0)
        .put("sdkInt", Build.VERSION.SDK_INT)

    // endregion

    companion object {

        const val TAG = "ShellVirtualDisplay"

        const val INVALID_DISPLAY = -1

        const val LAUNCH_OK = 0
        const val LAUNCH_NO_ENTRY = 1
        const val LAUNCH_ERROR = 2
        const val LAUNCH_INVALID_DISPLAY = 3

        private const val DISPLAY_NAME = "autojs6-virtual-screen"
        private const val WINDOWING_MODE_FULLSCREEN = 1
        private const val DISPLAY_IME_POLICY_LOCAL = 0
        private const val USER_CURRENT = -2
        private const val USER_ACTIVITY_EVENT_TOUCH = 2
        private const val INJECT_ASYNC = 0
        private const val INJECT_WAIT_FOR_RESULT = 1
        private const val TAP_MILLIS = 60L
        private const val SWIPE_STEPS = 10
        private const val AWAKE_PERIOD_MILLIS = 4_000L

    }

}
