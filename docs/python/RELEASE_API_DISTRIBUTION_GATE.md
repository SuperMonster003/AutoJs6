# Python Runtime release API distribution gate

`verifyPythonReleaseApiDistributionGate` creates a local, current-tree bundle of
three release AARs for the independent Python runtime plugin. The manifest
records the current Python Runtime protocol version (now `1.1`) rather than a
historical R1 phase label. It does not publish an artifact and it does not prove
plugin consumption, Android execution, Binder/PFD behavior, CPython execution,
or device acceptance.

## Checkable contract

- [x] Depend on `:plugin-api:common-plugin-api:assembleRelease`,
  `:plugin-api:protocol-wire-api:assembleRelease`, and
  `:plugin-api:python-runtime-api:assembleRelease`.
- [x] Order the Python Runtime assembly after Protocol Wire assembly even when
  Gradle parallel execution is enabled.
- [x] Copy the original, byte-identical `*-release.aar` files without renaming
  them into `build/distributions/python-runtime-release`.
- [x] Reject missing, empty, debug-named, non-release, duplicate, or extra AARs.
- [x] Record the host Git HEAD, whole-tree dirty flag, deterministic source
  fingerprint, protocol version, TaggedWire version, dependency direction,
  filenames, byte counts, and SHA-256 digests in
  `distribution-manifest.json`.
- [x] Remove any stale distribution and PASS receipt before the selected task
  graph executes; write the manifest and receipt via same-directory atomic
  moves.
- [x] Scope the receipt to `LOCAL_RELEASE_AAR_DISTRIBUTION_ONLY` and explicitly
  record `published=false`, `pluginConsumed=false`,
  `androidRuntimeVerified=false`, and `deviceVerified=false`.
- [x] Run the gate on the current tree and independently inspect its outputs.
- [x] Stage only the manifest-matched AAR bytes into the sibling plugin, rename
  them only at that explicit staging boundary, and pin their exact hashes.

Current-tree byte counts, hashes, source identity, and the exact three-artifact
set are recorded in the generated `distribution-manifest.json`. Copies staged
into the sibling plugin are renamed to `common-plugin-api.aar`,
`protocol-wire-api.aar`, and `python-runtime-api.aar`. Downstream plugin
consumption does not retroactively widen this distribution receipt.

The source fingerprint covers `build.gradle.kts`, `settings.gradle.kts`, and all
non-build files under `plugin-api/protocol-wire-api` and
`plugin-api/python-runtime-api`. Files are sorted by root-relative slash path;
each fingerprint record is
`path NUL byte-count NUL file-SHA-256 LF`, and the concatenated records are
hashed with SHA-256.

## Serialized execution

Before starting Gradle, confirm that no other Codex process is running a Gradle
task. If one is active, poll and wait for up to nine minutes rather than starting
a concurrent build. Then run:

```powershell
.\gradlew.bat --console=plain verifyPythonReleaseApiDistributionGate
```

Expected outputs after a successful current-tree run:

- `build/distributions/python-runtime-release/common-plugin-api-release.aar`
- `build/distributions/python-runtime-release/protocol-wire-api-release.aar`
- `build/distributions/python-runtime-release/python-runtime-api-release.aar`
- `build/distributions/python-runtime-release/distribution-manifest.json`
- `build/reports/python/release-api-distribution-gate.json`

An absent receipt, task failure, hash mismatch, or changed source fingerprint is
not releasable evidence. Even a PASS receipt means only that these three local
release AAR bytes were distributed from the recorded host tree.
