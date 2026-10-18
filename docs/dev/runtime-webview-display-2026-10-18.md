# WebView display sharing and background recording - 2026-10-18

This batch delivers screen sharing through an explicit WebView adapter, background web microphone capture, and the native Camera2/MediaRecorder background video path approved by the user after both WebView providers muted hidden camera tracks. It does not claim native WebView getDisplayMedia support or complete browser API equivalence.

## Delivered APIs

- `enableDisplayCapture({origin,maxWidth,maxHeight,frameRate,timeoutMs})` installs a getDisplayMedia adapter only when the page has no native method. The page must be secure, match the configured origin, and receive a visible-page user gesture. Every request gets fresh Android MediaProjection consent. The adapter supplies real canvas MediaStream video from MediaProjection frames, with one image in flight. It returns no system-audio track. Defaults are 1280 x 720 bounds, at most 5 fps and a 120000 ms consent timeout; rotation is letterboxed into the initial canvas dimensions.
- `disableDisplayCapture(id?)` cancels consent/capture and removes the matching page installation. Last-track/clone stop, Android projection revocation, navigation and window/View teardown release capture. Worker cleanup compares registration IDs before removing a replacement.
- `WebPermissionResponse.backgroundCapture` explicitly starts the appropriate camera/microphone foreground service while visible. The notification has a stop action. Web microphone recording continues while another app is visible. This service does not override Chromium's hidden-camera policy.
- `startBackgroundRecording(options?)`, `getBackgroundRecording()`, `stopBackgroundRecording()` and `cancelBackgroundRecording(id?)` provide native Camera2/MediaRecorder H.264/MP4, optionally AAC microphone audio. Start requires a visible WebView and declared/granted Android permissions. Options include native camera ID or facing, supported-size/rate preferences, bitrate and duration/size limits. Stops and limits return a temporary content URI; read/copy it before disposing the View. Notification stop finalizes the native file; cancellation/window teardown discards incomplete output.
- `stopMediaCapture()` cancels active media work, releases foreground leases and navigates to about:blank. Save page MediaRecorder data first. Native recording is independent of page camera MediaStream objects and continues through page navigation while the View/window remains owned.
- Node main-thread CJS/ESM imports, CJS/ESM/nested Workers and Bun expose the same facade. Native recording IDs protect later replacements during Worker cleanup. Native View event subscriptions now deliver independent replies to parent and Worker readers; exiting Workers release their subscriptions. Older hosts can fall back to the legacy single-reader protocol for pre-existing APIs.
- APK Builder retains foreground-service dependencies for selected CAMERA/RECORD_AUDIO and explicit FOREGROUND_SERVICE_MEDIA_PROJECTION. These normal permissions do not replace runtime camera/microphone decisions or screen-sharing consent.

## Candidate versions

| Component | Version |
| --- | --- |
| Host | 6.8.0, VERSION_BUILD 3917 + offset 1652 = Android 5569 |
| Node.js Runtime | 1.15.0, build 263 |
| Bun Runtime | 0.2.14, build 261 |
| Declarations | 4.60.0 |
| Ace | 2.22.0, build 297 |
| Documentation | 6.8.0, revision 137 |
| Offline Docs | 6.8.17, build 168 |
| APK Builder | 1.2.7, build 82; host 5569 |

Canonical Node/Bun declarations, 70 DTS/Ace runtime assets, completion indexes, documentation/search output and ten-language release histories were synchronized. Offline provenance pins Documentation commit `89a7719a3a6ff7ea4b241cd1eb6779b7796d5a54`. No push, publication, registry upload or runtime/catalog deployment was performed.

## Physical verification

| Device | Android | WebView | ABI / page size |
| --- | --- | --- | --- |
| Redmi 22120RN86C, bek749scrwv4wo8h | 13 / API 33 | 111.0.5563.116 | ARM64 / 4096 |
| Xiaomi 23046RP50C, 968e9f18 | 15 / API 35 | 130.0.6723.86 | ARM64 / 4096 |

The accepted matrix contains 177 JUnit executions. Each phone exercised screen sharing, web audio and native camera recording in six runtime modes. A separate activity in the test APK's distinct UID covered the recorded app; document.hidden was checked during the background interval. The screen recording decodes alternating red/green frames from that owned fixture, proving updated screen content. Audio and native video samples span the background interval; audio decoding detects the owned 660 Hz fixture tone. File hashes, dimensions, timestamps and byte counts are retained; camera frames and microphone recordings stay on the phones.

Lifecycle tests cover denial followed by fresh consent, track clones, pending-consent cancellation, native duration limits, cancellation completion, notification stop with valid MP4 output, and Worker recording cleanup without destroying the borrowed parent View or a later replacement. Camera/microphone grants and denials were tested separately for Node, nested Worker and Bun with permissions revoked before each run.

