package org.autojs.autojs.runtime.api.modules.compose

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.accessibility.AccessibilityServiceUsher
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.open.RunIntentActivity
import org.autojs.autojs.script.StringScriptSource
import org.autojs.autojs6.R
import org.autojs.plugin.compose.api.ComposeUiIds
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * P4.2: the host's installed accessibility service and real script selectors exercise Compose.
 * UiAutomation keeps services enabled and repairs the test runner's force-stop;
 * it never supplies nodes or actions.
 * Enable the isolated package's AccessibilityServiceUsher before running this test class.
 */
@RunWith(AndroidJUnit4::class)
class ComposeAccessibilityDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val scripts get() = AutoJs.instance.scriptEngineService
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }
    private val guardKey by lazy { context.getString(R.string.key_guard_mode) }
    private var prepared = false
    private var previousPluginEnabled = false
    private var previousGuard: Boolean? = null

    @Before fun prepare() {
        assumeTrue("Use the isolated -PcomposeUiSpike=true host", context.packageName.endsWith(".compose.spike"))
        context.packageManager.getApplicationInfo(ComposeUiIds.PLUGIN_PACKAGE_NAME, 0)
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        rebindPreviouslyEnabledService(automation)
        assertTrue("Enable the isolated host's AccessibilityServiceUsher first", AccessibilityService.waitForOperational(15_000))
        assertEquals(context.packageName, requireNotNull(AccessibilityService.instance).packageName)
        assertTrue("Use the host's default mode with Stable Mode disabled", requireNotNull(AccessibilityService.instance).serviceInfo.flags
            and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS != 0)
        previousPluginEnabled = PluginEnableStore.isEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME)
        previousGuard = if (preferences.contains(guardKey)) preferences.getBoolean(guardKey, false) else null
        prepared = true
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
        // Guard mode deliberately hides the host's own package from script selectors.
        assertTrue(preferences.edit().putBoolean(guardKey, false).commit())
    }

    @After fun restore() {
        if (!prepared) return
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, previousPluginEnabled)
        val edit = preferences.edit()
        previousGuard?.let { edit.putBoolean(guardKey, it) } ?: edit.remove(guardKey)
        assertTrue(edit.commit())
    }

    /**
     * Instrumentation force-stops its target package. Android can then leave that package's
     * already-enabled service in the crashed/binding set instead of binding the new test process.
     * Restart only that existing grant; never enable a service the device owner has not enabled.
     */
    private fun rebindPreviouslyEnabledService(automation: UiAutomation) {
        if (AccessibilityService.waitForOperational(1_000)) return
        val resolver = context.contentResolver
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return
        val enabledFlag = Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        val own = ComponentName(context, AccessibilityServiceUsher::class.java)
        val components = enabled.split(':')
        if (enabledFlag != 1 || components.none { ComponentName.unflattenFromString(it) == own }) return
        val others = components.filterNot { ComponentName.unflattenFromString(it) == own }.joinToString(":")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                writeSecureWithShell(automation, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others)
                SystemClock.sleep(300)
            } finally {
                try {
                    writeSecureWithShell(automation, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabled)
                } finally {
                    writeSecureWithShell(automation, Settings.Secure.ACCESSIBILITY_ENABLED, enabledFlag.toString())
                }
            }
            return
        }
        automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            assertTrue(Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others))
            // Give AccessibilityManager's settings observer a turn before restoring the grant.
            SystemClock.sleep(300)
        } finally {
            try {
                assertTrue(Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabled))
                assertTrue(Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, enabledFlag))
            } finally { automation.dropShellPermissionIdentity() }
        }
    }

    private fun writeSecureWithShell(automation: UiAutomation, key: String, value: String) {
        require(key == Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES || key == Settings.Secure.ACCESSIBILITY_ENABLED)
        // Android 7-9 UiAutomation uses Runtime.exec(String), whose tokenization does not parse
        // shell quotes. Invoke settings directly, accept one whitespace-free literal token,
        // and use delete for an empty list. No value is interpreted by a shell.
        require(value.isEmpty() || value.matches(Regex("[A-Za-z0-9_.$/:]+"))) { "Unexpected accessibility setting token" }
        val command = if (value.isEmpty()) "settings delete secure $key" else "settings put secure $key $value"
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        assertEquals("The temporary accessibility setting is written exactly", value,
            Settings.Secure.getString(context.contentResolver, key).orEmpty())
    }

    @Test fun rawTestTagsMatchHostIdSelectorsAndTheButtonAcceptsClick() {
        Page().use { page ->
            page.await("The built-in service sees the raw testTag") { hostHasNode("start_button") }
            val button = requireNotNull(hostNode("start_button"))
            try {
                assertEquals("start_button", button.viewIdResourceName)
                assertEquals(context.packageName, button.packageName.toString())
                assertTrue(button.isClickable)
            } finally { recycle(button) }
            val result = executeSelectors("""
                let node = id('start_button').findOnce();
                check(node !== null, 'id accepts the unprefixed testTag');
                check(String(node.id()) === 'start_button', 'viewIdResourceName is the exact testTag');
                check(idContains('start_').findOnce() !== null, 'idContains uses the raw string');
                check(idMatches('start_button').findOnce() !== null, 'idMatches matches the full raw string');
                check(idMatches(/^start_button$/).findOnce() !== null, 'idMatches accepts a JavaScript pattern');
                check(idMatches('start').findOnce() === null, 'idMatches requires the whole value');
                check(id('${context.packageName}:id/start_button').findOnce() === null, 'Do not invent a package prefix');
                check(idContains(':id/start_button').findOnce() === null, 'No resource prefix is exposed');
                check(id('start_button').findOnce().click(), 'A real host selector clicks the Compose button');
                JSON.stringify({id: String(node.id()), clicked: true});
            """)
            assertEquals("start_button", result.getString("id"))
            assertTrue(result.getBoolean("clicked"))
            page.await("The accessibility action reaches the JS callback once") { page.clicks.get() == 1 }
            page.assertHealthy()
        }
    }

    @Test fun textAndDescriptionExposeTheirOwnNodesAndReachTheClickableAncestor() {
        Page().use { page ->
            page.await("Standalone text and the tagged button label are exposed") {
                hostHasNode("status_text") && hostHasNode("start_label")
            }
            val result = executeSelectors("""
                let status = text('Compose accessibility ready').findOnce();
                check(status !== null && String(status.id()) === 'status_text', 'text finds a standalone Text');
                let description = desc('Compose status description').findOnce();
                check(description !== null && String(description.id()) === 'described_text', 'desc finds leaf contentDescription');

                let button = id('start_button').findOnce();
                let label = text('Compose start').findOnce();
                check(label !== null && String(label.id()) === 'start_label', 'Button text remains an accessibility child');
                check(!label.clickable(), 'The text child does not own the click action');
                check(String(label.parent().id()) === 'start_button', 'Text child has the tagged button as parent');
                check(label.parent().click(), 'Click the button reached through the text child');

                let buttonDescription = desc('Run Compose action').findOnce();
                check(buttonDescription !== null, 'Button contentDescription is discoverable');
                check(buttonDescription.id() === null && !buttonDescription.clickable(), 'Compose exposes a separate description node');
                check(String(buttonDescription.parent().id()) === 'start_button', 'Description node leads to the same button');
                check(buttonDescription.parent().click(), 'Click the button reached through the description node');
                check(String(button.text()) === '' && button.desc() === null, 'Button does not duplicate descendant text or description');
                JSON.stringify({textId: String(label.id()), textParentId: String(label.parent().id()),
                    descriptionParentId: String(buttonDescription.parent().id()),
                    buttonChildCount: button.childCount(), buttonClass: String(button.className())});
            """)
            assertEquals("start_label", result.getString("textId"))
            assertEquals("start_button", result.getString("textParentId"))
            assertEquals("start_button", result.getString("descriptionParentId"))
            assertTrue(result.getInt("buttonChildCount") >= 2)
            println("compose.p4.hostAccessibility=$result")
            page.await("Both ancestor actions reach the JS callback") { page.clicks.get() == 2 }
            page.assertHealthy()
        }
    }

    private fun executeSelectors(source: String): JSONObject {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val result = AtomicReference<JSONObject?>()
        val execution = scripts.execute(StringScriptSource("compose-p4-selectors.js", """
            function check(value, message) { if (!value) throw new Error(message); }
            // A new window can be exposed before it becomes the default selector's active window.
            check(id('start_button').findOne(5000) !== null, 'The Compose page becomes the active selector window');
        """.trimIndent() + "\n" + source.trimIndent()), object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, value: Any?) {
                try { result.set(JSONObject(value.toString())) } catch (error: Throwable) { failure.set(error) }
                finally { done.countDown() }
            }
            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                done.countDown()
            }
        }, ExecutionConfig())
        if (!done.await(20, TimeUnit.SECONDS)) {
            execution.engine?.forceStop()
            fail("Host selector script timed out")
        }
        failure.get()?.let { throw AssertionError("Host selector script failed; host tree=${hostTree()}", it) }
        return requireNotNull(result.get())
    }

    private inner class Page : Closeable {
        private val marker = "compose-p4-a11y-${UUID.randomUUID()}"
        private val started = CountDownLatch(1)
        private val engineRef = AtomicReference<RhinoJavaScriptEngine?>()
        private val failure = AtomicReference<Throwable?>()
        private val stopping = AtomicBoolean()
        val clicks = AtomicInteger()
        private val listener = object : SimpleScriptExecutionListener() {
            private fun owned(execution: ScriptExecution) = (execution.source as? StringScriptSource)?.script?.contains(marker) == true
            override fun onStart(execution: ScriptExecution) {
                if (!owned(execution)) return
                val engine = execution.engine as? RhinoJavaScriptEngine
                if (engine == null) failure.set(AssertionError("The UI script did not use Rhino"))
                else {
                    engineRef.set(engine)
                    engine.runtime.putProperty("compose.p4.a11y.clicks", clicks)
                }
                started.countDown()
            }
            override fun onException(execution: ScriptExecution, error: Throwable) {
                if (!owned(execution)) return
                if (!stopping.get()) failure.compareAndSet(null, error)
                started.countDown()
            }
        }

        init {
            val source = "\"ui\";\n// $marker\n" + """
                compose.mount(compose.Column({padding: 16},
                    compose.Text({testTag: 'status_text', text: 'Compose accessibility ready'}),
                    compose.Text({testTag: 'described_text', text: 'Described status',
                        contentDescription: 'Compose status description'}),
                    compose.Button({testTag: 'start_button', contentDescription: 'Run Compose action',
                        onClick: function () { runtime.getProperty('compose.p4.a11y.clicks').incrementAndGet(); }},
                        compose.Text({testTag: 'start_label', text: 'Compose start'}))));
            """.trimIndent()
            scripts.registerGlobalScriptExecutionListener(listener)
            try {
                instrumentation.runOnMainSync {
                    context.startActivity(Intent(context, RunIntentActivity::class.java)
                        .putExtra(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT, source)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                assertTrue("RunIntentActivity starts the owned UI script", started.await(30, TimeUnit.SECONDS))
                assertHealthy()
                assertNotNull(engineRef.get())
            } catch (error: Throwable) { close(); throw error }
        }

        fun assertHealthy() { failure.get()?.let { throw AssertionError("Owned UI script failed", it) } }

        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 20_000
            do {
                assertHealthy()
                if (condition()) return
                SystemClock.sleep(50)
            } while (SystemClock.uptimeMillis() < deadline)
            assertHealthy()
            fail("$message; host tree=${hostTree()}")
        }

        override fun close() {
            try {
                stopping.set(true)
                engineRef.get()?.let { engine ->
                    if (!engine.isDestroyed) {
                        val activity = engine.getTag("activity") as? Activity
                        if (activity != null) instrumentation.runOnMainSync { activity.finish() } else engine.forceStop()
                        val deadline = SystemClock.uptimeMillis() + 15_000
                        while (!engine.isDestroyed && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
                        if (!engine.isDestroyed) engine.forceStop()
                    }
                }
            } finally { scripts.unregisterGlobalScriptExecutionListener(listener) }
        }
    }

    /** Every node here comes from the host service, including readiness checks. */
    private fun hostHasNode(tag: String) = hostNode(tag)?.let { recycle(it); true } ?: false

    private fun hostNode(tag: String): AccessibilityNodeInfo? {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        val service = AccessibilityService.instance ?: return null
        service.windows.mapNotNull { it.root }.forEach { root ->
            if (root.packageName?.toString() == context.packageName) queue.add(root) else recycle(root)
        }
        var found: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (found == null && node.refresh()) {
                if (node.viewIdResourceName == tag) { found = node; continue }
                for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
            }
            recycle(node)
        }
        return found
    }

    private fun hostTree(): String {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        AccessibilityService.instance?.windows?.mapNotNull { it.root }?.forEach { root ->
            if (root.packageName?.toString() == context.packageName) queue.add(root) else recycle(root)
        }
        val output = ArrayList<String>()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (output.size < 100) {
                output += "[id=${node.viewIdResourceName},text=${node.text},desc=${node.contentDescription},click=${node.isClickable},children=${node.childCount}]"
                for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
            }
            recycle(node)
        }
        return output.joinToString(" ")
    }

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) = node.recycle()
}
