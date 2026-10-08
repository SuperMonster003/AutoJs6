# P1.1 contract freeze evidence

Date: 2026-10-02. Plugin baseline `f74a899` (build 8), host isolated branch baseline `fb784f034a`
(build 5309). The original host worktree contains other ongoing work and was not edited or staged.
The maintainer confirmed manual cleanup of the P0.3 AVD; both its registration and cache path were
absent at session start. No new AVD was created in this session.

## Delivered boundary

V1 consists of root identity/capability/error/limit/vocabulary objects, the loading interfaces,
immutable Parcelable model classes and ComponentCatalog.V1. The host packages the API module;
the plugin consumes its staged release AAR with compileOnly and references its inlined identity
constants. No Compose dependency is added to the host/API.

The reviewed snapshot has 747 records covering public literals, enums, loading method signatures,
model constructors/getters and catalog records. The catalog has 30 entries (29 mountable node
types and command-only Snackbar), 20 modifier words and 8 ordered shortcut mappings. TopAppBar
from P2.5 is now included in the A.5 list; the selection/input count is corrected from 7 to 6.

The ambiguous draft subtree field is now UiTree(rootId, nodes). It validates closure, ownership,
cycles/orphans, sibling keys, depth and callback budgets. Collections are detached and unmodifiable;
events copy scalar-only Bundles in both directions. BitmapRef uses weak process-local handles,
retains no ownership and does not serialize pixels. Foreign process handles are rejected. The
future process-external fallback must add image transport explicitly.

The complete semantics, additional container/Parcel bounds, patch replacement rules, generation
policy, editing sequence and capability types are in `compose-ui-plugin-protocol-v1.md`.

The old `api.spike` version -1 interfaces are retained in a separate namespace for P0 regression
until the P2.1 renderer migration. They are outside the frozen V1 surface. The existing plugin
factory remains a prototype; it does not advertise a delivered V1 renderer or script API. The
production host loader and final minimum host version remain P1.2/P1.3 work.

## Artifact provenance

Release task: `:plugin-api:compose-ui-api:assembleRelease` in host branch `spike/compose-ui-p0`.

- Staged file: `libs/compose-ui-api.aar`, 254415 bytes.
- SHA-256: `e6024147dd45776e1f0bc178da66a1a337e9d084cbe3dbcf244291e20857ba21`.
- License: MPL 2.0; API dependency common-plugin-api, no Compose implementation dependency.
- Bytecode audit: 204 class files, no native entries, no Compose class definitions or type descriptors.
- Both repositories retain the same 51 shared dependency versions and fingerprint
  `f3042acc624d499feea9907a20257b62debaa6c523f40bfce19b084274334576`.
- The API source and staged digest are recorded together with the local commits for this milestone.

## Verification

| Check | Result |
| --- | --- |
| API JVM suite | 13 tests pass: contract snapshot/version/errors, catalog/default/alias/scope consistency, model immutability/tree/limit validation and host source guard |
| Host shared classpath JVM suite | 3 tests pass; V1 API present in appDebug/appRelease/inrtDebug/inrtRelease dependency graphs, no Compose implementation |
| Plugin JVM suite | 24 tests pass, including identity alignment and both AAR digests |
| API release AAR / Android test APK | Assemble successfully |
| Host debug / Android test APK | Assemble successfully; debug native alignment gate passes |
| Plugin debug / Android test / release | Assemble successfully; shared classpath and DEX boundary checks pass |
| Plugin lint | Debug: no issues; release: 0 errors, 3 existing unused identity-resource warnings |
| API Parcelable device tests | API 24 x86 AVD (emulator-5554): 6/6; Redmi 22120RN86C API 33 (bek749scrwv4wo8h): 6/6 |
| Host loading/INFO regression | Redmi API 33, host 5309 + signed release plugin 9: 6/6 |
| Plugin activation/discovery regression | Redmi API 33, matching debug plugin/test APKs: 5/5 |
| Docs/icons | 36 generated documents consistent; icon check passes |
| Signed local plugin gate | `autojs6-plugin-compose-ui-v1.0.0-fb94ae8d.apk`, 1460194 bytes, CRC32/signature/native inventory/16 KB alignment pass |

Parcelable tests exercise all value/patch/command variants and fixed tags, nested frames, event
Bundle detachment, the byte budget, bad headers/counts/Boolean tags, borrowed bitmap identity,
recycle behavior and rejection of a foreign process nonce. JVM boundary cases include 64-level
trees/values, 5000 nodes, 2000 patches, ordinary/lazy child limits, modifier and callback limits,
finite float dimensions, duplicate keys, selection offsets and 32-million-pixel arithmetic.

The standalone INFO check binds from the host while the plugin itself is not instrumented. It
confirms that the moved identity constants remain inlined and getInfo does not need host-provided
V1 classes in the plugin process. INFO capabilities still contain only requiresHostVersion.

Plugin debug APK: 9224206 bytes / 88417 DEX method references. Release APK: 1460194 bytes / 28213
method references. The API classes remain absent from both plugin APKs. Measurements are build 9;
the renderer is still the retained P0 implementation, not the future complete V1 renderer.

## Reproduction and handoff

Host contract:

```powershell
.\gradlew.bat '-Pautojs.gradle.build.number.auto.increment.enabled=false' '-Pautojs.gradle.build.time.update.enabled=false' :plugin-api:compose-ui-api:testDebugUnitTest :plugin-api:compose-ui-api:assembleRelease :plugin-api:compose-ui-api:assembleDebugAndroidTest
adb -s <serial> install -r plugin-api/compose-ui-api/build/outputs/apk/androidTest/debug/compose-ui-api-debug-androidTest.apk
adb -s <serial> shell am instrument -w -r -e class org.autojs.plugin.compose.api.ComposeUiParcelTest org.autojs.plugin.compose.api.test/androidx.test.runner.AndroidJUnitRunner
```

Host integration uses the existing isolated `-PcomposeUiSpike=true` package and
`:app:testAppDebugUnitTest --tests org.autojs.autojs.core.plugin.compose.ComposeUiSharedClasspathTest`,
`:app:assembleAppDebug`, `:app:assembleAppDebugAndroidTest`, and ComposeUiSpikeDeviceTest.
Plugin verification uses its normal JVM/build/lint/distribution gates and
`.python/verify_apk_classpath.py`. No external app, release/index or remote Git write was performed.

Not executed here: full host JVM/lint suites, host release/inrt APK builds or device execution,
the wider device matrix, production V1 rendering, IME/TalkBack/performance acceptance, or remote CI.
This milestone freezes and tests the contract; those integration/rendering gates belong to later
stages. The four dependency graphs are verified, but that is not an assembled inrt APK claim.

Next: P1.2 production loader, selection/cache invalidation, session/transaction/callback scheduling
and theme bridge. Keep the V1 snapshot under review, retain the P0 fixture only until P2.1, and
back-fill the currently provisional host minimum 5308 in P1.3.
