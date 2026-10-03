package org.autojs.autojs.ui.floating.codegen

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.NodeInfo
import org.autojs.autojs.core.accessibility.codegen.CodeGenEngine
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Action
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Mode
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Scope
import org.autojs.autojs.core.accessibility.codegen.CodeGenRequest.Style
import org.autojs.autojs.core.accessibility.codegen.CodeGenResult
import org.autojs.autojs.core.accessibility.inspector.InspectorNode
import org.autojs.autojs.core.accessibility.inspector.InspectorNodes
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs6.R
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Executes rendered code through the host's real Rhino/selector/Flow pipeline on live controls.
 * zh-CN: 通过宿主真实 Rhino/选择器/Flow 流水线, 在实时控件上执行生成代码. */
@RunWith(AndroidJUnit4::class)
class CodeGenExecutionDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var activity: CodeGenSmokeActivity
    private lateinit var automation: UiAutomation
    private var sequence = 0

    @Before
    fun launchFixture() {
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        activity = instrumentation.startActivitySync(Intent(context, CodeGenSmokeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CodeGenSmokeActivity
        instrumentation.waitForIdleSync()
        await("Fixture visible") { automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Codegen click")?.isNotEmpty() == true }
        // A capture and the script's own accessibility connection must both be ready.
        await("Host accessibility service bound") { org.autojs.autojs.core.accessibility.AccessibilityService.instance != null }
    }

    @After
    fun closeFixture() {
        if (::activity.isInitialized) instrumentation.runOnMainSync { activity.finish() }
    }

    @Test
    fun clickLongClickAndEscapedTextHonorAllThreeModesAndBothStyles() {
        val modes = listOf(Mode.WAIT, Mode.WAIT_TIMEOUT(3_000), Mode.CHECK)
        for (style in Style.entries) for (mode in modes) {
            activity.resetReceipts()
            val clicked = generateOne("codegen-click", Action.CLICK, mode, style)
            execute(clicked.preferred.code)
            await("Exactly one click") { activity.clicks.get() == 1 }
            assertEquals(0, activity.longClicks.get())
            val longClicked = generateOne("codegen-click", Action.LONG_CLICK, mode, style)
            execute(longClicked.preferred.code)
            await("Exactly one long click") { activity.longClicks.get() == 1 }
            val text = "a\"b\\c"
            execute(generateOne("codegen-input", Action.SET_TEXT(text), mode, style).preferred.code)
            await("Text value retained exactly") { onMain { activity.input.text.toString() } == text }
        }
    }

    @Test
    fun duplicateButtonsSupportAnExactSingleTargetAndTheWholeCollection() {
        for (style in Style.entries) {
            activity.resetReceipts()
            val root = capture()
            val buttons = InspectorNodes.preOrder(root).filter { it.id() == context.getStringId(R.id.codegen_fixture_delete) }
            assertEquals(20, buttons.size)
            val single = result(root, CodeGenRequest(listOf(buttons[3]), action = Action.CLICK, style = style))
            execute(single.preferred.code)
            await("Fourth row clicked") { activity.itemClicks.get(3) == 1 }
            repeat(20) { index -> assertEquals(if (index == 3) 1 else 0, activity.itemClicks.get(index)) }
            activity.resetReceipts()
            val collection = result(root, CodeGenRequest(buttons, Scope.COLLECTION, action = Action.CLICK, style = style))
            assertEquals(20, collection.preferred.matchedCount)
            execute(collection.preferred.code)
            await("All rows clicked exactly once") { (0 until 20).all { activity.itemClicks.get(it) == 1 } }
        }
    }

    @Test
    fun fullIdsDistinguishFrameworkAndApplicationTitlesAndNoIdControlsStillWork() {
        val root = capture()
        val nodes = InspectorNodes.preOrder(root)
        assertEquals(2, nodes.count { it.id()?.substringAfterLast(":id/") == "title" })
        val title = nodes.single { it.id() == context.getStringId(R.id.title) }
        val generated = result(root, CodeGenRequest(listOf(title), mode = Mode.CHECK))
        assertTrue(generated.preferred.code.contains(context.getStringId(R.id.title)))
        execute(generated.preferred.code + "\nif (!found) throw new Error('Expected the application title');")
        val input = nodes.single { it.desc() == "codegen-input" }
        assertNull(input.id())
        execute(result(root, CodeGenRequest(listOf(input), action = Action.SET_TEXT("no id"))).preferred.code)
        await("Id-less input receives text") { onMain { activity.input.text.toString() } == "no id" }
    }

    @Test
    fun timedAndImmediateMissingTargetsSkipActionsInBothStyles() {
        for (style in Style.entries) for (mode in listOf(Mode.CHECK, Mode.WAIT_TIMEOUT(100))) {
            onMain { activity.button.visibility = View.VISIBLE }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(200)
            val code = generateOne("codegen-click", Action.CLICK, mode, style).preferred.code
            activity.resetReceipts()
            onMain { activity.button.visibility = View.GONE }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(300)
            execute(code)
            assertEquals(0, activity.clicks.get())
        }
    }

    @Test
    fun scrollingActionsUseTheCapturedScrollContainer() {
        for (style in Style.entries) {
            onMain { activity.scroll.scrollTo(0, 0) }
            instrumentation.waitForIdleSync()
            execute(generateOne("codegen-scroll", Action.SCROLL_FORWARD, Mode.WAIT_TIMEOUT(3_000), style).preferred.code)
            await("Scroll moved forward") { onMain { activity.scroll.scrollY } > 0 }
            SystemClock.sleep(400)
            val previous = onMain { activity.scroll.scrollY }
            execute(generateOne("codegen-scroll", Action.SCROLL_BACKWARD, Mode.CHECK, style).preferred.code)
            await("Scroll moved backward") { onMain { activity.scroll.scrollY } < previous }
        }
    }

    private fun generateOne(desc: String, action: Action, mode: Mode, style: Style): CodeGenResult<InspectorNode> {
        val root = capture()
        val target = InspectorNodes.preOrder(root).single { it.desc() == desc }
        return result(root, CodeGenRequest(listOf(target), action = action, mode = mode, style = style))
    }

    private fun result(root: InspectorNode, request: CodeGenRequest<InspectorNode>): CodeGenResult<InspectorNode> {
        val outcome = CodeGenEngine().generate(root, request)
        assertTrue("Generation failed: " + outcome, outcome is CodeGenResult)
        @Suppress("UNCHECKED_CAST")
        return outcome as CodeGenResult<InspectorNode>
    }

    private fun capture(): InspectorNode {
        instrumentation.waitForIdleSync()
        val raw = requireNotNull(automation.rootInActiveWindow)
        assertEquals(context.packageName, raw.packageName.toString())
        return InspectorNode(NodeInfo.capture(context, raw))
    }

    private fun execute(code: String) {
        val directory = File(context.getExternalFilesDir(null), "codegen-smoke").apply { mkdirs() }
        File(directory, "generated-" + sequence++ + ".js").writeText(code)
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val execution = AutoJs.instance.scriptEngineService.execute(
            StringScriptSource("codegen-smoke.js", code),
            object : SimpleScriptExecutionListener() {
                override fun onSuccess(execution: ScriptExecution, result: Any?) { done.countDown() }
                override fun onException(execution: ScriptExecution, error: Throwable) { failure.set(error); done.countDown() }
            },
            ExecutionConfig(workingDirectory = directory.path),
        )
        if (!done.await(12, TimeUnit.SECONDS)) {
            execution.engine?.forceStop()
            fail("Generated script timed out:\n" + code)
        }
        failure.get()?.let { throw AssertionError("Generated script failed:\n" + code, it) }
        instrumentation.waitForIdleSync()
    }

    private fun android.content.Context.getStringId(id: Int): String = resources.getResourceName(id)

    private fun <T> onMain(block: () -> T): T {
        val value = AtomicReference<T>()
        instrumentation.runOnMainSync { value.set(block()) }
        return value.get()
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(message, condition())
    }
}
