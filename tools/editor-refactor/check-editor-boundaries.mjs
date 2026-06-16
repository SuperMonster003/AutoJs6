#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const UI_EDIT_ROOT = path.join(
  PROJECT_ROOT,
  "app",
  "src",
  "main",
  "java",
  "org",
  "autojs",
  "autojs",
  "ui",
  "edit",
);

function read(relativePath) {
  return fs.readFileSync(path.join(PROJECT_ROOT, relativePath), "utf8");
}

function assertIncludes(file, text, label = text) {
  const content = read(file);
  if (!content.includes(text)) {
    throw new Error(`${file}: missing ${label}`);
  }
}

function assertNotIncludes(file, text, label = text) {
  const content = read(file);
  if (content.includes(text)) {
    throw new Error(`${file}: must not contain ${label}`);
  }
}

function listSourceFiles(root) {
  const result = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    const fullPath = path.join(root, entry.name);
    if (entry.isDirectory()) {
      result.push(...listSourceFiles(fullPath));
    } else if (/\.(kt|java)$/.test(entry.name)) {
      result.push(fullPath);
    }
  }
  return result;
}

function relativeToRoot(filePath) {
  return path.relative(PROJECT_ROOT, filePath).replaceAll(path.sep, "/");
}

function assertOnlyIn(pattern, allowedFiles) {
  const allowed = new Set(allowedFiles);
  const offenders = [];
  for (const filePath of listSourceFiles(UI_EDIT_ROOT)) {
    const relativePath = relativeToRoot(filePath);
    if (fs.readFileSync(filePath, "utf8").includes(pattern) && !allowed.has(relativePath)) {
      offenders.push(relativePath);
    }
  }
  if (offenders.length) {
    throw new Error(`${pattern} is only allowed in ${[...allowed].join(", ")}; found in ${offenders.join(", ")}`);
  }
}

const editorView = "app/src/main/java/org/autojs/autojs/ui/edit/EditorView.kt";
const editActivity = "app/src/main/java/org/autojs/autojs/ui/edit/EditActivity.kt";
const editorMenu = "app/src/main/java/org/autojs/autojs/ui/edit/EditorMenu.java";
const debugToolbarFragment = "app/src/main/java/org/autojs/autojs/ui/edit/toolbar/DebugToolbarFragment.java";
const aceEditorDebugActivity = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceEditorDebugActivity.kt";
const aceEditorDebugLayout = "app/src/main/res/layout/activity_ace_editor_debug.xml";
const editorMenuXml = "app/src/main/res/menu/menu_editor.xml";
const arrays = "app/src/main/res/values/arrays.xml";
const fragmentPreferences = "app/src/main/res/xml/fragment_preferences.xml";
const scriptEngine = "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/ScriptEditorEngine.kt";
const editorEngineType = "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/EditorEngineType.kt";
const editorEngineFactory = "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/EditorEngineFactory.kt";
const nativePolicy = "app/src/main/java/org/autojs/autojs/ui/edit/editor/viewport/NativeEditorViewportPolicy.kt";
const acePolicy = "app/src/main/java/org/autojs/autojs/ui/edit/editor/viewport/AceEditorViewportPolicy.kt";
const policyFactory = "app/src/main/java/org/autojs/autojs/ui/edit/editor/viewport/EditorViewportPolicyFactory.kt";
const commandDispatcher = "app/src/main/java/org/autojs/autojs/ui/edit/editor/command/EditorCommandDispatcher.kt";
const nativeEngine = "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt";
const aceEngine = "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/AceScriptEditorEngine.kt";
const functionsKeyboardHelper = "app/src/main/java/org/autojs/autojs/ui/edit/keyboard/FunctionsKeyboardHelper.java";
const fileReadPipeline = "app/src/main/java/org/autojs/autojs/ui/edit/editor/loading/EditorFileReadPipeline.kt";
const fileService = "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/EditorFileService.kt";
const textLoadPolicy = "app/src/main/java/org/autojs/autojs/ui/edit/editor/loading/EditorTextLoadPolicy.kt";
const textLoadTarget = "app/src/main/java/org/autojs/autojs/ui/edit/editor/loading/EditorTextLoadTarget.kt";
const editorTextProvider = "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/EditorTextProvider.kt";
const runScriptTextProvider = "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/RunScriptTextProvider.kt";
const themeFontService = "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/EditorThemeFontService.kt";
const breakpointModelStore = "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/EditorBreakpointModelStore.kt";
const versionHistoryService = "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/EditorVersionHistoryService.kt";
const draftService = "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/EditorDraftService.kt";
const aceCodeEditor = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceCodeEditor.kt";
const aceImeController = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceImeController.kt";
const aceImeState = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceImeState.kt";
const aceLspServerManager = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/lsp/AceLspServerManager.kt";
const aceDiagnostics = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/diagnostics/AceDiagnostics.kt";
const aceDiagnosticsSnapshot = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/diagnostics/AceDiagnosticsSnapshot.kt";
const aceRuntimeEventHistory = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/diagnostics/AceRuntimeEventHistory.kt";
const aceJsErrorClassifier = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/health/AceJsErrorClassifier.kt";
const aceFallbackPolicy = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/health/AceFallbackPolicy.kt";
const aceLspClient = "app/src/main/assets/editor/ace-builds-1.4.12/autojs6/autojs6_lsp_client.js";
const aceTooltip = "app/src/main/assets/editor/ace-builds-1.4.12/autojs6/autojs6_tooltip.js";
const aceCiGate = "tools/ace-ci/check-ace-editor-gate.mjs";
const aceReleaseGate = "tools/ace-ci/check-ace-release-gate.mjs";
const aceSurfaceSmoke = "tools/ace-hardening/check-editor-surface.mjs";
const aceLspClientTest = "tools/ace-lsp/test-lsp-client.mjs";
const aceTooltipTest = "tools/ace-tooltip/test-tooltip.mjs";
const roadmap = "Roadmap.md";
const aceEditorTestPlan = "docs/dev/ace-editor-test-plan.md";
const aceKeyboardViewportTestPlan = "docs/dev/ace-keyboard-viewport-test-plan.md";
const aceEditorIntegrationDoc = "docs/dev/ace-editor-integration.md";
const aceReleaseGateStatus = "docs/dev/ace-release-gate-status.md";
const aceSonyReleaseReport = "docs/dev/ace-release-device-report-sony-xq-at72.md";
const acePublishingCleanup = "docs/dev/ace-release-publishing-cleanup.md";
const visualEditorAceInvestigation = "docs/dev/visual-editor-ace-investigation.md";
const nativeOnlyAudit = "docs/dev/editor-native-only-audit.md";
const engineNeutralServicesDoc = "docs/dev/editor-engine-neutral-services.md";
const viewportPolicyDoc = "docs/dev/editor-viewport-policy.md";
const strings = "app/src/main/res/values/strings.xml";
const androidWorkflow = ".github/workflows/android.yml";

