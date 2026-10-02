# Installer source profiles: host evidence

Date: 2026-10-02. Host source baseline for this addition: `129cd804d8`, version 6.8.0 / 5311.
Other tasks' terminal files were already dirty and are excluded from this change. The installer
implementation advances the host sequence to build 5312. All commits remain local.

## Negotiated option presence

The contract stays at V3 with the same eleven public AIDL transactions. `source-profiles`,
`applySourceProfiles` and `sourceDeleteRequested` are additive vocabulary. The host opts in
only using the selected session's actual Binder capabilities, and establishes that local fact
before `openSession` can synchronously return a terminal callback.

The script parser snapshots explicitly supplied fields. The serializer omits absent profile
options only for an opted-in request. Explicit false/auto/current/none remain explicit; null is
accepted only for installer/installReason/packageSource resets. An explicitly null advanced
field still needs V3 plus advanced-install-options. Legacy peers retain the full previous
shape without the marker or newly permitted nulls. Interaction, timeout and continueOnError
stay session-wide; profiles do not acquire deadline ownership.

## Owned-source cleanup

The host owns deletion for file/path sources from scripts and host UI. Cleanup executes after
results, off the Binder/UI thread, while descriptors still exist. The session-local negotiated
flag cannot be forged by result JSON. Explicit false vetoes deletion; missing or malformed
sourceDeleteRequested retains sources with a note. Failed, unprocessed and successful false
items protect shared canonical paths and proven regular-file identities. Content URI ownership
is never claimed. Existing inode, replacement, symlink and modification checks remain in force.

The pure decision helper is shared by the script and host UI paths. It preserves confirmed
installation success even when deletion is impossible or a malformed plugin decision is
returned. UI cleanup also preserves an already observed terminal result across a cancelled
waiter's dispatcher handoff. JSON ceilings and typed script result overlays are retained.

## Validation

- `build/p9-profiles-api-verify.log`: installer-api Release assembly and 10 JVM tests passed,
  zero failures/errors/skips.
- `build/p9-profiles-host-verify.log`: 105 installer-related JVM tests across 14 classes passed,
  zero failures/errors/skips. App Debug production and Android test Kotlin compilation passed.
  The existing InstallerHostEntryDeviceTest always-true-condition warning remains unchanged.
- Pure tests cover omitted versus explicit defaults/nulls, immutable option snapshots,
  V1/V2/V3 and per-feature negotiation, malformed result cleanup decisions, explicit false,
  shared retained/failed/unprocessed items, physical aliases and legacy behavior. Existing
  maximum-size result and batch deletion tests still pass.
- Three Android regression methods were added for early callback negotiation, actual host UI
  file cleanup before descriptor closure and a retained hard-link alias. Compilation is
  verified here; execution is recorded separately when a dedicated device runs this build.
- Ten-language host changelog JSON and 22 generated history documents were rendered using the
  repository generator's pure rendering functions. The subsequent check reports 32 outputs
  with zero drift. Unrelated README metadata was preserved.

The API AAR is fixed at `build/p9-profiles-api-fixed/installer-api-release.aar`, 33,220 bytes,
SHA-256 `4681a41e3e10fc2c8589cf3e5e0f4c898e07155290c48ada9c4527a7b87c5b0b`.
Its three production/test API files are those committed with this evidence; the independent
API module does not encode the host versionCode. The shared manifest parser AAR is unchanged.
This evidence does not claim a new device installation matrix or a remote publication.

## First dedicated-device run and alias fixture correction

The fixed host 5312 and test APKs were run only on this task's dedicated API 31 emulator-5562.
The early callback negotiation and host UI owned-file cleanup methods passed. The initial
hard-link method failed at `Os.link`, before exercising cleanup. The device's audit log explicitly
denied the `link` permission from `untrusted_app` to its own `app_data_file`; cache and files
were both internal app data, not external/FUSE paths. Moving the fixture directory cannot
remove this SELinux restriction, and no permissions or SELinux policy were changed.

Build 5313 changes only the test fixture: the existing test-only ContentProvider receives a
duplicate descriptor for a uniquely named, test-owned private file and returns another descriptor
at `fixture/<UUID>`. The ordinary path and content source have distinct ownership keys but the
same actual fstat device/inode identity. The test verifies that a retained content alias protects
the owned file. A finally block closes exactly that registration and verifies it is no longer
openable, and the original private file/directory is removed by the fixture cleanup. There is no
shell-created hard link, permission expansion, source-path transfer or success-by-skip.
The pure JVM physical-alias/hard-link decision coverage remains; real device hard-link creation
is not claimed. The renamed method is `profileRetainedProviderAliasProtectsTheOwnedFile`.

The original failure remains in the installer plugin's
`build/profiles-host-device-5562/result.txt`. Later execution of the corrected test is recorded
with the main plugin's device evidence. The installer production implementation and API AAR
remain those of build 5312.

The corrected Android test assembly passed in 4m52s. Its first incremental compiler attempt
lost an unrelated Python test class and a compiler backup, then Gradle's automatic non-daemon
fallback completed successfully. No shared cache was deleted and no other task's sources were
changed. The fixed artifact is `build/p9-profiles-alias-fixed/host5313-androidTest.apk`,
3,433,353 bytes, SHA-256
`59defd5b943ad8f7c512eb82654b28d5cb018fc24e1b50ded520ed4c21bf10d7`.
DEX inspection confirms the new alias method and registration/cleanup implementation are present,
and the old hard-link method is absent. Use this test APK with the fixed production host 5312;
no replacement production host APK was built for the fixture correction.
