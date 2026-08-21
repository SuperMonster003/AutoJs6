# Node.js Post-GA Smoke Schedule

Last reviewed: 2026-08-21.

## Per Change

- Host unit tests and main/androidTest compilation.
- Contract/capability/example guard when the boundary changes.
- Focused real-plugin test for the changed route or provider.

## Weekly

- Core plugin bridge class.
- Plugin/Rhino engine switching.
- Seven example-project groups.
- Required-plugin security/denial class.
- APK inspection proving no libnode entry.

## Before Release

- Run the full [Release checklist](RELEASE_CHECKLIST.md).
- Sample every published plugin ABI and at least two Android/OEM boundaries.
- Verify install, update, disable, unauthorized, and contract-mismatch behavior.
- Run a bounded persistent-runtime soak from the retained R6 instrumentation.

Skipped tests are reported separately and never counted as smoke passes.
