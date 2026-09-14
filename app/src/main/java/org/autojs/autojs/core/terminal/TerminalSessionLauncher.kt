package org.autojs.autojs.core.terminal

/**
 * Builds the argv of an interactive shell that starts in a chosen directory.
 *
 * The wrapper `cd`s into `$1` (falling back to `$HOME` when the directory is gone) and then
 * `exec`s the shell named by `$0`, so the pty child is the interactive shell itself and receives
 * signals directly. The profile named by `$ENV` is sourced by the shell on startup.
 *
 * zh-CN: 构建在指定目录启动的交互式 shell 的 argv. 包装脚本先 `cd` 到 `$1` (目录不存在时退回 `$HOME`),
 * 再 `exec` 由 `$0` 指定的 shell, 使 pty 子进程就是交互式 shell 本身并直接接收信号.
 */
object TerminalSessionLauncher {

    const val WRAPPER_SCRIPT = "cd -- \"\$1\" 2>/dev/null || cd \"\$HOME\"; exec \"\$0\""

    /**
     * @return argv including argv[0]; `argv[0]` doubles as the executable path
     */
    @JvmStatic
    @JvmOverloads
    fun buildCommand(cwd: String, shell: String = TerminalEnvironment.DEFAULT_SHELL): List<String> = listOf(
        shell,
        "-c",
        WRAPPER_SCRIPT,
        shell,
        cwd,
    )

}
