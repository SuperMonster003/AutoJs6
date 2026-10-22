package org.autojs.autojs.runtime.api.modules.shell

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.augment.plugins.Plugins
import org.autojs.autojs.runtime.api.augment.plugins.PluginsTools
import org.autojs.autojs.runtime.api.augment.shell.Shell
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot

/**
 * Roadmap P3.4, fourth group: `shell`, `shizuku` and `plugins` (with `plugins.tools`) on the new script module layer. The
 * surfaces of shell and plugins are compared with the current augment calls in the same JVM ([RuntimeModulesFixture],
 * `length` left out, appendix C Q9); `shizuku` has `runtime.shizuku` (an object that needs Android) as its prototype, so
 * its surface is compared on the device only and its argument rules run here on a module without prototype. Commands,
 * processes, Shizuku state and the plugin tools run on the device (`build/script-modules-device/ShellModulesTest.json`;
 * no key events are sent, they would act on the device).
 *
 * zh-CN: P3.4 第四组: 新层上的 shell, shizuku 与 plugins (含 plugins.tools). shell 与 plugins 的表面在同一 JVM 中与现有 augment 调用比较 (不含 length);
 * shizuku 以需要 Android 的 runtime.shizuku 为原型, 表面只在真机比较, 参数规则在此以无原型的模块对象检查. 命令, 进程与 Shizuku 状态在真机运行 (不发送按键事件,
 * 那会操作设备).
 */
class ShellModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = newSide(newLayer = true)
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    private fun check(cases: List<Pair<String, String>>) {
        RuntimeModulesFixture.check(rhino, cases)
        RuntimeModulesFixture.exportForDevice(EXPORT, cases)
    }

    @Test
    fun surfaceMatchesTheCurrentModules() {
        val actual = RuntimeModulesFixture.describe(rhino, GLOBALS, PATHS)
        val expected = newSide(newLayer = false).use { RuntimeModulesFixture.describe(it, GLOBALS, PATHS) }
        assertEquals(expected, actual)
    }

    @Test
    fun installingBuildsNothing() {
        val global = rhino.ktScope.globalThis
        (GLOBALS + listOf("shizuku", "\$shizuku")).forEach { assertTrue(it, LazyDataSlot.isPending(global, it)) }
    }

    @Test
    fun shellArguments() = check(
        listOf(
            "shell.getCommand('ls')" to "\"ls\"",
            "shell.getCommand('ls', true)" to "\"su\\nls\"",
            "shell.getCommand('ls', 1)" to "\"su\\nls\"",
            "shell.getCommand(['a', 'b'])" to "\"a\\nb\"",
            "shell.getCommand('ls', { all: true })" to "\"ls --all\"",
            "shell.getCommand('ls', { a: 1 }, true)" to "\"su\\nls -a 1\"",
            "shell.getCommand('ls', undefined)" to "\"ls\"",
            "shell()" to "throws TypeError",
            "shell(1)" to "throws TypeError",
            "shell(null)" to "throws TypeError",
            "shell('ls', null)" to "throws TypeError",
            "shell('ls', [])" to "throws TypeError",
            "shell('ls', 1, true)" to "throws TypeError",
            "shell('ls', true, 1, 2)" to "throws TypeError",
            "shell.execCommand({})" to "throws TypeError",
            "shell.fromIntent(1)" to "throws TypeError",
            "shell.spawn()" to "throws TypeError",
            "shell.spawn(1)" to "throws TypeError",
            "shell.spawn('ls', 'x')" to "throws TypeError",
            "shell.spawn('ls', true, 1)" to "throws TypeError",
            "shell.setDefaultOptions(1)" to "throws TypeError",
            "shell.setDefaultOptions()" to "throws TypeError",
            "shell.checkAccess('x')" to "throws RangeError",
            "shell.checkAccess()" to "throws TypeError",
            "shell.isRootAvailable(1)" to "throws TypeError",
            "Tap(1)" to "throws TypeError",
            "Tap('a', 1)" to "throws TypeError",
            "Swipe(1, 2, 3)" to "throws TypeError",
            "Swipe(1, 2, 3, 4, 'x')" to "throws TypeError",
            "KeyCode({})" to "throws TypeError",
            "KeyCode()" to "throws TypeError",
            "Text()" to "throws TypeError",
            "Menu(1)" to "throws TypeError",
            "SetScreenMetrics(1)" to "throws TypeError",
        )
    )

    @Test
    fun shizukuArguments() = check(
        listOf(
            "typeof shizuku" to "\"function\"",
            "shizuku.getCommand(['a', 'b'])" to "\"a\\nb\"",
            "shizuku.getCommand('ls', '-l')" to "\"ls -l\"",
            "shizuku()" to "throws TypeError",
            "shizuku(1)" to "throws TypeError",
            "shizuku.execCommand('ls', null)" to "throws TypeError",
            "shizuku.spawn(1)" to "throws TypeError",
            "shizuku.spawn('ls', 'x')" to "throws TypeError",
            "shizuku.currentPackage(1)" to "throws TypeError",
        )
    )

    @Test
    fun pluginsArguments() = check(
        listOf(
            "typeof plugins" to "\"function\"",
            "[typeof plugins.extend.exclude, typeof plugins.extend.registerModule]" to "[\"function\",\"function\"]",
            "(plugins.extend('Mathx'), typeof Math.sum)" to "\"function\"",
            "(plugins.extend('number'), typeof Number.prototype.clamp)" to "\"function\"",
            // A device whose option "extend JavaScript built-in objects" is on has Arrayx before the script starts (P3.4 (5)):
            // these two compare with the state before the call (on the JVM, and on most devices, no `distinct` before).
            "(function () { var before = typeof [].distinct; plugins.extendAllBut('Arrayx'); return typeof [].distinct === before; })()" to "true",
            // Exclusions stay for the script (the current registry): extendAll keeps Arrayx out, extend names it explicitly.
            "(function () { var before = typeof [].distinct; plugins.extendAll(); return typeof [].distinct === before; })()" to "true",
            "(plugins.extend('Arrayx'), typeof [].distinct)" to "\"function\"",
            "(plugins.extend.registerModule({ Barx: { extendJsBuildInObjects: function () { globalThis.__aj6p34 = 1; } } }), plugins.extend('Barx'), globalThis.__aj6p34)" to "1",
            "plugins()" to "throws TypeError",
            "plugins.load()" to "throws TypeError",
            "plugins.load(null)" to "throws TypeError",
            "plugins.load('a', 'b')" to "throws TypeError",
            "plugins.tools.list(1)" to "throws TypeError",
            "plugins.tools.call()" to "throws TypeError",
            "plugins.tools.call(1)" to "throws TypeError",
            "plugins.tools.call('  ')" to "throws RangeError",
            "plugins.tools.call('a', 1)" to "throws TypeError",
            "plugins.tools.call('a', {}, 1)" to "throws TypeError",
            "plugins.tools.call('a', {}, {}, 1)" to "throws TypeError",
        )
    )

    /** Behaviour that needs Android (commands, processes, Shizuku): written for the device only; no key events are sent. */
    @Test
    fun deviceOnlyCases() = RuntimeModulesFixture.exportForDevice(
        EXPORT,
        listOf(
            "shell('echo aj6p34').code" to "0",
            "String(shell('echo aj6p34').result).trim()" to "\"aj6p34\"",
            "String(shell(['echo a', 'echo b']).result).trim().split('\\n').join()" to "\"a,b\"",
            "shell.execCommand('echo aj6p34', { exit: true }).code" to "0",
            "(function () { var p = shell.spawn('echo aj6p34'); var line = p.readLine(); p.waitFor(); return String(line); })()" to "\"aj6p34\"",
            "(function () { var p = shell.spawn(['echo a', 'echo b'], { env: { AJ6: 'x' } }); var r = [p.readLine(), p.readLine()].join(); p.waitFor(); return r; })()" to "\"a,b\"",
            "shell.fromIntent({ action: 'VIEW' })" to "\"-a 'android.intent.action.VIEW'\"",
            "typeof shell.isRootAvailable()" to "\"boolean\"",
            "(shell.setDefaultOptions({ root: false }), shell.getCommand('ls'))" to "\"ls\"",
            "shell.setDefaultOptions(null)" to "undefined",
            "shell.setDefaultOptions({ nope: 1 })" to "throws JavaException IllegalArgumentException",
            "shell.checkAccess('adb') === shizuku.state.isOperational" to "true",
            "typeof shell.currentPackage()" to "\"string\"",
            "KeyCode('NO_SUCH_KEY_AJ6P34')" to "throws RangeError",
            "Object.keys(shizuku.state).join()" to "\"isInstalled,hasService,isRunning,hasPermission,isOperational\"",
            "typeof shizuku.currentPackage()" to "\"string\"",
            "typeof shizuku.isOperational" to "\"function\"",
        )
    )

    private fun newSide(newLayer: Boolean): DifferentialRhino = DifferentialRhino().also { side ->
        RuntimeModulesFixture.prepareHost(side.ktRuntime)
        RuntimeModulesFixture.mount(side, MODULES + (if (newLayer) listOf("shizuku") else emptyList()), newLayer) { name, global, runtime ->
            when (name) {
                "shell" -> Shell(runtime).augment(global)
                "plugins" -> Plugins(runtime).augment(global).also { PluginsTools(runtime).augment(it) }
                else -> throw AssertionError(name)
            }
        }
    }

    private companion object {
        const val EXPORT = "ShellModulesTest"
        val MODULES = listOf("shell", "plugins")
        val PATHS = listOf("shell", "plugins", "plugins.tools")
        val GLOBALS = listOf(
            "Menu", "Home", "Back", "Up", "Down", "Left", "Right", "OK", "VolumeUp", "VolumeDown", "Power", "Camera", "Text", "Input", "Tap",
            "Screencap", "KeyCode", "SetScreenMetrics", "Swipe", "shell", "\$shell", "plugins", "\$plugins",
        )
    }

}
