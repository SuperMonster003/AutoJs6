package org.autojs.autojs.ui.explorer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.system.Os
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.ProgressBar
import android.widget.ListView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.external.fileprovider.AppFileProvider
import org.autojs.autojs.model.explorer.Explorer
import org.autojs.autojs.model.explorer.ExplorerDirPage
import org.autojs.autojs.model.explorer.ExplorerFileProvider
import org.autojs.autojs.model.explorer.PrivilegedAccessMode
import org.autojs.autojs.model.explorer.PrivilegedExplorerEntry
import org.autojs.autojs.model.explorer.PrivilegedExplorerFileItem
import org.autojs.autojs.model.explorer.PrivilegedExternalFiles.FileDescription
import org.autojs.autojs.model.explorer.PrivilegedExternalFiles.OpenKind
import org.autojs.autojs.model.explorer.PrivilegedExternalFiles.PreparedOpen
import org.autojs.autojs.model.explorer.PrivilegedFileOperationException
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** UI protocol tests never launch a viewer, request authorization, or allocate a large copy. */
@RunWith(AndroidJUnit4::class)
class PrivilegedExternalOpenUiDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val launches = CopyOnWriteArrayList<Intent>()
    private val noHandler = AtomicBoolean()
    private lateinit var activity: ExplorerPathMenuProbeActivity
    private lateinit var explorer: ExplorerView
    private lateinit var directory: File
    private lateinit var source: File
    private lateinit var applicationContext: Context
    private lateinit var item: PrivilegedExplorerFileItem
    private var controller: PrivilegedExplorerOperationsController? = null

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        directory = File(context.cacheDir, "privileged-external-open-ui-${UUID.randomUUID()}").apply { check(mkdir()) }
        source = File(directory, "ordinary-readable.mp4").apply { writeText("original readable bytes") }
        val cache = File(directory, "private-cache").apply { check(mkdir()) }
        val files = File(directory, "private-files").apply { check(mkdir()) }
        applicationContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir() = cache
            override fun getFilesDir() = files
        }
        activity = instrumentation.startActivitySync(Intent(context, ExplorerPathMenuProbeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)) as ExplorerPathMenuProbeActivity
        onMain {
            val themed = object : ContextThemeWrapper(activity, R.style.AppTheme) {
                override fun getApplicationContext() = this@PrivilegedExternalOpenUiDeviceTest.applicationContext
                override fun startActivity(intent: Intent) {
                    launches += Intent(intent)
                    if (noHandler.get()) throw ActivityNotFoundException("No viewer in this UI fixture")
                }
            }
            explorer = ExplorerView(themed).apply {
                setPluginActionProvider(ExplorerSurface.UNSPECIFIED, null)
                setExplorer(Explorer(ExplorerFileProvider(), 0), ExplorerDirPage.createRoot(directory))
            }
            item = PrivilegedExplorerFileItem(
                PrivilegedExplorerEntry(source.name, false, source.length(), source.lastModified()),
                source.path, ExplorerDirPage.createRoot(directory), PrivilegedAccessMode.ROOT,
            )
            activity.showExplorer(explorer)
        }
        await("The fixture should be attached") { onMain { explorer.isAttachedToWindow && !explorer.isRefreshing } }
    }

    @After
    fun tearDown() {
        onMain { controller?.detach() }
        await("Cancellation should finish before fixture cleanup") { onMain { controller?.isBusy != true } }
        if (::activity.isInitialized) {
            onMain { activity.finishAndRemoveTask() }
            await("The fixture activity should close") { onMain { activity.isDestroyed } }
        }
        if (::directory.isInitialized) {
            check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            check(directory.name.matches(Regex("privileged-external-open-ui-[a-f0-9-]{36}")))
            check(directory.deleteRecursively())
        }
    }

    @Test
    fun ordinaryReadableFileUsesItsOriginalFileProviderDescriptorEvenInRootMode() {
        open()
        await("The ordinary file should launch without a privileged copy") { launches.isNotEmpty() && onMain { controller?.isBusy == false } }
        val intent = launches.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(AppFileProvider.AUTHORITY, intent.data!!.authority)
        assertEquals("video/mp4", intent.type)
        assertReadOnlyGrant(intent)
        context.contentResolver.openFileDescriptor(intent.data!!, "r")!!.use { descriptor ->
            source.inputStream().use { original ->
                assertEquals("The external viewer must receive the original inode, not a copied file", Os.fstat(original.fd).st_ino, Os.fstat(descriptor.fileDescriptor).st_ino)
            }
        }
        assertFalse(File(applicationContext.cacheDir, "privileged-view-copies").exists())
    }

    @Test
    fun missingViewerReportsNoAppWithoutOfferingOrMakingACompatibilityCopy() {
        noHandler.set(true)
        open()
        await("The missing viewer should be reported") { hasTextContaining(context.getString(R.string.error_no_applications_available_for_viewing_this_file)) }
        assertEquals(1, launches.size)
        assertFalse(hasText(R.string.text_explorer_stream_compatibility_open))
        assertFalse(File(applicationContext.cacheDir, "privileged-view-copies").exists())
    }

    @Test
    fun unsupportedSeekableAccessRequiresConfirmationAndCancelCopiesNothing() {
        val access = StubAccess().apply { prepareError = PrivilegedFileOperationException("STREAM_UNSUPPORTED", "No seekable transport") }
        open(access)
        await("Compatibility fallback must ask first") { hasText(R.string.text_explorer_stream_compatibility_open) && hasTextContaining(source.path) }
        assertEquals(1, access.described.get())
        assertEquals(0, access.copied.get())
        clickText(R.string.dialog_button_cancel)
        assertTrue(launches.isEmpty())
        assertEquals(0, access.copied.get())
        assertFalse(onMain { controller!!.isBusy })
    }

    @Test
    fun permissionFailureDoesNotTriggerTheCompatibilityPath() {
        val access = StubAccess().apply { prepareError = PrivilegedFileOperationException("ACCESS_UNAVAILABLE", "Permission was revoked") }
        open(access)
        await("The access failure should be shown") { hasTextContaining(context.getString(R.string.text_explorer_privileged_permission_lost)) }
        assertEquals(0, access.described.get())
        assertEquals(0, access.copied.get())
        assertTrue(launches.isEmpty())
        assertFalse(hasText(R.string.text_explorer_stream_compatibility_open))
    }

    @Test
    fun unhandledStreamReleasesItsTokenWithoutStartingACopy() {
        val access = StubAccess()
        noHandler.set(true)
        open(access)
        await("A token not handed to any viewer must be released") { access.released.get() == 1 }
        assertReadOnlyGrant(launches.single())
        assertEquals(0, access.copied.get())
        assertEquals(0, access.described.get())
    }

    @Test
    fun anExplicitCompatibilityConfirmationOpensACompleteReadOnlyPrivateCopy() {
        showCompatibilityMenu(PrivilegedExternalOpenAccess.forContext(applicationContext))
        clickCompatibilityMenuItem()
        await("The explicit copy should ask before occupying temporary space") { hasTextContaining(source.path) }
        assertTrue(launches.isEmpty())
        clickText(R.string.text_open)
        await("The complete compatibility copy should be handed to the viewer") { launches.size == 1 && onMain { !controller!!.isBusy } }
        val intent = launches.single()
        assertReadOnlyGrant(intent)
        assertEquals(source.readText(), context.contentResolver.openInputStream(intent.data!!)!!.bufferedReader().use { it.readText() })
        context.contentResolver.openFileDescriptor(intent.data!!, "r")!!.use { descriptor ->
            source.inputStream().use { original ->
                assertTrue("Compatibility opening should use a separate file", Os.fstat(original.fd).st_ino != Os.fstat(descriptor.fileDescriptor).st_ino)
            }
        }
        assertEquals("original readable bytes", source.readText())
    }

    @Test
    fun explicitCompatibilityCopyUsesLongProgressAndCancelClosesItsSourceWithoutLaunchingAViewer() {
        val access = StubAccess().apply { waitForCancellation = true }
        showCompatibilityMenu(access)
        clickCompatibilityMenuItem()
        await("Copying must still await confirmation") { hasTextContaining(source.path) }
        assertEquals(0, access.copied.get())
        clickText(R.string.text_open)
        await("Progress above 2 GiB should remain a valid percentage") {
            onMain { windowViews().filterIsInstance<ProgressBar>().any { it.isShown && !it.isIndeterminate && it.max == 100 && it.progress == 66 } }
        }
        assertEquals(1, access.copied.get())
        assertEquals(3L * 1024 * 1024 * 1024, access.confirmed.get()!!.size)
        clickText(R.string.dialog_button_cancel)
        await("Cancellation must reach and close the source") { access.sourceClosed.get() && onMain { !controller!!.isBusy } }
        assertTrue(launches.isEmpty())
        assertFalse(hasText(R.string.text_explorer_stream_copy_title))
        assertFalse(hasTextContaining(context.getString(R.string.text_explorer_privileged_operation_failed, context.getString(R.string.text_explorer_stream_copy_title), "")))
    }

    @Test
    fun detachingTheExplorerCancelsTheCompatibilityCopy() {
        val access = StubAccess().apply { waitForCancellation = true }
        showCompatibilityMenu(access)
        clickCompatibilityMenuItem()
        await("The compatibility confirmation should be visible") { hasTextContaining(source.path) }
        clickText(R.string.text_open)
        await("The copy should have started") { access.copied.get() == 1 }
        onMain { controller!!.detach() }
        await("The source should close after its host detaches") { access.sourceClosed.get() && onMain { !controller!!.isBusy } }
        assertTrue(launches.isEmpty())
    }

    @Test
    fun returningAfterAReadFailureShowsItOnceAndNeverStartsAnAutomaticCopy() {
        val access = StubAccess()
        open(access)
        await("The external open should be handed off") { launches.size == 1 && onMain { !controller!!.isBusy } }
        access.failure.set("Fixture viewer lost its read authorization")
        onMain { controller!!.onWindowFocusGained() }
        await("The returned reader error should remain actionable") { hasTextContaining("Fixture viewer lost its read authorization") }
        assertTrue(hasText(R.string.text_explorer_access_options))
        clickText(R.string.dialog_button_dismiss)
        onMain { controller!!.onWindowFocusGained() }
        await("The notice should be consumed once") { access.failureReads.get() >= 2 }
        assertFalse(hasTextContaining("Fixture viewer lost its read authorization"))
        assertEquals(0, access.copied.get())
        assertEquals(1, launches.size)
    }

    private fun open(access: PrivilegedExternalOpenAccess = PrivilegedExternalOpenAccess.forContext(applicationContext)) {
        onMain {
            controller = PrivilegedExplorerOperationsController(explorer, access)
            controller!!.open(item)
        }
    }

    private fun showCompatibilityMenu(access: PrivilegedExternalOpenAccess) {
        onMain {
            controller = PrivilegedExplorerOperationsController(explorer, access)
            controller!!.showMenu(explorer.findViewById(R.id.explorer_path_more), item)
        }
    }

    private fun clickCompatibilityMenuItem() {
        var observedTitles = emptyList<String>()
        try {
            await("The native popup should expose its compatibility action") {
                onMain {
                    val menus = windowViews().filterIsInstance<ListView>().filter { it.isShown }
                    observedTitles = menus.flatMap { menu ->
                        val adapter = menu.adapter ?: return@flatMap emptyList()
                        (0 until adapter.count).map { index -> adapter.getItem(index).toString() }
                    }
                    for (menu in menus.asReversed()) {
                        val adapter = menu.adapter ?: continue
                        val position = (0 until adapter.count).firstOrNull { index ->
                            (adapter.getItem(index) as? MenuItem)?.itemId == R.id.explorer_privileged_open_compatibility
                        } ?: continue
                        assertEquals(context.getString(R.string.text_explorer_stream_compatibility_open),
                            (adapter.getItem(position) as MenuItem).title.toString())
                        // The last of nine native menu rows can be below the popup's viewport.
                        // Scroll the real popup before clicking its row; do not bypass its listener.
                        if (position < menu.firstVisiblePosition || position > menu.lastVisiblePosition) {
                            menu.setSelection(position)
                            return@onMain false
                        }
                        val row = menu.getChildAt(position - menu.firstVisiblePosition) ?: return@onMain false
                        if (!row.isShown) return@onMain false
                        return@onMain menu.performItemClick(row, position, adapter.getItemId(position))
                    }
                    false
                }
            }
        } catch (error: AssertionError) {
            throw AssertionError("${error.message}; native menu items: $observedTitles", error)
        }
    }

    private fun assertReadOnlyGrant(intent: Intent) {
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertNotNull(intent.clipData)
        assertEquals(intent.data, intent.clipData!!.getItemAt(0).uri)
    }

    private inner class StubAccess : PrivilegedExternalOpenAccess {
        var prepareError: PrivilegedFileOperationException? = null
        var waitForCancellation = false
        val described = AtomicInteger()
        val copied = AtomicInteger()
        val released = AtomicInteger()
        val sourceClosed = AtomicBoolean()
        val confirmed = AtomicReference<FileDescription>()
        val failure = AtomicReference<String?>()
        val failureReads = AtomicInteger()
        override fun prepare(mode: PrivilegedAccessMode, path: String): PreparedOpen {
            prepareError?.let { throw it }
            return PreparedOpen(Uri.parse("content://${context.packageName}.privileged-view/fake-token"), OpenKind.STREAM, 3L * 1024 * 1024 * 1024, "video/mp4", "fake-token")
        }
        override fun describe(mode: PrivilegedAccessMode, path: String): FileDescription {
            described.incrementAndGet()
            return FileDescription(3L * 1024 * 1024 * 1024, 8L * 1024 * 1024 * 1024)
        }
        override fun release(prepared: PreparedOpen) { released.incrementAndGet() }
        override fun copy(mode: PrivilegedAccessMode, path: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean, confirmed: FileDescription): File {
            this.confirmed.set(confirmed)
            copied.incrementAndGet()
            try {
                onProgress(2L * 1024 * 1024 * 1024 + 17, 3L * 1024 * 1024 * 1024)
                if (waitForCancellation) {
                    val deadline = SystemClock.elapsedRealtime() + 15_000
                    while (!cancelled() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(10)
                    check(cancelled()) { "The UI did not cancel its compatibility copy" }
                    throw PrivilegedFileOperationException("CANCELLED", "Cancelled")
                }
                return File(applicationContext.cacheDir, "compatibility-result.mp4").apply { writeText("copied fixture") }
            } finally { sourceClosed.set(true) }
        }
        override fun consumeFailure(): String? {
            failureReads.incrementAndGet()
            return failure.getAndSet(null)
        }
    }

    private fun hasText(resource: Int) = onMain { windowViews().filterIsInstance<TextView>().any { it.isShown && it.text.toString() == context.getString(resource) } }
    private fun hasTextContaining(text: String) = onMain { windowViews().filterIsInstance<TextView>().any { it.isShown && it.text.toString().contains(text) } }
    private fun clickText(resource: Int) = await("Missing UI action: ${context.getString(resource)}") {
        onMain {
            val view = windowViews().filterIsInstance<TextView>().lastOrNull { it.isShown && it.isEnabled && it.text.toString() == context.getString(resource) }
            view?.let { clickableParent(it)?.performClick() } == true
        }
    }
    private fun clickableParent(view: View): View? = if (view.isClickable) view else (view.parent as? View)?.let(::clickableParent)
    private fun windowViews() = WindowInspector.getGlobalWindowViews().flatMap(::descendants)
    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
    }
    private fun await(message: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) { if (predicate()) return; SystemClock.sleep(20) }
        throw AssertionError(message)
    }
    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(action)) }
        return result.get().getOrThrow()
    }
}
