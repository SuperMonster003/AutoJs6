package org.autojs.autojs.core.ui.preview

import org.autojs.plugin.ui.preview.UiPreviewContract
import java.security.MessageDigest
import java.util.Locale

/** A request that the preview service rejects, carrying the contract error code and a message for the client. */
class UiPreviewRequestException(val code: String, message: String) : Exception(message)

/** The validated parameters of a preview session (plain values, no Android types; see [UiPreviewRequests]). */
data class UiPreviewSessionParams(
    val mode: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val nightMode: Int,
    val localeTag: String,
    val fontScale: Float,
    val displayId: Int,
    val label: String,
    val clientVersionCode: Long,
)

/**
 * Request validation of the ui-mode preview service, kept free of Android types so the JVM tests cover it.
 * Every limit comes from [UiPreviewContract]; nothing here reads a request's XML text beyond its length and digest.
 *
 * zh-CN: ui 模式预览服务的请求校验, 不依赖 Android 类型以便 JVM 测试覆盖; 全部上限来自 [UiPreviewContract],
 * 这里除长度与摘要外不读取请求的 XML 正文.
 */
object UiPreviewRequests {

    private val CONTROL_CHARS = Regex("[\\p{Cntrl}]")

    /** The contract versions a session may be created with, ascending. */
    val SUPPORTED_CONTRACT_VERSIONS = listOf(
        UiPreviewContract.BASE_VERSION, UiPreviewContract.VERSION_V2, UiPreviewContract.VERSION_V3, UiPreviewContract.VERSION,
    )

    @Throws(UiPreviewRequestException::class)
    fun validateSession(params: UiPreviewSessionParams, contractVersion: Int, sdkInt: Int) {
        // Older clients keep working against a newer host: every version only appends.
        if (contractVersion !in SUPPORTED_CONTRACT_VERSIONS) {
            throw UiPreviewRequestException(
                UiPreviewContract.ERROR_CONTRACT_MISMATCH,
                "contractVersion must be one of ${SUPPORTED_CONTRACT_VERSIONS.joinToString()}, got $contractVersion",
            )
        }
        if (params.mode !in UiPreviewContract.MODES) {
            throw invalid("mode must be one of ${UiPreviewContract.MODES.joinToString()}")
        }
        if (params.mode == UiPreviewContract.MODE_EMBEDDED && sdkInt < UiPreviewContract.MIN_API_EMBEDDED) {
            throw UiPreviewRequestException(
                UiPreviewContract.ERROR_UNSUPPORTED_MODE,
                "the embedded mode needs API ${UiPreviewContract.MIN_API_EMBEDDED}, this device is API $sdkInt",
            )
        }
        validateSize(params.widthPx, params.heightPx)
        if (params.densityDpi != 0 && params.densityDpi !in UiPreviewContract.MIN_DENSITY_DPI..UiPreviewContract.MAX_DENSITY_DPI) {
            throw invalid("densityDpi must be 0 or ${UiPreviewContract.MIN_DENSITY_DPI}..${UiPreviewContract.MAX_DENSITY_DPI}")
        }
        if (params.nightMode !in listOf(UiPreviewContract.NIGHT_MODE_FOLLOW_HOST, UiPreviewContract.NIGHT_MODE_NO, UiPreviewContract.NIGHT_MODE_YES)) {
            throw invalid("nightMode must be 0, 1 or 2")
        }
        if (params.localeTag.length > UiPreviewContract.MAX_LOCALE_TAG_CHARS || CONTROL_CHARS.containsMatchIn(params.localeTag)) {
            throw invalid("localeTag must be a BCP 47 tag of at most ${UiPreviewContract.MAX_LOCALE_TAG_CHARS} chars")
        }
        if (params.fontScale != 0f && (params.fontScale.isNaN() || params.fontScale !in UiPreviewContract.MIN_FONT_SCALE..UiPreviewContract.MAX_FONT_SCALE)) {
            throw invalid("fontScale must be 0 or ${UiPreviewContract.MIN_FONT_SCALE}..${UiPreviewContract.MAX_FONT_SCALE}")
        }
        if (params.displayId < 0) throw invalid("displayId must not be negative")
    }

