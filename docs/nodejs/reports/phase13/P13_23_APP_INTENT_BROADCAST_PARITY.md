# P13-23 App/Intent/Broadcast Pro Parity

Status: ready

Date: 2026-06-24

## Landed Scope

P13-23 extends the existing Node `app` module without promoting the full Rhino
`app` surface.

- `app.intent(request)` now returns a frozen JSON-safe intent descriptor.
- `app.startActivity(request, options)` reuses the same descriptor normalizer.
- The descriptor accepts `category` and `categories`, de-duplicates categories,
  accepts a single numeric flag or an array of numeric flags, and accepts
  JSON-safe primitive extras and primitive arrays.
- Android bridge parsing now accepts `category`/`categories`, merged numeric
  flags, and primitive array extras before creating the Android `Intent`.
- Follow-up Pro helper parity now normalizes short `action`/`category` strings
  such as `VIEW` and `BROWSABLE`, accepts `url` as a descriptor alias for
  `data`, and allows URL string input through `app.startActivity()`.
- `app.openUrl()`, `app.sendEmail()`, `app.openAppSettings()`,
  `app.launchSettings()`, and `app.launchAppDetailsSettings()` now reuse the
  existing scoped `startActivity` / settings bridge methods instead of adding a
  new Android authority surface.
- `app.uninstall()` opens the Android package uninstall flow through
  `ACTION_DELETE` and the existing scoped activity bridge; it does not perform
  silent uninstall or package-manager mutation.
- `app.getPackageName()`, `app.getAppName()`, and `app.isInstalled()` now
  expose exact installed-app lookup through an explicit `app.query` capability.
  They return JSON-safe string/null/boolean values and do not expose raw
  `ApplicationInfo`, `PackageInfo`, or installed-app lists.
- `app.viewFile()` and `app.editFile()` launch Android `ACTION_VIEW` /
  `ACTION_EDIT` for files resolved inside the scoped Node working directory.
  They accept relative paths or absolute paths under the same working directory,
  return JSON-safe boolean launch results, and do not expose raw `Uri` or
  `FileProvider` handles.
- `app.launch()` is available as a Rhino-style alias for
  `app.launchPackage()`; it does not add a new Android bridge method or
  authority surface.
- Rhino global `launch()` migration example coverage now lives in
  `docs/nodejs/MIGRATION_FROM_RHINO.md`, where legacy `launch("pkg")`
  rewrites to `await require("app").launch("pkg", { timeoutMs })` while
  preserving `app.launchPackage()` as the clearer exact-package API.
- `app.intentToShell()` serializes the same JSON-safe descriptors into `am`
  intent arguments for migration scripts. It is a pure helper and does not
  execute shell commands or dispatch Android bridge requests.
- `app.parseUri()` is available as a pure JSON-safe URI inspection helper. It
  returns a frozen descriptor with string/number fields only and does not expose
  Android `Uri` instances.
- `app.getUriForFile()` is present only as a synchronous stable rejection stub
  because raw Android `Uri` and `FileProvider` handles still cannot cross into
  Node. Scoped file activity routing remains available through
  `app.viewFile()` and `app.editFile()`.
- `app.kill()`, `app.killDual()`, `app.startDualActivity()`,
  `app.launchDual*()`, `app.openDual*()`, `app.uninstallDual()`, and
  `app.isDualInstalled()` are present only as stable rejection stubs. They do
  not execute shell commands and do not dispatch bridge requests.
- `app.sendBroadcast()` is present only as a stable rejection stub. It does not
  dispatch to the bridge and does not register, send, or receive Android
  broadcasts.
- Privileged Rhino options such as `root`, `shizuku`, `dual`, `user`, and
  `userId` are rejected in the Node JavaScript facade before bridge dispatch.

## Evidence

