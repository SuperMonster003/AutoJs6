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

`nodeFull` is the default profile for builds that include AutoJs6 Safe Node Profile.

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

`rhinoOnly` disables Embedded Node native packaging and forces the effective Node runtime backend to Rhino for the build.

The final APK must not package:

- `libnode.so`
- `libautojs6-node.so`

`libc++_shared.so` can still appear if other native dependencies need it. The validation rule is specifically about Embedded Node runtime libraries.

Expected use:

- smallest compatibility build
- low-end devices
- emergency fallback releases that preserve Rhino scripts without shipping Node

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

`libnode.so` dominates APK size. The recommended release shape is:

- publish `nodeFull` as per-ABI APKs or split APKs when possible
- use `nodeMini` for arm64-only side-load channels
- keep `rhinoOnly` as a small fallback profile

Avoid making universal multi-ABI Embedded Node APKs the default distribution unless the larger download size is acceptable for that channel.
