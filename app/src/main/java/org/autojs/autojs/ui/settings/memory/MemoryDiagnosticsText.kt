package org.autojs.autojs.ui.settings.memory

import android.content.Context
import android.os.Build
import org.autojs.autojs.runtime.memory.ExitKind
import org.autojs.autojs.runtime.memory.MemorySnapshot
import org.autojs.autojs.runtime.memory.RetainedBridgeSession
import org.autojs.autojs.runtime.memory.RetainedRuntime
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Values and the copied text of the memory diagnostics page (docs/memory/ROADMAP.md T10). Sizes are binary megabytes as in the
 * memory gate reports, numbers use ASCII digits.
 * zh-CN: 内存诊断页面的数值与复制文本 (docs/memory/ROADMAP.md T10). 大小与内存门禁报告一样按二进制 MB 计, 数字使用 ASCII 数字.
 */
internal object MemoryDiagnosticsText {

    fun bytes(value: Long): String = when {
        value < 0 -> "-"
        value < MB -> String.format(Locale.ROOT, "%.1f KB", value / 1024.0)
        else -> String.format(Locale.ROOT, "%.2f MB", value / MB.toDouble())
    }

    fun duration(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1000
        return when {
            seconds < 60 -> "$seconds s"
            seconds < 3600 -> "${seconds / 60} min ${seconds % 60} s"
            else -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
        }
    }

    fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.1f s", ms / 1000.0)

    fun count(value: Int): String = if (value < 0) "-" else value.toString()

    fun time(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(timestamp))

    fun exitKind(context: Context, kind: ExitKind): String = context.getString(
        when (kind) {
            ExitKind.NORMAL -> R.string.text_memory_exit_normal
            ExitKind.ERROR -> R.string.text_memory_exit_error
            ExitKind.STOPPED -> R.string.text_memory_exit_stopped
        }
    )

    fun javaHeap(context: Context, s: MemorySnapshot) =
        context.getString(R.string.text_memory_java_heap_value, bytes(s.javaHeapUsed), bytes(s.javaHeapTotal), bytes(s.javaHeapMax))

    fun nativeHeap(context: Context, s: MemorySnapshot) =
        context.getString(R.string.text_memory_native_heap_value, bytes(s.nativeHeapAllocated), bytes(s.nativeHeapSize))

    fun threads(context: Context, s: MemorySnapshot) =
        context.getString(R.string.text_memory_threads_value, count(s.threads), count(s.allThreads))

    /**
     * The name of an engine of [RetainedRuntime.engine] or [RetainedBridgeSession.engine] (docs/memory-phase2/ROADMAP.md P4.4).
     * zh-CN: [RetainedRuntime.engine] 或 [RetainedBridgeSession.engine] 中引擎的名称 (docs/memory-phase2/ROADMAP.md P4.4).
     */
    fun engineName(engine: String): String = when (engine) {
        "rhino" -> "Rhino"
        "node" -> "Node.js"
        "bun" -> "Bun"
        "quickjs" -> "QuickJS"
        "lua" -> "Lua"
        "python" -> "Python"
        else -> engine
    }

    fun retained(context: Context, r: RetainedRuntime) = context.getString(
        R.string.text_memory_retained_summary, engineName(r.engine), exitKind(context, r.exitKind), duration(r.elapsedMs), count(r.id), r.source,
    )

    /**
     * The total of [MemorySnapshot.bridgeSessions], followed by the engines with any, e.g. "2 (Lua 1, Python 1)".
     * zh-CN: [MemorySnapshot.bridgeSessions] 的总数, 后接有未回收会话的引擎, 例如 "2 (Lua 1, Python 1)".
     */
    fun bridgeSessions(s: MemorySnapshot): String {
        val engines = s.bridgeSessions.filterValues { it > 0 }.map { (engine, n) -> "${engineName(engine)} $n" }
        val total = count(s.bridgeSessions.values.sum())
        return if (engines.isEmpty()) total else "$total (${engines.joinToString(", ")})"
    }

    fun bridgeSession(context: Context, b: RetainedBridgeSession) = context.getString(
        R.string.text_memory_bridge_session_summary, duration(b.elapsedMs), b.executionId, b.objects.joinToString(", "),
    )

    /**
     * The text of "Copy diagnostics text": app and device, every snapshot value, LeakCanary in debug builds, the unreclaimed runtimes
     * and bridge sessions.
     * zh-CN: "复制诊断文本" 的内容: 应用与设备, 快照各项, debug 构建中的 LeakCanary 状态, 未回收的 runtime 与桥接会话列表.
     */
    fun report(
        context: Context,
        s: MemorySnapshot,
        retained: List<RetainedRuntime>,
        bridge: List<RetainedBridgeSession>,
        leakCanary: String?,
    ): String = buildString {
        appendLine("${context.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${context.getString(R.string.text_memory_diagnostics)}")
        appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(s.timestamp)))
        appendLine()
        line(context, R.string.text_memory_java_heap, javaHeap(context, s))
        line(context, R.string.text_memory_native_heap, nativeHeap(context, s))
        line(context, R.string.text_memory_pss, bytes(s.pss))
        line(context, R.string.text_memory_engines, count(s.engines))
        line(context, R.string.text_memory_retained_runtimes, count(s.retainedRuntimes))
        line(context, R.string.text_memory_bridge_sessions, bridgeSessions(s))
        line(context, R.string.text_memory_images, count(s.images))
        line(context, R.string.text_memory_threads, threads(context, s))
        line(context, R.string.text_memory_rhino_contexts, count(s.contexts))
        leakCanary?.let { appendLine("${context.getString(R.string.text_leakcanary)}: $it") }
        appendLine()
        appendLine("${context.getString(R.string.text_memory_retained_runtimes)}:")
        if (retained.isEmpty()) {
            appendLine("- ${context.getString(R.string.text_none)}")
        }
        retained.forEach { r ->
            appendLine("- ${r.name}: ${retained(context, r).replace('\n', ' ')}")
        }
        appendLine()
        appendLine("${context.getString(R.string.text_memory_bridge_sessions)}:")
        if (bridge.isEmpty()) {
            appendLine("- ${context.getString(R.string.text_none)}")
        }
        bridge.forEach { b ->
            appendLine("- ${engineName(b.engine)}: ${bridgeSession(context, b).replace('\n', ' ')}")
        }
    }

    private fun StringBuilder.line(context: Context, titleRes: Int, value: String) {
        appendLine("${context.getString(titleRes)}: $value")
    }

    private const val MB = 1024L * 1024L
}