- Runtime facade: `app/src/main/cpp/node_bridge_sources.cpp`
- Android descriptor parser:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
- Type declarations: `docs/nodejs/types/autojs6-node/app.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Rhino global `launch()` migration example:
  `docs/nodejs/MIGRATION_FROM_RHINO.md`
- Conformance:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt`
- Packaged scoped file Activity smoke:
  `app/src/androidTest/java/org/autojs/autojs/apkbuilder/EmbeddedNodePackagedApkSmokeTest.kt#installAndRunAppBridgeEmbeddedNodePackagedApk`
- Host verifier:
  `tools/nodejs/phase13/verify-app-intent-broadcast-parity.js`
- Gradle gate: `:app:verifyNodePhase13AppIntentBroadcastParity`
- Generated reports:
  `build/reports/nodejs/phase13-app-intent-broadcast-parity.json` and
  `build/reports/nodejs/phase13-app-intent-broadcast-parity.md`

## Current Device Evidence

QV710AF65F / XQ-AT72 / Android 12 runs the current P13-23
`app.startActivity()` live provider evidence through:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest#appStartActivityShortFormsAndNegativeCorpusUseLiveBridge,org.autojs.autojs.engine.NodeAutoJsBridgeInstrumentationTest#androidAppBridgeRejectsInvalidStartActivityDescriptors" "-Pautojs.nodejs.test.deviceProfile=sony"
```

The focused smoke starts only the current package via short
`MAIN`/`LAUNCHER` action/category descriptors. It also asserts fail-closed
negative cases for invalid flags, non-object extras, and malformed components
with source markers:
`app.live.negative.flags=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|app|startActivity`,
`app.live.negative.extras=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|app|startActivity`,
and
`app.live.negative.component=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED|app|startActivity`.
`NodeAutoJsBridgeInstrumentationTest#androidAppBridgeRejectsInvalidStartActivityDescriptors`
also covers the Android provider-level invalid descriptor matrix.

## Descriptor Alias And Gap Matrix

`P13-23 descriptor alias matrix`:

| Rhino / Pro shape | P13-23 Node status | Node path |
| --- | --- | --- |
| URL string `startActivity("https://example.com/path")` | supported | URL string normalizes to an `ACTION_VIEW` descriptor. |
| Short `action: "VIEW"` | supported | `action: "VIEW"` expands to `android.intent.action.VIEW`. |
| Short `category: "BROWSABLE"` | supported | `category: "BROWSABLE"` expands to `android.intent.category.BROWSABLE`. |
| `url` descriptor alias | supported | `url` is normalized into descriptor `data`. |
| `package` descriptor alias | supported | `package` is normalized into descriptor `packageName`. |
| Named Rhino activity short forms beyond URL strings and JSON descriptors | stable-denied for v1.1 | Use explicit JSON-safe descriptors or keep the Rhino script on the Rhino engine. |
| Root, Shizuku, dual-user, or raw Android object options | stable-denied for v1.1 | Use future P13-22/P13-24 gates instead of the ordinary `app` bridge. |

`P13-23 Rhino app gap inventory`:

| Rhino / Pro shape | P13-23 Node mapping |
| --- | --- |
| `getInstalledApps()` / `getInstalledPackages()` | Future redacted JSON provider plus disclosure policy; raw Java object lists stay blocked. |
| Preset app aliases and localized app-name shortcuts | Exact `getPackageName(appName)` / `launchApp(appName)` lookup only, otherwise stable-denied for v1.1. |
| Raw `Intent`, `Uri`, `FileProvider`, `ApplicationInfo`, and `PackageInfo` objects | JSON-safe descriptors only; raw Android objects do not cross into Node. |
| `app.sendBroadcast()` / IntentTask mutation | Future P13-22 broadcast provider; stable-denied in P13-23. |

## Installed-App Query Privacy Policy

