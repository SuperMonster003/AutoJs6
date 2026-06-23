# Rhino Barcode/Qrcode Provider Implementation

Status: P12-28 direct `barcode` bridge provider POC implemented; Rhino
`compat.barcode` and `compat.qrcode` facades remain pending P12-29.

## Runtime Surface

P12-28 exposes the controlled CommonJS module:

- `require("barcode")`
- `barcode.detect(image, options?)`
- `barcode.detectAll(image, options?)`
- `barcode.recognizeText(image, options?)`
- `barcode.recognizeTexts(image, options?)`

The provider uses ML Kit barcode scanning internally and returns only
JSON-safe snapshots matching `BARCODE_PROVIDER_V1.md`. JavaScript does not
receive ML Kit `Barcode`, `WrappedBarcode`, `ImageWrapper`, `Bitmap`,
`InputImage`, scanner objects, native pointers, Java objects, or Android
objects.

## Bridge And Capability

Implemented runtime pieces:

- `NodeBridgeModules.BARCODE`
- `NodeBridgePermissionManifest.BARCODE`
- `NodeBridgeProviderRegistry` entry `barcode`
- `NodeBridgeResourceType.BARCODE_OPERATION`
- native diagnostics:
  `embedded_script.barcode_active_operation_count`,
  `embedded_script.barcode_live_dispatch_count`,
  `embedded_script.barcode_live_dispatch_failed_count`, and
  `embedded_script.barcode_service_destroy_cancel_count`

`barcode` requests require both `barcode` and `image` capabilities. Path and
capture inputs are implemented by the JS facade through temporary `images`
handles and are recycled after the barcode operation settles. Capture input
requires `{ type: "capture", requireExistingPermission: true }` and reuses the
existing screen-capture permission path without prompting from the service
process.

## Provider Behavior

The Android provider accepts only active provider-owned image handles. Unknown
or recycled handles reject before ML Kit dispatch. Region validation is shared
with OCR/image-handle crop logic but keeps barcode-specific error messages.

The live provider:

- builds ML Kit scanner options from `formats`, `qrOnly`, `maxResults`, and
  `enableAllPotentialBarcodes`;
- rejects unknown formats and non-QR `qrOnly` filters;
- bounds `maxResults` to 1..128;
- closes scanner resources on success, failure, cancellation, timeout, and
  service destroy;
- reports ML Kit provider failures through stable bridge errors.

Result snapshots include the v1 schema, raw/display values, numeric format and
type, format/type names, optional bounds/corner points, and selected structured
payloads for email, phone, sms, url, wifi, geoPoint, contactInfo,
calendarEvent, and driverLicense.

## Evidence

- Kotlin provider:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
- Permissions:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt`
- Provider health:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt`
- Resource cleanup:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeResourceRegistry.kt`
- JS module:
  `app/src/main/cpp/node_bridge_sources.cpp`
- Types:
  `docs/nodejs/types/autojs6-node/barcode.d.ts`
- Instrumentation:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBarcodeBridgeInstrumentationTest.kt`
- Conformance:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt`
- Host gate:
  `tools/nodejs/phase12/verify-rhino-compat-barcode-provider-implementation.js`
- Gradle gate:
  `:app:verifyNodeRhinoCompatBarcodeProviderImplementation`

## Handoff

P12-29 should add `compat.barcode` and `compat.qrcode` wrappers over this
provider. `compat.qrcode` must force QR-only mode and reject non-QR format
filters. Until then, the Rhino API status manifest keeps barcode/qrcode entries
as partial compatibility surfaces even though the direct provider exists.
