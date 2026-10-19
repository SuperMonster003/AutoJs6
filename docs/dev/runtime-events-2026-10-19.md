# Host events and notifications delivery

Date: 2026-10-19. Scope: Bun event observers and notifications, Node.js main/Worker facade alignment, documentation/types/Ace completion, real devices and generated applications. No tone, audio playback or recording was used. Notifications used owned low-importance channels.

## Delivered behavior

- Bun exposes host.events and host.notifications. Node main threads and CJS/ESM/nested Workers use autojs6:events and notifications/autojs6:notifications. Bare events and node:events remain the native EventEmitter module.
- observeBroadcasts, observeNotification, observeToast and observeKey return independent asynchronous subscriptions. Modules and subscriptions expose on/once/off; subscriptions expose id/source/closed, drainEvents and close. Repeating one source within one client reuses that client's subscription. Closing one client or Worker preserves others.
- Events are immutable JSON snapshots. Sources include screen/battery broadcasts, notification content, other applications' Toast messages and non-consuming key observation. Android accessibility and notification-listener access remain real prerequisites; no Java objects or notification click/reply actions are fabricated.
- Node main threads preserve native event push. Bun and Workers poll bounded queues; 128 queued host events, at most 32 items per drain and explicit overflow counts. Pushed client buffering is also bounded. Native source disconnect, abort, failed polling, pending registration cancellation and execution shutdown release observers. Throwing close/error callbacks do not prevent cleanup.
- Notifications expose notify, cancel, cancelAll, getPermissionStatus, openSettings and openNotificationSettings. Numeric IDs are scoped by execution and client, so identical parent/Worker IDs coexist. Worker exit and abandoned publication release only their owned notifications. Android-generated group summaries are not script-owned notification handles.
- Bun adds six independent default-off grants: events.observe, events.notification, events.toast, events.key, notifications.post and notifications.settings. Sending permission and notification-listener access remain separate. The packaged preparer adds POST_NOTIFICATIONS from Node/Bun notification capabilities; actual Android permission is still required.

## Repairs confirmed during validation

The previous native observer rejected a second subscription to the same source anywhere in an execution. Independent sessions now support main/Worker coexistence. The previous notification manager key used only a numeric ID; separate executions could replace or cancel each other's notification. Android tags plus per-client registry keys now isolate those publications, including explicit identical IDs.

Redmi did not consistently deliver an initial battery callback for background registration. The observer now publishes Android's returned sticky Intent and deduplicates an identical callback. The recorded values remain actual device readings.

