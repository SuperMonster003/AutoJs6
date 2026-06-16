package org.autojs.autojs.ui.edit.keyboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object AceActionHistoryStore {

    private const val PREF_NAME = "ace_action_history"
    private const val KEY_STATE_JSON = "state_json"
    private const val VERSION = 1
    private const val DEFAULT_MANUAL_ORDER = Int.MAX_VALUE

    data class Entry(
        val id: String,
        val label: String,
        val usageCount: Int,
        val firstUsedAt: Long,
        val lastUsedAt: Long,
        val pinned: Boolean = false,
        val pinnedAt: Long = 0L,
        val manualOrder: Int = DEFAULT_MANUAL_ORDER,
    )

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun record(context: Context, action: AceShortcutPanel.Action, now: Long = System.currentTimeMillis()) {
        val root = readRoot(context)
        val entries = readEntries(root).toMutableMap()
        val current = entries[action.id]
        entries[action.id] = if (current == null) {
            Entry(
                id = action.id,
                label = action.label,
                usageCount = 1,
                firstUsedAt = now,
                lastUsedAt = now,
            )
        } else {
            current.copy(
                label = action.label,
                usageCount = current.usageCount + 1,
                lastUsedAt = now,
            )
        }
        root.put("lastActionId", action.id)
        persistRoot(context, root, entries.values)
    }

    fun getLastActionId(context: Context): String? {
        return readRoot(context).optString("lastActionId").takeIf { it.isNotBlank() }
    }

    fun getRankedEntries(context: Context): List<Entry> {
        return readEntries(readRoot(context)).values.sortedWith(ENTRY_COMPARATOR)
    }

    fun setPinned(context: Context, id: String, pinned: Boolean, now: Long = System.currentTimeMillis()) {
        val root = readRoot(context)
        val entries = readEntries(root).toMutableMap()
        val current = entries[id] ?: return
        entries[id] = current.copy(
            pinned = pinned,
            pinnedAt = if (pinned) now else 0L,
            manualOrder = if (pinned) current.manualOrder else DEFAULT_MANUAL_ORDER,
        )
        persistRoot(context, root, entries.values)
    }

    fun reorderPinned(context: Context, orderedIds: List<String>) {
        val root = readRoot(context)
        val entries = readEntries(root).toMutableMap()
        orderedIds.forEachIndexed { index, id ->
            val current = entries[id] ?: return@forEachIndexed
            entries[id] = current.copy(
                pinned = true,
                manualOrder = index,
                pinnedAt = current.pinnedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
        }
        persistRoot(context, root, entries.values)
    }

    fun delete(context: Context, id: String) {
        val root = readRoot(context)
        val entries = readEntries(root).toMutableMap()
        entries.remove(id)
        if (root.optString("lastActionId") == id) {
            root.remove("lastActionId")
        }
        persistRoot(context, root, entries.values)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_STATE_JSON).apply()
    }

    private fun readRoot(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_STATE_JSON, null)
        return runCatching {
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        }.getOrDefault(JSONObject()).also { root ->
            root.put("version", VERSION)
        }
    }

    private fun readEntries(root: JSONObject): Map<String, Entry> {
        val array = root.optJSONArray("entries") ?: JSONArray()
        return (0 until array.length())
            .mapNotNull { index -> array.optJSONObject(index)?.toEntry() }
            .associateBy { it.id }
    }

    private fun persistRoot(context: Context, root: JSONObject, entries: Collection<Entry>) {
        val array = JSONArray()
        entries.sortedBy { it.id }.forEach { array.put(it.toJson()) }
        root.put("version", VERSION)
        root.put("entries", array)
        prefs(context).edit().putString(KEY_STATE_JSON, root.toString()).apply()
    }

    private fun JSONObject.toEntry(): Entry? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val label = optString("label").takeIf { it.isNotBlank() } ?: id
        val usageCount = optInt("usageCount", 0).coerceAtLeast(0)
        if (usageCount <= 0) {
            return null
        }
        return Entry(
            id = id,
            label = label,
            usageCount = usageCount,
            firstUsedAt = optLong("firstUsedAt", 0L),
            lastUsedAt = optLong("lastUsedAt", 0L),
            pinned = optBoolean("pinned", false),
            pinnedAt = optLong("pinnedAt", 0L),
            manualOrder = optInt("manualOrder", DEFAULT_MANUAL_ORDER),
        )
    }

    private fun Entry.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("label", label)
            put("usageCount", usageCount)
            put("firstUsedAt", firstUsedAt)
            put("lastUsedAt", lastUsedAt)
            put("pinned", pinned)
            put("pinnedAt", pinnedAt)
            put("manualOrder", manualOrder)
        }
    }

    private val ENTRY_COMPARATOR = Comparator<Entry> { left, right ->
        compareValuesBy(left, right) { if (it.pinned) 0 else 1 }
            .takeIf { it != 0 }
            ?: when {
                left.pinned && right.pinned -> comparePinned(left, right)
                else -> compareUnpinned(left, right)
            }
    }

    private fun comparePinned(left: Entry, right: Entry): Int {
        return compareValues(left.manualOrder, right.manualOrder)
            .takeIf { it != 0 }
            ?: compareValues(right.pinnedAt, left.pinnedAt).takeIf { it != 0 }
            ?: compareValues(left.label, right.label).takeIf { it != 0 }
            ?: compareValues(left.id, right.id)
    }

    private fun compareUnpinned(left: Entry, right: Entry): Int {
        return compareValues(right.usageCount, left.usageCount)
            .takeIf { it != 0 }
            ?: compareValues(right.lastUsedAt, left.lastUsedAt).takeIf { it != 0 }
            ?: compareValues(left.label, right.label).takeIf { it != 0 }
            ?: compareValues(left.id, right.id)
    }
}
