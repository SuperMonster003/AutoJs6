package org.autojs.autojs.core.plugin.editor

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.plugin.typescript.TypeScriptEditorDefinitionTargetResolver
import org.autojs.autojs.core.plugin.typescript.TypeScriptEditorProjectSnapshotProvider
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.edit.EditorMenu
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs.ui.edit.editor.engine.AceScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.ui.edit.editor.service.EditorCurrentDocumentCodeActionAuthorizer
import org.autojs.autojs.ui.edit.theme.Themes
import org.autojs.autojs.ui.log.LogActivity
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.autojs.plugin.editor.api.EditorPluginCallback
import org.autojs.plugin.editor.api.EditorPluginBooleanCallback
import org.autojs.plugin.editor.api.EditorPluginContract
import org.autojs.plugin.editor.api.EditorPluginCurrentDocumentCodeAction
import org.autojs.plugin.editor.api.EditorPluginCurrentDocumentCodeActionKind
import org.autojs.plugin.editor.api.EditorPluginFailure
import org.autojs.plugin.editor.api.EditorPluginDefinitionTarget
import org.autojs.plugin.editor.api.EditorPluginDefinitionTargetKind
import org.autojs.plugin.editor.api.EditorPluginSessionConfig
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Cross-APK smoke test. It is skipped when the Ace editor plugin is not installed. */
class AceEditorPluginHostDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun installedAceEditorPluginLoadsAndRunsThroughHostAbi() {
        assumeTrue("Ace editor plugin is not installed", isPluginInstalled())

