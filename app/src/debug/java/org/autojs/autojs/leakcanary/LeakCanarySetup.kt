package org.autojs.autojs.leakcanary

import android.app.Application
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
