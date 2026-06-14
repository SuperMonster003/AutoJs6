package org.autojs.autojs.ui.edit.editor.ace

import org.json.JSONArray
import org.json.JSONObject

data class AceTextDelta(
    val action: Action,
    val start: Position,
    val end: Position,
    val lines: List<String>,
) {

    enum class Action {
        INSERT,
        REMOVE,
    }

    data class Position(
        val row: Int,
        val column: Int,
    )

    val text: String
        get() = lines.joinToString("\n")

    companion object {
        fun fromJson(payloadJson: String?): AceTextDelta? {
            if (payloadJson.isNullOrBlank()) {
                return null
            }
            return runCatching {
                val obj = JSONObject(payloadJson)
                val action = when (obj.optString("action")) {
                    "insert" -> Action.INSERT
                    "remove" -> Action.REMOVE
                    else -> return@runCatching null
                }
                AceTextDelta(
                    action = action,
                    start = obj.optJSONObject("start").toPosition(),
                    end = obj.optJSONObject("end").toPosition(),
                    lines = obj.optJSONArray("lines").toStringList(),
                )
            }.getOrNull()
        }

        private fun JSONObject?.toPosition(): Position {
            return Position(
                row = this?.optInt("row") ?: 0,
                column = this?.optInt("column") ?: 0,
            )
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) {
                return emptyList()
            }
            return buildList {
                for (i in 0 until length()) {
                    add(optString(i))
                }
            }
        }
    }
}
