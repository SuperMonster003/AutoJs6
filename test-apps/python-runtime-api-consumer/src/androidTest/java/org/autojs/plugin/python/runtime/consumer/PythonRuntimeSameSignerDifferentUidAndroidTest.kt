package org.autojs.plugin.python.runtime.consumer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.python.runtime.api.IPythonExecutionCallback
import org.autojs.plugin.python.runtime.api.IPythonExecutionSession
import org.autojs.plugin.python.runtime.api.IPythonRuntimeProvider
import org.autojs.plugin.python.runtime.api.IPythonSessionOpenCallback
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.api.PythonProtocolVersion
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonRuntimeCodec
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import org.autojs.plugin.python.runtime.api.PythonSha256
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Exact-device hostile-caller fixture for the real Python Runtime provider.
 *
 * The target APK is signed like the debug host and plugin, but owns a distinct application UID.
 * This proves that the provider checks the canonical host package and UID at every provider entry,
 * instead of treating possession of the signature permission as sufficient authority. It does not
 * exercise a differently signed caller or relay an admitted session handle.
 */
class PythonRuntimeSameSignerDifferentUidAndroidTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sameSignerDifferentUidIsRejectedAtEveryProviderEntry() {
        val packageManager = context.packageManager
        val targetPackage = context.packageName
        val targetUid = context.applicationInfo.uid
        val hostUid = packageUid(packageManager, HOST_PACKAGE)

        assertEquals(TARGET_PACKAGE, targetPackage)
        assertEquals("Instrumentation must execute as the consumer target UID", targetUid, Process.myUid())
        assertNotEquals("Fixture must not run as the canonical host UID", hostUid, targetUid)
        val targetUidPackages = packageManager.getPackagesForUid(targetUid)?.toSet().orEmpty()
        assertTrue("Consumer UID does not own its target package", targetPackage in targetUidPackages)
        assertFalse(
            "Consumer UID unexpectedly owns the canonical host package",
            HOST_PACKAGE in targetUidPackages,
        )
        assertEquals(
            "Fixture and host must share the debug signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(targetPackage, HOST_PACKAGE),
        )
        assertEquals(
            "Fixture and real plugin must share the signature-permission signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(targetPackage, REAL_PLUGIN_PACKAGE),
        )

