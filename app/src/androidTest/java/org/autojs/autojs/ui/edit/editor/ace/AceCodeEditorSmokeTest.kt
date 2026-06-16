package org.autojs.autojs.ui.edit.editor.ace

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.ui.edit.EditActivity
import org.autojs.autojs.ui.edit.EditorView
import org.autojs.autojs.model.autocomplete.CodeCompletions
import org.autojs.autojs.ui.edit.completion.CodeCompletionBar
import org.autojs.autojs.ui.edit.keyboard.AceActionHistoryStore
import org.autojs.autojs.ui.edit.keyboard.AceShortcutPanel
import org.autojs.autojs.ui.edit.editor.ace.health.AceHealthState
import org.autojs.autojs.ui.edit.editor.ace.health.AceFailureType
import org.autojs.autojs.ui.edit.editor.engine.AceScriptEditorEngine
import org.autojs.autojs.ui.edit.editor.engine.EditorEnginePreferences
import org.autojs.autojs.ui.edit.editor.engine.EditorEngineType
import org.autojs.autojs.ui.edit.editor.loading.EditorTextLoadPolicy
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs6.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AceCodeEditorSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var activity: Activity? = null
    private val launchedEditActivities = CopyOnWriteArrayList<Activity>()

    @Before
    fun setUp() {
        resetSharedEditorState()
    }

    @After
    fun tearDown() {
        resetSharedEditorState()
    }

    private fun resetSharedEditorState() {
        AceEditorTestHooks.reset()
        AceEditorDisplayPreferences.setWordWrapEnabled(false)
        AceEditorDisplayPreferences.setLineNumbersEnabled(true)
        AceEditorLspPreferences.setEnabled(false)
        AceActionHistoryStore.clear(instrumentation.targetContext)
        EditorEnginePreferences.set(EditorEngineType.NATIVE)
        finishLaunchedActivity()
    }

    @Test
    fun loadsAceEditorAndReportsDiagnostics() {
        val editor = launchReadyEditor()

        val snapshot = runOnMainSync {
            editor.createDiagnosticsSnapshot(
                fileSizeBytes = editor.textSnapshot.length.toLong(),
                largeFilePolicy = "instrumentation_smoke",
            )
        }

        assertTrue(AceCodeEditor.hasRequiredAssets(instrumentation.targetContext))
        assertEquals("ACE", snapshot.engineType)
        assertEquals(AceHealthState.RUNNING.name, snapshot.aceHealthState)
        assertNull(snapshot.lastFailureType)
        assertNotNull(snapshot.webViewVersion)
        assertNotNull(snapshot.androidVersion)
        assertTrue((snapshot.textMirrorRevision ?: 0L) > 0L)
        assertNotNull(snapshot.lastReadyAtUptimeMillis)
        assertNotNull(snapshot.lastFirstPaintAtUptimeMillis)
        assertTrue(snapshot.runtimeEventHistory.any { it.contains("bridge_ready") })
        assertTrue(snapshot.runtimeEventHistory.any { it.contains("first_paint") })
    }

    @Test
    fun setTextAndInsertRoundTripThroughBridge() {
        val editor = launchReadyEditor()
        val initialText = "const value = 1;\nconsole.log(value);\n"
        val insertedText = "// inserted from instrumentation\n"

        runOnMainSync {
            editor.setText(initialText)
        }
        waitForEditorText(editor, initialText, "ACE setText should reach the WebView document")

        runOnMainSync {
            editor.jumpToEnd()
            editor.insert(insertedText)
        }
        val expectedText = initialText + insertedText
        waitForEditorText(editor, expectedText, "ACE insert should round-trip through the bridge")

        val snapshot = runOnMainSync { editor.createDiagnosticsSnapshot() }
        assertEquals(expectedText, runOnMainSync { editor.textSnapshot })
        assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
        assertNull(snapshot.lastFailureType)
    }

    @Test
    fun displayPreferencesApplyToAceSession() {
        AceEditorDisplayPreferences.setWordWrapEnabled(true)
        AceEditorDisplayPreferences.setLineNumbersEnabled(false)
        val editor = launchReadyEditor()

        val initialState = evaluateJsonObject(
            editor,
            """
                (function() {
                    return JSON.stringify({
                        wrap: editor.session.getUseWrapMode(),
                        showLineNumbers: editor.getOption("showLineNumbers"),
                        fixedWidthGutter: editor.getOption("fixedWidthGutter") === true,
                        hasRuntimeApi:
                            typeof window.AutoJsAce.setWordWrapEnabled === "function" &&
                            typeof window.AutoJsAce.setLineNumbersEnabled === "function"
                    });
                })();
            """.trimIndent(),
        )
        assertTrue(initialState.getBoolean("wrap"))
        assertFalse(initialState.getBoolean("showLineNumbers"))
        assertTrue(initialState.getBoolean("fixedWidthGutter"))
        assertTrue(initialState.getBoolean("hasRuntimeApi"))

        val toggledState = evaluateJsonObject(
            editor,
            """
                (function() {
                    window.AutoJsAce.setWordWrapEnabled(false);
                    window.AutoJsAce.setLineNumbersEnabled(true);
                    return JSON.stringify({
                        wrap: editor.session.getUseWrapMode(),
                        showLineNumbers: editor.getOption("showLineNumbers")
                    });
                })();
            """.trimIndent(),
        )
        assertFalse(toggledState.getBoolean("wrap"))
        assertTrue(toggledState.getBoolean("showLineNumbers"))
    }

    @Test
    fun aceShortcutPanelUsesTwoRowsAndExecutesTab() {
        val initialText = "const value = 1;\n"
        val file = createTempScriptFile("ace-shortcut-panel", initialText)
        try {
            val editorView = launchReadyEditActivity(file, initialText)
            val rows = runOnMainSync {
                val firstRow = editorView.findViewById<CodeCompletionBar>(R.id.code_completion_bar)
                    .codeCompletions
                val secondRow = editorView.findViewById<CodeCompletionBar>(R.id.symbol_bar)
                    .codeCompletions
                shortcutHints(firstRow) to shortcutHints(secondRow)
            }

            assertEquals(
                listOf("ESC", "^", "TAB", "(", ")", "/", "=", ",", ";", "\"", "'"),
                rows.first,
            )
            assertEquals(
                listOf("*", "<", "v", ">", "{", "}", "[", "]", "`", "<", ">", "-"),
                rows.second,
            )
            assertEquals(
                R.drawable.ic_star_black_48dp,
                runOnMainSync {
                    editorView.findViewById<CodeCompletionBar>(R.id.symbol_bar)
                        .codeCompletions[0]
                        .iconResId
                },
            )
            waitUntil("ACE action favorites shortcut should render as a themed star icon") {
                runOnMainSync {
                    val starCell = editorView.findViewById<CodeCompletionBar>(R.id.symbol_bar)
                        .getChildAt(0) as? TextView
                    starCell != null &&
                        starCell.text.isEmpty() &&
                        starCell.compoundDrawables[0] != null
                }
            }

            runOnMainSync {
                val secondRow = editorView.findViewById<CodeCompletionBar>(R.id.symbol_bar)
                    .codeCompletions
                editorView.onHintLongClick(secondRow, 0)
            }
            waitForEditorViewText(
                editorView,
                initialText,
                "ACE star shortcut should not mutate text when there is no last action",
            )
            assertTrue(runOnMainSync { AceActionHistoryStore.getRankedEntries(instrumentation.targetContext).isEmpty() })

            runOnMainSync {
                val firstRow = editorView.findViewById<CodeCompletionBar>(R.id.code_completion_bar)
                    .codeCompletions
                editorView.editor.jumpTo(0, 0)
                editorView.onHintClick(firstRow, 2)
            }

            waitForEditorViewText(
                editorView,
                "    $initialText",
                "ACE TAB shortcut should execute the ACE indent command",
            )
            val firstEntry = runOnMainSync {
                AceActionHistoryStore.getRankedEntries(instrumentation.targetContext).single()
            }
            assertEquals("TAB", firstEntry.label)
            assertEquals(1, firstEntry.usageCount)
            assertEquals(firstEntry.id, runOnMainSync { AceActionHistoryStore.getLastActionId(instrumentation.targetContext) })

            runOnMainSync {
                val secondRow = editorView.findViewById<CodeCompletionBar>(R.id.symbol_bar)
                    .codeCompletions
                editorView.onHintLongClick(secondRow, 0)
            }

            waitForEditorViewText(
                editorView,
                "        $initialText",
                "ACE star long-press should repeat the last editor shortcut",
            )
            val repeatedEntry = runOnMainSync {
                AceActionHistoryStore.getRankedEntries(instrumentation.targetContext).single()
            }
            assertEquals("TAB", repeatedEntry.label)
            assertEquals(2, repeatedEntry.usageCount)

            runOnMainSync {
                val firstRow = editorView.findViewById<CodeCompletionBar>(R.id.code_completion_bar)
                    .codeCompletions
                val parenAction = AceShortcutPanel.actionOf(firstRow[3])
                    ?: error("Expected ACE shortcut action for left paren")
                AceActionHistoryStore.record(instrumentation.targetContext, parenAction, now = 2_000L)
                AceActionHistoryStore.setPinned(instrumentation.targetContext, repeatedEntry.id, true, now = 3_000L)
                AceActionHistoryStore.applyFavoritesOrder(
                    instrumentation.targetContext,
                    orderedIds = listOf(parenAction.id, repeatedEntry.id),
                    pinnedCount = 1,
                )
            }
            val reorderedEntries = runOnMainSync {
                AceActionHistoryStore.getRankedEntries(instrumentation.targetContext)
            }
            assertEquals(listOf("(", "TAB"), reorderedEntries.map { it.label })
            assertTrue(reorderedEntries[0].pinned)
            assertFalse(reorderedEntries[1].pinned)

            runOnMainSync {
                AceActionHistoryStore.delete(instrumentation.targetContext, reorderedEntries[0].id)
            }
            val afterDeleteEntries = runOnMainSync {
                AceActionHistoryStore.getRankedEntries(instrumentation.targetContext)
            }
            assertEquals(listOf("TAB"), afterDeleteEntries.map { it.label })
            assertFalse(afterDeleteEntries.single().pinned)
        } finally {
            finishLaunchedActivity()
            file.delete()
        }
    }

    @Test
    fun aceLargeFileStaysOnAceEditor() {
        val largeText = buildLargeScriptText()
        val file = createTempScriptFile("ace-ux-large", largeText)
        try {
            assertTrue(file.length() >= EditorTextLoadPolicy.LARGE_FILE_THRESHOLD_BYTES)

            val editorView = startEditActivitySync(file)
            waitUntil("ACE large file should load without Native routing", timeoutMillis = LARGE_FILE_TIMEOUT_MS) {
                runOnMainSync {
                    editorView.editor.engineType == EditorEngineType.ACE &&
                        editorView.editor.isReady &&
                        !editorView.editor.isLoadingText &&
                        editorView.editor.textSnapshot.length == largeText.length &&
                        !editorView.isTextChanged &&
                        !editorView.saveStickyDirty
                }
            }
            assertEquals(largeText.take(128), runOnMainSync { editorView.editor.textSnapshot.take(128) })
            assertEquals(largeText.takeLast(128), runOnMainSync { editorView.editor.textSnapshot.takeLast(128) })

            val diagnostics = copyAceDiagnostics(editorView)
            assertTrue(diagnostics.contains("engineType=${EditorEngineType.ACE.name}"))
            assertTrue(diagnostics.contains("largeFilePolicy=auto;"))
            assertFalse(diagnostics.contains("auto-native-large-file"))
        } finally {
            finishLaunchedActivity()
            file.delete()
        }
    }

    @Test
    fun diagnosticsExportOmitsScriptText() {
        val secretIdentifier = "gateDiagnosticsSecret${SystemClock.uptimeMillis()}"
        val secretLiteral = "diagnostics-token-${SystemClock.uptimeMillis()}-do-not-export"
        val secretFunction = "diagnosticsExportSourceShouldStayPrivate"
        val initialText = """
            const $secretIdentifier = "$secretLiteral";
            function $secretFunction() {
                return $secretIdentifier;
            }
        """.trimIndent() + "\n"
        val file = createTempScriptFile("ace-gate-diagnostics-export", initialText)
        try {
            val editorView = launchReadyEditActivity(file, initialText)
            waitForAceRuntimeText(editorView, initialText, "ACE runtime text should be loaded before diagnostics export")

            val diagnostics = copyAceDiagnostics(editorView)
            assertTrue(diagnostics.startsWith("ACE diagnostics"))
            assertTrue(diagnostics.contains("engineType=${EditorEngineType.ACE.name}"))
            assertTrue(diagnostics.contains("aceHealthState=${AceHealthState.RUNNING.name}"))
            assertTrue(diagnostics.contains("fileSizeBytes="))
            listOf(secretIdentifier, secretLiteral, secretFunction).forEach { scriptText ->
                assertFalse(
                    "ACE diagnostics export should not contain script text: $scriptText",
                    diagnostics.contains(scriptText),
                )
            }
        } finally {
            finishLaunchedActivity()
            file.delete()
        }
    }

    @Test
    fun breakpointAndResizeEventsUpdateDiagnostics() {
        val editor = launchReadyEditor()

        runOnMainSync {
            editor.setText("line0\nline1\nline2\n")
        }
        waitForEditorText(editor, "line0\nline1\nline2\n", "ACE text should be ready before breakpoint smoke")

        runOnMainSync {
            editor.setBreakpoint(1, true)
            editor.ensureCursorVisible("instrumentation_smoke")
        }

        waitUntil("ACE breakpoint event should update Kotlin state") {
            runOnMainSync { editor.breakpointsSnapshot.contains(1) }
        }
        waitUntil("ACE resize should be scheduled with the smoke reason") {
            runOnMainSync {
                val snapshot = editor.createDiagnosticsSnapshot()
                snapshot.lastAceResizeScheduledReason == "instrumentation_smoke"
            }
        }
        waitUntil("ACE resizeDone should update diagnostics with the smoke reason") {
            runOnMainSync {
                val snapshot = editor.createDiagnosticsSnapshot()
                snapshot.lastAceResizeCompletedReason == "instrumentation_smoke"
            }
        }

        val snapshot = runOnMainSync { editor.createDiagnosticsSnapshot() }
        assertNull(snapshot.lastFailureType)
        assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
        assertTrue(snapshot.runtimeEventHistory.any { it.contains("resize_done") })
    }

    @Test
    fun selectedTextBrowseScrollDoesNotPullBackOnGenericResize() {
        val editor = launchReadyEditor()

        val result = waitForJsonResult(
            editor,
            """
                (function() {
                    if (window.__aceSelectionBrowseResult) {
                        return window.__aceSelectionBrowseResult;
                    }
                    if (window.__aceSelectionBrowseStarted) {
                        return "";
                    }
                    window.__aceSelectionBrowseStarted = true;

                    var lines = [];
                    for (var i = 0; i < 320; i++) {
                        var label = ("000" + i).slice(-3);
                        lines.push("line_" + label + "();");
                    }
                    var text = lines.join("\n") + "\n";
                    window.AutoJsAce.setText(text);
                    var selected = "line_005";
                    var start = text.indexOf(selected);
                    window.AutoJsAce.selectRange(start, start + selected.length);

                    setTimeout(function() {
                        var renderer = editor.renderer;
                        var lineHeight = renderer.lineHeight ||
                            (renderer.layerConfig && renderer.layerConfig.lineHeight) ||
                            16;
                        if (editor.scrollToLine) {
                            editor.scrollToLine(240, true, false, null);
                        }
                        renderer.scrollToY(lineHeight * 240);
                        setTimeout(function() {
                            var before = renderer.getFirstVisibleRow();
                            window.AutoJsAce.scheduleResize("selection_browse_smoke");
                            setTimeout(function() {
                                var state = window.AutoJsAce.getState();
                                window.__aceSelectionBrowseResult = JSON.stringify({
                                    before: before,
                                    after: renderer.getFirstVisibleRow(),
                                    selectedText: state.selectedText
                                });
                            }, 420);
                        }, 120);
                    }, 520);
                    return "";
                })();
            """.trimIndent(),
        )

        assertEquals("line_005", result.getString("selectedText"))
        assertTrue(
            "Selection should have been scrolled offscreen before resize: $result",
            result.getInt("before") >= 180,
        )
        assertTrue(
            "Generic resize should not reveal the offscreen selection again: $result",
            result.getInt("after") >= result.getInt("before") - 20,
        )
    }

    @Test
    fun aceReadOnlyModeBlocksMutationsAndDoesNotPersist() {
        val editor = launchReadyEditor()
        val initialText = "alpha();\nbeta();\ngamma();\n"

        runOnMainSync {
            editor.setText(initialText)
        }
        waitForEditorText(editor, initialText, "ACE text should be ready before read-only smoke")

        runOnMainSync {
            editor.setReadOnly(true)
            editor.jumpToEnd()
            editor.insert("kotlinInsert();\n")
            editor.deleteLine()
            editor.replaceSelection("kotlinReplace();")
            editor.undo()
            editor.redo()
            editor.clear()
        }
        waitUntil("ACE runtime should report read-only") {
            evaluateJsonObject(editor, "JSON.stringify(window.AutoJsAce.getState());")
                .getBoolean("readOnly")
        }

        val blockedState = evaluateJsonObject(
            editor,
            """
                (function() {
                    window.AutoJsAce.insert("jsInsert();\n");
                    window.AutoJsAce.insertAtLine(0, "jsInsertAtLine();\n");
                    window.AutoJsAce.deleteLine();
                    window.AutoJsAce.replaceSelection("jsReplace();");
                    window.AutoJsAce.replaceAll("alpha", "omega", false);
                    window.AutoJsAce.undo();
                    window.AutoJsAce.redo();
                    return JSON.stringify(window.AutoJsAce.getState());
                })();
            """.trimIndent(),
        )
        assertTrue(blockedState.getBoolean("readOnly"))
        waitForEditorText(editor, initialText, "ACE read-only mode should block Kotlin and JS mutations")

        runOnMainSync {
            editor.setReadOnly(false)
            editor.jumpToEnd()
            editor.insert("delta();\n")
        }
        waitForEditorText(editor, initialText + "delta();\n", "ACE should mutate again after read-only is disabled")

        runOnMainSync {
            editor.destroy()
            activity?.finish()
        }
        instrumentation.waitForIdleSync()
        activity = null

        val freshEditor = launchReadyEditor()
        val freshState = evaluateJsonObject(freshEditor, "JSON.stringify(window.AutoJsAce.getState());")
        assertFalse("ACE read-only mode must default off for a new session", freshState.getBoolean("readOnly"))
    }

    @Test
    fun acceptsStaticCompletionsThroughAcePopup() {
        val editor = launchReadyEditor(lspEnabled = false)

        val result = runCompletionAcceptSmoke(editor)
        assertTrue("Completion smoke should finish", result.getBoolean("ok"))
        assertCompletionText(result, "toast", "toast")
        assertCompletionText(result, "app.launch", "app.launch")
        assertCompletionText(result, "files.read", "files.read")
        assertCompletionText(result, "images.findImage", "images.findImage")
        assertCompletionText(result, "device.getBattery", "device.getBattery")

        val snapshot = runOnMainSync { editor.createDiagnosticsSnapshot() }
        assertEquals(AceHealthState.RUNNING.name, snapshot.aceHealthState)
        assertNull(snapshot.lastFailureType)
        assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
    }

    @Test
    fun tooltipAndLspFallbackStayStaticAndLocal() {
        val editor = launchReadyEditor(lspEnabled = false)
        runOnMainSync {
            editor.setText("app.launch(\"org.autojs.autojs6\");\n")
        }
        waitForEditorText(
            editor,
            "app.launch(\"org.autojs.autojs6\");\n",
            "ACE text should be ready before tooltip smoke",
        )

        val disabledState = evaluateJsonObject(
            editor,
            """
                (function() {
                    var shown = window.AutoJsAce.showLspTooltip(0, 5);
                    return JSON.stringify({
                        lsp: window.AutoJsAce.getLspState(),
                        hover: window.AutoJsAce.getLspHover(0, 5),
                        shown: shown,
                        tooltip: window.AutoJsAce.getTooltipState()
                    });
                })();
            """.trimIndent(),
        )
        val disabledLsp = disabledState.getJSONObject("lsp")
        assertFalse(disabledLsp.getBoolean("enabled"))
        assertEquals("disabled", disabledLsp.getString("state"))
        assertEquals("static-fallback", disabledLsp.getString("completionProvider"))
        assertEquals("static-fallback", disabledLsp.getString("hoverProvider"))
        assertFalse(disabledLsp.getBoolean("serverReady"))
        assertTrue(disabledState.getBoolean("shown"))
        assertEquals("app.launch", disabledState.getJSONObject("hover").getString("caption"))
        val tooltip = disabledState.getJSONObject("tooltip")
        assertTrue(tooltip.getBoolean("domCreated"))
        assertTrue(tooltip.getBoolean("markdownLoaded"))
        assertEquals("safe-subset", tooltip.getString("markdownRenderer"))

        runOnMainSync {
            editor.destroy()
            activity?.finish()
        }
        instrumentation.waitForIdleSync()
        activity = null

        val lspEditor = launchReadyEditor(lspEnabled = true)
        val enabledNoServer = evaluateJsonObject(
            lspEditor,
            """
                (function() {
                    return JSON.stringify(window.AutoJsAce.getLspState());
                })();
            """.trimIndent(),
        )
        assertTrue(enabledNoServer.getBoolean("enabled"))
        assertEquals("configured-no-server", enabledNoServer.getString("state"))
        assertEquals("unavailable", enabledNoServer.getString("transport"))
        assertEquals("static-completion", enabledNoServer.getString("fallback"))
        assertEquals("static-fallback", enabledNoServer.getString("completionProvider"))
        assertEquals("static-fallback", enabledNoServer.getString("hoverProvider"))
        assertFalse(enabledNoServer.getBoolean("serverReady"))
        assertFalse(enabledNoServer.getBoolean("serverAvailable"))

        val fallbackResult = runCompletionAcceptSmoke(lspEditor)
        assertTrue("LSP no-server completion smoke should finish", fallbackResult.getBoolean("ok"))
        assertCompletionText(fallbackResult, "files.read", "files.read")
        val snapshot = runOnMainSync { lspEditor.createDiagnosticsSnapshot() }
        assertEquals(AceHealthState.RUNNING.name, snapshot.aceHealthState)
        assertNull(snapshot.lastFailureType)
        assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
    }

    @Test
    fun ecmaScriptBuiltInSignatureHelpStaysStaticAndLocal() {
        val editor = launchReadyEditor(lspEnabled = false)
        val text = "Function.prototype.apply(this, args);\n"
        runOnMainSync {
            editor.setText(text)
        }
        waitForEditorText(editor, text, "ACE text should be ready before signature help smoke")

        val result = evaluateJsonObject(
            editor,
            """
                (function() {
                    var column = "Function.prototype.apply(this, ".length;
                    var shown = window.AutoJsAce.showSignatureHelp(0, column);
                    return JSON.stringify({
                        shown: shown,
                        help: window.AutoJsAce.getSignatureHelp(0, column),
                        state: window.AutoJsAce.getSignatureHelpState()
                    });
                })();
            """.trimIndent(),
        )

        assertTrue("ACE built-in signature help should show for Function.prototype.apply", result.getBoolean("shown"))
        val help = result.getJSONObject("help")
        assertEquals("Function.prototype.apply", help.getString("caption"))
        assertEquals("apply(thisArg: any, argArray?: any): any", help.getString("signature"))
        assertEquals(1, help.getInt("activeParameter"))
        val state = result.getJSONObject("state")
        assertTrue(state.getBoolean("domCreated"))
        assertTrue(state.getBoolean("visible"))
        assertEquals("apply(thisArg: any, argArray?: any): any", state.getString("signature"))
        assertEquals(1, state.getInt("activeParameter"))

        val snapshot = runOnMainSync { editor.createDiagnosticsSnapshot() }
        assertEquals(AceHealthState.RUNNING.name, snapshot.aceHealthState)
        assertNull(snapshot.lastFailureType)
        assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
    }

    @Test
    fun editActivityRotationRestoreAndEngineSwitchPreserveTextAndDirtyState() {
        val file = File(
            instrumentation.targetContext.cacheDir,
            "ace-gate7-${SystemClock.uptimeMillis()}.js",
        )
        val initialText = """
            const gate = "gate7";
            console.log(gate);
        """.trimIndent() + "\n"
        val marker = "// unsaved gate7 marker ${SystemClock.uptimeMillis()}\n"
        val expectedText = initialText + marker

        file.writeText(initialText)
        try {
            val editorView = launchReadyEditActivity(file, initialText)
            val editActivity = activity as EditActivity

            runOnMainSync {
                editorView.editor.jumpToEnd()
                editorView.editor.insert(marker)
                editorView.restoreDraftTextForThisSession(expectedText)
                editorView.editor.requestEditorFocus()
            }
            waitForEditorViewText(editorView, expectedText, "ACE EditActivity text should contain the unsaved marker")
            waitForAceRuntimeText(editorView, expectedText, "ACE runtime text should contain the unsaved marker")
            waitUntil("ACE EditActivity should keep sticky dirty state after draft restore") {
                runOnMainSync { editorView.saveStickyDirty }
            }

            assertAceCursorVisible(editorView, "ACE cursor should be visible before Gate 7 IME")
            showImeBestEffort(editorView)
            rotateAndAssertEditorState(
                editActivity,
                editorView,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                Configuration.ORIENTATION_LANDSCAPE,
                expectedText,
                expectedDirty = true,
                expectedEngineDirty = false,
                expectedStickyDirty = true,
            )
            rotateAndAssertEditorState(
                editActivity,
                editorView,
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                Configuration.ORIENTATION_PORTRAIT,
                expectedText,
                expectedDirty = true,
                expectedEngineDirty = false,
                expectedStickyDirty = true,
            )

            backgroundAndRestore(editActivity)
            waitForEditorViewState(
                editorView,
                expectedText,
                expectedDirty = true,
                message = "ACE EditActivity should preserve text and dirty state after background restore",
                expectedEngineDirty = false,
                expectedStickyDirty = true,
            )
            assertAceCursorVisible(editorView, "ACE cursor should be visible after background restore")

            val savedText = saveEditorView(editorView)
            assertEquals(expectedText, savedText)
            waitUntil("ACE EditActivity should be clean after save") {
                runOnMainSync { !editorView.isTextChanged && !editorView.saveStickyDirty }
            }
            assertEquals(expectedText, file.readText())

            switchEditorViewEngine(editorView, EditorEngineType.NATIVE, expectedText, expectedDirty = false)
            switchEditorViewEngine(editorView, EditorEngineType.ACE, expectedText, expectedDirty = false)

            val snapshot = runOnMainSync {
                (editorView.editor as AceScriptEditorEngine).createAceDiagnosticsSnapshot(
                    expectedText.length.toLong(),
                    "gate7_instrumentation",
                )
            }
            assertEquals(EditorEngineType.ACE.name, snapshot.engineType)
            assertEquals(AceHealthState.RUNNING.name, snapshot.aceHealthState)
            assertNull(snapshot.lastFailureType)
            assertFalse(snapshot.runtimeEventHistory.any { it.contains("fallback") })
        } finally {
            runCatching {
                (activity as? EditActivity)?.let { launched ->
                    runOnMainSync {
                        launched.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
            finishLaunchedActivity()
            file.delete()
        }
    }

    @Test
    fun readyTimeoutFallbackPreservesTextMirrorAndDirtyState() {
        val file = createTempScriptFile(
            "ace-gate8-ready-timeout",
            "const gate = \"gate8-ready\";\n",
        )
        val fallbackText = file.readText() + "// unsaved before ready-timeout fallback\n"

        AceEditorTestHooks.forceReadyTimeout = true
        try {
            val editorView = startEditActivity(file)
            waitUntil("Ready-timeout test should attach ACE before fallback") {
                runOnMainSync { editorView.editor.engineType == EditorEngineType.ACE }
            }
            runOnMainSync {
                editorView.restoreDraftTextForThisSession(fallbackText)
            }
            waitUntil("Ready-timeout test should update ACE TextMirror before fallback") {
                runOnMainSync {
                    editorView.editor.textSnapshot == fallbackText &&
                        editorView.saveStickyDirty
                }
            }

            waitForNativeFallback(
                editorView = editorView,
                expectedText = fallbackText,
                expectedDirty = true,
                expectedFailureType = AceFailureType.READY_TIMEOUT,
                timeoutMillis = READY_TIMEOUT_FALLBACK_TIMEOUT_MS,
            )
        } finally {
            finishLaunchedActivity()
            file.delete()
        }
    }

    @Test
    fun startupFallbackHooksReachNativeWithDiagnostics() {
        verifyStartupFallbackHook(
            filePrefix = "ace-gate8-js-init",
            initialText = "const gate = \"gate8-js-init\";\n",
            expectedFailureType = AceFailureType.JS_INIT_ERROR,
        ) {
            AceEditorTestHooks.forceJsInitThrow = true
        }
        finishLaunchedActivity()
        AceEditorTestHooks.reset()

        verifyStartupFallbackHook(
            filePrefix = "ace-gate8-renderer-gone",
            initialText = "const gate = \"gate8-renderer-gone\";\n",
            expectedFailureType = AceFailureType.RENDER_PROCESS_GONE,
        ) {
            AceEditorTestHooks.forceRendererGoneFallback = true
        }
        finishLaunchedActivity()
        AceEditorTestHooks.reset()

        verifyStartupFallbackHook(
            filePrefix = "ace-gate8-asset-missing",
            initialText = "const gate = \"gate8-asset-missing\";\n",
            expectedFailureType = AceFailureType.ASSET_MISSING,
            startsDirectlyInNative = true,
        ) {
            AceEditorTestHooks.forceAssetMissing = true
        }
    }

    private fun launchReadyEditor(lspEnabled: Boolean = false): AceCodeEditor {
        AceEditorLspPreferences.setEnabled(lspEnabled)
        val launched = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, AceEditorDebugActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        activity = launched
        instrumentation.waitForIdleSync()

        val editor = runOnMainSync {
            launched.findViewById<AceCodeEditor>(R.id.ace_editor)
        }
        assertNotNull(editor)

        waitUntil("ACE editor should report ready and first paint") {
            runOnMainSync {
                editor.isReady &&
                    editor.healthState == AceHealthState.RUNNING &&
                    editor.createDiagnosticsSnapshot().lastFirstPaintAtUptimeMillis != null
            }
        }
        return editor
    }

    private fun launchReadyEditActivity(file: File, expectedText: String): EditorView {
        val editorView = startEditActivitySync(file)
        waitUntil("EditActivity should start with ACE editor") {
            runOnMainSync {
                editorView.editor.engineType == EditorEngineType.ACE &&
                    editorView.editor.isReady
            }
        }
        waitForEditorViewText(editorView, expectedText, "EditActivity should load the test file into ACE")
        waitUntil("EditActivity should start clean after file load") {
            runOnMainSync { !editorView.isTextChanged && !editorView.saveStickyDirty }
        }
        return editorView
    }

    private fun startEditActivitySync(file: File): EditorView {
        EditorEnginePreferences.set(EditorEngineType.ACE)
        val launched = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, EditActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EditorView.EXTRA_PATH, file.absolutePath)
                .putExtra(EditorView.EXTRA_NAME, file.nameWithoutExtension),
        ) as EditActivity
        activity = launched
        launchedEditActivities.add(launched)
        instrumentation.waitForIdleSync()

        val editorView = runOnMainSync {
            launched.findViewById<EditorView>(R.id.editor_view)
        }
        assertNotNull(editorView)
        return editorView
    }

    private fun startEditActivity(file: File): EditorView {
        EditorEnginePreferences.set(EditorEngineType.ACE)
        val intent = Intent(instrumentation.targetContext, EditActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EditorView.EXTRA_PATH, file.absolutePath)
            .putExtra(EditorView.EXTRA_NAME, file.nameWithoutExtension)
        val monitor = instrumentation.addMonitor(EditActivity::class.java.name, null, false)
        try {
            instrumentation.targetContext.startActivity(intent)
            val launched = instrumentation.waitForMonitorWithTimeout(monitor, ACTIVITY_LAUNCH_TIMEOUT_MS)
                as? EditActivity
            assertNotNull("EditActivity should launch for ${file.name}", launched)
            activity = launched
            launchedEditActivities.add(launched!!)
        } finally {
            instrumentation.removeMonitor(monitor)
        }

        val editorView = runOnMainSync {
            activity!!.findViewById<EditorView>(R.id.editor_view)
        }
        assertNotNull(editorView)
        return editorView
    }

    private fun verifyStartupFallbackHook(
        filePrefix: String,
        initialText: String,
        expectedFailureType: AceFailureType,
        startsDirectlyInNative: Boolean = false,
        configureHook: () -> Unit,
    ) {
        val file = createTempScriptFile(filePrefix, initialText)
        configureHook()
        try {
            val editorView = startEditActivity(file)
            waitForNativeFallback(
                editorView = editorView,
                expectedText = initialText,
                expectedDirty = false,
                expectedFailureType = expectedFailureType,
                timeoutMillis = STARTUP_FALLBACK_TIMEOUT_MS,
                startsDirectlyInNative = startsDirectlyInNative,
            )
        } finally {
            finishLaunchedActivity()
            file.delete()
        }
    }

    private fun waitForNativeFallback(
        editorView: EditorView,
        expectedText: String,
        expectedDirty: Boolean,
        expectedFailureType: AceFailureType,
        timeoutMillis: Long,
        startsDirectlyInNative: Boolean = false,
    ) {
        waitUntil("ACE ${expectedFailureType.name} hook should end in Native", timeoutMillis = timeoutMillis) {
            runOnMainSync {
                editorView.editor.engineType == EditorEngineType.NATIVE &&
                    editorView.editor.textSnapshot == expectedText &&
                    editorView.isTextChanged == expectedDirty &&
                    editorView.saveStickyDirty == expectedDirty
            }
        }
        val diagnostics = copyAceDiagnostics(editorView)
        assertTrue(
            "${expectedFailureType.name} diagnostics should include the failure type",
            diagnostics.contains("lastFailureType=${expectedFailureType.name}"),
        )
        assertTrue(
            "${expectedFailureType.name} diagnostics should include runtime failure history",
            diagnostics.contains("runtimeEvent=") && diagnostics.contains(expectedFailureType.name),
        )
        if (!startsDirectlyInNative) {
            assertTrue(
                "${expectedFailureType.name} diagnostics should preserve the ACE engine snapshot",
                diagnostics.contains("engineType=ACE"),
            )
        }
    }

    private fun copyAceDiagnostics(editorView: EditorView): String {
        runOnMainSync {
            editorView.copyAceDiagnosticsToClipboard()
        }
        instrumentation.waitForIdleSync()
        return ClipboardUtils.getClipOrEmpty(instrumentation.targetContext).toString()
    }

    private fun finishLaunchedActivity() {
        val activities = (listOfNotNull(activity) + launchedEditActivities).distinct()
        if (activities.isNotEmpty()) {
            activities.forEach { launched ->
                hideImeForActivityBestEffort(launched)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(IME_HIDE_DELAY_MS)
            runOnMainSync {
                activities.forEach { launched ->
                    launched.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    if (!launched.isDestroyed) {
                        launched.finishAndRemoveTask()
                    }
                }
            }
            instrumentation.waitForIdleSync()
            waitForActivitiesToFinishBestEffort(activities)
            SystemClock.sleep(ACTIVITY_FINISH_DELAY_MS)
        }
        activity = null
        launchedEditActivities.clear()
    }

    private fun hideImeForActivityBestEffort(activity: Activity) {
        runCatching {
            runOnMainSync {
                val inputView = (activity as? EditActivity)
                    ?.findViewById<EditorView>(R.id.editor_view)
                    ?.editor
                    ?.inputView
                    ?: activity.currentFocus
                val inputMethodManager = instrumentation.targetContext.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
                inputView?.windowToken?.let { token ->
                    inputMethodManager.hideSoftInputFromWindow(token, 0)
                }
            }
        }
    }

    private fun waitForActivitiesToFinishBestEffort(activities: List<Activity>) {
        val deadline = SystemClock.uptimeMillis() + ACTIVITY_FINISH_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val unfinished = runCatching {
                runOnMainSync {
                    activities.any { launched -> !launched.isFinishing && !launched.isDestroyed }
                }
            }.getOrDefault(false)
            if (!unfinished) {
                return
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
    }

    private fun createTempScriptFile(prefix: String, text: String): File {
        return File(
            instrumentation.targetContext.cacheDir,
            "$prefix-${SystemClock.uptimeMillis()}.js",
        ).also { file ->
            file.writeText(text)
        }
    }

    private fun buildLargeScriptText(): String {
        val builder = StringBuilder(EditorTextLoadPolicy.LARGE_FILE_THRESHOLD_BYTES + 64 * 1024)
        var index = 0
        while (builder.length < EditorTextLoadPolicy.LARGE_FILE_THRESHOLD_BYTES + 32 * 1024) {
            builder.append("log(\"ace ux large line ")
                .append(index.toString().padStart(5, '0'))
                .append("\");\n")
            index++
        }
        return builder.toString()
    }

    private fun rotateAndAssertEditorState(
        activity: Activity,
        editorView: EditorView,
        requestedOrientation: Int,
        expectedOrientation: Int,
        expectedText: String,
        expectedDirty: Boolean,
        expectedEngineDirty: Boolean = expectedDirty,
        expectedStickyDirty: Boolean = expectedDirty,
    ) {
        runOnMainSync {
            activity.requestedOrientation = requestedOrientation
        }
        val orientationReached = runCatching {
            waitUntil("Activity should reach requested orientation $expectedOrientation") {
                runOnMainSync { activity.resources.configuration.orientation == expectedOrientation }
            }
        }.isSuccess
        if (!orientationReached) {
            assertEquals(requestedOrientation, runOnMainSync { activity.requestedOrientation })
        }
        waitForEditorViewState(
            editorView = editorView,
            expectedText = expectedText,
            expectedDirty = expectedDirty,
            message = "EditActivity should preserve text and dirty state after orientation $expectedOrientation",
            expectedEngineDirty = expectedEngineDirty,
            expectedStickyDirty = expectedStickyDirty,
        )
        if (runOnMainSync { editorView.editor.engineType == EditorEngineType.ACE }) {
            assertAceCursorVisible(editorView, "ACE cursor should be visible after orientation $expectedOrientation")
        }
    }

    private fun backgroundAndRestore(activity: EditActivity) {
        val restoreIntent = Intent(activity, EditActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        runOnMainSync {
            activity.moveTaskToBack(true)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(BACKGROUND_RESTORE_DELAY_MS)
        instrumentation.targetContext.startActivity(restoreIntent)
        instrumentation.waitForIdleSync()
        waitUntil("EditActivity should be restored from background") {
            runOnMainSync { !activity.isFinishing && !activity.isDestroyed }
        }
    }

    private fun showImeBestEffort(editorView: EditorView) {
        runOnMainSync {
            val inputView = editorView.editor.inputView
            inputView.requestFocus()
            editorView.editor.requestEditorFocus()
            val inputMethodManager = instrumentation.targetContext.getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
            inputMethodManager.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(IME_SHOW_DELAY_MS)
    }

    private fun switchEditorViewEngine(
        editorView: EditorView,
        targetType: EditorEngineType,
        expectedText: String,
        expectedDirty: Boolean,
    ) {
        runOnMainSync {
            editorView.switchEditorEngine(targetType)
        }
        waitUntil("EditActivity should switch to $targetType") {
            runOnMainSync {
                editorView.editor.engineType == targetType && editorView.editor.isReady
            }
        }
        waitForEditorViewState(
            editorView,
            expectedText,
            expectedDirty,
            "EditActivity $targetType engine should preserve text and dirty state",
        )
        if (targetType == EditorEngineType.ACE) {
            assertAceCursorVisible(editorView, "ACE cursor should be visible after switching back to ACE")
        }
    }

    private fun assertAceCursorVisible(editorView: EditorView, message: String) {
        waitUntil(message, timeoutMillis = TEXT_ROUND_TRIP_TIMEOUT_MS) {
            val webView = runOnMainSync {
                if (editorView.editor.engineType == EditorEngineType.ACE) {
                    editorView.editor.inputView as? WebView
                } else {
                    null
                }
            } ?: return@waitUntil false
            val state = evaluateJsonObject(webView, ACE_CURSOR_VISIBILITY_SCRIPT)
            state.optBoolean("visible", false)
        }
    }

    private fun waitForEditorViewState(
        editorView: EditorView,
        expectedText: String,
        expectedDirty: Boolean,
        message: String,
        expectedEngineDirty: Boolean = expectedDirty,
        expectedStickyDirty: Boolean = expectedDirty,
    ) {
        waitUntil(message, timeoutMillis = TEXT_ROUND_TRIP_TIMEOUT_MS) {
            runOnMainSync {
                editorView.editor.textSnapshot == expectedText &&
                    editorView.isTextChanged == expectedEngineDirty &&
                    editorView.saveStickyDirty == expectedStickyDirty
            }
        }
    }

    private fun waitForEditorViewText(editorView: EditorView, expected: String, message: String) {
        waitUntil(message, timeoutMillis = TEXT_ROUND_TRIP_TIMEOUT_MS) {
            runOnMainSync { editorView.editor.textSnapshot == expected }
        }
    }

    private fun waitForAceRuntimeText(editorView: EditorView, expected: String, message: String) {
        waitUntil(message, timeoutMillis = TEXT_ROUND_TRIP_TIMEOUT_MS) {
            val webView = runOnMainSync {
                if (editorView.editor.engineType == EditorEngineType.ACE) {
                    editorView.editor.inputView as? WebView
                } else {
                    null
                }
            } ?: return@waitUntil false
            evaluateJsString(
                webView,
                "window.AutoJsAce && window.AutoJsAce.getText ? window.AutoJsAce.getText() : null",
            ) == expected
        }
    }

    private fun saveEditorView(editorView: EditorView): String {
        val text = AtomicReference<String>()
        val error = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)
        runOnMainSync {
            editorView.save().subscribe({ savedText ->
                text.set(savedText)
                latch.countDown()
            }, { throwable ->
                error.set(throwable)
                latch.countDown()
            })
        }
        assertTrue("EditActivity save should finish", latch.await(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        error.get()?.let { throw AssertionError("EditActivity save should succeed: ${it.message}", it) }
        return text.get().orEmpty()
    }

    private fun runCompletionAcceptSmoke(editor: AceCodeEditor): JSONObject {
        runOnMainSync {
            editor.webView.evaluateJavascript(COMPLETION_ACCEPT_SMOKE_SCRIPT, null)
        }
        return waitForJsonResult(editor, "window.__aceGate6CompletionResult || null")
    }

    private fun assertCompletionText(result: JSONObject, name: String, expectedPrefix: String) {
        val cases = result.getJSONArray("cases")
        for (index in 0 until cases.length()) {
            val entry = cases.getJSONObject(index)
            if (entry.getString("name") == name) {
                val text = entry.getString("text")
                assertTrue(
                    "$name completion should insert $expectedPrefix, actual=$text",
                    text.startsWith(expectedPrefix),
                )
                return
            }
        }
        fail("Missing completion result for $name")
    }

    private fun shortcutHints(completions: CodeCompletions): List<String> {
        return (0 until completions.size()).map { completions.getHint(it) }
    }

    private fun waitForJsonResult(editor: AceCodeEditor, script: String): JSONObject {
        waitUntil("ACE JavaScript smoke should finish", timeoutMillis = JS_SMOKE_TIMEOUT_MS) {
            val value = evaluateJsString(editor, script)
            value.isNotBlank() && value != "null"
        }
        return evaluateJsonObject(editor, script)
    }

    private fun evaluateJsonObject(editor: AceCodeEditor, script: String): JSONObject {
        return JSONObject(evaluateJsString(editor, script))
    }

    private fun evaluateJsonObject(webView: WebView, script: String): JSONObject {
        return JSONObject(evaluateJsString(webView, script))
    }

    private fun evaluateJsString(editor: AceCodeEditor, script: String): String {
        return evaluateJsString(editor.webView, script)
    }

    private fun evaluateJsString(webView: WebView, script: String): String {
        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)
        runOnMainSync {
            webView.evaluateJavascript(script) { value ->
                result.set(value)
                latch.countDown()
            }
        }
        assertTrue("ACE JavaScript callback should return", latch.await(JS_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        val raw = result.get()
        if (raw.isNullOrBlank() || raw == "null") {
            return ""
        }
        return JSONArray("[$raw]").getString(0)
    }

    private fun waitForEditorText(editor: AceCodeEditor, expected: String, message: String) {
        waitUntil(message, timeoutMillis = TEXT_ROUND_TRIP_TIMEOUT_MS) {
            readEditorText(editor) == expected
        }
    }

    private fun readEditorText(editor: AceCodeEditor): String {
        val text = AtomicReference<String>()
        val latch = CountDownLatch(1)
        runOnMainSync {
            editor.getText { value ->
                text.set(value)
                latch.countDown()
            }
        }
        assertTrue("ACE getText callback should return", latch.await(JS_CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS))
        return text.get().orEmpty()
    }

    private fun waitUntil(
        message: String,
        timeoutMillis: Long = READY_TIMEOUT_MS,
        intervalMillis: Long = POLL_INTERVAL_MS,
        predicate: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val matched = try {
                predicate()
            } catch (error: Throwable) {
                lastError = error
                false
            }
            if (matched) {
                return
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(intervalMillis)
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

    companion object {
        private const val READY_TIMEOUT_MS = 10_000L
        private const val TEXT_ROUND_TRIP_TIMEOUT_MS = 5_000L
        private const val LARGE_FILE_TIMEOUT_MS = 20_000L
        private const val JS_SMOKE_TIMEOUT_MS = 10_000L
        private const val JS_CALLBACK_TIMEOUT_MS = 5_000L
        private const val SAVE_TIMEOUT_MS = 10_000L
        private const val STARTUP_FALLBACK_TIMEOUT_MS = 10_000L
        private const val READY_TIMEOUT_FALLBACK_TIMEOUT_MS = 12_000L
        private const val ACTIVITY_LAUNCH_TIMEOUT_MS = 45_000L
        private const val ACTIVITY_FINISH_TIMEOUT_MS = 2_000L
        private const val ACTIVITY_FINISH_DELAY_MS = 500L
        private const val BACKGROUND_RESTORE_DELAY_MS = 500L
        private const val IME_SHOW_DELAY_MS = 500L
        private const val IME_HIDE_DELAY_MS = 200L
        private const val POLL_INTERVAL_MS = 50L
        private val ACE_CURSOR_VISIBILITY_SCRIPT = """
            (function() {
                if (!window.editor || !editor.renderer || !editor.renderer.textToScreenCoordinates) {
                    return JSON.stringify({ visible: false, reason: "missing_editor" });
                }
                var renderer = editor.renderer;
                if (renderer.scrollCursorIntoView) {
                    renderer.scrollCursorIntoView();
                }
                var position = editor.getCursorPosition();
                var coords = renderer.textToScreenCoordinates(position.row, position.column);
                var rect = editor.container.getBoundingClientRect();
                var lineHeight = renderer.lineHeight ||
                    (renderer.layerConfig && renderer.layerConfig.lineHeight) ||
                    16;
                var pageX = Number(coords && coords.pageX) || 0;
                var pageY = Number(coords && coords.pageY) || 0;
                var visible =
                    pageY >= rect.top &&
                    pageY + lineHeight <= rect.bottom &&
                    pageX >= rect.left &&
                    pageX <= rect.right + 4;
                return JSON.stringify({
                    visible: visible,
                    pageX: pageX,
                    pageY: pageY,
                    lineHeight: lineHeight,
                    top: rect.top,
                    bottom: rect.bottom,
                    left: rect.left,
                    right: rect.right
                });
            })();
        """.trimIndent()
        private val COMPLETION_ACCEPT_SMOKE_SCRIPT = """
            window.__aceGate6CompletionResult = null;
            (function() {
                var cases = [
                    { name: "toast", line: "toas", value: "toast" },
                    { name: "app.launch", line: "app.la", value: "launch" },
                    { name: "files.read", line: "files.re", value: "read" },
                    { name: "images.findImage", line: "images.find", value: "findImage" },
                    { name: "device.getBattery", line: "device.getB", value: "getBattery" }
                ];
                var results = [];
                var index = 0;

                function finish(payload) {
                    window.__aceGate6CompletionResult = JSON.stringify(payload);
                }

                function fail(test, message) {
                    finish({
                        ok: false,
                        failedCase: test && test.name,
                        message: message || "unknown completion failure",
                        cases: results
                    });
                }

                function selectedMatch(completer, expectedValue) {
                    var matches = completer &&
                        completer.completions &&
                        completer.completions.filtered || [];
                    for (var i = 0; i < matches.length; i++) {
                        var item = matches[i];
                        if ((item.value || item.caption) === expectedValue) {
                            return item;
                        }
                    }
                    return null;
                }

                function next() {
                    if (index >= cases.length) {
                        finish({ ok: true, cases: results });
                        return;
                    }
                    var test = cases[index++];
                    editor.setValue(test.line, -1);
                    editor.moveCursorTo(0, test.line.length);
                    editor.focus();
                    editor.execCommand("startAutocomplete");
                    setTimeout(function() {
                        var completer = editor.completer;
                        var match = selectedMatch(completer, test.value);
                        if (!match) {
                            fail(test, "missing completion " + test.value);
                            return;
                        }
                        completer.insertMatch(match);
                        setTimeout(function() {
                            results.push({
                                name: test.name,
                                accepted: match.value || match.caption || "",
                                text: editor.session.getValue()
                            });
                            next();
                        }, 80);
                    }, 250);
                }

                try {
                    next();
                } catch (error) {
                    fail(cases[index] || null, String(error && error.message || error));
                }
            })();
            true;
        """.trimIndent()
    }
}
