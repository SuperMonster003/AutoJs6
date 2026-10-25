package org.autojs.autojs.runtime.api.modules.mediainfo

import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.ScriptableExtensions.getProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInstallNames
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject

/**
 * `mediainfo` on the new script module layer (roadmap P4.1): `mediainfo(path)` and read / snapshot / capabilities / get /
 * countGet of the current `augment/mediainfo/Mediainfo` (through the MediaInfo plugin), with its SNAPSHOT_SCHEMA_V1 / V2
 * as they are.
 *
 * Arguments follow the real signatures (T4): a missing or surplus argument is a TypeError of the generated layer. What
 * the current module rejects is checked here first (D10, IllegalArgumentException in the current module): a nullish
 * path is a TypeError and a blank one a RangeError, a nullish stream kind or parameter name a TypeError, options that are
 * not an object a TypeError, `streamNumber` that is not a number and `infoKind` that is not a string TypeErrors, a
 * `streamNumber` that is not a non-negative 32-bit integer and an unknown `infoKind` RangeErrors. Everything the current
 * module accepts stays accepted (the switches and the schema of `snapshot` convert as before).
 * Once the arguments are accepted, a failure of the current module (the plugin not installed or disabled among them)
 * is rethrown as the current augment layer does (`AugmentMembers.asCurrent`): the same InternalError in both states.
 *
 * zh-CN: 新脚本模块层上的 mediainfo (P4.1): mediainfo(path) 与现有 augment/mediainfo/Mediainfo 的 read / snapshot / capabilities / get / countGet
 * (经 MediaInfo 插件), SNAPSHOT_SCHEMA_V1 / V2 照原值. 参数按真实签名 (T4): 缺少或多余参数为生成层的 TypeError. 现有模块拒绝的在此先行检查 (D10, 现有模块均为
 * IllegalArgumentException): nullish 路径为 TypeError, 空白路径为 RangeError, nullish 的流类型或参数名为 TypeError, 不是对象的选项为 TypeError, 不是
 * 数值的 streamNumber 与不是字符串的 infoKind 为 TypeError, 不是非负 32 位整数的 streamNumber 与未知的 infoKind 为 RangeError. 现有模块接受的照旧接受
 * (snapshot 的开关与 schema 照旧转换).
 * 参数通过检查后, 现有模块的失败 (含插件未安装或未启用) 按现有 augment 层的方式重新抛出 (AugmentMembers.asCurrent), 两态为同一个 InternalError.
 */
@ScriptModule(name = "mediainfo")
class MediainfoModule(private val runtime: ScriptRuntime) {

    /** The current module: its data properties, and how its calls fail. */
    private val current by lazy { Mediainfo(runtime) }

    @ScriptInstall(names = [ScriptInstallNames(["SNAPSHOT_SCHEMA_V1", "SNAPSHOT_SCHEMA_V2"])])
    fun install(installation: ScriptModuleInstallation) = installation.onBuilt { AugmentMembers.defineProperties(current.scriptProperties, it) }

    @ScriptInvoke
    fun invoke(path: Any?): Any = read(MODULE, path)

    @ScriptFunction
    fun read(path: Any?): Any = read(FUNCTION_READ, path)

    @ScriptFunction
    fun snapshot(path: Any?, options: Any? = null): Any? {
        checkPath(FUNCTION_SNAPSHOT, path)
        checkOptions(FUNCTION_SNAPSHOT, options)
        return AugmentMembers.asCurrent(MODULE, "snapshot") { Mediainfo.snapshot(runtime, arrayOf(path, options)) }
    }

    @ScriptFunction
    fun capabilities(): Any? = AugmentMembers.asCurrent(MODULE, "capabilities") { Mediainfo.capabilities(runtime, emptyArray()) }

    @ScriptFunction
    fun get(path: Any?, streamKind: Any?, parameter: Any?, options: Any? = null): Any {
        checkPath(FUNCTION_GET, path)
        checkText(FUNCTION_GET, "streamKind", streamKind)
        checkText(FUNCTION_GET, "parameter", parameter)
        if (checkOptions(FUNCTION_GET, options)) {
            // Read as the current module reads them (getProp unwraps Java objects).
            // zh-CN: 读取方式同现有模块 (getProp 解包 Java 对象).
            val streamNumber = (options as NativeObject).getProp("streamNumber").takeUnless { it.isJsNullish() }
            val infoKind = options.getProp("infoKind").takeUnless { it.isJsNullish() }
            if (streamNumber != null && streamNumber !is Number) throw UtilValues.typeError(FUNCTION_GET, ScriptMessages.argumentType("options.streamNumber", "number", ScriptArgs.describe(streamNumber)))
            if (infoKind != null && infoKind !is String) throw UtilValues.typeError(FUNCTION_GET, ScriptMessages.argumentType("options.infoKind", "string", ScriptArgs.describe(infoKind)))
            AugmentMembers.values(FUNCTION_GET) { Mediainfo.queryOptions(options) }
        }
        return AugmentMembers.asCurrent(MODULE, "get") { Mediainfo.get(runtime, arrayOf(path, streamKind, parameter, options)) }
    }

    @ScriptFunction
    fun countGet(path: Any?, streamKind: Any?): Any {
        checkPath(FUNCTION_COUNT_GET, path)
        checkText(FUNCTION_COUNT_GET, "streamKind", streamKind)
        return AugmentMembers.asCurrent(MODULE, "countGet") { Mediainfo.countGet(runtime, arrayOf(path, streamKind)) }
    }

    private fun read(function: String, path: Any?): Any {
        checkPath(function, path)
        return AugmentMembers.asCurrent(MODULE, if (function == MODULE) null else "read") { Mediainfo.read(runtime, arrayOf(path)) }
    }

    private fun checkPath(function: String, path: Any?) {
        checkText(function, "path", path)
        if (Context.toString(path).isBlank()) throw UtilValues.rangeError(function, ScriptMessages.argumentCannotBe("path", UtilValues.quote(path)))
    }

    private fun checkText(function: String, name: String, value: Any?) {
        if (ScriptArgs.isNullish(value)) throw UtilValues.typeError(function, ScriptMessages.argumentType(name, "string", ScriptArgs.describe(value)))
    }

    /** Whether [options] is given; options that are given must be an object. */
    private fun checkOptions(function: String, options: Any?): Boolean {
        if (ScriptArgs.isNullish(options)) return false
        if (options !is NativeObject) throw UtilValues.typeError(function, ScriptMessages.argumentType("options", "Object", ScriptArgs.describe(options)))
        return true
    }

    private companion object {
        const val MODULE = "mediainfo"
        const val FUNCTION_READ = "mediainfo.read"
        const val FUNCTION_SNAPSHOT = "mediainfo.snapshot"
        const val FUNCTION_GET = "mediainfo.get"
        const val FUNCTION_COUNT_GET = "mediainfo.countGet"
    }

}
