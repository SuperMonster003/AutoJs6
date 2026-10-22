# Runtime AI core coverage, 2026-10-22

Implementation, documentation/types/completion and local checks are complete. Xiaomi has completed final AI 299 host, Ace and independently signed application acceptance, followed by restoration to its fresh snapshot. Redmi retains its earlier accepted AI 298 cohort; its AI 299 follow-up is deferred after an external host replacement. At the user's explicit request, Redmi's current state is preserved without further testing, cleanup or restoration. Complete two-device acceptance of AI 299 is not claimed. No push, tag or release.

## Scope and behavior

This batch implements the five registered AI core root names `ask`, `chat`, `stream`, `session` and `catalog` for Node.js main threads, CommonJS/ESM/nested Workers and Bun. The Node facade is callable, with `ai.ask === ai`, and is available as `ai`, `autojs6:ai` and explicit main-thread `$autojs.ai`. Bun uses `host.ai` behind the separate, default-off `ai.control` grant. Session `ask/chat/stream/close` and runtime `hasAccess/requestAccess/close` complete the portable core contract. `ai.agent` is deliberately outside this batch.

Inputs support JSON prompt, message, request, session and catalog forms through the existing host AI mapper and native provider runners. Streams expose `ready` and `result` Promises, typed open/chunk/delta/usage/done/error/cancelled events and cancellation. Bounded UTF-8 fragments preserve Unicode across bridge packets. Timeouts, late results, queue budgets, scoped sessions and Worker exit cleanup are covered. Terminal native resources remain owned until JavaScript acknowledges their result; aborting before acknowledgment can still close a just-created persistent session. Closing a session terminates its active turn.

Bun AI streaming is exempt from the ordinary 1024 cumulative bridge-call budget. Authorization, payload, concurrency and individual call limits remain. Bun native binaries and the published compatibility matrix are unchanged.

## Independently signed applications

The original host uses the existing signature-protected provider entry. Generated applications declare the normal `org.autojs.permission.AI_RUNTIME` entry permission and must additionally receive explicit per-application consent inside 3-Stone AI. The permission declaration alone grants no model/account access. The status provider returns only the caller's own authorization bit and offers no public grant operation. The approval Activity derives the requesting application from Android's result caller, rather than caller-controlled extras.

Consent is bound to package name, UID, first-install time and current signing certificate hashes. Same-install updates retain it; reinstall, UID or signer changes cannot inherit it. The plugin settings page lists authorized applications and can revoke them. Revocation interrupts active runtime sessions, and subsequent calls are denied. The original and runtime services share admission limits, while cancellation and cleanup remain owner-scoped. An unavailable or disabled approval Activity safely declines the request.

Actual Redmi tests rejected the first custom dangerous-permission approach: MIUI closed its system dialog without granting access in both grouped and ungrouped candidates. Those results are excluded. The final design uses provider-owned explicit consent and does not depend on a `pm grant` test shortcut. A separate packaging defect that stripped the AI entry permission was corrected in both Node and Bun capability preparation.

Xiaomi testing exposed two distinct lifecycle defects. AI 298 corrected a service-destruction cleanup gap: a session could mark cleanup as scheduled, then executor shutdown could discard that queued action. Destruction now takes over pending cleanup exactly once, and admission remains occupied until cleanup actually finishes. Four deterministic JVM regressions cover discarded work, late work, concurrent destruction and rejected scheduling.

After manual unlock, stage-labelled tests and temporary native lifecycle tracing established the remaining failure. Cancellation and callback sealing completed promptly, but native conversation/engine cleanup continued for approximately 8-14 seconds. The next sequential chat reached the single-session limit before that cleanup released it and returned `PROVIDER_FAILED`. AI 299 permits at most one successor to wait behind a session that is already closing. The successor starts only after actual native resource release; its timeout, cancellation and owner-death handling remain active while waiting. Active sessions still reject concurrent inference, and cancelling a waiter does not release the previous session's resource barrier. This adds no inference retry or overlapping model execution. Five JVM regressions cover admission, deferred start, cancellation/timeout, release before readiness and stale release. Temporary diagnostic logging was removed from the final APK.

## Source and artifact identity

The host worktree starts at `cfc55eef63111b44fb64761902ac4a402a65abb4`. Mutable primary files and later report commits are not substituted for the tested APKs. Full hashes, signer digests, source identities and raw output are retained under `AutoJs6/.bak/runtime-ai-core-20261021`.

