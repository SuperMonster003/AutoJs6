# AutoJs6 Node Profile v1 Known Issues

This page lists known limitations for the AutoJs6 Safe Node Profile v1 and practical workarounds. It is user-facing and
should be updated before every GA or release-candidate publication.

## Product Boundary

Node Profile v1 is not full desktop Node.js. It is a controlled Android runtime with scoped filesystem access, restricted
builtins, explicit bridge capability checks, packaged APK support, and Android lifecycle constraints.

Workaround: use the stable CommonJS subset and consult [NODE_PROFILE_V1_STABLE_SURFACE.md](NODE_PROFILE_V1_STABLE_SURFACE.md)
before relying on a Node or Android API.

## ESM And `.mjs`

`.mjs` files route to Node, but default ESM execution is rejected unless an experimental build enables
`autojs.nodejs.esm.experimental=true`. Dynamic import has a separate gate.

Workaround: use `.cjs` or `"nodejs";` CommonJS scripts for stable production scripts.

## Runtime `npm install`

Runtime `npm install`, postinstall scripts, lifecycle scripts, native builds, and unchecked `.bin` execution are not
supported.

Workaround: vendor pure JavaScript CommonJS dependencies, or use the host dependency bundling gate before packaging.

## Native Addons

`.node` native addons are disabled. They can bypass scoped fs, process, and Android safety boundaries.

Workaround: use pure JavaScript packages or an AutoJs6-owned bridge module.

## `child_process`

Node `child_process` is disabled.

Workaround: use `require("shell").exec()` for controlled non-interactive shell commands, with explicit timeout, scoped
`cwd`, JSON-safe environment values, and declared `shell` or `shell.root` capability.

## Raw Network Modules

Raw `http`, `https`, `net`, `tls`, `dns`, and `dgram` authority is disabled in normal builds. Controlled `fetch` and
`websocket` wrappers exist, but live Android network I/O remains experimental and off by default.

Workaround: use controlled `fetch` or `websocket` only when an approved build/device profile enables the network provider.
Do not assume raw Node networking exists.

## Worker Threads

`worker_threads` remains experimental and off by default. Workers, when explicitly tested, have a smaller capability set
than parent scripts and must not access AutoJs bridge modules or Android objects.

Workaround: keep stable automation scripts single-execution and Promise-based.

## Java And Android Interop

Raw Java and Android object access is disabled. The experimental Java allowlist is off by default and is not a replacement
for AutoJs bridge modules.

Workaround: use explicit bridge modules such as `app`, `device`, `clipboard`, `shell`, `accessibility`, or
`media_projection`.

## Scoped Filesystem

Filesystem access is limited to the script working directory. Absolute paths, NUL bytes, `..`, symlink escapes, realpath
escapes, and sensitive Android paths are denied.

Workaround: place required files inside the project directory and pass relative paths.

## Bridge Provider Availability

Some bridge modules have stable JavaScript API shape but partial Android provider availability:

- `dialogs` may reject until a UI provider is connected.
- `accessibility` exposes selector descriptors and fake-provider tests, but real actions require a safe accessibility provider.
- `media_projection` and `image` require permission and provider support for real capture/image handling.
- `work_manager` records metadata only and does not launch background Node jobs yet.

Workaround: handle rejected Promises and check provider readiness before depending on device capabilities.

## Packaged APK Size And ABI

Embedded Node native libraries are large. `nodeFull` carries `arm64-v8a`, `armeabi-v7a`, and `x86_64` by policy. `x86`
is unsupported and must fail fast.

Workaround: prefer split or per-ABI APKs for distribution. Use `nodeMini` or `rhinoOnly` only when the release flavor is
explicitly selected and verified.

## Embedded Node Disabled Builds

Builds that disable Embedded Node script execution, or `rhinoOnly` compatibility releases that do not ship Node native
libraries, are rollback tools. They preserve legacy Rhino scripts but are not allowed to hide a Node Profile v1 runtime
failure by silently treating a Node-marked script as an ordinary Rhino script.

Expected behavior: embedded Node execution fails clearly with `ERR_AUTOJS6_NODE_DISABLED` and the diagnostic message
names `-Pautojs.nodejs.embedded.script.enabled=true`.

Workaround: publish a Node-enabled `nodeFull` or `nodeMini` build for Node Profile v1 scripts. Use the legacy
Rhino-backed Node compatibility backend only as an explicit diagnostic choice and record it in release notes.

## Android Foreground Service Constraints

Packaged Node scripts use an embedded service process and Android foreground-service requirements. Android 14 and later
enforce service types and permissions more strictly.

Workaround: keep packaged manifest verification in the release gate and use the generated packaged diagnostic report.

## Current Sony Gate Device

Current unattended Node Profile v1 gates use Sony XQ-AT72 `QV710AF65F` with the temporary package identity `org.autojs.autojs6.nodejs`.

Preparation for local focused gates:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.nodejs
adb -s QV710AF65F shell cmd deviceidle whitelist +org.autojs.autojs6.nodejs.test
adb -s QV710AF65F shell am start -n org.autojs.autojs6.nodejs/org.autojs.autojs.launcher.AdaptiveIconAlias
```

OnePlus OPD2413 is a legacy Phase 4 evidence device only. It is excluded from current unattended gates because APK installation requires manual confirmation.

## Current RC Gate Caveats

As of the Phase 4 RC record:

- Packaged smoke host verification is green after the JNI compatibility fix.
- Lifecycle stress is green in the Phase 4 evidence set.
- Reduced memory stress is green in the Phase 4 evidence set.
- Full default memory stress and the full aggregate release-candidate rerun still need a final Sony publication report.

See [NODE_PROFILE_V1_RC.md](NODE_PROFILE_V1_RC.md).
