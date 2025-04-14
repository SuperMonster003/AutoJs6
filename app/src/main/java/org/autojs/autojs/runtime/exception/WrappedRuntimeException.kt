package org.autojs.autojs.runtime.exception

import org.mozilla.javascript.Context
import org.mozilla.javascript.EvaluatorException

// @Hint by SuperMonster003 on Oct 31, 2024.
//  ! This class include the exception's code line number when printing the error stack trace.
//  ! zh-CN: 当前类可在打印错误堆栈信息时包含异常所在的 code line number (代码行号).
class WrappedRuntimeException @JvmOverloads constructor(
    detailMessage: String,
    private val causeException: Throwable? = null,
) : EvaluatorException(detailMessage) {

    constructor(causeException: Throwable) : this(causeException.message ?: "", causeException)

    init {
        causeException?.let { initCause(it) }
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

    fun getWrappedException(): Throwable? = causeException

    @Deprecated("Use {@link #getWrappedException()} instead.", ReplaceWith("getWrappedException()"))
    fun unwrap(): Throwable? = getWrappedException()

    @Suppress("DEPRECATION")
    override fun toString() = super.toString().replace(Regex("^${WrappedRuntimeException::class.java.name}: "), "")

}
