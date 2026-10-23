package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.junit.Assert.assertTrue
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Roadmap P3.6 (evidence A of P3.3): the results of the new layer against the current augment modules in the same JVM,
 * expression by expression, with the error names compared loosely. A case agrees when both sides render the same, when
 * the Java exception of the current module is the one the new layer maps (`throws JavaException <Class>`, T5), or when
 * only the error name differs: the current module refused the arguments with an error of [ARGUMENT_ERRORS] (on a device
 * an InternalError or Error; on the JVM the Java exception its wrapper printed, or the message lookup that replaced it)
 * and the new layer throws a TypeError or RangeError (D10). Any other difference must be in the known differences
 * (expression to the new rendering and the row of appendix E that registers it), whose current result is checked to
 * still differ. Results the current module cannot produce on the JVM (`unavailable(jvm)`, or a call that waits for the
 * accessibility service or a device answer and is interrupted after [STALL_MILLIS]) are skipped; where the fixture fails
 * on the way to a result the device gives (see [onDevice]), the current result is read as on the device first.
 *
 * zh-CN: P3.6 (P3.3 的 A 类证据): 同一 JVM 中逐条比较新层与现有 augment 模块的结果, 错误名宽松比较. 两侧渲染相同, 现有模块的 Java 异常与新层映射的相同
 * (T5), 或只有错误名不同 (现有模块以 [ARGUMENT_ERRORS] 中的错误拒绝参数, 真机上为 InternalError 或 Error, JVM 上为包装打印的 Java 异常或替代它的消息查找;
 * 新层抛 TypeError 或 RangeError, D10) 时视为一致. 其他差异必须列入已知差异 (表达式到新层渲染与登记它的附录 E 行), 并检查现有结果仍然不同. 现有模块在 JVM 上
 * 无法得出的结果 (unavailable(jvm), 或等待无障碍服务 / 设备应答而在 [STALL_MILLIS] 后被中断的调用) 跳过; 夹具在得出真机结果的途中失败时 (见 [onDevice]),
 * 先把现有结果按真机读取: 无法构造的参数错误消息 (未 mock 的 Context.getString, ArgumentGuards 参数个数错误的 getQuantityString) 视为真机上可捕获的消息查找
 * 错误; 静态初始化需要 Android 的类 (RootUtils, StringUtils) 与夹具缺少的计时器主线程使结果不可得.
 */
internal object CurrentModuleResults {

    /** Error names of the current module that a TypeError or RangeError of the new layer replaces (D10). */
    val ARGUMENT_ERRORS = setOf(
        "InternalError", "Error", "TypeError", "RangeError", "MessageLookup",
        "IllegalArgumentException", "WrappedIllegalArgumentException", "IllegalStateException", "PatternSyntaxException",
    )

    /** How long one expression may run before the thread is interrupted. */
    const val STALL_MILLIS = 5_000L

    private const val BLOCKED = "unavailable(jvm) blocked"

    private val NEW_ARGUMENT_ERRORS = setOf("throws TypeError", "throws RangeError")

    /**
     * The counts of a comparison: [renamed] cases differ in the error name only, [skipped] ones are `unavailable(jvm)` on
     * the current side (the expressions with the reason).
     */
    data class Summary(val compared: Int, val same: Int, val renamed: Int, val known: Int, val skipped: List<String>)

    /**
     * Compares [expressions] on [newSide] (the new layer, mounted) and [currentSide] (the current modules, mounted) and
     * fails with every difference that is neither loose nor in [known] (expression to the expected new rendering and the
     * appendix E row).
     */
    fun assertAgree(newSide: DifferentialRhino, currentSide: DifferentialRhino, expressions: List<String>, known: Map<String, Pair<String, String>>): Summary {
        var same = 0
        var renamed = 0
        var knownCount = 0
        val skipped = mutableListOf<String>()
        val watchdog = Timer("current-module-results", true)
        val failures = try {
            (expressions + known.keys.filterNot { it in expressions }).mapNotNull { expression ->
                val current = renderWithin(watchdog, currentSide, expression)?.let(::onDevice) ?: DifferentialRhino.Rendered(BLOCKED, null)
                val new = renderWithin(watchdog, newSide, expression) ?: DifferentialRhino.Rendered(NEW_BLOCKED, null)
                val failure = compare(expression, current, new, known[expression])
                when {
                    failure != null -> Unit
                    expression in known -> knownCount++
                    current.key.startsWith("unavailable(jvm)") -> skipped += "$expression -> ${current.key.removePrefix("unavailable(jvm) ")}"
                    isSame(current, new) -> same++
                    else -> renamed++
                }
                failure
            }
        } finally {
            watchdog.cancel()
        }
        assertTrue("Results differ from the current modules:\n" + failures.joinToString("\n"), failures.isEmpty())
        return Summary(same + renamed + knownCount, same, renamed, knownCount, skipped)
    }

