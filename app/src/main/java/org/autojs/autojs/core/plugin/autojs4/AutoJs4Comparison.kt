package org.autojs.autojs.core.plugin.autojs4

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.ScriptExecutionListener
import org.autojs.autojs.script.AutoJs4ScriptSource
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.autojs.autojs.ui.autojs4.AutoJs4ComparisonActivity
import org.autojs.autojs.ui.autojs4.AutoJs4RuntimePluginUi
import org.autojs.autojs.util.DialogUtils.widgetThemeColor
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * "Run with AutoJs4 and compare" (roadmap P6.1): runs the same file or project first with the AutoJs6
 * engine and then through the AutoJs4 Runtime plugin, captures the host console lines of both runs and
 * shows a line diff with the durations in [AutoJs4ComparisonActivity]. Each run is given
 * [TIMEOUT_SECONDS]; a run that does not finish in time is force-stopped and reported as timed out.
 *
 * zh-CN: "使用 AutoJs4 运行并对照" (roadmap P6.1): 同一文件或项目先用 AutoJs6 引擎运行, 再经 AutoJs4 Runtime 插件
 * 运行, 采集两次运行回到宿主控制台的行, 在 [AutoJs4ComparisonActivity] 中显示逐行差异与用时. 每次运行最多等待
 * [TIMEOUT_SECONDS] 秒, 超时的运行被强制停止并标记为超时.
 */
object AutoJs4Comparison {

    const val ENGINE_AUTOJS6 = "AutoJs6"
    const val ENGINE_AUTOJS4 = "AutoJs4"
    const val TIMEOUT_SECONDS = 120L

    private const val AUTOJS4_LOG_PREFIX = "[AutoJs4] "
    private const val MAX_LCS_CELLS = 4_000_000L

    /** Timing lines the host prints while initializing its own runtime (RhinoJavaScriptEngine.withTimeConsuming). */
    private val HOST_TIMING_LINE = Regex("""^(runtime-init-prologue|script-init|runtime-init-epilogue): \d+$""")

    enum class Outcome { SUCCEEDED, FAILED, TIMED_OUT }

    class Side(
        val engine: String,
        val lines: List<String>,
        val durationMillis: Long,
        val outcome: Outcome,
        val detail: String?,
    )

    /** One diff line: [tag] is ' ' (same), '-' (AutoJs6 only) or '+' (AutoJs4 only). */
    class DiffLine(val tag: Char, val text: String)

    class Result(
        val targetPath: String,
        val autoJs6: Side,
        val autoJs4: Side,
        val diff: List<DiffLine>,
    ) {
        val differences: Int get() = diff.count { it.tag != ' ' }
        val identical: Boolean get() = differences == 0
    }

    /** The most recent result, read by [AutoJs4ComparisonActivity]. zh-CN: 最近一次结果, 由结果页读取. */
    @Volatile
    var latest: Result? = null
        private set

    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Starts a comparison of [target] (a script file or a project directory) from the UI thread. */
    fun start(context: Context, target: File) {
        if (!AutoJs4RuntimePluginUi.ensureAvailableForInteractiveLaunch(context)) return
        if (!running.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val dialog = try {
            MaterialDialog.Builder(context)
                .title(R.string.text_autojs4_comparison)
                .content(context.getString(R.string.text_autojs4_comparison_running_with, ENGINE_AUTOJS6))
                .progress(true, 0)
                .cancelable(false)
                .widgetThemeColor()
                .show()
        } catch (e: RuntimeException) {
            running.set(false)
            throw e
        }
        Thread({
            try {
                val autoJs6 = runSide(appContext, ENGINE_AUTOJS6) { listener -> executeWithAutoJs6(target, listener) }
                mainHandler.post {
                    runCatching { dialog.setContent(appContext.getString(R.string.text_autojs4_comparison_running_with, ENGINE_AUTOJS4)) }
                }
                val autoJs4 = runSide(appContext, ENGINE_AUTOJS4) { listener -> executeWithAutoJs4(target, listener) }
                latest = Result(target.path, autoJs6, autoJs4, diff(autoJs6.lines, autoJs4.lines))
                mainHandler.post {
                    runCatching { dialog.dismiss() }
                    AutoJs4ComparisonActivity.launch(appContext)
                }
            } catch (e: Throwable) {
                mainHandler.post {
                    runCatching { dialog.dismiss() }
                    ViewUtils.showToast(appContext, e.message ?: e.toString(), true)
                }
            } finally {
                running.set(false)
            }
        }, "autojs4-comparison").start()
    }

    private fun executeWithAutoJs6(target: File, listener: ScriptExecutionListener): ScriptExecution? {
        val file = if (target.isDirectory) {
            AutoJs4ScriptSource.forProject(target)?.file
                ?: throw IllegalArgumentException(AutoJs.instance.application.getString(R.string.error_autojs4_project_main_missing))
        } else {
            target
        }
        val launchStartedAt = android.os.SystemClock.elapsedRealtime()
        val persisted = ScriptLaunchSourceFactory.capturePersistedLaunchContext(file.path)
        val launch = ScriptLaunchSourceFactory.createPersistedPathLaunch(
            path = file.path,
            sourceKind = persisted.sourceKind,
            projectRoot = persisted.projectRoot,
        )
        val config = launch.createExecutionConfig().apply { nodeLaunchStartedAtMs = launchStartedAt }
        return AutoJs.instance.scriptEngineService.execute(launch.source, listener, config)
    }

    private fun executeWithAutoJs4(target: File, listener: ScriptExecutionListener): ScriptExecution? {
        val source = if (target.isDirectory) {
            AutoJs4ScriptSource.forProject(target)
                ?: throw IllegalArgumentException(AutoJs.instance.application.getString(R.string.error_autojs4_project_main_missing))
        } else {
            AutoJs4ScriptSource.forFile(target)
        }
        val config = ExecutionConfig(workingDirectory = source.workingDirectory)
        return AutoJs.instance.scriptEngineService.execute(source, listener, config)
    }

    private fun runSide(context: Context, engine: String, launch: (ScriptExecutionListener) -> ScriptExecution?): Side {
        val console = AutoJs.instance.globalConsole
        val captured = ArrayList<String>()
        val capturing = AtomicBoolean(true)
        val logListener = object : ConsoleImpl.LogListener {
            override fun onNewLog(logEntry: ConsoleImpl.LogEntry) {
                if (capturing.get()) synchronized(captured) { captured.add(logEntry.content.toString()) }
            }

            override fun onLogClear() = Unit
        }
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val startedAt = AtomicReference<Long?>()
        val listener = object : ScriptExecutionListener {
            override fun onStart(execution: ScriptExecution) {
                startedAt.compareAndSet(null, System.currentTimeMillis())
            }

            override fun onSuccess(execution: ScriptExecution, result: Any?) = finished.countDown()

            override fun onException(execution: ScriptExecution, e: Throwable) {
                failure.set(e)
                finished.countDown()
            }
        }
        console.addLogListener(logListener)
        val launchedAt = System.currentTimeMillis()
        val execution = try {
            launch(listener)
        } catch (e: Throwable) {
            console.removeLogListener(logListener)
            return Side(engine, emptyList(), 0, Outcome.FAILED, e.message ?: e.toString())
        }
        if (execution == null) {
            console.removeLogListener(logListener)
            return Side(engine, emptyList(), 0, Outcome.FAILED, null)
        }
        val completed = finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
            runCatching { execution.engine?.forceStop() }
            finished.await(5, TimeUnit.SECONDS)
        }
        capturing.set(false)
        console.removeLogListener(logListener)
        val duration = System.currentTimeMillis() - (startedAt.get() ?: launchedAt)
        val lines = normalize(context, synchronized(captured) { captured.toList() }, engine)
        val outcome = when {
            !completed -> Outcome.TIMED_OUT
            failure.get() != null -> Outcome.FAILED
            else -> Outcome.SUCCEEDED
        }
        return Side(engine, lines, duration, outcome, failure.get()?.let { it.message ?: it.toString() })
    }

