package org.autojs.plugin.mediainfo.api

/** Replaces only the identity field, preserving media metadata and line endings. */
object MediainfoReportIdentity {
    private val completeName = Regex("(?m)^(Complete name[\\t ]*:[\\t ]*)[^\\r\\n]*")

    fun inform(report: String, sourceName: String): String {
        val text = sourceName.replace("\r", "\\r").replace("\n", "\\n")
        return completeName.replace(report) { match -> match.groupValues[1] + text }
    }
}
