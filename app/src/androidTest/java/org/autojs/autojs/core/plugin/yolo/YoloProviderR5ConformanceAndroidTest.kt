package org.autojs.autojs.core.plugin.yolo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in hostile-provider coverage for the production YOLO Host.
 *
 * The fake behavior is selected by a test-owned model manifest. This test never constructs a
 * YoloRemoteSession, callback bridge, or Binder proxy directly.
 */
class YoloProviderR5ConformanceAndroidTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun duplicateAndLateTerminalsFailClosedWithoutCompletingAnotherRequest() {
        withAuthorizedProvider { target ->
            withSession(target, DUPLICATE_TERMINAL) { session ->
                val result = detect(session)
                assertTrue("The fake duplicate scenario unexpectedly returned detections", result.detections.isEmpty())
                awaitClosed(session)
                assertEquals("Both hostile terminal callbacks were not observed", 2, session.lastTerminalCount)
            }

            withSession(target, LATE_OLD_TERMINAL) { session ->
                val first = detect(session)
                assertTrue(first.detections.isEmpty())
                val secondFailure = detectFailure(session)
                assertTrue(
                    "A late old terminal did not fail the active request through the callback protocol guard: $secondFailure",
                    secondFailure.hasCause<YoloCallbackProtocolException>(),
                )
                awaitClosed(session)
            }
        }
    }

    @Test
    fun busyAndBothTimeoutCancellationOutcomesAreDeterministic() {
        withAuthorizedProvider { target ->
            withSession(target, STALL_CANCEL_ACK) { session ->
                val firstBitmap = bitmap()
                val secondBitmap = bitmap()
                try {
                    runBlocking {
                        val first = async(Dispatchers.Default) {
                            session.detect(
                                context,
                                firstBitmap,
                                detectOptions(DETECT_WAIT_MILLIS, CANCEL_GRACE_MILLIS),
                            )
                        }
                        try {
                            withTimeout(OBSERVATION_WAIT_MILLIS) {
                                while (session.lastDetectionObservation == null) delay(POLL_MILLIS)
                            }
                            val busy = runCatching {
                                session.detect(
                                    context,
                                    secondBitmap,
                                    detectOptions(DETECT_WAIT_MILLIS, CANCEL_GRACE_MILLIS),
                                )
                            }.exceptionOrNull()
                            assertTrue(
                                "Concurrent detection did not fail with Host BUSY: $busy",
                                busy is YoloSessionBusyException,
                            )
                        } finally {
                            first.cancelAndJoin()
                        }
                    }
                    assertFalse("Acknowledged cancellation left the Host session uncertain", session.isClosed)
                    awaitTerminalCount(session, 1)
                } finally {
                    firstBitmap.recycle()
                    secondBitmap.recycle()
                }
            }

            withSession(target, STALL_CANCEL_ACK) { session ->
                val failure = detectFailure(
                    session,
                    detectOptions(HOST_TIMEOUT_MILLIS, CANCEL_GRACE_MILLIS),
                )
                val timeout = failure.findCause<YoloDetectionTimeoutException>()
                assertNotNull("Host timeout did not produce YoloDetectionTimeoutException: $failure", timeout)
                assertFalse("Acknowledged timeout cancellation was marked uncertain", checkNotNull(timeout).stateUncertain)
                assertFalse("Acknowledged timeout cancellation closed the reusable session", session.isClosed)
                awaitTerminalCount(session, 1)
            }

            withSession(target, STALL_CANCEL_NO_ACK) { session ->
                val failure = detectFailure(
                    session,
                    detectOptions(HOST_TIMEOUT_MILLIS, NO_ACK_GRACE_MILLIS),
                )
                val timeout = failure.findCause<YoloDetectionTimeoutException>()
                assertNotNull("Unacknowledged timeout did not produce YoloDetectionTimeoutException: $failure", timeout)
                assertTrue("Unacknowledged timeout was not marked uncertain", checkNotNull(timeout).stateUncertain)
                assertTrue("Uncertain timeout did not close the Host session", session.isClosed)
            }
        }
    }

    @Test
    fun wrongCallbackUidAndProviderDeathInvalidateThePinnedSession() {
        withAuthorizedProvider { target ->
            withSession(target, WRONG_CALLBACK_UID) { session ->
                val failure = detectFailure(session)
                assertTrue(
                    "Callback from an isolated UID was not rejected: $failure",
                    failure.hasCause<SecurityException>() &&
                        failure.causeMessages().any { it.contains("callback UID mismatch") },
                )
                awaitClosed(session)
            }

            withSession(target, PROVIDER_DEATH) { session ->
                val failure = detectFailure(session)
                assertTrue(
                    "Provider death did not fail the active request without retry: $failure",
                    failure.hasCause<YoloSessionClosedException>() ||
                        failure.hasCause<YoloDispatchException>(),
                )
                awaitClosed(session)
            }
        }
    }

    private fun withAuthorizedProvider(block: (YoloPluginHost.Target) -> Unit) {
        requireConformanceEnvironment()
        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(context, FAKE_PACKAGE)
        assertTrue("The fake YOLO provider has no signer fingerprint", fingerprints.isNotEmpty())
        val snapshot = PluginStateSnapshot.capture(context, FAKE_PACKAGE, fingerprints)
        var primaryFailure: Throwable? = null
        try {
            fingerprints.forEach { PluginAuthorizationStore.grant(context, FAKE_PACKAGE, it) }
            PluginEnableStore.setEnabled(context, FAKE_PACKAGE, true)
            val target = runBlocking {
                YoloPluginHost.requireExactTarget(context, FAKE_COMPONENT)
            }
            assertEquals(FAKE_COMPONENT, target.component)
            assertTrue("Conformance session must use a different provider UID", target.providerUid != context.applicationInfo.uid)
            block(target)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            runCatching { snapshot.restore(context) }.exceptionOrNull()?.let { cleanup ->
                primaryFailure?.addSuppressed(cleanup) ?: throw cleanup
            }
        }
    }

    private fun withSession(
        target: YoloPluginHost.Target,
        scenario: String,
        block: (YoloPluginHost.Session) -> Unit,
    ) {
        val manifest = createScenarioManifest(scenario)
        var session: YoloPluginHost.Session? = null
        var primaryFailure: Throwable? = null
        try {
            val opened = runBlocking {
                YoloPluginHost.openSession(
                    context = context,
                    target = target,
                    modelArtifacts = listOf(
                        YoloPluginHost.ModelArtifactFile(
                            role = "manifest",
                            logicalName = "hostile-conformance.json",
                            file = manifest,
                        ),
                    ),
                    options = YoloPluginHost.OpenSessionOptions(
                        cpuThreads = 1,
                        decoderId = "ultralytics-detect",
                        timeoutMillis = OPEN_TIMEOUT_MILLIS,
                    ),
                )
            }
            session = opened
            assertTrue("Host retained the fake manifest descriptor", opened.modelDescriptorsClosedAfterOpen)
            assertTrue("Conformance session did not use a remote Binder", opened.isRemoteBinder)
            block(opened)
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching { session?.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            if (manifest.exists() && !manifest.delete()) {
                cleanupFailures += IllegalStateException("Unable to delete fake YOLO scenario manifest")
            }
            cleanupFailures.forEach { cleanup ->
                primaryFailure?.addSuppressed(cleanup) ?: throw cleanup
            }
        }
    }

    private fun detect(
        session: YoloPluginHost.Session,
        options: YoloPluginHost.DetectOptions = detectOptions(DETECT_WAIT_MILLIS, CANCEL_GRACE_MILLIS),
    ) = bitmap().let { image ->
        try {
            runBlocking { session.detect(context, image, options) }
        } finally {
            image.recycle()
        }
    }

    private fun detectFailure(
        session: YoloPluginHost.Session,
        options: YoloPluginHost.DetectOptions = detectOptions(DETECT_WAIT_MILLIS, CANCEL_GRACE_MILLIS),
    ): Throwable = runCatching { detect(session, options) }.exceptionOrNull()
        ?: throw AssertionError("Hostile YOLO detection unexpectedly succeeded")

    private fun createScenarioManifest(scenario: String): File =
        File.createTempFile("yolo-r5-hostile-", ".json", context.cacheDir).apply {
            writeText(
                JSONObject()
                    .put("schemaVersion", 1)
                    .put("kind", MANIFEST_KIND)
                    .put("scenario", scenario)
                    .toString(),
                Charsets.UTF_8,
            )
        }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(17, 34, 51))
    }

    private fun awaitClosed(session: YoloPluginHost.Session) {
        val deadline = SystemClock.elapsedRealtime() + OBSERVATION_WAIT_MILLIS
        while (!session.isClosed && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_MILLIS)
        }
        assertTrue("Host session did not close after a fatal provider fault", session.isClosed)
    }

    private fun awaitTerminalCount(session: YoloPluginHost.Session, expected: Int) {
        val deadline = SystemClock.elapsedRealtime() + OBSERVATION_WAIT_MILLIS
        while (session.lastTerminalCount < expected && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(POLL_MILLIS)
        }
        assertEquals("Unexpected Host terminal callback count", expected, session.lastTerminalCount)
    }

    private fun requireConformanceEnvironment() {
        assumeTrue(
            "R5 YOLO hostile conformance is opt-in; pass -e $ARG_ENABLED true",
            InstrumentationRegistry.getArguments().getString(ARG_ENABLED) == "true",
        )
        assertEquals(HOST_PACKAGE, context.packageName)
        assertEquals(
            "Fake YOLO provider and Host must use the same debug/test signer",
            PackageManager.SIGNATURE_MATCH,
            context.packageManager.checkSignatures(HOST_PACKAGE, FAKE_PACKAGE),
        )
    }

    private data class StoredBoolean(val present: Boolean, val value: Boolean)

    private data class PluginStateSnapshot(
        val packageName: String,
        val enable: StoredBoolean,
        val authorizations: Map<String, StoredBoolean>,
    ) {
        fun restore(context: Context) {
            val enablePreferences = context.getSharedPreferences(ENABLE_SP_NAME, Context.MODE_PRIVATE)
            val enableEditor = enablePreferences.edit().remove(enableKey(packageName))
            if (enable.present) enableEditor.putBoolean(enableKey(packageName), enable.value)
            check(enableEditor.commit()) { "Unable to restore fake YOLO provider enable state" }

            val authorizationPreferences = context.getSharedPreferences(AUTHORIZATION_SP_NAME, Context.MODE_PRIVATE)
            val authorizationEditor = authorizationPreferences.edit()
            authorizations.forEach { (fingerprint, stored) ->
                val key = authorizationKey(packageName, fingerprint)
                authorizationEditor.remove(key)
                if (stored.present) authorizationEditor.putBoolean(key, stored.value)
            }
            check(authorizationEditor.commit()) { "Unable to restore fake YOLO provider authorization state" }
        }

        companion object {
            fun capture(context: Context, packageName: String, fingerprints: List<String>): PluginStateSnapshot {
                val enablePreferences = context.getSharedPreferences(ENABLE_SP_NAME, Context.MODE_PRIVATE)
                val enableKey = enableKey(packageName)
                val authorizationPreferences = context.getSharedPreferences(AUTHORIZATION_SP_NAME, Context.MODE_PRIVATE)
                return PluginStateSnapshot(
                    packageName = packageName,
                    enable = StoredBoolean(
                        present = enablePreferences.contains(enableKey),
                        value = enablePreferences.getBoolean(enableKey, false),
                    ),
                    authorizations = fingerprints.associateWith { fingerprint ->
                        val key = authorizationKey(packageName, fingerprint)
                        StoredBoolean(
                            present = authorizationPreferences.contains(key),
                            value = authorizationPreferences.getBoolean(key, false),
                        )
                    },
                )
            }
        }
    }

    private companion object {
        const val ARG_ENABLED = "yoloR5ConformanceEnabled"
        const val HOST_PACKAGE = "org.autojs.autojs6"
        const val FAKE_PACKAGE = "org.autojs.plugin.yolo.fake"
        const val FAKE_SERVICE = "$FAKE_PACKAGE.service.FakeYoloProviderService"
        const val MANIFEST_KIND = "autojs6-yolo-hostile-conformance-v1"
        const val DUPLICATE_TERMINAL = "duplicate-terminal"
        const val LATE_OLD_TERMINAL = "late-old-terminal"
        const val STALL_CANCEL_ACK = "stall-cancel-ack"
        const val STALL_CANCEL_NO_ACK = "stall-cancel-no-ack"
        const val WRONG_CALLBACK_UID = "wrong-callback-uid"
        const val PROVIDER_DEATH = "provider-death"
        const val ENABLE_SP_NAME = "plugin_center_enable_state"
        const val AUTHORIZATION_SP_NAME = "plugin_center_authorization"
        const val OPEN_TIMEOUT_MILLIS = 15_000L
        const val DETECT_WAIT_MILLIS = 20_000L
        const val HOST_TIMEOUT_MILLIS = 150L
        const val CANCEL_GRACE_MILLIS = 2_000L
        const val NO_ACK_GRACE_MILLIS = 250L
        const val OBSERVATION_WAIT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L

        val FAKE_COMPONENT = ComponentName(FAKE_PACKAGE, FAKE_SERVICE)

        fun detectOptions(timeoutMillis: Long, cancellationGraceMillis: Long) =
            YoloPluginHost.DetectOptions(
                confidenceThreshold = 0.25,
                iouThreshold = 0.45,
                maxDetections = 1,
                timeoutMillis = timeoutMillis,
                cancellationGraceMillis = cancellationGraceMillis,
            )

        fun enableKey(packageName: String) = "key_\$_enabled_plugin_\$_$packageName"

        fun authorizationKey(packageName: String, fingerprint: String) =
            "key_\$_plugin_auth_\$_$packageName\$_$fingerprint"
    }
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return current
        current = current.cause
    }
    return null
}

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean = findCause<T>() != null

private fun Throwable.causeMessages(): List<String> {
    val messages = mutableListOf<String>()
    var current: Throwable? = this
    while (current != null) {
        current.message?.let(messages::add)
        current = current.cause
    }
    return messages
}
