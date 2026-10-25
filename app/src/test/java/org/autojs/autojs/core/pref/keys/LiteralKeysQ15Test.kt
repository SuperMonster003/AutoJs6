package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogConfiguration
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentConnectionOwner
import org.autojs.autojs.core.plugin.dex.DexCompilerComponent
import org.autojs.autojs.core.plugin.dex.DexCompilerComponentCodec
import org.autojs.autojs.core.plugin.dex.DexCompilerOptInOptions
import org.autojs.autojs.core.plugin.dex.DexCompilerOptInStore
import org.autojs.autojs.core.plugin.r8.R8CompilerComponent
import org.autojs.autojs.core.plugin.r8.R8CompilerComponentCodec
import org.autojs.autojs.core.plugin.r8.R8CompilerOptInStore
import org.autojs.autojs.core.plugin.r8.R8CompilerSelectionOptions
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingEffect
import org.autojs.autojs.core.pref.SettingKind
import org.autojs.autojs.core.pref.SettingsRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/settings/ROADMAP.md P3.6 (appendix C Q15): the default preference keys whose names do not start with `key_`,
 * declared in [AppearanceKeys], [ExtensibilityKeys], [DeveloperKeys] and [LayoutInspectorKeys] with their names
 * unchanged (R1), and the readers moved to them (R2).
 *
 * zh-CN: ROADMAP P3.6 (附录 C Q15): 名称不以 `key_` 开头的默认偏好键, 在四个领域对象中声明, 名称不变 (R1), 读取方改走键 (R2).
 */
class LiteralKeysQ15Test : DomainKeysTestBase() {

    @Test
    fun namesCategoriesAndDefaults() {
        val expected = listOf(
            Triple(AppearanceKeys.THEME_COLOR_DERIVED_ACCENT_AND_DARK, "THEME_COLOR_DERIVED_ACCENT_AND_DARK", SettingCategory.APPEARANCE),
            Triple(AppearanceKeys.COLOR_HISTORY_MERGE_SIMILAR, "COLOR_HISTORY_MERGE_SIMILAR", SettingCategory.APPEARANCE),
            Triple(ExtensibilityKeys.THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION, "three_stove_agent_script_root_configuration", SettingCategory.EXTENSIBILITY),
            Triple(DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_ENABLED, "dex_compiler_experimental_enabled", SettingCategory.DEVELOPER),
            Triple(DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_COMPONENT, "dex_compiler_experimental_component", SettingCategory.DEVELOPER),
            Triple(DeveloperKeys.R8_COMPILER_EXPLICIT_ENABLED, "r8_compiler_explicit_enabled", SettingCategory.DEVELOPER),
            Triple(DeveloperKeys.R8_COMPILER_EXPLICIT_COMPONENT, "r8_compiler_explicit_component", SettingCategory.DEVELOPER),
        )
        expected.forEach { (key, name, category) ->
            assertEquals(name, key.name)
            assertEquals(name, category, key.category)
            assertEquals(name, SettingKind.SETTING, key.kind)
            assertEquals(name, SettingEffect.NONE, key.effect)
            assertSame(name, key, SettingsRegistry.find(name))
        }
        assertFalse(AppearanceKeys.THEME_COLOR_DERIVED_ACCENT_AND_DARK.get())
        assertFalse(AppearanceKeys.COLOR_HISTORY_MERGE_SIMILAR.get())
        assertEquals(emptyMap<String, Any?>(), ExtensibilityKeys.THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION.get())
        assertEquals("{}", ExtensibilityKeys.THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION.defaultText)
        assertNull(DeveloperKeys.DEX_COMPILER_EXPERIMENTAL_COMPONENT.getOrNull())
        assertNull(DeveloperKeys.R8_COMPILER_EXPLICIT_COMPONENT.getOrNull())
        // P4.2 added COLOR_FAVORITE_SORT (Q18), the memory roadmap P5.1 added key_memory_script_heap_dump, and
        // docs/augment/ROADMAP.md P5.2 removed key_$_new_script_module_layer.
        assertEquals(12, AppearanceKeys.keys.size)
        assertEquals(3, ExtensibilityKeys.keys.size)
        assertEquals(10, DeveloperKeys.keys.size)
    }

