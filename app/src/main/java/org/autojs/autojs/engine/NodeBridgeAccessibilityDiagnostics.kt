package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.A11yScreenshotter
import org.autojs.autojs.core.accessibility.AccessibilityService as AutoJsAccessibilityService
import org.autojs.autojs.core.accessibility.node.NodeLifecycle
import org.autojs.autojs.core.accessibility.node.default
import org.autojs.autojs.core.accessibility.node.release
import org.autojs.autojs.core.accessibility.window.A11yWindowFacts
import org.autojs.autojs.core.accessibility.window.A11yWindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootPolicy
import org.autojs.autojs.core.accessibility.window.WindowRootProvider
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.bridge.BridgeAccessibilityLimits
import org.autojs.autojs.core.automator.bridge.BridgeBounds
import org.autojs.autojs.core.automator.bridge.BridgeDumpEntry
import org.autojs.autojs.core.automator.bridge.BridgeDumpOptions
import org.autojs.autojs.core.automator.bridge.BridgeNode
import org.autojs.autojs.core.automator.bridge.BridgeNodeDump
import org.autojs.autojs.core.automator.diagnostics.DumpFormat
import org.autojs.autojs.core.automator.diagnostics.DumpOptions
import org.autojs.autojs.core.automator.diagnostics.NodeDump
import org.autojs.autojs.core.automator.diagnostics.SelectorExplainer
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The diagnostics methods the MCP roadmap (P1.3) adds to the Node Bridge's `accessibility` and
 * `app` modules: `accessibility.dump(options)`, `accessibility.explain(selector, options)`,
 * `accessibility.screenshot(options)` and `app.currentWindow()`. Every result is a JSON object
 * with a `schema` field; the screenshot is written to a one-shot file under the host cache
 * (`payload.kind = "file"`, handed to Binder clients as a descriptor by the MCP broker) or, with
 * `savePath`, into the scoped working directory (`payload.kind = "workspace"`).
 *
 * zh-CN: MCP 路线图 (P1.3) 为 Node Bridge 的 `accessibility` 与 `app` 模块新增的诊断方法: `accessibility.dump(options)`,
 * `accessibility.explain(selector, options)`, `accessibility.screenshot(options)` 与 `app.currentWindow()`. 结果均为带 `schema`
 * 字段的 JSON 对象; 截图写入宿主 cache 下的一次性文件 (`payload.kind = "file"`, MCP 代理以描述符交给 Binder 客户端), 或在给出
 * `savePath` 时写入受限的工作目录 (`payload.kind = "workspace"`).
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
internal class NodeBridgeAccessibilityDiagnostics(context: Context, private val engineCwd: String) {

    private val appContext = context.applicationContext

