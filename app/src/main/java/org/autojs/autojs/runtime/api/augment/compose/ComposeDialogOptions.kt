package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.dialog.DialogInteropV1

/** Script options only; trusted Android window type/token are selected by the host. */
internal data class ComposeDialogOptions(val type: String = DialogInteropV1.ALERT, val cancelable: Boolean = true) {
    companion object {
        fun parse(value: Any?): ComposeDialogOptions {
            val fields = ComposeScriptValues().objectProperties(value, "compose.dialog")
            fields.keys.firstOrNull { it !in setOf("type", "cancelable", "theme") }?.let {
                throw ComposeUiContractException(ComposeUiErrorCodes.UNKNOWN_PROP, prop = it)
            }
            val type = if ("type" !in fields) DialogInteropV1.ALERT else
                (ComposeScriptValues.unwrap(fields["type"]) as? CharSequence)?.toString()
                    ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "type")
            if (type != DialogInteropV1.ALERT && type != DialogInteropV1.BOTTOM_SHEET) throw ComposeUiContractException(
                ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "type")
            val cancelable = if ("cancelable" !in fields) true else
                ComposeScriptValues.unwrap(fields["cancelable"]) as? Boolean
                    ?: throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, prop = "cancelable")
            return ComposeDialogOptions(type, cancelable)
        }
    }
}
