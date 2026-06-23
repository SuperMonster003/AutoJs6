# Rhino Barcode/Qrcode Examples And Packaged Smoke

Status: P12-30 barcode/qrcode examples and packaged policy evidence landed.

P12-30 adds checked Rhino-to-Node examples for barcode/qrcode migration and
documents the packaged smoke boundary. The runtime support itself comes from
P12-28 direct `require("barcode")` and P12-29 `compat.barcode` /
`compat.qrcode`.

## Examples

Files:

- `docs/nodejs/examples/rhino-compat/10-barcode.rhino.js`
- `docs/nodejs/examples/rhino-compat/10-barcode.node.cjs`

The Node example covers:

- project-relative image path input through `compat.barcode.detectAll(...)`;
- provider-owned image handle input through `compat.images.read(...)`;
- QR-only filtering through `compat.qrcode.detectAll(...)`;
- explicit temporary image cleanup through `compat.images.recycle(...)`.

The examples are static-verified by `verifyNodeRhinoCompatExamples` and locked
by the P12-30 gate `verifyNodeRhinoCompatBarcodeExamples`.

## Packaged Policy

Packaged barcode/qrcode support is provider-dependent. If ML Kit barcode
scanning is unavailable in a packaged APK because model assets or Google Play
services are not present, the provider must fail closed with a stable unavailable/degraded bridge error. Packaged scripts must not receive raw ML Kit
`Barcode`, scanner, `Bitmap`, `InputImage`, `ImageWrapper`, model path, native
pointer, Java object, or Android object.

The checked P12-30 evidence is a policy and static smoke gate rather than a
mandatory live packaged APK execution gate. Live packaged execution remains
device/provider dependent.

## Evidence

- Examples:
  `docs/nodejs/examples/rhino-compat/10-barcode.node.cjs`
  `docs/nodejs/examples/rhino-compat/10-barcode.rhino.js`
- Example manifest:
  `docs/nodejs/examples/rhino-compat/manifest.json`
- Example docs:
  `docs/nodejs/examples/rhino-compat/README.md`
  `docs/nodejs/EXAMPLES.md`
  `docs/nodejs/MIGRATION_FROM_RHINO.md`
- Static example gate:
  `tools/nodejs/phase11/verify-rhino-compat-examples.js`
- P12-30 gate:
  `tools/nodejs/phase12/verify-rhino-compat-barcode-examples.js`
- Gradle:
  `:app:verifyNodeRhinoCompatBarcodeExamples`
