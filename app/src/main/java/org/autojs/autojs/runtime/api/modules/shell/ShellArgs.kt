package org.autojs.autojs.runtime.api.modules.shell

import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Wrapper

/**
 * Argument rules shared by `shell` and `shizuku` (roadmap P3.4), checked before the current implementation
 * (`augment/shell/Shell`) runs, so the documented forms keep working and other values are TypeErrors (D10; the current
 * modules threw IllegalArgumentException / IllegalStateException):
 *
 * - a command is a string or an array of strings (issue #310: elements are joined by line breaks);
 * - `(command, withRoot)`: a boolean or a number; `(command, arguments)`: a string or a `ShellArguments` object;
 * - `(command, arguments, withRoot)`: arguments as above, `withRoot` read for its truthiness as before (documented as
 *   boolean or number);
 * - spawn options: a boolean (`root`) or an object.
 *
 * Arguments omitted (trailing `undefined` included, T4) are left out of the array passed on, as the current functions
 * branch on the argument count.
 *
 * zh-CN: shell 与 shizuku 共用的参数规则 (P3.4), 在现有实现 (augment/shell/Shell) 运行前检查, 文档所列形式照常可用, 其他值为 TypeError (D10; 现有模块抛
 * IllegalArgumentException / IllegalStateException): 命令为字符串或字符串数组 (issue #310: 元素以换行连接); (command, withRoot) 为布尔值或数值,
 * (command, arguments) 为字符串或 ShellArguments 对象; (command, arguments, withRoot) 的 arguments 同上, withRoot 与以前一样取真值 (文档写作布尔值或数值);
 * spawn 的选项为布尔值 (root) 或对象. 省略的参数 (含 T4 的尾部 undefined) 不放入传给现有函数的数组, 因为现有函数按参数个数分支.
 */
internal object ShellArgs {

    /** Marks an omitted optional argument. */
    val ABSENT = Any()

    fun command(function: String, command: Any?, arguments: Any?, withRoot: Any?): Array<Any?> {
        commandLine(function, command)
        if (arguments === ABSENT) return arrayOf(command)
        val value = unwrap(arguments)
        if (withRoot === ABSENT) {
            if (value !is Boolean && value !is Number && value !is CharSequence && value !is NativeObject) {
                throw GlobalValues.typeError(function, "withRoot / arguments", "boolean | number | string | Object", arguments)
            }
            return arrayOf(command, arguments)
        }
        if (value !is CharSequence && value !is NativeObject) throw GlobalValues.typeError(function, "arguments", "string | Object", arguments)
        return arrayOf(command, arguments, withRoot)
    }

    fun spawn(function: String, command: Any?, options: Any?): Array<Any?> {
        commandLine(function, command)
        if (options === ABSENT || isNullish(options)) return arrayOf(command)
        val value = unwrap(options)
        if (value !is Boolean && value !is NativeObject) throw GlobalValues.typeError(function, "options", "boolean | Object", options)
        return arrayOf(command, options)
    }

    private fun commandLine(function: String, command: Any?) {
        if (unwrap(command) !is CharSequence && command !is NativeArray) throw GlobalValues.typeError(function, "command", "string | string[]", command)
    }

    private fun unwrap(value: Any?): Any? = when (value) {
        is Wrapper -> value.unwrap()
        is ConsString -> value.toString()
        else -> value
    }

}
