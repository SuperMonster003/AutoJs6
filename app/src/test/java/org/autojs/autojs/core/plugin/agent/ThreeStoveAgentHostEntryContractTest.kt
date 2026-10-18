package org.autojs.autojs.core.plugin.agent

import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ThreeStoveAgentHostEntryContractTest {
    private val main = sequenceOf(File("src/main"), File("app/src/main"), File("../app/src/main"))
        .first { File(it, "AndroidManifest.xml").isFile }
    private fun parse(file: File) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(file)
    private fun Element.android(name: String) = getAttributeNS("http://schemas.android.com/apk/res/android", name)

    @Test fun exportedAttachReceiverRequiresPluginPermissionAndCannotReceiveBoot() {
        val document = parse(File(main, "AndroidManifest.xml"))
        val receivers = document.getElementsByTagName("receiver")
        val receiver = (0 until receivers.length).map { receivers.item(it) as Element }
            .single { it.android("name").endsWith(".ThreeStoveAgentAttachRequestReceiver") }
        assertEquals("true", receiver.android("exported"))
        assertEquals(ThreeStoveAgentActions.PLUGIN_PERMISSION, receiver.android("permission"))
        val actions = receiver.getElementsByTagName("action")
        assertEquals(listOf(ThreeStoveAgentActions.ACTION_ATTACH_REQUEST),
            (0 until actions.length).map { (actions.item(it) as Element).android("name") })
        val queries = document.getElementsByTagName("queries").item(0) as Element
        val intents = queries.getElementsByTagName("intent")
        assertTrue((0 until intents.length).map { intents.item(it) as Element }.any { intent ->
            val action = intent.getElementsByTagName("action").item(0) as? Element
            val category = intent.getElementsByTagName("category").item(0) as? Element
            action?.android("name") == ThreeStoveAgentActions.SERVICE_ACTION && category?.android("name") == ThreeStoveAgentActions.SERVICE_CATEGORY
        })
    }

    @Test fun allLocalesCoverAgentPromptsAndPreservePlaceholders() {
        val locales = listOf("values", "values-en", "values-ar", "values-es", "values-fr", "values-ja", "values-ko",
            "values-ru", "values-zh", "values-zh-rHK", "values-zh-rTW")
        val translated = locales.associateWith { locale ->
            val nodes = parse(File(main, "res/$locale/strings.xml")).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) as Element }
                .filter { it.getAttribute("name").contains("three_stove_agent") }
                .associate { it.getAttribute("name") to it.textContent }
        }
        val english = translated.getValue("values")
        // Only the task page and signer prompts remain: unavailable states open Plugin Center instead.
        assertEquals(setOf("prompt_three_stove_agent_launcher_unavailable", "prompt_three_stove_agent_trust"), english.keys)
        assertEquals(english, translated.getValue("values-en"))
        translated.forEach { (locale, values) ->
            assertEquals(locale, english.keys, values.keys)
            values.forEach { (key, value) ->
                assertEquals("$locale/$key", Regex("%[1-9]\\$[ds]").findAll(english.getValue(key)).map { it.value }.toList(),
                    Regex("%[1-9]\\$[ds]").findAll(value).map { it.value }.toList())
                assertTrue("$locale/$key", value.isNotBlank())
                assertFalse("$locale/$key uses the display name 3-Stove Agent", value.contains("Three Stove Agent"))
            }
        }
    }
}
