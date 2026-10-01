package org.autojs.autojs.core.plugin.installer

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import android.view.WindowManager
import android.widget.TextView
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.installer.api.InstallerContract as C
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

/**
 * Shell-only debug entry in an isolated process with the real AutoJs6 UID. It uses the production
 * plugin selection, binding, caller guard and session client; no test identity reaches the plugin.
 * Only the two fixed component-free fixtures below are accepted. The desktop driver must first
 * prove absence across every Android user. This entry never kills a process or removes a package.
 */
class InstallerProcessProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply { text = "Installer host process probe (test fixtures only)" })
        dispatch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dispatch(intent)
    }

    private fun dispatch(input: Intent) {
        val caseId = input.getStringExtra("caseId")?.takeIf { CASE.matches(it) } ?: run { finish(); return }
        val mode = input.getStringExtra("mode") ?: "host-death"
        val authorizer = input.getStringExtra("authorizer") ?: C.AUTHORIZER_NONE
        val expectedPid = input.getIntExtra("expectedPid", -1)
        if (mode == "die" && expectedPid > 0 && expectedPid != Process.myPid()) { finish(); return }
        Thread({
            val directory = File(filesDir.canonicalFile, "p6-installer-probe/$caseId")
            try {
                check(File("/proc/self/cmdline").readText().trimEnd('\u0000') == "$packageName:installer_process_probe")
                check(directory.isDirectory && directory.canonicalFile == directory.absoluteFile)
                if (mode == "prepare" || mode == "restore") {
                    if (mode == "prepare") InstallerProbeEnablement.prepare(this, directory)
                    else InstallerProbeEnablement.restore(this, directory)
                    runOnUiThread { finish() }
                } else if (mode == "die") die(caseId, directory, expectedPid)
                else if (mode == "cleanup") cleanup(caseId, directory)
                else {
                    check(mode == "host-death" || mode == "performance")
                    check(authorizer in listOf(C.AUTHORIZER_NONE, C.AUTHORIZER_SHIZUKU, C.AUTHORIZER_ROOT))
                    check(active.get() == null)
                    check(!File(directory, "evidence.json").exists()) { "An existing probe must not be replayed" }
                    val source = File(directory, "fixture.apk")
                    val fixture = PERFORMANCE_FIXTURE
                    validate(source, fixture, mode)
                    val evidence = JsonObject().apply {
                        addProperty("caseId", caseId)
                        addProperty("kind", mode)
                        addProperty("fixturePackage", fixture)
                        addProperty("hostPackage", packageName)
                        addProperty("pid", Process.myPid())
                        addProperty("uid", Process.myUid())
                        addProperty("sourceBytes", source.length())
                        addProperty("sourceSha256", sha256(source))
                        addProperty("authorizer", authorizer)
                        addProperty("phase", "starting")
                        addProperty("completed", false)
                        addProperty("passed", false)
                        add("stageElapsedMs", JsonObject())
                    }
                    val control = Active(caseId, evidence, File(directory, "evidence.json"))
                    check(active.compareAndSet(null, control))
                    control.write {}
                    if (mode == "performance") performance(source, control, authorizer)
                    else hostDeath(caseId, source, control, authorizer)
                }
            } catch (failure: Exception) {
                val control = active.get()?.takeIf { it.caseId == caseId && mode != "prepare" && mode != "restore" }
                control?.handle?.cancel()
                control?.handle?.close()
                control?.write { addProperty("completed", true); addProperty("passed", false); addProperty("phase", "failed") }
                if (control != null && control.handle == null && !control.evidence.has("stage")) active.compareAndSet(control, null)
                if (directory.isDirectory) atomicWrite(File(directory, "error.json"), JsonObject().apply {
                    addProperty("caseId", caseId)
                    addProperty("failed", true)
                    addProperty("error", failure.javaClass.simpleName + ": " + failure.message)
                    if (failure is InstallerPluginException) addProperty("errorCode", failure.error.code)
                })
                runOnUiThread { finish() }
            }
        }, "p6-host-installer-probe").start()
    }

    private fun die(caseId: String, directory: File, expectedPid: Int) {
        val evidence = JsonParser.parseString(AtomicFile(File(directory, "evidence.json")).openRead().bufferedReader().use { it.readText() }).asJsonObject
        check(expectedPid == Process.myPid() && evidence["pid"].asInt == expectedPid && evidence["uid"].asInt == Process.myUid())
        check(evidence["caseId"].asString == caseId && evidence["kind"].asString == "host-death" && evidence["phase"].asString == "ready-for-host-death")
        val control = requireNotNull(active.get()?.takeIf { it.caseId == caseId })
        check(control.handle?.isTerminal == false)
        val uri = Uri.parse("content://$packageName.installer-process-probe/$caseId/fixture.apk")
        check(contentResolver.call(uri, "status", caseId, null)?.getBoolean("holding") == true)
        // This is only a guarded self-death of the dedicated debug caller. No arbitrary PID is accepted.
        Process.killProcess(Process.myPid())
    }

    private fun hostDeath(caseId: String, source: File, control: Active, authorizer: String) = runBlocking {
        check(InstallerPluginHost.getSelectedInfo(this@InstallerProcessProbeActivity).id == "three-setup-installer")
        requestRootIfNeeded(control, authorizer)
        // The provider is in a different private host process. Its pipe remains open when only
        // this callback process dies, so EOF cannot race or masquerade as Binder cancellation.
        val uri = Uri.parse("content://$packageName.installer-process-probe/$caseId/fixture.apk")
        val input = InstallerSource.fromUri(this@InstallerProcessProbeActivity, uri)
        check(input.size == source.length())
        val requestId = "p6-host-death-$caseId"
        control.write { addProperty("requestId", requestId) }
        val handle = InstallerPluginHost.openSession(this@InstallerProcessProbeActivity, listOf(input),
            if (authorizer == C.AUTHORIZER_NONE) C.INTERACTION_AUTO else C.INTERACTION_SILENT,
            InstallerJson.InstallOptions(authorizer = authorizer, timeoutMillis = 180_000),
            listener = listener(control), hostSessionId = requestId)
        control.handle = handle
        control.write { addProperty("pluginSessionId", handle.pluginSessionId); addProperty("phase", "session-open") }
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (true) {
            check(!handle.isTerminal && SystemClock.elapsedRealtime() < deadline) { "The host source did not reach its held write" }
            val state = contentResolver.call(uri, "status", caseId, null)
            if (state?.getBoolean("holding") == true && handle.status()?.state == C.STAGE_PREPARING) {
                control.write {
                    addProperty("sourceProcessPid", state.getInt("pid"))
                    addProperty("sourceBytesSent", state.getLong("bytesSent"))
                    addProperty("phase", "ready-for-host-death")
                }
                break
            }
            SystemClock.sleep(50)
        }
        // Keep the dedicated lease and actual callback binder alive. A driver failure eventually
        // cancels through the normal timeout; it cannot complete this deliberately partial source.
        val answer = handle.await()
        control.write { addProperty("phase", "ended-before-host-death"); addProperty("outcome", answer.javaClass.simpleName) }
        handle.close()
    }

    private fun performance(source: File, control: Active, authorizer: String) = runBlocking {
        val context = this@InstallerProcessProbeActivity
        check(InstallerPluginHost.getSelectedInfo(context).id == "three-setup-installer")
        val inspectStart = SystemClock.elapsedRealtime()
        val inspected = InstallerSource.fromFile(source).use { InstallerPluginHost.inspect(context, it) }
        val inspectedDocument = JsonParser.parseString(inspected).asJsonObject
        check(inspectedDocument[C.FIELD_PACKAGE_NAME]?.asString == PERFORMANCE_FIXTURE)
        control.write { addProperty("inspectElapsedMs", SystemClock.elapsedRealtime() - inspectStart); add("inspect", inspectedDocument) }
        requestRootIfNeeded(control, authorizer)
        val usersStart = SystemClock.elapsedRealtime()
        val users = InstallerPluginHost.getUsers(context, authorizer)
        check(users.any { it.id == Process.myUid() / 100000 })
        control.write { addProperty("getUsersElapsedMs", SystemClock.elapsedRealtime() - usersStart); addProperty("userCount", users.size) }
        control.installStarted = SystemClock.elapsedRealtime()
        control.write { addProperty("phase", "installing"); addProperty("installStartedElapsedMs", control.installStarted) }
        val handle = InstallerPluginHost.openSession(context, listOf(InstallerSource.fromFile(source)),
            if (authorizer == C.AUTHORIZER_NONE) C.INTERACTION_AUTO else C.INTERACTION_SILENT,
            InstallerJson.InstallOptions(authorizer = authorizer, timeoutMillis = 300_000), listener(control),
            hostSessionId = "p6-performance-${control.caseId}")
        control.handle = handle
        try {
            val answer = handle.await()
            val completed = answer as? InstallerSessionOutcome.Completed ?: error("Installation failed: $answer")
            val result = completed.completion.single
            check(result.ok && result.packageName == PERFORMANCE_FIXTURE && result.versionCode == 1L && result.authorizer == authorizer)
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(PERFORMANCE_FIXTURE, 0)
            @Suppress("DEPRECATION")
            check(info.versionCode == 1)
            check(sha256(source) == control.evidence["sourceSha256"].asString)
            control.write {
                addProperty("installElapsedMs", SystemClock.elapsedRealtime() - control.installStarted)
                add("result", JsonParser.parseString(result.json))
                addProperty("sourcePreserved", true)
                addProperty("completed", true)
                addProperty("passed", true)
                addProperty("phase", "completed")
            }
        } finally { handle.close() }
        runOnUiThread { finish() }
    }

    private suspend fun requestRootIfNeeded(control: Active, authorizer: String) {
        if (authorizer != C.AUTHORIZER_ROOT) return
        // Root permission remains a real production request. It primes only the authorization
        // state; getUsers below measures the separate first privileged-service binding.
        val started = SystemClock.elapsedRealtime()
        check(InstallerPluginHost.requestAuthorizer(this, authorizer, 30_000))
        control.write { addProperty("requestAuthorizerElapsedMs", SystemClock.elapsedRealtime() - started) }
    }

    private fun listener(control: Active) = object : InstallerSessionListener {
        override fun onStage(stage: String, detail: InstallerJson.StageDetail) = control.write {
            addProperty("stage", stage)
            val stages = getAsJsonObject("stageElapsedMs")
            if (!stages.has(stage)) stages.addProperty(stage, (SystemClock.elapsedRealtime() - control.installStarted).coerceAtLeast(0))
        }
        override fun onProgress(progress: Float, detail: InstallerJson.ProgressDetail) = control.progress(progress) {
            addProperty("bytesWritten", detail.bytesWritten)
            addProperty("totalBytes", detail.totalBytes)
        }
        override fun onFailed(error: InstallerError) = control.write { addProperty("errorCode", error.code); addProperty("error", error.message) }
    }

    private fun cleanup(caseId: String, directory: File) {
        val evidenceFile = File(directory, "evidence.json")
        val evidence = JsonParser.parseString(AtomicFile(evidenceFile).openRead().bufferedReader().use { it.readText() }).asJsonObject
        check(evidence["caseId"].asString == caseId && evidence["hostPackage"].asString == packageName && evidence["uid"].asInt == Process.myUid())
        // Local handle.close() settles its own callback before the remote cancellation finishes.
        // A performance driver's uninstall/source cleanup may proceed only after real success.
        if (evidence["kind"].asString == "performance") {
            check(evidence["completed"]?.asBoolean == true && evidence["passed"]?.asBoolean == true) {
                "Unconfirmed installation: preserve the source and case for recovery"
            }
        }
        active.get()?.takeIf { it.caseId == caseId }?.let { control ->
            control.handle?.cancel()
            control.handle?.close()
            active.compareAndSet(control, null)
        }
        if (evidence["kind"].asString == "host-death") {
            val uri = Uri.parse("content://$packageName.installer-process-probe/$caseId/fixture.apk")
            val state = contentResolver.call(uri, "close", caseId, null)
            check(state?.getBoolean("closed") == true)
        }
        check(sha256(File(directory, "fixture.apk")) == evidence["sourceSha256"].asString)
        evidence.addProperty("phaseBeforeCleanup", evidence["phase"].asString)
        evidence.addProperty("phase", "cleaned")
        evidence.addProperty("cleanupPid", Process.myPid())
        evidence.addProperty("sourcePreserved", true)
        atomicWrite(File(directory, "evidence.json"), evidence)
        runOnUiThread { finish() }
    }

    private fun validate(source: File, expectedPackage: String, mode: String) {
        check(source.isFile && source.canonicalFile == source.absoluteFile)
        check(mode == "performance" || mode == "host-death")
        check(source.length() in (100L * 1024 * 1024)..(102L * 1024 * 1024))
        @Suppress("DEPRECATION")
        val info = requireNotNull(packageManager.getPackageArchiveInfo(source.path, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS))
        @Suppress("DEPRECATION")
        check(info.packageName == expectedPackage && info.versionCode == 1)
        check(requireNotNull(info.applicationInfo).flags and ApplicationInfo.FLAG_HAS_CODE == 0)
        check(info.activities.isNullOrEmpty() && info.services.isNullOrEmpty() && info.receivers.isNullOrEmpty() && info.providers.isNullOrEmpty())
        check(info.requestedPermissions.isNullOrEmpty())
        @Suppress("DEPRECATION")
        val existing = try { packageManager.getPackageInfo(expectedPackage, PackageManager.MATCH_UNINSTALLED_PACKAGES) }
            catch (_: PackageManager.NameNotFoundException) { null }
        check(existing == null) { "The fixed fixture already exists" }
    }

    private class Active(val caseId: String, val evidence: JsonObject, private val file: File) {
        @Volatile var handle: InstallerSessionHandle? = null
        @Volatile var installStarted = SystemClock.elapsedRealtime()
        private var progressSavedAt = Long.MIN_VALUE
        @Synchronized fun write(update: JsonObject.() -> Unit) { evidence.update(); atomicWrite(file, evidence) }
        @Synchronized fun progress(progress: Float, update: JsonObject.() -> Unit) {
            evidence.update()
            val now = SystemClock.elapsedRealtime()
            if (progressSavedAt == Long.MIN_VALUE || now - progressSavedAt >= 500 || progress >= 1f) {
                progressSavedAt = now
                atomicWrite(file, evidence)
            }
        }
    }

    companion object {
        internal val CASE = Regex("[a-z0-9-]{1,64}")
        internal const val PERFORMANCE_FIXTURE = "io.github.supermonster003.autojs6.installer.performance.fixture"
        private val active = AtomicReference<Active?>()
        private fun atomicWrite(file: File, value: JsonObject) {
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try { output.write(value.toString().toByteArray(Charsets.UTF_8)); output.fd.sync(); atomic.finishWrite(output) }
            catch (failure: Exception) { atomic.failWrite(output); throw failure }
        }
        private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").let { digest ->
            file.inputStream().use { input -> val buffer = ByteArray(128 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
