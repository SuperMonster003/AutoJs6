# Runtime Android objects and persistent tasks (2026-10-19)

## Delivered APIs

Node main threads, CJS/ESM Workers, nested Workers and Bun share Android-object, color and task facades. Native Paint handles support color changes, real text measurement, snapshots and close. Native ColorStateList handles support named/numeric states, negative states, matching, snapshots and native TextView textColor. Theme colors are live async snapshots and can be converted through the color helpers. Native URI parsing and FileProvider URI creation are available, including app.getUriForFile. Bun also receives the current Node pure-color conversion facade.

The tasks module uses the same persistent TimedTask/IntentTask database and scheduling backends as the host task UI. It provides daily, weekly, monthly, yearly, disposable and positive-delay countdown registration; local/external IntentTask registration; query/get/update/remove operations; local broadcasts; and weekday flags. Confirmed registrations survive Worker and execution exit. Native object handles and unconfirmed/abandoned registrations are released. Task results are readonly snapshots with numeric IDs. The legacy autojs6:compat one-shot WorkManager path preserves string IDs and its queryTasks behavior.

Bun adds independent default-off android.objects and tasks.control grants. Native View use additionally requires ui.layout. The Builder derives RECEIVE_BOOT_COMPLETED, WAKE_LOCK and SCHEDULE_EXACT_ALARM from task capabilities. Special exact-alarm access remains an Android decision, consistent with the [Android scheduling documentation](https://developer.android.com/develop/background-work/services/alarms).

## Corrected behavior

- Daily and weekly recurrence now follows local calendar dates across 23/25-hour days. Nonexistent local times move through the DST gap; repeated local times use the earlier occurrence. Monthly/yearly local-time construction shares the same conversion. JVM regressions cover spring/fall transitions and overlap handling against the [Joda-Time zone conversion API](https://www.joda.org/joda-time/apidocs/org/joda/time/DateTimeZone.html).
- IntentTask registrations distinguish local/external actions and MIME/category filters. Update/removal refreshes the remaining registrations instead of disabling another task with the same action or leaving stale receivers. Local broadcasts carry primitive extras and do not replay old emissions after registration refresh.
- New task updates preserve null category/MIME fields and send explicit patches rather than stale snapshots. Updating a countdown without a new delay preserves its exact deadline.
- FileProvider URI creation resolves the actual manifest component authority. The generated application package no longer tries to use org.autojs.autojs6.inrt.fileprovider from the template.
- A 1 ms Bun countdown while its creator was still executing reproduced an actual BUSY failure in host Binder admission. Host calls now wait fairly within their existing call budgets, check cancellation while queued and retain the existing bounded-worker/stalled/poisoned-dispatch behavior. A queue timeout does not dispatch, abandon or poison an unsubmitted operation. The separate cancellation lane remains available.

## Accepted physical evidence

133 final successful JUnit cases; failed and superseded attempts are excluded.

| Device | Phase | Cases | Seconds |
| --- | --- | --- | --- |
| redmi | node-host | 5 | 11.262 |
| redmi | host | 6 | 41.359 |
| redmi | node | 34 | 112.46 |
| redmi | bun | 13 | 27.549 |
| redmi | produce | 1 | 40.016 |
| redmi | packaged | 1 | 74.569 |
| xiaomi | node-host | 5 | 7.726 |
| xiaomi | host | 6 | 35.189 |
| xiaomi | node | 34 | 38.631 |
| xiaomi | bun | 13 | 24.785 |
| xiaomi | produce | 1 | 15.999 |
| xiaomi | packaged | 1 | 73.22 |
| xiaomi | ace | 13 | 228.146 |

Redmi 22120RN86C / bek749scrwv4wo8h ran Android 13/API 33. Xiaomi 23046RP50C / 968e9f18 ran Android 15/API 35. Both are native arm64 with 4096-byte pages.

Native tests exercised real Paint/ColorStateList/ThemeColor/FileProvider behavior, native TextView state colors, all six calendar registrations and updates, deadline preservation, separate grants, cancellation cleanup and local/external broadcast filtering with shared actions. Host Node, Bun and Worker tests checked actual object methods, calendar/Intent CRUD and cleanup. CJS/ESM/nested Worker exit preserved confirmed tasks and parent Paint objects while releasing the Worker's objects.

Each phone also executed six actual target scripts per runtime after registration: daily, weekly, disposable, regular countdown, a 1 ms countdown registered while the creator remained active, and IntentTask with received extras. All 24 host target executions have successful terminal callbacks as well as HTTP receipts. Monthly/yearly behavior was validated through registration, state, updates and date calculation; this batch did not wait a month/year for physical triggers or change device time zones.

Each phone built and installed a Bun and a Worker APK through the actual paired Runtime Kit/Builder. Each application ran twice with a force-stop/reopen between rounds. The 24 packaged receipts cover eight main runs and sixteen countdown/Intent target executions. Persistent calendar and Intent IDs survived relaunch, and the second main run read the first round's saved Intent payload. All receipts identify their own package and runtime build 5586. File URIs use each generated application's authority. Permission manifests, independent signing certificates and exact runtime DEX equality to the Kit template were verified.

The owned packaged fixtures explicitly enable SCHEDULE_EXACT_ALARM through test setup; this does not claim that a project declaration grants special access. Cross-process packaged checks allow two seconds for plugin teardown after HTTP receipts before the next trigger/force-stop. Host checks use actual terminal callbacks. No device reboot, Doze guarantee, cross-application concurrent Bun acceptance or native 16 KiB execution is claimed.

No acoustic stimulus or sustained tone was used. Task targets write owned files or send local fixture HTTP results. AlarmManager task triggers do not play alarm audio. Original media volume was 0 on both devices and was preserved.

## Verification and retained failures

- 5119 JVM cases discovered: 5093 passed and 26 existing skips, no failures/errors. The additional queue tests cover serial admission, timeout without dispatch/poison and cancellation without stopping the current script.
- All 48 Node facade/desktop Worker tests, the standalone Worker regression and 60 Bun SDK tests pass. Python suites pass 2 Node / 38 Bun / 8 API-extractor cases.
- TypeScript 5.1.3 and 6.0.3 smoke checks pass with 72 synchronized runtime declaration files. Ace passes 523 runtime queries and 268 core/default/JSOX queries. All 13 actual device popup cases pass; Node task and Bun Paint signatures were visually inspected.
- The focused coverage gate matches 10 native object routes and 10 task routes with the shared factories/types. The host documentation/type/Ace gate has zero failures across 1356 explicit exports and 1589 paths. These are check-scope counts, not full Node/Bun implementation parity counts.
- Current NDK C++ syntax, relevant Android/JVM assembly, Node/Bun/Ace lint, Bun runtime integrity, real Kit/Builder assembly and offline debug/release validation pass. The generator checks 164 modules; Offline Docs verifies 220 files. Ten-language notes and the new grant switches are synchronized.

Retained attempts include an Android JSON stub used by an initial JVM parser test (the test now uses a pure list parser), a stale test APK without the new class, and fixture cleanup using /data/user/0 paths against canonical /data/data task paths. Only the exact two failed fixture UUIDs per phone were recovered, and final fixtures verify task removal with canonical paths. Tests confirm the original task rows were preserved.

Other retained failures include null filter strings during the new API implementation, an HTTP receipt preceding runtime teardown/workspace commit, the template FileProvider authority, and an intermediate component lookup targeting the helper subclass instead of the manifest's androidx.core.content.FileProvider. Final checks use the actual component and terminal execution results. The invalid zero-delay test established the existing native positive-delay contract; the valid 1 ms test then reproduced and verified the fix for BUSY admission.

## Versions, provenance and remaining scope

Host 6.8.0 / Android build 5586; Node Runtime 1.21.0 / build 269; Bun Runtime 0.10.0 / build 269; declarations 4.68.0; Ace 2.30.0 / build 306; Offline Docs 6.8.26 / build 183; Builder 1.2.15 / repository build 90 paired to host 5586; Documentation revision 147.

Kit: `autojs6-runtime-v6.8.0+5586-15f96048c2ea41d04abd8f79f23bccdc96b0460e-arm64-v8a`. The source basis is host 15f96048c2ea41d04abd8f79f23bccdc96b0460e plus this batch and shared-checkout work. The base identifier is not a final content commit; APK hashes and receipts identify the exact tested artifacts. During this task, unrelated floating-menu changes were independently committed as 59ea4e570 and a1e5c4ab9; continuing WrappedShizuku/CircularMenu edits are excluded from this API commit. Shared release notes are staged semantically by this batch's own entries.

Documentation source `5e6c986a1361098a17353ad28f80a6d13f279f0a` is pinned in both Offline Docs provenance files. Bun's native binaries and frozen compatibility matrix were not changed. All commits remain local, without a push, catalog upload or public release.

The current contract supplies typed Android values/handles rather than arbitrary Java object graphs. IntentTask.lastIntent is the latest recorded broadcast, not a queue of per-event histories. Android background restrictions and runtime availability still apply. The Bun host queue is process-local and bounded by existing call deadlines; it does not turn the underlying single-execution runtime into a parallel engine or coordinate different generated applications. Broader Java/Android helpers, dedicated AI/flow/TTS/mail/EPUB and other plugin surfaces remain audit work; this does not imply full Rhino parity.

## Restoration

Both phones' original APK hashes, camera/audio/notification flags, listener access, accessibility settings, brightness/mode/timeout, modify-settings and exact-alarm app-ops, music/notification/alarm volumes and rotation were verified after restoration. Redmi's originally enabled accessibility service is bound; Xiaomi's originally disabled service remains disabled. Owned host task records and temporary sources were removed by verified fixture cleanup. Four generated applications and owned SDK/APK/proof files were removed, which also removes their schedules. All 13 fresh original-APK backups were deleted only after restored hashes matched.

Six desktop Worker fixture directories were moved to the Node repository's ignored .bak area. Pre-existing build/worker-host-tests was preserved. Evidence, exact APK receipts, source baselines and failed attempts remain under ignored .bak/runtime-android-tasks-20261019.

Reproduce with tools/api-coverage/check-runtime-android-tasks.mjs, Node test-android-tasks.js/test-worker-android-tasks.js, Bun android-tasks.test.mjs, declaration smoke checks and Ace runtime/default coverage. Physical suites are RuntimeAndroidTasksNativeTest, RuntimeAndroidTasksDeviceTest and RuntimeAndroidTasksPackagedDeviceTest.
