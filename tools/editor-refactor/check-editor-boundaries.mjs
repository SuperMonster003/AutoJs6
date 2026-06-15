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
const scriptEngine = "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/ScriptEditorEngine.kt";
const nativePolicy = "app/src/main/java/org/autojs/autojs/ui/edit/editor/viewport/NativeEditorViewportPolicy.kt";
const acePolicy = "app/src/main/java/org/autojs/autojs/ui/edit/editor/viewport/AceEditorViewportPolicy.kt";
const policyFactory = "app/src/main/java/org/autojs/autojs/ui/edit/editor/viewport/EditorViewportPolicyFactory.kt";
const commandDispatcher = "app/src/main/java/org/autojs/autojs/ui/edit/editor/command/EditorCommandDispatcher.kt";
const nativeEngine = "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt";
const aceEngine = "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/AceScriptEditorEngine.kt";
const functionsKeyboardHelper = "app/src/main/java/org/autojs/autojs/ui/edit/keyboard/FunctionsKeyboardHelper.java";
const fileReadPipeline = "app/src/main/java/org/autojs/autojs/ui/edit/editor/loading/EditorFileReadPipeline.kt";
const textLoadPolicy = "app/src/main/java/org/autojs/autojs/ui/edit/editor/loading/EditorTextLoadPolicy.kt";
const textLoadTarget = "app/src/main/java/org/autojs/autojs/ui/edit/editor/loading/EditorTextLoadTarget.kt";
const editorTextProvider = "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/EditorTextProvider.kt";
const runScriptTextProvider = "app/src/main/java/org/autojs/autojs/ui/edit/editor/service/RunScriptTextProvider.kt";

assertIncludes(editorView, "mViewportPolicy.onImeInsetsChanged(imeVisible, targetBottom)");
assertIncludes(editorView, "EditorViewportPolicyFactory.create(editor, this)");
assertIncludes(editorView, "EditorCommandDispatcher(");
assertIncludes(editorView, "mCommandDispatcher.onCompletionClick(completions, pos)");
assertIncludes(editorView, "mCommandDispatcher.onCompletionLongClick(completions, pos)");
assertIncludes(editorView, "mCommandDispatcher.onModuleLongClick(module)");
assertIncludes(editorView, "mCommandDispatcher.onPropertyClick(m, property)");
assertIncludes(editorView, "mCommandDispatcher.onPropertyLongClick(m, property)");
assertIncludes(editorView, "private val fileReadPipeline = EditorFileReadPipeline(context)");
assertIncludes(editorView, "fileReadPipeline.readText(uri)");
assertIncludes(editorView, ".streamTextChunks(");
assertIncludes(editorView, "private fun applyReadMetadata(metadata: EditorFileReadMetadata)");
assertIncludes(editorView, "private val editorTextProvider = EditorTextProvider(this) { editor }");
assertIncludes(editorView, "private val runScriptTextProvider = RunScriptTextProvider(");
assertIncludes(editorView, "runScriptTextProvider.saveToTmpFile()");
assertIncludes(editorView, "editorTextProvider.getTextForSaving()");
assertIncludes(editorView, "private val textLoadTarget: EditorTextLoadTarget");
assertIncludes(editorView, "EditorTextLoadPolicy.shouldStreamAsLargeFile(sizeOrNull)");
assertIncludes(editorView, "EditorTextLoadPolicy.shouldLoadProgressively(text.length)");
assertIncludes(editorView, "EditorTextLoadPolicy.largeFilePolicyLabel(");
assertIncludes(editorView, "textLoadTarget.loadSmallText(text)");
assertNotIncludes(editorView, "requestRectangleOnScreen");
assertNotIncludes(editorView, ".setPadding(");
assertNotIncludes(editorView, "TextUtils");
assertNotIncludes(editorView, "LARGE_FILE_MODE_THRESHOLD");
assertNotIncludes(editorView, "editor.setLoadingText(");
assertNotIncludes(editorView, "editor.appendTextChunk(");
assertNotIncludes(editorView, "editor.beginLargeTextLoading(");
assertNotIncludes(editorView, "editor.cancelLargeTextLoading(");
assertNotIncludes(editorView, "OpenableColumns");
assertNotIncludes(editorView, "StringUtils.detectCharset");
assertNotIncludes(editorView, "StringUtils.hasBom");
assertNotIncludes(editorView, "streamDecodeAndEmit(");
assertNotIncludes(editorView, "getTextForSavingObservable");
assertNotIncludes(editorView, "TmpScriptFiles");

assertIncludes(scriptEngine, "fun onImeInsetsChanged(visible: Boolean, bottomInset: Int) = Unit");
assertIncludes(policyFactory, "EditorEngineType.NATIVE -> NativeEditorViewportPolicy(editor, hostView)");
assertIncludes(policyFactory, "EditorEngineType.ACE -> AceEditorViewportPolicy(editor)");

assertIncludes(nativePolicy, "view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)");
assertIncludes(nativePolicy, "editor.ensureCursorVisibleIfPossible()");

assertIncludes(acePolicy, "editor.onImeInsetsChanged(visible, bottomInset)");
assertNotIncludes(acePolicy, "ensureCursorVisibleIfPossible");
assertNotIncludes(acePolicy, "requestRectangleOnScreen");

assertIncludes(commandDispatcher, "private val currentEditor: () -> ScriptEditorEngine");
assertNotIncludes(commandDispatcher, "CodeEditText");

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

assertOnlyIn("requestRectangleOnScreen", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt",
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

console.log("Editor boundary checks passed.");
