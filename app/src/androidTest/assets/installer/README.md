# Installer script smoke fixtures

`fixture-v1.apk` and `fixture-v2.apk` are the existing, no-code fixtures copied from
`AutoJs6-Plugin-Three-Setup-Installer/app/src/androidTest/assets`. Their source manifests and
rebuild script live in that repository's `tools/spike-fixtures` and
`tools/build-spike-fixtures.ps1`. Both use package
`io.github.supermonster003.autojs6.installer.spike.fixture`, contain no application code,
and have version codes 1 and 2.

SHA-256:

- v1: `fec583a389978fdc298e9d85979b95feceb93e6fb3fd3f581511c826401e8b69`
- v2: `dc3716a9c54e63219ed621ef06f68c4088f54630e4b4c167aebfdda653a4bb38`

The device test verifies these hashes and package identity before use. Installation requires
`installer.smoke.authorizer=shizuku`, an already granted Shizuku connection, and proof that
the fixture and retained fixture data are absent across every enumerated Android user.
Only the test-owned package is removed during final cleanup. The AAB is generated inside the
test's unique cache directory from the shared parser's minimal AAPT2 protobuf schema.

The lifecycle tests use the instrumentation-only `InstallerSmokePipeProvider`. Its random-token
sources never contain a valid APK; the tests wait for the real Binder `preparing` event and
observe pipe-reader closure after cancellation or script exit.
