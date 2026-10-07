package org.autojs.autojs.core.plugin.center

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.common.api.IPluginInfoProvider
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A host-side probe can bind a minified release without injecting debug test classes into it. */
class OfficialStandaloneReleaseDeviceTest {
    @Test fun publishedCandidatesExposeTheirNewIdentityAndProtectedInfoService() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("autojs.release.icons") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val requested = args.getString("releasePackages")?.split(',')?.toSet()
        val targets = mapOf(
            "io.github.supermonster003.autojs6.plugin.three.amber.player" to Triple("three-amber-player", "3-Amber Player", "4.0.0"),
            "io.github.supermonster003.autojs6.plugin.three.adapt.a11y" to Triple("three-adapt-a11y", "3-Adapt A11y", "1.4.0"),
            "io.github.supermonster003.autojs6.plugin.three.folio.epub" to Triple("three-folio-epub", "3-Folio EPUB", "2.0.0"),
        )
        for ((packageName, expected) in targets) {
            if (requested != null && packageName !in requested) continue
            val info = context.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            val app = requireNotNull(info.applicationInfo)
            assertEquals("Verify the final release candidate", 0, app.flags and ApplicationInfo.FLAG_DEBUGGABLE)
            val wake = requireNotNull(app.metaData.getString("org.autojs.plugin.WAKE_ACTIVITY"))
                .let { if (it.startsWith('.')) packageName + it else it }
            val wakeComponent = ComponentName(packageName, wake)
            assertEquals("org.autojs.permission.PLUGIN", context.packageManager.getActivityInfo(wakeComponent, 0).permission)
            context.startActivity(Intent().setComponent(wakeComponent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val services = context.packageManager.queryIntentServices(Intent("org.autojs.plugin.INFO").setPackage(packageName), 0)
            assertEquals(1, services.size)
            val service = services.single().serviceInfo
            assertTrue(service.exported)
            assertEquals("org.autojs.permission.PLUGIN", service.permission)
            val ready = CountDownLatch(1)
            var binder: IBinder? = null
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, remote: IBinder?) { binder = remote; ready.countDown() }
                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }
            assertTrue(context.bindService(Intent().setComponent(ComponentName(packageName, service.name)), connection, Context.BIND_AUTO_CREATE))
            try {
                assertTrue("Release Binder connection timed out: $packageName", ready.await(10, TimeUnit.SECONDS))
                val metadata = IPluginInfoProvider.Stub.asInterface(binder).info
                assertEquals(expected.first, metadata.id)
                assertEquals(expected.second, metadata.name)
                assertEquals(expected.third, metadata.versionName)
                @Suppress("DEPRECATION")
                assertEquals(info.versionCode.toLong(), metadata.versionCode)
                assertTrue(metadata.supportedAbis.isNullOrEmpty())
            } finally {
                context.unbindService(connection)
            }
        }
    }
}