    @Test
    fun stoveRootConfigurationKeepsItsText() {
        assertEquals("three_stove_agent_script_root_configuration", ThreeStoveAgentConnectionOwner.ROOT_CONFIGURATION_KEY)
        assertEquals("{}", ExtensibilityKeys.threeStoveAgentScriptRootConfigurationText())
        // The owner saves the normalized text as it is and reads it back as it is (Pref.getString / putString).
        val text = "{\"roots\":[{\"path\":\"/sdcard/Scripts\"}]}"
        ExtensibilityKeys.setThreeStoveAgentScriptRootConfigurationText(text)
        assertEquals(text, store.getString("three_stove_agent_script_root_configuration", null))
        assertEquals(text, ExtensibilityKeys.threeStoveAgentScriptRootConfigurationText())
        assertEquals(listOf(mapOf("path" to "/sdcard/Scripts")), ExtensibilityKeys.THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION.get()["roots"])
        // Malformed text is still returned as it is; the owner falls back to "{}" itself.
        store.put("three_stove_agent_script_root_configuration", "{oops")
        assertEquals("{oops", ExtensibilityKeys.threeStoveAgentScriptRootConfigurationText())
        assertEquals(emptyMap<String, Any?>(), ExtensibilityKeys.THREE_STOVE_AGENT_SCRIPT_ROOT_CONFIGURATION.get())
    }

    @Test
    fun dexCompilerSelectionRoundTrip() {
        assertEquals(DexCompilerOptInOptions(), DexCompilerOptInStore.read())
        val component = DexCompilerComponent("example.dex.compiler", "example.dex.compiler.Service")
        val commits = store.commitCount
        assertEquals(DexCompilerOptInOptions(enabled = true, selectedComponent = component), DexCompilerOptInStore.select(component))
        // Component and switch are one write, as the single SharedPreferences transaction before.
        assertEquals(commits + 1, store.commitCount)
        assertEquals(DexCompilerComponentCodec.encode(component), store.getString("dex_compiler_experimental_component", null))
        assertTrue(store.getBoolean("dex_compiler_experimental_enabled", false))
        assertEquals(DexCompilerOptInOptions(enabled = true, selectedComponent = component), DexCompilerOptInStore.read())
        // Turning it off keeps the component.
        assertEquals(DexCompilerOptInOptions(enabled = false, selectedComponent = component), DexCompilerOptInStore.disable())
        // A switch without a valid component reads as off, and values of another type read as missing, as before.
        store.put("dex_compiler_experimental_component", "v2:broken")
        store.put("dex_compiler_experimental_enabled", true)
        assertEquals(DexCompilerOptInOptions(), DexCompilerOptInStore.read())
        store.put("dex_compiler_experimental_component", 1)
        store.put("dex_compiler_experimental_enabled", "true")
        assertEquals(DexCompilerOptInOptions(), DexCompilerOptInStore.read())
    }

    @Test
    fun r8CompilerSelectionRoundTrip() {
        assertEquals(R8CompilerSelectionOptions(), R8CompilerOptInStore.read())
        val component = R8CompilerComponent("example.r8.compiler", "example.r8.compiler.Service")
        val commits = store.commitCount
        assertEquals(R8CompilerSelectionOptions(true, component), R8CompilerOptInStore.select(component))
        assertEquals(commits + 1, store.commitCount)
        assertEquals(R8CompilerComponentCodec.encode(component), store.getString("r8_compiler_explicit_component", null))
        assertEquals(R8CompilerSelectionOptions(true, component), R8CompilerOptInStore.read())
        assertEquals(R8CompilerSelectionOptions(false, component), R8CompilerOptInStore.disable())
        store.put("r8_compiler_explicit_enabled", 1)
        assertEquals(R8CompilerSelectionOptions(false, component), R8CompilerOptInStore.read())
    }

    @Test
    fun debugCodegenSelectionIsAFamilyOfTheCurrentModelKey() {
        val family = LayoutInspectorKeys.CODEGEN_MODEL_CURRENT_DEBUG
        assertEquals(LayoutInspectorKeys.CODEGEN_MODEL_CURRENT.name + "_debug_", family.prefix)
        assertEquals("key_\$_codegen_model_current_debug_", family.prefix)
        assertEquals(SettingCategory.LAYOUT_INSPECTOR, family.category)
        assertTrue(family in SettingsRegistry.families)
        val debug = ModelCatalogConfiguration("example/models", "debug-key", "0".repeat(64), "catalog-debug-p1")
        val name = debug.selectionPreferenceKey(LayoutInspectorKeys.CODEGEN_MODEL_CURRENT.name)
        assertEquals("key_\$_codegen_model_current_debug_p1", name)
        assertEquals("p1", family.suffixOf(name))
        assertSame(family, SettingsRegistry.findFamily(name))
        family.key("p1").set("model-a")
        assertEquals("model-a", store.getString(name, null))
        assertEquals(listOf(name), family.storedNames())
        val row = SettingsRegistry.describe().single { it.name == family.prefix }
        assertTrue(row.family)
        assertEquals("layout_inspector", row.category)
    }
}
