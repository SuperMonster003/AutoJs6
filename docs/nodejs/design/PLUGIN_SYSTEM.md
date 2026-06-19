# Node Plugin System JS-Only Manager

Status: S7-43 implements a local JS-only manager through `require("plugins")`.
`plugins.install(id, options?)` registers an already-present sanitized package
under `./plugins/<id>` for the current execution only. The manager now also has
per-plugin namespace metadata, explicit capability subsets,
`load`/`start`/`stop`/`unload` lifecycle state, a plugin-owned resource
registry, and explicit debug-only hot reload. There is still no download
installer, signed archive verifier, update channel, Android plugin adapter,
native provider, native plugin loader, or packaged APK plugin runtime.

## Goal

Node Profile plugins must not become a back door around the Safe Node Profile.

AutoJs6 already has multiple extension concepts:

- npm packages loaded from the scoped Node project
- AutoJs6 Android plugins exposed through AIDL-style APIs
- packaged APK templates and project assets
- native libraries bundled with the host app

A Node plugin model must define which of these are allowed, how they are
declared, how permissions are granted, how updates are verified, and whether
native code is ever acceptable. Until that model exists, Node scripts should use
ordinary pure JavaScript npm packages and named AutoJs bridge modules only.

## Current Decision

Expose only a project-local, JS-only manager in v1.1:

```js
const plugins = require("plugins");
const status = plugins.install("org.example.demo", {
  source: "./plugins/org.example.demo",
  config: { mode: "safe" }
});
const info = plugins.manifest("org.example.demo");
const entryPath = plugins.resolve("org.example.demo", "helper");
const demo = plugins.load("org.example.demo");
const started = plugins.start("org.example.demo");
const current = plugins.status("org.example.demo");
plugins.stop("org.example.demo");
plugins.disable("org.example.demo");
plugins.enable("org.example.demo");
plugins.unload("org.example.demo");
plugins.reload("org.example.demo", { debug: true });
```

Manager rules:

- plugin id resolves from `./plugins/<id>` under the Node working directory
- `package.json` must contain `autojs6.plugin`
- `autojs6.plugin.permissions` must be a string array
- `autojs6.plugin.dependencies` may declare string plugin dependency ids for
  diagnostics; it does not auto-load dependencies
- `autojs6.plugin.exports` may map named exports to `.js` or `.cjs` module
  paths under the plugin directory
- project metadata must declare exact `plugin.<id>` capability and every
  plugin-requested capability before install/load/enable/configure/unload
- local `node_modules/plugins` cannot shadow `require("plugins")`
- plugin entry must be `.js` or `.cjs` and stay inside the plugin directory
- plugin-relative and plugin package resolution use the plugin directory as the
  resolver root
- each plugin record has a path-rooted namespace such as
  `plugin:org.example.demo`; this is module-resolution isolation, not a
  separate process or VM context
- `plugins.capabilities(id)` and lifecycle contexts expose the effective
  capability subset: exact `plugin.<id>` plus plugin-requested permissions
- `plugins.start(id)` loads the main export and calls `exports.start(ctx)` when
  present; `plugins.stop(id)` calls `exports.stop(ctx)` when present, then
  releases all plugin-owned resources
- lifecycle `ctx.resources` can own timers and tracked provider/UI/socket/image
  handles; stop/unload/disable release registered resources even when a plugin
  forgets to release them explicitly
- `plugins.reload(id, { debug: true })` unloads cached modules and refreshes the
  local package descriptor; calls without `debug: true` fail with
  `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`
- `.node` files anywhere below the plugin directory reject with
  `ERR_AUTOJS6_NATIVE_ADDON_DISABLED`; common native library/object file
  extensions are rejected by the JS-only plugin policy
- plugin symlinks are rejected
- `install()` does not copy files, download packages, run package-manager
  lifecycle scripts, or persist global state; it validates and registers the
  local package for the current embedded Node execution
- `disable()` blocks future loads, stops the plugin if needed, releases owned
  resources, and unloads cached plugin modules; previously returned JavaScript
  object references cannot be revoked

Illustrative project metadata:

```json
{
  "type": "node",
  "node": {
    "permissions": [
      "plugin.org.example.demo",
      "toast"
    ]
  }
}
```

Native addons remain disabled:

```text
require("./addon.node") -> ERR_AUTOJS6_NATIVE_ADDON_DISABLED
process.dlopen -> unavailable
```

