# Node.js and Bun OpenCC and common image delivery

Date: 2026-10-17 (Asia/Taipei). This is the second batch following the runtime API audit.
It covers the matching development host and runtime/editor plugins, not a public release
or a claim that Node/Bun implement every Rhino API.

## Delivered behavior

- Node `autojs6:opencc` and Bun `host.opencc` are callable and provide `convert` plus
  all 32 named conversions. Native conversions, aliases and composite chains use
  the installed OpenCC provider and the host's shared conversion plan. Empty text
  is valid, nullish text is rejected, and provider failures remain failures. The
  bare Node `opencc` name can still be shadowed by an installed npm package.
- Common images cover encoded bytes/Base64, ARGB pixels and regions, independent
  copies, crop/resize/scale/rotation/flip, grayscale/threshold/blur/Gaussian/median/
  invert, and color searches. Node exposes 41 image functions including existing
  methods and helpers; Bun exposes 30. This is not the advanced Rhino OpenCV surface.
- Derived images keep the source usable, including identity transforms. Handles
  belong to one execution and are recycled explicitly or at execution cleanup.
  Node accepts signed ARGB colors returned by `pixel`, and shares recycle bookkeeping
  between `image` and `images`. Raw bridge recycling is outside that local bookkeeping.
- Device validation caught incorrect RGB inversion of half-transparent pixels:
  the original conversion inverted Android's premultiplied channels. Inversion now
  unpremultiplies first and premultiplies when writing back. The regression asserts
  `0x80ff0000 -> 0x8000ffff`, not just unchanged alpha or output dimensions.
- Node encoded uploads and binary output use PFD transport. `readPixels` converts
  tightly packed straight-alpha RGBA8 into row-major signed ARGB32 values.
  Bun uses encoded Base64 JSON, with the existing 64 KiB request and 256 KiB result
  budgets. It has no PFD, raw RGBA output or screen capture in this batch.
- Bun has separate `text.opencc`, `image.process` and `image.files` grants. The first
  two default on; file access defaults off. Plugin Center has localized switches.
  Memory-image permission cannot be used to bypass the file grant.
- Raw JSON pixel regions are limited to 16,384 pixels; Node's public binary pixel
  reader has no such JSON cap. Color-result arrays are limited to 4,096 points in
  both runtimes. Excess results are rejected before boxed result allocation, never
  silently truncated. Returned points use image coordinates.
- Bitmap-only codecs, pixel reads, copy/crop/rotate/flip and `detectsColor` do not
  require OpenCV. Resizing, filters, template matching and color searches do.
  Color detection offers diff/rgb/rgb+/hs/h/equal/oklab; searches use opaque pixels
  and per-channel RGB thresholds.

Worker execution retains its documented `autojs6:bridge` parent-message route.
It does not automatically gain the main-thread high-level modules. Native Node CLI
processes also do not automatically receive the script bootstrap.

## Coverage and validation

Physical device: Sony XQ-DQ72, `QV770340J7`, Android 13/API 33, native arm64-v8a,
4096-byte pages. The current Three-Hanzi-CC and OpenCV providers were freshly built.

| Check | Result |
| --- | --- |
| Node embedded-wrapper regression | Real OpenCC dispatch, upload release on success/failure, byte offsets, ARGB decode, signed colors and shared recycle tracking pass |
| Bun SDK | 15 tests and strict TypeScript fixture pass |
| Runtime declaration checks | 68 synchronized files; OpenCC 33 members each; image 41/30; no any/never image results |
| Ace desktop checks | 174 runtime queries across 11 routes; 226 Rhino/JSOX queries |
| Cross-runtime source gate | OpenCC registrations/plans/client aliases and dispatch match; image routes, helpers and development catalog match |
| Full host coverage | 91 mounts, 1351 exports and 1522 queried paths, zero failures; 8 parser tests |
| Host OpenCC/image/transport/Worker instrumentation | 4 tests pass on the final isolated build in 4.086 s, including exact half-alpha inversion |
| Node npm ecosystem and complete Android conformance | 34 tests pass on device |
| Bun complete runtime instrumentation | 13 tests pass on device, including lifecycle reaping and SDK bridge |
| Ace default/runtime/JSOX instrumentation | 4 tests pass on device; OpenCC and readPixels popups visually reviewed |
| Plugin JVM tests | Node 96, Bun 59, Offline Docs 2; no failures or skips |

`OpenccImageBridgeDeviceTest` runs all 32 conversions through real CJS, ESM and Bun
processes. Image cases assert signed and half-alpha pixels, ROI ordering, rotated
and flipped pixels, independent recycling, PNG/JPEG/WebP decode, host file round
trips, large descriptor uploads, full binary pixel reads, bounded JSON, grant
revocation and stale handles. The large PNG uses a fixed random seed so compression
cannot accidentally avoid the transport budgets. The Worker case uses its public
generic bridge rather than assuming the main-thread facade exists there.

Host builds were first checked in the shared workspace. During final validation,
unrelated ongoing Explorer edits temporarily failed compilation. A detached build
checkout based on `8dd810d02` isolates this batch's host sources without changing
those edits. This distinction is retained in the build evidence.

Documentation sources and generated HTML/JSON/search index are synchronized with
Offline Docs. Runtime source declarations are synchronized with TypeScript
Declarations 4.46.0 and Ace 2.8.0. The ten-language changes are generated from JSON.
The host's pre-existing cross-language changelog count mismatch is not part of this
batch; generated-file consistency is checked separately.

Detailed build/device logs, screenshots and installation/restoration records are
retained in the repositories' ignored `.bak/opencc-images-20261017` directories.
No APK, private backup, signing material or generated test report is committed.
The final tested arm64 host APK and matching instrumentation APK are retained under
the host evidence directory's `artifacts/` folder; temporary build checkout and
restoration APK backups are removed after verification.
The installed host/runtime/editor/provider APK hashes match the tested build files.
The final physical suites total 55 passed tests. Original installed APK bytes and
rotation settings are restored, and packages introduced only for this batch are
removed. Each host test restores the Bun grant preferences it changed.

## Remaining boundaries

Advanced feature extraction/matching and the rest of Rhino's image/OpenCV API,
Bun screen capture, high-level Worker facade parity, Android-object helpers,
calendar/IntentTask, UI/Compose and the other large runtime surfaces remain
separate audit items. Direct implementations, native alternatives and Rhino
delegation must continue to be counted separately.
