# P13-13 Java Interop Allowlist

- Status: partial
- Gate: `:app:verifyNodePhase13JavaInteropAllowlist`
- Runtime surface:
  - `require("java")`
  - `$autojs.java`
  - opaque `android.graphics.Rect` Java handles

## Implemented

P13-13 extends the existing build-time Java interop experiment without
promoting it into the Safe Node Profile. Default builds still reject
`require("java")` and `$autojs.java` calls with
`ERR_AUTOJS6_JAVA_INTEROP_DISABLED`.

When `autojs.nodejs.javaInterop.experimental=true` is enabled, the allowlist now
supports:

- `findClass` / `type` class descriptors for allowlisted classes.
- Static methods for the existing `java.lang.Math` and `java.util.UUID`
  allowlist.
- `android.graphics.Rect` construction as a provider-owned opaque handle.
- Rect instance calls through `java.call`, `java.callInstance`, handle
  `.call()`, handle `.callInstance()`, and `$autojs.java.call()`.
- Rect field reads through `java.getField`, handle `.getField()`, and
  `$autojs.java.getField()`.
- Explicit handle cleanup through `java.release`, `java.dispose`, handle
  `.release()`, handle `.dispose()`, and `$autojs.java.release()`.

The Android provider owns the Java object table and releases handles through the
existing `NodeBridgeResourceRegistry`, so script stop, service destroy, and
explicit release all converge on execution-scoped cleanup.

## Allowlist

| Class | Constructors | Static methods | Instance methods | Fields |
| --- | --- | --- | --- | --- |
| `java.lang.Math` | none | `abs`, `ceil`, `floor`, `max`, `min`, `pow`, `round`, `sqrt` | none | none |
| `java.util.UUID` | none | `fromString`, `randomUUID` | none | none |
| `android.graphics.Rect` | `new(left, top, right, bottom)` | none | `width`, `height`, `centerX`, `centerY`, `exactCenterX`, `exactCenterY`, `isEmpty`, `contains` | `left`, `top`, `right`, `bottom` |

## Denied Boundaries

classloader, reflection, native library loading, and process authority remain
denied. The experiment still blocks `java.lang.Class`, `ClassLoader`, `Runtime`,
`ProcessBuilder`, `java.io.*`, `java.nio.file.*`, `java.net.*`,
`java.lang.reflect.*`, `kotlin.reflect.*`, raw Android `Context` / `Activity` /
`Service` / `View`, Binder, media projection, accessibility service objects,
and raw image/native pointers.

This task does not implement dynamic dex/jar loading, unrestricted package
imports, generic method invocation, field mutation, Java callback wrapping, or
Rhino-style `JavaAdapter`.

## Remaining

- Thread dispatch and `$autojs.java.setThreadMode` /
  `$autojs.java.setDefaultThreadMode` semantics remain stable rejections.
- A broader method budget model beyond the explicit allowlist plus bridge
  timeout still needs a design gate.
- Pro profile opt-in promotion remains blocked until the Java/Rhino P13 surface
  has complete migration, packaged, rollback, and security evidence.

## Evidence

- Runtime JS facade: `app/src/main/cpp/node_bridge_sources.cpp`
- Android provider: `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
- Focused test:
  `NodeJavaInteropBridgeInstrumentationTest#kotlinJavaInteropProviderIsDisabledByDefaultAndAllowlistedWhenEnabled`
- Focused test:
  `NodeJavaInteropBridgeInstrumentationTest#jsJavaInteropExperimentalModuleUsesBridgeAndEnforcesAllowlist`
- Type declarations: `docs/nodejs/types/autojs6-node/autojs_global.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Host verifier: `tools/nodejs/phase13/verify-java-interop-allowlist.js`
- Gradle gate: `:app:verifyNodePhase13JavaInteropAllowlist`
- Report artifacts:
  - `build/reports/nodejs/phase13-java-interop-allowlist.json`
  - `build/reports/nodejs/phase13-java-interop-allowlist.md`

## Validation

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13JavaInteropAllowlist
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeJavaInteropBridgeInstrumentationTest"
```
