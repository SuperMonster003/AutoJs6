package org.autojs.autojs.runtime.api.augment.memory

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.AugmentableKey
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.memory.MemoryDiagnosticsCore
import org.autojs.autojs.runtime.memory.MemorySnapshot
import org.autojs.autojs.runtime.memory.RetainedRuntime
import org.autojs.autojs6.R
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Symbol
import java.io.File
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The `memory` module (docs/memory/ROADMAP.md D11 / T9 / P5.1): read-only memory diagnostics for scripts, read from the data source
 * of the memory gate and the developer options page ([org.autojs.autojs.runtime.memory.MemoryDiagnostics]). [snapshot] reads the
 * heaps and counters, [collect] forces garbage collection and waits for finished runtimes to be reclaimed, [retained] lists the
 * finished runtimes still reachable, [dumpHeap] writes an hprof file (debug builds, or with the developer option "Allow scripts to
 * export hprof" on, appendix C Q8) and [watch] hands an object to LeakCanary (debug builds with LeakCanary on, otherwise nothing
 * happens). Nothing here releases the resources of a script.
 *
 * zh-CN: `memory` 模块 (docs/memory/ROADMAP.md D11 / T9 / P5.1): 供脚本使用的只读内存诊断, 数据源与内存门禁及开发者选项页面相同. [snapshot]
 * 读取堆与计数, [collect] 强制回收并等待已结束的 runtime 被回收, [retained] 列出仍可达的已结束 runtime, [dumpHeap] 写出 hprof 文件 (debug 构建,
 * 或开发者选项 "允许脚本导出 hprof" 开启时, 附录 C Q8), [watch] 把对象交给 LeakCanary (debug 构建且 LeakCanary 开启时, 否则什么也不做).
 * 这里不会释放任何脚本的资源.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class Memory internal constructor(
    scriptRuntime: ScriptRuntime,
    private val platform: MemoryScriptPlatform,
) : Augmentable(scriptRuntime) {

    constructor(scriptRuntime: ScriptRuntime) : this(scriptRuntime, AndroidMemoryScriptPlatform(scriptRuntime))

    override val keys = listOf(AugmentableKey("memory"))

    override val selfAssignmentFunctions = listOf(
        ::snapshot.name,
        ::collect.name,
        ::retained.name,
        ::dumpHeap.name,
        ::watch.name,
    )

    /**
     * `memory.snapshot()`: the current readings, without collecting garbage first.
     * zh-CN: `memory.snapshot()`: 当前读数, 不先回收垃圾.
     */
    @RhinoRuntimeFunctionInterface
    fun snapshot(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Map<String, Double> = ensureArgumentsIsEmpty(args) {
        snapshotOf(platform.diagnostics.snapshot())
    }

    /**
     * `memory.collect(timeout?)`: forced garbage collection, then up to [timeout][COLLECT_TIMEOUT_MS] milliseconds of waiting for every
     * finished runtime to be reclaimed (it returns earlier once none is left, and stops when the script is stopped); returns a new snapshot.
     * zh-CN: `memory.collect(timeout?)`: 强制回收垃圾, 再最多等待 timeout 毫秒让已结束的 runtime 全部被回收 (全部回收即提前返回, 脚本被停止时中止);
     * 返回新的快照.
     */
    @RhinoRuntimeFunctionInterface
    fun collect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Map<String, Double> = ensureArgumentsAtMost(args, 1) { argList ->
        val (timeout) = argList
        val timeoutMs = timeoutOf(timeout)
        val diagnostics = platform.diagnostics
        diagnostics.awaitCleared(timeoutMs) {
            platform.checkInterrupted()
            diagnostics.retainedCount() == 0
        }
        snapshotOf(diagnostics.snapshot())
    }

    /**
     * `memory.retained()`: the finished runtimes that are still reachable, earliest first. A runtime that has just ended stays in the
     * list until the next garbage collection; the ones left after [collect] are held by something.
     * zh-CN: `memory.retained()`: 仍可达的已结束 runtime, 先结束的在前. 刚结束的 runtime 会留在列表中直到下一次垃圾回收; [collect] 之后仍在的才是被持有的.
     */
    @RhinoRuntimeFunctionInterface
    fun retained(scriptRuntime: ScriptRuntime, args: Array<out Any?>): List<Map<String, Any>> = ensureArgumentsIsEmpty(args) {
        platform.diagnostics.retained().map(::retainedOf)
    }

    /**
     * `memory.dumpHeap(path?)`: writes an hprof heap dump and returns its absolute path. [path] is resolved like other file paths of
     * scripts (relative to the working directory); a directory, or no path at all, gets the file name `AutoJs6-heap-<yyyyMMdd-HHmmss>.hprof`.
     * The dump holds everything in the heap, the variables of every script included, so release builds refuse it unless the developer
     * option "Allow scripts to export hprof" is on (appendix C Q8).
     * zh-CN: `memory.dumpHeap(path?)`: 写出 hprof 堆转储并返回其绝对路径. [path] 与脚本的其他文件路径一样解析 (相对于工作目录); 目录或省略路径时
     * 文件名为 `AutoJs6-heap-<yyyyMMdd-HHmmss>.hprof`. 转储包含堆中全部数据 (含所有脚本的变量), 因此 release 构建只在开发者选项 "允许脚本导出 hprof"
     * 开启时允许 (附录 C Q8).
     */
    @RhinoRuntimeFunctionInterface
    fun dumpHeap(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsAtMost(args, 1) { argList ->
        val (path) = argList
        if (!isHeapDumpAllowed()) {
            throw IllegalStateException(globalContext.getString(R.string.error_memory_script_heap_dump_not_allowed))
        }
        platform.dumpHeap(heapDumpFileOf(path)).absolutePath
    }

    /**
     * `memory.watch(obj, name?)`: asks LeakCanary to expect [obj] to be reclaimed soon and to report it with its reference chain otherwise.
     * Effective in debug builds with LeakCanary on (returns true), nothing happens otherwise (returns false). A wrapped Java object is
     * watched itself, not its script wrapper.
     * zh-CN: `memory.watch(obj, name?)`: 让 LeakCanary 预期 [obj] 很快被回收, 否则报告它及其引用链. 仅在 debug 构建且 LeakCanary 开启时有效 (返回 true),
     * 否则什么也不做 (返回 false). 对包装的 Java 对象监视其本身而非脚本包装.
     */
    @RhinoRuntimeFunctionInterface
    fun watch(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        val (obj, name) = argList
        if (obj == null || obj.isJsNullish() || obj is String || obj is ConsString || obj is Number || obj is Boolean || obj is Symbol || obj is BigInteger) {
            throw WrappedIllegalArgumentException("Argument obj ${obj.jsBrief()} for memory.watch must be an object")
        }
        val description = when {
            name.isJsNullish() -> "memory.watch(${obj.javaClass.simpleName}) in ${platform.scriptName()}"
            name is CharSequence -> name.toString()
            else -> throw WrappedIllegalArgumentException("Argument name ${name.jsBrief()} for memory.watch must be a string")
        }
        platform.watch(obj, description)
    }

    private fun isHeapDumpAllowed() = platform.isDebugBuild || platform.isScriptHeapDumpAllowed()

    private fun timeoutOf(timeout: Any?): Long {
        if (timeout.isJsNullish()) return MemoryDiagnosticsCore.COLLECT_TIMEOUT_MS
        val value = (timeout as? Number)?.toDouble()
        if (value == null || value.isNaN() || value < 0 || value > MAX_COLLECT_TIMEOUT_MS) {
            throw WrappedIllegalArgumentException("Argument timeout ${timeout.jsBrief()} for memory.collect must be a number of milliseconds from 0 to $MAX_COLLECT_TIMEOUT_MS")
        }
        return value.toLong()
    }

    private fun heapDumpFileOf(path: Any?): File {
        val defaultName = heapDumpFileName(platform.currentTimeMillis())
        if (path.isJsNullish()) return platform.resolvePath(defaultName)
        if (path !is CharSequence || path.isBlank()) {
            throw WrappedIllegalArgumentException("Argument path ${path.jsBrief()} for memory.dumpHeap must be a non-empty string")
        }
        val text = path.toString()
        val resolved = platform.resolvePath(text)
        return if (text.endsWith('/') || text.endsWith(File.separatorChar) || resolved.isDirectory) File(resolved, defaultName) else resolved
    }

    companion object {

        /** Upper bound of the timeout of `memory.collect`, in milliseconds. zh-CN: `memory.collect` 超时的上限 (毫秒). */
        const val MAX_COLLECT_TIMEOUT_MS = 60_000L

        /**
         * The file name of a heap dump without a given name, the one the memory diagnostics page suggests (T10).
         * zh-CN: 未给出文件名时的堆转储文件名, 与内存诊断页面建议的文件名相同 (T10).
         */
        @JvmStatic
        fun heapDumpFileName(timeMillis: Long) = "AutoJs6-heap-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(timeMillis))}.hprof"

        /**
         * The script object of a snapshot: every field of [MemorySnapshot] as a number, in its declaration order.
         * zh-CN: 快照的脚本对象: [MemorySnapshot] 的每个字段都是 number, 顺序与声明一致.
         */
        @JvmStatic
        fun snapshotOf(snapshot: MemorySnapshot): Map<String, Double> = with(snapshot) {
            linkedMapOf(
                "javaHeapUsed" to javaHeapUsed.toDouble(),
                "javaHeapTotal" to javaHeapTotal.toDouble(),
                "javaHeapMax" to javaHeapMax.toDouble(),
                "nativeHeapAllocated" to nativeHeapAllocated.toDouble(),
                "nativeHeapSize" to nativeHeapSize.toDouble(),
                "pss" to pss.toDouble(),
                "engines" to engines.toDouble(),
                "retainedRuntimes" to retainedRuntimes.toDouble(),
                "images" to images.toDouble(),
                "threads" to threads.toDouble(),
                "allThreads" to allThreads.toDouble(),
                "contexts" to contexts.toDouble(),
                "timestamp" to timestamp.toDouble(),
            )
        }

        /**
         * The script object of a retained runtime: every field of [RetainedRuntime], `exitKind` as "normal", "error" or "stopped".
         * zh-CN: 未回收 runtime 的脚本对象: [RetainedRuntime] 的每个字段, `exitKind` 为 "normal", "error" 或 "stopped".
         */
        @JvmStatic
        fun retainedOf(runtime: RetainedRuntime): Map<String, Any> = with(runtime) {
            linkedMapOf(
                "id" to id.toDouble(),
                "name" to name,
                "source" to source,
                "endedAt" to endedAt.toDouble(),
                "elapsedMs" to elapsedMs.toDouble(),
                "exitKind" to exitKind.id,
            )
        }
    }

}
