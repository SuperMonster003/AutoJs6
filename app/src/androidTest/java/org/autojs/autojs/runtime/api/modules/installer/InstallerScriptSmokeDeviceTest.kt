package org.autojs.autojs.runtime.api.modules.installer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginStateChangeRegistry
import org.autojs.autojs.core.plugin.installer.InstallerPluginHost
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.autojs.plugin.installer.api.InstallerActions
import org.autojs.plugin.installer.api.InstallerIds
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Actual host UID -> Rhino -> public installer Binder smoke tests. No default-installer setting
 * or existing application is changed. `installer.smoke.authorizer=shizuku` opts into the three
 * install forms using the bundled no-code fixture, only if Shizuku is already running/granted.
 * `installer.smoke.disablePlugin=true` opts into temporarily disabling the official plugin in
 * the host's plugin preference; the exact preference is restored in finally.
 * `installer.smoke.enablePlugin=true` temporarily enables that plugin for each test, restores
 * the exact original preference afterward, and turns an unavailable probe into a test failure.
 *
 * Class: org.autojs.autojs.runtime.api.modules.installer.InstallerScriptSmokeDeviceTest
 */
class InstallerScriptSmokeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val arguments get() = InstrumentationRegistry.getArguments()
    private var enableSnapshot: PluginPreferenceSnapshot? = null

    @Before fun prepareOptedInPlugin() {
        if (arguments.getString("installer.smoke.enablePlugin") != "true") return
        enableSnapshot = PluginPreferenceSnapshot()
        PluginEnableStore.setEnabled(context, InstallerIds.DEFAULT_PACKAGE_NAME, true)
        assertAvailableForOptIn()
    }

    @After fun restoreOptedInPlugin() {
        enableSnapshot?.close()
        enableSnapshot = null
    }

    @Test fun statusInspectAndArgumentErrorsUseTheRealScriptRuntime() {
        assumeAvailable()
        Inputs().use { inputs -> assertReport(runScript("inspect", inputs.config())) }
    }

    @Test fun shizukuSyncAsyncAndSessionFormsInstallOnlyOwnedFixtures() = synchronized(FIXTURE_LOCK) {
        assumeTrue("opt in with installer.smoke.authorizer=shizuku", arguments.getString("installer.smoke.authorizer") == "shizuku")
        assumeAvailable()
        val state = runBlocking { InstallerPluginHost.getAuthorizerState(context, "shizuku") }
        assertTrue("Explicit Shizuku opt-in requires an already available, running and granted connection", state.available && state.running && state.granted)
        FixtureOwnership().use { ownership ->
            Inputs().use { inputs ->
                ownership.started = true
                val report = runScript("forms", inputs.config(), timeoutMillis = 180_000)
                assertReport(report)
                assertEquals(3, report.getInt("installForms"))
                assertTrue("script also uninstalled its owned fixture", report.getBoolean("uninstalled"))
            }
        }
    }

    @Test fun aShortWaitDoesNotCancelAndExplicitCancelClosesTheBinderSource() {
        assumeAvailable()
        PipeSource().use { source ->
            val report = runScript("cancel", JSONObject().put("pipe", source.uri.toString()), timeoutMillis = 45_000)
            assertReport(report)
            assertEquals(1, report.getInt("terminalCount"))
            source.awaitReadersClosed()
        }
    }

    @Test fun stoppingAScriptClosesTheBinderSourceWithoutLateCallbacks() {
        assumeAvailable()
        PipeSource().use { source ->
            val running = startScript("stop", JSONObject().put("pipe", source.uri.toString()))
            try {
                await(20_000, "script did not observe Binder preparing") {
                    running.failure.get()?.let { throw AssertionError("stop script failed", it) }
                    running.engine.runtime.getProperty("installer.smoke.ready") == true
                }
                assertTrue(source.probe().getBoolean("opened"))
                assertNull("blocked source cannot already have completed", running.engine.runtime.getProperty("installer.smoke.terminal"))
                running.engine.forceStop()
                await(10_000, "script thread did not stop") { !running.engine.thread.isAlive }
                val callbacks = running.engine.runtime.getProperty("installer.smoke.callbacks")
                source.awaitReadersClosed()
                SystemClock.sleep(500)
                assertEquals("no listener runs after script exit", callbacks, running.engine.runtime.getProperty("installer.smoke.callbacks"))
                assertNull("no terminal callback is delivered after stop", running.engine.runtime.getProperty("installer.smoke.terminal"))
            } finally { running.close() }
        }
    }

    @Test fun unavailablePluginRejectsWithActualInstallerErrorObjects() = synchronized(FIXTURE_LOCK) {
        val toggle = arguments.getString("installer.smoke.disablePlugin") == "true"
        val available = runBlocking { InstallerPluginHost.isAvailable(context) }
        assumeTrue("plugin is available; opt in with installer.smoke.disablePlugin=true", !available || toggle)
        val matches = context.packageManager.queryIntentServices(
            Intent(InstallerActions.SERVICE_ACTION).addCategory(InstallerActions.SERVICE_CATEGORY), 0,
        ).mapNotNull { it.serviceInfo?.packageName }.toSet()
        assumeTrue("do not alter third-party installer preferences", matches.all { it == InstallerIds.DEFAULT_PACKAGE_NAME })
        val preferences = context.getSharedPreferences(PLUGIN_PREFERENCES, Context.MODE_PRIVATE)
        val key = "key_\$_enabled_plugin_\$_${InstallerIds.DEFAULT_PACKAGE_NAME}"
        val present = preferences.contains(key)
        val original = preferences.getBoolean(key, true)
        try {
            if (toggle && available) PluginEnableStore.setEnabled(context, InstallerIds.DEFAULT_PACKAGE_NAME, false)
            assertFalse(runBlocking { InstallerPluginHost.isAvailable(context) })
            Inputs().use { inputs -> assertReport(runScript("unavailable", inputs.config())) }
        } finally {
            if (toggle && available) {
                val edit = preferences.edit()
                if (present) edit.putBoolean(key, original) else edit.remove(key)
                check(edit.commit()) { "Failed to restore installer plugin preference" }
                PluginStateChangeRegistry.notifyChanged(InstallerIds.DEFAULT_PACKAGE_NAME)
            }
        }
    }

    private fun assumeAvailable() {
        if (arguments.getString("installer.smoke.enablePlugin") == "true") assertAvailableForOptIn()
        else assumeTrue("a trusted compatible installer plugin is required", runBlocking { InstallerPluginHost.isAvailable(context) })
    }

    private fun assertAvailableForOptIn() {
        if (runBlocking { InstallerPluginHost.isAvailable(context) }) return
        val diagnostic = runCatching { runBlocking { InstallerPluginHost.probe(context, InstallerIds.DEFAULT_PACKAGE_NAME) } }
            .fold(onSuccess = { "probe succeeded: $it; enabled=${PluginEnableStore.isEnabled(context, InstallerIds.DEFAULT_PACKAGE_NAME)}" },
                onFailure = { "${it.javaClass.simpleName}: ${it.message}" })
        fail("Official installer remains unavailable after explicit test opt-in: $diagnostic")
    }

    private inner class PluginPreferenceSnapshot : Closeable {
        private val preferences = context.getSharedPreferences(PLUGIN_PREFERENCES, Context.MODE_PRIVATE)
        private val key = "key_\$_enabled_plugin_\$_${InstallerIds.DEFAULT_PACKAGE_NAME}"
        private val present = preferences.contains(key)
        private val original = preferences.getBoolean(key, true)
        override fun close() {
            val edit = preferences.edit()
            if (present) edit.putBoolean(key, original) else edit.remove(key)
            check(edit.commit()) { "Failed to restore original installer plugin preference" }
            PluginStateChangeRegistry.notifyChanged(InstallerIds.DEFAULT_PACKAGE_NAME)
        }
    }

    private fun assertReport(report: JSONObject) {
        Log.i(TAG, report.toString())
        assertTrue("script failed: ${report.optString("error")}", report.optBoolean("ok"))
    }

    private inner class Running(val execution: ScriptExecution, val finished: CountDownLatch, val failure: AtomicReference<Throwable?>) : Closeable {
        val engine: RhinoJavaScriptEngine get() = execution.engine as RhinoJavaScriptEngine
        override fun close() {
            if (engine.thread.isAlive) engine.forceStop()
            engine.thread.join(10_000)
            assertFalse("smoke script is still alive", engine.thread.isAlive)
        }
    }

    private fun startScript(mode: String, config: JSONObject): Running {
        config.put("mode", mode).put("packageName", FIXTURE_PACKAGE)
        val source = instrumentation.context.assets.open("installer/smoke.js").bufferedReader().use { it.readText() }
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onStart(execution: ScriptExecution) { started.countDown() }
            override fun onSuccess(execution: ScriptExecution, result: Any?) { finished.countDown() }
            override fun onException(execution: ScriptExecution, error: Throwable) { failure.set(error); started.countDown(); finished.countDown() }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(
            StringScriptSource("installer-smoke-$mode", "var INSTALLER_SMOKE = $config;\n$source"), listener, ExecutionConfig(),
        )
        if (!started.await(15_000, TimeUnit.MILLISECONDS)) {
            (execution.engine as? RhinoJavaScriptEngine)?.forceStop()
            throw AssertionError("$mode script engine did not start")
        }
        failure.get()?.let { throw AssertionError("$mode script could not start", it) }
        return Running(execution, finished, failure)
    }

    private fun runScript(mode: String, config: JSONObject, timeoutMillis: Long = 90_000): JSONObject = startScript(mode, config).use { running ->
        assertTrue("$mode did not finish within $timeoutMillis ms", running.finished.await(timeoutMillis, TimeUnit.MILLISECONDS))
        running.failure.get()?.let { throw AssertionError("$mode script failed", it) }
        val json = running.engine.runtime.getProperty("installer.smoke.report") as? String ?: throw AssertionError("$mode left no report")
        JSONObject(json)
    }

    private inner class PipeSource : Closeable {
        val uri: Uri = Uri.parse(requireNotNull(context.contentResolver.call(Uri.parse("content://${InstallerSmokePipeProvider.AUTHORITY}"), "create", null, null)?.getString("uri")))
        fun probe() = requireNotNull(context.contentResolver.call(uri, "probe", uri.lastPathSegment, null))
        fun awaitReadersClosed() = await(15_000, "host or plugin still owns a blocked source reader") { probe().getBoolean("readersClosed") }
        override fun close() { context.contentResolver.call(uri, "close", uri.lastPathSegment, null) }
    }

    private inner class Inputs : Closeable {
        private val directory = File(context.cacheDir, "installer-smoke-${UUID.randomUUID()}").apply { check(mkdir()) }
        private val v1 = copy("fixture-v1.apk", "fec583a389978fdc298e9d85979b95feceb93e6fb3fd3f581511c826401e8b69")
        private val v2 = copy("fixture-v2.apk", "dc3716a9c54e63219ed621ef06f68c4088f54630e4b4c167aebfdda653a4bb38")
        private val bad = protect { File(directory, "not-an-apk.apk").apply { writeText("installer script smoke: deliberately invalid archive") } }
        private val aab = protect { File(directory, "minimal.aab").also { file ->
            val manifest = Proto().apply {
                message(1) {
                    message(1) { string(1, "android"); string(2, ANDROID_NAMESPACE) }
                    string(3, "manifest")
                    message(4) { string(2, "package"); string(3, FIXTURE_PACKAGE) }
                    message(4) { string(1, ANDROID_NAMESPACE); string(2, "versionCode"); string(3, "7") }
                    message(4) { string(1, ANDROID_NAMESPACE); string(2, "versionName"); string(3, "7.0") }
                }
            }.bytes()
            ZipOutputStream(file.outputStream()).use { output ->
                listOf("BundleConfig.pb" to byteArrayOf(), "base/manifest/AndroidManifest.xml" to manifest, "feature/manifest/AndroidManifest.xml" to manifest).forEach { (name, content) ->
                    output.putNextEntry(ZipEntry(name)); output.write(content); output.closeEntry()
                }
            }
        } }
        private fun copy(name: String, expectedHash: String): File = protect { File(directory, name).also { file ->
            instrumentation.context.assets.open("installer/$name").use { input -> file.outputStream().use(input::copyTo) }
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
            check(hash == expectedHash) { "Fixture bytes differ from the reviewed no-code APK" }
            val info = requireNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath, 0))
            check(info.packageName == FIXTURE_PACKAGE) { "Unexpected fixture package" }
        } }
        private fun <T> protect(body: () -> T): T = try { body() } catch (failure: Throwable) {
            directory.deleteRecursively()
            throw failure
        }
        fun config() = JSONObject().put("v1", v1.absolutePath).put("v2", v2.absolutePath).put("bad", bad.absolutePath).put("aab", aab.absolutePath)
        override fun close() {
            check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            check(directory.deleteRecursively()) { "Cannot remove smoke input directory" }
        }
    }

    /** Refuses any installed package or retained data across all users before taking ownership. */
    private inner class FixtureOwnership : Closeable {
        private val users = Regex("UserInfo\\{(\\d+):").findAll(shell("pm list users")).map { it.groupValues[1] }.toList()
        var started = false
        init {
            check(users.isNotEmpty()) { "Cannot enumerate device users" }
            check(users.none { FIXTURE_PACKAGE in packages(it) }) { "Fixture or its retained data already exists; refusing to modify it" }
        }
        override fun close() {
            if (!started) return
            // A commit already accepted by Android can outlive cancellation. Observe a quiet
            // interval and remove only the single package this test proved absent beforehand.
            val deadline = SystemClock.elapsedRealtime() + 30_000
            var absentSince = 0L
            while (SystemClock.elapsedRealtime() < deadline) {
                if (users.any { FIXTURE_PACKAGE in packages(it) }) {
                    val reply = shell("pm uninstall $FIXTURE_PACKAGE")
                    check(reply.contains("Success")) { "Owned fixture cleanup failed: $reply" }
                    absentSince = 0L
                } else {
                    if (absentSince == 0L) absentSince = SystemClock.elapsedRealtime()
                    if (SystemClock.elapsedRealtime() - absentSince >= 3_000) return
                }
                SystemClock.sleep(200)
            }
            error("Owned fixture did not settle after cleanup")
        }
        private fun packages(user: String): Set<String> {
            val lines = shell("pm list packages -u --user $user").lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            check("package:android" in lines && lines.all { it.startsWith("package:") }) { "Cannot safely list user $user packages" }
            return lines.map { it.removePrefix("package:") }.toSet()
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun await(timeout: Long, message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= deadline) throw AssertionError(message)
            SystemClock.sleep(25)
        }
    }

    /** Minimal AAPT2 protobuf manifest, following the shared parser's existing fixture schema. */
    private class Proto {
        private val output = ByteArrayOutputStream()
        fun string(field: Int, value: String) = content(field, value.toByteArray(Charsets.UTF_8))
        fun message(field: Int, body: Proto.() -> Unit) = content(field, Proto().apply(body).bytes())
        private fun content(field: Int, value: ByteArray) { raw((field shl 3) or 2); raw(value.size); output.write(value) }
        private fun raw(value: Int) {
            var next = value
            while (next and -128 != 0) { output.write((next and 127) or 128); next = next ushr 7 }
            output.write(next)
        }
        fun bytes(): ByteArray = output.toByteArray()
    }

    private companion object {
        const val TAG = "InstallerScriptSmoke"
        const val FIXTURE_PACKAGE = "io.github.supermonster003.autojs6.installer.spike.fixture"
        const val PLUGIN_PREFERENCES = "plugin_center_enable_state"
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        val FIXTURE_LOCK = Any()
    }
}