    @Throws(UiPreviewRequestException::class)
    fun validateSize(widthPx: Int, heightPx: Int) {
        if (widthPx !in UiPreviewContract.MIN_SIZE_PX..UiPreviewContract.MAX_WIDTH_PX ||
            heightPx !in UiPreviewContract.MIN_SIZE_PX..UiPreviewContract.MAX_HEIGHT_PX
        ) {
            throw invalid("size must be ${UiPreviewContract.MIN_SIZE_PX}..${UiPreviewContract.MAX_WIDTH_PX} x ${UiPreviewContract.MIN_SIZE_PX}..${UiPreviewContract.MAX_HEIGHT_PX} px")
        }
        if (widthPx.toLong() * heightPx > UiPreviewContract.MAX_SNAPSHOT_PIXELS) {
            throw invalid("size exceeds ${UiPreviewContract.MAX_SNAPSHOT_PIXELS} pixels")
        }
    }

    /** Checks a layout request; [hasOutput] says whether a snapshot descriptor was supplied. */
    @Throws(UiPreviewRequestException::class)
    fun validateLayout(xml: String?, bindingsJson: String?, xmlSha256: String?, mode: String, hasOutput: Boolean) {
        if (xml == null) throw invalid("xml is required")
        if (xml.length > UiPreviewContract.MAX_XML_CHARS) throw invalid("xml exceeds ${UiPreviewContract.MAX_XML_CHARS} chars")
        if (bindingsJson != null && bindingsJson.length > UiPreviewContract.MAX_BINDINGS_CHARS) {
            throw invalid("bindingsJson exceeds ${UiPreviewContract.MAX_BINDINGS_CHARS} chars")
        }
        if (xmlSha256 != null && !xmlSha256.equals(sha256Hex(xml), ignoreCase = true)) {
            throw invalid("xmlSha256 does not match xml")
        }
        if (mode == UiPreviewContract.MODE_SNAPSHOT && !hasOutput) throw invalid("the snapshot mode needs an output descriptor")
    }

    @Throws(UiPreviewRequestException::class)
    fun validatePointerMode(mode: Int, sdkInt: Int) {
        when (mode) {
            UiPreviewContract.POINTER_MODE_EDIT -> Unit
            UiPreviewContract.POINTER_MODE_INTERACTIVE -> if (sdkInt < UiPreviewContract.MIN_API_INTERACTIVE) {
                throw UiPreviewRequestException(
                    UiPreviewContract.ERROR_UNSUPPORTED_MODE,
                    "the interactive pointer mode needs API ${UiPreviewContract.MIN_API_INTERACTIVE}, this device is API $sdkInt",
                )
            }
            else -> throw invalid("pointer mode must be 0 or 1")
        }
    }

    /**
     * Checks a v2 `runScript` request (3-Block Compose 1.7.0): the text must be present, within the limit and a
     * `"ui"` mode script; the session must be embedded on a device that lets pointer input reach the tree (API 33+).
     * zh-CN: 校验 v2 `runScript` 请求 (3-Block Compose 1.7.0): 文本必须存在, 不超上限且是 `"ui"` 模式脚本; 会话必须是嵌入模式且设备允许指针进入树 (API 33+).
     */
    @Throws(UiPreviewRequestException::class)
    fun validateScript(text: String?, mode: String, sdkInt: Int) {
        if (mode != UiPreviewContract.MODE_EMBEDDED) {
            throw UiPreviewRequestException(UiPreviewContract.ERROR_UNSUPPORTED_MODE, "runScript needs an embedded session")
        }
        if (sdkInt < UiPreviewContract.MIN_API_INTERACTIVE) {
            throw UiPreviewRequestException(
                UiPreviewContract.ERROR_UNSUPPORTED_MODE,
                "the interactive preview needs API ${UiPreviewContract.MIN_API_INTERACTIVE}, this device is API $sdkInt",
            )
        }
        if (text == null) throw invalid("scriptText is required")
        if (text.length > UiPreviewContract.MAX_SCRIPT_CHARS) throw invalid("scriptText exceeds ${UiPreviewContract.MAX_SCRIPT_CHARS} chars")
        if (!UI_DIRECTIVE.containsMatchIn(text)) {
            throw UiPreviewRequestException(UiPreviewContract.ERROR_SCRIPT_FAILED, "the script does not start with the \"ui\" directive")
        }
    }

