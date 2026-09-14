package org.autojs.autojs.core.terminal

/**
 * Builds the environment of a terminal session as an ordered map and renders it as an `envp` array.
 *
 * Inherited variables that are meaningless or harmful inside the pty (dynamic loader overrides,
 * Node options of the host process) are dropped, names are validated so a stray entry can never
 * produce a malformed `KEY=VALUE` string, and the terminal-specific variables are laid over the rest.
 *
 * zh-CN: 以有序 Map 构建终端会话环境变量并渲染为 `envp` 数组. 剔除在 pty 内无意义或有害的继承变量
 * (动态加载器覆盖项, 宿主进程的 Node 选项), 校验变量名以避免产生畸形的 `KEY=VALUE`, 再叠加终端专有变量.
 */
object TerminalEnvironment {

    const val DEFAULT_TERM = "xterm-256color"
    const val DEFAULT_LANG = "en_US.UTF-8"
    const val DEFAULT_SHELL = "/system/bin/sh"
    const val FALLBACK_PATH = "/system/bin:/system/xbin"

    /** Inherited variables that must never leak into the session. zh-CN: 不得泄漏到会话中的继承变量. */
    val DROPPED_INHERITED_NAMES: Set<String> = setOf(
        "LD_LIBRARY_PATH",
        "LD_PRELOAD",
        "NODE_OPTIONS",
        "NODE_PATH",
    )

    private val NAME_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val NUL = Char(0)

    /**
     * @param pathPrepend directories placed in front of the inherited `PATH`, first wins
     * @param extras      overlaid last; a null value removes the variable
     */
    data class Spec(
        val home: String,
        val prefix: String,
        val tmpDir: String,
        val profile: String,
        val pathPrepend: List<String> = emptyList(),
        val termType: String = DEFAULT_TERM,
        val lang: String = DEFAULT_LANG,
        val shell: String = DEFAULT_SHELL,
        val extras: Map<String, String?> = emptyMap(),
    )

    @JvmStatic
    @JvmOverloads
    fun build(spec: Spec, inherited: Map<String, String> = System.getenv()): LinkedHashMap<String, String> {
        val env = LinkedHashMap<String, String>()
        inherited.forEach { (name, value) ->
            if (name in DROPPED_INHERITED_NAMES) return@forEach
            if (!isValidName(name) || !isValidValue(value)) return@forEach
            env[name] = value
        }
        val inheritedPath = inherited["PATH"]?.takeIf { it.isNotBlank() && isValidValue(it) } ?: FALLBACK_PATH
        env["HOME"] = spec.home
        env["PREFIX"] = spec.prefix
        env["TMPDIR"] = spec.tmpDir
        env["PATH"] = (spec.pathPrepend + inheritedPath.split(':'))
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(":")
        env["TERM"] = spec.termType
        env["LANG"] = spec.lang
        env["ENV"] = spec.profile
        env["SHELL"] = spec.shell
        spec.extras.forEach { (name, value) ->
            require(isValidName(name)) { "Invalid environment variable name: $name" }
            if (value == null) {
                env.remove(name)
            } else {
                require(isValidValue(value)) { "Invalid value for environment variable: $name" }
                env[name] = value
            }
        }
        return env
    }

    /**
     * Renders the map as `KEY=VALUE` strings, rejecting anything that could not survive execve.
     * zh-CN: 将 Map 渲染为 `KEY=VALUE` 字符串, 拒绝任何无法通过 execve 的条目.
     */
    @JvmStatic
    fun toEnvp(env: Map<String, String>): Array<String> = env.entries.map { (name, value) ->
        require(isValidName(name)) { "Invalid environment variable name: $name" }
        require(isValidValue(value)) { "Environment variable contains NUL: $name" }
        "$name=$value"
    }.toTypedArray()

    @JvmStatic
    fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name)

    @JvmStatic
    fun isValidValue(value: String): Boolean = value.indexOf(NUL) < 0

}