assertIncludes(editorView, "mViewportPolicy.onImeInsetsChanged(imeVisible, targetBottom)");
assertIncludes(editorView, "EditorViewportPolicyFactory.create(editor, this)");
assertIncludes(editorView, "val breakpointModelStore = EditorBreakpointModelStore { editor }");
assertIncludes(editorView, "breakpointModelStore.onEditorChanged()");
assertIncludes(editorView, "EditorCommandDispatcher(");
assertIncludes(editorView, "val commandDispatcher: EditorCommandDispatcher");
assertIncludes(editorView, "mCommandDispatcher.undo()");
assertIncludes(editorView, "mCommandDispatcher.redo()");
assertIncludes(editorView, "mCommandDispatcher.replaceSelection()");
assertIncludes(editorView, "mCommandDispatcher.onCompletionClick(completions, pos)");
assertIncludes(editorView, "mCommandDispatcher.onCompletionLongClick(completions, pos)");
assertIncludes(editorView, "mCommandDispatcher.onModuleLongClick(module)");
assertIncludes(editorView, "mCommandDispatcher.onPropertyClick(m, property)");
assertIncludes(editorView, "mCommandDispatcher.onPropertyLongClick(m, property)");
assertIncludes(editorView, "private val fileService = EditorFileService(");
assertIncludes(editorView, "hostView = this");
assertIncludes(editorView, "versionHistoryService = versionHistoryService");
assertIncludes(editorView, "onSaveAsSucceeded = { dest ->");
assertIncludes(editorView, "EditorFileService.DEFAULT_CHARSET_TO_WRITE_FILE");
assertIncludes(editorView, "fileService.queryContentLengthOrNull(uri)");
assertIncludes(editorView, "fileService.readText(uri)");
assertIncludes(editorView, "fileService.saveTextTransactional(");
assertIncludes(editorView, "EditorFileSaveEncoding(");
assertIncludes(editorView, "return fileService");
assertIncludes(editorView, ".streamTextChunks(");
assertIncludes(editorView, "private fun applyReadMetadata(metadata: EditorFileReadMetadata)");
assertIncludes(editorView, "private val editorTextProvider = EditorTextProvider(this) { editor }");
assertIncludes(editorView, "private val runScriptTextProvider = RunScriptTextProvider(");
assertIncludes(editorView, "runScriptTextProvider.saveToTmpFile()");
assertIncludes(editorView, "editorTextProvider.getTextForSaving()");
assertIncludes(editorView, "private val versionHistoryService = EditorVersionHistoryService(");
assertIncludes(editorView, "versionHistoryService.showDialog(uri)");
assertIncludes(editorView, "private val themeFontService = EditorThemeFontService(");
assertIncludes(editorView, "themeFontService.applyEditorPreferences()");
assertIncludes(editorView, "themeFontService.applyTheme(theme)");
assertIncludes(editorView, "themeFontService.persistAndApplyAceFont(font)");
assertIncludes(editorView, "themeFontService.persistAndApplyTheme(it)");
assertIncludes(editorView, "private val textLoadTarget: EditorTextLoadTarget");
assertIncludes(editorView, "EditorTextLoadPolicy.shouldStreamAsLargeFile(sizeOrNull)");
assertIncludes(editorView, "EditorTextLoadPolicy.shouldLoadProgressively(text.length)");
assertIncludes(editorView, "EditorTextLoadPolicy.largeFilePolicyLabel(");
assertIncludes(editorView, "textLoadTarget.loadSmallText(text)");
assertIncludes(editorView, "lastViewportPolicyEvent = viewportPolicyEventAt?.let");
assertIncludes(editorView, "lastViewportPolicyEventAtUptimeMillis = viewportPolicyEventAt");
assertIncludes(editorView, "lastViewportPolicyImeVisible = viewportPolicyEventAt?.let");
assertIncludes(editorView, "lastViewportPolicyBottomInset = viewportPolicyEventAt?.let");
assertIncludes(editorView, "latestText = oldEditor.textSnapshot");
assertNotIncludes(editorView, "requestRectangleOnScreen");
assertNotIncludes(editorView, "CodeEditor");
assertNotIncludes(editorView, "CodeEditText");
assertNotIncludes(editorView, "LayoutHelper");
assertNotIncludes(editorView, "TextViewUndoRedo");
assertNotIncludes(editorView, ".setPadding(");
assertNotIncludes(editorView, "TextUtils");
assertNotIncludes(editorView, "LARGE_FILE_MODE_THRESHOLD");
assertNotIncludes(editorView, "editor.setLoadingText(");
assertNotIncludes(editorView, "editor.appendTextChunk(");
assertNotIncludes(editorView, "editor.beginLargeTextLoading(");
assertNotIncludes(editorView, "editor.cancelLargeTextLoading(");
assertNotIncludes(editorView, "OpenableColumns");
assertNotIncludes(editorView, "EditorFileReadPipeline(context)");
assertNotIncludes(editorView, "StableDraftFileHelper");
assertNotIncludes(editorView, "FileChooserDialogBuilder");
assertNotIncludes(editorView, "HistoryPrefs");
assertNotIncludes(editorView, "StringUtils.detectCharset");
assertNotIncludes(editorView, "StringUtils.hasBom");
assertNotIncludes(editorView, "streamDecodeAndEmit(");
assertNotIncludes(editorView, "writeTextWithCharsetTransactional");
assertNotIncludes(editorView, "handleSaveFailure");
assertNotIncludes(editorView, "EmergencyDraftStore");
assertNotIncludes(editorView, "recordSavePreIfNeeded(");
assertNotIncludes(editorView, "buildBytesToWrite");
assertNotIncludes(editorView, "targetExtFromPath");
assertNotIncludes(editorView, "buildSafeFileNameForSaveAs");
assertNotIncludes(editorView, "getTextForSavingObservable");
assertNotIncludes(editorView, "TmpScriptFiles");
assertNotIncludes(editorView, "HistoryRepository");
assertNotIncludes(editorView, "HistoryUriUtils");
assertNotIncludes(editorView, "VersionHistoryController");
assertNotIncludes(editorView, "shouldTrackHistory");
assertNotIncludes(editorView, "mEditorTheme");
assertNotIncludes(editorView, "getEditorTextSize");
assertNotIncludes(editorView, "setEditorTextSize");
assertNotIncludes(editorView, "AceEditorFontPreferences");
assertNotIncludes(editorView, "ThemeColorManager");
assertNotIncludes(editorView, "ColorUtils");

assertIncludes(editActivity, "private lateinit var draftService: EditorDraftService");
assertIncludes(editActivity, "draftService.restoreDraftForThisSession(savedInstanceState)");
assertIncludes(editActivity, "draftService.deleteDraft()");
assertIncludes(editActivity, "draftService.saveDraftToInstanceState(outState)");
assertNotIncludes(editActivity, "StableDraftFileHelper");
assertNotIncludes(editActivity, "PFiles.read");
assertNotIncludes(editActivity, "Observable.just(path)");
assertNotIncludes(editActivity, "draftFileHelper");

assertIncludes(scriptEngine, "fun onImeInsetsChanged(visible: Boolean, bottomInset: Int) = Unit");
assertIncludes(editorEngineType, "NATIVE(\"native\")");
assertIncludes(editorEngineType, "ACE(\"ace\")");
assertNotIncludes(editorEngineType, "VISUAL_ACE");
assertIncludes(editorEngineFactory, "EditorEngineType.NATIVE -> createNativeCodeEditorEngine(context)");
assertIncludes(editorEngineFactory, "EditorEngineType.ACE -> createAceOrFallback(context)");
assertNotIncludes(editorEngineFactory, "Visual");
assertNotIncludes(editorView, "entry_editor_engine_visual_ace");
assertNotIncludes(editorMenu, "action_editor_engine_visual_ace");
assertNotIncludes(editorMenuXml, "action_editor_engine_visual_ace");
assertNotIncludes(arrays, "key_editor_engine_visual_ace");
assertNotIncludes(arrays, "entry_editor_engine_visual_ace");
assertNotIncludes(fragmentPreferences, "visual_ace");
assertNotIncludes(strings, "key_editor_engine_visual_ace");
assertNotIncludes(strings, "entry_editor_engine_visual_ace");
assertIncludes(policyFactory, "EditorEngineType.NATIVE -> NativeEditorViewportPolicy(editor, hostView)");
assertIncludes(policyFactory, "EditorEngineType.ACE -> AceEditorViewportPolicy(editor)");
assertNotIncludes(policyFactory, "VISUAL_ACE");

assertIncludes(nativePolicy, "view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)");
assertIncludes(nativePolicy, "editor.ensureCursorVisibleIfPossible()");

