package org.autojs.autojs.core.plugin.python

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.accessibility.AccessibilityService as AutoJsAccessibilityService
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.window.A11yWindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootProvider
import org.autojs.autojs.core.automator.GlobalActionAutomator
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.ScriptToast
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectSource
import org.autojs.autojs.script.PythonScriptSource
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs6.R
import org.autojs.plugin.python.runtime.api.IPythonHostCapabilityBroker
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.round

internal object PythonHostCapabilities {
    const val TOAST_SHOW = "toast.show"
    const val CLIP_GET = "clip.get"
    const val CLIP_SET = "clip.set"
    const val APP_LAUNCH = "app.launch"
    const val APP_LAUNCH_APP = "app.launch_app"
    const val APP_OPEN_URL = "app.open_url"
    const val DEVICE_INFO = "device.info"
    const val CONSOLE_LOG = "console.log"
    const val CONSOLE_WARN = "console.warn"
    const val CONSOLE_ERROR = "console.error"
    const val NOTICE_SHOW = "notice.show"
    const val FILES_READ_TEXT = "files.read_text"
    const val FILES_WRITE_TEXT = "files.write_text"
    const val FILES_EXISTS = "files.exists"
    const val FILES_IS_FILE = "files.is_file"
    const val FILES_IS_DIR = "files.is_dir"
    const val FILES_LIST = "files.list"
    const val DIALOGS_ALERT = "dialogs.alert"
    const val DIALOGS_CONFIRM = "dialogs.confirm"
    const val DIALOGS_PROMPT = "dialogs.prompt"
    const val DIALOGS_SELECT = "dialogs.select"
    const val ENGINES_CURRENT = "engines.current"
    const val ENGINES_STOP_SELF = "engines.stop_self"
    const val ENGINES_RUN = "engines.run"
    const val AUTOMATOR_CLICK = "automator.click"
    const val AUTOMATOR_LONG_CLICK = "automator.long_click"
    const val AUTOMATOR_PRESS = "automator.press"
    const val AUTOMATOR_SWIPE = "automator.swipe"
    const val AUTOMATOR_BACK = "automator.back"
    const val AUTOMATOR_HOME = "automator.home"
    const val SELECTOR_SNAPSHOT = "selector.snapshot"
    const val SELECTOR_FIND = "selector.find"
    const val SELECTOR_CLICK = "selector.click"
    const val SELECTOR_SET_TEXT = "selector.set_text"
    const val IMAGES_CAPTURE_SCREEN = "images.capture_screen"
    const val IMAGES_FIND_COLOR = "images.find_color"
    const val IMAGES_BEGIN_TEMPLATE = "images.begin_template"
    const val IMAGES_WRITE_TEMPLATE_CHUNK = "images.write_template_chunk"
    const val IMAGES_FIND_IMAGE = "images.find_image"
    const val IMAGES_RELEASE_TEMPLATE = "images.release_template"
    const val OCR_RECOGNIZE = "ocr.recognize"
    const val IMAGES_READ_CHUNK = "images.read_chunk"
    const val IMAGES_RELEASE = "images.release"

    val ENABLED = setOf(
        TOAST_SHOW,
        CLIP_GET,
        CLIP_SET,
        APP_LAUNCH,
        APP_LAUNCH_APP,
        APP_OPEN_URL,
        DEVICE_INFO,
        CONSOLE_LOG,
        CONSOLE_WARN,
        CONSOLE_ERROR,
        NOTICE_SHOW,
        FILES_READ_TEXT,
        FILES_WRITE_TEXT,
        FILES_EXISTS,
        FILES_IS_FILE,
        FILES_IS_DIR,
        FILES_LIST,
        DIALOGS_ALERT,
        DIALOGS_CONFIRM,
        DIALOGS_PROMPT,
        DIALOGS_SELECT,
        ENGINES_CURRENT,
        ENGINES_STOP_SELF,
        ENGINES_RUN,
        AUTOMATOR_CLICK,
        AUTOMATOR_LONG_CLICK,
        AUTOMATOR_PRESS,
        AUTOMATOR_SWIPE,
        AUTOMATOR_BACK,
        AUTOMATOR_HOME,
        SELECTOR_SNAPSHOT,
        SELECTOR_FIND,
        SELECTOR_CLICK,
        SELECTOR_SET_TEXT,
        IMAGES_CAPTURE_SCREEN,
        IMAGES_FIND_COLOR,
        IMAGES_BEGIN_TEMPLATE,
        IMAGES_WRITE_TEMPLATE_CHUNK,
        IMAGES_FIND_IMAGE,
        IMAGES_RELEASE_TEMPLATE,
        OCR_RECOGNIZE,
        IMAGES_READ_CHUNK,
        IMAGES_RELEASE,
    )
}

internal object PythonHostAutomatorPolicy {
    const val MAX_COORDINATE = 1_000_000
    const val MAX_DURATION_MILLIS = 4_000
}

internal enum class PythonHostConsoleLevel {
    LOG,
    WARN,
    ERROR,
}

internal data class PythonHostDeviceInfo(
    val batteryPercent: Double,
    val charging: Boolean,
    val screenOn: Boolean,
    val brightness: Int,
    val musicVolume: Int,
    val musicMaxVolume: Int,
    val notificationVolume: Int,
    val notificationMaxVolume: Int,
    val alarmVolume: Int,
    val alarmMaxVolume: Int,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", SCHEMA)
        add(
            "battery",
            JsonObject().apply {
                addProperty("percent", batteryPercent)
                addProperty("charging", charging)
            },
        )
        add(
            "screen",
            JsonObject().apply {
                addProperty("on", screenOn)
                addProperty("brightness", brightness)
            },
        )
        add(
            "volume",
            JsonObject().apply {
                add("music", volumeJson(musicVolume, musicMaxVolume))
                add("notification", volumeJson(notificationVolume, notificationMaxVolume))
                add("alarm", volumeJson(alarmVolume, alarmMaxVolume))
            },
        )
    }

    private fun volumeJson(current: Int, maximum: Int) = JsonObject().apply {
        addProperty("current", current)
        addProperty("max", maximum)
    }

    private companion object {
        const val SCHEMA = "autojs6-python-device-info-v1"
    }
}

internal object PythonHostNoticePolicy {
    const val CHANNEL_ID = "autojs6-python"
    const val NOTIFICATION_ID = 1
    const val MAX_TEXT_BYTES = 4 * 1024
    const val TAG_PREFIX = "autojs6-python:"

    fun tagForExecution(executionId: String) = "$TAG_PREFIX$executionId"
}

internal object PythonHostFilePolicy {
    const val ROOT_PATH = "."
    const val MAX_PATH_BYTES = 4 * 1024
    const val MAX_TEXT_BYTES = PythonRuntimeContract.MAX_HOST_CAPABILITY_TEXT_BYTES
    const val MAX_LIST_ENTRIES = 128
    const val MAX_NAME_BYTES = 255

    fun rootFor(source: PythonFileSource): File {
        val candidate = (source as? PythonProjectSource)?.projectRoot
            ?: requireNotNull(source.semanticFile.absoluteFile.parentFile) {
                "Python standalone source has no parent directory"
            }
        return candidate.canonicalFile.also { root ->
            require(root.isDirectory) { "Python Host file root must be a directory" }
        }
    }

    fun normalizeRelative(value: String): String {
        require(value.isNotEmpty()) { "Python Host file path is empty" }
        require(value.toByteArray(StandardCharsets.UTF_8).size <= MAX_PATH_BYTES) {
            "Python Host file path exceeds the byte limit"
        }
        require(
            value == ROOT_PATH ||
                !value.startsWith('/') &&
                !WINDOWS_DRIVE_PATH.matches(value) &&
                '\\' !in value &&
                value.none(Character::isISOControl) &&
                Normalizer.normalize(value, Normalizer.Form.NFC) == value &&
                value.split('/').none { it.isEmpty() || it == "." || it == ".." },
        ) {
            "Python Host file path must be normalized and relative"
        }
        return value
    }

    private val WINDOWS_DRIVE_PATH = Regex("^[A-Za-z]:.*")
}

internal class PythonHostFileScope(root: File) {
    private val root = root.canonicalFile.also { candidate ->
        require(candidate.isDirectory) { "Python Host file root must be a directory" }
    }
    private val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
    private val ignoreCase = File.separatorChar == '\\'

    fun readText(path: String): String {
        val file = resolve(path)
        if (!file.exists()) throw PythonHostCapabilityPathNotFoundException()
        if (!file.isFile) throw PythonHostCapabilityPathTypeException()
        if (file.length() > PythonHostFilePolicy.MAX_TEXT_BYTES) {
            throw PythonHostCapabilityResultLimitException()
        }
        val bytes = file.readBytes()
        if (bytes.size > PythonHostFilePolicy.MAX_TEXT_BYTES) {
            throw PythonHostCapabilityResultLimitException()
        }
        return decodeStrictUtf8(bytes)
    }

    fun writeText(path: String, text: String) {
        val file = resolve(path)
        if (file.exists() && !file.isFile) throw PythonHostCapabilityPathTypeException()
        val parent = file.parentFile
        if (parent == null || !parent.exists()) throw PythonHostCapabilityPathNotFoundException()
        if (!parent.isDirectory) throw PythonHostCapabilityPathTypeException()
        val bytes = encodeStrictUtf8(text)
        require(bytes.size <= PythonHostFilePolicy.MAX_TEXT_BYTES) {
            "Python Host file text exceeds the byte limit"
        }
        file.writeBytes(bytes)
    }

