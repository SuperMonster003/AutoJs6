package org.autojs.autojs.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.annotation.ScriptVariable
import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.SimpleActionAutomator
import org.autojs.autojs.core.accessibility.monitor.CloseableManager
import org.autojs.autojs.core.activity.ActivityInfoProvider
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.core.image.capture.ScreenCaptureRequester
import org.autojs.autojs.core.looper.Loopers
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.extension.ScriptableExtensions.deleteProp
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.rhino.AndroidClassLoader
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.ScriptToast
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.augment.app.App
import org.autojs.autojs.runtime.api.augment.autojs.Autojs
import org.autojs.autojs.runtime.api.augment.automator.Auto
import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.augment.automator.RootAutomator
import org.autojs.autojs.runtime.api.augment.barcode.Barcode
import org.autojs.autojs.runtime.api.augment.barcode.QrCode
import org.autojs.autojs.runtime.api.augment.base64.Base64
import org.autojs.autojs.runtime.api.augment.colors.Color
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs.runtime.api.augment.console.Console
import org.autojs.autojs.runtime.api.augment.continuation.Continuation
import org.autojs.autojs.runtime.api.augment.cryptyo.Crypto
import org.autojs.autojs.runtime.api.augment.device.Device
import org.autojs.autojs.runtime.api.augment.dialogs.Dialogs
import org.autojs.autojs.runtime.api.augment.engines.Engines
import org.autojs.autojs.runtime.api.augment.events.Events
import org.autojs.autojs.runtime.api.augment.events.Keys
import org.autojs.autojs.runtime.api.augment.files.Files
import org.autojs.autojs.runtime.api.augment.floaty.Floaty
import org.autojs.autojs.runtime.api.augment.global.Global
import org.autojs.autojs.runtime.api.augment.global.GlobalClasses
import org.autojs.autojs.runtime.api.augment.global.IsNullish
import org.autojs.autojs.runtime.api.augment.global.Species
import org.autojs.autojs.runtime.api.augment.http.Http
import org.autojs.autojs.runtime.api.augment.images.Images
import org.autojs.autojs.runtime.api.augment.jsox.Arrayx
import org.autojs.autojs.runtime.api.augment.jsox.Jsox
import org.autojs.autojs.runtime.api.augment.jsox.Mathx
import org.autojs.autojs.runtime.api.augment.jsox.Numberx
import org.autojs.autojs.runtime.api.augment.media.Media
import org.autojs.autojs.runtime.api.augment.mime.Mime
import org.autojs.autojs.runtime.api.augment.nanoid.NanoID
import org.autojs.autojs.runtime.api.augment.notice.Notice
import org.autojs.autojs.runtime.api.augment.ocr.Ocr
import org.autojs.autojs.runtime.api.augment.ocr.OcrMLKit
import org.autojs.autojs.runtime.api.augment.ocr.OcrPaddle
import org.autojs.autojs.runtime.api.augment.ocr.OcrRapid
import org.autojs.autojs.runtime.api.augment.opencc.OpenCC
import org.autojs.autojs.runtime.api.augment.plugins.Plugins
import org.autojs.autojs.runtime.api.augment.recorder.Recorder
import org.autojs.autojs.runtime.api.augment.s13n.S13n
import org.autojs.autojs.runtime.api.augment.selector.Selector
import org.autojs.autojs.runtime.api.augment.sensors.Sensors
import org.autojs.autojs.runtime.api.augment.shell.Shell
import org.autojs.autojs.runtime.api.augment.shizuku.Shizuku
import org.autojs.autojs.runtime.api.augment.sqlite.SQLite
import org.autojs.autojs.runtime.api.augment.storages.Storages
import org.autojs.autojs.runtime.api.augment.sysprops.SysProps
import org.autojs.autojs.runtime.api.augment.tasks.Tasks
import org.autojs.autojs.runtime.api.augment.threads.Threads
import org.autojs.autojs.runtime.api.augment.timers.Timers
import org.autojs.autojs.runtime.api.augment.toast.Toast
import org.autojs.autojs.runtime.api.augment.ui.UI
import org.autojs.autojs.runtime.api.augment.util.Util
import org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo
import org.autojs.autojs.runtime.api.augment.web.Web
import org.autojs.autojs.runtime.api.augment.web.WebSocket
import org.autojs.autojs.runtime.api.augment.web.WebSocketFields
import org.autojs.autojs.runtime.exception.ScriptEnvironmentException
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.tool.Supplier
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.isBackgroundThread
import org.autojs.autojs.util.RhinoUtils.isMainThread
import org.autojs.autojs.util.RhinoUtils.js_object_assign
import org.autojs.autojs.util.RhinoUtils.js_require
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.SdkVersionUtils
import org.autojs.autojs.util.StringUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.Wrapper
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import org.autojs.autojs.core.accessibility.UiSelector as CoreUiSelector
import org.autojs.autojs.core.crypto.Crypto as CoreCrypto
import org.autojs.autojs.core.image.Colors as CoreColors
import org.autojs.autojs.core.image.ImageWrapper as CoreImageWrapper
import org.autojs.autojs.core.permission.Permissions as CorePermissions
import org.autojs.autojs.core.web.WebSocket as CoreWebSocket
import org.autojs.autojs.rhino.continuation.Continuation as RhinoContinuation
import org.autojs.autojs.runtime.api.Barcode as ApiBarcode
import org.autojs.autojs.runtime.api.Device as ApiDevice
import org.autojs.autojs.runtime.api.Dialogs as ApiDialogs
import org.autojs.autojs.runtime.api.Engines as ApiEngines
import org.autojs.autojs.runtime.api.Events as ApiEvents
import org.autojs.autojs.runtime.api.Files as ApiFiles
import org.autojs.autojs.runtime.api.Floaty as ApiFloaty
import org.autojs.autojs.runtime.api.Http as ApiHttp
import org.autojs.autojs.runtime.api.Images as ApiImages
import org.autojs.autojs.runtime.api.Media as ApiMedia
import org.autojs.autojs.runtime.api.Mime as ApiMime
import org.autojs.autojs.runtime.api.Notice as ApiNotice
import org.autojs.autojs.runtime.api.Ocr as ApiOcr
import org.autojs.autojs.runtime.api.OcrMLKit as ApiOcrMLKit
import org.autojs.autojs.runtime.api.OcrPaddle as ApiOcrPaddle
import org.autojs.autojs.runtime.api.OcrRapid as ApiOcrRapid
import org.autojs.autojs.runtime.api.Plugins as ApiPlugins
import org.autojs.autojs.runtime.api.Recorder as ApiRecorder
import org.autojs.autojs.runtime.api.SQLite as ApiSQLite
import org.autojs.autojs.runtime.api.Scale as ApiScale
import org.autojs.autojs.runtime.api.Sensors as ApiSensors
import org.autojs.autojs.runtime.api.Threads as ApiThreads
import org.autojs.autojs.runtime.api.Timers as ApiTimers
import org.autojs.autojs.runtime.api.Toaster as ApiToaster
import org.autojs.autojs.runtime.api.UI as ApiUI
import org.autojs.autojs.runtime.api.augment.autojs.Version as AutojsVersion
import org.autojs.autojs.runtime.api.augment.global.Legacy as GlobalLegacy
import org.autojs.autojs.runtime.api.augment.notice.Channel as NoticeChannel
import org.autojs.autojs.runtime.api.augment.util.Inspect as UtilInspect
import org.autojs.autojs.runtime.api.augment.util.Java as UtilJava
import org.autojs.autojs.runtime.api.augment.util.MorseCode as UtilMorseCode
import org.autojs.autojs.runtime.api.augment.util.Version as UtilVersion
import org.autojs.autojs.runtime.api.augment.util.VersionCodes as UtilVersionCodes

