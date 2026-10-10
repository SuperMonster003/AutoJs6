package org.autojs.autojs.core.plugin.agent

import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.autojs.autojs.core.plugin.AidlPluginHost
import org.autojs.autojs.core.pref.Pref
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentLink
import org.autojs.plugin.three.stove.agent.api.IThreeStoveAgentRunCallback
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the process owner with a real controller and local Binder link, without a task. */
class ThreeStoveAgentConnectionOwnerTest {
    @Test fun startupIsLazyAndConcurrentUsesShareOneLinkDespiteTheOldDrawerPreference() = fixture { f ->
        Pref.putBoolean(LEGACY_DRAWER_KEY, true)
        f.owner.refresh()
        assertEquals("detached", f.controller.scriptRuns.status()["state"].asString)
        assertEquals(0, f.attaches.get())
        val first = f.owner.connect()
        val second = f.owner.connect()
        assertSame(first, second)
        assertEquals(ThreeStoveAgentUiState.ATTACHED, first.await().state)
        withTimeout(2000) { f.owner.state.first { it.state == ThreeStoveAgentUiState.ATTACHED } }
        assertEquals(1, f.attaches.get())
        assertEquals(0, f.runs.get())
    }

    @Test fun scriptQueriesConnectOnDemandAndDisconnectReleasesTheLink() = fixture { f ->
        val presets = withContext(Dispatchers.IO) { f.controller.scriptRuns.presets() }
        assertEquals(0, presets["presets"].asJsonArray.size())
        withTimeout(2000) { f.owner.state.first { it.state == ThreeStoveAgentUiState.ATTACHED } }
        assertEquals(1, f.attaches.get())
        assertEquals(0, f.runs.get())
        f.owner.disconnect()
        withTimeout(2000) { f.owner.state.first { it.state == ThreeStoveAgentUiState.DETACHED } }
        assertEquals(1, f.closed.get())
    }

    @Test fun aSynchronousUiCallerNeverStartsAnOnDemandBind() = fixture { f ->
        assertFalse(f.controller.ensureConnected(allowConnect = false))
        assertEquals(0, f.attaches.get())
        f.owner.connect().await()
        assertTrue(f.controller.ensureConnected(allowConnect = false))
        f.controller.detach()
        assertFalse(f.controller.ensureConnected(allowConnect = false))
        assertEquals(1, f.attaches.get())
    }

    @Test fun disabledPluginRevokesTheActiveLinkOnStateRefresh() = fixture { f ->
        f.owner.connect().await()
        f.inspection = ThreeStoveAgentInspection(ThreeStoveAgentUiState.PLUGIN_DISABLED)
        f.owner.refresh()
        withTimeout(2000) { f.owner.state.first { it.state == ThreeStoveAgentUiState.PLUGIN_DISABLED } }
        assertEquals("detached", f.controller.status.value.state)
        assertEquals(1, f.closed.get())
        assertEquals(ThreeStoveAgentUiState.PLUGIN_DISABLED, f.owner.connect().await().state)
        assertEquals(1, f.attaches.get())
        f.inspection = ThreeStoveAgentInspection(ThreeStoveAgentUiState.AVAILABLE)
        assertEquals(ThreeStoveAgentUiState.ATTACHED, f.owner.connect().await().state)
        assertEquals(2, f.attaches.get())
    }

    @Test fun pluginRequestsQueueForegroundGuidanceWithoutBindingAnUnavailablePlugin() = fixture { f ->
        f.inspection = ThreeStoveAgentInspection(ThreeStoveAgentUiState.AUTHORIZATION_REQUIRED)
        f.owner.requestFromPlugin()
        val guide = withTimeout(2000) { f.owner.pendingGuide.first { it != null } }!!
        assertEquals(ThreeStoveAgentUiState.AUTHORIZATION_REQUIRED, guide.state)
        assertEquals(0, f.attaches.get())
        assertTrue(f.owner.consumeGuide(guide))
        assertFalse(f.owner.consumeGuide(guide))
    }

    @Test fun disablingWhileBindingRevokesTheCompletedLeaseWithoutStartingATask() = fixture { f ->
        f.onAttach = { f.inspection = ThreeStoveAgentInspection(ThreeStoveAgentUiState.PLUGIN_DISABLED) }
        assertEquals(ThreeStoveAgentUiState.PLUGIN_DISABLED, f.owner.connect().await().state)
        assertEquals("detached", f.controller.status.value.state)
        assertEquals(1, f.attaches.get())
        assertEquals(1, f.closed.get())
        assertEquals(0, f.runs.get())
    }

