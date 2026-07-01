# AutoJs6 Node.js Error Catalog

This catalog documents the current user-visible AutoJs6 Safe Node Profile error codes, including `ERR_AUTOJS6_*`
codes and Node-compatible errors kept for developer familiarity.
It is verified by `:app:verifyNodeErrorCatalog` against `NodeRuntimeErrorCodes.kt` and the runtime bootstrap sources.

Prefer `error.autojs6Code` when it exists. Some compatibility paths preserve a legacy `error.code` and attach the
canonical AutoJs6 value through `autojs6Code` and `legacyCode`.

## ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED

- Meaning: A Node bridge call needs a capability that the project did not declare.
- Common cause: `project.json node.permissions` or `package.json autojs6.node.permissions` is present but omits the module or method capability.
- Example: calling `require("toast").showToast()` with a deny-by-default capability manifest that does not include `toast`.
- How to fix: declare the required bridge capability, or remove the manifest while migrating legacy scripts.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_BRIDGE_CANCELLED

- Meaning: A Node bridge call was cancelled before the provider completed it.
- Common cause: an `AbortSignal` fired, a provider-owned permission UI was dismissed, or a resource was closed while a bridge call was pending.
- Example: aborting `dialogs.alert()` through an `AbortController`.
- How to fix: treat cancellation as a normal control path and clean local state in `finally`.
- Related docs: [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_BRIDGE_INVALID_REQUEST

- Meaning: A bridge RPC message is malformed or cannot be dispatched safely.
- Common cause: invalid internal request JSON, missing module or method, invalid resource handle, or fuzzed bridge input.
- Example: an internal bridge test sends a message without a valid method.
- How to fix: use the public bridge modules instead of constructing raw bridge messages.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED

- Meaning: The Android or AutoJs provider refused the requested bridge operation.
- Common cause: accessibility, screen capture, image, shell, or work-manager authority is unavailable or outside policy.
- Example: `await require("accessibility").click(100, 100)` while the accessibility provider is not enabled.
- How to fix: enable the required Android capability, use a supported provider, or keep the operation behind a feature check.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## P14-16 UI/automation/media denial sync

P14-16 docs sync does not add a new error code. It records the expected denial
surface for UI/automation/media migration examples while
`pro.ui_automation_media` remains `provider_poc_not_promoted`:

- Missing project/package capability metadata rejects before provider dispatch
  with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`.
- Unpromoted provider paths such as overlay creation without promoted
  permission/disclosure evidence, accessibility when the service is unavailable,
  MediaProjection capture from the Node bridge, advanced image operations,
  packaged OCR/model lookup, and recorder sessions remain
  `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` or provider-specific stable denials.
- Provider failures after a declared and permitted request still use
  `ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED`; callers should not interpret provider
  failure as permission or profile promotion.

## P14-17 task database mutation denial sync

P14-17 does not add a new error code or expose `require("tasks")`. It records
the stable denial surface required before future task database mutation can be
promoted:

- Missing future `tasks` capability metadata must reject before provider
  dispatch with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED` or the existing
  disabled-module error shape.
- Invalid JSON descriptors, invalid JSON patches, owner/id namespace mismatch,
  and legacy numeric `TimedTask` ids used as mutable handles must reject with
  `ERR_AUTOJS6_BRIDGE_INVALID_REQUEST` or a future provider-specific stable
  validation code.
- `compat.tasks.addDailyTask()`, `addWeeklyTask()`, and IntentTask helpers
  remain `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED`; `compat.tasks.updateTask()`
  remains `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`.
- Raw `TimedTask`, scheduler, database cursor, mutable row handle,
  `IntentTask`, `PendingIntent`, `Intent`, and Java objects remain blocked.

## P14-18 task schema migration rollback denial sync

P14-18 does not add a new error code. It records how future schema migration
and rollback failures must stay stable:

- Current and older descriptor fixtures remain contract-only until a live
  migrator exists; mutation attempts still use the P14-17 denial surface.
- Corrupt descriptors must fail closed, be quarantined, and avoid scheduler
  reschedule; callers should receive stable invalid-request diagnostics rather
  than a raw database or parser exception.
- Unknown future schema descriptors must be preserved without mutation and
  reported as unsupported or invalid for the current provider version.
- Packaged owner mismatch must deny cross-package aliasing and must not delete
  main-app or legacy Rhino rows.

## P14-19 packaged task scheduling denial sync

P14-19 does not add a new error code or promote packaged `tasks` database
rows. It records the denial and rollback expectations for future packaged task
promotion:

- Packaged `work_manager` smoke may schedule/list/cancel only through the
  declared `work_manager` bridge capability; undeclared capability metadata
  remains `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`.
- Future packaged `tasks` database rows must reject cross-package owner/id
  mismatches with stable invalid-request or permission-denied diagnostics.
- Task removal and Safe downgrade must disable or remove only rows owned by
  the packaged app identity and must not mutate main-app or legacy Rhino rows.
- Process-death, relaunch, disclosure, stop-action, and crash recovery gaps
  stay future-promotion blockers rather than release-blocking errors.

## P14-20 IntentTask/broadcast denial sync

P14-20 does not add a new error code or promote `tasks.broadcast`. It records
the denial and cleanup expectations for future broadcast task promotion:

- `require("tasks")`, `require("tasks.broadcast")`, global `tasks`, and
  future task/broadcast capabilities stay unavailable until a live provider is
  promoted.
- `compat.tasks.addIntentTask()`, `queryIntentTasks()`, `getIntentTask(id)`,
  and `removeIntentTask(id)` continue to reject before bridge dispatch with the
  existing Rhino compatibility unsupported path.
- Exported receivers, boot/user-unlocked/package/startup/exact-alarm triggers,
  manifest mutation, and external broadcast wakeups remain permission-denied or
  unavailable until explicit metadata, user disclosure, and device evidence
  exist.
- Trigger payload failures must surface stable invalid-request or
  permission-denied diagnostics, never raw `Intent`, `Bundle`, `Uri`, receiver
  handle, `PackageManager`, or database exceptions.

## P14-21 packaged broadcast trigger/remove denial sync

P14-21 does not add a new error code or promote packaged broadcast-triggered
execution. It records packaged denial and cleanup expectations:

- Current packaged IntentTask/broadcast smoke must keep future modules and
  metadata denied until `tasks.broadcast` is promoted.
- Positive packaged trigger/remove smoke remains future work and must prove
  install, register, payload validation, delivery, remove cleanup, relaunch,
  crash recovery, and undeclared-capability denial before promotion.
- Remove cleanup must disable or delete only descriptors and receiver patches
  owned by the packaged app identity, cancel pending owned executions, prevent
  post-remove delivery, and preserve legacy Rhino rows.
- Exported receivers remain unavailable unless a later gate adds explicit
  metadata, allowlists, permission policy, abuse review, and device evidence.

## P14-22 notification ownership denial sync

P14-22 does not add a new error code or promote packaged notification
foreground disclosure. It records ownership and denial expectations:

- Missing `notifications` or `notifications.settings` capabilities still reject
  before bridge dispatch with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`.
- Missing Android `POST_NOTIFICATIONS` permission or disabled app
  notifications still reject notification posting with
  `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`.
- `cancel` and `cancelAll` must close only provider-owned active notification
  records; unknown ids remain no-op and must not expose or cancel raw external
  notification handles.
- Actions, groups, styles, inline reply, remote input, listeners,
  `RemoteViews`, DND mutation, foreground disclosure stop action, and packaged
  post/cancel behavior stay future-promotion blockers.

## ERR_AUTOJS6_BRIDGE_PROCESS_DEAD

- Meaning: The bridge process or service was destroyed before a pending request completed.
- Common cause: user stop, service shutdown, or native process termination.
- Example: a pending dialog or image request rejects after the Node service is stopped.
- How to fix: handle rejection, clean resources in `finally`, and avoid assuming long bridge calls survive service stop.
- Related docs: [TESTING.md](TESTING.md), [docs/nodejs/design/LONG_RUNNING_SERVICE.md](design/LONG_RUNNING_SERVICE.md).

## ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED

- Meaning: A bridge provider failed while handling a declared and permitted request.
- Common cause: provider-side exception, unavailable Android subsystem, invalid provider state, or a failed fake-provider test path.
- Example: `require("ocr").recognize(image)` rejects after the OCR provider reports an internal failure.
- How to fix: handle the rejection, verify the provider is available, and inspect provider diagnostics for the specific module.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_BRIDGE_RATE_LIMITED

- Meaning: A bridge module rejected the call because its request rate limit was exceeded.
- Common cause: tight polling loops over accessibility or other bounded bridge APIs.
- Example: repeatedly calling `findAll()` without a delay.
- How to fix: add backoff, batch work, or lower polling frequency.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT

- Meaning: A bridge resource quota was exceeded.
- Common cause: too many pending bridge calls, live image handles, shell commands, fetch requests, or WebSocket sessions.
- Example: opening more controlled WebSocket sessions than the active profile allows.
- How to fix: close or recycle handles, await pending calls, and keep concurrency under profile limits.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_BRIDGE_TIMEOUT

- Meaning: A bridge call did not complete before its timeout.
- Common cause: a provider did not respond, a permission UI stalled, or a fake-provider test intentionally timed out.
- Example: `await dialogs.input()` rejects when the service times out the request.
- How to fix: pass an appropriate timeout where supported and handle the rejection path.
- Related docs: [TESTING.md](TESTING.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN

- Meaning: A Rhino-style compatibility API was rejected because it would cross the Safe Node Profile boundary.
- Common cause: a migrated script expects raw Java, Rhino `Scriptable`, Android UI/accessibility objects, `importClass`, `importPackage`, or `JavaAdapter`.
- Example: asking the compat loader to expose `importClass`.
- How to fix: use a JSON-safe Node bridge facade, or keep the script on the Rhino engine until a safe migration path exists.
- Related docs: [design/RHINO_COMPAT_LOADER.md](design/RHINO_COMPAT_LOADER.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION

- Meaning: A Rhino compatibility loader or facade option was malformed.
- Common cause: passing a non-object options value to `require("autojs6:compat").install()`.
- Example: `compat.install("globals")`.
- How to fix: pass an options object and keep global installation explicit.
- Related docs: [design/RHINO_COMPAT_LOADER.md](design/RHINO_COMPAT_LOADER.md).

## ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED

- Meaning: A Rhino-style compatibility API is not implemented in the current Node compat facade.
- Common cause: using a Rhino API before its Phase 11 facade task has landed.
- Example: `compat.assertSupported("files.read")` before the scoped files facade is implemented.
- How to fix: use the current Node bridge module directly, or wait for the matching Phase 11 compat facade task.
- Related docs: [design/RHINO_COMPAT_LOADER.md](design/RHINO_COMPAT_LOADER.md), [RHINO_AUGMENT_COMPATIBILITY_MATRIX.md](RHINO_AUGMENT_COMPATIBILITY_MATRIX.md).

## ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED

- Meaning: Rhino `JavaAdapter` is not available inside the Node runtime.
- Common cause: a migrated script expects `new JavaAdapter(...)` to create Java listener or interface implementations from V8.
- Example: `require("rhino").JavaAdapter(android.view.View.OnClickListener, handlers)`.
- How to fix: keep the script on the Rhino engine for now, or wait for the P13-16 bounded `java.defineClass()` / proxy-interface provider.
- Related docs: [design/RHINO_SHIM.md](design/RHINO_SHIM.md), [design/JAVA_INTEROP.md](design/JAVA_INTEROP.md).

## ERR_AUTOJS6_BUILTIN_DISABLED

- Meaning: A Node builtin is disabled by the Safe Node Profile.
- Common cause: requiring `child_process`, `inspector`, `dgram`, `wasi`, or another denied builtin.
- Example: `require("child_process")`.
- How to fix: use the AutoJs-controlled module for the task, or keep the dependency out of the stable profile.
- Related docs: [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_DYNAMIC_IMPORT_UNSUPPORTED

- Meaning: dynamic `import()` is disabled in the current profile.
- Common cause: an ESM or bundler output uses `import()` while the dynamic-import feature flag is off.
- Example: `await import("./module.mjs")` in a stable build.
- How to fix: use CommonJS `require()` for stable scripts, or run only in an explicitly enabled experimental build.
- Related docs: [design/ESM.md](design/ESM.md), [EXPERIMENTAL_PROMOTION_POLICY.md](EXPERIMENTAL_PROMOTION_POLICY.md).

## ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED

- Meaning: Legacy code value for a disabled builtin.
- Common cause: compatibility path preserving the old `error.code` while attaching `autojs6Code=ERR_AUTOJS6_BUILTIN_DISABLED`.
- Example: `require("vm")` may expose this as the legacy code.
- How to fix: treat it as `ERR_AUTOJS6_BUILTIN_DISABLED` and remove or replace the dependency.
- Related docs: [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_EMBEDDED_NODE_DYNAMIC_IMPORT_UNSUPPORTED

- Meaning: Legacy code value for unsupported dynamic import.
- Common cause: compatibility path preserving the old `error.code` while attaching `autojs6Code=ERR_AUTOJS6_DYNAMIC_IMPORT_UNSUPPORTED`.
- Example: `import("file:///tmp/x.mjs")`, `eval("import('./x.mjs')")`, or an explicit diagnostic request with dynamic import disabled.
- How to fix: use local-only dynamic import from script source, avoid URL/protocol specifiers and dynamic-code constructors, or keep CommonJS.
- Related docs: [design/ESM.md](design/ESM.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED

- Meaning: ESM execution is not enabled for this path, or the requested ESM syntax is outside the partial profile.
- Common cause: ESM was explicitly disabled for a diagnostic run, static `import`/`export` syntax was used in a CommonJS entry, JSON import attributes were missing/unknown, or an unsupported ESM form outside the scoped v1.1 subset was used.
- Example: `import data from "./data.json"` without `with { type: "json" }`, legacy `assert { type: "json" }`, or an import attribute on a non-JSON module.
- How to fix: use supported partial ESM syntax, add `with { type: "json" }` for JSON imports, move static imports into a `.mjs` or `type=module` entry, or keep CommonJS syntax in `.cjs`/CommonJS files.
- Related docs: [design/ESM.md](design/ESM.md), [QUICK_START.md](QUICK_START.md).

## ERR_AUTOJS6_EMBEDDED_NODE_FETCH_DISABLED

- Meaning: Legacy code value for disabled controlled fetch or network access.
- Common cause: compatibility path preserving the old `error.code` while attaching `autojs6Code=ERR_AUTOJS6_NETWORK_DISABLED`.
- Example: `await fetch("https://example.com")` when the live network provider is disabled.
- How to fix: keep network behind an explicit experimental gate, or use fake providers in tests.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_EMBEDDED_NODE_INVALID_STATE

- Meaning: The embedded runtime rejected an operation because the runtime state was not valid for it.
- Common cause: calling an internal API after destroy, before start, or during an incompatible lifecycle phase.
- Example: a native bridge path receives an operation after the runtime has begun shutdown.
- How to fix: treat it as a runtime bug unless the script is using internal APIs; collect a Node Doctor report and logcat.
- Related docs: [NATIVE_CRASH_DIAGNOSTICS.md](NATIVE_CRASH_DIAGNOSTICS.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_EMBEDDED_NODE_NETWORK_DISABLED

- Meaning: Legacy code value for disabled network access.
- Common cause: compatibility path preserving the old `error.code` while attaching `autojs6Code=ERR_AUTOJS6_NETWORK_DISABLED`.
- Example: a limited raw network shim rejects before I/O.
- How to fix: use controlled network only in approved experimental builds.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT

- Meaning: Legacy code value for a script timeout.
- Common cause: a script exceeded the configured embedded runtime timeout.
- Example: `setInterval(() => {}, 1000)` without a stop path.
- How to fix: clear timers, finish work, or adjust the script timeout when the workload is intentionally long.
- Related docs: [TESTING.md](TESTING.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_EMBEDDED_NODE_UNSUPPORTED_OPTION

- Meaning: An option was recognized but is unsupported by the Safe Node Profile.
- Common cause: unsupported fs/glob options, unsupported execution options, or an internal compatibility option.
- Example: using scoped glob options that follow symlinks outside the working directory, or requesting `executionMode: "interactiveLongRunning"` from a normal script launch instead of the explicit interactive session surface.
- How to fix: remove the unsupported option and stay within the documented profile.
- Related docs: [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED

- Meaning: scoped fs denied an absolute path outside the current sandbox root.
- Common cause: a script passed `/sdcard/...`, `/proc/...`, `/sys/...`, `/dev/...`, or another outside-root absolute path to `fs`.
- Example: `fs.readFileSync("/proc/version")`.
- How to fix: keep the file inside the working directory and use either a
  relative path or an absolute path that resolves under that scoped root.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [design/FS_PROMISES.md](design/FS_PROMISES.md).

## ERR_AUTOJS6_FS_NUL_BYTE

- Meaning: scoped fs denied a path containing a NUL byte.
- Common cause: malformed input, fuzzed path data, or unsafe string concatenation.
- Example: `fs.readFileSync("bad" + String.fromCharCode(0) + "path")`.
- How to fix: validate or sanitize path input before calling `fs`.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_FS_PATH_ESCAPE

- Meaning: scoped fs denied a path that escapes the working directory.
- Common cause: `..` traversal or package target resolution outside the allowed root.
- Example: `fs.readFileSync("../secret.txt")`.
- How to fix: keep file paths relative to the script working directory and do not traverse above it.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_FS_REALPATH_ESCAPE

- Meaning: scoped fs denied a path whose canonical realpath escapes the working directory.
- Common cause: symlink chains or realpath resolution pointing outside the scoped root.
- Example: reading `outside-link/secret.txt` when the link targets an external file.
- How to fix: copy required files into the project root instead of using escaping symlinks.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_FS_SCOPED_PATH

- Meaning: generic scoped fs policy denial.
- Common cause: a filesystem operation cannot be represented safely inside the current scoped policy.
- Example: an unsupported path form reaches the scoped fs validator.
- How to fix: use ordinary relative paths inside the working directory.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [design/FILE_URL_POLICY.md](design/FILE_URL_POLICY.md).

## ERR_AUTOJS6_FS_SYMLINK_ESCAPE

- Meaning: scoped fs denied a symlink escape.
- Common cause: a symlink inside the project points outside the allowed working directory.
- Example: a dependency contains a link to a parent directory.
- How to fix: replace the symlink with copied files or remove the dependency path.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_FS_WATCH_LIMIT

- Meaning: the scoped fs watcher quota for the current embedded execution was exceeded.
- Common cause: opening too many active `fs.watch`, `fs.promises.watch`, or `fs.watchFile` handles without closing them.
- Example: creating a watcher per file in a large tree and leaving all handles active.
- How to fix: close watchers promptly, multiplex directory watches where safe, or poll a bounded set of files.
- Related docs: [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_FS_WATCH_RATE_LIMITED

- Meaning: a scoped fs watcher produced more events than the per-watcher rate limit allows.
- Common cause: a tight file write loop, a noisy generated output directory, or manually emitted watcher events.
- Example: rewriting the same watched file many times in one second.
- How to fix: debounce file changes, close noisy watchers, and re-read scoped state after a quiet period.
- Related docs: [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_JAVA_CLASS_DENIED

- Meaning: controlled Java interop denied access to a class.
- Common cause: the class is outside the allowlist or belongs to a blocked category.
- Example: requesting `java.lang.Runtime`.
- How to fix: avoid Java interop in stable scripts, or use only allowlisted experimental classes in debug validation.
- Related docs: [design/JAVA_INTEROP.md](design/JAVA_INTEROP.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_JAVA_INTEROP_DISABLED

- Meaning: Java interop is disabled in the current profile.
- Common cause: the Java interop experimental flag is off.
- Example: requiring or calling the Java bridge in a normal build.
- How to fix: keep the feature out of release scripts unless a future promotion task enables it.
- Related docs: [design/JAVA_INTEROP.md](design/JAVA_INTEROP.md), [EXPERIMENTAL_PROMOTION_POLICY.md](EXPERIMENTAL_PROMOTION_POLICY.md).

## ERR_AUTOJS6_JAVA_METHOD_DENIED

- Meaning: controlled Java interop denied a method or constructor call.
- Common cause: a method is not allowlisted, mutates unsafe state, or exposes reflection/loading.
- Example: calling a blocked static method on an otherwise visible class.
- How to fix: use approved bridge APIs instead of raw Java calls.
- Related docs: [design/JAVA_INTEROP.md](design/JAVA_INTEROP.md), [security/THREAT_MODEL_V1.md](security/THREAT_MODEL_V1.md).

## ERR_AUTOJS6_JAVA_REFLECTION_DENIED

- Meaning: Java reflection access was denied.
- Common cause: using `Class`, reflection APIs, class loaders, or reflective method lookup through interop.
- Example: attempting to load a class reflectively.
- How to fix: do not use reflection; request a narrow AutoJs bridge method if the capability is needed.
- Related docs: [design/JAVA_INTEROP.md](design/JAVA_INTEROP.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_MODULE_NOT_FOUND

- Meaning: the AutoJs6 module resolver could not resolve the requested module.
- Common cause: missing file, missing dependency, blocked package export, or unsupported package layout.
- Example: `require("missing-package")`.
- How to fix: vendor the dependency, fix `package.json`, or run resolver trace/visualizer diagnostics.
- Related docs: [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_MODULE_SOURCE_BUDGET_EXCEEDED

- Meaning: injected moduleSources exceeded the Safe Node Profile budget before native Node startup.
- Common cause: too many preloaded modules, a single module over `16777216` UTF-8 bytes, or total moduleSources over `67108864` UTF-8 bytes.
- Example: packaging a large CommonJS graph into encrypted moduleSources.
- How to fix: reduce bundled dependencies, split unsupported packages out of the packaged graph, or keep the app on disk-backed scoped `fs` instead of injected moduleSources.
- Related docs: [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_NATIVE_ADDON_DISABLED

- Meaning: native `.node` addons are disabled.
- Common cause: a dependency resolves to a `.node` binary through relative import, package `main`, `exports`, or `imports`.
- Example: `require("./binding.node")`.
- How to fix: use a pure JavaScript package or disable the dependency path for AutoJs6.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_NETWORK_ABORTED

- Meaning: a controlled network operation was aborted.
- Common cause: an AbortSignal fired, the provider cancelled the call, or service shutdown cancelled active I/O.
- Example: aborting `fetch()` before the response completes.
- How to fix: handle abort separately from network failure and clean local state.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [design/WEBSOCKET.md](design/WEBSOCKET.md).

## ERR_AUTOJS6_NETWORK_DISABLED

- Meaning: controlled network access is disabled in the current build or provider.
- Common cause: normal builds keep live network providers disabled; default raw network modules also reject before I/O unless the explicit raw Node network module opt-in is enabled.
- Example: `await fetch("https://example.com")` in a normal build.
- How to fix: use fake providers in tests or an explicitly approved experimental network build.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_NETWORK_ERROR

- Meaning: a controlled network operation failed with a provider or transport error that is not one of the more specific network codes.
- Common cause: provider exception, transport failure, rejected axios-compatible request wrapper, or an unexpected fake-provider failure.
- Example: a controlled axios request rejects without a timeout, abort, size, URL, permission, or policy-specific code.
- How to fix: inspect the wrapped error details, retry only when the operation is idempotent, and keep live network behind the approved experimental gate.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [design/WEBSOCKET.md](design/WEBSOCKET.md).

## ERR_AUTOJS6_NETWORK_INVALID_URL

- Meaning: a controlled network URL is invalid or unsupported.
- Common cause: unsupported scheme, malformed URL, invalid redirect target, or a forbidden URL form.
- Example: `fetch("file:///sdcard/a.txt")`.
- How to fix: use `http:` or `https:` for fetch, or `ws:` or `wss:` for WebSocket where the feature is enabled.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [design/WEBSOCKET.md](design/WEBSOCKET.md).

## ERR_AUTOJS6_NETWORK_PERMISSION_DENIED

- Meaning: Android or profile permissions denied the network request.
- Common cause: missing packaged `INTERNET` permission for an approved network feature or provider policy denial.
- Example: controlled fetch in a packaged APK without the required manifest policy.
- How to fix: keep network disabled for stable packages or use the documented manifest gate when the feature is promoted.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_NETWORK_POLICY_DENIED

- Meaning: controlled network policy rejected the request.
- Common cause: redirect overflow, concurrent-request overflow, unsupported method/body combination, or max connection limit.
- Example: a GET request with a body or too many active WebSocket sessions.
- How to fix: reduce concurrency, fix request options, and stay inside the documented limits.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [design/WEBSOCKET.md](design/WEBSOCKET.md).

## ERR_AUTOJS6_NETWORK_RESPONSE_TOO_LARGE

- Meaning: a network response or WebSocket message exceeded the configured size budget.
- Common cause: response body or message is larger than the Safe Node Profile limit.
- Example: downloading a large binary with controlled fetch.
- How to fix: request smaller payloads or wait for a future streamed API design gate.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [design/WEBSOCKET.md](design/WEBSOCKET.md).

## ERR_AUTOJS6_NETWORK_TIMEOUT

- Meaning: a controlled network operation exceeded its timeout.
- Common cause: slow server, unavailable network, connect timeout, or provider timeout.
- Example: `fetch()` to a non-responsive endpoint.
- How to fix: handle timeout, lower workload size, or pass an appropriate timeout where supported.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [design/WEBSOCKET.md](design/WEBSOCKET.md).

## ERR_AUTOJS6_NODE_DISABLED

- Meaning: embedded Node is disabled or unavailable for a Node-marked script.
- Common cause: runtime kill switch, Rhino-only flavor, missing native library, or packaged template without Node support.
- Example: running a `"nodejs";` script while embedded Node is disabled.
- How to fix: enable the Node runtime or use a build/flavor that includes embedded Node.
- Related docs: [ROLLBACK_PLAYBOOK.md](ROLLBACK_PLAYBOOK.md), [releases/NODE_PROFILE_V1_KNOWN_ISSUES.md](releases/NODE_PROFILE_V1_KNOWN_ISSUES.md).

## ERR_AUTOJS6_NODE_ENGINE_BUSY

- Meaning: an embedded Node execution is already active in the service process.
- Common cause: a second script starts while the first is still running and explicit enqueue is not requested.
- Example: launching two embedded Node scripts concurrently.
- How to fix: wait for the active script to finish or use the bounded execution queue where supported.
- Related docs: [design/MULTI_INSTANCE.md](design/MULTI_INSTANCE.md), [design/EXECUTION_QUEUE.md](design/EXECUTION_QUEUE.md).

## ERR_AUTOJS6_NODE_QUEUE_CANCELLED

- Meaning: a queued embedded Node execution was cancelled before it started.
- Common cause: user stop, service destroy, or explicit queue cancellation by execution id.
- Example: cancelling a queued script while another script is still active.
- How to fix: treat it as a normal cancellation and resubmit only if the user requested it.
- Related docs: [design/EXECUTION_QUEUE.md](design/EXECUTION_QUEUE.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_NODE_QUEUE_FULL

- Meaning: the bounded embedded execution queue is full.
- Common cause: too many scripts requested enqueue while another script is running.
- Example: enqueueing a third execution when the profile allows only one queued execution.
- How to fix: throttle launches and wait for queued work to finish.
- Related docs: [design/EXECUTION_QUEUE.md](design/EXECUTION_QUEUE.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_NODE_QUEUE_TIMEOUT

- Meaning: a queued execution did not acquire the active slot before its queue timeout.
- Common cause: the active script ran longer than the queue wait budget.
- Example: a queued short script times out behind a long-running timer script.
- How to fix: shorten the active script, increase the queue timeout where supported, or avoid queueing.
- Related docs: [design/EXECUTION_QUEUE.md](design/EXECUTION_QUEUE.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_NODE_TEST_ASYNC_ACTIVITY

- Meaning: the embedded `node:test` subset detected async activity after a test completed.
- Common cause: a test leaves timers or other pending work active after its body resolves.
- Example: a test schedules `setTimeout()` and returns before clearing or awaiting it.
- How to fix: await asynchronous work, clear timers, and keep node:test samples bounded.
- Related docs: [TESTING.md](TESTING.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_NODE_TEST_FAILURE

- Meaning: one or more embedded `node:test` subset cases failed.
- Common cause: assertion failure, rejected test body, failed subtest, or a test harness diagnostic path.
- Example: `assert.equal(actual, expected)` fails inside a sample compatibility test.
- How to fix: inspect the test diagnostics and fix the failing case before treating the profile gate as passed.
- Related docs: [TESTING.md](TESTING.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_NODE_TEST_TIMEOUT

- Meaning: an embedded `node:test` subset case exceeded its timeout.
- Common cause: a hung promise, uncleared timer, blocked event loop, or a test body that never resolves.
- Example: a test awaits a promise that is never settled.
- How to fix: add bounded waits, settle pending promises, and close handles before the test exits.
- Related docs: [TESTING.md](TESTING.md), [PERFORMANCE.md](PERFORMANCE.md).

## ERR_AUTOJS6_FOREGROUND_SERVICE_START_NOT_ALLOWED

- Meaning: Android denied starting the embedded Node foreground service from the current app state.
- Common cause: background foreground-service start restrictions on Android 12+.
- Example: launching an interactive Node runtime while the app is not allowed to start a foreground service.
- How to fix: start from a user-visible app surface or retry when Android permits foreground service launch.
- Related docs: [design/LONG_RUNNING_SERVICE.md](design/LONG_RUNNING_SERVICE.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_FOREGROUND_SERVICE_TYPE_INVALID

- Meaning: the embedded Node foreground service type is missing or does not match the manifest.
- Common cause: stale packaged template, missing `android:foregroundServiceType`, or missing foreground-service permission.
- Example: requesting the `specialUse` foreground-service type from a service manifest that declares no compatible type.
- How to fix: refresh the packaged template or manifest so the embedded Node service declares the expected type and permissions.
- Related docs: [design/LONG_RUNNING_SERVICE.md](design/LONG_RUNNING_SERVICE.md), [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).

## ERR_AUTOJS6_WORK_MANAGER_QUOTA_EXHAUSTED

- Meaning: Android WorkManager/JobScheduler quota is exhausted for a scheduled Node task path.
- Common cause: Android 16+ counts long-running WorkManager foreground workers against JobScheduler quota.
- Example: repeated long-running WorkManager jobs consume the quota window before the next Node task can run.
- How to fix: keep scheduled Node tasks bounded, defer work, or use the explicit user-visible foreground runtime for user-initiated long work.
- Related docs: [design/WORK_MANAGER.md](design/WORK_MANAGER.md), [design/LONG_RUNNING_SERVICE.md](design/LONG_RUNNING_SERVICE.md).

## ERR_AUTOJS6_PROCESS_API_DISABLED

- Meaning: a dangerous `process` API is disabled.
- Common cause: calling `process.chdir`, `process.binding`, `process.dlopen`, `_linkedBinding`, `getBuiltinModule`, or `exit`.
- Example: `process.dlopen({}, "native.node")`.
- How to fix: remove the dependency on dangerous process internals.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED

- Meaning: CommonJS attempted to require an ESM module while the partial ESM boundary was disabled or unavailable.
- Common cause: an explicit ESM-off diagnostic request resolves `require()` to `.mjs` or a package target selected as ESM.
- Example: `require("./module.mjs")`.
- How to fix: use the default v1.1 partial ESM profile for synchronous ESM graphs, or provide a CommonJS package branch for older/ESM-off profiles.
- Related docs: [design/ESM.md](design/ESM.md), [EXPERIMENTAL_PROMOTION_POLICY.md](EXPERIMENTAL_PROMOTION_POLICY.md).

## ERR_REQUIRE_ASYNC_MODULE

- Meaning: CommonJS attempted to synchronously require a partial ESM graph that needs asynchronous evaluation.
- Common cause: an ESM file reachable from `require()` contains `await` or dynamic `import()`.
- Example: `require("./module-with-top-level-await.mjs")`.
- How to fix: use an ESM entry or dynamic `import()` for asynchronous ESM graphs, or remove asynchronous ESM syntax from the required module.
- Related docs: [design/ESM.md](design/ESM.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION

- Meaning: A TypeScript source file uses an extension outside AutoJs6 lightweight type stripping.
- Common cause: running or packaging `.tsx` directly.
- Example: declaring `main.tsx` in `project.json`.
- How to fix: compile TSX to JavaScript on the desktop/build stage, or use `.ts`, `.mts`, or `.cts` with erasable type syntax only.
- Related docs: [PROJECT_WIZARD.md](PROJECT_WIZARD.md), [EXAMPLES.md](EXAMPLES.md), [TESTING.md](TESTING.md).

## ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX

- Meaning: AutoJs6 detected TypeScript syntax that requires code generation instead of simple type stripping.
- Common cause: `enum`, `namespace`, decorators, constructor parameter properties, or import aliases.
- Example: `enum Color { Red }`.
- How to fix: replace with erasable JavaScript-compatible patterns, such as a frozen object for enum-like values, or compile the project before packaging.
- Related docs: [PROJECT_WIZARD.md](PROJECT_WIZARD.md), [EXAMPLES.md](EXAMPLES.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_SCRIPT_TIMEOUT

- Meaning: canonical script timeout code.
- Common cause: the script did not finish before the runtime timeout.
- Example: an uncleared interval keeps the event loop alive.
- How to fix: clear timers, close handles, or configure a timeout appropriate for the workload.
- Related docs: [TESTING.md](TESTING.md), [PERFORMANCE.md](PERFORMANCE.md).

## ERR_AUTOJS6_WORKER_BRIDGE_DENIED

- Meaning: a worker attempted to access AutoJs bridge capabilities.
- Common cause: experimental `worker_threads` code imports bridge modules from a worker.
- Example: a worker calls `require("toast")`.
- How to fix: keep workers pure-JS and route AutoJs bridge calls through the main script.
- Related docs: [design/WORKER_THREADS.md](design/WORKER_THREADS.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_WORKER_DISABLED

- Meaning: `worker_threads` is disabled in the current profile.
- Common cause: normal builds keep worker support behind an experimental flag.
- Example: `require("worker_threads")` in a stable build.
- How to fix: avoid workers in stable scripts or use an experimental validation build.
- Related docs: [design/WORKER_THREADS.md](design/WORKER_THREADS.md), [EXPERIMENTAL_PROMOTION_POLICY.md](EXPERIMENTAL_PROMOTION_POLICY.md).

## ERR_AUTOJS6_WORKER_LIMIT_EXCEEDED

- Meaning: the worker count or resource limit was exceeded.
- Common cause: too many active experimental workers.
- Example: creating more workers than the profile permits.
- How to fix: terminate existing workers and keep worker concurrency below the limit.
- Related docs: [design/WORKER_THREADS.md](design/WORKER_THREADS.md), [PERFORMANCE.md](PERFORMANCE.md).

## ERR_AUTOJS6_WORKER_POOL_CLOSED

- Meaning: an experimental `WorkerPool` rejected work because the pool is closed or closing.
- Common cause: submitting a task after `close()` or service shutdown started.
- Example: calling `pool.run()` after the pool has rejected pending tasks during close.
- How to fix: stop submitting work after close begins and create a new pool only in an explicitly enabled worker profile.
- Related docs: [design/WORKER_THREADS.md](design/WORKER_THREADS.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_WORKER_POOL_QUEUE_FULL

- Meaning: an experimental `WorkerPool` task queue reached its configured limit.
- Common cause: submitting tasks faster than workers can complete them.
- Example: enqueueing another task when the pool queue limit is already full.
- How to fix: throttle submissions, await previous tasks, or lower concurrency in stable scripts by avoiding worker pools.
- Related docs: [design/WORKER_THREADS.md](design/WORKER_THREADS.md), [PERFORMANCE.md](PERFORMANCE.md).

## ERR_AUTOJS6_WORKER_POOL_TASK_CANCELLED

- Meaning: an experimental `WorkerPool` task was cancelled before completion.
- Common cause: caller cancellation, pool close, worker termination, or timeout cleanup.
- Example: cancelling a pending task with an `AbortSignal`.
- How to fix: handle cancellation as a normal control path and clean task-local resources.
- Related docs: [design/WORKER_THREADS.md](design/WORKER_THREADS.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_WORKER_POOL_TASK_FAILED

- Meaning: an experimental `WorkerPool` task failed while executing or while receiving its result.
- Common cause: worker exception, rejected task handler, worker exit, or unexpected worker error.
- Example: a pool task throws before posting its result.
- How to fix: catch task errors, keep worker code pure JavaScript, and inspect worker diagnostics.
- Related docs: [design/WORKER_THREADS.md](design/WORKER_THREADS.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_WORKER_POOL_TASK_TIMEOUT

- Meaning: an experimental `WorkerPool` task exceeded its timeout.
- Common cause: CPU-bound work, a hung promise, or a worker that does not respond before the task deadline.
- Example: a pool task loops indefinitely and never posts a result.
- How to fix: keep worker tasks bounded, add cooperative cancellation, and avoid workers in stable scripts.
- Related docs: [design/WORKER_THREADS.md](design/WORKER_THREADS.md), [PERFORMANCE.md](PERFORMANCE.md).

## ERR_AUTOJS6_WORKER_TERMINATED

- Meaning: a worker terminated before completing the requested operation.
- Common cause: explicit `terminate()`, parent service stop, uncaught worker error, or lifecycle cleanup.
- Example: awaiting a message from a worker after it has been terminated.
- How to fix: handle termination as a cancellation and clean parent-side state.
- Related docs: [design/WORKER_THREADS.md](design/WORKER_THREADS.md), [TESTING.md](TESTING.md).

## ERR_AUTOJS6_WORKER_TIMEOUT

- Meaning: a worker operation exceeded its timeout.
- Common cause: a worker did not initialize, respond, or terminate inside the configured budget.
- Example: a CPU-bound worker never posts its result.
- How to fix: bound worker work, add cooperative cancellation, or avoid workers in stable scripts.
- Related docs: [design/WORKER_THREADS.md](design/WORKER_THREADS.md), [PERFORMANCE.md](PERFORMANCE.md).