    /** The `"ui"` directive as the first statement, with comments allowed before it (the host's own execution mode rule). */
    private val UI_DIRECTIVE = Regex("""^\s*(?:(?://[^\n]*\n|/\*.*?\*/)\s*)*(['"])ui\1\s*;""", RegexOption.DOT_MATCHES_ALL)

    /** A label for the host's task list: control characters removed, trimmed, limited; [fallback] when empty. */
    fun sanitizeLabel(label: String?, fallback: String): String {
        val cleaned = label.orEmpty().replace(CONTROL_CHARS, " ").trim()
        if (cleaned.isEmpty()) return fallback
        return if (cleaned.length > UiPreviewContract.MAX_LABEL_CHARS) cleaned.substring(0, UiPreviewContract.MAX_LABEL_CHARS) else cleaned
    }

    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { String.format(Locale.ROOT, "%02x", it) }

    // ------------------------------------------------------------------------------------------------ v3

    /**
     * Checks the v3 keys of a layout request (host roadmap R2): a resources zip needs its lowercase SHA-256, and
     * `layoutPath` needs the zip. Returns the normalized `layoutPath`, or null.
     * zh-CN: 校验布局请求的 v3 键: 资源 zip 必须带小写 SHA-256, `layoutPath` 必须伴随 zip; 返回规范化的 `layoutPath`.
     */
    @Throws(UiPreviewRequestException::class)
    fun validateLayoutResources(hasResources: Boolean, resourcesSha256: String?, layoutPath: String?): String? {
        if (!hasResources) {
            if (resourcesSha256 != null || layoutPath != null) throw invalid("resourcesSha256 and layoutPath need resourcesFd")
            return null
        }
        validateArchiveDigest(resourcesSha256, UiPreviewContract.KEY_RESOURCES_SHA256)
        return layoutPath?.let { archivePath(it, UiPreviewContract.KEY_LAYOUT_PATH) }
    }

    /**
     * Checks the v3 keys of a `runScript` request: a project zip needs its digest and the script's path in it.
     * Returns the normalized `scriptPath`, or null without a project.
     * zh-CN: 校验 `runScript` 的 v3 键: 工程 zip 必须带摘要与脚本在其中的路径; 返回规范化的 `scriptPath`.
     */
    @Throws(UiPreviewRequestException::class)
    fun validateProject(hasProject: Boolean, projectSha256: String?, scriptPath: String?): String? {
        if (!hasProject) {
            if (projectSha256 != null || scriptPath != null) throw invalid("projectSha256 and scriptPath need projectFd")
            return null
        }
        validateArchiveDigest(projectSha256, UiPreviewContract.KEY_PROJECT_SHA256)
        if (scriptPath == null) throw invalid("scriptPath is required with projectFd")
        return archivePath(scriptPath, UiPreviewContract.KEY_SCRIPT_PATH)
    }

    @Throws(UiPreviewRequestException::class)
    fun validateArchiveDigest(sha256: String?, key: String) {
        if (sha256 == null || !SHA256_HEX.matches(sha256)) throw invalid("$key must be a lowercase SHA-256")
    }

