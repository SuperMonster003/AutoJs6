package org.autojs.autojs.runtime.api.modules.util

import android.os.Build
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.util.VersionCodes
import org.autojs.autojs.runtime.api.augment.util.VersionCodesInfo
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.ScriptToString
import org.autojs.autojs.runtime.api.modules.util.UtilValues.isNullish
import org.autojs.autojs.runtime.api.modules.util.UtilValues.typeError
import org.mozilla.javascript.Context
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import java.util.Calendar
import java.util.function.Supplier
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/** `util.version` on the new script module layer (roadmap P2.3): `sdkInt`, the API level of the device. */
@ScriptModule(name = "version", parent = UtilModule::class, dollarPrefix = false)
object UtilVersionModule {

    @ScriptProperty
    val sdkInt: Int = Build.VERSION.SDK_INT

}

/**
 * `util.versionCodes` on the new script module layer (roadmap P2.3): Android version codes (`util.versionCodes.TIRAMISU`,
 * from the module object's prototype, read-only, a new info object on every read as in the current module) and their
 * search. A query matches the API level, the version code, the release name or one of its words, the internal codename or
 * one of its words, the release timestamp, the release date, the platform version or its leading number; a `Date` is
 * matched by its time value against the release timestamp (local midnight of the release date; the current module
 * compared the Java text of the number, which never matched). Other query types are a TypeError.
 *
 * zh-CN: 新脚本模块层上的 util.versionCodes (P2.3): Android 版本代码 (位于模块对象的原型上, 只读, 每次读取返回新的信息对象, 同现有模块) 及其查询.
 * Date 按时间值与发布时间戳比较 (现有模块比较数字的 Java 文本, 从不匹配). 其他查询类型为 TypeError.
 */
@ScriptModule(name = "versionCodes", parent = UtilModule::class, dollarPrefix = false)
class UtilVersionCodesModule(private val runtime: ScriptRuntime?) {

    /** Read through VersionCodesInfo, whose initialization detaches the process-wide generated object (memory roadmap H18). */
    private val list: List<VersionCodes.Info> get() = VersionCodesInfo.list

    @ScriptFunction(name = "toString", toStringRole = ScriptToString.LITERAL)
    fun toStringOf(isInDetail: Any? = null): String = summary(isInDetail)

    @ScriptFunction
    fun search(cx: Context, scope: VarScope, query: Any?): Scriptable? {
        val text = queryText(query) ?: return null
        return list.firstOrNull { matches(it, text) }?.let { infoObject(cx, scope, it) }
    }

    @ScriptFunction
    fun searchAll(cx: Context, scope: VarScope, query: Any?): Scriptable {
        val text = queryText(query) ?: return cx.newArray(scope, 0)
        val found = list.filter { matches(it, text) }.distinct().sortedByDescending { it.apiLevel.toInt() }
        return cx.newArray(scope, found.map<VersionCodes.Info, Any?> { infoObject(cx, scope, it) }.toTypedArray())
    }

    /** One line per version: `CODE: apiLevel / releaseName / platformVersion`, or every field when [isInDetail]. */
    @ScriptFunction
    fun summary(isInDetail: Any? = null): String {
        val detail = !isNullish(isInDetail) && RhinoScriptRuntime.toBoolean(isInDetail)
        return list.joinToString("\n") { info ->
            when {
                detail -> listOf(info.versionCode, info.apiLevel, info.releaseName, info.platformVersion, info.internalCodename, info.releaseDate, info.releaseTimestamp).joinToString(", ")
                else -> "${info.versionCode}: ${info.apiLevel} / ${info.releaseName} / ${info.platformVersion}"
            }
        }
    }

