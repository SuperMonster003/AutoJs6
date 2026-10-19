package org.autojs.autojs.runtime.api.shell

import android.os.Build
import org.autojs.autojs.runtime.api.WrappedShizuku
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Starts the shell processes behind `new Shell(options)`, `shell.spawn` and `shizuku.spawn`: as the app (`sh`), through
 * `su` (root), or in the Shizuku user service (adb, with the uid Shizuku runs as), on pipes or (`pty`) on a
 * pseudo-terminal. `root` and `adb` combine the way Auto.js Pro does: `su` runs inside the Shizuku process.
 * zh-CN: 启动 `new Shell(options)`, `shell.spawn` 与 `shizuku.spawn` 背后的 shell 进程: 以应用身份 (`sh`), 经 `su` (root), 或在 Shizuku 用户服务中
 * (adb, 以 Shizuku 的运行身份), 使用管道或 (`pty`) 伪终端. `root` 与 `adb` 的组合与 Auto.js Pro 相同: 在 Shizuku 进程中运行 `su`.
 */
internal object ShellLauncher {

    data class Options(
        val root: Boolean = false,
        val adb: Boolean = false,
        /** stdout and stderr on a pseudo-terminal (see [PtyProcess]). zh-CN: stdout 与 stderr 接到伪终端 (见 [PtyProcess]). */
        val pty: Boolean = false,
        val env: Map<String, String> = emptyMap(),
        val cwd: String? = null,
        /** Auto.js Pro's `cmd`: the program line of an interactive shell, `sh` or `su` when absent. zh-CN: Auto.js Pro 的 `cmd`: 交互式 shell 的程序行, 缺省为 `sh` 或 `su`. */
        val cmd: String? = null,
    ) {
        init {
            env.keys.forEach { name ->
                require(ENV_NAME.matches(name)) { "Environment variable name \"$name\" is invalid" }
            }
        }

        val mode: String
            get() = when {
                adb && root -> "adb+root"
                adb -> "adb"
                root -> "root"
                else -> "sh"
            }

        val customCommand: String?
            get() = cmd?.trim()?.takeIf { it.isNotEmpty() }

        /** Whether the shell script itself applies [prelude], so nothing has to be written to stdin. zh-CN: shell 脚本本身是否应用 [prelude], 从而无需写入 stdin. */
        val embedsPrelude: Boolean
            get() = customCommand == null && (root || pty)
    }

    /**
     * An interactive shell reading commands from stdin: Pro's `cmd` as given, otherwise `sh`. Root shells are a tagged
     * `su -c` script (see [startRoot]). On a pseudo-terminal the script turns echo off and reads its commands as a script
     * file, so no prompt shows up; it is ready once it printed a marker, which is consumed here.
     * zh-CN: 从 stdin 读取命令的交互式 shell: 按原样使用 Pro 的 `cmd`, 否则为 `sh`. root shell 为带标记的 `su -c` 脚本 (见 [startRoot]). 在伪终端上, 脚本关闭
     * 回显并把命令当作脚本文件读取, 因此不会出现提示符; 它打印标记后即就绪, 标记在此处被读走.
     */
    fun startShell(options: Options): Process {
        options.customCommand?.let { return start(it.split(WHITESPACE), options.adb, options.pty) }
        if (!options.pty) {
            return when {
                options.root -> startRoot(options.adb, pty = false) { prelude(options) + "sh" }
                else -> start(listOf("sh"), options.adb, pty = false)
            }
        }
        val ready = "__autojs6_pty_ready_" + UUID.randomUUID().toString().replace("-", "") + "__"
        val script = NO_ECHO + prelude(options) + "echo $ready\n" + "sh /proc/self/fd/0"
        val process = when {
            options.root -> startRoot(options.adb, pty = true) { script }
            else -> start(listOf("sh", "-c", script), options.adb, pty = true)
        }
        try {
            awaitMarker(process, ready)
        } catch (t: Throwable) {
            ProcessTree.terminate(process)
            throw t
        }
        return process
    }

    /**
     * One command line through `sh -c` or `su -c`. The working directory and the environment are set inside the line,
     * because some `su` implementations reset both.
     * zh-CN: 经 `sh -c` 或 `su -c` 运行一条命令行. 工作目录与环境变量在命令行内设置, 因为部分 `su` 实现会重置二者.
     */
    fun startCommand(commandLine: String, options: Options): Process {
        val script = (if (options.pty) NO_ECHO else "") + prelude(options) + commandLine
        return when {
            options.root -> startRoot(options.adb, options.pty) { script }
            else -> start(listOf("sh", "-c", script), options.adb, options.pty)
        }
    }