Existing AutoJs6 plugin-center metadata and AIDL plugin APIs are not
automatically visible to Node. A future Node plugin can call Android or AutoJs
authority only through a named bridge/provider contract with explicit
capabilities and tests.

## Terminology

| Term | Meaning |
| --- | --- |
| npm package | Pure JavaScript package resolved by the scoped CommonJS resolver. |
| Node plugin | Future signed package that extends Node Profile through declared bridge capabilities. |
| Android plugin APK | Existing AutoJs6 plugin ecosystem package installed as an Android app. |
| Native plugin | Any plugin carrying `.so`, `.node`, or other native executable code. |
| Plugin manifest | Signed metadata that declares identity, permissions, entry points, assets, hashes, and compatibility. |

## Mode Comparison

| Mode | Description | Decision |
| --- | --- | --- |
| Pure JS npm package | Runs under existing scoped resolver and builtin policy. | Already supported; not a plugin system. |
| Manifested JS plugin | Signed package with JS entry and declared bridge capabilities. | Possible first Node plugin model. |
| Android plugin adapter | Node talks to an installed AutoJs6 plugin through a bridge provider. | Possible later, AIDL/permission design required. |
| Signed native provider plugin | Native code runs behind a host-owned provider boundary. | High risk, not v1.1. |
| Node native addon | Node loads `.node` directly into embedded process. | Rejected for default profile. |
| Unrestricted downloaded code | Plugin downloads and runs new code after install. | Rejected. |

## Prototype Package Format

The S7-43 manager registers unpacked project-local directories only:

```text
project/
  main.cjs
  project.json
  plugins/
    org.example.demo/
      package.json
      index.cjs
      lib/
        helper.cjs
```

Minimum package metadata:

```json
{
  "name": "org.example.demo",
  "version": "1.0.0",
  "main": "index.cjs",
  "autojs6": {
    "plugin": {
      "id": "org.example.demo",
      "permissions": ["toast"],
      "dependencies": ["org.example.shared"],
      "exports": {
        ".": "index.cjs",
        "helper": "lib/helper.cjs"
      }
    }
  }
}
```

`autojs6.plugin.id` must match the requested id. If the id field is omitted,
`package.json name` must match. `autojs6.plugin.entry` may override `main`, but
the target must remain a `.js` or `.cjs` file under the plugin directory. Named
exports use the same path policy, and dependency ids are declarative metadata
only.

## Future Package Format

A future Node plugin should be a deterministic archive with a top-level manifest:

```text
plugin.zip
  autojs6-node-plugin.json
  package/
    index.cjs
    package.json
  assets/
  abi/
    arm64-v8a/
    armeabi-v7a/
  signatures/
    manifest.sig
```

The first implementation should allow only `package/` and `assets/`. `abi/`
must be rejected until signed native plugins are explicitly approved.

Illustrative manifest:

```json
{
  "schemaVersion": 1,
  "id": "org.example.autojs6.node.plugin.demo",
  "name": "Demo Plugin",
  "version": "1.0.0",
  "nodeProfile": {
    "min": "1.1.0",
    "max": "1.x"
  },
  "entry": "package/index.cjs",
  "permissions": {
    "requires": ["toast"],
    "exports": ["plugin.org.example.demo"]
  },
  "android": {
    "permissions": [],
    "services": [],
    "receivers": []
  },
  "native": {
    "enabled": false,
    "abis": []
  },
  "javaInterop": {
    "enabled": false
  },
  "assets": [
    {
      "path": "assets/model.bin",
      "sha256": "..."
    }
  ],
  "signature": {
    "scheme": "autojs6-plugin-signature-v1",
    "signer": "..."
  }
}
```

Required manifest properties:

- stable reverse-DNS plugin id
- semantic version
- Node Profile compatibility range
- deterministic entry path under the archive
- exact file hashes
- requested capabilities
- exported plugin capabilities
- Android manifest additions, if any
- native-code declaration, default `false`
- signer identity

The manifest must be parsed before any plugin code is resolved.

## Permission Declaration

Plugins must not grant themselves authority.

Effective authority should be the intersection of:

```text
script/project declared capabilities
  AND plugin manifest requested capabilities
  AND host/package Android permissions
  AND feature flag policy
```

Rules:

- a project must opt into using a plugin id
- a project must declare the bridge capabilities the plugin will use
- a plugin can export namespaced capabilities, such as
  `plugin.org.example.demo`
