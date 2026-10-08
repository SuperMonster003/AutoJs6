# P0.2 in-process loading evidence

Date: 2026-10-02. Scope: the P0 draft and a dedicated debug host, not the frozen V1 contract or
the script-facing `compose` API. P0.3 / Q1 was decided on 2026-10-02; the historical P0.2 results below remain unchanged.

## Sources and isolation

- Plugin baseline: `master@3af9f57`, 1.0.0 / 6. Measurements below used build 6 with the P0.2 changes.
- Host baseline: `e86186920d`, 6.8.0 / 5309. The original host worktree had unrelated uncommitted
  inspector, terminal, package parser and documentation changes. None were changed or staged here.
- Host changes live in the separate `AutoJs6-ComposeUi-Spike` worktree, branch `spike/compose-ui-p0`.
  `-PcomposeUiSpike=true` builds package `org.autojs.autojs6.compose.spike`, with separate providers,
  preferences and test package. Existing AutoJs6 installations were not replaced.
- `plugin-api/compose-ui-api` builds a release AAR with no Compose dependency. The `api.spike`
  package and contract version -1 explicitly distinguish it from the future V1 API.
  SHA-256: `c39b0cc59834d1b9e98d7b2fdde5b8184eae7ff9fe8ff2d32a843f53a44ca437`.
- The debug host upgrades lifecycle 2.6.2 -> 2.9.4, savedstate 1.2.1 -> 1.3.2,
  emoji2 1.3.0 -> 1.4.0 and window 1.0.0 -> 1.5.0. Release host variants are unchanged.
  The baseline's resolved core is already 1.16.0, despite the 1.15.0 catalog declaration.
- `locks/host-shared-deps.lock` is copied identically into both repositories. Its 51 sorted
  `coordinate=version\n` records have SHA-256
  `f3042acc624d499feea9907a20257b62debaa6c523f40bfce19b084274334576`.
  This is a debug experiment fingerprint, not a compatibility guarantee for host build 5308.

## Implementation and observed results

| Check | Evidence | Result |
| --- | --- | --- |
| Factory discovery and cast | INFO category + application factory metadata; `PathClassLoader(apk, apk!/lib/<process ABI>, host.classLoader)` | Successful on debug and release plugin APKs |
| Class identity | Factory returns actual `Class` objects, compared with the host loader | Compose UI/runtime from plugin; contract, Kotlin, CoroutineContext, coroutines, core, activity, appcompat, lifecycle and savedstate from host |
| Activity owner chain | AppCompatActivity, as used by ScriptExecuteActivity; identity of both view-tree owners checked | Compose attaches and renders |
| Counter round trip | Accessibility node click on `+1`, queued host callback, replacement UiNode tree, accessibility text `Count: 1` | Successful; no synchronous callback during composition |
| RawWindow | Real host RawWindow/FloatyService; no initial owner; explicit LifecycleRegistry and SavedStateRegistryController attached before adding the view | Composition created, attached and disposed on close |
| Material resource table | Pinned Material 3 `m3c_dialog` resolved through public Resources lookup; compared against independently configured plugin resources | English, Chinese and Arabic resolve; density 240 and night mode match supplied Configuration |
| Theme | ContextWrapper retains Activity theme and window/system services | No getTheme override needed for this counter; broader components are not yet verified |
| Native search path | Factory iterates a Compose Path with move/line/close and asserts 3 segments | API 33 exercises graphics-path JNI; API 37 uses the platform iterator |
| Exit | DisposableEffect creation/disposal parity, no live composition, Recomposer reaches ShutDown after Activity destroy | Successful |
| Input limits | SpikeTreeTest exercises snapshot detachment, unsupported types, leaf children, callback id, node count, depth and text boundaries | 5 JVM tests pass; invalid replacement is rejected before state publication |
| Failure gates | Device test checks missing/disabled/unauthorized eligibility snapshots, host-version and draft-version rejection, missing reflective class; actual PluginEnableStore disable/restore | Distinct typed codes; absence and authorization are injected gate inputs, not destructive package/signer changes |

Resource and accessibility checks are deliberately separate: accessibility is read from the actual
Material Button/Text tree, while the private Material resource name is a diagnostic lookup only.
No private Compose API is linked and no TalkBack manual acceptance or broad Material component
coverage is claimed. The resource is retained explicitly in `raw/compose_spike_keep.xml`.

## R8 findings that debug builds did not reveal

1. Removing Kotlin from the plugin APK is not viable while the Kotlin INFO service runs in the
   plugin process. It remains bundled, but must preserve its ABI for parent-first rendering.
2. Default R8 renamed CoroutineContext to `s9`. AndroidUiDispatcher inherits the host coroutine
   hierarchy, causing `ClassCastException: d3 cannot be cast to s9` during view creation.