Exact app/package lookup is the only P13-23 promoted query shape:
`app.getPackageName(appName)`, `app.getAppName(packageName)`, and
`app.isInstalled(packageName)` require the explicit `app.query` capability and
return JSON-safe `string | null` or `boolean` results. P13-52 now lists
`app_query` / `app.query` as a disclosure surface with project capability review
and packaged metadata review channels.

Future raw list APIs must use `autojs6-node-app-query-redacted-list-v1` instead
of raw Android Java objects. The allowed item fields are:

```json
{
  "schema": "autojs6-node-app-query-redacted-list-v1",
  "items": [
    {
      "packageName": "org.example.app",
      "appName": "Example",
      "installed": true,
      "visibilityLimited": true,
      "source": "package_manager_redacted"
    }
  ]
}
```

The schema forbids `ApplicationInfo`, `PackageInfo`, APK paths, data dirs,
installer package names, signatures, requested permissions, UID/user ids,
flags, and install/update timestamps until a later provider has a reviewed
privacy model for each field.

Packaged `app.query` disclosure examples:

```json
{"node":{"permissions":["app.query"],"disclosures":["app_query_exact_lookup"]}}
```

```json
{"autojs6":{"node":{"permissions":["app.query"],"disclosures":["app_query_exact_lookup"]}}}
```

## Packaged Scoped File Activity Evidence

The focused packaged `app` bridge smoke now declares `node.permissions` for
`app.launch` and `app.activity`, bundles `packaged-app-activity-smoke.txt`, and
asserts `packaged.app.view_file=true` plus `packaged.app.edit_file=true` after
calling `app.viewFile()` and `app.editFile()` from the packaged APK.

The Android bridge selects the fixed main-app `AppFileProvider.AUTHORITY` for
normal app builds and `${packageName}.fileprovider` for inrt/packaged builds,
matching the packaged manifest authority rewrite.

Packaged `app.activity` disclosure example:

```json
{"node":{"permissions":["app.launch","app.activity"],"disclosures":["app_activity_scoped_file_view_edit"]}}
```

## Boundary

P13-23 does not add `sendBroadcast` to the Android bridge dispatch table and
does not add a broadcast capability. Broadcast receiver ownership and
IntentTask-triggered execution remain under the P13-22 blocker set.

P13-23 also does not fold root or Shizuku into the ordinary `app` API. Those
paths remain P13-24 work and must stay auditable under explicit shell/root/
Shizuku capabilities.

No raw Android `Intent`, `Uri`, `Bundle`, `ComponentName`, `PackageManager`,
`ResolveInfo`, `ApplicationInfo`, `PackageInfo`, `BroadcastReceiver`, or
`Context` object crosses the bridge.

P13-23 keeps `app.sendBroadcast()`, raw Android `Uri` / `FileProvider`
handles, dual-user helpers, and root/Shizuku app tiers stable-denied for v1.1.
Future promotion must land under the dedicated P13-22 broadcast provider or
P13-24 shell/root/Shizuku capability gates instead of expanding the ordinary
`app` bridge implicitly.

## Remaining Blockers

No P13-23 verifier blockers remain for the bounded v1.1 app/intent scope.

The docs, compatibility notes, surface manifest, and Gradle gate are
evidence-only for this bounded v1.1 decision. They do not add a separate P13-23
blocker beyond the safe Activity device smokes, redacted installed-app list
policy, packaged scoped-file evidence, negative corpus, and privacy disclosure
above.

## Verification

Expected commands:

```powershell
node tools\nodejs\phase13\verify-app-intent-broadcast-parity.js --repo-root D:\idea-projects\AutoJs6 --report-dir D:\idea-projects\AutoJs6\build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13AppIntentBroadcastParity
.\gradlew.bat --console=plain :app:verifyAutoJs6NodeTypeDeclarations :app:typeCheckNodeTypescriptSmoke
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeConformanceInstrumentationTest"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunAppBridgeEmbeddedNodePackagedApk" "-Pautojs.nodejs.test.deviceProfile=sony"
```
