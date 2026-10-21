package org.autojs.autojs.ui.edit.keyboard

import android.content.Context
import org.autojs.autojs.core.pref.keys.EditorKeyboardKeys
import org.json.JSONArray
import org.json.JSONObject

/**
 * Usage history of the Ace editor actions in the named store `ace_action_history`, the state
 * [EditorKeyboardKeys.ACE_ACTION_HISTORY] (docs/settings/ROADMAP.md P3.2, names unchanged); the JSON text is still
 * written and parsed with org.json here.
 */
object AceActionHistoryStore {

    private const val VERSION = 1
    private const val DEFAULT_MANUAL_ORDER = Int.MAX_VALUE
    private const val LEGACY_SHORTCUT_ACTION_PREFIX = "command:"
    private const val DEPRECATED_CANCEL_SEARCH_ACTION_ID = "editor:cancel_search"

    data class Entry(
        val id: String,
        val label: String,
        val mutatesText: Boolean,
        val payloadJson: String? = null,
        val payloadSummary: String? = null,
        val usageCount: Int,
        val firstUsedAt: Long,
        val lastUsedAt: Long,
        val pinned: Boolean = false,
        val pinnedAt: Long = 0L,
        val manualOrder: Int = DEFAULT_MANUAL_ORDER,
    )

    data class Action(
        val id: String,
        val label: String,
        val mutatesText: Boolean,
        val payloadJson: String? = null,
        val payloadSummary: String? = null,
    )

    fun record(context: Context, action: AceShortcutPanel.Action, now: Long = System.currentTimeMillis()) {
        record(
            context = context,
            action = Action(
                id = action.id,
                label = action.label,
                mutatesText = action.mutatesText,
            ),
            now = now,
        )
    }

    fun record(context: Context, action: Action, now: Long = System.currentTimeMillis()) {
        val root = readRoot(context)
        val entries = readEntries(root).toMutableMap()
        val current = entries[action.id]
        entries[action.id] = if (current == null) {
            Entry(
                id = action.id,
                label = action.label,
                mutatesText = action.mutatesText,
                payloadJson = action.payloadJson,
                payloadSummary = action.payloadSummary,
                usageCount = 1,
                firstUsedAt = now,
                lastUsedAt = now,
            )
        } else {
            current.copy(
                label = action.label,
                mutatesText = action.mutatesText,
                payloadJson = action.payloadJson ?: current.payloadJson,
                payloadSummary = action.payloadSummary ?: current.payloadSummary,
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

    fun getLastAction(context: Context): Action? {
        val root = readRoot(context)
        val id = root.optString("lastActionId").takeIf { it.isNotBlank() } ?: return null
        return readEntries(root)[id]?.toAction()
    }

    fun getRankedEntries(context: Context): List<Entry> {
        return readEntries(readRoot(context)).values.sortedWith(entryComparator())
    }

    fun setPinned(context: Context, id: String, pinned: Boolean, now: Long = System.currentTimeMillis()) {
        val root = readRoot(context)
        val entries = readEntries(root).toMutableMap()
        val current = entries[id] ?: return
        entries[id] = current.copy(
            pinned = pinned,
            pinnedAt = if (pinned) now else 0L,
            manualOrder = current.manualOrder,
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

    fun applyFavoritesOrder(context: Context, orderedIds: List<String>, pinnedCount: Int) {
        val root = readRoot(context)
        val entries = readEntries(root).toMutableMap()
        val now = System.currentTimeMillis()
        val safePinnedCount = pinnedCount.coerceIn(0, orderedIds.size)
        orderedIds.forEachIndexed { index, id ->
            val current = entries[id] ?: return@forEachIndexed
            val pinned = index < safePinnedCount
            entries[id] = current.copy(
                pinned = pinned,
                pinnedAt = if (pinned) current.pinnedAt.takeIf { it > 0L } ?: now else 0L,
                manualOrder = if (pinned) index else index - safePinnedCount,
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

    @Suppress("UNUSED_PARAMETER")
    fun clear(context: Context) {
        EditorKeyboardKeys.ACE_ACTION_HISTORY.remove()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun readRoot(context: Context): JSONObject {
        val raw = EditorKeyboardKeys.ACE_ACTION_HISTORY.storedText()
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

    @Suppress("UNUSED_PARAMETER")
    private fun persistRoot(context: Context, root: JSONObject, entries: Collection<Entry>) {
        val array = JSONArray()
        entries.sortedBy { it.id }.forEach { array.put(it.toJson()) }
        root.put("version", VERSION)
        root.put("entries", array)
        EditorKeyboardKeys.ACE_ACTION_HISTORY.setStoredText(root.toString())
    }

    private fun JSONObject.toEntry(): Entry? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        if (isDeprecatedActionId(id)) {
            return null
        }
        val label = optString("label").takeIf { it.isNotBlank() } ?: id
        val usageCount = optInt("usageCount", 0).coerceAtLeast(0)
        if (usageCount <= 0) {
            return null
        }
        val shortcutAction = AceShortcutPanel.actionById(id)
        return Entry(
            id = id,
            label = label,
            mutatesText = optBoolean("mutatesText", shortcutAction?.mutatesText ?: false),
            payloadJson = optString("payloadJson").takeIf { it.isNotBlank() },
            payloadSummary = optString("payloadSummary").takeIf { it.isNotBlank() },
            usageCount = usageCount,
            firstUsedAt = optLong("firstUsedAt", 0L),
            lastUsedAt = optLong("lastUsedAt", 0L),
            pinned = optBoolean("pinned", false),
            pinnedAt = optLong("pinnedAt", 0L),
            manualOrder = optInt("manualOrder", DEFAULT_MANUAL_ORDER),
        )
    }

    private fun isDeprecatedActionId(id: String): Boolean {
        return id.startsWith(LEGACY_SHORTCUT_ACTION_PREFIX) ||
            id == DEPRECATED_CANCEL_SEARCH_ACTION_ID
    }

    private fun Entry.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("label", label)
            put("mutatesText", mutatesText)
            payloadJson?.takeIf { it.isNotBlank() }?.let { put("payloadJson", it) }
            payloadSummary?.takeIf { it.isNotBlank() }?.let { put("payloadSummary", it) }
            put("usageCount", usageCount)
            put("firstUsedAt", firstUsedAt)
            put("lastUsedAt", lastUsedAt)
            put("pinned", pinned)
            put("pinnedAt", pinnedAt)
            put("manualOrder", manualOrder)
        }
    }

    private fun Entry.toAction(): Action = Action(
        id = id,
        label = label,
        mutatesText = mutatesText,
        payloadJson = payloadJson,
        payloadSummary = payloadSummary,
    )

    private fun entryComparator() = Comparator<Entry> { left, right ->
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
        return compareValues(right.lastUsedAt, left.lastUsedAt).takeIf { it != 0 }
            ?: compareValues(left.label, right.label).takeIf { it != 0 }
            ?: compareValues(left.id, right.id)
    }
}
