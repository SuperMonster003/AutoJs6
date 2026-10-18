# Bun accessibility, application and clipboard delivery - 2026-10-19

This batch adds the public Node-compatible automation interfaces to the explicit Bun SDK. The implementation and relevant coverage checks are complete. Xiaomi host action tests and genuine packaged applications on both phones passed. The Redmi host package's accessibility service remained unbound; its successful-action gate is explicitly not accepted. The unavailable-service behavior was verified instead and remains separate from positive action coverage.

## Delivered scope

- `host.accessibility` exposes 16 native operations: service availability/activation, text and selector lookup, click/long click, text replacement, forward/backward scrolling, global navigation, swipe and gesture. Sixteen local selector builders support text, descriptions, IDs, classes, regular expressions, booleans, depth and bounds. Results are immutable data snapshots, not Java nodes.
- `host.app` exposes 13 native routes for package queries, app launch/settings, activities/services/broadcasts and scoped file activities. Node's local Intent/URI/command-text helpers, mail/uninstall intent construction and aliases are reproduced from the actual Node source. Host application identity belongs to the executing host or packaged application. Root/Shizuku/dual-app helpers and raw Java Uri objects retain explicit rejection; no unsupported operation is faked as successful.
- `host.clipboard` exposes `getText`, `setText`, `hasText` through the host/packaged ClipboardManager, preserving Android foreground-read restrictions.
- Four independent, default-off grants are available in Plugin Center and packaged `bun.hostCapabilities`: `app.control`, `accessibility.control`, `accessibility.gesture`, `clipboard.control`. Gestures require both accessibility grants. App control maps explicitly to Node's app launch/settings/activity/query sub-capabilities.
- Long gestures and service activation use bounded native jobs and short result polls. Each execution admits four outstanding tasks; the SDK queues further operations from its client. Timeouts accept 1..120000 ms and AbortSignal stops waiting/cancels the task record. Already-dispatched Android actions cannot be undone. The existing 10-second RPC, 1024-call execution and JSON size limits remain. Closing an execution releases the job scope and shared provider.
- The host only exposes the public automation allowlists. Raw requests cannot reach internal Agent diagnostics, arbitrary Node modules or methods from another capability. Malformed method/argument types return INVALID_REQUEST.

`tools/host-client/sync-automation.mjs` in Bun Runtime reproduces the adapters and declarations from the Node public modules. Node source and its native payload were not changed. Documentation, the isolated Bun declarations, 70 DTS/Ace runtime assets, completion indexes, all ten README/changelog languages and the four permission descriptions were updated. The documentation also corrects stale single-reader wording for the already-delivered View event subscriptions.

## Candidate versions

| Component | Version |
| --- | --- |
| Host | 6.8.0, build 3920 + offset 1652 = Android 5572 |
| Bun Runtime | 0.3.0, build 262 |
| Node.js Runtime | 1.15.0, unchanged |
| TypeScript declarations | 4.61.0 |
| Ace | 2.23.0, build 298 |
| Documentation | 6.8.0, revision 138 |
| Offline Docs | 6.8.18, build 169 |
| APK Builder | 1.2.8, build 83, host 5572 |

Offline provenance pins Documentation commit `c03434f68945e6112866b6bfbd489b67ca81e1bb`. These are local development candidates; no publication, push, registry upload or catalog deployment was performed. Bun's native bytes, supported ABI/Android scope and frozen experimental compatibility matrix remain unchanged.

## Physical verification

| Device | Host checks | Packaged application |
| --- | --- | --- |
| Xiaomi 23046RP50C, 968e9f18, Android 15/API 35, ARM64/4096 | Node/Bun app and clipboard comparison, real accessibility actions, independent/raw grant denials, Bun 13-case regression, Ace 6-case coverage | Produced, installed and executed successfully |
| Redmi 22120RN86C, bek749scrwv4wo8h, Android 13/API 33, ARM64/4096 | Node/Bun app and clipboard comparison, independent/raw grant denials, Bun 13-case regression, explicit unavailable-service behavior | Produced, installed and executed successfully |

There are **42 accepted JUnit executions**, including production and execution of the two real APK Builder outputs. A host comparison test executes both Node and Bun. The host positive accessibility gate on Redmi is excluded from this total and is not reported as a pass.

The action fixture is a framework-only Java activity in the test APK's separate UID. Assertions check delivered clicks and long clicks, changed input text, regex/combined selectors, actual scrolling, completed swipes/gestures, broadcast and service delivery, URI activity routing and Android navigation acceptance. The Xiaomi host run includes a completed 10000 ms Bun gesture, exercising the polling path beyond one ordinary RPC. Fresh per-launch markers keep old accessibility trees from satisfying new-window checks. Returning Home may restrict subsequent background activity launches on Android; navigation tests therefore finish their sequence last and the test driver starts a fresh foreground fixture for each runtime.