/**
 * Created by Stardust on Jan 27, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Created by SuperMonster003 on May 24, 2024.
 */
@Suppress("unused", "PropertyName", "PrivatePropertyName")
class ScriptRuntime private constructor(builder: Builder) {

    private var mUiHandlerAppContext: Context
    private var mRootShell: AbstractShell? = null
    private val mProperties = ConcurrentHashMap<String, Any>()

    // @Hint by SuperMonster003 on May 6, 2024.
    //  ! Also exists as `global` object in JavaScript scripts.
    //  ! zh-CN: 同时作为 JavaScript 脚本的 `global` 全局对象存在.
    private lateinit var mTopLevelScope: TopLevelScope

    private lateinit var mThread: Thread

    @JvmField
    val closeableManager = CloseableManager()

    var topLevelScope: TopLevelScope
        get() = mTopLevelScope
        set(topLevelScope) {
            check(!::mTopLevelScope.isInitialized) { "Top level has already exists" }
            mTopLevelScope = topLevelScope
        }

    var clip: String
        get() {
            val get = { ClipboardUtils.getClipOrEmpty(mUiHandlerAppContext).toString() }
            if (isMainThread()) return get()
            val clip = VolatileDispose<String>()
            uiHandler.post { clip.setAndNotify(get()) }
            return clip.blockedGetOrThrow(ScriptInterruptedException::class.java)
        }
        set(text) {
            if (isMainThread()) {
                ClipboardUtils.setClip(mUiHandlerAppContext, text)
                return
            }
            val dispose = VolatileDispose<Any?>()
            uiHandler.post {
                ClipboardUtils.setClip(mUiHandlerAppContext, text)
                dispose.setAndNotify(text)
            }
            dispose.blockedGet(60000)
        }

