# Portable WebView clients and files - 2026-10-18

This batch supplies WebView download handling, document selection and portable
WebViewClient/WebChromeClient callbacks to Node main, CJS/ESM/nested Workers and
Bun host.ui. Documentation, declarations, Ace, offline docs and the genuine APK
Builder Runtime Kit are synchronized. These are locally verified candidates.

## Delivered API

- Ten public methods: setWebViewClient, setWebChromeClient, setDownloadListener,
  chooseFiles, cancelFileChooser, exitFullscreen, download, getDownload,
  cancelDownload and readDownload. The client registration surface has 19
  WebViewClient callbacks, 20 WebChromeClient callbacks and onDownloadStart.
- Android decisions requiring synchronous return values use preconfigured
  navigationRules, resourceRules and blockedKeyCodes. Corresponding JavaScript
  callbacks report the applied decision. Navigation is never cancelled and
  replayed merely to await a cross-process callback. Console consumption is
  likewise an explicit native option.
- Requests with native continuation objects support asynchronous replies:
  JavaScript dialogs, file selection, HTTP credentials, SSL, client certificates,
  safe browsing, permissions, geolocation, popup creation and visited history.
  Requests have identity, cancellation and timeout handling; stale replies cannot
  answer a newer request. Default native dialogs and document selection work
  without custom callbacks. Fullscreen Views stay native and use a private Activity.
- evaluateJavascript submits a short native task and receives its result through
  the event stream. A callback can read its View or open a document picker while
  evaluation is pending. User interaction does not hold a Bun RPC open. Its
  default timeout is 30000 ms, configurable from 100 to 120000 ms; it still returns
  Android's decoded JSON result rather than awaiting webpage Promises.
- Document selection returns readable content URIs or null. Open/multiple modes,
  MIME/extension filters, optional initial directory, cancellation and actual HTML
  uploads are supported. Newly acquired persistable read grants are reference
  counted and released with the View; existing grants are preserved. Temporary
  custom grants retain their Android lifetime. App-private providers are rejected.
- Downloads are explicit HTTP(S) DownloadManager jobs in public Downloads.
  Metadata, state, bounded byte reads and removal are available. Jobs survive
  WebView closure. Cookies are opt-in; blob:/data: downloads are not implemented.
- Worker termination conditionally removes only its own registrations and native
  tasks. A newer main-thread client is retained. Shared View identities remain
  execution-owned and have one event-queue reader. Window/session closure cancels
  pending UI operations; View disposal also releases grants.

## Corrections found during verification

- WebResourceRequest headers can be null on a real navigation. Serialization now
  emits an empty headers object instead of crashing the Android main thread.
- Some WebView providers pass JsPromptResult to confirm/alert as well. Reply
  decoding now uses the callback kind; a Boolean confirm reply is not mistaken
  for a prompt string.
- WebView may request visited history during construction, before an asynchronous
  client can register. That request is retained until configuration or disposal.
- Ordinary event pressure must not discard web task results, continuation requests
  or terminal close events. The native queue preserves these messages while
  discarding older ordinary notifications; a native overflow regression exercises
  both mixed and all-critical queues.
- Packaged WebViews need their own INTERNET permission. An external runtime's
  permission does not replace it. The Node project's execution budget is separate
  from WebView callback/evaluation timeouts: the fixture explicitly uses
  node.timeoutMs=120000 instead of the 5000 ms project default. Earlier short-budget
  runs stopped at different interaction stages; their logs remain in the evidence.

Test infrastructure corrections are recorded separately from product fixes: the
standalone document provider uses Java because the separate test-APK process does
not contain the host Kotlin runtime; fixture files keep stable bytes and modification
times across metadata queries and repeated reads; DocumentsUI activation waits for
the active grid and retries a single-file tap only while the same entry remains.
The local TLS server uses an ephemeral in-memory software key/certificate. The Ace
popup helper extracts the current identifier rather than everything after the last
dot, so object callback names are tested through the real popup.

## Accepted verification

| Suite | Redmi 22120RN86C, Android 13/API 33 | Xiaomi 23046RP50C, Android 15/API 35 |
| --- | ---: | ---: |
| WebView client/file/dialog/TLS/Worker/policy cases | 6 | 6 |
| Native queue overflow regression | 1 | 1 |
| Node runtime conformance, npm and Workers | 44 | 44 |
| Bun runtime instrumentation | 13 | 13 |
| WebView/Drawer/Toolbar/ViewPager regression | 4 | 4 |
| XML properties and widgets | 7 | 7 |
| Native UI interop | 4 | 4 |
| UI/Compose | 7 | 7 |
| Existing Node UI bridge regression | 9 | 9 |
| Genuine Node/Bun APK production and installed execution | 4 | 4 |
| Ace default-runtime and JSOX popup checks | - | 5 |

