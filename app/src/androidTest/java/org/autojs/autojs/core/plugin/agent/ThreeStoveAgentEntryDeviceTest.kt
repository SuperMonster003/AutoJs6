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
import org.autojs.autojs.ui.settings.ThreeStoveAgentGuide
import org.autojs.autojs.core.plugin.center.InstalledPluginRepository
import org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicy
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.settings.ThreeStoveAgentLauncher
import org.autojs.autojs.engine.NodeAccessibilitySmokeActivity
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentActions
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Requires the official 3-Stove Agent APK. No Agent task or model request is started. */
class ThreeStoveAgentEntryDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The installed plugin creates the PendingIntent and sends the production protected broadcast. */
    @Test fun installedPluginLauncherRequestsProductionAttachmentAfterWake() = runBlocking {
        assumeTrue("Opt in with -e autojs.agent.entry true",
            InstrumentationRegistry.getArguments().getString("autojs.agent.entry").toBoolean())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val controller = ThreeStoveAgentLinkController.get(context)
        val owner = ThreeStoveAgentConnectionOwner.get(context)
        assertNull("Do not interrupt an existing task", controller.status.value.runningRunId)
        assertEquals(0, controller.status.value.queuedCount)
        val enablePreferences = context.getSharedPreferences("plugin_center_enable_state", 0)
        val enableKey = "key_\$_enabled_plugin_\$_${ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME}"
        val enabled = if (enablePreferences.contains(enableKey)) enablePreferences.getBoolean(enableKey, false) else null
        val roots = Pref.getStringOrNull(ThreeStoveAgentConnectionOwner.ROOT_CONFIGURATION_KEY)
        fun await(message: String, check: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (!check() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertTrue(message, check())
        }
        try {
            instrumentation.runOnMainSync { owner.disconnect() }
            await("Disconnected before any plugin request") { controller.status.value.state == "detached" }
            context.startActivity(Intent("org.autojs.plugin.action.WAKE").addCategory(Intent.CATEGORY_DEFAULT)
                .setPackage(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Installed Wake Activity clears the stopped flag") {
                context.packageManager.getApplicationInfo(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, 0).flags and ApplicationInfo.FLAG_STOPPED == 0
            }
            PluginEnableStore.setEnabled(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, true)
            assertEquals(ThreeStoveAgentUiState.AVAILABLE, ThreeStoveAgentPluginInspector.inspect(context).state)
            val started = SystemClock.elapsedRealtime()
            context.startActivity(checkNotNull(ThreeStoveAgentLauncher.resolveIntent(context))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            await("Plugin broadcast must attach the real host controller") { controller.status.value.state == "attached" }
            assertNull(controller.status.value.runningRunId)
            assertEquals(0, controller.status.value.queuedCount)
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("stream", "P7_ENTRY productionBroadcastAttached=true elapsedMs=${SystemClock.elapsedRealtime() - started}\n")
            })
        } finally {
            instrumentation.runOnMainSync { owner.disconnect() }
            await("Final detach releases the production lease") { controller.status.value.state == "detached" }
            enablePreferences.edit().apply { if (enabled == null) remove(enableKey) else putBoolean(enableKey, enabled) }.commit()
            PluginStateChangeRegistry.notifyChanged(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)
            if (roots == null) Pref.remove(ThreeStoveAgentConnectionOwner.ROOT_CONFIGURATION_KEY)
            else Pref.putString(ThreeStoveAgentConnectionOwner.ROOT_CONFIGURATION_KEY, roots)
        }
    }

    @Test fun officialInstallNeedsNoEnablePromptAndExplicitDisableWins() {
        val packageName = ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME
        assumeTrue(org.autojs.autojs.core.plugin.center.PluginTrustManager.resolveTrustInfo(context, packageName).isOfficial)
        val preferences = context.getSharedPreferences("plugin_center_enable_state", 0)
        val key = "key_\$_enabled_plugin_\$_$packageName"
        val saved = if (preferences.contains(key)) preferences.getBoolean(key, false) else null
        try {
            preferences.edit().remove(key).commit()
            val state = ThreeStoveAgentPluginInspector.inspect(context).state
            assertTrue(state.name, state in setOf(ThreeStoveAgentUiState.AVAILABLE, ThreeStoveAgentUiState.ACTIVATION_REQUIRED))
            PluginEnableStore.setEnabled(context, packageName, false)
            assertEquals(ThreeStoveAgentUiState.PLUGIN_DISABLED, ThreeStoveAgentPluginInspector.inspect(context).state)
        } finally {
            preferences.edit().apply { if (saved == null) remove(key) else putBoolean(key, saved) }.commit()
            PluginStateChangeRegistry.notifyChanged(packageName)
        }
    }

    @Test fun installedPluginIsVisibleAndItsExistingLauncherIsExplicit() = runBlocking {
        assertEquals(1, ThreeStoveAgentPluginHost.queryServiceCount(context))
        val entry = InstalledPluginRepository().discoverInstalledSnapshot(context)
            .single { it.packageName == ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME }
        assertEquals("three-stove-agent", entry.engine)
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault(entry.engine))
        val launch = checkNotNull(ThreeStoveAgentLauncher.resolveIntent(context))
        assertEquals(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME, launch.component?.packageName)
        assertEquals(Intent.ACTION_MAIN, launch.action)
        assertTrue(launch.hasCategory(Intent.CATEGORY_LAUNCHER))
        val metadata = ThreeStoveAgentPluginHost.probeMetadata(context, ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME)
        assertEquals(ThreeStoveAgentIds.PLUGIN_ID, metadata.id)
        assertEquals(ThreeStoveAgentIds.ENGINE, metadata.engine)
        val required = metadata.capabilities!!.getLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION)
        @Suppress("DEPRECATION")
        val service = context.packageManager.queryIntentServices(Intent(ThreeStoveAgentActions.SERVICE_ACTION)
            .addCategory(ThreeStoveAgentActions.SERVICE_CATEGORY).setPackage(ThreeStoveAgentIds.DEFAULT_PACKAGE_NAME), PackageManager.GET_META_DATA).single().serviceInfo
        assertEquals("Installed metadata and service manifest must agree", service.metaData.getInt("requiresHostVersion").toLong(), required)
        assertTrue("Plugin may require a newer host than the original contract floor",
            required in ThreeStoveAgentIds.REQUIRED_HOST_VERSION_CODE..BuildConfig.VERSION_CODE.toLong())
    }

    @Test fun receiverManifestAndForeignIdentityRequestsAreRejectedBeforeBinding() {
        @Suppress("DEPRECATION")
        val receiver = context.packageManager.getReceiverInfo(ComponentName(context, ThreeStoveAgentAttachRequestReceiver::class.java), PackageManager.GET_META_DATA)
        assertTrue(receiver.exported)
        assertEquals(ThreeStoveAgentActions.PLUGIN_PERMISSION, receiver.permission)
        val request = Intent(ThreeStoveAgentActions.ACTION_ATTACH_REQUEST).setPackage(context.packageName)
        assertFalse(ThreeStoveAgentAttachRequestReceiver.isAuthorizedRequest(context, request))
        val identity = PendingIntent.getBroadcast(context, 519,
            Intent("org.autojs.autojs6.test.AGENT_IDENTITY").setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
        try {
            request.putExtra(ThreeStoveAgentActions.EXTRA_ATTACH_IDENTITY, identity)
            assertFalse("A host-created identity is not a plugin-created identity",
                ThreeStoveAgentAttachRequestReceiver.isAuthorizedRequest(context, request))
            val foreground = Intent(request).putExtra(ThreeStoveAgentActions.EXTRA_THREE_STOVE_AGENT_ATTACH, true)
            assertNull(ThreeStoveAgentAttachRequestReceiver.takeForegroundRequest(context, foreground))
            assertFalse(foreground.hasExtra(ThreeStoveAgentActions.EXTRA_THREE_STOVE_AGENT_ATTACH))
            assertFalse(foreground.hasExtra(ThreeStoveAgentActions.EXTRA_ATTACH_IDENTITY))
        } finally { identity.cancel() }
    }

    /** Only an unrecognized signer still asks in a dialog; every other state opens Plugin Center instead. */
    @Test fun signerConfirmationIsTheOnlyGuideDialog() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(context, NodeAccessibilitySmokeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.runOnMainSync {
                val themed = ContextThemeWrapper(activity, context.applicationInfo.theme)
                val tool = ThreeStoveAgentGuide(themed)
                val confirm = ThreeStoveAgentGuide::class.java.getDeclaredMethod("confirmSigner", ThreeStoveAgentInspection::class.java, String::class.java).apply { isAccessible = true }
                val field = ThreeStoveAgentGuide::class.java.getDeclaredField("dialog").apply { isAccessible = true }
                try {
                    confirm.invoke(tool, ThreeStoveAgentInspection(ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED), null)
                    val dialog = field.get(tool) as MaterialDialog
                    assertTrue(dialog.isShowing)
                    assertEquals(themed.getString(R.string.prompt_three_stove_agent_trust), dialog.contentView!!.text.toString())
                    assertEquals("3-Stove Agent", dialog.titleView!!.text.toString())
                    dialog.dismiss()
                } finally { tool.dispose() }
            }
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
