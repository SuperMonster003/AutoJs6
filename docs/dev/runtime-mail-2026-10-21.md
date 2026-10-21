# Runtime mail coverage and physical acceptance - 2026-10-21

Mail is implemented for Node.js main threads, CommonJS/ESM/nested Workers and Bun. Both physical devices complete the host, provider, runtime, editor and actual packaged-application checks and are restored. This is method-name and documented behavior coverage, not complete Rhino object/prototype equivalence. AI, flow and other remaining audit differences are still separate work.

## Public behavior

- All 52 registered root method names and five providers/accounts names have portable counterparts. Node uses `mail` / `autojs6:mail` and explicit `$autojs.mail`; Bun uses `host.mail` with the independent default-off `mail.control` grant.
- Network operations return Promises; Async names are aliases. The local `folder()` helper binds a path and offers asynchronous operations. Messages retain Date/address values and non-enumerable load/download helpers. Search keeps its fallback marker.
- Default-account close and facade disposal have separate scopes. Worker exit and late creation cleanup release only the owning accounts/watches. Token providers run on their JS thread, may return Promises and participate in the whole-operation deadline. Late replies after close are discarded.
- The bridge reuses the mail session, argument normalization, descriptor transfers and watch recovery. Incoming JSON is fragmented without splitting decoded Unicode; large attachment and RFC822 files travel through descriptors and atomic sinks. Whole message arguments are reduced to UID/folder references before transport.
- Download progress contains transfer packets only. Debug-only trace packets no longer reset the counter to zero; this correction applies to Rhino and portable runtimes.
- Bun mail polling/fragments do not consume the ordinary 1024-call execution quota. Permission, payload, concurrency and per-request deadline checks remain. Inline outgoing message parameters still have the runtime request-size limits.
- Watch creation starts an asynchronous server connection and initial cursor. `isActive` describes the task, not server readiness. Existing messages before cursor establishment are not replayed as new arrivals; explicit fetch/search handles existing mail. The physical fixtures establish readiness with observable synthetic probes, remove those probes with expunge, then send exactly one target SMTP message per program.

## Packaged account boundary

3-Stamp Mail has a separate normal-permission entry, `org.autojs.plugin.mail.permission.RUNTIME`, for independently signed applications. It accepts the caller's explicit credentials and checks the creating UID on session/watch handles. The original signature-protected service and CallerPolicy remain intact. Saved account enumeration/aliases and background watches stay behind that original entry. Packaged attempts are rejected with PLUGIN_UNAVAILABLE. No microphone permission is requested.

The two protocol AARs were built together from host `8be99cba4fa2dfc888d76eaa0e8ebb13b9ece751` and are pinned in the provider lock and notices. No AIDL transaction order changed. The Node bridge library was rebuilt; Node/Bun engine versions and the Bun Android/ABI compatibility matrix were not expanded.

## Exact tested sources and versions

| Component | Version | Tested implementation / source |
|---|---|---|
| AutoJs6 and Runtime Kit | 6.8.0 / build 5702 | `42a10f3d4e472647299160bc3a5380de644500bb` |
| Final host test APK | build 5702 target | `42a10f3d4e472647299160bc3a5380de644500bb` |
| Node Runtime | 1.27.0 / build 278 | `52a57074245544e0e9a7b215914e858753b79434` |
| Bun Runtime | 0.16.0 / build 278 | `ac11e9a4b8c95bb03b640d5516470eb4f33b2582` |
| 3-Stamp Mail | 2.1.0 / build 162 | `276eecb16a0f7d6a68653fdc955575ce7a2ed383` |
| Documentation | 6.8.0 / revision 155 | `06e2881d4ea0ff39ceed09a7af83d9c3626fb1c5` |
| TypeScript Declarations | 4.74.0 | `dee185cf851b26b98f6babafdd307577fe800069` |
| Ace Editor | 2.36.0 / build 316 | `e5a26c67f824bc24e05a1c5158eb1d78d3a4b16d` |
| Offline Docs | 6.8.34 / build 193 | `0487a64182e41748d7849dd5ebdd6653a33a8558` |
| APK Builder Template | 1.2.21 / repository build 98; Android 570201 | `08c73439107091f5a4237072f4f3a7293d5ea398` |

The arm64 Runtime Kit is `autojs6-runtime-v6.8.0+5702-42a10f3d4e472647299160bc3a5380de644500bb-arm64-v8a`, preserved in the Builder repository at `.bak/runtime-mail-20261021/kit-5702`. Its template SHA-256 is `36e23164059c62a811eea9264c486e3bcdbac616b97b189b978646e2e1507d93`. Generated apps have identical Runtime Kit DEX hashes and signing certificates different from the host. The main workspace was integrated at `d30c0c4f039060b09025ca25c5820e8b076abab7` while preserving its concurrent index and working changes. Later main-workspace commits/build numbers are not the identities of these accepted APKs.

## Local validation

| Scope | Accepted result |
|---|---|
| Node clients and Workers | 96 node:test cases plus seven legacy standalone programs |
| Bun SDK | 72 cases; shared factory/type synchronization passes |
| Host C++ wrapper | Syntax checked with its actual Android NDK compilation arguments |
| JVM suites | 5777 discovered, 5748 passed, 29 skipped, zero failures/errors |
| Runtime declarations | 77 mirrored files; TypeScript 5.1.3 and 6.0.3 have zero diagnostics |
| Ace runtime queries | 941 queries with typed roots, message/attachment helpers and watch callbacks |
| Mail coverage gate | 57 registered method names; permission derivation and packaged-account boundaries checked; wired into CI |
| Augment documentation/types/completion | 1356 explicit exports and 1589 queried paths, no failures |
| Android artifacts | Host/test, Runtime Kit, Node/Bun, Mail debug/release/test, Ace, Offline Docs and paired Builder assemblies and relevant checks pass |

