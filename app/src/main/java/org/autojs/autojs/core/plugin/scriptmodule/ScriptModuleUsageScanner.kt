package org.autojs.autojs.core.plugin.scriptmodule

import org.autojs.autojs.script.EncryptedScriptFileHeader
import java.io.File

/**
 * The plugin modules the scripts of a project name (roadmap P7.1, design
 * `docs/augment/P7-1-packaged-app-libraries-design.md` section 3): the string literals of `require(...)`,
 * `import ... from`, `export ... from`, `import '...'` and `import(...)`, and the global getters of [GLOBALS] used as
 * identifiers. A static scan: a name in a comment or a string adds a library (only its size), a computed name is
 * missed (the packaged app then explains the missing library when the script runs).
 *
 * zh-CN: 项目脚本中出现的插件模块名 (ROADMAP P7.1, 设计 P7-1 第 3 节): `require(...)`, `import ... from`, `export ... from`, `import '...'`
 * 与 `import(...)` 的字符串字面量, 以及作为标识符出现的 [GLOBALS] 全局 getter. 静态扫描: 注释或字符串中的名称会多带一个库 (只多出体积),
 * 拼接出的名称会漏掉 (打包应用运行到时说明缺少的库).
 */
internal object ScriptModuleUsageScanner {

    /** The libraries behind the global getters of the runtime (`ScriptRuntime.js_mod_*`), named as the libraries. */
    val GLOBALS = listOf("axios", "cheerio", "dayjs", "i18n", "pangu")

    val EXTENSIONS = setOf("js", "mjs", "cjs", "ts", "mts", "cts", "jsx", "tsx")

    const val MAX_FILE_BYTES = 4L * 1024 * 1024

    data class Usage(val moduleIds: Set<String>, val globals: Set<String>) {

        operator fun plus(other: Usage) = Usage(moduleIds + other.moduleIds, globals + other.globals)

        companion object {
            val EMPTY = Usage(emptySet(), emptySet())
        }
    }

    /** The module ids in [text], and the [GLOBALS] used as identifiers unless [globals] is false. */
    fun scan(text: CharSequence, globals: Boolean = true): Usage {
        val ids = LinkedHashSet<String>()
        MODULE_LITERALS.forEach { pattern -> pattern.findAll(text).forEach { ids += it.groupValues[2] } }
        val names = if (globals) GLOBAL_IDENTIFIER.findAll(text).mapTo(LinkedHashSet()) { it.value } else emptySet()
        return Usage(ids, names)
    }

    /**
     * The usage in the scripts of [source], a file or a directory whose [excludedDirs] (canonical) are skipped as in
     * the project archive: files with one of the [EXTENSIONS], at most [MAX_FILE_BYTES], not encrypted.
     */
    fun scan(source: File, excludedDirs: List<File> = emptyList(), checkCancelled: () -> Unit = {}): Usage {
        val files = if (source.isDirectory) {
            source.walkTopDown().onEnter { dir -> !isExcluded(dir, excludedDirs) }.filter { it.isFile }
        } else {
            sequenceOf(source)
        }
        return files.fold(Usage.EMPTY) { usage, file ->
            checkCancelled()
            readScript(file)?.let { usage + scan(it) } ?: usage
        }
    }

    /** The text of [file] if it is a script to scan; Latin-1 keeps the ASCII of any ASCII-compatible encoding. */
    private fun readScript(file: File): String? {
        if (file.extension.lowercase() !in EXTENSIONS || file.length() > MAX_FILE_BYTES) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size >= EncryptedScriptFileHeader.BLOCK_SIZE && EncryptedScriptFileHeader.isValidFile(bytes)) return null
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun isExcluded(dir: File, excludedDirs: List<File>): Boolean {
        if (excludedDirs.isEmpty()) return false
        val path = runCatching { dir.canonicalFile.path }.getOrDefault(dir.absolutePath)
        return excludedDirs.any { path == it.path || path.startsWith(it.path + File.separator) }
    }

    private const val LITERAL = """(['"`])([^'"`\r\n\\$]+)\1"""

    private val MODULE_LITERALS = listOf(
        Regex("""(?<![\w$])require\s*\(\s*$LITERAL\s*\)"""),
        Regex("""(?<![\w$.])import\s*\(?\s*$LITERAL"""),
        Regex("""(?<![\w$.])from\s*$LITERAL"""),
    )

    private val GLOBAL_IDENTIFIER = Regex("""(?<![\w$.])(?:${GLOBALS.joinToString("|")})(?![\w$])""")

}