3. Keeping Kotlin types and members still allowed R8 8.13.19 to synthesize optimized facade methods.
   A call to `CollectionsKt.f(Iterable)` failed with NoSuchMethodError because the parent copy has
   no such method. P0 therefore uses `-dontoptimize`, retains shrinking and obfuscation, keeps
   `kotlin.**`, and repackages other obfuscated classes under the plugin's private namespace.
4. compileOnly lifecycle-viewmodel does not contribute its consumer rules to plugin R8.
   `LifecycleRetainedValuesStoreOwner` lost its reflected constructor and failed at Activity view
   attachment. The relevant ViewModel no-arg constructor rule is now explicitly included.
5. Both complete device suites passed after these corrections. `.python/verify_apk_classpath.py`
   audits DEX definitions, required Kotlin names, absence of the draft contract, and absence of
   shared AndroidX/coroutines copies. Gradle separately checks dependency versions and exclusions.

The IDE debugger could not access the host worktree from the plugin project (outside-project
path rejection). Diagnosis used real device exception stacks, R8 mapping and repeated instrumented
reproductions. No debugger values or heap-leak analysis are claimed.

## Device evidence

| Device | API / ABI / page size | Plugin debug | Plugin release | Evidence |
| --- | --- | --- | --- | --- |
| Redmi 22120RN86C, serial bek749scrwv4wo8h | 33 / arm64-v8a / 4096 | 5 host probe tests pass | 5 host probe tests pass | E3 |
| AVD_API_37.1_16K, serial emulator-5574 | 37 / x86_64 / 16384 | 5 host probe tests pass | 5 host probe tests pass | E2 |

Both use the actual host app code, dedicated debug package, host build 5309 and plugin build 6.
The plugin activation/discovery suite also passed on API 37 (5 tests, debug plugin); API 33 uses
the matching debug plugin and debug instrumentation package. A debug instrumentation APK cannot
be run against the minified release target's removed R classes; release rendering is tested by
the host suite instead.

The following are single observations in separate fresh instrumentation processes, each running
only the PSS method against the release plugin. Debug.MemoryInfo values are KiB. GC and a 500 ms
settling interval precede each reading; they include Activity/window and concurrent host startup
work, so they are not a pure renderer allocation benchmark or a regression threshold.

| Device | Before mount | After mount | After destroy | Mount delta |
| --- | --- | --- | --- | --- |
| Redmi API 33 | 215976 | 229078 | 230295 | +13102 |
| API 37 / 16 KB AVD | 213067 | 213495 | 215627 | +428 |

After-destroy PSS does not return to baseline, as loaded classes, resource caches and host work
remain. Composition/recomposer shutdown is asserted independently; no general absence-of-leaks
claim is inferred from PSS. LeakCanary/heap retention analysis belongs to the later robustness gate.

Build-6 APK observations after the final R8 correction: debug 9,275,784 bytes / 88,417 DEX method
references; release 1,459,210 bytes / 28,213 references. Counts sum DEX method-id tables, not just
declared methods. Signed distribution and 16 KB native alignment use appendDigestToReleasedFiles.

Final metadata build 7 was rechecked before committing: the release plugin passed all 5 host
probe tests again on both devices. The local signed artifact is
`releases/autojs6-plugin-compose-ui-v1.0.0-f7a437db.apk` (1,459,210 bytes). Build-7 debug APK is
9,223,166 bytes. JVM tests: 24 passed. Lint debug: no issues. Lint release: zero errors, three
unused identity-resource warnings (plugin_engine / plugin_id / plugin_variant, referenced by
contract tests). Native inventory, signature, CRC32 and 16 KB alignment gates passed.
Workflow structure and paths were reviewed locally; PyYAML is unavailable and remote CI was not run.

## Reproduction

Plugin (from this repository):

```powershell
py .python/generate_markdown.py --check
py .python/generate_launcher_icons.py --check
.\gradlew.bat '-Pautojs.gradle.build.number.auto.increment.enabled=false' '-Pautojs.gradle.build.time.update.enabled=false' :app:testDebugUnitTest :app:verifySharedClasspath :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease :app:appendDigestToReleasedFiles
py .python/verify_apk_classpath.py app/build/outputs/apk/debug/autojs6-plugin-compose-ui-v1.0.0.apk app/build/outputs/apk/release/autojs6-plugin-compose-ui-v1.0.0.apk
```

Host (from the isolated worktree):