assertIncludes(acePolicy, "view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, 0)");
assertIncludes(acePolicy, "editor.onImeInsetsChanged(visible, bottomInset)");
assertNotIncludes(acePolicy, "ensureCursorVisibleIfPossible");
assertNotIncludes(acePolicy, "requestRectangleOnScreen");

assertIncludes(commandDispatcher, "private val currentEditor: () -> ScriptEditorEngine");
assertIncludes(commandDispatcher, "private val breakpointModelStore: EditorBreakpointModelStore");
assertIncludes(commandDispatcher, "fun insert(text: CharSequence?)");
assertIncludes(commandDispatcher, "fun copyLine()");
assertIncludes(commandDispatcher, "fun deleteLine()");
assertIncludes(commandDispatcher, "fun clear()");
assertIncludes(commandDispatcher, "fun toggleComment()");
assertIncludes(commandDispatcher, "fun beautifyCode()");
assertIncludes(commandDispatcher, "fun undo()");
assertIncludes(commandDispatcher, "fun redo()");
assertIncludes(commandDispatcher, "fun replaceSelection()");
assertIncludes(commandDispatcher, "fun addOrRemoveBreakpointAtCurrentLine()");
assertIncludes(commandDispatcher, "fun removeAllBreakpoints()");
assertIncludes(commandDispatcher, "breakpointModelStore.addOrRemoveAtCurrentLine()");
assertIncludes(commandDispatcher, "breakpointModelStore.removeAll()");
assertNotIncludes(commandDispatcher, "CodeEditText");

assertIncludes(editorMenu, "commandDispatcher().addOrRemoveBreakpointAtCurrentLine()");
assertIncludes(editorMenu, "commandDispatcher().removeAllBreakpoints()");
assertIncludes(editorMenu, "commandDispatcher().jumpToStart()");
assertIncludes(editorMenu, "commandDispatcher().jumpToEnd()");
assertIncludes(editorMenu, "commandDispatcher().jumpToLineStart()");
assertIncludes(editorMenu, "commandDispatcher().jumpToLineEnd()");
assertIncludes(editorMenu, "commandDispatcher().clear()");
assertIncludes(editorMenu, "commandDispatcher().toggleComment()");
assertIncludes(editorMenu, "commandDispatcher().beautifyCode()");
assertIncludes(editorMenu, "commandDispatcher().copyLine()");
assertIncludes(editorMenu, "commandDispatcher().deleteLine()");
assertNotIncludes(editorMenu, "currentEditor().addOrRemoveBreakpointAtCurrentLine");
assertNotIncludes(editorMenu, "currentEditor().removeAllBreakpoints");
assertNotIncludes(editorMenu, "currentEditor().jumpToStart");
assertNotIncludes(editorMenu, "currentEditor().jumpToEnd");
assertNotIncludes(editorMenu, "currentEditor().jumpToLineStart");
assertNotIncludes(editorMenu, "currentEditor().jumpToLineEnd");
assertNotIncludes(editorMenu, "currentEditor().copyLine");
assertNotIncludes(editorMenu, "currentEditor().deleteLine");
assertNotIncludes(editorMenu, "currentEditor().toggleComment");
assertNotIncludes(editorMenu, "currentEditor().beautifyCode");
assertNotIncludes(editorMenu, "currentEditor().setText(\"\")");

assertIncludes(debugToolbarFragment, "mEditorView.getBreakpointModelStore().setChangeListener(mBreakpointChangeListener)");
assertIncludes(debugToolbarFragment, "mEditorView.getBreakpointModelStore().getBreakpoints()");
assertIncludes(debugToolbarFragment, "mEditorView.getBreakpointModelStore().clearChangeListener()");
assertNotIncludes(debugToolbarFragment, "editor.setBreakpointChangeListener");
assertNotIncludes(debugToolbarFragment, "mEditorView.editor.getBreakpoints()");

assertIncludes(aceEngine, "override fun createAutoCompletion(context: Context): AutoCompletion? = null");
assertNotIncludes(aceEngine, "setLoadingGutterDigits");
assertIncludes(nativeEngine, "return AutoCompletion(context, codeEditor.codeEditText)");
assertNotIncludes(functionsKeyboardHelper, "CodeEditText");
assertNotIncludes(functionsKeyboardHelper, "Editable");

assertIncludes(fileReadPipeline, "class EditorFileReadPipeline(");
assertIncludes(fileReadPipeline, "fun readText(uri: Uri): EditorFileReadResult");
assertIncludes(fileReadPipeline, "fun streamTextChunks(");
assertIncludes(fileReadPipeline, "data class EditorFileReadMetadata(");
assertIncludes(fileReadPipeline, "StringUtils.detectCharset(bytes)");
assertIncludes(fileReadPipeline, "StringUtils.hasBom(bytes, charset)");
assertNotIncludes(fileReadPipeline, "ScriptEditorEngine");
assertNotIncludes(fileReadPipeline, "CodeEditText");

assertIncludes(fileService, "class EditorFileService(");
assertIncludes(fileService, "private val hostView: View");
assertIncludes(fileService, "private val versionHistoryService: EditorVersionHistoryService");
assertIncludes(fileService, "private val onSaveAsSucceeded: (File) -> Unit");
assertIncludes(fileService, "private val fileReadPipeline = EditorFileReadPipeline(context)");
assertIncludes(fileService, "fun queryContentLengthOrNull(uri: Uri): Long?");
assertIncludes(fileService, "fun readText(uri: Uri): EditorFileReadResult");
assertIncludes(fileService, "fun streamTextChunks(");
assertIncludes(fileService, "fun saveTextTransactional(");
assertIncludes(fileService, "fileReadPipeline.queryContentLengthOrNull(uri)");
assertIncludes(fileService, "fileReadPipeline.readText(uri)");
assertIncludes(fileService, "fileReadPipeline.streamTextChunks(");
assertIncludes(fileService, "versionHistoryService.recordSavePreIfNeeded(");
assertIncludes(fileService, "StableDraftFileHelper(context, uri.path).deleteDraft()");
assertIncludes(fileService, "private fun handleSaveFailure(");
assertIncludes(fileService, "EmergencyDraftStore(context).saveDraft(");
assertIncludes(fileService, "FileChooserDialogBuilder(context)");
assertIncludes(fileService, "onSaveAsSucceeded(dest)");
assertIncludes(fileService, "data class EditorFileSaveEncoding(");
assertNotIncludes(fileService, "CodeEditText");

assertIncludes(textLoadPolicy, "const val LARGE_FILE_THRESHOLD_BYTES = 512 * 1024");
assertIncludes(textLoadPolicy, "fun shouldRouteAceToNative(");
assertIncludes(textLoadPolicy, "return engineType == EditorEngineType.ACE && forceNativeRoute");
assertIncludes(textLoadTarget, "class EngineTextLoadTarget(");
assertIncludes(textLoadTarget, "private val editor: ScriptEditorEngine");
assertNotIncludes(textLoadTarget, "CodeEditText");
assertNotIncludes(scriptEngine, "setLoadingGutterDigits");

assertIncludes(editorTextProvider, "currentEditor: () -> ScriptEditorEngine");
assertIncludes(editorTextProvider, "currentEditor().getTextForSaving");
assertNotIncludes(editorTextProvider, "CodeEditText");

assertIncludes(runScriptTextProvider, "class RunScriptTextProvider(");
assertIncludes(runScriptTextProvider, "TmpScriptFiles.create(context)");
assertIncludes(runScriptTextProvider, "write(tmpFile, latestText)");
assertNotIncludes(runScriptTextProvider, "CodeEditText");

assertIncludes(themeFontService, "class EditorThemeFontService(");
assertIncludes(themeFontService, "var currentTheme: Theme? = null");
assertIncludes(themeFontService, "fun applyTheme(theme: Theme?): Theme?");
assertIncludes(themeFontService, "fun applyEditorPreferences()");
assertIncludes(themeFontService, "AceEditorFontPreferences.set(font)");
assertIncludes(themeFontService, "Themes.setCurrent(theme.name)");
assertIncludes(themeFontService, "ThemeColorManager.colorPrimary");
assertNotIncludes(themeFontService, "CodeEditText");