- plugin-exported capabilities do not imply core bridge capabilities
- `shell.root`, `ui.overlay`, native code, Java interop, and network remain
  explicit high-risk declarations
- missing declarations reject before code execution or provider dispatch
- the S7-43 manager treats `plugin.<id>` as an exact declaration; a generic
  `plugin` permission does not grant access to every plugin id
- legacy compatibility mode does not apply to `plugins.load()`; no enforced
  project/package capability manifest means the plugin load is denied

## Lifecycle And Resources

Lifecycle hooks are ordinary CommonJS exports:

```js
exports.start = function(ctx) {
  ctx.resources.setInterval(tick, 1000);
  ctx.resources.track("ui", uiHandle);
  ctx.resources.track("image", imageHandle);
};

exports.stop = function(ctx) {
  // Optional cooperative shutdown. The manager still releases registered
  // resources after this hook returns.
};
```

The lifecycle context contains:

- `id`, `namespace`, and `owner`
- `phase`, either `start` or `stop`
- frozen `config`
- frozen effective `capabilities`
- frozen plugin-declared `permissions`
- `resources`, the plugin-owned registry

The registry supports `track(kind, handle, cleanup?)`, `release(token)`,
`addCleanup(cleanup)`, `snapshot()`, and owned timer helpers. Supported tracked
resource kinds are `timer`, `provider`, `ui`, `socket`, `image`, and `generic`.
When no cleanup function is supplied, the manager looks for common safe cleanup
methods such as `close()`, `destroy()`, `dispose()`, `unsubscribe()`,
`cancel()`, or `recycle()` depending on the resource kind. This gives JS-only
plugins a common owner boundary while Android bridge providers remain
provider-owned and governed by the normal bridge resource registry.

`plugins.stop(id)`, `plugins.unload(id)`, and `plugins.disable(id)` release
registered resources. Hook or cleanup functions may return promises; in that
case the manager method that triggered cleanup, including `install`, `disable`,
`start`, `stop`, `reload`, or `unload`, returns a promise. Synchronous plugins
retain the synchronous behavior introduced by the first manager cut.

Example project metadata:

```json
{
  "type": "node",
  "node": {
    "plugins": [
      {
        "id": "org.example.autojs6.node.plugin.demo",
        "version": "1.0.0"
      }
    ],
    "permissions": [
      "toast",
      "plugin.org.example.demo"
    ]
  }
}
```

## Native Code Policy

Native code is disabled unless a separate signed-native-plugin model is
implemented.

Default denials:

- `.node` native addons
- `process.dlopen`
- arbitrary `.so` extraction and loading
- `System.load` and `System.loadLibrary`
- native code downloaded after install
- ABI fallback to unknown or host-incompatible libraries

If signed native plugins are ever allowed, they must use a provider boundary:

- native code is declared in the plugin manifest
- every ABI library has a hash and symbol archive
- supported ABI list is exact
- no direct Node `.node` loading into the embedded process
- native calls happen through a host-owned bridge or isolated service
- crashes are attributed to plugin id, version, ABI, and signer
- release gates include ABI size, dependency, and symbol reports
- packaged APKs include only reviewed ABI slices

Native support must not be enabled merely because a plugin archive contains
native files.

## Java Interop Policy

Plugins must not expand Java interop.

Rules:

- `java_interop` remains disabled by default
- plugins cannot add classes to the Java allowlist at runtime
- plugins cannot expose raw `Context`, `Activity`, `Service`, `View`, Binder,
  `ClassLoader`, reflection, filesystem, process, or native-library APIs
- Android operations must be represented as named bridge/provider methods
- AIDL plugin APIs must be wrapped by a host-owned provider that returns JSON
  snapshots or opaque handles
- provider handles are scoped to one Node execution and cleaned up on stop

If an Android plugin adapter is added, Node should see only the adapter's
declared JSON protocol, not the Android plugin service object.

## Packaging Policy

Packaged APK support must be designed before plugins ship.

Required packaged behavior:

- packaged project declares exact plugin ids and versions
- plugin archives are bundled at build time or rejected
- no first-run plugin download is required for a packaged app to start
- Android permissions, services, receivers, providers, and foreground-service
  types are injected only from reviewed plugin metadata