```powershell
.\gradlew.bat '-PcomposeUiSpike=true' '-Pautojs.gradle.build.number.auto.increment.enabled=false' '-Pautojs.gradle.build.time.update.enabled=false' :plugin-api:compose-ui-api:assembleRelease :app:verifyComposeUiSharedClasspath :app:assembleAppDebug :app:assembleAppDebugAndroidTest
adb -s <serial> shell appops set org.autojs.autojs6.compose.spike SYSTEM_ALERT_WINDOW allow
adb -s <serial> shell am instrument -w -r -e class org.autojs.autojs.core.plugin.compose.ComposeUiSpikeDeviceTest org.autojs.autojs6.compose.spike.test/androidx.test.runner.AndroidJUnitRunner
```

Install the matching ABI host APK, the host test APK and one plugin variant first. Tests use their
own host preference space and restore the enable state. For cold PSS, force-stop only the dedicated
test host and append `#pssBeforeLoadAfterLoadAndAfterDestroy` to the test class name. Never replace
or clear a user's installed host to run this probe.

## P0.3 handoff and limits

Q1(b) was explicitly approved by the maintainer on 2026-10-02. P0.3 applies the verified versions
to all host variants and removes the temporary host Activity. The in-process parent-first design
is retained; the isolated-loader fallback is not enabled. See the P0.3 addendum below.
P1 must replace the draft API, pin a real minimum host build, negotiate the fingerprint, cache
loaders, handle package updates, and add host-release R8 rules for every shared class boundary.
The current spike is not a production error-containment or script-session implementation.

Not run in this P0.2 session: API 24/28/31/35 host loading matrix, a minified host APK, TalkBack
manual traversal, broad component/theme/IME coverage, LeakCanary and CI remote execution (D7).
These remain later gates; only the two devices above are used as new functional evidence.

