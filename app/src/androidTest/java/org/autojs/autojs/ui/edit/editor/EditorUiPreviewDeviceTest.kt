package org.autojs.autojs.ui.edit.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * The layout preview panel of "ui" scripts in the editor (3-Block Compose roadmap P11.10): the toolbar toggle shows the
 * layout of the script being edited, edits refresh it, tapping an element moves the cursor to its start tag, a failed
 * layout is explained in the panel, rotation keeps the panel, and the toggle is remembered for the file. The layout is
 * laid out at the size of the window and scaled down uniformly into the panel (D74), in both orientations.
 * Screenshots go to the files dir of the app, `editor-ui-preview-test/`, which tools/ui-preview/run_editor_ui_preview_test.py pulls
 * through `run-as` (the shell cannot read the external files dir of an app on newer Android versions).
 */
class EditorUiPreviewDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val originalEnginePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE)
    private val originalSelectionModePreference =
        captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE)
    private val originalPreviewFiles: List<String>? =
        MiscStateKeys.EDITOR_UI_PREVIEW_FILES.takeIf { it.isStored() }?.get()
    private val launchedEditActivities = CopyOnWriteArrayList<Activity>()

    @After
    fun tearDown() {
        finishLaunchedActivities()
        val editor = Pref.get().edit()
        originalEnginePreference.restore(editor, EditorEnginePreferences.KEY_EDITOR_ENGINE)
        originalSelectionModePreference.restore(editor, EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE)
        editor.commit()
        originalPreviewFiles?.let { MiscStateKeys.EDITOR_UI_PREVIEW_FILES.set(it, commit = true) }
            ?: MiscStateKeys.EDITOR_UI_PREVIEW_FILES.remove(commit = true)
    }

    @Test
    fun previewFollowsEditsTapsRotationAndTheFile() {
        val file = File(instrumentation.targetContext.cacheDir, "ui-preview-${SystemClock.uptimeMillis()}.js")
        file.writeText(INITIAL_TEXT)
        try {
            // Opening a "ui" script shows the toggle; turning it on shows the layout.
            var editorView = launchReadyEditor(file, INITIAL_TEXT)
            val panelMessage = { runOnMainSync { previewMessage(editorView) } }
            val panelState = { runOnMainSync { "${previewMessage(editorView)} | ${fitState(editorView)}" } }
            assertFalse(runOnMainSync { editorView.isUiPreviewShown })
            clickPreviewToggle(editorView)
            waitUntil("The panel should show the layout of the script", explain = panelMessage) {
                runOnMainSync { editorView.isUiPreviewShown && previewTexts(editorView).containsAll(listOf("Alpha", "Beta")) }
            }
            runOnMainSync {
                val designerButton = editorView.findViewById<View>(R.id.editor_ui_preview_open_in_designer)
                assertEquals(editorView.isThreeBlockComposeDesignerAvailable(), designerButton.visibility == View.VISIBLE)
                assertEquals(LinearLayout.VERTICAL, editorView.findViewById<LinearLayout>(R.id.editor_split).orientation)
            }
            log("shown", "elements=${runOnMainSync { previewTexts(editorView) }.size}")
            waitUntil("The layout should be laid out at the window size and scaled down into the panel", explain = panelState) {
                runOnMainSync { isScaledToFit(editorView) }
            }
            log("scale", "portrait ${runOnMainSync { describeFit(editorView) }}")

            // Editing a line refreshes the panel once typing pauses.
            runOnMainSync {
                editorView.editor.jumpTo(GAMMA_LINE, 0)
                editorView.editor.insert(GAMMA_SOURCE_LINE)
            }
            waitForText(editorView, EDITED_TEXT)
            waitUntil("The panel should follow the edit", explain = panelMessage) {
                runOnMainSync { "Gamma" in previewTexts(editorView) }
            }

            // Tapping an element moves the cursor to its start tag.
            runOnMainSync { editorView.editor.jumpTo(0, 0) }
            hideIme(editorView)
            waitUntil("The cursor should leave the layout before the tap") {
                runOnMainSync { editorView.editor.selection.startOffset == 0 }
            }
            tapCenterOf(runOnMainSync { previewTextView(editorView, "Gamma") })
            val gammaOffset = EDITED_TEXT.indexOf("<text id=\"gamma\"")
            waitUntil("Tapping Gamma should move the cursor to its line") {
                runOnMainSync { editorView.editor.selection.startOffset == gammaOffset }
            }
            hideIme(editorView)
            screenshot("portrait")
            log("tap", "offset=$gammaOffset")

            // Rotation keeps the panel and the layout; the split turns side by side.
            runOnMainSync { editorView.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            // The split keeps its height: the insets of a rotation are measured again after the new layout.
            waitUntil("Landscape should put the panel on the right half of an editor that keeps its height", LONG_TIMEOUT_MS, panelState) {
                runOnMainSync {
                    val split = editorView.findViewById<LinearLayout>(R.id.editor_split)
                    val pane = editorView.findViewById<View>(R.id.editor_ui_preview_pane)
                    split.orientation == LinearLayout.HORIZONTAL && split.width > split.height && split.height * 3 > editorView.height &&
                        pane.width * 3 > split.width && editorView.isUiPreviewShown &&
                        isScaledToFit(editorView) && "Gamma" in previewTexts(editorView)
                }
            }
            screenshot("landscape")
            log("scale", "landscape ${runOnMainSync { describeFit(editorView) }}")
            runOnMainSync { editorView.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            waitUntil("Portrait should put the panel back below the editor", LONG_TIMEOUT_MS, panelMessage) {
                runOnMainSync {
                    val split = editorView.findViewById<LinearLayout>(R.id.editor_split)
                    split.orientation == LinearLayout.VERTICAL && split.height > split.width &&
                        editorView.isUiPreviewShown && "Gamma" in previewTexts(editorView)
                }
            }
            log("rotation", "kept")

            // A layout that fails is explained at its line, over the last layout that succeeded.
            runOnMainSync {
                editorView.editor.jumpTo(GAMMA_LINE + 1, "    </vertical".length)
                editorView.editor.insert("x")
            }
            waitForText(editorView, BROKEN_TEXT)
            val failureHead = runOnMainSync {
                editorView.context.getString(R.string.text_editor_ui_preview_layout_failed, BROKEN_LINE, "@@").substringBefore("@@")
            }
            val lastLayoutNote = runOnMainSync { editorView.context.getString(R.string.text_editor_ui_preview_showing_last_layout) }
            waitUntil("The failure should be explained at its line", explain = panelMessage) {
                runOnMainSync {
                    val message = previewMessage(editorView)
                    message.startsWith(failureHead) && message.endsWith(lastLayoutNote) && "Gamma" in previewTexts(editorView)
                }
            }
            screenshot("failure")
            runOnMainSync { editorView.editor.replaceAllTextUndoably(EDITED_TEXT, "editor-ui-preview-test") }
            waitForText(editorView, EDITED_TEXT)
            waitUntil("The explanation should go once the layout is fixed", explain = panelMessage) {
                runOnMainSync { previewMessage(editorView).isEmpty() && "Gamma" in previewTexts(editorView) }
            }
            log("failure", "explained")
            saveEditor(editorView)
            assertTrue(
                "The file should be remembered with its preview on",
                MiscStateKeys.EDITOR_UI_PREVIEW_FILES.get().any { it.endsWith(file.name) },
            )

            // The file opens again with its preview on; turning it off forgets the file.
            finishLaunchedActivities()
            editorView = launchReadyEditor(file, EDITED_TEXT)
            waitUntil("The preview should come back with the file", explain = panelMessage) {
                runOnMainSync { editorView.isUiPreviewShown && "Gamma" in previewTexts(editorView) }
            }
            clickPreviewToggle(editorView)
            waitUntil("Turning the preview off should hide the panel") {
                runOnMainSync { !editorView.isUiPreviewShown }
            }
            assertFalse(MiscStateKeys.EDITOR_UI_PREVIEW_FILES.get().any { it.endsWith(file.name) })
            log("memory", "per-file")
        } finally {
            finishLaunchedActivities()
            file.delete()
        }
    }

    @Test
    fun scriptsThatAreNotUiHaveNoToggle() {
        val text = "toast(\"plain\");\n"
        val file = File(instrumentation.targetContext.cacheDir, "ui-preview-plain-${SystemClock.uptimeMillis()}.js")
        file.writeText(text)
        try {
            val editorView = launchReadyEditor(file, text)
            SystemClock.sleep(SETTLE_DELAY_MS)
            assertTrue(runOnMainSync { previewToggles(editorView).none { it.visibility == View.VISIBLE } })
            // Typing the "ui" directive shows it.
            runOnMainSync {
                editorView.editor.jumpTo(0, 0)
                editorView.editor.insert("\"ui\";\n")
            }
            waitUntil("The toggle should appear for a \"ui\" script") {
                runOnMainSync { previewToggles(editorView).any { it.visibility == View.VISIBLE } && !editorView.isUiPreviewShown }
            }
        } finally {
            finishLaunchedActivities()
            file.delete()
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private fun launchReadyEditor(file: File, expectedText: String): EditorView {
        EditorEnginePreferences.set(EditorEngineType.NATIVE)
        val intent = Intent(instrumentation.targetContext, EditActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EditorView.EXTRA_PATH, file.absolutePath)
            .putExtra(EditorView.EXTRA_NAME, file.nameWithoutExtension)
        val monitor = instrumentation.addMonitor(EditActivity::class.java.name, null, false)
        val launched = try {
            instrumentation.targetContext.startActivity(intent)
            instrumentation.waitForMonitorWithTimeout(monitor, ACTIVITY_LAUNCH_TIMEOUT_MS) as? EditActivity
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        assertNotNull("EditActivity should launch for ${file.name}", launched)
        launchedEditActivities.add(launched!!)
        instrumentation.waitForIdleSync()
        val editorView = runOnMainSync { launched.findViewById<EditorView>(R.id.editor_view) }
        assertNotNull(editorView)
        waitUntil("The editor should load ${file.name}", LONG_TIMEOUT_MS) {
            runOnMainSync {
                editorView.editor.engineType == EditorEngineType.NATIVE && editorView.editor.isReady &&
                    !editorView.editor.isLoadingText && editorView.editor.textSnapshot == expectedText
            }
        }
        return editorView
    }

    private fun clickPreviewToggle(editorView: EditorView) {
        waitUntil("The toolbar should show the preview toggle") {
            runOnMainSync { previewToggles(editorView).any { it.visibility == View.VISIBLE && it.isShown } }
        }
        runOnMainSync { previewToggles(editorView).first { it.isShown }.performClick() }
        instrumentation.waitForIdleSync()
    }

    private fun previewToggles(editorView: EditorView): List<View> =
        descendants(editorView.activity.window.decorView).filter { it.id == R.id.ui_preview }

    private fun previewTextViews(editorView: EditorView): List<TextView> {
        val host = editorView.findViewById<ViewGroup>(R.id.editor_ui_preview_host) ?: return emptyList()
        return descendants(host).filterIsInstance<TextView>().filter { it.isShown }
    }

    private fun previewTexts(editorView: EditorView) = previewTextViews(editorView).map { it.text.toString() }

    private fun previewTextView(editorView: EditorView, text: String) =
        previewTextViews(editorView).first { it.text.toString() == text }

    private fun previewMessage(editorView: EditorView): String {
        val message = editorView.findViewById<TextView>(R.id.editor_ui_preview_message)
        return if (message.visibility == View.VISIBLE) message.text.toString() else ""
    }

    /** D74: the layout is laid out at the size of the window and scaled down uniformly to fit the panel, centered. */
    private fun isScaledToFit(editorView: EditorView): Boolean {
        val host = editorView.findViewById<ViewGroup>(R.id.editor_ui_preview_host)
        val root = host.takeIf { it.childCount == 1 }?.getChildAt(0) ?: return false
        val window = editorView.rootView
        val scale = root.scaleX
        val shownWidth = root.width * scale
        val shownHeight = root.height * scale
        return root.width == window.width && root.height == window.height && root.scaleY == scale && scale > 0f && scale < 1f &&
            shownWidth <= host.width + 1 && shownHeight <= host.height + 1 &&
            (abs(shownWidth - host.width) <= 1 || abs(shownHeight - host.height) <= 1) &&
            abs(root.translationX * 2 + shownWidth - host.width) <= 1 && abs(root.translationY * 2 + shownHeight - host.height) <= 1
    }

    /** The sizes [isScaledToFit] compares, for a failure message. */
    private fun fitState(editorView: EditorView): String {
        val split = editorView.findViewById<LinearLayout>(R.id.editor_split)
        val host = editorView.findViewById<ViewGroup>(R.id.editor_ui_preview_host) ?: return "no panel"
        val root = host.takeIf { it.childCount == 1 }?.getChildAt(0) ?: return "children=${host.childCount}"
        val window = editorView.rootView
        val parent = split.parent as ViewGroup
        val siblings = (0 until parent.childCount).map { parent.getChildAt(it) }
            .joinToString(",") { "${it.javaClass.simpleName}:${it.height}:${it.visibility}" }
        val ancestors = generateSequence(parent as View) { it.parent as? View }.joinToString("<") {
            "${it.javaClass.simpleName}:${it.width}x${it.height}/m${it.measuredWidth}x${it.measuredHeight}" + if (it.isLayoutRequested) "/lr" else ""
        }
        val editorChildren = (0 until editorView.childCount).map { editorView.getChildAt(it) }
            .joinToString(",") { "${it.javaClass.simpleName}:${it.width}x${it.height}:${it.visibility}" }
        @Suppress("DEPRECATION")
        val bottomInset = editorView.rootWindowInsets?.systemWindowInsetBottom
        return "split=${split.orientation}:${split.width}x${split.height} window=${window.width}x${window.height} " +
            "host=${host.width}x${host.height} root=${root.width}x${root.height} scale=${root.scaleX}/${root.scaleY} " +
            "translation=${root.translationX},${root.translationY} texts=${previewTexts(editorView)} " +
            "siblings=$siblings ancestors=$ancestors editor=${editorView.orientation}:$editorChildren bottomInset=$bottomInset " +
            "padding=${editorView.paddingLeft},${editorView.paddingTop},${editorView.paddingRight},${editorView.paddingBottom}"
    }

    private fun describeFit(editorView: EditorView): String {
        val root = editorView.findViewById<ViewGroup>(R.id.editor_ui_preview_host).getChildAt(0)
        return "${root.width}x${root.height} scale=${String.format(Locale.ROOT, "%.3f", root.scaleX)}"
    }

    private fun descendants(root: View): List<View> {
        val views = ArrayList<View>()
        val pending = ArrayDeque<View>().apply { add(root) }
        while (pending.isNotEmpty()) {
            val view = pending.removeFirst()
            views += view
            if (view is ViewGroup) for (i in 0 until view.childCount) pending += view.getChildAt(i)
        }
        return views
    }

    private fun tapCenterOf(view: View) {
        val (x, y) = runOnMainSync {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            // The preview is scaled down into the panel (D74): the size on screen includes the scale of the ancestors.
            var scaleX = 1f
            var scaleY = 1f
            var current: View? = view
            while (current != null) {
                scaleX *= current.scaleX
                scaleY *= current.scaleY
                current = current.parent as? View
            }
            (location[0] + view.width * scaleX / 2f) to (location[1] + view.height * scaleY / 2f)
        }
        val downTime = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
        instrumentation.waitForIdleSync()
    }

    private fun hideIme(editorView: EditorView) {
        runOnMainSync {
            val inputMethodManager = instrumentation.targetContext.getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(editorView.windowToken, 0)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(SETTLE_DELAY_MS)
    }

    private fun waitForText(editorView: EditorView, expectedText: String) {
        waitUntil("The editor should hold the edited text") {
            runOnMainSync { editorView.editor.textSnapshot == expectedText }
        }
    }

    private fun saveEditor(editorView: EditorView) {
        val error = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        runOnMainSync {
            editorView.save().subscribe({ latch.countDown() }, { throwable ->
                error.set(throwable)
                latch.countDown()
            })
        }
        assertTrue("Saving should finish", latch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        error.get()?.let { throw AssertionError("Saving should succeed: ${it.message}", it) }
    }

    /** A best-effort screenshot for the evidence; a device that refuses one does not fail the test. */
    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(SETTLE_DELAY_MS)
        runCatching {
            val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
            val directory = File(instrumentation.targetContext.filesDir, SCREENSHOT_DIR).apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }.onFailure { Log.w(TAG, "screenshot $name: ${it.message}") }
    }

    private fun log(step: String, detail: String) = Log.i(TAG, "$step: $detail")

    private fun finishLaunchedActivities() {
        val activities = launchedEditActivities.toList()
        if (activities.isNotEmpty()) {
            runOnMainSync {
                activities.forEach { launched ->
                    launched.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    if (!launched.isDestroyed) launched.finishAndRemoveTask()
                }
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(ACTIVITY_FINISH_DELAY_MS)
        }
        launchedEditActivities.clear()
    }

    /** Waits for [condition]; a timeout fails with [message] and, when given, what [explain] says (the panel's message). */
    private fun waitUntil(
        message: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        explain: (() -> String)? = null,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val result = try {
                condition()
            } catch (throwable: Throwable) {
                lastError = throwable
                false
            }
            if (result) return
            instrumentation.waitForIdleSync()
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        val detail = explain?.let { runCatching(it).getOrNull() }?.takeIf { it.isNotEmpty() }?.let { " (panel: $it)" }.orEmpty()
        screenshot("timeout")
        lastError?.let { throw AssertionError("$message$detail: ${it.message}", it) }
        fail(message + detail)
    }

    private fun <T> runOnMainSync(block: () -> T): T {
        val value = AtomicReference<T>()
        val error = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            try {
                value.set(block())
            } catch (throwable: Throwable) {
                error.set(throwable)
            }
        }
        error.get()?.let { throw it }
        return value.get()
    }

    private fun captureStringPreference(key: String): StoredStringPreference {
        val preferences = Pref.get()
        return StoredStringPreference(preferences.contains(key), preferences.getString(key, null))
    }

    private data class StoredStringPreference(val present: Boolean, val value: String?) {
        fun restore(editor: SharedPreferences.Editor, key: String) {
            if (present) editor.putString(key, value) else editor.remove(key)
        }
    }

    companion object {
        private const val TAG = "EditorUiPreviewTest"
        private const val SCREENSHOT_DIR = "editor-ui-preview-test"

        private val INITIAL_TEXT = """
            "ui";
            ui.layout(
                <vertical>
                    <text id="alpha" text="Alpha" textSize="18sp" padding="12"/>
                    <text id="beta" text="Beta" textSize="18sp" padding="12"/>
                </vertical>
            );
        """.trimIndent() + "\n"

        /** The 0-based line the Gamma element is typed at, before `</vertical>`. */
        private const val GAMMA_LINE = 5
        private const val GAMMA_SOURCE_LINE = "        <text id=\"gamma\" text=\"Gamma\" textSize=\"18sp\" padding=\"12\"/>\n"
        private val EDITED_TEXT = INITIAL_TEXT.lines().toMutableList()
            .apply { add(GAMMA_LINE, GAMMA_SOURCE_LINE.removeSuffix("\n")) }
            .joinToString("\n")
        private val BROKEN_TEXT = EDITED_TEXT.replace("</vertical>", "</verticalx>")

        /** The 1-based line of the broken end tag. */
        private const val BROKEN_LINE = GAMMA_LINE + 2

        private const val DEFAULT_TIMEOUT_MS = 10_000L
        private const val LONG_TIMEOUT_MS = 30_000L
        private const val ACTIVITY_LAUNCH_TIMEOUT_MS = 45_000L
        private const val ACTIVITY_FINISH_DELAY_MS = 500L
        private const val SETTLE_DELAY_MS = 500L
        private const val POLL_INTERVAL_MS = 50L
    }
}