    /**
     * [path] as a normalized `/`-separated relative path inside an archive: not empty, at most
     * [UiPreviewContract.MAX_ARCHIVE_PATH_CHARS] chars, no leading `/`, no backslash, colon or control character, no
     * empty, `.` or `..` segment. Anything else is rejected with [code] (a request key or a zip entry, [what]).
     * zh-CN: 归档内的规范化相对路径: 非空, 不超长, 不以 `/` 开头, 不含反斜杠 / 冒号 / 控制字符, 不含空段, `.` 段或 `..` 段.
     */
    @Throws(UiPreviewRequestException::class)
    fun archivePath(path: String?, what: String, code: String = UiPreviewContract.ERROR_INVALID_REQUEST): String {
        fun reject(reason: String): Nothing = throw UiPreviewRequestException(code, "$what \"${path.orEmpty().take(80)}\" $reason")
        if (path.isNullOrEmpty()) reject("is empty")
        if (path.length > UiPreviewContract.MAX_ARCHIVE_PATH_CHARS) reject("exceeds ${UiPreviewContract.MAX_ARCHIVE_PATH_CHARS} chars")
        if (path.startsWith("/")) reject("is absolute")
        if ('\\' in path || ':' in path || CONTROL_CHARS.containsMatchIn(path)) reject("contains a backslash, colon or control character")
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) reject("has an empty, . or .. segment")
        return segments.joinToString("/")
    }

    private val SHA256_HEX = Regex("[0-9a-f]{64}")

    // ------------------------------------------------------------------------------------------------ v4

    /**
     * The busy-wait limit of a v4 `runScript` request: absent (null) means the default, a number is taken as whole
     * milliseconds (a client may put an int or a long), 0 turns the watch off, anything else must be within the limits.
     * zh-CN: v4 `runScript` 请求的忙等上限: 缺省 (null) 取默认值, 数值按整毫秒 (客户端可放 int 或 long), 0 关闭观察, 其余须在上下限之内.
     */
    @Throws(UiPreviewRequestException::class)
    fun validateBusyWaitLimit(value: Any?): Long {
        if (value == null) return UiPreviewContract.DEFAULT_BUSY_WAIT_LIMIT_MS
        val limit = when (value) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw invalid("${UiPreviewContract.KEY_BUSY_WAIT_LIMIT_MS} must be an int or a long")
        }
        if (limit != 0L && limit !in UiPreviewContract.MIN_BUSY_WAIT_LIMIT_MS..UiPreviewContract.MAX_BUSY_WAIT_LIMIT_MS) {
            throw invalid(
                "${UiPreviewContract.KEY_BUSY_WAIT_LIMIT_MS} must be 0 or " +
                    "${UiPreviewContract.MIN_BUSY_WAIT_LIMIT_MS}..${UiPreviewContract.MAX_BUSY_WAIT_LIMIT_MS}, got $limit",
            )
        }
        return limit
    }

    /** The event of a v4 `sendLifecycle` request: one of [UiPreviewContract.LIFECYCLE_EVENTS]. */
    @Throws(UiPreviewRequestException::class)
    fun validateLifecycleEvent(event: String?): String {
        if (event == null || event !in UiPreviewContract.LIFECYCLE_EVENTS) {
            throw invalid("${UiPreviewContract.KEY_LIFECYCLE_EVENT} must be one of ${UiPreviewContract.LIFECYCLE_EVENTS.joinToString()}")
        }
        return event
    }

    // ------------------------------------------------------------------------------------------------ v5

    /**
     * The dialect of a v5 `runScript` request: absent (null) means `js`, anything else must be one of
     * [UiPreviewContract.SCRIPT_DIALECTS].
     * zh-CN: v5 `runScript` 请求的脚本方言: 缺省 (null) 为 `js`, 其余须为 [UiPreviewContract.SCRIPT_DIALECTS] 之一.
     */
    @Throws(UiPreviewRequestException::class)
    fun validateScriptDialect(value: Any?): String {
        if (value == null) return UiPreviewContract.SCRIPT_DIALECT_JS
        if (value !is String || value !in UiPreviewContract.SCRIPT_DIALECTS) {
            throw invalid("${UiPreviewContract.KEY_SCRIPT_DIALECT} must be one of ${UiPreviewContract.SCRIPT_DIALECTS.joinToString()}")
        }
        return value
    }

    /**
     * The file name a v5 TSX run is compiled under: the (sanitized) script name with path separators replaced and `.tsx`
     * appended unless it is there, so diagnostics and the host's task list name the script as the client does;
     * [FALLBACK_TSX_SOURCE_NAME] for a name that makes no file name of its own.
     * zh-CN: v5 TSX 运行的编译文件名: (已清理的) 脚本名, 替换路径分隔符, 未以 `.tsx` 结尾时追加; 不能成为文件名时用 [FALLBACK_TSX_SOURCE_NAME].
     */
    fun tsxSourceName(name: String): String {
        val base = name.replace('/', '_').replace('\\', '_').trim()
        if (base.isEmpty() || base == "." || base == ".." || base.startsWith(".")) return FALLBACK_TSX_SOURCE_NAME
        return if (base.endsWith(".tsx")) base else "$base.tsx"
    }

    const val FALLBACK_TSX_SOURCE_NAME = "ui-preview.tsx"

    private fun invalid(message: String) = UiPreviewRequestException(UiPreviewContract.ERROR_INVALID_REQUEST, message)
}
