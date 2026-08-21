# AutoJs6 Node.js

Last reviewed: 2026-08-21.

AutoJs6 runs Node.js scripts through the separately installed AutoJs6 Node.js
Runtime Plugin. The host APK does not contain libnode, JNI bindings for Node, or
an embedded Node service. A Node-marked script fails clearly when the required
plugin is missing, disabled, unauthorized, ABI-incompatible, or contract-incompatible;
it is never reinterpreted as Rhino.

## Ownership

| Concern | Owner |
| --- | --- |
| Node/V8/libuv, native bootstrap, module loading, TypeScript stripping, compatibility shims | Runtime plugin |
| Script routing, plugin discovery/trust, Binder client, workspace transfer | Host |
| AutoJs6 API dispatch, Android permissions, capability providers, resource cleanup | Host |
| Node.js API contract source of truth | Runtime plugin, mirrored into the host |
| User examples | Retained in both repositories and protected by a byte mirror check |

The host supports the **plugin** and **rhino** build backends. Historical backend
spellings are accepted only as migration aliases and resolve to the plugin backend.
Historical **nodeFull** and **nodeMini** release flavors now fail during configuration
with a migration message. Exported applications cannot bundle Node.js; projects that
still select the legacy Embedded Node.js APK-builder library are rejected explicitly.

The legacy work_manager wire value **backend="embedded"** and diagnostic keys under
**embedded_script.*** remain protocol compatibility tokens. They do not indicate
that the host embeds or loads a runtime.

## Start Here

- [Quick start](QUICK_START.md)
- [Current compatibility profile](COMPATIBILITY_PROFILE.md)
- [Host/plugin integration and responsibilities](INTEGRATION_PLAN.md)
- [Security model](SECURITY_MODEL.md)
- [Testing](TESTING.md)
- [Release checklist](RELEASE_CHECKLIST.md)
- [Rhino migration](MIGRATION_FROM_RHINO.md)
- [Example projects](EXAMPLES.md)
- [Error catalog](ERROR_CATALOG.md)

## Cross-Repository Guards

The repositories are not assumed to be siblings. Pass the plugin path explicitly:

~~~powershell
.\gradlew.bat :plugin-api:nodejs-api:verifyNodeJsApiMirror "-Pautojs.nodejs.plugin.root=D:\idea-projects\AutoJs6-Plugin-NodeJs-Runtime"
.\gradlew.bat :app:verifyNodeCapabilityManifestAlignment "-Pautojs.nodejs.plugin.root=D:\idea-projects\AutoJs6-Plugin-NodeJs-Runtime"
.\gradlew.bat :app:verifyNodePluginExampleMirror "-Pautojs.nodejs.plugin.root=D:\idea-projects\AutoJs6-Plugin-NodeJs-Runtime"
~~~

These are manual checks. They are intentionally not attached to preBuild or check.

## Historical Documents

The completion roadmaps, comparison inventories, v1.1 draft, R2–R6 acceptance
records, and old deployment context are retained as historical evidence. Each such
file is marked as a snapshot. Its old task names, paths, release flavors, and
embedded-runtime topology are not current instructions.
