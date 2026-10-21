package org.autojs.autojs.runtime.api.modules.bootstrap

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino.Side
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.ClassCache
import org.mozilla.javascript.ScriptableObject
import java.io.File

/**
 * Roadmap P2.5 (Q16): `assets/modules/jvm-npm.js`, which every script runs at start, looks its Java classes up on first
 * use; against the module before the change, loading it reflects none of them for the scope, and `require` resolves and
 * loads modules from files, JSON, `node_modules` and the native `require` the same way.
 *
 * zh-CN: ROADMAP P2.5 (Q16): 每个脚本启动时运行的 `assets/modules/jvm-npm.js` 在首次使用时才查找 Java 类;
 * 与改动前的模块相比, 加载时不再为作用域反射其中任何一个类, `require` 从文件, JSON, `node_modules` 与原生 `require` 解析和加载模块的方式相同.
 */
class JvmNpmTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sides: BootstrapSides
    private lateinit var root: File

    @Before
    fun setUp() {
        sides = BootstrapSides()
        root = temp.newFolder("root")
        File(root, "a.js").writeText("exports.name = 'a'; exports.dir = __dirname; exports.b = require('./b').value;")
        File(root, "b.js").writeText("module.exports = { value: 2 };")
        File(root, "data.json").writeText("{\"k\": [1, 2]}")
        File(root, "node_modules/pkg/lib").mkdirs()
        File(root, "node_modules/pkg/package.json").writeText("{\"main\": \"lib/main.js\"}")
        File(root, "node_modules/pkg/lib/main.js").writeText("module.exports = 'pkg main';")
        File(root, "bad.js").writeText("throw new TypeError('bad module');")
        // No Java class is touched before the module loads: the fakes only use Java when called.
        // zh-CN: 模块加载前不触及任何 Java 类: 替身只在被调用时才用到 Java.
        sides.both(
            """
            var ROOT = '${root.absolutePath.replace('\\', '/')}';
            var runtime = {
                files: {
                    cwd: function () { return ROOT; },
                    exists: function (path) { return false; },
                    readBytes: function (path) { return java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)); },
                },
            };
            var nativeRequired = [];
            var require = function (id) {
                nativeRequired.push(String(id));
                return id === 'missing-native' ? undefined : 'native ' + id;
            };
            """.trimIndent()
        )
        load(sides.jsScope, sides.original("jvm-npm.js"))
        load(sides.ktScope, sides.asset("jvm-npm.js"))
    }

    @After
    fun tearDown() = sides.close()

    private fun load(scope: TopLevelScope, file: File) {
        ScriptableObject.putProperty(scope.globalThis, "Module", sides.module(scope, file))
    }

    @Test
    fun loadingReflectsNoneOfTheModulesJavaClasses() {
        assertEquals(JAVA_CLASSES, reflected(sides.jsScope).filter { it in JAVA_CLASSES }.sorted())
        assertEquals(emptyList<String>(), reflected(sides.ktScope).filter { it in JAVA_CLASSES })
    }

    @Test
    fun theClassesAreLookedUpWhenUsed() {
        // Resolving reads system properties and the class loader, reading checks the encryption header and scans the
        // file; nothing decrypts, so ScriptEncryption and StandardCharsets stay unreflected.
        // zh-CN: 解析读取系统属性与类加载器, 读取文件时检查加密头并扫描文件; 没有解密, 所以 ScriptEncryption 与 StandardCharsets 仍未反射.
        sides.kt("require('./a')")
        assertEquals(
            listOf("java.io.File", "java.lang.System", "java.lang.Thread", "java.util.Scanner", "org.autojs.autojs.script.EncryptedScriptFileHeader").sorted(),
            reflected(sides.ktScope).filter { it in JAVA_CLASSES }.sorted(),
        )
    }

    @Test
    fun surfaceIsTheModules() = sides.assertSame(
        listOf(
            "[typeof require, require === global.require, require.root === ROOT, require.debug, typeof require.cache, typeof require.extensions]",
            "Object.keys(require).sort().join()",
            "Object.keys(Module).sort().join()",
            "[typeof Module.require.resolve, typeof Module.require.paths, Module.require.cache === require.cache, Module.require.root === ROOT]",
        )
    )

    @Test
    fun requireLoadsModulesTheSameWay() = sides.assertSame(
        listOf(
            "(function () { var a = require('./a'); return [a.name, a.b, a.dir.replace(/\\\\/g, '/') === ROOT, require('./a') === a]; })()",
            "require('./data.json').k.join()",
            "require('pkg')",
            "require('node:path')",
            "require('not-there')",
            "(function () { try { require('missing-native'); return 'no throw'; } catch (e) { return [e.message, e.code, e instanceof Error]; } })()",
            "(function () { try { require('./bad'); return 'no throw'; } catch (e) { return [e.name, e.message, Object.keys(require.cache).some(function (k) { return /bad\\.js${'$'}/.test(k); })]; } })()",
            "String(require.resolve('./b')).replace(/\\\\/g, '/') === ROOT + '/b.js'",
            "require.resolve('missing-file-or-module')",
            "[require.paths().length, /[\\\\/]\\.node_modules${'$'}/.test(require.paths()[0])]",
            "(function () { var b = Module.runMain('./b'); return [b.value, Module.require.main === require.cache[require.resolve('./b')]]; })()",
            "(function () { require.root = '/elsewhere'; return [Module.require.root, require.resolve('./b')]; })()",
            "nativeRequired.join()",
        )
    )

    /** The names of the classes reflected for [scope] so far. */
    private fun reflected(scope: TopLevelScope): List<String> {
        val cache = ClassCache.get(scope)
        val map = ClassCache::class.java.getDeclaredMethod("getClassCacheMap").apply { isAccessible = true }.invoke(cache) as Map<*, *>
        return map.keys.map { key ->
            val cls = key!!.javaClass.getDeclaredField("cls").apply { isAccessible = true }.get(key) as Class<*>
            cls.name
        }
    }

    private companion object {
        val JAVA_CLASSES = listOf(
            "java.io.File",
            "java.util.Scanner",
            "java.lang.System",
            "java.lang.Thread",
            "org.autojs.autojs.script.EncryptedScriptFileHeader",
            "org.autojs.autojs.engine.encryption.ScriptEncryption",
            "java.nio.charset.StandardCharsets",
        ).sorted()
    }

}
