package org.autojs.autojs.runtime.api.modules.app

import android.content.Intent
import android.net.Uri
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AppUtils.Companion.BroadcastShortForm
import org.autojs.autojs.runtime.api.augment.app.App
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs6.BuildConfig
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper
import java.net.URI
import org.autojs.autojs.util.App as PresetApp

/**
 * The `app` module on the new script module layer (roadmap P3.4): the module object's prototype is the runtime's
 * `AppUtils` (`ScriptRuntime.app`), the properties and functions (22 of them also global, the settings launchers with
 * their aliases) come in the order of the current module, and `app.autojs` is the `autojs` module object, defined when the
 * module object is built (a lazy property, so building `app` does not build `autojs`; formerly set by
 * `ScriptRuntime.augment()` after every module was mounted).
 *
 * The work is still done by the current module's functions (`augment/app/App`, Android intents, package manager and
 * shells; to be moved out before P5.2), called with the arguments as given. This layer adds the signatures (T4) and the
 * argument types of D10: a target, intent or options value of an undocumented type is a TypeError, an unknown broadcast
 * short form a RangeError, where the current module threw IllegalArgumentException (or, for `sendBroadcast`, failed in a
 * coroutine on the main thread). Names given as app names, package names or aliases stay as lenient as before (any value
 * is read as its string, nullish gives false / null).
 *
 * zh-CN: 新脚本模块层上的 app 模块 (P3.4): 模块对象的原型是运行时的 AppUtils (ScriptRuntime.app), 属性与函数 (其中 22 个也在全局, 设置页启动函数带别名) 的顺序同
 * 现有模块; app.autojs 是 autojs 模块对象, 在模块对象构建时定义 (惰性属性, 构建 app 不会构建 autojs; 原先由 ScriptRuntime.augment() 在全部模块挂载后设置).
 * 实际工作仍由现有模块的函数完成 (augment/app/App, 涉及 Android Intent, 包管理器与 shell; P5.2 前迁出), 参数原样传入. 本层增加签名 (T4) 与 D10 的参数类型:
 * 目标, Intent 或选项的类型不在文档之列为 TypeError, 未知的广播简称为 RangeError (现有模块抛 IllegalArgumentException, sendBroadcast 则在主线程协程中失败).
 * 以应用名, 包名或别名给出的名称与以前一样宽松 (任何值按字符串读取, null / undefined 得到 false / null).
 */
