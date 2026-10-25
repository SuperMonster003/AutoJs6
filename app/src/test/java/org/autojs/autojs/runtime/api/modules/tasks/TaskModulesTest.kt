package org.autojs.autojs.runtime.api.modules.tasks

import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.autojs.autojs.runtime.api.modules.ScriptModuleExpectations
import org.autojs.autojs.runtime.api.modules.differential.DifferentialRhino
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.LazyDataSlot

/**
 * Roadmap P3.4, third group: `settings`, `sysprops`, `powerManager`, `tasks` and `workManager` on the new script module
 * layer. Surfaces against the current augment calls in the same JVM ([RuntimeModulesFixture], `length` left out, appendix
 * C Q9), the argument rules that fail before any host call, the pure `tasks` helpers; reading system properties, the
 * settings stores, battery optimizations and the task database run on the device
 * (`build/script-modules-device/TaskModulesTest.json`).
 *
 * zh-CN: P3.4 第三组: 新层上的 settings, sysprops, powerManager, tasks 与 workManager. 表面在同一 JVM 中与现有 augment 调用比较 (不含 length), 检查在调用宿主
 * 之前失败的参数规则与 tasks 的纯计算函数; 读取系统属性, 设置存储, 电池优化与任务数据库在真机运行.
 */
class TaskModulesTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = newSide()
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
        val actual = RuntimeModulesFixture.describe(rhino, GLOBALS, MODULES)
        val expected = ScriptModuleExpectations.obsolete("describe")
        assertEquals(expected, actual)
    }

    @Test
    fun installingBuildsNothing() {
        val global = rhino.ktScope.globalThis
        GLOBALS.forEach { assertTrue(it, LazyDataSlot.isPending(global, it)) }
    }

    @Test
    fun tasksArguments() = check(
        listOf(
            "tasks.timeFlagToDays(5)" to "[0,2]",
            "tasks.timeFlagToDays(127)" to "[0,1,2,3,4,5,6]",
            "tasks.timeFlagToDays(0)" to "[]",
            "tasks.daysToTimeFlag([0, 2])" to "5",
            "tasks.daysToTimeFlag([6, 0, 6])" to "65",
            "tasks.daysToTimeFlag(null)" to "0",
            "tasks.addTask(null)" to "null",
            "tasks.updateTask(null)" to "null",
            "tasks.addTask(1)" to "throws TypeError",
            "tasks.addTask({})" to "throws TypeError",
            "tasks.addTask()" to "throws TypeError",
            "tasks.addTimedTask(1)" to "throws TypeError",
            "tasks.addDailyTask('x')" to "throws TypeError",
            "tasks.addCountdownTask({}, {})" to "throws TypeError",
            "tasks.getTimedTask()" to "throws TypeError",
            "tasks.getTimedTask('x')" to "throws TypeError",
            "tasks.getTimedTask(NaN)" to "throws RangeError",
            "tasks.getIntentTask(null)" to "throws TypeError",
            "tasks.removeTask(1)" to "throws TypeError",
            "tasks.removeTimedTask(1, 2)" to "throws TypeError",
            "tasks.removeIntentTask('x')" to "throws TypeError",
            "tasks.updateTask('x')" to "throws TypeError",
            "tasks.queryTimedTasks(1)" to "throws TypeError",
            "tasks.queryIntentTasks({}, 1)" to "throws TypeError",
            "tasks.timeFlagToDays()" to "throws TypeError",
            "tasks.timeFlagToDays('x')" to "throws TypeError",
            "tasks.timeFlagToDays(1.5)" to "[0]",
            "tasks.daysToTimeFlag('x')" to "throws TypeError",
            "tasks.daysToTimeFlag([1, 'x'])" to "throws TypeError",
        )
    )

    @Test
    fun workManagerSettingsPowerAndSyspropsArguments() = check(
        listOf(
            "workManager === work_manager && \$workManager === workManager && \$work_manager === workManager" to "true",
            "powerManager === power_manager && \$power_manager === powerManager" to "true",
            "workManager.getTimedTask()" to "throws TypeError",
            "workManager.getTimedTask(null)" to "throws TypeError",
            "workManager.removeTimedTask('x')" to "throws TypeError",
            "workManager.removeIntentTask(1, 2)" to "throws TypeError",
            "workManager.addDailyTask(1)" to "throws TypeError",
            "workManager.queryTimedTasks({}, 1)" to "throws TypeError",
            "timers.getTimedTask('x')" to "throws TypeError",
            "settings.isEnabled()" to "throws TypeError",
            "settings.isEnabled(null)" to "throws TypeError",
            "settings.setEnabled('stable_mode')" to "throws TypeError",
            "settings.setEnabled('stable_mode', 1)" to "throws TypeError",
            "settings.setEnabled('stable_mode', true, 1)" to "throws TypeError",
            "powerManager.isIgnoringBatteryOptimizations(1, 2)" to "throws TypeError",
            "powerManager.requestIgnoreBatteryOptimizations(1)" to "throws TypeError",
            "typeof sysprops" to "\"function\"",
            "sysprops()" to "throws TypeError",
            "sysprops.get()" to "throws TypeError",
            "sysprops.get(null)" to "throws TypeError",
            "sysprops('a', 'b', 'c')" to "throws TypeError",
            "sysprops.getInt('a', 'x')" to "throws TypeError",
            "sysprops.getBoolean('a', 1)" to "throws TypeError",
            "sysprops.getAll(1, 2, 3)" to "throws TypeError",
        )
    )

    /** Behaviour that needs Android: written for the device only. */
    @Test
    fun deviceOnlyCases() = RuntimeModulesFixture.exportForDevice(
        EXPORT,
        listOf(
            "sysprops.get('ro.build.version.sdk') === String(device.sdkInt)" to "true",
            "sysprops('ro.build.version.sdk') === sysprops.get('ro.build.version.sdk')" to "true",
            "sysprops.getInt('ro.build.version.sdk') === device.sdkInt" to "true",
            "sysprops.get('aj6.no.such.prop', 'dft')" to "\"dft\"",
            "sysprops.getInt('aj6.no.such.prop', 7)" to "7",
            "sysprops.getBoolean('aj6.no.such.prop', true)" to "true",
            "Object.keys(sysprops.getAll(/^ro\\.build\\.version\\.sdk$/)).join()" to "\"ro.build.version.sdk\"",
            "Object.keys(sysprops.getAll({ keys: /^ro\\.build\\.version\\.sdk$/ })).join()" to "\"ro.build.version.sdk\"",
            "Object.keys(sysprops.getAll({ key: 'ro.build.version.sdk', values: /^\\d+$/ })).indexOf('ro.build.version.sdk') >= 0" to "true",
            "Object.keys(sysprops.getAll('ro.build.version.sdk', /^\\d+$/)).indexOf('ro.build.version.sdk') >= 0" to "true",
            "Object.keys(sysprops.getAll()).length > 10" to "true",
            "typeof settings.isEnabled('stable_mode')" to "\"boolean\"",
            "(function () { var v = settings.isEnabled('stable_mode'); settings.setEnabled('stable_mode', v); return settings.isEnabled('stable_mode') === v; })()" to "true",
            "typeof powerManager.isIgnoringBatteryOptimizations()" to "\"boolean\"",
            "powerManager.isIgnoringBatteryOptimizations('org.autojs.autojs6') === powerManager.isIgnoringBatteryOptimizations()" to "true",
            "Array.isArray(tasks.queryTimedTasks())" to "true",
            "tasks.queryIntentTasks({ action: 'aj6.p34.none' }).length" to "0",
            "tasks.getTimedTask(-1)" to "null",
            "tasks.getIntentTask(-1)" to "null",
            "(function () { var t = tasks.addDisposableTask({ path: files.join(files.cwd(), 'aj6-p34-task.js'), date: Date.now() + 36e5 }); var ok = tasks.getTimedTask(t.id) !== null; tasks.removeTask(t); return ok && tasks.getTimedTask(t.id) === null; })()" to "true",
            "Array.isArray(workManager.queryTimedTasks())" to "true",
            "workManager.getTimedTask(-1)" to "null",
            "workManager.removeTimedTask(-1)" to "null",
            "workManager.removeIntentTask(-1)" to "null",
        )
    )

    private fun newSide(): DifferentialRhino = DifferentialRhino().also { side ->
        RuntimeModulesFixture.prepareHost(side.ktRuntime)
        RuntimeModulesFixture.mount(side, MODULES + "timers")
    }

    private companion object {
        const val EXPORT = "TaskModulesTest"
        val MODULES = listOf("tasks", "workManager", "settings", "powerManager", "sysprops")
        val GLOBALS = listOf(
            "tasks", "\$tasks", "workManager", "\$workManager", "work_manager", "\$work_manager", "settings", "\$settings",
            "powerManager", "\$powerManager", "power_manager", "\$power_manager", "sysprops", "\$sysprops",
        )
    }

}
