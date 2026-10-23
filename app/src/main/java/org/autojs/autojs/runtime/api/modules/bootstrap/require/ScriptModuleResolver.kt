package org.autojs.autojs.runtime.api.modules.bootstrap.require

import java.io.File

/**
 * The local lookup of `require(id)` (roadmap P3.7, design `docs/augment/P3-7-require-design.md` (a)): the rules and the
 * order of `assets/modules/jvm-npm.js`, in plain Kotlin so that they can be tested on temporary directories.
 *
 * For each root of `[base, ...paths]` it tries the file `<root>/<id>` with `.js`, `.mjs` and `.json` appended (none when
 * the id already ends with one of them, in any case), the directory `<root>/<id>` (`main` of its `package.json`, else
 * `index.js`), and `<dir>/node_modules/<id>` for the root and each of its ancestors; an absolute id is not joined to the
 * root and is also tried as a directory. A class loader resource `<id>.js` (jvm-npm's core module) comes before all of
 * them. Results are canonical paths (appendix C Q82).
 *
 * The probes are those of jvm-npm except that none is made under a directory known to be missing (a path through a
 * missing directory cannot exist, so the result is the same): a typical miss stats 15 - 20 paths instead of about 51.
 *
 * zh-CN: `require(id)` 的本地解析 (ROADMAP P3.7, 设计文档 (a)): 规则与顺序同 `assets/modules/jvm-npm.js`, 纯 Kotlin 实现, 可在临时目录上测试.
 * 对 `[base, ...paths]` 中每个 root 依次尝试: 文件 `<root>/<id>` 补 `.js`, `.mjs`, `.json` (id 已带其一时不补, 不分大小写), 目录 `<root>/<id>`
 * (package.json 的 main, 否则 index.js), 以及 root 与其各级上级目录下的 `node_modules/<id>`; 绝对路径的 id 不拼 root, 并且还按目录尝试.
 * 类加载器资源 `<id>.js` (jvm-npm 的 core module) 先于以上各项. 结果为规范路径 (附录 C Q82). 探测与 jvm-npm 相同, 只是已知不存在的目录下不再探测
 * (经过不存在的目录的路径不可能存在, 结果不变): 典型的找不到由约 51 次 stat 降为 15 - 20 次.
 *
 * @param packageMain the `main` of a `package.json` (given by its canonical path), or null when it has none or a falsy
 *   one; it throws when the file cannot be read or parsed, or when `main` is not a string, which [resolve] reports as a
 *   [PackageError].
 */
internal class ScriptModuleResolver(
    private val packageMain: (path: String) -> String?,
    private val disk: Disk = Disk.LOCAL,
    private val classResource: (name: String) -> Boolean = { name -> Thread.currentThread().contextClassLoader?.getResource(name) != null },
) {

    /** The file system seam, so that tests can count the probes. zh-CN: 文件系统接缝, 供测试统计探测次数. */
    interface Disk {
        fun exists(file: File): Boolean
        fun isDirectory(file: File): Boolean
        fun canonical(file: File): String

        companion object {
            val LOCAL = object : Disk {
                override fun exists(file: File) = file.exists()
                override fun isDirectory(file: File) = file.isDirectory
                override fun canonical(file: File): String = file.canonicalPath
            }
        }
    }

    sealed class Resolved {
        /** A file module, by its canonical path. zh-CN: 文件模块, 以规范路径表示. */
        data class FileModule(val path: String) : Resolved()

        /** A class loader resource (jvm-npm's core module), by its resource name. zh-CN: 类加载器资源 (jvm-npm 的 core module), 以资源名表示. */
        data class ResourceModule(val name: String) : Resolved()
    }

    /** A `package.json` that cannot be read or parsed (jvm-npm's PARSE_ERROR). zh-CN: 无法读取或解析的 package.json (jvm-npm 的 PARSE_ERROR). */
    class PackageError(cause: Throwable) : RuntimeException(cause.message, cause)

    /**
     * The module [id] seen from the directory [base] (null as jvm-npm's empty root), then from each of [paths]; null when
     * nothing matches.
     */
    fun resolve(id: String, base: String?, paths: List<String>): Resolved? {
        val resource = normalizeName(id, JS)
        if (classResource(resource)) return Resolved.ResourceModule(resource)
        val lookup = Lookup()
        for (root in listOf(base) + paths) {
            val result = with(lookup) {
                asFile(id, root, JS) ?: asFile(id, root, MJS) ?: asFile(id, root, JSON) ?: asDirectory(id, root) ?: asNodeModule(id, root)
            }
            if (result != null) return Resolved.FileModule(result)
        }
        return null
    }

    /** The probes of one [resolve] call, which remembers the directories it has checked. zh-CN: 一次 [resolve] 调用的探测, 记住已检查过的目录. */
    private inner class Lookup {

        private val directories = HashMap<String, Boolean>()

        private fun isDirectory(path: String) = directories.getOrPut(File(path).path) { disk.isDirectory(File(path)) }

        private fun existing(path: String): String? {
            val file = File(path)
            val parent = file.parent
            if (parent != null && !isDirectory(parent)) return null
            return if (disk.exists(file)) disk.canonical(file) else null
        }

        fun asFile(id: String, root: String?, ext: String): String? = when {
            !isAbsolute(id) -> existing(join(root, normalizeName(id, ext)))
            else -> existing(normalizeName(id, ext)) ?: asDirectory(id, null)
        }

        fun asDirectory(id: String, root: String?): String? {
            val base = if (root == null && isAbsolute(id)) id else join(root, id)
            if (!isDirectory(base)) return null
            val pkg = File(join(base, PACKAGE_JSON))
            if (disk.exists(pkg)) {
                try {
                    val main = packageMain(disk.canonical(pkg)) ?: return asFile(INDEX_JS, base, JS)
                    return asFile(main, base, JS) ?: asDirectory(main, base)
                } catch (e: Exception) {
                    throw PackageError(e)
                }
            }
            return asFile(INDEX_JS, base, JS)
        }

        fun asNodeModule(id: String, root: String?): String? {
            val base = join(root, NODE_MODULES)
            if (isDirectory(base)) {
                (asFile(id, base, JS) ?: asDirectory(id, base))?.let { return it }
            }
            return if (root.isNullOrEmpty()) null else asNodeModule(id, File(root).parent)
        }
    }

    companion object {

        const val JS = ".js"
        const val MJS = ".mjs"
        const val JSON = ".json"

        private const val INDEX_JS = "index.js"
        private const val PACKAGE_JSON = "package.json"
        private const val NODE_MODULES = "node_modules"

        private val KNOWN_EXTENSION = Regex("""\.(?:js|mjs|json)\z""", RegexOption.IGNORE_CASE)

        fun hasKnownExtension(name: String) = KNOWN_EXTENSION.containsMatchIn(name)

        /** [name], with [ext] appended unless it already ends with a known extension (jvm-npm's normalizeName). */
        fun normalizeName(name: String, ext: String = JS): String = when {
            hasKnownExtension(name) || name.endsWith(ext) -> name
            else -> name + ext
        }

        /** Whether [id] is absolute: it starts with the separator (jvm-npm's test), or is absolute on this platform. */
        fun isAbsolute(id: String) = id.startsWith(File.separator) || File(id).isAbsolute

        /** `[root, name].join(separator)` of jvm-npm, an absent root being empty. */
        fun join(root: String?, name: String) = (root ?: "") + File.separator + name
    }

}
