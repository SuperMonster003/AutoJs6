# P13-14 rhino.install Java Proxy

- Status: ready
- Gate: `:app:verifyNodePhase13RhinoInstallJavaProxy`
- Runtime surface:
  - `require("rhino").install({ experimental: true, globals: true })`
  - `Packages` Java proxy
  - `java` Java proxy
  - `android` Java proxy

## Implemented

P13-14 keeps the Safe Node Profile default unchanged: `rhino.install()` without
an explicit experimental option still throws
`ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`, `node_modules/rhino` cannot shadow the
controlled module, and no Rhino globals are installed by default.

When both conditions are true, `rhino.install()` can install a Pro-style Java
proxy subset:

- The script calls `rhino.install({ experimental: true, globals: true })` or
  passes an explicit `target` object.
- The build/run enables the existing Java interop experiment.

The installed `Packages`, `java`, and `android` values are JavaScript proxies.
They do not expose raw Rhino `Scriptable`, Java `Class`, class loaders, Android
objects, or reflection. Static method calls and constructors route through the
same P13-13 `$autojs.java` / `require("java")` allowlist bridge.

Supported examples in the experiment:

```js
const rhino = require("rhino");
rhino.install({ experimental: true, globals: true });

const max = await Packages.java.lang.Math.max(1, 2);
const uuid = await java.util.UUID.fromString("00000000-0000-0000-0000-000000000004");
const rect = await new android.graphics.Rect(1, 2, 6, 8);
const width = await rect.call("width");
await rect.release();
```

## Boundaries

This is not full Auto.js Pro Rhino interop. `importClass`, `importPackage`, and
`JavaAdapter` remain absent. The proxy surface is intentionally async for
bridge-backed calls and only reaches classes/methods accepted by the Java
interop allowlist. `Packages.java.lang.Runtime.exec`, classloader access,
reflection, native library loading, process authority, raw `Context`, raw
`Activity`, raw `View`, Binder, filesystem, and raw network authority remain
denied.

The P13-16 v1.1 replacement decision is satisfied for this gate:
`rhino.JavaAdapter()` is a stable denial, global `JavaAdapter` remains absent,
and `java.defineClass()` / `$autojs.java.defineClass()` are reserved
denial-only future provider names. The actual proxy-interface provider remains
future P13-16 provider work, not a prerequisite for the P13-14 Java proxy POC.

P13-14 v1.1 packaged policy remains fail-closed: packaged APK metadata cannot
enable `rhino.install()` or Java proxy globals, and no manifest authority is
added by this gate. Real Pro profile selector is delegated to the aggregate
Java/Rhino surface gate; this POC is available only through explicit
experimental test or debug configuration and does not count the selector as a
separate P13-14 blocker.

## Evidence

- Runtime JS facade: `app/src/main/cpp/node_bridge_sources.cpp`
- Provider registry metadata:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt`
- Focused test:
  `NodeRhinoShimDesignGateInstrumentationTest#rhinoShimFacadeIsDisabledByDefault`
- Focused test:
  `NodeRhinoShimDesignGateInstrumentationTest#rhinoInstallExperimentalJavaProxyUsesAllowlistWhenJavaInteropEnabled`
- Type declarations: `docs/nodejs/types/autojs6-node/rhino.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Host verifier: `tools/nodejs/phase13/verify-rhino-install-java-proxy.js`
- Gradle gate: `:app:verifyNodePhase13RhinoInstallJavaProxy`
- Report artifacts:
  - `build/reports/nodejs/phase13-rhino-install-java-proxy.json`
  - `build/reports/nodejs/phase13-rhino-install-java-proxy.md`

## Closure

P13-14 closes the v1.1 Java proxy POC scope because default `rhino.install()`
stays fail-closed, explicit experimental installs route only through the
P13-13 Java allowlist, packaged metadata remains denied, and Pro profile
promotion remains owned by the aggregate Java/Rhino surface gate.

Future packaged APK metadata, rollback, and security evidence remain required
before any promoted Pro profile can enable this surface.

## Validation

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13RhinoInstallJavaProxy
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRhinoShimDesignGateInstrumentationTest"
```
