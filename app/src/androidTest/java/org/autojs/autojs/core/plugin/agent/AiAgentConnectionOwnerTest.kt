package org.autojs.autojs.core.plugin.agent

import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.pref.Pref
import org.autojs.plugin.ai.agent.api.IAiAgentLink
import org.autojs.plugin.ai.agent.api.IAiAgentRunCallback
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the process owner with a real controller and local Binder link, without a task. */
class AiAgentConnectionOwnerTest {
    @Test fun defaultClosedDoesNotBindAndConcurrentConnectsShareOneLink() = fixture { f ->
        f.owner.isNormallyClosed = true
        assertEquals(AiAgentUiState.DETACHED, f.owner.connect(automatic = true).await().state)
        assertEquals(0, f.attaches.get())
        val first = f.owner.connect()
        val second = f.owner.connect()
        assertSame(first, second)
        assertEquals(AiAgentUiState.ATTACHED, first.await().state)
        withTimeout(2000) { f.owner.state.first { it.state == AiAgentUiState.ATTACHED } }
        assertEquals(1, f.attaches.get())
        assertEquals(0, f.runs.get())
        assertFalse(f.owner.isNormallyClosed)
    }

    @Test fun rememberedConnectionRestoresOnlyTheLinkAndDisconnectReleasesIt() = fixture { f ->
        f.owner.isNormallyClosed = false
        f.owner.restoreIfWanted()
        withTimeout(2000) { f.owner.state.first { it.state == AiAgentUiState.ATTACHED } }
        assertEquals(1, f.attaches.get())
        assertEquals(0, f.runs.get())
        f.owner.disconnect()
        withTimeout(2000) { f.owner.state.first { it.state == AiAgentUiState.DETACHED } }
        assertTrue(f.owner.isNormallyClosed)
        assertEquals(1, f.closed.get())
    }

    @Test fun disabledPluginRevokesTheActiveLinkOnStateRefresh() = fixture { f ->
        f.owner.connect().await()
        f.inspection = AiAgentInspection(AiAgentUiState.PLUGIN_DISABLED)
        f.owner.refresh()
        withTimeout(2000) { f.owner.state.first { it.state == AiAgentUiState.PLUGIN_DISABLED } }
        assertEquals("detached", f.controller.status.value.state)
        assertEquals(1, f.closed.get())
        assertTrue(f.owner.isNormallyClosed)
    }

    @Test fun pluginRequestsQueueForegroundGuidanceWithoutBindingAnUnavailablePlugin() = fixture { f ->
        f.inspection = AiAgentInspection(AiAgentUiState.AUTHORIZATION_REQUIRED)
        f.owner.requestFromPlugin()
        val guide = withTimeout(2000) { f.owner.pendingGuide.first { it != null } }!!
        assertEquals(AiAgentUiState.AUTHORIZATION_REQUIRED, guide.state)
        assertEquals(0, f.attaches.get())
        assertTrue(f.owner.consumeGuide(guide))
        assertFalse(f.owner.consumeGuide(guide))
    }

    @Test fun approvedRootsPersistAcrossReattachWhileRejectedChangesPreserveTheLink() = fixture { f ->
        val root = java.io.File(android.os.Environment.getExternalStorageDirectory(), "AgentP31Owner-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        try {
            val config = AiAgentScriptRoots.configuration(org.json.JSONObject().put("scriptRoots", org.json.JSONArray().put(root.path)).toString())
            assertEquals(AiAgentUiState.ATTACHED, f.owner.connect(configuration = config).await().state)
            assertEquals(config, f.savedRoots)
            assertEquals(config, f.configurations.last())
            val invalid = """{"scriptRoots":["/data"]}"""
            assertEquals(AiAgentUiState.FAILED, f.owner.connect(configuration = invalid).await().state)
            assertEquals("attached", f.controller.status.value.state)
            assertEquals(config, f.savedRoots)
            assertEquals(1, f.configurations.size)
            f.controller.detach()
            assertEquals(AiAgentUiState.ATTACHED, f.owner.connect().await().state)
            assertEquals(config, f.configurations.last())
            assertEquals(AiAgentUiState.ATTACHED, f.owner.connect(configuration = "{\"scriptRoots\":[]}").await().state)
            assertEquals("{\"scriptRoots\":[]}", f.savedRoots)
            assertEquals("{\"scriptRoots\":[]}", f.configurations.last())
            assertEquals(0, f.runs.get())
        } finally { root.deleteRecursively() }
    }

    private fun fixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val previous = Pref.getBoolean(AiAgentLinkController.NORMALLY_CLOSED_KEY, true)
        val f = Fixture()
        try { withContext(Dispatchers.Main) { block(f) } }
        finally {
            f.controller.detach()
            f.scope.cancel()
            Pref.putBoolean(AiAgentLinkController.NORMALLY_CLOSED_KEY, previous)
        }
    }

    private class Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val attaches = AtomicInteger()
        val closed = AtomicInteger()
        val runs = AtomicInteger()
        val configurations = java.util.concurrent.CopyOnWriteArrayList<String>()
        var savedRoots = "{}"
        @Volatile var inspection = AiAgentInspection(AiAgentUiState.AVAILABLE)
        val controller = AiAgentLinkController(context, scope = scope, available = { true }, uid = { Process.myUid() },
            attachLink = { config, _, _ ->
                configurations.add(config.getString(org.autojs.plugin.ai.agent.api.AiAgentContract.KEY_LINK_CONFIG_JSON)!!)
                attaches.incrementAndGet()
                AidlPluginHost.LeasedCall(object : IAiAgentLink.Stub() {
                    override fun getStatus() = Bundle()
                    override fun startRun(request: Bundle?, callback: IAiAgentRunCallback?) = Bundle().also { runs.incrementAndGet() }
                    override fun respond(response: Bundle?) = Bundle()
                    override fun cancelRun(ref: Bundle?) = Unit
                    override fun listRuns(query: Bundle?) = Bundle()
                    override fun getRun(ref: Bundle?) = Bundle()
                    override fun listPresets(query: Bundle?) = Bundle()
                    override fun updateConfig(config: Bundle?) { configurations.add(config!!.getString(org.autojs.plugin.ai.agent.api.AiAgentContract.KEY_LINK_CONFIG_JSON)!!) }
                    override fun detach(reason: Bundle?) = Unit
                }, Closeable { closed.incrementAndGet() })
            })
        val owner = AiAgentConnectionOwner(context, controller, scope, { inspection }, observePluginChanges = false,
            readRootConfiguration = { savedRoots }, saveRootConfiguration = { savedRoots = it })
    }
}
