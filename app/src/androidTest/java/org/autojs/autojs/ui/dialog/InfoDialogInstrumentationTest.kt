package org.autojs.autojs.ui.dialog

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.autojs.autojs.core.plugin.center.PluginCenterItem
import org.autojs.autojs.core.plugin.center.PluginInfoDialogManager
import org.autojs.autojs.model.explorer.ExplorerFileItem
import org.autojs.autojs.theme.app.ColorInfoDialogManager
import org.autojs.autojs.ui.main.MainActivity
import org.autojs.autojs.ui.main.scripts.ApkInfoDialogManager
import org.autojs.autojs.ui.main.scripts.EditableFileInfoDialogManager
import org.autojs.autojs.ui.main.scripts.ImageInfoDialogManager
import org.autojs.autojs.ui.main.scripts.MediaInfoDialogManager
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

class InfoDialogInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dialogs = mutableListOf<InfoDialog>()
    private val files = mutableListOf<File>()
    private var resumedActivity: MainActivity? = null
    private val activity: MainActivity get() = checkNotNull(resumedActivity) { "MainActivity has not been resumed" }

    @Before
    fun launchActivity() {
        // Connect UiAutomation before any view under test exists. ListView gives an item the accessibility delegate that
        // reports ACTION_CLICK only when accessibility is enabled at the moment the item view is created, and accessibility
        // is enabled for this process by the UiAutomation connection, which the first uiAutomation access makes lazily. A
        // popup menu opened before that first access keeps item nodes without ACTION_CLICK for as long as it is shown; in a
        // fresh process that was the first test to open one, see packageComponentsAppearOnlyInTheMoreInformationDialog.
        val automation = instrumentation.uiAutomation
        val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
        awaitMain(message = "Accessibility did not become enabled for the test process") { accessibilityManager.isEnabled }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // LeakCanary asks for the notification permission with an activity of its own once per fresh install. That
            // activity becomes the active window and hides the dialogs under test from rootInActiveWindow.
            automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // Wait for RESUMED instead of startActivitySync, which only accepts an activity it saw being created and then
        // waits for an idle main thread, 45 s at most. The first launch after a read-only emulator boots takes 30 s and
        // more, and once that launch has timed out the test cannot finish the activity it never received; MainActivity is
        // singleTask, so every later launch of the class only reaches the leaked instance as onNewIntent and times out
        // too. A resumed instance, whoever created it, is all these tests need.
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitMain(LAUNCH_TIMEOUT_MS, "MainActivity did not reach RESUMED") {
            resumedActivity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().firstOrNull { !it.isFinishing }
            resumedActivity != null
        }
    }

    @After
    fun cleanUp() {
        instrumentation.runOnMainSync {
            dialogs.forEach { it.dialog.dismiss() }
            resumedActivity?.finish()
        }
        files.forEach { it.delete() }
    }

    @Test
    fun insertionKeepsHiddenRowsAndSeparatorsAsStableAnchors() {
        instrumentation.runOnMainSync {
            val items = InfoDialogItems(activity)
            val first = items.addItem("First", "1", id = "first")
            val hidden = items.addItem("Hidden", "2", id = "hidden").apply { isVisible = false }
            val separator = items.addSeparator(id = "separator")
            val last = items.addItem("Last", "3", id = "last")
            items.addItem("Head", "0", index = 0, id = "head")
            items.addItemBefore(hidden, "Before", "4", id = "before")
            items.addItemAfter(separator, "After", "5", id = "after")
            items.addItemAfter(last, "Tail", "6", id = "tail")
            val expected = listOf("head", "first", "before", "hidden", "separator", "after", "last", "tail")
            assertEquals(expected, (0 until items.size).map { items.entryAt(it).id })
            hidden.isVisible = true
            assertEquals(expected, (0 until items.size).map { items.entryAt(it).id })
            assertSame(first, items["first"])
            assertSame(separator, items.entry("separator"))
            assertIllegalArgument { items.addItem("Invalid", index = -1) }
            assertIllegalArgument { items.addItem("Invalid", index = items.size + 1) }
            assertIllegalArgument { items.addItem("Duplicate", id = "first") }
            val foreign = InfoDialogItems(activity).addItem("Foreign")
            assertIllegalArgument { items.addItemBefore(foreign, "Invalid") }
            assertEquals(expected.size, items.size)
            items.remove(first)
            items.remove(separator)
            items.remove(last)
            assertEquals(listOf("head", "before", "hidden", "after", "tail"), (0 until items.size).map { items.entryAt(it).id })
            assertEquals(items.size, items.childCount)
        }
    }

    @Test
    fun longKeysRealignAfterInsertRenameAndHideInLtrAndRtl() {
        instrumentation.runOnMainSync {
            for (rtl in listOf(false, true)) {
                val configuration = android.content.res.Configuration(activity.resources.configuration).apply {
                    fontScale = 1.8F
                    setLocale(if (rtl) Locale.forLanguageTag("ar") else Locale.ENGLISH)
                }
                val items = InfoDialogItems(activity.createConfigurationContext(configuration)).apply {
                    layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                }
                val first = items.addItem("Key", "A value that must remain readable")
                val long = items.addItem("A very long translated information label with multiple words", "Second value")
                fun measure() {
                    val width = (320 * items.resources.displayMetrics.density).toInt()
                    items.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED)
                    items.layout(0, 0, width, items.measuredHeight)
                }
                measure()
                assertEquals(first.labelView.width, long.labelView.width)
                assertTrue(first.valueView.width > 0)
                assertTrue(long.labelView.lineCount > 1)
                if (rtl) assertTrue(first.labelView.left > first.valueView.left)
                else assertTrue(first.labelView.left < first.valueView.left)
                val wideKeyWidth = first.labelView.width
                long.isVisible = false
                measure()
                assertTrue(
                    "rtl=$rtl; before=$wideKeyWidth; after=${first.labelView.width}; " +
                            "requested=${first.labelView.layoutParams.width}; measured=${first.labelView.measuredWidth}",
                    first.labelView.width < wideKeyWidth,
                )
                first.key = "A different long label after showing the dialog"
                measure()
                assertTrue(first.labelView.width > 0)
                val inserted = items.addItemBefore(first, "Another translated label", "Inserted value")
                measure()
                assertEquals(first.labelView.width, inserted.labelView.width)
                assertTrue(inserted.view.bottom <= first.view.top)
            }
        }
    }

    @Test
    fun liveItemsCopyOnlyTheValueAndDismissCancelsWorkAndPreservesCallbacks() {
        lateinit var info: InfoDialog
        lateinit var job: Job
        var builderDismisses = 0
        var extraDismisses = 0
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val originalClip = clipboard.primaryClip
        try {
            instrumentation.runOnMainSync {
                info = InfoDialog(activity)
                val initial = info.items.addItem("Initial", "Before show")
                info.builder.title("Information test").dismissListener { builderDismisses++ }
                info.show({ extraDismisses++ })
                dialogs += info
                assertTrue(initial.valueView.performClick())
                assertEquals("Before show", clipboard.primaryClip!!.getItemAt(0).text.toString())
                val live = info.items.addItemAfter(initial, "Live", "After show")
                live.setValue("Actual value", " [ Estimated ]")
                assertTrue(live.valueView.performClick())
                assertEquals("Actual value", clipboard.primaryClip!!.getItemAt(0).text.toString())
                assertEquals("Actual value [ Estimated ]", live.valueView.text.toString())
                live.setValue(null)
                assertFalse(live.valueView.hasOnClickListeners())
                assertEquals(activity.getString(R.string.text_unknown), live.valueView.text.toString())
                live.setValueIfAbsent("Loaded")
                live.setValueIfAbsent("Must not replace loaded value")
                assertEquals("Loaded", live.value)
                job = info.scope.launch { awaitCancellation() }
                info.dialog.dismiss()
            }
            instrumentation.waitForIdleSync()
            assertTrue(job.isCancelled)
            assertEquals(1, builderDismisses)
            assertEquals(1, extraDismisses)
        } finally {
            clipboard.setPrimaryClip(originalClip ?: ClipData.newPlainText("", ""))
        }
    }

    @Test
    fun allManagersExposeUsableItemsAndKeepMetadataAndFailureStates() {
        val textFile = temporaryFile(".txt").apply { writeText("Hello\r\nWorld", Charsets.UTF_8) }
        val imageFile = temporaryFile(".png").apply {
            val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
            try {
                outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally {
                bitmap.recycle()
            }
        }
        val invalidPackage = temporaryFile(".apk").apply { writeText("Invalid APK") }
        val invalidMedia = temporaryFile(".wav").apply { writeText("Invalid media") }
        lateinit var text: InfoDialog
        lateinit var image: InfoDialog
        lateinit var apk: InfoDialog
        lateinit var media: InfoDialog
        lateinit var color: InfoDialog
        lateinit var plugin: InfoDialog
        instrumentation.runOnMainSync {
            text = checkNotNull(EditableFileInfoDialogManager.showEditableFileInfoDialog(activity, textFile)).also { dialogs += it }
            image = ImageInfoDialogManager.showImageInfoDialog(activity, imageFile).also { dialogs += it }
            apk = ApkInfoDialogManager.showApkInfoDialog(activity, invalidPackage).also { dialogs += it }
            media = MediaInfoDialogManager.showMediaInfoDialog(activity, ExplorerFileItem(invalidMedia, null)).also { dialogs += it }
            color = checkNotNull(ColorInfoDialogManager.showColorInfoDialog(activity, Color.RED)).also { dialogs += it }
            plugin = PluginInfoDialogManager.showPluginInfoDialog(
                activity,
                PluginCenterItem(
                    title = "Information test plugin", packageName = "org.autojs.test.info", versionName = "1.0.0",
                    isInstalled = true, packageSize = 1024, author = "Author", collaborators = listOf("Contributor"),
                ),
            ).also { dialogs += it }
            dialogs.forEach { info ->
                val first = info.items.entryAt(0)
                val custom = info.items.addItemBefore(first, "Custom", "Custom value", id = "custom")
                assertSame(custom, info.items.entryAt(0))
                assertTrue(custom.valueView.hasOnClickListeners())
            }
        }
        awaitMain {
            text.items["lineCount"].value == "2" && image.items["resolution"].value == "32 x 16" &&
                    apk.items["installation"].isVisible && media.items["containerFormat"].value != null &&
                    color.items["colorHex"].value != null
        }
        instrumentation.runOnMainSync {
            assertEquals("Windows (CRLF)", text.items["lineBreak"].value)
            assertEquals("PNG", image.items["format"].value)
            assertEquals("APK", apk.items["format"].value)
            assertTrue(media.items["fileSize"].value!!.isNotBlank())
            assertEquals("org.autojs.test.info", plugin.items["packageName"].value)
            assertEquals("Contributor", plugin.items["pluginItemInfoCollaboratorsFirst"].value)
            assertTrue(dialogs.all { it.items["custom"].value == "Custom value" })
        }
    }

    @Test
    fun packageComponentsAppearOnlyInTheMoreInformationDialog() {
        lateinit var info: InfoDialog
        val source = File(context.applicationInfo.sourceDir)
        instrumentation.runOnMainSync {
            info = ApkInfoDialogManager.showApkInfoDialog(activity, source).also { dialogs += it }
            assertIllegalArgument { info.items["components"] }
            assertIllegalArgument { info.items["pluginInfo"] }
            assertTrue(checkNotNull(info.dialog.optionsView).isShown)
        }
        captureIfRequested("package-main")
        instrumentation.runOnMainSync { checkNotNull(info.dialog.optionsView).performClick() }
        clickStableText(activity.getString(R.string.text_more_information))
        val componentsLabel = activity.getString(R.string.apk_info_components)
        awaitText(componentsLabel)
        awaitText(activity.getString(R.string.apk_info_components_apk, 1, 1, 0))
        val automation = instrumentation.uiAutomation
        val originalFlags = automation.serviceInfo.flags
        try {
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            val root = automation.rootInActiveWindow
            val labels = root.findAccessibilityNodeInfosByViewId("${context.packageName}:id/info_item_label")
            assertEquals(1, labels.size)
            assertEquals(componentsLabel, labels.single().text.toString())
            assertTrue(root.findAccessibilityNodeInfosByText(source.name).isNotEmpty())
            instrumentation.runOnMainSync { assertTrue(info.dialog.isShowing) }
            captureIfRequested("package-more-information")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitText(activity.getString(R.string.apk_info_package_name))
            instrumentation.runOnMainSync { assertTrue(info.dialog.isShowing) }
        } finally {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
        }
    }

    @Test
    fun openOptionsKeepTheirActionsWhenAnotherOptionArrivesBeforeThem() {
        lateinit var info: InfoDialog
        var originalClicks = 0
        var insertedClicks = 0
        val originalLabel = "Original information action"
        val insertedLabel = "Additional information action"
        val options = mutableListOf(
            MaterialDialog.OptionMenuItemSpec(originalLabel) { originalClicks++ },
        )
        instrumentation.runOnMainSync {
            info = InfoDialog(activity).apply {
                items.addItem("Metadata", "Available while optional information loads")
                builder.title("Options snapshot test").options(options).autoDismiss(false)
                show()
            }.also { dialogs += it }
        }
        openOptionsAfterLayout(info)
        awaitStableText(originalLabel)
        instrumentation.runOnMainSync {
            // Match an optional plugin result arriving while the first popup remains visible.
            options.add(0, MaterialDialog.OptionMenuItemSpec(insertedLabel) { insertedClicks++ })
        }
        clickStableText(originalLabel)
        awaitMain { originalClicks == 1 || insertedClicks != 0 }
        instrumentation.runOnMainSync {
            assertEquals("The visible original option must retain its original action", 1, originalClicks)
            assertEquals("An item not shown in this popup must not receive its click", 0, insertedClicks)
            assertTrue(info.dialog.isShowing)
        }
        openOptionsAfterLayout(info, previousPopupLabels = listOf(originalLabel))
        val insertedBounds = Rect().also { awaitStableText(insertedLabel).getBoundsInScreen(it) }
        val originalBounds = Rect().also { awaitStableText(originalLabel).getBoundsInScreen(it) }
        assertTrue("The next popup must include the new option before the original one", insertedBounds.bottom <= originalBounds.top)
        clickStableText(insertedLabel)
        awaitMain { insertedClicks == 1 }
        instrumentation.runOnMainSync {
            assertEquals(1, originalClicks)
            assertEquals(1, insertedClicks)
            assertTrue(info.dialog.isShowing)
        }
    }

    private fun openOptionsAfterLayout(info: InfoDialog, previousPopupLabels: List<String> = emptyList()) {
        instrumentation.waitForIdleSync()
        val deadline = SystemClock.elapsedRealtime() + 30_000
        var previousBounds: Rect? = null
        var stableSince = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            var bounds: Rect? = null
            instrumentation.runOnMainSync {
                val decor = info.dialog.window?.decorView
                val anchor = info.dialog.optionsView
                if (info.dialog.isShowing && decor?.isAttachedToWindow == true && decor.hasWindowFocus() &&
                    !decor.isLayoutRequested && anchor?.isShown == true && anchor.isEnabled &&
                    anchor.width > 0 && anchor.height > 0 && !anchor.isLayoutRequested
                ) {
                    val visible = Rect()
                    if (anchor.getGlobalVisibleRect(visible) && !visible.isEmpty) bounds = visible
                }
            }
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val previousPopupGone = root != null && previousPopupLabels.none { label ->
                root.findAccessibilityNodeInfosByText(label).any { it.text?.toString() == label && it.isVisibleToUser }
            }
            val now = SystemClock.elapsedRealtime()
            if (bounds != null && previousPopupGone) {
                if (bounds == previousBounds) {
                    if (now - stableSince >= 250L) {
                        instrumentation.waitForIdleSync()
                        instrumentation.runOnMainSync {
                            val decor = checkNotNull(info.dialog.window).decorView
                            val anchor = checkNotNull(info.dialog.optionsView)
                            assertTrue("The parent dialog must regain focus before opening its popup", decor.hasWindowFocus())
                            assertTrue("The options anchor must finish layout before opening its popup",
                                anchor.isShown && anchor.isEnabled && anchor.width > 0 && anchor.height > 0 &&
                                    !anchor.isLayoutRequested && !decor.isLayoutRequested)
                            assertTrue("The options button click was rejected", anchor.performClick())
                        }
                        return
                    }
                } else {
                    previousBounds = bounds
                    stableSince = now
                }
            } else {
                previousBounds = null
                stableSince = 0L
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("The information dialog did not become ready to open its options popup")
    }

    private fun awaitText(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString() == text }
                ?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Information dialog text did not appear: $text")
    }

    private fun awaitStableText(text: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        var previousBounds: Rect? = null
        var previousWindow = -1
        var stableSince = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            val now = SystemClock.elapsedRealtime()
            val node = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(text)
                ?.firstOrNull { it.text?.toString() == text && it.isVisibleToUser && it.isEnabled }
            val bounds = node?.let { candidate -> Rect().also(candidate::getBoundsInScreen) }
            if (node != null && bounds != null && !bounds.isEmpty) {
                if (bounds == previousBounds && node.windowId == previousWindow) {
                    // Popup nodes can exist before their entrance animation or anchor layout settles.
                    if (now - stableSince >= 250L) return node
                } else {
                    previousBounds = bounds
                    previousWindow = node.windowId
                    stableSince = now
                }
            } else {
                previousBounds = null
                previousWindow = -1
                stableSince = 0L
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Information dialog text did not become visible and stable: $text")
    }

    private fun clickStableText(text: String) {
        val label = awaitStableText(text)
        assertTrue("The menu item disappeared before its click: $text", label.refresh())
        var target = label
        while (!target.isClickable || target.actionList.none { it.id == AccessibilityNodeInfo.ACTION_CLICK }) {
            target = target.parent ?: throw AssertionError("No accessible click action for menu item: $text; ${describeForFailure(label)}")
        }
        assertTrue("The menu item must remain visible and enabled: $text", target.isVisibleToUser && target.isEnabled)
        // Activate the actual popup row. Retaining a text-node coordinate across popup animation
        // can dismiss the popup outside its row without ever invoking the item's callback.
        assertTrue("The menu click was rejected: $text", target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    /** The ancestry of a node and the windows on screen, for the message of a failed accessibility lookup. */
    private fun describeForFailure(node: AccessibilityNodeInfo): String {
        val chain = generateSequence(node) { it.parent }.joinToString(" < ") {
            val name = it.className?.toString()?.substringAfterLast('.') ?: "?"
            val actions = it.actionList.joinToString(",") { action -> action.id.toString() }
            name + (if (it.isClickable) "[clickable]" else "") + (if (it.isEnabled) "" else "[disabled]") +
                (if (it.isVisibleToUser) "" else "[invisible]") + "{" + actions + "}" + "@w" + it.windowId
        }
        val window = node.window?.let { "type=${it.type}, active=${it.isActive}, focused=${it.isFocused}, layer=${it.layer}" }
        val windows = instrumentation.uiAutomation.windows.joinToString { window ->
            "w${window.id}(type=${window.type}, active=${window.isActive}, focused=${window.isFocused}, title=${window.title}, " +
                "root=${window.root?.className?.toString()?.substringAfterLast('.')})"
        }
        return "node chain: $chain; node window: $window; windows: $windows"
    }

    private fun captureIfRequested(name: String) {
        if (InstrumentationRegistry.getArguments().getString("infoDialogScreenshots") != "true") return
        instrumentation.waitForIdleSync()
        // Wait for the window animation as well as the UI queue before capturing the dialog.
        SystemClock.sleep(300)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val directory = File(context.getExternalFilesDir(null), "info-dialog-ui-evidence").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertIllegalArgument(action: () -> Unit) {
        val error = runCatching(action).exceptionOrNull()
        assertTrue("Expected IllegalArgumentException, got $error", error is IllegalArgumentException)
    }

    private fun temporaryFile(suffix: String): File = File.createTempFile("info-dialog-", suffix, context.cacheDir).also { files += it }

    private fun awaitMain(
        timeoutMs: Long = 30_000,
        message: String = "Information dialog metadata did not become ready",
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        throw AssertionError(message)
    }

    private companion object {
        /** The first launch of a cold, freshly booted read-only emulator has been measured above 30 s. */
        const val LAUNCH_TIMEOUT_MS = 120_000L
    }
}
