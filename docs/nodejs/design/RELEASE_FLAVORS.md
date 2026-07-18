# Node.js Release Flavors

AutoJs6 uses a release packaging profile to decide whether Node runs in an
external runtime plugin, whether Embedded Node native files are shipped, and
which ABIs are valid. This is a delivery and packaging policy, not a new
JavaScript compatibility profile.

Gradle property:

```properties
autojs.nodejs.releaseFlavor=nodePlugin
```

Valid values:

- `nodeFull`
- `nodeMini`
- `nodePlugin`
- `rhinoOnly`

Release and gate tasks enforce this backend matrix:

| Release flavor | Default backend | Allowed release/gate backends | Embedded Node libraries in host APK |
| --- | --- | --- | --- |
| `nodePlugin` | `plugin` | `plugin` | none |
| `nodeFull` | `embedded` | `embedded`, `pluginOptional` | all supported ABIs |
| `nodeMini` | `embedded` | `embedded`, `pluginOptional` | configured ABI set |
| `rhinoOnly` | `rhino` | `rhino` | none |

Unknown flavors or backends always fail configuration. Non-release debug
builds may use `rhino`, `native`, `embedded`, `plugin`, `pluginOptional`, or
`auto` as explicit diagnostic overrides. `pluginOptional` is valid only with `nodeFull` or
`nodeMini`, where an embedded fallback is actually packaged.

## nodePlugin

`nodePlugin` is the default main-app release profile. It uses the required
external Binder backend and does not package `libnode.so`,
`libautojs6-node.so`, or another host Embedded Node native payload.
`libc++_shared.so` may still appear for unrelated native dependencies. A missing,
disabled, unauthorized, incompatible, or unavailable runtime plugin fails
closed; this flavor does not silently fall back to embedded execution.

The external process uses request-scoped workspace archive transport v2. It
receives explicit inputs through PFDs, executes in plugin-private storage, and
returns a verified output archive plus deletion tombstones. It never receives
a host sandbox path or recursively scans the host sandbox. Because side effects
may occur after dispatch, response/commit loss is terminal and is never replayed.

This backend runs Node inside the plugin's dedicated process. It is distinct
from the legacy compatibility loader that loads `.so` files from an installed
plugin APK into an AutoJs6-owned process, and from the project-local JS-only
`require("plugins")` manager. See
[RUNTIME_PLUGIN_BACKEND.md](RUNTIME_PLUGIN_BACKEND.md).

Expected use:

- default main-app distribution with a separately installed runtime plugin
- release channels that keep the large native runtime out of the host APK
- live R5 Binder lifecycle and contract verification

The completed R5 gate passed all eight tests without skips on
`arm64-v8a`, `armeabi-v7a`, and `x86_64`; see
[../R5_ACCEPTANCE_SAMPLING.md](../R5_ACCEPTANCE_SAMPLING.md).

## nodeFull

`nodeFull` is the full self-contained Embedded Node profile.
Ordinary production `inrt` template builds force `rhinoOnly` so the checked-in template APK stays small even when
the main app selects `nodePlugin`; use
`assembleInrtFullNodeRelease` or `embeddedNodeRefreshFullInrtTemplateApk` when a full Embedded Node template artifact is
needed for runtime testing.

Required ABIs:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`

Each selected ABI must package:

- `libnode.so`
- `libautojs6-node.so`
- `libc++_shared.so`

`nodeFull` intentionally does not allow narrowing with `autojs.nodejs.releaseAbis`. Use `nodeMini` for a smaller ABI set.

Expected use:

- full release candidates
- side-load builds where APK size is acceptable
- regression testing across supported native runtime ABIs
- full Embedded Node inrt test templates generated outside `assets-app/template.apk`

## nodeMini

`nodeMini` keeps Embedded Node enabled but allows a smaller ABI set.

Default ABI:

- `arm64-v8a`

Override:

```powershell
.\gradlew.bat --console=plain :app:assembleAppRelease `
  "-Pautojs.nodejs.releaseFlavor=nodeMini" `
  "-Pautojs.nodejs.releaseAbis=arm64-v8a"