assertIncludes(breakpointModelStore, "class EditorBreakpointModelStore(");
assertIncludes(breakpointModelStore, "private val currentEditor: () -> ScriptEditorEngine");
assertIncludes(breakpointModelStore, "fun getBreakpoints(): Collection<EditorBreakpoint>");
assertIncludes(breakpointModelStore, "fun setChangeListener(listener: EditorBreakpointChangeListener?)");
assertIncludes(breakpointModelStore, "fun onEditorChanged()");
assertIncludes(breakpointModelStore, "listenerOwner?.setBreakpointChangeListener(null)");
assertIncludes(breakpointModelStore, "owner.setBreakpointChangeListener(listener)");
assertNotIncludes(breakpointModelStore, "CodeEditText");

assertIncludes(versionHistoryService, "class EditorVersionHistoryService(");
assertIncludes(versionHistoryService, "private val currentEditor: () -> ScriptEditorEngine");
assertIncludes(versionHistoryService, "fun recordSavePreIfNeeded(");
assertIncludes(versionHistoryService, "HistoryUriUtils.toLogicalPathOrNull(uri)");
assertIncludes(versionHistoryService, "HistoryRepository(context.applicationContext).recordSavePre(");
assertIncludes(versionHistoryService, "VersionHistoryController(context).showForEditor(");
assertIncludes(versionHistoryService, "currentEditor().text = restoredText");
assertNotIncludes(versionHistoryService, "CodeEditText");

assertIncludes(draftService, "class EditorDraftService(");
assertIncludes(draftService, "private val currentEditor: () -> ScriptEditorEngine");
assertIncludes(draftService, "StableDraftFileHelper(context, draftKeyPath)");
assertIncludes(draftService, "fun restoreDraftForThisSession(savedInstanceState: Bundle?)");
assertIncludes(draftService, "fun saveDraftToInstanceState(outState: Bundle): Boolean");
assertIncludes(draftService, "PFiles.read(it)");
assertIncludes(draftService, "stableDraftFileHelper.saveDraft(text)");
assertIncludes(draftService, "stableDraftFileHelper.deleteDraft()");
assertNotIncludes(draftService, "CodeEditText");

assertIncludes(roadmap, "node tools/ace-hardening/check-editor-surface.mjs");
assertIncludes(roadmap, "node tools/ace-ci/check-ace-editor-gate.mjs");
assertIncludes(roadmap, "# AutoJs6 ACE Phase 9 Roadmap");
assertIncludes(roadmap, "release-candidate stabilization stage");
assertIncludes(roadmap, "## Current State");
assertIncludes(roadmap, "## Phase 9 Goal");
assertIncludes(roadmap, "## Feature Freeze");
assertIncludes(roadmap, "Server-backed LSP transport.");
assertIncludes(roadmap, "Public VisualEditorAce engine exposure.");
assertIncludes(roadmap, "Making ACE the default editor.");
assertIncludes(roadmap, "`node tools/ace-ci/check-ace-release-gate.mjs`");
assertIncludes(roadmap, "Automated/static gate:");
assertIncludes(roadmap, "Manual device gate:");
assertIncludes(roadmap, "Sony XQ-AT72");
assertIncludes(roadmap, "`docs/dev/ace-release-device-report-sony-xq-at72.md`");
assertIncludes(roadmap, "Gate 5: Mobile Selection, Clipboard, And CJK");
assertIncludes(roadmap, "Gate 8: Fallback Hooks And TextMirror");
assertIncludes(roadmap, "Gate 10: Publishing Cleanup");
assertIncludes(roadmap, "Classify the `version.properties` change as release version bump");
assertIncludes(roadmap, "## Blocker Classification");
assertIncludes(roadmap, "### P0 Blockers");
assertIncludes(roadmap, "ACE must not become the default editor.");
assertIncludes(roadmap, "ACE clean-host behavior remains the default ACE path.");
assertIncludes(roadmap, "ACE editor CI gate passes.");
assertIncludes(roadmap, "Keyboard and viewport matrix passes on representative devices.");
assertIncludes(roadmap, "Fallback preserves recent text through TextMirror in device/debug-hook");
assertIncludes(roadmap, "Server-backed LSP transport remains deferred until this gate is");
assertIncludes(roadmap, "VisualEditorAce remains deferred until this gate is satisfied.");

assertIncludes(nativeOnlyAudit, "# Editor Native-Only Audit");
assertIncludes(nativeOnlyAudit, "Native-only implementation detail");
assertIncludes(nativeOnlyAudit, "Removed from ACE path");
assertIncludes(nativeOnlyAudit, "`CodeEditor`");
assertIncludes(nativeOnlyAudit, "`CodeEditText`");
assertIncludes(nativeOnlyAudit, "`TextViewUndoRedo`");
assertIncludes(nativeOnlyAudit, "`HVScrollView`");
assertIncludes(nativeOnlyAudit, "`JavaScriptHighlighter`");
assertIncludes(nativeOnlyAudit, "`LayoutHelper`");
assertIncludes(nativeOnlyAudit, "Android `Layout`");
assertIncludes(nativeOnlyAudit, "`Editable.append`");
assertIncludes(nativeOnlyAudit, "`requestRectangleOnScreen`");
assertIncludes(nativeOnlyAudit, "`selectionStart`");
assertIncludes(nativeOnlyAudit, "`selectionEnd`");
assertIncludes(nativeOnlyAudit, "`setLoadingText`");
assertIncludes(nativeOnlyAudit, "`setLoadingGutterDigits`");
assertIncludes(nativeOnlyAudit, "`setRedoUndoEnabled`");
assertIncludes(nativeOnlyAudit, "`AutoCompletion(context, CodeEditText)`");
assertIncludes(nativeOnlyAudit, "`requestRectangleOnScreen` is allowed only in `NativeCodeEditorEngine.kt`.");
assertIncludes(nativeOnlyAudit, "`CodeEditText` is prohibited in `EditorView` and ACE code");

assertIncludes(engineNeutralServicesDoc, "# Editor Engine-Neutral Services");
assertIncludes(engineNeutralServicesDoc, "`ScriptEditorEngine`");
assertIncludes(engineNeutralServicesDoc, "`EditorViewportPolicy`");
assertIncludes(engineNeutralServicesDoc, "Forbidden Dependencies");
assertIncludes(engineNeutralServicesDoc, "File open");
assertIncludes(engineNeutralServicesDoc, "Theme/font");
assertIncludes(engineNeutralServicesDoc, "Symbol/function keyboard");
assertIncludes(engineNeutralServicesDoc, "Future Editor Core Contract");
assertIncludes(engineNeutralServicesDoc, "must not depend on");
assertIncludes(engineNeutralServicesDoc, "`CodeEditText`");
assertIncludes(engineNeutralServicesDoc, "`WebView` or `AceCodeEditor`");

assertIncludes(viewportPolicyDoc, "# Editor Viewport Policy Decision");
assertIncludes(viewportPolicyDoc, "The existing simple `EditorViewportPolicy` contract is enough");
assertIncludes(viewportPolicyDoc, "Do not add `EditorViewportHost` in this stage.");
assertIncludes(viewportPolicyDoc, "`EditorView` owns:");
assertIncludes(viewportPolicyDoc, "`NativeEditorViewportPolicy` owns:");
assertIncludes(viewportPolicyDoc, "`AceEditorViewportPolicy` owns:");
assertIncludes(viewportPolicyDoc, "Native bottom padding as an IME workaround");
assertIncludes(viewportPolicyDoc, "`requestRectangleOnScreen`");
assertIncludes(viewportPolicyDoc, "viewportPolicy=AceEditorViewportPolicy");
assertIncludes(viewportPolicyDoc, "ACE diagnostics identify `viewportPolicy=AceEditorViewportPolicy`.");

