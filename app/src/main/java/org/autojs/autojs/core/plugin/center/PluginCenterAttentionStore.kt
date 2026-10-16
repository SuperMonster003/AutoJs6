package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists the [PluginCenterAttentionLedger] of this host, shared by the Plugin Center tab and page.
 * zh-CN: 持久化本宿主的 [PluginCenterAttentionLedger], 由插件中心标签页与独立页面共用.
 */
internal object PluginCenterAttentionStore {

    private const val TAG = "PluginCenterAttention"
    private const val SP = "plugin_center_attention"
    private const val KEY_KNOWN = "known_first_install_times"
    private const val KEY_INITIALIZED = "initialized_sources"
    private const val KEY_PINS = "pins"

    private val processToken = UUID.randomUUID().toString()

    private var ledger: PluginCenterAttentionLedger? = null

    @Synchronized
    fun pins(context: Context): Map<String, PluginCenterAttentionLedger.Pin> = ledger(context).pins()

    @Synchronized
    fun reconcile(context: Context, source: String, observed: Map<String, Long?>): List<String> {
        val ledger = ledger(context)
        val initialized = source in ledger.initializedSources()
        val before = ledger.knownFirstInstallTimes()
        val fresh = ledger.reconcile(source, observed, System.currentTimeMillis())
        if (!initialized || fresh.isNotEmpty() || ledger.knownFirstInstallTimes() != before) {
            persist(context, ledger)
        }
        return fresh
    }

    @Synchronized
    fun recordFreshInstall(context: Context, packageName: String) {
        val firstInstallTime = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0).firstInstallTime
        }.getOrNull()
        val ledger = ledger(context)
        ledger.recordFreshInstall(packageName, firstInstallTime, System.currentTimeMillis())
        persist(context, ledger)
    }

    @Synchronized
    fun markEnableFailed(context: Context, packageName: String): Boolean = update(context) { markEnableFailed(packageName) }

    @Synchronized
    fun markSeen(context: Context, packageNames: Collection<String>): Boolean = update(context) { markSeen(packageNames) }

    @Synchronized
    fun endSession(context: Context): Boolean = update(context) { endSession() }

    @Synchronized
    fun forget(context: Context, packageName: String): Boolean = update(context) { forget(packageName) }

    private inline fun update(context: Context, change: PluginCenterAttentionLedger.() -> Boolean): Boolean {
        val ledger = ledger(context)
        return ledger.change().also { changed -> if (changed) persist(context, ledger) }
    }

    private fun ledger(context: Context): PluginCenterAttentionLedger = ledger ?: load(context).also { loaded ->
        ledger = loaded
        // Pins shown by an earlier process were dropped while loading. zh-CN: 载入时已丢弃更早进程展示过的置顶.
        persist(context, loaded)
    }

    private fun load(context: Context): PluginCenterAttentionLedger {
        val prefs = context.getSharedPreferences(SP, Context.MODE_PRIVATE)
        return runCatching {
            val known = JSONObject(prefs.getString(KEY_KNOWN, null) ?: "{}").let { json ->
                json.keys().asSequence().associateWith { json.getLong(it) }
            }
            val pins = JSONArray(prefs.getString(KEY_PINS, null) ?: "[]").let { array ->
                (0 until array.length()).map { index ->
                    val json = array.getJSONObject(index)
                    PluginCenterAttentionLedger.Pin(
                        packageName = json.getString("packageName"),
                        createdAt = json.getLong("createdAt"),
                        enableFailed = json.optBoolean("enableFailed"),
                        seenIn = json.optString("seenIn").takeIf { it.isNotEmpty() },
                    )
                }
            }
            PluginCenterAttentionLedger(processToken, known, prefs.getStringSet(KEY_INITIALIZED, null).orEmpty(), pins)
        }.getOrElse { error ->
            // An unreadable record starts over: the next comparison only records what is installed.
            // zh-CN: 记录无法读取时重新开始: 下一次比对只记录已安装的包.
            Log.w(TAG, "Cannot read the attention record", error)
            PluginCenterAttentionLedger(processToken)
        }
    }

    private fun persist(context: Context, ledger: PluginCenterAttentionLedger) {
        val known = JSONObject().apply { ledger.knownFirstInstallTimes().forEach { (name, time) -> put(name, time) } }
        val pins = JSONArray().apply {
            ledger.pins().values.forEach { pin ->
                put(JSONObject().apply {
                    put("packageName", pin.packageName)
                    put("createdAt", pin.createdAt)
                    put("enableFailed", pin.enableFailed)
                    pin.seenIn?.let { put("seenIn", it) }
                })
            }
        }
        context.getSharedPreferences(SP, Context.MODE_PRIVATE).edit {
            putString(KEY_KNOWN, known.toString())
            putStringSet(KEY_INITIALIZED, ledger.initializedSources())
            putString(KEY_PINS, pins.toString())
        }
    }
}