JVM counts are host 5019 (26 skipped), mail API 10, Node 96, Bun 60, provider app 88, mail-core 275 (three opt-in provider/performance probes skipped), Ace 187, Offline Docs two and Builder 40. The declaration generator's official Publish workflow was run; offline assets come from the official Documentation generator. Augment totals are public-surface checks, not a count of Node/Bun APIs implemented. Existing host translation-count gaps were preserved: this batch adds one feature and one fix in every language.

## Physical and generated-application validation

| Device | Platform | Accepted checks |
|---|---|---|
| Redmi `bek749scrwv4wo8h` | Android 13 / API 33, arm64, 4096-byte pages | 96 |
| Xiaomi `968e9f18` | Android 15 / API 35, arm64, 4096-byte pages | 96 |

Each consolidated total contains provider contract/session/watch/TLS/packaged-boundary 16, loopback mail-core three, synthetic alias setup one, native bridge two, runtime mail six, Node conformance/npm 34, Bun 13, Ace 19, real APK production one and packaged launch/relaunch one. After the cancellation correction, native/host and real packaged paths were rerun with build 5702. Provider, Node, Bun and Ace component evidence was reused only after checking their APK hashes were unchanged. Duplicate successful reruns and failed attempts are not added to these totals.

Each device uses an isolated loopback IMAP/SMTP/POP3 server and synthetic `example.invalid` accounts. Tests verify a 5000-marker Unicode body, a 98304-byte attachment and its SHA-256, progress, raw files, folder and message operations, UIDL, actual IDLE delivery, typed errors, cancellation, token refresh, the signed-host alias and normal-entry refusal. Token callbacks execute exactly twice per runtime; the fixture separately observes refused expired-token authentication and accepted replacement authentication, without treating library connection attempts as refresh calls.

Each device freshly installs a Bun app and a Node/Worker app, launches each, force-stops it, and launches it again. The provider is force-stopped before every launch. Across two devices there are eight launches and twelve successful program results. Every program reports its own package/application context and Runtime Kit build 5702. Both apps lack microphone permission; normal mail permission is granted and saved-account access is refused. No real mailbox credentials are read and no mail is sent to real recipients. Four Mail popup screenshots per device were visually inspected.

## Failures found and corrected

1. The public Bun route snapshot needed its six new mail transport methods and an independent-grant assertion.
2. Actual device transfers exposed debug packets being counted as zero-byte progress. The shared decoder now distinguishes transfer presence, preserving a legitimate explicit zero. JVM and physical tests cover the correction.
3. A complete message passed to raw/flag/move operations resent its long body and exceeded transport limits. The shared Node/Bun/Worker facade now sends only the reference; a regression includes a very large body and an unrelated throwing getter.
4. Fixture corrections keep file hashing inside Java, supply all explicitly configured loopback endpoints, and count token callbacks separately from protocol authentication attempts. Cold packaged startup exposed the existing asynchronous watch readiness boundary; the final test uses an observable readiness barrier before one SMTP target message and expunges its setup probes before POP3 reads.
5. Ace release validation found six declaration copies with CRLF. Their text was verified against the committed content and only line endings were normalized. Offline Docs' final build 193 preserves forward package-version continuity; provisional local builds were not installed or published.
6. Final review found that cancellation could enqueue an unread terminal packet after the caller stopped polling. A deterministic native test joins the cancelled token-request job and then checks the queue. It fails on build 5700 with one residual packet and passes on build 5702 with none. Cancelled jobs and late-resource cleanup now discard their queued packets; both devices repeat the changed host and packaged paths before restoration.

Every failed attempt remains in the ignored evidence directory. No failure was converted into an accepted run by relaxing the functional assertions.

## Device restoration and coordination

Redmi's six original APKs are restored and SHA-256 checked. Test-only packages, generated apps and files are removed. Bun grants, plugin selections and previous Ace evidence are restored. Recorded permissions, system settings, volume/app-ops and the original accessibility binding are verified. Its newly captured backups remain as evidence; no old speech restoration procedure was rerun.

The user released Xiaomi subject to current-state inspection, then requested Redmi first and natural completion of the other session's original batch. All four P1 fleet reports finished before takeover; the later active P3 run targeted an emulator and Xiaomi had no instrumentation. A fresh eight-APK/settings snapshot was made under `xiaomi-takeover`, retaining the early displaced snapshot separately. Only the host APK differed from that early snapshot. Restoration uses the takeover host build 5679, SHA-256 `0e709c94f91c779534b811aaadba3ce89aeac14f34b9b0b28215fe7dc62b1f2b`, together with the other takeover packages/settings. Eight original APKs and the original accessibility configuration are verified; its host accessibility service was not originally enabled. It does not apply the early build 5621 baseline.

All task-owned reverse mappings are removed. The synthetic servers stop through their owned stop files. No global ADB/Gradle stop, real account change, new AVD, push, tag or release is part of this batch. Evidence, immutable APKs, JUnit XML, screenshots and restore receipts are under ignored `.bak/runtime-mail-20261021/`. Temporary workspace/signing-copy and Worker-directory cleanup are recorded in `cleanup.json` after integration.

The integrated workspace counter is normalized to VERSION_BUILD 4085 (Android versionCode 5737) after parallel commits, following the workspace commit-count rule. That integrated head is not a newly accepted APK; the tested host/Runtime Kit remains build 5702 at the source commit above.
