# Barcode Provider V1

Status: P12-27 design-locked contract for a future controlled `barcode`
bridge provider and later `compat.barcode` / `compat.qrcode` facades.

P12-27 does not expose a runtime module. It locks the JSON-safe request and
result shape that P12-28 must implement before P12-29 can add Rhino-style
compat facades.

## Decision

The provider module name is:

```js
const barcode = require("barcode");
```

The v1 provider is Promise-based and JSON-only. It may use ML Kit internally,
but JavaScript must never receive ML Kit `Barcode`, AutoJs6 `WrappedBarcode`,
`ImageWrapper`, Android `Bitmap`, `InputImage`, scanner, native pointer, or any
Java/Android object.

The provider methods are:

- `detect(image, options?) -> Promise<BarcodeSnapshot | null>`
- `detectAll(image, options?) -> Promise<readonly BarcodeSnapshot[]>`
- `recognizeText(image, options?) -> Promise<string | null>`
- `recognizeTexts(image, options?) -> Promise<readonly string[]>`

`recognizeText()` returns the first detected `rawValue` or `null`.
`recognizeTexts()` returns all non-null `rawValue` values in provider order.

## Image Input

P12-27 locks three image input modes:

```ts
type BarcodeImageInput =
  | { type: "path"; path: string }
  | { type: "imageHandle"; id: string; __autojs6ImageHandle?: string }
  | { type: "capture"; requireExistingPermission: true };
```

Rules:

- `path` is project-relative and scoped like `image.readImage()`. Absolute
  paths, NUL, `..`, URI schemes, and symlink/realpath escapes are rejected.
- `imageHandle` must refer to an active provider-owned opaque image handle from
  `image` / `images`. Unknown or recycled handles reject before ML Kit dispatch.
- `capture` may only use an existing screen-capture permission. It must set
  `requireExistingPermission: true`; the barcode provider must not prompt from
  the service process.
- Path and capture inputs create temporary image handles that must be recycled
  after the barcode operation settles.

## Options

```ts
interface BarcodeOptions {
  region?: [number, number, number, number] | { x: number; y: number; width: number; height: number };
  formats?: readonly BarcodeFormatName[];
  qrOnly?: boolean;
  maxResults?: number;
  enableAllPotentialBarcodes?: boolean;
  timeoutMs?: number;
}
```

`region` is clipped against the source image bounds. It uses the same
`[x, y, width, height]` convention as image/OCR compatibility helpers.

`formats` is a format filter. Accepted names are the ML Kit format names without
exposing the ML Kit class: `QR_CODE`, `AZTEC`, `CODABAR`, `CODE_39`, `CODE_93`,
`CODE_128`, `DATA_MATRIX`, `EAN_8`, `EAN_13`, `ITF`, `PDF_417`, `UPC_A`,
`UPC_E`, and `ALL`.

`qrOnly: true` is the qrcode compatibility mode. It is equivalent to
`formats: ["QR_CODE"]`. A future `compat.qrcode` facade must always set this
mode and must reject any non-QR format filter.

## Result Snapshot

Every detected barcode is returned as a frozen JSON snapshot:

```json
{
  "schema": "autojs6-node-barcode-snapshot-v1",
  "rawValue": "https://example.com",
  "displayValue": "https://example.com",
  "format": 256,
  "formatName": "QR_CODE",
  "valueType": 8,
  "valueTypeName": "URL",
  "boundingBox": {
    "left": 10,
    "top": 20,
    "right": 110,
    "bottom": 120,
    "width": 100,
    "height": 100
  },
  "cornerPoints": [
    { "x": 10, "y": 20 },
    { "x": 110, "y": 20 },
    { "x": 110, "y": 120 },
    { "x": 10, "y": 120 }
  ],
  "structured": {
    "payloadType": "url",
    "url": {
      "title": "Example",
      "url": "https://example.com"
    }
  }
}
```

Required fields are:

- `rawValue`
- `displayValue`
- `format`
- `formatName`
- `valueType`
- `valueTypeName`

`boundingBox`, `cornerPoints`, and `structured` are optional but must remain
JSON-only when present. Coordinates are numbers, not Android `Rect` or `Point`
objects.

## Structured Payloads

Selected structured payloads may be returned as JSON. The provider exposes only
selected structured payloads as JSON:

| ML Kit value type | JSON field |
| --- | --- |
| email | `structured.email` |
| phone | `structured.phone` |
| sms | `structured.sms` |
| url | `structured.url` |
| wifi | `structured.wifi` |
| geoPoint | `structured.geoPoint` |
| contactInfo | `structured.contactInfo` |
| calendarEvent | `structured.calendarEvent` |
| driverLicense | `structured.driverLicense` |

The provider must omit fields that are not present. It must not return raw ML
Kit nested classes such as `Barcode.Email`, `Barcode.ContactInfo`, or
`Barcode.DriverLicense`.

## Limits

| Limit | Hard Maximum |
| --- | ---: |
| `maxResults` | 128 |
| `maxFormats` | 16 |
| `maxImageInputPathBytes` | 4096 |
| `maxRegionPixels` | 16777216 |
| `maxStructuredPayloadDepth` | 8 |
| `maxRawValueBytes` | 8192 |

The provider must fail closed when limits are exceeded. Unknown formats,
non-finite coordinates, negative dimensions, unsupported image inputs, recycled
handles, and model/provider unavailability use stable bridge errors rather than
falling back to Rhino objects.

## Capability And Resource Policy

P12-28 should add a separate `barcode` bridge capability. Barcode scanning also
depends on the existing `image` capability, and capture input depends on
`screen_capture`.

The provider must share the existing image handle resource model:

- provider-owned image handles remain opaque;
- path and capture temporary handles are recycled after the operation settles;
- unknown or recycled handles are denied before scanner dispatch;
- scanner operations are bounded by `timeoutMs`;
- cleanup diagnostics report active barcode operation count and cancellation
  count.

## Packaged Policy

Packaged APK support remains provider-dependent until P12-30. If ML Kit barcode
model assets or Google Play services are unavailable in a packaged APK, the
provider must return a stable degraded/unavailable error. Packaged code must not
receive raw `Bitmap`, ML Kit scanner, or model path objects.

## P12-28 And P12-29 Handoff

P12-28 owns runtime provider implementation:

- `NodeBridgeModules.BARCODE`;
- `NodeBridgePermissionManifest.BARCODE`;
- fake-provider shape tests;
- live ML Kit provider gate;
- provider health and degraded/unavailable model diagnostics.

P12-29 owns compatibility facades:

- `require("autojs6:compat").barcode`;
- `require("autojs6:compat").qrcode`;
- optional globals only after `compat.install({ globals: true })`;
- `qrcode` fixed QR-only filter;
- `recognizeText`/`recognizeTexts` Rhino return shape.

Until those tasks land, barcode/qrcode remain a documented bridge gap in the
status manifest.
