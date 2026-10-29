package org.autojs.autojs.core.plugin.scriptmodule

import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Installed
import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.NotInstalled
import org.autojs.autojs.engine.module.PluginModuleScriptProvider
import org.autojs.autojs.engine.module.ProcessWideAssetModuleScriptProvider
import org.autojs.autojs.engine.module.RhinoAssetLoader
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.modules.bootstrap.BootstrapSides
import org.autojs.autojs.runtime.api.modules.bootstrap.require.AssetModuleSource
import org.autojs.autojs.runtime.api.modules.bootstrap.require.ScriptRequire
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.commonjs.module.Require
import org.mozilla.javascript.commonjs.module.provider.MultiModuleScriptProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.Reader

/**
 * Roadmap P4.3 (design `docs/augment/P4-3-bundled-libraries-design.md` (b) / (d) / (g)): the plugin modules in both states
 * of the module layer. Each side has the engine's arrangement: a Rhino `Require` over the asset provider (a temporary
 * asset directory with one local file) and [PluginModuleScriptProvider] (a fake plugin whose files are the stand-ins of
 * [FILES], since P5.2 removed the host's copies of the plugin's files); the "js" side loads jvm-npm over it, the "kt" side
 * mounts [ScriptRequire] with [AssetModuleSource], and both define a global `dayjs` getter through the Rhino `Require`, as
 * `ScriptRuntime.js_mod_dayjs`. Every expression renders the same on both sides in the four states (messages, codes,
 * libraries and reasons of the errors included); the "kt" side also gives its errors the position of the calling script.
 *
 * zh-CN: ROADMAP P4.3 (设计 P4-3 (b) / (d) / (g)): 模块层两态下的插件模块. 两侧都照引擎的结构: Rhino Require 之下是资产 provider (只有一个本地文件的临时资产目录)
 * 与 [PluginModuleScriptProvider] (插件替身, 文件为 [FILES] 中的替身, P5.2 已删除宿主中的插件文件); "js" 侧在其上加载 jvm-npm,
 * "kt" 侧以 [AssetModuleSource] 挂载 [ScriptRequire], 两侧都像 ScriptRuntime.js_mod_dayjs 那样经 Rhino Require 定义全局 dayjs getter. 四态下每个表达式两侧结果相同 (含错误的消息, code, library,
 * reason); "kt" 侧的错误位置还取调用脚本的位置.
 */
class PluginModuleDifferentialTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var sides: BootstrapSides
    private lateinit var assets: File
    private val catalog = requireNotNull(ScriptModulePluginCatalog.parse(File(ScriptModulePluginStateTest.CATALOG).readText()))
    private val all = catalog.libraries.map { it.id }.toSet()

    @Volatile
    private var state: ScriptModulePluginState = NotInstalled
    private var opened = mutableListOf<String>()

    private val plugin = object : ScriptModulePluginAccess {
        override fun state() = state
        override fun open(plugin: Installed, path: String): Reader? {
            opened += path
            return FILES[path]?.reader()
        }
    }

    private fun installed(pluginEnabled: Boolean = true, authorized: Boolean = true, enabled: Set<String> = all, version: Long = 1) =
        Installed(version, 2, authorized, pluginEnabled, catalog, enabled)

    @Before
    fun setUp() {
        sides = BootstrapSides()
        assets = temp.newFolder("assets")
        File(assets, "modules").mkdirs()
        File(assets, "modules/pangu.js").writeText("module.exports = { local: true };")
        sides.both(PRELUDE)
        install(sides.jsScope, "js")
        val jvmNpm = sides.module(sides.jsScope, sides.obsolete("jvm-npm.js")) as ScriptableObject
        sides.jsGlobal.defineProperty("Module", jvmNpm, ScriptableObject.PERMANENT)
        sides.jsGlobal.defineProperty("require", ScriptableObject.getProperty(jvmNpm, "require"), ScriptableObject.PERMANENT)
        val ktProvider = provider(sides.ktScope, "kt")
        val ktRequire = Require(sides.cx, sides.ktScope, ktProvider, null, null, true)
        ScriptableObject.putProperty(sides.ktGlobal, "rhinoRequire", ktRequire)
        val native = { id: String -> ktRequire.call(sides.cx, sides.ktScope, sides.ktGlobal, arrayOf(id)) }
        val canLoad = { id: String -> ktProvider.getModuleScript(Context.getCurrentContext(), id, null, null, null) != null }
        val instance by lazy { ScriptRequire(sides.ktScope, Host(temp.root.path), AssetModuleSource(canLoad, native), native, nodePath = { null }) }
        ScriptRequire.mount(sides.ktGlobal) { instance }
        sides.both(GETTER)
    }

    @After
    fun tearDown() {
        sides.close()
        PluginModuleScriptProvider.clearForTests()
    }

    /** The engine's providers for [scope]: the asset provider first, then the plugin. */
    private fun provider(scope: TopLevelScope, side: String) = MultiModuleScriptProvider(
        listOf(ProcessWideAssetModuleScriptProvider(Loader("${temp.root.path}|$side"), "modules"), PluginModuleScriptProvider(plugin, scope)),
    )

    /** The "js" side: the Rhino `Require` as the global `require` that jvm-npm takes as its native one. */
    private fun install(scope: TopLevelScope, side: String) {
        val require = Require(sides.cx, scope, provider(scope, side), null, null, true)
        val global = scope.globalThis as ScriptableObject
        ScriptableObject.putProperty(global, "require", require)
        ScriptableObject.putProperty(global, "rhinoRequire", require)
    }

    private inner class Loader(override val namespace: String) : RhinoAssetLoader {
        override fun open(assetPath: String): Reader {
            val file = File(assets, assetPath)
            if (!file.isFile) throw FileNotFoundException(assetPath)
            return file.reader()
        }
    }

    private class Host(private val cwd: String) : ScriptRequire.Host {
        override fun cwd() = cwd
        override fun exists(path: String) = File(cwd, path).exists()
        override fun readBytes(path: String) = File(path).readBytes()
    }

    private fun same(vararg expressions: String) = sides.assertSame(expressions.toList())

    private fun both(expression: String, expected: String) {
        assertEquals("js: $expression", expected, sides.js("JSON.stringify($expression)").toString())
        assertEquals("kt: $expression", expected, sides.kt("JSON.stringify($expression)").toString())
    }

    @Test
    fun installedAndEnabled() {
        state = installed()
        both("[typeof require('dayjs'), require('dayjs') === dayjs, typeof dayjs().format]", """["function",true,"function"]""")
        both("require('path').sep", "\"/\"")
        both("[typeof require('lodash').forEach, typeof require('events').EventEmitter, require('banana-i18n') === require('banana-i18n')]", """["function","function",true]""")
        both("typeof require('fs').existsSync", "\"function\"")
        same("require('dayjs')(0).valueOf()", "require('node:path').join('a', 'b')")
    }

    @Test
    fun localFilesWin() {
        state = installed()
        both("require('pangu').local", "true")
        assertEquals(emptyList<String>(), opened.filter { it.startsWith("pangu") })
    }

    @Test
    fun notInstalled() {
        state = NotInstalled
        val error = """["throws","Error","MODULE_NOT_FOUND",true,"dayjs","not-installed"]"""
        both("attempt(function () { return require('dayjs'); })", error)
        both("attempt(function () { return dayjs; })", error)
        both("attempt(function () { return require('banana-i18n'); }).slice(4)", """["i18n","not-installed"]""")
        both("attempt(function () { return require('path'); }).slice(4)", """["path","not-installed"]""")
        both(
            "message(function () { require('dayjs'); })",
            "\"Cannot find module 'dayjs': it is provided by the official plugin \\\"Bundled Libraries\\\", which is not installed. Install the plugin in AutoJs6 > Plugin center.\"",
        )
        same("message(function () { dayjs; })", "message(function () { require('lodash'); })")
        // The "kt" side reports the calling script. zh-CN: "kt" 侧报告调用脚本的位置.
        assertEquals("[\"kt-side\",2]", sides.kt(POSITION).toString())
    }

    @Test
    fun disabledInThePluginCenter() {
        state = installed(pluginEnabled = false)
        both("attempt(function () { return require('dayjs'); }).slice(4)", """["dayjs","plugin-disabled"]""")
        both("attempt(function () { return dayjs; }).slice(4)", """["dayjs","plugin-disabled"]""")
        same("message(function () { require('path'); })")
        state = installed(pluginEnabled = false, authorized = false)
        both("attempt(function () { return require('dayjs'); }).slice(4)", """["dayjs","unauthorized"]""")
    }

    @Test
    fun libraryDisabledOnThePluginPage() {
        state = installed(enabled = all - "dayjs" - "buffer")
        both("attempt(function () { return require('dayjs'); }).slice(1)", """["Error","MODULE_NOT_FOUND",true,"dayjs","library-disabled"]""")
        both("attempt(function () { return dayjs; }).slice(4)", """["dayjs","library-disabled"]""")
        // fs needs buffer: the error names the library that is off. zh-CN: fs 依赖 buffer: 错误给出被关闭的库.
        both("attempt(function () { return require('fs'); }).slice(4)", """["buffer","library-disabled"]""")
        both("require('path').sep", "\"/\"")
        same("message(function () { require('dayjs'); })")
    }

    /** A switch takes effect for the next require of a module the script has not loaded; loaded ones stay (design (d)). */
    @Test
    fun switchesTakeEffectOnTheNextRequire() {
        state = installed()
        both("typeof require('dayjs')", "\"function\"")
        state = installed(enabled = all - "dayjs" - "lodash")
        both("[typeof require('dayjs'), typeof dayjs]", """["function","function"]""")
        both("attempt(function () { return require('lodash'); }).slice(4)", """["lodash","library-disabled"]""")
        state = installed(enabled = all)
        both("typeof require('lodash').forEach", "\"function\"")
    }

    /** Uninstalling gives the guidance again for modules not loaded yet; a newer plugin version is read again. */
    @Test
    fun uninstallAndUpdate() {
        state = installed()
        both("typeof require('path').join", "\"function\"")
        state = NotInstalled
        both("attempt(function () { return require('events'); }).slice(4)", """["events","not-installed"]""")
        state = installed(version = 2)
        val before = opened.size
        both("typeof require('events').EventEmitter", "\"function\"")
        assertEquals(listOf("events.js"), opened.drop(before))
    }

    companion object {

        /**
         * The fake plugin's files: stand-ins with the shape the tests read (a module with a sub-path, `fs` needing `buffer`),
         * not the libraries.
         * zh-CN: 插件替身的文件: 只具备测试读取的形状 (带子路径的模块, fs 依赖 buffer), 不是真正的库.
         */
        val FILES = mapOf(
            "dayjs.js" to "module.exports = require('dayjs/dayjs.min.js');",
            "dayjs/dayjs.min.js" to "module.exports = function dayjs(t) { var v = t === undefined ? 1 : +t; return { valueOf: function () { return v; }, format: function () { return String(v); } }; };",
            "path.js" to "exports.sep = '/'; exports.join = function () { return Array.prototype.slice.call(arguments).join('/'); };",
            "lodash.js" to "exports.forEach = function (a, f) { for (var i = 0; i < a.length; i++) f(a[i], i); return a; };",
            "events.js" to "function EventEmitter() {} EventEmitter.EventEmitter = EventEmitter; module.exports = EventEmitter;",
            "banana-i18n.js" to "module.exports = function Banana() {};",
            "buffer.js" to "exports.Buffer = function Buffer() {};",
            "fs.js" to "var Buffer = require('buffer').Buffer; var path = require('path'); exports.existsSync = function () { return false; };",
        )

        val PRELUDE = """
            var runtime = { files: { cwd: function () { return ''; }, exists: function () { return false; }, readBytes: function () { throw new Error('no files'); } } };
            function attempt(f) {
                try { return f(); } catch (e) { return ['throws', String(e.name), String(e.code), e instanceof Error, String(e.library), String(e.reason)]; }
            }
            function message(f) {
                try { f(); return 'no throw'; } catch (e) { return String(e.message); }
            }
        """.trimIndent()

        /** The global getter of `ScriptRuntime.js_mod_dayjs`: the module of the engine's `require`. */
        val GETTER = """
            Object.defineProperty(this, 'dayjs', { get: function () { return rhinoRequire('dayjs'); }, enumerable: true, configurable: false });
        """.trimIndent()

        val POSITION = """
            (function () { try {
                require('dayjs');
            } catch (e) { return JSON.stringify([e.fileName, e.lineNumber]); } })()
        """.trimIndent()
    }

}
