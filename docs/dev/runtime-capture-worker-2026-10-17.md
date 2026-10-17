# Bun screen capture and Node Worker host modules

Date: 2026-10-17 (Asia/Taipei). This batch closes the screenshot/consent gap in Bun
and the OpenCC/image facade gap in Node Workers. These are coordinated development
sources and locally tested APKs, not published releases or complete Rhino parity.

## Delivered APIs

- Bun `host.images.requestScreenCapture(options?)`, `captureScreen()` and
  `stopScreenCapture()` use the separate `screen.capture` grant, default off.
  The switch permits requesting Android consent; it never replaces that consent.
  Grant changes apply to the next execution, like the existing Bun grants.
- Consent uses a start request and bounded status calls. A status call waits at most
  one second, so the existing 10-second transport budget stays intact. Consent has
  a configurable 1..120000 ms budget, still bounded by the overall script timeout.
  Bun's default execution timeout is 60 seconds; callers may configure
  `bun.timeoutMillis` when a longer execution is needed.
- Capture returns an execution-owned image handle. Processing and file access keep
  their existing separate grants and JSON limits. Stop cancels pending consent or
  releases the active projection; execution exit also cleans up. This delivers
  screen frames, not video encoding or a recorded video file.
- Node Worker CJS, ESM and nested Workers can import `opencc`/`autojs6:opencc`,
  `image`/`images` and their canonical `autojs6:` aliases. All 33 OpenCC members and
  41 image functions reuse the main-thread public wrappers. Bare `opencc` retains
  npm precedence. Text is stringified before structured clone to preserve Buffer
  and custom `toString` behavior.
- Worker uploads use the parent's existing PFD path. Returned bytes are copied by
  structured clone and become independent Buffers. Metadata helpers remain
  synchronous; recycle bookkeeping is local to a thread. Deliberately transferred
  image handles belong to the execution and survive their creating Worker's exit.
  Undeliverable late images are recycled, and Worker termination cancels its
  pending screen consent. Other high-level host modules are not implicitly added.

## Problems corrected during real-device validation

1. `ScreenCaptureRequester` now associates its own proxy Activity through its
   direct callback. Merely stopping/covering that Activity is not cancellation.
   Explicit cancellation finishes the system child activity as well as the proxy,
   preventing consent dialogs from remaining after timeout or execution exit.
2. Node/Bun request the default display on Android 14+, avoiding the unsupported
   single-app selection flow for their full-screen capture contract. Other callers
   of the existing no-argument requester constructor retain their selection mode.
3. Explicit dimensions use the capturer's fixed-size mode. Display-change events
   no longer replace fixed dimensions or orientation with automatic dimensions.
4. The producer Surface is detached before an old ImageReader is closed. This
   fixes the no-frame state reproduced on the Redmi after rotation. Redundant
   immediate refresh after a size correction is also avoided.

The device fixture enables view IDs for UiAutomation, handles the observed Material
consent buttons, and uses an owned color Activity that survives rotation. Those
are test-harness changes, not additional runtime features.

## Verification

| Physical device | Host suite | Screen sizes checked |
| --- | --- | --- |
| Redmi 22120RN86C, Android 13/API 33, arm64, 4096-byte pages | 5/5, 44.587 s | 720x1650, 1650x720, fixed 320x180 |
| Xiaomi 23046RP50C, Android 15/API 35, arm64, 4096-byte pages | 5/5, 37.760 s | 1800x2880, 2880x1800, fixed 320x180 |

The host suite validates a real consent decision after an intentional 11-second
wait, captured center pixels from the owned color surface, rotation during one
capture session, fixed-size reauthorization, stop/repeated stop, denied and expired
consent, cancellation of the execution or Worker, and grant rejection including
raw-call bypass attempts. Worker cases use real OpenCC providers and encoded image
uploads above 64 KiB, Buffer output, full ARGB arrays, and handle handoff to the parent.

Additional device results: Node npm/conformance/WorkerDesktop 44/44 on the Redmi;
Bun runtime 13/13 and Ace 4/4 on the Xiaomi. Total: **71 passed physical tests**.
Ace's new screenshot-consent popup was visually reviewed and exposes
`Promise<boolean>` without clipping or overlapping the completion list.

Local checks include the current NDK syntax command, Node/Bun builds and JVM
tests, actual Worker preload/wrapper tests, 17 Bun SDK tests, TypeScript 5.1.3 and
6.0.3 declaration checks, 68 copied declaration files, and 186 Ace runtime queries.
The full host gate remains 91 mounts, 1351 exports and 1522 queried paths with zero
failures. Those host counts measure documentation/types/completion, not Node/Bun
implementation parity. Documentation generation covers 163 modules.

The host's cross-language changelog source count check still reports unrelated
draft differences. The new entries are present in all ten languages, and all 22
generated host Markdown files match their current source JSON.

## Delivery and retained evidence

TypeScript Declarations is 4.47.0; Ace is 2.9.0. Node/Bun source declarations,
the online guide and Offline Docs are synchronized. Existing published snapshots
are not changed and nothing is pushed or published.

The shared host workspace contains unrelated Explorer/editor changes. Builds use
the existing ignored `.bak/oi-check` directory, restored from the committed base
and overlaid with this batch's files. Only the API/capture changes and their own
changelog hunks are staged. The existing build directory is retained; this batch
does not retry the prior cleanup action rejected by automatic approval review.

Logs, APK hashes, device JSON and screenshots are retained under ignored
`.bak/capture-worker-20261017` directories. Installed host/runtime APK hashes match
the tested build outputs. Both devices' original installed APK bytes and rotation
settings are restored, temporary test-only packages are removed, and test grant
preferences are restored. The final arm64 host APK and its matching test APK are
retained separately from the disposable restoration backups.

Remaining audit work includes the other Worker host facades, advanced OpenCV,
Android-object helpers, calendar/IntentTask, UI/Compose, AI/flow/TTS/mail/EPUB and
other large surfaces. Native alternatives and Rhino delegation remain distinct
from direct support.
