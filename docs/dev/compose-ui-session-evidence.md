# P1.2 host/session and V1 preview evidence

Date: 2026-10-02. Host baseline d9b090fd68 on the isolated spike/compose-ui-p0 branch
(6.8.0 / 5309); plugin baseline 340c699 (1.0.0 / build 9), delivered plugin build 10.
The original host worktree was not edited or staged. Only local commits are authorized.

## Delivered work

- ComposeUiPluginHost checks installation, system/center enablement, authorization, host minimum,
  INFO/factory metadata, contract range and the exact shared dependency fingerprint. Four new
  selection messages are translated in all 11 host resource directories; existing plugin messages
  are reused. Lookup rechecks gates even on cache hits. APK code is cached per package/version/path/
  update time, and package install/update/removal invalidates the cache. Enable/trust/component
  changes invalidate selection while retaining identical code to preserve JNI loader ownership.
- ComposeUiPluginLoader uses a parent-first PathClassLoader with the current process ABI's APK
  native search path. ComposeHostContext delegates configured plugin resources while retaining
  host theme/services. ComposeFloatyOwners supplies lifecycle/saved-state owners before attachment.
- ComposeSession owns a monotonic node allocator/reconciler, callback bindings, one pending target,
  one in-flight transaction, bounded event queue and script/main dispatch. Successful apply advances
  tree, generation and callback bindings together. Rejection keeps the previous tree/generation and
  discards dependent pending work. Close fences event ingress, clears callbacks/trees and disposes
  without a synchronous wait across threads. Activity/window adapters release their Context,
  mount closure, lifecycle observer and theme bridge.
- TreeReconciler matches parent-scoped keys and same-type indices, preserves IDs on moves, handles
  slot updates/replacements and warns once for unkeyed lists. ComposeSessionScope enforces 8 sessions
  per engine. ScriptAsyncDispatcher integration captures the origin timer; P3/P4 will wire the actual
  script API and engine/window exit paths.
- ComposeThemeBridge delivers theme seed/night changes and unregisters on close. The weak theme
  listener becomes inert on close. Full renderer color/typography/dynamic-color mapping remains P2.
- The plugin factory now implements V1, advertises only Column/Text/Button with no optional features,
  and reports the BOM-resolved Compose runtime version and existing shared fingerprint. Its minimal
  implementation accepts Text.text, Button.enabled/click, primary/content-slot children and four
  modifiers: padding, fillMaxWidth, testTag, semantics. Other properties/modifiers/commands fail with
  typed errors. NodeStore validates and publishes whole immutable transactions, including cycle,
  identity, ownership, scope, callback and size constraints. It bounds intermediate node growth and
  rejects ancestry cycles before mutation. Deletion is iterative even when intermediate moves form
  a deep chain; an exact 2000-operation regression creates/removes a 2000-node chain before final
  validation without consuming the Java call stack.
- P0 renderer/loader/session fixtures are retired. Unused api.spike definitions remain only in the
  unchanged frozen AAR; its SHA-256 remains e6024147dd45776e1f0bc178da66a1a337e9d084cbe3dbcf244291e20857ba21.
  V1 signatures, snapshot and wire tags are unchanged. No new third-party dependency was introduced.

The dependency fingerprint remains f3042acc624d499feea9907a20257b62debaa6c523f40bfce19b084274334576
(51 components). Every host APK preBuild now runs the four-variant shared classpath gate, preventing
an APK from advertising a lock fingerprint that its actual resolved dependencies do not satisfy.
Plugin README/instructions/changelog sources were updated in all 10 languages. The host has no new
public script entry in this milestone, so its existing Compose API dependency changelog is retained;
the user-facing module feature belongs to P1.3/P3 once the entry is available.

## Verification

