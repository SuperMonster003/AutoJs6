package org.autojs.autojs.runtime.exception

import org.mozilla.javascript.Context
import org.mozilla.javascript.EvaluatorException

// @Hint by SuperMonster003 on Oct 31, 2024.
//  ! This class include the exception's code line number when printing the error stack trace.
//  ! zh-CN: 当前类可在打印错误堆栈信息时包含异常所在的 code line number (代码行号).
class WrappedIllegalArgumentException(detailMessage: String) : EvaluatorException(run {
    var droppedFormer = false
    var droppedLatter = false
    val result = mutableListOf<String>()
    for (e in Thread.currentThread().stackTrace) {
        if ("$e".startsWith(WrappedIllegalArgumentException::class.java.name)) {
            droppedFormer = true
            result.clear()
            continue
        }
        if ("$e".contains(Regex("^(java.lang.reflect.Method.invoke|org.mozilla.javascript.Interpreter)"))) {
            droppedLatter = true
            break
        }
        result += "$e"
    }
    if (droppedFormer) result.add(0, ELLIPSIS_MARK)
    if (droppedLatter) result.add(ELLIPSIS_MARK)
    buildString {
        appendLine(detailMessage)
        appendLine()
        appendLine(if (droppedFormer || droppedLatter) "Stack trace (partial):" else "Stack trace:")
        appendLine()
        appendLine(result.joinToString("\n") { "    $it" })
    }
}) {

    private val exception = IllegalArgumentException(details())

    /**
     * @see Context.throwAsScriptRuntimeEx
     */
    init {
        initCause(exception)

        val linep = intArrayOf(0)
        val sourceName = Context.getSourcePositionFromStack(linep)
        val lineNumber = linep[0]
        if (sourceName != null) {
            initSourceName(sourceName)
        }
        if (lineNumber != 0) {
            initLineNumber(lineNumber)
        }
    }

    /**
     * Get the wrapped exception.
     *
     * @return the exception that was presented as an argument to the constructor when this object
     * was created
     */
    fun getWrappedException(): Throwable = exception

    @Deprecated("Use {@link #getWrappedException()} instead.", ReplaceWith("getWrappedException()"))
    fun unwrap(): Throwable = getWrappedException()

    companion object {

        private const val ELLIPSIS_MARK = "... ..."

    }

}
