package org.autojs.autojs.engine

import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.AiProviderComponent
import org.autojs.autojs.core.plugin.ai.ThreeStoneAiOfficialPlugin
import org.autojs.autojs.core.plugin.bun.BunHostCapabilityGrants as Grants
import org.autojs.autojs.runtime.api.ai.AiPluginAskInputMapper
import org.junit.Assert.*
import org.junit.Test

class RuntimeAiArgumentsTest {
    private fun args(value: String) = JsonParser.parseString(value).asJsonArray

    @Test fun inputFormsPreserveMessagesAndOverrideWithoutMutatingCallers() {
        for (input in listOf("\"hello\"", "{\"role\":\"user\",\"content\":\"hello\"}",
            "[{\"role\":\"user\",\"content\":\"hello\"}]")) {
            val value = RuntimeAiArguments.request(args("[$input]"))
            assertEquals("hello", AiPluginAskInputMapper.map(value, 120000).messages.single().content)
        }
        val original = args("[{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}],\"temperature\":0.2},{\"temperature\":0.7}]")
        val result = RuntimeAiArguments.request(original)
        assertEquals(0.7, result.options.get("temperature").asDouble, 0.0)
        assertEquals(0.2, original[0].asJsonObject.get("temperature").asDouble, 0.0)
    }

    @Test fun sessionSystemAndGenerationControlsUseTheExistingMapper() {
        val request = RuntimeAiArguments.session(args("[{\"system\":\"answer briefly\",\"maxTokens\":8,\"backend\":\"cpu\"}]"))
        val mapped = AiPluginAskInputMapper.map(request, 120000)
        assertEquals(2, mapped.messages.size)
        assertEquals("answer briefly", mapped.messages.first().content)
        assertEquals(8L, mapped.maximumOutputTokens)
        assertEquals("cpu", mapped.backendProfile)
        assertFalse(request.options.has("system"))
    }

    @Test fun malformedInputsAndUnknownProviderOptionsAreRejected() {
        for (value in listOf("[]", "[null]", "[[]]", "[{}]", "[42]", "[\"a\",[]]", "[{\"messages\":42}]")) {
            assertThrows(IllegalArgumentException::class.java) { RuntimeAiArguments.request(args(value)) }
        }
        assertThrows(IllegalArgumentException::class.java) { RuntimeAiArguments.session(args("[{\"system\":null}]")) }
        assertThrows(IllegalArgumentException::class.java) {
            AiPluginAskInputMapper.map(RuntimeAiArguments.request(args("[\"a\",{\"apiKey\":\"synthetic\"}]")), 120000)
        }
    }

    @Test fun aiGrantIsIndependentAndPackagedPermissionDoesNotWeakenOtherProviders() {
        assertEquals(listOf("ai"), BunAutomationBridge.nodeCapabilities(listOf(Grants.AI_CONTROL)))
        assertEquals(listOf("ai.control"), Grants.validatePackagedCapabilities(listOf("ai.control")))
        assertEquals(listOf(ThreeStoneAiOfficialPlugin.RUNTIME_PERMISSION),
            NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("ai")).androidPermissions)
        for (method in RuntimeAiBridge.methods) {
            assertTrue(NodeBridgeModules.supports("ai", method))
            assertEquals(listOf("ai"), NodeBridgePermissionManifest.requiredCapabilitiesFor("ai", method))
        }
        val official = AiProviderComponent(ThreeStoneAiOfficialPlugin.PACKAGE_NAME, ThreeStoneAiOfficialPlugin.RUNTIME_CLASS_NAME)
        assertTrue(ThreeStoneAiOfficialPlugin.acceptsPermission(official, ThreeStoneAiOfficialPlugin.RUNTIME_PERMISSION))
        assertFalse(ThreeStoneAiOfficialPlugin.acceptsPermission(official, null))
        assertFalse(ThreeStoneAiOfficialPlugin.acceptsPermission(AiProviderComponent("example.other", "example.other.Service"),
            ThreeStoneAiOfficialPlugin.RUNTIME_PERMISSION))
    }
}