assertIncludes(aceEditorTestPlan, "## IME Show/Hide Manual Checklist");
assertIncludes(aceEditorTestPlan, "node tools/ace-ci/check-ace-editor-gate.mjs");
assertIncludes(aceEditorTestPlan, "docs/dev/ace-keyboard-viewport-test-plan.md");
assertIncludes(aceEditorTestPlan, "### ACE-IME-001: Input method and symbols/function bars");
assertIncludes(aceEditorTestPlan, "### ACE-IME-002: Repeated soft keyboard show/hide stress");
assertIncludes(aceEditorTestPlan, "### ACE-IME-003: Lower viewport tap anchor remains visible");
assertIncludes(aceEditorTestPlan, "### ACE-IME-004: Completion and selection while IME is visible");
assertIncludes(aceEditorTestPlan, "viewportPolicy=AceEditorViewportPolicy");
assertIncludes(aceEditorTestPlan, "lastAceResizeCompletedReason");
assertIncludes(aceEditorTestPlan, "heartbeatSuspendedReason");
assertIncludes(aceEditorTestPlan, "runtime event history and bridge event window counters");
assertIncludes(aceEditorTestPlan, "The isolated ACE debug page also has an `IME stress` action.");
assertIncludes(aceEditorTestPlan, "docs/dev/visual-editor-ace-investigation.md");
assertIncludes(aceEditorTestPlan, "investigation-only with no public enum, settings entry, or menu item");

assertIncludes(aceKeyboardViewportTestPlan, "# ACE Keyboard And Viewport Test Plan");
assertIncludes(aceKeyboardViewportTestPlan, "ACE-KV-001");
assertIncludes(aceKeyboardViewportTestPlan, "ACE-KV-002");
assertIncludes(aceKeyboardViewportTestPlan, "ACE-KV-003");
assertIncludes(aceKeyboardViewportTestPlan, "ACE-KV-004");
assertIncludes(aceKeyboardViewportTestPlan, "ACE-KV-005");
assertIncludes(aceKeyboardViewportTestPlan, "ACE-KV-006");
assertIncludes(aceKeyboardViewportTestPlan, "ACE-KV-007");
assertIncludes(aceKeyboardViewportTestPlan, "ACE-KV-008");
assertIncludes(aceKeyboardViewportTestPlan, "ACE-KV-009");
assertIncludes(aceKeyboardViewportTestPlan, "More -> Copy ACE diagnostics");
assertIncludes(aceKeyboardViewportTestPlan, "viewportPolicy=AceEditorViewportPolicy");
assertIncludes(aceKeyboardViewportTestPlan, "lastImeState");
assertIncludes(aceKeyboardViewportTestPlan, "lastImeHeight");
assertIncludes(aceKeyboardViewportTestPlan, "lastImeTransitionStartedAtUptimeMillis");
assertIncludes(aceKeyboardViewportTestPlan, "lastImeTransitionEndedAtUptimeMillis");
assertIncludes(aceKeyboardViewportTestPlan, "lastImeIgnoredRequestReason");
assertIncludes(aceKeyboardViewportTestPlan, "lastAceResizeScheduledReason");
assertIncludes(aceKeyboardViewportTestPlan, "lastAceResizeDispatchedReason");
assertIncludes(aceKeyboardViewportTestPlan, "lastAceResizeCompletedReason");
assertIncludes(aceKeyboardViewportTestPlan, "lastBridgeEventName");
assertIncludes(aceKeyboardViewportTestPlan, "currentBridgeEventWindowCount");
assertIncludes(aceKeyboardViewportTestPlan, "maxBridgeEventWindowCount");
assertIncludes(aceKeyboardViewportTestPlan, "runtimeEventHistory");
assertIncludes(aceKeyboardViewportTestPlan, "runtimeEvent=bridge_event_storm");
assertIncludes(aceKeyboardViewportTestPlan, "file-manager open first paint");
assertIncludes(aceKeyboardViewportTestPlan, "Sony XQ-AT72");
assertIncludes(aceKeyboardViewportTestPlan, "Top, middle, and lower tap anchors");
assertIncludes(aceKeyboardViewportTestPlan, "The isolated ACE debug activity also exposes an `IME stress` button.");
assertIncludes(aceKeyboardViewportTestPlan, "120-line sample");
assertIncludes(aceKeyboardViewportTestPlan, "programmatic show/hide cycles");
assertIncludes(aceKeyboardViewportTestPlan, "Programmatic IME toggling still does not reliably reproduce user tap anchors");
assertIncludes(aceKeyboardViewportTestPlan, "public VisualEditorAce engine exposure");
assertIncludes(aceKeyboardViewportTestPlan, "Server-backed LSP transport and public VisualEditorAce engine exposure");

assertIncludes(aceEditorIntegrationDoc, "Run the `IME Show/Hide Manual Checklist`");
assertIncludes(aceEditorIntegrationDoc, "Run `node tools/ace-ci/check-ace-release-gate.mjs`.");
assertIncludes(aceEditorIntegrationDoc, "Run `node tools/ace-ci/check-ace-editor-gate.mjs`.");
assertIncludes(aceEditorIntegrationDoc, "docs/dev/ace-keyboard-viewport-test-plan.md");
assertIncludes(aceEditorIntegrationDoc, "docs/dev/ace-release-gate-status.md");
assertIncludes(aceEditorIntegrationDoc, "docs/dev/ace-release-device-report-sony-xq-at72.md");
assertIncludes(aceEditorIntegrationDoc, "docs/dev/ace-release-publishing-cleanup.md");
assertIncludes(aceEditorIntegrationDoc, "## Clean Host Layout Decision");
assertIncludes(aceEditorIntegrationDoc, "ACE does not need a separate clean-host experiment flag at this stage.");
assertIncludes(aceEditorIntegrationDoc, "Do not add `key_ace_clean_host_layout`");
assertIncludes(aceEditorIntegrationDoc, "docs/dev/editor-viewport-policy.md");
assertIncludes(aceEditorIntegrationDoc, "runtime event history and bridge event window");
assertIncludes(aceEditorIntegrationDoc, "The isolated ACE debug activity includes an `IME stress` helper");
assertIncludes(aceEditorIntegrationDoc, "do not treat it as a replacement for physical tap-anchor and CJK validation");
assertIncludes(aceEditorIntegrationDoc, "docs/dev/visual-editor-ace-investigation.md");
assertIncludes(aceEditorIntegrationDoc, "Server-backed LSP transport and public VisualEditorAce engine exposure are");

assertIncludes(aceReleaseGateStatus, "# ACE Release Gate Status");
assertIncludes(aceReleaseGateStatus, "node tools/ace-ci/check-ace-release-gate.mjs");
assertIncludes(aceReleaseGateStatus, "Phase 9 is the active release-candidate stabilization phase.");
assertIncludes(aceReleaseGateStatus, "docs/dev/ace-release-device-report-sony-xq-at72.md");
assertIncludes(aceReleaseGateStatus, "docs/dev/ace-release-publishing-cleanup.md");
assertIncludes(aceReleaseGateStatus, "## Automated Gate");
assertIncludes(aceReleaseGateStatus, "## Manual Gate");
assertIncludes(aceReleaseGateStatus, "## Feature Freeze");
assertIncludes(aceReleaseGateStatus, "Sony XQ-AT72");
assertIncludes(aceReleaseGateStatus, "Server-backed LSP transport remains unavailable");
assertIncludes(aceReleaseGateStatus, "Public `VisualEditorAce` exposure remains deferred");
assertIncludes(aceReleaseGateStatus, "Do not expand the ACE feature surface during this gate.");

