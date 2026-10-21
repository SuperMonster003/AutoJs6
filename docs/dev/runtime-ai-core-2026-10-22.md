# Runtime AI core coverage, 2026-10-22

Implementation, documentation/types/completion and local checks are complete. Redmi has completed final acceptance and restoration. Xiaomi is restored, but its final host/UI/package acceptance remains open: manual unlock is required and an intermittent provider failure still needs diagnosis. No push, tag or release.

## Scope and behavior

This batch implements the five registered AI core root names `ask`, `chat`, `stream`, `session` and `catalog` for Node.js main threads, CommonJS/ESM/nested Workers and Bun. The Node facade is callable, with `ai.ask === ai`, and is available as `ai`, `autojs6:ai` and explicit main-thread `$autojs.ai`. Bun uses `host.ai` behind the separate, default-off `ai.control` grant. Session `ask/chat/stream/close` and runtime `hasAccess/requestAccess/close` complete the portable core contract. `ai.agent` is deliberately outside this batch.

Inputs support JSON prompt, message, request, session and catalog forms through the existing host AI mapper and native provider runners. Streams expose `ready` and `result` Promises, typed open/chunk/delta/usage/done/error/cancelled events and cancellation. Bounded UTF-8 fragments preserve Unicode across bridge packets. Timeouts, late results, queue budgets, scoped sessions and Worker exit cleanup are covered. Terminal native resources remain owned until JavaScript acknowledges their result; aborting before acknowledgment can still close a just-created persistent session. Closing a session terminates its active turn.

Bun AI streaming is exempt from the ordinary 1024 cumulative bridge-call budget. Authorization, payload, concurrency and individual call limits remain. Bun native binaries and the published compatibility matrix are unchanged.

## Independently signed applications

The original host uses the existing signature-protected provider entry. Generated applications declare the normal `org.autojs.permission.AI_RUNTIME` entry permission and must additionally receive explicit per-application consent inside 3-Stone AI. The permission declaration alone grants no model/account access. The status provider returns only the caller's own authorization bit and offers no public grant operation. The approval Activity derives the requesting application from Android's result caller, rather than caller-controlled extras.

Consent is bound to package name, UID, first-install time and current signing certificate hashes. Same-install updates retain it; reinstall, UID or signer changes cannot inherit it. The plugin settings page lists authorized applications and can revoke them. Revocation interrupts active runtime sessions, and subsequent calls are denied. The original and runtime services share admission limits, while cancellation and cleanup remain owner-scoped. An unavailable or disabled approval Activity safely declines the request.

Actual Redmi tests rejected the first custom dangerous-permission approach: MIUI closed its system dialog without granting access in both grouped and ungrouped candidates. Those results are excluded. The final design uses provider-owned explicit consent and does not depend on a `pm grant` test shortcut. A separate packaging defect that stripped the AI entry permission was corrected in both Node and Bun capability preparation.

Xiaomi testing also exposed a service-destruction cleanup gap: a session could mark cleanup as scheduled, then executor shutdown could discard that queued action. The provider now lets destruction take over pending cleanup exactly once and keeps admission occupied until cleanup actually finishes. Four deterministic JVM regressions cover discarded work, late work, concurrent destruction and rejected scheduling. The deterministic cleanup defect is fixed, but it does not establish the cause of every generic provider error. Xiaomi still has a failed repeat after one successful run; its final host acceptance remains suspended.

## Source and artifact identity

The host worktree starts at `cfc55eef63111b44fb64761902ac4a402a65abb4`. Mutable primary files and later report commits are not substituted for the tested APKs. Full hashes, signer digests, source identities and raw output are retained under `AutoJs6/.bak/runtime-ai-core-20261021`.

| Component | Version | Tested source |
|---|---|---|
| Host and Runtime Kit | 6.8.0 / 5835 | `43da3f16422fc474237da40df92c4afcf720d50d` |
| Node.js Runtime | 1.31.0 / 291 | `42b2589504b5ed2d20e329dc86973bb1a1b76052` |
| Bun Runtime | 0.20.0 / 293 | `68eaebf8042b93d5812e424016f6d71f2075bf17` |
| 3-Stone AI | 1.5.0 / 298 | `b52cae72e13d8c111ff6b713c5f78fc065e3abfb` |
| Documentation | revision 163 | `ed35b12ef21fee08f28cbde864e51d4cf5c28eb6` |
| TypeScript Declarations | 4.79.0 | `b01cbd5849cfd98d2fed94a751f90fd4baa5bfe8` |
| Ace Editor | 2.41.0 / 326 | `83c5b1918a07ef7d6870e3c2ecdfaa5097adecdb` |
| Offline Docs | 6.8.39 / 200 | `46b80cb63563d000d7c20bd26fa8d9df4fafc8bf` |
| APK Builder Template | 1.2.25 / repository 110 / Android 583501 | `3e439bdf572f27ef1640b1acea8019be00a832d9` |

The retained arm64 kit identity is `autojs6-runtime-v6.8.0+5835-43da3f16422fc474237da40df92c4afcf720d50d-arm64-v8a`. Its directory is `AutoJs6-Plugin-APK-Builder-Template/.bak/runtime-ai-core-20261021/kit-5835`. Earlier 5832/5833 candidates and the 5834 consent verification remain separate evidence, not the final kit.

## Local verification

The final host JVM suite passes 5,232 tests with 26 skipped and no failures. Plugin JVM suites pass Node 96, Bun 60, AI 502, Ace 187, Offline 2 and Builder 40. This is 6,119 passed and 26 skipped in total. The host's 45 legacy differential oracle files were staged only for JVM execution and archived before APK assembly.

