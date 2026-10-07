package org.autojs.autojs.theme

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * What "Undo script change" restores (D11, roadmap P6.3): the selection before the first of a run of script changes,
 * its theme color, and the intelligent library that was active then (its snapshot and runtime as stored by the engine).
 * zh-CN: "撤销脚本修改" 恢复的内容 (D11, Roadmap P6.3): 一连串脚本修改中第一次之前的选中状态, 当时的主题色,
 * 以及当时激活的智能库 (引擎保存的快照与运行状态).
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
data class ScriptUndoSnapshot(
    val libraryId: Int,
    val itemId: Int,
    val legacyIndex: Int,
    val customColor: Int?,
    val libraryName: String?,
    val itemName: String?,
    val primary: Int,
    val dark: Int,
    val accent: Int,
    val intelligentSnapshot: String? = null,
    val intelligentRuntime: String? = null,
) {

    fun encode(): String = JsonObject().apply {
        addProperty("libraryId", libraryId)
        addProperty("itemId", itemId)
        addProperty("legacyIndex", legacyIndex)
        customColor?.let { addProperty("customColor", it) }
        libraryName?.let { addProperty("libraryName", it) }
        itemName?.let { addProperty("itemName", it) }
        addProperty("primary", primary)
        addProperty("dark", dark)
        addProperty("accent", accent)
        intelligentSnapshot?.let { addProperty("intelligentSnapshot", it) }
        intelligentRuntime?.let { addProperty("intelligentRuntime", it) }
    }.toString()

    companion object {

        /** Null for missing or damaged data. zh-CN: 数据缺失或损坏时返回 null. */
        fun decode(text: String?): ScriptUndoSnapshot? = runCatching {
            val json = JsonParser.parseString(text ?: return null).asJsonObject
            fun int(key: String) = json.get(key)?.takeIf { it.isJsonPrimitive }?.asInt
            fun string(key: String) = json.get(key)?.takeIf { it.isJsonPrimitive }?.asString
            ScriptUndoSnapshot(
                libraryId = int("libraryId")!!,
                itemId = int("itemId")!!,
                legacyIndex = int("legacyIndex")!!,
                customColor = int("customColor"),
                libraryName = string("libraryName"),
                itemName = string("itemName"),
                primary = int("primary")!!,
                dark = int("dark")!!,
                accent = int("accent")!!,
                intelligentSnapshot = string("intelligentSnapshot"),
                intelligentRuntime = string("intelligentRuntime"),
            )
        }.getOrNull()

    }

}

/**
 * When the undo snapshot is taken and dropped: the first script change of a run takes it, later script changes and
 * intelligent rotations keep it, and any explicit choice, activation or restore drops it, so the undo menu shows only
 * while the latest explicit change came from a script.
 * zh-CN: 撤销快照的保存与丢弃时机: 一连串脚本修改中的第一次保存快照, 之后的脚本修改与智能换组保留快照,
 * 任何显式选择, 激活或恢复都会丢弃快照, 因此只有最近一次显式修改来自脚本时才显示撤销菜单.
 */
object ScriptUndoRules {

    fun capturesBefore(source: ThemeColorSource, hasSnapshot: Boolean) = source == ThemeColorSource.SCRIPT && !hasSnapshot

    fun dropsAfter(source: ThemeColorSource) = when (source) {
        ThemeColorSource.USER, ThemeColorSource.ACTIVATION, ThemeColorSource.RESTORE -> true
        ThemeColorSource.SCRIPT, ThemeColorSource.INTELLIGENT -> false
    }

}
