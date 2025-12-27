package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.util.StringUtils.uppercaseFirstChar
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * Created by SuperMonster003 on Dec 27, 2025.
 */
abstract class RegexListFilter(s: String, keysGetter: KeysGetter) : Filter {

    open val actionName: String = ""

    protected val input = s
    protected val keysGetter = keysGetter

    protected var isRegexSource = false
        private set

    // Compile regex only once to avoid repeated compilation during tree traversal.
    // zh-CN: 仅编译一次正则, 避免在遍历节点树时重复编译带来的开销.
    protected val compiledRegex by lazy(LazyThreadSafetyMode.NONE) { JsRegexUtils.compileJsOrPlainRegex(input) }

    constructor(regex: NativeRegExp, keysGetter: KeysGetter) : this(regex.toString(), keysGetter) {
        isRegexSource = true
    }

    override fun toString(): String {
        val literal = when {
            isRegexSource -> JsRegexUtils.formatAsJsRegexLiteral(input)
            else -> "\"$input\""
        }
        return "${keysGetter}${actionName.uppercaseFirstChar()}($literal)"
    }

}
