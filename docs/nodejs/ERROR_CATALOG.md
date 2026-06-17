# AutoJs6 Node.js Error Catalog

This catalog documents the current user-visible `ERR_AUTOJS6_*` error codes in the AutoJs6 Safe Node Profile.
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

## ERR_AUTOJS6_BRIDGE_PROCESS_DEAD

- Meaning: The bridge process or service was destroyed before a pending request completed.
- Common cause: user stop, service shutdown, or native process termination.
- Example: a pending dialog or image request rejects after the Node service is stopped.
- How to fix: handle rejection, clean resources in `finally`, and avoid assuming long bridge calls survive service stop.
- Related docs: [TESTING.md](TESTING.md), [docs/nodejs/design/LONG_RUNNING_SERVICE.md](design/LONG_RUNNING_SERVICE.md).

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

## ERR_AUTOJS6_BUILTIN_DISABLED

- Meaning: A Node builtin is disabled by the Safe Node Profile.
- Common cause: requiring `child_process`, `vm`, `inspector`, `dgram`, or another denied builtin.
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
- Example: `import("./x.mjs")` in the stable profile.
- How to fix: use CommonJS or an experimental build with the dynamic-import gate enabled.
- Related docs: [design/ESM.md](design/ESM.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED

- Meaning: ESM execution is not enabled for this path, or the requested ESM syntax is outside the partial profile.
- Common cause: ESM was explicitly disabled for a diagnostic run, static `import`/`export` syntax was used in a CommonJS entry, or an unsupported ESM form such as `import.meta` was used.
- Example: `import.meta.url` in a v1.1 partial ESM module.
- How to fix: use supported partial ESM syntax, move static imports into a `.mjs` or `type=module` entry, or keep CommonJS syntax in `.cjs`/CommonJS files.
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
- Example: using scoped glob options that follow symlinks outside the working directory.
- How to fix: remove the unsupported option and stay within the documented profile.
- Related docs: [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

## ERR_AUTOJS6_FS_ABSOLUTE_PATH_DENIED

- Meaning: scoped fs denied an absolute path.
- Common cause: a script passed `/sdcard/...`, `/proc/...`, `/sys/...`, `/dev/...`, or another absolute path to `fs`.
- Example: `fs.readFileSync("/proc/version")`.
- How to fix: use a relative path inside the working directory.
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
- Common cause: too many preloaded modules, a single module over `262144` UTF-8 bytes, or total moduleSources over `524288` UTF-8 bytes.
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
- Common cause: normal builds keep live network providers disabled; raw network modules also reject before I/O.
- Example: `await fetch("https://example.com")` in a normal build.
- How to fix: use fake providers in tests or an explicitly approved experimental network build.
- Related docs: [design/NETWORK.md](design/NETWORK.md), [SECURITY_MODEL.md](SECURITY_MODEL.md).

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

## ERR_AUTOJS6_PROCESS_API_DISABLED

- Meaning: a dangerous `process` API is disabled.
- Common cause: calling `process.chdir`, `process.binding`, `process.dlopen`, `_linkedBinding`, `getBuiltinModule`, or `exit`.
- Example: `process.dlopen({}, "native.node")`.
- How to fix: remove the dependency on dangerous process internals.
- Related docs: [SECURITY_MODEL.md](SECURITY_MODEL.md), [COMPATIBILITY_PROFILE.md](COMPATIBILITY_PROFILE.md).

## ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED

- Meaning: CommonJS attempted to require an ESM module in a profile that does not support that boundary.
- Common cause: `require()` resolves to `.mjs` or a package target selected as ESM.
- Example: `require("./module.mjs")`.
- How to fix: import the CommonJS dependency from an ESM entry, or provide a CommonJS package branch for `require`.
- Related docs: [design/ESM.md](design/ESM.md), [EXPERIMENTAL_PROMOTION_POLICY.md](EXPERIMENTAL_PROMOTION_POLICY.md).

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
