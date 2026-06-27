# Rhino Compat Packaged APK Smoke

Status: S11-29 packaged APK device gate and host contract report.

S11-29 adds a focused packaged APK smoke for Rhino compatibility APIs in Embedded Node. The smoke is implemented as:

```text
org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunRhinoCompatEmbeddedNodePackagedApk
```

Run it through the focused Gradle device gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatPackagedSmoke "-Pautojs.nodejs.test.deviceProfile=sony"
```

The focused gate selects only the S11-29 packaged Rhino compat instrumentation method, builds the packaged APK, installs it, runs the packaged Node entry, and checks required runtime markers.

## Host Contract

The host contract gate is:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatPackagedSmokeHost
```

It writes:

- `build/reports/nodejs/rhino-compat-packaged-smoke-verification.json`;
- `build/reports/nodejs/rhino-compat-packaged-smoke-verification.md`.

The JSON report uses schema `autojs6-node-rhino-compat-packaged-smoke-verification-v1`.

## Runtime Markers

The packaged smoke must emit and verify these markers:

| Marker | Purpose |
| --- | --- |
| `packaged.rhino_compat.ready=true` | all S11-29 packaged checks passed |
| `packaged.rhino_compat.loader=true` | `require("autojs6:compat")` loads with explicit opt-in status |
| `packaged.rhino_compat.toast=true` | `compat.toastLog(...)` reaches the packaged toast bridge |
| `packaged.rhino_compat.files=true` | `compat.files` can write, read, detect, and remove a scoped file |
| `packaged.rhino_compat.storage=true` | `compat.storages` roundtrips JSON through the packaged bridge |
| `packaged.rhino_compat.selector=true` | `compat.text(...).findOne()` and click wrappers route through accessibility |
| `packaged.rhino_compat.image=true` | `compat.images.read/clip/findImage/recycle` route through image handles |
| `packaged.rhino_compat.permissions=true` | bridge requests carry declared `node.permissions` |
| `packaged.rhino_compat.cleanup=true` | image handles created by the smoke are recycled |
| `packaged.rhino_compat.pending=0` | no pending bridge calls remain after the packaged script finishes |

## Boundary

The smoke uses the existing packaged fake bridge transport for storage, selector/accessibility, and image paths. It does not require live accessibility state, screenshot permission, or external files. The goal is to prove that Rhino compat loader and facade paths survive packaged APK construction, bridge permission manifests, script encryption, install/run, and resource cleanup.
