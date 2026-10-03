# P3.4-P3.5 floating scripts and lifecycle evidence

Date: 2026-10-03. Plugin baseline eec4889 (build15), host baseline 6fba451cae
(6.8.0 / 5316). Host master 2fd0809e57 was merged into the dedicated
AutoJs6-ComposeUi-Spike worktree as 8edfb7ac84 before implementation. The merge retained
both the existing Compose history and the new selector fix in generated Japanese/Korean
history documents. Unrelated code-generation work in the original host worktree was not
modified or staged. All task host work remains on spike/compose-ui-p0.

## Delivered scope

P3.4 provides compose.floaty for UI and non-UI scripts with raw and resizable native windows.
The facade owns a normal Compose session, forwards its script methods and exposes native
geometry, focus, touchability, resize controls and close. Options are validated before native
work. Native geometry follows legacy floaty's physical-pixel convention; node dimensions
continue to use catalog dp/sp units. Overlay authorization reuses floaty.ensurePermission;
missing permission produces PERMISSION_REQUIRED without opening settings automatically.

The adapter reuses FloatyService and its raw/resizable windows, installing lifecycle and saved
state owners before native attachment. Deferred creation avoids blocking the script thread.
Provisional registry ownership makes an immediate floaty.closeAll cancel an uncreated window;
stale service snapshots cannot later recreate it. Registry snapshot removal preserves concurrent
later additions. Removing redundant set monitors also avoids legacy closeAll waiting for main
while main waits for that same registry monitor. User drag/resize geometry survives unrelated
focus/touch updates. Focus/blur/scroll commands wait for native handles after accepted patches.

Each floating session acquires one wait token on the originating script thread and releases it
on close or failed startup. Workers post updates through compose.post. Renderer events and
native failure callbacks return to the script dispatcher. The last window closes the idle script
normally. Failed creation, native chrome close, closeAll and engine force-stop release the same
session. A bounded startup timeout prevents an unavailable service holding the script forever.

P3.5 completes 18 localized error codes across all 10 host languages, preserving Plugin Center
selection messages, JS Error identity, code/nodeId/prop/cause and source stacks. Nested and cyclic
causes convert without recursion; interruption still propagates. UI and floating sessions share
the limit of eight. Replacing a UI mount at the limit leaves floaties alive. Closing sessions
rejects retained-handle operations, clears callbacks/images/refs, and cancels their pending posts.
Closed root/ref getters become null immediately; closing an older binding cannot clear a shared
ref now owned by another binding. Rolled-back floating mounts never attach later.

## Failures found and corrected

The initial API24 IME fixture requested the keyboard after Compose semantics focus but before
the native window/InputMethodManager had accepted the editor. The fixture now waits for an
attached editor, native window focus and InputMethodManager.isActive, then asserts showSoftInput
succeeded. Actual IME visibility and native InputConnection composing/commit remain required.
No production IME workaround or change to the default keyboard was introduced.

A later HyperOS combined run exposed a genuine force-stop race: a delayed main-thread post
reached Timer after its script looper had begun quitting, throwing IllegalStateException on
Android main. Timer now gives that specific Handler rejection its own IllegalStateException
subclass, and the Compose dispatcher treats only that signal as cancellation. Queue overflow,
unrelated IllegalStateException and LinkageError still propagate. Platform close cancels delayed
main callbacks and refuses new posts. If force-stop discards the original cleanup queue, the
owner-thread exit hook retries cleanup; callback receipts, close notification, renderer disposal
and wait-token release are each guarded for exactly-once completion. JVM regressions exercise
both dropped cleanup and late delivery, independently of thread liveness.

Review also found that a saturated ordinary script queue could reject a native event/close or
commit receipt on Android main, and leave eventScheduled armed after a failed enqueue. Reserved,
bounded native control channels now isolate those messages from ordinary user posts. The real
device regression uses latches to pause only the script thread, fills the ordinary queue twice,
then verifies continued native event delivery and immediate native close with exactly-once script
cleanup after resuming. Ordinary user overflow remains LIMIT_EXCEEDED. A delayed callback reaching
a full queue reports that code and is cancelled; reactive and command scheduling can recover from
a rejected post. Renderer initialization failure is separately fatal and closes the session, avoiding
an invisible wait token after the adapter already disposed its native resources.

The bounded control allocator added one more startup edge: its rejection can occur after the native
adapter registered provisional ownership but before a core session exists. The construction catch
now closes both the provisional window and renderer; the outer close releases its token. A real
device test creates and closes two batches of eight within one script turn, then rejects the next
reservation. It keeps that engine alive deliberately and checks its own floaty registry is empty,
so automatic engine-exit closeAll cannot hide a leaked provisional entry.

## Verification

