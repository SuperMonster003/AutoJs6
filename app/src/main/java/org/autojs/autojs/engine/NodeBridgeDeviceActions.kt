package org.autojs.autojs.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import org.json.JSONArray
import org.json.JSONObject

/** Android application queries, separate from the script package installation catalog. */
internal object NodeBridgeDeviceActions {
    const val MAX_APPS = 1000

    fun listApps(context: Context, options: JSONObject?): JSONObject {
        require(options == null || options.keys().asSequence().all { it == "query" }) { "unknown app list option" }
        val rawQuery: Any? = options?.opt("query")
        val query = when (rawQuery) {
            null -> ""
            is String -> rawQuery
            else -> throw IllegalArgumentException("query must be a string")
        }
        require(query.toByteArray(Charsets.UTF_8).size <= 512) { "query exceeds 512 bytes" }
        val manager = context.packageManager
        val matches = manager.getInstalledApplications(0).sortedBy { it.packageName }.mapNotNull { app ->
            val label = manager.getApplicationLabel(app).toString()
            if (!app.packageName.contains(query, true) && !label.contains(query, true)) null else JSONObject()
                .put("packageName", app.packageName).put("appName", label).put("enabled", app.enabled)
                .put("system", app.flags and ApplicationInfo.FLAG_SYSTEM != 0)
        }
        return JSONObject().put("schema", "autojs6-bridge-android-apps-v1").put("query", query)
            .put("count", matches.size.coerceAtMost(MAX_APPS)).put("truncated", matches.size > MAX_APPS)
            .put("visibility", "visible_to_host").put("apps", JSONArray(matches.take(MAX_APPS)))
    }

    /** The public editor jump uses one-based positions. */
    fun editorPosition(options: JSONObject): Pair<Int, Int> = editorPosition(options.keys().asSequence().associateWith { options.get(it) })

    fun editorPosition(options: Map<String, Any?>): Pair<Int, Int> {
        require(options.keys.all { it == "line" || it == "column" }) { "unknown editor option" }
        fun position(key: String): Int {
            val value = if (key in options) options[key] else return 1
            require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble() && value.toLong() in 1L..1_000_000L) {
                "$key must be an integer from 1 to 1000000"
            }
            return value.toInt()
        }
        return position("line") to position("column")
    }
}