    fun exists(path: String): Boolean = resolve(path).exists()

    fun isFile(path: String): Boolean = resolve(path).isFile

    fun isDirectory(path: String): Boolean = resolve(path).isDirectory

    fun list(path: String): List<String> {
        val directory = resolve(path)
        if (!directory.exists()) throw PythonHostCapabilityPathNotFoundException()
        if (!directory.isDirectory) throw PythonHostCapabilityPathTypeException()
        val entries = directory.list()?.toList()
            ?: throw PythonHostCapabilityPermissionDeniedException()
        if (entries.size > PythonHostFilePolicy.MAX_LIST_ENTRIES) {
            throw PythonHostCapabilityResultLimitException()
        }
        entries.forEach { entry ->
            if (
                entry.isEmpty() ||
                '/' in entry ||
                '\\' in entry ||
                entry.any(Character::isISOControl) ||
                Normalizer.normalize(entry, Normalizer.Form.NFC) != entry ||
                encodeStrictUtf8(entry).size > PythonHostFilePolicy.MAX_NAME_BYTES
            ) {
                throw PythonHostCapabilityResultLimitException()
            }
        }
        return entries.sorted()
    }

    fun requireRegularFile(path: String): File {
        val file = resolve(path)
        if (!file.exists()) throw PythonHostCapabilityPathNotFoundException()
        if (!file.isFile) throw PythonHostCapabilityPathTypeException()
        return file
    }

    fun contains(file: File): Boolean {
        val candidate = file.canonicalFile
        return candidate == root || candidate.path.startsWith(rootPrefix, ignoreCase = ignoreCase)
    }

    val rootDirectory: File
        get() = root

    private fun resolve(path: String): File {
        val relative = PythonHostFilePolicy.normalizeRelative(path)
        if (relative == PythonHostFilePolicy.ROOT_PATH) return root
        val candidate = File(root, relative.replace('/', File.separatorChar)).canonicalFile
        require(candidate.path.startsWith(rootPrefix, ignoreCase = ignoreCase)) {
            "Python Host file path escapes the execution root"
        }
        return candidate
    }

    private fun decodeStrictUtf8(value: ByteArray): String = runCatching {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(value))
            .toString()
    }.getOrElse { throw PythonHostCapabilityFileEncodingException(it) }

    private fun encodeStrictUtf8(value: String): ByteArray = runCatching {
        val encoded = StandardCharsets.UTF_8
            .newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value))
        ByteArray(encoded.remaining()).also(encoded::get)
    }.getOrElse { throw IllegalArgumentException("Python Host file text is not valid UTF-8", it) }
}

internal object PythonHostEnginePolicy {
    const val INFO_SCHEMA = "autojs6-python-engine-info-v1"
    const val LAUNCH_SCHEMA = "autojs6-python-engine-launch-v1"
    const val MAX_SCRIPT_LAUNCHES = 16
    const val MAX_ENGINE_NAME_BYTES = 256
    const val MAX_SOURCE_NAME_BYTES = 1024
}

internal data class PythonHostEngineInfo(
    val id: Int,
    val engineName: String,
    val sourceName: String,
    val entryPoint: String,
    val project: Boolean,
    val startedAtMillis: Long,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", PythonHostEnginePolicy.INFO_SCHEMA)
        addProperty("id", id)
        addProperty("engineName", engineName)
        addProperty("sourceName", sourceName)
        addProperty("entryPoint", entryPoint)
        addProperty("project", project)
        addProperty("startedAtMillis", startedAtMillis)
    }
}

internal data class PythonHostEngineLaunch(
    val id: Int,
    val engineName: String,
    val sourceName: String,
    val path: String,
) {
    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("schema", PythonHostEnginePolicy.LAUNCH_SCHEMA)
        addProperty("id", id)
        addProperty("engineName", engineName)
        addProperty("sourceName", sourceName)
        addProperty("path", path)
    }
}

/** Bounded execution-local access to Host engine identity, self-stop and child launch. */
internal class PythonHostEngineScope(
    source: PythonFileSource,
    private val hostFiles: PythonHostFileScope,
    engineId: Int,
    engineStartedAtMillis: Long,
    private val stopSelf: () -> Unit,
    private val scriptLauncher: (ScriptLaunchSourceFactory.Launch, ExecutionConfig) -> PythonHostEngineLaunch =
        { launch, config ->
            val execution = ScriptEngineService.getInstance().execute(launch.source, config)
            PythonHostEngineLaunch(
                id = execution.id,
                engineName = launch.engineName,
                sourceName = launch.source.name,
                path = launch.source.name,
            )
        },
) {
    private val launchedScripts = AtomicInteger(0)
    private val info = PythonHostEngineInfo(
        id = engineId,
        engineName = PythonScriptSource.ENGINE,
        sourceName = source.name.takeIf(String::isNotBlank) ?: source.semanticFile.name,
        entryPoint = (source as? PythonProjectSource)?.entryPoint ?: source.semanticFile.name,
        project = source is PythonProjectSource,
        startedAtMillis = engineStartedAtMillis.coerceAtLeast(0L),
    ).also(::validateInfo)

    fun current(): PythonHostEngineInfo = info

    fun requestStopSelf() = stopSelf()

    fun runScript(path: String): PythonHostEngineLaunch {
        val relative = PythonHostFilePolicy.normalizeRelative(path)
        val file = hostFiles.requireRegularFile(relative)
        val launch = ScriptLaunchSourceFactory.createPathLaunch(
            path = file.path,
            workingDirectory = hostFiles.rootDirectory.path,
            defaultWorkingDirectory = hostFiles.rootDirectory.path,
        )
        if (launch.engineName == PythonScriptSource.ENGINE) {
            throw PythonHostCapabilityNestedPythonException()
        }
        launch.nodeProjectLaunchSource?.nodeProjectRootFile?.let { projectRoot ->
            require(hostFiles.contains(projectRoot)) {
                "Python child engine project root escapes the execution root"
            }
        }
        require(hostFiles.contains(File(launch.workingDirectory))) {
            "Python child engine working directory escapes the execution root"
        }
        validateLaunchCandidate(launch)
        val count = launchedScripts.incrementAndGet()
        if (count > PythonHostEnginePolicy.MAX_SCRIPT_LAUNCHES) {
            launchedScripts.decrementAndGet()
            throw PythonHostCapabilityEngineLaunchLimitException()
        }
        val config = launch.createExecutionConfig(
            base = ExecutionConfig(workingDirectory = hostFiles.rootDirectory.path),
            overrideWorkingDirectory = true,
        )
        return try {
            scriptLauncher(launch, config).copy(path = relative).also(::validateLaunch)
        } catch (error: Throwable) {
            launchedScripts.decrementAndGet()
            throw error
        }
    }

    private fun validateInfo(value: PythonHostEngineInfo) {
        require(value.id >= -1) { "Python Host engine ID is invalid" }
        requireBoundedName(value.engineName, "engine name", PythonHostEnginePolicy.MAX_ENGINE_NAME_BYTES)
        requireBoundedName(value.sourceName, "source name", PythonHostEnginePolicy.MAX_SOURCE_NAME_BYTES)
        requireBoundedName(value.entryPoint, "entry point", PythonHostFilePolicy.MAX_PATH_BYTES)
    }

    private fun validateLaunch(value: PythonHostEngineLaunch) {
        require(value.id >= 0) { "Python child engine ID is invalid" }
        requireBoundedName(value.engineName, "child engine name", PythonHostEnginePolicy.MAX_ENGINE_NAME_BYTES)
        requireBoundedName(value.sourceName, "child source name", PythonHostEnginePolicy.MAX_SOURCE_NAME_BYTES)
        PythonHostFilePolicy.normalizeRelative(value.path)
    }

    private fun validateLaunchCandidate(value: ScriptLaunchSourceFactory.Launch) {
        requireBoundedName(value.engineName, "child engine name", PythonHostEnginePolicy.MAX_ENGINE_NAME_BYTES)
        requireBoundedName(value.source.name, "child source name", PythonHostEnginePolicy.MAX_SOURCE_NAME_BYTES)
    }

    private fun requireBoundedName(value: String, label: String, maxBytes: Int) {
        require(value.isNotBlank() && value.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) {
            "Python Host $label is invalid"
        }
    }
}

internal interface PythonHostCapabilityActions {
    fun showToast(text: String)

    fun getClipboardText(): String

    fun setClipboardText(text: String)

    fun launchPackage(packageName: String): Boolean

    fun launchApp(label: String): Boolean

    fun openUrl(url: String): Boolean

    fun getDeviceInfo(): PythonHostDeviceInfo

    fun writeConsole(level: PythonHostConsoleLevel, text: String)

    fun postNotice(text: String)

    fun readFileText(path: String): String

    fun writeFileText(path: String, text: String)

    fun fileExists(path: String): Boolean

    fun isFile(path: String): Boolean

    fun isDirectory(path: String): Boolean

    fun listDirectory(path: String): List<String>

    fun showDialogAlert(title: String, text: String)

    fun showDialogConfirm(title: String, text: String): Boolean

    fun showDialogPrompt(title: String, text: String, defaultValue: String): String?

    fun showDialogSelect(title: String, items: List<String>): Int?

    fun currentEngine(): PythonHostEngineInfo

    fun requestStopSelf()

    fun runScript(path: String): PythonHostEngineLaunch