@ScriptModule(name = "app")
class AppModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.app

    @ScriptInstall
    fun install(installation: ScriptModuleInstallation) {
        installation.onBuilt { members ->
            installation.defineLazy("autojs", ScriptableObject.EMPTY, members) { installation.moduleObject("autojs") }
        }
    }

    @ScriptProperty
    val versionCode: Int = BuildConfig.VERSION_CODE

    @ScriptProperty
    val versionName: String = BuildConfig.VERSION_NAME

    @ScriptFunction
    fun intent(options: Any?): Intent = App.intent(runtime, arrayOf(intentOrOptions("app.intent", "options", options)))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun startActivity(target: Any?, options: Any? = null) {
        App.startActivity(runtime, targetArgs("app.startActivity", target, options))
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun startDualActivity(target: Any?, options: Any? = null) {
        App.startDualActivity(runtime, targetArgs("app.startDualActivity", target, options))
    }

    @ScriptFunction
    fun intentToShell(options: Any?): String = App.intentToShell(runtime, arrayOf(intentOrOptions("app.intentToShell", "options", options)))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun startService(options: Any?) {
        App.startService(runtime, arrayOf(intentOrOptions("app.startService", "options", options)))
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun sendEmail(options: Any?) {
        App.sendEmail(runtime, arrayOf(options))
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun sendBroadcast(target: Any?) {
        when (val value = unwrap(target)) {
            is CharSequence -> if (BroadcastShortForm.entries.none { it.name.equals(value.toString(), ignoreCase = true) }) {
                throw GlobalValues.rangeError("app.sendBroadcast", "target", BroadcastShortForm.entries.joinToString(" | ") { "'${it.shortName}'" }, target)
            }
            else -> intentOrOptions("app.sendBroadcast", "target", target)
        }
        App.sendBroadcast(runtime, arrayOf(target))
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun sendLocalBroadcastSync(intent: Any?) {
        val value = unwrap(intent)
        if (!isNullish(value) && value !is Intent) throw GlobalValues.typeError("app.sendLocalBroadcastSync", "intent", "android.content.Intent | null", intent)
        App.sendLocalBroadcastSync(runtime, arrayOf(if (isNullish(value)) null else intent))
    }

    @ScriptFunction
    fun parseUri(uri: Any?): Uri? = App.parseUri(runtime, arrayOf(uri))

    @ScriptFunction
    fun openUrl(url: String) {
        App.openUrl(runtime, arrayOf(url))
    }

    @ScriptFunction
    fun openDualUrl(url: String) {
        App.openDualUrl(runtime, arrayOf(url))
    }

    @ScriptFunction
    fun getUriForFile(path: Any?): Uri? = App.getUriForFile(runtime, arrayOf(path))

    @ScriptFunction
    fun getAppByAlias(alias: Any?): PresetApp? = App.getAppByAlias(runtime, arrayOf(alias))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun launch(app: Any?): Boolean = App.launch(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun launchDual(app: Any?): Boolean = App.launchDual(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun launchPackage(app: Any?): Boolean = App.launchPackage(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun launchDualPackage(app: Any?): Boolean = App.launchDualPackage(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun launchApp(app: Any?): Boolean = App.launchApp(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun launchDualApp(app: Any?): Boolean = App.launchDualApp(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun getPackageName(app: Any?): String? = App.getPackageName(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun getAppName(app: Any?): String? = App.getAppName(runtime, arrayOf(app))

    @ScriptFunction
    fun getInstalledApps(options: NativeObject? = null): Any = App.getInstalledApps(runtime, optionsArgs(options))

    @ScriptFunction
    fun getInstalledPackages(options: NativeObject? = null): Any = App.getInstalledPackages(runtime, optionsArgs(options))

    @ScriptFunction
    fun getApkInfo(path: String, options: NativeObject? = null): Any? = App.getApkInfo(runtime, arrayOf(path, *optionsArgs(options)))

    @ScriptFunction(aliases = ["launchSettings", "openAppSetting", "openAppSettings"], global = ScriptGlobal.ALSO)
    fun launchAppDetailsSettings(app: Any?): Boolean = App.launchAppDetailsSettings(runtime, arrayOf(app))

    @ScriptFunction(aliases = ["launchDualSettings", "openDualAppSetting", "openDualAppSettings"], global = ScriptGlobal.ALSO)
    fun launchDualAppDetailsSettings(app: Any?): Boolean = App.launchDualAppDetailsSettings(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun isInstalled(app: Any?): Boolean = App.isInstalled(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun isDualInstalled(app: Any?): Boolean = App.isDualInstalled(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun uninstall(app: Any?) {
        App.uninstall(runtime, arrayOf(app))
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun uninstallDual(app: Any?) {
        App.uninstallDual(runtime, arrayOf(app))
    }

    @ScriptFunction
    fun viewFile(path: Any?): Boolean = App.viewFile(runtime, arrayOf(filePath("app.viewFile", path)))

    @ScriptFunction
    fun editFile(path: Any?): Boolean = App.editFile(runtime, arrayOf(filePath("app.editFile", path)))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun kill(app: Any?): Boolean = App.kill(runtime, arrayOf(app))

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun killDual(app: Any?): Boolean = App.killDual(runtime, arrayOf(app))

    /** The arguments of startActivity / startDualActivity: a target of a documented type, options only with an intent or an object. */
    private fun targetArgs(function: String, target: Any?, options: Any?): Array<Any?> {
        val value = unwrap(target)
        if (value !is CharSequence && value !is Intent && value !is Uri && value !is URI && value !is NativeObject) {
            throw GlobalValues.typeError(function, "target", "string | android.content.Intent | android.net.Uri | java.net.URI | Object", target)
        }
        if (isNullish(options)) return arrayOf(target)
        if (value is CharSequence || value is Uri || value is URI) throw GlobalValues.cannotBe(function, "options", "given with a string or URI target")
        if (options !is NativeObject) throw GlobalValues.typeError(function, "options", "Object", options)
        return arrayOf(target, options)
    }

    private fun intentOrOptions(function: String, param: String, value: Any?): Any? = value.also {
        val unwrapped = unwrap(it)
        if (unwrapped !is Intent && unwrapped !is NativeObject) throw GlobalValues.typeError(function, param, "android.content.Intent | Object", it)
    }

    /** A file path for viewFile / editFile: nullish (false, as before) or a string. */
    private fun filePath(function: String, path: Any?): Any? = path.also {
        if (!isNullish(it) && unwrap(it) !is CharSequence) throw GlobalValues.typeError(function, "path", "string", it)
    }

    private fun optionsArgs(options: NativeObject?): Array<Any?> = if (options == null) emptyArray() else arrayOf(options)

    private fun unwrap(value: Any?): Any? = when (value) {
        is Wrapper -> value.unwrap()
        is ConsString -> value.toString()
        else -> value
    }

}
