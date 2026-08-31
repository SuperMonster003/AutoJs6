package org.autojs.autojs.ui.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.fragment.app.FragmentActivity
import org.autojs.autojs.core.plugin.editor.AceEditorPluginStateMonitor
import org.autojs.autojs.core.plugin.editor.AceEditorPluginHost
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs6.R
import org.autojs.plugin.editor.api.EditorPluginContract
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class AceEditorLanguageSettingsDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = Pref.get()
    private val originalPreferences = SETTINGS_KEYS.associateWith { key -> preferences.storedValue(key) }
    private val originalPluginEnabled = PluginEnableStore.isEnabled(
        context,
        EditorPluginContract.DEFAULT_PACKAGE_NAME,
        defaultEnabled = true,
    )
    private var activity: CodeEditorSettingsActivity? = null

    @Before
    fun setUp() {
        PluginEnableStore.setEnabled(context, EditorPluginContract.DEFAULT_PACKAGE_NAME, true)
        AceEditorPluginStateMonitor.refresh(AceEditorPluginStateMonitor.Cause.PLUGIN_STATE_CHANGED)
        instrumentation.waitForIdleSync()
        val pluginState = AceEditorPluginStateMonitor.current(context)
        assertTrue(
            "Ace Editor plugin must be eligible for the X-4 settings device test: " +
                "state=$pluginState, discovery=${AceEditorPluginHost.discover(context)}",
            pluginState.available,
        )
        EditorEnginePreferences.set(EditorEngineType.ACE)
        AceEditorLspPreferences.setEnabled(true)
        AceEditorLspPreferences.SUPPORTED_SEMANTIC_LANGUAGES.forEach(
            AceEditorLspPreferences::resetSemanticEnabled,
        )
    }

    @After
    fun tearDown() {
        activity?.let { launched ->
            runOnMainSync {
                if (!launched.isDestroyed) launched.finishAndRemoveTask()
            }
            instrumentation.waitForIdleSync()
        }
        preferences.edit().apply {
            SETTINGS_KEYS.forEach(::remove)
            originalPreferences.forEach { (key, value) -> value.restore(this, key) }
        }.commit()
        PluginEnableStore.setEnabled(
            context,
            EditorPluginContract.DEFAULT_PACKAGE_NAME,
            originalPluginEnabled,
        )
        AceEditorPluginStateMonitor.refresh(AceEditorPluginStateMonitor.Cause.PLUGIN_STATE_CHANGED)
        activity = null
    }

    @Test
    fun languageMatrixAndPerLanguageSemanticSwitchAreInteractive() {
        val fragment = launchSettings()
        val matrix = runOnMainSync {
            fragment.findPreference<AceLanguageSupportMatrixPreference>(
                context.getString(R.string.key_ace_language_support_matrix),
            )
        }
        val semantic = runOnMainSync {
            fragment.findPreference<AceSemanticLanguagesPreference>(
                context.getString(R.string.key_ace_semantic_languages),
            )
        }
        val fileTypes = runOnMainSync {
            fragment.findPreference<AceLspFileTypesPreference>(
                context.getString(R.string.key_ace_lsp_file_types),
            )
        }

        assertNotNull(matrix)
        assertNotNull(semantic)
        assertNotNull(fileTypes)
        assertTrue(runOnMainSync { matrix!!.isVisible })
        assertTrue(runOnMainSync { semantic!!.isVisible })
        assertTrue(runOnMainSync { fileTypes!!.isVisible })
        val activityContext = activity!!
        assertEquals(
            activityContext.getString(
                R.string.summary_ace_language_support_matrix,
                AceEditorLspPreferences.LANGUAGE_SUPPORT_MATRIX.size,
            ),
            runOnMainSync { matrix!!.summary.toString() },
        )
        assertEquals(
            activityContext.getString(R.string.summary_ace_semantic_languages, 4, 4),
            runOnMainSync { semantic!!.summary.toString() },
        )

        runOnMainSync { matrix!!.performClick() }
        waitForActiveWindow("JavaScript")
        val matrixWindow = scrollActiveWindowUntil("Kotlin")
        assertTrue(visibleTexts(matrixWindow).any { it.contains("P2+") })
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitUntil("Language support matrix dialog should close") {
            visibleTexts(instrumentation.uiAutomation.rootInActiveWindow).none {
                it.contains("P2+")
            }
        }

        runOnMainSync { semantic!!.performClick() }
        val semanticWindow = waitForActiveWindow("TypeScript / JavaScript", "Python")
        clickNode(semanticWindow, "Python")
        val kotlinLabel = activityContext.getString(
            R.string.format_ace_semantic_language_unavailable,
            "Kotlin",
        )
        val kotlinWindow = scrollActiveWindowUntil(kotlinLabel)
        val kotlinNode = findNode(kotlinWindow) { it.text?.toString() == kotlinLabel }
        assertNotNull(kotlinNode)
        assertEquals(kotlinLabel, kotlinNode!!.text.toString())
        val semanticTop = scrollActiveWindowUntil(
            "TypeScript / JavaScript",
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
        )
        clickNode(semanticTop, activityContext.getString(R.string.dialog_button_confirm))

        waitUntil("Python semantic preference should be disabled after confirmation") {
            !AceEditorLspPreferences.isSemanticEnabled(AceEditorLspPreferences.SEMANTIC_LANGUAGE_PYTHON)
        }
        assertFalse(AceEditorLspPreferences.isSemanticEnabled(AceEditorLspPreferences.SEMANTIC_LANGUAGE_KOTLIN))
        assertEquals(
            activityContext.getString(R.string.summary_ace_semantic_languages, 3, 4),
            runOnMainSync { semantic!!.summary.toString() },
        )
    }

    private fun launchSettings(): CodeEditorSettingsFragment {
        val monitor = instrumentation.addMonitor(CodeEditorSettingsActivity::class.java.name, null, false)
        try {
            context.startActivity(
                Intent(context, CodeEditorSettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            activity = instrumentation.waitForMonitorWithTimeout(monitor, ACTIVITY_TIMEOUT_MS)
                as? CodeEditorSettingsActivity
        } finally {
            instrumentation.removeMonitor(monitor)
        }
        assertNotNull("Code editor settings activity should launch", activity)
        instrumentation.waitForIdleSync()
        var fragment: CodeEditorSettingsFragment? = null
        waitUntil("Code editor settings fragment should be created") {
            fragment = runOnMainSync {
                (activity as FragmentActivity).supportFragmentManager
                    .findFragmentById(R.id.fragment_preferences) as? CodeEditorSettingsFragment
            }
            fragment?.preferenceScreen != null
        }
        return fragment!!
    }

    private fun waitForActiveWindow(vararg requiredText: String): AccessibilityNodeInfo {
        var root: AccessibilityNodeInfo? = null
        waitUntil("Expected dialog content was not exposed to accessibility") {
            instrumentation.waitForIdleSync()
            root = instrumentation.uiAutomation.rootInActiveWindow
            val texts = visibleTexts(root)
            requiredText.all { expected -> texts.any { it.contains(expected) } }
        }
        return root!!
    }

    private fun scrollActiveWindowUntil(
        expectedText: String,
        scrollAction: Int = AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
    ): AccessibilityNodeInfo {
        repeat(MAX_SCROLL_ATTEMPTS) {
            instrumentation.waitForIdleSync()
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (visibleTexts(root).any { text -> text.contains(expectedText) }) return root
            val scrollable = findNode(root) { node -> node.isScrollable }
            if (scrollable == null ||
                !scrollable.performAction(scrollAction)
            ) {
                fail("Dialog cannot scroll to accessibility text: $expectedText")
            }
            SystemClock.sleep(SCROLL_SETTLE_MS)
        }
        fail("Dialog did not expose accessibility text after scrolling: $expectedText")
        throw AssertionError("unreachable")
    }

    private fun clickNode(root: AccessibilityNodeInfo, text: String) {
        var node = findNode(root) { it.text?.toString() == text }
            ?: findNode(root) { it.text?.toString()?.contains(text) == true }
        assertNotNull("Accessibility node should contain: $text", node)
        while (node != null) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) break
            node = node.parent
        }
        assertNotNull("Accessibility node should be actionable: $text", node)
        instrumentation.waitForIdleSync()
    }

    private fun visibleTexts(root: AccessibilityNodeInfo?): List<String> {
        val texts = mutableListOf<String>()
        walk(root) { node ->
            node.text?.toString()?.takeIf(String::isNotBlank)?.let(texts::add)
        }
        return texts
    }

    private fun findNode(
        root: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        var result: AccessibilityNodeInfo? = null
        walk(root) { node ->
            if (result == null && predicate(node)) result = node
        }
        return result
    }

    private fun walk(root: AccessibilityNodeInfo?, visitor: (AccessibilityNodeInfo) -> Unit) {
        if (root == null) return
        visitor(root)
        for (index in 0 until root.childCount) walk(root.getChild(index), visitor)
    }

    private fun waitUntil(
        message: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                if (condition()) return
            } catch (throwable: Throwable) {
                lastError = throwable
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        lastError?.let { throw AssertionError("$message: ${it.message}", it) }
        fail(message)
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

    private fun SharedPreferences.storedValue(key: String): StoredPreference {
        return StoredPreference(contains(key), all[key])
    }

    private data class StoredPreference(
        val present: Boolean,
        val value: Any?,
    ) {
        fun restore(editor: SharedPreferences.Editor, key: String) {
            if (!present) return
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
    }

    private companion object {
        val SETTINGS_KEYS = listOf(
            EditorEnginePreferences.KEY_EDITOR_ENGINE,
            EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE,
            AceEditorLspPreferences.KEY_ACE_LSP_ENABLED,
            AceEditorLspPreferences.KEY_ACE_SEMANTIC_TYPESCRIPT_ENABLED,
            AceEditorLspPreferences.KEY_ACE_SEMANTIC_PYTHON_ENABLED,
            AceEditorLspPreferences.KEY_ACE_SEMANTIC_LUA_ENABLED,
            AceEditorLspPreferences.KEY_ACE_SEMANTIC_JAVA_ENABLED,
            AceEditorLspPreferences.KEY_ACE_SEMANTIC_KOTLIN_ENABLED,
        )
        const val ACTIVITY_TIMEOUT_MS = 45_000L
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val MAX_SCROLL_ATTEMPTS = 12
        const val POLL_INTERVAL_MS = 100L
        const val SCROLL_SETTLE_MS = 250L
    }
}
