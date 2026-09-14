# AutoJs6 Node.js

Last reviewed: 2026-08-26.

AutoJs6 runs Node.js scripts through the separately installed AutoJs6 Node.js
Runtime Plugin. The host APK contains no libnode, Node JNI bindings, embedded Node
service, or selectable Node build backend. A Node-marked script requires the plugin
and fails clearly when the plugin is missing, disabled, unauthorized,
ABI-incompatible, or contract-incompatible; it is never reinterpreted as Rhino.

Ordinary JavaScript continues to use the Rhino engine through source routing. This
is not a Node backend switch. Inrt/exported applications do not support the external
Node runtime path.

## Ownership

| Concern | Owner |
| --- | --- |
| Node/V8/libuv, native bootstrap, module loading, runtime defaults and compatibility shims | Runtime plugin |
| User examples, TypeScript declarations, project wizard and their verification tasks | Runtime plugin |
| Node.js API contract source of truth | Runtime plugin, mirrored into the host for compilation |
| Script routing, plugin discovery/trust, Binder client and workspace transfer | Host |
| AutoJs6 API dispatch, Android permissions, capability providers and resource cleanup | Host |
| Minimal route/Binder/bridge/compiler integration coverage | Host |

Runtime feature fields omitted by the host use plugin-owned defaults. The host may
send an explicit restrictive value for a security boundary or focused test, but no
Gradle property selects runtime features, profiles, release flavors, or backends.

The legacy work_manager wire value **backend="embedded"** and diagnostic keys under
**embedded_script.*** remain protocol compatibility tokens. They do not indicate
that the host embeds or loads a runtime.

## Start Here

- [Quick start](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/nodejs/QUICK_START.md)
- [Current compatibility profile](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/nodejs/COMPATIBILITY_PROFILE.md)
- [Host/plugin integration and responsibilities](INTEGRATION_PLAN.md)
- [Security model](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/nodejs/SECURITY_MODEL.md)
- [Testing](TESTING.md)
- [Release checklist](RELEASE_CHECKLIST.md)
- [Rhino migration](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/nodejs/MIGRATION_FROM_RHINO.md)
- [Example projects](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/nodejs/EXAMPLES.md)
- [Error catalog](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/nodejs/ERROR_CATALOG.md)

## Terminal

The host ships an in-app terminal (drawer > Tools > Terminal, directory menu >
"Open in terminal", project toolbar). It runs `/system/bin/sh` as the AutoJs6 app
user inside a host-owned pty (jackpal libtermexec) with `HOME` / `PREFIX` under
`<filesDir>/terminal`. The shell itself never depends on the plugin.

`node`, `npm` and `corepack` are provided by the Node.js Runtime plugin once it
declares the terminal-launcher contract as `<meta-data>` on its runtime service:

| Key (`org.autojs.plugin.nodejs.` prefix) | Meaning |
| --- | --- |
| `NODE_CLI_SCHEMA` | contract schema, currently `1` |
| `NODE_CLI_EXECUTABLE` | multi-call ELF launcher in the plugin's native library dir, e.g. `libnodexe.so` |
| `NODE_CLI_COMMANDS` | comma-separated names the launcher dispatches on (`node,npm,npx,corepack,yarn,pnpm`) |
| `NODE_CLI_ARCHIVE` / `NODE_CLI_ARCHIVE_SHA256` / `NODE_CLI_ARCHIVE_ROOT` | npm + corepack asset archive, its digest, and the archive directory holding `npm/` and `corepack/` |
| `NODE_CLI_NPM_VERSION` / `NODE_CLI_COREPACK_VERSION` | informational versions shown in diagnostics |

The host reads the declaration from `ServiceInfo.metaData` (no Binder call), checks
that the launcher exists and is a real ELF, runs it once with `--version`, and caches
the outcome per plugin build. Until a plugin with the contract is installed and the
check succeeds, the terminal is shell-only and shows a banner explaining why.
"Developer options > Node.js > Node.js launcher check" re-runs the check and prints
the raw result.

## Cross-Repository Guards

Run ownership and alignment guards from the runtime-plugin repository. When the
repositories are not siblings, pass the host path to the plugin:

~~~powershell
Set-Location D:\idea-projects\AutoJs6-Plugin-NodeJs-Runtime
.\gradlew.bat verifyNodeHostIntegration "-Pautojs.host.root=D:\idea-projects\AutoJs6"
~~~

The aggregate verifies the host mirror of nodejs-api and aligns the independent host
and plugin capability manifests with the plugin-owned catalog. Example, declaration,
and wizard checks are plugin-local because those assets now have a single owner.
These manual checks are intentionally not attached to the normal host preBuild chain.

## Historical Documents

The completion roadmaps, comparison inventories, v1.1 draft, R2-R6 acceptance
records, and old deployment context are retained as historical evidence. Each such
file is a snapshot. Its old task names, paths, Gradle properties, release flavors,
and embedded-runtime topology are not current instructions.