An already authorized notification service can still be disconnected during process startup. Observation now requests rebinding and waits within the request deadline, capped at four seconds. It does not grant notification access. This follows the [Android NotificationListenerService connection contract](https://developer.android.com/reference/android/service/notification/NotificationListenerService), which requires connection before ordinary service operations and permits a rebind request before connection.

Bun's existing per-execution call limit now closes event observers and notifications as well as Shell resources. The native quota test checks cleanup before broker destruction. Late cancelled registration/publication replies are retained long enough to close their native resources.

## Accepted physical evidence

129 final successful JUnit cases. Failed and superseded attempts are retained separately and are not included in that total.

| Device | Suite / phase | Cases | Seconds |
| --- | --- | --- | --- |
| redmi | node-host | 7 | 1.978 |
| redmi | host | 3 | 38.623 |
| redmi | node | 34 | 110.278 |
| redmi | bun | 13 | 28.79 |
| redmi | produce | 1 | 39.169 |
| redmi | packaged | 1 | 43.085 |
| xiaomi | node-host | 7 | 0.711 |
| xiaomi | host | 3 | 49.138 |
| xiaomi | node | 34 | 34.354 |
| xiaomi | bun | 13 | 25.067 |
| xiaomi | produce | 1 | 15.967 |
| xiaomi | packaged | 1 | 72.198 |
| xiaomi | ace | 11 | 204.786 |

Redmi bek749scrwv4wo8h is Android 13/API 33; Xiaomi 968e9f18 is Android 15/API 35. Both are native arm64 with 4096-byte pages. Every main Node/Bun and CJS/ESM/nested Worker scenario received its own actual notification, external test-package Toast, F8 key release and battery snapshot. Worker tests also checked parent notification/subscription survival and orphan cleanup.

F8 was supplied using an owned temporary keyboard through the [platform uinput test tool](https://android.googlesource.com/platform/frameworks/base/+/android15-platform-release/cmds/uinput/). This exercises Android's InputReader/accessibility path with synthetic hardware input; it is not a human pressing a physical key. The fixture waits for registration, sends down/up and checks that the device is removed afterward. Ordinary ADB key injection did not reach the observer in the earlier runs. Screen on/off registration is implemented, but this batch did not deliberately lock/unlock the devices to claim a separate screen-transition test.

Each phone produced and installed one Bun and one Worker APK using the actual paired Runtime Kit/Builder. Each APK ran twice with an owned-app force-stop between runs. Own package identity, runtime build 5584 and all four event sources were verified. All four manifests declare POST_NOTIFICATIONS from capabilities. Independent signature checks confirm their certificates differ from the host's, and their runtime DEX files match the actual Kit template.

## Test setup and retained failures

Instrumentation restarts the host process. The fixture rebinds already-authorized services and verifies readiness, using at most two accessibility setup attempts. Redmi initially remained unbound; the user re-enabled its host service and subsequent complete runs passed. Notification listeners are explicitly refreshed during setup. These preparation steps do not claim that API calls silently authorize Android services.

The packaged service components were correct, but the Bun fixture's accessibility entry was absent after launch/stop. Packaged tests therefore configure the two owned applications' service access after each foreground launch and wait for connection. The relaunch evidence includes this explicit setup; it is not a claim of automatic accessibility authorization persistence after force-stop.

Other retained fixture failures were a separate-APK Kotlin runtime dependency in the Toast Activity (replaced with a standalone Java Activity), immediate notification counts that included Android's automatic summary or preceded asynchronous posting (now check three owned tags after bounded waiting), and an Ace expectation that expanded NotificationId to number (the actual typed popup correctly preserves its namespace-qualified alias). The full corrected assertions pass. The existing Worker regression was also rerun with its intended standalone Node command after an unsupported test-runner invocation inherited invalid Worker flags.

## Local checks and synchronized surfaces

- 5109 JVM cases discovered across host/Node/Bun/Ace/Builder/Offline: 5083 passed, 26 existing skips, zero failures/errors.
- 18 Node facade/real desktop Worker checks and the existing standalone Worker host-module regression; all 55 Bun SDK checks pass. Node/Bun Python suites pass 2/38 cases; API extractor tests pass 8 cases.
- TypeScript 5.1.3 and 6.0.3 smoke programs pass with 70 synchronized runtime declaration files. The new declarations cover observer options/events/overflow and notification aliases without introducing Rhino globals into Node/Bun.
- Ace runtime coverage passes 473 queries; default/core/JSOX coverage passes 268 queries. All 11 actual-device popup cases pass, including both Node and Bun event/notification signatures, options and subscription members.
- The focused gate matches six native event routes, five notification routes and the shared factories/types. The full host source/documentation/type/Ace gate remains green at 1356 explicit exports and 1589 paths; this is not a count of fully implemented Node/Bun APIs.
- Current NDK C++ syntax, relevant Android assembly, Node/Bun/Ace lint, Bun runtime integrity, genuine Kit/Builder assembly and offline debug/release verification pass. All 164 Markdown modules are current; Offline Docs verifies 220 files.

## Boundaries, versions and provenance

Bun retains the existing 1024-call execution budget, four concurrent tasks, 64 KiB request/256 KiB response limits and ten-second individual RPC limit. start/poll consume calls, so this is a bounded event channel. Observe only needed sources, close subscriptions and choose an appropriate polling interval. Delivery remains subject to Android permissions and service availability. Arbitrary broadcasts, full Rhino EventEmitter extensions and Java notification objects remain outside this portable contract.

Host 6.8.0 / Android build 5584; Node plugin 1.19.0 / build 267; Bun plugin 0.8.0 / build 267; declarations 4.66.0; Ace 2.28.0 / build 304; Offline Docs 6.8.24 / build 181; Builder 1.2.13 / repository build 88, paired with host 5584; Documentation revision 145. Bun's native runtime bytes and its frozen experimental/ABI support matrix were not changed.

Runtime Kit: `autojs6-runtime-v6.8.0+5584-9d8c48c7da9168c3f01c040812957714534d5802-arm64-v8a`. Source basis is host 9d8c48c7da9168c3f01c040812957714534d5802 plus this delivery's working changes and the pre-existing shared-checkout edits. Four unrelated CircularMenu/CircularMenuOperationDialog production/test files and five release-draft files existed at the start; their baseline snapshots and diffs are retained. These original edits are excluded from this delivery's commits. The Kit source-base identifier is not represented as the final content commit; APK hashes identify the exact tested artifacts.

Documentation source `5087e0f177e65fd28b1e0d710e7b02e0511e8581` is pinned in both Offline Docs provenance files. The implementation, declaration and completion changes stay local; there was no push, catalog upload or release. Evidence, exact APKs, receipts and failed attempts are retained under ignored `.bak/runtime-events-20261019/`.

## Restoration and remaining audit

Original APK hashes, notification-listener access, camera/audio/notification permission flags, volume, rotation and the original accessibility lists were verified after restoration. Redmi's originally enabled host service is bound; Xiaomi's originally disabled host service remains disabled. Four task-created packaged applications and owned SDK/proof fixtures are removed. All 13 fresh original-APK backups were deleted only after restored hashes matched. Desktop Worker fixtures are archived under the Node repository's ignored .bak directory; the pre-existing Worker fixture directory remains in place.

Reproduce with tools/api-coverage/check-runtime-events.mjs, Node test-event-facades.js/test-worker-events.js, Bun events.test.mjs, declaration smoke checks and Ace runtime/default coverage tools. Android suites are RuntimeEventsNativeTest, RuntimeEventsDeviceTest and RuntimeEventsPackagedDeviceTest.

The remaining audit includes broader Bun/Worker device-control and dialog facades, Android-object helpers, calendar/IntentTask scheduling, and dedicated AI/flow/TTS/mail/EPUB and other plugin surfaces. This delivery does not imply full Rhino API equivalence.

The [device/dialog delivery](runtime-device-dialogs-2026-10-19.md) adds Bun and Node Worker device/dialog facades, scoped wake locks, precise cancellation, live progress handles, four independent Bun grants, derived APK permissions and Android 13/15 host/packaged validation. The original Bun device.info format remains compatible; full snapshots use getInfo.
