package org.autojs.autojs.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.annotation.ScriptVariable
import org.autojs.autojs.concurrent.VolatileDispose
import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.SimpleActionAutomator
import org.autojs.autojs.core.activity.ActivityInfoProvider
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.image.capture.ScreenCaptureRequester
import org.autojs.autojs.core.looper.LooperHelper
import org.autojs.autojs.core.looper.Loopers
import org.autojs.autojs.core.plugin.center.PluginAutoMountManager
import org.autojs.autojs.core.plugin.r8.AndroidR8CompilerHost
import org.autojs.autojs.core.plugin.r8.R8CompilerExplicitRuntimeRoute
import org.autojs.autojs.core.plugin.r8.R8RetraceExplicitRuntimeRoute
import org.autojs.autojs.core.pref.keys.ExtensibilityKeys
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.lang.ThreadCompat
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.pio.UncheckedIOException
import org.autojs.autojs.rhino.AndroidClassLoader
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.ProcessShell
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.ai.AiService
import org.autojs.autojs.runtime.api.epub.EpubService
import org.autojs.autojs.runtime.api.speech.SpeechService
import org.autojs.autojs.runtime.api.gallery.GalleryService
import org.autojs.autojs.runtime.api.terminal.TerminalService
import org.autojs.autojs.runtime.api.mail.MailService
import org.autojs.autojs.runtime.api.installer.InstallerService
import org.autojs.autojs.runtime.api.augment.ai.Ai
import org.autojs.autojs.runtime.api.augment.app.App
import org.autojs.autojs.runtime.api.augment.autojs.Autojs
import org.autojs.autojs.runtime.api.augment.automator.Auto
import org.autojs.autojs.runtime.api.augment.automator.Automator
import org.autojs.autojs.runtime.api.augment.automator.RootAutomator
import org.autojs.autojs.runtime.api.augment.barcode.Barcode
import org.autojs.autojs.runtime.api.augment.barcode.QrCode
import org.autojs.autojs.runtime.api.augment.base64.Base64
import org.autojs.autojs.runtime.api.augment.canvas.Canvas
import org.autojs.autojs.runtime.api.augment.colors.Color
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs.runtime.api.augment.console.Console
import org.autojs.autojs.runtime.api.augment.compose.Compose
import org.autojs.autojs.runtime.api.augment.continuation.Continuation
import org.autojs.autojs.runtime.api.augment.converter.Converter
import org.autojs.autojs.runtime.api.augment.cryptyo.Crypto
import org.autojs.autojs.runtime.api.augment.device.Device
import org.autojs.autojs.runtime.api.augment.dialogs.Dialogs
import org.autojs.autojs.runtime.api.augment.engines.Engines
import org.autojs.autojs.runtime.api.augment.events.Events
import org.autojs.autojs.runtime.api.augment.events.Keys
import org.autojs.autojs.runtime.api.augment.files.BatchRename
import org.autojs.autojs.runtime.api.augment.files.Files
import org.autojs.autojs.runtime.api.augment.floaty.Floaty
import org.autojs.autojs.runtime.api.augment.flow.Flow
import org.autojs.autojs.runtime.api.augment.formatter.Formatter
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
import org.autojs.autojs.runtime.api.augment.epub.Epub
import org.autojs.autojs.runtime.api.augment.speech.Speech
import org.autojs.autojs.runtime.api.augment.speech.SpeechModels
import org.autojs.autojs.runtime.api.augment.gallery.Gallery
import org.autojs.autojs.runtime.api.augment.terminal.Terminal
import org.autojs.autojs.runtime.api.augment.mail.Mail
import org.autojs.autojs.runtime.api.augment.installer.Installer
import org.autojs.autojs.runtime.api.augment.mediainfo.Mediainfo
import org.autojs.autojs.runtime.api.augment.memory.Memory
import org.autojs.autojs.runtime.api.augment.mime.Mime
import org.autojs.autojs.runtime.api.augment.nanoid.NanoID
import org.autojs.autojs.runtime.api.augment.notice.Notice
import org.autojs.autojs.runtime.api.augment.ocr.Ocr
import org.autojs.autojs.runtime.api.augment.ocr.OcrMLKit
import org.autojs.autojs.runtime.api.augment.ocr.OcrPaddle
import org.autojs.autojs.runtime.api.augment.ocr.OcrRapid
import org.autojs.autojs.runtime.api.augment.opencc.OpenCC
import org.autojs.autojs.runtime.api.augment.pinyin.Pinyin
import org.autojs.autojs.runtime.api.augment.pinyin4j.Pinyin4j
import org.autojs.autojs.runtime.api.augment.plugins.Plugins
import org.autojs.autojs.runtime.api.augment.plugins.PluginsTools
import org.autojs.autojs.runtime.api.augment.power.PowerManager
import org.autojs.autojs.runtime.api.augment.recorder.Recorder
import org.autojs.autojs.runtime.api.augment.s13n.S13n
import org.autojs.autojs.runtime.api.augment.selector.Selector
import org.autojs.autojs.runtime.api.augment.sensors.Sensors
import org.autojs.autojs.runtime.api.augment.settings.Settings
import org.autojs.autojs.runtime.api.augment.shell.Shell
import org.autojs.autojs.runtime.api.augment.shizuku.Shizuku
import org.autojs.autojs.runtime.api.augment.sqlite.SQLite
import org.autojs.autojs.runtime.api.augment.storages.Storages
import org.autojs.autojs.runtime.api.augment.sysprops.SysProps
import org.autojs.autojs.runtime.api.augment.tasks.Tasks
import org.autojs.autojs.runtime.api.augment.tasks.WorkManager
import org.autojs.autojs.runtime.api.augment.threads.Threads
import org.autojs.autojs.runtime.api.augment.timers.Timers
import org.autojs.autojs.runtime.api.augment.toast.Toast
import org.autojs.autojs.runtime.api.augment.tts.Tts
import org.autojs.autojs.runtime.api.augment.ui.UI
import org.autojs.autojs.runtime.api.augment.util.Util
import org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo
import org.autojs.autojs.runtime.api.augment.web.Web
import org.autojs.autojs.runtime.api.augment.web.WebSocket
import org.autojs.autojs.runtime.api.augment.web.WebSocketFields
import org.autojs.autojs.runtime.api.augment.yolo.Yolo
import org.autojs.autojs.runtime.api.augment.zip.Zip
import org.autojs.autojs.runtime.api.tts.TtsService
import org.autojs.autojs.runtime.exception.ScriptEnvironmentException
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.autojs.autojs.runtime.resource.RuntimeResourceScope.Stage
import org.autojs.autojs.tool.Supplier
import org.autojs.autojs.tool.UiHandler
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.isBackgroundThread
import org.autojs.autojs.util.RhinoUtils.isMainThread
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
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import org.autojs.autojs.core.accessibility.UiSelector as CoreUiSelector
import org.autojs.autojs.core.crypto.Crypto as CoreCrypto
import org.autojs.autojs.core.image.Colors as CoreColors
import org.autojs.autojs.core.permission.Permissions as CorePermissions
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
import org.autojs.autojs.runtime.api.Util as ApiUtil
import org.autojs.autojs.runtime.api.augment.autojs.Version as AutojsVersion
import org.autojs.autojs.runtime.api.augment.colors.ColorsTheme
import org.autojs.autojs.runtime.api.augment.converter.Bytes as BytesCvt
import org.autojs.autojs.runtime.api.augment.formatter.Bytes as BytesFmt
import org.autojs.autojs.runtime.api.augment.global.Legacy as GlobalLegacy
import org.autojs.autojs.runtime.api.augment.notice.Channel as NoticeChannel
import org.autojs.autojs.runtime.api.augment.util.Inspect as UtilInspect
import org.autojs.autojs.runtime.api.augment.util.Java as UtilJava
import org.autojs.autojs.runtime.api.augment.util.MorseCode as UtilMorseCode
import org.autojs.autojs.runtime.api.augment.util.Version as UtilVersion
import org.autojs.autojs.runtime.api.augment.util.VersionCodes as UtilVersionCodes
import org.mozilla.javascript.Context as RhinoContext
import org.autojs.autojs.runtime.api.modules.ScriptModuleLayer
import org.autojs.autojs.runtime.api.modules.ScriptModuleMounter
import org.autojs.autojs.runtime.api.modules.bootstrap.ContinuationPrototype
import org.autojs.autojs.runtime.api.modules.bootstrap.InternalGlobals
import org.autojs.autojs.runtime.api.modules.bootstrap.ResultAdapterFunction
import org.autojs.autojs.runtime.api.modules.bootstrap.ScriptPolyfills
import org.autojs.autojs.runtime.api.modules.bootstrap.require.AssetModuleSource
import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptModuleSources
import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptRequire
import org.autojs.autojs.script.JavaScriptSource

