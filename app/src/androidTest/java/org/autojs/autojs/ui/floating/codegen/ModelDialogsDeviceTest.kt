package org.autojs.autojs.ui.floating.codegen

import android.app.Activity
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.accessibility.codegen.store.ModelCatalogCompatibility
import org.autojs.autojs.core.accessibility.codegen.store.ModelSource
import org.autojs.autojs.core.accessibility.codegen.store.ModelVersionInfo
import org.autojs.autojs.core.accessibility.codegen.store.ModelVersionStatus
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Opt in with -e codegenModelsDevice true on an unlocked API 24+ debug installation with
 * SYSTEM_ALERT_WINDOW granted. Fixtures stay in memory and never fetch or install a model.
 * UiAutomation leaves the host's accessibility services enabled. Locale and font overrides
 * belong to test contexts only; neither app preferences nor device settings are modified.
 */
@RunWith(AndroidJUnit4::class)
class ModelDialogsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val cleanup = ArrayList<() -> Unit>()
    private var activity: Activity? = null

    @Before
    fun startBackdropWithoutSuppressingAccessibility() {
        assumeTrue("Pass -e codegenModelsDevice true to run model dialog device tests",
            InstrumentationRegistry.getArguments().getString("codegenModelsDevice") == "true")
        assumeTrue("Grant SYSTEM_ALERT_WINDOW before running model dialog device tests", Settings.canDrawOverlays(context))
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        activity = instrumentation.startActivitySync(Intent().apply {
            setClassName(context.packageName, "org.autojs.autojs.ui.floating.codegen.CodeGenSmokeActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        instrumentation.waitForIdleSync()
    }

    @After
    fun releaseDialogsAndBackdrop() {
        main {
            try {
                cleanup.asReversed().forEach { it() }
            } finally {
                cleanup.clear()
                activity?.finish()
                activity = null
            }
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun comparisonHasAllTwelveRowsAndReachableDifferencesInBothWindows() {
        for (floating in listOf(false, true)) {
            val dialog = duplicate(floating)
            constrainWindow(dialog)
            awaitLayout(dialog)
            main {
                assertWindowMode(dialog, floating)
                assertComparisonContents(comparison(dialog))
                assertButtonsReachable(dialog, DialogAction.POSITIVE, DialogAction.NEUTRAL, DialogAction.NEGATIVE)
            }
            assertEveryComparisonCellReachable(dialog)
            main { dialog.dismiss() }
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun duplicateChoicesAndBackDismissAnswerExactlyOnceInBothWindows() {
        val choices = listOf(
            DialogAction.POSITIVE to ModelDuplicateDialog.Choice.REPLACE,
            DialogAction.NEUTRAL to ModelDuplicateDialog.Choice.KEEP_BOTH,
            DialogAction.NEGATIVE to ModelDuplicateDialog.Choice.CANCEL,
        )
        for (floating in listOf(false, true)) {
            for ((action, expected) in choices) {
                val answers = ArrayList<ModelDuplicateDialog.Choice>()
                val dialog = duplicate(floating) { answers += it }
                awaitLayout(dialog)
                main {
                    assertButtonsReachable(dialog, DialogAction.POSITIVE, DialogAction.NEUTRAL, DialogAction.NEGATIVE)
                    assertTrue(dialog.getActionButton(action).performClick())
                    // A queued second click and the dismiss fallback must not answer again.
                    dialog.getActionButton(action).performClick()
                    dialog.dismiss()
                }
                waitOnMain("Duplicate choice did not close") { !dialog.isShowing && answers.size == 1 }
                instrumentation.waitForIdleSync()
                main { assertEquals(listOf(expected), answers) }
            }
            val answers = ArrayList<ModelDuplicateDialog.Choice>()
            val dialog = duplicate(floating) { answers += it }
            pressBack(dialog)
            waitOnMain("Back did not cancel the duplicate choice") { !dialog.isShowing && answers.size == 1 }
            main { dialog.dismiss() }
            instrumentation.waitForIdleSync()
            main { assertEquals(listOf(ModelDuplicateDialog.Choice.CANCEL), answers) }
        }
    }

    @Test
    fun identicalModelHasOnlyCloseAndNotifiesExactlyOnceInBothWindows() {
        for (floating in listOf(false, true)) {
            val closed = AtomicInteger()
            val dialog = main {
                ModelDuplicateDialog.showIdentical(ownerContext(floating), floating) { closed.incrementAndGet() }
                    .also { cleanup += { it.dismiss() } }
            }
            awaitLayout(dialog)
            main {
                assertWindowMode(dialog, floating)
                assertButtonsReachable(dialog, DialogAction.POSITIVE)
                assertEquals(dialog.context.getString(R.string.text_codegen_model_got_it),
                    dialog.getActionButton(DialogAction.POSITIVE).text.toString())
                assertTrue(texts(dialog).contains(dialog.context.getString(R.string.text_codegen_model_identical)))
                assertTrue(descendants(dialog.view).none { it is ModelComparisonView })
                dialog.getActionButton(DialogAction.POSITIVE).performClick()
                dialog.getActionButton(DialogAction.POSITIVE).performClick()
                dialog.dismiss()
            }
            waitOnMain("Identical model dialog did not close") { !dialog.isShowing && closed.get() == 1 }
            instrumentation.waitForIdleSync()
            assertEquals(1, closed.get())
        }
    }

    @Test
    fun successfulTransferKeepsComparisonAndNeverCancelsInBothWindows() {
        for (floating in listOf(false, true)) {
            for (finishAction in listOf(DialogAction.POSITIVE, DialogAction.NEUTRAL)) {
                val cancelled = AtomicInteger()
                val deleted = AtomicInteger()
                val transfer = transfer(floating, cancelled)
                val dialog = main { transfer.show() }
                awaitLayout(dialog)
                val originalComparison = main { comparison(dialog) }
                main {
                    assertWindowMode(dialog, floating)
                    transfer.updateProgress(512, 1024)
                    val bar = progress(dialog)
                    assertFalse(bar.isIndeterminate)
                    assertEquals(bar.max / 2, bar.progress)
                    val formatter = ModelComparisonFormatter(dialog.context)
                    assertTrue(texts(dialog).contains(dialog.context.getString(R.string.text_codegen_model_download_progress,
                        formatter.size(512), formatter.size(1024))))
                    transfer.showSuccess("2.0") { deleted.incrementAndGet() }
                    assertSame(originalComparison, comparison(dialog))
                    assertEquals(View.GONE, bar.visibility)
                    transfer.updateProgress(1, 2)
                    transfer.showFailure("Late failure must not replace success")
                }
                awaitLayout(dialog)
                main {
                    assertSame(originalComparison, comparison(dialog))
                    assertTrue(texts(dialog).contains(dialog.context.getString(R.string.text_codegen_model_updated, "2.0")))
                    assertButtonsReachable(dialog, DialogAction.POSITIVE, DialogAction.NEUTRAL)
                    assertEquals(dialog.context.getString(R.string.text_done), dialog.getActionButton(DialogAction.POSITIVE).text.toString())
                    assertEquals(dialog.context.getString(R.string.text_codegen_model_delete_old),
                        dialog.getActionButton(DialogAction.NEUTRAL).text.toString())
                }
                assertEveryComparisonCellReachable(dialog)
                main {
                    dialog.getActionButton(finishAction).performClick()
                    dialog.getActionButton(finishAction).performClick()
                    transfer.dispose()
                    transfer.dispose()
                }
                waitOnMain("Successful transfer did not close") { !dialog.isShowing }
                instrumentation.waitForIdleSync()
                assertEquals("Finishing a successful transfer must not cancel it", 0, cancelled.get())
                assertEquals(if (finishAction == DialogAction.NEUTRAL) 1 else 0, deleted.get())
            }
        }
    }

    @Test
    fun activeTransferButtonAndBackCancelOnlyOnceInBothWindows() {
        for (floating in listOf(false, true)) {
            for (useBack in listOf(false, true)) {
                val cancelled = AtomicInteger()
                val transfer = transfer(floating, cancelled)
                val dialog = main { transfer.show() }
                awaitLayout(dialog)
                main {
                    transfer.updateProgress(512, 0)
                    assertTrue(progress(dialog).isIndeterminate)
                    assertButtonsReachable(dialog, DialogAction.NEGATIVE)
                }
                if (useBack) pressBack(dialog) else main {
                    dialog.getActionButton(DialogAction.NEGATIVE).performClick()
                    dialog.getActionButton(DialogAction.NEGATIVE).performClick()
                }
                waitOnMain("Active transfer did not cancel") { !dialog.isShowing && cancelled.get() == 1 }
                main {
                    transfer.dispose()
                    transfer.dispose()
                    transfer.showSuccess("2.0")
                    transfer.showFailure("Late outcome must not reopen a disposed dialog")
                    transfer.updateProgress(1024, 1024)
                }
                instrumentation.waitForIdleSync()
                assertEquals(1, cancelled.get())
                main { assertFalse(dialog.isShowing) }
            }
        }
    }

    @Test
    fun failedTransferKeepsComparisonAndClosesWithoutCancellationInBothWindows() {
        for (floating in listOf(false, true)) {
            val cancelled = AtomicInteger()
            val transfer = transfer(floating, cancelled)
            val dialog = main { transfer.show() }
            awaitLayout(dialog)
            val originalComparison = main { comparison(dialog) }
            val message = main { dialog.context.getString(R.string.text_codegen_model_reason_network) }
            main {
                transfer.showFailure(message)
                transfer.updateProgress(1024, 1024)
                transfer.showSuccess("2.0")
            }
            awaitLayout(dialog)
            main {
                assertSame(originalComparison, comparison(dialog))
                assertTrue(texts(dialog).contains(message))
                assertTrue(texts(dialog).contains(dialog.context.getString(R.string.text_codegen_model_transfer_failed)))
                assertEquals(View.GONE, progress(dialog).visibility)
                assertButtonsReachable(dialog, DialogAction.POSITIVE)
                assertEquals(dialog.context.getString(R.string.text_close), dialog.getActionButton(DialogAction.POSITIVE).text.toString())
            }
            assertEveryComparisonCellReachable(dialog)
            main {
                dialog.getActionButton(DialogAction.POSITIVE).performClick()
                transfer.dispose()
                transfer.dispose()
            }
            waitOnMain("Failed transfer did not close") { !dialog.isShowing }
            instrumentation.waitForIdleSync()
            assertEquals(0, cancelled.get())
        }
    }

    @Test
    fun largeFontAndRtlKeepWrappedValuesScrollableAndButtonsReachable() {
        val original = main { Configuration(requireNotNull(activity).resources.configuration) }
        for (locale in listOf(Locale.ENGLISH, Locale.forLanguageTag("ar"))) {
            val testContext = main { configuredActivityContext(locale, 2f) }
            val dialog = main {
                ModelDuplicateDialog.showSameModel(testContext, oldInfo(), newInfo()) {}
                    .also { cleanup += { it.dismiss() } }
            }
            main {
                // A per-window direction exercises RTL without changing the backdrop or system.
                dialog.window!!.decorView.layoutDirection = testContext.resources.configuration.layoutDirection
            }
            constrainWindow(dialog)
            awaitLayout(dialog)
            main {
                assertWindowMode(dialog, floating = false)
                val comparison = comparison(dialog)
                assertEquals(2f, comparison.resources.configuration.fontScale, 0f)
                assertEquals(testContext.resources.configuration.layoutDirection, comparison.layoutDirection)
                val table = table(comparison)
                assertTrue("Large text must have a horizontally scrollable table", table.width > comparison.width)
                val oldName = (table.getChildAt(1) as LinearLayout).getChildAt(1) as TextView
                assertTrue("Long model names must wrap beyond their explicit newline", oldName.lineCount > oldName.text.count { it == '\n' } + 1)
                for (text in descendants(table).filterIsInstance<TextView>()) {
                    assertNull("Comparison values must not be ellipsized", text.ellipsize)
                    assertNotNull(text.layout)
                    for (line in 0 until text.layout.lineCount) assertEquals(0, text.layout.getEllipsisCount(line))
                    assertEquals(text.text.length, text.layout.getLineEnd(text.layout.lineCount - 1))
                }
                comparison.scrollTo(0, 0)
                assertEquals(0, comparison.scrollX)
                comparison.scrollTo(table.width, 0)
                assertTrue("Horizontal scroll must reach the other columns", comparison.scrollX > 0)
                assertButtonsReachable(dialog, DialogAction.POSITIVE, DialogAction.NEUTRAL, DialogAction.NEGATIVE)
            }
            assertEveryComparisonCellReachable(dialog)
            main { dialog.getActionButton(DialogAction.NEGATIVE).performClick() }
            instrumentation.waitForIdleSync()
        }
        main {
            val current = requireNotNull(activity).resources.configuration
            assertEquals(original.fontScale, current.fontScale, 0f)
            assertEquals(original.locales, current.locales)
            assertEquals(original.layoutDirection, current.layoutDirection)
        }
    }

    private fun duplicate(
        floating: Boolean,
        onChoice: (ModelDuplicateDialog.Choice) -> Unit = {},
    ): MaterialDialog = main {
        ModelDuplicateDialog.showSameModel(ownerContext(floating), oldInfo(), newInfo(), floating, onChoice)
            .also { cleanup += { it.dismiss() } }
    }

    private fun transfer(floating: Boolean, cancelled: AtomicInteger): ModelTransferDialog = main {
        ModelTransferDialog(ownerContext(floating), newInfo(), oldInfo(), floating, onCancel = { cancelled.incrementAndGet() })
            .also { cleanup += { it.dispose() } }
    }

    private fun ownerContext(floating: Boolean): Context =
        if (floating) context.applicationContext else requireNotNull(activity)

    private fun configuredActivityContext(locale: Locale, fontScale: Float): Context {
        val backdrop = requireNotNull(activity)
        val configuration = Configuration(backdrop.resources.configuration).apply {
            this.fontScale = fontScale
            setLocale(locale)
            setLayoutDirection(locale)
        }
        val configured = backdrop.createConfigurationContext(configuration)
        // Keep Activity in the wrapper chain for its window token, while resources and the
        // cloned inflater use only the test's configuration context.
        return object : ContextThemeWrapper(backdrop, R.style.AppTheme) {
            override fun getResources(): Resources = configured.resources
            override fun getAssets(): AssetManager = configured.assets
        }
    }

    private fun assertComparisonContents(comparison: ModelComparisonView) {
        val labels = listOf(
            R.string.text_codegen_compare_name, R.string.text_codegen_compare_version,
            R.string.text_codegen_compare_source, R.string.text_codegen_compare_content,
            R.string.text_codegen_compare_size, R.string.text_codegen_compare_compatibility,
            R.string.text_codegen_compare_dataset, R.string.text_codegen_compare_metrics,
            R.string.text_codegen_compare_released_at, R.string.text_codegen_compare_installed_at,
            R.string.text_codegen_compare_license, R.string.text_codegen_compare_status,
        )
        // Same name, official source, compatibility and license; all other payloads differ.
        val differentRows = setOf(1, 3, 4, 6, 7, 8, 9, 11)
        val table = table(comparison)
        assertEquals("E.5 requires one header and twelve value rows", 13, table.childCount)
        val headings = table.getChildAt(0) as LinearLayout
        assertEquals(listOf(R.string.text_codegen_compare_field, R.string.text_codegen_compare_installed, R.string.text_codegen_compare_new)
            .map { comparison.context.getString(it) }, (0 until headings.childCount).map { (headings.getChildAt(it) as TextView).text.toString() })
        labels.forEachIndexed { index, label ->
            val row = table.getChildAt(index + 1) as LinearLayout
            assertEquals("Each comparison row needs its label and both values", 3, row.childCount)
            val text = (row.getChildAt(0) as TextView).text.toString()
            val expected = comparison.context.getString(label)
            assertEquals(if (index in differentRows) comparison.context.getString(R.string.text_codegen_compare_different, expected) else expected, text)
            assertEquals("Differences must have a non-color marker", index in differentRows, text.contains('≠'))
        }
        fun value(row: Int, column: Int) = ((table.getChildAt(row + 1) as LinearLayout).getChildAt(column) as TextView).text.toString()
        assertTrue(value(1, 1).contains("1.0"))
        assertTrue(value(1, 2).contains("2.0"))
        assertEquals("aaaaaaaaaaaa", value(3, 1))
        assertEquals("bbbbbbbbbbbb", value(3, 2))
        assertEquals("-", value(9, 2))
        assertEquals("Apache-2.0", value(10, 1))
        assertEquals("Apache-2.0", value(10, 2))
    }

    private fun assertEveryComparisonCellReachable(dialog: MaterialDialog) {
        constrainWindow(dialog)
        awaitLayout(dialog)
        val cells = main {
            val scroll = dialog.customView as ScrollView
            assertTrue("The fixed comparison must be vertically scrollable", scroll.canScrollVertically(-1) || scroll.canScrollVertically(1))
            val comparison = comparison(dialog)
            val table = table(comparison)
            val maximumTableWidth = (dialog.window!!.decorView.width *
                comparison.resources.configuration.fontScale.coerceAtLeast(1f)).roundToInt()
            assertTrue("Long names must not expand the table beyond its window/font budget: " +
                "table=${table.width}, viewport=${comparison.width}, maximum=$maximumTableWidth",
                table.width <= maximumTableWidth)
            descendants(table).filterIsInstance<TextView>()
        }
        for (cell in cells) {
            var request = "Scroll was not applied"
            try {
                request = main { scrollLastLineIntoView(dialog, cell) }
                waitOnMain("Comparison text cannot be reached: ${main { cell.text }}") { lastLineVisible(cell) }
            } catch (failure: AssertionError) {
                val details = main { comparisonDiagnostics(dialog, cell) }
                val screenshot = captureDialogWindow(dialog)
                throw AssertionError("${failure.message}\n$request\n$details\nDialog-only screenshot: $screenshot", failure)
            }
        }
        main { assertTrue("The last status row must be reachable below the fold", (dialog.customView as ScrollView).scrollY > 0) }
    }

    private fun scrollLastLineIntoView(dialog: MaterialDialog, cell: TextView): String {
        val horizontal = comparison(dialog)
        val vertical = dialog.customView as ScrollView
        val requested = lastLineRect(cell)
        val horizontalViewport = horizontal.width - horizontal.paddingLeft - horizontal.paddingRight
        val verticalViewport = vertical.height - vertical.paddingTop - vertical.paddingBottom
        assertTrue("The whole text line must fit horizontally: line=${requested.width()}, viewport=$horizontalViewport",
            requested.width() <= horizontalViewport)
        assertTrue("The whole text line must fit vertically: line=${requested.height()}, viewport=$verticalViewport",
            requested.height() <= verticalViewport)

        // Exercise the same independent offsets a user can reach by scrolling each container.
        // API 24 requestRectangleOnScreen propagates a shared temporary rectangle through these
        // nested scrollers; scrolling can overwrite it with ancestor bounds and overscroll by the
        // dialog title's height. This test checks readable content, not that framework helper.
        val inHorizontal = Rect(requested).also { horizontal.offsetDescendantRectToMyCoords(cell, it) }
        val beforeX = horizontal.scrollX
        horizontal.scrollTo((inHorizontal.left - horizontal.paddingLeft).coerceAtLeast(0), horizontal.scrollY)
        val inVertical = Rect(requested).also { vertical.offsetDescendantRectToMyCoords(cell, it) }
        val beforeY = vertical.scrollY
        vertical.scrollTo(vertical.scrollX, (inVertical.top - vertical.paddingTop).coerceAtLeast(0))
        return "requested=$requested, horizontalContent=$inHorizontal, x=$beforeX -> ${horizontal.scrollX}, " +
            "verticalContent=$inVertical, y=$beforeY -> ${vertical.scrollY}"
    }

    private fun lastLineRect(view: TextView): Rect {
        val layout = requireNotNull(view.layout)
        val last = layout.lineCount - 1
        return Rect(view.totalPaddingLeft, view.totalPaddingTop + layout.getLineTop(last),
            view.width - view.totalPaddingRight, view.totalPaddingTop + layout.getLineBottom(last))
    }

    private fun lastLineVisible(view: TextView): Boolean {
        val visible = Rect()
        if (!view.getLocalVisibleRect(visible)) return false
        return visible.contains(lastLineRect(view))
    }

    private fun comparisonDiagnostics(dialog: MaterialDialog, cell: TextView): String = buildString {
        val localVisible = Rect()
        val visible = cell.getLocalVisibleRect(localVisible)
        val layout = requireNotNull(cell.layout)
        val last = layout.lineCount - 1
        appendLine("cellText=${cell.text}, lastLine=${lastLineRect(cell)}, localVisible=$visible:$localVisible")
        appendLine("textSize=${cell.textSize}, fontScale=${cell.resources.configuration.fontScale}, " +
            "layout=${layout.width}x${layout.height}, lines=${layout.lineCount}, " +
            "lineLeft=${layout.getLineLeft(last)}, lineRight=${layout.getLineRight(last)}, " +
            "lineTop=${layout.getLineTop(last)}, lineBottom=${layout.getLineBottom(last)}, " +
            "totalPadding=${cell.totalPaddingLeft},${cell.totalPaddingTop},${cell.totalPaddingRight},${cell.totalPaddingBottom}")
        val window = dialog.window!!
        val frame = Rect().also(window.decorView::getWindowVisibleDisplayFrame)
        appendLine("windowType=${window.attributes.type}, requestedWindow=${window.attributes.width}x${window.attributes.height}, visibleFrame=$frame")
        var ancestor: View? = cell
        while (ancestor != null) {
            val current = ancestor
            val local = Rect()
            val shown = current.getLocalVisibleRect(local)
            val location = IntArray(2).also(current::getLocationOnScreen)
            appendLine("${current.javaClass.simpleName}: bounds=${current.left},${current.top},${current.right},${current.bottom}; " +
                "measured=${current.measuredWidth}x${current.measuredHeight}; screen=${location.contentToString()}; " +
                "scroll=${current.scrollX},${current.scrollY}; padding=${current.paddingLeft},${current.paddingTop},${current.paddingRight},${current.paddingBottom}; " +
                "visible=$shown:$local; layoutRequested=${current.isLayoutRequested}; direction=${current.layoutDirection}" +
                if (current is ViewGroup) "; clipChildren=${current.clipChildren}; clipToPadding=${current.clipToPadding}" else "")
            ancestor = current.parent as? View
        }
    }

    /** Draw just this fixture dialog; do not capture the device screen or another app's windows. */
    private fun captureDialogWindow(dialog: MaterialDialog): String = runCatching {
        val bitmap = main {
            val decor = dialog.window!!.decorView
            check(decor.width > 0 && decor.height > 0) { "Dialog has no drawable bounds" }
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also {
                decor.draw(Canvas(it))
            }
        }
        try {
            val directory = requireNotNull(context.getExternalFilesDir("codegen-p6"))
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create dialog screenshot directory" }
            val file = File(directory, "model-dialog-failure-${SystemClock.uptimeMillis()}.png")
            FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            file.absolutePath
        } finally {
            bitmap.recycle()
        }
    }.getOrElse { "unavailable (${it.javaClass.simpleName}: ${it.message})" }

    private fun assertButtonsReachable(dialog: MaterialDialog, vararg expected: DialogAction) {
        val decor = dialog.window!!.decorView
        val origin = IntArray(2).also(decor::getLocationOnScreen)
        for (action in DialogAction.values()) {
            val button = dialog.getActionButton(action)
            if (action !in expected) {
                assertFalse("Unexpected $action action", button.visibility == View.VISIBLE)
                continue
            }
            assertEquals(View.VISIBLE, button.visibility)
            assertTrue("$action must remain enabled and laid out", button.isEnabled && button.width > 0 && button.height > 0)
            val location = IntArray(2).also(button::getLocationOnScreen)
            assertTrue("$action crosses the left edge", location[0] >= origin[0])
            assertTrue("$action crosses the right edge", location[0] + button.width <= origin[0] + decor.width)
            assertTrue("$action crosses the top edge", location[1] >= origin[1])
            assertTrue("$action crosses the bottom edge", location[1] + button.height <= origin[1] + decor.height)
            val visible = Rect()
            assertTrue("$action is clipped from the window", button.getGlobalVisibleRect(visible))
            assertTrue("$action is partly hidden", visible.width() >= button.width - 1 && visible.height() >= button.height - 1)
        }
    }

    private fun assertWindowMode(dialog: MaterialDialog, floating: Boolean) {
        val attributes = dialog.window!!.attributes
        if (floating) {
            val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            assertEquals(expected, attributes.type)
        } else {
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION, attributes.type)
        }
        assertEquals(0, attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    private fun constrainWindow(dialog: MaterialDialog) = main {
        val metrics = dialog.context.resources.displayMetrics
        val width = (320 * metrics.density).roundToInt().coerceAtMost(metrics.widthPixels)
        val height = (420 * metrics.density).roundToInt().coerceAtMost((metrics.heightPixels * 0.8f).roundToInt())
        dialog.window!!.setLayout(width, height)
    }

    private fun pressBack(dialog: MaterialDialog) {
        awaitLayout(dialog)
        waitOnMain("Dialog did not receive focus for Back") { dialog.window!!.decorView.hasWindowFocus() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    private fun awaitLayout(dialog: MaterialDialog) = waitOnMain("Model dialog did not lay out") {
        val decor = dialog.window!!.decorView
        dialog.isShowing && decor.isShown && decor.width > 0 && decor.height > 0 && !decor.isLayoutRequested && !dialog.view.isLayoutRequested
    }

    private fun comparison(dialog: MaterialDialog): ModelComparisonView = descendants(dialog.view).filterIsInstance<ModelComparisonView>().single()
    private fun table(comparison: ModelComparisonView): LinearLayout = comparison.getChildAt(0) as LinearLayout
    private fun progress(dialog: MaterialDialog): ProgressBar = descendants(dialog.customView!!).filterIsInstance<ProgressBar>().single()
    private fun texts(dialog: MaterialDialog): List<String> = descendants(dialog.view).filterIsInstance<TextView>().map { it.text.toString() }

    private fun descendants(root: View): List<View> = buildList {
        val queue = ArrayDeque<View>()
        queue.addLast(root)
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) queue.addLast(view.getChildAt(index))
        }
    }

    private fun waitOnMain(message: String, timeout: Long = 6_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (main(condition)) return
            SystemClock.sleep(50)
        }
        assertTrue(message, main(condition))
    }

    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }

    private fun oldInfo() = ModelVersionInfo(
        modelId = "device-test-model",
        displayName = mapOf(
            "en" to "Device test model with a deliberately long readable name that must wrap instead of being truncated in a narrow comparison column",
            "ar" to "نموذج اختبار على الجهاز باسم طويل لقراءة جميع الكلمات عند المقارنة داخل عمود ضيق دون إخفاء أي جزء من النص",
        ),
        versionCode = 1,
        versionName = "1.0",
        source = ModelSource.OFFICIAL,
        sha256 = "a".repeat(64),
        sizeBytes = 23_633,
        featureSchemaVersion = 1,
        compatibility = ModelCatalogCompatibility.COMPATIBLE,
        datasetId = "device-dataset-v1",
        datasetCreatedAt = Instant.parse("2026-10-01T00:00:00Z"),
        realTestTop1Survival = 0.82,
        realTestRuleTop1Survival = 0.72,
        releasedAt = Instant.parse("2026-10-02T00:00:00Z"),
        installedAtMillis = Instant.parse("2026-10-03T00:00:00Z").toEpochMilli(),
        license = "Apache-2.0",
        status = ModelVersionStatus.CURRENT,
    )

    private fun newInfo() = oldInfo().copy(
        versionCode = 2,
        versionName = "2.0",
        sha256 = "b".repeat(64),
        sizeBytes = 32_768,
        datasetId = "device-dataset-v2",
        datasetCreatedAt = Instant.parse("2026-10-04T00:00:00Z"),
        realTestTop1Survival = 0.91,
        releasedAt = Instant.parse("2026-10-05T00:00:00Z"),
        installedAtMillis = null,
        status = null,
    )
}
