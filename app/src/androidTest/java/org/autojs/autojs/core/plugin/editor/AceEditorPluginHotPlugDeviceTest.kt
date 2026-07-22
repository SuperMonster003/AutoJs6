package org.autojs.autojs.core.plugin.editor

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs6.R
import org.autojs.plugin.editor.api.EditorPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Device coverage for process-local Ace enable/disable hot plugging. */
class AceEditorPluginHotPlugDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun editorHotPlugPreservesUnsavedTextAndRespectsExplicitNativeSelection() {
        val enginePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE)
        val selectionModePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE)
        val originalPluginEnabled = PluginEnableStore.isEnabled(
            context,
            EditorPluginContract.DEFAULT_PACKAGE_NAME,
            defaultEnabled = true,
        )
        val sourceFile = createTempScriptFile(INITIAL_TEXT)
        var activity: EditActivity? = null
        var editorView: EditorView? = null

        try {
            assumeTrue("Ace editor plugin is not installed", isPluginInstalled())
            setPluginEnabled(true)
            assumeTrue(
                "Installed Ace editor plugin is not eligible for host loading",
                runOnMainSync { AceEditorPluginStateMonitor.current(context).available },
            )

            val autoSelection = runOnMainSync {
                EditorEnginePreferences.selectByUser(context, EditorEngineType.ACE)
            }
            assertEquals(EditorEngineType.ACE, autoSelection.effectiveType)
            assertFalse(autoSelection.needsAcePlugin)

            val launchedActivity = launchEditActivity(sourceFile)
            activity = launchedActivity
            val activeEditorView = runOnMainSync {
                checkNotNull(launchedActivity.findViewById<EditorView>(R.id.editor_view))
            }
            editorView = activeEditorView
            waitForEditorState(
                editorView = activeEditorView,
                expectedEngine = EditorEngineType.ACE,
                expectedText = INITIAL_TEXT,
                expectedDirty = false,
                message = "Editor should start with the available Ace plugin",
            )

            replaceTextAsUserEdit(activeEditorView, EDITED_TEXT)
            waitForEditorState(
                editorView = activeEditorView,
                expectedEngine = EditorEngineType.ACE,
                expectedText = EDITED_TEXT,
                expectedDirty = true,
                message = "Ace should expose the unsaved edit before hot unplug",
            )

            setPluginEnabled(false)
            waitForPluginAvailability(false)
            waitForEditorState(
                editorView = activeEditorView,
                expectedEngine = EditorEngineType.NATIVE,
                expectedText = EDITED_TEXT,
                expectedDirty = true,
                message = "Disabling Ace should fall back without losing unsaved text",
            )

            setPluginEnabled(true)
            waitForPluginAvailability(true)
            waitForEditorState(
                editorView = activeEditorView,
                expectedEngine = EditorEngineType.ACE,
                expectedText = EDITED_TEXT,
                expectedDirty = true,
                message = "AUTO mode should restore Ace when the plugin is enabled again",
            )

            val explicitNativeSelection = runOnMainSync {
                EditorEnginePreferences.selectByUser(context, EditorEngineType.NATIVE)
            }
            assertEquals(EditorEngineType.NATIVE, explicitNativeSelection.effectiveType)
            waitForEditorState(
                editorView = activeEditorView,
                expectedEngine = EditorEngineType.NATIVE,
                expectedText = EDITED_TEXT,
                expectedDirty = true,
                message = "Explicit Native selection should preserve the current document",
            )

            setPluginEnabled(false)
            waitForPluginAvailability(false)
            setPluginEnabled(true)
            waitForPluginAvailability(true)
            instrumentation.waitForIdleSync()
            SystemClock.sleep(PLUGIN_STATE_SETTLE_MS)

            runOnMainSync {
                assertEquals(EditorEngineType.NATIVE, EditorEnginePreferences.get())
                assertEquals(EditorEngineType.NATIVE, activeEditorView.editor.engineType)
                assertEquals(EDITED_TEXT, activeEditorView.editor.textSnapshot)
                assertTrue(activeEditorView.isTextChanged)
                assertTrue(activeEditorView.saveStickyDirty)
            }
        } finally {
            editorView?.let(::markEditorCleanBestEffort)
            activity?.let(::finishActivity)
            runOnMainSync {
                PluginEnableStore.setEnabled(
                    context,
                    EditorPluginContract.DEFAULT_PACKAGE_NAME,
                    originalPluginEnabled,
                )
                AceEditorPluginStateMonitor.refresh(AceEditorPluginStateMonitor.Cause.PLUGIN_STATE_CHANGED)
            }
            restoreEnginePreferences(enginePreference, selectionModePreference)
            sourceFile.delete()
        }
    }

    @Test
    fun editorPackageRemovalPreservesUnsavedTextAndRestoresAceAfterReinstall() {
        assumeTrue(
            "External package mutation test was not requested",
            InstrumentationRegistry.getArguments()
                .getString(EXTERNAL_PACKAGE_MUTATION_ARGUMENT)
                ?.toBooleanStrictOrNull() == true,
        )
        val enginePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE)
        val selectionModePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE)
        val originalPluginEnabled = PluginEnableStore.isEnabled(
            context,
            EditorPluginContract.DEFAULT_PACKAGE_NAME,
            defaultEnabled = true,
        )
        val sourceFile = createTempScriptFile(INITIAL_TEXT)
        var activity: EditActivity? = null
        var editorView: EditorView? = null

        try {
            assumeTrue("Ace editor plugin is not installed", isPluginInstalled())
            setPluginEnabled(true)
            assumeTrue(
                "Installed Ace editor plugin is not eligible for host loading",
                runOnMainSync { AceEditorPluginStateMonitor.current(context).available },
            )
            val selection = runOnMainSync {
                EditorEnginePreferences.selectByUser(context, EditorEngineType.ACE)
            }
            assertEquals(EditorEngineType.ACE, selection.effectiveType)

            val launchedActivity = launchEditActivity(sourceFile)
            activity = launchedActivity
            val activeEditorView = runOnMainSync {
                checkNotNull(launchedActivity.findViewById<EditorView>(R.id.editor_view))
            }
            editorView = activeEditorView
            waitForEditorState(
                editorView = activeEditorView,
                expectedEngine = EditorEngineType.ACE,
                expectedText = INITIAL_TEXT,
                expectedDirty = false,
                message = "Editor should start with the installed Ace package",
            )
            replaceTextAsUserEdit(activeEditorView, EDITED_TEXT)
            waitForEditorState(
                editorView = activeEditorView,
                expectedEngine = EditorEngineType.ACE,
                expectedText = EDITED_TEXT,
                expectedDirty = true,
                message = "Ace should expose the unsaved edit before package removal",
            )

            Log.i(TAG, EXTERNAL_READY_MARKER)
            waitForPluginAvailability(false, EXTERNAL_PACKAGE_MUTATION_TIMEOUT_MS)
            waitForEditorState(
                editorView = activeEditorView,
                expectedEngine = EditorEngineType.NATIVE,
                expectedText = EDITED_TEXT,
                expectedDirty = true,
                message = "Package removal should fall back without losing unsaved text",
            )

            Log.i(TAG, EXTERNAL_FALLBACK_VERIFIED_MARKER)
            waitForPluginAvailability(true, EXTERNAL_PACKAGE_MUTATION_TIMEOUT_MS)
            waitForEditorState(
                editorView = activeEditorView,
                expectedEngine = EditorEngineType.ACE,
                expectedText = EDITED_TEXT,
                expectedDirty = true,
                message = "Package reinstall should restore Ace in AUTO mode",
            )
        } finally {
            editorView?.let(::markEditorCleanBestEffort)
            activity?.let(::finishActivity)
            runOnMainSync {
                PluginEnableStore.setEnabled(
                    context,
                    EditorPluginContract.DEFAULT_PACKAGE_NAME,
                    originalPluginEnabled,
                )
                AceEditorPluginStateMonitor.refresh(AceEditorPluginStateMonitor.Cause.PLUGIN_STATE_CHANGED)
            }
            restoreEnginePreferences(enginePreference, selectionModePreference)
            sourceFile.delete()
        }
    }

    private fun setPluginEnabled(enabled: Boolean) {
        runOnMainSync {
            PluginEnableStore.setEnabled(
                context,
                EditorPluginContract.DEFAULT_PACKAGE_NAME,
                enabled,
            )
            // A no-op write does not notify PluginStateChangeRegistry, so refresh explicitly.
            AceEditorPluginStateMonitor.refresh(AceEditorPluginStateMonitor.Cause.PLUGIN_STATE_CHANGED)
        }
        instrumentation.waitForIdleSync()
    }

    private fun waitForPluginAvailability(
        expected: Boolean,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    ) {
        waitUntil("Ace plugin availability should become $expected", timeoutMillis) {
            runOnMainSync { AceEditorPluginStateMonitor.current(context).available == expected }
        }
    }

    private fun launchEditActivity(sourceFile: File): EditActivity {
        val intent = Intent(context, EditActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EditorView.EXTRA_PATH, sourceFile.absolutePath)
            .putExtra(EditorView.EXTRA_NAME, sourceFile.nameWithoutExtension)
        return (instrumentation.startActivitySync(intent) as EditActivity).also {
            instrumentation.waitForIdleSync()
        }
    }

    private fun replaceTextAsUserEdit(editorView: EditorView, text: String) {
        val deadline = SystemClock.uptimeMillis() + EDITOR_OPERATION_TIMEOUT_MS
        var attempt = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val completed = CountDownLatch(1)
            val applied = AtomicReference(false)
            runOnMainSync {
                editorView.editor.replaceAllTextUndoably(
                    text = text,
                    token = "ace-hot-plug-device-edit-${++attempt}",
                    onComplete = { success ->
                        applied.set(success)
                        completed.countDown()
                    },
                )
            }
            val remainingMillis = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(1L)
            if (!completed.await(remainingMillis, TimeUnit.MILLISECONDS)) break
            if (applied.get()) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        fail("Ace user edit should complete successfully")
    }

    private fun waitForEditorState(
        editorView: EditorView,
        expectedEngine: EditorEngineType,
        expectedText: String,
        expectedDirty: Boolean,
        message: String,
    ) {
        val lastState = AtomicReference("not sampled")
        try {
            waitUntil(message, timeoutMillis = EDITOR_STATE_TIMEOUT_MS) {
                runOnMainSync {
                    val editor = editorView.editor
                    val engine = editor.engineType
                    val ready = editor.isReady
                    val text = editor.textSnapshot
                    val dirty = editorView.isTextChanged
                    val stickyDirty = editorView.saveStickyDirty
                    lastState.set(
                        "engine=$engine, ready=$ready, text=${text.replace("\n", "\\n")}, " +
                            "dirty=$dirty, stickyDirty=$stickyDirty",
                    )
                    engine == expectedEngine && ready && text == expectedText &&
                        dirty == expectedDirty && stickyDirty == expectedDirty
                }
            }
        } catch (error: AssertionError) {
            throw AssertionError("$message; last state: ${lastState.get()}", error)
        }
    }

    private fun markEditorCleanBestEffort(editorView: EditorView) {
        runCatching {
            runOnMainSync {
                editorView.editor.markTextAsSaved()
                editorView.saveStickyDirty = false
            }
        }
    }

    private fun finishActivity(activity: Activity) {
        runOnMainSync {
            if (!activity.isDestroyed) {
                activity.finishAndRemoveTask()
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun createTempScriptFile(text: String): File = File(
        context.cacheDir,
        "ace-hot-plug-${SystemClock.uptimeMillis()}.js",
    ).also { it.writeText(text) }

    @Suppress("DEPRECATION")
    private fun isPluginInstalled(): Boolean = try {
        context.packageManager.getApplicationInfo(
            EditorPluginContract.DEFAULT_PACKAGE_NAME,
            PackageManager.MATCH_DISABLED_COMPONENTS,
        )
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun captureStringPreference(key: String): StoredStringPreference {
        val preferences = Pref.get()
        return StoredStringPreference(
            present = preferences.contains(key),
            value = preferences.getString(key, null),
        )
    }

    private fun restoreEnginePreferences(
        enginePreference: StoredStringPreference,
        selectionModePreference: StoredStringPreference,
    ) {
        val editor = Pref.get().edit()
        enginePreference.restore(editor, EditorEnginePreferences.KEY_EDITOR_ENGINE)
        selectionModePreference.restore(editor, EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE)
        assertTrue("Original editor preferences should be restored", editor.commit())
    }

    private fun waitUntil(
        message: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val satisfied = try {
                condition()
            } catch (error: Throwable) {
                lastError = error
                false
            }
            if (satisfied) return
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

    private data class StoredStringPreference(
        val present: Boolean,
        val value: String?,
    ) {
        fun restore(editor: SharedPreferences.Editor, key: String) {
            if (present) {
                editor.putString(key, value)
            } else {
                editor.remove(key)
            }
        }
    }

    private companion object {
        const val TAG = "AceHotPlugDeviceTest"
        const val EXTERNAL_PACKAGE_MUTATION_ARGUMENT = "aceExternalPackageMutation"
        const val EXTERNAL_READY_MARKER = "ACE_EXTERNAL_HOTPLUG_READY"
        const val EXTERNAL_FALLBACK_VERIFIED_MARKER = "ACE_EXTERNAL_HOTPLUG_FALLBACK_VERIFIED"
        const val INITIAL_TEXT = "const hotPlugState = 'initial';\n"
        const val EDITED_TEXT = "const hotPlugState = 'unsaved';\nconst dirty = true;\n"
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val EDITOR_STATE_TIMEOUT_MS = 25_000L
        const val EDITOR_OPERATION_TIMEOUT_MS = 10_000L
        const val EXTERNAL_PACKAGE_MUTATION_TIMEOUT_MS = 90_000L
        const val PLUGIN_STATE_SETTLE_MS = 750L
        const val POLL_INTERVAL_MS = 50L
    }
}