| Check | Result |
| --- | --- |
| Host Compose JVM | 16 pass: reconciliation 5, session limits/dispatch/rollback 4, callbacks 2, selection 2, shared classpaths 3 |
| Frozen API JVM | 13 pass, including exact V1 snapshot and no-Compose host source guard |
| Plugin JVM | 25 pass, including 6 NodeStore tests replacing the retired P0 tree tests |
| Host APKs | app debug + instrumentation assemble; native 16 KB alignment gate passes |
| Shared classpaths | app/inrt debug/release resolve the same 51 shared components; no Compose implementation in host |
| Plugin APKs/lint | debug, instrumentation, signed/minified release assemble; debug lint clean; release 0 errors, 3 pre-existing unused identity-resource warnings |
| API 24 x86 | Temporary clean AVD emulator-5560: 7/7 host tests with debug plugin; 2/2 counter/dispose + standalone INFO tests with minified release |
| API 35 arm64 | Xiaomi Pad 6 Pro 968e9f18: 7/7 host tests with debug plugin; 2/2 counter/dispose + standalone INFO with minified release |
| Plugin instrumentation | Xiaomi API 35, matching debug APK/test APK: 5/5 activation/discovery/native inventory tests |
| Docs/icons | 36 generated Markdown artifacts consistent; 2 generated icon assets consistent |
| Local release | autojs6-plugin-compose-ui-v1.0.0-dd04919c.apk, 1460582 bytes, CRC32 dd04919c; signature, exact native inventory and ELF/zip 16 KB alignment pass |
| DEX boundary | debug 9255392 bytes / 88527 method references; release 1460582 bytes / 28245 references; no host API/shared AndroidX/coroutine definitions bundled |

Device cases cover parent class identity, cache-hit gate rechecks, resource locale/density/night
alignment, real graphics-path iteration (native on API 24), Activity and RawWindow owners, queued
counter callbacks and SetProps, stable node IDs, detach/reattach, callback/tree/composition release,
theme changes, bounded event overflow, stale/closed event rejection and standalone INFO without
host API classes in the plugin process. Release validation stays on V1/Android APIs, without
requiring unminified Compose class/member names. Debug additionally checks hasComposition=false.

The initial existing API 24 AVD was covered by the original host's full-screen inspector window.
Its test Activity was RESUMED but mCurrentFocus belonged to the original host. API 33 Redmi and
some API 35 runs also exposed stale accessibility-node observations: the diagnostic state already
showed count=1, generation=2, one callback and no errors. The test now scopes window lookup to its
own package and refreshes virtual nodes before reading. Final clean-device runs above all pass.
The original inspector was not closed. IDE debugging was attempted, but the host worktree is outside
the IDE project and no matching session existed; runtime evidence came from device window state,
test-owned counters and accessibility observations, not a claimed debugger trace.

## Reproduction and limits

Use the isolated host flag -PcomposeUiSpike=true, and suppress automatic build/time increments.
Run :app:testAppDebugUnitTest --tests org.autojs.autojs.core.plugin.compose.*,
:plugin-api:compose-ui-api:testDebugUnitTest, :app:assembleAppDebug and
:app:assembleAppDebugAndroidTest. Install the plugin debug APK before executing
org.autojs.autojs.core.plugin.compose.ComposeUiLoaderTest from the dedicated host test package.
For a release plugin, run activityCounterUsesV1TransactionsHostOwnersAndClosesComposition and
standaloneInfoStillRunsWithoutTheHostProvidedApiClasses from that class. Always specify a serial.

Plugin verification uses the normal JVM/assemble/lint/appendDigestToReleasedFiles gates plus
.python/verify_apk_classpath.py. This milestone does not claim whole-host JVM/lint coverage,
assembled release/inrt host compatibility, hot replacement during an active rendering session,
heap-dump leak proof, the full device matrix, IME/TalkBack/performance acceptance or remote CI.
Dispose evidence establishes released composition/callback/tree/attachment state; P6 measures
long-running retention and performance. Host release currently does not enable minification.

Next: P1.3 plugin-center registration and final minimum host version. The current 5308 minimum is
still provisional. P2.1 NodeStore is delivered as a dependency of the P1.2 proof, while complete
renderer dispatch/value/theme handling and the remaining P2 tests remain unchecked. No public
compose JS global is delivered yet. No push, official index change or Release publication occurred.

## Cleanup

All 10 packages installed on the reused API 24 AVD, Redmi API 33 and Xiaomi API 35 for this
session were uninstalled successfully. Only this session's /data/local/tmp diagnostic files
were removed; no /sdcard data was touched. The temporary compose_ui_p1_api24 AVD was shut down,
its registration was checked against the app/build-owned path, and avdmanager delete removed
both registration and data directory (both paths verified absent). The existing AVD stayed running.