    val isStopped: Boolean
        get() = mThread.isInterrupted

    @JvmField
    @ScriptVariable
    val app = builder.appUtils

    @JvmField
    @ScriptVariable
    val console: GlobalConsole = builder.getConsole()

    internal val consoleTimeTable = ConsoleTimeTable(this)

    @JvmField
    @ScriptVariable
    val automator: SimpleActionAutomator

    @ScriptVariable
    val info: ActivityInfoProvider

    @JvmField
    @ScriptVariable
    val ui: ApiUI

    @JvmField
    @ScriptVariable
    val dialogs = ApiDialogs(this)

    @ScriptVariable
    lateinit var events: ApiEvents

    @JvmField
    @ScriptVariable
    val bridges = ScriptBridges()

    @ScriptVariable
    lateinit var loopers: Loopers

    @ScriptVariable
    lateinit var timers: ApiTimers

    @JvmField
    @ScriptVariable
    val http: ApiHttp

    @JvmField
    @ScriptVariable
    val device: ApiDevice

    @JvmField
    @ScriptVariable
    val recorder = ApiRecorder(this)

    @JvmField
    @ScriptVariable
    val toaster = ApiToaster(this)

    @JvmField
    @ScriptVariable
    val ocr = ApiOcr()

    @JvmField
    @ScriptVariable
    val accessibilityBridge = builder.getAccessibilityBridge()

    @JvmField
    @ScriptVariable
    val engines = ApiEngines(this)

    @ScriptVariable
    lateinit var threads: ApiThreads

    @JvmField
    @ScriptVariable
    val floaty: ApiFloaty

    @ScriptVariable
    val uiHandler: UiHandler

    @JvmField
    @ScriptVariable
    val colors = CoreColors()

    @JvmField
    @ScriptVariable
    val files = ApiFiles(this)

    @JvmField
    @ScriptVariable
    val notice = ApiNotice(this)

    @ScriptVariable
    lateinit var sensors: ApiSensors

    @JvmField
    @ScriptVariable
    val media: ApiMedia

    @ScriptVariable
    lateinit var plugins: ApiPlugins

    @JvmField
    @ScriptVariable
    val images: ApiImages

    @JvmField
    @ScriptVariable
    val ocrMLKit: ApiOcrMLKit

    @JvmField
    @ScriptVariable
    val ocrPaddle: ApiOcrPaddle

    @JvmField
    @ScriptVariable
    val ocrRapid: ApiOcrRapid

    @JvmField
    @ScriptVariable
    val barcode: ApiBarcode

    @JvmField
    @ScriptVariable
    val shizuku: WrappedShizuku

    @JvmField
    @ScriptVariable
    val mime: ApiMime

