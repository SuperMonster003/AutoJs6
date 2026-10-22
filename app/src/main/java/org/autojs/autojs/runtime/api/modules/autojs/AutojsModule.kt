package org.autojs.autojs.runtime.api.modules.autojs

import android.Manifest
import android.content.pm.PackageManager
import android.provider.Settings
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs.util.IntentUtils
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RootUtils
import org.autojs.autojs.util.RootUtils.RootMode
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Wrapper
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import android.content.Context as AndroidContext
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `autojs` module on the new script module layer (roadmap P3.4): the properties, functions and getters of the
 * current module in its order, `R` also as a global getter, and the sub-module `version` ([AutojsVersionModule]). The host
 * reaches the module object through `ScriptRuntime.augmentedAutojs` (`ScriptModuleMounter.moduleObject("autojs")` when the
 * new layer is on), and `app.autojs` links to it ([org.autojs.autojs.runtime.api.modules.app.AppModule]).
 *
 * `setRootMode` also takes a `RootMode` value, as documented (the current module and the obsolete JS refused it); a mode of
 * another type is a TypeError and an unknown mode a RangeError, `restart` / `exit` scripts that are neither a string nor an
 * array a TypeError (D10; the current module threw IllegalArgumentException).
 *
 * zh-CN: 新脚本模块层上的 autojs 模块 (P3.4): 属性, 函数与 getter 同现有模块及其顺序, R 也是全局 getter, 子模块 version. 宿主经
 * ScriptRuntime.augmentedAutojs 取模块对象 (新层开启时为 ScriptModuleMounter.moduleObject("autojs")), app.autojs 指向它. setRootMode 按文档也接受
 * RootMode 值 (现有模块与旧 JS 都拒绝); 其他类型的模式为 TypeError, 未知模式为 RangeError, restart / exit 的脚本既不是字符串也不是数组时为 TypeError
 * (D10; 现有模块抛 IllegalArgumentException).
 */
@ScriptModule(name = "autojs")
class AutojsModule(private val runtime: ScriptRuntime) {

    private val cachedResources = ConcurrentHashMap<String, ProxyObject>()

    @ScriptProperty
    val versionCode: Int = BuildConfig.VERSION_CODE

    @ScriptProperty
    val versionName: String = BuildConfig.VERSION_NAME

    @ScriptProperty
    val versionDate: String = BuildConfig.VERSION_DATE

    @ScriptProperty
    val name: String
        get() = GlobalAppContext.get().resources.getString(R.string.app_name)

    @ScriptProperty
    val packageName: String
        get() = GlobalAppContext.get().packageName

    @ScriptFunction
    fun isScreenPortrait(): Boolean = ScreenMetrics.isScreenPortrait

    @ScriptFunction
    fun isScreenLandscape(): Boolean = ScreenMetrics.isScreenLandscape

    @ScriptFunction
    fun isRootAvailable(): Boolean = RootUtils.isRootAvailable()

    @ScriptFunction
    fun getRootMode(): RootMode = RootUtils.getRootMode()

    /**
     * Sets the root mode from a `RootMode`, 1 / 0 / -1, true / false or 'root' / 'non-root' / 'auto' (ignoring case and
     * surrounding blanks); [isWriteIntoPreference] (true, or a string matching 'write_into_pref') also stores it in the
     * settings, anything else does not, as with the current module.
     */
    @ScriptFunction
    fun setRootMode(rootMode: Any?, isWriteIntoPreference: Any? = null) {
        val write = when (val value = unwrap(isWriteIntoPreference)) {
            is Boolean -> value
            is CharSequence -> matches(value, "write_into_pref")
            else -> false
        }
        RootUtils.setRootMode(rootModeOf(rootMode), write)
    }

    @ScriptFunction
    fun canModifySystemSettings(): Boolean = Settings.System.canWrite(GlobalAppContext.get())

    @ScriptFunction
    fun canWriteSecureSettings(): Boolean =
        GlobalAppContext.get().checkCallingOrSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    @ScriptFunction
    fun canDisplayOverOtherApps(): Boolean = Settings.canDrawOverlays(GlobalAppContext.get())

    @ScriptFunction
    fun getLanguage(): Locale = Language.getPrefLanguage().locale

    @ScriptFunction
    fun getLanguageTag(): String = getLanguage().toLanguageTag()

    @ScriptFunction
    fun restart(scripts: Any? = null) {
        val names = scriptNames("autojs.restart", scripts)
        if (names == null) IntentUtils.App.restart(GlobalAppContext.get()) else IntentUtils.App.restart(GlobalAppContext.get(), scriptsAfterRestart = names)
    }

