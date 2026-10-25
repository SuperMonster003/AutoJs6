package org.autojs.autojs.runtime.api.modules.settings

import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.RuntimeModulesFixture
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P12.1: the arguments of `settings.export` and `settings.categories` on the new script module
 * layer, refused before anything is read (the surface is compared with the current module by `TaskModulesTest`).
 * zh-CN: ROADMAP P12.1: 新脚本模块层上 `settings.export` 与 `settings.categories` 的参数检查, 在读取任何内容之前拒绝
 * (表面由 `TaskModulesTest` 与现有模块比较).
 */
class SettingsModuleTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino().also { side ->
            RuntimeModulesFixture.prepareHost(side.ktRuntime)
            RuntimeModulesFixture.mount(side, listOf("settings"), newLayer = true) { name, global, runtime ->
                check(name == "settings") { name }
                Settings(runtime).augment(global)
            }
        }
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun exportAndCategoriesArguments() = RuntimeModulesFixture.check(
        rhino,
        listOf(
            "typeof settings.export" to "\"function\"",
            "typeof settings.categories" to "\"function\"",
            "settings.export(1)" to "throws TypeError",
            "settings.export({})" to "throws TypeError",
            "settings.export('')" to "throws RangeError",
            "settings.export('  ')" to "throws RangeError",
            "settings.export(null, 1)" to "throws TypeError",
            "settings.export(null, [])" to "throws TypeError",
            "settings.export(null, ['appearance', 1])" to "throws TypeError",
            "settings.export(null, 'nope')" to "throws RangeError",
            "settings.export(undefined, ['appearance', 'nope'])" to "throws RangeError",
            "settings.export(null, null, 1)" to "throws TypeError",
            "settings.categories(1)" to "throws TypeError",
        )
    )

}
