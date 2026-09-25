package org.autojs.autojs.core.plugin.center

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Runs from the real host against separately installed, R8-processed release APKs. */
class CompanionReleaseSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun offlineDocsReleaseWakesAndReturnsInstalledMetadata() {
        verifyRelease("io.github.supermonster003.autojs6.plugin.offlinedocs", "offline-docs", "Offline Documentation")
    }

    @Test
    fun aceReleaseWakesAndReturnsInstalledMetadata() {
        verifyRelease("io.github.supermonster003.autojs6.plugin.ace.editor", "ace-editor", "Ace Editor")
    }

    @Suppress("DEPRECATION")
    private fun verifyRelease(packageName: String, expectedId: String, expectedName: String) {
        assumeTrue("Requires installed companion release APKs", arguments.getString("autojs.companion.release") == "true")
        val manager = context.packageManager
        val app = manager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val wakeName = requireNotNull(app.metaData.getString("org.autojs.plugin.WAKE_ACTIVITY"))
        val wake = ComponentName(packageName, if (wakeName.startsWith('.')) packageName + wakeName else wakeName)
        val wakeInfo = manager.getActivityInfo(wake, 0)
        assertTrue(wakeInfo.enabled && wakeInfo.exported)
        assertEquals("org.autojs.permission.PLUGIN", wakeInfo.permission)
        assertEquals(android.R.style.Theme_NoDisplay, wakeInfo.theme)
        context.startActivity(Intent().setComponent(wake).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        val services = manager.queryIntentServices(Intent("org.autojs.plugin.INFO").setPackage(packageName), 0)
        assertEquals("Exactly one INFO service", 1, services.size)
        val service = services.single().serviceInfo
        assertTrue(service.enabled && service.exported)
        assertEquals("org.autojs.permission.PLUGIN", service.permission)
        val ready = CountDownLatch(1)
        val binder = AtomicReference<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, value: IBinder) {
                binder.set(value)
                ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent().setComponent(ComponentName(packageName, service.name)), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("Release INFO service did not connect", ready.await(10, TimeUnit.SECONDS))
            val remote = requireNotNull(binder.get())
            assertEquals("org.autojs.plugin.common.api.IPluginInfoProvider", remote.interfaceDescriptor)
            val info = IPluginInfoProvider.Stub.asInterface(remote).info
            val installed = manager.getPackageInfo(packageName, 0)
            assertEquals(expectedId, info.id)
            assertEquals(expectedName, info.name)
            assertEquals(installed.versionName, info.versionName)
            assertEquals(if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong(), info.versionCode)
            assertTrue(!info.description.isNullOrBlank())
            assertNotNull(info.capabilities?.get(PluginCapabilityKeys.REQUIRES_HOST_VERSION))
        } finally {
            context.unbindService(connection)
        }
    }
}
