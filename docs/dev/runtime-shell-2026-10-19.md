# Host Shell, Shizuku and Worker delivery

Date: 2026-10-19. Scope: Bun host Shell plus Node.js main/Worker streaming and Shizuku facades, documentation/types/completion and real host/packaged validation. No acoustic signal or playback test was used.

## Delivered APIs

- Shared shell factory for Node main threads, CJS/ESM/nested Workers and Bun. Node imports shell/autojs6:shell and shizuku/autojs6:shizuku; Bun uses host.shell/host.shizuku.
- exec/execRoot/execShizuku/execFile, interactive open, spawn, native readable/writable streams, async line reads, line events, bounded capture, timeout/AbortSignal, waitFor, kill and close. Shizuku is callable and also exposes exec/spawn/open/access/permission methods.
- checkAccess remains synchronous and describes execution grants. getAccess queries Android/Shizuku state. requestShizukuPermission or shizuku.requestPermission explicitly asks the current app's permission; no authorization result is fabricated and no automatic ordinary-UID fallback occurs.
- Bun has independent default-off shell.control/shell.root/shell.shizuku grants. Node uses shell/shell.root/shell.shizuku. Packaged declarations automatically include Shizuku's Android permission; each package still obtains its own actual grant.
- Host working paths follow Android access rules. Ordinary default cwd is the host script directory; Shizuku defaults to / because shell UID cannot enter an app-private directory. Explicit relative paths still resolve against the host directory.
- Worker ownership tracks created handles, cleans late creation results, releases processes on termination and preserves parent-owned processes. Cancelled Bun starts/raw exec jobs and exhausted call quotas also release native processes.

## Bugs found and corrected

The physical binary test initially rejected a 24 KiB write: Base64 plus Android JSON slash escaping exceeded the 32 KiB argument budget. Writes now use 8 KiB raw chunks, with all-0xFF regression data to cover worst-case slash expansion.

PTY input/output share the terminal master. Closing a plain FileOutputStream interrupted stdout. PTY input close now sends terminal EOF without closing output. A second stty invocation also flushed input that arrived immediately after process creation; ordinary PTY commands now rely on the native termios setup already applied before exposure. su's extra terminal retains its separate setup. Expected reader interruption during termination is distinguished from a live stream failure.

Ordinary process cleanup now freezes and kills reachable descendants before ending the parent, including TERM-trapping commands. PTY, Root and Shizuku wrappers retain their group/remote cleanup paths. The Shizuku user-service revision was advanced so an older same-version process is replaced. Raw exec collection exits on owner cancellation rather than spinning after queue closure.

## Accepted physical evidence

130 final successful JUnit cases, plus 80 separate Rhino shell checks (40 per phone). Superseded and failed attempts remain separately archived and are not counted as passes.

| Device | Suite / phase | Cases | Seconds |
| --- | --- | --- | --- |
| redmi | host | 3 | 61.915 |
| redmi | node-host | 8 | 3.189 |
| redmi | node | 34 | 110.76 |
| redmi | bun | 13 | 28.604 |
| redmi | produce | 1 | 39.131 |
| redmi | packaged | 1 | 46.027 |
| xiaomi | host | 3 | 38.457 |
| xiaomi | node-host | 8 | 1.75 |
| xiaomi | node | 34 | 41.277 |
| xiaomi | bun | 13 | 24.735 |
| xiaomi | produce | 1 | 15.912 |
| xiaomi | packaged | 1 | 49.983 |
| xiaomi | ace | 10 | 194.471 |

Redmi bek749scrwv4wo8h is Android 13/API 33; Xiaomi 968e9f18 is Android 15/API 35. Both are native arm64 with 4096-byte pages. Main Node/Bun and CJS/ESM/nested Worker runs exercised ordinary UID and actual Shizuku UID 2000. Checks include literal execFile arguments, host cwd/env, binary stdin/stdout (70000 bytes, including 0xFF and all byte values), delayed-consumer backpressure (262144 bytes), separated streamed output, PTY EOF, timeout, abort, late close and parent/Worker ownership.

Each phone built and installed one Bun and one Worker APK through the genuine paired APK Builder, then ran each twice with an owned-app force-stop between runs. Their own package identity, host build 5583, ordinary app UID, Shizuku UID 2000 and Shell results were verified. All four manifests contain the Shizuku API permission derived from runtime capabilities. All output APK certificates were verified independently of the host certificate, and their runtime DEX files match the actual Runtime Kit template.

Redmi's first packaged Bun bind timed out. One complete retry of the unchanged APKs passed. A direct ADB Wake Activity attempt was denied by its published plugin permission; a host-context activation script was also attempted. The evidence does not establish activation as the cause of recovery, so first cold binding on this OEM remains an explicit limitation. Xiaomi passed its original packaged run.

Both phones had Shizuku installed but initially stopped. The already-installed starter was used without replacing its original file or deleting any shared temporary directory. The [upstream starter implementation](https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/jni/starter.cpp) describes the same local app_process launch mechanism. An owned copied starter exited without output; that failed setup is retained separately. Redmi's stored host grant was already allowed; Xiaomi's grant was initially denied and was explicitly requested for testing. Xiaomi's original ungranted state was restored through the Shizuku manager and verified through the API. Both servers were stopped through the manager afterward. Root/su success is not claimed on these non-root test devices.

