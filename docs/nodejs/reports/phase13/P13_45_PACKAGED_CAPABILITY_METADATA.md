# P13-45 Packaged APK Capability Metadata

P13-45 records packaged APK capability metadata as partial. The current landing
adds `packagedCapabilityProfile` diagnostics, a structured
`packagedMetadata` summary inside the bridge permission manifest, focused
device instrumentation, a host verifier, and a Gradle gate.

## Covered

- `project.json node.profile`, `node.profiles`, `node.permissions`,
  `node.builtins`, `node.nativeAssets`, `node.filesystem.roots`,
  `node.executionMode`, and `node.network` are parsed into a read-only metadata
  summary.
- `package.json autojs6` / `autojs6.node` metadata can contribute the same
  packaged profile, builtin, native asset, filesystem root, and network fields.
- `node.permissions` remains the authority source for bridge dispatch and
  Android manifest permission synthesis.
- `network` capability metadata maps to `android.permission.INTERNET` through
  the existing manifest permission path.
- Declared builtins, native assets, filesystem roots, and profiles are
  diagnostic-only and do not grant runtime authority.
- Missing bridge capabilities still reject before dispatch in JavaScript.

## Remaining Work

- Profile metadata does not yet select a real Pro/Desktop packaged runtime
  profile.
- Builtin metadata does not widen the builtin allowlist.
- Native asset metadata does not load `.node` files or signed artifacts.
- Filesystem-root metadata does not grant broader roots beyond the current
  scoped working directory policy.
- Full packaged install/run synthesis, user disclosure, rollback, and
  profile-specific device matrices remain future work.

## Evidence

- Runtime diagnostics: `packagedCapabilityProfile`
- Manifest metadata: `NodeBridgePermissionManifest.PackagedMetadata`
- Focused test:
  `NodePackagedCapabilityMetadataPhase13DecisionInstrumentationTest`
- Existing evidence:
  `NodeBridgePermissionManifestInstrumentationTest`,
  `EmbeddedNodePackagedApkSmokeTest`,
  `NodeWorkManagerBridgeInstrumentationTest`,
  `NodeProjectRuntimeCompatibilityDescriptor`,
  `NodeProjectLaunchDescriptor`, and `ApkBuilder`
- Host verifier:
  `tools/nodejs/phase13/verify-packaged-capability-metadata-decision.js`
- Gradle gate:
  `:app:verifyNodePhase13PackagedCapabilityMetadataDecision`

## Verification

```powershell
node tools\nodejs\phase13\verify-packaged-capability-metadata-decision.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13PackagedCapabilityMetadataDecision
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePackagedCapabilityMetadataPhase13DecisionInstrumentationTest"
```