| Component | Version | Tested source |
|---|---|---|
| Host and Runtime Kit | 6.8.0 / 5835 | `43da3f16422fc474237da40df92c4afcf720d50d` |
| Node.js Runtime | 1.31.0 / 291 | `42b2589504b5ed2d20e329dc86973bb1a1b76052` |
| Bun Runtime | 0.20.0 / 293 | `68eaebf8042b93d5812e424016f6d71f2075bf17` |
| 3-Stone AI, final Xiaomi cohort | 1.5.0 / 299 | `687d52c3d7ffe1f28678ef4f8f746ed2cdce1510` |
| Documentation | revision 163 | `ed35b12ef21fee08f28cbde864e51d4cf5c28eb6` |
| TypeScript Declarations | 4.79.0 | `b01cbd5849cfd98d2fed94a751f90fd4baa5bfe8` |
| Ace Editor | 2.41.0 / 326 | `83c5b1918a07ef7d6870e3c2ecdfaa5097adecdb` |
| Offline Docs | 6.8.39 / 200 | `46b80cb63563d000d7c20bd26fa8d9df4fafc8bf` |
| APK Builder Template | 1.2.25 / repository 110 / Android 583501 | `3e439bdf572f27ef1640b1acea8019be00a832d9` |

Redmi's earlier accepted AI 298 cohort uses `b52cae72e13d8c111ff6b713c5f78fc065e3abfb`; it is not evidence for the AI 299 admission fix. That fix changes provider scheduling without changing the public API, so Documentation 163, declarations 4.79.0 and Ace 326 remain synchronized. Provider release notes and generated README/changelog files are updated in all ten languages.

The retained arm64 kit identity is `autojs6-runtime-v6.8.0+5835-43da3f16422fc474237da40df92c4afcf720d50d-arm64-v8a`. Its directory is `AutoJs6-Plugin-APK-Builder-Template/.bak/runtime-ai-core-20261021/kit-5835`. Earlier 5832/5833 candidates and the 5834 consent verification remain separate evidence, not the final kit.

## Local verification

The final host JVM suite passes 5,232 tests with 26 skipped and no failures. Plugin JVM suites pass Node 96, Bun 60, AI 507, Ace 187, Offline 2 and Builder 40. This is 6,124 passed and 26 skipped in total. The host's 45 legacy differential oracle files were staged only for JVM execution and archived before APK assembly.

Node local tests pass 143 modern cases and seven legacy standalone programs; Bun SDK tests pass 83. The AI/Worker focused reruns and actual NDK compiler syntax check pass after the terminal-acknowledgment correction. The AI coverage gate confirms five registered roots, four session methods and three runtime helpers, with shared Node/Bun factory sources and an explicit agent exclusion. The gate is included in API coverage CI.

All 79 runtime declaration assets are synchronized, with no diagnostics under TypeScript 5.1.3 or 6.0.3. Ace passes 1,117 runtime queries. Documentation revision 163 synchronizes 169 modules and 7,344 search entries to Offline Docs 200. Plugin debug/test assembly, lint and signed release collection pass. These local signed builds do not constitute publication or real 16 KiB device acceptance.

Windows builds run serially behind an exclusive lock, with process affinity restricted to two logical processors, BelowNormal priority, one Gradle worker, no parallel projects and in-process Kotlin compilation. JVMs use `ActiveProcessorCount=2`. No global ADB or Gradle stop was used. `JAVA_TOOL_OPTIONS` was removed after its banner interfered with an inherited exact-stderr test; CPU limits remain enforced by process affinity and Gradle JVM options.

## Physical and packaged verification

The accepted device cohorts must be distinguished by AI provider version. Counts include each logical case once; repeated host regressions are additional stability evidence, not extra case coverage.

| Device and accepted provider | Physical cases | Packaged launches | Positive main/Worker program results | Ace screenshots inspected |
|---|---:|---:|---:|---:|
| Xiaomi `968e9f18`, Android 15/API 35, AI 299 | 60 | 10 | 6 | 8 |
| Redmi `bek749scrwv4wo8h`, Android 13/API 33, earlier AI 298 | 60 | 10 | 6 | 8 |

Xiaomi's final cohort passes provider discovery 4, Node 34, Bun 13, host 6, Ace 1, application production 1 and packaged lifecycle 1. The exact final AI 299 APK passes two consecutive six-case host runs in 41.992 and 42.588 seconds. Two earlier passing candidate runs are retained separately and excluded from final artifact acceptance. Independent Node/Bun tests ran with unchanged runtime APKs before the provider update; each receipt is checked against the exact component hashes on which that suite depends. All eight final Xiaomi Ace screenshots were personally inspected after unlock. `xiaomi-final/accepted-evidence.json` records the accepted cohort; the failed AI 297/298 and locked-device attempts under `xiaomi` remain excluded.

