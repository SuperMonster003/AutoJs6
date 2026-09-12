#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PROJECT_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");

const FILES = {
  settings: "settings.gradle.kts",
  appBuild: "app/build.gradle.kts",
  manifest: "app/src/main/AndroidManifest.xml",
  contract: "plugin-api/editor-api/src/main/java/org/autojs/plugin/editor/api/EditorPluginContract.kt",
  entrypoint: "plugin-api/editor-api/src/main/java/org/autojs/plugin/editor/api/EditorPluginEntrypoint.kt",
  session: "plugin-api/editor-api/src/main/java/org/autojs/plugin/editor/api/EditorPluginSession.kt",
  consumerRules: "plugin-api/editor-api/consumer-rules.pro",
  host: "app/src/main/java/org/autojs/autojs/core/plugin/editor/AceEditorPluginHost.kt",
  engine: "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/AceScriptEditorEngine.kt",
  factory: "app/src/main/java/org/autojs/autojs/ui/edit/editor/engine/EditorEngineFactory.kt",
  policyTest: "app/src/test/java/org/autojs/autojs/core/plugin/editor/AceEditorPluginPolicyTest.kt",
  mappingsTest: "app/src/test/java/org/autojs/autojs/ui/edit/editor/engine/AceEditorPluginMappingsTest.kt",
  deviceTest: "app/src/androidTest/java/org/autojs/autojs/core/plugin/editor/AceEditorPluginHostDeviceTest.kt",
};

function resolve(relativePath) {
  return path.join(PROJECT_ROOT, relativePath);
}

function read(relativePath) {
  return fs.readFileSync(resolve(relativePath), "utf8").replaceAll("\r\n", "\n");
}

function assertExists(relativePath) {
  if (!fs.existsSync(resolve(relativePath))) {
    throw new Error(`${relativePath}: required file is missing`);
  }
}

function assertMissing(relativePath) {
  if (fs.existsSync(resolve(relativePath))) {
    throw new Error(`${relativePath}: embedded ACE runtime file must stay outside the host`);
  }
}

function assertIncludes(relativePath, text, label = text) {
  if (!read(relativePath).includes(text)) {
    throw new Error(`${relativePath}: missing ${label}`);
  }
}

for (const file of Object.values(FILES)) {
  assertExists(file);
}

assertIncludes(FILES.settings, '"editor-api"', "editor-api Gradle module registration");
assertIncludes(FILES.appBuild, 'implementation(project(":plugin-api:editor-api"))');
assertIncludes(FILES.manifest, '<action android:name="org.autojs.plugin.EDITOR" />');
assertIncludes(FILES.manifest, '<category android:name="ace-editor" />');

if (!/^\s*const val VERSION = [1-9]\d*\s*$/m.test(read(FILES.contract))) {
  throw new Error(`${FILES.contract}: missing positive contract VERSION`);
}
assertIncludes(FILES.contract, "const val MIN_SUPPORTED_VERSION = 1");
assertIncludes(FILES.contract, "const val MAX_SUPPORTED_VERSION = VERSION");
assertIncludes(FILES.contract, 'const val ACTION_EDITOR = "org.autojs.plugin.EDITOR"');
assertIncludes(FILES.contract, 'const val CATEGORY_ACE = "ace-editor"');
assertIncludes(FILES.contract, "META_DATA_REQUIRES_HOST_VERSION");
assertIncludes(FILES.contract, "META_DATA_MAX_HOST_VERSION");
assertIncludes(FILES.entrypoint, "The plugin must package this API as `compileOnly`");
assertIncludes(FILES.session, "fun destroy()");
assertIncludes(FILES.consumerRules, "-keep class org.autojs.plugin.editor.api.** { *; }");

assertIncludes(FILES.host, "PathClassLoader(");
assertIncludes(FILES.host, "queryIntentServices(");
assertIncludes(FILES.host, "sameSignerAsHost");
assertIncludes(FILES.host, "PluginAuthorizedState.OFFICIAL");
assertIncludes(FILES.host, "PluginAuthorizedState.TRUSTED");
assertIncludes(FILES.host, "Re-resolve package, component, metadata, version and signer");
assertIncludes(FILES.host, "candidateEligibilityError(currentCandidate)");
assertIncludes(FILES.host, "ensureMainThread(");
assertIncludes(FILES.host, "destroyed.compareAndSet(false, true)");

assertIncludes(FILES.engine, "AceEditorPluginHost.createSession(");
assertIncludes(FILES.engine, "loadedSession.destroy()");
assertIncludes(FILES.factory, "AceScriptEditorEngine.create(context)");
assertIncludes(FILES.factory, "createAceFallback(context");
assertIncludes(FILES.factory, "lastAcePluginFailure");

assertIncludes(FILES.policyTest, "user grant is insufficient for in-process execution");
assertIncludes(FILES.policyTest, "required host version is enforced");
assertIncludes(FILES.policyTest, "maximum host version is enforced");
assertIncludes(FILES.mappingsTest, "snapshot round trip preserves editor state");
assertIncludes(FILES.deviceTest, "Cross-APK smoke test");
assertIncludes(FILES.deviceTest, "AceEditorPluginHost.createSession(");
assertIncludes(FILES.deviceTest, "assumeTrue(");

[
  "app/src/main/assets/editor/ace-builds-1.4.12",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceBridge.kt",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceCodeEditor.kt",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceEditorAssets.kt",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceEditorDebugActivity.kt",
  "app/src/main/java/org/autojs/autojs/ui/edit/editor/ace/AceImeController.kt",
  "app/src/main/res/layout/activity_ace_editor_debug.xml",
  "app/src/androidTest/java/org/autojs/autojs/ui/edit/editor/ace/AceCodeEditorSmokeTest.kt",
].forEach(assertMissing);

console.log("Editor plugin host gate passed.");
