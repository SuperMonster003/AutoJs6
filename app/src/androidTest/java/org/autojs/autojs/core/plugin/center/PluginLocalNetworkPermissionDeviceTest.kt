package org.autojs.autojs.core.plugin.center

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.main.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Run on a disposable emulator with the four companion APKs installed. */
class PluginLocalNetworkPermissionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation = instrumentation.uiAutomation
    private val permission = PluginLocalNetworkPermission.PERMISSION
    private val packages = listOf(
        "io.github.supermonster003.autojs6.plugin.nodejs",
        "io.github.supermonster003.autojs6.plugin.bun.runtime",
        "io.github.supermonster003.autojs6.plugin.python.runtime",
        "io.github.supermonster003.autojs6.plugin.mcp.server",
    )
    private val preferences = context.getSharedPreferences("plugin_center_enable_state", Context.MODE_PRIVATE)
    private var savedPreferences: Map<String, *>? = null
    private var savedGrants = emptyMap<String, Boolean>()

    @Before
    fun saveState() {
        assumeTrue("Explicitly opt in on a disposable emulator", InstrumentationRegistry.getArguments().getString("pluginLanPermissionProbe") == "true")
        for (packageName in packages) context.packageManager.getApplicationInfo(packageName, 0)
        savedPreferences = preferences.all.toMap()
        if (Build.VERSION.SDK_INT >= 37) {
            savedGrants = packages.associateWith { granted(it) }
        }
    }

    @After
    fun restoreState() {
        savedGrants.forEach { (packageName, allowed) -> setGrant(packageName, allowed) }
        savedPreferences?.let { saved ->
            preferences.edit().clear().apply {
                saved.forEach { (key, value) -> putBoolean(key, value as Boolean) }
            }.commit()
        }
    }

    @Test
    fun hostGrantDoesNotAllowAnUnpermittedPluginOrPersistAnEnableAttempt() {
        assumeTrue(Build.VERSION.SDK_INT >= 37)
        assertEquals("Grant the host permission before starting instrumentation", PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(permission))
        for (packageName in packages) {
            setGrant(packageName, false)
            assertFalse(packageName, PluginLocalNetworkPermission.isGranted(context, packageName))
            PluginEnableStore.setEnabled(context, packageName, true)
            assertFalse(packageName, PluginEnableStore.isEnabled(context, packageName))
        }
    }

    @Test
    fun revocationClearsPersistedEnablementAndGrantAloneDoesNotRestoreIt() {
        assumeTrue(Build.VERSION.SDK_INT >= 37)
        for (packageName in packages) {
            setGrant(packageName, true)
            PluginEnableStore.setEnabled(context, packageName, true)
            assertTrue(packageName, PluginEnableStore.isEnabled(context, packageName))
            setGrant(packageName, false)
            // This is also the automatic mount/connection path. It must not open UI.
            assertFalse(packageName, PluginEnableStore.isEnabled(context, packageName))
            setGrant(packageName, true)
            assertFalse(packageName, PluginEnableStore.isEnabled(context, packageName))
            PluginEnableStore.setEnabled(context, packageName, true)
            assertTrue(packageName, PluginEnableStore.isEnabled(context, packageName))
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun permissionHandoffIsProtectedAndHasNoLauncherEntry() {
        for (packageName in packages) {
            val activity = context.packageManager.getActivityInfo(
                ComponentName(packageName, "$packageName.LocalNetworkPermissionActivity"), 0,
            )
            assertTrue(packageName, activity.exported && activity.enabled)
            assertEquals(packageName, "org.autojs.permission.PLUGIN", activity.permission)
            assertTrue(packageName, context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName), 0,
            ).isEmpty())
        }
    }

    @Test
    fun olderAndroidDoesNotRequirePermissionForEnablement() {
        assumeTrue(Build.VERSION.SDK_INT < 37)
        for (packageName in packages) {
            assertFalse(packageName, PluginLocalNetworkPermission.isRequired(packageName))
            PluginEnableStore.setEnabled(context, packageName, true)
            assertTrue(packageName, PluginEnableStore.isEnabled(context, packageName))
        }
    }

    @Test
    fun permissionResultReachesBothPluginCenterEntryPoints() {
        val packageName = packages.first()
        if (Build.VERSION.SDK_INT >= 37) setGrant(packageName, true)
        // An already granted handoff finishes immediately, so this tests the actual cross-APK
        // result route without automating or bypassing a system permission dialog.
        for (screen in listOf(MainActivity::class.java, PluginCenterActivity::class.java)) {
            val activity = instrumentation.startActivitySync(
                Intent(context, screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            ) as FragmentActivity
            var launcher: ActivityResultLauncher<Intent>? = null
            try {
                val returned = CountDownLatch(1)
                val result = AtomicInteger(Int.MIN_VALUE)
                instrumentation.runOnMainSync {
                    launcher = activity.activityResultRegistry.register(
                        "plugin-lan-result-test", ActivityResultContracts.StartActivityForResult(),
                    ) {
                        result.set(it.resultCode)
                        returned.countDown()
                    }
                    launcher!!.launch(Intent().setComponent(
                        ComponentName(packageName, "$packageName.LocalNetworkPermissionActivity"),
                    ))
                }
                assertTrue("Permission result was swallowed by ${screen.simpleName}", returned.await(10, TimeUnit.SECONDS))
                assertEquals(android.app.Activity.RESULT_OK, result.get())
            } finally {
                instrumentation.runOnMainSync {
                    launcher?.unregister()
                    activity.finish()
                }
            }
        }
    }

    private fun granted(packageName: String) = context.packageManager.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED

    private fun setGrant(packageName: String, allowed: Boolean) {
        if (granted(packageName) == allowed) return
        if (allowed) automation.grantRuntimePermission(packageName, permission)
        else automation.revokeRuntimePermission(packageName, permission)
        assertEquals(packageName, allowed, granted(packageName))
    }
}
