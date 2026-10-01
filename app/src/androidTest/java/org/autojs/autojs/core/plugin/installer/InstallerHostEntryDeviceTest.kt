package org.autojs.autojs.core.plugin.installer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.viewpager.widget.ViewPager
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginCenterActivity
import org.autojs.autojs.ui.explorer.ExplorerView
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Opt-in acceptance of the actual MainActivity file rows and Plugin Center URL menu.
 * Navigation may select a private fixture directory; every install action is a click on the
 * production UI. This class never calls PackageInstallRouter or substitutes a Binder caller.
 *
 * Arguments: installer.entry.case, mode=absent|present, route=apk|xapk|url,
 * complete=installed|cancel, systemPackage (absent APK/URL), url (URL only).
 * present mode may explicitly opt in to installer.entry.enablePlugin=true.
 *
 * The driver waits for files/p1-installer-entry/<case>/ready.json, completes or cancels the
 * real installer UI, then writes driver-complete.json containing {"caseId":"...",
 * "outcome":"installed|cancel"}. No system permission or default is changed here. The
 * installed fixture and plugin history remain for the driver's separately audited cleanup.
 * Navigation preferences, temporary enablement and owned input/cache files are restored in
 * finally. After process death, run restoreInterruptedEntry with installer.entry.action=restore.
 */
