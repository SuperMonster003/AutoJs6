package org.autojs.autojs.runtime.api.augment.memory

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.leakcanary.LeakCanarySetup
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.memory.MemoryDiagnostics
import org.autojs.autojs.runtime.memory.MemoryDiagnosticsCore
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs6.BuildConfig
import java.io.File

/**
 * What the `memory` module ([Memory]) needs from the host, replaced by a fake in JVM tests.
 * zh-CN: `memory` 模块 ([Memory]) 依赖的宿主能力, JVM 测试中以替身代替.
 */
internal interface MemoryScriptPlatform {

    /** The process-wide diagnostics ([MemoryDiagnostics]). zh-CN: 进程级内存诊断. */
    val diagnostics: MemoryDiagnosticsCore

    val isDebugBuild: Boolean

    /** Developer options > Memory diagnostics > "Allow scripts to export hprof". zh-CN: 开发者选项 > 内存诊断 > "允许脚本导出 hprof". */
    fun isScriptHeapDumpAllowed(): Boolean

    fun currentTimeMillis(): Long

    /** [path] resolved like the other file paths of the script (relative to its working directory). zh-CN: 与脚本的其他文件路径一样解析 [path]. */
    fun resolvePath(path: String): File

    fun dumpHeap(file: File): File

    /** The name of the calling script, for the default LeakCanary description. zh-CN: 调用方脚本的名称, 用于默认的 LeakCanary 说明. */
    fun scriptName(): String

    /** Hands [obj] to LeakCanary; false when LeakCanary is not watching (release builds, or turned off). zh-CN: 把 [obj] 交给 LeakCanary; LeakCanary 未在监视时返回 false. */
    fun watch(obj: Any, description: String): Boolean

    /** Throws [ScriptInterruptedException] once the calling script is stopped. zh-CN: 调用方脚本被停止后抛出 [ScriptInterruptedException]. */
    fun checkInterrupted()
}

internal class AndroidMemoryScriptPlatform(private val scriptRuntime: ScriptRuntime) : MemoryScriptPlatform {

    override val diagnostics: MemoryDiagnosticsCore
        get() = MemoryDiagnostics

    override val isDebugBuild: Boolean
        get() = BuildConfig.DEBUG

    override fun isScriptHeapDumpAllowed() = DeveloperKeys.MEMORY_SCRIPT_HEAP_DUMP.get()

    override fun currentTimeMillis() = System.currentTimeMillis()

    override fun resolvePath(path: String) = File(scriptRuntime.files.nonNullPath(path))

    override fun dumpHeap(file: File) = MemoryDiagnostics.dumpHeap(file)

    override fun scriptName(): String {
        val engine = runCatching { scriptRuntime.engines.myEngine() }.getOrNull() ?: return "unknown script"
        val source = engine.getTag(ScriptEngine.TAG_SOURCE) as? ScriptSource
        return runCatching { source?.name }.getOrNull() ?: "#${engine.id}"
    }

    override fun watch(obj: Any, description: String): Boolean {
        if (LeakCanarySetup.status(GlobalAppContext.get())?.enabled != true) return false
        LeakCanarySetup.watch(obj, description)
        return true
    }

    override fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw ScriptInterruptedException()
    }
}
