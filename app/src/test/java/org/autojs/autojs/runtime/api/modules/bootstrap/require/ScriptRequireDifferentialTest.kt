package org.autojs.autojs.runtime.api.modules.bootstrap.require

import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino.Side
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapSides
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.Function
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.SerializableCallable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.PrintStream
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Roadmap P3.7 (design (d)): the global `require` and `Module` of the two states on the same files. The "js" side is the
 * state without the new module layer: `assets/modules/jvm-npm.js` run as the engine runs it, with the global `Module` and
 * `require` (`Module.require`) defined as `ScriptRuntime` does; the "kt" side mounts [ScriptRequire]. Both sides get the
 * same `runtime.files` and the same fake native `require`, which records the ids it is asked for.
 *
 * Everything renders the same, error messages included, except the registered differences (appendix E "P3.7": the nine
 * fixes of Q81 and the error of Q83), each checked with its own result on each side.
 *
 * zh-CN: ROADMAP P3.7 (设计 (d)): 在相同文件上对照两态的全局 require 与 Module. "js" 侧为未启用新模块层的状态: 按引擎的方式运行
 * `assets/modules/jvm-npm.js`, 并像 ScriptRuntime 那样定义全局 Module 与 require (`Module.require`); "kt" 侧挂载 [ScriptRequire].
 * 两侧使用相同的 `runtime.files` 与相同的原生 require 替身 (记录收到的 id). 除已登记的差异 (附录 E "P3.7": Q81 的 9 项修复与 Q83 的错误)
 * 外, 结果 (含错误消息) 全部相同; 每项差异分别核对两侧各自的结果.
 */
class ScriptRequireDifferentialTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sides: BootstrapSides
    private lateinit var root: File

    @Before
    fun setUp() {
        sides = BootstrapSides()
        root = temp.newFolder("root").canonicalFile
        write("a.js", "exports.name = 'a'; exports.b = require('./b').value; exports.dir = rel(__dirname); exports.file = rel(__filename); exports.self = this === exports; exports.args = arguments.length;")
        write("b.js", "module.exports = { value: 2 };")
        write(
            "fields.js",
            """
            module.exports = {
                id: rel(module.id), filename: rel(module.filename), loaded: module.loaded, parent: typeof module.parent, core: module.core,
                keys: Object.keys(module).join(), names: Object.getOwnPropertyNames(module).join(),
                accessor: (function (d) { return [typeof d.get, typeof d.set, d.enumerable, d.configurable].join(); })(Object.getOwnPropertyDescriptor(module, 'exports')),
                require: [require.name, require.length, typeof require.resolve, require.resolve.length, typeof require.paths, Object.keys(require).join()].join(),
                shared: require.cache === global.require.cache && require.extensions === global.require.extensions,
            };
            """.trimIndent()
        )
        write("parent.js", "require('./kid1'); require('./kid2'); module.exports = module.children.map(function (m) { return rel(m.id) + ':' + rel(m.parent.id); }).join();")
        write("kid1.js", "module.exports = 1;")
        write("kid2.js", "module.exports = 2;")
        write("modreq.js", "module.exports = module.require('./b').value + ':' + rel(require.resolve('./sub/inner'));")
        write("c1.js", "exports.early = true; var c2 = require('./c2'); exports.seen = c2.seen; module.exports.done = true;")
        write("c2.js", "var c1 = require('./c1'); exports.seen = JSON.stringify(c1);")
        write("flaky.js", "if (!global.flakyOnce) { global.flakyOnce = true; throw new Error('first'); } module.exports = 'second';")
        write("data.json", """{"k": [1, 2]}""")
        write("exp.json", """{"exports": 5, "v": 1}""")
        write("broken.json", "{bad")
        write("undef.js", "module.exports = undefined;")
        write("shebang.js", "#!/usr/bin/env node\nvar line; try { null.x; } catch (e) { line = e.lineNumber; } module.exports = 'sb ' + line;")
        write("empty.js", "")
        write("Up.JS", "module.exports = 'upper';")
        write("syntax.js", "var a = 1;\nvar b = {;")
        write("err3.js", "var a = 1;\nvar b = 2;\nnull.boom;")
        write("lines.js", "\nmodule.exports = [new Error('x').lineNumber, (function () { try { null.x; } catch (e) { return e.lineNumber; } })()];")
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
        write("dir/package.json", """{"main": "lib/entry"}""")
        write("dir/lib/entry.js", "module.exports = 'entry';")
        write("dir2/index.js", "module.exports = 'dir2 index';")
        write("dir3/package.json", """{"main": "sub"}""")
        write("dir3/sub/index.js", "module.exports = 'dir3 sub';")
        write("node_modules/pkg/index.js", "module.exports = 'root pkg';")
        write("node_modules/top.js", "module.exports = 'top';")
        write("x/node_modules/pkg/index.js", "module.exports = 'x pkg';")
        write("x/y/z/deep.js", "module.exports = [require('pkg'), require('top')].join();")
        write("lib/log.js", "exports.append = function (line) { return 'log ' + line; };")
        write("lib/a.js", "const log = require('./log.js'); let toast = 'a'; exports.tag = log.append(toast);")
        write("lib/b.js", "const log = require('./log.js'); let toast = 'b'; exports.tag = log.append(toast);")
        write("lib/t.js", "exports.t = typeof log;")
        write("main564.js", "module.exports = [require('./lib/a.js').tag, require('./lib/b.js').tag, require('./lib/t.js').t].join();")
        write("we,ird/m.js", "module.exports = require('./n');")
        write("we,ird/n.js", "module.exports = 'n';")
        File(SAMPLE).copyTo(File(root, File(SAMPLE).name))
        listOf(sides.jsGlobal, sides.ktGlobal).forEach { ScriptableObject.putProperty(it, "ROOT", root.path) }
        sides.both(PRELUDE)
        // The state without the layer, as ScriptRuntime sets it up. zh-CN: 未启用新层的状态, 同 ScriptRuntime 的设置.
        sides.js("var require = nativeFake;")
        val jvmNpm = sides.module(sides.jsScope, sides.asset("jvm-npm.js")) as ScriptableObject
        sides.jsGlobal.defineProperty("Module", jvmNpm, ScriptableObject.PERMANENT)
        sides.jsGlobal.defineProperty("require", ScriptableObject.getProperty(jvmNpm, "require"), ScriptableObject.PERMANENT)
        // The state with the layer. zh-CN: 启用新层的状态.
        val fake = ScriptableObject.getProperty(sides.ktGlobal, "nativeFake") as Function
        val native = { id: String -> fake.call(sides.cx, sides.ktScope, sides.ktGlobal, arrayOf(id)) }
        val instance by lazy { ScriptRequire(sides.ktScope, Host(root.path), AssetModuleSource({ true }, native), native, nodePath = { null }) }
        ScriptRequire.mount(sides.ktGlobal) { instance }
    }

    @After
    fun tearDown() = sides.close()

    private fun write(name: String, text: String) {
        File(root, name).apply { parentFile?.mkdirs() }.writeText(text)
    }

    /** `runtime.files` of the "kt" side, as the prelude's of the "js" side. zh-CN: "kt" 侧的 runtime.files, 同 "js" 侧序言中的定义. */
    private class Host(private val cwd: String) : ScriptRequire.Host {
        override fun cwd() = cwd
        override fun exists(path: String) = File(cwd, path).exists()
        override fun readBytes(path: String): ByteArray {
            if (path.endsWith("unreadable.js")) throw IOException("denied")
            return File(path).readBytes()
        }
    }

    /** Every expression renders the same on both sides, error messages included. */
    private fun assertBoth(vararg expressions: String) {
        val failures = expressions.mapNotNull { expression ->
            val js = sides.renderWithMessage(Side.JS, expression)
            val kt = sides.renderWithMessage(Side.KT, expression)
            if (js == kt) null else "$expression\n    js $js\n    kt $kt"
        }
        assertTrue("Different results:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** A registered difference: [expression] renders as [js] on the "js" side and as [kt] on the "kt" side. */
    private fun assertDifference(expression: String, js: String, kt: String) {
        assertEquals("js: $expression", js, sides.renderWithMessage(Side.JS, expression))
        assertEquals("kt: $expression", kt, sides.renderWithMessage(Side.KT, expression))
    }

    @Test
    fun surface() = assertBoth(
        "[typeof require, require === Module.require, require.name, require.length, Module.name, Module.length]",
        "Object.getOwnPropertyNames(require).join()",
        "Object.keys(require).join()",
        "['resolve', 'paths', 'cache', 'extensions', 'main', 'root'].map(function (k) { var d = Object.getOwnPropertyDescriptor(require, k); return [k, typeof d.value, typeof d.get, typeof d.set, d.writable, d.enumerable, d.configurable].join(); }).join(';')",
        "[require.resolve.name, require.resolve.length, require.paths.name, require.paths.length, JSON.stringify(require.cache), JSON.stringify(require.extensions)]",
        "Object.getOwnPropertyNames(Module).join()",
        "Object.keys(Module).join()",
        "['require', '_load', 'runMain'].map(function (k) { var d = Object.getOwnPropertyDescriptor(Module, k); return [k, Module[k].name, Module[k].length, d.writable, d.enumerable, d.configurable].join(); }).join(';')",
        "[typeof Module.prototype, Module.prototype.constructor === Module, Object.keys(Module.prototype).length]",
        "(function () { var m = new Module('x'); return [Object.keys(m).join(), Object.getOwnPropertyNames(m).join(), m.require.name, m.require.length, m instanceof Module, m.exports === m._exports, JSON.stringify(m.exports), m.id, m.filename, m.loaded, m.core === undefined, m.parent === undefined, m.children.length]; })()",
        "(function () { var p = new Module('p'); var c = new Module('c', p, true); return [p.children.length, p.children[0] === c, c.parent === p, c.core]; })()",
        "(function () { var m = new Module('m'); var d = Object.getOwnPropertyDescriptor(m, 'exports'); m.exports = 5; return [typeof d.get, typeof d.set, d.enumerable, d.configurable, m._exports]; })()",
        "[typeof require.main, require.root === ROOT, require.paths().slice(0, 2).map(function (p) { return /[\\\\/]\\.node_(modules|libraries)$/.test(p); })]",
    )

    @Test
    fun loading() = assertBoth(
        "(function () { var a = require('./a'); return [a.name, a.b, a.dir, a.file, a.self, a.args, require('./a') === a, require('./a.js') === a, require('./sub/../a') === a]; })()",
        "(function () { require('./a'); return Object.keys(require.cache).map(rel).sort().join(); })()",
        "require('./data.json').k.join()",
        "require('./data.json') === require('./data.json')",
        "require('./b') === require.cache[require.resolve('./b')].exports",
        "[require('./dir'), require('./dir2'), require('./dir3'), require('pkg'), require('top'), require('./x/y/z/deep')]",
        "[rel(require.resolve('./dir')), rel(require.resolve('./x/y/z/deep')), rel(require.resolve('pkg')), require.resolve('./nope'), require.resolve('nope')]",
        "(function () { var f = require('./fields'); return [f.id, f.filename, f.loaded, f.parent, f.core, f.keys, f.names, f.accessor, f.require, f.shared]; })()",
        "(function () { require('./fields'); return require.cache[require.resolve('./fields')].loaded; })()",
        "require('./parent')",
        "require('./modreq')",
        "(function () { var c1 = require('./c1'); return [c1.seen, c1.done, require('./c2').seen, require.cache[require.resolve('./c2')].loaded]; })()",
        "(function () { var first = attempt(function () { return require('./flaky'); }); return [first, require.resolve('./flaky') in require.cache, require('./flaky')]; })()",
        "require('./shebang')",
        "require('./main564')",
        "[typeof log, typeof leakedLoose]",
        "(function () { require('./loose'); return [typeof localVar, typeof localLet, typeof localConst, typeof localFn, leakedLoose]; })()",
        "attempt(function () { return require('./strict'); })",
        "typeof undeclaredStrict",
        "JSON.stringify(require('./reassign'))",
        "[require('path'), require('node:path'), require('node:events'), require('events'), require('https://example.com/x.js'), require('axios')]",
        "(function () { require('path'); require('node:path'); require('axios'); attempt(function () { return require('missing-native'); }); require('http://example.com/y.js'); require('events'); return nativeRequired.join(); })()",
        "(function () { require.root = ROOT + java.io.File.separator + 'sub'; var inner = require('./inner'); require.root = ROOT; return [inner, Module.require.root === ROOT]; })()",
        "rel(require.resolve('./inner', { id: ROOT + java.io.File.separator + 'sub' + java.io.File.separator + 'x.js' }))",
        "(function () { global.process = {}; var b = Module.runMain('./b'); return [b.value, require.main === require.cache[require.resolve('./b')], process.mainModule === require.main]; })()",
        """
        (function () {
            var original = Module._load, seen = [];
            Module._load = function (file, parent, core) { seen.push(rel(file) + ':' + (parent && rel(parent.id)) + ':' + core); return original.apply(this, arguments); };
            try { return [require('./hooked'), require('./parent')].concat(seen); } finally { Module._load = original; }
        })()
        """.trimIndent(),
    )

    @Test
    fun errors() = assertBoth(
        "attempt(function () { return require('missing-native'); })",
        "attempt(function () { return require(); })",
        "attempt(function () { return require(1); })",
        "(function () { try { require('./err3'); } catch (e) { return [e.name, e.message, e.lineNumber, require.resolve('./err3') in require.cache]; } })()",
        "(function () { try { require('./syntax'); } catch (e) { return [e.name, e.message, e.lineNumber, e instanceof SyntaxError, require.resolve('./syntax') in require.cache]; } })()",
        "require('./lines')",
        "(function () { try { require('./broken.json'); } catch (e) { return [e.name, e.message]; } })()",
        "(function () { try { require('./badpkg'); } catch (e) { return [e.name, e.message, e.code, e instanceof Error, e.cause.name]; } })()",
        "(function () { try { require('./unreadable'); } catch (e) { return [e.name, e.code, e instanceof Error, typeof e.cause, require.resolve('./unreadable') in require.cache]; } })()",
    )

    @Test
    fun sampleModule() = assertBoth(
        """
        (function () {
            var P = require('./${File(SAMPLE).name}');
            var widget = new P();
            widget.key = 'k1';
            P.getPref().put('k1', true);
            var xml = P.prototype.render();
            return [
                typeof P, P.name, ui.widgets['pref-checkbox'] === P, widget instanceof ui.Widget, widget.attrs.join(), widget.getKey(),
                typeof xml, xml.toXMLString(), P.getPref().name, P.getPref().get('k1', false), P.getPref().get('k2', 'none'),
                require('./${File(SAMPLE).name}') === P,
            ];
        })()
        """.trimIndent(),
    )

    @Test
    fun encryptedModules() {
        val key = "0123456789abcdef0123456789abcdef"
        val vector = "0123456789abcdef"
        File(root, "encrypted.js").outputStream().use { out ->
            EncryptedScriptFileHeader.writeHeader(out)
            out.write(AdvancedEncryptionStandard(key.toByteArray(), vector).encrypt("module.exports = 'secret ' + __filename.slice(-12);".toByteArray()))
        }
        ScriptEncryption.configure(key, vector)
        try {
            assertBoth("require('./encrypted')")
        } finally {
            ScriptEncryption.configure("", "")
        }
    }

    @Test
    fun absoluteIds() {
        // jvm-npm takes an id as absolute when it starts with the file separator, so on Windows it has none.
        // zh-CN: jvm-npm 以文件分隔符开头判断绝对路径 id, 因此在 Windows 上没有绝对路径 id.
        if (File.separatorChar != '/') return
        assertBoth(
            "[require(ROOT + '/b').value, require(ROOT + '/dir2'), rel(require.resolve(ROOT + '/dir')), require.resolve(ROOT + '/nope')]",
        )
    }

    /**
     * A module may capture a continuation at its top level, also in a module it requires, and the load completes when the
     * continuation is resumed: the calls from the script to the module body are script functions on both sides.
     * zh-CN: 模块 (包括它 require 的模块) 可以在顶层捕获 continuation, 恢复后加载照常完成: 两侧从脚本到模块函数体都是脚本函数.
     */
    @Test
    fun continuationsInModules() {
        write("inner.js", "module.exports = 'inner ' + capture();")
        write("outer.js", "module.exports = require('./inner') + ' / ' + capture();")
        val outcomes = listOf(sides.jsScope, sides.ktScope).map { scope ->
            ScriptableObject.putProperty(scope.globalThis, "capture", LambdaFunction(scope, "capture", 0, SerializableCallable { cx, _, _, _ ->
                throw cx.captureContinuation()
            }))
            val script = sides.cx.compileString("[require('./outer'), require.cache[require.resolve('./outer')].loaded].join()", "main", 1, null)
            val answers = ArrayDeque(listOf("a", "b"))
            try {
                var state: Any? = try {
                    sides.cx.executeScriptWithContinuations(script, scope)
                } catch (pending: ContinuationPending) {
                    pending
                }
                while (state is ContinuationPending && answers.isNotEmpty()) {
                    state = try {
                        sides.cx.resumeContinuation(state.continuation, scope, answers.removeFirst())
                    } catch (pending: ContinuationPending) {
                        pending
                    }
                }
                if (state is ContinuationPending) "captured again" else RhinoScriptRuntime.toString(state)
            } catch (e: RuntimeException) {
                "${e.javaClass.simpleName}: ${e.message}"
            }
        }
        assertEquals("inner a / b,true", outcomes[0])
        assertEquals(outcomes[0], outcomes[1])
    }

    /** The registered differences, fix by fix (appendix E "P3.7"). zh-CN: 已登记的差异, 逐项核对 (附录 E "P3.7"). */
    @Test
    fun registeredDifferences() {
        // 1. The parent directory: jvm-npm also split `parent.id` at "," (and "|"). zh-CN: 父目录: jvm-npm 还按 "," (与 "|") 拆分 parent.id.
        assertDifference("require('./we,ird/m')", "\"native ./n\"", "\"n\"")
        // 2. Cached values. zh-CN: 缓存值.
        assertDifference("[require('./exp.json').v, require('./exp.json')]", "[1,5]", """[1,{"exports":5,"v":1}]""")
        assertDifference("[require('./undef') === undefined, typeof require('./undef')]", """[true,"object"]""", """[true,"undefined"]""")
        // 3. An empty file. zh-CN: 空文件.
        assertDifference("attempt(function () { return JSON.stringify(require('./empty')); })", """["throws","Error","IO_ERROR",true]""", "\"{}\"")
        // 4. Extensions in any case. zh-CN: 扩展名不分大小写.
        assertDifference("require('./Up.JS')", "undefined", "\"upper\"")
        // 6. The source name of module errors. zh-CN: 模块错误的源名.
        assertDifference("(function () { try { require('./err3'); } catch (e) { return [e.fileName === require.resolve('./err3'), e.lineNumber]; } })()", "[false,3]", "[true,3]")
        // 7. D10. zh-CN: D10.
        assertDifference("[typeof require(''), nativeRequired.join()]", """["string","./n,"]""", "throws TypeError [Failed to call method \"require\": [ Argument \"id\" cannot be an empty string ]]")
        assertDifference("message(function () { require(1); })", "\"Cannot find function endsWith.\"", "\"Failed to call method \\\"require\\\": [ Argument \\\"id\\\" must be of type string, got number ]\"")
        // 8. `Module.runMain` of a missing file. zh-CN: Module.runMain 找不到文件.
        assertDifference("attempt(function () { return Module.runMain('./nope'); })", """["throws","Error","IO_ERROR",true]""", """["throws","Error","MODULE_NOT_FOUND",true]""")
        // 9. IO_ERROR and PARSE_ERROR are Error objects with the cause's message. zh-CN: IO_ERROR 与 PARSE_ERROR 为带原因消息的 Error 对象.
        assertDifference(
            "(function () { try { require('./unreadable'); } catch (e) { return [clean(e.message), Object.prototype.toString.call(e)]; } })()",
            """["Cannot read file [<root>/unreadable.js]: ","[object Object]"]""",
            """["Cannot read file [<root>/unreadable.js]: denied","[object Error]"]""",
        )
        // Q83: the not-found error; jvm-npm passed a relative id that missed to the native `require`.
        // zh-CN: Q83: 找不到模块的错误; jvm-npm 把未命中的相对 id 交给原生 require.
        assertDifference("message(function () { require('missing-native'); })", "\"Cannot find module missing-native\"", "\"Cannot find module 'missing-native' (from <root>)\"")
        assertDifference("attempt(function () { return require('./nope'); })", "\"native ./nope\"", """["throws","Error","MODULE_NOT_FOUND",true]""")
        assertDifference("nativeRequired.join()", "\"./n,,missing-native,./nope\"", "\"missing-native\"")
    }

    @Test
    fun nothingIsPrintedOnAMiss() {
        val printed = listOf(Side.JS, Side.KT).map { side ->
            val previous = System.out
            val captured = ByteArrayOutputStream()
            System.setOut(PrintStream(captured, true, "UTF-8"))
            try {
                sides.renderWithMessage(side, "require('bare-miss')")
            } finally {
                System.setOut(previous)
            }
            captured.toString("UTF-8").trim()
        }
        // 5. zh-CN: 修复项 5.
        assertEquals(listOf("Cannot resolve bare-miss defaulting to native", ""), printed)
    }

    private companion object {

        /** The referenced module of `示例代码 > 控件 > 自定义控件-使用配置勾选框.js`. */
        const val SAMPLE = "src/main/assets-app/sample/控件/自定义控件-模块-配置勾选框.js"

        val PRELUDE = """
            var runtime = {
                files: {
                    cwd: function () { return ROOT; },
                    exists: function (path) { return new java.io.File(ROOT, path).exists(); },
                    readBytes: function (path) {
                        if (/unreadable\.js$/.test(String(path))) throw new java.io.IOException('denied');
                        return java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
                    },
                },
            };
            var nativeRequired = [];
            function nativeFake(id) {
                nativeRequired.push(String(id));
                return id === 'missing-native' ? undefined : 'native ' + id;
            }
            var Paths = java.nio.file.Paths;
            function rel(p) { return String(Paths.get(ROOT).relativize(Paths.get(String(p)))).split(java.io.File.separator).join('/'); }
            function clean(s) { return String(s).split(ROOT).join('<root>').split(java.io.File.separator).join('/'); }
            function attempt(f) {
                try { return f(); } catch (e) { return ['throws', String(e.name), String(e.code), e instanceof Error]; }
            }
            function message(f) {
                try { f(); return 'no throw'; } catch (e) { return clean(e.message); }
            }
            Object.defineProperty(this, 'log', { value: function () {}, writable: true, enumerable: true, configurable: false });
            var ui = { widgets: {}, Widget: function () { this.attrs = []; }, registerWidget: function (name, widget) { ui.widgets[name] = widget; } };
            ui.Widget.prototype.defineAttr = function (name) { this.attrs.push(name); };
            var util = { extend: function (child, parent) { child.prototype = Object.create(parent.prototype); child.prototype.constructor = child; } };
            var storages = {
                create: function (name) {
                    var values = {};
                    return { name: name, get: function (k, d) { return k in values ? values[k] : d; }, put: function (k, v) { values[k] = v; } };
                },
            };
        """.trimIndent()
    }

}
