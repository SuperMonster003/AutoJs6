# P13-23 App/Intent/Broadcast Pro Parity

Status: partial

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
- `app.intentToShell()` serializes the same JSON-safe descriptors into `am`
  intent arguments for migration scripts. It is a pure helper and does not
  execute shell commands or dispatch Android bridge requests.
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
- Conformance:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt`
- Host verifier:
  `tools/nodejs/phase13/verify-app-intent-broadcast-parity.js`
- Gradle gate: `:app:verifyNodePhase13AppIntentBroadcastParity`
- Generated reports:
  `build/reports/nodejs/phase13-app-intent-broadcast-parity.json` and
  `build/reports/nodejs/phase13-app-intent-broadcast-parity.md`

## Boundary

P13-23 does not add `sendBroadcast` to the Android bridge dispatch table and
does not add a broadcast capability. Broadcast receiver ownership and
IntentTask-triggered execution remain under the P13-22 blocker set.

P13-23 also does not fold root or Shizuku into the ordinary `app` API. Those
paths remain P13-24 work and must stay auditable under explicit shell/root/
Shizuku capabilities.

No raw Android `Intent`, `Uri`, `Bundle`, `ComponentName`, `PackageManager`,
`ResolveInfo`, `BroadcastReceiver`, or `Context` object crosses the bridge.

## Remaining Blockers

- Full Rhino app helper parity: `parseUri`, `getUriForFile`, `viewFile`,
  `editFile`, `uninstall`, `kill`, `getPackageName`, `getAppName`, preset app
  aliases, and dual-app helpers.
- Activity short-form mapping beyond URL strings and JSON-safe descriptors.
- Root/Shizuku examples that combine `app.intentToShell()` with explicit
  P13-24 shell capabilities.
- Broadcast send promotion with receiver authority, exported/non-exported
  policy, manifest synthesis, user disclosure, rate limits, and packaged smoke.
- root/Shizuku/dual-user routes through explicit P13-24 capability surfaces.
- Installed app/package lookup privacy policy and packaged behavior.
- Negative device tests for invalid component names, invalid flag values,
  non-object extras, and blocked privileged options.
- Migration examples for Rhino `app.intent()` and `app.startActivity()` scripts.

## Verification

Expected commands:

```powershell
node tools\nodejs\phase13\verify-app-intent-broadcast-parity.js --repo-root D:\idea-projects\AutoJs6 --report-dir D:\idea-projects\AutoJs6\build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13AppIntentBroadcastParity
.\gradlew.bat --console=plain :app:verifyAutoJs6NodeTypeDeclarations :app:typeCheckNodeTypescriptSmoke
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeBridgeConformanceInstrumentationTest"
```
