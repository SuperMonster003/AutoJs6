# Runtime speech recognition and model management

The speech batch adds all 18 registered speech/models method names to Node main threads, CJS/ESM/nested Workers and Bun. Recognition, VAD, external PCM sessions, microphone dictation, wake sessions, model management and synthesis use the existing 3-Macaw Speech implementation. This completes this named API surface; it does not establish complete Rhino compatibility or close the other specialized module audit items.

## Public contract and lifecycle

Node exposes speech and autojs6:speech modules, plus the explicit $autojs.speech member, under the speech capability. Bun exposes host.speech under a separate speech.control grant that defaults off. Native calls are asynchronous; session state, result and event subscriptions are local. Documentation, Node/Bun declarations and Ace runtime completions describe these distinctions without injecting Rhino speech globals into these runtimes.

PCM views retain their visible byte range. Chunked uploads become host-owned temporary WAV files; session audio is divided into Binder-sized messages. Model progress and session events return to the originating JS thread. Fragmented result transport preserves Unicode. Operations support cancellation and deadlines, including late initialization/results, and client/Worker/execution cleanup releases owned jobs, uploads and bindings. Wake rounds retain early events until listeners can attach; fallback round numbering also advances for explicit round events.

Bun speech streaming is exempt from the ordinary 1024-call lifetime quota in both dispatchers. Grant, request/result size, concurrency and per-call deadlines remain enforced. Native Bun payloads and the compatibility matrix are unchanged. No implicit model download or license acceptance is added. Models and defaults remain shared in the speech plugin's store; bundled VAD is not an ordinary installable/default-switch model.

## Independently signed applications

The first real generated application exposed a service binding failure: the original speech entry requires the host signature permission. The fix adds a separate org.autojs.plugin.speech.PACKAGED service protected by the provider-owned normal permission org.autojs.plugin.speech.permission.RUNTIME. The original signature entry remains protected.

The host selects the appropriate entry and APK preparation adds the new permission for declared Node speech or Bun speech.control. It does not automatically add RECORD_AUDIO. The packaged entry checks the caller's own RECORD_AUDIO grant for microphone/wake sessions, in addition to the provider's microphone grant. File/PCM, model and synthesis operations do not require caller microphone access. Asynchronous operation cancellation is restricted to the creating UID, including callback death and late operation creation. The AIDL version and transaction order remain unchanged.

The paired host is 6.8.0/build 5621 from be1b3eee0ab9817fff5ebbdffcada282330e9313. The Runtime Kit is autojs6-runtime-v6.8.0+5621-be1b3eee0ab9817fff5ebbdffcada282330e9313-arm64-v8a. Node is 1.25.0/build 274, Bun 0.14.0/build 274 and 3-Macaw Speech 1.1.0/build 69. The staged speech-api AAR SHA-256 is 18d98c5dd386de30a02c34cd80d44ef822abc5729b6570209ba55119c2990e1f. Final fixture, coverage and report changes after that host commit do not change the Runtime Kit implementation.

## Accepted physical validation

Redmi bek749scrwv4wo8h, model 22120RN86C, Android 13/API 33, arm64-v8a and 4096-byte pages:

| Suite | Cases | Result |
| --- | ---: | --- |
| RuntimeSpeechNativeTest | 2 | Pass |
| RuntimeSpeechDeviceTest | 5 | Pass |
| Node NpmEcosystemSmokeTest and NodeRuntimePluginAndroidConformanceTest | 34 | Pass |
| BunRuntimeInstrumentedTest | 13 | Pass |
| RuntimeSpeechPackagedDeviceTest.produce | 1 | Pass |
| RuntimeSpeechPackagedDeviceTest.installedApplicationsUseOwnContextBeforeAndAfterRelaunch | 1 | Pass |
| Total on Redmi | 56 | Pass |

Two independently signed APKs each pass an initial launch and force-stop/reopen: four launches and six program results, covering Bun twice and Node main plus Worker twice. Their DEX hashes match the paired Runtime Kit; their signatures differ from the host signature. The Bun package deliberately omits RECORD_AUDIO and asserts microphone/wake PERMISSION_DENIED while file/model operations succeed. The Node/Worker package declares and receives RECORD_AUDIO and successfully opens/stops both session types. Both report their own application Context and runtime build 5621.

Real programs recognize the 6.625-second LibriSpeech WAV and offset PCM views, check transcription content, VAD segments, external-session event ordering, cancellation and plugin error codes. Model checks import an owned management archive with progress, temporarily change and restore the ASR default, remove the imported model and verify installation of already installed model files. The archive is a management fixture, not an inference model. No new model download or license acceptance is claimed. Existing installed ASR, KWS and TTS models are reused.

Actual 3-Macaw synthesis produces valid RIFF/WAVE output. Playback requests use volume zero and media usage. No sustained tone is played, and silent completion is not acoustic quality acceptance. CJS/ESM/nested Worker lifecycle and more than 1024 actual Bun speech transport calls also pass. Final inventory matches the pre-test installed model IDs, versions, sizes, licenses and defaults, with no owned imported model left.