    @JvmField
    @ScriptVariable
    val sqlite: ApiSQLite

    @JvmField
    @ScriptVariable
    val scale = ApiScale(this)

    @ScriptInterface
    var isExiting: Boolean = false

    @ScriptInterface
    val screenMetrics = ScreenMetrics()

    @get:ScriptInterface
    val rootShell: AbstractShell by lazy {
        builder.shellSupplier.get().also {
            it.SetScreenMetrics(screenMetrics)
            mRootShell = it
        }
    }

    private lateinit var augmentedApp: ScriptableObject

    private lateinit var augmentedAutojs: ScriptableObject

    internal lateinit var consoleProxyObject: ProxyObject

    internal lateinit var augmentedOcrMLKit: ScriptableObject

    internal lateinit var augmentedOcrPaddle: ScriptableObject

    internal lateinit var augmentedOcrRapid: ScriptableObject

    // @Commented by SuperMonster003 on May 24, 2022.
    //  ! Use internal Rhino JSON for better performance and compatibility.
    //  ! zh-CN: 使用 Rhino 内置的 JSON 以获得更好的性能及兼容性.
    // val js_JSON by lazy { js_require(this, "json2") as ScriptableObject }

    // @Commented by SuperMonster003 on May 26, 2024.
    //  ! Already implemented in Rhino 1.7.15 .
    //  ! zh-CN: 已在 Rhino 1.7.15 中实现.
    // val js_polyfill by lazy { js_require(this, "polyfill") as ScriptableObject }

    /**
     * @Hint by SuperMonster003 on Apr 17, 2022.
     * Try using internal Rhino Promise instead.
     * Legacy Promise may be ignored by AutoJs6 engine when Promise was placed at the end.
     * zh-CN:
     * 尝试使用 Rhino 内置的 Promise.
     * 传统 Promise 可能在位于脚本末尾时被 AutoJs6 引擎忽略执行.
     *
     * @example Code for reappearance
     * let test = () => 'hello';
     * Promise.resolve().then(test).then(res => log(res));
     */
    /**
     * @Hint by SuperMonster003 on May 24, 2022.
     * Use updated external Promise from Rhino instead of internal Rhino Promise.
     * Rhino Promise is not compatible with AutoJs6 continuation and causes a suspension.
     * zh-CN:
     * 使用升级后的扩展 Promise 替代 Rhino 的内置 Promise.
     * Rhino Promise 与 AutoJs6 的协程 (continuation) 机制不兼容, 且会造成假死.
     *
     * @example Code for reappearance
     * AutoJs6/示例代码/协程/协程HelloWorld.js
     */
    /**
     * Substitution of Promise.
     * zh-CN: Promise 的替代方案.
     */
    val js_Promise by lazy { js_require(this, "promise") as BaseFunction }

    val js_ResultAdapter by lazy { js_require(this, "result-adapter") as BaseFunction }

    private val js_object_observe_lite_min by lazy { js_require(this, "object-observe-lite.min") as BaseFunction }

    private val js_array_observe_min by lazy { js_require(this, "array-observe.min") as BaseFunction }

    private val js_mod_continuation by lazy { js_require(this, "continuation") as ScriptableObject }

    private val js_mod_internal by lazy { js_require(this, "internal") as ScriptableObject }

    private val js_Module by lazy { js_require(this, "jvm-npm") as ScriptableObject }

    init {
        info = accessibilityBridge.infoProvider
        automator = SimpleActionAutomator(accessibilityBridge, this).apply {
            setScreenMetrics(screenMetrics)
        }
        uiHandler = builder.getUiHandler().also {
            mUiHandlerAppContext = it.applicationContext
        }

        floaty = ApiFloaty(uiHandler, this)

        ui = ApiUI(mUiHandlerAppContext, this)
        images = ApiImages(mUiHandlerAppContext, this)
        device = ApiDevice(mUiHandlerAppContext)
        media = ApiMedia(mUiHandlerAppContext, this)
        sqlite = ApiSQLite(mUiHandlerAppContext, this)

        http = ApiHttp()
        ocrMLKit = ApiOcrMLKit()
        ocrPaddle = ApiOcrPaddle()
        ocrRapid = ApiOcrRapid()
        barcode = ApiBarcode()

        shizuku = WrappedShizuku
        mime = ApiMime
    }

