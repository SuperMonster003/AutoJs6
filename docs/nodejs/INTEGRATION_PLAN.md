# AutoJs6 Node.js Host/Plugin Integration

Last reviewed: 2026-08-26.

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
descriptor resolution, Node Doctor, and host-facing integration documentation.

The plugin retains libnode and all C++/JNI runtime code, runtime services, Node module
loader and compatibility shims, JavaScript-side preflight, runtime defaults, native
runtime tests, examples, TypeScript declarations, and developer project tooling.

The nodejs-api contract is authored in the plugin and mirrored into the host.
The plugin owns the cross-repository API/capability alignment guard.

## Build Policy

- Node-marked host sources always route to the required runtime plugin.
- Ordinary non-Node JavaScript routes to Rhino independently of Node.
- Inrt/exported applications reject the external Node runtime path.
- There is no Node backend, release-flavor, identity, profile, or runtime-feature
  Gradle property in the host.
- The host APK never copies native libraries from the plugin repository.
- Runtime defaults and optional runtime behavior are implemented by the plugin.

See [Testing](TESTING.md) for acceptance and [Security model](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/nodejs/SECURITY_MODEL.md)
for authority boundaries.
