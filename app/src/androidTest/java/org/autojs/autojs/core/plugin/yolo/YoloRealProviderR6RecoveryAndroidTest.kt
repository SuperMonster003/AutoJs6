package org.autojs.autojs.core.plugin.yolo

import android.content.ComponentName
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.plugin.yolo.api.YoloDevice
import org.autojs.plugin.yolo.api.YoloModelFormat
import org.autojs.plugin.yolo.api.YoloTask
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Opt-in R6 acceptance for a real provider process death.
 *
 * The old pinned session must die and a subsequent call on it must fail locally without restarting
 * or retrying the provider. Recovery is deliberately a new explicit target selection and session
 * open. Fixture bytes remain external to Host and Provider APKs and are copied through a shell pipe
 * into a short-lived Host cache directory.
 */
class YoloRealProviderR6RecoveryAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun providerDeathFailsPinnedSessionWithoutRetryThenExplicitReopenSucceeds() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "R6 YOLO recovery instrumentation is opt-in; pass -e $ARG_ENABLED true",
            arguments.getString(ARG_ENABLED) == "true",
        )
        assertEquals(HOST_PACKAGE, context.packageName)
        val shellFixtureRoot = requireNotNull(arguments.getString(ARG_FIXTURE_ROOT)) {
            "Missing -e $ARG_FIXTURE_ROOT <validated /data/local/tmp path>"
        }
        require(SAFE_FIXTURE_ROOT.matches(shellFixtureRoot)) {
            "R6 fixture root is outside the fixed /data/local/tmp namespace"
        }

        val privateFixtureRoot = File(context.cacheDir, "yolo-r6-${UUID.randomUUID()}")
        check(privateFixtureRoot.mkdir()) { "Unable to create private R6 fixture directory" }
        val manifest = copyShellFixture(shellFixtureRoot, privateFixtureRoot, "model-manifest-v1.json")
        val param = copyShellFixture(shellFixtureRoot, privateFixtureRoot, "model.ncnn.param")
        val bin = copyShellFixture(shellFixtureRoot, privateFixtureRoot, "model.ncnn.bin")
        val imageFile = copyShellFixture(shellFixtureRoot, privateFixtureRoot, "bus.jpg")

        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(context, PROVIDER_PACKAGE)
        assertTrue("The installed Provider has no signer fingerprint", fingerprints.isNotEmpty())
        val pluginState = PluginStateSnapshot.capture(context, PROVIDER_PACKAGE, fingerprints)
        var firstSession: YoloPluginHost.Session? = null
        var reopenedSession: YoloPluginHost.Session? = null
        var primaryFailure: Throwable? = null
        try {
            fingerprints.forEach { PluginAuthorizationStore.grant(context, PROVIDER_PACKAGE, it) }
            PluginEnableStore.setEnabled(context, PROVIDER_PACKAGE, true)

            val target = runBlocking { YoloPluginHost.requireExactTarget(context, PROVIDER_COMPONENT) }
            assertEquals(PROVIDER_COMPONENT, target.component)
            assertNotEquals("Provider unexpectedly shares the Host UID", context.applicationInfo.uid, target.providerUid)
            val model = listOf(
                YoloPluginHost.ModelArtifactFile("manifest", manifest.name, manifest),
                YoloPluginHost.ModelArtifactFile("ncnn-param", param.name, param),
                YoloPluginHost.ModelArtifactFile("ncnn-bin", bin.name, bin),
            )
            val openOptions = YoloPluginHost.OpenSessionOptions(
                task = YoloTask.DETECT,
                modelFormat = YoloModelFormat.NCNN,
                device = YoloDevice.CPU,
                cpuThreads = minOf(CPU_THREADS, target.capabilities.limits.maxCpuThreads),
                decoderId = DECODER_ID,
                timeoutMillis = minOf(SESSION_TIMEOUT_MILLIS, target.capabilities.limits.maxTimeoutMillis),
            )
            val detectOptions = YoloPluginHost.DetectOptions(
                confidenceThreshold = 0.25,
                iouThreshold = 0.45,
                maxDetections = 100,
                timeoutMillis = minOf(DETECT_TIMEOUT_MILLIS, target.capabilities.limits.maxTimeoutMillis),
            )

            val first = runBlocking { YoloPluginHost.openSession(context, target, model, openOptions) }
            firstSession = first
            assertTrue("Initial session is not a remote Binder", first.isRemoteBinder)
            val bitmap = requireNotNull(BitmapFactory.decodeFile(imageFile.absolutePath)) {
                "Unable to decode the externally staged R6 image"
            }
            try {
                val cold = runBlocking { first.detect(context, bitmap, detectOptions) }
                assertTrue("Cold inference returned no detections", cold.detections.isNotEmpty())
                val coldObservation = requireNotNull(first.lastDetectionObservation)
                assertEquals("Cold inference did not emit exactly one terminal", 1, coldObservation.terminalCount)

                val oldPids = providerPids()
                assertTrue("No live Provider process was visible before force-stop", oldPids.isNotEmpty())
                executeShellCommand("am force-stop $PROVIDER_PACKAGE")
                awaitSessionClosed(first)
                awaitProviderAbsent()

                val deadSessionFailure = runCatching {
                    runBlocking { first.detect(context, bitmap, detectOptions) }
                }.exceptionOrNull()
                assertTrue("A dead pinned session unexpectedly accepted another detect", deadSessionFailure != null)
                assertEquals(
                    "A post-death call created a new detection trace, implying hidden dispatch/retry",
                    coldObservation.sequence,
                    first.lastDetectionObservation?.sequence,
                )
                assertProviderAbsentFor(NO_RETRY_OBSERVATION_MILLIS)

                // Recovery begins only here: fresh explicit component selection, then a fresh session.
                val selectedAgain = runBlocking { YoloPluginHost.requireExactTarget(context, PROVIDER_COMPONENT) }
                assertEquals(PROVIDER_COMPONENT, selectedAgain.component)
                val reopened = runBlocking {
                    YoloPluginHost.openSession(context, selectedAgain, model, openOptions)
                }
                reopenedSession = reopened
                assertTrue("Explicitly reopened session is not a remote Binder", reopened.isRemoteBinder)
                val newPids = providerPids()
                assertTrue("Explicit reopen did not start the Provider process", newPids.isNotEmpty())
                val hot = runBlocking { reopened.detect(context, bitmap, detectOptions) }
                assertTrue("Hot inference after explicit reopen returned no detections", hot.detections.isNotEmpty())
                assertEquals("Hot inference did not emit exactly one terminal", 1, reopened.lastTerminalCount)
                reopened.close()
                reopened.close()
                val idle = awaitProviderIdle()

                val result = JSONObject()
                    .put("schemaVersion", 1)
                    .put("roadmapStage", "R6-D-RECOVERY")
                    .put("status", "PASS")
                    .put("component", target.component.flattenToString())
                    .put("providerUid", target.providerUid)
                    .put("coldDetectionCount", cold.detections.size)
                    .put("oldProviderPids", oldPids.sorted())
                    .put("forceStopCommand", "am force-stop $PROVIDER_PACKAGE")
                    .put("oldSessionClosed", first.isClosed)
                    .put("postDeathFailureType", deadSessionFailure?.javaClass?.name)
                    .put("postDeathFailureMessage", deadSessionFailure?.message)
                    .put("postDeathSequenceUnchanged", true)
                    .put("dispatchRetryAfterDeath", false)
                    .put("recoveryMode", "PROVIDER_KILL_EXPLICIT_REOPEN")
                    .put("newProviderPids", newPids.sorted())
                    .put("hotDetectionCount", hot.detections.size)
                    .put("explicitReopenSucceeded", true)
                    .put("idleAfterClose", idle.toJson())
                instrumentation.sendStatus(0, Bundle().apply {
                    putString(STATUS_RESULT_KEY, result.toString())
                })
            } finally {
                bitmap.recycle()
            }
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            runCatching { reopenedSession?.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { firstSession?.close() }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching { pluginState.restore(context) }.exceptionOrNull()?.let(cleanupFailures::add)
            runCatching {
                check(privateFixtureRoot.deleteRecursively()) { "Unable to remove private R6 fixture directory" }
            }.exceptionOrNull()?.let(cleanupFailures::add)
            cleanupFailures.forEach { cleanupFailure ->
                primaryFailure?.addSuppressed(cleanupFailure) ?: throw cleanupFailure
            }
        }
    }

    private fun copyShellFixture(shellRoot: String, destinationRoot: File, name: String): File {
        require(SAFE_LEAF.matches(name)) { "Unsafe R6 fixture leaf: $name" }
        val destination = File(destinationRoot, name)
        val descriptor = instrumentation.uiAutomation.executeShellCommand("cat $shellRoot/$name")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
        check(destination.isFile && destination.length() > 0L) { "External R6 fixture is missing or empty: $name" }
        return destination
    }

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    private fun providerPids(): Set<Int> = executeShellCommand("ps -A -o PID,NAME")
        .lineSequence()
        .map { it.trim().split(Regex("\\s+")) }
        .filter { it.size >= 2 }
        .mapNotNull { fields ->
            val processName = fields.last()
            fields.first().toIntOrNull()?.takeIf {
                processName == PROVIDER_PACKAGE || processName.startsWith("$PROVIDER_PACKAGE:")
            }
        }
        .toSet()

    private fun awaitSessionClosed(session: YoloPluginHost.Session) {
        val deadline = SystemClock.elapsedRealtime() + DEATH_TIMEOUT_MILLIS
        while (!session.isClosed && SystemClock.elapsedRealtime() < deadline) {
            CountDownLatch(1).await(POLL_MILLIS, TimeUnit.MILLISECONDS)
        }
        assertTrue("Pinned session did not observe real Provider Binder death", session.isClosed)
    }

    private fun awaitProviderAbsent() {
        val deadline = SystemClock.elapsedRealtime() + DEATH_TIMEOUT_MILLIS
        while (providerPids().isNotEmpty() && SystemClock.elapsedRealtime() < deadline) {
            CountDownLatch(1).await(POLL_MILLIS, TimeUnit.MILLISECONDS)
        }
        assertTrue("Provider process remained alive after force-stop", providerPids().isEmpty())
    }

    private fun assertProviderAbsentFor(durationMillis: Long) {
        val deadline = SystemClock.elapsedRealtime() + durationMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            assertTrue("Dead-session call restarted/retried the Provider", providerPids().isEmpty())
            CountDownLatch(1).await(POLL_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun awaitProviderIdle(): ProviderIdleSnapshot {
        val deadline = SystemClock.elapsedRealtime() + PROVIDER_IDLE_TIMEOUT_MILLIS
        var last: ProviderIdleSnapshot? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            val output = executeShellCommand("dumpsys activity service ${PROVIDER_COMPONENT.flattenToShortString()}")
            val line = output.lineSequence().map(String::trim)
                .firstOrNull { it.startsWith(PROVIDER_DIAGNOSTICS_PREFIX) }
            if (line != null) {
                val json = JSONObject(line.removePrefix(PROVIDER_DIAGNOSTICS_PREFIX))
                last = ProviderIdleSnapshot(
                    activeSessions = json.getInt("activeSessions"),
                    sessionGate = json.getBoolean("sessionGate"),
                    activeNativeHandles = json.getInt("activeNativeHandles"),
                    modelDirCount = json.getInt("modelDirCount"),
                )
                if (last.isIdle) return last
            }
            CountDownLatch(1).await(POLL_MILLIS, TimeUnit.MILLISECONDS)
        }
        throw AssertionError("Provider did not become idle after explicit reopen/close; last=$last")
    }

    private data class ProviderIdleSnapshot(
        val activeSessions: Int,
        val sessionGate: Boolean,
        val activeNativeHandles: Int,
        val modelDirCount: Int,
    ) {
        val isIdle: Boolean
            get() = activeSessions == 0 && !sessionGate && activeNativeHandles == 0 && modelDirCount == 0

        fun toJson() = JSONObject()
            .put("activeSessions", activeSessions)
            .put("sessionGate", sessionGate)
            .put("activeNativeHandles", activeNativeHandles)
            .put("modelDirCount", modelDirCount)
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
            check(enableEditor.commit()) { "Unable to restore YOLO Provider enable state" }

            val authorizationPreferences = context.getSharedPreferences(AUTHORIZATION_SP_NAME, Context.MODE_PRIVATE)
            val authorizationEditor = authorizationPreferences.edit()
            authorizations.forEach { (fingerprint, stored) ->
                val key = authorizationKey(packageName, fingerprint)
                authorizationEditor.remove(key)
                if (stored.present) authorizationEditor.putBoolean(key, stored.value)
            }
            check(authorizationEditor.commit()) { "Unable to restore YOLO Provider authorization state" }
        }

        companion object {
            fun capture(context: Context, packageName: String, fingerprints: List<String>): PluginStateSnapshot {
                val enablePreferences = context.getSharedPreferences(ENABLE_SP_NAME, Context.MODE_PRIVATE)
                val enabledKey = enableKey(packageName)
                val authorizationPreferences = context.getSharedPreferences(AUTHORIZATION_SP_NAME, Context.MODE_PRIVATE)
                return PluginStateSnapshot(
                    packageName = packageName,
                    enable = StoredBoolean(
                        present = enablePreferences.contains(enabledKey),
                        value = enablePreferences.getBoolean(enabledKey, false),
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

    companion object {
        private const val ARG_ENABLED = "yoloR6RecoveryEnabled"
        private const val ARG_FIXTURE_ROOT = "yoloR6FixtureRoot"
        private const val STATUS_RESULT_KEY = "yoloR6RecoveryResult"
        private const val HOST_PACKAGE = "org.autojs.autojs6"
        private const val PROVIDER_PACKAGE = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
        private const val ENABLE_SP_NAME = "plugin_center_enable_state"
        private const val AUTHORIZATION_SP_NAME = "plugin_center_authorization"
        private const val DECODER_ID = "ultralytics-detect"
        private const val CPU_THREADS = 4
        private const val SESSION_TIMEOUT_MILLIS = 120_000L
        private const val DETECT_TIMEOUT_MILLIS = 120_000L
        private const val DEATH_TIMEOUT_MILLIS = 10_000L
        private const val PROVIDER_IDLE_TIMEOUT_MILLIS = 10_000L
        private const val NO_RETRY_OBSERVATION_MILLIS = 750L
        private const val POLL_MILLIS = 50L
        private const val PROVIDER_DIAGNOSTICS_PREFIX = "AUTOJS6_YOLO_DIAGNOSTICS_V1="
        private val SAFE_FIXTURE_ROOT = Regex("^/data/local/tmp/autojs6-yolo-r6-[a-f0-9]{32}$")
        private val SAFE_LEAF = Regex("^[a-zA-Z0-9._-]+$")
        private val PROVIDER_COMPONENT = ComponentName(
            PROVIDER_PACKAGE,
            "$PROVIDER_PACKAGE.provider.YoloProviderService",
        )

        private fun enableKey(packageName: String) = "key_\$_enabled_plugin_\$_$packageName"
        private fun authorizationKey(packageName: String, fingerprint: String) =
            "key_\$_plugin_auth_\$_$packageName\$_$fingerprint"
    }
}