        val ready = CountDownLatch(1)
        val fatalFailure = AtomicReference<EditorPluginFailure>()
        val loaded = AtomicReference<AceEditorPluginHost.LoadedSession>()
        val activity = launchHostActivity()
        try {
            runOnMainSync {
                loaded.set(
                    AceEditorPluginHost.createSession(
                        context = activity,
                        config = EditorPluginSessionConfig(
                            hostPackageName = context.packageName,
                            hostVersionName = BuildConfig.VERSION_NAME,
                            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                            storageDirectoryPath = File(context.cacheDir, "ace-editor-plugin-smoke-fonts").absolutePath,
                        ),
                        callback = object : EditorPluginCallback {
                            override fun onReady(state: org.autojs.plugin.editor.api.EditorPluginState) {
                                ready.countDown()
                            }

                            override fun onFailure(failure: EditorPluginFailure) {
                                if (failure.fatal) {
                                    fatalFailure.compareAndSet(null, failure)
                                    ready.countDown()
                                }
                            }
                        },
                    ),
                )
                assertFalse(loaded.get().session.view.isAttachedToWindow)
                activity.setContentView(loaded.get().session.view)
            }

            assertTrue("Ace editor plugin did not become ready", ready.await(20, TimeUnit.SECONDS))
            fatalFailure.get()?.let { failure ->
                fail("Ace editor plugin failed: ${failure.type}: ${failure.message}")
            }

            val loadedSession = loaded.get()
            runOnMainSync {
                assertEquals(EditorPluginContract.DEFAULT_PACKAGE_NAME, loadedSession.plugin.candidate.serviceInfo.packageName)
                assertEquals(EditorPluginContract.VERSION, loadedSession.plugin.entrypoint.contractVersion)
                assertNotSame(EditorPluginCallback::class.java.classLoader, loadedSession.plugin.classLoader)
                assertEquals(loadedSession.plugin.classLoader, loadedSession.plugin.entrypoint.javaClass.classLoader)
                assertTrue(loadedSession.session.isReady)
                loadedSession.session.setInitialText(SMOKE_TEXT)
            }
            waitUntil("Ace editor plugin did not mirror host text") {
                runOnMainSync { loadedSession.session.textSnapshot == SMOKE_TEXT }
            }
        } finally {
            loaded.get()?.let { loadedSession ->
                runOnMainSync {
                    loadedSession.destroy()
                    loadedSession.destroy()
                }
            }
            finishHostActivity(activity)
        }
    }

    @Test
    fun savedAceThemeAndDocumentAreCommittedBeforeTheFirstVisibleEditorFrame() {
        assumeTrue("Ace editor plugin is not installed", isPluginInstalled())

        val enginePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE)
        val selectionModePreference = captureStringPreference(
            EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE,
        )
        val originalAceTheme = Themes.getCurrentThemeName(context, EditorEngineType.ACE)
        val originalPluginEnabled = PluginEnableStore.isEnabled(
            context,
            EditorPluginContract.DEFAULT_PACKAGE_NAME,
            defaultEnabled = true,
        )
        val sourceText = "print('first line')\nprint('second line')\n"
        val document = File(
            context.cacheDir,
            "ace-editor-initial-presentation-${SystemClock.uptimeMillis()}.py",
        ).apply { writeText(sourceText) }
        var activity: EditActivity? = null

        try {
            setPluginEnabled(true)
            assumeTrue(
                "Installed Ace editor plugin is not eligible for host loading",
                runOnMainSync { AceEditorPluginStateMonitor.current(context).available },
            )
            val selection = runOnMainSync {
                Themes.setCurrent(context, "Tomorrow Night")
                EditorEnginePreferences.selectByUser(context, EditorEngineType.ACE)
            }
            assertEquals(EditorEngineType.ACE, selection.effectiveType)

            val launchedActivity = launchEditActivity(document)
            activity = launchedActivity
            val editorView = runOnMainSync {
                checkNotNull(launchedActivity.findViewById<EditorView>(R.id.editor_view))
            }
            waitUntil(
                message = "Host did not publish the initial Python document in Ace",
                timeoutMillis = 20_000L,
            ) {
                runOnMainSync {
                    editorView.editor.engineType == EditorEngineType.ACE &&
                        editorView.editor.isReady &&
                        editorView.editor.textSnapshot == sourceText
                }
            }

            val engine = runOnMainSync { editorView.editor as AceScriptEditorEngine }
            var diagnostics = runOnMainSync { engine.createAceDiagnosticsSnapshot() }
            waitUntil(
                message = "Ace did not publish its first presentation frame: " +
                    diagnostics.runtimeEventHistory,
                timeoutMillis = 20_000L,
            ) {
                diagnostics = runOnMainSync { engine.createAceDiagnosticsSnapshot() }
                diagnostics.lastFirstPaintAtUptimeMillis != null
            }
            val events = diagnostics.runtimeEventHistory
            val themeAppliedAt = requireNotNull(diagnostics.lastThemeAppliedAtUptimeMillis) {
                "Saved Ace theme was not recorded: $events"
            }
            val pageLoadStartedAt = requireNotNull(diagnostics.lastPageLoadStartedAtUptimeMillis) {
                "Ace page load was not recorded: $events"
            }
            val documentLoadingAt = requireNotNull(
                diagnostics.lastHostDocumentLoadingStartedAtUptimeMillis,
            ) {
                "Initial document loading was not recorded: $events"
            }
            val documentReadyAt = requireNotNull(diagnostics.lastHostDocumentReadyAtUptimeMillis) {
                "Initial document readiness was not recorded: $events"
            }
            val firstPaintAt = requireNotNull(diagnostics.lastFirstPaintAtUptimeMillis) {
                "Ace first paint was not recorded: $events"
            }
            assertTrue(
                "Saved Ace theme must be applied no later than the plugin page starts: " +
                    "theme=$themeAppliedAt,page=$pageLoadStartedAt,events=$events",
                themeAppliedAt <= pageLoadStartedAt,
            )
            assertTrue(
                "Ace first paint must not precede the final document submission: " +
                    "loading=$documentLoadingAt,ready=$documentReadyAt," +
                    "firstPaint=$firstPaintAt,events=$events",
                documentLoadingAt <= documentReadyAt && documentReadyAt <= firstPaintAt,
            )

            var deferredIndexReadyAt = diagnostics.lastDeferredStaticIndexReadyAtUptimeMillis
            waitUntil(
                message = "Ace deferred completion index did not become ready",
                timeoutMillis = 20_000L,
            ) {
                diagnostics = runOnMainSync { engine.createAceDiagnosticsSnapshot() }
                deferredIndexReadyAt = diagnostics.lastDeferredStaticIndexReadyAtUptimeMillis
                deferredIndexReadyAt != null
            }
            assertTrue(
                "Deferred completion work must follow first paint: " +
                    "firstPaint=$firstPaintAt,deferredIndex=$deferredIndexReadyAt",
                requireNotNull(deferredIndexReadyAt) >= firstPaintAt,
            )

            val webView = runOnMainSync { engine.inputView as WebView }
            val presentation = decodeJavascriptObject(
                evaluateJavascript(
                    webView,
                    "JSON.stringify({" +
                        "ready: document.body.classList.contains('ace-ready')," +
                        "theme: window.editor.getTheme()," +
                        "bodyBackground: getComputedStyle(document.body).backgroundColor," +
                        "text: window.editor.getSession().getValue()," +
                        "deferredIndex: window.AutoJsAce.getDeferredStaticIndexState()" +
                        "})",
                ),
            )
            assertTrue(presentation.getBoolean("ready"))
            assertEquals("ace/theme/tomorrow_night", presentation.getString("theme"))
            assertEquals("rgb(29, 31, 33)", presentation.getString("bodyBackground"))
            assertEquals(sourceText, presentation.getString("text"))
            assertEquals(
                "ready",
                presentation.getJSONObject("deferredIndex").getString("status"),
            )
        } finally {
            activity?.let(::finishHostActivity)
            runOnMainSync {
                Themes.setCurrent(context, originalAceTheme)
                PluginEnableStore.setEnabled(
                    context,
                    EditorPluginContract.DEFAULT_PACKAGE_NAME,
                    originalPluginEnabled,
                )
                AceEditorPluginStateMonitor.refresh(
                    AceEditorPluginStateMonitor.Cause.PLUGIN_STATE_CHANGED,
                )
            }
            restoreEnginePreferences(enginePreference, selectionModePreference)
            document.delete()
        }
    }

    @Test
    fun foldingMenuWorksThroughPluginContractInReadOnlyMode() {
        assumeTrue("Ace editor plugin is not installed", isPluginInstalled())
        val enginePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE)
        val selectionModePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE)
        val originalPluginEnabled = PluginEnableStore.isEnabled(
            context, EditorPluginContract.DEFAULT_PACKAGE_NAME, defaultEnabled = true,
        )
        val sourceText = """
            function first() {
                if (true) {
                    return 1;
                }
            }
            function second() {
                return 2;
            }
        """.trimIndent()
        val document = File(context.cacheDir, "ace-folding-${SystemClock.uptimeMillis()}.js")
            .apply { writeText(sourceText) }
        var activity: EditActivity? = null
        try {
            setPluginEnabled(true)
            runOnMainSync { EditorEnginePreferences.selectByUser(context, EditorEngineType.ACE) }
            val launchedActivity = launchEditActivity(document).also { activity = it }
            val editorView = runOnMainSync {
                checkNotNull(launchedActivity.findViewById<EditorView>(R.id.editor_view))
            }
            waitUntil("Ace did not load the folding fixture", 20_000L) {
                runOnMainSync {
                    editorView.editor.engineType == EditorEngineType.ACE &&
                        editorView.editor.isReady && editorView.editor.textSnapshot == sourceText &&
                        !editorView.editor.isLoadingText
                }
            }
            val webView = runOnMainSync { editorView.editor.inputView as WebView }
            val foldingStateScript = """
                JSON.stringify({
                    mode: window.editor.session.getMode().${'$'}id,
                    style: window.editor.session.getFoldStyle(),
                    text: window.editor.getValue(),
                    folds: window.editor.session.getAllFolds().map(function(f) { return f.range.toString(); }),
                    widget: window.editor.session.getFoldWidget(0)
                })
            """.trimIndent()
            val menu = runOnMainSync {
                assertTrue("Update the Ace plugin to contract 7", editorView.editor.supportsCodeFolding)
                editorView.toggleReadOnlyMode()
                assertTrue(editorView.isReadOnly())
                androidx.appcompat.widget.PopupMenu(launchedActivity, editorView).apply {
                    inflate(R.menu.menu_editor)
                }.menu.also { EditorMenu(editorView).prepareOptionsMenu(it) }
            }
            runOnMainSync {
                val folding = menu.findItem(R.id.action_code_folding)
                assertTrue(folding.isVisible && folding.isEnabled)
                assertEquals(R.id.action_unfold_all, folding.subMenu!!.getItem(0).itemId)
                assertEquals(R.id.action_fold_all, folding.subMenu!!.getItem(1).itemId)
                assertTrue(EditorMenu(editorView).onOptionsItemSelected(menu.findItem(R.id.action_fold_all)))
            }
            println("Host folding state: " + evaluateJavascript(webView, foldingStateScript))
            waitUntil("Fold all did not reach the plugin") {
                evaluateJavascript(webView, "window.editor.session.getAllFolds().length") == "2"
            }
            runOnMainSync {
                assertTrue(EditorMenu(editorView).onOptionsItemSelected(menu.findItem(R.id.action_unfold_all)))
            }
            waitUntil("Unfold all did not reach the plugin") {
                evaluateJavascript(webView, "window.editor.session.getAllFolds().length") == "0"
            }
            runOnMainSync {
                assertEquals(sourceText, editorView.editor.textSnapshot)
                assertFalse(editorView.editor.isTextChanged)
                assertFalse(editorView.editor.canUndo)
            }
        } finally {
            activity?.let(::finishHostActivity)
            runOnMainSync {
                PluginEnableStore.setEnabled(context, EditorPluginContract.DEFAULT_PACKAGE_NAME, originalPluginEnabled)
                AceEditorPluginStateMonitor.refresh(AceEditorPluginStateMonitor.Cause.PLUGIN_STATE_CHANGED)
            }
            restoreEnginePreferences(enginePreference, selectionModePreference)
            document.delete()
        }
    }

    @Test
    fun installedAceEditorConsumesHostSnapshotForProjectDiagnosticsAndDefinitionNavigation() {
        assumeTrue("Ace editor plugin is not installed", isPluginInstalled())

        val project = File(context.cacheDir, "ace-editor-host-project-diagnostics").apply {
            deleteRecursively()
            mkdirs()
        }
        File(project, "tsconfig.json").writeText("{}")
        val sourceText = listOf(
            "import { shared } from './shared';",
            "import { absent } from './absent';",
            "const answer: 42 = shared('value');",
            "const imported: 7 = projectMagicAnswer;",
            "void absent; void answer; void imported;",
        ).joinToString("\n")
        val document = File(project, "main.ts").apply { writeText(sourceText) }
        val shared = File(project, "shared.ts").apply {
            writeText(
                "export function shared(value: string): 42 { return 42; }\n" +
                    "export const projectMagicAnswer = 7 as const;",
            )
        }
        val documentPath = document.canonicalPath
        val providerSnapshot = requireNotNull(
            TypeScriptEditorProjectSnapshotProvider.captureRequired(documentPath),
        )
        assertEquals(2, providerSnapshot.sourceFileCount)

        val ready = CountDownLatch(1)
        val fatalFailure = AtomicReference<EditorPluginFailure>()
        val definitionTarget = AtomicReference<EditorPluginDefinitionTarget>()
        val definitionRequested = CountDownLatch(1)
        val codeAction = AtomicReference<EditorPluginCurrentDocumentCodeAction>()
        val codeActionRequested = CountDownLatch(1)
        val loaded = AtomicReference<AceEditorPluginHost.LoadedSession>()
        val activity = launchHostActivity()
        try {
            runOnMainSync {
                loaded.set(
                    AceEditorPluginHost.createSession(
                        context = activity,
                        config = EditorPluginSessionConfig(
                            hostPackageName = context.packageName,
                            hostVersionName = BuildConfig.VERSION_NAME,
                            hostVersionCode = BuildConfig.VERSION_CODE.toLong(),
                            storageDirectoryPath =
                                File(context.cacheDir, "ace-editor-host-project-fonts").absolutePath,
                            documentPath = documentPath,
                            projectSnapshotProvider = TypeScriptEditorProjectSnapshotProvider,
                        ),
                        callback = object : EditorPluginCallback {
                            override fun onReady(state: org.autojs.plugin.editor.api.EditorPluginState) {
                                ready.countDown()
                            }

                            override fun onFailure(failure: EditorPluginFailure) {
                                if (failure.fatal) {
                                    fatalFailure.compareAndSet(null, failure)
                                    ready.countDown()
                                }
                            }

                            override fun onDefinitionNavigationRequested(
                                target: EditorPluginDefinitionTarget,
                            ) {
                                definitionTarget.set(target)
                                definitionRequested.countDown()
                            }

                            override fun onCurrentDocumentCodeActionRequested(
                                action: EditorPluginCurrentDocumentCodeAction,
                                onComplete: EditorPluginBooleanCallback,
                            ) {
                                codeAction.set(action)
                                val currentSession = loaded.get().session
                                val approved = EditorCurrentDocumentCodeActionAuthorizer.isAuthorized(
                                    action = action,
                                    currentText = currentSession.textSnapshot,
                                    readOnly = currentSession.isReadOnly,
                                )
                                codeActionRequested.countDown()
                                onComplete.onComplete(approved)
                            }
                        },
                    ),
                )
                activity.setContentView(loaded.get().session.view)
            }

            assertTrue(
                "Ace project diagnostic session did not become ready",
                ready.await(20, TimeUnit.SECONDS),
            )
            fatalFailure.get()?.let { failure ->
                fail("Ace editor plugin failed: ${failure.type}: ${failure.message}")
            }
            runOnMainSync {
                assertEquals(5, loaded.get().plugin.entrypoint.contractVersion)
                loaded.get().session.setInitialText(sourceText)
            }

            var diagnosticCodes = emptyList<String>()
            val deadline = SystemClock.uptimeMillis() + 30_000L
            while (SystemClock.uptimeMillis() < deadline && "2307" !in diagnosticCodes) {
                val raw = evaluateJavascript(
                    loaded.get().session.inputView as WebView,
                    "JSON.stringify(window.AutoJsAce && window.AutoJsAce.validateLsp " +
                        "? window.AutoJsAce.validateLsp() : [])",
                )
                val decoded = runCatching { JSONTokener(raw).nextValue() as? String }
                    .getOrNull().orEmpty()
                val diagnostics = runCatching { JSONArray(decoded) }.getOrElse { JSONArray() }
                diagnosticCodes = List(diagnostics.length()) { index ->
                    diagnostics.getJSONObject(index).optString("code")
                }
                if ("2307" !in diagnosticCodes) SystemClock.sleep(100L)
            }

            assertEquals(
                "Host-backed project diagnostic golden changed",
                listOf("2304", "2307"),
                diagnosticCodes.sorted(),
            )

            val navigationResult = evaluateJavascript(
                loaded.get().session.inputView as WebView,
                "Boolean(window.AutoJsAce && window.AutoJsAce.goToDefinition && " +
                    "window.AutoJsAce.goToDefinition(2, 22))",
            )
            assertEquals("true", navigationResult)
            assertTrue(
                "Ace editor plugin did not publish a definition navigation request",
                definitionRequested.await(10, TimeUnit.SECONDS),
            )
            val target = requireNotNull(definitionTarget.get())
            assertEquals(EditorPluginDefinitionTargetKind.PROJECT_SOURCE, target.kind)
            assertEquals(project.canonicalPath, target.projectRootPath)
            assertEquals("shared.ts", target.relativePath)
            assertEquals(0, target.line)
            assertEquals(16, target.column)
            assertEquals(
                providerSnapshot.sourceInventoryFingerprint,
                target.projectSourceInventoryFingerprint,
            )
            val resolved = requireNotNull(
                TypeScriptEditorDefinitionTargetResolver.resolve(documentPath, target),
            )
            assertEquals(shared.canonicalPath, resolved.targetPath)
            assertEquals(0, resolved.line)
            assertEquals(16, resolved.column)

            val quickFixResult = evaluateJavascript(
                loaded.get().session.inputView as WebView,
                "Boolean(window.AutoJsAce && window.AutoJsAce.quickFix && " +
                    "window.AutoJsAce.quickFix(3, 29))",
            )
            assertEquals("true", quickFixResult)
            assertTrue(
                "Ace editor plugin did not publish an auto-import code action",
                codeActionRequested.await(10, TimeUnit.SECONDS),
            )
            val requestedAction = requireNotNull(codeAction.get())
            assertEquals(EditorPluginCurrentDocumentCodeActionKind.AUTO_IMPORT, requestedAction.kind)
            assertEquals(2304, requestedAction.diagnosticCode)
            waitUntil("Host-approved auto-import was not applied") {
                runOnMainSync {
                    loaded.get().session.textSnapshot.lineSequence().first()
                        .contains("projectMagicAnswer")
                }
            }

            var remainingDiagnosticCodes = emptyList<String>()
            val codeFixDeadline = SystemClock.uptimeMillis() + 20_000L
            while (SystemClock.uptimeMillis() < codeFixDeadline) {
                val raw = evaluateJavascript(
                    loaded.get().session.inputView as WebView,
                    "JSON.stringify(window.AutoJsAce && window.AutoJsAce.validateLsp " +
                        "? window.AutoJsAce.validateLsp() : [])",
                )
                val decoded = runCatching { JSONTokener(raw).nextValue() as? String }
                    .getOrNull().orEmpty()
                val diagnostics = runCatching { JSONArray(decoded) }.getOrElse { JSONArray() }
                remainingDiagnosticCodes = List(diagnostics.length()) { index ->
                    diagnostics.getJSONObject(index).optString("code")
                }.sorted()
                if (remainingDiagnosticCodes == listOf("2307")) break
                SystemClock.sleep(100L)
            }
            assertEquals(
                "Auto-import should remove TS2304 while preserving the unrelated TS2307",
                listOf("2307"),
                remainingDiagnosticCodes,
            )
        } finally {
            loaded.get()?.let { loadedSession ->
                runOnMainSync { loadedSession.destroy() }
            }
            finishHostActivity(activity)
            project.deleteRecursively()
        }
    }

    @Test
    fun installedAceEditorRenamesThreeFilesThroughHostTransaction() {
        assumeTrue("Ace editor plugin is not installed", isPluginInstalled())

        val enginePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE)
        val selectionModePreference = captureStringPreference(
            EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE,
        )
        val lspEnabledPreference = captureBooleanPreference(
            AceEditorLspPreferences.KEY_ACE_LSP_ENABLED,
        )
        val lspFileTypesPreference = captureStringPreference(
            AceEditorLspPreferences.KEY_ACE_LSP_FILE_TYPES,
        )
        val lspFileTypesRevisionPreference = captureIntPreference(
            AceEditorLspPreferences.KEY_ACE_LSP_FILE_TYPES_REVISION,
        )
        val originalPluginEnabled = PluginEnableStore.isEnabled(
            context,
            EditorPluginContract.DEFAULT_PACKAGE_NAME,
            defaultEnabled = true,
        )
        val project = File(
            context.cacheDir,
            "ace-editor-host-project-rename-${SystemClock.uptimeMillis()}",
        ).apply {
            deleteRecursively()
            mkdirs()
        }
        File(project, "tsconfig.json").writeText("{\"compilerOptions\":{\"strict\":true}}")
        val mainText = listOf(
            "import { answer } from './shared';",
            "export const mainValue = answer('main');",
        ).joinToString("\n")
        val sharedText = listOf(
            "export function answer(value: string): number {",
            "    return value.length;",
            "}",
        ).joinToString("\n")
        val consumerText = listOf(
            "import { answer } from './shared';",
            "export const consumed = answer('consumer');",
        ).joinToString("\n")
        val document = File(project, "main.ts").apply { writeText(mainText) }
        val shared = File(project, "shared.ts").apply { writeText(sharedText) }
        val consumer = File(project, "consumer.ts").apply { writeText(consumerText) }
        val initialSnapshot = requireNotNull(
            TypeScriptEditorProjectSnapshotProvider.captureRequired(document.canonicalPath),
        )
        assertEquals(3, initialSnapshot.sourceFileCount)
        var activity: EditActivity? = null
        var editorView: EditorView? = null

        try {
            setPluginEnabled(true)
            assumeTrue(
                "Installed Ace editor plugin is not eligible for host loading",
                runOnMainSync { AceEditorPluginStateMonitor.current(context).available },
            )
            val selection = runOnMainSync {
                AceEditorLspPreferences.setEnabled(true)
                AceEditorLspPreferences.setFileTypes(AceEditorLspPreferences.DEFAULT_FILE_TYPES)
                EditorEnginePreferences.selectByUser(context, EditorEngineType.ACE)
            }
            assertEquals(EditorEngineType.ACE, selection.effectiveType)

            val launchedActivity = launchEditActivity(document)
            activity = launchedActivity
            val activeEditorView = runOnMainSync {
                checkNotNull(launchedActivity.findViewById<EditorView>(R.id.editor_view))
            }
            editorView = activeEditorView
            val editorLoadState = AtomicReference("not sampled")
            try {
                waitUntil(
                    message = "Host editor did not load the clean TypeScript document in Ace",
                    timeoutMillis = 25_000L,
                ) {
                    runOnMainSync {
                        val engine = activeEditorView.editor
                        editorLoadState.set(
                            "engine=${engine.engineType},ready=${engine.isReady}," +
                                "text=${engine.textSnapshot.replace("\n", "\\n")}," +
                                "dirty=${activeEditorView.isTextChanged}," +
                                "stickyDirty=${activeEditorView.saveStickyDirty}",
                        )
                        engine.engineType == EditorEngineType.ACE &&
                            engine.isReady &&
                            engine.textSnapshot == mainText &&
                            !activeEditorView.isTextChanged
                    }
                }
            } catch (error: AssertionError) {
                throw AssertionError(
                    "Host editor did not load the clean TypeScript document in Ace; " +
                        "last state: ${editorLoadState.get()}",
                    error,
                )
            }

            val webView = runOnMainSync { activeEditorView.editor.inputView as WebView }
            val renameColumn = mainText.lineSequence().elementAt(1).indexOf("answer") + 2
            assertTrue(renameColumn > 1)
            var renameInvoked = false
            val invokeDeadline = SystemClock.uptimeMillis() + 30_000L
            while (!renameInvoked && SystemClock.uptimeMillis() < invokeDeadline) {
                renameInvoked = evaluateJavascript(
                    webView,
                    "Boolean(window.AutoJsAce && window.AutoJsAce.renameSymbol && " +
                        "window.AutoJsAce.renameSymbol(1, $renameColumn))",
                ) == "true"
                if (!renameInvoked) SystemClock.sleep(100L)
            }
            val renameDiagnostics = runOnMainSync {
                val engine = activeEditorView.editor as AceScriptEditorEngine
                engine.createAceDiagnosticsSnapshot()
            }
            val renameRuntimeState = renameDiagnostics.lspRuntimeState.orEmpty()
            assertTrue(
                "Ace did not produce the host project rename candidate: $renameRuntimeState; " +
                    "documentUri=${renameDiagnostics.lspDocumentUri}; " +
                    "events=${renameDiagnostics.runtimeEventHistory}",
                renameInvoked,
            )

            setForegroundDialogTextAndConfirm("projectAnswer")
            confirmForegroundProjectRenamePreview("answer", "projectAnswer")

            val expectedMain = mainText.replace("answer", "projectAnswer")
            val expectedShared = sharedText.replace("answer", "projectAnswer")
            val expectedConsumer = consumerText.replace("answer", "projectAnswer")
            try {
                waitUntil(
                    message = "Host did not atomically publish the three-file project rename",
                    timeoutMillis = 30_000L,
                ) {
                    document.readText() == expectedMain &&
                        shared.readText() == expectedShared &&
                        consumer.readText() == expectedConsumer &&
                        runOnMainSync {
                            activeEditorView.editor.textSnapshot == expectedMain &&
                                !activeEditorView.isTextChanged &&
                                !activeEditorView.saveStickyDirty &&
                                !activeEditorView.editor.isReadOnly
                        }
                }
            } catch (error: AssertionError) {
                val editorState = runOnMainSync {
                    val diagnostics =
                        (activeEditorView.editor as AceScriptEditorEngine).createAceDiagnosticsSnapshot()
                    "text=${activeEditorView.editor.textSnapshot.replace("\n", "\\n")}," +
                        "dirty=${activeEditorView.isTextChanged}," +
                        "stickyDirty=${activeEditorView.saveStickyDirty}," +
                        "readOnly=${activeEditorView.editor.isReadOnly}," +
                        "events=${diagnostics.runtimeEventHistory}"
                }
                throw AssertionError(
                    "Host did not atomically publish the three-file project rename; " +
                        "main=${document.readText().replace("\n", "\\n")}; " +
                        "shared=${shared.readText().replace("\n", "\\n")}; " +
                        "consumer=${consumer.readText().replace("\n", "\\n")}; " +
                        "files=${project.list()?.sorted()}; editor=$editorState",
                    error,
                )
            }
            assertTrue(
                "Project rename temporary files must be removed after commit",
                project.walkTopDown().none { file ->
                    file.name.startsWith(".autojs6-rename-")
                },
            )

            val refreshedSnapshot = requireNotNull(
                TypeScriptEditorProjectSnapshotProvider.captureRequired(document.canonicalPath),
            )
            assertEquals(3, refreshedSnapshot.sourceFileCount)
            var postRenameDiagnostics = listOf("pending")
            var runtimeState = JSONObject()
            val diagnosticsDeadline = SystemClock.uptimeMillis() + 30_000L
            while (SystemClock.uptimeMillis() < diagnosticsDeadline) {
                val raw = evaluateJavascript(
                    webView,
                    "JSON.stringify(window.AutoJsAce && window.AutoJsAce.validateLsp " +
                        "? window.AutoJsAce.validateLsp() : [])",
                )
                val decoded = runCatching { JSONTokener(raw).nextValue() as? String }
                    .getOrNull().orEmpty()
                val diagnostics = runCatching { JSONArray(decoded) }.getOrElse { JSONArray() }
                postRenameDiagnostics = List(diagnostics.length()) { index ->
                    diagnostics.getJSONObject(index).optString("code")
                }.sorted()
                runtimeState = runOnMainSync {
                    val engine = activeEditorView.editor as AceScriptEditorEngine
                    runCatching {
                        JSONObject(engine.createAceDiagnosticsSnapshot().lspRuntimeState.orEmpty())
                    }.getOrElse { JSONObject() }
                }
                if (
                    postRenameDiagnostics.isEmpty() &&
                    runtimeState.optBoolean("tsProjectSnapshotReady") &&
                    runtimeState.optString("projectSourceInventoryFingerprint") ==
                    refreshedSnapshot.sourceInventoryFingerprint
                ) {
                    break
                }
                SystemClock.sleep(100L)
            }
            assertEquals(
                "Renamed three-file project should compile without TypeScript diagnostics: " +
                    runtimeState,
                emptyList<String>(),
                postRenameDiagnostics,
            )
            assertTrue(runtimeState.optBoolean("tsProjectSnapshotReady"))
            assertEquals(
                refreshedSnapshot.sourceInventoryFingerprint,
                runtimeState.optString("projectSourceInventoryFingerprint"),
            )
            assertEquals(3, runtimeState.optInt("tsLoadedProjectSourceFileCount"))
        } finally {
            editorView?.let { activeEditorView ->
                runCatching {
                    runOnMainSync {
                        activeEditorView.editor.markTextAsSaved()
                        activeEditorView.saveStickyDirty = false
                    }
                }
            }
            activity?.let(::finishHostActivity)
            runOnMainSync {
                PluginEnableStore.setEnabled(
                    context,
                    EditorPluginContract.DEFAULT_PACKAGE_NAME,
                    originalPluginEnabled,
                )
                AceEditorPluginStateMonitor.refresh(
                    AceEditorPluginStateMonitor.Cause.PLUGIN_STATE_CHANGED,
                )
            }
            restoreEnginePreferences(enginePreference, selectionModePreference)
            restoreLspPreferences(
                lspEnabledPreference,
                lspFileTypesPreference,
                lspFileTypesRevisionPreference,
            )
            project.deleteRecursively()
        }
    }

    @Test
    fun installedAceEditorRefreshesTwentyFileProjectDiagnosticsAfterSaveWithinOneSecond() {
        assumeTrue("Ace editor plugin is not installed", isPluginInstalled())

        val enginePreference = captureStringPreference(EditorEnginePreferences.KEY_EDITOR_ENGINE)
        val selectionModePreference = captureStringPreference(
            EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE,
        )
        val lspEnabledPreference = captureBooleanPreference(
            AceEditorLspPreferences.KEY_ACE_LSP_ENABLED,
        )
        val lspFileTypesPreference = captureStringPreference(
            AceEditorLspPreferences.KEY_ACE_LSP_FILE_TYPES,
        )
        val lspFileTypesRevisionPreference = captureIntPreference(
            AceEditorLspPreferences.KEY_ACE_LSP_FILE_TYPES_REVISION,
        )
        val originalPluginEnabled = PluginEnableStore.isEnabled(
            context,
            EditorPluginContract.DEFAULT_PACKAGE_NAME,
            defaultEnabled = true,
        )
        val project = File(
            context.cacheDir,
            "ace-editor-host-save-diagnostics-${SystemClock.uptimeMillis()}",
        ).apply {
            deleteRecursively()
            mkdirs()
        }
        File(project, "tsconfig.json").writeText("{\"compilerOptions\":{\"strict\":true}}")
        val mainText = listOf(
            "import { sharedValue } from './file01';",
            "export const checked: number = sharedValue;",
        ).joinToString("\n")
        val document = File(project, "main.ts").apply { writeText(mainText) }
        val shared = File(project, "file01.ts").apply {
            writeText("export const sharedValue = 42;")
        }
        (2 until 20).forEach { index ->
            val suffix = index.toString().padStart(2, '0')
            File(project, "file$suffix.ts").writeText("export const value$suffix = $index;")
        }
        val initialSnapshot = requireNotNull(
            TypeScriptEditorProjectSnapshotProvider.captureRequired(document.canonicalPath),
        )
        assertEquals(20, initialSnapshot.sourceFileCount)
        var activity: EditActivity? = null
        var editorView: EditorView? = null

        try {
            setPluginEnabled(true)
            assumeTrue(
                "Installed Ace editor plugin is not eligible for host loading",
                runOnMainSync { AceEditorPluginStateMonitor.current(context).available },
            )
            val selection = runOnMainSync {
                AceEditorLspPreferences.setEnabled(true)
                AceEditorLspPreferences.setFileTypes(AceEditorLspPreferences.DEFAULT_FILE_TYPES)
                EditorEnginePreferences.selectByUser(context, EditorEngineType.ACE)
            }
            assertEquals(EditorEngineType.ACE, selection.effectiveType)

            val launchedActivity = launchEditActivity(document)
            activity = launchedActivity
            val activeEditorView = runOnMainSync {
                checkNotNull(launchedActivity.findViewById<EditorView>(R.id.editor_view))
            }
            editorView = activeEditorView
            waitUntil(
                message = "Host editor did not load the 20-file TypeScript project in Ace",
                timeoutMillis = 25_000L,
            ) {
                runOnMainSync {
                    val engine = activeEditorView.editor
                    engine.engineType == EditorEngineType.ACE &&
                        engine.isReady &&
                        engine.textSnapshot == mainText &&
                        !activeEditorView.isTextChanged
                }
            }

            val webView = runOnMainSync { activeEditorView.editor.inputView as WebView }
            var initialRuntimeState = JSONObject()
            waitUntil(
                message = "Ace did not initialize the resident 20-file TypeScript service",
                timeoutMillis = 30_000L,
            ) {
                initialRuntimeState = readLspState(webView)
                initialRuntimeState.optJSONObject("tsService")?.optBoolean("ready") == true &&
                    initialRuntimeState.optString("projectSourceInventoryFingerprint") ==
                    initialSnapshot.sourceInventoryFingerprint &&
                    initialRuntimeState.optInt("projectSourceFileCount") == 20
            }
            initialRuntimeState = readLspState(webView)
            val initialTsState = requireNotNull(initialRuntimeState.optJSONObject("tsService")) {
                "Ace TypeScript service state was missing: $initialRuntimeState"
            }
            assertTrue(
                "Ace TypeScript service was not ready: $initialRuntimeState",
                initialTsState.optBoolean("ready"),
            )
            assertEquals(
                initialSnapshot.sourceInventoryFingerprint,
                initialRuntimeState.optString("projectSourceInventoryFingerprint"),
            )
            assertEquals(20, initialTsState.optInt("loadedProjectSourceFileCount"))
            val initialDiagnostics = validateLspDiagnosticCodes(webView)
            assertTrue(
                "Initial 20-file project should not contain TS2322: $initialDiagnostics",
                "2322" !in initialDiagnostics,
            )
            initialRuntimeState = readLspState(webView)
            val initialServiceRevision = initialRuntimeState.optLong("tsServiceInstanceRevision")
            val initialRefreshCount =
                initialRuntimeState.optLong("projectSnapshotIncrementalRefreshCount")
            val initialDiagnosticPublishCount =
                initialRuntimeState.optLong("diagnosticPublishCount")
            assertTrue(initialServiceRevision > 0L)

            shared.writeText("export const sharedValue = 'saved';")
            val siblingMutationSnapshot = requireNotNull(
                TypeScriptEditorProjectSnapshotProvider.captureRequired(document.canonicalPath),
            )
            assertEquals(20, siblingMutationSnapshot.sourceFileCount)
            assertTrue(
                "Sibling mutation must change the project inventory fingerprint",
                siblingMutationSnapshot.sourceInventoryFingerprint !=
                    initialSnapshot.sourceInventoryFingerprint,
            )

            val dirtyText = "$mainText\n"
            assertEquals(
                "true",
                evaluateJavascript(
                    webView,
                    "Boolean(window.AutoJsAce && window.AutoJsAce.setTextDirty && " +
                        "(window.AutoJsAce.setTextDirty(${JSONObject.quote(dirtyText)}, true, false), true))",
                ),
            )
            waitUntil("Ace dirty text did not reach the host editor") {
                runOnMainSync {
                    activeEditorView.editor.textSnapshot == dirtyText &&
                        activeEditorView.isTextChanged &&
                        activeEditorView.saveStickyDirty
                }
            }
            val stateBeforeSave = readLspState(webView)
            assertEquals(
                "The sibling disk mutation must remain outside the frozen snapshot before save",
                initialSnapshot.sourceInventoryFingerprint,
                stateBeforeSave.optString("projectSourceInventoryFingerprint"),
            )

            val savedText = AtomicReference<String>()
            val saveError = AtomicReference<Throwable>()
            val saveCompletedAt = AtomicReference(0L)
            val saveFinished = CountDownLatch(1)
            val saveStartedAt = SystemClock.elapsedRealtime()
            runOnMainSync {
                activeEditorView.save().subscribe({ text ->
                    savedText.set(text)
                    saveCompletedAt.set(SystemClock.elapsedRealtime())
                    saveFinished.countDown()
                }, { error ->
                    saveError.set(error)
                    saveCompletedAt.set(SystemClock.elapsedRealtime())
                    saveFinished.countDown()
                })
            }
            assertTrue(
                "Host save did not finish",
                saveFinished.await(10, TimeUnit.SECONDS),
            )
            saveError.get()?.let { error ->
                throw AssertionError("Host save failed: ${error.message}", error)
            }
            assertEquals(dirtyText, savedText.get())
            assertEquals(dirtyText, document.readText())
            val savedSnapshot = requireNotNull(
                TypeScriptEditorProjectSnapshotProvider.captureRequired(document.canonicalPath),
            )
            assertEquals(20, savedSnapshot.sourceFileCount)
            assertTrue(
                savedSnapshot.sourceInventoryFingerprint !=
                    siblingMutationSnapshot.sourceInventoryFingerprint,
            )

            var refreshedRuntimeState = JSONObject()
            var refreshedDiagnosticCodes = emptyList<String>()
            var diagnosticsObservedAt = 0L
            val diagnosticDeadline = SystemClock.elapsedRealtime() + 5_000L
            while (SystemClock.elapsedRealtime() < diagnosticDeadline) {
                refreshedRuntimeState = readLspState(webView)
                refreshedDiagnosticCodes = diagnosticCodes(refreshedRuntimeState)
                if (
                    "2322" in refreshedDiagnosticCodes &&
                    refreshedRuntimeState.optString("projectSourceInventoryFingerprint") ==
                    savedSnapshot.sourceInventoryFingerprint &&
                    refreshedRuntimeState.optString("lastDiagnosticReason") == "project-snapshot" &&
                    refreshedRuntimeState.optLong("projectSnapshotIncrementalRefreshCount") >
                    initialRefreshCount &&
                    refreshedRuntimeState.optLong("diagnosticPublishCount") >
                    initialDiagnosticPublishCount
                ) {
                    diagnosticsObservedAt = SystemClock.elapsedRealtime()
                    break
                }
                SystemClock.sleep(20L)
            }
            val saveToDiagnosticMillis = diagnosticsObservedAt - saveCompletedAt.get()
            val totalSaveLoopMillis = diagnosticsObservedAt - saveStartedAt
            assertTrue(
                "Saved 20-file project did not publish TS2322 from the refreshed snapshot: " +
                    "$refreshedRuntimeState; codes=$refreshedDiagnosticCodes",
                diagnosticsObservedAt > 0L,
            )
            assertTrue(
                "20-file diagnostics took ${saveToDiagnosticMillis} ms after save completion " +
                    "(${totalSaveLoopMillis} ms including the save): $refreshedRuntimeState",
                saveToDiagnosticMillis in 0L until 1_000L &&
                    totalSaveLoopMillis in 0L until 1_000L,
            )
            assertEquals(
                "Project save must reuse the resident TypeScript service",
                initialServiceRevision,
                refreshedRuntimeState.optLong("tsServiceInstanceRevision"),
            )
            assertEquals(450L, refreshedRuntimeState.optLong("diagnosticDebounceDelayMs"))
            assertEquals(50L, refreshedRuntimeState.optLong("projectSnapshotDiagnosticDelayMs"))
            assertTrue(refreshedRuntimeState.optLong("lastDiagnosticLatencyMs") < 1_000L)
            assertTrue(refreshedRuntimeState.optLong("lastDiagnosticDurationMs") < 1_000L)
            assertTrue(
                (refreshedRuntimeState.optJSONObject("tsService")
                    ?.optLong("projectSnapshotUpdateCount", 0L) ?: 0L) > 0L,
            )
            println(
                "T4_5_SAVE_DIAGNOSTICS_METRIC " +
                    "files=20,saveToDiagnosticMs=$saveToDiagnosticMillis," +
                    "saveAndDiagnosticMs=$totalSaveLoopMillis," +
                    "scheduledLatencyMs=${refreshedRuntimeState.optLong("lastDiagnosticLatencyMs")}," +
                    "diagnosticDurationMs=${refreshedRuntimeState.optLong("lastDiagnosticDurationMs")}," +
                    "serviceRevision=${refreshedRuntimeState.optLong("tsServiceInstanceRevision")}",
            )
        } finally {
            editorView?.let { activeEditorView ->
                runCatching {
                    runOnMainSync {
                        activeEditorView.editor.markTextAsSaved()
                        activeEditorView.saveStickyDirty = false
                    }
                }
            }
            activity?.let(::finishHostActivity)
            runOnMainSync {
                PluginEnableStore.setEnabled(
                    context,
                    EditorPluginContract.DEFAULT_PACKAGE_NAME,
                    originalPluginEnabled,
                )
                AceEditorPluginStateMonitor.refresh(
                    AceEditorPluginStateMonitor.Cause.PLUGIN_STATE_CHANGED,
                )
            }
            restoreEnginePreferences(enginePreference, selectionModePreference)
            restoreLspPreferences(
                lspEnabledPreference,
                lspFileTypesPreference,
                lspFileTypesRevisionPreference,
            )
            project.deleteRecursively()
        }
    }

    private fun launchEditActivity(sourceFile: File): EditActivity {
        val intent = Intent(context, EditActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EditorView.EXTRA_PATH, sourceFile.canonicalPath)
            .putExtra(EditorView.EXTRA_NAME, sourceFile.nameWithoutExtension)
        return (instrumentation.startActivitySync(intent) as EditActivity).also {
            instrumentation.waitForIdleSync()
        }
    }

    private fun setPluginEnabled(enabled: Boolean) {
        runOnMainSync {
            PluginEnableStore.setEnabled(
                context,
                EditorPluginContract.DEFAULT_PACKAGE_NAME,
                enabled,
            )
            AceEditorPluginStateMonitor.refresh(
                AceEditorPluginStateMonitor.Cause.PLUGIN_STATE_CHANGED,
            )
        }
        instrumentation.waitForIdleSync()
    }

    private fun setForegroundDialogTextAndConfirm(value: String) {
        val deadline = SystemClock.uptimeMillis() + DIALOG_TIMEOUT_MILLIS
        var lastTree = ""
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val nodes = accessibilityTree(root)
                lastTree = accessibilitySummary(nodes)
                val input = nodes.firstOrNull { node ->
                    node.className?.toString() == "android.widget.EditText" &&
                        node.actionList.any { action ->
                            action.id == AccessibilityNodeInfo.ACTION_SET_TEXT
                        }
                }
                val positive = findPositiveDialogButton(nodes, android.R.string.ok)
                if (input != null && positive != null) {
                    val arguments = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            value,
                        )
                    }
                    assertTrue(
                        "Unable to enter the project rename identifier",
                        input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments),
                    )
                    assertTrue(
                        "Unable to confirm the Ace project rename dialog",
                        positive.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                    )
                    SystemClock.sleep(DIALOG_SETTLE_MILLIS)
                    return
                }
            }
            SystemClock.sleep(DIALOG_POLL_MILLIS)
        }
        fail("Ace project rename dialog did not become actionable: $lastTree")
    }

    private fun confirmForegroundProjectRenamePreview(oldName: String, newName: String) {
        val title = context.getString(R.string.text_typescript_project_rename)
        val deadline = SystemClock.uptimeMillis() + DIALOG_TIMEOUT_MILLIS
        var lastTree = ""
        while (SystemClock.uptimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                val nodes = accessibilityTree(root)
                lastTree = accessibilitySummary(nodes)
                val titleVisible = nodes.any { node -> node.text?.toString() == title }
                val previewVisible = nodes.any { node ->
                    val text = node.text?.toString().orEmpty()
                    oldName in text && newName in text && "3" in text && "5" in text
                }
                val positive = findPositiveDialogButton(
                    nodes,
                    R.string.dialog_button_confirm,
                )
                if (titleVisible && previewVisible && positive != null) {
                    assertTrue(
                        "Unable to confirm the host project rename preview",
                        positive.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                    )
                    SystemClock.sleep(DIALOG_SETTLE_MILLIS)
                    return
                }
            }
            SystemClock.sleep(DIALOG_POLL_MILLIS)
        }
        fail("Host project rename preview did not become actionable: $lastTree")
    }

    private fun findPositiveDialogButton(
        nodes: List<AccessibilityNodeInfo>,
        textResource: Int,
    ): AccessibilityNodeInfo? = nodes.firstOrNull { node ->
        node.isClickable &&
            (
                node.viewIdResourceName?.substringAfterLast('/') in
                    setOf("button1", "md_buttonDefaultPositive") ||
                    node.text?.toString() == context.getString(textResource)
            )
    }

    private fun accessibilityTree(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            result += node
            repeat(node.childCount) { index ->
                node.getChild(index)?.let(pending::addLast)
            }
        }
        return result
    }

    private fun accessibilitySummary(nodes: List<AccessibilityNodeInfo>): String =
        nodes.take(80).joinToString(separator = " | ") { node ->
            "${node.className}:${node.viewIdResourceName}:${node.text}"
        }

    private fun captureStringPreference(key: String): StoredStringPreference {
        val preferences = Pref.get()
        return StoredStringPreference(
            present = preferences.contains(key),
            value = preferences.getString(key, null),
        )
    }

    private fun captureBooleanPreference(key: String): StoredBooleanPreference {
        val preferences = Pref.get()
        return StoredBooleanPreference(
            present = preferences.contains(key),
            value = preferences.getBoolean(key, false),
        )
    }

    private fun captureIntPreference(key: String): StoredIntPreference {
        val preferences = Pref.get()
        return StoredIntPreference(
            present = preferences.contains(key),
            value = preferences.getInt(key, 0),
        )
    }

    private fun restoreEnginePreferences(
        enginePreference: StoredStringPreference,
        selectionModePreference: StoredStringPreference,
    ) {
        val editor = Pref.get().edit()
        enginePreference.restore(editor, EditorEnginePreferences.KEY_EDITOR_ENGINE)
        selectionModePreference.restore(
            editor,
            EditorEnginePreferences.KEY_EDITOR_ENGINE_SELECTION_MODE,
        )
        assertTrue("Original editor preferences should be restored", editor.commit())
    }

    private fun restoreLspPreferences(
        enabled: StoredBooleanPreference,
        fileTypes: StoredStringPreference,
        fileTypesRevision: StoredIntPreference,
    ) {
        val editor = Pref.get().edit()
        enabled.restore(editor, AceEditorLspPreferences.KEY_ACE_LSP_ENABLED)
        fileTypes.restore(editor, AceEditorLspPreferences.KEY_ACE_LSP_FILE_TYPES)
        fileTypesRevision.restore(
            editor,
            AceEditorLspPreferences.KEY_ACE_LSP_FILE_TYPES_REVISION,
        )
        assertTrue("Original Ace LSP preferences should be restored", editor.commit())
    }

    private fun launchHostActivity(): Activity {
        val intent = Intent(context, LogActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return instrumentation.startActivitySync(intent).also {
            instrumentation.waitForIdleSync()
        }
    }

    private fun finishHostActivity(activity: Activity) {
        runOnMainSync {
            if (!activity.isDestroyed) {
                activity.finishAndRemoveTask()
            }
        }
        instrumentation.waitForIdleSync()
    }

    @Suppress("DEPRECATION")
    private fun isPluginInstalled(): Boolean = try {
        context.packageManager.getApplicationInfo(EditorPluginContract.DEFAULT_PACKAGE_NAME, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun waitUntil(
        message: String,
        timeoutMillis: Long = 10_000L,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) {
                return
            }
            SystemClock.sleep(50L)
        }
        fail(message)
    }

    private fun readLspState(webView: WebView): JSONObject {
        val raw = evaluateJavascript(
            webView,
            "JSON.stringify(window.AutoJsAce && window.AutoJsAce.getLspState " +
                "? window.AutoJsAce.getLspState() : {})",
        )
        val decoded = runCatching { JSONTokener(raw).nextValue() as? String }
            .getOrNull().orEmpty()
        return runCatching { JSONObject(decoded) }.getOrElse { JSONObject() }
    }

    private fun decodeJavascriptObject(raw: String): JSONObject {
        val decoded = runCatching { JSONTokener(raw).nextValue() as? String }
            .getOrNull().orEmpty()
        return JSONObject(decoded)
    }

    private fun validateLspDiagnosticCodes(webView: WebView): List<String> {
        val raw = evaluateJavascript(
            webView,
            "JSON.stringify(window.AutoJsAce && window.AutoJsAce.validateLsp " +
                "? window.AutoJsAce.validateLsp() : [])",
        )
        val decoded = runCatching { JSONTokener(raw).nextValue() as? String }
            .getOrNull().orEmpty()
        val diagnostics = runCatching { JSONArray(decoded) }.getOrElse { JSONArray() }
        return List(diagnostics.length()) { index ->
            diagnostics.getJSONObject(index).optString("code")
        }.sorted()
    }

    private fun diagnosticCodes(state: JSONObject): List<String> {
        val codes = state.optJSONArray("diagnosticCodes") ?: JSONArray()
        return List(codes.length()) { index -> codes.optString(index) }.sorted()
    }

    private fun evaluateJavascript(webView: WebView, script: String): String {
        val result = AtomicReference<String>()
        val completed = CountDownLatch(1)
        runOnMainSync {
            webView.evaluateJavascript(script) { raw ->
                result.set(raw)
                completed.countDown()
            }
        }
        assertTrue("Ace JavaScript evaluation timed out", completed.await(15, TimeUnit.SECONDS))
        return result.get().orEmpty()
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

    private data class StoredBooleanPreference(
        val present: Boolean,
        val value: Boolean,
    ) {
        fun restore(editor: SharedPreferences.Editor, key: String) {
            if (present) {
                editor.putBoolean(key, value)
            } else {
                editor.remove(key)
            }
        }
    }

    private data class StoredIntPreference(
        val present: Boolean,
        val value: Int,
    ) {
        fun restore(editor: SharedPreferences.Editor, key: String) {
            if (present) {
                editor.putInt(key, value)
            } else {
                editor.remove(key)
            }
        }
    }

    companion object {
        private const val SMOKE_TEXT = "const aceEditorPluginSmoke = true;\n"
        private const val DIALOG_TIMEOUT_MILLIS = 20_000L
        private const val DIALOG_POLL_MILLIS = 100L
        private const val DIALOG_SETTLE_MILLIS = 250L
    }
}
