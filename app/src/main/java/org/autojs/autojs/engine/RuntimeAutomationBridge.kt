package org.autojs.autojs.engine

import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityTool
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.State
import org.autojs.autojs.core.accessibility.window.WindowMatch
import org.autojs.autojs.core.accessibility.window.WindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootProvider
import org.autojs.autojs.core.automator.GlobalActionAutomator
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.action.ActionFactory
import org.autojs.autojs.core.automator.action.SimpleAction
import org.autojs.autojs.core.plugin.yolo.YoloPluginHost
import org.autojs.autojs.core.plugin.yolo.YoloSessionOpenException
import org.autojs.plugin.yolo.api.YoloContract
import org.autojs.plugin.yolo.api.YoloDevice
import org.autojs.plugin.yolo.api.YoloErrorCode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Host side of Rhino's automation family that the JS runtimes reach through the `automator`, `auto` and `yolo` bridge modules
 * (api-coverage P3.4): the global actions, point taps and presses, multi-stroke gestures, the text / bounds / editable targets
 * and scrolls of Rhino's automator (the same [GlobalActionAutomator] and [ActionFactory] actions), the accessibility service
 * management and window queries of `auto`, and YOLO detector sessions of `yolo.load` ([YoloPluginHost], one session per handle,
 * closed with the execution). Window and node results are JSON snapshots; UiObject / window members stay with the
 * java-prototype-objects rule. Capabilities: [capabilities] (Q178 (a): service management also needs `accessibility.service`,
 * point presses and gestures `accessibility.gesture`, the global actions other than back / home / recents `keys`, YOLO `yolo`).
 *
 * zh-CN: JS 运行时经 automator, auto 与 yolo 桥模块访问的 Rhino 自动化族的宿主侧 (api-coverage P3.4): Rhino automator 的全局动作, 点按与按压, 多笔画手势,
 * 文本 / 范围 / 可编辑框目标与滚动 (同一 [GlobalActionAutomator] 与 [ActionFactory] 动作), auto 的无障碍服务管理与窗口查询, 以及 yolo.load 的 YOLO 检测会话
 * ([YoloPluginHost], 每个句柄一个会话, 随执行关闭). 窗口与节点结果为 JSON 快照; UiObject / 窗口对象的成员仍按 java-prototype-objects 规则. 所需能力见
 * [capabilities] (Q178 (a): 服务管理另需 accessibility.service, 点按压与手势需 accessibility.gesture, back / home / recents 以外的全局动作需 keys, YOLO 需 yolo).
 */
