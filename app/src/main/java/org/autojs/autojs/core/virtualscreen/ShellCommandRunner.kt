package org.autojs.autojs.core.virtualscreen

/**
 * A short `sh -c` command run in the Shizuku user service process, i.e. with the shell (uid 2000)
 * or root identity. Used as the fallback when a hidden framework API is unavailable on a ROM.
 *
 * zh-CN: 在 Shizuku 用户服务进程 (shell uid 2000 或 root 身份) 中执行一条短命令, 用作系统隐藏接口不可用时的兜底.
 */
object ShellCommandRunner {

    data class Result(val code: Int, val output: String) {
        val ok: Boolean get() = code == 0
    }

    fun run(command: String): Result = try {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        Result(process.waitFor(), output)
    } catch (t: Throwable) {
        Result(-1, t.message ?: t.javaClass.simpleName)
    }

    /** Package names and components are the only values interpolated into shell commands. */
    private val SAFE_TOKEN = Regex("[A-Za-z0-9_.:/]+")

    fun isSafeToken(token: String?) = token != null && SAFE_TOKEN.matches(token)

}
