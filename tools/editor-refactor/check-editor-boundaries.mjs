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
const scriptEngine = "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/ScriptEditorEngine.kt";
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
const aceDiagnostics = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/diagnostics/AceDiagnostics.kt";
const aceDiagnosticsSnapshot = "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/diagnostics/AceDiagnosticsSnapshot.kt";
const aceSurfaceSmoke = "tools/ace-hardening/check-editor-surface.mjs";
const roadmap = "Roadmap.md";

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
assertIncludes(policyFactory, "EditorEngineType.NATIVE -> NativeEditorViewportPolicy(editor, hostView)");
assertIncludes(policyFactory, "EditorEngineType.ACE -> AceEditorViewportPolicy(editor)");

assertIncludes(nativePolicy, "view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)");
assertIncludes(nativePolicy, "editor.ensureCursorVisibleIfPossible()");

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

assertIncludes(aceSurfaceSmoke, "ACE editor bootstrap order");
assertIncludes(aceSurfaceSmoke, "hidden editor before first paint");
assertIncludes(aceSurfaceSmoke, "visible editor after first paint");
assertIncludes(aceSurfaceSmoke, "double requestAnimationFrame first-paint guard");
assertIncludes(aceSurfaceSmoke, "resizeDone reason payload");
assertIncludes(aceSurfaceSmoke, "gutter breakpoint tap zone");
assertIncludes(aceSurfaceSmoke, "native first-paint request hook");

assertIncludes(aceDiagnosticsSnapshot, "val pendingAceResizeReason: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastAceResizeScheduledReason: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastAceResizeDispatchedReason: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastAceResizeCompletedReason: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastViewportPolicyEvent: String?");
assertIncludes(aceDiagnosticsSnapshot, "val lastViewportPolicyImeVisible: Boolean?");

assertIncludes(aceDiagnostics, "pendingAceResizeReason=${snapshot.pendingAceResizeReason.orUnknown()}");
assertIncludes(aceDiagnostics, "lastAceResizeScheduledReason=${snapshot.lastAceResizeScheduledReason.orUnknown()}");
assertIncludes(aceDiagnostics, "lastAceResizeDispatchedReason=${snapshot.lastAceResizeDispatchedReason.orUnknown()}");
assertIncludes(aceDiagnostics, "lastAceResizeCompletedReason=${snapshot.lastAceResizeCompletedReason.orUnknown()}");
assertIncludes(aceDiagnostics, "lastViewportPolicyEvent=${snapshot.lastViewportPolicyEvent.orUnknown()}");
assertIncludes(aceDiagnostics, "lastViewportPolicyImeVisible=${snapshot.lastViewportPolicyImeVisible?.toString().orUnknown()}");

assertIncludes(aceCodeEditor, "private var lastAceResizeScheduledReason");
assertIncludes(aceCodeEditor, "private var lastAceResizeDispatchedReason");
assertIncludes(aceCodeEditor, "private var lastAceResizeCompletedReason");
assertIncludes(aceCodeEditor, "private fun handleResizeDone(payloadJson: String?)");
assertIncludes(aceCodeEditor, "lastAceResizeDispatchedReason = reason");
assertIncludes(aceCodeEditor, "lastAceResizeCompletedReason = resizeReasonFromPayload(payloadJson, lastAceResizeDispatchedReason)");

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