    @Test fun catalogFallsBackWithoutAnEnableGuideAndConnectsWhenAvailable() = fixture { f ->
        for (state in listOf(ThreeStoveAgentUiState.NOT_INSTALLED, ThreeStoveAgentUiState.PLUGIN_DISABLED, ThreeStoveAgentUiState.INCOMPATIBLE)) {
            f.inspection = ThreeStoveAgentInspection(state)
            assertEquals(emptyList<String>(), withContext(Dispatchers.IO) { f.controller.scriptRuns.roots() })
            assertNull(f.owner.pendingGuide.value)
        }
        assertEquals(0, f.attaches.get())
        f.inspection = ThreeStoveAgentInspection(ThreeStoveAgentUiState.AVAILABLE)
        assertEquals(emptyList<String>(), withContext(Dispatchers.IO) { f.controller.scriptRuns.roots() })
        assertEquals(1, f.attaches.get())
        assertEquals("attached", f.controller.status.value.state)
        assertEquals(0, f.runs.get())
    }

    @Test fun approvedRootsPersistAcrossReattachWhileRejectedChangesPreserveTheLink() = fixture { f ->
        val root = java.io.File(android.os.Environment.getExternalStorageDirectory(), "AgentP31Owner-${System.nanoTime()}")
        assertTrue(root.mkdirs())
        try {
            val config = ThreeStoveAgentScriptRoots.configuration(org.json.JSONObject().put("scriptRoots", org.json.JSONArray().put(root.path)).toString())
            assertEquals(ThreeStoveAgentUiState.ATTACHED, f.owner.connect(configuration = config).await().state)
            assertEquals(config, f.savedRoots)
            assertEquals(config, f.configurations.last())
            val invalid = """{"scriptRoots":["/data"]}"""
            assertEquals(ThreeStoveAgentUiState.FAILED, f.owner.connect(configuration = invalid).await().state)
            assertEquals("attached", f.controller.status.value.state)
            assertEquals(config, f.savedRoots)
            assertEquals(1, f.configurations.size)
            f.controller.detach()
            assertEquals(ThreeStoveAgentUiState.ATTACHED, f.owner.connect().await().state)
            assertEquals(config, f.configurations.last())
            assertEquals(ThreeStoveAgentUiState.ATTACHED, f.owner.connect(configuration = "{\"scriptRoots\":[]}").await().state)
            assertEquals("{\"scriptRoots\":[]}", f.savedRoots)
            assertEquals("{\"scriptRoots\":[]}", f.configurations.last())
            assertEquals(0, f.runs.get())
        } finally { root.deleteRecursively() }
    }

    private fun fixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val previous = Pref.getBoolean(LEGACY_DRAWER_KEY, true)
        val f = Fixture()
        try { withContext(Dispatchers.Main) { block(f) } }
        finally {
            f.controller.detach()
            f.scope.cancel()
            Pref.putBoolean(LEGACY_DRAWER_KEY, previous)
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
        var onAttach: () -> Unit = {}
        @Volatile var inspection = ThreeStoveAgentInspection(ThreeStoveAgentUiState.AVAILABLE)
        val controller: ThreeStoveAgentLinkController = ThreeStoveAgentLinkController(context, scope = scope, available = { inspection.state == ThreeStoveAgentUiState.AVAILABLE }, uid = { Process.myUid() },
            connectOnDemand = { showGuide -> owner.ensureConnected(showGuide) },
            attachLink = { config, _, _ ->
                configurations.add(config.getString(org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract.KEY_LINK_CONFIG_JSON)!!)
                attaches.incrementAndGet()
                onAttach()
                AidlPluginHost.LeasedCall(object : IThreeStoveAgentLink.Stub() {
                    override fun getStatus() = Bundle().apply {
                        putInt(org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract.KEY_CONTRACT_VERSION, org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract.CONTRACT_VERSION)
                        putString(org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract.KEY_STATUS_JSON, "{\"scriptRoots\":[]}")
                    }
                    override fun startRun(request: Bundle?, callback: IThreeStoveAgentRunCallback?) = Bundle().also { runs.incrementAndGet() }
                    override fun respond(response: Bundle?) = Bundle()
                    override fun cancelRun(ref: Bundle?) = Unit
                    override fun listRuns(query: Bundle?) = Bundle()
                    override fun getRun(ref: Bundle?) = Bundle()
                    override fun listPresets(query: Bundle?) = Bundle().apply {
                        putInt(org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract.KEY_CONTRACT_VERSION, org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract.CONTRACT_VERSION)
                        putString(org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract.KEY_RUN_RESPONSE_JSON, "{\"presets\":[]}")
                    }
                    override fun updateConfig(config: Bundle?) { configurations.add(config!!.getString(org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract.KEY_LINK_CONFIG_JSON)!!) }
                    override fun detach(reason: Bundle?) = Unit
                }, org.autojs.autojs.core.plugin.ServiceBindingLease { closed.incrementAndGet() }.apply { markRegistered() })
            })
        val owner: ThreeStoveAgentConnectionOwner = ThreeStoveAgentConnectionOwner(context, controller, scope, { inspection }, observePluginChanges = false,
            readRootConfiguration = { savedRoots }, saveRootConfiguration = { savedRoots = it })
    }

    companion object { private const val LEGACY_DRAWER_KEY = "key_\$_three_stove_agent_normally_closed" }
}
