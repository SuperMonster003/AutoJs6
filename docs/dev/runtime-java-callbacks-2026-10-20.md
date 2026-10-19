# Java callbacks, subclasses and class loading, 2026-10-20

This batch implements Java callbacks, generated subclasses and loadJar/loadDex for Node.js main threads, Bun and CJS/ESM/nested Workers. Documentation, declarations, Ace and coverage checks are synchronized. It also fixes a packaged Bun startup failure reproduced under memory pressure.

## Delivered behavior

The explicit java/autojs6:java module and Bun host.java now provide proxy, wrap, defineClass, loadJar, loadDex, retain and closeCallbacks. Generated classes expose ordinary constructor/static operations and release/dispose. The $autojs.java facade forwards these operations while preserving class/loader descriptors. Node uses java_interop; Bun continues to use the independent default-off java.interop grant, whose settings summary now includes callbacks and code loading.

Proxy objects are generated implementations of public interfaces, not java.lang.reflect.Proxy instances. Public, non-final superclasses and public/protected constructors and methods are supported. Required abstract implementations are checked, including interfaces inherited through a superclass. Unmodified default methods remain native. Callback method names or full parameter signatures select overrides. Class names accept Java identifiers, including Unicode and the default package. Parent constructors can call an override because the generated handler field is assigned before super(). Five JVM bytecode tests exercise these contracts before Android DEX conversion.

Callback functions execute in the JS runtime/Worker that registered them, may return Promises and receive typed Java values. Native calls leave the oneway Binder dispatcher immediately, so callback traffic can proceed. AsyncLocalStorage retains independent callback contexts; a waiting Java thread processes reentrant Java operations, including on the main Looper. Concrete superclass calls, nested callbacks and primitive/reference return conversion are supported. Return values are converted and retained natively before borrowed argument references are released.

Object and array arguments are borrowed handles. retain keeps a separate reference, and array mutation acts on the original Java array. Registration disposal, Worker exit and execution teardown release callbacks and unblock native waiters. In-flight reentry counts toward code-cache lifetime even when Android initiated the callback outside a bridge request. A focused native regression proves that close does not delete code while such an operation is still running. Direct Java callers receive callback failures; unsolicited Android callbacks log failures and return native defaults instead of throwing JS errors onto event threads.

JAR conversion uses the existing local ClassFileDexer. DEX input validation rejects non-code containers; code is copied to a unique private directory, made read-only before writing and loaded through an execution-local parent-first class-loader chain. Inputs are not modified. Loaded classes do not cross executions, and owned code files are removed after in-flight work finishes. Related dependencies should be passed together. Loading does not deploy Android resources or JNI libraries, hot-replace loaded classes or promise JVM class unloading.

Snapshots use identity text instead of implicitly invoking arbitrary toString/hashCode methods. The new JS regression proves that serialization invokes neither override, while explicit toString -> callSuper -> hashCode performs nested callbacks successfully in Node, Bun and Worker. Existing Rect/Locale snapshot assertions were updated to this documented behavior; explicit native toString behavior remains checked.

## Packaged startup fix

The first Redmi packaged Bun attempt failed before the script created its input view. Retained Android events show SplashActivity finishing at 03:07:58, followed by LMKD killing the packaged host at 03:08:00 with oom_score_adj 900. The process had become cached during runtime startup. This supplies a concrete cause for the similar first-launch symptom retained by the previous batch.

AssetsProjectLauncher now applies the existing 12-second Node startup/toast visibility window to Bun as well. The fixed cold-launch trace shows RuntimeInteropActivity appearing at 03:26:30 before SplashActivity closes at 03:26:40. No new foreground-service permission, background exemption or permanent visibility setting is introduced. This is a bounded startup safeguard, not a guarantee of indefinite background residency or survival under all memory pressure.

Both generated fixture applications were uninstalled and freshly installed for final verification. A separate ADB-server interruption broke both observation connections during an intermediate run; incomplete output was not counted as success. Complete accepted runs were collected again after reconnecting.

## Verification

Final accepted physical JUnit count: 149.

| Device | Platform | Accepted checks |
| --- | --- | --- |
| Redmi 22120RN86C, bek749scrwv4wo8h | Android 13/API 33, arm64, 4096-byte pages | Native callback/lifetime 4, shared runtime/Worker 5, prior object regression 4, Java transport regression 5, Node/npm 34, Bun runtime 13, APK production 1, packaged execution 1 |
| Xiaomi 23046RP50C, 968e9f18 | Android 15/API 35, arm64, 4096-byte pages | The same 67 cases plus all 15 Ace popup/default/JSOX cases |

Actual programs test Runnable/Comparator/Callable and loaded interfaces, async and concurrent replies, constructor callbacks on the main thread, protected overrides, callSuper, native-thread callbacks, long precision, returned and retained objects, original-array mutation and Android View.OnClickListener reentry. Click sound effects and haptic feedback are explicitly disabled. Error hooks, deadlines, late replies, owner termination, parent callback preservation, read-only code and execution isolation are checked separately.

Four APKs were generated through the real paired Runtime Kit and APK Builder. Eight launches include force-stop/reopen rounds, with twelve runtime-program results covering Bun, Node main-thread and Worker execution. Every result reports the generated application's own package and private code cache, runtime build 5592, working callbacks and actual JAR/DEX execution. Generated APK signatures differ from the host's; runtime DEX files exactly match the Kit template. The SDK copied into Bun projects matches the installed plugin bytes.

