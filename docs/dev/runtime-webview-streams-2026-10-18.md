# WebView streaming media delivery - 2026-10-18

This batch makes page-native getUserMedia/MediaRecorder usable from Node.js, Bun and Node.js Workers. It does not add DOM globals to either runtime or claim complete runtime API parity.

## Delivered behavior

- `NativeViewHandle.evaluateJavascriptAsync(source, options?)` awaits the current page's expression/Promise and returns JSON. Undefined becomes null. Page rejection and serialization failures preserve the error name/message. Default timeout is 30000 ms, range 100..2147483647. Ordinary `evaluateJavascript` keeps its existing synchronous-result semantics.
- `onPermissionRequest` accepts `{ resources, requestAndroidPermissions: true }` after the script checks origin. Only requested resources can be selected. Missing CAMERA/RECORD_AUDIO permissions are requested through Android; denial, undeclared permissions, client replacement, page navigation and View disposal are handled. Array replies keep their existing already-granted-permissions behavior.
- Actual page MediaStream/MediaRecorder objects stay in WebView. The script controls capture constraints, track enablement, pause/resume/requestData/stop and consumes recording blobs through download/readDownload. Downloads use existing bounded 32768-byte reads and optional SAF output destinations. A detached UI task does not hold Bun's 10-second RPC open.
- Node CJS, ESM import, CJS/ESM/nested Workers and Bun share the new facade. Worker creation/cancellation tracking includes asynchronous evaluation tasks. A Worker closes its own windows on exit. Closing a window containing a media-authorized WebView navigates that page to about:blank, releasing its camera/microphone while preserving the reusable View token. Reload before reattaching it; a borrowed parent window is not closed.
- APK Builder 1.2.6 retains MODIFY_AUDIO_SETTINGS whenever RECORD_AUDIO is selected. Chromium needs both for microphone capture. The normal permission does not replace the user's microphone grant, and CAMERA is not added implicitly.

## Versions and synchronization

| Component | Candidate |
| --- | --- |
| Host | 6.8.0, VERSION_BUILD 3916 + offset 1652 = Android 5568 |
| Node.js Runtime | 1.14.0, build 262 |
| Bun Runtime | 0.2.13, build 260 |
| TypeScript declarations | 4.59.0 |
| Ace | 2.21.0, build 296 |
| Documentation | 6.8.0, revision 136 |
| Offline Docs | 6.8.16, build 167 |
| APK Builder | 1.2.6, build 81; host 5568 |

Documentation source: `6d23c2584c72589d768dd40fe08fef689bb20a06`. Both Offline Docs provenance files pin that commit. The real runtime kit is `autojs6-runtime-v6.8.0+5568-a2957018dad223ae69357556012ba9435a18304a-arm64-v8a`: the SHA identifies the real pre-change host source base plus this batch's reviewed changes. All six accepted packaged APK DEX sets match that template exactly; signatures were verified and differ from the host signature.

Ten-language release notes, generated histories, Node/Bun canonical declarations, DTS runtime copies and Ace's bundled declaration/index assets were synchronized. No publication, push, release or registry/catalog deployment was performed.

## Verification

Two physical ARM64 devices, both 4096-byte pages:

| Device | Android | WebView |
| --- | --- | --- |
| Redmi 22120RN86C, bek749scrwv4wo8h | 13 / API 33 | 111.0.5563.116 |
| Xiaomi 23046RP50C, 968e9f18 | 15 / API 35 | 130.0.6723.86 |

The main accepted device matrix contains 169 JUnit executions, plus two focused repeats strengthening the non-enumerable Promise-result-slot cleanup assertion. The four stream tests on each phone include six runtime modes for awaited JavaScript and six for live audio/video recording, plus explicit web denial/invalid constraints and nested Worker microphone release while its parent remains active. Android permission grant and denial each passed separately for Node, nested Worker and Bun on both phones, with permissions revoked before every run.

Recordings are actual WebM VP8/Opus from the camera/microphone. Two chunks are downloaded and read before recording stops. Tests exercise pause/resume/requestData, wait for the final dataavailable/stop sequence, compare uploaded file SHA-256 with the page Blob, read downloaded bytes, decode a VP8 frame and decode Opus PCM containing the owned 660 Hz tone. Media bytes and camera frames remain on-device; only hashes, byte counts, dimensions and audio measurements are collected.

Six genuine APK Builder outputs (Node, nested Worker and Bun on each device) passed creation and installed execution. Each output validates its application/runtime identity, live media path and output, then Compose state/clicks across rotation. The final Builder manifest assertion checks CAMERA, RECORD_AUDIO and the automatically retained MODIFY_AUDIO_SETTINGS.

