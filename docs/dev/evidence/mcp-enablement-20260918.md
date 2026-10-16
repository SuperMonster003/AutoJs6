# MCP enablement after adb installation

The host now uses `PluginDefaultEnabledPolicy` for Plugin Center, `McpServerPluginInspector`, and the `McpServerPluginHost` Binder selector. MCP requires an explicit enable choice. Existing saved `true` and `false` values still take precedence; discovery does not create an enable preference.

## Cause and reproduction

The shared default policy only excluded YOLO. For an installed MCP plugin without a saved enable preference, Plugin Center therefore selected `true`, while both MCP runtime consumers selected `false`. This affected installations outside Plugin Center, including adb, and was independent of Android activation or signature authorization.

On an owned read-only `AVD_API_33` x86_64 instance, the companion MCP APK was installed with adb. The new instrumentation test uses the real installed-package discovery, Plugin Center item mapper, preference store and runtime inspector, with no online index. Against the pre-fix host APK it reported:

```text
center=true, enabledState=READY, runtime=PLUGIN_DISABLED
Tests run: 2, Failures: 1
```

The explicit enable/disable case passed on that APK. The missing-preference case failed at the first package-manager snapshot, exactly reproducing the inconsistency. The new JVM default-policy regression also failed before the production change.

APK SHA-256 values for that comparison:

| APK | SHA-256 |
| --- | --- |
| Pre-fix host | `594cb133a079df6fccc964627f59c3bda9d46de77970ad52ca08a1f3b8030af5` |
| Fixed host | `cf1eb5843d45cdfbf37f36c93cfba35436fbf206571fd737d3b84512c220980e` |
| MCP companion | `ebb4353f0c65faf613912b05c2dbe26c059c6ef725b187f3e0251e77cfd84caa` |

## Verification

- Host: AutoJs6 6.8.0 / 5281. Companion: MCP Server 1.0.2 / 65. Device: API 33 x86_64, `emulator-5584`, private read-only instance; no physical devices were modified.
- `:app:testAppDebugUnitTest --tests org.autojs.autojs.core.plugin.center.* --tests org.autojs.autojs.core.plugin.mcp.*`: 89 tests in 23 classes, zero failures, errors or skips.
- `:app:assembleAppDebug` and `:app:assembleAppDebugAndroidTest`: passed.
- The same device regression against the fixed host passed. It covers absent preferences in both initial snapshot and full discovery, no implicit preference writes, explicit `true -> false -> true`, and repeated Plugin Center view-model creation.
- `McpServerPluginRoundTripTest` also passed: real `openServer`, host capability attachment, running status, stop and close. Combined instrumentation result: `OK (3 tests)`, 2.218 s, zero skips.
- Ten changelog JSON sources and all 22 generated Markdown documents were updated. A second generator run left their hashes unchanged. Generation used the generator's checked-in related-project dates through its network-error fallback, avoiding unrelated external date refreshes.
- Full `:app:lintAppDebug` did not complete. The initial attempt reached dependency-version lookup and waited in the Google Maven HTTPS handshake. The offline retry ran for more than 15 minutes without a new report; thread snapshots showed `JoinEffectDetector.afterCheckRootProject` and `Analysis.eval` doing whole-project dataflow analysis. Only the owned Gradle clients were stopped, and the APK assembly tasks were then run separately. The previous report dated 2026-09-14 was preserved as historical evidence, not treated as a result for this change. No lint rules or build configuration were weakened.
- The plugin repository's Markdown check (10 languages / 36 artifacts) and `ApplicationTextPunctuationTest` passed for the roadmap update.

The IDE debugger and file-build tools are scoped to the MCP plugin project and rejected the sibling host path. The runtime evidence above comes from Android instrumentation rather than debugger logpoints. No public API, contract AAR, dependency, permission or plugin implementation changed. No release build or physical-device / ColorOS first-install activation test was needed for this host default-policy correction; those checks were not run.

Raw pre-fix failure, post-fix device output, JVM reports, build logs, lint thread snapshots and both compared host APKs are retained under the ignored `build/verification/mcp-enablement-20260918/` directory. The final APK assembly includes the generated changelog; all 38 DEX files match the device-tested fixed APK byte-for-byte.

An unrelated `docs/dev/apk-size-reduction-roadmap.md` edit remains in the host worktree. Following the MCP repository's AGENTS section 3.3, the scoped repair and evidence are left uncommitted rather than combining that user work into this change. Existing source changes present at session start were preserved byte-for-byte, and changelog generation preserved all prior entries.

## Update 2026-10-16

The maintainer decided that an MCP Server (and a YOLO provider) signed with the host's official certificate counts as enabled without a saved record, like the official 3-Stove Agent; trusted third-party, user-granted and unknown signers still require an explicit choice. Plugin Center, `McpServerPluginInspector` and the `McpServerPluginHost` Binder selector keep sharing `PluginDefaultEnabledPolicy`, now with the package's signer (`AidlPluginHost.defaultEnabledFor`), so they still agree. `McpServerEnablementDeviceTest` therefore expects the missing-preference case to follow the signer of the installed APK (enabled for an official release, disabled for a test-signed build). Starting the server, the pairing confirmation (MCP roadmap D6) and the tool group switches are unchanged; the MCP roadmap records the decision under D18 (MCP plugin repository commit `1b3811c`).
