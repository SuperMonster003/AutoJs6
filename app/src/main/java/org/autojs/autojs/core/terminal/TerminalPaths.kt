package org.autojs.autojs.core.terminal

import android.content.Context
import java.io.File

/**
 * Directory layout of the in-app terminal, rooted at `<filesDir>/terminal`.
 *
 * ```
 * terminal/
 *   home/                       $HOME
 *   usr/                        $PREFIX
 *     bin/                      command links (node / npm / ... once the plugin provides them)
 *     etc/profile               sourced by the interactive shell through $ENV
 *     tmp/                      $TMPDIR
 *     lib/autojs6-node-cli/     extracted npm + corepack
 *     lib/node_modules/         npm global installs
 *     .npm/  .corepack/         package manager caches
 * ```
 *
 * zh-CN: 应用内终端的目录布局, 根目录为 `<filesDir>/terminal`.
 */
class TerminalPaths(val root: File) {

    val home: File = File(root, "home")
    val prefix: File = File(root, "usr")
    val bin: File = File(prefix, "bin")
    val etc: File = File(prefix, "etc")
    val profile: File = File(etc, "profile")
    val tmp: File = File(prefix, "tmp")
    val lib: File = File(prefix, "lib")
    val nodeCliRoot: File = File(lib, "autojs6-node-cli")
    val globalNodeModules: File = File(lib, "node_modules")
    val npmCache: File = File(prefix, ".npm")
    val corepackHome: File = File(prefix, ".corepack")

    /**
     * Creates the directories the shell expects and writes the default profile once.
     * zh-CN: 创建 shell 所需目录, 并在首次时写入默认 profile.
     */
    fun ensureLayout(): TerminalPaths {
        listOf(home, bin, etc, tmp, lib).forEach { it.mkdirs() }
        if (!profile.exists() || profile.readText() == LEGACY_DEFAULT_PROFILE) {
            profile.writeText(DEFAULT_PROFILE)
        }
        return this
    }

    /**
     * Removes everything under the terminal root, including caches and installed packages.
     * zh-CN: 删除终端根目录下的全部内容, 包括缓存与已安装的包.
     */
    fun clearAll(): Boolean = root.deleteRecursively()

    /**
     * Renders [path] relative to `$HOME` using `~`, mirroring what the prompt shows.
     * zh-CN: 以 `~` 表示相对 `$HOME` 的路径, 与提示符显示一致.
     */
    fun toTildePath(path: String): String {
        val abbreviated = toTildePath(path, home.path)
        return if (abbreviated != path) abbreviated else toTildePath(path, runCatching { home.canonicalPath }.getOrDefault(home.path))
    }

    companion object {

        const val ROOT_DIR_NAME = "terminal"

        /**
         * Interactive-shell profile. mksh reads it through `$ENV`; the prompt shows the cwd with `~`.
         * zh-CN: 交互式 shell 的 profile. mksh 通过 `$ENV` 读取; 提示符以 `~` 显示当前目录.
         */
        internal val LEGACY_DEFAULT_PROFILE: String = """
            |# AutoJs6 terminal profile (managed by AutoJs6, edits may be overwritten)
            |PS1='${'$'}(case "${'$'}PWD" in "${'$'}HOME") printf "~";; "${'$'}HOME"/*) printf "~%s" "${'$'}{PWD#"${'$'}HOME"}";; *) printf "%s" "${'$'}PWD";; esac) ${'$'} '
            |HISTFILE="${'$'}HOME/.sh_history"
            |HISTSIZE=1000
            |export HISTFILE HISTSIZE
            |
        """.trimMargin()

        // Keep the directory on its own line so shell editing has the full terminal width.
        // Only migrate the exact managed profile; preserve user customizations.
        val DEFAULT_PROFILE: String = LEGACY_DEFAULT_PROFILE.replace("; esac) ", "; esac)\n")

        @JvmStatic
        fun of(context: Context): TerminalPaths = TerminalPaths(File(context.filesDir, ROOT_DIR_NAME))

        @JvmStatic
        fun toTildePath(path: String, home: String): String = when {
            path == home -> "~"
            path.startsWith("$home/") -> "~" + path.substring(home.length)
            else -> path
        }

    }

}