assertIncludes(aceSonyReleaseReport, "# ACE Release Device Report - Sony XQ-AT72");
assertIncludes(aceSonyReleaseReport, "## Device");
assertIncludes(aceSonyReleaseReport, "Android System WebView version");
assertIncludes(aceSonyReleaseReport, "Repository baseline commit");
assertIncludes(aceSonyReleaseReport, "LSP enabled");
assertIncludes(aceSonyReleaseReport, "Clean host enabled: yes");
assertIncludes(aceSonyReleaseReport, "## Automated Gate");
assertIncludes(aceSonyReleaseReport, "`node tools/ace-ci/check-ace-release-gate.mjs`");
assertIncludes(aceSonyReleaseReport, "## Manual Gate Summary");
assertIncludes(aceSonyReleaseReport, "ACE default");
assertIncludes(aceSonyReleaseReport, "LSP default-off");
assertIncludes(aceSonyReleaseReport, "VisualEditorAce unavailable");
assertIncludes(aceSonyReleaseReport, "TextMirror fallback");
assertIncludes(aceSonyReleaseReport, "Renderer-gone fallback");
assertIncludes(aceSonyReleaseReport, "Publishing cleanup audit");
assertIncludes(aceSonyReleaseReport, "## Final Decision");
assertIncludes(aceSonyReleaseReport, "Release candidate accepted");
assertIncludes(aceSonyReleaseReport, "Release candidate rejected");

assertIncludes(acePublishingCleanup, "# ACE Release Publishing Cleanup");
assertIncludes(acePublishingCleanup, "defaultConfig.applicationId");
assertIncludes(acePublishingCleanup, "The temporary application id and app label affect all `app` variants");
assertIncludes(acePublishingCleanup, "Restore `defaultConfig.applicationId` to `globalApplicationId`");
assertIncludes(acePublishingCleanup, "`BUILD_TIME`");
assertIncludes(acePublishingCleanup, "`VERSION_BUILD`");
assertIncludes(acePublishingCleanup, "build-generated churn");
assertIncludes(acePublishingCleanup, "ACE is now the static default editor");
assertIncludes(acePublishingCleanup, "LSP remains default-off");
assertIncludes(acePublishingCleanup, "VisualEditorAce has no public enum");

assertIncludes(visualEditorAceInvestigation, "# VisualEditorAce Investigation");
assertIncludes(visualEditorAceInvestigation, "No `VisualEditorAce` or `VISUAL_ACE` implementation exists");
assertIncludes(visualEditorAceInvestigation, "`EditorEngineType` exposes only `NATIVE` and ordinary `ACE`.");
assertIncludes(visualEditorAceInvestigation, "Keep `VisualEditorAce` deferred.");
assertIncludes(visualEditorAceInvestigation, "Do not add a `VISUAL_ACE` enum value");
assertIncludes(visualEditorAceInvestigation, "`EditorEngineType.VISUAL_ACE` with a stable preference value");
assertIncludes(visualEditorAceInvestigation, "Do not add `key_editor_engine_visual_ace`");
assertIncludes(visualEditorAceInvestigation, "Do not add `action_editor_engine_visual_ace`");

assertIncludes(aceEditorDebugActivity, "binding.actionImeStress.setOnClickListener");
assertIncludes(aceEditorDebugActivity, "private fun startImeStress()");
assertIncludes(aceEditorDebugActivity, "private fun runImeStressStep()");
assertIncludes(aceEditorDebugActivity, "private fun finishImeStress()");
assertIncludes(aceEditorDebugActivity, "IME_STRESS_CYCLES = 20");
assertIncludes(aceEditorDebugActivity, "binding.aceEditor.showSoftInput()");
assertIncludes(aceEditorDebugActivity, "binding.aceEditor.hideSoftInput()");
assertIncludes(aceEditorDebugActivity, "binding.aceEditor.ensureCursorVisible(\"debug_ime_stress_done\")");
assertIncludes(aceEditorDebugLayout, "android:id=\"@+id/action_ime_stress\"");
assertIncludes(aceEditorDebugLayout, "@string/text_ace_ime_stress");
assertIncludes(strings, "text_ace_ime_stress");
assertIncludes(strings, "text_ace_ime_stress_running");
assertIncludes(strings, "text_ace_ime_stress_done");

assertIncludes(aceCiGate, "Editor boundary checks");
assertIncludes(aceCiGate, "tools/editor-refactor/check-editor-boundaries.mjs");
assertIncludes(aceCiGate, "ACE surface hardening checks");
assertIncludes(aceCiGate, "tools/ace-hardening/check-editor-surface.mjs");
assertIncludes(aceCiGate, "AutoJs6 API model verification");
assertIncludes(aceCiGate, "tools/ace-completion/verify-autojs6-api-model.mjs");
assertIncludes(aceCiGate, "AutoJs6 Pro-only API detection");
assertIncludes(aceCiGate, "tools/ace-completion/verify-autojs6-pro-only.mjs");
assertIncludes(aceCiGate, "AutoJs6 completion index verification");
assertIncludes(aceCiGate, "tools/ace-completion/verify-autojs6-indices.mjs");
assertIncludes(aceCiGate, "AutoJs6 generated d.ts verification");
assertIncludes(aceCiGate, "tools/ace-completion/verify-autojs6-dts.mjs");
assertIncludes(aceCiGate, "ACE completer tests");
assertIncludes(aceCiGate, "tools/ace-completion/test-completer.mjs");
assertIncludes(aceCiGate, "ACE LSP client tests");
assertIncludes(aceCiGate, "tools/ace-lsp/test-lsp-client.mjs");
assertIncludes(aceCiGate, "ACE tooltip tests");
assertIncludes(aceCiGate, "tools/ace-tooltip/test-tooltip.mjs");
assertIncludes(aceCiGate, "ACE editor CI gate passed.");

assertIncludes(aceReleaseGate, "check-ace-editor-gate.mjs");
assertIncludes(aceReleaseGate, "docs/dev/ace-release-device-report-sony-xq-at72.md");
assertIncludes(aceReleaseGate, "# AutoJs6 ACE Phase 9 Roadmap");
assertIncludes(aceReleaseGate, "## Feature Freeze");
assertIncludes(aceReleaseGate, "# ACE Release Device Report - Sony XQ-AT72");
assertIncludes(aceReleaseGate, "ACE editor resource default");
assertIncludes(aceReleaseGate, "factory default to ACE");
assertIncludes(aceReleaseGate, "code editor preference order");
assertIncludes(aceReleaseGate, "Simplified Chinese ACE word wrap string");
assertIncludes(aceReleaseGate, "EditorEngineType.ACE -> AceEditorViewportPolicy(editor)");
assertIncludes(aceReleaseGate, "key_ace_clean_host_layout");
assertIncludes(aceReleaseGate, "ACE fallback switches to Native with the latest TextMirror-backed snapshot");
assertIncludes(aceReleaseGate, "forceRendererGoneFallback");
assertIncludes(aceReleaseGate, "forceJsInitThrow");
assertIncludes(aceReleaseGate, "serverAvailable = false");
assertIncludes(aceReleaseGate, "WebSocket");
assertIncludes(aceReleaseGate, "VISUAL_ACE");
assertIncludes(aceReleaseGate, "Sony XQ-AT72");
assertIncludes(aceReleaseGate, "ACE release gate automated checks passed.");

assertIncludes(androidWorkflow, "Set up Node.js 24");
assertIncludes(androidWorkflow, "actions/setup-node@v4");
assertIncludes(androidWorkflow, "node-version: '24'");
assertIncludes(androidWorkflow, "Run ACE editor gate");
assertIncludes(androidWorkflow, "node tools/ace-ci/check-ace-editor-gate.mjs");