Existing regression groups: Node npm/conformance/Worker 44 tests per phone, Bun 13 per phone, WebView callbacks 7 per phone and two blob/data download tests per phone. Ace passed all five physical coverage tests under its normal/default provider settings, including the new Promise API and explicit permission response popups, existing API hints and JSOX. Ace restored its preferences and rotation.

Desktop checks passed: shared Node/Bun facade synchronization and behavioral fixtures, CJS/ESM/nested Worker pending-task cancellation, 40 callback names and 12 public WebView methods, TS 5.1.3 and 6.0.3 strict declaration checks (including negative cases), 70 synchronized runtime assets, 392 Ace semantic queries, 27 Bun client tests, Node/Bun Python checks, Bun compatibility-matrix freshness, Markdown generation checks and full 163-module documentation freshness. C++ syntax used the current NDK compile command. Host/runtime/Ace/Builder builds, the actual runtime kit, and both Offline Docs APK inventories passed.

| JVM suite | Tests | Skipped |
| --- | ---: | ---: |
| AutoJs6 | 4717 | 26 |
| AutoJs6-Plugin-NodeJs-Runtime | 96 | 0 |
| AutoJs6-Plugin-Bun-Runtime | 59 | 0 |
| AutoJs6-Plugin-Ace-Editor | 187 | 0 |
| AutoJs6-Plugin-APK-Builder-Template | 40 | 0 |
| AutoJs6-Plugin-Offline-Docs | 2 | 0 |

Total: 5101 discovered, 5075 executed successfully, 26 skipped, no failures/errors. The new Builder test verifies permission dependency preservation and absence of implicit camera/microphone grants when not selected.

## Failures found during validation

1. The first navigation cleanup cancelled legacy synchronous evaluations during initial loading. Cleanup was narrowed to the new awaited evaluations and pending media requests. The new matrix and existing callback regressions then passed.
2. Closing a Worker's presentation left an execution-owned WebView alive and still capturing audio. Media-authorized pages now navigate to about:blank when their presentation closes. Both devices verify microphone release while the parent script/window is still alive.
3. Android MediaMetadataRetriever could not seek a streaming WebM without conventional seek metadata. Verification now feeds the actual VP8 stream into MediaCodec and checks decoded frame output; it also decodes Opus PCM. No media assertion was replaced by a success flag.
4. The first genuine packaged Node output failed with `NotReadableError`. Chromium logged missing MODIFY_AUDIO_SETTINGS even though CAMERA/RECORD_AUDIO had been granted. The Builder dependency fix and its binary-manifest unit test address the cause; all six rebuilt outputs passed. The failed APK/log are retained separately and are not accepted artifacts.
5. One Redmi legacy multiple-file-selection assertion failed with its useful message lost behind an encoded Worker data-URL stack. The assertion now preserves its concise message. The focused six-mode chooser rerun and subsequent complete callback group passed with unchanged production behavior; this UI-selection failure was not reproduced or represented as a new runtime fix.

## Boundaries and cleanup

The tests use trusted loopback pages. Real sites still require a secure context, their own origin decision and supported WebView codecs. This batch does not guarantee background capture, remote WebRTC calls, getDisplayMedia, every codec or 16 KiB device behavior. Generic Promise timeout cancels result delivery, not arbitrary already-running page code; stop tracks/recorders explicitly or dispose the owned WebView. A caller retaining recording blobs must bound memory by draining/releasing them.

Original installed APK bytes, CAMERA/RECORD_AUDIO grants and flags, volume and rotation were restored on both devices. Task-only apps were removed. Test downloads and generated APKs on the phones were cleaned, and the previous SDK fixture file was restored. Original APK backup copies were removed only after byte-for-byte restoration checks. The existing isolated build checkout and its local signing files were preserved. Host's five original user drafts remain unchanged relative to the initial diff.

Reproducible logs, accepted/failed APK distinctions, hashes, device receipts, signatures and restoration records are under the ignored `.bak/webview-streams-20261018/` workspace. Repository API entry points and device tests are tracked; temporary test outputs stay in `.bak`.

References: [Android PermissionRequest](https://developer.android.com/reference/android/webkit/PermissionRequest), [Media Capture and Streams](https://www.w3.org/TR/mediacapture-streams/), [MediaStream Recording](https://www.w3.org/TR/mediastream-recording/), [Chromium audio permission checks](https://chromium.googlesource.com/chromium/src/media/+/master/base/android/java/src/org/chromium/media/AudioManagerAndroid.java).