@RunWith(AndroidJUnit4::class)
class InstallerHostEntryDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val trackedActivities = linkedMapOf<Activity, Stage>()
    private var hostTaskId: Int? = null
    private var lastHostLifecycleEvent = 0L
    private var lifecycleTracking = false
    private val lifecycleCallback = ActivityLifecycleCallback { activity, stage ->
        if (isHostActivity(activity) &&
            (trackedActivities.containsKey(activity) || hostTaskId?.let { activity.taskId == it } == true)
        ) {
            trackedActivities[activity] = stage
            lastHostLifecycleEvent = SystemClock.elapsedRealtime()
        }
    }
    private val report = JSONObject()
    private lateinit var directory: File
    private lateinit var journal: JSONObject
    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    private val cacheRoot by lazy { context.cacheDir.canonicalFile }

    @Test
    fun actualHostEntry() {
        assumeTrue("Opt in to the actual host entry acceptance", arg("action") == "run")
        val mode = arg("mode").also { check(it in setOf("absent", "present")) }
        val route = arg("route").also { check(it in setOf("apk", "xapk", "url")) }
        val completion = arg("complete").also { check(it in setOf("installed", "cancel")) }
        val caseId = caseId()
        directory = caseDirectory(caseId)
        check(!directory.exists()) { "A case directory already exists; inspect or restore it first" }
        directory.parentFile?.listFiles().orEmpty().filter { it.isDirectory }.forEach { previous ->
            val restore = File(previous, "restore.json")
            if (restore.exists() || File(restore.path + ".bak").exists()) {
                check(!read(restore).getBoolean("pending")) { "An earlier entry case still needs restoration: ${previous.name}" }
            }
        }
        val users = assertFixtureAbsent()
        if (mode == "absent") {
            check(packages(userId()).none { it == PLUGIN }) { "Absent mode requires the plugin to be actually uninstalled" }
            check(!runBlocking { InstallerPluginHost.isAvailable(context) }) { "Another installer is available" }
        }
        val apkName = "installer-entry-$caseId.apk"
        val download = File(cacheDirectory("plugin_dl"), apkName)
        check(!download.exists()) { "Refusing to replace a pre-existing download" }
        check(importCopies(apkName).isEmpty()) { "Refusing to reuse a pre-existing imported source" }
        if (route == "url") validateUrl(arg("url"), apkName)
        check(directory.mkdirs())
        journal = JSONObject()
            .put("caseId", caseId).put("hostUid", Process.myUid())
            .put("mode", mode).put("route", route).put("pending", true)
            .put("fixturePackage", FIXTURE).put("fixtureSha256", FIXTURE_SHA256)
            .put("users", JSONArray(users)).put("apkName", apkName)
            .put("navigation", navigationSnapshot()).put("phase", "prepared")
        write("restore.json", journal)
        report.put("caseId", caseId).put("mode", mode).put("route", route)
            .put("hostUid", Process.myUid()).put("fixturePackage", FIXTURE)
            .put("cachePath", context.cacheDir.absolutePath).put("canonicalCacheRoot", cacheRoot.absolutePath)
        val oldFlags = instrumentation.uiAutomation.serviceInfo.flags
        try {
            startLifecycleTracking()
            instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            if (mode == "present") {
                if (arg("enablePlugin") == "true") InstallerProbeEnablement.prepare(context, directory)
                assertTrue("The official compatible installer must be available", runBlocking { InstallerPluginHost.isAvailable(context) })
                assertEquals("three-setup-installer", runBlocking { InstallerPluginHost.getSelectedInfo(context) }.id)
            }
            val sources = File(directory, "sources").apply { check(mkdir()) }
            val apk = File(sources, apkName).also(::copyAndVerifyFixture)
            val xapk = File(sources, "installer-entry-$caseId.xapk")
            ZipOutputStream(xapk.outputStream()).use { output ->
                output.putNextEntry(ZipEntry("base.apk"))
                apk.inputStream().use { it.copyTo(output) }
                output.closeEntry()
            }
            journal.put("xapkSha256", sha256(xapk))
            write("restore.json", journal)
            if (route == "url") openUrlEntry(arg("url"), apkName)
            else openFileEntry(sources, if (route == "apk") apk.name else xapk.name, directInstall = route == "xapk")
            if (route != "xapk") {
                awaitNode("production APK information dialog") { node ->
                    node.packageName?.toString() == context.packageName && node.text?.toString() == FIXTURE
                }
                capture("02-host-package-information")
                clickId("${context.packageName}:id/md_buttonDefaultPositive")
            }
            if (mode == "absent" && route == "xapk") {
                val expected = context.getString(R.string.error_installer_plugin_required_for_package_format,
                    "XAPK", ThreeSetupInstallerOfficialPlugin.DISPLAY_NAME)
                awaitNode("missing installer guidance") { it.packageName?.toString() == context.packageName && it.text?.toString() == expected }
                capture("03-plugin-required")
                report.put("targetPackage", context.packageName).put("outcome", "plugin-required")
                clickText(context.getString(R.string.dialog_button_cancel))
                assertFixtureAbsent()
            } else {
                val targetPackage = if (mode == "present") PLUGIN else arg("systemPackage").also {
                    check(it.matches(Regex("[A-Za-z0-9_.]+")) && it != PLUGIN && it != context.packageName)
                }
                awaitNode("actual installer confirmation", 45_000) {
                    it.packageName?.toString() == targetPackage &&
                        it.text?.toString()?.let { text -> text.contains(FIXTURE) || text.contains(FIXTURE_LABEL) } == true
                }
                if (mode == "present") {
                    awaitNode("plugin source package") { it.packageName?.toString() == PLUGIN && it.text?.toString()?.contains(FIXTURE) == true }
                }
                capture("03-installer-confirmation")
                report.put("targetPackage", targetPackage).put("requestedOutcome", completion)
                journal.put("phase", "awaiting-driver")
                write("restore.json", journal)
                write("ready.json", report)
                awaitDriverCompletion(caseId, completion)
                capture("04-driver-completed")
                if (completion == "installed") {
                    checkInstalledFixture()
                    report.put("installedVersion", 1).put("outcome", "installed")
                } else {
                    await("Installer UI must be dismissed before cancellation is accepted", 15_000) {
                        instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != targetPackage
                    }
                    assertFixtureAbsent()
                    report.put("outcome", "cancelled")
                }
            }
            report.put("passed", true)
        } catch (failure: Throwable) {
            report.put("passed", false).put("failure", failure.toString())
            runCatching { capture("failure") }
            throw failure
        } finally {
            try {
                restoreOwnedState()
                report.put("hostStateRestored", true)
            } catch (failure: Throwable) {
                report.put("passed", false).put("hostStateRestored", false).put("cleanupFailure", failure.toString())
                throw failure
            } finally {
                stopLifecycleTracking()
                write("result.json", report)
                instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply { flags = oldFlags }
            }
        }
    }

    @Test
    fun restoreInterruptedEntry() {
        assumeTrue("Explicit crash recovery only", arg("action") == "restore")
        directory = caseDirectory(caseId())
        journal = read(File(directory, "restore.json"))
        startLifecycleTracking()
        try { restoreOwnedState() } finally { stopLifecycleTracking() }
    }

    private fun openFileEntry(sources: File, name: String, directInstall: Boolean) {
        val activity = launchHostActivity(MainActivity::class.java)
        instrumentation.runOnMainSync { activity.findViewById<ViewPager>(R.id.viewpager).setCurrentItem(activity.filesItemIndex, false) }
        var explorer: ExplorerView? = null
        await("MainActivity must expose the production file list", 15_000) {
            instrumentation.runOnMainSync { explorer = activity.findViewById<ExplorerView>(R.id.item_list)?.takeIf { it.isShown } }
            explorer != null
        }
        instrumentation.runOnMainSync { requireNotNull(explorer).setExplorer(sources.absolutePath, sources.absolutePath) }
        fun isReady(): Boolean {
            var ready = false
            instrumentation.runOnMainSync {
                val list = explorer?.explorerItemListView
                val decor = activity.window.decorView
                ready = !activity.isFinishing && !activity.isDestroyed && decor.hasWindowFocus() &&
                    decor.isAttachedToWindow && !decor.isLayoutRequested &&
                    activity.findViewById<ViewPager>(R.id.viewpager).currentItem == activity.filesItemIndex &&
                    explorer?.currentPage?.path == sources.absolutePath && explorer?.isShown == true &&
                    list != null && list.scrollState == RecyclerView.SCROLL_STATE_IDLE && !list.isComputingLayout &&
                    !list.isLayoutRequested && list.itemAnimator?.isRunning != true
            }
            return ready
        }
        val action = if (directInstall) "install" else "more"
        report.put("inspectorInstalled", INSPECTOR in packages(userId()))
            .put("fileEntryGesture", if (directInstall) "file-row-install-button" else "file-row-more-information-install")
        fun rowActionMatches(node: AccessibilityNodeInfo): Boolean {
            if (!fileActionMatches(node, name, action)) return false
            if (directInstall) return true
            // The overflow information action exists exactly when the row information icon is
            // displaced. Do not race plugin discovery to obtain a different file-name route.
            val row = requireNotNull(fileRow(node))
            val visible = nodes(row).filter { it.isVisibleToUser }.mapNotNull { it.viewIdResourceName }.toSet()
            return "${context.packageName}:id/primary_viewer" in visible && "${context.packageName}:id/info" !in visible
        }
        awaitNode("fixture file row action", ready = ::isReady, matches = ::rowActionMatches)
        capture("01-file-row")
        clickNode("actual fixture file row $action", ready = ::isReady, beforeTouch = { node ->
            val row = requireNotNull(fileRow(node))
            report.put("visibleFileActions", JSONArray().apply {
                nodes(row).filter { it.isClickable && it.isVisibleToUser }.forEach {
                    put(JSONObject().put("id", it.viewIdResourceName).put("description", it.contentDescription)
                        .put("bounds", Rect().also(it::getBoundsInScreen).flattenToString()))
                }
            })
        }, matches = ::rowActionMatches)
        if (!directInstall) {
            awaitNode("actual file information menu item") { it.text?.toString() == context.getString(R.string.text_info) }
            capture("01-file-options")
            clickText(context.getString(R.string.text_info))
        }
    }

    private fun openUrlEntry(url: String, name: String) {
        report.put("fileEntryGesture", "plugin-center-url-input-download-information-install")
        val activity = launchHostActivity(PluginCenterActivity::class.java)
        clickId("${context.packageName}:id/action_add")
        clickText(context.getString(R.string.text_install_from_url))
        val input = awaitNode("URL input") { it.className?.toString() == "android.widget.EditText" && it.isEditable }
        check(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, url)
        })) { "The real URL input rejected text" }
        capture("01-url-input")
        clickText(context.getString(R.string.dialog_button_retrieve))
        awaitNode("downloaded package information", 45_000) { it.packageName?.toString() == context.packageName && it.text?.toString() == name }
        val download = File(cacheDirectory("plugin_dl"), name)
        assertEquals("The real URL download must be the reviewed fixture", FIXTURE_SHA256, sha256(download))
        val imports = importCopies(name)
        check(imports.size == 1 && sha256(imports.single()) == FIXTURE_SHA256) { "The real URL import was not the reviewed fixture" }
        report.put("url", url).put("downloadSha256", sha256(download)).put("importPath", imports.single().absolutePath)
    }

    private fun awaitDriverCompletion(caseId: String, completion: String) {
        val marker = File(directory, "driver-complete.json")
        await("The driver must complete the real installer UI and write driver-complete.json", 180_000) { marker.isFile }
        val value = read(marker)
        check(value.getString("caseId") == caseId && value.getString("outcome") == completion)
    }

    private fun checkInstalledFixture() {
        await("The reviewed fixture must be actually installed", 20_000) { FIXTURE in packages(userId()) && installedPath() != null }
        val source = File(requireNotNull(installedPath()))
        assertEquals("Installed bytes must match the no-code fixture", FIXTURE_SHA256, sha256(source))
        @Suppress("DEPRECATION")
        val info = requireNotNull(context.packageManager.getPackageArchiveInfo(source.absolutePath, 0))
        assertEquals(FIXTURE, info.packageName)
        @Suppress("DEPRECATION")
        assertEquals(1, info.versionCode)
        assertEquals(0, requireNotNull(info.applicationInfo).flags and ApplicationInfo.FLAG_HAS_CODE)
    }

    private fun installedPath(): String? = shell("pm path --user ${userId()} $FIXTURE").lineSequence()
        .map(String::trim).filter { it.startsWith("package:") }.map { it.removePrefix("package:") }.singleOrNull()

    private fun copyAndVerifyFixture(file: File) {
        instrumentation.context.assets.open("installer/fixture-v1.apk").use { input -> file.outputStream().use(input::copyTo) }
        assertEquals(FIXTURE_SHA256, sha256(file))
        @Suppress("DEPRECATION")
        val info = requireNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS))
        assertEquals(FIXTURE, info.packageName)
        assertEquals(0, requireNotNull(info.applicationInfo).flags and ApplicationInfo.FLAG_HAS_CODE)
        check(info.activities.isNullOrEmpty() && info.services.isNullOrEmpty() && info.providers.isNullOrEmpty() && info.receivers.isNullOrEmpty())
        ZipFile(file).use { zip -> check(zip.entries().asSequence().none { it.name.endsWith(".dex") || it.name.startsWith("lib/") }) }
    }

    private fun navigationSnapshot() = JSONObject().apply {
        NAVIGATION_KEYS.forEach { key ->
            put(key, JSONObject().put("present", prefs.contains(key)).apply {
                if (prefs.contains(key)) put("value", prefs.all[key])
            })
        }
    }

    private fun restoreOwnedState() {
        check(journal.getString("caseId") == directory.name && journal.getInt("hostUid") == Process.myUid())
        check(journal.getString("fixturePackage") == FIXTURE && journal.getString("fixtureSha256") == FIXTURE_SHA256)
        if (!journal.getBoolean("pending")) return
        finishActivities()
        val snapshot = journal.getJSONObject("navigation")
        check(snapshot.keys().asSequence().toSet() == NAVIGATION_KEYS.toSet())
        instrumentation.runOnMainSync {
            val editor = prefs.edit()
            NAVIGATION_KEYS.forEach { key ->
                val original = snapshot.getJSONObject(key)
                if (!original.getBoolean("present")) editor.remove(key)
                else if (key.endsWith("page_state")) editor.putInt(key, original.getInt("value"))
                else editor.putString(key, original.getString("value"))
            }
            check(editor.commit()) { "Cannot durably restore Explorer navigation preferences" }
            assertEquals(snapshot.toString(), navigationSnapshot().toString())
        }
        val restoredAt = SystemClock.elapsedRealtime()
        await("Restored navigation must remain stable after all owned activities are destroyed", 5_000) {
            instrumentation.runOnMainSync {
                check(ownedLiveActivities().isEmpty()) { "An owned host activity returned after navigation restoration" }
                assertEquals(snapshot.toString(), navigationSnapshot().toString())
            }
            SystemClock.elapsedRealtime() - restoredAt >= 1_000
        }
        if (File(directory, "enable-restore.json").exists() || File(directory, "enable-restore.json.bak").exists()) {
            InstallerProbeEnablement.restore(context, directory)
        }
        val apkName = journal.getString("apkName")
        check(apkName == "installer-entry-${directory.name}.apk")
        val download = File(cacheDirectory("plugin_dl"), apkName)
        deleteVerified(download, FIXTURE_SHA256)
        importCopies(apkName).forEach { file ->
            deleteVerified(file, FIXTURE_SHA256)
            check(requireNotNull(file.parentFile).listFiles().orEmpty().isEmpty()) { "An imported directory contains unrelated files" }
            check(file.parentFile!!.delete())
        }
        val sources = File(directory, "sources")
        if (sources.exists()) {
            check(sources.canonicalFile.parentFile == directory.canonicalFile)
            deleteVerified(File(sources, apkName), FIXTURE_SHA256)
            val xapk = File(sources, "installer-entry-${directory.name}.xapk")
            if (xapk.exists()) deleteVerified(xapk, journal.getString("xapkSha256"))
            check(sources.listFiles().orEmpty().isEmpty() && sources.delete())
        }
        journal.put("pending", false).put("phase", "restored")
        write("restore.json", journal)
    }

    private fun importCopies(name: String): List<File> {
        val root = cacheDirectory("from_uri")
        return root.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("import-") }.mapNotNull { dir ->
            val file = File(dir, name)
            if (!file.exists()) null else {
                check(dir.canonicalFile.parentFile == root && file.canonicalFile.parentFile == dir.canonicalFile)
                file
            }
        }
    }

    private fun cacheDirectory(name: String): File {
        check(name in setOf("plugin_dl", "from_uri"))
        // Android can expose the private app root through a platform path alias. Resolve that
        // trusted root first, while keeping each owned child lexical so symlinks still fail.
        return File(cacheRoot, name).also {
            check(it.canonicalFile == it.absoluteFile) { "An owned cache directory is a symlink: $it" }
        }
    }

    private fun deleteVerified(file: File, expected: String) {
        if (!file.exists()) return
        check(file.isFile && file.canonicalFile == file.absoluteFile && sha256(file) == expected) { "Owned source changed; retaining it for inspection: $file" }
        check(file.delete())
    }

    private fun assertFixtureAbsent(): List<String> {
        val users = Regex("UserInfo\\{(\\d+):").findAll(shell("pm list users")).map { it.groupValues[1] }.toList()
        check(users.isNotEmpty())
        check(users.all { FIXTURE !in packages(it) }) { "The fixture or retained data already exists; refusing to take ownership" }
        return users
    }

    private fun packages(user: String): Set<String> {
        check(user.matches(Regex("[0-9]+")))
        val lines = shell("pm list packages -u --user $user").lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        check("package:android" in lines && lines.all { it.startsWith("package:") }) { "Cannot safely enumerate packages for user $user" }
        return lines.map { it.removePrefix("package:") }.toSet()
    }

    private fun isHostActivity(activity: Activity) = activity is MainActivity || activity is PluginCenterActivity

    private fun startLifecycleTracking() {
        hostTaskId = journal.optInt("hostTaskId", -1).takeIf { it >= 0 }
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            monitor.addLifecycleCallback(lifecycleCallback)
            lifecycleTracking = true
            lastHostLifecycleEvent = SystemClock.elapsedRealtime()
            ownedLiveActivities().forEach { trackedActivities[it] = monitor.getLifecycleStageOf(it) }
        }
    }

    private fun stopLifecycleTracking() {
        if (!lifecycleTracking) return
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(lifecycleCallback)
            lifecycleTracking = false
        }
    }

    private fun <T : Activity> launchHostActivity(type: Class<T>): T {
        val intent = Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_CASE_ID, directory.name)
        val activity = type.cast(instrumentation.startActivitySync(intent))!!
        instrumentation.runOnMainSync {
            val task = activity.taskId
            check(task >= 0 && (hostTaskId == null || hostTaskId == task))
            check(activity.intent.getStringExtra(EXTRA_CASE_ID) == directory.name)
            hostTaskId = task
            journal.put("hostTaskId", task).put("hostTaskCaseId", directory.name)
            write("restore.json", journal)
            report.put("hostTaskId", task)
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            ownedLiveActivities().forEach { trackedActivities[it] = monitor.getLifecycleStageOf(it) }
            trackedActivities[activity] = monitor.getLifecycleStageOf(activity)
            lastHostLifecycleEvent = SystemClock.elapsedRealtime()
        }
        return activity
    }

    /** Called only on the main thread. Never adopts another task by package name alone. */
    private fun liveHostActivities(): List<Activity> {
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        return Stage.values().filter { it != Stage.DESTROYED }
            .flatMap { monitor.getActivitiesInStage(it) }.filter(::isHostActivity).distinct()
    }

    private fun ownedLiveActivities(): List<Activity> = liveHostActivities().filter { hostTaskId != null && it.taskId == hostTaskId }

    private fun finishActivities() {
        if (journal.has("hostTaskId")) {
            check(journal.getString("hostTaskCaseId") == directory.name && journal.getInt("hostTaskId") == hostTaskId)
        }
        await("Every owned host instance must reach DESTROYED before restoring navigation", 20_000) {
            var settled = false
            instrumentation.runOnMainSync {
                if (hostTaskId == null) {
                    // Older journals did not record a task. A fresh recovery runner can restore
                    // them only without live host screens; it must not guess which task to end.
                    check(liveHostActivities().isEmpty()) { "A legacy journal cannot identify a live host task" }
                }
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                ownedLiveActivities().forEach { activity ->
                    trackedActivities[activity] = monitor.getLifecycleStageOf(activity)
                    if (!activity.isFinishing) activity.finish()
                }
                settled = trackedActivities.values.all { it == Stage.DESTROYED } &&
                    ownedLiveActivities().isEmpty() && SystemClock.elapsedRealtime() - lastHostLifecycleEvent >= 1_000
            }
            settled
        }
        report.put("destroyedHostInstances", trackedActivities.size)
    }

    private fun fileRow(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.viewIdResourceName == "${context.packageName}:id/item") return current
            current = current.parent
        }
        return null
    }

    private fun fileActionMatches(node: AccessibilityNodeInfo, name: String, action: String): Boolean {
        if (node.viewIdResourceName != "${context.packageName}:id/$action") return false
        val row = fileRow(node) ?: return false
        return nodes(row).any { it.viewIdResourceName == "${context.packageName}:id/name" && it.text?.toString() == name }
    }

    private fun clickId(id: String) = clickNode(id) { it.viewIdResourceName == id }
    private fun clickText(text: String) = clickNode(text) { it.text?.toString()?.equals(text, ignoreCase = true) == true }
    private fun clickNode(
        description: String,
        ready: () -> Boolean = { true },
        beforeTouch: (AccessibilityNodeInfo) -> Unit = {},
        matches: (AccessibilityNodeInfo) -> Boolean,
    ) {
        val node = awaitNode(description, ready = ready, matches = matches)
        var clickable = node
        while (!clickable.isClickable) clickable = clickable.parent ?: error("No real click target for $description")
        check(clickable.isVisibleToUser && clickable.isEnabled && node.refresh())
        val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
        val bounds = requireNotNull(visibleBounds(node, root)) { "The touch target left the active window: $description" }
        check(matches(node) && ready()) { "The UI changed immediately before touching $description" }
        beforeTouch(node)
        val gestures = report.optJSONArray("touches") ?: JSONArray().also { report.put("touches", it) }
        gestures.put(JSONObject().put("description", description).put("windowId", node.windowId)
            .put("bounds", bounds.flattenToString()).put("x", bounds.centerX()).put("y", bounds.centerY()))
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) { "Touch injection failed: $description" } }
            finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN)
        SystemClock.sleep(60)
        send(MotionEvent.ACTION_UP)
    }

    private fun awaitNode(
        description: String,
        timeout: Long = 20_000,
        ready: () -> Boolean = { true },
        matches: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo {
        var previous: Rect? = null
        var previousWindow = -1
        var previousActions: String? = null
        var stableSince = 0L
        var result: AccessibilityNodeInfo? = null
        await("Missing visible stable UI node: $description", timeout) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val node = root?.let { active -> nodes(active).firstOrNull {
                it.isVisibleToUser && it.isEnabled && matches(it) && visibleBounds(it, active) != null
            } }
            val bounds = if (node != null && root != null && ready()) visibleBounds(node, root) else null
            val actions = node?.let(::fileRow)?.let { row -> nodes(row).filter { it.isClickable && it.isVisibleToUser }
                .joinToString { "${it.viewIdResourceName}:${it.contentDescription}:${Rect().also(it::getBoundsInScreen).flattenToString()}" } }
            if (node != null && bounds != null && bounds == previous && node.windowId == previousWindow && actions == previousActions) {
                if (SystemClock.elapsedRealtime() - stableSince >= 500L) result = node
            } else {
                previous = bounds
                previousWindow = node?.windowId ?: -1
                previousActions = actions
                stableSince = SystemClock.elapsedRealtime()
            }
            result != null
        }
        return requireNotNull(result)
    }

    private fun visibleBounds(node: AccessibilityNodeInfo, root: AccessibilityNodeInfo): Rect? {
        if (node.windowId != root.windowId) return null
        val size = Point()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealSize(size)
        val screen = Rect(0, 0, size.x, size.y)
        val window = Rect().also(root::getBoundsInScreen)
        val bounds = Rect().also(node::getBoundsInScreen)
        if (bounds.isEmpty || !bounds.intersect(window) || !bounds.intersect(screen)) return null
        return bounds
    }

    private fun nodes(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(root)
        for (i in 0 until root.childCount) root.getChild(i)?.let { yieldAll(nodes(it)) }
    }

    private fun capture(name: String) {
        val root = instrumentation.uiAutomation.rootInActiveWindow
        val details = JSONArray()
        root?.let { nodes(it).forEach { node ->
            details.put(JSONObject().put("package", node.packageName).put("id", node.viewIdResourceName)
                .put("text", node.text).put("description", node.contentDescription).put("class", node.className)
                .put("visible", node.isVisibleToUser).put("enabled", node.isEnabled).put("clickable", node.isClickable)
                .put("bounds", Rect().also(node::getBoundsInScreen).flattenToString()))
        } }
        write("$name.json", JSONObject().put("nodes", details))
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
    }

    private fun validateUrl(url: String, name: String) {
        val uri = URI(url)
        check(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1024..65535 &&
            uri.rawPath == "/$name" && uri.rawQuery == null && uri.rawFragment == null && uri.rawUserInfo == null) {
            "Only the exact loopback fixture URL is accepted"
        }
    }

    private fun caseId() = arg("case").also { check(it.matches(Regex("[a-z][a-z0-9-]{7,79}"))) }
    private fun caseDirectory(id: String) = File(context.filesDir.canonicalFile, "p1-installer-entry/$id").also {
        check(it.canonicalFile.parentFile == File(context.filesDir.canonicalFile, "p1-installer-entry"))
    }
    private fun arg(key: String) = arguments.getString("installer.entry.$key").orEmpty()
    private fun userId() = (Process.myUid() / 100_000).toString()
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65_536); while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) } }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun read(file: File): JSONObject = AtomicFile(file).openRead().use { input ->
        check(input.channel.size() in 1..65_536)
        JSONObject(input.readBytes().toString(Charsets.UTF_8))
    }
    private fun write(name: String, value: JSONObject) {
        val atomic = AtomicFile(File(directory, name))
        val output = atomic.startWrite()
        try { output.write(value.toString(2).toByteArray(Charsets.UTF_8)); output.fd.sync(); atomic.finishWrite(output) }
        catch (failure: Throwable) { atomic.failWrite(output); throw failure }
    }
    private fun await(message: String, timeout: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) { if (SystemClock.elapsedRealtime() >= deadline) throw AssertionError(message); SystemClock.sleep(75) }
    }

    private companion object {
        const val PLUGIN = "io.github.supermonster003.autojs6.plugin.three.setup.installer"
        const val INSPECTOR = "io.github.supermonster003.autojs6.plugin.apkinspector"
        const val EXTRA_CASE_ID = "org.autojs.autojs.test.installer.entry.case"
        const val FIXTURE = "io.github.supermonster003.autojs6.installer.spike.fixture"
        const val FIXTURE_LABEL = "3-Setup Spike Fixture"
        const val FIXTURE_SHA256 = "fec583a389978fdc298e9d85979b95feceb93e6fb3fd3f581511c826401e8b69"
        val NAVIGATION_KEYS = listOf("page_state", "explorer_current", "explorer_root", "explorer_history").map { "ExplorerView.$it" }
    }
}
