# Runtime Java/Android object interop, 2026-10-20

This batch replaces the old nine-class Node host allowlist with explicit public Java member access, adds the same facade to Bun and Node Workers, and synchronizes documentation, TypeScript declarations and Ace. It is an implementation and device-validation checkpoint, not a claim of complete Rhino parity or a public release.

## Delivered contract

Node main threads and CJS/ESM/nested Workers use java or autojs6:java. Bun uses host.java with the independent default-off java.interop grant; Node projects declare java_interop. Calls execute in the host application's process, or in the generated application's own process after packaging. Android permissions, UID, SELinux and platform reflection restrictions still apply. The broad Java grant is separate from the narrower android.objects helpers and can call public Android APIs without routing through those helpers.

The shared facade supports class descriptors and actual member/signature inspection; public constructors; static and instance calls; field reads/writes; exact overload parameterTypes and typed null; application Context; class-name/instance checks; primitive, reference and nested arrays; and explicit reference release. Mutable CharSequence implementations remain live handles. Objects returned through arrays become callable handles; cyclic arrays retain references without recursive serialization failure. Main-Looper operations use the per-call thread option.

Safe integer longs remain numbers and larger longs become bigint. Bigint arguments and explicit decimal long strings retain all 64 bits. Fractional values are not silently truncated into int. Ambiguous overloads reject instead of depending on reflection order. Public methods of private implementation classes are resolved through accessible public interfaces/base declarations. NaN and infinity survive the JSON transport through explicit markers.

Handles belong to their execution. Worker exit releases its returned references while preserving parent handles. JavaScript cancellation keeps the transport reply alive so late returned objects can be released; the regression first reproduced a lost late Context reference and then verified cleanup. Bun's cancelled automation jobs and provider destruction also release references. release drops a bridge reference only, and cannot close another resource kind. Native streams and other resources still require explicit close/recycle calls.

Node ESM generation now uses local bindings and export aliases for reserved names such as new. The previous export-const-new form caused syntax errors, including in Worker imports. Existing empty-class validation retains its Java class error code, and the legacy generic AutoJsJavaObjectHandle field type remains supported.

JavaScript callback/subclass generation, dedicated loadDex/loadJar convenience APIs and synchronous Rhino package globals remain unsupported. Arrays returned by methods are snapshots unless retained through an explicitly created Java array handle. A cancelled wait does not promise to roll back or forcibly interrupt an already entered Java method. Bun retains its existing JSON/call/concurrency budgets; its native binaries and frozen compatibility matrix are unchanged.

## Physical and packaged verification

The accepted final logs contain 136 JUnit cases:

| Device | Platform | Accepted cases |
| --- | --- | --- |
| Redmi 22120RN86C, bek749scrwv4wo8h | Android 13/API 33, arm64, 4096-byte pages | Native bridge 3, shared runtime 4, existing Java regression 5, Node conformance/npm 34, Bun runtime 13, APK production 1, packaged execution 1 |
| Xiaomi 23046RP50C, 968e9f18 | Android 15/API 35, arm64, 4096-byte pages | The same 61 cases, plus all 14 actual Ace popup/default/JSOX cases |

The actual runtime programs exercised Rect field mutation/copying, StringBuilder, Bundle and Intent object graphs, ArrayList and an unmodifiable collection, primitive/reference/nested/cyclic arrays, exact Long.MAX_VALUE, NaN/infinity, exception causes, final-field rejection, Context/File chains and main-thread TextView calls. Main-thread ESM, CJS/ESM/nested Worker cleanup and parent-handle preservation were checked separately. Native tests cover explicit disable, execution isolation, provider destruction, Bun's default-off grant and cancelled main-thread object creation.

Each phone built a Bun and a Node/Worker APK through the genuine paired Kit and APK Builder. Four generated APKs completed eight launches across initial execution and force-stop/reopen. Twelve packaged runtime program results cover Bun, Node main-thread and Worker calls. Every result reports its own application package and private cache path, and runtime build 5591. APK signatures differ from the host signature; all generated runtime DEX files exactly match the Kit template. The Bun SDK copied into each project is checked byte-for-byte against the installed runtime plugin.

One Redmi packaged attempt timed out waiting for the initial input view. A subsequent explicit launch showed the expected view. After placing the host in the foreground, the complete check passed, and a separate uninstall/reinstall of only the two owned fixture packages passed both initial and reopened rounds again. The first timeout is retained as a failed attempt; its underlying cause was not established, and it is not counted as a pass. No blanket OEM first-launch guarantee is claimed.