        val expectedComponent = ComponentName(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE)
        val provider = AtomicReference<IPythonRuntimeProvider?>()
        val connectionFailure = AtomicReference<Throwable?>()
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                runCatching {
                    check(name == expectedComponent) {
                        "Bound component $name differs from $expectedComponent"
                    }
                    provider.set(requireNotNull(IPythonRuntimeProvider.Stub.asInterface(service)))
                }.onFailure { error -> connectionFailure.compareAndSet(null, error) }
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connectionFailure.compareAndSet(
                    null,
                    AssertionError("Real Python Runtime disconnected before provider-entry checks: $name"),
                )
                connected.countDown()
            }

            override fun onNullBinding(name: ComponentName?) {
                connectionFailure.compareAndSet(
                    null,
                    AssertionError("Real Python Runtime returned a null binding: $name"),
                )
                connected.countDown()
            }

            override fun onBindingDied(name: ComponentName?) {
                connectionFailure.compareAndSet(
                    null,
                    AssertionError("Real Python Runtime binding died before provider-entry checks: $name"),
                )
                connected.countDown()
            }
        }
        val intent = Intent().setComponent(expectedComponent)
        assertNull("Pinned hostile-caller bind must be action-null", intent.action)

        var bound = false
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            assertTrue("Explicit real-plugin bind was rejected before Binder authorization", bound)
            assertTrue(
                "Timed out waiting for the exact real-plugin binding",
                connected.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            connectionFailure.get()?.let { error ->
                throw AssertionError("Unable to bind the exact real Python Runtime provider").apply {
                    initCause(error)
                }
            }
            val exactProvider = requireNotNull(provider.get()) {
                "Exact real Python Runtime provider was not connected"
            }

            assertSynchronousSecurityException("getRuntimeInfo") {
                exactProvider.runtimeInfo
            }
            assertSynchronousSecurityException("getCapabilities") {
                exactProvider.capabilities
            }

            val callbackCount = AtomicInteger(0)
            val receivedResultDescriptorCount = AtomicInteger(0)
            val admissionCallback = object : IPythonSessionOpenCallback.Stub() {
                override fun onOpened(session: IPythonExecutionSession?) {
                    callbackCount.incrementAndGet()
                    session?.let { unexpected -> runCatching { unexpected.close() } }
                }

                override fun onRejected(error: ByteArray?) {
                    callbackCount.incrementAndGet()
                }
            }
            val executionCallback = object : IPythonExecutionCallback.Stub() {
                override fun onStarted(metadata: ByteArray?) {
                    callbackCount.incrementAndGet()
                }

                override fun onStdout(chunk: ByteArray?) {
                    callbackCount.incrementAndGet()
                }

                override fun onStderr(chunk: ByteArray?) {
                    callbackCount.incrementAndGet()
                }

                override fun onResult(
                    result: ByteArray?,
                    descriptors: Array<out ParcelFileDescriptor?>?,
                ) {
                    callbackCount.incrementAndGet()
                    val received = descriptors?.filterNotNull().orEmpty()
                    receivedResultDescriptorCount.addAndGet(received.size)
                    received.forEach { descriptor -> runCatching { descriptor.close() } }
                }

                override fun onFailed(error: ByteArray?) {
                    callbackCount.incrementAndGet()
                }

                override fun onCancelled(cancellation: ByteArray?) {
                    callbackCount.incrementAndGet()
                }
            }
            val source = HOSTILE_SOURCE.toByteArray(Charsets.UTF_8)
            val request = PythonExecutionRequest(
                requestId = PythonRequestId.fromUuid(UUID.randomUUID()),
                protocolVersion = PythonProtocolVersion(
                    PythonRuntimeContract.PROTOCOL_MAJOR,
                    PythonRuntimeContract.PROTOCOL_MINOR,
                ),
                entryPoint = ENTRY_POINT,
                source = PythonPayloadReference(
                    kind = PythonPayloadKind.SOURCE,
                    descriptorIndex = 0,
                    declaredLengthBytes = source.size.toLong(),
                    sha256 = PythonSha256.digest(source),
                ),
                timeoutMillis = 1L,
                maxOutputBytes = 1L,
                maxOutputChunkBytes = 1,
                maxOutputChunks = 1L,
            )
            PythonRuntimeValidation.validateRequest(request)
            PythonRuntimeValidation.validateDescriptorReferences(request, 1)
            val encodedRequest = PythonRuntimeCodec.encodeExecutionRequest(request)
            assertEquals(request, PythonRuntimeCodec.decodeExecutionRequest(encodedRequest))

            val sourceFile = File.createTempFile("python-runtime-hostile-", ".py", context.cacheDir)
            try {
                sourceFile.writeBytes(source)
                val sourceDescriptor = ParcelFileDescriptor.open(
                    sourceFile,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
                val callerFileDescriptor = sourceDescriptor.fileDescriptor
                try {
                    assertTrue("Hostile source PFD was not valid before dispatch", callerFileDescriptor.valid())
                    assertSynchronousSecurityException("openSession") {
                        exactProvider.openSession(
                            encodedRequest,
                            arrayOf(sourceDescriptor),
                            admissionCallback,
                            executionCallback,
                        )
                    }
                    assertTrue(
                        "Security rejection closed the caller-owned source PFD",
                        callerFileDescriptor.valid(),
                    )
                } finally {
                    sourceDescriptor.close()
                    assertFalse("Caller-owned source PFD remained valid after close", callerFileDescriptor.valid())
                }
            } finally {
                assertTrue(
                    "Unable to remove the hostile-caller source snapshot",
                    !sourceFile.exists() || sourceFile.delete(),
                )
            }
            assertEquals("Rejected openSession emitted an asynchronous callback", 0, callbackCount.get())
            assertEquals(
                "Rejected openSession returned a result file descriptor",
                0,
                receivedResultDescriptorCount.get(),
            )
        } finally {
            if (bound) context.unbindService(connection)
        }
    }

    /**
     * Run only after the consumer target and its androidTest APK have both been signed by an
     * explicit TEST_ONLY signer which differs from the frozen host/plugin signer. The fixture
     * intentionally contains no key material and never signs an artifact itself.
     */
    @Test
    fun differentSignerCannotAcquirePluginPermissionOrBindProvider() {
        val packageManager = context.packageManager
        val targetPackage = context.packageName
        assertEquals(TARGET_PACKAGE, targetPackage)
        assertNotEquals(
            "Different-signer fixture unexpectedly matches the host signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(targetPackage, HOST_PACKAGE),
        )
        assertNotEquals(
            "Different-signer fixture unexpectedly matches the plugin signer",
            PackageManager.SIGNATURE_MATCH,
            packageManager.checkSignatures(targetPackage, REAL_PLUGIN_PACKAGE),
        )
        assertEquals(
            "Different-signer fixture unexpectedly acquired the signature permission",
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(PLUGIN_PERMISSION),
        )

        val callbackCount = AtomicInteger(0)
        val callback = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                callbackCount.incrementAndGet()
                callback.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                callbackCount.incrementAndGet()
                callback.countDown()
            }

            override fun onNullBinding(name: ComponentName?) {
                callbackCount.incrementAndGet()
                callback.countDown()
            }

            override fun onBindingDied(name: ComponentName?) {
                callbackCount.incrementAndGet()
                callback.countDown()
            }
        }
        val intent = Intent().setComponent(ComponentName(REAL_PLUGIN_PACKAGE, REAL_PLUGIN_SERVICE))
        assertNull("Different-signer probe must use an action-null exact component", intent.action)

        var bound = false
        var deniedSynchronously = false
        try {
            try {
                bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (_: SecurityException) {
                deniedSynchronously = true
            }
            if (bound) {
                fail("Different-signer caller unexpectedly passed the signature-permission bind")
            }
            assertTrue(
                "Different-signer bind was neither synchronously denied nor rejected",
                deniedSynchronously || !bound,
            )
            assertFalse(
                "Different-signer bind unexpectedly delivered a service callback",
                callback.await(DENIED_BIND_OBSERVATION_MILLIS, TimeUnit.MILLISECONDS),
            )
            assertEquals("Different-signer bind emitted a callback", 0, callbackCount.get())
        } finally {
            if (bound) context.unbindService(connection)
        }
    }

    private fun assertSynchronousSecurityException(label: String, action: () -> Unit) {
        try {
            action()
            fail("$label did not synchronously reject the same-signer different-UID caller")
        } catch (_: SecurityException) {
            // Expected: Binder propagates the provider's entry-point authorization failure.
        }
    }

    @Suppress("DEPRECATION")
    private fun packageUid(packageManager: PackageManager, packageName: String): Int =
        packageManager.getApplicationInfo(packageName, 0).uid

    private companion object {
        const val TARGET_PACKAGE = "org.autojs.plugin.python.runtime.consumer"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val REAL_PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
        const val REAL_PLUGIN_SERVICE = "$REAL_PLUGIN_PACKAGE.service.PythonRuntimePluginService"
        const val BIND_TIMEOUT_SECONDS = 10L
        const val ENTRY_POINT = "main.py"
        const val HOSTILE_SOURCE = "print('must not execute')\n"
        const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
        const val DENIED_BIND_OBSERVATION_MILLIS = 750L
    }
}
