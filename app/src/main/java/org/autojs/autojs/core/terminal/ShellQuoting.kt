package org.autojs.autojs.core.terminal

/**
 * POSIX shell quoting helpers for commands the app types into the terminal on the user's behalf.
 * zh-CN: 供应用代替用户向终端键入命令时使用的 POSIX shell 引用工具.
 */
object ShellQuoting {

    private val SAFE_WORD = Regex("[A-Za-z0-9_@%+=:,./-]+")

    /** Quotes a single argument so the shell sees it verbatim. zh-CN: 引用单个参数使 shell 原样接收. */
    @JvmStatic
    fun quote(arg: String): String = when {
        arg.isEmpty() -> "''"
        SAFE_WORD.matches(arg) -> arg
        else -> "'" + arg.replace("'", "'\\''") + "'"
    }

    /** Joins arguments into one command line. zh-CN: 将参数拼接为一条命令行. */
    @JvmStatic
    fun join(args: List<String>): String = args.joinToString(" ", transform = ::quote)

    @JvmStatic
    fun join(vararg args: String): String = join(args.toList())

}
