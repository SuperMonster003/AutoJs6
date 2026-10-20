package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Roadmap P1.3: the rules behind the developer options of the new script module layer (appendix C Q4) and the
 * entries of `fragment_developer_options.xml`.
 */
class ScriptModuleDebugSettingsTest {

    @Test
    fun modulesComeFromTheHostRegistry() {
        assertEquals(ScriptModules.moduleNames, ScriptModuleDebugSettings.moduleNames)
        // The host registry grows with every migrated module (P2-P4).
        assertTrue("base64" in ScriptModuleDebugSettings.moduleNames)
    }

    @Test
    fun printingNeedsTheMasterSwitchAndAnUnexcludedModule() {
        assertFalse(ScriptModuleDebugSettings.prints("base64", masterEnabled = false, excluded = emptySet()))
        assertTrue(ScriptModuleDebugSettings.prints("base64", masterEnabled = true, excluded = emptySet()))
        assertFalse(ScriptModuleDebugSettings.prints("base64", masterEnabled = true, excluded = setOf("base64")))
        assertTrue(ScriptModuleDebugSettings.prints("util", masterEnabled = true, excluded = setOf("base64")))
    }

    @Test
    fun theDialogStoresTheUncheckedModules() {
        val all = listOf("base64", "mime", "util")
        assertEquals(all, ScriptModuleDebugSettings.printingModules(all, emptySet()))
        assertEquals(listOf("base64", "util"), ScriptModuleDebugSettings.printingModules(all, setOf("mime", "gone")))
        assertEquals(setOf("mime"), ScriptModuleDebugSettings.excludedAfter(all, listOf("base64", "util"), emptySet()))
        assertEquals(emptySet<String>(), ScriptModuleDebugSettings.excludedAfter(all, all, setOf("util")))
        // Entries for modules that are not listed (any more) are kept as they are.
        assertEquals(setOf("gone", "base64", "mime", "util"), ScriptModuleDebugSettings.excludedAfter(all, emptyList(), setOf("gone")))
    }

    @Test
    fun developerOptionsListTheThreeEntries() {
        val xml = File("src/main/res/xml/fragment_developer_options.xml").readText()
        listOf(
            "app:key=\"@string/key_new_script_module_layer\"",
            "app:key=\"@string/key_script_module_debug_print\"",
            "app:key=\"@string/key_script_module_debug_print_disabled_modules\"",
            "app:dependency=\"@string/key_script_module_debug_print\"",
            "org.autojs.autojs.ui.settings.ScriptModuleDebugPrintModulesPreference",
        ).forEach { assertTrue("missing $it", it in xml) }
        val keys = File("src/main/res/values/strings_donottranslate.xml").readText()
        listOf("key_\$_new_script_module_layer", "key_\$_script_module_debug_print", "key_\$_script_module_debug_print_disabled_modules").forEach {
            assertTrue("missing key value $it", ">$it<" in keys)
        }
    }

}
