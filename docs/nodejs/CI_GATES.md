# AutoJs6 Node.js Verification Gates

Last reviewed: 2026-08-21.

The former phase, alpha/beta/RC, embedded-packaging, native dependency, and Node
Doctor Gradle gates have been retired. No Node-specific task is attached to the
default preBuild chain.

## Host-Only Checks

~~~powershell
.\gradlew.bat :app:verifyNodeExamples
.\gradlew.bat :app:verifyNodeProjectWizard
.\gradlew.bat :app:verifyAutoJs6NodeTypeDeclarations
.\gradlew.bat :app:testAppDebugUnitTest
.\gradlew.bat :app:compileAppDebugAndroidTestKotlin
.\gradlew.bat :app:assembleAppDebug
~~~

The TypeScript smoke task additionally requires a local TypeScript toolchain:

~~~powershell
.\gradlew.bat :app:typeCheckNodeTypescriptSmoke
~~~

## Cross-Repository Checks

~~~powershell
$pluginRoot = "D:\idea-projects\AutoJs6-Plugin-NodeJs-Runtime"
.\gradlew.bat :plugin-api:nodejs-api:verifyNodeJsApiMirror "-Pautojs.nodejs.plugin.root=$pluginRoot"
.\gradlew.bat :app:verifyNodeCapabilityManifestAlignment "-Pautojs.nodejs.plugin.root=$pluginRoot"
.\gradlew.bat :app:verifyNodePluginExampleMirror "-Pautojs.nodejs.plugin.root=$pluginRoot"
~~~

The plugin repository is authoritative for nodejs-api. The two repositories retain
their example trees intentionally, and the mirror task compares all files by
content. Capability alignment allows only documented catalog-version exceptions.

## Device Gate

Compile success is not runtime acceptance. Install the current host and current
runtime plugin, verify the plugin version, and run the real-plugin classes listed
in [Testing](TESTING.md). Treat an implausibly fast JUnit success as suspicious:
Assume-based tests can report OK while skipping the body when plugin discovery
fails.
