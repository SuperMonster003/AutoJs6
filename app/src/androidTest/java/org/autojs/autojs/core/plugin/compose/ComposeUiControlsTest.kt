package org.autojs.autojs.core.plugin.compose

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.ui.settings.AboutActivity
import org.autojs.plugin.compose.api.ComposeUiComponents as C
import org.autojs.plugin.compose.api.ComposeUiEventFields as F
import org.autojs.plugin.compose.api.ComposeUiEvents as E
import org.autojs.plugin.compose.api.ComposeUiModifiers as M
import org.autojs.plugin.compose.api.ComposeUiProps as P
import org.autojs.plugin.compose.api.ComposeUiIds
import org.autojs.plugin.compose.api.loading.ComposeUiEventSink
import org.autojs.plugin.compose.api.loading.ComposeUiHostEnvironment
import org.autojs.plugin.compose.api.loading.ComposeUiRenderer
import org.autojs.plugin.compose.api.loading.ComposeUiRendererFactory
import org.autojs.plugin.compose.api.model.ModifierOp
import org.autojs.plugin.compose.api.model.UiEvent
import org.autojs.plugin.compose.api.model.UiNode
import org.autojs.plugin.compose.api.model.UiTree
import org.autojs.plugin.compose.api.model.UiValue
import org.autojs.plugin.compose.api.model.UiCommand
import org.autojs.plugin.compose.api.model.SnackbarDuration
import org.autojs.plugin.compose.api.ComposeUiSlots as S
import org.autojs.plugin.compose.api.model.ScopeKind
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Drives the installed renderer through V1 and Android accessibility, without host Compose code. */
@RunWith(AndroidJUnit4::class)
class ComposeUiControlsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var host: ComposeUiPluginHost
    private var previousEnabled = true
    private var previousAccessibilityFlags = 0
    private var prepared = false

    @Before fun prepare() {
        assumeTrue("Use the isolated -PcomposeUiSpike=true host", context.packageName.endsWith(".compose.spike"))
        previousEnabled = PluginEnableStore.isEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME)
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
        host = ComposeUiPluginHost(context)
        instrumentation.uiAutomation.serviceInfo.also {
            previousAccessibilityFlags = it.flags
            it.flags = it.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            instrumentation.uiAutomation.serviceInfo = it
        }
        prepared = true
    }

    @After fun finish() {
        if (!prepared) return
        try { host.close() } finally {
            PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, previousEnabled)
            instrumentation.uiAutomation.serviceInfo.also {
                it.flags = previousAccessibilityFlags
                instrumentation.uiAutomation.serviceInfo = it
            }
        }
    }

    @Test fun checkedControlsWaitForHostPatchesAndCallbacksFollowCommittedGenerations() {
        Fixture(41).use { fixture ->
            val types = linkedMapOf("switch" to C.SWITCH, "checkbox" to C.CHECKBOX, "radio" to C.RADIO_BUTTON)
            val checked = types.keys.associateWith { false }.toMutableMap()
            val callbacks = LinkedHashMap<String, Int>()
            var acceptChanges = false
            fun tree() = UiTree(1, listOf(
                UiNode(1, C.COLUMN, children = listOf(2, 3, 4)),
            ) + types.entries.mapIndexed { index, (key, type) ->
                UiNode(index + 2, type, key, props = mapOf(P.CHECKED to UiValue.Bool(checked.getValue(key))),
                    modifier = tagged(key), callbacks = mapOf(E.CHECKED_CHANGE to callbacks.getValue(key)))
            })
            main {
                types.keys.forEach { key ->
                    callbacks[key] = fixture.callback { event ->
                        if (acceptChanges) {
                            checked[key] = event.payload.getBoolean(F.CHECKED)
                            fixture.session.render(tree())
                        }
                    }
                }
                fixture.session.render(tree())
                fixture.session.attach()
            }
            fixture.await("Three initially unchecked controls") {
                types.keys.all { key -> node(key)?.let { !isChecked(it) && it.isClickable } == true }
            }
            main { assertEquals(1L, fixture.session.generation) }

            // Observe proposals without accepting them: the renderer must retain the host values.
            types.keys.forEachIndexed { index, key ->
                click(key)
                fixture.await("$key proposal reaches the script queue") { fixture.delivered.size == index + 1 }
            }
            instrumentation.waitForIdleSync()
            types.keys.forEach { key -> assertFalse("$key changed without a host patch", isChecked(requireNotNull(node(key)))) }
            main { assertEquals(1L, fixture.session.generation) }
            fixture.delivered.forEach { event ->
                assertEquals(E.CHECKED_CHANGE, event.type)
                assertEquals(setOf(F.CHECKED), event.payload.keySet())
                assertTrue(event.payload.getBoolean(F.CHECKED))
                assertEquals(1L, event.generation)
            }

            // Each accepted script proposal creates a new transaction; subsequent events use it.
            main { acceptChanges = true }
            types.keys.forEachIndexed { index, key ->
                click(key)
                fixture.await("$key is checked after the host commits") {
                    node(key)?.let(::isChecked) == true && fixture.generation() == index + 2L
                }
            }
            assertEquals(listOf(1L, 1L, 1L, 1L, 2L, 3L), fixture.delivered.map { it.generation })
            main {
                assertEquals(4L, fixture.session.generation)
                val nodes = fixture.session.tree!!.nodes.associateBy { it.key }
                types.keys.forEach { key ->
                    assertEquals(UiValue.Bool(true), nodes.getValue(key).props[P.CHECKED])
                    fixture.delivered.filter { it.callbackId == callbacks.getValue(key) }.forEach {
                        assertEquals(nodes.getValue(key).nodeId, it.nodeId)
                    }
                }
            }
            fixture.assertQueuedDelivery()
        }
    }

    @Test fun sliderPublishesNumericProposalsAndFinishedEventsWithoutOwningTheValue() {
        Fixture(42).use { fixture ->
            var value = 0.25
            var acceptChanges = false
            var changed = 0
            var finished = 0
            fun tree() = UiTree(1, listOf(
                UiNode(1, C.COLUMN, children = listOf(2)),
                UiNode(2, C.SLIDER, "slider", props = mapOf(
                    P.VALUE to UiValue.Num(value),
                    P.RANGE to UiValue.ListOf(listOf(UiValue.Num(0.0), UiValue.Num(1.0))),
                ), modifier = tagged("slider") + ModifierOp(M.FILL_MAX_WIDTH),
                    callbacks = mapOf(E.VALUE_CHANGE to changed, E.VALUE_CHANGE_FINISHED to finished)),
            ))
            main {
                changed = fixture.callback { event ->
                    if (acceptChanges) {
                        value = event.payload.getDouble(F.VALUE)
                        fixture.session.render(tree())
                    }
                }
                finished = fixture.callback()
                fixture.session.render(tree())
                fixture.session.attach()
            }
            fixture.await("Slider exposes its controlled initial range") {
                node("slider")?.rangeInfo?.let { it.current == 0.25f && it.min == 0f && it.max == 1f } == true
            }
            setProgress(0.75f)
            fixture.await("Accessible progress action emits valueChange and valueChangeFinished") { fixture.delivered.size == 2 }
            instrumentation.waitForIdleSync()
            assertEquals("Slider changed without a host patch", 0.25f, requireNotNull(node("slider")?.rangeInfo).current, 0.001f)
            main {
                assertEquals(1L, fixture.session.generation)
                assertEquals(UiValue.Num(0.25), fixture.session.tree!!.nodes.single { it.type == C.SLIDER }.props[P.VALUE])
            }
            val proposal = fixture.delivered.first()
            assertEquals(E.VALUE_CHANGE, proposal.type)
            assertEquals(setOf(F.VALUE), proposal.payload.keySet())
            @Suppress("DEPRECATION")
            assertTrue("NUMBER payload is normalized to Double", proposal.payload.get(F.VALUE) is Double)
            assertEquals(0.75, proposal.payload.getDouble(F.VALUE), 0.001)
            assertEquals(E.VALUE_CHANGE_FINISHED, fixture.delivered.last().type)
            assertTrue(fixture.delivered.last().payload.isEmpty)

            main { acceptChanges = true }
            listOf(0.5f, 0.25f).forEachIndexed { index, target ->
                setProgress(target)
                fixture.await("Script accepted slider value $target") {
                    node("slider")?.rangeInfo?.current == target && fixture.generation() == index + 2L
                }
                fixture.await("Finished event follows accepted slider proposal") { fixture.delivered.size == (index + 2) * 2 }
            }
            assertEquals(listOf(1L, 1L, 1L, 1L, 2L, 2L), fixture.delivered.map { it.generation })
            assertEquals(listOf(E.VALUE_CHANGE, E.VALUE_CHANGE_FINISHED, E.VALUE_CHANGE, E.VALUE_CHANGE_FINISHED,
                E.VALUE_CHANGE, E.VALUE_CHANGE_FINISHED), fixture.delivered.map { it.type })
            main { assertEquals(3L, fixture.session.generation) }
            fixture.assertQueuedDelivery()
        }
    }

    @Test fun xiaomiChineseImeCommitsCandidateThroughInstalledKeyboard() {
        val ime = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)
        assumeTrue("Chinese IME acceptance device: API33 with its existing Xiaomi Sogou IME", android.os.Build.VERSION.SDK_INT == 33 && ime?.contains("com.sohu.inputmethod.sogou.xiaomi") == true)
        // Device acceptance opts in after confirming the installed IME's English QWERTY layout.
        // Only its language key is toggled, and it is toggled back before the fixture closes.
        assumeTrue(InstrumentationRegistry.getArguments().getString("composeSogouEnglishQwerty") == "true")
        Fixture(45, insetProbe = true).use { fixture ->
            main { fixture.session.render(inputTree(fixture.callback())); fixture.session.attach() }
            fixture.await("Chinese IME fixture is visible") { node("input") != null }
            click("input")
            fixture.await("Existing Chinese IME is shown") { fixture.imeBottom() > 0 }
            fixture.await("Chinese IME window and its animation have settled") {
                fixture.imeBottom() > 150 * context.resources.displayMetrics.density &&
                    kotlin.math.abs(fixture.keyboardTop().toLong() - fieldBounds().bottom) <= 3
            }
            val keyboard = requireNotNull(instrumentation.uiAutomation.windows.firstOrNull {
                it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD
            }).let { Rect().also(it::getBoundsInScreen) }
            fun tap(x: Float, y: Float) {
                val now = SystemClock.uptimeMillis()
                val down = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, x, y, 0)
                val up = android.view.MotionEvent.obtain(now, now + 80, android.view.MotionEvent.ACTION_UP, x, y, 0)
                down.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                up.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try {
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(down, true))
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(up, true))
                } finally { down.recycle(); up.recycle() }
            }
            fun languageKey() = tap(keyboard.left + keyboard.width() * .7875f, keyboard.top + keyboard.height() * .76f)
            fun snapshot(name: String) {
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    try { java.io.File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                    finally { bitmap.recycle() }
                }
            }
            snapshot("compose-ime-before.png")
            languageKey()
            try {
                SystemClock.sleep(300)
                // Physical QWERTY touches exercise the installed IME's composing/candidate path.
                for ((x, y) in listOf(.70f to .60f, .75f to .275f, .60f to .439f, .11f to .439f, .85f to .275f)) {
                    tap(keyboard.left + keyboard.width() * x, keyboard.top + keyboard.height() * y)
                }
                // Sogou may keep pinyin entirely in its own candidate buffer until selection.
                SystemClock.sleep(500)
                val candidate = imeCandidate("你好")
                if (candidate != null) Rect().also(candidate::getBoundsInScreen).let { tap(it.exactCenterX(), it.exactCenterY()) }
                else tap(keyboard.left + keyboard.width() * .11f, keyboard.top + keyboard.height() * .145f)
                fixture.await("Chinese candidate reaches native state and queued callback") {
                    fixture.delivered.lastOrNull()?.payload?.getString(F.TEXT) == "你好"
                }
                assertTrue(fixture.delivered.last().payload.getLong(F.EDIT_SEQ) > 0)
                fixture.assertQueuedDelivery()
            } finally {
                languageKey()
                SystemClock.sleep(300)
                snapshot("compose-ime-after.png")
            }
        }
    }

    private fun imeCandidate(text: String): AccessibilityNodeInfo? {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        return instrumentation.uiAutomation.windows.asSequence()
            .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            .mapNotNull { it.root }.mapNotNull(::find).firstOrNull()
    }

    @Test fun nativeInputConnectionAndScaffoldShareOneHostKeyboardInset() {
        Fixture(43, insetProbe = true).use { fixture ->
            var changed = 0
            main {
                changed = fixture.callback()
                fixture.session.render(inputTree(changed))
                fixture.session.attach()
            }
            fixture.await("Native field is exposed") { node("input") != null }
            fixture.await("Initial system-bar insets settle") { kotlin.math.abs(fieldBounds().bottom - fixture.contentBottom()) <= 3 }
            val original = fieldBounds()
            android.util.Log.i("ComposeInsetProbe", "Before keyboard: ${fixture.insetSnapshot()}")
            val softMode = fixture.originalSoftInputMode
            click("input")
            fixture.await("Keyboard insets reach the host") { fixture.imeBottom() > 0 }
            fixture.await("Field moves exactly to the keyboard edge") {
                val field = fieldBounds()
                val keyboard = fixture.keyboardTop()
                field.bottom <= keyboard + 3 && keyboard - field.bottom < 12 * context.resources.displayMetrics.density
            }
            assertTrue("Visible viewport shrinks when IME opens", fieldBounds().bottom < original.bottom)
            android.util.Log.i("ComposeInsetProbe", "Keyboard shown: ${fixture.insetSnapshot()}")
            lateinit var connection: InputConnection
            main {
                connection = requireNotNull(fixture.editor().onCreateInputConnection(EditorInfo()))
                assertTrue(connection.setComposingText("ni", 1))
                assertTrue(connection.setComposingText("nihao", 1))
                assertTrue(connection.commitText("你好", 1))
                assertTrue(connection.finishComposingText())
            }
            fixture.await("Native composing commit is delivered without script echo") { fixture.delivered.lastOrNull()?.payload?.getString(F.TEXT) == "你好" }
            main {
                assertTrue(connection.commitText("粘贴🙂", 1))
                assertTrue(connection.deleteSurroundingText(2, 0))
                assertTrue(connection.deleteSurroundingText(1, 0))
                assertTrue(connection.setSelection(1, 0))
            }
            fixture.await("Delete and reversed UTF-16 selection are delivered") {
                fixture.delivered.lastOrNull()?.payload?.let { it.getString(F.TEXT) == "你好粘" && it.getInt(F.SELECTION_START) == 1 && it.getInt(F.SELECTION_END) == 0 } == true
            }
            main {
                val fieldId = fixture.session.tree!!.nodes.single { it.type == C.OUTLINED_TEXT_FIELD }.nodeId
                fixture.session.execute(UiCommand.Edit(fieldId, text = "脚本", editSeq = fixture.delivered.last().payload.getLong(F.EDIT_SEQ)))
            }
            fixture.await("Current edit sequence permits script replacement") { fixture.delivered.lastOrNull()?.payload?.getString(F.TEXT) == "脚本" }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            fixture.await("Keyboard hiding restores the original content height") { fixture.imeBottom() == 0 && kotlin.math.abs(fieldBounds().bottom - original.bottom) <= 3 }
            android.util.Log.i("ComposeInsetProbe", "Keyboard hidden: ${fixture.insetSnapshot()}")
            main { fixture.session.detach() }
            instrumentation.waitForIdleSync()
            main { assertEquals("Detach restores the Activity's soft input policy", softMode, fixture.activity.window.attributes.softInputMode) }
            fixture.assertQueuedDelivery()
        }
    }

    @Test fun snackbarCommandCallbackSurvivesNewTreeAndTerminatesOnHostRemoval() {
        Fixture(44, insetProbe = true).use { fixture ->
            fun tree(text: String) = UiTree(1, listOf(UiNode(1, C.SCAFFOLD, slots = mapOf(S.CONTENT to 2)),
                UiNode(2, C.TEXT, props = mapOf(P.TEXT to UiValue.Str(text)))))
            main { fixture.session.render(tree("Before")); fixture.session.attach() }
            fixture.await("Initial tree commits") { fixture.generation() == 1L }
            main {
                fixture.session.execute(UiCommand.ShowSnackbar("Command result", "Accept", SnackbarDuration.INDEFINITE, fixture.callback()))
            }
            fixture.await("Action is visible") { findText("Accept") != null }
            main { fixture.session.render(tree("After")) }
            fixture.await("Unrelated tree commits while command is active") { fixture.generation() == 2L }
            assertTrue(requireNotNull(findText("Accept")).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            fixture.await("Command action reaches its original callback") { fixture.delivered.any { it.type == E.ACTION } }
            assertEquals(1L, fixture.delivered.single().generation)
            main { assertEquals(0, fixture.session.callbackCount) }
            main {
                fixture.session.execute(UiCommand.ShowSnackbar("Removed host", duration = SnackbarDuration.INDEFINITE, callbackId = fixture.callback()))
                fixture.session.render(UiTree(3, listOf(UiNode(3, C.TEXT, props = mapOf(P.TEXT to UiValue.Str("Closed host"))))))
            }
            fixture.await("Removing Scaffold delivers one dismissal") { fixture.delivered.size == 2 }
            assertEquals(E.DISMISS, fixture.delivered.last().type)
            assertEquals(2L, fixture.delivered.last().generation)
            main { assertEquals(0, fixture.session.callbackCount) }
            fixture.assertQueuedDelivery()
        }
    }

    private fun inputTree(callback: Int) = UiTree(1, listOf(
        UiNode(1, C.SCAFFOLD, modifier = listOf(ModifierOp(M.FILL_MAX_SIZE)), slots = mapOf(S.CONTENT to 2)),
        UiNode(2, C.BOX, modifier = listOf(ModifierOp(M.FILL_MAX_SIZE)), children = listOf(3)),
        UiNode(3, C.OUTLINED_TEXT_FIELD, "input", props = mapOf(P.SINGLE_LINE to UiValue.Bool(true)),
            modifier = tagged("input") + listOf(ModifierOp(M.FILL_MAX_WIDTH),
                ModifierOp(M.ALIGN, listOf(UiValue.Enum("bottomCenter")), ScopeKind.BOX)),
            callbacks = mapOf(E.VALUE_CHANGE to callback)),
    ))
    private fun fieldBounds() = Rect().also { requireNotNull(node("input")).getBoundsInScreen(it) }

    private inner class Fixture(private val id: Int, insetProbe: Boolean = false) : Closeable {
        val activity = instrumentation.startActivitySync(
            Intent(context, if (insetProbe) ComposeInsetsProbeActivity::class.java else AboutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as ComponentActivity
        init { if (insetProbe) main { activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN) } }
        val originalSoftInputMode = activity.window.attributes.softInputMode
        lateinit var session: ComposeSession
            private set
        val delivered = ConcurrentLinkedQueue<UiEvent>()
        private val emitted = ConcurrentLinkedQueue<UiEvent>()
        private val errors = ConcurrentLinkedQueue<ComposeUiError>()
        private val inEnqueue = AtomicBoolean()
        private val synchronousCallback = AtomicBoolean()
        private val wrongCallbackThread = AtomicBoolean()

        init {
            try {
                val loaded = host.load()
                // Keep the V1 event/IME guard on its recorded base factory; F2 tests cover interop.
                val recording = loaded.copy(androidViewFactory = null, factory = object : ComposeUiRendererFactory by loaded.factory {
                    override fun create(environment: ComposeUiHostEnvironment): ComposeUiRenderer {
                        val observed = object : ComposeUiHostEnvironment by environment {
                            override val eventSink = ComposeUiEventSink { event ->
                                emitted += event
                                inEnqueue.set(true)
                                try { environment.eventSink.enqueue(event) } finally { inEnqueue.set(false) }
                            }
                        }
                        return loaded.factory.create(observed)
                    }
                })
                main {
                    val renderer = ComposeAndroidRenderer(activity, recording, id, activity::setContentView, activity,
                        onDestroyed = { if (::session.isInitialized) session.close() })
                    session = ComposeSession(id, ScriptUiDispatcher.main(), ScriptUiDispatcher.mainExecutor(), renderer,
                        loaded.components, errors::add)
                    session.open()
                }
            } catch (failure: Throwable) { close(); throw failure }
        }

        fun callback(action: (UiEvent) -> Unit = {}): Int = session.registerCallback { event ->
            if (inEnqueue.get()) synchronousCallback.set(true)
            if (Looper.myLooper() != Looper.getMainLooper()) wrongCallbackThread.set(true)
            delivered += event
            action(event)
        }

        fun generation(): Long { var result = 0L; main { result = session.generation }; return result }
        fun imeBottom(): Int { var value = 0; main { value = ViewCompat.getRootWindowInsets(activity.window.decorView)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0 }; return value }
        fun contentBottom(): Int {
            var value = 0
            main {
                val decor = activity.window.decorView
                val location = IntArray(2).also(decor::getLocationOnScreen)
                value = location[1] + decor.height - (ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0)
            }
            return value
        }
        fun keyboardTop(): Int {
            val keyboard = instrumentation.uiAutomation.windows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            return keyboard?.let { Rect().also(it::getBoundsInScreen).top } ?: Int.MAX_VALUE
        }
        fun insetSnapshot(): String {
            var value = ""
            main {
                val decor = activity.window.decorView
                val content = activity.findViewById<View>(android.R.id.content)
                val rootInsets = ViewCompat.getRootWindowInsets(decor)
                val visible = Rect().also(decor::getWindowVisibleDisplayFrame)
                val field = node("input")?.let { Rect().also(it::getBoundsInScreen) }
                value = "decor=${decor.width}x${decor.height}, content=${content.width}x${content.height}, " +
                    "padding=${content.paddingTop}/${content.paddingBottom}, visible=$visible, field=$field, " +
                    "ime=${rootInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom}, " +
                    "bars=${rootInsets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom}, " +
                    "keyboardTop=${keyboardTop()}, softInput=${activity.window.attributes.softInputMode}"
            }
            return value
        }
        fun editor(): View {
            fun find(view: View): View? {
                if (view.onCheckIsTextEditor()) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                return null
            }
            return requireNotNull(find(activity.window.decorView))
        }
        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 15000
            do {
                checkErrors()
                if (condition()) return
                SystemClock.sleep(50)
            } while (SystemClock.uptimeMillis() < deadline)
            checkErrors()
            fail("$message; ${insetSnapshot()}")
        }

        fun assertQueuedDelivery() {
            checkErrors()
            assertFalse("A script callback ran inside renderer enqueue", synchronousCallback.get())
            assertFalse("UI-mode callback left the script dispatcher", wrongCallbackThread.get())
            assertEquals(emitted.toList(), delivered.toList())
            assertTrue(delivered.all { it.sessionId == id })
        }

        private fun checkErrors() { errors.firstOrNull()?.let { throw AssertionError("Renderer failed: ${it.code}", it) } }

        override fun close() {
            main {
                try {
                    if (::session.isInitialized) {
                        session.close()
                        assertEquals(ComposeSession.State.CLOSED, session.state)
                        assertEquals(0, session.callbackCount)
                        assertNull(session.tree)
                    }
                } finally { activity.finish() }
            }
        }
    }

    private fun tagged(key: String) = listOf(ModifierOp(M.TEST_TAG, listOf(UiValue.Str("compose-controls-$key"))))
    private fun isChecked(node: AccessibilityNodeInfo) = node.isChecked || node.isSelected
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun click(key: String) = assertTrue("$key exposes an accessible click", requireNotNull(node(key)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    private fun setProgress(value: Float) {
        val slider = requireNotNull(node("slider"))
        val action = AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id
        assertTrue("Slider exposes accessible progress changes", slider.actionList.any { it.id == action })
        assertTrue(slider.performAction(action, Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }))
    }

    private fun node(key: String): AccessibilityNodeInfo? {
        val tag = "compose-controls-$key"
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (!node.refresh()) return null
            if (node.viewIdResourceName == tag || node.viewIdResourceName == "${context.packageName}:id/$tag") return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { child -> find(child)?.let { return it } }
            return null
        }
        // Only inspect this isolated test host, including when another app owns accessibility focus.
        return instrumentation.uiAutomation.windows.asSequence().mapNotNull { it.root }
            .filter { it.packageName?.toString() == context.packageName }.mapNotNull(::find).firstOrNull()
    }
    private fun findText(text: String): AccessibilityNodeInfo? {
        fun contains(node: AccessibilityNodeInfo): Boolean {
            if (node.text?.toString() == text) return true
            for (i in 0 until node.childCount) if (node.getChild(i)?.let(::contains) == true) return true
            return false
        }
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isClickable && contains(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        return instrumentation.uiAutomation.windows.asSequence().mapNotNull { it.root }
            .filter { it.packageName?.toString() == context.packageName }.mapNotNull(::find).firstOrNull()
    }
}