/**
 * Created by Stardust on Jan 27, 2017.
 * Modified by SuperMonster003 as of Dec 29, 2025.
 * Transformed by SuperMonster003 on May 24, 2024.
 */
@Suppress("unused", "PropertyName")
class ScriptRuntime private constructor(builder: Builder) {

    private val mJob = SupervisorJob()
    val coroutineScope = CoroutineScope(Dispatchers.Default + mJob)
    val coroutineContext = coroutineScope.coroutineContext

    val ownerId = "runtime@${System.identityHashCode(this)}"

    private var mUiHandlerAppContext: Context
    private val mProperties = ConcurrentHashMap<String, Any>()

    // @Hint by SuperMonster003 on May 6, 2024.
    //  ! Also exists as `global` object in JavaScript scripts.
    //  ! zh-CN: 同时作为 JavaScript 脚本的 `global` 全局对象存在.
    private lateinit var mTopLevelScope: TopLevelScope

    private lateinit var mThread: Thread

    /**
     * Everything this runtime releases when it exits, registered by the modules and by [ownExitSteps] (docs/memory/ROADMAP.md T6 / P4.1).
     * zh-CN: 本运行时结束时释放的全部资源, 由各模块与 [ownExitSteps] 登记 (docs/memory/ROADMAP.md T6 / P4.1).
     */
    @JvmField
    val resourceScope = RuntimeResourceScope(ownerId)

    /**
     * The former closeable manager: the [Stage.CLOSEABLES] stage of [resourceScope] (`add` / `remove`).
     * zh-CN: 原 closeable 管理器: 即 [resourceScope] 的 [Stage.CLOSEABLES] 阶段 (`add` / `remove`).
     */
    @JvmField
    val closeableManager = resourceScope

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
    val console: ConsoleImpl = builder.getConsole()

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
    val util: ApiUtil

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

    private val ai: AiService
    private val tts: TtsService
    private val mail: MailService
    private val installer: InstallerService
    private val epub: EpubService
    private val gallery: GalleryService
    private val terminal: TerminalService
    private val speech: SpeechService
    private var compose: Compose? = null

