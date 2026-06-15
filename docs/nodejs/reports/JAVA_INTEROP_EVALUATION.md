# Java Interop Evaluation

Date: 2026-06-16

Device: Sony XQ-AT72 (`QV710AF65F`), Android 12

Branch/package identity: `feature/nodejs-runtime`, `org.autojs.autojs6.nodejs`

## Recommendation

Keep Java interop disabled for Node Profile v1 GA.

The allowlist prototype can remain as a debug/security-review experiment, but it must not be exposed as a release opt-in
without explicit security signoff. The current prototype is intentionally narrow and still creates a critical privilege
surface: any future allowlist entry can become a bridge around scoped filesystem, Android lifecycle, permissions,
packaged verification, or Rhino/Node isolation.

Release builds should continue to keep:

```properties
autojs.nodejs.javaInterop.experimental=false
```

Project metadata and packaged APK metadata must not enable Java interop. Users should use explicit AutoJs bridge modules
instead of raw Java or Android object access.

## Commands Run

Compile gate after adding the Task 124 dangerous-class assertions:

```powershell
.\gradlew.bat --console=plain :app:compileAppDebugAndroidTestKotlin
```

Result: passed.

Default/request-local focused Java interop suite:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeJavaInteropBridgeInstrumentationTest,org.autojs.autojs.engine.NodeBridgeResourceRegistryInstrumentationTest#androidProviderCleansJavaHandlesOnDestroyAndDoesNotLeakToNextExecution"
```

Result: passed, 4/4 tests.

Build-time experimental flag focused run:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.javaInterop.experimental=true" "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeJavaInteropBridgeInstrumentationTest#jsJavaInteropExperimentalModuleUsesBridgeAndEnforcesAllowlist"
```

Result: passed, 1/1 tests.

## Supported Experimental Cases

The prototype supports only these allowlisted cases under explicit experimental enablement:

- `require("java")`
- `java.type(className)`
- `java.callStatic(className, method, args)`
- `java.new(className, args)`
- selected `java.lang.Math` static helpers
- selected `java.util.UUID` static helpers
- `android.graphics.Rect` construction as an opaque provider-owned handle
- fake bridge transport serialization with `java_interop` permission tagging

This is not a stable API contract. It is a controlled experiment.

## Default Denial

The default gate passed:

- `BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED=false`
- `require("java")` rejects with `ERR_AUTOJS6_JAVA_INTEROP_DISABLED`
- `require.resolve("java")` rejects with `ERR_AUTOJS6_JAVA_INTEROP_DISABLED`
- local `node_modules/java` cannot shadow the denial
- `require("autojs6:profile")` reports `java_interop` disabled and experimental

## Dangerous-Class Denial

The focused tests now cover the Task 124 dangerous-class list:

- `java.lang.Runtime` denied with `ERR_AUTOJS6_JAVA_CLASS_DENIED`
- `java.lang.ProcessBuilder` denied with `ERR_AUTOJS6_JAVA_CLASS_DENIED`
- `java.lang.ClassLoader` denied with `ERR_AUTOJS6_JAVA_CLASS_DENIED`
- `java.lang.reflect.Method` denied with `ERR_AUTOJS6_JAVA_REFLECTION_DENIED`
- `java.lang.System.loadLibrary(...)` denied with `ERR_AUTOJS6_JAVA_CLASS_DENIED`

The JavaScript wrapper rejects these before dispatching to the bridge. The provider also rejects them when called
directly in Kotlin tests.

## Object Handle Cleanup

Opaque Java handles are provider-owned and execution-scoped.

The evaluation covered:

- `android.graphics.Rect` returns an opaque `java-*` handle
- `embedded_script.java_interop_handle_count` increments while the handle is active
- provider `destroy()` drops the handle count to `0`
- the shared bridge resource registry reports Java object handles as closed during destroy
- a later provider starts with no leaked Java handles

This is the minimum cleanup shape required for any future experiment. It is not enough to justify release exposure,
because additional allowlisted classes can introduce Android lifecycle, Binder, file, image, accessibility, or UI object
leaks.

## Release Decision

Node Profile v1 GA should document Java interop as:

- disabled by default
- release-disabled, not project/package opt-in
- debug/security-review experiment only
- allowlist-only when explicitly tested
- not a Rhino compatibility shim
- not raw `java.*`, `android.*`, `Packages`, or `importClass`
- blocked from promotion without security signoff

The next promotion gate must add a security review for every allowlisted class/method, packaged fail-closed diagnostics,
negative tests for raw Android object access and filesystem/network/process escape, service-destroy stress with pending
Java calls, and a release-policy decision for whether any Java interop should be user-visible at all.
