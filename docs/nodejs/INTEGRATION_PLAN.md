# AutoJs6 Node.js Host/Plugin Integration

Last reviewed: 2026-08-21.

The migration is complete: Node runtime execution is plugin-only.

## Runtime Topology

1. The host recognizes a Node-marked file or project.
2. NodePluginJavaScriptEngine builds a bounded request.
3. The host discovers and validates the fixed runtime-plugin service, signature,
   authorization, host-version range, contract version, and ABI.
4. The host transfers an explicit workspace archive and module-source provider
   handles through the published AIDL contract.
5. The plugin owns Node/V8/libuv startup, module resolution, TypeScript processing,
   execution, cancellation, and runtime cleanup.
6. Plugin bridge calls return to the host capability broker.
7. The host validates declaration, permission, provider health, limits, and resource
   ownership before invoking Android/AutoJs6 APIs.
8. Results and permitted workspace changes return to the host.

## Source Boundaries

The host retains the plugin client, script routing, workspace transport, capability
manifest/broker/providers, encrypted module-source provider, Android policy, project
descriptor resolution, Node Doctor, user docs/types, and examples.

The plugin retains libnode and all C++/JNI runtime code, runtime services, Node module
loader and compatibility shims, JavaScript-side preflight, and native-runtime tests.

The nodejs-api contract is authored in the plugin and mirrored into the host.
Capabilities and examples have separate cross-repository manual guards.

## Build Policy

- **nodePlugin** is the normal release mode and resolves to the plugin backend.
- **rhinoOnly** disables Node execution.
- Historical native/embedded/auto/pluginOptional backend names migrate to plugin.
- Historical nodeFull/nodeMini release flavors fail with a migration message.
- The host APK never copies native libraries from the plugin repository.
- Exported applications cannot request an Embedded Node.js library.

See [Testing](TESTING.md) for acceptance and [Security model](SECURITY_MODEL.md)
for authority boundaries.