    internal fun composeForTsx(): Compose = checkNotNull(compose)

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
            resourceScope.own(Stage.SHELL, "rootShell") { it.exit() }
        }
    }

    var isJavaPrimitiveWrap
        get() = bridges.isJavaPrimitiveWrap()
        set(b) = bridges.setJavaPrimitiveWrap(b)

    /** The `console` module object of the old augment call (`augmentLegacy`), set when the new layer does not serve it. */
    private lateinit var legacyConsoleProxyObject: ProxyObject

    /**
     * The `console` module object the setters of `console` return and `console.build` calls: the new layer's when it
     * serves the module (roadmap P3.2, built on demand), else the one of the old augment call.
     * zh-CN: console 各 setter 返回的, console.build 调用的 console 模块对象: 新层提供该模块时为新层的 (P3.2, 按需构建), 否则为原 augment 调用的.
     */
    internal val consoleProxyObject: ProxyObject
        get() = scriptModules?.moduleObject("console") as? ProxyObject ?: legacyConsoleProxyObject

    /**
     * The mounter of the new script module layer for this runtime, or null when its switch was off when the runtime
     * started (roadmap T8); `initEpilogue` extends the built-in objects with it (P2.1).
     * zh-CN: 本运行时的新脚本模块层挂载器, 运行时启动时开关关闭则为 null (T8); initEpilogue 用它扩展内置对象 (P2.1).
     */
    private var scriptModules: ScriptModuleMounter? = null

    internal lateinit var augmentedApp: ScriptableObject

    /** The `autojs` module object of the old augment call (`augmentLegacy`), set when the new layer does not serve it. */
    private lateinit var legacyAugmentedAutojs: ScriptableObject

    /**
     * The `autojs` module object the host reads (`UI.kt` reads `R`): the new layer's when it serves the module (roadmap
     * P3.4, built on demand), else the one of the old augment call.
     * zh-CN: 宿主读取的 autojs 模块对象 (UI.kt 读取 R): 新层提供该模块时为新层的 (P3.4, 按需构建), 否则为原 augment 调用的.
     */
    internal val augmentedAutojs: ScriptableObject
        get() = scriptModules?.moduleObject("autojs") ?: legacyAugmentedAutojs

    /** The engine module objects of `ocr` from the old augment call (`augmentLegacy`), set when the new layer does not serve it. */
    private lateinit var legacyAugmentedOcrMLKit: ScriptableObject

    private lateinit var legacyAugmentedOcrPaddle: ScriptableObject

    private lateinit var legacyAugmentedOcrRapid: ScriptableObject

    /**
     * The engine module objects `ocr.mlkit` / `ocr.paddle` / `ocr.rapid` that `ocr.tap`, the `ocr.mode` setter and
     * `options.mode` accept as modes: the new layer's when it serves `ocr` (roadmap P3.1, built on demand), else those of
     * the old augment call.
     * zh-CN: ocr.tap, ocr.mode 的 setter 与 options.mode 作为模式接受的引擎模块对象 ocr.mlkit / ocr.paddle / ocr.rapid: 新层提供 ocr 时为新层的 (P3.1,
     * 按需构建), 否则为原 augment 调用的.
     */
    internal val augmentedOcrMLKit: ScriptableObject
        get() = scriptModules?.moduleObject("ocr.mlkit") ?: legacyAugmentedOcrMLKit

    internal val augmentedOcrPaddle: ScriptableObject
        get() = scriptModules?.moduleObject("ocr.paddle") ?: legacyAugmentedOcrPaddle

    internal val augmentedOcrRapid: ScriptableObject
        get() = scriptModules?.moduleObject("ocr.rapid") ?: legacyAugmentedOcrRapid

    private val rhinoRequireFunction by lazy { topLevelScope.prop("require") as BaseFunction }

    // @Commented by SuperMonster003 on May 24, 2022.
    //  ! Use internal Rhino JSON for better performance and compatibility.
    //  ! zh-CN: 使用 Rhino 内置的 JSON 以获得更好的性能及兼容性.
    // internal val js_JSON by lazy { rhinoRequire("json2") as ScriptableObject }

    // @Commented by SuperMonster003 on May 26, 2024.
    //  ! Already implemented in Rhino 1.7.15 .
    //  ! zh-CN: 已在 Rhino 1.7.15 中实现.
    // internal val js_polyfill by lazy { rhinoRequire("polyfill") as ScriptableObject }

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
    internal val js_Promise by lazy { rhinoRequire("promise") as BaseFunction }

    /**
     * The global `ResultAdapter`, in Kotlin since roadmap P2.5 (formerly `assets/modules/result-adapter.js`); the name stays
     * for its callers in the augment package (unchanged until P5.2, R5). As the module was, it is created on first use (in
     * `RhinoJavaScriptEngine.init`), and the thread it is created on chooses the variant.
     * zh-CN: 全局 `ResultAdapter`, ROADMAP P2.5 起为 Kotlin 实现 (原 `assets/modules/result-adapter.js`); 名称因 augment 包中的调用方保留 (P5.2 前不改, R5).
     * 与原模块相同, 首次使用时 (在 `RhinoJavaScriptEngine.init` 中) 创建, 由创建时所在的线程选定变体.
     */
    internal val js_ResultAdapter: BaseFunction by lazy { ResultAdapterFunction(topLevelScope, topLevelScope.globalThis, isMainThread()) }
    internal val js_UiExt by lazy { rhinoRequire("ui-ext") as BaseFunction }
    internal val js_Module by lazy { if (scriptModules != null) scriptRequire.module else rhinoRequire(JVM_NPM) as ScriptableObject }

    /**
     * The Kotlin `require` and `Module` of roadmap P3.7, for the runtimes with the new script module layer (the others load
     * `jvm-npm.js`): Node core modules and the ids that the local lookup misses come from the engine's Rhino `require`,
     * whose modules are those of the global getters, and `jvm-npm` is this `Module`.
     * zh-CN: ROADMAP P3.7 的 Kotlin `require` 与 `Module`, 用于启用新脚本模块层的运行时 (其他运行时加载 `jvm-npm.js`): Node 核心模块与本地解析未命中的 id
     * 来自引擎的 Rhino require, 模块实例与全局 getter 相同; `jvm-npm` 即本 `Module`.
     */
    private val scriptRequire by lazy {
        ScriptRequire(
            scope = topLevelScope,
            host = object : ScriptRequire.Host {
                override fun cwd() = files.cwd()
                override fun exists(path: String) = files.exists(path)
                override fun readBytes(path: String) = files.readBytes(path)
            },
            sources = ScriptModuleSources(listOf(AssetModuleSource(::canRequireNamedModule, ::requireNamedModule))),
            nativeRequire = ::rhinoRequire,
        )
    }

    internal val js_mod_axios by lazy { rhinoRequire("axios") as BaseFunction }
    internal val js_mod_cheerio by lazy { rhinoRequire("cheerio") as ScriptableObject }
    internal val js_mod_dayjs by lazy { rhinoRequire("dayjs") as BaseFunction }
    internal val js_mod_i18n by lazy { rhinoRequire("i18n") as BaseFunction }
    internal val js_mod_pangu by lazy { rhinoRequire("pangu") as ScriptableObject }

    /**
     * The prototype of the `continuation` module object with `await` / `delay`, in Kotlin since roadmap P2.5 (formerly
     * `assets/modules/continuation.js`).
     * zh-CN: `continuation` 模块对象的原型 (含 await / delay), ROADMAP P2.5 起为 Kotlin 实现 (原 `assets/modules/continuation.js`).
     */
    internal val js_mod_continuation: ScriptableObject by lazy { ContinuationPrototype.create(topLevelScope) }

    init {
        accessibilityBridge.scriptOwnerId = ownerId
        info = accessibilityBridge.infoProvider
        automator = SimpleActionAutomator(accessibilityBridge, this).apply {
            setScreenMetrics(screenMetrics)
        }
        uiHandler = builder.getUiHandler().also {
            mUiHandlerAppContext = it.applicationContext
        }

        floaty = ApiFloaty(uiHandler, this)

        ui = ApiUI(mUiHandlerAppContext, this)
        util = ApiUtil
        images = ApiImages(mUiHandlerAppContext, this)
        device = ApiDevice(mUiHandlerAppContext)
        media = ApiMedia(mUiHandlerAppContext, this)
        sqlite = ApiSQLite(mUiHandlerAppContext, this)

        http = ApiHttp()
        ai = AiService(mUiHandlerAppContext, this)
        tts = TtsService(mUiHandlerAppContext, this)
        mail = MailService(mUiHandlerAppContext, this)
        installer = InstallerService(mUiHandlerAppContext, this)
        epub = EpubService(mUiHandlerAppContext, this)
        gallery = GalleryService(mUiHandlerAppContext, this)
        terminal = TerminalService(mUiHandlerAppContext, this)
        speech = SpeechService(mUiHandlerAppContext, this)
        ocrRapid = ApiOcrRapid()
        barcode = ApiBarcode()

        shizuku = WrappedShizuku
        mime = ApiMime

        ownExitSteps()
    }

    fun initPrologue() {
        LooperHelper.prepare()
        mThread = Thread.currentThread()
        threads = ApiThreads(this)
        timers = ApiTimers(this)
        loopers = Loopers(this)
        events = ApiEvents(mUiHandlerAppContext, accessibilityBridge, this)
        sensors = ApiSensors(mUiHandlerAppContext, this)
        plugins = ApiPlugins.PluginRuntime(
            topLevelScope = topLevelScope,
            pluginSearchDir = PFiles.join(engines.myEngine().cwd(), "plugins"),
            engine = engines.myEngine().source?.engineName ?: JavaScriptSource.ENGINE,
        ).let { ApiPlugins(mUiHandlerAppContext, it, resourceScope) }

        augment(topLevelScope.globalThis)
        applyInternalModules(topLevelScope.globalThis)
    }

    fun initEpilogue() {
        // Roadmap P2.5: the polyfills Object.observe (with "array observe" after "object observe") and structuredClone are
        // installed on first access, from lazy data properties where their modules put them, instead of for every script.
        // zh-CN: ROADMAP P2.5: polyfill Object.observe ("array observe" 在 "object observe" 之后) 与 structuredClone 改为首次访问时安装,
        // 惰性数据属性定义在原模块赋值的位置, 不再为每个脚本执行.
        ScriptPolyfills(topLevelScope, topLevelScope.globalThis, ::rhinoRequire) { function, thisObj, args ->
            callFunction(this, function, topLevelScope, thisObj, args)
        }.install()

        if (ExtensibilityKeys.EXTENDING_JS_BUILD_IN_OBJECTS.get()) extendJsBuiltIns()

        // @Hint by Stardust (https://github.com/hyb1996) on Feb 27, 2018
        //  ! 重定向 require 以支持相对路径和 npm 模块.
        //  ! en-US (translated by SuperMonster003 on Jul 27, 2024):
        //  ! Redirect the "require" method to support relative paths and npm modules.
        if (scriptModules != null) {
            // Roadmap P3.7: the Kotlin require replaces jvm-npm.js, created when a script first reads require or Module. The
            // native require is taken before it is replaced, so that the global getters (axios, ...) keep loading through it.
            // zh-CN: ROADMAP P3.7: Kotlin require 取代 jvm-npm.js, 脚本首次读取 require 或 Module 时创建. 原生 require 在被替换之前取得,
            // 使全局 getter (axios 等) 仍经它加载.
            rhinoRequireFunction
            ScriptRequire.mount(topLevelScope.globalThis as ScriptableObject) { scriptRequire }
        } else {
            topLevelScope.defineProp("Module", js_Module, PERMANENT)
            topLevelScope.defineProp("require", js_Module.prop("require"), PERMANENT)
        }

        PluginAutoMountManager.apply(this)
    }

    /**
     * Extends the JavaScript built-in objects for the "extend JavaScript built-in objects" option and the execution mode
     * "jsox": with the modules of the new script module layer when it serves jsox (roadmap P2.1), otherwise with the old
     * Jsox.
     * zh-CN: 为 "JavaScript 内置对象扩展" 设置与执行模式 "jsox" 扩展内置对象: 新脚本模块层提供 jsox 时用新层的模块 (ROADMAP P2.1), 否则用旧 Jsox.
     */
    fun extendJsBuiltIns() = ScriptModuleLayer.extendBuiltInsOr(scriptModules) { Jsox.extendAllRhinoWithRuntime(this) }

    /**
     * Whether the new script module layer serves the top-level module [name] for this runtime and has not built it yet
     * (roadmap P3.4): nothing of the script has reached the module, so host code that only acts for scripts using it (the
     * engine events of `engines`) can skip this runtime instead of building the module.
     * zh-CN: 新脚本模块层为本运行时提供顶层模块 [name] 且尚未构建 (P3.4): 脚本还没有用到该模块, 只为使用它的脚本工作的宿主代码 (engines 的引擎事件) 可跳过本运行时,
     * 不必为此构建模块.
     */
    fun isScriptModulePending(name: String): Boolean {
        val modules = scriptModules ?: return false
        return name in ScriptModuleLayer.MIGRATED && !modules.isBuilt(name)
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
    // No node allocator: releasing script-held nodes at exit is unsafe below API 33, see
    // AccessibilityNodeInfoAllocator.
    // zh-CN: 不带节点分配器: API 33 以下在脚本退出时释放脚本持有的节点并不安全, 见 AccessibilityNodeInfoAllocator.
    fun selector() = CoreUiSelector(accessibilityBridge)

    fun load(vararg path: String) {
        doLoad({ f: File -> isJarFile(f) || isDexFile(f) || isAarFile(f) }, *path)
    }

    fun load(dir: String, isRecursive: Boolean) {
        doLoad({ f: File -> isJarFile(f) || isDexFile(f) || isAarFile(f) || isRecursive && f.isDirectory }, dir)
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

    fun loadJarWithClasspath(program: String, vararg orderedClasspath: String) {
        try {
            ownLoads {
                ScriptRuntimeJarClasspathRoute.dispatch(
                    programPath = program,
                    orderedClasspathPaths = orderedClasspath,
                    resolvePath = files::nonNullPath,
                ) { programJar, classpathJars ->
                    sClassLoader.loadJarWithExperimentalCompilerClasspath(programJar, classpathJars)
                }
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    /**
     * Explicit full-release R8 entry. It is independent from loadJar/loadJarWithClasspath and
     * deliberately has no D8/dx fallback.
     */
    fun loadJarWithR8(
        program: String,
        keepRules: Array<String>,
    ) = loadJarWithR8(
        program,
        keepRules,
        emptyArray(),
        emptyArray(),
        IntArray(0),
    )

    fun loadJarWithR8(
        program: String,
        keepRules: Array<String>,
        orderedClasspath: Array<String>,
    ) = loadJarWithR8(
        program,
        keepRules,
        orderedClasspath,
        emptyArray(),
        IntArray(0),
    )

    fun loadJarWithR8(
        program: String,
        keepRules: Array<String>,
        orderedClasspath: Array<String>,
        consumerRules: Array<String>,
        consumerRuleClasspathOrdinals: IntArray,
    ) {
        loadJarWithR8Internal(
            program,
            keepRules,
            orderedClasspath,
            consumerRules,
            consumerRuleClasspathOrdinals,
            null,
        )
    }

    fun loadJarWithR8(
        program: String,
        keepRules: Array<String>,
        exportDirectory: String,
    ) = loadJarWithR8Internal(
        program,
        keepRules,
        emptyArray(),
        emptyArray(),
        IntArray(0),
        exportDirectory,
    )

    fun loadJarWithR8(
        program: String,
        keepRules: Array<String>,
        orderedClasspath: Array<String>,
        exportDirectory: String,
    ) = loadJarWithR8Internal(
        program,
        keepRules,
        orderedClasspath,
        emptyArray(),
        IntArray(0),
        exportDirectory,
    )

    fun loadJarWithR8(
        program: String,
        keepRules: Array<String>,
        orderedClasspath: Array<String>,
        consumerRules: Array<String>,
        consumerRuleClasspathOrdinals: IntArray,
        exportDirectory: String,
    ) = loadJarWithR8Internal(
        program,
        keepRules,
        orderedClasspath,
        consumerRules,
        consumerRuleClasspathOrdinals,
        exportDirectory,
    )

    private fun loadJarWithR8Internal(
        program: String,
        keepRules: Array<String>,
        orderedClasspath: Array<String>,
        consumerRules: Array<String>,
        consumerRuleClasspathOrdinals: IntArray,
        exportDirectory: String?,
    ) {
        try {
            R8CompilerExplicitRuntimeRoute.dispatch(
                programPath = program,
                keepRulePaths = keepRules,
                orderedClasspathPaths = orderedClasspath,
                consumerRulePaths = consumerRules,
                consumerRuleClasspathOrdinals = consumerRuleClasspathOrdinals,
                minApi = Build.VERSION.SDK_INT,
                exportDirectoryPath = exportDirectory,
                resolvePath = files::nonNullPath,
                compile = { spec ->
                    AndroidR8CompilerHost.runtimeDispatcher(applicationContext).compile(spec)
                },
                loadPublished = { artifact -> ownLoads { sClassLoader.loadR8CompilerDexZip(artifact) } },
            )
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    /** Provider-only protocol 1.1 stack retrace with mapping provenance verification. */
    fun retraceR8Stack(
        obfuscatedStackTrace: String,
        mapping: String,
        retraceMetadata: String,
    ): String = try {
        R8RetraceExplicitRuntimeRoute.dispatch(
            obfuscatedStackTrace = obfuscatedStackTrace,
            mappingPath = mapping,
            retraceMetadataPath = retraceMetadata,
            resolvePath = files::nonNullPath,
            retrace = { spec ->
                AndroidR8CompilerHost.runtimeRetraceDispatcher(applicationContext).retrace(spec)
            },
        )
    } catch (error: IOException) {
        throw UncheckedIOException(error)
    }

    fun loadJar(dir: String?, isRecursive: Boolean) {
        doLoadJar({ f: File -> isJarFile(f) || isRecursive && f.isDirectory }, dir!!)
    }

    fun loadAar(vararg paths: String) {
        doLoadAar({ f: File -> isAarFile(f) }, *paths)
    }

    fun loadAar(dir: String?, isRecursive: Boolean) {
        doLoadAar({ f: File -> isAarFile(f) || isRecursive && f.isDirectory }, dir!!)
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

    fun cancelScriptJobs() {
        mJob.cancel(CancellationException(mUiHandlerAppContext.getString(R.string.error_script_is_on_exiting)))
    }

    fun onExit() {
        Log.d(TAG, "on exit")
        this.isExiting = true

        // Every release step is registered in resourceScope, by the modules when they create their resources and by ownExitSteps
        // for the state of this runtime; close runs them once, in the fixed order of RuntimeResourceScope.Stage (the order of
        // this method before roadmap P4.1, appendix B.3 of docs/memory/ROADMAP.md), and a failing step does not stop the others.
        // zh-CN: 每个释放步骤都登记在 resourceScope 中: 各模块在创建资源时登记, 本运行时自身的状态由 ownExitSteps 登记; close 只执行一次,
        // 按 RuntimeResourceScope.Stage 的固定顺序 (即 P4.1 之前本方法的顺序, docs/memory/ROADMAP.md 附录 B.3), 某一步失败不影响其余步骤.
        resourceScope.close()
    }

    /**
     * Registers the exit steps of this runtime's own state (roadmap P4.1); the modules register theirs when they create their resources.
     * zh-CN: 登记本运行时自身状态的退出步骤 (ROADMAP P4.1); 各模块在创建资源时登记各自的步骤.
     */
    private fun ownExitSteps() {
        val scope = resourceScope
        scope.own(Stage.CONSOLE, "console.onRuntimeExit") {
            console.onRuntimeExit()
            if (console.configurator.isExitOnClose) {
                console.hideDelayed()
            }
        }

        // @Hint by 抠脚本人 (https://github.com/little-alei) on Jul 10, 2023.
        //  ! 清空无障碍事件.
        //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
        //  ! To clear accessibility event callbacks.
        // @Hint by SuperMonster003 on Dec 29, 2025.
        //  ! Only clean up accessibility event callbacks registered
        //  ! by the current script to avoid affecting other still-running scripts.
        //  ! zh-CN: 只清理当前脚本注册的无障碍事件回调, 避免影响其他仍在运行的脚本.
        //  # ignoresException({ AccessibilityService.clearAccessibilityEventCallback() })
        scope.own(Stage.ACCESSIBILITY_EVENTS, "automator.removeAllEventsForThisRuntime") { automator.removeAllEventsForThisRuntime() }

        // Drop the event subscription of this script's event-driven waits and event-assisted polling (roadmap P5.2).
        // zh-CN: 取消本脚本事件驱动等待与事件辅助轮询的事件订阅 (Roadmap P5.2).
        scope.own(Stage.ACCESSIBILITY_PULSE, "accessibilityBridge.releasePulse") { accessibilityBridge.releasePulse() }

        // Drop the accessibility state listener registered by this script (auto.stateListener).
        // zh-CN: 移除当前脚本注册的无障碍状态监听器 (auto.stateListener).
        scope.own(Stage.ACCESSIBILITY_LISTENER, "accessibilityBridge.setAccessibilityListener") { accessibilityBridge.setAccessibilityListener(null) }

        scope.own(Stage.ROOT_MODE, "RootUtils.resetRuntimeOverriddenRootModeState") { RootUtils.resetRuntimeOverriddenRootModeState() }

        scope.own(Stage.COMPOSE, "compose.close") { compose?.close() }

        // Clear interrupt status.
        // zh-CN: 清除 interrupt 状态.
        scope.own(Stage.INTERRUPT_STATUS, "ThreadCompat.interrupted") { ThreadCompat.interrupted() }

        scope.own(Stage.MAIL, "mail.close") { mail.close() }
        scope.own(Stage.INSTALLER, "installer.close") { installer.close() }
        scope.own(Stage.EPUB, "epub.close") { epub.close() }
        scope.own(Stage.GALLERY, "gallery.close") { gallery.close() }
        scope.own(Stage.TERMINAL, "terminal.close") { terminal.close() }
        scope.own(Stage.SPEECH, "speech.close") { speech.close() }
        scope.own(Stage.AI, "ai.close") { ai.close() }
        scope.own(Stage.TTS, "tts.close") { tts.close() }

        // The gesture dispatcher installed on this script's bridge runs on the servant looper just quit by loopers.recycle.
        // The bridge may stay referenced as the process-wide default bridge (AccessibilityService.bridge), so drop the dispatcher,
        // which holds this runtime (docs/memory/ROADMAP.md H17); nodes found through the bridge then use a standalone automator.
        // zh-CN: 本脚本 bridge 上安装的手势派发器运行在刚被 loopers.recycle 退出的 servant Looper 上.
        // 该 bridge 可能仍作为进程级默认 bridge (AccessibilityService.bridge) 被引用, 因此移除持有本运行时的派发器 (docs/memory/ROADMAP.md H17); 之后经该 bridge 找到的节点使用独立的 automator.
        scope.own(Stage.ACCESSIBILITY_TAP, "accessibilityBridge.tap") { accessibilityBridge.tap = null }

        // The modules of this script have removed their accessibility registrations by now; release whatever is left by owner id, so that
        // nothing on the process-wide buses keeps this runtime after a missed release, and log it (docs/memory/ROADMAP.md T7 / P4.2).
        // zh-CN: 此时本脚本各模块已移除各自的无障碍登记; 按 owner id 释放剩余的登记, 使进程级总线上的遗漏不会钉住本运行时, 并记录日志 (docs/memory/ROADMAP.md T7 / P4.2).
        scope.own(Stage.ACCESSIBILITY_OWNER, "AccessibilityService.releaseOwner") {
            val left = AccessibilityService.releaseOwner(ownerId)
            if (left > 0) Log.w(TAG, "$ownerId: released $left accessibility registration(s) its modules left behind")
        }

        // The loaders of runtime.loadDex / loadJar / loadAar that no other running script has loaded are no longer searched,
        // so they can be unloaded with their classes (docs/memory/ROADMAP.md H3 / P4.4). Last, after the closeables that may still
        // use the classes.
        // zh-CN: 其他运行中的脚本未加载过的 runtime.loadDex / loadJar / loadAar 加载器不再被搜索, 可与其中的类一起卸载 (docs/memory/ROADMAP.md H3 / P4.4).
        // 放在最后, 在可能仍用到这些类的 closeables 之后.
        scope.own(Stage.CLASS_LOADERS, "AndroidClassLoader.releaseOwner") {
            val released = sClassLoader.releaseOwner(ownerId)
            if (released > 0) Log.d(TAG, "$ownerId: released $released class loader(s) of runtime.loadDex / loadJar")
        }
    }

    fun setScreenMetrics(width: Int, height: Int) {
        screenMetrics.setScreenMetrics(width, height)
    }

    fun getProperty(key: String) = mProperties[key]

    fun beforeSetUiContent(view: android.view.View) { compose?.beforeSetContentView(view) }

    fun putProperty(key: String, value: Any) = mProperties.put(key, value)

    fun removeProperty(key: String) = mProperties.remove(key)

    fun createContinuation() = RhinoContinuation.create(this, topLevelScope)

    fun createContinuation(scope: Scriptable) = RhinoContinuation.create(this, scope)

    /**
     * Runs a load of runtime.loadDex / loadJar / loadAar for this script: the loaders it registers are released when the
     * script ends ([Stage.CLASS_LOADERS], docs/memory/ROADMAP.md H3 / P4.4).
     * zh-CN: 以本脚本的名义执行 runtime.loadDex / loadJar / loadAar 的加载: 期间登记的加载器在脚本结束时释放 ([Stage.CLASS_LOADERS], docs/memory/ROADMAP.md H3 / P4.4).
     */
    private fun <T> ownLoads(load: () -> T): T = sClassLoader.loadFor(ownerId, load)

    private fun doLoad(filter: FileFilter, vararg paths: String) {
        for (path in paths) {
            val file = File(files.nonNullPath(path))
            if (file.isDirectory) {
                val filtered = file.listFiles(filter)
                if (filtered != null) {
                    for (f in filtered) {
                        if (isJarFile(f)) loadJarWithoutExperimentalCompiler(f)
                        else if (isDexFile(f)) loadDex(f.path)
                        else if (isAarFile(f)) loadAar(f.path)
                    }
                }
            } else {
                if (isJarFile(file)) loadJarWithoutExperimentalCompiler(file)
                else if (isDexFile(file)) loadDex(file.path)
                else if (isAarFile(file)) loadAar(file.path)
            }
        }
    }

    /** The compatibility load() multiplexer stays local; only explicit loadJar() may opt in. */
    private fun loadJarWithoutExperimentalCompiler(file: File) {
        try {
            ownLoads { sClassLoader.loadJar(file) }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun doLoadDex(filter: FileFilter, vararg paths: String) {
        try {
            val classLoader = sClassLoader
            for (path in paths) {
                val file = File(files.nonNullPath(path))
                if (file.isDirectory) {
                    val filtered = file.listFiles(filter)
                    if (filtered != null) {
                        for (f in filtered) {
                            loadDex(f.path)
                        }
                    }
                } else {
                    ownLoads { classLoader.loadDex(file) }
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
                val file = File(files.nonNullPath(path))
                if (file.isDirectory) {
                    val filtered = file.listFiles(filter)
                    if (filtered != null) {
                        for (f in filtered) {
                            loadJar(f.path)
                        }
                    }
                } else {
                    ownLoads { classLoader.loadJarWithExperimentalCompiler(file) }
                }
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun doLoadAar(filter: FileFilter, vararg paths: String) {
        try {
            val classLoader = sClassLoader
            for (path in paths) {
                val file = File(files.nonNullPath(path))
                if (file.isDirectory) {
                    val filtered = file.listFiles(filter)
                    if (filtered != null) {
                        for (f in filtered) {
                            loadAar(f.path)
                        }
                    }
                } else {
                    ownLoads { classLoader.loadAar(file) }
                }
            }
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun augment(target: ScriptableObject) {

        // Roadmap T8 / appendix C Q8: ScriptModuleLayer.ORDER decides where every module is mounted. With the hidden
        // switch on, the migrated modules come from the new script module layer at their position; with it off, the
        // mounter is null and every module comes from its old augment call (augmentLegacy) as before.
        // zh-CN: ROADMAP T8 / 附录 C Q8: ScriptModuleLayer.ORDER 决定每个模块的挂载位置. 隐藏开关打开时, 已迁移的模块在该位置由新脚本模块层挂载;
        // 关闭时 mounter 为 null, 所有模块照旧来自原 augment 调用 (augmentLegacy).
        val modules = ScriptModuleLayer.mounterOrNull(this, target).also { scriptModules = it }
        ScriptModuleLayer.mountInOrder(modules) { name -> augmentLegacy(target, name) }

        // With the new layer, app.autojs is defined by the app module when it is built (P3.4).
        // zh-CN: 新层开启时, app.autojs 由 app 模块在构建时定义 (P3.4).
        if (modules == null) augmentedApp.defineProp(Autojs::class.java.simpleName.lowercase(), augmentedAutojs)
    }

    /**
     * The old augment call of the top-level module [name] of `ScriptModuleLayer.ORDER`, its sub-modules included (the
     * `augment` package, unchanged until P5.2 by roadmap R5).
     * zh-CN: `ScriptModuleLayer.ORDER` 中顶层模块 [name] 的原 augment 调用, 含其子模块 (augment 包, 按 R5 在 P5.2 前不改动).
     */
    private fun augmentLegacy(target: ScriptableObject, name: String) {
        when (name) {
            "global" -> Global(this).assignWithRuntime(target, this, GlobalClasses)
            "legacy" -> GlobalLegacy(this).assignWithRuntime(target, this)
            "isNullish" -> IsNullish.augmentWithRuntime(target, this)
            "util" -> Util.augmentWithRuntime(target, this, util).also { util ->
                UtilJava.augmentWithRuntime(util, this)
                UtilVersion.augmentWithRuntime(util, this)
                UtilVersionCodes.augmentWithRuntime(util, this, VersionCodesInfo.newObj(topLevelScope))
                UtilInspect.augmentWithRuntime(util, this)
                UtilMorseCode.augmentWithRuntime(util, this)
            }
            "species" -> Species.augmentWithRuntime(target, this)
            "app" -> App(this).augment(target, app).also { augmentedApp = it }
            "autojs" -> Autojs(this).augment(target).also { legacyAugmentedAutojs = it }.also { autojs ->
                AutojsVersion.augmentWithRuntime(autojs, this)
            }
            "shell" -> Shell(this).augment(target)
            "timers" -> Timers(this).augment(target, timers)
            "auto" -> Auto(this).augment(target)
            "automator" -> Automator(this).augment(target)
            "flow" -> Flow(this).augment(target)
            "selector" -> Selector(this).augment(target, additionalAttributes = READONLY)
            "events" -> Events(this).augment(target, events)
            "keys" -> Keys.augmentWithRuntime(target, this)
            "Canvas" -> Canvas(this).augment(target)
            "images" -> Images(this).augment(target)
            "ocr" -> Ocr(this).augment(target).also { ocr ->
                OcrMLKit(this).augment(ocr).also { legacyAugmentedOcrMLKit = it }
                OcrPaddle(this).augment(ocr).also { legacyAugmentedOcrPaddle = it }
                OcrRapid(this).augment(ocr).also { legacyAugmentedOcrRapid = it }
            }
            "barcode" -> Barcode(this).augment(target)
            "qrcode" -> QrCode(this).augment(target)
            "yolo" -> Yolo(this).augment(target)
            "threads" -> Threads(this).augment(target, threads)
            "ui" -> UI(this).proxying(target, ui)
            "colors" -> Colors.augmentWithRuntime(target, this, Colors.colorTables + colors).also { colors ->
                ColorsTheme(this).augment(colors)
            }
            "Color" -> Color.augmentWithRuntime(target, this, Colors.colorTables)
            "tasks" -> Tasks(this).augment(target)
            "workManager" -> WorkManager(this).augment(target)
            "dialogs" -> Dialogs(this).augment(target)
            "continuation" -> Continuation(this).augment(target, js_mod_continuation, additionalAttributes = READONLY)
            "ai" -> Ai(this, ai).augment(target)
            "tts" -> Tts(this, tts).augment(target)
            "http" -> Http(this).augment(target, http)
            "web" -> Web(this).augment(target)
            "WebSocket" -> WebSocket(this).augment(target, WebSocketFields)
            "s13n" -> S13n.augmentWithRuntime(target, this)
            "cvt" -> Converter.augmentWithRuntime(target, this).also { cvt ->
                BytesCvt.augmentWithRuntime(cvt, this)
            }
            "fmt" -> Formatter.augmentWithRuntime(target, this).also { fmt ->
                BytesFmt.augmentWithRuntime(fmt, this)
            }
            "console" -> Console(this).proxying(target, console).also { legacyConsoleProxyObject = it }
            "plugins" -> Plugins(this).augment(target).also { PluginsTools(this).augment(it) }
            "Arrayx" -> Arrayx(this).augment(target)
            "Numberx" -> Numberx(this).augment(target)
            "Mathx" -> Mathx(this).augment(target)
            "jsox" -> Jsox(this).augment(target)
            "files" -> Files(this).augment(target, files).also { BatchRename(this).augment(it) }
            "crypto" -> Crypto.augmentWithRuntime(target, this, CoreCrypto)
            "RootAutomator" -> RootAutomator(this).augment(target)
            "engines" -> Engines(this).augment(target)
            "floaty" -> Floaty(this).augment(target)
            "storages" -> Storages.augmentWithRuntime(target, this)
            "device" -> Device(this).augment(target, device)
            "powerManager" -> PowerManager(this).augment(target)
            "settings" -> Settings(this).augment(target)
            "recorder" -> Recorder(this).augment(target, recorder)
            "toast" -> Toast(this).augment(target)
            "media" -> Media.augmentWithRuntime(target, this, media)
            "sensors" -> Sensors.augmentWithRuntime(target, this, sensors)
            "base64" -> Base64.augmentWithRuntime(target, this)
            "notice" -> Notice(this).augment(target).also { notice ->
                NoticeChannel(this).augment(notice)
            }
            "shizuku" -> Shizuku(this).augment(target, shizuku)
            "opencc" -> OpenCC.augmentWithRuntime(target, this)
            "mime" -> Mime(this).augment(target, mime)
            "sysprops" -> SysProps(this).augment(target)
            "sqlite" -> SQLite(this).augment(target)
            "zip" -> Zip(this).augment(target)
            "nanoid" -> NanoID.augmentWithRuntime(target, this)
            "pinyin" -> Pinyin.augmentWithRuntime(target, this)
            "pinyin4j" -> Pinyin4j.augmentWithRuntime(target, this)
            "mediainfo" -> Mediainfo(this).augment(target)
            "mail" -> Mail(this, mail).augment(target)
            "installer" -> Installer(this, installer).augment(target)
            "epub" -> Epub(this, epub).augment(target)
            "gallery" -> Gallery(this, gallery).augment(target)
            "terminal" -> Terminal(this, terminal).augment(target)
            "speech" -> Speech(this, speech, tts).augment(target).also { SpeechModels(this, speech).augment(it) }
            "compose" -> Compose(this).also { compose = it }.augment(target)
            "memory" -> Memory(this).augment(target)
            else -> throw IllegalStateException("Script module $name has no augment call")
        }
    }

    /** `__exitIfError__` and `__asGlobal__`, in Kotlin since roadmap P2.5 (formerly `assets/modules/internal.js`). */
    private fun applyInternalModules(global: ScriptableObject) {
        InternalGlobals.install(topLevelScope, global)
    }

    private fun rhinoRequire(id: String): Any? {
        return callFunction(this, rhinoRequireFunction, topLevelScope, topLevelScope.globalThis, arrayOf(id))
    }

    /** Whether [scriptRequire] can take the named module [id] from the engine's Rhino `require`, without running it. */
    private fun canRequireNamedModule(id: String): Boolean = isJvmNpm(id) ||
        (engines.myEngine() as? RhinoJavaScriptEngine)?.canRequireNamedModule(RhinoContext.getCurrentContext(), id) == true

    private fun requireNamedModule(id: String): Any? = if (isJvmNpm(id)) scriptRequire.module else rhinoRequire(id)

    private fun isJvmNpm(id: String) = id == JVM_NPM || id == "$JVM_NPM.js"

    class Builder {

        lateinit var shellSupplier: Supplier<AbstractShell>
            private set

        lateinit var appUtils: AppUtils
            private set

        private lateinit var mConsole: ConsoleImpl
        private lateinit var mUiHandler: UiHandler
        private lateinit var mAccessibilityBridge: AccessibilityBridge

        private var mScreenCaptureRequester: ScreenCaptureRequester? = null

        fun setUiHandler(uiHandler: UiHandler) = also { mUiHandler = uiHandler }

        fun getUiHandler() = mUiHandler

        fun setConsole(console: ConsoleImpl) = also { mConsole = console }

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

        private const val JVM_NPM = "jvm-npm"

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

        private fun isAarFile(f: File): Boolean {
            return PFiles.getExtension(f.name).equals("aar", ignoreCase = true)
        }
    }
}
