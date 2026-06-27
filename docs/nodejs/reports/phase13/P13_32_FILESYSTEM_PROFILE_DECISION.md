# P13-32 Filesystem Profile Decision

Status: partial

P13-32 records filesystem profile expansion as partial. The current Node
Profile v1.1 Safe track keeps `process.cwd()` scoped to the script working
directory while allowing a distinct scoped `sandboxRoot` for the containing
AutoJs6 workspace or project root. Pro/Desktop expansion to user-authorized
roots, `/sdcard`, SAF/document roots, and app-private roots stays staged behind
explicit authority and packaged evidence.

## Decision

- `require("autojs6:profile").filesystemProfile` exposes a frozen diagnostic
  snapshot with `mode="scoped_working_directory"` and
  `additionalRoots=[]`.
- Normal scoped file reads and `realpath` inside `sandboxRoot` remain allowed.
  `process.cwd()` remains the script directory, so relative paths still resolve
  from the launched script location.
- Scripts launched from a subdirectory of the AutoJs6 working directory may
  read parent files that remain inside that workspace, for example
  `../version.properties` from `$cwd/utils`.
- Absolute outside paths, `/sdcard`, `/proc`, `/sys`, `/dev`, NUL-containing
  paths, parent-directory escapes outside `sandboxRoot`, promise escapes,
  symlink escapes, and realpath escapes remain denied with stable AutoJs6
  scoped-fs error codes.
- No user-authorized root, SAF root, app-private root, or Android shared
  storage root is promoted by this phase.

## Boundary

The following remain future work before filesystem profile expansion:

- root authority provider design for user-selected directories, SAF/document
  tree grants, app-private roots, and optional Android shared storage roots;
- manifest/project metadata, grant persistence, revocation, diagnostics, and
  rollback behavior for each promoted root class;
- full symlink, realpath, `..`, NUL, `/proc`, `/sys`, `/dev`, and sensitive
  path audit for every future root class;
- packaged APK smoke tests proving explicit metadata, runtime disclosure,
  missing-grant failure behavior, and downgrade rollback.

P13-33 owns advanced desktop fs APIs that are still unavailable or
platform-limited even inside scoped roots. The docs, desktop matrix, release
matrix, surface manifest, and Gradle gate are evidence checks only and do not
add a second blocker for expanded-root promotion.

## Verification

- Host verifier: `tools/nodejs/phase13/verify-filesystem-profile-decision.js`
- Gradle gate: `:app:verifyNodePhase13FilesystemProfileDecision`
- Focused device gate:
  `NodeFilesystemProfileDecisionInstrumentationTest`
- Existing regression gates:
  `NodeSecurityRegressionInstrumentationTest`,
  `NodeScopedFsFuzzerInstrumentationTest`,
  `NodeSafeFileOperationsInstrumentationTest`, and
  `NodeFileHandleAdvancedInstrumentationTest`
- Focused expanded sandbox regression:
  `NodeSafeFileOperationsInstrumentationTest#expandedSandboxKeepsCwdAndAllowsWorkspaceParentRead`
- Type declarations:
  `docs/nodejs/types/autojs6-node/autojs_global.d.ts` and
  `sample/nodejs/typescript-smoke/src/main.ts`

## Remaining Work

- Implement the explicit root authority provider and decide whether it is
  Pro-profile, Desktop-profile, or split by root type.
- Add project/package metadata, user grant UI, revocation, diagnostics,
  rollback behavior, packaged expanded-root smokes, and live-device
  failure-mode evidence.
- Re-run the scoped fs fuzzer and security corpus against each promoted root
  class before exposing broader paths to scripts.
