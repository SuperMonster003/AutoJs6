# P13-46 Plugin System Parity

P13-46 records plugin system parity as partial. The current landing adds
`pluginSystemProfile` diagnostics, keeps the JS-only local plugin manager as the
only callable plugin surface, verifies the host capability ceiling, and adds a
Phase 13 verifier plus Gradle gate.

## Covered

- `require("plugins")` remains the project-local JS-only manager under
  `./plugins/<id>`.
- `plugins.install()`, `load()`, `start()`, `stop()`, `unload()`,
  `reload()`, `enable()`, `disable()`, `configure()`, `status()`,
  `manifest()`, `resolve()`, `list()`, and `capabilities()` remain the tracked
  manager operations.
- Plugin manifests must stay inside the project plugin directory and request an
  exact `plugin.<id>` capability plus any bridge capabilities they need.
- Plugin manifests cannot declare `plugin` or `plugin.*` namespace capabilities
  in `autojs6.plugin.permissions`; plugin namespace ownership comes from the
  manager-validated package id.
- The host project/package permission manifest is the authority ceiling.
  Plugin-declared permissions do not grant extra host authority.
- Missing host capabilities reject before plugin code runs.
- Native `.node` files, native plugin payloads, Android plugin APK bridges,
  remote marketplace install/update, signed archives, and persistent global
  install state remain denied or deferred.

For P13-46 v1.1, plugin-declared permissions are request metadata only. They
cannot promote host bridge authority beyond the exact capabilities already
declared by the host project or package manifest.

## Remaining Work

- Android provider plugin adapters need a signed protocol, permission mapping,
  lifecycle ownership, and device evidence.
- Native plugins need ABI/hash allowlists, crash isolation, signature policy,
  and packaged install/run evidence before any promotion.
- Remote marketplace install/update needs trust, rollback, integrity, cache, and
  user-disclosure policy.
- Packaged plugin distribution is still limited to checked local plugin files;
  signed archive extraction and update policy remain future work.
- Previously returned JavaScript object references cannot be revoked after a
  plugin is disabled; stronger isolation still needs a worker/process boundary.

## Evidence

- Runtime diagnostics: `pluginSystemProfile`
- Focused test:
  `NodePluginSystemParityPhase13DecisionInstrumentationTest`
- Existing JS-only manager evidence:
  `NodePluginSystemInstrumentationTest`
- Design and policy:
  `docs/nodejs/design/PLUGIN_SYSTEM.md` and
  `docs/nodejs/reports/phase9/PLUGIN_POLICY.md`
- Host verifier:
  `tools/nodejs/phase13/verify-plugin-system-parity-decision.js`
- Gradle gate:
  `:app:verifyNodePhase13PluginSystemParityDecision`

## Verification

```powershell
node tools\nodejs\phase13\verify-plugin-system-parity-decision.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13PluginSystemParityDecision
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePluginSystemParityPhase13DecisionInstrumentationTest"
```
