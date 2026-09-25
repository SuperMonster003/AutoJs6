package org.autojs.autojs.core.plugin.agent

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.app.tool.AiAgentTool
import org.autojs.autojs.core.plugin.center.InstalledPluginRepository
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.settings.AiAgentLauncher
import org.autojs.autojs.engine.NodeAccessibilitySmokeActivity
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.ai.agent.api.AiAgentActions
import org.autojs.plugin.ai.agent.api.AiAgentIds
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Requires the official AI Agent APK. No Agent task or model request is started. */
class AiAgentEntryDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The installed plugin creates the PendingIntent and sends the production protected broadcast. */
    @Test fun installedPluginLauncherRequestsProductionAttachmentAfterWake() = runBlocking {
        assumeTrue("Opt in with -e autojs.agent.entry true",
            InstrumentationRegistry.getArguments().getString("autojs.agent.entry").toBoolean())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val controller = AiAgentLinkController.get(context)
        val owner = AiAgentConnectionOwner.get(context)
        assertNull("Do not interrupt an existing task", controller.status.value.runningRunId)
        assertEquals(0, controller.status.value.queuedCount)
        val wasClosed = owner.isNormallyClosed
        val enabled = PluginEnableStore.isEnabled(context, AiAgentIds.DEFAULT_PACKAGE_NAME, false)
        val roots = Pref.getStringOrNull(AiAgentConnectionOwner.ROOT_CONFIGURATION_KEY)
        fun await(message: String, check: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (!check() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertTrue(message, check())
        }
        try {
            instrumentation.runOnMainSync { owner.disconnect() }
            await("Disconnected before any plugin request") { controller.status.value.state == "detached" }
            context.startActivity(Intent("org.autojs.plugin.action.WAKE").addCategory(Intent.CATEGORY_DEFAULT)
                .setPackage(AiAgentIds.DEFAULT_PACKAGE_NAME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Installed Wake Activity clears the stopped flag") {
                context.packageManager.getApplicationInfo(AiAgentIds.DEFAULT_PACKAGE_NAME, 0).flags and ApplicationInfo.FLAG_STOPPED == 0
            }
            PluginEnableStore.setEnabled(context, AiAgentIds.DEFAULT_PACKAGE_NAME, true)
            assertEquals(AiAgentUiState.AVAILABLE, AiAgentPluginInspector.inspect(context).state)
            assertTrue("Automatic host restoration is disabled", owner.isNormallyClosed)
            val started = SystemClock.elapsedRealtime()
            context.startActivity(checkNotNull(AiAgentLauncher.resolveIntent(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            await("Plugin broadcast must attach the real host controller") { controller.status.value.state == "attached" }
            assertFalse(owner.isNormallyClosed)
            assertNull(controller.status.value.runningRunId)
            assertEquals(0, controller.status.value.queuedCount)
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("stream", "P7_ENTRY productionBroadcastAttached=true elapsedMs=${SystemClock.elapsedRealtime() - started}\n")
            })
        } finally {
            instrumentation.runOnMainSync { owner.disconnect() }
            await("Final detach releases the production lease") { controller.status.value.state == "detached" }
            PluginEnableStore.setEnabled(context, AiAgentIds.DEFAULT_PACKAGE_NAME, enabled)
            if (roots == null) Pref.remove(AiAgentConnectionOwner.ROOT_CONFIGURATION_KEY)
            else Pref.putString(AiAgentConnectionOwner.ROOT_CONFIGURATION_KEY, roots)
            owner.isNormallyClosed = wasClosed
        }
    }

    @Test fun installedPluginIsVisibleAndItsExistingLauncherIsExplicit() = runBlocking {
        assertEquals(1, AiAgentPluginHost.queryServiceCount(context))
        val entry = InstalledPluginRepository().discoverInstalledSnapshot(context)
            .single { it.packageName == AiAgentIds.DEFAULT_PACKAGE_NAME }
        assertEquals("ai-agent", entry.engine)
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault(entry.engine))
        val launch = checkNotNull(AiAgentLauncher.resolveIntent(context))
        assertEquals(AiAgentIds.DEFAULT_PACKAGE_NAME, launch.component?.packageName)
        assertEquals(Intent.ACTION_MAIN, launch.action)
        assertTrue(launch.hasCategory(Intent.CATEGORY_LAUNCHER))
        val metadata = AiAgentPluginHost.probeMetadata(context, AiAgentIds.DEFAULT_PACKAGE_NAME)
        assertEquals(AiAgentIds.PLUGIN_ID, metadata.id)
        assertEquals(AiAgentIds.ENGINE, metadata.engine)
        val required = metadata.capabilities!!.getLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION)
        @Suppress("DEPRECATION")
        val service = context.packageManager.queryIntentServices(Intent(AiAgentActions.SERVICE_ACTION)
            .addCategory(AiAgentActions.SERVICE_CATEGORY).setPackage(AiAgentIds.DEFAULT_PACKAGE_NAME), PackageManager.GET_META_DATA).single().serviceInfo
        assertEquals("Installed metadata and service manifest must agree", service.metaData.getInt("requiresHostVersion").toLong(), required)
        assertTrue("Plugin may require a newer host than the original contract floor",
            required in AiAgentIds.REQUIRED_HOST_VERSION_CODE..BuildConfig.VERSION_CODE.toLong())
    }

    @Test fun receiverManifestAndForeignIdentityRequestsAreRejectedBeforeBinding() {
        @Suppress("DEPRECATION")
        val receiver = context.packageManager.getReceiverInfo(ComponentName(context, AiAgentAttachRequestReceiver::class.java), PackageManager.GET_META_DATA)
        assertTrue(receiver.exported)
        assertEquals(AiAgentActions.PLUGIN_PERMISSION, receiver.permission)
        val request = Intent(AiAgentActions.ACTION_ATTACH_REQUEST).setPackage(context.packageName)
        assertFalse(AiAgentAttachRequestReceiver.isAuthorizedRequest(context, request))
        val identity = PendingIntent.getBroadcast(context, 519,
            Intent("org.autojs.autojs6.test.AGENT_IDENTITY").setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
        try {
            request.putExtra(AiAgentActions.EXTRA_ATTACH_IDENTITY, identity)
            assertFalse("A host-created identity is not a plugin-created identity",
                AiAgentAttachRequestReceiver.isAuthorizedRequest(context, request))
            val foreground = Intent(request).putExtra(AiAgentActions.EXTRA_AI_AGENT_ATTACH, true)
            AiAgentAttachRequestReceiver.handleForegroundRequest(context, foreground)
            assertFalse(foreground.hasExtra(AiAgentActions.EXTRA_AI_AGENT_ATTACH))
            assertFalse(foreground.hasExtra(AiAgentActions.EXTRA_ATTACH_IDENTITY))
        } finally { identity.cancel() }
    }

    @Test fun everyGuideStateRendersAndAttachedSubtitleShowsRunAndQueueCounts() {
        for (state in AiAgentUiState.entries) {
            assertTrue(state.name, AiAgentTool.subtitle(context, AiAgentToolState(state)).isNotBlank())
        }
        val text = AiAgentTool.subtitle(context, AiAgentToolState(AiAgentUiState.ATTACHED,
            AiAgentLinkStatus("attached", "test-run", queuedCount = 3)))
        assertTrue(text.contains("1"))
        assertTrue(text.contains("3"))
    }

    @Test fun allDiscoveryGuidesShowTheirSpecificMessageAndDismissCleanly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(context, NodeAccessibilitySmokeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.runOnMainSync {
                val themed = ContextThemeWrapper(activity, context.applicationInfo.theme)
                val tool = AiAgentTool(themed)
                val show = AiAgentTool::class.java.getDeclaredMethod("showGuide", AiAgentInspection::class.java).apply { isAccessible = true }
                val field = AiAgentTool::class.java.getDeclaredField("dialog").apply { isAccessible = true }
                val messages = mapOf(
                    AiAgentUiState.NOT_INSTALLED to R.string.prompt_ai_agent_install,
                    AiAgentUiState.APPLICATION_DISABLED to R.string.prompt_ai_agent_application_disabled,
                    AiAgentUiState.ACTIVATION_REQUIRED to R.string.prompt_ai_agent_activate,
                    AiAgentUiState.PLUGIN_DISABLED to R.string.prompt_ai_agent_enable,
                    AiAgentUiState.AUTHORIZATION_REQUIRED to R.string.prompt_ai_agent_authorize,
                    AiAgentUiState.TRUST_CONFIRMATION_REQUIRED to R.string.prompt_ai_agent_trust,
                    AiAgentUiState.FAILED to R.string.prompt_ai_agent_failed,
                )
                try {
                    for ((state, message) in messages) {
                        show.invoke(tool, AiAgentInspection(state))
                        val dialog = field.get(tool) as MaterialDialog
                        assertTrue(state.name, dialog.isShowing)
                        assertEquals(themed.getString(message), dialog.contentView!!.text.toString())
                        dialog.dismiss()
                    }
                    val required = BuildConfig.VERSION_CODE.toLong() + 1
                    show.invoke(tool, AiAgentInspection(AiAgentUiState.INCOMPATIBLE, required))
                    val dialog = field.get(tool) as MaterialDialog
                    assertTrue(dialog.isShowing)
                    assertEquals(themed.getString(R.string.prompt_ai_agent_incompatible, required), dialog.contentView!!.text.toString())
                } finally { tool.dispose() }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
