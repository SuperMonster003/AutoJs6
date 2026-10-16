package org.autojs.autojs.core.plugin.speech

import android.os.Bundle
import android.os.DeadObjectException
import android.os.RemoteException
import android.os.TransactionTooLargeException
import kotlinx.coroutines.TimeoutCancellationException
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechLimits
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

/**
 * Host-side view of one speech plugin failure (3-Macaw Speech roadmap D27): a code from `SpeechErrorCodes` and a
 * message bounded by the contract's ceiling.
 * zh-CN: 宿主侧对一次语音插件失败的描述 (3-Macaw Speech 路线图 D27): 来自 `SpeechErrorCodes` 的错误码, 与受契约上限约束的信息.
 */
data class SpeechError(
    val code: String,
    val message: String,
)

/**
 * Thrown by the speech host client; the script layer turns it into the `SpeechError` JS object.
 * zh-CN: 由语音宿主客户端抛出; 脚本层把它转换为 `SpeechError` JS 对象.
 */
class SpeechPluginException(val error: SpeechError, cause: Throwable? = null) : RuntimeException("${error.code}: ${error.message}", cause) {

    val code: String
        get() = error.code
}

/**
 * Normalizes the plugin's error answers and host-side failures into [SpeechError] values (roadmap D27). Bundle answers,
 * error events and progress failures carry `KEY_ERROR_CODE` / `KEY_ERROR_MESSAGE`; the methods of the contract that
 * return a session or an operation id throw `IllegalArgumentException` / `IllegalStateException` whose message starts
 * with the code, which [fromThrowable] decodes. Unknown codes collapse to `INTERNAL` and messages are truncated to the
 * contract ceiling.
 * zh-CN: 把插件的错误应答与宿主侧失败规范化为 [SpeechError] (路线图 D27). Bundle 应答, 错误事件与进度失败携带 `KEY_ERROR_CODE` /
 * `KEY_ERROR_MESSAGE`; 契约中返回会话或操作 id 的方法抛出消息以错误码开头的 `IllegalArgumentException` / `IllegalStateException`,
 * 由 [fromThrowable] 解码. 未知错误码归为 `INTERNAL`, 信息截断到契约上限.
 */
object SpeechErrorMapper {

    /** Where a throwable was raised; the same exception type maps differently per phase. zh-CN: 异常发生的阶段; 同一异常类型在不同阶段映射不同. */
    enum class Phase { DISCOVERY, OPEN, CALL, TRANSFER }

    fun normalize(code: String?, message: String?): SpeechError {
        val trimmedCode = code?.trim().orEmpty()
        val knownCode = if (SpeechErrorCodes.isKnown(trimmedCode)) trimmedCode else SpeechErrorCodes.INTERNAL
        val text = buildString {
            if (knownCode != trimmedCode && trimmedCode.isNotEmpty()) {
                append("unknown error code ").append(trimmedCode)
                if (!message.isNullOrBlank()) append(": ")
            }
            append(message?.trim().orEmpty())
        }.ifBlank { defaultMessage(knownCode) }
        return SpeechError(knownCode, truncate(text, SpeechLimits.MAX_ERROR_MESSAGE_BYTES))
    }

    /** The error of a Bundle answer, event or progress failure, or null when it carries no error code. zh-CN: Bundle 应答, 事件或进度失败中的错误; 无错误码时为 null. */
    fun fromBundle(bundle: Bundle?): SpeechError? {
        val code = bundle?.getString(SpeechContract.KEY_ERROR_CODE) ?: return null
        return normalize(code, bundle.getString(SpeechContract.KEY_ERROR_MESSAGE))
    }

