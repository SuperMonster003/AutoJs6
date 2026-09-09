package org.autojs.plugin.mediainfo.api

import java.util.Locale

data class MediainfoQueryOptions(val streamNumber: Int = 0, val infoKind: String = "TEXT") {
    companion object {
        val INFO_KINDS = listOf("NAME", "TEXT", "MEASURE", "OPTIONS", "NAME_TEXT", "MEASURE_TEXT", "INFO", "HOWTO", "DOMAIN")
        const val UNSUPPORTED = "MEDIAINFO_QUERY_UNSUPPORTED"

        fun parse(streamNumber: Any? = null, infoKind: Any? = null): MediainfoQueryOptions {
            val number = if (streamNumber == null) 0.0 else {
                require(streamNumber is Number) { "MediaInfo streamNumber must be a non-negative integer" }
                streamNumber.toDouble()
            }
            require(number.isFinite() && number >= 0 && number <= Int.MAX_VALUE && number == number.toInt().toDouble()) {
                "MediaInfo streamNumber must be a non-negative integer no greater than ${Int.MAX_VALUE}"
            }
            require(infoKind == null || infoKind is String) { "MediaInfo infoKind must be a string" }
            val kind = (infoKind as? String)?.trim()?.uppercase(Locale.US) ?: "TEXT"
            require(kind in INFO_KINDS) { "Unsupported MediaInfo infoKind: $infoKind" }
            return MediainfoQueryOptions(number.toInt(), kind)
        }
    }
}
