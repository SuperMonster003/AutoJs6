# P5.1 robustness evidence

Date: 2026-10-03. Baselines: plugin 99c4d1d (1.0.0 / build18), dedicated host
9332c85d43 (6.8.0 / build5317). Committed mainline 166910c968 was merged into the
dedicated host as cadb1ddac6. The original host worktree's uncommitted work was
neither copied nor staged. The V1 AAR, 51 shared dependencies and minimum host
version5316 are unchanged.

## Hostile script inputs and live package ownership

ComposeRobustnessTest drives the real Rhino-facing Compose implementation through
its existing test platform boundary. It verifies typed failures and preservation
of the previously accepted tree, handles and callback ownership:

| Input | Result |
| --- | --- |
| 5001 nodes in a valid per-container shape | LIMIT_EXCEEDED; previous page remains usable |
| Depth65 | LIMIT_EXCEEDED; previous page remains usable |
| 65537 ASCII characters | LIMIT_EXCEEDED; all changes in the batch roll back |
| 2001 property patch operations | LIMIT_EXCEEDED; no partial frame or handle mutation |
| Invalid color, negative sp size, unsupported enum | INVALID_ARGUMENT; accepted values survive |
| Appending an ancestor as a child | INVALID_ARGUMENT; parent/child topology survives |
| mount or floaty inside a render callback | INVALID_ARGUMENT before a nested owner is allocated |
| Direct worker-thread state write | INVALID_ARGUMENT; original value and frame survive |
| UI and floaty package invalidation | PLUGIN_UNAVAILABLE; refs, sessions, window leases and subscriptions release |

The new nested-mount guard is in the host. Package subscriptions are also new:
each live session watches the exact loaded package identity, and receives one
terminal error when the APK is replaced or removed. Center disablement retains
PLUGIN_DISABLED; other selection failures retain their existing specific codes.
There is no synchronous JS call from the package receiver. Delivery uses the
session's existing control channel, followed by normal disposal on its owner.
Attach errors now use that fatal control path too, returning RENDERER_FAILED and
releasing the failed session. The Compose-specific mount retains the existing
beforeSetUiContent notification and runtime.ui.view assignment, then invokes the
already validated Activity on main directly. The generic UI helper's Rhino wrapper
could terminate the entire engine before the Compose error handler ran. Two more
Rhino regressions cover attach failure followed by a successful remount, and
package invalidation during watch registration with no attach or patch publication.

ComposePackageSubscriptionsTest covers five cases, including out-of-order package
broadcasts, observer removal, code-identity precedence over a replacement's gates,
callbacks outside the subscription monitor, and an old PackageManager result
arriving after a new session subscribed. The host snapshots both subscriptions
and cached code keys before querying PackageManager. A delayed result can only
invalidate entries observed by that query, preserving a newer loader and its JNI
ownership. Watch registration performs an immediate refresh to cover the gap
between load and subscribe.
An independent review also caught an older loader-cache race: a delayed successful
load of epoch1 could unconditionally evict epoch2. Loading now only reads/creates
its complete identity, leaving removal to the observed-inventory invalidator.
ComposeUiPluginCodeCacheTest uses latches to interleave the two epochs and requires
the newer loader to remain shared, preventing a second JNI owner for the same APK.

## Native rendering and lifecycle checks

ComposeRobustnessDeviceTest uses actual scripts, Android views and the installed
minified plugin. Seven explicit cases passed on both API24 x86 (13.936 s) and
Xiaomi Pad API35 arm64 / HyperOS (13.582 s), initially with release18:

- One hundred mount/close cycles, each allowing a 30 ms posted delay and checking
  an accepted ref, then checking closed handles, cleared refs, an empty disposed container
  and exactly one close callback. No session remains.
- Closing from the first click handler suppresses later handlers and disposes
  that view.
- A worker cannot write state directly, but compose.post reaches the owning UI
  engine and updates its visible text.
- Force-stopping a non-UI script while its render is blocked interrupts that
  render, releases its owned overlay and prevents the late tree from publishing.
- An actual orientation change preserves the engine and subsequent updates.
  An explicit Activity.recreate then invokes onDestroy, destroys the old engine
  and disposes its view.
- UiAutomation executes the real system command am send-trim-memory against the
  isolated host with RUNNING_CRITICAL. A registered ComponentCallbacks2 must
  observe that level; the test then clicks and requires a visible state update.
  It never substitutes a direct call to onTrimMemory.
- Disabling the plugin in the center closes its existing session with exactly
  one PLUGIN_DISABLED report. Re-enabling permits a new mount in the same engine.

