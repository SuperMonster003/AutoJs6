package org.autojs.autojs.runtime.api.modules.sqlite

import org.autojs.autojs.core.database.Database
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Symbol
import org.mozilla.javascript.Wrapper
import kotlin.math.ceil
import kotlin.math.floor
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `sqlite` module on the new script module layer (roadmap P3.5): `sqlite(databaseFilePath, options?, callback?)`
 * and the same `sqlite.open`, returning the documented Database (the current `core.database.Database`, opened by the
 * current `augment/sqlite/SQLite.open` with the same option and callback conversion; appendix C Q24).
 *
 * Arguments follow the real signatures (T4, appendix C Q7); the arguments are checked here first (D10, all of them
 * IllegalArgumentException in the current module): a nullish path is a TypeError and a blank one a RangeError
 * (documented: an empty path throws), options that are not a plain object a TypeError, `options.version` without a
 * number a TypeError and one below 1 or beyond a 32-bit integer after truncation a RangeError (documented: converted to
 * a 32-bit integer, at least 1), a callback that is neither an object (converted to the DatabaseCallback interface) nor
 * a DatabaseCallback a TypeError. Android's own failures keep their Java types (T5).
 *
 * zh-CN: 新脚本模块层上的 sqlite 模块 (P3.5): sqlite(databaseFilePath, options?, callback?) 与同样的 sqlite.open, 返回文档所述的 Database (现有
 * core.database.Database, 由现有 augment/sqlite/SQLite.open 以同样的选项与回调转换打开; 附录 C Q24). 参数按真实签名 (T4, 附录 C Q7); 参数先在此检查
 * (D10, 现有模块均为 IllegalArgumentException): nullish 路径为 TypeError, 空白路径为 RangeError (文档: 空路径抛出异常), 选项不是普通对象为 TypeError,
 * options.version 不含数值为 TypeError, 截断后小于 1 或超出 32 位整数为 RangeError (文档: 先转换为 32 位整数, 不小于 1), 回调既不是对象 (转换为
 * DatabaseCallback 接口) 也不是 DatabaseCallback 为 TypeError. Android 自身的失败保留 Java 类型 (T5).
 */
@ScriptModule(name = "sqlite")
class SqliteModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(databaseFilePath: String, options: Any? = null, callback: Any? = null): Database = open(MODULE, databaseFilePath, options, callback)

    @ScriptFunction
    fun open(databaseFilePath: String, options: Any? = null, callback: Any? = null): Database = open(FUNCTION_OPEN, databaseFilePath, options, callback)

    private fun open(function: String, path: String, options: Any?, callback: Any?): Database {
        if (path.isBlank()) throw UtilValues.rangeError(function, ScriptMessages.argumentCannotBe("databaseFilePath", UtilValues.quote(path)))
        if (!ScriptArgs.isNullish(options)) {
            if (options !is NativeObject) throw UtilValues.typeError(function, ScriptMessages.argumentType("options", "Object", ScriptArgs.describe(options)))
            checkVersion(function, ScriptableObject.getProperty(options, "version"))
        }
        if (!ScriptArgs.isNullish(callback) && !isCallback(callback)) {
            throw UtilValues.typeError(function, ScriptMessages.argumentType("callback", "DatabaseCallback", ScriptArgs.describe(callback)))
        }
        return SQLite.open(runtime, arrayOf(path, options, callback))
    }

    private fun checkVersion(function: String, version: Any?) {
        if (version === Scriptable.NOT_FOUND || ScriptArgs.isNullish(version)) return
        val number = if (version is Symbol) Double.NaN else RhinoScriptRuntime.toNumber(version)
        if (number.isNaN()) throw UtilValues.typeError(function, ScriptMessages.argumentType("options.version", "number", ScriptArgs.describe(version)))
        val truncated = if (number > 0.0) floor(number) else ceil(number)
        if (!(truncated >= 1.0 && truncated <= Int.MAX_VALUE)) {
            throw UtilValues.rangeError(function, ScriptMessages.argumentType("options.version", "integer >= 1", UtilValues.quote(version)))
        }
    }

    private fun isCallback(value: Any?): Boolean = value is Scriptable && value !is Wrapper || (value as? Wrapper)?.unwrap() is Database.DatabaseCallback

    private companion object {
        const val MODULE = "sqlite"
        const val FUNCTION_OPEN = "sqlite.open"
    }

}
