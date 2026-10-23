package org.autojs.autojs.runtime.api.modules.bootstrap.require

import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptModuleResolver.Companion.JS
import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptModuleResolver.Companion.JSON
import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptModuleResolver.Companion.MJS
import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptModuleResolver.Companion.join
import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptModuleResolver.Companion.normalizeName
import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptModuleResolver.Disk
import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptModuleResolver.Resolved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Roadmap P3.7 (design (a)): the local lookup of the Kotlin `require` on temporary directories. Its results are those of a
 * literal port of jvm-npm's `resolve` (no pruning) for relative and bare ids, files, directories, `package.json`,
 * `node_modules` and the extra paths, with fewer probes; absolute ids, class loader resources and `package.json` errors are
 * checked on their own.
 *
 * zh-CN: ROADMAP P3.7 (设计 (a)): 在临时目录上验证 Kotlin require 的本地解析. 对相对与裸 id, 文件, 目录, package.json, node_modules
 * 与额外路径, 结果与逐行移植的 jvm-npm resolve (不剪枝) 相同, 探测更少; 绝对路径 id, 类加载器资源与 package.json 错误单独验证.
 */
class ScriptModuleResolverTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var lib: File
    private val disk = CountingDisk()

    @Before
    fun setUp() {
        root = temp.newFolder("root").canonicalFile
        lib = temp.newFolder("lib").canonicalFile
        write("a.js", "a.mjs", "m.mjs", "d.json", "both.json", "both.mjs")
        write("dir/package.json" to """{"main": "lib/entry"}""", "dir/lib/entry.js" to "")
        write("dir2/index.js")
        write("dir3/package.json" to """{"main": "sub"}""", "dir3/sub/index.js" to "")
        write("dir4/package.json" to "{}", "dir4/index.js" to "")
        write("dir5/package.json" to """{"main": "missing.js"}""", "dir5/index.js" to "")
        write("badpkg/package.json" to "{bad", "badpkg/index.js" to "")
        write("node_modules/pkg/index.js", "node_modules/top.js", "x/node_modules/pkg/index.js", "x/y/z/deep.js")
        File(lib, "libmod.js").writeText("")
    }

    private fun write(vararg names: String) = names.forEach { write(it to "") }

    private fun write(vararg files: Pair<String, String>) = files.forEach { (name, text) ->
        File(root, name).apply { parentFile?.mkdirs() }.writeText(text)
    }

    @Test
    fun resultsAreThoseOfJvmNpm() {
        val ids = listOf(
            "./a", "./a.js", "./a.mjs", "./m", "./d", "./d.json", "./both", "./dir", "./dir2", "./dir3", "./dir4", "./dir5",
            "./x/../a", "./x/y/z/deep", "pkg", "top", "libmod", "./missing", "missing", "./x/y", "./node_modules/pkg",
        )
        val bases = listOf(root.path, File(root, "x/y/z").path, File(root, "x").path, null)
        val paths = listOf(lib.path, File(temp.root, "absent").path)
        val failures = ids.flatMap { id ->
            bases.mapNotNull { base ->
                val expected = reference().resolve(id, base, paths)
                val actual = resolver().resolve(id, base, paths)
                if (expected == actual) null else "$id from $base: jvm-npm $expected, kotlin $actual"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun lookupOrder() {
        assertEquals(file("a.js"), resolver().resolve("./a", root.path, emptyList()))
        assertEquals(file("both.mjs"), resolver().resolve("./both", root.path, emptyList()))
        assertEquals(file("dir/lib/entry.js"), resolver().resolve("./dir", root.path, emptyList()))
        assertEquals(file("dir3/sub/index.js"), resolver().resolve("./dir3", root.path, emptyList()))
        // A missing `main` is not replaced by index.js, as in jvm-npm. zh-CN: main 指向的文件不存在时不改用 index.js, 同 jvm-npm.
        assertNull(resolver().resolve("./dir5", root.path, emptyList()))
        // The nearer node_modules wins. zh-CN: 较近的 node_modules 优先.
        assertEquals(file("x/node_modules/pkg/index.js"), resolver().resolve("pkg", File(root, "x/y/z").path, emptyList()))
        assertEquals(file("node_modules/pkg/index.js"), resolver().resolve("pkg", root.path, emptyList()))
        assertEquals(Resolved.FileModule(File(lib, "libmod.js").path), resolver().resolve("libmod", root.path, listOf(lib.path)))
        // Results are canonical paths (Q82). zh-CN: 结果为规范路径 (Q82).
        assertEquals(file("a.js"), resolver().resolve("./x/y/../../a", root.path, emptyList()))
        assertNull(resolver().resolve("./x/y", root.path, emptyList()))
    }

    @Test
    fun absoluteIds() {
        assertEquals(file("a.js"), resolver().resolve(File(root, "a").path, null, emptyList()))
        assertEquals(file("a.js"), resolver().resolve(File(root, "a").path, File(lib, "elsewhere").path, emptyList()))
        assertEquals(file("dir2/index.js"), resolver().resolve(File(root, "dir2").path, null, emptyList()))
        assertEquals(file("dir/lib/entry.js"), resolver().resolve(File(root, "dir").path, lib.path, emptyList()))
        assertNull(resolver().resolve(File(root, "missing").path, null, emptyList()))
    }

    @Test
    fun classLoaderResourcesComeFirst() {
        write("events.js")
        val resolver = ScriptModuleResolver(::packageMain, disk) { it == "events.js" }
        assertEquals(Resolved.ResourceModule("events.js"), resolver.resolve("events", root.path, emptyList()))
        assertEquals(Resolved.ResourceModule("events.js"), resolver.resolve("events.js", root.path, emptyList()))
        assertEquals(file("events.js"), resolver().resolve("events", root.path, emptyList()))
    }

    @Test
    fun packageErrors() {
        val error = assertThrows(ScriptModuleResolver.PackageError::class.java) { resolver().resolve("./badpkg", root.path, emptyList()) }
        assertTrue(error.cause is IllegalArgumentException)
        val notString = ScriptModuleResolver({ throw IllegalStateException("main is not a string") }, disk) { false }
        assertThrows(ScriptModuleResolver.PackageError::class.java) { notString.resolve("./dir", root.path, emptyList()) }
    }

    @Test
    fun aMissStatsAFractionOfJvmNpm() {
        val base = File(root, "x/y/z").path
        val paths = listOf(File(temp.root, "home/.node_modules").path, File(temp.root, "home/.node_libraries").path)
        disk.stats = 0
        assertNull(reference().resolve("missing", base, paths))
        val jvmNpm = disk.stats
        disk.stats = 0
        assertNull(resolver().resolve("missing", base, paths))
        val kotlin = disk.stats
        assertTrue("kotlin $kotlin, jvm-npm $jvmNpm", kotlin * 2 <= jvmNpm)
    }

    @Test
    fun names() {
        assertEquals("x.js", normalizeName("x"))
        assertEquals("x.JS", normalizeName("x.JS"))
        assertEquals("x.Json", normalizeName("x.Json"))
        assertEquals("x.ts.js", normalizeName("x.ts"))
        assertEquals("x.json", normalizeName("x", JSON))
        assertEquals("x.mjs", normalizeName("x.mjs", JSON))
        assertEquals(File.separator + "a", join(null, "a"))
    }

    private fun file(name: String) = Resolved.FileModule(File(root, name).canonicalPath)

    private fun resolver() = ScriptModuleResolver(::packageMain, disk) { false }

    private fun reference() = JvmNpmResolve(disk, ::packageMain)

    /** The `main` of a `package.json`, read with a regular expression; a text that is not an object is an error. */
    private fun packageMain(path: String): String? {
        val text = File(path).readText().trim()
        require(text.startsWith("{") && text.endsWith("}")) { "Not JSON: $text" }
        return Regex(""""main"\s*:\s*"([^"]*)"""").find(text)?.groupValues?.get(1)?.ifEmpty { null }
    }

    /** The local file system, counting the probes ([Disk.exists] and [Disk.isDirectory]). */
    private class CountingDisk : Disk {
        var stats = 0

        override fun exists(file: File) = Disk.LOCAL.exists(file).also { stats++ }

        override fun isDirectory(file: File) = Disk.LOCAL.isDirectory(file).also { stats++ }

        override fun canonical(file: File) = Disk.LOCAL.canonical(file)
    }

    /** `resolveAsFile`, `resolveAsDirectory` and `resolveAsNodeModule` of jvm-npm, line by line. */
    private class JvmNpmResolve(private val disk: Disk, private val packageMain: (String) -> String?) {

        fun resolve(id: String, base: String?, paths: List<String>): Resolved? {
            for (root in listOf(base) + paths) {
                val result = asFile(id, root, JS) ?: asFile(id, root, MJS) ?: asFile(id, root, JSON) ?: asDirectory(id, root) ?: asNodeModule(id, root)
                if (result != null) return Resolved.FileModule(result)
            }
            return null
        }

        private fun asFile(id: String, root: String?, ext: String): String? {
            if (!id.startsWith(File.separator)) {
                val file = File(join(root, normalizeName(id, ext)))
                return if (disk.exists(file)) disk.canonical(file) else null
            }
            val file = File(normalizeName(id, ext))
            return if (disk.exists(file)) disk.canonical(file) else asDirectory(id, null)
        }

        private fun asDirectory(id: String, root: String?): String? {
            val base = join(root, id)
            val pkg = File(join(base, "package.json"))
            if (disk.exists(pkg)) {
                val main = packageMain(disk.canonical(pkg)) ?: return asFile("index.js", base, JS)
                return asFile(main, base, JS) ?: asDirectory(main, base)
            }
            return asFile("index.js", base, JS)
        }

        private fun asNodeModule(id: String, root: String?): String? {
            val base = join(root, "node_modules")
            return asFile(id, base, JS) ?: asDirectory(id, base) ?: if (root.isNullOrEmpty()) null else asNodeModule(id, File(root).parent)
        }
    }

}