    fun click(x: Int, y: Int): Boolean

    fun longClick(x: Int, y: Int): Boolean

    fun press(x: Int, y: Int, durationMillis: Int): Boolean

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMillis: Int): Boolean

    fun back(): Boolean

    fun home(): Boolean

    fun selectorSnapshot(maxNodes: Int, maxDepth: Int): PythonHostSelectorSnapshot

    fun selectorFind(
        query: PythonHostSelectorQuery,
        maxNodes: Int,
        maxDepth: Int,
    ): PythonHostSelectorNode?

    fun clickNode(nodeId: String): Boolean

    fun setNodeText(nodeId: String, text: String): Boolean

    fun captureScreen(format: String, quality: Int): PythonHostScreenImageDescriptor

    fun findScreenColor(
        color: Int,
        threshold: Int,
        region: PythonHostImageRegion?,
    ): PythonHostColorMatch

    fun beginImageTemplate(
        format: String,
        byteLength: Int,
        sha256: String,
    ): PythonHostImageTemplateDescriptor

    fun writeImageTemplateChunk(
        templateId: String,
        offset: Int,
        data: String,
    ): PythonHostImageTemplateChunkAck

    fun findScreenImage(
        templateId: String,
        threshold: Int,
        region: PythonHostImageRegion?,
    ): PythonHostImageMatch

    fun recognizeImageText(templateId: String): List<String>

    fun releaseImageTemplate(templateId: String)

    fun readScreenImageChunk(imageId: String, offset: Int): PythonHostScreenImageChunk

    fun releaseScreenImage(imageId: String)

    fun close() = Unit
}

internal class AndroidPythonHostCapabilityActions(
    context: Context,
    executionId: String,
    fileRoot: File,
    source: PythonFileSource,
    engineId: Int,
    engineStartedAtMillis: Long,
    stopSelf: () -> Unit,
    private val globalConsole: GlobalConsole,
    private val interactiveInputController: PythonInteractiveInputController?,
) : PythonHostCapabilityActions {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val app = AppUtils(appContext)
    private val accessibilityAutomator = GlobalActionAutomator(appContext, mainHandler) {
        AutoJsAccessibilityService.instance
    }
    // Roadmap P5.4: the selector scope walks UiObject (a UiNode) with the runtime's registry
    // filters and node lifecycle; this adapter supplies the active window root (the same
    // rootInActiveWindow as before, through the window root provider), the refresh, the two
    // node actions and the wire values, restoring the null text the protocol distinguishes from
    // an empty one (UiNode.text() folds it).
    // zh-CN: Roadmap P5.4: 选择器作用域以运行时的 registry 过滤器与节点生命周期遍历 UiObject (即 UiNode); 本适配器提供活动窗口根节点
    // (仍是 rootInActiveWindow, 经窗口根节点 provider 取得), 刷新, 两个节点动作与线协议值, 并补回协议区分于空文本的 null 文本
    // (UiNode.text() 会把它折叠为空串).
    private val selector = PythonHostSelectorScope(
        object : PythonHostSelectorScope.Platform<UiObject> {
            override fun activeRoot(): UiObject? =
                WindowRootProvider.default.activeRoot(A11yWindowRootPolicy())?.let(UiObject::createRoot)

            override fun refresh(node: UiObject): Boolean = node.refresh()

            override fun click(node: UiObject): Boolean = node.click()

            override fun setText(node: UiObject, text: String): Boolean = node.setText(text)

            override fun capture(node: UiObject): PythonHostSelectorValues =
                PythonHostSelectorValues.of(node).copy(text = node.text?.toString())
        },
        NodeLifecycle.default,
    )
    private val screenImages = PythonHostScreenImageScope { format, quality ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw PythonHostCapabilityScreenCaptureUnavailableException()
        }
        PythonHostAccessibilityScreenCapture.capture(
            service = requireAccessibilityService(),
            format = format,
            quality = quality,
        )
    }
    private val imageTemplates = PythonHostImageTemplateScope()
    private val hostFiles = PythonHostFileScope(fileRoot)
    private val hostEngines = PythonHostEngineScope(
        source = source,
        hostFiles = hostFiles,
        engineId = engineId,
        engineStartedAtMillis = engineStartedAtMillis,
        stopSelf = stopSelf,
    )
    private val toastOwnerId = "python@$executionId"
    private val notificationTag = PythonHostNoticePolicy.tagForExecution(executionId)

    override fun showToast(text: String) = onMain {
        ScriptToast.enqueueToast(appContext, toastOwnerId, text)
    }

    override fun getClipboardText(): String = onMain {
        ClipboardUtils.getClipOrEmpty(appContext).toString()
    }

    override fun setClipboardText(text: String) = onMain {
        ClipboardUtils.setClip(appContext, text)
    }

    override fun launchPackage(packageName: String): Boolean = onMain {
        app.launchPackage(packageName)
    }

    override fun launchApp(label: String): Boolean = onMain {
        app.launchApp(label)
    }

    override fun openUrl(url: String): Boolean = onMain {
        runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    override fun getDeviceInfo(): PythonHostDeviceInfo = onMain {
        val batteryIntent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: error("Android battery state is unavailable")
        val batteryLevel = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val batteryScale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        require(batteryLevel >= 0 && batteryScale > 0) { "Android battery state is invalid" }
        val batteryStatus = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val audio = requireNotNull(appContext.getSystemService(AudioManager::class.java)) {
            "Android audio service is unavailable"
        }
        val power = requireNotNull(appContext.getSystemService(PowerManager::class.java)) {
            "Android power service is unavailable"
        }
        PythonHostDeviceInfo(
            batteryPercent = round((batteryLevel.toDouble() / batteryScale) * 1_000.0) / 10.0,
            charging = batteryStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                batteryStatus == android.os.BatteryManager.BATTERY_STATUS_FULL,
            screenOn = power.isInteractive,
            brightness = Settings.System.getInt(
                appContext.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                -1,
            ),
            musicVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            musicMaxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            notificationVolume = audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION),
            notificationMaxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION),
            alarmVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM),
            alarmMaxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
        )
    }

    override fun writeConsole(level: PythonHostConsoleLevel, text: String) = onMain {
        when (level) {
            PythonHostConsoleLevel.LOG -> globalConsole.log(text)
            PythonHostConsoleLevel.WARN -> globalConsole.warn(text)
            PythonHostConsoleLevel.ERROR -> globalConsole.error(text)
        }
    }

    override fun postNotice(text: String) = onMain {
        ensureNotificationPostingAllowed()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = requireNotNull(appContext.getSystemService(NotificationManager::class.java)) {
                "Android notification service is unavailable"
            }
            if (manager.getNotificationChannel(PythonHostNoticePolicy.CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        PythonHostNoticePolicy.CHANNEL_ID,
                        "AutoJs6 Python",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "Notifications posted by AutoJs6 Python scripts"
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    },
                )
            }
        }
        val notification = NotificationCompat.Builder(appContext, PythonHostNoticePolicy.CHANNEL_ID)
            .setSmallIcon(R.drawable.autojs6_status_bar_icon)
            .setContentTitle("AutoJs6 Python")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setLocalOnly(true)
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(
                notificationTag,
                PythonHostNoticePolicy.NOTIFICATION_ID,
                notification,
            )
        } catch (_: SecurityException) {
            // Permission can be revoked between the earlier check and posting.
            throw PythonHostCapabilityPermissionDeniedException()
        }
    }

    override fun readFileText(path: String): String = hostFiles.readText(path)

    override fun writeFileText(path: String, text: String) = hostFiles.writeText(path, text)

    override fun fileExists(path: String): Boolean = hostFiles.exists(path)

    override fun isFile(path: String): Boolean = hostFiles.isFile(path)

    override fun isDirectory(path: String): Boolean = hostFiles.isDirectory(path)

    override fun listDirectory(path: String): List<String> = hostFiles.list(path)

    override fun showDialogAlert(title: String, text: String) {
        val response = dialogController().requestDialog(PythonHostDialogRequest.Alert(title, text))
        check(response === PythonHostDialogResponse.Acknowledged) {
            "Python alert dialog returned the wrong response type"
        }
    }

    override fun showDialogConfirm(title: String, text: String): Boolean {
        val response = dialogController().requestDialog(PythonHostDialogRequest.Confirm(title, text))
        return (response as? PythonHostDialogResponse.Confirmation)?.confirmed
            ?: error("Python confirm dialog returned the wrong response type")
    }

    override fun showDialogPrompt(title: String, text: String, defaultValue: String): String? {
        val response = dialogController().requestDialog(
            PythonHostDialogRequest.Prompt(title, text, defaultValue),
        )
        val promptValue = response as? PythonHostDialogResponse.PromptValue
            ?: error("Python prompt dialog returned the wrong response type")
        return promptValue.value?.also { value ->
            if (value.toByteArray(StandardCharsets.UTF_8).size > PythonHostDialogPolicy.MAX_PROMPT_REPLY_BYTES) {
                throw PythonHostCapabilityResultLimitException()
            }
        }
    }

    override fun showDialogSelect(title: String, items: List<String>): Int? {
        val response = dialogController().requestDialog(PythonHostDialogRequest.Select(title, items))
        val selection = response as? PythonHostDialogResponse.Selection
            ?: error("Python select dialog returned the wrong response type")
        return selection.index
    }

    override fun currentEngine(): PythonHostEngineInfo = hostEngines.current()

    override fun requestStopSelf() {
        check(mainHandler.post(hostEngines::requestStopSelf)) {
            "AutoJs6 main dispatcher rejected a Python self-stop request"
        }
    }

    override fun runScript(path: String): PythonHostEngineLaunch = hostEngines.runScript(path)

    override fun click(x: Int, y: Int): Boolean = withAccessibility {
        accessibilityAutomator.click(x, y)
    }

    override fun longClick(x: Int, y: Int): Boolean = withAccessibility {
        accessibilityAutomator.longClick(x, y)
    }

    override fun press(x: Int, y: Int, durationMillis: Int): Boolean = withAccessibility {
        accessibilityAutomator.press(x, y, durationMillis)
    }

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMillis: Int): Boolean =
        withAccessibility {
            accessibilityAutomator.swipe(x1, y1, x2, y2, durationMillis.toLong())
        }

    override fun back(): Boolean = withAccessibility(accessibilityAutomator::back)

    override fun home(): Boolean = withAccessibility(accessibilityAutomator::home)

    override fun selectorSnapshot(maxNodes: Int, maxDepth: Int): PythonHostSelectorSnapshot =
        onMain { withAccessibility { selector.snapshot(maxNodes, maxDepth) } }

    override fun selectorFind(
        query: PythonHostSelectorQuery,
        maxNodes: Int,
        maxDepth: Int,
    ): PythonHostSelectorNode? = onMain {
        withAccessibility { selector.find(query, maxNodes, maxDepth) }
    }

    override fun clickNode(nodeId: String): Boolean = onMain {
        withAccessibility { selector.click(nodeId) }
    }

    override fun setNodeText(nodeId: String, text: String): Boolean = onMain {
        withAccessibility { selector.setText(nodeId, text) }
    }

    override fun captureScreen(format: String, quality: Int): PythonHostScreenImageDescriptor =
        screenImages.capture(format, quality)

    override fun findScreenColor(
        color: Int,
        threshold: Int,
        region: PythonHostImageRegion?,
    ): PythonHostColorMatch = PythonHostAccessibilityScreenCapture.useCapturedBitmap(
        service = requireAccessibilityService(),
    ) { bitmap ->
        PythonHostColorSearch.find(bitmap, color, threshold, region)
    }

    override fun beginImageTemplate(
        format: String,
        byteLength: Int,
        sha256: String,
    ): PythonHostImageTemplateDescriptor = imageTemplates.begin(format, byteLength, sha256)

    override fun writeImageTemplateChunk(
        templateId: String,
        offset: Int,
        data: String,
    ): PythonHostImageTemplateChunkAck = imageTemplates.writeChunk(templateId, offset, data)

    override fun findScreenImage(
        templateId: String,
        threshold: Int,
        region: PythonHostImageRegion?,
    ): PythonHostImageMatch = PythonHostAccessibilityScreenCapture.useCapturedBitmap(
        service = requireAccessibilityService(),
    ) { bitmap ->
        imageTemplates.find(
            templateId = templateId,
            source = object : PythonHostPixelSource {
                override val width = bitmap.width
                override val height = bitmap.height

                override fun readRow(x: Int, y: Int, width: Int, destination: IntArray) {
                    bitmap.getPixels(destination, 0, width, x, y, width, 1)
                }
            },
            threshold = threshold,
            region = region,
        )
    }

    override fun recognizeImageText(templateId: String): List<String> = PythonHostOcr.recognize(
        context = appContext,
        image = imageTemplates.copyDecoded(templateId),
    )

    override fun releaseImageTemplate(templateId: String) = imageTemplates.release(templateId)

    override fun readScreenImageChunk(imageId: String, offset: Int): PythonHostScreenImageChunk =
        screenImages.readChunk(imageId, offset)

    override fun releaseScreenImage(imageId: String) = screenImages.release(imageId)

    override fun close() {
        try {
            selector.close()
        } finally {
            try {
                screenImages.close()
            } finally {
                imageTemplates.close()
            }
        }
    }

    private fun ensureNotificationPostingAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw PythonHostCapabilityPermissionDeniedException()
        }
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            throw PythonHostCapabilityPermissionDeniedException()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = appContext.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(PythonHostNoticePolicy.CHANNEL_ID)
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) {
                throw PythonHostCapabilityPermissionDeniedException()
            }
        }
    }

    private fun dialogController(): PythonInteractiveInputController = interactiveInputController
        ?: throw PythonHostCapabilityInteractiveNotAllowedException()

    private fun <T> withAccessibility(action: () -> T): T {
        requireAccessibilityService()
        return action()
    }

    private fun requireAccessibilityService(): AutoJsAccessibilityService {
        if (!AutoJsAccessibilityService.registry.isOperational) {
            throw PythonHostCapabilityAccessibilityUnavailableException()
        }
        return AutoJsAccessibilityService.instance
            ?: throw PythonHostCapabilityAccessibilityUnavailableException()
    }

    private fun <T> onMain(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val task = FutureTask(Callable { action() })
        check(mainHandler.post(task)) { "AutoJs6 main dispatcher rejected a Python host capability" }
        return try {
            task.get(PythonRuntimeContract.HOST_CAPABILITY_CALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            task.cancel(true)
            throw PythonHostCapabilityTimeoutException(error)
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }
    }
}

