package org.autojs.autojs.core.plugin.center

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.Assume.assumeTrue
import java.io.File

class PluginLauncherIconDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fixture = instrumentation.context.packageName
    private val aliases = listOf("Auto", "Light", "Dark").map { "$fixture.launcher.$it" }

    @Before fun requireRootForOrdinaryPackageComponents() {
        val script = File(requireNotNull(context.externalCacheDir), "launcher-root-probe.sh")
        script.writeText("timeout 3 su 0 id 2>&1\n")
        try {
            val output = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("/system/bin/sh ${script.absolutePath}"))
                .bufferedReader().use { it.readText() }
            assumeTrue("These device cases require a Root-capable AVD or device", output.contains("uid=0"))
        } finally {
            script.delete()
        }
    }

    private fun states(): Map<String, Int> = aliases.associateWith {
        context.packageManager.getComponentEnabledSetting(ComponentName(fixture, it))
    }

    @Suppress("DEPRECATION")
    private fun visible(): List<String> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(fixture), 0,
    ).map { it.activityInfo.name }

    private fun execute(command: String) {
        // UiAutomation uses Runtime.exec without shell parsing; let sh read the exact command.
        val script = File(requireNotNull(context.externalCacheDir), "launcher-component-device-test.sh")
        script.writeText("$command 2>&1\n")
        try {
            val output = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("su 0 /system/bin/sh ${script.absolutePath}"))
                .bufferedReader().use { it.readText() }
            assertTrue(output, output.contains("AUTOJS_LAUNCHER_OK"))
        } finally {
            script.delete()
        }
    }

    private fun withFixture(block: () -> Unit) {
        val prefs = context.getSharedPreferences("plugin_launcher_icons", 0)
        val originalPreference = prefs.getString(fixture, null)
        val original = states()
        prefs.edit(commit = true) { remove(fixture) }
        try {
            execute(PluginLauncherIconController.command(context, fixture, aliases.associateWith { PackageManager.COMPONENT_ENABLED_STATE_DEFAULT }))
            block()
        } finally {
            prefs.edit(commit = true) { remove(fixture) }
            execute(PluginLauncherIconController.command(context, fixture, original))
            prefs.edit(commit = true) { if (originalPreference != null) putString(fixture, originalPreference) }
        }
    }

    @Test fun hideAndRestorePreserveTheChosenIconAndLeaveTheRealActivityEnabled() = withFixture {
        runBlocking {
            val selected = mapOf(aliases[0] to 2, aliases[1] to 1, aliases[2] to 0)
            execute(PluginLauncherIconController.command(context, fixture, selected))
            assertTrue(PluginLauncherIconController.isShown(context, fixture))
            assertEquals(listOf(aliases[1]), visible())
            val hidden = PluginLauncherIconController.setShown(context, fixture, false) { execute(it) }
            assertTrue(hidden.message, hidden.success)
            assertFalse(PluginLauncherIconController.isShown(context, fixture))
            assertTrue(visible().isEmpty())
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, context.packageManager.getComponentEnabledSetting(
                ComponentName(fixture, LauncherIconFixtureActivity::class.java.name)))
            val restored = PluginLauncherIconController.setShown(context, fixture, true) { execute(it) }
            assertTrue(restored.message, restored.success)
            assertEquals(selected, states())
            delay(1000) // A pending PACKAGE_CHANGED callback must not undo the user's Show choice.
            assertEquals(listOf(aliases[1]), visible())
            assertTrue(PluginLauncherIconController.isShown(context, fixture))
        }
    }

    @Test fun anUnavailablePermissionDoesNotSaveAFalseHiddenState() = withFixture {
        runBlocking {
            val original = states()
            val result = PluginLauncherIconController.setShown(context, fixture, false) {
                throw SecurityException("Permission denied")
            }
            assertFalse(result.success)
            assertTrue(PluginLauncherIconController.isShown(context, fixture))
            assertEquals(original, states())
            assertEquals(listOf(aliases[0]), visible())
        }
    }

    @Test fun iconsHiddenBeforeAutoJsCanBeRestoredToTheManifestDefault() = withFixture {
        runBlocking {
            execute(PluginLauncherIconController.command(context, fixture, aliases.associateWith { 2 }))
            assertTrue(visible().isEmpty())
            assertTrue(PluginLauncherIconController.setShown(context, fixture, false) { execute(it) }.success)
            assertTrue(PluginLauncherIconController.setShown(context, fixture, true) { execute(it) }.success)
            assertEquals(listOf(aliases[0]), visible())
        }
    }

    @Test fun theWizardLauncherOptionDefaultsToAllowedAndDoesNotModifyExistingPackages() = withFixture {
        val prefs = context.getSharedPreferences("plugin_install_wizard", 0)
        val key = "show_launcher_icons_for_new_plugins"
        val before = if (prefs.contains(key)) prefs.getBoolean(key, true) else null
        val original = states()
        try {
            prefs.edit(commit = true) { remove(key) }
            assertTrue(org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardProfileStore.showLauncherIcons(context))
            org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardProfileStore.setShowLauncherIcons(context, false)
            assertFalse(org.autojs.autojs.core.plugin.center.wizard.PluginInstallWizardProfileStore.showLauncherIcons(context))
            assertEquals(original, states())
            assertTrue(PluginLauncherIconController.isShown(context, fixture))
        } finally {
            prefs.edit(commit = true) { if (before == null) remove(key) else putBoolean(key, before) }
        }
    }
}