The rotation result follows the existing ScriptExecuteActivity Manifest, which
handles orientation, screenSize, smallestScreenSize and screenLayout itself.
The old D21/P5.1 parenthetical equating rotation with engine destruction was
inaccurate. The roadmap now distinguishes configuration handling from a real
Activity destruction; no host rotation policy was changed.

## Actual APK mutation and release-only probes

Package mutation is opt-in and runs only on an owned API24 AVD. The driver waits
for READY_FOR_PLUGIN_UPDATE or READY_FOR_PLUGIN_UNINSTALL emitted after the old
page is visibly mounted and its JNI bridge initialized. It then performs a real
package install/replace or uninstall. The test requires exactly one
PLUGIN_UNAVAILABLE report, disposed old views, released refs and a surviving
script engine. The uninstall case also requires isAvailable to become false.

The release native probe initializes graphics-path's retained
PathIteratorPreApi34Impl through the installed plugin ClassLoader and asserts
that loader owns the class. Initializing its superclass loads the actual native
library. An available debug PathUtilities can additionally run path iteration.
Release R8 is allowed to remove that unused Kotlin utility; reflecting it in the
old probe failed on all six devices even though functional rendering passed.
The fix changes the probe, not production keep rules. Native initialization then
passed on all six devices, including 32-bit x86 and the 16 KB x86_64 AVD.

The first real update run exposed a production issue after the old session had
already closed correctly: attaching a new-loader ComposeView to the same Activity
read a ComposeViewContext retained on the Activity's view tree by the old loader,
causing a same-name ClassCastException. An explicit Recomposer alone did not
isolate this Compose1.12 context. GuardedComposeContainer now installs one distinct
SavedStateRegistryOwner identity as both public view-tree owners on its child,
before the child attaches. It delegates the original host Lifecycle and
SavedStateRegistry, while ViewModelStoreOwner still inherits from the parent.
Compose's context lookup stops inside the renderer. There is no private Compose
tag access, reflection, new lifecycle or replacement registry.

The new replacingARendererKeepsItsComposeContextOutOfTheSharedActivityAncestors
regression first leaves an older ordinary ComposeView context on the shared
Activity ancestor, then attaches two successive renderers. Their own contexts and
owner identities differ, their host lifecycle/registry/model owner remain valid,
and neither renderer consumes or overwrites the ancestor's context. The full
plugin suite passes on API24 x86 (32/32, 22.554 s) and API35 arm64 (32/32, 31.042 s).
The actual release18 -> release19 replacement also passes on API24 (6.077 s): the
same script engine mounts the new APK with a new classloader, initializes its JNI
library and continues displaying the new page. Actual uninstall passed separately
(3.147 s). These are real package operations, not a mocked inventory notification.

Plugin build19 is autojs6-plugin-compose-ui-v1.0.0-5b2f63ce.apk, 2375069 bytes,
SHA-256 317316ff9ab160d4f235e667a219aac6496f9e431bc6b5a80c2977a250e62b4f.
JVM63/63, debug/androidTest/release assembly, signing/native alignment and debug
lint (zero issues) pass. The generated 36 documents match their sources.

The additional plugin-suite attempt on API37.1 passed its five standalone
contract cases but could not run the 27 ComposeTestRule cases: the resolved
Espresso dependency reflects InputManager.getInstance, which that system removed.
Those 27 failures are recorded as a test-infrastructure incompatibility, not
passes. API37's actual host matrix and inrt tests use public UiAutomation and do
not rely on that reflection. No hidden-API workaround or dependency-lock bypass
was introduced to make the optional plugin-suite attempt pass.

## Verification boundaries

Host selected JVM tests: 189/189 (24 classes, zero failures/errors/skips), including
eight hostile-script/ownership, five package-subscription and one loader-cache
test. Frozen API JVM tests:
16/16. The full host JVM suite and full host lint were not run. The 100-cycle test
asserts resource ownership and disposal; it is not a heap reachability proof or
long-running LeakCanary certification. Actual PSS measurements belong to P5.3.

Compatibility and the real inrt application are documented separately under
P5.2. Performance observations and proposed thresholds belong to P5.3/Q5. No
roadmap stage is added, split or dropped. All work remains local under D7.

Final source validation uses host5317 with all P5 fixes and release19. The combined
six-device matrix passes 110 cases; API24 and API35 each include all seven
robustness cases (23/23 totals in 52.384 s and 40.733 s). Real APK replacement and
uninstall were repeated with this host: 1/1 in 5.616 s and 1/1 in 3.493 s. The host
app/debug-test and inrt release Runtime Kit rebuilt successfully, including native
alignment; selected JVM189 and API16 passed with zero skips. Cleanup of the owned
devices and the final session checkpoint are recorded in the P5 completion entry.
