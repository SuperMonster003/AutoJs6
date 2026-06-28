# Java Interop Design Gate

Status: experimental allowlist prototype. Default builds keep Java interop disabled. Embedded Node scripts cannot access unrestricted Java, unrestricted Android classes, Rhino Java adapters, class loaders, reflection, or raw Android objects.

## Goal

Java and Android interop for Node scripts must not become a second privileged runtime surface beside the AutoJs bridge.

Any future interop must preserve the Safe Node Profile:

- default builds expose no unrestricted Java interop
- Android and AutoJs capabilities remain owned by bridge providers
- Node scripts do not receive raw `Context`, `Activity`, `Service`, `AccessibilityService`, `MediaProjection`, Binder, Java object, class-loader, or native pointer access
- values crossing the boundary are JSON snapshots or explicitly owned opaque handles
- calls that can touch Android state are asynchronous from JavaScript and do not block the Node/V8/libuv thread
- Rhino compatibility remains explicit migration work, not an accidental Node capability

## Current Decision

`java_interop` is registered as a critical experimental feature flag:

```text
id: java_interop
diagnosticName: javaInterop
gradleProperty: autojs.nodejs.javaInterop.experimental
defaultEnabled: false
buildTimeOverrideAllowed: true
projectJsonOverrideAllowed: false
packagedApkOverrideAllowed: false
status: experimental
```

With the flag off, the runtime keeps `require("java")`, `$autojs.java`,
`Packages`, `importClass`, direct `java.*`, direct `android.*`, reflection, and
Rhino Java adapter style APIs unavailable. With the flag on, `require("java")`
and the gated `$autojs.java` facade are restricted to a bridge-backed allowlist.
P13-14 can additionally expose `Packages`, `java`, and `android` JavaScript
proxies only through explicit `rhino.install({ experimental: true })`; those
proxies still route through the same allowlist. P13-15 keeps Rhino
`importClass` / `importPackage` semantics unsupported and returns the stable
`ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED` error from explicit `require("rhino")`
helpers.
P13-16 keeps Rhino `JavaAdapter` unsupported in Node with
`ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED` and reserves
`java.defineClass()` / `$autojs.java.defineClass()` as the future
proxy-interface migration name. The reserved methods currently throw
`ERR_AUTOJS6_JAVA_METHOD_DENIED` and do not dispatch to the bridge.

## Mode Comparison

| Mode | Description | Decision |
| --- | --- | --- |
| A. No direct Java interop | Node scripts use explicit AutoJs bridge modules only. | Current default and recommended release behavior. |
| B. Whitelisted Java classes only | A small allowlist exposes selected value or pure helper classes through a controlled wrapper. | Possible future experiment after tests and provider ownership are defined. |
| C. Developer-mode unrestricted Java interop | Scripts can access arbitrary Java/Android classes. | Not acceptable for normal builds or packaged APKs. Developer-mode only if ever added. |
| D. Rhino compatibility shim through separate process | Rhino-style Java access is routed to a separate compatibility process with explicit IPC. | Possible migration aid, but not the Node default and not a shortcut around bridge policy. |

## Recommendation

Use Mode A as the product default:

- direct Java interop remains disabled by default
- AutoJs bridge modules are the only path to Android and AutoJs capabilities
- high-risk APIs continue to be represented as denied or absent capabilities in `require("autojs6:profile")`

The current prototype starts with Mode B for a tiny build-time experiment:

- opt-in experimental flag only
- allowlist classes and methods explicitly
- no package wildcard authority
- no raw object graph traversal
- no reflection or class-loader access
- JSON values or short-lived opaque handles only
- provider-owned cleanup on script stop, timeout, service destroy, and native crash recovery

Modes C and D require separate design gates before implementation.

## Risk Model

Direct Java interop can bypass several Safe Node boundaries.

### Filesystem Escape

Dangerous paths:

- `java.io.File`
- `java.nio.file.*`
- Android storage APIs
- content resolver access through raw `Context`

These can bypass scoped `fs`, canonical path checks, package source budgets, and packaged APK verification. Future interop must not expose filesystem APIs unless each operation reuses the existing scoped filesystem policy.

### Android Permission Bypass

Raw access to `Context`, `Activity`, `Service`, `PackageManager`, `ContentResolver`, `AccessibilityService`, `MediaProjection`, `NotificationManager`, or Binder objects can use the app permissions without bridge prompts, diagnostics, timeouts, or lifecycle cleanup.

Android operations must stay behind named bridge providers with explicit request and response payloads.

