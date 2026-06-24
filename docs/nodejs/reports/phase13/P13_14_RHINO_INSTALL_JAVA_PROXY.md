# P13-14 rhino.install Java Proxy

- Status: partial
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

Packaged APK metadata remains fail-closed. There is no Pro profile runtime
selector yet; this POC is available only through explicit experimental test or
debug configuration.

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

## Remaining

- Add a real Pro profile runtime selector instead of build/test-only
  experimental wiring.
- Decide P13-15 `importClass` / `importPackage` behavior.
- Decide P13-16 `JavaAdapter` replacement behavior.
- Add packaged APK metadata, rollback, and security evidence before any
  profile promotion.

## Validation

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13RhinoInstallJavaProxy
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRhinoShimDesignGateInstrumentationTest"
```
