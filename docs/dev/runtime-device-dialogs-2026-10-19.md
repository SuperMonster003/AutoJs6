# Runtime device controls and dialogs (2026-10-19)

## Delivered behavior

Bun now exposes device controls through host.device and Activity-backed dialogs through host.dialogs. Node main threads, CJS/ESM Workers and nested Workers share the same device/dialog factories. The Bun SDK preserves its original small device.info result; device.getInfo returns the full live snapshot in both runtimes. Bun uses async getters rather than Node's synchronous startup metadata.

Device methods cover live screen/build/battery/memory information, brightness and brightness mode, timed screen wake locks, waking the screen, bounded vibration, music/notification/alarm volume and battery-optimization settings. Bun grants device.control, device.power, device.audio and dialogs.control are independent and default off. Android WRITE_SETTINGS remains separate special access. The actual APK builder derives WAKE_LOCK, VIBRATE, WRITE_SETTINGS and MODIFY_AUDIO_SETTINGS from project capabilities.

Dialogs include alert, confirm, input/prompt/rawInput, select, singleChoice, multiChoice and progress.show/update/dismiss. Input is text, without evaluation. Optional message/prefill overloads and neutral button cancellation are reflected in declarations and native behavior. Progress handles now reflect successful handle updates instead of retaining their initial state.

Each client owns its timed wake locks and dialog windows. Worker exit closes its resources while preserving the parent's. Closed scopes reject late creation; timed-out or aborted dialog requests are cancelled before or after Activity attachment. Bun raw calls are cancelled by native request ID, so two tokenless progress calls cannot cancel each other. Quota exhaustion also closes device/dialog resources. Bun's native runtime bytes and frozen Android/ABI compatibility matrix are unchanged.

## Physical validation

130 accepted JUnit cases. Superseded attempts and failures are retained separately and excluded from the total.

| Device | Phase | Cases | Seconds |
| --- | --- | --- | --- |
| redmi | node-host | 5 | 2.737 |
| redmi | host | 5 | 32.428 |
| redmi | node | 34 | 110.902 |
| redmi | bun | 13 | 28.726 |
| redmi | produce | 1 | 39.203 |
| redmi | packaged | 1 | 36.686 |
| xiaomi | node-host | 5 | 2.014 |
| xiaomi | host | 5 | 28.917 |
| xiaomi | node | 34 | 38.558 |
| xiaomi | bun | 13 | 24.879 |
| xiaomi | produce | 1 | 16.104 |
| xiaomi | packaged | 1 | 44.7 |
| xiaomi | ace | 12 | 212.521 |

Redmi 22120RN86C (bek749scrwv4wo8h) ran Android 13/API 33; Xiaomi 23046RP50C (968e9f18) ran Android 15/API 35. Both are native arm64 with 4096-byte pages. Real host tests exercised all live getter families, brightness writes with restoration, unchanged music-volume writes, battery settings dry-run, screen wake locks and the full dialog sequence. UI automation entered text, pressed positive/neutral/cancel buttons, selected actual list rows and checked timeout/abort cleanup. CJS/ESM/nested Workers also preserved the parent's progress window while their own windows closed.

Native tests directly checked independent timed wake locks, rejection of late scope creation, cancellation before Activity creation, cross-client progress rejection, raw Bun permission enforcement, abandoned resource cleanup and exact cancellation of one of two tokenless Bun dialogs.

Each phone generated and installed one Bun and one Worker application using the genuine Runtime Kit/Builder. Each application ran twice with a force-stop between runs. First-round WRITE_SETTINGS denial produced permission-denied behavior; second-round explicit app-op authorization allowed a real brightness write and restoration. All four manifests contain the four derived device permissions. Each result identifies its own package and runtime build 5585. Runtime DEX hashes match the paired Kit template, and each generated application's signing certificate differs from the host certificate.

The test programs never play audio. Media volume stayed at its original value of 0 on both phones; notification/alarm levels were read without changing them. Vibration used duration 0. No sustained tone or recording/audio stimulus was used.

## Retained failures and limits

The first native cleanup assertions referenced a diagnostic key that had not been emitted; the provider now reports its actual active wake-lock count, and the complete native suites pass. This was an assertion/diagnostic mismatch, not evidence that two native locks failed to acquire.

The first Redmi packaged run timed out before the owned entry field was visible, with an empty UI snapshot. The exact cause was not established. A complete rerun with the final permission assertions passed all four application runs; the failed attempt remains archived and is not counted as accepted evidence. Xiaomi's final complete packaged run also passed. Tests do not claim universal OEM background-launch reliability.