- package identity and signer are recorded in diagnostics
- encrypted scripts cannot request undeclared plugin authority at runtime
- ABI-specific native plugin assets are included only for selected APK ABIs
- model or data assets have size budgets and hashes

Packaged APK verifier must fail closed when:

- a declared plugin is missing
- a plugin manifest hash does not match
- a plugin requests unsupported Android manifest entries
- a plugin requests native code while native plugins are disabled
- plugin ABI assets do not match the packaged ABI set

## Update And Signature Policy

Plugins must be verified before installation and before update.

Minimum policy:

- plugin archives are signed
- signer identity is pinned by plugin id after first trusted install
- update version must be monotonic unless user explicitly allows rollback
- all files listed in the manifest have hashes
- unlisted executable files reject installation
- update channel is explicit: official, third-party, local, or developer
- official and third-party indexes are cached but not trusted without archive
  signature verification
- silent background updates are not allowed for plugins used by packaged APKs
- rollback retains old archive and diagnostics until cleanup

Third-party plugins should require clearer user confirmation than official
plugins. Developer/local plugins should show diagnostics that they are outside
normal release support.

## Resolver Policy

Plugin resolution must be deterministic.

Rules:

- a plugin id resolves only if declared in project metadata
- local `node_modules/<plugin-id>` cannot shadow a declared signed plugin
- signed plugin packages cannot shadow core bridge modules
- plugin code uses the same scoped resolver and builtin allowlist as user code
- plugin package dependencies are vendored or resolved inside the plugin archive
- a future signed archive runtime must keep plugin code from reaching outside
  its scoped plugin unpack directory
- the S7-43 manager reserves only `require("plugins")`; it does not make
  plugin ids directly require-able as package names
- the S7-43 manager enforces plugin-directory resolution for plugin
  `require()` calls, but it is not a separate process, context, or filesystem
  authority

Pure npm packages remain separate from the plugin resolver. They do not receive
plugin privileges.

## Runtime Boundary

Future plugin execution should be one of:

- same Node execution, pure JavaScript only, no extra authority except declared
  bridge calls
- host-owned provider, where plugin code runs in Android/Kotlin/Java and Node
  sees only JSON bridge methods
- isolated service/process for high-risk native or Android plugin adapters

The first plugin model should prefer pure JavaScript plus bridge provider
interfaces. High-risk plugins should not run in the embedded Node process.

## Diagnostics

Node Doctor and release reports should include:

- plugin system enabled/disabled
- declared plugin ids and versions
- installed plugin ids, versions, signers, and hashes
- requested and effective capabilities
- native-code status
- Java interop status
- packaged plugin manifest entries
- last plugin verification error
- plugin crash attribution

Crash intake should collect plugin id, version, ABI, signer, and native symbol
archive id when a plugin is involved.

## Test Gate

Before any implementation, add tests for:

- manifest parser accepts valid package and rejects malformed schema
- hash mismatch rejects before code execution
- unsigned or wrong-signer update rejects
- undeclared plugin id cannot be required
- local `node_modules` cannot shadow signed plugin ids
- plugin cannot shadow core bridge modules
- missing project capability rejects before plugin code runs
- plugin requested capability not declared by plugin manifest rejects
- native files reject while native plugins are disabled
- Java interop remains disabled through plugins
- packaged verifier fails closed for missing plugin, unsupported native code, and
  Android manifest mismatch
- diagnostics report plugin id, version, signer, and capability state
- service destroy and script stop release plugin-owned handles

## Open Questions

- Should Node plugin installation be managed by the existing AutoJs6 plugin
  center or by project-local archives only?
- Should plugins be per-project, per-user, or global app installations?
- What signer trust roots are acceptable for official and third-party plugins?
- Can packaged APKs include third-party plugins, or only project-local signed
  archives?
- Should plugin code be allowed to declare its own npm dependencies?
- Should plugin updates be allowed while a script that uses the plugin is
  running?
- Should Android plugin APK adapters run in the plugin app process, host app
  process, or a dedicated bridge service?
- What is the minimum symbol and ABI metadata required for native plugin crash
  triage?

## Recommendation

Keep the S7-43 implementation as a local JS-only lifecycle manager, not a full plugin
system. The first shippable direction beyond this manager should still be
manifested pure JavaScript plugins with signed archives, explicit project
opt-in, no native code, no Java interop expansion, and no packaged APK runtime
downloads.

Signed native provider plugins require a separate design and must not relax the
default native addon denial.