    /**
     * Drops the host's own framing lines ("Running [...]", "[...] finished in ...") and the engine start line of
     * AutoJs4, and removes the `[AutoJs4] ` prefix so that both sides can be compared line by line.
     * zh-CN: 去掉宿主自身的起止行与 AutoJs4 引擎的起始行, 并去掉 `[AutoJs4] ` 前缀, 使两侧可以逐行对照.
     */
    private fun normalize(context: Context, lines: List<String>, engine: String): List<String> {
        val startRunning = context.getString(R.string.text_start_running) + " ["
        val finishedTemplate = context.getString(R.string.text_execution_finished).trim()
        val finishedPattern = Regex(finishedTemplate.split("%s").joinToString(".*") { Regex.escape(it) })
        val result = ArrayList<String>(lines.size)
        for (raw in lines) {
            var line = raw.trimEnd()
            if (engine == ENGINE_AUTOJS4 && line.startsWith(AUTOJS4_LOG_PREFIX)) {
                line = line.removePrefix(AUTOJS4_LOG_PREFIX)
            }
            if (line.startsWith(startRunning) || finishedPattern.containsMatchIn(line)) continue
            if (engine == ENGINE_AUTOJS6 && HOST_TIMING_LINE.matches(line)) continue
            if (engine == ENGINE_AUTOJS4 && (line.startsWith("Script Running[") || line.startsWith("开始运行["))) continue
            result.add(line)
        }
        return result
    }

    /** Line diff by longest common subsequence; very large inputs fall back to a positional comparison. */
    fun diff(a: List<String>, b: List<String>): List<DiffLine> {
        val n = a.size
        val m = b.size
        val out = ArrayList<DiffLine>(n + m)
        if (n.toLong() * m > MAX_LCS_CELLS) {
            for (i in 0 until maxOf(n, m)) {
                val left = a.getOrNull(i)
                val right = b.getOrNull(i)
                when {
                    left != null && left == right -> out.add(DiffLine(' ', left))
                    else -> {
                        left?.let { out.add(DiffLine('-', it)) }
                        right?.let { out.add(DiffLine('+', it)) }
                    }
                }
            }
            return out
        }
        val table = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                table[i][j] = if (a[i] == b[j]) table[i + 1][j + 1] + 1 else maxOf(table[i + 1][j], table[i][j + 1])
            }
        }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    out.add(DiffLine(' ', a[i])); i++; j++
                }
                table[i + 1][j] >= table[i][j + 1] -> {
                    out.add(DiffLine('-', a[i])); i++
                }
                else -> {
                    out.add(DiffLine('+', b[j])); j++
                }
            }
        }
        while (i < n) out.add(DiffLine('-', a[i++]))
        while (j < m) out.add(DiffLine('+', b[j++]))
        return out
    }
}
