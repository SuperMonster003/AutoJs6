package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.modules.differential.DifferentialCases
import org.autojs.autojs.runtime.api.modules.differential.DifferentialCases.KnownDifference
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Obsolete JS against the new script module layer for the P3.4 members whose obsolete code runs without Android: the
 * global `sync` (`__threads__.js`), `tasks.timeFlagToDays` / `daysToTimeFlag` (`__tasks__.js`), `autojs.version`
 * (`__autojs__.js`), `app.intentToShell` (`__app__.js`) and `files.join` (`__files__.js`). The obsolete modules run with
 * stubs for the host objects they read when loaded (`context`, `R`, `Version`, `app`, the runtime's `files` / `threads`);
 * the other members of these modules forward to Android host objects in both implementations and are covered by the
 * module tests (`CoreRuntimeModulesTest`, `AppModulesTest`, `TaskModulesTest`, `ShellModulesTest`) and on the device.
 * Error types are compared (D10); the differences left are rows of ROADMAP appendix E "P3.4 new deviations".
 *
 * zh-CN: P3.4 中旧代码无需 Android 即可运行的成员的旧 JS 对新层差分: 全局 sync (`__threads__.js`), tasks.timeFlagToDays / daysToTimeFlag (`__tasks__.js`),
 * autojs.version (`__autojs__.js`), app.intentToShell (`__app__.js`) 与 files.join (`__files__.js`). 旧模块以 stub 提供其加载时读取的宿主对象 (context, R,
 * Version, app, 运行时的 files / threads); 这些模块的其他成员在两种实现中都转发给 Android 宿主对象, 由各模块测试与真机覆盖. 比较错误类型 (D10); 剩余差异见附录 E
 * "P3.4 新发现的偏差".
 */
class RuntimeModulesDifferentialTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
        RuntimeModulesFixture.prepareHost(rhino.ktRuntime, RuntimeModulesFixture.CORE_FIELDS + "app")
        RuntimeModulesFixture.installAppContext()
        rhino.mountNewModules("app", "autojs", "threads", "tasks", "files")
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun resultsAgree() {
        DifferentialCases.run(rhino, EXPRESSIONS, KNOWN_DIFFERENCES, exportDir = "build/script-modules-device", exportAll = true)
    }

    companion object {


        private val EXPRESSIONS = listOf(
            "typeof sync(function () {})",
            "sync(function (a) { return a + 1; })(1)",
            "sync(function (a) { return a * 2; }, {})(21)",
            "sync(function () { return this === globalThis || this === undefined; })()",
            "sync(1)",
            "sync()",
            "typeof sync(function () {}, {}, 1)",
            "tasks.timeFlagToDays(5)",
            "tasks.timeFlagToDays(127)",
            "tasks.timeFlagToDays(0)",
            "tasks.timeFlagToDays('5')",
            "tasks.timeFlagToDays(64)",
            "tasks.timeFlagToDays()",
            "tasks.daysToTimeFlag([0, 2])",
            "tasks.daysToTimeFlag([6, 0, 6])",
            "tasks.daysToTimeFlag([])",
            "tasks.daysToTimeFlag([7])",
            "tasks.daysToTimeFlag([0, '2'])",
            "tasks.daysToTimeFlag(null)",
            "autojs.version.code === autojs.versionCode && autojs.version.name === autojs.versionName && autojs.version.date === autojs.versionDate",
            "autojs.version.isAtLeast('1.0.0')",
            "autojs.version.isAtLeast('99.0.0')",
            "autojs.version.isAtLeast(autojs.version.name)",
            "autojs.version.isAtLeast(autojs.version.name, true)",
            "autojs.version.isAtLeast('6.0.0', 1)",
            "autojs.version.isHigherThan('1.0.0')",
            "autojs.version.isHigherThan('99.0.0')",
            "autojs.version.isLowerThan('99.0.0')",
            "autojs.version.isEqual(autojs.version.name)",
            "autojs.version.isEqual('1.0.0')",
            "autojs.version.isAtLeast(new Packages.io.github.g00fy2.versioncompare.Version('1.0.0'))",
            "autojs.version.isAtLeast(null)",
            "autojs.version.isAtLeast()",
            "[autojs.versionCode, autojs.versionName, autojs.versionDate].join()",
            "app.intentToShell({ action: 'VIEW', data: 'x' })",
            "app.intentToShell({ action: 'android.intent.action.MAIN' })",
            "app.intentToShell({ packageName: 'a.b', className: 'a.b.C' })",
            "app.intentToShell({ extras: { k: 'v', n: 1, f: 1.5, b: true } })",
            "app.intentToShell({ category: ['A', 'B'] })",
            "app.intentToShell({ category: 'A' })",
            "app.intentToShell({ type: 'text/plain' })",
            "app.intentToShell({ flags: 268435456 })",
            "app.intentToShell({})",
            "app.intentToShell(1)",
            "files.join('a', 'b') === String(new java.io.File('a', 'b').getPath())",
            "files.join('a')",
            "files.join('a', null)",
            "files.join()",
        )

        private val KNOWN_DIFFERENCES = listOf(
            KnownDifference("sync(1)", "throws InternalError", "throws TypeError", "D10: a target that is not a function is a TypeError (the obsolete JS failed in Rhino's Java constructor lookup)"),
            KnownDifference("sync()", "throws InternalError", "throws TypeError", "D10 / T4: the function is required"),
            KnownDifference("typeof sync(function () {}, {}, 1)", "\"function\"", "throws TypeError", "Q7: surplus arguments are a TypeError"),
            KnownDifference("tasks.timeFlagToDays()", "[0,1,2]", "throws TypeError", "T4: the flag is required (the obsolete JS read the digits of NaN)"),
            KnownDifference("tasks.daysToTimeFlag([0, '2'])", "1", "5", "current Kotlin kept: day numbers are read as JS numbers (the obsolete JS compared strictly)"),
            KnownDifference("tasks.daysToTimeFlag(null)", "throws TypeError", "0", "current Kotlin kept: no days give flag 0"),
            KnownDifference("autojs.version.isAtLeast('6.0.0', 1)", "true", "throws TypeError", "T4: ignoreSuffix is a boolean as documented"),
            KnownDifference("autojs.version.isAtLeast(null)", "throws InternalError", "throws TypeError", "D10: a nullish version is a TypeError (the obsolete JS failed in Rhino's overload choice)"),
            KnownDifference("autojs.version.isAtLeast()", "true", "throws TypeError", "T4: the version is required"),
            KnownDifference("app.intentToShell({ action: 'VIEW', data: 'x' })", "\" -a 'android.intent.action.VIEW' -d x\"", "\"-a 'android.intent.action.VIEW' -d x\"", "current Kotlin kept: the command is trimmed (the obsolete JS left a leading space)"),
            KnownDifference("app.intentToShell({ action: 'android.intent.action.MAIN' })", "\" -a 'android.intent.action.MAIN'\"", "\"-a 'android.intent.action.MAIN'\"", "current Kotlin kept: trimmed"),
            KnownDifference("app.intentToShell({ packageName: 'a.b', className: 'a.b.C' })", "\" -n 'a.b/a.b.C'\"", "\"-n 'a.b/a.b.C'\"", "current Kotlin kept: trimmed"),
            KnownDifference("app.intentToShell({ extras: { k: 'v', n: 1, f: 1.5, b: true } })", "\" --es 'k' 'v' --ei 'n' 1 --ef 'f' 1.5 --ez 'b' true\"", "\"--es 'k' 'v' --ei 'n' 1 --ef 'f' 1.5 --ez 'b' true\"", "current Kotlin kept: trimmed"),
            KnownDifference("app.intentToShell({ category: ['A', 'B'] })", "\" -c A -c B\"", "\"-c A -c B\"", "current Kotlin kept: trimmed"),
            KnownDifference("app.intentToShell({ category: 'A' })", "\" -c A\"", "\"-c A\"", "current Kotlin kept: trimmed"),
            KnownDifference("app.intentToShell({ type: 'text/plain' })", "\" -t text/plain\"", "\"-t text/plain\"", "current Kotlin kept: trimmed"),
            KnownDifference("app.intentToShell({ flags: 268435456 })", "\" -f 268435456\"", "\"-f 268435456\"", "current Kotlin kept: trimmed"),
            KnownDifference("app.intentToShell(1)", "\"\"", "throws TypeError", "D10: an intent or an options object (the obsolete JS read no property of the number)"),
            KnownDifference("files.join('a', null)", "throws JavaException NullPointerException", "throws TypeError", "D10: path parts are strings"),
            KnownDifference("files.join()", "throws InternalError", "throws TypeError", "D10 / T4: the base path is required"),
        )

    }

}