assertIncludes(aceSurfaceSmoke, "ACE editor bootstrap order");
assertIncludes(aceSurfaceSmoke, "hidden editor before first paint");
assertIncludes(aceSurfaceSmoke, "visible editor after first paint");
assertIncludes(aceSurfaceSmoke, "double requestAnimationFrame first-paint guard");
assertIncludes(aceSurfaceSmoke, "resizeDone reason payload");
assertIncludes(aceSurfaceSmoke, "gutter breakpoint tap zone");
assertIncludes(aceSurfaceSmoke, "native first-paint request hook");
assertIncludes(aceSurfaceSmoke, "autojs6_lsp_client.js");
assertIncludes(aceSurfaceSmoke, "autojs6_tooltip.js");
assertIncludes(aceSurfaceSmoke, "LSP client must stay off WebSocket and worker transports");
assertIncludes(aceSurfaceSmoke, "Tooltip must keep markdown local and escaped");

assertIncludes(aceDiagnosticsSnapshot, "val pendingAceResizeReason: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastAceResizeScheduledReason: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastAceResizeDispatchedReason: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastAceResizeCompletedReason: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastViewportPolicyEvent: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastViewportPolicyImeVisible: Boolean?");
assertIncludes(aceDiagnosticsSnapshot, "val lastImeState: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastImeHeight: Int?");
assertIncludes(aceDiagnosticsSnapshot, "val lastImeTransitionStartedAtUptimeMillis: Long?");
assertIncludes(aceDiagnosticsSnapshot, "val lastImeTransitionEndedAtUptimeMillis: Long?");
assertIncludes(aceDiagnosticsSnapshot, "val lastImeIgnoredRequestReason: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastBridgeEventName: String?");
assertIncludes(aceDiagnosticsSnapshot, "val currentBridgeEventWindowCount: Int");
assertIncludes(aceDiagnosticsSnapshot, "val lastBridgeEventWindowCount: Int");
assertIncludes(aceDiagnosticsSnapshot, "val maxBridgeEventWindowCount: Int");
assertIncludes(aceDiagnosticsSnapshot, "val maxBridgeEventWindowStartedAtUptimeMillis: Long?");
assertIncludes(aceDiagnosticsSnapshot, "val runtimeEventHistory: List<String>");
assertIncludes(aceDiagnosticsSnapshot, "val lspTransport: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lspServerUri: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lspRootUri: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lspDocumentUri: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lspFallback: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lspServerAvailable: Boolean");
assertIncludes(aceDiagnosticsSnapshot, "val lspStartSupported: Boolean");

assertIncludes(aceDiagnostics, "AceLspServerManager.preferenceSnapshot()");
assertIncludes(aceDiagnostics, "lspTransport=${snapshot.lspTransport.orUnknown()}");
assertIncludes(aceDiagnostics, "lspServerUri=${snapshot.lspServerUri.orUnknown()}");
assertIncludes(aceDiagnostics, "lspRootUri=${snapshot.lspRootUri.orUnknown()}");
assertIncludes(aceDiagnostics, "lspDocumentUri=${snapshot.lspDocumentUri.orUnknown()}");
assertIncludes(aceDiagnostics, "lspFallback=${snapshot.lspFallback.orUnknown()}");
assertIncludes(aceDiagnostics, "viewportPolicy=${snapshot.viewportPolicy.orUnknown()}");
assertIncludes(aceDiagnostics, "lastImeState=${snapshot.lastImeState.orUnknown()}");
assertIncludes(aceDiagnostics, "lastImeHeight=${snapshot.lastImeHeight?.toString().orUnknown()}");
assertIncludes(aceDiagnostics, "lastImeTransitionStartedAtUptimeMillis=${snapshot.lastImeTransitionStartedAtUptimeMillis?.toString().orUnknown()}");
assertIncludes(aceDiagnostics, "lastImeTransitionEndedAtUptimeMillis=${snapshot.lastImeTransitionEndedAtUptimeMillis?.toString().orUnknown()}");
assertIncludes(aceDiagnostics, "lastImeIgnoredRequestReason=${snapshot.lastImeIgnoredRequestReason.orUnknown()}");
assertIncludes(aceDiagnostics, "lastBridgeEventName=${snapshot.lastBridgeEventName.orUnknown()}");
assertIncludes(aceDiagnostics, "currentBridgeEventWindowCount=${snapshot.currentBridgeEventWindowCount}");
assertIncludes(aceDiagnostics, "maxBridgeEventWindowCount=${snapshot.maxBridgeEventWindowCount}");
assertIncludes(aceDiagnostics, "runtimeEventHistory=${snapshot.runtimeEventHistory.size}");
assertIncludes(aceDiagnostics, "runtimeEvent=$it");
assertIncludes(aceDiagnostics, "pendingAceResizeReason=${snapshot.pendingAceResizeReason.orUnknown()}");
assertIncludes(aceDiagnostics, "lastAceResizeScheduledReason=${snapshot.lastAceResizeScheduledReason.orUnknown()}");
assertIncludes(aceDiagnostics, "lastAceResizeDispatchedReason=${snapshot.lastAceResizeDispatchedReason.orUnknown()}");
assertIncludes(aceDiagnostics, "lastAceResizeCompletedReason=${snapshot.lastAceResizeCompletedReason.orUnknown()}");
assertIncludes(aceDiagnostics, "lastViewportPolicyEvent=${snapshot.lastViewportPolicyEvent.orUnknown()}");
assertIncludes(aceDiagnostics, "lastViewportPolicyImeVisible=${snapshot.lastViewportPolicyImeVisible?.toString().orUnknown()}");

assertIncludes(aceCodeEditor, "private var lastAceResizeScheduledReason");
assertIncludes(aceCodeEditor, "private val imeController = AceImeController(IME_REQUEST_COALESCE_MS)");
assertIncludes(aceCodeEditor, "private val eventHistory = AceRuntimeEventHistory()");
assertIncludes(aceCodeEditor, "private val lspServerManager = AceLspServerManager()");
assertIncludes(aceCodeEditor, "lspServerManager.attach()");
assertIncludes(aceCodeEditor, "lspServerManager.detach()");
assertIncludes(aceCodeEditor, "lspServerManager.bridgeOptionsJson()");
assertIncludes(aceCodeEditor, "eventHistory.record(\"lsp_manager\"");
assertIncludes(aceCodeEditor, "lastImeState = imeController.state.name");
assertIncludes(aceCodeEditor, "lastImeIgnoredRequestReason = imeController.lastIgnoredRequestReason");
assertIncludes(aceCodeEditor, "runtimeEventHistory = eventHistory.snapshot()");
assertIncludes(aceCodeEditor, "lastBridgeEventName = lastBridgeEventName");
assertIncludes(aceCodeEditor, "currentBridgeEventWindowCount = bridgeEventWindowCount");
assertIncludes(aceCodeEditor, "maxBridgeEventWindowCount = maxBridgeEventWindowCount");
assertIncludes(aceCodeEditor, "private fun recordBridgeEvent(name: String)");
assertIncludes(aceCodeEditor, "BRIDGE_EVENT_WINDOW_MS = 1_000L");
assertIncludes(aceCodeEditor, "BRIDGE_EVENT_STORM_THRESHOLD = 60");
assertIncludes(aceCodeEditor, "\"bridge_event_storm\"");
assertIncludes(aceCodeEditor, "val decision = imeController.requestShow(now)");
assertIncludes(aceCodeEditor, "val decision = imeController.requestHide(now)");
assertIncludes(aceCodeEditor, "imeController.onHostImeInsetsChanged(visible, bottomInset, now)");
assertIncludes(aceCodeEditor, "retryableDuringIme = healthMonitor.isHeartbeatSuspended() || imeController.isTransitioning");
assertIncludes(aceCodeEditor, "AceJsErrorClassifier.classifyBridgeEvent(name, isReady, payloadJson)");
assertIncludes(aceCodeEditor, "AceJsErrorClassifier.shouldTreatConsoleErrorAsFatal(");
assertIncludes(aceCodeEditor, "eventHistory.record(\"fallback\"");
assertIncludes(aceCodeEditor, "eventHistory.record(\"renderer_gone\"");
assertIncludes(aceCodeEditor, "\"console_error\",");
assertIncludes(aceCodeEditor, "eventHistory.record(\"heartbeat_result\"");
assertIncludes(aceCodeEditor, "private var lastAceResizeDispatchedReason");
assertIncludes(aceCodeEditor, "private var lastAceResizeCompletedReason");
assertIncludes(aceCodeEditor, "private fun handleResizeDone(payloadJson: String?)");
assertIncludes(aceCodeEditor, "lastAceResizeDispatchedReason = reason");
assertIncludes(aceCodeEditor, "lastAceResizeCompletedReason = resizeReasonFromPayload(payloadJson, lastAceResizeDispatchedReason)");

