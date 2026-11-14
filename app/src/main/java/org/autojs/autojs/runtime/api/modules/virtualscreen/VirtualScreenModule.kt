package org.autojs.autojs.runtime.api.modules.virtualscreen

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.virtualscreen.VirtualScreenManager
import org.autojs.autojs.core.virtualscreen.VirtualScreenSession
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

/**
 * `virtualScreen`: runs automation on a hidden background display instead of the physical screen.
 *
 * The display is created as the shell uid by the Shizuku user service (see
 * docs/virtual-screen/README.md). The script keeps a session object whose methods act on that
 * display; the session is released automatically when the script exits.
 *
 * zh-CN: `virtualScreen` 模块: 把自动化操作放到一块隐藏的后台显示屏上执行, 不占用物理主屏.
 * 显示屏由 Shizuku 用户服务以 shell 身份创建 (见 docs/virtual-screen/README.md). 脚本持有会话对象,
 * 其方法都作用于该屏; 脚本退出时自动释放.
 *
 * Created on Nov 8, 2026.
 */
@ScriptModule(name = "virtualScreen")
class VirtualScreenModule(private val runtime: ScriptRuntime) {

    /**
     * Creates a virtual screen. Options: width, height, dpi, flags (0 = automatic). The default
     * geometry is the current main display's metrics.
     *
     * zh-CN: 创建虚拟屏. 选项: width / height / dpi / flags (0 表示自动); 默认尺寸取当前主屏参数.
     */
    @ScriptFunction
    fun create(options: Any? = null): VirtualScreenSession {
        val metrics = GlobalAppContext.get().resources.displayMetrics
        val source = options as? Scriptable
        val width = intOption(source, "width", metrics.widthPixels)
        val height = intOption(source, "height", metrics.heightPixels)
        val dpi = intOption(source, "dpi", metrics.densityDpi)
        val flags = intOption(source, "flags", 0)
        return VirtualScreenManager.create(runtime, width, height, dpi, flags)
    }

    /** Returns the live session of [displayId], or null. */
    @ScriptFunction
    fun session(displayId: Int): VirtualScreenSession? = VirtualScreenManager.get(displayId)

    /** Display IDs of all live sessions. */
    @ScriptFunction
    fun screens(): IntArray = VirtualScreenManager.ids()

    /** Releases one display; returns false when it is already gone. */
    @ScriptFunction
    fun release(displayId: Int): Boolean = VirtualScreenManager.release(displayId)

    /** Releases every display created by this process; returns the number released. */
    @ScriptFunction
    fun releaseAll(): Int = VirtualScreenManager.releaseAll()

    /**
     * Diagnostics: Shizuku availability, Android version, whether own-focus is supported and the
     * live display IDs. Call this before creating a screen to explain a failure to the user.
     *
     * zh-CN: 诊断信息: Shizuku 可用性、Android 版本、是否支持独立焦点及当前显示屏 ID 列表.
     */
    @ScriptFunction
    fun state(): NativeObject = RhinoUtils.newNativeObject(runtime.topLevelScope).also { o ->
        val ids = VirtualScreenManager.ids()
        // A real NativeArray, not a Java int[] that scripts would see as an opaque Java object.
        val array = RhinoUtils.newNativeArray(ids.size.toLong())
        ids.forEachIndexed { index, id -> array.put(index, array, id) }
        o.defineProperty("available", VirtualScreenManager.isAvailable(), ScriptableObject.EMPTY)
        o.defineProperty("sdkInt", VirtualScreenManager.sdkInt(), ScriptableObject.EMPTY)
        o.defineProperty("ownFocusSupported", VirtualScreenManager.isOwnFocusSupported(), ScriptableObject.EMPTY)
        o.defineProperty("screenCount", VirtualScreenManager.screenCount(), ScriptableObject.EMPTY)
        o.defineProperty("displayIds", array, ScriptableObject.EMPTY)
        o.defineProperty("minRecommendedSdkInt", 31, ScriptableObject.EMPTY)
        o.defineProperty("ownFocusSdkInt", 34, ScriptableObject.EMPTY)
    }

    private fun intOption(options: Scriptable?, name: String, fallback: Int): Int {
        if (options == null) return fallback
        val value = runCatching { ScriptableObject.getProperty(options, name) }.getOrNull()
        return (value as? Number)?.toInt() ?: fallback
    }

}
