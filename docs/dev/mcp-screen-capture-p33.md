# MCP screenshot payloads (P3.3)

Since 2026-09-13, `image.captureScreen(options)` can return an encoded screenshot through the
MCP broker's existing one-shot descriptor transport. The options are the same as
`accessibility.screenshot`: `format` (`jpeg`, `png`, `webp`), `quality`, `scale` or `maxWidth`,
and `region` (`left`, `top`, `right`, `bottom`). `savePath` is rejected on this image method.
The no-argument `image.captureScreen()` still returns the provider-owned image handle used
by existing Node/Python callers.

The caller first requests `media_projection.requestScreenCapture`. A live capturer in the
broker session is reused, and a missing/revoked permission produces a permission error. The
encoded path takes a copy of the current frame, uses `NodeBridgeAccessibilityDiagnostics`
for cropping/scaling/encoding, and releases every source/intermediate bitmap. Its metadata
has `source: media_projection`; accessibility captures use `source: accessibility`.

The host rejects compressed images over 4 MiB. The plugin additionally limits the base64
representation to 4 MiB and retries at lower quality or smaller dimensions. Encoding and
capture stay in the host. The plugin reads the read-only descriptor, validates its length,
closes it, and produces standard MCP image content. No AIDL surface, contract version,
grant method set, permissions or locked AAR needs to change: both method names and the
one-shot payload transport were already part of the MCP grant.

The accessibility request must declare both `accessibility` and `screen_capture`; the
projection request declares `screen_capture`; encoded image capture declares `image` and
`screen_capture`. A missing declaration is rejected even if the session grant contains it.

On API 24, a consecutive capture can temporarily return no frame while the underlying
capturer refreshes its virtual display. `NodeScreenCaptureSession.captureFreshFrame` retries
up to six times with 40 ms between attempts, as the existing `Images.captureScreen` path does.
Each underlying attempt is already limited to 1.2 s, so the retry fits the bridge's 15 s capture
budget. Exceptions propagate immediately; no old bitmap is retained and consent is not
requested again. The regression test covers recovery, the exact retry ceiling, and permission
failure propagation.

`NodeBridgeScreenshotEncodingTest` runs deterministic Android bitmap tests for all three
formats, crop/scale, noisy PNG over the size ceiling, released source bitmaps, invalid options
and missing consent. Real capture evidence is maintained in the MCP Server plugin repository
under `docs/dev/p3-screen-evidence.md`.
