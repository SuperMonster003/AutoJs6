package org.autojs.autojs.core.plugin.agent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.provider.api.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Run against the preserved 2.0 fake APK and the new 2.1 fake APK, on disposable AVDs only. */
class AiProviderVisionCompatibilityAndroidTest {
    @Test fun providerVersionAndCatalogMatchNegotiatedImageSupport() {
        val arguments = InstrumentationRegistry.getArguments()
        val expected = arguments.getString("autojs.aiProvider.expectedMinor")?.toIntOrNull()
        assumeTrue("Explicit expected minor version is required", expected in 0..1)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pkg = "org.autojs.plugin.ai.provider.fake"
        assertEquals(android.content.pm.PackageManager.SIGNATURE_MATCH, context.packageManager.checkSignatures(context.packageName, pkg))
        val provider = AtomicReference<IAiProvider>()
        val ready = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                provider.set(IAiProvider.Stub.asInterface(binder)); ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        assertTrue(context.bindService(Intent().setComponent(ComponentName(pkg, "$pkg.service.FakeAiProviderService")),
            connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            val service = provider.get()
            val info = AiCommonCodec.decodeProviderInfo(service.providerInfo)
            assertEquals(expected, info.protocolRange.maximum.minor)
            val selected = AiProviderVersionPolicy.negotiate(info.protocolRange)
            assertEquals(expected, selected.minor)
            val caps = AiProviderCodec.decodeCapabilities(service.capabilities)
            assertEquals(expected == 1, caps.vision != null)
            for (minor in 0..requireNotNull(expected)) {
                val page = AtomicReference<AiTargetPage>()
                val complete = CountDownLatch(1)
                service.listTargets(AiProviderCodec.encodeTargetListRequest(AiTargetListRequest(
                    org.autojs.plugin.ai.common.api.AiProtocolVersion(2, minor), 100)), object : IAiTargetListCallback.Stub() {
                    override fun onPage(metadata: ByteArray?) {
                        page.set(AiProviderCodec.decodeTargetPage(requireNotNull(metadata))); complete.countDown()
                    }
                    override fun onFailed(error: ByteArray?) { complete.countDown() }
                })
                assertTrue(complete.await(10, TimeUnit.SECONDS))
                val targets = requireNotNull(page.get()).targets
                assertTrue(targets.isNotEmpty())
                assertEquals(minor == 1, targets.any { AiProviderCapabilityId.VISION in it.capabilityIds })
            }
        } finally { context.unbindService(connection) }
    }
}
