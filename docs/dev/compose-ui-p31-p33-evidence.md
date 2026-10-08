# P3.1-P3.3 script API and UI mounting evidence

Date: 2026-10-03. Plugin baseline d2ccbe4 (build14), host baseline 1aca0452c1
(6.8.0 / 5316). Host master 5b5841bfbf was merged cleanly as 0f9ce3ad8f before implementation.
The original host worktree continued unrelated code-generation work during this session; that work
was not modified or included in task commits. The Compose host changes remain in the dedicated
AutoJs6-ComposeUi-Spike worktree on spike/compose-ui-p0.

## Delivered scope

P3.1 provides compose/$compose, version/availability, createElement and 29 NODE factories derived
from the frozen catalog. Snackbar remains a command. Node handles implement validated properties,
aliases/shortcuts, children/slots/events, commands and atomic structural mutation. Public IDs are -1
outside a bound tree; internal IDs survive reinsertion. Cross-session ownership, cycle/duplicate-key,
scope, value and callback/node/depth limits are enforced. The host converts colors through its colors
module, owns resolved image lifetimes, and keeps every script function outside the renderer contract.

P3.2 adds state read tracking, per-tick coalescing, batch, refs, post and theme. Rendering matches new
drafts to existing handles while preserving explicitly pre-created nodes. Rejected frames restore the
accepted graph/ref state; an initially rejected native frame can recover through a later valid update.
Actual script failures preserve the previous UI and enough dependencies to retry. Structural typed
errors retain their codes. A failed synchronous mount releases its reaction instead of retaining an
invisible effect. Initial and ordinary commands wait for their relevant tree receipt; edits coalesce
to full text/selection snapshots and retain their captured sequence.

The underlying core accepts stable-ID trees with exactly-once accepted/rejected/superseded receipts,
including no-op submissions. It exposes callback liveness/pruning so superseded pending handlers do
not accumulate. Cross-owner and child/slot transfers use an atomic SetRoot fallback where incremental
ordering would introduce colliding IDs. Same-parent reorder continues to use Move.

P3.3 attaches through ScriptExecuteActivity and the existing ui content path. Replacement by another
mount or ui.layout closes the previous session, clears refs and resources and reports a warning for
legacy content replacement. Activity/engine teardown disposes native views and cancels posted work.
The module dispatcher alone does not keep otherwise completed non-UI scripts alive. The P2.4 host
inset owner remains unchanged. Missing theme options preserve live host defaults; partial overrides
merge with the host's current seed/night/font values instead of freezing an empty default theme.

The per-scope ComposeError constructor, code/cause/stack translation and UI-mode localized message
support these delivered entries. P3.5 remains a separate unfinished integration milestone, together
with P3.4 floaty. No roadmap stage was added, split or discarded. Full user documentation, declarations,
example bundles and the complete compatibility/memory/performance gates remain in their existing stages.

## Verification

| Check | Result |
| --- | --- |
| Selected host JVM | 111/111: core 39, script values/nodes/reactivity/API/errors 62, Augmentable regression 10 |
| Frozen contract JVM | 13/13; no AAR/wire/snapshot change |
| Script values | 13 cases, including units/colors/Unicode/list bounds, immutable modifier branches and theme conversion |
| Node graph | 18 cases, including rollback, callback versions/budgets, stable/manual identity, refs, editor lifetimes and cleanup |
| Reactive runtime | 9 cases, including coalescing, branch cleanup, failed dependency recovery and close during drain |
| Exported Rhino API | 16 cases, including globals/catalog, stable updates, mixed handles/render, failed mount/frame recovery and queued commands |
| ComposeError | 6 real-Rhino cases for Error identity, metadata, causes, stack and retryability |
| Device UI scripts | API24 x86 and API33 Redmi 22120RN86C: 7/7 each |
| Host build | app debug/androidTest/release assemble; both native page-alignment gates pass |
| Final plugin smoke | Build15 minified APK with isolated debug host on API33: counter, main-thread render recovery and standalone INFO, 3/3 |
| Plugin integrity | JVM 63/63, 36 generated documents and icon checks, signing/alignment and DEX ownership gates pass |
| Shared dependencies | 51 entries unchanged, same four host runtime classpaths and fingerprint as P2 |

Device scripts go through RunIntentActivity's supported inline pre-execute source, with an explicit
UI directive and a unique source marker. The first API33 attempt used a private-cache file path;
existing PathChecker requires external-storage-manager access for the path route even for that file,
so no engine started. Switching the test fixture to the public inline route avoided changing storage
permissions or production behavior. The isolated host package is org.autojs.autojs6.compose.spike.

The seven cases verify a state/render counter, a native field with controlled Switch/button, accepted
field removal/reinsertion and a new-lifetime edit, a 1000-row keyed list with scrolling and ID retention,
an actual UI-thread render exception that keeps Count0 alive and recovers to Count2 through the same
button, 20 real mount/close cycles, XML replacement (including suppressing remaining closed-session
listeners), and engine-stop cleanup across the deadline of a five-second delayed callback.
The lifecycle assertions cover detached/emptied views, cleared refs, exactly one close notification,
and cancelled callbacks. They do not substitute for the later long-running heap measurements.

The device runs used the already-minified plugin renderer (build14); plugin rendering code is unchanged
in this milestone. The final build15 updates packaged preview documentation and version metadata.
The unchanged 5316 renderer contract floor cannot distinguish older unpublished host artifacts with
the same build number: use the matching host worktree/build that contains this script API.

## Local artifacts and completion

Host implementation commits: 3ed3eac315 (stable submissions), 0483a1e3b4 (nodes/values/errors),
e5e7deab7d (reactive dependencies), de9d39d442 (public UI mounts and lifecycle). The matching host
release APK is assembled with its normal application ID; device verification used the isolated debug
host and never installed a release host over the user's application. Host app release is not minified.

Final local plugin: `releases/autojs6-plugin-compose-ui-v1.0.0-b5d8a20a.apk`, version1.0.0 / build15,
2359626 bytes, CRC32 b5d8a20a, SHA-256
fed06a9d8ce8ebc2d9e3110b8051a80d20dd1e9983975a96babba2a2453170cd.
Its release DEX remains 7142 classes / 44715 method references; no renderer implementation changed.
The closing text gate also corrected two typographic quotes in the preceding P2 roadmap evidence.

The three test-owned packages (plugin, isolated host and host test) were removed and absence checked
on both devices. No existing user app was replaced. The temporary compose_p3_api24 AVD (x86, port5560,
2 GB userdata) and its metadata were removed after checking its identity and resolved workspace path.
Existing AVDs were left intact. No default IME, storage-management permission or user file was changed.
Both task worktrees are clean after the documentation commits. No push, index publication or public
release was performed, in accordance with D7.
