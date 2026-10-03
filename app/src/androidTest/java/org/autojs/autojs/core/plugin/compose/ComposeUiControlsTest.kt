package org.autojs.autojs.core.plugin.compose

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
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

    private inner class Fixture(private val id: Int) : Closeable {
        private val activity = instrumentation.startActivitySync(
            Intent(context, AboutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as AboutActivity
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
                val recording = loaded.copy(factory = object : ComposeUiRendererFactory by loaded.factory {
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

        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 15000
            do {
                checkErrors()
                if (condition()) return
                SystemClock.sleep(50)
            } while (SystemClock.uptimeMillis() < deadline)
            checkErrors()
            fail(message)
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
}