Node local tests pass 143 modern cases and seven legacy standalone programs; Bun SDK tests pass 83. The AI/Worker focused reruns and actual NDK compiler syntax check pass after the terminal-acknowledgment correction. The AI coverage gate confirms five registered roots, four session methods and three runtime helpers, with shared Node/Bun factory sources and an explicit agent exclusion. The gate is included in API coverage CI.

All 79 runtime declaration assets are synchronized, with no diagnostics under TypeScript 5.1.3 or 6.0.3. Ace passes 1,117 runtime queries. Documentation revision 163 synchronizes 169 modules and 7,344 search entries to Offline Docs 200. Plugin debug/test assembly, lint and signed release collection pass. These local signed builds do not constitute publication or real 16 KiB device acceptance.

Windows builds run serially behind an exclusive lock, with process affinity restricted to two logical processors, BelowNormal priority, one Gradle worker, no parallel projects and in-process Kotlin compilation. JVMs use `ActiveProcessorCount=2`. No global ADB or Gradle stop was used. `JAVA_TOOL_OPTIONS` was removed after its banner interfered with an inherited exact-stderr test; CPU limits remain enforced by process affinity and Gradle JVM options.

## Physical and packaged verification

Redmi `bek749scrwv4wo8h`, Android 13/API 33, passes 60 accepted physical cases. The final AI 298 follow-up repeats the affected provider, host and generated-application tests; 48 independent Node/Bun/Ace cases retain receipts from the identical unchanged APKs. Each receipt is checked against the exact component hashes on which that suite depends. All eight Redmi Ace screenshots were personally inspected.

Two independently signed Bun and Node/Worker applications complete ten launches and six positive main/Worker program results: denied consent, first granted launch, force-stop/restart, revocation during a real stream and fresh denial after revocation. Positive results require nonempty generated text, observed chunks and token usage from the existing local Gemma E2B model. Each APK has an independent signer, the AI entry permission, no microphone permission and DEX hashes identical to retained Runtime Kit 5835. No online account is invoked and no model is downloaded.

The final test project keeps its log Activity visible during cold runtime startup. A hidden-launch candidate lost foreground before the last Node UI launch; Android refused its background Activity start. That interrupted attempt is excluded. A separate host repeat lost the Node process to an explicitly logged Android low-memory kill and is also excluded. Tests use the direct fixture entry to reduce unrelated plugin activation. Stage labels identify catalog/chat/stream/session failures without changing public runtime behavior. The final host-test source is `1e726404312d1de011afc04b3325e523f780727b`; production host/kit source remains `43da3f16422fc474237da40df92c4afcf720d50d`.

Xiaomi `968e9f18`, Android 15/API 35, passes the independent Node 34, Bun 13 and final provider discovery four cases. Its AI host suite had a Worker `PROVIDER_FAILED` on AI 297. After the deterministic cleanup correction, AI 298 completed one six-case host run, but a repeat failed in Bun with the same generic category. A single passing run is not treated as resolution. The exact failure stage must be collected with the final diagnostic test APK after unlock. `xiaomi/acceptance-suspended.json` prevents accidental collection of the earlier pass as final acceptance.

Android reports a secure, showing keyguard on Xiaomi. Two Ace attempts failed before the test window acquired focus. Collapsing the status bar did not remove the secure keyguard. Manual unlock has been requested; no PIN is requested or bypass attempted. Final Ace screenshots, actual packaged application launches/consent/revocation, and a stable final host rerun remain pending. The earlier partial host results are not a final device acceptance claim. Both physical devices use native arm64 and 4 KiB pages.

Active revocation on Redmi stops the request and leaves authorization false. The existing host runner exposes this provider terminal as `PROVIDER_FAILED`; subsequent unauthorized calls return `PERMISSION_DENIED`. No stronger cross-runtime error-code identity is claimed.

## Restoration and boundaries

Both devices are restored to their fresh snapshots. All eight original Redmi APKs and nine original Xiaomi APKs match their original SHA-256 values. Permission flags, settings, app ops, volumes and originally enabled accessibility bindings are verified. Owned test packages, generated applications and device files are removed. Original AI private settings, credentials and model catalogs match their baseline file hashes; both original model binaries on each device match the baseline catalog digests. Backups are retained. Xiaomi restoration is a safe return of a partially validated device, not proof of acceptance.

Xiaomi rejected downgrading the tested non-debuggable Node build 291 Release APK directly to its original debug build 244 APK. A separately recorded, same-version/same-signature debug build 291 APK was used only as a restoration bridge. The original build 244 APK was then restored and verified, with the application UID preserved throughout. Application data was not cleared, and the restoration bridge is not a tested runtime acceptance artifact.

Private archive transfers exposed different ADB stdin behavior: `exec-out` could stall on EOF, and Xiaomi truncated a shell-v2 `-T` write. The final restore uses `exec-in` into a uniquely named file inside the application's private cache, verifies its full hash before extraction, and immediately removes the temporary archive. Original local archives remain intact. Only one owned stalled ADB client was ended; the shared server was not restarted.

The general augment check still reports seven inherited memory static fallback gaps: `$memory`, `memory`, `memory.collect`, `memory.dumpHeap`, `memory.retained`, `memory.snapshot` and `memory.watch`. Its 1,361 explicit exports and 1,596 paths are documentation/type/completion counts, not a Node/Bun runtime implementation percentage. The static assets are unchanged from the batch baseline. The host translation-count gate also retains pre-existing deficits of 20 feature, 17 fix and seven improvement entries in other languages relative to Chinese; this batch's own entry is present in all ten languages.

The batch does not implement `ai.agent`, synchronous Rhino terminals, arbitrary JavaScript/Java object or prototype equivalence, new native Bun binaries or a wider real-device ABI/page-size matrix. All changes are local; no push, tag or release is performed.
