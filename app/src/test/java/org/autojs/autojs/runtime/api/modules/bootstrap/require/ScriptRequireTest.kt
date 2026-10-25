package org.autojs.autojs.runtime.api.modules.bootstrap.require

import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapSides
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino.Side
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.LazyDataSlot
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.commonjs.module.Require
import org.mozilla.javascript.commonjs.module.provider.StrongCachingModuleScriptProvider
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider
import java.io.File
import java.io.IOException

/**
 * Roadmap P3.7 (design (a), (b), (e)): the Kotlin `require` and `Module` on temporary directories: the cache, the module
 * fields, the function wrapper (isolation, `this`, strict mode, issue #564), cycles, JSON, shebang, empty and encrypted
 * files, the named modules, the errors (D10, Q83, PARSE_ERROR, IO_ERROR, source positions), `Module.runMain`,
 * `require.root`, `Module._load` hooks and the lazy mount.
 *
 * zh-CN: ROADMAP P3.7 (设计 (a), (b), (e)): 在临时目录上验证 Kotlin require 与 Module: 缓存, module 字段, 函数包装 (隔离, this, 严格模式,
 * issue #564), 循环依赖, JSON, shebang, 空文件与加密文件, 命名模块, 错误 (D10, Q83, PARSE_ERROR, IO_ERROR, 源位置), Module.runMain,
 * require.root, Module._load 钩子与惰性挂载.
 */
class ScriptRequireTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sides: BootstrapSides
    private lateinit var root: File
    private val namedLookups = mutableListOf<String>()
    private var created = 0

    @Before
    fun setUp() {
        sides = BootstrapSides()
        root = temp.newFolder("root").canonicalFile
        write("a.js", "exports.name = 'a'; exports.b = require('./b').value; exports.dir = rel(__dirname); exports.file = rel(__filename); exports.self = this === exports;")
        write("b.js", "module.exports = { value: 2 };")
        write(
            "fields.js",
            """
            module.exports = {
                id: rel(module.id), filename: rel(module.filename), loaded: module.loaded, parent: typeof module.parent, core: module.core,
                keys: Object.keys(module).join(), names: Object.getOwnPropertyNames(module).join(),
                accessor: (function (d) { return [typeof d.get, typeof d.set, d.enumerable, d.configurable].join(); })(Object.getOwnPropertyDescriptor(module, 'exports')),
                require: [require.name, require.length, typeof require.resolve, require.resolve.length, typeof require.paths].join(),
                shared: require.cache === global.require.cache && require.extensions === global.require.extensions,
            };
            """.trimIndent()
        )
        write("parent.js", "require('./kid1'); require('./kid2'); module.exports = module.children.map(function (m) { return rel(m.id) + ':' + rel(m.parent.id); }).join();")
        write("kid1.js", "module.exports = 1;")
        write("kid2.js", "module.exports = 2;")
        write("c1.js", "exports.early = true; var c2 = require('./c2'); exports.seen = c2.seen; module.exports.done = true;")
        write("c2.js", "var c1 = require('./c1'); exports.seen = JSON.stringify(c1);")
        write("flaky.js", "if (!global.flakyOnce) { global.flakyOnce = true; throw new Error('first'); } module.exports = 'second';")
        write("data.json", """{"k": [1, 2]}""")
        write("exp.json", """{"exports": 5, "v": 1}""")
        write("broken.json", "{bad")
        write("undef.js", "module.exports = undefined;")
        write(
            "shebang.js",
            """
            #!/usr/bin/env node
            var line; try { null.x; } catch (e) { line = e.lineNumber; } module.exports = 'sb ' + line;
            """.trimIndent()
        )
        write("empty.js", "")
        write("Up.JS", "module.exports = 'upper';")
        write(
            "err3.js",
            """
            var a = 1;
            var b = 2;
            null.boom;
            """.trimIndent()
        )
        write(
            "miss2.js",
            """
            // line 1
            require('./nope');
            """.trimIndent()
        )
        write("badpkg/package.json", "{bad")
        write("badpkg/index.js", "")
        write("unreadable.js", "module.exports = 'never';")
        write("strict.js", "'use strict'; undeclaredStrict = 1;")
        write("loose.js", "leakedLoose = 1; var localVar = 2; let localLet = 3; const localConst = 4; function localFn() {} module.exports = 'loose';")
        write("reassign.js", "exports.before = 1; exports = { replaced: true };")
        write("events.js", "module.exports = 'local events';")
        write("hooked.js", "module.exports = 'hooked';")
        write("sub/inner.js", "module.exports = 'inner';")
        write("sub/x.js", "")
        write("lib/log.js", "exports.append = function (line) { return 'log ' + line; };")
        write("lib/a.js", "const log = require('./log.js'); let toast = 'a'; exports.tag = log.append(toast);")
        write("lib/b.js", "const log = require('./log.js'); let toast = 'b'; exports.tag = log.append(toast);")
        write("lib/t.js", "exports.t = typeof log;")
        write("main564.js", "module.exports = [require('./lib/a.js').tag, require('./lib/b.js').tag, require('./lib/t.js').t].join();")
        write(WEIRD_DIR + "/m.js", "module.exports = require('./n');")
        write(WEIRD_DIR + "/n.js", "module.exports = 'n';")
        ScriptableObject.putProperty(sides.ktGlobal, "ROOT", root.path)
        sides.kt(
            """
            var Files = java.nio.file.Paths;
            function rel(p) { return String(Files.get(ROOT).relativize(Files.get(String(p)))).split(java.io.File.separator).join('/'); }
            function clean(s) { return String(s).split(ROOT).join('<root>').split(java.io.File.separator).join('/'); }
            function attempt(f) {
                try { return f(); } catch (e) { return ['throws', String(e.name), clean(e.message), String(e.code), e instanceof Error]; }
            }
            Object.defineProperty(this, 'log', { value: function () {}, writable: true, enumerable: true, configurable: false });
            Object.defineProperty(this, 'toast', { value: function () {}, writable: false, enumerable: true, configurable: false });
            """.trimIndent()
        )
        install(sides.ktScope)
    }

    @After
    fun tearDown() = sides.close()

    private fun write(name: String, text: String) {
        File(root, name).apply { parentFile?.mkdirs() }.writeText(text)
    }

    private fun install(scope: TopLevelScope) {
        val instance by lazy {
            created++
            ScriptRequire(scope, Host(root.path), AssetModuleSource(::canLoad, ::load), nativeRequire = { id -> "native $id" }, nodePath = { null })
        }
        ScriptRequire.mount(scope.globalThis as ScriptableObject) { instance }
    }

    private fun canLoad(id: String): Boolean {
        namedLookups += id
        return id in NAMED
    }

    private fun load(id: String): Any? = if (id == "falsy") Undefined.instance else "named $id"

    private inner class Host(private val cwd: String) : ScriptRequire.Host {
        override fun cwd() = cwd
        override fun exists(path: String) = File(cwd, path).exists()
        override fun readBytes(path: String): ByteArray {
            if (path.endsWith("unreadable.js")) throw IOException("denied")
            return File(path).readBytes()
        }
    }

    private fun assertKt(vararg cases: Pair<String, Any?>) = sides.assertRenders(Side.KT, cases.map { (expression, expected) -> expression to json(expected) })

    @Test
    fun loadsAndCachesByCanonicalPath() = assertKt(
        "(function () { var a = require('./a'); return [a.name, a.b, a.dir, a.file, a.self, require('./a') === a, require('./a.js') === a, require('./sub/../a') === a]; })()"
            to listOf("a", 2, "", "a.js", true, true, true, true),
        "(function () { require('./a'); return Object.keys(require.cache).map(rel).sort().join(); })()" to "a.js,b.js",
        "require('./data.json').k.join()" to "1,2",
        "require('./data.json') === require('./data.json')" to true,
        "require('./b') === require.cache[require.resolve('./b')].exports" to true,
    )

    @Test
    fun moduleFields() = assertKt(
        "(function () { var f = require('./fields'); return [f.id, f.filename, f.loaded, f.parent, f.core, f.keys, f.names, f.accessor, f.require, f.shared]; })()"
            to listOf(
                "fields.js", "fields.js", false, "undefined", false,
                "_exports,id,core,parent,children,filename,loaded,require",
                "_exports,id,core,parent,children,filename,loaded,exports,require",
                "function,function,false,false", "bound ,1,function,1,function", true,
            ),
        "(function () { require('./fields'); return require.cache[require.resolve('./fields')].loaded; })()" to true,
        "require('./parent')" to "kid1.js:parent.js,kid2.js:parent.js",
    )

    @Test
    fun cyclesRetriesAndCachedValues() = assertKt(
        "(function () { var c1 = require('./c1'); return [c1.seen, c1.done, require('./c2').seen]; })()" to listOf("{\"early\":true}", true, "{\"early\":true}"),
        "(function () { var first = attempt(function () { return require('./flaky'); }); return [first[2], require.resolve('./flaky') in require.cache, require('./flaky')]; })()"
            to listOf("first", false, "second"),
        // Q81 fix 2: a cached value is given as is. zh-CN: Q81 修复项 2: 缓存值原样给出.
        "[require('./exp.json').v, require('./exp.json').v, require('./exp.json').exports]" to listOf(1, 1, 5),
        "[require('./undef'), require('./undef')]" to listOf(null, null),
    )

    @Test
    fun functionWrapperIsolatesModules() = assertKt(
        "(function () { require('./loose'); return [typeof localVar, typeof localLet, typeof localConst, typeof localFn, leakedLoose]; })()"
            to listOf("undefined", "undefined", "undefined", "undefined", 1),
        "attempt(function () { return require('./strict'); })" to listOf("throws", "ReferenceError", "Assignment to undefined \"undeclaredStrict\" in strict mode", "undefined", true),
        "typeof undeclaredStrict" to "undefined",
        "JSON.stringify(require('./reassign'))" to "{\"before\":1}",
        // Issue #564: top-level const / let named like a global. zh-CN: issue #564: 与全局同名的顶层 const / let.
        "require('./main564')" to "log a,log b,function",
        "[typeof log, typeof toast]" to listOf("function", "function"),
    )

    @Test
    fun sourcesOfModules() {
        val key = "0123456789abcdef0123456789abcdef"
        val vector = "0123456789abcdef"
        File(root, "encrypted.js").outputStream().use { out ->
            EncryptedScriptFileHeader.writeHeader(out)
            out.write(AdvancedEncryptionStandard(key.toByteArray(), vector).encrypt("module.exports = 'secret';".toByteArray()))
        }
        ScriptEncryption.configure(key, vector)
        try {
            assertKt(
                "require('./shebang')" to "sb 2",
                // Q81 fixes 3 and 4. zh-CN: Q81 修复项 3 与 4.
                "JSON.stringify(require('./empty'))" to "{}",
                "require('./Up.JS')" to "upper",
                "require('./encrypted')" to "secret",
            )
        } finally {
            ScriptEncryption.configure("", "")
        }
    }

    @Test
    fun namedModules() {
        assertKt(
            "[require('path'), require('node:path'), require('node:events'), require('events')]" to listOf("named path.js", "named path.js", "local events", "local events"),
            "require('https://example.com/x.js')" to "native https://example.com/x.js",
            "require('axios')" to "named axios",
            "attempt(function () { return require('falsy'); })" to listOf("throws", "Error", "Cannot find module 'falsy' (from <root>)", "MODULE_NOT_FOUND", true),
            "attempt(function () { return require('./not-named'); })" to listOf("throws", "Error", "Cannot find module './not-named' (from <root>)", "MODULE_NOT_FOUND", true),
        )
        // Relative ids are not named modules. zh-CN: 相对 id 不是命名模块.
        assertEquals(listOf("path.js", "path.js", "axios", "falsy"), namedLookups)
    }

    @Test
    fun errors() = assertKt(
        // D10. zh-CN: D10.
        "attempt(function () { return require(); })" to listOf("throws", "TypeError", ARGUMENT_TYPE.format("require", "undefined"), "undefined", true),
        "attempt(function () { return require(1); })" to listOf("throws", "TypeError", ARGUMENT_TYPE.format("require", "number"), "undefined", true),
        "attempt(function () { return require(''); })" to listOf("throws", "TypeError", "Failed to call method \"require\": [ Argument \"id\" cannot be an empty string ]", "undefined", true),
        "attempt(function () { return require.resolve(null); })" to listOf("throws", "TypeError", ARGUMENT_TYPE.format("require.resolve", "null"), "undefined", true),
        "attempt(function () { return Module._load({}); })" to listOf("throws", "TypeError", "Failed to call method \"Module._load\": [ Argument \"file\" must be of type string, got Object ]", "undefined", true),
        "attempt(function () { return Module.runMain(); })" to listOf("throws", "TypeError", "Failed to call method \"Module.runMain\": [ Argument \"main\" must be of type string, got undefined ]", "undefined", true),
        // Q83. zh-CN: Q83.
        "attempt(function () { return require('node:'); })" to listOf("throws", "Error", "Cannot find module 'node:'", "MODULE_NOT_FOUND", true),
        "attempt(function () { return require('./sub/nope'); })" to listOf("throws", "Error", "Cannot find module './sub/nope' (from <root>)", "MODULE_NOT_FOUND", true),
        "(function () { try { require('./miss2'); } catch (e) { return [clean(e.fileName), e.lineNumber, e.code]; } })()" to listOf("<root>/miss2.js", 2, "MODULE_NOT_FOUND"),
        "(function () { try { require('./err3'); } catch (e) { return [e.name, rel(e.fileName), e.lineNumber]; } })()" to listOf("TypeError", "err3.js", 3),
        "attempt(function () { return require('./broken.json'); })" to listOf("throws", "SyntaxError", "Unexpected token in object literal", "undefined", true),
        // Q81 fix 9. zh-CN: Q81 修复项 9.
        "(function () { try { require('./badpkg'); } catch (e) { return [e.name, e.message, e.code, e instanceof Error, e.cause.name]; } })()"
            to listOf("Error", "Cannot load JSON file", "PARSE_ERROR", true, "SyntaxError"),
        "(function () { try { require('./unreadable'); } catch (e) { return [e.name, clean(e.message), e.code, typeof e.cause, require.resolve('./unreadable') in require.cache]; } })()"
            to listOf("Error", "Cannot read file [<root>/unreadable.js]: denied", "IO_ERROR", "object", false),
    )

    @Test
    fun runMainRootAndResolve() = assertKt(
        "[require.main, Object.getOwnPropertyDescriptor(require, 'main').set, require.root === ROOT]" to listOf(null, null, true),
        "(function () { global.process = {}; var b = Module.runMain('./b'); return [b.value, require.main === require.cache[require.resolve('./b')], process.mainModule === require.main]; })()"
            to listOf(2, true, true),
        // Q81 fix 8. zh-CN: Q81 修复项 8.
        "attempt(function () { return Module.runMain('./nope'); })" to listOf("throws", "Error", "Cannot find module './nope' (from <root>)", "MODULE_NOT_FOUND", true),
        "(function () { require.root = ROOT + java.io.File.separator + 'sub'; var inner = require('./inner'); require.root = ROOT; return [inner, Module.require.root === ROOT]; })()"
            to listOf("inner", true),
        "rel(require.resolve('./inner', { id: ROOT + java.io.File.separator + 'sub' + java.io.File.separator + 'x.js' }))" to "sub/inner.js",
        "[require.resolve('./nope'), require.resolve('nope')]" to listOf(false, false),
        // Q81 fix 1: the parent directory is not split at "," or "|". zh-CN: Q81 修复项 1: 父目录不再按 "," 与 "|" 拆分.
        "require('./$WEIRD_DIR/m')" to "n",
        "Array.isArray(require.paths()) && require.paths().length" to 2,
    )

    @Test
    fun loadHooksSeeEveryLoad() = assertKt(
        """
        (function () {
            var original = Module._load, seen = [];
            Module._load = function (file, parent, core) { seen.push(rel(file) + ':' + (parent && rel(parent.id)) + ':' + core); return original.apply(this, arguments); };
            try { return [require('./hooked'), require('./parent')].concat(seen); } finally { Module._load = original; }
        })()
        """.trimIndent() to listOf("hooked", "kid1.js:parent.js,kid2.js:parent.js", "hooked.js:undefined:false", "parent.js:undefined:false", "kid1.js:parent.js:false", "kid2.js:parent.js:false"),
    )

    @Test
    fun mountIsLazyAndKeepsThePlaceOfJvmNpm() {
        val scope = sides.freshScope()
        val global = scope.globalThis as ScriptableObject
        ScriptableObject.putProperty(global, "before", 1)
        install(scope)
        ScriptableObject.putProperty(global, "after", 2)
        created = 0
        assertTrue(LazyDataSlot.isPending(global, "require"))
        assertTrue(LazyDataSlot.isPending(global, "Module"))
        val keys = sides.eval(scope, "Object.keys(this).filter(function (k) { return ['before', 'Module', 'require', 'after'].indexOf(k) >= 0; }).join()")
        assertEquals("before,Module,require,after", keys)
        assertEquals(0, created)
        assertEquals(true, sides.eval(scope, "typeof require === 'function' && require === Module.require"))
        assertEquals(1, created)
        assertEquals("before,Module,require,after", sides.eval(scope, "Object.keys(this).filter(function (k) { return ['before', 'Module', 'require', 'after'].indexOf(k) >= 0; }).join()"))
        val permanent = (global.getAttributes("require") and ScriptableObject.PERMANENT) != 0 && (global.getAttributes("Module") and ScriptableObject.PERMANENT) != 0
        assertTrue(permanent)
        assertFalse(LazyDataSlot.isPending(global, "require"))
    }

    @Test
    fun rhinoRequireModulesKeepTopLevelConstOfGlobalNames() {
        // Issue #564 on the path of the engine's Rhino Require (asset, URL and TypeScript modules): a fix of the Rhino jar
        // that a jar upgrade must keep. zh-CN: issue #564 在引擎 Rhino Require 路径上 (资产, URL 与 TypeScript 模块): 依赖 Rhino jar 的修复, 升级 jar 时须保持.
        val scope = sides.freshScope()
        sides.eval(scope, "Object.defineProperty(this, 'log', { value: function () {}, writable: true, enumerable: true, configurable: false });")
        val provider = StrongCachingModuleScriptProvider(UrlModuleSourceProvider(listOf(File(root, "lib").toURI()), null))
        Require(sides.cx, scope, provider, null, null, false).install(scope)
        assertEquals("log a,log b,function", sides.eval(scope, "[require('a').tag, require('b').tag, require('t').t].join()"))
    }

    private companion object {

        val NAMED = setOf("path.js", "events.js", "axios", "falsy")

        val WEIRD_DIR = if (File.separatorChar == '/') "we,ird|dir" else "we,ird"

        const val ARGUMENT_TYPE = "Failed to call method \"%s\": [ Argument \"id\" must be of type string, got %s ]"

        /** [value] as [BootstrapSides.renderWithMessage] renders it. */
        fun json(value: Any?): String = when (value) {
            null -> "null"
            is String -> "\"" + value.replace("\"", "\\\"") + "\""
            is List<*> -> value.joinToString(",", "[", "]") { json(it) }
            else -> value.toString()
        }
    }

}
