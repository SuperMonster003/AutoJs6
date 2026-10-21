package org.autojs.autojs.leakcanary

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.get
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.runtime.memory.LeakCanaryStatus
import org.autojs.autojs6.R

/**
 * LeakCanary in debug builds (docs/memory/ROADMAP.md D5 / T5): heap dumps follow the developer options switch,
 * and the objects of finished scripts are handed to the ObjectWatcher through [watch] / [watchEngine].
 * LeakCanary is included unless the Gradle property `autojs.leakcanary.enabled` is false; its classes are then only on
 * the compile classpath and every call here becomes a no-op.
 *
 * zh-CN: debug 构建中的 LeakCanary (docs/memory/ROADMAP.md D5 / T5): 堆转储跟随开发者选项开关, 已结束脚本的对象经 [watch] / [watchEngine]
 * 交给 ObjectWatcher. 除非 Gradle 属性 `autojs.leakcanary.enabled` 为 false, 否则包含 LeakCanary; 为 false 时其类只在编译类路径上, 这里的调用全部为空操作.
 */
object LeakCanarySetup {

    private const val TAG = "LeakCanarySetup"

    /**
     * Developer options > Memory diagnostics > LeakCanary (shown in debug builds only).
     * zh-CN: 开发者选项 > 内存诊断 > LeakCanary (仅 debug 构建显示).
     */
    @JvmField
    val ENABLED = SettingKey.Bool(R.string.key_leakcanary_enabled, SettingCategory.DEVELOPER, SettingDefault.of(true))

    private val isIncluded: Boolean by lazy {
        runCatching { Class.forName("leakcanary.LeakCanary") }.isSuccess
    }

    @Volatile
    private var isEnabled = false

    fun setup(application: Application) {
        setUpStrictMode()
        if (!isIncluded) {
            Log.d(TAG, "LeakCanary isn't included in this debug build")
            return
        }
        defaultAutomaticHeapDumpOff(application)
        val isEnabledByResource = application.resources.getBoolean(R.bool.leak_canary_enabled)
        val isEnabledBySetting = runCatching { SettingsStores.default[ENABLED] }.getOrDefault(true)
        apply(isEnabledByResource && isEnabledBySetting)
    }

    /**
     * Applies the developer options switch: when off, script objects are no longer watched and LeakCanary does not dump the heap.
     * zh-CN: 应用开发者选项开关: 关闭时不再监视脚本对象, LeakCanary 也不转储堆.
     */
    @JvmStatic
    fun apply(enabled: Boolean) {
        isEnabled = enabled
        if (!isIncluded) return
        runCatching {
            LeakCanary.config = LeakCanary.config.copy(dumpHeap = enabled)
            LeakCanary.showLeakDisplayActivityLauncherIcon(enabled)
        }.onSuccess {
            Log.d(TAG, "LeakCanary has been set up as ${if (enabled) "enabled" else "disabled"}")
        }.onFailure {
            Log.w(TAG, "LeakCanary is included but couldn't be set up", it)
        }
    }

    /**
     * Expects [watchedObject] to become weakly reachable soon; LeakCanary reports it with its reference chain otherwise.
     * [description] must not reference script objects.
     * zh-CN: 预期 [watchedObject] 很快变为弱可达, 否则 LeakCanary 报告它及其引用链. [description] 不得引用脚本对象.
     */
    @JvmStatic
    fun watch(watchedObject: Any, description: String) {
        if (!isEnabled || !isIncluded) return
        runCatching { AppWatcher.objectWatcher.expectWeaklyReachable(watchedObject, description) }
            .onFailure { Log.w(TAG, "Failed to watch $description", it) }
    }

    /**
     * Watches a removed script engine and, for a JavaScript engine, its script runtime (T5).
     * zh-CN: 监视已移除的脚本引擎, 对 JavaScript 引擎还监视其脚本运行时 (T5).
     */
    @JvmStatic
    fun watchEngine(engine: ScriptEngine<*>) {
        if (!isEnabled || !isIncluded) return
        val name = runCatching { engine.getTag(ScriptEngine.TAG_SOURCE)?.toString() }.getOrNull() ?: "unknown"
        val label = "${engine.javaClass.simpleName} #${engine.id} ($name)"
        watch(engine, "$label removed")
        if (engine is JavaScriptEngine) {
            runCatching { engine.runtime }.getOrNull()?.let { watch(it, "ScriptRuntime of $label exited") }
        }
    }

