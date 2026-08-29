package org.autojs.autojs.core.plugin.explorer.session

import java.security.MessageDigest
import kotlin.text.Charsets.UTF_8

internal data class ExplorerPlaybackProgressRecord(
    val positionMillis: Long,
    val durationMillis: Long,
    val updatedAtMillis: Long,
)

internal enum class ExplorerPlaybackReportDecision { STORE, CLEAR }

/** Android-free validation, retention and serialization rules for opt-in host playback history. */
internal object ExplorerPlaybackHistoryPolicy {

    const val MIN_DURATION_MILLIS = 30_000L
    const val MIN_POSITION_MILLIS = 5_000L

    private const val COMPLETION_PERCENT = 95L
    private const val RECORD_SEPARATOR = '|'

    fun reportDecision(
        positionMillis: Long,
        durationMillis: Long,
        completed: Boolean,
    ): ExplorerPlaybackReportDecision {
        require(positionMillis >= 0L) { "Playback position is negative" }
        require(durationMillis > 0L) { "Playback duration is not positive" }
        require(positionMillis <= durationMillis) { "Playback position exceeds duration" }
        return if (
            completed ||
            durationMillis < MIN_DURATION_MILLIS ||
            positionMillis < MIN_POSITION_MILLIS ||
            positionMillis >= completionThreshold(durationMillis)
        ) {
            ExplorerPlaybackReportDecision.CLEAR
        } else {
            ExplorerPlaybackReportDecision.STORE
        }
    }

    fun keyForCanonicalPath(canonicalPath: String): String {
        require(canonicalPath.isNotEmpty()) { "Canonical path is empty" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalPath.toByteArray(UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    fun encode(record: ExplorerPlaybackProgressRecord): String {
        require(record.updatedAtMillis >= 0L) { "Playback update time is negative" }
        require(
            reportDecision(record.positionMillis, record.durationMillis, completed = false) ==
                ExplorerPlaybackReportDecision.STORE,
        ) { "Playback record is not resumable" }
        return listOf(
            record.positionMillis,
            record.durationMillis,
            record.updatedAtMillis,
        ).joinToString(RECORD_SEPARATOR.toString())
    }

    fun decode(value: String?): ExplorerPlaybackProgressRecord? {
        val parts = value?.split(RECORD_SEPARATOR) ?: return null
        if (parts.size != 3) return null
        val record = ExplorerPlaybackProgressRecord(
            positionMillis = parts[0].toLongOrNull() ?: return null,
            durationMillis = parts[1].toLongOrNull() ?: return null,
            updatedAtMillis = parts[2].toLongOrNull() ?: return null,
        )
        if (record.updatedAtMillis < 0L) return null
        return runCatching {
            if (
                reportDecision(
                    record.positionMillis,
                    record.durationMillis,
                    completed = false,
                ) == ExplorerPlaybackReportDecision.STORE
            ) {
                record
            } else {
                null
            }
        }.getOrNull()
    }

    private fun completionThreshold(durationMillis: Long): Long =
        durationMillis / 100L * COMPLETION_PERCENT +
            (durationMillis % 100L * COMPLETION_PERCENT + 99L) / 100L
}