    fun initPrologue() {
        mThread = Thread.currentThread()
        threads = ApiThreads(this)
        timers = ApiTimers(this)
        loopers = Loopers(this)
        events = ApiEvents(mUiHandlerAppContext, accessibilityBridge, this)
        sensors = ApiSensors(mUiHandlerAppContext, this)
        plugins = ApiPlugins.PluginRuntime(
            topLevelScope = topLevelScope,
            pluginSearchDir = PFiles.join(engines.myEngine().cwd(), "plugins"),
            engine = "rhino",
        ).let { ApiPlugins(mUiHandlerAppContext, it) }

        augment(topLevelScope)
        applyExecutionModules(topLevelScope)
        applyInternalModules(topLevelScope)
    }

    fun initEpilogue() {
        // @Hint by Stardust (https://github.com/hyb1996) on Feb 27, 2018
        //  ! 重定向 require 以支持相对路径和 npm 模块.
        //  ! en-US (translated by SuperMonster003 on Jul 27, 2024):
        //  ! Redirect the "require" method to support relative paths and npm modules.
        topLevelScope.defineProp("Module", js_Module, PERMANENT)
        topLevelScope.defineProp("require", js_Module.prop("require"), PERMANENT)

        // @OrderMatters by SuperMonster003 on Jul 24, 2024.
        //  ! "object observe" must be ahead of "array observe".
        //  ! zh-CN: "object observe" 需要先于 "array observe".
        callFunction(this, js_object_observe_lite_min, topLevelScope, topLevelScope, arrayOf(topLevelScope))
        callFunction(this, js_array_observe_min, topLevelScope, topLevelScope, arrayOf(topLevelScope))

        if (Pref.isCompatibilityWithClassesForVer4xEnabled) js_require(this, "redirect")
        if (Pref.isExtendingJsBuildInObjectsEnabled) Jsox.extendAllRhinoWithRuntime(this)
    }

    @ScriptInterface
    fun requestPermissions(permissions: Array<String?>) {
        CorePermissions.getPermissionsNeedToRequest(mUiHandlerAppContext, permissions).takeIf { it.isNotEmpty() }?.let {
            CorePermissions.requestPermissions(mUiHandlerAppContext, it)
        }
    }

