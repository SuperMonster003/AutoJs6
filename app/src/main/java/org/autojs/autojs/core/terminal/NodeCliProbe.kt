package org.autojs.autojs.core.terminal

import android.os.Build
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the plugin's Node.js launcher once (`--version`) to learn whether this device lets the
 * app start it, and classifies the outcome so the UI can explain a refusal instead of failing later
 * inside an interactive session.
 *
 * zh-CN: 运行一次插件的 Node.js 启动器 (`--version`), 判断当前设备是否允许应用启动它, 并对结果分类,
 * 以便界面能解释原因, 而不是在交互会话里才失败.
 */
object NodeCliProbe {

    const val DEFAULT_TIMEOUT_MS = 15_000L

    data class Result(
        val exitCode: Int?,
        val stdout: String,
        val stderr: String,
        val elapsedMs: Long,
        val error: String?,
        val timedOut: Boolean,
    ) {
        val succeeded: Boolean get() = exitCode == 0 && !timedOut && error == null

        /** The platform refused to execute the launcher. zh-CN: 平台拒绝执行启动器. */
        val denied: Boolean get() = classifyDenied(exitCode, error, stderr)

        val versionOutput: String? get() = stdout.trim().lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }

        fun summary(): String = when {
            succeeded -> "ok ${versionOutput.orEmpty()} (${elapsedMs} ms)"
            timedOut -> "timed out after $elapsedMs ms"
            error != null -> "failed to start: $error"
            else -> "exit $exitCode: ${stderr.trim().ifEmpty { stdout.trim() }.take(200)}"
        }
    }

    /**
     * Permission failures surface either as an `IOException` from `ProcessBuilder` (`error=13`),
     * or as the shell-style exit codes 126 / 127 with a "Permission denied" message.
     * zh-CN: 权限失败要么表现为 `ProcessBuilder` 的 `IOException` (`error=13`), 要么表现为退出码 126 / 127 并带 "Permission denied".
     */
    @JvmStatic
    fun classifyDenied(exitCode: Int?, error: String?, stderr: String): Boolean {
        val message = (error.orEmpty() + "\n" + stderr).lowercase()
        if ("permission denied" in message || "error=13" in message || "eacces" in message) return true
        return exitCode == 126 && "exec format" !in message
    }

    /**
     * @param env variables laid over the inherited process environment (`LD_*` overrides removed)
     */
    @JvmStatic
    @JvmOverloads
    fun run(
        executable: File,
        args: List<String> = listOf("--version"),
        env: Map<String, String> = emptyMap(),
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Result {
        val startedAt = System.currentTimeMillis()
        val builder = ProcessBuilder(listOf(executable.path) + args)
        builder.environment().apply {
            TerminalEnvironment.DROPPED_INHERITED_NAMES.forEach { remove(it) }
            putAll(env)
        }
        val process = try {
            builder.start()
        } catch (e: IOException) {
            return Result(null, "", "", elapsed(startedAt), e.message ?: e.javaClass.simpleName, false)
        }
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val readers = listOf(
            thread(name = "NodeCliProbe-stdout") { process.inputStream.bufferedReader().use { stdout.append(it.readText()) } },
            thread(name = "NodeCliProbe-stderr") { process.errorStream.bufferedReader().use { stderr.append(it.readText()) } },
        )
        val finished = try {
            waitForExit(process, timeoutMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!finished) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.destroyForcibly()
            } else {
                // Android 7 exposes only destroy() for terminating a Process.
                process.destroy()
            }
        }
        readers.forEach { runCatching { it.join(2_000) } }
        val exitCode = if (finished) process.exitValue() else null
        return Result(exitCode, stdout.toString(), stderr.toString(), elapsed(startedAt), null, !finished)
    }

    private fun elapsed(startedAt: Long) = System.currentTimeMillis() - startedAt

    /** Bounded process wait using APIs available on Android 7. */
    internal fun waitForExit(process: Process, timeoutMs: Long): Boolean {
        val startedAt = System.nanoTime()
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs.coerceAtLeast(0))
        while (true) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                val remaining = timeoutNanos - (System.nanoTime() - startedAt)
                if (remaining <= 0L) return false
                TimeUnit.NANOSECONDS.sleep(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(25)))
            }
        }
    }

}