No acoustic stimulus, recording tone, vibration or intentional audio playback was used. Original music volume was zero on both devices and was preserved.

## Local checks and retained failures

- JVM: 5120 discovered, 5094 passed, 26 existing skips, zero failures/errors across the host and five plugin repositories.
- Node: 55 facade/Worker unit cases and all standalone API/Worker regression programs pass. Bun: 62 SDK cases pass. Python: 2 Node, 38 Bun and 8 API-extractor cases pass.
- TypeScript 5.1.3 and 6.0.3 report zero diagnostics across 73 synchronized runtime declaration files. The legacy generic field compatibility smoke also passes.
- Ace: 556 runtime queries and 268 core/default/JSOX queries pass. All 14 real-device popup cases pass; Node and Bun newArray signatures were visually inspected. A final trailing-blank-line normalization changes no declaration semantics and was followed by synchronization, regeneration and type/completion checks.
- The focused gate matches 19 native Java routes with Bun's transport and shared facade/type sources. Its 25 facade-member count includes metadata and the explicitly unsupported defineClass entry, not 25 independent implemented operations.
- The host documentation/type/Ace gate has zero failures across 1356 explicit exports and 1589 paths. Those counts describe the existing extractor scope, not all Java classes, overloads or full runtime parity.
- Current NDK syntax, Android and androidTest assembly, relevant JVM checks, Node/Bun/Ace lint, Bun APK runtime integrity, Node example/TypeScript sample checks, genuine Kit/Builder assembly and repository checks pass. Documentation generation verifies 164 modules; Offline Docs verifies 220 files in debug and release APKs.

Retained attempts include Kotlin numeric-conversion compilation diagnostics, a duplicate ESM fixture variable, obsolete allowlist/error expectations, a mixed standalone/node:test invocation that inherited invalid Worker execArgv, initial Ace test-query typing mistakes, and a Documentation JSON date field rejected by its schema. These were corrected without relaxing the functional assertions. An APK-production attempt correctly rejected a newly edited SDK against an older installed Bun APK; final production uses matching bytes. An initial ADB backup failed before any device mutation and was repeated successfully. Final APK receipts and archived logs identify the exact accepted artifacts.

## Versions and provenance

Host 6.8.0/build 5591; Node Runtime 1.22.0/build 270; Bun Runtime 0.11.0/build 270; declarations 4.69.0; Ace 2.31.0/build 309; Offline Docs 6.8.28/build 185; APK Builder 1.2.16/repository build 91; Documentation revision 148.

The Kit is autojs6-runtime-v6.8.0+5591-4f770c4a18bf0831a1bbe0a1d08a4de8c638e8fa-arm64-v8a. Its source basis is host 4f770c4a18bf0831a1bbe0a1d08a4de8c638e8fa plus this working-tree batch; that identifier is not a final content commit. Exact APK and DEX hashes are recorded in the local receipts. Documentation commit ffc49a0819c3a5f1bc4b6034126cbbc091d98152 is pinned in both Offline Docs provenance files.

Evidence, source baselines, original failed attempts and artifact receipts are under ignored .bak/runtime-java-interop-20261020. Eight repositories are committed locally, with no push, tag, catalog update or public release. The host's pre-existing Simplified Chinese release-note edits are preserved through semantic staging.

## Reproduction and restoration

Use tools/api-coverage/check-runtime-java-interop.mjs, Node test-java-interop.js/test-worker-java-interop.js, Bun java-interop.test.mjs, runtime declaration smokes and Ace runtime/default coverage. The physical suites are RuntimeJavaInteropNativeTest, RuntimeJavaInteropDeviceTest, NodeJavaInteropBridgeInstrumentationTest and RuntimeJavaInteropPackagedDeviceTest. The packaged fixture requires an installed genuine paired Builder and matching Bun SDK asset.

Final restoration receipts verify both phones' original package hashes, camera/audio/notification permission flags, notification-listener and accessibility settings, brightness/mode/timeout, modify-settings and exact-alarm app-ops, three volume streams and rotation. Only the four generated fixture installations and task-created test packages are removed; pre-existing test packages are restored. Owned host proof/APK/SDK files and Java popup evidence are cleaned after archival. All 13 original APK backups were deleted only after exact hash restoration. Nine intermediate APK copies were also removed while retaining their recorded hashes and failed-attempt logs. Seven desktop Worker fixture directories created by this batch are archived under the Node repository's ignored .bak directory; the pre-existing worker-host-tests directory is preserved.
