package org.autojs.autojs.core.plugin.scriptmodule

import org.autojs.autojs.core.plugin.scriptmodule.ScriptModulePluginState.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Roadmap P4.3 (design P4-3 (d)): the guidance messages in 10 languages (`strings_script_module_plugins.xml`) have the
 * same names and placeholders, use ASCII punctuation, and the English fallback of the JVM equals the default resources.
 * zh-CN: ROADMAP P4.3 (设计 P4-3 (d)): 10 种语言的指引消息名称与占位符一致, 只用 ASCII 标点, JVM 上的英文回退与默认资源一致.
 */
class ScriptModulePluginMessagesTest {

    private fun read(directory: File): Map<String, String> {
        val text = File(directory, FILE).readText()
        return ENTRY.findAll(text).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
    }

    private fun unescape(value: String) = value.replace("\\'", "'").replace("\\\"", "\"").replace("&lt;", "<").replace("&amp;", "&")

    @Test
    fun everyLanguageHasTheSameMessages() {
        val directories = File(RES).listFiles { file -> file.isDirectory && File(file, FILE).isFile }!!.sortedBy { it.name }
        assertEquals(
            listOf("values", "values-ar", "values-en", "values-es", "values-fr", "values-ja", "values-ko", "values-ru", "values-zh", "values-zh-rHK", "values-zh-rTW"),
            directories.map { it.name },
        )
        val default = read(File(RES, "values"))
        assertEquals(default.keys.sorted(), default.keys.toList())
        directories.forEach { directory ->
            val messages = read(directory)
            assertEquals(directory.name, default.keys, messages.keys)
            messages.forEach { (name, text) ->
                assertEquals("${directory.name} $name", PLACEHOLDER.findAll(default.getValue(name)).map { it.value }.toSet(), PLACEHOLDER.findAll(text).map { it.value }.toSet())
                val bad = text.filter { it.code > 0x7F && (Character.getType(it).let { type -> type in PUNCTUATION_TYPES } || it.code in 0x3000..0x303F || it.code in 0xFF00..0xFFEF) }
                assertTrue("${directory.name} $name: $bad", bad.isEmpty())
            }
        }
        assertEquals(default, read(File(RES, "values-en")))
    }

    @Test
    fun fallbackEqualsTheDefaultResources() {
        val default = read(File(RES, "values"))
        assertEquals(default["error_script_module_plugin_not_installed"], ScriptModulePluginMessages.NOT_INSTALLED)
        assertEquals(default["error_script_module_plugin_not_authorized"], ScriptModulePluginMessages.UNAUTHORIZED)
        assertEquals(default["error_script_module_plugin_disabled"], ScriptModulePluginMessages.PLUGIN_DISABLED)
        assertEquals(default["error_script_module_plugin_library_disabled"], ScriptModulePluginMessages.LIBRARY_DISABLED)
        assertEquals(default["error_script_module_plugin_incompatible"], ScriptModulePluginMessages.INCOMPATIBLE)
        assertEquals(default["error_script_module_packaged_library_disabled"], ScriptModulePluginMessages.PACKAGED_LIBRARY_DISABLED)
        assertEquals(default["error_script_module_packaged_not_included"], ScriptModulePluginMessages.PACKAGED_NOT_INCLUDED)
        assertEquals(
            "Cannot find module 'banana-i18n': the library \"i18n\" is switched off in the plugin \"Bundled Libraries\". Switch it on in AutoJs6 > Plugin center > Bundled Libraries.",
            ScriptModulePluginMessages.unavailable("banana-i18n", "i18n", Reason.LIBRARY_DISABLED, packaged = false),
        )
    }

    /** Roadmap P7.1 (Q188): a packaged app has no Plugin center, so its messages speak to the developer. */
    @Test
    fun packagedAppsGetTwoMessages() {
        assertEquals(
            "Cannot find module 'dayjs': this app was packaged without the library \"dayjs\". Package the app again with AutoJs6 (select \"Bundled Libraries\" in \"Libraries\" if the module is loaded dynamically), or install the plugin \"Bundled Libraries\" on this device.",
            ScriptModulePluginMessages.unavailable("dayjs", "dayjs", Reason.NOT_INSTALLED, packaged = true),
        )
        listOf(Reason.UNAUTHORIZED, Reason.PLUGIN_DISABLED, Reason.INCOMPATIBLE).forEach { reason ->
            assertEquals(
                reason.code,
                ScriptModulePluginMessages.unavailable("dayjs", "dayjs", Reason.NOT_INSTALLED, packaged = true),
                ScriptModulePluginMessages.unavailable("dayjs", "dayjs", reason, packaged = true),
            )
        }
        assertEquals(
            "Cannot find module 'lodash': this app was packaged without the library \"lodash\", and the library is switched off in the plugin \"Bundled Libraries\" on this device. Package the app again with AutoJs6, or switch the library on in the plugin.",
            ScriptModulePluginMessages.unavailable("lodash", "lodash", Reason.LIBRARY_DISABLED, packaged = true),
        )
    }

    companion object {
        const val RES = "src/main/res"
        const val FILE = "strings_script_module_plugins.xml"
        val ENTRY = Regex("""<string name="([a-z_]+)">(.*?)</string>""")
        val PLACEHOLDER = Regex("""%\d[$]s""")
        val PUNCTUATION_TYPES = setOf(
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION,
        ).map { it.toInt() }.toSet()
    }

}
