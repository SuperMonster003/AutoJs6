package org.autojs.autojs.runtime.api.modules.memory

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.memory.Memory
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Symbol
import java.math.BigInteger

/**
 * `memory` on the new script module layer (roadmap P4.1; the module of docs/memory/ROADMAP.md P5.1): snapshot, collect,
 * retained, dumpHeap and watch of the current `augment/memory/Memory`, read from the memory diagnostics of the host.
 *
 * Arguments follow the real signatures (T4): a missing or surplus argument is a TypeError of the generated layer. What
 * the current module rejects is checked here first (D10, IllegalArgumentException in the current module): a `collect`
 * timeout that is not a number (NaN included) is a TypeError and one outside 0 to `Memory.MAX_COLLECT_TIMEOUT_MS` a
 * RangeError; a `dumpHeap` path that is not a string is a TypeError and a blank one a RangeError (checked before the
 * permission to dump, which stays an IllegalStateException); a `watch` target that is nullish or a primitive and a
 * `watch` name that is not a string are TypeErrors. The checks read the arguments as the current module does, unwrapped by
 * its argument guard.
 *
 * zh-CN: 新脚本模块层上的 memory (P4.1; docs/memory/ROADMAP.md P5.1 的模块): 现有 augment/memory/Memory 的 snapshot, collect, retained, dumpHeap,
 * watch, 读取宿主的内存诊断. 参数按真实签名 (T4): 缺少或多余参数为生成层的 TypeError. 现有模块拒绝的在此先行检查 (D10, 现有模块均为
 * IllegalArgumentException): collect 的超时不是数值 (含 NaN) 为 TypeError, 不在 0 到 Memory.MAX_COLLECT_TIMEOUT_MS 之间为 RangeError; dumpHeap 的路径
 * 不是字符串为 TypeError, 空白为 RangeError (先于导出许可的检查, 后者仍为 IllegalStateException); watch 的目标为 nullish 或原始值, 名称不是字符串,
 * 均为 TypeError. 检查读取的参数与现有模块相同, 即经其参数守卫解包后的值.
 */
@ScriptModule(name = "memory")
class MemoryModule(private val runtime: ScriptRuntime) {

    private val memory by lazy { Memory(runtime) }

    @ScriptFunction
    fun snapshot(): Any = memory.snapshot(runtime, emptyArray())

    @ScriptFunction
    fun collect(timeout: Any? = null): Any {
        val raw = RhinoUtils.unwrap(timeout)
        if (!ScriptArgs.isNullish(raw)) {
            val value = (raw as? Number)?.toDouble()
            if (value == null || value.isNaN()) throw UtilValues.typeError(FUNCTION_COLLECT, ScriptMessages.argumentType("timeout", "number", ScriptArgs.describe(timeout)))
            if (value < 0 || value > Memory.MAX_COLLECT_TIMEOUT_MS) {
                throw UtilValues.rangeError(FUNCTION_COLLECT, ScriptMessages.argumentType("timeout", "number from 0 to ${Memory.MAX_COLLECT_TIMEOUT_MS}", UtilValues.quote(timeout)))
            }
        }
        return memory.collect(runtime, arrayOf(timeout))
    }

    @ScriptFunction
    fun retained(): Any = memory.retained(runtime, emptyArray())

    @ScriptFunction
    fun dumpHeap(path: Any? = null): Any {
        val raw = RhinoUtils.unwrap(path)
        if (!ScriptArgs.isNullish(raw)) {
            if (raw !is CharSequence) throw UtilValues.typeError(FUNCTION_DUMP_HEAP, ScriptMessages.argumentType("path", "string", ScriptArgs.describe(path)))
            if (raw.isBlank()) throw UtilValues.rangeError(FUNCTION_DUMP_HEAP, ScriptMessages.argumentCannotBe("path", UtilValues.quote(path)))
        }
        return memory.dumpHeap(runtime, arrayOf(path))
    }

    @ScriptFunction
    fun watch(obj: Any?, name: Any? = null): Any {
        val target = RhinoUtils.unwrap(obj)
        if (ScriptArgs.isNullish(target) || target is String || target is ConsString || target is Number || target is Boolean || target is Symbol || target is BigInteger) {
            throw UtilValues.typeError(FUNCTION_WATCH, ScriptMessages.argumentType("obj", "object", ScriptArgs.describe(obj)))
        }
        val description = RhinoUtils.unwrap(name)
        if (!ScriptArgs.isNullish(description) && description !is CharSequence) {
            throw UtilValues.typeError(FUNCTION_WATCH, ScriptMessages.argumentType("name", "string", ScriptArgs.describe(name)))
        }
        return memory.watch(runtime, arrayOf(obj, name))
    }

    private companion object {
        const val FUNCTION_COLLECT = "memory.collect"
        const val FUNCTION_DUMP_HEAP = "memory.dumpHeap"
        const val FUNCTION_WATCH = "memory.watch"
    }

}
