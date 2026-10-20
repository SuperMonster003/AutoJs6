# Runtime EPUB books and reader sessions

This batch implements the 20 registered EPUB root method names for Node main threads, CJS/ESM/nested Workers and Bun, plus book operations and reader controls/events. Physical and packaged-application acceptance is still pending in this implementation checkpoint. No complete Rhino parity or new native Bun compatibility is claimed.

## Contract

Node provides epub / autojs6:epub and the explicit main-thread $autojs.epub facade. Bun provides host.epub under its independent, default-off epub.control grant. Remote methods return Promises; the Async names are aliases of the same functions. Metadata, toc, readingOrder and positions are ready when open resolves. Cover reads without an output path return encoded Uint8Array/Buffer data; callers can decode it through the separately granted image API.

The host reuses EpubScriptArguments, EpubBookOperations and the existing plugin Binder clients. Large text and byte results use fragmented replies, preserving Unicode. Operations have a default 60000 ms deadline and optional AbortSignal cancellation. Large positive deadlines are scheduled without overflowing JavaScript timers; an already elapsed deadline removes its abort listener and creates no client. Late-created resources, Worker exit and execution teardown release the client's own books and sessions.

Reader state and listeners stay on the owning JS thread. Event fields retain the existing EPUB contract, including totalProgression, chapterTitle and flattened bookmark/highlight changes. Startup events are buffered for listeners installed after read resolves. Control methods return Promises; eventual navigation is observed through progress events. Closing a session normally leaves the reader visible; finish:true also closes its Activity. The plugin's single visible scripted-reader policy and shared preferences/book data remain explicit.

## Independently signed applications

3-Folio EPUB adds a separate service and reader Activity alias guarded by org.autojs.plugin.epub.permission.RUNTIME. The original signature-protected host entries remain unchanged. Both routes use the same implementation; returned book and reader-control Binders check the creating UID, and service teardown closes only its own reader sessions. The host chooses the route from the calling application context. Node/Bun package preparation derives the normal permission from the declared EPUB capability. No microphone permission is needed.

The EPUB AIDL transaction order, contract versions, Readium dependencies and user-data schema are unchanged. Each service instance retains its eight-book limit and idle cleanup. Bun EPUB fragments/events do not consume the ordinary 1024-call execution quota; grant, payload, concurrency and per-RPC timeout checks remain. Native Bun binaries and compatibility reports are unchanged.

## Local verification checkpoint

- Host: 5014 JVM cases discovered, 4988 passed and 26 skipped; host and instrumentation APK assembly passed.
- Node: 87 client/Worker tests, seven legacy standalone programs and actual NDK syntax checking passed. Plugin JVM, debug/test APK and lint checks passed before the final deadline-only factory update; final artifacts must be rebuilt from the sealed implementation.
- Bun: 70 SDK cases passed; plugin JVM, debug/test APK and lint checks passed. Final artifacts must contain the final synchronized factory.
- EPUB provider: compilation, JVM, debug/test/release assembly and lint passed. Final artifacts must include the updated in-app instructions.
- Declarations: 76 runtime files synchronized; TypeScript 5.1.3 and 6.0.3 report no diagnostics. Ace's 831 runtime completion queries pass.
- Coverage: all 20 EPUB root names, the shared factory/types and both provider entry paths pass the focused check. The existing augment gate still covers 1356 explicit exports and 1589 documentation/type/completion paths with no failures; these are not Node/Bun runtime parity totals.
- Documentation: revision 153 generated and synchronized to Offline Docs 6.8.33/build 191; debug/release documentation APK validation passed.

The first full host build exposed an existing ConsoleTimeSettingsDeviceTest constructor mismatch after the typed-settings migration. That two-call-site test adaptation is a separate commit, ce884c909. A subsequent full JVM attempt lacked the ignored pre-6.6 module oracle files in the new worktree; copying the existing test inputs resolved all 30 initialization failures without changing those tests or production modules. Failed logs and the copied-input hashes are retained separately.

## Device and artifact plan

Fresh APK/settings backups have been captured for Redmi bek749scrwv4wo8h (API 33) and Xiaomi 968e9f18 (API 35). Neither device originally has 3-Folio EPUB installed. Tests explicitly require selection of the task-owned 3-Folio installation so the legacy Readium app and its data stay outside this batch.

RuntimeEpubNativeTest checks transport/client ownership and error codes. RuntimeEpubDeviceTest checks real books, long Unicode text, cover/resource output, search, annotations, reader events/controls, denied SDK/raw grants, streaming beyond 1024 calls and CJS/ESM/nested Worker cleanup. RuntimeEpubPackagedDeviceTest builds real Bun and Node/Worker applications, exercises fresh/restart launches and cold provider binding, and reports their own application identity. Ace adds real typed EPUB popup checks.

Acceptance, exact APK/Runtime Kit identities, restoration and cleanup will be filled in after those operations are performed. Evidence and local helpers are under the ignored .bak/runtime-epub-20261020 directory in the primary host checkout. Nothing is pushed, tagged or published.
