package org.autojs.autojs.core.plugin.compose

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ComposeUiPluginCodeCacheTest {
    @Test fun anOlderInventoryFinishingLaterCannotEvictTheNewApksNativeOwner() {
        val cache = HashMap<ComposeUiPluginLoader.Package, Any>()
        val oldIdentity = ComposeUiPluginLoader.Package("compose.test", 1, 1, "/old/base.apk", "Factory")
        val newIdentity = oldIdentity.copy(version = 2, updated = 2, apk = "/new/base.apk")
        val oldInventoryRead = CountDownLatch(1)
        val allowOldLoad = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val creations = AtomicInteger()
        val oldOwner = AtomicReference<Any?>()
        fun load(identity: ComposeUiPluginLoader.Package) = ComposeUiPluginLoader.loadCachedIdentity(cache, identity) {
            creations.incrementAndGet()
            Any()
        }
        val oldCaller = Thread {
            try {
                // A has read E1 from PackageManager but has not entered the shared code cache.
                oldInventoryRead.countDown()
                check(allowOldLoad.await(5, TimeUnit.SECONDS))
                oldOwner.set(load(oldIdentity))
            } catch (error: Throwable) { failure.set(error) }
        }
        oldCaller.start()
        try {
            assertTrue(oldInventoryRead.await(5, TimeUnit.SECONDS))
            // B reads and loads E2 while A still holds its earlier inventory value.
            val newOwner = load(newIdentity)
            allowOldLoad.countDown()
            oldCaller.join(5000)
            assertFalse("The delayed loader must finish", oldCaller.isAlive)
            failure.get()?.let { throw AssertionError(it) }
            assertNotSame(oldOwner.get(), newOwner)
            assertSame("C reuses B's loader instead of creating a second JNI owner", newOwner, load(newIdentity))
            assertSame(oldOwner.get(), load(oldIdentity))
            assertEquals("Exactly one construction per APK identity", 2, creations.get())
            assertEquals(setOf(oldIdentity, newIdentity), cache.keys)
        } finally {
            allowOldLoad.countDown()
            oldCaller.join(5000)
        }
    }
}
