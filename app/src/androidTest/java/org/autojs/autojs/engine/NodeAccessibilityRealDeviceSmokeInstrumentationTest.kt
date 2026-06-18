package org.autojs.autojs.engine

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.accessibility.AccessibilityService as AutoJsAccessibilityService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeAccessibilityRealDeviceSmokeInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val targetContext = instrumentation.targetContext
    private val testContext = instrumentation.context
    private var activity: NodeAccessibilitySmokeActivity? = null

    @After
    fun tearDown() {
        activity?.let { launched ->
            instrumentation.runOnMainSync {
                launched.finish()
            }
        }
        activity = null
    }

    @Test
    fun providerFindsAndClicksControlledUiWhenAccessibilityServiceIsEnabled() {
        assumeTrue(
            "Run with -Pautojs.nodejs.test.deviceProfile=accessibility, or pass " +
                    "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.accessibilitySmoke=true for focused runs.",
            accessibilitySmokeEnabled(),
        )
        assumeTrue(
            "Enable the AutoJs6 Node accessibility service on the Sony test device before running this optional smoke.",
            AutoJsAccessibilityService.waitForOperational(3_000L),
        )

        launchSmokeActivity()

        val provider = AndroidNodeBridgeCapabilityProvider(targetContext)
        val enabled = provider.dispatchAccessibilityRequest("isEnabled")
        assertTrue(enabled.error?.message.orEmpty(), enabled.ok)
        assertEquals("true", enabled.resultJson)

        val ensured = provider.dispatchAccessibilityRequest(
            method = "ensureEnabled",
            argsJson = """[{"prompt":false}]""",
        )
        assertTrue(ensured.error?.message.orEmpty(), ensured.ok)
        assertEquals("true", ensured.resultJson)

        val found = provider.dispatchAccessibilityRequest(
            method = "findByText",
            argsJson = """["${NodeAccessibilitySmokeActivity.TARGET_TEXT}",{"timeoutMs":1000}]""",
        )
        assertTrue(found.error?.message.orEmpty(), found.ok)
        val node = JSONObject(found.resultJson ?: "null")
        assertEquals(NodeAccessibilitySmokeActivity.TARGET_TEXT, node.getString("text"))
        assertEquals(NodeAccessibilitySmokeActivity.TARGET_DESC, node.getString("desc"))
        assertEquals("android.widget.TextView", node.getString("className"))
        assertNotNull(node.getJSONObject("bounds"))

        val selectorFound = provider.dispatchAccessibilityRequest(
            method = "findOne",
            argsJson = JSONArray()
                .put(selector(NodeAccessibilitySmokeActivity.TARGET_TEXT))
                .put(JSONObject().put("timeoutMs", 1000))
                .toString(),
        )
        assertTrue(selectorFound.error?.message.orEmpty(), selectorFound.ok)
        val selectorNode = JSONObject(selectorFound.resultJson ?: "null")
        assertEquals(NodeAccessibilitySmokeActivity.TARGET_TEXT, selectorNode.getString("text"))
        assertEquals(NodeAccessibilitySmokeActivity.TARGET_DESC, selectorNode.getString("desc"))
        assertNotNull(selectorNode.getJSONObject("bounds"))

        val clicked = provider.dispatchAccessibilityRequest(
            method = "click",
            argsJson = JSONArray()
                .put(selector(NodeAccessibilitySmokeActivity.BUTTON_TEXT))
                .put(JSONObject().put("timeoutMs", 1000))
                .toString(),
        )
        assertTrue(clicked.error?.message.orEmpty(), clicked.ok)
        assertEquals("true", clicked.resultJson)
        assertTrue(
            "Timed out waiting for controlled accessibility smoke button click",
            NodeAccessibilitySmokeActivity.awaitClick(2_000L),
        )
        assertEquals(1, NodeAccessibilitySmokeActivity.clickedCount())

        val clickedText = provider.dispatchAccessibilityRequest(
            method = "findByText",
            argsJson = """["${NodeAccessibilitySmokeActivity.CLICKED_TEXT}",{"timeoutMs":1000}]""",
        )
        assertTrue(clickedText.error?.message.orEmpty(), clickedText.ok)
        assertEquals(NodeAccessibilitySmokeActivity.CLICKED_TEXT, JSONObject(clickedText.resultJson ?: "null").getString("text"))
        assertEquals(NodeAccessibilitySmokeActivity.TARGET_TEXT, selectorNode.getString("text"))

        if (accessibilityGlobalActionsEnabled()) {
            val back = provider.dispatchAccessibilityRequest("back")
            assertTrue(back.error?.message.orEmpty(), back.ok)
            assertEquals("true", back.resultJson)

            launchSmokeActivity()
            val home = provider.dispatchAccessibilityRequest("home")
            assertTrue(home.error?.message.orEmpty(), home.ok)
            assertEquals("true", home.resultJson)
        }
    }

    private fun selector(text: String): JSONObject {
        return JSONObject()
            .put("type", "autojs6.accessibility.selector")
            .put("version", 1)
            .put("text", text)
    }

    private fun launchSmokeActivity() {
        activity?.let { launched ->
            instrumentation.runOnMainSync { launched.finish() }
        }
        NodeAccessibilitySmokeActivity.reset()
        activity = instrumentation.startActivitySync(
            Intent(testContext, NodeAccessibilitySmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as NodeAccessibilitySmokeActivity
        instrumentation.waitForIdleSync()
        Thread.sleep(500L)
    }

    private fun AndroidNodeBridgeCapabilityProvider.dispatchAccessibilityRequest(
        method: String,
        argsJson: String = "[]",
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        dispatch(
            NodeBridgeRequest(
                id = "a11y-smoke-$method",
                module = "accessibility",
                method = method,
                argsJson = argsJson,
                timeoutMs = 2_000L,
                permissions = listOf("accessibility"),
            ),
        ) { result ->
            response = result
            latch.countDown()
        }
        assertTrue("Timed out waiting for accessibility $method response", latch.await(2, TimeUnit.SECONDS))
        return checkNotNull(response)
    }

    private fun accessibilitySmokeEnabled(): Boolean {
        return arguments.getString("autojs.nodejs.test.deviceProfile") == "accessibility" ||
                arguments.getString("autojs.nodejs.accessibilitySmoke").toBooleanFlag()
    }

    private fun accessibilityGlobalActionsEnabled(): Boolean {
        return arguments.getString("autojs.nodejs.accessibilitySmoke.globalActions").toBooleanFlag()
    }

    private fun String?.toBooleanFlag(): Boolean {
        return equals("true", ignoreCase = true) || equals("yes", ignoreCase = true) || this == "1"
    }
}
