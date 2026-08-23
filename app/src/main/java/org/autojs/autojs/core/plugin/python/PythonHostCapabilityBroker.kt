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
import org.autojs.autojs.core.console.GlobalConsole
import org.autojs.autojs.runtime.api.AppUtils
import org.autojs.autojs.runtime.api.ScriptToast
import org.autojs.autojs.script.PythonFileSource
import org.autojs.autojs.script.PythonProjectSource
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
    )
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
}

internal class AndroidPythonHostCapabilityActions(
    context: Context,
    executionId: String,
    fileRoot: File,
    private val globalConsole: GlobalConsole,
) : PythonHostCapabilityActions {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val app = AppUtils(appContext)
    private val hostFiles = PythonHostFileScope(fileRoot)
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
        NotificationManagerCompat.from(appContext).notify(
            notificationTag,
            PythonHostNoticePolicy.NOTIFICATION_ID,
            notification,
        )
    }

    override fun readFileText(path: String): String = hostFiles.readText(path)

    override fun writeFileText(path: String, text: String) = hostFiles.writeText(path, text)

    override fun fileExists(path: String): Boolean = hostFiles.exists(path)

    override fun isFile(path: String): Boolean = hostFiles.isFile(path)

    override fun isDirectory(path: String): Boolean = hostFiles.isDirectory(path)

    override fun listDirectory(path: String): List<String> = hostFiles.list(path)

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
                is PythonHostDeviceInfo -> add(KEY_VALUE, value.toJson())
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
        val REQUEST_KEYS = setOf(KEY_VERSION, KEY_EXECUTION_ID, KEY_CALL_ID, KEY_CAPABILITY, KEY_ARGUMENTS)
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
        val WEB_URL = Regex("https?://.+", RegexOption.IGNORE_CASE)
    }
}

internal class PythonHostCapabilityBroker(
    context: Context,
    executionId: String,
    private val expectedPluginUid: Int,
    fileRoot: File,
    globalConsole: GlobalConsole,
    private val onViolation: (Throwable) -> Unit,
    actions: PythonHostCapabilityActions = AndroidPythonHostCapabilityActions(
        context,
        executionId,
        fileRoot,
        globalConsole,
    ),
) : IPythonHostCapabilityBroker.Stub(), AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val dispatcher = PythonHostCapabilityDispatcher(
        executionId = executionId,
        grantedCapabilities = PythonHostCapabilities.ENABLED,
        actions = actions,
    )

    override fun dispatch(request: ByteArray?): ByteArray {
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
        if (closed.compareAndSet(false, true)) dispatcher.close()
    }
}

private class PythonHostCapabilityTimeoutException(cause: Throwable) : RuntimeException(cause)

internal class PythonHostCapabilityPermissionDeniedException : RuntimeException()

internal class PythonHostCapabilityPathNotFoundException : RuntimeException()

internal class PythonHostCapabilityPathTypeException : RuntimeException()

internal class PythonHostCapabilityResultLimitException : RuntimeException()

internal class PythonHostCapabilityFileEncodingException(cause: Throwable) : RuntimeException(cause)

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

private val INTEGER = Regex("-?(?:0|[1-9][0-9]*)")
