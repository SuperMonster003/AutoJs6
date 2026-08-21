# AutoJs6 Node.js Testing

Last reviewed: 2026-08-21.

## Prerequisites

- JDK/Android SDK versions required by the host build.
- Runtime-plugin repository at a known path for cross-repository checks.
- For device tests, a current plugin installed, enabled, trusted, authorized, and
  compatible with the device ABI and host contract.

Record the installed plugin version before running tests. An old plugin can make all
plugin-dependent tests stop at an Assume precondition while instrumentation still
prints OK.

## Offline Verification

~~~powershell
$pluginRoot = "D:\idea-projects\AutoJs6-Plugin-NodeJs-Runtime"

.\gradlew.bat :plugin-api:nodejs-api:verifyNodeJsApiMirror "-Pautojs.nodejs.plugin.root=$pluginRoot"
.\gradlew.bat :app:verifyNodeCapabilityManifestAlignment "-Pautojs.nodejs.plugin.root=$pluginRoot"
.\gradlew.bat :app:verifyNodeExamples
.\gradlew.bat :app:verifyNodePluginExampleMirror "-Pautojs.nodejs.plugin.root=$pluginRoot"
.\gradlew.bat :app:verifyNodeProjectWizard
.\gradlew.bat :app:verifyAutoJs6NodeTypeDeclarations
.\gradlew.bat :app:testAppDebugUnitTest
.\gradlew.bat :app:compileAppDebugKotlin
.\gradlew.bat :app:compileAppDebugAndroidTestKotlin
.\gradlew.bat :app:assembleAppDebug
~~~

The mirror/capability tasks are manual and are not part of preBuild.

## Real-Plugin Instrumentation

Install the assembled host APK and androidTest APK, then run focused classes with raw
output:

~~~powershell
$serial = "DEVICE_SERIAL"
$runner = "org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner"

adb -s $serial shell am instrument -w -r -e class org.autojs.autojs.engine.NodeRuntimePluginBridgeInstrumentationTest $runner
adb -s $serial shell am instrument -w -r -e class org.autojs.autojs.engine.NodeR6EngineSwitchInstrumentationTest $runner
adb -s $serial shell am instrument -w -r -e class org.autojs.autojs.engine.NodeExampleProjectInstrumentationTest $runner
adb -s $serial shell am instrument -w -r -e class org.autojs.autojs.engine.NodeR6RequiredPluginSecurityInstrumentationTest $runner
~~~

These cover six bridge/workspace/contract cases, two plugin/Rhino switching cases,
seven example groups, and required-plugin security. Run the provider-specific retained
classes when their host dispatch code changes.

## APK Ownership Check

Inspect the APK zip entries after assembly. No path may end in **libnode.so** or
**libautojs6-node.so**. The absence of those libraries plus a successful real-plugin
script proves the runtime stayed outside the host.

## Result Interpretation

- Confirm each class reports its expected test count and credible duration.
- Read raw status lines to distinguish executed tests from Assume skips.
- Treat permission-sensitive device failures according to [Flake policy](FLAKE_POLICY.md).
- Compile-only results do not prove plugin discovery, Binder execution, or host API
  dispatch.
