# Embedded Node Release Flavors

AutoJs6 uses a release packaging profile to decide whether Embedded Node native runtime files are shipped and which ABIs are valid. This is a packaging policy, not a new JavaScript compatibility profile.

Gradle property:

```properties
autojs.nodejs.releaseFlavor=nodeFull
```

Valid values:

- `nodeFull`
- `nodeMini`
- `rhinoOnly`

## nodeFull

`nodeFull` is the default profile for main app builds that include AutoJs6 Safe Node Profile.
Ordinary production `inrt` template builds force `rhinoOnly` so the checked-in template APK stays small even when
`gradle.properties` selects `nodeFull` for the main app; use
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

The report is written to `app/build/reports/nodejs/abi-size-report.md`. It records APK artifact sizes, the packaged template size, per-ABI `libnode.so` / `libautojs6-node.so` / `libc++_shared.so` payloads, and an estimated native payload comparison for `nodeFull`, `nodeMini`, and `rhinoOnly`.

Packaged APK validation:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagedDiagnosticReport `
  "-Pautojs.embeddedNode.packagedApk=<apk>" `
  "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

When `autojs.embeddedNode.expectedAbis` is omitted, the packaged APK verifier derives the expected ABI set from `autojs.nodejs.releaseFlavor`:

- `nodeFull`: `arm64-v8a,armeabi-v7a,x86_64`
- `nodeMini`: `autojs.nodejs.releaseAbis`, or `arm64-v8a`
- `rhinoOnly`: no Embedded Node native runtime

Smoke APK checks can still pass an explicit `autojs.embeddedNode.expectedAbis` value to verify a single exported APK without changing the global release profile.

## APK Size Tradeoff

`libnode.so` dominates APK size. Use `nativeNodeAbiSizeReport` before release so the APK total, packaged template size, and per-ABI native payload are visible in one place. The recommended release shape is:

- publish `nodeFull` as per-ABI APKs or split APKs when possible
- use `nodeMini` for arm64-only side-load channels
- keep `rhinoOnly` as a small fallback profile
- keep the production inrt template slim and let the APK builder inject Embedded Node native libraries on demand

User-facing download notes should call out that `nodeFull` carries the full Embedded Node runtime for all supported ABIs, `nodeMini` narrows the ABI set for smaller side-load builds, and `rhinoOnly` is a no-Node compatibility fallback. Avoid making universal multi-ABI Embedded Node APKs the default distribution unless the larger download size is acceptable for that channel.
