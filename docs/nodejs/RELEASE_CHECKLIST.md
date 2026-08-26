# Node.js Host/Plugin Release Checklist

Last reviewed: 2026-08-26.

## Repository and Contract

- [ ] Record host and runtime-plugin revisions.
- [ ] Confirm unrelated worktree changes are excluded.
- [ ] Run plugin-owned `verifyNodeHostIntegration`.
- [ ] Run plugin example, project-wizard, declaration, and TypeScript checks.

## Host Build

- [ ] Run host unit tests.
- [ ] Compile main and androidTest Kotlin.
- [ ] Assemble the universal debug/release-equivalent artifact under test.
- [ ] Confirm no host Node runtime configuration task or BuildConfig flag exists.
- [ ] Confirm the APK has no libnode.so or libautojs6-node.so.
- [ ] Confirm only normal host jniLibs are packaged.

## Device

- [ ] Install the exact host and plugin artifacts.
- [ ] Record plugin version and contract; do not infer them from an old install.
- [ ] Run NodeRuntimePluginBridgeInstrumentationTest.
- [ ] Run NodeR6EngineSwitchInstrumentationTest.
- [ ] Run NodeHostPluginIntegrationInstrumentationTest.
- [ ] Run NodeR6RequiredPluginSecurityInstrumentationTest.
- [ ] Verify test bodies executed and no Assume skip was counted as a pass.
- [ ] Verify missing/disabled/unauthorized plugin failure remains explicit.

## Documentation and Distribution

- [ ] Quick start, compatibility, security, testing, and plugin HOST-API agree.
- [ ] Historical embedded-runtime documents remain marked as snapshots.
- [ ] Release notes say the runtime is externally installed.
- [ ] No exported-APK or selectable Node backend/flavor claim remains in active guidance.

See [Testing](TESTING.md) for exact commands.