There are 203 accepted instrumentation cases, excluding failed attempts and duplicate
reruns. Redmi's first six-case WebView run passed five cases; its file case failed
because the fixture rewrote selected files during reads. The corrected file case
then passed separately for all six runtime modes. Both records are retained and
count as six distinct accepted cases. Xiaomi's six-case run passed together.

The actual WebView scenarios cover page/resource/navigation notifications, static
resource interception and blocked navigation, JS alert/confirm/prompt replies,
HTTP authentication and 404 data, popup creation/closure, download metadata and
bytes, persistent download access after View disposal, system single/multiple
selection, real multipart uploads, grant restoration, default dialogs, Bun input
lasting longer than its RPC deadline, invalid/late/timeout/replaced replies, SSL
cancel/proceed against a local self-signed server, and Worker termination with an
open picker. Callback names being covered does not mean all 40 native callbacks
were independently triggered on both devices.

The genuine packaged fixtures retain application versionCode 1 and report runtime
versionCode 5565. Their binary Manifests contain the appropriate packaged-runtime
permission and INTERNET. They run WebView callback/file/upload/download checks,
navigation components, XML/native properties and Compose interaction/rotation.
All four produced APKs have certificates different from the host, and every
classes*.dex matches the final Runtime Kit template. The APK Builder's embedded
template is checked byte-for-byte against that kit.

Desktop/source validation includes the 40-callback/10-method Node/Bun/native/docs
gate; actual CJS/ESM/nested desktop Worker reply and termination tests; 27 Bun client
tests; 38 Bun Python tests and the unchanged compatibility matrix; TypeScript 5.1.3
and 6.0.3 checks over 70 synchronized declaration assets; real Ace semantic queries;
and the host Augmentable gate covering 1351 explicit exports/1522 queried paths with
no failures. That host export count is not a claim of full Node/Bun Rhino parity.

JVM results are 4691 passing host tests with 26 existing skips, plus Node 96, Bun 59,
Ace 187, APK Builder 39 and Offline Docs 2 passing tests: 5074 passes in total.
Host/Runtime Kit, Node, Bun, Ace, APK Builder and Offline Docs builds succeeded.
Generated Markdown checks passed in all six associated publishing repositories;
the documentation generator synchronized 163 modules and search data to Offline Docs.

## Versions and evidence

| Repository | Candidate |
| --- | --- |
| AutoJs6 | 6.8.0, source build 3913 / Android runtime 5565 |
| Node.js Runtime | 1.11.0, build 259 |
| Bun Runtime | 0.2.10, build 257 |
| TypeScript Declarations | 4.56.0 |
| Ace Editor | 2.18.0, build 293 |
| Documentation | 6.8.0, revision 133 |
| Offline Docs | 6.8.13, build 164 |
| APK Builder Template | 1.2.3, build 78, paired runtime 5565 |

Local evidence is retained in `.bak/webview-callbacks-20261018/`: per-suite logs,
installation SHA receipts, runtime result JSON, Ace screenshots, `evidence.json`,
`certificates.json`, `artifacts/manifest.json`, restoration records and final commit
metadata. Failed development attempts remain separate. The build uses the existing
isolated `.bak/oi-check` checkout, based on bd3b98dc026ca1a049e64a4192c19b2781c4eb92
plus the explicitly recorded source overlay; local signing files are preserved.

Both devices' original installed APKs are restored and verified by SHA-256.
Permission flags, music volume, rotation settings and projection release are checked.
No existing app data was cleared. The five pre-existing host documentation drafts
remain outside this batch's commits. Work is committed locally without pushing,
tagging, publishing an npm package, updating a release index or creating a release.

## Remaining boundaries

This is a portable JSON/async contract, not arbitrary Java WebViewClient subclassing
or raw View/Bitmap return-value parity. Camera capture, save/folder picker modes,
blob:/data: downloads and deprecated database/quota hooks remain outside this batch.
KeyChain client-certificate, safe-browsing, media/location permission and fullscreen
branches have implementation/type coverage but not an exhaustive device scenario
matrix here. Default dialog input-state restoration across rotation is not certified.
Both physical devices use arm64 and 4096-byte pages. Other ABIs, real 16 KB execution
and the complete signed-release matrix are not claimed. Bun's frozen official native
payload is unchanged. Other Android-object helpers and large runtime API gaps remain
tracked by [the continuing audit](runtime-api-coverage-2026-10-17.md).