Before the Xiaomi installation conflict below, AceApiCoverageDeviceTest passed all 17 cases on Xiaomi 968e9f18, Android 15/API 35. The Ace artifact has not changed since that run. The saved proof confirms default preferences and rotation were restored. Four inspected screenshots show typed recognition results and final-event text completions for Node and Bun. These 17 Ace cases are counted separately from the 56 accepted Redmi cases, for 73 accepted physical cases.

## Xiaomi deferred by the user

During final Xiaomi validation, another session replaced the host APK. The expected 5621 SHA-256 was f129d449a6955de532352088179729ec28475038b8100ec445811d697bf308a3; the observed external 5620 SHA-256 was f7727e88b2b2a18976deed12569d20de384d9bcd4939af236fae1bfe5974233c. The 5621 Builder correctly refused that mismatched host.

The user confirmed the other session was using Xiaomi and requested Redmi first. Further Xiaomi host/packaged verification, device cleanup and restoration are therefore deferred. Earlier/final Xiaomi runtime logs are retained but are not counted as acceptance of the expected 5621 combination. Original APK/settings backups and the external-host hash record remain intact; no subsequent Xiaomi operation is performed. The completed Ace run is the only Xiaomi result included in the accepted physical total.

## Desktop and build checks

| Check | Result |
| --- | --- |
| Node facade/Worker suite | 77 passed, plus seven legacy standalone API programs passed |
| Bun host SDK suite | 68 passed |
| Host JVM | 4817 discovered, 26 skipped, no failures |
| Speech protocol JVM | 8 passed |
| Node/Bun/Ace/Builder/Offline/Speech provider JVM | 96/60/187/40/2/276 passed |
| Combined JVM | 5486 discovered, 5460 passed, 26 skipped |
| Runtime declarations | 75 files synchronized; TypeScript 5.1.3 and 6.0.3, no diagnostics |
| Ace runtime coverage | 743 queries passed; core completion coverage also passed |
| Augment documentation/declaration/completion gate | 1356 explicit exports, 1589 paths, no failures |
| Speech-specific gate | 18 methods, shared facade/types, provider routes, signed/packaged entries and both APK permission paths passed |
| Builds | Host/Node/Bun/Speech/Builder build and lint, Ace build/tests and Offline debug/release APK verification passed |

The augment totals describe explicit documentation/declaration/completion coverage, not full Node/Bun runtime parity. The new speech gate runs from .github/workflows/api-coverage.yml with an explicit Speech repository checkout/ref. Node native syntax was checked against its actual NDK compile command. Existing Python repository/generator checks pass. No new ABI, Android 9-12L or 16 KB device acceptance is claimed.

Documentation revision 152 is committed as 90ca57c07495a459db49b744126664a9ecc2d525. Declaration version is 4.72.0, Ace 2.34.0/build 313, Builder 1.2.19/repository build 94 with HOST_VERSION_BUILD 5621, and Offline Docs 6.8.32/build 189. Offline provenance pins that exact Documentation commit; both APK variants verify 224 documentation files with canonical content SHA-256 3a614c029773036bde0f0c9a8a56bf0d325d9805df2bfc6ab33a7966a8252663.

## Corrections found during validation

- The explicit JVM route-count expectation changed from 150 to 158 for the eight new speech transport routes. The full rerun passes.
- Initial integrated fixtures incorrectly treated bundled VAD as an ordinary model and omitted custom-import languages. Fixtures and cleanup ordering were corrected; failed attempts remain separate from accepted evidence.
- The original signature-only service blocked generated applications. The separate permissioned entry and caller checks above resolve the actual product failure.
- The first Node packaged fixture embedded the WAV twice, producing 719208 source bytes and exceeding the existing 524288-byte source limit. The fixture now packages the audio asset once and passes bytes through workerData, with an explicit source-size assertion. The runtime limit is unchanged.
- A Xiaomi native-test startup interruption coincided with instrumentation stopping a still-starting SplashActivity; no application fatal stack was found. The driver now waits for launch completion. This is not claimed as a production startup fix.
- Offline provenance was found to retain an earlier documentation base commit after asset synchronization. Both provenance copies and generated README references now pin the final documentation commit, and APK verification was repeated.

## Restoration and retained evidence

Redmi's six original APKs were restored and their SHA-256 hashes verified. Task-only test/generated packages and owned external fixtures were removed; the prior SDK-file state was restored. Host/provider microphone grants and flags, camera/notification grants, voice assistant/recognition settings, volume, rotation, screen settings and notification-listener access match the baseline. The originally enabled host accessibility service is bound. Installed model inventory/defaults were verified before restoring the older host.

The speech provider was restored without uninstalling or clearing its model store: a same-version, same-signature debug APK permitted the data-preserving downgrade from tested release build 69 to original release build 52. This temporary debug APK is a restoration tool, not an accepted test artifact. Original Redmi backups were removed only after full restoration verification. Xiaomi backups remain because its restoration is deferred.

Local evidence is under .bak/runtime-speech-20261020: evidence.json, certificates.json, junit-totals.json and archived XML, artifact-bound receipts, failed and accepted logs, Ace screenshots, model inventories and per-device restoration records. The temporary host worktree and owned Worker fixture directories are cleaned or archived under the owning repository's ignored .bak before handoff. Existing user release drafts are preserved. Changes are local commits; nothing is pushed, tagged or publicly released.
