package org.autojs.autojs.ui.floating.codegen

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.UiAutomation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.NodeInfoSnapshot
import org.autojs.autojs.core.accessibility.codegen.CodeGenEngine
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenResult
import org.autojs.autojs.core.accessibility.inspector.InspectorNode
import org.autojs.autojs.core.automator.filter.JsLiteral
import org.autojs.autojs.util.DialogUtils.showAdaptive
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Real overlay dialogs on API 24 and later. UiAutomation never suppresses the host's accessibility
 * services; callers must grant overlay permission and configure an enabled software keyboard.
 */
class CodeGenUiDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val cleanup = ArrayList<() -> Unit>()
    private var activity: Activity? = null
    private lateinit var automation: UiAutomation
    private var originalAutomationFlags = 0
    private var clipboardTouched = false
    private var previousClip: ClipData? = null

    @Before
    fun startBackdropWithoutSuppressingAccessibility() {
        assumeTrue("Grant SYSTEM_ALERT_WINDOW before running the overlay smoke tests", Settings.canDrawOverlays(context))
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val info = automation.serviceInfo
        originalAutomationFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        activity = instrumentation.startActivitySync(Intent().apply {
            setClassName(context.packageName, "org.autojs.autojs.ui.floating.codegen.CodeGenSmokeActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        instrumentation.waitForIdleSync()
    }

    @After
    fun releaseOverlaysAndRestoreClipboard() {
        main {
            cleanup.asReversed().forEach { it() }
            cleanup.clear()
            if (clipboardTouched) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                previousClip?.let { clipboard.setPrimaryClip(it) } ?: run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
                    else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
            activity?.finish()
            activity = null
        }
        if (::automation.isInitialized) {
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalAutomationFlags }
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun optionsKeepDefaultsAndApplyExclusiveActionScopeAndStyleChoices() {
        val root = fixture()
        val generated = AtomicReference<CodeGenResult<InspectorNode>>()
        val builder = main {
            CodeGenerateDialog(context, root, root.children[0]) { generated.set(it) }
                .also { cleanup += { it.dispose() } }
        }
        val dialog = main { builder.build().showAdaptive() }
        waitOnMain("Options did not show") { visible(dialog) }
        main { assertOverlayWindow(dialog) }
        for (tag in listOf(
            "text_codegen_scope_single", "text_codegen_query_wait", "text_codegen_action_none",
            "text_codegen_receiver_auto", "text_code_style_classic",
        )) {
            val view = findOption(dialog, tag)
            assertTrue("Default is not selected: $tag", main { (view as CompoundButton).isChecked })
        }

        clickOption(dialog, "codegen_group_text_options")
        for (tag in listOf("codegen_attr_id", "codegen_attr_text", "codegen_attr_desc")) {
            val view = findOption(dialog, tag)
            assertTrue("Default attribute is not enabled: $tag", main { (view as CompoundButton).isChecked })
        }

        findOption(dialog, "text_codegen_scope_collection")
        waitOnMain("Target collection was not prepared") {
            dialog.view.findViewWithTag<View>("text_codegen_scope_collection")?.isEnabled == true
        }
        clickOption(dialog, "text_codegen_scope_collection")
        val selectedCollection = findOption(dialog, "codegen_collection_0")
        assertTrue(main { (selectedCollection as CompoundButton).isChecked })
        clickOption(dialog, "text_codegen_query_check")
        clickOption(dialog, "text_act_by_click")
        clickOption(dialog, "text_act_by_set_text")
        val click = findOption(dialog, "text_act_by_click")
        assertFalse("Selecting setText must replace click", main { (click as CompoundButton).isChecked })
        val input = findOption(dialog, "codegen_text_input") as EditText
        val value = "quoted \"value\"\nsecond line"
        main { input.setText(value) }
        clickOption(dialog, "text_codegen_receiver_self")
        clickOption(dialog, "text_code_style_flow")
        main { dialog.getActionButton(DialogAction.POSITIVE).performClick() }
        waitOnMain("The chosen options did not produce code") { generated.get() != null }

        val result = generated.get()
        assertEquals(2, result.preferred.matchedCount)
        assertTrue(result.candidates.all { it.code.contains("flow.whenPresent(") && it.code.contains("timeout: 0") })
        assertTrue(result.candidates.all { it.code.contains("resultType: 'widgets'") })
        assertTrue(result.candidates.all { it.code.contains("setTextEach(${JsLiteral.quote(value)})") })
        assertTrue(result.candidates.none { it.code.contains(".click") })
        assertFalse(main { visible(dialog) })
    }

    @Test
    fun invalidTimeoutStaysOpenAndOverlayInputCanShowHideAndRestoreIme() {
        val root = fixture()
        val builder = main {
            CodeGenerateDialog(context, root, root.children[0]).also { cleanup += { it.dispose() } }
        }
        val dialog = main { builder.build().showAdaptive() }
        waitOnMain("Options did not show") { visible(dialog) }
        clickOption(dialog, "text_codegen_query_timeout")
        val input = findOption(dialog, "codegen_timeout_input") as EditText
        main {
            assertOverlayWindow(dialog)
            assertEquals(0, dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            assertEquals("10000", input.text.toString())
            input.setText("0")
            dialog.getActionButton(DialogAction.POSITIVE).performClick()
            assertTrue("Invalid timeout must not dismiss options", visible(dialog))
            val error = dialog.view.findViewWithTag<TextView>("codegen_error")
            assertEquals(View.VISIBLE, error.visibility)
            assertEquals(dialog.context.getString(R.string.text_codegen_timeout_error), error.text.toString())
            input.setText("12500")
            assertEquals(View.GONE, error.visibility)
        }
        showIme(dialog, input)
        main {
            val ime = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            ime.hideSoftInputFromWindow(input.windowToken, 0)
        }
        waitForIme("Input method did not hide", expected = false)
        showIme(dialog, input)
        main {
            assertTrue(input.hasFocus())
            assertEquals("12500", input.text.toString())
            val ime = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            ime.hideSoftInputFromWindow(input.windowToken, 0)
        }
    }

    @Test
    fun resultSwitchesCandidatesCopiesCurrentCodeAndReturnsFromPreview() {
        val result = generatedResult()
        assertTrue("The fixture must produce meaningful alternative plans", result.candidates.size > 1)
        val chosenIndex = result.candidates.indexOfFirst { it.code != result.preferred.code }
        assertTrue("An alternative must generate different code", chosenIndex > 0)
        val previewed = AtomicReference<List<NodeInfo>>()
        val returnFromPreview = AtomicReference<() -> Unit>()
        val builder = main {
            CodeGenResultDialog(context, result) { nodes, restore ->
                previewed.set(nodes)
                returnFromPreview.set(restore)
            }.also { cleanup += { it.dispose() } }
        }
        val dialog = main { builder.build().showAdaptive() }
        waitOnMain("Result dialog did not show") { visible(dialog) && descendants(dialog.view).any { it is RecyclerView && it.childCount > 0 } }

        main {
            assertOverlayWindow(dialog)
            assertEquals(result.preferred.code, codeView(dialog).text.toString())
            assertTrue(codeView(dialog).isTextSelectable)
            val list = descendants(dialog.view).filterIsInstance<RecyclerView>().single()
            assertEquals(result.alternatives.size.coerceAtMost(9), list.adapter!!.itemCount)
            val choice = requireNotNull(list.findViewHolderForAdapterPosition(chosenIndex - 1)).itemView as RadioButton
            choice.performClick()
            assertTrue(choice.isChecked)
            assertEquals(result.candidates[chosenIndex].code, codeView(dialog).text.toString())
            assertTrue(texts(dialog).contains(CodeGenText.explanation(dialog.context, result.candidates[chosenIndex].explanation)))
            assertTrue(texts(dialog).contains(CodeGenText.matchCount(dialog.context, result.candidates[chosenIndex].matchedCount, false)))

            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            previousClip = clipboard.primaryClip
            clipboardTouched = true
            dialog.getActionButton(DialogAction.POSITIVE).performClick()
            assertEquals(result.candidates[chosenIndex].code, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            dialog.getActionButton(DialogAction.NEUTRAL).performClick()
            assertFalse("Preview must hide the dialog", visible(dialog))
            assertFalse("Preview decor must be hidden", dialog.window!!.decorView.visibility == View.VISIBLE)
            assertSame("Preview must retain the existing dialog", dialog, builder.build())
            assertEquals(result.candidates[chosenIndex].resolved.map { it.info }, previewed.get())
        }

        main { returnFromPreview.get().invoke() }
        waitOnMain("Preview return did not restore the existing dialog") { visible(dialog) }
        main {
            assertSame(dialog, builder.build())
            assertEquals(result.candidates[chosenIndex].code, codeView(dialog).text.toString())
            val preferred = descendants(dialog.view).filterIsInstance<RadioButton>()
                .single { it.text.toString() == dialog.context.getString(R.string.text_codegen_preferred) }
            preferred.performClick()
            assertTrue(preferred.isChecked)
            assertEquals(result.preferred.code, codeView(dialog).text.toString())
        }
    }

    @Test
    fun disposingHiddenResultPreventsAnyLatePreviewReturn() {
        val restore = AtomicReference<() -> Unit>()
        val result = generatedResult()
        val builder = main {
            CodeGenResultDialog(context, result) { _, callback -> restore.set(callback) }
                .also { cleanup += { it.dispose() } }
        }
        val dialog = main { builder.build().showAdaptive() }
        waitOnMain("Result dialog did not show") { visible(dialog) }
        main {
            dialog.getActionButton(DialogAction.NEUTRAL).performClick()
            assertFalse(visible(dialog))
            assertNotNull(restore.get())
            builder.dispose()
            restore.get().invoke()
            restore.get().invoke()
        }
        instrumentation.waitForIdleSync()
        main {
            assertFalse("A disposed preview must not reopen", dialog.isShowing)
            assertFalse(visible(dialog))
        }
    }

    @Test
    fun resultAtNarrowWidthKeepsCodeAndActionsInsideTheDialog() {
        val result = generatedResult()
        val builder = main { CodeGenResultDialog(context, result) { _, restore -> restore() }.also { cleanup += { it.dispose() } } }
        val dialog = main { builder.build().showAdaptive() }
        val requestedWidth = main {
            val pixels = (320 * dialog.context.resources.displayMetrics.density).roundToInt()
            pixels.coerceAtMost(dialog.context.resources.displayMetrics.widthPixels).also {
                dialog.window!!.setLayout(it, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        waitOnMain("Narrow result dialog did not lay out") {
            val decor = dialog.window!!.decorView
            visible(dialog) && codeView(dialog).width > 0 && decor.width <= requestedWidth && !decor.isLayoutRequested
        }
        main {
            val decor = dialog.window!!.decorView
            assertTrue(decor.width <= requestedWidth)
            val code = codeView(dialog)
            assertTrue(code.layout.width <= decor.width)
            assertTrue(code.layout.lineCount > 0)
            for (action in DialogAction.values()) {
                val button = dialog.getActionButton(action)
                assertEquals(View.VISIBLE, button.visibility)
                assertTrue("$action is not laid out", button.width > 0 && button.height > 0)
                val buttonLocation = IntArray(2)
                val dialogLocation = IntArray(2)
                button.getLocationOnScreen(buttonLocation)
                decor.getLocationOnScreen(dialogLocation)
                assertTrue("$action crosses the left edge", buttonLocation[0] >= dialogLocation[0])
                assertTrue("$action crosses the right edge", buttonLocation[0] + button.width <= dialogLocation[0] + decor.width)
            }
        }
    }

    private fun generatedResult(): CodeGenResult<InspectorNode> {
        val root = fixture()
        val outcome = CodeGenEngine().generate(
            InspectorNode(root), CodeGenRequest(listOf(InspectorNode(root.children[0])), mode = CodeGenRequest.Mode.CHECK),
        )
        assertTrue("Fixture generation failed: $outcome", outcome is CodeGenResult<*>)
        @Suppress("UNCHECKED_CAST")
        return outcome as CodeGenResult<InspectorNode>
    }

    private fun fixture(): NodeInfo = NodeInfoSnapshot(
        packageName = context.packageName,
        className = "android.widget.LinearLayout",
        bounds = intArrayOf(0, 0, 1080, 1200),
        children = listOf(
            NodeInfoSnapshot(
                packageName = context.packageName, fullId = "${context.packageName}:id/codegen_field",
                id = "codegen_field", className = "android.widget.EditText", text = "First field",
                desc = "Editable target", editable = true, clickable = true, enabled = true,
                depth = 1, indexInParent = 0, bounds = intArrayOf(40, 80, 900, 180),
            ),
            NodeInfoSnapshot(
                packageName = context.packageName, fullId = "${context.packageName}:id/codegen_field",
                id = "codegen_field", className = "android.widget.EditText", text = "Second field",
                editable = true, clickable = true, enabled = true,
                depth = 1, indexInParent = 1, bounds = intArrayOf(40, 200, 900, 300),
            ),
        ),
    ).toNodeInfo()

    private fun assertOverlayWindow(dialog: MaterialDialog) {
        val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        assertEquals(expected, dialog.window!!.attributes.type)
        assertEquals(0, dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    }

    private fun showIme(dialog: MaterialDialog, input: EditText) {
        main {
            input.requestRectangleOnScreen(Rect(0, 0, input.width, input.height), true)
            assertTrue("The overlay input cannot obtain focus", input.requestFocus())
        }
        waitOnMain("The overlay input did not receive window focus") { input.hasWindowFocus() && input.hasFocus() }
        main {
            val ime = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            ime.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            assertTrue(visible(dialog))
        }
        waitForIme("No input-method window appeared for the overlay EditText", expected = true)
    }

    private fun waitForIme(message: String, expected: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 6_000
        fun visible(): Boolean {
            val windows = automation.windows
            return try {
                windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            } finally {
                @Suppress("DEPRECATION")
                windows.forEach { it.recycle() }
            }
        }
        while (SystemClock.uptimeMillis() < deadline) {
            if (visible() == expected) return
            SystemClock.sleep(50)
        }
        assertEquals(message, expected, visible())
    }

    private fun clickOption(dialog: MaterialDialog, tag: String) {
        val view = findOption(dialog, tag)
        main { assertTrue("Option is disabled: $tag", view.isEnabled); view.performClick() }
        val list = main { dialog.view.findViewById<RecyclerView>(R.id.list) }
        waitOnMain("Options did not refresh after selecting $tag") { !list.isLayoutRequested && !list.isComputingLayout }
    }

    /** Options use a virtualized list, so find each tag by scrolling through the actual adapter. */
    private fun findOption(dialog: MaterialDialog, tag: String): View {
        main { dialog.view.findViewWithTag<View>(tag) }?.let { return it }
        val list = main { dialog.view.findViewById<RecyclerView>(R.id.list) }
        val count = main { list.adapter!!.itemCount }
        for (position in 0 until count) {
            main { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0) }
            waitOnMain("Options did not scroll to item $position") { !list.isLayoutRequested && !list.isComputingLayout }
            main { dialog.view.findViewWithTag<View>(tag) }?.let { return it }
        }
        error("No option view with tag $tag")
    }

    private fun visible(dialog: MaterialDialog): Boolean = dialog.isShowing && dialog.window?.decorView?.visibility == View.VISIBLE

    private fun codeView(dialog: MaterialDialog): TextView = descendants(dialog.view).filterIsInstance<TextView>().single { it.isTextSelectable }

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
        // runCatching catches Throwable, including AssertionError. Rethrow on the test thread so
        // a failed UI assertion never terminates the host's main thread or accessibility service.
        instrumentation.runOnMainSync { result.set(runCatching(block)) }
        return result.get().getOrThrow()
    }
}