After the user reported disruptive continuous sound, the new background-media fixtures were changed to a bounded 700 ms acoustic pulse at approximately 1/15 of the media-volume scale. It plays only after the background fixture is visible; display/Compose checks play no tone. A playback-thread deadline stops the pulse even when the test is waiting or fails, and cleanup restores the previous volume. The microphone level, 660 Hz and decoded-duration assertions remain unchanged. 6 supplemental tests passed: nested-Worker web audio, Node native camera, and installed Bun output on each phone. Their receipts and media metadata are separate from the full matrix above. Redmi uses index 1/15; Xiaomi uses 10/150. An initial raw index 1 on Xiaomi was too quiet and failed the acoustic assertion; the volume was normalized instead of weakening that assertion. Older playback latency/policy fixtures retain their separately calibrated tone behavior.

Six actual APK Builder outputs passed creation and installed execution: Node, nested Worker and Bun on both devices. Every output runs all three media paths while another UID is foreground, validates media data and identity, then checks Compose state/clicks across rotation. All accepted packaged DEX sets match the actual runtime template, and their verified signing certificates differ from the host certificate.

Existing regressions passed: Node npm/conformance/Worker 44 cases per device, Bun 13 per device, WebView client callbacks 7 per device, and the previous streaming-media group 4 per device. Ace's five physical coverage tests passed with its normal/default settings, new display/native-camera popups, option completions and JSOX, restoring preferences and rotation.

Desktop checks passed: 40 callback names and 19 public WebView methods, page adapter activation/origin/constraint/clone/backpressure/cancellation fixtures, shared Node/Bun facade checks, CJS/ESM/nested Worker ownership and event-reader cleanup, TS 5.1.3/6.0.3 strict positive/negative smoke cases, 402 Ace semantic queries, 27 Bun client tests, Python checks and the unchanged Bun compatibility matrix. Host/runtime/Ace/Builder assemblies, the real runtime kit and both Offline Docs APK inventories passed.

| JVM suite | Tests | Skipped |
| --- | ---: | ---: |
| AutoJs6 | 4718 | 26 |
| AutoJs6-Plugin-NodeJs-Runtime | 96 | 0 |
| AutoJs6-Plugin-Bun-Runtime | 59 | 0 |
| AutoJs6-Plugin-Ace-Editor | 187 | 0 |
| AutoJs6-Plugin-APK-Builder-Template | 40 | 0 |
| AutoJs6-Plugin-Offline-Docs | 2 | 0 |

Total: 5102 discovered, 5076 passed, 26 skipped; zero failures/errors.

## Findings and repairs

1. Both providers mute native getUserMedia camera video when the page is hidden despite a foreground service. This is retained as a platform boundary. The user explicitly approved the separate native file-recorder path, which was implemented and tested.
2. The standalone test-APK activity initially referenced Kotlin classes only present in the target APK. It was replaced with a framework-only Java activity in its own UID. Its button's theme capitalization was also made explicit. These were fixture failures, not background-capture acceptance.
3. Two readers of the same native View previously competed for a single event queue, making borrowed Worker handles appear closed. Explicit per-wrapper subscriptions, registration before asynchronous task submission and Worker-exit cleanup now deliver each reader's responses independently. Legacy-host fallback is retained.
4. A fixed-delay clone assertion was unsuitable for a static display and device-dependent encoding. The fixture now changes actual visible pixels and waits for encoded clone frames under the existing deadline, retaining the live-track assertion.
5. Native camera startup captures its successful start snapshot before posting it, distinguishes pending/active recording cancellation IDs, closes late-opened cameras after cancellation, and preserves new foreground leases during an old idle service instance's destruction.

## Build scope, boundaries and cleanup

The final kit uses verified source base `b496e1f521be16849c91e968fd02af81d0da0c7f` plus this batch's owned media changes. During work an independent Agent fix was committed and Shell/Shizuku changes appeared in the shared checkout. The Agent fix at that baseline is included; uncommitted parallel files were excluded. Subsequent unrelated Agent commits in the shared checkout are not part of this candidate build. The isolated build was cleaned and rebuilt after removing copied parallel sources. Shared localized resources retain both baseline Agent changes and media entries. The original user drafts and unrelated working files were preserved and are outside the media commit.

The adapter uses data: JPEG images, so page CSP must permit them. It does not replace an existing native getDisplayMedia, supply system playback audio, guarantee high frame rates or full WebRTC-call compatibility, or bypass protected-screen/lock/system/OEM restrictions. Background recording means a user-started foreground service continuing while another app is visible, not boot-time capture or recovery after force-stop. Existing while-in-use permissions and per-session projection consent remain authoritative.

Original APK hashes, CAMERA/RECORD_AUDIO grants/flags, volume and rotation were restored on both devices. Task-only apps and test-generated device APKs/downloads were removed; the previous SDK fixture file was restored. Verified original backup copies were removed after restoration. The existing isolated checkout and its signing/local files were preserved. Logs, receipts, tested APKs, signature/DEX checks and failure evidence remain in ignored `.bak/webview-display-20261018/` folders.

References: [Screen Capture](https://www.w3.org/TR/screen-capture/), [Android foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [Android 11 camera/microphone foreground-service rules](https://developer.android.com/about/versions/11/privacy/foreground-services).
