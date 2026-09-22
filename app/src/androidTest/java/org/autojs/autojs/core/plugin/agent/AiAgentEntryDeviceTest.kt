package org.autojs.autojs.core.plugin.agent

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.app.tool.AiAgentTool
import org.autojs.autojs.core.plugin.center.InstalledPluginRepository
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.ui.settings.AiAgentLauncher
import org.autojs.autojs.engine.NodeAccessibilitySmokeActivity
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.ai.agent.api.AiAgentActions
import org.autojs.plugin.ai.agent.api.AiAgentIds
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.*
import org.junit.Test

/** Requires the official P0 AI Agent APK on the isolated test device. No Agent task is started. */
class AiAgentEntryDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

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
        assertEquals(AiAgentIds.REQUIRED_HOST_VERSION_CODE,
            metadata.capabilities!!.getLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION))
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
