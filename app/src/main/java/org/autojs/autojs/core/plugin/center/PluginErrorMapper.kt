package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.plugin.isPluginBindRefused

import android.os.DeadObjectException
import android.os.RemoteException

object PluginErrorMapper {

    fun fromThrowable(t: Throwable): PluginError {
        val message = t.message ?: t.toString()
        val causeClass = t.javaClass.name
        val causeChain = generateSequence(t) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
        val diagnosticText = causeChain.joinToString(" | ") { cause ->
            "${cause.javaClass.name}: ${cause.message.orEmpty()}"
        }
        val code = when {
            isProtocolMismatch(diagnosticText, causeChain.joinToString("|") { it.javaClass.name }) -> PluginErrorCode.PROTOCOL_MISMATCH
            causeChain.any { it is SecurityException } -> PluginErrorCode.BIND_SECURITY_EXCEPTION
            causeChain.any { it is DeadObjectException } -> PluginErrorCode.PLUGIN_PROCESS_CRASHED
            causeChain.any { it is ClassNotFoundException || it is NoClassDefFoundError } -> PluginErrorCode.PLUGIN_PROCESS_CRASHED
            causeChain.any { it is RemoteException } -> PluginErrorCode.DEAD_OBJECT
            diagnosticText.contains("service binding died", ignoreCase = true) -> PluginErrorCode.PLUGIN_PROCESS_CRASHED
            diagnosticText.contains("service disconnected", ignoreCase = true) -> PluginErrorCode.PLUGIN_PROCESS_CRASHED
            diagnosticText.contains("onBindingDied", ignoreCase = true) -> PluginErrorCode.PLUGIN_PROCESS_CRASHED
            diagnosticText.contains("onServiceDisconnected", ignoreCase = true) -> PluginErrorCode.PLUGIN_PROCESS_CRASHED
            diagnosticText.contains("binderDied", ignoreCase = true) -> PluginErrorCode.PLUGIN_PROCESS_CRASHED
            diagnosticText.contains("process has died", ignoreCase = true) -> PluginErrorCode.PLUGIN_PROCESS_CRASHED
            diagnosticText.contains("process crashed", ignoreCase = true) -> PluginErrorCode.PLUGIN_PROCESS_CRASHED
            diagnosticText.contains("keeps stopping", ignoreCase = true) -> PluginErrorCode.PLUGIN_PROCESS_CRASHED
            diagnosticText.contains("No OCR service found", ignoreCase = true) -> PluginErrorCode.SERVICE_NOT_FOUND
            diagnosticText.contains("No plugin info service found", ignoreCase = true) -> PluginErrorCode.SERVICE_NOT_FOUND
            diagnosticText.contains("bindService SecurityException", ignoreCase = true) -> PluginErrorCode.BIND_SECURITY_EXCEPTION
            diagnosticText.contains("service returned null binding", ignoreCase = true) -> PluginErrorCode.BIND_FAILED
            diagnosticText.contains("onNullBinding", ignoreCase = true) -> PluginErrorCode.BIND_FAILED
            diagnosticText.contains("Plugin enable handshake timed out", ignoreCase = true) -> PluginErrorCode.HANDSHAKE_TIMEOUT
            diagnosticText.contains("bind", ignoreCase = true) &&
                    (diagnosticText.contains("timeout", ignoreCase = true) ||
                            diagnosticText.contains("timed out", ignoreCase = true)) -> PluginErrorCode.HANDSHAKE_TIMEOUT
            diagnosticText.contains("bindService failed", ignoreCase = true) -> PluginErrorCode.BIND_FAILED
            else -> PluginErrorCode.INTERNAL_ERROR
        }
        return PluginError(
            code = code,
            message = message,
            causeClass = causeClass,
            bindRefused = t.isPluginBindRefused(),
        )
    }

    private fun isProtocolMismatch(message: String, causeClass: String): Boolean {
        return causeClass.contains("BadParcelableException", ignoreCase = true) ||
                causeClass.contains("TransactionTooLargeException", ignoreCase = true) ||
                message.contains("Bad magic number for Bundle", ignoreCase = true) ||
                message.contains("Bundle length is not aligned by 4", ignoreCase = true) ||
                message.contains("TransactionTooLargeException", ignoreCase = true) ||
                (message.contains("Bundle", ignoreCase = true) && message.contains("unmarshal", ignoreCase = true))
    }

    fun shouldRecommendActivation(error: PluginError): Boolean {
        return when (error.code) {
            PluginErrorCode.BIND_FAILED,
            PluginErrorCode.HANDSHAKE_TIMEOUT,
            PluginErrorCode.DEAD_OBJECT,
            -> true
            else -> false
        }
    }

    private const val MAX_CAUSE_DEPTH = 8
}