assertIncludes(aceImeState, "enum class AceImeState");
assertIncludes(aceImeState, "SHOWING");
assertIncludes(aceImeState, "HIDING");

assertIncludes(aceImeController, "class AceImeController(");
assertIncludes(aceImeController, "coalesced:${state.name.lowercase()}");
assertIncludes(aceImeController, "reverse_during_hiding");
assertIncludes(aceImeController, "reverse_during_showing");
assertIncludes(aceImeController, "lastImeTransitionStartedAtUptimeMillis");
assertIncludes(aceImeController, "lastImeTransitionEndedAtUptimeMillis");
assertIncludes(aceImeController, "lastIgnoredRequestReason");

assertIncludes(aceRuntimeEventHistory, "class AceRuntimeEventHistory(");
assertIncludes(aceRuntimeEventHistory, "const val MAX_EVENTS = 30");
assertIncludes(aceRuntimeEventHistory, "events.removeFirst()");

assertIncludes(aceJsErrorClassifier, "object AceJsErrorClassifier");
assertIncludes(aceJsErrorClassifier, "scheduleResize");
assertIncludes(aceJsErrorClassifier, "scrollCursorIntoView");
assertIncludes(aceJsErrorClassifier, "focus");
assertIncludes(aceJsErrorClassifier, "blur");
assertIncludes(aceJsErrorClassifier, "completion");
assertIncludes(aceJsErrorClassifier, "lsp");
assertIncludes(aceJsErrorClassifier, "tooltip");
assertIncludes(aceJsErrorClassifier, "markdown");
assertIncludes(aceJsErrorClassifier, "AceFailureType.JS_INIT_ERROR");

assertIncludes(aceFallbackPolicy, "AceFailureType.LSP_ERROR");
assertIncludes(aceFallbackPolicy, "AceFailureType.COMPLETION_ERROR");

assertIncludes(aceLspServerManager, "class AceLspServerManager(");
assertIncludes(aceLspServerManager, "STATE_DISABLED = \"disabled\"");
assertIncludes(aceLspServerManager, "STATE_CONFIGURED_NO_SERVER = \"configured-no-server\"");
assertIncludes(aceLspServerManager, "TRANSPORT_UNAVAILABLE = \"unavailable\"");
assertIncludes(aceLspServerManager, "FALLBACK_STATIC_COMPLETION = \"static-completion\"");
assertIncludes(aceLspServerManager, "SYNTHETIC_DOCUMENT_URI = \"file:///autojs6/editor/current.js\"");
assertIncludes(aceLspServerManager, "serverAvailable = false");
assertIncludes(aceLspServerManager, "startSupported = false");
assertIncludes(aceLspServerManager, "fun bridgeOptionsJson(): String");
assertNotIncludes(aceLspServerManager, "WebSocket");
assertNotIncludes(aceLspServerManager, "ProcessBuilder");
assertNotIncludes(aceLspServerManager, "Runtime.getRuntime()");

assertIncludes(aceLspClient, "global.AutoJsAceLspClient");
assertIncludes(aceLspClient, "completionProvider: serverReady ? \"server\" : COMPLETION_PROVIDER_STATIC");
assertIncludes(aceLspClient, "hoverProvider: serverReady ? \"server\" : HOVER_PROVIDER_STATIC");
assertIncludes(aceLspClient, "serverAvailable === true");
assertIncludes(aceLspClient, "typeof options.serverUri === \"string\"");
assertIncludes(aceLspClient, "getCompletions: getCompletions");
assertIncludes(aceLspClient, "getHover: getHover");
assertNotIncludes(aceLspClient, "WebSocket");
assertNotIncludes(aceLspClient, "new Worker");

assertIncludes(aceLspClientTest, "NO_SERVER_OPTIONS");
assertIncludes(aceLspClientTest, "completionProvider === \"static-fallback\"");
assertIncludes(aceLspClientTest, "hoverProvider === \"static-fallback\"");
assertIncludes(aceLspClientTest, "app.launch");

assertIncludes(aceTooltip, "global.AutoJsAceTooltip");
assertIncludes(aceTooltip, "loadMarkdownRenderer: function()");
assertIncludes(aceTooltip, "escapeHtml");
assertIncludes(aceTooltip, "pointerType === \"touch\"");
assertIncludes(aceTooltip, "domCreated: !!element");
assertIncludes(aceTooltip, "markdownRenderer: markdownLoaded ? \"safe-subset\" : \"not-loaded\"");
assertNotIncludes(aceTooltip, "fetch(");
assertNotIncludes(aceTooltip, "XMLHttpRequest");
assertNotIncludes(aceTooltip, "innerHTML = hover.docText");

assertIncludes(aceTooltipTest, "Markdown renderer must escape HTML");
assertIncludes(aceTooltipTest, "Tooltip DOM should be lazy");
assertIncludes(aceTooltipTest, "safe-subset");

assertIncludes(editorView, "private val mPrimaryMenuStateSyncDebounceMs = 80L");
assertIncludes(editorView, "private fun schedulePrimaryMenuStateSync()");
assertIncludes(editorView, "schedulePrimaryMenuStateSync()");

assertOnlyIn("key_ace_clean_host_layout", []);
assertOnlyIn("VISUAL_ACE", []);
assertOnlyIn("VisualEditorAce", []);
assertOnlyIn("visual_ace", []);

assertOnlyIn("requestRectangleOnScreen", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt",
]);

assertOnlyIn("CodeEditText", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/AutoIndent.java",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/CodeEditText.kt",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/CodeEditor.kt",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/JavaScriptHighlighter.java",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt",
]);

assertOnlyIn("LayoutHelper", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/AutoIndent.java",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/CodeEditText.kt",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/CodeEditor.kt",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/LayoutHelper.java",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt",
]);

assertOnlyIn("TextViewUndoRedo", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/CodeEditor.kt",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/TextViewUndoRedo.java",
]);

assertOnlyIn("AutoCompletion(context, codeEditor.codeEditText)", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt",
]);

assertOnlyIn("setLoadingGutterDigits", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/CodeEditText.kt",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt",
]);

assertOnlyIn("editText.text?.append(chunk)", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt",
]);

assertOnlyIn("beginBatchEdit()", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt",
]);

assertOnlyIn("OpenableColumns", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/loading/EditorFileReadPipeline.kt",
]);

assertOnlyIn("StringUtils.detectCharset", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/loading/EditorFileReadPipeline.kt",
]);

assertOnlyIn("StringUtils.hasBom", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/loading/EditorFileReadPipeline.kt",
]);

assertOnlyIn("TmpScriptFiles.create(context)", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/RunScriptTextProvider.kt",
]);

assertOnlyIn("write(tmpFile, latestText)", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/RunScriptTextProvider.kt",
]);

assertOnlyIn("AceEditorFontPreferences.set(font)", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/EditorThemeFontService.kt",
]);

assertOnlyIn("Themes.setCurrent(theme.name)", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/EditorThemeFontService.kt",
]);

console.log("Editor boundary checks passed.");
