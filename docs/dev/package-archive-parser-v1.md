# Shared Package Archive Parser (`plugin-api/package-archive-parser`)

Status: module extracted on 2026-09-30 for plugin roadmap P1.1 of
`AutoJs6-Plugin-Three-Setup-Installer` (decisions D10 / D28). It holds the code the host used to
keep under `app/src/main/java/org/autojs/autojs/ui/main/scripts/` for reading Android package
archives; the host app and the installer plugin now consume the same classes. The plugin stages
the release AAR under `libs/package-archive-parser.aar` with a SHA-256 lock, exactly like the
Binder contract modules.

## Scope

Package `org.autojs.plugin.packagearchive`, an Android library without AIDL, resources or
`BuildConfig`; `Context` is used only by `PackageDeviceSpec.from(context)`.

| Source | Content |
| --- | --- |
| `AndroidPackageArchive.kt` | `AndroidPackageArchive` (staged selection, `stageSelectedApks`, `createDisplayApk`), `AndroidPackageFormat` (`APK`, `APKS`, `XAPK`, `APKM`, `APKZ`, `AAB`), `AndroidPackageSubtype`, `ArchiveInstallationState`, `ArchiveProblemCode` / `ArchiveProblem`, `ArchiveAssetEntry`, `ArchiveApkEntry`, `StagedApk`, `ManifestSummary`, `PackageDeviceSpec`, `AndroidPackageArchiveInspector.inspect(file, device, prepareForInstallation)`, `ManifestSummaryParser` |
| `BundletoolTocDecoder.kt` | `.apks` table-of-contents (`toc.pb`) decoding and device-targeted split selection |
| `ApkManifestDisplayDecoder.kt` | binary XML manifest decoding for display |
| `AabManifestDisplayDecoder.kt` | protobuf manifest decoding of `.aab` bundles for display |
| `ApkSignatureDetector.kt` | APK signature scheme detection (`v1`, `v2`, `v3`, `v3.1`, `v4`) |
| `PackageInspectionLimits.kt` | entry, size, name and time ceilings shared by every reader |

The six JVM tests moved with the sources (`AndroidPackageArchiveInspectorTest`,
`BundletoolTocDecoderTest`, `ApkManifestDisplayDecoderTest`, `AabManifestDisplayDecoderTest`,
`ApkSignatureDetectorTest`, `LargePackageInspectionTest`); they build their fixtures with
`ZipOutputStream` and need no Android runtime. `AndroidPackageArchiveDeviceTest` stays in the host
`androidTest` set because it needs a device `Context`.

## Changes made during the extraction

- Every former `internal` declaration is public: the plugin lives in another module and the
  classes are its parsing surface. Nothing else in the sources changed besides the package
  declaration.
- The host consumers (`ApkInfoDialogManager`, `DisplayManifestActivity`,
  `AndroidPackageSessionInstaller`, `PackageInstallerActivity`, the debug
  `ActivityLaunchCompatibilityProbe` and the device test) import the moved classes explicitly.
- Gson stays a dependency of the module for the `manifest.json` / `info.json` container
  descriptors; consumers of the raw AAR provide it themselves (both the host app and the plugin
  already depend on Gson).

## Relation to APK Inspector

`AutoJs6-Plugin-APK-Inspector` still carries its own, diverged copy of the same six files
(`AndroidPackageArchive.kt` differs by about 700 lines, `ApkSignatureDetector.kt` by 90) plus
inspector-only readers (signature verification, native library summary, 16 KB readiness, DEX
summary, container validation, AAB bundle config and resource table decoders). Plugin roadmap
D28 keeps those inspector-only readers out of this module; migrating the plugin to the shared AAR
is plugin roadmap P8 and does not block the installer's 1.0.0. Corrections worth folding back from
the inspector's copy are recorded here when they are adopted:

| Item | Decision |
| --- | --- |
| (none yet) | The extraction moved the host copy unchanged; the comparison with the inspector's `AndroidPackageArchiveValidator` and container metadata is scheduled with P8 |

## Distribution

- Host: `implementation(project(":plugin-api:package-archive-parser"))` in `app/build.gradle.kts`.
- Plugin: `:plugin-api:package-archive-parser:assembleRelease` produces
  `build/outputs/aar/package-archive-parser-release.aar`; the plugin copies it to
  `libs/package-archive-parser.aar` and records the SHA-256 in `locks/host-api-aars.lock`.
  The AAR must come from the same host build as `common-plugin-api.aar` and `installer-api.aar`.
