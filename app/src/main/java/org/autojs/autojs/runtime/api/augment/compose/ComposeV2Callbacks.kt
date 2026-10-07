package org.autojs.autojs.runtime.api.augment.compose

import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiEventFields
import org.autojs.plugin.compose.api.v2.ComposeUiV2.Events as E
import org.autojs.plugin.compose.api.v2.ComposeUiV2.Fields as F

/** Decode only validated scalar payloads; absence of a date is the script's explicit null. */
internal object ComposeV2Callbacks {
    fun arguments(event: String, payload: Map<String, Any?>): Array<Any?>? {
        fun invalid(field: String): Nothing = throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
            "Invalid $event event payload", prop = field)
        fun bool(field: String) = payload[field] as? Boolean ?: invalid(field)
        fun text(field: String) = payload[field] as? String ?: invalid(field)
        fun integer(field: String, minimum: Long, maximum: Long): Long {
            val value = (payload[field] as? Number)?.toDouble() ?: invalid(field)
            if (!value.isFinite() || value % 1.0 != 0.0 || value < minimum || value > maximum) invalid(field)
            return value.toLong()
        }
        return when (event) {
            E.OPEN_CHANGE -> arrayOf(bool(F.OPEN))
            E.SELECTED_CHANGE -> arrayOf(bool(F.SELECTED))
            E.EXPANDED_CHANGE -> arrayOf(bool(F.EXPANDED))
            E.QUERY_CHANGE, E.SEARCH -> arrayOf(text(ComposeUiEventFields.TEXT))
            E.PAGE_CHANGE -> arrayOf(integer(F.PAGE, 0, Int.MAX_VALUE.toLong()).toInt())
            E.TIME_CHANGE -> arrayOf(integer(F.HOUR, 0, 23).toInt(), integer(F.MINUTE, 0, 59).toInt())
            E.DATE_CHANGE -> arrayOf(if (F.SELECTED_DATE_MILLIS !in payload) null else {
                integer(F.SELECTED_DATE_MILLIS, -12212553600000L, 253402214400000L).also {
                    if (it % 86_400_000L != 0L) invalid(F.SELECTED_DATE_MILLIS)
                }
            })
            E.DISPLAY_MODE_CHANGE -> arrayOf(text(F.DISPLAY_MODE).also {
                if (it != "picker" && it != "input") invalid(F.DISPLAY_MODE)
            })
            E.REFRESH -> emptyArray()
            else -> null
        }
    }
}