| Check | Result |
| --- | --- |
| Selected host JVM | 154/154: core 52, script API 92, Augmentable regression 10 |
| Frozen contract JVM | 13/13; no AAR/wire/snapshot change |
| Session lifecycle | 19 cases, including eight-session limits, foreign stop, dropped cleanup, failed initialization and rejected control reservation |
| Errors and resources | 12 Rhino error cases and 2 code/resource checks; 18 codes, 10 languages / 11 resource directories |
| Dispatcher and receipts | 6 timer/control cases, 8 submission cases, 3 saturated-control cases |
| API24 x86 AVD | 15/15 in 30.409 s: floating scripts 8 and UI scripts 7 |
| Sony XQ-AT72 API31 arm64 | 9/9 in 13.289 s: floating scripts 8 and legacy raw-window owners 1 |
| Xiaomi Pad / HyperOS API35 arm64 | 15/15 in 28.292 s: floating scripts 8 and UI scripts 7 |
| Earlier exit-race stress | The corrected stop case passed 6 consecutive isolated API35 runs before the control-channel follow-up |
| Host build | app debug/androidTest/release assemble; debug and release native page-alignment gates pass |
| Plugin integrity | 63/63 JVM, 36 generated document checks, icon checks, signing/alignment and DEX ownership gates pass |
| Shared classpath | 51 entries and all four host runtime classpaths unchanged; both repository guards pass |

The final matrix uses host 6.8.0 / 5316 with the final implementation below and plugin 1.0.0 / 16.
The initial floating/IME runs used build15; final verification installs the signed, minified build16
on all three devices. Each floating suite now has eight cases, including both saturation and
reservation-rejection regressions. The 39 final device cases all passed after the final code change.

Device tests run actual non-UI source through ScriptEngineService. They validate worker-posted
HUD updates once per second, callback thread identity, Compose-button close, resizable native
chrome, pixel geometry/focus/touch settings, actual floating TextField IME and native editor
composition/commit, legacy closeAll, immediate closeAll before deferred attachment, and engine
stop across pending callback deadlines. HyperOS uses its desktop hosting surface with
requestFocus, matching the known D28 condition; this does not assert overlays above other apps.

The isolated host package is org.autojs.autojs6.compose.spike. Host release is also assembled
with its normal application ID but never installed over the user's host; host release is not
minified. The device matrix uses the minified plugin. Frozen V1 wire/AAR and all 51 shared
dependency entries remain unchanged. The 5316 contract floor cannot distinguish older local
host artifacts carrying the same version number, so these APIs require the matching host build.

Full compatibility/inrt coverage, long-running heap/performance measurements, TalkBack, example
bundles and declarations remain in their existing roadmap stages. External documentation and
declaration publication are deferred to P6 as planned. No roadmap stage was added or split.
Full host JVM/lint, inrt assembly/device execution and remote CI were not rerun in this milestone;
the reported counts describe the selected Compose/augmentation tests and app build variants only.

Reproduction uses host tasks `:app:testAppDebugUnitTest` (filters
`org.autojs.autojs.core.plugin.compose.*`, `org.autojs.autojs.runtime.api.augment.compose.*`,
`org.autojs.autojs.runtime.api.augment.Augmentable*Test`),
`:plugin-api:compose-ui-api:testDebugUnitTest`, `:app:assembleAppDebug`,
`:app:assembleAppDebugAndroidTest`, `:app:assembleAppRelease` and
`:app:verifyComposeUiSharedClasspath`. Set `-PcomposeUiSpike=true` and disable build-number/time
auto updates as in AGENTS. Device entry points are ComposeFloatyScriptDeviceTest,
ComposeScriptUiDeviceTest and ComposeUiLoaderTest#rawWindowOwnersAttachBeforeCompositionAndReleaseOnClose
through the isolated host's AndroidJUnitRunner. Use an explicit adb serial and grant overlay access
only to that owned host. API24 UI fixtures also require that package's storage permissions;
API35 uses its notification permission. The fixture preserves PluginEnableStore and UIAutomation flags.

## Local artifacts and cleanup

Host implementation commits: f7fcac31af (localized typed errors), 065c8148e8 (reserved native
delivery and recoverable teardown), 4d649bca96 (floating scripts, lifecycle guards, tests and user
history), following mainline merge 8edfb7ac84. Protocol/evidence copies are committed separately.

Final local plugin: `releases/autojs6-plugin-compose-ui-v1.0.0-f82f337c.apk`, version1.0.0 / build16,
2365090 bytes, CRC32 f82f337c, SHA-256
d753581e4be209fc3474e137b2d3d1115217ca971829e48cb1105a0c3868f1e7.
Its release DEX remains 7142 classes / 44715 method references. Plugin rendering code is unchanged;
this plugin build updates packaged preview documentation and version metadata.

The three test-owned packages (plugin, isolated host, host test) were uninstalled and their absence
verified on all three devices. No existing user application was replaced, and no default IME,
storage-management setting or user file was changed. The temporary compose_p34_api24 AVD on port5560
was stopped after verifying its identity. Direct directory removal was rejected by automatic policy;
after verifying the resolved data path and matching metadata, Android SDK avdmanager deleted that
named AVD successfully. Its data directory and registration are both absent. Other AVDs were not
managed by this task.

Both task worktrees are clean after the documentation commits; concurrent changes in the original
host worktree remain untouched. P3 is complete and the next roadmap starting point is P4.1.
This milestone continues to obey D7: local commits only, with no push, official-index entry or
public Release.
