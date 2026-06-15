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

assertIncludes(editorView, "mViewportPolicy.onImeInsetsChanged(imeVisible, targetBottom)");
assertIncludes(editorView, "EditorViewportPolicyFactory.create(editor, this)");
assertIncludes(editorView, "EditorCommandDispatcher(");
assertIncludes(editorView, "mCommandDispatcher.onCompletionClick(completions, pos)");
assertIncludes(editorView, "mCommandDispatcher.onCompletionLongClick(completions, pos)");
assertIncludes(editorView, "mCommandDispatcher.onModuleLongClick(module)");
assertIncludes(editorView, "mCommandDispatcher.onPropertyClick(m, property)");
assertIncludes(editorView, "mCommandDispatcher.onPropertyLongClick(m, property)");
assertNotIncludes(editorView, "requestRectangleOnScreen");
assertNotIncludes(editorView, ".setPadding(");
assertNotIncludes(editorView, "TextUtils");

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
assertIncludes(nativeEngine, "return AutoCompletion(context, codeEditor.codeEditText)");
assertNotIncludes(functionsKeyboardHelper, "CodeEditText");
assertNotIncludes(functionsKeyboardHelper, "Editable");

assertOnlyIn("requestRectangleOnScreen", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt",
]);

assertOnlyIn("AutoCompletion(context, codeEditor.codeEditText)", [
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/NativeCodeEditorEngine.kt",
]);

console.log("Editor boundary checks passed.");