- Full JVM run: 5125 discovered, 5099 passed, 26 existing skips, no failures/errors. The full-run totals were observed before a concurrent augment task replaced shared host XML output. Successful build logs and those observed totals are retained; plugin XML was archived. The 11 owned host tests were also rerun successfully into a separate archived report directory.
- Node: 61 unit cases plus the standalone API/Worker programs pass. Bun: 64 SDK cases pass. Python: 2 Node, 38 Bun and 8 API-extractor cases pass.
- TypeScript 5.1.3 and 6.0.3 report zero diagnostics across 73 synchronized files. Contextual this is typed with CallbackContext, including receiver and generic callSuper; obsolete never declarations in the example fixture now require Promise results.
- Ace: 600 runtime queries and 268 core/default/JSOX queries pass. All 15 physical popup cases pass. GeneratedClass and callSuper signatures are visually inspected in both runtime modes.
- The focused gate checks 28 native Java routes and 31 shared facade members plus class generation, local dex conversion, reentry and cleanup wiring. These are finite interface-scope counts, not every Java class/overload or complete Rhino parity.
- The existing host gate reports zero failures for 1356 explicit exports and 1589 paths. Relevant assembly, JVM checks, Node/Bun/Ace lint, NDK syntax, Bun runtime integrity, example/type compilation and repository checks pass.
- Documentation verifies 164 modules; Offline Docs verifies 220 files in debug/release APKs. A committed-source snapshot reproduces the complete documentation independently of concurrently edited speech.md.

Retained failures include the initial implicit Kotlin type cycle, missing future import, ConcurrentHashMap contains ambiguity, the machine's older javac on PATH, stale never declarations, missing contextual this typing, and Rect/Locale assertions expecting eager toString snapshots. None were accepted by weakening callback or loading behavior. The startup process death and ADB interruption are retained separately.

## Versions, limits and source basis

Host 6.8.0/build 5592; Node Runtime 1.23.0/build 271; Bun Runtime 0.12.0/build 271; declarations 4.70.0; Ace 2.32.0/build 310; Offline Docs 6.8.29/build 186; Builder 1.2.17/repository build 92; Documentation revision 149.

Kit: autojs6-runtime-v6.8.0+5592-bd9b13363d139ff8ea3a054d3207a3968cde356e-arm64-v8a. This identifies the base checkout plus this working-tree batch and shared-checkout work, not a final content commit. Exact APK/template/DEX receipts identify tested artifacts. Documentation source 20bc81da984d8559d8a91e2fff0111bd3a51637f is pinned in both Offline Docs provenance files. Local commits are recorded in .bak/runtime-java-callbacks-20261020/commits.json; nothing is pushed, tagged or publicly released.

Callbacks remain asynchronous IPC even when Java waits synchronously for a result. A callback's Java calls should use its owning facade and be awaited. General UI/client calls are outside Java's reentry lane. Timeout/cancellation does not forcibly interrupt arbitrary Java or already running JavaScript. Listeners still need native detachment. Bun retains its 1024-call, four-request and JSON budgets, and its native payload/compatibility matrix is unchanged. Synchronous Rhino package globals, automatic conversion of arbitrary function arguments, AAR resources and JNI deployment remain outside this batch.

The generated constructor's field assignment follows the [JVM initialization rule](https://cr.openjdk.org/~dlsmith/8302497/8302497-20231218/specs/invokespecial-verification-jvms.html), and read-only loading follows the [Android 14 dynamic-code requirement](https://developer.android.com/about/versions/14/behavior-changes-14). Both are additionally exercised on the actual devices.

## Restoration and reproduction

Both phones' original APK hashes, camera/audio/notification flags, listener/accessibility settings, brightness/mode/timeout, modify-settings/exact-alarm app-ops, three volume streams and rotation are verified after restoration. Original music volume was zero. No acoustic stimulus or sustained tone was used. The four generated fixture installations, newly installed test packages and owned SDK/JAR/DEX/APK/proof files are removed; pre-existing test packages are restored. All 13 original APK backups are deleted only after matching restored hashes.

Redmi acquired two additional accessibility services outside this batch (accessibilitycompat and three.adapt.a11y) during concurrent work. Finalization preserves those additions and verifies that all originally enabled services remain enabled and the restored AutoJs6 service is bound. Xiaomi's originally disabled host service remains disabled. The exact before/after service sets are retained in the restoration receipts.

8 desktop Worker fixture directories are archived under the Node repository's ignored .bak directory. Pre-existing worker-host-tests is preserved. Intermediate APK copies are pruned after recording their hashes. Committed-documentation validation and evidence stay under ignored .bak/runtime-java-callbacks-20261020. Existing Simplified Chinese release drafts and concurrent speech work are excluded from this batch's staging. The independent augment safety-net work was committed as a03d2db37 during this task; its version.properties update already carries build 3940 and is preserved.

Use check-runtime-java-callbacks.mjs, test-java-callbacks.js/test-worker-java-callbacks.js, java-callbacks.test.mjs and runtime declaration/Ace checks. build-java-callback-fixtures.py builds owned JAR/DEX inputs with the installed JDK/SDK. Physical suites are RuntimeJavaCallbacksNativeTest, RuntimeJavaCallbacksDeviceTest and RuntimeJavaCallbacksPackagedDeviceTest, with the prior Java regressions retained.