Brightness writes still follow the existing Node 0..255 input contract and require Android's modify-system-settings access. Other vendors may expose different raw brightness ranges. Waking an already-on test screen and duration-0 vibration were exercised; this batch does not claim a separate physical lock-screen transition or audible/haptic test. Android background Activity restrictions still apply. These portable APIs do not implement every Rhino/Java device helper or the full Rhino dialog-builder object model.

## Synchronized checks

- 5110 JVM cases discovered: 5084 passed, 26 existing skips, no failures/errors.
- 42 Node facade/real desktop Worker tests and the standalone Worker host-module regression passed. All 58 Bun SDK tests passed. Node/Bun Python suites passed 2/38 cases; API extractor regression passed 8 cases.
- TypeScript 5.1.3 and 6.0.3 smoke checks passed with 70 synchronized runtime declaration files, including readonly progress states, overloads, grant IDs and Node aliases.
- Ace runtime coverage passed 493 queries; core/default/JSOX coverage passed 268 queries. The actual device suite passed 12 cases, with default popup screenshots for Node device.getWidth, dialogs.confirm and Bun progress.dismiss. Screenshots were visually inspected.
- The focused gate matches 11 native device routes, 3 volume routes, 12 dialog routes and shared factories/types. The full host documentation/type/Ace gate remains at 1356 explicit exports and 1589 paths with no failures. Those numbers are coverage-check scope, not fully implemented Node/Bun API counts.
- Current NDK C++ syntax, relevant Android assembly/JVM tests, Node/Bun/Ace lint, Bun native integrity, real Kit/Builder assembly and offline debug/release APK verification passed. Documentation contains 164 generated modules; the offline APK gate verifies 220 files. Ten-language notes and four new localized grant switches are synchronized.

## Versions, source and restoration

Host 6.8.0 / Android build 5585; Node Runtime 1.20.0 / build 268; Bun Runtime 0.9.0 / build 268; declarations 4.67.0; Ace 2.29.0 / build 305; Offline Docs 6.8.25 / build 182; Builder 1.2.14 / repository build 89 paired to host 5585; Documentation revision 146.

Kit: `autojs6-runtime-v6.8.0+5585-27c2cddc7b66ade945399243b097d623b50a3a93-arm64-v8a`. Its source basis is host 27c2cddc7b66ade945399243b097d623b50a3a93 plus this batch's changes and the pre-existing shared-checkout edits. The source-base identifier is not presented as the final content commit; exact tested APK hashes and receipts are archived. Four unrelated CircularMenu production/test files and five release-draft files existed before this batch and are excluded from its commits.

Documentation source `c87563910897f1c2cd207a52a281c1343c029b7a` is pinned in both Offline Docs provenance files. All work remains local, without push, catalog upload or public release. Evidence, exact APKs and superseded attempts are in ignored `.bak/runtime-device-dialogs-20261019/`.

Both phones' original APK hashes, camera/audio/notification permission flags, notification-listener access, original accessibility lists, brightness/mode/timeout, modify-settings app-op, music/notification/alarm volume and rotation were checked after restoration. Redmi's originally enabled host accessibility service is bound; Xiaomi's originally disabled service remains disabled. Four task-created packaged applications and owned SDK/proof files were removed. All 13 fresh original-APK backups were deleted only after exact restored-hash verification. Five desktop Worker fixture directories were archived under the Node repository's ignored .bak; pre-existing build/worker-host-tests was preserved.

Reproduce with tools/api-coverage/check-runtime-device-dialogs.mjs, Node test-device-dialog-facades.js/test-worker-device-dialogs.js, Bun device-dialogs.test.mjs, declaration smoke checks and Ace runtime/default coverage. Physical suites are RuntimeDeviceDialogsNativeTest, RuntimeDeviceDialogsDeviceTest and RuntimeDeviceDialogsPackagedDeviceTest.

Remaining audit areas include Android-object helpers, calendar/IntentTask scheduling, dedicated AI/flow/TTS/mail/EPUB and other plugin surfaces, plus APIs whose Rhino/Java object model has no portable equivalent. This batch completes device/dialog parity with the current Node portable surface, with the documented Bun startup-metadata difference; it does not establish full Rhino parity.

The [Android object and persistent task delivery](runtime-android-tasks-2026-10-19.md) adds Node/Bun/Worker native object helpers, calendar/IntentTask APIs, scoped object cleanup, persistent registrations, DST-correct recurrence, filtered receiver lifecycle, queued Bun host calls and actual host/packaged validation on Android 13/15.