### Reflection

Dangerous APIs:

- `java.lang.Class`
- `java.lang.reflect.*`
- `kotlin.reflect.*`
- `MethodHandles`
- `sun.misc.Unsafe` where present

Reflection can discover denied APIs, call hidden members, reach class loaders, and bypass method allowlists. It must remain denied.

### Class Loader Access

Dangerous APIs:

- `java.lang.ClassLoader`
- `DexClassLoader`
- `PathClassLoader`
- `InMemoryDexClassLoader`
- resource and asset loading through arbitrary loaders

Class-loader access can load unreviewed bytecode, inspect app internals, or reach implementation classes that were never intended as script APIs.

### Native Library Loading

Dangerous APIs:

- `System.load`
- `System.loadLibrary`
- `Runtime.load`
- `Runtime.loadLibrary`
- `process.dlopen`
- native addons

Native library loading bypasses scoped filesystem, ABI packaging policy, and JavaScript-level capability restrictions. It remains denied.

### Process and Shell Execution

Dangerous APIs:

- `java.lang.Runtime.exec`
- `ProcessBuilder`
- raw `Process` objects

Node already keeps `child_process` disabled. Shell execution, where allowed, must use the controlled `shell` bridge with explicit root, timeout, output budget, and diagnostics.

### UI Thread Misuse

Direct Java interop can call UI APIs from the Node event-loop thread or hold stale `Activity` and `View` references after lifecycle changes.

Future UI-related interop must use bridge providers that dispatch to the main thread, reject on service destroy, and avoid raw UI object exposure.

### Object Lifetime and Leaks

Long-lived Java references can keep activities, services, images, accessibility nodes, or Binder objects alive after script completion.

Future opaque handles must be:

- scoped to one execution id
- reference-counted or explicitly released by the provider
- rejected after stop or timeout
- dumped in diagnostics when leaked
- destroyed with the provider on service destroy

## Future Allowlist Candidate

The first allowlist, if implemented, should prefer pure value helpers and immutable data shapes.

Possible candidates:

- `java.lang.Math` static numeric helpers
- selected `java.util.UUID` operations such as `randomUUID()` and `fromString()`
- selected immutable collection conversion helpers, if they only produce JSON-compatible data
- `android.graphics.Rect` as a value snapshot with numeric fields

Current experimental MVP:

- `java.lang.Math` selected static numeric helpers
- `java.util.UUID` selected static UUID helpers
- `android.graphics.Rect` construction as an opaque provider-owned handle
- P13-13 Rect handle calls: selected instance methods, field reads, and
  explicit `release` / `dispose` cleanup
- P13-16 `defineClass` is a reserved stable denial only; it does not load
  bytecode, define classes, or register callbacks yet

The phrase `java.util.*` must not mean package-wide authority. It can only mean selected classes and selected methods documented in an allowlist table.

Default denials:

- `java.lang.Class`
- `java.lang.ClassLoader`
- `java.lang.Runtime`
- `java.lang.Process`
- `java.lang.ProcessBuilder`
- `java.lang.System` methods that expose environment, properties, library loading, exit, or security-manager behavior
- `java.io.*`
- `java.nio.file.*`
- `java.net.*`
- `java.lang.reflect.*`
- `kotlin.reflect.*`
- Android `Context`, `Activity`, `Service`, `View`, `Intent`, Binder, `PackageManager`, `ContentResolver`, `AccessibilityService`, `AccessibilityNodeInfo`, `MediaProjection`, and raw image classes

## Current Experimental API Shape

When `autojs.nodejs.javaInterop.experimental=true` is enabled for an experimental run, the module name is:

```js
const java = require("java");

const uuid = await java.callStatic("java.util.UUID", "randomUUID", []);
console.log(String(uuid));

const rect = await java.new("android.graphics.Rect", [1, 2, 3, 4]);
console.log(await rect.call("width"));
console.log(await java.getField(rect, "left"));
await rect.release();
```

Current constraints:

- calls return Promises unless they are proven pure and fast
- all class names and method names are checked against a static allowlist
- arguments are JSON primitives or explicit value descriptors
- results are JSON primitives, value descriptors, or short-lived opaque handles
- no constructor or method is callable unless listed
- no object can reveal its runtime class or class loader
- no generic method invocation, reflection bridge, or property traversal exists
- P13-13 handle helpers are limited to provider-owned Rect handles and do not
  introduce generic Java reflection or arbitrary field traversal