Public references: [PathClassLoader constructors](https://developer.android.com/reference/dalvik/system/PathClassLoader)
and [Compose in Views lifecycle disposal](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views).
The results and R8 exceptions above are local observations, not conclusions inferred from those documents.

Local host commit: `21dff98f26` on `spike/compose-ui-p0`. The plugin change is the commit containing
this evidence (VERSION_BUILD=7). All four temporary packages were removed from both test devices;
the original host builds remain (Redmi 5310, AVD 5304). The AVD started by this session was stopped.


## P0.3 addendum: Q1(b) accepted and applied (2026-10-02)

The maintainer explicitly approved Q1(b). The host version catalog now declares lifecycle 2.9.4,
savedstate 1.3.2, emoji2 1.4.0 and window 1.5.0. They are ordinary implementation dependencies
for app/inrt debug/release, rather than debug-only additions. The 51-component canonical lock
fingerprint remains `f3042acc624d499feea9907a20257b62debaa6c523f40bfce19b084274334576`.
Canonical records are sorted by Maven coordinate (before `=`), not by the complete record string.

`app/compose-shared-classpath.gradle.kts` resolves all four runtime classpaths and rejects drift,
Compose implementations and the draft API in host APK dependencies. `ComposeUiSharedClasspathTest`
checks the resolved snapshots against the plugin fingerprint and guards removal of the temporary
host entry. No dependency on a sibling repository is introduced into either build.

The temporary `ComposeSpikeActivity` has been removed. The draft API is now an androidTestImplementation;
its loading helpers live only under androidTest. `ComposeSpikeSession` uses the existing host
AboutActivity for the counter, and closes on its real lifecycle. RawWindow continues to use the
host service. The test fixture refuses to alter enable preferences unless the target has the
explicit `.compose.spike` application ID. The `-PcomposeUiSpike=true` property only provides a
separate test installation; it no longer adds a development UI entry to the host.

The fixture uses an existing target Activity because synchronous instrumentation launch requires
an Activity in the instrumented process. See the [Android Instrumentation implementation](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/Instrumentation.java).
The test loader's parent supplies the draft contract from instrumentation and delegates shared
AndroidX/Kotlin types to the target. P0.2 remains the historical proof with a contract packaged in
the debug host; P1 must implement the frozen V1 contract in the production host.

D10 stays parent-first with an APK native path chosen from the process ABI. D11 preserves the
host theme, window and services, and delegates resources/assets/configuration to the plugin.
Floating windows must provide lifecycle/saved-state owners before attachment and destroy them
on close. The P0.2 R8 corrections remain required for the plugin. The V1 API draft omits diagnostic
classOrigins/probe/diagnostics methods, and no isolated-loader or process-external fallback is enabled.

| P0.3 validation | Result |
| --- | --- |
| Host shared runtime dependency graph | All 51 entries match in appDebug, appRelease, inrtDebug, inrtRelease |
| Host targeted JVM tests | 18 pass: ComposeUiSharedClasspathTest 3, PluginDefaultEnabledPolicyTest 7, PluginNativeAlignmentPolicyTest 2, ExplorerViewStateLifecyclePolicyTest 6 |
| Plugin JVM tests and shared classpath | 24 pass; both plugin classpaths still match the same fingerprint |
| Host APK assembly | app debug, debug instrumentation and release pass; release native alignment gate passes |
| Manifest / DEX boundary | All five appDebug and five appRelease merged manifests contain no temporary entry; arm64 debug/release DEX contain neither the P0 loader nor draft contract |
| API 24 x86, dedicated Compose_UI_P03_API24 AVD | 12/12 pass: loading probe 5, drawer lifecycle 3, plugin-center presentation 4 |
| API 33 arm64, Redmi 22120RN86C | The same 12/12 pass |
| Plugin docs / icons | 36 generated documents consistent; icon check passes |

Device runs use host build 5309 from the isolated branch and the unchanged signed plugin build 7
(`f7a437db`, release with R8). The plugin implementation and staged draft AAR have not changed in
P0.3; plugin build 8 records the documentation/lock-provenance commit and is not a new device-tested APK.
The host's existing release configuration has minification disabled, so a successful release build
must not be described as minified-host validation.

The first fresh API 24 run hit a runtime storage permission dialog: dumpsys showed GrantPermissionsActivity
as the resumed/focused Activity and both the host Activity and drawer remained STARTED. The API 33 first
run also had an immediate lifecycle assertion failure. Only the dedicated fixture package was granted
READ/WRITE_EXTERNAL_STORAGE on API 24 and POST_NOTIFICATIONS on API 33. The original drawer tests were
then restored without weaker assertions or extra waits, and all 12 cases passed on both devices.
IDE debugging remained unavailable for this worktree (outside-project path rejection); diagnosis used
the real foreground Activity/process state and instrumented assertions.

Host reproduction (after installing the matching ABI host, test APK and plugin):

```powershell
.\gradlew.bat '-PcomposeUiSpike=true' '-Pautojs.gradle.build.number.auto.increment.enabled=false' '-Pautojs.gradle.build.time.update.enabled=false' :app:verifyComposeUiSharedClasspath :app:testAppDebugUnitTest --tests org.autojs.autojs.core.plugin.compose.ComposeUiSharedClasspathTest --tests org.autojs.autojs.core.plugin.center.PluginDefaultEnabledPolicyTest --tests org.autojs.autojs.core.plugin.center.PluginNativeAlignmentPolicyTest --tests org.autojs.autojs.ui.explorer.ExplorerViewStateLifecyclePolicyTest :app:assembleAppDebug :app:assembleAppDebugAndroidTest :app:assembleAppRelease
# API 24 test fixture only:
adb -s <serial> shell pm grant org.autojs.autojs6.compose.spike android.permission.READ_EXTERNAL_STORAGE
adb -s <serial> shell pm grant org.autojs.autojs6.compose.spike android.permission.WRITE_EXTERNAL_STORAGE
# API 33 test fixture only:
adb -s <serial> shell pm grant org.autojs.autojs6.compose.spike android.permission.POST_NOTIFICATIONS
adb -s <serial> shell appops set org.autojs.autojs6.compose.spike SYSTEM_ALERT_WINDOW allow
adb -s <serial> shell am instrument -w -r -e class org.autojs.autojs.core.plugin.compose.ComposeUiSpikeDeviceTest,org.autojs.autojs.ui.main.drawer.DrawerLifecycleInstrumentationTest,org.autojs.autojs.core.plugin.center.PluginCenterPresentationDeviceTest org.autojs.autojs6.compose.spike.test/androidx.test.runner.AndroidJUnitRunner
```

Not rerun: the full host JVM/lint suites, inrt APK assembly/device execution, API 28/31/35/37 device
matrix, manual TalkBack/IME coverage, LeakCanary and remote CI. The present change is covered by the
four resolved dependency graphs, targeted host regression suites, app APK builds and API 24/33 runs;
the remaining compatibility matrix belongs to P5. Inrt release/debug dependency resolution is
verified, but that is not evidence of assembled or runnable inrt APKs.

P0 is complete. Continue at P1.1 to freeze the Compose-free V1 contract and stage its release AAR,
then implement the production loader/session in P1.2 and confirm the minimum host version in P1.3.
The provisional minimum 5308 and contract target 1 remain unchanged; the P0 draft version -1 is not
advertised as a delivered V1 or script API. All host changes remain on the isolated local branch;
the original host worktree's unrelated edits are preserved and no remote publishing occurs.


P0.3 cleanup: all three temporary packages were uninstalled successfully from both devices and
the dedicated API 24 emulator was stopped. The original running AVD was not stopped. Automatic
approval review rejected deletion of the newly created AVD cache with only `blocked by policy`;
its ignored local directory `build/compose-p03/avd-api24` and the `Compose_UI_P03_API24` registration
remain available locally. No source, test result or commit depends on deleting this cache.
