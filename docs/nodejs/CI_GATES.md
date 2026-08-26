# AutoJs6 Node.js Verification Gates

Last reviewed: 2026-08-26.

The former phase, embedded-packaging, backend-selection, native-dependency, Node
Doctor, sample-mirror, declaration, and project-wizard host Gradle gates are retired.
No Node-specific task is attached to the default host preBuild chain.

## Host Checks

~~~powershell
.\gradlew.bat :app:testAppDebugUnitTest --offline
.\gradlew.bat :app:compileAppDebugKotlin :app:compileAppDebugAndroidTestKotlin --offline
.\gradlew.bat :app:assembleAppDebug --offline
~~~

## Plugin Checks

Run from **AutoJs6-Plugin-NodeJs-Runtime**:

~~~powershell
.\gradlew.bat verifyNodePluginExamples verifyNodeProjectWizard `
  verifyAutoJs6NodeTypeDeclarations typeCheckNodeTypescriptSmoke --offline
.\gradlew.bat verifyNodeHostIntegration `
  "-Pautojs.host.root=D:\idea-projects\AutoJs6" --offline
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline
~~~

The plugin repository is authoritative for nodejs-api, examples, declarations,
wizard tooling, and runtime defaults. `verifyNodeHostIntegration` checks the host API
mirror and capability alignment; the host and plugin manifests remain independent
because they enforce different process boundaries.

## Device Gate

Compile success is not runtime acceptance. Install the current host and runtime
plugin, verify the plugin version, and run the real-plugin classes listed in
[Testing](TESTING.md). Treat an implausibly fast JUnit success as suspicious:
Assume-based tests can report OK while skipping the body when plugin discovery fails.
