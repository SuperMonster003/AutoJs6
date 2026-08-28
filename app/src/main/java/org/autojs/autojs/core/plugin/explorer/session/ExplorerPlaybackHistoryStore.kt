package org.autojs.autojs.core.plugin.explorer.session

import android.content.Context
import android.content.SharedPreferences
import org.autojs.autojs.core.pref.Pref
import java.io.File

/** Host-private bounded playback history. Preference keys contain only SHA-256 path digests. */
internal class ExplorerPlaybackHistoryStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    val enabled: Boolean
        get() = Pref.isExplorerPlaybackHistoryEnabled

    fun get(file: File): ExplorerPlaybackProgressRecord? = synchronized(LOCK) {
        if (!enabled) return@synchronized null
        val key = entryKey(file)
        val encoded = preferences.getString(key, null)
        val record = ExplorerPlaybackHistoryPolicy.decode(encoded)
        if (encoded != null && record == null) preferences.edit().remove(key).apply()
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
            ExplorerPlaybackReportDecision.CLEAR -> preferences.edit().remove(key).apply()
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
                prune(editor, key, record)
                editor.apply()
            }
        }
    }

    private fun prune(
        editor: SharedPreferences.Editor,
        pendingKey: String,
        pendingRecord: ExplorerPlaybackProgressRecord,
    ) {
        val records = preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(ENTRY_PREFIX)) return@mapNotNull null
            if (key == pendingKey) return@mapNotNull key to pendingRecord
            val record = ExplorerPlaybackHistoryPolicy.decode(value as? String)
            if (record == null) {
                editor.remove(key)
                null
            } else {
                key to record
            }
        }.toMutableList()
        if (records.none { it.first == pendingKey }) records += pendingKey to pendingRecord
        records.sortedWith(compareBy<Pair<String, ExplorerPlaybackProgressRecord>> { it.second.updatedAtMillis }
            .thenBy { it.first })
            .take((records.size - ExplorerPlaybackHistoryPolicy.MAX_ENTRIES).coerceAtLeast(0))
            .forEach { (key, _) -> editor.remove(key) }
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
