# Rhino Barcode/Qrcode Provider Contract

Status: P12-27 barcode/qrcode bridge schema design-locked.

P12-27 defines the JSON-safe contract for a future `barcode` bridge provider
and later `compat.barcode` / `compat.qrcode` Rhino facades. At the P12-27
checkpoint it did not expose runtime barcode APIs.

## Contract Files

- Design:
  `docs/nodejs/design/BARCODE_PROVIDER_V1.md`
- Schema:
  `docs/nodejs/schemas/barcode_provider_v1.schema.json`
- Fixtures:
  `docs/nodejs/fixtures/barcode-provider-v1-fixtures.json`
- Host verifier:
  `tools/nodejs/phase12/verify-rhino-compat-barcode-provider-contract.js`
- Gradle gate:
  `:app:verifyNodeRhinoCompatBarcodeProviderContract`

## Locked Runtime Shape

The future provider module is `barcode` and the v1 methods are:

- `detect(image, options?)`
- `detectAll(image, options?)`
- `recognizeText(image, options?)`
- `recognizeTexts(image, options?)`

Image input supports:

- project-relative path input;
- opaque provider-owned image handles;
- existing-permission capture input.

The request schema includes region, format filter, QR-only mode, max results,
potential barcode mode, and timeout. `qrcode` compatibility must be a QR-only
wrapper over the same provider and must reject non-QR format filters.

## Snapshot Boundary

Every barcode result is a JSON-safe snapshot with:

- `rawValue`
- `displayValue`
- `format`
- `formatName`
- `valueType`
- `valueTypeName`
- optional `boundingBox`
- optional `cornerPoints`
- selected structured payloads as JSON

The schema defines structured JSON payloads for email, phone, sms, url, wifi,
geoPoint, contactInfo, calendarEvent, and driverLicense.

The contract explicitly rejects raw ML Kit `Barcode`, AutoJs6 `WrappedBarcode`,
Rhino `ImageWrapper`, Android `Bitmap`, `InputImage`, scanner objects, native
pointers, Java objects, Android objects, and arbitrary handles.

## Current Runtime State

Barcode/qrcode remains a bridge gap after P12-27. P12-28 now lands the direct
`require("barcode")` provider, but the status manifest still keeps `barcode`
and `barcode.qr_code` partial until P12-29 lands the compat facades.

## Next Work

P12-28 implements `NodeBridgeModules.BARCODE`, the `barcode` capability,
fake-provider shape tests, live ML Kit provider evidence, provider health, and
handle cleanup. P12-29 should add `compat.barcode` and `compat.qrcode` wrappers.
