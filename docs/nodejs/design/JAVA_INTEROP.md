# Java And Android Interop

Status: stable allowlisted capability, enabled by default in Node Profile v1.2.

## Runtime Contract

Java interop has no Gradle opt-in, release block, project override, or packaged
override.

- BuildConfig: `NODEJS_JAVA_INTEROP_ENABLED=true`
- host/plugin request contract v2: `javaInteropEnabled=true`
- profile snapshot: `featureStates.javaInterop=true`
- capability status: `stable`

`require("java")` and `$autojs.java` expose the same provider-owned,
allowlisted facade. Explicit Rhino migration proxies use
`rhino.install({ explicit: true, target })` and route through that facade.

## Supported Boundary

The initial stable allowlist supports the reviewed surfaces for:

- `java.lang.Math` static methods/constants;
- `java.util.UUID` construction, parsing, generation, string conversion, and
  reviewed value access;
- `android.graphics.Rect` construction, selected instance methods, selected
  field reads, and explicit release/dispose.

Arguments and results cross the bridge as JSON-safe values or provider-owned
opaque handles. Handles belong to one execution id and are released explicitly
or during execution/provider cleanup.

The allowlist is exact. Package prefixes and class names that merely resemble an
allowed class do not inherit authority.

## Stable Denials

The capability does not expose unrestricted JVM or Android access. It rejects:

- `java.lang.Runtime`, `ProcessBuilder`, and Java process execution;
- `System.load`, `System.loadLibrary`, JNI loading, and native pointers;
- `ClassLoader`, dynamic class definition, bytecode loading, and arbitrary
  class discovery;
- reflection, method/field accessibility overrides, and Kotlin reflection;
- raw `Context`, `Activity`, `Service`, Binder, Intent, View, Window,
  accessibility, media-projection, bitmap, or other Android service objects;
- unrestricted package walking, Rhino `importClass`/`importPackage`, and
  `JavaAdapter`.

`java.defineClass()` and `$autojs.java.defineClass()` remain reserved and
denied until a separate callback/lifecycle design is implemented. This stable
denial is not a feature switch.

## Threading And Lifecycle

Provider operations validate the execution id, class/member allowlist, argument
shape, handle ownership, and result shape before dispatch. Android-state work
must use the appropriate provider thread rather than blocking V8/libuv.
Execution stop, timeout, cancellation, provider destroy, and native crash
recovery release all owned handles idempotently and report leaks through Node
resource diagnostics.

## Packaging And Security

Stable Java interop does not grant additional Android permissions and does not
bypass bridge permission checks for other capabilities. Packaged projects
cannot expand the class/member allowlist through metadata. Release and AAR
generation include the stable facade and denylist unchanged.

## Verification

The Java interop gate covers allowed static/instance/field operations, value
serialization, opaque-handle ownership and cleanup, shadow protection, explicit
Rhino proxy installation, and denial of runtime/process, reflection,
class-loader, native-library, raw Android object, and cross-execution handle
access.