On each accepted cohort, two independently signed Bun and Node/Worker applications complete ten launches and six positive main/Worker program results: denied consent, first granted launch, force-stop/restart, revocation during a real stream and fresh denial after revocation. Positive results require nonempty generated text, observed chunks and token usage from the existing local Gemma E2B model. Each APK has an independent signer, the AI entry permission, no microphone permission and DEX hashes identical to retained Runtime Kit 5835. No online account is invoked and no model is downloaded. Xiaomi's actual production and packaged lifecycle tests pass in 16.752 and 110.188 seconds.

The earlier Redmi AI 298 acceptance repeats the affected provider, host and generated-application tests; 48 independent Node/Bun/Ace cases retain receipts from identical unchanged APKs. The subsequent AI 299 follow-up uses a new snapshot under `redmi-admission`. Provider discovery passes four cases, but the host run loses Node to an explicitly logged Android low-memory kill. Before retry, the APK guard detects an external replacement of the host with SHA-256 `972cf2402cf89f842c945c3926310ff8b982ccd78345b97274d9a5732aa9c6b6`. The external APK is independently backed up. The user then directs this session to finish Xiaomi and preserve Redmi. No further Redmi installation, test, cleanup or restoration is performed, and the partial AI 299 follow-up is not counted as acceptance.

The final test project keeps its log Activity visible during cold runtime startup. A hidden-launch candidate lost foreground before the last Node UI launch; Android refused its background Activity start. That interrupted attempt and earlier low-memory failures are excluded. Stage labels identify catalog/chat/stream/session failures without changing public runtime behavior. The final host-test source is `1e726404312d1de011afc04b3325e523f780727b`; production host/kit source remains `43da3f16422fc474237da40df92c4afcf720d50d`.

Active revocation stops the request and leaves authorization false on both accepted cohorts. The existing host runner exposes this provider terminal as `PROVIDER_FAILED`; subsequent unauthorized calls return `PERMISSION_DENIED`. No stronger cross-runtime error-code identity is claimed. Both physical devices use native arm64 and 4 KiB pages.

## Restoration and boundaries

Xiaomi is restored to the new `xiaomi-final` snapshot taken after manual unlock and before this continuation's installations. All nine original APKs match their baseline SHA-256 values. Permission flags, settings, app ops, volumes and original accessibility configuration are verified, including restoring the screen timeout from the temporary one-hour value to 120 seconds. Owned test packages, generated applications and device files are removed, while originally installed test packages and previous Ace evidence are preserved. All 12 original AI private settings/profile/credential/catalog files match their baseline hashes; both existing model binaries match the baseline catalog digests. No model file is replaced.

Redmi's earlier `redmi-final` cohort completed restoration of eight original APKs, ten AI private files, both model binaries and settings. That historical restoration does not describe its current state after the new `redmi-admission` follow-up and external host installation. Current Redmi test components and files remain in place under the user's preservation instruction. A future handoff must inspect the then-current packages and another session's restoration needs before proceeding; the older baseline must not overwrite that external state. All original and takeover backups are retained.

Xiaomi rejected downgrading the tested non-debuggable Node build 291 Release APK directly to its original debug build 244 APK. A separately recorded, same-version/same-signature debug build 291 APK was used only as a restoration bridge. The original build 244 APK was then restored and verified, with the application UID preserved throughout. Application data was not cleared, and the restoration bridge is not a tested runtime acceptance artifact.

Private archive transfers exposed different ADB stdin behavior: `exec-out` could stall on EOF, and Xiaomi truncated a shell-v2 `-T` write. The final restore uses `exec-in` into a uniquely named file inside the application's private cache, verifies its full hash before extraction, and immediately removes the temporary archive. Original local archives remain intact. Only one owned stalled ADB client was ended; the shared server was not restarted.

The general augment check still reports seven inherited memory static fallback gaps: `$memory`, `memory`, `memory.collect`, `memory.dumpHeap`, `memory.retained`, `memory.snapshot` and `memory.watch`. Its 1,361 explicit exports and 1,596 paths are documentation/type/completion counts, not a Node/Bun runtime implementation percentage. The static assets are unchanged from the batch baseline. The host translation-count gate also retains pre-existing deficits of 20 feature, 17 fix and seven improvement entries in other languages relative to Chinese; this batch's own entry is present in all ten languages.

The batch does not implement `ai.agent`, synchronous Rhino terminals, arbitrary JavaScript/Java object or prototype equivalence, new native Bun binaries or a wider real-device ABI/page-size matrix. All changes are local; no push, tag or release is performed.
