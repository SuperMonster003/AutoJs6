package org.autojs.autojs.core.virtualscreen

import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Build
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * App-side registry of virtual screens. Creating a screen asks the Shizuku user service to create
 * the display on an [ImageReader] surface owned here; the display itself is never created in the
 * app process (a plain app may only create a PRIVATE display of its own content).
 *
 * zh-CN: app 侧的虚拟屏登记处. 建屏时请 Shizuku 用户服务在本进程持有的 ImageReader surface 上创建显示屏,
 * 显示屏绝不在 app 进程创建 (普通 App 只能创建仅供自己渲染内容的 PRIVATE 屏).
 */
object VirtualScreenManager {

    private val sessions = ConcurrentHashMap<Int, VirtualScreenSession>()

    /**
     * Creates a virtual display for [runtime]. [flags] 0 means automatic (SDK-dependent) flags.
     * A null [runtime] creates a *detached* screen that outlives scripts (used by the viewer) and
     * must be released explicitly. Throws when Shizuku is not operational or the shell could not
     * create the display.
     */
    @JvmStatic
    fun create(runtime: ScriptRuntime?, width: Int, height: Int, dpi: Int, flags: Int): VirtualScreenSession {
        require(width in 1..MAX_SIDE && height in 1..MAX_SIDE && dpi in 1..MAX_DPI) {
            "Invalid virtual display size: ${width}x$height@$dpi"
        }
        val service = WrappedShizuku.getServiceOrNull()
            ?: throw IllegalStateException(
                "Virtual screens need Shizuku (or a root Shizuku) with AutoJs6 granted. " +
                    "Call shizuku.requestPermission() or enable Shizuku first.",
            )
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val displayId = try {
            service.createVirtualDisplay(reader.surface, width, height, dpi, flags)
        } catch (t: Throwable) {
            reader.close()
            throw IllegalStateException("Unable to create a virtual display: ${t.message}", t)
        }
        if (displayId < 0) {
            reader.close()
            throw IllegalStateException(
                "The shell could not create a virtual display. Check Shizuku, Android ${Build.VERSION.RELEASE} " +
                    "and whether the ROM allows ADD_TRUSTED_DISPLAY.",
            )
        }
        val info = try {
            JSONObject(service.virtualDisplayInfo(displayId))
        } catch (t: Throwable) {
            JSONObject()
        }
        val session = VirtualScreenSession(runtime, service, reader, displayId, width, height, dpi, info)
        sessions[displayId] = session
        // Released in Stage.CLOSEABLES when the owning script exits. Detached screens deliberately
        // skip this: they stay alive across activities until the viewer releases them.
        runtime?.closeableManager?.add(session)
        return session
    }

    /** Creates a screen that is not owned by any script; the viewer releases it explicitly. */
    @JvmStatic
    fun createDetached(width: Int, height: Int, dpi: Int, flags: Int = 0): VirtualScreenSession =
        create(null, width, height, dpi, flags)

    @JvmStatic
    fun get(displayId: Int): VirtualScreenSession? = sessions[displayId]?.takeIf { !it.isReleased() }

    /** Snapshot of every live session, sorted by display id (for the viewer's screen list). */
    @JvmStatic
    fun liveSessions(): List<VirtualScreenSession> = ids().toList().mapNotNull(::get)

    @JvmStatic
    fun ids(): IntArray = sessions.keys.filter { get(it) != null }.sorted().toIntArray()

    @JvmStatic
    fun release(displayId: Int): Boolean = get(displayId)?.release() ?: false

    @JvmStatic
    fun releaseAll(): Int = ids().count { release(it) }

    @JvmStatic
    fun unregister(displayId: Int, session: VirtualScreenSession) {
        sessions.remove(displayId, session)
    }

    /** Best effort: also safe when called on a plain JVM (unit tests). */
    @JvmStatic
    fun isAvailable(): Boolean = runCatching { WrappedShizuku.isOperational() }.getOrDefault(false)

    @JvmStatic
    fun isOwnFocusSupported(): Boolean = sdkInt() >= VirtualDisplayFlags.OWN_FOCUS_SDK

    /** 0 on a plain JVM (unit tests), where android.jar is a stub. */
    @JvmStatic
    fun sdkInt(): Int = runCatching { Build.VERSION.SDK_INT }.getOrDefault(0)

    @JvmStatic
    fun screenCount(): Int = ids().size

    private const val MAX_SIDE = 4096
    private const val MAX_DPI = 1200

}
