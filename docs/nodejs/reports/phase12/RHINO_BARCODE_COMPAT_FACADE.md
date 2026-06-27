# Rhino Barcode Compat Facade

Status: P12-29 `compat.barcode` and `compat.qrcode` facades implemented.

P12-29 promotes the Rhino-facing barcode/qrcode API over the P12-28 controlled
`require("barcode")` provider. The facade remains Promise-based and JSON-only.
It does not expose ML Kit `Barcode`, scanner, `Bitmap`, `ImageWrapper`, or Java
objects.

## Runtime Surface

- `require("autojs6:compat").barcode`
- `require("autojs6:compat").qrcode`
- `compat.barcode.detect(imageOrOptions?, options?)`
- `compat.barcode.detectAll(imageOrOptions?, options?)`
- `compat.barcode.recognizeText(imageOrOptions?, options?)`
- `compat.barcode.recognizeTexts(imageOrOptions?, options?)`
- `compat.qrcode.detect(imageOrOptions?, options?)`
- `compat.qrcode.detectAll(imageOrOptions?, options?)`
- `compat.qrcode.recognizeText(imageOrOptions?, options?)`
- `compat.qrcode.recognizeTexts(imageOrOptions?, options?)`

Both facade objects are frozen callable modules. Calling the facade directly is
an alias for `recognizeTexts(...)`.

## Compatibility Behavior

- image inputs may be provider-owned handles, project-relative paths, typed
  direct-provider image inputs, or omitted to capture the screen through the
  existing-permission image path;
- temporary path/capture handles are recycled after the barcode operation
  settles;
- `compat.qrcode` forces `qrOnly: true` and `formats: ["QR_CODE"]`;
- `recognizeText()` returns `string | null`;
- `recognizeTexts()` returns a frozen string array;
- `detect()` / `detectAll()` return frozen JSON-safe barcode snapshots;
- `compat.install({ globals: true })` may install `barcode` and `qrcode`
  globals, but they are not installed by default.

## Evidence

- Runtime: `app/src/main/cpp/node_bridge_sources.cpp`
- Types: `docs/nodejs/types/autojs6-node/rhino_compat.d.ts`
- Fake transport tests:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBarcodeBridgeInstrumentationTest.kt`
- Conformance tests:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt`
- Status manifest:
  `docs/nodejs/rhino-compat/status-manifest.json`
- Surface manifest:
  `docs/nodejs/rhino-compat/phase12-surface-manifest.json`
- Gate: `tools/nodejs/phase12/verify-rhino-compat-barcode-facade.js`
- Gradle: `:app:verifyNodeRhinoCompatBarcodeFacade`

## Handoff

P12-30 should add barcode/qrcode examples and packaged smoke evidence. Packaged
behavior remains provider-dependent and must fail closed with stable errors when
ML Kit barcode scanning is unavailable.
