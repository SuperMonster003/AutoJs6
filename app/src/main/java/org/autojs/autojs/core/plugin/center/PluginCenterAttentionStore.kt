package org.autojs.autojs.core.plugin.center

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.SettingsJson.SettingsJsonException
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.ATTENTION_INITIALIZED_SOURCES
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.ATTENTION_KNOWN_FIRST_INSTALL_TIMES
import org.autojs.autojs.core.pref.keys.PluginCenterKeys.ATTENTION_PINS
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set
import java.util.UUID

/**
 * Persists the [PluginCenterAttentionLedger] of this host, shared by the Plugin Center tab and page.
 * zh-CN: 持久化本宿主的 [PluginCenterAttentionLedger], 由插件中心标签页与独立页面共用.
 *
 * The ledger is the named store `plugin_center_attention`, three states of [org.autojs.autojs.core.pref.keys.PluginCenterKeys]
 * (docs/settings/ROADMAP.md P3.1, names unchanged); the JSON texts are written and parsed with [SettingsJson] (appendix
 * C Q40), in the same shape org.json wrote before.
 */
internal object PluginCenterAttentionStore {

    private const val TAG = "PluginCenterAttention"
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

    @Suppress("UNUSED_PARAMETER")
    private fun load(context: Context): PluginCenterAttentionLedger {
        return runCatching { read(processToken) }.getOrElse { error ->
            // An unreadable record starts over: the next comparison only records what is installed.
            // zh-CN: 记录无法读取时重新开始: 下一次比对只记录已安装的包.
            Log.w(TAG, "Cannot read the attention record", error)
            PluginCenterAttentionLedger(processToken)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun persist(context: Context, ledger: PluginCenterAttentionLedger) = write(ledger)

    /**
     * Reads the record; throws [SettingsJsonException] when a state is not the shape [write] gives it.
     * zh-CN: 读取记录; 某个状态不是 [write] 写出的形状时抛 [SettingsJsonException].
     */
    internal fun read(processToken: String): PluginCenterAttentionLedger {
        val known = ATTENTION_KNOWN_FIRST_INSTALL_TIMES.storedText()?.let { SettingsJson.OBJECT.decode(SettingsJson.parse(it)) }.orEmpty()
            .mapValues { (name, time) -> SettingsJson.toLong(time) ?: throw SettingsJsonException("No first install time of $name") }
        val pins = ATTENTION_PINS.storedText()?.let { SettingsJson.ARRAY.decode(SettingsJson.parse(it)) }.orEmpty().map { element ->
            val json = SettingsJson.OBJECT.decode(element)
            PluginCenterAttentionLedger.Pin(
                packageName = json["packageName"] as? String ?: throw SettingsJsonException("A pin without its package name"),
                createdAt = SettingsJson.toLong(json["createdAt"]) ?: throw SettingsJsonException("A pin without its creation time"),
                enableFailed = json["enableFailed"] == true,
                seenIn = (json["seenIn"] as? String)?.takeIf { it.isNotEmpty() },
            )
        }
        return PluginCenterAttentionLedger(processToken, known, ATTENTION_INITIALIZED_SOURCES.storedOrNull().orEmpty(), pins)
    }

    internal fun write(ledger: PluginCenterAttentionLedger) {
        val pins = ledger.pins().values.map { pin ->
            linkedMapOf<String, Any?>("packageName" to pin.packageName, "createdAt" to pin.createdAt, "enableFailed" to pin.enableFailed)
                .apply { pin.seenIn?.let { put("seenIn", it) } }
        }
        // One write of the three states, as the single SharedPreferences edit before.
        SettingsStores[NamedStores.PLUGIN_CENTER_ATTENTION].edit {
            this[ATTENTION_KNOWN_FIRST_INSTALL_TIMES] = ledger.knownFirstInstallTimes()
            this[ATTENTION_INITIALIZED_SOURCES] = ledger.initializedSources()
            this[ATTENTION_PINS] = pins
        }
    }
}