    private val encoder: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoJs6BridgeScreenshot").apply { isDaemon = true }
    }

    fun dump(request: NodeBridgeRequest): NodeBridgeResponse {
        val parsed = runCatching { DumpArgs.parse(argsOf(request).optJSONObject(0) ?: JSONObject()) }
            .getOrElse { return invalid(request, it) }
        val roots = collectRoots(parsed.window)
        try {
            val result = JSONObject()
                .put("schema", SCHEMA_DUMP)
                .put("format", parsed.format.key)
                .put("window", parsed.window)
                .put("roots", roots.size)
            putCurrentWindow(result)
            if (parsed.format == DumpFormat.JSON) {
                val dump = BridgeNodeDump.dump(roots, BridgeDumpOptions(parsed.maxDepth, parsed.maxNodes, parsed.visibleOnly))
                val nodes = JSONArray()
                dump.entries.forEach { nodes.put(entryJson(it)) }
                result
                    .put("nodeCount", dump.nodes)
                    .put("truncated", dump.truncated)
                    .put("nodes", nodes)
            } else {
                val options = DumpOptions(
                    format = parsed.format,
                    maxDepth = parsed.maxDepth,
                    properties = parsed.properties,
                    visibleOnly = parsed.visibleOnly,
                    maxNodes = parsed.maxNodes,
                )
                val dump = NodeDump.dump(roots, options, asList = roots.size != 1)
                val (text, textTruncated) = capUtf8(dump.text, BridgeAccessibilityLimits.MAX_TEXT_BYTES)
                result
                    .put("nodeCount", dump.nodes)
                    .put("truncated", dump.truncated || textTruncated)
                    .put("textTruncated", textTruncated)
                    .put("text", text)
            }
            return NodeBridgeResponse.success(request.id, result.toString())
        } catch (error: Throwable) {
            return providerFailure(request, error)
        } finally {
            releaseRoots(roots, parsed.window)
        }
    }

    fun explain(request: NodeBridgeRequest): NodeBridgeResponse {
        val args = argsOf(request)
        val selectorJson = args.optJSONObject(0)
            ?: return invalid(request, "accessibility.explain requires a selector descriptor object as the first argument.")
        val parsed = runCatching { ExplainArgs.parse(args.optJSONObject(1) ?: JSONObject()) }
            .getOrElse { return invalid(request, it) }
        val filters = NodeBridgeAccessibilityCodec.selectorOf(selectorJson).toFilters()
        val roots = collectRoots(parsed.window)
        try {
            val explanation = SelectorExplainer.explain(roots, filters, maxNodes = parsed.maxNodes, keep = parsed.keep)
            val text = explanation.render()
            val steps = JSONArray()
            explanation.steps.forEach { step ->
                steps.put(
                    JSONObject()
                        .put("index", step.index)
                        .put("filter", step.filter)
                        .put("matched", step.matched)
                        .put("cumulative", step.cumulative),
                )
            }
            val result = JSONObject()
                .put("schema", SCHEMA_EXPLAIN)
                .put("selector", explanation.selector)
                .put("window", parsed.window)
                .put("roots", explanation.roots)
                .put("nodes", explanation.nodes)
                .put("truncated", explanation.truncated)
                .put("found", explanation.found)
                .put("count", explanation.count)
                .put("failingIndex", explanation.failingIndex)
                .put("failingFilter", explanation.failingFilter ?: JSONObject.NULL)
                .put("steps", steps)
                .put("matches", nodesJson(explanation.matches))
                .put("nearMisses", nodesJson(explanation.nearMisses))
                .put("text", text)
            putCurrentWindow(result)
            releaseNodes(explanation.matches + explanation.nearMisses, roots)
            return NodeBridgeResponse.success(request.id, result.toString())
        } catch (error: Throwable) {
            return providerFailure(request, error)
        } finally {
            releaseRoots(roots, parsed.window)
        }
    }

    fun screenshot(request: NodeBridgeRequest, service: AutoJsAccessibilityService, callback: (NodeBridgeResponse) -> Unit) {
        val parsed = runCatching { ScreenshotArgs.parse(argsOf(request).optJSONObject(0) ?: JSONObject()) }
            .getOrElse { error ->
                callback(invalid(request, error))
                return
            }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(
                NodeBridgeResponse.failure(
                    request.id,
                    screenshotUnavailable(request, "Android API ${Build.VERSION.SDK_INT} has no accessibility screenshot API (API 30+ required)"),
                ),
            )
            return
        }
        takeScreenshot(request, service, parsed, callback)
    }

    fun currentWindow(request: NodeBridgeRequest): NodeBridgeResponse {
        return try {
            val result = JSONObject().put("schema", SCHEMA_WINDOW)
            putCurrentWindow(result)
            val operational = AutoJsAccessibilityService.instance != null && AutoJsAccessibilityService.registry.isOperational
            val windows = JSONArray()
            if (operational) {
                WindowRootProvider.default.windows().forEach { window -> windows.put(windowJson(window)) }
            }
            result
                .put("accessibilityAvailable", operational)
                .put("windows", windows)
            NodeBridgeResponse.success(request.id, result.toString())
        } catch (error: Throwable) {
            providerFailure(request, error)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshot(
        request: NodeBridgeRequest,
        service: AutoJsAccessibilityService,
        parsed: ScreenshotArgs,
        callback: (NodeBridgeResponse) -> Unit,
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        val delivered = AtomicBoolean(false)
        runCatching {
            A11yScreenshotter(service).takeWithRetry { result ->
                if (!delivered.compareAndSet(false, true)) return@takeWithRetry
                when (result) {
                    is A11yScreenshotter.Result.Success -> {
                        runCatching {
                            encoder.execute { callback(encode(request, parsed, result.bitmap, startedAt)) }
                        }.onFailure { error ->
                            runCatching { result.bitmap.recycle() }
                            callback(providerFailure(request, error))
                        }
                    }
                    is A11yScreenshotter.Result.Failure -> callback(
                        NodeBridgeResponse.failure(
                            request.id,
                            screenshotUnavailable(request, "takeScreenshot failed with error code ${result.errorCode}"),
                        ),
                    )
                }
            }
        }.onFailure { error ->
            if (delivered.compareAndSet(false, true)) {
                callback(providerFailure(request, error))
            }
        }
    }

    /** Encoded MediaProjection capture for descriptor clients; the no-argument image method still returns a handle. */
    fun captureScreen(request: NodeBridgeRequest, capture: () -> Bitmap, callback: (NodeBridgeResponse) -> Unit) {
        val parsed = runCatching {
            val args = argsOf(request)
            require(args.length() == 1 && args.opt(0) is JSONObject) { "image.captureScreen requires one screenshot options object" }
            ScreenshotArgs.parse(args.getJSONObject(0)).also {
                require(it.savePath == null) { "image.captureScreen returns a one-shot payload and does not accept savePath" }
            }
        }.getOrElse { callback(invalid(request, it)); return }
        val startedAt = SystemClock.elapsedRealtime()
        encoder.execute {
            val bitmap = try {
                capture()
            } catch (error: SecurityException) {
                callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.permissionDenied(
                    "Screen capture permission is missing or was revoked; request it again on the phone.", request.module, request.method,
                )))
                return@execute
            } catch (error: Exception) {
                callback(providerFailure(request, error))
                return@execute
            }
            callback(encode(request, parsed, bitmap, startedAt, "media_projection"))
        }
    }

    private fun encode(request: NodeBridgeRequest, parsed: ScreenshotArgs, source: Bitmap, startedAt: Long, captureSource: String = "accessibility"): NodeBridgeResponse {
        val created = ArrayList<Bitmap>().apply { add(source) }
        try {
            var bitmap = source
            parsed.region?.let { region ->
                val rect = Rect(
                    region.left.coerceIn(0, bitmap.width),
                    region.top.coerceIn(0, bitmap.height),
                    region.right.coerceIn(0, bitmap.width),
                    region.bottom.coerceIn(0, bitmap.height),
                )
                if (rect.width() <= 0 || rect.height() <= 0) {
                    return invalid(request, "accessibility.screenshot region is empty after clamping to ${bitmap.width}x${bitmap.height}.")
                }
                if (rect.left != 0 || rect.top != 0 || rect.width() != bitmap.width || rect.height() != bitmap.height) {
                    bitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height()).also { created += it }
                }
            }
            val (targetWidth, targetHeight) = targetSize(bitmap.width, bitmap.height, parsed.scale, parsed.maxWidth)
            if (targetWidth != bitmap.width || targetHeight != bitmap.height) {
                bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also { created += it }
            }
            val bytes = ByteArrayOutputStream().use { out ->
                check(bitmap.compress(parsed.compressFormat(), parsed.quality, out)) { "Bitmap.compress failed for ${parsed.format}" }
                out.toByteArray()
            }
            if (bytes.size > BridgeAccessibilityLimits.MAX_SCREENSHOT_ENCODED_BYTES) {
                return NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.resourceLimit(
                        message = "accessibility.screenshot encoded to ${bytes.size} bytes, above the limit of ${BridgeAccessibilityLimits.MAX_SCREENSHOT_ENCODED_BYTES}; lower quality, scale, or maxWidth.",
                        module = request.module,
                        method = request.method,
                    ),
                )
            }
            val oneShot = parsed.savePath == null
            val file = when (val savePath = parsed.savePath) {
                null -> newPayloadFile(parsed.extension)
                else -> resolveScopedFile(savePath)
                    ?: return invalid(request, "accessibility.screenshot savePath must be a relative path inside the scoped working directory.")
            }
            file.parentFile?.let { parent ->
                if (!parent.isDirectory && !parent.mkdirs()) {
                    throw IOException("Unable to create ${parent.path}")
                }
            }
            file.writeBytes(bytes)
            val payload = JSONObject()
                .put("kind", if (oneShot) PAYLOAD_KIND_FILE else PAYLOAD_KIND_WORKSPACE)
                .put("path", file.absolutePath)
                .put("oneShot", oneShot)
                .put("bytes", bytes.size)
                .put("mime", parsed.mime)
            val result = JSONObject()
                .put("schema", SCHEMA_SCREENSHOT)
                .put("source", captureSource)
                .put("width", bitmap.width)
                .put("height", bitmap.height)
                .put("format", parsed.format)
                .put("mime", parsed.mime)
                .put("quality", parsed.quality)
                .put("bytes", bytes.size)
                .put("elapsedMs", SystemClock.elapsedRealtime() - startedAt)
                .put("payload", payload)
            return NodeBridgeResponse.success(request.id, result.toString())
        } catch (error: Throwable) {
            return providerFailure(request, error)
        } finally {
            created.distinct().forEach { bitmap -> runCatching { bitmap.recycle() } }
        }
    }

    private fun targetSize(width: Int, height: Int, scale: Double?, maxWidth: Int?): Pair<Int, Int> {
        val factor = when {
            scale != null -> scale
            maxWidth != null && width > maxWidth -> maxWidth.toDouble() / width
            else -> return width to height
        }
        return max(1, (width * factor).roundToInt()) to max(1, (height * factor).roundToInt())
    }

    private fun newPayloadFile(extension: String): File {
        val dir = File(appContext.cacheDir, PAYLOAD_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Unable to create ${dir.path}")
        }
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { stale ->
            if (stale.isFile && now - stale.lastModified() > PAYLOAD_TTL_MS) {
                stale.delete()
            }
        }
        return File(dir, "screenshot-${UUID.randomUUID()}.$extension")
    }

    private fun resolveScopedFile(rawPath: String): File? {
        if (rawPath.isBlank() || rawPath.indexOf('\u0000') >= 0) return null
        if (File(rawPath).isAbsolute || rawPath.split('/', '\\').any { it == ".." }) return null
        val base = runCatching {
            File(engineCwd.takeIf { it.isNotBlank() } ?: appContext.filesDir.absolutePath).canonicalFile
        }.getOrNull() ?: return null
        val candidate = runCatching { File(base, rawPath).canonicalFile }.getOrNull() ?: return null
        val basePath = base.path.trimEnd(File.separatorChar)
        return candidate.takeIf { it.path.startsWith(basePath + File.separator) }
    }

    private fun collectRoots(window: String): List<UiObject> {
        val provider = WindowRootProvider.default
        val infos = when (window) {
            WINDOW_ALL -> provider.roots(ROOT_NORMAL)
            else -> listOfNotNull(provider.activeRoot(ROOT_NORMAL) ?: provider.activeRoot(ROOT_FAST))
        }
        return infos.map { UiObject.createRoot(it) }
    }

    /**
     * Roots of the window list are fresh objects the caller owns; the active root may be the
     * service's cached fast root and is left alone, as `withBridgeRoot` does.
     * zh-CN: 窗口列表的根节点是调用方拥有的新对象; 活动根节点可能是服务缓存的 fast 根节点, 与 `withBridgeRoot` 一样不去动它.
     */
    private fun releaseRoots(roots: List<UiObject>, window: String) {
        if (window != WINDOW_ALL) return
        roots.forEach { root -> NodeLifecycle.default.release(root) }
    }

    private fun releaseNodes(nodes: List<UiObject>, roots: List<UiObject>) {
        nodes.forEach { node ->
            if (roots.none { it === node }) {
                NodeLifecycle.default.release(node)
            }
        }
    }

    private fun putCurrentWindow(json: JSONObject) {
        val provider = runCatching { AutoJs.instance.infoProvider }.getOrNull()
        json
            .put("packageName", runCatching { provider?.latestPackage }.getOrNull().orEmpty())
            .put("activityName", runCatching { provider?.latestActivity }.getOrNull().orEmpty())
    }

    private fun windowJson(window: AccessibilityWindowInfo): JSONObject {
        val bounds = Rect()
        runCatching { window.getBoundsInScreen(bounds) }
        val type = runCatching { A11yWindowFacts.type(window) }.getOrDefault(-1)
        return JSONObject()
            .put("id", runCatching { A11yWindowFacts.id(window) }.getOrDefault(-1))
            .put("type", type)
            .put("typeName", windowTypeName(type))
            .put("packageName", A11yWindowFacts.packageName(window) ?: JSONObject.NULL)
            .put("title", A11yWindowFacts.title(window) ?: JSONObject.NULL)
            .put("active", runCatching { A11yWindowFacts.isActive(window) }.getOrDefault(false))
            .put("focused", runCatching { A11yWindowFacts.isFocused(window) }.getOrDefault(false))
            .put("layer", runCatching { A11yWindowFacts.layer(window) }.getOrDefault(-1))
            .put("displayId", runCatching { A11yWindowFacts.displayId(window) }.getOrDefault(0))
            .put("bounds", NodeBridgeAccessibilityCodec.boundsJson(BridgeBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)))
    }

    private fun windowTypeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "application"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "input_method"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "accessibility_overlay"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "split_screen_divider"
        TYPE_MAGNIFICATION_OVERLAY -> "magnification_overlay"
        else -> "unknown"
    }

    private fun entryJson(entry: BridgeDumpEntry): JSONObject = NodeBridgeAccessibilityCodec.nodeJson(entry.node)
        .put("root", entry.root)
        .put("depth", entry.depth)
        .put("index", entry.index)
        .put("childCount", entry.childCount)
        .put("visible", entry.visible)
        .put("scrollable", entry.scrollable)
        .put("checkable", entry.checkable)
        .put("checked", entry.checked)
        .put("editable", entry.editable)
        .put("focused", entry.focused)
        .put("selected", entry.selected)
        .put("longClickable", entry.longClickable)

    private fun nodesJson(nodes: List<UiObject>): JSONArray = JSONArray().apply {
        nodes.forEach { node -> put(NodeBridgeAccessibilityCodec.nodeJson(BridgeNode.of(node))) }
    }

    private fun argsOf(request: NodeBridgeRequest): JSONArray {
        return runCatching { JSONArray(request.argsJson) }.getOrDefault(JSONArray())
    }

    private fun invalid(request: NodeBridgeRequest, error: Throwable): NodeBridgeResponse {
        return invalid(request, error.message ?: "${request.module}.${request.method} received invalid options.")
    }

    private fun invalid(request: NodeBridgeRequest, message: String): NodeBridgeResponse {
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.invalidRequest(message = message, module = request.module, method = request.method),
        )
    }

    private fun providerFailure(request: NodeBridgeRequest, error: Throwable): NodeBridgeResponse {
        Log.w(TAG, "${request.module}.${request.method} failed", error)
        return NodeBridgeResponse.failure(
            request.id,
            NodeBridgeError.providerFailed(
                message = error.message ?: error.javaClass.name,
                module = request.module,
                method = request.method,
                stack = error.stackTraceToString(),
            ),
        )
    }

    private fun screenshotUnavailable(request: NodeBridgeRequest, reason: String): NodeBridgeError {
        return NodeBridgeError.permissionDenied(
            message = "AutoJs6 accessibility screenshot is unavailable: $reason; fallback: media_projection (media_projection.requestScreenCapture + image.captureScreen).",
            module = request.module,
            method = request.method,
            category = NodeBridgeErrorCategory.UNAVAILABLE,
        )
    }

    private class DumpArgs(
        val format: DumpFormat,
        val window: String,
        val maxDepth: Int,
        val maxNodes: Int,
        val visibleOnly: Boolean,
        val properties: List<String>?,
    ) {
        companion object {
            private val KEYS = setOf("format", "window", "maxDepth", "maxNodes", "visibleOnly", "properties")

            fun parse(json: JSONObject): DumpArgs {
                rejectUnknownKeys(json, KEYS, "accessibility.dump")
                val format = DumpFormat.parse(json.optString("format", DumpFormat.TEXT.key))
                val properties = json.optJSONArray("properties")?.let { array ->
                    List(array.length()) { index -> array.optString(index) }.onEach(NodeDump::requireProperty)
                }?.takeIf { it.isNotEmpty() }
                return DumpArgs(
                    format = format,
                    window = windowOf(json, "accessibility.dump"),
                    maxDepth = intOf(json, "maxDepth", BridgeAccessibilityLimits.MAX_DEPTH, 0, BridgeAccessibilityLimits.MAX_DEPTH),
                    maxNodes = intOf(json, "maxNodes", BridgeAccessibilityLimits.MAX_NODES, 1, BridgeAccessibilityLimits.MAX_NODES),
                    visibleOnly = booleanOf(json, "visibleOnly", false),
                    properties = properties,
                )
            }
        }
    }

    private class ExplainArgs(val window: String, val maxNodes: Int, val keep: Int) {
        companion object {
            private val KEYS = setOf("window", "maxNodes", "keep")

            fun parse(json: JSONObject): ExplainArgs {
                rejectUnknownKeys(json, KEYS, "accessibility.explain")
                return ExplainArgs(
                    window = windowOf(json, "accessibility.explain"),
                    maxNodes = intOf(json, "maxNodes", BridgeAccessibilityLimits.MAX_EXPLAIN_NODES, 1, BridgeAccessibilityLimits.MAX_EXPLAIN_NODES),
                    keep = intOf(json, "keep", SelectorExplainer.DEFAULT_KEEP, 0, BridgeAccessibilityLimits.MAX_EXPLAIN_KEEP),
                )
            }
        }
    }

    private class ScreenshotArgs(
        val format: String,
        val quality: Int,
        val scale: Double?,
        val maxWidth: Int?,
        val region: BridgeBounds?,
        val savePath: String?,
    ) {
        val extension: String
            get() = when (format) {
                FORMAT_PNG -> "png"
                FORMAT_WEBP -> "webp"
                else -> "jpg"
            }

        val mime: String
            get() = when (format) {
                FORMAT_PNG -> "image/png"
                FORMAT_WEBP -> "image/webp"
                else -> "image/jpeg"
            }

        @Suppress("DEPRECATION")
        fun compressFormat(): Bitmap.CompressFormat = when (format) {
            FORMAT_PNG -> Bitmap.CompressFormat.PNG
            FORMAT_WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }

        companion object {
            const val FORMAT_JPEG = "jpeg"
            const val FORMAT_PNG = "png"
            const val FORMAT_WEBP = "webp"
            private val KEYS = setOf("format", "quality", "scale", "maxWidth", "region", "savePath")
            private val FORMATS = setOf(FORMAT_JPEG, FORMAT_PNG, FORMAT_WEBP)

            fun parse(json: JSONObject): ScreenshotArgs {
                rejectUnknownKeys(json, KEYS, "accessibility.screenshot")
                val format = json.optString("format", FORMAT_JPEG).lowercase().let { if (it == "jpg") FORMAT_JPEG else it }
                require(format in FORMATS) { "accessibility.screenshot format must be one of ${FORMATS.joinToString(", ")}, got \"$format\"" }
                val scale = if (json.has("scale") && !json.isNull("scale")) {
                    val value = json.opt("scale")
                    require(value is Number) { "accessibility.screenshot scale must be a number" }
                    value.toDouble().also { require(it > 0.0 && it <= 1.0) { "accessibility.screenshot scale must be within (0, 1], got $it" } }
                } else {
                    null
                }
                val maxWidth = if (json.has("maxWidth") && !json.isNull("maxWidth")) {
                    intOf(json, "maxWidth", 1, 1, Int.MAX_VALUE).also { require(it >= 1) { "accessibility.screenshot maxWidth must be positive" } }
                } else {
                    null
                }
                require(scale == null || maxWidth == null) { "accessibility.screenshot accepts either scale or maxWidth, not both" }
                val region = json.optJSONObject("region")?.let { NodeBridgeAccessibilityCodec.boundsOf(json, "region") }
                val savePath = json.optString("savePath").takeIf { json.has("savePath") && it.isNotBlank() }
                return ScreenshotArgs(
                    format = format,
                    quality = intOf(json, "quality", DEFAULT_QUALITY, 1, 100),
                    scale = scale,
                    maxWidth = maxWidth,
                    region = region,
                    savePath = savePath,
                )
            }
        }
    }

    companion object {
        const val SCHEMA_DUMP = "autojs6-bridge-accessibility-dump-v1"
        const val SCHEMA_EXPLAIN = "autojs6-bridge-accessibility-explain-v1"
        const val SCHEMA_SCREENSHOT = "autojs6-bridge-accessibility-screenshot-v1"
        const val SCHEMA_WINDOW = "autojs6-bridge-app-current-window-v1"
        const val WINDOW_ACTIVE = "active"
        const val WINDOW_ALL = "all"
        const val PAYLOAD_KIND_FILE = "file"
        const val PAYLOAD_KIND_WORKSPACE = "workspace"
        const val PAYLOAD_DIR = "bridge-payloads"
        const val PAYLOAD_TTL_MS = 10 * 60 * 1000L
        const val DEFAULT_QUALITY = 80

        private const val TAG = "NodeBridgeA11yDiag"
        private const val TYPE_MAGNIFICATION_OVERLAY = 6

        private val ROOT_NORMAL = A11yWindowRootPolicy()
        private val ROOT_FAST = A11yWindowRootPolicy(mode = WindowRootPolicy.Mode.FAST)

        private fun rejectUnknownKeys(json: JSONObject, keys: Set<String>, method: String) {
            val iterator = json.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                require(key in keys) { "$method does not accept option \"$key\"; expected one of ${keys.joinToString(", ")}" }
            }
        }

        private fun windowOf(json: JSONObject, method: String): String {
            val window = json.optString("window", WINDOW_ACTIVE)
            require(window == WINDOW_ACTIVE || window == WINDOW_ALL) { "$method window must be \"$WINDOW_ACTIVE\" or \"$WINDOW_ALL\", got \"$window\"" }
            return window
        }

        private fun intOf(json: JSONObject, key: String, default: Int, min: Int, max: Int): Int {
            if (!json.has(key) || json.isNull(key)) return default
            val value = json.opt(key)
            require(value is Number) { "$key must be a number" }
            return value.toInt().coerceIn(min, max)
        }

        private fun booleanOf(json: JSONObject, key: String, default: Boolean): Boolean {
            if (!json.has(key) || json.isNull(key)) return default
            val value = json.opt(key)
            require(value is Boolean) { "$key must be a boolean" }
            return value
        }

        /** Cuts [text] to at most [maxBytes] of UTF-8 on a character boundary. zh-CN: 把 [text] 按字符边界截到至多 [maxBytes] 个 UTF-8 字节. */
        fun capUtf8(text: String, maxBytes: Int): Pair<String, Boolean> {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            if (bytes.size <= maxBytes) return text to false
            var end = maxBytes
            while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
            return String(bytes, 0, end, StandardCharsets.UTF_8) to true
        }
    }
}