    @ScriptFunction
    fun exit(scripts: Any? = null) {
        val names = scriptNames("autojs.exit", scripts)
        if (names == null) IntentUtils.App.exit(GlobalAppContext.get()) else IntentUtils.App.exit(GlobalAppContext.get(), scriptsAfterRestart = names)
    }

    /** The application resources by type (`R.string.app_name`), a new proxy on every read as with the current module. */
    @ScriptGetter(name = "R", global = ScriptGlobal.ALSO)
    fun resources(): Any = proxyOf(RhinoUtils.newBaseFunction(runtime.topLevelScope, PROXY_GETTER_KEY, { args ->
        ArgumentGuards.ensureArgumentsOnlyOne(args) { type ->
            val niceType = RhinoUtils.coerceString(type).replace("strings", "string", true)
            cachedResources.getOrPut(niceType) {
                proxyOf(RhinoUtils.newBaseFunction(runtime.topLevelScope, PROXY_GETTER_KEY, { names ->
                    val name = RhinoUtils.coerceString(names[0])
                    val context = runtime.topLevelScope.globalThis.prop("activity") as? AndroidContext ?: GlobalAppContext.get()
                    context.resources.getIdentifier(name, niceType, context.packageName)
                }, RhinoUtils.NOT_CONSTRUCTABLE))
            }
        }
    }, RhinoUtils.NOT_CONSTRUCTABLE))

    @ScriptGetter
    val rotation: Int
        get() = ScreenMetrics.rotation

    @ScriptGetter
    val orientation: Int
        get() = ScreenMetrics.orientation

    @ScriptGetter
    val themeColor: ThemeColor
        get() = ThemeColorManager.currentThemeColor

    private fun proxyOf(getter: BaseFunction): ProxyObject {
        val scope = runtime.topLevelScope
        return ProxyObject(scope.globalThis, RhinoUtils.newNativeObject(scope).also { it.defineProp(PROXY_GETTER_KEY, getter) })
    }

    private fun rootModeOf(mode: Any?): RootMode = when (val value = unwrap(mode)) {
        is RootMode -> value
        is Number -> when (value.toDouble()) {
            1.0 -> RootMode.FORCE_ROOT
            0.0 -> RootMode.FORCE_NON_ROOT
            -1.0 -> RootMode.AUTO_DETECT
            else -> throw GlobalValues.rangeError("autojs.setRootMode", "rootMode", ROOT_MODES, mode)
        }
        is Boolean -> if (value) RootMode.FORCE_ROOT else RootMode.FORCE_NON_ROOT
        is CharSequence -> when {
            matches(value, "root") -> RootMode.FORCE_ROOT
            matches(value, "non-root") -> RootMode.FORCE_NON_ROOT
            matches(value, "auto") -> RootMode.AUTO_DETECT
            else -> throw GlobalValues.rangeError("autojs.setRootMode", "rootMode", ROOT_MODES, mode)
        }
        else -> throw GlobalValues.typeError("autojs.setRootMode", "rootMode", "RootMode | number | boolean | string", mode)
    }

    /** The scripts to run after a restart (trimmed, `.js` appended, "@" for the current script), or null for none. */
    private fun scriptNames(function: String, scripts: Any?): List<String>? = when (val value = unwrap(scripts)) {
        null -> null
        is CharSequence -> listOf(scriptName(value.toString()))
        is NativeArray -> value.mapNotNull { e -> if (isNullish(e)) null else scriptName(RhinoScriptRuntime.toString(e)) }
        is List<*> -> value.mapNotNull { e -> e?.let { scriptName(it.toString()) } }
        else -> if (isNullish(value)) null else throw GlobalValues.typeError(function, "scripts", "string | string[]", scripts)
    }

    private fun scriptName(name: String): String {
        val jsExt = FileUtils.TYPE.JAVASCRIPT.extensionWithDot
        return when (val s = name.trim()) {
            "@" -> runtime.engines.myEngine().source.fullPath
            else -> if (s.endsWith(jsExt)) s else "$s$jsExt"
        }
    }

    private fun unwrap(value: Any?): Any? = when (value) {
        is Wrapper -> value.unwrap()
        is ConsString -> value.toString()
        else -> value
    }

    private companion object {

        const val ROOT_MODES = "RootMode | 1 | 0 | -1 | true | false | 'root' | 'non-root' | 'auto'"

        /** The current `util.checkStringArgument(value, pattern)` for a plain pattern: whole match, ignoring case and blanks. */
        fun matches(value: CharSequence, pattern: String): Boolean = value.toString().trim().equals(pattern, ignoreCase = true)

    }

}