    /**
     * Runs [script] through `su -c` after a no-op line carrying a unique marker. The marker stays in the command line of
     * the root shell, which lets [RootShellProcess] find and end it (and what it started) through another root shell.
     * zh-CN: 经 `su -c` 运行 [script], 前面加一行带唯一标记的空操作. 标记留在 root shell 的命令行中, [RootShellProcess] 借此经另一个 root shell 找到并结束
     * 它 (及其启动的进程).
     */
    private fun startRoot(adb: Boolean, pty: Boolean, script: () -> String): Process {
        val marker = ROOT_MARKER_KEY + "=" + UUID.randomUUID().toString().replace("-", "")
        val process = start(listOf("su", "-c", ": $marker\n" + script()), adb, pty)
        return RootShellProcess(process) { start(listOf("su", "-c", rootKillerScript(marker)), adb, pty = false) }
    }

    /**
     * A root shell script that ends every process whose command line contains [marker] (toybox `pgrep -f`), with its
     * descendants. Each process is stopped before its children are collected, so a loop cannot start new ones meanwhile.
     * The marker is split by quotes in the script, so the script does not match itself. `@` stands for the dollar sign.
     * zh-CN: 一个 root shell 脚本, 结束命令行中包含 [marker] 的所有进程 (toybox `pgrep -f`) 及其子孙进程. 收集子进程前先暂停每个进程, 使循环无法在此期间
     * 启动新的子进程. 脚本中的标记被引号拆开, 因此脚本不会匹配自身. `@` 代表美元符号.
     */
    private fun rootKillerScript(marker: String): String {
        val (key, value) = marker.split('=', limit = 2)
        return listOf(
            "m=\"$key\"\"=$value\"",
            "kill_tree() {",
            "  kill -STOP \"@1\" 2>/dev/null",
            "  for c in @(pgrep -P \"@1\"); do kill_tree \"@c\"; done",
            "  kill -KILL \"@1\" 2>/dev/null",
            "}",
            "for p in @(pgrep -f \"@m\"); do kill_tree \"@p\"; done",
        ).joinToString("\n").replace('@', '$')
    }

    /** Reads the start of the output up to the line with [marker]; nothing else is printed before it. zh-CN: 读取输出开头直到含 [marker] 的行; 在它之前不会打印其他内容. */
    private fun awaitMarker(process: Process, marker: String) {
        val input = process.inputStream
        val seen = ByteArrayOutputStream()
        val target = marker.toByteArray(Charsets.UTF_8)
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("The shell ended before it was ready: " + seen.toString(Charsets.UTF_8.name()).trim().take(200))
            seen.write(b)
            if (b == '\n'.code && seen.toByteArray().let { bytes -> indexOf(bytes, target) >= 0 }) return
        }
    }

    private fun indexOf(bytes: ByteArray, target: ByteArray): Int {
        outer@ for (i in 0..bytes.size - target.size) {
            for (j in target.indices) if (bytes[i + j] != target[j]) continue@outer
            return i
        }
        return -1
    }

    /** `cd` and `export` lines for [options]. zh-CN: [options] 对应的 `cd` 与 `export` 行. */
    fun prelude(options: Options): String = buildString {
        options.cwd?.let { append("cd ").append(quote(it)).append(" || exit 1").append('\n') }
        options.env.forEach { (name, value) -> append("export ").append(name).append('=').append(quote(value)).append('\n') }
    }

    fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun start(command: List<String>, adb: Boolean, pty: Boolean): Process = when {
        adb -> WrappedShizuku.newProcess(command.toTypedArray(), pty = pty)
        pty -> PtyProcess.start(command)
        else -> ProcessBuilder(command).start()
    }

    /**
     * [PtyTermios] already sets this on the host's terminal; the line covers the terminal Magisk's su opens for the root
     * shell. Only from Android 10: toybox 0.7.x stty (Android 9 and below) fails as the app, and as root it sets wrong
     * flags (`iuclc`, which turns input into lower case).
     * zh-CN: [PtyTermios] 已在宿主终端上设置; 此行覆盖 Magisk su 为 root shell 打开的终端. 仅用于 Android 10 及以上: toybox 0.7.x 的 stty (Android 9 及以下)
     * 以应用身份运行时失败, 以 root 运行时会设置错误的标志 (`iuclc`, 把输入转为小写).
     */
    private val NO_ECHO = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) "stty -echo -onlcr 2>/dev/null\n" else ""
    private const val ROOT_MARKER_KEY = "aj6root"
    private val WHITESPACE = Regex("\\s+")
    private val ENV_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

}