    /**
     * What LeakCanary is doing, for the unreclaimed runtime warning and the memory diagnostics page (docs/memory/ROADMAP.md P2.3):
     * whether it watches, how many watched objects are retained, and whether it dumps and analyzes the heap by itself (its own
     * "Dump heap automatically" switch, read from the same store as [defaultAutomaticHeapDumpOff]). Null when it is not included.
     * zh-CN: LeakCanary 的状态, 供未回收警告与内存诊断页面使用 (docs/memory/ROADMAP.md P2.3): 是否在监视, 监视对象中有多少残留, 以及是否会自行转储并分析堆
     * (其自身的 "Dump heap automatically" 开关, 与 [defaultAutomaticHeapDumpOff] 读写同一存储). 未包含 LeakCanary 时为 null.
     */
    @JvmStatic
    fun status(context: Context): LeakCanaryStatus? {
        if (!isIncluded) return null
        if (!isEnabled) return LeakCanaryStatus(enabled = false, retainedObjects = 0, automaticHeapDump = false)
        return runCatching {
            val aboutScreenDumpEnabled = context.getSharedPreferences(LEAK_CANARY_HEAP_DUMP_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ABOUT_SCREEN_DUMP_ENABLED, true)
            LeakCanaryStatus(
                enabled = true,
                retainedObjects = AppWatcher.objectWatcher.retainedObjectCount,
                automaticHeapDump = LeakCanary.config.dumpHeap && aboutScreenDumpEnabled,
            )
        }.onFailure { Log.w(TAG, "Failed to read the LeakCanary status", it) }.getOrNull()
    }

    /**
     * LeakCanary's own "Dump heap automatically" switch (About screen of its Leaks app) is on by default, so a few retained
     * objects froze AutoJs6 for a heap dump during ordinary debugging. It is turned off the first time; LeakCanary still watches
     * and shows a notification with the retained object count, "Dump Heap Now" in the Leaks app dumps on demand, and a choice
     * made later on that screen is kept. Store and key are those of LeakCanary 2.14 (InternalLeakCanary.dumpEnabledInAboutScreen).
     * zh-CN: LeakCanary 自身的 "Dump heap automatically" 开关 (其 Leaks 应用的 About 页) 默认开启, 日常调试时少量残留对象就会让 AutoJs6 因转储堆而短暂冻结.
     * 首次运行时将其关闭; LeakCanary 仍会监视并以通知显示残留对象数, 可在 Leaks 应用中用 "Dump Heap Now" 按需转储, 之后在该页面所作的选择保持不变.
     * 存储与键名来自 LeakCanary 2.14 (InternalLeakCanary.dumpEnabledInAboutScreen).
     */
    private fun defaultAutomaticHeapDumpOff(application: Application) {
        runCatching {
            val prefs = application.getSharedPreferences(LEAK_CANARY_HEAP_DUMP_PREFS, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY_ABOUT_SCREEN_DUMP_ENABLED)) {
                prefs.edit().putBoolean(KEY_ABOUT_SCREEN_DUMP_ENABLED, false).apply()
            }
        }.onFailure { Log.w(TAG, "Failed to turn off automatic heap dumps of LeakCanary", it) }
    }

    private const val LEAK_CANARY_HEAP_DUMP_PREFS = "LeakCanaryHeapDumpPrefs"
    private const val KEY_ABOUT_SCREEN_DUMP_ENABLED = "AboutScreenDumpEnabled"

    // Leak checks of StrictMode as log hints besides the memory gate (docs/memory/ROADMAP.md 1.4 / P1.2).
    // zh-CN: StrictMode 的泄漏检查, 作为内存门禁之外的日志线索 (docs/memory/ROADMAP.md 1.4 / P1.2).
    private fun setUpStrictMode() {
        runCatching {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy())
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build(),
            )
        }.onFailure { Log.w(TAG, "Failed to set up StrictMode leak checks", it) }
    }

}