    fun fromThrowable(throwable: Throwable, phase: Phase): SpeechError = when (throwable) {
        is SpeechPluginException -> throwable.error
        is TimeoutCancellationException -> error(SpeechErrorCodes.TIMEOUT, throwable.message ?: "speech operation timed out")
        is CancellationException -> error(SpeechErrorCodes.SESSION_CANCELLED, "speech operation cancelled")
        is InterruptedException, is InterruptedIOException -> error(SpeechErrorCodes.SESSION_CANCELLED, "speech operation interrupted")
        is TransactionTooLargeException -> error(SpeechErrorCodes.LIMIT_EXCEEDED, describe("the request is too large for one Binder transaction", throwable))
        is DeadObjectException -> when (phase) {
            Phase.CALL, Phase.TRANSFER -> error(SpeechErrorCodes.SESSION_CLOSED, "speech plugin process died")
            else -> error(SpeechErrorCodes.PLUGIN_UNAVAILABLE, "speech plugin process died")
        }
        is RemoteException -> when (phase) {
            Phase.CALL, Phase.TRANSFER -> error(SpeechErrorCodes.SESSION_CLOSED, describe("speech plugin call failed", throwable))
            else -> error(SpeechErrorCodes.PLUGIN_UNAVAILABLE, describe("speech plugin binding failed", throwable))
        }
        is SecurityException -> encoded(throwable) ?: error(SpeechErrorCodes.PLUGIN_UNAVAILABLE, describe("speech plugin refused the caller", throwable))
        is IllegalArgumentException -> encoded(throwable) ?: error(SpeechErrorCodes.INVALID_OPTIONS, describe("invalid options", throwable))
        is IllegalStateException -> encoded(throwable) ?: when (phase) {
            Phase.DISCOVERY, Phase.OPEN -> error(SpeechErrorCodes.PLUGIN_UNAVAILABLE, describe("speech plugin is unavailable", throwable))
            else -> error(SpeechErrorCodes.INTERNAL, describe("speech host failure", throwable))
        }
        is UnsupportedOperationException -> encoded(throwable) ?: error(SpeechErrorCodes.PLUGIN_INCOMPATIBLE, describe("speech plugin does not implement the call", throwable))
        is FileNotFoundException -> error(SpeechErrorCodes.IO_FAILED, describe("file not found", throwable))
        is IOException -> error(SpeechErrorCodes.IO_FAILED, describe("speech file transfer failed", throwable))
        else -> error(SpeechErrorCodes.INTERNAL, describe("speech host failure", throwable))
    }

    /** The code the plugin encoded into an exception message (`CODE: detail`), or null for an ordinary message. zh-CN: 插件编码进异常消息的错误码 (`CODE: detail`); 普通消息为 null. */
    fun encoded(throwable: Throwable): SpeechError? {
        val decoded = SpeechErrorCodes.decode(throwable.message ?: return null) ?: return null
        return normalize(decoded.first, decoded.second)
    }

    fun defaultMessage(code: String): String = when (code) {
        SpeechErrorCodes.PLUGIN_UNAVAILABLE -> "speech plugin is unavailable"
        SpeechErrorCodes.PLUGIN_DISABLED -> "speech plugin is disabled"
        SpeechErrorCodes.PLUGIN_INCOMPATIBLE -> "speech plugin is incompatible with this host"
        SpeechErrorCodes.PERMISSION_DENIED -> "the speech plugin has no microphone permission; open the plugin to grant it"
        SpeechErrorCodes.MODEL_MISSING -> "no installed model"
        SpeechErrorCodes.MODEL_DOWNLOAD_FAILED -> "model download failed"
        SpeechErrorCodes.MODEL_CHECKSUM_MISMATCH -> "model checksum mismatch"
        SpeechErrorCodes.MODEL_LICENSE_NOT_ACCEPTED -> "the model license must be accepted first"
        SpeechErrorCodes.INVALID_OPTIONS -> "invalid options"
        SpeechErrorCodes.UNSUPPORTED_FORMAT -> "unsupported audio format"
        SpeechErrorCodes.LIMIT_EXCEEDED -> "limit exceeded"
        SpeechErrorCodes.SESSION_BUSY -> "the microphone is busy"
        SpeechErrorCodes.SESSION_CLOSED -> "session is closed"
        SpeechErrorCodes.SESSION_CANCELLED -> "operation cancelled"
        SpeechErrorCodes.ENGINE_FAILED -> "speech engine failure"
        SpeechErrorCodes.IO_FAILED -> "I/O failure"
        SpeechErrorCodes.TIMEOUT -> "operation timed out"
        else -> "speech plugin failure"
    }

    fun truncate(text: String, maxBytes: Int): String {
        if (text.toByteArray(StandardCharsets.UTF_8).size <= maxBytes) return text
        var end = text.length
        while (end > 0 && text.substring(0, end).toByteArray(StandardCharsets.UTF_8).size > maxBytes) {
            end -= maxOf(1, end / 8)
        }
        return text.substring(0, end)
    }

    /** A host-side failure as the exception the client throws. zh-CN: 以客户端抛出的异常表示的宿主侧失败. */
    fun failure(code: String, message: String, cause: Throwable? = null): SpeechPluginException =
        SpeechPluginException(normalize(code, message), cause)

    /** [fromThrowable] as an exception, keeping a [SpeechPluginException] as it is. zh-CN: 以异常形式返回 [fromThrowable]; [SpeechPluginException] 原样保留. */
    fun wrap(throwable: Throwable, phase: Phase): SpeechPluginException =
        throwable as? SpeechPluginException ?: SpeechPluginException(fromThrowable(throwable, phase), throwable)

    private fun error(code: String, message: String) = normalize(code, message)

    private fun describe(prefix: String, throwable: Throwable): String {
        val detail = throwable.message?.trim().orEmpty()
        return if (detail.isEmpty()) prefix else "$prefix: $detail"
    }
}