    /** The version code constants, each a read-only property giving a new info object. */
    @ScriptPrototype
    fun prototype(): Scriptable {
        val scope: VarScope? = runtime?.topLevelScope
        return NativeObject().also { obj ->
            scope?.let { RhinoScriptRuntime.setBuiltinProtoAndParent(obj, it, TopLevel.Builtins.Object) }
            list.forEach { info ->
                obj.defineProperty(info.versionCode, Supplier<Any?> {
                    infoObject(Context.getCurrentContext(), scope ?: throw IllegalStateException("util.versionCodes needs a script runtime"), info)
                }, null, READONLY or PERMANENT)
            }
        }
    }

    private fun queryText(query: Any?): String? = when (val q = if (query is Wrapper) query.unwrap() else query) {
        null, org.mozilla.javascript.Undefined.instance -> if (q == null) null else throw typeError("util.versionCodes.search", "query", "string | number | Date", query)
        is NativeDate -> RhinoScriptRuntime.numberToString(q.date, 10)
        is Number -> RhinoScriptRuntime.numberToString(q.toDouble(), 10)
        is CharSequence -> q.toString()
        else -> throw typeError("util.versionCodes.search", "query", "string | number | Date", query)
    }

    private fun matches(info: VersionCodes.Info, text: String): Boolean =
        info.apiLevel == text || info.versionCode == text ||
                info.releaseName == text || info.releaseName.split(WHITESPACE).contains(text) ||
                info.internalCodename == text || info.internalCodename.split(WHITESPACE).contains(text) ||
                info.releaseTimestamp == text || info.releaseDate == text ||
                info.platformVersion == text || info.platformVersion.split(NON_DIGITS).firstOrNull() == text

    private fun infoObject(cx: Context, scope: VarScope, info: VersionCodes.Info): Scriptable = (cx.newObject(scope) as ScriptableObject).also {
        it.defineProperty("versionCode", info.versionCode, READONLY or PERMANENT)
        it.defineProperty("apiLevel", info.apiLevel.toInt(), READONLY or PERMANENT)
        it.defineProperty("releaseName", info.releaseName, READONLY or PERMANENT)
        it.defineProperty("platformVersion", info.platformVersion, READONLY or PERMANENT)
        it.defineProperty("internalCodename", info.internalCodename, READONLY or PERMANENT)
        it.defineProperty("releaseDate", info.releaseDate, READONLY or PERMANENT)
        it.defineProperty("releaseTimestamp", timestampOf(info), READONLY or PERMANENT)
        it.defineProperty("valueOf", LambdaFunction(scope, "valueOf", 0, SerializableCallable { _, _, thisObj, _ ->
            (thisObj as? Scriptable)?.let { o -> ScriptableObject.getProperty(o, "apiLevel") }
        }, false), READONLY or DONTENUM or PERMANENT)
    }

    private companion object {

        val WHITESPACE = Regex("\\s+")
        val NON_DIGITS = Regex("\\D+")

        private val MONTHS = mapOf("jan" to 0, "feb" to 1, "mar" to 2, "apr" to 3, "may" to 4, "jun" to 5, "jul" to 6, "aug" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dec" to 11)
        private val QUARTER = Regex("^Q([1-4]), (\\d+)$")
        private val FULL_DATE = Regex("^(\\w{3})\\w* (\\d\\d?), (\\d+)$")

        /** Local midnight of the release date in milliseconds ("Q3, 2022" is July 1st), 0 when the date cannot be read. */
        fun timestampOf(info: VersionCodes.Info): Long {
            val date = info.releaseDate.replace(QUARTER) { m -> "${listOf("Jan", "Apr", "Jul", "Oct")[m.groupValues[1].toInt() - 1]} 1, ${m.groupValues[2]}" }
            val match = FULL_DATE.matchEntire(date) ?: return 0
            val month = MONTHS[match.groupValues[1].lowercase()] ?: return 0
            val day = match.groupValues[2].toIntOrNull() ?: return 0
            val year = match.groupValues[3].toIntOrNull() ?: return 0
            return Calendar.getInstance().apply {
                clear()
                set(year, month, day)
            }.timeInMillis
        }

    }

}