    @ScriptInterface
    fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            throw ScriptInterruptedException()
        }
    }

    @ScriptInterface
    fun shell(cmd: String, withRoot: Int): AbstractShell.Result = ProcessShell.execCommand(cmd, withRoot != 0)

    @ScriptInterface
    fun selector() = CoreUiSelector(accessibilityBridge)

    fun load(vararg path: String) {
        doLoad({ f: File -> isJarFile(f) || isDexFile(f) }, *path)
    }

    fun load(dir: String, isRecursive: Boolean) {
        doLoad({ f: File -> isJarFile(f) || isDexFile(f) || isRecursive && f.isDirectory }, dir)
    }

    fun loadDex(vararg path: String) {
        doLoadDex({ f: File -> isDexFile(f) }, *path)
    }

    fun loadDex(dir: String, isRecursive: Boolean) {
        doLoadDex({ f: File -> isDexFile(f) || isRecursive && f.isDirectory }, dir)
    }

    fun loadJar(vararg paths: String) {
        doLoadJar({ f: File -> isJarFile(f) }, *paths)
    }

    fun loadJar(dir: String?, isRecursive: Boolean) {
        doLoadJar({ f: File -> isJarFile(f) || isRecursive && f.isDirectory }, dir!!)
    }

    @JvmOverloads
    fun exit(e: Throwable? = null) {
        e?.let { engines.myEngine().uncaughtException(it) }
        mThread.interrupt()
        engines.myEngine().forceStop()
        threads.exit()
        if (isBackgroundThread()) {
            throw ScriptInterruptedException()
        }
    }

    @Deprecated("ScriptRuntime#stop is deprecated", ReplaceWith("exit()"))
    fun stop() = exit()

    fun onExit() {
        Log.d(TAG, "on exit")
        this.isExiting = true

        ignoresException({
            if (console.configurator.isExitOnClose) {
                console.hideDelayed()
            }
        })

        ignoresException({ CoreWebSocket.onExit("Triggered by $TAG") })

        // @Hint by 抠脚本人 (https://github.com/little-alei) on Jul 10, 2023.
        //  ! 清空无障碍事件.
        //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
        //  ! To clear accessibility event callbacks.
        ignoresException({ AccessibilityService.clearAccessibilityEventCallback() })

        ignoresException({ RootUtils.resetRuntimeOverriddenRootModeState() })

        /* 回收全部记录的 ImageWrapper 实例. */
        ignoresException({ CoreImageWrapper.recycleAll() })

        /* 清除 interrupt 状态. */
        ignoresException({ ThreadCompat.interrupted() })

        /* 浮动窗口需要第一时间关闭. */
        /* 以免出现恶意脚本全屏浮动窗口遮蔽屏幕并且在 exit 中写死循环的问题. */
        ignoresException({ floaty.closeAll() })

        ignoresException({ events.emit("exit") }, "Exception on exit: %s")
        ignoresException({ ScriptToast.clear(this) }, 500)

        ignoresException({ threads.shutDownAll() })
        ignoresException({ events.recycle() })
        ignoresException({ media.recycle() })
        ignoresException({ loopers.recycle() })
        ignoresException({ this.recycleShell() })
        ignoresException({ images.releaseScreenCapturer() })
        ignoresException({ images.stopScreenCapturerForegroundService() })
        ignoresException({ ocrMLKit.release() })
        ignoresException({ ocrPaddle.release() })
        ignoresException({ sensors.unregisterAll() })
        ignoresException({ timers.recycle() })
        ignoresException({ ui.recycle() })
        ignoresException({ closeableManager.recycleAll() })
    }

    fun setScreenMetrics(width: Int, height: Int) {
        screenMetrics.setScreenMetrics(width, height)
    }

    fun getProperty(key: String) = mProperties[key]

    fun putProperty(key: String, value: Any) = mProperties.put(key, value)

    fun removeProperty(key: String) = mProperties.remove(key)

    fun createContinuation() = RhinoContinuation.create(this, topLevelScope)

    fun createContinuation(scope: Scriptable) = RhinoContinuation.create(this, scope)

    private fun doLoad(filter: FileFilter, vararg paths: String) {
        for (path in paths) {
            val file = File(files.path(path))
            if (file.isDirectory) {
                val filtered = file.listFiles(filter)
                if (filtered != null) {
                    for (f in filtered) {
                        if (isJarFile(f)) loadJar(f.path)
                        else if (isDexFile(f)) loadDex(f.path)
                    }
                }
            } else {
                if (isJarFile(file)) loadJar(file.path)
                else if (isDexFile(file)) loadDex(file.path)
            }
        }
    }

    private fun doLoadDex(filter: FileFilter, vararg paths: String) {
        try {
            val classLoader = sClassLoader
            for (path in paths) {
                val file = File(files.path(path))
                if (file.isDirectory) {
                    val filtered = file.listFiles(filter)
                    if (filtered != null) {
                        for (f in filtered) {
                            loadDex(f.path)
                        }
                    }
                } else {
                    classLoader.loadDex(file)
                }
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun doLoadJar(filter: FileFilter, vararg paths: String) {
        try {
            val classLoader = sClassLoader
            for (path in paths) {
                val file = File(files.path(path))
                if (file.isDirectory) {
                    val filtered = file.listFiles(filter)
                    if (filtered != null) {
                        for (f in filtered) {
                            loadJar(f.path)
                        }
                    }
                } else {
                    classLoader.loadJar(file)
                }
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun ignoresException(r: Runnable, @Suppress("SameParameterValue") delay: Int) {
        uiHandler.postDelayed(r, delay.toLong())
    }

    private fun ignoresException(r: Runnable, consoleMessage: String? = null) {
        try {
            r.run()
        } catch (e: Throwable) {
            if (consoleMessage != null) {
                console.error(String.format(consoleMessage, e))
            }
            e.printStackTrace()
        }
    }

    private fun recycleShell() {
        mRootShell?.exit()
    }

    private fun augment(target: ScriptableObject) {

        Global(this).assign(target, GlobalClasses)
        GlobalLegacy(this).assign(target)
        IsNullish.augment(target, false)
        Util.augment(target, true).apply {
            UtilJava.augment(this, false)
            UtilVersion.augment(this, false)
            UtilVersionCodes.augment(this, VersionCodesInfo.obj, false)
            UtilInspect.augment(this, false)
            UtilMorseCode.augment(this, false)
        }
        Species.augment(target, true)
        App(this).augment(target, app, true).also { augmentedApp = it }
        Autojs(this).augment(target, true).also { augmentedAutojs = it }.apply {
            AutojsVersion.augment(this, false)
        }
        Shell(this).augment(target, true)
        Timers(this).augment(target, timers, true)
        Auto(this).augment(target, true)
        Automator(this).augment(target, true)
        Selector(this).augment(target, true, READONLY)
        Events(this).augment(target, events, true)
        Keys.augment(target, true)
        Images(this).augment(target, true)
        Ocr(this).augment(target, true).apply {
            OcrMLKit(this@ScriptRuntime).augment(this, false).also { augmentedOcrMLKit = it }
            OcrPaddle(this@ScriptRuntime).augment(this, false).also { augmentedOcrPaddle = it }
            OcrRapid(this@ScriptRuntime).augment(this, false).also { augmentedOcrRapid = it }
        }
        Barcode(this).augment(target, true)
        QrCode(this).augment(target, true)
        Threads(this).augment(target, threads, true)
        UI(this).proxying(target, ui, true)
        Colors.augment(target, listOf(colors, Colors), true)
        Color.augment(target, false)
        Tasks(this).augment(target, true)
        Dialogs(this).augment(target, true)
        Continuation(this).augment(target, js_mod_continuation, true, READONLY)
        Http(this).augment(target, http, true)
        Web(this).augment(target, true)
        WebSocket(this).augment(target, WebSocketFields, false)
        S13n.augment(target, true)
        Console(this).proxying(target, console, true).also { consoleProxyObject = it }
        Plugins(this).augment(target, true)
        Arrayx(this).augment(target, false)
        Numberx(this).augment(target, false)
        Mathx(this).augment(target, false)
        Jsox(this).augment(target, true)
        Files(this).augment(target, files, true)
        Crypto.augment(target, CoreCrypto, true)
        RootAutomator(this).augment(target, false)
        Engines(this).augment(target, true)
        Floaty(this).augment(target, true)
        Storages.augment(target, true)
        Device(this).augment(target, device, true)
        Recorder(this).augment(target, recorder, true)
        Toast(this).augment(target, true)
        Media.augment(target, media, true)
        Sensors.augment(target, sensors, true)
        Base64.augment(target, true)
        Notice(this).augment(target, true).apply {
            NoticeChannel(this@ScriptRuntime).augment(this, false)
        }
        Shizuku(this).augment(target, shizuku, true)
        OpenCC.augment(target, true)
        Mime(this).augment(target, mime, true)
        SysProps(this).augment(target, true)
        SQLite(this).augment(target, true)
        NanoID.augment(target, true)

        augmentedApp.defineProp(Autojs::class.java.simpleName.lowercase(), augmentedAutojs)
    }

    private fun applyExecutionModules(global: Scriptable) {
        ScriptEngineService.GLOBAL_MODULES.forEach { moduleMode ->
            val moduleName = ScriptEngineService.getModuleNameFromMode(moduleMode) ?: return@forEach
            var module = global.prop(moduleName) ?: return@forEach
            if (module is Wrapper) module = module.unwrap()
            when (module) {
                is ScriptEngineService.ScriptModuleIdentifier -> {
                    global.defineProp(moduleName, js_require(this, module.moduleFileName), PERMANENT)
                }
                else -> global.deleteProp(moduleName)
            }
        }
    }

    private fun applyInternalModules(global: Scriptable) {
        js_object_assign(global, js_mod_internal)
    }

    class Builder {

        lateinit var shellSupplier: Supplier<AbstractShell>
            private set

        lateinit var appUtils: AppUtils
            private set

        private lateinit var mConsole: GlobalConsole
        private lateinit var mUiHandler: UiHandler
        private lateinit var mAccessibilityBridge: AccessibilityBridge

        private var mScreenCaptureRequester: ScreenCaptureRequester? = null

        fun setUiHandler(uiHandler: UiHandler) = also { mUiHandler = uiHandler }

        fun getUiHandler() = mUiHandler

        fun setConsole(console: GlobalConsole) = also { mConsole = console }

        fun getConsole() = mConsole

        fun setAccessibilityBridge(accessibilityBridge: AccessibilityBridge) = also { mAccessibilityBridge = accessibilityBridge }

        fun getAccessibilityBridge() = mAccessibilityBridge

        fun setShellSupplier(shellSupplier: Supplier<AbstractShell>) = also { this.shellSupplier = shellSupplier }

        fun setScreenCaptureRequester(requester: ScreenCaptureRequester?) = also { mScreenCaptureRequester = requester }

        fun setAppUtils(appUtils: AppUtils) = also { this.appUtils = appUtils }

        fun build() = ScriptRuntime(this)
    }

    companion object {

        private val TAG = ScriptRuntime::class.java.simpleName

        private var sApplicationContext: WeakReference<Context>? = null

        private val sClassLoader: AndroidClassLoader
            get() = ContextFactory.getGlobal().applicationClassLoader as AndroidClassLoader

        @JvmStatic
        var applicationContext: Context
            get() = sApplicationContext?.get() ?: throw ScriptEnvironmentException("No application context")
            set(context) {
                sApplicationContext = WeakReference(context)
            }

        @JvmStatic
        fun popException(message: String?) {
            try {
                throw Exception(message)
            } catch (e: Exception) {
                e.printStackTrace()
                ViewUtils.showToast(applicationContext, message, true)
                try {
                    AutoJs.instance.globalConsole.printAllStackTrace(e)
                } catch (exception: UninitializedPropertyAccessException) {
                    /* Ignored. */
                } catch (exception: Exception) {
                    exception.printStackTrace()
                }
            }
        }

        @JvmStatic
        fun getStackTrace(e: Throwable, printJavaStackTrace: Boolean): String? {
            return buildString {
                e.message?.let { appendLine(it) }
                if (e is RhinoException) {
                    appendLine(e.details())
                    e.scriptStack.forEach {
                        it.renderV8Style(this)
                        appendLine()
                    }
                    if (!printJavaStackTrace) return@buildString
                    appendLine()
                    appendLine("- ".repeat(33).trim())
                    appendLine()
                }
                try {
                    StringWriter().use { writer ->
                        PrintWriter(writer).use { e.printStackTrace(it) }
                        writer.toString().lineSequence().forEach {
                            when {
                                it.contains(Regex("^\\s+at\\s")) -> {
                                    appendLine("${"\u0020".repeat(4)}${it.trimStart()}\u0020")
                                }
                                else -> appendLine(it)
                            }
                        }
                    }
                    trim()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    return e.message
                }
            }
        }

        @Throws(ScriptException::class)
        @ScriptInterface
        @JvmStatic
        fun requiresApi(requiresApi: Int) {
            val currentApi = Build.VERSION.SDK_INT
            if (currentApi < requiresApi) {
                throw ScriptException(
                    StringUtils.str(
                        R.string.text_requires_android_os_version,
                        SdkVersionUtils.sdkIntToString(requiresApi),
                        requiresApi,
                        SdkVersionUtils.sdkIntToString(currentApi),
                        currentApi,
                    )
                )
            }
        }

        private fun isDexFile(f: File): Boolean {
            return PFiles.getExtension(f.name).equals("dex", ignoreCase = true)
        }

        private fun isJarFile(f: File): Boolean {
            return PFiles.getExtension(f.name).equals("jar", ignoreCase = true)
        }

    }

}
