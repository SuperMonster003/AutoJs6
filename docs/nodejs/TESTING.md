# AutoJs6 Node.js Testing

Last reviewed: 2026-08-26.

## Prerequisites

- JDK/Android SDK versions required by the host and plugin builds.
- Runtime-plugin repository at a known path for plugin-owned asset and alignment checks.
- For device tests, a current plugin installed, enabled, trusted, authorized, and
  compatible with the device ABI and host contract.

Record the installed plugin version before running device tests. An old or missing
plugin can stop plugin-dependent tests at an Assume precondition while instrumentation
still prints OK.

## Host Verification

The host owns compilation, unit coverage for the Binder/workspace boundary, and
AndroidTest compilation. It no longer validates a local sample, declaration, or
project-wizard mirror.

~~~powershell
.\gradlew.bat :app:testAppDebugUnitTest --offline
.\gradlew.bat :app:compileAppDebugKotlin :app:compileAppDebugAndroidTestKotlin --offline
.\gradlew.bat :app:assembleAppDebug --offline
~~~

## Plugin-Owned Verification

Run these from **AutoJs6-Plugin-NodeJs-Runtime**:

~~~powershell
.\gradlew.bat verifyNodePluginExamples verifyNodeProjectWizard `
  verifyAutoJs6NodeTypeDeclarations typeCheckNodeTypescriptSmoke --offline
.\gradlew.bat verifyNodeHostIntegration `
  "-Pautojs.host.root=D:\idea-projects\AutoJs6" --offline
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline
~~~

The TypeScript task requires `tsc` on PATH. `autojs.host.root` is unnecessary when
the two repositories are siblings with their standard names.

## Real-Plugin Instrumentation

Install the assembled host APK and AndroidTest APK, then run focused classes with raw
output:

~~~powershell
$serial = "DEVICE_SERIAL"
$runner = "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner"

adb -s $serial shell am instrument -w -r -e class org.autojs.autojs.engine.NodeRuntimePluginBridgeInstrumentationTest $runner
adb -s $serial shell am instrument -w -r -e class org.autojs.autojs.engine.NodeR6EngineSwitchInstrumentationTest $runner
adb -s $serial shell am instrument -w -r -e class org.autojs.autojs.engine.NodeHostPluginIntegrationInstrumentationTest $runner
adb -s $serial shell am instrument -w -r -e class org.autojs.autojs.engine.NodeR6RequiredPluginSecurityInstrumentationTest $runner
~~~

These classes cover host source routing, plugin discovery/Binder execution, workspace
transport, a live host capability, TypeScript compiler dispatch, and required-plugin
security. Runtime feature and sample conformance belongs to the plugin repository.
Run provider-specific retained host classes when their Android dispatch code changes.

## APK Ownership Check

Inspect host APK entries after assembly. No path may end in **libnode.so** or
**libautojs6-node.so**. The absence of those libraries plus a successful real-plugin
script proves the runtime stayed outside the host.

## Result Interpretation

- Confirm each class reports its expected test count and credible duration.
- Read raw status lines to distinguish executed tests from Assume skips.
- Treat permission-sensitive device failures according to [Flake policy](FLAKE_POLICY.md).
- Compile-only results do not prove plugin discovery, Binder execution, or host API
  dispatch.