/**
 * Execution-scoped pure-data dispatcher. Binder identity checks remain in [PythonHostCapabilityBroker].
 */
internal class PythonHostCapabilityDispatcher(
    private val executionId: String,
    grantedCapabilities: Collection<String>,
    private val actions: PythonHostCapabilityActions,
    private val maxCalls: Int = PythonRuntimeContract.MAX_HOST_CAPABILITY_CALLS,
) {
    private val grantedCapabilities = grantedCapabilities.toSet()
    private val admittedCallIds = linkedSetOf<Long>()
    private var closed = false

    init {
        require(executionId.isNotBlank()) { "Python host capability execution ID is blank" }
        require(this.grantedCapabilities.all { it in PythonHostCapabilities.ENABLED }) {
            "Python host capability grant contains an unsupported capability"
        }
        require(maxCalls in 1..PythonRuntimeContract.MAX_HOST_CAPABILITY_CALLS) {
            "Python host capability call quota is invalid"
        }
    }

    fun dispatch(encodedRequest: ByteArray): ByteArray {
        require(encodedRequest.size in 1..PythonRuntimeContract.MAX_HOST_CAPABILITY_REQUEST_BYTES) {
            "Python host capability request exceeds the byte limit"
        }
        val request = decodeRequest(encodedRequest)
        require(request.executionId == executionId) {
            "Python host capability request execution ID does not match"
        }
        admit(request)?.let { error -> return encodeFailure(request, error.first, error.second) }
        if (request.capability !in grantedCapabilities) {
            return encodeFailure(request, ERROR_CAPABILITY_UNAVAILABLE, "Host capability was not granted")
        }
        val response = try {
            when (request.capability) {
                PythonHostCapabilities.TOAST_SHOW -> {
                    actions.showToast(request.singleTextArgument(ARG_TEXT))
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.CLIP_GET -> {
                    request.requireArguments()
                    encodeSuccess(request, actions.getClipboardText())
                }
                PythonHostCapabilities.CLIP_SET -> {
                    actions.setClipboardText(request.singleTextArgument(ARG_TEXT))
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.APP_LAUNCH -> encodeSuccess(
                    request,
                    actions.launchPackage(request.singleTextArgument(ARG_PACKAGE, PACKAGE_NAME)),
                )
                PythonHostCapabilities.APP_LAUNCH_APP -> encodeSuccess(
                    request,
                    actions.launchApp(request.singleTextArgument(ARG_NAME)),
                )
                PythonHostCapabilities.APP_OPEN_URL -> encodeSuccess(
                    request,
                    actions.openUrl(request.singleTextArgument(ARG_URL, WEB_URL)),
                )
                PythonHostCapabilities.DEVICE_INFO -> {
                    request.requireArguments()
                    encodeSuccess(request, actions.getDeviceInfo())
                }
                PythonHostCapabilities.CONSOLE_LOG -> {
                    actions.writeConsole(
                        PythonHostConsoleLevel.LOG,
                        request.singleTextArgument(ARG_TEXT),
                    )
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.CONSOLE_WARN -> {
                    actions.writeConsole(
                        PythonHostConsoleLevel.WARN,
                        request.singleTextArgument(ARG_TEXT),
                    )
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.CONSOLE_ERROR -> {
                    actions.writeConsole(
                        PythonHostConsoleLevel.ERROR,
                        request.singleTextArgument(ARG_TEXT),
                    )
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.NOTICE_SHOW -> {
                    actions.postNotice(
                        request.singleTextArgument(
                            ARG_TEXT,
                            maxBytes = PythonHostNoticePolicy.MAX_TEXT_BYTES,
                            allowEmpty = false,
                        ),
                    )
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.FILES_READ_TEXT -> encodeSuccess(
                    request,
                    actions.readFileText(request.singleFilePathArgument()),
                )
                PythonHostCapabilities.FILES_WRITE_TEXT -> {
                    val path = request.filePathArgument(ARG_TEXT)
                    val text = request.textArgument(
                        ARG_TEXT,
                        maxBytes = PythonHostFilePolicy.MAX_TEXT_BYTES,
                    )
                    actions.writeFileText(path, text)
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.FILES_EXISTS -> encodeSuccess(
                    request,
                    actions.fileExists(request.singleFilePathArgument()),
                )
                PythonHostCapabilities.FILES_IS_FILE -> encodeSuccess(
                    request,
                    actions.isFile(request.singleFilePathArgument()),
                )
                PythonHostCapabilities.FILES_IS_DIR -> encodeSuccess(
                    request,
                    actions.isDirectory(request.singleFilePathArgument()),
                )
                PythonHostCapabilities.FILES_LIST -> encodeSuccess(
                    request,
                    actions.listDirectory(request.singleFilePathArgument()),
                )
                PythonHostCapabilities.DIALOGS_ALERT -> {
                    request.requireArguments(ARG_TITLE, ARG_TEXT)
                    actions.showDialogAlert(
                        request.dialogTitleArgument(),
                        request.dialogTextArgument(),
                    )
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.DIALOGS_CONFIRM -> {
                    request.requireArguments(ARG_TITLE, ARG_TEXT)
                    encodeSuccess(
                        request,
                        actions.showDialogConfirm(
                            request.dialogTitleArgument(),
                            request.dialogTextArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.DIALOGS_PROMPT -> {
                    request.requireArguments(ARG_TITLE, ARG_TEXT, ARG_DEFAULT)
                    encodeSuccess(
                        request,
                        actions.showDialogPrompt(
                            request.dialogTitleArgument(),
                            request.dialogTextArgument(),
                            request.textArgument(
                                ARG_DEFAULT,
                                maxBytes = PythonHostDialogPolicy.MAX_PROMPT_REPLY_BYTES,
                            ),
                        ),
                    )
                }
                PythonHostCapabilities.DIALOGS_SELECT -> {
                    request.requireArguments(ARG_TITLE, ARG_ITEMS)
                    encodeSuccess(
                        request,
                        actions.showDialogSelect(
                            request.dialogTitleArgument(),
                            request.dialogItemsArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.ENGINES_CURRENT -> {
                    request.requireArguments()
                    encodeSuccess(request, actions.currentEngine())
                }
                PythonHostCapabilities.ENGINES_STOP_SELF -> {
                    request.requireArguments()
                    actions.requestStopSelf()
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.ENGINES_RUN -> encodeSuccess(
                    request,
                    actions.runScript(request.singleFilePathArgument()),
                )
                PythonHostCapabilities.AUTOMATOR_CLICK -> {
                    request.requireArguments(ARG_X, ARG_Y)
                    encodeSuccess(
                        request,
                        actions.click(
                            request.coordinateArgument(ARG_X),
                            request.coordinateArgument(ARG_Y),
                        ),
                    )
                }
                PythonHostCapabilities.AUTOMATOR_LONG_CLICK -> {
                    request.requireArguments(ARG_X, ARG_Y)
                    encodeSuccess(
                        request,
                        actions.longClick(
                            request.coordinateArgument(ARG_X),
                            request.coordinateArgument(ARG_Y),
                        ),
                    )
                }
                PythonHostCapabilities.AUTOMATOR_PRESS -> {
                    request.requireArguments(ARG_X, ARG_Y, ARG_DURATION_MILLIS)
                    encodeSuccess(
                        request,
                        actions.press(
                            request.coordinateArgument(ARG_X),
                            request.coordinateArgument(ARG_Y),
                            request.durationArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.AUTOMATOR_SWIPE -> {
                    request.requireArguments(ARG_X1, ARG_Y1, ARG_X2, ARG_Y2, ARG_DURATION_MILLIS)
                    encodeSuccess(
                        request,
                        actions.swipe(
                            request.coordinateArgument(ARG_X1),
                            request.coordinateArgument(ARG_Y1),
                            request.coordinateArgument(ARG_X2),
                            request.coordinateArgument(ARG_Y2),
                            request.durationArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.AUTOMATOR_BACK -> {
                    request.requireArguments()
                    encodeSuccess(request, actions.back())
                }
                PythonHostCapabilities.AUTOMATOR_HOME -> {
                    request.requireArguments()
                    encodeSuccess(request, actions.home())
                }
                PythonHostCapabilities.SELECTOR_SNAPSHOT -> {
                    request.requireArguments(ARG_MAX_NODES, ARG_MAX_DEPTH)
                    encodeSuccess(
                        request,
                        actions.selectorSnapshot(
                            request.snapshotNodeLimitArgument(),
                            request.selectorDepthArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.SELECTOR_FIND -> {
                    request.requireArguments(ARG_QUERY, ARG_MAX_NODES, ARG_MAX_DEPTH)
                    encodeSuccess(
                        request,
                        actions.selectorFind(
                            request.selectorQueryArgument(),
                            request.findNodeLimitArgument(),
                            request.selectorDepthArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.SELECTOR_CLICK -> encodeSuccess(
                    request,
                    actions.clickNode(request.singleNodeIdArgument()),
                )
                PythonHostCapabilities.SELECTOR_SET_TEXT -> {
                    request.requireArguments(ARG_NODE_ID, ARG_TEXT)
                    encodeSuccess(
                        request,
                        actions.setNodeText(
                            request.nodeIdArgument(),
                            request.textArgument(
                                ARG_TEXT,
                                maxBytes = PythonHostSelectorPolicy.MAX_SET_TEXT_BYTES,
                            ),
                        ),
                    )
                }
                PythonHostCapabilities.IMAGES_CAPTURE_SCREEN -> {
                    request.requireArguments(ARG_FORMAT, ARG_QUALITY)
                    encodeSuccess(
                        request,
                        actions.captureScreen(
                            request.imageFormatArgument(),
                            request.imageQualityArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.IMAGES_FIND_COLOR -> {
                    request.requireArguments(ARG_COLOR, ARG_THRESHOLD, ARG_REGION)
                    encodeSuccess(
                        request,
                        actions.findScreenColor(
                            request.imageColorArgument(),
                            request.imageColorThresholdArgument(),
                            request.imageRegionArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.IMAGES_BEGIN_TEMPLATE -> {
                    request.requireArguments(ARG_FORMAT, ARG_BYTE_LENGTH, ARG_SHA256)
                    encodeSuccess(
                        request,
                        actions.beginImageTemplate(
                            request.imageFormatArgument(),
                            request.templateByteLengthArgument(),
                            request.templateSha256Argument(),
                        ),
                    )
                }
                PythonHostCapabilities.IMAGES_WRITE_TEMPLATE_CHUNK -> {
                    request.requireArguments(ARG_TEMPLATE_ID, ARG_OFFSET, ARG_DATA)
                    encodeSuccess(
                        request,
                        actions.writeImageTemplateChunk(
                            request.templateIdArgument(),
                            request.templateOffsetArgument(),
                            request.templateChunkArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.IMAGES_FIND_IMAGE -> {
                    request.requireArguments(ARG_TEMPLATE_ID, ARG_THRESHOLD, ARG_REGION)
                    encodeSuccess(
                        request,
                        actions.findScreenImage(
                            request.templateIdArgument(),
                            request.imageColorThresholdArgument(),
                            request.imageRegionArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.OCR_RECOGNIZE -> encodeSuccess(
                    request,
                    actions.recognizeImageText(request.singleTemplateIdArgument()),
                )
                PythonHostCapabilities.IMAGES_RELEASE_TEMPLATE -> {
                    actions.releaseImageTemplate(request.singleTemplateIdArgument())
                    encodeSuccess(request, null)
                }
                PythonHostCapabilities.IMAGES_READ_CHUNK -> {
                    request.requireArguments(ARG_IMAGE_ID, ARG_OFFSET)
                    encodeSuccess(
                        request,
                        actions.readScreenImageChunk(
                            request.imageIdArgument(),
                            request.imageOffsetArgument(),
                        ),
                    )
                }
                PythonHostCapabilities.IMAGES_RELEASE -> {
                    actions.releaseScreenImage(request.singleImageIdArgument())
                    encodeSuccess(request, null)
                }
                else -> encodeFailure(
                    request,
                    ERROR_CAPABILITY_UNAVAILABLE,
                    "Host capability is unavailable",
                )
            }
        } catch (_: PythonHostCapabilityTimeoutException) {
            encodeFailure(request, ERROR_HOST_TIMEOUT, "Host capability timed out")
        } catch (_: PythonHostCapabilityPermissionDeniedException) {
            encodeFailure(request, ERROR_PERMISSION_DENIED, "Host capability permission was denied")
        } catch (_: SecurityException) {
            encodeFailure(request, ERROR_PERMISSION_DENIED, "Host capability permission was denied")
        } catch (_: PythonHostCapabilityPathNotFoundException) {
            encodeFailure(request, ERROR_PATH_NOT_FOUND, "Host file path does not exist")
        } catch (_: PythonHostCapabilityPathTypeException) {
            encodeFailure(request, ERROR_PATH_TYPE_MISMATCH, "Host file path has the wrong type")
        } catch (_: PythonHostCapabilityFileEncodingException) {
            encodeFailure(request, ERROR_FILE_ENCODING, "Host file is not valid UTF-8")
        } catch (_: PythonHostCapabilityResultLimitException) {
            encodeFailure(request, ERROR_RESULT_LIMIT_EXCEEDED, "Host capability result exceeds its limit")
        } catch (_: PythonHostCapabilityInteractiveNotAllowedException) {
            encodeFailure(
                request,
                ERROR_INTERACTIVE_NOT_ALLOWED,
                "Host dialog requires a foreground user-authorized execution",
            )
        } catch (_: PythonHostCapabilityNestedPythonException) {
            encodeFailure(
                request,
                ERROR_NESTED_PYTHON_NOT_ALLOWED,
                "Nested Python Runtime executions are unavailable while the current session is active",
            )
        } catch (_: PythonHostCapabilityEngineLaunchLimitException) {
            encodeFailure(
                request,
                ERROR_ENGINE_LAUNCH_LIMIT_EXCEEDED,
                "Python child engine launch quota is exhausted",
            )
        } catch (_: PythonHostCapabilityAccessibilityUnavailableException) {
            encodeFailure(
                request,
                ERROR_ACCESSIBILITY_UNAVAILABLE,
                "AutoJs6 accessibility service is unavailable",
            )
        } catch (_: PythonHostCapabilityStaleNodeException) {
            encodeFailure(
                request,
                ERROR_STALE_NODE,
                "Host selector node is stale or no longer retained",
            )
        } catch (_: PythonHostCapabilitySelectorScanLimitException) {
            encodeFailure(
                request,
                ERROR_SELECTOR_SCAN_LIMIT_EXCEEDED,
                "Host selector scan reached its node or depth limit",
            )
        } catch (_: PythonHostCapabilityScreenCaptureUnavailableException) {
            encodeFailure(
                request,
                ERROR_SCREEN_CAPTURE_UNAVAILABLE,
                "Accessibility screenshot capture requires Android 11 or newer",
            )
        } catch (_: PythonHostCapabilityScreenCaptureFailedException) {
            encodeFailure(
                request,
                ERROR_SCREEN_CAPTURE_FAILED,
                "Accessibility screenshot capture failed",
            )
        } catch (_: PythonHostCapabilityStaleImageException) {
            encodeFailure(
                request,
                ERROR_STALE_IMAGE,
                "Host screen image is stale or no longer retained",
            )
        } catch (_: PythonHostCapabilityInvalidImageTemplateException) {
            encodeFailure(
                request,
                ERROR_INVALID_IMAGE_TEMPLATE,
                "Image template is invalid or unsupported",
            )
        } catch (_: PythonHostCapabilityStaleTemplateException) {
            encodeFailure(
                request,
                ERROR_STALE_TEMPLATE,
                "Host image template is stale or no longer retained",
            )
        } catch (_: PythonHostCapabilityOcrUnavailableException) {
            encodeFailure(
                request,
                ERROR_OCR_UNAVAILABLE,
                "No enabled, authorized, and compatible Host OCR engine is available",
            )
        } catch (_: PythonHostCapabilityOcrFailedException) {
            encodeFailure(request, ERROR_OCR_FAILED, "Host OCR recognition failed")
        } catch (_: IllegalArgumentException) {
            encodeFailure(request, ERROR_INVALID_ARGUMENT, "Host capability arguments are invalid")
        } catch (_: Throwable) {
            encodeFailure(request, ERROR_HOST_FAILURE, "Host capability failed")
        }
        if (response.size > PythonRuntimeContract.MAX_HOST_CAPABILITY_RESPONSE_BYTES) {
            return encodeFailure(
                request,
                ERROR_RESULT_LIMIT_EXCEEDED,
                "Host capability result exceeds the byte limit",
            )
        }
        return response
    }

    @Synchronized
    fun close(): Boolean {
        if (closed) return false
        closed = true
        admittedCallIds.clear()
        runCatching(actions::close)
        return true
    }

    @Synchronized
    private fun admit(request: Request): Pair<String, String>? {
        if (closed) return ERROR_BROKER_CLOSED to "Host capability broker is closed"
        if (request.callId in admittedCallIds) {
            return ERROR_REPLAYED_CALL to "Host capability call ID was already used"
        }
        if (admittedCallIds.size >= maxCalls) {
            return ERROR_CALL_LIMIT_EXCEEDED to "Host capability call quota is exhausted"
        }
        admittedCallIds += request.callId
        return null
    }

    private fun decodeRequest(encoded: ByteArray): Request {
        val text = strictUtf8(encoded)
        val document = runCatching { JsonParser.parseString(text) }
            .getOrElse { throw IllegalArgumentException("Python host capability request is malformed", it) }
            .takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: throw IllegalArgumentException("Python host capability request is not an object")
        require(document.keySet() == REQUEST_KEYS) {
            "Python host capability request has missing or unexpected fields"
        }
        require(document.requireLong(KEY_VERSION) == JSON_VERSION.toLong()) {
            "Python host capability JSON version is unsupported"
        }
        val requestExecutionId = document.requireString(KEY_EXECUTION_ID)
        val callId = document.requireLong(KEY_CALL_ID)
        require(callId > 0L) { "Python host capability call ID must be positive" }
        val capability = document.requireString(KEY_CAPABILITY)
        require(capability.isNotBlank()) { "Python host capability name is blank" }
        val arguments = document.get(KEY_ARGUMENTS)
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?: throw IllegalArgumentException("Python host capability arguments must be an object")
        return Request(requestExecutionId, callId, capability, arguments)
    }

    private fun encodeSuccess(request: Request, value: Any?): ByteArray = encodeResponse(
        JsonObject().apply {
            addProperty(KEY_VERSION, JSON_VERSION)
            addProperty(KEY_EXECUTION_ID, request.executionId)
            addProperty(KEY_CALL_ID, request.callId)
            addProperty(KEY_OK, true)
            when (value) {
                null -> add(KEY_VALUE, JsonNull.INSTANCE)
                is String -> addProperty(KEY_VALUE, value)
                is Boolean -> addProperty(KEY_VALUE, value)
                is Int -> addProperty(KEY_VALUE, value)
                is PythonHostDeviceInfo -> add(KEY_VALUE, value.toJson())
                is PythonHostEngineInfo -> add(KEY_VALUE, value.toJson())
                is PythonHostEngineLaunch -> add(KEY_VALUE, value.toJson())
                is PythonHostSelectorSnapshot -> add(KEY_VALUE, value.toJson())
                is PythonHostSelectorNode -> add(KEY_VALUE, value.toJson())
                is PythonHostScreenImageDescriptor -> add(KEY_VALUE, value.toJson())
                is PythonHostScreenImageChunk -> add(KEY_VALUE, value.toJson())
                is PythonHostColorMatch -> add(KEY_VALUE, value.toJson())
                is PythonHostImageTemplateDescriptor -> add(KEY_VALUE, value.toJson())
                is PythonHostImageTemplateChunkAck -> add(KEY_VALUE, value.toJson())
                is PythonHostImageMatch -> add(KEY_VALUE, value.toJson())
                is List<*> -> add(
                    KEY_VALUE,
                    JsonArray().apply {
                        value.forEach { entry -> add(requireNotNull(entry) as String) }
                    },
                )
                else -> error("Unsupported Python host capability result type: ${value::class.java.name}")
            }
        },
    )

    private fun encodeFailure(request: Request, code: String, message: String): ByteArray = encodeResponse(
        JsonObject().apply {
            addProperty(KEY_VERSION, JSON_VERSION)
            addProperty(KEY_EXECUTION_ID, request.executionId)
            addProperty(KEY_CALL_ID, request.callId)
            addProperty(KEY_OK, false)
            add(
                KEY_ERROR,
                JsonObject().apply {
                    addProperty(KEY_ERROR_CODE, code)
                    addProperty(KEY_ERROR_MESSAGE, message)
                },
            )
        },
    )

    private fun encodeResponse(document: JsonObject): ByteArray =
        document.toString().toByteArray(StandardCharsets.UTF_8)

    private fun strictUtf8(value: ByteArray): String = runCatching {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(value))
            .toString()
    }.getOrElse { throw IllegalArgumentException("Python host capability request is not UTF-8", it) }

    private data class Request(
        val executionId: String,
        val callId: Long,
        val capability: String,
        val arguments: JsonObject,
    ) {
        fun requireArguments(vararg expected: String) {
            require(arguments.keySet() == expected.toSet()) {
                "Python host capability arguments have missing or unexpected fields"
            }
        }

        fun singleTextArgument(
            name: String,
            pattern: Regex? = null,
            maxBytes: Int = PythonRuntimeContract.MAX_HOST_CAPABILITY_TEXT_BYTES,
            allowEmpty: Boolean = true,
        ): String {
            requireArguments(name)
            return textArgument(name, pattern, maxBytes, allowEmpty)
        }

        fun textArgument(
            name: String,
            pattern: Regex? = null,
            maxBytes: Int = PythonRuntimeContract.MAX_HOST_CAPABILITY_TEXT_BYTES,
            allowEmpty: Boolean = true,
        ): String {
            val value = arguments.requireString(name)
            require(value.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) {
                "Python host capability text argument exceeds the byte limit"
            }
            require(allowEmpty || value.isNotEmpty()) {
                "Python host capability text argument is empty"
            }
            require(pattern == null || pattern.matches(value)) {
                "Python host capability text argument has an invalid shape"
            }
            return value
        }

        fun singleFilePathArgument(): String {
            requireArguments(ARG_PATH)
            return filePathArgument()
        }

        fun filePathArgument(vararg additionalArguments: String): String {
            requireArguments(ARG_PATH, *additionalArguments)
            return PythonHostFilePolicy.normalizeRelative(
                textArgument(
                    ARG_PATH,
                    maxBytes = PythonHostFilePolicy.MAX_PATH_BYTES,
                    allowEmpty = false,
                ),
            )
        }

        fun dialogTitleArgument(): String = textArgument(
            ARG_TITLE,
            maxBytes = PythonHostDialogPolicy.MAX_TITLE_BYTES,
            allowEmpty = false,
        )

        fun dialogTextArgument(): String = textArgument(
            ARG_TEXT,
            maxBytes = PythonHostDialogPolicy.MAX_TEXT_BYTES,
        )

        fun dialogItemsArgument(): List<String> {
            val array = arguments.get(ARG_ITEMS)
                ?.takeIf(JsonElement::isJsonArray)
                ?.asJsonArray
                ?: throw IllegalArgumentException("Python dialog items must be an array")
            require(array.size() in 1..PythonHostDialogPolicy.MAX_ITEMS) {
                "Python dialog item count exceeds its limit"
            }
            var totalBytes = 0
            return array.map { element ->
                require(element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                    "Python dialog item must be text"
                }
                val value = element.asString
                val size = value.toByteArray(StandardCharsets.UTF_8).size
                require(value.isNotEmpty() && size <= PythonHostDialogPolicy.MAX_ITEM_BYTES) {
                    "Python dialog item is empty or exceeds its byte limit"
                }
                totalBytes += size
                require(totalBytes <= PythonHostDialogPolicy.MAX_TOTAL_ITEM_BYTES) {
                    "Python dialog items exceed their aggregate byte limit"
                }
                value
            }
        }

        fun coordinateArgument(name: String): Int = integerArgument(
            name = name,
            minimum = 0,
            maximum = PythonHostAutomatorPolicy.MAX_COORDINATE,
        )

        fun durationArgument(): Int = integerArgument(
            name = ARG_DURATION_MILLIS,
            minimum = 1,
            maximum = PythonHostAutomatorPolicy.MAX_DURATION_MILLIS,
        )

        fun snapshotNodeLimitArgument(): Int = integerArgument(
            name = ARG_MAX_NODES,
            minimum = 1,
            maximum = PythonHostSelectorPolicy.MAX_SNAPSHOT_NODES,
        )

        fun findNodeLimitArgument(): Int = integerArgument(
            name = ARG_MAX_NODES,
            minimum = 1,
            maximum = PythonHostSelectorPolicy.MAX_FIND_NODES,
        )

        fun selectorDepthArgument(): Int = integerArgument(
            name = ARG_MAX_DEPTH,
            minimum = 0,
            maximum = PythonHostSelectorPolicy.MAX_DEPTH,
        )

        fun singleNodeIdArgument(): String {
            requireArguments(ARG_NODE_ID)
            return nodeIdArgument()
        }

        fun nodeIdArgument(): String = textArgument(
            ARG_NODE_ID,
            pattern = PythonHostSelectorPolicy.NODE_ID,
            maxBytes = 64,
            allowEmpty = false,
        )

        fun imageFormatArgument(): String = textArgument(
            ARG_FORMAT,
            maxBytes = 8,
            allowEmpty = false,
        ).also { format ->
            require(format in PythonHostImagePolicy.FORMATS) {
                "Python Host image format is unsupported"
            }
        }

        fun imageQualityArgument(): Int = integerArgument(
            name = ARG_QUALITY,
            minimum = PythonHostImagePolicy.MIN_QUALITY,
            maximum = PythonHostImagePolicy.MAX_QUALITY,
        )

        fun imageColorArgument(): Int = integerArgument(
            name = ARG_COLOR,
            minimum = 0,
            maximum = PythonHostImagePolicy.MAX_COLOR,
        )

        fun imageColorThresholdArgument(): Int = integerArgument(
            name = ARG_THRESHOLD,
            minimum = 0,
            maximum = PythonHostImagePolicy.MAX_COLOR_THRESHOLD,
        )

        fun imageRegionArgument(): PythonHostImageRegion? {
            val value = arguments.get(ARG_REGION)
                ?: throw IllegalArgumentException("Python Host image region is missing")
            if (value.isJsonNull) return null
            val region = value.takeIf(JsonElement::isJsonObject)?.asJsonObject
                ?: throw IllegalArgumentException("Python Host image region must be an object or null")
            require(region.keySet() == IMAGE_REGION_KEYS) {
                "Python Host image region has missing or unexpected fields"
            }
            fun coordinate(name: String, maximum: Int): Int {
                val encoded = region.requireLong(name)
                require(encoded in 0L..maximum.toLong()) {
                    "Python Host image region field '$name' is outside its allowed range"
                }
                return encoded.toInt()
            }
            return PythonHostImageRegion(
                x = coordinate(REGION_X, PythonHostImagePolicy.MAX_DIMENSION - 1),
                y = coordinate(REGION_Y, PythonHostImagePolicy.MAX_DIMENSION - 1),
                width = coordinate(REGION_WIDTH, PythonHostImagePolicy.MAX_DIMENSION).also {
                    require(it > 0) { "Python Host image region width must be positive" }
                },
                height = coordinate(REGION_HEIGHT, PythonHostImagePolicy.MAX_DIMENSION).also {
                    require(it > 0) { "Python Host image region height must be positive" }
                },
            )
        }

        fun singleImageIdArgument(): String {
            requireArguments(ARG_IMAGE_ID)
            return imageIdArgument()
        }

        fun imageIdArgument(): String = textArgument(
            ARG_IMAGE_ID,
            pattern = PythonHostImagePolicy.IMAGE_ID,
            maxBytes = PythonHostImagePolicy.MAX_IMAGE_ID_BYTES,
            allowEmpty = false,
        )

        fun imageOffsetArgument(): Int = integerArgument(
            name = ARG_OFFSET,
            minimum = 0,
            maximum = PythonHostImagePolicy.MAX_IMAGE_BYTES - 1,
        )

        fun templateByteLengthArgument(): Int = integerArgument(
            name = ARG_BYTE_LENGTH,
            minimum = 1,
            maximum = PythonHostImagePolicy.MAX_TEMPLATE_BYTES,
        )

        fun templateSha256Argument(): String = textArgument(
            ARG_SHA256,
            pattern = PythonHostImagePolicy.SHA256,
            maxBytes = 64,
            allowEmpty = false,
        )

        fun singleTemplateIdArgument(): String {
            requireArguments(ARG_TEMPLATE_ID)
            return templateIdArgument()
        }

        fun templateIdArgument(): String = textArgument(
            ARG_TEMPLATE_ID,
            pattern = PythonHostImagePolicy.TEMPLATE_ID,
            maxBytes = PythonHostImagePolicy.MAX_TEMPLATE_ID_BYTES,
            allowEmpty = false,
        )

        fun templateOffsetArgument(): Int = integerArgument(
            name = ARG_OFFSET,
            minimum = 0,
            maximum = PythonHostImagePolicy.MAX_TEMPLATE_BYTES - 1,
        )

        fun templateChunkArgument(): String = textArgument(
            ARG_DATA,
            maxBytes = PythonHostImagePolicy.MAX_TEMPLATE_CHUNK_BASE64_BYTES,
            allowEmpty = false,
        )

        fun selectorQueryArgument(): PythonHostSelectorQuery {
            val query = arguments.get(ARG_QUERY)
                ?.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?: throw IllegalArgumentException("Python selector query must be an object")
            require(query.keySet().isNotEmpty() && query.keySet().all { it in PythonHostSelectorPolicy.QUERY_KEYS }) {
                "Python selector query contains missing or unsupported conditions"
            }
            fun text(name: String): String? {
                if (!query.has(name)) return null
                val value = query.requireString(name)
                require(
                    value.isNotEmpty() &&
                        value.toByteArray(StandardCharsets.UTF_8).size <=
                        PythonHostSelectorPolicy.MAX_QUERY_TEXT_BYTES,
                ) {
                    "Python selector query text is empty or exceeds its byte limit"
                }
                return value
            }
            fun flag(name: String): Boolean? = if (query.has(name)) query.requireBoolean(name) else null
            return PythonHostSelectorQuery(
                text = text(QUERY_TEXT),
                textContains = text(QUERY_TEXT_CONTAINS),
                description = text(QUERY_DESCRIPTION),
                descriptionContains = text(QUERY_DESCRIPTION_CONTAINS),
                resourceId = text(QUERY_RESOURCE_ID),
                className = text(QUERY_CLASS_NAME),
                clickable = flag(QUERY_CLICKABLE),
                editable = flag(QUERY_EDITABLE),
                enabled = flag(QUERY_ENABLED),
                scrollable = flag(QUERY_SCROLLABLE),
            )
        }

        private fun integerArgument(name: String, minimum: Int, maximum: Int): Int {
            val value = arguments.requireLong(name)
            require(value in minimum.toLong()..maximum.toLong()) {
                "Python host capability integer argument is outside its allowed range"
            }
            return value.toInt()
        }
    }

    private companion object {
        const val JSON_VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_EXECUTION_ID = "executionId"
        const val KEY_CALL_ID = "callId"
        const val KEY_CAPABILITY = "capability"
        const val KEY_ARGUMENTS = "arguments"
        const val KEY_OK = "ok"
        const val KEY_VALUE = "value"
        const val KEY_ERROR = "error"
        const val KEY_ERROR_CODE = "code"
        const val KEY_ERROR_MESSAGE = "message"
        const val ARG_TEXT = "text"
        const val ARG_PACKAGE = "package"
        const val ARG_NAME = "name"
        const val ARG_URL = "url"
        const val ARG_PATH = "path"
        const val ARG_TITLE = "title"
        const val ARG_DEFAULT = "default"
        const val ARG_ITEMS = "items"
        const val ARG_X = "x"
        const val ARG_Y = "y"
        const val ARG_X1 = "x1"
        const val ARG_Y1 = "y1"
        const val ARG_X2 = "x2"
        const val ARG_Y2 = "y2"
        const val ARG_DURATION_MILLIS = "durationMillis"
        const val ARG_QUERY = "query"
        const val ARG_MAX_NODES = "maxNodes"
        const val ARG_MAX_DEPTH = "maxDepth"
        const val ARG_NODE_ID = "nodeId"
        const val ARG_FORMAT = "format"
        const val ARG_QUALITY = "quality"
        const val ARG_COLOR = "color"
        const val ARG_THRESHOLD = "threshold"
        const val ARG_REGION = "region"
        const val ARG_IMAGE_ID = "imageId"
        const val ARG_TEMPLATE_ID = "templateId"
        const val ARG_BYTE_LENGTH = "byteLength"
        const val ARG_SHA256 = "sha256"
        const val ARG_DATA = "data"
        const val ARG_OFFSET = "offset"
        const val REGION_X = "x"
        const val REGION_Y = "y"
        const val REGION_WIDTH = "width"
        const val REGION_HEIGHT = "height"
        const val QUERY_TEXT = "text"
        const val QUERY_TEXT_CONTAINS = "textContains"
        const val QUERY_DESCRIPTION = "description"
        const val QUERY_DESCRIPTION_CONTAINS = "descriptionContains"
        const val QUERY_RESOURCE_ID = "resourceId"
        const val QUERY_CLASS_NAME = "className"
        const val QUERY_CLICKABLE = "clickable"
        const val QUERY_EDITABLE = "editable"
        const val QUERY_ENABLED = "enabled"
        const val QUERY_SCROLLABLE = "scrollable"
        const val ERROR_CAPABILITY_UNAVAILABLE = "CAPABILITY_UNAVAILABLE"
        const val ERROR_INVALID_ARGUMENT = "INVALID_ARGUMENT"
        const val ERROR_HOST_FAILURE = "HOST_FAILURE"
        const val ERROR_HOST_TIMEOUT = "HOST_TIMEOUT"
        const val ERROR_PERMISSION_DENIED = "PERMISSION_DENIED"
        const val ERROR_BROKER_CLOSED = "BROKER_CLOSED"
        const val ERROR_REPLAYED_CALL = "REPLAYED_CALL"
        const val ERROR_CALL_LIMIT_EXCEEDED = "CALL_LIMIT_EXCEEDED"
        const val ERROR_RESULT_LIMIT_EXCEEDED = "RESULT_LIMIT_EXCEEDED"
        const val ERROR_PATH_NOT_FOUND = "PATH_NOT_FOUND"
        const val ERROR_PATH_TYPE_MISMATCH = "PATH_TYPE_MISMATCH"
        const val ERROR_FILE_ENCODING = "FILE_ENCODING_ERROR"
        const val ERROR_INTERACTIVE_NOT_ALLOWED = "INTERACTIVE_NOT_ALLOWED"
        const val ERROR_NESTED_PYTHON_NOT_ALLOWED = "NESTED_PYTHON_NOT_ALLOWED"
        const val ERROR_ENGINE_LAUNCH_LIMIT_EXCEEDED = "ENGINE_LAUNCH_LIMIT_EXCEEDED"
        const val ERROR_ACCESSIBILITY_UNAVAILABLE = "ACCESSIBILITY_UNAVAILABLE"
        const val ERROR_STALE_NODE = "STALE_NODE"
        const val ERROR_SELECTOR_SCAN_LIMIT_EXCEEDED = "SELECTOR_SCAN_LIMIT_EXCEEDED"
        const val ERROR_SCREEN_CAPTURE_UNAVAILABLE = "SCREEN_CAPTURE_UNAVAILABLE"
        const val ERROR_SCREEN_CAPTURE_FAILED = "SCREEN_CAPTURE_FAILED"
        const val ERROR_STALE_IMAGE = "STALE_IMAGE"
        const val ERROR_INVALID_IMAGE_TEMPLATE = "INVALID_IMAGE_TEMPLATE"
        const val ERROR_STALE_TEMPLATE = "STALE_TEMPLATE"
        const val ERROR_OCR_UNAVAILABLE = "OCR_UNAVAILABLE"
        const val ERROR_OCR_FAILED = "OCR_FAILED"
        val REQUEST_KEYS = setOf(KEY_VERSION, KEY_EXECUTION_ID, KEY_CALL_ID, KEY_CAPABILITY, KEY_ARGUMENTS)
        val IMAGE_REGION_KEYS = setOf(REGION_X, REGION_Y, REGION_WIDTH, REGION_HEIGHT)
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
        val WEB_URL = Regex("https?://.+", RegexOption.IGNORE_CASE)
    }
}

/**
 * The host capability broker of one execution. The plugin gets [binder], which forwards here until [close] and then lets go of
 * this broker, its dispatcher and the execution its violation callback reaches: the plugin process keeps that Binder until it
 * collects its proxy (docs/memory-phase2/ROADMAP.md C42).
 * zh-CN: 一次执行的宿主能力 broker. 插件拿到的是 [binder], 它在 [close] 之前转发到这里, 之后放开本 broker, 其分发器与违规回调所及的执行:
 * 插件进程在回收代理之前一直持有该 Binder (docs/memory-phase2/ROADMAP.md C42).
 */
internal class PythonHostCapabilityBroker(
    context: Context,
    executionId: String,
    private val expectedPluginUid: Int,
    fileRoot: File,
    source: PythonFileSource,
    engineId: Int,
    engineStartedAtMillis: Long,
    stopSelf: () -> Unit,
    globalConsole: GlobalConsole,
    interactiveInputController: PythonInteractiveInputController?,
    private val onViolation: (Throwable) -> Unit,
    actions: PythonHostCapabilityActions = AndroidPythonHostCapabilityActions(
        context,
        executionId,
        fileRoot,
        source,
        engineId,
        engineStartedAtMillis,
        stopSelf,
        globalConsole,
        interactiveInputController,
    ),
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val exported = ExportedHostCapabilityBroker(this)
    private val dispatcher = PythonHostCapabilityDispatcher(
        executionId = executionId,
        grantedCapabilities = PythonHostCapabilities.ENABLED,
        actions = actions,
    )

    val binder: IPythonHostCapabilityBroker
        get() = exported

    fun dispatch(request: ByteArray?): ByteArray {
        val callingUid = Binder.getCallingUid()
        if (callingUid != expectedPluginUid) {
            val error = SecurityException(
                "Python host capability caller UID $callingUid differs from pinned UID $expectedPluginUid",
            )
            close()
            onViolation(error)
            throw error
        }
        val identity = Binder.clearCallingIdentity()
        return try {
            dispatcher.dispatch(requireNotNull(request) { "Python host capability request is null" })
        } catch (error: Throwable) {
            close()
            onViolation(error)
            throw error
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    override fun close() {
        exported.clear()
        if (closed.compareAndSet(false, true)) dispatcher.close()
    }
}

/**
 * Forwards to [PythonHostCapabilityBroker] until cleared; afterwards a call fails, which the plugin reports to the script as an
 * unavailable broker.
 * zh-CN: 清空之前转发到 [PythonHostCapabilityBroker]; 之后的调用失败, 插件将其作为 broker 不可用报告给脚本.
 */
private class ExportedHostCapabilityBroker(broker: PythonHostCapabilityBroker) : IPythonHostCapabilityBroker.Stub() {

    private val target = AtomicReference<PythonHostCapabilityBroker?>(broker)

    fun clear() {
        target.set(null)
    }

    override fun dispatch(request: ByteArray?): ByteArray =
        target.get()?.dispatch(request) ?: throw IllegalStateException("Python host capability broker is closed")
}

private class PythonHostCapabilityTimeoutException(cause: Throwable) : RuntimeException(cause)

internal class PythonHostCapabilityPermissionDeniedException : RuntimeException()

internal class PythonHostCapabilityPathNotFoundException : RuntimeException()

internal class PythonHostCapabilityPathTypeException : RuntimeException()

internal class PythonHostCapabilityResultLimitException(
    cause: Throwable? = null,
) : RuntimeException(cause)

internal class PythonHostCapabilityFileEncodingException(cause: Throwable) : RuntimeException(cause)

internal class PythonHostCapabilityInteractiveNotAllowedException : RuntimeException()

internal class PythonHostCapabilityNestedPythonException : RuntimeException()

internal class PythonHostCapabilityEngineLaunchLimitException : RuntimeException()

internal class PythonHostCapabilityAccessibilityUnavailableException : RuntimeException()

internal class PythonHostCapabilityStaleNodeException : RuntimeException()

internal class PythonHostCapabilitySelectorScanLimitException : RuntimeException()

private fun JsonObject.requireString(name: String): String {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
        "Python host capability field '$name' must be a string"
    }
    return value.asString
}

private fun JsonObject.requireLong(name: String): Long {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
        "Python host capability field '$name' must be an integer"
    }
    val encoded = value.asJsonPrimitive.asString
    require(INTEGER.matches(encoded)) { "Python host capability field '$name' must be an integer" }
    return encoded.toLongOrNull()
        ?: throw IllegalArgumentException("Python host capability field '$name' is outside the integer range")
}

private fun JsonObject.requireBoolean(name: String): Boolean {
    val value = get(name)
    require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
        "Python host capability field '$name' must be a boolean"
    }
    return value.asBoolean
}

private val INTEGER = Regex("-?(?:0|[1-9][0-9]*)")