## Other validation

- 5109 JVM cases discovered across host/Node/Bun/Ace/Builder/Offline: 5083 passed, 26 existing skips, zero failures/errors.
- 51 Bun SDK tests; 15 Node facade/real desktop Worker tests, plus the existing Worker host-module regression. The shell subset includes capture, byte-preserving transfer, backpressure, Shizuku routing, late registration cleanup and abort.
- Node/Bun Python suites: 2/38 passed. API extractor suite: 8 passed. TypeScript 5.1.3 and 6.0.3 runtime smoke programs and 70 synchronized declaration files passed.
- Ace default/JSOX core checks: 268 queries; runtime checks: 448 queries. Actual device popup regression: 10 cases. Type/source checks cover both semantic and static completion paths.
- Focused shell gate matches ten host routes, ten shell methods, callable Shizuku and shared generated code/types. Full host surface gate remains green: 1356 explicit exports, 1589 paths, zero failures. This count measures the published extraction scope, not full Rhino/Node/Bun behavioral equivalence.
- Current NDK C++ syntax check, Node/Bun/Ace lint, runtime APK integrity, genuine Runtime Kit/Builder builds and offline debug/release verification passed. Offline documentation contains 220 verified files; all 164 API Markdown modules pass generation/normalization checks.

## Compatibility boundaries

Bun retains its 1024-call execution budget, four asynchronous tasks, request/response bounds and ten-second individual RPC limit. start/poll consume calls, so streamed host Shell is a bounded channel. Quota exhaustion closes the execution's Shell processes. The existing host limit defaults to two active Shell processes shared across Workers. Caller code must consume both streams or close; queues apply backpressure rather than silently dropping data.

PTY stdin close uses canonical terminal EOF. Programs that change terminal input mode should follow their own exit protocol or be explicitly closed. Java streams/TermSession are not sent across the bridge. Node native child_process and Bun.spawn retain their independent runtime UID/filesystem behavior. Detached services/daemons and Root authorization remain subject to Android and their own lifecycle.

## Versions and provenance

Host 6.8.0 / build 5583; Node 1.18.0 / build 266; Bun 0.7.0 / build 266; declarations 4.65.0; Ace 2.27.0 / build 303; Offline Docs 6.8.23 / build 179; APK Builder 1.2.12 / build 87; Documentation revision 144.

Runtime Kit: `autojs6-runtime-v6.8.0+5583-cd965385cf16b509c131f6d4bff608622be44942-arm64-v8a`. Source basis is host cd965385cf16b509c131f6d4bff608622be44942 plus this delivery's working changes. The shared checkout also acquired unrelated CircularMenu and CircularMenuMoreDialogInstrumentationTest changes during this run; snapshots are retained, and these files are excluded from this task's commits. Candidate APK hashes and template DEX hashes identify exactly what was tested. The source basis is not represented as the final content commit. One trailing-space-only cleanup in the shared JS factory occurred after physical validation; it changes no behavior. Node/V8 versions and Bun native runtime payload/support matrix were not changed.

Documentation source `ee88dfcc93222cf80911f9982ac973c0df205e82` is pinned in both Offline Docs provenance files. All changes remain local; there was no push, catalog upload or release. Evidence, tested APKs, startup/permission/restoration receipts and failed attempts are under ignored `.bak/runtime-shell-20261019/`. Original APK backups are removed only after their restored hashes are verified; owned fixture files are cleaned. Original and concurrent unrelated host edits remain outside the owned commits.

Final restoration verified all original APK hashes and removed 13 fresh original-APK backup files. Camera/audio permission flags, volume, rotation, the exact accessibility service list and stopped Shizuku state match the pre-test settings. Redmi's originally enabled host accessibility service is bound; Xiaomi's host service was originally disabled and remains disabled. The original Shizuku starter hash is unchanged. Test-only app packages, bridge fixture files and owned starter copies are removed. Additional concurrent edits to CircularMenuOperationDialogBuilder and its instrumentation test were observed after the candidate builds and are also excluded from this delivery.

## Reproduction and remaining audit

Run tools/api-coverage/check-runtime-shell.mjs and check.py, the Node test-shell-facade.js/test-worker-shell.js tests, Bun shell.test.mjs, declaration compiler checks and Ace completion gates. Android suites are NodeShellBridgeInstrumentationTest, RuntimeShellDeviceTest and RuntimeShellPackagedDeviceTest. The paired producer verifies the exact Bun SDK checksum and consumes the genuine Runtime Kit.

The next remaining areas include Bun/Worker event and notification facades and the broader Android-object, scheduling and dedicated plugin surfaces recorded in the overall audit. Passing this delivery does not imply that every Rhino API has a direct Node/Bun implementation.

The [event/notification delivery](runtime-events-2026-10-19.md) adds Bun and Node Worker facades, independent subscriptions, scoped notifications, cancellation/overflow cleanup and actual host/packaged validation on Android 13/15. Android service authorization and Bun transport budgets remain explicit; it does not imply full Rhino parity.