Both genuine packaged apps use their own application identity, declared Bun grants, their own Android accessibility-service grant, and their own UI/clipboard context. They execute the same action program and return owned JSON proof over a loopback server. Every packaged DEX set matches the actual runtime template, APK signatures verify, and the packaged certificates differ from the host certificate. The URI/email/uninstall convenience adapters are also checked against the actual Node parameter/Intent construction; no email is sent and no user application is uninstalled.

Clipboard contents are kept only in process memory for restoration and are not exported. Saved proofs contain owned fixture values, package identities, counts and status. No acoustic tone or media playback was used anywhere in this batch.

## Redmi host service limitation

The installed host package's service remained UNBOUND in the app and Binding in Android, with DEAD connection records. This persisted after settings re-enable, the normal system confirmation flow, and a visible host launch. It was observed outside instrumentation as well, so this report does not attribute the problem solely to the test runner or claim an established root cause. Xiaomi host actions and the independent packaged app on Redmi succeeded.

The separate unavailable-service gate verifies both Node and Bun report `isEnabled() == false` and reject a real selector operation. Bun returns `HOST_UNAVAILABLE` instead of returning an empty or successful action result. Enabling a system switch alone is not proof that the service is operational. The failed positive-gate logs remain archived. A successful Redmi host action result, a device reboot, or a fix to its underlying service binding is not claimed.

## Desktop and build verification

- 35 Bun SDK client tests passed, including Node adapter equivalence, explicit grants, selectors/Intent construction, task concurrency, pre-abort and cancellation without retries. Both client test files are included in CI.
- The 32 native allowlisted methods match the Node provider. The complete relevant runtime bridge checks also passed.
- TypeScript 5.1.3 and 6.0.3 strict positive/negative smoke checks passed. All 70 runtime declaration assets match their canonical sources.
- Ace passed 406 semantic queries and its six physical tests with normal/default settings, including app/selector/clipboard popups, Intent options, runtime isolation and JSOX. Preferences and rotation were restored.
- Documentation generation is current for 163 modules; README/changelog checks and Bun's unchanged compatibility matrix passed. Bun's 38 Python tests passed.
- Host, Bun, Ace and Builder assemblies passed. Both Offline Docs APK inventories passed, and Bun debug runtime integrity checks confirmed unchanged native payloads.

| JVM suite | Discovered | Skipped |
| --- | ---: | ---: |
| AutoJs6 | 4721 | 26 |
| AutoJs6-Plugin-Bun-Runtime | 59 | 0 |
| AutoJs6-Plugin-Ace-Editor | 187 | 0 |
| AutoJs6-Plugin-APK-Builder-Template | 40 | 0 |
| AutoJs6-Plugin-Offline-Docs | 2 | 0 |

Total: 5009 discovered, 4983 passed, 26 skipped, zero failures/errors.

## Findings, scope and cleanup

The first host comparison exposed missing app.query-style sub-capabilities in the test manifest and Bun's provider setup. The explicit permission mapping was completed and protected with policy tests. The test service rebind helper was corrected for UiAutomation command argument handling and Android component-name normalization; it preserves other enabled services. A stale-window assertion was replaced with a unique fixture marker while retaining actual action assertions. The original Redmi positive-service failure remains separate from these fixture repairs.

The candidate Runtime Kit uses verified host base `c1aea044d5726141309f0eac0222eb4d3e3b66e6` plus the owned changes. Builds reused the existing isolated checkout and excluded concurrent Shell/Shizuku/native edits and the original five user drafts. Shared files were staged by owned hunks. The isolated checkout and signing/local files were preserved.

Original installed APK hashes, camera/microphone grants and flags, accessibility service settings, media volume and rotation were restored on both phones. Clipboard restoration runs in test finally blocks. Task-only apps, generated device APKs and owned fixture preferences were removed; the previous SDK fixture file was restored. Verified original APK backup copies are removed only after successful restoration. Tested APKs, exact receipts, JSON proofs, failed attempts, signatures and DEX hashes remain in ignored `.bak/bun-automation-20261019/` folders.

This adds the three Bun automation families, not full Rhino or Node API parity. Coordinate click retains the current Node provider restriction; use an explicitly granted single-point gesture. Java objects, privileged/dual-app operations and the remaining storage/database/sensor/scheduling and specialized runtime modules still need their own coverage decisions.