    private const val NEW_BLOCKED = "blocked"

    /**
     * Renders [expression] on the Kotlin side of [side] and interrupts the thread after [STALL_MILLIS]; null when it had
     * to be interrupted.
     */
    private fun renderWithin(watchdog: Timer, side: DifferentialRhino, expression: String): DifferentialRhino.Rendered? {
        val thread = Thread.currentThread()
        val stalled = AtomicBoolean(false)
        val task = object : TimerTask() {
            override fun run() {
                stalled.set(true)
                thread.interrupt()
            }
        }
        watchdog.schedule(task, STALL_MILLIS)
        val rendered = try {
            side.render(DifferentialRhino.Side.KT, expression)
        } finally {
            task.cancel()
            Thread.interrupted()
        }
        return rendered.takeUnless { stalled.get() }
    }

    /**
     * The result of the current module as a device gives it, where the JVM fixture fails on the way: the message of an
     * argument error that cannot be built (an unmocked `Context.getString`, or the `getQuantityString` of an argument
     * count error of ArgumentGuards) is the message lookup the device throws as a catchable error; a class whose static
     * initializer needs Android (`RootUtils`, `StringUtils`) and the main thread of the runtime's timers, which the fixture
     * has not got, make the result unavailable.
     */
    private fun onDevice(current: DifferentialRhino.Rendered): DifferentialRhino.Rendered {
        val detail = current.detail.orEmpty()
        val key = when {
            current.key == "throws(java) RuntimeException" && "android.content.Context not mocked" in detail -> "throws MessageLookup"
            current.key == "throws NullPointerException" && "getQuantityString" in detail && "ArgumentGuards" in detail -> "throws MessageLookup"
            current.key == "throws(java) NoClassDefFoundError" || current.key == "throws(java) ExceptionInInitializerError" -> "unavailable(jvm) android-class"
            current.key == "throws NullPointerException" && "Threads.getMainThread()" in detail -> "unavailable(jvm) timers"
            else -> return current
        }
        return DifferentialRhino.Rendered(key, detail)
    }

    private fun isSame(current: DifferentialRhino.Rendered, new: DifferentialRhino.Rendered) =
        current.key == new.key || new.key == "throws JavaException " + current.key.removePrefix("throws ")

    /** The failure of one case, or null when it agrees (loosely), is skipped, or differs as [difference] registers. */
    private fun compare(expression: String, current: DifferentialRhino.Rendered, new: DifferentialRhino.Rendered, difference: Pair<String, String>?): String? {
        val isSame = isSame(current, new)
        val isRenamed = !isSame && new.key in NEW_ARGUMENT_ERRORS && current.key.startsWith("throws ") && current.key.removePrefix("throws ") in ARGUMENT_ERRORS
        return when {
            new.key == NEW_BLOCKED -> "$expression (the new layer did not return within $STALL_MILLIS ms)\n    current $current"
            difference == null && current.key.startsWith("unavailable(jvm)") -> null
            difference == null && (isSame || isRenamed) -> null
            difference == null -> "$expression\n    current $current\n    new     $new"
            new.key != difference.first -> "$expression (known: ${difference.second})\n    expected ${difference.first}\n    new      $new\n    current  $current"
            isSame || isRenamed -> "$expression (known difference no longer differs: ${difference.second})\n    current $current\n    new     $new"
            else -> null
        }
    }

}