internal class RuntimeAutomationBridge(
    private val context: Context,
    private val resolveFile: (String) -> File?,
    private val bitmapOf: (String) -> Bitmap,
) {

    private val actions by lazy { GlobalActionAutomator(context, null) { AccessibilityService.instance } }

    private val detectors = ConcurrentHashMap<String, YoloPluginHost.Session>()

    fun invoke(module: String, method: String, args: JSONArray): Any? = when (module) {
        AUTOMATOR -> automator(method, args)
        AUTO -> auto(method, args)
        YOLO -> yolo(method, args)
        else -> throw IllegalArgumentException("Unknown automation module: $module")
    }

    /** Closes the YOLO sessions of the execution (script end). zh-CN: 关闭本次执行的 YOLO 会话 (脚本结束时). */
    fun close() {
        detectors.values.forEach { runCatching { it.close() } }
        detectors.clear()
    }

    private fun automator(method: String, args: JSONArray): Any? = when (method) {
        "globalAction" -> globalAction(args.getString(0))
        "tap" -> {
            val (x, y) = point(args, 0)
            val duration = when (val kind = args.optString(2, "click")) {
                "click" -> ViewConfiguration.getTapTimeout() * 5L / 4
                "longClick" -> ViewConfiguration.getLongPressTimeout() * 5L / 4
                else -> throw IllegalArgumentException("Unknown tap kind: $kind")
            }
            requireService()
            actions.gesture(0, duration, intArrayOf(x, y))
        }
        "press" -> {
            val (x, y) = point(args, 0)
            requireService()
            actions.gesture(0, duration(args.getLong(2)), intArrayOf(x, y))
        }
        "gestures" -> {
            val strokes = strokes(args.getJSONArray(0))
            requireService()
            if (args.optBoolean(1)) {
                actions.gesturesAsync(strokes)
                true
            } else actions.gestures(*strokes)
        }
        "targetAction" -> perform(targetAction(args.getString(0), args.getJSONObject(1)))
        "editText" -> {
            val action = if (args.getString(0) == "append") UiObject.ACTION_APPEND_TEXT else AccessibilityNodeInfo.ACTION_SET_TEXT
            perform(ActionFactory.createActionWithEditableFilter(action, args.getInt(1), args.getString(2)))
        }
        "scroll" -> {
            val action = if (args.getString(0) == "up") AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            val index = args.getInt(1)
            perform(if (index < 0) ActionFactory.createScrollMaxAction(action) else ActionFactory.createScrollAction(action, index))
        }
        "switchToInputMethod", "switchToInputMethodWithId" -> switchInputMethod(method, args.getString(0))
        "isServiceRunning" -> AccessibilityTool.controller.isRunning()
        "ensureService" -> {
            val result = AccessibilityTool.controller.ensure(State.CONNECTED, launchSettingsIfNeeded = false)
            if (result is org.autojs.autojs.core.accessibility.control.A11yServiceController.EnsureResult.Failed) {
                throw IllegalStateException(context.getString(AccessibilityTool.messageResOf(result.reason)))
            }
            null
        }
        else -> throw IllegalArgumentException("Unknown automator method: $method")
    }

    private fun auto(method: String, args: JSONArray): Any? = when (method) {
        "start" -> AccessibilityTool(context).startService(false)
        "stop" -> AccessibilityTool(context).stopService(false)
        "launchSettings" -> AccessibilityTool(context).launchSettings().let { null }
        "clearCache" -> AccessibilityTool(context).clearCache()
        "state" -> JSONObject()
            .put("hasInstance", AccessibilityTool.controller.hasInstance())
            .put("hasService", AccessibilityTool.controller.hasService())
            .put("isRunning", AccessibilityTool.controller.isRunning())
            .put("isOperational", AccessibilityTool.controller.isOperational())
            .put("adoptedByEvent", AccessibilityService.registry.adoptedByEvent)
        "service" -> AccessibilityService.instance?.serviceInfo?.let { info ->
            JSONObject().put("id", info.id).put("eventTypes", info.eventTypes).put("feedbackType", info.feedbackType)
                .put("flags", info.flags).put("notificationTimeout", info.notificationTimeout)
                .put("capabilities", info.capabilities).put("packageNames", info.packageNames?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
        }
        "services" -> JSONArray(AccessibilityTool(context).getServices().toList())
        "windows" -> JSONArray(provider.windows().map(::windowJson))
        "windowRoot" -> provider.windows().firstOrNull { it.id == args.getInt(0) }?.let { window -> runCatching { window.root }.getOrNull()?.let(::nodeJson) }
        "roots" -> {
            val policy = policy(args)
            val ids = args.optJSONArray(2)?.let { array -> (0 until array.length()).map(array::getInt).toSet() }
            val roots = if (ids == null) provider.roots(policy) else provider.windows().filter { it.id in ids }.mapNotNull { runCatching { it.root }.getOrNull() }
            JSONArray(roots.map(::nodeJson))
        }
        "activeRoot" -> provider.activeRoot(policy(args))?.let(::nodeJson)
        else -> throw IllegalArgumentException("Unknown auto method: $method")
    }

    private fun yolo(method: String, args: JSONArray): Any? = when (method) {
        "load" -> {
            val options = args.optJSONObject(1) ?: JSONObject()
            val component = options.optString("component").trim().takeIf { it.isNotEmpty() }
                ?: throw CodedException(COMPONENT_REQUIRED, "yolo.load requires options.component; provider fallback is disabled")
            val name = ComponentName.unflattenFromString(component)
                ?: throw CodedException(INVALID_ARGUMENT, "options.component must use the form package/class")
            val artifacts = modelArtifacts(args.optString(0))
            val openOptions = openOptions(options)
            val target = runCatching { runBlocking { YoloPluginHost.requireExactTarget(context, name) } }.getOrElse { error ->
                throw CodedException(PROVIDER_UNAVAILABLE, error.message ?: "the explicit YOLO provider is unavailable")
            }
            val session = runCatching { runBlocking { YoloPluginHost.openSession(context, target, artifacts, openOptions) } }.getOrElse { error ->
                val code = when ((error as? YoloSessionOpenException)?.code) {
                    YoloErrorCode.MODEL_REJECTED -> MODEL_REJECTED
                    YoloErrorCode.UNSUPPORTED_CAPABILITY, YoloErrorCode.UNSUPPORTED_PROTOCOL -> UNSUPPORTED_CAPABILITY
                    else -> SESSION_OPEN_FAILED
                }
                throw CodedException(code, error.message ?: "the YOLO session could not be opened")
            }
            val id = "yolo-${UUID.randomUUID()}"
            detectors[id] = session
            JSONObject().put("id", id).put("component", session.component.flattenToString())
        }
        "detect" -> {
            val session = detectors[args.getString(0)]?.takeUnless { it.isClosed }
                ?: throw CodedException(SESSION_CLOSED, "the YOLO detector is closed; call yolo.load again")
            val bitmap = runCatching { bitmapOf(args.getString(1)) }.getOrElse { error ->
                throw CodedException(INVALID_ARGUMENT, "detector.detect image is unavailable or recycled: ${error.message}")
            }
            val result = runCatching { runBlocking { session.detect(bitmap, detectOptions(args.optJSONObject(2) ?: JSONObject())) } }.getOrElse { error ->
                if (session.isClosed) throw CodedException(SESSION_CLOSED, error.message ?: "the YOLO detector session is closed")
                throw CodedException(DETECT_FAILED, error.message ?: "YOLO detection failed")
            }
            if (result.imageWidth != bitmap.width || result.imageHeight != bitmap.height) {
                throw CodedException(DETECT_FAILED, "provider result image dimensions do not match the input image")
            }
            JSONArray(result.detections.map { detection ->
                JSONObject().put("classId", detection.classId).put("label", detection.label).put("confidence", detection.confidence)
                    .put("bounds", JSONObject().put("left", detection.bounds.left).put("top", detection.bounds.top)
                        .put("right", detection.bounds.right).put("bottom", detection.bounds.bottom))
            })
        }
        "close" -> detectors.remove(args.getString(0))?.let { runCatching { it.close() }; true } ?: false
        else -> throw IllegalArgumentException("Unknown yolo method: $method")
    }

    private val provider get() = WindowRootProvider.default

    private fun requireService() {
        if (AccessibilityService.instance == null || !AccessibilityService.registry.isOperational) {
            throw IllegalStateException("an enabled accessibility capability provider in the current process is required")
        }
    }

    private fun globalAction(name: String): Boolean {
        requireService()
        return when (name) {
            "back" -> AccessibilityService.instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true
            "home" -> AccessibilityService.instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
            "recents" -> AccessibilityService.instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) == true
            "notifications" -> actions.notifications()
            "quickSettings" -> actions.quickSettings()
            "powerDialog" -> actions.powerDialog()
            "splitScreen" -> actions.splitScreen()
            "lockScreen" -> actions.lockScreen()
            "takeScreenshot" -> actions.takeScreenshot()
            "headsetHook" -> actions.headsethook()
            "accessibilityButton" -> actions.accessibilityButton()
            "accessibilityButtonChooser" -> actions.accessibilityButtonChooser()
            "accessibilityShortcut" -> actions.accessibilityShortcut()
            "accessibilityAllApps" -> actions.accessibilityAllApps()
            "dismissNotificationShade" -> actions.dismissNotificationShade()
            else -> throw IllegalArgumentException("Unknown global action: $name")
        }
    }

    /** As Rhino's performAction: the action on every window root, true when all of them report success. */
    private fun perform(action: SimpleAction): Boolean {
        requireService()
        val roots = provider.roots(WindowRootPolicy())
        return roots.isNotEmpty() && roots.map { root -> action.perform(UiObject.createRoot(root)) }.all { it }
    }

    private fun targetAction(name: String, target: JSONObject): SimpleAction {
        val action = when (name) {
            "click" -> AccessibilityNodeInfoCompat.ACTION_CLICK
            "longClick" -> AccessibilityNodeInfo.ACTION_LONG_CLICK
            "scrollUp" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "scrollDown" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> throw IllegalArgumentException("Unknown target action: $name")
        }
        target.optJSONArray("bounds")?.let { b ->
            return ActionFactory.createActionWithBoundsFilter(action, Rect(b.getInt(0), b.getInt(1), b.getInt(2), b.getInt(3)))
        }
        return ActionFactory.createActionWithTextFilter(action, target.getString("text"), target.optInt("index", -1))
    }

    private fun switchInputMethod(method: String, value: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw UnsupportedOperationException("automator.$method requires Android 11 (API 30) or higher")
        requireService()
        val id = if (method == "switchToInputMethodWithId") value else {
            val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            manager.enabledInputMethodList.firstOrNull { it.packageName == value }?.id ?: return false
        }
        return AccessibilityService.instance?.softKeyboardController?.switchToInputMethod(id) == true
    }

    private fun point(args: JSONArray, index: Int): Pair<Int, Int> {
        val x = args.getInt(index)
        val y = args.getInt(index + 1)
        require(x >= 0 && y >= 0) { "Coordinates must not be negative: ($x, $y)" }
        return x to y
    }

    private fun duration(value: Long): Long {
        require(value in 1..MAX_GESTURE_MILLIS) { "A gesture duration must be in 1..$MAX_GESTURE_MILLIS ms, got $value" }
        return value
    }

    /** Strokes as `[start, duration, [[x, y], ...]]`, the shape of Rhino's automator.gestures after its argument forms. */
    private fun strokes(array: JSONArray): Array<GestureDescription.StrokeDescription> {
        require(array.length() in 1..GestureDescription.getMaxStrokeCount()) { "gestures takes 1..${GestureDescription.getMaxStrokeCount()} strokes" }
        return Array(array.length()) { i ->
            val stroke = array.getJSONArray(i)
            val start = stroke.getLong(0)
            val duration = duration(stroke.getLong(1))
            require(start >= 0 && start + duration <= MAX_GESTURE_MILLIS) { "Stroke $i must end within $MAX_GESTURE_MILLIS ms" }
            val points = stroke.getJSONArray(2)
            require(points.length() > 0) { "Stroke $i has no points" }
            val path = Path()
            for (p in 0 until points.length()) {
                val point = points.getJSONArray(p)
                val x = point.getInt(0).toFloat()
                val y = point.getInt(1).toFloat()
                require(x >= 0 && y >= 0) { "Coordinates must not be negative: ($x, $y)" }
                if (p == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            GestureDescription.StrokeDescription(path, start, duration)
        }
    }

    /** `[mode, appWindowsFallback]` of the facade's auto.setMode / setFlags (Rhino's per-script window root policy). */
    private fun policy(args: JSONArray): WindowRootPolicy<AccessibilityWindowInfo> = WindowRootPolicy(
        mode = if (args.optString(0) == "fast") WindowRootPolicy.Mode.FAST else WindowRootPolicy.Mode.NORMAL,
        fallback = if (args.optBoolean(1)) WindowRootPolicy.Fallback.APPLICATION_WINDOWS else WindowRootPolicy.Fallback.NONE,
    )

    private fun nodeJson(node: AccessibilityNodeInfo): JSONObject = RuntimeFlowQuery.snapshot(UiObject.createRoot(node))

    private fun windowJson(window: AccessibilityWindowInfo): JSONObject {
        val bounds = Rect().also(window::getBoundsInScreen)
        val root = runCatching { window.root }.getOrNull()
        return JSONObject().put("id", window.id)
            .put("type", WindowMatch.TYPES.entries.firstOrNull { it.value == window.type }?.key ?: window.type.toString())
            .put("layer", window.layer).put("title", window.title?.toString() ?: JSONObject.NULL)
            .put("active", window.isActive).put("focused", window.isFocused).put("accessibilityFocused", window.isAccessibilityFocused)
            .put("displayId", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.displayId else 0)
            .put("packageName", root?.packageName?.toString() ?: JSONObject.NULL)
            .put("bounds", JSONObject().put("left", bounds.left).put("top", bounds.top).put("right", bounds.right).put("bottom", bounds.bottom))
    }

    private fun modelArtifacts(path: String): List<YoloPluginHost.ModelArtifactFile> {
        if (path.isBlank()) throw CodedException(MODEL_INVALID, "yolo.load modelDir must not be blank")
        val directory = resolveFile(path)?.canonicalFile ?: throw CodedException(MODEL_INVALID, "modelDir could not be resolved")
        if (!directory.isDirectory) throw CodedException(MODEL_INVALID, "modelDir is not a readable directory: ${directory.path}")
        return listOf("manifest" to "model.json", "ncnn-param" to "model.ncnn.param", "ncnn-bin" to "model.ncnn.bin").map { (role, name) ->
            val file = File(directory, name).canonicalFile
            if (file.parentFile != directory || !file.isFile || !file.canRead()) throw CodedException(MODEL_INVALID, "modelDir must contain a readable $name")
            YoloPluginHost.ModelArtifactFile(role = role, logicalName = name, file = file)
        }
    }

    private fun openOptions(options: JSONObject): YoloPluginHost.OpenSessionOptions {
        val device = options.optString("device", "cpu").trim().lowercase()
        if (device != "cpu") throw CodedException(UNSUPPORTED_CAPABILITY, "the current YOLO provider profile supports options.device=\"cpu\" only")
        val threads = options.optInt("threads", YoloPluginHost.DEFAULT_CPU_THREADS)
        val decoderId = options.optString("decoderId", YoloPluginHost.DEFAULT_DECODER_ID).trim()
        val timeout = options.optLong("timeoutMillis", YoloPluginHost.DEFAULT_OPEN_TIMEOUT_MILLIS)
        if (threads !in 1..YoloContract.MAX_CPU_THREADS) throw CodedException(INVALID_ARGUMENT, "options.threads must be in 1..${YoloContract.MAX_CPU_THREADS}")
        if (decoderId.isBlank()) throw CodedException(INVALID_ARGUMENT, "options.decoderId must not be blank")
        if (timeout !in 1..YoloContract.MAX_TIMEOUT_MILLIS) throw CodedException(INVALID_ARGUMENT, "options.timeoutMillis must be in 1..${YoloContract.MAX_TIMEOUT_MILLIS}")
        return YoloPluginHost.OpenSessionOptions(device = YoloDevice.CPU, cpuThreads = threads, decoderId = decoderId, timeoutMillis = timeout)
    }

    private fun detectOptions(options: JSONObject): YoloPluginHost.DetectOptions {
        val confidence = options.optDouble("confidence", YoloPluginHost.DEFAULT_CONFIDENCE_THRESHOLD)
        val iou = options.optDouble("iouThreshold", YoloPluginHost.DEFAULT_IOU_THRESHOLD)
        val max = options.optInt("maxDetections", YoloPluginHost.DEFAULT_MAX_DETECTIONS)
        val timeout = options.optLong("timeoutMillis", YoloContract.DEFAULT_TIMEOUT_MILLIS)
        if (!confidence.isFinite() || confidence !in 0.0..1.0) throw CodedException(INVALID_ARGUMENT, "options.confidence must be finite and in [0, 1]")
        if (!iou.isFinite() || iou !in 0.0..1.0) throw CodedException(INVALID_ARGUMENT, "options.iouThreshold must be finite and in [0, 1]")
        if (max !in 1..YoloContract.MAX_DETECTIONS) throw CodedException(INVALID_ARGUMENT, "options.maxDetections must be in 1..${YoloContract.MAX_DETECTIONS}")
        if (timeout !in 1..YoloContract.MAX_TIMEOUT_MILLIS) throw CodedException(INVALID_ARGUMENT, "options.timeoutMillis must be in 1..${YoloContract.MAX_TIMEOUT_MILLIS}")
        return YoloPluginHost.DetectOptions(confidenceThreshold = confidence, iouThreshold = iou, maxDetections = max, timeoutMillis = timeout)
    }

    /** A failure carrying Rhino's YOLO error code; the bridge message starts with the code, which the facades copy to `error.code`. */
    class CodedException(code: String, message: String) : IllegalStateException("$code: $message")

    companion object {

        const val AUTOMATOR = "automator"
        const val AUTO = "auto"
        const val YOLO = "yolo"

        private const val MAX_GESTURE_MILLIS = 60_000L

        private const val COMPONENT_REQUIRED = "YOLO_COMPONENT_REQUIRED"
        private const val INVALID_ARGUMENT = "YOLO_INVALID_ARGUMENT"
        private const val MODEL_INVALID = "YOLO_MODEL_INVALID"
        private const val MODEL_REJECTED = "YOLO_MODEL_REJECTED"
        private const val UNSUPPORTED_CAPABILITY = "YOLO_UNSUPPORTED_CAPABILITY"
        private const val PROVIDER_UNAVAILABLE = "YOLO_PROVIDER_UNAVAILABLE"
        private const val SESSION_OPEN_FAILED = "YOLO_SESSION_OPEN_FAILED"
        private const val DETECT_FAILED = "YOLO_DETECT_FAILED"
        private const val SESSION_CLOSED = "YOLO_SESSION_CLOSED"

        private val SERVICE_MANAGEMENT = setOf("start", "stop", "launchSettings", "clearCache")
        private val ACCESSIBILITY_ACTIONS = setOf("back", "home", "recents")

        val methodsByModule: Map<String, Set<String>> = mapOf(
            AUTOMATOR to setOf(
                "globalAction", "tap", "press", "gestures", "targetAction", "editText", "scroll",
                "switchToInputMethod", "switchToInputMethodWithId", "isServiceRunning", "ensureService",
            ),
            AUTO to setOf("start", "stop", "launchSettings", "clearCache", "state", "service", "services", "windows", "windowRoot", "roots", "activeRoot"),
            YOLO to setOf("load", "detect", "close"),
        )

        /**
         * Q178 (a): `accessibility` for every automator / auto call, plus `accessibility.service` for the service management,
         * `accessibility.gesture` for presses and gestures and `keys` for the global actions other than back / home / recents
         * (as the keys module); YOLO needs `yolo` (and `image` to read the detected image).
         */
        fun capabilities(module: String, method: String, argsJson: String): List<String> = when (module) {
            AUTOMATOR -> when (method) {
                "press", "gestures" -> listOf(NodeBridgePermissionManifest.ACCESSIBILITY, NodeBridgePermissionManifest.ACCESSIBILITY_GESTURE)
                "globalAction" -> {
                    val name = runCatching { JSONArray(argsJson).optString(0) }.getOrDefault("")
                    if (name in ACCESSIBILITY_ACTIONS) listOf(NodeBridgePermissionManifest.ACCESSIBILITY) else listOf(NodeBridgePermissionManifest.KEYS)
                }
                else -> listOf(NodeBridgePermissionManifest.ACCESSIBILITY)
            }
            AUTO -> if (method in SERVICE_MANAGEMENT) listOf(NodeBridgePermissionManifest.ACCESSIBILITY, NodeBridgePermissionManifest.ACCESSIBILITY_SERVICE)
            else listOf(NodeBridgePermissionManifest.ACCESSIBILITY)
            YOLO -> if (method == "close") listOf(NodeBridgePermissionManifest.YOLO) else listOf(NodeBridgePermissionManifest.YOLO, NodeBridgePermissionManifest.IMAGE)
            else -> emptyList()
        }
    }
}
