package org.autojs.autojs.ui.explorer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import android.util.Base64
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.shizuku.IUserService
import org.autojs.autojs.model.explorer.Explorer
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileProvider
import org.autojs.autojs.model.explorer.PrivilegedAccessMode
import org.autojs.autojs.model.explorer.PrivilegedBackupManager
import org.autojs.autojs.model.explorer.PrivilegedBackupManager.BackupEntry
import org.autojs.autojs.model.explorer.PrivilegedBackupManager.RetentionPolicy
import org.autojs.autojs.model.explorer.PrivilegedEditSessions
import org.autojs.autojs.model.explorer.PrivilegedFileOperationException
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs6.R
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Isolated backup registries and preferences ensure UI cleanup never touches the user's backups. */
@RunWith(AndroidJUnit4::class)
class PrivilegedBackupUiDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val activities = mutableListOf<Activity>()
    private val actualSessions = mutableListOf<Uri>()
    private val preferences = listOf(EditorEnginePreferences.KEY_EDITOR_ENGINE, EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE)
        .associateWith { Pref.get().contains(it) to Pref.get().getString(it, null) }
    private lateinit var localRoot: File
    private lateinit var remoteRoot: String
    private lateinit var fixtureContext: FixtureContext
    private lateinit var service: IUserService
    private lateinit var explorer: ExplorerView
    private val mode = PrivilegedAccessMode.SHIZUKU

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        assumeTrue("Shizuku must already be running and authorized", WrappedShizuku.isRunning() && WrappedShizuku.hasPermission())
        service = checkNotNull(WrappedShizuku.getServiceOrNull())
        val token = UUID.randomUUID().toString()
        localRoot = File(context.cacheDir, "privileged-backup-ui-$token").apply { check(mkdir()) }
        fixtureContext = FixtureContext(context, localRoot, token)
        remoteRoot = "/data/local/tmp/autojs6-privileged-backup-ui-$token"
        shell("mkdir -m 700 -- ${quote(remoteRoot)}")
        PrivilegedBackupManager.setPolicy(fixtureContext, RetentionPolicy(10, 256L * 1024 * 1024))
        EditorEnginePreferences.set(EditorEngineType.NATIVE)
        launchExplorer()
    }

    @After
    fun tearDown() {
        onMain { activities.forEach { if (!it.isDestroyed) it.finishAndRemoveTask() } }
        await("Test activities should close") { onMain { activities.all { it.isDestroyed } } }
        PrivilegedBackupManager.awaitIdle()
        actualSessions.forEach { uri ->
            val directory = File(File(context.filesDir, "privileged-edit-sessions"), uri.pathSegments.single())
            check(directory.canonicalFile.parentFile == File(context.filesDir, "privileged-edit-sessions").canonicalFile)
            directory.deleteRecursively()
        }
        if (::remoteRoot.isInitialized) {
            check(remoteRoot.matches(Regex("/data/local/tmp/autojs6-privileged-backup-ui-[a-f0-9-]{36}")))
            File(context.filesDir, "privileged-edit-backups").listFiles().orEmpty()
                .filter { it.name.matches(Regex("[a-f0-9-]{36}\\.json")) }
                .forEach { file ->
                    val record = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return@forEach
                    if (record.optString("source").startsWith("$remoteRoot/") || record.optString("requestedSource").startsWith("$remoteRoot/")) {
                        AtomicFile(file).delete()
                    }
                }
            shell("rm -rf -- ${quote(remoteRoot)}")
        }
        if (::fixtureContext.isInitialized) fixtureContext.deletePreferences()
        if (::localRoot.isInitialized) {
            check(localRoot.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            check(localRoot.deleteRecursively())
        }
        Pref.get().edit().apply {
            preferences.forEach { (key, value) -> if (value.first) putString(key, value.second) else remove(key) }
        }.commit()
    }

    @Test
    fun pathMenuOpensBackupsAndPreviewIsReadOnly() {
        val source = createVersions("preview.txt", "before\n", "after\n")
        val entry = entries(source).single()
        onMain { assertTrue(explorer.findViewById<View>(R.id.explorer_path_more).performClick()) }
        await("Path menu should contain Edit backups") { onMain { windowViews().any { it.id == R.id.explorer_privileged_backups && it.isShown } } }
        onMain { assertTrue(windowViews().last { it.id == R.id.explorer_privileged_backups && it.isShown }.performClick()) }
        awaitRow(entry)
        openEntry(entry)
        val monitor = instrumentation.addMonitor(EditActivity::class.java.name, null, false)
        val preview = try {
            clickText(R.string.text_view)
            (instrumentation.waitForMonitorWithTimeout(monitor, 45_000L) as? EditActivity).also {
                assertNotNull("A text backup should open in the editor", it)
            }!!
        } finally { instrumentation.removeMonitor(monitor) }
        activities += preview
        val editor = onMain { preview.findViewById<EditorView>(R.id.editor_view) }
        await("The preview should load the backup read only") {
            onMain { !editor.editor.isLoadingText && editor.editor.textSnapshot == "before\n" && editor.isReadOnly() }
        }
        assertEquals("after\n", remoteRead(source))
    }

    @Test
    fun restoreRequiresConfirmationAndLeavesAnExistingDirtyEditorUntouched() {
        val source = createVersions("restore.txt", "old version\n", "current version\n")
        val entry = entries(source).single()
        val uri = PrivilegedEditSessions.create(context, mode, source).also { actualSessions += it }
        val editor = launchEditor(uri, source, "current version\n")
        onMain { editor.editor.jumpToEnd(); editor.editor.insert("unsaved draft\n") }
        await("The existing editor should be dirty") { onMain { editor.saveStickyDirty } }
        launchExplorer()
        openManager(source)
        openEntry(entry)
        clickText(R.string.text_restore)
        awaitConfirmation(R.string.text_restore)
        clickText(R.string.dialog_button_cancel)
        assertEquals("current version\n", remoteRead(source))
        openEntry(entry)
        clickText(R.string.text_restore)
        awaitConfirmation(R.string.text_restore)
        clickPositive(R.string.text_restore)
        await("The selected backup should become the source") { remoteRead(source) == "old version\n" }
        assertEquals("current version\nunsaved draft\n", onMain { editor.editor.textSnapshot })
        assertTrue(onMain { editor.saveStickyDirty })
        val failure = save(editor).exceptionOrNull()
        assertTrue(failure.toString(), failure is PrivilegedFileOperationException)
        assertEquals("SOURCE_CHANGED", (failure as PrivilegedFileOperationException).code)
        assertEquals("old version\n", remoteRead(source))
        assertTrue(entries(source).any { it.id != entry.id })
    }

    @Test
    fun sourceChangeAfterRestoreConfirmationRefusesOverwriteAndKeepsTheBackupVisible() {
        val source = createVersions("changed.txt", "old version\n", "current version\n")
        val entry = entries(source).single()
        openManager(source)
        openEntry(entry)
        clickText(R.string.text_restore)
        awaitConfirmation(R.string.text_restore)
        remoteWrite(source, "changed after confirmation\n")
        clickPositive(R.string.text_restore)
        await("Restore conflict should offer an explicit refresh") { onMain { windowViews().filterIsInstance<TextView>().any { it.isShown && it.text.toString() == context.getString(R.string.text_failed) } } }
        assertEquals("changed after confirmation\n", remoteRead(source))
        assertTrue(entries(source).any { it.id == entry.id })
        assertTrue(onMain { windowViews().any { it.tag == entry.id } })
    }

    @Test
    fun deletingAChangedBackupRequiresFreshConfirmationAndDoesNotDeleteTheOriginal() {
        val source = createVersions("delete.txt", "old version\n", "current version\n")
        remoteWrite(entries(source).single().backupPath, "backup changed outside AutoJs6\n")
        val entry = entries(source).single()
        assertEquals("changed", entry.status)
        openManager(source)
        openEntry(entry)
        clickText(R.string.text_delete)
        awaitConfirmation(R.string.text_delete)
        clickText(R.string.dialog_button_cancel)
        assertTrue(entries(source).any { it.id == entry.id })
        openEntry(entry)
        clickText(R.string.text_delete)
        awaitConfirmation(R.string.text_delete)
        clickPositive(R.string.text_delete)
        await("The explicitly deleted backup should disappear") { entries(source).none { it.id == entry.id } }
        assertEquals("current version\n", remoteRead(source))
    }

    @Test
    fun retentionChoicesApplyCleanupOnlyToTheIsolatedLibraryAndKeepItsLatestVersion() {
        val source = createVersions("retention.txt", "v0\n", "v1\n", "v2\n", "v3\n")
        val before = entries(source)
        assertEquals(3, before.size)
        val newest = before.maxBy { it.createdAt }
        openManager(source)
        await("All three backup rows should be visible for the layout preview") {
            onMain { before.all { entry -> windowViews().any { it.tag == entry.id && it.isShown } } }
        }
        saveListPreview()
        clickText(R.string.text_privileged_backup_policy)
        clickText(R.string.text_privileged_backup_max_versions)
        clickText("1")
        clickText(R.string.text_privileged_backup_max_bytes)
        clickText("16 MiB")
        clickPositive(R.string.dialog_button_confirm)
        await("The selected retention policy should be persisted") {
            PrivilegedBackupManager.policy(fixtureContext) == RetentionPolicy(1, 16L * 1024 * 1024)
        }
        await("Automatic cleanup should retain the newest eligible backup") { entries(source).size == 1 }
        assertEquals(newest.id, entries(source).single().id)
        assertEquals("v3\n", remoteRead(source))
    }

    private fun saveListPreview() {
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(context.cacheDir, "privileged-backup-preview.png").outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun launchExplorer() {
        // The restore test keeps an existing editor and its first explorer alive. A separate
        // task guarantees a new probe instance for startActivitySync instead of task reuse.
        val activity = instrumentation.startActivitySync(Intent(context, ExplorerPathMenuProbeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ExplorerPathMenuProbeActivity
        activities += activity
        onMain {
            val themed = object : ContextThemeWrapper(activity, R.style.AppTheme) {
                override fun getApplicationContext(): Context = fixtureContext
            }
            explorer = ExplorerView(themed).apply {
                setPluginActionProvider(ExplorerSurface.UNSPECIFIED, null)
                setExplorer(Explorer(ExplorerFileProvider(), 0), ExplorerDirPage.createRoot(localRoot))
            }
            activity.showExplorer(explorer)
        }
        await("The test explorer should load") { onMain { explorer.isAttachedToWindow && !explorer.isRefreshing } }
    }

    private fun createVersions(name: String, initial: String, vararg versions: String): String {
        val source = "$remoteRoot/$name"
        remoteWrite(source, initial)
        val uri = PrivilegedEditSessions.create(fixtureContext, mode, source)
        versions.forEach { text -> PrivilegedEditSessions.save(fixtureContext, uri, text.toByteArray(), "UTF-8", false) }
        return source
    }

    private fun entries(source: String) = PrivilegedBackupManager.list(fixtureContext, source).filter { it.status != "missing" }

    private fun openManager(source: String) {
        onMain { PrivilegedBackupUi.show(explorer, source, remoteRoot, mode) }
        entries(source).firstOrNull()?.let(::awaitRow)
    }

    private fun awaitRow(entry: BackupEntry) = await("The backup should appear in the compact list") {
        onMain { windowViews().any { it.tag == entry.id && it.isShown } && hasEnabledText(R.string.text_privileged_backup_policy) }
    }

    private fun openEntry(entry: BackupEntry) {
        awaitRow(entry)
        onMain { assertTrue(windowViews().last { it.tag == entry.id && it.isShown }.performClick()) }
    }

    private fun launchEditor(uri: Uri, source: String, text: String): EditorView {
        val monitor = instrumentation.addMonitor(EditActivity::class.java.name, null, false)
        val activity = try {
            onMain { assertTrue(EditActivity.editPrivilegedDocument(context, uri, File(source).name)) }
            instrumentation.waitForMonitorWithTimeout(monitor, 45_000L) as EditActivity
        } finally { instrumentation.removeMonitor(monitor) }
        activities += activity
        val editor = onMain { activity.findViewById<EditorView>(R.id.editor_view) }
        await("The original should load in the existing editor") { onMain { editor.editor.textSnapshot == text && !editor.editor.isLoadingText } }
        return editor
    }

    private fun save(editor: EditorView): Result<String> {
        val result = AtomicReference<Result<String>>()
        val finished = CountDownLatch(1)
        onMain { editor.save().subscribe({ result.set(Result.success(it)); finished.countDown() }, { result.set(Result.failure(it)); finished.countDown() }) }
        assertTrue(finished.await(60, TimeUnit.SECONDS))
        return result.get()
    }

    private fun awaitConfirmation(label: Int) = await("A destructive action must show its separate confirmation") {
        onMain { positiveButton(label) != null }
    }

    private fun clickPositive(label: Int) {
        awaitConfirmation(label)
        onMain { assertTrue(positiveButton(label)!!.performClick()) }
    }

    private fun positiveButton(label: Int): TextView? = windowViews().filterIsInstance<TextView>().lastOrNull {
        it.isShown && it.isEnabled && it.text.toString() == context.getString(label) &&
            runCatching { it.resources.getResourceEntryName(it.id) == "md_buttonDefaultPositive" }.getOrDefault(false)
    }

    private fun hasEnabledText(label: Int) = windowViews().filterIsInstance<TextView>().any {
        it.isShown && it.isEnabled && it.text.toString() == context.getString(label)
    }

    private fun clickText(label: Int) = clickText(context.getString(label))
    private fun clickText(text: String) = await("Missing UI action: $text") {
        onMain {
            val view = windowViews().filterIsInstance<TextView>().lastOrNull { it.isShown && it.isEnabled && it.text.toString() == text }
            view?.let { clickableParent(it)?.performClick() } == true
        }
    }

    private fun clickableParent(view: View): View? = if (view.isClickable) view else (view.parent as? View)?.let(::clickableParent)
    private fun windowViews() = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }

    private fun remoteWrite(path: String, text: String) = shell("printf %s ${quote(Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))} | base64 -d > ${quote(path)}")
    private fun remoteRead(path: String) = Base64.decode(shell("base64 < ${quote(path)}"), Base64.DEFAULT).toString(Charsets.UTF_8)
    private fun shell(command: String): String = JSONObject(service.execCommand(command)).let {
        assertEquals(it.toString(), 0, it.getInt("code")); it.optString("result")
    }
    private fun quote(value: String) = "'${value.replace("'", "'\\''")}'"

    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000L
        while (SystemClock.uptimeMillis() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        throw AssertionError(message)
    }

    private fun <T> onMain(action: () -> T): T {
        val value = AtomicReference<T>()
        val failure = AtomicReference<Throwable>()
        instrumentation.runOnMainSync { try { value.set(action()) } catch (error: Throwable) { failure.set(error) } }
        failure.get()?.let { throw it }
        return value.get()
    }

    private class FixtureContext(base: Context, root: File, private val token: String) : ContextWrapper(base) {
        private val privateFiles = File(root, "files").apply { check(mkdir()) }
        private val privateCache = File(root, "cache").apply { check(mkdir()) }
        private val preferenceNames = ConcurrentHashMap.newKeySet<String>()
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = privateFiles
        override fun getCacheDir() = privateCache
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val privateName = "backup-ui-$token-$name"
            preferenceNames += privateName
            return baseContext.getSharedPreferences(privateName, mode)
        }
        fun deletePreferences() { preferenceNames.forEach(baseContext::deleteSharedPreferences) }
    }
}