- P13-16 reserves `java.defineClass()` / `$autojs.java.defineClass()` for a
  future bounded proxy-interface provider; current calls throw
  `ERR_AUTOJS6_JAVA_METHOD_DENIED` without bridge dispatch
- P13-13 v1.1 stable-denies thread dispatch and
  `$autojs.java.setThreadMode()` / `$autojs.java.setDefaultThreadMode()` as
  runtime user APIs; future thread-mode work must be owned by a Java/Rhino
  provider promotion gate with lifecycle and main-thread evidence

P13-13 Java method budget policy is design-locked for any promoted or expanded
allowlist entry:

- every callable constructor, static method, instance method, and field read
  must have an explicit class/method allowlist row and a per-call timeout
- every execution gets a bounded Java-call budget; budget exhaustion must fail
  closed with a stable bridge/Java error before another Java call is dispatched
- provider-owned handles count against an execution-scoped handle budget and
  must be released on explicit release, script stop, and service destroy
- arguments and return values stay JSON primitives, frozen value descriptors, or
  short-lived opaque handles with bounded serialized byte size
- no allowlist entry may return raw Android lifecycle objects, Binder handles,
  threads, file descriptors, streams, class loaders, reflection objects, native
  pointers, or mutable object graphs
- every new allowlist entry requires positive tests, denial tests for adjacent
  unsafe methods, cleanup evidence, and budget/timeout coverage before profile
  promotion

An even safer first step is to avoid generic Java calls and expose named bridge modules instead, such as `rect`, `uuid`, or additional `image` value helpers.

## Rhino Compatibility Shim

Rhino-era scripts may use:

- `importClass`
- `importPackage`
- `Packages`
- `java.*`
- `android.*`
- `$autojs.java`
- `JavaAdapter`
- `java.defineClass()` / `$autojs.java.defineClass()` until the P13-16
  replacement provider is implemented

Node must not silently emulate these in-process. If a compatibility shim is ever added, it should:

- run outside the embedded Node process
- require explicit project migration metadata
- expose only serialized request and response values
- keep Rhino and Node lifecycles separate
- never pass Java object references into Node
- record compatibility diagnostics so users can migrate to bridge modules

Detailed Rhino shim policy is tracked in `docs/nodejs/design/RHINO_SHIM.md`.

## Packaged APK Policy

Packaged APKs must not gain Java interop by bundling the Node runtime.

If a future allowlist experiment is enabled:

- packaged overrides remain denied until a packaging design explicitly allows them
- diagnostics must list the enabled interop mode, class allowlist, and denied dangerous APIs
- no extra Android permission is implied by Java interop alone
- any feature that needs permissions must declare them through the owning bridge module
- encrypted scripts must not be able to request an expanded allowlist at runtime

## Error Policy

Future denials should use stable AutoJs6 errors:

- `ERR_AUTOJS6_JAVA_INTEROP_DISABLED`
- `ERR_AUTOJS6_JAVA_CLASS_DENIED`
- `ERR_AUTOJS6_JAVA_METHOD_DENIED`
- `ERR_AUTOJS6_JAVA_REFLECTION_DENIED`
- `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`

Error details may include:

```json
{
  "code": "ERR_AUTOJS6_JAVA_CLASS_DENIED",
  "className": "java.lang.Runtime",
  "method": "exec"
}
```

Do not include raw object identities, Binder references, filesystem paths outside scoped policy, or Android permission state beyond the bridge-approved diagnostic payload.

## Test Gate For Future Implementation

Any implementation task must include tests for:

- `java_interop` remains disabled by default in `require("autojs6:profile")`
- `require("java")`, `$autojs.java`, `Packages`, `importClass`, direct `java.*`, and direct `android.*` remain unavailable by default
- allowlisted class and method calls work only when the experiment is explicitly enabled
- denied classes and methods return stable error codes
- reflection, class loaders, native loading, process execution, raw filesystem, raw network, and raw Android context access are denied
- Java calls that touch Android state are Promise-based and dispatched through providers
- service destroy rejects pending calls and releases opaque handles
- packaged APK diagnostics fail closed when Java interop metadata requests unsupported authority

## Acceptance Gate

Java interop may move beyond this design only when:

- the feature flag policy is updated intentionally
- the allowlist is documented in the compatibility profile
- the security model lists all denied Java surfaces
- the bridge/provider ownership model is implemented
- negative tests prove dangerous Java APIs are not reachable
- packaged APK verification remains fail-closed
- Rhino compatibility behavior is documented separately from Node interop
