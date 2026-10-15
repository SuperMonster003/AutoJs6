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

    @Throws(UiPreviewRequestException::class)
    fun validateSession(params: UiPreviewSessionParams, contractVersion: Int, sdkInt: Int) {
        // A v1 client keeps working against a v2 host (v2 only appends transactions).
        if (contractVersion != UiPreviewContract.VERSION && contractVersion != UiPreviewContract.BASE_VERSION) {
            throw UiPreviewRequestException(
                UiPreviewContract.ERROR_CONTRACT_MISMATCH,
                "contractVersion must be ${UiPreviewContract.BASE_VERSION} or ${UiPreviewContract.VERSION}, got $contractVersion",
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

    private fun invalid(message: String) = UiPreviewRequestException(UiPreviewContract.ERROR_INVALID_REQUEST, message)
}