```

Allowed ABIs:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`

Expected use:

- smaller side-load APKs
- focused device testing
- release channels that publish per-ABI artifacts

## rhinoOnly

`rhinoOnly` disables Embedded Node native packaging and is intended for compatibility releases that preserve legacy Rhino scripts without shipping Node native libraries.

Node Profile v1 scripts must not be silently reinterpreted as ordinary Rhino scripts when Embedded Node is disabled or missing. Release gates should treat a Node-marked entry in a Node-disabled build as a clear fail-fast path with `ERR_AUTOJS6_NODE_DISABLED`, unless a maintainer explicitly selected the legacy Rhino-backed Node compatibility backend for diagnostics and recorded that choice in the release notes.

The final APK must not package:

- `libnode.so`
- `libautojs6-node.so`

`libc++_shared.so` can still appear if other native dependencies need it. The validation rule is specifically about Embedded Node runtime libraries.

Expected use:

- smallest compatibility build
- low-end devices
- emergency fallback releases that preserve Rhino scripts without shipping Node
- ordinary production `assembleInrtRelease` template APKs, with Embedded Node native libraries injected later only when the packaged project selects the `Embedded Node.js` library

## ABI Policy

Supported:

- `arm64-v8a`: primary release target
- `armeabi-v7a`: supported when smoke and stress coverage pass
- `x86_64`: emulator/debug or optional release target

Unsupported:

- `x86`: fail-fast

If `x86` is selected through `autojs.nodejs.releaseAbis`, or if `app/src/main/jniLibs/x86/libnode.so` appears, the Gradle build fails with an actionable error. x86 should stay unsupported until `libnode.so`, `libautojs6-node.so`, packaged APK smoke, and real device/emulator coverage are all explicitly added.

## Validation

Main APK validation:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagingPreflightAppDebug
```

ABI size report:

```powershell
.\gradlew.bat --console=plain :app:nativeNodeAbiSizeReport
```

The report is written to `app/build/reports/nodejs/abi-size-report.md`. It records APK artifact sizes, the packaged template size, per-ABI `libnode.so` / `libautojs6-node.so` / `libc++_shared.so` payloads, and an estimated native payload comparison for `nodePlugin`, `nodeFull`, `nodeMini`, and `rhinoOnly`.

Packaged APK validation:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagedDiagnosticReport `
  "-Pautojs.embeddedNode.packagedApk=<apk>" `
  "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

When `autojs.embeddedNode.expectedAbis` is omitted, the packaged APK verifier derives the expected ABI set from `autojs.nodejs.releaseFlavor`:

- `nodeFull`: `arm64-v8a,armeabi-v7a,x86_64`
- `nodeMini`: `autojs.nodejs.releaseAbis`, or `arm64-v8a`
- `nodePlugin`: no host Embedded Node native runtime
- `rhinoOnly`: no Embedded Node native runtime

Smoke APK checks can still pass an explicit `autojs.embeddedNode.expectedAbis` value to verify a single exported APK without changing the global release profile.

## APK Size Tradeoff

`libnode.so` dominates APK size. Use `nativeNodeAbiSizeReport` before release so the APK total, packaged template size, and per-ABI native payload are visible in one place. The recommended release shape is:

- use `nodePlugin` for the default main-app distribution
- publish `nodeFull` as per-ABI APKs or split APKs when possible
- use `nodeMini` for arm64-only side-load channels
- keep `rhinoOnly` as a small fallback profile
- keep the production inrt template slim and let the APK builder inject Embedded Node native libraries on demand

User-facing download notes should call out that `nodePlugin` requires the
external Node runtime plugin, `nodeFull` carries the full Embedded Node runtime
for all supported ABIs, `nodeMini` narrows the ABI set for smaller side-load
builds, and `rhinoOnly` is a no-Node compatibility fallback. Packaged apps
remain self-contained: the slim inrt template receives Embedded Node libraries
through APK-builder injection and does not depend on the external plugin.
