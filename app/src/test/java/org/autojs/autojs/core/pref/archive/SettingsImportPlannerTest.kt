package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.SettingEffect
import org.autojs.autojs.core.pref.TypedValue
import org.autojs.autojs.core.pref.archive.ImportPlan.Prerequisite
import org.autojs.autojs.core.pref.archive.ImportPlan.SkipReason
import org.autojs.autojs.core.pref.archive.SettingsSnapshotCodec.Rejection
import org.autojs.autojs.core.pref.keys.AppearanceKeys
import org.autojs.autojs.core.pref.keys.ConsoleKeys
import org.autojs.autojs.core.pref.keys.DeveloperKeys
import org.autojs.autojs.core.pref.keys.DomainKeysTestBase
import org.autojs.autojs.core.pref.keys.EditorKeys
import org.autojs.autojs.core.pref.keys.ExplorerKeys
import org.autojs.autojs.core.pref.keys.ExtensibilityKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.pref.keys.NamedStores
import org.autojs.autojs.core.pref.keys.PluginCenterKeys
import org.autojs.autojs.core.pref.store.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P7.2: the import plan of [SettingsImportPlanner] (T8, D8 / D9 / D20, appendix B "import
 * rules") over the real registry: missing keys are removed, unselected categories stay, the five skip reasons,
 * `plugin_enable_state` of packages not installed, `other` writes registered states only, and the deployment items of
 * the working directory and the editor font (given up -> `DECLINED`).
 *
 * zh-CN: ROADMAP P7.2: 以真实注册表验证 [SettingsImportPlanner] 的导入计划 (T8, D8 / D9 / D20, 附录 B "导入规则"): 文件缺键即删除,
 * 未勾选分类不动, 五种跳过原因, 未安装包名的 `plugin_enable_state`, `other` 只写登记过的状态, 工作目录与编辑器字体的部署项 (放弃即
 * `DECLINED`).
 */
class SettingsImportPlannerTest : DomainKeysTestBase() {

    private val nothingInstalled = ImportEnvironment()

    @Test
    fun keysTheFileDoesNotHaveAreRemoved() {
        store.edit {
            put(AppearanceKeys.NIGHT_MODE_ENABLED.name, true)
            put(AppearanceKeys.THEME_COLOR_PRIMARY.name, 7)
            put(AppearanceKeys.APP_LANGUAGE.name, key("key_app_language_en"))
        }
        val file = file("appearance", "default" to mapOf(AppearanceKeys.THEME_COLOR_PRIMARY.name to TypedValue("int", 9)))
        val plan = plan(listOf(file), "appearance")
        assertEquals(listOf("appearance"), plan.categories)
        assertEquals(listOf(ImportPlan.Put("appearance", StoreId.DEFAULT, AppearanceKeys.THEME_COLOR_PRIMARY.name, 9, SettingEffect.APPLY)), plan.puts)
        // Only the stored keys are removed; the ones without a stored value already have their defaults.
        assertEquals(
            setOf(AppearanceKeys.NIGHT_MODE_ENABLED.name, AppearanceKeys.APP_LANGUAGE.name),
            plan.removes.map { it.name }.toSet(),
        )
        assertTrue(plan.removes.all { it.category == "appearance" && it.store == StoreId.DEFAULT && it.effect == SettingEffect.APPLY })
        assertEquals(emptyList<ImportPlan.Skip>(), plan.skips)
        assertEquals(emptyList<ImportPlan.Deployment>(), plan.deployments)
        // An empty category file restores every stored key of the category (D8).
        assertEquals(3, plan(listOf(file("appearance")), "appearance").removes.size)
    }

    @Test
    fun categoriesThatAreNotSelectedAreNotTouched() {
        val timeVisible = ConsoleKeys.LOG_ACTIVITY_TIME_VISIBLE
        store.edit {
            put(AppearanceKeys.NIGHT_MODE_ENABLED.name, true)
            put(timeVisible.name, false)
        }
        val files = listOf(
            file("appearance", "default" to mapOf(AppearanceKeys.THEME_COLOR_PRIMARY.name to TypedValue("int", 9))),
            file("console"),
        )
        val plan = plan(files, "appearance")
        assertEquals(listOf("appearance"), plan.categories)
        assertTrue((plan.puts.map { it.category } + plan.removes.map { it.category }).all { it == "appearance" })
        assertTrue(plan.removes.none { it.name == timeVisible.name })
        // Data categories are left to their own codecs (T19), and a selected category needs its file.
        assertEquals(listOf("appearance"), plan(files, "appearance", "data_shortcuts").categories)
        assertThrows(IllegalArgumentException::class.java) { plan(files, "general") }
    }

    @Test
    fun entriesAreSkippedForTheFiveReasons() {
        val language = AppearanceKeys.APP_LANGUAGE
        val file = SettingsSnapshotCodec.Decoded(
            category(
                "appearance",
                "default" to linkedMapOf(
                    // Not declared at all, and declared by another category.
                    "future_key" to TypedValue("int", 1),
                    EditorKeys.ACE_FONT_LIGATURES_ENABLED.name to TypedValue("bool", true),
                    // A bool key with an int tag.
                    AppearanceKeys.NIGHT_MODE_ENABLED.name to TypedValue("int", 1),
                    // An Enum value out of the candidates.
                    language.name to TypedValue("string", "key_app_language_klingon"),
                ),
                "plugin_center_recent" to linkedMapOf("key_\$_last_install_time_\$_a.b" to TypedValue("long", 1L)),
            ),
            listOf(
                Rejection("default", AppearanceKeys.THEME_COLOR_ACCENT.name, "unknown type tag \"color\"", unknownTag = true),
                Rejection("default", AppearanceKeys.THEME_COLOR_PRIMARY.name, "not a int value"),
            ),
        )
        val plan = plan(listOf(file), "appearance")
        assertEquals(emptyList<ImportPlan.Put>(), plan.puts)
        assertEquals(
            mapOf(
                "future_key" to SkipReason.UNKNOWN_KEY,
                EditorKeys.ACE_FONT_LIGATURES_ENABLED.name to SkipReason.UNKNOWN_KEY,
                AppearanceKeys.NIGHT_MODE_ENABLED.name to SkipReason.TYPE_MISMATCH,
                language.name to SkipReason.INVALID_VALUE,
                // A state of `other` in the file of another category.
                "key_\$_last_install_time_\$_a.b" to SkipReason.UNKNOWN_KEY,
                AppearanceKeys.THEME_COLOR_ACCENT.name to SkipReason.NEWER_FORMAT,
                AppearanceKeys.THEME_COLOR_PRIMARY.name to SkipReason.INVALID_VALUE,
            ),
            plan.skips.associate { it.name to it.reason },
        )
        assertEquals("int, not bool", plan.skips.single { it.reason == SkipReason.TYPE_MISMATCH }.detail)
        // A category this version does not know is skipped as a whole.
        val newer = SettingsSnapshotCodec.Decoded(
            category("future_category", "default" to mapOf("a" to TypedValue("bool", true), "b" to TypedValue("int", 2))),
            listOf(Rejection("default", "c", "unknown type tag \"x\"", unknownTag = true)),
        )
        val newerPlan = plan(listOf(newer, file), "future_category", "appearance")
        assertEquals(listOf("appearance", "future_category"), newerPlan.categories)
        assertEquals(listOf("a", "b", "c"), newerPlan.skips.filter { it.category == "future_category" }.map { it.name })
        assertTrue(newerPlan.skips.filter { it.category == "future_category" }.all { it.reason == SkipReason.NEWER_FORMAT })
        // TARGET_MISSING is the subject of the plugin tests below; every reason is counted.
        val counts = newerPlan.skipCounts
        assertEquals(SkipReason.values().toList(), counts.keys.toList())
        assertEquals(listOf(3, 1, 2, 0, 4, 0), counts.values.toList())
    }

    @Test
    fun theEnableStateOfPluginsNotInstalledIsTargetMissing() {
        val enableStore = NamedStores.PLUGIN_CENTER_ENABLE_STATE
        val enabled = PluginCenterKeys.PLUGIN_ENABLED
        val suspended = PluginCenterKeys.PLUGIN_SUSPENDED
        store(enableStore).edit {
            put(enabled.nameOf("org.example.kept"), false)
            put(enabled.nameOf("org.example.gone"), false)
        }
        val file = file(
            "plugin_enable_state",
            enableStore.name to linkedMapOf(
                enabled.nameOf("org.example.installed") to TypedValue("bool", false),
                enabled.nameOf("org.example.missing") to TypedValue("bool", false),
                suspended.nameOf("org.example.missing") to TypedValue("bool", true),
                enabled.nameOf("org.example.kept") to TypedValue("bool", true),
            ),
        )
        val plan = SettingsImportPlanner.plan(listOf(file), listOf("plugin_enable_state"), ImportEnvironment(setOf("org.example.installed", "org.example.kept")))
        assertEquals(
            listOf(enabled.nameOf("org.example.installed") to false, enabled.nameOf("org.example.kept") to true),
            plan.puts.map { it.name to it.value },
        )
        assertTrue(plan.puts.all { it.store == enableStore })
        assertEquals(
            listOf(enabled.nameOf("org.example.missing"), suspended.nameOf("org.example.missing")),
            plan.skips.filter { it.reason == SkipReason.TARGET_MISSING }.map { it.name },
        )
        assertEquals("org.example.missing", plan.skips.first().detail)
        // A member the file does not have is removed, whether the plugin is installed or not.
        assertEquals(listOf(enabled.nameOf("org.example.gone")), plan.removes.map { it.name })
        // Nothing installed: every entry is skipped.
        assertEquals(4, plan(listOf(file), "plugin_enable_state").skipCounts[SkipReason.TARGET_MISSING])
    }

    @Test
    fun aCompilerPluginThatIsNotInstalledSkipsItsPair() {
        val dexComponent = "v1:11:9:org.example" + "a.b.Dexer"
        val r8Component = "r8-v1:9:7:org.other" + "a.b.R8x"
        val file = file(
            "developer",
            "default" to linkedMapOf(
                DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_ENABLED.name to TypedValue("bool", true),
                DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_COMPONENT.name to TypedValue("string", dexComponent),
                DeveloperKeys.R8_COMPILER_EXPLICIT_ENABLED.name to TypedValue("bool", true),
                DeveloperKeys.R8_COMPILER_EXPLICIT_COMPONENT.name to TypedValue("string", r8Component),
                DeveloperKeys.NEW_SCRIPT_MODULE_LAYER.name to TypedValue("bool", true),
            ),
        )
        val plan = SettingsImportPlanner.plan(listOf(file), listOf("developer"), ImportEnvironment(setOf("org.example")))
        assertEquals(
            listOf(DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_ENABLED.name, DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_COMPONENT.name, DeveloperKeys.NEW_SCRIPT_MODULE_LAYER.name),
            plan.puts.map { it.name },
        )
        assertEquals(
            listOf(DeveloperKeys.R8_COMPILER_EXPLICIT_ENABLED.name to "org.other", DeveloperKeys.R8_COMPILER_EXPLICIT_COMPONENT.name to "org.other"),
            plan.skips.map { it.name to it.detail },
        )
        assertTrue(plan.skips.all { it.reason == SkipReason.TARGET_MISSING })
        // A component that does not decode names no plugin: the app ignores it, the import writes it as it is.
        val unreadable = file("developer", "default" to mapOf(DeveloperKeys.R8_COMPILER_EXPLICIT_COMPONENT.name to TypedValue("string", "garbage")))
        assertEquals(1, plan(listOf(unreadable), "developer").puts.size)
    }

    @Test
    fun otherWritesTheRegisteredStatesOnly() {
        val regex = MiscStateKeys.FIND_OR_REPLACE_USING_REGEX
        val evaluated = MiscStateKeys.CODE_EVALUATE_CODE
        store.edit {
            put(evaluated.name, "log(1)")
            put("0f343b0931126a20f133d67c2b018a3b", true)
        }
        val file = file(
            "other",
            "default" to linkedMapOf(
                regex.name to TypedValue("bool", true),
                // The run-time fallback of P4.3 exports unregistered entries; they are not imported (D7).
                "0f343b0931126a20f133d67c2b018a3b" to TypedValue("bool", false),
                "future_state" to TypedValue("int", 3),
                // A cache is never imported.
                MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.name to TypedValue("int", 4),
            ),
        )
        val plan = plan(listOf(file), "other")
        assertEquals(listOf(ImportPlan.Put("other", StoreId.DEFAULT, regex.name, true, SettingEffect.NONE)), plan.puts)
        assertEquals(
            listOf("0f343b0931126a20f133d67c2b018a3b", "future_state", MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE.name),
            plan.skips.map { it.name },
        )
        assertTrue(plan.skips.all { it.reason == SkipReason.UNKNOWN_KEY })
        // A registered state the file does not have is removed; the unregistered entry of this device is left alone.
        assertEquals(listOf(evaluated.name), plan.removes.map { it.name })
    }

    @Test
    fun aWorkingDirectoryThatDoesNotExistIsADeployment() {
        val directory = ExplorerKeys.WORKING_DIRECTORY
        val file = file("explorer", "default" to mapOf(directory.name to TypedValue("string", "Scripts/AutoJs6")))
        val plan = plan(listOf(file), "explorer")
        assertEquals(emptyList<ImportPlan.Put>(), plan.puts)
        assertEquals(emptyList<ImportPlan.Skip>(), plan.skips)
        assertEquals(
            listOf(ImportPlan.Deployment(DeploymentKind.CREATE_DIRECTORY, "explorer", StoreId.DEFAULT, directory.name, "Scripts/AutoJs6", SettingEffect.NONE)),
            plan.deployments,
        )
        assertEquals(ImportPlan.Put("explorer", StoreId.DEFAULT, directory.name, "Scripts/AutoJs6", SettingEffect.NONE), plan.deployments.single().toPut())
        // An existing directory is written at once.
        val existing = ImportEnvironment { kind, value -> kind == DeploymentKind.CREATE_DIRECTORY && value == "Scripts/AutoJs6" }
        val direct = SettingsImportPlanner.plan(listOf(file), listOf("explorer"), existing)
        assertEquals(listOf(directory.name), direct.puts.map { it.name })
        assertEquals(emptyList<ImportPlan.Deployment>(), direct.deployments)
        // Undoing an import plans no deployment item (T9).
        val undo = SettingsImportPlanner.plan(listOf(file), listOf("explorer"), nothingInstalled, withDeployments = false)
        assertEquals(listOf(directory.name), undo.puts.map { it.name })
    }

    @Test
    fun aFontNotInstalledIsADeploymentAndGivingItUpIsDeclined() {
        val font = EditorKeys.ACE_EDITOR_FONT
        store.edit { put(font.name, "iosevka") }
        val file = file(
            "editor_appearance",
            "default" to linkedMapOf(font.name to TypedValue("string", "cascadia_code"), EditorKeys.ACE_FONT_LIGATURES_ENABLED.name to TypedValue("bool", false)),
        )
        val plan = plan(listOf(file), "editor_appearance")
        val deployment = plan.deployments.single()
        assertEquals(DeploymentKind.DOWNLOAD_EDITOR_FONT, deployment.kind)
        assertEquals("cascadia_code", deployment.value)
        assertEquals(setOf(Prerequisite.ACE_EDITOR_PLUGIN), deployment.prerequisites)
        assertEquals(listOf(EditorKeys.ACE_FONT_LIGATURES_ENABLED.name), plan.puts.map { it.name })
        // The key is in the file: it is not removed while the deployment waits.
        assertTrue(plan.removes.none { it.name == font.name })
        val declined = plan.decline(listOf(deployment))
        assertEquals(emptyList<ImportPlan.Deployment>(), declined.deployments)
        assertEquals(listOf(ImportPlan.Skip("editor_appearance", "default", font.name, SkipReason.DECLINED)), declined.skips)
        assertEquals(1, declined.skipCounts[SkipReason.DECLINED])
        assertEquals(plan.puts, declined.puts)
        assertSame(declined, declined.decline(listOf(deployment)))
        // With the Ace editor plugin installed there is no prerequisite; with the font installed, no deployment.
        val withPlugin = SettingsImportPlanner.plan(listOf(file), listOf("editor_appearance"), ImportEnvironment(aceEditorPluginInstalled = true))
        assertEquals(emptySet<Prerequisite>(), withPlugin.deployments.single().prerequisites)
        val installed = SettingsImportPlanner.plan(listOf(file), listOf("editor_appearance"), ImportEnvironment(aceEditorPluginInstalled = true) { kind, value ->
            kind == DeploymentKind.DOWNLOAD_EDITOR_FONT && value == "cascadia_code"
        })
        assertEquals(listOf(font.name, EditorKeys.ACE_FONT_LIGATURES_ENABLED.name), installed.puts.map { it.name })
    }

    @Test
    fun valuesAreWrittenInTheStoredFormOfTheirDeclarations() {
        val stove = ExtensibilityKeys.THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION
        val nightMode = AppearanceKeys.NIGHT_MODE
        val files = listOf(
            file("extensibility", "default" to mapOf(stove.name to TypedValue("json", "{\"root\":\"/sdcard/x\",\"n\":1}"))),
            file("appearance", "default" to mapOf(nightMode.name to TypedValue("string", key("key_night_mode_always_on")))),
        )
        val plan = plan(files, "extensibility", "appearance")
        assertEquals(listOf("appearance", "extensibility"), plan.categories)
        assertEquals(key("key_night_mode_always_on"), plan.puts.single { it.name == nightMode.name }.value)
        assertEquals("{\"root\":\"/sdcard/x\",\"n\":1}", plan.puts.single { it.name == stove.name }.value)
        assertEquals(listOf(StoreId.DEFAULT), plan.stores.toList())
        // A Json key refuses text its codec does not read; an object codec refuses an array.
        val refused = plan(listOf(file("extensibility", "default" to mapOf(stove.name to TypedValue("json", "[1]")))), "extensibility")
        assertEquals(SkipReason.INVALID_VALUE, refused.skips.single().reason)
        // A json declaration exported with a string tag (its text was no JSON document, P6.1) is a type mismatch.
        val text = plan(listOf(file("extensibility", "default" to mapOf(stove.name to TypedValue("string", "{root")))), "extensibility")
        assertEquals(SkipReason.TYPE_MISMATCH, text.skips.single().reason)
    }

    private fun plan(files: List<SettingsSnapshotCodec.Decoded>, vararg selected: String): ImportPlan =
        SettingsImportPlanner.plan(files, selected.toList(), nothingInstalled)

    private fun file(id: String, vararg stores: Pair<String, Map<String, TypedValue>>) = SettingsSnapshotCodec.Decoded(category(id, *stores), emptyList())

    private fun category(id: String, vararg stores: Pair<String, Map<String, TypedValue>>) = SettingsSnapshot.Category(id, linkedMapOf(*stores))
}
