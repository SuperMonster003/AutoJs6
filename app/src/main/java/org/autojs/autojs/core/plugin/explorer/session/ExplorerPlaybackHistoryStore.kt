package org.autojs.autojs.core.plugin.explorer.session

import android.content.Context
import org.autojs.autojs.core.pref.keys.ExplorerKeys
import java.io.File

/** Host-private single-item playback history. Preference keys contain only SHA-256 path digests. */
internal class ExplorerPlaybackHistoryStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    val enabled: Boolean
        get() = ExplorerKeys.EXPLORER_PLAYBACK_HISTORY.get()

    fun get(file: File): ExplorerPlaybackProgressRecord? = synchronized(LOCK) {
        if (!enabled) return@synchronized null
        val key = entryKey(file)
        val encoded = preferences.getString(key, null)
        val record = ExplorerPlaybackHistoryPolicy.decode(encoded)
        val obsoleteKeys = entryKeys().filter { it != key }.toMutableList()
        if (encoded != null && record == null) obsoleteKeys += key
        if (obsoleteKeys.isNotEmpty()) {
            preferences.edit().also { editor -> obsoleteKeys.forEach(editor::remove) }.apply()
        }
        record
    }

    fun report(
        file: File,
        positionMillis: Long,
        durationMillis: Long,
        completed: Boolean,
    ) = synchronized(LOCK) {
        if (!enabled) return@synchronized
        val key = entryKey(file)
        when (
            ExplorerPlaybackHistoryPolicy.reportDecision(
                positionMillis,
                durationMillis,
                completed,
            )
        ) {
            ExplorerPlaybackReportDecision.CLEAR -> {
                val keys = entryKeys()
                if (keys.isNotEmpty()) {
                    preferences.edit().also { editor -> keys.forEach(editor::remove) }.apply()
                }
            }
            ExplorerPlaybackReportDecision.STORE -> {
                val record = ExplorerPlaybackProgressRecord(
                    positionMillis = positionMillis,
                    durationMillis = durationMillis,
                    updatedAtMillis = clock().coerceAtLeast(0L),
                )
                val editor = preferences.edit().putString(
                    key,
                    ExplorerPlaybackHistoryPolicy.encode(record),
                )
                entryKeys().filter { it != key }.forEach(editor::remove)
                editor.apply()
            }
        }
    }

    private fun entryKeys(): Set<String> = preferences.all.keys.filterTo(mutableSetOf()) {
        it.startsWith(ENTRY_PREFIX)
    }

    private fun entryKey(file: File): String = ENTRY_PREFIX +
        ExplorerPlaybackHistoryPolicy.keyForCanonicalPath(file.canonicalFile.path)

    companion object {
        private const val PREFERENCES_NAME = "explorer_playback_history"
        private const val ENTRY_PREFIX = "entry."
        private val LOCK = Any()

        fun clearAll(context: Context): Int = synchronized(LOCK) {
            val preferences = context.applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            )
            val keys = preferences.all.keys.filter { it.startsWith(ENTRY_PREFIX) }
            if (keys.isNotEmpty()) {
                preferences.edit().also { editor -> keys.forEach(editor::remove) }.commit()
            }
            keys.size
        }

        fun entryCount(context: Context): Int = synchronized(LOCK) {
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .all.keys.count { it.startsWith(ENTRY_PREFIX) }
        }
    }
}
